package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractCAbilityPassiveNoOrders;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

public class CAbilityItemLifeBonus extends AbstractCAbilityPassiveNoOrders {
	private int lifeBonus;

	public CAbilityItemLifeBonus(final int handleId, final War3ID code, final War3ID alias, final int lifeBonus) {
		super(handleId, code, alias);
		this.lifeBonus = lifeBonus;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		unit.addMaxLifeRelative(game, this.lifeBonus);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		final int oldMaximumLife = unit.getMaximumLife();
		final float oldLife = unit.getLife();
		final int newMaximumLife = StrictMath.round(oldMaximumLife - this.lifeBonus);
		final float newLife = (oldLife * (newMaximumLife)) / oldMaximumLife;
		unit.setMaximumLife(newMaximumLife);
		unit.setLife(game, newLife);
	}

	public void setLifeBonus(final CSimulation game, final CUnit unit, final int lifeBonus) {
		onRemove(game, unit);
		this.lifeBonus = lifeBonus;
		onAdd(game, unit);
	}

}
