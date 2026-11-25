package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;

/**
 * Vampiric Potion: the user gets Data A bonus damage and attacks that give back Data B of the damage as life, for the
 * ability's duration.
 */
public class CAbilityItemVampiricPotion extends AbstractCAbilityItemInstantUse {
	private float damageBonus;
	private float lifeSteal;
	private War3ID buffId;

	public CAbilityItemVampiricPotion(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.damageBonus = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.lifeSteal = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.itempotionvampirism;
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if (orderId != getBaseOrderId()) {
			return true;
		}
		for (final CAbility ability : caster.getAbilities()) {
			if ((ability instanceof CBuffItemStat) && ability.getAlias().equals(this.buffId)) {
				caster.remove(game, (CBuffItemStat) ability);
				break;
			}
		}
		final String key = this.buffId.asStringValue();
		caster.add(game,
				new CBuffItemStat(game.getHandleIdAllocator().createId(), this.buffId, getDurationForTarget(caster),
						new NonStackingStatBuff(NonStackingStatBuffType.MELEEATK, key, this.damageBonus),
						new NonStackingStatBuff(NonStackingStatBuffType.RNGDATK, key, this.damageBonus),
						new NonStackingStatBuff(NonStackingStatBuffType.HPSTEAL, key, this.lifeSteal)));
		game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		game.unitSoundEffectEvent(caster, getAlias());
		caster.beginCooldown(game, getCooldownKey(), getCooldown());
		return false;
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		final float cooldownRemaining = getCooldownRemaining(game, unit);
		if (cooldownRemaining > 0) {
			receiver.cooldownNotYetReady(cooldownRemaining, unit.getCooldownLengthDisplayTicks(game, getCooldownKey())
					* com.etheller.warsmash.util.WarsmashConstants.SIMULATION_STEP_TIME);
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
