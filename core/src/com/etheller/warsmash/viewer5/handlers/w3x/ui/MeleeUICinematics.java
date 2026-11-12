package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.EnumSet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Blending;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.TextJustify;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.AbstractUIFrame;
import com.etheller.warsmash.parsers.fdf.frames.BackdropFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame2;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerColor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;

/**
 * Cinematic mode: the cinematic panel with its portrait and subtitles, and the fading and
 * hiding of the game interface around it.
 */
final class MeleeUICinematics {
	private static final String[] REFORGED_GRAPHICS_PACKAGES = { "_hd.w3mod\\", "_de.w3mod\\" };
	private static final float REFORGED_CINEMATIC_PORTRAIT_X = 0.028f;
	private static final float REFORGED_CINEMATIC_PORTRAIT_Y = 0.031f;
	private static final float REFORGED_CINEMATIC_PORTRAIT_SIZE = 0.1f;
	/** Without the portrait the Reforged subtitles are centered, the speaker's name this high above the bottom. */
	private static final float REFORGED_SUBTITLE_Y = 0.111f;
	private static final float SPEAKER_X = 0.015f;
	private static final float SPEAKER_Y = -0.035f;
	private static final float DIALOGUE_GAP = 0.004f;
	private static final float DIALOGUE_WIDTH = 0.6f;
	private boolean interfaceShown = true;
	private float interfaceOpacity = 1f;
	private float interfaceFadeRate = 0f;
	private boolean interfaceAlphaChanged = false;
	private StringFrame cinematicSpeakerText;
	private StringFrame cinematicDialogueText;
	private boolean subtitleDisplayOverride;
	private boolean cinematicPanelsHidden;
	private UIFrame cinematicScenePanel;
	/** The Reforged stone frame art is in the data, so a Reforged portrait stands in it. */
	private boolean reforgedPortraitFrame;

	final MeleeUI ui;

	MeleeUICinematics(final MeleeUI ui) {
		this.ui = ui;
	}

	void createCinematicPanel() {
		this.ui.cinematicPanel = this.ui.rootFrame.createFrame("CinematicPanel", this.ui.rootFrame, 0, 0);
		this.ui.cinematicPanel.setVisible(false);
		this.ui.reforgedCinematics = this.ui.war3MapViewer.isReforgedGraphics();
		this.cinematicSpeakerText = (StringFrame) this.ui.rootFrame.getFrameByName("CinematicSpeakerText", 0);
		this.ui.rootFrame.setText(this.cinematicSpeakerText, "");
		this.cinematicDialogueText = (StringFrame) this.ui.rootFrame.getFrameByName("CinematicDialogueText", 0);
		this.ui.rootFrame.setText(this.cinematicDialogueText, "");
		this.cinematicScenePanel = this.ui.rootFrame.getFrameByName("CinematicScenePanel", 0);
		final SpriteFrame2 cinematicPortraitSprite = (SpriteFrame2) this.ui.rootFrame
				.getFrameByName("CinematicPortrait", 0);
		this.ui.cinematicPortrait = new CinematicPortrait(cinematicPortraitSprite, this.cinematicScenePanel);
		styleCinematicPanel();
		anchorCinematicPanel();
		this.ui.infoPanel.positionPortrait();
	}

	static final class CinematicPortrait {
		private final EnumSet<AnimationTokens.SecondaryTag> recycleSet = EnumSet
				.noneOf(AnimationTokens.SecondaryTag.class);
		private EnumSet<AnimationTokens.SecondaryTag> secondaryTags;
		// If animationTargetDuration is 0, it will play the whole animation.
		private long portraitTargetDuration = 0;
		private long portraitCurrentDuration = 0;
		private long portraitShowDuration = -1;

		private final SpriteFrame2 cinematicPortrait;
		private final UIFrame cinematicScenePanel;

		public CinematicPortrait(final SpriteFrame2 cinematicPortrait, final UIFrame cinematicScenePanel) {
			this.cinematicPortrait = cinematicPortrait;
			this.cinematicScenePanel = cinematicScenePanel;
			this.secondaryTags = SequenceUtils.EMPTY;
		}

