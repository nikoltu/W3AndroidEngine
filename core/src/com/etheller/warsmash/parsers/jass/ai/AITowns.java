package com.etheller.warsmash.parsers.jass.ai;

import static com.etheller.warsmash.parsers.jass.ai.AIUnits.distance;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;

/**
 * The computer player's towns: finding them around its gold mines and halls, what each one owns, and the next
 * expansion the scripts ask for.
 */
final class AITowns {
	private static final float TOWN_RADIUS = 1500f;

	private AITowns() {
	}

	static void initTowns(final AIController ai) {
		float[] start = ai.player.getStartLocation();
		// A map can found a player's first town away from its start location (and next to another player's mine);
		// the town is where its hall stands.
		final CUnit hall = findOwnHallNear(ai, start[0], start[1]);
		if (hall != null) {
			start = new float[] { hall.getX(), hall.getY() };
		}
		final CUnit mine = findGoldMineNear(ai, start[0], start[1], 1600f);
		ai.towns.add(new AITown(0, start[0], start[1], mine));
		refreshTowns(ai);
		if (!ai.attackCaptain.isHomeSet()) {
			AIAttack.resetCaptainLocations(ai);
		}
	}

	private static CUnit findGoldMineNear(final AIController ai, final float x, final float y, final float radius) {
		CUnit best = null;
		float bestDistance = radius;
		for (final CUnit unit : ai.simulation.getUnits()) {
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

	private static CUnit findOwnHallNear(final AIController ai, final float x, final float y) {
		CUnit best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (ai.isOurs(unit) && unit.isBuilding() && !unit.isDead()
					&& unit.getClassifications().contains(CUnitClassification.TOWNHALL)) {
				final float d = distance(x, y, unit.getX(), unit.getY());
				if (d < bestDistance) {
					bestDistance = d;
					best = unit;
				}
			}
		}
		return best;
	}

	private static CUnit findOverlayOn(final AIController ai, final CUnit mine) {
		if (mine == null) {
			return null;
		}
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (!unit.isDead() && (unit.getOverlayedGoldMineData() != null)
					&& ((unit.getOverlayedGoldMineData().getParentGoldMineUnit() == mine)
							|| (distance(unit.getX(), unit.getY(), mine.getX(), mine.getY()) < 64))) {
				return unit;
			}
		}
		return null;
	}

