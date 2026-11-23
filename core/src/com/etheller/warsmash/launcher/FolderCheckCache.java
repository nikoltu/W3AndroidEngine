package com.etheller.warsmash.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.etheller.warsmash.datasources.FolderSignature;
import com.etheller.warsmash.util.Log;

/**
 * What a walk of a game data folder found, kept in a file so that the next start of the app does not walk the folder
 * again (tens of thousands of files take half a minute over the phone's shared storage). A result is only reused while
 * the folders it was made from look as they did: adding, deleting or renaming a file changes the modification time of
 * the folder that holds it, so the times of all folders, with the size and time of the files every check opens, are
 * the signature.
 */
final class FolderCheckCache {
	/**
	 * The version of the file's format and of the checks whose results it keeps (what counts as readable, the content
	 * problems looked for). Raise it when either changes: results of an older one are read again. The version of the
	 * app does not take part, so an update that leaves the checks alone keeps what was found.
	 */
	static final int LOGIC_VERSION = 3;
	private static final String HEADER = "warsmash-folder-check " + LOGIC_VERSION;
	private static final int MAX_FOLDERS = 4;

	private static final Map<String, Snapshot> SNAPSHOTS = new LinkedHashMap<>();
	private static File file;
	private static boolean loaded;

	private FolderCheckCache() {
	}

	/** One folder as it was walked. */
	static final class Snapshot extends FolderSignature {
		int entries;
		int files;
		/** The content problems found in the folder; null when they were not looked for yet. */
		List<String> problems;
		/** Set by {@link FolderCheckCache#beforeImport}: the signature was just checked against the folder. */
		boolean baseline;

		Snapshot copy() {
			final Snapshot copy = new Snapshot();
			copy.copyFrom(this);
			copy.entries = this.entries;
			copy.files = this.files;
			copy.problems = this.problems == null ? null : new ArrayList<>(this.problems);
			return copy;
		}
	}

	/** Keeps the results in this file. Null keeps them for this run only. */
	static void use(final File cacheFile) {
		synchronized (SNAPSHOTS) {
			if ((cacheFile != null) && cacheFile.equals(file)) {
				return;
			}
			file = cacheFile;
			SNAPSHOTS.clear();
			loaded = false;
		}
	}

	/** Reads the file again, as a new start of the app does. */
	static void restart() {
		synchronized (SNAPSHOTS) {
			SNAPSHOTS.clear();
			loaded = false;
		}
	}

	/** The result for a folder when the folder still looks as it did, else null (and the result is dropped). */
	static Snapshot get(final File folder) {
		final String path = folder.getAbsolutePath();
		final Snapshot snapshot;
		synchronized (SNAPSHOTS) {
			load();
			snapshot = SNAPSHOTS.get(path);
		}
		if (snapshot == null) {
			return null;
		}
		if (snapshot.matches(folder)) {
			return snapshot;
		}
		remove(folder);
		return null;
	}

	/** The result as it is kept, without looking at the folder. */
	static Snapshot peek(final File folder) {
		synchronized (SNAPSHOTS) {
			load();
			return SNAPSHOTS.get(folder.getAbsolutePath());
		}
	}

	/**
	 * Keeps the result of a walk that started at that time. A folder that changed so shortly before the walk that a
	 * change right after it would leave the time as it was is not kept.
	 */
	static void put(final File folder, final Snapshot snapshot, final long walkStarted) {
		if (!snapshot.settled(walkStarted)) {
			remove(folder);
			return;
		}
		store(folder, snapshot);
	}

	static void store(final File folder, final Snapshot snapshot) {
		synchronized (SNAPSHOTS) {
			load();
			final String path = folder.getAbsolutePath();
			SNAPSHOTS.remove(path);
			SNAPSHOTS.put(path, snapshot);
			while (SNAPSHOTS.size() > MAX_FOLDERS) {
				SNAPSHOTS.remove(SNAPSHOTS.keySet().iterator().next());
			}
			save();
		}
	}

	static void remove(final File folder) {
		synchronized (SNAPSHOTS) {
			load();
			if (SNAPSHOTS.remove(folder.getAbsolutePath()) != null) {
				save();
			}
		}
	}

	static void setProblems(final File folder, final List<String> problems) {
		synchronized (SNAPSHOTS) {
			load();
			final Snapshot snapshot = SNAPSHOTS.get(folder.getAbsolutePath());
			if (snapshot != null) {
				snapshot.problems = new ArrayList<>(problems);
				save();
			}
		}
	}

