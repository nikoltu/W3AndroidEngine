package com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedTransient;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWorldCollision;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;

public class CPathfindingProcessor {
	private final Rectangle tempRect = new Rectangle();
	private final Rectangle segmentRect = new Rectangle();
	private int footprintCells = 1;
	private float[] crossings = new float[64];
	private int gameTurnTick;
	private final PathingGrid pathingGrid;
	private final CWorldCollision worldCollision;
	private final LinkedList<PathfindingJob> moveQueue = new LinkedList<>();
	/**
	 * Measured on 1.29: a unit waiting for its path among many of its owner's keeps its turn when it gets the same
	 * order again. Here a new order takes the unit's search out of the queue and its next update asks again, so a
	 * search asked again within a couple of updates goes back to the place the old one had.
	 */
	private final Map<CBehaviorMove, int[]> withdrawnTurns = new IdentityHashMap<>();
	private int nextTurn = 0;
	private final SharedGraph graph;
	// things with modified state per current job:
	private final int[] goalCells = new int[9];
	private final float[] goalCellX = new float[9];
	private final float[] goalCellY = new float[9];
	private int goals = 0;
	private int pathfindJobId = 0;
	/**
	 * Measured on 1.29: a small unit (collision 16) ordered into a wood walks to the free point nearest the order point
	 * even when that is round the far side, which rings a whole cell apart often tie and miss; a larger one (a Footman
	 * in a group, a Paladin onto a Circle of Power) stops where rings a whole cell apart put it.
	 */
	private static final float NEAREST_PATHABLE_FINE_STEP = 16f;
	private static final float NEAREST_PATHABLE_STEP = 32f;
	private static final float NEAREST_PATHABLE_RADIUS = 1024f;
	private static final float BESIDE_UNIT_CELL = 32f;
	private static final float BESIDE_GOAL_REACH = 48f;
	/**
	 * Measured on 1.29: round a wall that scripts have made unwalkable a unit finds its way only as far as such a
	 * search goes, about max(2048, 4 * cells^2) steps (cells = the straight distance in cells): a wall of 1056 is
	 * walked round, a wall of 2112 or a cup is not, though the way round exists, and the unit stops at the point nearest
	 * to its target that it found. Over terrain and destructables the search goes on much further.
	 */
	private static final int MIN_SEARCH_ITERATIONS = 4096;
	private static final int MIN_SCRIPTED_SEARCH_ITERATIONS = 2048;
	private static final int SCRIPTED_TERRAIN_CELLS = 3;
	private static final int SCRIPTED_GOAL_CELLS = 8;
	/**
	 * Measured on 1.29: a unit of Neutral Passive that has had Wander (a Sheep, a Peasant given Wander, also once
	 * Wander is removed again) ordered behind a wood stops in the first corner it walks into on the way, where any
	 * other unit, a Player's Sheep too, walks the long way round. Its search looks only a few steps around where it
	 * stands and it walks to the point of that search nearest the target, sliding along what stands in its way.
	 */
	private static final int SHORT_SEARCH_ITERATIONS = 16;
	/**
	 * Measured on 1.29: ordered onto blocked ground (a wood), a unit walks round to the free point nearest the order point
	 * only when the nearest it can get on its own side is more than sqrt(2) times as far from it, or no nearer than where
	 * it stands (a unit already at the edge walks round); otherwise it stops on its own side, at the point nearest the
	 * order point that the first steps of its search reached. A free point those first steps already get to (a doodad
	 * in open ground) is simply walked to. Only for a move a player or a script ordered: a unit running from an
	 * attacker keeps to the nearest free point.
	 */
	private static final int NEAR_SIDE_ITERATIONS = 512;
	private static final double NEAR_SIDE_RATIO = StrictMath.sqrt(2);
	private static final float NEAR_SIDE_MIN_GAIN = 32f;
	private static final float SLIDE_STEP = 4f;
	private static final int SLIDE_STEPS = 64;
	private static final int WITHDRAWN_TURN_UPDATES = 2;
	private static final int DETOUR_ITERATION_FACTOR = 16;
	/**
	 * A goal that standing units close off cannot be reached, however far the search looks: the original walks to the
	 * ring and stops there. Such a pocket is found by a small fill from the goal; the search then ends once it has got
	 * to the ring and has looked a little way beyond the straight line, instead of filling the map for a way in.
	 */
	private static final int ENCLOSED_POCKET_CELLS = 1024;
	private static final float ENCLOSED_STOP_MARGIN = 192f;
	private static final double ENCLOSED_FLOOD_MARGIN = 1024;
	/** A free line this many cells long from the goal means the ground round it is open: no fill is needed. */
	private static final int ENCLOSED_OPEN_REACH = 12;
	/** The world positions of the three columns, then the three rows, a point's neighbors lie in. */
	@SavedTransient
	private float[] stepPoints;
	/** Their grid indices as the search's mapping gives them, NOT_ON_MAP for one off the map. */
	@SavedTransient
	private int[] stepCells;
	private static final int NOT_ON_MAP = Integer.MIN_VALUE;
	@SavedTransient
	private int[] enclosedSeen;
	@SavedTransient
	private int[] enclosedQueue;
	/** Stamp and pathable epoch of the warm-up search; a real search's are counted up from one, so never these. */
	private static final int WARM_UP_STAMP = Integer.MAX_VALUE;
	private static final int WARM_UP_EPOCH = Integer.MAX_VALUE - 1;
	private static final int WARM_UP_ITERATIONS = 6000;
	/**
	 * Iterations one tick may spend on searches. A search that has already used that much in earlier ticks is given as
	 * much again each tick, up to the maximum, so a long one ends in a few ticks while a short one costs what it did.
	 * It follows from the search's own count, so it is the same on every machine. While other searches wait behind it,
	 * a search under way may spend the maximum: a unit of a crowd given a new order otherwise stood some seconds for the
	 * searches of the units queued before it. A new search still starts only within the plain budget, so the other
	 * players' searches get their turn as before.
	 */
	private static final int TICK_ITERATIONS = 1500;
	private static final int TICK_ITERATIONS_MAX = 4000;
	/** Ticks a computer player's search may keep waiting for the shared graph while human orders go before it. */
	private static final int HUMAN_PRIORITY_MAX_WAIT = 200;
	private static final double DETOUR_MIN_DISTANCE = 1024;
	private int totalIterations = 0;
	private long jobNanos;
	private final int pathingGridCellCount;

	public CPathfindingProcessor(final PathingGrid pathingGrid, final CWorldCollision worldCollision,
			final SharedGraph graph) {
		this.pathingGrid = pathingGrid;
		this.worldCollision = worldCollision;
		this.graph = graph;
		this.pathingGridCellCount = pathingGrid.getWidth() * pathingGrid.getHeight();
	}

	/**
	 * One search state for all players' processors: a state per player filled during long cross-map searches ran the
	 * device out of memory. A search may pause between ticks with its state in the arrays, so only one processor at a
	 * time holds a started search; the others wait, and the first one that waited goes next.
	 */
	public static final class SharedGraph {
		private SearchGrid cells;
		private SearchGrid corners;
		private int lastJobId = 0;
		/** Counts the combinations of search and tick the pathable tests were made in; a test holds for one of them. */
		private int pathableEpoch;
		private int epochJobId;
		private int epochTick;
		private CPathfindingProcessor owner;
		private CPathfindingProcessor reservedFor;
		private final Reachability[] reachability = new Reachability[REACHABILITY_CACHE];
		private int[] fillQueue;
		@SavedTransient
		private boolean searchWarmed;
		/** Set while a map's first steps are still to come: the parts are then found after them, see prewarmParts. */
		@SavedTransient
		private boolean partsDeferred;

		/**
		 * The arrays of a search are made when one needs them: most maps are searched with one of the two kinds of
		 * point only, and a game that is not searching has no use for them.
		 */
		private SearchGrid searchGrid(final PathingGrid pathingGrid, final boolean corner) {
			SearchGrid grid = corner ? this.corners : this.cells;
			if (grid == null) {
				grid = new SearchGrid(pathingGrid, corner);
				if (corner) {
					this.corners = grid;
				}
				else {
					this.cells = grid;
				}
			}
			return grid;
		}

		private int pathableEpoch(final int jobId, final int tick) {
			if ((jobId != this.epochJobId) || (tick != this.epochTick)) {
				this.epochJobId = jobId;
				this.epochTick = tick;
				this.pathableEpoch++;
			}
			return this.pathableEpoch << 1;
		}

		/**
		 * Lets go of the search arrays when no search is under way, so that a saved game does not hold them. A search
		 * that was never finished keeps them: its open set is in them.
		 */
		public void dropStaleNodes() {
			if (this.owner == null) {
				this.cells = null;
				this.corners = null;
			}
		}

		/**
		 * Whether a unit standing at a point can walk to within a reach of another point, ignoring other units: some open
		 * grid point within that reach lies in the same part of the map as the grid points under the unit.
		 */
		public boolean canReach(final PathingGrid pathingGrid, final MovementType movementType,
				final float collisionSize, final float fromX, final float fromY, final float toX, final float toY,
				final float reach) {
			final Reachability parts = reachability(pathingGrid, movementType, collisionSize, false);
			final int startX = pathingGrid.getCellX(fromX);
			final int startY = pathingGrid.getCellY(fromY);
			final int[] startParts = new int[9];
			int startPartCount = 0;
			for (int x = startX - 1; x <= (startX + 1); x++) {
				for (int y = startY - 1; y <= (startY + 1); y++) {
					final int part = parts.part(this, pathingGrid, x, y);
					if (part != 0) {
						startParts[startPartCount++] = part;
					}
				}
			}
			if (startPartCount == 0) {
				return true;
			}
			final int minX = pathingGrid.getCellX(toX - reach);
			final int maxX = pathingGrid.getCellX(toX + reach);
			final int minY = pathingGrid.getCellY(toY - reach);
			final int maxY = pathingGrid.getCellY(toY + reach);
			boolean anyOpen = false;
			for (int x = minX; x <= maxX; x++) {
				for (int y = minY; y <= maxY; y++) {
					final int part = parts.part(this, pathingGrid, x, y);
					if (contains(startParts, startPartCount, part)) {
						return true;
					}
					anyOpen |= part != 0;
				}
			}
			// a target walled in by trees or buildings has no open point to judge by
			return !anyOpen;
		}

		/**
		 * Makes what the first search of a unit size would otherwise make in its first step: the search arrays and the
		 * parts of the map the unit can walk between, all found. Parts are only told apart by number, so finding them
		 * all ahead gives the searches the same answers.
		 */
		public void prewarm(final PathingGrid pathingGrid, final CWorldCollision worldCollision,
				final MovementType movementType, final float collisionSize) {
			if ((movementType == MovementType.FLY) || (movementType == MovementType.DISABLED)) {
				return;
			}
			final boolean corner = isCollisionSizeBetterSuitedForCorners(collisionSize);
			final SearchGrid grid = searchGrid(pathingGrid, corner);
			if (!this.searchWarmed && (this.owner == null)) {
				this.searchWarmed = true;
				new CPathfindingProcessor(pathingGrid, worldCollision, this).warmSearch(grid, movementType,
						collisionSize, corner);
			}
			if (!this.partsDeferred) {
				prewarmParts(pathingGrid, movementType, collisionSize);
			}
		}

		/**
		 * Leaves finding the parts of the map to {@link #prewarmParts}, called once the map's script has made its
		 * first changes to the pathing: a map that places thousands of trees or buildings in its first step has the
		 * parts found before it changed again, and the first search of each size found them all again.
		 */
		public void deferParts() {
			this.partsDeferred = true;
		}

		/** Finds the parts of the map for a unit size (the same as prewarm does when they are not deferred). */
		public void prewarmParts(final PathingGrid pathingGrid, final MovementType movementType,
				final float collisionSize) {
			if ((movementType == MovementType.FLY) || (movementType == MovementType.DISABLED)) {
				return;
			}
			final boolean corner = isCollisionSizeBetterSuitedForCorners(collisionSize);
			findAllParts(pathingGrid, reachability(pathingGrid, movementType, collisionSize, corner));
			if (corner && (reachabilities() < (REACHABILITY_CACHE - 2))) {
				// canReach asks about the cell points of every size; room is left for what the first orders ask
				findAllParts(pathingGrid, reachability(pathingGrid, movementType, collisionSize, false));
			}
		}

		private int reachabilities() {
			int count = 0;
			for (final Reachability entry : this.reachability) {
				if (entry != null) {
					count++;
				}
			}
			return count;
		}

		private void findAllParts(final PathingGrid pathingGrid, final Reachability parts) {
			for (int y = 0; y < parts.height; y++) {
				for (int x = 0; x < parts.width; x++) {
					parts.part(this, pathingGrid, x, y);
				}
			}
		}

		private Reachability reachability(final PathingGrid pathingGrid, final MovementType movementType,
				final float collisionSize, final boolean corners) {
			final MovementType ground = Reachability.ground(movementType);
			int slot = 0;
			for (int i = 0; i < this.reachability.length; i++) {
				final Reachability entry = this.reachability[i];
				if (entry == null) {
					slot = i;
					break;
				}
				if ((entry.movementType == ground) && (entry.collisionSize == collisionSize)
						&& (entry.corners == corners)) {
					entry.lastUse = this.lastJobId;
					entry.validate(this, pathingGrid);
					return entry;
				}
				if (entry.lastUse < this.reachability[slot].lastUse) {
					slot = i;
				}
			}
			final Reachability entry = new Reachability(pathingGrid, ground, collisionSize, corners);
			entry.lastUse = this.lastJobId;
			this.reachability[slot] = entry;
			return entry;
		}
	}

