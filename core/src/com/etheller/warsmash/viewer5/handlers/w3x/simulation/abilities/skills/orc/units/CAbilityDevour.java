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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Devour: swallows a land unit into the caster's Cargo Hold (Devour), one at a time.
 */
public class CAbilityDevour extends CAbilityTargetSpellBase {
	private int maximumCreepLevel;
	private War3ID digestingBuffId;

	public CAbilityDevour(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.devour;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.maximumCreepLevel = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
		this.digestingBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
	}

	@Override
	protected void innerCheckCanUseSpell(final CSimulation game, final CUnit unit, final int orderId,
			final AbilityActivationReceiver receiver) {
		final CAbilityDevourCargo cargo = unit.getFirstAbilityOfType(CAbilityDevourCargo.class);
		if ((cargo == null) || !cargo.isEmpty()) {
			receiver.activationCheckFailed(CommandStringErrorKeys.UNABLE_TO_LOAD_TARGET);
		}
		else {
			receiver.useOk();
		}
	}

	@Override
	protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit == null) || targetUnit.isBuilding()) {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_UNIT_WITH_THIS_ACTION);
		}
		else if ((this.maximumCreepLevel > 0) && game.isNeutralAggressive(targetUnit.getPlayerIndex())
				&& (targetUnit.getUnitType().getLevel() > this.maximumCreepLevel)) {
			receiver.targetCheckFailed(CommandStringErrorKeys.THAT_CREATURE_IS_TOO_POWERFUL);
		}
		else {
			receiver.targetOk(target);
		}
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		final CAbilityDevourCargo cargo = caster.getFirstAbilityOfType(CAbilityDevourCargo.class);
		if ((targetUnit != null) && !targetUnit.isDead() && (cargo != null) && cargo.isEmpty()) {
			simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.SPECIAL);
			cargo.devour(simulation, caster, targetUnit, this.digestingBuffId);
		}
		return false;
	}

	@Override
	public boolean isMagic() {
		return false;
	}

	@Override
	public boolean isPhysical() {
		return true;
	}
}
