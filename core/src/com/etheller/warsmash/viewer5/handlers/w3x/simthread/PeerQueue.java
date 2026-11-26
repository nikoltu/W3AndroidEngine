package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongSupplier;

import com.badlogic.gdx.utils.IntMap;

/**
 * The render side's list of world objects the simulation made whose peers are not built yet, and the rules for when
 * to build them. The simulation announces an object the moment it makes it ({@link #announce}); the render thread
 * builds its peer
 * <ul>
 * <li>ahead of time, at the start of a frame, as many as fit a time budget ({@link #beginFrame}), so a wave of units
 * is spread over the frames before its creation events come due and no frame carries all of it,</li>
 * <li>when the creation event comes due ({@link #present}), for those still waiting; one that has waited a frame by
 * then is counted as late,</li>
 * <li>or at once for everything ({@link #flush}), before the render thread does something that needs the peers.</li>
 * </ul>
 * Peers are built in the order the objects were made, whichever rule asks: asking for one builds those before it.
 * A handle given out again names several waiting objects one after the other; asking for the handle means the
 * earliest of them, the one whose creation event comes first.
 * <p>
 * {@link #announce} may be called from any thread; everything else is the render thread's.
 */
public final class PeerQueue {
	/** Builds the peer of an announced object. Render thread. */
	public interface Builder {
		void build(int handle, Object data);

		/** Whether what the peer is built from is loaded, so that building it does not wait for a file. */
		default boolean ready(final Object data) {
			return true;
		}

		/** A peer is built with what it needs not loaded yet. */
		default void builtCold(final Object data) {
		}

	}

	private static final class Item {
		private final int handle;
		private final Object data;
		private final long announcedFrame;
		/** The next waiting object with the same handle (made after this one), or null. */
		private Item laterSameHandle;

		Item(final int handle, final Object data, final long announcedFrame) {
			this.handle = handle;
			this.data = data;
			this.announcedFrame = announcedFrame;
		}
	}

	private final Builder builder;
	private final LongSupplier nanos;
	private final ConcurrentLinkedQueue<Item> incoming = new ConcurrentLinkedQueue<>();
	private final ArrayDeque<Item> waiting = new ArrayDeque<>();
	/** The earliest waiting object of each handle; the later ones hang off it. */
	private final IntMap<Item> waitingByHandle = new IntMap<>();
	private volatile long frame;

	private long builtEarly;
	private long builtAtEvent;
	private long late;
	private long cold;
	private long maxWaitFrames;
	private long totalBuildNanos;
	private long frameBuildNanos;
	private long maxFrameBuildNanos;
	private long builtThisFrame;
	private long maxBuiltPerFrame;

	public PeerQueue(final Builder builder) {
		this(builder, System::nanoTime);
	}

	public PeerQueue(final Builder builder, final LongSupplier nanos) {
		this.builder = builder;
		this.nanos = nanos;
	}

	/** Any thread: the object with this handle was made and its peer has to be built. */
	public void announce(final int handle, final Object data) {
		this.incoming.add(new Item(handle, data, this.frame));
	}

	private void takeIncoming() {
		Item item;
		while ((item = this.incoming.poll()) != null) {
			this.waiting.add(item);
			Item earlier = this.waitingByHandle.get(item.handle);
			if (earlier == null) {
				this.waitingByHandle.put(item.handle, item);
			}
			else {
				while (earlier.laterSameHandle != null) {
					earlier = earlier.laterSameHandle;
				}
				earlier.laterSameHandle = item;
			}
		}
	}

	private long buildHead() {
		final Item item = this.waiting.poll();
		if (!this.builder.ready(item.data)) {
			this.builder.builtCold(item.data);
			this.cold++;
		}
		// peers are built in the order the objects were made, so the head is the earliest of its handle
		if (item.laterSameHandle != null) {
			this.waitingByHandle.put(item.handle, item.laterSameHandle);
			item.laterSameHandle = null;
		}
		else {
			this.waitingByHandle.remove(item.handle);
		}
		this.maxWaitFrames = Math.max(this.maxWaitFrames, this.frame - item.announcedFrame);
		final long start = this.nanos.getAsLong();
		this.builder.build(item.handle, item.data);
		final long spent = this.nanos.getAsLong() - start;
		this.totalBuildNanos += spent;
		this.frameBuildNanos += spent;
		this.builtThisFrame++;
		return spent;
	}

