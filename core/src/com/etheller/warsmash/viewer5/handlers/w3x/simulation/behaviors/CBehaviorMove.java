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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.ABBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.harvest.CBehaviorWispHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CPathPoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

public class CBehaviorMove implements CBehavior {
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
	private static final int WALK_KEEP_WAIT_TICKS = 2;
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
	private static final int UNREACHABLE_BUILDING_SEARCHES = 3;
	/**
	 * Measured on 1.29: a group order over a long way takes its lanes within about a thousand units and then walks one
	 * path side by side, instead of every unit finding its own way to its own far slot.
	 */
	static final float SHARED_ROUTE_MIN_DISTANCE = 1350f;
	private static final float CARRY_WAY_MAX_SHIFT = 1024f;
	/** The walk to a standing attack target is only a grid row from this distance on. */
	private static final float ROW_PATH_MIN_DISTANCE = 256f;
	private static final float LANE_FORM_DISTANCE = 1000f;

	private static final Rectangle tempRect = new Rectangle();
	private static final int NOT_A_TICK = Integer.MIN_VALUE;
	private final CUnit unit;
	private int highlightOrderId;
	private final TargetVisitingResetter targetVisitingResetter;

	public CBehaviorMove(final CUnit unit) {
		this.unit = unit;
		this.targetVisitingResetter = new TargetVisitingResetter();
	}

	private boolean wasWithinPropWindow = false;
	private List<CPathPoint> path = null;
	private CPathPoint target;
	private CPathPoint approachCell;
	private int searchCycles = 0;
	private CUnit followUnit;
	private CRangedBehavior rangedBehavior;
	private boolean firstUpdate = true;
	private double movedDistance;
	private boolean disableCollision = false;
	private boolean pathfindingActive = false;
	/**
	 * The search still queued was asked for the order this one replaced. Left in the queue it would run to its end
	 * before the new order's search could even start, the unit standing the while.
	 */
	private boolean staleSearch;
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
	private final int[] approachWindow = new int[4];
	private int resetCount;
	private boolean takenOver;
	private int blockedNearTargetTicks;
	private float laneX;
	private float laneY;
	private CPathPoint sharedGoal;
	private boolean sharedRouteOff;
	/**
	 * The walk goes on along the way found for the point this order replaced until the search for the new point
	 * brings its own.
	 */
	private boolean replacingPath;
	private boolean carryPath;

