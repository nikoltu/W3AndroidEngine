package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.launcher.CampaignChoice;
import com.etheller.warsmash.launcher.StorageRoot;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;

/**
 * The order the campaigns are played in does not matter: what the scripts of Reign of Chaos and The Frozen Throne
 * missions make available lands in the profile the same way, and the campaign a menu opens on is the one played last.
 */
class CampaignOrderTest {
	@TempDir
	File temp;

	private static final String UNIFIED = "[Index]\r\nCampaignList=\"Tutorial\",\"Human\",\"NightElf\",\"NightElfEx\",\"HumanEx\"\r\n"
			+ campaign("Tutorial", 1, 2) + campaign("Human", 1, 3) + campaign("NightElf", 0, 2)
			+ campaign("NightElfEx", 1, 3) + campaign("HumanEx", 0, 2);
	private static final String ROC = "[Index]\r\nCampaignList=\"Tutorial\",\"Human\",\"NightElf\"\r\n"
			+ campaign("Tutorial", 1, 2) + campaign("Human", 1, 3) + campaign("NightElf", 0, 2);
	private static final String TFT = "[Index]\r\nCampaignList=\"\",\"NightElf\",\"Human\"\r\n"
			+ campaign("NightElf", 1, 3) + campaign("Human", 0, 2);

	private static String campaign(final String name, final int open, final int missions) {
		final StringBuilder text = new StringBuilder(
				"[" + name + "]\r\nHeader=\"" + name + "\"\r\nName=\"" + name + "\"\r\nDefaultOpen=" + open + "\r\n");
		for (int i = 0; i < missions; i++) {
			text.append("Mission").append(i).append("=\"C\",\"M\",\"Campaign/").append(name).append(i)
					.append(".w3x\"\r\n");
		}
		return text.toString();
	}

	@BeforeEach
	void profile() {
		Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(CampaignOrderTest.class.getClassLoader(),
				new Class[] { com.badlogic.gdx.Files.class }, (proxy, method, arguments) -> {
					if ("absolute".equals(method.getName()) || "local".equals(method.getName())) {
						return new FileHandle((String) arguments[0]);
					}
					throw new UnsupportedOperationException(method.getName());
				});
		StorageRoot.set(new File(this.temp, "root"));
		CCampaignProgress.forgetLoaded();
	}

	@AfterEach
	void restore() {
		CCampaignProgress.forgetLoaded();
		CCampaignProgress.setCampaignList(CCampaignProgress.CampaignList.COMBINED);
		StorageRoot.set(null);
		Gdx.files = null;
	}

	/** @param file the data's file of the list: none of the campaign files makes the combined list of later versions */
	private CampaignCatalog catalog(final String name, final String file, final String list) throws IOException {
		final File listFile = new File(this.temp, name + "/UI/" + (file == null ? "CampaignInfoClassic.txt" : file));
		listFile.getParentFile().mkdirs();
		Files.write(listFile.toPath(), list.getBytes(StandardCharsets.UTF_8));
		return CampaignCatalog.load(new FolderDataSource(new File(this.temp, name).toPath()),
				"UI\\" + (file == null ? "CampaignInfo.txt" : file));
	}

	private File progressFile() {
		return new File(this.temp, "root/Save/Player/Campaigns.w3v");
	}

	/** What the launcher shows: the profile's file as it was written, read for the list the catalog has. */
	private CCampaignProgress.Snapshot launcherView(final CampaignCatalog catalog) throws IOException {
		try (DataInputStream in = new DataInputStream(new FileInputStream(progressFile()))) {
			return CCampaignProgress.readSnapshot(in, catalog.getList());
		}
	}

