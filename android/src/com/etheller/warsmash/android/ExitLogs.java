package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.LauncherKeys.PREFERENCES;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.etheller.warsmash.BuildInfo;
import com.etheller.warsmash.launcher.CrashReport;

/**
 * A process the system ended without an exception (the low-memory killer, a native crash, an application-not-responding
 * kill) leaves no crash log of its own. From Android 11 on the system keeps a record of how each process ended; the
 * next start turns an abnormal one into a crash log, so that a game that closed silently is in the bug report.
 */
final class ExitLogs {
	private static final String TAG = "Warsmash";
	private static final String KEY_SEEN = "lastExitSeen";
	private static final String KEY_SUMMARY = "lastExitSummary";
	private static final int LOOK_BACK = 8;
	private static final int TRACE_LIMIT = 256 << 10;
	/** A Java crash writes its own log just before the process ends; an exit this close to a log is that crash. */
	private static final long OWN_LOG_MILLIS = 60_000;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
	private static boolean started;

	private ExitLogs() {
	}

	/** One short line about the newest abnormal exit found, or null. */
	static String lastSummary(final Context context) {
		return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_SUMMARY, null);
	}

	/** Once per process, on a worker thread: reads how the earlier processes ended and logs abnormal exits. */
	static synchronized void recordPreviousExit(final Context context) {
		if (started || (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)) {
			return;
		}
		started = true;
		final Context app = context.getApplicationContext();
		final Thread worker = new Thread(() -> {
			try {
				record(app);
			}
			catch (final Throwable e) {
				Log.w(TAG, "Unable to read how the last run ended", e);
			}
		}, "exit-log");
		worker.setDaemon(true);
		worker.setPriority(Thread.MIN_PRIORITY);
		worker.start();
	}

	private static void record(final Context context) throws Exception {
		final ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		if (manager == null) {
			return;
		}
		final SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
		final boolean first = !preferences.contains(KEY_SEEN);
		final long seen = preferences.getLong(KEY_SEEN, 0);
		final List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(context.getPackageName(), 0,
				LOOK_BACK);
		long newest = seen;
		boolean logged = false;
		// Newest first. The very first run only marks what the system already holds: those exits belong to a version
		// that could not log them.
		for (final ApplicationExitInfo exit : exits) {
			final long time = exit.getTimestamp();
			if (time <= seen) {
				continue;
			}
			newest = Math.max(newest, time);
			if (first || logged || !abnormal(exit) || ownLogWritten(context, exit)) {
				continue;
			}
			// Only the newest abnormal exit is logged.
			logged = true;
			final String summary = describe(exit);
			preferences.edit().putString(KEY_SUMMARY, summary).apply();
			if (CrashLogs.enabled(context)) {
				write(context, exit, summary);
			}
		}
		if (newest != seen) {
			preferences.edit().putLong(KEY_SEEN, newest).apply();
		}
	}

	private static boolean abnormal(final ApplicationExitInfo exit) {
		switch (exit.getReason()) {
		case ApplicationExitInfo.REASON_LOW_MEMORY:
		case ApplicationExitInfo.REASON_CRASH:
		case ApplicationExitInfo.REASON_CRASH_NATIVE:
		case ApplicationExitInfo.REASON_ANR:
		case ApplicationExitInfo.REASON_SIGNALED:
		case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
		case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
		case ApplicationExitInfo.REASON_DEPENDENCY_DIED:
			return true;
		case ApplicationExitInfo.REASON_EXIT_SELF:
		case ApplicationExitInfo.REASON_OTHER:
		case ApplicationExitInfo.REASON_UNKNOWN:
			return exit.getStatus() != 0;
		default:
			return false;
		}
	}

	private static boolean ownLogWritten(final Context context, final ApplicationExitInfo exit) {
		if (exit.getReason() != ApplicationExitInfo.REASON_CRASH) {
			return false;
		}
		final File own = CrashLogs.latest(context);
		return (own != null) && (own.lastModified() >= (exit.getTimestamp() - OWN_LOG_MILLIS));
	}

	static String reasonName(final int reason) {
		switch (reason) {
		case ApplicationExitInfo.REASON_ANR:
			return "ANR";
		case ApplicationExitInfo.REASON_CRASH:
			return "CRASH";
		case ApplicationExitInfo.REASON_CRASH_NATIVE:
			return "CRASH_NATIVE";
		case ApplicationExitInfo.REASON_DEPENDENCY_DIED:
			return "DEPENDENCY_DIED";
		case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
			return "EXCESSIVE_RESOURCE_USAGE";
		case ApplicationExitInfo.REASON_EXIT_SELF:
			return "EXIT_SELF";
		case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
			return "INITIALIZATION_FAILURE";
		case ApplicationExitInfo.REASON_LOW_MEMORY:
			return "LOW_MEMORY";
		case ApplicationExitInfo.REASON_OTHER:
			return "OTHER";
		case ApplicationExitInfo.REASON_PERMISSION_CHANGE:
			return "PERMISSION_CHANGE";
		case ApplicationExitInfo.REASON_SIGNALED:
			return "SIGNALED";
		case ApplicationExitInfo.REASON_USER_REQUESTED:
			return "USER_REQUESTED";
		case ApplicationExitInfo.REASON_USER_STOPPED:
			return "USER_STOPPED";
		case ApplicationExitInfo.REASON_UNKNOWN:
			return "UNKNOWN";
		default:
			return "reason " + reason;
		}
	}

	private static String time(final long millis) {
		return TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
	}

	private static String describe(final ApplicationExitInfo exit) {
		return reasonName(exit.getReason()) + " (status " + exit.getStatus() + ") at " + time(exit.getTimestamp());
	}

	private static void write(final Context context, final ApplicationExitInfo exit, final String summary)
			throws Exception {
		final StringBuilder text = new StringBuilder();
		text.append("Reason: ").append(reasonName(exit.getReason())).append('\n');
		text.append("Status or signal: ").append(exit.getStatus()).append('\n');
		text.append("Exit time: ").append(time(exit.getTimestamp())).append('\n');
		text.append("Importance: ").append(importance(exit.getImportance())).append('\n');
		text.append("PSS: ").append(exit.getPss()).append(" kB\n");
		text.append("RSS: ").append(exit.getRss()).append(" kB\n");
		text.append("Description: ").append(exit.getDescription()).append('\n');
		final CrashReport.Details details = new CrashReport.Details();
		details.time = exit.getTimestamp();
		final BuildInfo build = BuildInfo.current();
		details.appVersion = build.getVersionName();
		details.commit = build.getCommit();
		details.device = Build.MANUFACTURER + " " + Build.MODEL;
		details.androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
		details.graphics = CrashLogs.graphics(context);
		details.exit = text.toString().trim();
		details.exitTrace = trace(exit);
		final String name = CrashReport.fileName(details.time, ZoneId.systemDefault());
		CrashLogs.store(context, name,
				CrashReport.text(details, ZoneId.systemDefault()).getBytes(StandardCharsets.UTF_8));
		Log.i(TAG, "Logged the earlier process exit: " + summary);
	}

	private static String importance(final int value) {
		switch (value) {
		case ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
			return "foreground (" + value + ")";
		case ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE:
			return "visible (" + value + ")";
		case ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE:
			return "perceptible (" + value + ")";
		case ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE:
			return "service (" + value + ")";
		case ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED:
			return "cached (" + value + ")";
		default:
			return Integer.toString(value);
		}
	}

	/** The system's trace, for a native crash or an ANR; the system keeps none for other exits. */
	private static String trace(final ApplicationExitInfo exit) {
		if ((exit.getReason() != ApplicationExitInfo.REASON_CRASH_NATIVE)
				&& (exit.getReason() != ApplicationExitInfo.REASON_ANR)) {
			return null;
		}
		try (InputStream in = exit.getTraceInputStream()) {
			if (in == null) {
				return null;
			}
			final byte[] buffer = new byte[TRACE_LIMIT];
			int length = 0;
			int read;
			while ((length < buffer.length) && ((read = in.read(buffer, length, buffer.length - length)) > 0)) {
				length += read;
			}
			return CrashReport.traceText(buffer, length);
		}
		catch (final java.io.IOException | RuntimeException e) {
			return "The trace could not be read: " + e;
		}
	}
}
