package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.List;

import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;

/**
 * Debug check, on with {@code -Dwarsmash.simthread.verify}: after a step it compares a frame, column by column, with
 * what the live simulation says, reading the live objects directly (not through the writer's code). It also checks
 * that rows are sorted and complete and that the fog the frame carries, or the last one it carried when unchanged,
 * is the live fog. It allocates and is slow; it runs on the thread that writes the frame, before publication.
 */
public final class FrameVerifier {
	private static final int DETAIL_LIMIT = 30;
	private static final int PATH_POINTS = UnitFrameState.PATH_POINTS;

	private final int localPlayerIndex;
	private long mismatches;
	private long frames;
	private long framesWithMismatch;
	private byte[] knownFog = new byte[0];
	private int knownFogLength = -1;
	private int stepMismatches;
	private int stepTick;

	public FrameVerifier(final int localPlayerIndex) {
		this.localPlayerIndex = localPlayerIndex;
	}

	public long getMismatches() {
		return this.mismatches;
	}

	public long getFramesChecked() {
		return this.frames;
	}

	public long getFramesWithMismatch() {
		return this.framesWithMismatch;
	}

	/** Checks one frame; returns how many columns or rows disagreed with the live simulation. */
	public int verify(final CSimulation game, final SimFrame frame) {
		this.frames++;
		this.stepMismatches = 0;
		this.stepTick = frame.tick;
		final CPlayer local = (this.localPlayerIndex >= 0) && (this.localPlayerIndex < game.getPlayers().size())
				? game.getPlayer(this.localPlayerIndex)
				: null;
		final CPlayerFogOfWar fog = local == null ? null : local.getFogOfWar();
		same("tick", frame.tick, game.getGameTurnTick());
		same("gameTimeMillis", frame.gameTimeMillis,
				Math.round(game.getGameTurnTick() * 1000.0 * WarsmashConstants.SIMULATION_STEP_TIME));
		same("timeOfDay", frame.timeOfDay, game.getGameTimeOfDay());
		verifyUnits(game, frame.units, local, fog);
		verifyItems(game, frame.items, fog);
		verifyDestructables(game, frame.trees);
		verifyProjectiles(game, frame.projectiles);
		verifyFog(frame, fog);
		if (this.stepMismatches > 0) {
			this.framesWithMismatch++;
		}
		return this.stepMismatches;
	}

	private void verifyUnits(final CSimulation game, final UnitFrameState state, final CPlayer local,
			final CPlayerFogOfWar fog) {
		final List<CUnit> units = game.getUnits();
		final List<CUnit> pending = game.getPendingNewUnits();
		same("units.count", state.count, units.size() + pending.size());
		for (int i = 1; i < state.count; i++) {
			if (state.handle[i] <= state.handle[i - 1]) {
				fail("units.order row " + i + " handle " + state.handle[i] + " after " + state.handle[i - 1]);
			}
		}
		for (int n = 0; n < (units.size() + pending.size()); n++) {
			final CUnit unit = n < units.size() ? units.get(n) : pending.get(n - units.size());
			final int row = state.indexOf(unit.getHandleId());
			if (row < 0) {
				fail("unit " + unit.getHandleId() + " has no row");
				continue;
			}
			final String name = "unit " + unit.getHandleId() + " ";
			same(name + "typeId", state.typeId[row], unit.getTypeId().getValue());
			same(name + "owner", state.owner[row], unit.getPlayerIndex());
			same(name + "x", state.x[row], unit.getX());
			same(name + "y", state.y[row], unit.getY());
			same(name + "z", state.z[row], unit.getFlyHeight());
			same(name + "facing", state.facing[row], unit.getFacing());
			same(name + "speed", state.speed[row],
					(unit.getCurrentBehavior() instanceof com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove)
							? (float) unit.getSpeed()
							: 0f);
			same(name + "scale", state.scale[row], 1f);
			same(name + "movementType", state.movementType[row], (byte) unit.getMovementType().ordinal());
			same(name + "invisLevels", state.invisLevels[row], unit.getInvisLevels());
			final int maxLife = unit.getMaximumLife();
			final int maxMana = unit.getMaximumMana();
			same(name + "hpFraction", state.hpFraction[row], maxLife > 0 ? unit.getLife() / maxLife : 0f);
			same(name + "life", state.life[row], unit.getLife());
			same(name + "manaFraction", state.manaFraction[row], maxMana > 0 ? unit.getMana() / maxMana : 0f);
			final CPlayer owner = game.getPlayer(unit.getPlayerIndex());
			same(name + "playerColor", state.playerColor[row],
					owner == null ? unit.getPlayerIndex() : owner.getColor());
			same(name + "vertexColor", state.vertexColor[row], expectedVertexColor(unit.getVertexColor()));
			same(name + "orderId", state.orderId[row],
					unit.getCurrentOrder() == null ? 0 : unit.getCurrentOrder().getOrderId());
			final boolean queued = unit.getBuildQueueTypes()[0] != null;
			same(name + "queueFirstType", state.queueFirstType[row],
					(queued && (unit.getBuildQueue()[0] != null)) ? unit.getBuildQueue()[0].getValue() : 0);
			same(name + "flags", state.flags[row], expectedFlags(game, unit, local, fog));
			same(name + "targetClass", (int) state.targetClass[row], expectedTargetClass(game, unit, local));
			final float[] path = new float[PATH_POINTS * 2];
			final int count = unit.copyPathPoints(path, 0, PATH_POINTS);
			same(name + "pathCount", (int) state.pathCount[row], count);
			for (int i = 0; i < (count * 2); i++) {
				same(name + "path[" + i + "]", state.path[(row * PATH_POINTS * 2) + i], path[i]);
			}
		}
	}

