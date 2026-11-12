package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors;

import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityRanged;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.neutral.CAbilityWayGate;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMoveIntoRangeFor.PairAbilityLocator;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

/**
 * Measured on 1.29: a unit ordered to "move" onto an active Way Gate goes through it only when that brings it back to
 * the gate: a gate near the drop point must lead back to it, and the unit must not have a straight walk to the gate's
 * near side (from there it just walks up to it). Otherwise it walks up to the gate like to any structure. A ground
 * unit is moved once it comes within the gate's range (half its area) plus the gate's collision of the point nearest
 * the gate it can stand on. A flyer flies onto the gate and is moved on reaching it, and stays at the destination.
 */
public class CBehaviorMoveThroughWayGate extends CBehaviorMoveIntoRangeFor implements PairAbilityLocator {
	private static final float CELL = 32f;
	private static final int ENTRY_SEARCH_RINGS = 32;
	private static final float STRAIGHT_WALK_STEP = 16f;

	private CUnit goalGate;
	private boolean returning;
	private CUnit entryGate;
	private float entryX;
	private float entryY;

	public CBehaviorMoveThroughWayGate(final CUnit unit) {
		super(unit);
	}

	public CBehavior reset(final CSimulation game, final CUnit gate) {
		this.goalGate = gate;
		this.returning = false;
		this.entryGate = null;
		if (!leadsBackToGate(game, gate)) {
			return this.unit.getMoveBehavior().reset(OrderIds.move, new AbilityPointTarget(gate.getX(), gate.getY()));
		}
		return super.reset(game, OrderIds.move, gate, this);
	}

	@Override
	public CAbilityRanged getPartnerAbility(final CSimulation game, final CUnit caster, final CUnit transport,
			final boolean ignoreRange, final boolean ignoreDisabled) {
		return CAbilityWayGate.getWayGateAbility(game, caster, transport, ignoreRange, ignoreDisabled);
	}

	@Override
	public boolean isWithinRange(final CSimulation simulation) {
		final CUnit gate = this.target.visit(AbilityTargetVisitor.UNIT);
		if (gate == null) {
			return false;
		}
		final CAbilityWayGate wayGate = CAbilityWayGate.getWayGateAbility(simulation, this.unit, gate, true, true);
		if (wayGate == null) {
			return false;
		}
		if (isFlyer()) {
			// reached within this step
			return distance(this.unit.getX(), this.unit.getY(), gate.getX(),
					gate.getY()) <= (this.unit.getSpeed() * WarsmashConstants.SIMULATION_STEP_TIME);
		}
		updateEntry(simulation, gate);
		return distance(this.unit.getX(), this.unit.getY(), this.entryX,
				this.entryY) <= (wayGate.getCastRange() + gate.getUnitType().getCollisionSize());
	}

	@Override
	protected CBehavior update(final CSimulation simulation, final boolean withinFacingWindow) {
		final CUnit gate = this.target.visit(AbilityTargetVisitor.UNIT);
		if (gate != null) {
			final CAbilityWayGate wayGate = CAbilityWayGate.getWayGateAbility(simulation, this.unit, gate, true,
					false);
			if ((wayGate != null) && wayGate.teleport(simulation, gate, this.unit)) {
				if (this.returning) {
					// back at the gate it was sent to, it walks on up to it like to any structure
					return this.unit.getMoveBehavior().reset(OrderIds.move,
							new AbilityPointTarget(this.goalGate.getX(), this.goalGate.getY()));
				}
				if (isFlyer()) {
					return this.unit.pollNextOrderBehavior(simulation);
				}
				final CUnit returnGate = findReturnGate(simulation);
				if (returnGate != null) {
					this.returning = true;
					return innerReset(simulation, returnGate);
				}
			}
		}
		return this.unit.pollNextOrderBehavior(simulation);
	}

	private boolean isFlyer() {
		return this.unit.getMovementType() == MovementType.FLY;
	}

