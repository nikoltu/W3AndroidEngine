package com.etheller.warsmash.parsers.jass.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

/**
 * The engine side of a computer player, what Warcraft III keeps behind the common.ai natives: towns and their
 * gold mines, the harvest assignment, production and building placement, hero skills, the attack and defense
 * captains and the map knowledge (creep camps, expansions, enemy bases) the race scripts ask for.
 * <p>
 * All of the player's AI state is kept here, where saved games find it; the work is done by the stateless helpers
 * of this package.
 */
public class AIController {
	public static final int ATTACK_CAPTAIN = 1;
	public static final int DEFENSE_CAPTAIN = 2;
	private static final int MEGA_TARGET_INTERVAL = 200;

	final CSimulation simulation;
	final GlobalScope globalScope;
	final int playerIndex;
	final CPlayer player;

	final List<AITown> towns = new ArrayList<>();
	AITown pendingExpansion;
	final List<AIBuildJob> buildJobs = new ArrayList<>();
	final List<int[]> harvestRequests = new ArrayList<>();
	final Map<CUnit, int[]> harvestJobs = new LinkedHashMap<>();
	boolean harvestPassPending;
	int harvestCallTick;
	final Set<CUnit> harvestUnsent = new LinkedHashSet<>();
	final Map<Integer, List<CDestructable>> townTrees = new HashMap<>();
	final Map<Integer, Integer> townTreesTick = new HashMap<>();
	final AICaptain attackCaptain = new AICaptain();
	final AICaptain defenseCaptain = new AICaptain();
	final Set<CUnit> injured = new LinkedHashSet<>();
	final Map<CUnit, float[]> lastPositions = new LinkedHashMap<>();
	final List<AIGuardPost> guardPosts = new ArrayList<>();
	final List<AIReplacement> replacements = new ArrayList<>();
	final Map<CUnit, float[]> guardPositions = new LinkedHashMap<>();
	boolean unitsSeen;
	boolean gatheringStopped;
	final List<AIZepWave> zepWaves = new ArrayList<>();
	boolean stagePointSet;
	float stageX;
	float stageY;
	int stagePassedGeneration = -1;
	int stageArrivedGeneration = -1;
	int stageArrivedTick;
	final Set<CUnit> unreachable = new LinkedHashSet<>();
	final Map<CUnit, CUnit> orderedTargets = new LinkedHashMap<>();
	final Set<CUnit> gatherOrdered = new LinkedHashSet<>();
	private final Deque<int[]> commands = new ArrayDeque<>();
	final Map<War3ID, int[]> countCache = new HashMap<>();
	int countCacheTick = -1;
	private CUnit allianceTarget;
	CUnit enemyBase;
	CodeJassValue heroLevelsFunction;
	CUnit levelingHero;
	int levelingHeroLevel;
	CUnit threat;
	int retreatTick;
	int goalProgressGeneration = -1;
	float bestGoalDistance;
	int goalProgressTick;
	int ticks;

	private boolean campaign;
	boolean targetHeroes;
	boolean peonsRepair;
	boolean heroesFlee;
	boolean unitsFlee;
	boolean groupsFlee;
	boolean ignoreInjured;
	boolean heroesTakeItems;
	boolean newHeroes = true;
	boolean groupTimedLife = true;
	private boolean watchMegaTargets;
	private boolean slowChopping;
	private CUnit megaTarget;
	final Set<CUnit> ignoredUnits = new LinkedHashSet<>();
	final Set<CUnit> ignoredHeroes = new LinkedHashSet<>();
	int replacementCount = 3;
	boolean amphibious;
	AILand land;

	static final class AIGuardPost {
		final War3ID typeId;
		final float x;
		final float y;
		CUnit unit;

		private AIGuardPost(final War3ID typeId, final float x, final float y) {
			this.typeId = typeId;
			this.x = x;
			this.y = y;
		}
	}

	/**
	 * A unit the player had when the AI started, trained anew and sent back to its place when it dies.
	 */
	static final class AIReplacement {
		final War3ID typeId;
		final float x;
		final float y;
		CUnit unit;
		int used;
		boolean pending;

		AIReplacement(final CUnit unit) {
			this.typeId = unit.getTypeId();
			this.x = unit.getX();
			this.y = unit.getY();
			this.unit = unit;
		}
	}

	static final class AIZepWave {
		final CUnit zeppelin;
		final List<CUnit> cargo = new ArrayList<>();
		final float x;
		final float y;
		boolean flying;

