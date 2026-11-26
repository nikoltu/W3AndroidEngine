package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.ArrayDeque;
import java.util.function.IntSupplier;

/**
 * Collects the events the simulation records into the frame that the step in progress publishes. Events made outside
 * a step (while a map starts, in a command applied between steps) join the next frame to be published.
 * <p>
 * While every frame of the ring is still held by the render side, events wait here and go first into the next frame
 * the writer begins, so a command or a script never fails halfway for want of a frame; the simulation thread keeps
 * from starting work while the ring has too little room, so they wait only briefly.
 * <p>
 * Simulation thread only; the ring has one writer.
 */
public final class FrameEventSink implements RenderEventSink {
	private final SimFrameRing ring;
	private final RenderEventPool pool;
	private final IntSupplier tick;
	private int lastTick = Integer.MIN_VALUE;
	private int order;
	/** Events made while the ring had no frame to write, oldest first. */
	private final ArrayDeque<RenderEvent> waiting = new ArrayDeque<>();

	public FrameEventSink(final SimFrameRing ring, final RenderEventPool pool, final IntSupplier tick) {
		this.ring = ring;
		this.pool = pool;
		this.tick = tick;
		ring.setOnBeginWrite(this::takeWaiting);
	}

	private void takeWaiting(final SimFrame frame) {
		RenderEvent event;
		while ((event = this.waiting.pollFirst()) != null) {
			frame.addEvent(event);
		}
	}

	@Override
	public RenderEvent begin(final int code) {
		final int now = this.tick.getAsInt();
		if (now != this.lastTick) {
			this.lastTick = now;
			this.order = 0;
		}
		final RenderEvent event = this.pool.obtain();
		event.code = code;
		event.tick = now;
		event.order = this.order++;
		return event;
	}

	@Override
	public void emit(final RenderEvent event) {
		final SimFrame frame = this.ring.beginWrite();
		if (frame == null) {
			this.waiting.addLast(event);
			return;
		}
		frame.addEvent(event);
	}

	/** True when events wait in the frame being built, so they are not lost if no further step publishes it. */
	public boolean hasPending() {
		if (!this.waiting.isEmpty()) {
			return true;
		}
		final SimFrame frame = this.ring.beginWrite();
		return (frame != null) && (frame.eventCount() > 0);
	}

	/** Events that found the ring full and wait for the next frame. */
	public int waitingCount() {
		return this.waiting.size();
	}
}
