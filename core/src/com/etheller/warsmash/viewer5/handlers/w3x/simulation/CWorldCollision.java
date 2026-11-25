package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.util.Quadtree;
import com.etheller.warsmash.util.QuadtreeIntersector;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedTransient;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;

public class CWorldCollision {
	private static final float MINIMUM_COLLISION_SIZE = 0.001f /* THIS IS TO STOP QUADTREE FROM BUSTING */;
	private static final Rectangle tempFootprintArea = new Rectangle();
	private static final int ANY_TREE = 1;
	private static final int DEAD_TREE = 2;
	private static final int GROUND_TREE = 4;
	private static final int AIR_TREE = 8;
	private static final int SEA_TREE = 16;
	private static final int BUILDING_TREE = 32;
	private static final boolean REACH_CHECK = Boolean.getBoolean("warsmash.collisionReachCheck");
	private final Quadtree<CUnit> deadUnitCollision;
	private final Quadtree<CUnit> groundUnitCollision;
	private final Quadtree<CUnit> airUnitCollision;
	private final Quadtree<CUnit> seaUnitCollision;
	private final Quadtree<CUnit> buildingUnitCollision;
	private final Quadtree<CUnit> anyUnitEnumerableCollision;
	private final Quadtree<CDestructable> destructablesForEnum;
	private final Quadtree<CItem> itemsForEnum;
	private final float maxCollisionRadius;
	// The largest footprint (in cells) of any unit that was put in: units above MaxCollisionRadius have a square
	// clamped to it, but their footprint is not.
	private int maxRegisteredCells = 1;
	/** Counts the units put in, taken out and moved, so that what was gathered from the trees can tell it is stale. */
	@SavedTransient
	private int unitChanges;
	private final List<List<CUnit>> foundLists = new ArrayList<>();
	private int foundListDepth;
	private final EnumCollector enumCollector = new EnumCollector();
	private final RangeMatcher rangeMatcher = new RangeMatcher();
	private long enumStampCounter;
	private final List<Set<Object>> otherEnumSets = new ArrayList<>();
	private int otherEnumSetDepth;
	private final AnyUnitExceptTwoIntersector anyUnitExceptTwoIntersector;
	private final EachUnitOnlyOnceIntersector eachUnitOnlyOnceIntersector;
	private final ItemEnumIntersectorBoolean itemEnumIntersectorBoolean;

	public CWorldCollision(final Rectangle entireMapBounds, final float maxCollisionRadius) {
		this.deadUnitCollision = new Quadtree<>(entireMapBounds);
		this.groundUnitCollision = new Quadtree<>(entireMapBounds);
		this.airUnitCollision = new Quadtree<>(entireMapBounds);
		this.seaUnitCollision = new Quadtree<>(entireMapBounds);
		this.buildingUnitCollision = new Quadtree<>(entireMapBounds);
		this.anyUnitEnumerableCollision = new Quadtree<>(entireMapBounds);
		this.destructablesForEnum = new Quadtree<>(entireMapBounds);
		this.itemsForEnum = new Quadtree<>(entireMapBounds);
		this.maxCollisionRadius = maxCollisionRadius;
		this.anyUnitExceptTwoIntersector = new AnyUnitExceptTwoIntersector();
		this.eachUnitOnlyOnceIntersector = new EachUnitOnlyOnceIntersector();
		this.itemEnumIntersectorBoolean = new ItemEnumIntersectorBoolean();
	}

