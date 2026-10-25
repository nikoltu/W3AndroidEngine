package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.cryptlord;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * Locust Swarm: while channeled, releases a locust every interval up to the swarm size; the locusts find and attack
 * enemies by themselves and vanish when the channel ends.
 */
public class CAbilityLocustSwarm extends CAbilityNoTargetSpellBase {
	private int swarmSize;
	private float releaseInterval;
	private War3ID locustType;
	private int channelEndTick;
	private int nextReleaseTick;
	private final List<CUnit> locusts = new ArrayList<>();

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
		this.locustType = War3ID.fromString(worldEditorAbility.getFieldAsString(AbilityFields.UNIT_ID + level, 0));
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		this.channelEndTick = tick + ticks(getDuration());
		this.nextReleaseTick = tick;
		this.locusts.clear();
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		if ((tick >= this.nextReleaseTick) && (this.locusts.size() < this.swarmSize)) {
			this.nextReleaseTick = tick + ticks(this.releaseInterval);
			final CUnit locust = simulation.createUnit(this.locustType, caster.getPlayerIndex(), caster.getX(),
					caster.getY(), caster.getFacing());
			if (locust != null) {
				this.locusts.add(locust);
			}
		}
		return tick < this.channelEndTick;
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
			final boolean interrupted) {
		for (final CUnit locust : this.locusts) {
			if (!locust.isDead()) {
				locust.kill(game);
			}
		}
		this.locusts.clear();
	}

	private static int ticks(final float seconds) {
		return (int) StrictMath.ceil(seconds / WarsmashConstants.SIMULATION_STEP_TIME);
	}
}
