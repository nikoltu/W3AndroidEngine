package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

/**
 * Fixed ring of preallocated {@link SimFrame}s between exactly one writer and exactly one reader, without locks.
 * <p>
 * Writer (simulation thread): {@link #beginWrite()}, fill the frame, {@link #publish()}. When the ring is full,
 * {@code beginWrite} returns null and the writer waits, so the reader is never overtaken. Reader (render thread):
 * {@link #get(long)} / {@link #latest()} frames by sequence number, and {@link #release(long)} the ones it no longer
 * needs. A frame handed to the reader stays untouched until released. {@link #clear()} is for
 * moments when neither thread is running (load, restart).
 * <p>
 * Visibility comes from two volatile counters: the writer fills a frame and then raises {@code published}; the reader
 * releases a frame by raising {@code released}, after which the writer may reuse it.
 */
public final class SimFrameRing {
	public static final int DEFAULT_CAPACITY = 16;

	private final SimFrame[] frames;
	private final RenderEventPool pool;
	private volatile long published;
	private volatile long released;
	/** Writer only: the frame handed out by beginWrite and not yet published. */
	private SimFrame writing;
	/** Writer only: told of each frame beginWrite starts, before anything is written into it. */
	private java.util.function.Consumer<SimFrame> onBegin;

	public SimFrameRing(final int capacity, final RenderEventPool pool) {
		if (capacity < 3) {
			throw new IllegalArgumentException("A ring needs room for the frame being written and two to interpolate");
		}
		frames = new SimFrame[capacity];
		for (int i = 0; i < capacity; i++) {
			frames[i] = new SimFrame();
		}
		this.pool = pool;
	}

	public int capacity() {
		return frames.length;
	}

	/**
	 * Writer. The next frame, reset and ready to fill, or null while every slot is still held by the reader. Calling it
	 * again before {@link #publish()} returns the same frame, untouched.
	 */
	public SimFrame beginWrite() {
		if (writing != null) {
			return writing;
		}
		final long sequence = published;
		if ((sequence - released) >= frames.length) {
			return null;
		}
		final SimFrame frame = frames[(int) (sequence % frames.length)];
		frame.reset(pool);
		frame.sequence = sequence;
		writing = frame;
		if (onBegin != null) {
			onBegin.accept(frame);
		}
		return frame;
	}

	/** Writer: what is to go first into every frame begun from now on (events that found the ring full). */
	public void setOnBeginWrite(final java.util.function.Consumer<SimFrame> onBegin) {
		this.onBegin = onBegin;
	}

	/** Writer. Makes the frame from {@link #beginWrite()} visible to the reader. */
	public void publish() {
		if (writing == null) {
			throw new IllegalStateException("publish without beginWrite");
		}
		writing = null;
		published = published + 1;
	}

	/** Frames published so far; the newest has sequence {@code publishedCount() - 1}. */
	public long publishedCount() {
		return published;
	}

	/** Frames below this sequence were released and are gone. */
	public long oldestHeld() {
		return released;
	}

	/** Reader. The frame with this sequence, or null if it is not published yet or was already released. */
	public SimFrame get(final long sequence) {
		if ((sequence < released) || (sequence >= published)) {
			return null;
		}
		return frames[(int) (sequence % frames.length)];
	}

	/** Reader. The newest published frame still held, or null before the first or once it was released. */
	public SimFrame latest() {
		final long count = published;
		// a released frame may already be rewritten by the writer
		return (count == 0) || ((count - 1) < released) ? null : frames[(int) ((count - 1) % frames.length)];
	}

	/** Reader. Frees every frame below the sequence for the writer; never goes backwards or past the published ones. */
	public void release(final long sequence) {
		final long limit = Math.min(sequence, published);
		if (limit > released) {
			released = limit;
		}
	}
}
