package com.etheller.warsmash.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.etheller.warsmash.WarsmashGdxMapScreen;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.GameLanguage;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.units.DataTable;

/** The glue strings of the game data and their colour codes, as the launcher shows them. */
public final class GlueText {
	private static final String CAMPAIGN_PREFIX = "CAMP_";
	/**
	 * The strings the launcher words itself in every language it has a translation of (its glue_ texts). A language
	 * pack made for older game data has the English text under these; the launcher's own translation is used then.
	 * Campaign, mission and map texts are never among them.
	 */
	private static final Set<String> OWN_KEYS = new HashSet<>(Arrays.asList("ADD", "ADVANCED_OPTIONS", "BACK",
			"CAMPAIGN_VERSION_CLASSIC", "CAMPAIGN_VERSION_REFORGED", "CANCEL", "CHANGE_PORTRAIT",
			"COLON_ELAPSED_TIME", "COLON_MAP_SIZE", "COLON_OBSERVERS", "COLON_PLAYERS", "COLON_RACE", "COLON_SAVED",
			"COLON_SUGGESTED_PLAYERS", "COLON_TILESET", "COLOR", "COMPUTER_INSANE", "COMPUTER_NEWBIE",
			"COMPUTER_NORMAL", "CONTINUE", "DEFEAT", "DELETE", "EASY", "HARD", "KEY_ADD", "KEY_CAMPAIGN",
			"KEY_CUSTOM_GAME", "KEY_DELETE", "KEY_HELP", "KEY_LOAD_GAME", "KEY_LOAD_SAVED_GAME",
			"KEY_LOCAL_AREA_NETWORK", "KEY_NEUTRAL", "KEY_OPTIONS", "KEY_QUIT", "KEY_SELECT", "KEY_START_GAME",
			"KEY_VIEW_REPLAY", "KILLED", "LOAD_GAME", "LOCK_TEAMS", "MAP_SIZE", "NEW_PROFILE", "NONE", "NORMAL",
			"OK", "OPEN", "CLOSED", "PLAYER", "PLAYERS", "RACE", "RANDOM", "RANDOM_HERO", "RANDOM_RACES", "RAZED",
			"REPLAY_CONFIRM", "RESTART", "SCORE", "SCORE_TAB_HEROES", "SCORE_TAB_STRUCTURES", "SCORE_TAB_UNITS",
			"SELECT", "SELECT_DIFFICULTY_LEVEL", "SUGGESTED_PLAYERS", "TEAM", "TEAM_FORMAT", "TILESET", "VICTORY",
			"WATCH_REPLAY", "HUMAN", "ORC", "UNDEAD", "NIGHT_ELF", "COLOR_RED", "COLOR_BLUE", "COLOR_TEAL",
			"COLOR_PURPLE", "COLOR_YELLOW", "COLOR_ORANGE", "COLOR_GREEN", "COLOR_PINK", "COLOR_GRAY",
			"COLOR_LIGHT_BLUE", "COLOR_DARK_GREEN", "COLOR_BROWN", "COLOR_MAROON", "COLOR_NAVY", "COLOR_TURQUOISE",
			"COLOR_VIOLET", "COLOR_WHEAT", "COLOR_PEACH", "COLOR_MINT", "COLOR_LAVENDER", "COLOR_COAL", "COLOR_SNOW",
			"COLOR_EMERALD", "COLOR_PEANUT"));
	private static final Pattern STRING_LINE = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s+\"(.*)\"\\s*,?\\s*$");

	/** A piece of text in one colour; colour 0 is the text's own colour. */
	public static final class Run {
		public final String text;
		public final int colour;

		Run(final String text, final int colour) {
			this.text = text;
			this.colour = colour;
		}
	}

	private GlueText() {
	}

	/**
	 * The strings of the glue string lists by upper case key, the first definition winning; null when the game
	 * data has no string list.
	 */
	public static Map<String, String> read(final DataSource data) throws IOException {
		if (!data.has(SkinArt.STRING_FILES[0])) {
			return null;
		}
		final Map<String, String> found = new LinkedHashMap<>();
		for (final String path : SkinArt.STRING_FILES) {
			if (!data.has(path)) {
				continue;
			}
			try (InputStream in = data.getResourceAsStream(path);
					BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					final Matcher matcher = STRING_LINE.matcher(line);
					if (matcher.matches()) {
						found.putIfAbsent(matcher.group(1).toUpperCase(Locale.ROOT), matcher.group(2));
					}
				}
			}
		}
		return found;
	}

	/**
	 * The strings of the data as the configuration reads it. A language's string list replaces the whole file, and the
	 * ones of an extracted older game lack the campaign texts, which the launcher shows from their keys with no text of
	 * its own to fall back to; those keys are taken from the language the data is configured with. Every other key
	 * stays absent, so that the launcher's own text in the language takes its place; so do the keys the launcher words
	 * itself (see {@link #dropUntranslated}) that the language's list has in English.
	 */
	public static Map<String, String> read(final DataTable ini, final DataSource data) throws IOException {
		final Map<String, String> found = read(data);
		final String chosen = GameLanguage.configured(ini);
		if ((found == null) || chosen.isEmpty()
				|| chosen.equals(GameLanguage.canonical(GraphicsSettings.locale(ini)))) {
			return found;
		}
		final DataSource base = WarsmashGdxMapScreen.parseDataSources(ini, "");
		try {
			final Map<String, String> plain = read(base);
			if (plain != null) {
				for (final Map.Entry<String, String> entry : plain.entrySet()) {
					if (entry.getKey().startsWith(CAMPAIGN_PREFIX)) {
						found.putIfAbsent(entry.getKey(), entry.getValue());
					}
				}
				dropUntranslated(found, plain);
			}
		}
		finally {
			base.close();
		}
		return found;
	}

	/**
	 * Takes out of a language's strings the ones of the launcher's own list whose text is the one of the English
	 * strings: the language pack was not translated there, and the launcher's own translation says it better.
	 */
	static Set<String> ownKeys() {
		return OWN_KEYS;
	}

	static void dropUntranslated(final Map<String, String> language, final Map<String, String> english) {
		for (final String key : OWN_KEYS) {
			final String text = language.get(key);
			if ((text != null) && text.equals(english.get(key))) {
				language.remove(key);
			}
		}
	}

	/** A glue string's text in its colours: |cAARRGGBB starts a colour, |r ends it. */
	public static List<Run> runs(final String value) {
		final List<Run> runs = new ArrayList<>();
		final StringBuilder text = new StringBuilder();
		int colour = 0;
		int i = 0;
		while (i < value.length()) {
			final char c = value.charAt(i);
			if ((c == '|') && ((i + 1) < value.length())) {
				final char code = Character.toLowerCase(value.charAt(i + 1));
				if ((code == 'c') && ((i + 10) <= value.length())) {
					try {
						final int next = (int) Long.parseLong(value.substring(i + 2, i + 10), 16);
						flush(runs, text, colour);
						colour = next;
						i += 10;
						continue;
					}
					catch (final NumberFormatException e) {
						// Not a colour code; kept as text.
					}
				}
				else if (code == 'r') {
					flush(runs, text, colour);
					colour = 0;
					i += 2;
					continue;
				}
			}
			text.append(c);
			i++;
		}
		flush(runs, text, colour);
		return runs;
	}

	/** A glue string without its colour codes. */
	public static String plain(final String value) {
		final StringBuilder text = new StringBuilder();
		for (final Run run : runs(value)) {
			text.append(run.text);
		}
		return text.toString();
	}

	private static void flush(final List<Run> runs, final StringBuilder text, final int colour) {
		if (text.length() > 0) {
			runs.add(new Run(text.toString(), colour));
			text.setLength(0);
		}
	}
}