	// A melee game asks for four walking sizes, each on cell points (reachability checks) and, for the sizes that
	// path on corners, on corner points too, plus the naval and amphibious sizes; with fewer entries they pushed each
	// other out and every search rebuilt one over the whole map. Entries are only allocated when asked for.
	private static final int REACHABILITY_CACHE = 8;

	/**
	 * Which grid points of a search graph a unit can walk between, ignoring other units: the parts of the map apart
	 * from each other, found once and kept until buildings or destructables change the pathing. A search for a point in
	 * another part than the unit's walked every point the unit could reach before it gave up, and a few such searches
	 * at once held up the paths of all other units for many seconds.
	 */
	static final class Reachability {
		private static final byte UNKNOWN = 0;
		private static final byte OPEN = 1;
		private static final byte BLOCKED = 2;
		private final MovementType movementType;
		private final float collisionSize;
		private final boolean corners;
		private final int width;
		private final int height;
		private final byte[] pathable;
		private final int[] part;
		/**
		 * The first footprint column and row (PathingGrid.getFootprintCellX) of the grid points of each column and
		 * row, worked out once.
		 */
		private final int footprintCells;
		private final int[] firstColumns;
		private final int[] firstRows;
		private int parts;
		private int version;
		private int lastUse;
		/** The cells changed since the version, as the grid hands them out; a longer list finds everything again. */
		private final int[] changes = new int[MAX_CHANGES * 4];

		Reachability(final PathingGrid grid, final MovementType movementType, final float collisionSize,
				final boolean corners) {
			this.movementType = movementType;
			this.collisionSize = collisionSize;
			this.corners = corners;
			this.width = grid.getWidth() + (corners ? 1 : 0);
			this.height = grid.getHeight() + (corners ? 1 : 0);
			this.pathable = new byte[this.width * this.height];
			this.part = new int[this.width * this.height];
			this.version = grid.getOverlayVersion();
			this.footprintCells = PathingGrid.getFootprintCells(collisionSize);
			this.firstColumns = new int[this.width];
			for (int x = 0; x < this.width; x++) {
				this.firstColumns[x] = grid.getFootprintCellX(worldX(grid, x), this.footprintCells);
			}
			this.firstRows = new int[this.height];
			for (int y = 0; y < this.height; y++) {
				this.firstRows[y] = grid.getFootprintCellY(worldY(grid, y), this.footprintCells);
			}
		}

		/**
		 * Movement types that read the same pathing flag (PathingGrid.MovementType) reach the same parts, so they
		 * share one entry: a melee game's walking units, horses and hovering units then fit in the cache together.
		 */
		private static MovementType ground(final MovementType movementType) {
			switch (movementType) {
			case FOOT_NO_COLLISION:
			case HORSE:
			case HOVER:
				return MovementType.FOOT;
			default:
				return movementType;
			}
		}

		/** PathingGrid.isPathable at a grid point, by the footprint cells worked out up front. */
		private boolean samplesPathable(final PathingGrid grid, final int x, final int y) {
			final int firstX = this.firstColumns[x];
			final int firstY = this.firstRows[y];
			final int cells = this.footprintCells;
			if ((firstX < 0) || (firstY < 0) || ((firstX + cells) > grid.getWidth())
					|| ((firstY + cells) > grid.getHeight())) {
				return false;
			}
			for (int cellY = firstY; cellY < (firstY + cells); cellY++) {
				for (int cellX = firstX; cellX < (firstX + cells); cellX++) {
					if (!this.movementType.isPathable(grid.getCellPathing(cellX, cellY))) {
						return false;
					}
				}
			}
			return true;
		}

		void validate(final SharedGraph graph, final PathingGrid grid) {
			final int current = grid.getOverlayVersion();
			if (this.version == current) {
				return;
			}
			final int count = grid.collectOverlayChanges(this.version, this.changes);
			this.version = current;
			if ((count < 0) || !repair(graph, grid, count)) {
				Arrays.fill(this.pathable, UNKNOWN);
				Arrays.fill(this.part, 0);
				this.parts = 0;
			}
		}

		private static final int MAX_CHANGES = 32;
		/** Rectangles of changed points this close (in points) are repaired as one. */
		private static final int MERGE_GAP = 3;
		/** Repairs only look this far past the changed points, and no larger a patch than this. */
		private static final int WINDOW = 6;
		private static final int MAX_PATCH_POINTS = 4096;

		/**
		 * Brings the points near changed cells up to date and keeps the parts found so far where the change cannot have
		 * altered them, instead of finding every part again: a part (every point of one is labelled at once) that no
		 * changed point is next to is the same part as before. Returns false when the change is not one of the cases
		 * handled below, and everything is to be found again.
		 */
		private boolean repair(final SharedGraph graph, final PathingGrid grid, final int count) {
			// the points whose test reads a changed cell, as rectangles merged into patches
			int patches = 0;
			final int[] patch = new int[MAX_CHANGES * 4];
			for (int i = 0; i < count; i++) {
				final int minCellX = this.changes[i * 4];
				final int minCellY = this.changes[(i * 4) + 1];
				final int maxCellX = this.changes[(i * 4) + 2];
				final int maxCellY = this.changes[(i * 4) + 3];
				final int firstX = firstPoint(this.firstColumns, minCellX, maxCellX);
				final int firstY = firstPoint(this.firstRows, minCellY, maxCellY);
				if ((firstX < 0) || (firstY < 0)) {
					continue;
				}
				patch[patches * 4] = firstX;
				patch[(patches * 4) + 1] = firstY;
				patch[(patches * 4) + 2] = lastPoint(this.firstColumns, minCellX, maxCellX);
				patch[(patches * 4) + 3] = lastPoint(this.firstRows, minCellY, maxCellY);
				patches++;
			}
			boolean merged = true;
			while (merged && (patches > 1)) {
				merged = false;
				for (int a = 0; (a < patches) && !merged; a++) {
					for (int b = a + 1; (b < patches) && !merged; b++) {
						if ((patch[a * 4] <= (patch[(b * 4) + 2] + MERGE_GAP))
								&& (patch[b * 4] <= (patch[(a * 4) + 2] + MERGE_GAP))
								&& (patch[(a * 4) + 1] <= (patch[(b * 4) + 3] + MERGE_GAP))
								&& (patch[(b * 4) + 1] <= (patch[(a * 4) + 3] + MERGE_GAP))) {
							patch[a * 4] = Math.min(patch[a * 4], patch[b * 4]);
							patch[(a * 4) + 1] = Math.min(patch[(a * 4) + 1], patch[(b * 4) + 1]);
							patch[(a * 4) + 2] = Math.max(patch[(a * 4) + 2], patch[(b * 4) + 2]);
							patch[(a * 4) + 3] = Math.max(patch[(a * 4) + 3], patch[(b * 4) + 3]);
							System.arraycopy(patch, (patches - 1) * 4, patch, b * 4, 4);
							patches--;
							merged = true;
						}
					}
				}
			}
			int labelledCount = 0;
			final int[] labelled = new int[patches];
			for (int i = 0; i < patches; i++) {
				if (touchesLabelled(patch, i)) {
					labelled[labelledCount++] = i;
				}
				else {
					// no part found is next to it: its points are tested again when asked for
					for (int y = patch[(i * 4) + 1]; y <= patch[(i * 4) + 3]; y++) {
						Arrays.fill(this.pathable, (y * this.width) + patch[i * 4],
								(y * this.width) + patch[(i * 4) + 2] + 1, UNKNOWN);
					}
				}
			}
			if (labelledCount > 1) {
				// each patch is repaired as if it were the only change, on the state the one before it left; the
				// points of the others not tested yet count as closed until their turn
				for (int n = 0; n < labelledCount; n++) {
					final int i = labelled[n];
					for (int y = patch[(i * 4) + 1]; y <= patch[(i * 4) + 3]; y++) {
						for (int x = patch[i * 4]; x <= patch[(i * 4) + 2]; x++) {
							if (this.pathable[(y * this.width) + x] == UNKNOWN) {
								this.pathable[(y * this.width) + x] = BLOCKED;
							}
						}
					}
				}
			}
			for (int n = 0; n < labelledCount; n++) {
				final int i = labelled[n];
				repairPatch(graph, grid, patch[i * 4], patch[(i * 4) + 1], patch[(i * 4) + 2], patch[(i * 4) + 3]);
			}
			return true;
		}

		/** The lowest point whose footprint reaches cells minCell..maxCell, or -1; firsts rise with the point. */
		private int firstPoint(final int[] firsts, final int minCell, final int maxCell) {
			for (int i = 0; i < firsts.length; i++) {
				if (((firsts[i] + this.footprintCells) > minCell) && (firsts[i] <= maxCell)) {
					return i;
				}
			}
			return -1;
		}

		private int lastPoint(final int[] firsts, final int minCell, final int maxCell) {
			for (int i = firsts.length - 1; i >= 0; i--) {
				if (((firsts[i] + this.footprintCells) > minCell) && (firsts[i] <= maxCell)) {
					return i;
				}
			}
			return -1;
		}

		/** Whether a point of the patch or next to it has a part found. */
		private boolean touchesLabelled(final int[] patch, final int index) {
			final int minX = Math.max(patch[index * 4] - 1, 0);
			final int maxX = Math.min(patch[(index * 4) + 2] + 1, this.width - 1);
			final int minY = Math.max(patch[(index * 4) + 1] - 1, 0);
			final int maxY = Math.min(patch[(index * 4) + 3] + 1, this.height - 1);
			for (int y = minY; y <= maxY; y++) {
				for (int x = minX; x <= maxX; x++) {
					if (this.part[(y * this.width) + x] != 0) {
						return true;
					}
				}
			}
			return false;
		}

		private boolean step(final PathingGrid grid, final int cx, final int cy, final int nx, final int ny) {
			return open(grid, nx, ny) && open(grid, cx, ny) && open(grid, nx, cy);
		}

		/**
		 * The one patch with parts found next to it. Points that stop being open are taken out of their part, which
		 * is still the same part when the points of it next to the patch can still walk to each other through the
		 * points around the patch; points that become open join the part they are next to when that is the only one
		 * and no unlabelled point is next to them. Else the parts next to the patch are forgotten, found again when
		 * asked for.
		 */
		private void repairPatch(final SharedGraph graph, final PathingGrid grid, final int patchMinX,
				final int patchMinY, final int patchMaxX, final int patchMaxY) {
			final int minX = Math.max(patchMinX - 1, 0);
			final int maxX = Math.min(patchMaxX + 1, this.width - 1);
			final int minY = Math.max(patchMinY - 1, 0);
			final int maxY = Math.min(patchMaxY + 1, this.height - 1);
			// the parts found next to the patch, before anything changes
			int[] touched = new int[4];
			int touchedCount = 0;
			for (int y = minY; y <= maxY; y++) {
				for (int x = minX; x <= maxX; x++) {
					final int found = this.part[(y * this.width) + x];
					if ((found != 0) && !contains(touched, touchedCount, found)) {
						if (touchedCount == touched.length) {
							touched = Arrays.copyOf(touched, touchedCount * 2);
						}
						touched[touchedCount++] = found;
					}
				}
			}
			boolean anyLost = false;
			boolean anyGained = false;
			for (int y = patchMinY; y <= patchMaxY; y++) {
				for (int x = patchMinX; x <= patchMaxX; x++) {
					final int index = (y * this.width) + x;
					final boolean wasLabelled = this.part[index] != 0;
					final boolean wasOpen = this.pathable[index] == OPEN;
					final boolean nowOpen = samplesPathable(grid, x, y);
					this.pathable[index] = nowOpen ? OPEN : BLOCKED;
					if (wasLabelled && !nowOpen) {
						this.part[index] = 0;
						anyLost = true;
					}
					else if (nowOpen && !wasOpen) {
						anyGained = true;
					}
				}
			}
			boolean kept = true;
			if (anyLost && anyGained) {
				kept = false;
			}
			else if (anyLost) {
				if (((maxX - minX) + 1) * ((maxY - minY) + 1) > MAX_PATCH_POINTS) {
					kept = false;
				}
				else {
					kept = stillConnected(grid, touched, touchedCount, minX, minY, maxX, maxY);
				}
			}
			else if (anyGained) {
				kept = growParts(graph, grid, minX, minY, maxX, maxY);
			}
			if (!kept) {
				for (int i = 0; i < this.part.length; i++) {
					if ((this.part[i] != 0) && contains(touched, touchedCount, this.part[i])) {
						this.part[i] = 0;
					}
				}
			}
		}

