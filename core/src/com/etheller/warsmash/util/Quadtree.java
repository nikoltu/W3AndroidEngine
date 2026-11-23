package com.etheller.warsmash.util;

import java.util.function.Consumer;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

public class Quadtree<T> {
	private static final int MAX_DEPTH = 9; // 2^9 = 512, and 512 is the biggest map size...
	private static final int SPLIT_THRESHOLD = 6;

	private final Rectangle bounds;
	private Quadtree<T> northeast;
	private Quadtree<T> northwest;
	private Quadtree<T> southwest;
	private Quadtree<T> southeast;
	private final Array<Node<T>> nodes = new Array<>();
	private boolean leaf = true;
	private final NodeAdder nodeAdder = new NodeAdder();
	private final UniqueNodeAdder uniqueNodeAdder = new UniqueNodeAdder();
	private final boolean keepCoveringLeaves;

	public Quadtree(final Rectangle bounds) {
		this(bounds, false);
	}

	/**
	 * @param keepCoveringLeaves whether a leaf whose objects all cover the whole of it stays a leaf, however many they
	 *                           are: its quarters would each hold the same list, and a few map-wide rects (regions
	 *                           of a map script) split the tree to its full depth, hundreds of thousands of nodes.
	 *                           A walk meets the objects in the same order either way, only not more than once per
	 *                           leaf.
	 */
	public Quadtree(final Rectangle bounds, final boolean keepCoveringLeaves) {
		this.bounds = bounds;
		this.keepCoveringLeaves = keepCoveringLeaves;
	}

	/** How many nodes (leaves and splits) the tree has. */
	public int nodeCount() {
		if (this.leaf) {
			return 1;
		}
		return 1 + this.northeast.nodeCount() + this.northwest.nodeCount() + this.southwest.nodeCount()
				+ this.southeast.nodeCount();
	}

	public void add(final T object, final Rectangle bounds) {
		final Node<T> node = new Node<T>(object, bounds);
		add(node, 0);
	}

	public void remove(final T object, final Rectangle bounds) {
		remove(object, bounds, null);
	}

	public void translate(final T object, final Rectangle prevBoundsToUpdate, final float xShift, final float yShift) {
		Node<T> node = remove(object, prevBoundsToUpdate, null);
		prevBoundsToUpdate.x += xShift;
		prevBoundsToUpdate.y += yShift;
		if (node == null) {
			// Not found under its bounds (moved during an intersect walk); adding it again beats a null node.
			node = new Node<T>(object, prevBoundsToUpdate);
		}
		add(node, 0);
	}

	public boolean intersect(final Rectangle bounds, final QuadtreeIntersector<T> intersector) {
		if (this.leaf) {
			for (int i = 0; i < this.nodes.size; i++) {
				final Node<T> node = this.nodes.get(i);
				if (node.bounds.overlaps(bounds)) {
					if (intersector.onIntersect(node.object)) {
						return true;
					}
				}
			}
			return false;
		}
		else {
			// An intersector may move or remove objects, which can merge this node mid-walk.
			final Quadtree<T> northeast = this.northeast;
			final Quadtree<T> northwest = this.northwest;
			final Quadtree<T> southwest = this.southwest;
			final Quadtree<T> southeast = this.southeast;
			if (northeast.bounds.overlaps(bounds)) {
				if (northeast.intersect(bounds, intersector)) {
					return true;
				}
			}
			if (northwest.bounds.overlaps(bounds)) {
				if (northwest.intersect(bounds, intersector)) {
					return true;
				}
			}
			if (southwest.bounds.overlaps(bounds)) {
				if (southwest.intersect(bounds, intersector)) {
					return true;
				}
			}
			if (southeast.bounds.overlaps(bounds)) {
				if (southeast.intersect(bounds, intersector)) {
					return true;
				}
			}
			return false;
		}
	}

	/** Tests the objects of the leaves that meet the bounds, each with its own bounds, which the tester checks. */
	public interface NodeTester<T> {
		boolean test(T object, Rectangle objectBounds);
	}

