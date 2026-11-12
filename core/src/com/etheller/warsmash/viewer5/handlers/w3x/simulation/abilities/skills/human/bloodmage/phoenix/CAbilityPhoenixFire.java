package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.bloodmage.phoenix;

import java.util.EnumSet;
import java.util.List;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CSpellDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractGenericNoIconAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

public class CAbilityPhoenixFire extends AbstractGenericNoIconAbility {

	private static final CSpellDamageFlags SPELL_FLAGS = new CSpellDamageFlags();

	private float initialDamage;
	private float damagePerSecond;
	private float areaOfEffect;
	private float cooldown;
	private float duration;
	private EnumSet<CTargetType> targetsAllowed;

	private War3ID buffId;

	private int nextFireTick;
	private CUnitAttack plainSource;
	private CUnitAttackMissile plainMissile;

	public CAbilityPhoenixFire(final int handleId, final War3ID code, final War3ID alias, final float initialDamage,
			final float damagePerSecond, final float areaOfEffect, final float cooldown, final float duration,
			final War3ID buffId, final EnumSet<CTargetType> targetsAllowed) {
		super(handleId, code, alias);
		this.initialDamage = initialDamage;
		this.damagePerSecond = damagePerSecond;
		this.areaOfEffect = areaOfEffect;
		this.cooldown = cooldown;
		this.duration = duration;
		this.buffId = buffId;
		this.targetsAllowed = targetsAllowed;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		final int gameTurnTick = game.getGameTurnTick();
		if (gameTurnTick < this.nextFireTick) {
			return;
		}
		// Measured: the fire is the unit's own attack; with its attack removed the unit sends none.
		final List<CUnitAttack> attacks = unit.getCurrentAttacks();
		if (attacks.isEmpty() || (unit.getAttackBehavior() == null)) {
			return;
		}
		// One missile per interval, at the first unit in range that is not already burning; the interval does not
		// follow the per-unit cooldown override.
		final CUnit[] chosen = new CUnit[1];
		game.getWorldCollision().enumUnitsInRect(new Rectangle(unit.getX() - this.areaOfEffect,
				unit.getY() - this.areaOfEffect, this.areaOfEffect * 2, this.areaOfEffect * 2), enumUnit -> {
					if (unit.canReach(enumUnit, this.areaOfEffect)
							&& enumUnit.canBeTargetedBy(game, unit, this.targetsAllowed) && !isBurning(enumUnit)) {
						chosen[0] = enumUnit;
						return true;
					}
					return false;
				});
		final CUnit target = chosen[0];
		if (target == null) {
			return;
		}
		this.nextFireTick = gameTurnTick + CTimer.wholeSteps(this.cooldown);
		missileFor(attacks.get(0)).launch(game, unit, target, 0f, new CUnitAttackListener() {
			@Override
			public void onLaunch() {
			}

			@Override
			public void onHit(final AbilityTarget hit, final float damage) {
				if ((hit == target) && !target.isDead()) {
					ignite(game, unit, target);
				}
			}
		});
	}

	// The fire is a single-target missile; the unit's splash would add damage events on its neighbours.
	private CUnitAttack missileFor(final CUnitAttack attack) {
		if (!(attack instanceof CUnitAttackMissile)) {
			return attack;
		}
		if (this.plainSource != attack) {
			final CUnitAttackMissile m = (CUnitAttackMissile) attack;
			this.plainSource = attack;
			this.plainMissile = new CUnitAttackMissile(m.getAnimationBackswingPoint(), m.getAnimationDamagePoint(),
					m.getAttackType(), m.getCooldownTime(), m.getDamageBase(), m.getDamageDice(),
					m.getDamageSidesPerDie(), m.getDamageUpgradeAmount(), m.getRange(), m.getRangeMotionBuffer(),
					m.isShowUI(), m.getTargetsAllowed(), m.getWeaponSound(), m.getWeaponType(), m.getAnimationTag(),
					m.getProjectileArc(), m.getProjectileArt(), m.isProjectileHomingEnabled(),
					m.getProjectileSpeed());
		}
		return this.plainMissile;
	}

	private boolean isBurning(final CUnit target) {
		for (final CAbility ability : target.getAbilities()) {
			if ((ability instanceof CBuffPhoenixFire) && (((CBuffPhoenixFire) ability).getAlias().equals(this.buffId))) {
				return true;
			}
		}
		return false;
	}

	private void ignite(final CSimulation game, final CUnit source, final CUnit target) {
		target.damage(game, source, SPELL_FLAGS, CAttackType.SPELLS, CDamageType.FIRE,
				CWeaponSoundTypeJass.WHOKNOWS.name(), this.initialDamage);
		if (!target.isDead() && !isBurning(target)) {
			target.add(game, new CBuffPhoenixFire(game.getHandleIdAllocator().createId(), this.buffId, this.duration,
					source, this.damagePerSecond));
		}
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {

	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId) {

	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		return null;
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		return null;
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder) {
		return null;
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		receiver.notAnActiveAbility();
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget target,
			final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		receiver.notAnActiveAbility();
	}

	@Override
	public void checkCanTargetNoTarget(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTargetCheckReceiver<Void> receiver) {
		receiver.notAnActiveAbility();
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		receiver.notAnActiveAbility();
	}

	public float getInitialDamage() {
		return this.initialDamage;
	}

	public float getDamagePerSecond() {
		return this.damagePerSecond;
	}

	public float getAreaOfEffect() {
		return this.areaOfEffect;
	}

	public float getCooldown() {
		final Float override = getCooldownOverride(getLevel());
		return override != null ? Math.max(0f, override) : this.cooldown;
	}

	public void setBuffId(final War3ID buffId) {
		this.buffId = buffId;
	}

	public float getDuration() {
		return this.duration;
	}

	public EnumSet<CTargetType> getTargetsAllowed() {
		return this.targetsAllowed;
	}

	public void setInitialDamage(final float initialDamage) {
		this.initialDamage = initialDamage;
	}

	public void setDamagePerSecond(final float damagePerSecond) {
		this.damagePerSecond = damagePerSecond;
	}

	public void setAreaOfEffect(final float areaOfEffect) {
		this.areaOfEffect = areaOfEffect;
	}

	public void setCooldown(final float cooldown) {
		this.cooldown = cooldown;
	}

	public void setDuration(final float duration) {
		this.duration = duration;
	}

	public void setTargetsAllowed(final EnumSet<CTargetType> targetsAllowed) {
		this.targetsAllowed = targetsAllowed;
	}

}