	public void addUnit(final CUnit unit) {
		this.unitChanges++;
		Rectangle bounds = unit.getCollisionRectangle();
		if ((bounds != null) && (unit.placement.collisionTrees != 0)) {
			// Still registered (a hero revived from its own death trigger or from its bones): taken out first, so
			// that it is not left where it was nor kept twice.
			removeFromTrees(unit, bounds, unit.placement.collisionTrees);
			bounds = null;
		}
		maxRegisteredCells = Math.max(this.maxRegisteredCells,
				PathingGrid.getFootprintCells(unit.getUnitType().getCollisionSize()));
		if (bounds == null) {
			final float collisionSize = Math.max(MINIMUM_COLLISION_SIZE,
					Math.min(this.maxCollisionRadius, unit.getUnitType().getCollisionSize()));
			bounds = new Rectangle(unit.getX() - collisionSize, unit.getY() - collisionSize, collisionSize * 2,
					collisionSize * 2);
			unit.setCollisionRectangle(bounds);
		}
		final int trees = treesFor(unit);
		unit.placement.collisionTrees = trees;
		if ((trees & DEAD_TREE) != 0) {
			this.deadUnitCollision.add(unit, bounds);
		}
		if ((trees & ANY_TREE) != 0) {
			this.anyUnitEnumerableCollision.add(unit, bounds);
		}
		if ((trees & BUILDING_TREE) != 0) {
			this.buildingUnitCollision.add(unit, bounds);
		}
		if ((trees & SEA_TREE) != 0) {
			this.seaUnitCollision.add(unit, bounds);
		}
		if ((trees & GROUND_TREE) != 0) {
			this.groundUnitCollision.add(unit, bounds);
		}
		if ((trees & AIR_TREE) != 0) {
			this.airUnitCollision.add(unit, bounds);
		}
	}

	// The trees a unit goes into for what it is now. Buildings are in the building tree so that we can include them
	// when enumerating all units in a rect, but they don't really move dynamically.
	private static int treesFor(final CUnit unit) {
		if (unit.isUsableCorpse()) {
			return DEAD_TREE;
		}
		if (unit.isBuilding()) {
			return ANY_TREE | BUILDING_TREE;
		}
		final MovementType movementType = unit.getMovementType();
		if (movementType == null) {
			return ANY_TREE;
		}
		switch (movementType) {
		case AMPHIBIOUS:
			return ANY_TREE | SEA_TREE | GROUND_TREE;
		case FLOAT:
			return ANY_TREE | SEA_TREE;
		case FLY:
			return ANY_TREE | AIR_TREE;
		case DISABLED:
			return ANY_TREE;
		default:
		case FOOT:
		case FOOT_NO_COLLISION:
		case HORSE:
		case HOVER:
			return ANY_TREE | GROUND_TREE;
		}
	}

	// The trees the unit was put in. A snare or a removed move ability makes its movement type DISABLED, and a
	// death makes it a corpse, without it being put in again, so what it is now does not tell. A save from before
	// this was kept has none: then what it is now, as before.
	private static int registeredTrees(final CUnit unit) {
		final int trees = unit.placement.collisionTrees;
		return trees != 0 ? trees : (treesFor(unit) | ANY_TREE);
	}

	private void removeFromTrees(final CUnit unit, final Rectangle bounds, final int trees) {
		this.unitChanges++;
		if ((trees & ANY_TREE) != 0) {
			this.anyUnitEnumerableCollision.remove(unit, bounds);
		}
		if ((trees & DEAD_TREE) != 0) {
			this.deadUnitCollision.remove(unit, bounds);
		}
		if ((trees & BUILDING_TREE) != 0) {
			this.buildingUnitCollision.remove(unit, bounds);
		}
		if ((trees & SEA_TREE) != 0) {
			this.seaUnitCollision.remove(unit, bounds);
		}
		if ((trees & GROUND_TREE) != 0) {
			this.groundUnitCollision.remove(unit, bounds);
		}
		if ((trees & AIR_TREE) != 0) {
			this.airUnitCollision.remove(unit, bounds);
		}
	}

	public void addDestructable(final CDestructable dest) {
		final Rectangle bounds = dest.getOrCreateRegisteredEnumRectangle();
		this.destructablesForEnum.add(dest, bounds);
	}

	public void removeDestructable(final CDestructable dest) {
		final Rectangle bounds = dest.getOrCreateRegisteredEnumRectangle();
		this.destructablesForEnum.remove(dest, bounds);
	}

	public void addItem(final CItem item) {
		final Rectangle bounds = item.getOrCreateRegisteredEnumRectangle();
		this.itemsForEnum.add(item, bounds);
	}

	public void removeItem(final CItem item) {
		final Rectangle bounds = item.getOrCreateRegisteredEnumRectangle();
		this.itemsForEnum.remove(item, bounds);
	}

