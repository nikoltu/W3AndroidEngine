package com.etheller.warsmash.parsers.jass.ai;

import static com.etheller.warsmash.parsers.jass.ai.AIUnits.distance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

	static CUnit getCreepCamp(final AIController ai, final int minLevel, final int maxLevel, final boolean flyersOk) {
		final List<CUnit> creeps = new ArrayList<>();
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (!unit.isDead() && !unit.isHidden() && ai.simulation.isNeutralAggressive(unit.getPlayerIndex())
					&& !unit.isBuilding()) {
				creeps.add(unit);
			}
		}
		final float homeX = ai.attackCaptain.getHomeX();
		final float homeY = ai.attackCaptain.getHomeY();
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
				reachable &= !ai.unreachable.contains(member);
			}
			if ((level < minLevel) || (level > maxLevel) || (flyers && !flyersOk) || !reachable
					|| nearEnemyBase(ai, x, y)) {
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

	private static boolean nearEnemyBase(final AIController ai, final float x, final float y) {
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (!unit.isDead() && unit.isBuilding() && ai.isOpponent(unit.getPlayerIndex())
					&& (distance(x, y, unit.getX(), unit.getY()) < 1500)) {
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
		if ((ai.enemyBase == null) || ai.enemyBase.isDead()) {
			ai.enemyBase = findEnemyBase(ai, -1);
		}
		return ai.enemyBase;
	}

	/**
	 * @param onlyPlayer -1 for the nearest opponent
	 * @return the town hall nearest to that opponent's start location, else any of its buildings
	 */
	static CUnit findEnemyBase(final AIController ai, final int onlyPlayer) {
		final AITown home = AITowns.getTown(ai, 0);
		final float[] start = ai.player.getStartLocation();
		final float hx = home == null ? start[0] : home.getX();
		final float hy = home == null ? start[1] : home.getY();
		CUnit best = null;
		float bestScore = Float.MAX_VALUE;
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (unit.isDead() || !unit.isBuilding() || !ai.isOpponent(unit.getPlayerIndex())
					|| ((onlyPlayer != -1) && (unit.getPlayerIndex() != onlyPlayer))) {
				continue;
			}
			final float[] enemyStart = ai.simulation.getPlayer(unit.getPlayerIndex()).getStartLocation();
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
	 * An opponent whose army is far weaker than ours is worth an all-out attack on its main base.
	 */
	static CUnit getMegaTarget(final AIController ai) {
		final int ours = armyPower(ai, ai.playerIndex);
		if (ours < 20) {
			return null;
		}
		for (int i = 0; i < ai.simulation.getGameData().getMaxPlayers(); i++) {
			if (ai.isOpponent(i) && ((armyPower(ai, i) * 3) <= ours)) {
				final CUnit base = findEnemyBase(ai, i);
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
