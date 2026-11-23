package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;

/**
 * A new profile can play the prologue and the human campaign from the start, in both versions of the campaign maps
 * and with both kinds of campaign lists; the campaigns after them wait for the missions that open them.
 */
class NewProfileCampaignsTest {
	@TempDir
	File temp;

	private static String section(final String name, final int defaultOpen, final String... maps) {
		final StringBuilder text = new StringBuilder("[" + name + "]\r\nHeader=\"H\"\r\nName=\"N\"\r\nDefaultOpen="
				+ defaultOpen + "\r\n");
		for (int i = 0; i < maps.length; i++) {
			text.append("Mission").append(i).append("=\"C\",\"M\",\"").append(maps[i]).append("\"\r\n");
		}
		return text.toString();
	}

	/** As the combined list of the game data since 1.32 has it. */
	private static final String COMBINED = "[Index]\r\nCampaignList=\"\",\"Tutorial\",\"Human\",\"Undead\",\"\","
			+ "\"NightElfEx\",\"HumanEx\"\r\n"
			+ section("Tutorial", 1, "Campaign/Classic/ROC/Prologue01.w3m", "Campaign/Classic/ROC/Prologue02.w3m")
			+ section("Human", 1, "Campaign/Classic/ROC/Human01.w3m", "Campaign/Classic/ROC/Human02.w3m")
			+ section("Undead", 0, "Campaign/Classic/ROC/Undead01.w3m")
			+ section("NightElfEx", 1, "Campaign/Classic/TFT/NightElfX01.w3x")
			+ section("HumanEx", 0, "Campaign/Classic/TFT/HumanX01.w3x");

	/** The Reforged list keeps the human campaign closed by its own flag; the game's screens list it open anyway. */
	private static final String REFORGED = "[Index]\r\nCampaignList=\"\",\"Tutorial\",\"Human\",\"Undead\",\"\","
			+ "\"NightElfEx\",\"HumanEx\"\r\n"
			+ section("Tutorial", 1, "Campaign/Reforged/ROC/Prologue01.w3x", "Campaign/Reforged/ROC/Prologue02.w3x",
					"Campaign/Reforged/ROC/Prologue03.w3x")
			+ section("Human", 0, "Campaign/Reforged/ROC/Human01.w3x", "Campaign/Reforged/ROC/Human02.w3x")
			+ section("Undead", 0, "Campaign/Reforged/ROC/Undead01.w3x")
			+ section("NightElfEx", 1, "Campaign/Reforged/TFT/NightElfX01.w3x")
			+ section("HumanEx", 0, "Campaign/Reforged/TFT/HumanX01.w3x");

	/** The Reign of Chaos list of the versions before 1.32. */
	private static final String REIGN_OF_CHAOS = "[Index]\r\nCampaignList=\"Tutorial\",\"Human\",\"Undead\"\r\n"
			+ section("Tutorial", 1, "Maps/Campaign/Prologue01.w3m", "Maps/Campaign/Prologue02.w3m")
			+ section("Human", 1, "Maps/Campaign/Human01.w3m", "Maps/Campaign/Human02.w3m")
			+ section("Undead", 0, "Maps/Campaign/Undead01.w3m");

	private void write(final String path, final String text) throws IOException {
		final File file = new File(this.temp, path);
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private static void assertNewProfile(final CampaignCatalog catalog, final boolean reforged,
			final String... closed) {
		final CCampaignProgress.Snapshot progress = CCampaignProgress.emptySnapshot();
		for (final CampaignCatalog.Entry entry : catalog.getEntries()) {
			final String section = entry.getSection();
			final boolean open = section.equals("Tutorial") || section.equals("Human")
					|| section.equals("NightElfEx");
			assertEquals(open, CampaignCatalog.isOpen(entry, progress), section);
			assertEquals(open, CampaignCatalog.isMissionOpen(entry, 0, progress), section);
			for (int mission = 1; mission < entry.getMissions(reforged).size(); mission++) {
				assertFalse(CampaignCatalog.isMissionOpen(entry, mission, progress), section + " " + mission);
			}
			assertFalse(CampaignCatalog.isMissionWon(entry, 0, progress, reforged), section);
		}
		for (final String section : closed) {
			boolean listed = false;
			for (final CampaignCatalog.Entry entry : catalog.getEntries()) {
				listed |= entry.getSection().equals(section);
			}
			assertTrue(listed, section);
		}
		// The launcher opens on the prologue, the first open campaign.
		assertEquals("Tutorial", CampaignChoice.chosenCampaign(catalog, progress, null).getSection());
		assertEquals("Tutorial", CampaignChoice.playedCampaign(catalog, progress).getSection());
	}

	@Test
	void combinedListOpensPrologueAndHumanInBothVersions() throws IOException {
		write("UI/CampaignInfoClassic.txt", COMBINED);
		write("UI/CampaignInfoReforged.txt", REFORGED);
		write("Campaign/Reforged/ROC/Human01.w3x", "map");
		final CampaignCatalog catalog = CampaignCatalog.load(new FolderDataSource(this.temp.toPath()),
				"UI\\CampaignInfo.txt");
		assertTrue(catalog.hasReforgedMaps());
		assertTrue(catalog.isUnified());
		assertNewProfile(catalog, false, "Undead", "HumanEx");
		assertNewProfile(catalog, true, "Undead", "HumanEx");
		final CampaignCatalog.Entry human = catalog.getEntry(1);
		assertEquals("Campaign/Reforged/ROC/Human01.w3x", catalog.missionMapFilename(human, 0, true));
		assertEquals("Campaign/Classic/ROC/Human01.w3m", catalog.missionMapFilename(human, 0, false));
	}

	@Test
	void reignOfChaosListOpensPrologueAndHuman() throws IOException {
		write("UI/CampaignStrings.txt", REIGN_OF_CHAOS);
		final CampaignCatalog catalog = CampaignCatalog.load(new FolderDataSource(this.temp.toPath()),
				"UI\\CampaignStrings.txt");
		assertTrue(catalog.isClassicList());
		assertNewProfile(catalog, false, "Undead");
	}
}
