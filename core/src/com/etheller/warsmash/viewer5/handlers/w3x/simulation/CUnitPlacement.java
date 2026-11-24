package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit.PendingRegionEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegion;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegionEnumFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegionManager;

/**
 * Placing a unit on the map: free-cell search, unstuck, region events and reach checks.
 */
final class CUnitPlacement {
	private static final int STRUCTURE_PLACE_ATTEMPTS = 24;
	private static final int STRUCTURE_PUSH_MIN_BEYOND_EDGE = 145;
	private static final int STRUCTURE_PUSH_SPREAD = 34;

	private CUnitPlacement() {
	}

	static RegionCheckerImpl regionCheckerImpl = new RegionCheckerImpl();

	static void regeneratePathingInstance(final CUnit self, final CSimulation game, final ArgbImage buildingPathingPixelMap) {
		float unitX = self.getX();
		float unitY = self.getY();
		unitX = (float) Math.floor(unitX / 64f) * 64f;
		unitY = (float) Math.floor(unitY / 64f) * 64f;
		if (((buildingPathingPixelMap.getWidth() / 2) % 2) == 1) {
			unitX += 32f;
		}
		if (((buildingPathingPixelMap.getHeight() / 2) % 2) == 1) {
			unitY += 32f;
		}
		self.placement.pathingInstance = game.getPathingGrid().blitRemovablePathingOverlayTexture(unitX, unitY,
				270 /* no rotation, face forward */, buildingPathingPixelMap);
		self.setX(unitX);
		self.setY(unitY);
	}

	static void setPointAndCheckUnstuck(final CUnit self, final float newX, final float newY, final CSimulation game) {
		if (self.removedFromGame) {
			// Measured (1.29): moving a removed unit does nothing.
			return;
		}
		final CWorldCollision collision = game.getWorldCollision();
		final PathingGrid pathingGrid = game.getPathingGrid();
		;
		if (self.movement.pathingDisabled) {
			setPoint(self, newX, newY, collision, game.getRegionManager());
			game.unitRepositioned(self);
			return;
		}
		float outputX = newX, outputY = newY;
		int checkX = 0;
		int checkY = 0;
		float collisionSize;
		if (self.isBuilding() && (self.unitType.getBuildingPathingPixelMap() != null)) {
			CWidget.tempRect.setSize(self.unitType.getBuildingPathingPixelMap().getWidth() * 32,
					self.unitType.getBuildingPathingPixelMap().getHeight() * 32);
			collisionSize = CWidget.tempRect.getWidth() / 2;
		} else if (self.placement.collisionRectangle != null) {
			CWidget.tempRect.set(self.placement.collisionRectangle);
			collisionSize = self.unitType.getCollisionSize();
		} else {
			CWidget.tempRect.setSize(16, 16);
			collisionSize = self.unitType.getCollisionSize();
		}
		if (!self.isBuilding()) {
			final float[] free = findNearestFreeCell(self, newX, newY, collision, pathingGrid, collisionSize, self.getMovementType());
			setPoint(self, free[0], free[1], collision, game.getRegionManager());
			game.unitRepositioned(self);
			return;
		}
		for (int i = 0; i < 300; i++) {
			final float centerX = newX + (checkX * 64);
			final float centerY = newY + (checkY * 64);
			CWidget.tempRect.setCenter(centerX, centerY);
			if (!collision.intersectsAnythingOtherThan(CWidget.tempRect, self, self.getMovementType(), false)
					&& pathingGrid.isPathable(centerX, centerY, self.getMovementType(), collisionSize)) {
				outputX = centerX;
				outputY = centerY;
				break;
			}
			final double angle = (((int) Math.floor(Math.sqrt((4 * i) + 1)) % 4) * Math.PI) / 2;
			checkX -= (int) StrictMath.cos(angle);
			checkY -= (int) StrictMath.sin(angle);
		}
		setPoint(self, outputX, outputY, collision, game.getRegionManager());
		game.unitRepositioned(self);
	}

	/**
	 * Measured (1.29): a unit that stands over others when it is paused by a script, or when it is idle and takes a
	 * stop, hold, move or smart order, goes at once (same tick) to the first free cell of the placement spiral from
	 * its own cell; a unit that fits stays. Units are handled in the order the calls come, each against the current
	 * places of the rest.
	 */
	static void separateFromOverlapping(final CUnit self, final CSimulation game) {
		if (self.removedFromGame || self.isDead() || self.isHidden() || self.isBuilding() || self.movement.pathingDisabled
				|| (self.getMovementType() == MovementType.FLY)) {
			return;
		}
		final float[] free = findFreePoint(self, self.getX(), self.getY(), game);
		if ((free[0] != self.getX()) || (free[1] != self.getY())) {
			setPointAndCheckUnstuck(self, free[0], free[1], game);
		}
	}

