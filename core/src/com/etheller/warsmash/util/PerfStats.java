package com.etheller.warsmash.util;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

import com.badlogic.gdx.Gdx;

/**
 * Frame time split by section, logged every few seconds while the "warsmash.perf" system property is set or, where
 * there are no command line properties (Android), a file named warsmash.perf is in the application's local files.
 */
public final class PerfStats {
	public enum Section {
		TERRAIN_UPDATE, SCENE_UPDATE, WIDGETS, SIMULATION, SHADOWS, GROUND, CLIFFS, OPAQUE, SPLATS, WATER, TRANSLUCENT,
		INTERFACE, MELEE_UI, FRAME_END, GPU_WAIT, LIMITER, SWAP
	}

	public enum Step {
		UNITS, PROJECTILES, PATHING, TIMERS, JASS, AI, FOG, POST, TAIL, TURN, SAVES
	}

	public static boolean ENABLED = System.getProperty("warsmash.perf") != null;
	/** Waits for the GPU at the end of every frame to time it (changes frame pacing; only while measuring). */
	private static boolean finish = System.getProperty("warsmash.perf.finish") != null;
	private static boolean checkedLocalFile;
	/** The file the switch is kept in, relative to the application's local files. */
	public static final String SWITCH_FILE = "warsmash.perf";

	private static final long REPORT_NANOS = 5_000_000_000L;
	private static final long[] TOTALS = new long[Section.values().length];
	private static final long[] STEP_TOTALS = new long[Step.values().length];
	private static final int SECTION_COUNT = Section.values().length;
	private static final long[] FRAME_SECTIONS = new long[SECTION_COUNT + Step.values().length];
	private static final long[] WORST_SECTIONS = new long[FRAME_SECTIONS.length];
	private static long stepStart;
	private static long frameStart;
	private static long sectionStart;
	private static long frames;
	private static long frameTotal;
	private static long worstFrame;
	private static long reportStart;
	private static long simulationSteps;
	private static long pathJobs;
	private static long pathIterations;
	private static long pathDiscarded;
	private static long pathDiscardedIterations;
	private static long pathDiscardedNanos;
	private static long interfaceDraws;
	private static long drawCalls;
	private static long frameDrawCalls;
	private static long drawCallsWorst;
	private static long lastRenderEnd;
	private static boolean frameBegun;
	private static long simCpuStart;
	private static long simCpuFrame;
	private static long simCpuTotal;
	private static long simGapTotal;
	private static long cpuStart;
	private static long cpuTotal;
	private static long gcCountAtFrame;
	private static long gcBlockingAtFrame;
	private static long frameGcCount;
	private static long frameGcBlockingNanos;
	private static long gcCountAtReport;
	private static long gcTimeAtReport;
	private static long gcBlockingAtReport;
	private static long allocatedAtReport;
	private static Method cpuMethod;
	private static Object cpuTarget;
	private static Method statMethod;
	private static boolean probedRuntime;
	private static final long SPIKE_NANOS = Long.getLong("warsmash.perf.spike", 50L) * 1_000_000L;
	private static final long SLOW_LOAD_NANOS = 20_000_000L;
	private static final int[] WINDOW_HISTOGRAM = new int[1001];
	private static final int[] RUN_HISTOGRAM = new int[1001];
	private static long firstFrame;
	/** The last frame the loading screen drew, and the longest time between two of them, while a game loads. */
	private static long lastLoadingFrame;
	private static long worstLoadingFrame;
	private static int frameLoads;
	private static long frameLoadNanos;
	private static long frameTextureNanos;
	private static long textureBytes;
	private static int evictedTextures;
	private static int evictedModels;
	private static long slowestLoadNanos;
	private static String slowestLoad;

	/** The memory the game's model textures hold and how many were let go so far, for the next report. */
	public static void textures(final long bytes, final int evicted, final int evictedModelCount) {
		textureBytes = bytes;
		evictedTextures = evicted;
		evictedModels = evictedModelCount;
	}

