package com.etheller.warsmash.viewer5.handlers.w3x;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import com.badlogic.gdx.math.Vector2;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapRuntimeObjectData;
import com.etheller.warsmash.parsers.w3x.unitsdoo.War3MapUnitsDoo;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.DataErrors;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.BuildingShadow;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.Terrain.Splat;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderFogGhost;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItem;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItemTypeData;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderShadowType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitTypeData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderController;

/**
 * Creates the render side of units and items, also when a saved game is loaded.
 */
final class MapViewerUnits {
	private MapViewerUnits() {
	}

	static void loadUnitsAndItems(final War3MapViewer viewer, final Warcraft3MapRuntimeObjectData modifications, final War3MapW3i mapInformation)
			throws IOException {
		final War3Map mpq = viewer.mapMpq;
		viewer.unitsReady = false;
		viewer.renderUnitTypeData = new RenderUnitTypeData(modifications.getUnits(), mpq, viewer, viewer.unitAckSoundsTable,
				viewer.terrain.uberSplatTable);
		viewer.renderItemTypeData = new RenderItemTypeData(modifications.getItems(), mpq, viewer, viewer.miscData);

		if (viewer.dataSource.has("war3mapUnits.doo") && viewer.getGameData().isLoadUnitsFromWorldEditData()) {
			final War3MapUnitsDoo dooFile = mpq.readUnits(mapInformation);

			// Collect the units and items data.
			for (final com.etheller.warsmash.parsers.w3x.unitsdoo.Unit unit : dooFile.getUnits()) {
				final War3ID unitId = unit.getId();
				final float unitX = unit.getLocation()[0];
				final float unitY = unit.getLocation()[1];
				final float unitZ = unit.getLocation()[2];
				final int playerIndex = unit.getPlayer();
				final int customTeamColor = unit.getCustomTeamColor();
				final float unitAngle = unit.getAngle();
				final int editorConfigHitPointPercent = unit.getHitpoints();
				final int editorConfigManaAmount = unit.getMana();

				final CWidget widgetCreated = createNewUnit(viewer, modifications, unitId, unitX, unitY, playerIndex,
						customTeamColor, unitAngle, editorConfigHitPointPercent, editorConfigManaAmount);
				if (widgetCreated instanceof CUnit) {
					final CUnit unitCreated = (CUnit) widgetCreated;
					if (unit.getGoldAmount() != 0) {
						unitCreated.setGold(unit.getGoldAmount());
					}
				}
			}
		}
		viewer.simulation.unitsLoaded();

		viewer.terrain.loadSplats();

		viewer.unitsReady = true;
		viewer.anyReady = true;
	}

	static CWidget createNewUnit(final War3MapViewer viewer, final Warcraft3MapRuntimeObjectData modifications, final War3ID unitId, float unitX,
			float unitY, final int playerIndex, int customTeamColor, final float unitAngle) {
		return createNewUnit(viewer, modifications, unitId, unitX, unitY, playerIndex, customTeamColor, unitAngle, -1, -1);
	}

