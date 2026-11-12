package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.impl;

import java.util.EnumSet;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityTypeLevelData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;

public class CAbilityTypePhoenixFireLevelData extends CAbilityTypeLevelData {
	private final float initialDamage;
	private final float damagePerSecond;
	private final float areaOfEffect;
	private final float cooldown;
	private final float duration;
	private final War3ID buffId;

	public CAbilityTypePhoenixFireLevelData(EnumSet<CTargetType> targetsAllowed, float initialDamage,
			float damagePerSecond, float areaOfEffect, float cooldown, float duration, War3ID buffId) {
		super(targetsAllowed);
		this.initialDamage = initialDamage;
		this.damagePerSecond = damagePerSecond;
		this.areaOfEffect = areaOfEffect;
		this.cooldown = cooldown;
		this.duration = duration;
		this.buffId = buffId;
	}

	public float getInitialDamage() {
		return this.initialDamage;
	}

	public float getDamagePerSecond() {
		return this.damagePerSecond;
	}

	public float getAreaOfEffect() {
		return this.areaOfEffect;
	}

	public float getCooldown() {
		return this.cooldown;
	}

	public float getDuration() {
		return this.duration;
	}

	public War3ID getBuffId() {
		return this.buffId;
	}

}
