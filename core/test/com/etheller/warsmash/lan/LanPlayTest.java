package com.etheller.warsmash.lan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.protocol.LanData.LagEntry;
import net.warsmash.lan.protocol.LanData.ParticipantInfo;
import net.warsmash.lan.protocol.LanData.SlotInfo;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanTimings;

/** The rules of the waiting window: when it shows, and when the votes in it open. */
class LanPlayTest {
	private long now;
	private final List<Integer> votes = new ArrayList<Integer>();
	private final LanTimings timings = new LanTimings();

	private LanGameSetup setup(final int teamOfSecond, final boolean computerEnemy) {
		final LanGameSetup setup = new LanGameSetup();
		for (int i = 0; i < 2; i++) {
			final SlotInfo slot = new SlotInfo();
			slot.index = i;
			slot.kind = LanProtocol.SLOT_HUMAN;
			slot.team = i == 0 ? 0 : teamOfSecond;
			slot.color = i;
			setup.slots.add(slot);
			final ParticipantInfo p = new ParticipantInfo();
			p.id = i + 1;
			p.name = i == 0 ? "Host" : "Guest";
			p.slot = i;
			setup.participants.add(p);
		}
		if (computerEnemy) {
			final SlotInfo slot = new SlotInfo();
			slot.index = 2;
			slot.kind = LanProtocol.SLOT_COMPUTER;
			slot.team = 5;
			setup.slots.add(slot);
		}
		setup.hostOrder = new int[] { 1, 2 };
		return setup;
	}

	private LanPlay play(final LanGameSetup setup, final int me) {
		return new LanPlay(setup, me, this.timings, this.votes::add, () -> this.now);
	}

	private static List<LagEntry> lag(final int participant, final int waitedMs) {
		return Arrays.asList(new LagEntry(participant, LanProtocol.LAG_LAGGING, waitedMs));
	}

	@Test
	void theScoreScreenKnowsWhoPlaysWithAKeyboardAndMouse() {
		final LanGameSetup setup = setup(1, true);
		setup.participants.get(1).controlScheme = LanProtocol.CONTROL_KBM;
		final LanPlay play = play(setup, 1);
		assertFalse(play.usesKeyboardAndMouse(0));
		assertTrue(play.usesKeyboardAndMouse(1));
		assertFalse(play.usesKeyboardAndMouse(2), "a computer plays with neither");
		assertEquals(1 << 1, play.keyboardAndMouseSlots());
		play.onControlScheme(1, LanProtocol.CONTROL_KBM);
		play.onControlScheme(2, LanProtocol.CONTROL_TOUCH);
		assertTrue(play.usesKeyboardAndMouse(0));
		assertFalse(play.usesKeyboardAndMouse(1));
		assertEquals(1, play.keyboardAndMouseSlots());
	}

	@Test
	void aResyncAsksForTheGameToStartOverOnce() {
		final LanPlay play = play(setup(0, false), 2);
		assertFalse(play.takeRestart());
		play.onResync(LanProtocol.RESYNC_DESYNC, 40);
		assertTrue(play.takeRestart());
		assertFalse(play.takeRestart());
		assertFalse(play.isGivenUp());
	}

	@Test
	void aPlayerGivenUpSeesOneWindowAndNoLossOfTheHost() {
		final LanPlay play = play(setup(0, false), 2);
		play.onResync(LanProtocol.RESYNC_GIVE_UP, 40);
		play.onDisconnected("cannot stay in sync");
		assertTrue(play.isGivenUp());
		assertFalse(play.isHostLost());
		assertTrue(play.window().cannotSync);
		assertFalse(play.takeRestart());
	}

	@Test
	void noWindowWhileNobodyIsWaitedFor() {
		assertNull(play(setup(1, false), 1).window());
	}

	@Test
	void theWindowCountsTheWaitAndOpensContinueAfterThirtySeconds() {
		final LanPlay play = play(setup(0, false), 1);
		play.onLag(lag(2, 2000));
		LanPlay.Window w = play.window();
		assertNotNull(w);
		assertEquals(1, w.rows.size());
		assertEquals("Guest", w.rows.get(0).name);
		assertEquals(28, w.continueInSeconds);
		assertFalse(w.canContinue());
		this.now = 30000;
		w = play.window();
		assertTrue(w.canContinue());
		assertEquals(32000, w.rows.get(0).waitedMs);
		play.continueWithout();
		assertEquals(Arrays.asList(LanProtocol.VOTE_CONTINUE_WITHOUT), this.votes);
	}

