package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.Arrays;

/**
 * One request from the interface (or the network) to the simulation, carried by an {@link OrderInbox}. The payload is
 * the command in the existing encoded form (the same bytes LAN and replay use), so nothing new has to be serialized.
 * Instances are pooled by the inbox. The producer fills a message obtained from the inbox and submits it; the
 * simulation thread reads it while draining and must not keep it afterwards.
 */
public final class OrderMessage {
	/** An encoded player command: unit orders, selection, dialog clicks, chat, cheats, frame events. */
	public static final int COMMAND = 0;
	/** Pause or resume (payload: one byte, 1 = pause). */
	public static final int PAUSE = 1;
	/** A save request (payload: the encoded interface state captured by the render thread). */
	public static final int SAVE = 2;
	/** Speed change or fast-forward target. */
	public static final int SPEED = 3;
	/** A replay's vision (playerIndex: the player, or one of the writer's vision constants; payload: one byte, 1 = fog shown). */
	public static final int VISION = 4;

	public int kind;
	public int playerIndex;
	/** Position in the order the producer submitted its messages; used to keep each producer's order visible in tests and logs. */
	public long submitSequence;
	/** Wall-clock nanoseconds when submitted (input latency measurement). */
	public long submitNanos;
	/** Filled by the inbox when drained: the simulation tick the message is applied in (what a replay records). */
	public int appliedTick;
	public byte[] payload = new byte[32];
	public int payloadLength;

	public void setPayload(final byte[] source, final int offset, final int length) {
		if (payload.length < length) {
			payload = new byte[Math.max(length, payload.length * 2)];
		}
		System.arraycopy(source, offset, payload, 0, length);
		payloadLength = length;
	}

	public byte[] payloadCopy() {
		return Arrays.copyOf(payload, payloadLength);
	}

	void clear() {
		kind = 0;
		playerIndex = 0;
		submitSequence = 0;
		submitNanos = 0;
		appliedTick = 0;
		payloadLength = 0;
	}
}
