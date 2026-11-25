package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/** The reasons the launcher gives for a map the engine cannot play. */
class MapIssueTest {
	@TempDir
	File temp;

	@Test
	void encryptedArchivesAreToldByTheirExtension() {
		assertEquals(MapIssue.PROTECTED, MapIssue.ofName("Campaign\\ForsakenKingdom\\HumanRE01.w3xd"));
		assertEquals(MapIssue.NONE, MapIssue.ofName("Maps\\Custom\\a.w3x"));
		assertEquals(MapIssue.NONE, MapIssue.ofName(null));
	}

	@Test
	void aCampaignMapIsMissingWhenTheDataLacksIt() throws Exception {
		Files.createDirectories(new File(this.temp, "Campaign").toPath());
		Files.write(new File(this.temp, "Campaign/a.w3x").toPath(), new byte[] { 1 });
		Files.write(new File(this.temp, "Campaign/b.w3xd").toPath(), new byte[] { 1 });
		final FolderDataSource data = new FolderDataSource(this.temp.toPath());
		assertEquals(MapIssue.NONE, MapIssue.ofData(data, "Campaign\\a.w3x"));
		assertEquals(MapIssue.PROTECTED, MapIssue.ofData(data, "Campaign\\b.w3xd"));
		assertEquals(MapIssue.MISSING, MapIssue.ofData(data, "Campaign\\c.w3x"));
		assertEquals(MapIssue.MISSING, MapIssue.ofData(data, ""));
		assertEquals(MapIssue.NONE, MapIssue.ofData(data, "Doodads/Cinematic/X/X.mdl"));
		assertEquals(MapIssue.NONE, MapIssue.ofData(data, "Doodads\\Cinematic\\X\\X.MDX"));
	}

	private File mapFolder(final String name, final String... scripts) throws Exception {
		final File folder = new File(this.temp, "maps/" + name);
		Files.createDirectories(folder.toPath());
		for (final String script : scripts) {
			final File file = new File(folder, script);
			Files.createDirectories(file.getParentFile().toPath());
			Files.write(file.toPath(), new byte[] { 1 });
		}
		return folder;
	}

	@Test
	void aMapAboutToStartIsToldByItsScript() throws Exception {
		final FolderDataSource data = new FolderDataSource(Files.createDirectories(new File(this.temp, "data").toPath()));
		final String jass = mapFolder("Jass.w3x", "war3map.j").getAbsolutePath();
		final String scripted = mapFolder("Folder.w3x", "Scripts/war3map.j").getAbsolutePath();
		final String lua = mapFolder("Lua.w3x", "war3map.lua").getAbsolutePath();
		final String bare = mapFolder("Bare.w3x", "war3map.w3i").getAbsolutePath();
		assertEquals(MapIssue.NONE, MapIssue.ofFile(data, jass));
		assertEquals(MapIssue.NONE, MapIssue.ofFile(data, scripted));
		assertEquals(MapIssue.NONE, MapIssue.ofFile(data, lua));
		assertEquals(MapIssue.NO_SCRIPT, MapIssue.ofFile(data, bare));
	}

	@Test
	void aLuaMapIsRefusedWhereTheLuaLibraryIsOff() throws Exception {
		final FolderDataSource data = new FolderDataSource(Files.createDirectories(new File(this.temp, "data").toPath()));
		final String lua = mapFolder("Lua.w3x", "war3map.lua").getAbsolutePath();
		System.setProperty("warsmash.lua.disabled", "true");
		try {
			assertEquals(MapIssue.LUA, MapIssue.ofFile(data, lua));
			assertEquals(MapIssue.LUA, MapIssue.ofRecordedMap(data, lua));
		}
		finally {
			System.clearProperty("warsmash.lua.disabled");
		}
	}

	@Test
	void aMapAboutToStartIsToldByItsNameBeforeItIsOpened() throws Exception {
		final FolderDataSource data = new FolderDataSource(Files.createDirectories(new File(this.temp, "data").toPath()));
		final File sealed = new File(this.temp, "Sealed.w3xd");
		Files.write(sealed.toPath(), new byte[] { 1, 2, 3 });
		assertEquals(MapIssue.PROTECTED, MapIssue.ofFile(data, sealed.getAbsolutePath()));
	}

	@Test
	void aMapThatCannotBeOpenedIsLeftToTheEngine() throws Exception {
		final FolderDataSource data = new FolderDataSource(Files.createDirectories(new File(this.temp, "data").toPath()));
		assertEquals(MapIssue.NONE, MapIssue.ofFile(data, new File(this.temp, "Gone.w3x").getAbsolutePath()));
		assertEquals(MapIssue.NONE, MapIssue.ofFile(data, null));
		assertEquals(MapIssue.NONE, MapIssue.ofFile(data, ""));
		assertEquals(MapIssue.NONE, MapIssue.ofFile(data, "Doodads/Cinematic/X/X.mdl"));
	}

