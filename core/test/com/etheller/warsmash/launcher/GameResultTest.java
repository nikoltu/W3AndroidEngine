package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The result file a game leaves the launcher, and the campaign result of both skins made from it. */
class GameResultTest {
	private static GameResult won() {
		final GameResult result = new GameResult();
		result.result = "victory";
		result.source = LaunchRequest.SOURCE_CAMPAIGN;
		result.map = "Maps\\Campaign\\Human01.w3m";
		result.profile = "Player \"One\"";
		result.race = 1;
		result.finished = 1762650000123L;
		result.mapName = "The Defense of Strahnbrad";
		result.seconds = 3725;
		result.difficulty = "hard";
		final GameResult.Score score = new GameResult.Score();
		score.heroScore = 1200;
		score.heroesKilled = 1;
		score.unitScore = 2575;
		score.unitsTrained = 14;
		score.unitsKilled = 23;
		score.structuresBuilt = 6;
		score.structuresRazed = 4;
		score.resourceScore = 3300;
		score.totalScore = 7075;
		score.goldMined = 3000;
		score.lumberHarvested = 900;
		result.score = score;
		return result;
	}

	@Test
	void everyFieldComesBack() {
		final GameResult read = GameResult.fromJson(won().toJson());
		assertEquals("victory", read.result);
		assertEquals(LaunchRequest.SOURCE_CAMPAIGN, read.source);
		assertEquals("Maps\\Campaign\\Human01.w3m", read.map);
		assertEquals("Player \"One\"", read.profile);
		assertNull(read.loadFailure);
		assertEquals(1, read.race);
		assertEquals(1762650000123L, read.finished);
		assertEquals("The Defense of Strahnbrad", read.mapName);
		assertEquals(3725, read.seconds);
		assertEquals("hard", read.difficulty);
		assertNotNull(read.score);
		assertEquals(1200, read.score.heroScore);
		assertEquals(1, read.score.heroesKilled);
		assertEquals(2575, read.score.unitScore);
		assertEquals(14, read.score.unitsTrained);
		assertEquals(23, read.score.unitsKilled);
		assertEquals(6, read.score.structuresBuilt);
		assertEquals(4, read.score.structuresRazed);
		assertEquals(3300, read.score.resourceScore);
		assertEquals(7075, read.score.totalScore);
		assertEquals(3000, read.score.goldMined);
		assertEquals(900, read.score.lumberHarvested);
	}

	@Test
	void aFailureKeepsItsReasonAndHasNoScore() {
		final GameResult failed = new GameResult();
		failed.result = "error";
		failed.loadFailure = "NEWER_GAME";
		final GameResult read = GameResult.fromJson(failed.toJson());
		assertEquals("error", read.result);
		assertEquals("NEWER_GAME", read.loadFailure);
		assertEquals("", read.map);
		assertNull(read.score);
		assertTrue(read.failed());
		assertFalse(CampaignResult.shows(read));
	}

	@Test
	void theFirstFormatStillReads() {
		final GameResult read = GameResult.fromJson("{\n  \"version\": 1,\n  \"result\": \"defeat\",\n"
				+ "  \"source\": \"campaign\",\n  \"map\": \"Maps\\\\Campaign\\\\Prologue02.w3m\",\n"
				+ "  \"profile\": \"Player\",\n  \"race\": 2,\n  \"finished\": 5\n}");
		assertEquals("defeat", read.result);
		assertEquals("Maps\\Campaign\\Prologue02.w3m", read.map);
		assertEquals(2, read.race);
		assertEquals(5, read.finished);
		assertEquals("", read.mapName);
		assertEquals(0, read.seconds);
		assertEquals("", read.difficulty);
		assertNull(read.score);
		// Without its numbers there is no banner to show.
		assertFalse(CampaignResult.shows(read));
	}

