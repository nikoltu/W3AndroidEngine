package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.shadowhunter;

import java.util.HashSet;
import java.util.Set;

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
 * Big Bad Voodoo: while channeled, the caster's own non-hero units around it are invulnerable; a unit that leaves
 * the area, or the end of the channel, ends it.
 */
public class CAbilityBigBadVoodoo extends CAbilityNoTargetSpellBase {
	private float areaOfEffect;
	private int channelEndTick;
	private final Set<CUnit> protectedUnits = new HashSet<>();

	public CAbilityBigBadVoodoo(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.voodoo;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		this.channelEndTick = simulation.getGameTurnTick()
				+ (int) StrictMath.ceil(getDuration() / WarsmashConstants.SIMULATION_STEP_TIME);
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final Set<CUnit> inArea = new HashSet<>();
		simulation.getWorldCollision().enumUnitsInRange(caster.getX(), caster.getY(), this.areaOfEffect,
				(enumUnit) -> {
					if ((enumUnit.getPlayerIndex() == caster.getPlayerIndex()) && (enumUnit.getHeroData() == null)
							&& enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
						inArea.add(enumUnit);
					}
					return false;
				});
		for (final CUnit unit : this.protectedUnits) {
			if (!inArea.contains(unit)) {
				unit.setInvulnerable(false);
			}
		}
		for (final CUnit unit : inArea) {
			unit.setInvulnerable(true);
		}
		this.protectedUnits.clear();
		this.protectedUnits.addAll(inArea);
		return simulation.getGameTurnTick() < this.channelEndTick;
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
			final boolean interrupted) {
		for (final CUnit protectedUnit : this.protectedUnits) {
			protectedUnit.setInvulnerable(false);
		}
		this.protectedUnits.clear();
	}
}
