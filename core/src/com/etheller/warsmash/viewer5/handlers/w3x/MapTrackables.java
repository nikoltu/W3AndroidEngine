package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.etheller.warsmash.viewer5.Camera;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CTrackable;

/**
 * The models of the trackables a script made. A script may make hundreds of thousands, most of them on the same few
 * places, so trackables that share a model and a place share one model; the model is only made while its area is near
 * the camera, and a pointer is only tested against the areas its ray passes through.
 */
final class MapTrackables {
	private static final float CELL = 1024f;
	/** How far a model may reach beyond its place. */
	private static final float REACH = 768f;
	private static final float NEAR_MARGIN = 2048f;
	private static final float RELEASE_EXTRA = 1024f;
	private static final int REFRESH_FRAMES = 6;
	private static final int ACTIVATIONS_PER_REFRESH = 96;
	private static final float MIN_Z = -4096f;
	private static final float MAX_Z = 8192f;

	private final Map<SpotKey, Spot> spotsByKey = new HashMap<>();
	private final Map<Long, Cell> cellsByIndex = new HashMap<>();
	private final List<Cell> cells = new ArrayList<>();
	private final List<Spot> active = new ArrayList<>();
	private final Vector3 center = new Vector3();
	private int frame;

	private static final class SpotKey {
		private final String model;
		private final float x;
		private final float y;
		private final float facing;

		private SpotKey(final CTrackable trackable) {
			this.model = trackable.getModelPath();
			this.x = trackable.getX();
			this.y = trackable.getY();
			this.facing = trackable.getFacing();
		}

		@Override
		public boolean equals(final Object o) {
			if (!(o instanceof SpotKey)) {
				return false;
			}
			final SpotKey other = (SpotKey) o;
			return (Float.floatToIntBits(this.x) == Float.floatToIntBits(other.x))
					&& (Float.floatToIntBits(this.y) == Float.floatToIntBits(other.y))
					&& (Float.floatToIntBits(this.facing) == Float.floatToIntBits(other.facing))
					&& this.model.equals(other.model);
		}

		@Override
		public int hashCode() {
			return (((((this.model.hashCode() * 31) + Float.floatToIntBits(this.x)) * 31) + Float.floatToIntBits(this.y))
					* 31) + Float.floatToIntBits(this.facing);
		}
	}

	/** A model and a place; the first trackable made there is the one a pointer finds. */
	private static final class Spot {
		private final CTrackable owner;
		private final Cell cell;
		private RenderSpellEffect effect;
		private boolean active;
		private boolean unusable;

		private Spot(final CTrackable owner, final Cell cell) {
			this.owner = owner;
			this.cell = cell;
		}
	}

	private static final class Cell {
		private final float centerX;
		private final float centerY;
		private final List<Spot> spots = new ArrayList<>();
		private float minX = Float.MAX_VALUE;
		private float minY = Float.MAX_VALUE;
		private float maxX = -Float.MAX_VALUE;
		private float maxY = -Float.MAX_VALUE;
		private int activeSpots;

		private Cell(final float centerX, final float centerY) {
			this.centerX = centerX;
			this.centerY = centerY;
		}
	}

	void add(final CTrackable trackable) {
		if (trackable.getModelPath().isEmpty()) {
			return;
		}
		final SpotKey key = new SpotKey(trackable);
		if (this.spotsByKey.containsKey(key)) {
			return;
		}
		final int cx = (int) Math.floor(trackable.getX() / CELL);
		final int cy = (int) Math.floor(trackable.getY() / CELL);
		final long index = (((long) cx) << 32) | (cy & 0xffffffffL);
		Cell cell = this.cellsByIndex.get(index);
		if (cell == null) {
			cell = new Cell((cx + 0.5f) * CELL, (cy + 0.5f) * CELL);
			this.cellsByIndex.put(index, cell);
			this.cells.add(cell);
		}
		final Spot spot = new Spot(trackable, cell);
		this.spotsByKey.put(key, spot);
		cell.spots.add(spot);
		cell.minX = Math.min(cell.minX, trackable.getX());
		cell.maxX = Math.max(cell.maxX, trackable.getX());
		cell.minY = Math.min(cell.minY, trackable.getY());
		cell.maxY = Math.max(cell.maxY, trackable.getY());
	}

	int activeCount() {
		return this.active.size();
	}

	void update(final War3MapViewer viewer, final float deltaTime) {
		if (this.cells.isEmpty()) {
			return;
		}
		if ((this.frame++ % REFRESH_FRAMES) == 0) {
			refresh(viewer);
		}
		for (int i = this.active.size() - 1; i >= 0; i--) {
			final Spot spot = this.active.get(i);
			if (spot.effect.updateAnimations(viewer, deltaTime)) {
				// the model is gone for good; the place stays but has nothing to show
				spot.unusable = true;
				drop(spot, i);
			}
		}
	}

