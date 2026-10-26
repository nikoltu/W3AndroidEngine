package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.unit;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * Stops the attack kinds named by the ability's "Attacks Prevented" bits (1 melee, 2 ranged, 4 special, 8 spells).
 */
public class CBuffCloud extends CBuffTimed {
	private final List<StateModBuff> disables = new ArrayList<>();

	public CBuffCloud(final int handleId, final War3ID alias, final float duration, final int attacksPrevented) {
		super(handleId, alias, alias, duration);
		if ((attacksPrevented & 1) != 0) {
			this.disables.add(new StateModBuff(StateModBuffType.DISABLE_MELEE_ATTACK, 1));
		}
		if ((attacksPrevented & 2) != 0) {
			this.disables.add(new StateModBuff(StateModBuffType.DISABLE_RANGED_ATTACK, 1));
		}
		if ((attacksPrevented & 4) != 0) {
			this.disables.add(new StateModBuff(StateModBuffType.DISABLE_SPECIAL_ATTACK, 1));
		}
		if ((attacksPrevented & 8) != 0) {
			this.disables.add(new StateModBuff(StateModBuffType.DISABLE_SPELLS, 1));
		}
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		for (final StateModBuff disable : this.disables) {
			unit.addStateModBuff(disable);
			unit.computeUnitState(game, disable.getBuffType());
		}
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		for (final StateModBuff disable : this.disables) {
			unit.removeStateModBuff(disable);
			unit.computeUnitState(game, disable.getBuffType());
		}
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isPositive() {
		return false;
	}

	@Override
	public boolean isDispellable() {
		return true;
	}

	@Override
	public boolean isMagic() {
		return true;
	}
}
