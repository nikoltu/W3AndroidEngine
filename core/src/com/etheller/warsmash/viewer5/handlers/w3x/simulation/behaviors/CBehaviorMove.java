package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWorldCollision;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.harvest.CBehaviorWispHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CPathPoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CPathfindingProcessor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

public class CBehaviorMove implements CBehavior {
	private static final boolean ALWAYS_INTERRUPT_MOVE = false;
	/**
	 * Measured on 1.29: a unit turns its heading by turnRate radians every 0.029 s and walks once the
	 * heading is inside the propulsion window, straight toward its next point; the drawn facing turns slower and on its
	 * own.
	 */
	private static final float ORIGINAL_TURN_PERIOD = 0.029f;

	/** Degrees the heading turns in one simulation step; the same rate when turning to attack or cast. */
	public static float getTurnStep(final CUnit unit) {
		return (float) StrictMath.toDegrees(unit.getTurnRate())
				* (WarsmashConstants.SIMULATION_STEP_TIME / ORIGINAL_TURN_PERIOD);
	}
	/** Ticks a unit waits behind walking units before it searches a way around them. */
	private static final int WAIT_FOR_WALKERS_TICKS = 10;
	private static final float MAX_DETOUR_AROUND_UNITS = 320f;
	private static final float GIVE_UP_NEAR_TARGET = 384f;
	private static final int START_TICKS = 4;
	/**
	 * Measured on 1.29: a unit of an attack-moving crowd blocked next to the point keeps its order for a while (the
	 * crowd packs round the point over a few seconds) before it stops there.
	 */
	private static final int BLOCKED_NEAR_TARGET_TICKS = 50;
	// Measured on 1.29: a move to a point ends once the unit is within this distance of it, after a whole step of
	// 0.03 s of walking.
	static final float MOVE_END_DISTANCE = 14.5f;
	private static final float MOVE_END_STEP_TIME = 0.03f;
	private static final int APPROACH_SEARCH_CELLS = 65536;

	private static final Rectangle tempRect = new Rectangle();
	private final CUnit unit;
	private int highlightOrderId;
	private final TargetVisitingResetter targetVisitingResetter;

	public CBehaviorMove(final CUnit unit) {
		this.unit = unit;
		this.targetVisitingResetter = new TargetVisitingResetter();
	}

	private boolean wasWithinPropWindow = false;
	private List<CPathPoint> path = null;
	private CPathfindingProcessor.GridMapping gridMapping;
	private CPathPoint target;
	private int searchCycles = 0;
	private CUnit followUnit;
	private CRangedBehavior rangedBehavior;
	private boolean firstUpdate = true;
	private double movedDistance;
	private boolean disableCollision = false;
	private boolean pathfindingActive = false;
	private boolean firstPathfindJob = false;
	private boolean pathfindingFailedGiveUp;
	private int giveUpUntilTurnTick;
	private int prevSpeed = 0;
	private boolean pathReachesGoal = true;
	private boolean followSearch = false;
	private int waitForWalkersTicks = 0;
	private boolean searchAfterBlock = false;
	private double pathLengthWhenBlocked;
	private double pathStartDistance;
	private CGroupMove group;
	private CDestructable approachTarget;
	private int resetCount;
	private boolean takenOver;
	private int blockedNearTargetTicks;

	public CBehaviorMove reset(final int highlightOrderId, final AbilityTarget target) {
		if (!continuesSameMove(highlightOrderId, target)) {
			target.visit(this.targetVisitingResetter.reset(highlightOrderId));
		}
		this.rangedBehavior = null;
		this.group = null;
		resetDisableCollision(false);
		this.resetCount++;
		return this;
	}

	/**
	 * Measured on 1.29: a unit given the same move order again and again (a player clicking on, a script repeating its
	 * order) walks on along its way. Here a long path search spans many ticks, so starting it over threw away the path
	 * and walked the unit straight at the target, into the nearest pocket, at every repeat.
	 */
	private boolean continuesSameMove(final int highlightOrderId, final AbilityTarget target) {
		if (!(target instanceof AbilityPointTarget) || (this.unit.getCurrentBehavior() != this)
				|| (this.rangedBehavior != null) || (this.followUnit != null) || (this.target == null)
				|| (highlightOrderId != this.highlightOrderId) || this.pathfindingFailedGiveUp) {
			return false;
		}
		return isUnderwayTo((AbilityPointTarget) target);
	}

