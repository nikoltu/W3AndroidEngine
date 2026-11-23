package com.etheller.warsmash.viewer5.handlers.w3x;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.etheller.warsmash.parsers.w3x.doo.War3MapDoo;
import com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapRuntimeObjectData;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.units.manager.MutableObjectData.WorldEditorDataType;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.tga.TgaFile;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.CreatedDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.LoadMapTask;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.MapLoader;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.BuildingShadow;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.RemovablePathingMapInstance;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDoodad;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimThreadGuard;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.WorldPeerData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDoodadTable;

/**
 * Creates the doodads and destructables of a map, and works out their pathing.
 */
final class MapViewerDoodads {
	private MapViewerDoodads() {
	}

	/**
	 * Queues the creation of the map's doodads and destructables onto the loader a slice at a time: a map with
	 * thousands of them, which load models and textures, would otherwise hold the frame for as long as the app can be
	 * left unpaused.
	 */
	static void queueDoodadsAndDestructibles(final War3MapViewer viewer,
			final Warcraft3MapRuntimeObjectData modifications, final War3MapW3i w3iFile, final MapLoader loader)
			throws IOException {
		MapViewerLoading.applyModificationFile(viewer, viewer.doodadsData, viewer.doodadMetaData, modifications.getDoodads(),
				WorldEditorDataType.DOODADS);
		MapViewerLoading.applyModificationFile(viewer, viewer.doodadsData, viewer.destructableMetaData, modifications.getDestructibles(),
				WorldEditorDataType.DESTRUCTIBLES);

		final War3MapDoo doo = viewer.mapMpq.readDoodads(w3iFile);

		final List<com.etheller.warsmash.parsers.w3x.doo.Doodad> placed = new ArrayList<>();
		for (final com.etheller.warsmash.parsers.w3x.doo.Doodad doodad : doo.getDoodads()) {
			if ((doodad.getFlags() & 0x2) != 0) {
				placed.add(doodad);
			}
		}
		final LoadMapTask finish = () -> {
			viewer.doodadTable = CDoodadTable.build(doo.getDoodads(), id -> {
				final GameObject doodadRow = modifications.getDoodads().get(id);
				return doodadRow == null ? null
						: new float[] { doodadRow.readSLKTagFloat("maxPitch"), doodadRow.readSLKTagFloat("maxRoll") };
			}, viewer.simulation.getTerrainData());

			viewer.doodadsReady = true;
			viewer.anyReady = true;
		};
		final LoadMapTask terrainDoodads = () -> loader.addSlices(doo.getTerrainDoodads().iterator(),
				doodad -> loadTerrainDoodad(viewer, modifications, doodad), finish);
		loader.addSlices(placed.iterator(), doodad -> {
			final float[] location = doodad.getLocation();
			createDestructableOrDoodad(viewer, doodad.getId(), modifications, doodad.getVariation(), location,
					doodad.getAngle(), doodad.getLife(), doodad.getScale());
		}, terrainDoodads);
	}

