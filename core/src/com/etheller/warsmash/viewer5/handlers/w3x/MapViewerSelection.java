package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.RenderMathUtils;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.Texture;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.SelectionCircleSize;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.PathingType;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.Terrain.Splat;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.Terrain;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidgetFilterFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CTrackable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

/**
 * Selecting, highlighting and picking units, trees and trackables in the world.
 */
final class MapViewerSelection {
	private MapViewerSelection() {
	}

	static void showTrackable(final War3MapViewer viewer, final CTrackable trackable) {
		final RenderSpellEffect model = viewer.addSpecialEffect(trackable.getModelPath(), trackable.getX(), trackable.getY(),
				(float) Math.toRadians(trackable.getFacing()));
		if (model != null) {
			viewer.projectiles.remove(model);
			viewer.trackableModels.add(model);
			viewer.trackableOwners.add(trackable);
		}
	}

	/**
	 * The trackable whose model is under the screen point, nearest to the camera, or null. Trackables are pointed at
	 * by their model's mesh, as the game does; a model without one is pointed at by its bounds.
	 */
	static CTrackable rayPickTrackable(final War3MapViewer viewer, final float x, final float y) {
		if (viewer.trackableModels.isEmpty()) {
			return null;
		}
		final float[] ray = War3MapViewer.rayHeap;
		War3MapViewer.mousePosHeap.set(x, y);
		viewer.worldScene.camera.screenToWorldRay(ray, War3MapViewer.mousePosHeap);
		War3MapViewer.gdxRayHeap.set(ray[0], ray[1], ray[2], ray[3] - ray[0], ray[4] - ray[1], ray[5] - ray[2]);
		War3MapViewer.gdxRayHeap.direction.nor();
		CTrackable picked = null;
		float pickedDistance = Float.MAX_VALUE;
		for (int i = 0, n = viewer.trackableModels.size(); i < n; i++) {
			final MdxComplexInstance instance = viewer.trackableModels.get(i).getModelInstance();
			if ((instance == null) || !instance.shown() || !instance.isVisible(viewer.worldScene.camera)) {
				continue;
			}
			final boolean hit = ((MdxModel) instance.model).geosets.isEmpty()
					? instance.intersectRayBounds(War3MapViewer.gdxRayHeap, War3MapViewer.intersectionHeap)
					: instance.intersectRayWithCollision(War3MapViewer.gdxRayHeap, War3MapViewer.intersectionHeap, true, true);
			if (hit) {
				final float distance = War3MapViewer.intersectionHeap.dst2(War3MapViewer.gdxRayHeap.origin);
				if (distance < pickedDistance) {
					pickedDistance = distance;
					picked = viewer.trackableOwners.get(i);
				}
			}
		}
		return picked;
	}

	static void deselect(final War3MapViewer viewer) {
		for (final String key : viewer.selectedSplatModelKeys) {
			viewer.terrain.removeSplatBatchModel(key);
		}
		for (final RenderWidget unit : viewer.selected) {
			unit.unassignSelectionCircle();
		}
		viewer.selectedSplatModelKeys.clear();
		viewer.selected.clear();
	}

	static void doUnselectUnit(final War3MapViewer viewer, final RenderWidget widget) {
		if (viewer.selected.remove(widget)) {
			widget.getSelectionCircle();
		}
	}

