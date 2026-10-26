package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.skills.CBehaviorTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

public class CAbilityAncestralSpirit extends CAbilityTargetSpellBase {
	// The ability data has no unit type field; the game restricts it to the Tauren by code.
	private static final War3ID TAUREN = War3ID.fromString("otau");

	private float lifeRestoredFactor;
	private float manaRestoredFactor;
	private CBehaviorTargetSpellBase corpseBehavior;

	public CAbilityAncestralSpirit(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.ancestralspirit;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.lifeRestoredFactor = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.manaRestoredFactor = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
	}

	// A fresh Tauren corpse counts too, not only one that has decayed to the corpse stage the generic dead
	// target check asks for.
	@Override
	protected void innerCheckCanTarget(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit == null) || !targetUnit.isDead() || targetUnit.isHidden() || targetUnit.isHero()
				|| !TAUREN.equals(targetUnit.getTypeId())) {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_CORPSE);
		}
		else if (targetUnit.getPlayerIndex() != unit.getPlayerIndex()) {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_ONE_OF_YOUR_OWN_UNITS);
		}
		else if (unit.isMovementDisabled() && !unit.canReach(target, getCastRange())) {
			receiver.targetCheckFailed(CommandStringErrorKeys.TARGET_IS_OUTSIDE_RANGE);
		}
		else {
			receiver.targetOk(target);
		}
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		this.corpseBehavior = new CBehaviorTargetSpellBase(unit, this) {
			@Override
			protected boolean checkTargetStillValid(final CSimulation simulation) {
				final CUnit targetUnit = this.target.visit(AbilityTargetVisitor.UNIT);
				return (targetUnit != null) && targetUnit.isDead() && !targetUnit.isHidden();
			}
		};
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		return this.corpseBehavior.reset(game, target);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit != null) && targetUnit.isDead()) {
			targetUnit.resurrect(simulation);
			targetUnit.setLife(simulation, Math.max(1, targetUnit.getMaximumLife() * this.lifeRestoredFactor));
			targetUnit.setMana(targetUnit.getMaximumMana() * this.manaRestoredFactor);
			simulation.createTemporarySpellEffectOnUnit(targetUnit, getAlias(), CEffectType.TARGET);
			simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		}
		return false;
	}

	@Override
	public boolean isUniversal() {
		return true;
	}
}
