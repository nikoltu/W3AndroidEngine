package com.etheller.warsmash.launcher;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.CRC32;

import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.WarsmashGdxMapScreen;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.movie.Movie;
import com.etheller.warsmash.movie.Movies;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapObjectData;
import com.etheller.warsmash.parsers.w3x.w3i.Force;
import com.etheller.warsmash.parsers.w3x.w3i.Player;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3iFlags;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.StandardObjectData;
import com.etheller.warsmash.units.custom.WTS;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.GameReplays;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.W3gReplay;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplay;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplayHeader;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.MapListContainer;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/**
 * What the launcher's game pages list, read from the game data and the profile's files the way the engine reads them:
 * the campaigns and the profile's progress, the custom game maps and the saved games. The game data is read on one
 * worker thread, the answers come back on the thread the platform's main poster runs them on.
 */
public class GameCatalog {
	// 2: test maps are no longer listed, so lists kept by format 1 are made again.
	// 3: the header also holds a fingerprint of the map files, so added, removed and changed maps are noticed.
	private static final int MAP_LIST_FORMAT = 3;
	private static final int MAP_FOLDER_DEPTH = 8;

	/** The campaigns and how far the current profile got. */
	public static final class Campaigns {
		public final CampaignCatalog catalog;
		public final CCampaignProgress.Snapshot progress;
		/** The campaign cinematics whose first movie is in the game data, in lower case. */
		public final Set<String> movies;

		Campaigns(final CampaignCatalog catalog, final CCampaignProgress.Snapshot progress, final Set<String> movies) {
			this.catalog = catalog;
			this.progress = progress;
			this.movies = movies;
		}

		public boolean hasMovie(final CampaignMission cinematic) {
			return (cinematic != null) && !cinematic.getMovies().isEmpty()
					&& this.movies.contains(cinematic.getMovies().get(0).toLowerCase(Locale.ROOT));
		}
	}

	/** A map of the custom game list, as the engine's list shows it. */
	public static final class MapEntry {
		public final String path;
		public final int players;
		public final String name;
		public final boolean melee;
		/** The name in the list; maps of the same name and players also say where they lie. */
		public String label;

		MapEntry(final String path, final int players, final String name, final boolean melee) {
			this.path = path;
			this.players = players;
			this.name = name;
			this.melee = melee;
			this.label = name;
		}
	}

	/** A player slot of a map, as its information file sets it up. */
	public static final class Slot {
		public final int id;
		/** 1 user, 2 computer, 3 neutral, 4 rescuable */
		public final int type;
		/** 0 selectable, 1 human, 2 orc, 3 undead, 4 night elf */
		public final int race;
		public final String name;
		public int force = -1;

		Slot(final int id, final int type, final int race, final String name) {
			this.id = id;
			this.type = type;
			this.race = race;
			this.name = name;
		}
	}

	public static final class MapDetails {
		public final String path;
		public String name = "";
		public String description = "";
		public String suggested = "";
		public String size = "";
		public String tileset = "";
		public boolean melee;
		public boolean fixedSettings;
		public boolean customForces;
		public final List<Slot> slots = new ArrayList<>();
		public final List<String> forces = new ArrayList<>();
		public ArgbImage minimap;

		MapDetails(final String path) {
			this.path = path;
		}
	}

	public static final class SaveEntry {
		public final String name;
		public final File file;
		public final long saved;
		public SavedGame.Loadability loadability = SavedGame.Loadability.DAMAGED;
		public String mapFile;
		public float gameTime;
		/** The campaign the game is a mission of, by the number scripts know it by; -1 for none or not told. */
		public int campaign = -1;
		/** Whether the save says its campaign (-1 then is a game of no campaign); older saves do not. */
		public boolean campaignKept;
		public boolean reforgedCampaignMaps;
		/** easy, normal, hard or insane; null when not told. */
		public String difficulty;

		SaveEntry(final String name, final File file) {
			this.name = name;
			this.file = file;
			this.saved = file.lastModified();
		}
	}

	/** A player of a replay as its setup left the slot; observers are left out. */
	public static final class ReplayPlayer {
		/** Null for a computer player, which the lists name by its slot. */
		public final String name;
		public final int slot;
		public final int color;
		/** As JASS race preferences: 1 human, 2 orc, 4 night elf, 8 undead, 32 random. */
		public final int race;

