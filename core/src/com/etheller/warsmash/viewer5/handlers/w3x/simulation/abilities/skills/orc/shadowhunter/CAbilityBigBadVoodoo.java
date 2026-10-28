package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.shadowhunter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * Big Bad Voodoo: while channeled, the caster's own non-hero units around it are invulnerable; a unit that leaves
 * the area, or the end of the channel, ends it.
 */
public class CAbilityBigBadVoodoo extends CAbilityNoTargetSpellBase {
	private float areaOfEffect;
	private War3ID protectedBuffId;
	private War3ID casterBuffId;
	private int channelEndTick;
	private final Map<CUnit, CBuffBigBadVoodoo> protectedUnits = new HashMap<>();
	private CBuffBigBadVoodoo casterBuff;

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
		this.protectedBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
		this.casterBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 1);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		this.channelEndTick = simulation.getGameTurnTick()
				+ (int) StrictMath.ceil(getDuration() / WarsmashConstants.SIMULATION_STEP_TIME);
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		if (this.casterBuffId != null) {
			this.casterBuff = new CBuffBigBadVoodoo(simulation.getHandleIdAllocator().createId(), this.casterBuffId,
					false);
			caster.add(simulation, this.casterBuff);
		}
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
		final Iterator<Map.Entry<CUnit, CBuffBigBadVoodoo>> iterator = this.protectedUnits.entrySet().iterator();
		while (iterator.hasNext()) {
			final Map.Entry<CUnit, CBuffBigBadVoodoo> entry = iterator.next();
			if (!inArea.contains(entry.getKey())) {
				entry.getKey().remove(simulation, entry.getValue());
				iterator.remove();
			}
		}
		for (final CUnit unit : inArea) {
			if (!this.protectedUnits.containsKey(unit)) {
				final CBuffBigBadVoodoo buff = new CBuffBigBadVoodoo(simulation.getHandleIdAllocator().createId(),
						this.protectedBuffId, true);
				unit.add(simulation, buff);
				this.protectedUnits.put(unit, buff);
			}
		}
		return simulation.getGameTurnTick() < this.channelEndTick;
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit unit, final AbilityTarget target,
			final boolean interrupted) {
		for (final Map.Entry<CUnit, CBuffBigBadVoodoo> entry : this.protectedUnits.entrySet()) {
			entry.getKey().remove(game, entry.getValue());
		}
		this.protectedUnits.clear();
		if (this.casterBuff != null) {
			unit.remove(game, this.casterBuff);
			this.casterBuff = null;
		}
	}
}