	static void doSelectUnit(final War3MapViewer viewer, final List<RenderWidget> units) {
		deselect(viewer);
		if (units.isEmpty()) {
			return;
		}

		final Map<String, Terrain.Splat> splats = new HashMap<String, Terrain.Splat>();
		for (final RenderWidget unit : units) {
			if (unit.getSelectionScale() > 0) {
				String allyKey = "n:";
				final float selectionSize = unit.getSelectionScale();
				String path = null;
				for (int i = 0; i < viewer.selectionCircleSizes.size(); i++) {
					final SelectionCircleSize selectionCircleSize = viewer.selectionCircleSizes.get(i);
					if ((selectionSize < selectionCircleSize.size) || (i == (viewer.selectionCircleSizes.size() - 1))) {
						path = selectionCircleSize.texture;
						break;
					}
				}
				if (!path.toLowerCase().endsWith(".blp")) {
					path += ".blp";
				}
				if (unit instanceof RenderUnit) {
					final int selectedUnitPlayerIndex = ((RenderUnit) unit).getSimulationUnit().getFakePlayerIndex();
					final CPlayer localPlayer = viewer.simulation.getPlayer(viewer.localPlayerIndex);
					if (!localPlayer.hasAlliance(selectedUnitPlayerIndex, CAllianceType.PASSIVE)) {
						allyKey = "e:";
					} else if (localPlayer.hasAlliance(((RenderUnit) unit).getSimulationUnit().getPlayerIndex(),
							CAllianceType.SHARED_CONTROL)
							|| localPlayer.hasAlliance(selectedUnitPlayerIndex, CAllianceType.SHARED_CONTROL)) {
						allyKey = "f:";
					}
				}
				path = allyKey + path;
				if (unit.isShowSelectionCircleAboveWater()) {
					path = path + ":abovewater";
				}
				final SplatModel splatModel = viewer.terrain.getSplatModel("selection:" + path);
				if (splatModel != null) {
					final float x = unit.getX();
					final float y = unit.getY();
					final SplatMover splatInstance = splatModel.add(x - (selectionSize / 2), y - (selectionSize / 2),
							x + (selectionSize / 2), y + (selectionSize / 2), 5, viewer.terrain.centerOffset);
					unit.assignSelectionCircle(splatInstance);
					if (unit.getInstance().hidden()) {
						splatInstance.hide();
					}
				} else {
					if (!splats.containsKey(path)) {
						splats.put(path, new Splat());
					}
					final float x = unit.getX();
					final float y = unit.getY();
					Log.info("Selecting a unit at " + x + "," + y);
					if (unit.isShowSelectionCircleAboveWater()) {
						splats.get(path).aboveWater = true;
					}
					splats.get(path).locations.add(new float[] { x - (selectionSize / 2), y - (selectionSize / 2),
							x + (selectionSize / 2), y + (selectionSize / 2), 5 });
					splats.get(path).unitMapping.add(new Consumer<SplatModel.SplatMover>() {
						@Override
						public void accept(final SplatMover t) {
							unit.assignSelectionCircle(t);
							if (unit.getInstance().hidden()) {
								t.hide();
							}
						}
					});
				}
			}
			viewer.selected.add(unit);
		}
		for (final Map.Entry<String, Terrain.Splat> entry : splats.entrySet()) {
			final String path = entry.getKey();
			String filePath = path.substring(2);
			if (filePath.endsWith(":abovewater")) {
				filePath = filePath.substring(0, filePath.length() - 11);
			}
			final String allyKey = path.substring(0, 2);
			final Splat locations = entry.getValue();
			final SplatModel model = new SplatModel(Gdx.gl30, (Texture) viewer.load(filePath, PathSolver.DEFAULT, null),
					locations.locations, viewer.terrain.centerOffset, locations.unitMapping, true, false, true,
					locations.aboveWater);
			switch (allyKey) {
			case "e:":
				model.color[0] = viewer.selectionCircleColorEnemy.r;
				model.color[1] = viewer.selectionCircleColorEnemy.g;
				model.color[2] = viewer.selectionCircleColorEnemy.b;
				model.color[3] = viewer.selectionCircleColorEnemy.a;
				break;
			case "f:":
				model.color[0] = viewer.selectionCircleColorFriend.r;
				model.color[1] = viewer.selectionCircleColorFriend.g;
				model.color[2] = viewer.selectionCircleColorFriend.b;
				model.color[3] = viewer.selectionCircleColorFriend.a;
				break;
			default:
				model.color[0] = viewer.selectionCircleColorNeutral.r;
				model.color[1] = viewer.selectionCircleColorNeutral.g;
				model.color[2] = viewer.selectionCircleColorNeutral.b;
				model.color[3] = viewer.selectionCircleColorNeutral.a;
				break;
			}
			viewer.terrain.addSplatBatchModel("selection:" + path, model);
			viewer.selectedSplatModelKeys.add("selection:" + path);
		}
	}

