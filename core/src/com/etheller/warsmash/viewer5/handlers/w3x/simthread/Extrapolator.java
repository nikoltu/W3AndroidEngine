package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

/**
 * Where a unit or a missile is shown at the presentation time, from the frames around it: interpolated between the
 * frame before and the frame after, or, when the clock has run past the newest frame, carried on from it for the
 * extrapolated time (ground units along their path points, the rest along their last heading, missiles toward their
 * target). Standing units stay where they are. Nothing here reads the simulation.
 * <p>
 * Render thread only. The pose objects are reused by their owner every frame and hold a row hint, so a lookup is
 * usually a single comparison instead of a search.
 */
public final class Extrapolator {
	/** Kinds of {@link WorldObjectFrameState} of a frame. */
	public static final int PROJECTILES = 0;
	public static final int ITEMS = 1;
	public static final int TREES = 2;

	/** Matches a row of any creation serial (a lookup by handle alone). */
	public static final int ANY_SERIAL = Integer.MIN_VALUE;

	/** A unit that moves further than this between two frames was moved by the game (a blink), not walked. */
	private static final float TELEPORT_DISTANCE = 200f;

	private Extrapolator() {
	}

	/** The result of {@link #sampleUnit}, and the lookup hints. Owned by one render object. */
	public static final class UnitPose {
		private int hintBefore = -1;
		private int hintAfter = -1;
		private int hintPrevious = -1;
		/** The frame state the discrete values (flags, colours) are taken from, and the row in it. */
		public UnitFrameState state;
		public int row;
		public float x;
		public float y;
		public float z;
		public float facing;
		public float constructRatio;
		/** Seconds left on the fade timer at the presentation time. */
		public float fadeRemaining;
		/** The position comes from extrapolation. */
		public boolean extrapolated;
	}

	/** The result of {@link #sampleObject}. Owned by one render object. */
	public static final class ObjectPose {
		private int hintBefore = -1;
		private int hintAfter = -1;
		private int hintPrevious = -1;
		private int hintTarget = -1;
		public WorldObjectFrameState state;
		public int row;
		public float x;
		public float y;
		public float z;
		public float targetX;
		public float targetY;
		public boolean extrapolated;
	}

	/** As {@link #sampleUnit(PresentationClock, int, int, UnitPose)} for whichever unit has the handle. */
	public static boolean sampleUnit(final PresentationClock clock, final int handle, final UnitPose pose) {
		return sampleUnit(clock, handle, ANY_SERIAL, pose);
	}

	/**
	 * Fills the pose for the unit with the handle and creation serial; false when no frame near the presentation time
	 * has the unit (it is not made yet, or already gone, and a later unit may hold the handle), and the pose is then
	 * left as it was.
	 */
	public static boolean sampleUnit(final PresentationClock clock, final int handle, final int serial,
			final UnitPose pose) {
		final SimFrame before = clock.frameBefore();
		if (before == null) {
			return false;
		}
		final SimFrame after = clock.frameAfter();
		final int rowBefore = rowOf(before.units, handle, serial, pose.hintBefore);
		final int rowAfter = after == null ? -1 : rowOf(after.units, handle, serial, pose.hintAfter);
		pose.hintBefore = rowBefore;
		pose.hintAfter = rowAfter;
		if (rowBefore < 0) {
			if (rowAfter < 0) {
				return false;
			}
			// made between the two frames: it shows from the later one
			final UnitFrameState s = after.units;
			pose.state = s;
			pose.row = rowAfter;
			pose.x = s.x[rowAfter];
			pose.y = s.y[rowAfter];
			pose.z = s.z[rowAfter];
			pose.facing = s.facing[rowAfter];
			pose.constructRatio = s.constructRatio[rowAfter];
			pose.fadeRemaining = s.fadeRemaining[rowAfter];
			pose.extrapolated = false;
			return true;
		}
		final UnitFrameState b = before.units;
		pose.state = b;
		pose.row = rowBefore;
		final float elapsedSeconds = Math.max(0f, clock.presentationMillisExact() - before.gameTimeMillis) / 1000f;
		pose.fadeRemaining = Math.max(0f, b.fadeRemaining[rowBefore] - elapsedSeconds);
		pose.extrapolated = false;
		if (rowAfter >= 0) {
			final UnitFrameState a = after.units;
			final float t = clock.interpolation();
			final float jumpX = a.x[rowAfter] - b.x[rowBefore];
			final float jumpY = a.y[rowAfter] - b.y[rowBefore];
			final float tPosition = ((jumpX * jumpX) + (jumpY * jumpY)) > (TELEPORT_DISTANCE * TELEPORT_DISTANCE)
					? (t < 0.5f ? 0f : 1f)
					: t;
			pose.x = b.x[rowBefore] + (jumpX * tPosition);
			pose.y = b.y[rowBefore] + (jumpY * tPosition);
			pose.z = b.z[rowBefore] + ((a.z[rowAfter] - b.z[rowBefore]) * t);
			pose.facing = lerpDegrees(b.facing[rowBefore], a.facing[rowAfter], t);
			pose.constructRatio = b.constructRatio[rowBefore]
					+ ((a.constructRatio[rowAfter] - b.constructRatio[rowBefore]) * t);
			return true;
		}
		pose.x = b.x[rowBefore];
		pose.y = b.y[rowBefore];
		pose.z = b.z[rowBefore];
		pose.facing = b.facing[rowBefore];
		pose.constructRatio = b.constructRatio[rowBefore];
		final long extra = after == null ? clock.extrapolatedMillis() : 0;
		if ((extra > 0) && (b.speed[rowBefore] > 0)) {
			extrapolateUnit(clock, before, handle, serial, rowBefore, extra, pose);
		}
		return true;
	}

