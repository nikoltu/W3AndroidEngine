package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import com.badlogic.gdx.Gdx;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.PerfStats;

/**
 * The simulation thread of {@link SimThreadMode#ON}: it makes the game's steps on its own, as many as the render thread
 * has granted ({@link #grant}), while the render thread draws the frames it published.
 * <p>
 * <b>Steps.</b> The render thread turns its real time into steps exactly as the game without the thread does and grants
 * them; the thread makes them through its {@link Stepper} as soon as they are due. The game may fall behind by at most
 * the lag passed to {@link #grant} (about a second): steps granted beyond it are dropped, so a slow device plays slower
 * instead of hurrying to catch up, as without the thread. Between steps the thread also runs the jobs the render thread
 * hands it, in order: {@link #run} waits for the job (a step made for a replay's skip, a frame published at start),
 * {@link #post} does not.
 * <p>
 * <b>Calls to the render thread.</b> What the simulation needs an answer to from the render side ({@link #callOnRender},
 * natives that touch render objects, a save) waits in a queue that the render thread serves a few times per frame
 * ({@link #serve}). Once a step has made such a call the render thread keeps serving until the step ends (up to a short
 * budget), so a step with many of them is not spread over many frames.
 * <p>
 * <b>Holding.</b> The render thread may keep the thread from starting a step while it reads the simulation directly
 * ({@link #tryHold}, {@link #hold}, {@link #release}); a step that runs is finished first. {@link #park} is the same for
 * an app that goes to the background, with a bounded wait.
 * <p>
 * Render thread: grant, run, post, serve, hold, release, park, unpark, shutdown. Simulation thread: callOnRender. Any
 * thread: isSimThread.
 */
public final class SimThread implements SimThreadRunner {
	/** What the thread does to make one step, and what it does when it has nothing to do. */
	public interface Stepper {
		/**
		 * Simulation thread: makes one step if the game can make one now.
		 *
		 * @return {@link #STEPPED}, {@link #WAIT} (not now, but the granted step stays: a network turn has not come),
		 *         or {@link #BLOCKED} (the game cannot step: paused, ended, skipping; the granted steps are dropped)
		 */
		int step(int pendingSteps);

		/** Simulation thread, when no step is due or the game cannot step: commands, a pause's work, saves. */
		void idle();
	}

	public static final int STEPPED = 0;
	public static final int WAIT = 1;
	public static final int BLOCKED = 2;

	private static final int IDLE = 0;
	private static final int BUSY = 1;
	private static final int HELD = 2;

	/** How long the thread sleeps with nothing to do before it looks at commands and pauses again. */
	private static final long IDLE_PARK_NANOS = 16_000_000L;
	/** How soon it looks again when a step is granted but cannot be made yet. */
	private static final long WAIT_PARK_NANOS = 2_000_000L;
	private static final long SERVE_BUDGET_NANOS = Long.getLong("warsmash.simthread.servebudget", 8L) * 1_000_000L;

	private final LinkedBlockingQueue<Runnable> toSim = new LinkedBlockingQueue<>();
	private final LinkedBlockingQueue<Runnable> toRender = new LinkedBlockingQueue<>();
	private final ConcurrentLinkedQueue<Runnable> posted = new ConcurrentLinkedQueue<>();
	private final AtomicInteger credits = new AtomicInteger();
	private final AtomicInteger state = new AtomicInteger(IDLE);
	private final Thread thread;
	private final Thread renderThread;
	private final Stepper stepper;
	private volatile boolean stopped;
	private volatile boolean started;
	/** A step is running and asked the render thread for something: the render thread serves until it ends. */
	private volatile boolean crossedInStep;
	private volatile boolean stepping;
	/** Render thread only: a hold that {@link #park} made. */
	private boolean parked;
	/** Render thread only: the simulation is held or waits for the render thread. */
	private boolean heldByRender;
	/** Render thread only: holds taken while the simulation was already held (they release nothing). */
	private int nestedHolds;
	private volatile int lastResult = STEPPED;
	private volatile long attempts;
	/** Played before a call from the simulation is run, so the render side is up to date with the steps made. */
	private volatile Runnable beforeCall;
	/** What ended the thread in the middle of its work; the render thread throws it (see {@link #rethrowFailure}). */
	private volatile Throwable failure;
	private Object hintSession;
	private Method hintReport;

