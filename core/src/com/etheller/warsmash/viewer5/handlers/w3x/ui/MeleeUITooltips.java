package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.regex.Pattern;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.AbstractUIFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleStatusBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.StringFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextureFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.viewer5.Bounds;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.UiFrameState;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.TooltipModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.UiFrameSource;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ResourceType;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableActionFrame;

/**
 * The console's tool tips, the hover tips over the world and the health and mana bars drawn
 * over units.
 */
final class MeleeUITooltips {
	private StringFrame tooltipText;
	private StringFrame tooltipUberTipText;
	private StringFrame tooltipInfoText;
	private SetPoint uberTipBelowInfoSetPoint;
	private UIFrame[] tooltipResourceFrames;
	private TextureFrame[] tooltipResourceIconFrames;
	private StringFrame[] tooltipResourceTextFrames;
	private SetPoint uberTipNoResourcesSetPoint;
	private SetPoint uberTipWithResourcesSetPoint;
	private final java.util.Map<UIFrame, String[]> frameTooltips = new java.util.HashMap<>();

	private static final int RESOURCE_GOLD = 0;
	private static final int RESOURCE_LUMBER = 1;
	private static final int RESOURCE_SUPPLY = 2;
	private static final int RESOURCE_UPKEEP = 3;
	private static final int TIME_OF_DAY = 4;
	private static final int ATTACK_ONE = 5;
	private static final int ATTACK_TWO = 6;
	private static final int ARMOR = 7;
	private static final int HERO_ATTRIBUTES = 8;
	private static final int RANK = 9;
	private static final int FOOD = 10;
	/** The gold key mark the game puts after a name, "(|cfffed312F8|r)". */
	private static final Pattern HOTKEY_MARK = Pattern.compile("(?i)\\s*\\(\\|cfffed312[^|]*\\|r\\)");
	private static final float RESOURCE_BAR_ICON_WIDTH = 0.01640625f;
	/** The clock dial at the top middle of the console; the sprite frame itself has no size. */
	private static final float CLOCK_WIDTH = 0.047f;
	private static final float CLOCK_HEIGHT = 0.045f;
	/*
	 * The console tool tip as 1.29 draws it: a box of fixed width over the command card's right edge whose name,
	 * never wrapped, widens it when longer; the costs follow the name, each after the number before it. The
	 * border is drawn inside the frame, so the frame is a little larger than the box seen.
	 */
	private static final float TOOLTIP_WIDTH = 0.2204f;
	private static final float TOOLTIP_BOTTOM = 0.1626f;
	private static final float TOOLTIP_RIGHT = 0.0005f;
	private static final float TOOLTIP_INSET = 0.0058f;
	private static final float TEXT_WRAP_WIDTH = 0.210f;
	private static final float TEXT_TOP = 0.0059f;
	private static final float TEXT_BOTTOM = 0.0027f;
	private static final float GAP_BELOW_NAME = 0.0050f;
	private static final float GAP_BELOW_INFO = 0.0037f;
	private static final float COSTS_ROW = 0.0131f;
	private static final float COSTS_BELOW_NAME = 0.0076f;
	private static final float COST_SPACING = 0.007f;
	private static final float GAP_BELOW_COSTS = COSTS_ROW + GAP_BELOW_NAME;
	/*
	 * Without the enhanced tool tips 1.29 draws a box just around the name, the costs, the gray lines and the
	 * requirements, right of the pointer and on the part pointed at; the resource bar then has none.
	 */
	private static final float SIMPLE_TIP_RIGHT_OF_POINTER = 0.021f;
	private static final float SIMPLE_TIP_OVER_FRAME = 0.001f;
	private static final float SIMPLE_TEXT_WRAP_WIDTH = 0.200f;

	final MeleeUI ui;
	/** Where the pointer was when the tool tip came up, for the box without the enhanced tool tips. */
	private float pointerX;

	MeleeUITooltips(final MeleeUI ui) {
		this.ui = ui;
	}

