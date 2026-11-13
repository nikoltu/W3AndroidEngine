package com.etheller.warsmash.parsers.jass.ai;

import static com.etheller.warsmash.parsers.jass.ai.AIUnits.distance;
import static com.etheller.warsmash.parsers.jass.ai.AIUnits.isIdle;
import static com.etheller.warsmash.parsers.jass.ai.AIZeppelins.isAboard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CGroupMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

/**
 * The attack captain: who it takes, where it gathers, how it is driven to its target through the stage point, when
 * it gives up and retreats.
 */
final class AIAttack {
	private static final float HOME_RADIUS = 900f;
	private static final float GOAL_REACHED_RADIUS = 500f;
	private static final float STAGE_REACHED_RADIUS = 250f;
	private static final int STAGE_WAIT_TICKS = 70;
	private static final float GATHER_RADIUS = 400f;
	private static final float ENGAGE_RADIUS = 340f;
	private static final float CREEP_ENGAGE_RADIUS = 550f;
	private static final float ATTACKER_RADIUS = 1200f;
	private static final int GIVE_UP_TICKS = CTimer.wholeSteps(60);
	private static final War3ID[] SIEGE_UNITS = { War3ID.fromString("ushd"), War3ID.fromString("umtw"),
			War3ID.fromString("hmtm"), War3ID.fromString("hmtt"), War3ID.fromString("ebal"),
			War3ID.fromString("ocat") };

	private AIAttack() {
	}

	static void resetCaptainLocations(final AIController ai) {
		final AITown town = AITowns.getTown(ai, 0);
		final float[] start = ai.player.getStartLocation();
		final CUnit hall = town == null ? null : town.getHall();
		final CUnit mine = town == null ? null : town.getMine();
		final float hallX = hall == null ? start[0] : hall.getX();
		final float hallY = hall == null ? start[1] : hall.getY();
		if (mine == null) {
			ai.attackCaptain.setHome(hallX, hallY);
			ai.defenseCaptain.setHome(hallX, hallY);
			return;
		}
		// Measured on a melee map: the defense home halfway to the mine, the attack home as far on the other side.
		final float offsetX = (mine.getX() - hallX) / 2;
		final float offsetY = (mine.getY() - hallY) / 2;
		ai.attackCaptain.setHome(hallX - offsetX, hallY - offsetY);
		ai.defenseCaptain.setHome(hallX + offsetX, hallY + offsetY);
	}

	private static boolean isInjured(final AIController ai, final CUnit unit) {
		return ai.injured.contains(unit) || (ai.ignoreInjured && (unit.getLife() < (unit.getMaxLife() * 0.4f)));
	}

