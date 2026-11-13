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

	/**
	 * When the contact size limit is used: AUTO only on devices with a pen (or once a pen was seen), since a finger
	 * alone is never a palm on a phone; ON always; OFF never.
	 */
	public enum SizeRejection {
		AUTO, ON, OFF
	}

	private static final long PEN_GRACE_MILLIS = 500;

	private final float palmTouchMajorPx;
	private final PenFingerPolicy policy;
	private final SizeRejection sizeRejection;
	private boolean penCapable;
	private boolean penSeen;
	private final Set<Integer> dropped = new HashSet<>();
	private boolean penActive;
	private long penNearUntil;

	public PalmFilter(final float palmTouchMajorPx, final PenFingerPolicy policy) {
		this(palmTouchMajorPx, policy, SizeRejection.ON);
	}

	public PalmFilter(final float palmTouchMajorPx, final PenFingerPolicy policy, final SizeRejection sizeRejection) {
		this.palmTouchMajorPx = palmTouchMajorPx;
		this.policy = policy;
		this.sizeRejection = sizeRejection;
	}

	public void setPenCapable(final boolean penCapable) {
		this.penCapable = penCapable;
	}

	/** @return the contact size above which a finger is dropped as a palm right now, or infinity when none applies */
	public float effectiveLimitPx() {
		if ((this.sizeRejection == SizeRejection.OFF) || (this.palmTouchMajorPx <= 0)
				|| ((this.sizeRejection == SizeRejection.AUTO) && !this.penCapable && !this.penSeen)) {
			return Float.POSITIVE_INFINITY;
		}
		return this.palmTouchMajorPx;
	}

	/**
	 * @return whether the event should go on to gesture recognition and hover handling
	 */
	public boolean accept(final PointerEvent event) {
		final boolean pen = (event.tool == PointerEvent.Tool.STYLUS) || (event.tool == PointerEvent.Tool.ERASER);
		if (pen) {
			this.penSeen = true;
		}
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
					|| (event.touchMajor > effectiveLimitPx())) {
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
