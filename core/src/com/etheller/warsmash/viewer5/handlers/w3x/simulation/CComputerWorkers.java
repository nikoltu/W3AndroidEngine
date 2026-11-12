package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.List;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityWispHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityGoldMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

/**
 * Measured on 1.29: a worker that a script creates for a computer player, or gives to one, is sent to work at once,
 * with or without an AI script. A gold worker works for the owner's town hall nearest to it: it goes to the nearest
 * gold mine within {@link #HALL_MINE_RANGE} of that hall that does not yet have {@link #FULL_MINE_WORKERS} of the
 * owner's workers harvesting it, or else to the nearest one there, whether the owner sees it or not; with no mine
 * there it gets no order. An owner without a town hall sends it to the mine nearest its start location, full or not.
 * A Wisp goes to the nearest tree. When the target is within {@link #FAR_TARGET} and the worker cannot walk to it, the
 * worker gets no order. Other workers (Acolyte, Ghoul) stay idle, as do trained ones and those of user players.
 */
public final class CComputerWorkers {
	private static final int FULL_MINE_WORKERS = 5;
	private static final float MINE_REACH = 192;
	private static final float TREE_REACH = 96;
	private static final float NEAR_TARGET = 128;
	private static final float FAR_TARGET = 1700;
	// Measured: a hall 2036 from a mine still sends its new workers there, one 2857 away gets none.
	private static final float HALL_MINE_RANGE = 2400;

	public static void onScriptUnitGiven(final CSimulation simulation, final CUnit unit) {
		if ((unit == null) || unit.isDead() || unit.isHidden()
				|| !unit.getClassifications().contains(CUnitClassification.PEON)) {
			return;
		}
		final CPlayer player = simulation.getPlayer(unit.getPlayerIndex());
		if (player.getController() != CMapControl.COMPUTER) {
			return;
		}
		final CAbilityHarvest harvest = unit.getFirstAbilityOfType(CAbilityHarvest.class);
		if ((harvest != null) && (harvest.getGoldCapacity() > 0)) {
			// Units made during map initialization are not in the simulation's list yet.
			final List<CUnit> units = simulation.getUnitsIncludingNew();
			final CUnit mine = findMine(simulation, units, unit, player);
			if ((mine != null) && canReach(simulation, unit, mine.getX(), mine.getY(), MINE_REACH)) {
				unit.order(simulation, OrderIds.harvest, mine);
			}
		}
		else if (unit.getFirstAbilityOfType(CAbilityWispHarvest.class) != null) {
			final CDestructable tree = findTree(simulation, unit);
			if ((tree != null) && canReach(simulation, unit, tree.getX(), tree.getY(), TREE_REACH)) {
				unit.order(simulation, OrderIds.smart, tree);
			}
		}
	}

	/**
	 * Measured: a worker that cannot walk to a target 1661 away gets no order, one 1722 away gets it anyway, and so does
	 * one standing right beside its target.
	 */
	private static boolean canReach(final CSimulation simulation, final CUnit unit, final float x, final float y,
			final float reach) {
		final double distance = unit.distance(x, y);
		return (distance < NEAR_TARGET) || (distance > FAR_TARGET) || simulation.canWalkNear(unit, x, y, reach);
	}

	private static CUnit findMine(final CSimulation simulation, final List<CUnit> units, final CUnit worker,
			final CPlayer player) {
		CUnit hall = null;
		double hallDistance = Double.MAX_VALUE;
		for (final CUnit unit : units) {
			if ((unit.getPlayerIndex() == player.getId()) && !unit.isDead() && !unit.isConstructing()
					&& unit.getClassifications().contains(CUnitClassification.TOWNHALL)) {
				final double distance = unit.distanceSquaredNoCollision(worker);
				if (distance < hallDistance) {
					hallDistance = distance;
					hall = unit;
				}
			}
		}
		final float[] start = player.getStartLocation();
		CUnit nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		CUnit nearestFull = null;
		double nearestFullDistance = Double.MAX_VALUE;
		for (final CUnit mine : units) {
			if (mine.isDead() || (mine.getFirstAbilityOfType(CAbilityGoldMine.class) == null)) {
				continue;
			}
			final double distance;
			if (hall != null) {
				if (mine.distanceSquaredNoCollision(hall) > (HALL_MINE_RANGE * HALL_MINE_RANGE)) {
					continue;
				}
				distance = mine.distanceSquaredNoCollision(worker);
			}
			else if (start != null) {
				final double dx = mine.getX() - start[0];
				final double dy = mine.getY() - start[1];
				distance = (dx * dx) + (dy * dy);
			}
			else {
				distance = mine.distanceSquaredNoCollision(worker);
			}
			if ((hall == null) || (workersAt(units, player, mine) < FULL_MINE_WORKERS)) {
				if (distance < nearestDistance) {
					nearestDistance = distance;
					nearest = mine;
				}
			}
			else if (distance < nearestFullDistance) {
				nearestFullDistance = distance;
				nearestFull = mine;
			}
		}
		return nearest != null ? nearest : nearestFull;
	}

	private static int workersAt(final List<CUnit> units, final CPlayer player, final CUnit mine) {
		int count = 0;
		for (final CUnit unit : units) {
			if ((unit.getPlayerIndex() != player.getId()) || unit.isDead()) {
				continue;
			}
			final CAbilityHarvest harvest = unit.getFirstAbilityOfType(CAbilityHarvest.class);
			if ((harvest == null) || (harvest.getLastHarvestTarget() != mine)) {
				continue;
			}
			final COrder order = unit.getCurrentOrder();
			final int orderId = order == null ? 0 : order.getOrderId();
			// Measured: a worker that was stopped still holds its place at the mine it was sent to.
			if ((orderId == OrderIds.harvest) || (orderId == OrderIds.resumeharvesting)
					|| (orderId == OrderIds.returnresources) || (orderId == OrderIds.stop) || (orderId == 0)) {
				count++;
			}
		}
		return count;
	}

	private static CDestructable findTree(final CSimulation simulation, final CUnit worker) {
		CDestructable nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (final CDestructable tree : simulation.getDestructables()) {
			if (!tree.isDead() && tree.canBeTargetedBy(simulation, worker, CAbilityWispHarvest.TREE_ALIVE_TYPE_ONLY)) {
				final double distance = tree.distanceSquaredNoCollision(worker);
				if (distance < nearestDistance) {
					nearestDistance = distance;
					nearest = tree;
				}
			}
		}
		return nearest;
	}

	private CComputerWorkers() {
	}
}
