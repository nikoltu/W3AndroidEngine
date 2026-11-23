package com.etheller.warsmash.viewer5.handlers.blp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decodes a BLP1 or BLP2 image (JPEG, paletted, DXT1/3/5 or plain BGRA content) into RGBA bytes, without depending
 * on java.awt or javax.imageio: the largest stored mip level within a size limit, and the smaller stored levels below
 * it when asked.
 */
public final class BlpDecoder {
	private static final int MAGIC_BLP1 = 0x31504C42; // "BLP1"
	private static final int MAGIC_BLP2 = 0x32504C42; // "BLP2"
	private static final int CONTENT_JPEG = 0;
	private static final int CONTENT_DIRECT = 1;
	private static final int BLP1_HEADER_SIZE = 156;
	private static final int BLP2_HEADER_SIZE = 148;
	private static final int PALETTE_SIZE = 256 * 4;
	private static final int BLP2_PALETTE = 1;
	private static final int BLP2_DXT = 2;
	private static final int BLP2_BGRA = 3;
	private static final int BLP2_ALPHA_DXT1 = 0;
	private static final int BLP2_ALPHA_DXT3 = 1;
	private static final int BLP2_ALPHA_DXT5 = 7;

	private static final int MAX_LEVELS = 16;

	private final MipLevels levels;

	private BlpDecoder(final MipLevels levels) {
		this.levels = levels;
	}

	public int getWidth() {
		return this.levels.getWidth(0);
	}

	public int getHeight() {
		return this.levels.getHeight(0);
	}

	/**
	 * @return direct buffer of width * height RGBA pixels, rows from the top
	 */
	public ByteBuffer getRgba() {
		return this.levels.getRgba(0);
	}

	/** The decoded levels, the largest first. */
	public MipLevels getLevels() {
		return this.levels;
	}

	/**
	 * @return whether decode reads this file: a BLP1, or a BLP2 with JPEG,
	 *         paletted, DXT1/3/5 or BGRA content
	 */
	public static boolean isBlp(final ByteBuffer file) {
		final ByteBuffer data = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		final int magic = data.limit() >= 4 ? data.getInt(0) : 0;
		if (magic == MAGIC_BLP1) {
			return data.limit() >= BLP1_HEADER_SIZE;
		}
		if ((magic != MAGIC_BLP2) || (data.limit() < BLP2_HEADER_SIZE)) {
			return false;
		}
		final int content = data.getInt(4);
		if (content == CONTENT_JPEG) {
			return true;
		}
		if (content != CONTENT_DIRECT) {
			return false;
		}
		switch (data.get(8)) {
		case BLP2_PALETTE:
		case BLP2_BGRA:
			return true;
		case BLP2_DXT:
			return dxtFormat(data.get(10)) != 0;
		default:
			return false;
		}
	}

	/** The top level only, at its full size. */
	public static BlpDecoder decode(final ByteBuffer file) throws IOException {
		return decode(file, 0, false);
	}

