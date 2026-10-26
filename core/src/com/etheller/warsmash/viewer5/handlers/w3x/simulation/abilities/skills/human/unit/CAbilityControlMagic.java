package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.unit;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Control Magic (Spell Breaker): takes an enemy summoned unit for the caster's player. With "Charge for Current
 * Life" the mana paid is the given share of the summon's current life instead of the listed cost.
 */
public class CAbilityControlMagic extends CAbilityTargetSpellBase {
	private float manaPerSummonedHitPoint;
	private boolean chargeForCurrentLife;
	private War3ID buffId;

	public CAbilityControlMagic(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.controlmagic;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.manaPerSummonedHitPoint = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.chargeForCurrentLife = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_C + level, 0) != 0;
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	private int controlCost(final CUnit target) {
		if (!this.chargeForCurrentLife) {
			return getManaCost();
		}
		return (int) Math.ceil(target.getLife() * this.manaPerSummonedHitPoint);
	}

	@Override
	protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		final CUnit targetUnit = target instanceof CUnit ? (CUnit) target : null;
		if ((targetUnit == null) || !targetUnit.isUnitType(CUnitTypeJass.SUMMONED) || targetUnit.isHero()) {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_SUMMONED_UNITS);
		}
		else if (unit.getMana() < controlCost(targetUnit)) {
			receiver.targetCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_MANA);
		}
		else {
			receiver.targetOk(target);
		}
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target instanceof CUnit ? (CUnit) target : null;
		if ((targetUnit == null) || targetUnit.isDead()) {
			return false;
		}
		final int extraCost = controlCost(targetUnit) - getManaCost();
		if (extraCost > 0) {
			if (!caster.chargeMana(extraCost)) {
				return false;
			}
		}
		else if (extraCost < 0) {
			caster.setMana(caster.getMana() - extraCost);
		}
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		simulation.createTemporarySpellEffectOnUnit(targetUnit, getAlias(), CEffectType.TARGET);
		targetUnit.setPlayerIndex(simulation, caster.getPlayerIndex(), true);
		targetUnit.performDefaultBehavior(simulation);
		if (this.buffId != null) {
			targetUnit.add(simulation, new CBuffControlled(simulation.getHandleIdAllocator().createId(), this.buffId,
					getDurationForTarget(targetUnit)));
		}
		return false;
	}

	private static final class CBuffControlled extends CBuffTimed {
		CBuffControlled(final int handleId, final War3ID alias, final float duration) {
			super(handleId, alias, alias, duration);
		}

		@Override
		protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		}

		@Override
		protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		}

		@Override
		public boolean isTimedLifeBar() {
			return false;
		}

		@Override
		public boolean isPositive() {
			return false;
		}
	}
}
