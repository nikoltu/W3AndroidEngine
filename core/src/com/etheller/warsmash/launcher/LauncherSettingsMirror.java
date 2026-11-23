package com.etheller.warsmash.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * A copy of the launcher settings that must outlive the application, kept beside the profiles in the shared storage:
 * the game data folder, which the private preferences lose on an uninstall. Device specific choices (graphics, touch
 * tuning) are not copied, since a reinstall may be on another device or build.
 */
public final class LauncherSettingsMirror {
	public static final String FILE = "launcher-settings.txt";
	private static final String VERSION = "version";
	private static final String CURRENT_VERSION = "1";
	private static final String DATA_FOLDER = "dataFolder";

	private LauncherSettingsMirror() {
	}

	/** The copy's file, in the folder above the profiles ("WC3-Android" of the shared storage). */
	public static File file(final File sharedStorage) {
		return new File(ProfileStorage.sharedFolder(sharedStorage).getParentFile(), FILE);
	}

	/** The data folder the copy names, or null when there is no readable copy of a version this build knows. */
	public static String readDataFolder(final File file) {
		if ((file == null) || !file.isFile()) {
			return null;
		}
		final Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(file.toPath())) {
			properties.load(in);
		}
		catch (final IOException | RuntimeException e) {
			return null;
		}
		if (!CURRENT_VERSION.equals(properties.getProperty(VERSION))) {
			return null;
		}
		final String folder = properties.getProperty(DATA_FOLDER);
		return (folder == null) || folder.isEmpty() ? null : folder;
	}

	/** Writes the data folder to the copy unless it is already there; the file is replaced whole or not at all. */
	public static boolean writeDataFolder(final File file, final String dataFolder) {
		if ((file == null) || (dataFolder == null) || dataFolder.isEmpty()) {
			return false;
		}
		if (dataFolder.equals(readDataFolder(file))) {
			return true;
		}
		final Properties properties = new Properties();
		properties.setProperty(VERSION, CURRENT_VERSION);
		properties.setProperty(DATA_FOLDER, dataFolder);
		final File parent = file.getParentFile();
		final File part = new File(parent, FILE + ".part");
		try {
			if ((parent != null) && !parent.isDirectory() && !parent.mkdirs()) {
				return false;
			}
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			properties.store(bytes, null);
			Files.write(part.toPath(), bytes.toByteArray());
			Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return true;
		}
		catch (final IOException | RuntimeException e) {
			part.delete();
			return false;
		}
	}
}
