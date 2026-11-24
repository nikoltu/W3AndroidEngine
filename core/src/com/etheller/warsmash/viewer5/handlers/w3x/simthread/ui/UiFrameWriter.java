package com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui;

import java.util.Arrays;
import java.util.List;

import com.etheller.warsmash.parsers.fdf.datamodel.FrameTemplateEnvironment;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.ObjectData;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.IconUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.ItemUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.UnitIconUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.commandbuttons.CommandCardActivationReceiverPreviewCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.UiFrameState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CGameplayConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit.QueueItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold.CAbilityCargoHold;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CPrimaryAttribute;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilityNeutralBuilding;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CUpgradeClass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

/**
 * Fills {@link SimFrame#ui} for the local player's current selection, from simulation state only. It is called from
 * the same place that writes the world frame, after the world part, before the frame is published, on the thread that
 * runs the steps. It reads the simulation and the object data tables, changes nothing in them, and keeps no state
 * about the simulation between steps except the layout counter of the command card.
 * <p>
 * What the render side owns and hands in: the selection (handles, which one is active, the sub menu that is open,
 * whether a command waits for its target), the control groups, the unit under the pointer and the part of the
 * console the pointer is over. These go through the setters, which may be called from any thread; the writer uses
 * the latest values at each step.
 * <p>
 * Coverage: selection and its icons, the hero bar, control group sizes and leaders, idle workers, details of the
 * active unit, the unit under the pointer and each hero (name, level, life, mana, armor, damage, hero level progress
 * and attributes, skill points, buffs, inventory, cargo, build or training queue), the command card with its tool
 * tips, the player panel with the team resources, the tip of the console part under the pointer and of the widget
 * under the pointer.
 */
public final class UiFrameWriter {
	/** Number of inventory slots the frame carries. */
	private static final int ITEM_SLOTS = UnitDetailsModel.INVENTORY_SLOTS;
	/** Slots of the hero bar; the slots kept for the local player's heroes are padded up to this count at most. */
	private static final int HERO_BAR_SIZE = UiFrameState.MAX_HEROES;

	private final CommandCardModelBuilder cardBuilder;
	private final TooltipModelBuilder tooltipBuilder;
	private final PlayerPanelWriter panelWriter = new PlayerPanelWriter();
	private final FrameTemplateEnvironment templates;
	private final AbilityDataUI abilityDataUI;
	private final CommandCardActivationReceiverPreviewCallback itemPreview = new CommandCardActivationReceiverPreviewCallback();

	private volatile int localPlayerIndex;
	private volatile int viewingPlayerIndex = -1;

	private volatile int[] selection = new int[0];
	private volatile int activeInput;
	private volatile int subMenuOrderId;
	private volatile int exitMenuOrderId;
	private volatile boolean targeting;
	private volatile int hoveredUnit;
	private volatile int hoveredPanelTip = -1;
	private volatile int reservedHeroButtons = 3;
	private volatile int[][] controlGroupMembers = new int[UiFrameState.CONTROL_GROUPS][0];

	private int layoutVersion;
	private long lastLayoutHash;

	private long steps;
	private long totalNanos;
	private long maxNanos;

	/**
	 * @param abilityDataUI interface data of abilities, units, items and upgrades (icons as paths, texts)
	 * @param templates     the interface's string templates
	 * @param miscData      the game's misc data, may be null
	 * @param unitData      unit object data (caster ranks), may be null
	 */
	public UiFrameWriter(final AbilityDataUI abilityDataUI, final FrameTemplateEnvironment templates,
			final DataTable miscData, final ObjectData unitData, final int localPlayerIndex) {
		this.templates = templates;
		this.abilityDataUI = abilityDataUI;
		this.cardBuilder = new CommandCardModelBuilder(abilityDataUI, templates);
		this.tooltipBuilder = new TooltipModelBuilder(templates, miscData, unitData, abilityDataUI);
		this.localPlayerIndex = localPlayerIndex;
	}

	public CommandCardModelBuilder getCardBuilder() {
		return this.cardBuilder;
	}

