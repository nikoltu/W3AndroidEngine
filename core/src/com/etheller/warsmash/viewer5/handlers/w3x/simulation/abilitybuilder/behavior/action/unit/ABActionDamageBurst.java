package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.unit;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.floatcallbacks.ABFloatCallback;

public class ABActionDamageBurst extends ABActionDamageSplashBase {

	private ABFloatCallback partialDamageRadius;
	private ABFloatCallback damage;
	private ABFloatCallback partialDamage;

	@Override
	protected ABFloatCallback baseDamageCallback() {
		return damage;
	}

	@Override
	protected int tierCount() {
		return 1;
	}

	@Override
	protected ABFloatCallback tierRadiusCallback(final int tier) {
		return partialDamageRadius;
	}

	@Override
	protected ABFloatCallback tierValueCallback(final int tier) {
		return partialDamage;
	}

	@Override
	protected float tierDamage(final float tierValue, final float baseDamage) {
		return tierValue;
	}

	@Override
	protected float tierRatio(final float tierValue, final float baseDamage) {
		return tierValue / baseDamage;
	}
}