	public void removeUnit(final CUnit unit) {
		final Rectangle bounds = unit.getCollisionRectangle();
		if (bounds != null) {
			removeFromTrees(unit, bounds, registeredTrees(unit));
		}
		unit.placement.collisionTrees = 0;
		unit.setCollisionRectangle(null);
	}

	public void enumUnitsInRect(final Rectangle rect, final CUnitEnumFunction callback) {
		enumSnapshot(rect, callback, true, false);
	}

	public void enumCorpsesInRect(final Rectangle rect, final CUnitEnumFunction callback) {
		enumSnapshot(rect, callback, false, true);
	}

	public void enumUnitsOrCorpsesInRect(final Rectangle rect, final CUnitEnumFunction callback) {
		enumSnapshot(rect, callback, true, true);
	}

	/**
	 * Found first, called after: a callback that removes a unit (RemoveUnit in a filter) would otherwise shift the
	 * leaf under the walk and skip the next one. As in 1.29 a unit removed meanwhile is still visited; one that is
	 * hidden meanwhile is not. A callback can start another enumeration, so each nesting depth gets its own list.
	 * The walk itself calls no script or callback, so one collector with a fresh stamp serves every enumeration: a
	 * unit carries the stamp of the last walk that met it, which replaces a set that had to be emptied each time.
	 */
	private void enumSnapshot(final Rectangle rect, final CUnitEnumFunction callback, final boolean units,
			final boolean corpses) {
		if (this.foundListDepth == this.foundLists.size()) {
			this.foundLists.add(new ArrayList<>());
		}
		final List<CUnit> found = this.foundLists.get(this.foundListDepth++);
		try {
			found.clear();
			final EnumCollector collector = this.enumCollector;
			collector.stamp = ++this.enumStampCounter;
			collector.found = found;
			if (units) {
				this.anyUnitEnumerableCollision.intersect(rect, collector);
			}
			if (corpses) {
				this.deadUnitCollision.intersect(rect, collector);
			}
			collector.found = null;
			for (int i = 0, size = found.size(); i < size; i++) {
				final CUnit unit = found.get(i);
				// RemoveUnit hides the unit and takes it out of the collision at once.
				if ((unit.getCollisionRectangle() != null) && (unit.isHidden() || unit.isUnenumerable())) {
					continue;
				}
				if (callback.call(unit)) {
					return;
				}
			}
		}
		finally {
			found.clear();
			this.foundListDepth--;
		}
	}

	private static final class EnumCollector implements QuadtreeIntersector<CUnit> {
		private long stamp;
		private List<CUnit> found;

		@Override
		public boolean onIntersect(final CUnit unit) {
			if (unit.isHidden() || unit.isUnenumerable() || (unit.enumStamp == this.stamp)) {
				return false;
			}
			unit.enumStamp = this.stamp;
			this.found.add(unit);
			return false;
		}
	}

	public void enumCorpsesInRange(final float x, final float y, final float radius, final CUnitEnumFunction callback) {
		enumCorpsesInRect(new Rectangle(x - radius, y - radius, radius * 2, radius * 2), (enumUnit) -> {
			if (enumUnit.canReach(x, y, radius)) {
				return callback.call(enumUnit);
			}
			return false;
		});
	}

	public void enumUnitsInRange(final float x, final float y, final float radius, final CUnitEnumFunction callback) {
		enumUnitsInRect(new Rectangle(x - radius, y - radius, radius * 2, radius * 2), (enumUnit) -> {
			if (enumUnit.canReach(x, y, radius)) {
				return callback.call(enumUnit);
			}
			return false;
		});
	}

	/** A test for {@link CWorldCollision#anyUnitInRange}: cheap and exact parts, both without side effects. */
	public interface UnitMatcher {
		/** May reject a unit before its position is looked at; never rejects one that {@link #exact} would accept. */
		boolean quick(CUnit unit);

		boolean exact(CUnit unit);
	}

