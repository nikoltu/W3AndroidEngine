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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;

/** A campaign whose last mission opens nothing of its own campaign is still marked won once its victory is recorded. */
class CampaignWonTest {
	@TempDir
	File temp;

	private static final String LIST = "[Index]\r\nCampaignList=\"Tutorial\"\r\n"
			+ "[Tutorial]\r\nHeader=\"Prologue\"\r\nName=\"Exodus of the Horde\"\r\nDefaultOpen=1\r\n"
			+ "Mission0=\"Chapter One\",\"Chasing Visions\",\"Campaign/Prologue01.w3x\"\r\n"
			+ "Mission1=\"Chapter Two\",\"Departures\",\"Campaign/Prologue02.w3x\"\r\n";

	private CampaignCatalog catalog() throws IOException {
		final File file = new File(this.temp, "UI/CampaignInfoClassic.txt");
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), LIST.getBytes(StandardCharsets.UTF_8));
		return CampaignCatalog.load(new FolderDataSource(this.temp.toPath()), "UI\\CampaignInfo.txt");
	}

	private static long key(final int campaign, final int mission) {
		return (((long) campaign) << 32) | (mission & 0xFFFFFFFFL);
	}

	/** One list's progress: mission 1 of campaign 0 open, and with version 4 the given missions won. */
	private static CCampaignProgress.Snapshot progress(final int version, final long... won) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(version);
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
		if (version >= 4) {
			out.writeInt(won.length);
			for (final long mission : won) {
				out.writeLong(mission);
			}
		}
		out.flush();
		return CCampaignProgress.readSnapshot(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
				CCampaignProgress.CampaignList.COMBINED);
	}

	@Test
	void lastMissionWithoutRecordedVictoryIsNotWon() throws IOException {
		final CampaignCatalog.Entry entry = catalog().getEntry(0);
		final CCampaignProgress.Snapshot progress = progress(3);
		assertTrue(CampaignCatalog.isMissionWon(entry, 0, progress));
		assertFalse(CampaignCatalog.isMissionWon(entry, 1, progress));
		assertFalse(CampaignCatalog.isCompleted(entry, progress));
	}

	@Test
	void recordedVictoryOfTheLastMissionCompletesTheCampaign() throws IOException {
		final CampaignCatalog.Entry entry = catalog().getEntry(0);
		final CCampaignProgress.Snapshot progress = progress(4, key(0, 1));
		assertTrue(CampaignCatalog.isMissionWon(entry, 1, progress));
		assertTrue(CampaignCatalog.isCompleted(entry, progress));
	}
}
