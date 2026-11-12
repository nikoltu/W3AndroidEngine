package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.WarsmashGdxMapScreen;
import com.etheller.warsmash.WarsmashGdxMenuScreen;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.networking.GameTurnManager;
import com.etheller.warsmash.networking.WarsmashClient;
import com.etheller.warsmash.networking.WarsmashClientSendingOrderListener;
import com.etheller.warsmash.networking.WarsmashClientWriter;
import com.etheller.warsmash.parsers.jass.Jass2;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapObjectData;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3iFlags;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.custom.WTS;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.CampaignMaps;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.MapLoader;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.GameReplays;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.ReplayPlayback;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.ReplayRecording;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ai.AIDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CBasePlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfigStartLoc;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapPlacement;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandRecorder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderExecutor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListenerDelaying;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManagerEntry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplay;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplayException;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplayHeader;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.MenuUI.MenuState;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.CurrentNetGameMapLookupFile;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.CurrentNetGameMapLookupPath;

/**
 * Starting a game from the menu: a map's setup read from its script, a saved game or a replay put in its place, the
 * map loaded behind the loading screen, and the game screen it then hands over to.
 */
final class MenuMapStart {
	/** The game shows its error dialog without any text for a map it cannot open. */
	private static final String MAP_LOAD_ERROR = "";
	private final MenuUI menu;
	/** The map being loaded, for the error when it cannot be. */
	private String loadingMapFilename;
	/** The replay header of the game being loaded, when that game is recorded. */
	private CReplayHeader loadingReplayHeader;
	private Predicate<String> mapLoadFailureListener;
	private LoadingMap loadingMap;
	private FileHandle pendingSavedGame;
	private boolean loadedFixedPlayerSettings;
	private boolean pendingSavedGameByScript;
	/**
	 * The campaign version of the mission started last from the campaign screens; the levels its scripts change to
	 * are played in it too.
	 */
	private boolean reforgedCampaign;
	/** The campaign of the mission started last, by the number scripts know it by; -1 for none. */
	private int campaign = -1;
	/** The map prepared last when it is made for the Reforged graphics alone, else null. */
	private String reforgedGraphicsMap;

	MenuMapStart(final MenuUI menu) {
		this.menu = menu;
	}

	void setMapLoadFailureListener(final Predicate<String> listener) {
		this.mapLoadFailureListener = listener;
	}

	/**
	 * Gives up loading a map: its loading screen goes and the menu shows an error, as the original does for a map it
	 * cannot open.
	 */
	private void mapLoadFailed(final String mapFilename, final Exception cause) {
		Log.error("Unable to load the map " + mapFilename + ":");
		Log.error(cause);
		if (this.loadingMap != null) {
			try {
				this.loadingMap.viewer.dispose();
			}
			catch (final RuntimeException e) {
				Log.error(e);
			}
			this.loadingMap = null;
		}
		this.menu.beginGameInformation = null;
		this.loadingMapFilename = null;
		this.menu.loadingScreen.abandon();
		if ((this.mapLoadFailureListener != null) && this.mapLoadFailureListener.test(mapFilename)) {
			return;
		}
		showMapLoadError();
	}

	void showMapLoadError() {
		this.menu.restoreMainMenuBackground();
		this.menu.campaigns.getFade().setVisible(false);
		this.menu.glueSpriteLayerTopLeft.setVisible(true);
		this.menu.glueSpriteLayerTopRight.setVisible(true);
		this.menu.setGlueLayersSequence("Death");
		this.menu.menuState = MenuState.GOING_TO_MAIN_MENU;
		this.menu.dialog.showError(MAP_LOAD_ERROR, null);
	}

	/**
	 * @param campaign the campaign of the mission started, by the number scripts know it by; -1 for a game that is no
	 *                 campaign's, such as a custom game
	 * @param reforged whether the mission is played in the Reforged campaign version, else the Classic one
	 */
	void setCampaign(final int campaign, final boolean reforged) {
		this.campaign = campaign;
		this.reforgedCampaign = reforged;
		this.menu.campaigns.setStartedCampaign(campaign);
	}

