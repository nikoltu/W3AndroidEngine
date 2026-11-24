package com.etheller.warsmash.launcher;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The crash log the player can keep and attach to a bug report: the file names and how many are kept, and the text. It
 * holds what the player was told it holds, and nothing the player typed or owns (no names, no folders of the data).
 */
public final class CrashReport {
	/** How many crash logs are kept; the older ones are deleted when a new one is written. */
	public static final int KEEP = 5;
	/** The lines of the app's own log that a report carries. */
	public static final int LOG_LINES = 2000;
	private static final String PREFIX = "crash-";
	private static final String SUFFIX = ".txt";
	private static final String SNAPSHOT_PREFIX = "log-";
	private static final String RENDERER_MARK = "Renderer: ";
	private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
	private static final DateTimeFormatter TEXT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z",
			Locale.ROOT);

	/** What is known when the program crashes; any text may be null when it could not be found out. */
	public static final class Details {
		public long time;
		public String thread;
		public Throwable error;
		/** The last error the engine logged and recovered from, as its message and exception. */
		public String lastLoggedMessage;
		public Throwable lastLogged;
		public String appVersion;
		public String commit;
		public String device;
		public String androidVersion;
		public String renderer;
		public String graphics;
		public String map;
		/** What the system and the process held at the last moments before, one line each; see the Android app's memory notes. */
		public String memory;
		public List<String> log;
		/** Set when the system ended the process without an exception: what Android recorded about that exit. */
		public String exit;
		/** The system's trace of that exit (a native crash or an application-not-responding), if it kept one. */
		public String exitTrace;
	}

	private CrashReport() {
	}

	/** The name of a log written at this time, which sorts by time. */
	public static String fileName(final long timeMillis, final ZoneId zone) {
		return PREFIX + NAME_TIME.format(Instant.ofEpochMilli(timeMillis).atZone(zone)) + SUFFIX;
	}

	public static boolean isCrashLog(final String name) {
		return isPlainName(name, PREFIX);
	}

	/** The name of a log snapshot the player shares on demand, made at this time. */
	public static String snapshotName(final long timeMillis, final ZoneId zone) {
		return SNAPSHOT_PREFIX + NAME_TIME.format(Instant.ofEpochMilli(timeMillis).atZone(zone)) + SUFFIX;
	}

	public static boolean isSnapshot(final String name) {
		return isPlainName(name, SNAPSHOT_PREFIX);
	}

	/** Every file the share provider may serve: crash logs and snapshots. */
	public static boolean isShareable(final String name) {
		return isCrashLog(name) || isSnapshot(name);
	}

	private static boolean isPlainName(final String name, final String prefix) {
		return (name != null) && name.startsWith(prefix) && name.endsWith(SUFFIX) && (name.indexOf('/') < 0)
				&& (name.indexOf('\\') < 0) && (name.length() > (prefix.length() + SUFFIX.length()));
	}

	/**
	 * The text shared from the launcher at any time: the same facts a crash log carries (without an error), the app log
	 * of this run, and the newest crash log when there is one.
	 */
	public static String snapshotText(final Details details, final String latestCrashLog, final ZoneId zone) {
		final StringBuilder out = new StringBuilder(8192);
		out.append("Warsmash log\n");
		line(out, "Time", TEXT_TIME.format(Instant.ofEpochMilli(details.time).atZone(zone)));
		line(out, "App version", details.appVersion);
		line(out, "Commit", details.commit);
		line(out, "Device", details.device);
		line(out, "Android", details.androidVersion);
		line(out, "GPU", details.renderer);
		line(out, "Graphics", details.graphics);
		out.append("\nApp log of this run (newest last)\n");
		if (details.log != null) {
			final int from = Math.max(0, details.log.size() - LOG_LINES);
			for (int i = from; i < details.log.size(); i++) {
				out.append(details.log.get(i)).append('\n');
			}
		}
		if ((latestCrashLog != null) && !latestCrashLog.isEmpty()) {
			out.append("\nLatest saved crash log\n").append(latestCrashLog).append('\n');
		}
		return out.toString();
	}

	/** The names that go once only the newest {@code keep} crash logs may stay; other files are never listed. */
	public static List<String> expired(final Collection<String> names, final int keep) {
		final List<String> logs = new ArrayList<>();
		for (final String name : names) {
			if (isCrashLog(name)) {
				logs.add(name);
			}
		}
		logs.sort(null);
		final int count = Math.max(0, logs.size() - Math.max(0, keep));
		return new ArrayList<>(logs.subList(0, count));
	}

	/** The newest of the names that are crash logs, or null. */
	public static String newest(final Collection<String> names) {
		String best = null;
		for (final String name : names) {
			if (isCrashLog(name) && ((best == null) || (name.compareTo(best) > 0))) {
				best = name;
			}
		}
		return best;
	}

	/** The graphics card the engine named in its own log (it logs it when the menu and a map start), or null. */
	public static String rendererFrom(final List<String> logLines) {
		if (logLines == null) {
			return null;
		}
		for (int i = logLines.size() - 1; i >= 0; i--) {
			final String line = logLines.get(i);
			final int at = line.lastIndexOf(RENDERER_MARK);
			// Android's own "OpenGLRenderer: " lines end with the same text.
			if ((at >= 0) && ((at == 0) || !Character.isLetter(line.charAt(at - 1)))) {
				return line.substring(at + RENDERER_MARK.length()).trim();
			}
		}
		return null;
	}

	public static String text(final Details details, final ZoneId zone) {
		final StringBuilder out = new StringBuilder(8192);
		out.append("Warsmash crash log\n");
		line(out, "Time", TEXT_TIME.format(Instant.ofEpochMilli(details.time).atZone(zone)));
		line(out, "App version", details.appVersion);
		line(out, "Commit", details.commit);
		line(out, "Device", details.device);
		line(out, "Android", details.androidVersion);
		line(out, "GPU", details.renderer);
		line(out, "Graphics", details.graphics);
		line(out, "Map", details.map);
		line(out, "Thread", details.thread);
		if ((details.memory != null) && !details.memory.isEmpty()) {
			out.append("\nMemory (newest last)\n").append(details.memory).append('\n');
		}
		if (details.exit != null) {
			out.append("\nProcess exit (no exception was caught; the system ended the process)\n");
			out.append(details.exit).append('\n');
			if ((details.exitTrace != null) && !details.exitTrace.isEmpty()) {
				out.append("\nSystem trace of the exit\n").append(details.exitTrace).append('\n');
			}
			return out.toString();
		}
		out.append("\nError\n");
		out.append(trace(details.error)).append('\n');
		if ((details.lastLogged != null) || (details.lastLoggedMessage != null)) {
			out.append("\nLast error the game logged before\n");
			if (details.lastLoggedMessage != null) {
				out.append(details.lastLoggedMessage).append('\n');
			}
			if (details.lastLogged != null) {
				out.append(trace(details.lastLogged)).append('\n');
			}
		}
		out.append("\nApp log (newest last)\n");
		if (details.log != null) {
			final int from = Math.max(0, details.log.size() - LOG_LINES);
			for (int i = from; i < details.log.size(); i++) {
				out.append(details.log.get(i)).append('\n');
			}
		}
		return out.toString();
	}

	/**
	 * The text of a system trace: plain text as it is, a binary one (a native crash from Android 12 on is a protocol
	 * buffer) as the runs of readable characters in it.
	 */
	public static String traceText(final byte[] bytes, final int length) {
		boolean binary = false;
		for (int i = 0; i < Math.min(length, 512); i++) {
			if (bytes[i] == 0) {
				binary = true;
				break;
			}
		}
		if (!binary) {
			return new String(bytes, 0, length, java.nio.charset.StandardCharsets.UTF_8);
		}
		final StringBuilder out = new StringBuilder();
		int start = -1;
		for (int i = 0; i <= length; i++) {
			final boolean readable = (i < length) && (((bytes[i] >= 32) && (bytes[i] < 127)) || (bytes[i] == '\t'));
			if (readable) {
				if (start < 0) {
					start = i;
				}
			}
			else if (start >= 0) {
				if ((i - start) >= 4) {
					out.append(new String(bytes, start, i - start, java.nio.charset.StandardCharsets.US_ASCII))
							.append('\n');
				}
				start = -1;
			}
		}
		return out.toString();
	}

	private static void line(final StringBuilder out, final String name, final String value) {
		out.append(name).append(": ").append((value == null) || value.isEmpty() ? "unknown" : value).append('\n');
	}

	private static String trace(final Throwable error) {
		if (error == null) {
			return "none";
		}
		final StringWriter text = new StringWriter();
		try (PrintWriter writer = new PrintWriter(text)) {
			error.printStackTrace(writer);
		}
		return text.toString().trim();
	}
}