	/**
	 * Start of a render frame: builds waiting peers in order until the budget is spent (always at least one while
	 * any waits, and every one that has waited {@code maxWaitFrames} frames whatever the budget).
	 */
	public void beginFrame(final long budgetNanos, final long maxWaitFrames) {
		this.frame++;
		this.maxFrameBuildNanos = Math.max(this.maxFrameBuildNanos, this.frameBuildNanos);
		this.maxBuiltPerFrame = Math.max(this.maxBuiltPerFrame, this.builtThisFrame);
		this.frameBuildNanos = 0;
		this.builtThisFrame = 0;
		takeIncoming();
		long spent = 0;
		boolean any = false;
		while (!this.waiting.isEmpty()) {
			final boolean overdue = (this.frame - this.waiting.peek().announcedFrame) >= maxWaitFrames;
			if (!overdue && any && (spent >= budgetNanos)) {
				break;
			}
			spent += buildHead();
			any = true;
			this.builtEarly++;
		}
	}

	/** Builds every waiting peer. */
	public void flush() {
		takeIncoming();
		while (!this.waiting.isEmpty()) {
			buildHead();
			this.builtEarly++;
		}
	}

	/**
	 * The creation event of an object came due: its peer is there when this returns (the earliest waiting object of
	 * the handle). Does nothing for a handle that is built already.
	 *
	 * @return true when the peer had to be built now and had waited a frame or more, so it shows later than it should
	 */
	public boolean present(final int handle) {
		takeIncoming();
		final Item item = this.waitingByHandle.get(handle);
		if (item == null) {
			return false;
		}
		final boolean wasLate = (item.announcedFrame < this.frame) || !this.builder.ready(item.data);
		while (!this.waiting.isEmpty()) {
			final boolean target = this.waiting.peek() == item;
			buildHead();
			if (target) {
				this.builtAtEvent++;
				break;
			}
			this.builtEarly++;
		}
		if (wasLate) {
			this.late++;
		}
		return wasLate;
	}

	/** Whether a waiting peer needs something that is still loading (looks at the first few that wait). */
	public boolean waitingForFiles() {
		takeIncoming();
		int looked = 0;
		for (final Item item : this.waiting) {
			if (!this.builder.ready(item.data)) {
				return true;
			}
			if (++looked >= 64) {
				break;
			}
		}
		return false;
	}

	/** Whether the object is announced and has no peer yet. */
	public boolean isWaiting(final int handle) {
		takeIncoming();
		return this.waitingByHandle.containsKey(handle);
	}

	public int waitingCount() {
		takeIncoming();
		return this.waiting.size();
	}

	public long builtEarly() {
		return this.builtEarly;
	}

	public long builtAtEvent() {
		return this.builtAtEvent;
	}

	/** Peers that were built before the files they need were loaded. */
	public long cold() {
		return this.cold;
	}

	public long late() {
		return this.late;
	}

	public long maxWaitFrames() {
		return this.maxWaitFrames;
	}

	public long totalBuildNanos() {
		return this.totalBuildNanos;
	}

	/** The most time the peers built in one render frame took (counted when the next frame begins). */
	public long maxFrameBuildNanos() {
		return Math.max(this.maxFrameBuildNanos, this.frameBuildNanos);
	}

	public long maxBuiltPerFrame() {
		return Math.max(this.maxBuiltPerFrame, this.builtThisFrame);
	}

	public String summary() {
		return "peers built=" + (this.builtEarly + this.builtAtEvent) + " early=" + this.builtEarly + " atEvent="
				+ this.builtAtEvent + " late=" + this.late + " cold=" + this.cold + " maxWaitFrames=" + this.maxWaitFrames + " buildMs="
				+ (this.totalBuildNanos / 1_000_000L) + " maxFrameBuildMs=" + (maxFrameBuildNanos() / 1_000_000L)
				+ " maxPerFrame=" + maxBuiltPerFrame();
	}
}
