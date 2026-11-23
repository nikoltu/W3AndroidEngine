package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.cargohold;

import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold.CAbilityCargoHold;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold.CAbilityDrop;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CRangedBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

/**
 * Unload-all on a point. Measured on 1.29 with a transport ship and footmen: the units come off at the point that
 * lies the cargo hold area (250) from the transport towards the target point, or at the point itself when nearer,
 * as soon as a unit can stand there; a ship already by the shore so unloads at once, without moving, and a ship out
 * at sea sails towards the point until it can. Units put down farther than that area from the point then walk to
 * it. A ship out at sea told to unload on a point that near deep water does nothing. The point the units come off
 * at must be ground by its terrain: by a cliff shore (cliff where that point falls) the ship puts no one ashore.
 * Shallow water counts as ground only when the target point is within the cargo hold area (and a little more) of
 * the ship and the ship can walk a straight line to the spot: a ship in deep water does nothing for a point in the
 * shallows by it, one out at sea sails through the shallows until it can, and a rock between it and the spot is
 * sailed round first.
 */
public class CBehaviorDrop implements CRangedBehavior {
	private static final float NEAR_WATER_MARGIN = 32f;
	private static final float MEETING_POINT_SLACK = 64f;
	private static final float WALK_CHECK_STEP = 16f;
	/**
	 * Measured on 1.29: a Goblin Zeppelin sent to unload on a point flies on until it is this close to the point
	 * (it does not drop at the cargo hold area as a ship does), then puts the units down on the point itself.
	 */
	private static final float FLYING_DROP_RANGE = 80f;

	private final CUnit unit;
	private final CAbilityDrop ability;
	private AbilityPointTarget target;
	private AbilityPointTarget meetingPoint;
	private AbilityPointTarget dropOrigin;
	private int lastDropTick = 0;
	private boolean dropping;
	private float[] shoreSpot;

	public CBehaviorDrop(final CUnit unit, final CAbilityDrop ability) {
		this.unit = unit;
		this.ability = ability;
	}

	public CBehavior reset(final CSimulation game, final AbilityPointTarget target) {
		this.target = target;
		this.meetingPoint = null;
		this.dropOrigin = null;
		this.dropping = false;
		this.shoreSpot = null;
		this.lastDropTick = game.getGameTurnTick() - 1000000;
		final CAbilityCargoHold cargoData = this.unit.getCargoData();
		if ((cargoData == null) || cargoData.isEmpty()) {
			return this.unit.pollNextOrderBehavior(game);
		}
		if (dropSpot(game) != null) {
			this.dropping = true;
			return dropStep(game);
		}
		if (isFlying()) {
			this.meetingPoint = target;
			return this.unit.getMoveBehavior().reset(this.meetingPoint, this, false);
		}
		if (this.unit.isMovementDisabled() || isNearWater(game, cargoData)) {
			return unloadAtShore(game, cargoData);
		}
		float[] meeting = CargoReach.reachableOnTheWay(game, this.unit, target.x, target.y,
				(x, y) -> dropSpotFrom(game, x, y) != null);
		if (meeting == null) {
			meeting = CargoReach.nearestReachable(game, this.unit, target.x, target.y);
		}
		this.meetingPoint = new AbilityPointTarget(meeting[0], meeting[1]);
		if (isAtMeetingPoint()) {
			return unloadAtShore(game, cargoData);
		}
		return this.unit.getMoveBehavior().reset(this.meetingPoint, this, false);
	}

	/**
	 * Measured on 1.29: a ship by the shore told to unload on a point it cannot unload on or sail towards puts its
	 * cargo on the ground nearest to it at once, without moving; a ship with no ground within the cargo hold area
	 * does nothing.
	 */
	private CBehavior unloadAtShore(final CSimulation game, final CAbilityCargoHold cargoData) {
		if (this.unit.getMovementType() == MovementType.FLOAT) {
			final float[] spot = cargoData.findDropSpot(game, this.unit, cargoData.getUnit(0), this.unit.getX(),
					this.unit.getY());
			if (spot != null) {
				this.shoreSpot = spot;
				this.dropping = true;
				return dropStep(game);
			}
		}
		return this.unit.pollNextOrderBehavior(game);
	}