	@Test
	void aMissionEntryThatNamesAModelIsNotAMap() {
		assertTrue(CampaignMission.isModelEntry("Doodads/Cinematic/X/X.mdl"));
		assertTrue(CampaignMission.isModelEntry("Doodads\\Cinematic\\X\\X.MDX"));
		assertFalse(CampaignMission.isModelEntry("Campaign/Reforged/TFT/UndeadX08.w3x"));
		assertFalse(CampaignMission.isModelEntry(null));
	}

	@Test
	void theKeptListKeepsTheIssueOfAMap() {
		final File list = new File(this.temp, "maps.txt");
		MapListCache.write(list, "header", new MapListCache.Listing(
				Arrays.asList(new GameCatalog.MapEntry("Maps\\a.w3x", 2, "Lua", false, MapIssue.LUA),
						new GameCatalog.MapEntry("Maps\\b.w3x", 2, "Plain", true)),
				Collections.emptyList()));
		final MapListCache.Listing read = MapListCache.read(list, "header");
		assertEquals(MapIssue.LUA, read.maps.get(0).issue);
		assertEquals(MapIssue.NONE, read.maps.get(1).issue);
	}

	@Test
	void everyIssueHasItsTextsInTheLauncherStrings() throws Exception {
		final File resources = new File("../android/res/values/strings.xml");
		final LauncherStrings strings;
		try (FileInputStream in = new FileInputStream(resources)) {
			strings = new LauncherStrings(in);
		}
		for (final MapIssue issue : MapIssue.values()) {
			if (issue == MapIssue.NONE) {
				continue;
			}
			assertNotEquals(issue.message, strings.get(issue.message));
			assertNotEquals(issue.tag, strings.get(issue.tag));
			assertEquals(issue.english, strings.get(issue.message));
		}
		assertEquals("This map uses a Lua script, and the Lua library is not available on this system.", strings.get(MapIssue.LUA.message));
		assertEquals("This map is protected and cannot be opened.", strings.get(MapIssue.PROTECTED.message));
	}

	@Test
	void aMapNamedByAReplayOrASaveIsMissingWhenItIsNowhere() throws Exception {
		final FolderDataSource data = new FolderDataSource(Files.createDirectories(new File(this.temp, "data").toPath()));
		Files.createDirectories(new File(this.temp, "data/Maps").toPath());
		Files.write(new File(this.temp, "data/Maps/Kept.w3x").toPath(), new byte[] { 1 });
		final String lua = mapFolder("Lua.w3x", "war3map.lua").getAbsolutePath();
		final String jass = mapFolder("Jass.w3x", "war3map.j").getAbsolutePath();
		assertEquals(MapIssue.MISSING, MapIssue.ofRecordedMap(data, "Maps\\Gone.w3x"));
		assertEquals(MapIssue.MISSING, MapIssue.ofRecordedMap(data, new File(this.temp, "Gone.w3x").getAbsolutePath()));
		assertEquals(MapIssue.NONE, MapIssue.ofRecordedMap(data, lua));
		assertEquals(MapIssue.NONE, MapIssue.ofRecordedMap(data, jass));
		assertEquals(MapIssue.PROTECTED, MapIssue.ofRecordedMap(data, "Campaign\\ForsakenKingdom\\HumanRE01.w3xd"));
		assertEquals(MapIssue.NONE, MapIssue.ofRecordedMap(data, null));
		assertEquals(MapIssue.NONE, MapIssue.ofRecordedMap(data, "Doodads/Cinematic/X/X.mdl"));
	}

	@Test
	void aRefusedStartKeepsItsIssueThroughTheCauseChain() {
		for (final MapIssue issue : MapIssue.values()) {
			if (issue == MapIssue.NONE) {
				continue;
			}
			final MapIssueException refused = new MapIssueException(issue, "Maps\\a.w3x");
			assertEquals(issue, MapIssue.ofFailure(refused));
			assertEquals(issue, MapIssue.ofFailure(new RuntimeException("wrapped", refused)));
			assertEquals(issue.english + " (Maps\\a.w3x)", GameResult.describeFailure(refused));
		}
		assertEquals(MapIssue.NONE, MapIssue.ofFailure(new java.io.IOException("other")));
		assertEquals(MapIssue.NONE, MapIssue.ofFailure(null));
	}

	@Test
	void theResultOfARefusedStartCarriesTheIssueToTheLauncher() {
		final GameResult failed = new GameResult();
		failed.result = "error";
		failed.map = "Maps\\a.w3x";
		failed.issue = MapIssue.LUA;
		assertEquals(MapIssue.LUA, GameResult.fromJson(failed.toJson()).issue);
		failed.issue = MapIssue.NONE;
		assertEquals(MapIssue.NONE, GameResult.fromJson(failed.toJson()).issue);
		assertFalse(failed.toJson().contains("\"issue\""));
		assertEquals(MapIssue.NONE, GameResult.fromJson("{\"result\":\"error\",\"issue\":\"Gone\"}").issue);
	}
}