	/**
	 * Checks the result against the folder before files are added to it, so that {@link #afterImport} may take the
	 * added files into it; a result that no longer fits is dropped.
	 */
	static void beforeImport(final File folder) {
		final Snapshot snapshot = get(folder);
		if (snapshot != null) {
			snapshot.baseline = true;
		}
	}

	/**
	 * Takes files that an import added to a folder into the result: the folders it changed get their new times, so
	 * that the result stays valid, and the counts grow by the files added. A folder that changed in another way, or
	 * a result that was not checked before the import, is dropped.
	 */
	static void afterImport(final File folder, final DataFolderCheck.Tally added) {
		final Snapshot old = peek(folder);
		if ((old == null) || !old.baseline) {
			remove(folder);
			return;
		}
		final Set<String> touched = new TreeSet<>();
		touched.add("");
		for (final File dir : added.touched) {
			final String relative = FolderSignature.relative(folder, dir);
			if (relative != null) {
				touched.add(relative);
			}
		}
		final Snapshot now = new Snapshot();
		for (final Map.Entry<String, Long> dir : old.dirs.entrySet()) {
			final long time = FolderSignature.folderOf(folder, dir.getKey()).lastModified();
			if ((time != dir.getValue()) && !touched.contains(dir.getKey())) {
				remove(folder);
				return;
			}
			now.dirs.put(dir.getKey(), time);
		}
		for (final String relative : touched) {
			final File below = FolderSignature.folderOf(folder, relative);
			if (!now.dirs.containsKey(relative) && below.isDirectory()) {
				now.dirs.put(relative, below.lastModified());
			}
		}
		for (final String key : old.keys.keySet()) {
			final File opened = new File(folder, key);
			if (!FolderSignature.isReadable(opened)) {
				remove(folder);
				return;
			}
			now.keys.put(key, new long[] { opened.length(), opened.lastModified() });
		}
		now.entries = old.entries + added.entries;
		now.files = old.files + added.files;
		if (!now.settled(System.currentTimeMillis())) {
			// The import changed the folders a moment ago, so a change right after it could leave their times as they
			// are (see FolderSignature.settleMillis). As FolderDataSource does for its index, wait until a later change
			// would show, and keep the result only if the folders still look as they did.
			final long wait = FolderSignature.settleMillis - (System.currentTimeMillis() - now.newest());
			try {
				Thread.sleep(Math.max(0, wait) + 50);
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				remove(folder);
				return;
			}
			if (!now.matches(folder)) {
				remove(folder);
				return;
			}
		}
		store(folder, now);
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		if ((file == null) || !file.isFile()) {
			return;
		}
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
			if (!HEADER.equals(reader.readLine())) {
				return;
			}
			String path = null;
			Snapshot snapshot = null;
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				final String[] fields = line.split("\t", -1);
				switch (fields[0]) {
				case "folder":
					path = fields[1];
					snapshot = new Snapshot();
					break;
				case "counts":
					snapshot.entries = Integer.parseInt(fields[1]);
					snapshot.files = Integer.parseInt(fields[2]);
					break;
				case "problems":
					snapshot.problems = new ArrayList<>();
					for (final String problem : fields[1].split(",")) {
						if (!problem.isEmpty()) {
							snapshot.problems.add(problem);
						}
					}
					break;
				case "end":
					SNAPSHOTS.put(path, snapshot);
					snapshot = null;
					break;
				default:
					snapshot.read(fields);
					break;
				}
			}
		}
		catch (final IOException | RuntimeException e) {
			// A damaged file is a result that was never kept: the folders are walked again.
			SNAPSHOTS.clear();
			Log.warn("The folder check cache cannot be read: " + e);
		}
	}

	private static void save() {
		if (file == null) {
			return;
		}
		final StringBuilder text = new StringBuilder(HEADER).append('\n');
		for (final Map.Entry<String, Snapshot> entry : SNAPSHOTS.entrySet()) {
			final Snapshot snapshot = entry.getValue();
			if (!FolderSignature.plain(entry.getKey())) {
				continue;
			}
			final StringBuilder record = new StringBuilder();
			boolean writable = true;
			record.append("folder\t").append(entry.getKey()).append('\n');
			record.append("counts\t").append(snapshot.entries).append('\t').append(snapshot.files).append('\n');
			if (snapshot.problems != null) {
				record.append("problems\t").append(String.join(",", snapshot.problems)).append('\n');
			}
			writable = snapshot.appendTo(record);
			record.append("end\n");
			if (writable) {
				text.append(record);
			}
		}
		final File temp = new File(file.getParentFile(), file.getName() + ".tmp");
		try {
			Files.write(temp.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
			Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		catch (final IOException e) {
			Log.warn("The folder check cache cannot be written: " + e);
		}
	}
}
