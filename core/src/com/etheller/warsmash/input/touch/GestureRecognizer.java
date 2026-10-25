package com.etheller.warsmash.input.touch;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns finger and pen pointer events into gestures: tap, long press, one finger drag, two finger pan and stepped
 * zoom, two finger tap. Time comes from the events and from {@link #update(long)}, so the recognizer runs the same
 * in tests as on a device. A long press is reported when its threshold passes, and two finger movement is judged
 * once all fingers of a frame have moved (judging after each single finger would see a distance that never
 * existed), which is why update must be called every frame after the frame's events.
 */
public final class GestureRecognizer {
	private enum State {
		IDLE, PENDING, LONG, DRAG, TWO_FINGER, PAN_REMAINING, IGNORE
	}

	private final TouchSettings settings;
	private final GestureListener listener;
	private final Map<Integer, float[]> pointers = new LinkedHashMap<>();

	private State state = State.IDLE;
	private int primaryId;
	private boolean pen;
	private boolean secondary;
	private float downX;
	private float downY;
	private long downTime;

	private long twoFingerStart;
	private float twoStartCentroidX;
	private float twoStartCentroidY;
	private float twoStartDistance;
	private boolean twoMoved;
	private boolean panStarted;
	private boolean zoomActive;
	private boolean twoFingerDirty;
	private int holdTapPointer = -1;
	private long holdTapDownTime;
	private float holdTapDownX;
	private float holdTapDownY;
	private float zoomStepDistance;
	private float remainingOffsetX;
	private float remainingOffsetY;

	public GestureRecognizer(final TouchSettings settings, final GestureListener listener) {
		this.settings = settings;
		this.listener = listener;
	}

	public void onEvent(final PointerEvent event) {
		switch (event.action) {
		case DOWN:
			down(event);
			break;
		case MOVE:
			move(event);
			break;
		case UP:
			up(event);
			break;
		case CANCEL:
			cancel();
			break;
		default:
			break;
		}
	}

	public void update(final long nowMillis) {
		if (this.twoFingerDirty && (this.state == State.TWO_FINGER)) {
			twoFingerMoved();
		}
		this.twoFingerDirty = false;
		if ((this.state == State.PENDING) && !this.pen
				&& ((nowMillis - this.downTime) >= this.settings.longPressMs)) {
			this.state = State.LONG;
			final float[] position = this.pointers.get(this.primaryId);
			this.listener.longPress(position[0], position[1]);
		}
	}

	private void down(final PointerEvent event) {
		switch (this.state) {
		case IDLE:
			this.pointers.clear();
			this.pointers.put(event.pointerId, new float[] { event.x, event.y });
			this.primaryId = event.pointerId;
			this.pen = (event.tool == PointerEvent.Tool.STYLUS) || (event.tool == PointerEvent.Tool.ERASER);
			this.secondary = this.pen && ((event.buttons & PointerEvent.PEN_BUTTON_MASK) != 0);
			this.downX = event.x;
			this.downY = event.y;
			this.downTime = event.timeMillis;
			this.state = State.PENDING;
			this.listener.pointerAt(event.x, event.y);
			break;
		case PENDING:
			if (!this.pen) {
				startTwoFinger(event);
			}
			break;
		case LONG:
			if (this.holdTapPointer == -1) {
				this.holdTapPointer = event.pointerId;
				this.holdTapDownTime = event.timeMillis;
				this.holdTapDownX = event.x;
				this.holdTapDownY = event.y;
			}
			break;
		case DRAG:
			if (!this.pen && ((event.timeMillis - this.downTime) <= this.settings.twoFingerWindowMs)) {
				this.listener.cancel();
				startTwoFinger(event);
			}
			break;
		default:
			break;
		}
	}

	private void startTwoFinger(final PointerEvent event) {
		this.pointers.put(event.pointerId, new float[] { event.x, event.y });
		this.state = State.TWO_FINGER;
		this.twoFingerStart = event.timeMillis;
		final float[] centroid = centroid();
		this.twoStartCentroidX = centroid[0];
		this.twoStartCentroidY = centroid[1];
		this.twoStartDistance = Math.max(1, distance());
		this.twoMoved = false;
		this.panStarted = false;
		this.zoomActive = false;
	}

	private void move(final PointerEvent event) {
		final float[] position = this.pointers.get(event.pointerId);
		if (position == null) {
			return;
		}
		position[0] = event.x;
		position[1] = event.y;
		switch (this.state) {
		case PENDING: {
			if (this.pen && ((event.buttons & PointerEvent.PEN_BUTTON_MASK) != 0)) {
				this.secondary = true;
			}
			final float slop = this.pen ? this.settings.penSlopPx : this.settings.tapSlopPx;
			if (this.secondary && (Math.hypot(event.x - this.downX, event.y - this.downY) > slop)) {
				this.state = State.PAN_REMAINING;
				this.remainingOffsetX = 0;
				this.remainingOffsetY = 0;
				this.listener.panStart(this.downX, this.downY);
				this.listener.pan(event.x, event.y);
			}
			else if (Math.hypot(event.x - this.downX, event.y - this.downY) > slop) {
				this.state = State.DRAG;
				this.listener.dragStart(this.downX, this.downY, this.pen);
				this.listener.drag(event.x, event.y);
			}
			else {
				this.listener.pointerAt(event.x, event.y);
			}
			break;
		}
		case DRAG:
			this.listener.drag(event.x, event.y);
			break;
		case TWO_FINGER:
			this.twoFingerDirty = true;
			break;
		case PAN_REMAINING:
			this.listener.pan(event.x + this.remainingOffsetX, event.y + this.remainingOffsetY);
			break;
		default:
			break;
		}
	}

	private void twoFingerMoved() {
		final float[] centroid = centroid();
		final float shift = (float) Math.hypot(centroid[0] - this.twoStartCentroidX,
				centroid[1] - this.twoStartCentroidY);
		final float distance = Math.max(1, distance());
		if (!this.twoMoved && ((shift > this.settings.tapSlopPx)
				|| (Math.abs(distance - this.twoStartDistance) > this.settings.tapSlopPx))) {
			this.twoMoved = true;
		}
		if (!this.twoMoved) {
			return;
		}
		if (!this.panStarted) {
			this.panStarted = true;
			this.listener.panStart(this.twoStartCentroidX, this.twoStartCentroidY);
		}
		this.listener.pan(centroid[0], centroid[1]);
		if (!this.zoomActive) {
			final float ratio = distance / this.twoStartDistance;
			final boolean pastStart = (ratio >= this.settings.pinchStartRatio)
					|| (ratio <= (1 / this.settings.pinchStartRatio));
			if (pastStart && (Math.abs(distance - this.twoStartDistance) > shift)) {
				this.zoomActive = true;
				this.zoomStepDistance = distance;
				this.listener.zoomStep(ratio > 1 ? 1 : -1);
			}
			return;
		}
		while ((distance / this.zoomStepDistance) >= this.settings.pinchStepRatio) {
			this.zoomStepDistance *= this.settings.pinchStepRatio;
			this.listener.zoomStep(1);
		}
		while ((distance / this.zoomStepDistance) <= (1 / this.settings.pinchStepRatio)) {
			this.zoomStepDistance /= this.settings.pinchStepRatio;
			this.listener.zoomStep(-1);
		}
	}

	private void up(final PointerEvent event) {
		final float[] position = this.pointers.get(event.pointerId);
		if (position != null) {
			position[0] = event.x;
			position[1] = event.y;
		}
		switch (this.state) {
		case PENDING:
			if (event.pointerId == this.primaryId) {
				if (this.secondary) {
					this.listener.secondaryTap(event.x, event.y);
				}
				else {
					this.listener.tap(event.x, event.y, this.pen);
				}
				this.state = State.IDLE;
			}
			break;
		case LONG:
			if (event.pointerId == this.primaryId) {
				this.listener.longPressEnd(event.x, event.y);
				this.holdTapPointer = -1;
				this.state = this.pointers.size() > 1 ? State.IGNORE : State.IDLE;
			}
			else if (event.pointerId == this.holdTapPointer) {
				if (((event.timeMillis - this.holdTapDownTime) <= this.settings.twoFingerTapMs) && (Math.hypot(
						event.x - this.holdTapDownX, event.y - this.holdTapDownY) <= this.settings.tapSlopPx)) {
					final float[] held = this.pointers.get(this.primaryId);
					this.listener.holdTap(held[0], held[1]);
				}
				this.holdTapPointer = -1;
			}
			break;
		case DRAG:
			if (event.pointerId == this.primaryId) {
				this.listener.dragEnd(event.x, event.y);
				this.state = State.IDLE;
			}
			break;
		case TWO_FINGER: {
			if (position == null) {
				break;
			}
			if (this.twoFingerDirty) {
				twoFingerMoved();
				this.twoFingerDirty = false;
			}
			final float[] centroid = centroid();
			this.pointers.remove(event.pointerId);
			if (!this.twoMoved && ((event.timeMillis - this.twoFingerStart) <= this.settings.twoFingerTapMs)) {
				this.listener.twoFingerTap(centroid[0], centroid[1]);
				this.state = State.IGNORE;
			}
			else if (this.panStarted) {
				final float[] remaining = this.pointers.values().iterator().next();
				this.remainingOffsetX = centroid[0] - remaining[0];
				this.remainingOffsetY = centroid[1] - remaining[1];
				this.state = State.PAN_REMAINING;
			}
			else {
				this.state = State.IGNORE;
			}
			break;
		}
		case PAN_REMAINING:
			if (position != null) {
				this.listener.panEnd(event.x + this.remainingOffsetX, event.y + this.remainingOffsetY);
				this.state = State.IDLE;
			}
			break;
		default:
			break;
		}
		this.pointers.remove(event.pointerId);
		if ((this.state == State.IGNORE) && this.pointers.isEmpty()) {
			this.state = State.IDLE;
		}
	}

	private void cancel() {
		if ((this.state != State.IDLE) && (this.state != State.IGNORE)) {
			this.listener.cancel();
		}
		this.pointers.clear();
		this.state = State.IDLE;
	}

	private float[] centroid() {
		float x = 0;
		float y = 0;
		int count = 0;
		for (final float[] position : this.pointers.values()) {
			x += position[0];
			y += position[1];
			count++;
			if (count == 2) {
				break;
			}
		}
		return new float[] { x / count, y / count };
	}

	private float distance() {
		final float[][] two = new float[2][];
		int count = 0;
		for (final float[] position : this.pointers.values()) {
			two[count++] = position;
			if (count == 2) {
				break;
			}
		}
		if (count < 2) {
			return 0;
		}
		return (float) Math.hypot(two[0][0] - two[1][0], two[0][1] - two[1][1]);
	}
}
