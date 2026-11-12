package com.etheller.warsmash.viewer5.handlers.w3x.ui.replay;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.MenuItem;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.MenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.PopupMenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleCheckBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleStatusBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextureFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.GameCameraManager;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.ReplayPlayback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.CommandCardIcon;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.MeleeChat;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.CommandCardCommandListener;

/**
 * The replay panel of a replay's game, the game's own SimpleReplayPanelV1 (SimpleReplayPanelV2 with the Reforged skin)
 * in the command card's place, as measured on 1.29: Replay Vision (a player, or all players), the elapsed time out of
 * the whole at the speed, the time bar, Fog of War and Auto Camera, and Pause, Speed Up, Speed Down and Restart in the
 * card's bottom row. A click on the time bar seeks. On a phone the panel is two rows under the top of the screen,
 * which a button folds away. Nothing here reaches the replay's game: the pause is the viewer's own, the speed only
 * plays its steps faster.
 */
public final class ReplayPanel implements CommandCardCommandListener {
	public interface Host {
		/** Plays the same replay again from its start, on to the step before it is shown (0: from the start). */
		void restart(int startTick);

		/** The player whose resources the resource bar shows changed. */
		void viewingPlayerChanged();
	}

	private static final String PANEL_V1 = "SimpleReplayPanelV1";
	private static final String PANEL_V2 = "SimpleReplayPanelV2";
	private static final String COLLAPSE_ART = "observer-replay-btn-collapse-resting_4k.dds";
	private static final String EXPAND_ART = "observer-replay-btn-expand-resting_4k.dds";
	private static final String FALLBACK_COLLAPSE_KEY = "EscMenuPopupMenuArrow";
	private static final int ACTION_PAUSE = 1;
	private static final int ACTION_SPEED_UP = 2;
	private static final int ACTION_SPEED_DOWN = 3;
	private static final int ACTION_RESTART = 4;
	private static final int ACTION_COLLAPSE = 5;
	/** 1/2X is the slowest (measured); faster goes by whole steps. */
	private static final float[] SPEEDS = { 0.5f, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
	private static final int NORMAL_SPEED = 1;
	/** A finger's size on the panel's small controls, as the touch design asks (96 px of a 1848 px tall screen). */
	private static final float TOUCH_HEIGHT = 0.031f;
	private static final float AUTO_CAMERA_INTERVAL = 3f;
	private static final float AUTO_CAMERA_PAN_TIME = 0.75f;
	private static final float AUTO_CAMERA_MIN_DISTANCE = 512f;
	/** The panel's phone layout: used when the screen is not taller than this many inches. */
	private static final float PHONE_MAX_HEIGHT_INCHES = 3.9f;
	/** -Dwarsmash.replay.phone=true|false picks the layout by hand, also without a touch screen. */
	private static final String PHONE_PROPERTY = "warsmash.replay.phone";
	private static final float PHONE_ROW = 0.034f;
	private static final float PHONE_PAD = 0.004f;
	private static final float PHONE_WIDTH = 0.5f;
	private static final float PHONE_TOP = 0.034f;
	/** A step is never sought to the very end, which would end the replay under the finger. */
	private static final int SEEK_END_MARGIN = 20;

	private final GameUI rootFrame;
	private final Viewport uiViewport;
	private final War3MapViewer viewer;
	private final ReplayPlayback playback;
	private final GameCameraManager camera;
	private final Host host;
	private final boolean phone;
	private final SimpleFrame holder;
	private final SimpleFrame content;
	private final StringFrame elapsedTime;
	private final StringFrame speedMultiplier;
	private final UIFrame speedLabel;
	private final SimpleStatusBarFrame statusBar;
	private final PopupMenuFrame visionMenu;
	private final StringFrame visionTitle;
	private final SimpleCheckBoxFrame fogCheckBox;
	private final SimpleCheckBoxFrame cameraCheckBox;
	private final CommandCardIcon[] buttons = new CommandCardIcon[4];
	private final Timeline timeline;
	/** The menu's entries: a player index or {@link War3MapViewer#REPLAY_VISION_ALL_PLAYERS}. */
	private final List<Integer> visionChoices = new ArrayList<>();
	private final List<String> visionNames = new ArrayList<>();
	private final String totalText;
	private CommandCardIcon collapseButton;
	private Texture collapseTexture;
	private Texture expandTexture;
	private boolean collapsed;
	private float laidOutWidth = -1;
	private int speedIndex = NORMAL_SPEED;
	private boolean paused;
	private boolean seeking;
	private int shownSecond = -1;
	private float autoCameraTime;
	private final Map<CUnit, Float> lastLife = new IdentityHashMap<>();

	/**
	 * @param commandCard   the command card's buttons: the panel covers the card, its buttons take the bottom row's
	 *                      places
	 * @param reforgedSkin  whether the game is played with the Reforged skin: its panel is used when the game data has
	 *                      its art, else the Classic one
	 * @return the panel, or null when the game data has no replay panel
	 */
	public static ReplayPanel create(final GameUI rootFrame, final Viewport uiViewport, final War3MapViewer viewer,
			final ReplayPlayback playback, final GameCameraManager camera, final CommandCardIcon[][] commandCard,
			final Host host, final boolean reforgedSkin) {
		String panel = null;
		if (reforgedSkin && hasReforgedArt(rootFrame, viewer)) {
			panel = PANEL_V2;
		}
		else if (rootFrame.getTemplates().getFrame(PANEL_V1) != null) {
			panel = PANEL_V1;
		}
		else if (rootFrame.getTemplates().getFrame(PANEL_V2) != null) {
			panel = PANEL_V2;
		}
		if (panel == null) {
			Log.warn("Replay: the game data has no replay panel, the replay is shown without it");
			return null;
		}
		return new ReplayPanel(rootFrame, uiViewport, viewer, playback, camera, commandCard, host, panel);
	}

	private static boolean hasReforgedArt(final GameUI rootFrame, final War3MapViewer viewer) {
		if (rootFrame.getTemplates().getFrame(PANEL_V2) == null) {
			return false;
		}
		final String[] textureKeys = { "ReplayPanelBackdropV2", "ReplayTimelineFill", "ReplayTimelineBorder",
			"ReplayCheckBoxNormal", "ReplayCheckBoxCheck" };
		for (final String key : textureKeys) {
			if (!rootFrame.hasSkinField(key)) {
				return false;
			}
			try {
				if (ImageUtils.getAnyExtensionTexture(viewer.dataSource, rootFrame.getSkinField(key)) == null) {
					return false;
				}
			}
			catch (final RuntimeException e) {
				return false;
			}
		}
		return true;
	}

	private static boolean phoneScreen() {
		final String forced = System.getProperty(PHONE_PROPERTY);
		if (forced != null) {
			return Boolean.parseBoolean(forced);
		}
		if (!TouchInputRouter.isActive()) {
			return false;
		}
		final float ppi = Gdx.graphics.getPpiY();
		return (ppi > 0) && ((Gdx.graphics.getHeight() / ppi) < PHONE_MAX_HEIGHT_INCHES);
	}

	private ReplayPanel(final GameUI rootFrame, final Viewport uiViewport, final War3MapViewer viewer,
			final ReplayPlayback playback, final GameCameraManager camera, final CommandCardIcon[][] commandCard,
			final Host host, final String panel) {
		this.rootFrame = rootFrame;
		this.uiViewport = uiViewport;
		this.viewer = viewer;
		this.playback = playback;
		this.camera = camera;
		this.host = host;
		this.phone = phoneScreen();
		final CommandCardIcon topLeft = commandCard[0][0];
		final CommandCardIcon bottomRight = commandCard[commandCard.length - 1][commandCard[0].length - 1];
		this.holder = (SimpleFrame) rootFrame.createFrameByType("SIMPLEFRAME", "SmashReplayPanel", rootFrame, "", 0);
		this.content = (SimpleFrame) rootFrame.createFrameByType("SIMPLEFRAME", "SmashReplayPanelContent",
				this.holder, "", 0);
		this.holder.add(this.content);
		if (!this.phone) {
			this.holder.addSetPoint(new SetPoint(FramePoint.TOPLEFT, topLeft, FramePoint.TOPLEFT, 0, 0));
			this.holder.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT, bottomRight, FramePoint.BOTTOMRIGHT, 0, 0));
		}
		this.content.setSetAllPoints(true);
		rootFrame.createSimpleFrame(panel, this.content, 0);