	/**
	 * The game screen's setup runs in steps under the loading screen, frames frameStart never sees, so it is reported
	 * on its own. The loading screen of a game that follows another one (a level change, a loaded save) is drawn by
	 * the menu, so it is reported here too and kept out of the frames of the games: counted as one frame, the whole
	 * loading was the worst frame of the window it ended in.
	 *
	 * @param steps the time of each setup step: the interface's build and main, what loads after it, the script's
	 *              load, its start or the save's load, the sound preload and the game's opening steps
	 * @param frames the frames the setup took, a step of it can take several
	 * @param longestFrame the longest of them
	 */
	public static void showDone(final long start, final long[] steps, final int frames, final long longestFrame) {
		checkSwitchFile();
		if (ENABLED) {
			final long end = System.nanoTime();
			long total = 0;
			for (final long step : steps) {
				total += step;
			}
			System.err.println("Screen setup: total " + (total / 1_000_000) + " ms in " + frames
					+ " frames, longest " + (longestFrame / 1_000_000) + " ms (interface build " + (steps[0] / 1_000_000)
					+ " ms, interface main " + (steps[1] / 1_000_000) + " ms, after interface "
					+ (steps[2] / 1_000_000) + " ms, script load " + (steps[3] / 1_000_000)
					+ " ms, script start or save load " + (steps[4] / 1_000_000) + " ms, sound preload "
					+ (steps[5] / 1_000_000) + " ms, opening steps " + (steps[6] / 1_000_000) + " ms), switch "
					+ ((end - start) / 1_000_000) + " ms");
			if (lastLoadingFrame != 0) {
				worstLoadingFrame = Math.max(worstLoadingFrame, end - lastLoadingFrame);
			}
			if (frameStart != 0) {
				final long lastFrameEnd = lastRenderEnd != 0 ? lastRenderEnd : frameStart;
				final long between = end - lastFrameEnd;
				System.err.println("Game change: " + (between / 1_000_000) + " ms from the last frame of the game before,"
						+ " longest loading screen frame " + (worstLoadingFrame / 1_000_000) + " ms, screen setup "
						+ (total / 1_000_000) + " ms in " + frames + " frames");
				if (reportStart != 0) {
					reportStart += between;
				}
				frameStart = 0;
				lastRenderEnd = 0;
			}
		}
		lastLoadingFrame = 0;
		worstLoadingFrame = 0;
	}

	/** A frame of the menu's loading screen, drawn while a game loads. */
	public static void loadingFrame() {
		if (ENABLED) {
			final long now = System.nanoTime();
			if (lastLoadingFrame != 0) {
				worstLoadingFrame = Math.max(worstLoadingFrame, now - lastLoadingFrame);
			}
			lastLoadingFrame = now;
		}
	}

	/** The time of one part of a setup step that runs in a single frame, to see where the frame goes. */
	public static void setupPart(final String what, final long startNanos) {
		if (ENABLED) {
			System.err.println("Setup part: " + what + " " + ((System.nanoTime() - startNanos) / 1_000_000) + " ms");
		}
	}

	/** {@link #setupPart} for what is worth a line only when it is slow. */
	public static void slowSetupPart(final String what, final long startNanos) {
		checkSwitchFile();
		if (ENABLED && ((System.nanoTime() - startNanos) >= 20_000_000L)) {
			setupPart(what, startNanos);
		}
	}

	public static void openingStepsDone(final int steps, final long nanos) {
		if (ENABLED) {
			System.err.println("Opening steps: " + steps + " steps and the sounds of their units in " + (nanos / 1_000_000)
					+ " ms");
		}
	}

	/** What opening the game data's storage took: the storage itself, then waiting for the list of its paths. */
	public static void cascOpened(final long start, final long storageEnd, final long listEnd, final int paths,
			final boolean listSaved) {
		if (ENABLED) {
			System.err.println("CASC open: total " + ((listEnd - start) / 1_000_000) + " ms, storage "
					+ ((storageEnd - start) / 1_000_000) + " ms, waiting for path list "
					+ ((listEnd - storageEnd) / 1_000_000) + " ms (" + paths + " paths, "
					+ (listSaved ? "read from disk" : "read from the storage") + ")");
		}
	}

	/** Whether the switch file is in this folder (the application's local files; the working folder on a desktop). */
	public static boolean isSwitchedOn(final java.io.File folder) {
		return new java.io.File(folder, SWITCH_FILE).isFile();
	}

