package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a result kept from a walk of a game data folder is checked against: the modification times of all its folders
 * and the size and time of the files every check opens. Adding, deleting or renaming a file changes the modification
 * time of the folder that holds it, so a folder that still has these times still has the files it had.
 */
public class FolderSignature {
	/** File systems stamp folders coarsely (FAT in two seconds), so a change right after a walk could keep the time. */
	public static volatile long settleMillis = 2500;

	/** The files every check of an extracted folder opens; they are tried for real, not only by their rights. */
	private static final String[] OPENED_FILES = { "units/UnitData.slk", "ui/WorldEditGameStrings.txt",
		".build.info" };

	/** Folders, relative to the data folder and "" for itself, with their modification times. */
	public final Map<String, Long> dirs = new LinkedHashMap<>();
	/** The files every check opens, with their size and modification time. */
	public final Map<String, long[]> keys = new LinkedHashMap<>();

	public long newest() {
		long newest = 0;
		for (final long time : this.dirs.values()) {
			newest = Math.max(newest, time);
		}
		return newest;
	}

	/**
	 * Whether a walk that started at that time may be kept: a folder that changed so shortly before it that a change
	 * right after the walk would leave the time as it was must not be.
	 */
	public boolean settled(final long walkStarted) {
		return walkStarted - newest() >= settleMillis;
	}

	public boolean matches(final File folder) {
		for (final Map.Entry<String, Long> dir : this.dirs.entrySet()) {
			if (folderOf(folder, dir.getKey()).lastModified() != dir.getValue()) {
				return false;
			}
		}
		for (final Map.Entry<String, long[]> key : this.keys.entrySet()) {
			final File opened = new File(folder, key.getKey());
			if ((opened.length() != key.getValue()[0]) || (opened.lastModified() != key.getValue()[1])
					|| !isReadable(opened)) {
				return false;
			}
		}
		return true;
	}

	public void copyFrom(final FolderSignature other) {
		this.dirs.putAll(other.dirs);
		this.keys.putAll(other.keys);
	}

	/**
	 * Writes the lines of the signature; false when a name could not survive the file, and then nothing may be kept.
	 */
	public boolean appendTo(final StringBuilder text) {
		for (final Map.Entry<String, Long> dir : this.dirs.entrySet()) {
			if (!plain(dir.getKey())) {
				return false;
			}
			text.append("dir\t").append(dir.getValue()).append('\t').append(dir.getKey()).append('\n');
		}
		for (final Map.Entry<String, long[]> key : this.keys.entrySet()) {
			if (!plain(key.getKey())) {
				return false;
			}
			text.append("key\t").append(key.getValue()[0]).append('\t').append(key.getValue()[1]).append('\t')
					.append(key.getKey()).append('\n');
		}
		return true;
	}

	/** Takes a line written by {@link #appendTo}; false for a line that is not of the signature. */
	public boolean read(final String[] fields) {
		switch (fields[0]) {
		case "dir":
			this.dirs.put(fields[2], Long.parseLong(fields[1]));
			return true;
		case "key":
			this.keys.put(fields[3], new long[] { Long.parseLong(fields[1]), Long.parseLong(fields[2]) });
			return true;
		default:
			return false;
		}
	}

	public static File folderOf(final File folder, final String relative) {
		return relative.isEmpty() ? folder : new File(folder, relative);
	}

	/** The path of a folder below another, with "/", and "" for the folder itself; null when it is not below. */
	public static String relative(final File folder, final File dir) {
		final String base = folder.getAbsolutePath();
		final String path = dir.getAbsolutePath();
		if (path.equals(base)) {
			return "";
		}
		if (!path.startsWith(base + File.separator)) {
			return null;
		}
		return path.substring(base.length() + 1).replace(File.separatorChar, '/');
	}

	/** Whether the check opens this file (a path with "/") wherever it lies in the folder. */
	public static boolean isOpenedFile(final String path) {
		final String lower = path.toLowerCase(Locale.ROOT);
		for (final String opened : OPENED_FILES) {
			final String name = opened.toLowerCase(Locale.ROOT);
			if (lower.equals(name) || lower.endsWith("/" + name)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isReadable(final File file) {
		if ((file == null) || !file.isFile()) {
			return false;
		}
		try (InputStream stream = new FileInputStream(file)) {
			return stream.read() >= 0;
		}
		catch (final IOException e) {
			return false;
		}
	}

	/** Names with a tab or a line break would not survive the file; such a folder is simply not kept. */
	public static boolean plain(final String name) {
		return (name.indexOf('\t') < 0) && (name.indexOf('\n') < 0) && (name.indexOf('\r') < 0);
	}
}
