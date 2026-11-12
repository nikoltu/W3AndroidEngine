package com.etheller.warsmash.parsers.jass.ai;

import static com.etheller.warsmash.parsers.jass.ai.AIUnits.canHarvestGold;
import static com.etheller.warsmash.parsers.jass.ai.AIUnits.canHarvestLumber;
import static com.etheller.warsmash.parsers.jass.ai.AIUnits.distance;
import static com.etheller.warsmash.parsers.jass.ai.AIUnits.isBuilding;
import static com.etheller.warsmash.parsers.jass.ai.AIUnits.isIdle;
import static com.etheller.warsmash.parsers.jass.ai.AIUnits.isWorkerType;
import static com.etheller.warsmash.parsers.jass.ai.AIUnits.minesFromOverlay;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityWispHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.harvest.CBehaviorHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.harvest.CBehaviorReturnResources;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIdUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

/**
 * The computer player's workers: the gold and lumber jobs the scripts ask for, per town, and the repairs.
 */
final class AIHarvest {
	static final int GOLD = 0;
	static final int WOOD = 1;
	private static final int TREE_CACHE_TICKS = CTimer.wholeSteps(20);
	private static final int SETTLE_TICKS = 5;

	private AIHarvest() {
	}

	static void harvest(final AIController ai, final int resource, final int town, final int peons) {
		ai.harvestPassPending = true;
		ai.harvestCallTick = ai.ticks;
		if (peons > 0) {
			ai.harvestRequests.add(new int[] { resource, town, peons });
		}
	}

	private static boolean canDo(final AIController ai, final CUnit worker, final int resource, final int town) {
		if (resource == WOOD) {
			return canHarvestLumber(worker);
		}
		if (!canHarvestGold(worker)) {
			return false;
		}
		final AITown aiTown = AITowns.getTown(ai, town);
		if ((aiTown == null) || (aiTown.getMine() == null) || (aiTown.getGold() <= 0)) {
			return false;
		}
		if (minesFromOverlay(worker)) {
			return AITowns.ownsOverlay(ai, aiTown) || (worker.getFirstAbilityOfType(CAbilityWispHarvest.class) != null);
		}
		return aiTown.getOverlay() == null;
	}