	public TooltipModelBuilder getTooltipBuilder() {
		return this.tooltipBuilder;
	}

	public void setLocalPlayerIndex(final int localPlayerIndex) {
		this.localPlayerIndex = localPlayerIndex;
	}

	/** The player whose resources the panel shows (the local player, or in a replay the one being watched). */
	public void setViewingPlayerIndex(final int viewingPlayerIndex) {
		this.viewingPlayerIndex = viewingPlayerIndex;
	}

	/**
	 * Sets the selection the next steps describe.
	 *
	 * @param handles      handles of the selected units, in selection order
	 * @param count        how many of them
	 * @param activeHandle the active (sub group leader) unit, 0 to take the first
	 */
	public void setSelection(final int[] handles, final int count, final int activeHandle) {
		this.selection = Arrays.copyOf(handles, Math.min(count, UiFrameState.MAX_SELECTED));
		this.activeInput = activeHandle;
	}

	/**
	 * The sub menu that is open on the card: its order id and the order id of the menu above it (what its cancel
	 * button returns to); both 0 for the main card.
	 */
	public void setSubMenu(final int subMenuOrderId, final int exitMenuOrderId) {
		this.subMenuOrderId = subMenuOrderId;
		this.exitMenuOrderId = exitMenuOrderId;
	}

	/** Whether a command waits for its target: the card then shows only its cancel button. */
	public void setTargeting(final boolean targeting) {
		this.targeting = targeting;
	}

	/** The widget under the pointer (a unit, an item or a tree), 0 for none. */
	public void setHoveredUnit(final int handle) {
		this.hoveredUnit = handle;
	}

	/** The console part the pointer is over (a {@link TooltipModelBuilder} constant), -1 for none. */
	public void setHoveredPanelTip(final int tip) {
		this.hoveredPanelTip = tip;
	}

	/** How many hero bar slots are kept for the local player's own heroes before those of allies come. */
	public void setReservedHeroButtons(final int reserved) {
		this.reservedHeroButtons = Math.max(0, reserved);
	}

	/**
	 * The members of every control group, as handles in the order they joined it. The frame carries the first unit,
	 * the size and the unit the group is shown by.
	 */
	public void setControlGroups(final int[][] memberHandles) {
		final int[][] copy = new int[UiFrameState.CONTROL_GROUPS][];
		for (int i = 0; i < copy.length; i++) {
			copy[i] = (memberHandles != null) && (i < memberHandles.length) && (memberHandles[i] != null)
					? memberHandles[i].clone() : new int[0];
		}
		this.controlGroupMembers = copy;
	}

	public long getSteps() {
		return this.steps;
	}

	public long getTotalNanos() {
		return this.totalNanos;
	}

	public long getMaxNanos() {
		return this.maxNanos;
	}

	/** Writes the interface part of the frame, which the caller got from the ring and has not published yet. */
	public void write(final CSimulation game, final SimFrame frame) {
		final long start = System.nanoTime();
		final int local = this.localPlayerIndex;
		final CPlayer localPlayer = (local >= 0) && (local < game.getPlayers().size()) ? game.getPlayer(local) : null;
		final UiFrameState ui = frame.ui;
		final int[] selected = this.selection;
		if (localPlayer != null) {
			writePlayers(game, ui, local);
			this.itemPreview.setup(game.getUnitData(), game.getUpgradeData(), this.templates);
			writeSelection(game, ui, selected, local);
			writeHeroes(game, ui, local);
			writeControlGroups(game, ui);
			writeIdleWorkers(game, ui, local);
			writeHovered(game, ui, local, localPlayer);
			final int viewing = this.viewingPlayerIndex >= 0 ? this.viewingPlayerIndex : local;
			final CPlayer viewingPlayer = viewing < game.getPlayers().size() ? game.getPlayer(viewing) : localPlayer;
			this.panelWriter.write(game, viewingPlayer, frame.gameTimeMillis, ui.panel);
			writePanelTip(game, ui, viewingPlayer);
			this.itemPreview.setup(null, null, null);
		}
		final long took = System.nanoTime() - start;
		this.steps++;
		this.totalNanos += took;
		if (took > this.maxNanos) {
			this.maxNanos = took;
		}
	}