	private static void loadTerrainDoodad(final War3MapViewer viewer, final Warcraft3MapRuntimeObjectData modifications,
			final com.etheller.warsmash.parsers.w3x.doo.TerrainDoodad doodad) {
		final GameObject row = modifications.getDoodads().get(doodad.getId());
		String file = row.readSLKTag("file");//
		final MdxModel model;
		if ((file == null) || MapViewerEffects.isNoModelPath(file)) {
			// A doodad type without a model file (map dummies) is placed all the same, just not seen.
			model = viewer.emptyModel();
		}
		else {
			if (file.toLowerCase(Locale.ROOT).endsWith(".mdl")) {
				file = file.substring(0, file.length() - 4);
			}
			if (!file.toLowerCase(Locale.ROOT).endsWith(".mdx")) {
				file += ".mdx";
			}
			model = null;
		}

		final String pathingTexture = row.readSLKTag("pathTex");
		ArgbImage pathingTextureImage;
		if ((pathingTexture != null) && (pathingTexture.length() > 0) && !"_".equals(pathingTexture)) {

			pathingTextureImage = viewer.filePathToPathingMap.get(pathingTexture.toLowerCase(Locale.ROOT));
			if (pathingTextureImage == null) {
				if (viewer.mapMpq.has(pathingTexture)) {
					try {
						pathingTextureImage = TgaFile.readTGAPixels(pathingTexture,
								viewer.mapMpq.getResourceAsStream(pathingTexture));
						viewer.filePathToPathingMap.put(pathingTexture.toLowerCase(Locale.ROOT), pathingTextureImage);
					} catch (final Exception exc) {
						Log.error(exc);
					}
				}
			}
		} else {
			pathingTextureImage = null;
		}
		if (pathingTextureImage != null) {
			// blit out terrain cells under this TerrainDoodad
			final int textureWidth = pathingTextureImage.getWidth();
			final int textureHeight = pathingTextureImage.getHeight();
			final int textureWidthTerrainCells = textureWidth / 4;
			final int textureHeightTerrainCells = textureHeight / 4;
			final int minCellX = (int) doodad.getLocation()[0];
			final int minCellY = (int) doodad.getLocation()[1];
			final int maxCellX = (minCellX + textureWidthTerrainCells) - 1;
			final int maxCellY = (minCellY + textureHeightTerrainCells) - 1;
			for (int j = minCellY; j <= maxCellY; j++) {
				for (int i = minCellX; i <= maxCellX; i++) {
					viewer.terrain.removeTerrainCellWithoutFlush(i, j);
					viewer.simulation.getTerrainData().hideCell(i, j);
				}
			}
			viewer.terrain.flushRemovedTerrainCells();
			viewer.simulation.getTerrainData().flushHiddenCells();
		}

		Log.info("Loading terrain doodad: " + file);
		viewer.terrainDoodads.add(new TerrainDoodad(viewer, model, file, row, doodad, pathingTextureImage));
	}

	static void createDoodad(final War3MapViewer viewer, final GameObject row, final int doodadVariation, final float[] location,
			final float facingRadians, final float[] scale) {
		final MdxModel model = getDoodadModel(viewer, doodadVariation, row);
		final float maxPitch = row.readSLKTagFloat("maxPitch");
		final float maxRoll = row.readSLKTagFloat("maxRoll");
		final float defScale = row.readSLKTagFloat("defScale");
		final RenderDoodad renderDoodad = new RenderDoodad(viewer, model, row, location, scale, facingRadians, maxPitch,
				maxRoll, defScale, doodadVariation);
		renderDoodad.instance.uniformScale(defScale);
		renderDoodad.enableLazyClock();
		viewer.doodads.add(renderDoodad);
		viewer.pendingDoodads.add(renderDoodad);
		viewer.decals.add(renderDoodad);
	}

	// Measured on 1.29: a destructable with a fixed rotation (gates, pathing blockers) ignores the facing it is
	// created with, and its pathing keeps the fixed orientation.
	static float getDestructableFacing(final GameObject row, final float requestedFacingRadians) {
		final String fixedRotation = row.readSLKTag("fixedRot");
		if ((fixedRotation != null) && !fixedRotation.isEmpty()) {
			try {
				final float degrees = Float.parseFloat(fixedRotation.trim());
				if (degrees >= 0) {
					return (float) Math.toRadians(degrees);
				}
			}
			catch (final NumberFormatException e) {
				// not a fixed rotation
			}
		}
		return requestedFacingRadians;
	}

	// Measured on 1.29: a destructable's pathing map turns only in quarter turns, to the one nearest its facing
	// (45 degrees takes 90, 269.0 takes 270).
	static int getPathingRotation(final float facingRadians) {
		final int quarterTurns = Math.round((float) Math.toDegrees(facingRadians) / 90f);
		return (((quarterTurns * 90) % 360) + 360) % 360;
	}

	// Measured on 1.29: a destructable made by script stands where its path texture's corners fall on the 64 grid:
	// the coordinate is cut toward zero to a multiple of 64 and, when half the texture is an odd number of cells
	// (2x2), moved 32 further from zero (crate -5296, 2400 at -5248, 2368; barrel -4720, 2396 at -4704, 2400).
	static float snapRunTimeDestructableX(final War3MapViewer viewer, final GameObject row, final float x, final float facingDegrees) {
		return snapRunTimeDestructableCoordinate(x, getRotatedPathingCells(viewer, row, facingDegrees, true));
	}

