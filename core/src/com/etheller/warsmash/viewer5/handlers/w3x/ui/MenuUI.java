package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.SingleModelScreen;
import com.etheller.warsmash.WarsmashGdxMenuScreen;
import com.etheller.warsmash.WarsmashGdxMultiScreenGame;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.input.touch.VirtualCursor;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.frames.BackdropFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.ListBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.MenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.ScrollBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.parsers.jass.Jass2.RootFrameListener;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.custom.WTS;
import com.etheller.warsmash.util.DataSourceFileHandle;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.StringBundle;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.util.WorldEditStrings;
import com.etheller.warsmash.viewer5.FogSettings;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.GameReplays;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplay;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.FocusableFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.DialogWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.sound.KeyedSounds;

import net.warsmash.uberserver.GamingNetworkConnection;
import net.warsmash.uberserver.LobbyPlayerType;

/**
 * The glue menu: the main menu and the single player menu with their dialogs, the input and music of the menu
 * screens, and the parts that drive the other screens (online service, campaigns, custom game, profiles, loading
 * screen, game start).
 */
public class MenuUI {
	private static final Vector2 screenCoordsVector = new Vector2();
	private static final boolean ENABLE_NOT_YET_IMPLEMENTED_BUTTONS = false;
	public static final int MAIN_PHASES = 4;

	final DataSource dataSource;
	final Scene uiScene;
	final Viewport uiViewport;
	final MdxViewer viewer;
	private final RootFrameListener rootFrameListener;
	private final float heightRatioCorrection;
	GameUI rootFrame;
	SpriteFrame cursorFrame;

	private ClickableFrame mouseDownUIFrame;
	private ClickableFrame mouseOverUIFrame;
	private FocusableFrame focusUIFrame;

	UIFrame mainMenuFrame;

	SpriteFrame glueSpriteLayerTopRight;

	SpriteFrame glueSpriteLayerTopLeft;
	SpriteFrame glueSpriteLayerCenter;

	private final WorldEditStrings worldEditStrings;

	private KeyedSounds uiSounds;

	private GlueTextButtonFrame singlePlayerButton;
	private GlueTextButtonFrame battleNetButton;
	private GlueTextButtonFrame localAreaNetworkButton;
	private GlueTextButtonFrame optionsButton;
	private GlueTextButtonFrame creditsButton;
	private GlueButtonFrame realmButton;
	private GlueTextButtonFrame exitButton;

	MenuState menuState;

	UIFrame singlePlayerMenu;

	private GlueButtonFrame profileButton;
	private GlueTextButtonFrame campaignButton;
	private GlueTextButtonFrame loadSavedButton;
	private SavedGameDialog savedGameDialog;
	private SimpleFrame savedGameHolder;
	private GameOptionsDialog optionsDialog;
	private GlueTextButtonFrame viewReplayButton;
	private GlueTextButtonFrame customCampaignButton;
	private GlueTextButtonFrame skirmishButton;
	private GlueTextButtonFrame singlePlayerCancelButton;
	private GlueButtonFrame editionButton;

	final WarsmashGdxMultiScreenGame screenManager;

	final DataTable warsmashIni;

	private UnitSound glueScreenLoop;

	private SpriteFrame warcraftIIILogo;
	final SingleModelScreen menuScreen;

	private UnitSound mainMenuGlueScreenLoop;
	final PlayerProfileManager profileManager;
	/** A launcher shows its own menu: the glue menu is never shown, only the loading screens of the games. */
	private boolean launcherMode;
	/** Game data the menu's owner keeps open for the maps; null opens it again for every map. */
	DataSource gameData;
	BeginGameInformation beginGameInformation;
	War3MapConfig currentMapConfig;
	private final DataTable musicSLK;
	private Music[] currentMusics;
	private int currentMusicIndex;
	private boolean currentMusicRandomizeIndex;
	DialogWar3 dialog;
	private boolean hideUI;
	private FogSettings menuFogSettings;

	final MenuBattleNet battleNet;
	final MenuProfiles profiles;
	final MenuSkirmish skirmish;
	final MenuCampaigns campaigns;
	final MenuLoadingScreen loadingScreen;
	final MenuMapStart mapStart;

	public MenuUI(final DataSource dataSource, final Viewport uiViewport, final Scene uiScene, final MdxViewer viewer,
			final WarsmashGdxMultiScreenGame screenManager, final SingleModelScreen menuScreen,
			final DataTable warsmashIni, final RootFrameListener rootFrameListener,
			final GamingNetworkConnection gamingNetworkConnection, final String mapDownloadDir) {
		this.dataSource = dataSource;
		this.worldEditStrings = new WorldEditStrings(dataSource);
		this.uiViewport = uiViewport;
		this.uiScene = uiScene;
		this.viewer = viewer;
		this.screenManager = screenManager;
		this.menuScreen = menuScreen;
		this.warsmashIni = warsmashIni;
		this.rootFrameListener = rootFrameListener;

		this.heightRatioCorrection = getMinWorldHeight() / 1200f;

		this.profileManager = PlayerProfileManager.loadFromGdx();

		this.musicSLK = new DataTable(StringBundle.EMPTY);
		final String musicSLKPath = "UI\\SoundInfo\\Music.SLK";
		if (viewer.dataSource.has(musicSLKPath)) {
			try (InputStream miscDataTxtStream = viewer.dataSource.getResourceAsStream(musicSLKPath)) {
				this.musicSLK.readSLK(miscDataTxtStream);
			}
			catch (final IOException e) {
				Log.error(e);
			}
		}
		final DataTable miscData = new DataTable(this.worldEditStrings);
		try (InputStream miscDataTxtStream = this.dataSource.getResourceAsStream("UI\\MiscData.txt")) {
			miscData.readTXT(miscDataTxtStream, true);
		}
		catch (final IOException e) {
			Log.error(e);
		}
		final Element zFogElement = miscData.get("MenuZFog");
		if (zFogElement != null) {
			this.menuFogSettings = FogSettings.parse(zFogElement, WarsmashConstants.GAME_VERSION);
		}

		this.battleNet = new MenuBattleNet(this, gamingNetworkConnection);
		this.profiles = new MenuProfiles(this, this.profileManager);
		this.skirmish = new MenuSkirmish(this);
		this.campaigns = new MenuCampaigns(this);
		this.loadingScreen = new MenuLoadingScreen(this);
		this.mapStart = new MenuMapStart(this);
		gamingNetworkConnection.addListener(new MenuBattleNetServerListener(this, this.battleNet,
				gamingNetworkConnection, dataSource, mapDownloadDir));
	}

	public float getHeightRatioCorrection() {
		return this.heightRatioCorrection;
	}

	/**
	 * Called "main" because this was originally written in JASS so that maps could
	 * override it, and I may convert it back to the JASS at some point.
	 */
	public void main() {
		for (int phase = 0; phase < MAIN_PHASES; phase++) {
			mainPhase(phase);
		}
	}

