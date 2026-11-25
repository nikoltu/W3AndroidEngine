package com.etheller.warsmash.viewer5.handlers.w3x.environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.parsers.w3x.wpm.War3MapWpm;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWorldCollision;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CBuildingPathingType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;

public class PathingGrid {
	public static final ArgbImage BLANK_PATHING = new ArgbImage(1, 1, new int[1]);
	private static final Map<String, MovementType> movetpToMovementType = new HashMap<>();
	static {
		for (final MovementType movementType : MovementType.values()) {
			if (!movementType.typeKey.isEmpty()) {
				movetpToMovementType.put(movementType.typeKey, movementType);
			}
		}
	}

	private final short[] pathingGrid;
	private final short[] dynamicPathingOverlay; // for buildings and trees
	private final int[] pathingGridSizes;
	private final float[] centerOffset;
	private final Set<RemovablePathingMapInstance> dynamicPathingInstances;
	/**
	 * Set when instances were taken out: the overlay is laid down again from the remaining ones the next time it is read,
	 * so a script that removes tens of thousands of trees in one go lays it down once.
	 */
	private boolean overlayStale;
	/** The cells taken out since the overlay was last valid; a few of them are laid down again alone, many all at once. */
	private static final int INCREMENTAL_REMOVALS = 16;
	private final int[] removedCells = new int[INCREMENTAL_REMOVALS * 4];
	private int removedCount;
	/** The instances by the blocks of cells they reach, to find those near a removed one without going through all. */
	private static final int BLOCK_SHIFT = 3;
	private final Map<Long, List<RemovablePathingMapInstance>> instanceBlocks = new HashMap<>();
	private int refreshStamp;

	private static long blockKey(final int blockX, final int blockY) {
		return (((long) blockY) << 32) ^ (blockX & 0xFFFFFFFFL);
	}

	private void indexInstance(final RemovablePathingMapInstance instance, final boolean add) {
		final int[] cells = instance.cells();
		final int maxBlockX = Math.max(cells[2], 0) >> BLOCK_SHIFT;
		final int maxBlockY = Math.max(cells[3], 0) >> BLOCK_SHIFT;
		for (int by = Math.max(cells[1], 0) >> BLOCK_SHIFT; by <= maxBlockY; by++) {
			for (int bx = Math.max(cells[0], 0) >> BLOCK_SHIFT; bx <= maxBlockX; bx++) {
				final long key = blockKey(bx, by);
				if (add) {
					this.instanceBlocks.computeIfAbsent(key, k -> new ArrayList<>()).add(instance);
				}
				else {
					final List<RemovablePathingMapInstance> inBlock = this.instanceBlocks.get(key);
					if (inBlock != null) {
						inBlock.remove(instance);
						if (inBlock.isEmpty()) {
							this.instanceBlocks.remove(key);
						}
					}
				}
			}
		}
	}
	private int overlayVersion;
	/**
	 * The pathing cells each recent change of the overlay touched, newest last in a ring, each with the version the
	 * change ended on; a reader that knows the version it last saw takes the cells changed since
	 * ({@link #collectOverlayChanges}). A change that touches no cell of the grid is not listed.
	 */
	private static final int CHANGE_LOG = 256;
	private final int[] changeVersions = new int[CHANGE_LOG];
	private final int[] changeCells = new int[CHANGE_LOG * 4];
	private int changesLogged;
	private int changesDroppedVersion = Integer.MIN_VALUE;
	private boolean changesSuppressed;
	public static final short SCRIPT_PEON_BLOCKED = 1;
	public static final short SCRIPT_PEON_OPEN = 2;
	public static final short SCRIPT_AMPHIBIOUS_BLOCKED = 4;
	public static final short SCRIPT_AMPHIBIOUS_OPEN = 8;
	/** Allocated by the first script change of peon harvest or amphibious pathing; parallel to the grid. */
	private short[] scriptFlags;

	public PathingGrid(final War3MapWpm terrainPathing, final float[] centerOffset) {
		this.centerOffset = centerOffset;
		this.pathingGrid = terrainPathing.getPathing();
		this.pathingGridSizes = terrainPathing.getSize();
		this.dynamicPathingOverlay = new short[this.pathingGrid.length];
		this.dynamicPathingInstances = new LinkedHashSet<>();
	}

