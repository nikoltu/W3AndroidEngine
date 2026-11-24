package com.etheller.warsmash.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.os.Debug;
import android.util.Log;

import com.etheller.warsmash.parsers.fdf.DataSourceFDFParserBuilder;
import com.etheller.warsmash.units.HashedGameObject;
import com.etheller.warsmash.util.ImageUtils;


/**
 * What the system and the process hold at the moments that matter for the Android low memory killer (a game starts and
 * finishes loading, the app goes to the background, the system asks for memory back). The last lines are kept in a
 * file, so a crash log or the log of an exit the system forced ("LOW_MEMORY") of the next start still tells what the
 * process held before it went.
 */
final class MemoryState {
	private static final String TAG = "Warsmash";
	private static final String FILE = "memory-state.txt";
	private static final int KEEP_LINES = 40;
	private static final ArrayDeque<String> RECENT = new ArrayDeque<>();
	private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
	/** The highest process total seen by {@link #sample} since {@link #resetPeak}, in kB. */
	private static volatile long peakPssKb;
	private static final java.util.concurrent.ExecutorService SAMPLER = java.util.concurrent.Executors
			.newSingleThreadExecutor(runnable -> {
				final Thread thread = new Thread(runnable, "memory-state");
				thread.setDaemon(true);
				thread.setPriority(Thread.MIN_PRIORITY);
				return thread;
			});
	private static boolean previousRead;
	private static String previousRun;

	private MemoryState() {
	}

	/** The process total in kB, which is what the system's own accounting of the exit reports as PSS. */
	static long pssKb() {
		final Debug.MemoryInfo info = new Debug.MemoryInfo();
		Debug.getMemoryInfo(info);
		final long total = info.getTotalPss();
		peakPssKb = Math.max(peakPssKb, total);
		return total;
	}

	static void resetPeak() {
		peakPssKb = 0;
	}

	static long peakPssKb() {
		return peakPssKb;
	}

	/** One line: the system's memory state, then the process's. */
	static String snapshot(final Context context) {
		final StringBuilder line = new StringBuilder(220);
		final ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		if (manager != null) {
			final ActivityManager.MemoryInfo system = new ActivityManager.MemoryInfo();
			manager.getMemoryInfo(system);
			line.append("avail=").append(system.availMem >> 20).append("MB threshold=").append(system.threshold >> 20)
					.append("MB low=").append(system.lowMemory).append(" total=").append(system.totalMem >> 20)
					.append("MB");
		}
		final Debug.MemoryInfo info = new Debug.MemoryInfo();
		Debug.getMemoryInfo(info);
		final long total = info.getTotalPss();
		peakPssKb = Math.max(peakPssKb, total);
		final Runtime runtime = Runtime.getRuntime();
		line.append(" | pss=").append(total >> 10).append("MB java=").append(summary(info, "summary.java-heap"))
				.append(" native=").append(summary(info, "summary.native-heap")).append(" graphics=")
				.append(summary(info, "summary.graphics")).append(" code=").append(summary(info, "summary.code"))
				.append(" | heap=").append((runtime.totalMemory() - runtime.freeMemory()) >> 20).append('/')
				.append(runtime.maxMemory() >> 20).append("MB");
		return line.toString();
	}

	private static String summary(final Debug.MemoryInfo info, final String key) {
		try {
			return (Long.parseLong(info.getMemoryStat(key)) >> 10) + "MB";
		}
		catch (final NumberFormatException | NullPointerException e) {
			return "?";
		}
	}

	/** {@link #note} off the calling thread, for the main thread's callbacks: reading the process total takes a while. */
	static void noteAsync(final Context context, final String event) {
		final Context app = context.getApplicationContext();
		SAMPLER.execute(() -> note(app, event));
	}

	/** Keeps the highest process total, off the calling thread. */
	static void samplePeakAsync() {
		SAMPLER.execute(MemoryState::pssKb);
	}

	/** Logs and keeps one event with the memory state. Never throws; may take a few tens of milliseconds. */
	static void note(final Context context, final String event) {
		try {
			previous(context);
			final String text;
			synchronized (TIME) {
				text = TIME.format(new Date()) + " " + event + " | " + snapshot(context);
			}
			Log.i(TAG, "Memory " + text);
			final String all;
			synchronized (RECENT) {
				RECENT.addLast(text);
				while (RECENT.size() > KEEP_LINES) {
					RECENT.removeFirst();
				}
				all = String.join("\n", RECENT) + "\n";
			}
			final File file = new File(context.getFilesDir(), FILE);
			try (FileOutputStream out = new FileOutputStream(file)) {
				out.write(all.getBytes(StandardCharsets.UTF_8));
			}
		}
		catch (final IOException | RuntimeException e) {
			Log.w(TAG, "Unable to note the memory state", e);
		}
	}

	/** The lines kept by this process and, when it is the first of a new run, by the one before. */
	static String recent(final Context context) {
		synchronized (RECENT) {
			if (!RECENT.isEmpty()) {
				return String.join("\n", RECENT);
			}
		}
		return previous(context);
	}

	/** The lines the process before this one kept; null if it kept none. */
	static String previous(final Context context) {
		try {
			final File file = new File(context.getFilesDir(), FILE);
			if (file.isFile()) {
				return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
			}
		}
		catch (final IOException | RuntimeException e) {
			Log.w(TAG, "Unable to read the memory state", e);
		}
		return null;
	}

	/**
	 * The system asks the app for memory back, or the app is no longer on the screen: lets go of what the process only
	 * keeps to be faster, then collects. The textures decoded ahead for a loading screen are decoded where they are
	 * needed instead, the object data's shared strings start sharing again, the parsed interface files are parsed again when asked for, and what the finished games left on the
	 * Java heap is given back to the system, which counts the pages a heap holds although nothing lives in them
	 * against the process. A game's state is not touched.
	 */
	static void release(final Context context, final String event) {
		noteAsync(context, event);
		ImageUtils.dropPrefetched();
		HashedGameObject.releaseSharedValues();
		DataSourceFDFParserBuilder.releaseParsed();
		SAMPLER.execute(() -> {
			System.gc();
			System.gc();
			note(context, event + ", collected");
		});
	}

	static String levelName(final int level) {
		switch (level) {
		case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
			return "COMPLETE";
		case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
			return "MODERATE";
		case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
			return "BACKGROUND";
		case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
			return "UI_HIDDEN";
		case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
			return "RUNNING_CRITICAL";
		case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
			return "RUNNING_LOW";
		case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
			return "RUNNING_MODERATE";
		default:
			return Integer.toString(level);
		}
	}
}
