package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.item;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CIllusions;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * Wand of Illusion: creates one illusion of the target unit for the caster's player beside it.
 */
public class CAbilityWandOfIllusion extends CAbilityTargetSpellBase {
	private static final float OFFSET = 100;
	private float damageDealtFactor;
	private float damageTakenFactor;
	private War3ID buffId;

	public CAbilityWandOfIllusion(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.wandOfIllusion;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.damageDealtFactor = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.damageTakenFactor = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit == null) {
			return false;
		}
		final double angle = simulation.getSeededRandom().nextDouble() * 2 * Math.PI;
		final CUnit image = CIllusions.create(simulation, targetUnit, caster.getPlayerIndex(),
				targetUnit.getX() + (float) (Math.cos(angle) * OFFSET),
				targetUnit.getY() + (float) (Math.sin(angle) * OFFSET), targetUnit.getFacing(),
				this.damageDealtFactor, this.damageTakenFactor, getDurationForTarget(targetUnit), this.buffId);
		if (image != null) {
			simulation.createTemporarySpellEffectOnUnit(image, getAlias(), CEffectType.TARGET);
		}
		return false;
	}
}
