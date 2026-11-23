package com.etheller.warsmash.viewer5.handlers.blp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.DataErrors;
import com.etheller.warsmash.viewer5.RawOpenGLTextureResourceAccess;
import com.etheller.warsmash.viewer5.handlers.tga.TgaFile;

/**
 * Custom maps carry textures that no tool of the game made: truncated, cut at the header, or with sizes that claim
 * more than the file holds. Each must end as an exception the loader reports, never as an error that takes the loading
 * thread down, an enormous allocation, or an endless loop.
 */
class TextureDecoderHardeningTest {

	private static byte[] tga(final int type, final int w, final int h, final int depth, final byte[] body) {
		final ByteBuffer b = ByteBuffer.allocate(18 + body.length).order(ByteOrder.LITTLE_ENDIAN);
		b.put(2, (byte) type);
		b.putShort(12, (short) w);
		b.putShort(14, (short) h);
		b.put(16, (byte) depth);
		b.position(18);
		b.put(body);
		return b.array();
	}

	private static ArgbImage readTga(final byte[] file) throws IOException {
		return TgaFile.readTGAPixels("test.tga", new ByteArrayInputStream(file));
	}

	@Test
	void validTgaDecodes() throws IOException {
		final ArgbImage image = readTga(tga(2, 2, 1, 24, new byte[] { 1, 2, 3, 4, 5, 6 }));
		assertEquals(2, image.getWidth());
		assertEquals(1, image.getHeight());
	}

	@Test
	void truncatedTgaIsRejected() {
		assertThrows(RuntimeException.class, () -> readTga(new byte[10]));
		assertThrows(RuntimeException.class, () -> readTga(tga(2, 4, 4, 32, new byte[8])));
		assertThrows(RuntimeException.class, () -> readTga(tga(10, 4, 4, 32, new byte[3])));
	}

	@Test
	void tgaWithNoPixelsIsRejected() {
		assertThrows(IllegalStateException.class, () -> readTga(tga(2, 0, 4, 24, new byte[0])));
		assertThrows(IllegalStateException.class, () -> readTga(tga(2, 4, 0, 24, new byte[0])));
	}

	@Test
	void hugeTgaSizeWithLittleDataDoesNotAllocateTheImage() {
		// 65535 x 65535 overflows an int pixel count; run length data sized its buffer before looking at the file
		assertThrows(IllegalStateException.class,
				() -> readTga(tga(10, 65535, 65535, 32, new byte[] { (byte) 0xFF, 1, 2, 3, 4 })));
		assertThrows(IllegalStateException.class,
				() -> readTga(tga(10, 30000, 30000, 24, new byte[] { (byte) 0xFF, 1, 2, 3 })));
		assertThrows(IllegalStateException.class, () -> readTga(tga(2, 30000, 30000, 24, new byte[16])));
	}

	@Test
	void tgaColorMapIndexOutsideTheMapIsRejected() {
		final byte[] file = tga(1, 1, 1, 8, new byte[] { 0, 0, 0, 9 });
		file[1] = 1;
		file[5] = 1;
		file[7] = 24;
		assertThrows(IllegalStateException.class, () -> readTga(file));
	}

	@Test
	void tgaDecoderSurvivesDamagedBytes() {
		final byte[] good = tga(10, 8, 8, 32, rle(8 * 8, 4));
		final Random random = new Random(7);
		for (int i = 0; i < 400; i++) {
			final byte[] file = good.clone();
			for (int k = 0; k < 3; k++) {
				file[random.nextInt(file.length)] = (byte) random.nextInt(256);
			}
			final byte[] damaged = i % 5 == 0 ? Arrays.copyOf(file, random.nextInt(file.length)) : file;
			try {
				readTga(damaged);
			}
			catch (final IOException | RuntimeException e) {
				// reported by the loader
			}
		}
	}