	/**
	 * Whether an attack-move given again to the same point can walk on along this move (see continuesSameMove).
	 */
	public boolean continuesAttackMove(final CBehaviorAttackMove attackMove, final AbilityPointTarget point) {
		return (this.rangedBehavior == attackMove) && (this.followUnit == null) && (this.target != null)
				&& !this.pathfindingFailedGiveUp && isUnderwayTo(point);
	}

	private boolean isUnderwayTo(final AbilityPointTarget point) {
		if ((point.x != this.target.x) || (point.y != this.target.y)) {
			return false;
		}
		return this.pathfindingActive || ((this.path != null) && !this.path.isEmpty() && this.pathReachesGoal);
	}

	public CBehaviorMove reset(final AbilityTarget target, final CRangedBehavior rangedBehavior,
			final boolean disableCollision) {
		final int highlightOrderId = rangedBehavior.getHighlightOrderId();
		target.visit(this.targetVisitingResetter.reset(highlightOrderId));
		this.rangedBehavior = rangedBehavior;
		this.group = null;
		resetDisableCollision(disableCollision);
		this.resetCount++;
		return this;
	}

	public int getResetCount() {
		return this.resetCount;
	}

	/**
	 * The walk this behaviour was on was replaced by an order that set it up for itself and does not walk now (its
	 * target is already in reach). Ending it then is not an interruption of that order.
	 */
	public void takenOver() {
		this.takenOver = true;
	}

	public CGroupMove getGroup() {
		return this.group;
	}

	public void setGroup(final CGroupMove group) {
		this.group = group;
	}

	private void resetDisableCollision(final boolean disableCollision) {
		final boolean previousCollisionState = this.disableCollision;
		this.disableCollision = disableCollision;
		if ((previousCollisionState != this.disableCollision) && (this.unit.getCurrentBehavior() == this)) {
			// begin() / end() are sometimes not called on behavior when it resets
			// while it was already the active behavior. Until that design is fixed,
			// need to ensure we can't leave unit stuck in "no collision" mode here
			this.unit.setNoCollisionMovementType(this.disableCollision);
		}
	}

	private void internalResetMove(final int highlightOrderId, final float targetX, final float targetY) {
		this.highlightOrderId = highlightOrderId;
		this.wasWithinPropWindow = false;
		this.gridMapping = CPathfindingProcessor.isCollisionSizeBetterSuitedForCorners(
				this.unit.getUnitType().getCollisionSize()) ? CPathfindingProcessor.GridMapping.CORNERS
						: CPathfindingProcessor.GridMapping.CELLS;
		this.target = new CPathPoint(targetX, targetY);
		this.path = null;
		this.searchCycles = 0;
		this.followUnit = null;
		this.firstUpdate = true;
		this.movedDistance = 0;
		this.pathfindingFailedGiveUp = false;
		this.giveUpUntilTurnTick = 0;
		this.followSearch = false;
		this.waitForWalkersTicks = 0;
		this.blockedNearTargetTicks = 0;
		this.searchAfterBlock = false;
		this.approachTarget = null;
	}

	private void internalResetMove(final int highlightOrderId, final CUnit followUnit) {
		this.highlightOrderId = highlightOrderId;
		this.wasWithinPropWindow = false;
		this.gridMapping = CPathfindingProcessor.isCollisionSizeBetterSuitedForCorners(
				this.unit.getUnitType().getCollisionSize()) ? CPathfindingProcessor.GridMapping.CORNERS
						: CPathfindingProcessor.GridMapping.CELLS;
		this.target = new CPathPoint(followUnit.getX(), followUnit.getY());
		this.path = null;
		this.searchCycles = 0;
		this.followUnit = followUnit;
		this.firstUpdate = true;
		this.movedDistance = 0;
		this.pathfindingFailedGiveUp = false;
		this.giveUpUntilTurnTick = 0;
		this.followSearch = false;
		this.waitForWalkersTicks = 0;
		this.blockedNearTargetTicks = 0;
		this.searchAfterBlock = false;
		this.approachTarget = null;
	}

	@Override
	public int getHighlightOrderId() {
		return this.highlightOrderId;
	}

	/**
	 * The last leg of a path to a followed unit heads for the unit itself, which may have moved on. A Way Gate does
	 * not move and its last path point already lies by its edge: heading for its centre instead runs the unit into the
	 * gate's own pathing, and it stands there until it paths again (1.29 walks on into the gate without a stop).
	 */
	private boolean isLastLegToFollowedUnit() {
		return (this.followUnit != null) && (this.path.size() == 1)
				&& !((this.rangedBehavior instanceof CBehaviorMoveThroughWayGate) && this.followUnit.isBuilding());
	}

