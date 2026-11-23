package com.etheller.warsmash.parsers.facefx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.etheller.warsmash.datasources.DataSource;

/**
 * The FaceFX data of a game's files: the face of a character next to its portrait model and the animations of the
 * spoken lines. Nothing is read before a line plays, and data without FaceFX (Classic portraits, a data source without
 * the Reforged files) answers null everywhere.
 */
public final class FaceFxLibrary {
	private static final String ACTOR_EXTENSION = ".facefx_ingame";
	private static final String PORTRAIT_SUFFIX = "_portrait";

	/**
	 * The animsets of each game data, by the data's graphics independent view: every map played on the same data lists
	 * the same ones, and listing them through a map's data took a frame of almost half a second at the end of every
	 * loading screen with spoken lines.
	 */
	private static final Map<DataSource, List<String>> GAME_ANIMSETS = new WeakHashMap<>();

	private final DataSource data;
	private final DataSource gameData;
	private final DataSource mapData;
	private final Map<String, FaceFxActor> actors = new HashMap<>();
	private FaceFxAnimIndex index;

	public FaceFxLibrary(final DataSource data) {
		this(data, null, null);
	}

	/**
	 * @param data     the map's data, which the files are read from
	 * @param gameData the game data under the map, whose animsets are listed once for all the maps played on it; null
	 *                 to list them through the map's data
	 * @param mapData  the map's own files, listed for the animsets a map brings along; null for none
	 */
	public FaceFxLibrary(final DataSource data, final DataSource gameData, final DataSource mapData) {
		this.data = data;
		this.gameData = gameData;
		this.mapData = mapData;
	}

	/**
	 * {@code Units\Human\Footman\Footman_Portrait.mdx} has its face in {@code Units\Human\Footman\Footman.facefx_ingame};
	 * null for a model path that cannot have one.
	 */
	public static String actorPathForModel(final String modelPath) {
		if (modelPath == null) {
			return null;
		}
		final String path = modelPath.replace('/', '\\');
		final int slash = path.lastIndexOf('\\');
		String name = path.substring(slash + 1);
		final int dot = name.lastIndexOf('.');
		if (dot <= 0) {
			return null;
		}
		name = name.substring(0, dot);
		if (name.toLowerCase(Locale.ROOT).endsWith(PORTRAIT_SUFFIX) && (name.length() > PORTRAIT_SUFFIX.length())) {
			name = name.substring(0, name.length() - PORTRAIT_SUFFIX.length());
		}
		return path.substring(0, slash + 1) + name + ACTOR_EXTENSION;
	}

	/** @return the speaker name an actor file has: its name without folders and extension, lower case */
	public static String speakerOfActor(final String actorPath) {
		return FaceFxAnimIndex.speakerOfFile(actorPath.replace('/', '\\'));
	}

	/** @return the face of the character whose portrait model is at this path, null when it has none */
	public synchronized FaceFxActor actorForModel(final String modelPath) {
		final String path = actorPathForModel(modelPath);
		if (path == null) {
			return null;
		}
		final String key = path.toLowerCase(Locale.ROOT);
		if (this.actors.containsKey(key)) {
			return this.actors.get(key);
		}
		FaceFxActor actor = null;
		try {
			final byte[] bytes = read(path);
			if (bytes != null) {
				actor = FaceFxActor.read(bytes);
			}
		}
		catch (final IOException | RuntimeException e) {
			actor = null;
		}
		this.actors.put(key, actor);
		return actor;
	}

	/**
	 * @param modelPath the portrait model, whose file name is the speaker's
	 * @param soundPath the sound file of the line
	 * @return the animation of the line, null when there is none
	 */
	public FaceFxAnimSet.Anim animFor(final String modelPath, final String soundPath) {
		final String actorPath = actorPathForModel(modelPath);
		return index().find((actorPath == null) ? null : speakerOfActor(actorPath), soundPath);
	}

	/** Lists the animations now, which took a frame of a tenth of a second and more at the first line. */
	public void prepare() {
		index();
	}

	private synchronized FaceFxAnimIndex index() {
		if (this.index == null) {
			this.index = new FaceFxAnimIndex(listAnimsets(), this::read);
		}
		return this.index;
	}

	/** The animsets of the map's data, the game's first. */
	Collection<String> listAnimsets() {
		if (this.gameData == null) {
			return this.data.getListfileByExtension(FaceFxAnimIndex.ANIMSET_EXTENSION);
		}
		final List<String> game = gameAnimsets(this.gameData);
		if (this.mapData == null) {
			return game;
		}
		// In the order the map's data has them: the game's files, then the map's.
		final List<String> listed = new ArrayList<>(game);
		listed.addAll(this.mapData.getListfileByExtension(FaceFxAnimIndex.ANIMSET_EXTENSION));
		return listed;
	}

	/** The logical paths of the game data's animsets, listed at the first map played on that data. */
	static List<String> gameAnimsets(final DataSource gameData) {
		final DataSource key = gameData.graphicsIndependent();
		synchronized (GAME_ANIMSETS) {
			List<String> animsets = GAME_ANIMSETS.get(key);
			if (animsets == null) {
				final Set<String> logical = new LinkedHashSet<>();
				for (final String path : gameData.getListfileByExtension(FaceFxAnimIndex.ANIMSET_EXTENSION)) {
					logical.add(FaceFxAnimIndex.logicalPath(path));
				}
				animsets = Collections.unmodifiableList(new ArrayList<>(logical));
				GAME_ANIMSETS.put(key, animsets);
			}
			return animsets;
		}
	}

	private byte[] read(final String path) throws IOException {
		if (!this.data.has(path)) {
			return null;
		}
		final ByteBuffer buffer = this.data.read(path);
		if (buffer == null) {
			return null;
		}
		final byte[] bytes = new byte[buffer.remaining()];
		buffer.duplicate().get(bytes);
		return bytes;
	}
}
