package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.combat;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractCAbilityPassiveNoOrders;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

public class CAbilityInvulnerable extends AbstractCAbilityPassiveNoOrders {
	private final StateModBuff invulnerableBuff = new StateModBuff(StateModBuffType.INVULNERABLE, 1);

	public CAbilityInvulnerable(final int handleId, final War3ID code, final War3ID alias) {
		super(handleId, code, alias);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		setInvulnerableSource(game, unit, true);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
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