	static float snapRunTimeDestructableY(final War3MapViewer viewer, final GameObject row, final float y, final float facingDegrees) {
		return snapRunTimeDestructableCoordinate(y, getRotatedPathingCells(viewer, row, facingDegrees, false));
	}

	static int getRotatedPathingCells(final War3MapViewer viewer, final GameObject row, final float facingDegrees, final boolean xAxis) {
		final ArgbImage pathingPixelMap = viewer.getDestructablePathingPixelMap(row);
		if (pathingPixelMap == null) {
			return 0;
		}
		final boolean turned = (getPathingRotation(
				getDestructableFacing(row, (float) Math.toRadians(facingDegrees))) % 180) != 0;
		return xAxis != turned ? pathingPixelMap.getWidth() : pathingPixelMap.getHeight();
	}

	static float snapRunTimeDestructableCoordinate(final float coordinate, final int pathingCells) {
		float snapped = ((int) (coordinate / 64f)) * 64f;
		if (((pathingCells / 2) % 2) == 1) {
			snapped += coordinate < 0 ? -32f : 32f;
		}
		return snapped;
	}

	static RenderDestructable createNewDestructable(final War3MapViewer viewer, final War3ID doodadId, final GameObject row,
			final int doodadVariation, final float[] location, final float requestedFacingRadians,
			final short lifePercent, final float[] scale) {
		final CDestructable simulationDestructable = spawnDestructable(viewer, row, location, requestedFacingRadians,
				lifePercent);
		return buildDestructablePeer(viewer, row, doodadVariation, location, requestedFacingRadians, lifePercent, scale,
				simulationDestructable);
	}

	/**
	 * The simulation's part of making a destructable: its pathing and the object. It reads the object data and
	 * changes the simulation only, so the simulation thread can do it.
	 */
	static CDestructable spawnDestructable(final War3MapViewer viewer, final GameObject row, final float[] location,
			final float requestedFacingRadians, final short lifePercent) {
		final float facingRadians = getDestructableFacing(row, requestedFacingRadians);
		RemovablePathingMapInstance destructablePathing = null;
		RemovablePathingMapInstance destructablePathingDeath = null;

		final ArgbImage destructablePathingPixelMap = viewer.getDestructablePathingPixelMap(row);
		if (destructablePathingPixelMap != null) {
			destructablePathing = viewer.terrain.pathingGrid.createRemovablePathingOverlayTexture(location[0],
					location[1], getPathingRotation(facingRadians), destructablePathingPixelMap);
			if (lifePercent > 0) {
				destructablePathing.add();
			}
		}
		final ArgbImage destructablePathingDeathPixelMap = viewer.getDestructablePathingDeathPixelMap(row);
		if (destructablePathingDeathPixelMap != null) {
			destructablePathingDeath = viewer.terrain.pathingGrid.createRemovablePathingOverlayTexture(location[0],
					location[1], getPathingRotation(facingRadians), destructablePathingDeathPixelMap);
			if (lifePercent <= 0) {
				destructablePathingDeath.add();
			}
		}
		final float x = location[0];
		final float y = location[1];
		final CDestructable simulationDestructable = viewer.simulation.internalCreateDestructable(
				War3ID.fromString(row.getId()), x, y, destructablePathing, destructablePathingDeath);
		if (lifePercent != 100) {
			simulationDestructable.setLife(viewer.simulation, simulationDestructable.getLife() * (lifePercent / 100f));
		}
		return simulationDestructable;
	}

