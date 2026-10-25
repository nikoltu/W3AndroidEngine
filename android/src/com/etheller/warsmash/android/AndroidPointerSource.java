package com.etheller.warsmash.android;

import android.view.InputDevice;
import android.view.MotionEvent;

import com.etheller.warsmash.input.touch.PointerEvent;
import com.etheller.warsmash.input.touch.TouchInputRouter;

/**
 * Turns finger and pen motion events into pointer events for the touch router. Mouse events are left to libGDX, so
 * a mouse or touchpad keeps its desktop behavior.
 */
final class AndroidPointerSource {
	private final TouchInputRouter router;

	AndroidPointerSource(final TouchInputRouter router) {
		this.router = router;
	}

	/**
	 * @return whether the event was taken; events that are not taken go on to libGDX.
	 */
	boolean onTouchEvent(final MotionEvent event) {
		if (event.getToolType(event.getActionIndex()) == MotionEvent.TOOL_TYPE_MOUSE) {
			return false;
		}
		final int masked = event.getActionMasked();
		switch (masked) {
		case MotionEvent.ACTION_DOWN:
		case MotionEvent.ACTION_POINTER_DOWN:
			post(event, event.getActionIndex(), PointerEvent.Action.DOWN);
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_POINTER_UP:
			post(event, event.getActionIndex(),
					((event.getFlags() & MotionEvent.FLAG_CANCELED) != 0) ? PointerEvent.Action.CANCEL
							: PointerEvent.Action.UP);
			break;
		case MotionEvent.ACTION_MOVE:
			for (int i = 0; i < event.getPointerCount(); i++) {
				post(event, i, PointerEvent.Action.MOVE);
			}
			break;
		case MotionEvent.ACTION_CANCEL:
			post(event, event.getActionIndex(), PointerEvent.Action.CANCEL);
			break;
		default:
			return false;
		}
		return true;
	}

	boolean onGenericMotionEvent(final MotionEvent event) {
		if (((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) == 0)
				|| (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE)) {
			return false;
		}
		switch (event.getActionMasked()) {
		case MotionEvent.ACTION_HOVER_ENTER:
		case MotionEvent.ACTION_HOVER_MOVE:
			post(event, 0, PointerEvent.Action.HOVER_MOVE);
			return true;
		case MotionEvent.ACTION_HOVER_EXIT:
			post(event, 0, PointerEvent.Action.HOVER_EXIT);
			return true;
		default:
			return false;
		}
	}

	private void post(final MotionEvent event, final int index, final PointerEvent.Action action) {
		this.router.post(new PointerEvent(event.getPointerId(index), tool(event.getToolType(index)), action,
				event.getX(index), event.getY(index), event.getButtonState(), event.getPressure(index),
				event.getTouchMajor(index), 0, event.getEventTime()));
	}

	private static PointerEvent.Tool tool(final int toolType) {
		switch (toolType) {
		case MotionEvent.TOOL_TYPE_FINGER:
			return PointerEvent.Tool.FINGER;
		case MotionEvent.TOOL_TYPE_STYLUS:
			return PointerEvent.Tool.STYLUS;
		case MotionEvent.TOOL_TYPE_ERASER:
			return PointerEvent.Tool.ERASER;
		case MotionEvent.TOOL_TYPE_MOUSE:
			return PointerEvent.Tool.MOUSE;
		default:
			return PointerEvent.Tool.UNKNOWN;
		}
	}
}
