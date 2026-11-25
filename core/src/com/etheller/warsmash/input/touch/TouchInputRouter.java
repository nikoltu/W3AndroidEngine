package com.etheller.warsmash.input.touch;

import com.etheller.warsmash.util.InputLatencyProbe;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongSupplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;

/**
 * Takes pointer events from a platform source on its own thread and replays them on the render thread, as the chosen
 * control scheme asks. Under the touch scheme touches go through the gesture recognizer to the RTS adapter, and pen
 * hover and wheel go straight to whichever screen currently owns libGDX input. Under the mouse and keyboard scheme
 * the platform leaves fingers to libGDX as mouse clicks, and only the pen arrives here, as a mouse with hover. The
 * pen's side button is Shift under both.
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
	private final RtsTouchAdapter adapter;
	private final DoubleTapMapper doubleTap;
	private final PenMouse penMouse;
	private volatile ControlScheme scheme;
	private volatile TouchStyle style;
	private volatile PenButton penButton;
	private volatile boolean resetRequested;

	/**
	 * @param clock the time base of the events, for example Android uptime
	 */
	public TouchInputRouter(final TouchSettings settings, final LongSupplier clock, final boolean debugLog) {
		this.adapter = new RtsTouchAdapter(settings, debugLog);
		this.doubleTap = new DoubleTapMapper(this.adapter, settings, TouchInputRouter::currentTarget, clock,
				debugLog);
		this.recognizer = new GestureRecognizer(settings, this.doubleTap);
		this.penMouse = new PenMouse(settings);
		this.scheme = settings.controlScheme;
		this.style = settings.touchStyle;
		this.penButton = settings.penButton;
		this.penMouse.setPenButton(settings.penButton);
		this.adapter.setLongPressOrders(settings.touchStyle == TouchStyle.TAP_HOLD);
		this.palmFilter = new PalmFilter(settings.palmTouchMajorPx, settings.penFingerPolicy, settings.palmRejection);
		this.clock = clock;
		this.debugLog = debugLog;
	}

	public void setPenCapable(final boolean penCapable) {
		this.palmFilter.setPenCapable(penCapable);
	}

	public static void install(final TouchInputRouter router) {
		installed = router;
		VirtualCursor.setTouchDevice((router != null) && (router.scheme == ControlScheme.TOUCH));
	}

	/**
	 * @return whether the touch scheme is in force, which is what the interface asks to hide keyboard hints and to
	 *         draw no cursor
	 */
	public static boolean isActive() {
		final TouchInputRouter router = installed;
		return (router != null) && (router.scheme == ControlScheme.TOUCH);
	}

	public static TouchInputRouter installedRouter() {
		return installed;
	}

	public ControlScheme scheme() {
		return this.scheme;
	}

	/** Switches the scheme at once, also in the middle of a game; whatever gesture is in progress is dropped. */
	public void setScheme(final ControlScheme scheme) {
		if (this.scheme != scheme) {
			this.scheme = scheme;
			this.resetRequested = true;
			if (installed == this) {
				VirtualCursor.setTouchDevice(scheme == ControlScheme.TOUCH);
			}
		}
	}

	/** @param style how orders are given: by holding, or by a double tap that also takes over the pen */
	public void setStyle(final TouchStyle style) {
		this.style = style;
		this.doubleTap.setStyle(style);
		this.adapter.setLongPressOrders(style == TouchStyle.TAP_HOLD);
	}

	public void setPenButton(final PenButton penButton) {
		this.penButton = penButton;
		this.recognizer.setPenButton(penButton);
		this.penMouse.setPenButton(penButton);
	}

	/** A mouse or touchpad moved: the cursor is the system's again, not where the pen or finger last was. */
	public static void mouseUsed() {
		VirtualCursor.useSystemCursor();
	}

	private static TouchTarget currentTarget() {
		final InputProcessor processor = Gdx.input.getInputProcessor();
		return processor instanceof TouchTarget ? (TouchTarget) processor : null;
	}

	public static void drainInstalled() {
		final TouchInputRouter router = installed;
		if (router != null) {
			router.drain();
		}
	}

	public void post(final PointerEvent event) {
		if (InputLatencyProbe.ENABLED) {
			InputLatencyProbe.input();
		}
		this.events.add(event);
	}

	public void drain() {
		if (this.resetRequested) {
			this.resetRequested = false;
			this.events.clear();
			this.recognizer.onEvent(new PointerEvent(0, PointerEvent.Tool.UNKNOWN, PointerEvent.Action.CANCEL, 0, 0, 0,
					0, 0, 0, this.clock.getAsLong()));
			VirtualModifiers.setPenButtonHeld(false, this.penButton);
		}
		PointerEvent event;
		while ((event = this.events.poll()) != null) {
			dispatch(event);
		}
		final long now = this.clock.getAsLong();
		this.recognizer.update(now);
		this.doubleTap.update(now);
		this.adapter.update();
		this.penMouse.update(now);
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

	private static boolean isPen(final PointerEvent event) {
		return (event.tool == PointerEvent.Tool.STYLUS) || (event.tool == PointerEvent.Tool.ERASER);
	}

	static void followPenButton(final PointerEvent event, final PenButton mode) {
		switch (event.action) {
		case DOWN:
		case MOVE:
		case HOVER_MOVE:
		case BUTTONS:
			VirtualModifiers.setPenButtonHeld((event.buttons & PointerEvent.PEN_BUTTON_MASK) != 0, mode);
			break;
		default:
			VirtualModifiers.setPenButtonHeld(false, mode);
			break;
		}
	}

	private void dispatchPenMouse(final PointerEvent event) {
		final InputProcessor processor = Gdx.input.getInputProcessor();
		VirtualCursor.setFingerInput(false);
		switch (event.action) {
		case HOVER_MOVE:
			if (processor != null) {
				this.penMouse.hover(processor, event.x, event.y);
			}
			break;
		case HOVER_EXIT:
			if (processor != null) {
				processor.mouseMoved(-100000, -100000);
			}
			break;
		default:
			this.penMouse.handle(event, processor);
			break;
		}
	}

	private void dispatch(final PointerEvent event) {
		final boolean pen = isPen(event);
		final boolean lifting = (event.action == PointerEvent.Action.UP) || (event.action == PointerEvent.Action.CANCEL)
				|| (event.action == PointerEvent.Action.HOVER_EXIT);
		if (pen && !lifting) {
			followPenButton(event, this.penButton);
		}
		if (event.action != PointerEvent.Action.BUTTONS) {
			route(event);
		}
		// A tap is carried out when the tip lifts, so the button still counts then and stops only after it.
		if (pen && lifting) {
			followPenButton(event, this.penButton);
		}
	}

	private void route(final PointerEvent event) {
		if (this.scheme == ControlScheme.MOUSE_KEYBOARD) {
			if (!isPen(event)) {
				return;
			}
			// With double tap orders the pen needs the gesture path, whose tap delay a plain mouse click lacks.
			if (this.style == TouchStyle.TAP_HOLD) {
				dispatchPenMouse(event);
				return;
			}
		}
		else if (!this.palmFilter.accept(event)) {
			if (this.debugLog && (event.action == PointerEvent.Action.DOWN)) {
				Gdx.app.log(LOG_TAG, "dropped as palm or finger near pen (limit " + this.palmFilter.effectiveLimitPx() + "): " + event);
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