	private boolean leadsBackToGate(final CSimulation simulation, final CUnit gate) {
		final CAbilityWayGate wayGate = CAbilityWayGate.getWayGateAbility(simulation, this.unit, gate, true, false);
		if ((wayGate == null) || (wayGate.getDestination() == null) || this.unit.isMovementDisabled()) {
			return false;
		}
		if (isFlyer()) {
			return true;
		}
		updateEntry(simulation, gate);
		final float goalX = this.entryX;
		final float goalY = this.entryY;
		if (isStraightWalk(simulation, goalX, goalY)) {
			return false;
		}
		final AbilityPointTarget destination = wayGate.getDestination();
		final double direct = this.unit.distance(goalX, goalY);
		for (final CUnit candidate : simulation.getUnits()) {
			if ((candidate == gate) || candidate.isDead()) {
				continue;
			}
			final CAbilityWayGate returnWayGate = CAbilityWayGate.getWayGateAbility(simulation, this.unit, candidate,
					true, false);
			if ((returnWayGate == null) || (returnWayGate.getDestination() == null)) {
				continue;
			}
			final AbilityPointTarget back = returnWayGate.getDestination();
			if ((distance(destination.x, destination.y, candidate.getX(), candidate.getY())
					+ distance(back.x, back.y, goalX, goalY)) < direct) {
				return true;
			}
		}
		return false;
	}

	private boolean isStraightWalk(final CSimulation simulation, final float toX, final float toY) {
		final PathingGrid pathingGrid = simulation.getPathingGrid();
		final MovementType movementType = this.unit.getMovementType();
		final float collisionSize = this.unit.getUnitType().getCollisionSize();
		final float fromX = this.unit.getX();
		final float fromY = this.unit.getY();
		final double length = distance(fromX, fromY, toX, toY);
		final int steps = (int) Math.ceil(length / STRAIGHT_WALK_STEP);
		for (int i = 1; i < steps; i++) {
			final float t = (float) i / steps;
			if (!pathingGrid.isPathable(fromX + ((toX - fromX) * t), fromY + ((toY - fromY) * t), movementType,
					collisionSize)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The pathing cell centre nearest the gate that the unit can stand on; measured on 1.29, a ground unit stops there
	 * when it walks up to the gate.
	 */
	private void updateEntry(final CSimulation simulation, final CUnit gate) {
		if (this.entryGate == gate) {
			return;
		}
		this.entryGate = gate;
		final PathingGrid pathingGrid = simulation.getPathingGrid();
		final MovementType movementType = this.unit.getMovementType();
		// whole cells only: a Footman (31) fits a cell next to trees that a Paladin (32) does not
		final float collisionSize = (float) Math.floor(this.unit.getUnitType().getCollisionSize() / CELL) * CELL;
		final float baseX = ((float) Math.floor(gate.getX() / CELL) * CELL) + (CELL / 2);
		final float baseY = ((float) Math.floor(gate.getY() / CELL) * CELL) + (CELL / 2);
		this.entryX = gate.getX();
		this.entryY = gate.getY();
		double best = Double.MAX_VALUE;
		for (int ring = 0; ring <= ENTRY_SEARCH_RINGS; ring++) {
			if (((ring - 1) * CELL) > best) {
				break;
			}
			for (int i = ring; i >= -ring; i--) {
				for (int j = ring; j >= -ring; j--) {
					if ((Math.abs(i) != ring) && (Math.abs(j) != ring)) {
						continue;
					}
					final float x = baseX + (i * CELL);
					final float y = baseY + (j * CELL);
					final double distance = distance(x, y, gate.getX(), gate.getY());
					if ((distance < best) && pathingGrid.contains(x, y)
							&& pathingGrid.isPathable(x, y, movementType, collisionSize)) {
						best = distance;
						this.entryX = x;
						this.entryY = y;
					}
				}
			}
		}
	}

	private CUnit findReturnGate(final CSimulation simulation) {
		double best = distance(this.unit.getX(), this.unit.getY(), this.goalGate.getX(), this.goalGate.getY());
		CUnit bestGate = null;
		for (final CUnit candidate : simulation.getUnits()) {
			if ((candidate == this.goalGate) || candidate.isDead()) {
				continue;
			}
			final CAbilityWayGate wayGate = CAbilityWayGate.getWayGateAbility(simulation, this.unit, candidate, true,
					false);
			if (wayGate == null) {
				continue;
			}
			final AbilityPointTarget destination = wayGate.getDestination();
			if (destination == null) {
				continue;
			}
			final double viaGate = distance(this.unit.getX(), this.unit.getY(), candidate.getX(), candidate.getY())
					+ distance(destination.x, destination.y, this.goalGate.getX(), this.goalGate.getY());
			if (viaGate < best) {
				best = viaGate;
				bestGate = candidate;
			}
		}
		return bestGate;
	}

	private static double distance(final float x1, final float y1, final float x2, final float y2) {
		final double dx = x2 - x1;
		final double dy = y2 - y1;
		return StrictMath.sqrt((dx * dx) + (dy * dy));
	}
}