	/** The render side of {@link #spawnDestructable}: model, shadow and the peer. */
	static RenderDestructable buildDestructablePeer(final War3MapViewer viewer, final GameObject row,
			final int doodadVariation, final float[] location, final float requestedFacingRadians,
			final short lifePercent, final float[] scale, final CDestructable simulationDestructable) {
		final float facingRadians = getDestructableFacing(row, requestedFacingRadians);
		BuildingShadow destructableShadow = null;
		// A walkable one (bridge, ramp, gate) is walked on by its model's bounds as soon as it stands, so its model is
		// read at once; any other may be read in the background while the game goes on.
		final boolean walkable = row.readSLKTagBoolean("walkable");
		final MdxModel model;
		final boolean deferBefore = viewer.setDeferModelLoadsInGame(!walkable);
		try {
			model = getDoodadModel(viewer, doodadVariation, row);
		}
		finally {
			viewer.setDeferModelLoadsInGame(deferBefore);
		}
		final MdxModel portraitModel = viewer.loadWaitingModelMdx(row.readSLKTag("portraitmodel"));
		final boolean waitForModel = model.isPending();

		final float maxPitch = row.readSLKTagFloat("maxPitch");
		final float maxRoll = row.readSLKTagFloat("maxRoll");
		final String shadowString = row.readSLKTag("shadow");
		String lateShadow = null;
		if ((shadowString != null) && (shadowString.length() > 0) && !"_".equals(shadowString)
				&& !"none".equalsIgnoreCase(shadowString)) {
			if (waitForModel) {
				// whether the model casts a shadow of its own is known once it is built
				lateShadow = shadowString;
			}
			else {
				destructableShadow = viewer.terrain.addShadow(shadowString, location[0], location[1],
						!viewer.dynamicShadowManager.castsSceneShadow(model, scale[2]));
			}
		}

		final float x = location[0];
		final float y = location[1];
		// Used to be this, but why: (float) Math.sqrt((scale[0]) * (scale[1]) *
		// (scale[2]));
		final float selectionScale = 1.0f;
		final RenderDestructable renderDestructable = new RenderDestructable(viewer, model, portraitModel, row, location,
				scale, facingRadians, selectionScale, maxPitch, maxRoll, lifePercent, destructableShadow, lateShadow,
				waitForModel, simulationDestructable, doodadVariation);
		if (walkable) {
			final float angle = facingRadians;
			BoundingBox boundingBox = model.bounds.getBoundingBox();
			if (boundingBox == null) {
				// TODO this is a hack and should be fixed later
				boundingBox = new BoundingBox(new Vector3(-10, -10, 0), new Vector3(10, 10, 0));
			}
			final Rectangle renderDestructableBounds = getRotatedBoundingBox(viewer, x, y, angle, boundingBox);
			viewer.walkableObjectsTree.add((MdxComplexInstance) renderDestructable.instance, renderDestructableBounds);
			renderDestructable.walkableBounds = renderDestructableBounds;
		}
		viewer.widgets.add(renderDestructable);
		viewer.decals.add(renderDestructable);
		viewer.destructableToRenderPeer.put(simulationDestructable, renderDestructable);
		return renderDestructable;
	}

	/**
	 * The simulation's part of a destructable made while the game runs (a script, a saved game): the snapping, the
	 * simulation object and what the peer is built from.
	 *
	 * @param z NaN to have the render side find the height
	 */
	static WorldPeerData.Destructable spawnRunTimeDestructable(final War3MapViewer viewer, final War3ID typeId,
			final float requestedX, final float requestedY, final float z, final float facing, final float scale,
			final int variation) {
		final GameObject row = viewer.allObjectData.getDestructibles().get(typeId);
		if (row == null) {
			throw new NullPointerException("createDestructableZ called on: " + typeId + "," + requestedX + ","
					+ requestedY + "," + z + "," + facing + "," + scale + "," + variation);
		}
		final float x = snapRunTimeDestructableX(viewer, row, requestedX, facing);
		final float y = snapRunTimeDestructableY(viewer, row, requestedY, facing);
		final CDestructable simulationDestructable = spawnDestructable(viewer, row, new float[] { x, y, z },
				(float) Math.toRadians(facing), (short) 100);
		return new WorldPeerData.Destructable(simulationDestructable, typeId, x, y, z, facing, scale, variation);
	}

