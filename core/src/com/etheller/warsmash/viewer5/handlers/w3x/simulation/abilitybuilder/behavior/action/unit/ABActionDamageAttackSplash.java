package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.unit;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.floatcallbacks.ABFloatCallback;

public class ABActionDamageAttackSplash extends ABActionDamageSplashBase {

	private ABFloatCallback damage;
	private ABFloatCallback mediumDamageRadius;
	private ABFloatCallback smallDamageRadius;
	private ABFloatCallback damageFactorMedium;
	private ABFloatCallback damageFactorSmall;

	@Override
	protected ABFloatCallback baseDamageCallback() {
		return damage;
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
		return tier == 0 ? damageFactorMedium : damageFactorSmall;
	}

	@Override
	protected float tierDamage(final float tierValue, final float baseDamage) {
		return baseDamage * tierValue;
	}

	@Override
	protected float tierRatio(final float tierValue, final float baseDamage) {
		return tierValue;
	}
}
