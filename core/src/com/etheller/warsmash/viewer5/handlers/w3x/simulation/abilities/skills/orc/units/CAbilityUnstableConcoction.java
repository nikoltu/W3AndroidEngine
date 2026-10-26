package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * Unstable Concoction: the caster dives at an air unit with its move speed bonus and blows itself up on reaching it,
 * dealing the full damage inside the full damage radius (always the target) and the partial damage to the other
 * valid air units in the partial radius.
 */
public class CAbilityUnstableConcoction extends CAbilityTargetSpellBase {
	private float fullDamageRadius;
	private float fullDamageAmount;
	private float partialDamageRadius;
	private float partialDamageAmount;
	private float maximumDamage;
	private float moveSpeedBonus;

	public CAbilityUnstableConcoction(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.unstableconcoction;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.fullDamageRadius = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.fullDamageAmount = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.partialDamageRadius = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.partialDamageAmount = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.maximumDamage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_E + level, 0);
		this.moveSpeedBonus = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_F + level, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit == null) {
			return false;
		}
		caster.setPaused(true);
		final float diveSpeed = caster.getSpeed() + this.moveSpeedBonus;
		final CTimer dive = new CTimer() {
			@Override
			public void onFire(final CSimulation game) {
				if (caster.isDead()) {
					pause(game);
					return;
				}
				final float dx = targetUnit.getX() - caster.getX();
				final float dy = targetUnit.getY() - caster.getY();
				final float distance = (float) Math.sqrt((dx * dx) + (dy * dy));
				final float step = diveSpeed * WarsmashConstants.SIMULATION_STEP_TIME;
				final float contact = caster.getUnitType().getCollisionSize()
						+ targetUnit.getUnitType().getCollisionSize();
				if (targetUnit.isDead() || (distance <= (contact + step))) {
					pause(game);
					explode(game, caster, targetUnit, targetUnit.getX(), targetUnit.getY());
					return;
				}
				caster.setFacing((float) StrictMath.toDegrees(StrictMath.atan2(dy, dx)));
				caster.setPointAndCheckUnstuck(caster.getX() + ((dx / distance) * step),
						caster.getY() + ((dy / distance) * step), game);
			}
		};
		dive.setRepeats(true);
		dive.setTimeoutTime(WarsmashConstants.SIMULATION_STEP_TIME);
		dive.start(simulation);
		return false;
	}

	private void explode(final CSimulation simulation, final CUnit caster, final CUnit targetUnit, final float x,
			final float y) {
		caster.setExplodesOnDeath(true);
		caster.kill(simulation);
		final float radius = Math.max(this.fullDamageRadius, this.partialDamageRadius);
		final float[] totalDamage = { 0 };
		simulation.getWorldCollision().enumUnitsInRange(x, y, radius, (enumUnit) -> {
			if ((enumUnit == targetUnit) || (!enumUnit.isDead()
					&& enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed()))) {
				float damage = ((enumUnit == targetUnit) || enumUnit.canReach(x, y, this.fullDamageRadius))
						? this.fullDamageAmount
						: this.partialDamageAmount;
				if (this.maximumDamage > 0) {
					damage = Math.min(damage, this.maximumDamage - totalDamage[0]);
				}
				if (damage > 0) {
					totalDamage[0] += enumUnit.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS,
							CDamageType.FIRE, CWeaponSoundTypeJass.WHOKNOWS.name(), damage);
				}
			}
			return false;
		});
	}
}
