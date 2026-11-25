package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.launcher.ButtonFlash;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.GameMarks;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.GameReplays;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.ReplayPlayback;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.ReplayRecording;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.ReplaySession;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CCameraField;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.FocusableFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.warsmash.input.touch.PointKind;
import com.etheller.warsmash.input.touch.VirtualCursor;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.fdf.FrameTemplatePrefetch;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FrameTemplateEnvironment;
import com.etheller.warsmash.parsers.fdf.datamodel.BackdropCornerFlags;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.Vector4Definition;
import com.etheller.warsmash.parsers.fdf.frames.BackdropFrame;
import com.etheller.warsmash.parsers.fdf.frames.ColorCodedText;
import com.etheller.warsmash.parsers.fdf.frames.FadingSpriteBatch;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleStatusBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.parsers.jass.OsKeys;
import com.etheller.warsmash.parsers.jass.Jass2.RootFrameListener;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.lan.LanPlay;
import com.etheller.warsmash.lan.LockstepTurnManager;
import com.etheller.warsmash.lan.ui.LanOverlay;
import net.warsmash.lan.protocol.LanProtocol;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.util.StringBundle;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover;
import com.etheller.warsmash.viewer5.handlers.w3x.TextTag;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.ScriptUiState;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.CameraPreset;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.CameraRates;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.GameCameraManager;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.UnitIconUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItem;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.commandbuttons.CommandButtonListener;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedInterfaceState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CPlayerStateListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CLeaderboard;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CMultiboard;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitEnumFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitStateListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityView;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerColor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandHost;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManagerEntry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.MeleeUIAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableActionFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.CommandCardCommandListener;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.CommandErrorListener;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.QueueIconListener;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialogButton;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.replay.ReplayPanel;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog;

