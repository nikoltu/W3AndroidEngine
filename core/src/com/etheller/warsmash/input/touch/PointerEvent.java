package com.etheller.warsmash.input.touch;

/**
 * One touch, pen or hover sample from a platform pointer source, in screen pixels with the origin at the top left,
 * like libGDX input coordinates.
 */
public final class PointerEvent {
	public enum Action {
		DOWN, MOVE, UP, CANCEL, HOVER_MOVE, HOVER_EXIT, SCROLL
	}

	public enum Tool {
		FINGER, STYLUS, ERASER, MOUSE, UNKNOWN
	}

	/** Button bits as in Android MotionEvent: secondary (2) and stylus primary (32) both mean the pen button. */
	public static final int PEN_BUTTON_MASK = 2 | 32;

	public final int pointerId;
	public final Tool tool;
	public final Action action;
	public final float x;
	public final float y;
	public final int buttons;
	public final float pressure;
	public final float touchMajor;
	public final float scroll;
	public final long timeMillis;

	public PointerEvent(final int pointerId, final Tool tool, final Action action, final float x, final float y,
			final int buttons, final float pressure, final float touchMajor, final float scroll,
			final long timeMillis) {
		this.pointerId = pointerId;
		this.tool = tool;
		this.action = action;
		this.x = x;
		this.y = y;
		this.buttons = buttons;
		this.pressure = pressure;
		this.touchMajor = touchMajor;
		this.scroll = scroll;
		this.timeMillis = timeMillis;
	}

	@Override
	public String toString() {
		return this.action + " " + this.tool + " #" + this.pointerId + " " + (int) this.x + "," + (int) this.y
				+ " buttons=" + this.buttons;
	}
}
