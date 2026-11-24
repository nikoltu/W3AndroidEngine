package com.etheller.warsmash.viewer5;

import java.util.ArrayList;
import java.util.List;

/**
 * A scene.
 *
 * Every scene has its own list of model instances, and its own camera and
 * viewport.
 *
 * In addition, in Ghostwolf's original code every scene may have its own
 * AudioContext if enableAudio() is called. If audo is enabled, the
 * AudioContext's listener's location will be updated automatically. Note that
 * due to browser policies, this may be done only after user interaction with
 * the web page.
 *
 * In "Warsmash", we are starting from an attempt to replicate Ghostwolf, but
 * audio is always on in LibGDX generally. So we will probably simplify or skip
 * over those behaviors other than a boolean on/off toggle for audio.
 */
public class WorldScene extends Scene {

	/**
	 * Cell edge in world units: 16 map tiles. The camera sees a few thousand units, so a few cells are visible at
	 * once, while a building or a spell effect of several hundred units still lies in one to four cells.
	 */
	public static final int GRID_CELL_SIZE = 16 * 128;

	public Grid grid;
	/** Where the viewer has vision; null shows every instance. */
	public FogFilter fogFilter;
	private final InstanceProcessor instanceProcessor = new InstanceProcessor();
	private final List<ModelInstance> cellInstances = new ArrayList<>();
	/** Instances whose sequence stops at its end; they keep their clock while out of view. */
	private final List<ModelInstance> clockInstances = new ArrayList<>();
	/** Seconds the scene has been updated for; the clocks of lazy instances are caught up to it when they are seen. */
	private double sceneTime;

	public WorldScene(final ModelViewer viewer, final SceneLightManager lightManager) {
		super(viewer, lightManager);
		this.grid = new Grid(-100000, -100000, 200000, 200000, 200000, 200000);
	}

	/**
	 * Replaces the grid by one covering the given area, keeping every instance already in the scene and the order
	 * it had in its cells.
	 */
	public void setGridBounds(final float x, final float y, final int width, final int depth) {
		final List<ModelInstance> existing = new ArrayList<>();
		for (final GridCell cell : this.grid.cells) {
			for (int i = 0, n = cell.instances.size(); i < n; i++) {
				final ModelInstance instance = cell.instances.get(i);
				if (instance.left != -1) {
					instance.left = -1;
					existing.add(instance);
				}
			}
		}
		this.grid = new Grid(x, y, width, depth, GRID_CELL_SIZE, GRID_CELL_SIZE);
		for (int i = 0, n = existing.size(); i < n; i++) {
			existing.get(i).updateSceneGridLocationInfo();
		}
	}

	@Override
	public void instanceMoved(final ModelInstance instance, final float x, final float y) {
		this.grid.moved(instance, x, y);
	}

	@Override
	protected void instanceAdded(final ModelInstance instance) {
		instance.clockTime = this.sceneTime;
		trackClock(instance);
	}

	@Override
	protected void innerRemove(final ModelInstance instance) {
		this.grid.remove(instance);
		if (instance.clockTracked) {
			// Without it the list keeps the instance until the next scene update, which a game that does not draw
			// never runs.
			instance.clockTracked = false;
			final int index = this.clockInstances.lastIndexOf(instance);
			if (index >= 0) {
				final int last = this.clockInstances.size() - 1;
				this.clockInstances.set(index, this.clockInstances.get(last));
				this.clockInstances.remove(last);
			}
		}
	}

	@Override
	public void clear() {
		// First remove references to this scene stored in the instances.
		for (final GridCell cell : this.grid.cells) {
			for (final ModelInstance instance : cell.instances) {
				instance.scene = null;
			}
		}

		// Then remove references to the instances.
		this.grid.clear();
	}

	@Override
	protected void innerUpdate(final float dt, final int frame) {
		this.visibleCells = 0;
		this.visibleInstances = 0;
		this.sceneTime += dt;

		// Update and collect all of the visible instances.
		instanceProcessor.reset(dt, frame);
		for (final GridCell cell : this.grid.cells) {
			if (cell.isVisible(this.camera)) {
				this.visibleCells += 1;

				// An update can move an instance to another cell, so the cell's list is copied first.
				this.cellInstances.clear();
				for (int i = 0, n = cell.instances.size(); i < n; i++) {
					this.cellInstances.add(cell.instances.get(i));
				}
				for (int i = 0, n = this.cellInstances.size(); i < n; i++) {
					instanceProcessor.call(this.cellInstances.get(i));
				}
			}
		}
		advanceClocksOutOfView(dt, frame);
	}

