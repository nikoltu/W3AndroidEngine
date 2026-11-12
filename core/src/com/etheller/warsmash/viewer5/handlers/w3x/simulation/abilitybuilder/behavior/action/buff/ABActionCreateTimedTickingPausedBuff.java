package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.buff;

import java.util.Map;

import com.etheller.warsmash.parsers.jass.JassTextGenerator;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABTimedTickingPausedBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.ABTimedBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABSingleAction;

public class ABActionCreateTimedTickingPausedBuff extends ABActionCreateTimedTickingBuffBase implements ABSingleAction {

	private Map<String, ABCallback> uniqueValues;

	@Override
	protected TickingBuffConstructor buffConstructor() {
		return ABTimedTickingPausedBuff::new;
	}

	@Override
	protected void addUniqueValues(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
			final int castId, final ABTimedBuff buff) {
		addUniqueValues(uniqueValues, game, caster, localStore, castId, buff);
	}

	@Override
	public String generateJassEquivalent(final JassTextGenerator jassTextGenerator) {
		return generateJass(jassTextGenerator, "CreateTimedTickingPausedBuffAU");
	}
}
