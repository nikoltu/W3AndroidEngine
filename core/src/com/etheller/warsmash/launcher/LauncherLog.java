package com.etheller.warsmash.launcher;

import com.etheller.warsmash.util.Log;

/** Where the launcher's warnings go: the platform's log, standard error until a platform sets one. */
public final class LauncherLog {
	public interface Sink {
		void log(int level, String message, Throwable error);
	}

	public static final int INFO = 0;
	public static final int WARN = 1;
	public static final int ERROR = 2;

	private static volatile Sink sink = (level, message, error) -> {
		final String text = (level == ERROR ? "E " : level == WARN ? "W " : "I ") + message;
		if (level == INFO) {
			Log.info(text);
		}
		else if (level == WARN) {
			Log.warn(text, error);
		}
		else {
			Log.error(text, error);
		}
	};

	private LauncherLog() {
	}

	public static void setSink(final Sink sink) {
		LauncherLog.sink = sink;
	}

	public static void i(final String message) {
		sink.log(INFO, message, null);
	}

	public static void w(final String message) {
		sink.log(WARN, message, null);
	}

	public static void w(final String message, final Throwable error) {
		sink.log(WARN, message, error);
	}

	public static void e(final String message, final Throwable error) {
		sink.log(ERROR, message, error);
	}
}
