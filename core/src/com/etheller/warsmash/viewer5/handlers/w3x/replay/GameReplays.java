package com.etheller.warsmash.viewer5.handlers.w3x.replay;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.CRC32;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.BuildInfo;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.launcher.MapFiles;
import com.etheller.warsmash.launcher.StorageRoot;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.CampaignMaps;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ChecksumSteps;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.SimulationChecksum;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCache;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplay;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplayException;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplayHeader;

/**
 * Where replays are kept and which games are recorded. As measured on 1.29: every custom game and every game of a
 * map started directly is recorded to Replay/LastReplay, written while the game goes as TempReplay and renamed when
 * it ends; campaign missions and loaded saves are not recorded.
 */
public final class GameReplays {
	public static final String FOLDER = "Replay";
	public static final String EXTENSION = ".wsr";
	public static final String LAST_REPLAY = "LastReplay";
	/** The replay of the game going on, or of one that was cut off (a crash). */
	public static final String TEMP_REPLAY = "TempReplay";
	/**
	 * -Dwarsmash.replay.record=&lt;file&gt;: every game that is not a loaded save is recorded straight into that file,
	 * campaign missions too; a test reads it even when its process was ended in the middle of the game.
	 */
	public static final String RECORD_PROPERTY = "warsmash.replay.record";
	/** -Dwarsmash.replay.report=&lt;file&gt;: a playback writes its result line there when it ends. */
	public static final String REPORT_PROPERTY = "warsmash.replay.report";
	/**
	 * -Dwarsmash.replay.skipto=&lt;step&gt;: a playback plays on to that step without drawing, then as usual, and logs
	 * how fast it got there.
	 */
	public static final String SKIP_TO_PROPERTY = "warsmash.replay.skipto";
	/**
	 * -Dwarsmash.replay.force=true: a replay is played although this engine would refuse it (another version, map or
	 * game data), as the player's "try anyway" does.
	 */
	public static final String FORCE_PROPERTY = "warsmash.replay.force";
	/**
	 * -Dwarsmash.replay.look=&lt;x&gt;,&lt;y&gt;: once a playback reached its skipto step, the camera looks there (a
	 * problem report's marked moment).
	 */
	public static final String LOOK_PROPERTY = "warsmash.replay.look";
	/** -Dwarsmash.replay.select=&lt;handle&gt;,...: the units selected once the skipto step is reached. */
	public static final String SELECT_PROPERTY = "warsmash.replay.select";
	/** -Dwarsmash.replay.pause=true: the playback pauses once the skipto step is reached. */
	public static final String PAUSE_PROPERTY = "warsmash.replay.pause";
	/** -Dwarsmash.replay.exit=true: the program ends with the playback. */
	public static final String EXIT_PROPERTY = "warsmash.replay.exit";
	/** -Dwarsmash.version=&lt;version&gt;: stands for the engine's own version; a replay of another one is refused. */
	public static final String VERSION_PROPERTY = "warsmash.version";
	/**
	 * The game data files the game's rules come from, read from the data without graphics packages (the simulation
	 * reads those alone). Text that only names and describes things (the *Strings files) is left out, so a replay plays
	 * in every language. The sound tables are in because the length of a sound is something scripts wait by.
	 */
	private static final String[] DATA_FILES = { "Scripts\\common.j", "Scripts\\Blizzard.j", "Scripts\\common.ai",
			"Units\\UnitAbilities.slk", "Units\\UnitBalance.slk", "Units\\UnitData.slk", "Units\\UnitUI.slk",
			"Units\\UnitWeapons.slk", "Units\\ItemData.slk", "Units\\DestructableData.slk", "Doodads\\Doodads.slk",
			"Units\\AbilityData.slk", "Units\\AbilityBuffData.slk", "Units\\UpgradeData.slk",
			"Units\\CampaignUnitFunc.txt", "Units\\HumanUnitFunc.txt", "Units\\NeutralUnitFunc.txt",
			"Units\\NightElfUnitFunc.txt", "Units\\OrcUnitFunc.txt", "Units\\UndeadUnitFunc.txt",
			"Units\\UnitWeaponsFunc.txt", "Units\\UnitSkin.txt", "Units\\UnitWeaponsSkin.txt", "Units\\ItemFunc.txt",
			"Units\\ItemSkin.txt", "Units\\DestructableSkin.txt", "Doodads\\DoodadSkins.txt",
			"Units\\CampaignAbilityFunc.txt", "Units\\CommonAbilityFunc.txt", "Units\\HumanAbilityFunc.txt",
			"Units\\NeutralAbilityFunc.txt", "Units\\NightElfAbilityFunc.txt", "Units\\OrcAbilityFunc.txt",
			"Units\\UndeadAbilityFunc.txt", "Units\\ItemAbilityFunc.txt", "Units\\AbilitySkin.txt",
			"Units\\AbilityBuffSkin.txt", "Units\\CampaignUpgradeFunc.txt", "Units\\HumanUpgradeFunc.txt",
			"Units\\NeutralUpgradeFunc.txt", "Units\\NightElfUpgradeFunc.txt", "Units\\OrcUpgradeFunc.txt",
			"Units\\UndeadUpgradeFunc.txt", "Units\\UpgradeSkin.txt", "Units\\MiscData.txt", "Units\\MiscGame.txt",
			"TerrainArt\\Terrain.slk", "TerrainArt\\CliffTypes.slk", "TerrainArt\\Water.slk", "UI\\MiscData.txt",
			"UI\\SoundInfo\\MiscData.txt", "UI\\SoundInfo\\UISounds.slk", "UI\\SoundInfo\\AbilitySounds.slk",
			"UI\\SoundInfo\\AmbienceSounds.slk", "UI\\SoundInfo\\AmbientMusic.slk",
			"UI\\SoundInfo\\UnitCombatSounds.slk", "UI\\SoundInfo\\AnimSounds.slk" };
	/** Once a game second. */
	public static final int CHECKSUM_INTERVAL = 20;