	/** How the players relate to each other and their colours: what the world UI needs of the other players. */
	private static void writePlayers(final CSimulation game, final UiFrameState ui, final int local) {
		final int count = Math.min(UiFrameState.PLAYER_SLOTS, game.getPlayers().size());
		ui.userPlayingMask = 0;
		ui.replayPlayerMask = 0;
		ui.localPlayerEvents = 0;
		ui.neutralAggressiveMask = 0;
		for (int i = 0; i < UiFrameState.PLAYER_SLOTS; i++) {
			ui.alliancePassive[i] = 0;
			ui.allianceSharedControl[i] = 0;
			ui.playerColor[i] = -1;
			ui.playerName[i] = null;
		}
		for (int i = 0; i < count; i++) {
			final CPlayer player = game.getPlayer(i);
			if (player == null) {
				continue;
			}
			ui.playerColor[i] = player.getColor();
			if (i == local) {
				ui.localPlayerEvents = (player.hasPlayerEvents(JassGameEventsWar3.EVENT_PLAYER_KEY_DOWN)
						? UiFrameState.EVENT_KEY_DOWN : 0)
						| (player.hasPlayerEvents(JassGameEventsWar3.EVENT_PLAYER_KEY_UP) ? UiFrameState.EVENT_KEY_UP : 0)
						| (player.hasPlayerEvents(JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN)
								? UiFrameState.EVENT_MOUSE_DOWN : 0)
						| (player.hasPlayerEvents(JassGameEventsWar3.EVENT_PLAYER_MOUSE_UP)
								? UiFrameState.EVENT_MOUSE_UP : 0)
						| (player.hasPlayerEvents(JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE)
								? UiFrameState.EVENT_MOUSE_MOVE : 0);
			}
			ui.playerName[i] = player.getName();
			if ((player.getController() == CMapControl.USER) && (player.getSlotState() == CPlayerSlotState.PLAYING)) {
				ui.userPlayingMask |= 1 << i;
			}
			if ((i < (game.getGameData().getMaxPlayers() - 4)) && (player.getSlotState() == CPlayerSlotState.PLAYING)) {
				ui.replayPlayerMask |= 1 << i;
			}
			if (game.isNeutralAggressive(i)) {
				ui.neutralAggressiveMask |= 1 << i;
			}
			for (int j = 0; j < count; j++) {
				if (player.hasAlliance(j, CAllianceType.PASSIVE)) {
					ui.alliancePassive[i] |= 1 << j;
				}
				if (player.hasAlliance(j, CAllianceType.SHARED_CONTROL)) {
					ui.allianceSharedControl[i] |= 1 << j;
				}
			}
		}
		ui.playersWritten = true;
	}

	private void writeSelection(final CSimulation game, final UiFrameState ui, final int[] selected, final int local) {
		int count = 0;
		CUnit active = null;
		int activeHandle = 0;
		final int wantedActive = this.activeInput;
		for (final int handle : selected) {
			final CUnit unit = game.getUnit(handle);
			if (unit == null) {
				continue;
			}
			ui.selected[count] = handle;
			fillSelected(ui.selectedDetails[count], unit);
			count++;
			// The wanted active unit if it is selected, else the first selected one.
			if ((active == null) || (handle == wantedActive)) {
				active = unit;
				activeHandle = handle;
			}
		}
		ui.selectedCount = count;
		if (active == null) {
			ui.activeUnit = 0;
			ui.commandCard.layoutVersion = this.layoutVersion;
			return;
		}
		ui.activeUnit = activeHandle;
		final boolean multiSelect = count > 1;
		this.cardBuilder.build(game, active, this.subMenuOrderId, this.exitMenuOrderId, multiSelect, this.targeting,
				local, ui.commandCard, ui.active);
		fillDetails(game, active, ui.active, local);
		final long hash = layoutHash(ui.commandCard);
		if (hash != this.lastLayoutHash) {
			this.lastLayoutHash = hash;
			this.layoutVersion++;
		}
		ui.commandCard.layoutVersion = this.layoutVersion;
	}

