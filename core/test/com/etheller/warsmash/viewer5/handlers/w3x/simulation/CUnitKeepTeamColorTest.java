package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

class CUnitKeepTeamColorTest {
	private static CSimulation simulation(final int... colors) {
		final CSimulation simulation = TestObjects.allocate(CSimulation.class);
		final List<CPlayer> players = new ArrayList<>();
		for (final int color : colors) {
			final CPlayer player = TestObjects.allocate(CPlayer.class);
			TestObjects.set(player, "color", color);
			players.add(player);
		}
		TestObjects.set(simulation, "players", players);
		return simulation;
	}

	private static CUnit unit(final int playerIndex) {
		final CUnit unit = TestObjects.allocate(CUnit.class);
		TestObjects.set(unit, "playerIndex", playerIndex);
		TestObjects.set(unit, "teamColorOverride", -1);
		return unit;
	}

	@Test
	void givenUnitKeepsTheColorOfItsOldOwner() {
		final CUnit unit = unit(0);
		unit.keepShownTeamColor(simulation(5, 9), 1);
		assertEquals(5, unit.getTeamColorOverride());
	}

	@Test
	void colorChosenByScriptIsNotReplaced() {
		final CUnit unit = unit(0);
		unit.setTeamColorOverride(3);
		unit.keepShownTeamColor(simulation(5, 9), 1);
		assertEquals(3, unit.getTeamColorOverride());
	}

	@Test
	void givingTheUnitToItsOwnPlayerChangesNothing() {
		final CUnit unit = unit(1);
		unit.keepShownTeamColor(simulation(5, 9), 1);
		assertEquals(-1, unit.getTeamColorOverride());
	}
}
