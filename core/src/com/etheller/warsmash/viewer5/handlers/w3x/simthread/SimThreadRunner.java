package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.Set;
import java.util.function.Supplier;

/**
 * What the map viewer asks of the thread that runs its simulation, whether that thread waits for the render thread
 * ({@link SerialSimThread}) or runs on its own ({@link SimThread}).
 */
public interface SimThreadRunner {
	boolean isSimThread();

	/** Render thread: runs the job on the simulation thread and returns when it is done. */
	void run(Runnable job);

	/** Any thread: runs the call on the render thread and returns its result. */
	<T> T callOnRender(Supplier<T> call);

	/**
	 * A stand-in for an interface whose calls are made on the render thread, except the methods named in inPlace: they
	 * are made on the calling thread, for the ones that read the simulation and hand the interface only what they read.
	 */
	<T> T onRender(Class<T> type, T target, Set<String> inPlace);

	void stop();
}
