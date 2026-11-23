package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.LauncherKeys.KEY_CRASH_LOGS;
import static com.etheller.warsmash.launcher.LauncherKeys.PREFERENCES;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import com.etheller.warsmash.BuildInfo;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.launcher.CrashReport;
import com.etheller.warsmash.launcher.LauncherOptions;
import com.etheller.warsmash.launcher.ProblemReport;

/**
 * The opt-in crash log: written when the program crashes and the player turned "Save crash logs" on. The copy the
 * player can reach is in Documents/Warsmash (the app's own external folder before Android 10); the copies the app
 * shares from are in its private files. Nothing is sent anywhere by the app.
 */
final class CrashLogs {
	private static final String TAG = "Warsmash";
	static final String FOLDER = "Warsmash";
	private static final String SHARED_FOLDER = "crash-logs";
	private static final long LOGCAT_MILLIS = 5000;
	/** In a debuggable build, this file in the app's files folder makes the next game crash after a few seconds. */
	private static final String CRASH_TEST_FILE = "crash-test";
	private static final long CRASH_TEST_MILLIS = 8000;
	/** Freed when the log is written, so that writing it after an OutOfMemoryError has room. */
	private static volatile byte[] reserve;

	private CrashLogs() {
	}

	/** Called once the crash handler is installed. */
	static void prepare() {
		reserve = new byte[256 << 10];
	}