	public SimThread(final Stepper stepper) {
		this.stepper = stepper;
		this.renderThread = Thread.currentThread();
		this.thread = new Thread(this::loop, "warsmash-sim");
		this.thread.setDaemon(true);
		// above the loaders and workers, so that on a phone the scheduler keeps it on a fast core
		this.thread.setPriority(Math.min(Thread.MAX_PRIORITY, Thread.NORM_PRIORITY + 2));
	}

	/** Render thread, once the game exists: the thread starts taking jobs and granted steps. */
	public void start() {
		if (!this.started) {
			this.started = true;
			SimThreadGuard.registerSim(this.thread);
			this.thread.start();
		}
	}

	/** Render thread: what runs before each call of the simulation thread is served. */
	public void setBeforeCall(final Runnable beforeCall) {
		this.beforeCall = beforeCall;
	}

	@Override
	public boolean isSimThread() {
		return Thread.currentThread() == this.thread;
	}

	private void loop() {
		setUpThread();
		try {
			steps();
		}
		catch (final Throwable t) {
			fail(t);
		}
		finally {
			closeHints();
		}
	}

	/**
	 * Simulation thread: the game is left as the failure found it, half a step done, so nothing of it runs again; the
	 * render thread throws the failure where it next deals with the thread, as the step would have thrown there without
	 * the thread.
	 */
	private void fail(final Throwable t) {
		if (this.failure == null) {
			this.failure = t;
		}
		this.stopped = true;
		this.credits.set(0);
	}

	/** Render thread: the failure of the simulation thread, thrown as it was thrown there. */
	private void rethrowFailure() {
		final Throwable t = this.failure;
		if (t instanceof RuntimeException) {
			throw (RuntimeException) t;
		}
		if (t instanceof Error) {
			throw (Error) t;
		}
		if (t != null) {
			throw new IllegalStateException("The simulation failed", t);
		}
	}

	private void steps() {
		long lastIdle = 0;
		while (!this.stopped) {
			final Runnable job = this.toSim.poll();
			if (job != null) {
				job.run();
				continue;
			}
			final int pending = this.credits.get();
			if ((pending > 0) && !this.parkedOrHeld() && this.state.compareAndSet(IDLE, BUSY)) {
				final int result;
				final long start = System.nanoTime();
				this.stepping = true;
				try {
					runPosted();
					result = this.stepper.step(pending);
				}
				catch (final Throwable t) {
					// before the step lets go of the simulation, so a hold that waited for it does not read it
					fail(t);
					throw t;
				}
				finally {
					this.stepping = false;
					this.crossedInStep = false;
					this.state.set(IDLE);
				}
				this.lastResult = result;
				this.attempts++;
				if (result == STEPPED) {
					final long nanos = System.nanoTime() - start;
					this.credits.decrementAndGet();
					reportWork(nanos);
					if (PerfStats.ENABLED) {
						PerfStats.simThreadStep();
						PerfStats.simThreadSection(PerfStats.SimSection.STEP, nanos);
					}
					continue;
				}
				if (result == BLOCKED) {
					this.credits.set(0);
				}
				runIdle();
				lastIdle = System.nanoTime();
				parkFor(WAIT_PARK_NANOS);
				continue;
			}
			final long now = System.nanoTime();
			if ((now - lastIdle) >= WAIT_PARK_NANOS) {
				lastIdle = now;
				runIdle();
			}
			parkFor(pending > 0 ? WAIT_PARK_NANOS : IDLE_PARK_NANOS);
		}
	}

	private boolean parkedOrHeld() {
		return this.state.get() == HELD;
	}

	private void runIdle() {
		if (!this.state.compareAndSet(IDLE, BUSY)) {
			return;
		}
		try {
			runPosted();
			this.stepper.idle();
		}
		catch (final Throwable t) {
			fail(t);
			throw t;
		}
		finally {
			this.crossedInStep = false;
			this.state.set(IDLE);
		}
	}

	private void parkFor(final long nanos) {
		final long start = System.nanoTime();
		if (this.toSim.isEmpty()) {
			LockSupport.parkNanos(this, nanos);
		}
		if (PerfStats.ENABLED) {
			PerfStats.simThreadSection(PerfStats.SimSection.WAIT, System.nanoTime() - start);
		}
	}

	private void wake() {
		LockSupport.unpark(this.thread);
	}

