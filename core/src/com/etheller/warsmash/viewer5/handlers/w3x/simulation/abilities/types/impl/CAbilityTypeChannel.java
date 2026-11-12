package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.impl;

import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.channel.CAbilityChannel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;

public class CAbilityTypeChannel extends CAbilityType<CAbilityTypeChannelLevelData> {

	public CAbilityTypeChannel(final War3ID alias, final War3ID code,
			final List<CAbilityTypeChannelLevelData> levelData) {
		super(alias, code, levelData);
	}

	@Override
	public CAbility createAbility(final int handleId) {
		final CAbilityTypeChannelLevelData levelData = getLevelData(0);
		return new CAbilityChannel(handleId, getCode(), getAlias(), levelData.getArtDuration());
	}

	@Override
	public void setLevel(final CSimulation game, final CUnit unit, final CLevelingAbility existingAbility, final int level) {
		final CAbilityTypeChannelLevelData levelData = getLevelData(level - 1);
		final CAbilityChannel heroAbility = ((CAbilityChannel) existingAbility);
		heroAbility.setArtDuration(levelData.getArtDuration());
		heroAbility.setLevel(game, unit, level);
	}

}
