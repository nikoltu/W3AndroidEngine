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

import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.WarsmashGdxMapScreen;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.GameLanguage;
import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.datasources.GraphicsSettings;
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
import com.etheller.warsmash.viewer5.handlers.w3x.replay.GameMarks;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.GameReplays;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.W3gReplay;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CampaignUnlock;
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
	/** The campaigns and how far the current profile got. */
	public static final class Campaigns {
		public final CampaignCatalog catalog;
		public final CCampaignProgress.Snapshot progress;
		/** The campaign cinematics whose first movie is in the game data, in lower case. */
		public final Set<String> movies;
		/** The campaign maps the game cannot play, by file name in lower case; the others are not in it. */
		private final Map<String, MapIssue> mapIssues;

		Campaigns(final CampaignCatalog catalog, final CCampaignProgress.Snapshot progress, final Set<String> movies,
				final Map<String, MapIssue> mapIssues) {
			this.catalog = catalog;
			this.progress = progress;
			this.movies = movies;
			this.mapIssues = mapIssues;
		}

		/** Whether the campaign maps the profile has chosen are the Reforged ones, whose mission lists it shows. */
		public boolean reforgedMaps(final String campaignMaps) {
			return LaunchRequest.CAMPAIGN_MAPS_REFORGED.equals(LaunchRequest.playedMaps(this.catalog, campaignMaps));
		}

		/** Why a mission cannot be played on the campaign maps the profile has chosen; NONE when it can. */
		public MapIssue issue(final CampaignCatalog.Entry entry, final int mission, final String campaignMaps) {
			final String file = this.catalog.missionMapFilename(entry, mission, reforgedMaps(campaignMaps));
			final MapIssue issue = file == null ? null : this.mapIssues.get(file.toLowerCase(Locale.ROOT));
			return issue != null ? issue : MapIssue.NONE;
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
		/** Why the engine cannot play the map; {@link MapIssue#NONE} when it can. */
		public final MapIssue issue;
		/** The name in the list; maps of the same name and players also say where they lie. */
		public String label;

		MapEntry(final String path, final int players, final String name, final boolean melee) {
			this(path, players, name, melee, MapIssue.NONE);
		}

		MapEntry(final String path, final int players, final String name, final boolean melee,
				final MapIssue issue) {
			this.path = path;
			this.players = players;
			this.name = name;
			this.melee = melee;
			this.issue = issue;
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
		public MapIssue issue = MapIssue.NONE;
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
	/** The campaign lists read so far, by the game version whose list it is; the data lists them per edition. */
	private final Map<Integer, CampaignCatalog> campaignCatalogs = new HashMap<>();
	private final Map<CampaignCatalog, Set<String>> campaignMovies = new HashMap<>();
	private volatile String edition;
	private volatile List<MapEntry> maps;
	private volatile String mapsHeader;
	private volatile List<MapListCache.Skipped> skipped = Collections.emptyList();
	private Element tilesets;
	private DataTable worldEditData;
	/** Read by the LAN threads that extract maps through {@link #virtualMaps()} too. */
	private volatile boolean closed;

	/**
	 * @param saves   the engine's Save folder
	 * @param profile the name of the engine's current profile, asked whenever the profile's files are read
	 * @param mapList where the custom game list is kept between runs for the same game data
	 * @param main    runs the answers on the thread that asked
	 */
	public GameCatalog(final DataTable ini, final String dataKey, final File saves, final Supplier<String> profile,
			final File mapList, final Consumer<Runnable> main) {
		this.ini = ini;
		// The maps the catalog opens give their titles in the language of the configuration.
		GameLanguage.useInGame(ini);
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
		run(work, done, null);
	}

	/** @param closed runs instead of done when the catalog is closed before the answer; may be null */
	private <T> void run(final Work<T> work, final Consumer<T> done, final Runnable closed) {
		if (this.closed) {
			if (closed != null) {
				closed.run();
			}
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
				else if (closed != null) {
					closed.run();
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

	/** The pictures of the game data at the given paths (without extension); a path it has none for is left out. */
	public void icons(final java.util.Collection<String> paths, final Consumer<Map<String, ArgbImage>> done) {
		final List<String> wanted = new ArrayList<>(paths);
		run(() -> {
			final Map<String, ArgbImage> found = new HashMap<>();
			final DataSource data = data();
			for (final String path : wanted) {
				try {
					final ArgbImage image = LauncherImages.decode(data, path);
					if (image != null) {
						found.put(path, image);
					}
				}
				catch (final IOException | RuntimeException e) {
					LauncherLog.w("Unable to read " + path, e);
				}
			}
			return found;
		}, done);
	}

	/**
	 * The edition the campaign list follows, {@link LauncherKeys#EDITION_ROC} or EDITION_TFT: game data that list
	 * their campaigns per edition give the Reign of Chaos or the Frozen Throne ones, as the game started in that
	 * edition does. Not told (null), or for data with one list, it is the engine's game version. Asked answers that
	 * follow the edition are to be asked again.
	 */
	public void setEdition(final String edition) {
		this.edition = edition;
	}

	/** null when the game data cannot be read */
	public void campaigns(final Consumer<Campaigns> done) {
		final String asked = this.edition;
		run(() -> {
			final CampaignCatalog catalog = campaignCatalog(asked);
			Set<String> movies = this.campaignMovies.get(catalog);
			if (movies == null) {
				movies = findMovies(data(), catalog);
				this.campaignMovies.put(catalog, movies);
			}
			// The campaigns opened by hand are laid over what the profile reached; its progress file is left as it is.
			final CCampaignProgress.Snapshot progress = readProgress(catalog.getList())
					.unlocking(catalog.numbersOf(CampaignUnlock.read(profileFolder())));
			return new Campaigns(catalog, progress, movies, findMapIssues(data(), catalog));
		}, done);
	}

	private CampaignCatalog campaignCatalog(final String edition) throws IOException {
		final DataSource data = data();
		final int version = LauncherKeys.editionGameVersion(edition, WarsmashConstants.GAME_VERSION);
		CampaignCatalog catalog = this.campaignCatalogs.get(version);
		if (catalog == null) {
			final Element skin = GameUI.loadSkin(data, WarsmashConstants.GAME_VERSION).getSkin();
			catalog = CampaignCatalog.load(data, CampaignCatalog.campaignFile(data,
					v -> GameUI.trySkinField(skin, "CampaignFile", v), version));
			this.campaignCatalogs.put(version, catalog);
		}
		return catalog;
	}

	/**
	 * What the game data lacks of the scripts, the campaign maps and the melee maps; empty for complete data, null
	 * when the data cannot be read.
	 */
	public void dataContent(final Consumer<List<DataContentCheck.Problem>> done) {
		final String asked = this.edition;
		run(() -> {
			CampaignCatalog catalog = null;
			try {
				catalog = campaignCatalog(asked);
			}
			catch (final IOException | RuntimeException e) {
				LauncherLog.w("No campaign list in the game data", e);
			}
			return DataContentCheck.check(data(), catalog);
		}, done);
	}

	/** The missions' maps that the game data lacks or keeps encrypted, for both versions of the campaign maps. */
	private static Map<String, MapIssue> findMapIssues(final DataSource data, final CampaignCatalog catalog) {
		final Map<String, MapIssue> found = new HashMap<>();
		for (final CampaignCatalog.Entry entry : catalog.getEntries()) {
			final int count = entry.getMissions(true).size();
			for (int mission = 0; mission < count; mission++) {
				for (final boolean reforged : new boolean[] { false, true }) {
					final String file = catalog.missionMapFilename(entry, mission, reforged);
					final MapIssue issue = MapIssue.ofData(data, file);
					if ((issue != MapIssue.NONE) && (file != null)) {
						found.put(file.toLowerCase(Locale.ROOT), issue);
					}
				}
			}
		}
		return found;
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
		final String asked = this.edition;
		run(() -> {
			final CampaignCatalog catalog = this.campaignCatalogs
					.get(LauncherKeys.editionGameVersion(asked, WarsmashConstants.GAME_VERSION));
			if (catalog == null) {
				return null;
			}
			final List<CCampaignProgress.Snapshot> snapshots = new ArrayList<>();
			for (final File folder : folders) {
				snapshots.add(readProgress(catalog.getList(), folder));
			}
			return snapshots;
		}, done);
	}

	/** A campaign of the list that the player can open by hand, and whether it is. */
	public static final class UnlockEntry {
		public final String section;
		public final String header;
		public final String name;
		public final boolean unlocked;

		UnlockEntry(final String section, final String header, final String name, final boolean unlocked) {
			this.section = section;
			this.header = header;
			this.name = name;
			this.unlocked = unlocked;
		}
	}

	/** Every campaign of the game data's list, with whether the current profile has opened it by hand; null when unreadable. */
	public void unlockEntries(final Consumer<List<UnlockEntry>> done) {
		final String asked = this.edition;
		run(() -> {
			final Set<String> unlocked = CampaignUnlock.read(profileFolder());
			final List<UnlockEntry> found = new ArrayList<>();
			for (final CampaignCatalog.Entry entry : campaignCatalog(asked).getEntries()) {
				found.add(new UnlockEntry(entry.getSection(), entry.getData().getHeader(), entry.getData().getName(),
						unlocked.contains(entry.getSection())));
			}
			return found;
		}, done);
	}

	/**
	 * Opens a campaign by hand for the current profile, or takes it back; the profile's progress is not changed.
	 * Answers whether the choice was kept.
	 */
	public void setCampaignUnlocked(final String section, final boolean unlocked, final Consumer<Boolean> done) {
		run(() -> {
			final File folder = profileFolder();
			final Set<String> sections = CampaignUnlock.read(folder);
			if (unlocked ? !sections.add(section) : !sections.remove(section)) {
				return true;
			}
			return CampaignUnlock.write(folder, sections);
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

	/**
	 * The maps of the custom game list, ordered as the engine orders them; kept on disk for the same game data. A map
	 * added, replaced or removed since the last answer makes the list again.
	 */
	public void maps(final Consumer<List<MapEntry>> done) {
		run(() -> {
			final String header = MapListCache.header(this.dataKey, this.ini);
			GameLanguage.useInGame(this.ini);
			if ((this.maps == null) || !header.equals(this.mapsHeader)) {
				MapListCache.Listing listing = MapListCache.read(this.mapList, header);
				if (listing == null) {
					listing = MapListCache.scan(data());
					MapListCache.write(this.mapList, header, listing);
				}
				this.mapsHeader = header;
				this.maps = listing.maps;
				this.skipped = listing.skipped;
				labelNamesakes(this.maps);
			}
			return this.maps;
		}, done);
	}

	/**
	 * Why a map about to be started cannot be played by the engine; {@link MapIssue#NONE} when it can, null when the
	 * catalog was closed before the answer.
	 */
	public void mapIssue(final String path, final Consumer<MapIssue> done) {
		run(() -> MapIssue.ofFile(data(), path), issue -> done.accept(issue == null ? MapIssue.NONE : issue),
				() -> done.accept(null));
	}

	/** A map of the list prepared for a LAN game it hosts. */
	public static final class LanMap {
		public final File file;
		public final net.warsmash.lan.protocol.LanData.MapInfo info;
		public final int playerSlots;
		public final boolean usesSync;
		/** Why the engine cannot play the map; the map is then not offered, only told. */
		public final MapIssue issue;

		LanMap(final File file, final net.warsmash.lan.protocol.LanData.MapInfo info, final int playerSlots,
				final boolean usesSync, final MapIssue issue) {
			this.file = file;
			this.info = info;
			this.playerSlots = playerSlots;
			this.usesSync = usesSync;
			this.issue = issue;
		}
	}

	/**
	 * The map of the list as the host of a LAN game announces it: its file, size and hashes, how many play, and
	 * whether its script calls natives a LAN game cannot yet run alike. Null when the map is not a file that can be
	 * shared.
	 */
	public void lanMap(final String path, final net.warsmash.lan.maps.LanMapIndex index,
			final Consumer<LanMap> done) {
		run(() -> {
			final MapIssue named = MapIssue.ofName(path);
			if (named != MapIssue.NONE) {
				return new LanMap(null, null, 0, false, named);
			}
			final File file = data().getFile(path);
			final net.warsmash.lan.protocol.LanData.MapInfo info = index.describe(file);
			final String problem = net.warsmash.lan.maps.MapRules.checkInfo(info);
			if (problem != null) {
				LauncherLog.w("The map cannot be shared in a LAN game: " + problem);
				return null;
			}
			final War3Map map = War3MapViewer.beginLoadingMap(data(), path);
			try {
				final War3MapW3i w3i = map.readMapInformation();
				final int players = w3i.getPlayers().size();
				info.title = trigStr(Warcraft3MapObjectData.loadWTS(map), w3i.getName());
				final int[] playable = w3i.getPlayableSize();
				info.playableWidth = Math.max(0, Math.min(65535, playable[0]));
				info.playableHeight = Math.max(0, Math.min(65535, playable[1]));
				boolean sync = false;
				for (final String script : new String[] { "war3map.j", "Scripts\\war3map.j", "war3map.lua" }) {
					if (map.has(script)) {
						sync = com.etheller.warsmash.launcher.lan.LanMapScan
								.usesSync(java.nio.charset.StandardCharsets.ISO_8859_1.decode(map.read(script)).toString());
						break;
					}
				}
				return new LanMap(file, info, Math.max(1, Math.min(players, 24)), sync, MapIssue.of(map));
			}
			finally {
				map.close();
			}
		}, done);
	}

	/**
	 * The maps of the game data as the LAN search can look into them: a map that is only in the game's archives is
	 * found there by its name and told from another of that name by its content. Reads happen on the catalog's thread.
	 */
	public net.warsmash.lan.maps.LanMapIndex.VirtualMaps virtualMaps() {
		return new net.warsmash.lan.maps.LanMapIndex.VirtualMaps() {
			@Override
			public List<String> pathsNamed(final String fileName) {
				final List<MapEntry> listed = GameCatalog.this.maps;
				final List<String> paths = new ArrayList<>();
				if (listed != null) {
					for (final MapEntry entry : listed) {
						if (fileName(entry.path).equalsIgnoreCase(fileName)) {
							paths.add(entry.path);
						}
					}
				}
				return paths;
			}

			@Override
			public File extract(final String path) throws IOException {
				if (GameCatalog.this.closed) {
					return null;
				}
				try {
					return GameCatalog.this.worker.submit(() -> data().getFile(path)).get();
				}
				catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException(e);
				}
				catch (final java.util.concurrent.ExecutionException | java.util.concurrent.RejectedExecutionException e) {
					throw new IOException(e);
				}
			}

			@Override
			public long stamp() {
				final String header = GameCatalog.this.mapsHeader;
				return (header == null ? GameCatalog.this.dataKey : header).hashCode();
			}
		};
	}

	/** The checksum of the game data, which the players of a LAN game compare. */
	public void dataChecksum(final Consumer<Long> done) {
		run(() -> com.etheller.warsmash.lan.LanGameStart.dataChecksum(data()), done);
	}

	/** The maps of the player's own folders left out of the list by the last answer of maps(), with the reason. */
	public List<MapListCache.Skipped> skippedMaps() {
		return this.skipped;
	}

	static String mapFingerprint(final DataTable ini) {
		return MapListCache.fingerprint(ini);
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
			return LauncherText.get("map_folder_reign");
		}
		final String normal = path.replace('\\', '/');
		final int end = normal.lastIndexOf('/');
		final String folder = normal.substring(normal.lastIndexOf('/', end - 1) + 1, Math.max(end, 0));
		if ("frozenthrone".equalsIgnoreCase(folder)) {
			return LauncherText.get("map_folder_frozen");
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
			details.issue = MapIssue.of(map);
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
			final Map<GraphicsSet, Integer> dataChecksums = new HashMap<>();
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
							final GraphicsSet graphics = GameReplays.playbackGraphics(data(), this.ini,
									header.getMapPath(), header.isCampaignGame());
							final DataSource replayData = GraphicsSettings.gameData(data(), this.ini, graphics);
							if (GameReplays.isThisEngine(header) && !dataChecksums.containsKey(graphics)) {
								dataChecksums.put(graphics, GameReplays.dataChecksum(replayData));
							}
							entry.unplayable = GameReplays.unplayableReason(header, replayData,
									dataChecksums.get(graphics));
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

	/**
	 * Completes a problem report the platform began (its facts and logs) with the replay of the latest game, its
	 * marked moments, the map and the newest save, and writes it into the folder. Answers null when it could not be
	 * written.
	 *
	 * @param request its replay is null for the latest game
	 */
	public void problemReport(final ProblemReport.Request request, final File folder,
			final java.time.ZoneId zone, final Consumer<ProblemReport.Result> done) {
		run(() -> {
			if (request.replay == null) {
				request.replay = latestReplay();
			}
			request.facts.put("Language", GameLanguage.effective(this.ini));
			if (request.replay != null) {
				final String stem = request.replay.getName().substring(0, request.replay.getName().lastIndexOf('.'));
				request.marks = new File(request.replay.getParentFile(), stem + GameMarks.EXTENSION);
				describeReplay(request);
			}
			final File unrecorded = new File(replayFolder(), GameMarks.UNRECORDED + GameMarks.EXTENSION);
			if (unrecorded.isFile()
					&& ((request.replay == null) || (unrecorded.lastModified() > request.replay.lastModified()))) {
				request.unrecordedMarks = unrecorded;
			}
			request.save = newestSave(profileFolder(), 4);
			return ProblemReport.write(request, folder, zone);
		}, done);
	}

	/** The replay of the latest game: LastReplay, or the replay of a game cut off since (a crash). */
	private File latestReplay() {
		final File last = new File(replayFolder(), GameReplays.LAST_REPLAY + GameReplays.EXTENSION);
		final File cutOff = new File(replayFolder(), GameReplays.TEMP_REPLAY + GameReplays.EXTENSION);
		if (cutOff.isFile() && (!last.isFile() || (cutOff.lastModified() > last.lastModified()))) {
			return cutOff;
		}
		return last.isFile() ? last : null;
	}

	private void describeReplay(final ProblemReport.Request request) {
		final Map<String, String> facts = request.facts;
		facts.put("Replay", request.replay.getName());
		final CReplay replay;
		try (InputStream in = new BufferedInputStream(new FileInputStream(request.replay))) {
			replay = CReplay.read(in);
		}
		catch (final IOException | RuntimeException e) {
			facts.put("Replay state", "cannot be read: " + e.getMessage());
			return;
		}
		final CReplayHeader header = replay.getHeader();
		facts.put("Replay engine", header.getEngineVersion() + ", commit " + header.getBuildCommit()
				+ (header.isOfficialBuild() ? ", official build" : ""));
		facts.put("Replay state", (replay.isComplete() ? "complete" : "cut off") + ", "
				+ (GameReplays.isThisEngine(header) ? "recorded by this build" : "recorded by another build"));
		final String mapPath = header.getMapPath();
		final String mapName = mapPath.replace('\\', '/').substring(mapPath.replace('\\', '/').lastIndexOf('/') + 1);
		facts.put("Map", mapName);
		facts.put("Map path", mapPath);
		facts.put("Map hash", String.format(Locale.ROOT, "CRC32 %08x, %d bytes", header.getMapChecksum(),
				header.getMapSize()));
		facts.put("Game data hash", String.format(Locale.ROOT, "%08x", header.getDataChecksum()));
		facts.put("Campaign game", header.isCampaignGame() ? "yes" : "no");
		final int seconds = (int) (replay.getLastTick() * WarsmashConstants.SIMULATION_STEP_TIME);
		facts.put("Game length", String.format(Locale.ROOT, "%d:%02d:%02d (%d steps)", seconds / 3600,
				(seconds / 60) % 60, seconds % 60, replay.getLastTick()));
		request.map = mapFile(header.getMapPath());
	}

	/** The map as a file of its own (the player's own map), or null for a map of the game data. */
	private File mapFile(final String mapPath) {
		final File direct = new File(mapPath);
		if (direct.isAbsolute() && direct.isFile()) {
			return direct;
		}
		final File custom = CustomContent.folder(this.ini);
		if (custom != null) {
			final File inCustom = new File(custom, mapPath.replace('\\', File.separatorChar));
			if (inCustom.isFile()) {
				return inCustom;
			}
		}
		return null;
	}

	/** The newest saved game in the profile's folder or its folders of saves (zone maps keep some there). */
	private static File newestSave(final File folder, final int depth) {
		final File[] files = folder.listFiles();
		File newest = null;
		if (files == null) {
			return null;
		}
		for (final File file : files) {
			File found = null;
			if (file.isDirectory()) {
				if (depth > 0) {
					found = newestSave(file, depth - 1);
				}
			}
			else if (file.getName().toLowerCase(Locale.ROOT).endsWith(CSaveStorage.SAVE_EXTENSION)) {
				found = file;
			}
			if ((found != null) && ((newest == null) || (found.lastModified() > newest.lastModified()))) {
				newest = found;
			}
		}
		return newest;
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