	/**
	 * Render thread: grants steps that came due; the steps still waiting are capped at maxPending.
	 *
	 * @return the steps dropped because the game is too far behind
	 */
	public int grant(final int steps, final int maxPending) {
		rethrowFailure();
		if (steps <= 0) {
			return 0;
		}
		int dropped = 0;
		while (true) {
			final int now = this.credits.get();
			final int next = Math.min(maxPending, now + steps);
			if (this.credits.compareAndSet(now, Math.max(now, next))) {
				dropped = (now + steps) - Math.max(now, next);
				break;
			}
		}
		wake();
		return dropped;
	}

	/** Drops the steps granted and not yet made (a skip ahead or a load takes over). */
	public void dropGranted() {
		this.credits.set(0);
	}

	/** Steps granted and not yet made. */
	public int pending() {
		return this.credits.get();
	}

	/**
	 * Render thread: waits until the granted steps are made or the game cannot make them now, serving the calls the
	 * steps make meanwhile. For a game that is not drawn, where there is nothing else to do.
	 */
	public void awaitGranted() {
		rethrowFailure();
		final long since = this.attempts;
		while (!this.stopped) {
			final boolean idle = this.state.get() != BUSY;
			if (idle && ((this.credits.get() == 0)
					|| ((this.lastResult == WAIT) && (this.attempts > since)))) {
				break;
			}
			final Runnable request = poll(500_000L);
			if (request != null) {
				runCall(request);
			}
		}
		rethrowFailure();
		// the steps' writes are seen from here on
		hold();
		release();
	}

	/**
	 * Render thread: runs the job on the simulation thread between two steps and returns when it is done, serving
	 * the calls the job makes meanwhile. An exception of the job is thrown here.
	 */
	@Override
	public void run(final Runnable job) {
		rethrowFailure();
		if (isSimThread() || !this.started || (this.stopped && !this.thread.isAlive())) {
			// no thread runs the simulation (any more): the render thread has it to itself
			job.run();
			return;
		}
		final Throwable[] failure = new Throwable[1];
		final Runnable done = () -> {
		};
		this.toSim.add(() -> {
			try {
				job.run();
			}
			catch (final Throwable t) {
				failure[0] = t;
			}
			finally {
				this.toRender.add(done);
			}
		});
		wake();
		while (true) {
			final Runnable request = poll(1_000_000L);
			if (request == done) {
				break;
			}
			if (request != null) {
				runCall(request);
			}
			else if (!this.thread.isAlive() && this.toRender.isEmpty()) {
				// it ended before it took the job
				rethrowFailure();
				throw new IllegalStateException("The simulation thread ended before it ran the job");
			}
		}
		if (failure[0] instanceof RuntimeException) {
			throw (RuntimeException) failure[0];
		}
		if (failure[0] instanceof Error) {
			throw (Error) failure[0];
		}
		if (failure[0] != null) {
			throw new IllegalStateException(failure[0]);
		}
	}

	/**
	 * Any thread: a job for the simulation thread, run before its next step or in its next idle moment, never while
	 * the render thread holds the simulation; the caller does not wait. Jobs run in the order they were posted.
	 */
	public void post(final Runnable job) {
		this.posted.add(job);
		wake();
	}

	/** Simulation thread, holding the simulation: the posted jobs. */
	private void runPosted() {
		Runnable job;
		while ((job = this.posted.poll()) != null) {
			job.run();
		}
	}

	/** Any thread: something for the render thread to do at its next serving point; the caller does not wait. */
	public void postToRender(final Runnable call) {
		this.toRender.add(call);
	}

	/**
	 * Any thread. Runs the call on the render thread and returns its result; on any other thread than the simulation
	 * thread, in place. From the simulation thread it waits until the render thread has served it.
	 */
	@Override
	public <T> T callOnRender(final Supplier<T> call) {
		if (!isSimThread()) {
			return call.get();
		}
		if (this.stepping) {
			this.crossedInStep = true;
		}
		final Call<T> task = new Call<>(call);
		this.toRender.add(task);
		final long waitStart = SimThreadGuard.collecting() ? System.nanoTime() : 0L;
		try {
			return task.get();
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted waiting for the render thread", e);
		}
		catch (final ExecutionException e) {
			final Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw new IllegalStateException(cause);
		}
		finally {
			if (waitStart != 0L) {
				SimThreadGuard.waited(System.nanoTime() - waitStart);
			}
		}
	}

