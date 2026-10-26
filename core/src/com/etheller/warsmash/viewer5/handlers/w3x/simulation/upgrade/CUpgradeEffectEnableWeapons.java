package com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade;

import java.util.IdentityHashMap;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * Turns on attacks that the unit type leaves off (its weapsOn bits), such as the Flying Machine bombs, the same way
 * an attack enabling buff does.
 */
public class CUpgradeEffectEnableWeapons implements CUpgradeEffect {
	private final int attackBits;
	private final Map<CUnit, StateModBuff> buffs = new IdentityHashMap<>();

	public CUpgradeEffectEnableWeapons(final int attackBits) {
		this.attackBits = attackBits;
	}

	@Override
	public void apply(final CSimulation simulation, final CUnit unit, final int level) {
		if ((this.attackBits == 0) || this.buffs.containsKey(unit)) {
			return;
		}
		final StateModBuff buff = new StateModBuff(StateModBuffType.ENABLE_ATTACK, this.attackBits);
		this.buffs.put(unit, buff);
		unit.addStateModBuff(buff);
		unit.computeUnitState(simulation, StateModBuffType.ENABLE_ATTACK);
	}

	@Override
	public void unapply(final CSimulation simulation, final CUnit unit, final int level) {
		final StateModBuff buff = this.buffs.remove(unit);
		if (buff != null) {
			unit.removeStateModBuff(buff);
			unit.computeUnitState(simulation, StateModBuffType.ENABLE_ATTACK);
		}
	}
}