	private int expectedFlags(final CSimulation game, final CUnit unit, final CPlayer local,
			final CPlayerFogOfWar fog) {
		int flags = 0;
		flags |= unit.isHidden() ? UnitFrameState.HIDDEN : 0;
		flags |= unit.getLife() <= 0 ? UnitFrameState.DEAD : 0;
		final boolean visible;
		if (local == null) {
			visible = true;
		}
		else if (unit.getLife() <= 0) {
			visible = fog.isVisible(game, game.getPathingGrid(), unit.getX(), unit.getY());
		}
		else {
			visible = unit.isVisible(game, local.getId());
		}
		flags |= visible ? UnitFrameState.VISIBLE_TO_LOCAL : 0;
		flags |= unit.isBuilding() ? UnitFrameState.BUILDING : 0;
		flags |= unit.getBuildQueueTypes()[0] != null ? UnitFrameState.BUSY : 0;
		flags |= unit.getHeroData() != null && !unit.getHeroData().isDisabled() ? UnitFrameState.HERO : 0;
		flags |= unit.getMovementType() == MovementType.FLY ? UnitFrameState.FLYING : 0;
		flags |= unit.getClassifications().contains(CUnitClassification.SUMMONED) ? UnitFrameState.SUMMONED : 0;
		return flags;
	}

	private int expectedTargetClass(final CSimulation game, final CUnit unit, final CPlayer local) {
		int result = 0;
		if (local != null) {
			if (unit.getPlayerIndex() == local.getId()) {
				result |= UnitFrameState.TARGET_OWN | UnitFrameState.TARGET_ALLY;
			}
			else if (local.hasAlliance(unit.getPlayerIndex(),
					com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType.PASSIVE)) {
				result |= UnitFrameState.TARGET_ALLY;
			}
			else {
				result |= UnitFrameState.TARGET_ENEMY;
			}
		}
		if (unit.getPlayerIndex() >= (game.getPlayers().size() - 4)) {
			result |= UnitFrameState.TARGET_NEUTRAL;
		}
		if (unit.getTargetedAs().contains(CTargetType.MECHANICAL)) {
			result |= UnitFrameState.TARGET_MECHANICAL;
		}
		if (unit.isInvulnerable()) {
			result |= UnitFrameState.TARGET_INVULNERABLE;
		}
		return result;
	}

	private static int expectedVertexColor(final float[] color) {
		if (color == null) {
			return 0;
		}
		final float alpha = color.length > 3 ? color[3] : 1f;
		return (clamp(alpha) << 24) | (clamp(color[0]) << 16) | (clamp(color[1]) << 8) | clamp(color[2]);
	}

	private static int clamp(final float value) {
		final int scaled = Math.round(value * 255f);
		return scaled < 0 ? 0 : (scaled > 255 ? 255 : scaled);
	}

	private void verifyItems(final CSimulation game, final WorldObjectFrameState state, final CPlayerFogOfWar fog) {
		final List<CItem> items = game.getItems();
		same("items.count", state.count, items.size());
		checkSorted("items", state);
		for (final CItem item : items) {
			final int row = indexOf(state, item.getHandleId());
			if (row < 0) {
				fail("item " + item.getHandleId() + " has no row");
				continue;
			}
			final String name = "item " + item.getHandleId() + " ";
			same(name + "x", state.x[row], item.getX());
			same(name + "y", state.y[row], item.getY());
			same(name + "z", state.z[row], item.getFlyHeight());
			same(name + "life", state.life[row], item.getLife());
			int flags = 0;
			if (!item.isHidden() && ((fog == null) || fog.isVisible(game, game.getPathingGrid(), item.getX(),
					item.getY()))) {
				flags |= WorldObjectFrameState.VISIBLE;
			}
			flags |= item.getLife() <= 0 ? WorldObjectFrameState.DEAD : 0;
			flags |= item.getContainedUnit() != null ? WorldObjectFrameState.CARRIED : 0;
			same(name + "flags", state.flags[row], flags);
		}
	}

