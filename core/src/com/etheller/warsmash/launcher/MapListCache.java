package com.etheller.warsmash.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.GameLanguage;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapObjectData;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3iFlags;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.custom.WTS;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.MapListContainer;

/**
 * The custom game list kept on disk between runs. The launcher and the engine's own menu keep and read the same
 * file, so a map is opened only once for both. The list is used while the map files are the same, by name, size and
 * time, and made again when a map was added, replaced or removed.
 */
public final class MapListCache {
	// 2: test maps are no longer listed, so lists kept by format 1 are made again.
	// 3: the header also holds a fingerprint of the map files, so added, removed and changed maps are noticed.
	// 4: maps that cannot be read in the player's own folders are kept with their reason.
	// 5: every map also holds the issue that keeps it from being played.
	private static final int FORMAT = 5;
	private static final int MAP_FOLDER_DEPTH = 8;
	private static final String SKIPPED = "!";

	/** Where the launcher and the engine keep the list; set by the platform before either reads it. */
	private static volatile File shared;

	/** Why a map of the player's own folders is not in the list. */
	public enum Problem {
		/** The file is not a map archive, or it is damaged. */
		UNREADABLE,
		/** The archive has no map information. */
		NO_INFORMATION
	}

	/** A map of the player's own folders that was left out of the list, and why. */
	public static final class Skipped {
		public final String path;
		public final Problem problem;

		Skipped(final String path, final Problem problem) {
			this.path = path;
			this.problem = problem;
		}
	}

	/** The maps of the list in their order, and the ones left out with a reason. */
	public static final class Listing {
		public final List<GameCatalog.MapEntry> maps;
		public final List<Skipped> skipped;

		Listing(final List<GameCatalog.MapEntry> maps, final List<Skipped> skipped) {
			this.maps = maps;
			this.skipped = skipped;
		}
	}

	private MapListCache() {
	}

	public static void setShared(final File file) {
		shared = file;
	}

	/** The file the launcher and the engine share, or null when the platform did not name one. */
	public static File shared() {
		return shared;
	}

	/**
	 * The list for the game data, read from the file while it is for the same data and map files, else made by
	 * opening the maps and kept again.
	 */
	public static Listing load(final File file, final DataTable ini, final DataSource data) {
		final String header = header(GameCatalog.dataKey(ini), ini);
		GameLanguage.useInGame(ini);
		Listing listing = read(file, header);
		if (listing == null) {
			listing = scan(data);
			write(file, header, listing);
		}
		return listing;
	}

	static String header(final String dataKey, final DataTable ini) {
		// The titles are read in the game's language, so a list kept for another language is made again.
		final String language = GameLanguage.mapFolder(GameLanguage.effective(ini)) == null ? ""
				: "|" + GameLanguage.effective(ini);
		return FORMAT + "|" + dataKey + "|" + fingerprint(ini) + language;
	}

	/**
	 * Tells when the maps of the game data may have changed without opening the data: the map files of the folder
	 * sources and the files the other sources are kept in, by name, size and time. Folder times are left out: a language or graphics pack
	 * changes them without touching any map, and an added, removed or renamed map shows in the file names.
	 */
	static String fingerprint(final DataTable ini) {
		final Element sources = ini.get("DataSources");
		final StringBuilder facts = new StringBuilder();
		for (int i = 0; (sources != null) && (i < sources.size()); i++) {
			final String id = String.format(Locale.ROOT, "%02d", i);
			final String path = sources.getField("Path" + id);
			if ((path == null) || path.isEmpty()) {
				continue;
			}
			final File root = new File(path);
			final String type = sources.getField("Type" + id);
			facts.append('#').append(i).append('\n');
			if ("Folder".equals(type)) {
				addMapFacts(facts, root, "", 0, false);
			}
			else if (CustomContent.SOURCE_TYPE.equals(type)) {
				addMapFacts(facts, new File(root, "Maps"), "/Maps", 1, true);
			}
			else {
				// A directory's own time changes with any file added beside the maps, so only a file source counts.
				if (root.isFile()) {
					addFileFacts(facts, root, "");
				}
				// A CASC storage is updated through its build information.
				addFileFacts(facts, new File(root, ".build.info"), ".build.info");
			}
		}
		final CRC32 crc = new CRC32();
		crc.update(facts.toString().getBytes(StandardCharsets.UTF_8));
		return Long.toHexString(crc.getValue()) + "-" + facts.length();
	}

	private static void addFileFacts(final StringBuilder facts, final File file, final String name) {
		facts.append(name).append('\t').append(file.length()).append('\t').append(file.lastModified()).append('\n');
	}