	private void fillSelected(final SelectedUnitModel out, final CUnit unit) {
		out.unitHandle = unit.getHandleId();
		out.typeId = unit.getTypeId().getValue();
		final IconUI unitUI = this.abilityDataUI.getUnitUI(unit.getUnitType().getTypeId());
		out.iconPath = unitUI == null ? null : unitUI.getIconPath();
		out.tip = unitUI == null ? null : unitUI.getToolTip();
		out.uberTip = unitUI == null ? null : unitUI.getUberTip();
		out.life = unit.getLife();
		out.maxLife = unit.getMaximumLife();
		out.mana = unit.getMana();
		out.maxMana = unit.getMaximumMana();
	}

	private static long layoutHash(final CommandCardModel card) {
		long hash = card.unitHandle;
		hash = (hash * 31) + card.buttonCount;
		for (int i = 0; i < card.buttonCount; i++) {
			final CommandButtonModel button = card.buttons[i];
			hash = (hash * 31) + button.slot;
			hash = (hash * 31) + button.kind;
			hash = (hash * 31) + button.id;
			hash = (hash * 31) + (button.iconPath == null ? 0 : button.iconPath.hashCode());
			hash = (hash * 31) + (button.enabled ? 1 : 0);
			hash = (hash * 31) + (button.autocastable ? 1 : 0);
		}
		return hash;
	}

	/** The hero bar: the local player's heroes, then the heroes of players that share their units. */
	private void writeHeroes(final CSimulation game, final UiFrameState ui, final int local) {
		final int[] handles = new int[HERO_BAR_SIZE];
		int count = 0;
		final List<CUnit> heroes = game.getPlayer(local).getHeroes();
		for (int i = 0; (i < heroes.size()) && (count < HERO_BAR_SIZE); i++) {
			handles[count] = heroes.get(i).getHandleId();
			count++;
		}
		boolean padding = true;
		final int maxPlayers = game.getGameData().getMaxPlayers();
		for (int playerIndex = 0; playerIndex < maxPlayers; playerIndex++) {
			final CPlayer player = game.getPlayer(playerIndex);
			if ((playerIndex == local) || !player.hasAlliance(local, CAllianceType.SHARED_CONTROL)) {
				continue;
			}
			for (final CUnit hero : player.getHeroes()) {
				if (padding) {
					padding = false;
					while (count < Math.min(this.reservedHeroButtons, HERO_BAR_SIZE)) {
						handles[count++] = 0;
					}
				}
				if (count < HERO_BAR_SIZE) {
					handles[count++] = hero.getHandleId();
				}
			}
		}
		ui.heroCount = count;
		for (int i = 0; i < count; i++) {
			ui.heroes[i] = handles[i];
			final UnitDetailsModel details = ui.heroDetails[i];
			final CUnit hero = handles[i] == 0 ? null : game.getUnit(handles[i]);
			if (hero == null) {
				ui.heroes[i] = 0;
				details.clear();
				continue;
			}
			fillDetails(game, hero, details, local);
			this.cardBuilder.collectBuffs(game, hero, local, details);
		}
	}

	private void writeControlGroups(final CSimulation game, final UiFrameState ui) {
		final int[][] members = this.controlGroupMembers;
		for (int group = 0; group < UiFrameState.CONTROL_GROUPS; group++) {
			final int[] handles = members[group];
			ui.controlGroupFirst[group] = handles.length == 0 ? 0 : handles[0];
			ui.controlGroupSize[group] = handles.length;
			CUnit best = null;
			for (final int handle : handles) {
				final CUnit unit = game.getUnit(handle);
				if ((unit == null) || unit.isDead()) {
					continue;
				}
				if ((best == null) || (compareStrength(unit, best) > 0)) {
					best = unit;
				}
			}
			ui.controlGroupLeader[group] = best == null ? 0 : best.getHandleId();
			ui.controlGroupLeaderType[group] = best == null ? 0 : best.getTypeId().getValue();
			final IconUI leaderUI = best == null ? null : this.abilityDataUI.getUnitUI(best.getTypeId());
			ui.controlGroupLeaderIcon[group] = leaderUI == null ? null : leaderUI.getIconPath();
		}
	}