	static void clearUnitMouseOverHighlight(final War3MapViewer viewer, final RenderWidget unit) {
		viewer.mouseHighlightWidgets.remove(unit);
		final SplatMover selectionPreviewHighlight = unit.getSelectionPreviewHighlight();
		if (selectionPreviewHighlight != null) {
			selectionPreviewHighlight.destroy(Gdx.gl30, viewer.terrain.centerOffset);
		}
		unit.unassignSelectionPreviewHighlight();
	}

	static void clearUnitMouseOverHighlight(final War3MapViewer viewer) {
		for (final String modelKey : viewer.mouseHighlightSplatModelKeys) {
			viewer.terrain.removeSplatBatchModel(modelKey);
		}
		for (final RenderWidget widget : viewer.mouseHighlightWidgets) {
			widget.unassignSelectionPreviewHighlight();
		}
		viewer.mouseHighlightSplatModelKeys.clear();
		viewer.mouseHighlightWidgets.clear();
	}

	static void showUnitMouseOverHighlight(final War3MapViewer viewer, final RenderWidget unit) {
		final Map<String, Terrain.Splat> splats = new HashMap<String, Terrain.Splat>();
		if (unit.getSelectionScale() > 0) {
			String allyKey = "n:";
			final float selectionSize = unit.getSelectionScale();
			String path = null;
			for (int i = 0; i < viewer.selectionCircleSizes.size(); i++) {
				final SelectionCircleSize selectionCircleSize = viewer.selectionCircleSizes.get(i);
				if ((selectionSize < selectionCircleSize.size) || (i == (viewer.selectionCircleSizes.size() - 1))) {
					path = selectionCircleSize.texture;
					break;
				}
			}
			if (!path.toLowerCase().endsWith(".blp")) {
				path += ".blp";
			}
			if (unit instanceof RenderUnit) {
				final CUnit simulationUnit = ((RenderUnit) unit).getSimulationUnit();
				final int selectedUnitPlayerIndex = simulationUnit.getFakePlayerIndex();
				final CPlayer localPlayer = viewer.simulation.getPlayer(viewer.localPlayerIndex);
				if (!localPlayer.hasAlliance(selectedUnitPlayerIndex, CAllianceType.PASSIVE)) {
					allyKey = "e:";
				} else if (localPlayer.hasAlliance(simulationUnit.getPlayerIndex(), CAllianceType.SHARED_CONTROL)
						|| localPlayer.hasAlliance(selectedUnitPlayerIndex, CAllianceType.SHARED_CONTROL)) {
					allyKey = "f:";
				}
			}
			path = allyKey + path;
			if (unit.isShowSelectionCircleAboveWater()) {
				path = path + ":abovewater";
			}
			final SplatModel splatModel = viewer.terrain.getSplatModel("mouseover:" + path);
			if (splatModel != null) {
				final float x = unit.getX();
				final float y = unit.getY();
				final SplatMover splatInstance = splatModel.add(x - (selectionSize / 2), y - (selectionSize / 2),
						x + (selectionSize / 2), y + (selectionSize / 2), 4, viewer.terrain.centerOffset);
				unit.assignSelectionPreviewHighlight(splatInstance);
				if (unit.getInstance().hidden()) {
					splatInstance.hide();
				}
			} else {
				if (!splats.containsKey(path)) {
					splats.put(path, new Splat());
				}
				final float x = unit.getX();
				final float y = unit.getY();
				if (unit.isShowSelectionCircleAboveWater()) {
					splats.get(path).aboveWater = true;
				}
				splats.get(path).locations.add(new float[] { x - (selectionSize / 2), y - (selectionSize / 2),
						x + (selectionSize / 2), y + (selectionSize / 2), 4 });
				splats.get(path).unitMapping.add(new Consumer<SplatModel.SplatMover>() {
					@Override
					public void accept(final SplatMover t) {
						unit.assignSelectionPreviewHighlight(t);
						if (unit.getInstance().hidden()) {
							t.hide();
						}
					}
				});
			}
		}
		viewer.mouseHighlightWidgets.add(unit);
		for (final Map.Entry<String, Terrain.Splat> entry : splats.entrySet()) {
			final String path = entry.getKey();
			String filePath = path.substring(2);
			if (filePath.endsWith(":abovewater")) {
				filePath = filePath.substring(0, filePath.length() - 11);
			}
			final String allyKey = path.substring(0, 2);
			final Splat locations = entry.getValue();
			final SplatModel model = new SplatModel(Gdx.gl30, (Texture) viewer.load(filePath, PathSolver.DEFAULT, null),
					locations.locations, viewer.terrain.centerOffset, locations.unitMapping, true, false, true,
					locations.aboveWater);
			switch (allyKey) {
			case "e:":
				model.color[0] = viewer.selectionCircleColorEnemy.r;
				model.color[1] = viewer.selectionCircleColorEnemy.g;
				model.color[2] = viewer.selectionCircleColorEnemy.b;
				model.color[3] = viewer.selectionCircleColorEnemy.a * 0.5f;
				break;
			case "f:":
				model.color[0] = viewer.selectionCircleColorFriend.r;
				model.color[1] = viewer.selectionCircleColorFriend.g;
				model.color[2] = viewer.selectionCircleColorFriend.b;
				model.color[3] = viewer.selectionCircleColorFriend.a * 0.5f;
				break;
			default:
				model.color[0] = viewer.selectionCircleColorNeutral.r;
				model.color[1] = viewer.selectionCircleColorNeutral.g;
				model.color[2] = viewer.selectionCircleColorNeutral.b;
				model.color[3] = viewer.selectionCircleColorNeutral.a * 0.5f;
				break;
			}
			viewer.mouseHighlightSplatModelKeys.add("mouseover:" + path);
			viewer.terrain.addSplatBatchModel("mouseover:" + path, model);
		}
	}