	/**
	 * A folder source can hold maps in its root, in the folder of a game data layer and below Maps at any depth;
	 * nothing else is looked into, since the data folders hold tens of thousands of files.
	 */
	private static void addMapFacts(final StringBuilder facts, final File folder, final String name, final int depth,
			final boolean inMaps) {
		final File[] children = folder.listFiles();
		if (children == null) {
			return;
		}
		Arrays.sort(children);
		for (final File child : children) {
			final String childName = name + "/" + child.getName();
			if (child.isDirectory()) {
				final boolean maps = child.getName().equalsIgnoreCase("Maps");
				if ((depth < MAP_FOLDER_DEPTH) && (inMaps || maps || (depth == 0))) {
					addMapFacts(facts, child, childName, depth + 1, inMaps || maps);
				}
			}
			else {
				final String lower = child.getName().toLowerCase(Locale.ROOT);
				if (lower.endsWith(".w3x") || lower.endsWith(".w3m")) {
					addFileFacts(facts, child, childName);
				}
			}
		}
	}

	static Listing read(final File file, final String header) {
		if ((file == null) || !file.isFile()) {
			return null;
		}
		try {
			final List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
			if (lines.isEmpty() || !lines.get(0).equals(header)) {
				return null;
			}
			final List<GameCatalog.MapEntry> entries = new ArrayList<>();
			final List<Skipped> skipped = new ArrayList<>();
			for (int i = 1; i < lines.size(); i++) {
				final String[] parts = lines.get(i).split("\t", -1);
				if ((parts.length == 3) && SKIPPED.equals(parts[0])) {
					skipped.add(new Skipped(parts[1], Problem.valueOf(parts[2])));
				}
				else if (parts.length == 5) {
					entries.add(new GameCatalog.MapEntry(parts[0], Integer.parseInt(parts[1]), parts[2],
							"1".equals(parts[3]), MapIssue.valueOf(parts[4])));
				}
			}
			return new Listing(entries, skipped);
		}
		catch (final IOException | RuntimeException e) {
			return null;
		}
	}

	static void write(final File file, final String header, final Listing listing) {
		if (file == null) {
			return;
		}
		final StringBuilder text = new StringBuilder().append(header).append('\n');
		for (final GameCatalog.MapEntry entry : listing.maps) {
			text.append(entry.path).append('\t').append(entry.players).append('\t')
					.append(entry.name.replace('\t', ' ').replace('\n', ' ')).append('\t')
					.append(entry.melee ? '1' : '0').append('\t').append(entry.issue.name()).append('\n');
		}
		for (final Skipped skip : listing.skipped) {
			text.append(SKIPPED).append('\t').append(skip.path).append('\t').append(skip.problem.name()).append('\n');
		}
		// Written beside the file first, since the launcher and a game can write the list at the same time.
		final File partial = new File(file.getAbsoluteFile().getParentFile(),
				file.getName() + "." + Long.toHexString(System.nanoTime()));
		try {
			partial.getParentFile().mkdirs();
			Files.write(partial.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
			Files.move(partial.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		catch (final IOException e) {
			LauncherLog.w("Unable to keep the map list", e);
			partial.delete();
		}
	}

	/** Whether a map lies in the folders of the player's own content, whose unreadable maps are reported. */
	static boolean isOwnMap(final String path) {
		final String lower = path.replace('/', '\\').toLowerCase(Locale.ROOT);
		return lower.startsWith("maps\\custom\\") || lower.startsWith("maps\\melee\\");
	}

	/** Opens the maps of the data as the engine's list does, ordered by players, then by name. */
	static Listing scan(final DataSource data) {
		final List<GameCatalog.MapEntry> entries = new ArrayList<>();
		final List<Skipped> skipped = new ArrayList<>();
		final Set<String> seen = new HashSet<>();
		for (final String path : data.getListfile(MapListContainer.LISTED_MAPS)) {
			// Several data sources can list the same file, also spelled with other case or separators.
			if (!seen.add(path.replace('\\', '/').toLowerCase(Locale.ROOT))) {
				continue;
			}
			Problem problem = Problem.UNREADABLE;
			try {
				final War3Map map = new War3Map(data, path);
				problem = Problem.NO_INFORMATION;
				final War3MapW3i info = map.readMapInformation();
				final WTS wts = Warcraft3MapObjectData.loadWTS(map);
				entries.add(new GameCatalog.MapEntry(path, info.getPlayers().size(),
						GameCatalog.trigStr(wts, info.getName()), info.hasFlag(War3MapW3iFlags.MELEE_MAP),
						MapIssue.of(map)));
			}
			catch (final IOException | RuntimeException e) {
				// The engine's list leaves out a map it cannot read, too.
				LauncherLog.w("Skipping map with unreadable info: " + path, e);
				if (isOwnMap(path)) {
					skipped.add(new Skipped(path, problem));
				}
			}
		}
		Collections.sort(entries, (a, b) -> a.players == b.players ? a.name.compareTo(b.name)
				: Integer.compare(a.players, b.players));
		Collections.sort(skipped, (a, b) -> a.path.compareToIgnoreCase(b.path));
		return new Listing(entries, skipped);
	}
}