	/**
	 * Turns the logging on or off the way the switch file does, from the launcher, before any game runs: the file is
	 * made or removed in the folder, and this run follows it. Options the file holds ("finish", "glmem") are kept.
	 */
	public static synchronized void setSwitch(final java.io.File folder, final boolean on) {
		final java.io.File file = new java.io.File(folder, SWITCH_FILE);
		try {
			if (on) {
				if (!file.exists()) {
					folder.mkdirs();
					java.nio.file.Files.write(file.toPath(), new byte[0]);
				}
			}
			else {
				java.nio.file.Files.deleteIfExists(file.toPath());
			}
		}
		catch (final java.io.IOException e) {
			Log.warn("The performance log switch could not be changed: " + e);
			return;
		}
		ENABLED = on || (System.getProperty("warsmash.perf") != null);
		checkedLocalFile = true;
	}

	/**
	 * The start of a frame whose first work comes before the viewer's own update (the interface's update); the
	 * viewer's frameStart that follows then does not start a second one.
	 */
	public static void beginFrame() {
		frameStart();
		frameBegun = ENABLED;
	}

	/**
	 * Reads the switch file once. The game screen's setup runs before its first frame, and its report must not wait
	 * for the first frame to find the switch.
	 */
	public static void checkSwitchFile() {
		if (!checkedLocalFile) {
			checkedLocalFile = true;
			if ((Gdx.files != null) && Gdx.files.local("warsmash.perf").exists()) {
				ENABLED = true;
				try {
					finish |= Gdx.files.local("warsmash.perf").readString().contains("finish");
				}
				catch (final RuntimeException e) {
					// the file is only a switch; unreadable means no options
				}
			}
		}
	}

	public static void frameStart() {
		checkSwitchFile();
		if (!ENABLED) {
			return;
		}
		if (frameBegun) {
			frameBegun = false;
			return;
		}
		final long now = System.nanoTime();
		if (lastRenderEnd != 0) {
			addSection(Section.SWAP, now - lastRenderEnd);
			lastRenderEnd = 0;
		}
		final long cpu = cpuNanos();
		final long gcCount = runtimeStat("art.gc.gc-count");
		final long gcBlocking = runtimeStat("art.gc.blocking-gc-time") * 1_000_000L;
		if (frameStart != 0) {
			final long frame = now - frameStart;
			cpuTotal += cpu - cpuStart;
			simGapTotal += simulationGap(FRAME_SECTIONS);
			frameGcCount = gcCount - gcCountAtFrame;
			frameGcBlockingNanos = gcBlocking - gcBlockingAtFrame;
			drawCalls += frameDrawCalls;
			if (frameDrawCalls > drawCallsWorst) {
				drawCallsWorst = frameDrawCalls;
			}
			frameTotal += frame;
			if (frame > worstFrame) {
				worstFrame = frame;
				System.arraycopy(FRAME_SECTIONS, 0, WORST_SECTIONS, 0, FRAME_SECTIONS.length);
			}
			frames++;
			final int bucket = (int) Math.min(WINDOW_HISTOGRAM.length - 1, frame / 1_000_000L);
			WINDOW_HISTOGRAM[bucket]++;
			RUN_HISTOGRAM[bucket]++;
			if (frame >= SPIKE_NANOS) {
				spike(now, frame);
			}
		}
		else {
			firstFrame = now;
		}
		Arrays.fill(FRAME_SECTIONS, 0);
		frameDrawCalls = 0;
		simCpuFrame = 0;
		cpuStart = cpu;
		gcCountAtFrame = gcCount;
		gcBlockingAtFrame = gcBlocking;
		frameLoads = 0;
		frameLoadNanos = 0;
		frameTextureNanos = 0;
		slowestLoadNanos = 0;
		slowestLoad = null;
		if (reportStart == 0) {
			reportStart = now;
		}
		if ((now - reportStart) >= REPORT_NANOS) {
			report(now);
		}
		frameStart = now;
		sectionStart = now;
	}

	public static void mark() {
		if (ENABLED) {
			sectionStart = System.nanoTime();
		}
	}

	public static void end(final Section section) {
		if (!ENABLED) {
			return;
		}
		final long now = System.nanoTime();
		TOTALS[section.ordinal()] += now - sectionStart;
		FRAME_SECTIONS[section.ordinal()] += now - sectionStart;
		sectionStart = now;
		if (section == Section.WIDGETS) {
			simCpuStart = cpuNanos();
		}
		else if (section == Section.SIMULATION) {
			simCpuFrame = cpuNanos() - simCpuStart;
			simCpuTotal += simCpuFrame;
		}
	}

	private static void addSection(final Section section, final long nanos) {
		TOTALS[section.ordinal()] += nanos;
		FRAME_SECTIONS[section.ordinal()] += nanos;
	}

