package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedDefault;

final class CUnitStatsState {
	/**
	 * What GetUnitFacing reads in 1.29: the drawn facing, which follows the heading on a clock of its own, a turn every
	 * 0.03 s that speeds up to the model's top turn speed (orientInterp), so after a quick turn of the heading it lags
	 * behind it for a while. NaN in a save from before it was kept: the heading then.
	 */
	@SavedDefault("NaN")
	float drawnFacing = Float.NaN;
	/** Radians per drawn facing tick, signed. */
	float drawnTurnVelocity;
	int drawnFacingTick;
	float mana;
	/**
	 * What the last unit update regenerated and the values right after it: 1.29 regenerates continuously, so a script
	 * reading inside the step sees only the part up to its time.
	 */
	int regenTick = -1;
	float lifeRegenGiven;
	float lifeAfterRegen;
	float manaRegenGiven;
	float manaAfterRegen;
	int baseMaximumLife;
	int maximumLife;
	float lifeRegen;
	float lifeRegenStrengthBonus;
	float lifeRegenBonus;
	float manaRegen;
	float manaRegenIntelligenceBonus;
	float manaRegenBonus;
	int baseMaximumMana;
	int maximumMana;
	int maxSpeed;
	int minSpeed;
	int baseSpeed;
	int speedBonus;
	float baseLifeRegenPerTick;
	float currentLifeRegenPerTick;
	float currentManaRegenPerTick;
}