	/**
	 * One part of {@link #main()}, so a caller can draw progress between the parts.
	 */
	public void mainPhase(final int phase) {
		switch (phase) {
		case 0:
			mainSkins();
			break;
		case 1:
			mainMenus();
			break;
		case 2:
			mainSkirmish();
			break;
		default:
			mainDialogs();
			break;
		}
	}

	private void mainSkins() {
		// =================================
		// Load skins and templates
		// =================================
		this.rootFrame = new GameUI(this.dataSource, GameUI.loadSkin(this.dataSource, WarsmashConstants.GAME_VERSION),
				this.uiViewport, this.uiScene, this.viewer, 0, WTS.DO_NOTHING);
		this.rootFrame.setWideRoot(true);

		if (!this.launcherMode) {
			this.menuScreen.setModel(this.rootFrame.getSkinField("GlueSpriteLayerBackground"), this.menuFogSettings);
		}
		this.rootFrameListener.onCreate(this.rootFrame);
		try {
			this.rootFrame.loadTOCFile("UI\\FrameDef\\FrameDef.toc");
		}
		catch (final IOException exc) {
			throw new IllegalStateException("Unable to load FrameDef.toc", exc);
		}
		try {
			this.rootFrame.loadTOCFile("UI\\FrameDef\\SmashFrameDef.toc");
		}
		catch (final IOException exc) {
			throw new IllegalStateException("Unable to load SmashFrameDef.toc", exc);
		}

		this.campaigns.loadCatalog();
	}

