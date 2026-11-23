package com.etheller.warsmash.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The launcher's own texts outside Android: the Android string resources file read as it is, so that both launchers
 * say the same.
 */
public final class LauncherStrings {
	private static final Pattern STRING = Pattern.compile("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>",
			Pattern.DOTALL);
	private final Map<String, String> strings = new HashMap<>();

	public LauncherStrings(final InputStream resources) throws IOException {
		final String xml = new String(LauncherImages.bytes(resources), StandardCharsets.UTF_8);
		final Matcher matcher = STRING.matcher(xml);
		while (matcher.find()) {
			this.strings.put(matcher.group(1), unescape(matcher.group(2)));
		}
	}

	/** The texts of a translation over these; a name it leaves out keeps its text. */
	public void overlay(final InputStream translation) throws IOException {
		final String xml = new String(LauncherImages.bytes(translation), StandardCharsets.UTF_8);
		final Matcher matcher = STRING.matcher(xml);
		while (matcher.find()) {
			this.strings.put(matcher.group(1), unescape(matcher.group(2)));
		}
	}

	private static String unescape(final String value) {
		final StringBuilder text = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if ((c == '\\') &&((i + 1) < value.length())) {
				final char next = value.charAt(++i);
				text.append(next == 'n' ? '\n' : next == 't' ? '\t' : next);
			}
			else {
				text.append(c);
			}
		}
		return text.toString().replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
				.replace("&apos;", "'").replace("&amp;", "&");
	}

	/** The text by its resource name; the name itself when it is missing. */
	public String get(final String name) {
		final String value = this.strings.get(name);
		return value != null ? value : name;
	}

	/** A size in bytes as people read it: 850 MB, 1.9 GB. */
	public static String size(final long bytes) {
		if (bytes < 1000) {
			return bytes + " B";
		}
		final String[] units = { "kB", "MB", "GB", "TB" };
		double value = bytes / 1000.0;
		int unit = 0;
		while ((value >= 1000) && (unit < (units.length - 1))) {
			value /= 1000;
			unit++;
		}
		return String.format(Locale.ROOT, value < 10 ? "%.1f %s" : "%.0f %s", value, units[unit]);
	}

	/** A text with its %1$s / %1$d arguments filled in. */
	public String get(final String name, final Object... arguments) {
		return String.format(Locale.ROOT, get(name), arguments);
	}
}
