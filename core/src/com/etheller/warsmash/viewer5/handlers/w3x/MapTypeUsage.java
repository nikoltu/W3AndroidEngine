package com.etheller.warsmash.viewer5.handlers.w3x;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.blp.TextureTranscoder;

/**
 * The unit types a map's games made after their opening steps, and when, kept between games: a script that makes the
 * units of its waves from variables names those types nowhere in a creation call, so the loading screen could not tell
 * them from the many the script only mentions. The next loading of the same script has the types of the earlier games
 * ready, earliest first, instead of the first unit of each loading its model in the frame the script made it in.
 */
final class MapTypeUsage {
	private static final String FILE = "script-type-usage.lst";
	/** Types made later than this are not kept: they are left to the loading that follows. */
	static final int MAX_TICK = 4800;
	private static final int MAX_LINES = 3000;
	private static final long WRITE_INTERVAL_NANOS = 3_000_000_000L;

	private final String key;
	private final Map<War3ID, Integer> types = new LinkedHashMap<>();
	private boolean dirty;
	private long lastWrite = System.nanoTime();

	MapTypeUsage(final String script) {
		this.key = Integer.toHexString(script.hashCode()) + "." + Integer.toHexString(script.length());
		final File file = file();
		if ((file != null) && file.isFile()) {
			try {
				for (final String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
						.split("\n")) {
					final String[] parts = line.trim().split(" ");
					if ((parts.length == 3) && parts[0].equals(this.key)) {
						this.types.put(War3ID.fromString(parts[1]), Integer.parseInt(parts[2]));
					}
				}
			}
			catch (final IOException | RuntimeException e) {
				this.types.clear();
			}
		}
	}

	/** The types earlier games made, the ones made first earliest. */
	List<War3ID> typesByTick() {
		final List<Map.Entry<War3ID, Integer>> entries = new ArrayList<>(this.types.entrySet());
		entries.sort((a, b) -> Integer.compare(a.getValue(), b.getValue()));
		final List<War3ID> result = new ArrayList<>(entries.size());
		for (final Map.Entry<War3ID, Integer> entry : entries) {
			result.add(entry.getKey());
		}
		return result;
	}

	/** A unit of the type was made at a game step; only the first of each type is noted. */
	void created(final War3ID typeId, final int tick) {
		if ((tick > 1) && (tick <= MAX_TICK) && !this.types.containsKey(typeId)) {
			this.types.put(typeId, tick);
			this.dirty = true;
		}
	}

	/** Writes what was noted, at most every few seconds, so that a game closed by the system keeps most of it. */
	void flushIfDue() {
		if (this.dirty && ((System.nanoTime() - this.lastWrite) >= WRITE_INTERVAL_NANOS)) {
			flush();
		}
	}

	void flush() {
		this.dirty = false;
		this.lastWrite = System.nanoTime();
		try {
			final File file = file();
			if (file == null) {
				return;
			}
			final StringBuilder text = new StringBuilder();
			int lines = 0;
			for (final Map.Entry<War3ID, Integer> entry : this.types.entrySet()) {
				text.append(this.key).append(' ').append(entry.getKey()).append(' ').append(entry.getValue())
						.append('\n');
				lines++;
			}
			if (file.isFile()) {
				for (final String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
						.split("\n")) {
					if ((lines < MAX_LINES) && !line.isEmpty() && !line.startsWith(this.key + " ")) {
						text.append(line).append('\n');
						lines++;
					}
				}
			}
			final File partial = new File(file.getParentFile(), FILE + ".tmp");
			Files.write(partial.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
			Files.move(partial.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		catch (final IOException | RuntimeException e) {
			Log.warn("Types a map made not kept: " + e);
		}
	}

	private static File file() {
		final File folder = TextureTranscoder.cacheFolder();
		return folder == null ? null : new File(folder, FILE);
	}
}
