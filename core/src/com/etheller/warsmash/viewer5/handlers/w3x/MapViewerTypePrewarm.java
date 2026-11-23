package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.AssetReadiness;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackInstant;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;

/**
 * Loads the models of the unit types that are about to come into play while the game goes on: the types in production
 * queues and the structures workers are on their way to build, of every player. Made when the first unit of a type
 * appeared, they held that frame up for all of their files, a tenth of a second and more on a desktop; here a type's
 * model is read in one frame and its textures are decoded on helper threads and uploaded one a frame, long before the
 * production is done.
 * <p>
 * Only what is drawn is loaded ahead, which the simulation never reads, so the game plays the same either way.
 */
final class MapViewerTypePrewarm {
	/** Frames between looks at the queues: a production takes seconds, so a few frames late costs nothing. */
	private static final int POLL_FRAMES = 15;
	private static final long UPLOAD_SLICE_NANOS = 1_000_000;

	/** Steps between looks at the queues when the simulation thread does them: a production takes seconds. */
	private static final int SCAN_STEPS = 8;
	/** Time a frame the types of units just made may take before the rest wait for the next frame. */
	private static final long URGENT_SLICE_NANOS = 2_000_000;

	private final AssetReadiness feed = new AssetReadiness();
	private int framesToPoll;
	private int stepsToScan;

	/** What the simulation tells the render side about the types it needs; see {@link AssetReadiness}. */
	AssetReadiness feed() {
		return this.feed;
	}

	/**
	 * A step of the simulation ended: every few steps the queues are looked at and the types in them offered. Called
	 * on the simulation thread, which is the one that may read the units.
	 */
	void onStep(final CSimulation simulation) {
		if (--this.stepsToScan <= 0) {
			this.stepsToScan = SCAN_STEPS;
			scan(simulation, this.feed);
		}
	}

	/** Simulation thread: a unit of the type was made or is about to be. */
	static void offer(final AssetReadiness feed, final War3ID typeId, final CUnitType unitType,
			final boolean urgent) {
		if (!feed.hasArt(typeId)) {
			final List<String> art = new ArrayList<>();
			if (unitType != null) {
				for (final CUnitAttack attack : unitType.getAttacks()) {
					final String name = attack instanceof CUnitAttackMissile
							? ((CUnitAttackMissile) attack).getProjectileArt()
							: attack instanceof CUnitAttackInstant ? ((CUnitAttackInstant) attack).getProjectileArt()
									: null;
					if ((name != null) && !name.isEmpty()) {
						art.add(name);
					}
				}
			}
			feed.setArt(typeId, art.toArray(new String[0]));
		}
		if (urgent) {
			feed.offerUrgent(typeId);
		}
		else {
			feed.offerPlanned(typeId);
		}
	}

	/**
	 * One step a frame: the uploads of the prepared textures of models loaded since, as many as fit in a slice, or else
	 * the model of the next type.
	 */
	void update(final War3MapViewer viewer) {
		final long urgentEnd = System.nanoTime() + URGENT_SLICE_NANOS;
		War3ID made;
		while ((made = this.feed.pollUrgent()) != null) {
			if (!viewer.renderUnitTypeData.isModelReady(made)) {
				load(viewer, made);
			}
			if (System.nanoTime() >= urgentEnd) {
				break;
			}
		}
		if (viewer.hasDeferredTextures()) {
			final long end = System.nanoTime() + UPLOAD_SLICE_NANOS;
			while (viewer.finishReadyDeferredTexture() && (System.nanoTime() < end)) {
			}
			if (viewer.hasDeferredTextures()) {
				return;
			}
		}
		if (!viewer.isSimulationThreaded() && (--this.framesToPoll <= 0)) {
			this.framesToPoll = POLL_FRAMES;
			scan(viewer.simulation, this.feed);
		}
		final War3ID typeId = this.feed.pollPlanned();
		if (typeId != null) {
			load(viewer, typeId);
		}
	}

	private static void scan(final CSimulation simulation, final AssetReadiness feed) {
		final List<CUnit> units = simulation.getUnits();
		for (int i = 0, n = units.size(); i < n; i++) {
			final CUnit unit = units.get(i);
			final War3ID[] buildQueue = unit.getBuildQueue();
			final CUnit.QueueItemType[] buildQueueTypes = unit.getBuildQueueTypes();
			for (int j = 0; j < buildQueue.length; j++) {
				if ((buildQueueTypes[j] == CUnit.QueueItemType.UNIT) && (buildQueue[j] != null)) {
					offer(feed, buildQueue[j], simulation.getUnitData().getUnitType(buildQueue[j]), false);
				}
			}
			final List<War3ID> structures = unit.getUnitType().getStructuresBuilt();
			if (!structures.isEmpty()) {
				final COrder order = unit.getCurrentOrder();
				if (order != null) {
					for (int j = 0, m = structures.size(); j < m; j++) {
						if (structures.get(j).getValue() == order.getOrderId()) {
							offer(feed, structures.get(j), simulation.getUnitData().getUnitType(structures.get(j)), false);
							break;
						}
					}
				}
			}
		}
	}

	private void load(final War3MapViewer viewer, final War3ID typeId) {
		final boolean before = viewer.setDeferTextureLoads(true);
		try {
			final RenderUnitType renderType = viewer.renderUnitTypeData.get(typeId);
			if (renderType != null) {
				renderType.getPortraitModel();
			}
			for (final String art : this.feed.art(typeId)) {
				viewer.loadModelMdx(art);
			}
			final UnitSoundset soundset = viewer.renderUnitTypeData.getSoundset(typeId);
			if (soundset != null) {
				soundset.ready.prefetch();
				soundset.what.prefetch();
			}
		}
		catch (final RuntimeException e) {
			// The type is made again when its unit comes, and fails there as it would have anyway.
			Log.error(e);
		}
		finally {
			viewer.setDeferTextureLoads(before);
		}
	}
}