	/**
	 * A saved game goes on in the campaign, version and difficulty it was saved in, so that it ends as that
	 * campaign's mission. A save from before saves kept their campaign is known by its map when only one campaign
	 * lists it; its version and difficulty stay as they were.
	 */
	private void continueSavedCampaign(final FileHandle savedGame, final boolean byScript) {
		final SavedGame.Header header;
		try {
			header = SavedGame.readHeader(savedGame);
		}
		catch (final IOException e) {
			// Loading itself tells why the save cannot be read.
			return;
		}
		int savedCampaign = header.campaign;
		if (header.formatVersion < SavedGame.CAMPAIGN_FORMAT_VERSION) {
			savedCampaign = this.menu.campaigns.campaignOfMap(header.mapFile);
			if ((savedCampaign < 0) && byScript) {
				// A zone left by the map's own LoadGame stays in the campaign it was entered from.
				return;
			}
		}
		else {
			this.reforgedCampaign = header.reforgedCampaignMaps;
			if (header.difficulty != null) {
				this.menu.currentMapConfig.setGameDifficulty(header.difficulty);
			}
		}
		this.campaign = savedCampaign;
		this.menu.campaigns.setStartedCampaign(savedCampaign);
	}

	/**
	 * The graphics a game is played with, its models, icons and interface alike; the menu skin has no say in them. A
	 * campaign mission has those of its campaign version, any other map those the options set for custom games. A map
	 * made for the Reforged graphics alone is never played in Classic.
	 */
	private GraphicsSet gameGraphics(final String mapFilename) {
		final boolean reforgedMap = mapFilename.equals(this.reforgedGraphicsMap);
		if (CampaignMaps.isCampaignMap(mapFilename)) {
			return this.reforgedCampaign || reforgedMap ? GraphicsSet.REFORGED : GraphicsSet.CLASSIC;
		}
		final GraphicsSet custom = GraphicsSettings.graphicsSet(this.menu.warsmashIni);
		return reforgedMap && (custom == GraphicsSet.CLASSIC) ? GraphicsSet.REFORGED : custom;
	}

