package com.etheller.warsmash.parsers.w3x;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.datasources.GameLanguage;
import com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapObjectData;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.custom.WTS;

class War3MapLocaleTest {
	@TempDir
	File temp;

	@AfterEach
	void english() {
		GameLanguage.useInGame(new DataTable(null));
	}

	private File map(final boolean withLanguageFolders) throws Exception {
		final File map = new File(this.temp, "map.w3x");
		Files.createDirectories(map.toPath());
		write(new File(map, "war3map.wts"), "STRING 1\n{\nObjective\n}\n\nSTRING 2\n{\nOnly in the map\n}\n");
		write(new File(map, "war3map.j"), "// script");
		if (withLanguageFolders) {
			final File german = new File(map, "_Locales/deDE.w3mod");
			Files.createDirectories(german.toPath());
			write(new File(german, "war3map.wts"), "STRING 1\n{\nZiel\n}\n");
			final File spanish = new File(map, "_Locales/esES.w3mod");
			Files.createDirectories(spanish.toPath());
			write(new File(spanish, "war3map.wts"), "STRING 1\n{\nObjetivo\n}\n");
		}
		return map;
	}

	private static void write(final File file, final String text) throws Exception {
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private WTS wts(final String language, final boolean withLanguageFolders) throws Exception {
		final DataTable ini = new DataTable(null);
		GameLanguage.apply(ini, language);
		GameLanguage.useInGame(ini);
		final File game = new File(this.temp, "game");
		Files.createDirectories(game.toPath());
		final War3Map map = new War3Map(new FolderDataSource(game.toPath()), map(withLanguageFolders));
		return Warcraft3MapObjectData.loadWTS(map);
	}

	@Test
	void theLanguageFolderOfTheMapWinsOverItsRootFile() throws Exception {
		assertEquals("Ziel", wts("deDE", true).get(1));
		assertEquals("Objetivo", wts("esES", true).get(1));
	}

	@Test
	void anEnglishGameReadsTheRootFileAndNothingChanges() throws Exception {
		assertEquals("Objective", wts("enUS", true).get(1));
		assertEquals("Objective", wts("", true).get(1));
	}

	@Test
	void aMapWithoutTheFolderOfTheLanguageIsReadAsBefore() throws Exception {
		assertEquals("Objective", wts("deDE", false).get(1));
		assertEquals("Objective", wts("frFR", true).get(1));
	}

	@Test
	void aFileTheFolderLacksStillComesFromTheMapRoot() throws Exception {
		final DataTable ini = new DataTable(null);
		GameLanguage.apply(ini, "deDE");
		GameLanguage.useInGame(ini);
		final File game = new File(this.temp, "game");
		Files.createDirectories(game.toPath());
		final War3Map map = new War3Map(new FolderDataSource(game.toPath()), map(true));
		assertEquals("// script", new String(map.read("war3map.j").array(), StandardCharsets.UTF_8));
		assertEquals("Ziel", Warcraft3MapObjectData.loadWTS(map).get(1));
	}

	@Test
	void theLanguageFolderIsFoundInAnySpellingOfThePath() throws Exception {
		final DataSource contents = new FolderDataSource(map(true).toPath());
		final DataSource localized = War3Map.localized(contents, "deDE");
		assertTrue(new String(localized.read("war3map.wts").array(), StandardCharsets.UTF_8).contains("Ziel"));
		assertSame(contents, War3Map.localized(contents, "enUS"));
		assertNull(GameLanguage.mapFolder("enUS"));
		assertEquals("_Locales/deDE.w3mod/", GameLanguage.mapFolder("dede"));
	}
}
