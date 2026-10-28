package com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup;

import net.warsmash.uberserver.LobbyPlayerType;

public interface PlayerSlotPaneListener {
	void setPlayerSlot(int index, LobbyPlayerType lobbyPlayerType);

	void setPlayerRace(int index, int raceItemIndex);

	// The Battle.net lobby protocol has no messages for these yet.
	default void setPlayerTeam(final int index, final int team) {
	}

	default void setPlayerHandicap(final int index, final float handicap) {
	}
}
