package com.etheller.warsmash.util;

import java.util.Locale;

import com.badlogic.gdx.Gdx;

/**
 * Measures how long a unit order takes from the input event to the first frame that shows the unit turning or moving, one order
 * at a time. Only active with -Dwarsmash.inputlatency=true; every hook is a no-op otherwise.
 */
public final class InputLatencyProbe {
	public static final boolean ENABLED = Boolean.getBoolean("warsmash.inputlatency");

	private static final int IDLE = 0;
	private static final int SUBMITTED = 1;
	private static final int DRAINED = 2;
	private static final int APPLIED = 3;
	private static final int MOVED = 4;
	private static final float MOVE_DISTANCE = 1.0f;
	private static final float TURN_DEGREES = 3.0f;
	private static final long TIMEOUT_NANOS = 3_000_000_000L;

	private static volatile int watchId = -1;
	private static int state = IDLE;
	private static long eventNanos;
	private static volatile long lastInputNanos;
	private static long submitNanos;
	private static long drainNanos;
	private static long applyNanos;
	private static long reactNanos;
	private static boolean haveBase;
	private static boolean movedBefore;
	private static float baseX;
	private static float baseY;
	private static float baseFacing;

	private InputLatencyProbe() {
	}

	/** A touch event reached the game. */
	public static void input() {
		lastInputNanos = System.nanoTime();
	}

	/** Interface thread: a unit order was queued for the simulation thread. */
	public static synchronized void submitted(final int unitHandleId) {
		if (state != IDLE) {
			return;
		}
		submitNanos = System.nanoTime();
		eventNanos = lastInputNanos;
		haveBase = false;
		movedBefore = false;
		watchId = unitHandleId;
		state = SUBMITTED;
	}

	/** The order reached the list the next step applies (the simulation thread, or the interface thread unthreaded). */
	public static synchronized void delayed(final int unitHandleId) {
		if (state == IDLE) {
			submitted(unitHandleId);
		}
		if ((state == SUBMITTED) && (unitHandleId == watchId)) {
			drainNanos = System.nanoTime();
			state = DRAINED;
		}
	}

	/** The step end that applies the delayed orders. */
	public static synchronized void applying() {
		if (state == DRAINED) {
			applyNanos = System.nanoTime();
			state = APPLIED;
		}
	}

	/** Interface thread, for each unit when its picture is placed for the frame. */
	public static void placed(final int unitHandleId, final float x, final float y, final float facing) {
		if (unitHandleId != watchId) {
			return;
		}
		synchronized (InputLatencyProbe.class) {
			if (state == IDLE) {
				return;
			}
			if (!haveBase) {
				haveBase = true;
				baseX = x;
				baseY = y;
				baseFacing = facing;
				return;
			}
			float turned = Math.abs(facing - baseFacing) % 360f;
			turned = Math.min(turned, 360f - turned);
			if ((Math.hypot(x - baseX, y - baseY) <= MOVE_DISTANCE) && (turned <= TURN_DEGREES)) {
				return;
			}
			if (state == APPLIED) {
				reactNanos = System.nanoTime();
				state = MOVED;
			}
			else if (state != MOVED) {
				movedBefore = true;
				baseX = x;
				baseY = y;
				baseFacing = facing;
			}
		}
	}

	/** Interface thread, when the frame is done. */
	public static synchronized void frameEnd() {
		if (state == IDLE) {
			return;
		}
		final long now = System.nanoTime();
		if (state == MOVED) {
			final boolean haveEvent = (eventNanos != 0) && ((submitNanos - eventNanos) < 2_000_000_000L);
			Gdx.app.log("INLAT", String.format(Locale.ROOT,
					"unit=%d busy=%b ev2submit=%.1f submit2drain=%.1f drain2apply=%.1f apply2react=%.1f react2frame=%.1f submit2frame=%.1f total=%.1f",
					watchId, movedBefore, haveEvent ? (submitNanos - eventNanos) / 1e6 : -1.0,
					(drainNanos - submitNanos) / 1e6, (applyNanos - drainNanos) / 1e6, (reactNanos - applyNanos) / 1e6,
					(now - reactNanos) / 1e6, (now - submitNanos) / 1e6,
					haveEvent ? (now - eventNanos) / 1e6 : -1.0));
			reset();
		}
		else if ((now - submitNanos) > TIMEOUT_NANOS) {
			Gdx.app.log("INLAT", "timeout state=" + state + " unit=" + watchId);
			reset();
		}
	}

	private static void reset() {
		state = IDLE;
		watchId = -1;
	}
}
