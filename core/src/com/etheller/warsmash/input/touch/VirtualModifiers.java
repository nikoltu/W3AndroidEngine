package com.etheller.warsmash.input.touch;

/**
 * The on-screen Shift for touch play. It is held like the key: down while a finger rests on it, so orders and
 * selections given with other fingers meanwhile are queued or added.
 */
public final class VirtualModifiers {
	public enum ShiftState {
		OFF, HELD
	}

	private static volatile ShiftState shift = ShiftState.OFF;

	private VirtualModifiers() {
	}

	public static boolean isShiftDown() {
		return shift == ShiftState.HELD;
	}

	public static ShiftState getShiftState() {
		return shift;
	}

	static void setShiftHeld(final boolean held) {
		shift = held ? ShiftState.HELD : ShiftState.OFF;
	}
}
