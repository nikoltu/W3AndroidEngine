package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;

public class CAbilityLightningShield extends CAbilityTargetSpellBase {
	private float damagePerSecond;
	private float areaOfEffect;
	private War3ID buffId;

	public CAbilityLightningShield(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.lightningshield;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.damagePerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit != null) {
			for (final CAbility ability : targetUnit.getAbilities()) {
				if ((ability instanceof CBuffLightningShield) && this.buffId.equals(ability.getAlias())) {
					targetUnit.remove(simulation, (CBuffLightningShield) ability);
					break;
				}
			}
			targetUnit.add(simulation,
					new CBuffLightningShield(simulation.getHandleIdAllocator().createId(), this.buffId,
							getDurationForTarget(targetUnit), caster, this.damagePerSecond, this.areaOfEffect,
							simulation.getPlayer(caster.getPlayerIndex()).hasAlliance(targetUnit.getPlayerIndex(),
									CAllianceType.PASSIVE)));
		}
		return false;
	}
}
