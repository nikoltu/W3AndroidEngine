package com.etheller.warsmash.input.touch;

/**
 * The on-screen Shift for touch play. One tap arms it for the next order or selection, a second tap within the
 * double tap time locks it until tapped again.
 */
public final class VirtualModifiers {
	public enum ShiftState {
		OFF, ONCE, LOCKED
	}

	private static final long DOUBLE_TAP_MILLIS = 400;
	private static volatile ShiftState shift = ShiftState.OFF;
	private static long lastShiftTapMillis;

	private VirtualModifiers() {
	}

	public static boolean isShiftDown() {
		return shift != ShiftState.OFF;
	}

	public static ShiftState getShiftState() {
		return shift;
	}

	static void tapShift(final long nowMillis) {
		final boolean doubleTap = (nowMillis - lastShiftTapMillis) < DOUBLE_TAP_MILLIS;
		lastShiftTapMillis = nowMillis;
		switch (shift) {
		case OFF:
			shift = ShiftState.ONCE;
			break;
		case ONCE:
			shift = doubleTap ? ShiftState.LOCKED : ShiftState.OFF;
			break;
		case LOCKED:
			shift = ShiftState.OFF;
			break;
		}
	}

	/** Called after an order or selection, which uses up a Shift armed for one action. */
	static void consumeOnce() {
		if (shift == ShiftState.ONCE) {
			shift = ShiftState.OFF;
		}
	}
}
