package com.etheller.warsmash.parsers.jass.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit.QueueItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitTypeRequirement;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUpgradeType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.AbstractCAbilityBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityRepair;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityAcolyteHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityWispHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityQueue;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityReviveHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.upgrade.CAbilityUpgrade;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorHoldPosition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIdUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.BuildOnBuildingIntersector;

/**
 * The engine side of a computer player, what Warcraft III keeps behind the common.ai natives: towns and their
 * gold mines, the harvest assignment, production and building placement, hero skills, the attack and defense
 * captains and the map knowledge (creep camps, expansions, enemy bases) the race scripts ask for.
 */
public class AIController {
	public static final int ATTACK_CAPTAIN = 1;
	public static final int DEFENSE_CAPTAIN = 2;

	private static final float TOWN_RADIUS = 1500f;
	private static final float HOME_RADIUS = 900f;
	private static final float THREAT_RADIUS = 1300f;
	private static final float CAMP_LINK_DISTANCE = 600f;
	private static final float BUILDING_GAP = 96f;
	private static final float MAX_LEG = 2500f;
	private static final int MAX_QUEUED_PER_PRODUCER = 2;
	private static final int BUILD_JOB_TIMEOUT_TICKS = (int) (90 / WarsmashConstants.SIMULATION_STEP_TIME);
	private static final int BUILD_JOB_GRACE_TICKS = (int) (2 / WarsmashConstants.SIMULATION_STEP_TIME);
	private static final int GIVE_UP_TICKS = (int) (60 / WarsmashConstants.SIMULATION_STEP_TIME);
	private static final int TREE_CACHE_TICKS = (int) (20 / WarsmashConstants.SIMULATION_STEP_TIME);
	private static final int GOLD = 0;
	private static final int WOOD = 1;
	private static final War3ID[] SIEGE_UNITS = { War3ID.fromString("ushd"), War3ID.fromString("umtw"),
			War3ID.fromString("hmtm"), War3ID.fromString("hmtt"), War3ID.fromString("ebal"),
			War3ID.fromString("ocat") };

	private final CSimulation simulation;
	private final GlobalScope globalScope;
	private final int playerIndex;
	private final CPlayer player;

	private final List<AITown> towns = new ArrayList<>();
	private AITown pendingExpansion;
	private final List<AIBuildJob> buildJobs = new ArrayList<>();
	private final List<int[]> harvestRequests = new ArrayList<>();
	private final Map<CUnit, int[]> harvestJobs = new HashMap<>();
	private final Map<Integer, List<CDestructable>> townTrees = new HashMap<>();
	private final Map<Integer, Integer> townTreesTick = new HashMap<>();
	private final AICaptain attackCaptain = new AICaptain();
	private final AICaptain defenseCaptain = new AICaptain();
	private final Set<CUnit> injured = new HashSet<>();
	private final Map<CUnit, float[]> lastPositions = new HashMap<>();
	private final Set<CUnit> unreachable = new HashSet<>();
	private final Map<CUnit, CUnit> orderedTargets = new HashMap<>();
	private final Set<CUnit> gatherOrdered = new HashSet<>();
	private final Deque<int[]> commands = new ArrayDeque<>();
	private final Map<War3ID, int[]> countCache = new HashMap<>();
	private int countCacheTick = -1;
	private CUnit allianceTarget;
	private CUnit enemyBase;
	private CodeJassValue heroLevelsFunction;
	private CUnit levelingHero;
	private int levelingHeroLevel;
	private CUnit threat;
	private int retreatTick;
	private int goalProgressGeneration = -1;
	private float bestGoalDistance;
	private int goalProgressTick;
	private int ticks;

	private boolean campaign;
	private boolean targetHeroes;
	private boolean peonsRepair;
	private boolean heroesFlee;
	private boolean unitsFlee;
	private boolean groupsFlee;
	private boolean ignoreInjured;
	private boolean heroesTakeItems;
	private boolean newHeroes = true;
	private int replacementCount;

	public AIController(final CSimulation simulation, final GlobalScope globalScope, final int playerIndex) {
		this.simulation = simulation;
		this.globalScope = globalScope;
		this.playerIndex = playerIndex;
		this.player = simulation.getPlayer(playerIndex);
	}

	public CPlayer getPlayer() {
		return this.player;
	}

	public void update() {
		this.ticks++;
		if (this.towns.isEmpty()) {
			initTowns();
		}
		if ((this.ticks % 10) == 0) {
			updateBuildJobs();
			updateHeroSkills();
			updateCaptains();
		}
		if ((this.ticks % 20) == 5) {
			refreshTowns();
			assignHarvesters();
		}
	}


	public void setCampaign(final boolean campaign) {
		this.campaign = campaign;
	}

	public void setTargetHeroes(final boolean targetHeroes) {
		this.targetHeroes = targetHeroes;
	}

	public void setPeonsRepair(final boolean peonsRepair) {
		this.peonsRepair = peonsRepair;
	}

	public void setHeroesFlee(final boolean heroesFlee) {
		this.heroesFlee = heroesFlee;
	}

	public void setUnitsFlee(final boolean unitsFlee) {
		this.unitsFlee = unitsFlee;
	}

	public void setGroupsFlee(final boolean groupsFlee) {
		this.groupsFlee = groupsFlee;
	}

	public void setIgnoreInjured(final boolean ignoreInjured) {
		this.ignoreInjured = ignoreInjured;
	}

	public void setHeroesTakeItems(final boolean heroesTakeItems) {
		this.heroesTakeItems = heroesTakeItems;
	}

	public void setNewHeroes(final boolean newHeroes) {
		this.newHeroes = newHeroes;
	}

	public void setReplacementCount(final int replacementCount) {
		this.replacementCount = replacementCount;
	}

	public void setHeroLevelsFunction(final CodeJassValue heroLevelsFunction) {
		this.heroLevelsFunction = heroLevelsFunction;
	}

	public int getMeleeDifficulty() {
		if (this.player.getAIDifficulty() == null) {
			return 2;
		}
		switch (this.player.getAIDifficulty()) {
		case NEWBIE:
			return 1;
		case INSANE:
			return 3;
		default:
			return 2;
		}
	}


	public void command(final int command, final int data) {
		this.commands.addLast(new int[] { command, data });
	}

	public int getCommandsWaiting() {
		return this.commands.size();
	}

	public int getLastCommand() {
		return this.commands.isEmpty() ? 0 : this.commands.peekFirst()[0];
	}

	public int getLastData() {
		return this.commands.isEmpty() ? 0 : this.commands.peekFirst()[1];
	}

	public void popLastCommand() {
		this.commands.pollFirst();
	}


	private boolean isOurs(final CUnit unit) {
		return (unit.getPlayerIndex() == this.playerIndex) && !unit.isDead();
	}

	private static boolean isWorkerType(final CUnit unit) {
		return unit.getClassifications().contains(CUnitClassification.PEON);
	}

