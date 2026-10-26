package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.listeners;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;

public interface CUnitSpellEffectListener {
	void onSpellEffect(CSimulation simulation, CUnit caster, CAbility ability);
}
