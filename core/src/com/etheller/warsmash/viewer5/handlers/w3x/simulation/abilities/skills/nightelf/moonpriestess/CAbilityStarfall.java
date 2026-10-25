package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.moonpriestess;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * Starfall: while channeled, every interval a wave damages the enemies around the caster; buildings take a reduced
 * share.
 */
public class CAbilityStarfall extends CAbilityNoTargetSpellBase {
	private float damagePerWave;
	private float interval;
	private float buildingFactor;
	private float areaOfEffect;
	private int channelEndTick;
	private int nextWaveTick;

	public CAbilityStarfall(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.starfall;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.damagePerWave = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.interval = Math.max(0.1f, worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0));
		this.buildingFactor = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		this.channelEndTick = tick + ticks(getDuration());
		this.nextWaveTick = tick;
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		if (tick >= this.nextWaveTick) {
			this.nextWaveTick = tick + ticks(this.interval);
			simulation.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), this.areaOfEffect,
					(enumUnit) -> {
						if (!enumUnit.isUnitAlly(simulation.getPlayer(caster.getPlayerIndex()))
								&& enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
							final float damage = enumUnit.isBuilding() ? this.damagePerWave * this.buildingFactor
									: this.damagePerWave;
							simulation.createTemporarySpellEffectOnUnit(enumUnit, getAlias(), CEffectType.TARGET);
							enumUnit.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS,
									CDamageType.UNIVERSAL, CWeaponSoundTypeJass.WHOKNOWS.name(), damage);
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
