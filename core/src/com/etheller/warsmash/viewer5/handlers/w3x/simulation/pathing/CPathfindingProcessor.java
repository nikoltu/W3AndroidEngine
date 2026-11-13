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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWorldCollision;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;

public class CPathfindingProcessor {
	private final Rectangle tempRect = new Rectangle();
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
	private final Node[][] nodes;
	private final Node[][] cornerNodes;
	private final Node[] goalSet = new Node[9];
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
	private static final int MIN_SEARCH_ITERATIONS = 4096;
	/**
	 * Measured on 1.29: a unit of Neutral Passive that has had Wander (a Sheep, a Peasant given Wander, also once
	 * Wander is removed again) ordered behind a wood stops in the first corner it walks into on the way, where any
	 * other unit, a Player's Sheep too, walks the long way round. Its search looks only a few steps around where it
	 * stands and it walks to the point of that search nearest the target, sliding along what stands in its way.
	 */
	private static final int SHORT_SEARCH_ITERATIONS = 16;
	private static final float SLIDE_STEP = 4f;
	private static final int SLIDE_STEPS = 64;
	private static final int WITHDRAWN_TURN_UPDATES = 2;
	private static final int DETOUR_ITERATION_FACTOR = 16;
	private static final double DETOUR_MIN_DISTANCE = 1024;
	private int totalIterations = 0;
	private int totalJobLoops = 0;
	private final int pathingGridCellCount;

	public CPathfindingProcessor(final PathingGrid pathingGrid, final CWorldCollision worldCollision,
			final SharedGraph graph) {
		this.pathingGrid = pathingGrid;
		this.worldCollision = worldCollision;
		this.graph = graph;
		this.nodes = graph.nodes;
		this.cornerNodes = graph.cornerNodes;
		this.pathingGridCellCount = pathingGrid.getWidth() * pathingGrid.getHeight();
	}

	/**
	 * One node grid for all players' processors: a grid per player filled during long cross-map searches ran the
	 * device out of memory. A search may pause between ticks with its state in the nodes, so only one processor at a
	 * time holds a started search; the others wait, and the first one that waited goes next.
	 */
	public static final class SharedGraph {
		private final Node[][] nodes;
		private final Node[][] cornerNodes;
		private int lastJobId = 0;
		private CPathfindingProcessor owner;
		private CPathfindingProcessor reservedFor;
		private final Reachability[] reachability = new Reachability[REACHABILITY_CACHE];
		private int[] fillQueue;

		public SharedGraph(final PathingGrid pathingGrid) {
			this.nodes = new Node[pathingGrid.getHeight()][pathingGrid.getWidth()];
			this.cornerNodes = new Node[pathingGrid.getHeight() + 1][pathingGrid.getWidth() + 1];
		}

		/**
		 * Lets go of the nodes only searches before the last one touched. Such a node is reset on its next touch, so
		 * one made again in its place searches the same; kept, every part of the map ever searched stayed in memory
		 * and in each saved game, which grew with every visit of a zone map's hub.
		 */
		public void dropStaleNodes() {
			dropStaleNodes(this.nodes);
			dropStaleNodes(this.cornerNodes);
		}

