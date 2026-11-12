package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.Set;
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
		CWidget.tempRect.setCenter(x, y);
		return !collision.intersectsAnythingOtherThan(CWidget.tempRect, self, movementType, false)
				&& pathingGrid.isPathable(x, y, movementType, collisionSize);
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
					CWidget.tempRect.setCenter(cx + 16f, cy + 16f);
					if (!collision.intersectsAnythingOtherThan(CWidget.tempRect, self, movementType, false)
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
		for (final CRegion region : self.priorContainingRegions) {
			if (!self.containingRegions.contains(region)) {
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
			// 2 cells off a gate), one cell less than the building check allows; a zero range (a Wisp on a tree) keeps one cell.
			if ((pathingPixelMap != null) && canReachToPathing(self, range, targetDest.getPathingRotation(), pathingPixelMap,
					targetX, targetY, Math.max(1f, (range * range) / (32f * 32f)))) {
				return true;
			}
		}
		return distance <= range;
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

	static boolean canReachToPathing(final CUnit self, final float range, final float rotationForPathing,
			final ArgbImage buildingPathingPixelMap, final float targetX, final float targetY,
			final float rangeInCellsSquare) {
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
		int minCheckX = relativeGridX - rangeInCells;
		int minCheckY = relativeGridY - rangeInCells;
		int maxCheckX = relativeGridX + rangeInCells;
		int maxCheckY = relativeGridY + rangeInCells;
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
						if (((dx * dx) + (dy * dy)) <= rangeInCellsSquare) {
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

	static boolean isInRegion(final CUnit self, final CRegion region) {
		return self.containingRegions.contains(region);
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