	/** The end of the screen's render, before the swap of the buffers that follows; the wait for it is a section. */
	public static void renderEnd() {
		if (ENABLED) {
			lastRenderEnd = System.nanoTime();
		}
	}

	/** Waits for the GPU when asked to (warsmash.perf.finish, or "finish" in the warsmash.perf file). */
	public static boolean finishWanted() {
		return ENABLED && finish;
	}

	/** One draw call of the world's scenes; the interface's are counted by its batch. */
	public static void draw() {
		if (ENABLED) {
			frameDrawCalls++;
		}
	}

	private static boolean probe() {
		if (!probedRuntime) {
			probedRuntime = true;
			try {
				final Class<?> debug = Class.forName("android.os.Debug");
				cpuMethod = debug.getMethod("threadCpuTimeNanos");
				statMethod = debug.getMethod("getRuntimeStat", String.class);
			}
			catch (final ReflectiveOperationException | LinkageError e) {
				try {
					final Class<?> factory = Class.forName("java.lang.management.ManagementFactory");
					cpuTarget = factory.getMethod("getThreadMXBean").invoke(null);
					cpuMethod = Class.forName("java.lang.management.ThreadMXBean").getMethod("getCurrentThreadCpuTime");
				}
				catch (final ReflectiveOperationException | LinkageError | RuntimeException e2) {
					cpuMethod = null;
				}
			}
		}
		return true;
	}

