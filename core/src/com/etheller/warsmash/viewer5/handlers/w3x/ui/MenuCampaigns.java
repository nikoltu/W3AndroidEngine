package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.launcher.CampaignResult;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.MenuItem;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.MenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.PopupMenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CBasePlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManagerEntry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.MenuUI.MenuState;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.CurrentNetGameMapLookupPath;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignButtonUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMenuData;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMenuUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/**
 * The campaign screens: the campaign list, each campaign's missions, the difficulty choice, and the backgrounds,
 * sounds and cursors of the campaigns.
 */
final class MenuCampaigns {
	// Measured: the 1.29 campaign screen offers Normal and Hard; Easy is reached only by reducing the difficulty after
	// a defeat.
	private static final CMapDifficulty[] CAMPAIGN_DIFFICULTIES = { CMapDifficulty.NORMAL, CMapDifficulty.HARD };
	private static final String[] CAMPAIGN_DIFFICULTY_KEYS = { "NORMAL", "HARD" };
	private final MenuUI menu;
	private UIFrame campaignMenu;
	private SpriteFrame campaignFade;
	private GlueTextButtonFrame campaignBackButton;
	private UIFrame missionSelectFrame;
	private UIFrame campaignSelectFrame;
	private DataTable campaignStrings;
	private CampaignCatalog campaignCatalog;
	private boolean classicCampaignList = false;
	private SpriteFrame campaignWarcraftIIILogo;
	private CampaignMenuData currentCampaign;
	private final List<CampaignEntry> campaignEntries = new ArrayList<>();
	private PopupMenuFrame difficultySelect;
	private CMapDifficulty campaignDifficulty = CMapDifficulty.NORMAL;
	private CampaignMenuUI campaignRootMenuUI;
	private CampaignMenuUI currentMissionSelectMenuUI;
	/** The campaign a launcher started a mission of, else null. */
	private CampaignCatalog.Entry startedEntry;

	MenuCampaigns(final MenuUI menu) {
		this.menu = menu;
	}

