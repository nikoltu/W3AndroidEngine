package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.archmage;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import com.etheller.warsmash.util.RandomBounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitEnumFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.GetAbilityByRawcodeVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.brewmaster.CBuffBreathOfFire;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.creeps.CCreepCastAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CSpellDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

public class CAbilityBlizzard extends CAbilityPointTargetSpellBase implements CCreepCastAbility {
	// Measured on 1.29: creeps rain fire on a group of at least two enemies.
	private static final int CREEP_CAST_MINIMUM_TARGETS = 2;
	private static final float WAVE_DAMAGE_DELAY = 0.9f;
	// Measured on 1.29: a computer player's unit leaves the area this long after the spell starts.
	private static final float HARMFUL_AREA_REACTION_DELAY = 1.7f;
	// Measured on 1.29: the hit buff lasts more than 0.9 and less than 1 second.
	private static final float HIT_BUFF_DURATION = 0.95f;

	private float buildingReduction;
	private float damage;
	private float damagePerSecond;
	private float maximumDamagePerWave;
	private int shardCount;
	private int waveCount;
	private float waveDelay;
	private float areaOfEffect;
	private War3ID effectId;
	private War3ID hitBuffId;
	private War3ID burnBuffId;
	private final int orderId;
	private final CDamageType damageType;

	private int currentWave;
	private int nextWaveTick;
	private int damageTick = -1;
	private int channelEndTick;
	private final Rectangle recycleRect = new Rectangle();

	public CAbilityBlizzard(final int handleId, final War3ID alias) {
		this(handleId, alias, OrderIds.blizzard, CDamageType.COLD);
	}

