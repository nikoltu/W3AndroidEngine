package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.Arrays;

/**
 * Per-step state of projectiles, items and trees (destructables), which are far fewer and simpler than units. One
 * instance holds one kind; the owning {@link SimFrame} has one for each. Writer: the simulation thread while the frame
 * is built. Reader: the render thread after publication. Rows are sorted by ascending handle.
 * <p>
 * Columns by kind: projectiles use speed and target (handle of the unit chased, 0 for a point); items use the
 * {@link #VISIBLE} and {@link #CARRIED} flags and target for the script's colour (-1 for none); trees use {@link #life} (fraction) and {@link #DEAD}.
 */
public final class WorldObjectFrameState {
	public static final int VISIBLE = 1;
	public static final int DEAD = 1 << 1;
	public static final int CARRIED = 1 << 2;
	public static final int BLIGHTED = 1 << 3;
	/** Trees: targeted as a tree (the ones a tap on the ground may pick). */
	public static final int TREE = 1 << 4;

	public int count;
	public int[] handle = new int[0];
	/** Items and trees: the creation serial of the object (see {@link UnitFrameState#serial}); projectiles: 0. */
	public int[] serial = new int[0];
	public float[] x = new float[0];
	public float[] y = new float[0];
	public float[] z = new float[0];
	public float[] speed = new float[0];
	public int[] target = new int[0];
	public float[] life = new float[0];
	/** Projectiles: where the missile is headed when it chases a point. */
	public float[] targetX = new float[0];
	public float[] targetY = new float[0];
	public int[] flags = new int[0];

	public void clear() {
		count = 0;
	}

	/** Row of the handle, or -1; rows are sorted by handle. */
	public int indexOf(final int objectHandle) {
		final int index = Arrays.binarySearch(handle, 0, count, objectHandle);
		return index >= 0 ? index : -1;
	}

	/** Row of the object with the handle and creation serial, or -1. */
	public int indexOf(final int objectHandle, final int objectSerial) {
		final int index = indexOf(objectHandle);
		return (index >= 0) && (serial[index] == objectSerial) ? index : -1;
	}

	public int addRow() {
		if (count == handle.length) {
			final int capacity = Math.max(32, count * 2);
			handle = Arrays.copyOf(handle, capacity);
			serial = Arrays.copyOf(serial, capacity);
			x = Arrays.copyOf(x, capacity);
			y = Arrays.copyOf(y, capacity);
			z = Arrays.copyOf(z, capacity);
			speed = Arrays.copyOf(speed, capacity);
			target = Arrays.copyOf(target, capacity);
			life = Arrays.copyOf(life, capacity);
			targetX = Arrays.copyOf(targetX, capacity);
			targetY = Arrays.copyOf(targetY, capacity);
			flags = Arrays.copyOf(flags, capacity);
		}
		return count++;
	}
}
