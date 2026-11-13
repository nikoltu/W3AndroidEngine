package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CFogMaskSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

public class CPlayerFogOfWar {
	public static final int PATHING_RATIO = 4;
	public static final int GRID_STEP = PATHING_RATIO * 32;

	// Unit sight on 1.29 (measured for 800..1800): the radius is rounded to whole cells, and along each row or column
	// the reach sqrt(r^2 - near^2) is rounded too, so the shape is wider than a plain circle of that radius.
	public static int getSightCells(final float sightRadius) {
		return (int) Math.floor((sightRadius / GRID_STEP) + 0.5f);
	}

	public static boolean isInSight(final int dx, final int dy, final int sightCells) {
		final int far = Math.max(Math.abs(dx), Math.abs(dy));
		final int near = Math.min(Math.abs(dx), Math.abs(dy));
		if (far > sightCells) {
			return false;
		}
		return Math.floor(Math.sqrt((sightCells * sightCells) - (near * near)) + 0.5) >= far;
	}
	private final int width;
	private final int height;
	// Plain arrays: a get or put per cell on a direct buffer is slow on Android, and vision checks touch every cell
	// in sight of every unit each second.
	private final byte[] fogOfWar;
	private final byte[] detection;
	private final ByteBuffer fogOfWarBuffer;

	public CPlayerFogOfWar(final PathingGrid pathingGrid) {
		this.width = (pathingGrid.getWidth() / PATHING_RATIO) + 1;
		this.height = (pathingGrid.getHeight() / PATHING_RATIO) + 1;
		final int fogOfWarBufferLen = this.width * this.height;
		this.fogOfWar = new byte[fogOfWarBufferLen];
		this.detection = new byte[fogOfWarBufferLen];
		Arrays.fill(this.fogOfWar, (byte) -1);
		this.fogOfWarBuffer = ByteBuffer.wrap(this.fogOfWar);
	}

	public void revealAll() {
		Arrays.fill(this.fogOfWar, (byte) 0);
	}