	// this blit function is basically copied from HiveWE, maybe remember to mention
	// that in credits as well:
	// https://github.com/stijnherfst/HiveWE/blob/master/Base/PathingMap.cpp
	private void blitPathingOverlayTexture(final float positionX, final float positionY, final int rotationInput,
			final ArgbImage pathingTextureTga, boolean blocksVision) {
		this.overlayVersion++;
		final int rotation = (rotationInput + 450) % 360;
		final int divW = ((rotation % 180) != 0) ? pathingTextureTga.getHeight() : pathingTextureTga.getWidth();
		final int divH = ((rotation % 180) != 0) ? pathingTextureTga.getWidth() : pathingTextureTga.getHeight();
		logTextureChange(positionX, positionY, divW, divH);
		for (int j = 0; j < pathingTextureTga.getHeight(); j++) {
			for (int i = 0; i < pathingTextureTga.getWidth(); i++) {
				int x = i;
				int y = j;

				switch (rotation) {
				case 90:
					x = pathingTextureTga.getHeight() - 1 - j;
					y = i;
					break;
				case 180:
					x = pathingTextureTga.getWidth() - 1 - i;
					y = pathingTextureTga.getHeight() - 1 - j;
					break;
				case 270:
					x = j;
					y = pathingTextureTga.getWidth() - 1 - i;
					break;
				}
				// Width and height for centering change if rotation is not divisible by 180
				final int xx = (getCellX(positionX) + x) - (divW / 2);
				final int yy = (getCellY(positionY) + y) - (divH / 2);

				if ((xx < 0) || (xx > (this.pathingGridSizes[0] - 1)) || (yy < 0)
						|| (yy > (this.pathingGridSizes[1] - 1))) {
					continue;
				}

				final int rgb = pathingTextureTga.getRGB(i, pathingTextureTga.getHeight() - 1 - j);
				byte data = 0;
				if ((rgb & 0xFF) > 127) {
					data |= PathingFlags.UNBUILDABLE;
				}
				if (((rgb & 0xFF00) >>> 8) > 127) {
					data |= PathingFlags.UNFLYABLE;
				}
				if (((rgb & 0xFF0000) >>> 16) > 127) {
					data |= PathingFlags.UNWALKABLE | PathingFlags.UNSWIMABLE
							| (blocksVision ? PathingFlags.BLOCKVISION : 0);
				}
				this.dynamicPathingOverlay[(yy * this.pathingGridSizes[0]) + xx] |= data;
			}
		}
	}

	private void refreshOverlay() {
		if (!this.overlayStale) {
			return;
		}
		this.overlayStale = false;
		final int version = this.overlayVersion;
		if (this.removedCount <= INCREMENTAL_REMOVALS) {
			// Few instances left: only the cells they covered are laid down again, from the instances that reach them,
			// so a script that removes and creates one at a time does not lay down the whole overlay for each.
			final int count = this.removedCount;
			this.removedCount = 0;
			for (int r = 0; r < count; r++) {
				for (int y = this.removedCells[(r * 4) + 1]; y <= this.removedCells[(r * 4) + 3]; y++) {
					final int row = y * this.pathingGridSizes[0];
					Arrays.fill(this.dynamicPathingOverlay, row + this.removedCells[r * 4],
							row + this.removedCells[(r * 4) + 2] + 1, (short) 0);
				}
			}
			this.changesSuppressed = true;
			try {
				this.refreshStamp++;
				for (int r = 0; r < count; r++) {
					final int minX = this.removedCells[r * 4];
					final int minY = this.removedCells[(r * 4) + 1];
					final int maxX = this.removedCells[(r * 4) + 2];
					final int maxY = this.removedCells[(r * 4) + 3];
					for (int by = minY >> BLOCK_SHIFT; by <= (maxY >> BLOCK_SHIFT); by++) {
						for (int bx = minX >> BLOCK_SHIFT; bx <= (maxX >> BLOCK_SHIFT); bx++) {
							final List<RemovablePathingMapInstance> inBlock = this.instanceBlocks.get(blockKey(bx, by));
							if (inBlock == null) {
								continue;
							}
							for (int i = 0; i < inBlock.size(); i++) {
								final RemovablePathingMapInstance instance = inBlock.get(i);
								if ((instance.stamp != this.refreshStamp) && instance.reaches(minX, minY, maxX, maxY)) {
									instance.stamp = this.refreshStamp;
									instance.blit();
								}
							}
						}
					}
				}
			}
			finally {
				this.changesSuppressed = false;
				this.overlayVersion = version;
			}
			return;
		}
		this.removedCount = 0;
		Arrays.fill(this.dynamicPathingOverlay, (short) 0);
		// The others are laid down again as they were; the cells that differ were logged when the instance was taken out.
		this.changesSuppressed = true;
		try {
			for (final RemovablePathingMapInstance instance : this.dynamicPathingInstances) {
				instance.blit();
			}
		}
		finally {
			this.changesSuppressed = false;
			this.overlayVersion = version;
		}
	}

	private void noteRemoved(final RemovablePathingMapInstance instance) {
		if (this.removedCount >= INCREMENTAL_REMOVALS) {
			this.removedCount = INCREMENTAL_REMOVALS + 1;
			return;
		}
		final int[] cells = instance.cells();
		final int minX = Math.max(cells[0], 0);
		final int minY = Math.max(cells[1], 0);
		final int maxX = Math.min(cells[2], this.pathingGridSizes[0] - 1);
		final int maxY = Math.min(cells[3], this.pathingGridSizes[1] - 1);
		if ((minX > maxX) || (minY > maxY)) {
			return;
		}
		final int slot = this.removedCount * 4;
		this.removedCells[slot] = minX;
		this.removedCells[slot + 1] = minY;
		this.removedCells[slot + 2] = maxX;
		this.removedCells[slot + 3] = maxY;
		this.removedCount++;
	}

