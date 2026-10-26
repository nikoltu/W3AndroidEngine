package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.lich;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityWispHarvest;
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
 * Death and Decay: while channeled, every second each unit in the area, of any owner, loses a share of its maximum
 * life (buildings scaled by the building reduction), and the trees in the area are destroyed.
 */
public class CAbilityDeathAndDecay extends CAbilityPointTargetSpellBase {
	private float maxLifeFractionPerSecond;
	private static final float INTERVAL = 1f;

	private float buildingFactor;
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
		this.buildingFactor = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
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
			this.nextPulseTick = tick + ticks(INTERVAL);
			simulation.getWorldCollision().enumUnitsInRange(target.getX(), target.getY(), this.areaOfEffect,
					(enumUnit) -> {
						if (enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
							enumUnit.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS,
									CDamageType.UNIVERSAL, CWeaponSoundTypeJass.WHOKNOWS.name(),
									enumUnit.getMaximumLife() * this.maxLifeFractionPerSecond * INTERVAL
											* (enumUnit.isBuilding() ? this.buildingFactor : 1f));
						}
						return false;
					});
			simulation.getWorldCollision().enumDestructablesInRange(target.getX(), target.getY(), this.areaOfEffect,
					(enumDest) -> {
						if (enumDest.canBeTargetedBy(simulation, caster, CAbilityWispHarvest.TREE_ALIVE_TYPE_ONLY)) {
							enumDest.setLife(simulation, 0);
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