		public void endCinematicSequence() {
			this.secondaryTags = SequenceUtils.EMPTY;
			this.cinematicScenePanel.setVisible(false);
		}

		public void update(final float dt) {
			if (isResetNeeded()) {
				endCinematicSequence();
			}
			else {
				this.portraitCurrentDuration += (dt * 1000);
				if (this.portraitTargetDuration != 0) {
					if (this.portraitCurrentDuration < this.portraitTargetDuration) {
						this.cinematicPortrait.setSequenceLoopMode(SequenceLoopMode.ALWAYS_LOOP);
					}
					else {
						this.cinematicPortrait.setSequenceEnded(true);
						this.cinematicPortrait.setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP);
						this.portraitTargetDuration = 0;
					}
				}

				if (this.cinematicPortrait.isSequenceEnded()) {
					this.recycleSet.clear();
					this.recycleSet.addAll(this.secondaryTags);
					if (this.cinematicPortrait.setSequence(PrimaryTag.PORTRAIT, this.recycleSet) == null) {
						this.cinematicPortrait.setSequence(PrimaryTag.STAND, this.recycleSet);
					}
				}
			}
		}

		private boolean isResetNeeded() {
			return this.portraitCurrentDuration > this.portraitShowDuration;
		}

		public void talk(final UnitSound us, final float extraDuration) {
			// TODO we somehow called talk from null by clicking a unit right at the same
			// time it died, so I do a null check here until I study that case further.
			this.recycleSet.clear();
			this.recycleSet.addAll(this.secondaryTags);
			this.recycleSet.add(SecondaryTag.TALK);
			if (us != null) {
				this.portraitTargetDuration = (long) (1000 * us.getLastPlayedDuration());
			}
			else {
				this.portraitTargetDuration = 0;
			}
			this.portraitTargetDuration += (long) (1000 * extraDuration);
			this.portraitCurrentDuration = 0;

			if (this.cinematicPortrait.setSequence(PrimaryTag.PORTRAIT, this.recycleSet) == null) {
				this.cinematicPortrait.setSequence(PrimaryTag.STAND, this.recycleSet);
			}
		}