	/** A campaign's missions won in order, as its mission scripts make the next ones and the next campaign available. */
	private static void play(final CampaignCatalog catalog, final String section, final boolean race) {
		CCampaignProgress.setCampaignList(catalog.getList());
		final CampaignCatalog.Entry entry = byName(catalog, section);
		if (race) {
			CCampaignProgress.setClassicMenuCampaignIndex(entry.getNumber());
		}
		else {
			CCampaignProgress.setMenuCampaignIndex(entry.getNumber());
		}
		final int count = entry.getData().getMissions().size();
		for (int mission = 1; mission < count; mission++) {
			CCampaignProgress.setMissionAvailable(entry.getNumber(), mission, true);
		}
		if (entry.getNumber() + 1 < catalog.getEntries().size()) {
			CCampaignProgress.setCampaignAvailable(entry.getNumber() + 1, true);
		}
	}

	private static CampaignCatalog.Entry byName(final CampaignCatalog catalog, final String section) {
		for (final CampaignCatalog.Entry entry : catalog.getEntries()) {
			if (entry.getSection().equals(section)) {
				return entry;
			}
		}
		throw new AssertionError(section);
	}

	private static void assertPlayed(final CampaignCatalog catalog, final CCampaignProgress.Snapshot view,
			final String section) {
		final CampaignCatalog.Entry entry = byName(catalog, section);
		for (int mission = 0; mission < entry.getData().getMissions().size(); mission++) {
			assertTrue(CampaignCatalog.isMissionOpen(entry, mission, view), section + " mission " + mission);
		}
	}

	private static String playedSection(final CampaignCatalog catalog, final CCampaignProgress.Snapshot view) {
		return CampaignChoice.playedCampaign(catalog, view).getSection();
	}

	@Test
	void combinedListFollowsTheCampaignPlayedLastInEitherOrder() throws IOException {
		final CampaignCatalog catalog = catalog("unified", null, UNIFIED);
		play(catalog, "NightElfEx", false);
		assertEquals("NightElfEx", playedSection(catalog, launcherView(catalog)));
		play(catalog, "Human", true);
		CCampaignProgress.Snapshot view = launcherView(catalog);
		assertEquals("Human", playedSection(catalog, view));
		assertPlayed(catalog, view, "NightElfEx");
		assertPlayed(catalog, view, "Human");
		assertTrue(CampaignCatalog.isOpen(byName(catalog, "HumanEx"), view));
		play(catalog, "NightElfEx", false);
		assertEquals("NightElfEx", playedSection(catalog, launcherView(catalog)));

		CCampaignProgress.forgetLoaded();
		assertTrue(progressFile().delete());
		play(catalog, "Human", true);
		play(catalog, "NightElfEx", false);
		view = launcherView(catalog);
		assertEquals("NightElfEx", playedSection(catalog, view));
		assertPlayed(catalog, view, "Human");
		assertPlayed(catalog, view, "NightElfEx");
	}

	@Test
	void separateListsKeepTheirOwnProgressInEitherOrder() throws IOException {
		final CampaignCatalog roc = catalog("roc", "CampaignStrings.txt", ROC);
		final CampaignCatalog tft = catalog("tft", "CampaignStrings_exp.txt", TFT);
		play(tft, "NightElf", false);
		play(roc, "Human", true);
		assertPlayed(roc, launcherView(roc), "Human");
		assertPlayed(tft, launcherView(tft), "NightElf");
		assertTrue(CampaignCatalog.isOpen(byName(tft, "Human"), launcherView(tft)));
		assertEquals("Human", playedSection(roc, launcherView(roc)));
		assertEquals("NightElf", playedSection(tft, launcherView(tft)));

		CCampaignProgress.forgetLoaded();
		assertTrue(progressFile().delete());
		play(roc, "Human", true);
		play(tft, "NightElf", false);
		assertPlayed(roc, launcherView(roc), "Human");
		assertPlayed(tft, launcherView(tft), "NightElf");
	}

