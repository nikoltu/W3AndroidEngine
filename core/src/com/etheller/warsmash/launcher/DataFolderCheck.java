package com.etheller.warsmash.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.etheller.warsmash.units.Element;

/**
 * What a game data folder holds and how the engine configuration names it: an installed game (CASC), an extracted
 * folder or the files of a local files install. It only reads, so it can look at an installed game.
 */
public final class DataFolderCheck {
	public enum Kind {
		CASC,
		FOLDER,
		/** The "_retail_" folder of a local files install: game files with the text in a locale layer. */
		LOCAL_FILES,
		NONE
	}

	public static final String CLASSIC_PREFIXES = "war3.w3mod,war3.w3mod\\_deprecated.w3mod,war3.w3mod\\_locales\\enus.w3mod";

	private static final Map<String, String> DESCRIPTIONS = Collections.synchronizedMap(new HashMap<>());
	private static final Map<String, Readability> READABILITY = new HashMap<>();
	/** Written last by the data packer into a folder it made. */
	private static final String PACKER_MARKER = "w3-datapacker.txt";
	/** A text locale among the build tags, such as "enUS? enUS speech?:enUS text?". */
	private static final Pattern LOCALE_TAG = Pattern.compile("\\b([a-z]{2}[A-Z]{2})\\b");
	private static final Pattern CONFIG_LOCALE = Pattern.compile("^\\s*LANGID\\s*=\\s*'([a-z]{2}[A-Z]{2})'\\s*$");
	private static final int MAX_SEARCH_DEPTH = 2;
	/** Picking the root of a full storage must not freeze the window. */
	private static final int MAX_SEARCHED_FOLDERS = 400;

	private DataFolderCheck() {
	}

	/**
	 * Without "All files access" Android 11+ still lists the folders of another app's files but hides the files, so a
	 * folder counts only when the files the engine opens first can be read.
	 */
	public static Kind detect(final File folder) {
		if ((folder == null) || !folder.isDirectory() || !folder.canRead()) {
			return Kind.NONE;
		}
		// A local files install keeps the build info, indices and a small archive of an install, but its game files
		// lie unpacked in "_retail_", which the archives do not hold.
		final File archives = child(child(folder, "Data"), "data");
		if (isWarcraftBuildInfo(child(folder, ".build.info")) && hasCascIndex(archives)
				&& isReadable(child(archives, "data.000")) && (detect(child(folder, "_retail_")) == Kind.NONE)) {
			return Kind.CASC;
		}
		// An extracted game data folder, as the desktop Folder data source reads it.
		if (isReadable(child(child(folder, "ui"), "WorldEditGameStrings.txt"))
				&& isReadable(child(child(folder, "units"), "UnitData.slk"))) {
			return Kind.FOLDER;
		}
		if ((localeLayer(folder) != null) && isReadable(child(child(folder, "units"), "UnitData.slk"))) {
			return Kind.LOCAL_FILES;
		}
		return Kind.NONE;
	}

	private static File localeLayer(final File folder) {
		final File locales = child(folder, "_locales");
		File layer = child(locales, "enus.w3mod");
		if (layer == null) {
			final File[] children = locales == null ? null : locales.listFiles();
			if (children != null) {
				Arrays.sort(children);
				for (final File child : children) {
					if (child.getName().toLowerCase(Locale.ROOT).endsWith(".w3mod")) {
						layer = child;
						break;
					}
				}
			}
		}
		return isReadable(child(child(layer, "ui"), "WorldEditGameStrings.txt")) ? layer : null;
	}