	static void getClickLocation(final War3MapViewer viewer, final Vector3 out, final int screenX, final int screenY,
			final boolean intersectWithWater, final boolean pushResultZAboveWater) {
		final float[] ray = War3MapViewer.rayHeap;
		War3MapViewer.mousePosHeap.set(screenX, screenY);
		viewer.worldScene.camera.screenToWorldRay(ray, War3MapViewer.mousePosHeap);
		War3MapViewer.gdxRayHeap.set(ray[0], ray[1], ray[2], ray[3] - ray[0], ray[4] - ray[1], ray[5] - ray[2]);
		War3MapViewer.gdxRayHeap.direction.nor();// needed for libgdx
		if (intersectWithWater) {
			RenderMathUtils.intersectRayTriangles(War3MapViewer.gdxRayHeap, viewer.terrain.softwareWaterAndGroundMesh.vertices,
					viewer.terrain.softwareWaterAndGroundMesh.indices, 3, out);
		} else {
			RenderMathUtils.intersectRayTriangles(War3MapViewer.gdxRayHeap, viewer.terrain.softwareGroundMesh.vertices,
					viewer.terrain.softwareGroundMesh.indices, 3, out);
		}
		War3MapViewer.rectangleHeap.set(Math.min(out.x, War3MapViewer.gdxRayHeap.origin.x), Math.min(out.y, War3MapViewer.gdxRayHeap.origin.y),
				Math.abs(out.x - War3MapViewer.gdxRayHeap.origin.x), Math.abs(out.y - War3MapViewer.gdxRayHeap.origin.y));
		viewer.walkableObjectsTree.intersect(War3MapViewer.rectangleHeap, viewer.walkablesIntersectionFinder.reset(War3MapViewer.gdxRayHeap));
		if (viewer.walkablesIntersectionFinder.found) {
			out.set(viewer.walkablesIntersectionFinder.intersection);
		} else {
			final float oldZ = out.z;
			out.z = Math.max(viewer.getWalkableRenderHeight(out.x, out.y), viewer.terrain.getGroundHeight(out.x, out.y));

			if (pushResultZAboveWater) {
				out.z = Math.max(out.z, oldZ);
				final short pathing = viewer.simulation.getPathingGrid().getPathing(out.x, out.y);
				if (PathingGrid.isPathingFlag(pathing, PathingType.SWIMMABLE)
						&& !PathingGrid.isPathingFlag(pathing, PathingType.WALKABLE)) {
					out.z = Math.max(out.z, viewer.terrain.getWaterHeight(out.x, out.y));
				}
			}
		}
	}

