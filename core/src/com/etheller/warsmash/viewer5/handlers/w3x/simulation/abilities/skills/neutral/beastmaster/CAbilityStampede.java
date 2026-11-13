package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.beastmaster;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityCollisionProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

/**
 * Stampede: while the Beastmaster channels, thunder lizards run in the direction he faces and explode on the first
 * enemy they reach, damaging every enemy near the explosion. Lizards already running keep going when the channel
 * ends.
 */
public class CAbilityStampede extends CAbilityPointTargetSpellBase {
	private static final float COLLISION_INTERVAL = 0.05f;

	private float beastsPerSecond;
	private float collisionRadius;
	private float damage;
	private float damageRadius;
	private float damageDelay;
	private float areaOfEffect;
	private float projectileSpeed;

	private int channelEndTick;
	private float spawnAccumulator;
	private float centerX;
	private float centerY;
	private float facing;
	private final List<PendingExplosion> pendingExplosions = new ArrayList<>();

	public CAbilityStampede(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.stampede;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.beastsPerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.collisionRadius = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.damage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.damageRadius = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.damageDelay = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_E + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.projectileSpeed = worldEditorAbility.getFieldAsFloat(AbilityFields.PROJECTILE_SPEED, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		this.channelEndTick = simulation.getGameTurnTick() + ticks(getDuration());
		this.spawnAccumulator = 0;
		this.facing = (float) caster.angleTo(target);
		this.centerX = caster.getX();
		this.centerY = caster.getY();
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		this.spawnAccumulator += this.beastsPerSecond * WarsmashConstants.SIMULATION_STEP_TIME;
		while (this.spawnAccumulator >= 1) {
			this.spawnAccumulator -= 1;
			spawnLizard(simulation, caster);
		}
		return simulation.getGameTurnTick() < this.channelEndTick;
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		super.onTick(game, unit);
		if (this.pendingExplosions.isEmpty()) {
			return;
		}
		final int tick = game.getGameTurnTick();
		for (int i = 0; i < this.pendingExplosions.size();) {
			final PendingExplosion explosion = this.pendingExplosions.get(i);
			if (tick >= explosion.tick) {
				this.pendingExplosions.remove(i);
				explode(game, unit, explosion.x, explosion.y);
			}
			else {
				i++;
			}
		}
	}

	private void spawnLizard(final CSimulation simulation, final CUnit caster) {
		final Random random = simulation.getSeededRandom();
		// Measured on 1.29: the lizards start on a line one area behind the Beastmaster, across the facing and as wide
		// as the area on either side, and run two areas forward, so they also sweep the ground behind him.
		final float lateral = ((random.nextFloat() * 2) - 1) * this.areaOfEffect;
		final float cos = (float) StrictMath.cos(this.facing);
		final float sin = (float) StrictMath.sin(this.facing);
		final float startX = (this.centerX - (sin * lateral)) - (cos * this.areaOfEffect);
		final float startY = (this.centerY + (cos * lateral)) - (sin * this.areaOfEffect);
		final float endX = startX + (cos * this.areaOfEffect * 2);
		final float endY = startY + (sin * this.areaOfEffect * 2);
		final CAbilityCollisionProjectileListener listener = new CAbilityCollisionProjectileListener() {
			@Override
			public void onLaunch(final CSimulation game, final CProjectile projectile, final AbilityTarget target) {
			}

			@Override
			public void onPreHits(final CSimulation game, final CProjectile projectile,
					final AbilityPointTarget location) {
			}

			@Override
			public boolean canHitTarget(final CSimulation game, final CWidget target) {
				final CUnit unit = target.visit(AbilityTargetVisitor.UNIT);
				return (unit != null) && !unit.isUnitAlly(game.getPlayer(caster.getPlayerIndex()))
						&& unit.canBeAreaTargetedBy(game, caster, CAreaTargetVisibility.ANY, getTargetsAllowed(),
								BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset());
			}

			@Override
			public void onHit(final CSimulation game, final CProjectile projectile, final AbilityTarget target) {
				CAbilityStampede.this.pendingExplosions.add(new PendingExplosion(projectile.getX(),
						projectile.getY(), game.getGameTurnTick() + ticks(CAbilityStampede.this.damageDelay)));
			}

			@Override
			public void setUnitTargets(final int units) {
			}

			@Override
			public void setDestructableTargets(final int dests) {
			}

			@Override
			public void setCurrentLocation(final AbilityPointTarget loc) {
			}
		};
		simulation.createCollisionProjectile(caster, getAlias(), startX, startY,
				this.facing, this.projectileSpeed, false, new AbilityPointTarget(endX, endY), 1, 1,
				this.collisionRadius, this.collisionRadius, COLLISION_INTERVAL, listener, false);
	}

	private void explode(final CSimulation simulation, final CUnit caster, final float x, final float y) {
		simulation.spawnSpellEffectOnPoint(x, y, this.facing, getAlias(), CEffectType.SPECIAL, 0).remove();
		final List<CUnit> targets = new ArrayList<>();
		simulation.getWorldCollision().enumUnitsInRange(x, y, this.damageRadius, (enumUnit) -> {
			if (enumUnit.canReach(x, y, this.damageRadius)
					&& !enumUnit.isUnitAlly(simulation.getPlayer(caster.getPlayerIndex()))
					&& enumUnit.canBeAreaTargetedBy(simulation, caster, CAreaTargetVisibility.ANY, getTargetsAllowed(),
							BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset())) {
				targets.add(enumUnit);
			}
			return false;
		});
		for (final CUnit target : targets) {
			target.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.UNIVERSAL,
					CWeaponSoundTypeJass.WHOKNOWS.name(), this.damage);
		}
	}

	private static int ticks(final float seconds) {
		return StrictMath.round(seconds / WarsmashConstants.SIMULATION_STEP_TIME);
	}

	@Override
	public float getUIAreaOfEffect() {
		return this.areaOfEffect;
	}

	private static final class PendingExplosion {
		private final float x;
		private final float y;
		private final int tick;

		private PendingExplosion(final float x, final float y, final int tick) {
			this.x = x;
			this.y = y;
			this.tick = tick;
		}
	}
}