	/** The game data's campaign list, which the campaign progress numbers its campaigns by. */
	void loadCatalog() {
		final CampaignCatalog campaignCatalog;
		try {
			campaignCatalog = CampaignCatalog.load(this.menu.dataSource,
					this.menu.rootFrame.trySkinField("CampaignFile"));
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
		this.campaignCatalog = campaignCatalog;
		this.campaignStrings = campaignCatalog.getStrings();
		this.classicCampaignList = campaignCatalog.isClassicList();
		CCampaignProgress.setCampaignList(campaignCatalog.getList());
	}

	SpriteFrame getFade() {
		return this.campaignFade;
	}

	void setMissionSelectMenuVisible(final boolean visible) {
		// Null when the game data has no campaign list.
		if (this.currentMissionSelectMenuUI != null) {
			this.currentMissionSelectMenuUI.setVisible(visible);
		}
	}

	/** @return false when the game data lists no campaigns */
	boolean create() {
		final GameUI rootFrame = this.menu.rootFrame;
		final Viewport uiViewport = this.menu.uiViewport;
		this.campaignMenu = rootFrame.createFrame("CampaignMenu", rootFrame, 0, 0);
		this.campaignMenu.setVisible(false);
		this.campaignFade = (SpriteFrame) rootFrame.getFrameByName("SlidingDoors", 0);
		// The doors darken the screen behind the campaign menus; left at 4:3 they leave the sides of a wide screen bright.
		this.menu.fillScreenWidth(this.campaignFade);
		this.campaignFade.setVisible(false);
		this.campaignBackButton = (GlueTextButtonFrame) rootFrame.getFrameByName("BackButton", 0);
		this.campaignBackButton.setVisible(false);
		this.missionSelectFrame = rootFrame.getFrameByName("MissionSelectFrame", 0);
		this.missionSelectFrame.setVisible(false);
		final StringFrame missionName = (StringFrame) rootFrame.getFrameByName("MissionName", 0);
		final StringFrame missionNameHeader = (StringFrame) rootFrame.getFrameByName("MissionNameHeader", 0);

		this.campaignSelectFrame = rootFrame.getFrameByName("CampaignSelectFrame", 0);
		this.campaignSelectFrame.setVisible(false);
		this.difficultySelect = (PopupMenuFrame) rootFrame.getFrameByName("DifficultySelect", 0);
		final List<MenuItem> difficultyItems = new ArrayList<>();
		for (final String difficultyKey : CAMPAIGN_DIFFICULTY_KEYS) {
			difficultyItems.add(new MenuItem(rootFrame.getTemplates().getDecoratedString(difficultyKey), -2));
		}
		((MenuFrame) this.difficultySelect.getPopupMenuFrame()).setItems(uiViewport, difficultyItems);
		final StringFrame difficultyTitle = (StringFrame) ((GlueTextButtonFrame) this.difficultySelect
				.getPopupTitleFrame()).getButtonText();
		int startIndex = 0;
		final int scriptDefault = CCampaignProgress.getDefaultDifficulty();
		for (int i = 0; i < CAMPAIGN_DIFFICULTIES.length; i++) {
			if (CAMPAIGN_DIFFICULTIES[i].ordinal() == scriptDefault) {
				startIndex = i;
				this.campaignDifficulty = CAMPAIGN_DIFFICULTIES[i];
			}
		}
		rootFrame.setDecoratedText(difficultyTitle, CAMPAIGN_DIFFICULTY_KEYS[startIndex]);
		this.difficultySelect.setMenuClickListener((button, menuItemIndex) -> {
			if ((menuItemIndex >= 0) && (menuItemIndex < CAMPAIGN_DIFFICULTY_KEYS.length)) {
				this.campaignDifficulty = CAMPAIGN_DIFFICULTIES[menuItemIndex];
				rootFrame.setDecoratedText(difficultyTitle, CAMPAIGN_DIFFICULTY_KEYS[menuItemIndex]);
			}
		});

		this.campaignWarcraftIIILogo = (SpriteFrame) rootFrame.getFrameByName("WarCraftIIILogo", 0);
		rootFrame.setSpriteFrameModel(this.campaignWarcraftIIILogo, rootFrame.getSkinField("MainMenuLogo"));
		this.campaignWarcraftIIILogo.setVisible(false);
		this.campaignWarcraftIIILogo.addSetPoint(new SetPoint(FramePoint.TOPRIGHT, this.campaignMenu,
				FramePoint.TOPRIGHT, GameUI.convertX(uiViewport, -0.13f), GameUI.convertY(uiViewport, -0.08f)));
		this.campaignRootMenuUI = createCampaignMenuUI();
		this.campaignRootMenuUI.setVisible(false);
		placeCampaignMenuUI(this.campaignRootMenuUI);

		this.campaignBackButton.setOnClick(() -> {
			if (this.menu.menuState == MenuState.MISSION_SELECT) {
				setMissionSelectMenuVisible(false);
				this.missionSelectFrame.setVisible(false);
				this.menu.menuState = MenuState.CAMPAIGN;
			}
			else {
				hideScreens();
				this.difficultySelect.getPopupMenuFrame().setVisible(false);
				this.campaignFade.setSequence("Birth");
				this.menu.menuState = MenuState.LEAVING_CAMPAIGN;
			}
		});
		final Element campaignIndex = this.campaignStrings.get("Index");
		if (campaignIndex == null) {
			return false;
		}
		// Read entry by entry: the joined field string drops the blank entries at the start of the list.
		final String[] campaignList = campaignIndex.getFieldAsList("CampaignList").toArray(new String[0]);
		final CampaignMenuData[] campaignDatas = new CampaignMenuData[campaignList.length];
		for (int i = 0; i < campaignList.length; i++) {
			final String campaign = campaignList[i];
			final Element campaignElement = this.campaignStrings.get(campaign);
			if (campaignElement != null) {
				final CampaignMenuData newCampaign = new CampaignMenuData(campaignElement);
				campaignDatas[i] = newCampaign;
				if (this.currentCampaign == null) {
					this.currentCampaign = newCampaign;
				}

			}
		}
		// Scripts number the campaigns by their place among the list's named entries (SetMissionAvailable,
		// SetCampaignAvailable); a blank entry is a gap between groups of campaigns on the screen.
		int campaignNumber = 0;
		for (final CampaignMenuData campaign : campaignDatas) {
			if (campaign == null) {
				this.campaignRootMenuUI.addGap();
			}
			else {
				final int number = campaignNumber;
				final List<CampaignButtonUI> missionButtons = new ArrayList<>();
				final CampaignMenuUI missionSelectMenuUI = createCampaignMenuUI();
				missionSelectMenuUI.setVisible(false);
				placeCampaignMenuUI(missionSelectMenuUI);

				for (final CampaignMission mission : campaign.getMissions()) {
					missionButtons.add(missionSelectMenuUI.addButton(mission.getHeader(), mission.getMissionName(),
							() -> startMission(number, mission, false)));
				}

				final CampaignButtonUI campaignButtonUI = this.campaignRootMenuUI.addButton(campaign.getHeader(),
						campaign.getName(), () -> {
							this.difficultySelect.getPopupMenuFrame().setVisible(false);
							if (campaign != this.currentCampaign) {
								hideScreens();
								this.campaignFade.setSequence("Birth");
								this.currentCampaign = campaign;
								this.currentMissionSelectMenuUI = missionSelectMenuUI;
								this.menu.menuState = MenuState.GOING_TO_MISSION_SELECT;
							}
							else {
								this.campaignSelectFrame.setVisible(false);
								this.campaignRootMenuUI.setVisible(false);
								setMissionSelectMenuVisible(true);
								this.missionSelectFrame.setVisible(true);
								this.menu.menuState = MenuState.MISSION_SELECT;
							}
							rootFrame.setDecoratedText(missionName, campaign.getName());
							rootFrame.setDecoratedText(missionNameHeader, campaign.getHeader());
						});
				this.campaignEntries.add(new CampaignEntry(campaign, campaignNumber++, campaignButtonUI,
						missionSelectMenuUI, missionButtons));
				if (campaign == this.currentCampaign) {
					this.currentMissionSelectMenuUI = missionSelectMenuUI;
				}
			}
		}
		return true;
	}

	private CampaignMenuUI createCampaignMenuUI() {
		return new CampaignMenuUI(null, this.campaignMenu, this.menu.rootFrame, this.menu.uiViewport);
	}

	/** The campaign list and the mission lists take the same place at the right of the screen. */
	private void placeCampaignMenuUI(final CampaignMenuUI menuUI) {
		final Viewport uiViewport = this.menu.uiViewport;
		menuUI.addSetPoint(new SetPoint(FramePoint.TOPRIGHT, this.campaignMenu, FramePoint.TOPRIGHT,
				GameUI.convertX(uiViewport, -0.0f), GameUI.convertY(uiViewport, -0.12f)));
		menuUI.setWidth(GameUI.convertX(uiViewport, 0.30f));
		menuUI.setHeight(GameUI.convertY(uiViewport, 0.42f));
		this.menu.rootFrame.add(menuUI);
	}

	/** Hides the campaign screens' frames; the mission list shown, the doors and the difficulty menu are left. */
	void hideScreens() {
		this.campaignMenu.setVisible(false);
		this.campaignBackButton.setVisible(false);
		this.missionSelectFrame.setVisible(false);
		this.campaignSelectFrame.setVisible(false);
		this.campaignWarcraftIIILogo.setVisible(false);
		this.campaignRootMenuUI.setVisible(false);
	}

	/** The single player menu's Campaign button. */
	void open() {
		this.menu.setGlueLayersSequence("SinglePlayer Death");
		this.menu.singlePlayerMenu.setVisible(false);
		this.menu.profiles.hidePanel();
		refreshCampaignAvailability();
		this.menu.menuState = MenuState.GOING_TO_CAMPAIGN;
	}

	/**
	 * @param campaignNumber the mission's campaign, by the number scripts know it by
	 * @param reforged       whether the mission is played in the Reforged campaign version, else the Classic one
	 * @return false when the mission's map cannot be started; the campaign screens then stay as they are
	 */
	private boolean startMission(final int campaignNumber, final CampaignMission mission, final boolean reforged) {
		if (!this.menu.mapStart.prepareMapConfigs(mission.getMapFilename())) {
			return false;
		}
		hideScreens();
		setMissionSelectMenuVisible(false);
		this.campaignFade.setSequence("Birth");
		this.difficultySelect.getPopupMenuFrame().setVisible(false);
		this.menu.currentMapConfig.setGameDifficulty(this.campaignDifficulty);
		int localPlayerIndex = -1;
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			final CBasePlayer player = this.menu.currentMapConfig.getPlayer(i);
			if (player.getController() == CMapControl.USER) {
				player.setSlotState(CPlayerSlotState.PLAYING);
				player.setName(this.menu.profileManager.getCurrentProfile());
				if (localPlayerIndex == -1) {
					localPlayerIndex = i;
				}
			}
		}
		final BeginGameInformation beginGameInformation = new BeginGameInformation();
		this.menu.beginGameInformation = beginGameInformation;
		beginGameInformation.gameMapLookup = new CurrentNetGameMapLookupPath(mission.getMapFilename());
		beginGameInformation.localPlayerIndex = localPlayerIndex;
		this.menu.mapStart.setCampaign(campaignNumber, reforged);
		return true;
	}

