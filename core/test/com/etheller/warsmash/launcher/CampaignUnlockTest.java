package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CampaignUnlock;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;

/** Campaigns opened by hand show every mission, and taking them back leaves the profile's own progress as it was. */
class CampaignUnlockTest {
	@TempDir
	File temp;

	private static final String LIST = "[Index]\r\nCampaignList=\"Tutorial\",\"Human\"\r\n"
			+ "[Tutorial]\r\nHeader=\"Prologue\"\r\nName=\"Exodus of the Horde\"\r\nDefaultOpen=1\r\n"
			+ "Mission0=\"Chapter One\",\"Chasing Visions\",\"Campaign/Prologue01.w3x\"\r\n"
			+ "Mission1=\"Chapter Two\",\"Departures\",\"Campaign/Prologue02.w3x\"\r\n"
			+ "[Human]\r\nHeader=\"Human\"\r\nName=\"Alliance\"\r\nDefaultOpen=0\r\n"
			+ "Mission0=\"Chapter One\",\"Defense\",\"Campaign/Human01.w3x\"\r\n"
			+ "Mission1=\"Chapter Two\",\"Culling\",\"Campaign/Human02.w3x\"\r\n"
			+ "Mission2=\"Chapter Three\",\"March\",\"Campaign/Human03.w3x\"\r\n";

	private CampaignCatalog catalog() throws IOException {
		final File file = new File(this.temp, "UI/CampaignInfoClassic.txt");
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), LIST.getBytes(StandardCharsets.UTF_8));
		return CampaignCatalog.load(new FolderDataSource(this.temp.toPath()), "UI\\CampaignInfo.txt");
	}

	private static long key(final int campaign, final int mission) {
		return (((long) campaign) << 32) | (mission & 0xFFFFFFFFL);
	}

	/** A profile whose Human campaign never opened: only mission 1 of the prologue is available. */
	private static byte[] stuckProgress() throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(4);
		out.writeInt(1);
		out.writeUTF(CCampaignProgress.CampaignList.COMBINED.name());
		out.writeInt(1);
		out.writeLong(key(0, 1));
		for (int flags = 0; flags < 3; flags++) {
			out.writeInt(0);
		}
		out.writeBoolean(false);
		out.writeInt(-1);
		out.writeInt(-1);
		out.writeInt(1);
		out.writeLong(key(0, 0));
		out.flush();
		return bytes.toByteArray();
	}

	private static CCampaignProgress.Snapshot snapshot(final byte[] bytes) throws IOException {
		return CCampaignProgress.readSnapshot(new DataInputStream(new ByteArrayInputStream(bytes)),
				CCampaignProgress.CampaignList.COMBINED);
	}

	@Test
	void unlockedCampaignOpensEveryMissionAndOthersStayAsTheyWere() throws IOException {
		final CampaignCatalog catalog = catalog();
		final CampaignCatalog.Entry prologue = catalog.getEntry(0);
		final CampaignCatalog.Entry human = catalog.getEntry(1);
		final CCampaignProgress.Snapshot real = snapshot(stuckProgress());
		assertFalse(CampaignCatalog.isOpen(human, real));
		assertFalse(CampaignCatalog.isMissionOpen(human, 1, real));

		final Set<String> sections = new HashSet<>(Arrays.asList("Human"));
		final CCampaignProgress.Snapshot shown = real.unlocking(catalog.numbersOf(sections));
		assertTrue(CampaignCatalog.isOpen(human, shown));
		for (int mission = 0; mission < 3; mission++) {
			assertTrue(CampaignCatalog.isMissionOpen(human, mission, shown));
		}
		assertTrue(shown.isOpeningCinematicAvailable(human.getNumber()));
		assertTrue(CampaignCatalog.isMissionOpen(prologue, 1, shown));
		assertFalse(shown.isOpeningCinematicAvailable(prologue.getNumber()));
		// What the campaign screens mark as won stays what the profile won.
		assertFalse(CampaignCatalog.isMissionWon(human, 0, shown));
		assertFalse(CampaignCatalog.isCompleted(human, shown));
		assertTrue(CampaignCatalog.isMissionWon(prologue, 0, shown));
		assertFalse(CampaignCatalog.isMissionWon(prologue, 1, shown));
		assertEquals(Collections.singleton(1), catalog.numbersOf(sections));
	}

	@Test
	void takingTheUnlockBackShowsTheRealProgress() throws IOException {
		final CampaignCatalog catalog = catalog();
		final CampaignCatalog.Entry human = catalog.getEntry(1);
		final CCampaignProgress.Snapshot real = snapshot(stuckProgress());
		final CCampaignProgress.Snapshot shown = real.unlocking(Collections.singleton(1));
		assertTrue(CampaignCatalog.isMissionOpen(human, 2, shown));
		final CCampaignProgress.Snapshot back = shown.real();
		assertFalse(CampaignCatalog.isOpen(human, back));
		assertFalse(CampaignCatalog.isMissionOpen(human, 2, back));
		assertTrue(CampaignCatalog.isMissionOpen(catalog.getEntry(0), 1, back));
	}

	@Test
	void unlockFileIsSeparateAndTheProgressFileIsUntouchedByteForByte() throws IOException {
		final byte[] progress = stuckProgress();
		final File progressFile = new File(this.temp, "Campaigns.w3v");
		Files.write(progressFile.toPath(), progress);

		assertTrue(CampaignUnlock.read(this.temp).isEmpty());
		assertTrue(CampaignUnlock.write(this.temp, new HashSet<>(Arrays.asList("Human", "Undead"))));
		assertEquals(new HashSet<>(Arrays.asList("Human", "Undead")), CampaignUnlock.read(this.temp));
		assertArrayEquals(progress, Files.readAllBytes(progressFile.toPath()));

		assertTrue(CampaignUnlock.write(this.temp, new HashSet<>(Arrays.asList("Undead"))));
		assertEquals(Collections.singleton("Undead"), CampaignUnlock.read(this.temp));
		assertTrue(CampaignUnlock.write(this.temp, Collections.emptySet()));
		assertFalse(new File(this.temp, CampaignUnlock.FILE).exists());
		assertTrue(CampaignUnlock.read(this.temp).isEmpty());
		assertArrayEquals(progress, Files.readAllBytes(progressFile.toPath()));
		assertEquals(Arrays.asList("Campaigns.w3v"), Arrays.asList(this.temp.list((dir, name) -> name.startsWith("Campaign"))));
	}

	@Test
	void unreadableUnlockFileMeansNothingUnlocked() throws IOException {
		Files.write(new File(this.temp, CampaignUnlock.FILE).toPath(), new byte[] { 1, 2, 3 });
		assertTrue(CampaignUnlock.read(this.temp).isEmpty());
	}
}