	private void verifyDestructables(final CSimulation game, final WorldObjectFrameState state) {
		final List<CDestructable> destructables = game.getDestructables();
		same("trees.count", state.count, destructables.size());
		checkSorted("trees", state);
		for (final CDestructable destructable : destructables) {
			final int row = indexOf(state, destructable.getHandleId());
			if (row < 0) {
				fail("destructable " + destructable.getHandleId() + " has no row");
				continue;
			}
			final String name = "tree " + destructable.getHandleId() + " ";
			same(name + "x", state.x[row], destructable.getX());
			same(name + "y", state.y[row], destructable.getY());
			same(name + "z", state.z[row], destructable.getFlyHeight());
			final float maxLife = destructable.getMaxLife();
			same(name + "life", state.life[row], maxLife > 0 ? destructable.getLife() / maxLife : 0f);
			same(name + "flags", state.flags[row] & ~WorldObjectFrameState.BLIGHTED, WorldObjectFrameState.VISIBLE
					| (destructable.getLife() <= 0 ? WorldObjectFrameState.DEAD : 0));
		}
	}

	private void verifyProjectiles(final CSimulation game, final WorldObjectFrameState state) {
		final List<CEffect> live = game.getProjectiles();
		final List<CEffect> pending = game.getPendingProjectiles();
		int expected = 0;
		for (int i = 0; i < (live.size() + pending.size()); i++) {
			final CEffect effect = i < live.size() ? live.get(i) : pending.get(i - live.size());
			if (effect instanceof CProjectile) {
				expected++;
			}
		}
		same("projectiles.count", state.count, expected);
		checkSorted("projectiles", state);
		// rows are numbered in list order, so the n-th projectile of the lists is the n-th row
		int row = 0;
		for (int i = 0; i < (live.size() + pending.size()); i++) {
			final CEffect effect = i < live.size() ? live.get(i) : pending.get(i - live.size());
			if (!(effect instanceof CProjectile) || (row >= state.count)) {
				continue;
			}
			final CProjectile projectile = (CProjectile) effect;
			final String name = "projectile row " + row + " ";
			same(name + "x", state.x[row], projectile.getX());
			same(name + "y", state.y[row], projectile.getY());
			same(name + "speed", state.speed[row], projectile.getSpeed());
			same(name + "target", state.target[row],
					projectile.getTarget() instanceof CWidget ? ((CWidget) projectile.getTarget()).getHandleId() : 0);
			same(name + "flags", state.flags[row],
					projectile.isDone() ? WorldObjectFrameState.DEAD : WorldObjectFrameState.VISIBLE);
			row++;
		}
	}

	private void verifyFog(final SimFrame frame, final CPlayerFogOfWar fog) {
		if (fog == null) {
			return;
		}
		final byte[] live = fog.getFogOfWarBuffer().array();
		if (frame.fogChanged) {
			if (this.knownFog.length < frame.fogLength) {
				this.knownFog = new byte[frame.fogLength];
			}
			System.arraycopy(frame.fog, 0, this.knownFog, 0, frame.fogLength);
			this.knownFogLength = frame.fogLength;
		}
		else if (this.knownFogLength < 0) {
			fail("fog never carried but not marked changed");
			return;
		}
		same("fog.length", this.knownFogLength, live.length);
		if (this.knownFogLength == live.length) {
			int differing = 0;
			for (int i = 0; i < live.length; i++) {
				if (live[i] != this.knownFog[i]) {
					differing++;
				}
			}
			same("fog.cells" + (frame.fogChanged ? "" : "(kept)"), differing, 0);
		}
	}

	private void checkSorted(final String what, final WorldObjectFrameState state) {
		for (int i = 1; i < state.count; i++) {
			if (state.handle[i] <= state.handle[i - 1]) {
				fail(what + ".order row " + i + " handle " + state.handle[i] + " after " + state.handle[i - 1]);
			}
		}
	}

	private static int indexOf(final WorldObjectFrameState state, final int handle) {
		return java.util.Arrays.binarySearch(state.handle, 0, state.count, handle) >= 0
				? java.util.Arrays.binarySearch(state.handle, 0, state.count, handle)
				: -1;
	}

	private void same(final String what, final long actual, final long expected) {
		if (actual != expected) {
			fail(what + " frame=" + actual + " live=" + expected);
		}
	}

	private void same(final String what, final float actual, final float expected) {
		if (Float.floatToIntBits(actual) != Float.floatToIntBits(expected)) {
			fail(what + " frame=" + actual + " live=" + expected);
		}
	}

	private void fail(final String message) {
		this.mismatches++;
		this.stepMismatches++;
		if (this.mismatches <= DETAIL_LIMIT) {
			Log.info("FRAMEVERIFY mismatch tick " + this.stepTick + ": " + message);
		}
	}
}
