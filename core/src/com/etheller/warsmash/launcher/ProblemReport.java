package com.etheller.warsmash.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A problem report made from Options › Debugging: one zip with the replay of the game, the moments the player marked in
 * it, the app's log, the newest crash log and what the game ran on, so that the problem can be played again on another
 * computer. Nothing is sent: the platform hands the file to the player, who decides where it goes. The map of the game
 * (when it is a file of its own) and the newest save always go in; a map above {@link #MAP_LIMIT} stays out, which the
 * report's own text says.
 */
public final class ProblemReport {
	/** The largest map a report carries; a larger one would not go through most ways of sharing. */
	public static final long MAP_LIMIT = 64L << 20;
	/** The largest save a report carries. */
	public static final long SAVE_LIMIT = 32L << 20;
	/** The largest replay a report carries; a game of hours stays far below it. */
	public static final long REPLAY_LIMIT = 32L << 20;
	/** The end of a longer log is kept. */
	public static final int LOG_LIMIT = 4 << 20;
	/** How many reports stay in the folder they are written to; the older ones are deleted. */
	public static final int KEEP = 3;
	public static final String INFO = "report.txt";
	public static final String REPLAY = "replay.wsr";
	public static final String MARKS = "marks.txt";
	public static final String UNRECORDED_MARKS = "marks-unrecorded.txt";
	public static final String APP_LOG = "app-log.txt";
	public static final String CRASH_LOG = "crash-log.txt";
	public static final String MAP_FOLDER = "map/";
	public static final String SAVE_FOLDER = "save/";
	private static final String PREFIX = "bug-report-";
	private static final String SUFFIX = ".zip";
	private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
	private static final DateTimeFormatter TEXT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z",
			Locale.ROOT);

	/** What goes into a report; any file may be null when there is none. */
	public static final class Request {
		public long time;
		/** Facts about the build, the device and the game, in the order they are listed: name to value. */
		public final Map<String, String> facts = new LinkedHashMap<>();
		public File replay;
		/** The moments marked in the replay's game. */
		public File marks;
		/** The moments marked in a later game that has no replay (a campaign mission). */
		public File unrecordedMarks;
		public List<String> appLog;
		/** The name the app log is listed with in the report. */
		public String appLogName = "the app log of this run";
		public File crashLog;
		public File map;
		public File save;
	}

	/** A written report: the zip and, for the player, what went in and what stayed out. */
	public static final class Result {
		public final File file;
		public final List<String> included;
		public final List<String> leftOut;

		Result(final File file, final List<String> included, final List<String> leftOut) {
			this.file = file;
			this.included = included;
			this.leftOut = leftOut;
		}
	}

	private ProblemReport() {
	}

	/** The name of a report written at this time, which sorts by time. */
	public static String fileName(final long timeMillis, final ZoneId zone) {
		return PREFIX + NAME_TIME.format(Instant.ofEpochMilli(timeMillis).atZone(zone)) + SUFFIX;
	}

	/** Whether the name is one of a report, with no folder in it (a share provider serves only these). */
	public static boolean isReport(final String name) {
		return (name != null) && name.startsWith(PREFIX) && name.endsWith(SUFFIX) && (name.indexOf('/') < 0)
				&& (name.indexOf('\\') < 0) && (name.length() > (PREFIX.length() + SUFFIX.length()));
	}

	/**
	 * Writes the report into the folder, beside the newest {@link #KEEP} - 1 earlier ones; the zip appears under its
	 * name only when it is complete.
	 */
	public static Result write(final Request request, final File folder, final ZoneId zone) throws IOException {
		if (!folder.isDirectory() && !folder.mkdirs()) {
			throw new IOException("The folder " + folder + " cannot be made");
		}
		final File file = new File(folder, fileName(request.time, zone));
		final File part = new File(folder, file.getName() + ".part");
		final List<String> included = new ArrayList<>();
		final List<String> leftOut = new ArrayList<>();
		try (OutputStream out = new FileOutputStream(part)) {
			write(request, out, zone, included, leftOut);
		}
		catch (final IOException | RuntimeException e) {
			part.delete();
			throw e;
		}
		file.delete();
		if (!part.renameTo(file)) {
			part.delete();
			throw new IOException("The report could not be named " + file.getName());
		}
		final List<String> reports = new ArrayList<>();
		final String[] names = folder.list();
		if (names != null) {
			for (final String name : names) {
				if (isReport(name)) {
					reports.add(name);
				}
			}
		}
		reports.sort(null);
		for (int i = 0; i < (reports.size() - KEEP); i++) {
			new File(folder, reports.get(i)).delete();
		}
		return new Result(file, included, leftOut);
	}

	/**
	 * Writes the zip.
	 *
	 * @param included gets a line for each part that went in
	 * @param leftOut  gets a line for each part that was asked for or found but stayed out, with the reason
	 */
	public static void write(final Request request, final OutputStream output, final ZoneId zone,
			final List<String> included, final List<String> leftOut) throws IOException {
		final ZipOutputStream zip = new ZipOutputStream(output);
		if (request.replay != null) {
			replay(zip, request, included, leftOut);
		}
		else {
			leftOut.add("Replay: no replay was found (campaign missions and loaded saves are not recorded)");
		}
		if ((request.marks != null) && request.marks.isFile()) {
			file(zip, MARKS, request.marks);
			included.add("Marked moments");
		}
		if ((request.unrecordedMarks != null) && request.unrecordedMarks.isFile()) {
			file(zip, UNRECORDED_MARKS, request.unrecordedMarks);
			included.add("Marked moments of a game without a replay");
		}
		if ((request.appLog != null) && !request.appLog.isEmpty()) {
			entry(zip, APP_LOG, tail(joined(request.appLog), LOG_LIMIT));
			included.add("App log");
		}
		else {
			leftOut.add("App log: " + request.appLogName + " is empty");
		}
		if ((request.crashLog != null) && request.crashLog.isFile()) {
			entry(zip, CRASH_LOG, tail(read(request.crashLog), LOG_LIMIT));
			included.add("Latest crash log");
		}
		optional(zip, request.map, MAP_FOLDER, MAP_LIMIT, "Map", included, leftOut);
		optional(zip, request.save, SAVE_FOLDER, SAVE_LIMIT, "Latest save", included, leftOut);
		entry(zip, INFO, info(request, zone, included, leftOut).getBytes(StandardCharsets.UTF_8));
		zip.finish();
		output.flush();
	}

	private static void replay(final ZipOutputStream zip, final Request request, final List<String> included,
			final List<String> leftOut) throws IOException {
		final File replay = request.replay;
		if (!replay.isFile()) {
			leftOut.add("Replay: " + replay.getName() + " is gone");
			return;
		}
		if (replay.length() > REPLAY_LIMIT) {
			leftOut.add("Replay: " + replay.getName() + " is larger than " + megabytes(REPLAY_LIMIT));
			return;
		}
		file(zip, REPLAY, replay);
		included.add("Replay " + replay.getName());
	}

	private static String sha1(final File file) {
		try (InputStream in = new FileInputStream(file)) {
			final java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
			final byte[] buffer = new byte[1 << 16];
			int read;
			while ((read = in.read(buffer)) > 0) {
				digest.update(buffer, 0, read);
			}
			final StringBuilder hex = new StringBuilder();
			for (final byte value : digest.digest()) {
				hex.append(String.format(Locale.ROOT, "%02x", value & 0xFF));
			}
			return hex.toString();
		}
		catch (final IOException | java.security.NoSuchAlgorithmException e) {
			return "unreadable";
		}
	}

	private static void optional(final ZipOutputStream zip, final File file, final String folder, final long limit,
			final String label, final List<String> included, final List<String> leftOut) throws IOException {
		// A map of the game data and a game never saved are not files to leave out.
		if ((file == null) || !file.isFile()) {
			return;
		}
		if (file.length() > limit) {
			// Size and hash let us find the very same version of the file ourselves.
			leftOut.add(label + ": " + file.getName() + " is larger than " + megabytes(limit) + " ("
					+ file.length() + " bytes, SHA-1 " + sha1(file) + ")");
		}
		else {
			file(zip, folder + safeName(file.getName()), file);
			included.add(label + " " + file.getName());
		}
	}

	static String info(final Request request, final ZoneId zone, final List<String> included,
			final List<String> leftOut) {
		final StringBuilder out = new StringBuilder(2048);
		out.append("Warsmash bug report\n");
		line(out, "Time", TEXT_TIME.format(Instant.ofEpochMilli(request.time).atZone(zone)));
		for (final Map.Entry<String, String> fact : request.facts.entrySet()) {
			line(out, fact.getKey(), fact.getValue());
		}
		out.append("\nIncluded\n");
		for (final String part : included) {
			out.append("- ").append(part).append('\n');
		}
		if (!leftOut.isEmpty()) {
			out.append("\nLeft out\n");
			for (final String part : leftOut) {
				out.append("- ").append(part).append('\n');
			}
		}
		return out.toString();
	}

	private static void line(final StringBuilder out, final String name, final String value) {
		out.append(name).append(": ").append((value == null) || value.isEmpty() ? "unknown" : value).append('\n');
	}

	private static String megabytes(final long bytes) {
		return (bytes >> 20) + " MB";
	}

	/** A file name the zip can hold in a folder of its own. */
	private static String safeName(final String name) {
		final String safe = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_");
		return safe.isEmpty() ? "file" : safe;
	}

	private static byte[] joined(final List<String> lines) {
		final StringBuilder text = new StringBuilder(lines.size() * 80);
		for (final String line : lines) {
			text.append(line).append('\n');
		}
		return text.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] read(final File file) throws IOException {
		return java.nio.file.Files.readAllBytes(file.toPath());
	}

	/** The last {@code limit} bytes, from a line's start. */
	static byte[] tail(final byte[] bytes, final int limit) {
		if (bytes.length <= limit) {
			return bytes;
		}
		int from = bytes.length - limit;
		while ((from < bytes.length) && (bytes[from - 1] != '\n')) {
			from++;
		}
		final byte[] kept = new byte[bytes.length - from];
		System.arraycopy(bytes, from, kept, 0, kept.length);
		return kept;
	}

	private static void entry(final ZipOutputStream zip, final String name, final byte[] bytes) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(bytes);
		zip.closeEntry();
	}

	private static void file(final ZipOutputStream zip, final String name, final File file) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		try (InputStream in = new FileInputStream(file)) {
			final byte[] buffer = new byte[1 << 16];
			int read;
			while ((read = in.read(buffer)) != -1) {
				zip.write(buffer, 0, read);
			}
		}
		zip.closeEntry();
	}
}
