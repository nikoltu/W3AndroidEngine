package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackFinalDamageTakenModificationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;

/**
 * On the channeling Banshee: attacks against her deal the ability's damage amplification.
 */
public class CBuffPossessionCaster extends CBuffTimed implements CUnitAttackFinalDamageTakenModificationListener {
	private final float damageAmplification;

	public CBuffPossessionCaster(final int handleId, final War3ID alias, final float damageAmplification) {
		super(handleId, alias, alias, 0);
		this.damageAmplification = damageAmplification;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		unit.addFinalDamageTakenModificationListener(this);
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		unit.removeFinalDamageTakenModificationListener(this);
	}

	@Override
	public float onDamage(final CSimulation simulation, final CUnit attacker, final CUnit target,
			final CDamageFlags flags, final CAttackType attackType, final CDamageType damageType,
			final float previousDamage) {
		if ((flags != null) && flags.isAttack() && (this.damageAmplification > 0)) {
			return previousDamage * this.damageAmplification;
		}
		return previousDamage;
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isPositive() {
		return false;
	}
}