	private void internalStartMap(final String mapFilename, final int localPlayerIndex) {
		if (this.pendingSavedGame != null) {
			continueSavedCampaign(this.pendingSavedGame, this.pendingSavedGameByScript);
		}
		this.menu.loadingScreen.beginLoading();
		final DataSource codebase = GraphicsSettings.gameData(
				this.menu.gameData != null ? this.menu.gameData
						: WarsmashGdxMapScreen.parseDataSources(this.menu.warsmashIni),
				this.menu.warsmashIni, gameGraphics(mapFilename));
		final GameTurnManager turnManager;
		turnManager = GameTurnManager.PAUSED;
		final War3MapViewer viewer = new War3MapViewer(codebase, this.menu.screenManager, this.menu.currentMapConfig,
				turnManager);
		viewer.setTextureSizeLimit(GraphicsSettings.textureSize(this.menu.warsmashIni));
		viewer.setCampaign(this.campaign, this.reforgedCampaign);
		final boolean loadingSavedGame = this.pendingSavedGame != null;
		viewer.setSavedGameToLoad(this.pendingSavedGame, this.pendingSavedGameByScript);
		setPendingLoad(null, false);
		this.loadingReplayHeader = null;
		final CReplay replay = this.menu.beginGameInformation.replay;
		if (replay != null) {
			viewer.setGameSeed(replay.getHeader().getSeed());
		}
		else if (!loadingSavedGame
				&& GameReplays.isRecorded(mapFilename, this.menu.beginGameInformation.hostInetAddress != null)) {
			try {
				this.loadingReplayHeader = GameReplays.header(codebase, mapFilename, viewer,
						this.menu.currentMapConfig, localPlayerIndex);
			}
			catch (final IOException | RuntimeException e) {
				Log.error("The game is not recorded: " + e);
			}
		}

		if (WarsmashGdxMapScreen.ENABLE_AUDIO) {
			viewer.worldScene.enableAudio();
			viewer.enableAudio();
		}
		try {
			viewer.setMapFileName(mapFilename);
			final War3Map map = War3MapViewer.beginLoadingMap(codebase, mapFilename);
			final War3MapW3i mapInfo = map.readMapInformation();
			final DataTable worldEditData = viewer.loadWorldEditData(map);
			final WTS wts = viewer.preloadWTS(map);
			this.menu.loadingScreen.showMap(map, mapInfo, worldEditData, wts, localPlayerIndex);
			this.loadingMap = new LoadingMap(viewer, map, mapInfo);

		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Loads the game the menu was told to start, behind the loading screen, and hands over to the game screen once it
	 * is loaded.
	 *
	 * @return whether the menu is busy with that this frame and does nothing else
	 */
	boolean update(final float deltaTime) {
		final BeginGameInformation beginGameInformation = this.menu.beginGameInformation;
		if ((beginGameInformation == null) || (this.menu.menuState == MenuState.GOING_TO_MAP)) {
			return false;
		}
		this.menu.campaigns.getFade().setVisible(false);
		if (!beginGameInformation.loadingStarted) {
			final String mapFilename;
			if (beginGameInformation.gameMapLookup instanceof CurrentNetGameMapLookupFile) {
				mapFilename = ((CurrentNetGameMapLookupFile) beginGameInformation.gameMapLookup).getFile()
						.getAbsolutePath();
			}
			else if (beginGameInformation.gameMapLookup instanceof CurrentNetGameMapLookupPath) {
				mapFilename = ((CurrentNetGameMapLookupPath) beginGameInformation.gameMapLookup).getPath();
			}
			else {
				throw new RuntimeException("Begin game information failed");
			}
			this.loadingMapFilename = mapFilename;
			try {
				internalStartMap(mapFilename, beginGameInformation.localPlayerIndex);
			}
			catch (final RuntimeException e) {
				mapLoadFailed(mapFilename, e);
				return true;
			}
			beginGameInformation.loadingStarted = true;
			return true;
		}
		if (this.loadingMap == null) {
			return false;
		}
		final MenuLoadingScreen loadingScreen = this.menu.loadingScreen;
		final int localPlayerIndex = beginGameInformation.localPlayerIndex;
		try {
			if (this.loadingMap.activeMapLoader != null) {
				if (loadingScreen.isMapDone() || this.loadingMap.activeMapLoader.process()) {
					loadingScreen.markMapDone();
					// A replay starts at once, as in the original.
					if (loadingScreen.holdsForTap() && (beginGameInformation.hostInetAddress == null)
							&& (beginGameInformation.replay == null)) {
						loadingScreen.showTapToContinue(deltaTime);
						return true;
					}
					loadingScreen.markMapStarted();
					CPlayerUnitOrderListener uiOrderListener;
					final WarsmashClient warsmashClient;
					if (beginGameInformation.hostInetAddress != null) {

						try {
							final InetAddress byAddress = InetAddress.getByAddress(beginGameInformation.hostInetAddress);
							Log.warn("Connecting to address: " + byAddress);
							warsmashClient = new WarsmashClient(byAddress, beginGameInformation.hostUdpPort,
									this.loadingMap.viewer, beginGameInformation.sessionToken,
									beginGameInformation.serverSlotToMapSlot);
						}
						catch (final UnknownHostException e) {
							throw new RuntimeException(e);
						}
						catch (final IOException e) {
							throw new RuntimeException(e);
						}
						final WarsmashClientWriter warsmashClientWriter = warsmashClient.getWriter();
						warsmashClientWriter.joinGame();
						warsmashClientWriter.send();
						uiOrderListener = new WarsmashClientSendingOrderListener(warsmashClientWriter);
					}
					else if (beginGameInformation.replay != null) {
						final ReplayPlayback playback = new ReplayPlayback(beginGameInformation.replay,
								beginGameInformation.replayFile, this.loadingMap.viewer,
								beginGameInformation.replayStartTick);
						playback.setForced(beginGameInformation.replayForced);
						uiOrderListener = playback.viewerCommands(localPlayerIndex);
						warsmashClient = null;
						this.loadingMap.viewer.setGameTurnManager(playback);
					}
					else {
						final War3MapViewer mapViewer = this.loadingMap.viewer;
						final CPlayerUnitOrderExecutor executor = new CPlayerUnitOrderExecutor(
								this.loadingMap.viewer.simulation, localPlayerIndex);
						final CPlayerUnitOrderListenerDelaying delayingListener = new CPlayerUnitOrderListenerDelaying(
								new CPlayerCommandRecorder(this.loadingMap.viewer.simulation, localPlayerIndex,
										executor));
						uiOrderListener = delayingListener;
						warsmashClient = null;
						final ReplayRecording recording = startRecording(mapViewer);
						mapViewer.setGameTurnManager(new GameTurnManager() {
							@Override
							public void turnCompleted(final int gameTurnTick) {
								if (recording != null) {
									recording.afterStep();
								}
								delayingListener.publishDelayedActions();
							}

							@Override
							public int getLatestCompletedTurn() {
								return Integer.MAX_VALUE;
							}

							@Override
							public void framesSkipped(final float skippedCount) {

							}
						});
					}

					// TODO not cast menu screen
					this.menu.screenManager.setScreen(new WarsmashGdxMapScreen(this.loadingMap.viewer,
							this.menu.screenManager, (WarsmashGdxMenuScreen) this.menu.menuScreen, uiOrderListener));
					this.loadingMap = null;
					this.menu.beginGameInformation = null;
					// The loading screen stays drawn until the game has drawn its first frame; hiding it now
					// would end this frame black while the game's first frames are still being prepared.
					if (warsmashClient != null) {
						warsmashClient.startThread();
					}
				}
				else {
					loadingScreen.setLoadingProgress(this.loadingMap.activeMapLoader.getCompletionRatio());
				}
			}
			else {
				this.loadingMap.activeMapLoader = this.loadingMap.viewer.createMapLoader(this.loadingMap.map,
						this.loadingMap.mapInfo, localPlayerIndex);
			}
		}
		catch (final IOException | RuntimeException e) {
			// A map that cannot be loaded goes back to where it was started from, saying so, instead of
			// leaving its loading screen up for good.
			mapLoadFailed(this.loadingMapFilename, e);
		}
		return true;
	}

	/**
	 * The save the next map started puts in place of the map's start, or null.
	 *
	 * @param byScript whether the map's own LoadGame asked for it
	 */
	void setPendingLoad(final FileHandle savedGame, final boolean byScript) {
		this.pendingSavedGame = savedGame;
		this.pendingSavedGameByScript = byScript;
	}

	boolean startMap(final String mapFilename, final String computerSlots) {
		// A level a script changes to (or restarts) is played at the difficulty of the game before it.
		final CMapDifficulty difficulty = this.menu.currentMapConfig == null ? CMapDifficulty.NORMAL
				: this.menu.currentMapConfig.getGameDifficulty();
		if (!prepareMapConfigs(mapFilename)) {
			return false;
		}
		final War3MapConfig currentMapConfig = this.menu.currentMapConfig;
		this.menu.mainMenuFrame.setVisible(false);
		currentMapConfig.setGameDifficulty(difficulty);

		this.menu.campaigns.hideScreens();
		this.menu.campaigns.setMissionSelectMenuVisible(false);
		this.menu.skirmish.hide();
		this.menu.setGlueLayersSequence("Death");
		final BeginGameInformation beginGameInformation = new BeginGameInformation();
		this.menu.beginGameInformation = beginGameInformation;
		beginGameInformation.gameMapLookup = new CurrentNetGameMapLookupPath(mapFilename);
		int localPlayerIndex = -1;
		// Measured: with fixed player settings the original's -loadfile plays only the first user slot, the local
		// player's; the other user slots stay empty and get no starting units.
		final boolean fixedPlayerSettings = this.loadedFixedPlayerSettings;
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			final CBasePlayer player = currentMapConfig.getPlayer(i);
			if (player.getController() == CMapControl.USER) {
				if (fixedPlayerSettings && (localPlayerIndex != -1)) {
					player.setSlotState(CPlayerSlotState.EMPTY);
					continue;
				}
				player.setSlotState(CPlayerSlotState.PLAYING);
				if (localPlayerIndex == -1) {
					localPlayerIndex = i;
				}
			}
			else if (player.getController() == CMapControl.COMPUTER) {
				// Measured: the original's -loadfile plays the computer slots of the map script's config.
				player.setSlotState(CPlayerSlotState.PLAYING);
				player.setAIDifficulty(AIDifficulty.NORMAL);
			}
		}
		if (computerSlots != null) {
			for (final String slot : computerSlots.split(",")) {
				final String[] slotAndRace = slot.split(":");
				final CBasePlayer player = currentMapConfig.getPlayer(Integer.parseInt(slotAndRace[0].trim()));
				player.setController(CMapControl.COMPUTER);
				player.setSlotState(CPlayerSlotState.PLAYING);
				player.setAIDifficulty(AIDifficulty.NORMAL);
				if (slotAndRace.length > 1) {
					for (int i = 0; i < WarsmashConstants.RACE_MANAGER.getEntryCount(); i++) {
						final CRaceManagerEntry entry = WarsmashConstants.RACE_MANAGER.get(i);
						if (entry.getKey().equalsIgnoreCase(slotAndRace[1].trim())) {
							player.setRacePref(
									WarsmashConstants.RACE_MANAGER.getRacePreferenceById(entry.getRacePrefId()));
						}
					}
				}
				if (localPlayerIndex == player.getId()) {
					localPlayerIndex = -1;
				}
			}
			if (localPlayerIndex == -1) {
				for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
					if (currentMapConfig.getPlayer(i).getController() == CMapControl.USER) {
						currentMapConfig.getPlayer(i).setSlotState(CPlayerSlotState.PLAYING);
						localPlayerIndex = i;
						break;
					}
				}
			}
		}
		if (localPlayerIndex != -1) {
			currentMapConfig.getPlayer(localPlayerIndex).setName(this.menu.profileManager.getCurrentProfile());
		}
		else {
			localPlayerIndex = observerSlot(currentMapConfig);
			if (localPlayerIndex == -1) {
				this.menu.beginGameInformation = null;
				Log.error("No slot is left to watch the game from: " + mapFilename);
				return false;
			}
		}
		beginGameInformation.localPlayerIndex = localPlayerIndex;
		this.menu.menuState = MenuState.GOING_TO_MAP;
		return true;
	}

