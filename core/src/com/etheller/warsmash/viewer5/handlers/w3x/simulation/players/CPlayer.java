package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.scope.trigger.TriggerBooleanExpression;
import com.etheller.interpreter.ast.scope.variableevent.CLimitOp;
import com.etheller.warsmash.parsers.jass.JassAIEnvironment;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CPlayerStateListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CReleaseCheats;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CPlayerStateListener.CPlayerStateNotifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit.QueueItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUpgradeType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CBasePlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderNoTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetPoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;

public class CPlayer extends CBasePlayer {
	private static final War3ID HERO_TECHTREE = War3ID.fromString("HERO");
	private final CRace race;
	private final float[] startLocation;
	private int gold;
	private int lumber;
	private int heroTokens;
	private int foodCap;
	private int foodUsed;
	// 1.29 starts every player at 100; the game data has no field for it.
	private int foodCapCeiling = 100;
	private final Map<CUnit, Runnable> foodWaiters = new LinkedHashMap<>();
	private final Map<War3ID, Integer> rawcodeToTechtreeUnlocked = new HashMap<>();
	private final Map<War3ID, Integer> rawcodeToTechtreeInProgress = new HashMap<>();
	private final Map<War3ID, Integer> rawcodeToTechtreeMaxAllowed = new HashMap<>();
	private final List<CUnit> heroes = new ArrayList<>();
	private List<CUnit> cachedHeroes;
	private List<Integer> cachedHeroLevels;
	private final List<CFogModifier> fogModifiers = new ArrayList<>();
	private final List<CFogModifier> fogModifiersAfterUnits = new ArrayList<>();
	private final EnumMap<JassGameEventsWar3, List<CPlayerEvent>> eventTypeToEvents = new EnumMap<>(
			JassGameEventsWar3.class);

	// Player state data
	private boolean givesBounty = false;
	// Measured: 1.29 keeps any integer here and Blizzard.j's allied victory checks compare it to 1.
	private int alliedVictory;
	private int gameResult;
	private CPlayerGameResult removedResult;
	private int placed;
	private boolean observerOnDeath;
	private boolean observer;
	private boolean unfollowable;
	private int goldUpkeepRate;
	private int lumberUpkeepRate;
	private int goldGathered;
	private int lumberGathered;
	private final int[] scores = new int[CPlayerScore.VALUES.length];
	private int heroesUsed;
	private int researchedLevels;
	private int experienceGained;
	private boolean noCreepSleep;
	private int cheats;

	private final Set<War3ID> disabledAbilities = new HashSet<>();

	// if you use triggers for this then the transient tag here becomes really
	// questionable -- it already was -- but I meant for those to inform us
	// which fields shouldn't be persisted if we do game state save later
	private transient CPlayerStateNotifier stateNotifier = new CPlayerStateNotifier();
	private float handicapXP = 1.0f;
	private float handicapDamage = 1.0f;
	private float handicapReviveTime = 1.0f;
	private final CPlayerFogOfWar fogOfWar;
	private CPlayerSelection selection;
	private JassAIEnvironment aiScript;

	public CPlayer(final CRace race, final float[] startLocation, final CBasePlayer configPlayer,
			final CPlayerFogOfWar fogOfWar) {
		super(configPlayer);
		this.race = race;
		this.startLocation = startLocation;
		// Below: 32x32 cells to find the number of 128x128 cells
		this.fogOfWar = fogOfWar;
		for (final Map.Entry<CPlayerState, Integer> configState : configPlayer.getConfigPlayerStates().entrySet()) {
			applyPlayerState(configState.getKey(), configState.getValue());
		}
	}

	public CPlayerSelection getSelection() {
		if (this.selection == null) {
			this.selection = new CPlayerSelection();
		}
		return this.selection;
	}

	public CPlayerFogOfWar getFogOfWar() {
		return this.fogOfWar;
	}

	public void setAlliance(final CPlayer other, final CAllianceType alliance, final boolean flag) {
		setAlliance(other.getId(), alliance, flag);
	}

