package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileOnDiskTest {
	@TempDir
	File temp;

	@Test
	void aCompoundOfFoldersGivesTheFileOfTheSourceThatReadsIt() throws Exception {
		final File low = new File(this.temp, "low");
		final File high = new File(this.temp, "high");
		assertEquals(true, new File(low, "maps").mkdirs());
		assertEquals(true, new File(high, "maps").mkdirs());
		Files.write(new File(low, "maps/a.w3x").toPath(), new byte[] { 1 });
		Files.write(new File(low, "maps/b.w3x").toPath(), new byte[] { 2 });
		Files.write(new File(high, "maps/a.w3x").toPath(), new byte[] { 3 });
		final DataSource data = new CompoundDataSource(
				Arrays.<DataSource>asList(new FolderDataSource(low.toPath()), new FolderDataSource(high.toPath())));
		assertEquals(new File(high, "maps/a.w3x").getCanonicalFile(),
				data.fileOnDisk("maps/a.w3x").getCanonicalFile());
		assertEquals(new File(low, "maps/b.w3x").getCanonicalFile(),
				data.fileOnDisk("Maps/B.w3x").getCanonicalFile());
		assertNull(data.fileOnDisk("maps/c.w3x"));
	}
}