		this.elapsedTime = (StringFrame) rootFrame.getFrameByName("ReplayElapsedTime", 0);
		this.speedMultiplier = (StringFrame) rootFrame.getFrameByName("ReplaySpeedMultiplier", 0);
		this.speedLabel = rootFrame.getFrameByName("ReplaySpeedLabel", 0);
		this.statusBar = (SimpleStatusBarFrame) rootFrame.getFrameByName("ReplayStatusBar", 0);
		this.visionMenu = (PopupMenuFrame) rootFrame.getFrameByName("ReplayVisionMenu", 0);
		this.visionTitle = (StringFrame) ((GlueTextButtonFrame) this.visionMenu.getPopupTitleFrame()).getButtonText();
		this.fogCheckBox = (SimpleCheckBoxFrame) rootFrame.getFrameByName("ReplayFogCheckBox", 0);
		this.cameraCheckBox = (SimpleCheckBoxFrame) rootFrame.getFrameByName("ReplayCameraCheckBox", 0);
		final boolean touch = TouchInputRouter.isActive();
		final float touchHeight = touch ? GameUI.convertY(uiViewport, TOUCH_HEIGHT) : 0;
		this.fogCheckBox.setTouchArea(rootFrame.getFrameByName("ReplayFogString", 0), touchHeight);
		this.cameraCheckBox.setTouchArea(rootFrame.getFrameByName("ReplayCameraString", 0), touchHeight);
		this.fogCheckBox.setChecked(true);
		this.fogCheckBox.setOnClick(this::visionChanged);
		this.cameraCheckBox.setOnClick(() -> this.autoCameraTime = AUTO_CAMERA_INTERVAL);