	@Override
	public void setAlliance(final int otherPlayerIndex, final CAllianceType allianceType, final boolean value) {
		final boolean before = hasAlliance(otherPlayerIndex, allianceType);
		super.setAlliance(otherPlayerIndex, allianceType, value);
		if (before == value) {
			return;
		}
		this.stateNotifier.allianceChanged(otherPlayerIndex, allianceType, value);
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_ALLIANCE_CHANGED);
		if (eventList != null) {
			for (final CPlayerEvent event : new ArrayList<>(eventList)) {
				if (((CPlayerAllianceEvent) event).getAllianceType() == allianceType) {
					event.fire(this, CommonTriggerExecutionScope.triggerPlayerScope(
							JassGameEventsWar3.EVENT_PLAYER_ALLIANCE_CHANGED, event.getTrigger(), this));
				}
			}
		}
	}

	public CPlayerGameResult getRemovedResult() {
		return this.removedResult;
	}

	public void setRemovedResult(final CPlayerGameResult removedResult) {
		this.removedResult = removedResult;
	}

	public CRace getRace() {
		return this.race;
	}

	public int getGold() {
		return this.gold;
	}

	public int getLumber() {
		return this.lumber;
	}

	public int getHeroTokens() {
		return this.heroTokens;
	}

	public int getFoodCap() {
		return this.foodCap;
	}

	public int getFoodUsed() {
		return this.foodUsed;
	}

	public int getFoodCapCeiling() {
		return this.foodCapCeiling;
	}

	/**
	 * The food a player may use: the food cap state itself stays above the ceiling, as GetPlayerState shows it.
	 */
	public int getFoodCapLimit() {
		if (hasCheat(CReleaseCheats.NO_FOOD_LIMIT)) {
			return Integer.MAX_VALUE / 2;
		}
		return Math.min(this.foodCap, this.foodCapCeiling);
	}

	/** A single player chat cheat in force for this player; see CReleaseCheats for the flags. */
	public boolean hasCheat(final int cheat) {
		return (this.cheats & cheat) != 0;
	}

	public void setCheat(final int cheat, final boolean on) {
		this.cheats = on ? (this.cheats | cheat) : (this.cheats & ~cheat);
		if (on && (cheat == CReleaseCheats.NO_FOOD_LIMIT)) {
			retryFoodWaiters();
		}
	}

	public float[] getStartLocation() {
		return this.startLocation;
	}

	public void setGold(final int gold) {
		final int before = this.gold;
		this.gold = gold;
		this.stateNotifier.goldChanged();
		fireStateLimitEvents(CPlayerState.RESOURCE_GOLD, before, gold);
	}

	public void addGold(final int gold) {
		setGold(getGold() + gold);
	}

	public void setLumber(final int lumber) {
		final int before = this.lumber;
		this.lumber = lumber;
		this.stateNotifier.lumberChanged();
		fireStateLimitEvents(CPlayerState.RESOURCE_LUMBER, before, lumber);
	}

	/**
	 * Adds gold gathered by workers or mines after the upkeep tax for the current food usage; returns the amount
	 * actually received.
	 */
	public int addHarvestedGold(final CSimulation game, final int gold) {
		final int received = game.getGameplayConstants().applyUpkeepGoldTax(gold, this.foodUsed);
		addGold(received);
		this.goldGathered += gold;
		addScore(CPlayerScore.GOLD_MINED_TOTAL, gold);
		addScore(CPlayerScore.GOLD_MINED_UPKEEP, received);
		addScore(CPlayerScore.GOLD_LOST_UPKEEP, gold - received);
		return received;
	}

	public int addHarvestedLumber(final CSimulation game, final int lumber) {
		final int received = game.getGameplayConstants().applyUpkeepLumberTax(lumber, this.foodUsed);
		addLumber(received);
		this.lumberGathered += lumber;
		addScore(CPlayerScore.LUMBER_TOTAL, lumber);
		addScore(CPlayerScore.LUMBER_LOST_UPKEEP, lumber - received);
		return received;
	}

	public void addLumber(final int lumber) {
		setLumber(getLumber() + lumber);
	}

	public void setHeroTokens(final int heroTokens) {
		final int before = this.heroTokens;
		this.heroTokens = heroTokens;
		this.stateNotifier.heroTokensChanged();
		fireStateLimitEvents(CPlayerState.RESOURCE_HERO_TOKENS, before, heroTokens);
	}

	public void setFoodCap(final int foodCap) {
		final boolean raised = foodCap > this.foodCap;
		final int before = this.foodCap;
		this.foodCap = foodCap;
		this.scores[CPlayerScore.FOOD_MAXPROD.ordinal()] = Math.max(this.scores[CPlayerScore.FOOD_MAXPROD.ordinal()],
				Math.min(foodCap, this.foodCapCeiling));
		this.stateNotifier.foodChanged();
		fireStateLimitEvents(CPlayerState.RESOURCE_FOOD_CAP, before, foodCap);
		if (raised) {
			retryFoodWaiters();
		}
	}

	public void setFoodCapCeiling(final int foodCapCeiling) {
		final int before = this.foodCapCeiling;
		this.foodCapCeiling = foodCapCeiling;
		this.stateNotifier.foodChanged();
		fireStateLimitEvents(CPlayerState.FOOD_CAP_CEILING, before, foodCapCeiling);
	}

	public void setFoodUsed(final int foodUsed) {
		final boolean freed = foodUsed < this.foodUsed;
		final int before = this.foodUsed;
		this.foodUsed = foodUsed;
		this.scores[CPlayerScore.FOOD_MAXUSED.ordinal()] = Math.max(this.scores[CPlayerScore.FOOD_MAXUSED.ordinal()],
				foodUsed);
		this.stateNotifier.foodChanged();
		fireStateLimitEvents(CPlayerState.RESOURCE_FOOD_USED, before, foodUsed);
		if (freed) {
			retryFoodWaiters();
		}
	}

	/**
	 * A building whose next queue item waits for food.
	 */
	public void addFoodWaiter(final CUnit unit, final Runnable retry) {
		this.foodWaiters.put(unit, retry);
	}

	public void removeFoodWaiter(final CUnit unit) {
		this.foodWaiters.remove(unit);
	}

	private void retryFoodWaiters() {
		if ((this.foodWaiters != null) && !this.foodWaiters.isEmpty()) {
			for (final Runnable retry : new ArrayList<>(this.foodWaiters.values())) {
				retry.run();
			}
		}
	}

	public void addScore(final CPlayerScore score, final int amount) {
		this.scores[score.ordinal()] += amount;
	}

	/**
	 * Counts a unit or building that joins the player by training, construction, purchase or a script; measured in the
	 * original game, the starting units, heroes and units created by scripts count as well, and the unit total is the
	 * gold and lumber cost of all of them.
	 */
	public void scoreUnitGained(final CUnitType unitType) {
		final int cost = unitType.getGoldCost() + unitType.getLumberCost();
		addScore(unitType.isBuilding() ? CPlayerScore.STRUCT_BUILT : CPlayerScore.UNITS_TRAINED, 1);
		addScore(CPlayerScore.UNIT_TOTAL, cost);
		if (unitType.isHero()) {
			this.heroesUsed++;
		}
	}

	/**
	 * Measured in the original game: a killed hero counts as a killed unit as well, a destroyed building only as razed,
	 * and the unit total grows by the cost of whatever was destroyed.
	 */
	public void scoreKill(final CUnitType killedType) {
		addScore(CPlayerScore.UNIT_TOTAL, killedType.getGoldCost() + killedType.getLumberCost());
		if (killedType.isBuilding()) {
			addScore(CPlayerScore.STRUCT_RAZED, 1);
			return;
		}
		addScore(CPlayerScore.UNITS_KILLED, 1);
		if (killedType.isHero()) {
			addScore(CPlayerScore.HEROES_KILLED, 1);
		}
	}

	// Measured: the tech percentage is the researched upgrade levels of the player's race over all of them, rounded down.
	private void scoreResearch(final CSimulation simulation, final CUpgradeType upgradeType, final int levels) {
		// Neutral slots have no race; at game end they inherit unfinished research.
		if (this.race == null) {
			return;
		}
		final CRaceManagerEntry raceEntry = simulation.getGameData().getRaceManager().tryGet(this.race);
		if ((levels == 0) || (raceEntry == null)
				|| (upgradeType.getUnitRace() != CUnitRace.parseRace(raceEntry.getKey()))) {
			return;
		}
		this.researchedLevels += levels;
		final int total = simulation.getUpgradeData().getTotalLevels(upgradeType.getUnitRace());
		this.scores[CPlayerScore.TECH_PERCENT.ordinal()] = total == 0 ? 0 : (this.researchedLevels * 100) / total;
	}

	public int getHeroesUsed() {
		return this.heroesUsed;
	}

	public void addExperienceGained(final int experience) {
		this.experienceGained += experience;
	}

	public int getExperienceGained() {
		return this.experienceGained;
	}

	public int getScore(final CPlayerScore score) {
		switch (score) {
		case RESOURCE_TOTAL:
			// Measured at Low Upkeep: only what reached the player counts, the gold lost to upkeep does not.
			return this.scores[CPlayerScore.GOLD_MINED_UPKEEP.ordinal()] + this.scores[CPlayerScore.LUMBER_TOTAL.ordinal()]
					- this.scores[CPlayerScore.LUMBER_LOST_UPKEEP.ordinal()];
		case HERO_TOTAL:
			// Measured: ten points for each experience point the player's heroes gained.
			return this.experienceGained * 10;
		case TOTAL:
			return this.scores[CPlayerScore.UNIT_TOTAL.ordinal()] + getScore(CPlayerScore.HERO_TOTAL)
					+ getScore(CPlayerScore.RESOURCE_TOTAL);
		default:
			return this.scores[score.ordinal()];
		}
	}

	public int getTechtreeUnlocked(final War3ID rawcode) {
		final Integer techtreeUnlocked = this.rawcodeToTechtreeUnlocked.get(rawcode);
		if (techtreeUnlocked == null) {
			return 0;
		}
		return techtreeUnlocked;
	}

	/** Whether a requirement of this level is met; synergy lifts every requirement. */
	public boolean isRequirementMet(final War3ID rawcode, final int requiredLevel) {
		return hasCheat(CReleaseCheats.NO_TECH_REQUIREMENTS) || (getTechtreeUnlocked(rawcode) >= requiredLevel);
	}

	public int getTechtreeInProgress(final War3ID rawcode) {
		final Integer techtreeInProgress = this.rawcodeToTechtreeInProgress.get(rawcode);
		if (techtreeInProgress == null) {
			return 0;
		}
		return techtreeInProgress;
	}

	public Map<War3ID, Integer> getTechtreeUnlockedLevels() {
		return Collections.unmodifiableMap(this.rawcodeToTechtreeUnlocked);
	}

	public Map<War3ID, Integer> getTechtreeInProgressCounts() {
		return Collections.unmodifiableMap(this.rawcodeToTechtreeInProgress);
	}

	public int getTechtreeUnlockedOrInProgress(final War3ID rawcode) {
		return getTechtreeUnlocked(rawcode) + getTechtreeInProgress(rawcode);
	}

	public void addTechtreeUnlocked(final CSimulation simulation, final War3ID rawcode) {
		addSingleTechtreeUnlocked(rawcode);
		final CUnitData unitData = simulation.getUnitData();
		final CUnitType unitType = unitData.getUnitType(rawcode);
		if (unitType != null) {
			final List<War3ID> dependencyOr = unitType.getDependencyOr();
			for (final War3ID otherDependency : dependencyOr) {
				addSingleTechtreeUnlocked(otherDependency);
			}
		}
		fireRequirementUpdateForAbilities(simulation, false);
	}

	private void addSingleTechtreeUnlocked(final War3ID rawcode) {
		final Integer techtreeUnlocked = this.rawcodeToTechtreeUnlocked.get(rawcode);
		if (techtreeUnlocked == null) {
			this.rawcodeToTechtreeUnlocked.put(rawcode, 1);
		} else {
			this.rawcodeToTechtreeUnlocked.put(rawcode, techtreeUnlocked + 1);
		}
	}

	public void setTechtreeUnlocked(final CSimulation simulation, final War3ID rawcode, final int setToLevel) {
		final int prev = getTechtreeUnlocked(rawcode);
		this.rawcodeToTechtreeUnlocked.put(rawcode, setToLevel);
		fireRequirementUpdateForAbilities(simulation, prev > setToLevel);
	}

	public void removeTechtreeUnlocked(final CSimulation simulation, final War3ID rawcode) {
		removeSingleTechtreeUnlocked(rawcode);
		final CUnitData unitData = simulation.getUnitData();
		final CUnitType unitType = unitData.getUnitType(rawcode);
		if (unitType != null) {
			final List<War3ID> dependencyOr = unitType.getDependencyOr();
			for (final War3ID otherDependency : dependencyOr) {
				removeSingleTechtreeUnlocked(otherDependency);
			}
		}
		fireRequirementUpdateForAbilities(simulation, true);
	}

	private void removeSingleTechtreeUnlocked(final War3ID rawcode) {
		final Integer techtreeUnlocked = this.rawcodeToTechtreeUnlocked.get(rawcode);
		if (techtreeUnlocked == null) {
			this.rawcodeToTechtreeUnlocked.put(rawcode, -1);
		} else {
			this.rawcodeToTechtreeUnlocked.put(rawcode, techtreeUnlocked - 1);
		}
	}

	public void addTechtreeInProgress(final CSimulation simulation, final War3ID rawcode) {
		addSingleTechtreeInProgress(rawcode);
		final CUnitData unitData = simulation.getUnitData();
		final CUnitType unitType = unitData.getUnitType(rawcode);
		if (unitType != null) {
			final List<War3ID> dependencyOr = unitType.getDependencyOr();
			for (final War3ID otherDependency : dependencyOr) {
				addSingleTechtreeInProgress(otherDependency);
			}
		}
	}

	private void addSingleTechtreeInProgress(final War3ID rawcode) {
		final Integer techtreeUnlocked = this.rawcodeToTechtreeInProgress.get(rawcode);
		if (techtreeUnlocked == null) {
			this.rawcodeToTechtreeInProgress.put(rawcode, 1);
		} else {
			this.rawcodeToTechtreeInProgress.put(rawcode, techtreeUnlocked + 1);
		}
	}

	public void removeTechtreeInProgress(final CSimulation simulation, final War3ID rawcode) {
		removeSingleTechtreeInProgress(rawcode);
		final CUnitData unitData = simulation.getUnitData();
		final CUnitType unitType = unitData.getUnitType(rawcode);
		if (unitType != null) {
			final List<War3ID> dependencyOr = unitType.getDependencyOr();
			for (final War3ID otherDependency : dependencyOr) {
				removeSingleTechtreeInProgress(otherDependency);
			}
		}
	}

	private void removeSingleTechtreeInProgress(final War3ID rawcode) {
		final Integer techtreeUnlocked = this.rawcodeToTechtreeInProgress.get(rawcode);
		if (techtreeUnlocked == null) {
			this.rawcodeToTechtreeInProgress.put(rawcode, -1);
		} else {
			this.rawcodeToTechtreeInProgress.put(rawcode, techtreeUnlocked - 1);
		}
	}

	public void setTechtreeMaxAllowed(final War3ID war3id, final int maximum) {
		this.rawcodeToTechtreeMaxAllowed.put(war3id, maximum);
	}

	public int getTechtreeMaxAllowed(final War3ID war3id) {
		final Integer maxAllowed = this.rawcodeToTechtreeMaxAllowed.get(war3id);
		if (maxAllowed != null) {
			return maxAllowed;
		}
		return -1;
	}

	public void addStateListener(final CPlayerStateListener listener) {
		this.stateNotifier.subscribe(listener);
	}

	public void removeStateListener(final CPlayerStateListener listener) {
		this.stateNotifier.unsubscribe(listener);
	}

	public void chargeFor(final CUnitType unitType) {
		changeResources(-unitType.getGoldCost(), -unitType.getLumberCost());
	}

	public void chargeFor(final CUpgradeType upgradeType) {
		final int unlockCount = getTechtreeUnlocked(upgradeType.getTypeId());
		changeResources(-upgradeType.getGoldCost(unlockCount), -upgradeType.getLumberCost(unlockCount));
	}

	public boolean charge(final int gold, final int lumber) {
		if ((this.lumber >= lumber) && (this.gold >= gold)) {
			changeResources(-gold, -lumber);
			return true;
		}
		return false;
	}

	public void refundFor(final CUnitType unitType) {
		changeResources(unitType.getGoldCost(), unitType.getLumberCost());
	}

	public void refundFor(final CUpgradeType upgradeType) {
		final int unlockCount = getTechtreeUnlocked(upgradeType.getTypeId());
		changeResources(upgradeType.getGoldCost(unlockCount), upgradeType.getLumberCost(unlockCount));
	}

	public void refund(final int gold, final int lumber) {
		changeResources(gold, lumber);
	}

	private void changeResources(final int goldDelta, final int lumberDelta) {
		final int goldBefore = this.gold;
		final int lumberBefore = this.lumber;
		this.lumber += lumberDelta;
		this.gold += goldDelta;
		this.stateNotifier.lumberChanged();
		this.stateNotifier.goldChanged();
		fireStateLimitEvents(CPlayerState.RESOURCE_LUMBER, lumberBefore, this.lumber);
		fireStateLimitEvents(CPlayerState.RESOURCE_GOLD, goldBefore, this.gold);
	}

	public void setUnitFoodUsed(final CUnit unit, final int foodUsed) {
		setFoodUsed(this.foodUsed + unit.setFoodUsed(foodUsed));
	}

	public void setUnitFoodMade(final CUnit unit, final int foodMade) {
		setFoodCap(this.foodCap + unit.setFoodMade(foodMade));
	}

	public void onHeroDeath(final CUnit hero) {
		this.stateNotifier.heroDeath();
		firePlayerUnitEvents(hero, CommonTriggerExecutionScope::playerHeroRevivableScope,
				JassGameEventsWar3.EVENT_PLAYER_HERO_REVIVABLE);
	}

	public void firePlayerUnitEvents(final CUnit hero,
			final CommonTriggerExecutionScope.UnitEventScopeBuilder eventScopeBuilder,
			final JassGameEventsWar3 eventType) {
		final List<CPlayerEvent> eventList = getEventList(eventType);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(hero, eventScopeBuilder.create(eventType, event.getTrigger(), hero));
			}
		}
	}

	public void firePlayerEvents(final CommonTriggerExecutionScope.PlayerEventScopeBuilder eventScopeBuilder,
			final JassGameEventsWar3 eventType) {
		final List<CPlayerEvent> eventList = getEventList(eventType);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(this, eventScopeBuilder.create(eventType, event.getTrigger(), this));
			}
		}
	}

	public void fireChatEvents(final String message) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_CHAT);
		if (eventList != null) {
			for (final CPlayerEvent event : new ArrayList<>(eventList)) {
				if (event instanceof CPlayerChatEvent) {
					((CPlayerChatEvent) event).fireChat(message);
				}
			}
		}
	}

	/**
	 * CachePlayerHeroData: the heroes the player owns now and their levels, kept for the score screen's Heroes tab,
	 * since a defeat then gives the units to Neutral Passive.
	 */
	public void cacheHeroData(final CSimulation game) {
		final List<CUnit> units = new ArrayList<>(game.getUnits());
		units.sort(java.util.Comparator.comparingInt(CUnit::getHandleId));
		this.cachedHeroes = new ArrayList<>();
		this.cachedHeroLevels = new ArrayList<>();
		for (final CUnit unit : units) {
			if ((unit.getPlayerIndex() == getId()) && unit.isHero() && (unit.getHeroData() != null)
					&& !unit.isIllusion()) {
				this.cachedHeroes.add(unit);
				this.cachedHeroLevels.add(unit.getHeroData().getHeroLevel());
			}
		}
	}

	/** The heroes of the last CachePlayerHeroData, or null when it was not called for the player. */
	public List<CUnit> getCachedHeroes() {
		return this.cachedHeroes;
	}

	public int getCachedHeroLevel(final int index) {
		return this.cachedHeroLevels.get(index);
	}

	public List<CUnit> getHeroes() {
		return this.heroes;
	}

	public int getHeroCount(final CSimulation game, final boolean includeInProgress) {
		if (!includeInProgress) {
			return this.heroes.size();
		} else {
			int heroInProgressCount = 0;
			for (final Map.Entry<War3ID, Integer> entry : this.rawcodeToTechtreeInProgress.entrySet()) {
				final CUnitType unitType = game.getUnitData().getUnitType(entry.getKey());
				if ((unitType != null) && unitType.isHero()) {
					heroInProgressCount += entry.getValue();
				}
			}
			return this.heroes.size() + heroInProgressCount;
		}
	}

	public void fireUnitSummonEvents(final CUnit summoner, final CUnit summoned) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_SUMMON);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(summoned, CommonTriggerExecutionScope.unitSummonScope(
						JassGameEventsWar3.EVENT_PLAYER_UNIT_SUMMON, event.getTrigger(), summoner, summoned, this));
			}
		}
	}

	public void fireUnitRescuedEvents(final CUnit unit, final CUnit rescuer) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_RESCUED);
		if (eventList != null) {
			for (final CPlayerEvent event : new ArrayList<>(eventList)) {
				event.fire(unit, CommonTriggerExecutionScope.unitRescuedScope(JassGameEventsWar3.EVENT_PLAYER_UNIT_RESCUED,
						event.getTrigger(), unit, rescuer, this));
			}
		}
	}

	public void fireUnitChangeOwnerEvents(final CUnit unit, final CPlayer previousOwner) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_CHANGE_OWNER);
		if (eventList != null) {
			for (final CPlayerEvent event : new ArrayList<>(eventList)) {
				event.fire(unit, CommonTriggerExecutionScope.unitChangeOwnerScope(
						JassGameEventsWar3.EVENT_PLAYER_UNIT_CHANGE_OWNER, event.getTrigger(), unit, this,
						previousOwner));
			}
		}
	}

	public void fireHeroLevelEvents(final CUnit hero) {
		firePlayerUnitEvents(hero, CommonTriggerExecutionScope::playerHeroRevivableScope,
				JassGameEventsWar3.EVENT_PLAYER_HERO_LEVEL);
	}

	public void fireUnitDeathEvents(final CUnit dyingUnit, final CUnit killingUnit) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_DEATH);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(dyingUnit, CommonTriggerExecutionScope.unitDeathScope(
						JassGameEventsWar3.EVENT_PLAYER_UNIT_DEATH, event.getTrigger(), dyingUnit, killingUnit, this));
			}
		}
	}

	public void fireUnitDecayEvents(final CUnit decayingUnit) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_DECAY);
		if (eventList != null) {
			for (final CPlayerEvent event : new ArrayList<>(eventList)) {
				event.fire(decayingUnit, CommonTriggerExecutionScope.unitDecayScope(
						JassGameEventsWar3.EVENT_PLAYER_UNIT_DECAY, event.getTrigger(), decayingUnit, this));
			}
		}
	}

	public void fireUnitAttackedEvents(final CUnit attackedUnit, final CUnit attacker) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_ATTACKED);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(attackedUnit, CommonTriggerExecutionScope.unitAttackedScope(
						JassGameEventsWar3.EVENT_PLAYER_UNIT_ATTACKED, event.getTrigger(), attackedUnit, attacker, this));
			}
		}
	}

	public void fireSimpleUnitEvents(final CUnit unit, final JassGameEventsWar3 type, final CSimulation game) {
		final List<CPlayerEvent> eventList = getEventList(type);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(unit, CommonTriggerExecutionScope.simpleUnitScope(type, event.getTrigger(), unit, this));
			}
		}
	}

	public void fireOrderEvents(final CUnit unit, final CSimulation game, final COrderNoTarget orderNoTarget) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_ISSUED_ORDER);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(unit,
						CommonTriggerExecutionScope.unitOrderScope(JassGameEventsWar3.EVENT_PLAYER_UNIT_ISSUED_ORDER,
								event.getTrigger(), unit, orderNoTarget.getOrderId()));
			}
		}
	}

	public void fireOrderEvents(final CUnit unit, final CSimulation game, final COrderTargetPoint order) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_ISSUED_POINT_ORDER);
		if (eventList != null) {
			final AbilityPointTarget target = order.getTarget(game);
			for (final CPlayerEvent event : eventList) {
				event.fire(unit,
						CommonTriggerExecutionScope.unitOrderPointScope(
								JassGameEventsWar3.EVENT_PLAYER_UNIT_ISSUED_POINT_ORDER, event.getTrigger(), unit,
								order.getOrderId(), target.x, target.y));
			}
		}
	}

	public void fireOrderEvents(final CUnit unit, final CSimulation game, final COrderTargetWidget order) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_ISSUED_TARGET_ORDER);
		if (eventList != null) {
			final CWidget target = order.getTarget(game);
			for (final CPlayerEvent event : eventList) {
				event.fire(unit,
						CommonTriggerExecutionScope.unitOrderTargetScope(
								JassGameEventsWar3.EVENT_PLAYER_UNIT_ISSUED_TARGET_ORDER, event.getTrigger(), unit,
								order.getOrderId(), target));
			}
		}
	}

	public void fireConstructFinishEvents(final CUnit unit, final CSimulation game, final CUnit constructingUnit) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_CONSTRUCT_FINISH);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(unit,
						CommonTriggerExecutionScope.unitConstructFinishScope(
								JassGameEventsWar3.EVENT_PLAYER_UNIT_CONSTRUCT_FINISH, event.getTrigger(), unit,
								constructingUnit));
			}
		}
	}

	public void fireTrainFinishEvents(final CUnit unit, final CSimulation game, final CUnit trainedUnit) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_TRAIN_FINISH);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				// Measured: the filter of a train-finish event sees the trained unit, which is how the melee script
				// recognises a trained hero and hands it its Scroll of Town Portal.
				event.fire(trainedUnit, CommonTriggerExecutionScope.unitTrainFinishScope(
						JassGameEventsWar3.EVENT_PLAYER_UNIT_TRAIN_FINISH, event.getTrigger(), unit, trainedUnit, this));
			}
		}
	}

	public void fireResearchFinishEvents(final CUnit unit, final CSimulation game, final War3ID researched) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_RESEARCH_FINISH);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(unit, CommonTriggerExecutionScope.unitResearchFinishScope(
						JassGameEventsWar3.EVENT_PLAYER_UNIT_RESEARCH_FINISH, event.getTrigger(), unit, researched, this));
			}
		}
	}

	public void firePickUpItemEvents(final CUnit unit, final CItem item, final CSimulation game) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_PICKUP_ITEM);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(unit, CommonTriggerExecutionScope.unitPickupItemScope(
						JassGameEventsWar3.EVENT_PLAYER_UNIT_PICKUP_ITEM, event.getTrigger(), unit, item));
			}
		}
	}

	public void fireItemEvents(final CUnit unit, final CItem item, final JassGameEventsWar3 eventType) {
		final List<CPlayerEvent> eventList = getEventList(eventType);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(unit, CommonTriggerExecutionScope.unitPickupItemScope(eventType, event.getTrigger(), unit,
						item));
			}
		}
	}

	private List<CPlayerEvent> getOrCreateEventList(final JassGameEventsWar3 eventType) {
		List<CPlayerEvent> playerEvents = this.eventTypeToEvents.get(eventType);
		if (playerEvents == null) {
			playerEvents = new ArrayList<>();
			this.eventTypeToEvents.put(eventType, playerEvents);
		}
		return playerEvents;
	}

	public boolean hasPlayerEvents(final JassGameEventsWar3 eventType) {
		final List<CPlayerEvent> eventList = getEventList(eventType);
		return (eventList != null) && !eventList.isEmpty();
	}

	private List<CPlayerEvent> getEventList(final JassGameEventsWar3 eventType) {
		return this.eventTypeToEvents.get(eventType);
	}

	@Override
	public RemovableTriggerEvent addEvent(final GlobalScope globalScope, final Trigger whichTrigger,
			final JassGameEventsWar3 eventType) {
		final CPlayerEvent playerEvent = new CPlayerEvent(globalScope, this, whichTrigger, eventType, null);
		getOrCreateEventList(eventType).add(playerEvent);
		return playerEvent;
	}

	public RemovableTriggerEvent addStateEvent(final GlobalScope globalScope, final Trigger whichTrigger,
			final CPlayerState state, final CLimitOp limitOp, final double limitValue) {
		final CPlayerEvent playerEvent = new CPlayerStateEvent(globalScope, this, whichTrigger, state, limitOp,
				limitValue);
		getOrCreateEventList(playerEvent.getEventType()).add(playerEvent);
		return playerEvent;
	}

	public RemovableTriggerEvent addAllianceEvent(final GlobalScope globalScope, final Trigger whichTrigger,
			final CAllianceType allianceType) {
		final CPlayerEvent playerEvent = new CPlayerAllianceEvent(globalScope, this, whichTrigger, allianceType);
		getOrCreateEventList(playerEvent.getEventType()).add(playerEvent);
		return playerEvent;
	}

	public RemovableTriggerEvent addUnitEvent(final GlobalScope globalScope, final Trigger whichTrigger,
			final JassGameEventsWar3 eventType, final TriggerBooleanExpression filter) {
		final CPlayerEvent playerEvent = new CPlayerEvent(globalScope, this, whichTrigger, eventType, filter);
		getOrCreateEventList(eventType).add(playerEvent);
		return playerEvent;
	}

	public void addEvent(final CPlayerEvent playerEvent) {
		getOrCreateEventList(playerEvent.getEventType()).add(playerEvent);
	}

	@Override
	public void removeEvent(final CPlayerEvent playerEvent) {
		final List<CPlayerEvent> eventList = getEventList(playerEvent.getEventType());
		if (eventList != null) {
			eventList.remove(playerEvent);
		}
	}

	public void setPlayerState(final CSimulation simulation, final CPlayerState whichPlayerState, final int value) {
		final int before = getPlayerState(simulation, whichPlayerState);
		applyPlayerState(whichPlayerState, value);
		switch (whichPlayerState) {
		case RESOURCE_GOLD:
		case RESOURCE_LUMBER:
		case RESOURCE_HERO_TOKENS:
		case RESOURCE_FOOD_CAP:
		case RESOURCE_FOOD_USED:
		case FOOD_CAP_CEILING:
			// the setters fire the events
			break;
		default:
			fireStateLimitEvents(whichPlayerState, before, getPlayerState(simulation, whichPlayerState));
			break;
		}
	}

	/**
	 * Measured on 1.29: a player state event fires on every change of the state that leaves it meeting the limit, not
	 * only when the state starts to meet it.
	 */
	private void fireStateLimitEvents(final CPlayerState state, final int before, final int after) {
		if ((before == after) || (this.eventTypeToEvents == null)) {
			return;
		}
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_STATE_LIMIT);
		if (eventList != null) {
			for (final CPlayerEvent event : new ArrayList<>(eventList)) {
				final CPlayerStateEvent stateEvent = (CPlayerStateEvent) event;
				if ((stateEvent.getState() == state) && stateEvent.isMatching(after)) {
					event.fire(this, CommonTriggerExecutionScope.playerStateLimitScope(event.getTrigger(), this,
							state));
				}
			}
		}
	}

	private void applyPlayerState(final CPlayerState whichPlayerState, final int value) {
		switch (whichPlayerState) {
		case GAME_RESULT:
			this.gameResult = value;
			break;
		case RESOURCE_GOLD:
			setGold(value);
			break;
		case RESOURCE_LUMBER:
			setLumber(value);
			break;
		case RESOURCE_HERO_TOKENS:
			setHeroTokens(value);
			break;
		case RESOURCE_FOOD_CAP:
			setFoodCap(value);
			break;
		case RESOURCE_FOOD_USED:
			setFoodUsed(value);
			break;
		case FOOD_CAP_CEILING:
			setFoodCapCeiling(value);
			break;
		case ALLIED_VICTORY:
			this.alliedVictory = value;
			break;
		case GIVES_BOUNTY:
			this.givesBounty = (value != 0);
			break;
		case PLACED:
			this.placed = value;
			break;
		case OBSERVER_ON_DEATH:
			this.observerOnDeath = (value != 0);
			break;
		case OBSERVER:
			this.observer = (value != 0);
			break;
		case UNFOLLOWABLE:
			this.unfollowable = (value != 0);
			break;
		case GOLD_UPKEEP_RATE:
			this.goldUpkeepRate = value;
			break;
		case LUMBER_UPKEEP_RATE:
			this.lumberUpkeepRate = value;
			break;
		case GOLD_GATHERED:
			this.goldGathered = value;
			break;
		case LUMBER_GATHERED:
			this.lumberGathered = value;
			break;
		case NO_CREEP_SLEEP:
			this.noCreepSleep = (value != 0);
			break;
		default:
			break;
		}
	}

	public int getPlayerState(final CSimulation simulation, final CPlayerState whichPlayerState) {
		switch (whichPlayerState) {
		case GAME_RESULT:
			return this.gameResult;
		case RESOURCE_GOLD:
			return getGold();
		case RESOURCE_LUMBER:
			return getLumber();
		case RESOURCE_HERO_TOKENS:
			return getHeroTokens();
		case RESOURCE_FOOD_CAP:
			return getFoodCap();
		case RESOURCE_FOOD_USED:
			return getFoodUsed();
		case FOOD_CAP_CEILING:
			return getFoodCapCeiling();
		case ALLIED_VICTORY:
			return this.alliedVictory;
		case GIVES_BOUNTY:
			return this.givesBounty ? 1 : 0;
		case PLACED:
			return this.placed;
		case OBSERVER_ON_DEATH:
			return this.observerOnDeath ? 1 : 0;
		case OBSERVER:
			return this.observer ? 1 : 0;
		case UNFOLLOWABLE:
			return this.unfollowable ? 1 : 0;
		case GOLD_UPKEEP_RATE:
			return this.goldUpkeepRate;
		case LUMBER_UPKEEP_RATE:
			return this.lumberUpkeepRate;
		case GOLD_GATHERED:
			return this.goldGathered;
		case LUMBER_GATHERED:
			return this.lumberGathered;
		case NO_CREEP_SLEEP:
			return this.noCreepSleep ? 1 : 0;
		default:
			return 0;
		}
	}

	public boolean isObserver() {
		return this.observer;
	}

	/**
	 * The 'HERO' techtree maximum (SetPlayerMaxHeroesAllowed) counts the heroes the player has, dead ones and
	 * those in training included.
	 */
	public boolean isHeroCountAllowedByMax(final CSimulation game) {
		final int maxHeroes = getTechtreeMaxAllowed(HERO_TECHTREE);
		return (maxHeroes < 0) || (getHeroCount(game, true) < maxHeroes);
	}

	/**
	 * Measured: a dead hero awaiting revive is not counted by GetPlayerTechCount or requirements, but it still
	 * takes its place in its type's limit (one dead Paladin blocks a new Paladin at limit 1, not at limit 2).
	 */
	public boolean isTechtreeAllowedByMax(final War3ID techtree) {
		final int techtreeMaxAllowed = getTechtreeMaxAllowed(techtree);
		if (techtreeMaxAllowed >= 0) {
			if ((getTechtreeUnlockedOrInProgress(techtree) + getDeadHeroCount(techtree)) >= techtreeMaxAllowed) {
				return false;
			}
		}
		return true;
	}

	private int getDeadHeroCount(final War3ID typeId) {
		int count = 0;
		for (final CUnit hero : this.heroes) {
			if (hero.isDead() && hero.getTypeId().equals(typeId)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * GetPlayerTechCount: 'HERO' is the number of living heroes, a reincarnating one included (measured), anything
	 * else its techtree count.
	 */
	public int getTechCount(final War3ID techtree) {
		if (HERO_TECHTREE.equals(techtree)) {
			int count = 0;
			for (final CUnit hero : this.heroes) {
				if (!hero.isDead() || hero.isFalseDeath()) {
					count++;
				}
			}
			return count;
		}
		return getTechtreeUnlocked(techtree);
	}

	public void setHandicapXP(final float handicapXP) {
		this.handicapXP = handicapXP;
	}

	public float getHandicapXP() {
		return this.handicapXP;
	}

	public void setHandicapDamage(final float handicapDamage) {
		this.handicapDamage = handicapDamage;
	}

	public float getHandicapDamage() {
		return this.handicapDamage;
	}

	public void setHandicapReviveTime(final float handicapReviveTime) {
		this.handicapReviveTime = handicapReviveTime;
	}

	public float getHandicapReviveTime() {
		return this.handicapReviveTime;
	}

	public void fireAbilityEffectEventsTarget(final CAbility spellAbility, final CUnit spellAbilityUnit,
			final CUnit spellTargetUnit, final War3ID alias) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_SPELL_EFFECT);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(spellAbilityUnit,
						CommonTriggerExecutionScope.unitSpellTargetUnitScope(
								JassGameEventsWar3.EVENT_PLAYER_UNIT_SPELL_EFFECT, event.getTrigger(), spellAbility,
								spellAbilityUnit, spellTargetUnit, alias));
			}
		}
	}

	public void fireAbilityEffectEventsPoint(final CAbility spellAbility, final CUnit spellAbilityUnit,
			final AbilityPointTarget abilityPointTarget, final War3ID alias) {
		final List<CPlayerEvent> eventList = getEventList(JassGameEventsWar3.EVENT_PLAYER_UNIT_SPELL_EFFECT);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(spellAbilityUnit,
						CommonTriggerExecutionScope.unitSpellPointScope(
								JassGameEventsWar3.EVENT_PLAYER_UNIT_SPELL_EFFECT, event.getTrigger(), spellAbility,
								spellAbilityUnit, abilityPointTarget, alias));
			}
		}
	}

	public void fireSpellEventsNoTarget(final JassGameEventsWar3 eventId, final CAbility spellAbility,
			final CUnit spellAbilityUnit) {
		final List<CPlayerEvent> eventList = getEventList(eventId);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(spellAbilityUnit, CommonTriggerExecutionScope.unitSpellNoTargetScope(eventId,
						event.getTrigger(), spellAbility, spellAbilityUnit, spellAbility.getAlias()));
			}
		}
	}

	public void fireSpellEventsPointTarget(final JassGameEventsWar3 eventId, final CAbility spellAbility,
			final CUnit spellAbilityUnit, final AbilityPointTarget abilityPointTarget) {
		final List<CPlayerEvent> eventList = getEventList(eventId);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(spellAbilityUnit,
						CommonTriggerExecutionScope.unitSpellPointScope(eventId, event.getTrigger(), spellAbility,
								spellAbilityUnit, abilityPointTarget, spellAbility.getAlias()));
			}
		}
	}

	public void fireSpellEventsUnitTarget(final JassGameEventsWar3 eventId, final CAbility spellAbility,
			final CUnit spellAbilityUnit, final CUnit unitTarget) {
		final List<CPlayerEvent> eventList = getEventList(eventId);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(spellAbilityUnit, CommonTriggerExecutionScope.unitSpellTargetUnitScope(eventId,
						event.getTrigger(), spellAbility, spellAbilityUnit, unitTarget, spellAbility.getAlias()));
			}
		}
	}

	public void fireSpellEventsItemTarget(final JassGameEventsWar3 eventId, final CAbility spellAbility,
			final CUnit spellAbilityUnit, final CItem itemTarget) {
		final List<CPlayerEvent> eventList = getEventList(eventId);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(spellAbilityUnit, CommonTriggerExecutionScope.unitSpellTargetItemScope(eventId,
						event.getTrigger(), spellAbility, spellAbilityUnit, itemTarget, spellAbility.getAlias()));
			}
		}
	}

	public void fireSpellEventsDestructableTarget(final JassGameEventsWar3 eventId, final CAbility spellAbility,
			final CUnit spellAbilityUnit, final CDestructable destTarget) {
		final List<CPlayerEvent> eventList = getEventList(eventId);
		if (eventList != null) {
			for (final CPlayerEvent event : eventList) {
				event.fire(spellAbilityUnit, CommonTriggerExecutionScope.unitSpellTargetDestructableScope(eventId,
						event.getTrigger(), spellAbility, spellAbilityUnit, destTarget, spellAbility.getAlias()));
			}
		}
	}

	public void addTechResearched(final CSimulation simulation, final War3ID techIdRawcodeId, final int levels) {
		final int previousUnlockCount = getTechtreeUnlocked(techIdRawcodeId);
		if (levels != 0) {
			final int setToLevel = previousUnlockCount + levels;
			setTechToLevel(simulation, techIdRawcodeId, setToLevel);
		}
		fireRequirementUpdateForAbilities(simulation, false);
	}

	public void decTechResearched(final CSimulation simulation, final War3ID techIdRawcodeId, final int levels) {
		if (levels > 0) {
			setTechResearched(simulation, techIdRawcodeId, Math.max(0, getTechtreeUnlocked(techIdRawcodeId) - levels));
		}
	}

	public void setTechResearched(final CSimulation simulation, final War3ID techIdRawcodeId, final int setToLevel) {
		final int previousUnlockCount = getTechtreeUnlocked(techIdRawcodeId);
		if ((setToLevel > previousUnlockCount) || (setToLevel < previousUnlockCount)) {
			setTechToLevel(simulation, techIdRawcodeId, setToLevel);
		}
		fireRequirementUpdateForAbilities(simulation, false);
	}

	private void setTechToLevel(final CSimulation simulation, final War3ID techIdRawcodeId, final int setToLevel) {
		final int previousLevel = getTechtreeUnlocked(techIdRawcodeId);
		setTechtreeUnlocked(simulation, techIdRawcodeId, setToLevel);
		// terminate in progress upgrades of this kind for player
		final CUpgradeType upgradeType = simulation.getUpgradeData().getType(techIdRawcodeId);
		if (upgradeType != null) {
			scoreResearch(simulation, upgradeType, Math.max(0, setToLevel) - Math.max(0, previousLevel));
			for (final CUnit unit : simulation.getUnitsIncludingNew()) {
				if (unit.getPlayerIndex() == getId()) {
					if (unit.isBuildQueueActive() && (unit.getBuildQueueTypes()[0] == QueueItemType.RESEARCH)
							&& (unit.getBuildQueue()[0].getValue() == techIdRawcodeId.getValue())) {
						unit.cancelBuildQueueItem(simulation, 0);
					}
					if (unit.getUnitType().getUpgradesUsed().contains(techIdRawcodeId)) {
						if (previousLevel != 0) {
							upgradeType.unapply(simulation, unit, previousLevel);
						}
						if (setToLevel != 0) {
							upgradeType.apply(simulation, unit, setToLevel);
						}
					}
				}
			}
			if (previousLevel != 0) {
				upgradeType.unapply(simulation, getId(), previousLevel);
			}
			if (setToLevel != 0) {
				upgradeType.apply(simulation, getId(), setToLevel);
			}
		}
	}

	public void addFogModifer(final CSimulation game, final CFogModifier fogModifier, final boolean afterUnits) {
		if (afterUnits) {
			this.fogModifiersAfterUnits.add(fogModifier);
		} else {
			this.fogModifiers.add(fogModifier);
		}
		fogModifier.onAdd(game, this);
	}

	public void removeFogModifer(final CSimulation game, final CFogModifier fogModifier) {
		this.fogModifiers.remove(fogModifier);
		this.fogModifiersAfterUnits.remove(fogModifier);
		fogModifier.onRemove(game, this);
	}

	public void updateFogModifiers(final CSimulation game) {
		for (int i = this.fogModifiers.size() - 1; i >= 0; i--) {
			final CFogModifier fogModifier = this.fogModifiers.get(i);
			this.fogOfWar.setPrivateWrite(!fogModifier.isSharedVision());
			fogModifier.update(game, this, game.getPathingGrid(), this.fogOfWar);
			this.fogOfWar.setPrivateWrite(false);
			updateFogModifierOfAllies(game, fogModifier);
		}
	}

	public void updateFogModifiersAfterUnits(final CSimulation game) {
		for (int i = this.fogModifiersAfterUnits.size() - 1; i >= 0; i--) {
			final CFogModifier fogModifier = this.fogModifiersAfterUnits.get(i);
			fogModifier.update(game, this, game.getPathingGrid(), this.fogOfWar);
			updateFogModifierOfAllies(game, fogModifier);
		}
	}

	// Measured on 1.29: a modifier made with the shared vision flag also acts on every player this one gives shared
	// vision to at the time of the update, in its own phase of the update.
	private void updateFogModifierOfAllies(final CSimulation game, final CFogModifier fogModifier) {
		if (!fogModifier.isSharedWithAllies()) {
			return;
		}
		for (int i = 0, n = game.getGameData().getMaxPlayers(); i < n; i++) {
			if ((i != getId()) && (hasAlliance(i, CAllianceType.SHARED_VISION)
					|| hasAlliance(i, CAllianceType.SHARED_VISION_FORCED))) {
				final CPlayer ally = game.getPlayer(i);
				if (ally == null) {
					continue;
				}
				fogModifier.update(game, ally, game.getPathingGrid(), ally.getFogOfWar());
			}
		}
	}

	public void setAbilityEnabled(final CSimulation simulation, final War3ID ability, final boolean enabled) {
		if (enabled) {
			this.disabledAbilities.remove(ability);
		} else {
			this.disabledAbilities.add(ability);
		}
		fireRequirementUpdateForAbilities(simulation, !enabled);
	}

	public boolean isAbilityDisabled(final War3ID abilityId) {
		return this.disabledAbilities.contains(abilityId);
	}

	public void fireRequirementUpdateForAbilities(final CSimulation simulation, final boolean disable) {
		simulation.fireRequirementUpdateForAbilities(this, disable);
	}

	public JassAIEnvironment getAiScript() {
		return this.aiScript;
	}

	public void setAiScript(final JassAIEnvironment aiScript) {
		if ((this.aiScript != null) && (this.aiScript != aiScript)) {
			this.aiScript.stop();
		}
		this.aiScript = aiScript;
	}
}