	static void getClickLocationOnZPlane(final War3MapViewer viewer, final Vector3 out, final int screenX, final int screenY, final float worldZ) {
		final float[] ray = War3MapViewer.rayHeap;
		War3MapViewer.mousePosHeap.set(screenX, screenY);
		viewer.worldScene.camera.screenToWorldRay(ray, War3MapViewer.mousePosHeap);
		War3MapViewer.gdxRayHeap.set(ray[0], ray[1], ray[2], ray[3] - ray[0], ray[4] - ray[1], ray[5] - ray[2]);
		War3MapViewer.gdxRayHeap.direction.nor();// needed for libgdx
		War3MapViewer.planeHeap.set(0, 0, -1, worldZ);
		Intersector.intersectRayPlane(War3MapViewer.gdxRayHeap, War3MapViewer.planeHeap, out);
	}

	static void showConfirmation(final War3MapViewer viewer, final Vector3 position, final float red, final float green, final float blue) {
		viewer.confirmationInstance.show();
		viewer.confirmationInstance.setSequence(0);
		viewer.confirmationInstance.setLocation(position);
		viewer.worldScene.instanceMoved(viewer.confirmationInstance, position.x, position.y);
		viewer.confirmationInstance.vertexColor[0] = red;
		viewer.confirmationInstance.vertexColor[1] = green;
		viewer.confirmationInstance.vertexColor[2] = blue;
	}

	static RenderWidget rayPickUnit(final War3MapViewer viewer, final float x, final float y) {
		return rayPickUnit(viewer, x, y, CWidgetFilterFunction.ACCEPT_ALL);
	}

	static RenderWidget rayPickUnit(final War3MapViewer viewer, final float x, final float y, final CWidgetFilterFunction filter) {
		final float[] ray = War3MapViewer.rayHeap;
		War3MapViewer.mousePosHeap.set(x, y);
		viewer.worldScene.camera.screenToWorldRay(ray, War3MapViewer.mousePosHeap);
		War3MapViewer.gdxRayHeap.set(ray[0], ray[1], ray[2], ray[3] - ray[0], ray[4] - ray[1], ray[5] - ray[2]);
		War3MapViewer.gdxRayHeap.direction.nor();// needed for libgdx

		RenderWidget entity = null;
		War3MapViewer.intersectionHeap2.set(ray[3], ray[4], ray[5]);
		for (final RenderWidget unit : viewer.widgets) {
			final MdxComplexInstance instance = unit.getInstance();
			if (instance.shown() && instance.isVisible(viewer.worldScene.camera)
					&& instance.intersectRayWithCollisionSimple(War3MapViewer.gdxRayHeap, War3MapViewer.intersectionHeap)) {
				if (filter.call(unit.getSimulationWidget())) {
					final float groundHeight = viewer.terrain.getGroundHeight(War3MapViewer.intersectionHeap.x, War3MapViewer.intersectionHeap.y);
					if (War3MapViewer.intersectionHeap.z > groundHeight) {
						if ((entity == null) && !unit.isIntersectedOnMeshAlways()) {
							entity = unit;
						} else {
							if (instance.intersectRayWithMeshSlow(War3MapViewer.gdxRayHeap, War3MapViewer.intersectionHeap)) {
								if (War3MapViewer.intersectionHeap.z > viewer.terrain.getGroundHeight(War3MapViewer.intersectionHeap.x,
										War3MapViewer.intersectionHeap.y)) {
									viewer.worldScene.camera.worldToCamera(War3MapViewer.intersectionHeap, War3MapViewer.intersectionHeap);
									if ((entity == null) || (War3MapViewer.intersectionHeap.z < War3MapViewer.intersectionHeap2.z)) {
										entity = unit;
										War3MapViewer.intersectionHeap2.set(War3MapViewer.intersectionHeap);
									}
								}
							}
						}
					}
				}
			}
		}
		if (entity == null) {
			entity = pickConstructionByFootprint(viewer, x, y, filter);
		}
		return entity;
	}

