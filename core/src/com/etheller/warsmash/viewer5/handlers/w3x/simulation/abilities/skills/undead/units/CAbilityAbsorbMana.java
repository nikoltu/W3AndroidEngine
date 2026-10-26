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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Absorb Mana: takes the mana (and life, if the ability allows any) of one of the caster's own units, which flies
 * back to the caster.
 */
public class CAbilityAbsorbMana extends CAbilityTargetSpellBase {
	private float maximumLifeAbsorbed;
	private float maximumManaAbsorbed;
	private float projectileSpeed;
	private boolean projectileHomingEnabled;

	public CAbilityAbsorbMana(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.absorb;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.maximumLifeAbsorbed = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.maximumManaAbsorbed = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.projectileSpeed = worldEditorAbility.getFieldAsFloat(AbilityFields.PROJECTILE_SPEED, 0);
		this.projectileHomingEnabled = worldEditorAbility.getFieldAsBoolean(AbilityFields.PROJECTILE_HOMING_ENABLED,
				0);
	}

	@Override
	protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit == null) || (targetUnit == unit)) {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_UNIT_WITH_THIS_ACTION);
		}
		else if ((targetUnit.getMaximumMana() <= 0) && (this.maximumLifeAbsorbed <= 0)) {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_UNIT_WITH_MANA);
		}
		else if ((targetUnit.getMana() < 1) && (this.maximumLifeAbsorbed <= 0)) {
			receiver.targetCheckFailed(CommandStringErrorKeys.NOT_ENOUGH_MANA_TO_ABSORB);
		}
		else {
			receiver.targetOk(target);
		}
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit == null) || targetUnit.isDead()) {
			return false;
		}
		final float mana = Math.min(targetUnit.getMana(), this.maximumManaAbsorbed);
		final float life = Math.max(0, Math.min(targetUnit.getLife() - 1, this.maximumLifeAbsorbed));
		targetUnit.setMana(targetUnit.getMana() - mana);
		if (life > 0) {
			targetUnit.setLife(simulation, targetUnit.getLife() - life);
		}
		simulation.createProjectile(targetUnit, getAlias(), targetUnit.getX(), targetUnit.getY(),
				(float) targetUnit.angleTo(caster), this.projectileSpeed, this.projectileHomingEnabled, caster,
				new CAbilityProjectileListener() {
					@Override
					public void onLaunch(final CSimulation game, final CProjectile projectile,
							final AbilityTarget target) {
					}

					@Override
					public void onHit(final CSimulation game, final CProjectile projectile,
							final AbilityTarget target) {
						if (!caster.isDead()) {
							caster.restoreMana(game, mana);
							if (life > 0) {
								caster.heal(game, life);
							}
						}
					}
				});
		return false;
	}
}