	/** The cells a texture of divW x divH cells centred on a position covers (see the blit), as a logged change. */
	private void logTextureChange(final float positionX, final float positionY, final int divW, final int divH) {
		if (this.changesSuppressed) {
			return;
		}
		final int minX = getCellX(positionX) - (divW / 2);
		final int minY = getCellY(positionY) - (divH / 2);
		logChange(this.overlayVersion, minX, minY, (minX + divW) - 1, (minY + divH) - 1);
	}

	private void logChange(final int version, final int minX, final int minY, final int maxX, final int maxY) {
		final int clippedMinX = Math.max(minX, 0);
		final int clippedMinY = Math.max(minY, 0);
		final int clippedMaxX = Math.min(maxX, this.pathingGridSizes[0] - 1);
		final int clippedMaxY = Math.min(maxY, this.pathingGridSizes[1] - 1);
		if ((clippedMinX > clippedMaxX) || (clippedMinY > clippedMaxY)) {
			return;
		}
		final int slot = this.changesLogged % CHANGE_LOG;
		if (this.changesLogged >= CHANGE_LOG) {
			this.changesDroppedVersion = Math.max(this.changesDroppedVersion, this.changeVersions[slot]);
		}
		this.changeVersions[slot] = version;
		this.changeCells[slot * 4] = clippedMinX;
		this.changeCells[(slot * 4) + 1] = clippedMinY;
		this.changeCells[(slot * 4) + 2] = clippedMaxX;
		this.changeCells[(slot * 4) + 3] = clippedMaxY;
		this.changesLogged++;
	}

	/**
	 * The cells (minX, minY, maxX, maxY of each, written to out) whose pathing the overlay or a terrain flag changed
	 * after the given version. Returns how many rectangles that is, or -1 when out holds too few of them or the log
	 * no longer reaches back to the version.
	 */
	public int collectOverlayChanges(final int sinceVersion, final int[] out) {
		if (sinceVersion < this.changesDroppedVersion) {
			return -1;
		}
		int count = 0;
		final int logged = Math.min(this.changesLogged, CHANGE_LOG);
		for (int i = 0; i < logged; i++) {
			final int slot = (this.changesLogged - logged + i) % CHANGE_LOG;
			if (this.changeVersions[slot] <= sinceVersion) {
				continue;
			}
			if ((count * 4) >= out.length) {
				return -1;
			}
			System.arraycopy(this.changeCells, slot * 4, out, count * 4, 4);
			count++;
		}
		return count;
	}

	public boolean checkPathingTexture(final float positionX, final float positionY, final int rotationInput,
			ArgbImage pathingTextureTga, final EnumSet<CBuildingPathingType> preventPathingTypes,
			final EnumSet<CBuildingPathingType> requirePathingTypes, final CWorldCollision cWorldCollision,
			final CUnit unitToExcludeFromCollisionChecks) {
		return checkPathingTexture(positionX, positionY, rotationInput, pathingTextureTga, preventPathingTypes,
				requirePathingTypes, cWorldCollision, unitToExcludeFromCollisionChecks, null, -1);
	}