public class MeleeUI implements CUnitStateListener, CommandButtonListener, CommandCardCommandListener,
		QueueIconListener, CommandErrorListener, CPlayerStateListener, WarsmashUI, WarsmashToggleableUI,
		War3MapViewer.InterfaceStateKeeper {
	private static final float PANEL_TIP_REFRESH_PERIOD = 0.25f;
	private static final float CLASSIC_CONSOLE_BACKDROP_HEIGHT = 0.13f;
	/**
	 * Floating text: the font size and the line spacing for a text tag height of 1, and the width lines wrap at. The
	 * glyphs are drawn smaller than the lines are apart.
	 */
	private static final float TEXT_TAG_FONT_SIZE_PER_HEIGHT = 0.55f;
	private static final float TEXT_TAG_LINE_SPACING_PER_HEIGHT = 0.61f;
	private static final float TEXT_TAG_WRAP_WIDTH = 0.2f;
	/** The black shadow under floating text: two pixels right and one down on a screen 900 pixels high. */
	private static final float TEXT_TAG_SHADOW_OFFSET_X = 0.6f * 2 / 900;
	private static final float TEXT_TAG_SHADOW_OFFSET_Y = 0.6f / 900;
	static final float CHAT_MESSAGE_SECONDS = 10;
	static final String BUILDING_PATHING_PREVIEW_KEY = "buildingPathingPreview";
	public static final float DEFAULT_COMMAND_CARD_ICON_WIDTH = 0.039f;
	public static final float DEFAULT_INVENTORY_ICON_WIDTH = 0.03125f;
	static final int COMMAND_CARD_WIDTH = 4;
	static final int COMMAND_CARD_HEIGHT = 3;
	static final int INVENTORY_HEIGHT = 3;

	static final Vector2 screenCoordsVector = new Vector2();
	static final Vector3 clickLocationTemp = new Vector3();
	static final int BUFF_DISPLAY_MAX = 10;
	final MeleeUICommands commands = new MeleeUICommands(this);
	final MeleeUIInfoPanel infoPanel = new MeleeUIInfoPanel(this);
	final MeleeUITooltips tooltips = new MeleeUITooltips(this);
	final MeleeUIInput input = new MeleeUIInput(this);
	final MeleeUIControlGroups groups = new MeleeUIControlGroups(this);
	final MeleeUIMenus menus = new MeleeUIMenus(this);
	final MeleeUIMessages messages = new MeleeUIMessages(this);
	final MeleeUIMultiboard multiboard = new MeleeUIMultiboard(this);
	final MeleeUITeamResources teamResources = new MeleeUITeamResources(this);
	final MeleeUIMinimapPanel minimapPanel = new MeleeUIMinimapPanel(this);
	final MeleeUICinematics cinematics = new MeleeUICinematics(this);
	private PortraitLipSync lipSync;
	final MeleeUIOriginFrames originFrames = new MeleeUIOriginFrames(this);
	FrameEventSink frameEvents;
	/** The frame templates of the interface; they are read ahead while the map loads. */
	public static final String FRAME_DEF_TOC = "UI\\FrameDef\\FrameDef.toc";
	public static final String SMASH_FRAME_DEF_TOC = "UI\\FrameDef\\SmashFrameDef.toc";

	final DataSource dataSource;
	final ExtendViewport uiViewport;
	final Scene uiScene;
	final Scene portraitScene;
	final GameCameraManager cameraManager;
	final War3MapViewer war3MapViewer;
	private final RootFrameListener rootFrameListener;
	GameUI rootFrame;
	UIFrame consoleUI;
	UIFrame resourceBar;
	StringFrame resourceBarGoldText;
	StringFrame resourceBarLumberText;
	StringFrame resourceBarSupplyText;
	StringFrame resourceBarUpkeepText;
	SpriteFrame timeIndicator;
	StringFrame unitLifeText;
	StringFrame unitManaText;
	MeleeUIInfoPanel.Portrait portrait;
	final Rectangle tempRect = new Rectangle();

	// tooltip
	UIFrame tooltipFrame;

	// hovertip
	UIFrame hovertipFrame;
	StringFrame hovertipText;

	SimpleFrame simpleInfoPanelUnitDetail;
	StringFrame simpleNameValue;
	StringFrame simpleClassValue;
	StringFrame simpleBuildingActionLabel;
	SimpleStatusBarFrame simpleBuildTimeIndicator;
	SimpleStatusBarFrame simpleHeroLevelBar;
	SimpleStatusBarFrame simpleProgressIndicator;

	final BuffBarIcon[] buffBarIcons = new BuffBarIcon[BUFF_DISPLAY_MAX];
	int currentBuffBarIconIndex = 0;

	UIFrame simpleInfoPanelBuildingDetail;
	StringFrame simpleBuildingNameValue;
	StringFrame simpleBuildingBuildingActionLabel;
	SimpleStatusBarFrame simpleBuildingBuildTimeIndicator;
	final QueueIcon[] queueIconFrames = new QueueIcon[WarsmashConstants.BUILD_QUEUE_SIZE];
	QueueIcon selectWorkerInsideFrame;
	final UIFrame[] selectedUnitHighlightBackdrop = new UIFrame[WarsmashConstants.MAX_SELECTION_SIZE];
	final MultiSelectionIcon[] selectedUnitFrames = new MultiSelectionIcon[WarsmashConstants.MAX_SELECTION_SIZE];
	HeroBarIcon idleWorkerIcon;
	SimpleFrame heroBar;
	boolean userUIEnabled = true;
	final CineFilter cineFilter = new CineFilter();
	final List<CUnit> idleWorkers = new ArrayList<>();

	UIFrame attack1Icon;
	StringFrame attack1InfoPanelIconLevel;
	UIFrame attack2Icon;
	StringFrame attack2InfoPanelIconLevel;
	UIFrame armorIcon;
	StringFrame armorInfoPanelIconLevel;
	UIFrame rankIcon;
	UIFrame foodIcon;

	UIFrame heroInfoPanel;

	SimpleFrame inventoryBarFrame;
	StringFrame inventoryTitleFrame;
	final int inventoryWidth;
	final CommandCardIcon[][] inventoryIcons;
	Texture consoleInventoryNoCapacityTexture;

	final CommandCardIcon[][] commandCard = new CommandCardIcon[COMMAND_CARD_HEIGHT][COMMAND_CARD_WIDTH];

	RenderUnit selectedUnit;
	final List<Integer> subMenuOrderIdStack = new ArrayList<>();

	UIFrame inventoryCover;
	SpriteFrame cursorFrame;
	private boolean cursorHiddenForFinger;
	MeleeUIMinimap meleeUIMinimap;
	MeleeAllianceDialog allianceDialog;
	MeleeQuestDialog questDialog;
	MeleeChat chat;
	MeleeMessageLog messageLog;
	float questButtonFlashTime = ButtonFlash.OFF;
	MeleeScoreScreen scoreScreen;
	final CPlayerUnitOrderListener unitOrderListener;
	CAbilityView activeCommand;
	int activeCommandOrderId;
	RenderUnit activeCommandUnit;
	MdxComplexInstance cursorModelInstance = null;
	MdxComplexInstance rallyPointInstance = null;
	Pixmap cursorModelUnderneathPathingRedGreenPixmap;
	Texture cursorModelUnderneathPathingRedGreenPixmapTexture;
	SplatModel cursorModelUnderneathPathingRedGreenSplatModel;
	SplatMover placementCursor = null;

	int selectedSoundCount = 0;

	// TODO these corrections are used for old hardcoded UI stuff, we should
	// probably remove them later
	final float widthRatioCorrection;
	final float heightRatioCorrection;
	ClickableFrame mouseDownUIFrame;
	ClickableFrame mouseOverUIFrame;
	SimpleFrame smashSimpleInfoPanel;
	final CPlayer localPlayer;
	MeleeUIAbilityActivationReceiver meleeUIAbilityActivationReceiver;
	List<RenderUnit> selectedUnits = Collections.emptyList();
	final List<List<RenderUnit>> controlGroups = new ArrayList<>();
	Set<RenderUnit> dragSelectPreviewUnits = new HashSet<>();
	private Set<RenderUnit> dragSelectPreviewUnitsUpcoming = new HashSet<>();
	private final CUnitEnumFunction dragSelectPreviewEnum = new CUnitEnumFunction() {
		@Override
		public boolean call(final CUnit unit) {
			final RenderUnit renderUnit = MeleeUI.this.war3MapViewer.getRenderPeer(unit);
			if (!unit.isDead()
					&& renderUnit.isSelectable(MeleeUI.this.war3MapViewer.simulation,
							MeleeUI.this.war3MapViewer.getLocalPlayerIndex())
					&& MeleeUI.this.dragSelectPreviewUnitsUpcoming.add(renderUnit)) {
				final SimpleStatusBarFrame simpleStatusBarFrame = MeleeUI.this.tooltips.getHpBar();
				if (!unit.isInvulnerable()) {
					MeleeUI.this.tooltips.positionHealthBar(simpleStatusBarFrame, renderUnit, 1.0f);
				}
				if (!MeleeUI.this.dragSelectPreviewUnits.contains(renderUnit) && isSelectionOn(DRAG_SELECT_UI_OFF)) {
					MeleeUI.this.war3MapViewer.showUnitMouseOverHighlight(renderUnit);
				}
			}
			return false;
		}
	};
	private final int[] controlGroupBarSizes = new int[ControlGroupBar.SLOT_COUNT];
	private final Texture[] controlGroupBarIcons = new Texture[ControlGroupBar.SLOT_COUNT];
	private SetPoint hovertipSetPoint;
	private UIFrame hovertipSetPointFrame;
	private final FreeTypeFontParameter textTagFontParam = new FreeTypeFontParameter();
	private final Color textTagShadowColor = new Color(0, 0, 0, 1);
	private final Color textTagDrawColor = new Color();
	CItem draggingItem;
	SimpleButtonFrame questsButton;
	private final Runnable exitGameRunnable;
	private boolean resultShownOutside;
	SimpleFrame smashEscMenu;
	MeleeGameMenu gameMenu;
	private String mapMusicField;
	private boolean mapMusicRandom;
	private int mapMusicIndex;
	private String playingMusicField;
	boolean menuFocusFromTouch;
	Runnable openMenuAction;
	FocusableFrame menuFocus;
	/** The text box of a map's own interface that holds the typed keys while the game menu is not up. */
	FocusableFrame scriptFocus;
	private UIFrame[] modalFrames;
	private boolean[] modalFramesShown;
	RenderWidget mouseOverUnit;
	final Vector3 lastMouseDragStart = new Vector3();
	final Vector3 lastMouseDragCameraTargetStart = new Vector3();
	final Vector3 lastMouseClickLocation = new Vector3();

	final List<SimpleStatusBarFrame> hpBarFrames = new ArrayList<>();
	int hpBarFrameIndex = 0;
	boolean allowDrag;
	String[] includeFrames;
	String[] ignoreFrames;
	private final ShapeRenderer shapeRenderer = new ShapeRenderer();
	int draggingMouseButton;
	MusicPlayer musicPlayer;
	private int shownButtonEffectsVersion;
	final List<CTimerDialog> timerDialogs = new ArrayList<>();
	final List<CScriptDialog> scriptDialogs = new ArrayList<>();
	int lastScriptDialogShown;
	/** The dialog shown last; the only one that can be on the screen. */
	CScriptDialog pausingScriptDialog;
	private static final int SCRIPT_DIALOG_PAUSE_STEPS = 3;
	private int simulationDrivenDepth;
	/** How many moments the player marked in this game. */
	private int markedMoments;
	private final CPlayerCommandHost commandHost = new CPlayerCommandHost() {
		@Override
		public void clickDialogButton(final int playerIndex, final int dialogHandleId, final int buttonHandleId) {
			// By handle id: the list of dialogs changes order when a script clears one.
			for (final CScriptDialog dialog : MeleeUI.this.scriptDialogs) {
				if (dialog.getHandleId() == dialogHandleId) {
					for (final CScriptDialogButton button : dialog.getButtons()) {
						if (button.getHandleId() == buttonHandleId) {
							runSimulationDriven(() -> button.click(dialog));
							return;
						}
					}
					return;
				}
			}
		}

		@Override
		public void chatMessage(final int playerIndex, final int recipient, final int recipientPlayerIndex,
				final String message) {
			receivedChat(playerIndex, recipient, recipientPlayerIndex, message);
		}

		@Override
		public void setGamePaused(final int playerIndex, final boolean paused) {
			final War3MapViewer viewer = MeleeUI.this.war3MapViewer;
			final boolean lan = viewer.isLanGame();
			int remaining = -1;
			// A referee pauses from the seat nobody plays, and as often as it likes.
			final boolean referee = lan && viewer.simulation.getPlayer(playerIndex)
					.getSlotState() != CPlayerSlotState.PLAYING;
			final String who = referee ? MeleeUI.this.rootFrame.smashString("SMASH_A_REFEREE", "A referee")
					: viewer.simulation.getPlayer(playerIndex).getName();
			if (lan && paused && !referee) {
				// Counted from the commands every machine gets, so every machine refuses the same pauses.
				if (viewer.isGamePaused(War3MapViewer.PauseSource.PLAYER)) {
					return;
				}
				final int used = MeleeUI.this.pausesUsed.merge(playerIndex, 1, Integer::sum);
				if (used > LanPlay.PAUSES_PER_PLAYER) {
					MeleeUI.this.pausesUsed.put(playerIndex, LanPlay.PAUSES_PER_PLAYER);
					Log.warn("LAN: " + viewer.simulation.getPlayer(playerIndex).getName() + " has no pauses left");
					if (playerIndex == viewer.getLocalPlayerIndex()) {
						MeleeUI.this.messages.displayTimedText(0, 0, 5,
								MeleeUI.this.rootFrame.smashString("SMASH_NO_PAUSES_LEFT", "You have no pauses left."));
					}
					return;
				}
				remaining = LanPlay.PAUSES_PER_PLAYER - used;
			}
			viewer.setGamePaused(War3MapViewer.PauseSource.PLAYER, paused);
			if (paused) {
				String text = MeleeUI.this.rootFrame.getTemplates().getDecoratedString("PAUSE_GAME_NOTIFY_NO_TIMEOUT")
						.replace("%s", who);
				if (remaining >= 0) {
					text += " (" + String.format(Locale.ROOT, remaining == 1
							? MeleeUI.this.rootFrame.smashString("SMASH_PAUSE_REMAINING", "%d pause remaining")
							: MeleeUI.this.rootFrame.smashString("SMASH_PAUSES_REMAINING", "%d pauses remaining"),
							remaining) + ")";
					Log.warn("LAN: " + who + " paused the game, " + remaining + " pauses remaining");
				}
				MeleeUI.this.messages.displayTimedText(0, 0, 10, text);
			}
			else if (lan) {
				Log.warn("LAN: " + who + " resumed the game");
			}
		}

		@Override
		public void minimapSignal(final int playerIndex, final float x, final float y) {
			// The sender's own signal is already shown when it was sent.
			if ((playerIndex != MeleeUI.this.war3MapViewer.getLocalPlayerIndex())
					&& MeleeUI.this.war3MapViewer.simulation.getPlayer(playerIndex)
							.hasAlliance(MeleeUI.this.war3MapViewer.getLocalPlayerIndex(), CAllianceType.PASSIVE)) {
				MeleeUI.this.minimapPanel.showMinimapSignal(playerIndex, x, y);
			}
		}
	};
	/** Pauses each player of a LAN game has used. */
	private final Map<Integer, Integer> pausesUsed = new HashMap<>();
	private LanOverlay lanOverlay;
	int leaderboardSlot = -1;
	private final DataTable musicSLK;

	public int[][] commandCardGridHotkeys = { { Input.Keys.Q, Input.Keys.W, Input.Keys.E, Input.Keys.R },
			{ Input.Keys.A, Input.Keys.S, Input.Keys.D, Input.Keys.F },
			{ Input.Keys.Z, Input.Keys.X, Input.Keys.C, Input.Keys.V } };
	float simpleProgressIndicatorDurationRemaining;
	float simpleProgressIndicatorDurationMax;

	private boolean showing;
	UIFrame upperButtonBar;
	UIFrame cinematicPanel;
	UIFrame minimapButtonBar;
	boolean minimapSignalTargeting;
	final java.util.Map<UIFrame, Integer> timerDialogSlots = new java.util.IdentityHashMap<>();
	int hoveredPanelTip = -1;
	/** Where the mouse rests outside the clickable frames, in UI coordinates; NaN when nowhere. */
	float panelTipX = Float.NaN;
	float panelTipY;
	private float panelTipRefreshTime;
	UIFrame simpleInfoPanelItemDetail;
	StringFrame simpleItemNameValue;
	StringFrame simpleItemDescriptionValue;
	UIFrame simpleInfoPanelDestructableDetail;
	StringFrame simpleDestructableNameValue;
	SimpleFrame smashCommandButtons;
	/** A replay's panel in the command card's place; null in a game that is played. */
	ReplayPanel replayPanel;
	/** The game is played with the Reforged skin (the launcher's), which a replay's panel follows. */
	private boolean reforgedSkin;
	boolean userControlEnabled = true;
	private boolean scriptCursorHidden;
	/** EnableSelect, EnableDragSelect and EnablePreSelect: a state bit off stops the player's action, a UI bit off hides its feedback. */
	static final int SELECT_OFF = 1;
	static final int SELECT_UI_OFF = 2;
	static final int DRAG_SELECT_OFF = 4;
	static final int DRAG_SELECT_UI_OFF = 8;
	static final int PRE_SELECT_OFF = 16;
	static final int PRE_SELECT_UI_OFF = 32;
	int selectionOff;
	MeleeUICinematics.CinematicPortrait cinematicPortrait;
	boolean reforgedCinematics;
	Texture cinematicGradientTexture;
	private static final float CAMERA_HEIGHT_SIGMA = 500;
	private static final float CAMERA_HEIGHT_STEP = 128;
	private static final int CAMERA_HEIGHT_RADIUS = 8;
	private boolean worldFrameFullScreen;
	private Rectangle scriptWorldRect;
	private int worldFrameWidth;
	private int worldFrameHeight;

	boolean swallowNextTypedChar;

	boolean chatOpenEntryAfterDialog;

	public MeleeUI(final DataSource dataSource, final ExtendViewport uiViewport, final Scene uiScene,
			final Scene portraitScene, final CameraPreset[] cameraPresets, final CameraRates cameraRates,
			final War3MapViewer war3MapViewer, final RootFrameListener rootFrameListener,
			final CPlayerUnitOrderListener unitOrderListener, final Runnable exitGameRunnable) {
		this.dataSource = dataSource;
		this.uiViewport = uiViewport;
		this.uiScene = uiScene;
		this.portraitScene = portraitScene;
		this.war3MapViewer = war3MapViewer;
		this.inventoryWidth = war3MapViewer.getGameData().isUseNineItemInventory() ? 3 : 2;
		this.inventoryIcons = new CommandCardIcon[INVENTORY_HEIGHT][this.inventoryWidth];
		this.rootFrameListener = rootFrameListener;
		this.unitOrderListener = unitOrderListener;
		this.exitGameRunnable = exitGameRunnable;
		war3MapViewer.setGameEndScreen(() -> endGame(true));

		this.cameraManager = new GameCameraManager(cameraPresets, cameraRates);

		this.cameraManager.setupCamera(war3MapViewer.worldScene);
		this.localPlayer = this.war3MapViewer.simulation.getPlayer(war3MapViewer.getLocalPlayerIndex());
		this.war3MapViewer.simulation.setPlayerCommandHost(this.commandHost);
		final float[] startLocation = this.localPlayer.getStartLocation();
		this.cameraManager.target.x = startLocation[0];
		this.cameraManager.target.y = startLocation[1];
		this.cameraManager.setStepClock(this.war3MapViewer.simulation::getGameTurnTick,
				(x, y) -> Math.max(smoothedGroundHeight(x, y), war3MapViewer.terrain.getWaterHeight(x, y)));

		this.widthRatioCorrection = this.uiViewport.getMinWorldWidth() / 1600f;
		this.heightRatioCorrection = this.uiViewport.getMinWorldHeight() / 1200f;

		this.localPlayer.addStateListener(this);
		this.messages.watchSharedControl();

		this.musicSLK = new DataTable(StringBundle.EMPTY);
		final String musicSLKPath = "UI\\SoundInfo\\Music.SLK";
		if (war3MapViewer.dataSource.has(musicSLKPath)) {
			try (InputStream miscDataTxtStream = war3MapViewer.dataSource.getResourceAsStream(musicSLKPath)) {
				this.musicSLK.readSLK(miscDataTxtStream);
			}
			catch (final IOException e) {
				Log.error(e);
			}
		}

		if (war3MapViewer.getGameData().isEnableMusic()) {
			this.musicPlayer = new MusicPlayerLibGDX(dataSource, this.musicSLK);
		}
		else {
			this.musicPlayer = MusicPlayer.DO_NOTHING;
		}
		war3MapViewer.getSoundMixer().setMusicScaleListener(this.musicPlayer::optionsChanged);
		war3MapViewer.getSoundMixer().makeAudible();
	}

	/**
	 * The interface sounds a game plays from its start: clicks, rally points, item pick up, and the warnings and
	 * errors the skin names (no gold, under attack, ...).
	 */
	public List<String> getInterfaceSoundKeys() {
		final List<String> keys = new ArrayList<>(Arrays.asList(ClickableFrame.SOUND_KEY_INTERFACE_CLICK,
				ClickableFrame.SOUND_KEY_MENU_BUTTON_CLICK, ClickableFrame.SOUND_KEY_SUB_GROUP_SELECTION_CHANGE,
				"InterfaceError", "RallyPointPlace", "PlaceBuildingDefault", "AutoCastButtonClick", "MapPing",
				"ItemGet", "ItemDrop"));
		for (final String field : this.rootFrame.getSkinFieldNames()) {
			if (field.endsWith("Sound")) {
				keys.add(this.rootFrame.getSkinField(field));
			}
		}
		return keys;
	}

	/**
	 * Called "main" because this was originally written in JASS so that maps could
	 * override it, and I may convert it back to the JASS at some point.
	 */
	@Override
	public void main() {
		while (!mainNext()) {
		}
	}

	private int mainSlice;

	/**
	 * {@link #main} one slice at a time, the skin and templates, the console and its bars, the panels, then the rest, so a
	 * loading screen can draw between them.
	 *
	 * @return whether main is done
	 */
	public boolean mainNext() {
		switch (this.mainSlice++) {
		case 0:
			mainSkinAndTemplates();
			return false;
		case 1:
			mainConsoleAndBars();
			return false;
		case 2:
			mainPanels();
			return false;
		default:
			mainRest();
			return true;
		}
	}

	private void mainSkinAndTemplates() {
		// =================================
		// Load skins and templates
		// =================================
		long part = System.nanoTime();
		final CRace race = this.localPlayer.getRace();
		final CRaceManagerEntry raceEntry = this.war3MapViewer.getGameData().getRaceManager().get(race);
		final String racialSkinKey = raceEntry.getKey();
		final int racialCommandIndex = raceEntry.getRaceId() - 1;

		this.rootFrame = new GameUI(this.dataSource, GameUI.loadSkin(this.dataSource, racialSkinKey), this.uiViewport,
				this.uiScene, this.war3MapViewer, racialCommandIndex, this.war3MapViewer.getAllObjectData().getWts(),
				this.war3MapViewer.getGameData().getGameVersion());
		this.rootFrameListener.onCreate(this.rootFrame);
		final FrameTemplatePrefetch prefetch = this.war3MapViewer.takeFrameTemplatePrefetch();
		final FrameTemplateEnvironment readAhead = prefetch == null ? null : prefetch.await();
		if (readAhead != null) {
			this.rootFrame.adoptTemplates(readAhead, prefetch.getTocFilePaths());
		}
		else {
			try {
				this.rootFrame.loadTOCFile(FRAME_DEF_TOC);
			}
			catch (final IOException exc) {
				throw new IllegalStateException("Unable to load FrameDef.toc", exc);
			}
			try {
				this.rootFrame.loadTOCFile(SMASH_FRAME_DEF_TOC);
			}
			catch (final IOException exc) {
				throw new IllegalStateException("Unable to load SmashFrameDef.toc", exc);
			}
		}
		if (this.rootFrame.getTemplates().getFrame("MinimapButtonBar") == null) {
			try {
				this.rootFrame.loadTOCFile("UI\\FrameDef\\SmashMinimapFrameDef.toc");
			}
			catch (final IOException exc) {
				throw new IllegalStateException("Unable to load SmashMinimapFrameDef.toc", exc);
			}
		}
		this.infoPanel.createIconBackdrops();
		PerfStats.setupPart("interface skin and templates", part);
	}

	private void mainConsoleAndBars() {
		long part = System.nanoTime();
		// =================================
		// Load major UI components
		// =================================
		// Console UI is the background with the racial theme
		this.consoleUI = this.rootFrame.createSimpleFrame("ConsoleUI", this.rootFrame, 0);
		this.consoleUI.setSetAllPoints(true);
		// The console tiles leave their openings transparent for this black backdrop; the world is drawn a little
		// lower than the openings begin, so without it thin bands of the world show in the command card and info panel.
		if (this.consoleUI instanceof SimpleFrame) {
			final UIFrame consoleBackdrop = this.rootFrame.getTemplates().getFrame("ConsoleUIBackdrop") != null
					? this.rootFrame.createFrame("ConsoleUIBackdrop", this.consoleUI, 0, 0)
					: createClassicConsoleBackdrop();
			((SimpleFrame) this.consoleUI).add(0,
					consoleBackdrop instanceof BackdropFrame
							? new ConsoleBackdrop((BackdropFrame) consoleBackdrop, this.infoPanel, this.consoleUI)
							: consoleBackdrop);
		}

		PerfStats.setupPart("interface console", part);
		part = System.nanoTime();
		this.menus.createTopBar();
		this.groups.createHeroBar();
		this.minimapPanel.createMinimapButtons();
		this.menus.createEscMenu();
		this.menus.createTimeIndicator();
		PerfStats.setupPart("interface bars and menus", part);
	}

	private void mainPanels() {
		long part = System.nanoTime();
		this.infoPanel.createPortrait();
		PerfStats.setupPart("interface portrait", part);
		part = System.nanoTime();
		this.infoPanel.createInfoPanel();
		this.infoPanel.createInfoPanelIcons();
		this.commands.createInventory();
		this.messages.createErrorMessageFrame();
		this.commands.createCommandCard();
		this.tooltips.createTooltips();
		this.cinematics.createCinematicPanel();
		PerfStats.setupPart("interface panels and command card", part);
	}

	private void mainRest() {
		long part = System.nanoTime();

		// this.tooltipFrame = this.rootFrame.createFrameByType("BACKDROP",
		// "SmashToolTipBackdrop", this.rootFrame, "", 0);

		if (this.war3MapViewer.getReplaySession() instanceof ReplayPlayback) {
			this.replayPanel = ReplayPanel.create(this.rootFrame, this.uiViewport, this.war3MapViewer,
					(ReplayPlayback) this.war3MapViewer.getReplaySession(), this.cameraManager, this.commandCard,
					new ReplayPanel.Host() {
						@Override
						public void restart(final int startTick) {
							restartReplay(startTick);
						}

						@Override
						public void viewingPlayerChanged() {
							MeleeUI.this.menus.refreshResourceBar();
						}
					}, this.reforgedSkin);
			((ReplayPlayback) this.war3MapViewer.getReplaySession()).setChatListener(this::replayChat);
			((ReplayPlayback) this.war3MapViewer.getReplaySession()).setSkipListener(this::showReplayMoment);
			if (this.replayPanel != null) {
				this.smashCommandButtons.setVisible(false);
			}
			this.questsButton.setEnabled(false);
		}

		this.cursorFrame = (SpriteFrame) this.rootFrame.createFrameByType("SPRITE", "SmashCursorFrame", this.rootFrame,
				"", 0);
		this.rootFrame.setSpriteFrameModel(this.cursorFrame, this.rootFrame.getSkinField("Cursor"));
		this.cursorFrame.setSequence("Normal");
		this.cursorFrame.setZDepth(1.0f);
		if (this.war3MapViewer.getGameData().isCatchCursor()) {
			Gdx.input.setCursorCatched(true);
		}
		this.includeFrames = new String[] { "EscMenuBackdrop", "ScriptDialog", "SmashHoverTip", "SmashHpBar" };
		if (this.war3MapViewer.getGameTurnManager() instanceof LockstepTurnManager) {
			final LanPlay play = ((LockstepTurnManager) this.war3MapViewer.getGameTurnManager()).play();
			if (play != null) {
				this.lanOverlay = new LanOverlay(this.rootFrame, this.uiViewport, play, new LanOverlay.Host() {
					@Override
					public void showLine(final String line) {
						MeleeUI.this.messages.showChatMessage(line, CHAT_MESSAGE_SECONDS);
					}

					@Override
					public void showChat(final LanPlay.WatcherChat chat) {
						if (MeleeUI.this.chat != null) {
							MeleeUI.this.chat.showWatcherLine(chat.name,
									chat.channel == LanProtocol.CHAT_ALL ? MeleeChat.Recipient.ALL
											: MeleeChat.Recipient.OBSERVERS,
									chat.text);
						}
					}

					@Override
					public void leave() {
						quitMission();
					}
				});
			}
		}
		this.ignoreFrames = new String[] { "SmashHoverTip", "SmashHpBar", "SmashGameMessageFrame" };

		PerfStats.setupPart("interface cursor and replay panel", part);
		part = System.nanoTime();
		this.meleeUIMinimap = this.minimapPanel.createMinimap(this.war3MapViewer);
		this.meleeUIMinimap.setHeroAlertShown(this.groups::isHeroAlertMinimapShown);
		this.meleeUIMinimap.setAllyColorFilter(this.war3MapViewer.getAllyColorFilter());
		this.meleeUIMinimap.setTerrainVisible(GameOptions.get().isMinimapTerrain());
		this.meleeUIMinimap.setAltIconSource(this.war3MapViewer::getAltMinimapIcon,
				(path) -> ImageUtils.getAnyExtensionTexture(this.war3MapViewer.mapMpq, path));
		this.meleeUIMinimap.setScriptIconSource(this.war3MapViewer::getScriptMinimapIcons);
		this.meleeUIMinimap.setTerrainTextureSource(this.war3MapViewer.scriptUiState::getMinimapTerrainTexture);

		this.meleeUIAbilityActivationReceiver = new MeleeUIAbilityActivationReceiver(
				this.war3MapViewer.getLocalPlayerIndex(), this.messages::getUiSoundForError);

		this.commands.createRallyIndicators();

		this.modalFrames = new UIFrame[] { this.allianceDialog.getFrame(), this.questDialog.getFrame(),
				this.chat.getDialogFrame(), this.messageLog.getFrame(), this.smashEscMenu };
		this.modalFramesShown = new boolean[this.modalFrames.length];
		// Last among the root's children, so that the dialogs take clicks before the console frames under them.
		for (final UIFrame modalFrame : this.modalFrames) {
			this.rootFrame.remove(modalFrame);
			this.rootFrame.add(modalFrame);
		}

		PerfStats.setupPart("interface minimap", part);
		part = System.nanoTime();
		this.rootFrame.positionBounds(this.rootFrame, this.uiViewport);

		this.input.selectUnit(null);
		PerfStats.setupPart("interface layout", part);
	}

	@Override
	public void onClick(final int abilityHandleId, final int orderId, final boolean rightClick) {
		this.commands.onClick(abilityHandleId, orderId, rightClick);
	}

	@Override
	public void openMenu(final int orderId) {
		this.commands.openMenu(orderId);
	}

	/**
	 * A player command even when a script's ForceUIKey opened the menu: 1.29 replays hold opening the skill and build
	 * menus as player actions, and the event comes with the command, not in the middle of the script.
	 */
	void fireMenuGameEvent(final JassGameEventsWar3 event) {
		this.unitOrderListener.fireGameEvent(event.getEventId());
	}

	/**
	 * Scripts and the game's own changes reach the interface in the middle of a step. What they make it do belongs to
	 * the game already and happens again in a replay, so it stays out of the player's command stream.
	 */
	void runSimulationDriven(final Runnable action) {
		this.simulationDrivenDepth++;
		try {
			action.run();
		}
		finally {
			this.simulationDrivenDepth--;
		}
	}

	boolean isSimulationDriven() {
		return this.simulationDrivenDepth > 0;
	}

	public void setReservedLocalHeroButtons(final int reserved) {
		this.groups.setReservedLocalHeroButtons(reserved);
	}

	public void forceUIKey(final String key) {
		this.commands.forceUIKey(key);
	}

	@Override
	public void showInterfaceError(final int playerIndex, final String externStringKey) {
		this.messages.showInterfaceError(playerIndex, externStringKey);
	}

	@Override
	public void showCommandErrorWithoutSound(final int playerIndex, final String message) {
		this.messages.showCommandErrorWithoutSound(playerIndex, message);
	}

	public void showGameMessage(final String message, final float expireTime) {
		this.messages.showGameMessage(message, expireTime);
	}

	@Override
	public void showUpgradeCompleteAlert(final int playerIndex, final War3ID queuedRawcode, final int level) {
		this.messages.showUpgradeCompleteAlert(playerIndex, queuedRawcode, level);
	}

	@Override
	public void update(final float deltaTime) {
		this.input.flushScriptSelection();
		this.war3MapViewer.scriptUiState.flushFrames();
		updateWorldFrameViewport();
		this.originFrames.update();
		this.menus.updateAlliesButton();
		if (this.replayPanel != null) {
			this.replayPanel.update(deltaTime);
			if (this.war3MapViewer.getViewingPlayerIndex() != this.war3MapViewer.getLocalPlayerIndex()) {
				// The resource bar's events are the local player's; another player's resources are looked up.
				this.menus.refreshResourceBar();
			}
		}
		this.cinematics.updateInterfaceFade(deltaTime);
		this.portrait.update(deltaTime);
		this.cineFilter.update(deltaTime);
		this.input.flushMouseMove();
		this.questDialog.update();
		this.chat.followKeyboard();
		this.menus.followKeyboard();
		this.messages.liftGameMessages();
		if (this.questButtonFlashTime >= 0) {
			this.questButtonFlashTime += deltaTime;
			this.questsButton.setLit(ButtonFlash.isLit(this.questButtonFlashTime));
		}
		if (this.portrait.isResetNeeded()) {
			this.portrait.resetCinematicSequence();
			this.portrait.setSelectedUnit(this.selectedUnit);
		}
		this.cinematics.advanceScene(this.war3MapViewer.isGamePaused() ? 0
				: deltaTime * this.war3MapViewer.getTimeScale() * this.war3MapViewer.getReplaySpeed());
		if (this.cinematicPanel.isVisible()) {
			this.cinematicPortrait.update(deltaTime);
		}

		final int baseMouseX = VirtualCursor.getX();
		int mouseX = baseMouseX;
		final int baseMouseY = VirtualCursor.getY();
		int mouseY = baseMouseY;
		final int minX = this.uiViewport.getScreenX();
		final int maxX = minX + this.uiViewport.getScreenWidth();
		final int minY = this.uiViewport.getScreenY();
		final int maxY = minY + this.uiViewport.getScreenHeight();
		// Without a captured cursor (Android) only a real mouse scrolls at the edges, never a finger or pen.
		final boolean padMouse = !this.war3MapViewer.getGameData().isCatchCursor() && VirtualCursor.isMouseOver();
		final boolean edgeScroll = (this.war3MapViewer.getGameData().isCatchCursor() || padMouse)
				&& this.userControlEnabled && !GameOptions.get().isMouseScrollDisabled();
		final int edgeX = padMouse ? VirtualCursor.getMouseX() : mouseX;
		final int edgeY = padMouse ? VirtualCursor.getMouseY() : mouseY;
		final boolean left = (edgeX <= (minX + 3)) && edgeScroll;
		final boolean right = (edgeX >= (maxX - 3)) && edgeScroll;
		final boolean up = (edgeY <= (minY + 3)) && edgeScroll;
		final boolean down = (edgeY >= (maxY - 3)) && edgeScroll;
		this.cameraManager.applyVelocity(deltaTime, up, down, left, right);

		mouseX = Math.max(minX, Math.min(maxX, mouseX));
		mouseY = Math.max(minY, Math.min(maxY, mouseY));
		if (Gdx.input.isCursorCatched()) {
			if (this.war3MapViewer.getGameData().isCatchCursor() && this.showing) {
				Gdx.input.setCursorPosition(mouseX, mouseY);
			}
		}
		this.hpBarFrameIndex = 0;
		if (!this.allowDrag) {
			// While a building's placement ghost follows the cursor, what it passes over gets no bar or hover text.
			if ((this.mouseOverUnit != null) && isSelectionOn(PRE_SELECT_OFF) && (this.cursorModelInstance == null)
					&& this.input.isUnitSelectable(this.mouseOverUnit)) {
				final SimpleStatusBarFrame simpleStatusBarFrame = this.tooltips.getHpBar();
				this.tooltips.positionHealthBar(simpleStatusBarFrame, this.mouseOverUnit, 1.0f);
				if (this.mouseOverUnit instanceof RenderUnit) {
					final RenderUnit renderUnit = (RenderUnit) this.mouseOverUnit;
					if (renderUnit.getSimulationUnit().getMaximumMana() > 0) {
						final SimpleStatusBarFrame simpleStatusManaBarFrame = this.tooltips.getHpBar();
						this.tooltips.positionManaBar(simpleStatusManaBarFrame, renderUnit, 1.0f);
					}
				}
				final String hoverTipTextValue = this.tooltips.getWorldFrameHoverTipText(this.war3MapViewer.simulation,
						this.mouseOverUnit);
				this.hovertipFrame.setVisible(hoverTipTextValue != null);
				if (hoverTipTextValue != null) {
					this.rootFrame.setText(this.hovertipText, hoverTipTextValue);
					final float predictedViewportHeight = this.hovertipText.getPredictedViewportHeight()
							+ GameUI.convertY(this.uiViewport, 0.009f);
					this.hovertipFrame.setHeight(predictedViewportHeight);
					this.hovertipFrame.setWidth(
							this.hovertipText.getPredictedViewportWidth() + GameUI.convertX(this.uiViewport, 0.012f));
					final float hovertipGap = GameUI.convertY(this.uiViewport, 0.003f);
					if ((this.hovertipSetPointFrame != this.hovertipFrame)
							|| (this.hovertipSetPoint.getOther() != simpleStatusBarFrame)
							|| (this.hovertipSetPoint.getY() != hovertipGap)) {
						this.hovertipSetPoint = new SetPoint(FramePoint.BOTTOM, simpleStatusBarFrame, FramePoint.TOP, 0,
								hovertipGap);
						this.hovertipSetPointFrame = this.hovertipFrame;
						this.hovertipFrame.addSetPoint(this.hovertipSetPoint);
					}
					this.hovertipFrame.positionBounds(this.rootFrame, this.uiViewport);
				}
				if (this.mouseOverUnit.getSimulationWidget().isInvulnerable()
						|| (this.mouseOverUnit instanceof RenderItem)) {
					// this is a bit silly, for now I'm using it to position the "Gold" text on gold
					// mines even though they are invulnerable
					simpleStatusBarFrame.setVisible(false);
				}
			}
			else {
				this.hovertipFrame.setVisible(false);
			}
		}
		else if ((this.draggingMouseButton == Input.Buttons.LEFT) && isSelectionOn(DRAG_SELECT_OFF)) {
			final float minDragX = Math.min(this.lastMouseClickLocation.x, this.lastMouseDragStart.x);
			final float minDragY = Math.min(this.lastMouseClickLocation.y, this.lastMouseDragStart.y);
			final float maxDragX = Math.max(this.lastMouseClickLocation.x, this.lastMouseDragStart.x);
			final float maxDragY = Math.max(this.lastMouseClickLocation.y, this.lastMouseDragStart.y);
			this.tempRect.set(minDragX, minDragY, maxDragX - minDragX, maxDragY - minDragY);
			this.dragSelectPreviewUnitsUpcoming.clear();
			this.war3MapViewer.simulation.getWorldCollision().enumUnitsInRect(this.tempRect,
					this.dragSelectPreviewEnum);
			for (final RenderUnit unit : this.dragSelectPreviewUnits) {
				if (!this.dragSelectPreviewUnitsUpcoming.contains(unit)) {
					this.war3MapViewer.clearUnitMouseOverHighlight(unit);
				}
			}
			final Set<RenderUnit> temp = this.dragSelectPreviewUnits;
			this.dragSelectPreviewUnits = this.dragSelectPreviewUnitsUpcoming;
			this.dragSelectPreviewUnitsUpcoming = temp;
		}
		if (GameOptions.get().isAlwaysShowStatusBars() && !MeleeUIInput.isAltDown() && this.consoleUI.isVisible()) {
			this.tooltips.showAllStatusBars();
		}
		for (int i = this.hpBarFrameIndex; i < this.hpBarFrames.size(); i++) {
			this.hpBarFrames.get(i).setVisible(false);
		}
		if (this.simpleProgressIndicator.isVisible() && (this.simpleProgressIndicatorDurationRemaining > 0)) {
			// NOTE: this is some approximation, not an accurate bar visual
			this.simpleProgressIndicatorDurationRemaining -= deltaTime;
			this.simpleProgressIndicator
					.setValue(this.simpleProgressIndicatorDurationRemaining / this.simpleProgressIndicatorDurationMax);
			;
		}

		screenCoordsVector.set(mouseX, mouseY);
		this.uiViewport.unproject(screenCoordsVector);
		this.cursorFrame.setFramePointX(FramePoint.LEFT, screenCoordsVector.x);
		this.cursorFrame.setFramePointY(FramePoint.BOTTOM, screenCoordsVector.y);
		if (!this.war3MapViewer.scriptUiState.isCursorEnabled()) {
			this.cursorFrame.setVisible(false);
			this.scriptCursorHidden = true;
		}
		else if (this.scriptCursorHidden) {
			this.scriptCursorHidden = false;
			this.cursorFrame.setVisible(true);
		}

		if (this.activeCommand != null) {
			if (this.draggingItem != null) {
				this.commands.setCursorState(MenuCursorState.HOLD_ITEM);
			}
			else {
				this.commands.setCursorState(MenuCursorState.TARGET_CURSOR);
				this.activeCommand.visit(this.commands.cursorTargetSetupVisitor.reset(baseMouseX, baseMouseY));
			}
		}
		else {
			if (this.cursorModelInstance != null) {
				this.cursorModelInstance.detach();
				this.cursorModelInstance = null;
				this.cursorFrame.setVisible(true);
			}
			if (this.placementCursor != null) {
				this.placementCursor.destroy(Gdx.gl30, this.war3MapViewer.terrain.centerOffset);
				this.placementCursor = null;
				this.cursorFrame.setVisible(true);
			}
			releaseBuildingPathingPreview();
			if (down) {
				if (left) {
					this.commands.setCursorState(MenuCursorState.SCROLL_DOWN_LEFT);
				}
				else if (right) {
					this.commands.setCursorState(MenuCursorState.SCROLL_DOWN_RIGHT);
				}
				else {
					this.commands.setCursorState(MenuCursorState.SCROLL_DOWN);
				}
			}
			else if (up) {
				if (left) {
					this.commands.setCursorState(MenuCursorState.SCROLL_UP_LEFT);
				}
				else if (right) {
					this.commands.setCursorState(MenuCursorState.SCROLL_UP_RIGHT);
				}
				else {
					this.commands.setCursorState(MenuCursorState.SCROLL_UP);
				}
			}
			else if (left) {
				this.commands.setCursorState(MenuCursorState.SCROLL_LEFT);
			}
			else if (right) {
				this.commands.setCursorState(MenuCursorState.SCROLL_RIGHT);
			}
			else if (this.minimapSignalTargeting) {
				this.commands.setCursorState(MenuCursorState.TARGET_CURSOR);
			}
			else if (this.mouseOverUnit != null) {
				if (this.mouseOverUnit instanceof RenderUnit) {
					final RenderUnit mouseOverUnitUnit = (RenderUnit) this.mouseOverUnit;
					final int playerIndex = mouseOverUnitUnit.getSimulationUnit().getFakePlayerIndex();
					if (!this.localPlayer.hasAlliance(playerIndex, CAllianceType.PASSIVE)) {
						this.commands.setCursorState(MenuCursorState.SELECT, Color.RED);
					}
					else if (this.localPlayer.hasAlliance(playerIndex, CAllianceType.SHARED_CONTROL)
							|| this.localPlayer.hasAlliance(mouseOverUnitUnit.getSimulationUnit().getPlayerIndex(),
									CAllianceType.SHARED_CONTROL)) {
						this.commands.setCursorState(MenuCursorState.SELECT, Color.GREEN);
					}
					else {
						this.commands.setCursorState(MenuCursorState.SELECT, Color.YELLOW);
					}
				}
				else {
					this.commands.setCursorState(MenuCursorState.SELECT, Color.YELLOW);
				}
			}
			else {
				this.commands.setCursorState(MenuCursorState.NORMAL);
			}
		}
		if (this.selectedUnit != null) {
			this.infoPanel.updateBuildTimeIndicators();
			this.infoPanel.refreshCargoIfChanged();
		}
		if ((this.mouseOverUIFrame == null) && !Float.isNaN(this.panelTipX)) {
			this.panelTipRefreshTime += deltaTime;
			// Resources, the clock, the selection and its values change under a resting mouse.
			if (this.panelTipRefreshTime >= PANEL_TIP_REFRESH_PERIOD) {
				this.panelTipRefreshTime = 0;
				this.input.updatePanelTip(this.panelTipX, this.panelTipY, true);
			}
		}

		this.cameraManager.frameTime(deltaTime, this.war3MapViewer.isGamePaused());
		this.cameraManager.updateCamera();
		if (this.allowDrag && (this.draggingMouseButton == Input.Buttons.MIDDLE)) {
			// in case camera updates while dragging mouse, update where we think mouse is
			// in 3d
			this.lastMouseDragCameraTargetStart.set(this.cameraManager.target);
		}

		final long currentMillis = TimeUtils.millis();
		this.messages.advanceGameClock(this.war3MapViewer.isGamePaused() ? 0
				: deltaTime * this.war3MapViewer.getTimeScale() * this.war3MapViewer.getReplaySpeed());
		this.messages.updateMessages(currentMillis);
		if (this.lanOverlay != null) {
			this.lanOverlay.update(deltaTime);
		}
		final int buttonEffectsVersion = this.war3MapViewer.scriptUiState.getCommandButtonEffectsVersion();
		if (buttonEffectsVersion != this.shownButtonEffectsVersion) {
			this.shownButtonEffectsVersion = buttonEffectsVersion;
			if (this.selectedUnit != null) {
				this.infoPanel.reloadSelectedUnitUI(this.selectedUnit);
			}
		}
		this.musicPlayer.setPausedByFocus(this.war3MapViewer.scriptUiState.isMusicHeldByFocus());
		this.musicPlayer.update();
		this.minimapPanel.updateMinimapPings(currentMillis);
		this.minimapPanel.updateCreepCamps(currentMillis);
		final CMultiboard scriptBoard = this.war3MapViewer.simulation.getShownMultiboard();
		// The score screen replaces the whole interface; a board still shown would show itself again every frame.
		this.multiboard.update(this.scoreScreen.isVisible() ? null
				: this.teamResources.update((scriptBoard != null) && scriptBoard.isDisplayed() ? scriptBoard : null));
		if (!this.scoreScreen.isVisible()) {
			this.messages.layoutTimerDialogs();
			for (final CTimerDialog timerDialog : this.timerDialogs) {
				timerDialog.update(this.rootFrame, this.war3MapViewer.simulation);
			}
		}
		this.groups.updateHeroBar();
		this.groups.updateIdleWorkers();
		if (VirtualCursor.isCursorHidden()) {
			if (this.cursorFrame.isVisible()) {
				this.cursorFrame.setVisible(false);
				this.cursorHiddenForFinger = true;
			}
		}
		else if (this.cursorHiddenForFinger) {
			this.cursorHiddenForFinger = false;
			this.cursorFrame.setVisible(this.userControlEnabled && this.showing && (this.cursorModelInstance == null)
					&& (this.placementCursor == null));
		}
	}

	/** Measured on 1.29 with a dialog on the screen: the world keeps 0.39 of its brightness, the buttons about 0.47. */
	private static final float DIALOG_WORLD_DIM = 0.61f;
	private static final float DIALOG_BUTTON_DIM = 0.53f;
	private final Rectangle dialogDimPortrait = new Rectangle();

	boolean isScriptDialogShown() {
		for (final CScriptDialog dialog : this.scriptDialogs) {
			if (dialog.isVisible()) {
				return true;
			}
		}
		return false;
	}

	/** The world darkens under a dialog, the portrait scene drawn before the interface stays as it is. */
	private void renderDialogWorldDim(final SpriteBatch batch, final Texture black) {
		final float left = (this.uiViewport.getMinWorldWidth() - this.uiViewport.getWorldWidth()) / 2;
		final float right = left + this.uiViewport.getWorldWidth();
		final float top = this.uiViewport.getWorldHeight();
		batch.setColor(1, 1, 1, DIALOG_WORLD_DIM);
		if (this.infoPanel.isPortraitShown()) {
			final Rectangle portrait = this.infoPanel.portraitBounds(this.dialogDimPortrait);
			final float openLeft = Math.max(left, Math.min(right, portrait.x));
			final float openRight = Math.max(openLeft, Math.min(right, portrait.x + portrait.width));
			final float openBottom = Math.max(0, Math.min(top, portrait.y));
			final float openTop = Math.max(openBottom, Math.min(top, portrait.y + portrait.height));
			batch.draw(black, left, 0, openLeft - left, top);
			batch.draw(black, openRight, 0, right - openRight, top);
			batch.draw(black, openLeft, 0, openRight - openLeft, openBottom);
			batch.draw(black, openLeft, openTop, openRight - openLeft, top - openTop);
		}
		else {
			batch.draw(black, left, 0, right - left, top);
		}
		batch.setColor(1, 1, 1, 1);
	}

	/** The hero buttons, the command card and the inventory darken under a dialog; the portrait and minimap do not. */
	private void renderDialogButtonDim(final SpriteBatch batch, final Texture black) {
		batch.setColor(1, 1, 1, DIALOG_BUTTON_DIM);
		for (final CommandCardIcon[] row : this.commandCard) {
			drawDialogDim(batch, black, row);
		}
		for (final CommandCardIcon[] row : this.inventoryIcons) {
			drawDialogDim(batch, black, row);
		}
		batch.setColor(1, 1, 1, 1);
		this.groups.renderDialogDim(batch, black, DIALOG_BUTTON_DIM);
	}

	private static void drawDialogDim(final SpriteBatch batch, final Texture black, final CommandCardIcon[] icons) {
		for (final CommandCardIcon icon : icons) {
			if ((icon != null) && icon.isVisible()) {
				final Rectangle bounds = icon.getRenderBounds();
				batch.draw(black, bounds.x, bounds.y, bounds.width, bounds.height);
			}
		}
	}

	@Override
	public void render(final SpriteBatch batch, final GlyphLayout glyphLayout) {
		// The dialogs are drawn by renderModal, over the minimap and the interface's 3D models drawn after this.
		for (int i = 0; i < this.modalFrames.length; i++) {
			this.modalFramesShown[i] = this.modalFrames[i].isVisible();
			this.modalFrames[i].setVisible(false);
		}
		this.cineFilter.render(batch, this.rootFrame,
				(this.uiViewport.getMinWorldWidth() - this.uiViewport.getWorldWidth()) / 2, 0, this.uiViewport.getWorldWidth(),
				this.uiViewport.getWorldHeight());
		if (this.cinematics.isScriptDialogDim()) {
			final Texture black = this.rootFrame.loadTexture("Textures\\Black32.blp");
			if (black != null) {
				renderDialogWorldDim(batch, black);
			}
		}
		this.rootFrame.render(batch, this.rootFrame.getFont20(), glyphLayout);
		if (this.cinematics.isScriptDialogDim()) {
			final Texture black = this.rootFrame.loadTexture("Textures\\Black32.blp");
			if (black != null) {
				renderDialogButtonDim(batch, black);
			}
		}
		for (int i = 0; i < this.modalFrames.length; i++) {
			this.modalFrames[i].setVisible(this.modalFramesShown[i]);
		}
		if (this.selectedUnit != null) {
			this.rootFrame.getFont20().setColor(Color.WHITE);

		}

		if (this.scoreScreen.isVisible()) {
			return;
		}
		this.minimapPanel.updateMinimapCameraFootprint();
		final float gameInterfaceAlpha = this.cinematics.getGameInterfaceAlpha();
		final FadingSpriteBatch fadingBatch = (gameInterfaceAlpha < 1f) && (batch instanceof FadingSpriteBatch)
				? (FadingSpriteBatch) batch
				: null;
		if (fadingBatch != null) {
			fadingBatch.setAlphaScale(gameInterfaceAlpha);
		}
		this.meleeUIMinimap.render(this.war3MapViewer.simulation, batch, this.war3MapViewer.units,
				this.war3MapViewer.getFogGhosts(),
				this.war3MapViewer.simulation.getPathingGrid(), this.war3MapViewer.getFogOfWar(),
				this.war3MapViewer.simulation.getPlayer(this.war3MapViewer.getLocalPlayerIndex()));
		if (fadingBatch != null) {
			fadingBatch.setAlphaScale(1f);
		}
		this.timeIndicator.setSequence(this.war3MapViewer.simulation.isFalseTimeOfDay() ? 1 : 0);
		this.timeIndicator.setFrameByRatio(this.war3MapViewer.simulation.getGameTimeOfDay()
				/ this.war3MapViewer.simulation.getGameplayConstants().getGameDayHours());
		for (final TextTag textTag : this.war3MapViewer.textTags) {
			if (textTag.isVisible() && textTag.isShownTo(this.war3MapViewer.getLocalPlayerIndex())) {
				this.war3MapViewer.worldScene.camera.worldToScreen(screenCoordsVector, textTag.getPosition());
				if (this.war3MapViewer.worldScene.camera.rect.contains(screenCoordsVector.x,
						(Gdx.graphics.getHeight() - screenCoordsVector.y))) {
					final Vector2 unprojected = this.uiViewport.unproject(screenCoordsVector);
					unprojected.add(this.uiViewport.getMinWorldWidth() * textTag.getScreenCoordTravelOffset().x,
							this.uiViewport.getMinWorldHeight() * textTag.getScreenCoordTravelOffset().y);
					final float fontSize = GameUI.convertY(this.uiViewport,
							textTag.getFontHeight() * TEXT_TAG_FONT_SIZE_PER_HEIGHT);
					this.textTagFontParam.size = Math.max(1, Math.round(fontSize));
					// below: generateFont is a caching call, so hopefully this is not allocating
					// font object on every loop, which would be wasteful
					final BitmapFont myTextTagFont = this.rootFrame.getFontGenerator()
							.generateFont(this.textTagFontParam);
					final ColorCodedText textTagText = textTag.getLayout();
					textTagText.set(textTag.getText(), myTextTagFont, glyphLayout,
							GameUI.convertY(this.uiViewport, textTag.getFontHeight() * TEXT_TAG_LINE_SPACING_PER_HEIGHT),
							GameUI.convertX(this.uiViewport, TEXT_TAG_WRAP_WIDTH));
					float x = unprojected.x;
					float y = unprojected.y;
					if (textTag.isCentered()) {
						x -= (textTagText.getWidth() / 2);
						y += (textTagText.getHeight() / 2);
					}
					else {
						if (textTag.isCenteredOnPoint()) {
							x -= (textTagText.getWidth() / 2);
						}
						// The point is the bottom of the last line, below its descenders.
						y += textTagText.getHeight() - myTextTagFont.getDescent();
					}
					final Color textTagColor = this.textTagDrawColor.set(textTag.getColor());
					textTagColor.a = textTag.getDrawAlpha();
					this.textTagShadowColor.a = textTagColor.a;
					textTagText.drawInColor(batch, x + GameUI.convertX(this.uiViewport, TEXT_TAG_SHADOW_OFFSET_X),
							y - GameUI.convertY(this.uiViewport, TEXT_TAG_SHADOW_OFFSET_Y), this.textTagShadowColor);
					textTagText.draw(batch, x, y, textTagColor);
				}
			}
		}
		if (fadingBatch != null) {
			fadingBatch.setAlphaScale(gameInterfaceAlpha);
		}
		final ControlGroupBar bar = gameInterfaceAlpha > 0f ? this.groups.getControlGroupBar() : null;
		if (bar != null) {
			final int[] groupSizes = this.controlGroupBarSizes;
			final Texture[] groupIcons = this.controlGroupBarIcons;
			for (int group = 0; group < ControlGroupBar.SLOT_COUNT; group++) {
				final List<RenderUnit> members = group < this.controlGroups.size() ? this.controlGroups.get(group)
						: Collections.<RenderUnit>emptyList();
				groupSizes[group] = members.size();
				final CUnit strongest = MeleeUIControlGroups.strongestUnit(members);
				final UnitIconUI strongestUI = strongest == null ? null
						: this.war3MapViewer.getAbilityDataUI().getUnitUI(strongest.getTypeId());
				groupIcons[group] = strongestUI == null ? null : strongestUI.getIcon();
			}
			bar.render(batch, this.rootFrame.getFont20(), groupSizes, groupIcons);
		}
		if (fadingBatch != null) {
			fadingBatch.setAlphaScale(1f);
		}
		if (WarsmashConstants.SHOW_FPS) {
			final BitmapFont font14 = this.rootFrame.getFont14();
			font14.setColor(Color.YELLOW);
			final float screenLeft = (this.uiViewport.getMinWorldWidth() - this.uiViewport.getWorldWidth()) / 2;
			font14.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), screenLeft + 8,
					this.uiViewport.getMinWorldHeight() - 8);
		}
		if ((this.draggingMouseButton == Input.Buttons.LEFT) && isSelectionOn(DRAG_SELECT_UI_OFF)
				&& isSelectionOn(DRAG_SELECT_OFF)) {
			batch.end();
			this.shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
			this.shapeRenderer.setColor(Color.GREEN);
			Gdx.gl.glLineWidth(2);
			this.shapeRenderer.begin(ShapeType.Line);
			this.cameraManager.camera.worldToScreen(screenCoordsVector, this.lastMouseDragStart);
			final Vector2 unprojected = this.uiViewport.unproject(screenCoordsVector);
			final float x = unprojected.x;
			final float y = unprojected.y;
			this.cameraManager.camera.worldToScreen(screenCoordsVector, this.lastMouseClickLocation);
			final Vector2 unprojectedEnd = this.uiViewport.unproject(screenCoordsVector);
			final float minX = Math.min(x, unprojectedEnd.x);
			final float minY = Math.min(y, unprojectedEnd.y);
			this.shapeRenderer.rect(minX, minY, Math.max(x, unprojectedEnd.x) - minX,
					Math.max(y, unprojectedEnd.y) - minY);
			this.shapeRenderer.end();
			Gdx.gl.glLineWidth(1);
			batch.begin();
		}
	}

	@Override
	public void renderModal(final SpriteBatch batch, final GlyphLayout glyphLayout) {
		for (final UIFrame modalFrame : this.modalFrames) {
			modalFrame.render(batch, this.rootFrame.getFont20(), glyphLayout);
		}
	}

	public void portraitTalk(final UnitSound us) {
		this.infoPanel.portraitTalk(us);
	}

	public void setDraggingItem(final CItem itemInSlot) {
		this.commands.setDraggingItem(itemInSlot);
	}

	public void selectUnit(RenderUnit unit) {
		this.input.selectUnit(unit);
	}

	@Override
	public void rallyPointChanged() {
		this.commands.rallyPointChanged();
	}

	@Override
	public void waypointsChanged() {
		this.commands.waypointsChanged();
	}

	@Override
	public void commandButton(final int buttonPositionX, final int buttonPositionY, final Texture icon,
			final int abilityHandleId, final int orderId, final int autoCastId, final boolean active,
			final boolean autoCastActive, final boolean menuButton, final String tip, final String uberTip,
			final char hotkey, final int goldCost, final int lumberCost, final int foodCost, final int manaCost,
			final float cooldownRemaining, final float cooldownMax, final int numberOverlay) {
		this.commands.commandButton(buttonPositionX, buttonPositionY, icon, abilityHandleId, orderId, autoCastId,
				active, autoCastActive, menuButton, tip, uberTip, hotkey, goldCost, lumberCost, foodCost, manaCost,
				cooldownRemaining, cooldownMax, numberOverlay);
	}

	@Override
	public void buff(final Texture icon, final int level, final String tip, final String uberTip,
			final boolean positive, final boolean leveled) {
		this.infoPanel.buff(icon, level, tip, uberTip, positive, leveled);
	}

	@Override
	public void timedLifeBar(final int level, final String toolTip, final float durationRemaining,
			final float durationMax) {
		this.infoPanel.timedLifeBar(level, toolTip, durationRemaining, durationMax);
	}

	@Override
	public void resize(final int width, final int height) {
		this.cameraManager.resize(setupWorldFrameViewport(width, height));
		this.infoPanel.positionPortrait();
		this.groups.anchorHeroBar();
		this.cinematics.anchorCinematicPanel();
		// Timer dialogs and the leaderboard follow the right screen edge, which moves with the window width.
		this.timerDialogSlots.clear();
		this.leaderboardSlot = -1;
		this.multiboard.resetAnchor();
		if (this.rootFrame.isAutoPosition()) {
			this.rootFrame.positionBounds(this.rootFrame, this.uiViewport);
		}
		// The frame under the mouse is another one now; the next mouse move shows its tooltip where it belongs.
		this.input.endTouchHover();
	}

	/**
	 * Reforged cinematics show the world on the whole screen, where the classic letterbox keeps the world in the
	 * area above the console.
	 */
	void updateWorldFrameViewport() {
		final boolean fullScreen = this.reforgedCinematics && this.cinematicPanel.isVisible();
		final Rectangle scriptRect = scriptWorldRectangle();
		final boolean scriptChanged = (scriptRect == null) ? (this.scriptWorldRect != null)
				: !scriptRect.equals(this.scriptWorldRect);
		if (((fullScreen != this.worldFrameFullScreen) || scriptChanged) && (this.worldFrameWidth > 0)) {
			this.worldFrameFullScreen = fullScreen;
			this.scriptWorldRect = scriptRect;
			this.cameraManager.resize(setupWorldFrameViewport(this.worldFrameWidth, this.worldFrameHeight));
		}
	}

	/**
	 * A script that hides the game's interface puts the world where its world frame is, which is the whole screen
	 * unless the script moves or sizes that frame; null while the classic console keeps the world to its strip.
	 */
	private Rectangle scriptWorldRectangle() {
		final ScriptUiState state = this.war3MapViewer.scriptUiState;
		if (!state.isOriginFramesHidden() || (this.worldFrameWidth <= 0)) {
			return null;
		}
		final UIFrame frame = state.getWorldFrame();
		if (frame == null) {
			return new Rectangle(0, 0, this.worldFrameWidth, this.worldFrameHeight);
		}
		final float uiWidth = this.uiViewport.getWorldWidth();
		final float uiHeight = this.uiViewport.getWorldHeight();
		final float left = frame.getFramePointX(FramePoint.LEFT);
		final float right = frame.getFramePointX(FramePoint.RIGHT);
		final float bottom = frame.getFramePointY(FramePoint.BOTTOM);
		final float top = frame.getFramePointY(FramePoint.TOP);
		if (!(right > left) || !(top > bottom) || !(uiWidth > 0) || !(uiHeight > 0)) {
			return new Rectangle(0, 0, this.worldFrameWidth, this.worldFrameHeight);
		}
		final float scaleX = this.worldFrameWidth / uiWidth;
		final float scaleY = this.worldFrameHeight / uiHeight;
		final int x0 = Math.max(0, Math.round(left * scaleX));
		final int y0 = Math.max(0, Math.round(bottom * scaleY));
		final int x1 = Math.min(this.worldFrameWidth, Math.round(right * scaleX));
		final int y1 = Math.min(this.worldFrameHeight, Math.round(top * scaleY));
		if ((x1 <= x0) || (y1 <= y0)) {
			return new Rectangle(0, 0, this.worldFrameWidth, this.worldFrameHeight);
		}
		return new Rectangle(x0, y0, x1 - x0, y1 - y0);
	}

	private float smoothedX = Float.NaN;
	private float smoothedY;
	private float smoothedHeight;

	/**
	 * The camera follows a blurred ground height, not the height under the target: on 1.29 it sits tens of units
	 * under a hilltop and above a valley floor. A Gaussian over the ground heights fits the measured points.
	 */
	private float smoothedGroundHeight(final float x, final float y) {
		if ((x == this.smoothedX) && (y == this.smoothedY)) {
			return this.smoothedHeight;
		}
		float sum = 0;
		float weights = 0;
		for (int dy = -CAMERA_HEIGHT_RADIUS; dy <= CAMERA_HEIGHT_RADIUS; dy++) {
			for (int dx = -CAMERA_HEIGHT_RADIUS; dx <= CAMERA_HEIGHT_RADIUS; dx++) {
				final float distance2 = ((dx * dx) + (dy * dy)) * CAMERA_HEIGHT_STEP * CAMERA_HEIGHT_STEP;
				final float weight = (float) Math.exp(-0.5 * distance2
						/ (CAMERA_HEIGHT_SIGMA * CAMERA_HEIGHT_SIGMA));
				sum += weight * this.war3MapViewer.terrain.getGroundHeight(x + (dx * CAMERA_HEIGHT_STEP),
						y + (dy * CAMERA_HEIGHT_STEP));
				weights += weight;
			}
		}
		this.smoothedX = x;
		this.smoothedY = y;
		this.smoothedHeight = sum / weights;
		return this.smoothedHeight;
	}

	/**
	 * The Classic console definitions (1.29 to 1.31) have no backdrop, yet their tiles are transparent in the lower part
	 * of the openings too, from about 0.117 down, where the world is still drawn to 0.111; the same black backdrop as
	 * the later definitions' fills that strip.
	 */
	private BackdropFrame createClassicConsoleBackdrop() {
		final BackdropFrame backdrop = new BackdropFrame("ConsoleUIBackdrop", this.consoleUI, false, false,
				this.rootFrame.loadTexture("Textures\\Black32.blp"), EnumSet.noneOf(BackdropCornerFlags.class), 0, 0,
				new Vector4Definition(0, 0, 0, 0), null, false);
		backdrop.setHeight(GameUI.convertY(this.uiViewport, CLASSIC_CONSOLE_BACKDROP_HEIGHT));
		backdrop.addSetPoint(new SetPoint(FramePoint.BOTTOMLEFT, this.consoleUI, FramePoint.BOTTOMLEFT, 0, 0));
		backdrop.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT, this.consoleUI, FramePoint.BOTTOMRIGHT, 0, 0));
		return backdrop;
	}

	private Rectangle setupWorldFrameViewport(final int width, final int height) {
		this.worldFrameWidth = width;
		this.worldFrameHeight = height;
		if (this.scriptWorldRect != null) {
			this.tempRect.set(this.scriptWorldRect);
			this.cameraManager.setProjectionHeightScale(1);
			return this.tempRect;
		}
		this.tempRect.x = 0;
		this.tempRect.width = width;
		if (this.worldFrameFullScreen) {
			this.tempRect.y = 0;
			this.tempRect.height = height;
			this.cameraManager.setProjectionHeightScale(1);
			return this.tempRect;
		}
		// The classic world is projected into the strip between the top bar (0.015 of the 0.6 high UI) and the console
		// (from 0.126), but drawn up to the screen edge, since the bar's tiles leave its corners clear; so the
		// viewport is as tall as that strip is far from the screen top, with the field of view stretched to match.
		// The packages' world is projected into everything above their console.
		final boolean packageInterface = this.war3MapViewer.isPackageInterface();
		final float bottomHeight = (packageInterface ? 0.21333f : 0.21f) * height;
		final float topHeight = packageInterface ? 0 : 0.025f * height;
		final float stripHeight = height - topHeight - bottomHeight;
		final float viewportHeight = packageInterface ? stripHeight : stripHeight + topHeight * 2;
		this.tempRect.y = height - (int) viewportHeight;
		this.tempRect.height = (int) viewportHeight;
		this.cameraManager.setProjectionHeightScale(viewportHeight / stripHeight);
		return this.tempRect;
	}

	public void positionPortrait() {
		this.infoPanel.positionPortrait();
	}

	@Override
	public void lifeChanged() {
		this.infoPanel.lifeChanged();
	}

	@Override
	public void hideStateChanged() {
		this.infoPanel.hideStateChanged();
	}

	@Override
	public void manaChanged() {
		this.infoPanel.manaChanged();
	}

	@Override
	public void goldChanged() {
		this.menus.goldChanged();
	}

	@Override
	public void lumberChanged() {
		this.menus.lumberChanged();
	}

	@Override
	public void foodChanged() {
		this.menus.foodChanged();
	}

	/**
	 * A replay's Restart, or a seek back: the replay is loaded again and plays from its start at normal speed, on to
	 * the step undrawn.
	 */
	private void restartReplay(final int startTick) {
		final ReplaySession session = this.war3MapViewer.getReplaySession();
		if (!(session instanceof ReplayPlayback) || (((ReplayPlayback) session).getFile() == null)) {
			return;
		}
		final ReplayPlayback playback = (ReplayPlayback) session;
		this.war3MapViewer.getGameExit().setNextReplay(playback.getFile(), startTick, playback.isForced());
		this.war3MapViewer.setGamePaused(War3MapViewer.PauseSource.MENU, false);
		quitMission();
	}

	@Override
	public void upkeepChanged() {
		this.menus.upkeepChanged();
	}

	@Override
	public void heroDeath() {
		if (this.selectedUnit != null) {
			if (this.selectedUnit.getSimulationUnit().getUnitType().isRevivesHeroes()) {
				this.infoPanel.reloadSelectedUnitUI(this.selectedUnit);
			}
		}
	}

	@Override
	public void heroTokensChanged() {
		// TODO Auto-generated method stub
	}

	@Override
	public void ordersChanged() {
		this.infoPanel.reloadSelectedUnitUI(this.selectedUnit);
		if (this.mouseOverUIFrame instanceof ClickableActionFrame) {
			this.tooltips.loadTooltip((ClickableActionFrame) this.mouseOverUIFrame);
		}
	}

	@Override
	public void heroStatsChanged() {
		this.infoPanel.reloadSelectedUnitUI(this.selectedUnit);
	}

	@Override
	public void attacksChanged() {
		this.infoPanel.reloadSelectedUnitUI(this.selectedUnit);
	}

	@Override
	public void abilitiesChanged() {
		this.infoPanel.reloadSelectedUnitUI(this.selectedUnit);
	}

	@Override
	public void upgradesChanged() {
		this.infoPanel.reloadSelectedUnitUI(this.selectedUnit);
	}

	@Override
	public void inventoryChanged() {
		this.infoPanel.reloadSelectedUnitUI(this.selectedUnit);
	}

	@Override
	public void queueChanged() {
		this.infoPanel.reloadSelectedUnitUI(this.selectedUnit);
	}

	public RenderUnit getSelectedUnit() {
		return this.selectedUnit;
	}

	@Override
	public boolean keyDown(final int keycode) {
		sendScriptKeyEvent(keycode, true);
		return this.input.keyDown(keycode);
	}

	@Override
	public UIFrame getOriginFrame(final int originType, final int index) {
		return this.originFrames.get(originType, index);
	}

	@Override
	public void setFrameEventSink(final FrameEventSink sink) {
		this.frameEvents = sink;
	}

	@Override
	public void setScriptFocus(final UIFrame frame, final boolean focus) {
		if (focus) {
			if ((frame instanceof FocusableFrame) && ((FocusableFrame) frame).isFocusable()) {
				takeScriptFocus((FocusableFrame) frame);
			}
		}
		else if (frame == this.scriptFocus) {
			takeScriptFocus(null);
		}
	}

	void takeScriptFocus(final FocusableFrame frame) {
		if (frame == this.scriptFocus) {
			return;
		}
		if (this.scriptFocus != null) {
			this.scriptFocus.onFocusLost();
		}
		this.scriptFocus = frame;
		if (frame != null) {
			frame.onFocusGained();
		}
	}

	@Override
	public CPlayerUnitOrderListener getUnitOrderListener() {
		return this.unitOrderListener;
	}

	@Override
	public CUnit getMouseFocusUnit() {
		return this.mouseOverUnit instanceof RenderUnit ? ((RenderUnit) this.mouseOverUnit).getSimulationUnit() : null;
	}

	/** A key the player presses reaches the key triggers of a script as a player command, like every input. */
	private void sendScriptKeyEvent(final int keycode, final boolean down) {
		final int localPlayerIndex = this.war3MapViewer.getLocalPlayerIndex();
		if ((this.unitOrderListener == null) || (localPlayerIndex < 0)) {
			return;
		}
		final int virtualKey = OsKeys.toVirtual(keycode);
		final CPlayer player = this.war3MapViewer.simulation.getPlayer(localPlayerIndex);
		if ((virtualKey >= 0) && (player != null)
				&& player.hasPlayerEvents(down ? JassGameEventsWar3.EVENT_PLAYER_KEY_DOWN
						: JassGameEventsWar3.EVENT_PLAYER_KEY_UP)) {
			this.unitOrderListener.keyEvent(virtualKey, OsKeys.heldMetaKeys(Gdx.input), down);
		}
	}

	public boolean keyTyped(final char character) {
		return this.input.keyTyped(character);
	}

	@Override
	public boolean keyUp(final int keycode) {
		sendScriptKeyEvent(keycode, false);
		return this.input.keyUp(keycode);
	}

	@Override
	public boolean scrolled(final float amountX, final float amountY) {
		return this.input.scrolled(amountX, amountY);
	}

	@Override
	public boolean touchDown(final int screenX, final int screenY, final float worldScreenY, final int button) {
		this.input.sendMouseEvent(JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN, screenX, screenY, worldScreenY, button);
		return this.input.touchDown(screenX, screenY, worldScreenY, button);
	}

	@Override
	public boolean touchUp(final int screenX, final int screenY, final float worldScreenY, final int button) {
		this.input.sendMouseEvent(JassGameEventsWar3.EVENT_PLAYER_MOUSE_UP, screenX, screenY, worldScreenY, button);
		return this.input.touchUp(screenX, screenY, worldScreenY, button);
	}

	public void assignControlGroup(final int group, final boolean add) {
		this.groups.assignControlGroup(group, add);
	}

	public boolean selectControlGroup(final int group) {
		return this.groups.selectControlGroup(group);
	}

	public List<RenderUnit> getControlGroup(final int group) {
		return this.groups.getControlGroup(group);
	}

	public boolean isOverSlider(final int screenX, final int screenY) {
		return this.input.isOverSlider(screenX, screenY);
	}

	@Override
	public boolean touchDragged(final int screenX, final int screenY, final float worldScreenY, final int pointer) {
		this.input.sendMouseEvent(JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE, screenX, screenY, worldScreenY, 0);
		return this.input.touchDragged(screenX, screenY, worldScreenY, pointer);
	}

	@Override
	public boolean isOverClickableUi(final int screenX, final int screenY) {
		return this.input.isOverClickableUi(screenX, screenY);
	}

	@Override
	public PointKind pointKindAt(final int screenX, final int screenY) {
		return this.input.pointKindAt(screenX, screenY);
	}

	@Override
	public boolean hasOrderableSelection() {
		return this.input.hasOrderableSelection();
	}

	@Override
	public boolean isOverInventorySlot(final int screenX, final int screenY) {
		return this.commands.isOverInventorySlot(screenX, screenY);
	}

	@Override
	public boolean isOverAutocastButton(final int screenX, final int screenY) {
		return this.commands.isOverAutocastButton(screenX, screenY);
	}

	@Override
	public boolean hasActiveCommand() {
		return this.commands.hasActiveCommand();
	}

	@Override
	public boolean isPlacingBuilding() {
		return this.commands.isPlacingBuilding();
	}

	@Override
	public void screenToWorld(final int screenX, final int screenY, final float[] out) {
		this.input.screenToWorld(screenX, screenY, out);
	}

	@Override
	public void worldToScreen(final float[] world, final float[] out) {
		this.input.worldToScreen(world, out);
	}

	@Override
	public int touchOverlaySlotAt(final int screenX, final int screenY) {
		return this.groups.touchOverlaySlotAt(screenX, screenY);
	}

	@Override
	public void selectControlGroupSlot(final int slot) {
		this.groups.selectControlGroupSlot(slot);
	}

	@Override
	public void assignControlGroupSlot(final int slot, final boolean add) {
		this.groups.assignControlGroupSlot(slot, add);
	}

	@Override
	public void noteAlertPosition(final int playerIndex, final float x, final float y) {
		this.minimapPanel.noteAlertPosition(playerIndex, x, y);
	}

	@Override
	public void unitAttacked(final CUnit unit, final CUnit attacker) {
		this.minimapPanel.unitAttacked(unit, attacker);
		this.groups.unitAttacked(unit, attacker);
	}

	@Override
	public void heroDied(final CUnit hero) {
		this.messages.heroDied(hero);
	}

	@Override
	public void cancelPointerDrag() {
		this.input.cancelPointerDrag();
	}

	@Override
	public boolean skipCinematic() {
		return this.cinematics.skipCinematic();
	}

	@Override
	public void endTouchHover() {
		this.input.endTouchHover();
	}

	@Override
	public boolean mouseMoved(final int screenX, final int screenY, final float worldScreenY) {
		this.input.sendMouseEvent(JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE, screenX, screenY, worldScreenY, 0);
		return this.input.mouseMoved(screenX, screenY, worldScreenY);
	}

	public float getHeightRatioCorrection() {
		return this.heightRatioCorrection;
	}

	@Override
	public void queueIconClicked(final int index) {
		this.infoPanel.queueIconClicked(index);
	}

	/**
	 * Takes the red and green pathing square of a building being placed off the ground and frees its texture; a new
	 * one is made for the next building.
	 */
	void releaseBuildingPathingPreview() {
		if (this.cursorModelUnderneathPathingRedGreenSplatModel != null) {
			if (this.placementCursor != null) {
				this.placementCursor.destroy(Gdx.gl30, this.war3MapViewer.terrain.centerOffset);
				this.placementCursor = null;
			}
			this.war3MapViewer.terrain.removeSplatBatchModel(BUILDING_PATHING_PREVIEW_KEY);
			this.cursorModelUnderneathPathingRedGreenSplatModel = null;
		}
		if (this.cursorModelUnderneathPathingRedGreenPixmapTexture != null) {
			this.cursorModelUnderneathPathingRedGreenPixmapTexture.dispose();
			this.cursorModelUnderneathPathingRedGreenPixmapTexture = null;
		}
		if (this.cursorModelUnderneathPathingRedGreenPixmap != null) {
			this.cursorModelUnderneathPathingRedGreenPixmap.dispose();
			this.cursorModelUnderneathPathingRedGreenPixmap = null;
		}
	}

	@Override
	public void dispose() {
		if (this.rootFrame != null) {
			this.rootFrame.dispose();
		}
		this.shapeRenderer.dispose();
		this.meleeUIMinimap.dispose();
		if (this.cursorModelUnderneathPathingRedGreenPixmapTexture != null) {
			this.cursorModelUnderneathPathingRedGreenPixmapTexture.dispose();
		}
		if (this.cursorModelUnderneathPathingRedGreenPixmap != null) {
			this.cursorModelUnderneathPathingRedGreenPixmap.dispose();
		}
		if (this.cinematicGradientTexture != null) {
			this.cinematicGradientTexture.dispose();
		}
	}

	@Override
	public GameCameraManager getCameraManager() {
		return this.cameraManager;
	}

	@Override
	public Music playMusic(final String musicField, final boolean random, final int index) {
		return playMusicEx(musicField, random, index, 0, -1);
	}

	@Override
	public Music setMapMusic(final String musicField, final boolean random, final int index) {
		this.mapMusicField = musicField;
		this.mapMusicRandom = random;
		this.mapMusicIndex = index;
		return this.musicPlayer.setDefaultMusic(musicField, random, index);
	}

	@Override
	public void playMapMusic() {
		this.musicPlayer.playDefaultMusic();
	}

	@Override
	public Music playMusicEx(final String musicField, final boolean random, final int index, final int fromMSecs,
			final int fadeInMSecs) {
		this.playingMusicField = musicField;
		return this.musicPlayer.playMusicEx(musicField, random, index, fromMSecs, fadeInMSecs);
	}

	@Override
	public void stopMusic(final boolean fadeOut) {
		this.playingMusicField = null;
		this.musicPlayer.stopMusic();
	}

	@Override
	public void resumeMusic() {
		this.musicPlayer.resumeMusic();
	}

	@Override
	public void setMusicVolume(final int volume) {
		this.musicPlayer.setVolume(volume);
	}

	@Override
	public void setMusicPlayPosition(final int millisecs) {
		this.musicPlayer.setMusicPosition(millisecs);
	}

	@Override
	public void gameClosed() {
		this.musicPlayer.stopMusic();
		CSoundMixer.audible().stopLoops();
	}

	@Override
	public Scene getUiScene() {
		return this.uiScene;
	}

	@Override
	public void scriptSelectUnit(final CUnit unit, final boolean select) {
		this.input.scriptSelectUnit(unit, select);
	}

	@Override
	public void scriptClearSelection() {
		this.input.scriptClearSelection();
	}

	@Override
	public void flashQuestDialogButton() {
		this.menus.flashQuestDialogButton();
	}

	@Override
	public void forceQuestDialogUpdate() {
		this.menus.forceQuestDialogUpdate();
	}

	@Override
	public void pingMinimap(final float x, final float y, final float duration) {
		this.minimapPanel.pingMinimap(x, y, duration);
	}

	@Override
	public void pingMinimap(final float x, final float y, final float duration, final int red, final int green,
			final int blue, final boolean extraEffects) {
		this.minimapPanel.pingMinimap(x, y, duration, red, green, blue, extraEffects);
	}

	@Override
	public void setLeaderboard(final CLeaderboard leaderboard) {
		this.messages.setLeaderboard(leaderboard);
	}

	@Override
	public void removeTimerDialog(final CTimerDialog dialog) {
		this.messages.removeTimerDialog(dialog);
	}

	@Override
	public CTimerDialog createTimerDialog(final CTimer timer) {
		return this.messages.createTimerDialog(timer);
	}

	@Override
	public void displayTimedText(final float x, final float y, final float duration, final String message) {
		this.messages.displayTimedText(x, y, duration, message);
	}

	@Override
	public void clearTextMessages() {
		this.messages.clearTextMessages();
	}

	@Override
	public CScriptDialog createScriptDialog(final GlobalScope globalScope) {
		return this.messages.createScriptDialog(globalScope);
	}

	@Override
	public CScriptDialogButton createScriptDialogButton(final CScriptDialog scriptDialog, final String text,
			final char hotkey) {
		return this.messages.createScriptDialogButton(scriptDialog, text, hotkey);
	}

	/**
	 * The game menu pauses a game with one human player, as in the original game.
	 */
	/**
	 * A game without the network is single player, as in the original (a -loadfile or campaign game pauses for its
	 * menu): the map's other user slots do not make it a multiplayer game.
	 */
	boolean isSinglePlayerGame() {
		return !this.war3MapViewer.isNetworkGame();
	}

	/**
	 * Capture a Bug: the step, the camera and the selected units go to the game's marks file, beside its replay,
	 * for a problem report. Nothing reaches the game.
	 */
	void markThisMoment() {
		final ReplaySession session = this.war3MapViewer.getReplaySession();
		final boolean recorded = session instanceof ReplayRecording;
		final FileHandle file = recorded ? ((ReplayRecording) session).marksFile() : GameMarks.unrecorded();
		final List<GameMarks.Unit> units = new ArrayList<>();
		for (final RenderUnit renderUnit : this.selectedUnits) {
			final CUnit unit = renderUnit.getSimulationUnit();
			units.add(new GameMarks.Unit(unit.getHandleId(), unit.getTypeId().asStringValue(), unit.getPlayerIndex(),
					unit.getX(), unit.getY(), unit.getLife(), unit.getMaximumLife()));
		}
		final int tick = this.war3MapViewer.simulation.getGameTurnTick();
		final int number = this.markedMoments + 1;
		try {
			GameMarks.write(file, number == 1, GameMarks.head(this.war3MapViewer.getMapFileName(), recorded),
					GameMarks.text(number, tick, tick * WarsmashConstants.SIMULATION_STEP_TIME,
							this.cameraManager.target.x, this.cameraManager.target.y, units));
			this.markedMoments = number;
			this.messages.showGameMessage(String.format(Locale.ROOT, this.rootFrame.smashString("SMASH_BUG_CAPTURED", "Bug %d captured (%s)."),
					number, gameClock(tick * WarsmashConstants.SIMULATION_STEP_TIME)), 6);
		}
		catch (final IOException e) {
			Log.error("The moment could not be marked", e);
			this.messages.showGameMessage(this.rootFrame.smashString("SMASH_BUG_FAILED", "The moment could not be marked."), 6);
		}
	}

	private static String gameClock(final float seconds) {
		final int whole = (int) seconds;
		return String.format(Locale.ROOT, "%d:%02d:%02d", whole / 3600, (whole / 60) % 60, whole % 60);
	}

	/**
	 * A replay played on to a marked moment (a problem report): the camera goes where the player looked, the units the
	 * player had selected are selected, and the replay pauses there, as the playback's options ask.
	 */
	private void showReplayMoment() {
		final float[] look = GameReplays.lookAt();
		if (look != null) {
			this.cameraManager.target.x = look[0];
			this.cameraManager.target.y = look[1];
		}
		final int[] select = GameReplays.selectAtMoment();
		if (select != null) {
			runSimulationDriven(() -> this.input.selectUnits(unitsOf(select)));
		}
		if (GameReplays.pauseAtMoment()) {
			if (this.replayPanel == null) {
				this.war3MapViewer.setGamePaused(War3MapViewer.PauseSource.REPLAY, true);
			}
			else if (!this.replayPanel.isPaused()) {
				this.replayPanel.togglePause();
			}
		}
	}

	public void quitMission() {
		this.war3MapViewer.gameEnded();
		this.exitGameRunnable.run();
	}

	@Override
	public SavedInterfaceState captureInterfaceState() {
		final SavedInterfaceState state = new SavedInterfaceState();
		state.cameraX = this.cameraManager.target.x;
		state.cameraY = this.cameraManager.target.y;
		state.selectedUnitHandleIds = handleIdsOf(this.selectedUnits);
		state.controlGroupHandleIds = new int[this.controlGroups.size()][];
		for (int i = 0; i < this.controlGroups.size(); i++) {
			state.controlGroupHandleIds[i] = handleIdsOf(this.controlGroups.get(i));
		}
		state.userControlEnabled = this.userControlEnabled;
		state.userUIEnabled = this.userUIEnabled;
		state.interfaceShown = this.cinematics.isInterfaceShown();
		state.cinematicPanelsHidden = this.cinematics.isCinematicPanelsHidden();
		state.subtitlesForced = this.cinematics.isSubtitleDisplayOverride();
		this.cinematics.captureScene(state);
		state.cineFilter = this.cineFilter.capture();
		state.cineFilterTexture = this.cineFilter.getTexturePath();
		state.selectionOff = this.selectionOff;
		state.allyFilterButtonEnabled = this.minimapPanel.allyFilterEnabled;
		state.creepFilterButtonEnabled = this.minimapPanel.creepFilterEnabled;
		state.timerDialogs = new ArrayList<>(this.timerDialogs);
		state.scriptDialogs = new ArrayList<>(this.scriptDialogs);
		final CCameraField[] cameraFields = CCameraField.values();
		state.cameraFields = new float[cameraFields.length];
		for (int i = 0; i < cameraFields.length; i++) {
			state.cameraFields[i] = this.cameraManager.getField(cameraFields[i]);
		}
		final Rectangle bounds = this.cameraManager.getCameraBounds();
		if (bounds != null) {
			state.cameraBounds = new float[] { bounds.x, bounds.y, bounds.width, bounds.height };
		}
		state.mapMusic = this.mapMusicField;
		state.mapMusicRandom = this.mapMusicRandom;
		state.mapMusicIndex = this.mapMusicIndex;
		state.playingMusic = this.playingMusicField;
		return state;
	}

	private static int[] handleIdsOf(final List<RenderUnit> units) {
		final int[] handleIds = new int[units.size()];
		for (int i = 0; i < handleIds.length; i++) {
			handleIds[i] = units.get(i).getSimulationUnit().getHandleId();
		}
		return handleIds;
	}

	private List<RenderUnit> unitsOf(final int[] handleIds) {
		final List<RenderUnit> units = new ArrayList<>();
		if (handleIds != null) {
			for (final int handleId : handleIds) {
				final CUnit unit = this.war3MapViewer.simulation.getUnit(handleId);
				final RenderUnit renderUnit = unit == null ? null : this.war3MapViewer.getRenderPeer(unit);
				if (renderUnit != null) {
					units.add(renderUnit);
				}
			}
		}
		return units;
	}

	@Override
	public void restoreInterfaceState(final SavedInterfaceState state) {
		this.localPlayer.addStateListener(this);
		this.messages.watchSharedControl();
		this.menus.goldChanged();
		this.menus.lumberChanged();
		this.menus.foodChanged();
		this.menus.upkeepChanged();
		if (state.cameraBounds != null) {
			this.cameraManager.setCameraBounds(new Rectangle(state.cameraBounds[0], state.cameraBounds[1],
					state.cameraBounds[2], state.cameraBounds[3]));
		}
		if (state.mapMusic != null) {
			setMapMusic(state.mapMusic, state.mapMusicRandom, state.mapMusicIndex);
		}
		if (state.playingMusic != null) {
			playMusic(state.playingMusic, true, 0);
		}
		this.cinematics.forceCinematicSubtitles(state.subtitlesForced);
		this.cinematics.hideCinematicPanels(state.cinematicPanelsHidden);
		this.cinematics.showInterface(state.interfaceShown, 0f);
		this.cinematics.enableUserUI(state.userUIEnabled);
		this.cinematics.restoreScene(state);
		if (state.cineFilter != null) {
			this.cineFilter.restore(state.cineFilterTexture, state.cineFilter);
		}
		this.selectionOff = state.selectionOff;
		this.minimapPanel.enableFilterButtons(state.allyFilterButtonEnabled, state.creepFilterButtonEnabled);
		if (state.timerDialogs != null) {
			for (final CTimerDialog dialog : state.timerDialogs) {
				final UIFrame frame = this.rootFrame.createFrame("TimerDialog", this.rootFrame, 0, 0);
				dialog.attachFrames(this.rootFrame, frame,
						(StringFrame) this.rootFrame.getFrameByName("TimerDialogValue", 0),
						(StringFrame) this.rootFrame.getFrameByName("TimerDialogTitle", 0));
				this.timerDialogs.add(dialog);
			}
		}
		if (state.scriptDialogs != null) {
			final List<CScriptDialog> byShownOrder = new ArrayList<>(state.scriptDialogs);
			byShownOrder.sort(Comparator.comparingInt(CScriptDialog::getShownOrder));
			final CScriptDialog lastShown = CScriptDialog.shownLast(state.scriptDialogs);
			for (final CScriptDialog dialog : byShownOrder) {
				dialog.setShownOrder(++this.lastScriptDialogShown);
			}
			this.pausingScriptDialog = lastShown;
			for (final CScriptDialog dialog : state.scriptDialogs) {
				final String title = dialog.getTitle();
				final boolean visible = dialog == lastShown;
				final boolean titleLost = dialog.isTitleLost();
				final boolean titleApplied = dialog.isTitleApplied();
				final List<CScriptDialogButton> buttons = new ArrayList<>(dialog.getButtons());
				this.messages.clearDialog(dialog);
				if (title != null) {
					dialog.setTitle(this.rootFrame, title);
				}
				for (final CScriptDialogButton button : buttons) {
					this.messages.attachScriptDialogButton(dialog, button);
					if (button.isQuitButton()) {
						final boolean toScoreScreen = button.isQuitToScoreScreen();
						button.setQuitAction(toScoreScreen, () -> endGame(toScoreScreen));
					}
				}
				dialog.setVisible(visible);
				if (titleLost) {
					dialog.loseTitle(this.rootFrame, this.uiViewport);
				}
				dialog.setTitleApplied(titleApplied);
			}
		}
		if (state.cameraFields != null) {
			final CCameraField[] cameraFields = CCameraField.values();
			for (int i = 0; i < cameraFields.length; i++) {
				final CCameraField field = cameraFields[i];
				if (Math.abs(this.cameraManager.getField(field) - state.cameraFields[i]) < 0.0001f) {
					continue;
				}
				// The camera reports angles in radians but is set in degrees.
				final boolean angle = (field == CCameraField.ANGLE_OF_ATTACK) || (field == CCameraField.ROTATION)
						|| (field == CCameraField.FIELD_OF_VIEW) || (field == CCameraField.ROLL);
				this.cameraManager.setField(field,
						angle ? (float) Math.toDegrees(state.cameraFields[i]) : state.cameraFields[i], 0);
			}
		}
		this.cameraManager.target.x = state.cameraX;
		this.cameraManager.target.y = state.cameraY;
		if (state.controlGroupHandleIds != null) {
			for (int i = 0; (i < state.controlGroupHandleIds.length) && (i < this.controlGroups.size()); i++) {
				this.controlGroups.set(i, unitsOf(state.controlGroupHandleIds[i]));
			}
		}
		this.cinematics.enableUserControl(state.userControlEnabled);
		runSimulationDriven(() -> this.input.selectUnits(unitsOf(state.selectedUnitHandleIds)));
	}

	private String savedReplayName() {
		String map = this.war3MapViewer.getMapFileName().replace('\\', '/');
		map = map.substring(map.lastIndexOf('/') + 1);
		final int dot = map.lastIndexOf('.');
		if (dot > 0) {
			map = map.substring(0, dot);
		}
		return CSaveStorage.safeName(map + " " + new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(new Date()));
	}

	/**
	 * Set when what started the game shows its result on its own page, as a launcher does after a campaign mission:
	 * the game then ends without its score screen.
	 */
	public void setReforgedSkin(final boolean reforgedSkin) {
		this.reforgedSkin = reforgedSkin;
	}

	/**
	 * A chat line a recorded player sent, shown when the player whose view the replay shows would have got it: all
	 * players' lines with All Players, else those to everyone, to that player or to the allies of the sender.
	 */
	private void replayChat(final int senderIndex, final int recipient, final int recipientPlayerIndex,
			final String message) {
		if (this.war3MapViewer.isFastForwarding() || (recipient < 0) || (recipient >= MeleeChat.Recipient.values().length)
				|| (this.chat == null)) {
			return;
		}
		final MeleeChat.Recipient kind = MeleeChat.Recipient.values()[recipient];
		final int vision = this.war3MapViewer.getReplayVisionPlayer();
		final CPlayer sender = this.war3MapViewer.simulation.getPlayer(senderIndex);
		boolean shown = vision == War3MapViewer.REPLAY_VISION_ALL_PLAYERS;
		if (!shown) {
			switch (kind) {
			case ALL:
			case OBSERVERS:
				shown = true;
				break;
			case ALLIES:
				shown = (senderIndex == vision) || sender.hasAlliance(vision, CAllianceType.PASSIVE);
				break;
			default:
				shown = (senderIndex == vision) || (recipientPlayerIndex == vision);
				break;
			}
		}
		if (shown) {
			this.chat.showReceived(sender, kind, message);
		}
	}

	/**
	 * A line another player of a LAN game sent, shown to this player when it was meant for them: to everyone, to
	 * their allies, or to them alone.
	 */
	private void receivedChat(final int senderIndex, final int recipient, final int recipientPlayerIndex,
			final String message) {
		final War3MapViewer viewer = this.war3MapViewer;
		final int local = viewer.getLocalPlayerIndex();
		if (!viewer.isLanGame() || (senderIndex == local) || viewer.isFastForwarding()
				|| (recipient < 0) || (recipient >= MeleeChat.Recipient.values().length)) {
			return;
		}
		Log.warn("LAN: chat line from " + viewer.simulation.getPlayer(senderIndex).getName() + ", shown "
				+ (this.chat != null));
		if (this.chat == null) {
			return;
		}
		final MeleeChat.Recipient kind = MeleeChat.Recipient.values()[recipient];
		final CPlayer sender = viewer.simulation.getPlayer(senderIndex);
		final boolean shown;
		switch (kind) {
		case ALL:
			shown = true;
			break;
		case ALLIES:
			shown = sender.hasAlliance(local, CAllianceType.PASSIVE);
			break;
		case PLAYER:
			shown = recipientPlayerIndex == local;
			break;
		default:
			shown = false;
			break;
		}
		if (shown) {
			this.chat.showReceived(sender, kind, message);
		}
	}

	/** What the LAN game of this machine knows of the others, or null when this is not a LAN game. */
	LanPlay lanPlay() {
		return this.war3MapViewer.getGameTurnManager() instanceof LockstepTurnManager
				? ((LockstepTurnManager) this.war3MapViewer.getGameTurnManager()).play()
				: null;
	}

	public void setResultShownOutside(final boolean resultShownOutside) {
		this.resultShownOutside = resultShownOutside;
	}

	/**
	 * Whether a game's end leaves its result to the launcher: only a campaign mission has it, on the campaign's page;
	 * any other game, custom or loaded, shows the score screen here before it returns.
	 */
	static boolean resultShownByLauncher(final boolean launcherShowsResult, final int campaign) {
		return launcherShowsResult && (campaign >= 0);
	}

	@Override
	public void endGame(final boolean showScoreScreen) {
		if (showScoreScreen
				&& !resultShownByLauncher(this.resultShownOutside, this.war3MapViewer.getCampaign())) {
			this.war3MapViewer.gameEnded();
			this.cinematics.updateInterfaceVisibility(false);
			this.cinematicPanel.setVisible(false);
			this.war3MapViewer.setWorldHidden(true);
			final ReplaySession replay = this.war3MapViewer.getReplaySession();
			if ((replay instanceof ReplayRecording) && ((ReplayRecording) replay).isSaved()) {
				this.scoreScreen.setReplaySaver(() -> ((ReplayRecording) replay).saveAs(savedReplayName()) != null);
			}
			this.scoreScreen.show();
			this.cursorFrame.setVisible(true);
		}
		else {
			quitMission();
		}
	}

	@Override
	public void destroyDialog(final CScriptDialog dialog) {
		this.messages.destroyDialog(dialog);
	}

	@Override
	public void displayScriptDialog(final CScriptDialog dialog, final boolean flag) {
		this.messages.displayScriptDialog(dialog, flag);
	}

	@Override
	public void setScriptDialogTitle(final CScriptDialog dialog, final String text) {
		this.messages.setScriptDialogTitle(dialog, text);
	}

	/**
	 * A dialog on the screen stops a single player game, but not at once: the game goes on for a few steps first. Every
	 * dialog's part in it is in the simulation's steps, so a replay needs no stop of its own (the click that ends it is
	 * in its record at the step it came at), and a network game, where only some of the machines show a dialog, never
	 * stops.
	 */
	boolean isScriptDialogPause() {
		final CScriptDialog dialog = this.pausingScriptDialog;
		return (dialog != null) && dialog.isVisible() && !this.war3MapViewer.isLanGame()
				&& !this.war3MapViewer.isReplayPlayback()
				&& (this.war3MapViewer.simulation.getGameTurnTick() >= (dialog.getShownTick()
						+ SCRIPT_DIALOG_PAUSE_STEPS));
	}

	@Override
	public void clearDialog(final CScriptDialog dialog) {
		this.messages.clearDialog(dialog);
	}

	@Override
	public void removedUnit(final CUnit whichUnit) {
		runSimulationDriven(() -> deselectRemovedUnit(whichUnit));
	}

	private void deselectRemovedUnit(final CUnit whichUnit) {
		final RenderUnit renderUnit = this.war3MapViewer.getRenderPeer(whichUnit);
		if ((this.selectedUnits != null) && this.selectedUnits.contains(renderUnit)) {
			final List<RenderUnit> newSelectedUnits = new ArrayList<>(this.selectedUnits);
			newSelectedUnits.remove(renderUnit);
			this.input.selectUnits(newSelectedUnits);
		}
		else if (this.selectedUnit == renderUnit) {
			this.input.selectUnit(null);
		}
	}

	@Override
	public void removedItem(final CItem whichItem) {
		// TODO unselect an item if it exists
	}

	@Override
	public void onHide() {
		this.timeIndicator.setVisible(false);
		this.cursorFrame.setVisible(false);
		this.showing = false;
	}

	@Override
	public void onShow() {
		this.timeIndicator.setVisible(true);
		this.cursorFrame.setVisible(true);
		this.showing = true;
	}

	@Override
	public void showInterface(final boolean show, final float fadeDuration) {
		this.cinematics.showInterface(show, fadeDuration);
	}

	@Override
	public void enableUserUI(final boolean enable) {
		this.cinematics.enableUserUI(enable);
	}

	@Override
	public CineFilter getCineFilter() {
		return this.cinematics.getCineFilter();
	}

	@Override
	public void setCinematicScene(final int portraitUnitId, final CPlayerColor color, final String speakerTitle,
			final String text, final float sceneDuration, final float voiceoverDuration) {
		this.cinematics.setCinematicScene(portraitUnitId, color, speakerTitle, text, sceneDuration, voiceoverDuration);
	}

	@Override
	public void dialogueSoundStarted(final String soundPath) {
		lipSync().soundStarted(soundPath);
	}

	/** Lists the faces' animations of spoken lines while the map loads, which the first line did before. */
	public void prepareLipSync() {
		lipSync().prepare();
	}

	PortraitLipSync lipSync() {
		if (this.lipSync == null) {
			this.lipSync = new PortraitLipSync(this.war3MapViewer.faceFxLibrary());
		}
		return this.lipSync;
	}

	@Override
	public void endCinematicScene() {
		this.cinematics.endCinematicScene();
	}

	@Override
	public void forceCinematicSubtitles(final boolean value) {
		this.cinematics.forceCinematicSubtitles(value);
	}

	@Override
	public void hideCinematicPanels(final boolean hide) {
		this.cinematics.hideCinematicPanels(hide);
	}

	public boolean getCreepCampFilterState() {
		return GameOptions.get().isMinimapCreeps();
	}

	public void setCreepCampFilterState(final boolean shown) {
		this.minimapPanel.setCreepCampFilterState(shown);
	}

	public void enableMinimapFilterButtons(final boolean ally, final boolean creep) {
		this.minimapPanel.enableFilterButtons(ally, creep);
	}

	public void displayLoadDialog() {
		this.menus.displayLoadDialog();
	}

	public void enableSelect(final boolean state, final boolean ui) {
		setSelectionBits(SELECT_OFF, !state, SELECT_UI_OFF, !ui);
	}

	public void enableDragSelect(final boolean state, final boolean ui) {
		setSelectionBits(DRAG_SELECT_OFF, !state, DRAG_SELECT_UI_OFF, !ui);
	}

	public void enablePreSelect(final boolean state, final boolean ui) {
		setSelectionBits(PRE_SELECT_OFF, !state, PRE_SELECT_UI_OFF, !ui);
		if (!isSelectionOn(PRE_SELECT_UI_OFF)) {
			this.war3MapViewer.clearUnitMouseOverHighlight();
		}
	}

	private void setSelectionBits(final int stateBit, final boolean stateOff, final int uiBit, final boolean uiOff) {
		this.selectionOff = (this.selectionOff & ~(stateBit | uiBit)) | (stateOff ? stateBit : 0)
				| (uiOff ? uiBit : 0);
	}

	boolean isSelectionOn(final int offBit) {
		// BlzEnableSelections(false, ...) stops the player's click and box selection.
		if (((offBit & (SELECT_OFF | DRAG_SELECT_OFF)) != 0) && !this.war3MapViewer.scriptUiState.isSelectionEnabled()) {
			return false;
		}
		return (this.selectionOff & offBit) == 0;
	}

	@Override
	public void enableUserControl(final boolean value) {
		this.cinematics.enableUserControl(value);
	}
}
