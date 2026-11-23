package com.etheller.warsmash.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * Finds the file of a map that is named by a path of the device storage rather than by a path of the game data: the
 * -loadfile argument, a map of a shared folder.
 */
public final class MapFiles {
	private static String[] externalRoots() {
		return new String[] { "/sdcard/", "/storage/emulated/0/", "/storage/self/primary/", "/mnt/sdcard/",
				"/mnt/user/0/primary/" };
	}

	private MapFiles() {
	}

	/** Whether the path names a file of the device rather than of the game data. */
	public static boolean isAbsolute(final String path) {
		return (path != null) && (path.startsWith("/") || path.startsWith("file:/"));
	}

	/** The map file the path names, or null when none of the names the storage goes by has one. */
	public static File find(final String path) {
		for (final String candidate : candidates(path)) {
			final File file = new File(candidate);
			if (file.exists()) {
				return file;
			}
		}
		return null;
	}

	/** Why {@link #find} found nothing, for the log: a missing file and a folder the app may not read look alike. */
	public static String describeMissing(final String path) {
		final File file = new File(candidates(path).get(0));
		final File parent = file.getParentFile();
		final boolean folderExists = (parent != null) && parent.exists();
		final boolean folderReadable = (parent != null) && parent.canRead();
		return "No map file " + file + " (folder exists " + folderExists + ", readable " + folderReadable
				+ "); on Android the shared storage needs All files access";
	}

	static List<String> candidates(final String path) {
		final List<String> found = new ArrayList<>();
		String name = path.trim();
		if (name.startsWith("file://")) {
			name = name.substring("file://".length());
		}
		else if (name.startsWith("file:/")) {
			name = name.substring("file:".length());
		}
		found.add(name);
		final String[] roots = externalRoots();
		for (final String root : roots) {
			if (name.startsWith(root)) {
				for (final String other : roots) {
					if (!other.equals(root)) {
						found.add(other + name.substring(root.length()));
					}
				}
				break;
			}
		}
		return found;
	}
}
