package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitType;
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

	private final Set<War3ID> seen = new HashSet<>();
	private final ArrayDeque<War3ID> queue = new ArrayDeque<>();
	private int framesToPoll;

	/**
	 * One step a frame: the uploads of the prepared textures of models loaded since, as many as fit in a slice, or else
	 * the model of the next type.
	 */
	void update(final War3MapViewer viewer) {
		if (viewer.hasDeferredTextures()) {
			final long end = System.nanoTime() + UPLOAD_SLICE_NANOS;
			while (viewer.finishReadyDeferredTexture() && (System.nanoTime() < end)) {
			}
			if (viewer.hasDeferredTextures()) {
				return;
			}
		}
		if (--this.framesToPoll <= 0) {
			this.framesToPoll = POLL_FRAMES;
			poll(viewer);
		}
		final War3ID typeId = this.queue.poll();
		if (typeId != null) {
			load(viewer, typeId);
		}
	}

	private void poll(final War3MapViewer viewer) {
		final List<CUnit> units = viewer.simulation.getUnits();
		for (int i = 0, n = units.size(); i < n; i++) {
			final CUnit unit = units.get(i);
			final War3ID[] buildQueue = unit.getBuildQueue();
			final CUnit.QueueItemType[] buildQueueTypes = unit.getBuildQueueTypes();
			for (int j = 0; j < buildQueue.length; j++) {
				if ((buildQueueTypes[j] == CUnit.QueueItemType.UNIT) && (buildQueue[j] != null)) {
					offer(buildQueue[j]);
				}
			}
			final List<War3ID> structures = unit.getUnitType().getStructuresBuilt();
			if (!structures.isEmpty()) {
				final COrder order = unit.getCurrentOrder();
				if (order != null) {
					for (int j = 0, m = structures.size(); j < m; j++) {
						if (structures.get(j).getValue() == order.getOrderId()) {
							offer(structures.get(j));
							break;
						}
					}
				}
			}
		}
	}

	private void offer(final War3ID typeId) {
		if (this.seen.add(typeId)) {
			this.queue.add(typeId);
		}
	}

	private void load(final War3MapViewer viewer, final War3ID typeId) {
		final boolean before = viewer.setDeferTextureLoads(true);
		try {
			final RenderUnitType renderType = viewer.renderUnitTypeData.get(typeId);
			if (renderType != null) {
				renderType.getPortraitModel();
			}
			final CUnitType unitType = viewer.simulation.getUnitData().getUnitType(typeId);
			if (unitType != null) {
				for (final CUnitAttack attack : unitType.getAttacks()) {
					final String art = attack instanceof CUnitAttackMissile
							? ((CUnitAttackMissile) attack).getProjectileArt()
							: attack instanceof CUnitAttackInstant ? ((CUnitAttackInstant) attack).getProjectileArt()
									: null;
					if ((art != null) && !art.isEmpty()) {
						viewer.loadModelMdx(art);
					}
				}
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
