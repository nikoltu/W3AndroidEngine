package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl;

import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.CAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.impl.CAbilityTypeCargoHoldCorpses;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.impl.CAbilityTypeCargoHoldLevelData;

public class CAbilityTypeDefinitionCargoHoldCorpses
		extends AbstractCAbilityTypeDefinition<CAbilityTypeCargoHoldLevelData> implements CAbilityTypeDefinition {

	@Override
	protected CAbilityTypeCargoHoldLevelData createLevelData(final GameObject abilityEditorData, final int level) {
		final int cargoCapacity = abilityEditorData.getFieldAsInteger(DATA_A + level, 0);
		final float castRange = abilityEditorData.getFieldAsFloat(CAST_RANGE + level, 0);
		final float duration = abilityEditorData.getFieldAsFloat(DURATION + level, 0);

		return new CAbilityTypeCargoHoldLevelData(getTargetsAllowed(abilityEditorData, level), cargoCapacity, duration,
				castRange);
	}

	@Override
	protected CAbilityType<?> innerCreateAbilityType(final War3ID alias, final GameObject abilityEditorData,
			final List<CAbilityTypeCargoHoldLevelData> levelData) {
		return new CAbilityTypeCargoHoldCorpses(alias, abilityEditorData.getFieldAsWar3ID(CODE, -1), levelData);
	}

}
