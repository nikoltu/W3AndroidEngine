package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.seawitch;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffSpellArt;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

/**
 * Monsoon: while channeled, a storm over the target area strikes every enemy inside it with lightning bolts. Each
 * unit is struck on its own irregular rhythm, about once per strike interval; the bolt lands a short delay after it
 * appears.
 */
public class CAbilityMonsoon extends CAbilityPointTargetSpellBase {
	private float damagePerBolt;
	private float strikeInterval;
	private float boltDelay;
	private float areaOfEffect;
	private War3ID buffId;
	private War3ID effectId;
	private int channelEndTick;
	private float centerX;
	private float centerY;
	private SimulationRenderComponentModel storm;
	private final Map<CUnit, Strike> strikes = new LinkedHashMap<>();

	public CAbilityMonsoon(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.monsoon;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.damagePerBolt = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.strikeInterval = Math.max(0.1f, worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0));
		this.boltDelay = Math.max(0f, worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0));
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
		this.effectId = AbstractCAbilityTypeDefinition.getEffectId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		this.channelEndTick = simulation.getGameTurnTick() + ticks(getDuration());
		this.centerX = target.getX();
		this.centerY = target.getY();
		clearStrikes(simulation);
		removeStorm();
		if (isSet(this.effectId)) {
			this.storm = simulation.spawnSpellEffectOnPoint(this.centerX, this.centerY, caster.getFacing(),
					this.effectId, CEffectType.EFFECT, 0);
		}
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		final Set<CUnit> inArea = new LinkedHashSet<>();
		simulation.getWorldCollision().enumUnitsInRange(this.centerX, this.centerY, this.areaOfEffect,
				(enumUnit) -> {
					if (enumUnit.canReach(this.centerX, this.centerY, this.areaOfEffect)
							&& !enumUnit.isUnitAlly(simulation.getPlayer(caster.getPlayerIndex()))
							&& enumUnit.canBeAreaTargetedBy(simulation, caster, CAreaTargetVisibility.NOT_HIDDEN_GHOST, getTargetsAllowed(),
									BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset())) {
						inArea.add(enumUnit);
					}
					return false;
				});
		final Random random = simulation.getSeededRandom();
		for (final Iterator<Map.Entry<CUnit, Strike>> iterator = this.strikes.entrySet().iterator(); iterator
				.hasNext();) {
			final Map.Entry<CUnit, Strike> entry = iterator.next();
			if (!inArea.contains(entry.getKey())) {
				entry.getValue().removeBuff(simulation, entry.getKey());
				iterator.remove();
			}
		}
		for (final CUnit unit : inArea) {
			Strike strike = this.strikes.get(unit);
			if (strike == null) {
				// Measured on 1.29: the first bolt on a unit lands one to two strike intervals after it enters the
				// storm, later bolts 0.6 to 1.6 intervals apart.
				strike = new Strike(tick + ticks(this.strikeInterval * (1f + random.nextFloat())));
				if (isSet(this.buffId)) {
					strike.buff = new CBuffSpellArt(simulation.getHandleIdAllocator().createId(), this.buffId, false,
							false);
					unit.add(simulation, strike.buff);
				}
				this.strikes.put(unit, strike);
			}
			if (!strike.boltShown && (tick >= (strike.landTick - ticks(this.boltDelay)))) {
				strike.boltShown = true;
				if (isSet(this.buffId)) {
					simulation.createTemporarySpellEffectOnUnit(unit, this.buffId, CEffectType.TARGET);
				}
			}
			if (tick >= strike.landTick) {
				strike.landTick = tick + ticks(this.strikeInterval * (0.6f + random.nextFloat()));
				strike.boltShown = false;
				unit.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.LIGHTNING,
						CWeaponSoundTypeJass.WHOKNOWS.name(), this.damagePerBolt);
			}
		}
		return tick < this.channelEndTick;
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
			final boolean interrupted) {
		clearStrikes(game);
		removeStorm();
	}

	private void clearStrikes(final CSimulation simulation) {
		for (final Map.Entry<CUnit, Strike> entry : this.strikes.entrySet()) {
			entry.getValue().removeBuff(simulation, entry.getKey());
		}
		this.strikes.clear();
	}

	private void removeStorm() {
		if (this.storm != null) {
			this.storm.remove();
			this.storm = null;
		}
	}

	@Override
	public float getUIAreaOfEffect() {
		return this.areaOfEffect;
	}

	private static boolean isSet(final War3ID id) {
		return (id != null) && !War3ID.NONE.equals(id);
	}

	private static int ticks(final float seconds) {
		return CTimer.ceilSteps(seconds);
	}

	private static final class Strike {
		private int landTick;
		private boolean boltShown;
		private CBuffSpellArt buff;

		private Strike(final int landTick) {
			this.landTick = landTick;
		}

		private void removeBuff(final CSimulation simulation, final CUnit unit) {
			if ((this.buff != null) && unit.getAbilities().contains(this.buff)) {
				unit.remove(simulation, this.buff);
			}
		}
	}
}