	/**
	 * Each cell as the player who sees it best has it (visible before fogged before masked): the sight of several
	 * players together, which only a picture uses.
	 */
	public void showMostVisibleOf(final List<CPlayerFogOfWar> sources) {
		Arrays.fill(this.fogOfWar, (byte) -1);
		for (final CPlayerFogOfWar source : sources) {
			final byte[] cells = source.fogOfWar;
			for (int i = 0, n = Math.min(cells.length, this.fogOfWar.length); i < n; i++) {
				if ((cells[i] & 0xFF) < (this.fogOfWar[i] & 0xFF)) {
					this.fogOfWar[i] = cells[i];
				}
			}
		}
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public ByteBuffer getFogOfWarBuffer() {
		return this.fogOfWarBuffer;
	}

	private byte getState(CFogMaskSettings fogMaskSettings, final PathingGrid pathingGrid, final float x,
			final float y) {
		final int indexX = pathingGrid.getFogOfWarIndexX(x);
		final int indexY = pathingGrid.getFogOfWarIndexY(y);
		return getState(fogMaskSettings, indexX, indexY);
	}

	private byte getState(CFogMaskSettings fogMaskSettings, final int indexX, final int indexY) {
		final int index = (indexY * getWidth()) + indexX;
		if ((index >= 0) && (index < this.fogOfWar.length)) {
			return fogMaskSettings.getFogStateFromSettings(this.fogOfWar[index]);
		}
		return 0;
	}

	private byte getDetectionState(final int indexX, final int indexY) {
		final int index = (indexY * getWidth()) + indexX;
		if ((index >= 0) && (index < this.detection.length)) {
			return this.detection[index];
		}
		return 0;
	}

	public CFogState getFogState(CFogMaskSettings fogMaskSettings, final PathingGrid pathingGrid, final float x,
			final float y) {
		return CFogState.getByMask(getState(fogMaskSettings, pathingGrid, x, y));
	}

	public CFogState getFogState(CFogMaskSettings fogMaskSettings, final int indexX, final int indexY) {
		return CFogState.getByMask(getState(fogMaskSettings, indexX, indexY));
	}

	public boolean isVisible(CFogMaskSettings fogMaskSettings, final PathingGrid pathingGrid, final float x,
			final float y) {
		return getState(fogMaskSettings, pathingGrid, x, y) == 0;
	}

	public boolean isVisible(CFogMaskSettings fogMaskSettings, final int indexX, final int indexY) {
		return getState(fogMaskSettings, indexX, indexY) == 0;
	}

	public boolean isDetecting(CFogMaskSettings fogMaskSettings, PathingGrid pathingGrid, float x, float y,
			byte invisLevels) {
		final int indexX = pathingGrid.getFogOfWarIndexX(x);
		final int indexY = pathingGrid.getFogOfWarIndexY(y);
		return isVisible(fogMaskSettings, indexX, indexY)
				&& ((getDetectionState(indexX, indexY) & invisLevels) == invisLevels);
	}

	public boolean isDetecting(CFogMaskSettings fogMaskSettings, final int indexX, final int indexY, byte invisLevels) {
		return isVisible(fogMaskSettings, indexX, indexY)
				&& ((getDetectionState(indexX, indexY) & invisLevels) == invisLevels);
	}

	private void setState(final PathingGrid pathingGrid, final float x, final float y, final byte fogOfWarState) {
		final int indexX = pathingGrid.getFogOfWarIndexX(x);
		final int indexY = pathingGrid.getFogOfWarIndexY(y);
		setState(indexX, indexY, fogOfWarState);
	}

	private void setState(final int indexX, final int indexY, final byte fogOfWarState) {
		final int writeIndex = (indexY * getWidth()) + indexX;
		if ((writeIndex >= 0) && (writeIndex < this.fogOfWar.length)) {
			if (this.privateCells != null) {
				// A cell that is visible only through a modifier made without shared vision stays out of what
				// allies get; a cell something else already shows stays shared.
				if (this.writingPrivate && (fogOfWarState == 0)) {
					if (this.fogOfWar[writeIndex] != 0) {
						this.privateCells[writeIndex] = 1;
						this.anyPrivateCells = true;
					}
				}
				else {
					this.privateCells[writeIndex] = 0;
				}
			}
			this.fogOfWar[writeIndex] = fogOfWarState;
		}
	}

	private byte[] privateCells;
	private boolean writingPrivate;
	private boolean anyPrivateCells;

	/** Cells set from now on by a modifier made without shared vision (until set false). */
	public void setPrivateWrite(final boolean privateWrite) {
		if (privateWrite && (this.privateCells == null)) {
			this.privateCells = new byte[this.fogOfWar.length];
		}
		this.writingPrivate = privateWrite;
	}

	public void setVisible(final PathingGrid pathingGrid, final float x, final float y, final CFogState fogOfWarState) {
		setState(pathingGrid, x, y, fogOfWarState.getMask());
	}

	public void setVisible(final int indexX, final int indexY, final CFogState fogOfWarState) {
		setState(indexX, indexY, fogOfWarState.getMask());
	}

	public void setFogStateRect(PathingGrid pathingGrid, Rectangle rectangle, CFogState fogOfWarState) {
		// Measured on 1.29: the cells from the one holding the minimum edge up to, but not including, the one holding
		// the maximum edge; a rect inside one cell covers nothing.
		final int xMin = pathingGrid.getFogOfWarIndexX(rectangle.x);
		final int yMin = pathingGrid.getFogOfWarIndexY(rectangle.y);
		final int xMax = pathingGrid.getFogOfWarIndexX(rectangle.x + rectangle.width) - 1;
		final int yMax = pathingGrid.getFogOfWarIndexY(rectangle.y + rectangle.height) - 1;
		for (int i = xMin; i <= xMax; i += 1) {
			for (int j = yMin; j <= yMax; j += 1) {
				setVisible(i, j, fogOfWarState);
			}
		}
	}

	public void setDetecting(final PathingGrid pathingGrid, final float x, final float y, final CFogState fogOfWarState,
			byte detectLevels) {
		final int indexX = pathingGrid.getFogOfWarIndexX(x);
		final int indexY = pathingGrid.getFogOfWarIndexY(y);
		setDetecting(indexX, indexY, fogOfWarState, detectLevels);
	}

	public void setDetecting(final int indexX, final int indexY, final CFogState fogOfWarState, byte detectLevels) {
		final int writeIndex = (indexY * getWidth()) + indexX;
		if ((writeIndex >= 0) && (writeIndex < this.fogOfWar.length)) {
			this.fogOfWar[writeIndex] = fogOfWarState.getMask();
			this.detection[writeIndex] |= detectLevels;
			if (this.privateCells != null) {
				this.privateCells[writeIndex] = 0;
			}
		}
	}

	// A cell another unit already made visible cannot change by being seen again (unless detection is added), and
	// units of one player mostly stand within each other's sight, so the terrain and line of sight checks are skipped.
	private boolean isAlreadyVisible(final int indexX, final int indexY) {
		final int index = (indexY * this.width) + indexX;
		return (index >= 0) && (index < this.fogOfWar.length) && (this.fogOfWar[index] == 0)
				&& ((this.privateCells == null) || (this.privateCells[index] == 0));
	}

	public void checkCardinalVision(final CSimulation game, final PathingGrid pathingGrid, final boolean flying,
			final int dx, final int dy, final int dxp, final int dyp, final float dxf, final float dyf,
			final float dxfp, final float dyfp, final int myZ) {
		if (isAlreadyVisible(dx, dy)) {
			return;
		}
		if ((flying || game.isTerrainWater(dxf, dyf) || (myZ > game.getTerrainHeight(dxf, dyf))
				|| (!game.isTerrainRomp(dxf, dyf) && (myZ == game.getTerrainHeight(dxf, dyf))))
				&& (flying || !pathingGrid.isBlockVision(dxfp, dyfp)) && this.isVisible(game, dxp, dyp)) {
			this.setVisible(dx, dy, CFogState.VISIBLE);
		}
	}

	// The fog cell the viewing unit stands in: its own sight blockers do not hide what lies beyond it (1.29 sees out of a
	// tree line it stands against), only the blockers of the cells the sight passes through.
	private int viewerX = Integer.MIN_VALUE;
	private int viewerY = Integer.MIN_VALUE;

	public void setViewerCell(final int indexX, final int indexY) {
		this.viewerX = indexX;
		this.viewerY = indexY;
	}

	public void clearViewerCell() {
		this.viewerX = Integer.MIN_VALUE;
		this.viewerY = Integer.MIN_VALUE;
	}

	private boolean blocksSightFrom(final PathingGrid pathingGrid, final int indexX, final int indexY, final float x,
			final float y) {
		return ((indexX != this.viewerX) || (indexY != this.viewerY)) && pathingGrid.isBlockVision(x, y);
	}

	public void checkCardinalVision(final CSimulation game, final PathingGrid pathingGrid, final byte detections,
			final boolean flying, final int dx, final int dy, final int dxp, final int dyp, final float dxf,
			final float dyf, final float dxfp, final float dyfp, final int myZ) {
		if ((detections == 0) && isAlreadyVisible(dx, dy)) {
			return;
		}
		if ((flying || game.isTerrainWater(dxf, dyf) || (myZ > game.getTerrainHeight(dxf, dyf))
				|| (!game.isTerrainRomp(dxf, dyf) && (myZ == game.getTerrainHeight(dxf, dyf))))
				&& (flying || !blocksSightFrom(pathingGrid, dxp, dyp, dxfp, dyfp)) && this.isVisible(game, dxp, dyp)) {
			this.setDetecting(dx, dy, CFogState.VISIBLE, detections);
		}
	}

	public void checkDiagonalVision(final CSimulation game, final PathingGrid pathingGrid, final boolean flying,
			final int x, final int y, final int dx, final int dy, final int dxp, final int dyp, final float dxf,
			final float dyf, final float dxfp, final float dyfp, final int myZ) {
		if (isAlreadyVisible(dx, dy)) {
			return;
		}
		if ((flying || game.isTerrainWater(dxf, dyf) || (myZ > game.getTerrainHeight(dxf, dyf))
				|| (!game.isTerrainRomp(dxf, dyf) && (myZ == game.getTerrainHeight(dxf, dyf))))
				&& (flying || !blocksSightFrom(pathingGrid, dxp, dyp, dxfp, dyfp)) && this.isVisible(game, dxp, dyp)
				&& ((x == y)
						|| ((x > y) && this.isVisible(game, dxp, dy)
								&& (flying || !pathingGrid.isBlockVision(dxfp, dyf)))
						|| ((x < y) && this.isVisible(game, dx, dyp)
								&& (flying || !pathingGrid.isBlockVision(dxf, dyfp))))) {
			this.setVisible(dx, dy, CFogState.VISIBLE);
		}
	}

	public void checkDiagonalVision(final CSimulation game, final PathingGrid pathingGrid, final byte detections,
			final boolean flying, final int x, final int y, final int dx, final int dy, final int dxp, final int dyp,
			final float dxf, final float dyf, final float dxfp, final float dyfp, final int myZ) {
		if ((detections == 0) && isAlreadyVisible(dx, dy)) {
			return;
		}
		if ((flying || game.isTerrainWater(dxf, dyf) || (myZ > game.getTerrainHeight(dxf, dyf))
				|| (!game.isTerrainRomp(dxf, dyf) && (myZ == game.getTerrainHeight(dxf, dyf))))
				&& (flying || !blocksSightFrom(pathingGrid, dxp, dyp, dxfp, dyfp)) && this.isVisible(game, dxp, dyp)
				&& ((x == y)
						|| ((x > y) && this.isVisible(game, dxp, dy)
								&& (flying || !pathingGrid.isBlockVision(dxfp, dyf)))
						|| ((x < y) && this.isVisible(game, dx, dyp)
								&& (flying || !pathingGrid.isBlockVision(dxf, dyfp))))) {
			this.setDetecting(dx, dy, CFogState.VISIBLE, detections);
		}
	}

	public void convertVisibleToFogged() {
		final byte[] cells = this.fogOfWar;
		for (int i = 0; i < cells.length; i++) {
			if (cells[i] == 0) {
				cells[i] = 127;
			}
		}
		Arrays.fill(this.detection, (byte) 0);
		if (this.anyPrivateCells) {
			Arrays.fill(this.privateCells, (byte) 0);
			this.anyPrivateCells = false;
		}
	}

	// Shared vision passes on only what the giver's own units and modifiers see; copying the cells first keeps vision
	// another ally passed to the giver from reaching the receiver in the same update.
	private byte[] ownFogOfWar;
	private byte[] ownDetection;

	public void keepOwnVision() {
		if (this.ownFogOfWar == null) {
			this.ownFogOfWar = new byte[this.fogOfWar.length];
			this.ownDetection = new byte[this.detection.length];
		}
		System.arraycopy(this.fogOfWar, 0, this.ownFogOfWar, 0, this.fogOfWar.length);
		System.arraycopy(this.detection, 0, this.ownDetection, 0, this.detection.length);
		if (this.anyPrivateCells) {
			for (int i = 0; i < this.privateCells.length; i++) {
				if (this.privateCells[i] != 0) {
					this.ownFogOfWar[i] = 127;
				}
			}
		}
	}

	public void addOwnVisionOf(final CPlayerFogOfWar giver) {
		final byte[] giverCells = giver.ownFogOfWar;
		final byte[] giverDetection = giver.ownDetection;
		final byte[] cells = this.fogOfWar;
		for (int i = 0; i < cells.length; i++) {
			if (giverCells[i] == 0) {
				cells[i] = 0;
				this.detection[i] |= giverDetection[i];
			}
		}
	}

	// Cells a radius modifier covers, measured on 1.29: only the cell of the centre and the radius in whole cells
	// (floor) count, not where in the cell the centre is. From 3 cells the shape is the one of a unit's sight, except
	// that the northernmost row is missing, so it spans one row more to the south. The two smaller radii are
	// irregular and kept as measured, as offsets from the centre cell.
	private static final int[][][] SMALL_RADIUS_CELLS = {
			{},
			{ { -1, 0 }, { 0, 0 }, { -1, -1 }, { 0, -1 } },
			{ { 0, 2 }, { -2, 0 }, { -1, 0 }, { 0, 0 }, { 2, 0 }, { -1, -1 }, { 0, -1 }, { 0, -2 } } };

	public void setFogStateRadius(PathingGrid pathingGrid, float myX, float myY, float radius, CFogState state) {
		if (!(radius >= GRID_STEP)) {
			return;
		}
		final int cells = (int) Math.min(radius / GRID_STEP, 1 << 15);
		final int centerX = pathingGrid.getFogOfWarIndexX(myX);
		final int centerY = pathingGrid.getFogOfWarIndexY(myY);
		if (cells < SMALL_RADIUS_CELLS.length) {
			for (final int[] offset : SMALL_RADIUS_CELLS[cells]) {
				setCell(centerX + offset[0], centerY + offset[1], state);
			}
			return;
		}
		final int rowMin = Math.max(-cells, -centerY);
		final int rowMax = Math.min(cells - 1, (this.height - 1) - centerY);
		final long radiusSq = (long) cells * cells;
		for (int dy = rowMin; dy <= rowMax; dy++) {
			final int near = Math.abs(dy);
			// How far along the row isInSight still holds: past the row's own distance the reach of its rounded
			// square root, inside it the distance up to which the cell at that column still reaches the row.
			final double inner = radiusSq - ((near - 0.5) * (near - 0.5));
			int reach = (int) Math.floor(Math.sqrt(inner));
			if (reach >= near) {
				reach = (int) Math.floor(Math.sqrt(radiusSq - ((long) near * near)) + 0.5);
			}
			final int fromX = Math.max(-reach, -centerX);
			final int toX = Math.min(reach, (this.width - 1) - centerX);
			final byte mask = state.getMask();
			for (int dx = fromX; dx <= toX; dx++) {
				setState(centerX + dx, centerY + dy, mask);
			}
		}
	}

	private void setCell(final int indexX, final int indexY, final CFogState state) {
		if ((indexX >= 0) && (indexX < this.width) && (indexY >= 0) && (indexY < this.height)) {
			setState(indexX, indexY, state.getMask());
		}
	}
}
