package com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks;

import java.util.EnumSet;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetWidgetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CWeaponType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackSettings;

public class CUnitAttackMissileLine extends CUnitAttackMissile {
	private float damageSpillDistance;
	private float damageSpillRadius;
	private boolean artillery;
	private float damageLossFactor;
	private EnumSet<CTargetType> spillTargets = EnumSet.noneOf(CTargetType.class);
	private static final Rectangle spillRect = new Rectangle();

	public CUnitAttackMissileLine(final float animationBackswingPoint, final float animationDamagePoint,
			final CAttackType attackType, final float cooldownTime, final int damageBase, final int damageDice,
			final int damageSidesPerDie, final int damageUpgradeAmount, final int range, final float rangeMotionBuffer,
			final boolean showUI, final EnumSet<CTargetType> targetsAllowed, final String weaponSound,
			final CWeaponType weaponType, final EnumSet<SecondaryTag> animationTag, final float projectileArc, final String projectileArt,
			final boolean projectileHomingEnabled, final int projectileSpeed, final float damageSpillDistance,
			final float damageSpillRadius, final boolean isArtillery) {
		super(animationBackswingPoint, animationDamagePoint, attackType, cooldownTime, damageBase, damageDice,
				damageSidesPerDie, damageUpgradeAmount, range, rangeMotionBuffer, showUI, targetsAllowed, weaponSound,
				weaponType, animationTag, projectileArc, projectileArt, isArtillery ? false : projectileHomingEnabled, projectileSpeed);
		this.damageSpillDistance = damageSpillDistance;
		this.damageSpillRadius = damageSpillRadius;
		this.artillery = isArtillery;
		if (isArtillery) {
			this.damageFlags = new CAttackDamageFlags(true);
			this.damageFlags.setExplode(true);
		}
		initialSettings();
	}

	@Override
	public CUnitAttack copy() {
		final CUnitAttackMissileLine copy = new CUnitAttackMissileLine(getAnimationBackswingPoint(), getAnimationDamagePoint(), getAttackType(),
				getCooldownTime(), getDamageBase(), getDamageDice(), getDamageSidesPerDie(), getDamageUpgradeAmount(),
				getRange(), getRangeMotionBuffer(), isShowUI(), getTargetsAllowed(), getWeaponSound(), getWeaponType(),
				getAnimationTag(), getProjectileArc(), getProjectileArt(), isProjectileHomingEnabled(), getProjectileSpeed(),
				this.damageSpillDistance, this.damageSpillRadius, this.artillery);
		copy.setSpill(this.damageLossFactor, this.spillTargets);
		return copy;
	}

	public float getDamageSpillDistance() {
		return this.damageSpillDistance;
	}

	public float getDamageSpillRadius() {
		return this.damageSpillRadius;
	}

	public void setDamageSpillDistance(final float damageSpillDistance) {
		this.damageSpillDistance = damageSpillDistance;
	}

	public void setDamageSpillRadius(final float damageSpillRadius) {
		this.damageSpillRadius = damageSpillRadius;
	}

	public Boolean isArtillery() {
		return this.artillery;
	}

	/** What a hit spills onto behind its target (the data's damage loss factor and splash targets). */
	public void setSpill(final float damageLossFactor, final EnumSet<CTargetType> spillTargets) {
		this.damageLossFactor = damageLossFactor;
		this.spillTargets = spillTargets;
	}

	/**
	 * A line attack also hurts the units behind its target, along the attack direction up to the spill distance and
	 * within the spill radius of that line, with the damage loss factor taken off.
	 */
	@Override
	public void doDamage(final CSimulation cSimulation, final CUnit source, final AbilityTarget target,
			final float damage, final float x, final float y, final int bounceIndex,
			final CUnitAttackListener attackListener, final CUnitAttackSettings settings) {
		super.doDamage(cSimulation, source, target, damage, x, y, bounceIndex, attackListener, settings);
		if ((this.damageSpillDistance <= 0) || this.spillTargets.isEmpty()) {
			return;
		}
		final float dx = x - source.getX();
		final float dy = y - source.getY();
		final float length = (float) Math.sqrt((dx * dx) + (dy * dy));
		if (length <= 0) {
			return;
		}
		final float ux = dx / length;
		final float uy = dy / length;
		final float endX = x + (ux * this.damageSpillDistance);
		final float endY = y + (uy * this.damageSpillDistance);
		final float reach = this.damageSpillRadius;
		spillRect.set(Math.min(x, endX) - reach, Math.min(y, endY) - reach, Math.abs(endX - x) + (2 * reach),
				Math.abs(endY - y) + (2 * reach));
		final CWidget primary = target.visit(AbilityTargetWidgetVisitor.INSTANCE);
		final float spilledDamage = damage * (1.0f - this.damageLossFactor);
		cSimulation.getWorldCollision().enumUnitsInRect(spillRect, (enumUnit) -> {
			if ((enumUnit != primary) && (enumUnit != source) && !enumUnit.isDead()) {
				final float along = ((enumUnit.getX() - x) * ux) + ((enumUnit.getY() - y) * uy);
				final float across = Math.abs(((enumUnit.getX() - x) * uy) - ((enumUnit.getY() - y) * ux));
				if ((along > 0) && (along <= this.damageSpillDistance) && (across <= reach)
						&& enumUnit.canBeTargetedBy(cSimulation, source, this.spillTargets)) {
					enumUnit.damage(cSimulation, source, this.damageFlags, getAttackType(),
							getWeaponType().getDamageType(), getWeaponSound(), spilledDamage, 0);
					attackListener.onHit(enumUnit, spilledDamage);
				}
			}
			return false;
		});
	}

	public CUnitAttackSettings initialSettings() {
		this.attackModifier = super.initialSettings();
		this.attackModifier.addAnimationNames(getAnimationTag());
		if (this.artillery) {
			this.attackModifier.setImpactZ(0f);
			this.attackModifier.setApplyEffectsOnMiss(true);
		}
		return this.attackModifier;
	}

}
