package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;

class CAbilityCargoHoldVersionTest {
	@Test
	void versionChangesWithEveryLoadAndUnloadAndIndexesStayInListOrder() {
		final CAbilityCargoHold hold = new CAbilityCargoHold(1, null, null, 8, 0, 0, EnumSet.noneOf(CTargetType.class));
		final CUnit transport = TestObjects.allocate(CUnit.class);
		final CUnit first = TestObjects.allocate(CUnit.class);
		final CUnit second = TestObjects.allocate(CUnit.class);
		int version = hold.getCargoVersion();
		hold.addUnit(transport, first);
		assertNotEquals(version, version = hold.getCargoVersion());
		hold.addUnit(transport, second);
		assertNotEquals(version, version = hold.getCargoVersion());
		assertSame(first, hold.getUnit(0));
		assertSame(second, hold.getUnit(1));
		hold.removeUnitAtIndex(transport, 0);
		assertNotEquals(version, hold.getCargoVersion());
		assertEquals(1, hold.getCargoCount());
		assertSame(second, hold.getUnit(0));
	}
}