	/**
	 * @param maxSize the largest width and height to decode, 0 for no limit: the first stored level within it becomes
	 *                the top level; a file without such a level keeps its smallest one
	 * @param mips    whether the smaller stored levels are decoded too
	 */
	public static BlpDecoder decode(final ByteBuffer file, final int maxSize, final boolean mips) throws IOException {
		if (!isBlp(file)) {
			throw new IOException("Not a supported BLP file");
		}
		final ByteBuffer data = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		final boolean blp1 = data.getInt(0) == MAGIC_BLP1;
		final int headerSize = blp1 ? BLP1_HEADER_SIZE : BLP2_HEADER_SIZE;
		final int width = data.getInt(12);
		final int height = data.getInt(16);
		if ((width <= 0) || (height <= 0) || (((long) width * height) > (1 << 24))) {
			throw new IOException("Bad BLP dimensions " + width + "x" + height);
		}
		final boolean hasMips = blp1 ? data.getInt(24) != 0 : data.get(11) != 0;
		// Only levels the file holds count: a chain may stop before one pixel, and a level cut short by the end of
		// the file is used only as the top level.
		int storedLevels = 1;
		if (hasMips) {
			final int fullChain = Math.min(MAX_LEVELS, MipLevels.levelCount(width, height));
			while ((storedLevels < fullChain) && (mipOffset(data, headerSize, storedLevels) > 0)
					&& (mipSize(data, headerSize, storedLevels) > 0) && ((mipOffset(data, headerSize, storedLevels)
							+ (long) mipSize(data, headerSize, storedLevels)) <= data.limit())) {
				storedLevels++;
			}
		}
		final int first = MipLevels.firstWithin(width, height, maxSize, storedLevels);
		final MipLevels levels = new MipLevels();
		for (int level = first; level < (mips ? storedLevels : first + 1); level++) {
			final int levelWidth = MipLevels.size(width, level);
			final int levelHeight = MipLevels.size(height, level);
			levels.add(levelWidth, levelHeight, decodeLevel(data, blp1, headerSize, levelWidth, levelHeight,
					mipOffset(data, headerSize, level), mipSize(data, headerSize, level)));
		}
		return new BlpDecoder(levels);
	}

	private static int mipOffset(final ByteBuffer data, final int headerSize, final int level) {
		return data.getInt((headerSize - 128) + (level * 4));
	}

	private static int mipSize(final ByteBuffer data, final int headerSize, final int level) {
		return data.getInt((headerSize - 64) + (level * 4));
	}

	private static byte[] decodeLevel(final ByteBuffer data, final boolean blp1, final int headerSize,
			final int width, final int height, final int mipOffset, final int mipSize) throws IOException {
		if (data.getInt(4) == CONTENT_JPEG) {
			return decodeJpeg(data, headerSize, width, height, mipOffset, mipSize);
		}
		if (blp1) {
			return decodePaletted(data, headerSize, width, height, mipOffset, data.getInt(8));
		}
		final int alphaDepth = data.get(9) & 0xFF;
		switch (data.get(8)) {
		case BLP2_PALETTE:
			return decodePaletted(data, headerSize, width, height, mipOffset, alphaDepth);
		case BLP2_DXT: {
			final int dxt = dxtFormat(data.get(10));
			// a size field that claims more than the level can hold must not size the copy
			final int blockBytes = dxt == DdsDecoder.FOURCC_DXT1 ? 8 : 16;
			final long levelBytes = (long) ((width + 3) / 4) * ((height + 3) / 4) * blockBytes;
			final byte[] pixels = DdsDecoder.decodeBlocks(bytes(data, mipOffset, (int) Math.min(mipSize, levelBytes)),
					width, height, dxt);
			if (alphaDepth == 0) {
				// Without alpha bits the game uploads DXT1 as opaque RGB, where the
				// transparent black of a three color block is plain black.
				for (int i = 3; i < pixels.length; i += 4) {
					pixels[i] = (byte) 0xFF;
				}
			}
			return pixels;
		}
		default: {
			final byte[] pixels = bytes(data, mipOffset, width * height * 4);
			for (int i = 0; i < pixels.length; i += 4) {
				final byte blue = pixels[i];
				pixels[i] = pixels[i + 2];
				pixels[i + 2] = blue;
				if (alphaDepth == 0) {
					pixels[i + 3] = (byte) 0xFF;
				}
			}
			return pixels;
		}
		}
	}

