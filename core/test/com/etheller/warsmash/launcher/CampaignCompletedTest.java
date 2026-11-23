package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;

/**
 * A campaign whose last victory was never recorded is still completed: the last mission's ending cinematic or the
 * next campaign it opened tells, in the version of the maps whose last mission was reached.
 */
class CampaignCompletedTest {
	@TempDir
	File temp;

	private static String section(final String name, final int defaultOpen, final String version,
			final int missions) {
		final StringBuilder text = new StringBuilder("[" + name + "]\r\nHeader=\"H\"\r\nName=\"N\"\r\nDefaultOpen="
				+ defaultOpen + "\r\n");
		for (int i = 0; i < missions; i++) {
			text.append("Mission").append(i).append("=\"C\",\"M\",\"Campaign/").append(version).append("/ROC/")
					.append(name).append(i).append(".w3x\"\r\n");
		}
		return text.toString();
	}

	private CampaignCatalog catalog() throws IOException {
		final String index = "[Index]\r\nCampaignList=\"\",\"Tutorial\",\"Human\",\"Undead\",\"Orc\"\r\n";
		write("UI/CampaignInfoClassic.txt", index + section("Tutorial", 1, "Classic", 2)
				+ section("Human", 1, "Classic", 3) + section("Undead", 0, "Classic", 3) + section("Orc", 0, "Classic", 2));
		write("UI/CampaignInfoReforged.txt", index + section("Tutorial", 1, "Reforged", 5)
				+ section("Human", 0, "Reforged", 3) + section("Undead", 0, "Reforged", 3)
				+ section("Orc", 0, "Reforged", 2));
		return CampaignCatalog.load(new FolderDataSource(this.temp.toPath()), "UI\\CampaignInfo.txt");
	}

	private void write(final String path, final String text) throws IOException {
		final File file = new File(this.temp, path);
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private static long key(final int campaign, final int mission) {
		return (((long) campaign) << 32) | (mission & 0xFFFFFFFFL);
	}

	/** A version 3 profile (no victories recorded): missions made available, campaigns opened, ending cinematics. */
	private static CCampaignProgress.Snapshot progress(final long[] missions, final int[] campaigns,
			final int[] endings) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(3);
		out.writeInt(1);
		out.writeUTF(CCampaignProgress.CampaignList.COMBINED.name());
		out.writeInt(missions.length);
		for (final long mission : missions) {
			out.writeLong(mission);
		}
		for (final int[] flags : new int[][] { campaigns, {}, endings }) {
			out.writeInt(flags.length);
			for (final int flag : flags) {
				out.writeInt(flag);
			}
		}
		out.writeBoolean(false);
		out.writeInt(-1);
		out.writeInt(-1);
		out.flush();
		return CCampaignProgress.readSnapshot(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
				CCampaignProgress.CampaignList.COMBINED);
	}

	@Test
	void classicPrologueIsCompletedByTheHumanCampaignItsLastMissionOpened() throws IOException {
		final CampaignCatalog catalog = catalog();
		final CampaignCatalog.Entry prologue = catalog.getEntry(0);
		// Departures, the last classic chapter, makes the human campaign available.
		final CCampaignProgress.Snapshot progress = progress(new long[] { key(0, 1), key(1, 0) }, new int[] { 1 },
				new int[0]);
		assertTrue(CampaignCatalog.isMissionWon(prologue, 1, progress, false));
		assertTrue(CampaignCatalog.isCompleted(prologue, progress, false));
		// The Reforged prologue goes on to chapter five, which this profile never reached.
		assertFalse(CampaignCatalog.isCompleted(prologue, progress, true));
		assertFalse(CampaignCatalog.isCompleted(catalog.getEntry(1), progress, false));
	}

	@Test
	void reforgedPrologueIsCompletedByItsFifthChapterOpeningTheHumanCampaign() throws IOException {
		final CampaignCatalog.Entry prologue = catalog().getEntry(0);
		final CCampaignProgress.Snapshot reached = progress(
				new long[] { key(0, 1), key(0, 2), key(0, 3), key(0, 4) }, new int[0], new int[0]);
		assertFalse(CampaignCatalog.isCompleted(prologue, reached, true));
		final CCampaignProgress.Snapshot won = progress(new long[] { key(0, 1), key(0, 2), key(0, 3), key(0, 4) },
				new int[] { 1 }, new int[0]);
		assertTrue(CampaignCatalog.isCompleted(prologue, won, true));
		assertTrue(CampaignCatalog.isCompleted(prologue, won, false));
	}

	@Test
	void humanAndUndeadAreCompletedByTheirEndingCinematicOrTheNextCampaign() throws IOException {
		final CampaignCatalog catalog = catalog();
		final CampaignCatalog.Entry human = catalog.getEntry(1);
		final CampaignCatalog.Entry undead = catalog.getEntry(2);
		final long[] humanReached = { key(1, 1), key(1, 2) };
		assertFalse(CampaignCatalog.isCompleted(human, progress(humanReached, new int[0], new int[0]), true));
		assertTrue(CampaignCatalog.isCompleted(human, progress(humanReached, new int[0], new int[] { 1 }), true));
		assertTrue(CampaignCatalog.isCompleted(human, progress(humanReached, new int[] { 2 }, new int[0]), false));
		final long[] undeadReached = { key(2, 1), key(2, 2) };
		assertFalse(CampaignCatalog.isCompleted(undead, progress(undeadReached, new int[] { 2 }, new int[0]), false));
		assertTrue(CampaignCatalog.isCompleted(undead, progress(undeadReached, new int[] { 2, 3 }, new int[0]),
				true));
	}

	@Test
	void campaignsOpenedByHandAreNotCompleted() throws IOException {
		final CampaignCatalog catalog = catalog();
		final CCampaignProgress.Snapshot unlocked = progress(new long[0], new int[0], new int[0])
				.unlocking(Collections.singleton(2));
		assertFalse(CampaignCatalog.isCompleted(catalog.getEntry(1), unlocked, false));
		assertFalse(CampaignCatalog.isCompleted(catalog.getEntry(0), unlocked, false));
	}
}
