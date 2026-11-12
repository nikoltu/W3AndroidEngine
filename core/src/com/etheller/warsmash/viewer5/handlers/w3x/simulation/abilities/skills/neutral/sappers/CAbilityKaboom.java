package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.sappers;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.AutocastType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.CAutocastAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityUnitOrPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

public class CAbilityKaboom extends CAbilityUnitOrPointTargetSpellBase implements CAutocastAbility {

	private float fullDamageRadius;
	private float fullDamageAmount;
	private float partialDamageAmount;
	private float partialDamageRadius;
	private boolean explodesOnDeath;
	private float buildingDamageFactor;
	private boolean exploding = false;
	private boolean autoCastOn = false;
	private boolean pointOrder = false;

	public CAbilityKaboom(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.selfdestruct;
	}

	@Override
	public int getAutoCastOnOrderId() {
		return OrderIds.selfdestructon;
	}

	@Override
	public int getAutoCastOffOrderId() {
		return OrderIds.selfdestructoff;
	}

	@Override
	protected void innerCheckCanSmartTarget(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		if (isAutoCastOn()) {
			this.innerCheckCanTarget(game, unit, getBaseOrderId(), target, receiver);
		}
		else {
			super.innerCheckCanSmartTarget(game, unit, orderId, target, receiver);
		}
	}

	@Override
	protected void innerCheckCanTarget(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		// Measured on 1.29: the order on a destructable it cannot blow up (support column) is taken, and nothing happens.
		if (isUntargetableDestructable(game, unit, target)) {
			receiver.targetOk(target);
			return;
		}
		super.innerCheckCanTarget(game, unit, orderId, target, receiver);
	}

	private boolean isUntargetableDestructable(final CSimulation game, final CUnit unit, final CWidget target) {
		return (target instanceof CDestructable) && !target.canBeTargetedBy(game, unit, getTargetsAllowed(),
				new BooleanAbilityTargetCheckReceiver<CWidget>());
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		if (isUntargetableDestructable(game, caster, target)) {
			return caster.pollNextOrderBehavior(game);
		}
		this.pointOrder = false;
		return super.begin(game, caster, playerIndex, orderId, autoOrder, target);
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		this.pointOrder = true;
		return super.begin(game, caster, playerIndex, orderId, autoOrder, point);
	}

	@Override
	public float getCastRange() {
		// Measured on 1.29: on a point the sapper walks onto it (trees 130 past the point go down); the added reach
		// is for unit targets only.
		return this.pointOrder ? super.getCastRange() - 128 : super.getCastRange();
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.fullDamageAmount = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.fullDamageRadius = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.partialDamageAmount = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.partialDamageRadius = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.explodesOnDeath = worldEditorAbility.getFieldAsBoolean(AbilityFields.DATA_F + level, 0);
		this.buildingDamageFactor = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_E + level, 0);

		setCastRange(super.getCastRange() + 128);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		this.exploding = true;
		caster.kill(simulation);
		return false;
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
		if (this.explodesOnDeath) {
			this.exploding = true;
		}
		if (this.exploding) {
			explode(game, cUnit);
		}
	}

	private void explode(final CSimulation simulation, final CUnit caster) {
		final float radius = StrictMath.max(this.partialDamageRadius, this.fullDamageRadius);
		simulation.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), radius, (enumUnit) -> {
			if (enumUnit.canBeAreaTargetedBy(simulation, caster, CAreaTargetVisibility.NOT_INVISIBLE,
					getTargetsAllowed(),
					BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset())) {
				float damageAmount;
				if (caster.canReach(enumUnit, this.fullDamageRadius)) {
					damageAmount = this.fullDamageAmount;
				}
				else {
					damageAmount = this.partialDamageAmount;
				}
				if (enumUnit.isBuilding()) {
					damageAmount *= this.buildingDamageFactor;
				}
				enumUnit.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.DEMOLITION,
						CWeaponSoundTypeJass.WHOKNOWS.name(), damageAmount);
			}
			return false;
		});
		// Measured on 1.29: the blast also destroys trees and wall-type destructables (support columns) in range.
		simulation.getWorldCollision().enumDestructablesInRange(caster.getX(), caster.getY(), radius,
				(enumDestructable) -> {
					if (enumDestructable.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
						final float damageAmount = enumDestructable.distance(caster.getX(),
								caster.getY()) <= this.fullDamageRadius ? this.fullDamageAmount
										: this.partialDamageAmount;
						enumDestructable.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS,
								CDamageType.DEMOLITION, CWeaponSoundTypeJass.WHOKNOWS.name(), damageAmount);
					}
					return false;
				});
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
	public boolean isAutoCastOn() {
		return this.autoCastOn;
	}

	@Override
	public AutocastType getAutocastType() {
		return AutocastType.NEARESTENEMY;
	}

	@Override
	public void checkCanAutoTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		this.checkCanTarget(game, unit, playerIndex, orderId, false, target, receiver);
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
