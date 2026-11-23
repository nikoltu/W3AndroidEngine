package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PeerQueueTest {
	private final List<Integer> built = new ArrayList<>();
	private long clock;
	/** What building one peer costs on the fake clock. */
	private long cost = 1_000_000L;
	private final PeerQueue queue = new PeerQueue((handle, data) -> {
		this.built.add(handle);
		this.clock += this.cost;
	}, () -> this.clock);

	@Test
	void aWaveIsSpreadOverTheFramesByTheBudget() {
		for (int i = 1; i <= 10; i++) {
			this.queue.announce(i, "unit" + i);
		}
		this.queue.beginFrame(3_000_000L, 100);
		assertEquals(3, this.built.size());
		this.queue.beginFrame(3_000_000L, 100);
		assertEquals(6, this.built.size());
		this.queue.beginFrame(3_000_000L, 100);
		this.queue.beginFrame(3_000_000L, 100);
		assertEquals(10, this.built.size());
		assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), this.built);
		assertEquals(0, this.queue.waitingCount());
	}

	@Test
	void everyFrameBuildsAtLeastOne() {
		this.cost = 10_000_000L;
		this.queue.announce(1, "a");
		this.queue.announce(2, "b");
		this.queue.beginFrame(1_000_000L, 100);
		assertEquals(List.of(1), this.built);
		this.queue.beginFrame(1_000_000L, 100);
		assertEquals(List.of(1, 2), this.built);
	}

	@Test
	void anOverduePeerIsBuiltWhateverTheBudget() {
		this.cost = 10_000_000L;
		this.queue.beginFrame(1_000_000L, 2);
		for (int i = 1; i <= 4; i++) {
			this.queue.announce(i, "u");
		}
		this.queue.beginFrame(1_000_000L, 2);
		assertEquals(List.of(1), this.built);
		// two frames after they were announced the rest are overdue, and all come out at once
		this.queue.beginFrame(1_000_000L, 2);
		assertEquals(List.of(1, 2, 3, 4), this.built);
	}

	@Test
	void presentBuildsEverythingBeforeTheObjectInOrder() {
		this.queue.announce(5, "a");
		this.queue.announce(6, "b");
		this.queue.announce(7, "c");
		assertFalse(this.queue.present(6));
		assertEquals(List.of(5, 6), this.built);
		assertTrue(this.queue.isWaiting(7));
		assertFalse(this.queue.present(6));
		assertEquals(2, this.built.size());
	}

	@Test
	void aPeerThatWaitedAFrameAndIsBuiltAtItsEventIsLate() {
		this.queue.beginFrame(1_000_000L, 100);
		this.queue.announce(1, "a");
		this.queue.beginFrame(0L, 100);
		// built in this frame already (at least one per frame), so it is not waiting any more
		assertFalse(this.queue.present(1));
		this.queue.announce(2, "b");
		this.queue.announce(3, "c");
		this.queue.beginFrame(0L, 100);
		assertEquals(List.of(1, 2), this.built);
		assertTrue(this.queue.present(3));
		assertEquals(1, this.queue.late());
		assertEquals(List.of(1, 2, 3), this.built);
	}

	@Test
	void anEventInTheFrameOfTheCreationIsNeverLate() {
		this.queue.beginFrame(1_000_000L, 100);
		this.queue.announce(1, "a");
		assertFalse(this.queue.present(1));
		assertEquals(0, this.queue.late());
		assertEquals(1, this.queue.builtAtEvent());
	}

	@Test
	void flushBuildsAllAndPresentFindsNothingAfterwards() {
		this.queue.announce(1, "a");
		this.queue.announce(2, "b");
		this.queue.flush();
		assertEquals(List.of(1, 2), this.built);
		assertFalse(this.queue.present(2));
		assertEquals(2, this.built.size());
	}

	@Test
	void announcingFromAnotherThreadIsSeen() throws InterruptedException {
		final Thread thread = new Thread(() -> this.queue.announce(9, "x"));
		thread.start();
		thread.join();
		assertTrue(this.queue.isWaiting(9));
		this.queue.flush();
		assertEquals(List.of(9), this.built);
	}

	@Test
	void aPeerBuiltBeforeItsFilesAreLoadedIsCountedCold() {
		final List<Integer> builtNow = new ArrayList<>();
		final boolean[] ready = { false };
		final PeerQueue cold = new PeerQueue(new PeerQueue.Builder() {
			@Override
			public void build(final int handle, final Object data) {
				builtNow.add(handle);
			}

			@Override
			public boolean ready(final Object data) {
				return ready[0];
			}
		}, () -> 0L);
		cold.announce(1, "a");
		assertTrue(cold.waitingForFiles());
		cold.beginFrame(1_000_000L, 2);
		assertEquals(List.of(1), builtNow);
		assertEquals(1, cold.cold());
		assertFalse(cold.waitingForFiles());
	}

	@Test
	void aPeerWhoseFilesAreLoadingCountsAsLateWhenItsEventComes() {
		final PeerQueue late = new PeerQueue(new PeerQueue.Builder() {
			@Override
			public void build(final int handle, final Object data) {
			}

			@Override
			public boolean ready(final Object data) {
				return false;
			}
		}, () -> 0L);
		late.announce(1, "a");
		assertTrue(late.present(1));
		assertEquals(1, late.late());
	}
}