		final int lastTick = Math.max(1, playback.getLastTick());
		this.totalText = clock((int) (lastTick * WarsmashConstants.SIMULATION_STEP_TIME));
		// The elapsed time is followed by the whole, which leaves no room for the label before it.
		hide("ReplayElapsedTimeLabel");
		final AbstractRenderableFrame speedAt = (AbstractRenderableFrame) rootFrame.getFrameByName("ReplaySpeedAtLabel",
				0);
		if (!this.phone && (speedAt != null)) {
			speedAt.clearFramePointAssignments();
			speedAt.addSetPoint(new SetPoint(FramePoint.LEFT, this.elapsedTime, FramePoint.RIGHT,
					GameUI.convertX(uiViewport, 0.004f), 0));
			if (PANEL_V1.equals(panel)) {
				// The Classic panel is too narrow for "at" as well.
				speedAt.setVisible(false);
				final AbstractRenderableFrame multiplier = (AbstractRenderableFrame) this.speedMultiplier;
				multiplier.clearFramePointAssignments();
				multiplier.addSetPoint(new SetPoint(FramePoint.LEFT, this.elapsedTime, FramePoint.RIGHT,
						GameUI.convertX(uiViewport, 0.006f), 0));
			}
		}

		fillVisionMenu(touch);
		this.visionMenu.setMenuClickListener((button, index) -> chooseVision(index));

