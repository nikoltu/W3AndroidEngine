package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPostDamageListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitPriorityLoopData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * The tree the Mountain Giant holds: the club attack replaces the normal one until the strikes run out.
 */
public class CBuffWarClub extends CBuffTimed implements CUnitAttackPostDamageListener {
	private final StateModBuff enableClubAttack;
	private final StateModBuff disableNormalAttack;
	private int strikesLeft;

	public CBuffWarClub(final int handleId, final War3ID alias, final int disabledAttackIndex,
			final int enabledAttackIndex, final int strikes) {
		super(handleId, alias, alias, 0);
		this.enableClubAttack = new StateModBuff(StateModBuffType.ENABLE_ATTACK, 1 << enabledAttackIndex);
		this.disableNormalAttack = new StateModBuff(StateModBuffType.DISABLE_ATTACK_INDEX, 1 << disabledAttackIndex);
		this.strikesLeft = strikes;
	}

	public void resetStrikes(final int strikes) {
		this.strikesLeft = strikes;
	}

	public int getStrikesLeft() {
		return this.strikesLeft;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		unit.addStateModBuff(this.enableClubAttack);
		unit.addStateModBuff(this.disableNormalAttack);
		unit.computeUnitState(game, StateModBuffType.ENABLE_ATTACK);
		unit.addPostDamageListener(this);
		unit.getUnitAnimationListener().addSecondaryTag(SecondaryTag.UPGRADE);
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		unit.removeStateModBuff(this.enableClubAttack);
		unit.removeStateModBuff(this.disableNormalAttack);
		unit.computeUnitState(game, StateModBuffType.ENABLE_ATTACK);
		unit.removePostDamageListener(this);
		unit.getUnitAnimationListener().removeSecondaryTag(SecondaryTag.UPGRADE);
		final CBehavior behavior = unit.getCurrentBehavior();
		if (!unit.isDead() && (behavior instanceof CBehaviorAttack)) {
			// The running attack still holds the club attack; ordering it again picks the normal one.
			unit.order(game, OrderIds.attack, ((CBehaviorAttack) behavior).getTarget());
		}
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		// Removed here rather than inside the hit callback, which runs while the attack listeners are iterated.
		if (this.strikesLeft <= 0) {
			unit.remove(game, this);
		}
	}

	@Override
	public void onHit(final CSimulation simulation, final CUnit attacker, final AbilityTarget target,
			final CUnitAttack cUnitAttack, final float damage, final CUnitPriorityLoopData postListenerLoop) {
		this.strikesLeft--;
	}

	@Override
	public int getPriority(final CSimulation simulation, final CUnit attacker, final AbilityTarget target,
			final CUnitAttack cUnitAttack) {
		return 0;
	}

	@Override
	public float getDurationMax() {
		return 0;
	}

	@Override
	public float getDurationRemaining(final CSimulation game, final CUnit unit) {
		return 0;
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}
}
