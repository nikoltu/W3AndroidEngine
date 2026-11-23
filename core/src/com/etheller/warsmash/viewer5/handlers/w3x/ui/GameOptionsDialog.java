package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.launcher.CustomFolderChooser;
import com.etheller.warsmash.launcher.LauncherOptions;
import com.etheller.warsmash.launcher.RenderScaleStore;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FrameClass;
import com.etheller.warsmash.parsers.fdf.datamodel.FrameDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.MenuItem;
import com.etheller.warsmash.parsers.fdf.datamodel.SetPointDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.fields.FloatFrameDefinitionField;
import com.etheller.warsmash.parsers.fdf.datamodel.fields.StringFrameDefinitionField;
import com.etheller.warsmash.parsers.fdf.frames.AbstractUIFrame;
import com.etheller.warsmash.parsers.fdf.frames.CheckBoxFrame;
import com.etheller.warsmash.parsers.fdf.frames.GlueTextButtonFrame;
import com.etheller.warsmash.parsers.fdf.frames.MenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.PopupMenuFrame;
import com.etheller.warsmash.parsers.fdf.frames.ScrollBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;

/**
 * The Options dialog of the game menu and the main menu. Controls are filled from {@link GameOptions} when a panel
 * opens and written back only on OK. A control this engine cannot honor on this device is disabled.
 */
final class GameOptionsDialog {
	interface Host {
		void closed();

		void applied();

		/** Whether this menu can use another content folder at once; only the main menu can. */
		default boolean offersCustomFolder() {
			return false;
		}

		/** The player chose the folder to keep their own maps in (null: none); the game uses it from now on. */
		default void customFolderChosen(final File folder) {
		}
	}

	// The data gives the panel no size of its own; it shares the backdrop of the main panel of the game menu.
	private static final float PANEL_WIDTH = 0.288f;
	private static final float PANEL_HEIGHT = 0.384f;

	private final GameUI rootFrame;
	private final Viewport uiViewport;
	private final SimpleFrame holder;
	private final UIFrame backdrop;
	private final Host host;
	private final boolean catchCursor;
	private final boolean enableMusic;
	private final List<PopupMenuFrame> popups = new ArrayList<>();
	private UIFrame panel;
	private UIFrame optionsPanel;
	private UIFrame bottomPanel;
	private UIFrame activePanel;
	private Runnable activeWrite;
	private boolean showing;

	private ScrollBarFrame mouseScroll;
	private CheckBoxFrame mouseScrollDisable;
	private ScrollBarFrame keyScroll;
	private CheckBoxFrame tooltips;
	private CheckBoxFrame healthBars;
	private CheckBoxFrame cinematicPortraits;

	private ScrollBarFrame gamma;
	private CheckBoxFrame vSync;
	private int particles;
	private int unitShadows;
	private int renderScale;
	private int frameCap;
	private PopupMenuFrame renderScaleMenu;
	private PopupMenuFrame frameCapMenu;

	private CheckBoxFrame sound;
	private ScrollBarFrame soundVolume;
	private CheckBoxFrame music;
	private ScrollBarFrame musicVolume;
	private CheckBoxFrame ambient;
	private CheckBoxFrame movement;
	private CheckBoxFrame unit;
	private CheckBoxFrame subtitles;

	GameOptionsDialog(final GameUI rootFrame, final Viewport uiViewport, final SimpleFrame holder,
			final UIFrame backdrop, final Host host, final boolean catchCursor, final boolean enableMusic) {
		this.rootFrame = rootFrame;
		this.uiViewport = uiViewport;
		this.holder = holder;
		this.backdrop = backdrop;
		this.host = host;
		this.catchCursor = catchCursor;
		this.enableMusic = enableMusic;
	}

	SimpleFrame getHolder() {
		return this.holder;
	}

	boolean isShowing() {
		return this.showing;
	}

	void show() {
		if (this.panel == null) {
			create();
		}
		this.showing = true;
		this.panel.setVisible(true);
		this.backdrop.setVisible(true);
		showOptionsPanel();
	}

	private void close() {
		this.showing = false;
		this.panel.setVisible(false);
		this.host.closed();
	}

	boolean keyDown(final int keycode) {
		if (keycode == Input.Keys.ESCAPE) {
			if (closePopups()) {
				return true;
			}
			if (this.activePanel == this.optionsPanel) {
				close();
			}
			else {
				showOptionsPanel();
			}
		}
		else if ((keycode == Input.Keys.ENTER) && (this.activePanel != this.optionsPanel)) {
			accept();
		}
		return true;
	}