	static CWidget createNewUnit(final War3MapViewer viewer, final Warcraft3MapRuntimeObjectData modifications, final War3ID unitId, float unitX,
			float unitY, final int playerIndex, int customTeamColor, final float unitAngle,
			int editorConfigHitPointPercent, int editorConfigManaAmount) {
		Splat buildingUberSplat = null;
		SplatMover buildingUberSplatDynamicIngame = null;
		ArgbImage buildingPathingPixelMap = null;
		BuildingShadow buildingShadowInstance = null;

		// Hardcoded?
		if (War3MapViewer.sloc.equals(unitId)) {
			// path = "Objects\\StartLocation\\StartLocation.mdx";
			viewer.startLocations[playerIndex] = new Vector2(unitX, unitY);
		} else {
			final RenderUnitType renderUnitType = viewer.renderUnitTypeData.get(unitId);
			if (renderUnitType == null) {
				final RenderItemType renderItemType = viewer.renderItemTypeData.get(unitId);
				if (renderItemType != null) {

					final CItem simulationItem = viewer.simulation.internalCreateItem(unitId, unitX, unitY);
					final float unitZ = Math.max(viewer.getWalkableRenderHeight(unitX, unitY),
							viewer.terrain.getGroundHeight(unitX, unitY));
					final RenderItem renderItem = new RenderItem(viewer, renderItemType, unitX, unitY, unitZ, unitAngle,
							simulationItem);
					viewer.widgets.add(renderItem);
					viewer.itemToRenderPeer.put(simulationItem, renderItem);

					renderItem.shadow = createUnitShadow(viewer, renderItemType.getRenderShadowType(), unitX, unitY);
					return simulationItem;

				} else {
					DataErrors.report(DataErrors.UNKNOWN_ID, "unit " + unitId, "Unknown unit ID: " + unitId);
				}
			} else {
				buildingPathingPixelMap = renderUnitType.getBuildingPathingPixelMap();

				final float angle = (float) Math.toDegrees(unitAngle);
				final CUnit simulationUnit = viewer.simulation.internalCreateUnit(unitId, playerIndex, unitX, unitY,
						angle, buildingPathingPixelMap, editorConfigHitPointPercent, editorConfigManaAmount);
				unitX = simulationUnit.getX();
				unitY = simulationUnit.getY();
				if (!renderUnitType.isAllowCustomTeamColor() || (customTeamColor == -1)) {
					if (renderUnitType.getTeamColor() != -1) {
						customTeamColor = renderUnitType.getTeamColor();
					} else {
						customTeamColor = viewer.simulation.getPlayer(playerIndex).getColor();
					}
				}
				final float unitZ = Math.max(viewer.getWalkableRenderHeight(unitX, unitY),
						viewer.terrain.getGroundHeight(unitX, unitY)) + simulationUnit.getFlyHeight();

				final String texturePath = renderUnitType.getUberSplat();
				final float s = renderUnitType.getUberSplatScaleValue();
				if (texturePath != null) {
					if (viewer.unitsReady) {
						buildingUberSplatDynamicIngame = addUberSplatIngame(viewer, unitX, unitY, texturePath, s);
					} else {
						if (!viewer.terrain.splats.containsKey(texturePath)) {
							viewer.terrain.splats.put(texturePath, new Splat());
						}
						final float x = unitX;
						final float y = unitY;
						buildingUberSplat = viewer.terrain.splats.get(texturePath);
						buildingUberSplat.locations.add(new float[] { x - s, y - s, x + s, y + s, 1 });
					}
				}

				final String buildingShadow = renderUnitType.getBuildingShadow();
				if (buildingShadow != null) {
					buildingShadowInstance = viewer.terrain.addShadow(buildingShadow, unitX, unitY);
				}

				final RenderUnit renderUnit = new RenderUnit(viewer, unitX, unitY, unitZ, customTeamColor, simulationUnit,
						renderUnitType, buildingShadowInstance, viewer.selectionCircleScaleFactor,
						renderUnitType.getAnimationWalkSpeed(), renderUnitType.getAnimationRunSpeed(),
						renderUnitType.getScalingValue());
				viewer.unitToRenderPeer.put(simulationUnit, renderUnit);
				viewer.widgets.add(renderUnit);
				viewer.units.add(renderUnit);
				renderUnit.shadow = createUnitShadow(viewer, renderUnitType.getRenderShadowType(), unitX, unitY);
				if (buildingUberSplat != null) {
					buildingUberSplat.unitMapping.add(new Consumer<SplatModel.SplatMover>() {

						@Override
						public void accept(final SplatMover t) {
							renderUnit.uberSplat = t;
						}
					});
				}
				if (buildingUberSplatDynamicIngame != null) {
					renderUnit.uberSplat = buildingUberSplatDynamicIngame;
				}
				return simulationUnit;

			}
		}
		return null;
	}

	static SplatMover createUnitShadow(final War3MapViewer viewer, final RenderShadowType renderShadowType, final float unitX, final float unitY) {
		if (renderShadowType != null) {
			final float x = unitX - renderShadowType.getX();
			final float y = unitY - renderShadowType.getY();
			return viewer.terrain.addUnitShadowSplat(renderShadowType.getTexture(), x, y, x + renderShadowType.getWidth(),
					y + renderShadowType.getHeight(), 3, 0.5f, false);
		}
		return null;
	}

	static SplatMover addUberSplatIngame(final War3MapViewer viewer, final float unitX, final float unitY, final String texturePath,
			final float s) {
		SplatMover buildingUberSplatDynamicIngame;
		buildingUberSplatDynamicIngame = viewer.terrain.addUberSplat(texturePath, unitX, unitY, 1, s, false, false, false,
				false);
		return buildingUberSplatDynamicIngame;
	}

