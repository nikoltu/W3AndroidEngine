package com.etheller.warsmash.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.viewer5.handlers.blp.BlpDecoder;
import com.etheller.warsmash.viewer5.handlers.blp.DdsDecoder;
import com.etheller.warsmash.viewer5.handlers.tga.TgaFile;

/** The game's own pictures as straight ARGB pixels, which each platform turns into its own images. */
public final class LauncherImages {
	private LauncherImages() {
	}

	/** The image at a path given without extension: DDS in data from 1.30 on, BLP before; null when absent. */
	public static ArgbImage decode(final DataSource data, final String path) throws IOException {
		for (final String extension : new String[] { ".dds", ".blp" }) {
			if (!data.has(path + extension)) {
				continue;
			}
			// A map archive's read leaves the buffer at its end; the stream reads it whole from the start.
			final ByteBuffer file;
			try (InputStream stream = data.getResourceAsStream(path + extension)) {
				if (stream == null) {
					continue;
				}
				file = ByteBuffer.wrap(bytes(stream));
			}
			if (DdsDecoder.isDds(file)) {
				final DdsDecoder image = DdsDecoder.decode(file);
				return argb(image.getWidth(), image.getHeight(), image.getRgba());
			}
			final BlpDecoder image = BlpDecoder.decode(file);
			return argb(image.getWidth(), image.getHeight(), image.getRgba());
		}
		return null;
	}

	/** The picture of a map in its own archive: the game data has a default preview that would hide the map's. */
	public static ArgbImage minimap(final War3Map map) {
		final DataSource archive = map.getInternalMpqContentsDataSource();
		for (final String name : new String[] { "war3mapPreview", "war3mapMap" }) {
			try {
				// The editor saves the map picture as TGA; Reforged-era maps carry a BLP2 that decode reads.
				if (archive.has(name + ".tga")) {
					try (InputStream stream = archive.getResourceAsStream(name + ".tga")) {
						return opaqueIfBlank(TgaFile.readTGAPixels(name + ".tga", stream));
					}
				}
				final ArgbImage image = decode(archive, name);
				if (image != null) {
					return opaqueIfBlank(image);
				}
			}
			catch (final IOException | RuntimeException e) {
				LauncherLog.w("Unable to read " + name, e);
			}
		}
		return null;
	}

