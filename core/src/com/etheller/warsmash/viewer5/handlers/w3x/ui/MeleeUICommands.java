package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Blending;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.TextJustify;
import com.etheller.warsmash.parsers.fdf.frames.FilterModeTextureFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.SingleStringFrame;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextureFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.RenderMathUtils;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.ViewerTextureRenderable;
import com.etheller.warsmash.viewer5.handlers.mdx.Attachment;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxNode;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.PathingFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.IconUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.OrderButtonUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidgetFilterFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityGenericDoNothing;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityView;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.COrderButton;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.AbstractCAbilityBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityBuildInProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityHumanBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityNagaBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityNeutralBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityNightElfBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityOrcBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityUndeadBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.combat.CAbilityColdArrows;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.GenericNoIconAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.GenericSingleIconActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.GenericSingleIconPassiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityReturnResources;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilityAwakenHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilityNeutralBuilding;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilitySellItems;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilitySellUnits;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.jass.CAbilityJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.nightelf.root.CAbilityRoot;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityQueue;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityRally;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityReviveHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.upgrade.CAbilityUpgrade;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissileLine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissileSplash;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CBuildingPathingType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.BuildOnBuildingIntersector;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CWidgetAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ExternStringMsgAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ExternStringMsgTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.CommandCardCommandListener;
import com.hiveworkshop.rms.parsers.mdlx.MdlxLayer.FilterMode;

/**
 * The command card and the inventory of the selected unit, the order chosen on them with its
 * cursor and targeting, and the rally point and waypoint indicators of the selection.
 */
final class MeleeUICommands {
	private MenuCursorState cursorState;
	private Color cursorColor;
	private ArgbImage cursorModelPathing;
	private PixmapTextureData cursorModelUnderneathPathingRedGreenPixmapTextureData;
	private CUnitType cursorBuildingUnitType;
	final CursorTargetSetupVisitor cursorTargetSetupVisitor = new CursorTargetSetupVisitor();
	final ActiveCommandUnitTargetFilter activeCommandUnitTargetFilter = new ActiveCommandUnitTargetFilter();
	private final RallyPositioningVisitor rallyPositioningVisitor = new RallyPositioningVisitor();
	private MdxModel waypointModel;
	private final List<MdxComplexInstance> waypointModelInstances = new ArrayList<>();
	private final ItemCommandCardCommandListener itemCommandCardCommandListener = new ItemCommandCardCommandListener();

	private final BuildOnBuildingIntersector buildOnBuildingIntersector = new BuildOnBuildingIntersector();

	final MeleeUI ui;

	MeleeUICommands(final MeleeUI ui) {
		this.ui = ui;
	}