	@Override
	public CBehavior update(final CSimulation simulation) {
		if ((this.rangedBehavior != null) && (this.rangedBehavior.isWithinRange(simulation)
				|| isUnorderedChaseOfLostTarget(simulation))) {
			if (this.pathfindingActive) {
				simulation.removeFromPathfindingQueue(this);
				this.pathfindingActive = false;
			}
			return this.unit.finishMoveBehavior(simulation, this.rangedBehavior);
		}
		if (this.firstUpdate) {
			// when units start moving, if they're on top of other units, maybe push them to
			// the side
			this.unit.setPointAndCheckUnstuck(this.unit.getX(), this.unit.getY(), simulation);
			this.firstUpdate = false;
			// a unit about to walk off is no obstacle for the paths of the others ordered with it
			this.unit.markWalked(simulation.getGameTurnTick() + START_TICKS);
		}
		if (this.pathfindingFailedGiveUp) {
			onMoveGiveUp(simulation);
			return this.unit.pollNextOrderBehavior(simulation);
		}
		final float prevX = this.unit.getX();
		final float prevY = this.unit.getY();

		MovementType movementType = this.unit.getMovementType();
		if (movementType == null) {
			movementType = MovementType.DISABLED;
		}
		else if ((movementType == MovementType.FOOT) && this.disableCollision) {
			movementType = MovementType.FOOT_NO_COLLISION;
		}
		final PathingGrid pathingGrid = simulation.getPathingGrid();
		final CWorldCollision worldCollision = simulation.getWorldCollision();
		final float collisionSize = this.unit.getUnitType().getCollisionSize();
		final float startFloatingX = prevX;
		final float startFloatingY = prevY;
		if (this.approachTarget != null) {
			if ((this.rangedBehavior instanceof CBehaviorAttack) && (this.path == null) && !this.pathfindingActive) {
				aimAtApproachCell(simulation, this.approachTarget, pathingGrid, movementType, collisionSize);
			}
			this.approachTarget = null;
		}
		final boolean walkThrough = this.unit.isPathingDisabled() && (this.followUnit == null);
		if (this.path == null) {
			if (walkThrough) {
				// Measured on 1.29: a unit with its pathing turned off walks the straight line, through buildings and
				// off a blocked start.
				this.path = new ArrayList<>();
				this.path.add(new CPathPoint(this.target.x, this.target.y));
				this.pathReachesGoal = true;
			}
			else if (!this.pathfindingActive) {
				if (this.followUnit != null) {
					this.target.x = this.followUnit.getX();
					this.target.y = this.followUnit.getY();
				}
				simulation.findNaiveSlowPath(this.unit, this.followUnit, startFloatingX, startFloatingY, this.target,
						movementType, collisionSize, true, this);
				this.pathfindingActive = true;
				this.firstPathfindJob = true;
			}
		}
		else if ((this.followUnit != null) && (this.path.size() > 1) && (this.target.distance(this.followUnit.getX(),
				this.followUnit.getY()) > (0.1 * this.target.distance(this.unit.getX(), this.unit.getY())))) {
			this.target.x = this.followUnit.getX();
			this.target.y = this.followUnit.getY();
			if (this.pathfindingActive) {
				simulation.removeFromPathfindingQueue(this);
			}
			simulation.findNaiveSlowPath(this.unit, this.followUnit, startFloatingX, startFloatingY, this.target,
					movementType, collisionSize, this.searchCycles < 4, this);
			this.pathfindingActive = true;
			this.followSearch = true;
		}
		if ((this.path != null) && this.path.isEmpty() && !this.pathReachesGoal && !this.pathfindingActive) {
			return endOfPartialPath(simulation, movementType, collisionSize);
		}
		float currentTargetX;
		float currentTargetY;
		if ((this.path == null) || this.path.isEmpty()) {
			if (this.followUnit != null) {
				currentTargetX = this.followUnit.getX();
				currentTargetY = this.followUnit.getY();
			}
			else {
				currentTargetX = this.target.x;
				currentTargetY = this.target.y;
			}
		}
		else {
			if (isLastLegToFollowedUnit()) {
				currentTargetX = this.followUnit.getX();
				currentTargetY = this.followUnit.getY();
			}
			else {
				final CPathPoint nextPathElement = this.path.get(0);
				currentTargetX = nextPathElement.x;
				currentTargetY = nextPathElement.y;
			}
		}

		float deltaX = currentTargetX - prevX;
		float deltaY = currentTargetY - prevY;
		double goalAngleRad = StrictMath.atan2(deltaY, deltaX);
		float goalAngle = (float) StrictMath.toDegrees(goalAngleRad);
		if (goalAngle < 0) {
			goalAngle += 360;
		}
		float facing = this.unit.getFacing();
		float delta = goalAngle - facing;
		final float propulsionWindow = this.unit.getPropWindow();
		final int speed = this.group == null ? this.unit.getSpeed()
				: this.group.limitSpeed(this.unit, this.unit.getSpeed());
		final boolean speedChange = speed != prevSpeed;
		this.prevSpeed = speed;

		if (delta < -180) {
			delta = 360 + delta;
		}
		if (delta > 180) {
			delta = -360 + delta;
		}
		float absDelta = Math.abs(delta);
		final float turnStep = getTurnStep(this.unit);
		// part of this tick left for walking once the heading comes inside the window
		final float walkFraction;
		if (absDelta < propulsionWindow) {
			walkFraction = 1;
		}
		else if ((turnStep > 0) && ((absDelta - turnStep) < propulsionWindow)) {
			walkFraction = 1 - ((absDelta - propulsionWindow) / turnStep);
		}
		else {
			walkFraction = 0;
		}

		if ((absDelta <= Math.max(1.0f, turnStep)) && (absDelta != 0)) {
			this.unit.setFacing(goalAngle);
		}
		else if (absDelta != 0) {
			facing += Math.signum(delta) * turnStep;
			this.unit.setFacing(facing);
		}
		final boolean blockedByGiveUpUntilTickDelay = simulation.getGameTurnTick() < this.giveUpUntilTurnTick;
		// While a first path or a new path toward a moving target is searched, the unit keeps walking toward the target
		// or along its old path (1.29 starts walking at once; the search takes a tick).
		final boolean walkWhileSearching = this.pathfindingActive
				&& ((this.path == null) ? this.firstPathfindJob : (this.followSearch && !this.path.isEmpty()));
		if (!blockedByGiveUpUntilTickDelay && ((this.path != null) || walkWhileSearching)
				&& (!this.pathfindingActive || walkWhileSearching) && (walkFraction > 0)) {
			final float speedTick = speed * WarsmashConstants.SIMULATION_STEP_TIME * walkFraction;
			double continueDistance = speedTick;
			float walkX = prevX;
			float walkY = prevY;
			do {
				boolean done;
				boolean endsShort = false;
				float nextX, nextY;
				deltaX = currentTargetX - walkX;
				deltaY = currentTargetY - walkY;
				final double travelDistance = Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
				if ((this.path == null ? !walkThrough : ((this.path.size() == 1) && this.pathReachesGoal))
						&& (this.followUnit == null)
						&& ((this.rangedBehavior == null) || (this.rangedBehavior instanceof CBehaviorAttackMove))
						&& (this.group == null) && (travelDistance > 0)
						&& (travelDistance <= (continueDistance + MOVE_END_DISTANCE))) {
					final double stepDistance = speed * MOVE_END_STEP_TIME;
					final double wholeWay = this.movedDistance + travelDistance;
					final double walked = (travelDistance > MOVE_END_DISTANCE) && (stepDistance > 0)
							? Math.max(0, Math.min(travelDistance, (Math.ceil((wholeWay - MOVE_END_DISTANCE) / stepDistance)
									* stepDistance) - this.movedDistance))
							: 0;
					nextX = walkX + (float) ((deltaX / travelDistance) * walked);
					nextY = walkY + (float) ((deltaY / travelDistance) * walked);
					continueDistance = 0;
					done = true;
					endsShort = true;
				}
				else if (travelDistance <= continueDistance) {
					nextX = currentTargetX;
					nextY = currentTargetY;
					continueDistance = continueDistance - travelDistance;
					done = true;
				}
				else {
					nextX = walkX + (float) ((deltaX / travelDistance) * continueDistance);
					nextY = walkY + (float) ((deltaY / travelDistance) * continueDistance);
					continueDistance = 0;
//					done = (this.gridMapping.getX(pathingGrid, nextX) == this.gridMapping.getX(pathingGrid,
//							currentTargetX))
//							&& (this.gridMapping.getY(pathingGrid, nextY) == this.gridMapping.getY(pathingGrid,
//									currentTargetY));
					done = false;
				}
				if (this.unit.getCollisionRectangle() != null) {
					tempRect.set(this.unit.getCollisionRectangle());
				}
				else {
					tempRect.set(nextX, nextY, 0, 0);
				}
				tempRect.setCenter(nextX, nextY);
				if (walkThrough || (movementType == null) || (pathingGrid.isPathableFrom(walkX, walkY, nextX, nextY, movementType,
						collisionSize)
						&& !worldCollision.intersectsAnythingOtherThan(tempRect, this.unit, movementType, false))) {
					this.movedDistance += StrictMath.hypot(nextX - walkX, nextY - walkY);
					this.unit.setPoint(nextX, nextY, worldCollision, simulation.getRegionManager());
					this.unit.markWalked(simulation.getGameTurnTick());
					this.waitForWalkersTicks = 0;
					this.blockedNearTargetTicks = 0;
					walkX = nextX;
					walkY = nextY;
					if (endsShort) {
						if (this.pathfindingActive) {
							simulation.removeFromPathfindingQueue(this);
							this.pathfindingActive = false;
						}
						onMoveGiveUp(simulation);
						return this.unit.pollNextOrderBehavior(simulation);
					}
					if (done) {
						// if we're making headway along the path then it's OK to start thinking fast
						// again
						if (travelDistance > 0) {
							this.searchCycles = 0;
						}
						if (this.path == null) {
							// reached the target before the first search finished
							simulation.removeFromPathfindingQueue(this);
							this.pathfindingActive = false;
							onMoveGiveUp(simulation);
							return this.unit.pollNextOrderBehavior(simulation);
						}
						if (this.path.isEmpty()) {
							if (!this.pathReachesGoal) {
								return endOfPartialPath(simulation, movementType, collisionSize);
							}
							onMoveGiveUp(simulation);
							return this.unit.pollNextOrderBehavior(simulation);
						}
						else {
							this.path.remove(0);
							final boolean emptyPath = this.path.isEmpty();
							if (emptyPath && !this.pathReachesGoal) {
								return endOfPartialPath(simulation, movementType, collisionSize);
							}
							if (emptyPath) {
								if (this.followUnit != null) {
									currentTargetX = this.followUnit.getX();
									currentTargetY = this.followUnit.getY();
								}
								else {
									currentTargetX = this.target.x;
									currentTargetY = this.target.y;
								}
							}
							else {
								if (isLastLegToFollowedUnit()) {
									currentTargetX = this.followUnit.getX();
									currentTargetY = this.followUnit.getY();
								}
								else {
									final CPathPoint firstPathElement = this.path.get(0);
									currentTargetX = firstPathElement.x;
									currentTargetY = firstPathElement.y;
								}
							}
							deltaY = currentTargetY - nextY;
							deltaX = currentTargetX - nextX;
							if ((deltaX == 0.000f) && (deltaY == 0.000f) && this.path.isEmpty()) {
								onMoveGiveUp(simulation);
								return this.unit.pollNextOrderBehavior(simulation);
							}
							goalAngleRad = StrictMath.atan2(deltaY, deltaX);
							goalAngle = (float) StrictMath.toDegrees(goalAngleRad);
							if (goalAngle < 0) {
								goalAngle += 360;
							}
							facing = this.unit.getFacing();
							delta = goalAngle - facing;

							if (delta < -180) {
								delta = 360 + delta;
							}
							if (delta > 180) {
								delta = -360 + delta;
							}
							absDelta = Math.abs(delta);
							if (absDelta >= propulsionWindow) {
								if (this.wasWithinPropWindow) {
									playStandAnimation();
								}
								this.wasWithinPropWindow = false;
								return this;
							}
						}
					}
				}
				else {
					// As in 1.29, a unit stopped by group-mates walking across its way waits for them instead of
					// searching a way around units that will have moved on.
					if ((this.waitForWalkersTicks < WAIT_FOR_WALKERS_TICKS)
							&& pathingGrid.isPathable(nextX, nextY, movementType, collisionSize)
							&& !worldCollision.intersectsStandingUnitOtherThan(tempRect, this.unit, null, movementType,
									simulation.getGameTurnTick())) {
						this.waitForWalkersTicks++;
						break;
					}
					this.waitForWalkersTicks = 0;
					if (this.followUnit != null) {
						this.target.x = this.followUnit.getX();
						this.target.y = this.followUnit.getY();
						// Measured on 1.29: a unit following a target it was ordered to attack but cannot hit (a
						// Paladin on a flyer) stops once it stands blocked next to it, as a move to a taken point
						// does.
						if ((this.rangedBehavior == null) && (this.highlightOrderId == OrderIds.attack)
								&& (this.unit.distance(this.target.x, this.target.y) <= Math.max(128f,
										collisionSize * 4))) {
							onMoveGiveUp(simulation);
							return this.unit.pollNextOrderBehavior(simulation);
						}
					}
					// A point taken by other units is never reached; arriving next to it ends the move, otherwise
					// the unit searches a path every tick and starves the pathfinding queue of other units.
					else if (((this.rangedBehavior == null) || (this.rangedBehavior instanceof CBehaviorAttackMove))
							&& (this.unit.distance(this.target.x, this.target.y) <= Math.max(128f, collisionSize * 4))) {
						if ((this.rangedBehavior != null) && (this.blockedNearTargetTicks < BLOCKED_NEAR_TARGET_TICKS)) {
							this.blockedNearTargetTicks++;
							break;
						}
						onMoveGiveUp(simulation);
						return this.unit.pollNextOrderBehavior(simulation);
					}
					if (!this.pathfindingActive) {
						if ((this.followUnit == null) && (this.rangedBehavior instanceof CBehaviorAttack)
								&& (this.rangedBehavior.getTarget() instanceof CDestructable)) {
							aimAtApproachCell(simulation, (CDestructable) this.rangedBehavior.getTarget(), pathingGrid,
									movementType, collisionSize);
						}
						simulation.findNaiveSlowPath(this.unit, this.followUnit, startFloatingX, startFloatingY,
								this.target, movementType, collisionSize, this.searchCycles < 4, this);
						this.pathfindingActive = true;
						this.searchAfterBlock = true;
						// The way the unit was on is the way around the terrain; only what the units add to it counts as
						// a detour.
						this.pathLengthWhenBlocked = (this.path == null) ? 0 : pathLength(this.path);
						this.searchCycles++;
						return this;
					}
				}
				if (this.unit.isScriptAnimationHeld()) {
					this.unit.getUnitAnimationListener().replayEndedAnimation();
				}
				else if (speed > 0) {
					this.unit.getUnitAnimationListener().playWalkAnimation(speedChange, speed, true);
				}
				else {
					playStandAnimation();
				}
				this.wasWithinPropWindow = true;
			}
			while (continueDistance > 0);
		}
		else {
			// If this happens, the unit is facing the wrong way, and has to turn before
			// moving.
			if (this.wasWithinPropWindow) {
				playStandAnimation();
			}
			this.wasWithinPropWindow = false;
		}

		return this;
	}

