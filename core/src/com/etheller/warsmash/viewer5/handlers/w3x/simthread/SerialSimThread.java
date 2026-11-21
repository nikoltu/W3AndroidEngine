package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

/**
 * The thread that runs simulation steps in {@link SimThreadMode#SERIAL}: the render thread hands a job over and waits
 * until it is done, so only one of the two threads runs at any time and the game plays exactly as without the thread.
 * While the render thread waits it also carries out what the simulation asks of it ({@link #callOnRender}), the way
 * the simulation reaches the few things that can only be done on the render thread.
 * <p>
 * Render thread: {@link #run}. Simulation thread: {@link #callOnRender}. {@link #stop} from the render thread.
 */
public final class SerialSimThread implements SimThreadRunner {
	private static final Runnable DONE = () -> {
	};

	private final LinkedBlockingQueue<Runnable> toSim = new LinkedBlockingQueue<>();
	private final LinkedBlockingQueue<Runnable> toRender = new LinkedBlockingQueue<>();
	private final Thread thread;
	private volatile boolean stopped;

	public SerialSimThread() {
		this.thread = new Thread(this::loop, "warsmash-sim");
		this.thread.setDaemon(true);
		this.thread.start();
		SimThreadGuard.threadsAlternate(true);
	}

	private void loop() {
		try {
			while (!this.stopped) {
				final Runnable job = this.toSim.take();
				job.run();
			}
		}
		catch (final InterruptedException e) {
			// stop() ends the loop
		}
	}

	public boolean isSimThread() {
		return Thread.currentThread() == this.thread;
	}

	/**
	 * Render thread. Runs the job on the simulation thread and returns when it is done, carrying out the render
	 * thread's calls the job makes meanwhile. An exception of the job is thrown here, as if it ran in place.
	 */
	public void run(final Runnable job) {
		if (isSimThread()) {
			job.run();
			return;
		}
		// the render thread owns the simulation until the first step, as it does while a game loads
		SimThreadGuard.registerSim(this.thread);
		final Throwable[] failure = new Throwable[1];
		this.toSim.add(() -> {
			try {
				job.run();
			}
			catch (final Throwable t) {
				failure[0] = t;
			}
			finally {
				this.toRender.add(DONE);
			}
		});
		try {
			Runnable request;
			while ((request = this.toRender.take()) != DONE) {
				request.run();
			}
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted waiting for the simulation thread", e);
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
	 * Any thread. Runs the call on the render thread and returns its result; on the render thread itself, in place.
	 * From the simulation thread this blocks until the render thread, which waits in {@link #run}, has done it.
	 */
	public <T> T callOnRender(final Supplier<T> call) {
		if (!isSimThread()) {
			return call.get();
		}
		final FutureTask<T> task = new FutureTask<>(call::get);
		this.toRender.add(task);
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
	}

	/**
	 * A stand-in for the target of an interface type whose every call is made on the render thread (see
	 * {@link #callOnRender}); for what the simulation reports to the interface directly.
	 */
	public <T> T onRender(final Class<T> type, final T target, final Set<String> inPlace) {
		final Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
				(self, method, args) -> {
					if (inPlace.contains(method.getName())) {
						return invoke(method, target, args);
					}
					if (isSimThread()) {
						SimThreadGuard.crossing("interface", type.getSimpleName() + "." + method.getName());
					}
					return callOnRender(() -> invoke(method, target, args));
				});
		return type.cast(proxy);
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

	/** Ends the thread; a job that is running finishes first. */
	public void stop() {
		this.stopped = true;
		this.thread.interrupt();
	}
}
