package com.etheller.warsmash.viewer5.handlers.blp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decodes the top mip level of a paletted BLP1 image into RGBA bytes, without
 * depending on java.awt or javax.imageio. JPEG content is not handled here.
 */
public final class BlpDecoder {
	private static final int MAGIC_BLP1 = 0x31504C42; // "BLP1"
	private static final int CONTENT_JPEG = 0;
	private static final int HEADER_SIZE = 156;
	private static final int PALETTE_SIZE = 256 * 4;

	private final int width;
	private final int height;
	private final ByteBuffer rgba;

	private BlpDecoder(final int width, final int height, final ByteBuffer rgba) {
		this.width = width;
		this.height = height;
		this.rgba = rgba;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	/**
	 * @return direct buffer of width * height RGBA pixels, rows from the top
	 */
	public ByteBuffer getRgba() {
		return this.rgba;
	}

	public static boolean isPalettedBlp1(final ByteBuffer file) {
		final ByteBuffer data = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		return (data.remaining() >= HEADER_SIZE) && (data.getInt(0) == MAGIC_BLP1)
				&& (data.getInt(4) != CONTENT_JPEG);
	}

	public static boolean isJpegBlp1(final ByteBuffer file) {
		final ByteBuffer data = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		return (data.remaining() >= HEADER_SIZE + 4) && (data.getInt(0) == MAGIC_BLP1)
				&& (data.getInt(4) == CONTENT_JPEG);
	}

	/**
	 * The top mip level of a JPEG BLP1 is its shared JPEG header followed by the mip
	 * data; the four JPEG components hold blue, green, red and alpha.
	 */
	public static BlpDecoder decodeJpeg(final ByteBuffer file) throws IOException {
		if (!isJpegBlp1(file)) {
			throw new IOException("Not a JPEG BLP1 file");
		}
		final ByteBuffer data = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		final int alphaBits = data.getInt(8);
		final int width = data.getInt(12);
		final int height = data.getInt(16);
		final int mipOffset = data.getInt(28);
		final int mipSize = data.getInt(28 + 64);
		final int jpegHeaderSize = data.getInt(HEADER_SIZE);
		if (((HEADER_SIZE + 4 + jpegHeaderSize) > data.limit()) || ((mipOffset + mipSize) > data.limit())) {
			throw new IOException("Truncated JPEG BLP1 file");
		}
		final byte[] jpeg = new byte[jpegHeaderSize + mipSize];
		data.position(HEADER_SIZE + 4);
		data.get(jpeg, 0, jpegHeaderSize);
		data.position(mipOffset);
		data.get(jpeg, jpegHeaderSize, mipSize);
		final JpegDecoder image = JpegDecoder.decode(jpeg);
		final byte[] components = image.getComponents();
		final int componentCount = image.getComponentCount();
		final int pixelCount = width * height;
		final ByteBuffer rgba = ByteBuffer.allocateDirect(pixelCount * 4).order(ByteOrder.nativeOrder());
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if ((x < image.getWidth()) && (y < image.getHeight())) {
					final int source = ((y * image.getWidth()) + x) * componentCount;
					rgba.put(components[source + Math.min(2, componentCount - 1)]);
					rgba.put(components[source + Math.min(1, componentCount - 1)]);
					rgba.put(components[source]);
					rgba.put(((componentCount > 3) && (alphaBits != 0)) ? components[source + 3] : (byte) 0xFF);
				}
				else {
					rgba.putInt(0);
				}
			}
		}
		rgba.position(0);
		return new BlpDecoder(width, height, rgba);
	}

	public static BlpDecoder decode(final ByteBuffer file) throws IOException {
		if (!isPalettedBlp1(file)) {
			throw new IOException("Not a paletted BLP1 file");
		}
		final ByteBuffer data = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		final int alphaBits = data.getInt(8);
		final int width = data.getInt(12);
		final int height = data.getInt(16);
		final int mipOffset = data.getInt(28);
		final int pixelCount = width * height;
		if ((HEADER_SIZE + PALETTE_SIZE > data.limit()) || (mipOffset + pixelCount > data.limit())) {
			throw new IOException("Truncated BLP1 file");
		}

		final ByteBuffer rgba = ByteBuffer.allocateDirect(pixelCount * 4).order(ByteOrder.nativeOrder());
		final int alphaOffset = mipOffset + pixelCount;
		for (int i = 0; i < pixelCount; i++) {
			final int paletteEntry = HEADER_SIZE + ((data.get(mipOffset + i) & 0xFF) * 4);
			rgba.put(data.get(paletteEntry + 2));
			rgba.put(data.get(paletteEntry + 1));
			rgba.put(data.get(paletteEntry));
			rgba.put((byte) alpha(data, alphaOffset, alphaBits, i));
		}
		rgba.position(0);
		return new BlpDecoder(width, height, rgba);
	}

	private static int alpha(final ByteBuffer data, final int alphaOffset, final int alphaBits, final int pixel) {
		switch (alphaBits) {
		case 8:
			return data.get(alphaOffset + pixel) & 0xFF;
		case 4: {
			final int packed = data.get(alphaOffset + (pixel / 2)) & 0xFF;
			final int nibble = (pixel % 2) == 0 ? packed & 0xF : packed >>> 4;
			return (nibble << 4) | nibble;
		}
		case 1:
			return ((data.get(alphaOffset + (pixel / 8)) >>> (pixel % 8)) & 1) == 0 ? 0 : 0xFF;
		default:
			return 0xFF;
		}
	}
}