	/** The render side of {@link #spawnRunTimeDestructable}. */
	static RenderDestructable buildRunTimeDestructablePeer(final War3MapViewer viewer,
			final WorldPeerData.Destructable data) {
		final GameObject row = viewer.allObjectData.getDestructibles().get(data.typeId);
		final float z = Float.isNaN(data.z)
				? Math.max(viewer.getWalkableRenderHeight(data.x, data.y), viewer.terrain.getGroundHeight(data.x, data.y))
				: data.z;
		final RenderDestructable peer = buildDestructablePeer(viewer, row, data.variation,
				new float[] { data.x, data.y, z }, (float) Math.toRadians(data.facingDegrees), (short) 100,
				new float[] { data.scale, data.scale, data.scale }, data.destructable);
		viewer.createdDestructables.add(new CreatedDestructable(data.destructable.getHandleId(),
				data.typeId.getValue(), data.x, data.y, z, data.facingDegrees, data.scale, data.variation));
		return peer;
	}

	static void createDestructableOrDoodad(final War3MapViewer viewer, final War3ID doodadId, final Warcraft3MapRuntimeObjectData modifications,
			final int doodadVariation, final float[] location, final float facingRadians, final short lifePercent,
			final float[] scale) {
		GameObject row = modifications.getDoodads().get(doodadId);
		if (row == null) {
			row = modifications.getDestructibles().get(doodadId);
			if (row != null) {
				// the map is loading: the render thread makes the simulation's objects
				SimThreadGuard.renderOwnsSimulation(true);
				try {
					createNewDestructable(viewer, doodadId, row, doodadVariation, location, facingRadians, lifePercent, scale);
				}
				finally {
					SimThreadGuard.renderOwnsSimulation(false);
				}
			}
		} else {
			createDoodad(viewer, row, doodadVariation, location, facingRadians, scale);
		}
	}

	static MdxModel getDoodadModel(final War3MapViewer viewer, final int doodadVariation, final GameObject row) {
		String file = row.readSLKTag("file");
		final int numVar = row.readSLKTagInt("numVar");
		if ((file == null) || MapViewerEffects.isNoModelPath(file)) {
			return viewer.emptyModel();
		}

		if (file.endsWith(".mdx") || file.endsWith(".mdl")) {
			file = file.substring(0, file.length() - 4);
		}

		String fileVar = file;

		file += ".mdx";

		if (numVar > 1) {
			fileVar += Math.min(doodadVariation, numVar - 1);
		}

		fileVar += ".mdx";
		// First see if the model is local.
		// Doodads referring to local models may have invalid variations, so if the
		// variation doesn't exist, try without a variation.

		String path;
		if (viewer.mapMpq.has(fileVar)) {
			path = fileVar;
		} else {
			path = file;
		}
		MdxModel model;
		if (viewer.mapMpq.has(path)) {
			model = (MdxModel) viewer.load(path, viewer.mapPathSolver, viewer.solverParams);
		} else {
			model = (MdxModel) viewer.load(fileVar, viewer.mapPathSolver, viewer.solverParams);
		}
		return model;
	}