	@Test
	void textThatIsNoResultIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> GameResult.fromJson("not json {"));
		assertThrows(IllegalArgumentException.class, () -> GameResult.fromJson("[1, 2]"));
	}

	@Test
	void playTimeIsShownAsTheScoreScreenShowsIt() {
		final GameResult result = won();
		assertEquals("1:02:05", result.playTime());
		result.seconds = 11;
		assertEquals("0:00:11", result.playTime());
	}

	@Test
	void theBannerComesAfterACampaignMissionWonLostOrLeft() {
		final GameResult result = won();
		assertTrue(CampaignResult.shows(result));
		result.result = "defeat";
		assertTrue(CampaignResult.shows(result));
		result.result = "quit";
		assertTrue(CampaignResult.shows(result));
		assertTrue(result.lost());
		result.result = "exit";
		assertFalse(CampaignResult.shows(result));
		result.result = "victory";
		result.source = LaunchRequest.SOURCE_CUSTOM;
		assertFalse(CampaignResult.shows(result));
	}

	@Test
	void theRowsCountAsTheReforgedResultDoes() {
		final GameResult.Score score = won().score;
		assertArrayEquals(new int[] { 1200, 1 }, CampaignResult.numbers(score, 0));
		assertArrayEquals(new int[] { 2575, 23 }, CampaignResult.numbers(score, 1));
		assertArrayEquals(new int[] { 6, 4 }, CampaignResult.numbers(score, 2));
	}

	@Test
	void eachCampaignHasItsBanner() {
		assertEquals(SkinArt.RESULT_BANNER_HORDE, CampaignResult.banner("Tutorial"));
		assertEquals(SkinArt.RESULT_BANNER_HORDE, CampaignResult.banner("OrcEx"));
		assertEquals(SkinArt.RESULT_BANNER_HUMAN, CampaignResult.banner("Human"));
		assertEquals(SkinArt.RESULT_BANNER_UNDEAD, CampaignResult.banner("UndeadEx"));
		assertEquals(SkinArt.RESULT_BANNER_NIGHTELF, CampaignResult.banner("NightElf"));
		assertEquals(SkinArt.RESULT_BANNER_FK, CampaignResult.banner("UndeadRe"));
		assertEquals(SkinArt.RESULT_BANNER_RANDOM, CampaignResult.banner("Custom"));
		assertEquals(SkinArt.DIFFICULTY_HARD, CampaignResult.difficulty("insane")[0]);
		assertNull(CampaignResult.difficulty(""));
	}

	@Test
	void theBannerStaysOnTheScreen() {
		final CampaignResult.Layout tablet = new CampaignResult.Layout(2960 - 100, 2960, 1848);
		assertEquals(2960 - tablet.w, tablet.x, 0.01f);
		final CampaignResult.Layout phone = new CampaignResult.Layout(1100, 1600, 720);
		assertTrue(phone.x >= 0);
		assertTrue(phone.difficultyIconY + phone.difficultyIcon < phone.h);
		assertTrue(phone.detailsX + (2 * phone.columnW) <= phone.contentX + phone.contentW);
	}

	@Test
	void theClassicResultFitsItsPanel() {
		// The mission list's place on the tablet and on the phone, with the frame and buttons of each.
		assertClassicFits(new CampaignResult.ClassicLayout(1956, 208, 924, 1432, 40, 112));
		assertClassicFits(new CampaignResult.ClassicLayout(948, 130, 600, 582, 20, 96));
		// A window narrower than tall keeps the text inside the panel.
		assertClassicFits(new CampaignResult.ClassicLayout(0, 0, 300, 1400, 20, 96));
	}

	private static void assertClassicFits(final CampaignResult.ClassicLayout l) {
		assertTrue(l.contentX > l.x);
		assertTrue(l.contentX + l.contentW < l.x + l.w);
		assertTrue(l.titleY > l.y);
		assertTrue(l.titleY + l.titleH <= l.lineY[0]);
		assertTrue(l.lineY[0] + l.lineH <= l.rowY[0]);
		for (int i = 1; i < l.rowY.length; i++) {
			assertTrue(l.rowY[i - 1] + l.rowH <= l.rowY[i]);
		}
		assertTrue(l.valueY + l.valueH <= l.rowH);
		assertTrue(l.rowY[l.rowY.length - 1] + l.rowH <= l.nameY);
		assertTrue(l.nameY + l.nameH <= l.lineY[1]);
		assertTrue(l.lineY[1] + l.lineH <= l.durationY);
		assertTrue(l.durationY + l.durationH <= l.difficultyY);
		assertTrue(l.difficultyY + l.difficultyH < l.buttonY);
		assertTrue(l.buttonY + l.buttonH < l.y + l.h);
		assertTrue(l.buttonX >= l.contentX);
		assertTrue(l.buttonX + l.buttonW <= l.contentX + l.contentW + 0.01f);
		assertEquals(l.contentX + l.contentW, l.columnX[1] + l.columnW, 0.01f);
	}

	@Test
	void theTitleSaysVictoryOrDefeat() {
		final GameResult result = won();
		assertEquals("VICTORY", CampaignResult.title(result)[0]);
		result.result = "defeat";
		assertEquals("DEFEAT", CampaignResult.title(result)[0]);
	}

	@Test
	void campaignOfTheGamePlayedComesBackAndOlderResultsDoNotTellIt() {
		final GameResult result = won();
		assertEquals(GameResult.CAMPAIGN_UNKNOWN, GameResult.fromJson(result.toJson()).campaign);
		result.campaign = 3;
		assertEquals(3, GameResult.fromJson(result.toJson()).campaign);
		result.campaign = -1;
		assertEquals(-1, GameResult.fromJson(result.toJson()).campaign);
	}

	@Test
	void resultFollowsTheCampaignOfTheGamePlayedNotOfTheOneStarted() {
		final LaunchRequest started = new LaunchRequest();
		started.campaign = 1;
		started.mission = 2;
		final GameResult played = new GameResult();
		assertEquals(LaunchRequest.SOURCE_CAMPAIGN, started.source(played));
		played.campaign = 4;
		assertEquals(LaunchRequest.SOURCE_CAMPAIGN, started.source(played));
		played.campaign = -1;
		assertEquals(LaunchRequest.SOURCE_LOAD, started.source(played));
		assertEquals(LaunchRequest.SOURCE_CAMPAIGN, started.source(null));
	}

	@Test
	void campaignSaveLoadedInACustomGameEndsAsThatCampaignsMission() {
		final LaunchRequest custom = new LaunchRequest();
		custom.customMap = "Maps/Skirmish.w3x";
		final GameResult played = new GameResult();
		played.campaign = -1;
		assertEquals(LaunchRequest.SOURCE_CUSTOM, custom.source(played));
		played.campaign = 2;
		assertEquals(LaunchRequest.SOURCE_CAMPAIGN, custom.source(played));
		assertTrue(CampaignResult.shows(withSource(played, custom)));
	}

	private static GameResult withSource(final GameResult played, final LaunchRequest request) {
		played.source = request.source(played);
		played.result = "victory";
		played.score = new GameResult.Score();
		return played;
	}
}
