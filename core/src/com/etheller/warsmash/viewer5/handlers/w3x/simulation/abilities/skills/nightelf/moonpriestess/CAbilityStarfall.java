package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.moonpriestess;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

/**
 * Starfall: while channeled, the stars circle above the caster and every interval a wave of falling stars hits the
 * enemies around it; buildings take a reduced share. Enemies in the area carry the spell's buff, whose art is the
 * falling star.
 */
public class CAbilityStarfall extends CAbilityNoTargetSpellBase {
	// The falling star model (StarfallTarget) lands this long after it appears.
	private static final float STAR_FALL_TIME = 0.767f;

	private float damagePerWave;
	private float interval;
	private float buildingFactor;
	private float areaOfEffect;
	private War3ID buffId;
	private War3ID effectId;
	private int channelEndTick;
	private int nextWaveTick;
	private boolean starsFalling;
	private SimulationRenderComponentModel sky;
	private final Map<CUnit, CBuffSpellArt> buffed = new LinkedHashMap<>();

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
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
		this.effectId = AbstractCAbilityTypeDefinition.getEffectId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		this.channelEndTick = tick + ticks(getDuration());
		// The first wave falls one interval after the channel starts, as in the original game.
		this.nextWaveTick = tick + ticks(this.interval);
		this.starsFalling = false;
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		removeSky();
		if (isSet(this.effectId)) {
			this.sky = simulation.spawnSpellEffectOnPoint(caster.getX(), caster.getY(), caster.getFacing(),
					this.effectId, CEffectType.EFFECT, 0);
		}
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		final Set<CUnit> inArea = new LinkedHashSet<>();
		simulation.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), this.areaOfEffect,
				(enumUnit) -> {
					if (!enumUnit.isUnitAlly(simulation.getPlayer(caster.getPlayerIndex()))
							&& enumUnit.canBeAreaTargetedBy(simulation, caster, CAreaTargetVisibility.VISIBLE,
									getTargetsAllowed())) {
						inArea.add(enumUnit);
					}
					return false;
				});
		updateBuffs(simulation, inArea);
		if (!this.starsFalling && (tick >= (this.nextWaveTick - ticks(STAR_FALL_TIME)))) {
			// Stars start falling early so that they land with the wave's damage.
			this.starsFalling = true;
			if (isSet(this.buffId)) {
				for (final CUnit enumUnit : inArea) {
					simulation.createTemporarySpellEffectOnUnit(enumUnit, this.buffId, CEffectType.TARGET);
				}
			}
		}
		if (tick >= this.nextWaveTick) {
			this.nextWaveTick = tick + ticks(this.interval);
			this.starsFalling = false;
			for (final CUnit enumUnit : inArea) {
				final float damage = enumUnit.isBuilding() ? this.damagePerWave * this.buildingFactor
						: this.damagePerWave;
				enumUnit.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.UNIVERSAL,
						CWeaponSoundTypeJass.WHOKNOWS.name(), damage);
			}
		}
		return tick < this.channelEndTick;
	}

	private void updateBuffs(final CSimulation simulation, final Set<CUnit> inArea) {
		if (!isSet(this.buffId)) {
			return;
		}
		for (final Iterator<Map.Entry<CUnit, CBuffSpellArt>> iterator = this.buffed.entrySet().iterator(); iterator
				.hasNext();) {
			final Map.Entry<CUnit, CBuffSpellArt> entry = iterator.next();
			final boolean attached = entry.getKey().getAbilities().contains(entry.getValue());
			if (!attached || !inArea.contains(entry.getKey())) {
				if (attached) {
					entry.getKey().remove(simulation, entry.getValue());
				}
				iterator.remove();
			}
		}
		for (final CUnit unit : inArea) {
			if (!this.buffed.containsKey(unit)) {
				// The buff's art is one falling star, shown with each wave rather than kept on the unit.
				final CBuffSpellArt buff = new CBuffSpellArt(simulation.getHandleIdAllocator().createId(),
						this.buffId, false, false);
				unit.add(simulation, buff);
				this.buffed.put(unit, buff);
			}
		}
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
			final boolean interrupted) {
		for (final Map.Entry<CUnit, CBuffSpellArt> entry : this.buffed.entrySet()) {
			if (entry.getKey().getAbilities().contains(entry.getValue())) {
				entry.getKey().remove(game, entry.getValue());
			}
		}
		this.buffed.clear();
		removeSky();
	}

	private void removeSky() {
		if (this.sky != null) {
			this.sky.remove();
			this.sky = null;
		}
	}

	private static boolean isSet(final War3ID id) {
		return (id != null) && !War3ID.NONE.equals(id);
	}

	private static int ticks(final float seconds) {
		return CTimer.ceilSteps(seconds);
	}
}