	private void playStandAnimation() {
		if (this.unit.isScriptAnimationHeld()) {
			this.unit.getUnitAnimationListener().replayEndedAnimation();
		}
		else {
			this.unit.getUnitAnimationListener().playAnimation(false, PrimaryTag.STAND, SequenceUtils.EMPTY, 1.0f,
					true);
		}
	}

	/**
	 * Measured on 1.29: a unit chasing a target it picked itself (acquired, attacked back)
	 * drops the chase as soon as the target dies or is removed, while one ordered onto the target walks on to where it
	 * was and stops there.
	 */
	private boolean isUnorderedChaseOfLostTarget(final CSimulation simulation) {
		if (!(this.rangedBehavior instanceof CBehaviorAttack)) {
			return false;
		}
		final COrder order = this.unit.getCurrentOrder();
		if (order instanceof COrderTargetWidget) {
			return false;
		}
		return !((CBehaviorAttack) this.rangedBehavior).checkTargetStillValid(simulation);
	}

	/**
	 * Measured on 1.29: a unit ordered to attack a destructable whose middle cannot be reached (a gate standing in a
	 * forest) walks to the free spot next to the destructable that is nearest by its way there, and attacks from
	 * there; it does not walk to the free spot nearest the middle and give up. The footprint is the destructable's
	 * pathing map as the attack range check sees it. A group spreads along the destructable: a spot where another
	 * unit already stands is passed over, and a unit stopped by the others on its way picks the next free spot.
	 */
	private void aimAtApproachCell(final CSimulation simulation, final CDestructable dest,
			final PathingGrid pathingGrid, final MovementType movementType, final float collisionSize) {
		if (pathingGrid.isPathable(dest.getX(), dest.getY(), movementType, collisionSize)) {
			return;
		}
		final ArgbImage pathingMap = dest.isDead() ? dest.getDestType().getPathingDeathPixelMap()
				: dest.getDestType().getPathingPixelMap();
		if (pathingMap == null) {
			return;
		}
		final boolean turned = (((dest.getPathingRotation() + 450) % 360) % 180) != 0;
		final float halfWidth = (turned ? pathingMap.getHeight() : pathingMap.getWidth()) * 16f;
		final float halfHeight = (turned ? pathingMap.getWidth() : pathingMap.getHeight()) * 16f;
		final float reach = collisionSize + 32f;
		final int width = pathingGrid.getWidth();
		final int height = pathingGrid.getHeight();
		final int startX = pathingGrid.getCellX(this.unit.getX());
		final int startY = pathingGrid.getCellY(this.unit.getY());
		if ((startX < 0) || (startY < 0) || (startX >= width) || (startY >= height)) {
			return;
		}
		final BitSet seen = new BitSet(width * height);
		final int[] queue = new int[APPROACH_SEARCH_CELLS];
		int head = 0;
		int tail = 0;
		queue[tail++] = (startY * width) + startX;
		seen.set((startY * width) + startX);
		while (head < tail) {
			final int cell = queue[head++];
			final int cellX = cell % width;
			final int cellY = cell / width;
			final float worldX = pathingGrid.getWorldX(cellX);
			final float worldY = pathingGrid.getWorldY(cellY);
			final float outX = Math.max(0f, Math.abs(worldX - dest.getX()) - halfWidth);
			final float outY = Math.max(0f, Math.abs(worldY - dest.getY()) - halfHeight);
			if ((((outX * outX) + (outY * outY)) <= (reach * reach))
					&& !isSpotTaken(simulation, worldX, worldY, movementType, collisionSize)) {
				this.target = new CPathPoint(worldX, worldY);
				return;
			}
			for (int direction = 0; direction < 4; direction++) {
				final int nextX = cellX + ((direction == 0) ? 1 : (direction == 1) ? -1 : 0);
				final int nextY = cellY + ((direction == 2) ? 1 : (direction == 3) ? -1 : 0);
				if ((nextX < 0) || (nextY < 0) || (nextX >= width) || (nextY >= height)) {
					continue;
				}
				final int next = (nextY * width) + nextX;
				if (seen.get(next)) {
					continue;
				}
				seen.set(next);
				if ((tail < queue.length) && pathingGrid.isPathable(pathingGrid.getWorldX(nextX),
						pathingGrid.getWorldY(nextY), movementType, collisionSize)) {
					queue[tail++] = next;
				}
			}
		}
	}