	/** A file written before the order was kept: its progress stays, and the menu keeps the choice it always had. */
	@Test
	void fileFromBeforeKeepsItsProgress() throws IOException {
		final CampaignCatalog catalog = catalog("unified", null, UNIFIED);
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(6);
		out.writeInt(1);
		out.writeUTF(CCampaignProgress.CampaignList.COMBINED.name());
		out.writeInt(2);
		out.writeLong((3L << 32) | 1);
		out.writeLong((3L << 32) | 2);
		out.writeInt(1);
		out.writeInt(4);
		out.writeInt(0);
		out.writeInt(0);
		out.writeBoolean(true);
		out.writeInt(3);
		out.writeInt(1);
		out.writeInt(0);
		out.writeInt(-1);
		out.writeInt(0);
		out.writeInt(0);
		out.flush();
		progressFile().getParentFile().mkdirs();
		Files.write(progressFile().toPath(), bytes.toByteArray());

		final CCampaignProgress.Snapshot old = CCampaignProgress.readSnapshot(
				new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), catalog.getList());
		assertEquals(3, old.getCombinedMenuCampaignIndex());

		play(catalog, "Human", true);
		final CCampaignProgress.Snapshot view = launcherView(catalog);
		assertPlayed(catalog, view, "NightElfEx");
		assertPlayed(catalog, view, "Human");
		assertTrue(CampaignCatalog.isOpen(byName(catalog, "HumanEx"), view));
		assertEquals("Human", playedSection(catalog, view));
	}

	/** The version 6 reader of the builds before the order was kept: its progress is intact on a file that has it. */
	private static long[] legacyRead(final byte[] bytes) throws IOException {
		final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
		assertEquals(6, in.readInt());
		final int lists = in.readInt();
		long missions = 0;
		long campaigns = 0;
		int menu = -2;
		int classic = -2;
		for (int i = 0; i < lists; i++) {
			in.readUTF();
			final int missionCount = in.readInt();
			for (int j = 0; j < missionCount; j++) {
				missions += in.readLong();
			}
			for (int flags = 0; flags < 3; flags++) {
				final int count = in.readInt();
				for (int j = 0; j < count; j++) {
					campaigns += in.readInt();
				}
			}
			in.readBoolean();
			menu = in.readInt();
			classic = in.readInt();
			final int won = in.readInt();
			for (int j = 0; j < won; j++) {
				in.readLong();
			}
			in.readInt();
			final int buttons = in.readInt();
			for (int j = 0; j < buttons; j++) {
				in.readInt();
			}
			final int difficulties = in.readInt();
			for (int j = 0; j < difficulties; j++) {
				in.readLong();
				in.readInt();
			}
		}
		return new long[] { lists, missions, campaigns, menu, classic };
	}

	@Test
	void versionSixReaderLosesNothingOfTheNewFile() throws IOException {
		final CampaignCatalog catalog = catalog("unified", null, UNIFIED);
		play(catalog, "NightElfEx", false);
		play(catalog, "Human", true);
		final byte[] fresh = Files.readAllBytes(progressFile().toPath());
		final long[] read = legacyRead(fresh);
		assertEquals(1, read[0]);
		assertEquals(3, read[3]);
		assertEquals(1, read[4]);
		assertTrue(read[1] > 0 && read[2] > 0);

		// A build that drops the order writes the same progress; this one then falls back to the old choice.
		final ByteArrayOutputStream dropped = new ByteArrayOutputStream();
		dropped.write(fresh, 0, fresh.length - (4 + 4 + 2 + "COMBINED".length() + 4));
		final byte[] old = dropped.toByteArray();
		assertEquals(read[1], legacyRead(old)[1]);
		final CCampaignProgress.Snapshot view = CCampaignProgress.readSnapshot(
				new DataInputStream(new ByteArrayInputStream(old)), catalog.getList());
		assertPlayed(catalog, view, "NightElfEx");
		assertPlayed(catalog, view, "Human");
		assertEquals(3, view.getCombinedMenuCampaignIndex());
	}
}
