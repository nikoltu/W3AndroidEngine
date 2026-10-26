package com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityHarvest;

public class CUpgradeEffectLumberCapacity implements CUpgradeEffect {
	private final int base;
	private final int mod;

	public CUpgradeEffectLumberCapacity(final int base, final int mod) {
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
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof CAbilityHarvest) {
				final CAbilityHarvest harvest = (CAbilityHarvest) ability;
				if (harvest.getLumberCapacity() > 0) {
					harvest.setLumberCapacity(harvest.getLumberCapacity() + amount);
				}
			}
		}
	}
}
