package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapFilesTest {
	@Test
	void absolutePathsAreFilesOfTheDevice() {
		assertTrue(MapFiles.isAbsolute("/sdcard/WC3-Android/Maps/a.w3x"));
		assertTrue(MapFiles.isAbsolute("file:///sdcard/a.w3x"));
		assertFalse(MapFiles.isAbsolute("Maps\\Custom\\a.w3x"));
		assertFalse(MapFiles.isAbsolute(null));
	}

	@Test
	void sharedStorageGoesByAllItsNames() {
		assertEquals("/storage/emulated/0/Maps/a (2).w3x", MapFiles.candidates("/sdcard/Maps/a (2).w3x").get(1));
		assertEquals("/sdcard/Maps/a.w3x", MapFiles.candidates("/storage/emulated/0/Maps/a.w3x").get(1));
		assertEquals("/sdcard/Maps/a.w3x", MapFiles.candidates(" file:///sdcard/Maps/a.w3x ").get(0));
		assertEquals(1, MapFiles.candidates("/data/local/tmp/a.w3x").size());
	}

	@Test
	void findsExistingFileOnly(@TempDir final Path dir) throws IOException {
		final Path map = Files.write(dir.resolve("m (1).w3x"), new byte[] { 1 });
		final File found = MapFiles.find(map.toString());
		assertNotNull(found);
		assertEquals(map.toFile(), found);
		assertNull(MapFiles.find(dir.resolve("none.w3x").toString()));
		assertTrue(MapFiles.describeMissing(dir.resolve("none.w3x").toString()).contains("folder exists true"));
	}
}
