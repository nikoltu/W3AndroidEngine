package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold.CAbilityCargoHoldCorpses;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

/**
 * Drop All Corpses: the Meat Wagon empties its cargo hold on the spot at once.
 */
public class CAbilityDropCorpses extends CAbilityNoTargetSpellBase {

	public CAbilityDropCorpses(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.unloadallcorpses;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if (orderId == getBaseOrderId()) {
			final CAbilityCargoHoldCorpses hold = caster.getFirstAbilityOfType(CAbilityCargoHoldCorpses.class);
			if (hold != null) {
				hold.unloadAllInstant(game, caster);
			}
			return false;
		}
		return super.checkBeforeQueue(game, caster, playerIndex, orderId, autoOrder, target);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		return false;
	}

	@Override
	public boolean isMagic() {
		return false;
	}

	@Override
	public boolean isUniversal() {
		return true;
	}
}
