package com.etheller.warsmash.datasources;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;

/**
 * The language the game text and speech are read in. A game's data holds its language files in a locale layer
 * ("war3.w3mod\_locales\enus.w3mod" of the newer installs, "enus-war3local.mpq" of the 1.30 ones, a "_locales" folder
 * of extracted data); the language chosen here puts the layer of its code over the one the data is configured with, so
 * a file the chosen language lacks is still read in the configured one. Only text and speech come from these layers,
 * the rules of the game do not.
 */
public final class GameLanguage {
	public static final String SECTION = "Language";
	public static final String KEY_LOCALE = "Locale";

	/** Every language code the game data has been seen in, with the name the launchers show for it. */
	private static final String[][] NAMES = { { "enUS", "English (US)" }, { "enGB", "English (UK)" },
			{ "deDE", "Deutsch" }, { "esES", "Español" }, { "esMX", "Español (México)" },
			{ "frFR", "Français" }, { "itIT", "Italiano" }, { "plPL", "Polski" },
			{ "ptBR", "Português (Brasil)" }, { "ruRU", "Russian" }, { "koKR", "Korean" },
			{ "zhTW", "Chinese (Traditional)" }, { "zhCN", "Chinese (Simplified)" }, { "jaJP", "Japanese" } };

	private static final Pattern CODE = Pattern.compile("(?i)[a-z]{4}");
	private static final Pattern MODULE_LAYER = Pattern.compile("(?i)(.*\\\\_locales\\\\)([a-z]{4})(\\.w3mod)");
	private static final Pattern MPQ_LAYER = Pattern.compile("(?i)([a-z]{4})(-war3local\\.mpq)");

	/** The language the skin's locale entries ("MasterFont:zhCN") are read for; empty for none. */
	private static volatile String skinLanguage = "";

	/** The language the running game reads its data in; what a map script asks for as the locale of this machine. */
	private static volatile String gameLanguage = "";

	private GameLanguage() {
	}

	/** Chooses the language whose entries of the skin file (fonts) replace the plain ones; empty for none. */
	public static void useForSkin(final String locale) {
		skinLanguage = canonical(locale);
	}

	/** Notes the language a game data configuration is read in: the chosen one, else the one its data is made for. */
	public static void useInGame(final DataTable ini) {
		gameLanguage = effective(ini);
	}

	/** The language a configuration is read in: the chosen one, else the one its data is made for; empty for none. */
	public static String effective(final DataTable ini) {
		final String chosen = configured(ini);
		return chosen.isEmpty() ? canonical(GraphicsSettings.locale(ini)) : chosen;
	}

	/**
	 * The folder of a map archive that holds the map's own files in a language (the war3map.wts of the strings the
	 * map shows), or null for English, in which the map's own files are already the ones to read.
	 */
	public static String mapFolder(final String locale) {
		final String code = canonical(locale);
		return code.isEmpty() || code.equals("enUS") ? null : "_Locales/" + code + ".w3mod/";
	}

	/** The language the game reads its text and speech in; "enUS" until a configuration says otherwise. */
	public static String inGame() {
		final String language = gameLanguage;
		return language.isEmpty() ? "enUS" : language;
	}

	/** The language chosen for the skin file's entries, or empty. */
	public static String skinLanguage() {
		return skinLanguage;
	}

	/** The code in its usual spelling ("eses" and "ESES" give "esES"), or "" when the text is no language code. */
	public static String canonical(final String code) {
		if ((code == null) || !CODE.matcher(code.trim()).matches()) {
			return "";
		}
		final String trimmed = code.trim();
		return trimmed.substring(0, 2).toLowerCase(Locale.ROOT) + trimmed.substring(2).toUpperCase(Locale.ROOT);
	}

	/** The name of a language for the launchers; the code itself for one they do not know. */
	public static String displayName(final String code) {
		for (final String[] name : NAMES) {
			if (name[0].equalsIgnoreCase(code)) {
				return name[1];
			}
		}
		return code;
	}

	/** Puts the languages in the order the launchers list them: the known ones as in the table, the rest after. */
	public static List<String> sorted(final List<String> codes) {
		final List<String> sorted = new ArrayList<>();
		for (final String[] name : NAMES) {
			if (codes.contains(name[0])) {
				sorted.add(name[0]);
			}
		}
		final List<String> others = new ArrayList<>();
		for (final String code : codes) {
			if (!sorted.contains(code) && !others.contains(code)) {
				others.add(code);
			}
		}
		Collections.sort(others);
		sorted.addAll(others);
		return sorted;
	}

	/** The language the configuration chooses; empty when it leaves the choice to the data. */
	public static String configured(final DataTable ini) {
		final Element section = ini == null ? null : ini.get(SECTION);
		return section == null ? "" : canonical(section.getField(KEY_LOCALE));
	}

	/** Sets the language of a configuration, for this run only; empty leaves the choice to the data. */
	public static void apply(final DataTable ini, final String locale) {
		if (ini == null) {
			return;
		}
		Element section = ini.get(SECTION);
		if (section == null) {
			section = new Element(SECTION, ini);
			ini.put(SECTION, section);
		}
		section.setField(KEY_LOCALE, canonical(locale));
	}

	/**
	 * The prefixes of a game data source with the layer of a language over the one it is configured with. The
	 * prefixes it leaves unchanged when it names no language layer or the data is already read in that language.
	 */
	public static List<String> prefixes(final List<String> configured, final String locale) {
		final String code = canonical(locale);
		if (code.isEmpty()) {
			return configured;
		}
		final String lower = code.toLowerCase(Locale.ROOT);
		for (int i = 0; i < configured.size(); i++) {
			final String prefix = configured.get(i).trim().replace('/', '\\');
			String chosen = null;
			final Matcher module = MODULE_LAYER.matcher(prefix);
			final Matcher mpq = MPQ_LAYER.matcher(prefix);
			if (module.matches()) {
				chosen = module.group(1) + lower + module.group(3);
			}
			else if (mpq.matches()) {
				chosen = lower + mpq.group(2);
			}
			if (chosen != null) {
				if (chosen.equalsIgnoreCase(prefix)) {
					return configured;
				}
				final List<String> list = new ArrayList<>(configured);
				// A later prefix wins, so the chosen language goes after the configured one, which stays as the fallback.
				list.add(i + 1, chosen);
				return list;
			}
		}
		return configured;
	}

	/** The path of the sibling language folder of a "_locales\xx.w3mod" folder path, or null for another folder. */
	public static String siblingFolder(final String path, final String locale) {
		final String code = canonical(locale);
		final String normal = path.replace('/', '\\');
		final Matcher module = MODULE_LAYER.matcher(normal);
		if (code.isEmpty() || !module.matches()) {
			return null;
		}
		return module.group(1) + code.toLowerCase(Locale.ROOT) + module.group(3);
	}

	/** The language folder a flat game data folder keeps its other languages in, or null for no language. */
	public static String childFolder(final String path, final String locale) {
		final String code = canonical(locale);
		if (code.isEmpty() || path.trim().isEmpty() || MODULE_LAYER.matcher(path.replace('/', '\\')).matches()) {
			return null;
		}
		return new File(new File(path, "_locales"), code.toLowerCase(Locale.ROOT) + ".w3mod").getPath();
	}
}