	/**
	 * A game in which only computers play is watched from a slot no one plays, as an observer: the game is the same as
	 * without a local player, which is what determinism tests and replays of such games need.
	 *
	 * @return -1 when every player slot is taken
	 */
	static int observerSlot(final War3MapConfig config) {
		final int playerSlots = WarsmashConstants.MAX_PLAYERS - 4;
		for (int i = 0; i < playerSlots; i++) {
			final CBasePlayer player = config.getPlayer(i);
			if ((player.getSlotState() != CPlayerSlotState.PLAYING) && (player.getController() != CMapControl.USER)
					&& (player.getController() != CMapControl.COMPUTER)) {
				return i;
			}
		}
		for (int i = 0; i < playerSlots; i++) {
			if (config.getPlayer(i).getSlotState() != CPlayerSlotState.PLAYING) {
				return i;
			}
		}
		return -1;
	}

	private ReplayRecording startRecording(final War3MapViewer viewer) {
		final CReplayHeader header = this.loadingReplayHeader;
		this.loadingReplayHeader = null;
		if (header == null) {
			return null;
		}
		try {
			return GameReplays.startRecording(viewer, header);
		}
		catch (final IOException | RuntimeException e) {
			Log.error("The game is not recorded: " + e);
			return null;
		}
	}

