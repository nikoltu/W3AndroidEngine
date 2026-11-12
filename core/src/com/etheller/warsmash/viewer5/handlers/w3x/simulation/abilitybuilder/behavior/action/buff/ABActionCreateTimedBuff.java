package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.buff;

import java.util.Map;

import com.etheller.warsmash.parsers.jass.JassTextGenerator;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABTimedBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABSingleAction;

public class ABActionCreateTimedBuff extends ABActionCreateTimedBuffBase implements ABSingleAction {

	private Map<String, ABCallback> uniqueValues;

	@Override
	protected ABTimedBuff createBuff(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
			final int castId, final boolean showTimedLife, final boolean isLeveled, final boolean isPositive,
			final boolean isDispellable) {
		return applyShowIcon(new ABTimedBuff(game.getHandleIdAllocator().createId(),
				buffId.callback(game, caster, localStore, castId), localStore,
				(CAbility) localStore.get(ABLocalStoreKeys.ABILITY), caster,
				duration.callback(game, caster, localStore, castId), showTimedLife, onAddActions, onRemoveActions,
				onExpireActions, castId, isLeveled, isPositive, isDispellable), game, caster, localStore, castId);
	}

	@Override
	protected void addUniqueValues(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
			final int castId, final ABTimedBuff buff) {
		addUniqueValues(uniqueValues, game, caster, localStore, castId, buff);
	}

	@Override
	public String generateJassEquivalent(final JassTextGenerator jassTextGenerator) {
		return generateJass(jassTextGenerator, "CreateTimedBuffAU", null, false);
	}
}
