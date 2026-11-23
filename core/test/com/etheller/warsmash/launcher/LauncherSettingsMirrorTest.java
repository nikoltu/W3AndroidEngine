package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

class LauncherSettingsMirrorTest {
	@Test
	public void dataFolderSurvivesWriteAndRead() throws Exception {
		final File storage = Files.createTempDirectory("storage").toFile();
		final File file = LauncherSettingsMirror.file(storage);
		assertEquals(new File(new File(storage, "WC3-Android"), LauncherSettingsMirror.FILE), file);
		assertNull(LauncherSettingsMirror.readDataFolder(file));
		final String folder = "/storage/emulated/0/Download/Game installai GOG/Warcraft III";
		assertTrue(LauncherSettingsMirror.writeDataFolder(file, folder));
		assertEquals(folder, LauncherSettingsMirror.readDataFolder(file));
		assertTrue(LauncherSettingsMirror.writeDataFolder(file, "/storage/1234-ABCD/WC3"));
		assertEquals("/storage/1234-ABCD/WC3", LauncherSettingsMirror.readDataFolder(file));
		assertFalse(new File(file.getParentFile(), LauncherSettingsMirror.FILE + ".part").exists());
	}

	@Test
	public void unknownVersionAndEmptyValuesAreIgnored() throws Exception {
		final File storage = Files.createTempDirectory("storage").toFile();
		final File file = LauncherSettingsMirror.file(storage);
		assertFalse(LauncherSettingsMirror.writeDataFolder(file, ""));
		assertFalse(LauncherSettingsMirror.writeDataFolder(file, null));
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), "version=2\ndataFolder=/x\n".getBytes("UTF-8"));
		assertNull(LauncherSettingsMirror.readDataFolder(file));
		Files.write(file.toPath(), "version=1\n".getBytes("UTF-8"));
		assertNull(LauncherSettingsMirror.readDataFolder(file));
	}
}
