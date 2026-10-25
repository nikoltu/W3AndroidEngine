package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.warden;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * Fan of Knives: every enemy around the caster takes the damage per target, shared out evenly once the total would
 * exceed the maximum total damage.
 */
public class CAbilityFanOfKnives extends CAbilityNoTargetSpellBase {
	private float damagePerTarget;
	private float maximumTotalDamage;
	private float areaOfEffect;

	public CAbilityFanOfKnives(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.fanofknives;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.damagePerTarget = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.maximumTotalDamage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final List<CUnit> hit = new ArrayList<>();
		simulation.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), this.areaOfEffect,
				(enumUnit) -> {
					if (!enumUnit.isUnitAlly(simulation.getPlayer(caster.getPlayerIndex()))
							&& enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
						hit.add(enumUnit);
					}
					return false;
				});
		float damage = this.damagePerTarget;
		if ((this.maximumTotalDamage > 0) && ((damage * hit.size()) > this.maximumTotalDamage)) {
			damage = this.maximumTotalDamage / hit.size();
		}
		for (final CUnit unit : hit) {
			unit.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.UNIVERSAL,
					CWeaponSoundTypeJass.WHOKNOWS.name(), damage);
		}
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		return false;
	}
}
