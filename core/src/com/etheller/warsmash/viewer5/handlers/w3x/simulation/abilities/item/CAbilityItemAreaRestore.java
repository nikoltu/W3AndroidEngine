package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

/**
 * Area restoration items and runes: Healing (life, Data A), Mana (mana, Data A) and Restoration (life Data A, mana
 * Data B) for the user and the friendly units around it, at once when used or picked up. The cooldown is kept under
 * the ability code, which is the items' cooldown group.
 */
public class CAbilityItemAreaRestore extends AbstractCAbilityItemInstantUse {
	private final int orderId;
	private final boolean restoresLife;
	private final boolean restoresMana;
	private float life;
	private float mana;
	private float areaOfEffect;

	public CAbilityItemAreaRestore(final int handleId, final War3ID alias, final int orderId,
			final boolean restoresLife, final boolean restoresMana) {
		super(handleId, alias);
		this.orderId = orderId;
		this.restoresLife = restoresLife;
		this.restoresMana = restoresMana;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		final float dataA = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		final float dataB = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.life = this.restoresLife ? dataA : 0;
		this.mana = this.restoresLife ? dataB : dataA;
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	@Override
	public int getBaseOrderId() {
		return this.orderId;
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if (orderId != this.orderId) {
			return true;
		}
		game.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), this.areaOfEffect, (enumUnit) -> {
			if (enumUnit.canBeTargetedBy(game, caster, false, getTargetsAllowed(),
					BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset())) {
				if (this.life > 0) {
					enumUnit.heal(game, this.life);
				}
				if (this.restoresMana && (this.mana > 0) && (enumUnit.getMaximumMana() > 0)) {
					enumUnit.restoreMana(game, this.mana);
				}
				game.createTemporarySpellEffectOnUnit(enumUnit, getAlias(), CEffectType.TARGET);
			}
			return false;
		});
		game.unitSoundEffectEvent(caster, getAlias());
		caster.beginCooldown(game, getCode(), getCooldown());
		return false;
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		final float cooldownRemaining = getCooldownRemaining(game, unit);
		if (cooldownRemaining > 0) {
			receiver.cooldownNotYetReady(cooldownRemaining,
					unit.getCooldownLengthDisplayTicks(game, getCode()) * WarsmashConstants.SIMULATION_STEP_TIME);
		}
		else {
			receiver.useOk();
		}
	}

	@Override
	public boolean isMagic() {
		return false;
	}

	@Override
	public CAbilityCategory getAbilityCategory() {
		return CAbilityCategory.ITEM;
	}
}
