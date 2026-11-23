package com.etheller.warsmash.lan.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.lan.LanPlay;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.protocol.LanData.LagEntry;
import net.warsmash.lan.protocol.LanData.ParticipantInfo;
import net.warsmash.lan.protocol.LanData.SlotInfo;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanTimings;

/** What the waiting window says and when it has to be built again. */
class LanOverlayTest {
	private static LanPlay.Window window(final long waited) {
		final LanGameSetup setup = new LanGameSetup();
		for (int i = 0; i < 2; i++) {
			final SlotInfo slot = new SlotInfo();
			slot.index = i;
			slot.kind = LanProtocol.SLOT_HUMAN;
			slot.team = i;
			slot.color = i;
			setup.slots.add(slot);
			final ParticipantInfo p = new ParticipantInfo();
			p.id = i + 1;
			p.name = i == 0 ? "Host" : "Guest";
			p.slot = i;
			setup.participants.add(p);
		}
		final LanPlay play = new LanPlay(setup, 1, new LanTimings(), vote -> {
		}, () -> 0L);
		play.onLag(Arrays.asList(new LagEntry(2, LanProtocol.LAG_BACKGROUND, (int) waited)));
		return play.window();
	}

	@Test
	void theTextNamesWhoIsWaitedForWhyAndForHowLong() {
		final String text = LanOverlay.textOf(window(12500));
		assertTrue(text.startsWith("Waiting for players..."));
		assertTrue(text.contains("Guest|r - app in background - 12 s"), text);
		assertTrue(text.contains("|cff0042ff"), "in the player's color: " + text);
	}

	@Test
	void theButtonsCountDownAndOpen() {
		assertEquals("Continue without them (18)", LanOverlay.continueLabelOf(window(12000)));
		assertEquals("Continue without them", LanOverlay.continueLabelOf(window(31000)));
		assertEquals("Claim victory (48)", LanOverlay.claimLabelOf(window(12000)));
		assertEquals("Claim victory", LanOverlay.claimLabelOf(window(61000)));
	}

	@Test
	void theWindowIsBuiltAgainOnlyWhenItsFramesChange() {
		assertEquals(LanOverlay.layoutOf(window(1000)), LanOverlay.layoutOf(window(50000)));
	}

	@Test
	void aLostHostHasItsOwnLayout() {
		final LanGameSetup setup = new LanGameSetup();
		final LanPlay play = new LanPlay(setup, 1, new LanTimings(), vote -> {
		}, () -> 0L);
		play.onDisconnected("x");
		assertNotEquals(LanOverlay.layoutOf(window(1000)), LanOverlay.layoutOf(play.window()));
		assertTrue(LanOverlay.textOf(play.window()).contains("host has left"));
	}

	@Test
	void aHostThatIsBeingLookedForHasItsOwnWindow() {
		final LanGameSetup setup = new LanGameSetup();
		final LanPlay play = new LanPlay(setup, 1, new LanTimings(), vote -> {
		}, () -> 0L);
		play.onMigrating();
		assertNotEquals(LanOverlay.layoutOf(window(1000)), LanOverlay.layoutOf(play.window()));
		assertEquals("Host migration...", LanOverlay.textOf(play.window()));
	}

	@Test
	void aPlayerThatIsStillLoadingIsShownLoadingWithItsPercent() {
		final LanGameSetup setup = new LanGameSetup();
		final SlotInfo slot = new SlotInfo();
		slot.kind = LanProtocol.SLOT_HUMAN;
		setup.slots.add(slot);
		for (int i = 0; i < 2; i++) {
			final ParticipantInfo p = new ParticipantInfo();
			p.id = i + 1;
			p.name = i == 0 ? "Host" : "Guest";
			p.slot = 0;
			setup.participants.add(p);
		}
		final LanPlay play = new LanPlay(setup, 2, new LanTimings(), vote -> {
		}, () -> 0L);
		play.onLoadProgress(1, 35);
		play.onPresence(1, LanProtocol.PRESENCE_ABSENT);
		final String text = LanOverlay.textOf(play.window());
		assertTrue(text.startsWith("Waiting for players to load..."));
		assertTrue(text.contains("Host|r - loading - 35 %"));
		assertNotEquals(LanOverlay.layoutOf(window(1000)), LanOverlay.layoutOf(play.window()));
	}
}
