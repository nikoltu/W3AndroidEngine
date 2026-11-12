package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.launcher.LauncherSlot;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.ListBoxFrame.ListBoxSelelectionListener;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.parsers.jass.Jass2;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapObjectData;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3iFlags;
import com.etheller.warsmash.units.custom.WTS;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ai.AIDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CBasePlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRacePreference;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.MenuUI.MenuState;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.AdvancedOptionsPane;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.CurrentNetGameMapLookupPath;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.MapInfoPane;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.MapListContainer;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.PlayerSlotPaneListener;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.TeamSetupPane;

import net.warsmash.uberserver.LobbyPlayerType;

/**
 * The single player custom game screen ("Skirmish"): its map list, map information, advanced options and player
 * slots, and its Start Game, which a launcher also uses.
 */
final class MenuSkirmish {
	private static final String SKIRMISH_MAP_PREFERENCE = "SkirmishMap";
	private final MenuUI menu;
	private UIFrame skirmish;
	private UIFrame mapInfoPanel;
	private UIFrame advancedOptionsPanel;
	private GlueTextButtonFrame mapInfoButton;
	private GlueTextButtonFrame advancedOptionsButton;
	private AdvancedOptionsPane skirmishAdvancedOptionsPane;
	private int randomStartLocationCount;
	private MapInfoPane skirmishMapInfoPane;
	private MapListContainer skirmishMapList;
	private PlayerSlotPaneListenerImplementation skirmishSlotListener;
	private Runnable skirmishStart;
	private GlueTextButtonFrame skirmishCancelButton;

	MenuSkirmish(final MenuUI menu) {
		this.menu = menu;
	}

	void create() {
		final GameUI rootFrame = this.menu.rootFrame;
		final Viewport uiViewport = this.menu.uiViewport;
		this.skirmish = rootFrame.createFrame("Skirmish", rootFrame, 0, 0);
		this.skirmish.setVisible(false);

		this.mapInfoButton = (GlueTextButtonFrame) rootFrame.getFrameByName("MapInfoButton", 0);
		this.advancedOptionsButton = (GlueTextButtonFrame) rootFrame.getFrameByName("AdvancedOptionsButton", 0);
		this.mapInfoPanel = rootFrame.getFrameByName("MapInfoPanel", 0);
		this.advancedOptionsPanel = rootFrame.getFrameByName("AdvancedOptionsPanel", 0);
		final SimpleFrame mapInfoPaneContainer = (SimpleFrame) rootFrame.getFrameByName("MapInfoPaneContainer", 0);
		final SimpleFrame advancedOptionsPaneContainer = (SimpleFrame) rootFrame
				.getFrameByName("AdvancedOptionsPaneContainer", 0);
		this.skirmishAdvancedOptionsPane = new AdvancedOptionsPane(rootFrame, advancedOptionsPaneContainer);
		// Each panel's button opens the other panel: the map info one reads "Advanced Options" and the other "Map Info".
		this.mapInfoButton.setOnClick(() -> showAdvancedOptions(true));
		this.advancedOptionsButton.setOnClick(() -> showAdvancedOptions(false));
		this.skirmishMapInfoPane = new MapInfoPane(rootFrame, uiViewport, mapInfoPaneContainer);

		final SimpleFrame teamSetupContainer = (SimpleFrame) rootFrame.getFrameByName("TeamSetupContainer", 0);
		final TeamSetupPane teamSetupPane = new TeamSetupPane(rootFrame, uiViewport, teamSetupContainer);

		final GlueTextButtonFrame playGameButton = (GlueTextButtonFrame) rootFrame.getFrameByName("PlayGameButton", 0);
		final MapListContainer mapListContainer = new MapListContainer(rootFrame, uiViewport, "MapListContainer",
				this.menu.dataSource, ((StringFrame) rootFrame.getFrameByName("ProfileListText", 0)).getFrameFont());
		this.skirmishMapList = mapListContainer;
		mapListContainer.addSelectionListener(new ListBoxSelelectionListener() {
			War3Map lastMapListMap;
			String prevSelectedItem = "";

			@Override
			public void onSelectionChanged(final int newSelectedIndex, final String newSelectedItem) {
				if (newSelectedItem != null) {
					if (newSelectedItem.compareTo(this.prevSelectedItem) == 0) {
						return;
					}
					this.prevSelectedItem = newSelectedItem;

					try {
						final War3Map map = War3MapViewer.beginLoadingMap(MenuSkirmish.this.menu.dataSource,
								newSelectedItem);
						if (this.lastMapListMap != null) {
							try {
								this.lastMapListMap.close();
							}
							catch (final IOException e) {
								Log.error(e);
							}
						}
						this.lastMapListMap = map;
						selectMap(map, newSelectedItem, teamSetupPane);
					}
					catch (final IOException e) {
						Log.error(e);
					}
				}
			}
		});
		this.skirmishStart = () -> start(mapListContainer.getSelectedItem());
		playGameButton.setOnClick(this.skirmishStart);

		this.skirmishCancelButton = (GlueTextButtonFrame) rootFrame.getFrameByName("CancelButton", 0);
		this.skirmishCancelButton.setOnClick(() -> {
			this.menu.setGlueLayersSequence("SinglePlayerSkirmish Death");
			this.skirmish.setVisible(false);
			this.menu.menuState = MenuState.GOING_TO_SINGLE_PLAYER;
		});
	}

