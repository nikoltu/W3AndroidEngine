package com.etheller.warsmash.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Where the profiles of a player are kept so that they outlive the application: a folder of its own in the shared
 * storage, and the copying that moves the profiles there without ever losing a file.
 */
public final class ProfileStorage {
	/** The folder below the shared storage, with Save, Replay and the manifest of the profile list. */
	public static final String SHARED_PATH = "WC3-Android/Profiles";
	public static final String CONFLICTS = "Conflicts";
	/** The folders of the engine's own storage that go with the profiles. */
	public static final String[] FOLDERS = { "Save", "Replay" };
	private static final String PART = ".part";

	private ProfileStorage() {
	}

	public static File sharedFolder(final File sharedStorage) {
		return new File(sharedStorage, SHARED_PATH);
	}

	/**
	 * The folder the engine's player files go to: the shared one when the player wants it and it can be used, else
	 * null for the application's own storage.
	 */
	public static File choose(final boolean wanted, final File sharedFolder, final boolean usable) {
		return wanted && usable && (sharedFolder != null) ? sharedFolder : null;
	}

	/** Whether files can be made in the folder (made if need be). */
	public static boolean writable(final File folder) {
		if ((folder == null) || (!folder.isDirectory() && !folder.mkdirs())) {
			return false;
		}
		final File probe = new File(folder, ".probe");
		try {
			Files.write(probe.toPath(), new byte[] { 1 });
			return probe.delete() || !probe.exists();
		}
		catch (final IOException | RuntimeException e) {
			return false;
		}
	}

	/** Whether any profile folder under the root holds a file, that is, whether a copy has anything to move. */
	public static boolean hasFiles(final File root) {
		for (final String name : FOLDERS) {
			if (anyFile(new File(root, name))) {
				return true;
			}
		}
		return false;
	}

	private static boolean anyFile(final File file) {
		if (file.isFile()) {
			return true;
		}
		final File[] children = file.listFiles();
		if (children != null) {
			for (final File child : children) {
				if (anyFile(child)) {
					return true;
				}
			}
		}
		return false;
	}

	/** What a copy did. */
	public static final class Result {
		public int copied;
		public int skipped;
		public int conflicts;
		public final List<String> failed = new ArrayList<>();
		public final List<String> unverified = new ArrayList<>();

		public boolean ok() {
			return this.failed.isEmpty() && this.unverified.isEmpty();
		}
	}

	/**
	 * Copies the tree below one folder into another. Nothing at the target is overwritten and nothing at the source is
	 * deleted: a file that is not there is copied (through a temporary name, so that an interrupted copy leaves no
	 * half file), an identical one is left, and a different one is kept in the conflicts folder. Running it again
	 * continues where an interrupted run stopped. Every source file is compared with its copy at the end.
	 *
	 * @param conflicts where files that differ from the target's go
	 */
	public static Result copyTree(final File from, final File to, final File conflicts) {
		final Result result = new Result();
		if (!from.isDirectory()) {
			return result;
		}
		copy(from, to, conflicts, result);
		verify(from, to, conflicts, result);
		return result;
	}

	private static void copy(final File from, final File to, final File conflicts, final Result result) {
		final File[] children = from.listFiles();
		if (children == null) {
			result.failed.add(from.getPath());
			return;
		}
		Arrays.sort(children);
		for (final File child : children) {
			final File target = new File(to, child.getName());
			final File other = new File(conflicts, child.getName());
			if (child.isDirectory()) {
				if (!target.isDirectory() && !target.mkdirs()) {
					result.failed.add(target.getPath());
					continue;
				}
				copy(child, target, other, result);
			}
			else if (child.getName().endsWith(PART)) {
				continue;
			}
			else if (!target.exists()) {
				if (copyFile(child, target)) {
					result.copied++;
				}
				else {
					result.failed.add(child.getPath());
				}
			}
			else if (target.isFile() && sameContent(child, target)) {
				result.skipped++;
			}
			else if (!other.exists() || !sameContent(child, other)) {
				if (copyFile(child, other)) {
					result.conflicts++;
				}
				else {
					result.failed.add(child.getPath());
				}
			}
			else {
				result.conflicts++;
			}
		}
	}

	private static void verify(final File from, final File to, final File conflicts, final Result result) {
		final File[] children = from.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			final File target = new File(to, child.getName());
			final File other = new File(conflicts, child.getName());
			if (child.isDirectory()) {
				verify(child, target, other, result);
			}
			else if (!child.getName().endsWith(PART) && !sameContent(child, target) && !sameContent(child, other)) {
				result.unverified.add(child.getPath());
			}
		}
	}

	private static boolean copyFile(final File from, final File to) {
		final File part = new File(to.getPath() + PART);
		try {
			to.getParentFile().mkdirs();
			Files.copy(from.toPath(), part.toPath(), StandardCopyOption.REPLACE_EXISTING);
			if (part.length() != from.length()) {
				part.delete();
				return false;
			}
			part.setLastModified(from.lastModified());
			Files.move(part.toPath(), to.toPath());
			return true;
		}
		catch (final IOException | RuntimeException e) {
			part.delete();
			LauncherLog.w("Unable to copy " + from + " to " + to, e);
			return false;
		}
	}

	private static boolean sameContent(final File a, final File b) {
		if (!a.isFile() || !b.isFile() || (a.length() != b.length())) {
			return false;
		}
		final byte[] first = new byte[8192];
		final byte[] second = new byte[8192];
		try (InputStream in = Files.newInputStream(a.toPath()); InputStream other = Files.newInputStream(b.toPath())) {
			while (true) {
				final int read = readFully(in, first);
				if (read != readFully(other, second)) {
					return false;
				}
				if (read <= 0) {
					return true;
				}
				for (int i = 0; i < read; i++) {
					if (first[i] != second[i]) {
						return false;
					}
				}
			}
		}
		catch (final IOException | RuntimeException e) {
			return false;
		}
	}

	private static int readFully(final InputStream in, final byte[] buffer) throws IOException {
		int total = 0;
		while (total < buffer.length) {
			final int read = in.read(buffer, total, buffer.length - total);
			if (read < 0) {
				break;
			}
			total += read;
		}
		return total;
	}

	/** Writes a file so that a reader sees the old one or the whole new one. */
	static boolean writeAtomically(final File file, final byte[] data) {
		final File part = new File(file.getPath() + PART);
		try {
			file.getParentFile().mkdirs();
			try (OutputStream out = Files.newOutputStream(part.toPath())) {
				out.write(data);
			}
		}
		catch (final IOException | RuntimeException e) {
			part.delete();
			LauncherLog.w("Unable to write " + file, e);
			return false;
		}
		try {
			Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		}
		catch (final IOException | RuntimeException e) {
			try {
				Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			catch (final IOException | RuntimeException again) {
				part.delete();
				LauncherLog.w("Unable to write " + file, again);
				return false;
			}
		}
		return true;
	}
}
