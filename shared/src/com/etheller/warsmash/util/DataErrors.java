package com.etheller.warsmash.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects data problems (missing assets, bad or unknown data entries) seen while loading, so that a run ends with
 * one report of each distinct problem and how often it occurred instead of only a scattered trail of warnings. Each
 * occurrence is still logged as a warning when it happens.
 */
public final class DataErrors {
	public static final String MISSING_ASSET = "missing asset";
	public static final String MISSING_SOUND = "missing sound";
	public static final String BAD_ENTRY = "bad data entry";
	public static final String UNKNOWN_ID = "unknown id";
	public static final String MISSING_STRING = "missing string";
	public static final String BAD_TEXTURE = "undecodable texture";

	private static final int MAX_ENTRIES = 4096;
	private static final Map<String, int[]> COUNTS = new LinkedHashMap<>();
	private static int dropped;

	private DataErrors() {
	}

	/** Logs the message as a warning and records the problem under its category and subject. */
	public static void report(final String category, final String subject, final Object message) {
		Log.warn(message);
		record(category, subject);
	}

	public static void record(final String category, final String subject) {
		final String key = category + ": " + subject;
		synchronized (COUNTS) {
			final int[] count = COUNTS.get(key);
			if (count != null) {
				count[0]++;
			}
			else if (COUNTS.size() < MAX_ENTRIES) {
				COUNTS.put(key, new int[] { 1 });
			}
			else {
				dropped++;
			}
		}
	}

	public static int size() {
		synchronized (COUNTS) {
			return COUNTS.size();
		}
	}

	/** The distinct problems recorded since the last report, each with its count. */
	public static List<String> entries() {
		synchronized (COUNTS) {
			final List<String> lines = new ArrayList<>(COUNTS.size());
			for (final Map.Entry<String, int[]> entry : COUNTS.entrySet()) {
				final int count = entry.getValue()[0];
				lines.add(count > 1 ? entry.getKey() + " (x" + count + ")" : entry.getKey());
			}
			return lines;
		}
	}

	/** Logs the collected problems once and starts a new collection; does nothing when there are none. */
	public static void logReport(final String title) {
		final List<String> lines;
		final int droppedNow;
		synchronized (COUNTS) {
			if (COUNTS.isEmpty() && (dropped == 0)) {
				return;
			}
			lines = entries();
			droppedNow = dropped;
			COUNTS.clear();
			dropped = 0;
		}
		final StringBuilder report = new StringBuilder();
		report.append("Data report (").append(title).append("): ").append(lines.size()).append(" distinct problems");
		for (final String line : lines) {
			report.append(System.lineSeparator()).append("  ").append(line);
		}
		if (droppedNow > 0) {
			report.append(System.lineSeparator()).append("  ... and ").append(droppedNow).append(" more not listed");
		}
		Log.warn(report);
	}

	public static void clear() {
		synchronized (COUNTS) {
			COUNTS.clear();
			dropped = 0;
		}
	}
}