	/** Editor map pictures often carry an all-zero alpha channel that only the picture's colours are meant to override. */
	private static ArgbImage opaqueIfBlank(final ArgbImage image) {
		final int[] pixels = pixels(image);
		for (final int colour : pixels) {
			if ((colour >>> 24) != 0) {
				return image;
			}
		}
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] |= 0xFF000000;
		}
		return new ArgbImage(image.getWidth(), image.getHeight(), pixels);
	}

	/** The pixels of an image row by row, as int ARGB. */
	public static int[] pixels(final ArgbImage image) {
		final int width = image.getWidth();
		final int[] pixels = new int[width * image.getHeight()];
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < width; x++) {
				pixels[(y * width) + x] = image.getRGB(x, y);
			}
		}
		return pixels;
	}

	private static ArgbImage argb(final int width, final int height, final ByteBuffer rgba) {
		final int[] pixels = new int[width * height];
		final ByteBuffer data = rgba.duplicate();
		data.position(0);
		for (int i = 0; i < pixels.length; i++) {
			final int r = data.get() & 0xFF;
			final int g = data.get() & 0xFF;
			final int b = data.get() & 0xFF;
			final int a = data.get() & 0xFF;
			pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
		}
		return new ArgbImage(width, height, pixels);
	}

	/**
	 * An edge file holds eight squares in a row: left, right, top, bottom and the four corners; top and bottom lie
	 * turned on their side. They are laid out as a 3 x 3 image, as BackdropFrame draws them.
	 */
	public static ArgbImage edgeNine(final ArgbImage edge) {
		final int s = Math.min(edge.getWidth() / 8, edge.getHeight());
		final int[] nine = new int[9 * s * s];
		final int width = 3 * s;
		for (int i = 0; i < 8; i++) {
			final int x = SkinArt.EDGE_PLACES[i][0] * s;
			final int y = SkinArt.EDGE_PLACES[i][1] * s;
			final boolean turned = (i == 2) || (i == 3);
			for (int v = 0; v < s; v++) {
				for (int u = 0; u < s; u++) {
					final int colour = edge.getRGB((i * s) + u, v);
					if (turned) {
						// A quarter turn clockwise.
						nine[((y + u) * width) + ((x + s) - 1 - v)] = colour;
					}
					else {
						nine[((y + v) * width) + x + u] = colour;
					}
				}
			}
		}
		return new ArgbImage(width, width, nine);
	}

	/**
	 * The menu button holder without the spike on its left: the frame part of the 512 x 128 image, its left end made
	 * from the mirrored right end so the blue button sits in the middle.
	 */
	public static ArgbImage holder(final ArgbImage border) {
		final int[] cut = SkinArt.holderCut(border.getWidth());
		final int left = cut[0];
		final int top = cut[1];
		final int width = cut[2];
		final int height = cut[3];
		final int end = cut[4];
		final int[] holder = new int[width * height];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				final int from = x < end ? ((left + width) - 1 - x) : left + x;
				holder[(y * width) + x] = border.getRGB(from, top + y);
			}
		}
		return new ArgbImage(width, height, holder);
	}

	/** A loading screen of the game data by its tile prefix, or null when a tile is missing. */
	public static ArgbImage tiles(final DataSource data, final String prefix) throws IOException {
		final ArgbImage topLeft = decode(data, prefix + "-TopLeft");
		final ArgbImage topRight = decode(data, prefix + "-TopRight");
		final ArgbImage bottomLeft = decode(data, prefix + "-BotLeft");
		final ArgbImage bottomRight = decode(data, prefix + "-BotRight");
		if ((topLeft == null) || (topRight == null) || (bottomLeft == null) || (bottomRight == null)) {
			return null;
		}
		return tiles(topLeft, topRight, bottomLeft, bottomRight);
	}

	/** Loading screens are four tiles: two square ones above and two half-height ones below, on black. */
	public static ArgbImage tiles(final ArgbImage topLeft, final ArgbImage topRight, final ArgbImage bottomLeft,
			final ArgbImage bottomRight) {
		final int width = topLeft.getWidth() + topRight.getWidth();
		final int height = topLeft.getHeight() + bottomLeft.getHeight();
		final int[] joined = new int[width * height];
		Arrays.fill(joined, 0xFF000000);
		copy(topLeft, joined, width, 0, 0);
		copy(topRight, joined, width, topLeft.getWidth(), 0);
		copy(bottomLeft, joined, width, 0, topLeft.getHeight());
		copy(bottomRight, joined, width, topLeft.getWidth(), topLeft.getHeight());
		return new ArgbImage(width, height, joined);
	}

	private static void copy(final ArgbImage image, final int[] target, final int targetWidth, final int x,
			final int y) {
		final int height = Math.min(image.getHeight(), (target.length / targetWidth) - y);
		final int width = Math.min(image.getWidth(), targetWidth - x);
		for (int row = 0; row < height; row++) {
			for (int column = 0; column < width; column++) {
				final int colour = image.getRGB(column, row);
				// Drawn over black as a canvas draws it.
				final int alpha = colour >>> 24;
				target[((y + row) * targetWidth) + x + column] = alpha == 0xFF ? colour : over(colour, alpha);
			}
		}
	}

	private static int over(final int colour, final int alpha) {
		final int r = (((colour >> 16) & 0xFF) * alpha) / 255;
		final int g = (((colour >> 8) & 0xFF) * alpha) / 255;
		final int b = ((colour & 0xFF) * alpha) / 255;
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	public static byte[] bytes(final InputStream in) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final byte[] buffer = new byte[65536];
		int read;
		while ((read = in.read(buffer)) > 0) {
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}
}
