package com.etheller.warsmash.launcher;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where the launcher's own words come from for the code that has no view of its own: the platform's string resources
 * (the Android strings file, which the desktop launcher reads as it is), in the game language. A platform that does not
 * have the file on its class path tells where they come from with {@link #use}.
 */
public final class LauncherText {
	/** Where the words come from: a resource name and the values of its %n$s places. */
	public interface Source {
		String get(String name, Object... arguments);
	}

	private static volatile Source source;

	private LauncherText() {
	}

	public static void use(final Source words) {
		source = words;
	}

	public static void use(final LauncherStrings words) {
		source = fromStrings(words);
	}

	private static Source fromStrings(final LauncherStrings words) {
		return (name, arguments) -> arguments.length == 0 ? words.get(name) : words.get(name, arguments);
	}

	private static Source source() {
		Source found = source;
		if (found == null) {
			try (InputStream in = LauncherText.class.getResourceAsStream("/strings.xml")) {
				if (in != null) {
					found = fromStrings(new LauncherStrings(in));
					source = found;
				}
			}
			catch (final IOException e) {
				// the names stand in for the words
			}
		}
		return found;
	}

	/** The words by their resource name; the name itself when there are none. */
	public static String get(final String name, final Object... arguments) {
		final Source words = source();
		return words == null ? name : words.get(name, arguments);
	}
}