	/**
	 * Puts a flyer forced to the ground (Web) where a land unit of its size could stand: it stays when its own place
	 * fits one, otherwise it goes to the first free cell of the spiral from fromX, fromY.
	 */
	static void placeOnGround(final CUnit self, final CSimulation game, final float fromX, final float fromY) {
		final CWorldCollision collision = game.getWorldCollision();
		final PathingGrid pathingGrid = game.getPathingGrid();
		final float collisionSize = self.unitType.getCollisionSize();
		// Measured with a Gryphon Rider (collision 8) next to a Crypt Fiend (32): a cell 48 from the Fiend on an axis
		// is refused, 68 and 73 are taken, so the flyer counts as 32 here, not 8.
		final float size = Math.max(64f, collisionSize * 2);
		CWidget.tempRect.setSize(size, size);
		if (fitsAt(self, self.getX(), self.getY(), collision, pathingGrid, collisionSize, MovementType.FOOT)) {
			return;
		}
		final float[] free = findNearestFreeCell(self, fromX, fromY, collision, pathingGrid, collisionSize,
				MovementType.FOOT);
		setPoint(self, free[0], free[1], collision, game.getRegionManager());
		game.unitRepositioned(self);
	}

	/**
	 * Where this (non-building) unit would stand if put at the point, without moving it.
	 */
	static float[] findFreePoint(final CUnit self, final float x, final float y, final CSimulation game) {
		if (self.movement.pathingDisabled || self.isBuilding()) {
			return new float[] { x, y };
		}
		if (self.placement.collisionRectangle != null) {
			CWidget.tempRect.set(self.placement.collisionRectangle);
		}
		else {
			CWidget.tempRect.setSize(16, 16);
		}
		return findNearestFreeCell(self, x, y, game.getWorldCollision(), game.getPathingGrid(),
				self.unitType.getCollisionSize(), self.getMovementType());
	}

	static boolean fitsAt(final CUnit self, final float x, final float y, final CWorldCollision collision,
			final PathingGrid pathingGrid, final float collisionSize, final MovementType movementType) {
		return !blockedByUnit(self, x, y, collision, pathingGrid, collisionSize, movementType)
				&& pathingGrid.isPathable(x, y, movementType, collisionSize);
	}

	/**
	 * Whether another unit stands where this one would at the point. Ground units are compared by the pathing cells
	 * they stand on, flyers not at all, others by their collision squares.
	 */
	static boolean blockedByUnit(final CUnit self, final float x, final float y, final CWorldCollision collision,
			final PathingGrid pathingGrid, final float collisionSize, final MovementType movementType) {
		if (movementType == MovementType.FLY) {
			// Measured: flyers made at one point all stay on it.
			return false;
		}
		if ((movementType == MovementType.FOOT) || (movementType == MovementType.HORSE)
				|| (movementType == MovementType.HOVER) || (movementType == MovementType.AMPHIBIOUS)) {
			return collision.intersectsFootprintOtherThan(pathingGrid, x, y,
					PathingGrid.getFootprintCells(collisionSize), self, movementType);
		}
		CWidget.tempRect.setCenter(x, y);
		return collision.intersectsAnythingOtherThan(CWidget.tempRect, self, movementType, false);
	}

