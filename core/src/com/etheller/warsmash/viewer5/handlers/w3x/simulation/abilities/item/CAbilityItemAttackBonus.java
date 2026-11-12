package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractCAbilityPassiveNoOrders;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

public class CAbilityItemAttackBonus extends AbstractCAbilityPassiveNoOrders {
	private int damageBonus;

	public CAbilityItemAttackBonus(final int handleId, final War3ID code, final War3ID alias, final int damageBonus) {
		super(handleId, code, alias);
		this.damageBonus = damageBonus;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		for (final CUnitAttack attack : unit.getUnitSpecificAttacks()) {
			attack.setTemporaryDamageBonus(attack.getTemporaryDamageBonus() + this.damageBonus);
		}
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		for (final CUnitAttack attack : unit.getUnitSpecificAttacks()) {
			attack.setTemporaryDamageBonus(attack.getTemporaryDamageBonus() - this.damageBonus);
		}
	}

	public void setDamageBonus(final CSimulation game, final CUnit unit, final int damageBonus) {
		onRemove(game, unit);
		this.damageBonus = damageBonus;
		onAdd(game, unit);
	}

}
