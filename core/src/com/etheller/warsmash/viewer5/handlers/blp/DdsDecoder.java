package com.etheller.warsmash.viewer5.handlers.blp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decodes the top mip level of a block compressed DDS image (DXT1, DXT3, DXT5,
 * ATI2) into RGBA bytes, without depending on java.awt or javax.imageio.
 */
public final class DdsDecoder {
	private static final int MAGIC = 0x20534444; // "DDS "
	private static final int HEADER_SIZE = 128;
	private static final int DDPF_FOURCC = 0x4;
	private static final int FOURCC_DXT1 = fourCC("DXT1");
	private static final int FOURCC_DXT3 = fourCC("DXT3");
	private static final int FOURCC_DXT5 = fourCC("DXT5");
	private static final int FOURCC_ATI2 = fourCC("ATI2");

	private final int width;
	private final int height;
	private final ByteBuffer rgba;

	private DdsDecoder(final int width, final int height, final ByteBuffer rgba) {
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

	public static boolean isDds(final ByteBuffer file) {
		final ByteBuffer data = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		return (data.remaining() >= HEADER_SIZE) && (data.getInt(0) == MAGIC);
	}

	public static DdsDecoder decode(final ByteBuffer file) throws IOException {
		final ByteBuffer data = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		if ((data.remaining() < HEADER_SIZE) || (data.getInt(0) != MAGIC)) {
			throw new IOException("Not a DDS file");
		}
		final int height = data.getInt(12);
		final int width = data.getInt(16);
		final int pixelFormatFlags = data.getInt(80);
		final int fourCC = data.getInt(84);
		if ((pixelFormatFlags & DDPF_FOURCC) == 0) {
			throw new IOException("Unsupported uncompressed DDS");
		}

		final ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
		final byte[] block = new byte[16 * 4];
		final int blocksWide = Math.max(1, (width + 3) / 4);
		final int blocksHigh = Math.max(1, (height + 3) / 4);
		data.position(HEADER_SIZE);
		for (int blockY = 0; blockY < blocksHigh; blockY++) {
			for (int blockX = 0; blockX < blocksWide; blockX++) {
				if (fourCC == FOURCC_DXT1) {
					decodeColorBlock(data, block, true);
				}
				else if (fourCC == FOURCC_DXT3) {
					final long alphaBits = data.getLong();
					decodeColorBlock(data, block, false);
					for (int i = 0; i < 16; i++) {
						final int alpha = (int) ((alphaBits >>> (4 * i)) & 0xF);
						block[(i * 4) + 3] = (byte) ((alpha << 4) | alpha);
					}
				}
				else if (fourCC == FOURCC_DXT5) {
					final byte[] alphas = new byte[16];
					decodeInterpolatedBlock(data, alphas);
					decodeColorBlock(data, block, false);
					for (int i = 0; i < 16; i++) {
						block[(i * 4) + 3] = alphas[i];
					}
				}
				else if (fourCC == FOURCC_ATI2) {
					// Unlike BC5, the legacy ATI2 code stores the green block first.
					final byte[] red = new byte[16];
					final byte[] green = new byte[16];
					decodeInterpolatedBlock(data, green);
					decodeInterpolatedBlock(data, red);
					for (int i = 0; i < 16; i++) {
						block[i * 4] = red[i];
						block[(i * 4) + 1] = green[i];
						block[(i * 4) + 2] = 0;
						block[(i * 4) + 3] = (byte) 0xFF;
					}
				}
				else {
					throw new IOException("Unsupported DDS format: " + Integer.toHexString(fourCC));
				}
				writeBlock(rgba, block, blockX * 4, blockY * 4, width, height);
			}
		}
		rgba.position(0);
		return new DdsDecoder(width, height, rgba);
	}

	private static void decodeColorBlock(final ByteBuffer data, final byte[] out, final boolean dxt1) {
		final int color0 = data.getShort() & 0xFFFF;
		final int color1 = data.getShort() & 0xFFFF;
		final int indices = data.getInt();
		final int[] palette = new int[16];
		expand565(color0, palette, 0);
		expand565(color1, palette, 4);
		final boolean fourColors = !dxt1 || (color0 > color1);
		for (int channel = 0; channel < 3; channel++) {
			final int c0 = palette[channel];
			final int c1 = palette[4 + channel];
			if (fourColors) {
				palette[8 + channel] = ((2 * c0) + c1) / 3;
				palette[12 + channel] = (c0 + (2 * c1)) / 3;
			}
			else {
				palette[8 + channel] = (c0 + c1) / 2;
				palette[12 + channel] = 0;
			}
		}
		palette[3] = 255;
		palette[7] = 255;
		palette[11] = 255;
		palette[15] = fourColors ? 255 : 0;
		for (int i = 0; i < 16; i++) {
			final int index = (indices >>> (2 * i)) & 0x3;
			out[i * 4] = (byte) palette[index * 4];
			out[(i * 4) + 1] = (byte) palette[(index * 4) + 1];
			out[(i * 4) + 2] = (byte) palette[(index * 4) + 2];
			out[(i * 4) + 3] = (byte) palette[(index * 4) + 3];
		}
	}

	private static void decodeInterpolatedBlock(final ByteBuffer data, final byte[] out) {
		final int value0 = data.get() & 0xFF;
		final int value1 = data.get() & 0xFF;
		long indices = 0;
		for (int i = 0; i < 6; i++) {
			indices |= (long) (data.get() & 0xFF) << (8 * i);
		}
		final int[] values = new int[8];
		values[0] = value0;
		values[1] = value1;
		if (value0 > value1) {
			for (int i = 1; i < 7; i++) {
				values[i + 1] = (((7 - i) * value0) + (i * value1)) / 7;
			}
		}
		else {
			for (int i = 1; i < 5; i++) {
				values[i + 1] = (((5 - i) * value0) + (i * value1)) / 5;
			}
			values[6] = 0;
			values[7] = 255;
		}
		for (int i = 0; i < 16; i++) {
			out[i] = (byte) values[(int) ((indices >>> (3 * i)) & 0x7)];
		}
	}

	private static void expand565(final int color, final int[] out, final int offset) {
		final int red = (color >>> 11) & 0x1F;
		final int green = (color >>> 5) & 0x3F;
		final int blue = color & 0x1F;
		out[offset] = (red << 3) | (red >>> 2);
		out[offset + 1] = (green << 2) | (green >>> 4);
		out[offset + 2] = (blue << 3) | (blue >>> 2);
	}

	private static void writeBlock(final ByteBuffer rgba, final byte[] block, final int x0, final int y0,
			final int width, final int height) {
		for (int y = 0; y < 4; y++) {
			final int pixelY = y0 + y;
			if (pixelY >= height) {
				break;
			}
			for (int x = 0; x < 4; x++) {
				final int pixelX = x0 + x;
				if (pixelX >= width) {
					break;
				}
				final int target = ((pixelY * width) + pixelX) * 4;
				final int source = ((y * 4) + x) * 4;
				rgba.put(target, block[source]);
				rgba.put(target + 1, block[source + 1]);
				rgba.put(target + 2, block[source + 2]);
				rgba.put(target + 3, block[source + 3]);
			}
		}
	}

	private static int fourCC(final String code) {
		return code.charAt(0) | (code.charAt(1) << 8) | (code.charAt(2) << 16) | (code.charAt(3) << 24);
	}
}