	/**
	 * The folder with game data at or near the chosen one: players often pick the folder above it ("Games", "Warcraft
	 * III" around an extracted "data") or a folder inside it ("Data" of an install, "ui" of extracted files).
	 */
	public static File locate(final File folder) {
		if (folder == null) {
			return null;
		}
		File found = gameData(folder);
		if (found == null) {
			found = gameData(folder.getParentFile());
		}
		if (found != null) {
			return found;
		}
		final int[] budget = { MAX_SEARCHED_FOLDERS };
		for (int depth = 1; depth <= MAX_SEARCH_DEPTH; depth++) {
			found = locateBelow(folder, depth, budget);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/** The folder itself, or the "_retail_" folder when it is the root of a local files install. */
	private static File gameData(final File folder) {
		if (detect(folder) != Kind.NONE) {
			return folder;
		}
		final File retail = child(folder, "_retail_");
		return detect(retail) != Kind.NONE ? retail : null;
	}

	private static File locateBelow(final File folder, final int depth, final int[] budget) {
		final File[] children = folder.listFiles();
		if (children == null) {
			return null;
		}
		Arrays.sort(children);
		for (final File child : children) {
			if (!child.isDirectory() || child.getName().startsWith(".") || (budget[0]-- <= 0)) {
				continue;
			}
			final File found = depth == 1 ? gameData(child) : locateBelow(child, depth - 1, budget);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/**
	 * The name of a zip file lying in the folder, or null: the engine cannot read zipped data, and the prep tool hands
	 * its output out as a zip.
	 */
	public static String zipName(final File folder) {
		final File[] children = folder == null ? null : folder.listFiles();
		if (children == null) {
			return null;
		}
		for (final File child : children) {
			if (child.isFile() && child.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
				return child.getName();
			}
		}
		return null;
	}

	/** Whether the main data source of an engine configuration still holds game data. */
	public static boolean hasGameData(final Element dataSources) {
		if (dataSources == null) {
			return true;
		}
		final String type = dataSources.getField("Type00");
		if (!"Folder".equalsIgnoreCase(type) && !"CASC".equalsIgnoreCase(type)) {
			return true;
		}
		String path = dataSources.getField("Path00");
		if ((path.length() > 1) && path.startsWith("\"") && path.endsWith("\"")) {
			path = path.substring(1, path.length() - 1);
		}
		return detect(new File(path)) != Kind.NONE;
	}

	public static String dataSourcesSection(final Kind kind, final File dataFolder, final File... extraFolders) {
		final List<String> lines = new ArrayList<>();
		int index = 0;
		lines.add("Type00=" + (kind == Kind.CASC ? "CASC" : "Folder"));
		lines.add("Path00=\"" + dataFolder.getAbsolutePath() + "\"");
		if (kind == Kind.CASC) {
			lines.add("Prefixes00=" + cascPrefixes(dataFolder));
			// 1.31 stores its file table in the older TVFS layout.
			if (cascVersion(dataFolder).startsWith("version 1.31")) {
				lines.add("OldFormat00=1");
			}
		}
		final List<File> folders = new ArrayList<>();
		if (kind == Kind.LOCAL_FILES) {
			folders.add(localeLayer(dataFolder));
		}
		folders.addAll(Arrays.asList(extraFolders));
		for (final File extraFolder : folders) {
			if ((extraFolder != null) && extraFolder.isDirectory()) {
				index++;
				final String id = String.format(Locale.ROOT, "%02d", index);
				lines.add("Type" + id + "=Folder");
				lines.add("Path" + id + "=\"" + extraFolder.getAbsolutePath() + "\"");
			}
		}
		final StringBuilder section = new StringBuilder("[DataSources]\n");
		section.append("Count=").append(index + 1).append('\n');
		for (final String line : lines) {
			section.append(line).append('\n');
		}
		return section.toString();
	}

	/** A short line for the launcher: the CASC build version, or how many files an extracted folder holds. */
	public static synchronized String describe(final Kind kind, final File folder) {
		final String key = kind + ":" + folder.getAbsolutePath();
		String description = DESCRIPTIONS.get(key);
		if (description == null) {
			description = kind == Kind.CASC ? cascVersion(folder)
					: String.format(Locale.US, "%,d files", countFiles(folder));
			DESCRIPTIONS.put(key, description);
		}
		return description;
	}

	/**
	 * The game version an extracted folder was made from: a build info kept beside or above it, the version the data
	 * packer noted in its marker file, or else "Reforged" for the files only Reforged has (its asset aliases and web
	 * interface), which do not tell the build; empty when the folder does not say.
	 */
	public static synchronized String folderGameVersion(final File folder) {
		final String key = "version:" + folder.getAbsolutePath();
		String version = DESCRIPTIONS.get(key);
		if (version == null) {
			version = "";
			for (final File dir : new File[] { folder, folder.getParentFile() }) {
				if (version.isEmpty() && (dir != null) && new File(dir, ".build.info").isFile()) {
					version = cascVersion(dir).replace("version ", "");
				}
			}
			if (version.isEmpty()) {
				version = markerVersion(new File(folder, PACKER_MARKER));
			}
			if (version.isEmpty() && (new File(folder, "filealiases.json").isFile()
					|| new File(folder, "webui").isDirectory())) {
				version = "Reforged";
			}
			DESCRIPTIONS.put(key, version);
		}
		return version;
	}

	private static String markerVersion(final File marker) {
		return markerField(marker, "gameVersion");
	}

	private static String markerField(final File marker, final String name) {
		if (!marker.isFile()) {
			return "";
		}
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(marker), StandardCharsets.UTF_8))) {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				final int equals = line.indexOf('=');
				if ((equals > 0) && name.equalsIgnoreCase(line.substring(0, equals).trim())) {
					return line.substring(equals + 1).trim();
				}
			}
		}
		catch (final IOException e) {
			return "";
		}
		return "";
	}