	private void refresh(final War3MapViewer viewer) {
		final Camera camera = viewer.worldScene.camera;
		int budget = ACTIVATIONS_PER_REFRESH;
		final float radius = (CELL * 0.7072f) + REACH + NEAR_MARGIN;
		for (int c = 0, n = this.cells.size(); c < n; c++) {
			final Cell cell = this.cells.get(c);
			this.center.set(cell.centerX, cell.centerY, 0);
			if (camera.testSphere(this.center, radius)) {
				final List<Spot> spots = cell.spots;
				for (int i = 0, m = spots.size(); (i < m) && (budget > 0); i++) {
					final Spot spot = spots.get(i);
					if (!spot.active && !spot.unusable) {
						budget--;
						activate(viewer, spot);
					}
				}
			}
			else if ((cell.activeSpots > 0) && !camera.testSphere(this.center, radius + RELEASE_EXTRA)) {
				for (int i = this.active.size() - 1; i >= 0; i--) {
					final Spot spot = this.active.get(i);
					if (spot.cell == cell) {
						if (spot.effect.release()) {
							drop(spot, i);
						}
					}
				}
			}
		}
	}

	private void activate(final War3MapViewer viewer, final Spot spot) {
		final CTrackable trackable = spot.owner;
		final RenderSpellEffect effect = MapViewerEffects.createSpecialEffect(viewer, trackable.getModelPath(),
				trackable.getX(), trackable.getY(), (float) Math.toRadians(trackable.getFacing()), false);
		if (effect == null) {
			spot.unusable = true;
			return;
		}
		// a trackable stays on the map; its model must not die with its animation queue
		effect.setKillWhenDone(false);
		spot.effect = effect;
		spot.active = true;
		spot.cell.activeSpots++;
		this.active.add(spot);
	}

	private void drop(final Spot spot, final int activeIndex) {
		this.active.remove(activeIndex);
		spot.cell.activeSpots--;
		spot.active = false;
		spot.effect = null;
	}

	/**
	 * The trackable whose model is under the ray, nearest to its origin, or null. Models are pointed at by their mesh,
	 * as the game does; a model without one is pointed at by its bounds.
	 */
	CTrackable pick(final Ray ray, final Vector3 intersection, final Camera camera) {
		if (this.active.isEmpty()) {
			return null;
		}
		CTrackable picked = null;
		float pickedDistance = Float.MAX_VALUE;
		for (int c = 0, n = this.cells.size(); c < n; c++) {
			final Cell cell = this.cells.get(c);
			if ((cell.activeSpots == 0) || !rayTouches(ray, cell)) {
				continue;
			}
			for (int i = 0, m = cell.spots.size(); i < m; i++) {
				final Spot spot = cell.spots.get(i);
				if (!spot.active) {
					continue;
				}
				final MdxComplexInstance instance = spot.effect.getModelInstance();
				if ((instance == null) || !instance.shown() || !instance.isVisible(camera)) {
					continue;
				}
				final boolean hit = ((MdxModel) instance.model).geosets.isEmpty()
						? instance.intersectRayBounds(ray, intersection)
						: instance.intersectRayWithCollision(ray, intersection, true, true);
				if (hit) {
					final float distance = intersection.dst2(ray.origin);
					if ((distance < pickedDistance)
							|| ((distance == pickedDistance) && (picked != null)
									&& (spot.owner.getHandleId() < picked.getHandleId()))) {
						pickedDistance = distance;
						picked = spot.owner;
					}
				}
			}
		}
		return picked;
	}

	/** Whether the ray passes through the area the cell's models stand in. */
	private static boolean rayTouches(final Ray ray, final Cell cell) {
		float tMin = 0;
		float tMax = Float.MAX_VALUE;
		for (int axis = 0; axis < 3; axis++) {
			final float origin;
			final float direction;
			final float low;
			final float high;
			if (axis == 0) {
				origin = ray.origin.x;
				direction = ray.direction.x;
				low = cell.minX - REACH;
				high = cell.maxX + REACH;
			}
			else if (axis == 1) {
				origin = ray.origin.y;
				direction = ray.direction.y;
				low = cell.minY - REACH;
				high = cell.maxY + REACH;
			}
			else {
				origin = ray.origin.z;
				direction = ray.direction.z;
				low = MIN_Z;
				high = MAX_Z;
			}
			if (Math.abs(direction) < 1e-9f) {
				if ((origin < low) || (origin > high)) {
					return false;
				}
			}
			else {
				float t1 = (low - origin) / direction;
				float t2 = (high - origin) / direction;
				if (t1 > t2) {
					final float swap = t1;
					t1 = t2;
					t2 = swap;
				}
				tMin = Math.max(tMin, t1);
				tMax = Math.min(tMax, t2);
				if (tMin > tMax) {
					return false;
				}
			}
		}
		return true;
	}
}