		final String[][] buttonArt = { { "ReplayPauseIcon", "REPLAY_TOOLTIP_PAUSE" },
			{ "ReplaySpeedUpIcon", "REPLAY_TOOLTIP_SPEED_UP" }, { "ReplaySpeedDownIcon", "REPLAY_TOOLTIP_SPEED_DOWN" },
			{ "ReplayRestartIcon", "REPLAY_TOOLTIP_RESTART" } };
		final int bottomRow = commandCard.length - 1;
		for (int i = 0; i < this.buttons.length; i++) {
			this.buttons[i] = button(i, commandCard[bottomRow][i], skinTexture(buttonArt[i][0]),
					tip(buttonArt[i][1], touch));
		}
		this.timeline = new Timeline(this.holder);
		this.holder.add(this.timeline);
		this.timeline.addSetPoint(new SetPoint(FramePoint.LEFT, this.statusBar, FramePoint.LEFT, 0, 0));
		this.timeline.addSetPoint(new SetPoint(FramePoint.RIGHT, this.statusBar, FramePoint.RIGHT, 0, 0));
		this.timeline.setHeight(Math.max(GameUI.convertY(uiViewport, 0.012f), touchHeight));
		if (this.phone) {
			layoutPhone(touch);
		}
		this.viewer.setReplayVision(this.visionChoices.get(defaultVisionIndex()), true);
		this.rootFrame.setText(this.visionTitle, this.visionNames.get(defaultVisionIndex()));
		lookAtStartOfRecordedPlayer();
		showSpeed();
		update(0);
	}

	private void hide(final String frameName) {
		final UIFrame frame = this.rootFrame.getFrameByName(frameName, 0);
		if (frame != null) {
			frame.setVisible(false);
		}
	}

	private Texture skinTexture(final String iconKey) {
		Texture texture = null;
		if (this.rootFrame.hasSkinField(iconKey)) {
			texture = loadTexture(this.rootFrame.getSkinField(iconKey));
		}
		if (texture == null) {
			texture = ImageUtils.getAnyExtensionTexture(this.viewer.dataSource, ImageUtils.DEFAULT_ICON_PATH);
		}
		return texture;
	}

	private CommandCardIcon button(final int index, final CommandCardIcon place, final Texture texture,
			final String tip) {
		final String name = "SmashReplayButton_" + index;
		final CommandCardIcon button = new CommandCardIcon(name, this.holder, this);
		this.holder.add(button);
		final float width = place != null ? place.getAssignedWidth()
				: GameUI.convertY(this.uiViewport, PHONE_ROW);
		final float height = place != null ? place.getAssignedHeight()
				: GameUI.convertY(this.uiViewport, PHONE_ROW);
		if (place != null) {
			button.addSetPoint(new SetPoint(FramePoint.CENTER, place, FramePoint.CENTER, 0, 0));
		}
		button.setWidth(width);
		button.setHeight(height);
		final TextureFrame icon = new TextureFrame(name + "_Icon", this.rootFrame, false, null);
		icon.addSetPoint(new SetPoint(FramePoint.CENTER, button, FramePoint.CENTER, 0, 0));
		icon.setWidth(width);
		icon.setHeight(height);
		final SpriteFrame cooldown = (SpriteFrame) this.rootFrame.createFrameByType("SPRITE", name + "_Cooldown",
				this.rootFrame, "", 0);
		button.set(icon, null, cooldown, null, null, null);
		button.clear();
		button.setCommandButtonData(texture, 0, index + 1, 0, false, false, false, tip, "", '\0', 0, 0, 0, 0, false,
				0, 0, -1);
		return button;
	}

	/** A touch screen has no keys, so the key in the tip is left out there. */
	private String tip(final String key, final boolean touch) {
		final String tip = this.rootFrame.getTemplates().getDecoratedString(key);
		return touch ? tip.replaceAll("\\s*\\((?i:\\|c[0-9a-f]{8})[^|]*\\|r\\)", "") : tip;
	}

	/**
	 * The phone's layout: two rows under the top of the screen, Vision and the two switches over the four buttons and
	 * the time bar, in a frame the first button folds away.
	 */
	private void layoutPhone(final boolean touch) {
		final float row = GameUI.convertY(this.uiViewport, PHONE_ROW);
		final float pad = GameUI.convertX(this.uiViewport, PHONE_PAD);
		hide("SimpleReplayPanelBackdrop");
		hide("ReplayVisionLabel");
		hide("ReplaySpeedAtLabel");
		hide("ReplaySpeedLabel");
		final UIFrame backdrop = this.rootFrame.createFrame("EscMenuBackdrop", this.content, 0, 0);
		if (backdrop != null) {
			this.content.add(0, backdrop);
			backdrop.setSetAllPoints(true);
			backdrop.setWidth(GameUI.convertX(this.uiViewport, PHONE_WIDTH));
			backdrop.setHeight(GameUI.convertY(this.uiViewport, (3 * PHONE_PAD) + (2 * PHONE_ROW)));
		}
		final float rowOneY = -GameUI.convertY(this.uiViewport, PHONE_PAD + (PHONE_ROW / 2));
		final float rowTwoY = -GameUI.convertY(this.uiViewport, (2 * PHONE_PAD) + (1.5f * PHONE_ROW));
		float x = GameUI.convertX(this.uiViewport, PHONE_PAD);

		this.collapseTexture = reforgedArt(COLLAPSE_ART);
		this.expandTexture = reforgedArt(EXPAND_ART);
		final Texture fallback = skinTexture(FALLBACK_COLLAPSE_KEY);
		this.collapseButton = button(4, null, this.collapseTexture != null ? this.collapseTexture : fallback, "");
		place(this.collapseButton, x, rowOneY);
		x += row + pad;

		final float menuWidth = GameUI.convertX(this.uiViewport, 0.19f);
		this.visionMenu.clearFramePointAssignments();
		this.visionMenu.setWidth(menuWidth);
		this.visionMenu.setHeight(GameUI.convertY(this.uiViewport, 0.028f));
		place(this.visionMenu, x, rowOneY);
		x += menuWidth + pad;

		final float box = GameUI.convertY(this.uiViewport, 0.022f);
		x = phoneSwitch(this.fogCheckBox, "ReplayFogString", x, rowOneY, box, pad);
		phoneSwitch(this.cameraCheckBox, "ReplayCameraString", x, rowOneY, box, pad);

		x = GameUI.convertX(this.uiViewport, PHONE_PAD);
		for (final CommandCardIcon button : this.buttons) {
			button.clearFramePointAssignments();
			place(button, x, rowTwoY);
			x += row + pad;
		}
		final float width = GameUI.convertX(this.uiViewport, PHONE_WIDTH);
		this.statusBar.clearFramePointAssignments();
		this.statusBar.setWidth(width - x - pad);
		this.statusBar.setHeight(GameUI.convertY(this.uiViewport, 0.02f));
		place(this.statusBar, x, rowTwoY);
		final AbstractRenderableFrame elapsed = (AbstractRenderableFrame) this.elapsedTime;
		elapsed.clearFramePointAssignments();
		elapsed.addSetPoint(
				new SetPoint(FramePoint.LEFT, this.statusBar, FramePoint.LEFT, GameUI.convertX(this.uiViewport, 0.005f), 0));
		final AbstractRenderableFrame speed = (AbstractRenderableFrame) this.speedMultiplier;
		speed.clearFramePointAssignments();
		speed.addSetPoint(new SetPoint(FramePoint.RIGHT, this.statusBar, FramePoint.RIGHT,
				-GameUI.convertX(this.uiViewport, 0.005f), 0));
		this.speedLabel.setVisible(false);

		this.holder.setWidth(width);
		this.holder.setHeight(GameUI.convertY(this.uiViewport, (3 * PHONE_PAD) + (2 * PHONE_ROW)));
		anchorPhone();
	}

	private float phoneSwitch(final SimpleCheckBoxFrame checkBox, final String labelName, final float x,
			final float y, final float box, final float pad) {
		checkBox.clearFramePointAssignments();
		checkBox.setWidth(box);
		checkBox.setHeight(box);
		place(checkBox, x, y);
		final AbstractRenderableFrame label = (AbstractRenderableFrame) this.rootFrame.getFrameByName(labelName, 0);
		label.clearFramePointAssignments();
		label.addSetPoint(new SetPoint(FramePoint.LEFT, checkBox, FramePoint.RIGHT, pad, 0));
		return x + box + pad + GameUI.convertX(this.uiViewport, 0.07f);
	}

	/** Puts a frame's left edge x pixels right of the panel's, its middle y pixels (negative: below) its top. */
	private void place(final UIFrame frame, final float x, final float y) {
		frame.addSetPoint(new SetPoint(FramePoint.LEFT, this.holder, FramePoint.TOPLEFT, x, y));
	}

	/** A Reforged interface picture, or null when the game data does not have it. */
	private Texture reforgedArt(final String file) {
		final String[] folders = { "UI\\Console\\Reforged\\ControlPanel\\", "UI\\Widgets\\Console\\Reforged\\" };
		for (final String folder : folders) {
			final Texture texture = loadTexture(folder + file);
			if (texture != null) {
				return texture;
			}
		}
		return null;
	}

	private Texture loadTexture(final String path) {
		try {
			return ImageUtils.getAnyExtensionTexture(this.viewer.dataSource, path);
		}
		catch (final RuntimeException e) {
			return null;
		}
	}

	/** The width the interface is laid out in: frames are placed from the left of that, not of the whole screen. */
	private float uiWidth() {
		return this.uiViewport instanceof ExtendViewport ? ((ExtendViewport) this.uiViewport).getMinWorldWidth()
				: this.uiViewport.getWorldWidth();
	}

	/** The phone's panel hangs from the top of the screen, centered; the screen's shape can change. */
	private void anchorPhone() {
		final float width = this.collapsed ? GameUI.convertY(this.uiViewport, PHONE_ROW)
				: GameUI.convertX(this.uiViewport, PHONE_WIDTH);
		final float full = GameUI.convertX(this.uiViewport, PHONE_WIDTH);
		this.laidOutWidth = uiWidth();
		this.holder.addAnchor(new AnchorDefinition(FramePoint.TOPLEFT, (this.laidOutWidth - full) / 2,
				-GameUI.convertY(this.uiViewport, PHONE_TOP)));
		this.holder.setWidth(width);
		this.holder.setHeight(this.collapsed ? GameUI.convertY(this.uiViewport, PHONE_ROW + (2 * PHONE_PAD))
				: GameUI.convertY(this.uiViewport, (3 * PHONE_PAD) + (2 * PHONE_ROW)));
		this.holder.positionBounds(this.rootFrame, this.uiViewport);
	}

	private void setCollapsed(final boolean collapsed) {
		this.collapsed = collapsed;
		this.content.setVisible(!collapsed);
		for (final CommandCardIcon button : this.buttons) {
			button.setVisible(!collapsed);
		}
		this.timeline.setVisible(!collapsed);
		if (collapsed) {
			this.visionMenu.getPopupMenuFrame().setVisible(false);
		}
		if (this.collapseButton != null) {
			final Texture art = collapsed ? this.expandTexture : this.collapseTexture;
			if (art != null) {
				this.collapseButton.setCommandButtonData(art, 0, 5, 0, false, false, false, "", "", '\0', 0, 0, 0, 0,
						false, 0, 0, -1);
			}
		}
		anchorPhone();
	}

	private void fillVisionMenu(final boolean touch) {
		final CSimulation game = this.viewer.simulation;
		int people = 0;
		for (int i = 0; i < this.viewer.getGameData().getMaxPlayers(); i++) {
			final CPlayer player = game.getPlayer(i);
			if (this.viewer.isReplayVisionPlayer(player) && (player.getController() == CMapControl.USER)) {
				people++;
			}
		}
		// A replay of one person against the computer has no All Players (measured).
		if (people > 1) {
			this.visionChoices.add(War3MapViewer.REPLAY_VISION_ALL_PLAYERS);
			this.visionNames.add(this.rootFrame.getTemplates().getDecoratedString("ALL_PLAYERS"));
		}
		for (int i = 0; i < this.viewer.getGameData().getMaxPlayers(); i++) {
			final CPlayer player = game.getPlayer(i);
			if (!this.viewer.isReplayVisionPlayer(player)) {
				continue;
			}
			this.visionChoices.add(i);
			this.visionNames.add("|cff" + MeleeChat.playerTextHex(player.getColor()) + playerName(player) + "|r");
		}
		final MenuFrame menu = (MenuFrame) this.visionMenu.getPopupMenuFrame();
		if (touch) {
			menu.setItemHeight(GameUI.convertY(this.uiViewport, TOUCH_HEIGHT));
		}
		final List<MenuItem> items = new ArrayList<>();
		for (final String name : this.visionNames) {
			items.add(new MenuItem(name, -2));
		}
		menu.setItems(this.uiViewport, items);
		menu.clearFramePointAssignments();
		if (this.phone) {
			// The phone's panel is at the top of the screen: the menu opens downwards.
			menu.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.visionMenu, FramePoint.BOTTOMLEFT, 0, 0));
			menu.addSetPoint(new SetPoint(FramePoint.TOPRIGHT, this.visionMenu, FramePoint.BOTTOMRIGHT, 0, 0));
		}
		else {
			// The panel is at the bottom of the screen: the menu opens upwards (measured).
			menu.addSetPoint(new SetPoint(FramePoint.BOTTOMLEFT, this.visionMenu, FramePoint.TOPLEFT, 0, 0));
			menu.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT, this.visionMenu, FramePoint.TOPRIGHT, 0, 0));
		}
	}

	/** The computer's slot has no name in a replay; the original calls it by its number (measured). */
	private String playerName(final CPlayer player) {
		if (player.getController() == CMapControl.COMPUTER) {
			return this.rootFrame.getTemplates().getDecoratedString("PLAYER") + " " + (player.getId() + 1);
		}
		return player.getName();
	}

	/**
	 * The camera starts at the recorded local player's start location, as it did in the recorded game. A game that
	 * only computers played was watched from a slot nobody played, whose start location is nowhere in the game: the
	 * first player who played is shown instead.
	 */
	private void lookAtStartOfRecordedPlayer() {
		int player = this.viewer.getLocalPlayerIndex();
		if (!this.visionChoices.contains(player)) {
			player = -1;
			for (final int choice : this.visionChoices) {
				if (choice >= 0) {
					player = choice;
					break;
				}
			}
		}
		if (player < 0) {
			return;
		}
		final float[] start = this.viewer.simulation.getPlayer(player).getStartLocation();
		this.camera.target.x = start[0];
		this.camera.target.y = start[1];
	}

	private int defaultVisionIndex() {
		final int recording = this.viewer.getLocalPlayerIndex();
		final int index = this.visionChoices.indexOf(recording);
		return index >= 0 ? index : 0;
	}

	private void chooseVision(final int index) {
		if ((index < 0) || (index >= this.visionChoices.size())) {
			return;
		}
		this.rootFrame.setText(this.visionTitle, this.visionNames.get(index));
		this.viewer.setReplayVision(this.visionChoices.get(index), this.fogCheckBox.isChecked());
		this.host.viewingPlayerChanged();
		this.lastLife.clear();
	}

	private void visionChanged() {
		this.viewer.setReplayVision(this.viewer.getReplayVisionPlayer(), this.fogCheckBox.isChecked());
	}

	@Override
	public void onClick(final int abilityHandleId, final int orderId, final boolean rightClick) {
		if (rightClick) {
			return;
		}
		switch (orderId) {
		case ACTION_PAUSE:
			togglePause();
			break;
		case ACTION_SPEED_UP:
			changeSpeed(1);
			break;
		case ACTION_SPEED_DOWN:
			changeSpeed(-1);
			break;
		case ACTION_RESTART:
			this.host.restart(0);
			break;
		case ACTION_COLLAPSE:
			setCollapsed(!this.collapsed);
			break;
		default:
			break;
		}
	}

	@Override
	public void openMenu(final int orderId) {
	}

	public void togglePause() {
		this.paused = !this.paused;
		this.viewer.setGamePaused(War3MapViewer.PauseSource.REPLAY, this.paused);
	}

	public boolean isPaused() {
		return this.paused;
	}

	public void changeSpeed(final int steps) {
		this.speedIndex = Math.max(0, Math.min(SPEEDS.length - 1, this.speedIndex + steps));
		this.viewer.setReplaySpeed(SPEEDS[this.speedIndex]);
		showSpeed();
	}

	/**
	 * Plays on to a step (forward: the steps go by undrawn; back: the replay starts again from its beginning and goes
	 * on to the step undrawn), as the time bar's click asks.
	 */
	public void seekTo(final int requestedTick) {
		final int tick = Math.max(0, Math.min(requestedTick, this.playback.getLastTick() - SEEK_END_MARGIN));
		final int now = this.viewer.simulation.getGameTurnTick();
		if (this.seeking || this.viewer.isFastForwarding() || (tick == now)) {
			return;
		}
		if (tick < now) {
			this.host.restart(tick);
			return;
		}
		this.seeking = true;
		final boolean wasPaused = this.paused;
		if (wasPaused) {
			this.viewer.setGamePaused(War3MapViewer.PauseSource.REPLAY, false);
		}
		this.lastLife.clear();
		this.playback.skipTo(tick, () -> {
			this.seeking = false;
			if (wasPaused) {
				this.viewer.setGamePaused(War3MapViewer.PauseSource.REPLAY, true);
			}
		});
	}

	private void showSpeed() {
		final float speed = SPEEDS[this.speedIndex];
		this.rootFrame.setText(this.speedMultiplier,
				speed < 1 ? "1/" + Math.round(1 / speed) + "X" : Math.round(speed) + "X");
		// "Speed" follows the number, which changes its width.
		this.speedMultiplier.positionBounds(this.rootFrame, this.uiViewport);
		this.speedLabel.positionBounds(this.rootFrame, this.uiViewport);
	}

	private static String clock(final int second) {
		return String.format(Locale.ROOT, "%02d:%02d:%02d", second / 3600, (second / 60) % 60, second % 60);
	}

	/** The replay's keys, as the tooltips name them: P pauses, + and - change the speed. */
	public boolean keyDown(final int keycode) {
		switch (keycode) {
		case Input.Keys.P:
		case Input.Keys.PAUSE:
			togglePause();
			return true;
		case Input.Keys.PLUS:
		case Input.Keys.EQUALS:
		case Input.Keys.NUMPAD_ADD:
			changeSpeed(1);
			return true;
		case Input.Keys.MINUS:
		case Input.Keys.NUMPAD_SUBTRACT:
			changeSpeed(-1);
			return true;
		default:
			return false;
		}
	}

	public UIFrame getFrame() {
		return this.holder;
	}

	public void setVisible(final boolean visible) {
		this.holder.setVisible(visible);
		if (!visible) {
			this.visionMenu.getPopupMenuFrame().setVisible(false);
		}
	}

	public void update(final float deltaTime) {
		if (this.phone && (this.laidOutWidth != uiWidth())) {
			anchorPhone();
		}
		final int tick = this.viewer.simulation.getGameTurnTick();
		final int second = (int) (tick * WarsmashConstants.SIMULATION_STEP_TIME);
		if (second != this.shownSecond) {
			this.shownSecond = second;
			this.rootFrame.setText(this.elapsedTime, clock(second) + " / " + this.totalText);
		}
		final int lastTick = Math.max(1, this.playback.getLastTick());
		this.statusBar.setValue(Math.min(1f, tick / (float) lastTick));
		if (this.cameraCheckBox.isChecked()) {
			followAction(deltaTime);
		}
	}

	/**
	 * Auto Camera: every few seconds the camera pans to where the shown player's units (or anyone's, with all players
	 * shown) lost the most life since the last look, if that is away from where it looks.
	 */
	private void followAction(final float deltaTime) {
		this.autoCameraTime += deltaTime;
		if ((this.autoCameraTime < AUTO_CAMERA_INTERVAL) || this.paused || this.seeking) {
			return;
		}
		this.autoCameraTime = 0;
		final int vision = this.viewer.getReplayVisionPlayer();
		float sumX = 0;
		float sumY = 0;
		float lost = 0;
		for (final CUnit unit : this.viewer.simulation.getUnits()) {
			final float life = unit.isDead() ? 0 : unit.getLife();
			final Float before = this.lastLife.put(unit, life);
			if ((before == null) || (life >= before) || unit.isHidden()) {
				continue;
			}
			final boolean watched = (vision == War3MapViewer.REPLAY_VISION_ALL_PLAYERS)
					? this.viewer.isReplayVisionPlayer(this.viewer.simulation.getPlayer(unit.getPlayerIndex()))
					: (unit.getPlayerIndex() == vision);
			if (!watched || !this.viewer.isShownToViewer(unit)) {
				continue;
			}
			final float damage = before - life;
			sumX += unit.getX() * damage;
			sumY += unit.getY() * damage;
			lost += damage;
		}
		this.lastLife.keySet().removeIf(CUnit::isDead);
		if (lost <= 0) {
			return;
		}
		final float x = sumX / lost;
		final float y = sumY / lost;
		final float dx = x - this.camera.target.x;
		final float dy = y - this.camera.target.y;
		if (((dx * dx) + (dy * dy)) >= (AUTO_CAMERA_MIN_DISTANCE * AUTO_CAMERA_MIN_DISTANCE)) {
			this.camera.panToTimed(x, y, AUTO_CAMERA_PAN_TIME);
		}
	}

	/**
	 * The time bar's touch area, over the bar and as tall as a finger asks: a click on it seeks to the step at that
	 * place, a drag moves where it will go.
	 */
	private final class Timeline extends AbstractRenderableFrame implements ClickableFrame {
		private float pointerX;

		private Timeline(final UIFrame parent) {
			super("SmashReplayTimeline", parent);
		}

		@Override
		protected void innerPositionBounds(final GameUI gameUI, final Viewport viewport) {
		}

		@Override
		protected void internalRender(final SpriteBatch batch, final BitmapFont baseFont,
				final GlyphLayout glyphLayout) {
		}

		private boolean hits(final float screenX, final float screenY) {
			if (!isVisible() || !ReplayPanel.this.holder.isVisible() || ReplayPanel.this.seeking) {
				return false;
			}
			if (!this.renderBounds.contains(screenX, screenY)) {
				return false;
			}
			this.pointerX = screenX;
			return true;
		}

		@Override
		public UIFrame touchDown(final float screenX, final float screenY, final int button) {
			return hits(screenX, screenY) ? this : null;
		}

		@Override
		public UIFrame touchUp(final float screenX, final float screenY, final int button) {
			return hits(screenX, screenY) ? this : null;
		}

		@Override
		public UIFrame getFrameChildUnderMouse(final float screenX, final float screenY) {
			return hits(screenX, screenY) ? this : null;
		}

		@Override
		public void mouseDown(final GameUI gameUI, final Viewport uiViewport) {
		}

		@Override
		public void mouseUp(final GameUI gameUI, final Viewport uiViewport) {
		}

		@Override
		public void mouseEnter(final GameUI gameUI, final Viewport uiViewport) {
		}

		@Override
		public void mouseExit(final GameUI gameUI, final Viewport uiViewport) {
		}

		@Override
		public void onClick(final int button) {
			final SimpleStatusBarFrame bar = ReplayPanel.this.statusBar;
			final float left = bar.getFramePointX(FramePoint.LEFT);
			final float width = bar.getFramePointX(FramePoint.RIGHT) - left;
			if (width <= 0) {
				return;
			}
			final float fraction = Math.max(0f, Math.min(1f, (this.pointerX - left) / width));
			seekTo(Math.round(fraction * ReplayPanel.this.playback.getLastTick()));
		}

		@Override
		public String getSoundKey() {
			return SOUND_KEY_INTERFACE_CLICK;
		}

		@Override
		public void mouseDragged(final GameUI rootFrame, final Viewport uiViewport, final float x, final float y) {
			this.pointerX = x;
		}
	}
}