	@Test
	void someoneWaitedForCannotContinueWithoutThemselves() {
		final LanPlay play = play(setup(0, false), 2);
		play.onLag(lag(2, 40000));
		assertEquals(-1, play.window().continueInSeconds);
	}

	@Test
	void theWindowClosesWhenTheWaitEnds() {
		final LanPlay play = play(setup(0, false), 1);
		play.onLag(lag(2, 2000));
		play.onLagEnd();
		assertNull(play.window());
	}

	@Test
	void teammatesGetNoClaim() {
		final LanPlay play = play(setup(0, false), 1);
		play.onLag(lag(2, 70000));
		assertEquals(-1, play.window().claimInSeconds);
	}

	@Test
	void claimOpensAfterAMinuteWhenTheOnlyOpponentIsWaitedFor() {
		final LanPlay play = play(setup(1, false), 1);
		play.onLag(lag(2, 10000));
		LanPlay.Window w = play.window();
		assertEquals(50, w.claimInSeconds);
		assertFalse(w.canClaim());
		play.onLag(lag(2, 60000));
		w = play.window();
		assertTrue(w.canClaim());
		play.claimVictory();
		assertEquals(Arrays.asList(LanProtocol.VOTE_CLAIM_VICTORY), this.votes);
	}

	@Test
	void aComputerOpponentTakesTheClaimAway() {
		final LanPlay play = play(setup(1, true), 1);
		play.onLag(lag(2, 90000));
		assertEquals(-1, play.window().claimInSeconds);
	}

	@Test
	void anAbsentOpponentIsClaimableWithoutAWait() {
		final LanPlay play = play(setup(1, false), 1);
		assertNull(play.window());
		play.onPresence(2, LanProtocol.PRESENCE_ABSENT);
		final LanPlay.Window w = play.window();
		assertNotNull(w);
		assertTrue(w.absentOnly);
		assertTrue(w.canClaim());
		play.onPresence(2, LanProtocol.PRESENCE_LEFT);
		assertNull(play.window());
	}

	@Test
	void anAbsentOpponentThatIsStillLoadingIsWaitedForNotClaimable() {
		final LanPlay play = play(setup(1, false), 2);
		play.onLoadProgress(1, 40);
		play.onPresence(1, LanProtocol.PRESENCE_ABSENT);
		assertEquals("Host is still loading the game.", play.pollMessage());
		LanPlay.Window w = play.window();
		assertNotNull(w);
		assertTrue(w.loading);
		assertFalse(w.absentOnly);
		assertEquals(-1, w.claimInSeconds);
		assertEquals(-1, w.continueInSeconds);
		assertEquals(40, w.rows.get(0).loadPercent);
		this.now += 10000;
		play.onLoadProgress(1, 55);
		assertEquals(55, play.window().rows.get(0).loadPercent);
		play.onLoadProgress(1, 100);
		w = play.window();
		assertNotNull(w);
		assertTrue(w.absentOnly, "a player that has loaded and is still absent is gone");
	}

	@Test
	void aLoaderThatFallsSilentIsAbsentAgain() {
		final LanPlay play = play(setup(1, false), 2);
		play.onLoadProgress(1, 40);
		play.onPresence(1, LanProtocol.PRESENCE_ABSENT);
		this.now += LanPlay.LOAD_REPORT_FRESH_MS + 1;
		final LanPlay.Window w = play.window();
		assertNotNull(w);
		assertFalse(w.loading);
		assertTrue(w.absentOnly);
	}

	@Test
	void turnsThatStopWithoutAWordMakeTheHostTheOneWaitedFor() {
		final LanPlay play = play(setup(1, false), 2);
		play.starved();
		assertNull(play.window());
		this.now = 2500;
		final LanPlay.Window w = play.window();
		assertNotNull(w);
		assertEquals("Host", w.rows.get(0).name);
		play.fed();
		assertNull(play.window());
	}

