package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

class StorageTreeTest {
	private static final File PRIMARY = new File("/storage/emulated/0");
	private static final File DOWNLOADS = new File("/storage/emulated/0/Download");

	private static StorageTree.Result resolve(final String authority, final String id) {
		return StorageTree.resolve(authority, id, PRIMARY, DOWNLOADS);
	}

	private static String path(final StorageTree.Result result) {
		return result.folder.getPath().replace(File.separatorChar, '/');
	}

	@Test
	void internalStorageFolders() {
		assertEquals("/storage/emulated/0", path(resolve(StorageTree.AUTHORITY_STORAGE, "primary:")));
		assertEquals("/storage/emulated/0/Games/WC3", path(resolve(StorageTree.AUTHORITY_STORAGE, "primary:Games/WC3")));
		assertEquals("/storage/emulated/0/Documents/WC3", path(resolve(StorageTree.AUTHORITY_STORAGE, "home:WC3")));
	}

	@Test
	void aRemovableCardIsItsOwnVolume() {
		assertEquals("/storage/1234-ABCD/WC3", path(resolve(StorageTree.AUTHORITY_STORAGE, "1234-ABCD:WC3")));
		assertEquals("/storage/1234-ABCD", path(resolve(StorageTree.AUTHORITY_STORAGE, "1234-ABCD:")));
	}

	@Test
	void downloadsRootAndRawIds() {
		assertEquals("/storage/emulated/0/Download", path(resolve(StorageTree.AUTHORITY_DOWNLOADS, "downloads")));
		assertEquals("/storage/emulated/0/Download/WC3",
				path(resolve(StorageTree.AUTHORITY_DOWNLOADS, "raw:/storage/emulated/0/Download/WC3")));
		assertEquals("/storage/1234-ABCD/Download", path(resolve(StorageTree.AUTHORITY_DOWNLOADS, "raw:/storage/1234-ABCD/Download")));
	}

	@Test
	void downloadsRowsHaveNoPath() {
		assertEquals(StorageTree.Problem.NO_PATH, resolve(StorageTree.AUTHORITY_DOWNLOADS, "42").problem);
		assertEquals(StorageTree.Problem.NO_PATH, resolve(StorageTree.AUTHORITY_DOWNLOADS, "msf:42").problem);
	}

	@Test
	void otherProvidersAreRefusedWithAReason() {
		final StorageTree.Result result = resolve("com.android.providers.media.documents", "image:5");
		assertFalse(result.ok());
		assertNull(result.folder);
		assertEquals(StorageTree.Problem.UNKNOWN_PROVIDER, result.problem);
		assertEquals(StorageTree.Problem.UNKNOWN_PROVIDER, resolve("com.google.android.apps.docs.storage", "abc").problem);
	}

	@Test
	void malformedIdsAreRefused() {
		assertEquals(StorageTree.Problem.INVALID, resolve(StorageTree.AUTHORITY_STORAGE, "primary").problem);
		assertEquals(StorageTree.Problem.INVALID, resolve(StorageTree.AUTHORITY_STORAGE, ":x").problem);
		assertEquals(StorageTree.Problem.INVALID, resolve(StorageTree.AUTHORITY_STORAGE, "primary:../../data").problem);
		assertEquals(StorageTree.Problem.INVALID, resolve(StorageTree.AUTHORITY_DOWNLOADS, "raw:relative/dir").problem);
		assertEquals(StorageTree.Problem.INVALID, resolve(StorageTree.AUTHORITY_DOWNLOADS, "raw:/sdcard/../data").problem);
		assertEquals(StorageTree.Problem.INVALID, resolve(null, "primary:x").problem);
		assertEquals(StorageTree.Problem.INVALID, resolve(StorageTree.AUTHORITY_STORAGE, "").problem);
		assertTrue(resolve(StorageTree.AUTHORITY_STORAGE, "primary:a..b").ok());
	}
}
