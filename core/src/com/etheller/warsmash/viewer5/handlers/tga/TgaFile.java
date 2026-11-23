/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.etheller.warsmash.viewer5.handlers.tga;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.StreamBytes;

/**
 *
 * @author Riven, modified by Oger-Lord
 */
public class TgaFile {
	private static final int MAX_PIXELS = 1 << 26;

	/**
	 * Read a TGA image from a file
	 *
	 * @param file
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static BufferedImage readTGA(final File file) throws FileNotFoundException, IOException {
		return readTGA(file.getName(), new FileInputStream(file));

	}

	/**
	 * Read a TGA image from an input stream.
	 *
	 * @param name
	 * @param stream
	 * @return
	 * @throws IOException
	 */
	public static BufferedImage readTGA(final String name, final InputStream stream) throws IOException {
		final int[] dimensions = new int[3];
		final int[] pixels = decodePixels(name, stream, dimensions);
		final BufferedImage dst = new BufferedImage(dimensions[0], dimensions[1],
				dimensions[2] == 1 ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		final int[] dstPixels = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();
		System.arraycopy(pixels, 0, dstPixels, 0, pixels.length);
		return dst;
	}

	/**
	 * Reads a TGA image as ARGB pixels without java.awt, matching what getRGB of
	 * readTGA's image returns.
	 */
	public static ArgbImage readTGAPixels(final String name, final InputStream stream) throws IOException {
		final int[] dimensions = new int[3];
		final int[] pixels = decodePixels(name, stream, dimensions);
		if (dimensions[2] == 0) {
			for (int i = 0; i < pixels.length; i++) {
				pixels[i] |= 0xFF000000;
			}
		}
		return new ArgbImage(dimensions[0], dimensions[1], pixels);
	}

	/**
	 * @param dimensions receives width, height and 1 if the image has alpha
	 */
	private static int[] decodePixels(final String name, final InputStream stream, final int[] dimensions)
			throws IOException {

		// Read Header
		final byte[] header = new byte[18];
		int headerRead = 0;
		while (headerRead < header.length) {
			final int n = stream.read(header, headerRead, header.length - headerRead);
			if (n < 0) {
				throw new IllegalStateException("Error " + name + " truncated header");
			}
			headerRead += n;
		}

		// Read pixel data
		final byte[] data = StreamBytes.readAll(stream);

		// Verify Header
		final int idLength = header[0] & 0xFF;
		final int colorMapType = header[1] & 0xFF;
		final int imageType = header[2] & 0xFF;
		if (colorMapType > 1) {
			throw new IllegalStateException("Error " + name + " invalid color map type: " + colorMapType);
		}
		final boolean runLengthEncoded = imageType >= 9;
		final int baseType = imageType & 7;
		if (((imageType != 1) && (imageType != 2) && (imageType != 3) && (imageType != 9) && (imageType != 10)
				&& (imageType != 11))) {
			throw new IllegalStateException("Error " + name + " unsupported image type: " + imageType);
		}
		final boolean colorMapped = baseType == 1;
		if (colorMapped && (colorMapType != 1)) {
			throw new IllegalStateException("Error " + name + " color mapped image without a color map");
		}
		int w = 0, h = 0;
		w |= (header[12] & 0xFF) << 0;
		w |= (header[13] & 0xFF) << 8;
		h |= (header[14] & 0xFF) << 0;
		h |= (header[15] & 0xFF) << 8;
		final int depth = header[16] & 0xFF;
		final int descriptor = header[17] & 0xFF;
		final int attributeBits = descriptor & 15;

		// The image ID field and the color map sit between the header and the pixel data
		int offset = idLength;
		int colorMapLength = 0;
		int colorMapEntryBits = 0;
		int colorMapFirst = 0;
		if (colorMapType == 1) {
			colorMapFirst = (header[3] & 0xFF) | ((header[4] & 0xFF) << 8);
			colorMapLength = (header[5] & 0xFF) | ((header[6] & 0xFF) << 8);
			colorMapEntryBits = header[7] & 0xFF;
			offset += colorMapLength * ((colorMapEntryBits + 7) / 8);
		}
		if (offset > data.length) {
			throw new IllegalStateException("Error " + name + " not enaugh pixel data");
		}

		// Bits per stored sample and, after the color map, per resulting pixel
		final int sampleBits = depth;
		final int pixelBits = colorMapped ? colorMapEntryBits : depth;
		if (colorMapped) {
			if ((sampleBits != 8) && (sampleBits != 16)) {
				throw new IllegalStateException("Error " + name + " invalid pixel depth: " + depth);
			}
			if (!isColorBits(pixelBits)) {
				throw new IllegalStateException("Error " + name + " invalid color map depth: " + pixelBits);
			}
		}
		else if (baseType == 3) {
			if ((sampleBits != 8) && (sampleBits != 16)) {
				throw new IllegalStateException("Error " + name + " invalid pixel depth: " + depth);
			}
		}
		else if (!isColorBits(sampleBits)) {
			throw new IllegalStateException("Error " + name + " invalid pixel depth: " + depth);
		}
		final boolean alpha;
		if (colorMapped) {
			alpha = ((pixelBits == 32) || (pixelBits == 16) || (pixelBits == 15)) && (attributeBits != 0);
		}
		else if (baseType == 3) {
			alpha = sampleBits == 16;
		}
		else {
			alpha = ((sampleBits == 32) || (sampleBits == 16)) && (attributeBits != 0);
		}

		// a damaged header must not size the arrays below: 65535 x 65535 overflows an int
		if ((w == 0) || (h == 0) || (((long) w * h) > MAX_PIXELS)) {
			throw new IllegalStateException("Error " + name + " invalid size: " + w + "x" + h);
		}
		final int sampleBytes = (sampleBits + 7) / 8;
		final int pixelCount = w * h;
		final byte[] raw;
		if (runLengthEncoded) {
			raw = decodeRunLength(name, data, offset, pixelCount, sampleBytes);
			offset = 0;
		}
		else {
			if ((data.length - offset) < ((long) pixelCount * sampleBytes)) {
				throw new IllegalStateException("Error " + name + " not enaugh pixel data");
			}
			raw = data;
		}

		int[] palette = null;
		if (colorMapped) {
			palette = new int[colorMapFirst + colorMapLength];
			final int entryBytes = (colorMapEntryBits + 7) / 8;
			final int mapStart = idLength;
			for (int i = 0; i < colorMapLength; i++) {
				palette[colorMapFirst + i] = toArgb(data, mapStart + (i * entryBytes), colorMapEntryBits, false, alpha);
			}
		}

		// Samples come in file order; the origin bits say where row 0 and column 0 sit
		final boolean topOrigin = (descriptor & 0x20) != 0;
		final boolean rightOrigin = (descriptor & 0x10) != 0;
		final int[] pixels = new int[pixelCount];
		for (int i = 0; i < pixelCount; i++) {
			final int p = offset + (i * sampleBytes);
			final int argb;
			if (colorMapped) {
				final int index = sampleBytes == 1 ? (raw[p] & 0xFF) : ((raw[p] & 0xFF) | ((raw[p + 1] & 0xFF) << 8));
				if (index >= palette.length) {
					throw new IllegalStateException("Error " + name + " color map index out of range: " + index);
				}
				argb = palette[index];
			}
			else {
				argb = toArgb(raw, p, sampleBits, baseType == 3, alpha);
			}
			final int row = i / w;
			final int column = i - (row * w);
			final int destRow = topOrigin ? row : (h - 1 - row);
			final int destColumn = rightOrigin ? (w - 1 - column) : column;
			pixels[(destRow * w) + destColumn] = argb;
		}

		dimensions[0] = w;
		dimensions[1] = h;
		dimensions[2] = alpha ? 1 : 0;
		return pixels;
	}

	private static boolean isColorBits(final int bits) {
		return (bits == 15) || (bits == 16) || (bits == 24) || (bits == 32);
	}

	private static int expand5(final int value) {
		return (value << 3) | (value >> 2);
	}

	/**
	 * Converts one stored sample (or color map entry) to ARGB; the alpha byte is left 0 for images without alpha.
	 */
	private static int toArgb(final byte[] data, final int p, final int bits, final boolean grey,
			final boolean alpha) {
		if (grey) {
			final int g = data[p] & 0xFF;
			final int a = (bits == 16) && alpha ? (data[p + 1] & 0xFF) << 24 : 0;
			return a | (g << 16) | (g << 8) | g;
		}
		if (bits == 24) {
			return ((data[p + 0]) & 0xFF) | (((data[p + 1]) & 0xFF) << 8) | (((data[p + 2]) & 0xFF) << 16);
		}
		if (bits == 32) {
			return ((data[p + 0]) & 0xFF) | (((data[p + 1]) & 0xFF) << 8) | (((data[p + 2]) & 0xFF) << 16)
					| (alpha ? ((data[p + 3]) & 0xFF) << 24 : 0);
		}
		final int v = (data[p] & 0xFF) | ((data[p + 1] & 0xFF) << 8);
		final int b = expand5(v & 0x1F);
		final int g = expand5((v >> 5) & 0x1F);
		final int r = expand5((v >> 10) & 0x1F);
		return (alpha && ((v & 0x8000) != 0) ? 0xFF000000 : 0) | (r << 16) | (g << 8) | b;
	}

	/**
	 * Expands TGA run-length packets into the same byte layout an uncompressed image has.
	 */
	private static byte[] decodeRunLength(final String name, final byte[] packed, final int start,
			final int pixelCount, final int bytesPerPixel) {
		// a packet of at most 128 pixels takes at least bytesPerPixel + 1 bytes, so a short file cannot fill a big image
		if (((long) pixelCount * bytesPerPixel) > ((long) Math.max(0, packed.length - start) * 128 * bytesPerPixel
				/ (bytesPerPixel + 1))) {
			throw new IllegalStateException("Error " + name + " not enaugh pixel data");
		}
		final byte[] raw = new byte[pixelCount * bytesPerPixel];
		int in = start;
		int out = 0;
		while (out < raw.length) {
			if (in >= packed.length) {
				throw new IllegalStateException("Error " + name + " not enaugh pixel data");
			}
			final int packetHeader = packed[in++] & 0xFF;
			final int count = (packetHeader & 0x7F) + 1;
			final int length = count * bytesPerPixel;
			if ((out + length) > raw.length) {
				throw new IllegalStateException("Error " + name + " run past the image end");
			}
			if ((packetHeader & 0x80) != 0) {
				if ((in + bytesPerPixel) > packed.length) {
					throw new IllegalStateException("Error " + name + " not enaugh pixel data");
				}
				for (int i = 0; i < count; i++) {
					System.arraycopy(packed, in, raw, out, bytesPerPixel);
					out += bytesPerPixel;
				}
				in += bytesPerPixel;
			}
			else {
				if ((in + length) > packed.length) {
					throw new IllegalStateException("Error " + name + " not enaugh pixel data");
				}
				System.arraycopy(packed, in, raw, out, length);
				in += length;
				out += length;
			}
		}
		return raw;
	}
}