	public boolean checkPathingTexture(final float positionX, final float positionY, final int rotationInput,
			ArgbImage pathingTextureTga, final EnumSet<CBuildingPathingType> preventPathingTypes,
			final EnumSet<CBuildingPathingType> requirePathingTypes, final CWorldCollision cWorldCollision,
			final CUnit unitToExcludeFromCollisionChecks, final CSimulation simulation,
			final int ignoredControllerIndex) {
		if (pathingTextureTga == null) {
			pathingTextureTga = BLANK_PATHING;
		}
		final int rotation = (rotationInput + 450) % 360;
		final int divW = ((rotation % 180) != 0) ? pathingTextureTga.getHeight() : pathingTextureTga.getWidth();
		final int divH = ((rotation % 180) != 0) ? pathingTextureTga.getWidth() : pathingTextureTga.getHeight();
		short anyPathingTypesInRegion = 0;
		short pathingTypesFillingRegion = (short) 0xFFFF;
		for (int j = 0; j < pathingTextureTga.getHeight(); j++) {
			for (int i = 0; i < pathingTextureTga.getWidth(); i++) {
				int x = i;
				int y = j;

				switch (rotation) {
				case 90:
					x = pathingTextureTga.getHeight() - 1 - j;
					y = i;
					break;
				case 180:
					x = pathingTextureTga.getWidth() - 1 - i;
					y = pathingTextureTga.getHeight() - 1 - j;
					break;
				case 270:
					x = j;
					y = pathingTextureTga.getWidth() - 1 - i;
					break;
				}
				// Width and height for centering change if rotation is not divisible by 180
				final int xx = (getCellX(positionX) + x) - (divW / 2);
				final int yy = (getCellY(positionY) + y) - (divH / 2);

				if ((xx < 0) || (xx > (this.pathingGridSizes[0] - 1)) || (yy < 0)
						|| (yy > (this.pathingGridSizes[1] - 1))) {
					continue;
				}

				final short cellPathing = getCellPathing(xx, yy);
				anyPathingTypesInRegion |= cellPathing;
				pathingTypesFillingRegion &= cellPathing;
			}
		}
		final float width = pathingTextureTga.getWidth() * 32f;
		final float height = pathingTextureTga.getHeight() * 32f;
		final float offsetX = ((pathingTextureTga.getWidth() % 2) == 1) ? 16f : 0f;
		final float offsetY = ((pathingTextureTga.getHeight() % 2) == 1) ? 16f : 0f;
		final Rectangle pathingMapRectangle = new Rectangle((positionX - (width / 2)) + offsetX,
				(positionY - (height / 2)) + offsetY, width, height);
		anyPathingTypesInRegion |= unitPlacementPathing(pathingMapRectangle, cWorldCollision,
				unitToExcludeFromCollisionChecks, simulation, ignoredControllerIndex);
		for (final CBuildingPathingType pathingType : preventPathingTypes) {
			if (PathingFlags.isPathingFlag(anyPathingTypesInRegion, pathingType)) {
				return false;
			}
		}
		for (final CBuildingPathingType pathingType : requirePathingTypes) {
			if (!PathingFlags.isPathingFlag(pathingTypesFillingRegion, pathingType)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The pathing flags that the units in the area add for a structure placed over it. Units that the player
	 * ignoredControllerIndex controls step aside and add none.
	 */
	public static short unitPlacementPathing(final Rectangle area, final CWorldCollision cWorldCollision,
			final CUnit unitToExclude, final CSimulation simulation, final int ignoredControllerIndex) {
		short pathing = 0;
		if (cWorldCollision.intersectsAnythingOtherThan(area, unitToExclude, null, MovementType.AMPHIBIOUS, true,
				simulation, ignoredControllerIndex)) {
			pathing |= PathingFlags.UNBUILDABLE | PathingFlags.UNWALKABLE | PathingFlags.UNSWIMABLE;
		}
		if (cWorldCollision.intersectsAnythingOtherThan(area, unitToExclude, null, MovementType.FLOAT, true,
				simulation, ignoredControllerIndex)) {
			pathing |= PathingFlags.UNSWIMABLE;
		}
		if (cWorldCollision.intersectsAnythingOtherThan(area, unitToExclude, null, MovementType.FLY, true, simulation,
				ignoredControllerIndex)) {
			pathing |= PathingFlags.UNFLYABLE;
		}
		if (cWorldCollision.intersectsAnythingOtherThan(area, unitToExclude, null, MovementType.FOOT, true, simulation,
				ignoredControllerIndex)) {
			pathing |= PathingFlags.UNBUILDABLE | PathingFlags.UNWALKABLE;
		}
		return pathing;
	}

	public RemovablePathingMapInstance blitRemovablePathingOverlayTexture(final float positionX, final float positionY,
			final int rotationInput, final ArgbImage pathingTextureTga) {
		final RemovablePathingMapInstance removablePathingMapInstance = new RemovablePathingMapInstance(positionX,
				positionY, rotationInput, pathingTextureTga);
		removablePathingMapInstance.add();
		return removablePathingMapInstance;
	}

	public RemovablePathingMapInstance createRemovablePathingOverlayTexture(final float positionX,
			final float positionY, final int rotationInput, final ArgbImage pathingTextureTga) {
		return new RemovablePathingMapInstance(positionX, positionY, rotationInput, pathingTextureTga);
	}

	/** Changes whenever buildings or destructables add or remove pathing. */
	public int getOverlayVersion() {
		return this.overlayVersion;
	}

	public int getWidth() {
		return this.pathingGridSizes[0];
	}

	public int getHeight() {
		return this.pathingGridSizes[1];
	}

	public boolean contains(final float x, final float y) {
		final int cellX = getCellX(x);
		final int cellY = getCellY(y);
		return (cellX >= 0) && (cellY >= 0) && (cellX < this.pathingGridSizes[0]) && (cellY < this.pathingGridSizes[1]);
	}

	public short getPathing(final float x, final float y) {
		return getCellPathing(getCellX(x), getCellY(y));
	}

	public short getTerrainPathing(final float x, final float y) {
		return getCellPermanentPathing(getCellX(x), getCellY(y));
	}

	public int getCellX(final float x) {
		return floorCell((x - this.centerOffset[0]) / 32.0f);
	}

	/** Floor, so the cell column left of the map is -1 and not 0; equal to a plain cast for in-map positions. */
	private static int floorCell(final float userCellSpace) {
		return (userCellSpace < 0f) ? (int) Math.floor(userCellSpace) : (int) userCellSpace;
	}

	public int getCellY(final float y) {
		return floorCell((y - this.centerOffset[1]) / 32.0f);
	}

	public float getWorldX(final int cellX) {
		return (cellX * 32f) + this.centerOffset[0] + 16f;
	}

	public float getWorldY(final int cellY) {
		return (cellY * 32f) + this.centerOffset[1] + 16f;
	}

	public float getWorldXFromCorner(final int cornerX) {
		return (cornerX * 32f) + this.centerOffset[0];
	}

	public float getWorldYFromCorner(final int cornerY) {
		return (cornerY * 32f) + this.centerOffset[1];
	}

	public int getCornerX(final float x) {
		final float userCellSpaceX = ((x + 16f) - this.centerOffset[0]) / 32.0f;
		final int cellX = (int) userCellSpaceX;
		return cellX;
	}

	public int getCornerY(final float y) {
		final float userCellSpaceY = ((y + 16f) - this.centerOffset[1]) / 32.0f;
		final int cellY = (int) userCellSpaceY;
		return cellY;
	}

	/** What a cell outside the map reads as: the map boundary stops every kind of movement and building. */
	private static final short OUTSIDE_PATHING = PathingFlags.UNWALKABLE | PathingFlags.UNFLYABLE
			| PathingFlags.UNBUILDABLE | PathingFlags.UNSWIMABLE;

	public short getCellPathing(final int cellX, final int cellY) {
		if (!isCellInside(cellX, cellY)) {
			return OUTSIDE_PATHING;
		}
		refreshOverlay();
		final int index = (cellY * this.pathingGridSizes[0]) + cellX;
		return (short) (this.pathingGrid[index] | this.dynamicPathingOverlay[index]);
	}

	private short getCellPermanentPathing(final int cellX, final int cellY) {
		if (!isCellInside(cellX, cellY)) {
			return OUTSIDE_PATHING;
		}
		final int index = (cellY * this.pathingGridSizes[0]) + cellX;
		return (this.pathingGrid[index]);
	}

	public void setCellPathing(final int cellX, final int cellY, final short pathingValue) {
		if (!isCellInside(cellX, cellY)) {
			return;
		}
		final int index = (cellY * this.pathingGridSizes[0]) + cellX;
		this.pathingGrid[index] = pathingValue;
	}

	public void setCellBlighted(final int cellX, final int cellY, final boolean blighted) {
		if (blighted) {
			setCellPathing(cellX, cellY, (short) (getCellPermanentPathing(cellX, cellY) | PathingFlags.BLIGHTED));
		}
		else {
			setCellPathing(cellX, cellY,
					(short) (getCellPermanentPathing(cellX, cellY) & (short) ~PathingFlags.BLIGHTED));
		}
	}

	/**
	 * SetTerrainPathable: one pathing cell of the map's own terrain gets a flag set or cleared. Peon harvest and
	 * amphibious pathing have no flag of their own in the map file, so scripts keep theirs beside the grid.
	 */
	public void setTerrainFlag(final float x, final float y, final short flag, final boolean set) {
		final int cellX = getCellX(x);
		final int cellY = getCellY(y);
		if (!isCellInside(cellX, cellY)) {
			return;
		}
		if ((flag == PathingFlags.UNWALKABLE) || (flag == PathingFlags.UNSWIMABLE)) {
			// Peon harvest and amphibious pathing keep what the map had when the cell's walking or swimming changes.
			final short scripted = getScriptFlags(x, y);
			final short pathing = this.pathingGrid[(cellY * this.pathingGridSizes[0]) + cellX];
			final boolean unwalkable = PathingFlags.isPathingFlag(pathing, PathingFlags.UNWALKABLE);
			if ((scripted & (SCRIPT_PEON_BLOCKED | SCRIPT_PEON_OPEN)) == 0) {
				setScriptFlag(x, y, SCRIPT_PEON_BLOCKED, SCRIPT_PEON_OPEN, unwalkable);
			}
			if ((scripted & (SCRIPT_AMPHIBIOUS_BLOCKED | SCRIPT_AMPHIBIOUS_OPEN)) == 0) {
				setScriptFlag(x, y, SCRIPT_AMPHIBIOUS_BLOCKED, SCRIPT_AMPHIBIOUS_OPEN,
						unwalkable && PathingFlags.isPathingFlag(pathing, PathingFlags.UNSWIMABLE));
			}
		}
		final int index = (cellY * this.pathingGridSizes[0]) + cellX;
		this.pathingGrid[index] = (short) (set ? (this.pathingGrid[index] | flag) : (this.pathingGrid[index] & ~flag));
		this.overlayVersion++;
		logChange(this.overlayVersion, cellX, cellY, cellX, cellY);
	}

	public void setScriptFlag(final float x, final float y, final short onFlag, final short offFlag,
			final boolean on) {
		final int cellX = getCellX(x);
		final int cellY = getCellY(y);
		if (!isCellInside(cellX, cellY)) {
			return;
		}
		if (this.scriptFlags == null) {
			this.scriptFlags = new short[this.pathingGrid.length];
		}
		final int index = (cellY * this.pathingGridSizes[0]) + cellX;
		this.scriptFlags[index] = (short) ((this.scriptFlags[index] & ~(onFlag | offFlag)) | (on ? onFlag : offFlag));
	}

	/** Whether a script has changed the walkability of a pathing cell within the given cells of a point. */
	public boolean isTerrainScripted(final float x, final float y, final int cells) {
		if (this.scriptFlags == null) {
			return false;
		}
		final int centerX = getCellX(x);
		final int centerY = getCellY(y);
		for (int cellY = centerY - cells; cellY <= (centerY + cells); cellY++) {
			for (int cellX = centerX - cells; cellX <= (centerX + cells); cellX++) {
				if (isCellInside(cellX, cellY) && ((this.scriptFlags[(cellY * this.pathingGridSizes[0])
						+ cellX] & (SCRIPT_PEON_BLOCKED | SCRIPT_PEON_OPEN)) != 0)) {
					return true;
				}
			}
		}
		return false;
	}

	/** The peon harvest and amphibious flags scripts set on the cell, as {@link #SCRIPT_PEON_BLOCKED} and the like. */
	public short getScriptFlags(final float x, final float y) {
		final int cellX = getCellX(x);
		final int cellY = getCellY(y);
		if ((this.scriptFlags == null) || !isCellInside(cellX, cellY)) {
			return 0;
		}
		return this.scriptFlags[(cellY * this.pathingGridSizes[0]) + cellX];
	}

	private boolean isCellInside(final int cellX, final int cellY) {
		return (cellX >= 0) && (cellY >= 0) && (cellX < this.pathingGridSizes[0])
				&& (cellY < this.pathingGridSizes[1]);
	}

	public void setBlighted(final float x, final float y, final boolean blighted) {
		setCellBlighted(getCellX(x), getCellY(y), blighted);
	}

	public boolean isPathable(final float x, final float y, final PathingType pathingType) {
		return !PathingFlags.isPathingFlag(getPathing(x, y), pathingType.preventionFlag);
	}

	public boolean isPathable(final float x, final float y, final MovementType pathingType) {
		return pathingType.isPathable(getPathing(x, y));
	}

	/**
	 * Measured: a unit stands on a square of n x n pathing cells, n = floor(collision / 16) + 1 (collision 16 and 31:
	 * 2, 32: 3, 48: 4), laid out from the cell that holds its position: that cell and (n - 1) / 2 cells toward +x and
	 * +y, n / 2 cells toward -x and -y. A Footman can stand right below or left of a wall, but keeps a cell above or
	 * right of it; with an odd n the square is centred on the cell.
	 */
	public static int getFootprintCells(final float collisionSize) {
		return collisionSize == 0f ? 1 : (int) (collisionSize / 16f) + 1;
	}

	/** The first (lowest) footprint column of a unit at x; see getFootprintCells. */
	public int getFootprintCellX(final float x, final int footprintCells) {
		return (int) Math.floor((x - this.centerOffset[0]) / 32.0f) - (footprintCells / 2);
	}

	public int getFootprintCellY(final float y, final int footprintCells) {
		return (int) Math.floor((y - this.centerOffset[1]) / 32.0f) - (footprintCells / 2);
	}

	/**
	 * The middle of the footprint of a unit at x: the lower left corner of its cell for an even number of cells, the
	 * cell centre for an odd one.
	 */
	public float getFootprintCenterX(final float x, final float collisionSize) {
		final int cells = getFootprintCells(collisionSize);
		return (getFootprintCellX(x, cells) * 32f) + this.centerOffset[0] + (cells * 16f);
	}

	public float getFootprintCenterY(final float y, final float collisionSize) {
		final int cells = getFootprintCells(collisionSize);
		return (getFootprintCellY(y, cells) * 32f) + this.centerOffset[1] + (cells * 16f);
	}

	public boolean isPathable(final float unitX, final float unitY, final MovementType pathingType,
			final float collisionSize) {
		if (collisionSize == 0f) {
			if (!contains(unitX, unitY)) {
				return false;
			}
			return pathingType.isPathable(getPathing(unitX, unitY));
		}
		final int cells = getFootprintCells(collisionSize);
		final int firstX = getFootprintCellX(unitX, cells);
		final int firstY = getFootprintCellY(unitY, cells);
		if ((firstX < 0) || (firstY < 0) || ((firstX + cells) > this.pathingGridSizes[0])
				|| ((firstY + cells) > this.pathingGridSizes[1])) {
			return false;
		}
		// The footprint is inside the map, as checked above.
		final short[] terrain = this.pathingGrid;
		refreshOverlay();
		final short[] overlay = this.dynamicPathingOverlay;
		final int width = this.pathingGridSizes[0];
		for (int cellY = firstY; cellY < (firstY + cells); cellY++) {
			int index = (cellY * width) + firstX;
			for (int cellX = 0; cellX < cells; cellX++, index++) {
				if (!pathingType.isPathable((short) (terrain[index] | overlay[index]))) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * A step from a point where the unit already overlaps unwalkable cells (1.29 places a unit at the centre of a
	 * cell whose corner fits, so a wide unit can stand partly in trees) is allowed when it overlaps no cell it did not
	 * already overlap; otherwise such a unit could never walk out.
	 */
	public boolean isPathableFrom(final float fromX, final float fromY, final float unitX, final float unitY,
			final MovementType pathingType, final float collisionSize) {
		if (isPathable(unitX, unitY, pathingType, collisionSize)) {
			return true;
		}
		if ((collisionSize == 0f) || isPathable(fromX, fromY, pathingType, collisionSize)) {
			return false;
		}
		final int cells = getFootprintCells(collisionSize);
		final int firstX = getFootprintCellX(unitX, cells);
		final int firstY = getFootprintCellY(unitY, cells);
		if ((firstX < 0) || (firstY < 0) || ((firstX + cells) > this.pathingGridSizes[0])
				|| ((firstY + cells) > this.pathingGridSizes[1])) {
			return false;
		}
		final int fromFirstX = getFootprintCellX(fromX, cells);
		final int fromFirstY = getFootprintCellY(fromY, cells);
		for (int cellY = firstY; cellY < (firstY + cells); cellY++) {
			for (int cellX = firstX; cellX < (firstX + cells); cellX++) {
				if (!pathingType.isPathable(getCellPathing(cellX, cellY))
						&& ((cellX < fromFirstX) || (cellX >= (fromFirstX + cells)) || (cellY < fromFirstY)
								|| (cellY >= (fromFirstY + cells)))) {
					return false;
				}
			}
		}
		return true;
	}

	public boolean isCellPathable(final int x, final int y, final MovementType pathingType, final float collisionSize) {
		return isPathable(getWorldX(x), getWorldY(y), pathingType, collisionSize);
	}

	public boolean isCellPathable(final int x, final int y, final MovementType pathingType) {
		return pathingType.isPathable(getCellPathing(x, y));
	}

	public static boolean isPathingFlag(final short pathingValue, final PathingType pathingType) {
		return !PathingFlags.isPathingFlag(pathingValue, pathingType.preventionFlag);
	}

	// movetp referring to the unit data field of the same name
	public static MovementType getMovementType(final String movetp) {
		return movetpToMovementType.get(movetp);
	}

	public int getFogOfWarIndexX(final float x) {
		// A fog cell spans the tile between two terrain corners (measured on 1.29), like the terrain height lookups.
		final float userCellSpaceX = (x - this.centerOffset[0]) / (32f * CPlayerFogOfWar.PATHING_RATIO);
		final int cellX = (int) userCellSpaceX;
		return cellX;
	}

	public int getFogOfWarIndexY(final float y) {
		final float userCellSpaceY = (y - this.centerOffset[1]) / (32f * CPlayerFogOfWar.PATHING_RATIO);
		final int cellY = (int) userCellSpaceY;
		return cellY;
	}

	public float getFogOfWarSampleX(final int indexX) {
		return this.centerOffset[0] + (indexX * 32f * CPlayerFogOfWar.PATHING_RATIO) + 16f;
	}

	public float getFogOfWarSampleY(final int indexY) {
		return this.centerOffset[1] + (indexY * 32f * CPlayerFogOfWar.PATHING_RATIO) + 16f;
	}

	public static int getFogOfWarDistance(final float d) {
		return getFogOfWarDistance(d, false);
	}

	public static int getFogOfWarDistance(final float d, boolean roundUp) {
		final float userCellSpace = ((d + (16f * CPlayerFogOfWar.PATHING_RATIO)))
				/ (32f * CPlayerFogOfWar.PATHING_RATIO);
		if (roundUp) {
			return (int) (Math.ceil(userCellSpace) + 0.1);
		}
		else {
			return (int) userCellSpace;
		}
	}

	public boolean isCellBlockVision(final int cellX, final int cellY) {
		if (!isCellInside(cellX, cellY)) {
			return true;
		}
		refreshOverlay();
		final int index = (cellY * this.pathingGridSizes[0]) + cellX;
		return PathingFlags.isPathingFlag(this.dynamicPathingOverlay[index], PathingFlags.BLOCKVISION);
	}

	public boolean isBlockVision(final float x, final float y) {
		return isCellBlockVision(getCellX(x), getCellY(y));
	}

	public static final class PathingFlags {
		public static final short UNWALKABLE = 0x2;
		public static final short UNFLYABLE = 0x4;
		public static final short UNBUILDABLE = 0x8;
		public static final short BLOCKVISION = 0x10;
		public static final short BLIGHTED = 0x20;
		public static final short UNSWIMABLE = 0x40; // PROBABLY, didn't confirm this flag value is accurate
		public static final short BOUDNARY = 0xF0;

		public static boolean isPathingFlag(final short pathingValue, final int flag) {
			return (pathingValue & flag) != 0;
		}

		public static boolean isPathingFlag(final short pathingValue, final CBuildingPathingType pathingType) {
			switch (pathingType) {
			case BLIGHTED:
				return PathingFlags.isPathingFlag(pathingValue, PathingFlags.BLIGHTED);
			case UNAMPH:
				return PathingFlags.isPathingFlag(pathingValue, PathingFlags.UNWALKABLE)
						&& PathingFlags.isPathingFlag(pathingValue, PathingFlags.UNSWIMABLE);
			case UNBUILDABLE:
				return PathingFlags.isPathingFlag(pathingValue, PathingFlags.UNBUILDABLE);
			case UNFLOAT:
				return PathingFlags.isPathingFlag(pathingValue, PathingFlags.UNSWIMABLE);
			case UNFLYABLE:
				return PathingFlags.isPathingFlag(pathingValue, PathingFlags.UNFLYABLE);
			case UNWALKABLE:
				return PathingFlags.isPathingFlag(pathingValue, PathingFlags.UNWALKABLE);
			case BLOCKVISION:
				return PathingFlags.isPathingFlag(pathingValue, PathingFlags.BLOCKVISION);
			default:
				return false;
			}
		}

		private PathingFlags() {
		}
	}

	public static enum MovementType {
		FOOT("foot", PathingFlags.UNWALKABLE, false),
		FOOT_NO_COLLISION("", PathingFlags.UNWALKABLE, false),
		HORSE("horse", PathingFlags.UNWALKABLE, false),
		FLY("fly", PathingFlags.UNFLYABLE, false),
		HOVER("hover", PathingFlags.UNWALKABLE, false),
		FLOAT("float", PathingFlags.UNSWIMABLE, false),
		AMPHIBIOUS("amph", PathingFlags.UNWALKABLE | PathingFlags.UNSWIMABLE, true),
		DISABLED("", 0, false);

		private final String typeKey;
		private final int blockingFlags;
		private final boolean blockedOnlyByAll;

		// TODO windwalk pathing type can walk through units but not through items

		/**
		 * @param blockedOnlyByAll whether the type is blocked only where every one of the flags is set, rather than
		 *                         where any is
		 */
		private MovementType(final String typeKey, final int blockingFlags, final boolean blockedOnlyByAll) {
			this.typeKey = typeKey;
			this.blockingFlags = blockingFlags;
			this.blockedOnlyByAll = blockedOnlyByAll;
		}

		public boolean isPathable(final short pathingValue) {
			final int blocking = pathingValue & this.blockingFlags;
			return this.blockedOnlyByAll ? (blocking != this.blockingFlags) : (blocking == 0);
		}
	}

	public static enum PathingType {
		WALKABLE(PathingFlags.UNWALKABLE),
		FLYABLE(PathingFlags.UNFLYABLE),
		BUILDABLE(PathingFlags.UNBUILDABLE),
		SWIMMABLE(PathingFlags.UNSWIMABLE);

		private final int preventionFlag;

		private PathingType(final int preventionFlag) {
			this.preventionFlag = preventionFlag;
		}
	}

	public final class RemovablePathingMapInstance {
		private final float positionX;
		private final float positionY;
		private final int rotationInput;
		private final ArgbImage pathingTextureTga;
		private boolean blocksVision = false;
		private boolean added = false;

		public RemovablePathingMapInstance(final float positionX, final float positionY, final int rotationInput,
				final ArgbImage pathingTextureTga) {
			this.positionX = positionX;
			this.positionY = positionY;
			this.rotationInput = rotationInput;
			this.pathingTextureTga = pathingTextureTga;
		}

		private int stamp;

		private int[] cells() {
			final boolean turned = (((this.rotationInput + 450) % 360) % 180) != 0;
			final int w = turned ? this.pathingTextureTga.getHeight() : this.pathingTextureTga.getWidth();
			final int h = turned ? this.pathingTextureTga.getWidth() : this.pathingTextureTga.getHeight();
			final int minX = getCellX(this.positionX) - (w / 2);
			final int minY = getCellY(this.positionY) - (h / 2);
			return new int[] { minX, minY, (minX + w) - 1, (minY + h) - 1 };
		}

		private boolean reaches(final int minX, final int minY, final int maxX, final int maxY) {
			final int[] own = cells();
			return (own[0] <= maxX) && (own[2] >= minX) && (own[1] <= maxY) && (own[3] >= minY);
		}

		private void blit() {
			blitPathingOverlayTexture(this.positionX, this.positionY, this.rotationInput, this.pathingTextureTga,
					this.blocksVision);
		}

		public void remove() {
			if (!this.added) {
				return;
			}
			this.added = false;
			PathingGrid.this.dynamicPathingInstances.remove(this);
			PathingGrid.this.indexInstance(this, false);
			PathingGrid.this.overlayStale = true;
			PathingGrid.this.overlayVersion++;
			PathingGrid.this.noteRemoved(this);
			final boolean turned = (((this.rotationInput + 450) % 360) % 180) != 0;
			PathingGrid.this.logTextureChange(this.positionX, this.positionY,
					turned ? this.pathingTextureTga.getHeight() : this.pathingTextureTga.getWidth(),
					turned ? this.pathingTextureTga.getWidth() : this.pathingTextureTga.getHeight());
		}

		public void add() {
			if (this.added) {
				return;
			}
			this.added = true;
			PathingGrid.this.dynamicPathingInstances.add(this);
			PathingGrid.this.indexInstance(this, true);
			blit();
		}

		public int getRotationInput() {
			return this.rotationInput;
		}

		public void setBlocksVision(boolean flag) {
			this.blocksVision = flag;
			// An instance that is not in the grid (a dead destructable's live pathing) must not leave cells behind.
			if (this.added) {
				blit();
			}
		}
	}
}
