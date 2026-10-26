package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Unsummon Building: the building drains away step by step and its owner gets back the salvage share of what it
 * cost for each step of life it loses.
 */
public class CAbilityUnsummon extends CAbilityTargetSpellBase {
	private static final War3ID UNSUMMON_BUFF_ID = War3ID.fromString("Buns");

	private float salvageCostRatio;
	private float lifePerSecond;

	public CAbilityUnsummon(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.unsummon;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.salvageCostRatio = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.lifePerSecond = Math.max(1, worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0));
	}

	@Override
	protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit == null) || !targetUnit.isBuilding()) {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_BUILDING);
		}
		else if (targetUnit.isConstructingOrUpgrading()) {
			receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_THIS_UNIT);
		}
		else if (targetUnit.getFirstAbilityOfType(CBuffUnsummoning.class) != null) {
			receiver.targetCheckFailed(CommandStringErrorKeys.TARGET_IS_BEING_UNSUMMONED);
		}
		else {
			receiver.targetOk(target);
		}
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit != null) && !targetUnit.isDead()
				&& (targetUnit.getFirstAbilityOfType(CBuffUnsummoning.class) == null)) {
			targetUnit.add(simulation, new CBuffUnsummoning(simulation.getHandleIdAllocator().createId(),
					UNSUMMON_BUFF_ID, this.salvageCostRatio, this.lifePerSecond));
		}
		return false;
	}

	@Override
	public boolean isMagic() {
		return false;
	}

	@Override
	public boolean isUniversal() {
		return true;
	}
}
