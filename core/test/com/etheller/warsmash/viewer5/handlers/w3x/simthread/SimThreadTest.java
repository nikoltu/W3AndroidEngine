package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SimThreadTest {
	private static final Duration LIMIT = Duration.ofSeconds(5);

	@AfterEach
	void forgetTheThreads() {
		// the guard keeps the thread a game started; the other tests run their simulations on the test thread
		SimThreadGuard.reset();
	}

	private abstract static class Steps implements SimThread.Stepper {
		@Override
		public void idle() {
		}
	}

	/** Serves the simulation thread's calls as the render thread does each frame, until a failure comes out. */
	private static RuntimeException serveUntilFailure(final SimThread thread) {
		final long end = System.nanoTime() + LIMIT.toNanos();
		while (System.nanoTime() < end) {
			try {
				thread.serve();
			}
			catch (final RuntimeException e) {
				return e;
			}
			Thread.onSpinWait();
		}
		fail("the simulation thread's failure never reached the render thread");
		return null;
	}

	@Test
	void aCallWhoseCatchUpFailsEndsWithThatFailureOnBothThreads() {
		assertTimeoutPreemptively(LIMIT, () -> {
			final SimThread[] holder = new SimThread[1];
			final AtomicInteger answers = new AtomicInteger();
			holder[0] = new SimThread(new Steps() {
				@Override
				public int step(final int pendingSteps) {
					holder[0].callOnRender(() -> 42);
					answers.incrementAndGet();
					return SimThread.STEPPED;
				}
			});
			final SimThread thread = holder[0];
			final NullPointerException playing = new NullPointerException("a frame could not be played");
			thread.setBeforeCall(() -> {
				throw playing;
			});
			thread.start();
			thread.grant(1, 10);
			assertSame(playing, serveUntilFailure(thread));
			// the step that waited for the call did not go on, and the thread does not keep the render thread waiting
			assertSame(playing, assertThrows(NullPointerException.class, thread::hold));
			assertSame(playing, assertThrows(NullPointerException.class, thread::serve));
			assertTrue(answers.get() == 0);
			assertTrue(thread.shutdown(1000));
		});
	}

	@Test
	void aFailedStepIsThrownOnTheRenderThreadAndNoStepFollows() {
		assertTimeoutPreemptively(LIMIT, () -> {
			final AtomicInteger steps = new AtomicInteger();
			final IllegalStateException broken = new IllegalStateException("a step failed");
			final SimThread thread = new SimThread(new Steps() {
				@Override
				public int step(final int pendingSteps) {
					steps.incrementAndGet();
					throw broken;
				}
			});
			thread.start();
			thread.grant(5, 10);
			assertSame(broken, serveUntilFailure(thread));
			assertSame(broken, assertThrows(IllegalStateException.class, () -> thread.grant(1, 10)));
			assertSame(broken, assertThrows(IllegalStateException.class, thread::tryHold));
			assertSame(broken, assertThrows(IllegalStateException.class, () -> thread.run(() -> {
			})));
			assertTrue(steps.get() == 1);
			assertTrue(thread.shutdown(1000));
		});
	}

	@Test
	void aFailedIdleWorkIsThrownOnTheRenderThread() {
		assertTimeoutPreemptively(LIMIT, () -> {
			final IllegalArgumentException broken = new IllegalArgumentException("a command failed");
			final SimThread thread = new SimThread(new SimThread.Stepper() {
				@Override
				public int step(final int pendingSteps) {
					return SimThread.STEPPED;
				}

				@Override
				public void idle() {
					throw broken;
				}
			});
			thread.start();
			assertSame(broken, serveUntilFailure(thread));
			assertTrue(thread.shutdown(1000));
		});
	}

	@Test
	void aFailedRenderCallFailsTheStepThatMadeIt() {
		assertTimeoutPreemptively(LIMIT, () -> {
			final SimThread[] holder = new SimThread[1];
			final UnsupportedOperationException refused = new UnsupportedOperationException("the render side refused");
			holder[0] = new SimThread(new Steps() {
				@Override
				public int step(final int pendingSteps) {
					holder[0].callOnRender(() -> {
						throw refused;
					});
					return SimThread.STEPPED;
				}
			});
			final SimThread thread = holder[0];
			thread.start();
			thread.grant(1, 10);
			assertSame(refused, serveUntilFailure(thread));
			assertTrue(thread.shutdown(1000));
		});
	}

	@Test
	void shutdownWaitsForTheStepThatRuns() {
		assertTimeoutPreemptively(LIMIT, () -> {
			final AtomicBoolean inStep = new AtomicBoolean();
			final AtomicBoolean stepEnded = new AtomicBoolean();
			final SimThread[] holder = new SimThread[1];
			holder[0] = new SimThread(new Steps() {
				@Override
				public int step(final int pendingSteps) {
					inStep.set(true);
					try {
						Thread.sleep(300);
					}
					catch (final InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					// a call made late in the step is still served while the render thread waits for the end
					holder[0].callOnRender(() -> 1);
					stepEnded.set(true);
					return SimThread.STEPPED;
				}
			});
			final SimThread thread = holder[0];
			thread.start();
			thread.grant(1, 10);
			while (!inStep.get()) {
				Thread.onSpinWait();
			}
			assertFalse(thread.shutdown(50));
			assertTrue(stepEnded.get());
			assertFalse(thread.isBusy());
		});
	}
}