	private static byte[] rle(final int pixels, final int bytesPerPixel) {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		int left = pixels;
		while (left > 0) {
			final int count = Math.min(128, left);
			out.write(0x80 | (count - 1));
			for (int i = 0; i < bytesPerPixel; i++) {
				out.write(i + 1);
			}
			left -= count;
		}
		return out.toByteArray();
	}

	/** A BLP1 with a palette of one color, no mip levels and the given alpha field; the pixels follow the palette. */
	private static byte[] blp1Paletted(final int w, final int h, final int alphaBits, final int pixelBytes) {
		final ByteBuffer b = ByteBuffer.allocate(156 + 1024 + pixelBytes).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(0, 0x31504C42);
		b.putInt(4, 1);
		b.putInt(8, alphaBits);
		b.putInt(12, w);
		b.putInt(16, h);
		b.putInt(24, 0);
		b.putInt(28, 156 + 1024);
		b.putInt(92, pixelBytes);
		return b.array();
	}

	private static byte[] blp2(final int content, final int encoding, final int alphaDepth, final int alphaType,
			final int w, final int h, final int bodyBytes, final int claimedSize) {
		final ByteBuffer b = ByteBuffer.allocate(148 + bodyBytes).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(0, 0x32504C42);
		b.putInt(4, content);
		b.put(8, (byte) encoding);
		b.put(9, (byte) alphaDepth);
		b.put(10, (byte) alphaType);
		b.put(11, (byte) 0);
		b.putInt(12, w);
		b.putInt(16, h);
		b.putInt(20, 148);
		b.putInt(84, claimedSize);
		return b.array();
	}

	@Test
	void validPalettedBlpDecodes() throws IOException {
		final BlpDecoder decoder = BlpDecoder.decode(ByteBuffer.wrap(blp1Paletted(4, 4, 0, 16)));
		assertEquals(4, decoder.getWidth());
	}

	@Test
	void blpCutAtTheHeaderIsRejected() {
		final byte[] file = blp1Paletted(4, 4, 0, 16);
		assertThrows(IOException.class, () -> BlpDecoder.decode(ByteBuffer.wrap(file, 0, 100).slice()));
		// header present, palette cut off
		assertThrows(IOException.class, () -> BlpDecoder.decode(ByteBuffer.wrap(file, 0, 200).slice()));
		assertThrows(IOException.class, () -> BlpDecoder.decode(ByteBuffer.wrap(new byte[0])));
	}

	@Test
	void blpWithAnImpossibleAlphaFieldReadsAsOpaque() throws IOException {
		final BlpDecoder decoder = BlpDecoder.decode(ByteBuffer.wrap(blp1Paletted(4, 4, Integer.MAX_VALUE, 16)));
		assertEquals(4, decoder.getHeight());
		assertEquals((byte) 0xFF, decoder.getRgba().get(3));
	}

	@Test
	void blpWithAHugeBlockSizeDoesNotSizeTheCopy() throws IOException {
		// the level claims 2 GB of blocks; the file holds none, which decodes as zeros as a cut level does
		final byte[] file = blp2(1, 2, 8, 7, 64, 64, 0, Integer.MAX_VALUE);
		assertEquals(64, BlpDecoder.decode(ByteBuffer.wrap(file)).getWidth());
	}

