package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.paladin;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

public class CBuffDivineShield extends CBuffTimed {
	private final StateModBuff invulnerableBuff = new StateModBuff(StateModBuffType.INVULNERABLE, 1);
	public CBuffDivineShield(int handleId, War3ID alias, float duration) {
		super(handleId, alias, alias, duration);
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	protected void onBuffAdd(CSimulation game, CUnit unit) {
		setInvulnerableSource(game, unit, true);
	}

	@Override
	protected void onBuffRemove(CSimulation game, CUnit unit) {
		setInvulnerableSource(game, unit, false);
	}

	private void setInvulnerableSource(final CSimulation game, final CUnit unit, final boolean active) {
		if (active) {
			unit.addStateModBuff(this.invulnerableBuff);
		}
		else {
			unit.removeStateModBuff(this.invulnerableBuff);
		}
		unit.computeUnitState(game, StateModBuffType.INVULNERABLE);
	}
}