		/**
		 * Whether every part that was next to the patch is still one part: its points in the area around the patch
		 * walk to each other through points of the part within a few points more around. The rest of a part reaches
		 * that area without crossing the patch, so that is all that has to hold.
		 */
		private boolean stillConnected(final PathingGrid grid, final int[] touched, final int touchedCount,
				final int minX, final int minY, final int maxX, final int maxY) {
			final int windowMinX = Math.max(minX - WINDOW, 0);
			final int windowMaxX = Math.min(maxX + WINDOW, this.width - 1);
			final int windowMinY = Math.max(minY - WINDOW, 0);
			final int windowMaxY = Math.min(maxY + WINDOW, this.height - 1);
			final int windowWidth = (windowMaxX - windowMinX) + 1;
			final int[] queue = new int[windowWidth * ((windowMaxY - windowMinY) + 1)];
			for (int t = 0; t < touchedCount; t++) {
				final int label = touched[t];
				int remaining = 0;
				int seed = -1;
				for (int y = minY; y <= maxY; y++) {
					for (int x = minX; x <= maxX; x++) {
						if (this.part[(y * this.width) + x] == label) {
							remaining++;
							seed = (y * this.width) + x;
						}
					}
				}
				if (remaining == 0) {
					continue;
				}
				final boolean[] seen = new boolean[queue.length];
				int head = 0;
				int tail = 0;
				queue[tail++] = seed;
				seen[(((seed / this.width) - windowMinY) * windowWidth) + ((seed % this.width) - windowMinX)] = true;
				remaining--;
				while ((head < tail) && (remaining > 0)) {
					final int index = queue[head++];
					final int cx = index % this.width;
					final int cy = index / this.width;
					for (final Direction direction : Direction.VALUES) {
						final int nx = cx + direction.xOffset;
						final int ny = cy + direction.yOffset;
						if ((nx < windowMinX) || (nx > windowMaxX) || (ny < windowMinY) || (ny > windowMaxY)) {
							continue;
						}
						final int next = (ny * this.width) + nx;
						final int seenAt = ((ny - windowMinY) * windowWidth) + (nx - windowMinX);
						if (seen[seenAt] || (this.part[next] != label) || !step(grid, cx, cy, nx, ny)) {
							continue;
						}
						seen[seenAt] = true;
						queue[tail++] = next;
						if ((nx >= minX) && (nx <= maxX) && (ny >= minY) && (ny <= maxY)) {
							remaining--;
						}
					}
				}
				if (remaining > 0) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Labels the points that became open, and whatever open points not yet in a part they lead to, with the part
		 * they step from. A part found whole stays whole this way: a point it now steps to that has no part is in no
		 * part found, so all of the points it leads to are too. Returns false when a step now joins two parts found.
		 */
		private boolean growParts(final SharedGraph graph, final PathingGrid grid, final int minX, final int minY,
				final int maxX, final int maxY) {
			if ((graph.fillQueue == null) || (graph.fillQueue.length < this.part.length)) {
				graph.fillQueue = new int[this.part.length];
			}
			final int[] queue = graph.fillQueue;
			int head = 0;
			int tail = 0;
			for (int y = minY; y <= maxY; y++) {
				for (int x = minX; x <= maxX; x++) {
					final int index = (y * this.width) + x;
					if (this.part[index] != 0) {
						queue[tail++] = index;
					}
				}
			}
			while (head < tail) {
				final int index = queue[head++];
				final int label = this.part[index];
				final int cx = index % this.width;
				final int cy = index / this.width;
				for (final Direction direction : Direction.VALUES) {
					final int nx = cx + direction.xOffset;
					final int ny = cy + direction.yOffset;
					if (!step(grid, cx, cy, nx, ny)) {
						continue;
					}
					final int next = (ny * this.width) + nx;
					final int other = this.part[next];
					if (other == label) {
						continue;
					}
					if (other != 0) {
						return false;
					}
					this.part[next] = label;
					queue[tail++] = next;
				}
			}
			return true;
		}

		private float worldX(final PathingGrid grid, final int x) {
			return this.corners ? grid.getWorldXFromCorner(x) : grid.getWorldX(x);
		}

		private float worldY(final PathingGrid grid, final int y) {
			return this.corners ? grid.getWorldYFromCorner(y) : grid.getWorldY(y);
		}

		private boolean open(final PathingGrid grid, final int x, final int y) {
			if ((x < 0) || (y < 0) || (x >= this.width) || (y >= this.height)) {
				return false;
			}
			final int index = (y * this.width) + x;
			byte value = this.pathable[index];
			if (value == UNKNOWN) {
				value = samplesPathable(grid, x, y) ? OPEN : BLOCKED;
				this.pathable[index] = value;
			}
			return value == OPEN;
		}

		/** The part already found for a grid point (every point of a part is found at once), else 0. */
		int label(final int x, final int y) {
			if ((x < 0) || (y < 0) || (x >= this.width) || (y >= this.height)) {
				return 0;
			}
			return this.part[(y * this.width) + x];
		}

		/** The part of the map a grid point is in (by the steps the search takes), 0 for a point no unit fits on. */
		int part(final SharedGraph graph, final PathingGrid grid, final int x, final int y) {
			if (!open(grid, x, y)) {
				return 0;
			}
			final int start = (y * this.width) + x;
			if (this.part[start] != 0) {
				return this.part[start];
			}
			final int id = ++this.parts;
			if ((graph.fillQueue == null) || (graph.fillQueue.length < this.part.length)) {
				graph.fillQueue = new int[this.part.length];
			}
			final int[] queue = graph.fillQueue;
			int head = 0;
			int tail = 0;
			this.part[start] = id;
			queue[tail++] = start;
			final int[] part = this.part;
			final int width = this.width;
			while (head < tail) {
				final int index = queue[head++];
				final int cx = index % width;
				final int cy = index / width;
				if ((cx > 0) && (cy > 0) && (cx < (width - 1)) && (cy < (this.height - 1))) {
					// the eight points around it are tested once each; a step needs the point it goes to and
					// the two it passes between
					final boolean north = open(grid, cx, cy + 1);
					final boolean south = open(grid, cx, cy - 1);
					final boolean east = open(grid, cx + 1, cy);
					final boolean west = open(grid, cx - 1, cy);
					if (north && (part[index + width] == 0)) {
						part[index + width] = id;
						queue[tail++] = index + width;
					}
					if (south && (part[index - width] == 0)) {
						part[index - width] = id;
						queue[tail++] = index - width;
					}
					if (east && (part[index + 1] == 0)) {
						part[index + 1] = id;
						queue[tail++] = index + 1;
					}
					if (west && (part[index - 1] == 0)) {
						part[index - 1] = id;
						queue[tail++] = index - 1;
					}
					if (north && east && (part[index + width + 1] == 0) && open(grid, cx + 1, cy + 1)) {
						part[index + width + 1] = id;
						queue[tail++] = index + width + 1;
					}
					if (north && west && (part[index + width - 1] == 0) && open(grid, cx - 1, cy + 1)) {
						part[index + width - 1] = id;
						queue[tail++] = index + width - 1;
					}
					if (south && east && (part[index - width + 1] == 0) && open(grid, cx + 1, cy - 1)) {
						part[index - width + 1] = id;
						queue[tail++] = index - width + 1;
					}
					if (south && west && (part[index - width - 1] == 0) && open(grid, cx - 1, cy - 1)) {
						part[index - width - 1] = id;
						queue[tail++] = index - width - 1;
					}
					continue;
				}
				for (final Direction direction : Direction.VALUES) {
					final int nx = cx + direction.xOffset;
					final int ny = cy + direction.yOffset;
					if (!open(grid, nx, ny)) {
						continue;
					}
					final int next = (ny * width) + nx;
					if ((part[next] != 0) || !open(grid, cx, ny) || !open(grid, nx, cy)) {
						continue;
					}
					part[next] = id;
					queue[tail++] = next;
				}
			}
			return id;
		}
	}

	private boolean staysOnNearSide(final PathfindingJob job) {
		job.nearSideDecided = true;
		if ((job.nearSideCell < 0) || (job.nearSideDistance > (NEAR_SIDE_RATIO
				* StrictMath.hypot(job.goalX - job.goal.x, job.goalY - job.goal.y)))) {
			return false;
		}
		job.nearSideStays = job.nearSideDistance >= (StrictMath.hypot(job.startX - job.goal.x,
				job.startY - job.goal.y) - NEAR_SIDE_MIN_GAIN);
		// at the edge already: a new order walks round, a search on the way (the unit has come to the edge) stops
		return !job.nearSideStays || !job.queueItem.isFirstSearch();
	}

	private void finishOnNearSide(final PathfindingJob job, final CSimulation simulation) {
		final LinkedList<CPathPoint> path;
		if (job.nearSideStays) {
			path = new LinkedList<>();
			path.add(new CPathPoint(job.startX, job.startY));
		}
		else {
			path = buildPath(job, job.nearSideCell, false);
		}
		finishJob();
		job.queueItem.pathFound(job.goal, path, false, simulation);
	}

	private void finishJob() {
		this.moveQueue.poll();
		if (this.graph.owner == this) {
			this.graph.owner = null;
		}
	}

	/**
	 * Finds the path to a point using a naive, slow, and unoptimized algorithm.
	 * Does not have optimizations yet, do this for a bunch of units and it will
	 * probably lag like a walrus. The implementation here was created by reading
	 * the wikipedia article on A* to jog my memory from data structures class back
	 * in college, and is meant only as a first draft to get things working.
	 *
	 * @param collisionSize
	 *
	 *
	 * @param start
	 * @param goal
	 * @param playerIndex
	 * @param queueItem
	 * @return
	 */
	public void findNaiveSlowPath(final CUnit ignoreIntersectionsWithThisUnit,
			final CUnit ignoreIntersectionsWithThisSecondUnit, final float startX, final float startY,
			final CPathPoint goal, final PathingGrid.MovementType movementType, final float collisionSize,
			final boolean allowSmoothing, final CBehaviorMove queueItem) {
		findNaiveSlowPath(ignoreIntersectionsWithThisUnit, ignoreIntersectionsWithThisSecondUnit, startX, startY, goal,
				movementType, collisionSize, allowSmoothing, queueItem, false);
	}

	/**
	 * @param humanOrder whether the unit belongs to a human player: its search does not wait behind a computer
	 *                   player's long one
	 */
	public void findNaiveSlowPath(final CUnit ignoreIntersectionsWithThisUnit,
			final CUnit ignoreIntersectionsWithThisSecondUnit, final float startX, final float startY,
			final CPathPoint goal, final PathingGrid.MovementType movementType, final float collisionSize,
			final boolean allowSmoothing, final CBehaviorMove queueItem, final boolean humanOrder) {
		final PathfindingJob job = new PathfindingJob(ignoreIntersectionsWithThisUnit,
				ignoreIntersectionsWithThisSecondUnit, startX, startY, goal, movementType, collisionSize, allowSmoothing,
				queueItem);
		job.humanOrder = humanOrder;
		job.queuedTick = this.gameTurnTick;
		final int[] withdrawn = this.withdrawnTurns.remove(queueItem);
		if (withdrawn == null) {
			job.turn = this.nextTurn++;
			this.moveQueue.offer(job);
			return;
		}
		job.turn = withdrawn[0];
		final ListIterator<PathfindingJob> iterator = this.moveQueue.listIterator();
		while (iterator.hasNext()) {
			final PathfindingJob queued = iterator.next();
			// a search under way keeps the shared graph's nodes; nothing goes before it
			if (!queued.jobStarted && (queued.turn > job.turn)) {
				iterator.previous();
				break;
			}
		}
		iterator.add(job);
	}

	/** The searches waiting for their turn, the one running first. */
	public List<PathfindingJob> getMoveQueue() {
		return Collections.unmodifiableList(this.moveQueue);
	}

	public int getNextTurn() {
		return this.nextTurn;
	}

	public int getPathfindJobId() {
		return this.pathfindJobId;
	}

	public void removeFromPathfindingQueue(final CBehaviorMove behaviorMove) {
		// TODO because of silly java things, this remove is O(N) for now,
		// we could do some refactors to make it O(1) but do we care?
		final Iterator<PathfindingJob> iterator = this.moveQueue.iterator();
		while (iterator.hasNext()) {
			final PathfindingJob job = iterator.next();
			if (job.queueItem == behaviorMove) {
				this.withdrawnTurns.put(behaviorMove, new int[] { job.turn, WITHDRAWN_TURN_UPDATES });
				iterator.remove();
			}
		}

	}

	private boolean pathableBetween(final CUnit ignoreIntersectionsWithThisUnit,
			final CUnit ignoreIntersectionsWithThisSecondUnit, final float startX, final float startY,
			final PathingGrid.MovementType movementType, final float collisionSize, final float x, final float y) {
		return this.pathingGrid.isPathable(x, y, movementType, collisionSize)
				&& this.pathingGrid.isPathable(startX, y, movementType, collisionSize)
				&& this.pathingGrid.isPathable(x, startY, movementType, collisionSize)
				&& isPathableDynamically(x, y, ignoreIntersectionsWithThisUnit, ignoreIntersectionsWithThisSecondUnit,
						movementType)
				&& isPathableDynamically(x, startY, ignoreIntersectionsWithThisUnit,
						ignoreIntersectionsWithThisSecondUnit, movementType)
				&& isPathableDynamically(startX, y, ignoreIntersectionsWithThisUnit,
						ignoreIntersectionsWithThisSecondUnit, movementType);
	}

	private boolean isPathableDynamically(final float x, final float y, final CUnit ignoreIntersectionsWithThisUnit,
			final CUnit ignoreIntersectionsWithThisSecondUnit, final PathingGrid.MovementType movementType) {
		if (usesFootprint(movementType)) {
			return !this.worldCollision.intersectsFootprintOtherThan(this.pathingGrid, x, y, this.footprintCells,
					ignoreIntersectionsWithThisUnit, ignoreIntersectionsWithThisSecondUnit, movementType,
					this.gameTurnTick);
		}
		return !this.worldCollision.intersectsStandingUnitOtherThan(tempRect.setCenter(x, y),
				ignoreIntersectionsWithThisUnit, ignoreIntersectionsWithThisSecondUnit, movementType, this.gameTurnTick);
	}

	/** Ground units are compared by the pathing cells they stand on, as their movement does. */
	private static boolean usesFootprint(final PathingGrid.MovementType movementType) {
		return (movementType == PathingGrid.MovementType.FOOT) || (movementType == PathingGrid.MovementType.HORSE)
				|| (movementType == PathingGrid.MovementType.HOVER)
				|| (movementType == PathingGrid.MovementType.AMPHIBIOUS);
	}

	public static boolean isCollisionSizeBetterSuitedForCorners(final float collisionSize) {
		return (((2 * (int) collisionSize) / 32) % 2) == 1;
	}

	/**
	 * Whether the job's unit fits at a grid point, the same test pathableBetween makes for each of its three points. A
	 * point is tested by up to eight of its neighbors, so the answer is kept for the rest of the job's work in this
	 * tick (units do not move within a tick, so it cannot change).
	 */
	private boolean nodePathable(final PathfindingJob job, final SearchGrid grid, final int epoch, final int index,
			final float x, final float y) {
		// A point just below the map truncates to the first row or column: it shares that node with the point
		// inside, which would let the answer depend on which of the two a tick happened to test first. It is
		// tested every time and not kept.
		if ((x >= grid.xs[0]) && (y >= grid.ys[0])) {
			final int cached = grid.pathable(index);
			if ((cached & ~1) == epoch) {
				return (cached & 1) != 0;
			}
		}
		return testNodePathable(job, grid, epoch, index, x, y);
	}

	private boolean testNodePathable(final PathfindingJob job, final SearchGrid grid, final int epoch, final int index,
			final float x, final float y) {
		final boolean pathable = this.pathingGrid.isPathable(x, y, job.movementType, job.collisionSize)
				&& isPathableDynamically(x, y, job.ignoreIntersectionsWithThisUnit, secondUnitAt(job, x, y),
						job.movementType);
		if ((x >= grid.xs[0]) && (y >= grid.ys[0])) {
			grid.setPathable(index, epoch | (pathable ? 1 : 0));
		}
		return pathable;
	}

	private static void openAdd(final SearchGrid grid, final int index) {
		grid.setOpen(index, true);
		grid.add(index);
	}

	private boolean isGoal(final int index) {
		for (int i = 0; i < this.goals; i++) {
			if (index == this.goalCells[i]) {
				return true;
			}
		}
		return false;
	}

	private static double distance(final float x, final float y, final float px, final float py) {
		final double dx = (double) px - x;
		final double dy = (double) py - y;
		return Math.sqrt((dx * dx) + (dy * dy));
	}

	/** The estimate of the distance left to the goal points from a point; it always overestimates. */
	private float h(final float x, final float y) {
		float bestDistance = 0;
		for (int i = 0; i < this.goals; i++) {
			final double dx = (double) this.goalCellX[i] - x;
			final double dy = (double) this.goalCellY[i] - y;
			final float possibleDistance = (float) Math.sqrt((dx * dx) + (dy * dy));
			if (possibleDistance > bestDistance) {
				bestDistance = possibleDistance;
			}
		}
		return bestDistance;
	}

	/**
	 * The search state of one kind of grid point (cell centers or cell corners), kept in arrays indexed by
	 * y * width + x, and the open set of the search: the binary heap of java.util.PriorityQueue (same order of equal
	 * points, so the same paths) over those indices, ordered by their current f. Entries whose stamp is not the
	 * search's are from an earlier search and are reset when the search first touches them.
	 */
	static final class SearchGrid {
		private static final int DIRECTION_MASK = 15;
		private static final int OPEN_FLAG = 16;
		private final int width;
		private final int height;
		/** The world position of each column and row of points. */
		private final float[] xs;
		private final float[] ys;
		/** The g and f of each point, side by side. */
		private final double[] scores;
		/**
		 * Four ints for each point, so that what a search reads of a point is in one cache line: the search it was last
		 * touched by, the point it was reached from (-1 for none), the step to it as the ordinal of the direction plus
		 * one (0 for none) with the open set flag above, and the epoch of its pathable test (shifted left by one) with
		 * the result in bit 0. Entries whose stamp is not the search's are from an earlier search and are reset when
		 * the search first touches them.
		 */
		private final int[] state;
		private int[] heap = new int[64];
		private int heapSize;

		private SearchGrid(final PathingGrid pathingGrid, final boolean corner) {
			this.width = pathingGrid.getWidth() + (corner ? 1 : 0);
			this.height = pathingGrid.getHeight() + (corner ? 1 : 0);
			this.xs = new float[this.width];
			this.ys = new float[this.height];
			for (int x = 0; x < this.width; x++) {
				this.xs[x] = corner ? pathingGrid.getWorldXFromCorner(x) : pathingGrid.getWorldX(x);
			}
			for (int y = 0; y < this.height; y++) {
				this.ys[y] = corner ? pathingGrid.getWorldYFromCorner(y) : pathingGrid.getWorldY(y);
			}
			final int count = this.width * this.height;
			this.scores = new double[count * 2];
			this.state = new int[count * 4];
		}

		private double g(final int index) {
			return this.scores[index << 1];
		}

		private double f(final int index) {
			return this.scores[(index << 1) + 1];
		}

		private void setScores(final int index, final double g, final double f) {
			this.scores[index << 1] = g;
			this.scores[(index << 1) + 1] = f;
		}

		private int cameFrom(final int index) {
			return this.state[(index << 2) + 1];
		}

		private int direction(final int index) {
			return this.state[(index << 2) + 2] & DIRECTION_MASK;
		}

		private void setCameFrom(final int index, final int from, final int direction) {
			this.state[(index << 2) + 1] = from;
			this.state[(index << 2) + 2] = (this.state[(index << 2) + 2] & OPEN_FLAG) | direction;
		}

		private boolean isOpen(final int index) {
			return (this.state[(index << 2) + 2] & OPEN_FLAG) != 0;
		}

		private void setOpen(final int index, final boolean open) {
			final int flags = this.state[(index << 2) + 2];
			this.state[(index << 2) + 2] = open ? (flags | OPEN_FLAG) : (flags & DIRECTION_MASK);
		}

		private int pathable(final int index) {
			return this.state[(index << 2) + 3];
		}

		private void setPathable(final int index, final int value) {
			this.state[(index << 2) + 3] = value;
		}

		private void touch(final int index, final int pathfindJobId) {
			final int at = index << 2;
			if (pathfindJobId != this.state[at]) {
				this.scores[index << 1] = Float.POSITIVE_INFINITY;
				this.scores[(index << 1) + 1] = Float.POSITIVE_INFINITY;
				this.state[at] = pathfindJobId;
				this.state[at + 1] = -1;
				this.state[at + 2] = 0;
			}
		}

		private void clearOpen() {
			this.heapSize = 0;
		}

		private boolean isEmpty() {
			return this.heapSize == 0;
		}

		private void add(final int index) {
			if (this.heapSize >= this.heap.length) {
				this.heap = Arrays.copyOf(this.heap, this.heap.length * 2);
			}
			final double value = f(index);
			int k = this.heapSize++;
			while (k > 0) {
				final int parent = (k - 1) >>> 1;
				final int e = this.heap[parent];
				if (Double.compare(value, f(e)) >= 0) {
					break;
				}
				this.heap[k] = e;
				k = parent;
			}
			this.heap[k] = index;
		}

		private int poll() {
			final int[] heap = this.heap;
			final int result = heap[0];
			final int n = --this.heapSize;
			final int x = heap[n];
			if (n > 0) {
				final double xf = f(x);
				int k = 0;
				final int half = n >>> 1;
				while (k < half) {
					int child = (k << 1) + 1;
					int c = heap[child];
					final int right = child + 1;
					if ((right < n) && (Double.compare(f(c), f(heap[right])) > 0)) {
						c = heap[child = right];
					}
					if (Double.compare(xf, f(c)) <= 0) {
						break;
					}
					heap[k] = c;
					k = child;
				}
				heap[k] = x;
			}
			return result;
		}
	}

	/** Kept empty so that saved games with a search under way from before the arrays still load; their fields are skipped. */
	static final class Node {
	}

	/** See Node. */
	static final class OpenSet {
	}

	private static enum Direction {
		NORTH_WEST(-1, 1), NORTH(0, 1), NORTH_EAST(1, 1), EAST(1, 0), SOUTH_EAST(1, -1), SOUTH(0, -1),
		SOUTH_WEST(-1, -1), WEST(-1, 0);

		public static final Direction[] VALUES = values();

		private final int xOffset;
		private final int yOffset;
		private final double length;

		private static Direction of(final float dx, final float dy) {
			final int xOffset = (int) Math.signum(dx);
			final int yOffset = (int) Math.signum(dy);
			for (final Direction direction : VALUES) {
				if ((direction.xOffset == xOffset) && (direction.yOffset == yOffset)) {
					return direction;
				}
			}
			return null;
		}

		private Direction(final int xOffset, final int yOffset) {
			this.xOffset = xOffset;
			this.yOffset = yOffset;
			final double sqrt = Math.sqrt((xOffset * xOffset) + (yOffset * yOffset));
			this.length = sqrt;
		}
	}

	public static interface GridMapping {
		int getX(PathingGrid grid, float worldX);

		int getY(PathingGrid grid, float worldY);

		public static final GridMapping CELLS = new GridMapping() {
			@Override
			public int getX(final PathingGrid grid, final float worldX) {
				return grid.getCellX(worldX);
			}

			@Override
			public int getY(final PathingGrid grid, final float worldY) {
				return grid.getCellY(worldY);
			}

		};

		public static final GridMapping CORNERS = new GridMapping() {
			@Override
			public int getX(final PathingGrid grid, final float worldX) {
				return grid.getCornerX(worldX);
			}

			@Override
			public int getY(final PathingGrid grid, final float worldY) {
				return grid.getCornerY(worldY);
			}

		};
	}

	public void update(final CSimulation simulation) {
		if (!PerfStats.ENABLED) {
			updateJobs(simulation);
			return;
		}
		final long start = System.nanoTime();
		updateJobs(simulation);
		this.jobNanos += System.nanoTime() - start;
	}

	private void updateJobs(final CSimulation simulation) {
		this.gameTurnTick = simulation.getGameTurnTick();
		if (!this.withdrawnTurns.isEmpty()) {
			final Iterator<int[]> withdrawn = this.withdrawnTurns.values().iterator();
			while (withdrawn.hasNext()) {
				if (--withdrawn.next()[1] <= 0) {
					withdrawn.remove();
				}
			}
		}
		final SharedGraph graph = this.graph;
		if ((graph.owner == this) && (this.moveQueue.isEmpty() || !this.moveQueue.peek().jobStarted)) {
			graph.owner = null;
		}
		if ((graph.reservedFor == this) && this.moveQueue.isEmpty()) {
			graph.reservedFor = null;
		}
		final PathfindingJob head = this.moveQueue.peek();
		if ((head != null) && head.humanOrder && !head.jobStarted) {
			// A human player's order does not wait behind a computer player's: it takes the reservation, and a
			// search of theirs that has paused between ticks starts over later (once, so it cannot starve).
			if ((graph.reservedFor != null) && (graph.reservedFor != this)) {
				final PathfindingJob reservedHead = graph.reservedFor.moveQueue.peek();
				if ((reservedHead == null) || (!reservedHead.humanOrder
						&& ((this.gameTurnTick - reservedHead.queuedTick) < HUMAN_PRIORITY_MAX_WAIT))) {
					graph.reservedFor = this;
				}
			}
			if ((graph.owner != null) && (graph.owner != this)) {
				final PathfindingJob ownerHead = graph.owner.moveQueue.peek();
				if ((ownerHead != null) && ownerHead.jobStarted && !ownerHead.humanOrder && !ownerHead.preempted) {
					ownerHead.preempted = true;
					if (PerfStats.ENABLED) {
						PerfStats.pathDiscarded(graph.owner.totalIterations, graph.owner.jobNanos);
					}
					ownerHead.jobStarted = false;
					ownerHead.searchGrid = null;
					graph.owner = null;
					graph.reservedFor = this;
				}
			}
		}
		if ((graph.owner != null) && (graph.owner != this)) {
			if (!this.moveQueue.isEmpty() && (graph.reservedFor == null)) {
				graph.reservedFor = this;
			}
			return;
		}
		int workIterations = 0;
		JobsLoop: while (!this.moveQueue.isEmpty()) {
			final PathfindingJob job = this.moveQueue.peek();
			final int tickBudget = !job.jobStarted ? TICK_ITERATIONS
					: (this.moveQueue.size() > 1) ? TICK_ITERATIONS_MAX
							: Math.min(TICK_ITERATIONS_MAX, Math.max(TICK_ITERATIONS, this.totalIterations));
			if (job.jobStarted && (job.searchGrid == null)) {
				// a search saved before its state moved into the shared arrays starts again
				job.jobStarted = false;
			}
			this.tempRect.set(0, 0, job.collisionSize * 2, job.collisionSize * 2);
			this.footprintCells = PathingGrid.getFootprintCells(job.collisionSize);
			if (!job.jobStarted) {
				if ((graph.reservedFor != null) && (graph.reservedFor != this)) {
					break JobsLoop;
				}
				graph.owner = this;
				graph.reservedFor = null;
				this.pathfindJobId = ++graph.lastJobId;
				this.totalIterations = 0;
				this.jobNanos = 0;
				job.jobStarted = true;
				PerfStats.pathJob();
				workIterations += 5; // setup of job predicted cost
				job.goalX = job.goal.x;
				job.goalY = job.goal.y;
				job.goalMoved = false;
				job.goalBesideUnit = false;
				job.besideBlocker = null;
				job.goalOutOfReach = false;
				job.goalOnBlockedGround = false;
				job.weightForHittingWalls = 1E9f;
				job.bestCell = -1;
				job.bestDistance = Float.MAX_VALUE;
				job.bestGLimit = Double.MAX_VALUE;
				job.limitExtended = false;
				job.metScriptedTerrain = false;
				job.nearSideCell = -1;
				job.nearSideDistance = Float.MAX_VALUE;
				job.shortSearch = searchesShort(simulation, job.ignoreIntersectionsWithThisUnit);
				if (!job.shortSearch) {
					if (!this.pathingGrid.isPathable(job.goalX, job.goalY, job.movementType, job.collisionSize)) {
						if (!moveGoalToNearest(job, true)) {
							moveGoalToNearest(job, false);
						}
						job.goalOnBlockedGround = job.goalMoved;
					}
					else if (!isPathableDynamically(job.goalX, job.goalY, job.ignoreIntersectionsWithThisUnit,
							job.ignoreIntersectionsWithThisSecondUnit, job.movementType)) {
						if (!moveGoalBesideStandingUnit(job)) {
							moveGoalToNearest(job, true);
						}
					}
				}
				if (!job.shortSearch && (!this.pathingGrid.isPathable(job.goalX, job.goalY, job.movementType,
						job.collisionSize)
						|| !isPathableDynamically(job.goalX, job.goalY, job.ignoreIntersectionsWithThisUnit,
								job.ignoreIntersectionsWithThisSecondUnit, job.movementType))) {
					job.weightForHittingWalls = 5E2f;
				}
				if ((job.startX == job.goalX) && (job.startY == job.goalY)) {
					finishJob();
					job.queueItem.pathFound(job.goal, new LinkedList<>(), !job.goalMoved || job.goalBesideUnit,
							simulation);
					continue JobsLoop;
				}
				if (!job.shortSearch && (job.queueItem != null) && !job.goalMoved) {
					final CPathPoint cell = job.queueItem.approachCell(simulation, job.startX, job.startY);
					if ((cell != null) && this.pathingGrid.isPathable(cell.x, cell.y, job.movementType,
							job.collisionSize)
							&& segmentClear(job, job.startX, job.startY, cell.x, cell.y)) {
						finishJob();
						job.queueItem.pathFoundToApproachCell(job.goal, cell, simulation);
						continue JobsLoop;
					}
				}
				// Measured: an attack on a standing unit keeps to the rows of cell centers, as 1.29 does
				if (isCollisionSizeBetterSuitedForCorners(job.collisionSize) && !((job.queueItem != null)
						&& job.queueItem.walksRowsToStandingTarget(simulation))) {
					job.searchGrid = graph.searchGrid(this.pathingGrid, true);
					job.gridMapping = GridMapping.CORNERS;
				}
				else {
					job.searchGrid = graph.searchGrid(this.pathingGrid, false);
					job.gridMapping = GridMapping.CELLS;
				}
				if ((job.weightForHittingWalls >= 1E9f) && !job.shortSearch
						&& !this.pathingGrid.isTerrainScripted(job.goalX, job.goalY, SCRIPTED_GOAL_CELLS) && !aimAtReachablePart(job)) {
					// Measured: the original walks to the reachable point nearest the target and stops there; with no
					// such point nearer than where it stands, it stays.
					final LinkedList<CPathPoint> stay = new LinkedList<>();
					stay.add(new CPathPoint(job.startX, job.startY));
					finishJob();
					job.queueItem.pathFound(job.goal, stay, false, simulation);
					continue JobsLoop;
				}
				final double straightCells = StrictMath.hypot(job.goalX - job.startX, job.goalY - job.startY) / 32;
				job.straightCells = straightCells;
				job.nearSideDecided = job.shortSearch || !job.goalOnBlockedGround || (job.queueItem == null)
						|| !job.queueItem.isOrderedPointMove();
				job.iterationLimit = job.shortSearch ? SHORT_SEARCH_ITERATIONS
						: (int) Math.min(this.pathingGridCellCount,
								Math.max(MIN_SEARCH_ITERATIONS, 4 * straightCells * straightCells));
				final SearchGrid grid = job.searchGrid;
				final int goalCellY = Math.min(Math.max(0, job.gridMapping.getY(this.pathingGrid, job.goalY)),
						grid.height - 1);
				final int goalCellX = Math.min(Math.max(0, job.gridMapping.getX(this.pathingGrid, job.goalX)),
						grid.width - 1);
				final int mostLikelyGoal = (goalCellY * grid.width) + goalCellX;
				grid.touch(mostLikelyGoal, this.pathfindJobId);
				final double bestGoalDistance = distance(grid.xs[goalCellX], grid.ys[goalCellY], job.goalX,
						job.goalY);
				this.goals = 0;
				for (int i = goalCellX - 1; i <= (goalCellX + 1); i++) {
					for (int j = goalCellY - 1; j <= (goalCellY + 1); j++) {
						if ((j >= 0) && (j < grid.height)) {
							if ((i >= 0) && (i < grid.width)) {
								final int possibleGoal = (j * grid.width) + i;
								grid.touch(possibleGoal, this.pathfindJobId);
								if (distance(grid.xs[i], grid.ys[j], job.goalX, job.goalY) <= bestGoalDistance) {
									this.goalCells[this.goals] = possibleGoal;
									this.goalCellX[this.goals] = grid.xs[i];
									this.goalCellY[this.goals] = grid.ys[j];
									this.goals++;
								}
							}
						}
					}
				}
				job.goalNodesBlocked = false;
				if (job.weightForHittingWalls >= 1E9f) {
					// no node the search could end on is free: it can only get near the goal, however far it looks
					final int goalEpoch = graph.pathableEpoch(this.pathfindJobId, simulation.getGameTurnTick());
					job.goalNodesBlocked = true;
					for (int i = 0; i < this.goals; i++) {
						final int goalCell = this.goalCells[i];
						if (nodePathable(job, grid, goalEpoch, goalCell, this.goalCellX[i], this.goalCellY[i])) {
							job.goalNodesBlocked = false;
							break;
						}
					}
				}
				// a unit a script has put off the map searches from the nearest grid point instead of past the arrays
				final int startGridY = Math.min(Math.max(0, job.gridMapping.getY(this.pathingGrid, job.startY)),
						grid.height - 1);
				final int startGridX = Math.min(Math.max(0, job.gridMapping.getX(this.pathingGrid, job.startX)),
						grid.width - 1);
				job.enclosedGoalRadius = 0;
				if ((job.weightForHittingWalls >= 1E9f) && !job.goalNodesBlocked && !job.shortSearch) {
					job.enclosedGoalRadius = enclosedGoalRadius(job, grid,
							graph.pathableEpoch(this.pathfindJobId, simulation.getGameTurnTick()),
							(startGridY * grid.width) + startGridX);
				}
				grid.clearOpen();

				final int startCell = (startGridY * grid.width) + startGridX;
				grid.touch(startCell, this.pathfindJobId);
				final float startPointX = grid.xs[startGridX];
				final float startPointY = grid.ys[startGridY];
				if (job.startX > startPointX) {
					job.startGridMinX = startGridX;
					job.startGridMaxX = startGridX + 1;
				}
				else if (job.startX < startPointX) {
					job.startGridMinX = startGridX - 1;
					job.startGridMaxX = startGridX;
				}
				else {
					job.startGridMinX = startGridX;
					job.startGridMaxX = startGridX;
				}
				if (job.startY > startPointY) {
					job.startGridMinY = startGridY;
					job.startGridMaxY = startGridY + 1;
				}
				else if (job.startY < startPointY) {
					job.startGridMinY = startGridY - 1;
					job.startGridMaxY = startGridY;
				}
				else {
					job.startGridMinY = startGridY;
					job.startGridMaxY = startGridY;
				}
				for (int cellX = job.startGridMinX; cellX <= job.startGridMaxX; cellX++) {
					for (int cellY = job.startGridMinY; cellY <= job.startGridMaxY; cellY++) {
						if ((cellX >= 0) && (cellX < this.pathingGrid.getWidth()) && (cellY >= 0)
								&& (cellY < this.pathingGrid.getHeight())) {
							final int possibleNode = (cellY * grid.width) + cellX;
							grid.touch(possibleNode, this.pathfindJobId);
							final float x = grid.xs[cellX];
							final float y = grid.ys[cellY];
							if (pathableBetween(job.ignoreIntersectionsWithThisUnit,
									job.ignoreIntersectionsWithThisSecondUnit, job.startX, job.startY, job.movementType,
									job.collisionSize, x, y)) {

								final double tentativeScore = distance(x, y, job.startX, job.startY);
								job.bestGLimit = Math.min(job.bestGLimit, tentativeScore + job.weightForHittingWalls);
								grid.setScores(possibleNode, tentativeScore, tentativeScore + h(x, y));
								openAdd(grid, possibleNode);

							}
							else {
								final double tentativeScore = job.weightForHittingWalls;
								// A unit squeezed among others at its start still looks for the nearest point it
								// can walk to.
								job.bestGLimit = Math.min(job.bestGLimit, tentativeScore + job.weightForHittingWalls);
								grid.setScores(possibleNode, tentativeScore, tentativeScore + h(x, y));
								openAdd(grid, possibleNode);

							}
						}
					}
				}
			}

			final SearchGrid grid = job.searchGrid;
			final int epoch = graph.pathableEpoch(this.pathfindJobId, simulation.getGameTurnTick());
			while (!grid.isEmpty()) {
				final int current = grid.poll();
				grid.touch(current, this.pathfindJobId);
				grid.setOpen(current, false);
				if (isGoal(current)) {
					if (crossesBlockedGround(job, current)) {
						// Measured on 1.29: a target walled off by destructables (a ship before gates across its
						// river) cannot be reached; the unit goes to the reachable point nearest to it, not to the wall.
						break;
					}
					final LinkedList<CPathPoint> totalPath = buildPath(job, current, true);
					finishJob();
					job.queueItem.pathFound(job.goal, totalPath, (!job.goalMoved || job.goalBesideUnit) && !job.goalOutOfReach,
							simulation);
					continue JobsLoop;
				}
				final int currentRow = current / grid.width;
				final float currentX = grid.xs[current - (currentRow * grid.width)];
				final float currentY = grid.ys[currentRow];
				final double currentG = grid.g(current);
				trackBest(job, current, currentX, currentY, currentG);
				expandNeighbors(job, grid, epoch, current, currentX, currentY, currentG);
				workIterations++;
				this.totalIterations++;
				PerfStats.pathIteration();
				if (!job.nearSideDecided
						&& (this.totalIterations >= Math.max(NEAR_SIDE_ITERATIONS, job.iterationLimit / 8))
						&& staysOnNearSide(job)) {
					finishOnNearSide(job, simulation);
					continue JobsLoop;
				}
				// Measured: the original walks across a whole melee map; a fixed cap stopped such paths after a few
				// steps. A short chase into a crowd of units has no way through and searched the whole map each time
				// a unit in a fight moved, so the limit grows with the distance instead.
				if (job.metScriptedTerrain && (this.totalIterations > Math.min(job.iterationLimit,
						Math.max(MIN_SCRIPTED_SEARCH_ITERATIONS, 4 * job.straightCells * job.straightCells)))) {
					break;
				}
				if ((job.enclosedGoalRadius > 0)
						&& (job.bestDistance <= (job.enclosedGoalRadius + ENCLOSED_STOP_MARGIN))
						&& ((currentG + h(currentX, currentY)) > ((job.straightCells * 32) + ENCLOSED_FLOOD_MARGIN))) {
					break;
				}
				if (this.totalIterations > job.iterationLimit) {
					// Measured on 1.29: a unit ordered to a point behind a long wall walks out and round it, even when
					// that way is many times the straight distance. Such a search (a far goal, or one that has not got
					// any nearer to the goal) goes on once, with a much larger limit; short chases keep the cheap one. So does one to
					// the free point nearest a goal on blocked ground (a grove): the original walks the whole way round to it.
					// A search that has met terrain changed by a script does not, nor one whose goal nodes are all
					// blocked: it can only end near the goal, so looking further for a way in finds nothing.
					if (!job.limitExtended && !job.shortSearch && !job.metScriptedTerrain && !job.goalNodesBlocked && ((StrictMath.hypot(job.goalX - job.startX,
							job.goalY - job.startY) >= DETOUR_MIN_DISTANCE) || job.goalOnBlockedGround || !nearerThanStart(job))) {
						job.limitExtended = true;
						job.iterationLimit = (int) Math.min(this.pathingGridCellCount,
								(long) job.iterationLimit * DETOUR_ITERATION_FACTOR);
					}
					else {
						break;
					}
				}
				if (workIterations >= tickBudget) {
					// breaking jobs loop will implicitly exit without calling pathFound() below
					break JobsLoop;
				}
			}
			// Measured: the original walks to the reachable point nearest the target and stops there.
			final LinkedList<CPathPoint> partialPath;
			if (nearerThanStart(job)) {
				partialPath = buildPath(job, job.bestCell, false);
				final float bestX = job.searchGrid.xs[job.bestCell % job.searchGrid.width];
				final float bestY = job.searchGrid.ys[job.bestCell / job.searchGrid.width];
				if (job.shortSearch && ((job.bestEndX != bestX) || (job.bestEndY != bestY))) {
					final List<CPathPoint> slide = new ArrayList<>();
					slideTowardGoal(job, bestX, bestY, slide);
					slide.add(new CPathPoint(job.bestEndX, job.bestEndY));
					// The slide is tested in steps; a leg of it that clips a cell corner between them ends it there.
					float legX = bestX;
					float legY = bestY;
					for (final CPathPoint point : slide) {
						if (!segmentClearOfTerrain(job, legX, legY, point.x - legX, point.y - legY)) {
							break;
						}
						partialPath.add(point);
						legX = point.x;
						legY = point.y;
					}
					if (job.allowSmoothing) {
						final CUnit unit = job.ignoreIntersectionsWithThisUnit;
						float anchorX = unit == null ? job.startX : unit.getX();
						float anchorY = unit == null ? job.startY : unit.getY();
						final ListIterator<CPathPoint> points = partialPath.listIterator();
						while (points.hasNext()) {
							final CPathPoint point = points.next();
							if (points.hasNext()) {
								final CPathPoint next = partialPath.get(points.nextIndex());
								if (segmentClear(job, anchorX, anchorY, next.x, next.y)) {
									points.remove();
									continue;
								}
							}
							anchorX = point.x;
							anchorY = point.y;
						}
					}
				}
			}
			else {
				partialPath = new LinkedList<>();
				partialPath.add(new CPathPoint(job.startX, job.startY));
			}
			finishJob();
			job.queueItem.pathFound(job.goal, partialPath, false, simulation);
		}
	}

	/**
	 * Runs the search loop over a stretch of open ground on stamps no real search has, so that its code has been
	 * compiled by the time the first real search runs (a phone runs code it has not compiled yet some tens of times
	 * slower, and a crowd's first searches are such). It changes nothing a search reads: the points it touches are
	 * reset by the first search that does.
	 */
	private void warmSearch(final SearchGrid grid, final MovementType movementType, final float collisionSize,
			final boolean corner) {
		final int startCellX = grid.width / 2;
		final int startCellY = grid.height / 2;
		final int goalCellX = Math.min(grid.width - 1, startCellX + 70);
		final int goalCellY = Math.min(grid.height - 1, startCellY + 50);
		final PathfindingJob job = new PathfindingJob(null, null, grid.xs[startCellX], grid.ys[startCellY],
				new CPathPoint(grid.xs[goalCellX], grid.ys[goalCellY]), movementType, collisionSize, true, null);
		job.searchGrid = grid;
		job.gridMapping = corner ? GridMapping.CORNERS : GridMapping.CELLS;
		job.goalX = job.goal.x;
		job.goalY = job.goal.y;
		job.weightForHittingWalls = 5E2f;
		job.bestGLimit = Double.MAX_VALUE;
		job.bestDistance = Float.MAX_VALUE;
		job.nearSideDecided = true;
		this.pathfindJobId = WARM_UP_STAMP;
		this.footprintCells = PathingGrid.getFootprintCells(collisionSize);
		this.tempRect.set(0, 0, collisionSize * 2, collisionSize * 2);
		final int goalCell = (goalCellY * grid.width) + goalCellX;
		this.goals = 1;
		this.goalCells[0] = goalCell;
		this.goalCellX[0] = grid.xs[goalCellX];
		this.goalCellY[0] = grid.ys[goalCellY];
		grid.clearOpen();
		final int startCell = (startCellY * grid.width) + startCellX;
		grid.touch(startCell, WARM_UP_STAMP);
		grid.setScores(startCell, 0, h(grid.xs[startCellX], grid.ys[startCellY]));
		openAdd(grid, startCell);
		for (int i = 0; (i < WARM_UP_ITERATIONS) && !grid.isEmpty(); i++) {
			final int current = grid.poll();
			grid.touch(current, WARM_UP_STAMP);
			grid.setOpen(current, false);
			if (isGoal(current)) {
				break;
			}
			final int currentRow = current / grid.width;
			final float currentX = grid.xs[current - (currentRow * grid.width)];
			final float currentY = grid.ys[currentRow];
			final double currentG = grid.g(current);
			trackBest(job, current, currentX, currentY, currentG);
			expandNeighbors(job, grid, WARM_UP_EPOCH, current, currentX, currentY, currentG);
		}
		grid.clearOpen();
	}

	/** The nearest point to the goal the search has reached, kept for a search that ends without reaching it. */
	private void trackBest(final PathfindingJob job, final int current, final float currentX, final float currentY,
			final double currentG) {
		if (currentG < job.bestGLimit) {
			if (job.shortSearch) {
				slideTowardGoal(job, currentX, currentY, null);
				final float distanceToGoal = (float) StrictMath.hypot(job.goalX - this.slideX,
						job.goalY - this.slideY);
				if (distanceToGoal < (job.bestDistance - 1)) {
					job.bestDistance = distanceToGoal;
					job.bestCell = current;
					job.bestEndX = this.slideX;
					job.bestEndY = this.slideY;
				}
			}
			else {
				if (!job.nearSideDecided) {
					final float distanceToTarget = (float) distance(currentX, currentY, job.goal.x, job.goal.y);
					if (distanceToTarget < job.nearSideDistance) {
						job.nearSideDistance = distanceToTarget;
						job.nearSideCell = current;
					}
				}
				final float distanceToGoal = (float) distance(currentX, currentY, job.goalX, job.goalY);
				if (distanceToGoal < job.bestDistance) {
					job.bestDistance = distanceToGoal;
					job.bestCell = current;
				}
			}
		}
	}

	/** Gives each neighbor of a point the better of its scores; kept apart from the search loop so a small, hot method. */
	private void expandNeighbors(final PathfindingJob job, final SearchGrid grid, final int epoch, final int current,
			final float currentX, final float currentY, final double currentG) {
		final int currentDirection = grid.direction(current);
		final PathingGrid pathingGrid = this.pathingGrid;
		final GridMapping gridMapping = job.gridMapping;
		final int currentMappedX = gridMapping.getX(pathingGrid, currentX);
		final int currentMappedY = gridMapping.getY(pathingGrid, currentY);
		if (this.stepPoints == null) {
			this.stepPoints = new float[6];
			this.stepCells = new int[6];
		}
		// The eight neighbors share three columns and three rows of points.
		final float[] stepPoints = this.stepPoints;
		final int[] stepCells = this.stepCells;
		final int cellsWide = pathingGrid.getWidth();
		final int cellsHigh = pathingGrid.getHeight();
		for (int k = 0; k < 3; k++) {
			final float x = currentX + ((k - 1) * 32);
			final int cellX = pathingGrid.getCellX(x);
			stepPoints[k] = x;
			stepCells[k] = ((cellX >= 0) && (cellX < cellsWide)) ? gridMapping.getX(pathingGrid, x) : NOT_ON_MAP;
			final float y = currentY + ((k - 1) * 32);
			final int cellY = pathingGrid.getCellY(y);
			stepPoints[3 + k] = y;
			stepCells[3 + k] = ((cellY >= 0) && (cellY < cellsHigh)) ? gridMapping.getY(pathingGrid, y) : NOT_ON_MAP;
		}
		for (final Direction direction : Direction.VALUES) {
			final int column = direction.xOffset + 1;
			final int row = direction.yOffset + 4;
			final int neighborX = stepCells[column];
			final int neighborY = stepCells[row];
			if ((neighborX != NOT_ON_MAP) && (neighborY != NOT_ON_MAP)) {
				final float x = stepPoints[column];
				final float y = stepPoints[row];
				double turnCost;
				if ((currentDirection != 0) && ((direction.ordinal() + 1) != currentDirection)) {
					turnCost = 0.25;
				}
				else {
					turnCost = 0;
				}
				double tentativeScore = currentG + ((direction.length + turnCost) * 32);
				final int neighbor = (neighborY * grid.width) + neighborX;
				if (!nodePathable(job, grid, epoch, neighbor, x, y)
						|| !nodePathable(job, grid, epoch, (neighborY * grid.width) + currentMappedX, currentX,
								y)
						|| !nodePathable(job, grid, epoch, (currentMappedY * grid.width) + neighborX, x,
								currentY)) {
					tentativeScore += (direction.length) * job.weightForHittingWalls;
					if (!job.metScriptedTerrain
							&& pathingGrid.isTerrainScripted(x, y, SCRIPTED_TERRAIN_CELLS)) {
						job.metScriptedTerrain = true;
					}
				}
				grid.touch(neighbor, this.pathfindJobId);
				if (tentativeScore < grid.g(neighbor)) {
					grid.setCameFrom(neighbor, current, direction.ordinal() + 1);
					grid.setScores(neighbor, tentativeScore,
							tentativeScore + h(grid.xs[neighborX], grid.ys[neighborY]));
					if (!grid.isOpen(neighbor)) {
						openAdd(grid, neighbor);
					}
				}
			}
		}
	}

	/**
	 * Whether a straight line of free points leaves the goal cell for a good way in some direction, so the ground round
	 * it cannot be a pocket and the fill (which most jobs would pay for in full on open ground) is skipped.
	 */
	private boolean openAround(final PathfindingJob job, final SearchGrid grid, final int goalCell) {
		final int goalX = goalCell % grid.width;
		final int goalY = goalCell / grid.width;
		for (final Direction direction : Direction.VALUES) {
			int step = 1;
			for (; step <= ENCLOSED_OPEN_REACH; step++) {
				final int x = goalX + (direction.xOffset * step);
				final int y = goalY + (direction.yOffset * step);
				if ((x < 0) || (y < 0) || (x >= grid.width) || (y >= grid.height)
						|| !this.pathingGrid.isPathable(grid.xs[x], grid.ys[y], job.movementType, job.collisionSize)
						|| !isPathableDynamically(grid.xs[x], grid.ys[y], job.ignoreIntersectionsWithThisUnit,
								secondUnitAt(job, grid.xs[x], grid.ys[y]), job.movementType)) {
					break;
				}
			}
			if (step > ENCLOSED_OPEN_REACH) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The distance from the goal to the farthest point of the free ground round it, when standing units close that
	 * ground off completely within a few cells; 0 when it is open to the rest of the map, or holds the unit itself.
	 */
	private float enclosedGoalRadius(final PathfindingJob job, final SearchGrid grid, final int epoch,
			final int startCell) {
		int goalCell = -1;
		for (int i = 0; i < this.goals; i++) {
			if (nodePathable(job, grid, epoch, this.goalCells[i], this.goalCellX[i], this.goalCellY[i])) {
				goalCell = this.goalCells[i];
				break;
			}
		}
		if (goalCell < 0) {
			return 0;
		}
		if (openAround(job, grid, goalCell)) {
			return 0;
		}
		final int seenSize = ENCLOSED_POCKET_CELLS * 4;
		if (this.enclosedSeen == null) {
			this.enclosedSeen = new int[seenSize];
			this.enclosedQueue = new int[ENCLOSED_POCKET_CELLS];
		}
		else {
			Arrays.fill(this.enclosedSeen, 0);
		}
		final int[] seen = this.enclosedSeen;
		final int[] queue = this.enclosedQueue;
		int head = 0;
		int tail = 0;
		queue[tail++] = goalCell;
		seen[(goalCell * 31) % seenSize] = goalCell + 1;
		float radius = 0;
		boolean unitsInRing = false;
		while (head < tail) {
			final int cell = queue[head++];
			if (cell == startCell) {
				return 0;
			}
			final int cellX = cell % grid.width;
			final int cellY = cell / grid.width;
			radius = Math.max(radius,
					(float) distance(grid.xs[cellX], grid.ys[cellY], job.goalX, job.goalY));
			for (final Direction direction : Direction.VALUES) {
				final int nextX = cellX + direction.xOffset;
				final int nextY = cellY + direction.yOffset;
				if ((nextX < 0) || (nextY < 0) || (nextX >= grid.width) || (nextY >= grid.height)) {
					continue;
				}
				final int next = (nextY * grid.width) + nextX;
				// Not through nodePathable: its answer is kept by cell for the search, which tests other points of it.
				if (!this.pathingGrid.isPathable(grid.xs[nextX], grid.ys[nextY], job.movementType,
						job.collisionSize)) {
					continue;
				}
				if (!isPathableDynamically(grid.xs[nextX], grid.ys[nextY], job.ignoreIntersectionsWithThisUnit,
						secondUnitAt(job, grid.xs[nextX], grid.ys[nextY]), job.movementType)) {
					unitsInRing = true;
					continue;
				}
				int slot = (next * 31) % seenSize;
				boolean known = false;
				while (seen[slot] != 0) {
					if (seen[slot] == (next + 1)) {
						known = true;
						break;
					}
					slot = (slot + 1) % seenSize;
				}
				if (known) {
					continue;
				}
				if (tail >= ENCLOSED_POCKET_CELLS) {
					return 0;
				}
				seen[slot] = next + 1;
				queue[tail++] = next;
			}
		}
		// A pocket that terrain alone closes off is left to the full search, as before.
		return unitsInRing ? radius : 0;
	}

	/**
	 * Whether the search reached its (pathable) goal only by a step through terrain or destructables the unit cannot
	 * cross. Steps through standing units do not count: those move away.
	 */
	private boolean crossesBlockedGround(final PathfindingJob job, final int end) {
		final SearchGrid grid = job.searchGrid;
		if ((job.weightForHittingWalls < 1E9f) || (grid.g(end) < job.weightForHittingWalls)) {
			return false;
		}
		int steps = 0;
		for (int node = end; (grid.cameFrom(node) >= 0) && (steps <= this.pathingGridCellCount); node = grid.cameFrom(node)) {
			steps++;
			final int from = grid.cameFrom(node);
			final float nodeX = grid.xs[node % grid.width];
			final float nodeY = grid.ys[node / grid.width];
			final float fromX = grid.xs[from % grid.width];
			final float fromY = grid.ys[from / grid.width];
			if (((grid.g(node) - grid.g(from)) >= job.weightForHittingWalls) && (!this.pathingGrid.isPathable(nodeX,
					nodeY, job.movementType, job.collisionSize)
					|| !this.pathingGrid.isPathable(fromX, nodeY, job.movementType, job.collisionSize)
					|| !this.pathingGrid.isPathable(nodeX, fromY, job.movementType, job.collisionSize))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * When no grid point next to the job's goal can be reached from its start (ignoring units), moves the goal to the
	 * reachable point nearest to it. Returns false when that point is no nearer to the goal than the start.
	 */
	private boolean aimAtReachablePart(final PathfindingJob job) {
		final Reachability reach = this.graph.reachability(this.pathingGrid, job.movementType, job.collisionSize,
				job.searchGrid == this.graph.corners);
		final int startX = job.gridMapping.getX(this.pathingGrid, job.startX);
		final int startY = job.gridMapping.getY(this.pathingGrid, job.startY);
		final int[] startParts = new int[9];
		int startPartCount = 0;
		for (int x = startX - 1; x <= (startX + 1); x++) {
			for (int y = startY - 1; y <= (startY + 1); y++) {
				final float worldX = reach.worldX(this.pathingGrid, x);
				final float worldY = reach.worldY(this.pathingGrid, y);
				if ((Math.abs(worldX - job.startX) >= 32) || (Math.abs(worldY - job.startY) >= 32)) {
					continue;
				}
				final int part = reach.part(this.graph, this.pathingGrid, x, y);
				if ((part != 0) && this.pathingGrid.isPathable(worldX, job.startY, job.movementType, job.collisionSize)
						&& this.pathingGrid.isPathable(job.startX, worldY, job.movementType, job.collisionSize)) {
					startParts[startPartCount++] = part;
				}
			}
		}
		if (startPartCount == 0) {
			// squeezed in among obstacles: the search finds its own way out
			return true;
		}
		final int goalX = job.gridMapping.getX(this.pathingGrid, job.goalX);
		final int goalY = job.gridMapping.getY(this.pathingGrid, job.goalY);
		for (int x = goalX - 1; x <= (goalX + 1); x++) {
			for (int y = goalY - 1; y <= (goalY + 1); y++) {
				if (contains(startParts, startPartCount, reach.label(x, y))) {
					return true;
				}
			}
		}
		int bestX = -1;
		int bestY = -1;
		double bestDistance = Double.MAX_VALUE;
		final int maxRing = Math.max(reach.width, reach.height);
		for (int ring = 0; (ring <= maxRing) && (((ring - 1) * 32) <= bestDistance); ring++) {
			for (int i = -ring; i <= ring; i++) {
				// Only the cells of the ring itself: the whole row on its two sides, the two ends in between.
				final int jStep = (Math.abs(i) == ring) ? 1 : Math.max(1, 2 * ring);
				for (int j = -ring; j <= ring; j += jStep) {
					final int x = goalX + i;
					final int y = goalY + j;
					if ((x < 0) || (y < 0) || (x >= reach.width) || (y >= reach.height)) {
						continue;
					}
					final float offsetX = reach.worldX(this.pathingGrid, x) - job.goalX;
					final float offsetY = reach.worldY(this.pathingGrid, y) - job.goalY;
					// hypot is exact to within an ulp, so a square clearly above the best cannot be nearer
					if (((double) offsetX * offsetX) + ((double) offsetY * offsetY) > (bestDistance * bestDistance
							* 1.0000001)) {
						continue;
					}
					final double distance = StrictMath.hypot(offsetX, offsetY);
					if ((distance < bestDistance) && contains(startParts, startPartCount, reach.label(x, y))) {
						bestDistance = distance;
						bestX = x;
						bestY = y;
					}
				}
			}
		}
		if (bestX < 0) {
			return true;
		}
		if (bestDistance >= (StrictMath.hypot(job.goalX - job.startX, job.goalY - job.startY) - 16)) {
			return false;
		}
		job.goalX = reach.worldX(this.pathingGrid, bestX);
		job.goalY = reach.worldY(this.pathingGrid, bestY);
		job.goalMoved = true;
		job.goalOutOfReach = true;
		return true;
	}

	private float slideX;
	private float slideY;

	/**
	 * Walks from a point straight toward the job's goal, sliding along what blocks it, as far as it gets nearer. With
	 * turns given, the points where the slide changes direction are added to it, so the unit can follow the slide.
	 */
	private void slideTowardGoal(final PathfindingJob job, final float fromX, final float fromY,
			final List<CPathPoint> turns) {
		float x = fromX;
		float y = fromY;
		int lastMove = 0;
		for (int i = 0; i < SLIDE_STEPS; i++) {
			final float dx = job.goalX - x;
			final float dy = job.goalY - y;
			final double distance = StrictMath.hypot(dx, dy);
			if (distance <= SLIDE_STEP) {
				break;
			}
			final float stepX = (float) ((dx * SLIDE_STEP) / distance);
			final float stepY = (float) ((dy * SLIDE_STEP) / distance);
			final int move;
			if (slidePathable(job, x + stepX, y + stepY)) {
				move = 1;
			}
			else if ((Math.abs(stepX) >= Math.abs(stepY)) && slidePathable(job, x + stepX, y)) {
				move = 2;
			}
			else if (slidePathable(job, x, y + stepY)) {
				move = 3;
			}
			else if ((Math.abs(stepX) < Math.abs(stepY)) && slidePathable(job, x + stepX, y)) {
				move = 2;
			}
			else {
				break;
			}
			if ((turns != null) && (lastMove != 0) && (move != lastMove)) {
				turns.add(new CPathPoint(x, y));
			}
			lastMove = move;
			if (move != 3) {
				x += stepX;
			}
			if (move != 2) {
				y += stepY;
			}
		}
		this.slideX = x;
		this.slideY = y;
	}

	private boolean slidePathable(final PathfindingJob job, final float x, final float y) {
		return this.pathingGrid.isPathable(x, y, job.movementType, job.collisionSize) && isPathableDynamically(x, y,
				job.ignoreIntersectionsWithThisUnit, job.ignoreIntersectionsWithThisSecondUnit, job.movementType);
	}

	private static boolean searchesShort(final CSimulation simulation, final CUnit unit) {
		return (unit != null) && simulation.isNeutralPassive(unit.getPlayerIndex()) && unit.isWanderer();
	}

	private static boolean contains(final int[] values, final int count, final int value) {
		if (value == 0) {
			return false;
		}
		for (int i = 0; i < count; i++) {
			if (values[i] == value) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearerThanStart(final PathfindingJob job) {
		return (job.bestCell >= 0)
				&& (job.bestDistance < (StrictMath.hypot(job.goalX - job.startX, job.goalY - job.startY) - 16));
	}

	/**
	 * Measured: the original cuts straight across a stretch of path of 1229, but not one of 2034; the cap on one cut is
	 * in between.
	 */
	private static final float MAX_SMOOTHED_SEGMENT = 1536f;

	/**
	 * The path from the start through the searched nodes to endNode (and on to the goal when it was reached). With
	 * smoothing, a node is skipped whenever the unit can walk straight past it, so units walk any-angle lines instead
	 * of the grid's zigzag.
	 */
	private LinkedList<CPathPoint> buildPath(final PathfindingJob job, final int endNode, final boolean reachedGoal) {
		final SearchGrid grid = job.searchGrid;
		final ArrayList<CPathPoint> chain = new ArrayList<>();
		int node = endNode;
		while (node >= 0) {
			chain.add(new CPathPoint(grid.xs[node % grid.width], grid.ys[node / grid.width]));
			node = grid.cameFrom(node);
			if (chain.size() > this.pathingGridCellCount) {
				Log.error(new IllegalStateException("PATHING SYSTEM ERROR: cycle in the path near " + chain.get(0)
						+ ", unit " + CUnit.maybeMeaningfulName(job.ignoreIntersectionsWithThisUnit)));
				return new LinkedList<>();
			}
		}
		Collections.reverse(chain);
		if (reachedGoal) {
			if (!job.goalMoved) {
				chain.add(job.goal);
			}
			else {
				chain.add(new CPathPoint(job.goalX, job.goalY));
			}
		}
		final LinkedList<CPathPoint> path = new LinkedList<>();
		float anchorX = job.startX;
		float anchorY = job.startY;
		final CUnit unit = job.ignoreIntersectionsWithThisUnit;
		if (job.allowSmoothing && (unit != null) && ((unit.getX() != job.startX) || (unit.getY() != job.startY))
				&& !chain.isEmpty()) {
			// The unit walked on toward its target while the search waited its turn. The path is smoothed from where
			// it stands now; when the first point cannot be walked to straight from there, it goes back to where the
			// search started.
			if (segmentClear(job, unit.getX(), unit.getY(), chain.get(0).x, chain.get(0).y)) {
				anchorX = unit.getX();
				anchorY = unit.getY();
			}
			else if (segmentClear(job, unit.getX(), unit.getY(), job.startX, job.startY)) {
				path.add(new CPathPoint(job.startX, job.startY));
			}
		}
		Direction lastDirection = null;
		for (int i = 0; i < chain.size(); i++) {
			final CPathPoint point = chain.get(i);
			if (i < (chain.size() - 1)) {
				final CPathPoint next = chain.get(i + 1);
				if (job.allowSmoothing) {
					if ((next.distance(anchorX, anchorY) <= MAX_SMOOTHED_SEGMENT)
							&& segmentClear(job, anchorX, anchorY, next.x, next.y)) {
						continue;
					}
				}
				else {
					final Direction direction = Direction.of(next.x - point.x, next.y - point.y);
					if ((direction != null) && (direction == lastDirection)) {
						lastDirection = direction;
						continue;
					}
					lastDirection = direction;
				}
			}
			path.add(point);
			anchorX = point.x;
			anchorY = point.y;
		}
		return path;
	}

	/** Whether the job's unit can walk the straight line between two points: the same tests its movement makes. */
	private boolean segmentClear(final PathfindingJob job, final float x0, final float y0, final float x1,
			final float y1) {
		final float dx = x1 - x0;
		final float dy = y1 - y0;
		if (!segmentClearOfTerrain(job, x0, y0, dx, dy)) {
			return false;
		}
		final int steps = Math.max(1, (int) Math.ceil(StrictMath.hypot(dx, dy) / 8));
		if (steps > 2) {
			// with no standing unit near the line, none can stand on a point of it
			final float half = job.collisionSize + (usesFootprint(job.movementType) ? 32f : 0f);
			this.segmentRect.set(Math.min(x0, x1) - half, Math.min(y0, y1) - half, Math.abs(dx) + (2 * half),
					Math.abs(dy) + (2 * half));
			if (!this.worldCollision.intersectsStandingUnitOtherThan(this.segmentRect,
					job.ignoreIntersectionsWithThisUnit,
					(job.besideBlocker != null) ? null : job.ignoreIntersectionsWithThisSecondUnit,
					job.movementType, this.gameTurnTick)) {
				return true;
			}
		}
		for (int step = 1; step <= steps; step++) {
			final float x = x0 + ((dx * step) / steps);
			final float y = y0 + ((dy * step) / steps);
			if (!isPathableDynamically(x, y, job.ignoreIntersectionsWithThisUnit, secondUnitAt(job, x, y),
					job.movementType)) {
				return false;
			}
		}
		return true;
	}

	/** The unit to the side of which a goal was set stops blocking only close to that goal, so paths lead around it. */
	private static CUnit secondUnitAt(final PathfindingJob job, final float x, final float y) {
		if ((job.besideBlocker != null) && (job.ignoreIntersectionsWithThisSecondUnit == job.besideBlocker)
				&& (StrictMath.hypot(x - job.goalX, y - job.goalY) > BESIDE_GOAL_REACH)) {
			return null;
		}
		return job.ignoreIntersectionsWithThisSecondUnit;
	}

	/**
	 * The ground test of segmentClear, without gaps: the cells under the unit (PathingGrid.getFootprintCells) change
	 * only where its position crosses a cell edge, so the line is tested at every such crossing and once between each
	 * two. Fixed steps missed the corners of cells that stick into the line (diagonal fences, rows of crates and
	 * trees); the unit then walked into one, stopped and searched again.
	 */
	private boolean segmentClearOfTerrain(final PathfindingJob job, final float x0, final float y0, final float dx,
			final float dy) {
		int count = 0;
		final float collisionSize = job.collisionSize;
		count = addCellEdgeCrossings(count, x0, dx, true);
		count = addCellEdgeCrossings(count, y0, dy, false);
		count = addCrossing(count, 1f);
		Arrays.sort(this.crossings, 0, count);
		float previous = 0f;
		for (int k = 0; k < count; k++) {
			final float t = this.crossings[k];
			if (t <= previous) {
				continue;
			}
			final float middle = (previous + t) / 2;
			if (!this.pathingGrid.isPathable(x0 + (dx * middle), y0 + (dy * middle), job.movementType, collisionSize)
					|| !this.pathingGrid.isPathable(x0 + (dx * t), y0 + (dy * t), job.movementType, collisionSize)) {
				return false;
			}
			previous = t;
		}
		return true;
	}

	private int addCellEdgeCrossings(int count, final float from, final float delta, final boolean xAxis) {
		if (delta == 0f) {
			return count;
		}
		final float to = from + delta;
		final int first = xAxis ? this.pathingGrid.getCornerX(Math.min(from, to))
				: this.pathingGrid.getCornerY(Math.min(from, to));
		final int last = xAxis ? this.pathingGrid.getCornerX(Math.max(from, to))
				: this.pathingGrid.getCornerY(Math.max(from, to));
		for (int corner = first; corner <= last; corner++) {
			final float edge = xAxis ? this.pathingGrid.getWorldXFromCorner(corner)
					: this.pathingGrid.getWorldYFromCorner(corner);
			final float t = (edge - from) / delta;
			if ((t > 0f) && (t < 1f)) {
				count = addCrossing(count, t);
			}
		}
		return count;
	}

	private int addCrossing(final int count, final float t) {
		if (count == this.crossings.length) {
			this.crossings = Arrays.copyOf(this.crossings, count * 2);
		}
		this.crossings[count] = t;
		return count + 1;
	}

	/**
	 * Measured: ordered onto ground it cannot stand on, or onto a point other units stand on, the original walks to the
	 * free point nearest the target (around a wall of units if that is where it is). Searching toward the blocked point
	 * itself explored nearly the whole map, or made walls of units cheap to cross so the unit stopped in front of them.
	 * A point counts as free of units when no standing unit's collision square overlaps the mover's; the stricter cell
	 * test the search and the walk make would put the point a cell or two farther out and the unit on a detour to it.
	 */
	private boolean moveGoalToNearest(final PathfindingJob job, final boolean freeOfUnits) {
		float bestX = 0;
		float bestY = 0;
		double bestDistance = Double.MAX_VALUE;
		double bestStartDistance = Double.MAX_VALUE;
		int foundRing = -1;
		final boolean toBuilding = (job.ignoreIntersectionsWithThisSecondUnit != null)
				&& job.ignoreIntersectionsWithThisSecondUnit.isBuilding();
		final float step = job.collisionSize > NEAREST_PATHABLE_FINE_STEP ? NEAREST_PATHABLE_STEP
				: NEAREST_PATHABLE_FINE_STEP;
		final int rings = (int) (NEAREST_PATHABLE_RADIUS / step);
		for (int ring = 1; ring <= rings; ring++) {
			// a point on the next rings can still be nearer than the corners of this one, its footprint (and so the
			// distance measured) up to half a cell off the point
			if ((foundRing != -1) && (ring > (foundRing + 2))) {
				break;
			}
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if ((Math.abs(i) != ring) && (Math.abs(j) != ring)) {
						continue;
					}
					final float x = job.goalX + (i * step);
					final float y = job.goalY + (j * step);
					// measured from the middle of the unit's footprint: by the point itself, a unit beside a building
					// would be a cell nearer on its south and west sides and always walk round to them
					final double distanceX = this.pathingGrid.getFootprintCenterX(x, job.collisionSize) - job.goalX;
					final double distanceY = this.pathingGrid.getFootprintCenterY(y, job.collisionSize) - job.goalY;
					// toward a building every spot of its first free ring is as near, a corner of it too: the one
					// nearest the start is chosen, so a unit coming diagonally stops at the corner
					final double distance = toBuilding ? Math.max(Math.abs(distanceX), Math.abs(distanceY))
							: StrictMath.hypot(distanceX, distanceY);
					if (distance > bestDistance) {
						continue;
					}
					final double startDistance = StrictMath.hypot(x - job.startX, y - job.startY);
					if ((distance == bestDistance) && (startDistance >= bestStartDistance)) {
						continue;
					}
					if (this.pathingGrid.contains(x, y)
							&& this.pathingGrid.isPathable(x, y, job.movementType, job.collisionSize)
							// toward a building the spot must be one the walk can stand on: units beside it take a cell
							// footprint, so a gap between two of them that only the collision squares allow is no spot
							&& (!freeOfUnits || !(toBuilding && usesFootprint(job.movementType)
									? !isPathableDynamically(x, y, job.ignoreIntersectionsWithThisUnit,
											job.ignoreIntersectionsWithThisSecondUnit, job.movementType)
									: this.worldCollision.intersectsStandingUnitOtherThan(
											this.tempRect.setCenter(x, y), job.ignoreIntersectionsWithThisUnit,
											job.ignoreIntersectionsWithThisSecondUnit, job.movementType,
											this.gameTurnTick)))) {
						bestDistance = distance;
						bestStartDistance = startDistance;
						bestX = x;
						bestY = y;
						if (foundRing == -1) {
							foundRing = ring;
						}
					}
				}
			}
		}
		if (foundRing == -1) {
			return false;
		}
		job.goalX = bestX;
		job.goalY = bestY;
		job.goalMoved = true;
		return true;
	}

	/**
	 * Measured: ordered onto a point a unit stands on, the original sends the mover to one of the four pathing cells
	 * straight north, west, east or south of the cell that unit stands in, as many whole cells out as the two collision
	 * sizes together need, the one the fewest cells across and up from where the mover starts (ties: the nearer one as it
	 * would be walked), and stops short of it as of any point it walks to.
	 */
	private boolean moveGoalBesideStandingUnit(final PathfindingJob job) {
		switch (job.movementType) {
		case FOOT:
		case HORSE:
		case HOVER:
		case AMPHIBIOUS:
			break;
		default:
			return false;
		}
		final CUnit[] blocker = new CUnit[1];
		final float goalX = job.goalX;
		final float goalY = job.goalY;
		this.worldCollision.enumUnitsInRect(this.tempRect.setCenter(goalX, goalY), (unit) -> {
			if (blocksBeside(unit, job, null) && !unit.isBuilding() && ((blocker[0] == null) || (StrictMath
					.hypot(unit.getX() - goalX, unit.getY() - goalY) < StrictMath.hypot(blocker[0].getX() - goalX,
							blocker[0].getY() - goalY)))) {
				blocker[0] = unit;
			}
			return false;
		});
		if (blocker[0] == null) {
			return false;
		}
		final float cellX = ((float) Math.floor(blocker[0].getX() / BESIDE_UNIT_CELL) * BESIDE_UNIT_CELL)
				+ (BESIDE_UNIT_CELL / 2);
		final float cellY = ((float) Math.floor(blocker[0].getY() / BESIDE_UNIT_CELL) * BESIDE_UNIT_CELL)
				+ (BESIDE_UNIT_CELL / 2);
		final float distance = BESIDE_UNIT_CELL
				* ((float) Math.floor((job.collisionSize + blocker[0].getUnitType().getCollisionSize())
						/ BESIDE_UNIT_CELL) + 1);
		float bestX = 0;
		float bestY = 0;
		final int startCellX = (int) Math.floor(job.startX / BESIDE_UNIT_CELL);
		final int startCellY = (int) Math.floor(job.startY / BESIDE_UNIT_CELL);
		final boolean moverAligned = isCellAligned(job.collisionSize);
		final boolean blockerAligned = isCellAligned(blocker[0].getUnitType().getCollisionSize());
		// Measured: a mover whose size is a whole number of cells goes north of a target that is not one, whatever its
		// start; between two such sizes, or when the sum reaches an odd number of cells, the side by the east / west
		// wins ties, but an odd number of cells out from a start south of the target further than eight cells away the
		// south side does; a whole-cell target seen by a smaller mover is never approached from north or east (a hero
		// target measured on move orders only).
		final boolean northFirst = moverAligned && !blockerAligned;
		final boolean oddRing = (((int) (distance / BESIDE_UNIT_CELL)) & 1) == 1;
		final boolean noNorthEast = blockerAligned && !moverAligned && !oddRing
				&& (!blocker[0].isHero() || ((job.queueItem != null) && job.queueItem.isMoveOrder()))
				&& ((job.ignoreIntersectionsWithThisUnit == null) || !job.ignoreIntersectionsWithThisUnit.isHero());
		final int startRing = Math.max(Math.abs(startCellX - (int) Math.floor(cellX / BESIDE_UNIT_CELL)),
				Math.abs(startCellY - (int) Math.floor(cellY / BESIDE_UNIT_CELL)));
		// up to nine cells out the round tie is ignored for a start south of the target; from twelve cells out the nearer
		// side by cell centres wins
		final boolean startSouth = (job.startY < cellY) && (startRing <= 10);
		final boolean southFirst = oddRing && (job.startY < cellY) && (startRing > 8);
		final boolean startEast = job.startX > cellX;
		final boolean ignoreRoundTie = oddRing || noNorthEast || startSouth;
		int bestPriority = Integer.MAX_VALUE;
		int bestStartCells = Integer.MAX_VALUE;
		double bestTieDistance = Double.MAX_VALUE;
		int bestRank = Integer.MAX_VALUE;
		for (int pass = 0; (pass < 2) && (bestStartCells == Integer.MAX_VALUE); pass++) {
			// north, west, east, south
			for (int side = 0; side < 4; side++) {
				if (noNorthEast && (pass == 0) && ((side == 0) || (side == 2))) {
					continue;
				}
				final float x = cellX + ((side == 1 ? -1 : side == 2 ? 1 : 0) * distance);
				final float y = cellY + ((side == 0 ? 1 : side == 3 ? -1 : 0) * distance);
				final int priority = (northFirst && (side == 0)) ? 0 : 1;
				final int startCells = Math.abs((int) Math.floor(x / BESIDE_UNIT_CELL) - startCellX)
						+ Math.abs((int) Math.floor(y / BESIDE_UNIT_CELL) - startCellY);
				if ((priority > bestPriority) || ((priority == bestPriority) && (startCells > bestStartCells))
						|| !this.pathingGrid.contains(x, y)
						|| !this.pathingGrid.isPathable(x, y, job.movementType, job.collisionSize)
						|| !isFreeBeside(x, y, job, blocker[0])) {
					continue;
				}
				// equal cell counts: the side nearer by whole-cell centres, a side behind blocked ground counts as farther
				double tieDistance = ignoreRoundTie ? 0
						: StrictMath.hypot(x - ((startCellX * BESIDE_UNIT_CELL) + (BESIDE_UNIT_CELL / 2)),
								y - ((startCellY * BESIDE_UNIT_CELL) + (BESIDE_UNIT_CELL / 2)));
				if (!straightLineWalkable(job, x, y)) {
					tieDistance += 1e6;
				}
				final int rank = southFirst ? (side == 3 ? 0 : 1 + side)
						: besideSideRank(side, oddRing, noNorthEast, startSouth, startEast);
				if ((priority < bestPriority) || (startCells < bestStartCells)
						|| (tieDistance < (bestTieDistance - 1e-3))
						|| ((tieDistance <= (bestTieDistance + 1e-3)) && (rank < bestRank))) {
					bestPriority = priority;
					bestStartCells = startCells;
					bestTieDistance = tieDistance;
					bestRank = rank;
					bestX = x;
					bestY = y;
				}
			}
		}
		if (bestStartCells == Integer.MAX_VALUE) {
			return false;
		}
		job.goalX = bestX;
		job.goalY = bestY;
		job.goalMoved = true;
		job.goalBesideUnit = true;
		if (job.ignoreIntersectionsWithThisSecondUnit == null) {
			// its square reaches over the side cells the original counts as free
			job.ignoreIntersectionsWithThisSecondUnit = blocker[0];
			job.besideBlocker = blocker[0];
		}
		return true;
	}

	private static boolean isCellAligned(final float collisionSize) {
		return (collisionSize > 0) && ((collisionSize % BESIDE_UNIT_CELL) == 0);
	}

	/** Order among sides tied on cells and distance: lower wins. Sides are north, west, east, south. */
	private static int besideSideRank(final int side, final boolean oddRing, final boolean noNorthEast,
			final boolean startSouth, final boolean startEast) {
		if (noNorthEast) {
			return startEast ? (side == 3 ? 0 : 1) : (side == 1 ? 0 : 1);
		}
		if (oddRing || startSouth) {
			return (side == 1) || (side == 2) ? side : 4 + side;
		}
		return side;
	}

	private boolean straightLineWalkable(final PathfindingJob job, final float x, final float y) {
		final float length = (float) StrictMath.hypot(x - job.startX, y - job.startY);
		final int steps = (int) Math.ceil(length / (BESIDE_UNIT_CELL / 2));
		for (int step = 1; step < steps; step++) {
			final float px = job.startX + (((x - job.startX) * step) / steps);
			final float py = job.startY + (((y - job.startY) * step) / steps);
			if (!this.pathingGrid.contains(px, py)
					|| !this.pathingGrid.isPathable(px, py, job.movementType, job.collisionSize)) {
				return false;
			}
		}
		return true;
	}

	private boolean isFreeBeside(final float x, final float y, final PathfindingJob job, final CUnit blocker) {
		final boolean[] taken = new boolean[1];
		this.worldCollision.enumUnitsInRect(this.tempRect.setCenter(x, y), (unit) -> {
			taken[0] = blocksBeside(unit, job, blocker);
			return taken[0];
		});
		return !taken[0];
	}

	private boolean blocksBeside(final CUnit unit, final PathfindingJob job, final CUnit blocker) {
		return (unit != job.ignoreIntersectionsWithThisUnit) && (unit != job.ignoreIntersectionsWithThisSecondUnit)
				&& (unit != blocker) && !unit.isNoUnitCollision() && !unit.isWalking(this.gameTurnTick)
				&& (unit.getMovementType() != MovementType.FLY)
				&& (unit.getMovementType() != MovementType.FOOT_NO_COLLISION);
	}

	public static final class PathfindingJob {
		private final CUnit ignoreIntersectionsWithThisUnit;
		private CUnit ignoreIntersectionsWithThisSecondUnit;
		private final float startX;
		private final float startY;
		private final CPathPoint goal;
		private final MovementType movementType;
		private final float collisionSize;
		private final boolean allowSmoothing;
		private final CBehaviorMove queueItem;
		private boolean jobStarted;
		private boolean humanOrder;
		private boolean preempted;
		float enclosedGoalRadius;
		private int queuedTick;
		private int turn;
		public float goalY;
		public float goalX;
		public float weightForHittingWalls;
		int iterationLimit;
		boolean limitExtended;
		boolean metScriptedTerrain;
		double straightCells;
		boolean shortSearch;
		float bestEndX;
		float bestEndY;
		boolean goalMoved;
		boolean goalBesideUnit;
		CUnit besideBlocker;
		boolean goalOutOfReach;
		boolean goalOnBlockedGround;
		boolean goalNodesBlocked;
		int bestCell;
		float bestDistance;
		double bestGLimit;
		boolean nearSideDecided;
		int nearSideCell;
		float nearSideDistance;
		boolean nearSideStays;
		SearchGrid searchGrid;
		GridMapping gridMapping;
		int startGridMinX;
		int startGridMinY;
		int startGridMaxX;
		int startGridMaxY;

		public PathfindingJob(final CUnit ignoreIntersectionsWithThisUnit,
				final CUnit ignoreIntersectionsWithThisSecondUnit, final float startX, final float startY,
				final CPathPoint goal, final PathingGrid.MovementType movementType, final float collisionSize,
				final boolean allowSmoothing, final CBehaviorMove queueItem) {
			this.ignoreIntersectionsWithThisUnit = ignoreIntersectionsWithThisUnit;
			this.ignoreIntersectionsWithThisSecondUnit = ignoreIntersectionsWithThisSecondUnit;
			this.startX = startX;
			this.startY = startY;
			this.goal = goal;
			this.movementType = movementType;
			this.collisionSize = collisionSize;
			this.allowSmoothing = allowSmoothing;
			this.queueItem = queueItem;
			this.jobStarted = false;
		}

		public CUnit getIgnoreIntersectionsWithThisUnit() {
			return this.ignoreIntersectionsWithThisUnit;
		}

		public float getStartX() {
			return this.startX;
		}

		public float getStartY() {
			return this.startY;
		}

		public int getTurn() {
			return this.turn;
		}

		public boolean isJobStarted() {
			return this.jobStarted;
		}
	}
}