	/**
	 * Whether {@code enumUnitsInRange} would meet a unit the matcher accepts, without building the list of units. The
	 * matcher must change nothing, as then the order of the walk does not matter either.
	 */
	public boolean anyUnitInRange(final float x, final float y, final float radius, final UnitMatcher matcher) {
		final RangeMatcher range = this.rangeMatcher;
		range.rect.set(x - radius, y - radius, radius * 2, radius * 2);
		range.x = x;
		range.y = y;
		range.radius = radius;
		range.matcher = matcher;
		try {
			return this.anyUnitEnumerableCollision.intersectNodes(range.rect, range);
		}
		finally {
			range.matcher = null;
		}
	}

	private static final class RangeMatcher implements Quadtree.NodeTester<CUnit> {
		private final Rectangle rect = new Rectangle();
		private float x;
		private float y;
		private float radius;
		private UnitMatcher matcher;

		@Override
		public boolean test(final CUnit unit, final Rectangle bounds) {
			return this.matcher.quick(unit) && bounds.overlaps(this.rect) && !unit.isHidden()
					&& !unit.isUnenumerable() && unit.canReach(this.x, this.y, this.radius)
					&& this.matcher.exact(unit);
		}
	}

	public void enumUnitsOrCorpsesInRange(final float x, final float y, final float radius, final CUnitEnumFunction callback) {
		enumUnitsOrCorpsesInRect(new Rectangle(x - radius, y - radius, radius * 2, radius * 2), (enumUnit) -> {
			if (enumUnit.canReach(x, y, radius)) {
				return callback.call(enumUnit);
			}
			return false;
		});
	}

	public void enumBuildingsAtPoint(final float x, final float y, final QuadtreeIntersector<CUnit> callback) {
		this.buildingUnitCollision.intersect(x, y, callback);
	}

	// The quadtree keeps an object in every leaf its bounds touch, so a rect across leaf edges meets it more than
	// once; the set lets each callback see it once. Lambdas, not the shared intersectors, so that a callback can
	// start another enumeration.
	public void enumItemsInRect(final Rectangle rect, final CItemEnumFunction callback) {
		// Found first, called after: a callback that removes its item (RemoveItem on each picked item) would
		// otherwise shift the leaf under the walk and skip the next item.
		final List<CItem> found = new ArrayList<>();
		final Set<Object> seen = acquireOtherEnumSet();
		try {
			this.itemsForEnum.intersect(rect, (item) -> {
				if (!item.isHidden() && seen.add(item)) {
					found.add(item);
				}
				return false;
			});
		}
		finally {
			this.otherEnumSetDepth--;
		}
		for (final CItem item : found) {
			// One that an earlier callback removed or gave to a unit is no longer on the ground.
			if (!item.isHidden() && callback.call(item)) {
				return;
			}
		}
	}

	public void enumDestructablesInRect(final Rectangle rect, final CDestructableEnumFunction callback) {
		// The ones in the rect are taken first: a callback that makes new destructables there (a script that
		// swaps every tree for another) must not meet them in the same enumeration. One a callback removed
		// before its turn is not visited (measured).
		final List<CDestructable> found = new ArrayList<>();
		final Set<Object> seen = acquireOtherEnumSet();
		try {
			this.destructablesForEnum.intersect(rect, (dest) -> {
				if (seen.add(dest)) {
					found.add(dest);
				}
				return false;
			});
		}
		finally {
			this.otherEnumSetDepth--;
		}
		for (final CDestructable dest : found) {
			if (dest.isRemovedFromGame() || dest.isRemovalQueued()) {
				continue;
			}
			if (callback.call(dest)) {
				return;
			}
		}
	}

	private Set<Object> acquireOtherEnumSet() {
		if (this.otherEnumSetDepth == this.otherEnumSets.size()) {
			this.otherEnumSets.add(Collections.newSetFromMap(new IdentityHashMap<>()));
		}
		final Set<Object> set = this.otherEnumSets.get(this.otherEnumSetDepth++);
		set.clear();
		return set;
	}

	public void enumDestructablesInRange(final float x, final float y, final float radius,
			final CDestructableEnumFunction callback) {
		enumDestructablesInRect(new Rectangle(x - radius, y - radius, radius * 2, radius * 2), (enumUnit) -> {
			if (enumUnit.distance(x, y) <= radius) {
				return callback.call(enumUnit);
			}
			return false;
		});
	}