	boolean startReplay(final FileHandle file, final int startTick, final boolean forced) {
		final CReplay replay;
		try {
			replay = GameReplays.load(file);
			final String reason = GameReplays.unplayableReason(replay,
					this.menu.gameData != null ? this.menu.gameData : this.menu.dataSource);
			if (reason != null) {
				if (!forced && !Boolean.getBoolean(GameReplays.FORCE_PROPERTY)) {
					throw new CReplayException(reason);
				}
				Log.warn("Replay: played although " + reason);
			}
		}
		catch (final IOException e) {
			GameReplays.report("result=REJECTED reason=" + e.getMessage());
			return false;
		}
		final CReplayHeader header = replay.getHeader();
		final String buildWarning = GameReplays.buildWarning(header);
		if (buildWarning != null) {
			Log.warn("Replay: " + buildWarning);
		}
		setCampaign(-1, false);
		if (!startMap(header.getMapPath(), null)) {
			return false;
		}
		header.applyTo(this.menu.currentMapConfig, WarsmashConstants.RACE_MANAGER);
		this.menu.beginGameInformation.localPlayerIndex = header.getLocalPlayerIndex();
		this.menu.beginGameInformation.replay = replay;
		this.menu.beginGameInformation.replayFile = file;
		this.menu.beginGameInformation.replayStartTick = startTick;
		this.menu.beginGameInformation.replayForced = forced;
		return true;
	}

