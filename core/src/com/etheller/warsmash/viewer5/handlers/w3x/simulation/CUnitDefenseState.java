package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDefenseType;

final class CUnitDefenseState {
	float agilityDefensePermanentBonus;
	float agilityDefenseTemporaryBonus;
	float agilityMoveBonus;
	int permanentDefenseBonus;
	float temporaryDefenseBonus;
	float scriptDefenseBonus;
	float totalTemporaryDefenseBonus;
	int currentDefenseDisplay;
	float baseDefense;
	float currentDefense;
	CDefenseType defenseType;
}
