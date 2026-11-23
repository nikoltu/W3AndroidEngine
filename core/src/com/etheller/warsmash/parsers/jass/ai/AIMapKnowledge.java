package com.etheller.warsmash.parsers.jass.ai;

import static com.etheller.warsmash.parsers.jass.ai.AIUnits.distance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;

/**
 * What the computer player knows of the map beyond its own towns: creep camps, the opponents' bases and expansions,
 * and how strong the opponents' armies are.
 */
final class AIMapKnowledge {
	private static final float CAMP_LINK_DISTANCE = 600f;

	private AIMapKnowledge() {
	}

	/** A group of creeps whose camp spots lie within linking distance of each other, as of one tick. */
	static final class Camp {
		final List<CUnit> members = new ArrayList<>();
		int level;
		boolean flyers;
		CUnit strongest;
		float x;
		float y;
	}

	private static long cellKey(final int cx, final int cy) {
		return (((long) cx) << 32) ^ (cy & 0xFFFFFFFFL);
	}

	private static int cellOf(final float v) {
		return (int) Math.floor(v / CAMP_LINK_DISTANCE);
	}

	/**
	 * Measured: a creep belongs to the camp it was placed in wherever it stands; ones drawn away from their camp by a
	 * fight, or moved by a script, do not become camps of their own. Creeps are therefore linked by their camp spots.
	 */
	private static float spotX(final CUnit creep) {
		return creep.isGuarding() ? creep.getGuardX() : creep.getX();
	}

	private static float spotY(final CUnit creep) {
		return creep.isGuarding() ? creep.getGuardY() : creep.getY();
	}

