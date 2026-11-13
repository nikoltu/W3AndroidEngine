package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.brewmaster;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetUnitVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;

/**
 * Drunken Haze: a missile to the target unit drenches the enemies around it.
 */
public class CAbilityDrunkenHaze extends CAbilityTargetSpellBase {
	private float missChance;
	private float moveSpeedReduction;
	private float attackSpeedReduction;
	private float areaOfEffect;
	private float projectileSpeed;
	private boolean projectileHomingEnabled;
	private War3ID buffId;

	public CAbilityDrunkenHaze(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.drunkenhaze;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.missChance = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.moveSpeedReduction = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.attackSpeedReduction = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.projectileSpeed = worldEditorAbility.getFieldAsFloat(AbilityFields.PROJECTILE_SPEED, 0);
		this.projectileHomingEnabled = worldEditorAbility.getFieldAsBoolean(AbilityFields.PROJECTILE_HOMING_ENABLED,
				0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit == null) {
			return false;
		}
		simulation.createProjectile(caster, getAlias(), caster.getX(), caster.getY(),
				(float) caster.angleTo(targetUnit), this.projectileSpeed, this.projectileHomingEnabled, targetUnit,
				new CAbilityProjectileListener() {
					@Override
					public void onLaunch(final CSimulation game, final CProjectile projectile,
							final AbilityTarget target) {
					}

					@Override
					public void onHit(final CSimulation game, final CProjectile projectile,
							final AbilityTarget target) {
						final CUnit hitUnit = target.visit(AbilityTargetUnitVisitor.INSTANCE);
						final float x = hitUnit != null ? hitUnit.getX() : projectile.getX();
						final float y = hitUnit != null ? hitUnit.getY() : projectile.getY();
						drench(game, caster, x, y);
					}
				});
		return false;
	}

	private void drench(final CSimulation game, final CUnit caster, final float x, final float y) {
		final List<CUnit> drenched = new ArrayList<>();
		game.getWorldCollision().enumUnitsInRange(x, y, this.areaOfEffect, (enumUnit) -> {
			if (enumUnit.canReach(x, y, this.areaOfEffect)
					&& enumUnit.canBeAreaTargetedBy(game, caster, CAreaTargetVisibility.ANY, getTargetsAllowed(),
							BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset())) {
				drenched.add(enumUnit);
			}
			return false;
		});
		for (final CUnit unit : drenched) {
			removeHaze(game, unit);
			unit.add(game, new CBuffDrunkenHaze(game.getHandleIdAllocator().createId(), this.buffId,
					getDurationForTarget(unit), this.missChance, this.moveSpeedReduction, this.attackSpeedReduction));
		}
	}

	private static void removeHaze(final CSimulation game, final CUnit unit) {
		CAbility haze = null;
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof CBuffDrunkenHaze) {
				haze = ability;
				break;
			}
		}
		if (haze != null) {
			unit.remove(game, haze);
		}
	}

	@Override
	public float getUIAreaOfEffect() {
		return this.areaOfEffect;
	}
}
