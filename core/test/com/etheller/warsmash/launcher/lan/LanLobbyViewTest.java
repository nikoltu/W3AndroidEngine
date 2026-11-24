package com.etheller.warsmash.launcher.lan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;

class LanLobbyViewTest {
	private static LanMessages.LobbyState lobby(final int slots) {
		final LanMessages.LobbyState s = new LanMessages.LobbyState();
		s.ownerId = 0;
		s.options.observers = LanProtocol.OBSERVERS_REFEREES;
		s.map.sha256 = new byte[32];
		for (int i = 0; i < slots; i++) {
			final LanData.SlotInfo slot = new LanData.SlotInfo();
			slot.index = i;
			slot.team = i;
			s.slots.add(slot);
		}
		return s;
	}

	private static void human(final LanMessages.LobbyState s, final int slot, final int id, final String name) {
		final LanData.SlotInfo info = s.slots.get(slot);
		info.kind = LanProtocol.SLOT_HUMAN;
		info.participantId = id;
		info.name = name;
	}

	private static void watcher(final LanMessages.LobbyState s, final int id, final String name, final int role) {
		final LanData.ObserverInfo o = new LanData.ObserverInfo();
		o.participantId = id;
		o.name = name;
		o.role = role;
		s.observers.add(o);
	}

	@Test
	void theNextHostIsAPlayerThenAReferee_thenAnObserver() {
		final LanMessages.LobbyState s = lobby(4);
		human(s, 0, 0, "Host");
		human(s, 2, 5, "Third");
		watcher(s, 3, "Obs", LanProtocol.ROLE_OBSERVER);
		watcher(s, 4, "Ref", LanProtocol.ROLE_REFEREE);
		assertEquals("Third", LanLobbyView.of(s, 0, null, -1).nextHostName());
		s.slots.get(2).kind = LanProtocol.SLOT_OPEN;
		assertEquals("Ref", LanLobbyView.of(s, 0, null, -1).nextHostName());
		s.observers.remove(1);
		assertEquals("Obs", LanLobbyView.of(s, 0, null, -1).nextHostName());
		s.observers.clear();
		assertEquals(null, LanLobbyView.of(s, 0, null, -1).nextHostName());
	}

	@Test
	void everyoneShowsThePortraitTheyChose() {
		final LanMessages.LobbyState s = lobby(3);
		human(s, 0, 0, "Host");
		human(s, 1, 4, "Guest");
		human(s, 2, 6, "Plain");
		watcher(s, 5, "Ref", LanProtocol.ROLE_REFEREE);
		s.portraits.put(0, "7");
		s.portraits.put(4, "0");
		s.portraits.put(5, "21");
		final LanLobbyView view = LanLobbyView.of(s, 4, null, -1);
		assertEquals(7, view.rows.get(0).portrait);
		assertEquals(0, view.rows.get(1).portrait);
		assertEquals(-1, view.rows.get(2).portrait);
		assertEquals(21, view.watchers.get(0).portrait);
	}

	@Test
	void onlyTheHostRemovesWatchers() {
		final LanMessages.LobbyState s = lobby(4);
		human(s, 0, 0, "Host");
		human(s, 1, 1, "Guest");
		watcher(s, 3, "Obs", LanProtocol.ROLE_OBSERVER);
		assertTrue(LanLobbyView.of(s, 0, null, -1).watchers.get(0).kickable);
		assertFalse(LanLobbyView.of(s, 1, null, -1).watchers.get(0).kickable);
		assertFalse(LanLobbyView.of(s, 3, null, -1).watchers.get(0).kickable);
	}

	@Test
	void aTeamBeyondTheSeatsIsShownWithinThem() {
		final LanMessages.LobbyState s = lobby(4);
		s.slots.get(2).team = 9;
		assertEquals(3, LanLobbyView.of(s, 0, null, -1).rows.get(2).team);
	}

	@Test
	void theColorsOfTheOtherPlayersAndComputersAreTaken() {
		final LanMessages.LobbyState s = lobby(4);
		human(s, 0, 0, "Host");
		human(s, 1, 5, "Guest");
		s.slots.get(2).kind = LanProtocol.SLOT_COMPUTER;
		s.slots.get(0).color = 3;
		s.slots.get(1).color = 7;
		s.slots.get(2).color = 9;
		s.slots.get(3).color = 11;
		final boolean[] taken = LanLobbyView.of(s, 0, null, -1).takenColors(0);
		assertFalse(taken[3]);
		assertTrue(taken[7]);
		assertTrue(taken[9]);
		assertFalse(taken[11]);
	}
}
