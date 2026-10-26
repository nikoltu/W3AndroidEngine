package com.etheller.warsmash.android;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DataFolderCheck {
	enum Kind {
		CASC,
		FOLDER,
		NONE
	}

	static final String CLASSIC_PREFIXES = "war3.w3mod,war3.w3mod\\_deprecated.w3mod,war3.w3mod\\_locales\\enus.w3mod";

	private static final Map<String, String> DESCRIPTIONS = new HashMap<>();

	private DataFolderCheck() {
	}

	static Kind detect(final File folder) {
		if ((folder == null) || !folder.isDirectory() || !folder.canRead()) {
			return Kind.NONE;
		}
		if (new File(folder, ".build.info").isFile() && hasChildFolder(folder, "Data")) {
			return Kind.CASC;
		}
		// An extracted game data folder, as the desktop Folder data source reads it.
		if (hasChildFolder(folder, "units") && hasChildFolder(folder, "ui")) {
			return Kind.FOLDER;
		}
		return Kind.NONE;
	}

	static String dataSourcesSection(final Kind kind, final File dataFolder, final File... extraFolders) {
		final List<String> lines = new ArrayList<>();
		int index = 0;
		lines.add("Type00=" + (kind == Kind.CASC ? "CASC" : "Folder"));
		lines.add("Path00=\"" + dataFolder.getAbsolutePath() + "\"");
		if (kind == Kind.CASC) {
			lines.add("Prefixes00=" + CLASSIC_PREFIXES);
		}
		for (final File extraFolder : extraFolders) {
			if (extraFolder.isDirectory()) {
				index++;
				final String id = String.format(Locale.ROOT, "%02d", index);
				lines.add("Type" + id + "=Folder");
				lines.add("Path" + id + "=\"" + extraFolder.getAbsolutePath() + "\"");
			}
		}
		final StringBuilder section = new StringBuilder("[DataSources]\n");
		section.append("Count=").append(index + 1).append('\n');
		for (final String line : lines) {
			section.append(line).append('\n');
		}
		return section.toString();
	}

	/** A short line for the launcher: the CASC build version, or how many files an extracted folder holds. */
	static synchronized String describe(final Kind kind, final File folder) {
		final String key = kind + ":" + folder.getAbsolutePath();
		String description = DESCRIPTIONS.get(key);
		if (description == null) {
			description = kind == Kind.CASC ? cascVersion(folder)
					: String.format(Locale.US, "%,d files", countFiles(folder));
			DESCRIPTIONS.put(key, description);
		}
		return description;
	}

	private static String cascVersion(final File folder) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(new File(folder, ".build.info")), StandardCharsets.UTF_8))) {
			final String header = reader.readLine();
			final String values = reader.readLine();
			if ((header != null) && (values != null)) {
				final String[] names = header.split("\\|");
				final String[] fields = values.split("\\|", -1);
				for (int i = 0; (i < names.length) && (i < fields.length); i++) {
					if (names[i].startsWith("Version!")) {
						return "version " + fields[i];
					}
				}
			}
		}
		catch (final IOException e) {
			return "";
		}
		return "";
	}

	private static int countFiles(final File folder) {
		final File[] children = folder.listFiles();
		if (children == null) {
			return 0;
		}
		int count = 0;
		for (final File child : children) {
			count += child.isDirectory() ? countFiles(child) : 1;
		}
		return count;
	}

	private static boolean hasChildFolder(final File folder, final String name) {
		final File[] children = folder.listFiles();
		if (children == null) {
			return false;
		}
		for (final File child : children) {
			if (child.isDirectory() && child.getName().equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}
}