	public CBehaviorMove reset(final int highlightOrderId, final AbilityTarget target) {
		if (!continuesSameMove(highlightOrderId, target)) {
			this.carryPath = continuesWayToNearbyPoint(highlightOrderId, target);
			target.visit(this.targetVisitingResetter.reset(highlightOrderId));
			this.carryPath = false;
		}
		this.rangedBehavior = null;
		this.group = null;
		this.laneX = 0;
		this.laneY = 0;
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
	 * Measured on 1.29: a crowd re-ordered to points a little apart (a player clicking on) keeps walking along its
	 * way, where here the search for each new point takes its turn in the queue and a unit with no way walks straight
	 * at the point, into the trees on its side. The way found for the earlier point still leads the right way, so the
	 * unit walks it until the new one is found.
	 */
	private boolean continuesWayToNearbyPoint(final int highlightOrderId, final AbilityTarget target) {
		if (!(target instanceof AbilityPointTarget) || (this.unit.getCurrentBehavior() != this)
				|| (this.rangedBehavior != null) || (this.followUnit != null) || (this.target == null)
				|| (highlightOrderId != this.highlightOrderId) || this.pathfindingFailedGiveUp || (this.path == null)
				|| (this.path.size() < 2) || !this.pathReachesGoal || this.unit.isPathingDisabled()) {
			return false;
		}
		final AbilityPointTarget point = (AbilityPointTarget) target;
		return StrictMath.hypot(point.x - this.target.x, point.y - this.target.y) <= CARRY_WAY_MAX_SHIFT;
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

	/**
	 * Copies the next points of the way being walked, nearest first, as x/y pairs into out starting at offset; returns
	 * how many points were copied. Only reads.
	 */
	public int copyPathPoints(final float[] out, final int offset, final int maxPoints) {
		final List<CPathPoint> points = this.path;
		if (points == null) {
			return 0;
		}
		final int count = Math.min(maxPoints, points.size());
		for (int i = 0; i < count; i++) {
			final CPathPoint point = points.get(i);
			out[offset + (i * 2)] = point.x;
			out[offset + (i * 2) + 1] = point.y;
		}
		return count;
	}

	public CBehaviorMove reset(final AbilityTarget target, final CRangedBehavior rangedBehavior,
			final boolean disableCollision) {
		final int highlightOrderId = rangedBehavior.getHighlightOrderId();
		target.visit(this.targetVisitingResetter.reset(highlightOrderId));
		this.rangedBehavior = rangedBehavior;
		this.group = null;
		this.laneX = 0;
		this.laneY = 0;
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

	/** The point this walk heads for, when it is a plain walk to a point (a slot of a group order). */
	CPathPoint getPointGoal() {
		return walksToPoint() ? this.target : null;
	}

	/** A walk to a point, or an attack-move to one: the walk of the latter is the same until an enemy turns up. */
	private boolean walksToPoint() {
		return (this.followUnit == null)
				&& ((this.rangedBehavior == null) || (this.rangedBehavior instanceof CBehaviorAttackMove));
	}

	/** Where the unit's slot lies from the point its group was sent to. */
	void setLane(final float laneX, final float laneY) {
		this.laneX = laneX;
		this.laneY = laneY;
	}

	/**
	 * The goal a path is searched for: a unit of a group walking a long way takes the group's point, so that the
	 * units share their way and step aside into their lanes along it.
	 */
	private CPathPoint searchGoal() {
		if ((this.group == null) || this.sharedRouteOff || !walksToPoint()) {
			return this.target;
		}
		if (this.sharedGoal == null) {
			if (this.unit.distance(this.target.x, this.target.y) < SHARED_ROUTE_MIN_DISTANCE) {
				this.sharedRouteOff = true;
				return this.target;
			}
			this.sharedGoal = new CPathPoint(this.target.x - this.laneX, this.target.y - this.laneY);
		}
		return this.sharedGoal;
	}

	/**
	 * Moves the waypoints of the path to the group's point over to the unit's lane: the lane is taken up over the first
	 * thousand units of the way, and the path ends at the unit's own slot. Points the lane would put into terrain stay
	 * on the shared path. A path that stops short of the group's point (a search cut off by its limit, or a point that
	 * lies in terrain) is shifted the same way and ends on its lane; the unit searches on from there. Returns null when
	 * the slot itself cannot be walked to.
	 */
	private List<CPathPoint> shiftToLane(final List<CPathPoint> waypoints, final boolean reachesGoal,
			final CSimulation simulation) {
		final PathingGrid pathingGrid = simulation.getPathingGrid();
		MovementType movementType = this.unit.getMovementType();
		if (movementType == null) {
			movementType = MovementType.DISABLED;
		}
		final float collisionSize = this.unit.getUnitType().getCollisionSize();
		final List<CPathPoint> shifted = new ArrayList<>(waypoints.size() + 1);
		float rawX = this.unit.getX();
		float rawY = this.unit.getY();
		float fromX = rawX;
		float fromY = rawY;
		float walked = 0;
		for (int i = 0; i < waypoints.size(); i++) {
			final CPathPoint point = waypoints.get(i);
			final boolean last = (i == (waypoints.size() - 1)) && reachesGoal;
			final float segment = (float) StrictMath.hypot(point.x - rawX, point.y - rawY);
			if (!last && (walked < LANE_FORM_DISTANCE) && ((walked + segment) > LANE_FORM_DISTANCE)) {
				final float t = (LANE_FORM_DISTANCE - walked) / segment;
				final float laneStartX = rawX + ((point.x - rawX) * t) + this.laneX;
				final float laneStartY = rawY + ((point.y - rawY) * t) + this.laneY;
				if (pathingGrid.isPathable(laneStartX, laneStartY, movementType, collisionSize)
						&& pathingGrid.isPathableFrom(fromX, fromY, laneStartX, laneStartY, movementType, collisionSize)) {
					shifted.add(new CPathPoint(laneStartX, laneStartY));
					fromX = laneStartX;
					fromY = laneStartY;
				}
			}
			walked += segment;
			rawX = point.x;
			rawY = point.y;
			if (last) {
				final float endX = point.x + this.laneX;
				final float endY = point.y + this.laneY;
				if (!pathingGrid.isPathable(endX, endY, movementType, collisionSize)) {
					return null;
				}
				shifted.add(new CPathPoint(this.target.x, this.target.y));
				break;
			}
			final float formed = Math.min(1f, walked / LANE_FORM_DISTANCE);
			float chosenX = point.x;
			float chosenY = point.y;
			for (final float scale : new float[] { formed, formed / 2 }) {
				final float candidateX = point.x + (this.laneX * scale);
				final float candidateY = point.y + (this.laneY * scale);
				if (pathingGrid.isPathable(candidateX, candidateY, movementType, collisionSize) && pathingGrid
						.isPathableFrom(fromX, fromY, candidateX, candidateY, movementType, collisionSize)) {
					chosenX = candidateX;
					chosenY = candidateY;
					break;
				}
			}
			shifted.add(new CPathPoint(chosenX, chosenY));
			fromX = chosenX;
			fromY = chosenY;
		}
		return shifted;
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
		this.staleSearch = this.pathfindingActive;
		this.target = new CPathPoint(targetX, targetY);
		this.sharedGoal = null;
		this.sharedRouteOff = false;
		this.replacingPath = this.carryPath && (this.path != null);
		if (!this.replacingPath) {
			this.path = null;
		}
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
		this.staleSearch = this.pathfindingActive;
		this.target = new CPathPoint(followUnit.getX(), followUnit.getY());
		this.sharedGoal = null;
		this.sharedRouteOff = false;
		this.replacingPath = false;
		this.path = null;
		this.searchCycles = 0;
		this.followUnit = followUnit;
		this.approachCell = null;
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
	 * gate's own pathing, and it stands there until it paths again (1.29 walks on into the gate without a stop). The
	 * same holds for any building when the path ends short of it: the last point is the free spot nearest to it, and a
	 * unit that stands by a corner of the building would only be sent into its wall.
	 */
	private boolean isLastLegToFollowedUnit() {
		return (this.followUnit != null) && (this.path.size() == 1)
				&& (this.pathReachesGoal || !this.followUnit.isBuilding())
				&& !((this.approachCell != null) && (this.path.get(0) == this.approachCell))
				&& !((this.rangedBehavior instanceof CBehaviorMoveThroughWayGate) && this.followUnit.isBuilding());
	}

	@Override
	public CBehavior update(final CSimulation simulation) {
		if (this.staleSearch) {
			this.staleSearch = false;
			if (this.pathfindingActive) {
				// withdrawn, the search asked below takes its place in the queue
				simulation.removeFromPathfindingQueue(this);
				this.pathfindingActive = false;
			}
		}
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
				simulation.findNaiveSlowPath(this.unit, this.followUnit, startFloatingX, startFloatingY, searchGoal(),
						movementType, collisionSize, smoothsPath(simulation), this);
				this.pathfindingActive = true;
				this.firstPathfindJob = true;
			}
		}
		else if (this.replacingPath && !this.pathfindingActive) {
			simulation.findNaiveSlowPath(this.unit, null, startFloatingX, startFloatingY, searchGoal(), movementType,
					collisionSize, smoothsPath(simulation), this);
			this.pathfindingActive = true;
			this.followSearch = true;
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
						&& (this.group == null) && !this.replacingPath && (travelDistance > 0)
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
						&& !blockedByUnit(worldCollision, pathingGrid, nextX, nextY, movementType, collisionSize,
								NOT_A_TICK))) {
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
						else if (this.replacingPath && (this.path.size() == 1)) {
							// the way found for the earlier point is walked to its end: the unit waits for the new one
							standWhileBlocked();
							break;
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
							&& !blockedByUnit(worldCollision, pathingGrid, nextX, nextY, movementType, collisionSize,
									simulation.getGameTurnTick())) {
						this.waitForWalkersTicks++;
						// a tick or two of waiting between steps keeps the walk; a longer wait stands
						if (this.waitForWalkersTicks > WALK_KEEP_WAIT_TICKS) {
							standWhileBlocked();
						}
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
							standWhileBlocked();
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
								this.target, movementType, collisionSize, smoothsPath(simulation) && (this.searchCycles < 4),
								this);
						this.pathfindingActive = true;
						this.searchAfterBlock = true;
						// The way the unit was on is the way around the terrain; only what the units add to it counts as
						// a detour.
						this.pathLengthWhenBlocked = (this.path == null) ? 0 : pathLength(this.path);
						this.searchCycles++;
						standWhileBlocked();
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

	/** A unit that is held up does not walk on the spot: it stands until it moves again. */
	private void standWhileBlocked() {
		if (this.wasWithinPropWindow) {
			playStandAnimation();
		}
		this.wasWithinPropWindow = false;
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
		final float reach = collisionSize + 32f;
		final boolean[] nearFootprint = approachFootprint(pathingGrid, dest, pathingMap, reach);
		final int[] nearWindow = this.approachWindow;
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
			final int windowX = cellX - nearWindow[0];
			final int windowY = cellY - nearWindow[1];
			if ((windowX >= 0) && (windowY >= 0) && (windowX < nearWindow[2]) && (windowY < nearWindow[3])
					&& nearFootprint[(windowY * nearWindow[2]) + windowX]
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

	/**
	 * The cells within reach of the destructable's pathing as the grid holds it (a diagonal gate is a band of cells,
	 * not the box around it), in a window of the grid whose origin and size are left in approachWindow.
	 */
	private boolean[] approachFootprint(final PathingGrid pathingGrid, final CDestructable dest,
			final ArgbImage pathingMap, final float reach) {
		final int rotation = (dest.getPathingRotation() + 450) % 360;
		final boolean turned = (rotation % 180) != 0;
		final int divW = turned ? pathingMap.getHeight() : pathingMap.getWidth();
		final int divH = turned ? pathingMap.getWidth() : pathingMap.getHeight();
		final int originX = pathingGrid.getCellX(dest.getX()) - (divW / 2);
		final int originY = pathingGrid.getCellY(dest.getY()) - (divH / 2);
		final int margin = (int) Math.ceil(reach / 32f) + 1;
		final int windowWidth = divW + (2 * margin);
		final int windowHeight = divH + (2 * margin);
		this.approachWindow[0] = originX - margin;
		this.approachWindow[1] = originY - margin;
		this.approachWindow[2] = windowWidth;
		this.approachWindow[3] = windowHeight;
		final boolean[] near = new boolean[windowWidth * windowHeight];
		final float reachSquare = reach * reach;
		for (int j = 0; j < pathingMap.getHeight(); j++) {
			for (int i = 0; i < pathingMap.getWidth(); i++) {
				if (((pathingMap.getRGB(i, pathingMap.getHeight() - 1 - j) & 0xFF0000) >>> 16) <= 127) {
					continue;
				}
				// the same layout PathingGrid.blitPathingOverlayTexture gives the texture
				final int x;
				final int y;
				switch (rotation) {
				case 90:
					x = pathingMap.getHeight() - 1 - j;
					y = i;
					break;
				case 180:
					x = pathingMap.getWidth() - 1 - i;
					y = pathingMap.getHeight() - 1 - j;
					break;
				case 270:
					x = j;
					y = pathingMap.getWidth() - 1 - i;
					break;
				default:
					x = i;
					y = j;
				}
				for (int offsetY = -margin; offsetY <= margin; offsetY++) {
					final float outY = Math.max(0f, (Math.abs(offsetY) * 32f) - 16f);
					for (int offsetX = -margin; offsetX <= margin; offsetX++) {
						final float outX = Math.max(0f, (Math.abs(offsetX) * 32f) - 16f);
						if (((outX * outX) + (outY * outY)) <= reachSquare) {
							near[((y + offsetY + margin) * windowWidth) + x + offsetX + margin] = true;
						}
					}
				}
			}
		}
		return near;
	}

	private boolean isSpotTaken(final CSimulation simulation, final float x, final float y,
			final MovementType movementType, final float collisionSize) {
		return blockedByUnit(simulation.getWorldCollision(), simulation.getPathingGrid(), x, y, movementType,
				collisionSize, simulation.getGameTurnTick());
	}

	/**
	 * Whether another unit is in the way of this one at the point. Units on the ground are in the way when they share a
	 * pathing cell of the footprint the unit stands on, others when their collision squares overlap; with a
	 * tick, units that walk at that tick are left out.
	 */
	private boolean blockedByUnit(final CWorldCollision worldCollision, final PathingGrid pathingGrid, final float x,
			final float y, final MovementType movementType, final float collisionSize, final int gameTurnTick) {
		if ((movementType == MovementType.FOOT) || (movementType == MovementType.HORSE)
				|| (movementType == MovementType.HOVER) || (movementType == MovementType.AMPHIBIOUS)) {
			return worldCollision.intersectsFootprintOtherThan(pathingGrid, x, y,
					PathingGrid.getFootprintCells(collisionSize), this.unit, null, movementType, gameTurnTick);
		}
		if (this.unit.getCollisionRectangle() != null) {
			tempRect.set(this.unit.getCollisionRectangle());
		}
		else {
			tempRect.set(x, y, 0, 0);
		}
		tempRect.setCenter(x, y);
		if (gameTurnTick == NOT_A_TICK) {
			return worldCollision.intersectsAnythingOtherThan(tempRect, this.unit, movementType, false);
		}
		return worldCollision.intersectsStandingUnitOtherThan(tempRect, this.unit, null, movementType, gameTurnTick);
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
		if ((searchedGoal != this.target) && ((searchedGoal != this.sharedGoal) || (this.sharedGoal == null))) {
			this.pathfindingActive = false;
			this.followSearch = false;
			return;
		}
		if (searchedGoal == this.sharedGoal) {
			final List<CPathPoint> lanePath = !waypoints.isEmpty() ? shiftToLane(waypoints, reachesGoal, simulation)
					: null;
			if (lanePath == null) {
				// the slot or the shared way is no good: this unit finds its own way to its slot
				this.sharedRouteOff = true;
				this.pathfindingActive = false;
				this.followSearch = false;
				this.replacingPath = false;
				this.path = null;
				return;
			}
			pathFound(lanePath, reachesGoal, simulation);
			return;
		}
		pathFound(waypoints, reachesGoal, simulation);
	}

	public void pathFound(final List<CPathPoint> waypoints, final boolean reachesGoal,
			final CSimulation simulation) {
		this.pathfindingActive = false;
		this.followSearch = false;
		this.replacingPath = false;
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

	private boolean smoothsPath(final CSimulation simulation) {
		return !walksRowsToStandingTarget(simulation);
	}

	/**
	 * Measured: the way of an attack to a standing unit, from further away than a short walk, is not smoothed and runs
	 * on the cell centres (1.29 walks the grid row of its start up to the target); a short walk, a creep target the
	 * unit was not ordered to attack, spell approaches, work orders and the way to a walking unit are unchanged.
	 */
	public boolean walksRowsToStandingTarget(final CSimulation simulation) {
		return (this.followUnit != null) && (this.rangedBehavior instanceof CBehaviorAttack)
				&& !this.followUnit.isBuilding()
				&& !this.followUnit.isWalking(simulation.getGameTurnTick())
				&& (StrictMath.hypot(this.unit.getX() - this.followUnit.getX(),
						this.unit.getY() - this.followUnit.getY()) > ROW_PATH_MIN_DISTANCE)
				&& (!simulation.isNeutralAggressive(this.followUnit.getPlayerIndex())
						|| isOrderedAgainstFollowUnit(simulation));
	}

	/** An attack ordered on this very unit, not one taken up on the way of an attack-move or by acquisition. */
	private boolean isOrderedAgainstFollowUnit(final CSimulation simulation) {
		final COrder order = this.unit.getCurrentOrder();
		return (order != null) && (order.getTarget(simulation) == this.followUnit);
	}

	/**
	 * Measured: an attack on a standing unit from further away than a short walk, ordered or taken up by an idle unit,
	 * goes straight from where the unit stands to the centre of a cell on the near side of the target: two cells toward
	 * the unit along the axis the target lies on (a diagonal one: two on both axes). Across that axis it is the cell next
	 * to the target's toward the unit, but the unit keeps its own row (or column) while the target is less than two cells
	 * across toward higher coordinates or less than three toward lower ones. Returns null when the order does not walk
	 * that way.
	 */
	public CPathPoint approachCell(final CSimulation simulation, final float fromX, final float fromY) {
		if (!walksRowsToStandingTarget(simulation)
				|| (!isOrderedAgainstFollowUnit(simulation) && (this.unit.getCurrentOrder() != null))) {
			return null;
		}
		final float targetX = this.followUnit.getX();
		final float targetY = this.followUnit.getY();
		final float ax = Math.abs(targetX - fromX);
		final float ay = Math.abs(targetY - fromY);
		final boolean alongX = ay <= (1.2f * ax);
		final boolean alongY = ax <= (1.2f * ay);
		return new CPathPoint(approachCoordinate(targetX, fromX, alongX), approachCoordinate(targetY, fromY, alongY));
	}

	private static float approachCoordinate(final float target, final float from, final boolean along) {
		final int fromCell = (int) StrictMath.floor(from / 32f);
		int targetCell = (int) StrictMath.floor(target / 32f);
		final int cell;
		if (along) {
			cell = targetCell - (2 * Integer.signum(targetCell - fromCell));
		}
		else {
			if ((targetCell > fromCell) && ((targetCell * 32f) == target)) {
				// a target right on a cell line counts in the cell on the unit's side
				targetCell--;
			}
			final int cells = targetCell - fromCell;
			cell = ((cells >= 2) || (cells <= -3)) ? targetCell - Integer.signum(cells) : fromCell;
		}
		return (cell * 32f) + 16f;
	}

	public void pathFoundToApproachCell(final CPathPoint searchedGoal, final CPathPoint cell,
			final CSimulation simulation) {
		this.approachCell = cell;
		final List<CPathPoint> waypoints = new ArrayList<>();
		waypoints.add(cell);
		pathFound(searchedGoal, waypoints, true, simulation);
	}

	/** A move order given to the unit itself (not one an AI or a group march issues on its way). */
	public boolean isMoveOrder() {
		final COrder order = this.unit.getCurrentOrder();
		return (this.followUnit == null) && (this.rangedBehavior == null) && (order != null)
				&& ((order.getOrderId() == OrderIds.move) || (order.getOrderId() == OrderIds.smart));
	}

	/**
	 * A move to a point that a player or a script ordered: not a flight from an attacker, an approach for an attack or
	 * a spell, or a follow.
	 */
	public boolean isOrderedPointMove() {
		return (this.followUnit == null) && (this.rangedBehavior == null)
				&& ((this.highlightOrderId == OrderIds.move) || (this.highlightOrderId == OrderIds.smart))
				&& !this.unit.isFleeing();
	}

	/** Whether the search under way is the first one of this move, not one made on the way. */
	public boolean isFirstSearch() {
		return this.firstPathfindJob;
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
	 * brought it closer; at a wall that scripts have made it stops. A unit on its way to act on something (attack,
	 * harvest, build) heads on toward it instead, since its own range decides when it has arrived.
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
			if ((this.rangedBehavior instanceof ABBehavior) && (this.followUnit != null) && this.followUnit.isBuilding()
					&& (this.searchCycles > UNREACHABLE_BUILDING_SEARCHES)
					&& (this.unit.distance(this.target.x, this.target.y) > (this.pathStartDistance - 64))) {
				// a cast on a building that cannot be reached: the searches bring the unit no nearer, so it stops where it stands
				onMoveGiveUp(simulation);
				return this.unit.pollNextOrderBehavior(simulation);
			}
			this.pathReachesGoal = true;
			return this;
		}
		final double distance = this.unit.distance(this.target.x, this.target.y);
		if ((distance > (this.pathStartDistance - 64)) || (this.searchCycles > 9)
				|| simulation.getPathingGrid().isTerrainScripted(this.unit.getX(), this.unit.getY(), 4)) {
			onMoveGiveUp(simulation);
			return this.unit.pollNextOrderBehavior(simulation);
		}
		if (this.followUnit != null) {
			this.target.x = this.followUnit.getX();
			this.target.y = this.followUnit.getY();
		}
		simulation.findNaiveSlowPath(this.unit, this.followUnit, this.unit.getX(), this.unit.getY(), this.target,
				movementType, collisionSize, smoothsPath(simulation), this);
		this.pathfindingActive = true;
		this.searchCycles++;
		return this;
	}

	public boolean isPathfindingActive() {
		return this.pathfindingActive;
	}

	/** For the movement trace: what the path search and the walk along the path are doing. */
	public String traceSummary() {
		final StringBuilder summary = new StringBuilder();
		summary.append(this.pathfindingActive ? "search" : "walk");
		if (this.path != null) {
			summary.append(" nodes=").append(this.path.size());
			if (!this.path.isEmpty()) {
				final CPathPoint next = this.path.get(0);
				summary.append(" next=").append(next.x).append(',').append(next.y);
			}
			summary.append(this.pathReachesGoal ? " reaches" : " partial");
		}
		if (this.target != null) {
			summary.append(" goal=").append(this.target.x).append(',').append(this.target.y);
		}
		return summary.toString().replace(' ', '|');
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
