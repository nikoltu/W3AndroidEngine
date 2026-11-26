/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.backends.lwjgl;

import static com.badlogic.gdx.utils.SharedLibraryLoader.*;

import com.badlogic.gdx.utils.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Local override of libGDX's LWJGL natives loader. On macOS, the engine links LWJGL 2 from
 * the Legacy Fabric rebuild ("2.9.4+legacyfabric.18"), which names its native libraries per
 * architecture ("liblwjgl-macos-aarch64.dylib", ...) so Apple Silicon Macs are supported
 * natively. On Windows and Linux, the stock libGDX 1.14.2 loader logic is used unchanged.
 */
public final class LwjglNativesLoader {
	static public boolean load = true;

	static {
		System.setProperty("org.lwjgl.input.Mouse.allowNegativeMouseCoords", "true");

		// Don't extract natives if using JWS.
		try {
			Method method = Class.forName("javax.jnlp.ServiceManager").getDeclaredMethod("lookup", new Class[] {String.class});
			method.invoke(null, "javax.jnlp.PersistenceService");
			load = false;
		} catch (Throwable ex) {
			load = true;
		}
	}

	/** Architecture name as used in the Legacy Fabric LWJGL natives file names. */
	static private String lwjglArchName() {
		final String javaArch = System.getProperty("os.arch", "").toLowerCase();
		if (javaArch.contains("aarch64") || javaArch.contains("arm64") || javaArch.contains("armv8")) return "aarch64";
		if (javaArch.contains("amd64") || javaArch.contains("x86_64") || javaArch.contains("x64")) return "amd64";
		return "i386";
	}

	/** Extracts the first of the candidate resource names that exists on the classpath, into dir. */
	static private void extractFirstTo(final SharedLibraryLoader loader, final String[] candidates, final File dir)
			throws IOException {
		Throwable last = null;
		for (final String candidate : candidates) {
			try {
				loader.extractFileTo(candidate, dir);
				return;
			}
			catch (final Throwable t) {
				last = t;
			}
		}
		throw new IOException("No LWJGL native found on the classpath: " + String.join(", ", candidates), last);
	}

	/** Extracts the first of the candidate resource names that exists on the classpath, to a temp location. */
	static private File extractFirst(final SharedLibraryLoader loader, final String[] candidates) throws IOException {
		Throwable last = null;
		for (final String candidate : candidates) {
			try {
				return loader.extractFile(candidate, null);
			}
			catch (final Throwable t) {
				last = t;
			}
		}
		throw new IOException("No LWJGL native found on the classpath: " + String.join(", ", candidates), last);
	}

	/** Extracts the LWJGL native libraries from the classpath and sets the "org.lwjgl.librarypath" system property. */
	static public void load () {
		GdxNativesLoader.load();
		if (GdxNativesLoader.disableNativesLoading) return;
		if (!load) return;

		final SharedLibraryLoader loader = new SharedLibraryLoader();
		File nativesDir = null;
		try {
			if (os == Os.MacOsX) {
				// Warsmash macOS adaptation: Legacy Fabric natives include per-arch names.
				final String arch = lwjglArchName();
				final String[] lwjglLibs = new String[] {"liblwjgl-macos-" + arch + ".dylib", "liblwjgl.dylib"};
				final String[] openalLibs = new String[] {"libopenal-" + arch + ".dylib", "openal.dylib"};
				nativesDir = extractFirst(loader, lwjglLibs).getParentFile();
				if (!LwjglApplicationConfiguration.disableAudio) extractFirstTo(loader, openalLibs, nativesDir);
			} else {
				// Windows/Linux: stock libGDX 1.14.2 loader logic, unchanged.
				String lwjglLib = null;
				String openalLib = null;
				switch (os) {
				case Windows:
					switch (bitness) {
					case _32:
						lwjglLib = "lwjgl.dll";
						openalLib = "OpenAL32.dll";
						break;
					case _64:
						lwjglLib = "lwjgl64.dll";
						openalLib = "OpenAL64.dll";
						break;
					}
					break;
				case Linux:
					switch (bitness) {
					case _32:
						lwjglLib = "liblwjgl.so";
						openalLib = "libopenal.so";
						break;
					case _64:
						lwjglLib = "liblwjgl64.so";
						openalLib = "libopenal64.so";
						break;
					}
					break;
				}
				if (lwjglLib == null) throw new GdxRuntimeException("Unknown LWJGL platform: " + os + ", " + bitness);
				nativesDir = loader.extractFile(lwjglLib, null).getParentFile();
				if (!LwjglApplicationConfiguration.disableAudio) loader.extractFileTo(openalLib, nativesDir);
			}
		} catch (Throwable ex) {
			throw new GdxRuntimeException("Unable to extract LWJGL natives.", ex);
		}
		System.setProperty("org.lwjgl.librarypath", nativesDir.getAbsolutePath());
		load = false;
	}
}
