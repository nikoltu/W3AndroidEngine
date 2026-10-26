package com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissileBounce;

public class CUpgradeEffectAttackTargetCount implements CUpgradeEffect {
	private final int base;
	private final int mod;

	public CUpgradeEffectAttackTargetCount(final int base, final int mod) {
		this.base = base;
		this.mod = mod;
	}

	@Override
	public void apply(final CSimulation simulation, final CUnit unit, final int level) {
		change(unit, Util.levelValue(this.base, this.mod, level - 1));
	}

	@Override
	public void unapply(final CSimulation simulation, final CUnit unit, final int level) {
		change(unit, -Util.levelValue(this.base, this.mod, level - 1));
	}

	private static void change(final CUnit unit, final int amount) {
		for (final CUnitAttack attack : unit.getUnitSpecificAttacks()) {
			if (attack instanceof CUnitAttackMissileBounce) {
				final CUnitAttackMissileBounce bounce = (CUnitAttackMissileBounce) attack;
				bounce.setMaximumNumberOfTargets(bounce.getMaximumNumberOfTargets() + amount);
			}
		}
		unit.notifyAttacksChanged();
	}
}