	static Rectangle getRotatedBoundingBox(final War3MapViewer viewer, final float x, final float y, final float angle,
			final BoundingBox boundingBox) {
		final float x1 = boundingBox.min.x;
		final float y1 = boundingBox.min.y;
		final float x2 = boundingBox.min.x + boundingBox.getWidth();
		final float y2 = boundingBox.min.y;
		final float x3 = boundingBox.min.x + boundingBox.getWidth();
		final float y3 = boundingBox.min.y + boundingBox.getHeight();
		final float x4 = boundingBox.min.x;
		final float y4 = boundingBox.min.y + boundingBox.getHeight();
		final float angle1 = (float) StrictMath.atan2(y1, x1) + angle;
		final float len1 = (float) StrictMath.sqrt((x1 * x1) + (y1 * y1));
		final float angle2 = (float) StrictMath.atan2(y2, x2) + angle;
		final float len2 = (float) StrictMath.sqrt((x2 * x2) + (y2 * y2));
		final float angle3 = (float) StrictMath.atan2(y3, x3) + angle;
		final float len3 = (float) StrictMath.sqrt((x3 * x3) + (y3 * y3));
		final float angle4 = (float) StrictMath.atan2(y4, x4) + angle;
		final float len4 = (float) StrictMath.sqrt((x4 * x4) + (y4 * y4));
		final double x1prime = StrictMath.cos(angle1) * len1;
		final double x2prime = StrictMath.cos(angle2) * len2;
		final double x3prime = StrictMath.cos(angle3) * len3;
		final double x4prime = StrictMath.cos(angle4) * len4;
		final double y1prime = StrictMath.sin(angle1) * len1;
		final double y2prime = StrictMath.sin(angle2) * len2;
		final double y3prime = StrictMath.sin(angle3) * len3;
		final double y4prime = StrictMath.sin(angle4) * len4;
		final float minX = (float) StrictMath.min(StrictMath.min(x1prime, x2prime), StrictMath.min(x3prime, x4prime));
		final float minY = (float) StrictMath.min(StrictMath.min(y1prime, y2prime), StrictMath.min(y3prime, y4prime));
		final float maxX = (float) StrictMath.max(StrictMath.max(x1prime, x2prime), StrictMath.max(x3prime, x4prime));
		final float maxY = (float) StrictMath.max(StrictMath.max(y1prime, y2prime), StrictMath.max(y3prime, y4prime));
		return new Rectangle(x + minX, y + minY, maxX - minX, maxY - minY);
	}

	static CDestructable createDestructableForSavedGame(final War3MapViewer viewer, final CreatedDestructable created) {
		final CDestructable destructable;
		SimThreadGuard.renderOwnsSimulation(true);
		try {
			destructable = viewer.simulation.getSimulationRenderController().createDestructableZ(
					new War3ID(created.typeId), created.x, created.y, created.z, created.facing, created.scale,
					created.variation);
		}
		finally {
			SimThreadGuard.renderOwnsSimulation(false);
		}
		// The simulation's handle id comes back with the saved state; this list keeps the saved game's numbers.
		viewer.createdDestructables.remove(viewer.createdDestructables.size() - 1);
		viewer.createdDestructables.add(created);
		return destructable;
	}

	/**
	 * SetDoodadAnimation. Null area means every doodad of the type.
	 */
	static void setDoodadAnimation(final War3MapViewer viewer, final String doodadId, final Rectangle area, final float centerX,
			final float centerY, final float radius, final boolean nearestOnly, final String animation) {
		forDoodads(viewer, doodadId, area, centerX, centerY, radius, nearestOnly,
				doodad -> doodad.playAnimation(animation));
	}

	/**
	 * SetDoodadColor. Null area means every doodad of the type.
	 */
	static void setDoodadColor(final War3MapViewer viewer, final String doodadId, final Rectangle area, final float centerX,
			final float centerY, final float radius, final boolean nearestOnly, final int playerColor) {
		forDoodads(viewer, doodadId, area, centerX, centerY, radius, nearestOnly,
				doodad -> doodad.setPlayerColor(playerColor));
	}

	private static void forDoodads(final War3MapViewer viewer, final String doodadId, final Rectangle area,
			final float centerX, final float centerY, final float radius, final boolean nearestOnly,
			final Consumer<RenderDoodad> action) {
		RenderDoodad nearest = null;
		float nearestDistance = Float.MAX_VALUE;
		for (final RenderDoodad doodad : viewer.doodads) {
			if (!doodad.isOfType(doodadId)) {
				continue;
			}
			final float dx = doodad.getX() - centerX;
			final float dy = doodad.getY() - centerY;
			final float distance = (dx * dx) + (dy * dy);
			if (area != null) {
				if (area.contains(doodad.getX(), doodad.getY())) {
					action.accept(doodad);
				}
			}
			else if (distance <= (radius * radius)) {
				if (nearestOnly) {
					if (distance < nearestDistance) {
						nearestDistance = distance;
						nearest = doodad;
					}
				}
				else {
					action.accept(doodad);
				}
			}
		}
		if (nearest != null) {
			action.accept(nearest);
		}
	}
}
