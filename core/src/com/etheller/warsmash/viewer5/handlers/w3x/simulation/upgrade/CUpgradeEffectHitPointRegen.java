package com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

public class CUpgradeEffectHitPointRegen implements CUpgradeEffect {
	private float base;
	private float mod;

	public CUpgradeEffectHitPointRegen(float base, float mod) {
		this.base = base;
		this.mod = mod;
	}

	// Measured (Troll Regeneration, 1.29): the bonus is flat hit points per second, not a share of the base rate.
	@Override
	public void apply(CSimulation simulation, CUnit unit, int level) {
		unit.setLifeRegenBonus(
				unit.getLifeRegenBonus() + Util.levelValue(base, mod, level - 1));
	}

	@Override
	public void unapply(CSimulation simulation, CUnit unit, int level) {
		unit.setLifeRegenBonus(
				unit.getLifeRegenBonus() - Util.levelValue(base, mod, level - 1));
	}
}
