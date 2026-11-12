package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.neutral;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractCAbilityPassiveNoOrders;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpell;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorStop;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

public class CAbilityWander extends AbstractCAbilityPassiveNoOrders implements CAbilitySpell {
	private static final War3ID CODE = War3ID.fromString("Awan");
	/**
	 * Measured on 1.29 (Sheep): a standing critter waits 5 to 10 s, then walks up to about 350 in a random direction;
	 * the wait starts over whenever it stands again, after a walk, a run or an order.
	 */
	private static final float MIN_WAIT = 5.0f;
	private static final float MAX_EXTRA_WAIT = 5.0f;
	private static final float MAX_RANGE = 350;
	private int standingSinceTick = -1;
	private int wanderTick = -1;

	public CAbilityWander(final int handleId, final War3ID alias) {
		super(handleId, CODE, alias);
	}

	@Override
	public void populate(final GameObject worldEditorAbility, final int level) {
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		unit.markWanderer();
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if (unit.isMovementDisabled()) {
			return;
		}
		if (!(unit.getCurrentBehavior() instanceof CBehaviorStop) && (unit.getCurrentBehavior() != null)) {
			this.standingSinceTick = -1;
			this.wanderTick = -1;
			return;
		}
		final int tick = game.getGameTurnTick();
		if (this.standingSinceTick == -1) {
			this.standingSinceTick = tick;
		}
		// The random part of the wait is drawn only once the fixed part is over: a critter busy again before that
		// takes nothing from the shared random sequence.
		if ((this.wanderTick == -1) && ((tick - this.standingSinceTick) >= CTimer.wholeSteps(MIN_WAIT))) {
			this.wanderTick = tick + CTimer.wholeSteps(game.getSeededRandom().nextFloat() * MAX_EXTRA_WAIT);
		}
		if ((this.wanderTick != -1) && (tick >= this.wanderTick)) {
			this.standingSinceTick = -1;
			this.wanderTick = -1;
			final float randomAngle = game.getSeededRandom().nextFloat() * (float) Math.PI * 2.f;
			final float range = game.getSeededRandom().nextFloat() * MAX_RANGE;
			unit.order(game, OrderIds.move,
					new AbilityPointTarget(unit.getX() + (float) (StrictMath.cos(randomAngle) * range),
							unit.getY() + (float) (StrictMath.sin(randomAngle) * range)));
		}
	}

	@Override
	public CAbilityCategory getAbilityCategory() {
		return CAbilityCategory.MOVEMENT;
	}

}
