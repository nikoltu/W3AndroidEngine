package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.cryptlord;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackPostDamageListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitPriorityLoopData;

/**
 * Locust Swarm: while channeled, releases a locust every interval up to the swarm size. Locusts attack enemies
 * within the area around the caster, at most a set number on one target; a locust that has dealt the return
 * threshold flies back and heals the caster for the return factor of the damage it carried.
 */
public class CAbilityLocustSwarm extends CAbilityNoTargetSpellBase {
	private static final float TARGETING_INTERVAL = 0.25f;
	private static final float RETURN_RANGE = 128f;
	private static final float RETURN_ORDER_INTERVAL = 1f;

	private int swarmSize;
	private float releaseInterval;
	private int maxPerTarget;
	private float returnFactor;
	private float returnThreshold;
	private War3ID locustType;
	private float areaOfEffect;
	private int channelEndTick;
	private int nextReleaseTick;
	private int nextTargetingTick;
	private final Map<CUnit, Locust> locusts = new LinkedHashMap<>();

	public CAbilityLocustSwarm(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.locustswarm;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.swarmSize = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
		this.releaseInterval = Math.max(0.05f, worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0));
		this.maxPerTarget = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_C + level, 0);
		this.returnFactor = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.returnThreshold = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_E + level, 0);
		this.locustType = War3ID.fromString(worldEditorAbility.getFieldAsString(AbilityFields.UNIT_ID + level, 0));
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		this.channelEndTick = tick + ticks(getDuration());
		this.nextReleaseTick = tick;
		this.nextTargetingTick = tick;
		this.locusts.clear();
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		if ((tick >= this.nextReleaseTick) && (this.locusts.size() < this.swarmSize)) {
			this.nextReleaseTick = tick + ticks(this.releaseInterval);
			final CUnit locustUnit = simulation.createUnit(this.locustType, caster.getPlayerIndex(), caster.getX(),
					caster.getY(), caster.getFacing());
			if (locustUnit != null) {
				final Locust locust = new Locust(locustUnit);
				locustUnit.addPostDamageListener(locust);
				this.locusts.put(locustUnit, locust);
			}
		}
		if (tick >= this.nextTargetingTick) {
			this.nextTargetingTick = tick + ticks(TARGETING_INTERVAL);
			updateLocusts(simulation, caster);
		}
		return tick < this.channelEndTick;
	}

	private void updateLocusts(final CSimulation simulation, final CUnit caster) {
		final Map<CUnit, Integer> attackersPerTarget = new HashMap<>();
		for (final Iterator<Locust> iterator = this.locusts.values().iterator(); iterator.hasNext();) {
			final Locust locust = iterator.next();
			if (locust.unit.isDead()) {
				iterator.remove();
				continue;
			}
			if ((locust.target != null) && (locust.target.isDead()
					|| (locust.target.distance(caster) > this.areaOfEffect))) {
				locust.target = null;
			}
			if (locust.target != null) {
				attackersPerTarget.merge(locust.target, 1, Integer::sum);
			}
		}
		for (final Locust locust : this.locusts.values()) {
			if (locust.returning) {
				if (locust.unit.distance(caster) <= RETURN_RANGE) {
					caster.heal(simulation, locust.carried * this.returnFactor);
					locust.carried = 0;
					locust.returning = false;
				}
				else {
					final int tick = simulation.getGameTurnTick();
					if (tick >= locust.nextReturnOrderTick) {
						locust.nextReturnOrderTick = tick + ticks(RETURN_ORDER_INTERVAL);
						locust.unit.order(simulation, OrderIds.move, new AbilityPointTarget(caster.getX(), caster.getY()));
					}
					continue;
				}
			}
			if (locust.target == null) {
				locust.target = pickTarget(simulation, caster, attackersPerTarget);
				if (locust.target != null) {
					attackersPerTarget.merge(locust.target, 1, Integer::sum);
					locust.unit.order(simulation, OrderIds.attack, locust.target);
				}
				else {
					locust.unit.order(simulation, OrderIds.move, caster);
				}
			}
		}
	}

	private CUnit pickTarget(final CSimulation simulation, final CUnit caster,
			final Map<CUnit, Integer> attackersPerTarget) {
		final CUnit[] best = { null };
		final double[] bestDistance = { Double.MAX_VALUE };
		simulation.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), this.areaOfEffect,
				(enumUnit) -> {
					if (enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed())
							&& (attackersPerTarget.getOrDefault(enumUnit, 0) < this.maxPerTarget)) {
						final double distance = enumUnit.distance(caster);
						if (distance < bestDistance[0]) {
							bestDistance[0] = distance;
							best[0] = enumUnit;
						}
					}
					return false;
				});
		return best[0];
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
			final boolean interrupted) {
		for (final CUnit locust : this.locusts.keySet()) {
			if (!locust.isDead()) {
				locust.kill(game);
			}
		}
		this.locusts.clear();
	}

	private static int ticks(final float seconds) {
		return (int) StrictMath.ceil(seconds / WarsmashConstants.SIMULATION_STEP_TIME);
	}

	private final class Locust implements CUnitAttackPostDamageListener {
		private final CUnit unit;
		private CUnit target;
		private float carried;
		private boolean returning;
		private int nextReturnOrderTick;

		private Locust(final CUnit unit) {
			this.unit = unit;
		}

		@Override
		public void onHit(final CSimulation simulation, final CUnit attacker, final AbilityTarget hitTarget,
				final CUnitAttack cUnitAttack, final float damage, final CUnitPriorityLoopData postListenerLoop) {
			this.carried += damage;
			if (!this.returning && (this.carried >= CAbilityLocustSwarm.this.returnThreshold)) {
				this.returning = true;
				this.target = null;
			}
		}

		@Override
		public int getPriority(final CSimulation simulation, final CUnit attacker, final AbilityTarget hitTarget,
				final CUnitAttack cUnitAttack) {
			return 0;
		}
	}
}