	static void refreshTowns(final AIController ai) {
		for (final AITown town : ai.towns) {
			CUnit mine = town.getMine();
			if ((mine == null) || ai.isGone(mine)) {
				mine = findGoldMineNear(ai, town.getX(), town.getY(), town.getId() == 0 ? 1600f : 400f);
				town.setMine(mine);
			}
			town.setOverlay(findOverlayOn(ai, mine));
			town.setHall(findHallNear(ai, town));
		}
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (!ai.isOurs(unit) || !unit.isBuilding()) {
				continue;
			}
			final boolean hall = unit.getClassifications().contains(CUnitClassification.TOWNHALL);
			final boolean overlay = unit.getOverlayedGoldMineData() != null;
			if ((!hall && !overlay) || (townOf(ai, unit.getX(), unit.getY()) != -1)) {
				continue;
			}
			final CUnit mine = overlay ? unit.getOverlayedGoldMineData().getParentGoldMineUnit()
					: findGoldMineNear(ai, unit.getX(), unit.getY(), 1000f);
			if (mine == null) {
				continue;
			}
			final int id;
			if ((ai.pendingExpansion != null) && (ai.pendingExpansion.getMine() == mine)) {
				id = ai.pendingExpansion.getId();
				ai.pendingExpansion = null;
			}
			else {
				id = nextTownId(ai);
			}
			final AITown town = new AITown(id, mine.getX(), mine.getY(), mine);
			town.setOverlay(findOverlayOn(ai, mine));
			town.setHall(findHallNear(ai, town));
			ai.towns.add(town);
		}
	}

	private static int nextTownId(final AIController ai) {
		int id = 0;
		for (final AITown town : ai.towns) {
			id = Math.max(id, town.getId() + 1);
		}
		if (ai.pendingExpansion != null) {
			id = Math.max(id, ai.pendingExpansion.getId() + 1);
		}
		return id;
	}

	private static CUnit findHallNear(final AIController ai, final AITown town) {
		CUnit best = null;
		float bestDistance = 1100f;
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (ai.isOurs(unit) && unit.getClassifications().contains(CUnitClassification.TOWNHALL)) {
				final float d = distance(town.getX(), town.getY(), unit.getX(), unit.getY());
				if (d < bestDistance) {
					bestDistance = d;
					best = unit;
				}
			}
		}
		return best;
	}

	static AITown getTown(final AIController ai, final int id) {
		for (final AITown town : ai.towns) {
			if (town.getId() == id) {
				return town;
			}
		}
		return null;
	}

	static int townOf(final AIController ai, final float x, final float y) {
		int best = -1;
		float bestDistance = TOWN_RADIUS;
		for (final AITown town : ai.towns) {
			final float d = Math.min(distance(x, y, town.getX(), town.getY()),
					distance(x, y, town.getCenterX(), town.getCenterY()));
			if (d < bestDistance) {
				bestDistance = d;
				best = town.getId();
			}
		}
		return best;
	}

	static boolean ownsOverlay(final AIController ai, final AITown town) {
		return (town.getOverlay() != null) && (town.getOverlay().getPlayerIndex() == ai.playerIndex);
	}

	static boolean townHasMine(final AIController ai, final int id) {
		final AITown town = getTown(ai, id);
		if ((town == null) || (town.getMine() == null) || (town.getGold() <= 0)) {
			return false;
		}
		return (town.getOverlay() == null) || ownsOverlay(ai, town);
	}

	static boolean townHasHall(final AIController ai, final int id) {
		final AITown town = getTown(ai, id);
		return (town != null) && (town.getHall() != null) && !ai.isGone(town.getHall());
	}

	private static boolean isMineOwned(final AIController ai, final AITown town) {
		return (town.getMine() != null) && (town.getGold() > 0)
				&& (townHasHall(ai, town.getId()) || ownsOverlay(ai, town))
				&& ((town.getOverlay() == null) || ownsOverlay(ai, town));
	}

	static int getMinesOwned(final AIController ai) {
		int count = 0;
		for (final AITown town : ai.towns) {
			if (isMineOwned(ai, town)) {
				count++;
			}
		}
		return count;
	}

	static int getGoldOwned(final AIController ai) {
		int gold = 0;
		for (final AITown town : ai.towns) {
			if (isMineOwned(ai, town)) {
				gold += town.getGold();
			}
		}
		return gold;
	}

	static int townWithMine(final AIController ai) {
		int best = -1;
		for (final AITown town : ai.towns) {
			if (isMineOwned(ai, town) && ((best == -1) || (town.getId() < best))) {
				best = town.getId();
			}
		}
		return best;
	}

	static AITown townNeedingOverlay(final AIController ai) {
		for (final AITown town : ai.towns) {
			if ((town.getMine() != null) && (town.getOverlay() == null) && (town.getGold() > 0)) {
				return town;
			}
		}
		return null;
	}

	/** One pass over the units for everything an expansion check looks at. */
	private static final class ExpansionScan {
		final List<CUnit> mines = new ArrayList<>();
		final List<CUnit> overlays = new ArrayList<>();
		final List<CUnit> foreignHalls = new ArrayList<>();

		ExpansionScan(final AIController ai) {
			for (final CUnit unit : ai.simulation.getUnits()) {
				if (unit.isDead()) {
					continue;
				}
				if ((unit.getGoldMineData() != null) && (unit.getOverlayedGoldMineData() == null)) {
					this.mines.add(unit);
				}
				if (unit.getOverlayedGoldMineData() != null) {
					this.overlays.add(unit);
				}
				if ((unit.getPlayerIndex() != ai.playerIndex)
						&& unit.getClassifications().contains(CUnitClassification.TOWNHALL)) {
					this.foreignHalls.add(unit);
				}
			}
		}
	}

	static int getNextExpansion(final AIController ai) {
		ExpansionScan scan = null;
		if (ai.pendingExpansion != null) {
			scan = new ExpansionScan(ai);
			if (isExpansionAvailable(ai, ai.pendingExpansion.getMine(), scan)) {
				return ai.pendingExpansion.getId();
			}
		}
		ai.pendingExpansion = null;
		final AITown home = getTown(ai, 0);
		if (home == null) {
			return -1;
		}
		if (scan == null) {
			scan = new ExpansionScan(ai);
		}
		CUnit best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final CUnit unit : scan.mines) {
			if (isExpansionAvailable(ai, unit, scan)) {
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
		ai.pendingExpansion = new AITown(nextTownId(ai), best.getX(), best.getY(), best);
		return ai.pendingExpansion.getId();
	}

	private static boolean isExpansionAvailable(final AIController ai, final CUnit mine, final ExpansionScan scan) {
		if ((mine == null) || ai.isGone(mine) || (mine.getGold() <= 0)) {
			return false;
		}
		for (final CUnit unit : scan.overlays) {
			if ((unit.getOverlayedGoldMineData().getParentGoldMineUnit() == mine)
					|| (distance(unit.getX(), unit.getY(), mine.getX(), mine.getY()) < 64)) {
				return false;
			}
		}
		for (final AITown town : ai.towns) {
			if (town.getMine() == mine) {
				return false;
			}
		}
		for (final CUnit unit : scan.foreignHalls) {
			if (distance(unit.getX(), unit.getY(), mine.getX(), mine.getY()) < 1200f) {
				return false;
			}
		}
		return true;
	}

	static CUnit getExpansionFoe(final AIController ai) {
		if (ai.pendingExpansion == null) {
			return null;
		}
		return strongestEnemyNear(ai, ai.pendingExpansion.getX(), ai.pendingExpansion.getY(), 1000f);
	}

	private static CUnit strongestEnemyNear(final AIController ai, final float x, final float y,
			final float radius) {
		CUnit best = null;
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (ai.isEnemy(unit) && (distance(x, y, unit.getX(), unit.getY()) < radius)) {
				// Measured: of equal ones the nearest to the expansion, whichever came first.
				final int level = unit.getUnitType().getLevel();
				if ((best == null) || (level > best.getUnitType().getLevel())
						|| ((level == best.getUnitType().getLevel())
								&& (distance(x, y, unit.getX(), unit.getY()) < distance(x, y, best.getX(),
										best.getY())))) {
					best = unit;
				}
			}
		}
		return best;
	}

	static CUnit getExpansionPeon(final AIController ai) {
		if (ai.pendingExpansion == null) {
			return null;
		}
		return AIConstruction.findBuilder(ai, null, ai.pendingExpansion.getX(), ai.pendingExpansion.getY(), -1, true);
	}

	static boolean setExpansion(final AIController ai, final CUnit peon, final int typeId) {
		final CUnitType type = ai.simulation.getUnitData().getUnitType(new War3ID(typeId));
		if ((ai.pendingExpansion == null) || (peon == null) || (type == null) || !ai.isOurs(peon)) {
			return false;
		}
		final AbilityPointTarget location = AIConstruction.findTownStructureLocation(ai, type, peon,
				ai.pendingExpansion);
		if (location == null) {
			return false;
		}
		return AIConstruction.orderBuild(ai, peon, type, location, ai.pendingExpansion.getId());
	}
}
