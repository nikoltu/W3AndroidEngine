package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.lich;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CGameplayConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffSlow;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * Frost Nova: the target takes the specific damage, every enemy around it the area damage, and all of them are
 * slowed by the frost slow of the gameplay constants.
 */
public class CAbilityFrostNova extends CAbilityTargetSpellBase {
	private float areaDamage;
	private float targetDamage;
	private float areaOfEffect;
	private War3ID buffId;

	public CAbilityFrostNova(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.frostnova;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.areaDamage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.targetDamage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit == null) {
			return false;
		}
		final CGameplayConstants constants = simulation.getGameplayConstants();
		simulation.createTemporarySpellEffectOnUnit(targetUnit, getAlias(), CEffectType.TARGET);
		targetUnit.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.UNIVERSAL,
				CWeaponSoundTypeJass.WHOKNOWS.name(), this.targetDamage);
		simulation.getWorldCollision().enumUnitsInRange(targetUnit.getX(), targetUnit.getY(), this.areaOfEffect,
				(enumUnit) -> {
					if (!enumUnit.isUnitAlly(simulation.getPlayer(caster.getPlayerIndex()))
							&& enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
						enumUnit.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.UNIVERSAL,
								CWeaponSoundTypeJass.WHOKNOWS.name(), this.areaDamage);
						enumUnit.add(simulation,
								new CBuffSlow(simulation.getHandleIdAllocator().createId(), this.buffId,
										getDurationForTarget(enumUnit), constants.getFrostAttackSpeedDecrease(),
										constants.getFrostMoveSpeedDecrease()));
					}
					return false;
				});
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		return false;
	}
}
