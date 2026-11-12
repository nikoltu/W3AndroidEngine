package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractCAbilityPassiveNoOrders;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

public class CAbilityItemStatBonus extends AbstractCAbilityPassiveNoOrders {
	private int strength;
	private int agility;
	private int intelligence;

	public CAbilityItemStatBonus(final int handleId, final War3ID code, final War3ID alias, final int strength,
			final int agility, final int intelligence) {
		super(handleId, code, alias);
		this.strength = strength;
		this.agility = agility;
		this.intelligence = intelligence;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		final CAbilityHero heroData = unit.getHeroData();
		if (heroData == null) {
			return;
		}
		heroData.addStrengthBonus(game, unit, this.strength);
		heroData.addAgilityBonus(game, unit, this.agility);
		heroData.addIntelligenceBonus(game, unit, this.intelligence);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		final CAbilityHero heroData = unit.getHeroData();
		if (heroData == null) {
			return;
		}
		heroData.addStrengthBonus(game, unit, -this.strength);
		heroData.addAgilityBonus(game, unit, -this.agility);
		heroData.addIntelligenceBonus(game, unit, -this.intelligence);
	}

	public void setStats(final CSimulation game, final CUnit unit, final int strength, final int agility,
			final int intelligence) {
		onRemove(game, unit);
		this.strength = strength;
		this.agility = agility;
		this.intelligence = intelligence;
		onAdd(game, unit);
	}

}