	public boolean intersectsAnythingOtherThan(final Rectangle newPossibleRectangle, final CUnit sourceUnitToIgnore,
			final MovementType movementType, final boolean forConstruction) {
		return this.intersectsAnythingOtherThan(newPossibleRectangle, sourceUnitToIgnore, null, movementType,
				forConstruction);
	}

	public boolean intersectsAnythingOtherThan(final Rectangle newPossibleRectangle, final CUnit sourceUnitToIgnore,
			final CUnit sourceSecondUnitToIgnore, final MovementType movementType, final boolean forConstruction) {
		return intersectsAnythingOtherThan(newPossibleRectangle, sourceUnitToIgnore, sourceSecondUnitToIgnore,
				movementType, forConstruction, null, -1);
	}

	/** As intersectsAnythingOtherThan, but units walking at gameTurnTick do not count. */
	public boolean intersectsStandingUnitOtherThan(final Rectangle newPossibleRectangle, final CUnit sourceUnitToIgnore,
			final CUnit sourceSecondUnitToIgnore, final MovementType movementType, final int gameTurnTick) {
		this.anyUnitExceptTwoIntersector.walkingTick = gameTurnTick;
		try {
			return intersectsAnythingOtherThan(newPossibleRectangle, sourceUnitToIgnore, sourceSecondUnitToIgnore,
					movementType, false);
		}
		finally {
			this.anyUnitExceptTwoIntersector.walkingTick = NOT_A_TICK;
		}
	}

	// Units the player ignoredControllerIndex controls (its own and those of players sharing control with it) are
	// skipped: they step aside for a building that player places.
	public boolean intersectsAnythingOtherThan(final Rectangle newPossibleRectangle, final CUnit sourceUnitToIgnore,
			final CUnit sourceSecondUnitToIgnore, final MovementType movementType, final boolean forConstruction,
			final CSimulation simulation, final int ignoredControllerIndex) {
		this.anyUnitExceptTwoIntersector.ignoreControlledBy(simulation, ignoredControllerIndex);
		if (movementType != null) {
			switch (movementType) {
			case AMPHIBIOUS:
				if (this.seaUnitCollision.intersect(newPossibleRectangle, this.anyUnitExceptTwoIntersector
						.reset(sourceUnitToIgnore, sourceSecondUnitToIgnore, forConstruction))) {
					return true;
				}
				if (this.groundUnitCollision.intersect(newPossibleRectangle, this.anyUnitExceptTwoIntersector
						.reset(sourceUnitToIgnore, sourceSecondUnitToIgnore, forConstruction))) {
					return true;
				}
				return false;
			case FLOAT:
				return this.seaUnitCollision.intersect(newPossibleRectangle, this.anyUnitExceptTwoIntersector
						.reset(sourceUnitToIgnore, sourceSecondUnitToIgnore, forConstruction));
			case FLY:
				return this.airUnitCollision.intersect(newPossibleRectangle, this.anyUnitExceptTwoIntersector
						.reset(sourceUnitToIgnore, sourceSecondUnitToIgnore, forConstruction));
			case FOOT_NO_COLLISION:
				return this.itemsForEnum.intersect(newPossibleRectangle, this.itemEnumIntersectorBoolean);
			case DISABLED:
				return false;
			default:
			case FOOT:
			case HORSE:
			case HOVER:
				return this.groundUnitCollision.intersect(newPossibleRectangle, this.anyUnitExceptTwoIntersector
						.reset(sourceUnitToIgnore, sourceSecondUnitToIgnore, forConstruction));
			}
		}
		return false;
	}

	/**
	 * Whether a ground unit of the given footprint (PathingGrid.getFootprintCells) at x, y shares a pathing cell with the
	 * footprint of another ground unit. Measured: this, not the overlap of collision squares, is what keeps a unit from
	 * being put next to another one, so two Footmen need two cells between their positions and a Knight a Footman
	 * has on its high side one more than on its low side.
	 */
	public boolean intersectsFootprintOtherThan(final PathingGrid pathingGrid, final float x, final float y,
			final int footprintCells, final CUnit sourceUnitToIgnore, final MovementType movementType) {
		return intersectsFootprintOtherThan(pathingGrid, x, y, footprintCells, sourceUnitToIgnore, null, movementType,
				NOT_A_TICK);
	}