	/** CPU time of the game thread; the wall time of a section minus it is time spent waiting or preempted. */
	private static long cpuNanos() {
		probe();
		try {
			return cpuMethod == null ? 0 : (Long) cpuMethod.invoke(cpuTarget);
		}
		catch (final ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	/** A counter of the runtime's garbage collector (Android only: art.gc.*), 0 where there is none. */
	private static long runtimeStat(final String key) {
		probe();
		try {
			return statMethod == null ? 0 : Long.parseLong((String) statMethod.invoke(null, key));
		}
		catch (final ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	public static void stepMark() {
		if (ENABLED) {
			stepStart = System.nanoTime();
		}
	}

	public static void stepEnd(final Step step) {
		if (ENABLED) {
			final long now = System.nanoTime();
			STEP_TOTALS[step.ordinal()] += now - stepStart;
			FRAME_SECTIONS[SECTION_COUNT + step.ordinal()] += now - stepStart;
			stepStart = now;
		}
	}

	public static void addStep(final Step step, final long nanos) {
		STEP_TOTALS[step.ordinal()] += nanos;
		FRAME_SECTIONS[SECTION_COUNT + step.ordinal()] += nanos;
	}

	/** A file the game thread loaded while playing, which a slow frame then names. */
	public static void loaded(final String path, final long nanos) {
		frameLoads++;
		final int dot = path.lastIndexOf('.');
		if ((dot != -1) && (path.regionMatches(true, dot, ".blp", 0, 4) || path.regionMatches(true, dot, ".dds", 0, 4))) {
			frameTextureNanos += nanos;
		}
		else {
			frameLoadNanos += nanos;
		}
		if (nanos >= SLOW_LOAD_NANOS) {
			Gdx.app.log("PERF", String.format(Locale.ROOT, "slow load %.1fms %s", nanos / 1e6, path));
		}
		if (nanos > slowestLoadNanos) {
			slowestLoadNanos = nanos;
			slowestLoad = path;
		}
	}

	private static void spike(final long now, final long frame) {
		final StringBuilder line = new StringBuilder();
		line.append(String.format(Locale.ROOT, "SPIKE at=%.1fs frame=%.1fms", (now - firstFrame) / 1e9, frame / 1e6));
		final Section[] sections = Section.values();
		final Step[] steps = Step.values();
		long covered = 0;
		for (int i = 0; i < sections.length; i++) {
			covered += FRAME_SECTIONS[i];
		}
		line.append(String.format(Locale.ROOT, " other=%.1f", (frame - covered) / 1e6));
		for (int i = 0; i < FRAME_SECTIONS.length; i++) {
			if (FRAME_SECTIONS[i] >= 1_000_000L) {
				final String name = i < sections.length ? sections[i].name() : steps[i - sections.length].name();
				line.append(' ').append(name.toLowerCase(Locale.ROOT)).append('=')
						.append(String.format(Locale.ROOT, "%.1f", FRAME_SECTIONS[i] / 1e6));
			}
		}
		line.append(String.format(Locale.ROOT, " simgap=%.1f simcpu=%.1f draws=%d", simulationGap(FRAME_SECTIONS) / 1e6,
				simCpuFrame / 1e6, frameDrawCalls));
		if (frameGcCount > 0) {
			line.append(String.format(Locale.ROOT, " gc=%d gcblock=%.1f", frameGcCount, frameGcBlockingNanos / 1e6));
		}
		if (frameLoads > 0) {
			line.append(String.format(Locale.ROOT, " loads=%d files=%.1fms textures=%.1fms slowest=%.1fms %s", frameLoads,
					frameLoadNanos / 1e6, frameTextureNanos / 1e6, slowestLoadNanos / 1e6, slowestLoad));
		}
		Gdx.app.log("PERF", line.toString());
	}

	/**
	 * What the simulation section took that no step accounts for: the game steps' own sections added up (the fog
	 * update runs inside the timers, so it is left out).
	 */
	private static long simulationGap(final long[] sections) {
		long steps = 0;
		for (final Step step : Step.values()) {
			if (step != Step.FOG) {
				steps += sections[SECTION_COUNT + step.ordinal()];
			}
		}
		return sections[Section.SIMULATION.ordinal()] - steps;
	}

	private static int percentile(final int[] histogram, final long count, final double fraction) {
		long seen = 0;
		final long target = (long) Math.ceil(count * fraction);
		for (int i = 0; i < histogram.length; i++) {
			seen += histogram[i];
			if (seen >= target) {
				return i + 1;
			}
		}
		return histogram.length;
	}

	private static long over(final int[] histogram, final int millis) {
		long count = 0;
		for (int i = millis; i < histogram.length; i++) {
			count += histogram[i];
		}
		return count;
	}

	public static void simulationStep() {
		simulationSteps++;
	}

	public static void pathJob() {
		pathJobs++;
	}

	public static void pathIteration() {
		pathIterations++;
	}

	/** A search a human order has thrown away: the iterations and the time it had spent. */
	public static void pathDiscarded(final long iterations, final long nanos) {
		pathDiscarded++;
		pathDiscardedIterations += iterations;
		pathDiscardedNanos += nanos;
		final String message = String.format(Locale.ROOT, "path search discarded iterations=%d ms=%.3f", iterations,
				nanos / 1e6);
		if (Gdx.app != null) {
			Gdx.app.log("PERF", message);
		}
		else {
			System.out.println("PERF " + message);
		}
	}

	public static void interfaceDraws(final int draws) {
		interfaceDraws += draws;
	}

	private static void report(final long now) {
		final StringBuilder line = new StringBuilder();
		final double seconds = (now - reportStart) / 1e9;
		line.append(String.format(Locale.ROOT, "fps=%.1f avg=%.1fms worst=%.1fms steps/s=%.1f paths/s=%.1f iterations/s=%.0f",
				frames / seconds, frames == 0 ? 0 : frameTotal / 1e6 / frames, worstFrame / 1e6,
				simulationSteps / seconds, pathJobs / seconds, pathIterations / seconds));
		line.append(String.format(Locale.ROOT, " ui_draws=%.0f draws=%.0f worst_draws=%d cpu=%.2f sim_cpu=%.2f p50=%dms",
				frames == 0 ? 0 : (double) interfaceDraws / frames, frames == 0 ? 0 : (double) drawCalls / frames,
				drawCallsWorst, frames == 0 ? 0 : cpuTotal / 1e6 / frames, frames == 0 ? 0 : simCpuTotal / 1e6 / frames,
				percentile(WINDOW_HISTOGRAM, frames, 0.5)));
		if (pathDiscarded > 0) {
			line.append(String.format(Locale.ROOT, " path_discarded=%d discarded_iterations=%d discarded_ms=%.1f",
					pathDiscarded, pathDiscardedIterations, pathDiscardedNanos / 1e6));
		}
		final long gcCount = runtimeStat("art.gc.gc-count");
		final long gcTime = runtimeStat("art.gc.gc-time");
		final long gcBlocking = runtimeStat("art.gc.blocking-gc-time");
		final long allocated = runtimeStat("art.gc.bytes-allocated");
		line.append(String.format(Locale.ROOT, " gc_n=%d gc_ms=%d gc_block_ms=%d alloc_mb_s=%.1f", gcCount - gcCountAtReport,
				gcTime - gcTimeAtReport, gcBlocking - gcBlockingAtReport, (allocated - allocatedAtReport) / 1048576.0 / seconds));
		if (Gdx.app != null) {
			line.append(String.format(Locale.ROOT, " java_heap_mb=%.0f native_heap_mb=%.0f", Gdx.app.getJavaHeap() / 1048576.0,
					Gdx.app.getNativeHeap() / 1048576.0));
		}
		line.append(String.format(Locale.ROOT, " texture_mb=%.0f evicted=%d models_evicted=%d", textureBytes / 1048576.0, evictedTextures,
				evictedModels));
		if (GlMemory.ON) {
			line.append(' ').append(GlMemory.summary());
			if (Gdx.app != null) {
				Gdx.app.log("GLMEM", GlMemory.owners());
				Gdx.app.log("GLMEM", GlMemory.shape());
				Gdx.app.log("GLMEM", com.etheller.warsmash.parsers.fdf.FontGeneratorHolder.report());
			}
		}
		gcCountAtReport = gcCount;
		gcTimeAtReport = gcTime;
		gcBlockingAtReport = gcBlocking;
		allocatedAtReport = allocated;
		long runFrames = 0;
		for (final int count : RUN_HISTOGRAM) {
			runFrames += count;
		}
		line.append(String.format(Locale.ROOT, " p99=%dms over33=%d over50=%d | run p99=%dms p999=%dms over33=%d over50=%d over100=%d",
				percentile(WINDOW_HISTOGRAM, frames, 0.99), over(WINDOW_HISTOGRAM, 33), over(WINDOW_HISTOGRAM, 50),
				percentile(RUN_HISTOGRAM, runFrames, 0.99), percentile(RUN_HISTOGRAM, runFrames, 0.999),
				over(RUN_HISTOGRAM, 33), over(RUN_HISTOGRAM, 50), over(RUN_HISTOGRAM, 100)));
		Arrays.fill(WINDOW_HISTOGRAM, 0);
		for (final Section section : Section.values()) {
			final long total = TOTALS[section.ordinal()];
			line.append(' ').append(section.name().toLowerCase(Locale.ROOT)).append('=')
					.append(String.format(Locale.ROOT, "%.2f", frames == 0 ? 0 : total / 1e6 / frames));
			TOTALS[section.ordinal()] = 0;
		}
		line.append(String.format(Locale.ROOT, " sim_gap=%.2f", frames == 0 ? 0 : simGapTotal / 1e6 / frames));
		simGapTotal = 0;
		line.append(" |");
		for (final Step step : Step.values()) {
			final long total = STEP_TOTALS[step.ordinal()];
			line.append(' ').append(step.name().toLowerCase(Locale.ROOT)).append('=')
					.append(String.format(Locale.ROOT, "%.2f", frames == 0 ? 0 : total / 1e6 / frames));
			STEP_TOTALS[step.ordinal()] = 0;
		}
		line.append(" | worst:");
		final Section[] sections = Section.values();
		final Step[] steps = Step.values();
		long covered = 0;
		for (int i = 0; i < sections.length; i++) {
			covered += WORST_SECTIONS[i];
		}
		line.append(String.format(Locale.ROOT, " other=%.1f simgap=%.1f", (worstFrame - covered) / 1e6,
				simulationGap(WORST_SECTIONS) / 1e6));
		for (int i = 0; i < WORST_SECTIONS.length; i++) {
			if (WORST_SECTIONS[i] >= 2_000_000L) {
				final String name = i < sections.length ? sections[i].name() : steps[i - sections.length].name();
				line.append(' ').append(name.toLowerCase(Locale.ROOT)).append('=')
						.append(String.format(Locale.ROOT, "%.1f", WORST_SECTIONS[i] / 1e6));
			}
		}
		Gdx.app.log("PERF", line.toString());
		frames = 0;
		frameTotal = 0;
		worstFrame = 0;
		simulationSteps = 0;
		pathJobs = 0;
		pathIterations = 0;
		pathDiscarded = 0;
		pathDiscardedIterations = 0;
		pathDiscardedNanos = 0;
		interfaceDraws = 0;
		drawCalls = 0;
		drawCallsWorst = 0;
		cpuTotal = 0;
		simCpuTotal = 0;
		reportStart = now;
	}

	private PerfStats() {
	}
}
