package com.etheller.warsmash.launcher;

import java.io.File;

/**
 * Turns a folder picked through the system's document picker (a tree URI: authority and document id) back into the
 * path it stands for, since the engine reads its data through plain files. Pure, so it needs no Android classes.
 */
public final class StorageTree {
	public static final String AUTHORITY_STORAGE = "com.android.externalstorage.documents";
	public static final String AUTHORITY_DOWNLOADS = "com.android.providers.downloads.documents";

	public enum Problem {
		/** The folder is served by an app that has no place on the device's storage (cloud, media, gallery). */
		UNKNOWN_PROVIDER,
		/** A Downloads entry known only by a number: its path cannot be told; a folder of the internal storage can. */
		NO_PATH,
		/** The document id has no shape the provider uses. */
		INVALID
	}

	public static final class Result {
		public final File folder;
		public final Problem problem;

		private Result(final File folder, final Problem problem) {
			this.folder = folder;
			this.problem = problem;
		}

		public boolean ok() {
			return this.folder != null;
		}
	}

	private StorageTree() {
	}

	/**
	 * @param authority the authority of the tree URI
	 * @param documentId the tree document id
	 * @param primaryRoot the internal storage, "/storage/emulated/0"
	 * @param downloadsRoot the system's Download folder
	 */
	public static Result resolve(final String authority, final String documentId, final File primaryRoot,
			final File downloadsRoot) {
		if ((authority == null) || (documentId == null) || documentId.isEmpty()) {
			return failure(Problem.INVALID);
		}
		if (AUTHORITY_STORAGE.equals(authority)) {
			return resolveStorage(documentId, primaryRoot);
		}
		if (AUTHORITY_DOWNLOADS.equals(authority)) {
			return resolveDownloads(documentId, downloadsRoot);
		}
		return failure(Problem.UNKNOWN_PROVIDER);
	}

	private static Result resolveStorage(final String documentId, final File primaryRoot) {
		final int colon = documentId.indexOf(':');
		if (colon <= 0) {
			return failure(Problem.INVALID);
		}
		final String volume = documentId.substring(0, colon);
		final String relative = documentId.substring(colon + 1);
		if (!safe(relative)) {
			return failure(Problem.INVALID);
		}
		final File base;
		if ("primary".equalsIgnoreCase(volume)) {
			base = primaryRoot;
		}
		else if ("home".equalsIgnoreCase(volume)) {
			base = new File(primaryRoot, "Documents");
		}
		else if (volume.indexOf('/') >= 0) {
			return failure(Problem.INVALID);
		}
		else {
			base = new File("/storage/" + volume);
		}
		return success(relative.isEmpty() ? base : new File(base, relative));
	}

	private static Result resolveDownloads(final String documentId, final File downloadsRoot) {
		if ("downloads".equals(documentId)) {
			return success(downloadsRoot);
		}
		if (documentId.startsWith("raw:")) {
			final String path = documentId.substring("raw:".length());
			if (!path.startsWith("/") || !safe(path)) {
				return failure(Problem.INVALID);
			}
			return success(new File(path));
		}
		// "msf:123", "123": a row of the downloads database; it stands for a download, not for a place.
		return failure(Problem.NO_PATH);
	}

	private static boolean safe(final String path) {
		for (final String part : path.split("/", -1)) {
			if (part.equals("..")) {
				return false;
			}
		}
		return path.indexOf('\0') < 0;
	}

	private static Result success(final File folder) {
		return new Result(folder, null);
	}

	private static Result failure(final Problem problem) {
		return new Result(null, problem);
	}
}