	/**
	 * A unit that cannot stand where it is put keeps that point when it is free; otherwise it goes to the centre of the
	 * first free 32 pathing cell along the square spiral the original game uses (from the point's own cell: down 1,
	 * right 1, up 2, left 2, down 3, ...), which is not always the nearest one. Measured: the original tests the
	 * pathing map at the cell's lower left corner but other units at the cell centre.
	 */
	static float[] findNearestFreeCell(final CUnit self, final float x, final float y, final CWorldCollision collision,
			final PathingGrid pathingGrid, final float collisionSize, final MovementType movementType) {
		if (fitsAt(self, x, y, collision, pathingGrid, collisionSize, movementType)) {
			return new float[] { x, y };
		}
		final float baseX = (float) Math.floor(x / 32f) * 32f;
		final float baseY = (float) Math.floor(y / 32f) * 32f;
		final int[] stepX = { 0, 1, 0, -1 };
		final int[] stepY = { -1, 0, 1, 0 };
		int cellX = 0;
		int cellY = 0;
		int direction = 0;
		final int maxSteps = CUnit.UNIT_PLACE_SEARCH_CELLS;
		int visited = 0;
		for (int legLength = 1; visited < maxSteps; legLength++) {
			for (int leg = 0; leg < 2; leg++) {
				for (int step = 0; (step < legLength) && (visited < maxSteps); step++) {
					visited++;
					final float cx = baseX + (cellX * 32f);
					final float cy = baseY + (cellY * 32f);
					if (!blockedByUnit(self, cx + 16f, cy + 16f, collision, pathingGrid, collisionSize, movementType)
							&& pathingGrid.isPathable(cx, cy, movementType, collisionSize)) {
						return new float[] { cx + 16f, cy + 16f };
					}
					cellX += stepX[direction];
					cellY += stepY[direction];
				}
				direction = (direction + 1) % 4;
			}
		}
		return new float[] { x, y };
	}

	/**
	 * Teleports a unit to a structure (a town hall). Measured: the original game tries random 32 cells of the square
	 * the structure's pathing map covers and takes the first one the unit fits on, so the units land evenly over the
	 * free cells round the building; a unit that finds no fit in a score of tries (only large ones) is placed by the
	 * free-cell search from the structure's centre.
	 */
	static void placeAroundStructure(final CUnit self, final CSimulation game, final CUnit structure) {
		final CWorldCollision collision = game.getWorldCollision();
		final PathingGrid pathingGrid = game.getPathingGrid();
		final float collisionSize = self.unitType.getCollisionSize();
		final MovementType movementType = self.getMovementType();
		if (self.placement.collisionRectangle != null) {
			CWidget.tempRect.set(self.placement.collisionRectangle);
		}
		else {
			CWidget.tempRect.setSize(16, 16);
		}
		final ArgbImage pathing = structure.getUnitType().getBuildingPathingPixelMap();
		final int cellsX = pathing == null ? 4 : Math.max(1, pathing.getWidth());
		final int cellsY = pathing == null ? 4 : Math.max(1, pathing.getHeight());
		final float firstX = (float) Math.floor((structure.getX() - (cellsX * 16f)) / 32f) * 32f;
		final float firstY = (float) Math.floor((structure.getY() - (cellsY * 16f)) / 32f) * 32f;
		final Random random = game.getSeededRandom();
		for (int attempt = 0; attempt < STRUCTURE_PLACE_ATTEMPTS; attempt++) {
			final float cx = firstX + (random.nextInt(cellsX) * 32f);
			final float cy = firstY + (random.nextInt(cellsY) * 32f);
			if (!blockedByUnit(self, cx + 16f, cy + 16f, collision, pathingGrid, collisionSize, movementType)
					&& pathingGrid.isPathable(cx, cy, movementType, collisionSize)) {
				setPoint(self, cx + 16f, cy + 16f, collision, game.getRegionManager());
				game.unitRepositioned(self);
				return;
			}
		}
		final float[] free = findNearestFreeCell(self, structure.getX(), structure.getY(), collision, pathingGrid,
				collisionSize, movementType);
		setPoint(self, free[0], free[1], collision, game.getRegionManager());
		game.unitRepositioned(self);
	}

	/**
	 * A unit standing where a structure is started (by a builder walking to the site) is not nudged to the nearest
	 * free cell. Measured on 1.29 (Footman and Knight under a Farm): it jumps along the axis it lies nearest to (the
	 * vertical one when level, upwards on the centre line) to a point 209 to 242 from the structure's centre, 145 to
	 * 178 beyond the edge of a 4 cell structure, keeping its place across that axis plus 0 to 31. A point that does not
	 * fit goes through the ordinary free-cell search.
	 */
	static void pushOutOfNewStructure(final CUnit self, final CSimulation game, final CUnit structure) {
		final ArgbImage pathing = structure.getUnitType().getBuildingPathingPixelMap();
		final int cellsX = pathing == null ? 4 : Math.max(1, pathing.getWidth());
		final int cellsY = pathing == null ? 4 : Math.max(1, pathing.getHeight());
		final float offsetX = self.getX() - structure.getX();
		final float offsetY = self.getY() - structure.getY();
		final boolean alongX = Math.abs(offsetX) > Math.abs(offsetY);
		final Random random = game.getSeededRandom();
		final float distance = ((alongX ? cellsX : cellsY) * 16f) + STRUCTURE_PUSH_MIN_BEYOND_EDGE
				+ random.nextInt(STRUCTURE_PUSH_SPREAD);
		final float across = random.nextInt(32);
		if (alongX) {
			setPointAndCheckUnstuck(self, structure.getX() + (offsetX < 0 ? -distance : distance), self.getY() + across,
					game);
		}
		else {
			setPointAndCheckUnstuck(self, self.getX() + across, structure.getY() + (offsetY < 0 ? -distance : distance),
					game);
		}
	}