	boolean startCampaignMission(final int campaignNumber, final int missionIndex, final CMapDifficulty difficulty,
			final boolean reforgedMaps) {
		for (final CampaignEntry entry : this.campaignEntries) {
			if (entry.number == campaignNumber) {
				final List<CampaignMission> missions = entry.data.getMissions();
				if ((missionIndex < 0) || (missionIndex >= missions.size())) {
					return false;
				}
				CampaignMission mission = missions.get(missionIndex);
				final CampaignCatalog.Entry catalogEntry = this.campaignCatalog.getEntry(campaignNumber);
				if (catalogEntry != null) {
					final String mapFilename = this.campaignCatalog.missionMapFilename(catalogEntry, missionIndex,
							reforgedMaps);
					if ((mapFilename != null) && !mapFilename.equals(mission.getMapFilename())) {
						mission = new CampaignMission(mission.getHeader(), mission.getMissionName(), mapFilename);
					}
				}
				this.menu.mainMenuFrame.setVisible(false);
				this.currentCampaign = entry.data;
				this.currentMissionSelectMenuUI = entry.missionMenu;
				if (difficulty != null) {
					this.campaignDifficulty = difficulty;
				}
				this.startedEntry = catalogEntry;
				if (!startMission(campaignNumber, mission, reforgedMaps)) {
					this.menu.mainMenuFrame.setVisible(true);
					return false;
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * A saved game of a campaign's mission goes on as a mission of that campaign, as if the launcher had started it.
	 *
	 * @param campaignNumber by the number scripts know it by; -1 for a game that is no campaign's
	 */
	void setStartedCampaign(final int campaignNumber) {
		this.startedEntry = (campaignNumber >= 0) && (this.campaignCatalog != null)
				? this.campaignCatalog.getEntry(campaignNumber)
				: null;
	}

	/** The campaign a map is a mission of, by the number scripts know it by; -1 when none or more than one lists it. */
	int campaignOfMap(final String mapFile) {
		final CampaignCatalog.Entry entry = CampaignResult.campaignOfMap(this.campaignCatalog, mapFile);
		return entry == null ? -1 : entry.getNumber();
	}

	/** Whether a map is one of the missions of the campaign the launcher started, in either version of its maps. */
	boolean isStartedCampaignMission(final String mapFile) {
		return (this.startedEntry != null)
				&& (CampaignResult.playedMission(this.campaignCatalog, this.startedEntry, mapFile) >= 0);
	}

	/**
	 * A won map that is a mission of the campaign being played is recorded as won: the last missions of some campaigns
	 * open nothing of their own campaign that would tell. A campaign's map won as a custom game is no mission won.
	 */
	void recordVictory(final String mapFile) {
		final CampaignCatalog.Entry entry = this.startedEntry;
		if (entry == null) {
			return;
		}
		final int mission = CampaignResult.playedMission(this.campaignCatalog, entry, mapFile);
		if (mission >= 0) {
			CCampaignProgress.setMissionWon(entry.getNumber(), mission);
		}
	}

	/**
	 * The campaign screens list only what the profile has opened: a campaign that is open by default or made available
	 * by a script, and in it the first mission and those a script made available.
	 */
	private void refreshCampaignAvailability() {
		final int menuCampaignIndex = this.classicCampaignList ? CCampaignProgress.getClassicMenuCampaignIndex()
				: CCampaignProgress.getMenuCampaignIndex();
		CampaignEntry firstOpen = null;
		CampaignEntry menuCampaign = null;
		boolean currentOpen = false;
		for (final CampaignEntry entry : this.campaignEntries) {
			final boolean open = entry.data.isDefaultOpen() || CCampaignProgress.isCampaignAvailable(entry.number);
			entry.button.setVisible(open);
			for (int i = 0; i < entry.missionButtons.size(); i++) {
				entry.missionButtons.get(i)
						.setVisible(open && ((i == 0) || CCampaignProgress.isMissionAvailable(entry.number, i)));
			}
			entry.missionMenu.positionBounds(this.menu.rootFrame, this.menu.uiViewport);
			if (open) {
				if (firstOpen == null) {
					firstOpen = entry;
				}
				if (entry.number == menuCampaignIndex) {
					menuCampaign = entry;
				}
				currentOpen |= entry.data == this.currentCampaign;
			}
		}
		this.campaignRootMenuUI.positionBounds(this.menu.rootFrame, this.menu.uiViewport);
		final CampaignEntry shown = menuCampaign != null ? menuCampaign : (currentOpen ? null : firstOpen);
		if (shown != null) {
			this.currentCampaign = shown.data;
			this.currentMissionSelectMenuUI = shown.missionMenu;
		}
	}

	/** @return whether the state is one of the campaign screens', which it then advances */
	boolean updateMenuState(final MenuState menuState) {
		switch (menuState) {
		case LEAVING_CAMPAIGN:
			this.menu.setGlueLayersSequence("Birth");
			if (this.campaignFade.isVisible()) {
				this.campaignFade.setSequence("Death");
			}
			this.menu.switchToMainMenuGlueScreenLoop();
			this.menu.restoreMainMenuBackground();
			this.menu.menuState = MenuState.GOING_TO_SINGLE_PLAYER;
			return true;
		case GOING_TO_CAMPAIGN:
			this.menu.setGlueLayersSequence("Death");
			this.campaignMenu.setVisible(true);
			this.campaignFade.setVisible(true);
			this.campaignFade.setSequence("Birth");
			this.menu.menuState = MenuState.GOING_TO_CAMPAIGN_PART2;
			return true;
		case GOING_TO_CAMPAIGN_PART2:
			showCampaignBackground();
			this.campaignFade.setSequence("Death");
			this.menu.menuState = MenuState.CAMPAIGN;
			return true;
		case CAMPAIGN:
			this.campaignMenu.setVisible(true);
			this.campaignBackButton.setVisible(true);
			this.campaignWarcraftIIILogo.setVisible(true);
			this.campaignSelectFrame.setVisible(true);
			this.campaignRootMenuUI.setVisible(true);
			return true;
		case GOING_TO_MISSION_SELECT:
			showCampaignBackground();
			this.campaignFade.setSequence("Death");
			this.menu.menuState = MenuState.MISSION_SELECT;
			return true;
		case MISSION_SELECT:
			this.campaignMenu.setVisible(true);
			this.campaignBackButton.setVisible(true);
			this.campaignWarcraftIIILogo.setVisible(true);
			setMissionSelectMenuVisible(true);
			this.missionSelectFrame.setVisible(true);
			return true;
		default:
			return false;
		}
	}

	/** Back on the campaign screens from a mission. */
	void onReturnFromGame(final boolean forceCampaignSelect) {
		// The mission may have opened missions or campaigns and picked the campaign the menu shows.
		setMissionSelectMenuVisible(false);
		refreshCampaignAvailability();
		if (forceCampaignSelect) {
			this.missionSelectFrame.setVisible(false);
			this.menu.menuState = MenuState.CAMPAIGN;
		}
		final GameUI rootFrame = this.menu.rootFrame;
		rootFrame.setDecoratedText((StringFrame) rootFrame.getFrameByName("MissionName", 0),
				this.currentCampaign.getName());
		rootFrame.setDecoratedText((StringFrame) rootFrame.getFrameByName("MissionNameHeader", 0),
				this.currentCampaign.getHeader());
		showCampaignBackground();
	}

	/** The current campaign's background model, ambient sound and race cursor. */
	private void showCampaignBackground() {
		final GameUI rootFrame = this.menu.rootFrame;
		final String currentCampaignBackgroundModel = getCurrentBackgroundModel();
		final String currentCampaignAmbientSound = rootFrame.trySkinField(this.currentCampaign.getAmbientSound());
		this.menu.menuScreen.setModel(currentCampaignBackgroundModel, this.currentCampaign.getBackgroundFogSettings());
		this.menu.switchGlueScreenLoop(currentCampaignAmbientSound);
		final DataTable skinData = rootFrame.getSkinData();
		final String cursorSkin = getRaceNameByCursorID(this.currentCampaign.getCursor());
		rootFrame.setSpriteFrameModel(this.menu.cursorFrame, skinData.get(cursorSkin).getField("Cursor"));
	}

	private static String getRaceNameByCursorID(final int cursorId) {
		final CRaceManagerEntry raceEntry = WarsmashConstants.RACE_MANAGER.get(getRaceByCursorID(cursorId));
		return raceEntry.getKey();
	}

	private static CRace getRaceByCursorID(final int cursorId) {
		final int raceId = cursorId + 1;
		final CRace race = WarsmashConstants.RACE_MANAGER.getRace(raceId);
		if (race == null) {
			return WarsmashConstants.RACE_MANAGER.getRace(1); // when in doubt, default to human
		}
		return race;
	}

	private String getCurrentBackgroundModel() {
		return this.menu.rootFrame.getSkinField(this.currentCampaign.getBackground());
	}

	private static final class CampaignEntry {
		private final CampaignMenuData data;
		private final int number;
		private final CampaignButtonUI button;
		private final CampaignMenuUI missionMenu;
		private final List<CampaignButtonUI> missionButtons;

		private CampaignEntry(final CampaignMenuData data, final int number, final CampaignButtonUI button,
				final CampaignMenuUI missionMenu, final List<CampaignButtonUI> missionButtons) {
			this.data = data;
			this.number = number;
			this.button = button;
			this.missionMenu = missionMenu;
			this.missionButtons = missionButtons;
		}
	}
}