		ReplayPlayer(final String name, final int slot, final int color, final int race) {
			this.name = name;
			this.slot = slot;
			this.color = color;
			this.race = race;
		}

		/** The race as the lobby counts races ({@link CustomGame#RACES}): 0 for random or a choice of several. */
		public int raceIndex() {
			switch (this.race & 0x0F) {
			case 1:
				return 1;
			case 2:
				return 2;
			case 8:
				return 3;
			case 4:
				return 4;
			default:
				return 0;
			}
		}
	}

	public static final class ReplayEntry {
		/** The file's name without its extension. */
		public final String name;
		public final File file;
		public final long modified;
		/** A Warcraft III replay (.w3g), which this engine lists but cannot play. */
		public final boolean warcraft;
		public boolean lastReplay;
		public boolean damaged;
		public String mapFile;
		public float length;
		public String version;
		/** When the game was played; the file's time when the replay does not tell. */
		public long recorded;
		public final List<ReplayPlayer> players = new ArrayList<>();
		/** Why this engine cannot play it (another version, another map or game data), or null. */
		public String unplayable;
		/** Recorded by another version of the engine. */
		public boolean otherVersion;
		/** Whether the map is a melee map, or null when the map cannot be read here. */
		public Boolean melee;

		ReplayEntry(final File file, final boolean warcraft) {
			final String fileName = file.getName();
			this.name = fileName.substring(0, fileName.lastIndexOf('.'));
			this.file = file;
			this.modified = file.lastModified();
			this.recorded = this.modified;
			this.warcraft = warcraft;
		}

		public boolean playable() {
			return !this.damaged && (this.unplayable == null);
		}

		/**
		 * An incompatible replay of this engine's own kind (another version, map or game data) can be tried anyway,
		 * with the recorded checksums ignored; a Warcraft III replay cannot.
		 */
		public boolean canTryAnyway() {
			return !this.damaged && !this.warcraft && (this.unplayable != null);
		}

		/** The map's file name without its folders and extension, or "" when the replay does not tell. */
		public String mapName() {
			if (this.mapFile == null) {
				return "";
			}
			final String path = this.mapFile.replace('\\', '/');
			final String file = path.substring(path.lastIndexOf('/') + 1);
			final int dot = file.lastIndexOf('.');
			return dot > 0 ? file.substring(0, dot) : file;
		}
	}

