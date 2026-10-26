package com.etheller.warsmash.input.touch;

import com.badlogic.gdx.Gdx;

/**
 * Pointer position for code that polls the cursor every frame. Touch events bypass libGDX input, so its own position
 * would stay where the last mouse event left it; while a touch router is active this holds the last finger or pen
 * position instead.
 */
public final class VirtualCursor {
	private static volatile boolean active;
	private static volatile int x;
	private static volatile int y;
	private static volatile boolean fingerInput;
	private static volatile boolean touchDevice;

	private VirtualCursor() {
	}

	static void setFingerInput(final boolean finger) {
		fingerInput = finger;
	}

	/**
	 * @return whether the last pointer to go down or hover was a finger; a cursor under a finger only hides what it
	 *         points at, so it is not drawn then
	 */
	public static boolean isFingerInput() {
		return fingerInput;
	}

	static void setTouchDevice(final boolean touch) {
		touchDevice = touch;
	}

	/**
	 * @return whether the cursor is left undrawn: a touch device is played with fingers, and a cursor would only sit
	 *         where the last pointer was, pen included
	 */
	public static boolean isCursorHidden() {
		return touchDevice || fingerInput;
	}

	static void moveTo(final int screenX, final int screenY) {
		x = screenX;
		y = screenY;
		active = true;
	}

	public static int getX() {
		return active ? x : Gdx.input.getX();
	}

	public static int getY() {
		return active ? y : Gdx.input.getY();
	}
}
