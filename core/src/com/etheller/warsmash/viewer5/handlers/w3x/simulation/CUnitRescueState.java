package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

final class CUnitRescueState {
	float rescueRange = CUnit.DEFAULT_RESCUE_RANGE;
	int rescuableByPlayers;
	int unrescuableByPlayers;
	int lastRescueTick = Integer.MIN_VALUE / 2;
}