	private void mainMenus() {
		// Create main menu
		this.mainMenuFrame = this.rootFrame.createFrame("MainMenuFrame", this.rootFrame, 0, 0);

		this.warcraftIIILogo = (SpriteFrame) this.rootFrame.getFrameByName("WarCraftIIILogo", 0);
		this.rootFrame.setSpriteFrameModel(this.warcraftIIILogo, this.rootFrame.getSkinField("MainMenuLogo"));
		this.warcraftIIILogo.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.mainMenuFrame, FramePoint.TOPLEFT,
				GameUI.convertX(this.uiViewport, 0.13f), GameUI.convertY(this.uiViewport, -0.08f)));
		setMainMenuVisible(false);
		this.rootFrame.getFrameByName("RealmSelect", 0).setVisible(false);
		// 2.0 and later menus carry an age rating badge that the game shows only in the Korean region.
		final UIFrame ratingsLogo = this.rootFrame.getFrameByName("RatingsLogo", 0);
		if (ratingsLogo != null) {
			ratingsLogo.setVisible(false);
		}

		this.glueSpriteLayerTopRight = (SpriteFrame) this.rootFrame.createFrameByType("SPRITE",
				"SmashGlueSpriteLayerTopRight", this.rootFrame, "", 0);
		anchorGlueLayer(this.glueSpriteLayerTopRight, FramePoint.TOPRIGHT);
		final String topRightModel = this.rootFrame.getSkinField("GlueSpriteLayerTopRight");
		this.rootFrame.setSpriteFrameModel(this.glueSpriteLayerTopRight, topRightModel);
		this.glueSpriteLayerTopRight.setSequence("MainMenu Birth");

		this.glueSpriteLayerTopLeft = (SpriteFrame) this.rootFrame.createFrameByType("SPRITE",
				"SmashGlueSpriteLayerTopLeft", this.rootFrame, "", 0);
		anchorGlueLayer(this.glueSpriteLayerTopLeft, FramePoint.TOPLEFT);
		final String topLeftModel = this.rootFrame.getSkinField("GlueSpriteLayerTopLeft");
		this.rootFrame.setSpriteFrameModel(this.glueSpriteLayerTopLeft, topLeftModel);
		this.glueSpriteLayerTopLeft.setSequence("MainMenu Birth");

		this.glueSpriteLayerCenter = (SpriteFrame) this.rootFrame.createFrameByType("SPRITE",
				"SmashGlueSpriteLayerCenter", this.rootFrame, "", 0);
		anchorGlueLayer(this.glueSpriteLayerCenter, FramePoint.CENTER);
		final String centerModel = this.rootFrame.getSkinField("GlueSpriteLayerCenter");
		this.rootFrame.setSpriteFrameModel(this.glueSpriteLayerCenter, centerModel);
		this.glueSpriteLayerCenter.setVisible(false);
		// A previewed loading screen goes straight on to its map; the menu behind it is never shown.
		if (this.launcherMode || this.loadingScreen.isPreviewing()) {
			this.glueSpriteLayerTopRight.setVisible(false);
			this.glueSpriteLayerTopLeft.setVisible(false);
		}

		this.cursorFrame = (SpriteFrame) this.rootFrame.createFrameByType("SPRITE", "SmashCursorFrame", this.rootFrame,
				"", 0);
		this.rootFrame.setSpriteFrameModel(this.cursorFrame, this.rootFrame.getSkinField("Cursor"));
		this.cursorFrame.setSequence("Normal");
		this.cursorFrame.setZDepth(1024.0f);
		if (WarsmashConstants.CATCH_CURSOR) {
			Gdx.input.setCursorCatched(true);
		}

		this.battleNet.createConnectDialog();

		// Main Menu interactivity
		this.singlePlayerButton = (GlueTextButtonFrame) this.rootFrame.getFrameByName("SinglePlayerButton", 0);
		this.battleNetButton = (GlueTextButtonFrame) this.rootFrame.getFrameByName("BattleNetButton", 0);
		this.realmButton = (GlueButtonFrame) this.rootFrame.getFrameByName("RealmButton", 0);
		this.localAreaNetworkButton = (GlueTextButtonFrame) this.rootFrame.getFrameByName("LocalAreaNetworkButton", 0);
		this.optionsButton = (GlueTextButtonFrame) this.rootFrame.getFrameByName("OptionsButton", 0);
		this.creditsButton = (GlueTextButtonFrame) this.rootFrame.getFrameByName("CreditsButton", 0);
		this.exitButton = (GlueTextButtonFrame) this.rootFrame.getFrameByName("ExitButton", 0);
		this.editionButton = (GlueButtonFrame) this.rootFrame.getFrameByName("EditionButton", 0);

		if (this.editionButton != null) {
			if (WarsmashConstants.GAME_VERSION == 0) {
				// The shipped FDF only describes the expansion menu, whose button offers the switch back to RoC.
				for (final String[] state : new String[][] { { "", "up" }, { "Pushed", "down" },
						{ "Disabled", "disabled" } }) {
					final UIFrame backdrop = this.rootFrame.getFrameByName("EditionButton" + state[0] + "BackdropTemplate",
							0);
					if (backdrop instanceof BackdropFrame) {
						((BackdropFrame) backdrop).setBackground(this.rootFrame
								.loadTexture("UI\\Widgets\\Glues\\GlueScreen-TFT-EditionButton-" + state[1] + ".blp"));
					}
				}
			}
			this.editionButton.setOnClick(() -> {
				WarsmashConstants.GAME_VERSION = WarsmashConstants.GAME_VERSION == 1 ? 0 : 1;
				leaveMainMenu(MenuState.RESTARTING);
			});
		}

		this.localAreaNetworkButton.setEnabled(true);
		GameOptions.get().apply();
		this.optionsButton.setEnabled(true);
		this.optionsButton.setOnClick(this::showOptions);
		this.creditsButton.setEnabled(false);

		this.exitButton.setOnClick(() -> leaveMainMenu(MenuState.QUITTING));
		this.singlePlayerButton.setOnClick(() -> leaveMainMenu(MenuState.GOING_TO_SINGLE_PLAYER));
		this.battleNetButton.setOnClick(this.battleNet::connect);
		this.realmButton.setOnClick(this.battleNet::showConnectDialog);

		// Create single player
		this.singlePlayerMenu = this.rootFrame.createFrame("SinglePlayerMenu", this.rootFrame, 0, 0);
		this.singlePlayerMenu.setVisible(false);

		this.profiles.create();

		// Single Player Interactivity
		this.profileButton = (GlueButtonFrame) this.rootFrame.getFrameByName("ProfileButton", 0);
		this.campaignButton = (GlueTextButtonFrame) this.rootFrame.getFrameByName("CampaignButton", 0);
		this.loadSavedButton = (GlueTextButtonFrame) this.rootFrame.getFrameByName("LoadSavedButton", 0);
		this.loadSavedButton.setOnClick(this::showSavedGames);
		this.viewReplayButton = (GlueTextButtonFrame) this.rootFrame.getFrameByName("ViewReplayButton", 0);
		this.viewReplayButton.setOnClick(this::showReplays);
		this.customCampaignButton = (GlueTextButtonFrame) this.rootFrame.getFrameByName("CustomCampaignButton", 0);
		this.skirmishButton = (GlueTextButtonFrame) this.rootFrame.getFrameByName("SkirmishButton", 0);

		this.singlePlayerCancelButton = (GlueTextButtonFrame) this.rootFrame.getFrameByName("CancelButton", 0);

		this.profiles.createNameText();

		setSinglePlayerButtonsEnabled(true);

		this.profileButton.setOnClick(this.profiles::open);
		this.campaignButton.setOnClick(this.campaigns::open);
		this.skirmishButton.setOnClick(this.skirmish::open);

		this.singlePlayerCancelButton.setOnClick(() -> {
			if (this.menuState == MenuState.SINGLE_PLAYER_PROFILE) {
				this.glueSpriteLayerTopLeft.setSequence("RealmSelection Death");
				this.profiles.hidePanel();
			}
			else {
				this.glueSpriteLayerTopLeft.setSequence("SinglePlayer Death");
			}
			this.glueSpriteLayerTopRight.setSequence("SinglePlayer Death");
			this.singlePlayerMenu.setVisible(false);
			this.menuState = MenuState.GOING_TO_MAIN_MENU;
		});

	}

	/** A main menu button that leaves the main menu: its panels close, then the state follows. */
	private void leaveMainMenu(final MenuState nextState) {
		setGlueLayersSequence("MainMenu Death");
		setMainMenuVisible(false);
		this.menuState = nextState;
	}

	private void mainSkirmish() {
		this.skirmish.create();
		if (!this.campaigns.create()) {
			this.campaignButton.setEnabled(false);
		}
	}

	private void mainDialogs() {
		final UIFrame confirmDialog = this.rootFrame.createFrame("DialogWar3", this.rootFrame, 0, 0);
		confirmDialog.setVisible(false);

		this.loadingScreen.create();

		this.battleNet.createBattleNetUI();

		this.dialog = new DialogWar3(this.rootFrame, this.uiViewport);

		// position all
		this.rootFrame.positionBounds(this.rootFrame, this.uiViewport);

		this.menuState = MenuState.GOING_TO_MAIN_MENU;

		loadSounds();

		try {
			final String glueLoopField = this.rootFrame.getSkinField("GlueScreenLoop");
			this.mainMenuGlueScreenLoop = this.uiSounds.getSound(glueLoopField);
		}
		catch (final Exception exc) {
			Log.error(exc);
			this.mainMenuGlueScreenLoop = new UnitSound(null, 0, 0, 0, 0, 0, 0, false);
		}
		this.glueScreenLoop = this.mainMenuGlueScreenLoop;
		if (!this.launcherMode) {
			this.glueScreenLoop.play(this.uiScene.audioContext, 0f, 0f, 0f);
		}
	}

	public void show() {
		if (this.launcherMode) {
			return;
		}
		playMusic(this.rootFrame.trySkinField("GlueMusic"), true, 0);
		this.glueScreenLoop.play(this.uiScene.audioContext, 0f, 0f, 0f);
	}

	/** The profile a launcher starts the game with, for a loading screen previewed before it is taken on. */
	public void setStartupProfile(final String profile) {
		this.loadingScreen.setStartupProfile(profile);
	}

	/**
	 * Told the file of a map that cannot be loaded (a level a script changes to, say), before the menu shows the error
	 * itself.
	 *
	 * @param listener returns whether it took care of the failure (a launcher showing its own screen)
	 */
	public void setMapLoadFailureListener(final Predicate<String> listener) {
		this.mapStart.setMapLoadFailureListener(listener);
	}

	/** The menu with the error dialog, for a map that could not be started or loaded. */
	public void showMapLoadError() {
		this.mapStart.showMapLoadError();
	}

	/** Must be set before the menu is built. */
	public void setLauncherMode(final boolean launcherMode) {
		this.launcherMode = launcherMode;
	}

	/** Game data the maps read instead of opening their own; a finished map's close leaves it open. */
	public void setGameData(final DataSource gameData) {
		this.gameData = gameData;
	}

	/**
	 * Shows the loading screen of the map a launcher starts while the rest of the menu is still being built, so that
	 * the start shows the map's loading screen from its first frames; the game then goes on loading on the same
	 * screen. The players of a melee screen are only listed once the game is set up.
	 *
	 * @param startupSlots the launcher's slots as {@link #startSkirmish} takes them, for the local player's race; or
	 *                     null
	 * @return false when the map cannot be read
	 */
	public boolean previewLoadingScreen(final String mapFilename, final String startupSlots) {
		return this.loadingScreen.preview(mapFilename, startupSlots);
	}

	/** While the loading screen is previewed: how much of the menu is built. */
	public void setPreviewProgress(final float ratio) {
		this.loadingScreen.setPreviewProgress(ratio);
	}

	/**
	 * A loaded map waits on its loading screen until the player taps (or presses a key), the touch counterpart of the
	 * original's "Press any key to continue"; the game clock does not start before.
	 */
	public void setWaitForTap(final boolean waitForTap) {
		this.loadingScreen.setWaitForTap(waitForTap);
	}

	/**
	 * The Reforged interface skin: its loading screens, and in a game its interface art when the game data has it.
	 * Must be set before the loading screen of a game is filled.
	 */
	public void setReforgedLoadingScreens(final boolean reforgedLoadingScreens) {
		this.loadingScreen.setReforgedLoadingScreens(reforgedLoadingScreens);
	}

	/**
	 * @param portrait the number of the local player's portrait among the Reforged web interface's portraits (from
	 *                 1), or 0 for the default one; must be set before the loading screen of a game is filled
	 */
	public void setReforgedPortrait(final int portrait) {
		this.loadingScreen.setReforgedPortrait(portrait);
	}

	/**
	 * Starts a mission as its button on the mission screen does, for a launcher that shows the campaigns itself.
	 *
	 * @param campaignNumber the campaign's number among the named entries of the campaign list, as scripts count
	 * @return false when the game data has no such mission
	 */
	public boolean startCampaignMission(final int campaignNumber, final int missionIndex,
			final CMapDifficulty difficulty) {
		return startCampaignMission(campaignNumber, missionIndex, difficulty, false);
	}

	/**
	 * @param reforgedMaps the campaign screen's version choice: the Reforged maps of the missions where the data has
	 *                     them and the Reforged graphics, else the classic maps and graphics
	 */
	public boolean startCampaignMission(final int campaignNumber, final int missionIndex,
			final CMapDifficulty difficulty, final boolean reforgedMaps) {
		return this.campaigns.startCampaignMission(campaignNumber, missionIndex, difficulty, reforgedMaps);
	}

	public boolean isLoadingScreenShown() {
		return this.loadingScreen.isShown();
	}

	/** Whether a map is one of the missions of the campaign {@link #startCampaignMission} started. */
	public boolean isStartedCampaignMission(final String mapFile) {
		return this.campaigns.isStartedCampaignMission(mapFile);
	}

	/** Records a won map that is a campaign's mission as won. */
	public void recordVictory(final String mapFile) {
		this.campaigns.recordVictory(mapFile);
	}

	/**
	 * Starts a custom game as the custom game screen does after the given choices, for a launcher that shows its own
	 * screen: the map is selected in the list, the slots and options are set through the screen's own controls and
	 * Start Game is pressed.
	 *
	 * @param slots   "slot:type:race:team:color" entries separated by ";", where type is a {@link LobbyPlayerType}
	 *                name, race the race menu's item (0 random), team the team's index and color the player colour's
	 *                number, -1 leaving any of them as the map has it
	 * @param options the Advanced Options that are on, separated by ",": lockTeams, teamsTogether, sharedControl,
	 *                randomRaces, randomHero; null keeps the screen's defaults
	 * @return false when the map is not in the list or cannot be read
	 */
	public boolean startSkirmish(final String mapPath, final String slots, final String options) {
		return this.skirmish.startSkirmish(mapPath, slots, options);
	}

	public boolean startMap(final String mapFilename) {
		return startMap(mapFilename, null);
	}

	/**
	 * The save the next map started puts in place of the map's start, or null.
	 *
	 * @param byScript whether the map's own LoadGame asked for it
	 */
	public void setPendingLoad(final FileHandle savedGame, final boolean byScript) {
		this.mapStart.setPendingLoad(savedGame, byScript);
	}

	/**
	 * @param computerSlots comma separated "slot:race" pairs that are played by a normal computer, or null
	 */
	public boolean startMap(final String mapFilename, final String computerSlots) {
		return this.mapStart.startMap(mapFilename, computerSlots);
	}

	/**
	 * Plays a replay: its map with the recorded game setup and seed, its commands in their steps. A replay of another
	 * engine version or map is refused with the reason.
	 */
	public boolean startReplay(final FileHandle file) {
		return this.mapStart.startReplay(file, 0, false);
	}

	/**
	 * @param startTick the step the replay plays on to without drawing before it is shown
	 * @param forced    play it although this engine would refuse it (another version, map or game data); it may
	 *                  desync
	 */
	public boolean startReplay(final FileHandle file, final int startTick, final boolean forced) {
		return this.mapStart.startReplay(file, startTick, forced);
	}

	/** The profile a launcher chose for the game it starts: saves and campaign progress go to its folder. */
	public void useProfile(final String profileName) {
		this.profiles.useProfile(profileName);
	}

	protected void setSinglePlayerButtonsEnabled(final boolean b) {
		this.profileButton.setEnabled(b);
		this.campaignButton.setEnabled(b);
		this.loadSavedButton.setEnabled(b);
		this.viewReplayButton.setEnabled(b);
		if (this.customCampaignButton != null) {
			this.customCampaignButton.setEnabled(b && ENABLE_NOT_YET_IMPLEMENTED_BUTTONS);
		}
		this.skirmishButton.setEnabled(b);
		this.singlePlayerCancelButton.setEnabled(b);
	}

	void setMainMenuButtonsEnabled(final boolean b) {
		this.singlePlayerButton.setEnabled(b);
		this.battleNetButton.setEnabled(b);
		this.realmButton.setEnabled(b);
		this.localAreaNetworkButton.setEnabled(b && ENABLE_NOT_YET_IMPLEMENTED_BUTTONS);
		this.optionsButton.setEnabled(b);
		this.creditsButton.setEnabled(b && ENABLE_NOT_YET_IMPLEMENTED_BUTTONS);
		this.exitButton.setEnabled(b);
		if (this.editionButton != null) {
			this.editionButton.setEnabled(b);
		}
	}

	void setMainMenuVisible(final boolean visible) {
		this.mainMenuFrame.setVisible(visible);
		this.warcraftIIILogo.setVisible(visible);
	}

	/** The top left and top right glue layers play the same sequence. */
	void setGlueLayersSequence(final String sequence) {
		this.glueSpriteLayerTopLeft.setSequence(sequence);
		this.glueSpriteLayerTopRight.setSequence(sequence);
	}

	/** The main menu's background model and cursor, after a campaign's or a map's. */
	void restoreMainMenuBackground() {
		this.menuScreen.setModel(this.rootFrame.getSkinField("GlueSpriteLayerBackground"), this.menuFogSettings);
		this.rootFrame.setSpriteFrameModel(this.cursorFrame, this.rootFrame.getSkinField("Cursor"));
	}

	void switchToMainMenuGlueScreenLoop() {
		this.glueScreenLoop.stop();
		this.glueScreenLoop = this.mainMenuGlueScreenLoop;
		this.glueScreenLoop.play(this.uiScene.audioContext, 0f, 0f, 0f);
	}

	/** @param soundKey the ambient sound that loops in place of the current one */
	void switchGlueScreenLoop(final String soundKey) {
		this.glueScreenLoop.stop();
		this.glueScreenLoop = this.uiSounds.getSound(soundKey);
		this.glueScreenLoop.play(this.uiScene.audioContext, 0f, 0f, 0f);
	}

	/**
	 * Glue layer models are authored for a 4:3 screen, so each keeps a 4:3 frame pinned to the screen side it
	 * decorates; on a wide screen the side panels then meet the edges where their buttons are.
	 */
	void anchorGlueLayer(final UIFrame layer, final FramePoint point) {
		layer.setWidth(GameUI.convertX(this.uiViewport, 0.8f));
		layer.setHeight(GameUI.convertY(this.uiViewport, 0.6f));
		layer.addSetPoint(new SetPoint(point, this.rootFrame, point, 0, 0));
	}

	/**
	 * Scales a 4:3 backdrop model up until it covers the full screen width, keeping its proportions; the top and
	 * bottom are cut off instead of leaving empty bars at the sides.
	 */
	void fillScreenWidth(final SpriteFrame backdrop) {
		placeBackdrop(backdrop, Math.max(1, this.uiViewport.getWorldWidth() / getMinWorldWidth()));
	}

	void placeBackdrop(final SpriteFrame backdrop, final float scale) {
		backdrop.clearFramePointAssignments();
		backdrop.setWidth(GameUI.convertX(this.uiViewport, 0.8f) * scale);
		backdrop.setHeight(GameUI.convertY(this.uiViewport, 0.6f) * scale);
		backdrop.addSetPoint(new SetPoint(FramePoint.CENTER, this.rootFrame, FramePoint.CENTER, 0, 0));
		backdrop.setModelScale(scale);
		backdrop.positionBounds(this.rootFrame, this.uiViewport);
	}

	public void resize() {
		this.rootFrame.updateRootBounds();
		this.rootFrame.positionBounds(this.rootFrame, this.uiViewport);
	}

	public void render(final SpriteBatch batch, final GlyphLayout glyphLayout) {
		if (!this.hideUI) {
			final BitmapFont font20 = this.rootFrame.getFont20();
			if (WarsmashConstants.SHOW_FPS) {
				final BitmapFont font14 = this.rootFrame.getFont14();
				font14.setColor(Color.YELLOW);
				final Rectangle screen = this.rootFrame.getRenderBounds();
				font14.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), screen.x + 8,
						(screen.y + screen.height) - 8);
			}
			if (!this.loadingScreen.render(batch, font20, glyphLayout)) {
				this.rootFrame.render(batch, font20, glyphLayout);
			}
		}
	}

	private float getMinWorldWidth() {
		if (this.uiViewport instanceof ExtendViewport) {
			return ((ExtendViewport) this.uiViewport).getMinWorldWidth();
		}
		return this.uiViewport.getWorldWidth();
	}

	private float getMinWorldHeight() {
		if (this.uiViewport instanceof ExtendViewport) {
			return ((ExtendViewport) this.uiViewport).getMinWorldHeight();
		}
		return this.uiViewport.getWorldHeight();
	}

	public void update(final float deltaTime) {
		if (this.mapStart.update(deltaTime)) {
			return;
		}
		if ((this.currentMusics != null) && (this.currentMusics.length > 0)) {
			if ((this.currentMusics[this.currentMusicIndex] != null)
					&& !this.currentMusics[this.currentMusicIndex].isPlaying()) {
				if (this.currentMusicRandomizeIndex) {
					this.currentMusicIndex = (int) (Math.random() * this.currentMusics.length);
				}
				else {
					this.currentMusicIndex = (this.currentMusicIndex + 1) % this.currentMusics.length;
				}
				this.currentMusics[this.currentMusicIndex].play();
			}
		}
		if ((this.focusUIFrame != null) && !this.focusUIFrame.isVisibleOnScreen()) {
			setFocusFrame(this.rootFrame.getNextFocusFrame());
		}

		final int baseMouseX = VirtualCursor.getX();
		int mouseX = baseMouseX;
		final int baseMouseY = VirtualCursor.getY();
		int mouseY = baseMouseY;
		final int minX = this.uiViewport.getScreenX();
		final int maxX = minX + this.uiViewport.getScreenWidth();
		final int minY = this.uiViewport.getScreenY();
		final int maxY = minY + this.uiViewport.getScreenHeight();

		mouseX = Math.max(minX, Math.min(maxX, mouseX));
		mouseY = Math.max(minY, Math.min(maxY, mouseY));
		if (Gdx.input.isCursorCatched()) {
			if (WarsmashConstants.CATCH_CURSOR) {
				Gdx.input.setCursorPosition(mouseX, mouseY);
			}
		}

		screenCoordsVector.set(mouseX, mouseY);
		this.uiViewport.unproject(screenCoordsVector);
		this.cursorFrame.setFramePointX(FramePoint.LEFT, screenCoordsVector.x);
		this.cursorFrame.setFramePointY(FramePoint.BOTTOM, screenCoordsVector.y);
		this.cursorFrame.setSequence("Normal");
		if (this.cursorFrame.isVisible() == VirtualCursor.isCursorHidden()) {
			this.cursorFrame.setVisible(!VirtualCursor.isCursorHidden());
		}

		// A hidden sprite's animation does not advance (the launcher hides the glue layers), so it never ends.
		if ((!this.glueSpriteLayerTopRight.isVisible() || this.glueSpriteLayerTopRight.isSequenceEnded())
				&& (!this.glueSpriteLayerTopLeft.isVisible() || this.glueSpriteLayerTopLeft.isSequenceEnded())
				&& (!this.campaigns.getFade().isVisible() || this.campaigns.getFade().isSequenceEnded())
				&& (!this.battleNet.getDoors().isVisible() || this.battleNet.getDoors().isSequenceEnded())) {
			final MenuState state = this.menuState;
			if (this.battleNet.updateMenuState(state) || this.campaigns.updateMenuState(state)
					|| this.skirmish.updateMenuState(state) || this.profiles.updateMenuState(state)) {
				return;
			}
			switch (state) {
			case GOING_TO_MAIN_MENU:
				setGlueLayersSequence("MainMenu Birth");
				if (this.battleNet.getDoors().isVisible()) {
					this.battleNet.getDoors().setVisible(false);
					this.battleNet.getBattleNetUI().setVisible(false);
				}
				this.menuState = MenuState.MAIN_MENU;
				break;
			case MAIN_MENU:
				setMainMenuVisible(true);
				setGlueLayersSequence("MainMenu Stand");
				break;
			case GOING_TO_SINGLE_PLAYER:
				setGlueLayersSequence("SinglePlayer Birth");
				this.menuState = MenuState.SINGLE_PLAYER;
				break;
			case GOING_TO_MAP:
				if ((this.beginGameInformation != null) && (this.beginGameInformation.sessionToken != 0)) {
					this.menuState = MenuState.BATTLE_NET_CUSTOM_GAME_LOBBY;
				}
				else {
					this.menuState = MenuState.SINGLE_PLAYER;
				}
				break;
			case SINGLE_PLAYER:
				// This state is applied every frame, and the menu has to stay hidden behind the saved games.
				this.singlePlayerMenu.setVisible((this.savedGameHolder == null) || !this.savedGameHolder.isVisible());
				this.campaigns.getFade().setVisible(false);
				setSinglePlayerButtonsEnabled(true);
				setGlueLayersSequence("SinglePlayer Stand");
				break;
			case QUITTING:
				Gdx.app.exit();
				break;
			case RESTARTING: {
				final WarsmashGdxMenuScreen restarted = new WarsmashGdxMenuScreen(this.warsmashIni,
						this.screenManager);
				restarted.setReforgedLoadingScreens(this.loadingScreen.isReforgedLoadingScreens());
				this.screenManager.setScreen(restarted);
				break;
			}
			default:
				break;
			}
		}

	}

	public boolean isOverSlider(final int screenX, final int screenY) {
		screenCoordsVector.set(screenX, screenY);
		this.uiViewport.unproject(screenCoordsVector);
		return ScrollBarFrame.isSliderPart(this.rootFrame.getFrameChildUnderMouse(screenCoordsVector.x,
				screenCoordsVector.y));
	}

	public boolean scrolled(final int screenX, final int screenY, final float amountY) {
		screenCoordsVector.set(screenX, screenY);
		this.uiViewport.unproject(screenCoordsVector);
		final ListBoxFrame listBox = ListBoxFrame.findUnder(this.rootFrame, screenCoordsVector.x, screenCoordsVector.y);
		if (listBox == null) {
			return false;
		}
		listBox.scrollItems(this.rootFrame, this.uiViewport, Math.round(amountY));
		return true;
	}

	public boolean touchDown(final int screenX, final int screenY, final float worldScreenY, final int button) {
		if (this.loadingScreen.isWaitingForTap()) {
			return true;
		}
		screenCoordsVector.set(screenX, screenY);
		this.uiViewport.unproject(screenCoordsVector);
		final UIFrame clickedUIFrame = this.rootFrame.touchDown(screenCoordsVector.x, screenCoordsVector.y, button);
		closePopupMenusOutside(clickedUIFrame);
		if (this.optionsDialog != null) {
			this.optionsDialog.pressed(clickedUIFrame);
		}
		if (clickedUIFrame != null) {
			if (clickedUIFrame instanceof ClickableFrame) {
				this.mouseDownUIFrame = (ClickableFrame) clickedUIFrame;
				this.mouseDownUIFrame.mouseDown(this.rootFrame, this.uiViewport);
			}
			if (clickedUIFrame instanceof FocusableFrame) {
				final FocusableFrame clickedFocusableFrame = (FocusableFrame) clickedUIFrame;
				if (clickedFocusableFrame.isFocusable()) {
					if (clickedFocusableFrame == this.focusUIFrame) {
						// A tap on the box that has focus brings back an on-screen keyboard closed with Enter.
						clickedFocusableFrame.onFocusGained();
					}
					else {
						setFocusFrame(clickedFocusableFrame);
					}
				}
			}
		}
		return false;
	}

	private void closePopupMenusOutside(final UIFrame clickedUIFrame) {
		for (final UIFrame childFrame : this.rootFrame.getChildFrames()) {
			if ((childFrame instanceof MenuFrame) && childFrame.isVisible()
					&& (childFrame.getParent() instanceof ClickableFrame)
					&& !isSelfOrDescendant(clickedUIFrame, childFrame.getParent())) {
				childFrame.setVisible(false);
			}
		}
	}

	private static boolean isSelfOrDescendant(UIFrame frame, final UIFrame ancestor) {
		while (frame != null) {
			if (frame == ancestor) {
				return true;
			}
			frame = frame.getParent();
		}
		return false;
	}

	private void showOptions() {
		if (this.optionsDialog == null) {
			final SimpleFrame holder = (SimpleFrame) this.rootFrame.createSimpleFrame("SmashEscMenu", this.rootFrame, 0);
			holder.addAnchor(new AnchorDefinition(FramePoint.TOP, 0, GameUI.convertY(this.uiViewport, -0.05f)));
			final UIFrame backdrop = this.rootFrame.createFrame("EscMenuBackdrop", holder, 0, 0);
			holder.add(backdrop);
			this.rootFrame.add(holder);
			this.optionsDialog = new GameOptionsDialog(this.rootFrame, this.uiViewport, holder, backdrop,
					new GameOptionsDialog.Host() {
						@Override
						public void closed() {
							holder.setVisible(false);
							setMainMenuVisible(true);
						}

						@Override
						public void applied() {
							if (MenuUI.this.currentMusics != null) {
								for (final Music music : MenuUI.this.currentMusics) {
									if (music != null) {
										music.setVolume(CSoundMixer.getMusicOptionScale());
									}
								}
							}
						}
					}, WarsmashConstants.CATCH_CURSOR, WarsmashConstants.ENABLE_MUSIC);
		}
		setMainMenuVisible(false);
		this.optionsDialog.getHolder().setVisible(true);
		this.optionsDialog.show();
	}

	private void showSavedGames() {
		openSavedGameDialog(false);
	}

	/** View Replay: the replays in the saved games' dialog. */
	private void showReplays() {
		openSavedGameDialog(true);
	}

	private void openSavedGameDialog(final boolean replays) {
		if (this.savedGameDialog == null) {
			final SimpleFrame holder = (SimpleFrame) this.rootFrame.createSimpleFrame("SmashEscMenu", this.rootFrame, 0);
			holder.addAnchor(new AnchorDefinition(FramePoint.TOP, 0, GameUI.convertY(this.uiViewport, -0.05f)));
			final UIFrame backdrop = this.rootFrame.createFrame("EscMenuBackdrop", holder, 0, 0);
			holder.add(backdrop);
			this.rootFrame.add(holder);
			this.savedGameDialog = new SavedGameDialog(this.rootFrame, this.uiViewport, holder, backdrop,
					new SavedGameDialog.Host() {
						@Override
						public void focusOn(final FocusableFrame frame) {
							setFocusFrame(frame);
						}

						@Override
						public void saveGame(final String name) {
						}

						@Override
						public SavedGame.Loadability loadability(final FileHandle file) {
							return SavedGame.loadability(file);
						}

						@Override
						public void loadGame(final String name) {
							holder.setVisible(false);
							startSavedGame(name);
						}

						@Override
						public void closed() {
							holder.setVisible(false);
							MenuUI.this.singlePlayerMenu.setVisible(true);
						}

						@Override
						public List<String> replayNames() {
							return MenuUI.this.replayNames();
						}

						@Override
						public String replayProblem(final String name) {
							return MenuUI.this.replayProblem(name);
						}

						@Override
						public boolean replayTryable(final String name) {
							return !MenuUI.REPLAY_DAMAGED.equals(MenuUI.this.replayProblem(name));
						}

						@Override
						public void playReplay(final String name, final boolean forced) {
							holder.setVisible(false);
							if (!MenuUI.this.startReplay(GameReplays.folder().child(name + GameReplays.EXTENSION), 0,
									forced)) {
								MenuUI.this.showMapLoadError();
							}
						}
					});
			this.savedGameHolder = holder;
		}
		this.singlePlayerMenu.setVisible(false);
		this.savedGameHolder.setVisible(true);
		if (replays) {
			this.savedGameDialog.showReplays();
		}
		else {
			this.savedGameDialog.showLoad();
		}
	}

	private static final String REPLAY_DAMAGED = "damaged";

	/** The replays of the Replay folder: the last game's first, then the newest first. */
	private List<String> replayNames() {
		final List<FileHandle> files = new ArrayList<>();
		for (final FileHandle file : GameReplays.folder().list(GameReplays.EXTENSION)) {
			files.add(file);
		}
		files.sort((a, b) -> {
			final boolean lastA = a.nameWithoutExtension().equalsIgnoreCase(GameReplays.LAST_REPLAY);
			final boolean lastB = b.nameWithoutExtension().equalsIgnoreCase(GameReplays.LAST_REPLAY);
			return lastA != lastB ? (lastA ? -1 : 1) : Long.compare(b.lastModified(), a.lastModified());
		});
		final List<String> names = new ArrayList<>();
		for (final FileHandle file : files) {
			names.add(file.nameWithoutExtension());
		}
		return names;
	}

	/** Why a replay is not played as it is: REPLAY_DAMAGED for a file that cannot be read, else the engine's reason. */
	private String replayProblem(final String name) {
		try {
			final CReplay replay = GameReplays.load(GameReplays.folder().child(name + GameReplays.EXTENSION));
			return GameReplays.unplayableReason(replay, this.gameData != null ? this.gameData : this.dataSource);
		}
		catch (final IOException | RuntimeException e) {
			return REPLAY_DAMAGED;
		}
	}

	/**
	 * Loads a saved game of the current profile, as Load Game in the saved games list does.
	 *
	 * @return false when the file cannot be read
	 */
	public boolean startSavedGame(final String name) {
		return startSavedGame(CSaveStorage.savedGame(name));
	}

	/** A save outside the profile's folder. */
	public boolean startSavedGame(final FileHandle file) {
		return this.mapStart.startSavedGame(file);
	}

	void setFocusFrame(final FocusableFrame clickedFocusableFrame) {
		// The profile view asks for its edit box again while it stays open; refocusing would reopen the
		// on-screen keyboard every time.
		if (clickedFocusableFrame == this.focusUIFrame) {
			return;
		}
		if (this.focusUIFrame != null) {
			this.focusUIFrame.onFocusLost();
		}
		this.focusUIFrame = clickedFocusableFrame;
		if (this.focusUIFrame != null) {
			this.focusUIFrame.onFocusGained();
		}
	}

	public boolean touchUp(final int screenX, final int screenY, final float worldScreenY, final int button) {
		// The game starts on the release, so that no half of the tap reaches it.
		if (this.loadingScreen.continueAfterLoading()) {
			return true;
		}
		screenCoordsVector.set(screenX, screenY);
		this.uiViewport.unproject(screenCoordsVector);
		final UIFrame clickedUIFrame = this.rootFrame.touchUp(screenCoordsVector.x, screenCoordsVector.y, button);
		if (this.mouseDownUIFrame != null) {
			if (clickedUIFrame == this.mouseDownUIFrame) {
				this.mouseDownUIFrame.onClick(button);
				final String soundKey = this.mouseDownUIFrame.getSoundKey();
				if (soundKey != null) {
					this.uiSounds.getSound(soundKey).play(this.uiScene.audioContext, 0, 0, 0);
				}
			}
			this.mouseDownUIFrame.mouseUp(this.rootFrame, this.uiViewport);
		}
		this.mouseDownUIFrame = null;
		return false;
	}

	public boolean touchDragged(final int screenX, final int screenY, final float worldScreenY, final int pointer) {
		screenCoordsVector.set(screenX, screenY);
		this.uiViewport.unproject(screenCoordsVector);
		if (this.mouseDownUIFrame != null) {
			this.mouseDownUIFrame.mouseDragged(this.rootFrame, this.uiViewport, screenCoordsVector.x,
					screenCoordsVector.y);
		}
		return false;
	}

	public boolean mouseMoved(final int screenX, final int screenY, final float worldScreenY) {
		screenCoordsVector.set(screenX, screenY);
		this.uiViewport.unproject(screenCoordsVector);
		final UIFrame mousedUIFrame = this.rootFrame.getFrameChildUnderMouse(screenCoordsVector.x,
				screenCoordsVector.y);
		if (mousedUIFrame != this.mouseOverUIFrame) {
			if (this.mouseOverUIFrame != null) {
				this.mouseOverUIFrame.mouseExit(this.rootFrame, this.uiViewport);
			}
			if (mousedUIFrame instanceof ClickableFrame) {
				this.mouseOverUIFrame = (ClickableFrame) mousedUIFrame;
				this.mouseOverUIFrame.mouseEnter(this.rootFrame, this.uiViewport);
			}
			else {
				this.mouseOverUIFrame = null;
			}
		}
		return false;
	}

	private void loadSounds() {
		final DataTable uiSoundsTable = new DataTable(this.worldEditStrings);
		try {
			try (InputStream miscDataTxtStream = this.dataSource.getResourceAsStream("UI\\SoundInfo\\UISounds.slk")) {
				uiSoundsTable.readSLK(miscDataTxtStream);
			}
			try (InputStream miscDataTxtStream = this.dataSource
					.getResourceAsStream("UI\\SoundInfo\\AmbienceSounds.slk")) {
				uiSoundsTable.readSLK(miscDataTxtStream);
			}
		}
		catch (final IOException e) {
			Log.error(e);
		}
		this.uiSounds = new KeyedSounds(uiSoundsTable, this.dataSource);
	}

	public KeyedSounds getUiSounds() {
		return this.uiSounds;
	}

	static enum MenuState {
		GOING_TO_MAIN_MENU,
		MAIN_MENU,
		GOING_TO_BATTLE_NET_LOGIN,
		GOING_TO_BATTLE_NET_LOGIN_PART2,
		BATTLE_NET_LOGIN,
		LEAVING_BATTLE_NET,
		LEAVING_BATTLE_NET_FROM_LOGGED_IN,
		GOING_TO_BATTLE_NET_CUSTOM_GAME_MENU,
		BATTLE_NET_CUSTOM_GAME_MENU,
		GOING_TO_BATTLE_NET_CREATE_CUSTOM_GAME_MENU,
		BATTLE_NET_CREATE_CUSTOM_GAME_MENU,
		GOING_TO_BATTLE_NET_CHANNEL_MENU,
		BATTLE_NET_CHANNEL_MENU,
		GOING_TO_BATTLE_NET_WELCOME,
		BATTLE_NET_WELCOME,
		GOING_TO_SINGLE_PLAYER,
		LEAVING_CAMPAIGN,
		SINGLE_PLAYER,
		GOING_TO_SINGLE_PLAYER_SKIRMISH,
		SINGLE_PLAYER_SKIRMISH,
		GOING_TO_MAP,
		GOING_TO_CAMPAIGN,
		GOING_TO_CAMPAIGN_PART2,
		GOING_TO_MISSION_SELECT,
		MISSION_SELECT,
		CAMPAIGN,
		SINGLE_PLAYER_PROFILE,
		QUITTING,
		RESTARTING,
		GOING_TO_BATTLE_NET_CHAT_CHANNEL,
		GOING_TO_BATTLE_NET_CHAT_CHANNEL_FROM_OUTSIDE,
		BATTLE_NET_CHAT_CHANNEL,
		GOING_TO_BATTLE_NET_CUSTOM_GAME_LOBBY,
		BATTLE_NET_CUSTOM_GAME_LOBBY;
	}

	public void hide() {
		this.glueScreenLoop.stop();
		stopMusic();
	}

	public void dispose() {
		this.loadingScreen.clearReforgedLoading();
		if (this.rootFrame != null) {
			this.rootFrame.dispose();
		}
	}

	public boolean keyDown(final int keycode) {
		if (this.loadingScreen.isWaitingForTap()) {
			return true;
		}
		if ((this.optionsDialog != null) && this.optionsDialog.isShowing()) {
			this.optionsDialog.keyDown(keycode);
			return true;
		}
		if (this.focusUIFrame != null) {
			this.focusUIFrame.keyDown(keycode);
		}
		return false;
	}

	public boolean keyUp(final int keycode) {
		if (this.loadingScreen.continueAfterLoading()) {
			return true;
		}
		if (keycode == Input.Keys.TAB) {
			// accessibility tab focus ui
			final List<FocusableFrame> focusableFrames = this.rootFrame.getFocusableFrames();
			final int indexOf = focusableFrames.indexOf(this.focusUIFrame) + 1;
			for (int i = indexOf; i < focusableFrames.size(); i++) {
				final FocusableFrame focusableFrame = focusableFrames.get(i);
				if (focusableFrame.isVisibleOnScreen() && focusableFrame.isFocusable()) {
					setFocusFrame(focusableFrame);
					break;
				}
			}
		}
		else {
			if (this.focusUIFrame != null) {
				this.focusUIFrame.keyUp(keycode);
			}
		}
		return false;
	}

	public boolean keyTyped(final char character) {
		if (this.focusUIFrame != null) {
			this.focusUIFrame.keyTyped(character);
		}
		if ((character == 'z') && Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)) {
			this.hideUI = !this.hideUI;
			this.uiScene.show = !this.uiScene.show;
		}
		return false;
	}

	public void onReturnFromGame(final boolean forceCampaignSelect) {
		this.loadingScreen.hide();
		final SavedGame.Loadability loadFailure = SavedGame.consumeLoadFailure();
		if (loadFailure != null) {
			this.dialog.showError(SavedGameDialog.reasonKey(loadFailure), null);
		}
		switch (this.menuState) {
		default:
		case GOING_TO_MAIN_MENU:
		case MAIN_MENU:
			switchToMainMenuGlueScreenLoop();
			restoreMainMenuBackground();
			break;
		case CAMPAIGN:
		case MISSION_SELECT:
			this.campaigns.onReturnFromGame(forceCampaignSelect);
			break;
		case BATTLE_NET_CUSTOM_GAME_LOBBY: {
			this.menuScreen.setModel(this.rootFrame.getSkinField("GlueSpriteLayerBackground"), this.menuFogSettings);
			this.menuScreen.alternateModelToBattlenet();
			this.rootFrame.setSpriteFrameModel(this.cursorFrame, this.rootFrame.getSkinField("Cursor"));
			this.battleNet.requestEnterDefaultChat();
			break;
		}
		}
	}

	private void stopMusic() {
		if (this.currentMusics != null) {
			for (final Music music : this.currentMusics) {
				if (music != null) {
					music.stop();
				}
			}
			this.currentMusics = null;
		}
	}

	public Music playMusic(final String musicField, final boolean random, int index) {
		if (WarsmashConstants.ENABLE_MUSIC) {
			stopMusic();

			final String[] semicolonMusics = musicField.split(";");
			final List<String> musicPaths = new ArrayList<>();
			for (String musicPath : semicolonMusics) {
				// dumb support for comma as well as semicolon, I wonder if we can
				// clean this up, simplify?
				if (this.musicSLK.get(musicPath) != null) {
					musicPath = this.musicSLK.get(musicPath).getField("FileNames");
				}
				final String[] moreSplitMusics = musicPath.split(",");
				for (final String finalSplitPath : moreSplitMusics) {
					musicPaths.add(finalSplitPath);
				}
			}
			final String[] musics = musicPaths.toArray(new String[musicPaths.size()]);

			this.currentMusics = new Music[musics.length];
			int validMusicCount = 0;
			for (int i = 0; i < musics.length; i++) {
				final String path = MusicPlayerLibGDX.findMusicFile(this.viewer.dataSource, musics[i]);
				if (path != null) {
					final Music newMusic = Extensions.audio.newMusic(new DataSourceFileHandle(this.viewer.dataSource, path));
					newMusic.setVolume(CSoundMixer.getMusicOptionScale());
					this.currentMusics[i] = newMusic;
					validMusicCount++;
				}
			}
			if (this.currentMusics.length != validMusicCount) {
				final Music[] fixedList = new Music[validMusicCount];
				int fixedListIndex = 0;
				for (int i = 0; i < this.currentMusics.length; i++) {
					if (this.currentMusics[i] != null) {
						fixedList[fixedListIndex++] = this.currentMusics[i];
					}
				}
				this.currentMusics = fixedList;
			}
			if (this.currentMusics.length == 0) {
				return null;
			}
			if (random) {
				index = (int) (Math.random() * this.currentMusics.length);
			}
			index = Math.min(index, this.currentMusics.length - 1);
			this.currentMusicIndex = index;
			this.currentMusicRandomizeIndex = random;
			if (this.currentMusics[index] != null) {
				this.currentMusics[index].play();
			}
		}
		return null;
	}

	public void playCurrentBattleNetGlueSpriteDeath() {
		this.battleNet.playCurrentBattleNetGlueSpriteDeath();
	}
}