	/**
	 * A mip level of a JPEG BLP is its shared JPEG header followed by the level's
	 * data; the four JPEG components hold blue, green, red and alpha. The alpha
	 * component is used even when the header's alpha bits are 0, as the game does:
	 * many retail images (the campaign loading screen emblems and markers) say 0
	 * there and still carry their transparency in it.
	 */
	private static byte[] decodeJpeg(final ByteBuffer data, final int headerSize, final int width,
			final int height, final int mipOffset, final int mipSize) throws IOException {
		final int jpegHeaderSize = data.getInt(headerSize);
		if ((jpegHeaderSize < 0) || (mipSize < 0) || (mipOffset < 0)
				|| (((long) headerSize + 4 + jpegHeaderSize) > data.limit())
				|| (((long) mipOffset + mipSize) > data.limit())) {
			throw new IOException("Truncated JPEG BLP file");
		}
		final byte[] jpeg = new byte[jpegHeaderSize + mipSize];
		data.position(headerSize + 4);
		data.get(jpeg, 0, jpegHeaderSize);
		data.position(mipOffset);
		data.get(jpeg, jpegHeaderSize, mipSize);
		final JpegDecoder image = JpegDecoder.decode(jpeg);
		final byte[] components = image.getComponents();
		final int componentCount = image.getComponentCount();
		final byte[] rgba = new byte[width * height * 4];
		int target = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if ((x < image.getWidth()) && (y < image.getHeight())) {
					final int source = ((y * image.getWidth()) + x) * componentCount;
					rgba[target] = components[source + Math.min(2, componentCount - 1)];
					rgba[target + 1] = components[source + Math.min(1, componentCount - 1)];
					rgba[target + 2] = components[source];
					rgba[target + 3] = componentCount > 3 ? components[source + 3] : (byte) 0xFF;
				}
				target += 4;
			}
		}
		return rgba;
	}

	private static byte[] decodePaletted(final ByteBuffer data, final int paletteOffset, final int width,
			final int height, final int mipOffset, final int alphaBits) throws IOException {
		if ((paletteOffset + PALETTE_SIZE) > data.limit()) {
			throw new IOException("Truncated BLP palette");
		}
		final int pixelCount = width * height;
		// only these alpha depths exist; anything else reads as opaque instead of sizing the copy by a bad field
		final int alphaDepth = (alphaBits == 1) || (alphaBits == 4) || (alphaBits == 8) ? alphaBits : 0;
		final byte[] indices = bytes(data, mipOffset, pixelCount + (((pixelCount * alphaDepth) + 7) / 8));
		final byte[] pixels = new byte[pixelCount * 4];
		for (int i = 0; i < pixelCount; i++) {
			final int paletteEntry = paletteOffset + ((indices[i] & 0xFF) * 4);
			pixels[i * 4] = data.get(paletteEntry + 2);
			pixels[(i * 4) + 1] = data.get(paletteEntry + 1);
			pixels[(i * 4) + 2] = data.get(paletteEntry);
			pixels[(i * 4) + 3] = (byte) alpha(indices, pixelCount, alphaDepth, i);
		}
		return pixels;
	}

	private static int alpha(final byte[] data, final int alphaOffset, final int alphaBits, final int pixel) {
		switch (alphaBits) {
		case 8:
			return data[alphaOffset + pixel] & 0xFF;
		case 4: {
			final int packed = data[alphaOffset + (pixel / 2)] & 0xFF;
			final int nibble = (pixel % 2) == 0 ? packed & 0xF : packed >>> 4;
			return (nibble << 4) | nibble;
		}
		case 1:
			return ((data[alphaOffset + (pixel / 8)] >>> (pixel % 8)) & 1) == 0 ? 0 : 0xFF;
		default:
			return 0xFF;
		}
	}

	private static int dxtFormat(final int alphaType) {
		switch (alphaType) {
		case BLP2_ALPHA_DXT1:
			return DdsDecoder.FOURCC_DXT1;
		case BLP2_ALPHA_DXT3:
			return DdsDecoder.FOURCC_DXT3;
		case BLP2_ALPHA_DXT5:
			return DdsDecoder.FOURCC_DXT5;
		default:
			return 0;
		}
	}

	/**
	 * A mip level cut short by the end of the file reads as zeros there, as a
	 * truncated chunk does in the ImageIO plugin.
	 */
	private static byte[] bytes(final ByteBuffer data, final int offset, final int length) {
		final byte[] out = new byte[Math.max(0, length)];
		if ((offset >= 0) && (offset < data.limit())) {
			data.position(offset);
			data.get(out, 0, Math.min(out.length, data.limit() - offset));
		}
		return out;
	}

}
