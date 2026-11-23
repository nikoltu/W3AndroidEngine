package com.etheller.warsmash.datasources;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.etheller.warsmash.util.Log;

/**
 * The list of files of a large data folder, kept in a file of the app so that the next start does not walk the folder
 * again (tens of thousands of files take half a minute over the shared storage of a phone). A list is only used while
 * the folder looks as it did when it was made, see {@link FolderSignature}; a list that cannot be read in full, or that
 * was made of another folder, counts as none. It lists names only, so no version of the app takes part: the way the
 * names are looked up is not in it.
 */
public final class FolderIndexStore {
	/** The version of the file's format; a file of another one is read as none. */
	private static final String HEADER = "warsmash-folder-index 1";
	/** A folder with fewer files is walked faster than a file is read. */
	static final int MIN_FILES = 1000;
	private static final int MAX_FILES_KEPT = 8;

	private static volatile File directory;

	private FolderIndexStore() {
	}

	/** What was kept of a folder. */
	static final class Kept {
		final FolderSignature signature = new FolderSignature();
		final ArrayList<String> files = new ArrayList<>();
	}

	/** Keeps the lists in this folder; null keeps none. */
	public static void use(final File folder) {
		directory = folder;
	}

	/** Whether the lists are kept in files at all. */
	static boolean isUsed() {
		return directory != null;
	}

	private static File fileOf(final File folder) {
		final File where = directory;
		if (where == null) {
			return null;
		}
		return new File(where, "folder-index-" + Integer.toHexString(folder.getAbsolutePath().hashCode()) + ".txt");
	}

	/** The list of the folder when one was kept and the folder still looks as it did, else null. */
	static Kept load(final File folder) {
		final File file = fileOf(folder);
		if ((file == null) || !file.isFile()) {
			return null;
		}
		final Kept kept = new Kept();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8), 1 << 16)) {
			if (!HEADER.equals(reader.readLine())
					|| !("folder\t" + folder.getAbsolutePath()).equals(reader.readLine())) {
				return null;
			}
			int expected = -1;
			boolean ended = false;
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.startsWith("files\t")) {
					expected = Integer.parseInt(line.substring("files\t".length()));
					break;
				}
				if (!kept.signature.read(line.split("\t", -1))) {
					return null;
				}
			}
			if (expected < 0) {
				return null;
			}
			kept.files.ensureCapacity(expected);
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.equals("end")) {
					ended = true;
					break;
				}
				kept.files.add(line);
			}
			if (!ended || (kept.files.size() != expected) || !kept.signature.matches(folder)) {
				return null;
			}
			return kept;
		}
		catch (final IOException | RuntimeException e) {
			Log.warn("The folder index cannot be read: " + e);
			return null;
		}
	}

	/** Keeps the list of a folder that was walked whole and has not changed since. */
	static void save(final File folder, final FolderSignature signature, final Collection<String> files) {
		final File file = fileOf(folder);
		if ((file == null) || (files.size() < MIN_FILES)) {
			return;
		}
		final StringBuilder text = new StringBuilder(files.size() * 48 + 4096);
		text.append(HEADER).append('\n').append("folder\t").append(folder.getAbsolutePath()).append('\n');
		if (!FolderSignature.plain(folder.getAbsolutePath()) || !signature.appendTo(text)) {
			return;
		}
		text.append("files\t").append(files.size()).append('\n');
		for (final String name : files) {
			if (!FolderSignature.plain(name)) {
				return;
			}
			text.append(name).append('\n');
		}
		text.append("end\n");
		final File temp = new File(file.getParentFile(), file.getName() + ".tmp");
		try {
			file.getParentFile().mkdirs();
			Files.write(temp.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
			Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			trim(file);
		}
		catch (final IOException e) {
			Log.warn("The folder index cannot be written: " + e);
		}
	}

	/**
	 * Keeps the list of files of a folder that another walk (the launcher's check) has just made whole, with the
	 * signature it made, so that the data source does not walk the folder for the same list. Paths are relative with
	 * "/".
	 */
	public static void keep(final File folder, final FolderSignature signature, final Collection<String> files) {
		if ((directory == null) || (files.size() < MIN_FILES)) {
			return;
		}
		if (File.separatorChar == '/') {
			save(folder, signature, files);
			return;
		}
		final List<String> local = new ArrayList<>(files.size());
		for (final String name : files) {
			local.add(name.replace('/', File.separatorChar));
		}
		save(folder, signature, local);
	}

	/** Drops what was kept of a folder that was walked and found not fit to keep. */
	static void remove(final File folder) {
		final File file = fileOf(folder);
		if ((file != null) && file.isFile() && !file.delete()) {
			Log.warn("The folder index cannot be removed: " + file);
		}
	}

	/** Folders that were once used and are not any more must not pile up. */
	private static void trim(final File newest) {
		final File[] kept = newest.getParentFile().listFiles(
				(dir, name) -> name.startsWith("folder-index-") && name.endsWith(".txt"));
		if ((kept == null) || (kept.length <= MAX_FILES_KEPT)) {
			return;
		}
		Arrays.sort(kept, Comparator.comparingLong(File::lastModified));
		for (int i = 0; i < kept.length - MAX_FILES_KEPT; i++) {
			if (!kept[i].equals(newest)) {
				kept[i].delete();
			}
		}
	}
}