	private GameReplays() {
	}

	/** Where {@link #LOOK_PROPERTY} points the camera, or null. */
	public static float[] lookAt() {
		final String value = System.getProperty(LOOK_PROPERTY);
		if ((value == null) || value.isEmpty()) {
			return null;
		}
		final String[] parts = value.split(",");
		if (parts.length != 2) {
			return null;
		}
		try {
			return new float[] { Float.parseFloat(parts[0].trim()), Float.parseFloat(parts[1].trim()) };
		}
		catch (final NumberFormatException e) {
			return null;
		}
	}

	/** The unit handles {@link #SELECT_PROPERTY} names, or null. */
	public static int[] selectAtMoment() {
		final String value = System.getProperty(SELECT_PROPERTY);
		if ((value == null) || value.trim().isEmpty()) {
			return null;
		}
		final String[] parts = value.split(",");
		final int[] handles = new int[parts.length];
		try {
			for (int i = 0; i < parts.length; i++) {
				handles[i] = Integer.parseInt(parts[i].trim());
			}
		}
		catch (final NumberFormatException e) {
			return null;
		}
		return handles;
	}

	public static boolean pauseAtMoment() {
		return Boolean.getBoolean(PAUSE_PROPERTY);
	}

	public static String engineVersion() {
		final String version = System.getProperty(VERSION_PROPERTY);
		if ((version != null) && !version.isEmpty()) {
			return version;
		}
		return BuildInfo.current().getVersionName();
	}

	public static FileHandle folder() {
		return StorageRoot.local(FOLDER);
	}

	public static FileHandle lastReplay() {
		return folder().child(LAST_REPLAY + EXTENSION);
	}

	private static String forcedRecordFile() {
		final String file = System.getProperty(RECORD_PROPERTY);
		return (file == null) || file.isEmpty() ? null : file;
	}

	/**
	 * @param networkGame the old UDP network game, which has no replay; a LAN game is recorded like any other
	 */
	public static boolean isRecorded(final boolean campaignGame, final boolean networkGame) {
		if (networkGame) {
			return false;
		}
		if (forcedRecordFile() != null) {
			return true;
		}
		return !campaignGame;
	}

	/** The header of a game about to be loaded: the viewer has its seed, the config the game setup's slots. */
	public static CReplayHeader header(final DataSource dataSource, final String mapFile, final War3MapViewer viewer,
			final War3MapConfig config, final int localPlayerIndex) throws IOException {
		return header(dataSource, mapFile, viewer, config, localPlayerIndex, System.currentTimeMillis());
	}