	/** The camps do not change within a tick, so the grouping is made once per tick and reused. */
	private static List<Camp> campsOf(final AIController ai) {
		final int tick = ai.simulation.getGameTurnTick();
		if ((ai.campCache != null) && (ai.campCacheTick == tick)) {
			return ai.campCache;
		}
		final List<CUnit> creeps = new ArrayList<>();
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (!unit.isDead() && !unit.isHidden() && ai.simulation.isNeutralAggressive(unit.getPlayerIndex())
					&& !unit.isBuilding()) {
				creeps.add(unit);
			}
		}
		// Creeps are bucketed by cell so that linking only looks at the neighbouring cells; candidates are still
		// visited in list order, so the groups come out exactly as an all-pairs scan would make them.
		final Map<Long, List<Integer>> cells = new HashMap<>();
		for (int i = 0; i < creeps.size(); i++) {
			final CUnit creep = creeps.get(i);
			cells.computeIfAbsent(cellKey(cellOf(spotX(creep)), cellOf(spotY(creep))), (key) -> new ArrayList<>())
					.add(i);
		}
		final boolean[] visited = new boolean[creeps.size()];
		final List<Camp> camps = new ArrayList<>();
		final List<Integer> candidates = new ArrayList<>();
		for (int seed = 0; seed < creeps.size(); seed++) {
			if (visited[seed]) {
				continue;
			}
			final Camp result = new Camp();
			final List<CUnit> camp = result.members;
			camp.add(creeps.get(seed));
			visited[seed] = true;
			for (int i = 0; i < camp.size(); i++) {
				final CUnit member = camp.get(i);
				final int cx = cellOf(spotX(member));
				final int cy = cellOf(spotY(member));
				candidates.clear();
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						final List<Integer> cell = cells.get(cellKey(cx + dx, cy + dy));
						if (cell != null) {
							candidates.addAll(cell);
						}
					}
				}
				Collections.sort(candidates);
				for (final int index : candidates) {
					final CUnit other = creeps.get(index);
					if (!visited[index] && (distance(spotX(member), spotY(member), spotX(other),
							spotY(other)) < CAMP_LINK_DISTANCE)) {
						visited[index] = true;
						camp.add(other);
					}
				}
			}
			float x = 0;
			float y = 0;
			for (final CUnit member : camp) {
				result.level += member.getUnitType().getLevel();
				result.flyers |= member.getUnitType().getMovementType() == PathingGrid.MovementType.FLY;
				if ((result.strongest == null)
						|| (member.getUnitType().getLevel() > result.strongest.getUnitType().getLevel())) {
					result.strongest = member;
				}
				x += spotX(member);
				y += spotY(member);
			}
			result.x = x / camp.size();
			result.y = y / camp.size();
			camps.add(result);
		}
		ai.campCache = camps;
		ai.campCacheTick = tick;
		return camps;
	}

	static CUnit getCreepCamp(final AIController ai, final int minLevel, final int maxLevel, final boolean flyersOk) {
		final float homeX = ai.attackCaptain.getHomeX();
		final float homeY = ai.attackCaptain.getHomeY();
		List<CUnit> enemyBuildings = null;
		CUnit best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final Camp camp : campsOf(ai)) {
			if ((camp.level < minLevel) || (camp.level > maxLevel) || (camp.flyers && !flyersOk)) {
				continue;
			}
			boolean reachable = true;
			for (final CUnit member : camp.members) {
				reachable &= !ai.unreachable.contains(member);
			}
			if (!reachable) {
				continue;
			}
			if (enemyBuildings == null) {
				enemyBuildings = enemyBuildings(ai);
			}
			if (nearEnemyBase(enemyBuildings, camp.x, camp.y)) {
				continue;
			}
			final float d = distance(homeX, homeY, camp.x, camp.y);
			if (d < bestDistance) {
				bestDistance = d;
				best = camp.strongest;
			}
		}
		return best;
	}

	private static List<CUnit> enemyBuildings(final AIController ai) {
		final List<CUnit> buildings = new ArrayList<>();
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (!unit.isDead() && unit.isBuilding() && ai.isOpponent(unit.getPlayerIndex())) {
				buildings.add(unit);
			}
		}
		return buildings;
	}

	private static boolean nearEnemyBase(final List<CUnit> buildings, final float x, final float y) {
		for (final CUnit unit : buildings) {
			if (distance(x, y, unit.getX(), unit.getY()) < 1500) {
				return true;
			}
		}
		return false;
	}

	static boolean creepsOnMap(final AIController ai) {
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (!unit.isDead() && ai.simulation.isNeutralAggressive(unit.getPlayerIndex())) {
				return true;
			}
		}
		return false;
	}

	static CUnit getEnemyBase(final AIController ai) {
		// Measured: asked in the run that started the search, with no wait between, there is no answer yet.
		if (ai.enemyBaseSearchTick == ai.simulation.getGameTurnTick()) {
			return null;
		}
		if ((ai.enemyBase == null) || ai.isGone(ai.enemyBase)) {
			ai.enemyBase = findEnemyBase(ai, -1);
		}
		return ai.enemyBase;
	}

	/**
	 * Measured: the opponent's building nearest to our town, a town hall no better than any other; with none left,
	 * one of its units.
	 *
	 * @param onlyPlayer -1 for the nearest opponent
	 */
	static CUnit findEnemyBase(final AIController ai, final int onlyPlayer) {
		return findEnemyBase(ai, onlyPlayer, true);
	}

	private static CUnit findEnemyBase(final AIController ai, final int onlyPlayer, final boolean unitsAllowed) {
		final AITown home = AITowns.getTown(ai, 0);
		final float[] start = ai.player.getStartLocation();
		final float hx = home == null ? start[0] : home.getX();
		final float hy = home == null ? start[1] : home.getY();
		CUnit best = null;
		float bestDistance = Float.MAX_VALUE;
		CUnit bestUnit = null;
		float bestUnitDistance = Float.MAX_VALUE;
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (unit.isDead() || !ai.isOpponent(unit.getPlayerIndex())
					|| ((onlyPlayer != -1) && (unit.getPlayerIndex() != onlyPlayer))) {
				continue;
			}
			final float d = distance(hx, hy, unit.getX(), unit.getY());
			if (unit.isBuilding()) {
				if (d < bestDistance) {
					bestDistance = d;
					best = unit;
				}
			}
			else if (unitsAllowed && !unit.isHidden() && (d < bestUnitDistance)) {
				bestUnitDistance = d;
				bestUnit = unit;
			}
		}
		return best != null ? best : bestUnit;
	}

	static CUnit getEnemyExpansion(final AIController ai) {
		final AITown home = AITowns.getTown(ai, 0);
		if (home == null) {
			return null;
		}
		CUnit best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (unit.isDead() || !ai.isOpponent(unit.getPlayerIndex())
					|| !(unit.getClassifications().contains(CUnitClassification.TOWNHALL)
							|| (unit.getOverlayedGoldMineData() != null))) {
				continue;
			}
			final float[] enemyStart = ai.simulation.getPlayer(unit.getPlayerIndex()).getStartLocation();
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

	private static int armyPower(final AIController ai, final int ownerIndex) {
		int power = 0;
		for (final CUnit unit : ai.simulation.getUnits()) {
			if ((unit.getPlayerIndex() == ownerIndex) && !unit.isDead() && !unit.isBuilding()
					&& !AIUnits.isWorkerType(unit)) {
				final CAbilityHero heroData = unit.getHeroData();
				power += heroData != null ? heroData.getHeroLevel() * 2 : unit.getUnitType().getLevel();
			}
		}
		return power;
	}

	/**
	 * Measured: zero for campaign and melee scripts alike, with the opponent's army unseen, in sight, beside our base,
	 * with a hero and with creeps around.
	 */
	static int getEnemyPower(final AIController ai) {
		return 0;
	}

	/**
	 * An opponent whose army is far weaker than ours is worth an all-out attack on its main base. Measured: only a
	 * building is ever the target; an opponent left with peasants alone gives none.
	 */
	static CUnit getMegaTarget(final AIController ai) {
		final int ours = armyPower(ai, ai.playerIndex);
		if (ours < 20) {
			return null;
		}
		for (int i = 0; i < ai.simulation.getGameData().getMaxPlayers(); i++) {
			if (ai.isOpponent(i) && ((armyPower(ai, i) * 3) <= ours)) {
				final CUnit base = findEnemyBase(ai, i, false);
				if (base != null) {
					return base;
				}
			}
		}
		return null;
	}

	static boolean isTowered(final AIController ai, final CUnit target) {
		if (target == null) {
			return false;
		}
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (!unit.isDead() && unit.isBuilding() && !unit.isConstructing()
					&& ai.isEnemyPlayer(unit.getPlayerIndex()) && !unit.getUnitType().getAttacks().isEmpty()
					&& (distance(unit.getX(), unit.getY(), target.getX(), target.getY()) < 1000)) {
				return true;
			}
		}
		return false;
	}
}