	/** A hero before other units, then the higher level, then the dearer one. */
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

	/** The local player's workers standing without an order, not inside a mine or burrow. */
	private void writeIdleWorkers(final CSimulation game, final UiFrameState ui, final int local) {
		int count = 0;
		for (final CUnit unit : game.getUnits()) {
			if ((unit.getPlayerIndex() == local) && !unit.isDead() && !unit.isHidden() && !unit.isBuilding()
					&& unit.getClassifications().contains(CUnitClassification.PEON)
					&& ((unit.getCurrentBehavior() == null) || (unit.getCurrentBehavior() == unit.getStopBehavior()))) {
				if (count < UiFrameState.MAX_IDLE_WORKERS) {
					ui.idleWorkers[count] = unit.getHandleId();
				}
				count++;
			}
		}
		ui.idleWorkerCount = Math.min(count, UiFrameState.MAX_IDLE_WORKERS);
	}

	private void writeHovered(final CSimulation game, final UiFrameState ui, final int local,
			final CPlayer localPlayer) {
		ui.hoverTip = null;
		final int handle = this.hoveredUnit;
		ui.hoverHandle = handle;
		if (handle == 0) {
			return;
		}
		final CWidget widget = game.getWidget(handle);
		if (widget instanceof CUnit) {
			final CUnit unit = (CUnit) widget;
			fillDetails(game, unit, ui.hovered, local);
			this.cardBuilder.collectBuffs(game, unit, local, ui.hovered);
			ui.hoverTip = this.tooltipBuilder.unitHoverTip(game, unit, localPlayer, game.getGameData().getMaxPlayers());
		}
		else if (widget instanceof CItem) {
			ui.hoverTip = this.tooltipBuilder.itemHoverTip((CItem) widget);
		}
		else if (widget instanceof CDestructable) {
			ui.hoverTip = TooltipModelBuilder.destructableHoverTip((CDestructable) widget);
		}
	}

	private void writePanelTip(final CSimulation game, final UiFrameState ui, final CPlayer viewingPlayer) {
		final int tip = this.hoveredPanelTip;
		ui.panelTipShown = false;
		ui.panelTipIndex = tip;
		ui.panelTip.clear();
		if (tip < 0) {
			return;
		}
		final CUnit active = ui.activeUnit == 0 ? null : game.getUnit(ui.activeUnit);
		ui.panelTipShown = this.tooltipBuilder.panelTip(tip, game, viewingPlayer, active, ui.panelTip);
	}

