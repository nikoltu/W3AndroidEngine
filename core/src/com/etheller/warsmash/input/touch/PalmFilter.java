package com.etheller.warsmash.input.touch;

import java.util.HashSet;
import java.util.Set;

/**
 * Drops touches that are most likely the hand resting on the screen: very wide contacts, and with the default policy
 * every finger while the pen is touching or hovering, and for a short time after it leaves. A dropped pointer stays
 * dropped until it lifts, so its later moves cannot start a gesture halfway.
 */
public final class PalmFilter {
	public enum PenFingerPolicy {
		IGNORE, ALLOW
	}

	private static final long PEN_GRACE_MILLIS = 500;

	private final float palmTouchMajorPx;
	private final PenFingerPolicy policy;
	private final Set<Integer> dropped = new HashSet<>();
	private boolean penActive;
	private long penNearUntil;

	public PalmFilter(final float palmTouchMajorPx, final PenFingerPolicy policy) {
		this.palmTouchMajorPx = palmTouchMajorPx;
		this.policy = policy;
	}

	/**
	 * @return whether the event should go on to gesture recognition and hover handling
	 */
	public boolean accept(final PointerEvent event) {
		final boolean pen = (event.tool == PointerEvent.Tool.STYLUS) || (event.tool == PointerEvent.Tool.ERASER);
		switch (event.action) {
		case HOVER_MOVE:
			if (pen) {
				this.penActive = true;
			}
			return true;
		case HOVER_EXIT:
			if (pen) {
				penLeft(event.timeMillis);
			}
			return true;
		case DOWN:
			if (pen) {
				this.penActive = true;
				return true;
			}
			if (((this.policy == PenFingerPolicy.IGNORE) && isPenNear(event.timeMillis))
					|| (event.touchMajor > this.palmTouchMajorPx)) {
				this.dropped.add(event.pointerId);
				return false;
			}
			return true;
		case MOVE:
			return !this.dropped.contains(event.pointerId);
		case UP:
		case CANCEL:
			if (this.dropped.remove(event.pointerId)) {
				return false;
			}
			if (pen) {
				penLeft(event.timeMillis);
			}
			return true;
		default:
			return true;
		}
	}

	private void penLeft(final long timeMillis) {
		this.penActive = false;
		this.penNearUntil = timeMillis + PEN_GRACE_MILLIS;
	}

	private boolean isPenNear(final long timeMillis) {
		return this.penActive || (timeMillis < this.penNearUntil);
	}
}