	boolean startSavedGame(final FileHandle file) {
		CSaveStorage.finishBackgroundWrites();
		try {
			final SavedGame.Header header = SavedGame.readHeader(file);
			setPendingLoad(file, false);
			this.reforgedCampaign = false;
			if (!startMap(header.mapFile, null)) {
				setPendingLoad(null, false);
				return false;
			}
			this.menu.singlePlayerMenu.setVisible(false);
			return true;
		}
		catch (final IOException exc) {
			Log.error(exc);
			this.menu.singlePlayerMenu.setVisible(true);
			return false;
		}
	}

	private void loadAndCacheMapConfigs(final String mapFilename) throws IOException {
		final War3Map map = War3MapViewer.beginLoadingMap(this.menu.dataSource, mapFilename);
		final War3MapW3i mapInfo = map.readMapInformation();
		this.reforgedGraphicsMap = War3MapViewer.isReforgedGraphicsMap(mapInfo) ? mapFilename : null;
		requireJassScript(map, mapFilename);
		final WTS wtsFile = Warcraft3MapObjectData.loadWTS(map);
		this.menu.rootFrame.setMapStrings(wtsFile);
		final War3MapConfig war3MapConfig = new War3MapConfig(WarsmashConstants.MAX_PLAYERS);
		war3MapConfig.applyPlayerNames(mapInfo, this.menu.rootFrame::getTrigStr);
		war3MapConfig.applyMapInformation(mapInfo);
		Jass2.loadConfig(map, this.menu.uiViewport, this.menu.uiScene, this.menu.rootFrame, war3MapConfig,
				WarsmashConstants.JASS_FILE_LIST).config();
		shuffleStartLocations(war3MapConfig, prepareMeleeLobby(war3MapConfig, mapInfo));
		this.loadedFixedPlayerSettings = mapInfo.hasFlag(War3MapW3iFlags.FIXED_PLAYER_SETTINGS_FOR_CUSTOM_FORCES);
		this.menu.currentMapConfig = war3MapConfig;
	}

	static void requireJassScript(final War3Map map, final String mapFilename) throws IOException {
		if (!map.hasJassScript()) {
			throw new IOException("The map has no JASS script (war3map.j), only a Lua one or none: " + mapFilename);
		}
	}