		public void setCinematicTalkingHead(final MdxModel portraitModel, final int teamColorIndex,
				final EnumSet<SecondaryTag> secondaryTags, final float faceLockTime) {
			this.cinematicScenePanel.setVisible(true);
			this.portraitShowDuration = (long) (1000 * faceLockTime);
			this.secondaryTags = secondaryTags;
			this.cinematicPortrait.setModel(portraitModel);
			this.cinematicPortrait.setTeamColor(teamColorIndex);
		}
	}

	/**
	 * The cinematic panel spans the whole screen instead of the centered 4:3 area: the letterbox bars reach both
	 * screen edges and the portrait with its frame, anchored to the panel's bottom left corner, sits at the left
	 * screen edge as in the Reforged Classic interface.
	 */
	void anchorCinematicPanel() {
		final float screenLeft = Math.min(0,
				(this.ui.uiViewport.getMinWorldWidth() - this.ui.uiViewport.getWorldWidth()) / 2);
		final float screenWidth = this.ui.uiViewport.getMinWorldWidth() - (2 * screenLeft);
		final AbstractRenderableFrame panel = (AbstractRenderableFrame) this.ui.cinematicPanel;
		panel.clearFramePointAssignments();
		panel.addSetPoint(new SetPoint(FramePoint.BOTTOMLEFT, this.ui.rootFrame, FramePoint.BOTTOMLEFT, screenLeft, 0));
		panel.addSetPoint(new SetPoint(FramePoint.TOPRIGHT, this.ui.rootFrame, FramePoint.TOPRIGHT, -screenLeft, 0));
		for (final String name : new String[] { "CinematicBottomBorder", "HDCinematicBackground" }) {
			final AbstractRenderableFrame frame = (AbstractRenderableFrame) this.ui.rootFrame.getFrameByName(name, 0);
			if (frame != null) {
				frame.clearFramePointAssignments();
				frame.setWidth(screenWidth);
				frame.addSetPoint(new SetPoint(FramePoint.BOTTOMLEFT, this.ui.cinematicPanel, FramePoint.BOTTOMLEFT, 0,
						0));
			}
		}
		final AbstractRenderableFrame top = (AbstractRenderableFrame) this.ui.rootFrame
				.getFrameByName("CinematicTopBorder", 0);
		if (top != null) {
			top.clearFramePointAssignments();
			top.setWidth(screenWidth);
			top.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.cinematicPanel, FramePoint.TOPLEFT, 0, 0));
		}
		panel.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
	}

	/**
	 * The cinematic panel of the graphics mode the map is played in. Classic: the letterbox borders and the portrait in
	 * its frame. Reforged: no letterbox, the dialogue over a dark gradient along the bottom of the screen, and the
	 * speaker's portrait only with "Show Portraits in Cinematics", in the Reforged stone frame. The HD frames of the
	 * panel's definition name artwork that only the Reforged graphics package has; data without it (an extracted
	 * Classic folder) keeps the Classic portrait frame rather than a bare portrait.
	 */
	private void styleCinematicPanel() {
		final boolean reforged = this.ui.reforgedCinematics;
		setFrameVisible("CinematicTopBorder", !reforged && !this.cinematicPanelsHidden);
		setFrameVisible("CinematicBottomBorder", !reforged && !this.cinematicPanelsHidden);
		final UIFrame background = this.ui.rootFrame.getFrameByName("HDCinematicBackground", 0);
		final UIFrame cover = this.ui.rootFrame.getFrameByName("HDCinematicPortraitCover", 0);
		if (!reforged || this.cinematicPanelsHidden) {
			setFrameVisible("HDCinematicBackground", false);
		}
		if (!reforged) {
			applyPortraitOption();
			return;
		}
		if ((background instanceof BackdropFrame) && !this.cinematicPanelsHidden) {
			Texture gradient = reforgedGraphicsTexture("CinematicPanelBackground");
			if (gradient == null) {
				gradient = this.ui.cinematicGradientTexture = createCinematicGradient();
			}
			((BackdropFrame) background).setBackground(gradient);
			background.setVisible(true);
		}
		final Texture border = reforgedGraphicsTexture("CinematicBorder");
		this.reforgedPortraitFrame = (border != null) && (cover instanceof BackdropFrame);
		applyPortraitOption();
		if (!this.reforgedPortraitFrame) {
			return;
		}
		((BackdropFrame) cover).setBackground(border);
		// Inside the window of the stone frame instead of the classic frame's.
		for (final String name : new String[] { "CinematicPortraitBackground", "CinematicPortrait" }) {
			final AbstractRenderableFrame frame = (AbstractRenderableFrame) this.ui.rootFrame.getFrameByName(name, 0);
			if (frame != null) {
				frame.clearFramePointAssignments();
				frame.setWidth(GameUI.convertX(this.ui.uiViewport, REFORGED_CINEMATIC_PORTRAIT_SIZE));
				frame.setHeight(GameUI.convertY(this.ui.uiViewport, REFORGED_CINEMATIC_PORTRAIT_SIZE));
				frame.addSetPoint(new SetPoint(FramePoint.BOTTOMLEFT, this.ui.cinematicPanel, FramePoint.BOTTOMLEFT,
						GameUI.convertX(this.ui.uiViewport, REFORGED_CINEMATIC_PORTRAIT_X),
						GameUI.convertY(this.ui.uiViewport, REFORGED_CINEMATIC_PORTRAIT_Y)));
			}
		}
	}

	/**
	 * The portrait as the options ask for it now; the in-game Options can change it between two lines. Reforged
	 * cinematics without the portrait show only the subtitles, centered near the bottom of the screen, with no dark
	 * gradient behind them.
	 */
	private void applyPortraitOption() {
		final boolean reforged = this.ui.reforgedCinematics;
		final boolean portrait = !reforged || GameOptions.get().isCinematicPortraits();
		setFrameVisible("CinematicPortraitContainer", portrait);
		setFrameVisible("CinematicPortraitCover", !reforged || !this.reforgedPortraitFrame);
		setFrameVisible("HDCinematicPortraitCover", reforged && this.reforgedPortraitFrame);
		if (reforged) {
			setFrameVisible("HDCinematicBackground", portrait && !this.cinematicPanelsHidden);
		}
		anchorSubtitles(portrait);
	}

	private void anchorSubtitles(final boolean besidePortrait) {
		final AbstractRenderableFrame speaker = this.cinematicSpeakerText;
		final AbstractRenderableFrame dialogue = this.cinematicDialogueText;
		final UIFrame cover = this.ui.rootFrame.getFrameByName("CinematicPortraitCover", 0);
		final TextJustify justify = besidePortrait ? TextJustify.LEFT : TextJustify.CENTER;
		this.cinematicSpeakerText.setJustifyH(justify);
		this.cinematicDialogueText.setJustifyH(justify);
		speaker.clearFramePointAssignments();
		dialogue.clearFramePointAssignments();
		dialogue.setWidth(GameUI.convertX(this.ui.uiViewport, DIALOGUE_WIDTH));
		final float gap = GameUI.convertY(this.ui.uiViewport, -DIALOGUE_GAP);
		if (besidePortrait) {
			speaker.addSetPoint(new SetPoint(FramePoint.TOPLEFT, cover, FramePoint.TOPRIGHT,
					GameUI.convertX(this.ui.uiViewport, SPEAKER_X), GameUI.convertY(this.ui.uiViewport, SPEAKER_Y)));
			dialogue.addSetPoint(new SetPoint(FramePoint.TOPLEFT, speaker, FramePoint.BOTTOMLEFT, 0, gap));
		}
		else {
			speaker.addSetPoint(new SetPoint(FramePoint.TOP, this.ui.cinematicPanel, FramePoint.BOTTOM, 0,
					GameUI.convertY(this.ui.uiViewport, REFORGED_SUBTITLE_Y)));
			dialogue.addSetPoint(new SetPoint(FramePoint.TOP, speaker, FramePoint.BOTTOM, 0, gap));
		}
		speaker.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
		dialogue.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
	}

	private void setFrameVisible(final String name, final boolean visible) {
		final UIFrame frame = this.ui.rootFrame.getFrameByName(name, 0);
		if (frame != null) {
			frame.setVisible(visible);
		}
	}

	/** The Reforged graphics package's version of a skin's texture; null without that package. */
	private Texture reforgedGraphicsTexture(final String skinField) {
		final String path = this.ui.rootFrame.getSkinField(skinField);
		if ((path == null) || path.isEmpty()) {
			return null;
		}
		final int dotIndex = path.lastIndexOf('.');
		final String dds = (dotIndex == -1 ? path : path.substring(0, dotIndex)) + ".dds";
		for (final String graphicsPackage : REFORGED_GRAPHICS_PACKAGES) {
			final String packaged = graphicsPackage + dds;
			if (this.ui.war3MapViewer.dataSource.has(packaged)) {
				return this.ui.rootFrame.loadTexture(packaged);
			}
		}
		return null;
	}

	/**
	 * Data without the Reforged graphics package: black, opaque at the bottom left and fading out upwards and to the
	 * right, as the package's cinematic panel background.
	 */
	private static Texture createCinematicGradient() {
		final int width = 256;
		final int height = 32;
		final Pixmap pixmap = new Pixmap(width, height, Format.RGBA8888);
		pixmap.setBlending(Blending.None);
		for (int y = 0; y < height; y++) {
			final float fromTop = (float) Math.sqrt(y / (float) (height - 1));
			for (int x = 0; x < width; x++) {
				final float alpha = fromTop * (1f - (0.97f * (x / (float) (width - 1))));
				pixmap.drawPixel(x, y, Color.rgba8888(0, 0, 0, alpha));
			}
		}
		final Texture texture = new Texture(pixmap);
		texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		pixmap.dispose();
		return texture;
	}

	public boolean skipCinematic() {
		if (!this.ui.cinematicPanel.isVisible()) {
			return false;
		}
		this.ui.unitOrderListener.issueGuiPlayerEvent(JassGameEventsWar3.EVENT_PLAYER_END_CINEMATIC.getEventId());
		return true;
	}

	/**
	 * As in 1.29: over the fade duration the game interface and the letterbox cross-fade
	 * linearly; a duration of 0 switches at once.
	 */
	public void showInterface(final boolean show, final float fadeDuration) {
		this.interfaceShown = show;
		final float target = show ? 1f : 0f;
		if ((fadeDuration <= 0f) || (this.interfaceOpacity == target)) {
			this.interfaceOpacity = target;
			this.interfaceFadeRate = 0f;
			applyInterfaceVisibility();
			return;
		}
		this.interfaceFadeRate = (target - this.interfaceOpacity) / fadeDuration;
		applyInterfaceVisibility();
	}

	void updateInterfaceFade(final float deltaTime) {
		if (this.interfaceFadeRate == 0f) {
			return;
		}
		this.interfaceOpacity += this.interfaceFadeRate * deltaTime;
		if ((this.interfaceFadeRate > 0f) ? (this.interfaceOpacity >= 1f) : (this.interfaceOpacity <= 0f)) {
			this.interfaceOpacity = this.interfaceShown ? 1f : 0f;
			this.interfaceFadeRate = 0f;
			applyInterfaceVisibility();
		}
		else {
			setInterfaceAlpha(getGameInterfaceAlpha(), 1f - this.interfaceOpacity);
		}
	}

	boolean isInterfaceFading() {
		return this.interfaceFadeRate != 0f;
	}

	float getGameInterfaceAlpha() {
		if (!this.ui.userUIEnabled) {
			return 0f;
		}
		if (isInterfaceFading()) {
			return this.interfaceOpacity;
		}
		return this.interfaceShown ? 1f : 0f;
	}

	private void setInterfaceAlpha(final float gameAlpha, final float cinematicAlpha) {
		if (!this.interfaceAlphaChanged && (gameAlpha >= 1f) && (cinematicAlpha >= 1f)) {
			return;
		}
		this.interfaceAlphaChanged = (gameAlpha < 1f) || (cinematicAlpha < 1f);
		for (final UIFrame frame : new UIFrame[] { this.ui.consoleUI, this.ui.resourceBar, this.ui.timeIndicator,
				this.ui.minimapButtonBar, this.ui.smashSimpleInfoPanel, this.ui.smashCommandButtons,
				this.ui.upperButtonBar, this.ui.heroBar }) {
			fadeFrame(frame, gameAlpha);
		}
		fadeFrame(this.ui.cinematicPanel, cinematicAlpha);
		if (this.ui.portrait.modelInstance != null) {
			this.ui.portrait.modelInstance.setVertexAlpha(gameAlpha);
		}
	}

	private static void fadeFrame(final UIFrame frame, final float alpha) {
		if (frame instanceof AbstractRenderableFrame) {
			((AbstractRenderableFrame) frame).setRenderAlpha(alpha);
		}
		fadeFrameModels(frame, alpha);
	}

	// Interface models are drawn by the UI scene, not by their frames, so they fade apart from the batch.
	private static void fadeFrameModels(final UIFrame frame, final float alpha) {
		if (frame instanceof SpriteFrame) {
			((SpriteFrame) frame).setModelAlpha(alpha);
		}
		else if (frame instanceof SpriteFrame2) {
			((SpriteFrame2) frame).setModelAlpha(alpha);
		}
		if (frame instanceof AbstractUIFrame) {
			for (final UIFrame child : ((AbstractUIFrame) frame).getChildFrames()) {
				fadeFrameModels(child, alpha);
			}
		}
	}

	public void enableUserUI(final boolean enable) {
		this.ui.userUIEnabled = enable;
		applyInterfaceVisibility();
	}

	public CineFilter getCineFilter() {
		return this.ui.cineFilter;
	}

	/**
	 * EnableUserUI(false) hides the game interface during a fade without entering cinematic mode, so the letterbox
	 * follows ShowInterface alone.
	 */
	private void applyInterfaceVisibility() {
		if (isInterfaceFading()) {
			updateInterfaceVisibility(this.ui.userUIEnabled);
			this.ui.cinematicPanel.setVisible(true);
			setInterfaceAlpha(getGameInterfaceAlpha(), 1f - this.interfaceOpacity);
		}
		else {
			updateInterfaceVisibility(this.interfaceShown && this.ui.userUIEnabled);
			this.ui.cinematicPanel.setVisible(!this.interfaceShown);
			setInterfaceAlpha(1f, 1f);
		}
		this.ui.infoPanel.positionPortrait();
		this.ui.updateWorldFrameViewport();
	}

	void updateInterfaceVisibility(final boolean show) {
		this.ui.consoleUI.setVisible(show);
		this.ui.resourceBar.setVisible(show);
		this.ui.timeIndicator.setVisible(show);
		this.ui.meleeUIMinimap.setVisible(show);
		this.ui.minimapButtonBar.setVisible(show);
		this.ui.smashSimpleInfoPanel.setVisible(show);
		this.ui.smashCommandButtons.setVisible(show && (this.ui.replayPanel == null));
		if (this.ui.replayPanel != null) {
			this.ui.replayPanel.setVisible(show);
		}
		this.ui.upperButtonBar.setVisible(show);
		if (this.ui.heroBar != null) {
			this.ui.heroBar.setVisible(show);
		}
		this.ui.cinematicPanel.setVisible(!show);

		this.ui.infoPanel.positionPortrait();
		this.ui.updateWorldFrameViewport();
	}

	public void setCinematicScene(final int portraitUnitId, final CPlayerColor color, final String speakerTitle,
			final String text, final float sceneDuration, final float voiceoverDuration) {
		final RenderUnitType unitTypeData = this.ui.war3MapViewer.getUnitTypeData(new War3ID(portraitUnitId));
		if (this.ui.cinematicPanel.isVisible()) {
			applyPortraitOption();
			this.ui.rootFrame.setText(this.cinematicSpeakerText, speakerTitle);
			this.ui.rootFrame.setText(this.cinematicDialogueText, areSubtitlesShown() ? text : "");
			this.ui.cinematicPortrait.setCinematicTalkingHead(unitTypeData == null ? null
					: unitTypeData.getPortraitModel(), color.getHandleId(),
					unitTypeData == null ? SequenceUtils.EMPTY : unitTypeData.getRequiredAnimationNames(),
					sceneDuration);
			this.ui.cinematicPortrait.talk(null, voiceoverDuration);
		}
		else {
			if (unitTypeData != null) {
				this.ui.portrait.setCinematicTalkingHead(unitTypeData.getPortraitModel(), color.getHandleId(),
						unitTypeData.getRequiredAnimationNames(), sceneDuration, null, voiceoverDuration);
			}
			if (areSubtitlesShown()) {
				this.ui.messages.showGameMessage("|Cffffcc00" + speakerTitle + "|r: " + text, sceneDuration);
			}
		}
	}

	public void endCinematicScene() {
		this.ui.cinematicPortrait.endCinematicSequence();
		this.ui.rootFrame.setText(this.cinematicSpeakerText, "");
		this.ui.rootFrame.setText(this.cinematicDialogueText, "");
		this.ui.portrait.faceLockTime = 0;
		this.ui.input.selectUnit(this.ui.selectedUnit);
	}

	private boolean areSubtitlesShown() {
		return this.subtitleDisplayOverride || GameOptions.get().isSubtitles();
	}

	public void forceCinematicSubtitles(final boolean value) {
		this.subtitleDisplayOverride = value;
	}

	/**
	 * BlzHideCinematicPanels: cinematic mode without its letterbox bars (Classic) or its dark gradient (Reforged); the
	 * portrait and the dialogue stay.
	 */
	public void hideCinematicPanels(final boolean hide) {
		if (this.cinematicPanelsHidden != hide) {
			this.cinematicPanelsHidden = hide;
			styleCinematicPanel();
		}
	}

	public void enableUserControl(final boolean value) {
		this.ui.userControlEnabled = value;
		this.ui.cursorFrame.setVisible(value);
		if (!value) {
			this.ui.allowDrag = false;
			this.ui.subMenuOrderIdStack.clear();
			this.ui.activeCommandUnit = null;
			this.ui.activeCommand = null;
			this.ui.activeCommandOrderId = -1;
			this.ui.mouseDownUIFrame = null;
			this.ui.war3MapViewer.clearUnitMouseOverHighlight();
			this.ui.dragSelectPreviewUnits.clear();
			this.ui.mouseOverUnit = null;
		}
	}
}
