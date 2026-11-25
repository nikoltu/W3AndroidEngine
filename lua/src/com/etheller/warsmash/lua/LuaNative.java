package com.etheller.warsmash.lua;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** Finds and loads the native Lua library: the APK's on Android, a file or a class path resource on the desktop. */
final class LuaNative {
	private static final String LIBRARY = "warsmashlua";
	private static boolean loaded;

	private LuaNative() {
	}

	static synchronized void load() {
		if (loaded) {
			return;
		}
		final String path = System.getProperty("warsmash.lua.native");
		if (path != null && !path.isEmpty()) {
			System.load(new File(path).getAbsolutePath());
			loaded = true;
			return;
		}
		UnsatisfiedLinkError failure;
		try {
			System.loadLibrary(LIBRARY);
			loaded = true;
			return;
		}
		catch (final UnsatisfiedLinkError e) {
			failure = e;
		}
		final String name = System.mapLibraryName(LIBRARY);
		final String resource = "/natives/" + platform() + "/" + name;
		try (InputStream in = LuaNative.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw failure;
			}
			final int dot = name.lastIndexOf('.');
			final Path temp = Files.createTempFile(name.substring(0, dot), name.substring(dot));
			Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
			temp.toFile().deleteOnExit();
			System.load(temp.toAbsolutePath().toString());
			loaded = true;
		}
		catch (final IOException e) {
			throw new UnsatisfiedLinkError("Cannot extract the Lua library: " + e);
		}
	}

	static String platform() {
		final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		final String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		final String osName = os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
		final String archName = (arch.equals("amd64") || arch.equals("x86_64")) ? "x86_64"
				: (arch.equals("aarch64") || arch.equals("arm64")) ? "arm64" : arch;
		return osName + "-" + archName;
	}
}
