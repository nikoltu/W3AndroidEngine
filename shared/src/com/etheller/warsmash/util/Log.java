package com.etheller.warsmash.util;

import java.io.PrintStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Leveled engine log. Errors and warnings go to the standard error stream, info and debug to the standard output
 * stream, with the message text unchanged, so the per-run log files and the Android log keep their format.
 *
 * The threshold comes from the system property "warsmash.log" (error, warn, info, debug, off); the default shows
 * every level.
 */
public final class Log {
	public enum Level {
		OFF, ERROR, WARN, INFO, DEBUG;
	}

	private static volatile Level threshold = parseLevel(System.getProperty("warsmash.log"), Level.DEBUG);
	private static final AtomicInteger loggedThrowables = new AtomicInteger();

	private Log() {
	}

	public static Level parseLevel(final String name, final Level fallback) {
		if (name == null) {
			return fallback;
		}
		try {
			return Level.valueOf(name.trim().toUpperCase(Locale.US));
		}
		catch (final IllegalArgumentException e) {
			return fallback;
		}
	}

	/** How many exceptions were logged so far, whatever the threshold, for tests that expect none. */
	public static int getLoggedThrowableCount() {
		return loggedThrowables.get();
	}

	public static Level getLevel() {
		return threshold;
	}

	public static void setLevel(final Level level) {
		threshold = level;
	}

	public static boolean isEnabled(final Level level) {
		return (level != Level.OFF) && (level.ordinal() <= threshold.ordinal());
	}

	public static void error(final Object message) {
		write(Level.ERROR, message, null);
	}

	public static void error(final Object message, final Throwable throwable) {
		write(Level.ERROR, message, throwable);
	}

	public static void error(final Throwable throwable) {
		write(Level.ERROR, null, throwable);
	}

	public static void warn(final Object message) {
		write(Level.WARN, message, null);
	}

	public static void warn(final Object message, final Throwable throwable) {
		write(Level.WARN, message, throwable);
	}

	public static void warn(final Throwable throwable) {
		write(Level.WARN, null, throwable);
	}

	public static void info(final Object message) {
		write(Level.INFO, message, null);
	}

	public static void debug(final Object message) {
		write(Level.DEBUG, message, null);
	}

	public static void debug(final Object message, final Throwable throwable) {
		write(Level.DEBUG, message, throwable);
	}

	public static void errorf(final String format, final Object... args) {
		if (isEnabled(Level.ERROR)) {
			System.err.printf(format, args);
		}
	}

	public static void infof(final String format, final Object... args) {
		if (isEnabled(Level.INFO)) {
			System.out.printf(format, args);
		}
	}

	/** Writes without a line break, for output assembled from several parts. */
	public static void infoPart(final Object part) {
		if (isEnabled(Level.INFO)) {
			System.out.print(part);
		}
	}

	private static void write(final Level level, final Object message, final Throwable throwable) {
		if (throwable != null) {
			loggedThrowables.incrementAndGet();
		}
		if (!isEnabled(level)) {
			return;
		}
		// The streams are looked up on every call: launchers redirect them to per-run files after start.
		final PrintStream stream = level.ordinal() <= Level.WARN.ordinal() ? System.err : System.out;
		if (throwable == null) {
			stream.println(message);
			return;
		}
		synchronized (stream) {
			if (message != null) {
				stream.println(message);
			}
			throwable.printStackTrace(stream);
		}
	}
}