	/** Fills the statistics of a unit; the buff list is left to the card builder. */
	public void fillDetails(final CSimulation game, final CUnit unit, final UnitDetailsModel d, final int local) {
		d.clearKeepingBuffs();
		d.unitHandle = unit.getHandleId();
		d.typeId = unit.getTypeId().getValue();
		d.playerIndex = unit.getPlayerIndex();
		d.x = unit.getX();
		d.y = unit.getY();
		final CAbilityHero heroData = unit.getHeroData();
		final boolean hero = (heroData != null) && !heroData.isDisabled();
		d.hero = hero;
		d.typeName = unit.getName();
		d.name = hero ? heroData.getProperName() : unit.getName();
		d.level = hero ? heroData.getHeroLevel() : unit.getUnitType().getLevel();
		d.hp = unit.getLife();
		d.maxHp = unit.getMaximumLife();
		d.mana = unit.getMana();
		d.maxMana = unit.getMaximumMana();
		d.maxManaInt = unit.getMaximumMana();
		d.dead = unit.isDead();
		d.hidden = unit.isHidden();
		d.visibleToLocal = unit.isVisible(game, local);
		d.paused = unit.isPaused();
		d.controlShared = game.getPlayer(unit.getPlayerIndex()).hasAlliance(local, CAllianceType.SHARED_CONTROL);
		d.building = unit.isBuilding();
		d.illusion = unit.isIllusion();
		d.constructing = unit.isConstructingOrUpgrading();
		d.upgrading = unit.isUpgrading();
		final IconUI unitUI = this.abilityDataUI.getUnitUI(unit.getTypeId());
		d.iconPath = unitUI == null ? null : unitUI.getIconPath();
		d.armor = unit.getCurrentDefenseDisplay();
		d.armorType = unit.getDefenseType().ordinal();
		d.armorText = this.tooltipBuilder.defenseText(unit);
		final War3ID armorUpgrade = unit.getUnitType().getUpgradeClassToType().get(CUpgradeClass.ARMOR);
		d.armorUpgradeLevel = armorUpgrade == null ? -1
				: game.getPlayer(unit.getPlayerIndex()).getTechtreeUnlocked(armorUpgrade);
		final List<CUnitAttack> attacks = unit.getCurrentAttacks();
		d.attackCount = Math.min(attacks.size(), UnitDetailsModel.ATTACKS);
		if (!attacks.isEmpty()) {
			final CUnitAttack attack = attacks.get(0);
			d.damageMin = attack.getMinDamageDisplay();
			d.damageMax = attack.getMaxDamageDisplay();
			d.attackType = attack.getAttackType().ordinal();
		}
		for (int i = 0; i < d.attackCount; i++) {
			final CUnitAttack attack = attacks.get(i);
			d.attackTypes[i] = attack.getAttackType().ordinal();
			d.attackTexts[i] = TooltipModelBuilder.damageText(attack);
			d.attackShown[i] = attack.isShowUI();
		}
		War3ID weaponUpgrade = unit.getUnitType().getUpgradeClassToType().get(CUpgradeClass.MELEE);
		if (weaponUpgrade == null) {
			weaponUpgrade = unit.getUnitType().getUpgradeClassToType().get(CUpgradeClass.RANGED);
			if (weaponUpgrade == null) {
				weaponUpgrade = unit.getUnitType().getUpgradeClassToType().get(CUpgradeClass.ARTILLERY);
			}
		}
		d.weaponUpgradeLevel = weaponUpgrade == null ? -1
				: game.getPlayer(unit.getPlayerIndex()).getTechtreeUnlocked(weaponUpgrade);
		d.foodMade = unit.getUnitType().getFoodMade();
		if (hero) {
			final CGameplayConstants constants = game.getGameplayConstants();
			final int heroLevel = heroData.getHeroLevel();
			d.heroXp = heroData.getXp() - constants.getNeedHeroXPSum(heroLevel - 1);
			d.heroXpToNextLevel = constants.getNeedHeroXP(heroLevel);
			d.strength = heroData.getStrength().getCurrent();
			d.agility = heroData.getAgility().getCurrent();
			d.intelligence = heroData.getIntelligence().getCurrent();
			d.strengthText = heroData.getStrength().getDisplayText();
			d.agilityText = heroData.getAgility().getDisplayText();
			d.intelligenceText = heroData.getIntelligence().getDisplayText();
			d.skillPoints = heroData.getSkillPoints();
			final CPrimaryAttribute primary = unit.getUnitType().getPrimaryAttribute();
			d.primaryAttribute = primary == CPrimaryAttribute.AGILITY ? UnitDetailsModel.ATTRIBUTE_AGILITY
					: primary == CPrimaryAttribute.INTELLIGENCE ? UnitDetailsModel.ATTRIBUTE_INTELLIGENCE
							: UnitDetailsModel.ATTRIBUTE_STRENGTH;
		}
		else {
			String classText = null;
			for (final CUnitClassification classification : unit.getClassifications()) {
				if (classification.getDisplayName() != null) {
					classText = classification.getDisplayName();
				}
			}
			d.classText = classText;
			d.rank = TooltipModelBuilder.casterRank(game, unit);
			d.rankName = this.tooltipBuilder.casterField(unit, TooltipModelBuilder.CASTER_RANK_NAMES, d.rank);
			d.rankArt = d.rankName == null ? null
					: this.tooltipBuilder.casterField(unit, TooltipModelBuilder.CASTER_RANK_ART, d.rank);
		}
		writeInventory(game, unit, d, local);
		writeCargo(unit, d);
		writeQueue(game, unit, d, local);
	}