	private boolean isSpotTaken(final CSimulation simulation, final float x, final float y,
			final MovementType movementType, final float collisionSize) {
		tempRect.set(x - collisionSize, y - collisionSize, collisionSize * 2, collisionSize * 2);
		return simulation.getWorldCollision().intersectsStandingUnitOtherThan(tempRect, this.unit, null, movementType,
				simulation.getGameTurnTick());
	}

	private void onMoveGiveUp(final CSimulation simulation) {
		// An attack-move whose point cannot be reached ends where the walk ended (1.29), instead of starting again
		// as the unit's default behaviour.
		if (this.rangedBehavior instanceof CBehaviorAttackMove) {
			this.unit.setDefaultBehavior(this.unit.getStopBehavior());
		}
		if (this.rangedBehavior != null) {
			this.rangedBehavior.endMove(simulation, true);
		}
	}

	private final class TargetVisitingResetter implements AbilityTargetVisitor<Void> {
		private int highlightOrderId;

		private TargetVisitingResetter reset(final int highlightOrderId) {
			this.highlightOrderId = highlightOrderId;
			return this;
		}

		@Override
		public Void accept(final AbilityPointTarget target) {
			internalResetMove(this.highlightOrderId, target.x, target.y);
			return null;
		}

		@Override
		public Void accept(final CUnit target) {
			internalResetMove(this.highlightOrderId, target);
			return null;
		}

