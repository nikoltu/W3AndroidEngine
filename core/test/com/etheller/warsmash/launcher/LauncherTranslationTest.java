package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** The launcher's translation, and the rule that takes its words over the ones of a language pack left in English. */
class LauncherTranslationTest {
	private static final Pattern PLACE = Pattern.compile("%(?:\\d\\$)?[sd]|%%");
	private static final String RESOURCES = "../android/res/";

	private static LauncherStrings english() throws IOException {
		try (FileInputStream in = new FileInputStream(new File(RESOURCES + "values/strings.xml"))) {
			return new LauncherStrings(in);
		}
	}

	private static LauncherStrings spanish() throws IOException {
		final LauncherStrings strings = english();
		try (FileInputStream in = new FileInputStream(new File(RESOURCES + "values-es/strings.xml"))) {
			strings.overlay(in);
		}
		return strings;
	}

	private static String places(final String text) {
		final Matcher matcher = PLACE.matcher(text);
		final StringBuilder found = new StringBuilder();
		while (matcher.find()) {
			found.append(matcher.group()).append(' ');
		}
		return found.toString();
	}

	@Test
	void everyKeyTheLauncherFallsBackOnHasItsOwnTextInBothLanguages() throws IOException {
		final LauncherStrings english = english();
		final LauncherStrings spanish = spanish();
		final List<String> keys = new ArrayList<>(GlueText.ownKeys());
		for (final String[] row : CustomGame.SLOT_TYPES) {
			keys.add(row[1]);
		}
		for (final String[] row : CustomGame.RACES) {
			keys.add(row[0]);
		}
		for (final String[] row : CustomGame.COLOR_NAMES) {
			keys.add(row[0]);
		}
		for (final String[] row : LauncherKeys.DIFFICULTIES) {
			keys.add(row[1]);
		}
		for (final String[] row : LauncherKeys.CAMPAIGN_MAPS) {
			keys.add(row[1]);
		}
		for (final String[] row : CampaignResult.ROWS) {
			keys.add(row[1]);
			keys.add(row[2]);
		}
		keys.addAll(Arrays.asList("VICTORY", "DEFEAT", "CONTINUE", "RESTART", "EASY", "NORMAL", "HARD",
				"KEY_CAMPAIGN", "KEY_LOAD_SAVED_GAME", "KEY_VIEW_REPLAY", "KEY_CUSTOM_GAME", "KEY_LOCAL_AREA_NETWORK",
				"KEY_OPTIONS", "KEY_HELP", "KEY_QUIT"));
		for (final String key : keys) {
			final String name = "glue_" + key.toLowerCase(Locale.ROOT);
			assertNotEquals(name, english.get(name), name + " has no English text");
			assertNotEquals(name, spanish.get(name), name + " has no Spanish text");
			assertEquals(places(english.get(name)), places(spanish.get(name)), name);
		}
	}

	@Test
	void theSpanishTextsKeepThePlacesOfTheEnglishOnes() throws IOException {
		final String xml = new String(Files.readAllBytes(new File(RESOURCES + "values-es/strings.xml").toPath()),
				StandardCharsets.UTF_8);
		final LauncherStrings english = english();
		final LauncherStrings spanish = spanish();
		final Matcher names = Pattern.compile("<string name=\"([^\"]+)\"").matcher(xml);
		int translated = 0;
		while (names.find()) {
			final String name = names.group(1);
			assertNotEquals(name, english.get(name), "the Spanish text of " + name + " has no English one");
			assertEquals(places(english.get(name)), places(spanish.get(name)), name);
			translated++;
		}
		assertTrue(translated > 600, "the Spanish texts: " + translated);
	}

	private static Map<String, String> map(final String... pairs) {
		final Map<String, String> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put(pairs[i], pairs[i + 1]);
		}
		return map;
	}

	@Test
	void aLanguageLeftInEnglishGivesWayToTheLaunchersOwnWords() {
		final Map<String, String> language = map("BACK", "Back", "CONTINUE", "|CffffffffC|Rontinue", "SCORE", "Score",
				"KEY_CAMPAIGN", "|CffffffffC|Rampaña");
		final Map<String, String> english = map("BACK", "Back", "CONTINUE", "|CffffffffC|Rontinue", "SCORE", "Score",
				"KEY_CAMPAIGN", "|CffffffffC|Rampaign");
		GlueText.dropUntranslated(language, english);
		assertFalse(language.containsKey("BACK"));
		assertFalse(language.containsKey("CONTINUE"));
		assertFalse(language.containsKey("SCORE"));
		assertEquals("|CffffffffC|Rampaña", language.get("KEY_CAMPAIGN"));
	}

	@Test
	void campaignMissionAndMapTextsAreNeverTakenOver() {
		final Map<String, String> language = map("CAMP_MAP_PROLOGUE01", "Chasing Visions", "CAMP_TUTORIAL_NAME",
				"Exodus of the Horde", "CAMP_CHAPTER_1", "Chapter One", "MAPNAME_X", "Same", "SOME_OTHER_KEY", "Same");
		final Map<String, String> english = new HashMap<>(language);
		GlueText.dropUntranslated(language, english);
		assertEquals(english, language);
		for (final String key : GlueText.ownKeys()) {
			assertFalse(key.startsWith("CAMP_"), key);
			assertFalse(key.startsWith("MAPNAME"), key);
		}
	}

	@Test
	void aTextTheLanguageHasTranslatedStays() {
		final Map<String, String> language = map("BACK", "Atrás", "CANCEL", "Cancelar");
		final Map<String, String> english = map("BACK", "Back", "CANCEL", "Cancel");
		GlueText.dropUntranslated(language, english);
		assertEquals("Atrás", language.get("BACK"));
		assertEquals("Cancelar", language.get("CANCEL"));
	}
}
