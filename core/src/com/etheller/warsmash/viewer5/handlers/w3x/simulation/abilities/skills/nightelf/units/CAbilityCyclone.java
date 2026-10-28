package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

public class CAbilityCyclone extends CAbilityTargetSpellBase {
	private boolean dispellable;
	private War3ID buffId;

	public CAbilityCyclone(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.cyclone;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.dispellable = worldEditorAbility.getFieldAsBoolean(AbilityFields.DATA_A + level, 0);
		// Measured: an enemy unit gets the second buff of the list (Bcy2), not the first.
		final War3ID secondBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 1);
		this.buffId = !War3ID.NONE.equals(secondBuffId) ? secondBuffId
				: AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit != null) {
			for (final CAbility ability : targetUnit.getAbilities()) {
				if (ability instanceof CBuffCyclone) {
					targetUnit.remove(simulation, (CBuffCyclone) ability);
					break;
				}
			}
			targetUnit.add(simulation, new CBuffCyclone(simulation.getHandleIdAllocator().createId(), this.buffId,
					getDurationForTarget(targetUnit), this.dispellable));
		}
		return false;
	}
}
