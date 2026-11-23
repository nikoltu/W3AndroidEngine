package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.TextJustify;
import com.etheller.warsmash.parsers.fdf.datamodel.Vector4Definition;
import com.etheller.warsmash.parsers.fdf.frames.FilterModeTextureFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleStatusBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextureFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.FastNumberFormat;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.PortraitCameraManager;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItem;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.commandbuttons.CommandCardActivationReceiverPreviewCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.UiFrameState;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.CommandButtonModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.CommandCardModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.SelectedUnitModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.UnitDetailsModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold.CAbilityCargoHold;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDefenseType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CodeKeyType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CWidgetAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.AbstractClickableActionFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.MultiSelectionIconListener;
import com.hiveworkshop.rms.parsers.mdlx.MdlxLayer.FilterMode;

/**
 * The console's middle: the portrait and the selected unit's info panel with its stats, buffs,
 * build queue, cargo and the icons of a multiple selection.
 */
final class MeleeUIInfoPanel {

	private final Vector2 projectionTemp1 = new Vector2();
	private final Vector2 projectionTemp2 = new Vector2();
	private final Rectangle portraitBoundsTemp = new Rectangle();
	private StringFrame simpleBuildingDescriptionValue;
	private TextureFrame simpleBuildQueueBackdrop;
	private final UIFrame[] cargoBackdrop = new UIFrame[WarsmashConstants.MAX_SELECTION_SIZE];
	private final MultiSelectionIcon[] cargoUnitFrames = new MultiSelectionIcon[WarsmashConstants.MAX_SELECTION_SIZE];
	private TextureFrame attack1IconBackdrop;
	private StringFrame attack1InfoPanelIconValue;
	private TextureFrame attack2IconBackdrop;
	private StringFrame attack2InfoPanelIconValue;
	private TextureFrame armorIconBackdrop;
	private StringFrame armorInfoPanelIconValue;
	private InfoPanelIconBackdrops damageBackdrops;
	private InfoPanelIconBackdrops defenseBackdrops;
	private InfoPanelIconBackdrops damageBackdropsNeutral;
	private InfoPanelIconBackdrops defenseBackdropsNeutral;
	private SimpleFrame smashAttack1IconWrapper;
	private SimpleFrame smashAttack2IconWrapper;
	private SimpleFrame smashArmorIconWrapper;
	private TextureFrame primaryAttributeIcon;
	private StringFrame strengthValue;
	private StringFrame agilityValue;
	private StringFrame intelligenceValue;
	private SimpleFrame smashHeroInfoPanelWrapper;
	private TextureFrame rankIconBackdrop;
	private StringFrame rankInfoPanelIconValue;
	private StringFrame rankInfoPanelIconLevel;
	private StringFrame foodInfoPanelIconValue;

	private final StringBuilder recycleStringBuilder = new StringBuilder();
	private MultiSelectionIconListener multiSelectClickListener;
	private MultiSelectionIconListener cargoClickListener;
	private float frontQueueIconWidth;
	private float selectedUnitIconScale = 1.0f;
	private int shownActiveUnit;
	private int shownLayoutVersion = -1;
	private ClickablePortrait clickablePortrait;
	private StringFrame smashBuffStatusBar;
	/** The "Status:" label is only drawn in front of buff icons, and only for a single non-building unit. */
	private boolean buffStatusAllowed;
	private final CommandCardActivationReceiverPreviewCallback activationReceiverPreviewCallback = new CommandCardActivationReceiverPreviewCallback();

	final MeleeUI ui;

	MeleeUIInfoPanel(final MeleeUI ui) {
		this.ui = ui;
	}

	void createIconBackdrops() {
		this.damageBackdrops = new InfoPanelIconBackdrops(CAttackType.values(), this.ui.rootFrame, "Damage", "");
		this.defenseBackdrops = new InfoPanelIconBackdrops(CDefenseType.values(), this.ui.rootFrame, "Armor", "");
		this.damageBackdropsNeutral = new InfoPanelIconBackdrops(CAttackType.values(), this.ui.rootFrame, "Damage",
				"Neutral");
		this.defenseBackdropsNeutral = new InfoPanelIconBackdrops(CDefenseType.values(), this.ui.rootFrame, "Armor",
				"Neutral");
	}

	void createPortrait() {
		// Create the unit portrait stuff
		this.ui.portrait = new Portrait(this.ui.war3MapViewer, this.ui.portraitScene);
		this.ui.rootFrame.createSimpleFrame("UnitPortrait", this.ui.consoleUI, 0);
		final SimpleFrame unitPortraitModel = (SimpleFrame) this.ui.rootFrame.getFrameByName("UnitPortraitModel", 0);
		this.clickablePortrait = new ClickablePortrait("SmashClickablePortrait", unitPortraitModel);
		this.clickablePortrait.setSetAllPoints(true);
		unitPortraitModel.add(this.clickablePortrait);

		this.ui.unitLifeText = (StringFrame) this.ui.rootFrame.getFrameByName("UnitPortraitHitPointText", 0);
		this.ui.unitManaText = (StringFrame) this.ui.rootFrame.getFrameByName("UnitPortraitManaPointText", 0);
	}

