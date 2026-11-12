package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.unit;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.floatcallbacks.ABFloatCallback;

public class ABActionDamageSplash extends ABActionDamageSplashBase {

	private ABFloatCallback fullDamage;
	private ABFloatCallback mediumDamageRadius;
	private ABFloatCallback mediumDamage;
	private ABFloatCallback smallDamageRadius;
	private ABFloatCallback smallDamage;

	@Override
	protected ABFloatCallback baseDamageCallback() {
		return fullDamage;
	}

	@Override
	protected int tierCount() {
		return 2;
	}

	@Override
	protected ABFloatCallback tierRadiusCallback(final int tier) {
		return tier == 0 ? mediumDamageRadius : smallDamageRadius;
	}

	@Override
	protected ABFloatCallback tierValueCallback(final int tier) {
		return tier == 0 ? mediumDamage : smallDamage;
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
