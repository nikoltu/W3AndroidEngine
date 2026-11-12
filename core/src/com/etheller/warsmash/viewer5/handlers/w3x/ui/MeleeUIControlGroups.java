package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.TextJustify;
import com.etheller.warsmash.parsers.fdf.datamodel.Vector4Definition;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleFrame;
import com.etheller.warsmash.parsers.fdf.frames.SimpleStatusBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.SingleStringFrame;
import com.etheller.warsmash.parsers.fdf.frames.SpriteFrame;
import com.etheller.warsmash.parsers.fdf.frames.TextureFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.IconUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.MultiSelectionIconListener;

/**
 * The shortcuts to the player's own units: control groups, the hero bar and the idle
 * worker button.
 */
final class MeleeUIControlGroups {
	// Hero buttons down the left edge of the console, below the upper button bar, in 4:3 interface units.
	private static final int HERO_BAR_SIZE = 7;
	/** F1 to F3 select the first three heroes of the bar. */
	static final int HERO_HOTKEYS = 3;
	private static final float HERO_BAR_LEFT = 0.004f;
	private static final float HERO_BAR_TOP = 0.034f;
	private static final float HERO_BUTTON_SIZE = 0.038f;
	private static final float HERO_BUTTON_STEP = 0.056f;
	private static final float IDLE_WORKER_BOTTOM = 0.17f;
	private static final long IDLE_WORKER_HOLD_MILLIS = 500;
	// Measured on 1.29: the skill point badge shows 0.32 of the button wide (its art has a clear margin), in the
	// button's bottom right corner.
	private static final float HERO_POINTS_OVERLAY_RATIO = 0.44f;
	private static final float HERO_POINTS_FONT_RATIO = 0.25f;
	// Measured on 1.29: a hurt hero's button turns red three times, for half a second each second, and its minimap
	// mark flashes red five times, every 0.635 s; a hit while it flashes does not start it again.
	private static final long HERO_ALERT_MILLIS = 2900;
	private static final long HERO_ALERT_BUTTON_PERIOD = 1000;
	private static final long HERO_ALERT_BUTTON_START = 100;
	private static final long HERO_ALERT_BUTTON_END = 600;
	private static final long HERO_ALERT_MINIMAP_PERIOD = 635;
	private static final long HERO_ALERT_MINIMAP_SHOWN = 300;
	// The red button keeps its red channel and a quarter of the others.
	private static final float HERO_ALERT_TINT = 0.25f;
	private final Map<CUnit, Long> heroAlertStarts = new HashMap<>();
	private final HeroBarIcon[] heroBarIcons = new HeroBarIcon[HERO_BAR_SIZE];
	private final List<CUnit> heroBarUnits = new ArrayList<>();
	// Calibration: the original's default count is not measured; three is the usual hero limit of a player.
	private int reservedLocalHeroButtons = 3;
	private int lastHeroBarPressed = -1;
	private int idleWorkerCycle;
	private long idleWorkerPressMillis;
	private long lastHeroBarPressMillis;
	private static final int CONTROL_GROUP_COUNT = 10;
	private static final long CONTROL_GROUP_DOUBLE_PRESS_MILLIS = 400;
	private int lastControlGroupPressed = -1;
	private ControlGroupBar controlGroupBar;
	private long lastControlGroupPressMillis;

	private int lastTownHallHandleId = -1;

	final MeleeUI ui;

	MeleeUIControlGroups(final MeleeUI ui) {
		this.ui = ui;
	}

	public void setReservedLocalHeroButtons(final int reserved) {
		this.reservedLocalHeroButtons = Math.max(0, reserved);
	}

	public void assignControlGroup(final int group, final boolean add) {
		while (this.ui.controlGroups.size() < CONTROL_GROUP_COUNT) {
			this.ui.controlGroups.add(new ArrayList<>());
		}
		final List<RenderUnit> members = this.ui.controlGroups.get(group);
		if (!add) {
			members.clear();
		}
		for (final RenderUnit unit : this.ui.selectedUnits) {
			if (!members.contains(unit)
					&& (unit.getSimulationUnit().getPlayerIndex() == this.ui.war3MapViewer.getLocalPlayerIndex())) {
				members.add(unit);
			}
		}
	}