	/**
	 * Rain of Fire is the same spell under its own order, burning instead of freezing.
	 */
	public CAbilityBlizzard(final int handleId, final War3ID alias, final int orderId,
			final CDamageType damageType) {
		super(handleId, alias);
		this.orderId = orderId;
		this.damageType = damageType;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.buildingReduction = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.damage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.damagePerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_E + level, 0);
		this.maximumDamagePerWave = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_F + level, 0);
		this.shardCount = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_C + level, 0);
		this.waveCount = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);

		this.waveDelay = getCastingTime();
		setCastingTime(0); // dont use the casting time field normally
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.effectId = AbstractCAbilityTypeDefinition.getEffectId(worldEditorAbility, level);
		// The second buff (BHbz) marks the units a wave hit; the first (BHbd) is not seen on the caster in 1.29.
		this.hitBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 1);
		if ((this.hitBuffId == null) || War3ID.NONE.equals(this.hitBuffId)) {
			this.hitBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
		}
		this.burnBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
	}

	@Override
	public int getBaseOrderId() {
		return this.orderId;
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		this.currentWave = 0;
		this.damageTick = -1;
		this.nextWaveTick = tick;
		this.channelEndTick = tick + ticks(this.waveDelay * this.waveCount);
		simulation.getHarmfulAreas().add(simulation, caster, target.getX(), target.getY(), this.areaOfEffect,
				HARMFUL_AREA_REACTION_DELAY, (this.waveDelay * this.waveCount) + WAVE_DAMAGE_DELAY);
		return true;
	}

	/**
	 * Measured on 1.29: the first wave falls on the effect, each wave hits 0.9 seconds
	 * after it falls, a new wave falls every "Cast" seconds and the channel ends "Cast" seconds after the last one.
	 */
	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		if ((this.currentWave < this.waveCount) && (tick >= this.nextWaveTick)) {
			this.currentWave++;
			spawnShards(simulation, caster, target);
			this.damageTick = tick + ticks(WAVE_DAMAGE_DELAY);
			this.nextWaveTick = tick + ticks(this.waveDelay);
		}
		if ((this.damageTick >= 0) && (tick >= this.damageTick)) {
			this.damageTick = -1;
			damageWave(simulation, caster, target);
		}
		return tick < this.channelEndTick;
	}

	private void spawnShards(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final Random seededRandom = simulation.getSeededRandom();
		for (int i = 0; i < this.shardCount; i++) {
			final float randomAngle = RandomBounds.nextFloat(seededRandom, (float) (StrictMath.PI * 2));
			/* (1 - StrictMath.pow(seededRandom.nextFloat(), 2)) */
			final float randomDistance = seededRandom.nextFloat() * this.areaOfEffect;
			simulation.spawnSpellEffectOnPoint(target.getX() + ((float) StrictMath.cos(randomAngle) * randomDistance),
					target.getY() + ((float) StrictMath.sin(randomAngle) * randomDistance), 0, this.effectId,
					CEffectType.EFFECT, 0).remove();
			simulation.unitSoundEffectEvent(caster, this.effectId);
		}
	}

	private void damageWave(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final List<CUnit> damageTargets = new ArrayList<>();
		simulation.getWorldCollision().enumUnitsInRect(
				this.recycleRect.set(target.getX() - this.areaOfEffect, target.getY() - this.areaOfEffect,
						this.areaOfEffect * 2, this.areaOfEffect * 2),
				new CUnitEnumFunction() {
					@Override
					public boolean call(final CUnit possibleTarget) {
						if (possibleTarget.canReach(target, CAbilityBlizzard.this.areaOfEffect)
								&& possibleTarget.canBeAreaTargetedBy(simulation, caster, CAreaTargetVisibility.NOT_HIDDEN_GHOST,
										getTargetsAllowed(),
										BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset())
								&& !hasHitBuff(possibleTarget)) {
							damageTargets.add(possibleTarget);
						}
						return false;
					}
				});
		float damagePerTarget = this.damage;
		if ((this.maximumDamagePerWave > 0)
				&& ((damagePerTarget * damageTargets.size()) > this.maximumDamagePerWave)) {
			damagePerTarget = this.maximumDamagePerWave / damageTargets.size();
		}
		final float damagePerTargetBuilding = damagePerTarget * (this.buildingReduction);
		for (final CUnit damageTarget : damageTargets) {
			final float thisTargetDamage = damageTarget.isBuilding() ? damagePerTargetBuilding : damagePerTarget;
			damageTarget.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS, this.damageType,
					CWeaponSoundTypeJass.WHOKNOWS.name(), thisTargetDamage);
			if (!damageTarget.isDead() && (this.damagePerSecond > 0) && (getDuration() > 0)
					&& (this.burnBuffId != null) && !War3ID.NONE.equals(this.burnBuffId)) {
				// Measured on 1.29: a new wave restarts the burn instead of adding a second one.
				final CAbility previousBurn = damageTarget
						.getAbility(GetAbilityByRawcodeVisitor.getInstance().reset(this.burnBuffId));
				if (previousBurn instanceof CBuffBreathOfFire) {
					damageTarget.remove(simulation, (CBuffBreathOfFire) previousBurn);
				}
				damageTarget.add(simulation, new CBuffBreathOfFire(simulation.getHandleIdAllocator().createId(),
						this.burnBuffId, getDuration(), caster, this.damagePerSecond));
			}
			if (!damageTarget.isDead() && (this.hitBuffId != null) && !War3ID.NONE.equals(this.hitBuffId)) {
				damageTarget.add(simulation, new CBuffBlizzard(simulation.getHandleIdAllocator().createId(),
						this.hitBuffId, HIT_BUFF_DURATION));
			}
		}
	}

	/**
	 * Measured on 1.29 (Doom Guard): the creep rains fire on the enemy it attacks when at least two enemies stand in
	 * the area around it, none of them a hero, and the creep itself stays out of the area; 3 Footmen in melee around
	 * it, 3 spread 300 apart, one alone or 2 with a Paladin get none.
	 */
	@Override
	public boolean tryCreepCast(final CSimulation game, final CUnit creep, final CUnit attackTarget,
			final boolean inAttackRange) {
		if ((getCooldownRemaining(game, creep) > 0) || (creep.getMana() < getManaCost())) {
			return false;
		}
		if ((this.orderId != OrderIds.rainoffire) || (attackTarget.getHeroData() != null)
				|| creep.canReach(attackTarget, this.areaOfEffect) || !creep.canReach(attackTarget, getCastRange())) {
			return false;
		}
		final int[] targets = { 0 };
		final boolean[] hero = { false };
		game.getWorldCollision().enumUnitsInRange(attackTarget.getX(), attackTarget.getY(), this.areaOfEffect,
				(enumUnit) -> {
					if (enumUnit.canReach(attackTarget, this.areaOfEffect)
							&& enumUnit.canBeTargetedBy(game, creep, getTargetsAllowed())
							&& !enumUnit.isUnitAlly(game.getPlayer(creep.getPlayerIndex()))) {
						if (enumUnit.getHeroData() != null) {
							hero[0] = true;
						}
						else {
							targets[0]++;
						}
					}
					return false;
				});
		if (hero[0] || (targets[0] < CREEP_CAST_MINIMUM_TARGETS)) {
			return false;
		}
		return creep.order(game, this.orderId, attackTarget, true);
	}

	private boolean hasHitBuff(final CUnit unit) {
		return (this.hitBuffId != null)
				&& (unit.getAbility(GetAbilityByRawcodeVisitor.getInstance().reset(this.hitBuffId)) != null);
	}

	private static int ticks(final float seconds) {
		return StrictMath.round(seconds / WarsmashConstants.SIMULATION_STEP_TIME);
	}

	/**
	 * Like other area spells, it can be aimed at a unit: it falls where the unit stands when the spell starts.
	 */
	@Override
	protected void innerCheckCanTarget(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		if (orderId != getBaseOrderId()) {
			receiver.orderIdNotAccepted();
		}
		else if (!unit.isMovementDisabled() || unit.canReach(target, getCastRange())) {
			receiver.targetOk(target);
		}
		else {
			receiver.targetCheckFailed(CommandStringErrorKeys.TARGET_IS_OUTSIDE_RANGE);
		}
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		return begin(game, caster, playerIndex, orderId, autoOrder,
				new AbilityPointTarget(target.getX(), target.getY()));
	}

	@Override
	public float getUIAreaOfEffect() {
		return this.areaOfEffect;
	}

}
