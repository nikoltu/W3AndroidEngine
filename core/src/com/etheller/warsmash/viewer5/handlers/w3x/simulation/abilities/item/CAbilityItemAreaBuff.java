package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

/**
 * Area buff items and runes: Scroll of Protection (armor, Data A, with life Data B and mana Data C for the
 * restoring version) and Scroll / Rune of Speed (movement speed, Data A) for the user and the friendly units around it,
 * at once when used or picked up, for the ability's duration. Without an area (Potion of Speed) only the user gets it.
 */
public class CAbilityItemAreaBuff extends AbstractCAbilityItemInstantUse {
	private final int orderId;
	private final NonStackingStatBuffType statType;
	private float statValue;
	private float life;
	private float mana;
	private float areaOfEffect;
	private War3ID buffId;

	public CAbilityItemAreaBuff(final int handleId, final War3ID alias, final int orderId,
			final NonStackingStatBuffType statType) {
		super(handleId, alias);
		this.orderId = orderId;
		this.statType = statType;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.statValue = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.life = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.mana = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
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
		game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		if (this.areaOfEffect <= 0) {
			applyBuff(game, caster);
			game.unitSoundEffectEvent(caster, getAlias());
			caster.beginCooldown(game, getCode(), getCooldown());
			return false;
		}
		game.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), this.areaOfEffect, (enumUnit) -> {
			if (enumUnit.canBeAreaTargetedBy(game, caster, CAreaTargetVisibility.ANY, getTargetsAllowed(),
					BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset())) {
				applyBuff(game, enumUnit);
				if (this.life > 0) {
					enumUnit.heal(game, this.life);
				}
				if ((this.mana > 0) && (enumUnit.getMaximumMana() > 0)) {
					enumUnit.restoreMana(game, this.mana);
				}
				if ((this.life > 0) || (this.mana > 0)) {
					game.createTemporarySpellEffectOnUnit(enumUnit, getAlias(), CEffectType.TARGET);
				}
			}
			return false;
		});
		game.unitSoundEffectEvent(caster, getAlias());
		caster.beginCooldown(game, getCode(), getCooldown());
		return false;
	}

	private void applyBuff(final CSimulation game, final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if ((ability instanceof CBuffItemStat) && ability.getAlias().equals(this.buffId)) {
				unit.remove(game, (CBuffItemStat) ability);
				break;
			}
		}
		unit.add(game, new CBuffItemStat(game.getHandleIdAllocator().createId(), this.buffId,
				getDurationForTarget(unit),
				new NonStackingStatBuff(this.statType, this.buffId.asStringValue(), this.statValue)));
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
