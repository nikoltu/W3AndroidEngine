package com.etheller.warsmash.viewer5.handlers.w3x.replay;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;

/**
 * The moments a player captured with Capture a Bug, for a problem report: a text file beside the game's replay (moved
 * with it when the game ends), or beside the replays for a game that is not recorded. Only the player's own program
 * knows of them, so the game and its replay play the same with or without them.
 *
 * <pre>
 * map=&lt;map path&gt;
 * recorded=true|false
 * mark &lt;n&gt; tick=&lt;step&gt; time=&lt;game seconds&gt; camera=&lt;x&gt;,&lt;y&gt;
 * unit &lt;n&gt; handle=&lt;id&gt; type=&lt;rawcode&gt; owner=&lt;player&gt; x=&lt;x&gt; y=&lt;y&gt; life=&lt;life&gt;/&lt;max&gt;
 * </pre>
 */
public final class GameMarks {
	public static final String EXTENSION = ".marks";
	/** The marks of the last game that had no replay. */
	public static final String UNRECORDED = "Unrecorded";

	/** A selected unit as it was at the marked moment. */
	public static final class Unit {
		public final int handle;
		public final String type;
		public final int owner;
		public final float x;
		public final float y;
		public final float life;
		public final float maxLife;

		public Unit(final int handle, final String type, final int owner, final float x, final float y,
				final float life, final float maxLife) {
			this.handle = handle;
			this.type = type;
			this.owner = owner;
			this.x = x;
			this.y = y;
			this.life = life;
			this.maxLife = maxLife;
		}
	}

	private GameMarks() {
	}

	/** The marks file kept beside a replay file. */
	public static FileHandle of(final FileHandle replay) {
		return replay.sibling(replay.nameWithoutExtension() + EXTENSION);
	}

	/** Where the marks of a game without a replay go. */
	public static FileHandle unrecorded() {
		return GameReplays.folder().child(UNRECORDED + EXTENSION);
	}

	/** The first lines of a game's marks file. */
	public static String head(final String mapFile, final boolean recorded) {
		return "map=" + mapFile + "\nrecorded=" + recorded + "\n";
	}

	/** The lines of one mark. */
	public static String text(final int number, final int tick, final float seconds, final float cameraX,
			final float cameraY, final List<Unit> units) {
		final StringBuilder out = new StringBuilder(128);
		out.append(String.format(Locale.ROOT, "mark %d tick=%d time=%.2f camera=%.1f,%.1f\n", number, tick, seconds,
				cameraX, cameraY));
		for (final Unit unit : units) {
			out.append(String.format(Locale.ROOT, "unit %d handle=%d type=%s owner=%d x=%.1f y=%.1f life=%.1f/%.1f\n",
					number, unit.handle, unit.type, unit.owner, unit.x, unit.y, unit.life, unit.maxLife));
		}
		return out.toString();
	}

	/**
	 * Adds a mark to the file; the first mark of a game starts the file anew.
	 *
	 * @param head the file's first lines (see {@link #head}), written when {@code first}
	 */
	public static void write(final FileHandle file, final boolean first, final String head, final String mark)
			throws IOException {
		file.parent().mkdirs();
		try (OutputStream out = file.write(!first)) {
			if (first) {
				out.write(head.getBytes(StandardCharsets.UTF_8));
			}
			out.write(mark.getBytes(StandardCharsets.UTF_8));
		}
		catch (final RuntimeException e) {
			throw new IOException("The marks could not be written to " + file.path(), e);
		}
	}
}
