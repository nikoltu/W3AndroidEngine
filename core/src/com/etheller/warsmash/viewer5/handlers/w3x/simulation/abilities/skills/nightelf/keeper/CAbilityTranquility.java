package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.keeper;

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
 * Tranquility: while channeled, heals every friendly unit around the caster each interval.
 */
public class CAbilityTranquility extends CAbilityNoTargetSpellBase {
	private float healPerSecond;
	private float interval;
	private float areaOfEffect;
	private int channelEndTick;
	private int nextPulseTick;

	public CAbilityTranquility(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.tranquility;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.healPerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.interval = Math.max(0.1f, worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0));
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		this.channelEndTick = tick + ticks(getDuration());
		this.nextPulseTick = tick;
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		if (tick >= this.nextPulseTick) {
			this.nextPulseTick = tick + ticks(this.interval);
			final float heal = this.healPerSecond * this.interval;
			simulation.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), this.areaOfEffect,
					(enumUnit) -> {
						if (enumUnit.isUnitAlly(simulation.getPlayer(caster.getPlayerIndex()))
								&& enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
							enumUnit.heal(simulation, heal);
						}
						return false;
					});
		}
		return tick < this.channelEndTick;
	}

	private static int ticks(final float seconds) {
		return (int) StrictMath.ceil(seconds / WarsmashConstants.SIMULATION_STEP_TIME);
	}
}