		private void dropStaleNodes(final Node[][] graph) {
			for (final Node[] row : graph) {
				for (int x = 0; x < row.length; x++) {
					final Node node = row[x];
					if ((node != null) && (node.pathfindJobId != this.lastJobId)
							&& (node.pathableJobId != this.lastJobId)) {
						row[x] = null;
					}
				}
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
					entry.validate(pathingGrid);
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

	// A melee game asks for four (walking units of four collision sizes); with fewer, entries pushed each other out
	// and every search rebuilt one over the whole map.
	private static final int REACHABILITY_CACHE = 5;

	/**
	 * Which grid points of a search graph a unit can walk between, ignoring other units: the parts of the map apart
	 * from each other, found once and kept until buildings or destructables change the pathing. A search for a point in
	 * another part than the unit's walked every point the unit could reach before it gave up, and a few such searches
	 * at once held up the paths of all other units for many seconds.
	 */
	private static final class Reachability {
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

		private Reachability(final PathingGrid grid, final MovementType movementType, final float collisionSize,
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

		private void validate(final PathingGrid grid) {
			if (this.version != grid.getOverlayVersion()) {
				this.version = grid.getOverlayVersion();
				Arrays.fill(this.pathable, UNKNOWN);
				Arrays.fill(this.part, 0);
				this.parts = 0;
			}
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
		private int label(final int x, final int y) {
			if ((x < 0) || (y < 0) || (x >= this.width) || (y >= this.height)) {
				return 0;
			}
			return this.part[(y * this.width) + x];
		}

		/** The part of the map a grid point is in (by the steps the search takes), 0 for a point no unit fits on. */
		private int part(final SharedGraph graph, final PathingGrid grid, final int x, final int y) {
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
			while (head < tail) {
				final int index = queue[head++];
				final int cx = index % this.width;
				final int cy = index / this.width;
				for (final Direction direction : Direction.VALUES) {
					final int nx = cx + direction.xOffset;
					final int ny = cy + direction.yOffset;
					if (!open(grid, nx, ny)) {
						continue;
					}
					final int next = (ny * this.width) + nx;
					if ((this.part[next] != 0) || !open(grid, cx, ny) || !open(grid, nx, cy)) {
						continue;
					}
					this.part[next] = id;
					queue[tail++] = next;
				}
			}
			return id;
		}
	}

	private void finishJob() {
		this.moveQueue.poll();
		if (this.graph.owner == this) {
			this.graph.owner = null;
		}
	}

	/**
	 * Nodes are created on first use: a search touches a small part of the map, while a node for every cell
	 * of every processor took most of the simulation's memory.
	 */
	private Node node(final Node[][] searchGraph, final int y, final int x) {
		Node node = searchGraph[y][x];
		if (node == null) {
			node = new Node(searchGraph == this.cornerNodes
					? new CPathPoint(this.pathingGrid.getWorldXFromCorner(x), this.pathingGrid.getWorldYFromCorner(y))
					: new CPathPoint(this.pathingGrid.getWorldX(x), this.pathingGrid.getWorldY(y)));
			searchGraph[y][x] = node;
		}
		return node;
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
		final PathfindingJob job = new PathfindingJob(ignoreIntersectionsWithThisUnit,
				ignoreIntersectionsWithThisSecondUnit, startX, startY, goal, movementType, collisionSize, allowSmoothing,
				queueItem);
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
		return !this.worldCollision.intersectsStandingUnitOtherThan(tempRect.setCenter(x, y),
				ignoreIntersectionsWithThisUnit, ignoreIntersectionsWithThisSecondUnit, movementType, this.gameTurnTick);
	}

	public static boolean isCollisionSizeBetterSuitedForCorners(final float collisionSize) {
		return (((2 * (int) collisionSize) / 32) % 2) == 1;
	}

	/**
	 * Whether the job's unit fits at a grid point, the same test pathableBetween makes for each of its three points. A
	 * point is tested by up to eight of its neighbors, so the answer is kept for the rest of the job's work in this
	 * tick (units do not move within a tick, so it cannot change).
	 */
	private boolean nodePathable(final PathfindingJob job, final CSimulation simulation, final float x, final float y) {
		final Node node = node(job.searchGraph, job.gridMapping.getY(this.pathingGrid, y),
				job.gridMapping.getX(this.pathingGrid, x));
		final int tick = simulation.getGameTurnTick();
		if ((node.pathableJobId != this.pathfindJobId) || (node.pathableTick != tick)) {
			node.pathableJobId = this.pathfindJobId;
			node.pathableTick = tick;
			node.pathable = this.pathingGrid.isPathable(x, y, job.movementType, job.collisionSize)
					&& isPathableDynamically(x, y, job.ignoreIntersectionsWithThisUnit,
							job.ignoreIntersectionsWithThisSecondUnit, job.movementType);
		}
		return node.pathable;
	}

	private static void openAdd(final PathfindingJob job, final Node node) {
		node.open = true;
		job.openSet.add(node);
	}

	public double f(final Node n) {
		return n.g + h(n);
	}

	public double g(final Node n) {
		return n.g;
	}

	private boolean isGoal(final Node n) {
		for (int i = 0; i < this.goals; i++) {
			if (n == this.goalSet[i]) {
				return true;
			}
		}
		return false;
	}

	public float h(final Node n) {
		float bestDistance = 0;
		for (int i = 0; i < this.goals; i++) {
			final float possibleDistance = (float) n.point.distance(this.goalSet[i].point);
			if (possibleDistance > bestDistance) {
				bestDistance = possibleDistance; // always overestimate
			}
		}
		return bestDistance;
	}

	/**
	 * The open set of a search: the binary heap of java.util.PriorityQueue (same order of equal nodes, so the same
	 * paths), kept in the engine's own fields so that a save made while a unit's path is being searched can store it.
	 */
	static final class OpenSet {
		private Node[] queue = new Node[64];
		private int size;

		boolean isEmpty() {
			return this.size == 0;
		}

		void add(final Node node) {
			if (this.size >= this.queue.length) {
				this.queue = Arrays.copyOf(this.queue, this.queue.length * 2);
			}
			int k = this.size++;
			while (k > 0) {
				final int parent = (k - 1) >>> 1;
				final Node e = this.queue[parent];
				if (Double.compare(node.f, e.f) >= 0) {
					break;
				}
				this.queue[k] = e;
				k = parent;
			}
			this.queue[k] = node;
		}

		Node poll() {
			if (this.size == 0) {
				return null;
			}
			final Node result = this.queue[0];
			final int n = --this.size;
			final Node x = this.queue[n];
			this.queue[n] = null;
			if (n > 0) {
				int k = 0;
				final int half = n >>> 1;
				while (k < half) {
					int child = (k << 1) + 1;
					Node c = this.queue[child];
					final int right = child + 1;
					if ((right < n) && (Double.compare(c.f, this.queue[right].f) > 0)) {
						c = this.queue[child = right];
					}
					if (Double.compare(x.f, c.f) <= 0) {
						break;
					}
					this.queue[k] = c;
					k = child;
				}
				this.queue[k] = x;
			}
			return result;
		}
	}

	public static final class Node {
		public Direction cameFromDirection;
		private final CPathPoint point;
		private double f;
		private double g;
		private Node cameFrom;
		private int pathfindJobId;
		private boolean open;
		private int pathableJobId;
		private int pathableTick = -1;
		private boolean pathable;

		private Node(final CPathPoint point) {
			this.point = point;
		}

		private void touch(final int pathfindJobId) {
			if (pathfindJobId != this.pathfindJobId) {
				this.g = Float.POSITIVE_INFINITY;
				this.f = Float.POSITIVE_INFINITY;
				this.cameFrom = null;
				this.cameFromDirection = null;
				this.open = false;
				this.pathfindJobId = pathfindJobId;
			}
		}
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
		if ((graph.owner != null) && (graph.owner != this)) {
			if (!this.moveQueue.isEmpty() && (graph.reservedFor == null)) {
				graph.reservedFor = this;
			}
			return;
		}
		int workIterations = 0;
		JobsLoop: while (!this.moveQueue.isEmpty()) {
			this.totalJobLoops++;
			final PathfindingJob job = this.moveQueue.peek();
			this.tempRect.set(0, 0, job.collisionSize * 2, job.collisionSize * 2);
			if (!job.jobStarted) {
				if ((graph.reservedFor != null) && (graph.reservedFor != this)) {
					break JobsLoop;
				}
				graph.owner = this;
				graph.reservedFor = null;
				this.pathfindJobId = ++graph.lastJobId;
				this.totalIterations = 0;
				this.totalJobLoops = 0;
				job.jobStarted = true;
				PerfStats.pathJob();
				workIterations += 5; // setup of job predicted cost
				job.goalX = job.goal.x;
				job.goalY = job.goal.y;
				job.goalMoved = false;
				job.goalBesideUnit = false;
				job.goalOutOfReach = false;
				job.weightForHittingWalls = 1E9f;
				job.bestNode = null;
				job.bestDistance = Float.MAX_VALUE;
				job.bestGLimit = Double.MAX_VALUE;
				job.limitExtended = false;
				job.shortSearch = searchesShort(simulation, job.ignoreIntersectionsWithThisUnit);
				if (!job.shortSearch) {
					if (!this.pathingGrid.isPathable(job.goalX, job.goalY, job.movementType, job.collisionSize)) {
						if (!moveGoalToNearest(job, true)) {
							moveGoalToNearest(job, false);
						}
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
				if (isCollisionSizeBetterSuitedForCorners(job.collisionSize)) {
					job.searchGraph = this.cornerNodes;
					job.gridMapping = GridMapping.CORNERS;
				}
				else {
					job.searchGraph = this.nodes;
					job.gridMapping = GridMapping.CELLS;
				}
				if ((job.weightForHittingWalls >= 1E9f) && !job.shortSearch && !aimAtReachablePart(job)) {
					// Measured: the original walks to the reachable point nearest the target and stops there; with no
					// such point nearer than where it stands, it stays.
					final LinkedList<CPathPoint> stay = new LinkedList<>();
					stay.add(new CPathPoint(job.startX, job.startY));
					finishJob();
					job.queueItem.pathFound(job.goal, stay, false, simulation);
					continue JobsLoop;
				}
				final double straightCells = StrictMath.hypot(job.goalX - job.startX, job.goalY - job.startY) / 32;
				job.iterationLimit = job.shortSearch ? SHORT_SEARCH_ITERATIONS
						: (int) Math.min(this.pathingGridCellCount,
								Math.max(MIN_SEARCH_ITERATIONS, 4 * straightCells * straightCells));
				final int goalCellY = Math.min(Math.max(0, job.gridMapping.getY(this.pathingGrid, job.goalY)),
						job.searchGraph.length - 1);
				final int goalCellX = Math.min(Math.max(0, job.gridMapping.getX(this.pathingGrid, job.goalX)),
						job.searchGraph[goalCellY].length - 1);
				final Node mostLikelyGoal = node(job.searchGraph, goalCellY, goalCellX);
				mostLikelyGoal.touch(this.pathfindJobId);
				final double bestGoalDistance = mostLikelyGoal.point.distance(job.goalX, job.goalY);
				Arrays.fill(this.goalSet, null);
				this.goals = 0;
				for (int i = goalCellX - 1; i <= (goalCellX + 1); i++) {
					for (int j = goalCellY - 1; j <= (goalCellY + 1); j++) {
						if ((j >= 0) && (j < job.searchGraph.length)) {
							if ((i >= 0) && (i < job.searchGraph[j].length)) {
								final Node possibleGoal = node(job.searchGraph, j, i);
								possibleGoal.touch(this.pathfindJobId);
								if (possibleGoal.point.distance(job.goalX, job.goalY) <= bestGoalDistance) {
									this.goalSet[this.goals++] = possibleGoal;
								}
							}
						}
					}
				}
				final int startGridY = job.gridMapping.getY(this.pathingGrid, job.startY);
				final int startGridX = job.gridMapping.getX(this.pathingGrid, job.startX);
				job.openSet = new OpenSet();

				job.start = node(job.searchGraph, startGridY, startGridX);
				job.start.touch(this.pathfindJobId);
				if (job.startX > job.start.point.x) {
					job.startGridMinX = startGridX;
					job.startGridMaxX = startGridX + 1;
				}
				else if (job.startX < job.start.point.x) {
					job.startGridMinX = startGridX - 1;
					job.startGridMaxX = startGridX;
				}
				else {
					job.startGridMinX = startGridX;
					job.startGridMaxX = startGridX;
				}
				if (job.startY > job.start.point.y) {
					job.startGridMinY = startGridY;
					job.startGridMaxY = startGridY + 1;
				}
				else if (job.startY < job.start.point.y) {
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
							final Node possibleNode = node(job.searchGraph, cellY, cellX);
							possibleNode.touch(this.pathfindJobId);
							final float x = possibleNode.point.x;
							final float y = possibleNode.point.y;
							if (pathableBetween(job.ignoreIntersectionsWithThisUnit,
									job.ignoreIntersectionsWithThisSecondUnit, job.startX, job.startY, job.movementType,
									job.collisionSize, x, y)) {

								final double tentativeScore = possibleNode.point.distance(job.startX, job.startY);
								job.bestGLimit = Math.min(job.bestGLimit, tentativeScore + job.weightForHittingWalls);
								possibleNode.g = tentativeScore;
								possibleNode.f = tentativeScore + h(possibleNode);
								openAdd(job, possibleNode);

							}
							else {
								final double tentativeScore = job.weightForHittingWalls;
								// A unit squeezed among others at its start still looks for the nearest point it
								// can walk to.
								job.bestGLimit = Math.min(job.bestGLimit, tentativeScore + job.weightForHittingWalls);
								possibleNode.g = tentativeScore;
								possibleNode.f = tentativeScore + h(possibleNode);
								openAdd(job, possibleNode);

							}
						}
					}
				}
			}

			while (!job.openSet.isEmpty()) {
				Node current = job.openSet.poll();
				current.touch(this.pathfindJobId);
				current.open = false;
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
				if (current.g < job.bestGLimit) {
					if (job.shortSearch) {
						slideTowardGoal(job, current.point.x, current.point.y, null);
						final float distanceToGoal = (float) StrictMath.hypot(job.goalX - this.slideX,
								job.goalY - this.slideY);
						if (distanceToGoal < (job.bestDistance - 1)) {
							job.bestDistance = distanceToGoal;
							job.bestNode = current;
							job.bestEndX = this.slideX;
							job.bestEndY = this.slideY;
						}
					}
					else {
						final float distanceToGoal = (float) current.point.distance(job.goalX, job.goalY);
						if (distanceToGoal < job.bestDistance) {
							job.bestDistance = distanceToGoal;
							job.bestNode = current;
						}
					}
				}

				for (final Direction direction : Direction.VALUES) {
					final float x = current.point.x + (direction.xOffset * 32);
					final float y = current.point.y + (direction.yOffset * 32);
					if (this.pathingGrid.contains(x, y)) {
						double turnCost;
						if ((current.cameFromDirection != null) && (direction != current.cameFromDirection)) {
							turnCost = 0.25;
						}
						else {
							turnCost = 0;
						}
						double tentativeScore = current.g + ((direction.length + turnCost) * 32);
						if (!nodePathable(job, simulation, x, y) || !nodePathable(job, simulation, current.point.x, y)
								|| !nodePathable(job, simulation, x, current.point.y)) {
							tentativeScore += (direction.length) * job.weightForHittingWalls;
						}
						final Node neighbor = node(job.searchGraph, job.gridMapping.getY(this.pathingGrid, y),
								job.gridMapping.getX(this.pathingGrid, x));
						neighbor.touch(this.pathfindJobId);
						if (tentativeScore < neighbor.g) {
							neighbor.cameFrom = current;
							neighbor.cameFromDirection = direction;
							neighbor.g = tentativeScore;
							neighbor.f = tentativeScore + h(neighbor);
							if (!neighbor.open) {
								openAdd(job, neighbor);
							}
						}
					}
				}
				workIterations++;
				this.totalIterations++;
				PerfStats.pathIteration();
				// Measured: the original walks across a whole melee map; a fixed cap stopped such paths after a few
				// steps. A short chase into a crowd of units has no way through and searched the whole map each time
				// a unit in a fight moved, so the limit grows with the distance instead.
				if (this.totalIterations > job.iterationLimit) {
					// Measured on 1.29: a unit ordered to a point behind a long wall walks out and round it, even when
					// that way is many times the straight distance. Such a search (a far goal, or one that has not got
					// any nearer to the goal) goes on once, with a much larger limit; short chases keep the cheap one.
					if (!job.limitExtended && !job.shortSearch && ((StrictMath.hypot(job.goalX - job.startX,
							job.goalY - job.startY) >= DETOUR_MIN_DISTANCE) || !nearerThanStart(job))) {
						job.limitExtended = true;
						job.iterationLimit = (int) Math.min(this.pathingGridCellCount,
								(long) job.iterationLimit * DETOUR_ITERATION_FACTOR);
					}
					else {
						break;
					}
				}
				if (workIterations >= 1500) {
					// breaking jobs loop will implicitly exit without calling pathFound() below
					break JobsLoop;
				}
			}
			// Measured: the original walks to the reachable point nearest the target and stops there.
			final LinkedList<CPathPoint> partialPath;
			if (nearerThanStart(job)) {
				partialPath = buildPath(job, job.bestNode, false);
				if (job.shortSearch && ((job.bestEndX != job.bestNode.point.x)
						|| (job.bestEndY != job.bestNode.point.y))) {
					final List<CPathPoint> slide = new ArrayList<>();
					slideTowardGoal(job, job.bestNode.point.x, job.bestNode.point.y, slide);
					slide.add(new CPathPoint(job.bestEndX, job.bestEndY));
					// The slide is tested in steps; a leg of it that clips a cell corner between them ends it there.
					float legX = job.bestNode.point.x;
					float legY = job.bestNode.point.y;
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
	 * Whether the search reached its (pathable) goal only by a step through terrain or destructables the unit cannot
	 * cross. Steps through standing units do not count: those move away.
	 */
	private boolean crossesBlockedGround(final PathfindingJob job, final Node end) {
		if ((job.weightForHittingWalls < 1E9f) || (end.g < job.weightForHittingWalls)) {
			return false;
		}
		int steps = 0;
		for (Node node = end; (node.cameFrom != null) && (steps <= this.pathingGridCellCount); node = node.cameFrom) {
			steps++;
			final Node from = node.cameFrom;
			if (((node.g - from.g) >= job.weightForHittingWalls) && (!this.pathingGrid.isPathable(node.point.x,
					node.point.y, job.movementType, job.collisionSize)
					|| !this.pathingGrid.isPathable(from.point.x, node.point.y, job.movementType, job.collisionSize)
					|| !this.pathingGrid.isPathable(node.point.x, from.point.y, job.movementType,
							job.collisionSize))) {
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
				job.searchGraph == this.cornerNodes);
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
				for (int j = -ring; j <= ring; j++) {
					if ((Math.abs(i) != ring) && (Math.abs(j) != ring)) {
						continue;
					}
					final int x = goalX + i;
					final int y = goalY + j;
					if ((x < 0) || (y < 0) || (x >= reach.width) || (y >= reach.height)) {
						continue;
					}
					final double distance = StrictMath.hypot(reach.worldX(this.pathingGrid, x) - job.goalX,
							reach.worldY(this.pathingGrid, y) - job.goalY);
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
		return (job.bestNode != null)
				&& (job.bestDistance < (StrictMath.hypot(job.goalX - job.startX, job.goalY - job.startY) - 16));
	}

	private static final float MAX_SMOOTHED_SEGMENT = 1024f;

	/**
	 * The path from the start through the searched nodes to endNode (and on to the goal when it was reached). With
	 * smoothing, a node is skipped whenever the unit can walk straight past it, so units walk any-angle lines instead
	 * of the grid's zigzag.
	 */
	private LinkedList<CPathPoint> buildPath(final PathfindingJob job, final Node endNode, final boolean reachedGoal) {
		final ArrayList<CPathPoint> chain = new ArrayList<>();
		Node node = endNode;
		while (node != null) {
			chain.add(node.point);
			node = node.cameFrom;
			if (chain.size() > this.pathingGridCellCount) {
				Log.error(new IllegalStateException("PATHING SYSTEM ERROR: cycle in the path near " + endNode.point
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
		final int steps = Math.max(1, (int) Math.ceil(StrictMath.hypot(dx, dy) / 8));
		for (int step = 1; step <= steps; step++) {
			final float x = x0 + ((dx * step) / steps);
			final float y = y0 + ((dy * step) / steps);
			if (!isPathableDynamically(x, y, job.ignoreIntersectionsWithThisUnit,
					job.ignoreIntersectionsWithThisSecondUnit, job.movementType)) {
				return false;
			}
		}
		return segmentClearOfTerrain(job, x0, y0, dx, dy);
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
							&& (!freeOfUnits || isPathableDynamically(x, y, job.ignoreIntersectionsWithThisUnit,
									job.ignoreIntersectionsWithThisSecondUnit, job.movementType))) {
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
	 * sizes together need, the one the fewest cells across and up from where the mover starts (ties in that order), and
	 * stops short of it as of any point it walks to.
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
		int bestStartCells = Integer.MAX_VALUE;
		// north, west, east, south
		for (int side = 0; side < 4; side++) {
			final float x = cellX + ((side == 1 ? -1 : side == 2 ? 1 : 0) * distance);
			final float y = cellY + ((side == 0 ? 1 : side == 3 ? -1 : 0) * distance);
			final int startCells = Math.abs((int) Math.floor(x / BESIDE_UNIT_CELL) - startCellX)
					+ Math.abs((int) Math.floor(y / BESIDE_UNIT_CELL) - startCellY);
			if ((startCells < bestStartCells) && this.pathingGrid.contains(x, y)
					&& this.pathingGrid.isPathable(x, y, job.movementType, job.collisionSize)
					&& isFreeBeside(x, y, job, blocker[0])) {
				bestStartCells = startCells;
				bestX = x;
				bestY = y;
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
		private int turn;
		public float goalY;
		public float goalX;
		public float weightForHittingWalls;
		int iterationLimit;
		boolean limitExtended;
		boolean shortSearch;
		float bestEndX;
		float bestEndY;
		boolean goalMoved;
		boolean goalBesideUnit;
		boolean goalOutOfReach;
		Node bestNode;
		float bestDistance;
		double bestGLimit;
		Node[][] searchGraph;
		GridMapping gridMapping;
		OpenSet openSet;
		Node start;
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