	private void writeInventory(final CSimulation game, final CUnit unit, final UnitDetailsModel d, final int local) {
		CAbilityInventory inventory = unit.getInventoryData();
		if ((inventory != null) && !inventory.isRequirementsMet(game, unit)) {
			inventory = null;
		}
		boolean enabled = unit.getPlayerIndex() == local;
		if (inventory == null) {
			final CAbilityNeutralBuilding neutralBuildingData = unit.getNeutralBuildingData();
			if (neutralBuildingData != null) {
				final CUnit selectedPlayerUnit = neutralBuildingData.getSelectedPlayerUnit(local);
				if (selectedPlayerUnit != null) {
					inventory = selectedPlayerUnit.getInventoryData();
					enabled = false;
				}
			}
		}
		if (inventory == null) {
			return;
		}
		d.inventoryShown = true;
		d.inventoryEnabled = enabled;
		final boolean nineItems = game.getGameData().isUseNineItemInventory();
		final CGameplayConstants constants = game.getGameplayConstants();
		final int capacity = Math.min(ITEM_SLOTS, inventory.getItemCapacity());
		d.inventoryCapacity = capacity;
		for (int index = 0; index < capacity; index++) {
			final CItem item = inventory.getItemInSlot(index);
			if (item == null) {
				continue;
			}
			d.itemTypeIds[index] = item.getTypeId().getValue();
			d.itemCharges[index] = item.getCharges();
			final ItemUI itemUI = this.abilityDataUI.getItemUI(item.getTypeId());
			if (itemUI != null) {
				final IconUI iconUI = itemUI.getIconUI();
				d.itemIconPaths[index] = iconUI.getIconPath();
				d.itemNames[index] = itemUI.getName();
				d.itemUberTips[index] = iconUI.getUberTip();
			}
			d.itemActive[index] = item.isActivelyUsed() && enabled;
			d.itemPawnable[index] = item.isPawnable();
			final CItemType itemType = item.getItemType();
			d.itemPawnGold[index] = constants.getPawnItemValue(itemType.getGoldCost());
			d.itemPawnLumber[index] = constants.getPawnItemValue(itemType.getLumberCost());
			inventory.checkCanUse(game, unit, local, OrderIds.itemUseFirst(nineItems) + index, false,
					this.itemPreview.reset());
			d.itemCooldown[index] = this.itemPreview.getCooldownRemaining();
			d.itemCooldownMax[index] = this.itemPreview.getCooldownMax();
		}
	}

	private void writeCargo(final CUnit unit, final UnitDetailsModel d) {
		final CAbilityCargoHold cargoData = unit.getCargoData();
		if ((cargoData == null) || cargoData.isEmpty()) {
			return;
		}
		d.cargoCapacity = cargoData.getCargoCapacity();
		d.cargoCount = Math.min(cargoData.getCargoCount(), UnitDetailsModel.MAX_CARGO);
		for (int i = 0; i < d.cargoCount; i++) {
			final CUnit contained = cargoData.getUnit(i);
			final UnitIconUI unitUI = this.abilityDataUI.getUnitUI(contained.getTypeId());
			d.cargoIconPaths[i] = unitUI == null ? null : unitUI.getIconPath();
			if (contained.isHero()) {
				d.cargoTips[i] = contained.getHeroData().getProperName();
				d.cargoUberTips[i] = "Level " + contained.getHeroData().getHeroLevel();
			}
			else {
				d.cargoTips[i] = contained.getUnitType().getName();
				d.cargoUberTips[i] = unitUI == null ? null : unitUI.getUberTip();
			}
			d.cargoLife[i] = contained.getLife() / contained.getMaximumLife();
			d.cargoHasMana[i] = contained.getMaximumMana() > 0;
			d.cargoMana[i] = d.cargoHasMana[i] ? contained.getMana() / contained.getMaximumMana() : 0;
		}
	}

