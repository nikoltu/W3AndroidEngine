package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class SimFrameRingTest {
	private final RenderEventPool pool = new RenderEventPool();

	private static void fill(final SimFrame frame, final int tick) {
		frame.tick = tick;
		frame.gameTimeMillis = tick * 50L;
		final int row = frame.units.addRow();
		frame.units.handle[row] = tick;
		frame.units.x[row] = tick;
		frame.units.y[row] = tick * 2f;
	}

	@Test
	void ringNeedsRoomForInterpolation() {
		assertThrows(IllegalArgumentException.class, () -> new SimFrameRing(2, pool));
	}

	@Test
	void publishedFramesComeBackInOrder() {
		final SimFrameRing ring = new SimFrameRing(4, pool);
		assertNull(ring.latest());
		assertNull(ring.get(0));
		for (int i = 0; i < 3; i++) {
			fill(ring.beginWrite(), i);
			ring.publish();
		}
		assertEquals(3, ring.publishedCount());
		assertEquals(0, ring.get(0).tick);
		assertEquals(2, ring.get(2).tick);
		assertEquals(2, ring.latest().tick);
		assertEquals(2, ring.latest().sequence);
		assertNull(ring.get(3));
	}

	@Test
	void unpublishedFrameIsInvisibleAndBeginWriteIsRepeatable() {
		final SimFrameRing ring = new SimFrameRing(3, pool);
		final SimFrame first = ring.beginWrite();
		assertSame(first, ring.beginWrite());
		assertNull(ring.get(0));
		assertEquals(0, ring.publishedCount());
		ring.publish();
		assertSame(first, ring.get(0));
		assertThrows(IllegalStateException.class, ring::publish);
	}

	@Test
	void writerWaitsWhenEveryFrameIsHeld() {
		final SimFrameRing ring = new SimFrameRing(3, pool);
		for (int i = 0; i < 3; i++) {
			assertNotNull(ring.beginWrite());
			ring.publish();
		}
		assertNull(ring.beginWrite());
		ring.release(1);
		final SimFrame reused = ring.beginWrite();
		assertNotNull(reused);
		assertEquals(3, reused.sequence);
		assertSame(reused, ring.beginWrite());
	}

	@Test
	void releasedFramesAreGoneAndReleaseNeverGoesBackOrPastPublished() {
		final SimFrameRing ring = new SimFrameRing(4, pool);
		for (int i = 0; i < 3; i++) {
			fill(ring.beginWrite(), i);
			ring.publish();
		}
		ring.release(2);
		assertNull(ring.get(1));
		assertEquals(2, ring.get(2).tick);
		ring.release(1);
		assertEquals(2, ring.oldestHeld());
		ring.release(100);
		assertEquals(3, ring.oldestHeld());
		assertNull(ring.get(2));
	}

	@Test
	void reusedFrameIsEmptyAndItsEventsGoBackToThePool() {
		final SimFrameRing ring = new SimFrameRing(3, pool);
		SimFrame frame = ring.beginWrite();
		fill(frame, 0);
		for (int i = 0; i < 5; i++) {
			final RenderEvent event = pool.obtain();
			event.code = RenderEvent.UNIT_CREATED;
			event.a = i;
			frame.addEvent(event);
		}
		ring.publish();
		assertEquals(5, ring.get(0).eventCount());
		assertEquals(3, ring.get(0).event(3).a);
		final int created = pool.createdCount();
		for (int i = 1; i < 3; i++) {
			ring.beginWrite();
			ring.publish();
		}
		ring.release(3);
		frame = ring.beginWrite();
		assertEquals(0, frame.eventCount());
		assertEquals(0, frame.units.count);
		for (int i = 0; i < 5; i++) {
			final RenderEvent event = pool.obtain();
			assertEquals(0, event.code);
			frame.addEvent(event);
		}
		assertEquals(created, pool.createdCount());
	}

	@Test
	void fogIsKeptOnlyWhenSetAndFlagsChange() {
		final SimFrameRing ring = new SimFrameRing(3, pool);
		SimFrame frame = ring.beginWrite();
		frame.fogVersion = 1;
		frame.setFog(new byte[] { 1, 2, 3 }, 3, 1);
		ring.publish();
		assertTrue(ring.get(0).fogChanged);
		assertEquals(3, ring.get(0).fogLength);
		frame = ring.beginWrite();
		frame.fogVersion = 1;
		ring.publish();
		assertFalse(ring.get(1).fogChanged);
	}

	/** One writer racing one reader: every frame the reader sees is complete and in order, and nothing is overwritten while held. */
	@Test
	void concurrentWriterAndReaderSeeConsistentFrames() throws Exception {
		final SimFrameRing ring = new SimFrameRing(8, pool);
		final int total = 200_000;
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread writer = new Thread(() -> {
			try {
				for (int tick = 0; tick < total; tick++) {
					SimFrame frame;
					while ((frame = ring.beginWrite()) == null) {
						Thread.onSpinWait();
					}
					fill(frame, tick);
					for (int i = 0; i < 3; i++) {
						final RenderEvent event = pool.obtain();
						event.a = tick;
						frame.addEvent(event);
					}
					ring.publish();
				}
			}
			catch (final Throwable t) {
				failure.compareAndSet(null, t);
			}
		});
		writer.start();
		long next = 0;
		final long deadline = System.nanoTime() + 60_000_000_000L;
		while ((next < total) && (failure.get() == null)) {
			assertTrue(System.nanoTime() < deadline, "reader timed out at " + next);
			final SimFrame frame = ring.get(next);
			if (frame == null) {
				Thread.onSpinWait();
				continue;
			}
			assertEquals(next, frame.sequence);
			assertEquals((int) next, frame.tick);
			assertEquals(1, frame.units.count);
			assertEquals((float) next, frame.units.x[0]);
			assertEquals(next * 2f, frame.units.y[0]);
			assertEquals(3, frame.eventCount());
			assertEquals((int) next, frame.event(2).a);
			next++;
			ring.release(next - 1);
		}
		writer.join();
		assertNull(failure.get());
		assertEquals(total, next);
	}
}