	/** A press outside an open list closes it, as the list has no other way out on a touch screen. */
	void pressed(final UIFrame frame) {
		if (!this.showing) {
			return;
		}
		for (final PopupMenuFrame popup : this.popups) {
			if (popup.getPopupMenuFrame().isVisible() && !isSelfOrDescendant(frame, popup)
					&& !isSelfOrDescendant(frame, popup.getPopupMenuFrame())) {
				popup.getPopupMenuFrame().setVisible(false);
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

	private boolean closePopups() {
		boolean closed = false;
		for (final PopupMenuFrame popup : this.popups) {
			closed |= popup.getPopupMenuFrame().isVisible();
			popup.getPopupMenuFrame().setVisible(false);
		}
		return closed;
	}

	private void create() {
		this.panel = this.rootFrame.createFrame("EscMenuOptionsPanel", this.holder, 0, 0);
		this.holder.add(this.panel);
		this.optionsPanel = frame("OptionsPanel");
		this.optionsPanel.setWidth(GameUI.convertX(this.uiViewport, PANEL_WIDTH));
		this.optionsPanel.setHeight(GameUI.convertY(this.uiViewport, PANEL_HEIGHT));
		this.bottomPanel = frame("BottomButtonPanel");

		final UIFrame gameplayPanel = frame("GameplayPanel");
		final UIFrame videoPanel = frame("VideoPanel");
		final UIFrame soundPanel = frame("SoundPanel");
		frame("NetworkPanel").setVisible(false);
		gameplayPanel.setVisible(false);
		videoPanel.setVisible(false);
		soundPanel.setVisible(false);
		this.bottomPanel.setVisible(false);

		button("GameplayButton", () -> showPanel(gameplayPanel, this::readGameplay, this::writeGameplay));
		button("VideoButton", () -> showPanel(videoPanel, this::readVideo, this::writeVideo));
		button("SoundButton", () -> showPanel(soundPanel, this::readSound, this::writeSound));
		// No network game exists to apply a latency to.
		((GlueTextButtonFrame) frame("NetworkButton")).setEnabled(false);
		button("OptionsPreviousButton", this::close);
		button("OptionsOKButton", this::accept);
		button("OptionsCancelButton", this::showOptionsPanel);

		createGameplayControls();
		createVideoControls();
		createSoundControls();
		// The lists are made as children of the root, which is drawn under a modal holder.
		for (final PopupMenuFrame popup : this.popups) {
			this.rootFrame.remove(popup.getPopupMenuFrame());
			this.holder.add(popup.getPopupMenuFrame());
		}
	}

	private void createGameplayControls() {
		// The simulation runs at one fixed rate and the rates of the original's speeds are not measured.
		enable((ScrollBarFrame) frame("GameSpeedSlider"), false);

		this.mouseScroll = (ScrollBarFrame) frame("MouseScrollSlider");
		this.keyScroll = (ScrollBarFrame) frame("KeyScrollSlider");
		this.mouseScrollDisable = (CheckBoxFrame) frame("MouseScrollDisable");
		this.tooltips = (CheckBoxFrame) frame("TooltipsCheckBox");
		this.healthBars = (CheckBoxFrame) frame("HealthBarsCheckBox");
		// A finger scrolls by dragging, so this slider sets that speed; only the edge scrolling can be turned off.
		enable(this.mouseScroll, this.catchCursor || TouchInputRouter.isActive());
		enable(this.mouseScrollDisable, this.catchCursor || TouchInputRouter.isActive());
		enable(this.keyScroll,
				!TouchInputRouter.isActive() || Gdx.input.isPeripheralAvailable(Input.Peripheral.HardwareKeyboard));
		disableCheckBox("SubgroupCheckBox");
		disableCheckBox("FormationToggleCheckBox");
		hide("CustomKeysLabel", "CustomKeysValue", "DifficultyLabel", "DifficultyValue");
		// Reforged has this choice in its own options screen; the panel gets it in the place of the hidden lines.
		this.cinematicPortraits = addCheckBox("CinematicPortraitsCheckBox", "HealthBarsCheckBox",
				"OPTIONS_SHOW_CINE_PORTRAITS",
				this.rootFrame.smashString("SMASH_SHOW_CINE_PORTRAITS", "Show Portraits in Cinematics"));
		addCustomFolderRow();
	}

	/**
	 * Where the player's own maps and campaigns are kept: a button and the folder under it, in the place the hidden
	 * lines leave free, when the platform can ask for a folder here.
	 */
	private void addCustomFolderRow() {
		final CustomFolderChooser chooser = CustomFolderChooser.Holder.get();
		final UIFrame above = frame("CinematicPortraitsCheckBox");
		final FrameDefinition buttonTemplate = this.rootFrame.getTemplates().getFrame("EscMenuButtonTemplate");
		final FrameDefinition labelTemplate = this.rootFrame.getTemplates().getFrame("EscMenuLabelTextTemplate");
		if ((chooser == null) || !this.host.offersCustomFolder() || (above == null) || !(above.getParent() instanceof AbstractUIFrame)
				|| (buttonTemplate == null) || (labelTemplate == null)) {
			return;
		}
		final AbstractUIFrame panel = (AbstractUIFrame) above.getParent();
		final UIFrame made = this.rootFrame.createFrame("EscMenuButtonTemplate", panel, 0, 0);
		if (!(made instanceof GlueTextButtonFrame)) {
			return;
		}
		final GlueTextButtonFrame button = (GlueTextButtonFrame) made;
		button.setWidth(GameUI.convertX(this.uiViewport, 0.2f));
		button.setHeight(GameUI.convertY(this.uiViewport, 0.028f));
		button.addSetPoint(new SetPoint(FramePoint.TOPLEFT, above, FramePoint.BOTTOMLEFT, 0,
				GameUI.convertY(this.uiViewport, -0.004f)));
		if (button.getButtonText() instanceof StringFrame) {
			this.rootFrame.setText((StringFrame) button.getButtonText(),
					this.rootFrame.smashString("SMASH_CUSTOM_CONTENT_FOLDER", "Custom Content Folder..."));
		}
		panel.add(button);

		final FrameDefinition label = new FrameDefinition(FrameClass.Frame, "TEXT", "CustomFolderValue");
		label.inheritFrom(labelTemplate, false);
		label.set("Width", new FloatFrameDefinitionField(0.26f));
		label.set("Height", new FloatFrameDefinitionField(0.02f));
		label.set("Text", new StringFrameDefinitionField(customFolderText(chooser.current())));
		label.add(new SetPointDefinition(FramePoint.TOPLEFT, button.getName(), FramePoint.BOTTOMLEFT, 0, 0));
		final UIFrame value = this.rootFrame.inflate(label, panel, null, label.has("DecorateFileNames"));
		panel.add(value);
		button.setOnClick(() -> chooser.choose(kept -> {
			if (kept != null) {
				this.host.customFolderChosen(kept);
				if (value instanceof StringFrame) {
					this.rootFrame.setText((StringFrame) value, customFolderText(kept));
				}
			}
		}));
	}

	private String customFolderText(final File folder) {
		return folder == null ? this.rootFrame.smashString("SMASH_NONE_CHOSEN", "None chosen") : folder.getPath();
	}

	/**
	 * A check box with its label under another one of the panel, made from the templates of the panel's own boxes;
	 * null when the data lacks them.
	 */
	private CheckBoxFrame addCheckBox(final String name, final String below, final String textKey,
			final String absentText) {
		final UIFrame above = frame(below);
		final FrameDefinition boxTemplate = this.rootFrame.getTemplates().getFrame("EscMenuCheckBoxTemplate");
		final FrameDefinition labelTemplate = this.rootFrame.getTemplates().getFrame("EscMenuLabelTextTemplate");
		if ((above == null) || !(above.getParent() instanceof AbstractUIFrame) || (boxTemplate == null)
				|| (labelTemplate == null)) {
			return null;
		}
		final AbstractUIFrame panel = (AbstractUIFrame) above.getParent();
		final FrameDefinition box = new FrameDefinition(FrameClass.Frame, "GLUECHECKBOX", name);
		box.inheritFrom(boxTemplate, true);
		box.add(new SetPointDefinition(FramePoint.TOPLEFT, below, FramePoint.BOTTOMLEFT, 0, 0));
		final UIFrame boxFrame = this.rootFrame.inflate(box, panel, null, box.has("DecorateFileNames"));
		panel.add(boxFrame);
		final String text = this.rootFrame.getTemplates().getDecoratedString(textKey);
		final FrameDefinition label = new FrameDefinition(FrameClass.Frame, "TEXT", name + "Label");
		label.inheritFrom(labelTemplate, false);
		label.set("Width", new FloatFrameDefinitionField(0.195f));
		label.set("Height", new FloatFrameDefinitionField(0.024f));
		label.set("Text", new StringFrameDefinitionField(textKey.equals(text) ? absentText : text));
		label.add(new SetPointDefinition(FramePoint.LEFT, name, FramePoint.RIGHT, 0.003f, 0));
		panel.add(this.rootFrame.inflate(label, panel, null, label.has("DecorateFileNames")));
		return boxFrame instanceof CheckBoxFrame ? (CheckBoxFrame) boxFrame : null;
	}

	private void createVideoControls() {
		this.gamma = (ScrollBarFrame) frame("GammaSlider");
		this.vSync = (CheckBoxFrame) frame("VSyncCheckBox");
		if (GameOptions.isVSyncAdjustable()) {
			enable(this.vSync, true);
		}
		else {
			// The screen's own refresh always syncs here and the screen has one fixed mode, so these lines are left
			// out and the ones below move up.
			this.vSync = null;
			hide("VSyncLabel", "VSyncCheckBox", "WindowModeLabel", "EscOptionsWindowModeMenu", "ResolutionLabel",
					"EscOptionsResolutionMenu");
			final UIFrame particlesLabel = frame("ParticlesLabel");
			final UIFrame gammaLabel = frame("GammaLabel");
			if ((particlesLabel != null) && (gammaLabel != null)) {
				particlesLabel.addSetPoint(new SetPoint(FramePoint.TOPLEFT, gammaLabel, FramePoint.BOTTOMLEFT, 0,
						GameUI.convertY(this.uiViewport, -0.03f)));
			}
		}
		setUpPopup("EscOptionsParticlesMenu", index -> this.particles = index);
		setUpPopup("EscOptionsShadowsMenu", index -> this.unitShadows = index);
		if (GameOptions.isVSyncAdjustable()) {
			disablePopup("EscOptionsWindowModeMenu", this.rootFrame.getTemplates().getDecoratedString(
					Gdx.graphics.isFullscreen() ? "WINDOW_MODE_FULLSCREEN" : "WINDOW_MODE_WINDOWED"));
			disablePopup("EscOptionsResolutionMenu", Gdx.graphics.getWidth() + " x " + Gdx.graphics.getHeight());
		}
		// The panel has no lines for these two; the lights and occlusion lines, which no setting backs, make room.
		final List<String> scales = new ArrayList<>();
		for (final int percent : GraphicsSettings.RENDER_SCALES) {
			scales.add(percent + "%");
		}
		if (RenderScaleStore.Holder.get() != null) {
			this.renderScaleMenu = reuseLine("EscOptionsLightsMenu", "LightsLabel",
					this.rootFrame.smashString("SMASH_RENDER_SCALE", "Render scale:"), scales,
					index -> this.renderScale = index);
		}
		else {
			disablePopup("EscOptionsLightsMenu", "");
		}
		final List<String> caps = new ArrayList<>();
		for (final int fps : LauncherOptions.FRAME_CAPS) {
			caps.add(fps == 0 ? this.rootFrame.smashString("SMASH_NO_LIMIT", "No limit") : fps + " FPS");
		}
		this.frameCapMenu = reuseLine("EscOptionsOcclusionMenu", "OcclusionLabel",
				this.rootFrame.smashString("SMASH_FRAME_RATE_LIMIT", "Frame rate limit:"), caps,
				index -> this.frameCap = index);
		hide("ModelDetailLabel", "ModelDetailValue", "AnimQualityLabel", "AnimQualityValue", "TextureQualityLabel",
				"TextureQualityValue", "ResolutionLabel", "ResolutionValue", "SpellFilterLabel", "SpellFilterValue",
				"SpellDetailLabel", "SpellDetailValue", "GammaValue");
	}

	private void createSoundControls() {
		this.sound = (CheckBoxFrame) frame("SoundCheckBox");
		this.soundVolume = (ScrollBarFrame) frame("SoundVolumeSlider");
		this.music = (CheckBoxFrame) frame("MusicCheckBox");
		this.musicVolume = (ScrollBarFrame) frame("MusicVolumeSlider");
		this.ambient = (CheckBoxFrame) frame("AmbientCheckBox");
		this.movement = (CheckBoxFrame) frame("MovementCheckBox");
		this.unit = (CheckBoxFrame) frame("UnitCheckBox");
		this.subtitles = (CheckBoxFrame) frame("SubtitlesCheckBox");
		// warsmash.ini can leave out music altogether.
		enable(this.music, this.enableMusic);
		enable(this.musicVolume, this.enableMusic);
		disableCheckBox("EnviroCheckBox");
		disableCheckBox("PositionalCheckBox");
		hide("ProviderLabel", "ProviderValue");
	}

	/** The panel differs between game data versions: older data has no frame for some of the controls. */
	private UIFrame frame(final String name) {
		return this.rootFrame.getFrameByName(name, 0);
	}

	private static void enable(final ScrollBarFrame slider, final boolean enabled) {
		if (slider != null) {
			slider.setEnabled(enabled);
		}
	}

	private static void enable(final CheckBoxFrame checkBox, final boolean enabled) {
		if (checkBox != null) {
			checkBox.setEnabled(enabled);
		}
	}

	private static void check(final CheckBoxFrame checkBox, final boolean checked) {
		if (checkBox != null) {
			checkBox.setChecked(checked);
		}
	}

	private static boolean checked(final CheckBoxFrame checkBox, final boolean absent) {
		return checkBox != null ? checkBox.isChecked() : absent;
	}

	private static int value(final ScrollBarFrame slider, final int absent) {
		return slider != null ? slider.getValue() : absent;
	}

	private void button(final String name, final Runnable action) {
		((GlueTextButtonFrame) frame(name)).setOnClick(action);
	}

	private void hide(final String... names) {
		for (final String name : names) {
			final UIFrame frame = frame(name);
			if (frame != null) {
				frame.setVisible(false);
			}
		}
	}

	private void disableCheckBox(final String name) {
		final CheckBoxFrame checkBox = (CheckBoxFrame) frame(name);
		enable(checkBox, false);
		check(checkBox, false);
	}

	private interface IndexListener {
		void selected(int index);
	}

	private void setUpPopup(final String name, final IndexListener listener) {
		final PopupMenuFrame popup = (PopupMenuFrame) frame(name);
		if (popup == null) {
			return;
		}
		this.popups.add(popup);
		popup.setMenuClickListener((button, index) -> {
			listener.selected(index);
			showPopupValue(popup, index);
		});
	}

	private void showPopupValue(final PopupMenuFrame popup, final int index) {
		if (popup == null) {
			return;
		}
		final MenuFrame menu = (MenuFrame) popup
				.getPopupMenuFrame();
		setPopupTitle(popup, menu.getMenuItem(index).getText());
	}

	private void setPopupTitle(final PopupMenuFrame popup, final String text) {
		this.rootFrame.setText((StringFrame) ((GlueTextButtonFrame) popup.getPopupTitleFrame()).getButtonText(), text);
	}

	/** A line of the panel that offers no choice, turned into one with these values; null when the data lacks it. */
	private PopupMenuFrame reuseLine(final String popupName, final String labelName, final String label,
			final List<String> values, final IndexListener listener) {
		final PopupMenuFrame popup = (PopupMenuFrame) frame(popupName);
		if ((popup == null) || !(popup.getPopupMenuFrame() instanceof MenuFrame)) {
			return null;
		}
		final UIFrame labelFrame = frame(labelName);
		if (labelFrame instanceof StringFrame) {
			this.rootFrame.setText((StringFrame) labelFrame, label);
		}
		final List<MenuItem> items = new ArrayList<>();
		for (final String value : values) {
			items.add(new MenuItem(value, -2));
		}
		((MenuFrame) popup.getPopupMenuFrame()).setItems(this.uiViewport, items);
		setUpPopup(popupName, listener);
		return popup;
	}

	private void disablePopup(final String name, final String shownValue) {
		final PopupMenuFrame popup = (PopupMenuFrame) frame(name);
		if (popup == null) {
			return;
		}
		popup.setEnabled(false);
		((GlueTextButtonFrame) popup.getPopupTitleFrame()).setEnabled(false);
		setPopupTitle(popup, shownValue);
	}

	private void showOptionsPanel() {
		closePopups();
		if (this.activePanel != null) {
			this.activePanel.setVisible(false);
		}
		this.bottomPanel.setVisible(false);
		this.activePanel = this.optionsPanel;
		this.activeWrite = null;
		this.optionsPanel.setVisible(true);
		fit();
	}

	private void showPanel(final UIFrame subPanel, final Runnable read, final Runnable write) {
		this.optionsPanel.setVisible(false);
		this.activePanel = subPanel;
		this.activeWrite = write;
		subPanel.setVisible(true);
		this.bottomPanel.setVisible(true);
		fit();
		read.run();
		fit();
	}

	private void fit() {
		SavedGameDialog.fit(this.holder, this.backdrop, this.optionsPanel, this.rootFrame, this.uiViewport);
	}

	private void accept() {
		final GameOptions options = GameOptions.get();
		this.activeWrite.run();
		options.save();
		options.apply();
		this.host.applied();
		showOptionsPanel();
	}

	private void setSlider(final ScrollBarFrame slider, final int value) {
		if (slider != null) {
			slider.setValue(this.rootFrame, this.uiViewport, value);
		}
	}

	private void readGameplay() {
		final GameOptions options = GameOptions.get();
		setSlider(this.mouseScroll, options.getMouseScroll());
		setSlider(this.keyScroll, options.getKeyboardScroll());
		check(this.mouseScrollDisable, options.isMouseScrollDisabled());
		check(this.tooltips, options.isEnhancedTooltips());
		check(this.healthBars, options.isAlwaysShowStatusBars());
		check(this.cinematicPortraits, options.isCinematicPortraits());
	}

	private void writeGameplay() {
		final GameOptions options = GameOptions.get();
		options.setMouseScroll(value(this.mouseScroll, options.getMouseScroll()));
		options.setKeyboardScroll(value(this.keyScroll, options.getKeyboardScroll()));
		options.setMouseScrollDisabled(checked(this.mouseScrollDisable, options.isMouseScrollDisabled()));
		options.setEnhancedTooltips(checked(this.tooltips, options.isEnhancedTooltips()));
		options.setAlwaysShowStatusBars(checked(this.healthBars, options.isAlwaysShowStatusBars()));
		options.setCinematicPortraits(checked(this.cinematicPortraits, options.isCinematicPortraits()));
	}

	private void readVideo() {
		final GameOptions options = GameOptions.get();
		setSlider(this.gamma, options.getGamma());
		check(this.vSync, options.isVSync());
		this.particles = options.getParticles();
		this.unitShadows = options.isUnitShadows() ? 1 : 0;
		showPopupValue((PopupMenuFrame) frame("EscOptionsParticlesMenu"), this.particles);
		showPopupValue((PopupMenuFrame) frame("EscOptionsShadowsMenu"), this.unitShadows);
		final RenderScaleStore store = RenderScaleStore.Holder.get();
		if ((store != null) && (this.renderScaleMenu != null)) {
			this.renderScale = GraphicsSettings.renderScaleChoice(store.percent());
			showPopupValue(this.renderScaleMenu, this.renderScale);
		}
		if (this.frameCapMenu != null) {
			this.frameCap = LauncherOptions.frameCapChoice(options.getFrameCap());
			showPopupValue(this.frameCapMenu, this.frameCap);
		}
	}

	private void writeVideo() {
		final GameOptions options = GameOptions.get();
		options.setGamma(value(this.gamma, options.getGamma()));
		options.setVSync(checked(this.vSync, options.isVSync()));
		options.setParticles(this.particles);
		options.setUnitShadows(this.unitShadows != 0);
		final RenderScaleStore store = RenderScaleStore.Holder.get();
		if ((store != null) && (this.renderScaleMenu != null)) {
			final int percent = GraphicsSettings.RENDER_SCALES[this.renderScale];
			if (percent != GraphicsSettings.RENDER_SCALES[GraphicsSettings.renderScaleChoice(store.percent())]) {
				store.set(percent);
			}
		}
		if (this.frameCapMenu != null) {
			options.setFrameCap(LauncherOptions.FRAME_CAPS[this.frameCap]);
		}
	}

	private void readSound() {
		final GameOptions options = GameOptions.get();
		check(this.sound, options.isSound());
		setSlider(this.soundVolume, options.getSoundVolume());
		check(this.music, options.isMusic());
		setSlider(this.musicVolume, options.getMusicVolume());
		check(this.ambient, options.isAmbientSounds());
		check(this.movement, options.isMovementSounds());
		check(this.unit, options.isUnitSounds());
		check(this.subtitles, options.isSubtitles());
	}

	private void writeSound() {
		final GameOptions options = GameOptions.get();
		options.setSound(checked(this.sound, options.isSound()));
		options.setSoundVolume(value(this.soundVolume, options.getSoundVolume()));
		options.setMusic(checked(this.music, options.isMusic()));
		options.setMusicVolume(value(this.musicVolume, options.getMusicVolume()));
		options.setAmbientSounds(checked(this.ambient, options.isAmbientSounds()));
		options.setMovementSounds(checked(this.movement, options.isMovementSounds()));
		options.setUnitSounds(checked(this.unit, options.isUnitSounds()));
		options.setSubtitles(checked(this.subtitles, options.isSubtitles()));
	}
}