	private static void extrapolateUnit(final PresentationClock clock, final SimFrame before, final int handle,
			final int serial, final int row, final long extraMillis, final UnitPose pose) {
		final UnitFrameState s = before.units;
		float remaining = (s.speed[row] * extraMillis) / 1000f;
		float cx = s.x[row];
		float cy = s.y[row];
		final int points = s.pathCount[row];
		if (points > 0) {
			final int base = row * UnitFrameState.PATH_POINTS * 2;
			for (int i = 0; (i < points) && (remaining > 0); i++) {
				final float px = s.path[base + (i * 2)];
				final float py = s.path[base + (i * 2) + 1];
				final float dx = px - cx;
				final float dy = py - cy;
				final float segment = (float) Math.sqrt((dx * dx) + (dy * dy));
				if (segment >= remaining) {
					cx += (dx * remaining) / segment;
					cy += (dy * remaining) / segment;
					remaining = 0;
				}
				else {
					cx = px;
					cy = py;
					remaining -= segment;
				}
			}
		}
		else {
			final SimFrame previous = clock.framePrevious();
			final int previousRow = previous == null ? -1 : rowOf(previous.units, handle, serial, pose.hintPrevious);
			pose.hintPrevious = previousRow;
			if (previousRow < 0) {
				return;
			}
			final float dx = s.x[row] - previous.units.x[previousRow];
			final float dy = s.y[row] - previous.units.y[previousRow];
			final float length = (float) Math.sqrt((dx * dx) + (dy * dy));
			if (length <= 0f) {
				return;
			}
			cx += (dx * remaining) / length;
			cy += (dy * remaining) / length;
		}
		pose.x = cx;
		pose.y = cy;
		pose.extrapolated = true;
	}

