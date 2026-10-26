package com.etheller.warsmash.input.touch;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongSupplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;

/**
 * Takes pointer events from a platform source on its own thread and replays them on the render thread: touches go
 * through the gesture recognizer to the RTS adapter, pen hover and wheel go straight to whichever screen currently
 * owns libGDX input.
 */
public final class TouchInputRouter {
	private static final String LOG_TAG = "WarsmashTouch";
	private static final int SHIFT_SLOT = 10;
	private static volatile TouchInputRouter installed;
	private int shiftPointerId = -1;

	private final Queue<PointerEvent> events = new ConcurrentLinkedQueue<>();
	private final GestureRecognizer recognizer;
	private final PalmFilter palmFilter;
	private final LongSupplier clock;
	private final boolean debugLog;

	/**
	 * @param clock the time base of the events, for example Android uptime
	 */
	public TouchInputRouter(final TouchSettings settings, final LongSupplier clock, final boolean debugLog) {
		this.recognizer = new GestureRecognizer(settings, new RtsTouchAdapter(settings, debugLog));
		this.palmFilter = new PalmFilter(settings.palmTouchMajorPx, settings.penFingerPolicy);
		this.clock = clock;
		this.debugLog = debugLog;
	}

	public static void install(final TouchInputRouter router) {
		installed = router;
		VirtualCursor.setTouchDevice(router != null);
	}

	public static boolean isActive() {
		return installed != null;
	}

	public static void drainInstalled() {
		final TouchInputRouter router = installed;
		if (router != null) {
			router.drain();
		}
	}

	public void post(final PointerEvent event) {
		this.events.add(event);
	}

	public void drain() {
		PointerEvent event;
		while ((event = this.events.poll()) != null) {
			dispatch(event);
		}
		this.recognizer.update(this.clock.getAsLong());
	}

	/**
	 * A finger that lands on the on-screen Shift holds it until it lifts and never reaches gesture recognition, so the
	 * other fingers keep giving ordinary taps and drags meanwhile.
	 */
	private boolean handleShiftPointer(final PointerEvent event, final InputProcessor processor) {
		if (event.pointerId == this.shiftPointerId) {
			if ((event.action == PointerEvent.Action.UP) || (event.action == PointerEvent.Action.CANCEL)) {
				this.shiftPointerId = -1;
				VirtualModifiers.setShiftHeld(false);
				if (this.debugLog) {
					Gdx.app.log(LOG_TAG, "shift released");
				}
			}
			return true;
		}
		if ((event.action == PointerEvent.Action.DOWN) && (this.shiftPointerId == -1)
				&& (processor instanceof TouchTarget) && (((TouchTarget) processor)
						.touchOverlaySlotAt(Math.round(event.x), Math.round(event.y)) == SHIFT_SLOT)) {
			this.shiftPointerId = event.pointerId;
			VirtualModifiers.setShiftHeld(true);
			if (this.debugLog) {
				Gdx.app.log(LOG_TAG, "shift held");
			}
			return true;
		}
		return false;
	}

	private void dispatch(final PointerEvent event) {
		if (!this.palmFilter.accept(event)) {
			if (this.debugLog && (event.action == PointerEvent.Action.DOWN)) {
				Gdx.app.log(LOG_TAG, "dropped as palm or finger near pen: " + event);
			}
			return;
		}
		if ((event.action == PointerEvent.Action.DOWN) || (event.action == PointerEvent.Action.HOVER_MOVE)) {
			VirtualCursor.setFingerInput(event.tool == PointerEvent.Tool.FINGER);
		}
		final InputProcessor processor = Gdx.input.getInputProcessor();
		switch (event.action) {
		case HOVER_MOVE:
			if (processor != null) {
				VirtualCursor.moveTo(Math.round(event.x), Math.round(event.y));
				processor.mouseMoved(Math.round(event.x), Math.round(event.y));
			}
			break;
		case HOVER_EXIT:
			if (processor != null) {
				// Far outside the screen, so unit highlights and tooltips under the pen go away.
				processor.mouseMoved(-100000, -100000);
			}
			break;
		case SCROLL:
			if (processor != null) {
				processor.scrolled(0, event.scroll);
			}
			break;
		default:
			if (handleShiftPointer(event, processor)) {
				break;
			}
			if (this.debugLog && (event.action != PointerEvent.Action.MOVE)) {
				Gdx.app.log(LOG_TAG, event.toString());
			}
			this.recognizer.onEvent(event);
			break;
		}
	}
}