	/** Keeps the clock of an instance running while it is out of view, until its sequence ends. */
	public void trackClock(final ModelInstance instance) {
		if (!instance.clockTracked && !instance.lazyClock && instance.endsSequence()) {
			instance.clockTracked = true;
			this.clockInstances.add(instance);
		}
	}

	/** @see ModelInstance#settleClock */
	public void settleClock(final ModelInstance instance, final boolean advance) {
		final double gap = this.sceneTime - instance.clockTime;
		instance.clockTime = this.sceneTime;
		if (advance && (gap > 0) && instance.rendered && !instance.paused) {
			instance.catchUpClock((float) gap);
		}
	}

	private void advanceClocksOutOfView(final float dt, final int frame) {
		for (int i = this.clockInstances.size() - 1; i >= 0; i--) {
			final ModelInstance instance = this.clockInstances.get(i);
			if ((instance.scene != this) || !instance.endsSequence()) {
				instance.clockTracked = false;
				final int last = this.clockInstances.size() - 1;
				this.clockInstances.set(i, this.clockInstances.get(last));
				this.clockInstances.remove(last);
			}
			else if ((instance.updateFrame < frame) && instance.rendered && !instance.paused) {
				instance.advanceClock(dt);
			}
		}
	}

	/**
	 * Models attached to a hidden one (a unit out of sight) are hidden with it, and effects of the world only show
	 * where the viewer has vision.
	 */
	private boolean isHiddenFromViewer(final ModelInstance instance) {
		if (instance.parent != null) {
			GenericNode root = instance.parent;
			while (root.parent != null) {
				root = root.parent;
			}
			return (root instanceof ModelInstance) && !((ModelInstance) root).rendered;
		}
		if (this.fogFilter == null) {
			return false;
		}
		return (instance.hiddenInFog && !this.fogFilter.isVisible(instance.worldLocation.x, instance.worldLocation.y))
				|| (instance.hiddenInMask && !this.fogFilter.isExplored(instance.worldLocation.x, instance.worldLocation.y));
	}

	public interface FogFilter {
		boolean isVisible(float x, float y);

		/** Whether the point is out of the black mask: in sight or in fog. */
		default boolean isExplored(final float x, final float y) {
			return true;
		}
	}

	private final class InstanceProcessor implements ModelInstanceCallback {
		private float dt;
		private int frame;

		public InstanceProcessor reset(final float dt, final int frame) {
			this.dt = dt;
			this.frame = frame;
			return this;
		}

		@Override
		public void call(final ModelInstance instance) {
//			final ModelInstance instance = cell.instances.get(i);
			if (instance.isWaiting()) {
				// a child (an effect on a unit's attachment point) whose model is not built yet, or not loaded for it
				if (!instance.lazyLoad || !instance.rendered || !instance.isVisible(camera)) {
					return;
				}
				// made empty to cost nothing while out of view; it is loaded where the camera first sees it
				instance.loadNow();
			}
			if (instance.rendered && (instance.cullFrame < frame) && instance.isVisible(camera)) {
				instance.cullFrame = frame;

				if (instance.updateFrame < frame) {
					if (instance.lazyClock) {
						// This frame's time is added by the update itself.
						final double gap = (sceneTime - dt) - instance.clockTime;
						instance.clockTime = sceneTime;
						if (gap > 0) {
							instance.catchUpClock((float) gap);
						}
					}
					instance.update(dt, WorldScene.this);
					if (!instance.rendered) {
						// it became hidden while it updated
						return;
					}
				}
				// Still updated, so that an effect hidden in the fog plays out and ends as it would in sight.
				if (isHiddenFromViewer(instance)) {
					return;
				}

				if (instance.isBatched()) {
					if (currentBatchedInstance < batchedInstances.size()) {
						batchedInstances.set(currentBatchedInstance++, instance);
					}
					else {
						batchedInstances.add(instance);
						currentBatchedInstance++;
					}
				}
				else {
					if (currentInstance < instances.size()) {
						instances.set(currentInstance++, instance);
					}
					else {
						instances.add(instance);
						currentInstance++;
					}
				}

				visibleInstances += 1;
				for (int i = 0, l = instance.childrenInstances.size(); i < l; i++) {
					call(instance.childrenInstances.get(i));
				}
			}
		}
	}
}
