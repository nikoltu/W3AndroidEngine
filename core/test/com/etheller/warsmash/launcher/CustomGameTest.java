package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The slots a custom game lobby offers and the launch it makes of them. */
class CustomGameTest {
	private static GameCatalog.MapDetails map(final boolean fixed, final int... types) {
		final GameCatalog.MapDetails details = new GameCatalog.MapDetails("Maps/test.w3x");
		details.fixedSettings = fixed;
		details.customForces = fixed;
		for (int i = 0; i < types.length; i++) {
			details.slots.add(new GameCatalog.Slot(i, types[i], 1, "Player " + i));
		}
		return details;
	}

	@Test
	void fixedSettingsLockOnlyTheComputersTheMapPlays() {
		final GameCatalog.MapDetails details = map(true, 1, 1, 2);
		assertTrue(CustomGame.typeEditable(details, 0));
		assertTrue(CustomGame.typeEditable(details, 1));
		assertFalse(CustomGame.typeEditable(details, 2));
	}

	@Test
	void anUnfixedMapLocksNothing() {
		final GameCatalog.MapDetails details = map(false, 1, 2);
		assertTrue(CustomGame.typeEditable(details, 0));
		assertTrue(CustomGame.typeEditable(details, 1));
	}

	@Test
	void aFixedMapLaunchesWithoutChangesUntilTheHostChangesASlot() {
		final GameCatalog.MapDetails details = map(true, 1, 1, 1, 2);
		final CustomGame lobby = new CustomGame();
		lobby.chooseSlots(details);
		assertEquals("", lobby.request(details).slots);
	}

	@Test
	void aFixedMapLaunchesTheSlotsTheHostClosedOrGaveAComputerKeepingTheMapsOwnSettings() {
		final GameCatalog.MapDetails details = map(true, 1, 1, 1, 1, 2);
		final CustomGame lobby = new CustomGame();
		lobby.chooseSlots(details);
		lobby.slotTypes[1] = 1;
		lobby.slotTypes[2] = 4;
		lobby.slotRaces[2] = 3;
		lobby.slotTeams[2] = 5;
		final List<LauncherSlot> slots = LauncherSlot.parse(lobby.request(details).slots);
		assertEquals(2, slots.size());
		assertEquals(1, slots.get(0).player);
		assertEquals("CLOSED", slots.get(0).type);
		assertEquals(2, slots.get(1).player);
		assertEquals("COMPUTER_INSANE", slots.get(1).type);
		for (final LauncherSlot slot : slots) {
			assertEquals(LauncherSlot.MAP_VALUE, slot.race);
			assertEquals(LauncherSlot.MAP_VALUE, slot.team);
			assertEquals(LauncherSlot.MAP_VALUE, slot.color);
		}
	}

	@Test
	void aFixedMapNeverLaunchesItsOwnComputersNorTheHostsSlot() {
		final GameCatalog.MapDetails details = map(true, 1, 1, 2);
		final CustomGame lobby = new CustomGame();
		lobby.chooseSlots(details);
		lobby.slotTypes[2] = 0;
		assertEquals("", lobby.request(details).slots);
		lobby.slotTypes[2] = 3;
		assertEquals("", lobby.request(details).slots);
	}

	@Test
	void anUnfixedMapLaunchesEveryVisibleSlotWithItsChoices() {
		final GameCatalog.MapDetails details = map(false, 1, 1, 3);
		final CustomGame lobby = new CustomGame();
		lobby.chooseSlots(details);
		final List<LauncherSlot> slots = LauncherSlot.parse(lobby.request(details).slots);
		assertEquals(2, slots.size());
		assertEquals("USER", slots.get(0).type);
		assertEquals("COMPUTER_NORMAL", slots.get(1).type);
		assertEquals(1, slots.get(0).race);
	}

	@Test
	void aFixedMapWhoseSlotsSkipNumbersLaunchesOnlyTheChosenComputersByPlayerNumber() {
		final GameCatalog.MapDetails details = new GameCatalog.MapDetails("Maps/test.w3x");
		details.fixedSettings = true;
		details.customForces = true;
		for (final int id : new int[] { 1, 2, 3, 4, 5, 7, 8, 9, 10, 11 }) {
			details.slots.add(new GameCatalog.Slot(id, 1, 1, "Player " + id));
		}
		final CustomGame lobby = new CustomGame();
		lobby.chooseSlots(details);
		lobby.slotTypes[1] = 4;
		lobby.slotTypes[8] = 4;
		final List<LauncherSlot> slots = LauncherSlot.parse(lobby.request(details).slots);
		assertEquals(2, slots.size());
		assertEquals(2, slots.get(0).player);
		assertEquals(10, slots.get(1).player);
	}

	private static GameCatalog.MapDetails selectableMap() {
		final GameCatalog.MapDetails details = new GameCatalog.MapDetails("Maps/test.w3x");
		details.fixedSettings = true;
		details.customForces = true;
		details.slots.add(new GameCatalog.Slot(0, 1, 0, "Player 0"));
		details.slots.add(new GameCatalog.Slot(1, 1, 0, "Player 1"));
		details.slots.add(new GameCatalog.Slot(2, 1, 2, "Player 2"));
		details.slots.add(new GameCatalog.Slot(3, 2, 0, "Computer"));
		return details;
	}

	@Test
	void aFixedMapLetsTheHostPickTheRaceOnlyWhereTheMapLeavesItSelectable() {
		final GameCatalog.MapDetails details = selectableMap();
		assertTrue(CustomGame.raceEditable(details, 0));
		assertTrue(CustomGame.raceEditable(details, 1));
		assertFalse(CustomGame.raceEditable(details, 2));
		assertFalse(CustomGame.raceEditable(details, 3));
		assertTrue(CustomGame.raceEditable(map(false, 1, 2), 1));
	}

	@Test
	void aFixedMapLaunchesThePlayersChosenRaceKeepingTeamAndColour() {
		final GameCatalog.MapDetails details = selectableMap();
		final CustomGame lobby = new CustomGame();
		lobby.chooseSlots(details);
		assertEquals("", lobby.request(details).slots);
		lobby.slotRaces[0] = 4;
		lobby.slotRaces[1] = 2;
		lobby.slotTypes[1] = 4;
		lobby.slotRaces[2] = 3;
		final List<LauncherSlot> slots = LauncherSlot.parse(lobby.request(details).slots);
		assertEquals(2, slots.size());
		assertEquals("USER", slots.get(0).type);
		assertEquals(4, slots.get(0).race);
		assertEquals("COMPUTER_INSANE", slots.get(1).type);
		assertEquals(2, slots.get(1).race);
		for (final LauncherSlot slot : slots) {
			assertEquals(LauncherSlot.MAP_VALUE, slot.team);
			assertEquals(LauncherSlot.MAP_VALUE, slot.color);
		}
	}
}