	void createHeroBar() {
		final SimpleFrame heroBar = (SimpleFrame) this.ui.rootFrame.createSimpleFrame("SmashHeroBar", this.ui.rootFrame,
				0);
		this.ui.heroBar = heroBar;
		final MultiSelectionIconListener listener = new MultiSelectionIconListener() {
			@Override
			public void multiSelectIconClicked(final int index) {
				heroBarClicked(index);
			}

			@Override
			public void multiSelectIconPress(final int index) {
			}

			@Override
			public void multiSelectIconRelease(final int index) {
			}
		};
		final float size = GameUI.convertY(this.ui.uiViewport, HERO_BUTTON_SIZE);
		final BitmapFont pointsFont = this.ui.rootFrame.generateFont(HERO_BUTTON_SIZE * HERO_POINTS_FONT_RATIO);
		final String glowModel = this.ui.rootFrame.hasSkinField("HeroBarPointModel")
				? this.ui.rootFrame.getSkinField("HeroBarPointModel")
				: null;
		for (int i = 0; i < HERO_BAR_SIZE; i++) {
			final HeroBarIcon icon = new HeroBarIcon("SmashHeroButton", heroBar, listener, i);
			final TextureFrame iconTexture = new TextureFrame("SmashHeroButtonBackdrop", icon, false,
					new Vector4Definition(0, 1, 0, 1));
			final SimpleStatusBarFrame hpBar = new SimpleStatusBarFrame("SmashHeroHpBar" + i, heroBar, true, true,
					3.0f);
			hpBar.getBarFrame().setTexture("SimpleHpBarConsole", this.ui.rootFrame);
			hpBar.getBorderFrame().setTexture("Textures\\Black32.blp", this.ui.rootFrame);
			hpBar.setWidth(size);
			hpBar.setHeight(size * MultiSelectionIcon.HP_BAR_HEIGHT_RATIO);
			hpBar.addSetPoint(new SetPoint(FramePoint.TOP, iconTexture, FramePoint.BOTTOM, 0,
					-size * MultiSelectionIcon.HP_BAR_SPACING_RATIO));
			final SimpleStatusBarFrame manaBar = new SimpleStatusBarFrame("SmashHeroManaBar" + i, heroBar, true, true,
					3.0f);
			manaBar.getBarFrame().setTexture("SimpleManaBarConsole", this.ui.rootFrame);
			manaBar.getBorderFrame().setTexture("Textures\\Black32.blp", this.ui.rootFrame);
			manaBar.setWidth(size);
			manaBar.setHeight(size * MultiSelectionIcon.HP_BAR_HEIGHT_RATIO);
			manaBar.addSetPoint(new SetPoint(FramePoint.TOP, hpBar, FramePoint.BOTTOM, 0,
					-size * MultiSelectionIcon.HP_BAR_SPACING_RATIO));
			manaBar.getBarFrame().setColor(0f, 0f, 1f, 1f);
			icon.set(iconTexture, hpBar, manaBar);
			iconTexture.addSetPoint(new SetPoint(FramePoint.CENTER, icon, FramePoint.CENTER, 0, 0));
			icon.setWidth(size);
			icon.setHeight(size);
			iconTexture.setWidth(size);
			iconTexture.setHeight(size);
			final TextureFrame pointsOverlay = new TextureFrame("SmashHeroButtonNumberOverlay" + i, icon, true, null);
			pointsOverlay.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT, iconTexture, FramePoint.BOTTOMRIGHT, 0, 0));
			pointsOverlay.setWidth(size * HERO_POINTS_OVERLAY_RATIO);
			pointsOverlay.setHeight(size * HERO_POINTS_OVERLAY_RATIO);
			pointsOverlay.setTexture("CommandButtonNumberOverlay", this.ui.rootFrame);
			final SingleStringFrame pointsText = new SingleStringFrame("SmashHeroButtonNumberOverlayText" + i,
					pointsOverlay, Color.WHITE, TextJustify.CENTER, TextJustify.MIDDLE, pointsFont);
			pointsText.setSetAllPoints(true);
			SpriteFrame pointsGlow = null;
			if (glowModel != null) {
				pointsGlow = (SpriteFrame) this.ui.rootFrame.createFrameByType("SPRITE", "SmashHeroButtonGlow" + i,
						heroBar, "", 0);
				pointsGlow.addSetPoint(new SetPoint(FramePoint.CENTER, iconTexture, FramePoint.CENTER, 0, 0));
				this.ui.rootFrame.setSpriteFrameModel(pointsGlow, glowModel);
				pointsGlow.setWidth(size);
				pointsGlow.setHeight(size);
				// Behind the 2D interface, as in the original: the button covers the inner half of the glow.
				pointsGlow.setZDepth(-1.0f);
				pointsGlow.setVisible(false);
			}
			icon.setSkillPointFrames(pointsOverlay, pointsText, pointsGlow);
			pointsOverlay.setVisible(false);
			pointsText.setVisible(false);
			heroBar.add(icon);
			if (pointsGlow != null) {
				heroBar.add(pointsGlow);
			}
			icon.setVisible(false);
			this.heroBarIcons[i] = icon;
		}
		this.ui.idleWorkerIcon = createIdleWorkerButton(heroBar, size, pointsFont);
		anchorHeroBar();
	}

	private HeroBarIcon createIdleWorkerButton(final SimpleFrame parent, final float size,
			final BitmapFont countFont) {
		final MultiSelectionIconListener listener = new MultiSelectionIconListener() {
			@Override
			public void multiSelectIconClicked(final int index) {
				final long heldMillis = TimeUtils.millis() - MeleeUIControlGroups.this.idleWorkerPressMillis;
				idleWorkerClicked(heldMillis >= IDLE_WORKER_HOLD_MILLIS);
			}

			@Override
			public void multiSelectIconPress(final int index) {
				MeleeUIControlGroups.this.idleWorkerPressMillis = TimeUtils.millis();
			}

			@Override
			public void multiSelectIconRelease(final int index) {
			}
		};
		final HeroBarIcon icon = new HeroBarIcon("SmashIdleWorkerButton", parent, listener, 0);
		final TextureFrame iconTexture = new TextureFrame("SmashIdleWorkerBackdrop", icon, false,
				new Vector4Definition(0, 1, 0, 1));
		final SimpleStatusBarFrame hpBar = new SimpleStatusBarFrame("SmashIdleWorkerHpBar", parent, true, true, 3.0f);
		final SimpleStatusBarFrame manaBar = new SimpleStatusBarFrame("SmashIdleWorkerManaBar", parent, true, true,
				3.0f);
		hpBar.setVisible(false);
		manaBar.setVisible(false);
		icon.set(iconTexture, hpBar, manaBar);
		iconTexture.addSetPoint(new SetPoint(FramePoint.CENTER, icon, FramePoint.CENTER, 0, 0));
		icon.setWidth(size);
		icon.setHeight(size);
		iconTexture.setWidth(size);
		iconTexture.setHeight(size);
		iconTexture.setTexture(this.ui.rootFrame.loadTexture(this.ui.rootFrame.getSkinField("IdlePeon")));
		// The count of idle workers, on the number overlay of the hero buttons (1.29).
		final TextureFrame countOverlay = new TextureFrame("SmashIdleWorkerNumberOverlay", icon, true, null);
		countOverlay.addSetPoint(new SetPoint(FramePoint.BOTTOMRIGHT, iconTexture, FramePoint.BOTTOMRIGHT, 0, 0));
		countOverlay.setWidth(size * HERO_POINTS_OVERLAY_RATIO);
		countOverlay.setHeight(size * HERO_POINTS_OVERLAY_RATIO);
		countOverlay.setTexture("CommandButtonNumberOverlay", this.ui.rootFrame);
		final SingleStringFrame countText = new SingleStringFrame("SmashIdleWorkerNumberOverlayText", countOverlay,
				Color.WHITE, TextJustify.CENTER, TextJustify.MIDDLE, countFont);
		countText.setSetAllPoints(true);
		icon.setSkillPointFrames(countOverlay, countText, null);
		countOverlay.setVisible(false);
		countText.setVisible(false);
		icon.setToolTip(this.ui.rootFrame.getTemplates().getDecoratedString("IDLE_PEON"));
		icon.setInfoTip(this.ui.rootFrame.getTemplates().getDecoratedString("IDLE_PEON_DESC"));
		icon.setUberTip("");
		parent.add(icon);
		icon.setVisible(false);
		return icon;
	}

	/**
	 * Hero buttons and the idle worker button sit at the real left screen edge, as in the original game, not at the
	 * left edge of the centered 4:3 area the console is laid out in; called again when the window is resized.
	 */
	void anchorHeroBar() {
		final float screenLeft = (this.ui.uiViewport.getMinWorldWidth() - this.ui.uiViewport.getWorldWidth()) / 2;
		for (int i = 0; i < HERO_BAR_SIZE; i++) {
			final MultiSelectionIcon icon = this.heroBarIcons[i];
			if (icon == null) {
				continue;
			}
			icon.clearFramePointAssignments();
			icon.addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.consoleUI, FramePoint.TOPLEFT,
					Math.min(0, screenLeft) + GameUI.convertX(this.ui.uiViewport, HERO_BAR_LEFT),
					-GameUI.convertY(this.ui.uiViewport, HERO_BAR_TOP + (i * HERO_BUTTON_STEP))));
		}
		if (this.ui.idleWorkerIcon != null) {
			this.ui.idleWorkerIcon.clearFramePointAssignments();
			this.ui.idleWorkerIcon.addSetPoint(new SetPoint(FramePoint.BOTTOMLEFT, this.ui.consoleUI,
					FramePoint.BOTTOMLEFT, Math.min(0, screenLeft) + GameUI.convertX(this.ui.uiViewport, HERO_BAR_LEFT),
					GameUI.convertY(this.ui.uiViewport, IDLE_WORKER_BOTTOM)));
		}
	}

	/** An enemy hurt the unit: a hero of the bar starts its alert. */
	void unitAttacked(final CUnit unit, final CUnit attacker) {
		if (!unit.isHero() || (attacker == null) || (attacker.getPlayerIndex() == unit.getPlayerIndex())) {
			return;
		}
		final CPlayer owner = this.ui.war3MapViewer.simulation.getPlayer(unit.getPlayerIndex());
		final int localPlayerIndex = this.ui.localPlayer.getId();
		if (owner.hasAlliance(attacker.getPlayerIndex(), CAllianceType.PASSIVE)
				|| ((owner.getId() != localPlayerIndex)
						&& !owner.hasAlliance(localPlayerIndex, CAllianceType.SHARED_CONTROL))) {
			return;
		}
		final long now = TimeUtils.millis();
		final Long start = this.heroAlertStarts.get(unit);
		if ((start == null) || ((now - start) >= HERO_ALERT_MILLIS)) {
			this.heroAlertStarts.put(unit, now);
		}
	}

	private long heroAlertTime(final CUnit hero, final long now) {
		final Long start = this.heroAlertStarts.get(hero);
		if (start == null) {
			return -1;
		}
		final long elapsed = now - start;
		if ((elapsed >= HERO_ALERT_MILLIS) || hero.isDead()) {
			this.heroAlertStarts.remove(hero);
			return -1;
		}
		return elapsed;
	}

	boolean isHeroAlertMinimapShown(final CUnit hero) {
		final long elapsed = heroAlertTime(hero, TimeUtils.millis());
		return (elapsed >= 0) && ((elapsed % HERO_ALERT_MINIMAP_PERIOD) < HERO_ALERT_MINIMAP_SHOWN);
	}

	private boolean isHeroAlertButtonShown(final CUnit hero, final long now) {
		final long elapsed = heroAlertTime(hero, now);
		if (elapsed < 0) {
			return false;
		}
		final long phase = elapsed % HERO_ALERT_BUTTON_PERIOD;
		return (phase >= HERO_ALERT_BUTTON_START) && (phase < HERO_ALERT_BUTTON_END);
	}

	void updateHeroBar() {
		this.heroBarUnits.clear();
		for (final CUnit hero : this.ui.localPlayer.getHeroes()) {
			if (this.heroBarUnits.size() >= HERO_BAR_SIZE) {
				break;
			}
			this.heroBarUnits.add(hero);
		}
		// Heroes of players sharing their units come after the slots kept for the local player's own heroes.
		final int localPlayerIndex = this.ui.localPlayer.getId();
		boolean alliedPadding = true;
		for (int playerIndex = 0; playerIndex < this.ui.war3MapViewer.getGameData().getMaxPlayers(); playerIndex++) {
			final CPlayer player = this.ui.war3MapViewer.simulation.getPlayer(playerIndex);
			if ((playerIndex == localPlayerIndex) || !player.hasAlliance(localPlayerIndex, CAllianceType.SHARED_CONTROL)) {
				continue;
			}
			for (final CUnit hero : player.getHeroes()) {
				if (alliedPadding) {
					alliedPadding = false;
					while (this.heroBarUnits.size() < Math.min(this.reservedLocalHeroButtons, HERO_BAR_SIZE)) {
						this.heroBarUnits.add(null);
					}
				}
				if (this.heroBarUnits.size() < HERO_BAR_SIZE) {
					this.heroBarUnits.add(hero);
				}
			}
		}
		final long now = TimeUtils.millis();
		final boolean barShown = this.ui.heroBar.isVisible();
		for (int i = 0; i < HERO_BAR_SIZE; i++) {
			final HeroBarIcon icon = this.heroBarIcons[i];
			final boolean used = (i < this.heroBarUnits.size()) && (this.heroBarUnits.get(i) != null);
			if (icon.isVisible() != used) {
				icon.setVisible(used);
			}
			if (used) {
				final CUnit hero = this.heroBarUnits.get(i);
				final IconUI unitUI = this.ui.war3MapViewer.getAbilityDataUI().getUnitUI(hero.getTypeId());
				icon.setTexture(hero.isDead() ? unitUI.getIconDisabled() : unitUI.getIcon());
				if (isHeroAlertButtonShown(hero, now)) {
					icon.setIconColor(1f, HERO_ALERT_TINT, HERO_ALERT_TINT, 1f);
				}
				else {
					icon.setIconColor(1f, 1f, 1f, 1f);
				}
				// Measured on 1.29: a dead hero's button shows no skill points.
				icon.setSkillPoints(hero.isDead() ? 0 : hero.getHeroData().getSkillPoints(), barShown);
				icon.setHero(i, hero, this.ui.rootFrame.getTemplates());
				icon.setLifeRatioRemaining(hero.isDead() ? 0 : hero.getLife() / hero.getMaximumLife());
				// Measured on 1.29: a dead hero's button has no bars.
				icon.setLifeBarVisible(!hero.isDead());
				final boolean useManaBar = (hero.getMaximumMana() > 0) && !hero.isDead();
				icon.setManaBarVisible(useManaBar);
				if (useManaBar) {
					icon.setManaRatioRemaining(hero.isDead() ? 0 : hero.getMana() / hero.getMaximumMana());
				}
			}
			else {
				icon.setSkillPoints(0, barShown);
			}
		}
	}

	/**
	 * Idle workers: the local player's workers standing without an order, not inside a mine or burrow.
	 */
	void updateIdleWorkers() {
		this.ui.idleWorkers.clear();
		final int localPlayerIndex = this.ui.war3MapViewer.getLocalPlayerIndex();
		for (final CUnit unit : this.ui.war3MapViewer.simulation.getUnits()) {
			if ((unit.getPlayerIndex() == localPlayerIndex) && !unit.isDead() && !unit.isHidden()
					&& !unit.isBuilding() && unit.getClassifications().contains(CUnitClassification.PEON)
					&& ((unit.getCurrentBehavior() == null) || (unit.getCurrentBehavior() == unit.getStopBehavior()))) {
				this.ui.idleWorkers.add(unit);
			}
		}
		final boolean show = !this.ui.idleWorkers.isEmpty();
		if (this.ui.idleWorkerIcon.isVisible() != show) {
			this.ui.idleWorkerIcon.setVisible(show);
		}
		this.ui.idleWorkerIcon.setCount(this.ui.idleWorkers.size());
	}

	/**
	 * Selects the next idle worker in turn and centers the camera on it (button or F8); with Ctrl, or a held press on a
	 * touch screen, it selects all idle workers instead, as in the original game.
	 */
	void idleWorkerClicked(final boolean held) {
		if (this.ui.idleWorkers.isEmpty()) {
			return;
		}
		if (held || MeleeUIInput.isControlDown()) {
			final List<RenderWidget> all = new ArrayList<>();
			for (final CUnit worker : this.ui.idleWorkers) {
				final RenderUnit renderUnit = this.ui.war3MapViewer.getRenderPeer(worker);
				if (renderUnit != null) {
					all.add(renderUnit);
				}
			}
			this.ui.input.selectWidgets(all);
			this.ui.war3MapViewer.doSelectUnit(all);
			return;
		}
		this.idleWorkerCycle = (this.idleWorkerCycle + 1) % this.ui.idleWorkers.size();
		final CUnit worker = this.ui.idleWorkers.get(this.idleWorkerCycle);
		final RenderUnit renderUnit = this.ui.war3MapViewer.getRenderPeer(worker);
		if (renderUnit == null) {
			return;
		}
		final List<RenderWidget> selection = Arrays.asList(renderUnit);
		this.ui.input.selectWidgets(selection);
		this.ui.war3MapViewer.doSelectUnit(selection);
		this.ui.cameraManager.jumpTo(worker.getX(), worker.getY());
	}

	/** The hero's key works as its button: false when the bar has no living hero there. */
	boolean heroHotkeyPressed(final int index) {
		return heroBarClicked(index);
	}

	/** A hero button selects its hero, a second press soon after also centers the camera on it. */
	private boolean heroBarClicked(final int index) {
		if (index >= this.heroBarUnits.size()) {
			return false;
		}
		final CUnit hero = this.heroBarUnits.get(index);
		final RenderUnit renderUnit = hero == null ? null : this.ui.war3MapViewer.getRenderPeer(hero);
		if ((renderUnit == null) || hero.isDead()) {
			return false;
		}
		if (this.ui.activeCommand != null) {
			this.ui.commands.useActiveCommandOnUnit(MeleeUIInput.isShiftDown(), renderUnit);
			return true;
		}
		final long now = TimeUtils.millis();
		final boolean doublePress = (index == this.lastHeroBarPressed)
				&& ((now - this.lastHeroBarPressMillis) < CONTROL_GROUP_DOUBLE_PRESS_MILLIS);
		this.lastHeroBarPressed = index;
		this.lastHeroBarPressMillis = now;
		final List<RenderWidget> selection = Arrays.asList(renderUnit);
		this.ui.input.selectWidgets(selection);
		this.ui.war3MapViewer.doSelectUnit(selection);
		if (doublePress) {
			this.ui.cameraManager.jumpTo(hero.getX(), hero.getY());
		}
		return true;
	}

	/**
	 * Selects the units of a control group; pressing the same group again quickly also centers the camera on it.
	 */
	public boolean selectControlGroup(final int group) {
		if (group >= this.ui.controlGroups.size()) {
			return false;
		}
		final List<RenderUnit> members = this.ui.controlGroups.get(group);
		members.removeIf(unit -> unit.getSimulationUnit().isDead());
		if (members.isEmpty()) {
			return false;
		}
		final long now = TimeUtils.millis();
		final boolean doublePress = (group == this.lastControlGroupPressed)
				&& ((now - this.lastControlGroupPressMillis) < CONTROL_GROUP_DOUBLE_PRESS_MILLIS);
		this.lastControlGroupPressed = group;
		this.lastControlGroupPressMillis = now;
		final List<RenderWidget> widgets = new ArrayList<>(members);
		this.ui.input.selectWidgets(widgets);
		this.ui.war3MapViewer.doSelectUnit(widgets);
		if (doublePress) {
			centerOnControlGroup(members);
		}
		return true;
	}

	private void centerOnControlGroup(final List<RenderUnit> members) {
		float x = 0;
		float y = 0;
		for (final RenderUnit unit : members) {
			x += unit.getSimulationUnit().getX();
			y += unit.getSimulationUnit().getY();
		}
		this.ui.cameraManager.jumpTo(x / members.size(), y / members.size());
	}

	/** The unit a group is shown by: a hero before other units, then the higher level, then the dearer one. */
	static CUnit strongestUnit(final List<RenderUnit> members) {
		CUnit best = null;
		for (final RenderUnit member : members) {
			final CUnit unit = member.getSimulationUnit();
			if (unit.isDead()) {
				continue;
			}
			if ((best == null) || (compareStrength(unit, best) > 0)) {
				best = unit;
			}
		}
		return best;
	}

	private static int compareStrength(final CUnit a, final CUnit b) {
		final boolean heroA = a.getHeroData() != null;
		final boolean heroB = b.getHeroData() != null;
		if (heroA != heroB) {
			return heroA ? 1 : -1;
		}
		final int levelA = heroA ? a.getHeroData().getHeroLevel() : a.getUnitType().getLevel();
		final int levelB = heroB ? b.getHeroData().getHeroLevel() : b.getUnitType().getLevel();
		if (levelA != levelB) {
			return Integer.compare(levelA, levelB);
		}
		return Integer.compare(a.getUnitType().getGoldCost() + a.getUnitType().getLumberCost(),
				b.getUnitType().getGoldCost() + b.getUnitType().getLumberCost());
	}

	public List<RenderUnit> getControlGroup(final int group) {
		if (group >= this.ui.controlGroups.size()) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(this.ui.controlGroups.get(group));
	}

	ControlGroupBar getControlGroupBar() {
		if ((this.controlGroupBar == null) && TouchInputRouter.isActive()) {
			final String buttonTexture = this.ui.rootFrame.trySkinField("UpperMenuButtonTexture");
			this.controlGroupBar = new ControlGroupBar(this.ui.uiViewport,
					buttonTexture == null ? null : this.ui.rootFrame.loadTexture(buttonTexture),
					this.ui.rootFrame.loadTexture("ReplaceableTextures\\CommandButtons\\BTNSkillz.blp"),
					this.ui.rootFrame.generateFont(0.01f));
		}
		return this.controlGroupBar;
	}

	public int touchOverlaySlotAt(final int screenX, final int screenY) {
		final ControlGroupBar bar = getControlGroupBar();
		return bar == null ? -1 : bar.slotAt(screenX, screenY);
	}

	public void selectControlGroupSlot(final int slot) {
		switch (slot) {
		case ControlGroupBar.PLUS_SLOT:
			assignFirstFreeControlGroup();
			return;
		default:
			selectControlGroup(ControlGroupBar.groupOfSlot(slot));
		}
	}

	public void assignControlGroupSlot(final int slot, final boolean add) {
		if (slot == ControlGroupBar.PLUS_SLOT) {
			assignFirstFreeControlGroup();
			return;
		}
		assignControlGroup(ControlGroupBar.groupOfSlot(slot), add);
	}

	/**
	 * Backspace centers the camera on a town hall of the local player, the next one on each press, in the order they
	 * were built. It selects nothing.
	 */
	boolean jumpToNextTownHall() {
		CUnit first = null;
		CUnit next = null;
		for (final CUnit unit : this.ui.war3MapViewer.simulation.getUnits()) {
			if ((unit.getPlayerIndex() != this.ui.war3MapViewer.getLocalPlayerIndex()) || unit.isDead()
					|| unit.isHidden() || unit.isConstructing() || !unit.isUnitType(CUnitTypeJass.TOWNHALL)) {
				continue;
			}
			if ((first == null) || (unit.getHandleId() < first.getHandleId())) {
				first = unit;
			}
			if ((unit.getHandleId() > this.lastTownHallHandleId)
					&& ((next == null) || (unit.getHandleId() < next.getHandleId()))) {
				next = unit;
			}
		}
		final CUnit townHall = next != null ? next : first;
		if (townHall == null) {
			return false;
		}
		this.lastTownHallHandleId = townHall.getHandleId();
		this.ui.cameraManager.jumpTo(townHall.getX(), townHall.getY());
		return true;
	}

	private void assignFirstFreeControlGroup() {
		for (int slot = 0; slot < ControlGroupBar.SLOT_COUNT; slot++) {
			final int group = ControlGroupBar.groupOfSlot(slot);
			if (getControlGroup(group).isEmpty()) {
				assignControlGroup(group, false);
				return;
			}
		}
	}
}
