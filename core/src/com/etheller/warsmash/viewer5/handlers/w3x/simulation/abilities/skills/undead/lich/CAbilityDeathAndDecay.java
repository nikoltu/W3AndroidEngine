package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.lich;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

/**
 * Death and Decay: while channeled, every interval each unit in the area, of any owner, loses a share of its maximum
 * life.
 */
public class CAbilityDeathAndDecay extends CAbilityPointTargetSpellBase {
	private float maxLifeFractionPerSecond;
	private float interval;
	private float areaOfEffect;
	private int channelEndTick;
	private int nextPulseTick;
	private SimulationRenderComponentModel areaEffect;

	public CAbilityDeathAndDecay(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.deathanddecay;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.maxLifeFractionPerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.interval = Math.max(0.1f, worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0));
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		this.channelEndTick = tick + ticks(getDuration());
		this.nextPulseTick = tick;
		this.areaEffect = simulation.spawnSpellEffectOnPoint(target.getX(), target.getY(), 0, getAlias(),
				CEffectType.EFFECT, 0);
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		if (tick >= this.nextPulseTick) {
			this.nextPulseTick = tick + ticks(this.interval);
			simulation.getWorldCollision().enumUnitsInRange(target.getX(), target.getY(), this.areaOfEffect,
					(enumUnit) -> {
						if (enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
							enumUnit.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS,
									CDamageType.UNIVERSAL, CWeaponSoundTypeJass.WHOKNOWS.name(),
									enumUnit.getMaximumLife() * this.maxLifeFractionPerSecond * this.interval);
						}
						return false;
					});
		}
		return tick < this.channelEndTick;
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
			final boolean interrupted) {
		if (this.areaEffect != null) {
			this.areaEffect.remove();
			this.areaEffect = null;
		}
	}

	private static int ticks(final float seconds) {
		return (int) StrictMath.ceil(seconds / WarsmashConstants.SIMULATION_STEP_TIME);
	}
}