	/**
	 * Measured on 1.29: a ship far out ordered to unload on land 96, 196, 275 or 277 from the nearest water cell
	 * does nothing, while points 296 and 396 from it are sailed for: the cargo hold area and about one more cell.
	 * Only deep water counts: a point in the shallows that far from deep water is sailed for too.
	 */
	private boolean isNearWater(final CSimulation game, final CAbilityCargoHold cargoData) {
		if (this.unit.getMovementType() != MovementType.FLOAT) {
			return false;
		}
		final PathingGrid grid = game.getPathingGrid();
		if (grid.contains(this.target.x, this.target.y)) {
			final short terrain = grid.getTerrainPathing(this.target.x, this.target.y);
			if (MovementType.FLOAT.isPathable(terrain) && MovementType.FOOT.isPathable(terrain)) {
				// A point in the shallows is sailed for from farther off than the cargo hold area, whatever deep
				// water lies by it.
				return StrictMath.hypot(this.target.x - this.unit.getX(),
						this.target.y - this.unit.getY()) <= cargoData.getDropRadius();
			}
		}
		return isNearDeepWater(grid, this.target.x, this.target.y, cargoData.getDropRadius() + NEAR_WATER_MARGIN);
	}

	private static boolean isNearDeepWater(final PathingGrid grid, final float x, final float y, final float radius) {
		final int minX = Math.max(0, grid.getCellX(x - radius));
		final int maxX = Math.min(grid.getWidth() - 1, grid.getCellX(x + radius));
		final int minY = Math.max(0, grid.getCellY(y - radius));
		final int maxY = Math.min(grid.getHeight() - 1, grid.getCellY(y + radius));
		for (int cellY = minY; cellY <= maxY; cellY++) {
			for (int cellX = minX; cellX <= maxX; cellX++) {
				if ((StrictMath.hypot(grid.getWorldX(cellX) - x, grid.getWorldY(cellY) - y) <= radius)
						&& grid.isCellPathable(cellX, cellY, MovementType.FLOAT)
						&& !grid.isCellPathable(cellX, cellY, MovementType.FOOT)) {
					return true;
				}
			}
		}
		return false;
	}

	private float[] dropSpot(final CSimulation game) {
		if (this.shoreSpot != null) {
			return this.shoreSpot;
		}
		if (this.dropOrigin != null) {
			return dropSpotFrom(game, this.dropOrigin.x, this.dropOrigin.y);
		}
		return dropSpotFrom(game, this.unit.getX(), this.unit.getY());
	}

	private float[] dropSpotFrom(final CSimulation game, final float x, final float y) {
		final CAbilityCargoHold cargoData = this.unit.getCargoData();
		if ((cargoData == null) || cargoData.isEmpty()) {
			return null;
		}
		final CUnit passenger = cargoData.getUnit(0);
		final double distance = StrictMath.hypot(this.target.x - x, this.target.y - y);
		if (isFlying() && (distance > FLYING_DROP_RANGE)) {
			return null;
		}
		final float radius = cargoData.getDropRadius();
		float spotX = this.target.x;
		float spotY = this.target.y;
		if (distance > radius) {
			spotX = (float) (x + (((this.target.x - x) * radius) / distance));
			spotY = (float) (y + (((this.target.y - y) * radius) / distance));
		}
		MovementType movementType = passenger.getMovementType();
		if (movementType == null) {
			movementType = MovementType.FOOT;
		}
		final PathingGrid grid = game.getPathingGrid();
		if (!grid.contains(spotX, spotY)) {
			return null;
		}
		// Trees and units there do not matter: the unit is then put on the free spot nearest to it.
		final short terrain = grid.getTerrainPathing(spotX, spotY);
		if (!movementType.isPathable(terrain)) {
			return null;
		}
		if ((movementType == MovementType.FLOAT) || !MovementType.FLOAT.isPathable(terrain)) {
			return new float[] { spotX, spotY };
		}
		// Shallows count as ground only once the ship is by its target point and can walk a straight line to the spot.
		if ((distance <= (radius + NEAR_WATER_MARGIN)) && isWalkable(grid, x, y, spotX, spotY)) {
			return new float[] { spotX, spotY };
		}
		return null;
	}

