package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;

/** The Reforged campaign list's prologue goes on past the classic one; the classic version keeps its two chapters. */
class ReforgedMissionsTest {
	@TempDir
	File temp;

	private static final String CLASSIC = "[Index]\r\nCampaignList=\"Tutorial\",\"Human\"\r\n"
			+ "[Tutorial]\r\nHeader=\"CAMP_TUTORIAL_HEADER\"\r\nName=\"CAMP_TUTORIAL_NAME\"\r\nDefaultOpen=1\r\n"
			+ "Mission0=\"CAMP_CHAPTER_1\",\"CAMP_MAP_PROLOGUE01\",\"Campaign/Classic/ROC/Prologue01.w3m\"\r\n"
			+ "Mission1=\"CAMP_CHAPTER_2\",\"CAMP_MAP_PROLOGUE02\",\"Campaign/Classic/ROC/Prologue02.w3m\"\r\n"
			+ "[Human]\r\nHeader=\"CAMP_HUMAN_HEADER\"\r\nName=\"CAMP_HUMAN_NAME\"\r\nDefaultOpen=0\r\n"
			+ "Mission0=\"CAMP_CHAPTER_1\",\"CAMP_MAP_HUMAN01\",\"Campaign/Classic/ROC/Human01.w3m\"\r\n";

	private static final String REFORGED = "[Index]\r\nCampaignList=\"Tutorial\",\"Human\"\r\n"
			+ "[Tutorial]\r\nHeader=\"CAMP_TUTORIAL_HEADER\"\r\nName=\"CAMP_TUTORIAL_NAME\"\r\nDefaultOpen=1\r\n"
			+ "Mission0=\"CAMP_CHAPTER_1\",\"CAMP_MAP_PROLOGUE01\",\"Campaign/Reforged/ROC/Prologue01.w3x\"\r\n"
			+ "Mission1=\"CAMP_CHAPTER_2\",\"CAMP_MAP_PROLOGUE02\",\"Campaign/Reforged/ROC/Prologue02.w3x\"\r\n"
			+ "Mission2=\"CAMP_CHAPTER_3\",\"CAMP_MAP_PROLOGUE03\",\"Campaign/Reforged/ROC/Prologue03.w3x\"\r\n"
			+ "Mission3=\"CAMP_CHAPTER_4\",\"CAMP_MAP_PROLOGUE04\",\"Campaign/Reforged/ROC/Prologue04.w3x\"\r\n"
			+ "Mission4=\"CAMP_CHAPTER_5\",\"CAMP_MAP_PROLOGUE05\",\"Campaign/Reforged/ROC/Prologue05.w3x\"\r\n"
			+ "[Human]\r\nHeader=\"CAMP_HUMAN_HEADER\"\r\nName=\"CAMP_HUMAN_NAME\"\r\nDefaultOpen=0\r\n"
			+ "Mission0=\"CAMP_CHAPTER_1\",\"CAMP_MAP_HUMAN01\",\"Campaign/Reforged/ROC/Human01.w3x\"\r\n";

	private void write(final String path, final String text) throws IOException {
		final File file = new File(this.temp, path);
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private CampaignCatalog catalog(final boolean reforgedList) throws IOException {
		write("UI/CampaignInfoClassic.txt", CLASSIC);
		if (reforgedList) {
			write("UI/CampaignInfoReforged.txt", REFORGED);
		}
		for (int i = 1; i <= 5; i++) {
			write("Campaign/Reforged/ROC/Prologue0" + i + ".w3x", "map");
		}
		return CampaignCatalog.load(new FolderDataSource(this.temp.toPath()), "UI\\CampaignInfo.txt");
	}

	private static long key(final int campaign, final int mission) {
		return (((long) campaign) << 32) | (mission & 0xFFFFFFFFL);
	}

	/** The prologue's missions made available, nothing recorded won. */
	private static CCampaignProgress.Snapshot progress(final int... missions) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(4);
		out.writeInt(1);
		out.writeUTF(CCampaignProgress.CampaignList.COMBINED.name());
		out.writeInt(missions.length);
		for (final int mission : missions) {
			out.writeLong(key(0, mission));
		}
		for (int flags = 0; flags < 3; flags++) {
			out.writeInt(0);
		}
		out.writeBoolean(false);
		out.writeInt(-1);
		out.writeInt(-1);
		out.writeInt(0);
		out.flush();
		return CCampaignProgress.readSnapshot(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
				CCampaignProgress.CampaignList.COMBINED);
	}

