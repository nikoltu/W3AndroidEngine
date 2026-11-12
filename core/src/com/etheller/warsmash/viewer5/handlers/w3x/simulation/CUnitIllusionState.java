package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.HashMap;
import java.util.Map;

final class CUnitIllusionState {
	boolean illusion;
	float illusionDamageDealtFactor = 1;
	float illusionDamageTakenFactor = 1;
	final Map<Integer, Integer> illusionAbilityLevels = new HashMap<>();
}