	private final DataTable ini;
	private final String dataKey;
	private final File saves;
	private final Supplier<String> profile;
	private final File mapList;
	private final Consumer<Runnable> main;
	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "LauncherCatalog");
		thread.setDaemon(true);
		return thread;
	});
	private DataSource data;
	private CampaignCatalog campaignCatalog;
	private Set<String> campaignMovies;
	private List<MapEntry> maps;
	private Element tilesets;
	private DataTable worldEditData;
	private boolean closed;

	/**
	 * @param saves   the engine's Save folder
	 * @param profile the name of the engine's current profile, asked whenever the profile's files are read
	 * @param mapList where the custom game list is kept between runs for the same game data
	 * @param main    runs the answers on the thread that asked
	 */
	public GameCatalog(final DataTable ini, final String dataKey, final File saves, final Supplier<String> profile,
			final File mapList, final Consumer<Runnable> main) {
		this.ini = ini;
		this.dataKey = dataKey;
		this.saves = saves;
		this.profile = profile;
		this.mapList = mapList;
		this.main = main;
	}

	/**
	 * A catalog reading the game data the caller already opened, rather than opening it a second time; the data is
	 * closed with the catalog, after its last read.
	 */
	public GameCatalog(final DataTable ini, final String dataKey, final File saves, final Supplier<String> profile,
			final File mapList, final Consumer<Runnable> main, final DataSource data) {
		this(ini, dataKey, saves, profile, mapList, main);
		this.data = data;
	}

	/** What the kept map list was read from: the data sources of the engine configuration. */
	public static String dataKey(final DataTable ini) {
		final Element sources = ini.get("DataSources");
		final StringBuilder key = new StringBuilder();
		for (int i = 0; (sources != null) && (i < sources.size()); i++) {
			final String id = String.format(Locale.ROOT, "%02d", i);
			key.append('|').append(sources.getField("Type" + id)).append(':').append(sources.getField("Path" + id));
		}
		return key.toString();
	}

	/** The engine's current player profile in its preferences, as its folder below Save is named. */
	public static String currentProfile(final LauncherStore engine) {
		final String name = engine.getString("CurrentProfile", Profiles.ENGINE_DEFAULT);
		final String safe = CSaveStorage.safeName(name);
		return safe.isEmpty() ? "Player" : safe;
	}

	/** Save/&lt;profile&gt;, where the engine keeps the current profile's saves and progress. */
	private File profileFolder() {
		return Profiles.folder(this.saves, this.profile.get());
	}

	public void close() {
		this.closed = true;
		this.worker.execute(() -> {
			if (this.data != null) {
				try {
					this.data.close();
				}
				catch (final IOException e) {
					LauncherLog.w("Unable to close the game data", e);
				}
				this.data = null;
			}
		});
		this.worker.shutdown();
	}

	private <T> void run(final Work<T> work, final Consumer<T> done) {
		if (this.closed) {
			return;
		}
		this.worker.execute(() -> {
			T value;
			try {
				value = work.run();
			}
			catch (final Exception | LinkageError e) {
				LauncherLog.e("Launcher catalog failed", e);
				value = null;
			}
			final T answer = value;
			this.main.accept(() -> {
				if (!this.closed) {
					done.accept(answer);
				}
			});
		});
	}

	private interface Work<T> {
		T run() throws Exception;
	}

	private DataSource data() {
		if (this.data == null) {
			WarsmashConstants.loadDataConstants(this.ini.get("Emulator"), this.ini);
			this.data = WarsmashGdxMapScreen.parseDataSources(this.ini);
		}
		return this.data;
	}

	/** The melee races' command cards for the hotkey editor; null when the game data cannot be read. */
	public void hotkeys(final Map<String, String> words, final Consumer<HotkeyCatalog> done) {
		run(() -> HotkeyCatalog.read(data(), words), done);
	}

	/** null when the game data cannot be read */
	public void campaigns(final Consumer<Campaigns> done) {
		run(() -> {
			if (this.campaignCatalog == null) {
				final DataSource data = data();
				final String file = GameUI.trySkinField(
						GameUI.loadSkin(data, WarsmashConstants.GAME_VERSION).getSkin(), "CampaignFile");
				this.campaignCatalog = CampaignCatalog.load(data, file);
			}
			if (this.campaignMovies == null) {
				this.campaignMovies = findMovies(data(), this.campaignCatalog);
			}
			return new Campaigns(this.campaignCatalog, readProgress(this.campaignCatalog.getList()),
					this.campaignMovies);
		}, done);
	}

	private static Set<String> findMovies(final DataSource data, final CampaignCatalog catalog) {
		final Set<String> found = new HashSet<>();
		for (final CampaignCatalog.Entry entry : catalog.getEntries()) {
			for (final CampaignMission cinematic : Arrays.asList(entry.getData().getIntroCinematic(),
					entry.getData().getOpenCinematic(), entry.getData().getEndCinematic())) {
				if ((cinematic != null) && !cinematic.getMovies().isEmpty()) {
					final String name = cinematic.getMovies().get(0);
					if (Movies.find(data, name, 0) != null) {
						found.add(name.toLowerCase(Locale.ROOT));
					}
				}
			}
		}
		return found;
	}

	/** The movies of these names that are in the game data, for a screen this wide; null when unreadable. */
	public void movies(final List<String> names, final int screenWidth, final Consumer<List<Movie>> done) {
		run(() -> Movies.find(data(), names, screenWidth), done);
	}

	/** The campaigns and the progress of each profile folder given, in their order; null when unreadable. */
	public void progress(final List<File> folders, final Consumer<List<CCampaignProgress.Snapshot>> done) {
		run(() -> {
			if (this.campaignCatalog == null) {
				return null;
			}
			final List<CCampaignProgress.Snapshot> snapshots = new ArrayList<>();
			for (final File folder : folders) {
				snapshots.add(readProgress(this.campaignCatalog.getList(), folder));
			}
			return snapshots;
		}, done);
	}

	private CCampaignProgress.Snapshot readProgress(final CCampaignProgress.CampaignList list) {
		return readProgress(list, profileFolder());
	}

	private CCampaignProgress.Snapshot readProgress(final CCampaignProgress.CampaignList list, final File folder) {
		final File file = new File(folder, "Campaigns.w3v");
		if (!file.isFile()) {
			return CCampaignProgress.emptySnapshot();
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
			return CCampaignProgress.readSnapshot(in, list);
		}
		catch (final IOException e) {
			LauncherLog.w("Unable to read the campaign progress", e);
			return CCampaignProgress.emptySnapshot();
		}
	}

	/** The maps of the custom game list, ordered as the engine orders them; kept on disk for the same game data. */
	public void maps(final Consumer<List<MapEntry>> done) {
		run(() -> {
			if (this.maps == null) {
				final String header = mapListHeader();
				this.maps = readMapList(header);
				if (this.maps == null) {
					this.maps = scanMaps();
					writeMapList(header, this.maps);
				}
				labelNamesakes(this.maps);
			}
			return this.maps;
		}, done);
	}

	private String mapListHeader() {
		return MAP_LIST_FORMAT + "|" + this.dataKey + "|" + mapFingerprint(this.ini);
	}

	/**
	 * Tells when the maps of the game data may have changed without opening the data: the map files of the folder
	 * sources and the files the other sources are kept in, by name, size and time, and the folders that may hold maps
	 * by time.
	 */
	static String mapFingerprint(final DataTable ini) {
		final Element sources = ini.get("DataSources");
		final StringBuilder facts = new StringBuilder();
		for (int i = 0; (sources != null) && (i < sources.size()); i++) {
			final String id = String.format(Locale.ROOT, "%02d", i);
			final String path = sources.getField("Path" + id);
			if ((path == null) || path.isEmpty()) {
				continue;
			}
			final File root = new File(path);
			facts.append('#').append(i).append('\n');
			if ("Folder".equals(sources.getField("Type" + id))) {
				addMapFacts(facts, root, "", 0, false);
			}
			else {
				addFileFacts(facts, root, "");
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
		addFileFacts(facts, folder, name + "/");
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

	private List<MapEntry> readMapList(final String header) {
		final File file = this.mapList;
		if (!file.isFile()) {
			return null;
		}
		try {
			final List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
			if (lines.isEmpty() || !lines.get(0).equals(header)) {
				return null;
			}
			final List<MapEntry> entries = new ArrayList<>();
			for (int i = 1; i < lines.size(); i++) {
				final String[] parts = lines.get(i).split("\t", -1);
				if (parts.length == 4) {
					entries.add(new MapEntry(parts[0], Integer.parseInt(parts[1]), parts[2], "1".equals(parts[3])));
				}
			}
			return entries;
		}
		catch (final IOException | RuntimeException e) {
			return null;
		}
	}

	private void writeMapList(final String header, final List<MapEntry> entries) {
		final StringBuilder text = new StringBuilder().append(header).append('\n');
		for (final MapEntry entry : entries) {
			text.append(entry.path).append('\t').append(entry.players).append('\t')
					.append(entry.name.replace('\t', ' ').replace('\n', ' ')).append('\t')
					.append(entry.melee ? '1' : '0').append('\n');
		}
		try {
			final File file = this.mapList;
			file.getParentFile().mkdirs();
			Files.write(file.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
		}
		catch (final IOException e) {
			LauncherLog.w("Unable to keep the map list", e);
		}
	}

	private List<MapEntry> scanMaps() {
		final DataSource data = data();
		final List<MapEntry> entries = new ArrayList<>();
		final Set<String> seen = new HashSet<>();
		for (final String path : data.getListfile()) {
			// Several data sources can list the same file, also spelled with other case or separators.
			if (!MapListContainer.isListedMap(path)
					|| !seen.add(path.replace('\\', '/').toLowerCase(Locale.ROOT))) {
				continue;
			}
			try {
				final War3Map map = new War3Map(data, path);
				final War3MapW3i info = map.readMapInformation();
				final WTS wts = Warcraft3MapObjectData.loadWTS(map);
				entries.add(new MapEntry(path, info.getPlayers().size(), trigStr(wts, info.getName()),
						info.hasFlag(War3MapW3iFlags.MELEE_MAP)));
			}
			catch (final IOException | RuntimeException e) {
				// The engine's list leaves out a map it cannot read, too.
				LauncherLog.w("Skipping map with unreadable info: " + path, e);
			}
		}
		// As the engine's list sorts them: by players, then by name.
		Collections.sort(entries, (a, b) -> a.players == b.players ? a.name.compareTo(b.name)
				: Integer.compare(a.players, b.players));
		return entries;
	}

	/**
	 * Ladder seasons and the Reign of Chaos copies keep the names of the maps they change, so maps of the same name
	 * and players are told apart by their folder, or by their file when the folders are the same, too.
	 */
	private static void labelNamesakes(final List<MapEntry> entries) {
		final Map<String, List<MapEntry>> byName = new HashMap<>();
		for (final MapEntry entry : entries) {
			entry.label = entry.name;
			byName.computeIfAbsent(entry.players + "|" + entry.name, key -> new ArrayList<>()).add(entry);
		}
		for (final List<MapEntry> namesakes : byName.values()) {
			if (namesakes.size() < 2) {
				continue;
			}
			final Set<String> folders = new HashSet<>();
			for (final MapEntry entry : namesakes) {
				folders.add(folderLabel(entry.path));
			}
			final boolean byFolder = folders.size() == namesakes.size();
			for (final MapEntry entry : namesakes) {
				entry.label = entry.name + " (" + (byFolder ? folderLabel(entry.path) : fileName(entry.path)) + ")";
			}
		}
	}

	private static String fileName(final String path) {
		final String normal = path.replace('\\', '/');
		return normal.substring(normal.lastIndexOf('/') + 1);
	}

	/** The map's folder as words: frozenthrone is Frozen Throne, 2023season2 is 2023 Season 2. */
	private static String folderLabel(final String path) {
		if (path.toLowerCase(Locale.ROOT).endsWith(".w3m")) {
			return "Reign of Chaos";
		}
		final String normal = path.replace('\\', '/');
		final int end = normal.lastIndexOf('/');
		final String folder = normal.substring(normal.lastIndexOf('/', end - 1) + 1, Math.max(end, 0));
		if ("frozenthrone".equalsIgnoreCase(folder)) {
			return "Frozen Throne";
		}
		final StringBuilder words = new StringBuilder();
		for (int i = 0; i < folder.length(); i++) {
			final char c = folder.charAt(i);
			if ((i > 0) && (Character.isDigit(c) != Character.isDigit(folder.charAt(i - 1)))) {
				words.append(' ');
			}
			words.append((i == 0) || (words.charAt(words.length() - 1) == ' ') ? Character.toUpperCase(c) : c);
		}
		return words.toString();
	}

	public static String trigStr(final WTS wts, final String value) {
		if ((value != null) && value.startsWith("TRIGSTR_")) {
			try {
				final String found = wts.get(Integer.parseInt(value.substring(8).trim()));
				return found != null ? found : value;
			}
			catch (final NumberFormatException e) {
				return value;
			}
		}
		return value != null ? value : "";
	}

	/** A map's information pane and player slots; null when the map cannot be read. */
	public void mapDetails(final String path, final Consumer<MapDetails> done) {
		run(() -> {
			final DataSource data = data();
			// A replay's map can be one outside the game data, named by its file.
			final War3Map map = War3MapViewer.beginLoadingMap(data, path);
			final War3MapW3i info = map.readMapInformation();
			final WTS wts = Warcraft3MapObjectData.loadWTS(map);
			final MapDetails details = new MapDetails(path);
			details.name = trigStr(wts, info.getName());
			details.description = trigStr(wts, info.getDescription());
			details.suggested = trigStr(wts, info.getRecommendedPlayers());
			final int[] size = info.getPlayableSize();
			details.size = size[0] + " x " + size[1];
			details.tileset = tilesetName(info.getTileset());
			details.melee = info.hasFlag(War3MapW3iFlags.MELEE_MAP);
			details.fixedSettings = info.hasFlag(War3MapW3iFlags.FIXED_PLAYER_SETTINGS_FOR_CUSTOM_FORCES);
			details.customForces = info.hasFlag(War3MapW3iFlags.USE_CUSTOM_FORCES);
			for (final Force force : info.getForces()) {
				details.forces.add(trigStr(wts, force.getName()));
			}
			for (final Player player : info.getPlayers()) {
				final Slot slot = new Slot(player.getId(), player.getType(), player.getRace(),
						trigStr(wts, player.getName()));
				for (int f = 0; f < info.getForces().size(); f++) {
					if ((info.getForces().get(f).getPlayerMasks() & (1L << player.getId())) != 0) {
						slot.force = f;
						break;
					}
				}
				details.slots.add(slot);
			}
			Collections.sort(details.slots, (a, b) -> Integer.compare(a.id, b.id));
			if (!info.hasFlag(War3MapW3iFlags.HIDE_MINIMAP_IN_PREVIEW_SCREENS)) {
				details.minimap = LauncherImages.minimap(map);
			}
			return details;
		}, done);
	}

	private String tilesetName(final char tileset) {
		try {
			if (this.tilesets == null) {
				this.worldEditData = new StandardObjectData(data()).getWorldEditData();
				this.tilesets = this.worldEditData.get("TileSets");
			}
			final String key = this.tilesets.getField(Character.toString(tileset), 0);
			final String name = this.worldEditData.getLocalizedString(key);
			return name != null ? name : key;
		}
		catch (final RuntimeException e) {
			return "?";
		}
	}

	/**
	 * The profile's saved games, newest first, each with whether this engine can load it; games of test maps are left
	 * out.
	 */
	public void saves(final Consumer<List<SaveEntry>> done) {
		run(() -> {
			final List<SaveEntry> saves = new ArrayList<>();
			final File[] files = profileFolder().listFiles();
			if (files != null) {
				for (final File file : files) {
					final String name = file.getName();
					if (!file.isFile() || !name.toLowerCase(Locale.ROOT).endsWith(CSaveStorage.SAVE_EXTENSION)) {
						continue;
					}
					final SaveEntry entry = new SaveEntry(
							name.substring(0, name.length() - CSaveStorage.SAVE_EXTENSION.length()), file);
					final FileHandle handle = new FileHandle(file);
					try {
						entry.loadability = SavedGame.loadability(handle);
						final SavedGame.Header header = SavedGame.readHeader(handle);
						entry.mapFile = header.mapFile;
						entry.gameTime = header.gameTime;
						entry.campaign = header.campaign;
						entry.campaignKept = header.formatVersion >= SavedGame.CAMPAIGN_FORMAT_VERSION;
						entry.reforgedCampaignMaps = header.reforgedCampaignMaps;
						entry.difficulty = header.difficulty == null ? null
								: header.difficulty.name().toLowerCase(Locale.ROOT);
						if ((entry.mapFile != null) && MapListContainer.isTestMap(entry.mapFile)) {
							continue;
						}
					}
					catch (final IOException | RuntimeException | LinkageError e) {
						entry.loadability = entry.loadability == SavedGame.Loadability.LOADABLE
								? SavedGame.Loadability.DAMAGED
								: entry.loadability;
					}
					saves.add(entry);
				}
			}
			Collections.sort(saves, (a, b) -> Long.compare(b.saved, a.saved));
			return saves;
		}, done);
	}

	/** Replay/, where the engine keeps the replays of every profile (as the original does). */
	public File replayFolder() {
		return new File(this.saves.getAbsoluteFile().getParentFile(), GameReplays.FOLDER);
	}

	/**
	 * The replays, LastReplay first and then the newest first, each with its map, players, length and whether this
	 * engine plays it. Warcraft III replays put in the folder are listed too, as replays of another game.
	 */
	public void replays(final Consumer<List<ReplayEntry>> done) {
		run(() -> {
			final List<ReplayEntry> replays = new ArrayList<>();
			final File[] files = replayFolder().listFiles();
			Integer dataChecksum = null;
			final Map<String, Boolean> melee = new HashMap<>();
			if (files != null) {
				for (final File file : files) {
					final String lower = file.getName().toLowerCase(Locale.ROOT);
					final boolean ours = lower.endsWith(GameReplays.EXTENSION);
					if (!file.isFile() || !(ours || lower.endsWith(W3gReplay.EXTENSION))) {
						continue;
					}
					final ReplayEntry entry = new ReplayEntry(file, !ours);
					entry.lastReplay = entry.name.equalsIgnoreCase(GameReplays.LAST_REPLAY);
					try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
						if (ours) {
							final CReplay replay = CReplay.read(in);
							final CReplayHeader header = replay.getHeader();
							describe(entry, header);
							entry.length = replay.getLastTick() * WarsmashConstants.SIMULATION_STEP_TIME;
							if (GameReplays.isThisEngine(header) && (dataChecksum == null)) {
								dataChecksum = GameReplays.dataChecksum(data());
							}
							entry.unplayable = GameReplays.unplayableReason(header, data(), dataChecksum);
							if (!melee.containsKey(entry.mapFile)) {
								melee.put(entry.mapFile, isMelee(entry.mapFile));
							}
							entry.melee = melee.get(entry.mapFile);
						}
						else {
							describe(entry, W3gReplay.read(in));
						}
					}
					catch (final IOException | RuntimeException e) {
						entry.damaged = true;
					}
					replays.add(entry);
				}
			}
			Collections.sort(replays, (a, b) -> a.lastReplay != b.lastReplay ? (a.lastReplay ? -1 : 1)
					: Long.compare(b.modified, a.modified));
			return replays;
		}, done);
	}

	private Boolean isMelee(final String path) {
		try {
			final War3Map map = War3MapViewer.beginLoadingMap(data(), path);
			return map.readMapInformation().hasFlag(War3MapW3iFlags.MELEE_MAP);
		}
		catch (final IOException | RuntimeException e) {
			return null;
		}
	}

	private static void describe(final ReplayEntry entry, final CReplayHeader header) {
		entry.mapFile = header.getMapPath();
		entry.version = header.getEngineVersion();
		entry.recorded = header.getRecordedAt();
		entry.otherVersion = !GameReplays.isThisEngine(header);
		for (final CReplayHeader.Slot slot : header.getSlots()) {
			final boolean person = slot.getController() == CMapControl.USER;
			if ((slot.getSlotState() != CPlayerSlotState.PLAYING)
					|| (!person && (slot.getController() != CMapControl.COMPUTER))) {
				continue;
			}
			entry.players.add(new ReplayPlayer(person ? slot.getName() : null, slot.getId(), slot.getColor(),
					slot.getRacePreferences()));
		}
	}

	private static void describe(final ReplayEntry entry, final W3gReplay replay) {
		entry.mapFile = replay.mapPath;
		entry.version = replay.version;
		entry.length = replay.lengthMillis / 1000f;
		entry.unplayable = "a Warcraft III replay";
		entry.otherVersion = true;
		for (final W3gReplay.Player player : replay.players) {
			if (!player.observer) {
				entry.players.add(new ReplayPlayer(player.name, player.color, player.color, player.race));
			}
		}
	}

	/** The file the replay would be under with this name, to ask before another one is written over. */
	public static File replayFile(final ReplayEntry entry, final String name) {
		final String fileName = entry.file.getName();
		return new File(entry.file.getParentFile(),
				CSaveStorage.safeName(name) + fileName.substring(fileName.lastIndexOf('.')));
	}

	/**
	 * Gives the replay a new name in the same folder. LastReplay is copied instead, so that the next game writes it
	 * again (the original's Save Replay).
	 *
	 * @return the replay's new file, or null when the name is empty or the file cannot be written
	 */
	public static File renameReplay(final ReplayEntry entry, final String name) {
		if (CSaveStorage.safeName(name).isEmpty()) {
			return null;
		}
		final File target = replayFile(entry, name);
		if (target.equals(entry.file)) {
			return target;
		}
		try {
			if (entry.lastReplay) {
				Files.copy(entry.file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			else {
				Files.move(entry.file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			return target;
		}
		catch (final IOException e) {
			LauncherLog.w("Unable to rename " + entry.file, e);
			return null;
		}
	}
}
