package com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDefenseType;

public class CUpgradeEffectArmorType implements CUpgradeEffect {
	private final CDefenseType defenseType;

	public CUpgradeEffectArmorType(final int defenseTypeIndex) {
		this.defenseType = ((defenseTypeIndex >= 0) && (defenseTypeIndex < CDefenseType.VALUES.length))
				? CDefenseType.VALUES[defenseTypeIndex]
				: null;
	}

	@Override
	public void apply(final CSimulation simulation, final CUnit unit, final int level) {
		if (this.defenseType != null) {
			unit.setDefenseType(this.defenseType);
			unit.notifyAttacksChanged();
		}
	}

	@Override
	public void unapply(final CSimulation simulation, final CUnit unit, final int level) {
		if (this.defenseType != null) {
			unit.setDefenseType(unit.getUnitType().getDefenseType());
			unit.notifyAttacksChanged();
		}
	}
}
