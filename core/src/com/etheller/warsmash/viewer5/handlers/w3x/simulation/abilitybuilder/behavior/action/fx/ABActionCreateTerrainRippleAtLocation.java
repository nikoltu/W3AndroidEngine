package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.fx;

import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CTerrainDeformations;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.booleancallbacks.ABBooleanCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.floatcallbacks.ABFloatCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.integercallbacks.ABIntegerCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.locationcallbacks.ABLocationCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABAction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.effects.ABTerrainEffect;

public class ABActionCreateTerrainRippleAtLocation implements ABAction {

	private ABLocationCallback location;
	
	private ABFloatCallback startRadius;
	private ABFloatCallback finalRadius;
	private ABFloatCallback radius;
	
	private ABFloatCallback depth;
	
	private ABFloatCallback duration;
	private ABIntegerCallback rippleCount;
	
	private ABFloatCallback period;
	
	private ABIntegerCallback spaceWaves;
	private ABIntegerCallback timeWaves;
	private ABBooleanCallback onlyNegative;
	private ABIntegerCallback startDelay;

	@Override
	public void runAction(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
			final int castId) {
		final AbilityPointTarget loc = this.location.callback(game, caster, localStore, castId);

		float startRad = 0;
		float endRad = 0;
		if (radius != null) {
			startRad = radius.callback(game, caster, localStore, castId);
			endRad = startRad;
		} else {
			startRad = startRadius.callback(game, caster, localStore, castId);
			endRad = finalRadius.callback(game, caster, localStore, castId);
		}
		final float theDepth = depth.callback(game, caster, localStore, castId);
		final float thePeriod = period.callback(game, caster, localStore, castId);
		final int swaves = spaceWaves.callback(game, caster, localStore, castId);
		final int twaves = timeWaves.callback(game, caster, localStore, castId);
		final boolean onlyNeg = onlyNegative.callback(game, caster, localStore, castId);

		int intervals = 0;
		if (rippleCount != null) {
			intervals = rippleCount.callback(game, caster, localStore, castId);
		} else {
			final float dur = duration.callback(game, caster, localStore, castId);
			intervals = ((int) dur) * 2;
		}

		final float startPct = endRad > 0 ? startRad / endRad : 1;
		final CTerrainDeformations.Deformation deformation = game.getTerrainDeformations().ripple(game, loc.x, loc.y,
				Math.max(startRad, endRad), theDepth, Math.round(thePeriod), intervals, swaves, twaves, startPct,
				onlyNeg, startDelay == null ? 0 : startDelay.callback(game, caster, localStore, castId));
		final ABTerrainEffect fx = new ABTerrainEffect(game, deformation, 0);
		localStore.put(ABLocalStoreKeys.LASTCREATEDFX, fx);
	}
}
