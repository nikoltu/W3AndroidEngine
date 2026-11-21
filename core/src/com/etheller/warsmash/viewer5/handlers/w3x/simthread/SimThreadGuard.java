package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.util.Log;

/**
 * Debug checks of which thread runs a piece of code. Each side registers its thread once; the asserts then throw
 * {@link IllegalStateException} when code that belongs to one side runs on the other. Checks are on only while
 * {@link #ENABLED}; otherwise every method is a field read.
 * <p>
 * With {@code -Dwarsmash.simthread.guard=collect} a wrong-thread call does not throw: it is counted under its caller
 * and the list is written to the log every few seconds and at exit (and to the file named by
 * {@code warsmash.simthread.guard.file}), so one run lists every place that still crosses threads.
 */
public final class SimThreadGuard {
	private static final String PROPERTY = "warsmash.simthread.guard";
	private static final String FILE_PROPERTY = "warsmash.simthread.guard.file";
	private static final String COLLECT = "collect";
	private static final int STACK_FRAMES = 6;
	private static final long REPORT_NANOS = 10_000_000_000L;

	public static final boolean ENABLED = (System.getProperty(PROPERTY) != null)
			|| SimThreadGuard.class.desiredAssertionStatus();
	private static final boolean COLLECTING = COLLECT.equalsIgnoreCase(System.getProperty(PROPERTY));

	private static volatile Thread simThread;
	private static volatile Thread glThread;
	private static volatile boolean renderOwnsSimulation;
	private static volatile boolean renderHoldsSimulation;
	private static volatile boolean threadsAlternate;

	private static final class Violation {
		final String role;
		final String[] stack;
		long count;

		Violation(final String role, final String[] stack) {
			this.role = role;
			this.stack = stack;
		}
	}

	private static final Map<String, Violation> VIOLATIONS = new LinkedHashMap<>();
	private static final Map<String, long[]> CROSSINGS = new LinkedHashMap<>();
	private static long lastReport;
	private static boolean hookInstalled;

	/** Called by the thread that runs simulation steps, when it starts. */
	public static void registerSim(final Thread thread) {
		simThread = thread;
	}

	/** Called by the render (GL) thread, when the screen starts. */
	public static void registerGl(final Thread thread) {
		glThread = thread;
	}

	/**
	 * The game's simulation thread has ended: until the next game starts one, the render thread loads and takes apart
	 * simulations alone.
	 */
	public static void simulationStopped() {
		simThread = null;
		threadsAlternate = false;
	}

	public static void reset() {
		simThread = null;
		glThread = null;
		threadsAlternate = false;
	}

	/**
	 * While set, the render thread may touch simulation state: it loads a map or a saved game and no step runs. The
	 * code that does it sets and clears this around the loading.
	 */
	public static void renderOwnsSimulation(final boolean owns) {
		renderOwnsSimulation = owns;
	}

	/**
	 * While set, the render thread may touch simulation state because the simulation thread of ON mode is held between
	 * two steps or waits for the render thread's answer; set and cleared by that thread's holds.
	 */
	public static void renderHoldsSimulation(final boolean holds) {
		renderHoldsSimulation = holds;
	}

	/**
	 * Set for the whole run of {@link SimThreadMode#SERIAL}: the render thread hands each step over and waits for it, so
	 * the two threads never run together and the render thread may read the simulation whenever it runs.
	 */
	public static void threadsAlternate(final boolean alternate) {
		threadsAlternate = alternate;
	}

	/** Code that reads or writes simulation state. */
	public static void assertSim() {
		if (ENABLED && !((renderOwnsSimulation || renderHoldsSimulation || threadsAlternate)
				&& (Thread.currentThread() == glThread))) {
			check(simThread, "simulation");
		}
	}

	/** Code that touches GL, the interface or render peers. */
	public static void assertGl() {
		if (ENABLED) {
			check(glThread, "render");
		}
	}

	private static void check(final Thread expected, final String role) {
		if ((expected != null) && (expected != Thread.currentThread())) {
			if (COLLECTING) {
				collect(role);
				return;
			}
			throw new IllegalStateException(
					"Expected the " + role + " thread, ran on " + Thread.currentThread().getName());
		}
	}

	private static void collect(final String role) {
		final StackTraceElement[] trace = new Throwable().getStackTrace();
		final List<String> frames = new ArrayList<>();
		// the first two are collect and check, the third is the assert itself
		for (int i = 3; (i < trace.length) && (frames.size() < STACK_FRAMES); i++) {
			frames.add(trace[i].getClassName() + "." + trace[i].getMethodName() + ":" + trace[i].getLineNumber());
		}
		final StringBuilder key = new StringBuilder(role);
		for (final String frame : frames) {
			key.append('|').append(frame);
		}
		synchronized (VIOLATIONS) {
			Violation violation = VIOLATIONS.get(key.toString());
			if (violation == null) {
				violation = new Violation(role, frames.toArray(new String[0]));
				VIOLATIONS.put(key.toString(), violation);
				installHook();
			}
			violation.count++;
			final long now = System.nanoTime();
			if ((now - lastReport) > REPORT_NANOS) {
				lastReport = now;
				report();
			}
		}
	}

	/**
	 * The simulation thread asked the render thread to do something for it (collect mode only), counted by kind and
	 * name: these are the crossings that work today only because the render thread does them on request.
	 */
	public static void crossing(final String kind, final String what) {
		if (COLLECTING) {
			synchronized (VIOLATIONS) {
				CROSSINGS.computeIfAbsent(kind + " " + what, key -> new long[1])[0]++;
				installHook();
			}
		}
	}

	private static void installHook() {
		if (!hookInstalled) {
			hookInstalled = true;
			Runtime.getRuntime().addShutdownHook(new Thread(SimThreadGuard::report, "guard-report"));
		}
	}

	/** Number of distinct places that crossed threads so far (collect mode). */
	public static int violationSites() {
		synchronized (VIOLATIONS) {
			return VIOLATIONS.size();
		}
	}

	/** Writes the collected list to the log and the guard file. */
	public static void report() {
		final List<Violation> sorted;
		synchronized (VIOLATIONS) {
			sorted = new ArrayList<>(VIOLATIONS.values());
		}
		if (sorted.isEmpty() && CROSSINGS.isEmpty()) {
			return;
		}
		sorted.sort(Comparator.comparing((Violation v) -> v.role).thenComparing((Violation v) -> -v.count));
		final StringBuilder text = new StringBuilder();
		text.append("GUARD sites=").append(sorted.size()).append('\n');
		synchronized (VIOLATIONS) {
			for (final Map.Entry<String, long[]> crossing : CROSSINGS.entrySet()) {
				text.append("GUARD crossing ").append(crossing.getKey()).append(" x").append(crossing.getValue()[0])
						.append('\n');
			}
		}
		for (final Violation violation : sorted) {
			text.append("GUARD ").append(violation.role).append(" x").append(violation.count);
			for (final String frame : violation.stack) {
				text.append("\n    ").append(frame);
			}
			text.append('\n');
		}
		final String file = System.getProperty(FILE_PROPERTY);
		if (file != null) {
			try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
				out.print(text);
			}
			catch (final Exception e) {
				Log.warn("GUARD report not written: " + e);
			}
		}
		else {
			Log.info(text.toString());
		}
	}

	private SimThreadGuard() {
	}
}
