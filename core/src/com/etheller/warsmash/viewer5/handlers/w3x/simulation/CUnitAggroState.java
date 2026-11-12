package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

final class CUnitAggroState {
	int nextCallForHelpTick;
	CUnit pendingAttackBackSource;
	CUnit pendingSpellAnswerSource;
	CUnit spellMissileSource;
	boolean pendingAttackBackWasHit;
	boolean pendingAttackBackHelp;
	int fleeMoveResetCount = -1;
	CUnit.CreepCamp creepCamp;
	boolean guarding;
	float guardX;
	float guardY;
	int lastCombatTick;
	int lastHurtTick = Integer.MIN_VALUE / 2;
	int guardReturn = CUnit.GUARD_RETURN_NONE;
	boolean guardEngaged;
	int guardAwayTick = -1;
	boolean wanderer;
}