	@Test
	void jpegBlpWithOffsetsOutsideTheFileIsRejected() {
		final byte[] file = blp2(0, 0, 0, 0, 4, 4, 16, Integer.MAX_VALUE);
		ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN).putInt(20, Integer.MAX_VALUE - 4);
		assertThrows(IOException.class, () -> BlpDecoder.decode(ByteBuffer.wrap(file)));
		final byte[] negative = blp2(0, 0, 0, 0, 4, 4, 16, 8);
		ByteBuffer.wrap(negative).order(ByteOrder.LITTLE_ENDIAN).putInt(20, -8);
		assertThrows(IOException.class, () -> BlpDecoder.decode(ByteBuffer.wrap(negative)));
	}

	@Test
	void blpWithBadDimensionsIsRejected() {
		assertThrows(IOException.class, () -> BlpDecoder.decode(ByteBuffer.wrap(blp1Paletted(0, 4, 0, 16))));
		assertThrows(IOException.class, () -> BlpDecoder.decode(ByteBuffer.wrap(blp1Paletted(65535, 65535, 0, 16))));
	}

	@Test
	void jpegWithAHugeFrameIsRejectedBeforeAnyBufferIsMade() {
		final byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC0, 0, 11, 8, (byte) 0xFF, (byte) 0xFF,
				(byte) 0xFF, (byte) 0xFF, 1, 1, 0x11, 0 };
		assertThrows(IOException.class, () -> JpegDecoder.decode(jpeg));
		assertThrows(IOException.class, () -> JpegDecoder.decode(new byte[] { (byte) 0xFF }));
	}

	@Test
	void ddsDefectsAreRejected() {
		assertThrows(IOException.class, () -> DdsDecoder.decode(ByteBuffer.wrap(new byte[60])));
		final byte[] file = dds("DXT1", 0, 4);
		assertThrows(IOException.class, () -> DdsDecoder.decode(ByteBuffer.wrap(file)));
		final byte[] unknown = dds("ABCD", 4, 4);
		assertThrows(IOException.class, () -> DdsDecoder.decode(ByteBuffer.wrap(unknown)));
		final byte[] huge = dds("DXT5", 65535, 65535);
		assertThrows(IOException.class, () -> DdsDecoder.decode(ByteBuffer.wrap(huge)));
	}

	@Test
	void ddsCutShortStillDecodes() throws IOException {
		final byte[] file = dds("DXT1", 64, 64);
		assertEquals(64, DdsDecoder.decode(ByteBuffer.wrap(file)).getWidth());
	}

	private static byte[] dds(final String fourCC, final int w, final int h) {
		final ByteBuffer b = ByteBuffer.allocate(128 + 64).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(0, 0x20534444);
		b.putInt(12, h);
		b.putInt(16, w);
		b.putInt(80, 4);
		for (int i = 0; i < 4; i++) {
			b.put(84 + i, (byte) fourCC.charAt(i));
		}
		return b.array();
	}

	@Test
	void damagedBlpAndDdsEndAsExceptionsOnly() {
		final byte[][] seeds = { blp1Paletted(8, 8, 8, 128), blp2(1, 2, 8, 7, 16, 16, 256, 256),
				blp2(1, 3, 8, 0, 8, 8, 256, 256), dds("DXT5", 16, 16), dds("ATI2", 8, 8) };
		final Random random = new Random(11);
		for (final byte[] seed : seeds) {
			for (int i = 0; i < 500; i++) {
				byte[] file = seed.clone();
				for (int k = 0; k < 4; k++) {
					file[random.nextInt(Math.min(file.length, 160))] = (byte) random.nextInt(256);
				}
				if (i % 4 == 0) {
					file = Arrays.copyOf(file, random.nextInt(file.length));
				}
				final ByteBuffer data = ByteBuffer.wrap(file);
				try {
					if (DdsDecoder.isDds(data)) {
						DdsDecoder.decode(data, 0, true);
					}
					else if (BlpDecoder.isBlp(data)) {
						BlpDecoder.decode(data, 0, true);
					}
				}
				catch (final IOException | RuntimeException e) {
					// reported by the loader
				}
			}
		}
	}

	@Test
	void aDecodeFailureLeavesADataErrorsEntry() {
		DataErrors.clear();
		RawOpenGLTextureResourceAccess.recordDecodeFailure("units\\bad.blp", new IOException("Truncated JPEG BLP file"));
		assertTrue(DataErrors.entries().get(0).startsWith(DataErrors.BAD_TEXTURE + ": units\\bad.blp"));
		DataErrors.clear();
	}
}
