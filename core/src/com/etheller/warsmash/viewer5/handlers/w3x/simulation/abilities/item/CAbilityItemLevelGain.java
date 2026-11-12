package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

public class CAbilityItemLevelGain extends AbstractCAbilityItemInstantUse {
	private int levelsToGain;

	public CAbilityItemLevelGain(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.levelsToGain = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if (orderId == getBaseOrderId()) {
			final CAbilityHero heroData = caster.getHeroData();
			heroData.setHeroLevel(game, caster, heroData.getHeroLevel() + this.levelsToGain, true);
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			return false;
		}
		else {
			return true;
		}
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.itemlevelgain;
	}

	public int getLevelsToGain() {
		return this.levelsToGain;
	}

	public void setLevelsToGain(final int levelsToGain) {
		this.levelsToGain = levelsToGain;
	}
}