	private void writeQueue(final CSimulation game, final CUnit unit, final UnitDetailsModel d, final int local) {
		if (unit.isConstructingOrUpgrading()) {
			final War3ID constructingTypeId = unit.isUpgrading() ? unit.getUpgradeIdType() : unit.getTypeId();
			d.queueCount = 1;
			d.queueTypeIds[0] = constructingTypeId.getValue();
			d.queueKinds[0] = UnitDetailsModel.QUEUE_UNIT;
			final UnitIconUI constructingUI = this.abilityDataUI.getUnitUI(constructingTypeId);
			if (constructingUI != null) {
				d.queueIconPaths[0] = constructingUI.getIconPath();
				d.queueTips[0] = constructingUI.getToolTip();
				d.queueUberTips[0] = constructingUI.getUberTip();
			}
			d.queueProgress = Math.min(unit.getConstructionProgress() / unit.getConstructionDuration(game), 0.99f);
			final CUnit worker = unit.getWorker();
			if ((worker != null) && !unit.isConstructionConsumesWorker() && !unit.isConstructingPaused()) {
				final UnitIconUI workerUI = this.abilityDataUI.getUnitUI(worker.getTypeId());
				d.workerIconPath = workerUI == null ? null : workerUI.getIconPath();
				d.workerHandle = worker.getHandleId();
			}
			return;
		}
		final War3ID[] queue = unit.getBuildQueue();
		final QueueItemType[] types = unit.getBuildQueueTypes();
		if ((queue[0] == null) || (unit.getPlayerIndex() != local)) {
			return;
		}
		d.queueShown = true;
		int count = 0;
		for (int i = 0; (i < types.length) && (i < UnitDetailsModel.MAX_QUEUE); i++) {
			final QueueItemType type = types[i];
			if ((type == null) || ((i > 0) && QueueItemType.SACRIFICE.equals(types[0]))) {
				continue;
			}
			int id = queue[i].getValue();
			IconUI entryUI;
			switch (type) {
			case RESEARCH: {
				d.queueKinds[count] = UnitDetailsModel.QUEUE_RESEARCH;
				entryUI = this.abilityDataUI.getUpgradeUI(queue[i],
						game.getPlayer(unit.getPlayerIndex()).getTechtreeUnlocked(queue[i]));
				d.queueIconPaths[count] = entryUI == null ? null : entryUI.getIconPath();
				d.queueTips[count] = entryUI == null ? null : entryUI.getToolTip();
				d.queueUberTips[count] = entryUI == null ? null : entryUI.getUberTip();
				break;
			}
			case HERO_REVIVE: {
				d.queueKinds[count] = UnitDetailsModel.QUEUE_HERO_REVIVE;
				final CUnit hero = game.getUnit(id);
				id = hero == null ? 0 : hero.getTypeId().getValue();
				final UnitIconUI unitUI = hero == null ? null : this.abilityDataUI.getUnitUI(hero.getTypeId());
				d.queueIconPaths[count] = unitUI == null ? null : unitUI.getIconPath();
				d.queueTips[count] = unitUI == null || hero == null ? null
						: unitUI.getReviveTip() + " - " + hero.getHeroData().getProperName();
				d.queueUberTips[count] = unitUI == null ? null : unitUI.getUberTip();
				break;
			}
			case SACRIFICE:
			case UNIT:
			default: {
				d.queueKinds[count] = type == QueueItemType.SACRIFICE ? UnitDetailsModel.QUEUE_SACRIFICE
						: UnitDetailsModel.QUEUE_UNIT;
				entryUI = this.abilityDataUI.getUnitUI(queue[i]);
				d.queueIconPaths[count] = entryUI == null ? null : entryUI.getIconPath();
				d.queueTips[count] = entryUI == null ? null : entryUI.getToolTip();
				d.queueUberTips[count] = entryUI == null ? null : entryUI.getUberTip();
				break;
			}
			}
			d.queueTypeIds[count++] = id;
		}
		d.queueCount = count;
		final float timeRemaining = unit.getBuildQueueTimeRemaining(game);
		d.queueProgress = Math.min(unit.getConstructionProgress() / timeRemaining, 0.99f);
	}
}