	void createTooltips() {
		this.ui.tooltipFrame = this.ui.rootFrame.createFrame("SmashToolTip", this.ui.rootFrame, 0, 0);
		this.ui.tooltipFrame.setWidth(GameUI.convertX(this.ui.uiViewport, TOOLTIP_WIDTH));
		this.tooltipText = (StringFrame) this.ui.rootFrame.getFrameByName("SmashToolTipText", 0);
		// The name never wraps: a longer one widens the box instead.
		this.tooltipText.addAnchor(new AnchorDefinition(FramePoint.TOPLEFT,
				GameUI.convertX(this.ui.uiViewport, TOOLTIP_INSET), GameUI.convertY(this.ui.uiViewport, -TEXT_TOP)));
		this.ui.tooltipFrame.setVisible(false);

		this.ui.hovertipFrame = this.ui.rootFrame.createFrame("SmashHoverTip", this.ui.rootFrame, 0, 0);
		this.ui.hovertipText = (StringFrame) this.ui.rootFrame.getFrameByName("SmashHoverTipText", 0);
		this.ui.hovertipText.setWidth(GameUI.convertX(this.ui.uiViewport, 0.274f));
		this.ui.hovertipText.addAnchor(new AnchorDefinition(FramePoint.TOPLEFT,
				GameUI.convertX(this.ui.uiViewport, 0.006f), GameUI.convertY(this.ui.uiViewport, -0.006f)));
		this.ui.hovertipFrame.setVisible(false);

		this.tooltipUberTipText = (StringFrame) this.ui.rootFrame.getFrameByName("SmashUberTipText", 0);
		this.tooltipUberTipText.setWidth(GameUI.convertX(this.ui.uiViewport, TEXT_WRAP_WIDTH));
		this.uberTipNoResourcesSetPoint = new SetPoint(FramePoint.TOPLEFT, this.tooltipText, FramePoint.BOTTOMLEFT, 0,
				GameUI.convertY(this.ui.uiViewport, -GAP_BELOW_NAME));
		this.uberTipWithResourcesSetPoint = new SetPoint(FramePoint.TOPLEFT, this.tooltipText, FramePoint.BOTTOMLEFT, 0,
				GameUI.convertY(this.ui.uiViewport, -GAP_BELOW_COSTS));
		this.tooltipUberTipText.addSetPoint(this.uberTipNoResourcesSetPoint);
		this.tooltipInfoText = (StringFrame) this.ui.rootFrame.getFrameByName("SmashToolTipInfoText", 0);
		this.tooltipInfoText.setWidth(GameUI.convertX(this.ui.uiViewport, TEXT_WRAP_WIDTH));
		this.tooltipInfoText.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.tooltipText, FramePoint.BOTTOMLEFT, 0,
				GameUI.convertY(this.ui.uiViewport, -GAP_BELOW_NAME)));
		this.uberTipBelowInfoSetPoint = new SetPoint(FramePoint.TOPLEFT, this.tooltipInfoText, FramePoint.BOTTOMLEFT, 0,
				GameUI.convertY(this.ui.uiViewport, -GAP_BELOW_INFO));
		this.tooltipResourceFrames = new UIFrame[ResourceType.VALUES.length];
		this.tooltipResourceIconFrames = new TextureFrame[ResourceType.VALUES.length];
		this.tooltipResourceTextFrames = new StringFrame[ResourceType.VALUES.length];
		for (int i = 0; i < this.tooltipResourceFrames.length; i++) {
			this.tooltipResourceFrames[i] = this.ui.rootFrame.createFrame("SmashToolTipIconResource",
					this.ui.tooltipFrame, 0, i);
			this.tooltipResourceIconFrames[i] = (TextureFrame) this.ui.rootFrame
					.getFrameByName("SmashToolTipIconResourceBackdrop", i);
			this.tooltipResourceTextFrames[i] = (StringFrame) this.ui.rootFrame
					.getFrameByName("SmashToolTipIconResourceLabel", i);
			// Each cost follows the number before it, so a longer number pushes the next one along.
			if (i == 0) {
				this.tooltipResourceFrames[i].addSetPoint(new SetPoint(FramePoint.LEFT, this.tooltipText,
						FramePoint.BOTTOMLEFT, 0, GameUI.convertY(this.ui.uiViewport, -COSTS_BELOW_NAME)));
			}
			else {
				this.tooltipResourceFrames[i].addSetPoint(new SetPoint(FramePoint.LEFT,
						this.tooltipResourceTextFrames[i - 1], FramePoint.RIGHT,
						GameUI.convertX(this.ui.uiViewport, COST_SPACING), 0));
			}
			// have we really no better API than the below???
			((AbstractUIFrame) this.ui.tooltipFrame).add(this.tooltipResourceFrames[i]);
			this.ui.rootFrame.remove(this.tooltipResourceFrames[i]);
		}
	}

	/** The text over the widget under the pointer (a unit, an item, a tree); null when it shows none. */
	String getWorldFrameHoverTipText(final CSimulation game, final RenderWidget whichUnit) {
		if (whichUnit == null) {
			return null;
		}
		final UiFrameState state = this.ui.commands.frameState(false);
		return state.hoverHandle == whichUnit.getSimulationWidget().getHandleId() ? state.hoverTip : null;
	}

	void showAllStatusBars() {
		final UnitFrameView view = this.ui.unitView;
		final int width = Gdx.graphics.getWidth();
		final int height = Gdx.graphics.getHeight();
		for (final RenderUnit unit : this.ui.war3MapViewer.units) {
			if ((unit == this.ui.mouseOverUnit) || this.ui.dragSelectPreviewUnits.contains(unit) || !view.unit(unit)
					|| view.isDead() || view.isInvulnerable() || !view.isSelectableByLocal()) {
				continue;
			}
			MeleeUI.clickLocationTemp.set(unit.getX(), unit.getY(), unit.getZ());
			this.ui.war3MapViewer.worldScene.camera.worldToScreen(MeleeUI.screenCoordsVector,
					MeleeUI.clickLocationTemp);
			if ((MeleeUI.screenCoordsVector.x < 0) || (MeleeUI.screenCoordsVector.x > width)
					|| (MeleeUI.screenCoordsVector.y < 0) || (MeleeUI.screenCoordsVector.y > height)) {
				continue;
			}
			positionHealthBar(getHpBar(), unit, 1.0f);
			if (view.hasMana()) {
				positionManaBar(getHpBar(), unit, 1.0f);
			}
		}
	}

	void positionHealthBar(final SimpleStatusBarFrame simpleStatusBarFrame, final RenderWidget unit,
			final float alpha) {
		if (!this.ui.unitView.widget(unit)) {
			simpleStatusBarFrame.setVisible(false);
			return;
		}
		final float lifeRatioRemaining = this.ui.unitView.lifeRatio();
		positionStatusBar(simpleStatusBarFrame, unit, alpha, lifeRatioRemaining,
				Math.min(1.0f, 2.0f - (lifeRatioRemaining * 2)), Math.min(1.0f, lifeRatioRemaining * 2), 0, 0);
	}

	void positionManaBar(final SimpleStatusBarFrame simpleStatusBarFrame, final RenderUnit unit,
			final float alpha) {
		if (!this.ui.unitView.unit(unit)) {
			simpleStatusBarFrame.setVisible(false);
			return;
		}
		positionStatusBar(simpleStatusBarFrame, unit, alpha, this.ui.unitView.manaRatio(), 0, 0, 1, 16f);
	}

	private void positionStatusBar(final SimpleStatusBarFrame simpleStatusBarFrame, final RenderWidget unit,
			final float alpha, final float ratio, final float red, final float green, final float blue,
			final float offsetY) {
		simpleStatusBarFrame.setVisible(true);
		MeleeUI.clickLocationTemp.x = unit.getX();
		MeleeUI.clickLocationTemp.y = unit.getY();
		MeleeUI.clickLocationTemp.z = unit.getZ();
		this.ui.war3MapViewer.worldScene.camera.worldToScreen(MeleeUI.screenCoordsVector, MeleeUI.clickLocationTemp);
		// The bar hangs above the middle of the unit: perspective leans the top of a tall building away from the
		// screen's centre, so only the height is taken from the top.
		final float centerScreenX = MeleeUI.screenCoordsVector.x;
		final Bounds unitBounds = unit.getInstance().getBounds();
		if (unitBounds != null) {
			final BoundingBox unitBoundsBox = unitBounds.getBoundingBox();
			if (unitBoundsBox != null) {
				MeleeUI.clickLocationTemp.z += unitBoundsBox.max.z;
			}
		}
		this.ui.war3MapViewer.worldScene.camera.worldToScreen(MeleeUI.screenCoordsVector, MeleeUI.clickLocationTemp);
		MeleeUI.screenCoordsVector.x = centerScreenX;
		simpleStatusBarFrame.getBarFrame().setTexture("SimpleHpBarConsole", this.ui.rootFrame);
		simpleStatusBarFrame.getBorderFrame().setTexture("Textures\\Black32.blp", this.ui.rootFrame);
		simpleStatusBarFrame.getBorderFrame().setColor(0f, 0f, 0f, alpha);
		simpleStatusBarFrame.getBarFrame().setColor(red, green, blue, alpha);
		final Vector2 unprojected = this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
		// A building's bar is as wide as its selection circle; a unit's is wider than its circle.
		final boolean building = (unit instanceof RenderUnit) && ((RenderUnit) unit).getSimulationUnit().isBuilding();
		final float widthPerSelectionScale = building ? 1.2f : 1.5f;
		simpleStatusBarFrame
				.setWidth((unit.getSelectionScale() * widthPerSelectionScale * Gdx.graphics.getWidth()) / 2560);
		simpleStatusBarFrame.setHeight(16);
		simpleStatusBarFrame.addSetPoint(new SetPoint(FramePoint.CENTER, this.ui.rootFrame, FramePoint.BOTTOMLEFT,
				unprojected.x, unprojected.y - offsetY));
		simpleStatusBarFrame.setValue(ratio);
		simpleStatusBarFrame.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
	}

	SimpleStatusBarFrame getHpBar() {
		final SimpleStatusBarFrame simpleStatusBarFrame;
		if (this.ui.hpBarFrameIndex >= this.ui.hpBarFrames.size()) {
			simpleStatusBarFrame = new SimpleStatusBarFrame("SmashHpBar" + this.ui.hpBarFrameIndex, this.ui.rootFrame,
					true, true, 3.0f);
			this.ui.rootFrame.add(simpleStatusBarFrame);
			this.ui.hpBarFrames.add(simpleStatusBarFrame);
		}
		else {
			simpleStatusBarFrame = this.ui.hpBarFrames.get(this.ui.hpBarFrameIndex);
		}
		this.ui.hpBarFrameIndex++;
		return simpleStatusBarFrame;
	}

	void setFrameTooltip(final UIFrame frame, final String key) {
		if (frame != null) {
			this.frameTooltips.put(frame, new String[] { key, key + "_UBER" });
		}
	}

	void setPointer(final float x) {
		this.pointerX = x;
	}

	void loadTooltip(final ClickableActionFrame mousedUIFrame) {
		showTooltip(mousedUIFrame.getToolTip(), mousedUIFrame.getInfoTip(), mousedUIFrame.getUberTip(),
				mousedUIFrame.getToolTipGoldCost(), mousedUIFrame.getToolTipLumberCost(),
				mousedUIFrame.getToolTipFoodCost(), mousedUIFrame.getToolTipManaCost(), mousedUIFrame);
	}

	void loadFrameTooltip(final UIFrame frame) {
		final String[] keys = this.frameTooltips.get(frame);
		if (keys == null) {
			this.ui.tooltipFrame.setVisible(false);
			return;
		}
		showTooltip(decorated(keys[0]), null, decorated(keys[1]), 0, 0, 0, 0, frame);
	}

	/**
	 * The part of the console under the point that shows a tool tip without being a button: a resource of the
	 * resource bar (its text and, left of gold, lumber and food, the icon), the clock, and the damage, armor and
	 * hero attribute icons of the selected unit.
	 */
	int panelTipAt(final float x, final float y) {
		if ((this.ui.consoleUI == null) || !this.ui.consoleUI.isVisible()) {
			return -1;
		}
		if ((this.ui.resourceBar != null) && this.ui.resourceBar.isVisible()) {
			final StringFrame[] texts = { this.ui.resourceBarGoldText, this.ui.resourceBarLumberText,
					this.ui.resourceBarSupplyText, this.ui.resourceBarUpkeepText };
			for (int i = 0; i < texts.length; i++) {
				final Rectangle bounds = texts[i].getRenderBounds();
				final float left = bounds.x - (i == RESOURCE_UPKEEP ? 0
						: GameUI.convertX(this.ui.uiViewport, RESOURCE_BAR_ICON_WIDTH));
				if ((x >= left) && (x <= (bounds.x + bounds.width)) && (y >= bounds.y)
						&& (y <= (bounds.y + bounds.height))) {
					return i;
				}
			}
		}
		if (overClock(x, y)) {
			return TIME_OF_DAY;
		}
		if (this.ui.selectedUnit == null) {
			return -1;
		}
		if (contains(this.ui.attack1Icon, x, y)) {
			return ATTACK_ONE;
		}
		if (contains(this.ui.attack2Icon, x, y)) {
			return ATTACK_TWO;
		}
		if (contains(this.ui.armorIcon, x, y)) {
			return ARMOR;
		}
		if (contains(this.ui.heroInfoPanel, x, y)) {
			return HERO_ATTRIBUTES;
		}
		if (contains(this.ui.rankIcon, x, y)) {
			return RANK;
		}
		if (contains(this.ui.foodIcon, x, y)) {
			return FOOD;
		}
		return -1;
	}

	private boolean overClock(final float x, final float y) {
		if ((this.ui.timeIndicator == null) || !this.ui.timeIndicator.isVisibleOnScreen()
				|| !(this.ui.consoleUI instanceof AbstractRenderableFrame)) {
			return false;
		}
		final Rectangle console = ((AbstractRenderableFrame) this.ui.consoleUI).getRenderBounds();
		final float top = console.y + console.height;
		return (Math.abs(x - (console.x + (console.width / 2))) <= (GameUI.convertX(this.ui.uiViewport, CLOCK_WIDTH) / 2))
				&& (y <= top) && (y >= (top - GameUI.convertY(this.ui.uiViewport, CLOCK_HEIGHT)));
	}

	private static boolean contains(final UIFrame frame, final float x, final float y) {
		return (frame instanceof AbstractRenderableFrame) && frame.isVisibleOnScreen()
				&& ((AbstractRenderableFrame) frame).getRenderBounds().contains(x, y);
	}

	/** Shows the tool tip of a part found by {@link #panelTipAt}; false when it has none now. */
	boolean loadPanelTip(final int tip) {
		final UiFrameSource source = this.ui.commands.frames();
		source.writer().setHoveredPanelTip(tip);
		source.inputsChanged();
		final UiFrameState state = this.ui.commands.frameState(false);
		if (!state.panelTipShown || (state.panelTipIndex != tip)) {
			return false;
		}
		UIFrame frame = null;
		if ((tip >= ATTACK_ONE) && (tip <= FOOD)) {
			final UIFrame[] frames = { this.ui.attack1Icon, this.ui.attack2Icon, this.ui.armorIcon,
					this.ui.heroInfoPanel, this.ui.rankIcon, this.ui.foodIcon };
			// The selection may have changed since the mouse came here, and its panel with it.
			frame = frames[tip - ATTACK_ONE];
			if ((this.ui.selectedUnit == null) || (frame == null) || !frame.isVisibleOnScreen()) {
				return false;
			}
		}
		final TooltipModel model = state.panelTip;
		showTooltip(model.title, model.extra, model.body, frame);
		return true;
	}

	private String decorated(final String key) {
		return this.ui.rootFrame.getTemplates().getDecoratedString(key);
	}

	private void showTooltip(final String toolTip, final String info, final String uberTip) {
		showTooltip(toolTip, info, uberTip, 0, 0, 0, 0, null);
	}

	private void showTooltip(final String toolTip, final String info, final String uberTip, final UIFrame frame) {
		showTooltip(toolTip, info, uberTip, 0, 0, 0, 0, frame);
	}

	/**
	 * Shows the console tool tip. The frame is the part pointed at: without the enhanced tool tips the box goes on
	 * it, and a tip with no frame then shows nothing.
	 */
	private void showTooltip(final String toolTip, final String info, final String uberTip, final int goldCost,
			final int lumberCost, final int foodCost, final int manaCost, final UIFrame frame) {
		final boolean enhanced = GameOptions.get().isEnhancedTooltips();
		if ((toolTip == null) || (uberTip == null) || (!enhanced && !(frame instanceof AbstractRenderableFrame))) {
			this.ui.tooltipFrame.setVisible(false);
			return;
		}
		final String shownUberTip = enhanced ? uberTip : requirementLines(uberTip);
		final float wrapWidth = GameUI.convertX(this.ui.uiViewport, enhanced ? TEXT_WRAP_WIDTH : SIMPLE_TEXT_WRAP_WIDTH);
		this.tooltipInfoText.setWidth(wrapWidth);
		this.tooltipUberTipText.setWidth(wrapWidth);
		this.ui.rootFrame.setText(this.tooltipUberTipText, shownUberTip);
		int resourceIndex = 0;
		if (goldCost != 0) {
			this.tooltipResourceFrames[resourceIndex].setVisible(true);
			this.tooltipResourceIconFrames[resourceIndex].setTexture("ToolTipGoldIcon", this.ui.rootFrame);
			this.ui.rootFrame.setText(this.tooltipResourceTextFrames[resourceIndex], Integer.toString(goldCost));
			resourceIndex++;
		}
		if (lumberCost != 0) {
			this.tooltipResourceFrames[resourceIndex].setVisible(true);
			this.tooltipResourceIconFrames[resourceIndex].setTexture("ToolTipLumberIcon", this.ui.rootFrame);
			this.ui.rootFrame.setText(this.tooltipResourceTextFrames[resourceIndex], Integer.toString(lumberCost));
			resourceIndex++;
		}
		if (foodCost != 0) {
			this.tooltipResourceFrames[resourceIndex].setVisible(true);
			this.tooltipResourceIconFrames[resourceIndex].setTexture("ToolTipSupplyIcon", this.ui.rootFrame);
			this.ui.rootFrame.setText(this.tooltipResourceTextFrames[resourceIndex], Integer.toString(foodCost));
			resourceIndex++;
		}
		if (manaCost != 0) {
			this.tooltipResourceFrames[resourceIndex].setVisible(true);
			this.tooltipResourceIconFrames[resourceIndex].setTexture("ToolTipManaIcon", this.ui.rootFrame);
			this.ui.rootFrame.setText(this.tooltipResourceTextFrames[resourceIndex], Integer.toString(manaCost));
			resourceIndex++;
		}
		for (int i = resourceIndex; i < this.tooltipResourceFrames.length; i++) {
			this.tooltipResourceFrames[i].setVisible(false);
		}
		final boolean hasInfo = (info != null) && !info.isEmpty() && (resourceIndex == 0);
		final boolean hasUberTip = !shownUberTip.isEmpty();
		this.ui.rootFrame.setText(this.tooltipInfoText, hasInfo ? info : "");
		this.tooltipInfoText.setVisible(hasInfo);
		this.tooltipUberTipText.setVisible(hasUberTip);
		float gapsHeight;
		if (hasInfo) {
			this.tooltipUberTipText.addSetPoint(this.uberTipBelowInfoSetPoint);
			gapsHeight = GAP_BELOW_NAME + (hasUberTip ? GAP_BELOW_INFO : 0);
		}
		else if (resourceIndex != 0) {
			this.tooltipUberTipText.addSetPoint(this.uberTipWithResourcesSetPoint);
			gapsHeight = COSTS_ROW + (hasUberTip ? GAP_BELOW_NAME : 0);
		}
		else {
			this.tooltipUberTipText.addSetPoint(this.uberTipNoResourcesSetPoint);
			gapsHeight = hasUberTip ? GAP_BELOW_NAME : 0;
		}
		// A touch screen has no keyboard: the gold key marks of the names go.
		this.ui.rootFrame.setText(this.tooltipText,
				TouchInputRouter.isActive() ? HOTKEY_MARK.matcher(toolTip).replaceAll("") : toolTip);
		this.ui.tooltipFrame.setHeight(this.tooltipText.getPredictedViewportHeight()
				+ (hasInfo ? this.tooltipInfoText.getPredictedViewportHeight() : 0)
				+ (hasUberTip ? this.tooltipUberTipText.getPredictedViewportHeight() : 0)
				+ GameUI.convertY(this.ui.uiViewport, TEXT_TOP + gapsHeight + TEXT_BOTTOM));
		final float insets = 2 * GameUI.convertX(this.ui.uiViewport, TOOLTIP_INSET);
		float width = this.tooltipText.getPredictedViewportWidth() + insets;
		if (enhanced) {
			width = Math.max(width, GameUI.convertX(this.ui.uiViewport, TOOLTIP_WIDTH));
		}
		else {
			if (hasInfo) {
				width = Math.max(width, this.tooltipInfoText.getPredictedViewportWidth() + insets);
			}
			if (hasUberTip) {
				width = Math.max(width, this.tooltipUberTipText.getPredictedViewportWidth() + insets);
			}
		}
		final AbstractRenderableFrame box = (AbstractRenderableFrame) this.ui.tooltipFrame;
		box.setWidth(width);
		box.clearFramePointAssignments();
		box.addAnchor(new AnchorDefinition(FramePoint.BOTTOMRIGHT, GameUI.convertX(this.ui.uiViewport, TOOLTIP_RIGHT),
				GameUI.convertY(this.ui.uiViewport, TOOLTIP_BOTTOM)));
		box.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
		if (!enhanced) {
			if (resourceIndex != 0) {
				final Rectangle lastCost = this.tooltipResourceTextFrames[resourceIndex - 1].getRenderBounds();
				width = Math.max(width, (lastCost.x + lastCost.width) - this.tooltipText.getRenderBounds().x + insets);
				box.setWidth(width);
			}
			final Rectangle console = this.ui.rootFrame.getRenderBounds();
			final Rectangle over = ((AbstractRenderableFrame) frame).getRenderBounds();
			final float left = Math.min(this.pointerX + GameUI.convertX(this.ui.uiViewport, SIMPLE_TIP_RIGHT_OF_POINTER),
					(console.x + console.width) - width);
			box.clearFramePointAssignments();
			box.addAnchor(new AnchorDefinition(FramePoint.BOTTOMLEFT, left - console.x,
					(over.y + over.height + GameUI.convertY(this.ui.uiViewport, SIMPLE_TIP_OVER_FRAME)) - console.y));
			box.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
		}
		this.ui.tooltipFrame.setVisible(true);
	}

	/** The requirement lines the command card puts before a description; they stay without the enhanced tool tips. */
	private String requirementLines(final String uberTip) {
		final int end = uberTip.indexOf("|n|r");
		if ((end < 0) || !uberTip.startsWith(decorated("REQUIRESTOOLTIP"))) {
			return "";
		}
		return uberTip.substring(0, end);
	}
}