	/** The data packer that made an extracted folder, from its marker file; empty for other folders. */
	public static String packerTool(final File folder) {
		return markerField(new File(folder, PACKER_MARKER), "tool");
	}

	/** The file whose reading made the folder count as game data. */
	public static String checkedFile(final Kind kind) {
		return kind == Kind.CASC ? ".build.info" : "units/UnitData.slk";
	}

	/**
	 * The game's text language: an install's build tags, the data packer's note, the locale layer of a local files
	 * install or the language its config.txt sets; empty when the folder does not say.
	 */
	public static synchronized String locale(final Kind kind, final File folder) {
		final String key = "locale:" + folder.getAbsolutePath();
		String locale = DESCRIPTIONS.get(key);
		if (locale == null) {
			locale = "";
			if (kind == Kind.CASC) {
				locale = cascLocales(folder).isEmpty() ? "" : cascLocale(folder);
			}
			else {
				locale = markerField(new File(folder, PACKER_MARKER), "locale");
				final File layer = locale.isEmpty() ? localeLayer(folder) : null;
				if (layer != null) {
					final String name = layer.getName().replaceAll("(?i)\\.w3mod$", "");
					locale = name.length() == 4 ? name.substring(0, 2).toLowerCase(Locale.ROOT)
							+ name.substring(2).toUpperCase(Locale.ROOT) : name;
				}
				if (locale.isEmpty()) {
					locale = configLocale(new File(folder, "config.txt"));
				}
			}
			DESCRIPTIONS.put(key, locale);
		}
		return locale;
	}

	/** The text languages an installed game holds, as its build tags list them ("enUS", "ruRU"), in that order. */
	public static synchronized List<String> cascLocales(final File folder) {
		final List<String> locales = new ArrayList<>();
		final Matcher matcher = LOCALE_TAG.matcher(buildInfoField(folder, "Tags!"));
		while (matcher.find()) {
			if (!locales.contains(matcher.group(1))) {
				locales.add(matcher.group(1));
			}
		}
		return locales;
	}