	/**
	 * Measured: the jobs are dealt out only when the script changes its requests. A lowered request takes the job
	 * from the last workers who got it, but they go on with their work. A request still short takes idle workers
	 * first, then free workers already at that resource, then free workers at the other one; of those, one carrying
	 * a load stops and waits for the next change of requests, which sends it off. Workers inside a
	 * mine are passed over. Idle workers left without a job then mine gold.
	 */
	static void assignHarvesters(final AIController ai) {
		final List<CUnit> workers = new ArrayList<>();
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (ai.isOurs(unit) && !ai.isBuildJobWorker(unit) && !ai.attackCaptain.getMembers().contains(unit)
					&& (canHarvestGold(unit) || canHarvestLumber(unit))) {
				workers.add(unit);
			}
		}
		ai.harvestJobs.keySet().retainAll(workers);
		ai.harvestUnsent.retainAll(ai.harvestJobs.keySet());
		// A script sets its requests over several calls that may run in different ticks; they are dealt once it is
		// done.
		if (ai.harvestPassPending && ((ai.ticks - ai.harvestCallTick) >= SETTLE_TICKS)) {
			ai.harvestPassPending = false;
			dealJobs(ai, workers);
			for (final CUnit worker : workers) {
				if (!ai.harvestJobs.containsKey(worker) && !worker.isHidden() && isFree(worker)
						&& isWorkerType(worker)) {
					final int town = canDo(ai, worker, GOLD, 0) ? 0 : Math.max(AITowns.townWithMine(ai), 0);
					if (canDo(ai, worker, GOLD, town)) {
						orderHarvest(ai, worker, GOLD, town);
					}
				}
			}
		}
		// Orders the town cannot carry out yet (no trees or mine known) are given again until the worker sets off.
		for (final Iterator<CUnit> iterator = ai.harvestUnsent.iterator(); iterator.hasNext();) {
			final CUnit worker = iterator.next();
			if (!isIdle(worker)) {
				iterator.remove();
			}
			else if (!worker.isHidden()) {
				final int[] job = ai.harvestJobs.get(worker);
				orderHarvest(ai, worker, job[0], job[1]);
			}
		}
		if (ai.peonsRepair) {
			assignRepairs(ai, workers);
		}
	}

	private static void dealJobs(final AIController ai, final List<CUnit> workers) {
		final List<int[]> requests = new ArrayList<>();
		for (final int[] request : ai.harvestRequests) {
			int[] merged = null;
			for (final int[] existing : requests) {
				if ((existing[0] == request[0]) && (existing[1] == request[1])) {
					merged = existing;
				}
			}
			if (merged == null) {
				requests.add(new int[] { request[0], request[1], request[2] });
			}
			else {
				merged[2] += request[2];
			}
		}
		final List<CUnit> holders = new ArrayList<>(ai.harvestJobs.keySet());
		for (int i = holders.size() - 1; i >= 0; i--) {
			final CUnit worker = holders.get(i);
			final int[] job = ai.harvestJobs.get(worker);
			int wanted = 0;
			for (final int[] request : requests) {
				if ((request[0] == job[0]) && (request[1] == job[1])) {
					wanted = request[2];
				}
			}
			if (countJobs(ai, job[0], job[1]) > wanted) {
				ai.harvestJobs.remove(worker);
			}
		}
		for (final Map.Entry<CUnit, int[]> entry : ai.harvestJobs.entrySet()) {
			final CUnit worker = entry.getKey();
			if (!worker.isHidden() && isFree(worker)) {
				ai.harvestUnsent.add(worker);
				orderHarvest(ai, worker, entry.getValue()[0], entry.getValue()[1]);
			}
		}
		for (final int[] request : requests) {
			final int resource = request[0];
			final int town = request[1];
			for (int stage = 0; stage < 3; stage++) {
				for (final CUnit worker : workers) {
					if (countJobs(ai, resource, town) >= request[2]) {
						break;
					}
					if (ai.harvestJobs.containsKey(worker) || worker.isHidden() || isBuilding(worker)
							|| !canDo(ai, worker, resource, town)) {
						continue;
					}
					final boolean idle = isFree(worker);
					if (stage == 0) {
						if (idle) {
							ai.harvestJobs.put(worker, new int[] { resource, town });
							ai.harvestUnsent.add(worker);
							orderHarvest(ai, worker, resource, town);
						}
						continue;
					}
					if (idle) {
						continue;
					}
					final int doing = currentResource(worker);
					if ((stage == 1) && (doing == resource)) {
						ai.harvestJobs.put(worker, new int[] { resource, town });
					}
					else if ((stage == 2) && (doing != -1) && (doing != resource)) {
						ai.harvestJobs.put(worker, new int[] { resource, town });
						final CAbilityHarvest harvest = worker.getFirstAbilityOfType(CAbilityHarvest.class);
						if (harvest.getCarriedResourceAmount() > 0) {
							worker.order(ai.simulation, OrderIds.stop, null);
						}
						else {
							ai.harvestUnsent.add(worker);
							orderHarvest(ai, worker, resource, town);
						}
					}
				}
			}
		}
	}

	/**
	 * Idle, or a worker busy with something other than gathering, like the walk of a freshly made one.
	 */
	private static boolean isFree(final CUnit worker) {
		return isIdle(worker) || (isWorkerType(worker) && (currentResource(worker) == -1) && !isBuilding(worker)
				&& !(worker.getCurrentBehavior() instanceof CBehaviorHarvest)
				&& !(worker.getCurrentBehavior() instanceof CBehaviorReturnResources));
	}

	private static int countJobs(final AIController ai, final int resource, final int town) {
		int count = 0;
		for (final int[] job : ai.harvestJobs.values()) {
			if ((job[0] == resource) && (job[1] == town)) {
				count++;
			}
		}
		return count;
	}

	private static int currentResource(final CUnit worker) {
		final CAbilityHarvest harvest = worker.getFirstAbilityOfType(CAbilityHarvest.class);
		if (harvest == null) {
			return -1;
		}
		final CWidget target = harvest.getLastHarvestTarget();
		if (target instanceof CDestructable) {
			return WOOD;
		}
		return target instanceof CUnit ? GOLD : -1;
	}

	private static void orderHarvest(final AIController ai, final CUnit worker, final int resource,
			final int townId) {
		final AITown town = AITowns.getTown(ai, townId);
		if (town == null) {
			return;
		}
		if (resource == GOLD) {
			if (minesFromOverlay(worker)) {
				if (AITowns.ownsOverlay(ai, town)) {
					orderHarvestTarget(ai, worker, town.getOverlay());
				}
				else if ((town.getOverlay() == null) && (town.getMine() != null)) {
					entangle(ai, town);
				}
			}
			else if (town.getMine() != null) {
				orderHarvestTarget(ai, worker, town.getMine());
			}
			return;
		}
		final List<CDestructable> trees = getTrees(ai, town);
		int tries = 0;
		for (final Iterator<CDestructable> iterator = trees.iterator(); iterator.hasNext() && (tries < 6);) {
			final CDestructable tree = iterator.next();
			if (tree.isDead()) {
				iterator.remove();
				continue;
			}
			tries++;
			if (orderHarvestTarget(ai, worker, tree)) {
				return;
			}
		}
	}

	/**
	 * A worker that carries resources only takes the return order, so it returns them first and goes on to the
	 * target from there.
	 */
	private static boolean orderHarvestTarget(final AIController ai, final CUnit worker, final CWidget target) {
		if (worker.order(ai.simulation, OrderIds.harvest, target)) {
			return true;
		}
		final CAbilityHarvest harvest = worker.getFirstAbilityOfType(CAbilityHarvest.class);
		if ((harvest == null) || (harvest.getCarriedResourceAmount() <= 0)) {
			return false;
		}
		harvest.setLastHarvestTarget(target);
		return worker.order(ai.simulation, OrderIds.returnresources, null);
	}

	private static void entangle(final AIController ai, final AITown town) {
		final int entangleOrder = OrderIdUtils.getOrderId("entangleinstant");
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (ai.isOurs(unit) && AIUnits.isReady(unit)
					&& unit.getClassifications().contains(CUnitClassification.TOWNHALL)
					&& (distance(unit.getX(), unit.getY(), town.getMine().getX(), town.getMine().getY()) < 1200)) {
				unit.order(ai.simulation, entangleOrder, town.getMine());
				return;
			}
		}
	}

	private static List<CDestructable> getTrees(final AIController ai, final AITown town) {
		final Integer cachedTick = ai.townTreesTick.get(town.getId());
		List<CDestructable> trees = ai.townTrees.get(town.getId());
		if ((trees == null) || (cachedTick == null) || ((ai.ticks - cachedTick) > TREE_CACHE_TICKS)
				|| trees.isEmpty()) {
			trees = new ArrayList<>();
			final float cx = town.getCenterX();
			final float cy = town.getCenterY();
			for (final CDestructable destructable : ai.simulation.getDestructables()) {
				if (!destructable.isDead() && destructable.getDestType().getTargetedAs().contains(CTargetType.TREE)
						&& (distance(cx, cy, destructable.getX(), destructable.getY()) < 2500)) {
					trees.add(destructable);
				}
			}
			trees.sort((a, b) -> Float.compare(distance(cx, cy, a.getX(), a.getY()),
					distance(cx, cy, b.getX(), b.getY())));
			ai.townTrees.put(town.getId(), trees);
			ai.townTreesTick.put(town.getId(), ai.ticks);
		}
		return trees;
	}

	private static void assignRepairs(final AIController ai, final List<CUnit> workers) {
		if (ai.threat != null) {
			return;
		}
		for (final CUnit worker : workers) {
			if (isBuilding(worker) && !ai.isBuildJobWorker(worker)) {
				return;
			}
		}
		for (final CUnit unit : ai.simulation.getUnits()) {
			if (!ai.isOurs(unit) || !unit.isBuilding() || unit.isConstructing() || unit.isUpgrading()
					|| (unit.getLife() > (unit.getMaxLife() * 0.6f))) {
				continue;
			}
			CUnit best = null;
			float bestDistance = 1500f;
			for (final CUnit worker : workers) {
				final int[] job = ai.harvestJobs.get(worker);
				if (worker.isHidden() || (job == null) || (job[0] != WOOD)) {
					continue;
				}
				final float d = distance(worker.getX(), worker.getY(), unit.getX(), unit.getY());
				if (d < bestDistance) {
					bestDistance = d;
					best = worker;
				}
			}
			if ((best != null) && best.order(ai.simulation, AIUnits.repairOrderId(best), unit)) {
				return;
			}
		}
	}
}