	public boolean intersectNodes(final Rectangle bounds, final NodeTester<T> tester) {
		if (this.leaf) {
			for (int i = 0; i < this.nodes.size; i++) {
				final Node<T> node = this.nodes.get(i);
				if (tester.test(node.object, node.bounds)) {
					return true;
				}
			}
			return false;
		}
		return (this.northeast.bounds.overlaps(bounds) && this.northeast.intersectNodes(bounds, tester))
				|| (this.northwest.bounds.overlaps(bounds) && this.northwest.intersectNodes(bounds, tester))
				|| (this.southwest.bounds.overlaps(bounds) && this.southwest.intersectNodes(bounds, tester))
				|| (this.southeast.bounds.overlaps(bounds) && this.southeast.intersectNodes(bounds, tester));
	}

	public boolean intersect(final float x, final float y, final QuadtreeIntersector<T> intersector) {
		if (this.leaf) {
			for (int i = 0; i < this.nodes.size; i++) {
				final Node<T> node = this.nodes.get(i);
				if (node.bounds.contains(x, y)) {
					if (intersector.onIntersect(node.object)) {
						return true;
					}
				}
			}
			return false;
		}
		else {
			// An intersector may move or remove objects, which can merge this node mid-walk.
			final Quadtree<T> northeast = this.northeast;
			final Quadtree<T> northwest = this.northwest;
			final Quadtree<T> southwest = this.southwest;
			final Quadtree<T> southeast = this.southeast;
			if (northeast.bounds.contains(x, y)) {
				if (northeast.intersect(x, y, intersector)) {
					return true;
				}
			}
			if (northwest.bounds.contains(x, y)) {
				if (northwest.intersect(x, y, intersector)) {
					return true;
				}
			}
			if (southwest.bounds.contains(x, y)) {
				if (southwest.intersect(x, y, intersector)) {
					return true;
				}
			}
			if (southeast.bounds.contains(x, y)) {
				if (southeast.intersect(x, y, intersector)) {
					return true;
				}
			}
			return false;
		}
	}

	private void add(final Node<T> node, final int depth) {
		if ((depth == 0) && !this.bounds.overlaps(node.bounds)) {
			// Outside the world (a unit moved off the map edge): it takes no part in collision until it is back.
			return;
		}
		if (this.leaf) {
			if ((this.nodes.size >= SPLIT_THRESHOLD) && (depth < MAX_DEPTH) && !allCover(node)) {
				split(depth);
				// then dont return and add as a nonleaf
			}
			else {
				this.nodes.add(node);
				return;
			}
		}
		boolean overlapsAny = false;
		if (this.northeast.bounds.overlaps(node.bounds)) {
			this.northeast.add(node, depth + 1);
			overlapsAny = true;
		}
		if (this.northwest.bounds.overlaps(node.bounds)) {
			this.northwest.add(node, depth + 1);
			overlapsAny = true;
		}
		if (this.southwest.bounds.overlaps(node.bounds)) {
			this.southwest.add(node, depth + 1);
			overlapsAny = true;
		}
		if (this.southeast.bounds.overlaps(node.bounds)) {
			this.southeast.add(node, depth + 1);
			overlapsAny = true;
		}
		if (!overlapsAny) {
			throw new IllegalStateException("Does not overlap anything! " + node.bounds);
		}
	}

	/** Whether this leaf keeps its objects although it is full, see {@link #Quadtree(Rectangle, boolean)}. */
	private boolean allCover(final Node<T> added) {
		if (!this.keepCoveringLeaves || !covers(added.bounds)) {
			return false;
		}
		for (int i = 0; i < this.nodes.size; i++) {
			if (!covers(this.nodes.get(i).bounds)) {
				return false;
			}
		}
		return true;
	}

	private boolean covers(final Rectangle objectBounds) {
		return (objectBounds.x <= this.bounds.x) && (objectBounds.y <= this.bounds.y)
				&& ((objectBounds.x + objectBounds.width) >= (this.bounds.x + this.bounds.width))
				&& ((objectBounds.y + objectBounds.height) >= (this.bounds.y + this.bounds.height));
	}

