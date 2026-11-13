package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.brewmaster;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetUnitVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityCollisionProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

/**
 * Breath of Fire: a widening wave of fire toward the target point. Every enemy it reaches takes the damage (shared
 * out when the maximum damage would be passed); units under Drunken Haze also start to burn.
 */
public class CAbilityBreathOfFire extends CAbilityPointTargetSpellBase {
	private static final float COLLISION_INTERVAL = 0.05f;

	private float damage;
	private float maximumDamage;
	private float distance;
	private float finalArea;
	private float burnDamagePerSecond;
	private float areaOfEffect;
	private float projectileSpeed;
	private War3ID buffId;

	public CAbilityBreathOfFire(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.breathoffire;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.damage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.maximumDamage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.distance = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.finalArea = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.burnDamagePerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_E + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.projectileSpeed = worldEditorAbility.getFieldAsFloat(AbilityFields.PROJECTILE_SPEED, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final double angle = caster.angleTo(target);
		final float endX = caster.getX() + (float) (StrictMath.cos(angle) * this.distance);
		final float endY = caster.getY() + (float) (StrictMath.sin(angle) * this.distance);
		final float damagePerHit = this.damage;
		final float maximum = this.maximumDamage;
		simulation.createCollisionProjectile(caster, getAlias(), caster.getX(), caster.getY(), (float) angle,
				this.projectileSpeed, false, new AbilityPointTarget(endX, endY), 0, 1, this.areaOfEffect,
				this.finalArea, COLLISION_INTERVAL, new CAbilityCollisionProjectileListener() {
					private float remainingDamage = maximum;
					private int unitTargets;
					private float damagePerTarget;

					@Override
					public void onLaunch(final CSimulation game, final CProjectile projectile,
							final AbilityTarget target) {
					}

					@Override
					public void onPreHits(final CSimulation game, final CProjectile projectile,
							final AbilityPointTarget location) {
						this.damagePerTarget = damagePerHit;
						if ((maximum > 0) && (this.unitTargets > 0)) {
							this.damagePerTarget = Math.min(damagePerHit, this.remainingDamage / this.unitTargets);
						}
					}

					@Override
					public boolean canHitTarget(final CSimulation game, final CWidget target) {
						final CUnit unit = target.visit(AbilityTargetVisitor.UNIT);
						return (unit != null) && !unit.isUnitAlly(game.getPlayer(caster.getPlayerIndex()))
								&& unit.canBeAreaTargetedBy(game, caster, CAreaTargetVisibility.ANY, getTargetsAllowed(),
										BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset());
					}

					@Override
					public void onHit(final CSimulation game, final CProjectile projectile,
							final AbilityTarget target) {
						final CUnit unit = target.visit(AbilityTargetUnitVisitor.INSTANCE);
						if (unit == null) {
							return;
						}
						final boolean hazed = CBuffDrunkenHaze.isHazed(unit);
						if (this.damagePerTarget > 0) {
							unit.damage(game, caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.FIRE,
									CWeaponSoundTypeJass.WHOKNOWS.name(), this.damagePerTarget);
							this.remainingDamage -= this.damagePerTarget;
						}
						if (hazed && !unit.isDead()) {
							ignite(game, caster, unit);
						}
					}

					@Override
					public void setUnitTargets(final int units) {
						this.unitTargets = units;
					}

					@Override
					public void setDestructableTargets(final int dests) {
					}

					@Override
					public void setCurrentLocation(final AbilityPointTarget loc) {
					}
				}, true);
		return false;
	}

	private void ignite(final CSimulation game, final CUnit caster, final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof CBuffBreathOfFire) {
				unit.remove(game, ability);
				break;
			}
		}
		unit.add(game, new CBuffBreathOfFire(game.getHandleIdAllocator().createId(), this.buffId,
				getDurationForTarget(unit), caster, this.burnDamagePerSecond));
	}

	@Override
	public float getUIAreaOfEffect() {
		return this.areaOfEffect;
	}
}
