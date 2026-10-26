package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold.CAbilityCargoHoldCorpses;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;

/**
 * Exhume Corpses: while the cargo hold has fewer corpses than the ability's maximum, a new one appears in it after
 * every interval.
 */
public class CAbilityExhumeCorpses extends CAbilityPassiveSpellBase {
	private int maximumCorpses;
	private War3ID corpseUnitType;
	private int ticksTowardsCorpse;

	public CAbilityExhumeCorpses(final int handleId, final War3ID alias) {
		super(handleId, alias, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.maximumCorpses = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
		final String unitType = worldEditorAbility.getFieldAsString(AbilityFields.UNIT_ID + level, 0);
		this.corpseUnitType = (unitType == null) || (unitType.length() != 4) ? null : War3ID.fromString(unitType);
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if (isDisabled() || unit.isDead() || (this.corpseUnitType == null)) {
			return;
		}
		final CAbilityCargoHoldCorpses hold = unit.getFirstAbilityOfType(CAbilityCargoHoldCorpses.class);
		if ((hold == null) || hold.isFull() || (hold.getCargoCount() >= this.maximumCorpses)) {
			this.ticksTowardsCorpse = 0;
			return;
		}
		this.ticksTowardsCorpse++;
		if ((this.ticksTowardsCorpse * WarsmashConstants.SIMULATION_STEP_TIME) >= getDuration()) {
			this.ticksTowardsCorpse = 0;
			final CUnit corpse = game.createUnit(this.corpseUnitType, unit.getPlayerIndex(), unit.getX(),
					unit.getY(), unit.getFacing());
			if (corpse != null) {
				corpse.becomeCorpse(game);
				hold.addCorpse(game, unit, corpse);
			}
		}
	}
}
