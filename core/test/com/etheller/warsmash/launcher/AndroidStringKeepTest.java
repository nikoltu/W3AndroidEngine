package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The Android launcher looks some strings up by name at run time (options rows, LAN text, hotkey editor words, map
 * problems), which the resource shrinker of a release build cannot see: every such name must be defined and matched by
 * the keep rule.
 */
class AndroidStringKeepTest {
	private static Path root() {
		for (final String candidate : new String[] { "..", "." }) {
			final Path path = Paths.get(candidate);
			if (Files.isDirectory(path.resolve("android/res/raw"))) {
				return path;
			}
		}
		return null;
	}

	private static String read(final Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private static List<String> literals(final String source, final String regex) {
		final List<String> found = new ArrayList<>();
		final Matcher matcher = Pattern.compile(regex).matcher(source);
		while (matcher.find()) {
			found.add(matcher.group(1));
		}
		return found;
	}

	@SuppressWarnings("unchecked")
	private static void optionNames(final Set<String> names) throws IllegalAccessException {
		for (final Field field : LauncherOptions.class.getFields()) {
			if (!Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			final Object value = field.get(null);
			if (value instanceof String[]) {
				for (final String name : (String[]) value) {
					names.add(name);
				}
			}
			else if (value instanceof String[][]) {
				for (final String[] row : (String[][]) value) {
					for (final String name : row) {
						names.add(name);
					}
				}
			}
			else if (value instanceof List) {
				for (final Object row : (List<Object>) value) {
					if (row instanceof LauncherOptions.Row) {
						names.add(((LauncherOptions.Row) row).label);
						names.addAll(((LauncherOptions.Row) row).choices);
					}
				}
			}
		}
	}

	@Test
	void everyNameLookedUpAtRunTimeSurvivesTheReleaseBuild() throws Exception {
		final Path root = root();
		Assumptions.assumeTrue(root != null, "the android module is not next to core");
		final String keep = read(root.resolve("android/res/raw/launcher_keep.xml"));
		final List<String> prefixes = new ArrayList<>();
		for (final String rule : literals(keep, "tools:keep=\"([^\"]*)\"")) {
			for (final String part : rule.split(",")) {
				final String trimmed = part.trim();
				if (trimmed.startsWith("@string/") && trimmed.endsWith("*")) {
					prefixes.add(trimmed.substring("@string/".length(), trimmed.length() - 1));
				}
			}
		}
		assertTrue(!prefixes.isEmpty(), "launcher_keep.xml keeps nothing");

		final Set<String> names = new TreeSet<>();
		optionNames(names);
		names.addAll(literals(read(root.resolve("core/src/com/etheller/warsmash/launcher/lan/LanText.java")),
				"\\bt\\(\"([a-z0-9_]+)\""));
		final String editor = read(root.resolve("android/src/com/etheller/warsmash/android/HotkeyEditorPage.java"));
		final Matcher words = Pattern.compile("WORDS = \\{([^}]*)\\}").matcher(editor);
		assertTrue(words.find(), "the hotkey editor's word list is gone");
		for (final String word : literals(words.group(1), "\"([a-z0-9_]+)\"")) {
			names.add("controls_" + word);
		}
		for (final MapIssue issue : MapIssue.values()) {
			if (issue.message != null) {
				names.add(issue.message);
				names.add(issue.tag);
			}
		}
		for (final String file : new String[] { "OptionsPage", "HotkeyEditorPage", "LanPage", "LobbyPage" }) {
			names.addAll(literals(read(root.resolve("android/src/com/etheller/warsmash/android/" + file + ".java")),
					"stringId\\(\"([a-z0-9_]+)\"\\)"));
		}

		final StringBuilder defined = new StringBuilder();
		try (java.util.stream.Stream<Path> files = Files.list(root.resolve("android/res/values"))) {
			for (final Path file : (Iterable<Path>) files::iterator) {
				defined.append(read(file));
			}
		}
		final Set<String> missing = new TreeSet<>();
		final Set<String> dropped = new TreeSet<>();
		for (final String name : names) {
			if (!defined.toString().contains("name=\"" + name + "\"")) {
				missing.add(name);
			}
			boolean kept = false;
			for (final String prefix : prefixes) {
				kept |= name.startsWith(prefix);
			}
			if (!kept) {
				dropped.add(name);
			}
		}
		assertTrue(missing.isEmpty(), "looked up by name but not defined in the Android strings: " + missing);
		assertTrue(dropped.isEmpty(), "looked up by name but not kept by launcher_keep.xml: " + dropped);
	}
}