	/** @param recordedAt the time the game started, which every player of a shared game records the same */
	public static CReplayHeader header(final DataSource dataSource, final String mapFile, final War3MapViewer viewer,
			final War3MapConfig config, final int localPlayerIndex, final long recordedAt) throws IOException {
		final long[] map = mapChecksum(dataSource, mapFile);
		return CReplayHeader.of(engineVersion(), BuildInfo.current().getBuildId(),
				BuildInfo.current().isOfficial(), mapFile, (int) map[0], map[1], dataChecksum(dataSource),
				recordedAt, viewer.getGameSeed(), localPlayerIndex, viewer.getTimeScale(), config,
				CHECKSUM_INTERVAL, CGameCache.snapshot(), viewer.isCampaignGame());
	}

	/** The same, with the players that play with a keyboard and mouse marked (bit i: player slot i). */
	public static CReplayHeader header(final DataSource dataSource, final String mapFile, final War3MapViewer viewer,
			final War3MapConfig config, final int localPlayerIndex, final long recordedAt,
			final int keyboardAndMouseSlots) throws IOException {
		return header(dataSource, mapFile, viewer, config, localPlayerIndex, recordedAt)
				.withKeyboardAndMouseSlots(keyboardAndMouseSlots);
	}

	public static ReplayRecording startRecording(final War3MapViewer viewer, final CReplayHeader header)
			throws IOException {
		return startRecording(viewer, header, null);
	}

	/**
	 * @param checksum how the game's checksum is made, for a caller that needs the same value for itself; null for a
	 *                 checksum of its own
	 */
	public static ReplayRecording startRecording(final War3MapViewer viewer, final CReplayHeader header,
			final ChecksumSteps checksum) throws IOException {
		final String forced = forcedRecordFile();
		final FileHandle target = forced != null ? Gdx.files.absolute(new File(forced).getAbsolutePath())
				: lastReplay();
		final FileHandle written = forced != null ? target : folder().child(TEMP_REPLAY + EXTENSION);
		final ReplayRecording recording = new ReplayRecording(viewer.simulation, header, written, target,
				checksum != null ? checksum : SimulationChecksum.sliced(header.getChecksumInterval()));
		viewer.setReplaySession(recording);
		return recording;
	}

	public static CReplay load(final FileHandle file) throws IOException {
		if (!file.exists()) {
			throw new CReplayException("no such file");
		}
		try (InputStream in = file.read()) {
			return CReplay.read(in);
		}
	}

	/**
	 * The graphics a replay of this map is played with: the ones a game of it is recorded with, which is where its data
	 * checksum comes from. A replay is never of a campaign version, so only the map's own Reforged mark and the
	 * options count.
	 */
	public static GraphicsSet playbackGraphics(final DataSource base, final DataTable ini, final String mapFile) {
		return playbackGraphics(base, ini, mapFile, CampaignMaps.isCampaignMap(mapFile));
	}

	/** @param campaignGame whether the replay is of a campaign's game, as its header says */
	public static GraphicsSet playbackGraphics(final DataSource base, final DataTable ini, final String mapFile,
			final boolean campaignGame) {
		boolean reforgedMap = false;
		try {
			reforgedMap = War3MapViewer
					.isReforgedGraphicsMap(War3MapViewer.beginLoadingMap(base, mapFile).readMapInformation());
		}
		catch (final IOException | RuntimeException e) {
			// the map check names a map that cannot be read
		}
		return GraphicsSettings.gameGraphics(ini, campaignGame, false, reforgedMap);
	}

	/** Why this engine cannot play the replay on the game data as the options set the graphics, or null. */
	public static String unplayableReason(final CReplay replay, final DataSource base, final DataTable ini) {
		final DataSource data = GraphicsSettings.gameData(base, ini,
				playbackGraphics(base, ini, replay.getHeader().getMapPath(), replay.getHeader().isCampaignGame()));
		return unplayableReason(replay.getHeader(), data, null);
	}

	/** Why this engine cannot play the replay, or null. */
	public static String unplayableReason(final CReplay replay, final DataSource dataSource) {
		return unplayableReason(replay.getHeader(), dataSource, null);
	}