	static void setPoint(final CUnit self, final float newX, final float newY, final CWorldCollision collision,
			final CRegionManager regionManager) {
		final float prevX = self.getX();
		final float prevY = self.getY();
		self.setX(newX);
		self.setY(newY);
		collision.translate(self, newX - prevX, newY - prevY);
		checkRegionEvents(self, regionManager);
	}

	static void dispatchRegionEvent(final CUnit self, final CRegionManager regionManager, final CRegion region, final boolean enter) {
		if (!self.regionEventsDeferred) {
			if (enter) {
				regionManager.onUnitEnterRegion(self, region);
			}
			else {
				regionManager.onUnitLeaveRegion(self, region);
			}
			return;
		}
		if (self.pendingRegionEvents == null) {
			self.pendingRegionEvents = new ArrayList<>();
		}
		self.pendingRegionEvents.add(new PendingRegionEvent(region, enter));
	}

	static void checkRegionEvents(final CUnit self, final CRegionManager regionManager) {
		final Set<CRegion> temp = self.containingRegions;
		self.containingRegions = self.priorContainingRegions;
		self.priorContainingRegions = temp;
		self.containingRegions.clear();
		regionManager.checkRegions(
				self.placement.collisionRectangle == null ? CWidget.tempRect.set(self.getX(), self.getY(), 0, 0) : self.placement.collisionRectangle,
				regionCheckerImpl.reset(self, regionManager));
		// A leave trigger may move the unit again, which swaps and clears both sets, so the leaving regions are
		// collected before any of them is dispatched.
		List<CRegion> left = null;
		for (final CRegion region : self.priorContainingRegions) {
			if (!self.containingRegions.contains(region)) {
				if (left == null) {
					left = new ArrayList<>();
				}
				left.add(region);
			}
		}
		if (left != null) {
			for (final CRegion region : left) {
				dispatchRegionEvent(self, regionManager, region, false);
			}
		}
	}

	static boolean canReach(final CUnit self, final AbilityTarget target, final float range) {
		final double distance = self.distance(target);
		if (target instanceof CUnit) {
			final CUnit targetUnit = (CUnit) target;
			final CUnitType targetUnitType = targetUnit.getUnitType();
			if (targetUnit.isBuilding() && (targetUnitType.getBuildingPathingPixelMap() != null)) {
				final ArgbImage buildingPathingPixelMap = targetUnitType.getBuildingPathingPixelMap();
				final float targetX = target.getX();
				final float targetY = target.getY();
				if (canReachToPathing(self, range, targetUnit.getFacing(), buildingPathingPixelMap, targetX, targetY)) {
					return true;
				}
			}
		} else if (target instanceof CDestructable) {
			final CDestructable targetDest = (CDestructable) target;
			final CDestructableType targetDestType = targetDest.getDestType();
			final ArgbImage pathingPixelMap = targetDest.isDead() ? targetDestType.getPathingDeathPixelMap()
					: targetDestType.getPathingPixelMap();
			final float targetX = target.getX();
			final float targetY = target.getY();
			// Measured on 1.29: a unit attacks a destructable from up to range / 32 cells off its pathing (a footman
			// 2 cells off a gate), one cell less than the building check allows; a zero range (a Wisp on a tree) is
			// met by the footprint touching the tree from any side, where the footprint reaches either way off the
			// unit's own cell.
			if ((pathingPixelMap != null) && canReachToPathing(self, range, targetDest.getPathingRotation(), pathingPixelMap,
					targetX, targetY, Math.max(1f, (range * range) / (32f * 32f)), range <= 0f)) {
				return true;
			}
		}
		return distance <= range;
	}

