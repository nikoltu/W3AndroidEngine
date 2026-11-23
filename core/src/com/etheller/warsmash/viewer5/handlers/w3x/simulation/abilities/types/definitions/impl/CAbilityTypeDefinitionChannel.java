package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.channel.CAbilityChannel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.channel.CAbilityChannelPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.channel.CAbilityChannelUnitOrPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.channel.CAbilityChannelUnitTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.channel.ChannelSpell;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpell;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityTypeLevelData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.CAbilityTypeDefinition;

/** Channel: the spell class it becomes depends on the target type its data sets. */
public class CAbilityTypeDefinitionChannel implements CAbilityTypeDefinition {

	@Override
	public CAbilityType<?> createAbilityType(final War3ID alias, final GameObject abilityEditorData) {
		final List<CAbilityTypeLevelData> emptyLevelDatas = new ArrayList<>();
		final int levels = abilityEditorData.getFieldAsInteger(AbilityFields.LEVELS, 0);
		for (int i = 0; i < levels; i++) {
			emptyLevelDatas.add(null);
		}
		final int targetType = ChannelSpell.targetType(abilityEditorData);
		return new CAbilityType<CAbilityTypeLevelData>(alias,
				abilityEditorData.getFieldAsWar3ID(AbilityFields.CODE, -1), emptyLevelDatas) {
			@Override
			public CAbility createAbility(final int handleId) {
				final CAbilitySpell spellAbility = createSpell(handleId, getAlias(), targetType);
				spellAbility.populate(abilityEditorData, 1);
				return spellAbility;
			}

			@Override
			public void setLevel(final CSimulation game, final CUnit unit, final CLevelingAbility existingAbility,
					final int level) {
				existingAbility.setLevel(game, unit, level);
				((CAbilitySpell) existingAbility).populate(abilityEditorData, level);
			}
		};
	}

	private static CAbilitySpell createSpell(final int handleId, final War3ID alias, final int targetType) {
		switch (targetType) {
		case ChannelSpell.TARGET_NONE:
			return new CAbilityChannel(handleId, alias);
		case ChannelSpell.TARGET_UNIT:
			return new CAbilityChannelUnitTarget(handleId, alias);
		case ChannelSpell.TARGET_POINT:
			return new CAbilityChannelPointTarget(handleId, alias);
		default:
			return new CAbilityChannelUnitOrPointTarget(handleId, alias);
		}
	}
}
