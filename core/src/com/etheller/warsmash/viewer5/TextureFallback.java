package com.etheller.warsmash.viewer5;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.StreamBytes;
import com.etheller.warsmash.viewer5.handlers.blp.BlpDecoder;
import com.etheller.warsmash.viewer5.handlers.blp.DdsDecoder;
import com.etheller.warsmash.viewer5.handlers.blp.MipLevels;

/**
 * What a texture shows when its file could not be decoded: the same texture in the other of the two formats the game
 * ships it in, and when that is missing or unreadable too, the neutral value of its kind: grey for a colour map, a
 * flat normal for a normal map, a rough, unoccluded, non-metal, no-team-colour surface for an ORM map.
 */
final class TextureFallback {
	private static final int GREY = 0x80;
	private static final String NORMAL_SUFFIX = "_normal";
	private static final String ORM_SUFFIX = "_orm";

	/** What a texture of the HD materials holds; the three kinds are never exchanged for each other. */
	enum Kind {
		DIFFUSE, NORMAL, ORM
	}

	private TextureFallback() {
	}

	/** The kind a texture file is by its name; any file that is not a normal or ORM map is a colour map. */
	static Kind kindOf(final String path) {
		if (path == null) {
			return Kind.DIFFUSE;
		}
		final int dot = path.lastIndexOf('.');
		final String base = (dot > path.lastIndexOf('/')) && (dot > path.lastIndexOf('\\'))
				? path.substring(0, dot).toLowerCase(Locale.ROOT)
				: path.toLowerCase(Locale.ROOT);
		if (base.endsWith(NORMAL_SUFFIX)) {
			return Kind.NORMAL;
		}
		return base.endsWith(ORM_SUFFIX) ? Kind.ORM : Kind.DIFFUSE;
	}

	/** The path with .dds and .blp swapped, or null for any other extension. */
	static String otherFormat(final String path) {
		if (path == null) {
			return null;
		}
		final int dot = path.length() - 4;
		if (dot < 0) {
			return null;
		}
		final String extension = path.substring(dot);
		final boolean upper = extension.equals(extension.toUpperCase(Locale.ROOT));
		if (extension.equalsIgnoreCase(".dds")) {
			return path.substring(0, dot) + (upper ? ".BLP" : ".blp");
		}
		if (extension.equalsIgnoreCase(".blp")) {
			return path.substring(0, dot) + (upper ? ".DDS" : ".dds");
		}
		return null;
	}

	/** The decoded levels of the other format's file, or null when the data sources have none that decodes. */
	static MipLevels alternate(final DataSource dataSource, final String fetchUrl, final int limit) {
		final String other = otherFormat(fetchUrl);
		if ((other == null) || (dataSource == null) || (kindOf(other) != kindOf(fetchUrl))) {
			return null;
		}
		try {
			if (!dataSource.has(other)) {
				return null;
			}
			final byte[] bytes;
			try (InputStream stream = dataSource.getResourceAsStream(other)) {
				if (stream == null) {
					return null;
				}
				bytes = StreamBytes.readAll(stream);
			}
			final ByteBuffer file = ByteBuffer.wrap(bytes);
			if (DdsDecoder.isDds(file)) {
				return DdsDecoder.decode(file, limit, true).getLevels();
			}
			if (BlpDecoder.isBlp(file)) {
				return BlpDecoder.decode(file, limit, true).getLevels();
			}
		}
		catch (final IOException | RuntimeException e) {
			// the original failure is what gets reported; this one only means there is no usable other file
		}
		return null;
	}

	/** One opaque #808080 pixel, as the RGBA upload takes it. */
	static ByteBuffer grey() {
		return neutral(Kind.DIFFUSE);
	}

	/**
	 * One pixel that leaves a material as it would be without the map, as the RGBA upload takes it. The ORM map's alpha
	 * is the team colour amount, which a stand-in must leave at none: the surface would be tinted by the team colour,
	 * black for a model that has none.
	 */
	static ByteBuffer neutral(final Kind kind) {
		final ByteBuffer pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
		switch (kind) {
		case NORMAL:
			pixel.put((byte) 0x80).put((byte) 0x80).put((byte) 0xFF).put((byte) 0xFF);
			break;
		case ORM:
			pixel.put((byte) 0xFF).put((byte) 0xCC).put((byte) 0x00).put((byte) 0x00);
			break;
		default:
			pixel.put((byte) GREY).put((byte) GREY).put((byte) GREY).put((byte) 0xFF);
			break;
		}
		pixel.flip();
		return pixel;
	}
}
