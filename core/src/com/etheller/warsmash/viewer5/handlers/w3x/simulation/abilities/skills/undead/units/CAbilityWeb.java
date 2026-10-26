package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.AutocastType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.CAutocastAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units.CBuffEnsnare;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetUnitVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Web: binds an enemy air unit to the ground, where it cannot move and is attacked as though it were a land unit.
 * Cast on its own at enemy flyers in range while autocast is on.
 */
public class CAbilityWeb extends CAbilityTargetSpellBase implements CAutocastAbility {
	private float airUnitLowerDuration;
	private float airUnitHeight;
	private float projectileSpeed;
	private boolean projectileHomingEnabled;
	private War3ID airBuffId;
	private War3ID groundBuffId;
	private boolean autoCastOn;

	public CAbilityWeb(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.web;
	}

	@Override
	public int getAutoCastOnOrderId() {
		return OrderIds.webon;
	}

	@Override
	public int getAutoCastOffOrderId() {
		return OrderIds.weboff;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.airUnitLowerDuration = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.airUnitHeight = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.projectileSpeed = worldEditorAbility.getFieldAsFloat(AbilityFields.PROJECTILE_SPEED, 0);
		this.projectileHomingEnabled = worldEditorAbility.getFieldAsBoolean(AbilityFields.PROJECTILE_HOMING_ENABLED,
				0);
		this.airBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
		final War3ID groundBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 1);
		this.groundBuffId = War3ID.NONE.equals(groundBuffId) ? this.airBuffId : groundBuffId;
	}

	@Override
	protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit != null) && isWebbed(targetUnit)) {
			receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_THIS_UNIT);
		}
		else {
			receiver.targetOk(target);
		}
	}

	private boolean isWebbed(final CUnit unit) {
		return CUndeadUnitAbilities.hasBuff(unit, this.airBuffId)
				|| CUndeadUnitAbilities.hasBuff(unit, this.groundBuffId);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit != null) {
			simulation.createProjectile(caster, getAlias(), caster.getX(), caster.getY(),
					(float) caster.angleTo(targetUnit), this.projectileSpeed, this.projectileHomingEnabled,
					targetUnit, new CAbilityProjectileListener() {
						@Override
						public void onLaunch(final CSimulation game, final CProjectile projectile,
								final AbilityTarget target) {
						}

						@Override
						public void onHit(final CSimulation game, final CProjectile projectile,
								final AbilityTarget target) {
							final CUnit unitTarget = target.visit(AbilityTargetUnitVisitor.INSTANCE);
							if ((unitTarget != null) && !unitTarget.isDead() && !isWebbed(unitTarget)) {
								final boolean flying = unitTarget.isUnitType(CUnitTypeJass.FLYING);
								unitTarget.add(game,
										new CBuffEnsnare(game.getHandleIdAllocator().createId(),
												flying ? CAbilityWeb.this.airBuffId : CAbilityWeb.this.groundBuffId,
												getDurationForTarget(unitTarget), flying,
												CAbilityWeb.this.airUnitHeight,
												CAbilityWeb.this.airUnitLowerDuration));
							}
						}
					});
		}
		return false;
	}

	@Override
	public AutocastType getAutocastType() {
		return AutocastType.NEARESTENEMY;
	}

	@Override
	public boolean isAutoCastOn() {
		return this.autoCastOn;
	}

	@Override
	public void setAutoCastOn(final CSimulation simulation, final CUnit caster, final boolean autoCastOn,
			final boolean notify) {
		this.autoCastOn = autoCastOn;
		if (notify) {
			caster.setAutocastAbility(simulation, autoCastOn ? this : null);
		}
	}

	@Override
	public void checkCanAutoTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		if ((getCooldownRemaining(game, unit) > 0) || (unit.getMana() < getManaCost())
				|| !isRequirementsMet(game, unit) || !unit.canReach(target, getCastRange())) {
			receiver.orderIdNotAccepted();
			return;
		}
		innerCheckCanTarget(game, unit, orderId, target, receiver);
	}

	@Override
	public void checkCanAutoTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityPointTarget target, final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public void checkCanAutoTargetNoTarget(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final AbilityTargetCheckReceiver<Void> receiver) {
		receiver.orderIdNotAccepted();
	}
}