	static void clearRenderPeersForSavedGame(final War3MapViewer viewer) {
		final CSimulation game = viewer.simulation;
		final SimulationRenderController controller = game.getSimulationRenderController();
		for (final CUnit unit : new ArrayList<>(game.getUnits())) {
			controller.removeUnit(unit);
		}
		for (final RenderFogGhost ghost : viewer.fogGhosts) {
			ghost.remove(viewer);
		}
		viewer.fogGhosts.clear();
		for (final RenderItem item : new ArrayList<>(viewer.itemToRenderPeer.values())) {
			viewer.widgets.remove(item);
			viewer.worldScene.removeInstance(item.instance);
		}
		viewer.itemToRenderPeer.clear();
	}

	/**
	 * Destructables kept their peers (they were read into the objects the map or {@link #createDestructableForSavedGame}
	 * made); units and items get new ones.
	 */
	static void finishLoadingSavedGame(final War3MapViewer viewer, final Collection<CDestructable> preparedDestructables) {
		final Set<CDestructable> current = new HashSet<>(viewer.simulation.getDestructables());
		for (final CDestructable destructable : preparedDestructables) {
			if (!current.contains(destructable) || destructable.isRemovedFromGame()) {
				final RenderDestructable peer = viewer.destructableToRenderPeer.get(destructable);
				if (peer != null) {
					viewer.widgets.remove(peer);
					viewer.decals.remove(peer);
					viewer.simulation.getSimulationRenderController().removeDestructable(destructable);
				}
			}
		}
		for (final CUnit unit : viewer.simulation.getUnits()) {
			createRenderUnitForSavedGame(viewer, unit);
		}
		for (final CItem item : viewer.simulation.getItems()) {
			createRenderItemForSavedGame(viewer, item);
		}
		viewer.terrain.setFogOfWarData(viewer.simulation, viewer.simulation.getPlayer(viewer.localPlayerIndex).getFogOfWar());
		viewer.worldScene.fogFilter = viewer.viewerFogFilter;
	}

	static void createRenderItemForSavedGame(final War3MapViewer viewer, final CItem item) {
		final RenderItemType renderItemType = viewer.renderItemTypeData.get(item.getTypeId());
		if (renderItemType != null) {
			final float x = item.getX();
			final float y = item.getY();
			final float z = Math.max(viewer.getWalkableRenderHeight(x, y), viewer.terrain.getGroundHeight(x, y));
			final RenderItem renderItem = new RenderItem(viewer, renderItemType, x, y, z, 0, item);
			viewer.widgets.add(renderItem);
			viewer.itemToRenderPeer.put(item, renderItem);
			renderItem.shadow = createUnitShadow(viewer, renderItemType.getRenderShadowType(), x, y);
		}
	}

	static void createRenderUnitForSavedGame(final War3MapViewer viewer, final CUnit unit) {
		final RenderUnitType renderUnitType = viewer.renderUnitTypeData.get(unit.getTypeId());
		if (renderUnitType == null) {
			return;
		}
		final float x = unit.getX();
		final float y = unit.getY();
		final int teamColor = renderUnitType.getTeamColor() != -1 ? renderUnitType.getTeamColor()
				: viewer.simulation.getPlayer(unit.getPlayerIndex()).getColor();
		final float z = Math.max(viewer.getWalkableRenderHeight(x, y), viewer.terrain.getGroundHeight(x, y))
				+ unit.getFlyHeight();
		final String buildingShadow = renderUnitType.getBuildingShadow();
		final BuildingShadow shadow = buildingShadow == null ? null : viewer.terrain.addShadow(buildingShadow, x, y);
		final RenderUnit renderUnit = new RenderUnit(viewer, x, y, z, teamColor, unit, renderUnitType, shadow,
				viewer.selectionCircleScaleFactor, renderUnitType.getAnimationWalkSpeed(),
				renderUnitType.getAnimationRunSpeed(), renderUnitType.getScalingValue());
		viewer.unitToRenderPeer.put(unit, renderUnit);
		viewer.widgets.add(renderUnit);
		viewer.units.add(renderUnit);
		renderUnit.shadow = createUnitShadow(viewer, renderUnitType.getRenderShadowType(), x, y);
		if (renderUnitType.getUberSplat() != null) {
			renderUnit.uberSplat = addUberSplatIngame(viewer, x, y, renderUnitType.getUberSplat(),
					renderUnitType.getUberSplatScaleValue());
		}
	}
}