	/**
	 * The language the engine reads an installed game in: the only one it holds, else the one of this computer's
	 * language, else the first the install lists; English when the tags name none.
	 */
	public static String cascLocale(final File folder) {
		final List<String> locales = cascLocales(folder);
		if (locales.isEmpty()) {
			return "enUS";
		}
		final Locale system = Locale.getDefault();
		for (final String locale : locales) {
			if (locale.equals(system.getLanguage() + system.getCountry())) {
				return locale;
			}
		}
		for (final String locale : locales) {
			if (locale.startsWith(system.getLanguage())) {
				return locale;
			}
		}
		return locales.get(0);
	}

	/** The data source prefixes of an installed game: the Classic files with the text and speech of its language. */
	public static String cascPrefixes(final File folder) {
		return "war3.w3mod,war3.w3mod\\_deprecated.w3mod,war3.w3mod\\_locales\\"
				+ cascLocale(folder).toLowerCase(Locale.ROOT) + ".w3mod";
	}

	/** The LANGID='enUS' line of the game's config.txt; its comment lines list every language. */
	private static String configLocale(final File config) {
		if (!config.isFile()) {
			return "";
		}
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(config), StandardCharsets.UTF_8))) {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				final Matcher matcher = CONFIG_LOCALE.matcher(line);
				if (matcher.matches()) {
					return matcher.group(1);
				}
			}
		}
		catch (final IOException e) {
			return "";
		}
		return "";
	}

	private static String buildInfoField(final File folder, final String prefix) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(new File(folder, ".build.info")), StandardCharsets.UTF_8))) {
			final String header = reader.readLine();
			final String values = reader.readLine();
			if ((header != null) && (values != null)) {
				final String[] names = header.split("\\|");
				final String[] fields = values.split("\\|", -1);
				for (int i = 0; (i < names.length) && (i < fields.length); i++) {
					if (names[i].startsWith(prefix)) {
						return fields[i];
					}
				}
			}
		}
		catch (final IOException e) {
			return "";
		}
		return "";
	}

	private static String cascVersion(final File folder) {
		final String version = buildInfoField(folder, "Version!");
		return version.isEmpty() ? "" : "version " + version;
	}

	private static int countFiles(final File folder) {
		final File[] children = folder.listFiles();
		if (children == null) {
			return 0;
		}
		int count = 0;
		for (final File child : children) {
			count += child.isDirectory() ? countFiles(child) : 1;
		}
		return count;
	}

	/** The files of a folder the game cannot open, as the launcher reports them. */
	public static final class Readability {
		/** Files and folders looked at. */
		public final int files;
		/** Files that cannot be read and folders that cannot be listed. */
		public final int unreadable;
		/** The first of them, relative to the folder, folders ending in "/". */
		public final List<String> names;
		/** The search stopped at its limit, so there may be more. */
		public final boolean partial;

		Readability(final int files, final int unreadable, final List<String> names, final boolean partial) {
			this.files = files;
			this.unreadable = unreadable;
			this.names = names;
			this.partial = partial;
		}

		/** "units/UnitData.slk, ui/" and "…" when there are more than the names kept. */
		public String firstNames() {
			final String shown = String.join(", ", this.names);
			return this.unreadable > this.names.size() ? shown + ", …" : shown;
		}
	}

	/** The files every check of an extracted folder opens; they are tried for real, not only by their rights. */
	private static final String[] OPENED_FILES = { "units/UnitData.slk", "ui/WorldEditGameStrings.txt",
		".build.info" };

	/**
	 * Walks a folder for files the game cannot read: another user's files in the app's folder on Android, or files
	 * without read rights on a computer. A file counts by its read right, except the few every check opens, which are
	 * opened; a folder that cannot be listed counts once.
	 *
	 * @param maxFiles stops after this many files and folders, for a folder as large as a whole storage
	 * @param maxNames how many names are kept
	 */
	public static Readability readability(final File folder, final int maxFiles, final int maxNames) {
		final int[] counts = { 0, 0 };
		final List<String> names = new ArrayList<>();
		final boolean partial = !walkReadability(folder, "", counts, names, maxFiles, maxNames);
		final Readability readability = new Readability(counts[0], counts[1], names, partial);
		synchronized (READABILITY) {
			READABILITY.put(folder.getAbsolutePath(), readability);
		}
		return readability;
	}

	/** Forgets what was read of a folder whose files were replaced, such as by an import. */
	public static synchronized void forget(final File folder) {
		final String path = folder.getAbsolutePath();
		DESCRIPTIONS.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + path));
		synchronized (READABILITY) {
			READABILITY.remove(path);
		}
	}

	/** What the last walk of this folder found, without walking it again; null when it was not walked. */
	public static Readability lastReadability(final File folder) {
		synchronized (READABILITY) {
			return READABILITY.get(folder.getAbsolutePath());
		}
	}

	private static boolean walkReadability(final File folder, final String relative, final int[] counts,
			final List<String> names, final int maxFiles, final int maxNames) {
		final File[] children = folder.listFiles();
		if (children == null) {
			unreadable(relative.isEmpty() ? "./" : relative, counts, names, maxNames);
			return true;
		}
		Arrays.sort(children);
		for (final File child : children) {
			if (counts[0]++ >= maxFiles) {
				return false;
			}
			final String path = relative + child.getName();
			if (child.isDirectory()) {
				if (!walkReadability(child, path + "/", counts, names, maxFiles, maxNames)) {
					return false;
				}
			}
			else if (!child.canRead() || (isOpenedFile(path) && !isReadable(child))) {
				unreadable(path, counts, names, maxNames);
			}
		}
		return true;
	}

	private static boolean isOpenedFile(final String path) {
		final String lower = path.toLowerCase(Locale.ROOT);
		for (final String opened : OPENED_FILES) {
			final String name = opened.toLowerCase(Locale.ROOT);
			if (lower.equals(name) || lower.endsWith("/" + name)) {
				return true;
			}
		}
		return false;
	}

	private static void unreadable(final String path, final int[] counts, final List<String> names,
			final int maxNames) {
		counts[1]++;
		if (names.size() < maxNames) {
			names.add(path);
		}
	}

	/** The child with this name in any letter case, as the data sources look files up. */
	private static File child(final File folder, final String name) {
		if (folder == null) {
			return null;
		}
		final File[] children = folder.listFiles();
		if (children == null) {
			return null;
		}
		for (final File child : children) {
			if (child.getName().equalsIgnoreCase(name)) {
				return child;
			}
		}
		return null;
	}

	private static boolean isReadable(final File file) {
		if ((file == null) || !file.isFile()) {
			return false;
		}
		try (InputStream stream = new FileInputStream(file)) {
			return stream.read() >= 0;
		}
		catch (final IOException e) {
			return false;
		}
	}

	/** Other Blizzard games are CASC installs too; their build info names another CDN path. */
	private static boolean isWarcraftBuildInfo(final File file) {
		if (!isReadable(file)) {
			return false;
		}
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
			final String header = reader.readLine();
			final String values = reader.readLine();
			if ((header == null) || (values == null)) {
				return false;
			}
			final String[] names = header.split("\\|");
			final String[] fields = values.split("\\|", -1);
			for (int i = 0; (i < names.length) && (i < fields.length); i++) {
				if (names[i].startsWith("CDN Path!")) {
					return fields[i].toLowerCase(Locale.ROOT).contains("war3");
				}
			}
			return true;
		}
		catch (final IOException e) {
			return false;
		}
	}

	private static boolean hasCascIndex(final File folder) {
		final File[] children = folder == null ? null : folder.listFiles();
		if (children == null) {
			return false;
		}
		for (final File child : children) {
			if (child.getName().toLowerCase(Locale.ROOT).endsWith(".idx") && isReadable(child)) {
				return true;
			}
		}
		return false;
	}
}
