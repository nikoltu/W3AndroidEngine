package com.etheller.warsmash.util;

import java.util.Arrays;

import com.badlogic.gdx.Gdx;

/**
 * Frame time split by section, logged every few seconds while the "warsmash.perf" system property is set or, where
 * there are no command line properties (Android), a file named warsmash.perf is in the application's local files.
 */
public final class PerfStats {
	public enum Section {
		TERRAIN_UPDATE, SCENE_UPDATE, WIDGETS, SIMULATION, SHADOWS, GROUND, CLIFFS, OPAQUE, SPLATS, WATER, TRANSLUCENT,
		INTERFACE
	}

	public enum Step {
		UNITS, PROJECTILES, PATHING, TIMERS, JASS, AI, FOG
	}

	public static boolean ENABLED = System.getProperty("warsmash.perf") != null;
	private static boolean checkedLocalFile;

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
	private static long interfaceDraws;

	public static void frameStart() {
		if (!checkedLocalFile) {
			checkedLocalFile = true;
			ENABLED |= (Gdx.files != null) && Gdx.files.local("warsmash.perf").exists();
		}
		if (!ENABLED) {
			return;
		}
		final long now = System.nanoTime();
		if (frameStart != 0) {
			final long frame = now - frameStart;
			frameTotal += frame;
			if (frame > worstFrame) {
				worstFrame = frame;
				System.arraycopy(FRAME_SECTIONS, 0, WORST_SECTIONS, 0, FRAME_SECTIONS.length);
			}
			frames++;
		}
		Arrays.fill(FRAME_SECTIONS, 0);
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

	public static void simulationStep() {
		simulationSteps++;
	}

	public static void pathJob() {
		pathJobs++;
	}

	public static void pathIteration() {
		pathIterations++;
	}

	public static void interfaceDraws(final int draws) {
		interfaceDraws += draws;
	}

	private static void report(final long now) {
		final StringBuilder line = new StringBuilder();
		final double seconds = (now - reportStart) / 1e9;
		line.append(String.format("fps=%.1f avg=%.1fms worst=%.1fms steps/s=%.1f paths/s=%.1f iterations/s=%.0f",
				frames / seconds, frames == 0 ? 0 : frameTotal / 1e6 / frames, worstFrame / 1e6,
				simulationSteps / seconds, pathJobs / seconds, pathIterations / seconds));
		line.append(String.format(" ui_draws=%.0f", frames == 0 ? 0 : (double) interfaceDraws / frames));
		for (final Section section : Section.values()) {
			final long total = TOTALS[section.ordinal()];
			line.append(' ').append(section.name().toLowerCase()).append('=')
					.append(String.format("%.2f", frames == 0 ? 0 : total / 1e6 / frames));
			TOTALS[section.ordinal()] = 0;
		}
		line.append(" |");
		for (final Step step : Step.values()) {
			final long total = STEP_TOTALS[step.ordinal()];
			line.append(' ').append(step.name().toLowerCase()).append('=')
					.append(String.format("%.2f", frames == 0 ? 0 : total / 1e6 / frames));
			STEP_TOTALS[step.ordinal()] = 0;
		}
		line.append(" | worst:");
		final Section[] sections = Section.values();
		final Step[] steps = Step.values();
		long covered = 0;
		for (int i = 0; i < sections.length; i++) {
			covered += WORST_SECTIONS[i];
		}
		line.append(String.format(" other=%.1f", (worstFrame - covered) / 1e6));
		for (int i = 0; i < WORST_SECTIONS.length; i++) {
			if (WORST_SECTIONS[i] >= 2_000_000L) {
				final String name = i < sections.length ? sections[i].name() : steps[i - sections.length].name();
				line.append(' ').append(name.toLowerCase()).append('=')
						.append(String.format("%.1f", WORST_SECTIONS[i] / 1e6));
			}
		}
		Gdx.app.log("PERF", line.toString());
		frames = 0;
		frameTotal = 0;
		worstFrame = 0;
		simulationSteps = 0;
		pathJobs = 0;
		pathIterations = 0;
		interfaceDraws = 0;
		reportStart = now;
	}

	private PerfStats() {
	}
}