		AIZepWave(final CUnit zeppelin, final float x, final float y) {
			this.zeppelin = zeppelin;
			this.x = x;
			this.y = y;
		}
	}

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
			AITowns.initTowns(this);
		}
		if ((this.ticks % 10) == 0) {
			AIConstruction.updateBuildJobs(this);
			AIProduction.updateHeroSkills(this);
			AIDefense.placeNewUnits(this);
			AIZeppelins.updateZepWaves(this);
			AIAttack.updateCaptains(this);
			if (this.heroesTakeItems) {
				AIDefense.takeItems(this);
			}
		}
		if (this.watchMegaTargets && ((this.ticks % MEGA_TARGET_INTERVAL) == (MEGA_TARGET_INTERVAL - 10))) {
			this.megaTarget = AIMapKnowledge.getMegaTarget(this);
		}
		if ((this.ticks % 20) == 5) {
			AITowns.refreshTowns(this);
			AIHarvest.assignHarvesters(this);
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

	public void setSlowChopping(final boolean slowChopping) {
		this.slowChopping = slowChopping;
	}

	/**
	 * Measured: with slow chopping each load a worker brings home is a tenth of its size, at least one, for gold as
	 * well as lumber; the trips take as long as ever.
	 */
	public int scaleHarvestedAmount(final int amount) {
		return this.slowChopping ? Math.max(1, amount / 10) : amount;
	}

	public void setWatchMegaTargets(final boolean watchMegaTargets) {
		this.watchMegaTargets = watchMegaTargets;
		if (!watchMegaTargets) {
			this.megaTarget = null;
		}
	}

	public void setGroupTimedLife(final boolean groupTimedLife) {
		this.groupTimedLife = groupTimedLife;
	}

	/**
	 * Measured: the summoned units the AI has left out of its groups and the heroes the player had when the AI
	 * started, per unit type.
	 */
	public int getIgnoredUnits(final int typeIdValue) {
		final War3ID typeId = new War3ID(typeIdValue);
		int count = 0;
		for (final CUnit unit : this.ignoredUnits) {
			if (isOurs(unit) && unit.getTypeId().equals(typeId)) {
				count++;
			}
		}
		for (final CUnit unit : this.ignoredHeroes) {
			if (isOurs(unit) && unit.getTypeId().equals(typeId)) {
				count++;
			}
		}
		return count;
	}

	public void setNewHeroes(final boolean newHeroes) {
		this.newHeroes = newHeroes;
	}

	public void setReplacementCount(final int replacementCount) {
		this.replacementCount = replacementCount;
	}

	/**
	 * Measured: without it the attack captain plans for its amphibious units as if they walked, so they are not sent
	 * into water.
	 */
	public void setAmphibious() {
		this.amphibious = true;
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

	boolean isOurs(final CUnit unit) {
		return (unit.getPlayerIndex() == this.playerIndex) && !unit.isDead();
	}

	boolean isBuildJobWorker(final CUnit unit) {
		for (final AIBuildJob job : this.buildJobs) {
			if (job.getWorker() == unit) {
				return true;
			}
		}
		return false;
	}

	boolean isGuardCandidate(final CUnit unit) {
		return isOurs(unit) && !unit.isBuilding() && !unit.isHidden() && !AIUnits.isWorkerType(unit)
				&& (unit.getUnitType().getMovementType() != PathingGrid.MovementType.DISABLED);
	}

	/**
	 * Workers and the units harvesting for us stay out of the captains.
	 */
	boolean isArmyUnit(final CUnit unit) {
		return isGuardCandidate(unit) && !this.harvestJobs.containsKey(unit) && !isBuildJobWorker(unit)
				&& !this.ignoredUnits.contains(unit);
	}

	boolean isEnemyPlayer(final int otherIndex) {
		if ((otherIndex == this.playerIndex) || this.player.hasAlliance(otherIndex, CAllianceType.PASSIVE)) {
			return false;
		}
		return (otherIndex < (this.simulation.getGameData().getMaxPlayers() - 4)) || this.simulation.isNeutralAggressive(otherIndex);
	}

	boolean isEnemy(final CUnit unit) {
		return !unit.isDead() && !unit.isHidden() && isEnemyPlayer(unit.getPlayerIndex());
	}

	boolean isOpponent(final int otherIndex) {
		if ((otherIndex >= (this.simulation.getGameData().getMaxPlayers() - 4)) || !isEnemyPlayer(otherIndex)) {
			return false;
		}
		final CPlayer other = this.simulation.getPlayer(otherIndex);
		return (other.getSlotState() == CPlayerSlotState.PLAYING)
				&& ((other.getController() == CMapControl.USER) || (other.getController() == CMapControl.COMPUTER));
	}

	public int getUnitCount(final int typeId, final boolean done) {
		return AIProduction.getUnitCount(this, typeId, done);
	}

	public int getTownUnitCount(final int typeId, final int town, final boolean done) {
		return AIProduction.countUnits(this, this.playerIndex, new War3ID(typeId), done, town);
	}

	/**
	 * Counts units of a type like the AI sees them: a structure being upgraded counts as its new type until done,
	 * queued units and heroes being revived count as not done, and so do the structures our workers are on
	 * their way to build.
	 */
	public int countUnits(final int ownerIndex, final War3ID id, final boolean done, final int town) {
		return AIProduction.countUnits(this, ownerIndex, id, done, town);
	}

	public AITown getTown(final int id) {
		return AITowns.getTown(this, id);
	}

	/**
	 * @return the id of the town nearest to the point within the town radius, or -1
	 */
	public int townOf(final float x, final float y) {
		return AITowns.townOf(this, x, y);
	}

	public boolean townHasMine(final int id) {
		return AITowns.townHasMine(this, id);
	}

	public boolean townHasHall(final int id) {
		return AITowns.townHasHall(this, id);
	}

	public int getMinesOwned() {
		return AITowns.getMinesOwned(this);
	}

	public int getGoldOwned() {
		return AITowns.getGoldOwned(this);
	}

	public int townWithMine() {
		return AITowns.townWithMine(this);
	}

	public int getNextExpansion() {
		return AITowns.getNextExpansion(this);
	}

	public CUnit getExpansionFoe() {
		return AITowns.getExpansionFoe(this);
	}

	public int getExpansionX() {
		return this.pendingExpansion == null ? -1 : (int) this.pendingExpansion.getX();
	}

	public int getExpansionY() {
		return this.pendingExpansion == null ? -1 : (int) this.pendingExpansion.getY();
	}

	public CUnit getExpansionPeon() {
		return AITowns.getExpansionPeon(this);
	}

	public boolean setExpansion(final CUnit peon, final int typeId) {
		return AITowns.setExpansion(this, peon, typeId);
	}

	public boolean setProduce(final int qty, final int typeIdValue, final int town) {
		return AIProduction.setProduce(this, qty, typeIdValue, town);
	}

	public int getUpgradeLevel(final int typeId) {
		return AIProduction.getUpgradeLevel(this, typeId);
	}

	public int getUpgradeGoldCost(final int typeId) {
		return AIProduction.getUpgradeGoldCost(this, typeId);
	}

	public int getUpgradeWoodCost(final int typeId) {
		return AIProduction.getUpgradeWoodCost(this, typeId);
	}

	public boolean setUpgrade(final int typeId) {
		return AIProduction.setUpgrade(this, typeId);
	}

	public void clearHarvestAI() {
		this.harvestRequests.clear();
		this.harvestPassPending = true;
		this.harvestCallTick = this.ticks;
	}

	public void harvestGold(final int town, final int peons) {
		AIHarvest.harvest(this, AIHarvest.GOLD, town, peons);
	}

	public void harvestWood(final int town, final int peons) {
		AIHarvest.harvest(this, AIHarvest.WOOD, town, peons);
	}

	public void stopGathering() {
		this.harvestRequests.clear();
		this.harvestPassPending = true;
		this.harvestCallTick = this.ticks;
		this.gatheringStopped = true;
	}

	public void addGuardPost(final int typeIdValue, final float x, final float y) {
		this.guardPosts.add(new AIGuardPost(new War3ID(typeIdValue), x, y));
	}

	/**
	 * Measured: moves nobody who already has a place, neither units from the start nor spare defenders, also when the
	 * post was added or vacated later. New units take their post without it.
	 */
	public void fillGuardPosts() {
		AIDefense.placeNewUnits(this);
	}

	public void returnGuardPosts() {
		AIDefense.returnGuardPosts(this);
	}

	public int getLevelingHeroId() {
		return this.levelingHero == null ? 0 : this.levelingHero.getTypeId().getValue();
	}

	public int getLevelingHeroLevel() {
		return this.levelingHeroLevel;
	}

	public void createCaptains() {
		if (this.towns.isEmpty()) {
			AITowns.initTowns(this);
		}
		resetCaptainLocations();
	}

	public void resetCaptainLocations() {
		AIAttack.resetCaptainLocations(this);
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

	public boolean addAssault(final int qty, final int typeIdValue) {
		return AIAttack.addAssault(this, qty, typeIdValue);
	}

	public boolean addDefenders(final int qty, final int typeIdValue) {
		return AIDefense.addDefenders(this, qty, typeIdValue);
	}

	public void removeInjuries() {
		AIAttack.removeInjuries(this);
	}

	public void removeSiege() {
		AIAttack.removeSiege(this);
	}

	public boolean isCaptainFull() {
		return AIAttack.isCaptainFull(this);
	}

	public boolean isCaptainEmpty() {
		AIAttack.pruneMembers(this);
		return this.attackCaptain.getMembers().isEmpty();
	}

	public int getCaptainGroupSize() {
		AIAttack.pruneMembers(this);
		return this.attackCaptain.getMembers().size();
	}

	public int getCaptainReadinessHP() {
		return AIAttack.getCaptainReadinessHP(this);
	}

	public int getCaptainReadinessMa() {
		return AIAttack.getCaptainReadinessMa(this);
	}

	public int getCaptainReadiness() {
		return getCaptainReadinessHP();
	}

	public boolean isCaptainRetreating() {
		return this.attackCaptain.isRetreating();
	}

	public boolean isCaptainAtGoal() {
		return AIAttack.isCaptainAtGoal(this);
	}

	public boolean isCaptainHome() {
		return AIAttack.isCaptainHome(this);
	}

	public boolean isCaptainInCombat(final boolean attackCaptain) {
		return AIAttack.isCaptainInCombat(this, attackCaptain);
	}

	public void attackMoveKill(final CUnit target) {
		AIAttack.attackMoveKill(this, target);
	}

	public void attackMoveXY(final float x, final float y) {
		AIAttack.attackMoveXY(this, x, y);
	}

	public void captainGoHome() {
		AIAttack.captainGoHome(this);
	}

	/**
	 * Measured: only a target unit is approached through the stage point (SuicidePlayer, AttackMoveKill); AttackMoveXY
	 * goes straight, on foot and in the air. The group moves on a few seconds after its first units get there, stragglers do not wait.
	 */
	public void setStagePoint(final float x, final float y) {
		this.stagePointSet = true;
		this.stageX = x;
		this.stageY = y;
	}

	public void loadZepWave(final int x, final int y) {
		AIZeppelins.loadZepWave(this, x, y);
	}

	public void clearCaptainTargets() {
		this.attackCaptain.clearGoal();
	}

	public boolean suicidePlayer(final CPlayer target, final boolean checkFull, final boolean buildings) {
		return AIAttack.suicidePlayer(this, target, checkFull, buildings);
	}

	public void captainVs(final CPlayer target, final boolean buildings) {
		AIAttack.captainVs(this, target, buildings);
	}

	public void suicideUnit(final int count, final int typeIdValue, final int targetPlayer) {
		AIAttack.suicideUnit(this, count, typeIdValue, targetPlayer);
	}

	public boolean isTownThreatened() {
		return this.threat != null;
	}

	public CUnit getCreepCamp(final int minLevel, final int maxLevel, final boolean flyersOk) {
		return AIMapKnowledge.getCreepCamp(this, minLevel, maxLevel, flyersOk);
	}

	public boolean creepsOnMap() {
		return AIMapKnowledge.creepsOnMap(this);
	}

	public void startGetEnemyBase() {
		this.enemyBase = AIMapKnowledge.findEnemyBase(this, -1);
	}

	public CUnit getEnemyBase() {
		return AIMapKnowledge.getEnemyBase(this);
	}

	public CUnit getEnemyExpansion() {
		return AIMapKnowledge.getEnemyExpansion(this);
	}

	public int getEnemyPower() {
		return AIMapKnowledge.getEnemyPower(this);
	}

	/**
	 * Measured: only a script that watches mega targets gets one, and it is judged every ten seconds, the first time
	 * about ten seconds after the script starts.
	 */
	public CUnit getMegaTarget() {
		if ((this.megaTarget != null) && this.megaTarget.isDead()) {
			this.megaTarget = null;
		}
		return this.megaTarget;
	}

	public boolean isTowered(final CUnit target) {
		return AIMapKnowledge.isTowered(this, target);
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
		return AIProduction.mergeUnits(this, qty, typeA, typeB, result);
	}

	public boolean convertUnits(final int qty, final int typeIdValue) {
		return AIProduction.convertUnits(this, qty, typeIdValue);
	}

	public void unsummon(final CUnit building) {
		AIProduction.unsummon(this, building);
	}
}
