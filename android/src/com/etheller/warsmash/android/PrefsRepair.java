package com.etheller.warsmash.android;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

/**
 * Android reads a preferences file that is cut off mid-tag as an empty one and then writes the empty map back on the
 * next save, so every setting would be gone. A broken file is kept as ".corrupt" and replaced by the settings that are
 * still whole in it, before anything reads the preferences.
 */
final class PrefsRepair {
	private static final String TAG = "PrefsRepair";
	private static final Pattern ENTRY = Pattern.compile("<(?:int|long|float|boolean) name=\"[^\"]*\" value=\"[^\"]*\"\s*/>"
			+ "|<string name=\"[^\"]*\" value=\"[^\"]*\"\s*/>" + "|<string name=\"[^\"]*\">[^<]*</string>"
			+ "|<set name=\"[^\"]*\">(?:\s*<string>[^<]*</string>)*\s*</set>");

	private PrefsRepair() {
	}

	static void run(final Context context) {
		final File[] files = new File(context.getApplicationInfo().dataDir, "shared_prefs").listFiles();
		if (files == null) {
			return;
		}
		for (final File file : files) {
			if (!file.getName().endsWith(".xml") || (file.length() == 0)) {
				continue;
			}
			try {
				final byte[] bytes = Files.readAllBytes(file.toPath());
				if (isWhole(bytes)) {
					continue;
				}
				Files.copy(file.toPath(), new File(file.getPath() + ".corrupt").toPath(),
						StandardCopyOption.REPLACE_EXISTING);
				final StringBuilder repaired = new StringBuilder("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n");
				final Matcher matcher = ENTRY.matcher(new String(bytes, StandardCharsets.UTF_8));
				while (matcher.find()) {
					repaired.append("    ").append(matcher.group()).append('\n');
				}
				repaired.append("</map>\n");
				final File part = new File(file.getPath() + ".part");
				Files.write(part.toPath(), repaired.toString().getBytes(StandardCharsets.UTF_8));
				Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
				Log.w(TAG, file.getName() + " was cut off; kept the whole settings, the original is " + file.getName() + ".corrupt");
			}
			catch (final IOException | RuntimeException e) {
				Log.e(TAG, "Unable to repair " + file, e);
			}
		}
	}

	private static boolean isWhole(final byte[] bytes) {
		// The parser may take a file that ends inside the last element for a finished one, so the closing tag is checked too.
		if (!new String(bytes, StandardCharsets.UTF_8).trim().endsWith("</map>")) {
			return false;
		}
		try {
			final XmlPullParser parser = Xml.newPullParser();
			parser.setInput(new ByteArrayInputStream(bytes), null);
			while (parser.next() != XmlPullParser.END_DOCUMENT) {
				// Reading to the end finds a cut-off tag.
			}
			return true;
		}
		catch (final Exception e) {
			return false;
		}
	}
}