	/** As above, but units walking at gameTurnTick do not count. */
	public boolean intersectsFootprintOtherThan(final PathingGrid pathingGrid, final float x, final float y,
			final int footprintCells, final CUnit sourceUnitToIgnore, final CUnit sourceSecondUnitToIgnore,
			final MovementType movementType, final int gameTurnTick) {
		final int firstX = pathingGrid.getFootprintCellX(x, footprintCells);
		final int firstY = pathingGrid.getFootprintCellY(y, footprintCells);
		final float reach = footprintReach(footprintCells);
		final Rectangle area = tempFootprintArea;
		area.set(x - reach, y - reach, reach * 2, reach * 2);
		final AnyUnitExceptTwoIntersector intersector = this.anyUnitExceptTwoIntersector;
		intersector.ignoreControlledBy(null, -1);
		intersector.reset(sourceUnitToIgnore, sourceSecondUnitToIgnore, false);
		intersector.footprint(pathingGrid, firstX, firstY, footprintCells);
		intersector.walkingTick = gameTurnTick;
		try {
			final boolean amphibious = movementType == MovementType.AMPHIBIOUS;
			if (REACH_CHECK) {
				checkFootprintReach(pathingGrid, x, y, footprintCells, intersector, area, amphibious);
			}
			if (amphibious) {
				return this.seaUnitCollision.intersect(area, intersector)
						|| this.groundUnitCollision.intersect(area, intersector);
			}
			return this.groundUnitCollision.intersect(area, intersector);
		}
		finally {
			intersector.walkingTick = NOT_A_TICK;
			intersector.footprint(null, 0, 0, 0);
		}
	}

	public int getUnitChanges() {
		return this.unitChanges;
	}

	/** The reach intersectsFootprintOtherThan looks as far as around a point for a footprint of that many cells. */
	public float footprintCandidateReach(final int footprintCells) {
		return footprintReach(footprintCells);
	}

	/**
	 * Adds the units whose square overlaps the area and that count for a footprint test at all (all but the ones the
	 * caller leaves out itself and the footprint comparison): what intersectsFootprintOtherThan goes through.
	 */
	public void collectFootprintCandidates(final Rectangle area, final boolean amphibious, final int gameTurnTick,
			final List<CUnit> out) {
		final AnyUnitExceptTwoIntersector intersector = this.anyUnitExceptTwoIntersector;
		intersector.ignoreControlledBy(null, -1);
		intersector.reset(null, null, false);
		intersector.footprint(null, 0, 0, 0);
		intersector.walkingTick = gameTurnTick;
		try {
			collectPassing(area, intersector, amphibious, out);
		}
		finally {
			intersector.walkingTick = NOT_A_TICK;
		}
	}

	/**
	 * Half the side of the square that holds every unit whose footprint can share a cell with one of footprintCells.
	 * A unit of c cells stands within 32 * (f / 2 + c / 2 + 1) of the point, and its collision square reaches c * 16 -
	 * 16 (all of MaxCollisionRadius for a bigger one) of that; a cell more for the rounding of the odd sizes.
	 */
	private float footprintReach(final int footprintCells) {
		final float extra = Math.max(0f, (16f * this.maxRegisteredCells) - 16f - this.maxCollisionRadius);
		return Math.min((16f * footprintCells) + 64f + extra, fullFootprintReach(footprintCells));
	}

	private float fullFootprintReach(final int footprintCells) {
		return 32f * (footprintCells + PathingGrid.getFootprintCells(this.maxCollisionRadius) + 1);
	}

