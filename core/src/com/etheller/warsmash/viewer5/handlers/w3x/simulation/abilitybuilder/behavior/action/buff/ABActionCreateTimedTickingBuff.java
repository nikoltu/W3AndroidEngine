package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.buff;

import java.util.Map;

import com.etheller.warsmash.parsers.jass.JassTextGenerator;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.stringcallbacks.ABStringCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABTimedTickingBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABTimedBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABSingleAction;

public class ABActionCreateTimedTickingBuff extends ABActionCreateTimedTickingBuffBase implements ABSingleAction {

	private Map<ABStringCallback, ABCallback> uniqueValues;

	@Override
	protected TickingBuffConstructor buffConstructor() {
		return ABTimedTickingBuff::new;
	}

	@Override
	protected void addUniqueValues(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
			final int castId, final ABTimedBuff buff) {
		if (uniqueValues != null) {
			for (final ABStringCallback key : uniqueValues.keySet()) {
				buff.addUniqueValue(uniqueValues.get(key).callback(game, caster, localStore, castId),
						key.callback(game, caster, localStore, castId));
			}
		}
	}

	@Override
	public String generateJassEquivalent(final JassTextGenerator jassTextGenerator) {
		return generateJass(jassTextGenerator, "CreateTimedTickingBuffAU");
	}
}