	void createInfoPanel() {
		final float infoPanelUnitDetailWidth = GameUI.convertY(this.ui.uiViewport, 0.180f);
		final float infoPanelUnitDetailHeight = GameUI.convertY(this.ui.uiViewport, 0.120f);
		this.ui.smashSimpleInfoPanel = (SimpleFrame) this.ui.rootFrame.createSimpleFrame("SmashSimpleInfoPanel",
				this.ui.rootFrame, 0);
		this.ui.smashSimpleInfoPanel
				.addAnchor(new AnchorDefinition(FramePoint.BOTTOM, 0, GameUI.convertY(this.ui.uiViewport, 0.0f)));
		this.ui.smashSimpleInfoPanel.setWidth(infoPanelUnitDetailWidth);
		this.ui.smashSimpleInfoPanel.setHeight(infoPanelUnitDetailHeight);

		// Create Simple Info Unit Detail
		this.ui.simpleInfoPanelUnitDetail = (SimpleFrame) this.ui.rootFrame.createSimpleFrame(
				"SimpleInfoPanelUnitDetail", this.ui.smashSimpleInfoPanel, 0);
		this.ui.simpleNameValue = (StringFrame) this.ui.rootFrame.getFrameByName("SimpleNameValue", 0);
		this.ui.simpleClassValue = (StringFrame) this.ui.rootFrame.getFrameByName("SimpleClassValue", 0);
		this.ui.simpleBuildingActionLabel = (StringFrame) this.ui.rootFrame.getFrameByName("SimpleBuildingActionLabel",
				0);
		this.ui.simpleBuildTimeIndicator = (SimpleStatusBarFrame) this.ui.rootFrame
				.getFrameByName("SimpleBuildTimeIndicator", 0);
		final TextureFrame simpleBuildTimeIndicatorBar = this.ui.simpleBuildTimeIndicator.getBarFrame();
		simpleBuildTimeIndicatorBar.setTexture("SimpleBuildTimeIndicator", this.ui.rootFrame);
		final TextureFrame simpleBuildTimeIndicatorBorder = this.ui.simpleBuildTimeIndicator.getBorderFrame();
		simpleBuildTimeIndicatorBorder.setTexture("SimpleBuildTimeIndicatorBorder", this.ui.rootFrame);
		final float buildTimeIndicatorWidth = GameUI.convertX(this.ui.uiViewport, 0.10538f);
		final float buildTimeIndicatorHeight = GameUI.convertY(this.ui.uiViewport, 0.0103f);
		this.ui.simpleBuildTimeIndicator.setWidth(buildTimeIndicatorWidth);
		this.ui.simpleBuildTimeIndicator.setHeight(buildTimeIndicatorHeight);

		this.ui.simpleHeroLevelBar = (SimpleStatusBarFrame) this.ui.rootFrame.getFrameByName("SimpleHeroLevelBar", 0);
		final TextureFrame simpleHeroLevelBarBar = this.ui.simpleHeroLevelBar.getBarFrame();
		simpleHeroLevelBarBar.setTexture("SimpleXpBarConsole", this.ui.rootFrame);
		simpleHeroLevelBarBar.setColor(new Color(138f / 255f, 0, 131f / 255f, 1f));
		final TextureFrame simpleHeroLevelBarBorder = this.ui.simpleHeroLevelBar.getBorderFrame();
		simpleHeroLevelBarBorder.setTexture("SimpleXpBarBorder", this.ui.rootFrame);
		this.ui.simpleHeroLevelBar.setWidth(infoPanelUnitDetailWidth);
 this.ui.simpleProgressIndicator = (SimpleStatusBarFrame) this.ui.rootFrame.getFrameByName("SimpleProgressIndicator",
		0);
		final TextureFrame simpleProgressIndicatorBar = this.ui.simpleProgressIndicator.getBarFrame();
		simpleProgressIndicatorBar.setTexture("SimpleProgressBarConsole", this.ui.rootFrame);
		simpleProgressIndicatorBar.setColor(new Color(0.254902f, 0.509804f, 0.823529f, 1f));
		// 0.823529, 0.509804, 0.254902
		final TextureFrame simpleProgressIndicatorBorder = this.ui.simpleProgressIndicator.getBorderFrame();
		simpleProgressIndicatorBorder.setTexture("SimpleProgressBarBorder", this.ui.rootFrame);
		this.ui.simpleProgressIndicator.setWidth(infoPanelUnitDetailWidth);
		this.ui.simpleProgressIndicator.setVisible(false);
 this.smashBuffStatusBar = this.ui.rootFrame.createStringFrame("SmashBuffStatusBar", this.ui.simpleInfoPanelUnitDetail,
		Color.WHITE, TextJustify.LEFT, TextJustify.MIDDLE, 0.01f);
		this.ui.rootFrame.remove(this.smashBuffStatusBar);
		this.ui.simpleInfoPanelUnitDetail.add(this.smashBuffStatusBar);
		this.smashBuffStatusBar.setHeight(GameUI.convertY(this.ui.uiViewport, 0.01f));
		this.smashBuffStatusBar.addAnchor(new AnchorDefinition(FramePoint.BOTTOMLEFT,
				GameUI.convertX(this.ui.uiViewport, 0.03f), GameUI.convertY(this.ui.uiViewport, 0.012f)));
		this.smashBuffStatusBar.setWidth(GameUI.convertX(this.ui.uiViewport, 0.035f));
		this.ui.rootFrame.setDecoratedText(this.smashBuffStatusBar, "COLON_STATUS");
		UIFrame buffBarPreviousFrame = this.smashBuffStatusBar;
		for (int i = 0; i < MeleeUI.BUFF_DISPLAY_MAX; i++) {
			final BuffBarIcon buffBarIcon = new BuffBarIcon("SmashBuffStatusBarIcon" + i,
					this.ui.simpleInfoPanelUnitDetail);
			buffBarIcon.setWidth(GameUI.convertX(this.ui.uiViewport, 0.015f));
			buffBarIcon.setHeight(GameUI.convertY(this.ui.uiViewport, 0.015f));
			final TextureFrame barIconTex = new TextureFrame("SmashBuffStatusBarIcon" + i + "Texture", buffBarIcon,
					false, TextureFrame.DEFAULT_TEX_COORDS);
			barIconTex.setSetAllPoints(true);
			buffBarIcon.setIconFrame(barIconTex);
			this.ui.simpleInfoPanelUnitDetail.add(buffBarIcon);
//			this.rootFrame.add(buffBarIcon);
			buffBarIcon.addSetPoint(new SetPoint(FramePoint.LEFT, buffBarPreviousFrame, FramePoint.RIGHT,
					GameUI.convertX(this.ui.uiViewport, 0.001f), 0));
			buffBarPreviousFrame = buffBarIcon;
			this.ui.buffBarIcons[i] = buffBarIcon;
		}

		// Create Simple Info Panel Building Detail
		this.ui.simpleInfoPanelBuildingDetail = this.ui.rootFrame.createSimpleFrame("SimpleInfoPanelBuildingDetail",
				this.ui.smashSimpleInfoPanel, 0);
		this.ui.simpleBuildingNameValue = (StringFrame) this.ui.rootFrame.getFrameByName("SimpleBuildingNameValue", 0);
		this.simpleBuildingDescriptionValue = (StringFrame) this.ui.rootFrame
				.getFrameByName("SimpleBuildingDescriptionValue", 0);
		this.ui.simpleBuildingBuildingActionLabel = (StringFrame) this.ui.rootFrame
				.getFrameByName("SimpleBuildingActionLabel", 0);
		this.ui.simpleBuildingBuildTimeIndicator = (SimpleStatusBarFrame) this.ui.rootFrame
				.getFrameByName("SimpleBuildTimeIndicator", 0);
		final TextureFrame simpleBuildingBuildTimeIndicatorBar = this.ui.simpleBuildingBuildTimeIndicator.getBarFrame();
		simpleBuildingBuildTimeIndicatorBar.setTexture("SimpleBuildTimeIndicator", this.ui.rootFrame);
		final TextureFrame simpleBuildingBuildTimeIndicatorBorder = this.ui.simpleBuildingBuildTimeIndicator
				.getBorderFrame();
		simpleBuildingBuildTimeIndicatorBorder.setTexture("SimpleBuildTimeIndicatorBorder", this.ui.rootFrame);
		this.ui.simpleBuildingBuildTimeIndicator.setWidth(buildTimeIndicatorWidth);
		this.ui.simpleBuildingBuildTimeIndicator.setHeight(buildTimeIndicatorHeight);
		this.ui.simpleInfoPanelBuildingDetail.setVisible(false);
		this.simpleBuildQueueBackdrop = (TextureFrame) this.ui.rootFrame.getFrameByName("SimpleBuildQueueBackdrop", 0);
		this.simpleBuildQueueBackdrop.setWidth(infoPanelUnitDetailWidth);
		this.simpleBuildQueueBackdrop.setHeight(infoPanelUnitDetailWidth * 0.5f);
		this.simpleBuildQueueBackdrop.setVisible(false);

		// Create Simple Info Panel Item Detail
		this.ui.simpleInfoPanelItemDetail = this.ui.rootFrame.createSimpleFrame("SimpleInfoPanelItemDetail",
				this.ui.smashSimpleInfoPanel, 0);
		this.ui.simpleItemNameValue = (StringFrame) this.ui.rootFrame.getFrameByName("SimpleItemNameValue", 0);
		this.ui.simpleItemDescriptionValue = (StringFrame) this.ui.rootFrame
				.getFrameByName("SimpleItemDescriptionValue", 0);
		this.ui.simpleInfoPanelItemDetail.setVisible(false);

		// Create Simple Info Panel Destructable Detail
		this.ui.simpleInfoPanelDestructableDetail = this.ui.rootFrame.createSimpleFrame(
				"SimpleInfoPanelDestructableDetail", this.ui.smashSimpleInfoPanel, 0);
		this.ui.simpleDestructableNameValue = (StringFrame) this.ui.rootFrame
				.getFrameByName("SimpleDestructableNameValue", 0);
		this.ui.simpleInfoPanelDestructableDetail.setVisible(false);

		this.ui.queueIconFrames[0] = new QueueIcon("SmashBuildQueueIcon0", this.ui.smashSimpleInfoPanel, this.ui, 0);
		final TextureFrame queueIconFrameBackdrop0 = new TextureFrame("SmashBuildQueueIcon0Backdrop",
				this.ui.queueIconFrames[0], false, new Vector4Definition(0, 1, 0, 1));
		queueIconFrameBackdrop0
				.addSetPoint(new SetPoint(FramePoint.CENTER, this.ui.queueIconFrames[0], FramePoint.CENTER, 0, 0));
		this.ui.queueIconFrames[0].set(queueIconFrameBackdrop0);
		this.ui.queueIconFrames[0]
				.addSetPoint(new SetPoint(FramePoint.CENTER, this.ui.smashSimpleInfoPanel, FramePoint.BOTTOMLEFT,
						(infoPanelUnitDetailWidth * (15 + 19f)) / 256, (infoPanelUnitDetailWidth * (66 + 19f)) / 256));
		this.frontQueueIconWidth = (infoPanelUnitDetailWidth * 38) / 256;
		this.ui.queueIconFrames[0].setWidth(this.frontQueueIconWidth);
		this.ui.queueIconFrames[0].setHeight(this.frontQueueIconWidth);
		queueIconFrameBackdrop0.setWidth(this.frontQueueIconWidth);
		queueIconFrameBackdrop0.setHeight(this.frontQueueIconWidth);
		this.ui.smashSimpleInfoPanel.add(this.ui.queueIconFrames[0]);

		for (int i = 1; i < this.ui.queueIconFrames.length; i++) {
			this.ui.queueIconFrames[i] = new QueueIcon("SmashBuildQueueIcon" + i, this.ui.smashSimpleInfoPanel, this.ui,
					i);
			final TextureFrame queueIconFrameBackdrop = new TextureFrame("SmashBuildQueueIcon" + i + "Backdrop",
					this.ui.queueIconFrames[i], false, new Vector4Definition(0, 1, 0, 1));
			this.ui.queueIconFrames[i].set(queueIconFrameBackdrop);
			queueIconFrameBackdrop
					.addSetPoint(new SetPoint(FramePoint.CENTER, this.ui.queueIconFrames[i], FramePoint.CENTER, 0, 0));
			this.ui.queueIconFrames[i].addSetPoint(new SetPoint(FramePoint.CENTER, this.ui.smashSimpleInfoPanel,
					FramePoint.BOTTOMLEFT, (infoPanelUnitDetailWidth * (13 + 14.5f + (40 * (i - 1)))) / 256,
					(infoPanelUnitDetailWidth * (24 + 14.5f)) / 256));
			final float queueIconWidth = (infoPanelUnitDetailWidth * 29) / 256;
			this.ui.queueIconFrames[i].setWidth(queueIconWidth);
			this.ui.queueIconFrames[i].setHeight(queueIconWidth);
			queueIconFrameBackdrop.setWidth(queueIconWidth);
			queueIconFrameBackdrop.setHeight(queueIconWidth);
			this.ui.smashSimpleInfoPanel.add(this.ui.queueIconFrames[i]);
		}
		this.ui.selectWorkerInsideFrame = new QueueIcon("SmashBuildQueueWorkerIcon", this.ui.smashSimpleInfoPanel,
				this.ui, 1);
		final TextureFrame selectWorkerInsideIconFrameBackdrop = new TextureFrame("SmashBuildQueueWorkerIconBackdrop",
				this.ui.selectWorkerInsideFrame, false, new Vector4Definition(0, 1, 0, 1));
		this.ui.selectWorkerInsideFrame.set(selectWorkerInsideIconFrameBackdrop);
		selectWorkerInsideIconFrameBackdrop
				.addSetPoint(new SetPoint(FramePoint.CENTER, this.ui.selectWorkerInsideFrame, FramePoint.CENTER, 0, 0));
		this.ui.selectWorkerInsideFrame
				.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.queueIconFrames[1], FramePoint.TOPLEFT, 0, 0));
		this.ui.selectWorkerInsideFrame.setWidth(this.frontQueueIconWidth);
		this.ui.selectWorkerInsideFrame.setHeight(this.frontQueueIconWidth);
		selectWorkerInsideIconFrameBackdrop.setWidth(this.frontQueueIconWidth);
		selectWorkerInsideIconFrameBackdrop.setHeight(this.frontQueueIconWidth);
		this.ui.smashSimpleInfoPanel.add(this.ui.selectWorkerInsideFrame);

		final int halfSelectionMaxSize = this.ui.selectedUnitFrames.length / 3;
		for (int i = 0; i < this.ui.selectedUnitFrames.length; i++) {
			{
				final FilterModeTextureFrame selectedSubgroupHighlightBackdrop = new FilterModeTextureFrame(
						"SmashMultiSelectUnitIconHighlightBackdrop", this.ui.smashSimpleInfoPanel, true,
						new Vector4Definition(0, 1, 0, 1));
				selectedSubgroupHighlightBackdrop.setFilterMode(FilterMode.ADDITIVE);
				this.ui.selectedUnitHighlightBackdrop[i] = selectedSubgroupHighlightBackdrop;
				selectedSubgroupHighlightBackdrop.setTexture("SelectedSubgroupHighlight", this.ui.rootFrame);
				selectedSubgroupHighlightBackdrop.setWidth(this.frontQueueIconWidth * 1.37f);
				selectedSubgroupHighlightBackdrop.setHeight(this.frontQueueIconWidth * 1.75f);
				selectedSubgroupHighlightBackdrop.setColor(1.0f, 1.0f, 0.0f, 1.0f);
				this.ui.smashSimpleInfoPanel.add(selectedSubgroupHighlightBackdrop);
				selectedSubgroupHighlightBackdrop
						.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.smashSimpleInfoPanel, FramePoint.TOPLEFT,
								((-this.frontQueueIconWidth * .37f) / 2) + (this.frontQueueIconWidth * .10f)
										+ (this.frontQueueIconWidth * 1.10f * (i % halfSelectionMaxSize)),
								(((this.frontQueueIconWidth * .37f) / 2) + (this.frontQueueIconWidth * -.75f))
										- (this.frontQueueIconWidth * 1.5f * (i / halfSelectionMaxSize))));
			}
			{
				final TextureFrame cargoBackdrop = new TextureFrame("SmashCargoBackdrop", this.ui.smashSimpleInfoPanel,
						true, new Vector4Definition(0, 1, 0, 1));
				cargoBackdrop.setVisible(false);
				this.cargoBackdrop[i] = cargoBackdrop;
				cargoBackdrop.setTexture("CargoBackdrop", this.ui.rootFrame);
				cargoBackdrop.setWidth(this.frontQueueIconWidth * 1.37f);
				cargoBackdrop.setHeight(this.frontQueueIconWidth * 1.75f);
				cargoBackdrop.setColor(1.0f, 1.0f, 0.0f, 1.0f);
				this.ui.smashSimpleInfoPanel.add(cargoBackdrop);
				cargoBackdrop
						.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.smashSimpleInfoPanel, FramePoint.TOPLEFT,
								((-this.frontQueueIconWidth * .37f) / 2) + (this.frontQueueIconWidth * .0f)
										+ (this.frontQueueIconWidth * 1.20f * (i % halfSelectionMaxSize)),
								(((this.frontQueueIconWidth * .37f) / 2) + (this.frontQueueIconWidth * -.75f))
										- (this.frontQueueIconWidth * 1.5f * (i / halfSelectionMaxSize))));
			}
		}
		this.cargoClickListener = new MultiSelectionIconListener() {
			@Override
			public void multiSelectIconRelease(final int index) {

			}

			@Override
			public void multiSelectIconPress(final int index) {

			}

			@Override
			public void multiSelectIconClicked(final int index) {
				if (MeleeUIInfoPanel.this.ui.selectedUnit != null) {
					final CUnit simulationUnit = MeleeUIInfoPanel.this.ui.selectedUnit.getSimulationUnit();
					final CAbilityCargoHold cargoData = simulationUnit.getCargoData();
					if (cargoData != null) {
						if ((index >= 0) && (index < cargoData.getCargoCount())) {
							final CUnit unitInside = cargoData.getUnit(index);
							final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE.reset();
							final CSimulation simulation = MeleeUIInfoPanel.this.ui.war3MapViewer.simulation;
							cargoData.checkCanUse(simulation, simulationUnit,
									MeleeUIInfoPanel.this.ui.localPlayer.getId(), OrderIds.unload, false,
									activationReceiver);
							if (activationReceiver.isOk()) {
								final CWidgetAbilityTargetCheckReceiver targetCheckReceiver = CWidgetAbilityTargetCheckReceiver.INSTANCE;
								cargoData.checkCanTarget(simulation, simulationUnit,
										MeleeUIInfoPanel.this.ui.localPlayer.getId(), OrderIds.unload, false,
										unitInside, targetCheckReceiver.reset());
								if (targetCheckReceiver.getTarget() != null) {
									final CPlayer player = simulation.getPlayer(simulationUnit.getPlayerIndex());
									MeleeUIInfoPanel.this.ui.unitOrderListener.issueTargetOrder(
											simulationUnit.getHandleId(), cargoData.getHandleId(), OrderIds.unload,
											targetCheckReceiver.getTarget().getHandleId(), false);
								}
							}
						}
					}
				}
			}
		};
		this.multiSelectClickListener = new MultiSelectionIconListener() {
			@Override
			public void multiSelectIconClicked(final int index) {
				if (index < MeleeUIInfoPanel.this.ui.selectedUnits.size()) {
					final RenderUnit clickUnit = MeleeUIInfoPanel.this.ui.selectedUnits.get(index);
					if (MeleeUIInfoPanel.this.ui.activeCommand != null) {
						MeleeUIInfoPanel.this.ui.commands.useActiveCommandOnUnit(MeleeUIInput.isShiftDown(), clickUnit);
					}
					else if (!MeleeUIInfoPanel.this.ui.war3MapViewer.scriptUiState.isSelectionEnabled()) {
						return;
					}
					else if (clickUnit == MeleeUIInfoPanel.this.ui.selectedUnit) {
						final List<RenderWidget> newSelection = Arrays.asList(MeleeUIInfoPanel.this.ui.selectedUnit);
						MeleeUIInfoPanel.this.ui.input.selectWidgets(newSelection);
						MeleeUIInfoPanel.this.ui.war3MapViewer.doSelectUnit(newSelection);
					}
					else {
						MeleeUIInfoPanel.this.ui.input.selectUnit(clickUnit);
					}
				}
			}

			@Override
			public void multiSelectIconPress(final int index) {
			}

			@Override
			public void multiSelectIconRelease(final int index) {
			}
		};
		for (int i = 0; i < this.ui.selectedUnitFrames.length; i++) {
			this.ui.selectedUnitFrames[i] = new MultiSelectionIcon("SmashMultiSelectUnitIcon",
					this.ui.smashSimpleInfoPanel, this.multiSelectClickListener, i);
			final TextureFrame multiSelectUnitIconFrameBackdrop = new TextureFrame("SmashMultiSelectUnitIconBackdrop",
					this.ui.selectedUnitFrames[i], false, new Vector4Definition(0, 1, 0, 1));

			final SimpleStatusBarFrame hpBarFrame = new SimpleStatusBarFrame(
					"SmashMultiSelectHpBar" + this.ui.hpBarFrameIndex, this.ui.smashSimpleInfoPanel, true, true, 3.0f);
			hpBarFrame.getBarFrame().setTexture("SimpleHpBarConsole", this.ui.rootFrame);
			hpBarFrame.getBorderFrame().setTexture("Textures\\Black32.blp", this.ui.rootFrame);
			hpBarFrame.setWidth(this.frontQueueIconWidth);
			hpBarFrame.setHeight(this.frontQueueIconWidth * MultiSelectionIcon.HP_BAR_HEIGHT_RATIO);
			hpBarFrame.addSetPoint(new SetPoint(FramePoint.TOP, multiSelectUnitIconFrameBackdrop, FramePoint.BOTTOM, 0,
					-this.frontQueueIconWidth * MultiSelectionIcon.HP_BAR_SPACING_RATIO));
 final SimpleStatusBarFrame manaBarFrame = new SimpleStatusBarFrame("SmashMultiSelectManaBar" + this.ui.hpBarFrameIndex,
		this.ui.smashSimpleInfoPanel, true, true, 3.0f);
			manaBarFrame.getBarFrame().setTexture("SimpleManaBarConsole", this.ui.rootFrame);
			manaBarFrame.getBorderFrame().setTexture("Textures\\Black32.blp", this.ui.rootFrame);
			manaBarFrame.setWidth(this.frontQueueIconWidth);
			manaBarFrame.setHeight(this.frontQueueIconWidth * MultiSelectionIcon.HP_BAR_HEIGHT_RATIO);
			manaBarFrame.addSetPoint(new SetPoint(FramePoint.TOP, hpBarFrame, FramePoint.BOTTOM, 0,
					-this.frontQueueIconWidth * MultiSelectionIcon.HP_BAR_SPACING_RATIO));
			manaBarFrame.getBarFrame().setColor(0f, 0f, 1f, 1f);

			this.ui.selectedUnitFrames[i].set(multiSelectUnitIconFrameBackdrop, hpBarFrame, manaBarFrame);
			multiSelectUnitIconFrameBackdrop.addSetPoint(new SetPoint(FramePoint.CENTER, this.ui.selectedUnitFrames[i],
					FramePoint.CENTER, 0, 0));
			this.ui.selectedUnitFrames[i]
					.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.smashSimpleInfoPanel, FramePoint.TOPLEFT,
							(this.frontQueueIconWidth * .10f)
									+ (this.frontQueueIconWidth * 1.10f * (i % halfSelectionMaxSize)),
							(this.frontQueueIconWidth * -.75f)
									- (this.frontQueueIconWidth * 1.5f * (i / halfSelectionMaxSize))));
			this.ui.selectedUnitFrames[i].setWidth(this.frontQueueIconWidth);
			this.ui.selectedUnitFrames[i].setHeight(this.frontQueueIconWidth);
			multiSelectUnitIconFrameBackdrop.setWidth(this.frontQueueIconWidth);
			multiSelectUnitIconFrameBackdrop.setHeight(this.frontQueueIconWidth);
			this.ui.smashSimpleInfoPanel.add(this.ui.selectedUnitFrames[i]);

			this.ui.selectedUnitFrames[i].setVisible(false);
		}
		for (int i = 0; i < this.cargoUnitFrames.length; i++) {
			this.cargoUnitFrames[i] = new MultiSelectionIcon("SmashMultiSelectUnitIcon", this.ui.smashSimpleInfoPanel,
					this.cargoClickListener, i);
			this.cargoUnitFrames[i].setVisible(false);
			final TextureFrame cargoUnitIconFrameBackdrop = new TextureFrame("SmashCargoUnitIconBackdrop",
					this.cargoUnitFrames[i], false, new Vector4Definition(0, 1, 0, 1));

			final SimpleStatusBarFrame hpBarFrame = new SimpleStatusBarFrame(
					"SmashMultiSelectHpBar" + this.ui.hpBarFrameIndex, this.ui.smashSimpleInfoPanel, true, true, 3.0f);
			hpBarFrame.getBarFrame().setTexture("SimpleHpBarConsole", this.ui.rootFrame);
			hpBarFrame.getBorderFrame().setTexture("Textures\\Black32.blp", this.ui.rootFrame);
			hpBarFrame.setWidth(this.frontQueueIconWidth);
			hpBarFrame.setHeight(this.frontQueueIconWidth * MultiSelectionIcon.HP_BAR_HEIGHT_RATIO);
			hpBarFrame.addSetPoint(new SetPoint(FramePoint.TOP, cargoUnitIconFrameBackdrop, FramePoint.BOTTOM, 0,
					-this.frontQueueIconWidth * MultiSelectionIcon.HP_BAR_SPACING_RATIO));
 final SimpleStatusBarFrame manaBarFrame = new SimpleStatusBarFrame("SmashMultiSelectManaBar" + this.ui.hpBarFrameIndex,
		this.ui.smashSimpleInfoPanel, true, true, 3.0f);
			manaBarFrame.getBarFrame().setTexture("SimpleManaBarConsole", this.ui.rootFrame);
			manaBarFrame.getBorderFrame().setTexture("Textures\\Black32.blp", this.ui.rootFrame);
			manaBarFrame.setWidth(this.frontQueueIconWidth);
			manaBarFrame.setHeight(this.frontQueueIconWidth * MultiSelectionIcon.HP_BAR_HEIGHT_RATIO);
			manaBarFrame.addSetPoint(new SetPoint(FramePoint.TOP, hpBarFrame, FramePoint.BOTTOM, 0,
					-this.frontQueueIconWidth * MultiSelectionIcon.HP_BAR_SPACING_RATIO));
			manaBarFrame.getBarFrame().setColor(0f, 0f, 1f, 1f);

			this.cargoUnitFrames[i].set(cargoUnitIconFrameBackdrop, hpBarFrame, manaBarFrame);
			cargoUnitIconFrameBackdrop
					.addSetPoint(new SetPoint(FramePoint.CENTER, this.cargoUnitFrames[i], FramePoint.CENTER, 0, 0));
			this.cargoUnitFrames[i]
					.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.smashSimpleInfoPanel, FramePoint.TOPLEFT,
							(this.frontQueueIconWidth * .0f)
									+ (this.frontQueueIconWidth * 1.20f * (i % halfSelectionMaxSize)),
							(this.frontQueueIconWidth * -.75f)
									- (this.frontQueueIconWidth * 1.5f * (i / halfSelectionMaxSize))));
			this.cargoUnitFrames[i].setWidth(this.frontQueueIconWidth);
			this.cargoUnitFrames[i].setHeight(this.frontQueueIconWidth);
			cargoUnitIconFrameBackdrop.setWidth(this.frontQueueIconWidth);
			cargoUnitIconFrameBackdrop.setHeight(this.frontQueueIconWidth);
			this.ui.smashSimpleInfoPanel.add(this.cargoUnitFrames[i]);

			this.cargoUnitFrames[i].setVisible(false);
		}
	}

	void createInfoPanelIcons() {
		this.smashAttack1IconWrapper = (SimpleFrame) this.ui.rootFrame.createSimpleFrame(
				"SmashSimpleInfoPanelIconDamage", this.ui.simpleInfoPanelUnitDetail, 0);
		this.smashAttack1IconWrapper.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.simpleInfoPanelUnitDetail,
				FramePoint.TOPLEFT, 0, GameUI.convertY(this.ui.uiViewport, -0.040f)));
		this.smashAttack1IconWrapper.setWidth(GameUI.convertX(this.ui.uiViewport, 0.1f));
		this.smashAttack1IconWrapper.setHeight(GameUI.convertY(this.ui.uiViewport, 0.030125f));
		this.ui.attack1Icon = this.ui.rootFrame.createSimpleFrame("SimpleInfoPanelIconDamage",
				this.smashAttack1IconWrapper, 0);
		this.attack1IconBackdrop = (TextureFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconBackdrop", 0);
		this.attack1InfoPanelIconValue = (StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconValue", 0);
		this.ui.attack1InfoPanelIconLevel = (StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconLevel", 0);
 this.smashAttack2IconWrapper = (SimpleFrame) this.ui.rootFrame.createSimpleFrame("SmashSimpleInfoPanelIconDamage",
		this.ui.simpleInfoPanelUnitDetail, 0);
		this.smashAttack2IconWrapper
				.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.simpleInfoPanelUnitDetail, FramePoint.TOPLEFT,
						GameUI.convertX(this.ui.uiViewport, 0.1f), GameUI.convertY(this.ui.uiViewport, -0.03925f)));
		this.smashAttack2IconWrapper.setWidth(GameUI.convertX(this.ui.uiViewport, 0.1f));
		this.smashAttack2IconWrapper.setHeight(GameUI.convertY(this.ui.uiViewport, 0.030125f));
		this.ui.attack2Icon = this.ui.rootFrame.createSimpleFrame("SimpleInfoPanelIconDamage",
				this.smashAttack2IconWrapper, 1);
		this.attack2IconBackdrop = (TextureFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconBackdrop", 1);
		this.attack2InfoPanelIconValue = (StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconValue", 1);
		this.ui.attack2InfoPanelIconLevel = (StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconLevel", 1);

		this.smashArmorIconWrapper = (SimpleFrame) this.ui.rootFrame.createSimpleFrame("SmashSimpleInfoPanelIconArmor",
				this.ui.simpleInfoPanelUnitDetail, 0);
		this.smashArmorIconWrapper.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.simpleInfoPanelUnitDetail,
				FramePoint.TOPLEFT, GameUI.convertX(this.ui.uiViewport, 0f),
				GameUI.convertY(this.ui.uiViewport, -0.0705f)));
		this.smashArmorIconWrapper.setWidth(GameUI.convertX(this.ui.uiViewport, 0.1f));
		this.smashArmorIconWrapper.setHeight(GameUI.convertY(this.ui.uiViewport, 0.030125f));
		this.ui.armorIcon = this.ui.rootFrame.createSimpleFrame("SimpleInfoPanelIconArmor", this.smashArmorIconWrapper,
				0);
		this.armorIconBackdrop = (TextureFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconBackdrop", 0);
		this.armorInfoPanelIconValue = (StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconValue", 0);
		this.ui.armorInfoPanelIconLevel = (StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconLevel", 0);
 this.smashHeroInfoPanelWrapper = (SimpleFrame) this.ui.rootFrame.createSimpleFrame("SmashSimpleInfoPanelIconHero",
		this.ui.simpleInfoPanelUnitDetail, 0);
		this.smashHeroInfoPanelWrapper.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.simpleInfoPanelUnitDetail,
				FramePoint.TOPLEFT, GameUI.convertX(this.ui.uiViewport, 0.1f),
				GameUI.convertY(this.ui.uiViewport, -0.037f)));
		this.smashHeroInfoPanelWrapper.setWidth(GameUI.convertX(this.ui.uiViewport, 0.1f));
		this.smashHeroInfoPanelWrapper.setHeight(GameUI.convertY(this.ui.uiViewport, 0.0625f));
		this.ui.heroInfoPanel = this.ui.rootFrame.createSimpleFrame("SimpleInfoPanelIconHero",
				this.smashHeroInfoPanelWrapper, 0);
		this.primaryAttributeIcon = (TextureFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconHeroIcon", 0);
		this.strengthValue = (StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconHeroStrengthValue", 0);
		this.agilityValue = (StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconHeroAgilityValue", 0);
		this.intelligenceValue = (StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconHeroIntellectValue", 0);

		// A caster's rank and a building's food take the second attack's place (1.29).
		final SimpleFrame rankWrapper = createRightColumnWrapper("SmashSimpleInfoPanelIconRank");
		this.ui.rankIcon = this.ui.rootFrame.createSimpleFrame("SimpleInfoPanelIconRank", rankWrapper, 0);
		this.rankIconBackdrop = (TextureFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconBackdrop", 0);
		this.rankInfoPanelIconValue = (StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconValue", 0);
		this.rankInfoPanelIconLevel = (StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconLevel", 0);
		final SimpleFrame foodWrapper = createRightColumnWrapper("SmashSimpleInfoPanelIconFood");
		this.ui.foodIcon = this.ui.rootFrame.createSimpleFrame("SimpleInfoPanelIconFood", foodWrapper, 0);
		this.foodInfoPanelIconValue = (StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconValue", 0);
		((StringFrame) this.ui.rootFrame.getFrameByName("InfoPanelIconLevel", 0)).setVisible(false);
		this.ui.rankIcon.setVisible(false);
		this.ui.foodIcon.setVisible(false);
	}

	private SimpleFrame createRightColumnWrapper(final String name) {
		final SimpleFrame wrapper = (SimpleFrame) this.ui.rootFrame.createSimpleFrame(name,
				this.ui.simpleInfoPanelUnitDetail, 0);
		wrapper.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.simpleInfoPanelUnitDetail, FramePoint.TOPLEFT,
				GameUI.convertX(this.ui.uiViewport, 0.1f), GameUI.convertY(this.ui.uiViewport, -0.03925f)));
		wrapper.setWidth(GameUI.convertX(this.ui.uiViewport, 0.1f));
		wrapper.setHeight(GameUI.convertY(this.ui.uiViewport, 0.030125f));
		return wrapper;
	}

	private void updateRankAndFoodIcons(final UnitDetailsModel unit) {
		if (unit.rankName != null) {
			this.ui.rankIcon.setVisible(true);
			if (unit.rankArt != null) {
				this.rankIconBackdrop.setTexture(unit.rankArt, this.ui.rootFrame);
			}
			this.ui.rootFrame.setText(this.rankInfoPanelIconValue, unit.rankName);
			this.ui.rootFrame.setText(this.rankInfoPanelIconLevel, Integer.toString(unit.rank));
			return;
		}
		if (unit.foodMade > 0) {
			this.ui.foodIcon.setVisible(true);
			this.ui.rootFrame.setText(this.foodInfoPanelIconValue, Integer.toString(unit.foodMade));
		}
	}

	void updateBuildTimeIndicators() {
		final UiFrameState state = this.ui.commands.frameState(false);
		final UnitDetailsModel d = state.active;
		if ((state.activeUnit == 0) || (d.unitHandle != state.activeUnit)) {
			return;
		}
		if ((state.activeUnit != this.shownActiveUnit) || (state.commandCard.layoutVersion != this.shownLayoutVersion)) {
			// A frame came with another unit or card than the panel shows (the selection leads the frame).
			reloadSelectedUnitUI(this.ui.selectedUnit);
		}
		if (this.ui.simpleBuildTimeIndicator.isVisible()) {
			this.ui.simpleBuildTimeIndicator.setValue(d.queueProgress);
		}
		if (this.ui.simpleBuildingBuildTimeIndicator.isVisible()) {
			this.ui.simpleBuildingBuildTimeIndicator.setValue(d.queueProgress);
		}
	}

	public void portraitTalk(final UnitSound us) {
		this.ui.portrait.talk(us, 0);
	}

	static final class Portrait {
		MdxComplexInstance modelInstance;
		private final PortraitCameraManager portraitCameraManager;
		private final Scene portraitScene;
		private final EnumSet<AnimationTokens.SecondaryTag> recycleSet = EnumSet
				.noneOf(AnimationTokens.SecondaryTag.class);
		private EnumSet<AnimationTokens.SecondaryTag> secondaryTags;
		private RenderWidget lastUnit;
		// If animationTargetDuration is 0, it will play the whole animation.
		private long portraitTargetDuration = 0;
		private long portraitCurrentDuration = 0;
		long faceLockTime = 0;

		public Portrait(final War3MapViewer war3MapViewer, final Scene portraitScene) {
			this.portraitScene = portraitScene;
			this.portraitCameraManager = new PortraitCameraManager();
			this.portraitCameraManager.setupCamera(this.portraitScene);
			this.portraitScene.camera.viewport(new Rectangle(100, 0, 6400, 48));
		}

		public void resetCinematicSequence() {
			this.secondaryTags = SequenceUtils.EMPTY;
			if (this.modelInstance != null) {
				this.portraitScene.removeInstance(this.modelInstance);
			}
			this.modelInstance = null;
			this.portraitCameraManager.setModelInstance(null, null);
			this.faceLockTime = 0;
		}

		public void update(final float dt) {
			this.portraitCurrentDuration += (dt * 1000);
			if (this.modelInstance != null) {
				if (this.portraitTargetDuration != 0) {
					if (this.portraitCurrentDuration < this.portraitTargetDuration) {
						this.modelInstance.sequenceLoopMode = SequenceLoopMode.ALWAYS_LOOP;
					}
					else {
						this.modelInstance.sequenceEnded = true;
						this.modelInstance.sequenceLoopMode = SequenceLoopMode.NEVER_LOOP;
						this.portraitTargetDuration = 0;
					}
				}

				if (this.modelInstance.sequenceEnded || (this.modelInstance.sequence == -1)) {
					this.recycleSet.clear();
					this.recycleSet.addAll(this.secondaryTags);
					if (SequenceUtils.randomSequence(this.modelInstance, PrimaryTag.PORTRAIT, this.recycleSet,
							true) == null) {
						SequenceUtils.randomSequence(this.modelInstance, PrimaryTag.STAND, this.recycleSet, true);
					}
				}
			}
			this.portraitCameraManager.updateCamera();
		}

		public boolean isResetNeeded() {
			return (this.faceLockTime != 0) && (this.portraitCurrentDuration > this.faceLockTime);
		}

		public void talk(final UnitSound us, final float extraDuration) {
			if ((this.faceLockTime > 0) && (this.portraitCurrentDuration <= this.faceLockTime)) {
				return;
			}
			innerTalk(us, extraDuration);
		}

		private void innerTalk(final UnitSound us, final float extraDuration) {
			// TODO we somehow called talk from null by clicking a unit right at the same
			// time it died, so I do a null check here until I study that case further.
			if (this.modelInstance != null) {
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
				if (SequenceUtils.randomSequence(this.modelInstance, PrimaryTag.PORTRAIT, this.recycleSet,
						true) == null) {
					SequenceUtils.randomSequence(this.modelInstance, PrimaryTag.STAND, this.recycleSet, true);
				}
			}
		}

		public void setSelectedUnit(final RenderUnit unit) {
			if ((this.faceLockTime > 0) && (this.portraitCurrentDuration <= this.faceLockTime)) {
				return;
			}
			if (this.lastUnit != unit) {
				this.lastUnit = unit;
				if (unit == null) {
					this.secondaryTags = SequenceUtils.EMPTY;
					if (this.modelInstance != null) {
						this.portraitScene.removeInstance(this.modelInstance);
					}
					this.modelInstance = null;
					this.portraitCameraManager.setModelInstance(null, null);
				}
				else {
					this.secondaryTags = unit.getSecondaryAnimationTags();
					final MdxModel portraitModel = unit.getPortraitModel();
					if (portraitModel != null) {
						if (this.modelInstance != null) {
							this.portraitScene.removeInstance(this.modelInstance);
						}
						this.modelInstance = (MdxComplexInstance) portraitModel.addInstance();
						this.portraitCameraManager.setModelInstance(this.modelInstance, portraitModel);
						this.modelInstance.setBlendTime(portraitModel.blendTime);
						this.modelInstance.setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP);
						this.modelInstance.setScene(this.portraitScene);
						this.modelInstance.setVertexColor(unit.instance.vertexColor);
						this.modelInstance.setTeamColor(unit.playerIndex);
						showNewModel();
					}
				}
			}
		}

		public void setSelectedItem(final RenderItem unit) {
			if ((this.faceLockTime > 0) && (this.portraitCurrentDuration <= this.faceLockTime)) {
				return;
			}
			if (this.lastUnit != unit) {
				this.lastUnit = unit;
				this.secondaryTags = SequenceUtils.EMPTY;
				if (unit == null) {
					if (this.modelInstance != null) {
						this.portraitScene.removeInstance(this.modelInstance);
					}
					this.modelInstance = null;
					this.portraitCameraManager.setModelInstance(null, null);
				}
				else {
					final MdxModel portraitModel = unit.portraitModel;
					if (portraitModel != null) {
						if (this.modelInstance != null) {
							this.portraitScene.removeInstance(this.modelInstance);
						}
						this.modelInstance = (MdxComplexInstance) portraitModel.addInstance();
						this.portraitCameraManager.setModelInstance(this.modelInstance, portraitModel);
						this.modelInstance.setBlendTime(portraitModel.blendTime);
						this.modelInstance.setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP);
						this.modelInstance.setScene(this.portraitScene);
						this.modelInstance.setVertexColor(unit.instance.vertexColor);
						showNewModel();
					}
				}
			}
		}

		public void setSelectedDestructable(final RenderDestructable unit) {
			if ((this.faceLockTime > 0) && (this.portraitCurrentDuration <= this.faceLockTime)) {
				return;
			}
			if (this.lastUnit != unit) {
				this.lastUnit = unit;
				this.secondaryTags = SequenceUtils.EMPTY;
				if (unit == null) {
					if (this.modelInstance != null) {
						this.portraitScene.removeInstance(this.modelInstance);
					}
					this.modelInstance = null;
					this.portraitCameraManager.setModelInstance(null, null);
				}
				else {
					final MdxModel portraitModel = unit.getPortraitModel();
					if (portraitModel != null) {
						if (this.modelInstance != null) {
							this.portraitScene.removeInstance(this.modelInstance);
						}
						this.modelInstance = (MdxComplexInstance) portraitModel.addInstance();
						this.portraitCameraManager.setModelInstance(this.modelInstance, portraitModel);
						this.modelInstance.setBlendTime(portraitModel.blendTime);
						this.modelInstance.setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP);
						this.modelInstance.setScene(this.portraitScene);
						showNewModel();
					}
				}
			}
		}

		public void setCinematicTalkingHead(final MdxModel portraitModel, final int teamColorIndex,
				final EnumSet<SecondaryTag> secondaryTags, final float faceLockTime, final UnitSound us,
				final float extraDuration) {
			this.faceLockTime = (long) (1000 * faceLockTime);
			if (this.modelInstance != null) {
				this.portraitScene.removeInstance(this.modelInstance);
			}
			this.secondaryTags = secondaryTags;
			if (portraitModel != null) {
				this.modelInstance = (MdxComplexInstance) portraitModel.addInstance();
				this.portraitCameraManager.setModelInstance(this.modelInstance, portraitModel);
				this.modelInstance.setBlendTime(portraitModel.blendTime);
				this.modelInstance.setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP);
				this.modelInstance.setScene(this.portraitScene);
				this.modelInstance.setTeamColor(teamColorIndex);
			}
			this.lastUnit = null;
			innerTalk(us, extraDuration);
			showNewModel();
		}

		/**
		 * The model and its portrait camera have to change in the same frame: the interface updates the portrait
		 * before it switches it, so the new model would be drawn once with the camera of the old one and, with no
		 * sequence yet, without the camera's animation.
		 */
		private void showNewModel() {
			update(0);
		}
	}

	private void layoutSelectedUnitFrames(final int selectedCount) {
		// Up to 12 units use two rows of six; larger groups shrink to three rows of eight so the whole
		// group stays inside the info panel.
		final boolean compact = selectedCount > 12;
		final int columns = compact ? 8 : 6;
		final float scale = compact ? 0.75f : 1.0f;
		this.selectedUnitIconScale = scale;
		final float iconWidth = this.frontQueueIconWidth * scale;
		for (int i = 0; i < this.ui.selectedUnitFrames.length; i++) {
			final float x = (iconWidth * .10f) + (iconWidth * 1.10f * (i % columns));
			final float y = (iconWidth * -.75f) - (iconWidth * 1.5f * (i / columns));
			final MultiSelectionIcon icon = this.ui.selectedUnitFrames[i];
			icon.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.smashSimpleInfoPanel, FramePoint.TOPLEFT, x, y));
			icon.setWidth(iconWidth);
			icon.setHeight(iconWidth);
			final UIFrame highlight = this.ui.selectedUnitHighlightBackdrop[i];
			highlight.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.smashSimpleInfoPanel, FramePoint.TOPLEFT,
					x - ((iconWidth * .37f) / 2), y + ((iconWidth * .37f) / 2)));
			highlight.setWidth(iconWidth * 1.37f);
		}
	}

	void reloadSelectedUnitUI(final RenderUnit unit) {
		if (unit == null) {
			return;
		}
		final UiFrameState state = this.ui.commands.frameState(true);
		final UnitDetailsModel d = state.active;
		if ((state.activeUnit == 0) || (d.unitHandle != state.activeUnit)
				|| (state.activeUnit != unit.getSimulationUnit().getHandleId())) {
			return;
		}
		this.shownActiveUnit = state.activeUnit;
		this.shownLayoutVersion = state.commandCard.layoutVersion;
		final CUnit simulationUnit = unit.getSimulationUnit();
		final float lifeRatioRemaining = d.hp / d.maxHp;
		this.ui.rootFrame.setText(this.ui.unitLifeText,
				FastNumberFormat.formatWholeNumber(d.hp) + " / " + FastNumberFormat.formatWholeNumber(d.maxHp));
		this.ui.unitLifeText.setColor(new Color(Math.min(1.0f, 2.0f - (lifeRatioRemaining * 2)),
				Math.min(1.0f, lifeRatioRemaining * 2), 0, 1.0f));
		final int maximumMana = d.maxManaInt;
		if (maximumMana > 0) {
			this.ui.rootFrame.setText(this.ui.unitManaText,
					FastNumberFormat.formatWholeNumber(d.mana) + " / " + maximumMana);
		}
		else {
			this.ui.rootFrame.setText(this.ui.unitManaText, "");
		}
		final boolean multiSelect = state.selectedCount > 1;
		this.ui.commands.repositionRallyPoint(simulationUnit);
		this.ui.commands.repositionWaypointFlags(simulationUnit);
		if (!multiSelect) {
			for (int i = 0; i < this.ui.selectedUnitFrames.length; i++) {
				this.ui.selectedUnitFrames[i].setVisible(false);
				this.ui.selectedUnitHighlightBackdrop[i].setVisible(false);
			}
		}
		for (int i = 0; i < this.cargoUnitFrames.length; i++) {
			this.cargoUnitFrames[i].setVisible(false);
			this.cargoBackdrop[i].setVisible(false);
		}
		this.ui.simpleProgressIndicator.setVisible(false);
		this.ui.rankIcon.setVisible(false);
		this.ui.foodIcon.setVisible(false);
		if (d.queueShown && !multiSelect) {
			for (int i = 0; i < this.ui.queueIconFrames.length; i++) {
				if (i >= d.queueCount) {
					this.ui.queueIconFrames[i].setVisible(false);
				}
				else {
					this.ui.queueIconFrames[i].setVisible(true);
					this.ui.queueIconFrames[i].setTexture(this.ui.commands.icon(d.queueIconPaths[i]));
					this.ui.queueIconFrames[i].setToolTip(d.queueTips[i]);
					this.ui.queueIconFrames[i].setUberTip(d.queueUberTips[i]);
				}
			}
			this.ui.simpleInfoPanelBuildingDetail.setVisible(!multiSelect);
			this.ui.simpleInfoPanelItemDetail.setVisible(false);
			this.ui.simpleInfoPanelDestructableDetail.setVisible(false);
			this.simpleBuildQueueBackdrop.setVisible(true);
			this.ui.simpleInfoPanelUnitDetail.setVisible(false);
			this.ui.rootFrame.setText(this.ui.simpleBuildingNameValue, d.typeName);
			this.ui.rootFrame.setText(this.simpleBuildingDescriptionValue, "");

			this.ui.simpleBuildingBuildTimeIndicator.setVisible(true);
			this.ui.simpleBuildTimeIndicator.setVisible(false);
			this.ui.simpleHeroLevelBar.setVisible(false);
			final int firstKind = d.queueKinds[0];
			if (firstKind == UnitDetailsModel.QUEUE_UNIT) {
				this.ui.rootFrame.setText(this.ui.simpleBuildingBuildingActionLabel,
						this.ui.rootFrame.getTemplates().getDecoratedString("TRAINING"));
			}
			else if (firstKind == UnitDetailsModel.QUEUE_SACRIFICE) {
				this.ui.rootFrame.setText(this.ui.simpleBuildingBuildingActionLabel,
						this.ui.rootFrame.getTemplates().getDecoratedString("TRAINING"));
				this.simpleBuildQueueBackdrop.setVisible(false);
			}
			else if (firstKind == UnitDetailsModel.QUEUE_HERO_REVIVE) {
				this.ui.rootFrame.setText(this.ui.simpleBuildingBuildingActionLabel,
						this.ui.rootFrame.getTemplates().getDecoratedString("REVIVING"));
			}
			else {
				this.ui.rootFrame.setText(this.ui.simpleBuildingBuildingActionLabel,
						this.ui.rootFrame.getTemplates().getDecoratedString("RESEARCHING"));
			}
			this.ui.attack1Icon.setVisible(false);
			this.ui.attack2Icon.setVisible(false);
			this.ui.armorIcon.setVisible(false);
			this.ui.heroInfoPanel.setVisible(false);
			this.ui.selectWorkerInsideFrame.setVisible(false);
			this.smashBuffStatusBar.setVisible(false);
		}
		else if (multiSelect) {
			for (int i = 0; i < this.ui.queueIconFrames.length; i++) {
				this.ui.queueIconFrames[i].setVisible(false);
			}
			layoutSelectedUnitFrames(state.selectedCount);
			for (int i = 0; i < this.ui.selectedUnitFrames.length; i++) {
				final boolean useIcon = i < state.selectedCount;
				this.ui.selectedUnitFrames[i].setVisible(useIcon);
				final SelectedUnitModel selectedUnit = state.selectedDetails[i];
				final boolean focused = useIcon && (selectedUnit.typeId == d.typeId);
				this.ui.selectedUnitHighlightBackdrop[i].setVisible(focused);
				if (useIcon) {
					this.ui.selectedUnitFrames[i].setTexture(this.ui.commands.icon(selectedUnit.iconPath));
					this.ui.selectedUnitFrames[i].setToolTip(selectedUnit.tip);
					this.ui.selectedUnitFrames[i].setUberTip(selectedUnit.uberTip);
					this.ui.selectedUnitFrames[i].setLifeRatioRemaining(selectedUnit.life / selectedUnit.maxLife);
					final boolean useManaBar = selectedUnit.maxMana > 0;
					this.ui.selectedUnitFrames[i].setManaBarVisible(useManaBar);
					if (useManaBar) {
						this.ui.selectedUnitFrames[i].setManaRatioRemaining(selectedUnit.mana / selectedUnit.maxMana);
					}
					if (focused) {
						this.ui.selectedUnitFrames[i].showFocused(this.ui.rootFrame, this.ui.uiViewport);
						if (useManaBar) {
							this.ui.selectedUnitHighlightBackdrop[i]
									.setHeight(this.frontQueueIconWidth * this.selectedUnitIconScale * 1.75f);
						}
						else {
							this.ui.selectedUnitHighlightBackdrop[i]
									.setHeight(this.frontQueueIconWidth * this.selectedUnitIconScale * 1.55f);
						}
						this.ui.selectedUnitHighlightBackdrop[i].positionBounds(this.ui.rootFrame, this.ui.uiViewport);
					}
					else {
						this.ui.selectedUnitFrames[i].showUnFocused(this.ui.rootFrame, this.ui.uiViewport);
					}
				}
			}
			this.ui.simpleInfoPanelBuildingDetail.setVisible(false);
			this.ui.simpleInfoPanelItemDetail.setVisible(false);
			this.ui.simpleInfoPanelDestructableDetail.setVisible(false);
			this.ui.simpleInfoPanelUnitDetail.setVisible(false);
			this.ui.simpleBuildingBuildTimeIndicator.setVisible(false);
			this.ui.simpleBuildTimeIndicator.setVisible(false);
			this.ui.simpleHeroLevelBar.setVisible(false);
			this.ui.attack1Icon.setVisible(false);
			this.ui.attack2Icon.setVisible(false);
			this.ui.armorIcon.setVisible(false);
			this.ui.heroInfoPanel.setVisible(false);
			this.ui.selectWorkerInsideFrame.setVisible(false);
			this.smashBuffStatusBar.setVisible(false);
		}
		else {
			for (final QueueIcon queueIconFrame : this.ui.queueIconFrames) {
				queueIconFrame.setVisible(false);
			}
			this.ui.simpleInfoPanelBuildingDetail.setVisible(false);
			this.ui.simpleInfoPanelItemDetail.setVisible(false);
			this.ui.simpleInfoPanelDestructableDetail.setVisible(false);
			this.ui.simpleInfoPanelUnitDetail.setVisible(!multiSelect);
			final boolean constructing = d.constructing;
			this.buffStatusAllowed = !multiSelect && !d.building && !constructing;
			this.smashBuffStatusBar.setVisible(false);
			if ((d.cargoCount > 0) && !multiSelect && !constructing) {
				this.ui.attack1Icon.setVisible(false);
				this.ui.attack2Icon.setVisible(false);
				this.ui.armorIcon.setVisible(false);
				this.ui.heroInfoPanel.setVisible(false);
				this.ui.simpleBuildTimeIndicator.setVisible(false);
				this.ui.simpleBuildingBuildTimeIndicator.setVisible(false);
				this.ui.selectWorkerInsideFrame.setVisible(false);

				this.ui.simpleClassValue.setVisible(!d.building);
				this.ui.rootFrame.setText(this.ui.simpleNameValue, d.typeName);
				this.ui.rootFrame.setText(this.ui.simpleClassValue, "");
				this.ui.simpleHeroLevelBar.setVisible(false);

				for (int i = 0; i < this.cargoUnitFrames.length; i++) {
					final boolean cargoCapacityPresent = i < d.cargoCapacity;
					final boolean cargoUnitPresent = i < d.cargoCount;
					this.cargoUnitFrames[i].setVisible(cargoUnitPresent);
					this.cargoBackdrop[i].setVisible(cargoCapacityPresent);
					if (cargoUnitPresent) {
						this.cargoUnitFrames[i].setTexture(this.ui.commands.icon(d.cargoIconPaths[i]));
						this.cargoUnitFrames[i].setToolTip(d.cargoTips[i]);
						this.cargoUnitFrames[i].setUberTip(d.cargoUberTips[i]);
						this.cargoUnitFrames[i].setLifeRatioRemaining(d.cargoLife[i]);
						this.cargoUnitFrames[i].showFocused(this.ui.rootFrame, this.ui.uiViewport);
						this.cargoUnitFrames[i].setManaBarVisible(d.cargoHasMana[i]);
						if (d.cargoHasMana[i]) {
							this.cargoUnitFrames[i].setManaRatioRemaining(d.cargoMana[i]);
						}
					}
					else {
						this.cargoUnitFrames[i].setTexture(null);
					}
				}
			}
			else {
				for (int i = 0; i < this.cargoUnitFrames.length; i++) {
					this.cargoUnitFrames[i].setVisible(false);
				}
				final String unitTypeName = d.typeName;

				final boolean anyAttacks = d.attackCount > 0;
				final UIFrame localArmorIcon = this.ui.armorIcon;
				final TextureFrame localArmorIconBackdrop = this.armorIconBackdrop;
				final StringFrame localArmorInfoPanelIconValue = this.armorInfoPanelIconValue;
				final StringFrame localArmorInfoPanelIconLevel = this.ui.armorInfoPanelIconLevel;
				if (anyAttacks && !constructing) {
					final boolean weaponUpgradeLevelVisible = d.weaponUpgradeLevel >= 0;
					final InfoPanelIconBackdrops damageBackdrops = weaponUpgradeLevelVisible ? this.damageBackdrops
							: this.damageBackdropsNeutral;
					this.ui.attack1Icon.setVisible(true);
					this.attack1IconBackdrop.setTexture(damageBackdrops.getTexture(CAttackType.values()[d.attackTypes[0]]));
					this.ui.rootFrame.setText(this.attack1InfoPanelIconValue, d.attackTexts[0]);
					this.ui.attack1InfoPanelIconLevel.setVisible(weaponUpgradeLevelVisible);
					if (weaponUpgradeLevelVisible) {
						this.ui.rootFrame.setText(this.ui.attack1InfoPanelIconLevel,
								Integer.toString(d.weaponUpgradeLevel));
					}
					// A hero's attribute panel takes the second attack's place, so a hero shows only its first
					// attack (1.29).
					if ((d.attackCount > 1) && !d.hero) {
						this.ui.attack2Icon.setVisible(d.attackShown[1]);
						this.attack2IconBackdrop.setTexture(damageBackdrops.getTexture(CAttackType.values()[d.attackTypes[1]]));
						this.ui.rootFrame.setText(this.attack2InfoPanelIconValue, d.attackTexts[1]);
						this.ui.attack2InfoPanelIconLevel.setVisible(weaponUpgradeLevelVisible);
						if (weaponUpgradeLevelVisible) {
							this.ui.rootFrame.setText(this.ui.attack2InfoPanelIconLevel,
									Integer.toString(d.weaponUpgradeLevel));
						}
					}
					else {
						this.ui.attack2Icon.setVisible(false);
					}
 this.smashArmorIconWrapper.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.simpleInfoPanelUnitDetail,
		FramePoint.TOPLEFT, GameUI.convertX(this.ui.uiViewport, 0f), GameUI.convertY(this.ui.uiViewport, -0.0705f)));
					this.smashArmorIconWrapper.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
					this.ui.armorIcon.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
				}
				else {
					this.ui.attack1Icon.setVisible(false);
					this.ui.attack2Icon.setVisible(false);
 this.smashArmorIconWrapper.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.simpleInfoPanelUnitDetail,
		FramePoint.TOPLEFT, GameUI.convertX(this.ui.uiViewport, 0f), GameUI.convertY(this.ui.uiViewport, -0.040f)));
					this.smashArmorIconWrapper.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
					this.ui.armorIcon.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
				}

				final boolean hero = d.hero;
				this.ui.heroInfoPanel.setVisible(hero);
				if (hero) {
					String iconKey;
					switch (d.primaryAttribute) {
					case UnitDetailsModel.ATTRIBUTE_AGILITY:
						iconKey = "InfoPanelIconHeroIconAGI";
						break;
					case UnitDetailsModel.ATTRIBUTE_INTELLIGENCE:
						iconKey = "InfoPanelIconHeroIconINT";
						break;
					default:
					case UnitDetailsModel.ATTRIBUTE_STRENGTH:
						iconKey = "InfoPanelIconHeroIconSTR";
						break;
					}
					this.primaryAttributeIcon.setTexture(iconKey, this.ui.rootFrame);

					this.ui.rootFrame.setText(this.strengthValue, d.strengthText);
					this.ui.rootFrame.setText(this.agilityValue, d.agilityText);
					this.ui.rootFrame.setText(this.intelligenceValue, d.intelligenceText);
					final String infopanelLevelClass = this.ui.rootFrame.getTemplates()
							.getDecoratedString("INFOPANEL_LEVEL_CLASS").replace("%u", "%d"); // :(
					this.ui.simpleClassValue.setVisible(true);
					this.ui.rootFrame.setText(this.ui.simpleClassValue,
							String.format(Locale.ROOT, infopanelLevelClass, d.level, unitTypeName));
					this.ui.rootFrame.setText(this.ui.simpleNameValue, d.name);
					// An illusion hero shows its timed life bar in place of the experience bar.
					this.ui.simpleHeroLevelBar.setVisible(!d.illusion);
					this.ui.simpleHeroLevelBar.setValue(d.heroXp / (float) d.heroXpToNextLevel);
				}
				else {
					this.ui.simpleClassValue.setVisible(!d.building);
					this.ui.rootFrame.setText(this.ui.simpleNameValue, unitTypeName);
					if (d.classText != null) {
						this.ui.rootFrame.setText(this.ui.simpleClassValue, d.classText);
					}
					else {
						this.ui.rootFrame.setText(this.ui.simpleClassValue, "");
					}
					this.ui.simpleHeroLevelBar.setVisible(false);
				}

				localArmorIcon.setVisible(!constructing);
				if (!constructing && !hero && !this.ui.attack2Icon.isVisible()) {
					updateRankAndFoodIcons(d);
				}
				this.ui.simpleBuildTimeIndicator.setVisible(constructing);
				this.ui.simpleBuildingBuildTimeIndicator.setVisible(false);
				if (constructing) {
					this.ui.rootFrame.setText(this.ui.simpleBuildingActionLabel,
							this.ui.rootFrame.getTemplates().getDecoratedString("CONSTRUCTING"));
					this.ui.queueIconFrames[0].setVisible(true);
					this.ui.queueIconFrames[0].setTexture(this.ui.commands.icon(d.queueIconPaths[0]));
					this.ui.queueIconFrames[0].setToolTip(d.queueTips[0]);
					this.ui.queueIconFrames[0].setUberTip(d.queueUberTips[0]);
					if (d.workerIconPath != null) {
						this.ui.selectWorkerInsideFrame.setVisible(true);
						this.ui.selectWorkerInsideFrame.setTexture(this.ui.commands.icon(d.workerIconPath));
					}
					else {
						this.ui.selectWorkerInsideFrame.setVisible(false);
					}
				}
				else {
					this.ui.rootFrame.setText(this.ui.simpleBuildingActionLabel, "");
					this.ui.selectWorkerInsideFrame.setVisible(false);
				}
				final boolean armorUpgradeLevelVisible = d.armorUpgradeLevel >= 0;
				final Texture defenseTexture = (armorUpgradeLevelVisible ? this.defenseBackdrops
						: this.defenseBackdropsNeutral).getTexture(CDefenseType.values()[d.armorType]);
				if (defenseTexture == null) {
					throw new RuntimeException(CDefenseType.values()[d.armorType] + " can't find texture!");
				}
				localArmorIconBackdrop.setTexture(defenseTexture);

				this.ui.rootFrame.setText(localArmorInfoPanelIconValue, d.armorText);
				localArmorInfoPanelIconLevel.setVisible(armorUpgradeLevelVisible);
				if (armorUpgradeLevelVisible) {
					this.ui.rootFrame.setText(localArmorInfoPanelIconLevel, Integer.toString(d.armorUpgradeLevel));
				}
			}
		}
		this.ui.inventoryCover.setVisible(!d.inventoryShown);
		this.ui.inventoryBarFrame.setVisible(d.inventoryShown);
		if (d.inventoryShown) {
			final boolean nineItems = this.ui.war3MapViewer.getGameData().isUseNineItemInventory();
			int index = 0;
			for (int i = 0; i < MeleeUI.INVENTORY_HEIGHT; i++) {
				for (int j = 0; j < this.ui.inventoryWidth; j++) {
					final CommandCardIcon inventoryIcon = this.ui.inventoryIcons[i][j];
					if (d.itemTypeIds[index] != 0) {
						// TODO: below we set menu=false, this is bad, item should be based on item abil
						final boolean activelyUsed = d.itemActive[index];
						this.recycleStringBuilder.setLength(0);
						if (d.itemPawnable[index]) {
							this.recycleStringBuilder.append(
									MeleeUICommands.touchItemHint(this.ui.rootFrame,
									this.ui.rootFrame.getTemplates().getDecoratedString("ITEM_PAWN_TOOLTIP")));
							this.recycleStringBuilder.append("|n");
						}
						if (activelyUsed) {
							this.recycleStringBuilder.append(
									MeleeUICommands.touchItemHint(this.ui.rootFrame,
									this.ui.rootFrame.getTemplates().getDecoratedString("ITEM_USE_TOOLTIP")));
							this.recycleStringBuilder.append("|n");
						}
						this.recycleStringBuilder.append(d.itemUberTips[index]);
						inventoryIcon.setCommandButtonData(
								d.inventoryEnabled ? this.ui.commands.icon(d.itemIconPaths[index])
										: this.ui.commands.iconDisabled(d.itemIconPaths[index]),
								0, activelyUsed ? OrderIds.itemUseFirst(nineItems) + index : 0, index + 1,
								activelyUsed, false, false, d.itemNames[index], this.recycleStringBuilder.toString(),
								'\0', d.itemPawnGold[index], d.itemPawnLumber[index], 0, 0, false,
								d.itemCooldown[index], d.itemCooldownMax[index],
								d.itemCharges[index] > 0 ? d.itemCharges[index] : -1);
					}
					else {
						if (index >= d.inventoryCapacity) {
							inventoryIcon.setCommandButtonData(this.ui.consoleInventoryNoCapacityTexture, 0, 0, 0,
									false, false, false, null, null, '\0', 0, 0, 0, 0, false, 0, 0, -1);
						}
						else {
							if (this.ui.draggingItem != null) {
								inventoryIcon.setCommandButtonData(null, 0, 0, index + 1, true, false, false, null,
										null, '\0', 0, 0, 0, 0, false, 0, 0, -1);
							}
							else {
								inventoryIcon.clear();
							}
						}
					}
					index++;
				}
			}
		}
		this.ui.commands.clearAndRepopulateCommandCard();
		updateBuildTimeIndicators();
	}

	void hideBuffStatus() {
		this.smashBuffStatusBar.setVisible(false);
	}

	/** The "Status:" label is drawn in front of the buff icons only. */
	void showBuffStatus() {
		this.smashBuffStatusBar.setVisible(this.buffStatusAllowed);
	}

	/** The buff bar is shown from the frame by {@link MeleeUICommands#showBuffs}; nothing calls the listener now. */
	public void buff(final Texture icon, final int level, final String tip, final String uberTip,
			final boolean positive, final boolean leveled) {
	}

	public void timedLifeBar(final int level, final String toolTip, final float durationRemaining,
			final float durationMax) {
		this.ui.simpleProgressIndicatorDurationRemaining = durationRemaining;
		this.ui.simpleProgressIndicatorDurationMax = durationMax;
		this.ui.simpleProgressIndicator.setVisible(true);
		this.ui.simpleProgressIndicator.setValue(durationRemaining / durationMax);
		this.ui.simpleClassValue.setVisible(true);
		this.ui.rootFrame.setText(this.ui.simpleClassValue, toolTip);
	}

	/** The unit portrait's place in the console, in interface coordinates. */
	Rectangle portraitBounds(final Rectangle out) {
		final Rectangle moved = this.ui.originFrames.scriptPortraitBounds();
		return moved != null ? out.set(moved) : defaultPortraitBounds(out);
	}

	Rectangle defaultPortraitBounds(final Rectangle out) {
		return out.set(422 * this.ui.widthRatioCorrection, 57 * this.ui.heightRatioCorrection,
				167 * this.ui.widthRatioCorrection, 170 * this.ui.heightRatioCorrection);
	}

	boolean isPortraitShown() {
		return (this.ui.portrait != null) && this.ui.portrait.portraitScene.show;
	}

	public void positionPortrait() {
		if (this.ui.cinematicPanel.isVisible() && !(this.ui.cinematics.isInterfaceFading() && this.ui.userUIEnabled)) {
			this.ui.portrait.portraitScene.show = false;
			// A hidden scene still clears its viewport, which no letterbox covers in Reforged cinematics.
			this.ui.portrait.portraitScene.camera.viewport(this.ui.tempRect.set(0, 0, 0, 0));
			return;
		}
		else {
			this.ui.portrait.portraitScene.show = this.ui.originFrames.isPortraitScriptVisible();
			final Rectangle bounds = portraitBounds(this.portraitBoundsTemp);
			this.projectionTemp1.x = bounds.x;
			this.projectionTemp1.y = bounds.y;
			this.projectionTemp2.x = bounds.x + bounds.width;
			this.projectionTemp2.y = bounds.y + bounds.height;
			this.ui.uiViewport.project(this.projectionTemp1);
			this.ui.uiViewport.project(this.projectionTemp2);
		}

		// project already includes the viewport's screen offset (letterbox bars), adding it again shifts the portrait
		// out of its frame.
		this.ui.tempRect.x = this.projectionTemp1.x;
		this.ui.tempRect.y = this.projectionTemp1.y;
		this.ui.tempRect.width = this.projectionTemp2.x - this.projectionTemp1.x;
		this.ui.tempRect.height = this.projectionTemp2.y - this.projectionTemp1.y;
		this.ui.portrait.portraitScene.camera.viewport(this.ui.tempRect);
	}

	private static final class InfoPanelIconBackdrops {
		private final Texture[] damageBackdropTextures;

		public InfoPanelIconBackdrops(final CodeKeyType[] attackTypes, final GameUI gameUI, final String prefix,
				final String suffix) {
			this.damageBackdropTextures = new Texture[attackTypes.length];
			for (int index = 0; index < attackTypes.length; index++) {
				final CodeKeyType attackType = attackTypes[index];
				String skinLookupKey = "InfoPanelIcon" + prefix + attackType.getCodeKey() + suffix;
				if (!gameUI.hasSkinField(skinLookupKey) && (attackType == CAttackType.SPELLS)) {
					skinLookupKey = "InfoPanelIcon" + prefix + CAttackType.MAGIC.getCodeKey() + suffix;
				}
				final Texture suffixTexture = gameUI.loadTexture(gameUI.getSkinField(skinLookupKey),
						suffix.isEmpty());
				if (suffixTexture != null) {
					this.damageBackdropTextures[index] = suffixTexture;
				}
				else {
					skinLookupKey = "InfoPanelIcon" + prefix + attackType.getCodeKey();
					if (!gameUI.hasSkinField(skinLookupKey) && (attackType == CAttackType.SPELLS)) {
						skinLookupKey = "InfoPanelIcon" + prefix + CAttackType.MAGIC.getCodeKey();
					}
					this.damageBackdropTextures[index] = gameUI.loadTexture(gameUI.getSkinField(skinLookupKey));
				}
			}
		}

		public Texture getTexture(final CodeKeyType attackType) {
			if (attackType != null) {
				final int ordinal = attackType.ordinal();
				if ((ordinal >= 0) && (ordinal < this.damageBackdropTextures.length)) {
					return this.damageBackdropTextures[ordinal];
				}
			}
			return this.damageBackdropTextures[0];
		}
	}

	/** The state of the selected unit in the frame, null when the frame has none for it yet. */
	private UnitDetailsModel selectedDetails() {
		if (this.ui.selectedUnit == null) {
			return null;
		}
		final UiFrameState state = this.ui.commands.frameState(true);
		final UnitDetailsModel d = state.active;
		return (state.activeUnit == 0) || (d.unitHandle != state.activeUnit)
				|| (state.activeUnit != this.ui.selectedUnit.getSimulationUnit().getHandleId()) ? null : d;
	}

	public void lifeChanged() {
		final UnitDetailsModel d = selectedDetails();
		if (d == null) {
			return;
		}
		if (d.dead) {
			this.ui.runSimulationDriven(this::removeSubGroupHighlightSelectedUnitFromSelection);
		}
		else {
			final float lifeRatioRemaining = d.hp / d.maxHp;
			this.ui.rootFrame.setText(this.ui.unitLifeText,
					FastNumberFormat.formatWholeNumber(d.hp) + " / " + FastNumberFormat.formatWholeNumber(d.maxHp));
			this.ui.unitLifeText.setColor(new Color(Math.min(1.0f, 2.0f - (lifeRatioRemaining * 2)),
					Math.min(1.0f, lifeRatioRemaining * 2), 0, 1.0f));
		}
	}

	public void hideStateChanged() {
		final UnitDetailsModel d = selectedDetails();
		if (d == null) {
			return;
		}
		if (d.hidden || !d.visibleToLocal) {
			if (d.paused || !d.controlShared) {
				// use "not controllable or paused" as a proxy for whether to unselect a hidden
				// unit. this tries to mimic the behavior on War3 that workers inside buildings,
				// and units with 0 vision radius who walk into the fog of war, remain selected
				this.ui.runSimulationDriven(this::removeSubGroupHighlightSelectedUnitFromSelection);
			}

		}
	}

	private void removeSubGroupHighlightSelectedUnitFromSelection() {
		final RenderUnit preferredSelectionReplacement = this.ui.selectedUnit.getPreferredSelectionReplacement();
		final List<RenderWidget> newSelection;
		newSelection = new ArrayList<>(this.ui.selectedUnits);
		newSelection.remove(this.ui.selectedUnit);
		if (preferredSelectionReplacement != null) {
			newSelection.add(preferredSelectionReplacement);
		}
		this.ui.input.selectWidgets(newSelection);
		this.ui.war3MapViewer.doSelectUnit(newSelection);

		// clear active commands
		this.ui.activeCommandUnit = null;
		this.ui.activeCommand = null;
		this.ui.activeCommandOrderId = -1;
		if (this.ui.draggingItem != null) {
			this.ui.commands.setDraggingItem(null);
		}
	}

	public void manaChanged() {
		final UnitDetailsModel d = selectedDetails();
		if (d == null) {
			return;
		}
		if (d.maxManaInt > 0) {
			this.ui.rootFrame.setText(this.ui.unitManaText,
					FastNumberFormat.formatWholeNumber(d.mana) + " / " + d.maxManaInt);
		}
		else {
			this.ui.rootFrame.setText(this.ui.unitManaText, "");
		}
	}

	public void queueIconClicked(final int index) {
		final UnitDetailsModel d = selectedDetails();
		if (d == null) {
			return;
		}
		if (d.constructing) {
			switch (index) {
			case 0: {
				final CommandCardModel card = this.ui.commands.frameState(false).commandCard;
				for (int i = 0; i < card.buttonCount; i++) {
					final CommandButtonModel button = card.buttons[i];
					if ((button.id == OrderIds.cancel) && button.enabled && (button.abilityHandleId != 0)) {
						this.ui.unitOrderListener.issueImmediateOrder(d.unitHandle, button.abilityHandleId,
								OrderIds.cancel, false);
					}
				}
				break;
			}
			case 1: {
				final RenderUnit worker = this.ui.commands.renderUnitOf(d.workerHandle);
				if (worker != null) {
					final List<RenderWidget> unitList = Arrays.asList(worker);
					this.ui.war3MapViewer.doSelectUnit(unitList);
					this.ui.input.selectWidgets(unitList);
				}
				break;
			}
			}
		}
		else {
			this.ui.unitOrderListener.unitCancelTrainingItem(d.unitHandle, index);
		}
	}

	private final class ClickablePortrait extends AbstractClickableActionFrame implements ClickableFrame {
		public ClickablePortrait(final String name, final UIFrame parent) {
			super(name, parent);
		}

		@Override
		public void mouseEnter(final GameUI gameUI, final Viewport uiViewport) {
		}

		@Override
		public void mouseExit(final GameUI gameUI, final Viewport uiViewport) {
		}

		@Override
		public void mouseDragged(final GameUI rootFrame, final Viewport uiViewport, final float x, final float y) {
		}

		@Override
		public void mouseDown(final GameUI gameUI, final Viewport uiViewport) {
			if ((MeleeUIInfoPanel.this.ui.getCameraManager().getTargetControllerUnit() == null)
					&& (MeleeUIInfoPanel.this.ui.selectedUnit != null)) {
				MeleeUIInfoPanel.this.ui.getCameraManager().setTargetController(MeleeUIInfoPanel.this.ui.selectedUnit,
						0, 0, false);
			}
		}

		@Override
		public void mouseUp(final GameUI gameUI, final Viewport uiViewport) {
			MeleeUIInfoPanel.this.ui.getCameraManager().setTargetController(null, 0, 0, false);
		}

		@Override
		public void onClick(final int button) {
		}

		@Override
		protected void innerPositionBounds(final GameUI gameUI, final Viewport viewport) {
		}

		@Override
		protected void internalRender(final SpriteBatch batch, final BitmapFont baseFont,
				final GlyphLayout glyphLayout) {
		}

		@Override
		public String getSoundKey() {
			return SOUND_KEY_NONE;
		}
	}
}