	/**
	 * A map's information and script config read for a start; on failure the menu is left as it was.
	 *
	 * @return false when the map cannot be read or has no script this engine runs
	 */
	boolean prepareMapConfigs(final String mapFilename) {
		try {
			loadAndCacheMapConfigs(mapFilename);
			return true;
		}
		catch (final IOException | RuntimeException e) {
			Log.error(e);
			return false;
		}
	}

	/**
	 * Melee games start as free for all: each player on a team of their own until the lobby joins them.
	 *
	 * @return how many players take random start locations, 0 when the map fixes them
	 */
	static int prepareMeleeLobby(final War3MapConfig config, final War3MapW3i mapInfo) {
		if (!mapInfo.hasFlag(War3MapW3iFlags.MELEE_MAP)
				|| mapInfo.hasFlag(War3MapW3iFlags.FIXED_PLAYER_SETTINGS_FOR_CUSTOM_FORCES)) {
			return 0;
		}
		final int playerCount = Math.min(WarsmashConstants.MAX_PLAYERS, mapInfo.getPlayers().size());
		for (int i = 0; i < playerCount; i++) {
			config.getPlayer(i).setTeam(i);
		}
		config.setLobbyTeams(true);
		return playerCount;
	}

	/** With a fixed game seed the random start locations are the same in every run too. */
	private static Random startLocationRandom() {
		final Integer fixed = Integer.getInteger("warsmash.seed");
		return fixed != null ? new Random(fixed) : new Random();
	}

	/**
	 * A melee map without fixed player settings gives its players random start locations, as the original game's
	 * game setup does; the map script itself gives each player its own. With teams together, the players of a team
	 * take the free locations nearest to their first member's.
	 */
	static void shuffleStartLocations(final War3MapConfig config, final int playerCount) {
		// Measured: a script's SetGamePlacement(MAP_PLACEMENT_FIXED) keeps each player on the start location its
		// SetPlayerStartLocation gave it.
		if (config.getPlacement() == CMapPlacement.FIXED) {
			return;
		}
		final Random random = startLocationRandom();
		final List<Integer> locations = new ArrayList<>();
		for (int i = 0; i < playerCount; i++) {
			locations.add(config.getPlayer(i).getStartLocationIndex());
		}
		Collections.shuffle(locations, random);
		if (config.getPlacement() != CMapPlacement.TEAMS_TOGETHER) {
			for (int i = 0; i < playerCount; i++) {
				config.getPlayer(i).setStartLocationIndex(locations.get(i));
			}
			return;
		}
		final Map<Integer, List<Integer>> teams = new LinkedHashMap<>();
		final List<Integer> order = new ArrayList<>();
		for (int i = 0; i < playerCount; i++) {
			order.add(i);
		}
		Collections.shuffle(order, random);
		for (final int i : order) {
			teams.computeIfAbsent(config.getPlayer(i).getTeam(), (team) -> new ArrayList<>()).add(i);
		}
		for (final List<Integer> members : teams.values()) {
			final War3MapConfigStartLoc first = config.getStartLoc(locations.get(0));
			for (final int member : members) {
				int nearest = 0;
				float nearestDistance = Float.MAX_VALUE;
				for (int k = 0; k < locations.size(); k++) {
					final War3MapConfigStartLoc candidate = config.getStartLoc(locations.get(k));
					final float dx = candidate.getX() - first.getX();
					final float dy = candidate.getY() - first.getY();
					final float distance = (dx * dx) + (dy * dy);
					if (distance < nearestDistance) {
						nearestDistance = distance;
						nearest = k;
					}
				}
				config.getPlayer(member).setStartLocationIndex(locations.remove(nearest));
			}
		}
	}

	private static final class LoadingMap {

		private final War3MapViewer viewer;
		private final War3Map map;
		private final War3MapW3i mapInfo;

		private MapLoader activeMapLoader = null;

		public LoadingMap(final War3MapViewer viewer, final War3Map map, final War3MapW3i mapInfo) {
			this.viewer = viewer;
			this.map = map;
			this.mapInfo = mapInfo;
		}

	}
}