	private static boolean canHarvestGold(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if ((ability instanceof CAbilityAcolyteHarvest) || (ability instanceof CAbilityWispHarvest)) {
				return true;
			}
			if ((ability instanceof CAbilityHarvest) && (((CAbilityHarvest) ability).getGoldCapacity() > 0)) {
				return true;
			}
		}
		return false;
	}

	private static boolean canHarvestLumber(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof CAbilityWispHarvest) {
				return true;
			}
			if ((ability instanceof CAbilityHarvest) && (((CAbilityHarvest) ability).getLumberCapacity() > 0)) {
				return true;
			}
		}
		return false;
	}

	private static int repairOrderId(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof CAbilityRepair) {
				return ((CAbilityRepair) ability).getBaseOrderId();
			}
		}
		return OrderIds.repair;
	}

	private static boolean minesFromOverlay(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if ((ability instanceof CAbilityAcolyteHarvest) || (ability instanceof CAbilityWispHarvest)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isIdle(final CUnit unit) {
		final CBehavior behavior = unit.getCurrentBehavior();
		return (behavior == null) || (behavior == unit.getStopBehavior()) || (behavior instanceof CBehaviorHoldPosition);
	}

	private static boolean isBuilding(final CUnit unit) {
		final CBehavior behavior = unit.getCurrentBehavior();
		return (behavior != null) && (behavior.getBehaviorCategory() == CBehaviorCategory.BUILD);
	}

	private boolean isBuildJobWorker(final CUnit unit) {
		for (final AIBuildJob job : this.buildJobs) {
			if (job.getWorker() == unit) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Workers and the units harvesting for us stay out of the captains.
	 */
	private boolean isArmyUnit(final CUnit unit) {
		return isOurs(unit) && !unit.isBuilding() && !unit.isHidden() && !isWorkerType(unit)
				&& !this.harvestJobs.containsKey(unit) && !isBuildJobWorker(unit)
				&& (unit.getUnitType().getMovementType() != PathingGrid.MovementType.DISABLED);
	}

	private boolean isEnemyPlayer(final int otherIndex) {
		if ((otherIndex == this.playerIndex) || this.player.hasAlliance(otherIndex, CAllianceType.PASSIVE)) {
			return false;
		}
		return (otherIndex < (WarsmashConstants.MAX_PLAYERS - 4)) || this.simulation.isNeutralAggressive(otherIndex);
	}

	private boolean isEnemy(final CUnit unit) {
		return !unit.isDead() && !unit.isHidden() && isEnemyPlayer(unit.getPlayerIndex());
	}

	private boolean isOpponent(final int otherIndex) {
		if ((otherIndex >= (WarsmashConstants.MAX_PLAYERS - 4)) || !isEnemyPlayer(otherIndex)) {
			return false;
		}
		final CPlayer other = this.simulation.getPlayer(otherIndex);
		return (other.getSlotState() == CPlayerSlotState.PLAYING)
				&& ((other.getController() == CMapControl.USER) || (other.getController() == CMapControl.COMPUTER));
	}

	private static float distance(final float x1, final float y1, final float x2, final float y2) {
		final float dx = x1 - x2;
		final float dy = y1 - y2;
		return (float) Math.sqrt((dx * dx) + (dy * dy));
	}

	private static int queueLength(final CUnit unit) {
		int count = 0;
		for (final War3ID item : unit.getBuildQueue()) {
			if (item != null) {
				count++;
			}
		}
		return count;
	}


	public int getUnitCount(final int typeId, final boolean done) {
		if (this.countCacheTick != this.simulation.getGameTurnTick()) {
			this.countCacheTick = this.simulation.getGameTurnTick();
			this.countCache.clear();
		}
		final War3ID id = new War3ID(typeId);
		int[] counts = this.countCache.get(id);
		if (counts == null) {
			counts = new int[] { countUnits(this.playerIndex, id, false, -1), countUnits(this.playerIndex, id, true, -1) };
			this.countCache.put(id, counts);
		}
		return done ? counts[1] : counts[0];
	}

	public int getTownUnitCount(final int typeId, final int town, final boolean done) {
		return countUnits(this.playerIndex, new War3ID(typeId), done, town);
	}

	/**
	 * Counts units of a type like the AI sees them: a structure being upgraded counts as its new type until done,
	 * queued units and heroes being revived count as not done, and so do the structures our workers are on
	 * their way to build.
	 */
	public int countUnits(final int ownerIndex, final War3ID id, final boolean done, final int town) {
		int count = 0;
		for (final CUnit unit : this.simulation.getUnits()) {
			if ((unit.getPlayerIndex() != ownerIndex) || unit.isDead()) {
				continue;
			}
			if ((town != -1) && (townOf(unit.getX(), unit.getY()) != town)) {
				continue;
			}
			if (unit.isUpgrading()) {
				if (done ? id.equals(unit.getTypeId()) : id.equals(unit.getUpgradeIdType())) {
					count++;
				}
			}
			else if (id.equals(unit.getTypeId()) && (!done || !unit.isConstructing())) {
				count++;
			}
			if (!done) {
				final War3ID[] queue = unit.getBuildQueue();
				final QueueItemType[] types = unit.getBuildQueueTypes();
				for (int i = 0; i < queue.length; i++) {
					if (queue[i] == null) {
						continue;
					}
					if ((types[i] == QueueItemType.UNIT) && id.equals(queue[i])) {
						count++;
					}
					else if (types[i] == QueueItemType.HERO_REVIVE) {
						final CUnit hero = this.simulation.getUnit(queue[i].getValue());
						if ((hero != null) && id.equals(hero.getTypeId())) {
							count++;
						}
					}
				}
			}
		}
		if (!done && (ownerIndex == this.playerIndex)) {
			for (final AIBuildJob job : this.buildJobs) {
				if (id.equals(job.getTypeId()) && ((town == -1) || (job.getTown() == town)) && !isLaidDown(job)) {
					count++;
				}
			}
		}
		return count;
	}


	private void initTowns() {
		final float[] start = this.player.getStartLocation();
		final CUnit mine = findGoldMineNear(start[0], start[1], 1600f);
		this.towns.add(new AITown(0, start[0], start[1], mine));
		refreshTowns();
		if (!this.attackCaptain.isHomeSet()) {
			resetCaptainLocations();
		}
	}

	private CUnit findGoldMineNear(final float x, final float y, final float radius) {
		CUnit best = null;
		float bestDistance = radius;
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!unit.isDead() && (unit.getGoldMineData() != null) && (unit.getOverlayedGoldMineData() == null)) {
				final float d = distance(x, y, unit.getX(), unit.getY());
				if (d < bestDistance) {
					bestDistance = d;
					best = unit;
				}
			}
		}
		return best;
	}

	private CUnit findOverlayOn(final CUnit mine) {
		if (mine == null) {
			return null;
		}
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!unit.isDead() && (unit.getOverlayedGoldMineData() != null)
					&& ((unit.getOverlayedGoldMineData().getParentGoldMineUnit() == mine)
							|| (distance(unit.getX(), unit.getY(), mine.getX(), mine.getY()) < 64))) {
				return unit;
			}
		}
		return null;
	}

	private void refreshTowns() {
		for (final AITown town : this.towns) {
			CUnit mine = town.getMine();
			if ((mine == null) || mine.isDead()) {
				mine = findGoldMineNear(town.getX(), town.getY(), town.getId() == 0 ? 1600f : 400f);
				town.setMine(mine);
			}
			town.setOverlay(findOverlayOn(mine));
			town.setHall(findHallNear(town));
		}
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!isOurs(unit) || !unit.isBuilding()) {
				continue;
			}
			final boolean hall = unit.getClassifications().contains(CUnitClassification.TOWNHALL);
			final boolean overlay = unit.getOverlayedGoldMineData() != null;
			if ((!hall && !overlay) || (townOf(unit.getX(), unit.getY()) != -1)) {
				continue;
			}
			final CUnit mine = overlay ? unit.getOverlayedGoldMineData().getParentGoldMineUnit()
					: findGoldMineNear(unit.getX(), unit.getY(), 1000f);
			if (mine == null) {
				continue;
			}
			final int id;
			if ((this.pendingExpansion != null) && (this.pendingExpansion.getMine() == mine)) {
				id = this.pendingExpansion.getId();
				this.pendingExpansion = null;
			}
			else {
				id = nextTownId();
			}
			final AITown town = new AITown(id, mine.getX(), mine.getY(), mine);
			town.setOverlay(findOverlayOn(mine));
			town.setHall(findHallNear(town));
			this.towns.add(town);
		}
	}

	private int nextTownId() {
		int id = 0;
		for (final AITown town : this.towns) {
			id = Math.max(id, town.getId() + 1);
		}
		if (this.pendingExpansion != null) {
			id = Math.max(id, this.pendingExpansion.getId() + 1);
		}
		return id;
	}

	private CUnit findHallNear(final AITown town) {
		CUnit best = null;
		float bestDistance = 1100f;
		for (final CUnit unit : this.simulation.getUnits()) {
			if (isOurs(unit) && unit.getClassifications().contains(CUnitClassification.TOWNHALL)) {
				final float d = distance(town.getX(), town.getY(), unit.getX(), unit.getY());
				if (d < bestDistance) {
					bestDistance = d;
					best = unit;
				}
			}
		}
		return best;
	}

	public AITown getTown(final int id) {
		for (final AITown town : this.towns) {
			if (town.getId() == id) {
				return town;
			}
		}
		return null;
	}

	/**
	 * @return the id of the town nearest to the point within the town radius, or -1
	 */
	public int townOf(final float x, final float y) {
		int best = -1;
		float bestDistance = TOWN_RADIUS;
		for (final AITown town : this.towns) {
			final float d = Math.min(distance(x, y, town.getX(), town.getY()),
					distance(x, y, town.getCenterX(), town.getCenterY()));
			if (d < bestDistance) {
				bestDistance = d;
				best = town.getId();
			}
		}
		return best;
	}

	private boolean ownsOverlay(final AITown town) {
		return (town.getOverlay() != null) && (town.getOverlay().getPlayerIndex() == this.playerIndex);
	}

	public boolean townHasMine(final int id) {
		final AITown town = getTown(id);
		if ((town == null) || (town.getMine() == null) || (town.getGold() <= 0)) {
			return false;
		}
		return (town.getOverlay() == null) || ownsOverlay(town);
	}

	public boolean townHasHall(final int id) {
		final AITown town = getTown(id);
		return (town != null) && (town.getHall() != null) && !town.getHall().isDead();
	}

	private boolean isMineOwned(final AITown town) {
		return (town.getMine() != null) && (town.getGold() > 0)
				&& (townHasHall(town.getId()) || ownsOverlay(town)) && ((town.getOverlay() == null) || ownsOverlay(town));
	}

	public int getMinesOwned() {
		int count = 0;
		for (final AITown town : this.towns) {
			if (isMineOwned(town)) {
				count++;
			}
		}
		return count;
	}

	public int getGoldOwned() {
		int gold = 0;
		for (final AITown town : this.towns) {
			if (isMineOwned(town)) {
				gold += town.getGold();
			}
		}
		return gold;
	}

	public int townWithMine() {
		int best = -1;
		for (final AITown town : this.towns) {
			if (isMineOwned(town) && ((best == -1) || (town.getId() < best))) {
				best = town.getId();
			}
		}
		return best;
	}


	public int getNextExpansion() {
		if ((this.pendingExpansion != null) && isExpansionAvailable(this.pendingExpansion.getMine())) {
			return this.pendingExpansion.getId();
		}
		this.pendingExpansion = null;
		final AITown home = getTown(0);
		if (home == null) {
			return -1;
		}
		CUnit best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!unit.isDead() && (unit.getGoldMineData() != null) && (unit.getOverlayedGoldMineData() == null)
					&& isExpansionAvailable(unit)) {
				final float d = distance(home.getX(), home.getY(), unit.getX(), unit.getY());
				if (d < bestDistance) {
					bestDistance = d;
					best = unit;
				}
			}
		}
		if (best == null) {
			return -1;
		}
		this.pendingExpansion = new AITown(nextTownId(), best.getX(), best.getY(), best);
		return this.pendingExpansion.getId();
	}

	private boolean isExpansionAvailable(final CUnit mine) {
		if ((mine == null) || mine.isDead() || (mine.getGold() <= 0) || (findOverlayOn(mine) != null)) {
			return false;
		}
		for (final AITown town : this.towns) {
			if (town.getMine() == mine) {
				return false;
			}
		}
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!unit.isDead() && (unit.getPlayerIndex() != this.playerIndex)
					&& unit.getClassifications().contains(CUnitClassification.TOWNHALL)
					&& (distance(unit.getX(), unit.getY(), mine.getX(), mine.getY()) < 1200f)) {
				return false;
			}
		}
		return true;
	}

	public CUnit getExpansionFoe() {
		if (this.pendingExpansion == null) {
			return null;
		}
		return strongestEnemyNear(this.pendingExpansion.getX(), this.pendingExpansion.getY(), 1000f);
	}

	private CUnit strongestEnemyNear(final float x, final float y, final float radius) {
		CUnit best = null;
		for (final CUnit unit : this.simulation.getUnits()) {
			if (isEnemy(unit) && (distance(x, y, unit.getX(), unit.getY()) < radius)
					&& ((best == null) || (unit.getUnitType().getLevel() > best.getUnitType().getLevel()))) {
				best = unit;
			}
		}
		return best;
	}

	public int getExpansionX() {
		return this.pendingExpansion == null ? -1 : (int) this.pendingExpansion.getX();
	}

	public int getExpansionY() {
		return this.pendingExpansion == null ? -1 : (int) this.pendingExpansion.getY();
	}

	public CUnit getExpansionPeon() {
		if (this.pendingExpansion == null) {
			return null;
		}
		return findBuilder(null, this.pendingExpansion.getX(), this.pendingExpansion.getY(), -1);
	}

	public boolean setExpansion(final CUnit peon, final int typeId) {
		final CUnitType type = this.simulation.getUnitData().getUnitType(new War3ID(typeId));
		if ((this.pendingExpansion == null) || (peon == null) || (type == null) || !isOurs(peon)) {
			return false;
		}
		final AbilityPointTarget location = findTownStructureLocation(type, peon, this.pendingExpansion);
		if (location == null) {
			return false;
		}
		return orderBuild(peon, type, location, this.pendingExpansion.getId());
	}


	private boolean requirementsMet(final CUnitType type) {
		if (!this.player.isTechtreeAllowedByMax(type.getTypeId())) {
			return false;
		}
		for (final CUnitTypeRequirement requirement : type.getRequirements()) {
			if (this.player.getTechtreeUnlocked(requirement.getRequirement()) < requirement.getRequiredLevel()) {
				return false;
			}
		}
		if (type.isHero()) {
			if (!this.player.isHeroCountAllowedByMax(this.simulation)) {
				return false;
			}
			for (final CUnitTypeRequirement requirement : type
					.getRequirementsTier(this.player.getHeroCount(this.simulation, true))) {
				if (this.player.getTechtreeUnlocked(requirement.getRequirement()) < requirement.getRequiredLevel()) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean isReady(final CUnit unit) {
		return !unit.isConstructing() && !unit.isUpgrading() && !unit.isHidden();
	}

	public boolean setProduce(final int qty, final int typeIdValue, final int town) {
		final War3ID typeId = new War3ID(typeIdValue);
		final CUnitType type = this.simulation.getUnitData().getUnitType(typeId);
		if ((type == null) || (qty <= 0)) {
			return false;
		}
		if (type.isHero()) {
			return produceHero(type);
		}
		if (findBuilder(type, 0, 0, -1) != null) {
			return buildStructures(qty, type, town);
		}
		final List<CUnit> bases = new ArrayList<>();
		final List<CUnit> producers = new ArrayList<>();
		boolean anyBase = false;
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!isOurs(unit) || ((town != -1) && (townOf(unit.getX(), unit.getY()) != town))) {
				continue;
			}
			final CAbilityUpgrade upgrade = unit.getFirstAbilityOfType(CAbilityUpgrade.class);
			if ((upgrade != null) && upgrade.getUpgradesTo().contains(typeId)) {
				anyBase = true;
				if (isReady(unit)) {
					bases.add(unit);
				}
			}
			final CAbilityQueue queue = unit.getFirstAbilityOfType(CAbilityQueue.class);
			if ((queue != null) && queue.getUnitsTrained().contains(typeId) && isReady(unit)) {
				producers.add(unit);
			}
		}
		if (anyBase) {
			if (!requirementsMet(type)) {
				return false;
			}
			int started = 0;
			for (final CUnit base : bases) {
				if (started >= qty) {
					break;
				}
				if ((base.getBuildQueue()[0] == null) && base.order(this.simulation, typeIdValue, null)) {
					started++;
				}
			}
			return true;
		}
		if (producers.isEmpty() || !requirementsMet(type)) {
			return false;
		}
		train(producers, typeIdValue, qty);
		return true;
	}

	private void train(final List<CUnit> producers, final int typeIdValue, final int qty) {
		for (int i = 0; i < qty; i++) {
			CUnit best = null;
			int bestQueue = MAX_QUEUED_PER_PRODUCER;
			for (final CUnit producer : producers) {
				final int length = queueLength(producer);
				if (length < bestQueue) {
					bestQueue = length;
					best = producer;
				}
			}
			if ((best == null) || !best.order(this.simulation, typeIdValue, null)) {
				return;
			}
		}
	}

	private boolean produceHero(final CUnitType type) {
		CUnit altar = null;
		CUnit deadHero = null;
		final List<CUnit> producers = new ArrayList<>();
		for (final CUnit unit : this.simulation.getUnits()) {
			if (unit.getPlayerIndex() != this.playerIndex) {
				continue;
			}
			if (unit.isDead()) {
				final CAbilityHero heroData = unit.getHeroData();
				if ((heroData != null) && unit.getTypeId().equals(type.getTypeId()) && heroData.isAwaitingRevive()
						&& !heroData.isReviving()) {
					deadHero = unit;
				}
				continue;
			}
			if (!isReady(unit)) {
				continue;
			}
			if ((altar == null) && (unit.getFirstAbilityOfType(CAbilityReviveHero.class) != null)
					&& (queueLength(unit) < MAX_QUEUED_PER_PRODUCER)) {
				altar = unit;
			}
			final CAbilityQueue queue = unit.getFirstAbilityOfType(CAbilityQueue.class);
			if ((queue != null) && queue.getUnitsTrained().contains(type.getTypeId())) {
				producers.add(unit);
			}
		}
		if (deadHero != null) {
			if (altar == null) {
				return false;
			}
			altar.order(this.simulation, deadHero.getHandleId(), null);
			return true;
		}
		if (!this.newHeroes || producers.isEmpty() || !requirementsMet(type)) {
			return false;
		}
		train(producers, type.getTypeId().getValue(), 1);
		return true;
	}

	public int getUpgradeLevel(final int typeId) {
		final War3ID id = new War3ID(typeId);
		return this.player.getTechtreeUnlocked(id) + this.player.getTechtreeInProgress(id);
	}

	public int getUpgradeGoldCost(final int typeId) {
		final War3ID id = new War3ID(typeId);
		final CUpgradeType upgrade = this.simulation.getUpgradeData().getType(id);
		return upgrade == null ? 0 : upgrade.getGoldCost(this.player.getTechtreeUnlocked(id));
	}

	public int getUpgradeWoodCost(final int typeId) {
		final War3ID id = new War3ID(typeId);
		final CUpgradeType upgrade = this.simulation.getUpgradeData().getType(id);
		return upgrade == null ? 0 : upgrade.getLumberCost(this.player.getTechtreeUnlocked(id));
	}

	public boolean setUpgrade(final int typeId) {
		final War3ID id = new War3ID(typeId);
		final CUpgradeType upgrade = this.simulation.getUpgradeData().getType(id);
		if (upgrade == null) {
			return false;
		}
		if (this.player.getTechtreeInProgress(id) > 0) {
			return true;
		}
		final CUpgradeType.UpgradeLevel level = upgrade.getLevel(this.player.getTechtreeUnlocked(id));
		if (level == null) {
			return false;
		}
		for (final CUnitTypeRequirement requirement : level.getRequirements()) {
			if (this.player.getTechtreeUnlocked(requirement.getRequirement()) < requirement.getRequiredLevel()) {
				return false;
			}
		}
		CUnit best = null;
		for (final CUnit unit : this.simulation.getUnits()) {
			if (isOurs(unit) && isReady(unit)) {
				final CAbilityQueue queue = unit.getFirstAbilityOfType(CAbilityQueue.class);
				if ((queue != null) && queue.getResearchesAvailable().contains(id)
						&& ((best == null) || (queueLength(unit) < queueLength(best)))) {
					best = unit;
				}
			}
		}
		if ((best == null) || (queueLength(best) >= MAX_QUEUED_PER_PRODUCER)) {
			return false;
		}
		return best.order(this.simulation, typeId, null);
	}


	private static AbstractCAbilityBuild getBuildAbility(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof AbstractCAbilityBuild) {
				return (AbstractCAbilityBuild) ability;
			}
		}
		return null;
	}

	/**
	 * @param type null for any worker that can build
	 */
	private CUnit findBuilder(final CUnitType type, final float x, final float y, final int preferredTown) {
		CUnit best = null;
		float bestScore = Float.MAX_VALUE;
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!isOurs(unit) || unit.isHidden() || isBuildJobWorker(unit) || isBuilding(unit)
					|| this.attackCaptain.getMembers().contains(unit)) {
				continue;
			}
			final AbstractCAbilityBuild build = getBuildAbility(unit);
			if ((build == null) || ((type != null) && !build.getStructuresBuilt().contains(type.getTypeId()))) {
				continue;
			}
			float score = distance(x, y, unit.getX(), unit.getY());
			final int[] job = this.harvestJobs.get(unit);
			if (job == null) {
				score -= 4000;
			}
			else if (job[0] == WOOD) {
				score -= 2000;
			}
			if ((preferredTown != -1) && (townOf(unit.getX(), unit.getY()) != preferredTown)) {
				score += 3000;
			}
			if (score < bestScore) {
				bestScore = score;
				best = unit;
			}
		}
		return best;
	}

	private boolean buildStructures(final int qty, final CUnitType type, final int townId) {
		if (!requirementsMet(type)) {
			return false;
		}
		for (int i = 0; i < qty; i++) {
			if ((this.player.getGold() < type.getGoldCost()) || (this.player.getLumber() < type.getLumberCost())) {
				break;
			}
			AITown town = getTown(townId == -1 ? 0 : townId);
			if (type.isCanBeBuiltOnThem() && (townId == -1)) {
				town = townNeedingOverlay();
			}
			if (town == null) {
				break;
			}
			final CUnit builder = findBuilder(type, town.getCenterX(), town.getCenterY(), town.getId());
			if (builder == null) {
				break;
			}
			final AbilityPointTarget location;
			if (type.isCanBeBuiltOnThem()
					|| (type.getClassifications().contains(CUnitClassification.TOWNHALL) && !townHasHall(town.getId()))) {
				location = findTownStructureLocation(type, builder, town);
			}
			else {
				location = findBuildLocation(type, builder, town);
			}
			if ((location == null) || !orderBuild(builder, type, location, town.getId())) {
				break;
			}
		}
		return true;
	}

	private AITown townNeedingOverlay() {
		for (final AITown town : this.towns) {
			if ((town.getMine() != null) && (town.getOverlay() == null) && (town.getGold() > 0)) {
				return town;
			}
		}
		return null;
	}

	private boolean orderBuild(final CUnit builder, final CUnitType type, final AbilityPointTarget location,
			final int town) {
		if (!builder.order(this.simulation, type.getTypeId().getValue(), location)) {
			return false;
		}
		this.harvestJobs.remove(builder);
		this.buildJobs.add(new AIBuildJob(builder, type, location.x, location.y, town,
				this.simulation.getGameTurnTick()));
		return true;
	}

	private boolean isLaidDown(final AIBuildJob job) {
		for (final CUnit unit : this.simulation.getUnits()) {
			if (isOurs(unit) && unit.getTypeId().equals(job.getTypeId())
					&& (distance(unit.getX(), unit.getY(), job.getX(), job.getY()) < 64)) {
				return true;
			}
		}
		return false;
	}

	private void updateBuildJobs() {
		final int tick = this.simulation.getGameTurnTick();
		for (final Iterator<AIBuildJob> iterator = this.buildJobs.iterator(); iterator.hasNext();) {
			final AIBuildJob job = iterator.next();
			final CUnit worker = job.getWorker();
			boolean finished = worker.isDead() || isLaidDown(job)
					|| ((tick - job.getIssuedTick()) > BUILD_JOB_TIMEOUT_TICKS);
			if (!finished && ((tick - job.getIssuedTick()) > BUILD_JOB_GRACE_TICKS)) {
				final COrder order = worker.getCurrentOrder();
				finished = (order == null) || (order.getOrderId() != job.getTypeId().getValue());
				finished &= !isBuilding(worker);
			}
			if (finished) {
				iterator.remove();
			}
		}
	}

	private int halfWidth(final CUnitType type) {
		final ArgbImage map = type.getBuildingPathingPixelMap();
		return map == null ? 64 : (map.getWidth() * 16);
	}

	private int halfHeight(final CUnitType type) {
		final ArgbImage map = type.getBuildingPathingPixelMap();
		return map == null ? 64 : (map.getHeight() * 16);
	}

	private boolean isObstructed(final CUnitType type, final CUnit builder, final float x, final float y) {
		return AbstractCAbilityBuild.isBuildLocationObstructed(this.simulation, type, type.getBuildingPathingPixelMap(),
				type.isCanBeBuiltOnThem(), x, y, builder, BuildOnBuildingIntersector.INSTANCE.reset(x, y));
	}

	private boolean overlapsBuildJob(final CUnitType type, final float x, final float y) {
		for (final AIBuildJob job : this.buildJobs) {
			if ((Math.abs(job.getX() - x) < (halfWidth(type) + halfWidth(job.getType()) + 64))
					&& (Math.abs(job.getY() - y) < (halfHeight(type) + halfHeight(job.getType()) + 64))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Haunted mines go on the gold mine, town halls at a mining distance from it; the start town's hall goes back
	 * to the start location.
	 */
	private AbilityPointTarget findTownStructureLocation(final CUnitType type, final CUnit builder,
			final AITown town) {
		if (type.isCanBeBuiltOnThem()) {
			return town.getMine() == null ? null : new AbilityPointTarget(town.getMine().getX(), town.getMine().getY());
		}
		if (town.getId() == 0) {
			final AbilityPointTarget start = new AbilityPointTarget(town.getX(), town.getY());
			AbstractCAbilityBuild.roundTargetPoint(start, type);
			if (!isObstructed(type, builder, start.x, start.y)) {
				return start;
			}
		}
		final CUnit mine = town.getMine();
		if (mine == null) {
			return null;
		}
		final float minRadius = halfWidth(mine.getUnitType()) + halfWidth(type) + 160;
		final float awayAngle = (float) Math.atan2(mapCenterY() - mine.getY(), mapCenterX() - mine.getX());
		return searchLocation(type, builder, mine.getX(), mine.getY(), minRadius, minRadius + 384, awayAngle, null);
	}

	private float mapCenterX() {
		final PathingGrid grid = this.simulation.getPathingGrid();
		return (grid.getWorldX(0) + grid.getWorldX(grid.getWidth() - 1)) / 2;
	}

	private float mapCenterY() {
		final PathingGrid grid = this.simulation.getPathingGrid();
		return (grid.getWorldY(0) + grid.getWorldY(grid.getHeight() - 1)) / 2;
	}

	private AbilityPointTarget findBuildLocation(final CUnitType type, final CUnit builder, final AITown town) {
		final float cx = town.getCenterX();
		final float cy = town.getCenterY();
		final CUnit mine = town.getMine();
		float preferredAngle = (float) Math.atan2(mapCenterY() - cy, mapCenterX() - cx);
		if (mine != null) {
			preferredAngle = (float) Math.atan2(cy - mine.getY(), cx - mine.getX());
		}
		final float minRadius = Math.max(256f, 160f + halfWidth(type) + 128f);
		return searchLocation(type, builder, cx, cy, minRadius, 1800f, preferredAngle, mine);
	}

	/**
	 * Rings outward from the center, starting each ring at the preferred angle, for the first spot that is free
	 * with a cell of room around it, keeps clear of the gold mine and of the path between mine and hall, and is
	 * not taken by another worker's pending building.
	 */
	private AbilityPointTarget searchLocation(final CUnitType type, final CUnit builder, final float cx,
			final float cy, final float minRadius, final float maxRadius, final float preferredAngle,
			final CUnit avoidMine) {
		final AbilityPointTarget point = new AbilityPointTarget(0, 0);
		final float size = Math.max(halfWidth(type), halfHeight(type));
		for (float radius = minRadius; radius <= maxRadius; radius += 64f) {
			final int steps = Math.max(8, (int) ((2 * Math.PI * radius) / 96f));
			for (int step = 0; step < steps; step++) {
				final int side = ((step & 1) == 0) ? 1 : -1;
				final double angle = preferredAngle + (side * ((step + 1) / 2) * ((2 * Math.PI) / steps));
				point.x = cx + (float) (Math.cos(angle) * radius);
				point.y = cy + (float) (Math.sin(angle) * radius);
				AbstractCAbilityBuild.roundTargetPoint(point, type);
				if (avoidMine != null) {
					final float mineDistance = distance(point.x, point.y, avoidMine.getX(), avoidMine.getY());
					if ((mineDistance < (size + 320f))
							|| (distanceToSegment(point.x, point.y, cx, cy, avoidMine.getX(), avoidMine.getY()) < (size
									+ 160f))) {
						continue;
					}
				}
				if (this.attackCaptain.isHomeSet() && (distance(point.x, point.y, this.attackCaptain.getHomeX(),
						this.attackCaptain.getHomeY()) < (size + 400f))) {
					continue;
				}
				if (overlapsBuildJob(type, point.x, point.y) || isObstructed(type, builder, point.x, point.y)) {
					continue;
				}
				if (!type.isCanBeBuiltOnThem() && (isObstructed(type, builder, point.x + BUILDING_GAP, point.y)
						|| isObstructed(type, builder, point.x - BUILDING_GAP, point.y)
						|| isObstructed(type, builder, point.x, point.y + BUILDING_GAP)
						|| isObstructed(type, builder, point.x, point.y - BUILDING_GAP))) {
					continue;
				}
				return new AbilityPointTarget(point.x, point.y);
			}
		}
		return null;
	}

	private static float distanceToSegment(final float px, final float py, final float ax, final float ay,
			final float bx, final float by) {
		final float dx = bx - ax;
		final float dy = by - ay;
		final float lengthSquared = (dx * dx) + (dy * dy);
		float t = lengthSquared == 0 ? 0 : (((px - ax) * dx) + ((py - ay) * dy)) / lengthSquared;
		t = Math.max(0, Math.min(1, t));
		return distance(px, py, ax + (t * dx), ay + (t * dy));
	}


	public void clearHarvestAI() {
		this.harvestRequests.clear();
	}

	public void harvestGold(final int town, final int peons) {
		if (peons > 0) {
			this.harvestRequests.add(new int[] { GOLD, town, peons });
		}
	}

	public void harvestWood(final int town, final int peons) {
		if (peons > 0) {
			this.harvestRequests.add(new int[] { WOOD, town, peons });
		}
	}

	public void stopGathering() {
		this.harvestRequests.clear();
	}

	private boolean canDo(final CUnit worker, final int resource, final int town) {
		if (resource == WOOD) {
			return canHarvestLumber(worker);
		}
		if (!canHarvestGold(worker)) {
			return false;
		}
		final AITown aiTown = getTown(town);
		if ((aiTown == null) || (aiTown.getMine() == null) || (aiTown.getGold() <= 0)) {
			return false;
		}
		if (minesFromOverlay(worker)) {
			return ownsOverlay(aiTown) || (worker.getFirstAbilityOfType(CAbilityWispHarvest.class) != null);
		}
		return aiTown.getOverlay() == null;
	}

	private void assignHarvesters() {
		final List<CUnit> workers = new ArrayList<>();
		for (final CUnit unit : this.simulation.getUnits()) {
			if (isOurs(unit) && !isBuildJobWorker(unit) && !this.attackCaptain.getMembers().contains(unit)
					&& (canHarvestGold(unit) || canHarvestLumber(unit))) {
				workers.add(unit);
			}
		}
		this.harvestJobs.keySet().retainAll(workers);
		final List<int[]> slots = new ArrayList<>();
		for (final int[] request : this.harvestRequests) {
			for (int i = 0; i < request[2]; i++) {
				slots.add(new int[] { request[0], request[1] });
			}
		}
		final Set<CUnit> claimed = new HashSet<>();
		final CUnit[] slotWorkers = new CUnit[slots.size()];
		for (int s = 0; s < slots.size(); s++) {
			final int[] slot = slots.get(s);
			for (final CUnit worker : workers) {
				final int[] job = this.harvestJobs.get(worker);
				if (!claimed.contains(worker) && (job != null) && (job[0] == slot[0]) && (job[1] == slot[1])) {
					claimed.add(worker);
					slotWorkers[s] = worker;
					break;
				}
			}
		}
		for (int s = 0; s < slots.size(); s++) {
			if (slotWorkers[s] != null) {
				continue;
			}
			final int[] slot = slots.get(s);
			final AITown town = getTown(slot[1]);
			if (town == null) {
				continue;
			}
			CUnit best = null;
			float bestScore = Float.MAX_VALUE;
			for (final CUnit worker : workers) {
				if (claimed.contains(worker) || worker.isHidden() || isBuilding(worker)
						|| !canDo(worker, slot[0], slot[1])) {
					continue;
				}
				float score = distance(worker.getX(), worker.getY(), town.getCenterX(), town.getCenterY());
				if (this.harvestJobs.containsKey(worker)) {
					score += 5000;
				}
				if (!isWorkerType(worker) && !isIdle(worker)) {
					score += 10000;
				}
				if (score < bestScore) {
					bestScore = score;
					best = worker;
				}
			}
			if (best != null) {
				claimed.add(best);
				slotWorkers[s] = best;
				this.harvestJobs.put(best, slot);
				orderHarvest(best, slot[0], slot[1]);
			}
		}
		for (int s = 0; s < slots.size(); s++) {
			final CUnit worker = slotWorkers[s];
			if ((worker != null) && !worker.isHidden() && isIdle(worker)) {
				orderHarvest(worker, slots.get(s)[0], slots.get(s)[1]);
			}
		}
		for (final CUnit worker : workers) {
			if (claimed.contains(worker)) {
				continue;
			}
			if (!isWorkerType(worker)) {
				this.harvestJobs.remove(worker);
			}
			else if (!worker.isHidden() && isIdle(worker) && !this.harvestRequests.isEmpty()) {
				final int[] job = canDo(worker, WOOD, 0) ? new int[] { WOOD, 0 } : new int[] { GOLD, 0 };
				if (canDo(worker, job[0], job[1])) {
					this.harvestJobs.put(worker, job);
					orderHarvest(worker, job[0], job[1]);
				}
			}
		}
		if (this.peonsRepair) {
			assignRepairs(workers);
		}
	}

	private void orderHarvest(final CUnit worker, final int resource, final int townId) {
		final AITown town = getTown(townId);
		if (town == null) {
			return;
		}
		if (resource == GOLD) {
			if (minesFromOverlay(worker)) {
				if (ownsOverlay(town)) {
					worker.order(this.simulation, OrderIds.harvest, town.getOverlay());
				}
				else if (town.getOverlay() == null) {
					entangle(town);
				}
			}
			else if (town.getMine() != null) {
				worker.order(this.simulation, OrderIds.harvest, town.getMine());
			}
			return;
		}
		final List<CDestructable> trees = getTrees(town);
		int tries = 0;
		for (final Iterator<CDestructable> iterator = trees.iterator(); iterator.hasNext() && (tries < 6);) {
			final CDestructable tree = iterator.next();
			if (tree.isDead()) {
				iterator.remove();
				continue;
			}
			tries++;
			if (worker.order(this.simulation, OrderIds.harvest, tree)) {
				return;
			}
		}
	}

	private void entangle(final AITown town) {
		final int entangleOrder = OrderIdUtils.getOrderId("entangleinstant");
		for (final CUnit unit : this.simulation.getUnits()) {
			if (isOurs(unit) && isReady(unit) && unit.getClassifications().contains(CUnitClassification.TOWNHALL)
					&& (distance(unit.getX(), unit.getY(), town.getMine().getX(), town.getMine().getY()) < 1200)) {
				unit.order(this.simulation, entangleOrder, town.getMine());
				return;
			}
		}
	}

	private List<CDestructable> getTrees(final AITown town) {
		final Integer cachedTick = this.townTreesTick.get(town.getId());
		List<CDestructable> trees = this.townTrees.get(town.getId());
		if ((trees == null) || (cachedTick == null) || ((this.ticks - cachedTick) > TREE_CACHE_TICKS)
				|| trees.isEmpty()) {
			trees = new ArrayList<>();
			final float cx = town.getCenterX();
			final float cy = town.getCenterY();
			for (final CDestructable destructable : this.simulation.getDestructables()) {
				if (!destructable.isDead() && destructable.getDestType().getTargetedAs().contains(CTargetType.TREE)
						&& (distance(cx, cy, destructable.getX(), destructable.getY()) < 2500)) {
					trees.add(destructable);
				}
			}
			trees.sort((a, b) -> Float.compare(distance(cx, cy, a.getX(), a.getY()),
					distance(cx, cy, b.getX(), b.getY())));
			this.townTrees.put(town.getId(), trees);
			this.townTreesTick.put(town.getId(), this.ticks);
		}
		return trees;
	}

	private void assignRepairs(final List<CUnit> workers) {
		if (this.threat != null) {
			return;
		}
		for (final CUnit worker : workers) {
			if (isBuilding(worker) && !isBuildJobWorker(worker)) {
				return;
			}
		}
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!isOurs(unit) || !unit.isBuilding() || unit.isConstructing() || unit.isUpgrading()
					|| (unit.getLife() > (unit.getMaxLife() * 0.6f))) {
				continue;
			}
			CUnit best = null;
			float bestDistance = 1500f;
			for (final CUnit worker : workers) {
				final int[] job = this.harvestJobs.get(worker);
				if (worker.isHidden() || (job == null) || (job[0] != WOOD)) {
					continue;
				}
				final float d = distance(worker.getX(), worker.getY(), unit.getX(), unit.getY());
				if (d < bestDistance) {
					bestDistance = d;
					best = worker;
				}
			}
			if ((best != null) && best.order(this.simulation, repairOrderId(best), unit)) {
				return;
			}
		}
	}


	public int getLevelingHeroId() {
		return this.levelingHero == null ? 0 : this.levelingHero.getTypeId().getValue();
	}

	public int getLevelingHeroLevel() {
		return this.levelingHeroLevel;
	}

	private void updateHeroSkills() {
		if (this.heroLevelsFunction == null) {
			return;
		}
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!isOurs(unit)) {
				continue;
			}
			final CAbilityHero heroData = unit.getHeroData();
			if (heroData == null) {
				continue;
			}
			for (int guard = 0; (guard < 4) && (heroData.getSkillPoints() > 0); guard++) {
				final int before = heroData.getSkillPoints();
				this.levelingHero = unit;
				this.levelingHeroLevel = Math.max(1, (heroData.getHeroLevel() - before) + 1);
				int skill = 0;
				try {
					final JassThread thread = this.globalScope
							.createThreadCapturingReturnValue(this.heroLevelsFunction, TriggerExecutionScope.EMPTY);
					final JassValue value = this.globalScope.runThreadUntilCompletionAndReadReturnValue(thread,
							"SetHeroLevels", IntegerJassValue.ZERO);
					if (value != null) {
						skill = value.visit(IntegerJassValueVisitor.getInstance());
					}
				}
				finally {
					this.levelingHero = null;
				}
				if (skill != 0) {
					heroData.selectHeroSkill(this.simulation, unit, new War3ID(skill));
				}
				if (heroData.getSkillPoints() == before) {
					for (final War3ID other : unit.getUnitType().getHeroAbilityList()) {
						heroData.selectHeroSkill(this.simulation, unit, other);
						if (heroData.getSkillPoints() != before) {
							break;
						}
					}
				}
				if (heroData.getSkillPoints() == before) {
					break;
				}
			}
		}
	}


	public void createCaptains() {
		resetCaptainLocations();
	}

	public void resetCaptainLocations() {
		final AITown town = getTown(0);
		final float[] start = this.player.getStartLocation();
		float x = town == null ? start[0] : town.getCenterX();
		float y = town == null ? start[1] : town.getCenterY();
		final double angle = Math.atan2(mapCenterY() - y, mapCenterX() - x);
		x += (float) (Math.cos(angle) * 500);
		y += (float) (Math.sin(angle) * 500);
		this.attackCaptain.setHome(x, y);
		this.defenseCaptain.setHome(x, y);
	}

	public void setCaptainHome(final int which, final float x, final float y) {
		if ((which & ATTACK_CAPTAIN) != 0) {
			this.attackCaptain.setHome(x, y);
		}
		if ((which & DEFENSE_CAPTAIN) != 0) {
			this.defenseCaptain.setHome(x, y);
		}
	}

	public void shiftTownSpot(final float x, final float y) {
		setCaptainHome(ATTACK_CAPTAIN | DEFENSE_CAPTAIN, x, y);
	}

	public void teleportCaptain(final float x, final float y) {
		for (final CUnit unit : this.attackCaptain.getMembers()) {
			unit.setPointAndCheckUnstuck(x, y, this.simulation);
		}
	}

	public void initAssault() {
		this.attackCaptain.beginAssault();
	}

	private boolean isInjured(final CUnit unit) {
		return this.injured.contains(unit) || (this.ignoreInjured && (unit.getLife() < (unit.getMaxLife() * 0.4f)));
	}

	public boolean addAssault(final int qty, final int typeIdValue) {
		final War3ID typeId = new War3ID(typeIdValue);
		final List<CUnit> members = this.attackCaptain.getMembers();
		int got = 0;
		for (final CUnit unit : members) {
			if (unit.getTypeId().equals(typeId)) {
				got++;
			}
		}
		for (final CUnit unit : this.attackCaptain.getPreviousMembers()) {
			if (got >= qty) {
				break;
			}
			if (unit.getTypeId().equals(typeId) && isArmyUnit(unit) && !members.contains(unit) && !isInjured(unit)) {
				members.add(unit);
				got++;
			}
		}
		if (got < qty) {
			for (final CUnit unit : this.simulation.getUnits()) {
				if (got >= qty) {
					break;
				}
				if (unit.getTypeId().equals(typeId) && isArmyUnit(unit) && !unit.isConstructing()
						&& !members.contains(unit) && !isInjured(unit)) {
					members.add(unit);
					got++;
				}
			}
		}
		for (final CUnit unit : this.simulation.getUnits()) {
			if (unit.getTypeId().equals(typeId) && isArmyUnit(unit) && !members.contains(unit) && isInjured(unit)) {
				this.injured.add(unit);
			}
		}
		int resting = 0;
		for (final CUnit unit : this.injured) {
			if (unit.getTypeId().equals(typeId) && !members.contains(unit)) {
				resting++;
			}
		}
		final boolean satisfied = (got + resting) >= qty;
		this.attackCaptain.addRequestResult(satisfied);
		return satisfied;
	}

	public boolean addDefenders(final int qty, final int typeIdValue) {
		return getUnitCount(typeIdValue, true) >= qty;
	}

	public void removeInjuries() {
		for (final Iterator<CUnit> iterator = this.attackCaptain.getMembers().iterator(); iterator.hasNext();) {
			final CUnit unit = iterator.next();
			if (unit.getLife() < (unit.getMaxLife() * 0.5f)) {
				iterator.remove();
				this.injured.add(unit);
			}
		}
	}

	public void removeSiege() {
		for (final Iterator<CUnit> iterator = this.attackCaptain.getMembers().iterator(); iterator.hasNext();) {
			final CUnit unit = iterator.next();
			for (final War3ID siege : SIEGE_UNITS) {
				if (siege.equals(unit.getTypeId())) {
					iterator.remove();
					break;
				}
			}
		}
	}

	private boolean isGathered(final CUnit unit) {
		return distance(unit.getX(), unit.getY(), this.attackCaptain.getHomeX(),
				this.attackCaptain.getHomeY()) < (HOME_RADIUS + 300);
	}

	public boolean isCaptainFull() {
		if (!this.attackCaptain.isRequestsFull()) {
			return false;
		}
		if (!this.attackCaptain.hasGoal()) {
			for (final CUnit unit : this.attackCaptain.getMembers()) {
				if (!isGathered(unit)) {
					return false;
				}
			}
		}
		return true;
	}

	public boolean isCaptainEmpty() {
		pruneMembers();
		return this.attackCaptain.getMembers().isEmpty();
	}

	public int getCaptainGroupSize() {
		pruneMembers();
		return this.attackCaptain.getMembers().size();
	}

	public int getCaptainReadinessHP() {
		pruneMembers();
		final List<CUnit> members = this.attackCaptain.getMembers();
		if (members.isEmpty()) {
			return 0;
		}
		float sum = 0;
		for (final CUnit unit : members) {
			sum += unit.getLife() / unit.getMaxLife();
		}
		return (int) ((sum * 100) / members.size());
	}

	public int getCaptainReadinessMa() {
		pruneMembers();
		float sum = 0;
		int count = 0;
		for (final CUnit unit : this.attackCaptain.getMembers()) {
			if (unit.getMaximumMana() > 0) {
				sum += unit.getMana() / unit.getMaximumMana();
				count++;
			}
		}
		return count == 0 ? 100 : (int) ((sum * 100) / count);
	}

	public int getCaptainReadiness() {
		return getCaptainReadinessHP();
	}

	public boolean isCaptainRetreating() {
		return this.attackCaptain.isRetreating();
	}

	public boolean isCaptainAtGoal() {
		pruneMembers();
		if (!this.attackCaptain.hasGoal()) {
			return false;
		}
		return distance(this.attackCaptain.getCenterX(), this.attackCaptain.getCenterY(),
				this.attackCaptain.getGoalX(), this.attackCaptain.getGoalY()) < 600;
	}

	public boolean isCaptainHome() {
		pruneMembers();
		return distance(this.attackCaptain.getCenterX(), this.attackCaptain.getCenterY(),
				this.attackCaptain.getHomeX(), this.attackCaptain.getHomeY()) < HOME_RADIUS;
	}

	public boolean isCaptainInCombat(final boolean attackCaptain) {
		pruneMembers();
		if (!attackCaptain) {
			if (this.threat != null) {
				return true;
			}
			for (final CUnit unit : this.simulation.getUnits()) {
				if (isArmyUnit(unit) && !this.attackCaptain.getMembers().contains(unit)
						&& (unit.getCurrentBehavior() instanceof CBehaviorAttack)) {
					return true;
				}
			}
			return false;
		}
		for (final CUnit unit : this.attackCaptain.getMembers()) {
			if (unit.getCurrentBehavior() instanceof CBehaviorAttack) {
				return true;
			}
		}
		final List<CUnit> members = this.attackCaptain.getMembers();
		final int checks = Math.min(members.size(), 8);
		for (int i = 0; i < checks; i++) {
			final CUnit member = members.get((i * members.size()) / checks);
			if (enemyNear(member.getX(), member.getY(), 500f) != null) {
				return true;
			}
		}
		return false;
	}

	private CUnit enemyNear(final float x, final float y, final float radius) {
		final CUnit[] found = new CUnit[1];
		this.simulation.getWorldCollision().enumUnitsInRange(x, y, radius, (unit) -> {
			if (isEnemy(unit) && unit.isVisible(this.simulation, this.playerIndex)) {
				found[0] = unit;
				return true;
			}
			return false;
		});
		return found[0];
	}

	public void attackMoveKill(final CUnit target) {
		if ((target == null) || target.isDead()) {
			return;
		}
		if (this.unreachable.contains(target)) {
			retreat();
			return;
		}
		this.attackCaptain.setGoal(target, target.getX(), target.getY());
		this.attackCaptain.setRetreating(false);
		driveAttackCaptain();
	}

	public void attackMoveXY(final float x, final float y) {
		this.attackCaptain.setGoal(null, x, y);
		this.attackCaptain.setRetreating(false);
		driveAttackCaptain();
	}

	public void captainGoHome() {
		this.attackCaptain.clearGoal();
		for (final CUnit unit : this.attackCaptain.getMembers()) {
			unit.order(this.simulation, OrderIds.move,
					new AbilityPointTarget(this.attackCaptain.getHomeX(), this.attackCaptain.getHomeY()));
		}
	}

	public void clearCaptainTargets() {
		this.attackCaptain.clearGoal();
	}

	public boolean suicidePlayer(final CPlayer target, final boolean checkFull, final boolean buildings) {
		if ((target == null) || (checkFull && !isCaptainFull())) {
			return false;
		}
		final CUnit unit = nearestUnitOf(target.getId(), buildings);
		if (unit == null) {
			return false;
		}
		attackMoveKill(unit);
		return true;
	}

	public void captainVs(final CPlayer target, final boolean buildings) {
		if (target != null) {
			final CUnit unit = nearestUnitOf(target.getId(), buildings);
			if (unit != null) {
				attackMoveKill(unit);
			}
		}
	}

	private CUnit nearestUnitOf(final int ownerIndex, final boolean buildings) {
		final float x = this.attackCaptain.getCenterX();
		final float y = this.attackCaptain.getCenterY();
		CUnit best = null;
		float bestScore = Float.MAX_VALUE;
		for (final CUnit unit : this.simulation.getUnits()) {
			if ((unit.getPlayerIndex() != ownerIndex) || unit.isDead() || unit.isHidden()) {
				continue;
			}
			if (buildings && !unit.isBuilding()) {
				continue;
			}
			float score = distance(x, y, unit.getX(), unit.getY());
			if (buildings && unit.getClassifications().contains(CUnitClassification.TOWNHALL)) {
				score -= 2000;
			}
			if (score < bestScore) {
				bestScore = score;
				best = unit;
			}
		}
		return best;
	}

	public void suicideUnit(final int count, final int typeIdValue, final int targetPlayer) {
		final War3ID typeId = new War3ID(typeIdValue);
		int sent = 0;
		for (final CUnit unit : this.simulation.getUnits()) {
			if (sent >= count) {
				break;
			}
			if (isArmyUnit(unit) && unit.getTypeId().equals(typeId)
					&& !this.attackCaptain.getMembers().contains(unit)) {
				final CUnit target = targetPlayer >= 0 ? nearestUnitOf(targetPlayer, true) : getEnemyBase();
				if (target != null) {
					unit.order(this.simulation, OrderIds.attack, new AbilityPointTarget(target.getX(), target.getY()));
					sent++;
				}
			}
		}
	}

	private void pruneMembers() {
		for (final Iterator<CUnit> iterator = this.attackCaptain.getMembers().iterator(); iterator.hasNext();) {
			final CUnit unit = iterator.next();
			if (!isOurs(unit) || unit.isHidden()) {
				iterator.remove();
			}
		}
		this.injured.removeIf((unit) -> !isOurs(unit) || (unit.getLife() >= (unit.getMaxLife() * 0.85f)));
	}

	private void updateCaptains() {
		pruneMembers();
		this.attackCaptain.forgetOrders();
		final AICaptain attack = this.attackCaptain;
		if ((attack.getGoalUnit() != null)
				&& (attack.getGoalUnit().isDead() || attack.getGoalUnit().isHidden())) {
			attack.clearGoal();
		}
		final AbilityPointTarget home = new AbilityPointTarget(attack.getHomeX(), attack.getHomeY());
		for (final Iterator<CUnit> iterator = attack.getMembers().iterator(); iterator.hasNext();) {
			final CUnit unit = iterator.next();
			final boolean hero = unit.getHeroData() != null;
			final float lifeRatio = unit.getLife() / unit.getMaxLife();
			if ((hero && this.heroesFlee && (lifeRatio < 0.25f)) || (!hero && this.unitsFlee && (lifeRatio < 0.15f))) {
				iterator.remove();
				this.injured.add(unit);
				unit.order(this.simulation, OrderIds.move, home);
			}
		}
		if (this.groupsFlee && attack.hasGoal() && !attack.getMembers().isEmpty()
				&& (getCaptainReadinessHP() < 30) && isCaptainInCombat(true)) {
			retreat();
		}
		if (attack.isRetreating() && isCaptainHome() && ((this.ticks - this.retreatTick) > 100)) {
			attack.setRetreating(false);
		}
		if (attack.hasGoal()) {
			checkStuck();
		}
		if (attack.hasGoal()) {
			driveAttackCaptain();
		}
		else if (!attack.isRetreating()) {
			this.gatherOrdered.retainAll(attack.getMembers());
			for (final CUnit unit : attack.getMembers()) {
				if (isGathered(unit) || (unit.getCurrentBehavior() instanceof CBehaviorAttack)) {
					continue;
				}
				if ((!this.gatherOrdered.contains(unit) || isIdle(unit))
						&& unit.order(this.simulation, OrderIds.attack, home)) {
					this.gatherOrdered.add(unit);
				}
			}
		}
		if (attack.hasGoal()) {
			this.gatherOrdered.clear();
		}
		for (final CUnit unit : this.injured) {
			if (isIdle(unit) && (distance(unit.getX(), unit.getY(), home.x, home.y) > HOME_RADIUS)) {
				unit.order(this.simulation, OrderIds.move, home);
			}
		}
		updateDefense(home);
	}

	/**
	 * Units that cannot get any closer to their target keep trying to path to it; they are sent to attack-move
	 * instead, and a target the whole group cannot reach is given up so the scripts move on.
	 */
	private void checkStuck() {
		final AICaptain attack = this.attackCaptain;
		this.lastPositions.keySet().retainAll(attack.getMembers());
		final AbilityPointTarget goal = new AbilityPointTarget(attack.getGoalX(), attack.getGoalY());
		for (final CUnit unit : attack.getMembers()) {
			float[] last = this.lastPositions.get(unit);
			if (last == null) {
				last = new float[] { unit.getX(), unit.getY(), 0 };
				this.lastPositions.put(unit, last);
				continue;
			}
			final boolean moving = (unit.getCurrentBehavior() instanceof CBehaviorMove)
					&& !((CBehaviorMove) unit.getCurrentBehavior()).isPathfindingActive();
			if (moving && (distance(last[0], last[1], unit.getX(), unit.getY()) < 8)) {
				last[2]++;
			}
			else {
				last[2] = 0;
			}
			last[0] = unit.getX();
			last[1] = unit.getY();
			if ((last[2] > 0) && ((last[2] % 6) == 0) && ((attack.getGoalUnit() == null) || (distance(unit.getX(),
					unit.getY(), attack.getGoalUnit().getX(), attack.getGoalUnit().getY()) >= 900))) {
				unit.order(this.simulation, OrderIds.stop, null);
				unit.order(this.simulation, OrderIds.attack, legTarget(unit, goal));
				attack.markOrdered(unit);
			}
		}
		final float goalDistance = distance(attack.getCenterX(), attack.getCenterY(), goal.x, goal.y);
		if ((this.goalProgressGeneration != attack.getGeneration()) || (goalDistance < (this.bestGoalDistance - 150))) {
			this.goalProgressGeneration = attack.getGeneration();
			this.bestGoalDistance = goalDistance;
			this.goalProgressTick = this.ticks;
		}
		else if (isCaptainInCombat(true)) {
			this.goalProgressTick = this.ticks;
		}
		else if ((this.ticks - this.goalProgressTick) > GIVE_UP_TICKS) {
			if ((attack.getGoalUnit() != null)
					&& this.simulation.isNeutralAggressive(attack.getGoalUnit().getPlayerIndex())) {
				this.unreachable.add(attack.getGoalUnit());
			}
			this.lastPositions.clear();
			retreat();
		}
	}

	private void retreat() {
		final AICaptain attack = this.attackCaptain;
		attack.clearGoal();
		attack.setRetreating(true);
		this.retreatTick = this.ticks;
		final AbilityPointTarget home = new AbilityPointTarget(attack.getHomeX(), attack.getHomeY());
		for (final CUnit unit : attack.getMembers()) {
			unit.order(this.simulation, OrderIds.move, home);
		}
	}

	private void driveAttackCaptain() {
		final AICaptain attack = this.attackCaptain;
		final CUnit goalUnit = attack.getGoalUnit();
		final AbilityPointTarget goal = new AbilityPointTarget(attack.getGoalX(), attack.getGoalY());
		this.orderedTargets.keySet().retainAll(attack.getMembers());
		for (final CUnit unit : attack.getMembers()) {
			// Sleeping creeps are not picked up by attack-move, so the target itself is attacked once close.
			if ((goalUnit != null) && (distance(unit.getX(), unit.getY(), goalUnit.getX(), goalUnit.getY()) < 900)) {
				if ((unit.getCurrentBehavior() instanceof CBehaviorAttack)
						|| ((this.orderedTargets.get(unit) == goalUnit) && !isIdle(unit) && !isStuck(unit))) {
					continue;
				}
				if (unit.order(this.simulation, OrderIds.attack, goalUnit)) {
					this.orderedTargets.put(unit, goalUnit);
					attack.markOrdered(unit);
					continue;
				}
			}
			if (!attack.needsOrder(unit) && !isIdle(unit)) {
				continue;
			}
			this.orderedTargets.remove(unit);
			final AbilityPointTarget leg = legTarget(unit, goal);
			if (unit.order(this.simulation, OrderIds.attack, leg)) {
				attack.markOrdered(unit);
			}
			else if (unit.order(this.simulation, OrderIds.move, leg)) {
				attack.markOrdered(unit);
			}
		}
	}

	/**
	 * The engine's path search gives up on goals across the map, so far goals are approached in legs: a pathable
	 * point a few screens ahead on the way, and the next leg once the unit gets there and stops.
	 */
	private AbilityPointTarget legTarget(final CUnit unit, final AbilityPointTarget goal) {
		final float d = distance(unit.getX(), unit.getY(), goal.x, goal.y);
		if (d <= MAX_LEG) {
			return goal;
		}
		final float cx = unit.getX() + (((goal.x - unit.getX()) * MAX_LEG) / d);
		final float cy = unit.getY() + (((goal.y - unit.getY()) * MAX_LEG) / d);
		final PathingGrid grid = this.simulation.getPathingGrid();
		final PathingGrid.MovementType movementType = unit.getMovementType();
		final float collisionSize = unit.getUnitType().getCollisionSize();
		for (float radius = 0; radius <= 768; radius += 64) {
			final int steps = radius == 0 ? 1 : 12;
			for (int step = 0; step < steps; step++) {
				final double angle = (2 * Math.PI * step) / steps;
				final float x = cx + (float) (Math.cos(angle) * radius);
				final float y = cy + (float) (Math.sin(angle) * radius);
				if (grid.contains(x, y) && ((movementType == null) || grid.isPathable(x, y, movementType, collisionSize))) {
					return new AbilityPointTarget(x, y);
				}
			}
		}
		return goal;
	}

	private boolean isStuck(final CUnit unit) {
		final float[] last = this.lastPositions.get(unit);
		return (last != null) && (last[2] >= 6);
	}

	private CUnit findThreat() {
		CUnit best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final AITown town : this.towns) {
			if (town.getHall() == null) {
				continue;
			}
			final float x = town.getCenterX();
			final float y = town.getCenterY();
			final CUnit[] found = new CUnit[1];
			final float[] foundDistance = { THREAT_RADIUS };
			this.simulation.getWorldCollision().enumUnitsInRange(x, y, THREAT_RADIUS, (unit) -> {
				if (isEnemy(unit) && !this.simulation.isNeutralAggressive(unit.getPlayerIndex())
						&& unit.isVisible(this.simulation, this.playerIndex)) {
					final float d = distance(x, y, unit.getX(), unit.getY());
					if (d < foundDistance[0]) {
						foundDistance[0] = d;
						found[0] = unit;
					}
				}
				return false;
			});
			if ((found[0] != null) && (foundDistance[0] < bestDistance)) {
				bestDistance = foundDistance[0];
				best = found[0];
			}
		}
		return best;
	}

	public boolean isTownThreatened() {
		return this.threat != null;
	}

	private void updateDefense(final AbilityPointTarget home) {
		this.threat = findThreat();
		final boolean attackIdle = !this.attackCaptain.hasGoal() && !this.attackCaptain.isRetreating();
		final List<CUnit> defenders = this.defenseCaptain.getMembers();
		defenders.clear();
		for (final CUnit unit : this.simulation.getUnits()) {
			if (isArmyUnit(unit) && !unit.isConstructing() && !this.injured.contains(unit)
					&& (attackIdle || !this.attackCaptain.getMembers().contains(unit))) {
				defenders.add(unit);
			}
		}
		if (this.threat != null) {
			final AbilityPointTarget target = new AbilityPointTarget(this.threat.getX(), this.threat.getY());
			for (final CUnit unit : defenders) {
				if (!(unit.getCurrentBehavior() instanceof CBehaviorAttack)) {
					unit.order(this.simulation, OrderIds.attack, target);
				}
			}
			return;
		}
		for (final CUnit unit : defenders) {
			if (isIdle(unit) && (distance(unit.getX(), unit.getY(), home.x, home.y) > HOME_RADIUS)
					&& !this.attackCaptain.getMembers().contains(unit)) {
				unit.order(this.simulation, OrderIds.attack, home);
			}
		}
	}


	public CUnit getCreepCamp(final int minLevel, final int maxLevel, final boolean flyersOk) {
		final List<CUnit> creeps = new ArrayList<>();
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!unit.isDead() && !unit.isHidden() && this.simulation.isNeutralAggressive(unit.getPlayerIndex())
					&& !unit.isBuilding()) {
				creeps.add(unit);
			}
		}
		final float homeX = this.attackCaptain.getHomeX();
		final float homeY = this.attackCaptain.getHomeY();
		final Set<CUnit> visited = new HashSet<>();
		CUnit best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final CUnit seed : creeps) {
			if (visited.contains(seed)) {
				continue;
			}
			final List<CUnit> camp = new ArrayList<>();
			camp.add(seed);
			visited.add(seed);
			for (int i = 0; i < camp.size(); i++) {
				final CUnit member = camp.get(i);
				for (final CUnit other : creeps) {
					if (!visited.contains(other) && (distance(member.getX(), member.getY(), other.getX(),
							other.getY()) < CAMP_LINK_DISTANCE)) {
						visited.add(other);
						camp.add(other);
					}
				}
			}
			int level = 0;
			boolean flyers = false;
			CUnit strongest = null;
			float x = 0;
			float y = 0;
			for (final CUnit member : camp) {
				level += member.getUnitType().getLevel();
				flyers |= member.getUnitType().getMovementType() == PathingGrid.MovementType.FLY;
				if ((strongest == null) || (member.getUnitType().getLevel() > strongest.getUnitType().getLevel())) {
					strongest = member;
				}
				x += member.getX();
				y += member.getY();
			}
			x /= camp.size();
			y /= camp.size();
			boolean reachable = true;
			for (final CUnit member : camp) {
				reachable &= !this.unreachable.contains(member);
			}
			if ((level < minLevel) || (level > maxLevel) || (flyers && !flyersOk) || !reachable
					|| nearEnemyBase(x, y)) {
				continue;
			}
			final float d = distance(homeX, homeY, x, y);
			if (d < bestDistance) {
				bestDistance = d;
				best = strongest;
			}
		}
		return best;
	}

	private boolean nearEnemyBase(final float x, final float y) {
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!unit.isDead() && unit.isBuilding() && isOpponent(unit.getPlayerIndex())
					&& (distance(x, y, unit.getX(), unit.getY()) < 1500)) {
				return true;
			}
		}
		return false;
	}

	public boolean creepsOnMap() {
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!unit.isDead() && this.simulation.isNeutralAggressive(unit.getPlayerIndex())) {
				return true;
			}
		}
		return false;
	}

	public void startGetEnemyBase() {
		this.enemyBase = findEnemyBase(-1);
	}

	public CUnit getEnemyBase() {
		if ((this.enemyBase == null) || this.enemyBase.isDead()) {
			this.enemyBase = findEnemyBase(-1);
		}
		return this.enemyBase;
	}

	/**
	 * @param onlyPlayer -1 for the nearest opponent
	 * @return the town hall nearest to that opponent's start location, else any of its buildings
	 */
	private CUnit findEnemyBase(final int onlyPlayer) {
		final AITown home = getTown(0);
		final float[] start = this.player.getStartLocation();
		final float hx = home == null ? start[0] : home.getX();
		final float hy = home == null ? start[1] : home.getY();
		CUnit best = null;
		float bestScore = Float.MAX_VALUE;
		for (final CUnit unit : this.simulation.getUnits()) {
			if (unit.isDead() || !unit.isBuilding() || !isOpponent(unit.getPlayerIndex())
					|| ((onlyPlayer != -1) && (unit.getPlayerIndex() != onlyPlayer))) {
				continue;
			}
			final float[] enemyStart = this.simulation.getPlayer(unit.getPlayerIndex()).getStartLocation();
			float score = distance(hx, hy, enemyStart[0], enemyStart[1])
					+ distance(unit.getX(), unit.getY(), enemyStart[0], enemyStart[1]);
			if (!unit.getClassifications().contains(CUnitClassification.TOWNHALL)) {
				score += 3000;
			}
			if (score < bestScore) {
				bestScore = score;
				best = unit;
			}
		}
		return best;
	}

	public CUnit getEnemyExpansion() {
		final AITown home = getTown(0);
		if (home == null) {
			return null;
		}
		CUnit best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final CUnit unit : this.simulation.getUnits()) {
			if (unit.isDead() || !isOpponent(unit.getPlayerIndex())
					|| !(unit.getClassifications().contains(CUnitClassification.TOWNHALL)
							|| (unit.getOverlayedGoldMineData() != null))) {
				continue;
			}
			final float[] enemyStart = this.simulation.getPlayer(unit.getPlayerIndex()).getStartLocation();
			if (distance(unit.getX(), unit.getY(), enemyStart[0], enemyStart[1]) < 1500) {
				continue;
			}
			final float d = distance(home.getX(), home.getY(), unit.getX(), unit.getY());
			if (d < bestDistance) {
				bestDistance = d;
				best = unit;
			}
		}
		return best;
	}

	private int armyPower(final int ownerIndex) {
		int power = 0;
		for (final CUnit unit : this.simulation.getUnits()) {
			if ((unit.getPlayerIndex() == ownerIndex) && !unit.isDead() && !unit.isBuilding()
					&& !isWorkerType(unit)) {
				final CAbilityHero heroData = unit.getHeroData();
				power += heroData != null ? heroData.getHeroLevel() * 2 : unit.getUnitType().getLevel();
			}
		}
		return power;
	}

	public int getEnemyPower() {
		int power = 0;
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			if (isOpponent(i)) {
				power += armyPower(i);
			}
		}
		return power;
	}

	/**
	 * An opponent whose army is far weaker than ours is worth an all-out attack on its main base.
	 */
	public CUnit getMegaTarget() {
		final int ours = armyPower(this.playerIndex);
		if (ours < 20) {
			return null;
		}
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			if (isOpponent(i) && ((armyPower(i) * 3) <= ours)) {
				final CUnit base = findEnemyBase(i);
				if (base != null) {
					return base;
				}
			}
		}
		return null;
	}

	public boolean isTowered(final CUnit target) {
		if (target == null) {
			return false;
		}
		for (final CUnit unit : this.simulation.getUnits()) {
			if (!unit.isDead() && unit.isBuilding() && !unit.isConstructing() && isEnemyPlayer(unit.getPlayerIndex())
					&& !unit.getUnitType().getAttacks().isEmpty()
					&& (distance(unit.getX(), unit.getY(), target.getX(), target.getY()) < 1000)) {
				return true;
			}
		}
		return false;
	}

	public CUnit getBuilding(final CPlayer owner) {
		if (owner == null) {
			return null;
		}
		for (final CUnit unit : this.simulation.getUnits()) {
			if ((unit.getPlayerIndex() == owner.getId()) && !unit.isDead() && unit.isBuilding()) {
				return unit;
			}
		}
		return null;
	}

	public void setAllianceTarget(final CUnit target) {
		this.allianceTarget = target;
	}

	public CUnit getOwnAllianceTarget() {
		if ((this.allianceTarget != null) && this.allianceTarget.isDead()) {
			this.allianceTarget = null;
		}
		return this.allianceTarget;
	}


	public boolean mergeUnits(final int qty, final int typeA, final int typeB, final int result) {
		if (getUnitCount(result, false) >= qty) {
			return true;
		}
		final War3ID a = new War3ID(typeA);
		final War3ID b = new War3ID(typeB);
		CUnit first = null;
		CUnit second = null;
		for (final CUnit unit : this.simulation.getUnits()) {
			if (isArmyUnit(unit) && isIdle(unit) && !this.attackCaptain.getMembers().contains(unit)) {
				if ((first == null) && unit.getTypeId().equals(a)) {
					first = unit;
				}
				else if ((second == null) && unit.getTypeId().equals(b)) {
					second = unit;
				}
			}
		}
		if ((first == null) || (second == null)) {
			return false;
		}
		return second.order(this.simulation, OrderIdUtils.getOrderId("coupletarget"), first)
				|| first.order(this.simulation, OrderIdUtils.getOrderId("coupletarget"), second);
	}

	public boolean convertUnits(final int qty, final int typeIdValue) {
		final War3ID typeId = new War3ID(typeIdValue);
		int converted = 0;
		for (final CUnit unit : this.simulation.getUnits()) {
			if ((converted < qty) && isArmyUnit(unit) && unit.getTypeId().equals(typeId)
					&& unit.order(this.simulation, OrderIdUtils.getOrderId("sphinxform"), null)) {
				converted++;
			}
		}
		return converted > 0;
	}

	public void unsummon(final CUnit building) {
		if ((building == null) || !isOurs(building)) {
			return;
		}
		final int unsummonOrder = OrderIdUtils.getOrderId("unsummon");
		for (final CUnit unit : this.simulation.getUnits()) {
			if (isOurs(unit) && !unit.isHidden() && unit.order(this.simulation, unsummonOrder, building)) {
				return;
			}
		}
	}
}