	private void split(final int depth) {
		final int splitDepth = depth + 1;
		final float halfWidth = this.bounds.width / 2;
		final float x = this.bounds.x;
		final float xMidpoint = x + halfWidth;
		final float halfHeight = this.bounds.height / 2;
		final float y = this.bounds.y;
		final float yMidpoint = y + halfHeight;
		this.northeast = new Quadtree<>(new Rectangle(xMidpoint, yMidpoint, halfWidth, halfHeight),
				this.keepCoveringLeaves);
		this.northwest = new Quadtree<>(new Rectangle(x, yMidpoint, halfWidth, halfHeight), this.keepCoveringLeaves);
		this.southwest = new Quadtree<>(new Rectangle(x, y, halfWidth, halfHeight), this.keepCoveringLeaves);
		this.southeast = new Quadtree<>(new Rectangle(xMidpoint, y, halfWidth, halfHeight), this.keepCoveringLeaves);
		this.leaf = false;
		this.nodes.forEach(this.nodeAdder.reset(splitDepth));
		this.nodes.clear();
	}

	private Node<T> remove(final T object, final Rectangle bounds, final Quadtree<T> parent) {
		Node<T> returnValue = null;
		if (this.leaf) {
			for (int i = 0; i < this.nodes.size; i++) {
				if (this.nodes.get(i).object == object) {
					returnValue = this.nodes.removeIndex(i);
					break;
				}
			}
		}
		else {
			if (this.northeast.bounds.overlaps(bounds)) {
				final Node<T> removed = this.northeast.remove(object, bounds, this);
				if (removed != null) {
					returnValue = removed;
				}
			}
			if (this.northwest.bounds.overlaps(bounds)) {
				final Node<T> removed = this.northwest.remove(object, bounds, this);
				if (removed != null) {
					returnValue = removed;
				}
			}
			if (this.southwest.bounds.overlaps(bounds)) {
				final Node<T> removed = this.southwest.remove(object, bounds, this);
				if (removed != null) {
					returnValue = removed;
				}
			}
			if (this.southeast.bounds.overlaps(bounds)) {
				final Node<T> removed = this.southeast.remove(object, bounds, this);
				if (removed != null) {
					returnValue = removed;
				}
			}
			mergeIfNecessary();
		}
		return returnValue;
	}

	private void mergeIfNecessary() {
		if (this.northeast.leaf && this.northwest.leaf && this.southwest.leaf && this.southeast.leaf) {
			final int children = this.northeast.nodes.size + this.northwest.nodes.size + this.southwest.nodes.size
					+ this.southeast.nodes.size; // might include duplicates
			if (children <= SPLIT_THRESHOLD) {
				this.leaf = true;
				addAllUnique(this.northeast.nodes);
				addAllUnique(this.northwest.nodes);
				addAllUnique(this.southwest.nodes);
				addAllUnique(this.southeast.nodes);
				this.northeast = this.northwest = this.southwest = this.southeast = null;
			}
		}
	}

	private void addAllUnique(final Array<Node<T>> nodes) {
		nodes.forEach(this.uniqueNodeAdder);
	}

	private static final class Node<T> {
		private final T object;
		private final Rectangle bounds;

		public Node(final T object, final Rectangle bounds) {
			this.object = object;
			this.bounds = bounds;
		}
	}

	private final class NodeAdder implements Consumer<Node<T>> {
		private int splitDepth;

		private NodeAdder reset(final int splitDepth) {
			this.splitDepth = splitDepth;
			return this;
		}

		@Override
		public void accept(final Node<T> node) {
			add(node, this.splitDepth);
		}
	}

	private final class UniqueNodeAdder implements Consumer<Node<T>> {

		private UniqueNodeAdder reset() {
			return this;
		}

		@Override
		public void accept(final Node<T> node) {
			for (int i = 0; i < Quadtree.this.nodes.size; i++) {
				if (Quadtree.this.nodes.get(i) == node) {
					return;
				}
			}
			Quadtree.this.nodes.add(node);
		}
	}
}