	private static boolean isWalkable(final PathingGrid grid, final float fromX, final float fromY, final float toX,
			final float toY) {
		final double length = StrictMath.hypot(toX - fromX, toY - fromY);
		final int steps = (int) (length / WALK_CHECK_STEP) + 1;
		for (int i = 0; i <= steps; i++) {
			final float x = (float) (fromX + (((toX - fromX) * i) / steps));
			final float y = (float) (fromY + (((toY - fromY) * i) / steps));
			if (!grid.contains(x, y) || !MovementType.FOOT.isPathable(grid.getTerrainPathing(x, y))) {
				return false;
			}
		}
		return true;
	}

	private boolean isFlying() {
		return this.unit.getMovementType() == MovementType.FLY;
	}

	private boolean isAtMeetingPoint() {
		return (this.meetingPoint == null) || (this.unit.distance(this.meetingPoint.x, this.meetingPoint.y) <= 0);
	}

	@Override
	public boolean isWithinRange(final CSimulation simulation) {
		return this.dropping || (dropSpot(simulation) != null) || isAtMeetingPoint();
	}

	@Override
	public CBehavior update(final CSimulation simulation) {
		if (!this.dropping) {
			if (dropSpot(simulation) == null) {
				// A ship stopped by the shore a little short of its meeting point drops as if it stood there.
				if ((this.meetingPoint == null)
						|| (this.unit.distance(this.meetingPoint.x, this.meetingPoint.y) > MEETING_POINT_SLACK)
						|| (dropSpotFrom(simulation, this.meetingPoint.x, this.meetingPoint.y) == null)) {
					return this.unit.pollNextOrderBehavior(simulation);
				}
				this.dropOrigin = this.meetingPoint;
			}
			this.dropping = true;
		}
		return dropStep(simulation);
	}

	private CBehavior dropStep(final CSimulation simulation) {
		final CAbilityCargoHold cargoData = this.unit.getCargoData();
		if (cargoData.isEmpty()) {
			return this.unit.pollNextOrderBehavior(simulation);
		}
		final int gameTurnTick = simulation.getGameTurnTick();
		final int durationTicks = CTimer.ceilSteps(cargoData.getDuration());
		if (durationTicks <= 0) {
			while (!cargoData.isEmpty()) {
				if (!dropOne(simulation, cargoData)) {
					return this.unit.pollNextOrderBehavior(simulation);
				}
			}
			return this.unit.pollNextOrderBehavior(simulation);
		}
		if ((gameTurnTick - this.lastDropTick) >= durationTicks) {
			if (!dropOne(simulation, cargoData)) {
				return this.unit.pollNextOrderBehavior(simulation);
			}
			this.unit.notifyOrdersChanged();
			this.lastDropTick = gameTurnTick;
			if (cargoData.isEmpty()) {
				return this.unit.pollNextOrderBehavior(simulation);
			}
		}
		return this;
	}

	private boolean dropOne(final CSimulation simulation, final CAbilityCargoHold cargoData) {
		final float[] spot = dropSpot(simulation);
		if (spot == null) {
			return false;
		}
		final CUnit dropped = cargoData.dropUnitByIndex(simulation, this.unit, 0, spot[0], spot[1]);
		if ((this.shoreSpot == null) && (StrictMath.hypot(dropped.getX() - this.target.x,
				dropped.getY() - this.target.y) > cargoData.getDropRadius())) {
			dropped.order(simulation, OrderIds.move, new AbilityPointTarget(this.target.x, this.target.y));
		}
		return true;
	}

	@Override
	public AbilityTarget getTarget() {
		return this.target;
	}

	@Override
	public void begin(final CSimulation game) {
	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {
	}

	@Override
	public void endMove(final CSimulation game, final boolean interrupted) {
	}

	@Override
	public int getHighlightOrderId() {
		return OrderIds.unloadall;
	}

	@Override
	public boolean interruptable() {
		return true;
	}

	@Override
	public CBehaviorCategory getBehaviorCategory() {
		return CBehaviorCategory.SPELL;
	}

	@Override
	public <T> T visit(final CBehaviorVisitor<T> visitor) {
		return visitor.accept(this);
	}
}