	void createInventory() {
		this.ui.inventoryBarFrame = (SimpleFrame) this.ui.rootFrame.createSimpleFrame("SmashSimpleInventoryBar",
				this.ui.smashSimpleInfoPanel, 0);
		this.ui.inventoryBarFrame.setWidth(GameUI.convertX(this.ui.uiViewport, 0.079f));
		this.ui.inventoryBarFrame.setHeight(GameUI.convertY(this.ui.uiViewport, 0.115f));
		this.ui.inventoryBarFrame.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT, this.ui.consoleUI,
				FramePoint.BOTTOMLEFT, GameUI.convertX(this.ui.uiViewport, 0.591f),
				GameUI.convertY(this.ui.uiViewport, 0.0f)));

		if (GameUI.DEBUG) {
			final FilterModeTextureFrame placeholderPreview = new FilterModeTextureFrame(null,
					this.ui.inventoryBarFrame, false, null);
			placeholderPreview.setFilterMode(FilterMode.ADDALPHA);
			placeholderPreview.setTexture("ReplaceableTextures\\TeamColor\\TeamColor06.blp", this.ui.rootFrame);
			placeholderPreview.setSetAllPoints(true);
			this.ui.inventoryBarFrame.add(placeholderPreview);
		}

		int commandButtonIndex = 0;
		final BitmapFont inventoryNumberOverlayFont = this.ui.rootFrame
				.generateFont(MeleeUI.DEFAULT_INVENTORY_ICON_WIDTH * 0.25f);
		for (int j = 0; j < MeleeUI.INVENTORY_HEIGHT; j++) {
			for (int i = 0; i < this.ui.inventoryWidth; i++) {
				final CommandCardIcon commandCardIcon = new CommandCardIcon(
						"SmashInventoryButton_" + commandButtonIndex, this.ui.inventoryBarFrame,
						this.itemCommandCardCommandListener);
				this.ui.inventoryBarFrame.add(commandCardIcon);
				final TextureFrame iconFrame = new TextureFrame("SmashInventoryButton_" + commandButtonIndex + "_Icon",
						this.ui.rootFrame, false, null);
				final TextureFrame numberOverlayFrame = new TextureFrame(
						"SmashCommandButton_" + commandButtonIndex + "_NumberOverlay", this.ui.rootFrame, true, null);
				final SpriteFrame cooldownFrame = (SpriteFrame) this.ui.rootFrame.createFrameByType("SPRITE",
						"SmashInventoryButton_" + commandButtonIndex + "_Cooldown", this.ui.rootFrame, "", 0);
				commandCardIcon.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.inventoryBarFrame,
						FramePoint.TOPLEFT, GameUI.convertX(this.ui.uiViewport, 0.0037f + (0.04f * i)),
						GameUI.convertY(this.ui.uiViewport, -0.0021f - (0.03815f * j))));
				commandCardIcon.setWidth(GameUI.convertX(this.ui.uiViewport, MeleeUI.DEFAULT_INVENTORY_ICON_WIDTH));
				commandCardIcon.setHeight(GameUI.convertY(this.ui.uiViewport, MeleeUI.DEFAULT_INVENTORY_ICON_WIDTH));
				iconFrame.addSetPoint(new SetPoint(FramePoint.CENTER, commandCardIcon, FramePoint.CENTER, 0, 0));
				iconFrame.setWidth(GameUI.convertX(this.ui.uiViewport, MeleeUI.DEFAULT_INVENTORY_ICON_WIDTH));
				iconFrame.setHeight(GameUI.convertY(this.ui.uiViewport, MeleeUI.DEFAULT_INVENTORY_ICON_WIDTH));
				iconFrame.setTexture(ImageUtils.DEFAULT_ICON_PATH, this.ui.rootFrame);
				cooldownFrame.addSetPoint(new SetPoint(FramePoint.CENTER, commandCardIcon, FramePoint.CENTER, 0, 0));
				this.ui.rootFrame.setSpriteFrameModel(cooldownFrame,
						this.ui.rootFrame.getSkinField("CommandButtonCooldown"));
				cooldownFrame.setWidth(GameUI.convertX(this.ui.uiViewport, MeleeUI.DEFAULT_INVENTORY_ICON_WIDTH));
				cooldownFrame.setHeight(GameUI.convertY(this.ui.uiViewport, MeleeUI.DEFAULT_INVENTORY_ICON_WIDTH));
				cooldownFrame.setModelScale(
						MeleeUI.DEFAULT_INVENTORY_ICON_WIDTH / MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH);

				numberOverlayFrame.addSetPoint(
						new SetPoint(FramePoint.BOTTOMRIGHT, commandCardIcon, FramePoint.BOTTOMRIGHT, 0, 0));
				numberOverlayFrame.setWidth(GameUI.convertX(this.ui.uiViewport,
						MeleeUI.DEFAULT_INVENTORY_ICON_WIDTH) * 0.4f);
				numberOverlayFrame.setHeight(GameUI.convertY(this.ui.uiViewport,
						MeleeUI.DEFAULT_INVENTORY_ICON_WIDTH) * 0.4f);
				numberOverlayFrame.setTexture("CommandButtonNumberOverlay", this.ui.rootFrame);
				final SingleStringFrame numberOverlayStringFrame = new SingleStringFrame(
						"SmashCommandButton_NumberOverlayText", numberOverlayFrame, Color.WHITE, TextJustify.CENTER,
						TextJustify.BOTTOM, inventoryNumberOverlayFont);
				numberOverlayStringFrame.setSetAllPoints(true);

				commandCardIcon.set(iconFrame, null, cooldownFrame, null, numberOverlayFrame, numberOverlayStringFrame);
				this.ui.inventoryIcons[j][i] = commandCardIcon;
				commandCardIcon.clear();
				commandButtonIndex++;
			}
		}
		this.ui.inventoryTitleFrame = this.ui.rootFrame.createStringFrame("SmashInventoryText",
				this.ui.inventoryBarFrame, new Color(0xFCDE12FF), TextJustify.CENTER, TextJustify.MIDDLE, 0.0109f);
		this.ui.rootFrame.setText(this.ui.inventoryTitleFrame,
				this.ui.rootFrame.getTemplates().getDecoratedString("INVENTORY"));
		this.ui.inventoryTitleFrame
				.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.inventoryBarFrame, FramePoint.TOPLEFT,
						GameUI.convertX(this.ui.uiViewport, 0.004f), GameUI.convertY(this.ui.uiViewport, 0.0165625f)));
		this.ui.inventoryTitleFrame.setWidth(
				GameUI.convertX(this.ui.uiViewport, this.ui.war3MapViewer.getGameData().isUseNineItemInventory() ? 0.101f : 0.071f));
		this.ui.inventoryTitleFrame.setHeight(GameUI.convertX(this.ui.uiViewport, 0.01125f));
		this.ui.inventoryTitleFrame.setFontShadowColor(new Color(0f, 0f, 0f, 0.9f));
		this.ui.inventoryTitleFrame.setFontShadowOffsetX(GameUI.convertX(this.ui.uiViewport, 0.001f));
		this.ui.inventoryTitleFrame.setFontShadowOffsetY(GameUI.convertY(this.ui.uiViewport, -0.001f));
		this.ui.rootFrame.remove(this.ui.inventoryTitleFrame);
		this.ui.inventoryBarFrame.add(this.ui.inventoryTitleFrame);
		try {
			this.ui.consoleInventoryNoCapacityTexture = ImageUtils.getAnyExtensionTexture(this.ui.dataSource,
					this.ui.rootFrame.getSkinField("ConsoleInventoryNoCapacity"));
		}
		catch (final Exception exc) {
			Log.error(exc);
		}
		// A piece of the console under the info panel: the HD and DE covers are opaque where the unit display
		// reaches over them.
		this.ui.inventoryCover = this.ui.rootFrame.createSimpleFrame("SmashConsoleInventoryCover", this.ui.consoleUI, 0);
	}

	void createCommandCard() {
		int commandButtonIndex = 0;
		final BitmapFont commandCardNumberOverlayFont = this.ui.rootFrame
				.generateFont(MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH * 0.25f);
		this.ui.smashCommandButtons = (SimpleFrame) this.ui.rootFrame.createFrameByType("SIMPLEFRAME",
				"SmashCommandButtons", this.ui.rootFrame, "", 0);
		for (int j = 0; j < MeleeUI.COMMAND_CARD_HEIGHT; j++) {
			for (int i = 0; i < MeleeUI.COMMAND_CARD_WIDTH; i++) {
				final CommandCardIcon commandCardIcon = new CommandCardIcon("SmashCommandButton_" + commandButtonIndex,
						this.ui.rootFrame, this.ui);
				this.ui.smashCommandButtons.add(commandCardIcon);
				final TextureFrame iconFrame = new TextureFrame("SmashCommandButton_" + commandButtonIndex + "_Icon",
						this.ui.rootFrame, false, null);
				final FilterModeTextureFrame activeHighlightFrame = new FilterModeTextureFrame(
						"SmashCommandButton_" + commandButtonIndex + "_ActiveHighlight", this.ui.rootFrame, true, null);
				activeHighlightFrame.setFilterMode(FilterMode.ADDALPHA);
				final TextureFrame numberOverlayFrame = new TextureFrame(
						"SmashCommandButton_" + commandButtonIndex + "_NumberOverlay", this.ui.rootFrame, true, null);
				final SpriteFrame cooldownFrame = (SpriteFrame) this.ui.rootFrame.createFrameByType("SPRITE",
						"SmashCommandButton_" + commandButtonIndex + "_Cooldown", this.ui.rootFrame, "", 0);
				final SpriteFrame autocastFrame = (SpriteFrame) this.ui.rootFrame.createFrameByType("SPRITE",
						"SmashCommandButton_" + commandButtonIndex + "_Autocast", this.ui.rootFrame, "", 0);
				commandCardIcon.addAnchor(new AnchorDefinition(FramePoint.BOTTOMLEFT,
						GameUI.convertX(this.ui.uiViewport,
								0.6175f + (this.ui.war3MapViewer.getGameData().isUseNineItemInventory() ? 0.0405f : 0.0f) + (0.0434f * i)),
						GameUI.convertY(this.ui.uiViewport, 0.095f - (0.044f * j))));
				commandCardIcon.setWidth(GameUI.convertX(this.ui.uiViewport, MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH));
				commandCardIcon.setHeight(GameUI.convertY(this.ui.uiViewport, MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH));
				iconFrame.addSetPoint(new SetPoint(FramePoint.CENTER, commandCardIcon, FramePoint.CENTER, 0, 0));
				iconFrame.setWidth(GameUI.convertX(this.ui.uiViewport, MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH));
				iconFrame.setHeight(GameUI.convertY(this.ui.uiViewport, MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH));
				iconFrame.setTexture(ImageUtils.DEFAULT_ICON_PATH, this.ui.rootFrame);
				activeHighlightFrame
						.addSetPoint(new SetPoint(FramePoint.CENTER, commandCardIcon, FramePoint.CENTER, 0, 0));
				activeHighlightFrame.setWidth(GameUI.convertX(this.ui.uiViewport,
						MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH));
				activeHighlightFrame.setHeight(GameUI.convertY(this.ui.uiViewport,
						MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH));
				activeHighlightFrame.setTexture("CommandButtonActiveHighlight", this.ui.rootFrame);

				numberOverlayFrame.addSetPoint(
						new SetPoint(FramePoint.BOTTOMRIGHT, commandCardIcon, FramePoint.BOTTOMRIGHT, 0, 0));
				numberOverlayFrame.setWidth(GameUI.convertX(this.ui.uiViewport,
						MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH) * 0.4f);
				numberOverlayFrame.setHeight(GameUI.convertY(this.ui.uiViewport,
						MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH) * 0.4f);
				numberOverlayFrame.setTexture("CommandButtonNumberOverlay", this.ui.rootFrame);
				final SingleStringFrame numberOverlayStringFrame = new SingleStringFrame(
						"SmashCommandButton_NumberOverlayText", numberOverlayFrame, Color.WHITE, TextJustify.CENTER,
						TextJustify.BOTTOM, commandCardNumberOverlayFont);
//				numberOverlayStringFrame.addAnchor(new AnchorDefinition(FramePoint.CENTER, 0, 0));
				numberOverlayStringFrame.setSetAllPoints(true);
				cooldownFrame.addSetPoint(new SetPoint(FramePoint.CENTER, commandCardIcon, FramePoint.CENTER, 0, 0));
				this.ui.rootFrame.setSpriteFrameModel(cooldownFrame,
						this.ui.rootFrame.getSkinField("CommandButtonCooldown"));
				cooldownFrame.setWidth(GameUI.convertX(this.ui.uiViewport, MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH));
				cooldownFrame.setHeight(GameUI.convertY(this.ui.uiViewport, MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH));
				autocastFrame.addSetPoint(new SetPoint(FramePoint.CENTER, commandCardIcon, FramePoint.CENTER, 0, 0));
				this.ui.rootFrame.setSpriteFrameModel(autocastFrame,
						this.ui.rootFrame.getSkinField("CommandButtonAutocast"));
				autocastFrame.setWidth(GameUI.convertX(this.ui.uiViewport, MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH));
				autocastFrame.setHeight(GameUI.convertY(this.ui.uiViewport, MeleeUI.DEFAULT_COMMAND_CARD_ICON_WIDTH));
				commandCardIcon.set(iconFrame, activeHighlightFrame, cooldownFrame, autocastFrame, numberOverlayFrame,
						numberOverlayStringFrame);
				this.ui.commandCard[j][i] = commandCardIcon;
				commandCardIcon.clear();
				commandButtonIndex++;
			}
		}
	}

	void createRallyIndicators() {
		final MdxModel rallyModel = this.ui.war3MapViewer.loadModelMdx(
				this.ui.rootFrame.getSkinField("RallyIndicatorDst"));
		this.ui.rallyPointInstance = (MdxComplexInstance) rallyModel.addInstance();
		this.ui.rallyPointInstance.rotate(RenderUnit.tempQuat.setFromAxis(RenderMathUtils.VEC3_UNIT_Z,
				this.ui.war3MapViewer.simulation.getGameplayConstants().getBuildingAngle()));
		this.ui.rallyPointInstance.setSequenceLoopMode(SequenceLoopMode.ALWAYS_LOOP);
		SequenceUtils.randomStandSequence(this.ui.rallyPointInstance);
		this.ui.rallyPointInstance.hide();
		this.waypointModel = this.ui.war3MapViewer.loadModelMdx(this.ui.rootFrame.getSkinField("WaypointIndicator"));
	}

	public void onClick(final int abilityHandleId, final int orderId, final boolean rightClick) {
		if (this.ui.selectedUnit == null) {
			return;
		}
		if (orderId == 0) {
			return;
		}
		if (this.ui.draggingItem != null) {
			// A command button replaces the held item; otherwise the next ground click would send a drop-item
			// order carrying the new command's ability.
			setDraggingItem(null);
			this.ui.activeCommandUnit = null;
			this.ui.activeCommand = null;
			this.ui.activeCommandOrderId = -1;
		}
		// TODO not O(N)
		CAbilityView abilityToUse = null;
		for (final CAbility ability : this.ui.selectedUnit.getSimulationUnit().getAbilities()) {
			if (ability.getHandleId() == abilityHandleId) {
				abilityToUse = ability;
				break;
			}
		}
		if (abilityToUse != null) {
			abilityToUse.checkCanUse(this.ui.war3MapViewer.simulation, this.ui.selectedUnit.getSimulationUnit(),
					this.ui.localPlayer.getId(), orderId, false, this.ui.meleeUIAbilityActivationReceiver.reset(this.ui,
							this.ui.war3MapViewer.worldScene.audioContext, this.ui.selectedUnit));
			if (this.ui.meleeUIAbilityActivationReceiver.isUseOk()) {
				final BooleanAbilityTargetCheckReceiver<Void> noTargetReceiver = BooleanAbilityTargetCheckReceiver
						.<Void>getInstance().reset();
				abilityToUse.checkCanTargetNoTarget(this.ui.war3MapViewer.simulation,
						this.ui.selectedUnit.getSimulationUnit(), this.ui.localPlayer.getId(), orderId, false,
						noTargetReceiver);
				if (noTargetReceiver.isTargetable()) {
					final boolean shiftDown = MeleeUIInput.isShiftDown();
					this.ui.unitOrderListener.issueImmediateOrder(
							this.ui.selectedUnit.getSimulationUnit().getHandleId(), abilityHandleId, orderId,
							shiftDown);
					if (abilityToUse instanceof CAbilityHero) {
						if ((((CAbilityHero) abilityToUse).getSkillPoints() <= 1) && (orderId != OrderIds.skillmenu)) {
							// using up the last skill point, so close the menu
							// TODO this is kind of a stupid hack and should probably be improved later
							openMenu(0);
						}
					}
					// A hero's skills are learned by the hero shown on the card only.
					if ((this.ui.selectedUnits.size() > 1) && !(abilityToUse instanceof CAbilityHero)) {
						for (final RenderUnit otherSelectedUnit : this.ui.selectedUnits) {
							if (otherSelectedUnit != this.ui.selectedUnit) {
								abilityToUse = null;
								for (final CAbility ability : otherSelectedUnit.getSimulationUnit().getAbilities()) {
									final BooleanAbilityTargetCheckReceiver<Void> receiver = BooleanAbilityTargetCheckReceiver
											.<Void>getInstance().reset();
									ability.checkCanTargetNoTarget(this.ui.war3MapViewer.simulation,
											otherSelectedUnit.getSimulationUnit(), this.ui.localPlayer.getId(), orderId,
											false, receiver);
									if (receiver.isTargetable()) {
										abilityToUse = ability;
									}
								}
								if (abilityToUse != null) {
									this.ui.unitOrderListener.issueImmediateOrder(
											otherSelectedUnit.getSimulationUnit().getHandleId(),
											abilityToUse.getHandleId(), orderId, shiftDown);
								}
							}
						}
					}
				}
				else {
					this.ui.activeCommand = abilityToUse;
					this.ui.activeCommandOrderId = orderId;
					this.ui.activeCommandUnit = this.ui.selectedUnit;
					clearAndRepopulateCommandCard();
				}
			}
		}
		else {
			this.ui.unitOrderListener.issueImmediateOrder(this.ui.selectedUnit.getSimulationUnit().getHandleId(),
					abilityHandleId, orderId, MeleeUIInput.isShiftDown());
			if (this.ui.selectedUnits.size() > 1) {
				for (final RenderUnit otherSelectedUnit : this.ui.selectedUnits) {
					if (otherSelectedUnit != this.ui.selectedUnit) {
						this.ui.unitOrderListener.issueImmediateOrder(
								otherSelectedUnit.getSimulationUnit().getHandleId(), abilityHandleId, orderId,
								MeleeUIInput.isShiftDown());
					}
				}
			}
		}
		if (rightClick) {
			this.ui.war3MapViewer.getUiSounds().getSound("AutoCastButtonClick")
					.play(this.ui.uiScene.audioContext, 0, 0, 0);
		}
	}

	public void openMenu(final int orderId) {
		if ((this.ui.activeCommand != null) && (this.ui.draggingItem == null)) {
			// The only button shown while a command waits for a target is Cancel; it carries the open submenu's
			// order id, so reopening that submenu would leave the command (a building ghost) active.
			this.ui.activeCommandUnit = null;
			this.ui.activeCommand = null;
			this.ui.activeCommandOrderId = -1;
			clearAndRepopulateCommandCard();
			return;
		}
		if (orderId == 0) {
			this.ui.subMenuOrderIdStack.clear();
			this.ui.activeCommandUnit = null;
			this.ui.activeCommand = null;
			this.ui.activeCommandOrderId = -1;
		}
		else {
			this.ui.subMenuOrderIdStack.add(orderId);
		}
		clearAndRepopulateCommandCard();
		if (orderId == OrderIds.skillmenu) {
			this.ui.fireMenuGameEvent(JassGameEventsWar3.EVENT_GAME_SHOW_SKILL);
		}
		else if ((orderId == OrderIds.buildmenu) || (orderId == OrderIds.humanbuild) || (orderId == OrderIds.orcbuild)
				|| (orderId == OrderIds.undeadbuild) || (orderId == OrderIds.nightelfbuild)
				|| (orderId == OrderIds.nagabuild)) {
			this.ui.fireMenuGameEvent(JassGameEventsWar3.EVENT_GAME_BUILD_SUBMENU);
		}
	}

	/** ForceUIKey names the command's own hotkey letter, whatever key layout the player has chosen. */
	public void forceUIKey(final String key) {
		if ((key == null) || key.isEmpty()) {
			return;
		}
		final char c = Character.toUpperCase(key.charAt(0));
		final int keycode = Input.Keys.valueOf(String.valueOf(c));
		for (int j = 0; j < MeleeUI.COMMAND_CARD_HEIGHT; j++) {
			for (int i = 0; i < MeleeUI.COMMAND_CARD_WIDTH; i++) {
				final CommandCardIcon icon = this.ui.commandCard[j][i];
				if (icon.isVisible() && icon.checkHotkey(c, keycode)) {
					this.ui.runSimulationDriven(() -> icon.onClick(Input.Buttons.LEFT));
					return;
				}
			}
		}
	}

	void setCursorState(final MenuCursorState state, final Color color) {
		if (state != this.cursorState) {
			if (state.getAnimationName() != null) {
				this.ui.cursorFrame.setSequence(state.getAnimationName());
			}
		}
		if (color != this.cursorColor) {
			this.ui.cursorFrame.setVertexColor(color);
		}
		this.cursorState = state;
	}

	void setCursorState(final MenuCursorState state) {
		setCursorState(state, Color.WHITE);
	}

	final class CursorTargetSetupVisitor implements CAbilityVisitor<Void> {
		private int baseMouseX;
		private int baseMouseY;

		CursorTargetSetupVisitor reset(final int baseMouseX, final int baseMouseY) {
			this.baseMouseX = baseMouseX;
			this.baseMouseY = baseMouseY;
			return this;
		}

		@Override
		public Void accept(final CAbilityAttack ability) {
			if (MeleeUICommands.this.ui.activeCommandOrderId == OrderIds.attackground) {
				float radius = 0;
				for (final CUnitAttack attack : MeleeUICommands.this.ui.activeCommandUnit.getSimulationUnit()
						.getCurrentAttacks()) {
					if (attack.getWeaponType().isAttackGroundSupported()) {
						if (attack instanceof CUnitAttackMissileSplash) {
							final int areaOfEffectSmallDamage = ((CUnitAttackMissileSplash) attack)
									.getAreaOfEffectSmallDamage();
							radius = areaOfEffectSmallDamage;
						}
						else if (attack instanceof CUnitAttackMissileLine) {
							final float areaOfEffectSmallDamage = ((CUnitAttackMissileLine) attack)
									.getDamageSpillRadius();
							radius = areaOfEffectSmallDamage;
						}
					}
				}
				handlePlacementCursor(ability, radius);
			}
			else {
				handleTargetCursor(ability);
			}
			return null;
		}

		@Override
		public Void accept(final CAbilityMove ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityOrcBuild ability) {
			handleBuildCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityHumanBuild ability) {
			handleBuildCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityUndeadBuild ability) {
			handleBuildCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityNightElfBuild ability) {
			handleBuildCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityGenericDoNothing ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final GenericSingleIconPassiveAbility ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityColdArrows ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityNagaBuild ability) {
			handleBuildCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityNeutralBuild ability) {
			handleBuildCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityBuildInProgress ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityQueue ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilitySellItems ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilitySellUnits ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityAwakenHero ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityUpgrade ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityReviveHero ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final AbilityBuilderActiveAbility ability) {
			final float uiAreaOfEffect = ability.getUIAreaOfEffect();
			if (Float.isNaN(uiAreaOfEffect)) {
				handleTargetCursor(ability);
			}
			else {
				handlePlacementCursor(ability, uiAreaOfEffect);
			}
			return null;
		}

		@Override
		public Void accept(final GenericSingleIconActiveAbility ability) {
			final float uiAreaOfEffect = ability.getUIAreaOfEffect();
			if (Float.isNaN(uiAreaOfEffect)) {
				handleTargetCursor(ability);
			}
			else {
				handlePlacementCursor(ability, uiAreaOfEffect);
			}
			return null;
		}

		@Override
		public Void accept(final GenericNoIconAbility ability) {
			// this should probably never happen
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CBuff ability) {
			// this should probably never happen
			return null;
		}

		@Override
		public Void accept(final CAbilityReturnResources ability) {
			// this should probably never happen
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityNeutralBuilding ability) {
			// this should probably never happen
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityRally ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityHero ability) {
			handleTargetCursor(ability);
			return null;
		}

		@Override
		public Void accept(final CAbilityRoot ability) {
			handleBuildCursor(null,
					MeleeUICommands.this.ui.activeCommandUnit.getSimulationUnit().getTypeId().getValue());
			return null;
		}

		@Override
		public Void accept(final CAbilityJass ability) {
			// TODO getting icon from type is currently inefficient
			final COrderButton orderCommandCardIcon = ability.getOrderCommandCardIcon(
					MeleeUICommands.this.ui.war3MapViewer.simulation,
					MeleeUICommands.this.ui.selectedUnit.getSimulationUnit(),
					MeleeUICommands.this.ui.activeCommandOrderId);
			final OrderButtonUI renderPeer = MeleeUICommands.this.ui.war3MapViewer.getAbilityDataUI()
					.getRenderPeer(orderCommandCardIcon);
			if (renderPeer.getMouseTargetRadius() > 0) {
				handlePlacementCursor(ability, renderPeer.getMouseTargetRadius());
			}
			else if (renderPeer.getPreviewBuildUnitId() != null) {
				handleBuildCursor(null, renderPeer.getPreviewBuildUnitId().getValue());
			}
			else {
				handleTargetCursor(ability);
			}
			return null;
		}

		private void handleTargetCursor(final CAbility ability) {
			if (MeleeUICommands.this.ui.cursorModelInstance != null) {
				MeleeUICommands.this.ui.cursorModelInstance.detach();
				MeleeUICommands.this.ui.cursorModelInstance = null;
				MeleeUICommands.this.ui.cursorFrame.setVisible(true);
			}
			MeleeUICommands.this.ui.cursorFrame.setSequence("Target");
		}

		private void handleBuildCursor(final AbstractCAbilityBuild ability) {
			handleBuildCursor(ability, MeleeUICommands.this.ui.activeCommandOrderId);
		}

		private void handleBuildCursor(final AbstractCAbilityBuild ability, final int previewBuildUnitId) {
			boolean justLoaded = false;
			final War3MapViewer viewer = MeleeUICommands.this.ui.war3MapViewer;
			if (MeleeUICommands.this.ui.cursorModelInstance == null) {
				final War3ID buildingTypeId = new War3ID(previewBuildUnitId);
				MeleeUICommands.this.cursorBuildingUnitType = viewer.simulation.getUnitData()
						.getUnitType(buildingTypeId);
				final MdxModel model = viewer.getUnitTypeData(buildingTypeId).getModel();
				MeleeUICommands.this.ui.cursorModelInstance = (MdxComplexInstance) model.addInstance();
//				MeleeUI.this.cursorModelInstance.setVertexColor(new float[] { 1, 1, 1, 0.5f });
				final int playerColorIndex = viewer.simulation.getPlayer(
						MeleeUICommands.this.ui.activeCommandUnit.getSimulationUnit().getPlayerIndex()).getColor();
				MeleeUICommands.this.ui.cursorModelInstance.setTeamColor(playerColorIndex);
				MeleeUICommands.this.ui.cursorModelInstance.rotate(
						RenderUnit.tempQuat.setFromAxis(RenderMathUtils.VEC3_UNIT_Z,
						viewer.simulation.getGameplayConstants().getBuildingAngle()));
				final float scalingValue = viewer.getUnitTypeData(buildingTypeId).getScalingValue();
				MeleeUICommands.this.ui.cursorModelInstance.scale(new float[] { scalingValue, scalingValue,
						scalingValue });
				MeleeUICommands.this.ui.cursorModelInstance.setAnimationSpeed(0f);
				justLoaded = true;
				final CUnitType buildingUnitType = MeleeUICommands.this.cursorBuildingUnitType;
				MeleeUICommands.this.cursorModelPathing = buildingUnitType.getBuildingPathingPixelMap();

				if (MeleeUICommands.this.cursorModelPathing != null) {
					MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap = new Pixmap(
							MeleeUICommands.this.cursorModelPathing.getWidth(),
							MeleeUICommands.this.cursorModelPathing.getHeight(), Format.RGBA8888);
					MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap.setBlending(Blending.None);
					MeleeUICommands.this.cursorModelUnderneathPathingRedGreenPixmapTextureData = new PixmapTextureData(
							MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap, Format.RGBA8888, false,
							false);
					MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmapTexture = new Texture(
							MeleeUICommands.this.cursorModelUnderneathPathingRedGreenPixmapTextureData);
					final ViewerTextureRenderable greenPixmap = new ViewerTextureRenderable.GdxViewerTextureRenderable(
							MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmapTexture);
					MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenSplatModel = new SplatModel(Gdx.gl30,
							greenPixmap, new ArrayList<>(), viewer.terrain.centerOffset, new ArrayList<>(), true, false,
							true, false);
					MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenSplatModel.color[3] = 0.20f;
				}
			}
			viewer.getClickLocation(MeleeUI.clickLocationTemp, this.baseMouseX,
					Gdx.graphics.getHeight() - this.baseMouseY, false, false);
			if (MeleeUICommands.this.cursorModelPathing != null) {
				MeleeUI.clickLocationTemp.x = AbstractCAbilityBuild.roundToBuildGrid(MeleeUI.clickLocationTemp.x,
						MeleeUICommands.this.cursorModelPathing.getWidth());
				MeleeUI.clickLocationTemp.y = AbstractCAbilityBuild.roundToBuildGrid(MeleeUI.clickLocationTemp.y,
						MeleeUICommands.this.cursorModelPathing.getHeight());
				MeleeUI.clickLocationTemp.z = viewer.terrain.getGroundHeight(MeleeUI.clickLocationTemp.x,
						MeleeUI.clickLocationTemp.y);

				final int cursorWidthCells = MeleeUICommands.this.cursorModelPathing.getWidth();
				final int halfCursorWidthCells = cursorWidthCells / 2;
				final float halfRenderWidth = cursorWidthCells * 16;
				final int cursorHeightCells = MeleeUICommands.this.cursorModelPathing.getHeight();
				final int halfCursorHeightCells = cursorHeightCells / 2;
				final float halfRenderHeight = cursorHeightCells * 16;
				final PathingGrid pathingGrid = viewer.simulation.getPathingGrid();
				boolean blockAll = false;
				final int cellX = pathingGrid.getCellX(MeleeUI.clickLocationTemp.x);
				final int cellY = pathingGrid.getCellY(MeleeUI.clickLocationTemp.y);
				if ((cellX < halfCursorWidthCells) || (cellX > (pathingGrid.getWidth() - halfCursorWidthCells))
						|| (cellY < halfCursorHeightCells)
						|| (cellY > (pathingGrid.getHeight() - halfCursorHeightCells))) {
					blockAll = true;
				}
				final boolean canBeBuiltOnThem = MeleeUICommands.this.cursorBuildingUnitType.isCanBeBuiltOnThem();
				if (canBeBuiltOnThem) {
					viewer.simulation.getWorldCollision().enumBuildingsAtPoint(MeleeUI.clickLocationTemp.x,
							MeleeUI.clickLocationTemp.y,
							MeleeUICommands.this.buildOnBuildingIntersector.reset(MeleeUI.clickLocationTemp.x,
							MeleeUI.clickLocationTemp.y));
					blockAll = MeleeUICommands.this.buildOnBuildingIntersector.getUnitToBuildOn() == null;
				}
				if (blockAll) {
					for (int i = 0; i < MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap
							.getWidth(); i++) {
						for (int j = 0; j < MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap
								.getHeight(); j++) {
							MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap.drawPixel(i,
									MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap
											.getHeight() - 1 - j, Color.rgba8888(1, 0, 0, 1.0f));
						}
					}
				}
				else if (!canBeBuiltOnThem) {
					for (int i = 0; i < MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap
							.getWidth(); i++) {
						for (int j = 0; j < MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap
								.getHeight(); j++) {
							boolean blocked = false;
							final short pathing = pathingGrid.getPathing(
									(MeleeUI.clickLocationTemp.x + (i * 32)) - halfRenderWidth,
									(MeleeUI.clickLocationTemp.y + (j * 32)) - halfRenderHeight);
							for (final CBuildingPathingType preventedType : MeleeUICommands.this.cursorBuildingUnitType
									.getPreventedPathingTypes()) {
								if (PathingFlags.isPathingFlag(pathing, preventedType)) {
									blocked = true;
								}
							}
							for (final CBuildingPathingType requiredType : MeleeUICommands.this.cursorBuildingUnitType
									.getRequiredPathingTypes()) {
								if (!PathingFlags.isPathingFlag(pathing, requiredType)) {
									blocked = true;
								}
							}
							final int color = blocked ? Color.rgba8888(1, 0, 0, 1.0f) : Color.rgba8888(0, 1, 0, 1.0f);
							MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap.drawPixel(i,
									MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap
											.getHeight() - 1 - j, color);
						}
					}
				}
				else {
					for (int i = 0; i < MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap
							.getWidth(); i++) {
						for (int j = 0; j < MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap
								.getHeight(); j++) {
							MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap.drawPixel(i,
									MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmap
											.getHeight() - 1 - j, Color.rgba8888(0, 1, 0, 1.0f));
						}
					}
				}
				MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenPixmapTexture
						.load(MeleeUICommands.this.cursorModelUnderneathPathingRedGreenPixmapTextureData);

				if (justLoaded) {
					viewer.terrain.addSplatBatchModel(MeleeUI.BUILDING_PATHING_PREVIEW_KEY,
							MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenSplatModel);
					MeleeUICommands.this.ui.placementCursor =
							MeleeUICommands.this.ui.cursorModelUnderneathPathingRedGreenSplatModel.add(
							MeleeUI.clickLocationTemp.x - halfRenderWidth,
							MeleeUI.clickLocationTemp.y - halfRenderHeight,
							MeleeUI.clickLocationTemp.x + halfRenderWidth,
							MeleeUI.clickLocationTemp.y + halfRenderHeight, 10, viewer.terrain.centerOffset);
				}
				MeleeUICommands.this.ui.placementCursor.setLocation(MeleeUI.clickLocationTemp.x,
						MeleeUI.clickLocationTemp.y, viewer.terrain.centerOffset);
			}
			MeleeUICommands.this.ui.cursorModelInstance.setLocation(MeleeUI.clickLocationTemp);
			SequenceUtils.randomSequence(MeleeUICommands.this.ui.cursorModelInstance, PrimaryTag.STAND);
			MeleeUICommands.this.ui.cursorFrame.setVisible(false);
			if (justLoaded) {
				MeleeUICommands.this.ui.cursorModelInstance.setScene(viewer.worldScene);
			}
		}

		private void handlePlacementCursor(final CAbility ability, final float radius) {
			final War3MapViewer viewer = MeleeUICommands.this.ui.war3MapViewer;
			viewer.getClickLocation(MeleeUI.clickLocationTemp, this.baseMouseX,
					Gdx.graphics.getHeight() - this.baseMouseY,
					false, true); // TODO change this to true once the placement cursor can draw over water, which
			// it should upgrade to do probs
			if (MeleeUICommands.this.ui.placementCursor == null) {
				MeleeUICommands.this.ui.placementCursor = viewer.terrain.addUberSplat(
						MeleeUICommands.this.ui.rootFrame.getSkinField("PlacementCursor"), MeleeUI.clickLocationTemp.x,
						MeleeUI.clickLocationTemp.y, 10, radius, true, true, true, true);
			}
			MeleeUICommands.this.ui.placementCursor.setLocation(MeleeUI.clickLocationTemp.x,
					MeleeUI.clickLocationTemp.y, viewer.terrain.centerOffset);
			MeleeUICommands.this.ui.cursorFrame.setVisible(false);
		}
	}

	private final class RallyPositioningVisitor implements AbilityTargetVisitor<Void> {
		private MdxComplexInstance rallyPointInstance = null;

		public RallyPositioningVisitor reset(final MdxComplexInstance rallyPointInstance) {
			this.rallyPointInstance = rallyPointInstance;
			return this;
		}

		@Override
		public Void accept(final AbilityPointTarget target) {
			this.rallyPointInstance.setParent(null);
			final float rallyPointX = target.getX();
			final float rallyPointY = target.getY();
			this.rallyPointInstance.setLocation(rallyPointX, rallyPointY,
					MeleeUICommands.this.ui.war3MapViewer.terrain.getGroundHeight(rallyPointX, rallyPointY));
			return null;
		}

		@Override
		public Void accept(final CUnit target) {
			final RenderUnit renderUnit = MeleeUICommands.this.ui.war3MapViewer.getRenderPeer(target);
			final MdxModel model = (MdxModel) renderUnit.instance.model;
			int index = -1;
			for (int i = 0; i < model.attachments.size(); i++) {
				final Attachment attachment = model.attachments.get(i);
				if (attachment.getName().toLowerCase(Locale.US).startsWith("sprite rally")) {
					index = i;
					break;
				}
			}
			if (index == -1) {
				for (int i = 0; i < model.attachments.size(); i++) {
					final Attachment attachment = model.attachments.get(i);
					if (attachment.getName().toLowerCase(Locale.US).startsWith("sprite")) {
						index = i;
						break;
					}
				}
			}
			if (index == -1) {
				for (int i = 0; i < model.attachments.size(); i++) {
					final Attachment attachment = model.attachments.get(i);
					if (attachment.getName().toLowerCase(Locale.US).startsWith("overhead ref")) {
						index = i;
					}
				}
			}
			if (index != -1) {
				final MdxNode attachment = renderUnit.instance.getAttachment(index);
				this.rallyPointInstance.setParent(attachment);
				this.rallyPointInstance.setLocation(0, 0, 0);
			}
			else {
				this.rallyPointInstance.setParent(null);
				final float rallyPointX = target.getX();
				final float rallyPointY = target.getY();
				this.rallyPointInstance.setLocation(rallyPointX, rallyPointY,
						MeleeUICommands.this.ui.war3MapViewer.terrain.getGroundHeight(rallyPointX, rallyPointY));
			}
			return null;
		}

		@Override
		public Void accept(final CDestructable target) {
			this.rallyPointInstance.setParent(null);
			final float rallyPointX = target.getX();
			final float rallyPointY = target.getY();
			this.rallyPointInstance.setLocation(rallyPointX, rallyPointY,
					MeleeUICommands.this.ui.war3MapViewer.terrain.getGroundHeight(rallyPointX, rallyPointY) + 192);
			return null;
		}

		@Override
		public Void accept(final CItem target) {
			this.rallyPointInstance.setParent(null);
			final float rallyPointX = target.getX();
			final float rallyPointY = target.getY();
			this.rallyPointInstance.setLocation(rallyPointX, rallyPointY,
					MeleeUICommands.this.ui.war3MapViewer.terrain.getGroundHeight(rallyPointX, rallyPointY));
			return null;
		}
	}

	final class ActiveCommandUnitTargetFilter implements CWidgetFilterFunction {
		String lastFailureMessage = null;

		@Override
		public boolean call(final CWidget unit) {
			final ExternStringMsgTargetCheckReceiver<CWidget> targetReceiver = ExternStringMsgTargetCheckReceiver
					.<CWidget>getInstance().reset();
			MeleeUICommands.this.ui.activeCommand.checkCanTarget(MeleeUICommands.this.ui.war3MapViewer.simulation,
					MeleeUICommands.this.ui.activeCommandUnit.getSimulationUnit(),
					MeleeUICommands.this.ui.localPlayer.getId(), MeleeUICommands.this.ui.activeCommandOrderId, false,
					unit, targetReceiver);
			this.lastFailureMessage = targetReceiver.getExternStringKey();
			return targetReceiver.getTarget() != null;
		}

		public void reset() {
			this.lastFailureMessage = null;
		}
	}

	public void setDraggingItem(final CItem itemInSlot) {
		this.ui.draggingItem = itemInSlot;
		for (final CommandCardIcon[] row : this.ui.inventoryIcons) {
			for (final CommandCardIcon icon : row) {
				if (icon != null) {
					icon.setLeftClickPlacesItem(itemInSlot != null);
				}
			}
		}
		if (itemInSlot != null) {
			final String iconPath = this.ui.war3MapViewer.getAbilityDataUI().getItemUI(itemInSlot.getTypeId())
					.getItemIconPathForDragging();
			this.ui.cursorFrame.setReplaceableId(21, this.ui.war3MapViewer.blp(iconPath));

			int index = 0;
			final CAbilityInventory inventory = this.ui.selectedUnit.getSimulationUnit().getInventoryData();
			for (int i = 0; i < MeleeUI.INVENTORY_HEIGHT; i++) {
				for (int j = 0; j < this.ui.inventoryWidth; j++) {
					final CommandCardIcon inventoryIcon = this.ui.inventoryIcons[i][j];
					final CItem item = inventory.getItemInSlot(index);
					if (item == null) {
						if (index < inventory.getItemCapacity()) {
							inventoryIcon.setCommandButtonData(null, 0, 0, index + 1, true, false, false, null, null,
									'\0', 0, 0, 0, 0, false, 0, 0, -1);
						}
					}
					index++;
				}
			}
		}
		else {
			if (this.ui.selectedUnit != null) {
				final CAbilityInventory inventory = this.ui.selectedUnit.getSimulationUnit().getInventoryData();
				if (inventory != null) {
					int index = 0;
					for (int i = 0; i < MeleeUI.INVENTORY_HEIGHT; i++) {
						for (int j = 0; j < this.ui.inventoryWidth; j++) {
							final CommandCardIcon inventoryIcon = this.ui.inventoryIcons[i][j];
							final CItem item = inventory.getItemInSlot(index);
							if (item == null) {
								if (index < inventory.getItemCapacity()) {
									inventoryIcon.clear();
								}
							}
							index++;
						}
					}
				}
			}

		}
	}

	public void rallyPointChanged() {
		if (this.ui.selectedUnit != null) {
			final CUnit simulationUnit = this.ui.selectedUnit.getSimulationUnit();
			repositionRallyPoint(simulationUnit);
		}
	}

	void repositionRallyPoint(final CUnit simulationUnit) {
		final AbilityTarget rallyPoint = simulationUnit.getRallyPoint();
		if ((rallyPoint != null) && (simulationUnit.getFirstAbilityOfType(CAbilityRally.class) != null)) {
			this.ui.rallyPointInstance.setTeamColor(
					this.ui.war3MapViewer.simulation.getPlayer(simulationUnit.getPlayerIndex()).getColor());
			this.ui.rallyPointInstance.show();
			this.ui.rallyPointInstance.detach();
			rallyPoint.visit(this.rallyPositioningVisitor.reset(this.ui.rallyPointInstance));
			this.ui.rallyPointInstance.setScene(this.ui.war3MapViewer.worldScene);
		}
		else {
			this.ui.rallyPointInstance.hide();
			this.ui.rallyPointInstance.detach();
		}
	}

	public void waypointsChanged() {
		if (this.ui.selectedUnit != null) {
			final CUnit simulationUnit = this.ui.selectedUnit.getSimulationUnit();
			repositionWaypointFlags(simulationUnit);
		}
		else {
			repositionWaypointFlags(null);
		}
	}

	void repositionWaypointFlags(final CUnit simulationUnit) {
		final Iterator<COrder> iterator;
		int orderIndex = 0;
		if (simulationUnit != null) {
			final Queue<COrder> orderQueue = simulationUnit.getOrderQueue();
			iterator = orderQueue.iterator();
			final COrder order = simulationUnit.getCurrentOrder();
			if ((order != null) && order.isQueued()) {
				final MdxComplexInstance waypointModelInstance = getOrCreateWaypointIndicator(orderIndex);
				final AbilityTarget target = order.getTarget(this.ui.war3MapViewer.simulation);
				if (target != null) {
					waypointModelInstance.show();
					waypointModelInstance.detach();
					target.visit(this.rallyPositioningVisitor.reset(waypointModelInstance));
					waypointModelInstance.setScene(this.ui.war3MapViewer.worldScene);
				}
				else {
					waypointModelInstance.hide();
					waypointModelInstance.detach();
				}
				orderIndex++;
			}
		}
		else {
			iterator = Collections.emptyIterator();
		}
		for (; (orderIndex < this.waypointModelInstances.size()) || iterator.hasNext(); orderIndex++) {
			final MdxComplexInstance waypointModelInstance = getOrCreateWaypointIndicator(orderIndex);
			if (iterator.hasNext()) {
				final COrder order = iterator.next();
				if (order != null) {
					final AbilityTarget target = order.getTarget(this.ui.war3MapViewer.simulation);
					if (target != null) {
						waypointModelInstance.show();
						waypointModelInstance.detach();
						target.visit(this.rallyPositioningVisitor.reset(waypointModelInstance));
						waypointModelInstance.setScene(this.ui.war3MapViewer.worldScene);
					}
					else {
						waypointModelInstance.hide();
						waypointModelInstance.detach();
					}
				}
				else {
					waypointModelInstance.hide();
					waypointModelInstance.detach();
				}
			}
			else {
				waypointModelInstance.hide();
				waypointModelInstance.detach();
			}
		}
	}

	private MdxComplexInstance getOrCreateWaypointIndicator(final int index) {
		while (index >= this.waypointModelInstances.size()) {
			final MdxComplexInstance waypointModelInstance = (MdxComplexInstance) this.waypointModel.addInstance();
			waypointModelInstance.rotate(RenderUnit.tempQuat.setFromAxis(RenderMathUtils.VEC3_UNIT_Z,
					this.ui.war3MapViewer.simulation.getGameplayConstants().getBuildingAngle()));
			waypointModelInstance.setSequenceLoopMode(SequenceLoopMode.ALWAYS_LOOP);
			SequenceUtils.randomStandSequence(waypointModelInstance);
			waypointModelInstance.hide();
			this.waypointModelInstances.add(waypointModelInstance);
		}
		return this.waypointModelInstances.get(index);
	}

	void clearCommandCard() {
		for (int j = 0; j < MeleeUI.COMMAND_CARD_HEIGHT; j++) {
			for (int i = 0; i < MeleeUI.COMMAND_CARD_WIDTH; i++) {
				this.ui.commandCard[j][i].clear();
			}
		}
		for (int i = 0; i < MeleeUI.BUFF_DISPLAY_MAX; i++) {
			this.ui.buffBarIcons[i].clear();
		}
		this.ui.currentBuffBarIconIndex = 0;
	}

	/** The item hints describe mouse use; on a touch screen the same actions are a tap and a drag. */
	static String touchItemHint(final String hint) {
		if (!TouchInputRouter.isActive() || (hint == null)) {
			return hint;
		}
		return hint.replace("Left-Click", "Tap").replace("Drop item on shop", "Drag item onto a shop");
	}

	public void commandButton(final int buttonPositionX, final int buttonPositionY, final Texture icon,
			final int abilityHandleId, final int orderId, final int autoCastId, final boolean active,
			final boolean autoCastActive, final boolean menuButton, final String tip, final String uberTip,
			final char hotkey, final int goldCost, final int lumberCost, final int foodCost, final int manaCost,
			final float cooldownRemaining, final float cooldownMax, final int numberOverlay) {
		if ((buttonPositionX == -11) || (buttonPositionY == -11)) {
			// NOTE some guys said they liked to do this as a hack to hide icons or whatever
			return;
		}
		int x = Math.max(0, Math.min(MeleeUI.COMMAND_CARD_WIDTH - 1, buttonPositionX));
		int y = Math.max(0, Math.min(MeleeUI.COMMAND_CARD_HEIGHT - 1, buttonPositionY));
		while ((x >= 0) && (y >= 0) && this.ui.commandCard[y][x].isVisible()) {
			x--;
			if ((x < 0) && (y != 0)) {
				x = MeleeUI.COMMAND_CARD_WIDTH - 1;
				y--;
			}
		}
		if ((y < 0) || (x < 0)) {
			if (y < 0) {
				y = 0;
			}
			if (x < 0) {
				x = 0;
			}
			while ((x < MeleeUI.COMMAND_CARD_WIDTH) && (y < MeleeUI.COMMAND_CARD_HEIGHT)
					&& this.ui.commandCard[y][x].isVisible()) {
				x++;
				if (x >= MeleeUI.COMMAND_CARD_WIDTH) {
					x = 0;
					y++;
				}
			}
		}
		if ((x < MeleeUI.COMMAND_CARD_WIDTH) && (x >= 0) && (y < MeleeUI.COMMAND_CARD_HEIGHT) && (y >= 0)) {
			this.ui.commandCard[y][x].setCommandButtonData(icon, abilityHandleId, orderId, autoCastId, active,
					autoCastActive, menuButton, tip, uberTip, hotkey, goldCost, lumberCost, foodCost, manaCost,
					this.ui.selectedUnit.getSimulationUnit().getMana() < manaCost, cooldownRemaining, cooldownMax,
					numberOverlay);
		}
	}

	void clearAndRepopulateCommandCard() {
		clearCommandCard();
		final AbilityDataUI abilityDataUI = this.ui.war3MapViewer.getAbilityDataUI();
		final int menuOrderId = getSubMenuOrderId();
		if ((this.ui.activeCommand != null) && (this.ui.draggingItem == null)) {
			final IconUI cancelUI = abilityDataUI.getCancelUI();
			commandButton(cancelUI.getButtonPositionX(), cancelUI.getButtonPositionY(), cancelUI.getIcon(), 0,
					menuOrderId, 0, false, false, true, cancelUI.getToolTip(), cancelUI.getUberTip(),
					cancelUI.getHotkey(), 0, 0, 0, 0, 0, 0, -1);
		}
		else {
			if (menuOrderId != 0) {
				final int exitOrderId = this.ui.subMenuOrderIdStack.size() > 1
						? this.ui.subMenuOrderIdStack.get(this.ui.subMenuOrderIdStack.size() - 2)
						: 0;
				final IconUI cancelUI = abilityDataUI.getCancelUI();
				commandButton(cancelUI.getButtonPositionX(), cancelUI.getButtonPositionY(), cancelUI.getIcon(), 0,
						exitOrderId, 0, false, false, true, cancelUI.getToolTip(), cancelUI.getUberTip(),
						cancelUI.getHotkey(), 0, 0, 0, 0, 0, 0, -1);
			}
			this.ui.selectedUnit.populateCommandCard(this.ui.war3MapViewer.simulation, this.ui.rootFrame, this.ui,
					abilityDataUI, menuOrderId, this.ui.selectedUnits.size() > 1,
					this.ui.war3MapViewer.getLocalPlayerIndex());
		}
	}

	int getSubMenuOrderId() {
		return this.ui.subMenuOrderIdStack.isEmpty() ? 0
				: this.ui.subMenuOrderIdStack.get(this.ui.subMenuOrderIdStack.size() - 1);
	}

	void useActiveCommandOnUnit(final boolean shiftDown, final RenderWidget rayPickUnit) {
		if (this.ui.draggingItem != null) {
			this.ui.unitOrderListener.issueDropItemAtTargetOrder(
					this.ui.activeCommandUnit.getSimulationUnit().getHandleId(), this.ui.activeCommand.getHandleId(),
					this.ui.activeCommandOrderId, this.ui.draggingItem.getHandleId(),
					rayPickUnit.getSimulationWidget().getHandleId(), shiftDown);
			setDraggingItem(null);
		}
		else {
			this.ui.unitOrderListener.issueTargetOrder(this.ui.activeCommandUnit.getSimulationUnit().getHandleId(),
					this.ui.activeCommand.getHandleId(), this.ui.activeCommandOrderId,
					rayPickUnit.getSimulationWidget().getHandleId(), shiftDown);
			if (this.ui.selectedUnits.size() > 1) {
				for (final RenderUnit otherSelectedUnit : this.ui.selectedUnits) {
					if (otherSelectedUnit != this.ui.activeCommandUnit) {
						CAbility abilityToUse = null;
						CWidget targetToUse = null;
						for (final CAbility ability : otherSelectedUnit.getSimulationUnit().getAbilities()) {
							final CWidgetAbilityTargetCheckReceiver receiver = CWidgetAbilityTargetCheckReceiver.INSTANCE
									.reset();
							ability.checkCanTarget(this.ui.war3MapViewer.simulation,
									otherSelectedUnit.getSimulationUnit(), this.ui.localPlayer.getId(),
									this.ui.activeCommandOrderId, false, rayPickUnit.getSimulationWidget(), receiver);
							if (receiver.getTarget() != null) {
								abilityToUse = ability;
								targetToUse = receiver.getTarget();
							}
						}
						if (abilityToUse != null) {
							this.ui.unitOrderListener.issueTargetOrder(
									otherSelectedUnit.getSimulationUnit().getHandleId(), abilityToUse.getHandleId(),
									this.ui.activeCommandOrderId, targetToUse.getHandleId(), shiftDown);
						}
					}
				}
			}
		}
		final UnitSound yesSound = this.ui.activeCommand instanceof CAbilityAttack ? this.ui.getSelectedUnit()
				.soundset.yesAttack : this.ui.getSelectedUnit().soundset.yes;
		if (yesSound.playUnitResponse(this.ui.war3MapViewer.worldScene.audioContext, this.ui.getSelectedUnit())) {
			this.ui.infoPanel.portraitTalk(yesSound);
		}
		this.ui.selectedSoundCount = 0;
		if (this.ui.activeCommand instanceof CAbilityRally) {
			this.ui.war3MapViewer.getUiSounds().getSound("RallyPointPlace").play(this.ui.uiScene.audioContext, 0, 0, 0);
		}
		if (!shiftDown) {
			this.ui.subMenuOrderIdStack.clear();
			this.ui.activeCommandUnit = null;
			this.ui.activeCommand = null;
			this.ui.activeCommandOrderId = -1;
			clearAndRepopulateCommandCard();
		}
	}

	public boolean isOverInventorySlot(final int screenX, final int screenY) {
		MeleeUI.screenCoordsVector.set(screenX, screenY);
		this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
		final UIFrame frame = this.ui.rootFrame.getFrameChildUnderMouse(MeleeUI.screenCoordsVector.x,
				MeleeUI.screenCoordsVector.y);
		for (final CommandCardIcon[] row : this.ui.inventoryIcons) {
			for (final CommandCardIcon icon : row) {
				if ((icon != null) && (icon == frame)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isOverAutocastButton(final int screenX, final int screenY) {
		MeleeUI.screenCoordsVector.set(screenX, screenY);
		this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
		final UIFrame frame = this.ui.rootFrame.getFrameChildUnderMouse(MeleeUI.screenCoordsVector.x,
				MeleeUI.screenCoordsVector.y);
		return (frame instanceof CommandCardIcon) && ((CommandCardIcon) frame).isAutoCastCapable();
	}

	public boolean hasActiveCommand() {
		return this.ui.activeCommand != null;
	}

	public boolean isPlacingBuilding() {
		return (this.ui.activeCommand != null) && (this.ui.cursorModelInstance != null);
	}

	private class ItemCommandCardCommandListener implements CommandCardCommandListener {
		@Override
		public void onClick(final int abilityHandleId, final int orderId, final boolean rightClick) {
			final RenderUnit selectedUnit2 = MeleeUICommands.this.ui.selectedUnit;
			final CUnit simulationUnit = selectedUnit2.getSimulationUnit();
			final CAbilityInventory inventoryData = simulationUnit.getInventoryData();
			if (inventoryData == null) {
				return;
			}
			if (rightClick) {
				final int slot = orderId - 1;
				final CItem itemInSlot = inventoryData.getItemInSlot(slot);
				if (MeleeUICommands.this.ui.draggingItem != null) {
					final CUnit activeCmdSimUnit = MeleeUICommands.this.ui.activeCommandUnit.getSimulationUnit();
					MeleeUICommands.this.ui.unitOrderListener.issueTargetOrder(activeCmdSimUnit.getHandleId(),
							activeCmdSimUnit.getInventoryData().getHandleId(), OrderIds.itemDragFirst(MeleeUICommands.this.ui.war3MapViewer.getGameData().isUseNineItemInventory()) + slot,
							MeleeUICommands.this.ui.draggingItem.getHandleId(), false);
					setDraggingItem(null);
					MeleeUICommands.this.ui.activeCommand = null;
					MeleeUICommands.this.ui.activeCommandUnit = null;
				}
				else {
					if (itemInSlot != null) {
						setDraggingItem(itemInSlot);
						MeleeUICommands.this.ui.activeCommand = inventoryData;
						MeleeUICommands.this.ui.activeCommandUnit = selectedUnit2;
						MeleeUICommands.this.ui.activeCommandOrderId = OrderIds.dropitem;
					}
				}
			}
			else {
				final CSimulation game = MeleeUICommands.this.ui.war3MapViewer.simulation;
				final ExternStringMsgAbilityActivationReceiver receiver = ExternStringMsgAbilityActivationReceiver.INSTANCE;
				receiver.reset();
				inventoryData.checkCanUse(game, simulationUnit, MeleeUICommands.this.ui.localPlayer.getId(), orderId,
						false, receiver);
				if (receiver.isUseOk()) {
					final BooleanAbilityTargetCheckReceiver<Void> targetReceiver = BooleanAbilityTargetCheckReceiver
							.getInstance();
					targetReceiver.reset();
					inventoryData.checkCanTargetNoTarget(game, simulationUnit,
							MeleeUICommands.this.ui.localPlayer.getId(), orderId, false, targetReceiver);
					if (targetReceiver.isTargetable()) {
						MeleeUICommands.this.ui.unitOrderListener.issueImmediateOrder(simulationUnit.getHandleId(),
								inventoryData.getHandleId(), orderId, MeleeUIInput.isShiftDown());
					}
					else {
						MeleeUICommands.this.ui.activeCommand = inventoryData;
						MeleeUICommands.this.ui.activeCommandOrderId = orderId;
						MeleeUICommands.this.ui.activeCommandUnit = selectedUnit2;
						clearAndRepopulateCommandCard();
					}
				}
				else {
					final String externStringKey = receiver.getExternStringKey();
					if ((externStringKey != null) && !externStringKey.isEmpty()) {
						MeleeUICommands.this.ui.messages.showInterfaceError(simulationUnit.getPlayerIndex(),
								externStringKey);
					}
				}
			}
		}

		@Override
		public void openMenu(final int orderId) {
			MeleeUICommands.this.openMenu(orderId);
		}

	}
}
