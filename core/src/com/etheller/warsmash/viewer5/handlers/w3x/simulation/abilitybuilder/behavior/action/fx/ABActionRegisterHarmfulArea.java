package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.fx;

import java.util.Map;

import com.etheller.warsmash.parsers.jass.JassTextGenerator;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.floatcallbacks.ABFloatCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.locationcallbacks.ABLocationCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABSingleAction;

/**
 * Marks a ground area that starts hurting the units in it after a delay, so that computer players' units step out of
 * it.
 */
public class ABActionRegisterHarmfulArea implements ABSingleAction {

	private ABLocationCallback location;
	private ABFloatCallback radius;
	private ABFloatCallback delay;
	private ABFloatCallback duration;

	@Override
	public void runAction(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
			final int castId) {
		final AbilityPointTarget loc = this.location.callback(game, caster, localStore, castId);
		game.getHarmfulAreas().add(game, caster, loc.getX(), loc.getY(),
				this.radius.callback(game, caster, localStore, castId),
				this.delay.callback(game, caster, localStore, castId),
				this.duration.callback(game, caster, localStore, castId));
	}

	@Override
	public String generateJassEquivalent(final JassTextGenerator jassTextGenerator) {
		throw new UnsupportedOperationException("a harmful area has no script equivalent");
	}
}
