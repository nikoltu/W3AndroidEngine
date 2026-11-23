package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

class CAbilityDropCommandCardTest {
	@Test
	void unloadAllAsksTheCommandCardForAPointYetStaysAcceptedFromScripts() {
		final CAbilityDrop drop = new CAbilityDrop(1, null, null, 80f);
		final BooleanAbilityTargetCheckReceiver<Void> receiver = BooleanAbilityTargetCheckReceiver.<Void>getInstance()
				.reset();
		drop.checkCanTargetNoTarget(null, null, 0, OrderIds.unloadall, false, receiver);
		assertTrue(receiver.isTargetable());
		assertTrue(drop.needsTargetFromCommandCard(OrderIds.unloadall));
		assertFalse(drop.needsTargetFromCommandCard(OrderIds.stop));
	}
}