	/**
	 * Whether the cells a unit stands on touch the pathing of a building, also at its corners. Measured: a unit that
	 * stands diagonally off a corner of a building attacks it from there, though that is past the reach from its
	 * centre.
	 */
	static boolean isBesideBuilding(final CUnit self, final CUnit building) {
		final ArgbImage buildingPathingPixelMap = building.getUnitType().getBuildingPathingPixelMap();
		if (buildingPathingPixelMap == null) {
			return false;
		}
		return canReachToPathing(self, 0f, building.getFacing(), buildingPathingPixelMap, building.getX(),
				building.getY(), 3f);
	}

	static boolean canReach(final CUnit self, final float x, final float y, final float range) {
		return self.distance(x, y) <= range; // TODO use dist squared for performance
	}

	static boolean canReachToPathing(final CUnit self, final float range, final float rotationForPathing,
			final ArgbImage buildingPathingPixelMap, final float targetX, final float targetY) {
		final int rangeInCells = (int) Math.floor(range / 32f) + 1;
		return canReachToPathing(self, range, rotationForPathing, buildingPathingPixelMap, targetX, targetY,
				rangeInCells * rangeInCells);
	}

	/**
	 * Measured: a worker raising a structure over a mine (an Acolyte over a Gold Mine) starts once its footprint
	 * touches the mine's blocked cells, from any side. canReachToPathing keeps a cell more off the footprint than that.
	 */
	static boolean canReachToPathingTouching(final CUnit self, final float rotationForPathing,
			final ArgbImage buildingPathingPixelMap, final float targetX, final float targetY) {
		return canReachToPathing(self, 0, rotationForPathing, buildingPathingPixelMap, targetX, targetY, 1, true);
	}

	static boolean canReachToPathing(final CUnit self, final float range, final float rotationForPathing,
			final ArgbImage buildingPathingPixelMap, final float targetX, final float targetY,
			final float rangeInCellsSquare) {
		return canReachToPathing(self, range, rotationForPathing, buildingPathingPixelMap, targetX, targetY,
				rangeInCellsSquare, false);
	}