	// Looks for what the square of footprintReach would miss: every unit that passes the footprint test in the square
	// of the full reach has to pass it in the small one too.
	private void checkFootprintReach(final PathingGrid pathingGrid, final float x, final float y,
			final int footprintCells, final AnyUnitExceptTwoIntersector intersector, final Rectangle smallArea,
			final boolean amphibious) {
		final float fullReach = fullFootprintReach(footprintCells);
		final Rectangle fullArea = new Rectangle(x - fullReach, y - fullReach, fullReach * 2, fullReach * 2);
		final List<CUnit> inFull = new ArrayList<>();
		final List<CUnit> inSmall = new ArrayList<>();
		collectPassing(fullArea, intersector, amphibious, inFull);
		collectPassing(smallArea, intersector, amphibious, inSmall);
		if (!new HashSet<>(inSmall).containsAll(inFull)) {
			inFull.removeAll(inSmall);
			throw new IllegalStateException("collision reach missed " + inFull.size() + " unit(s) at " + x + ", " + y
					+ " footprint " + footprintCells + " reach " + smallArea.width / 2 + ": " + inFull.get(0).getUnitType()
							.getCollisionSize() + " at " + inFull.get(0).getX() + ", " + inFull.get(0).getY());
		}
	}

	private void collectPassing(final Rectangle area, final AnyUnitExceptTwoIntersector intersector,
			final boolean amphibious, final List<CUnit> out) {
		final QuadtreeIntersector<CUnit> collector = (unit) -> {
			if (intersector.onIntersect(unit)) {
				out.add(unit);
			}
			return false;
		};
		if (amphibious) {
			this.seaUnitCollision.intersect(area, collector);
		}
		this.groundUnitCollision.intersect(area, collector);
	}

	public void translate(final CUnit unit, final float xShift, final float yShift) {
		this.unitChanges++;
		final Rectangle bounds = unit.getCollisionRectangle();
		if (bounds != null) {
			// Also the trees it belongs in now: one put in while it could not move goes into the ground tree once it
			// moves again.
			final int trees = unit.placement.collisionTrees | treesFor(unit);
			unit.placement.collisionTrees = trees;
			final float oldX = bounds.x;
			final float oldY = bounds.y;
			if ((trees & DEAD_TREE) != 0) {
				translateIn(this.deadUnitCollision, unit, bounds, oldX, oldY, xShift, yShift);
			}
			if ((trees & ANY_TREE) != 0) {
				translateIn(this.anyUnitEnumerableCollision, unit, bounds, oldX, oldY, xShift, yShift);
			}
			if ((trees & BUILDING_TREE) != 0) {
				translateIn(this.buildingUnitCollision, unit, bounds, oldX, oldY, xShift, yShift);
			}
			if ((trees & SEA_TREE) != 0) {
				translateIn(this.seaUnitCollision, unit, bounds, oldX, oldY, xShift, yShift);
			}
			if ((trees & GROUND_TREE) != 0) {
				translateIn(this.groundUnitCollision, unit, bounds, oldX, oldY, xShift, yShift);
			}
			if ((trees & AIR_TREE) != 0) {
				translateIn(this.airUnitCollision, unit, bounds, oldX, oldY, xShift, yShift);
			}
		} // else probably moving a dead unit that isn't corpse yet
	}

	// The trees share one rectangle: each finds the unit under where it was.
	private static void translateIn(final Quadtree<CUnit> tree, final CUnit unit, final Rectangle bounds,
			final float oldX, final float oldY, final float xShift, final float yShift) {
		bounds.x = oldX;
		bounds.y = oldY;
		tree.translate(unit, bounds, xShift, yShift);
	}

	public void translate(final CItem item, final float xShift, final float yShift) {
		final Rectangle bounds = item.getOrCreateRegisteredEnumRectangle();
		if (!item.isDead()) {
			this.itemsForEnum.translate(item, bounds, xShift, yShift);
		}
	}

	private static final int NOT_A_TICK = Integer.MIN_VALUE;

	private static final class AnyUnitExceptTwoIntersector implements QuadtreeIntersector<CUnit> {
		private CUnit firstUnit;
		private CUnit secondUnit;
		private boolean forConstruction;

		private CSimulation simulation;
		private int ignoredControllerIndex = -1;
		private int walkingTick = NOT_A_TICK;
		private PathingGrid footprintGrid;
		private int footprintX;
		private int footprintY;
		private int footprintCells;