	/** Called as a game starts from the launcher. */
	static void crashIfAsked(final Context context) {
		if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
			return;
		}
		final File trigger = new File(context.getFilesDir(), CRASH_TEST_FILE);
		if (!trigger.isFile()) {
			return;
		}
		final boolean memory = trigger.length() > 0;
		trigger.delete();
		new Thread(() -> {
			try {
				Thread.sleep(CRASH_TEST_MILLIS);
			}
			catch (final InterruptedException e) {
				return;
			}
			if (memory) {
				throw new OutOfMemoryError("Test crash");
			}
			throw new IllegalStateException("Test crash");
		}, "crash-test").start();
	}

	static boolean enabled(final Context context) {
		return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_CRASH_LOGS, false);
	}

	/** Where the player finds the logs, as the consent says. */
	static String location(final Context context) {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? "Documents/" + FOLDER
				: "Android/data/" + context.getPackageName() + "/files/Documents/" + FOLDER;
	}

	/** The folder the share goes from; the provider serves only this one. */
	static File privateFolder(final Context context) {
		return new File(context.getFilesDir(), SHARED_FOLDER);
	}

	/** The newest log the app can share, or null. */
	static File latest(final Context context) {
		final String[] names = privateFolder(context).list();
		final String newest = names == null ? null : CrashReport.newest(Arrays.asList(names));
		return newest == null ? null : new File(privateFolder(context), newest);
	}

	/** Writes the report of a crash; never throws, as the program is already going down. */
	static void write(final AndroidLauncher game, final Thread thread, final Throwable error) {
		reserve = null;
		try {
			if (!enabled(game)) {
				return;
			}
			final CrashReport.Details details = new CrashReport.Details();
			details.time = System.currentTimeMillis();
			details.thread = thread == null ? null : thread.getName();
			details.error = error;
			details.lastLogged = com.etheller.warsmash.util.Log.getLastError();
			details.lastLoggedMessage = com.etheller.warsmash.util.Log.getLastErrorMessage();
			if (details.lastLogged == error) {
				// The same exception, already printed above as the crash.
				details.lastLogged = null;
				details.lastLoggedMessage = null;
			}
			final BuildInfo build = BuildInfo.current();
			details.appVersion = build.getVersionName();
			details.commit = build.getCommit();
			details.device = Build.MANUFACTURER + " " + Build.MODEL;
			details.androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
			details.log = ownLog();
			details.renderer = CrashReport.rendererFrom(details.log);
			details.graphics = graphics(game);
			details.map = map(game);
			final String name = CrashReport.fileName(details.time, ZoneId.systemDefault());
			store(game, name, CrashReport.text(details, ZoneId.systemDefault()).getBytes(StandardCharsets.UTF_8));
		}
		catch (final Throwable e) {
			Log.e(TAG, "Unable to write the crash log", e);
		}
	}

	/** Keeps a log in the private folder and where the player finds it, and drops the oldest ones. */
	static void store(final Context context, final String name, final byte[] text) throws IOException {
		final File own = new File(privateFolder(context), name);
		own.getParentFile().mkdirs();
		try (OutputStream out = new java.io.FileOutputStream(own)) {
			out.write(text);
		}
		try {
			saveForPlayer(context, name, text);
		}
		catch (final IOException | RuntimeException e) {
			Log.e(TAG, "Unable to save the crash log where the player finds it", e);
		}
		final String[] kept = privateFolder(context).list();
		if (kept != null) {
			for (final String old : CrashReport.expired(Arrays.asList(kept), CrashReport.KEEP)) {
				new File(privateFolder(context), old).delete();
			}
		}
	}

	/** The app's own log: an app may read it without a permission, and only its own process's lines. */
	static List<String> ownLog() {
		final List<String> lines = new ArrayList<>();
		final Thread reader = new Thread(() -> {
			Process process = null;
			try {
				process = new ProcessBuilder("logcat", "-d", "-t", Integer.toString(CrashReport.LOG_LINES),
						"--pid=" + android.os.Process.myPid()).redirectErrorStream(true).start();
				try (BufferedReader in = new BufferedReader(
						new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = in.readLine()) != null) {
						synchronized (lines) {
							lines.add(line);
						}
					}
				}
			}
			catch (final IOException e) {
				synchronized (lines) {
					lines.add("The app log could not be read: " + e.getMessage());
				}
			}
			finally {
				if (process != null) {
					process.destroy();
				}
			}
		}, "crash-log-reader");
		reader.setDaemon(true);
		reader.start();
		try {
			reader.join(LOGCAT_MILLIS);
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		synchronized (lines) {
			return new ArrayList<>(lines);
		}
	}

	static String graphics(final Context context) {
		try {
			final EngineSettings engine = new EngineSettings(context);
			return "set=" + engine.graphics(GraphicsSettings.KEY_SET, "")
					+ ", textureSize=" + engine.graphics(GraphicsSettings.KEY_TEXTURE_SIZE, "")
					+ ", textureCompression=" + engine.graphics(GraphicsSettings.KEY_TEXTURE_COMPRESSION, "")
					+ ", renderScale=" + engine.graphics(GraphicsSettings.KEY_RENDER_SCALE, "")
					+ ", frameCap=" + engine.number(LauncherOptions.KEY_FRAME_CAP, 0)
					+ ", particles=" + engine.number("particles", 2)
					+ ", unitShadows=" + engine.flag("unitShadows", true);
		}
		catch (final RuntimeException e) {
			return null;
		}
	}

	private static String map(final AndroidLauncher game) {
		try {
			return game.currentMapFile();
		}
		catch (final RuntimeException e) {
			return null;
		}
	}

	/** The copy in Documents/Warsmash, or in the app's external folder where MediaStore cannot make one. */
	private static void saveForPlayer(final Context context, final String name, final byte[] text)
			throws IOException {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			final ContentResolver resolver = context.getContentResolver();
			final ContentValues values = new ContentValues();
			values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
			values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
			values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER);
			final Uri uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
					values);
			if (uri == null) {
				throw new IOException("MediaStore made no file");
			}
			try (OutputStream out = resolver.openOutputStream(uri)) {
				if (out == null) {
					throw new IOException("MediaStore opened no file");
				}
				out.write(text);
			}
			dropOldMediaStoreLogs(resolver);
			return;
		}
		final File folder = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
		if (folder == null) {
			throw new IOException("No external folder");
		}
		final File shown = new File(folder, FOLDER);
		shown.mkdirs();
		try (OutputStream out = new java.io.FileOutputStream(new File(shown, name))) {
			out.write(text);
		}
		final String[] names = shown.list();
		if (names != null) {
			for (final String old : CrashReport.expired(Arrays.asList(names), CrashReport.KEEP)) {
				new File(shown, old).delete();
			}
		}
	}

	/** The app sees only the files it made itself, which is exactly the crash logs to rotate. */
	private static void dropOldMediaStoreLogs(final ContentResolver resolver) {
		final Uri files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
		final List<String> names = new ArrayList<>();
		final List<Long> ids = new ArrayList<>();
		try (Cursor cursor = resolver.query(files,
				new String[] { MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME },
				MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
				new String[] { Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER + "%" }, null)) {
			while ((cursor != null) && cursor.moveToNext()) {
				ids.add(cursor.getLong(0));
				names.add(cursor.getString(1));
			}
		}
		final List<String> expired = CrashReport.expired(names, CrashReport.KEEP);
		for (int i = 0; i < names.size(); i++) {
			if (expired.contains(names.get(i))) {
				resolver.delete(android.content.ContentUris.withAppendedId(files, ids.get(i)), null, null);
			}
		}
	}

	/**
	 * Writes the log snapshot to share: this run's app log and the newest crash log. Blocks while the log is read, so
	 * not for the main thread. Null when there is nothing to share.
	 */
	static File snapshot(final Context context) throws IOException {
		final CrashReport.Details details = new CrashReport.Details();
		details.time = System.currentTimeMillis();
		final BuildInfo build = BuildInfo.current();
		details.appVersion = build.getVersionName();
		details.commit = build.getCommit();
		details.device = Build.MANUFACTURER + " " + Build.MODEL;
		details.androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
		details.log = ownLog();
		details.renderer = CrashReport.rendererFrom(details.log);
		details.graphics = graphics(context);
		String crash = null;
		final File last = latest(context);
		if (last != null) {
			crash = new String(java.nio.file.Files.readAllBytes(last.toPath()), StandardCharsets.UTF_8);
		}
		if (details.log.isEmpty() && (crash == null)) {
			return null;
		}
		final File folder = privateFolder(context);
		folder.mkdirs();
		final String[] names = folder.list();
		if (names != null) {
			for (final String old : names) {
				if (CrashReport.isSnapshot(old)) {
					new File(folder, old).delete();
				}
			}
		}
		final File file = new File(folder, CrashReport.snapshotName(details.time, ZoneId.systemDefault()));
		try (OutputStream out = new java.io.FileOutputStream(file)) {
			out.write(CrashReport.snapshotText(details, crash, ZoneId.systemDefault())
					.getBytes(StandardCharsets.UTF_8));
		}
		return file;
	}

	/**
	 * The part of a problem report the phone knows: the build, the device, the graphics, this run's app log and the
	 * newest crash log. Blocks while the log is read, so not for the main thread.
	 */
	static ProblemReport.Request reportRequest(final Context context) {
		final ProblemReport.Request request = new ProblemReport.Request();
		request.time = System.currentTimeMillis();
		final BuildInfo build = BuildInfo.current();
		request.facts.put("App version", build.getVersionName());
		request.facts.put("Commit", build.getBuildId());
		request.facts.put("Device", Build.MANUFACTURER + " " + Build.MODEL);
		request.facts.put("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
		request.appLog = ownLog();
		final String renderer = CrashReport.rendererFrom(request.appLog);
		request.facts.put("GPU", renderer != null ? renderer : "not in this run's log (it is logged once a game runs)");
		request.facts.put("Graphics", graphics(context));
		final String simulationThread = System.getProperty("warsmash.simthread");
		if (simulationThread != null) {
			request.facts.put("Simulation thread", simulationThread);
		}
		request.crashLog = latest(context);
		final String exit = ExitLogs.lastSummary(context);
		if (exit != null) {
			request.facts.put("Last abnormal process exit", exit);
		}
		return request;
	}

	/** The share sheet for the newest crash log; false when there is none. */
	static boolean share(final Context context) {
		final File log = latest(context);
		return (log != null) && share(context, log, "Warsmash crash log");
	}

	/** The share sheet for a file of the private folder. */
	static boolean share(final Context context, final File log, final String subject) {
		final Uri uri = CrashLogProvider.uri(context, log.getName());
		final Intent send = new Intent(Intent.ACTION_SEND);
		send.setType(CrashLogProvider.type(log.getName()));
		send.putExtra(Intent.EXTRA_STREAM, uri);
		send.putExtra(Intent.EXTRA_SUBJECT, subject);
		send.setClipData(ClipData.newRawUri(log.getName(), uri));
		send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		final Intent chooser = Intent.createChooser(send, null);
		chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		context.startActivity(chooser);
		return true;
	}
}