	static boolean addAssault(final AIController ai, final int qty, final int typeIdValue) {
		final War3ID typeId = new War3ID(typeIdValue);
		final List<CUnit> members = ai.attackCaptain.getMembers();
		int got = 0;
		for (final CUnit unit : members) {
			if (unit.getTypeId().equals(typeId)) {
				got++;
			}
		}
		for (final CUnit unit : ai.attackCaptain.getPreviousMembers()) {
			if (got >= qty) {
				break;
			}
			if (unit.getTypeId().equals(typeId) && ai.isArmyUnit(unit) && !members.contains(unit)
					&& !isInjured(ai, unit)) {
				members.add(unit);
				got++;
			}
		}
		if (got < qty) {
			for (final CUnit unit : ai.simulation.getUnits()) {
				if (got >= qty) {
					break;
				}
				if (unit.getTypeId().equals(typeId) && ai.isArmyUnit(unit) && !unit.isConstructing()
						&& !members.contains(unit) && !isInjured(ai, unit)) {
					AIDefense.leavePost(ai, unit);
					ai.defenseCaptain.getMembers().remove(unit);
					members.add(unit);
					got++;
				}
			}
		}
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (unit.getTypeId().equals(typeId) && ai.isArmyUnit(unit) && !members.contains(unit)
					&& isInjured(ai, unit)) {
				ai.injured.add(unit);
			}
		}
		int resting = 0;
		for (final CUnit unit : ai.injured) {
			if (unit.getTypeId().equals(typeId) && !members.contains(unit)) {
				resting++;
			}
		}
		final boolean satisfied = (got + resting) >= qty;
		ai.attackCaptain.addRequestResult(satisfied);
		return satisfied;
	}

	static void removeInjuries(final AIController ai) {
		for (final Iterator<CUnit> iterator = ai.attackCaptain.getMembers().iterator(); iterator.hasNext();) {
			final CUnit unit = iterator.next();
			if (unit.getLife() < (unit.getMaxLife() * 0.5f)) {
				iterator.remove();
				ai.injured.add(unit);
			}
		}
	}

	static void removeSiege(final AIController ai) {
		for (final Iterator<CUnit> iterator = ai.attackCaptain.getMembers().iterator(); iterator.hasNext();) {
			final CUnit unit = iterator.next();
			for (final War3ID siege : SIEGE_UNITS) {
				if (siege.equals(unit.getTypeId())) {
					iterator.remove();
					break;
				}
			}
		}
	}

	private static boolean isGathered(final AIController ai, final CUnit unit) {
		return distance(unit.getX(), unit.getY(), ai.attackCaptain.getHomeX(),
				ai.attackCaptain.getHomeY()) < (HOME_RADIUS + 300);
	}

	static boolean isCaptainFull(final AIController ai) {
		if (!ai.attackCaptain.isRequestsFull()) {
			return false;
		}
		if (!ai.attackCaptain.hasGoal()) {
			for (final CUnit unit : ai.attackCaptain.getMembers()) {
				if (!isGathered(ai, unit)) {
					return false;
				}
			}
		}
		return true;
	}

	static int getCaptainReadinessHP(final AIController ai) {
		pruneMembers(ai);
		final List<CUnit> members = ai.attackCaptain.getMembers();
		if (members.isEmpty()) {
			return 0;
		}
		float sum = 0;
		for (final CUnit unit : members) {
			sum += unit.getLife() / unit.getMaxLife();
		}
		return (int) ((sum * 100) / members.size());
	}

	static int getCaptainReadinessMa(final AIController ai) {
		pruneMembers(ai);
		float sum = 0;
		int count = 0;
		for (final CUnit unit : ai.attackCaptain.getMembers()) {
			if (unit.getMaximumMana() > 0) {
				sum += unit.getMana() / unit.getMaximumMana();
				count++;
			}
		}
		return count == 0 ? 100 : (int) ((sum * 100) / count);
	}

	static boolean isCaptainAtGoal(final AIController ai) {
		pruneMembers(ai);
		// Measured: a captain whose group has been wiped out reports itself at its goal.
		if (ai.attackCaptain.getMembers().isEmpty()) {
			return true;
		}
		if (!ai.attackCaptain.hasGoal()) {
			return isCaptainHome(ai);
		}
		return distance(ai.attackCaptain.getCenterX(), ai.attackCaptain.getCenterY(), ai.attackCaptain.getGoalX(),
				ai.attackCaptain.getGoalY()) < 600;
	}

	static boolean isCaptainHome(final AIController ai) {
		pruneMembers(ai);
		return distance(ai.attackCaptain.getCenterX(), ai.attackCaptain.getCenterY(), ai.attackCaptain.getHomeX(),
				ai.attackCaptain.getHomeY()) < HOME_RADIUS;
	}

	static boolean isCaptainInCombat(final AIController ai, final boolean attackCaptain) {
		pruneMembers(ai);
		if (!attackCaptain) {
			if (ai.threat != null) {
				return true;
			}
			for (final CUnit unit : ai.simulation.getUnits()) {
				if (ai.isArmyUnit(unit) && !ai.attackCaptain.getMembers().contains(unit)
						&& (unit.getCurrentBehavior() instanceof CBehaviorAttack)) {
					return true;
				}
			}
			return false;
		}
		for (final CUnit unit : ai.attackCaptain.getMembers()) {
			if (unit.getCurrentBehavior() instanceof CBehaviorAttack) {
				return true;
			}
		}
		final List<CUnit> members = ai.attackCaptain.getMembers();
		final int checks = Math.min(members.size(), 8);
		for (int i = 0; i < checks; i++) {
			final CUnit member = members.get((i * members.size()) / checks);
			if (enemyNear(ai, member.getX(), member.getY(), 500f) != null) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Measured: the group marches with the AI move, which walks past enemies. Enemy units beside the way are left
	 * alone (one 230 from the group's centre was taken on, ones 365 and 640 away were not); a creep camp about 500
	 * from the centre was taken on. Once it fights, the members still marching are switched to attack-move.
	 */
	private static boolean isEngaged(final AIController ai) {
		final AICaptain attack = ai.attackCaptain;
		final List<CUnit> members = attack.getMembers();
		if (members.isEmpty()) {
			return false;
		}
		for (final CUnit unit : members) {
			if (unit.getCurrentBehavior() instanceof CBehaviorAttack) {
				return true;
			}
		}
		final float x = attack.getCenterX();
		final float y = attack.getCenterY();
		if (enemyNear(ai, x, y, ENGAGE_RADIUS) != null) {
			return true;
		}
		final boolean[] creep = new boolean[1];
		ai.simulation.getWorldCollision().enumUnitsInRange(x, y, CREEP_ENGAGE_RADIUS, (unit) -> {
			if (ai.isEnemy(unit) && ai.simulation.isNeutralAggressive(unit.getPlayerIndex())
					&& unit.isVisible(ai.simulation, ai.playerIndex)) {
				creep[0] = true;
				return true;
			}
			return false;
		});
		if (creep[0]) {
			return true;
		}
		final boolean[] attacked = new boolean[1];
		ai.simulation.getWorldCollision().enumUnitsInRange(x, y, ATTACKER_RADIUS, (unit) -> {
			if (ai.isEnemy(unit) && (unit.getCurrentBehavior() instanceof CBehaviorAttack)
					&& members.contains(((CBehaviorAttack) unit.getCurrentBehavior()).getTarget())) {
				attacked[0] = true;
				return true;
			}
			return false;
		});
		return attacked[0];
	}

	private static CUnit enemyNear(final AIController ai, final float x, final float y, final float radius) {
		final CUnit[] found = new CUnit[1];
		ai.simulation.getWorldCollision().enumUnitsInRange(x, y, radius, (unit) -> {
			if (ai.isEnemy(unit) && unit.isVisible(ai.simulation, ai.playerIndex)) {
				found[0] = unit;
				return true;
			}
			return false;
		});
		return found[0];
	}

	static void attackMoveKill(final AIController ai, final CUnit target) {
		if ((target == null) || target.isDead()) {
			return;
		}
		if (ai.unreachable.contains(target)) {
			retreat(ai);
			return;
		}
		if (isAcrossWater(ai, target.getX(), target.getY())) {
			captainGoHome(ai);
			return;
		}
		ai.attackCaptain.setGoal(target, target.getX(), target.getY());
		ai.attackCaptain.setRetreating(false);
		driveAttackCaptain(ai);
	}

	static void attackMoveXY(final AIController ai, final float x, final float y) {
		if (isAcrossWater(ai, x, y)) {
			captainGoHome(ai);
			return;
		}
		ai.attackCaptain.setGoal(null, x, y);
		ai.attackCaptain.setRetreating(false);
		driveAttackCaptain(ai);
	}

	static void captainGoHome(final AIController ai) {
		goHome(ai, OrderIds.AImove);
	}

	private static void goHome(final AIController ai, final int orderId) {
		ai.attackCaptain.clearGoal();
		ai.gatherOrdered.addAll(orderGroup(ai, ai.attackCaptain.getMembers(), orderId, ai.attackCaptain.getHomeX(),
				ai.attackCaptain.getHomeY()));
	}

	/**
	 * Measured: the captain's group takes a gather, retreat or home order together, each unit going for its place in
	 * a formation around the point, as a player's group does.
	 */
	private static List<CUnit> orderGroup(final AIController ai, final List<CUnit> members, final int orderId,
			final float x, final float y) {
		final List<CUnit> taken = new ArrayList<>();
		CGroupMove.order(ai.simulation, new ArrayList<>(members), orderId, x, y, (unit, slotX, slotY) -> {
			if (unit.order(ai.simulation, orderId, new AbilityPointTarget(slotX, slotY))) {
				taken.add(unit);
				return true;
			}
			return false;
		});
		return taken;
	}

	static boolean suicidePlayer(final AIController ai, final CPlayer target, final boolean checkFull,
			final boolean buildings) {
		if ((target == null) || (checkFull && !isCaptainFull(ai))) {
			return false;
		}
		final CUnit unit = nearestUnitOf(ai, target.getId(), buildings);
		if (unit == null) {
			return false;
		}
		attackMoveKill(ai, unit);
		return true;
	}

	static void captainVs(final AIController ai, final CPlayer target, final boolean buildings) {
		if (target != null) {
			final CUnit unit = nearestUnitOf(ai, target.getId(), buildings);
			if (unit != null) {
				attackMoveKill(ai, unit);
			}
		}
	}

	private static CUnit nearestUnitOf(final AIController ai, final int ownerIndex, final boolean buildings) {
		final float x = ai.attackCaptain.getCenterX();
		final float y = ai.attackCaptain.getCenterY();
		CUnit best = null;
		float bestScore = Float.MAX_VALUE;
		for (final CUnit unit : ai.simulation.getUnits()) {
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

	static void suicideUnit(final AIController ai, final int count, final int typeIdValue, final int targetPlayer) {
		final War3ID typeId = new War3ID(typeIdValue);
		int sent = 0;
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (sent >= count) {
				break;
			}
			if (ai.isArmyUnit(unit) && unit.getTypeId().equals(typeId)
					&& !ai.attackCaptain.getMembers().contains(unit)) {
				final CUnit target = targetPlayer >= 0 ? nearestUnitOf(ai, targetPlayer, true)
						: AIMapKnowledge.getEnemyBase(ai);
				if (target != null) {
					unit.order(ai.simulation, OrderIds.attack, new AbilityPointTarget(target.getX(), target.getY()));
					sent++;
				}
			}
		}
	}

	static void pruneMembers(final AIController ai) {
		for (final Iterator<CUnit> iterator = ai.attackCaptain.getMembers().iterator(); iterator.hasNext();) {
			final CUnit unit = iterator.next();
			if (!ai.isOurs(unit) || (unit.isHidden() && !isAboard(ai, unit))) {
				iterator.remove();
			}
		}
		ai.injured.removeIf((unit) -> !ai.isOurs(unit) || (unit.getLife() >= (unit.getMaxLife() * 0.85f)));
	}

	static void updateCaptains(final AIController ai) {
		pruneMembers(ai);
		ai.attackCaptain.forgetOrders();
		final AICaptain attack = ai.attackCaptain;
		if ((attack.getGoalUnit() != null) && (attack.getGoalUnit().isDead() || attack.getGoalUnit().isHidden())) {
			attack.clearGoal();
		}
		final AbilityPointTarget home = new AbilityPointTarget(attack.getHomeX(), attack.getHomeY());
		for (final Iterator<CUnit> iterator = attack.getMembers().iterator(); iterator.hasNext();) {
			final CUnit unit = iterator.next();
			final boolean hero = unit.getHeroData() != null;
			final float lifeRatio = unit.getLife() / unit.getMaxLife();
			if ((hero && ai.heroesFlee && (lifeRatio < 0.25f)) || (!hero && ai.unitsFlee && (lifeRatio < 0.15f))) {
				iterator.remove();
				ai.injured.add(unit);
				unit.order(ai.simulation, OrderIds.move, home);
			}
		}
		if (ai.groupsFlee && attack.hasGoal() && !attack.getMembers().isEmpty() && (getCaptainReadinessHP(ai) < 30)
				&& isCaptainInCombat(ai, true)) {
			retreat(ai);
		}
		if (attack.isRetreating() && isCaptainHome(ai) && ((ai.ticks - ai.retreatTick) > 100)) {
			attack.setRetreating(false);
		}
		if (attack.hasGoal() && (attack.getGoalUnit() == null) && !attack.getMembers().isEmpty()
				&& (distance(attack.getCenterX(), attack.getCenterY(), attack.getGoalX(),
						attack.getGoalY()) < GOAL_REACHED_RADIUS)
				&& !isCaptainInCombat(ai, true)) {
			// Measured: a group that has reached a point goal walks home with the plain move.
			goHome(ai, OrderIds.move);
		}
		// Measured: a group that took on enemies on its way and has finished them does not go on to its goal; it
		// retreats home at once.
		if (attack.hasGoal() && !attack.getMembers().isEmpty() && (ai.fightGeneration == attack.getGeneration())
				&& !isEngaged(ai) && !isCaptainInCombat(ai, true)) {
			retreat(ai);
		}
		if (attack.hasGoal()) {
			checkStuck(ai);
		}
		if (attack.hasGoal()) {
			driveAttackCaptain(ai);
		}
		else if (!attack.isRetreating()) {
			ai.gatherOrdered.retainAll(attack.getMembers());
			final List<CUnit> gathering = new ArrayList<>();
			for (final CUnit unit : attack.getMembers()) {
				if (unit.getCurrentBehavior() instanceof CBehaviorAttack) {
					continue;
				}
				if (!ai.gatherOrdered.contains(unit) || (isIdle(unit)
						&& (distance(unit.getX(), unit.getY(), home.x, home.y) >= GATHER_RADIUS))) {
					gathering.add(unit);
				}
			}
			ai.gatherOrdered.addAll(orderGroup(ai, gathering, OrderIds.AImove, home.x, home.y));
		}
		if (attack.hasGoal()) {
			ai.gatherOrdered.clear();
			if (ai.targetHeroes) {
				targetHeroes(ai);
			}
		}
		for (final CUnit unit : ai.injured) {
			if (isIdle(unit) && (distance(unit.getX(), unit.getY(), home.x, home.y) > HOME_RADIUS)) {
				unit.order(ai.simulation, OrderIds.move, home);
			}
		}
		AIDefense.updateDefense(ai);
	}

	/**
	 * Units that cannot get any closer to their target keep trying to path to it; they are sent to attack-move
	 * instead, and a target the whole group cannot reach is given up so the scripts move on.
	 */
	private static void checkStuck(final AIController ai) {
		final AICaptain attack = ai.attackCaptain;
		ai.lastPositions.keySet().retainAll(attack.getMembers());
		final AbilityPointTarget goal = new AbilityPointTarget(attack.getGoalX(), attack.getGoalY());
		for (final CUnit unit : attack.getMembers()) {
			if (isAboard(ai, unit)) {
				ai.goalProgressTick = ai.ticks;
				continue;
			}
			float[] last = ai.lastPositions.get(unit);
			if (last == null) {
				last = new float[] { unit.getX(), unit.getY(), 0 };
				ai.lastPositions.put(unit, last);
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
				unit.order(ai.simulation, OrderIds.stop, null);
				unit.order(ai.simulation, OrderIds.attack, goal);
				attack.markOrdered(unit);
			}
		}
		final float goalDistance = distance(attack.getCenterX(), attack.getCenterY(), goal.x, goal.y);
		if ((ai.goalProgressGeneration != attack.getGeneration()) || (goalDistance < (ai.bestGoalDistance - 150))) {
			ai.goalProgressGeneration = attack.getGeneration();
			ai.bestGoalDistance = goalDistance;
			ai.goalProgressTick = ai.ticks;
		}
		else if (isCaptainInCombat(ai, true)) {
			ai.goalProgressTick = ai.ticks;
		}
		else if ((ai.ticks - ai.goalProgressTick) > GIVE_UP_TICKS) {
			if ((attack.getGoalUnit() != null)
					&& ai.simulation.isNeutralAggressive(attack.getGoalUnit().getPlayerIndex())) {
				ai.unreachable.add(attack.getGoalUnit());
			}
			ai.lastPositions.clear();
			retreat(ai);
		}
	}

	private static void retreat(final AIController ai) {
		final AICaptain attack = ai.attackCaptain;
		attack.clearGoal();
		attack.setRetreating(true);
		ai.retreatTick = ai.ticks;
		orderGroup(ai, attack.getMembers(), OrderIds.move, attack.getHomeX(), attack.getHomeY());
	}

	private static void driveAttackCaptain(final AIController ai) {
		final AICaptain attack = ai.attackCaptain;
		final boolean viaStage = updateStagePhase(ai);
		final CUnit goalUnit = viaStage ? null : attack.getGoalUnit();
		final AbilityPointTarget goal = viaStage ? new AbilityPointTarget(ai.stageX, ai.stageY)
				: new AbilityPointTarget(attack.getGoalX(), attack.getGoalY());
		ai.orderedTargets.keySet().retainAll(attack.getMembers());
		final boolean footReachable = ai.simulation.getPathingGrid().isPathable(goal.x, goal.y,
				MovementType.FOOT);
		final AbilityPointTarget home = new AbilityPointTarget(attack.getHomeX(), attack.getHomeY());
		final boolean engaged = isEngaged(ai);
		if (engaged) {
			ai.fightGeneration = attack.getGeneration();
		}
		final List<CUnit> toGoal = new ArrayList<>();
		final List<CUnit> toHome = new ArrayList<>();
		for (final CUnit unit : attack.getMembers()) {
			if (isAboard(ai, unit)) {
				continue;
			}
			// Sleeping creeps are not picked up by attack-move, so the target itself is attacked once close.
			if ((goalUnit != null) && (distance(unit.getX(), unit.getY(), goalUnit.getX(), goalUnit.getY()) < 900)) {
				if ((unit.getCurrentBehavior() instanceof CBehaviorAttack)
						|| ((ai.orderedTargets.get(unit) == goalUnit) && !isIdle(unit) && !isStuck(ai, unit))) {
					continue;
				}
				if (unit.order(ai.simulation, OrderIds.attack, goalUnit)) {
					ai.orderedTargets.put(unit, goalUnit);
					attack.markOrdered(unit);
					continue;
				}
			}
			final boolean amphibiousHome = !ai.amphibious && !footReachable && isAmphibious(unit);
			if (!attack.needsOrder(unit) && !isIdle(unit)
					&& !(engaged && !amphibiousHome && AIUnits.isAIMoving(unit))) {
				continue;
			}
			ai.orderedTargets.remove(unit);
			(amphibiousHome ? toHome : toGoal).add(unit);
		}
		for (final CUnit unit : orderGroup(ai, toHome, OrderIds.AImove, home.x, home.y)) {
			attack.markOrdered(unit);
		}
		final int goalOrder = engaged ? OrderIds.attack : OrderIds.AImove;
		final List<CUnit> taken = orderGroup(ai, toGoal, goalOrder, goal.x, goal.y);
		for (final CUnit unit : taken) {
			attack.markOrdered(unit);
		}
		if (goalOrder != OrderIds.AImove) {
			toGoal.removeAll(taken);
			for (final CUnit unit : orderGroup(ai, toGoal, OrderIds.AImove, goal.x, goal.y)) {
				attack.markOrdered(unit);
			}
		}
	}

	/**
	 * Measured: members still closing in on their target switch to an enemy hero within their acquisition range;
	 * those already striking finish their target first.
	 */
	private static void targetHeroes(final AIController ai) {
		for (final CUnit unit : ai.attackCaptain.getMembers()) {
			if (isAboard(ai, unit) || unit.getUnitType().getAttacks().isEmpty()) {
				continue;
			}
			final CBehavior behavior = unit.getCurrentBehavior();
			final CUnit current = behavior instanceof CBehaviorAttack
					? ((CBehaviorAttack) behavior).getTarget().visit(AbilityTargetVisitor.UNIT)
					: null;
			if ((current == null) || (current.getHeroData() != null)
					|| ((CBehaviorAttack) behavior).isWithinRange(ai.simulation)) {
				continue;
			}
			final CUnit[] hero = new CUnit[1];
			final float[] best = { Float.MAX_VALUE };
			ai.simulation.getWorldCollision().enumUnitsInRange(unit.getX(), unit.getY(),
					unit.getAcquisitionRange(), (other) -> {
						if ((other.getHeroData() != null) && ai.isEnemy(other)
								&& other.isVisible(ai.simulation, ai.playerIndex)) {
							final float d = distance(unit.getX(), unit.getY(), other.getX(), other.getY());
							if (d < best[0]) {
								best[0] = d;
								hero[0] = other;
							}
						}
						return false;
					});
			if ((hero[0] != null) && unit.order(ai.simulation, OrderIds.attack, hero[0])) {
				ai.attackCaptain.markOrdered(unit);
			}
		}
	}

	private static boolean updateStagePhase(final AIController ai) {
		final AICaptain attack = ai.attackCaptain;
		if (!ai.stagePointSet || (attack.getGoalUnit() == null)
				|| (ai.stagePassedGeneration == attack.getGeneration())) {
			return false;
		}
		if (ai.stageArrivedGeneration == attack.getGeneration()) {
			if ((ai.ticks - ai.stageArrivedTick) < STAGE_WAIT_TICKS) {
				return true;
			}
			ai.stagePassedGeneration = attack.getGeneration();
			attack.clearOrders();
			return false;
		}
		for (final CUnit unit : attack.getMembers()) {
			if (distance(unit.getX(), unit.getY(), ai.stageX, ai.stageY) < STAGE_REACHED_RADIUS) {
				ai.stageArrivedGeneration = attack.getGeneration();
				ai.stageArrivedTick = ai.ticks;
				break;
			}
		}
		return true;
	}

	/**
	 * Measured: without SetAmphibious a group with amphibious units plans over land only, water as shallow as a ford
	 * included; a target on land it cannot reach that way sends the whole group home at once.
	 */
	private static boolean isAcrossWater(final AIController ai, final float x, final float y) {
		if (ai.amphibious) {
			return false;
		}
		pruneMembers(ai);
		boolean amphibious = false;
		for (final CUnit unit : ai.attackCaptain.getMembers()) {
			amphibious |= isAmphibious(unit);
		}
		if (!amphibious) {
			return false;
		}
		final AILand land = AILand.of(ai);
		final int goalMass = land.massNear(x, y, 64f);
		if (goalMass == -1) {
			return false;
		}
		final int groupMass = land.massNear(ai.attackCaptain.getCenterX(), ai.attackCaptain.getCenterY(), 400f);
		return (groupMass != -1) && (groupMass != goalMass);
	}

	private static boolean isAmphibious(final CUnit unit) {
		return unit.getUnitType().getMovementType() == MovementType.AMPHIBIOUS;
	}

	private static boolean isStuck(final AIController ai, final CUnit unit) {
		final float[] last = ai.lastPositions.get(unit);
		return (last != null) && (last[2] >= 6);
	}
}
