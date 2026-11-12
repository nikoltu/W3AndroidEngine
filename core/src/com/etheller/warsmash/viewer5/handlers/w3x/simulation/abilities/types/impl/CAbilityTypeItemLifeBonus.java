package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.impl;

import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.CAbilityItemLifeBonus;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;

public class CAbilityTypeItemLifeBonus extends CAbilityType<CAbilityTypeItemLifeBonusLevelData> {

	public CAbilityTypeItemLifeBonus(final War3ID alias, final War3ID code,
			final List<CAbilityTypeItemLifeBonusLevelData> levelData) {
		super(alias, code, levelData);
	}

	@Override
	public CAbility createAbility(final int handleId) {
		final CAbilityTypeItemLifeBonusLevelData levelData = getLevelData(0);
		return new CAbilityItemLifeBonus(handleId, getCode(), getAlias(), levelData.getLifeBonus());
	}

	@Override
	public void setLevel(final CSimulation game, final CUnit unit, final CLevelingAbility existingAbility, final int level) {
		final CAbilityTypeItemLifeBonusLevelData levelData = getLevelData(level - 1);
		if (existingAbility instanceof CAbilityItemLifeBonus) {
			((CAbilityItemLifeBonus) existingAbility).setLifeBonus(game, unit, levelData.getLifeBonus());
		}
		existingAbility.setLevel(game, unit, level);
	}
}