	@Test
	void aGameThatStandsStillOnPurposeWaitsForNobody() {
		final LanPlay play = play(setup(1, false), 2);
		this.now = 60000;
		play.idle();
		play.starved();
		assertNull(play.window());
	}

	@Test
	void theLossOfTheHostIsKeptAndHandedOutOnce() {
		final LanPlay play = play(setup(1, false), 2);
		play.onDisconnected("closed by host");
		assertTrue(play.window().hostLost);
		assertEquals(1, play.takeHostLoss());
		assertEquals(-1, play.takeHostLoss());
	}

	@Test
	void leavingOnPurposeIsNotALostHost() {
		final LanPlay play = play(setup(1, false), 2);
		play.onDisconnected(LanClient.LEFT_REASON);
		assertFalse(play.isHostLost());
		assertNull(play.window());
	}

	@Test
	void presenceChangesBecomeMessagesForOthersOnly() {
		final LanPlay play = play(setup(1, false), 1);
		play.onPresence(2, LanProtocol.PRESENCE_ABSENT);
		play.onPresence(2, LanProtocol.PRESENCE_PLAYING);
		play.onPresence(2, LanProtocol.PRESENCE_LEFT);
		play.onPresence(1, LanProtocol.PRESENCE_ABSENT);
		assertEquals("Guest is absent.", play.pollMessage());
		assertEquals("Guest is back in the game.", play.pollMessage());
		assertEquals("Guest has left the game.", play.pollMessage());
		assertNull(play.pollMessage());
		assertEquals(LanProtocol.PRESENCE_LEFT, play.presenceOf(2));
	}

	@Test
	void aMendedLinkTakesBackALostHostThatTheGameHasNotActedOn() {
		final LanPlay play = play(setup(1, false), 2);
		play.onDisconnected("closed by host");
		assertTrue(play.isHostLost());
		play.onReconnected();
		assertFalse(play.isHostLost());
		assertNull(play.window());
		play.onDisconnected("closed by host");
		assertEquals(1, play.takeHostLoss());
		play.onReconnected();
		assertTrue(play.isHostLost(), "the game already made the host's player leave");
	}

	@Test
	void whileTheGameIsPlayedThroughTheWindowShowsHowFarAndOldNewsIsSilent() {
		final LanPlay play = play(setup(1, false), 2);
		play.setCatchUp(true, 12 * 60 * 20 + 40 * 20, 18 * 60 * 20 + 5 * 20);
		final LanPlay.Window w = play.window();
		assertNotNull(w);
		assertEquals(70, w.rejoinPercent);
		assertEquals("12:40 / 18:05", w.rejoinTime);
		play.onPresence(1, LanProtocol.PRESENCE_ABSENT);
		assertNull(play.pollMessage());
		play.setCatchUp(false, 0, 0);
		final LanPlay.Window after = play.window();
		assertTrue(after == null || after.rejoinPercent < 0);
	}

	@Test
	void whileAHostIsLookedForTheGameWaitsAndTheLossIsNotActedOn() {
		final LanPlay play = play(setup(1, false), 1);
		play.onReconnecting();
		play.onMigrating();
		final LanPlay.Window w = play.window();
		assertTrue(w.migrating);
		assertFalse(w.hostLost);
		assertEquals(-1, play.takeHostLoss(), "the host's player does not leave on the player's own say");
		this.now = 60000;
		play.starved();
		assertTrue(play.window().migrating, "no wait for the old host's turns while one is looked for");
		play.onHostChanged(2);
		assertNull(play.window());
		assertEquals("Guest is now the host.", play.pollMessage());
		assertEquals(2, play.currentHost());
	}

	@Test
	void theNewHostIsTheOneWhoseLossIsActedOnIfNoHostIsFound() {
		final LanPlay play = play(setup(1, false), 1);
		play.onMigrating();
		play.onHostChanged(2);
		assertEquals("Guest is now the host.", play.pollMessage());
		play.onDisconnected("closed by host");
		assertTrue(play.window().hostLost);
		assertEquals(2, play.takeHostLoss());
	}

	@Test
	void theHostThatThePlayerIsItselfIsNotAnnounced() {
		final LanPlay play = play(setup(1, false), 2);
		play.onMigrating();
		play.onHostChanged(2);
		assertNull(play.pollMessage());
	}
}