	private static boolean canReachToPathing(final CUnit self, final float range, final float rotationForPathing,
			final ArgbImage buildingPathingPixelMap, final float targetX, final float targetY,
			final float rangeInCellsSquare, final boolean touching) {
		if (buildingPathingPixelMap == null) {
			return canReach(self, targetX, targetY, range);
		}
		final int rotation = ((int) rotationForPathing + 450) % 360;
		final float relativeOffsetX = self.getX() - targetX;
		final float relativeOffsetY = self.getY() - targetY;
		final int gridWidth = (rotation % 180) != 0 ? buildingPathingPixelMap.getHeight()
				: buildingPathingPixelMap.getWidth();
		final int gridHeight = (rotation % 180) != 0 ? buildingPathingPixelMap.getWidth()
				: buildingPathingPixelMap.getHeight();
		final int relativeGridX = (int) Math.floor(relativeOffsetX / 32f) + (gridWidth / 2);
		final int relativeGridY = (int) Math.floor(relativeOffsetY / 32f) + (gridHeight / 2);
		final int rangeInCells = (int) Math.floor(range / 32f) + 1;
		// Measured: a ground unit reaches from the cells it stands on (PathingGrid.getFootprintCells), not from the cell
		// of its point: a worker on a corner of a building, its square diagonal to the building's corner cell, delivers
		// there as one beside a side does.
		final int footprint = footprintCells(self);
		final int firstX = relativeGridX - (footprint / 2);
		final int firstY = relativeGridY - (footprint / 2);
		final int lastX = (firstX + footprint) - 1;
		final int lastY = (firstY + footprint) - 1;
		int minCheckX = firstX - rangeInCells;
		int minCheckY = firstY - rangeInCells;
		int maxCheckX = lastX + rangeInCells;
		int maxCheckY = lastY + rangeInCells;
		if ((minCheckX < gridWidth) && (maxCheckX >= 0)) {
			if ((minCheckY < gridHeight) && (maxCheckY >= 0)) {
				if (minCheckX < 0) {
					minCheckX = 0;
				}
				if (minCheckY < 0) {
					minCheckY = 0;
				}
				if (maxCheckX > (gridWidth - 1)) {
					maxCheckX = gridWidth - 1;
				}
				if (maxCheckY > (gridHeight - 1)) {
					maxCheckY = gridHeight - 1;
				}
				for (int checkX = minCheckX; checkX <= maxCheckX; checkX++) {
					for (int checkY = minCheckY; checkY <= maxCheckY; checkY++) {
						final int dx = relativeGridX - checkX;
						final int dy = relativeGridY - checkY;
						final int fx = Math.max(0, Math.max(firstX - checkX, checkX - lastX));
						final int fy = Math.max(0, Math.max(firstY - checkY, checkY - lastY));
						// a point of the footprint within the reach (a footprint cell diagonal to the building's
						// corner cell counts as one cell off both ways)
						final boolean near = (((dx * dx) + (dy * dy)) <= rangeInCellsSquare)
								|| (((fx * fx) + (fy * fy)) <= (touching ? rangeInCellsSquare : (rangeInCellsSquare - 1)));
						if (near) {
							if (((getRGBFromPixelData(self, buildingPathingPixelMap, checkX, checkY, rotation)
									& 0xFF0000) >>> 16) > 127) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	/** The cells a ground unit stands on in each direction; flyers and unit types without collision stand on one. */
	private static int footprintCells(final CUnit self) {
		if (self.getMovementType() == MovementType.FLY) {
			return 1;
		}
		return PathingGrid.getFootprintCells(self.getUnitType().getCollisionSize());
	}

	static int getRGBFromPixelData(final CUnit self, final ArgbImage buildingPathingPixelMap, final int checkX, final int checkY,
			final int rotation) {

		// Below: y is downwards (:()
		int x;
		int y;
		switch (rotation) {
		// the inverse of how PathingGrid lays a turned pathing map into the grid
		case 90:
			x = checkY;
			y = buildingPathingPixelMap.getHeight() - 1 - checkX;
			break;
		case 180:
			x = buildingPathingPixelMap.getWidth() - 1 - checkX;
			y = buildingPathingPixelMap.getHeight() - 1 - checkY;
			break;
		case 270:
			x = buildingPathingPixelMap.getWidth() - 1 - checkY;
			y = checkX;
			break;
		default:
		case 0:
			x = checkX;
			y = checkY;
		}
		return buildingPathingPixelMap.getRGB(x, buildingPathingPixelMap.getHeight() - 1 - y);
	}

	static void innerUpdateNoCollisionMovementType(final CUnit self, final CWorldCollision worldCollision) {
		// NOTE: changing movement type will crash the game unless we also remove/add
		// the unit from world collision
		if (self.movement.movementOverride != self.movement.nextMovementOverride) {
			final boolean hasCollision = self.getCollisionRectangle() != null;
			if (hasCollision) {
				worldCollision.removeUnit(self);
			}
			self.movement.movementOverride = self.movement.nextMovementOverride;
			if (hasCollision) {
				worldCollision.addUnit(self);
			}
		}
	}

	static void nudgeAround(final CUnit self, final CSimulation simulation, final CUnit structure) {
		setPointAndCheckUnstuck(self, structure.getX(), structure.getY(), simulation);
	}

	/**
	 * The unit's footprint touches the region right now. A region made after the unit last moved (a trigger action
	 * making one for the unit that just entered a rect) is not in the unit's cached region set yet, so the cache alone
	 * would answer false for it.
	 */
	static boolean isInRegion(final CUnit self, final CRegion region, final CRegionManager regionManager) {
		if (self.containingRegions.contains(region)) {
			return true;
		}
		final RegionMatchImpl match = new RegionMatchImpl(region);
		regionManager.checkRegions(
				self.placement.collisionRectangle == null ? new Rectangle(self.getX(), self.getY(), 0, 0)
						: self.placement.collisionRectangle,
				match);
		return match.found;
	}

	private static final class RegionMatchImpl implements CRegionEnumFunction {
		private final CRegion target;
		private boolean found;

		private RegionMatchImpl(final CRegion target) {
			this.target = target;
		}

		@Override
		public boolean call(final CRegion region) {
			if (region == this.target) {
				this.found = true;
			}
			return this.found;
		}
	}

	static final class RegionCheckerImpl implements CRegionEnumFunction {
		CUnit unit;
		CRegionManager regionManager;

		public RegionCheckerImpl reset(final CUnit unit, final CRegionManager regionManager) {
			this.unit = unit;
			this.regionManager = regionManager;
			return this;
		}

		@Override
		public boolean call(final CRegion region) {
			if (this.unit.containingRegions.add(region)) {
				if (!this.unit.priorContainingRegions.contains(region)) {
					this.unit.dispatchRegionEvent(this.regionManager, region, true);
				}
			}
			return false;
		}

	}

}
