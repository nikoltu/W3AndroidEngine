package com.etheller.warsmash.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.etheller.warsmash.datasources.FolderIndexStore;
import com.etheller.warsmash.datasources.FolderSignature;
import com.etheller.warsmash.datasources.GameLanguage;
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
	/** How many files a folder held when it was last counted whole, so that an import does not count it again. */
	private static final Map<String, Integer> FILE_COUNTS = new HashMap<>();
	/** Written last by the data packer into a folder it made. */
	private static final String PACKER_MARKER = "w3-datapacker.txt";
	/** A text locale among the build tags, such as "enUS? enUS speech?:enUS text?". */
	private static final Pattern LOCALE_TAG = Pattern.compile("\\b([a-z]{2}[A-Z]{2})\\b");
	private static final Pattern VERSION = Pattern.compile("(?:^|[^0-9])([0-9]{1,4})[.]([0-9]{1,4})(?:[.]|$)");
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
				&& isReadable(child(archives, "data.000")) && (missingCascFile(folder) == null)
				&& (detect(child(folder, "_retail_")) == Kind.NONE)) {
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

	/**
	 * The first file the engine opens to mount an installed game that is missing, by the exact names it opens them
	 * (the lookups above ignore case, a phone's storage does not); null when all are there.
	 */
	public static String missingCascFile(final File folder) {
		if (!new File(folder, ".build.info").isFile()) {
			return ".build.info";
		}
		if (!new File(folder, "Data/data").isDirectory()) {
			return "Data/data";
		}
		final String key = buildInfoField(folder, "Build Key");
		if (key.length() < 4) {
			return ".build.info";
		}
		final String config = "Data/config/" + key.substring(0, 2) + "/" + key.substring(2, 4) + "/" + key;
		return new File(folder, config).isFile() ? null : config;
	}

	/** The locale layer a local files install keeps its text in; null for the other kinds. */
	static File localeLayerOf(final Kind kind, final File folder) {
		return kind == Kind.LOCAL_FILES ? localeLayer(folder) : null;
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
			if (usesOldCascFormat(dataFolder)) {
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
			description = kind == Kind.CASC ? cascVersion(folder) : filesText(folder);
			DESCRIPTIONS.put(key, description);
		}
		return description;
	}

	private static String filesText(final File folder) {
		final String path = folder.getAbsolutePath();
		Integer count;
		synchronized (FILE_COUNTS) {
			count = FILE_COUNTS.get(path);
		}
		if (count == null) {
			final Readability kept = current(folder);
			count = kept != null ? Integer.valueOf(kept.fileCount) : null;
		}
		if (count == null) {
			count = countFiles(folder);
			synchronized (FILE_COUNTS) {
				FILE_COUNTS.put(path, count);
			}
		}
		return String.format(Locale.US, "%,d files", count);
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
				// Only a local files install keeps its own text in a locale layer; in a flat folder the layers are
				// language packs added to it and say nothing about the language of the folder itself.
				final File layer = locale.isEmpty() && kind == Kind.LOCAL_FILES ? localeLayer(folder) : null;
				if (layer != null) {
					final String name = layer.getName().replaceAll("(?i)\\.w3mod$", "");
					locale = name.length() == 4 ? name.substring(0, 2).toLowerCase(Locale.ROOT)
							+ name.substring(2).toUpperCase(Locale.ROOT) : name;
				}
				if (locale.isEmpty()) {
					locale = configLocale(new File(folder, "config.txt"));
				}
				// A flat folder that only got language packs imported is the English game they were added to.
				if (locale.isEmpty() && kind != Kind.LOCAL_FILES && child(folder, "_locales") != null) {
					locale = "enUS";
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
	 * The languages a game data folder can be read in, in the order the launchers list them: an install's build tags, or
	 * the language folders an extracted folder or local files install keeps, each holding the game's text. A folder
	 * keeps its own language beside them.
	 */
	public static synchronized List<String> locales(final Kind kind, final File folder) {
		final String key = "locales:" + kind + ":" + folder.getAbsolutePath();
		String cached = DESCRIPTIONS.get(key);
		if (cached == null) {
			final List<String> found = new ArrayList<>();
			if (kind == Kind.CASC) {
				found.addAll(cascLocales(folder));
			}
			else {
				final File[] layers = child(folder, "_locales") == null ? null : child(folder, "_locales").listFiles();
				for (final File layer : layers == null ? new File[0] : layers) {
					final String name = layer.getName();
					final String code = name.toLowerCase(Locale.ROOT).endsWith(".w3mod")
							? GameLanguage.canonical(name.substring(0, name.length() - ".w3mod".length())) : "";
					if (!code.isEmpty() && isReadable(child(child(layer, "ui"), "WorldEditGameStrings.txt"))
							&& !found.contains(code)) {
						found.add(code);
					}
				}
			}
			final String own = locale(kind, folder);
			// The base language is read from the game's own text, not a language folder, so it stays selectable
			// beside the packs added to it.
			if (!own.isEmpty() && !found.contains(own)) {
				found.add(own);
			}
			cached = String.join(",", GameLanguage.sorted(found));
			DESCRIPTIONS.put(key, cached);
		}
		return cached.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(cached.split(",")));
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

	/** A field of the build info's row the game runs on (the active one; the first when none is marked). */
	private static String buildInfoField(final File folder, final String prefix) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(new File(folder, ".build.info")), StandardCharsets.UTF_8))) {
			final String header = reader.readLine();
			if (header == null) {
				return "";
			}
			final String[] names = header.split("\\|");
			String[] row = null;
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.isEmpty()) {
					continue;
				}
				final String[] fields = line.split("\\|", -1);
				if (row == null) {
					row = fields;
				}
				if ("1".equals(rowField(names, fields, "Active!"))) {
					row = fields;
					break;
				}
			}
			return row == null ? "" : rowField(names, row, prefix);
		}
		catch (final IOException e) {
			return "";
		}
	}

	private static String rowField(final String[] names, final String[] fields, final String prefix) {
		for (int i = 0; (i < names.length) && (i < fields.length); i++) {
			if (names[i].startsWith(prefix)) {
				return fields[i];
			}
		}
		return "";
	}

	/**
	 * Whether an installed game keeps its file table in the older TVFS layout, which 1.30 and 1.31 use and 1.32 and
	 * Reforged no longer do. A build info without a readable version is taken as the newer layout.
	 */
	public static boolean usesOldCascFormat(final File folder) {
		return isOldCascFormat(buildInfoField(folder, "Version!"));
	}

	/**
	 * Whether the engine cannot run an installed game: 1.30 opens with the older layout but its Classic files lie in
	 * MPQ archives of the install ("war3.mpq"), not in the "war3.w3mod" the engine reads.
	 */
	public static boolean isUnsupportedCasc(final File folder) {
		return isUnsupportedCascVersion(buildInfoField(folder, "Version!"));
	}

	static boolean isOldCascFormat(final String version) {
		final int[] numbers = versionNumbers(version);
		return (numbers != null) && ((numbers[0] < 1) || ((numbers[0] == 1) && (numbers[1] < 32)));
	}

	static boolean isUnsupportedCascVersion(final String version) {
		final int[] numbers = versionNumbers(version);
		return (numbers != null) && ((numbers[0] < 1) || ((numbers[0] == 1) && (numbers[1] < 31)));
	}

	/** Major and minor number of a version such as "1.31.1.12164" or "version 1.30.4.11274"; null when it has none. */
	private static int[] versionNumbers(final String version) {
		final Matcher matcher = VERSION.matcher(version == null ? "" : version);
		if (!matcher.find()) {
			return null;
		}
		try {
			return new int[] { Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)) };
		}
		catch (final NumberFormatException e) {
			return null;
		}
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
		/** Files among those looked at, without the folders. */
		final int fileCount;

		Readability(final int files, final int unreadable, final List<String> names, final boolean partial,
				final int fileCount) {
			this.files = files;
			this.unreadable = unreadable;
			this.names = names;
			this.partial = partial;
			this.fileCount = fileCount;
		}

		/** "units/UnitData.slk, ui/" and "…" when there are more than the names kept. */
		public String firstNames() {
			final String shown = String.join(", ", this.names);
			return this.unreadable > this.names.size() ? shown + ", …" : shown;
		}
	}

	/**
	 * Walks a folder for files the game cannot read: another user's files in the app's folder on Android, or files
	 * without read rights on a computer. A file counts by its read right, except the few every check opens, which are
	 * opened; a folder that cannot be listed counts once.
	 *
	 * @param maxFiles stops after this many files and folders, for a folder as large as a whole storage
	 * @param maxNames how many names are kept
	 */
	public static Readability readability(final File folder, final int maxFiles, final int maxNames) {
		return readability(folder, maxFiles, maxNames, new FileRights());
	}

	static Readability readability(final File folder, final int maxFiles, final int maxNames, final Rights rights) {
		// Two windows asking at once (the data folder window is opened and resumed) would walk the same folder twice.
		synchronized (WALK_LOCK) {
			final Readability kept = current(folder);
			if ((kept != null) && (kept.files <= maxFiles)) {
				return kept;
			}
			return walk(folder, maxFiles, maxNames, rights);
		}
	}

	private static final Object WALK_LOCK = new Object();

	private static Readability walk(final File folder, final int maxFiles, final int maxNames, final Rights rights) {
		WALKS.incrementAndGet();
		final long started = System.currentTimeMillis();
		final FolderCheckCache.Snapshot snapshot = new FolderCheckCache.Snapshot();
		final int[] counts = { 0, 0, 0 };
		final List<String> names = new ArrayList<>();
		final List<String> listed = new ArrayList<>();
		final boolean partial = !walkReadability(folder, "", counts, names, maxFiles, maxNames, snapshot, new Pace(rights),
				listed);
		final Readability readability = new Readability(counts[0], counts[1], names, partial, counts[2]);
		synchronized (READABILITY) {
			READABILITY.put(folder.getAbsolutePath(), readability);
		}
		if (!partial) {
			// The walk met every file, so the launcher's file count needs no second walk over the same folder.
			synchronized (FILE_COUNTS) {
				FILE_COUNTS.put(folder.getAbsolutePath(), counts[2]);
			}
			synchronized (DataFolderCheck.class) {
				DESCRIPTIONS.remove(Kind.FOLDER + ":" + folder.getAbsolutePath());
				DESCRIPTIONS.remove(Kind.LOCAL_FILES + ":" + folder.getAbsolutePath());
			}
		}
		if (!partial && (counts[1] == 0)) {
			snapshot.entries = counts[0];
			snapshot.files = counts[2];
			FolderCheckCache.put(folder, snapshot, started);
			// The data source needs the same list of files; it is kept for it, so that it does not walk the folder too.
			if (snapshot.settled(started)) {
				FolderIndexStore.keep(folder, snapshot, listed);
			}
		}
		else {
			FolderCheckCache.remove(folder);
		}
		return readability;
	}

	/** Forgets everything held in memory, as a new start of the app does; for the tests. */
	static synchronized void resetMemory() {
		DESCRIPTIONS.clear();
		synchronized (READABILITY) {
			READABILITY.clear();
		}
		synchronized (FILE_COUNTS) {
			FILE_COUNTS.clear();
		}
		FolderCheckCache.restart();
	}

	/** Walks made, for the tests. */
	static final java.util.concurrent.atomic.AtomicInteger WALKS = new java.util.concurrent.atomic.AtomicInteger();

	/**
	 * Keeps what the walks of folders find in this file, so that the first check after the app starts does not walk a
	 * large folder again. What is kept stays valid across updates of the app; see FolderCheckCache for when it is read
	 * again.
	 */
	public static void useCache(final File file) {
		FolderCheckCache.use(file);
	}

	/**
	 * The result of a whole walk of the folder when it was made and the folder still looks as it did (the times of
	 * its folders and of the files every check opens are the same); null when the folder has to be walked. It
	 * only looks at the folders, not at the files in them.
	 */
	public static Readability current(final File folder) {
		final FolderCheckCache.Snapshot snapshot = FolderCheckCache.get(folder);
		final String path = folder.getAbsolutePath();
		if (snapshot == null) {
			synchronized (READABILITY) {
				READABILITY.remove(path);
			}
			synchronized (FILE_COUNTS) {
				FILE_COUNTS.remove(path);
			}
			return null;
		}
		synchronized (READABILITY) {
			Readability known = READABILITY.get(path);
			if ((known == null) || (known.files != snapshot.entries) || (known.fileCount != snapshot.files)
					|| (known.unreadable != 0) || known.partial) {
				known = new Readability(snapshot.entries, 0, new ArrayList<>(), false, snapshot.files);
				READABILITY.put(path, known);
			}
			synchronized (FILE_COUNTS) {
				FILE_COUNTS.put(path, snapshot.files);
			}
			return known;
		}
	}

	/** The content problems found for the folder at the time of its walk; null when the walk is not current. */
	static List<String> cachedProblems(final File folder) {
		final FolderCheckCache.Snapshot snapshot = FolderCheckCache.get(folder);
		return (snapshot == null) || (snapshot.problems == null) ? null : new ArrayList<>(snapshot.problems);
	}

	static void cacheProblems(final File folder, final List<String> problems) {
		FolderCheckCache.setProblems(folder, problems);
	}

	/** Checks what is kept of a folder before an import adds files to it. */
	static void beforeImport(final File folder) {
		FolderCheckCache.beforeImport(folder);
	}

	/** The files and folders below a folder, itself included, that a walk meets, and how many of them are files. */
	public static final class Tally {
		int entries;
		int files;
		int unreadable;
		/** The folders the files were added to or that were added, where they are now. */
		final java.util.Set<File> touched = new java.util.HashSet<>();

		void add(final Tally other) {
			this.entries += other.entries;
			this.files += other.files;
			this.unreadable += other.unreadable;
			this.touched.addAll(other.touched);
		}

		/** A folder whose contents changed. */
		void touch(final File folder) {
			if (folder != null) {
				this.touched.add(folder);
			}
		}
	}

	/** Counts a file or a folder with everything in it, as {@link #readability} does. */
	public static Tally tally(final File file) {
		return tally(file, null);
	}

	/** As {@link #tally(File)}, noting the folders below as they will be named once moved to the other place. */
	public static Tally tally(final File file, final File movedTo) {
		final Tally tally = new Tally();
		tally(file, movedTo, tally);
		return tally;
	}

	private static void tally(final File file, final File movedTo, final Tally tally) {
		tally.entries++;
		if (file.isDirectory()) {
			if (movedTo != null) {
				tally.touch(movedTo);
			}
			final File[] children = file.listFiles();
			if (children == null) {
				tally.unreadable++;
				return;
			}
			for (final File child : children) {
				tally(child, movedTo == null ? null : new File(movedTo, child.getName()), tally);
			}
		}
		else {
			tally.files++;
			if (!file.canRead()) {
				tally.unreadable++;
			}
		}
	}

	/**
	 * Takes files that were added to a folder into what was read of it, so that the folder is not walked whole again:
	 * only its description, language and content are read anew. What was not read before stays unread, and files the
	 * game cannot read send the next check over the whole folder.
	 */
	public static void added(final File folder, final Tally added) {
		final String path = folder.getAbsolutePath();
		final Readability known;
		final Integer count;
		synchronized (DataFolderCheck.class) {
			DESCRIPTIONS.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + path)
					&& !entry.getKey().startsWith(Kind.FOLDER + ":")
					&& !entry.getKey().startsWith(Kind.LOCAL_FILES + ":"));
			synchronized (READABILITY) {
				known = READABILITY.remove(path);
			}
			synchronized (FILE_COUNTS) {
				count = FILE_COUNTS.remove(path);
			}
			if (added.unreadable > 0) {
				DESCRIPTIONS.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + path));
				FolderCheckCache.remove(folder);
				return;
			}
		}
		// It may wait for the folder's times to settle, which must not hold up the checks asking for descriptions.
		FolderCheckCache.afterImport(folder, added);
		synchronized (DataFolderCheck.class) {
			addedToMemory(path, added, known, count);
		}
	}

	private static void addedToMemory(final String path, final Tally added, final Readability known,
			final Integer count) {
		if (count != null) {
			final int total = count + added.files;
			synchronized (FILE_COUNTS) {
				FILE_COUNTS.put(path, total);
			}
			DESCRIPTIONS.remove(Kind.FOLDER + ":" + path);
			DESCRIPTIONS.remove(Kind.LOCAL_FILES + ":" + path);
		}
		if ((known != null) && (known.unreadable == 0) && !known.partial) {
			synchronized (READABILITY) {
				READABILITY.put(path, new Readability(known.files + added.entries, 0, known.names, false,
						known.fileCount + added.files));
			}
		}
	}

	/** Forgets what was read of a folder whose files were replaced, such as by an import. */
	public static synchronized void forget(final File folder) {
		final String path = folder.getAbsolutePath();
		DESCRIPTIONS.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + path));
		synchronized (READABILITY) {
			READABILITY.remove(path);
		}
		synchronized (FILE_COUNTS) {
			FILE_COUNTS.remove(path);
		}
		FolderCheckCache.remove(folder);
	}

	/** What the last walk of this folder found, without walking it again; null when it was not walked. */
	public static Readability lastReadability(final File folder) {
		synchronized (READABILITY) {
			return READABILITY.get(folder.getAbsolutePath());
		}
	}

	/**
	 * How a walk learns whether the game can read a file. Asking the system for every file is slow where each answer
	 * goes to another process (the shared storage of a phone: twenty seconds for the files of the game), so a file whose
	 * owner, group and mode are those of a file of the same folder that was found readable is readable too.
	 */
	interface Rights {
		/** What the read right of a file depends on besides its folder; null when that cannot be told. */
		Object kindOf(File file);

		boolean canRead(File file);

		/**
		 * Whether the file is a folder and its kind, from a single look at it (each look is a round trip too); null
		 * when the rights cannot give both, and then the walk asks for each alone.
		 */
		default Look look(final File file) {
			return null;
		}

		/** Whether a walk may time these answers to choose how it asks; false asks as the kinds allow, always. */
		default boolean timed() {
			return false;
		}
	}

	/** What a single look at a file tells. */
	static final class Look {
		final boolean directory;
		final Object kind;

		Look(final boolean directory, final Object kind) {
			this.directory = directory;
			this.kind = kind;
		}
	}

	/** The rights the system gives: owner, group and mode of a file where the file system has them. */
	static final class FileRights implements Rights {
		private boolean unsupported;

		@Override
		public Object kindOf(final File file) {
			if (this.unsupported) {
				return null;
			}
			try {
				final PosixFileAttributes attributes = Files.readAttributes(file.toPath(), PosixFileAttributes.class);
				return Arrays.asList(attributes.permissions(), attributes.owner(), attributes.group());
			}
			catch (final UnsupportedOperationException e) {
				this.unsupported = true;
				return null;
			}
			catch (final IOException | RuntimeException e) {
				return null;
			}
		}

		@Override
		public Look look(final File file) {
			if (this.unsupported) {
				return null;
			}
			try {
				final PosixFileAttributes attributes = Files.readAttributes(file.toPath(), PosixFileAttributes.class);
				return new Look(attributes.isDirectory(),
						Arrays.asList(attributes.permissions(), attributes.owner(), attributes.group()));
			}
			catch (final UnsupportedOperationException e) {
				this.unsupported = true;
				return null;
			}
			catch (final IOException | RuntimeException e) {
				return null;
			}
		}

		@Override
		public boolean canRead(final File file) {
			return file.canRead();
		}

		@Override
		public boolean timed() {
			return true;
		}
	}

	/**
	 * How a walk asks the rights. The shortcut by kind exists for storage where each answer is a round trip to another
	 * process; where an answer costs about what a look at the file does (the app's own folders, internal storage) it
	 * only adds the risk of a file that differs without its kind showing it. So the first files are asked for one by
	 * one, with both answers timed, and the rest are asked by kind only when asking is much slower than looking.
	 */
	private static final class Pace {
		/** Files asked for one by one before the walk chooses. */
		static final int SAMPLE = 100;
		/** An answer must take this many times a look at the file, and this long, to be worth sparing. */
		static final long SLOWER_BY = 2;
		static final long SLOW_NANOS = 50_000;
		/** Files of a kind in a folder that must be found readable before the rest of the kind are taken to be. */
		static final int CONFIRMED = 2;

		private final Rights rights;
		private final long[] reads = new long[SAMPLE];
		private final long[] looks = new long[SAMPLE];
		private int sampled;
		private int looked;
		private boolean measuring;
		private boolean byKind = true;

		Pace(final Rights rights) {
			this.rights = rights;
			this.measuring = rights.timed();
		}

		boolean looks() {
			return this.measuring || this.byKind;
		}

		boolean canRead(final File file, final Look look, final long lookNanos,
				final java.util.Map<Object, int[]> readableKinds) {
			if (this.measuring) {
				final long started = System.nanoTime();
				final boolean readable = this.rights.canRead(file);
				this.reads[this.sampled] = System.nanoTime() - started;
				this.looks[this.sampled] = lookNanos;
				if (look != null) {
					this.looked++;
				}
				if (readable && (look != null) && (look.kind != null)) {
					readableKinds.computeIfAbsent(look.kind, kind -> new int[1])[0]++;
				}
				if (++this.sampled >= SAMPLE) {
					this.measuring = false;
					// A typical answer, so that a pause of the thread in a few of them does not decide, against the
					// quick looks: the first ones are slow while the code is new to the runtime.
					Arrays.sort(this.reads);
					Arrays.sort(this.looks);
					final long read = this.reads[SAMPLE / 2];
					final long quick = this.looks[SAMPLE / 10];
					this.byKind = (this.looked == SAMPLE) && (read > SLOWER_BY * quick) && (read >= SLOW_NANOS);
				}
				return readable;
			}
			if (!this.byKind) {
				return this.rights.canRead(file);
			}
			final Object kind = look == null ? this.rights.kindOf(file) : look.kind;
			final int[] confirmed = kind == null ? null : readableKinds.get(kind);
			if ((confirmed != null) && (confirmed[0] >= CONFIRMED)) {
				return true;
			}
			final boolean readable = this.rights.canRead(file);
			if (readable && (kind != null)) {
				readableKinds.computeIfAbsent(kind, k -> new int[1])[0]++;
			}
			return readable;
		}
	}

	private static boolean walkReadability(final File folder, final String relative, final int[] counts,
			final List<String> names, final int maxFiles, final int maxNames,
			final FolderCheckCache.Snapshot snapshot, final Pace pace, final List<String> listed) {
		// The time is read before the folder is listed, so that a change during the walk shows at the next check.
		snapshot.dirs.put(relative.isEmpty() ? "" : relative.substring(0, relative.length() - 1),
				folder.lastModified());
		final File[] children = folder.listFiles();
		if (children == null) {
			unreadable(relative.isEmpty() ? "./" : relative, counts, names, maxNames);
			return true;
		}
		Arrays.sort(children);
		// Only a kind found readable by two of its files is remembered: any other file is asked for itself.
		final java.util.Map<Object, int[]> readableKinds = new java.util.HashMap<>();
		for (final File child : children) {
			if (counts[0]++ >= maxFiles) {
				return false;
			}
			final String path = relative + child.getName();
			final long looked = pace.looks() ? System.nanoTime() : 0;
			final Look look = pace.looks() ? pace.rights.look(child) : null;
			final long lookNanos = pace.looks() ? System.nanoTime() - looked : 0;
			if (look == null ? child.isDirectory() : look.directory) {
				if (!walkReadability(child, path + "/", counts, names, maxFiles, maxNames, snapshot, pace, listed)) {
					return false;
				}
			}
			else {
				counts[2]++;
				listed.add(path);
				if (!pace.canRead(child, look, lookNanos, readableKinds) || (isOpenedFile(path) && !isReadable(child))) {
					unreadable(path, counts, names, maxNames);
				}
				else if (isOpenedFile(path)) {
					snapshot.keys.put(path, new long[] { child.length(), child.lastModified() });
				}
			}
		}
		return true;
	}

	private static boolean isOpenedFile(final String path) {
		return FolderSignature.isOpenedFile(path);
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

	static boolean isReadable(final File file) {
		return FolderSignature.isReadable(file);
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