	/**
	 * As {@link #sampleUnit} for a projectile, item or tree row. The target of a missile that chases a unit is
	 * taken from that unit's row in the frame.
	 */
	public static boolean sampleObject(final PresentationClock clock, final int kind, final int handle,
			final int serial, final ObjectPose pose) {
		final SimFrame before = clock.frameBefore();
		if (before == null) {
			return false;
		}
		final SimFrame after = clock.frameAfter();
		final WorldObjectFrameState b = objects(before, kind);
		final int rowBefore = rowOf(b, handle, serial, pose.hintBefore);
		final WorldObjectFrameState a = after == null ? null : objects(after, kind);
		final int rowAfter = a == null ? -1 : rowOf(a, handle, serial, pose.hintAfter);
		pose.hintBefore = rowBefore;
		pose.hintAfter = rowAfter;
		pose.extrapolated = false;
		if (rowBefore < 0) {
			if (rowAfter < 0) {
				return false;
			}
			setObject(pose, a, rowAfter);
			return true;
		}
		setObject(pose, b, rowBefore);
		if (rowAfter >= 0) {
			final float t = clock.interpolation();
			pose.x = b.x[rowBefore] + ((a.x[rowAfter] - b.x[rowBefore]) * t);
			pose.y = b.y[rowBefore] + ((a.y[rowAfter] - b.y[rowBefore]) * t);
			pose.z = b.z[rowBefore] + ((a.z[rowAfter] - b.z[rowBefore]) * t);
			pose.targetX = b.targetX[rowBefore] + ((a.targetX[rowAfter] - b.targetX[rowBefore]) * t);
			pose.targetY = b.targetY[rowBefore] + ((a.targetY[rowAfter] - b.targetY[rowBefore]) * t);
			return true;
		}
		final long extra = after == null ? clock.extrapolatedMillis() : 0;
		if ((kind == PROJECTILES) && (extra > 0) && (b.speed[rowBefore] > 0)
				&& ((b.flags[rowBefore] & WorldObjectFrameState.DEAD) == 0)) {
			float tx = b.targetX[rowBefore];
			float ty = b.targetY[rowBefore];
			if (b.target[rowBefore] != 0) {
				final int unitRow = rowOf(before.units, b.target[rowBefore], pose.hintTarget);
				pose.hintTarget = unitRow;
				if (unitRow >= 0) {
					tx = before.units.x[unitRow];
					ty = before.units.y[unitRow];
				}
			}
			final float dx = tx - b.x[rowBefore];
			final float dy = ty - b.y[rowBefore];
			final float length = (float) Math.sqrt((dx * dx) + (dy * dy));
			if (length > 0f) {
				final float step = Math.min(length, (b.speed[rowBefore] * extra) / 1000f);
				pose.x = b.x[rowBefore] + ((dx * step) / length);
				pose.y = b.y[rowBefore] + ((dy * step) / length);
				pose.extrapolated = true;
			}
		}
		return true;
	}

	private static void setObject(final ObjectPose pose, final WorldObjectFrameState s, final int row) {
		pose.state = s;
		pose.row = row;
		pose.x = s.x[row];
		pose.y = s.y[row];
		pose.z = s.z[row];
		pose.targetX = s.targetX[row];
		pose.targetY = s.targetY[row];
	}

	/** The object state of a kind in a frame. */
	public static WorldObjectFrameState objects(final SimFrame frame, final int kind) {
		return kind == PROJECTILES ? frame.projectiles : (kind == ITEMS ? frame.items : frame.trees);
	}

	/** Row of the handle in the state, trying the hint first; -1 when absent. */
	public static int rowOf(final UnitFrameState s, final int handle, final int hint) {
		return rowOf(s, handle, ANY_SERIAL, hint);
	}

	/** Row of the handle and creation serial ({@link #ANY_SERIAL} for any), trying the hint first; -1 when absent. */
	public static int rowOf(final UnitFrameState s, final int handle, final int serial, final int hint) {
		final int row = ((hint >= 0) && (hint < s.count) && (s.handle[hint] == handle)) ? hint : s.indexOf(handle);
		return (row >= 0) && (serial != ANY_SERIAL) && (s.serial[row] != serial) ? -1 : row;
	}

	public static int rowOf(final WorldObjectFrameState s, final int handle, final int hint) {
		return rowOf(s, handle, ANY_SERIAL, hint);
	}

	public static int rowOf(final WorldObjectFrameState s, final int handle, final int serial, final int hint) {
		final int row = findRow(s, handle, hint);
		return (row >= 0) && (serial != ANY_SERIAL) && (s.serial[row] != serial) ? -1 : row;
	}

	private static int findRow(final WorldObjectFrameState s, final int handle, final int hint) {
		if ((hint >= 0) && (hint < s.count) && (s.handle[hint] == handle)) {
			return hint;
		}
		int low = 0;
		int high = s.count - 1;
		while (low <= high) {
			final int mid = (low + high) >>> 1;
			final int value = s.handle[mid];
			if (value < handle) {
				low = mid + 1;
			}
			else if (value > handle) {
				high = mid - 1;
			}
			else {
				return mid;
			}
		}
		return -1;
	}

	/** Between two angles in degrees along the shorter way round. */
	public static float lerpDegrees(final float from, final float to, final float t) {
		float delta = (to - from) % 360f;
		if (delta > 180f) {
			delta -= 360f;
		}
		else if (delta < -180f) {
			delta += 360f;
		}
		return from + (delta * t);
	}
}