	/** The map the list selects: its information, its script's player setup, and the screen's panes for it. */
	private void selectMap(final War3Map map, final String mapPath, final TeamSetupPane teamSetupPane)
			throws IOException {
		final GameUI rootFrame = this.menu.rootFrame;
		final War3MapW3i mapInfo = map.readMapInformation();
		final WTS wtsFile = Warcraft3MapObjectData.loadWTS(map);
		rootFrame.setMapStrings(wtsFile);
		final War3MapConfig war3MapConfig = new War3MapConfig(WarsmashConstants.MAX_PLAYERS);
		war3MapConfig.applyPlayerNames(mapInfo, rootFrame::getTrigStr);
		war3MapConfig.setMapName("NOTEXTERN: default name string");
		war3MapConfig.setMapDescription("NOTEXTERN: default description string");
		war3MapConfig.applyMapInformation(mapInfo);
		this.skirmishAdvancedOptionsPane.setMapInformation(mapInfo);
		MenuMapStart.requireJassScript(map, mapPath);
		Jass2.loadConfig(map, this.menu.uiViewport, this.menu.uiScene, rootFrame, war3MapConfig,
				WarsmashConstants.JASS_FILE_LIST).config();
		boolean foundFirstHuman = false;
		boolean foundFirstComp = false;
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			final CBasePlayer player = war3MapConfig.getPlayer(i);
			if (player.getController() == CMapControl.USER) {
				if (!foundFirstHuman) {
					player.setSlotState(CPlayerSlotState.PLAYING);
					player.setName(this.menu.profileManager.getCurrentProfile());
					foundFirstHuman = true;
				}
			}
			else if (player.getController() == CMapControl.COMPUTER) {
				if (!foundFirstComp || mapInfo.hasFlag(War3MapW3iFlags.FIXED_PLAYER_SETTINGS_FOR_CUSTOM_FORCES)) {
					player.setSlotState(CPlayerSlotState.PLAYING);
					foundFirstComp = true;
				}
			}
		}
		this.randomStartLocationCount = MenuMapStart.prepareMeleeLobby(war3MapConfig, mapInfo);
		this.skirmishMapInfoPane.setMap(rootFrame, this.menu.uiViewport, map, mapInfo, war3MapConfig);
		this.skirmishSlotListener = new PlayerSlotPaneListenerImplementation(teamSetupPane, war3MapConfig, mapInfo,
				rootFrame, this.menu.uiViewport);
		teamSetupPane.setMap(rootFrame, this.menu.uiViewport, war3MapConfig, mapInfo.getPlayers().size(), mapInfo,
				this.skirmishSlotListener);
		this.menu.currentMapConfig = war3MapConfig;
	}

	/** Start Game with the given map of the list selected; nothing happens without one. */
	private void start(final String selectedItem) {
		if (selectedItem != null) {
			final Preferences preferences = Gdx.app.getPreferences("WarsmashWC3Engine");
			preferences.putString(SKIRMISH_MAP_PREFERENCE, selectedItem);
			preferences.flush();
			// A custom game is no campaign's, whatever mission was played before it in this session.
			this.menu.mapStart.setCampaign(-1, false);
			this.menu.campaigns.hideScreens();
			this.menu.campaigns.setMissionSelectMenuVisible(false);
			this.skirmish.setVisible(false);
			this.menu.setGlueLayersSequence("Death");
			final War3MapConfig currentMapConfig = this.menu.currentMapConfig;
			this.skirmishAdvancedOptionsPane.applyTo(currentMapConfig);
			// The lobby marks a closed slot as playing without a controller; in the game it is empty, as in
			// the original, so the melee scripts give it no starting base.
			for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
				final CBasePlayer configPlayer = currentMapConfig.getPlayer(i);
				if ((configPlayer.getSlotState() == CPlayerSlotState.PLAYING)
						&& (configPlayer.getController() == CMapControl.NONE)) {
					configPlayer.setSlotState(CPlayerSlotState.EMPTY);
				}
				else if ((configPlayer.getSlotState() == CPlayerSlotState.PLAYING)
						&& (configPlayer.getController() == CMapControl.COMPUTER)) {
					// Measured: a lobby computer plays under its slot label, e.g. "Computer (Easy)", which
					// GetPlayerName, the score screen and the unit tooltips show.
					configPlayer.setName(this.menu.rootFrame.getTemplates()
							.getDecoratedString(MenuLoadingScreen.getComputerSlotKey(configPlayer.getAIDifficulty())));
				}
			}
			MenuMapStart.shuffleStartLocations(currentMapConfig, this.randomStartLocationCount);
			final BeginGameInformation beginGameInformation = new BeginGameInformation();
			this.menu.beginGameInformation = beginGameInformation;
			beginGameInformation.gameMapLookup = new CurrentNetGameMapLookupPath(selectedItem);
			int localPlayerIndex = -1;
			for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
				final CBasePlayer configPlayer = currentMapConfig.getPlayer(i);
				if ((configPlayer.getSlotState() == CPlayerSlotState.PLAYING)
						&& (configPlayer.getController() == CMapControl.USER)) {
					localPlayerIndex = i;
					break;
				}
			}
			beginGameInformation.localPlayerIndex = localPlayerIndex;
			this.menu.menuState = MenuState.GOING_TO_MAP;
		}
	}

	private void showAdvancedOptions(final boolean advanced) {
		this.mapInfoPanel.setVisible(!advanced);
		this.advancedOptionsPanel.setVisible(advanced);
	}

	/** The single player menu's Custom Game button. */
	void open() {
		this.menu.setGlueLayersSequence("SinglePlayer Death");
		this.menu.singlePlayerMenu.setVisible(false);
		this.menu.profiles.hidePanel();
		this.menu.menuState = MenuState.GOING_TO_SINGLE_PLAYER_SKIRMISH;
	}

	void hide() {
		this.skirmish.setVisible(false);
	}

	boolean startSkirmish(final String mapPath, final String slots, final String options) {
		final PlayerSlotPaneListenerImplementation before = this.skirmishSlotListener;
		if (!this.skirmishMapList.select(mapPath) || (this.skirmishSlotListener == before)
				|| (this.skirmishSlotListener == null)) {
			return false;
		}
		for (final LauncherSlot slot : LauncherSlot.parse(slots)) {
			this.skirmishSlotListener.setPlayerSlot(slot.player, LobbyPlayerType.valueOf(slot.type));
			if (slot.race >= 0) {
				this.skirmishSlotListener.setPlayerRace(slot.player, slot.race);
			}
			if (slot.team >= 0) {
				this.skirmishSlotListener.setPlayerTeam(slot.player, slot.team);
			}
			if (slot.color >= 0) {
				this.skirmishSlotListener.setPlayerColor(slot.player, slot.color);
			}
		}
		if (options != null) {
			final List<String> on = Arrays.asList(options.split(","));
			this.skirmishAdvancedOptionsPane.setOptions(on.contains("lockTeams"), on.contains("teamsTogether"),
					on.contains("sharedControl"), on.contains("randomRaces"), on.contains("randomHero"));
		}
		this.menu.mainMenuFrame.setVisible(false);
		this.skirmishStart.run();
		return true;
	}

	/** @return whether the state is one of the custom game screen's, which it then advances */
	boolean updateMenuState(final MenuState menuState) {
		switch (menuState) {
		case GOING_TO_SINGLE_PLAYER_SKIRMISH:
			this.menu.setGlueLayersSequence("SinglePlayerSkirmish Birth");
			showAdvancedOptions(false);
			this.skirmishMapList.selectInitial(
					Gdx.app.getPreferences("WarsmashWC3Engine").getString(SKIRMISH_MAP_PREFERENCE, null));
			this.menu.menuState = MenuState.SINGLE_PLAYER_SKIRMISH;
			return true;
		case SINGLE_PLAYER_SKIRMISH:
			this.skirmish.setVisible(true);
			this.menu.setGlueLayersSequence("SinglePlayerSkirmish Stand");
			return true;
		default:
			return false;
		}
	}

	private static final class PlayerSlotPaneListenerImplementation implements PlayerSlotPaneListener {
		private final TeamSetupPane teamSetupPane;
		private final War3MapConfig war3MapConfig;
		private final War3MapW3i mapInfo;
		private final GameUI rootFrame;
		private final Viewport uiViewport;

		private PlayerSlotPaneListenerImplementation(final TeamSetupPane teamSetupPane,
				final War3MapConfig war3MapConfig, final War3MapW3i mapInfo, final GameUI rootFrame,
				final Viewport uiViewport) {
			this.teamSetupPane = teamSetupPane;
			this.war3MapConfig = war3MapConfig;
			this.mapInfo = mapInfo;
			this.rootFrame = rootFrame;
			this.uiViewport = uiViewport;
		}

		@Override
		public void setPlayerSlot(final int index, final LobbyPlayerType lobbyPlayerType) {
			final CBasePlayer player = this.war3MapConfig.getPlayer(index);
			switch (lobbyPlayerType) {
			case OPEN:
				player.setController(CMapControl.NONE);
				player.setSlotState(CPlayerSlotState.EMPTY);
				player.setAIDifficulty(null);
				break;
			case CLOSED:
				player.setController(CMapControl.NONE);
				player.setSlotState(CPlayerSlotState.PLAYING);
				player.setAIDifficulty(null);
				break;
			case COMPUTER_NEWBIE:
				player.setController(CMapControl.COMPUTER);
				player.setSlotState(CPlayerSlotState.PLAYING);
				player.setAIDifficulty(AIDifficulty.NEWBIE);
				break;
			case COMPUTER_NORMAL:
				player.setController(CMapControl.COMPUTER);
				player.setSlotState(CPlayerSlotState.PLAYING);
				player.setAIDifficulty(AIDifficulty.NORMAL);
				break;
			case COMPUTER_INSANE:
				player.setController(CMapControl.COMPUTER);
				player.setSlotState(CPlayerSlotState.PLAYING);
				player.setAIDifficulty(AIDifficulty.INSANE);
				break;
			case USER:
				player.setController(CMapControl.USER);
				player.setSlotState(CPlayerSlotState.PLAYING);
				player.setAIDifficulty(null);
				break;
			}
			notifyUpdated(index);
		}

		@Override
		public void setPlayerRace(final int index, final int raceItemIndex) {
			final CBasePlayer player = this.war3MapConfig.getPlayer(index);
			if (raceItemIndex == 0) {
				player.setRacePref(WarsmashConstants.RACE_MANAGER.getRandomRacePreference());
			}
			else {
				final CRace race = WarsmashConstants.RACE_MANAGER.getRace(raceItemIndex);
				final CRacePreference racePreference = WarsmashConstants.RACE_MANAGER.getRacePreferenceForRace(race);
				player.setRacePref(racePreference);
			}
			notifyUpdated(index);
		}

		@Override
		public void setPlayerTeam(final int index, final int team) {
			this.war3MapConfig.getPlayer(index).setTeam(team);
			notifyUpdated(index);
		}

		@Override
		public void setPlayerHandicap(final int index, final float handicap) {
			this.war3MapConfig.getPlayer(index).setHandicap(handicap);
			notifyUpdated(index);
		}

		@Override
		public void setPlayerColor(final int index, final int color) {
			this.war3MapConfig.getPlayer(index).setColor(color);
			notifyUpdated(index);
		}

		private void notifyUpdated(final int index) {
			this.teamSetupPane.notifyPlayerDataUpdated(index, this.rootFrame, this.uiViewport, this.war3MapConfig,
					this.mapInfo);
		}
	}
}
