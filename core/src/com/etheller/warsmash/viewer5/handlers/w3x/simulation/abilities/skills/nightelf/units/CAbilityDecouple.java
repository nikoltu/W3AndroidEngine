package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

/**
 * Decouple: the Hippogryph Rider splits back into the Archer and the Hippogryph, each keeping the health and mana
 * fraction of the rider.
 */
public class CAbilityDecouple extends CAbilityNoTargetSpellBase {
	private War3ID riderUnitId;
	private War3ID mountUnitId;

	public CAbilityDecouple(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.decouple;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.riderUnitId = War3ID.fromString(worldEditorAbility.getFieldAsString(AbilityFields.DATA_A + level, 0));
		this.mountUnitId = War3ID.fromString(worldEditorAbility.getFieldAsString(AbilityFields.DATA_B + level, 0));
	}

	@Override
	public boolean isMagic() {
		return false;
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final float lifeFraction = caster.getLife() / Math.max(1, caster.getMaximumLife());
		final float manaFraction = caster.getMaximumMana() > 0 ? caster.getMana() / caster.getMaximumMana() : 1;
		final CUnit rider = spawn(simulation, caster, this.riderUnitId, lifeFraction, manaFraction);
		spawn(simulation, caster, this.mountUnitId, lifeFraction, manaFraction);
		if (rider != null) {
			simulation.unitPreferredSelectionReplacement(caster, rider);
		}
		simulation.removeUnit(caster);
		return false;
	}

	private static CUnit spawn(final CSimulation simulation, final CUnit caster, final War3ID unitId,
			final float lifeFraction, final float manaFraction) {
		final CUnit unit = simulation.createUnitSimple(unitId, caster.getPlayerIndex(), caster.getX(),
				caster.getY(), caster.getFacing());
		if (unit != null) {
			unit.setLife(simulation, Math.max(1, unit.getMaximumLife() * lifeFraction));
			if (unit.getMaximumMana() > 0) {
				unit.setMana(unit.getMaximumMana() * manaFraction);
			}
		}
		return unit;
	}
}