	@Test
	void reforgedPrologueHasFiveChaptersAndClassicTwo() throws IOException {
		final CampaignCatalog catalog = catalog(true);
		final CampaignCatalog.Entry prologue = catalog.getEntry(0);
		assertEquals(2, prologue.getMissions(false).size());
		assertEquals(5, prologue.getMissions(true).size());
		assertEquals("CAMP_MAP_PROLOGUE03", prologue.getMissions(true).get(2).getMissionName());
		assertEquals("CAMP_MAP_PROLOGUE05", prologue.getMissions(true).get(4).getMissionName());
		assertEquals(1, catalog.getEntry(1).getMissions(true).size());
		assertEquals("Campaign/Reforged/ROC/Prologue01.w3x", catalog.missionMapFilename(prologue, 0, true));
		assertEquals("Campaign/Classic/ROC/Prologue02.w3m", catalog.missionMapFilename(prologue, 1, false));
		assertEquals("Campaign/Reforged/ROC/Prologue03.w3x", catalog.missionMapFilename(prologue, 2, true));
		assertNull(catalog.missionMapFilename(prologue, 2, false));
		assertNull(catalog.missionMapFilename(prologue, 5, true));
	}

	@Test
	void departuresVictoryLeadsOnToRidersOnTheStormOnlyInReforged() throws IOException {
		final CampaignCatalog catalog = catalog(true);
		final CampaignCatalog.Entry prologue = catalog.getEntry(0);
		final CCampaignProgress.Snapshot progress = progress(1, 2);
		assertTrue(CampaignCatalog.isMissionOpen(prologue, 2, progress));
		assertTrue(CampaignCatalog.isMissionWon(prologue, 1, progress, true));
		assertFalse(CampaignCatalog.isMissionWon(prologue, 2, progress, true));
		assertFalse(CampaignCatalog.isCompleted(prologue, progress, true));
		assertEquals(2, CampaignChoice.furthestMission(prologue, progress, true));
		assertEquals(1, CampaignChoice.furthestMission(prologue, progress, false));
		final int played = CampaignResult.playedMission(catalog, prologue, "Campaign\\Reforged\\ROC\\Prologue02.w3x");
		assertEquals(1, played);
		assertEquals(2, CampaignResult.continueMission(prologue, progress, played, true, true));
		assertEquals(-1, CampaignResult.continueMission(prologue, progress, played, true, false));
		assertEquals(2, CampaignResult.playedMission(catalog, prologue, "Campaign/Reforged/ROC/Prologue03.w3x"));
		assertEquals(4, CampaignResult.playedMission(catalog, prologue, "Campaign\\Reforged\\ROC\\Prologue05.w3x"));
		assertEquals(0, CampaignResult.campaignOfMap(catalog, "Campaign\\Reforged\\ROC\\Prologue04.w3x").getNumber());
	}

	@Test
	void reforgedMissionsCountWonFromTheirOwnLastChapter() throws IOException {
		final CampaignCatalog.Entry prologue = catalog(true).getEntry(0);
		final CCampaignProgress.Snapshot progress = progress(1, 2, 3, 4);
		assertTrue(CampaignCatalog.isMissionWon(prologue, 3, progress, true));
		assertFalse(CampaignCatalog.isMissionWon(prologue, 4, progress, true));
		// The classic list ends with Departures, which no mission of its own campaign tells won.
		assertFalse(CampaignCatalog.isMissionWon(prologue, 1, progress(1), false));
	}

	@Test
	void glueListShowsTheChosenVersionsOpenMissions() throws IOException {
		final CampaignCatalog.Entry prologue = catalog(true).getEntry(0);
		final CCampaignProgress.Snapshot progress = progress(1, 2, 3);
		// Classic: the two chapters, whatever else the progress has opened.
		assertTrue(CampaignCatalog.isMissionListed(prologue, 0, false, false, progress));
		assertTrue(CampaignCatalog.isMissionListed(prologue, 1, false, false, progress));
		assertFalse(CampaignCatalog.isMissionListed(prologue, 2, false, false, progress));
		// Reforged: all five chapters are in the list, those opened show.
		assertTrue(CampaignCatalog.isMissionListed(prologue, 2, true, false, progress));
		assertTrue(CampaignCatalog.isMissionListed(prologue, 3, true, false, progress));
		assertFalse(CampaignCatalog.isMissionListed(prologue, 4, true, false, progress));
		assertFalse(CampaignCatalog.isMissionListed(prologue, 5, true, true, progress));
		// A campaign opened by hand lists every mission of the version.
		assertTrue(CampaignCatalog.isMissionListed(prologue, 4, true, true, progress));
		assertFalse(CampaignCatalog.isMissionListed(prologue, 4, false, true, progress));
		// A campaign that is not open lists nothing.
		assertFalse(CampaignCatalog.isMissionListed(catalog(true).getEntry(1), 0, true, false, progress));
		assertEquals(Profiles.campaignMapsKey("A"), Profiles.campaignMapsKey("A"));
		assertEquals("profile.campaignmaps.A", Profiles.campaignMapsKey("A"));
	}

	@Test
	void dataWithoutTheReforgedListKeepsTheClassicMissions() throws IOException {
		final CampaignCatalog catalog = catalog(false);
		final CampaignCatalog.Entry prologue = catalog.getEntry(0);
		assertFalse(catalog.hasReforgedMaps());
		assertEquals(2, prologue.getMissions(true).size());
		assertNull(catalog.missionMapFilename(prologue, 2, true));
	}
}