		public void footprint(final PathingGrid pathingGrid, final int firstX, final int firstY, final int cells) {
			this.footprintGrid = pathingGrid;
			this.footprintX = firstX;
			this.footprintY = firstY;
			this.footprintCells = cells;
		}

		public AnyUnitExceptTwoIntersector reset(final CUnit firstUnit, final CUnit secondUnit,
				final boolean forConstruction) {
			this.firstUnit = firstUnit;
			this.secondUnit = secondUnit;
			this.forConstruction = forConstruction;
			return this;
		}

		public void ignoreControlledBy(final CSimulation simulation, final int ignoredControllerIndex) {
			this.simulation = simulation;
			this.ignoredControllerIndex = simulation == null ? -1 : ignoredControllerIndex;
		}

		@Override
		public boolean onIntersect(final CUnit intersectingObject) {
			// A unit with its pathing turned off walks through others, but they still go round it (1.29).
			if (intersectingObject.isHidden() || intersectingObject.isUnenumerable()
					|| (MovementType.FOOT_NO_COLLISION.equals(intersectingObject.getMovementType())
							&& !intersectingObject.isPathingDisabled())
					|| (this.forConstruction && intersectingObject.isNoBuildingCollision())
					|| (!this.forConstruction && intersectingObject.isNoUnitCollision())) {
				return false;
			}
			if ((this.walkingTick != NOT_A_TICK) && intersectingObject.isWalking(this.walkingTick)) {
				return false;
			}
			if (this.ignoredControllerIndex >= 0) {
				final int ownerIndex = intersectingObject.getPlayerIndex();
				if ((ownerIndex == this.ignoredControllerIndex) || this.simulation.getPlayer(ownerIndex)
						.hasAlliance(this.ignoredControllerIndex, CAllianceType.SHARED_CONTROL)) {
					return false;
				}
			}
			if ((intersectingObject == this.firstUnit) || (intersectingObject == this.secondUnit)) {
				return false;
			}
			if (this.footprintGrid == null) {
				return true;
			}
			final float collisionSize = intersectingObject.getUnitType().getCollisionSize();
			if (collisionSize <= 0f) {
				return false;
			}
			final int cells = PathingGrid.getFootprintCells(collisionSize);
			final int otherX = this.footprintGrid.getFootprintCellX(intersectingObject.getX(), cells);
			final int otherY = this.footprintGrid.getFootprintCellY(intersectingObject.getY(), cells);
			return (otherX < (this.footprintX + this.footprintCells)) && (this.footprintX < (otherX + cells))
					&& (otherY < (this.footprintY + this.footprintCells)) && (this.footprintY < (otherY + cells));
		}
	}

	private static final class EachUnitOnlyOnceIntersector implements QuadtreeIntersector<CUnit> {
		private CUnitEnumFunction consumerDelegate;
		private final Set<CUnit> intersectedUnits = new HashSet<>();
		private boolean done;

		public EachUnitOnlyOnceIntersector reset(final CUnitEnumFunction consumerDelegate) {
			this.consumerDelegate = consumerDelegate;
			this.intersectedUnits.clear();
			this.done = false;
			return this;
		}

		@Override
		public boolean onIntersect(final CUnit intersectingObject) {
			if (intersectingObject.isHidden() || intersectingObject.isUnenumerable()) {
				return false;
			}
			if (this.done) {
				// This check is because we may use the intersector for multiple intersect
				// calls, see "enumUnitsInRect" and how it uses this intersector first on the
				// ground unit layer, then the flying unit layer, without recycling
				return true;
			}
			if (this.intersectedUnits.add(intersectingObject)) {
				this.done = this.consumerDelegate.call(intersectingObject);
				return this.done;
			}
			return false;
		}
	}

	private static final class ItemEnumIntersectorBoolean implements QuadtreeIntersector<CItem> {

		@Override
		public boolean onIntersect(final CItem intersectingObject) {
			if (intersectingObject.isHidden()) {
				return false;
			}
			return true;
		}
	}
}