	@Override
	public <T> T onRender(final Class<T> type, final T target, final Set<String> inPlace) {
		final Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(self, method, args) -> {
					if (inPlace.contains(method.getName())) {
						return invoke(method, target, args);
					}
					if (isSimThread()) {
						SimThreadGuard.crossing("interface", type.getSimpleName() + "." + method.getName());
					}
					if (isSimThread() && (method.getReturnType() == void.class)) {
						SimThreadGuard.notWaiting();
						// nothing comes back, so the step does not wait for the interface
						this.toRender.add(() -> invoke(method, target, args));
						return null;
					}
					return callOnRender(() -> invoke(method, target, args));
				});
		return type.cast(proxy);
	}

	/** A call of the simulation thread that it waits for; it ends with an answer or with the failure that kept it. */
	private static final class Call<T> extends FutureTask<T> {
		Call(final Supplier<T> call) {
			super(call::get);
		}

		void fail(final Throwable t) {
			setException(t);
		}
	}

	private static Object invoke(final Method method, final Object target, final Object[] args) {
		try {
			return method.invoke(target, args);
		}
		catch (final InvocationTargetException e) {
			final Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw new IllegalStateException(cause);
		}
		catch (final IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Render thread: carries out what the simulation thread asked of it. When a step that is running asked, keeps
	 * serving until the step ends or the budget is used up.
	 */
	public void serve() {
		rethrowFailure();
		final long end = System.nanoTime() + SERVE_BUDGET_NANOS;
		while (true) {
			Runnable request = this.toRender.poll();
			if (request == null) {
				if (!this.crossedInStep || !this.stepping || (System.nanoTime() >= end)) {
					return;
				}
				request = poll(200_000L);
				if (request == null) {
					continue;
				}
			}
			runCall(request);
		}
	}

	private Runnable poll(final long nanos) {
		try {
			return this.toRender.poll(nanos, TimeUnit.NANOSECONDS);
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private void runCall(final Runnable request) {
		if (!(request instanceof Call)) {
			request.run();
			return;
		}
		final Call<?> call = (Call<?>) request;
		// the simulation thread waits for this answer, so the render side may read it meanwhile
		final boolean wasHeld = this.heldByRender;
		this.heldByRender = true;
		SimThreadGuard.renderHoldsSimulation(true);
		try {
			final Runnable before = this.beforeCall;
			if (before != null) {
				try {
					before.run();
				}
				catch (final Throwable t) {
					// the call fails as it would have where the simulation made it, and the waiting step with it
					call.fail(t);
					throw t;
				}
			}
			call.run();
		}
		finally {
			this.heldByRender = wasHeld;
			SimThreadGuard.renderHoldsSimulation(wasHeld);
		}
	}

	private void held(final boolean held) {
		this.heldByRender = held;
		SimThreadGuard.renderHoldsSimulation(held);
	}

	/**
	 * Render thread: keeps the simulation from starting a step, if no step runs now.
	 *
	 * @return true when held; then {@link #release} must follow
	 */
	public boolean tryHold() {
		rethrowFailure();
		if (!this.started) {
			return true;
		}
		if (this.parked || this.heldByRender) {
			this.nestedHolds++;
			return true;
		}
		if (this.state.compareAndSet(IDLE, HELD)) {
			held(true);
			return true;
		}
		return false;
	}

	/** Render thread: waits for the step that runs (serving its calls) and keeps the next one from starting. */
	public void hold() {
		rethrowFailure();
		if (!this.started) {
			return;
		}
		if (this.parked || this.heldByRender) {
			this.nestedHolds++;
			return;
		}
		while (!this.state.compareAndSet(IDLE, HELD)) {
			if (this.stopped && !this.thread.isAlive()) {
				rethrowFailure();
				return;
			}
			final Runnable request = poll(200_000L);
			if (request != null) {
				runCall(request);
			}
		}
		// the step waited for ended on a failure: what it left is not to be read
		rethrowFailure();
		held(true);
	}

	/** Render thread: lets the simulation go on after {@link #tryHold} or {@link #hold}. */
	public void release() {
		if (!this.started) {
			return;
		}
		if (this.nestedHolds > 0) {
			this.nestedHolds--;
			return;
		}
		held(false);
		this.state.compareAndSet(HELD, IDLE);
		wake();
	}

	/**
	 * Render thread (the app goes to the background): holds the simulation, waiting at most this long for a step that
	 * runs. Stays held until {@link #unpark}.
	 *
	 * @return whether the simulation is held
	 */
	public boolean park(final long maxWaitMillis) {
		rethrowFailure();
		if (!this.started || this.parked) {
			return this.parked;
		}
		final long end = System.nanoTime() + (maxWaitMillis * 1_000_000L);
		while (!this.state.compareAndSet(IDLE, HELD)) {
			if (System.nanoTime() >= end) {
				return false;
			}
			final Runnable request = poll(200_000L);
			if (request != null) {
				runCall(request);
			}
		}
		this.parked = true;
		held(true);
		return true;
	}

	public void unpark() {
		if (this.parked) {
			this.parked = false;
			held(false);
			this.state.compareAndSet(HELD, IDLE);
			wake();
		}
	}

	/** Ends the thread; a job or step that is running finishes first. Does not wait (see {@link #shutdown}). */
	@Override
	public void stop() {
		this.stopped = true;
		this.credits.set(0);
		this.thread.interrupt();
	}

	/**
	 * Render thread: ends the thread and waits until it has ended, serving its calls meanwhile, so that the step that
	 * runs finishes before the game it plays is taken apart; nothing the step uses may be let go while it runs, as
	 * without the thread the step would have ended before anything else. A step that takes longer than the time is
	 * written to the log with where it is, and waited for further.
	 *
	 * @return whether the thread ended within the time
	 */
	public boolean shutdown(final long maxWaitMillis) {
		this.stopped = true;
		this.credits.set(0);
		this.parked = false;
		this.state.compareAndSet(HELD, IDLE);
		wake();
		if (!this.started) {
			return true;
		}
		final long wait = maxWaitMillis * 1_000_000L;
		long reportAt = System.nanoTime() + wait;
		boolean inTime = true;
		while (this.thread.isAlive()) {
			final Runnable request = poll(1_000_000L);
			if (request != null) {
				runCall(request);
			}
			else if (System.nanoTime() >= reportAt) {
				inTime = false;
				reportAt = System.nanoTime() + wait;
				Log.warn("The simulation thread is still in its step: " + where(this.thread));
			}
		}
		// calls the thread left that nobody waits for (an interface's notice): the game they were for is over
		this.toRender.clear();
		return inTime;
	}

	private static String where(final Thread thread) {
		final StringBuilder text = new StringBuilder();
		for (final StackTraceElement element : thread.getStackTrace()) {
			text.append("\n    at ").append(element);
		}
		return text.toString();
	}

	/** Whether a step or a job runs on the simulation thread at this moment. */
	public boolean isBusy() {
		return this.state.get() == BUSY;
	}

	/** On Android: a higher thread priority and, where the system has one, a performance hint session for the steps. */
	private void setUpThread() {
		try {
			final Class<?> process = Class.forName("android.os.Process");
			final int tid = (Integer) process.getMethod("myTid").invoke(null);
			// THREAD_PRIORITY_DISPLAY, the render thread's own
			process.getMethod("setThreadPriority", int.class).invoke(null, -4);
			final int sdk = Class.forName("android.os.Build$VERSION").getField("SDK_INT").getInt(null);
			if ((sdk >= 31) && (Gdx.app != null)) {
				final Object manager = Gdx.app.getClass().getMethod("getSystemService", String.class).invoke(Gdx.app,
						"performance_hint");
				if (manager != null) {
					final Method create = manager.getClass().getMethod("createHintSession", int[].class, long.class);
					// a step is to be done well within the 50 ms the picture trails the game by
					this.hintSession = create.invoke(manager, new int[] { tid }, 20_000_000L);
					if (this.hintSession != null) {
						this.hintReport = this.hintSession.getClass().getMethod("reportActualWorkDuration", long.class);
					}
				}
			}
		}
		catch (final ClassNotFoundException e) {
			// not Android
		}
		catch (final Throwable t) {
			Log.warn("Simulation thread: no priority or performance hint: " + t);
		}
	}

	private void reportWork(final long nanos) {
		if (this.hintReport != null) {
			try {
				this.hintReport.invoke(this.hintSession, Math.max(1L, nanos));
			}
			catch (final Throwable t) {
				this.hintReport = null;
			}
		}
	}

	private void closeHints() {
		if (this.hintSession != null) {
			try {
				this.hintSession.getClass().getMethod("close").invoke(this.hintSession);
			}
			catch (final Throwable t) {
				// the session goes with the process
			}
			this.hintSession = null;
		}
	}
}