		@Override
		public Void accept(final CDestructable target) {
			internalResetMove(this.highlightOrderId, target.getX(), target.getY());
			CBehaviorMove.this.approachTarget = target;
			return null;
		}

		@Override
		public Void accept(final CItem target) {
			internalResetMove(this.highlightOrderId, target.getX(), target.getY());
			return null;
		}
	}

	@Override
	public void begin(final CSimulation game) {
		this.takenOver = false;
		if (this.disableCollision) {
			this.unit.setNoCollisionMovementType(true);
		}
	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {
		if (this.disableCollision) {
			this.unit.setNoCollisionMovementType(false);
		}
		if (ALWAYS_INTERRUPT_MOVE) {
			game.removeFromPathfindingQueue(this);
			this.pathfindingActive = false;
		}
		if ((this.rangedBehavior != null) && !this.takenOver) {
			this.rangedBehavior.endMove(game, interrupted);
		}
		this.takenOver = false;
	}

	public CUnit getUnit() {
		return this.unit;
	}

	public CRangedBehavior getRangedBehavior() {
		return this.rangedBehavior;
	}

	/**
	 * A search started for an earlier target (the order was replaced while it ran) brings nothing: the unit asks for
	 * a path to its current target on its next update. Measured on 1.29: a new move order given right after a long
	 * one goes straight to the new point.
	 */
	public void pathFound(final CPathPoint searchedGoal, final List<CPathPoint> waypoints, final boolean reachesGoal,
			final CSimulation simulation) {
		if (searchedGoal != this.target) {
			this.pathfindingActive = false;
			this.followSearch = false;
			return;
		}
		pathFound(waypoints, reachesGoal, simulation);
	}

	public void pathFound(final List<CPathPoint> waypoints, final boolean reachesGoal,
			final CSimulation simulation) {
		this.pathfindingActive = false;
		this.followSearch = false;
		this.path = waypoints;
		this.pathReachesGoal = reachesGoal;
		final boolean searchedAfterBlock = this.searchAfterBlock;
		this.searchAfterBlock = false;
		if (searchedAfterBlock && isPlainMove()
				&& (this.unit.distance(this.target.x, this.target.y) <= GIVE_UP_NEAR_TARGET) && !waypoints.isEmpty()
				&& (pathLength(waypoints) > ((2 * Math.max(this.pathLengthWhenBlocked,
						waypoints.get(waypoints.size() - 1).distance(this.unit.getX(), this.unit.getY())))
						+ MAX_DETOUR_AROUND_UNITS))) {
			// As in 1.29, a unit stopped by standing units on its way to a taken point stops near it instead of
			// walking far around them.
			this.pathfindingFailedGiveUp = true;
		}
		this.pathStartDistance = this.unit.distance(this.target.x, this.target.y);
		if (this.firstPathfindJob) {
			this.firstPathfindJob = false;
		}
		else if (this.searchCycles > 6) {
			if (this.searchCycles > 9) {
				this.pathfindingFailedGiveUp = true;
			}
			else {
				this.giveUpUntilTurnTick = simulation.getGameTurnTick()
						+ CTimer.wholeSteps(5);
			}
		}
	}

	private boolean isPlainMove() {
		return (this.followUnit == null)
				&& ((this.rangedBehavior == null) || (this.rangedBehavior instanceof CBehaviorAttackMove));
	}

	private double pathLength(final List<CPathPoint> waypoints) {
		double length = 0;
		float x = this.unit.getX();
		float y = this.unit.getY();
		for (final CPathPoint point : waypoints) {
			length += point.distance(x, y);
			x = point.x;
			y = point.y;
		}
		return length;
	}

	/**
	 * Measured: a unit whose target cannot be reached walks to the reachable point nearest to it and stops. A search
	 * cut short by its iteration limit also ends at such a point, so the unit searches again from there while that
	 * brought it closer. A unit on its way to act on something (attack, harvest, build) heads on toward it instead,
	 * since its own range decides when it has arrived.
	 */
	private CBehavior endOfPartialPath(final CSimulation simulation, final MovementType movementType,
			final float collisionSize) {
		final boolean plainMove = (this.rangedBehavior == null)
				|| (this.rangedBehavior instanceof CBehaviorAttackMove);
		if (!plainMove) {
			if (this.rangedBehavior instanceof CBehaviorWispHarvest) {
				final CBehavior next = ((CBehaviorWispHarvest) this.rangedBehavior).onPathEnd(simulation);
				if (next != null) {
					return next;
				}
			}
			this.pathReachesGoal = true;
			return this;
		}
		final double distance = this.unit.distance(this.target.x, this.target.y);
		if ((distance > (this.pathStartDistance - 64)) || (this.searchCycles > 9)) {
			onMoveGiveUp(simulation);
			return this.unit.pollNextOrderBehavior(simulation);
		}
		if (this.followUnit != null) {
			this.target.x = this.followUnit.getX();
			this.target.y = this.followUnit.getY();
		}
		simulation.findNaiveSlowPath(this.unit, this.followUnit, this.unit.getX(), this.unit.getY(), this.target,
				movementType, collisionSize, true, this);
		this.pathfindingActive = true;
		this.searchCycles++;
		return this;
	}

	public boolean isPathfindingActive() {
		return this.pathfindingActive;
	}

	@Override
	public boolean interruptable() {
		return true;
	}

	@Override
	public <T> T visit(final CBehaviorVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public CBehaviorCategory getBehaviorCategory() {
		return CBehaviorCategory.MOVEMENT;
	}
	
	public AbilityTarget getTarget() {
		if (this.rangedBehavior != null) {
			return this.rangedBehavior.getTarget();
		} else if (this.followUnit != null) {
			return this.followUnit;
		}
		return new AbilityPointTarget(this.target.x, this.target.y);
	}
}
