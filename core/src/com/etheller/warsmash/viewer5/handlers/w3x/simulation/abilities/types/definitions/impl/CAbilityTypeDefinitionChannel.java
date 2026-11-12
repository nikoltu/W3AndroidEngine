package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl;

import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.CAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.impl.CAbilityTypeChannel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.impl.CAbilityTypeChannelLevelData;

public class CAbilityTypeDefinitionChannel extends AbstractCAbilityTypeDefinition<CAbilityTypeChannelLevelData>
		implements CAbilityTypeDefinition {

	@Override
	protected CAbilityTypeChannelLevelData createLevelData(final GameObject abilityEditorData, final int level) {
		final float artDuration = abilityEditorData.getFieldAsFloat(DATA_D + level, 0);
		return new CAbilityTypeChannelLevelData(getTargetsAllowed(abilityEditorData, level), artDuration);
	}

	@Override
	protected CAbilityType<?> innerCreateAbilityType(final War3ID alias, final GameObject abilityEditorData,
			final List<CAbilityTypeChannelLevelData> levelData) {
		return new CAbilityTypeChannel(alias, abilityEditorData.getFieldAsWar3ID(CODE, -1), levelData);
	}

}