	/**
	 * @param dataChecksum the game data's checksum when the caller already knows it (a list checks many replays
	 *                     against the same data), else null
	 */
	public static String unplayableReason(final CReplayHeader header, final DataSource dataSource,
			final Integer dataChecksum) {
		if (!header.getEngineVersion().equals(engineVersion())) {
			return "recorded with engine version " + header.getEngineVersion() + ", this is " + engineVersion();
		}
		if (!isThisCommit(header)) {
			return "recorded with build " + header.getBuildCommit() + ", this is " + BuildInfo.current().getBuildId();
		}
		final long[] map;
		try {
			map = mapChecksum(dataSource, header.getMapPath());
		}
		catch (final IOException | RuntimeException e) {
			return "the map " + header.getMapPath() + " cannot be read: " + e.getMessage();
		}
		if (((int) map[0] != header.getMapChecksum()) || (map[1] != header.getMapSize())) {
			return "the map " + header.getMapPath() + " is not the one the game was played on";
		}
		final int data;
		try {
			data = dataChecksum != null ? dataChecksum : dataChecksum(dataSource);
		}
		catch (final IOException | RuntimeException e) {
			return "the game data cannot be read: " + e.getMessage();
		}
		if (data != header.getDataChecksum()) {
			return "recorded on other game data";
		}
		return null;
	}

	/**
	 * A playback's result line: to the log, to the report file and, when the program only plays the replay, the
	 * program ends.
	 */
	public static void report(final String line) {
		Log.warn("Replay: " + line);
		final String reportFile = System.getProperty(REPORT_PROPERTY);
		if ((reportFile != null) && !reportFile.isEmpty()) {
			try {
				Files.write(new File(reportFile).toPath(), (line + "\n").getBytes(StandardCharsets.UTF_8));
			}
			catch (final IOException e) {
				Log.error(e);
			}
		}
		if (Boolean.getBoolean(EXIT_PROPERTY)) {
			Gdx.app.exit();
		}
	}

	public static boolean isThisEngine(final CReplayHeader header) {
		return header.getEngineVersion().equals(engineVersion()) && isThisCommit(header);
	}

	/**
	 * Builds of one version but other commits play the rules differently. A commit that is not known on either side
	 * cannot be told apart, so it is let through.
	 */
	private static boolean isThisCommit(final CReplayHeader header) {
		final String mine = BuildInfo.current().getBuildId();
		final String recorded = header.getBuildCommit();
		return BuildInfo.UNKNOWN_COMMIT.equals(mine) || BuildInfo.UNKNOWN_COMMIT.equals(recorded)
				|| mine.equals(recorded);
	}

	/**
	 * A playable replay that is not exactly this kind of build: the same commit, but one side is an official build and
	 * the other is not. Null when there is nothing to warn about.
	 */
	public static String buildWarning(final CReplayHeader header) {
		if (header.isOfficialBuild() == BuildInfo.current().isOfficial()) {
			return null;
		}
		return "recorded with " + (header.isOfficialBuild() ? "an official" : "an unofficial") + " build, this is "
				+ (BuildInfo.current().isOfficial() ? "an official" : "an unofficial") + " one";
	}

	/**
	 * The CRC-32 of the game data files, each with its name and size; a missing file counts as missing. It is the
	 * same for the data of any graphics set: the simulation does not read the graphics packages.
	 */
	public static int dataChecksum(final DataSource gameData) throws IOException {
		final DataSource dataSource = gameData.graphicsIndependent();
		final CRC32 crc = new CRC32();
		final byte[] buffer = new byte[1 << 16];
		for (final String file : DATA_FILES) {
			crc.update(file.getBytes(StandardCharsets.UTF_8));
			long size = -1;
			if (dataSource.has(file)) {
				try (InputStream in = dataSource.getResourceAsStream(file)) {
					if (in != null) {
						size = 0;
						int read;
						while ((read = in.read(buffer)) != -1) {
							crc.update(buffer, 0, read);
							size += read;
						}
					}
				}
			}
			for (int shift = 0; shift < 64; shift += 8) {
				crc.update((int) (size >>> shift));
			}
		}
		return (int) crc.getValue();
	}

	/** The CRC-32 and the size of the map file. */
	public static long[] mapChecksum(final DataSource dataSource, final String mapFile) throws IOException {
		final CRC32 crc = new CRC32();
		long size = 0;
		final boolean device = MapFiles.isAbsolute(mapFile) || !dataSource.has(mapFile);
		final File file = device ? MapFiles.find(mapFile) : null;
		if (device && (file == null)) {
			throw new IOException("no such map");
		}
		try (InputStream in = device ? new FileInputStream(file) : dataSource.getResourceAsStream(mapFile)) {
			if (in == null) {
				throw new IOException("no such map");
			}
			final byte[] buffer = new byte[1 << 16];
			int read;
			while ((read = in.read(buffer)) != -1) {
				crc.update(buffer, 0, read);
				size += read;
			}
		}
		return new long[] { crc.getValue(), size };
	}
}
