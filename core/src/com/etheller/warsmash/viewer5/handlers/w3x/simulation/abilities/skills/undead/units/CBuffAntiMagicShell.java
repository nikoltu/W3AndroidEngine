package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackFinalDamageTakenModificationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

public class CBuffAntiMagicShell extends CBuffTimed implements CUnitAttackFinalDamageTakenModificationListener {
	private final float magicDamageReduction;
	private final StateModBuff magicImmuneBuff;
	private float shieldLifeRemaining;
	private boolean broken;

	public CBuffAntiMagicShell(final int handleId, final War3ID alias, final float duration, final float shieldLife,
			final float magicDamageReduction) {
		super(handleId, alias, alias, duration);
		this.shieldLifeRemaining = shieldLife;
		this.magicDamageReduction = magicDamageReduction;
		this.magicImmuneBuff = shieldLife > 0 ? null : new StateModBuff(StateModBuffType.MAGIC_IMMUNE, 1);
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		unit.addFinalDamageTakenModificationListener(this);
		if (this.magicImmuneBuff != null) {
			unit.addStateModBuff(this.magicImmuneBuff);
			unit.computeUnitState(game, StateModBuffType.MAGIC_IMMUNE);
		}
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		unit.removeFinalDamageTakenModificationListener(this);
		if (this.magicImmuneBuff != null) {
			unit.removeStateModBuff(this.magicImmuneBuff);
			unit.computeUnitState(game, StateModBuffType.MAGIC_IMMUNE);
		}
	}

	@Override
	public float onDamage(final CSimulation simulation, final CUnit attacker, final CUnit target,
			final CDamageFlags flags, final CAttackType attackType, final CDamageType damageType,
			final float previousDamage) {
		if (this.magicImmuneBuff != null) {
			if ((attackType == CAttackType.MAGIC) && (this.magicDamageReduction > 0)) {
				return previousDamage * (1 - this.magicDamageReduction);
			}
			return previousDamage;
		}
		final boolean magicDamage = (attackType == CAttackType.SPELLS) || attackType.isMagic()
				|| damageType.isMagic();
		final boolean summonedAttacker = (attacker != null) && attacker.isUnitType(CUnitTypeJass.SUMMONED);
		if (this.broken || (previousDamage <= 0) || !(magicDamage || summonedAttacker)) {
			return previousDamage;
		}
		final float absorbed = Math.min(previousDamage, this.shieldLifeRemaining);
		this.shieldLifeRemaining -= absorbed;
		if (this.shieldLifeRemaining <= 0) {
			this.broken = true;
			target.remove(simulation, this);
		}
		return previousDamage - absorbed;
	}

	public float getShieldLifeRemaining() {
		return this.shieldLifeRemaining;
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isDispellable() {
		return true;
	}

	@Override
	public boolean isMagic() {
		return true;
	}
}