	/**
	 * A building still rising out of the ground (Night Elf birth animation) has its collision shapes below the
	 * terrain, so the model ray test misses it; the original still selects it by clicking where it stands.
	 */
	static RenderWidget pickConstructionByFootprint(final War3MapViewer viewer, final float x, final float y,
			final CWidgetFilterFunction filter) {
		getClickLocation(viewer, viewer.constructionPickHeap, (int) x, (int) y, false, false);
		final float pickX = viewer.constructionPickHeap.x;
		final float pickY = viewer.constructionPickHeap.y;
		RenderWidget best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final RenderWidget widget : viewer.widgets) {
			if (!(widget instanceof RenderUnit)) {
				continue;
			}
			final CUnit unit = ((RenderUnit) widget).getSimulationUnit();
			if (!unit.isBuilding() || !unit.isConstructing() || !widget.getInstance().shown()
					|| !filter.call(unit)) {
				continue;
			}
			final float radius = Math.max(unit.getUnitType().getCollisionSize(), 48f);
			final float dx = unit.getX() - pickX;
			final float dy = unit.getY() - pickY;
			final float distance = (dx * dx) + (dy * dy);
			if ((distance <= (radius * radius)) && (distance < bestDistance)) {
				best = widget;
				bestDistance = distance;
			}
		}
		return best;
	}

	/**
	 * A fingertip covers more ground than a trunk, so a tap that misses every tree model takes the tree nearest the
	 * touched ground point, when that point lies within the tree's selection circle widened by the finger tolerance.
	 */
	static RenderWidget pickNearestTree(final War3MapViewer viewer, final float x, final float y, final float tolerancePx,
			final CWidgetFilterFunction filter) {
		getClickLocation(viewer, viewer.constructionPickHeap, (int) x, (int) y, false, false);
		final float pickX = viewer.constructionPickHeap.x;
		final float pickY = viewer.constructionPickHeap.y;
		getClickLocation(viewer, viewer.constructionPickHeap, (int) (x + tolerancePx), (int) y, false, false);
		final float tolerance = (float) Math.hypot(viewer.constructionPickHeap.x - pickX,
				viewer.constructionPickHeap.y - pickY);
		RenderWidget best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final RenderWidget widget : viewer.widgets) {
			if (!(widget instanceof RenderDestructable)) {
				continue;
			}
			final CDestructable tree = ((RenderDestructable) widget).getSimulationDestructable();
			if (tree.isDead() || !tree.getDestType().getTargetedAs().contains(CTargetType.TREE)
					|| !widget.getInstance().shown() || !widget.getInstance().isVisible(viewer.worldScene.camera)) {
				continue;
			}
			final float radius = (widget.getSelectionScale() / 2) + tolerance;
			final float dx = tree.getX() - pickX;
			final float dy = tree.getY() - pickY;
			final float distance = (dx * dx) + (dy * dy);
			if ((distance <= (radius * radius)) && (distance < bestDistance) && filter.call(tree)) {
				best = widget;
				bestDistance = distance;
			}
		}
		return best;
	}
}
