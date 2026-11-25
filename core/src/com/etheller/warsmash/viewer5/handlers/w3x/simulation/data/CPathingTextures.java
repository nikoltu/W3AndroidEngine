package com.etheller.warsmash.viewer5.handlers.w3x.simulation.data;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.tga.TgaFile;

/**
 * The pathing textures of buildings and destructables (their pathTex and pathTexDeath fields), read once from the
 * map's data and shared by every type that names them. The game needs nothing drawn to place its pathing.
 */
public final class CPathingTextures {
	private final DataSource dataSource;
	private final Map<String, ArgbImage> textures = new HashMap<>();

	public CPathingTextures(final DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/** The texture at the path, or null for none ("" or "_") or one that cannot be read. */
	public ArgbImage get(final String path) {
		if ((path == null) || path.isEmpty() || "_".equals(path) || "none".equalsIgnoreCase(path)) {
			return null;
		}
		final String key = path.toLowerCase(Locale.ROOT);
		ArgbImage texture = this.textures.get(key);
		if (texture == null) {
			try {
				// Footprints are simulation data: no graphics package may change them.
				final DataSource data = this.dataSource.graphicsIndependent();
				if (key.endsWith(".tga")) {
					texture = TgaFile.readTGAPixels(path, data.getResourceAsStream(path));
				}
				else {
					try (InputStream stream = data.getResourceAsStream(path)) {
						texture = ImageUtils.getArgbImage(stream, path);
					}
				}
				this.textures.put(key, texture);
			}
			catch (final Exception exc) {
				Log.error("Failure to get pathing: " + exc.getClass() + ":" + exc.getMessage());
			}
		}
		return texture;
	}
}
