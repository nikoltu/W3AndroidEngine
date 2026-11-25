package com.etheller.warsmash.viewer5.handlers.blp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.OffHeapBlock;

/** A file under a DX10 header holds what the same format holds under its own four character code. */
class DdsDx10Test {
	private static final int DX10 = 'D' | ('X' << 8) | ('1' << 16) | ('0' << 24);

	@Test
	void blockFormatsDecodeAsTheirLegacyCodes() throws IOException {
		final int[] dxgi = { 71, 72, 74, 75, 77, 78 };
		final String[] legacy = { "DXT1", "DXT1", "DXT3", "DXT3", "DXT5", "DXT5" };
		for (int f = 0; f < dxgi.length; f++) {
			for (final int[] size : new int[][] { { 64, 32 }, { 13, 7 }, { 1, 1 } }) {
				final byte[] payload = payload(size[0], size[1], legacy[f].equals("DXT1") ? 8 : 16, 7 + f);
				final MipLevels expected = DdsDecoder.decode(ByteBuffer.wrap(file(0, legacy[f], size, payload)), 0, true)
						.getLevels();
				final MipLevels actual = DdsDecoder.decode(ByteBuffer.wrap(file(dxgi[f], null, size, payload)), 0, true)
						.getLevels();
				assertEquals(expected.count(), actual.count());
				for (int level = 0; level < expected.count(); level++) {
					assertArrayEquals(bytes(expected.getRgba(level)), bytes(actual.getRgba(level)));
				}
			}
		}
	}

	@Test
	void blocksOfADx10FileAreTheFilesBytes() {
		final int[] size = { 32, 16 };
		final byte[] payload = payload(size[0], size[1], 8, 3);
		final byte[] file = file(72, null, size, payload);
		final CompressedMips blocks = DdsDecoder.blocks(OffHeapBlock.copyOf(file), 0);
		assertNotNull(blocks);
		assertEquals(CompressedMips.FORMAT_RGBA_S3TC_DXT1, blocks.getFormat());
		final ByteBuffer top = blocks.getView(0);
		for (int i = 0; i < top.remaining(); i++) {
			assertEquals(file[148 + i], top.get(i));
		}
		blocks.free();
	}

	@Test
	void fourBytePixelsKeepOrSwapTheirChannels() throws IOException {
		final int[] size = { 5, 3 };
		final byte[] payload = new byte[5 * 3 * 4 * 2];
		new Random(5).nextBytes(payload);
		final ByteBuffer rgba = DdsDecoder.decode(ByteBuffer.wrap(file(28, null, new int[] { 5, 3 }, payload)), 0, false)
				.getRgba();
		final ByteBuffer bgra = DdsDecoder.decode(ByteBuffer.wrap(file(87, null, size, payload)), 0, false).getRgba();
		final byte[] kept = bytes(rgba);
		final byte[] swapped = bytes(bgra);
		for (int at = 0; at < 5 * 3 * 4; at += 4) {
			assertEquals(payload[at], kept[at]);
			assertEquals(payload[at + 3], kept[at + 3]);
			assertEquals(payload[at], swapped[at + 2]);
			assertEquals(payload[at + 2], swapped[at]);
			assertEquals(payload[at + 3], swapped[at + 3]);
		}
	}

	@Test
	void fourBytePixelsDecodeOffHeapToo() throws IOException {
		final byte[] payload = new byte[8 * 4 * 4 * 2];
		new Random(9).nextBytes(payload);
		final byte[] file = file(28, null, new int[] { 8, 4 }, payload);
		final OffHeapBlock block = OffHeapBlock.copyOf(file);
		final DdsDecoder image = DdsDecoder.decode(block.buffer(), 0, true, true);
		assertArrayEquals(Arrays.copyOf(payload, 8 * 4 * 4), bytes(image.getRgba()));
		image.getLevels().free();
		block.free();
	}

	@Test
	void formatsWithoutADecoderAreRefused() {
		assertThrows(IOException.class,
				() -> DdsDecoder.decode(ByteBuffer.wrap(file(98, null, new int[] { 4, 4 }, new byte[16])), 0, false));
	}

	/** Header, extra header for dxgi other than 0, then the levels of a chain of payload bytes. */
	private static byte[] file(final int dxgi, final String legacy, final int[] size, final byte[] payload) {
		final int start = dxgi == 0 ? 128 : 148;
		final byte[] file = new byte[start + payload.length];
		System.arraycopy(payload, 0, file, start, payload.length);
		final ByteBuffer header = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
		header.putInt(0, 0x20534444);
		header.putInt(8, 0x20000);
		header.putInt(12, size[1]);
		header.putInt(16, size[0]);
		int levels = 1;
		for (int edge = Math.max(size[0], size[1]); edge > 1; edge >>= 1) {
			levels++;
		}
		header.putInt(28, dxgi == 28 || dxgi == 87 ? 1 : levels);
		header.putInt(80, 0x4);
		header.putInt(84, dxgi == 0 ? legacy.charAt(0) | (legacy.charAt(1) << 8) | (legacy.charAt(2) << 16)
				| (legacy.charAt(3) << 24) : DX10);
		if (dxgi != 0) {
			header.putInt(128, dxgi);
		}
		return file;
	}

	/** Random blocks for every level of a chain. */
	private static byte[] payload(final int width, final int height, final int blockSize, final int seed) {
		int total = 0;
		int levels = 1;
		for (int edge = Math.max(width, height); edge > 1; edge >>= 1) {
			levels++;
		}
		for (int level = 0; level < levels; level++) {
			total += Math.max(1, (Math.max(1, width >> level) + 3) / 4)
					* Math.max(1, (Math.max(1, height >> level) + 3) / 4) * blockSize;
		}
		final byte[] bytes = new byte[total];
		new Random(seed).nextBytes(bytes);
		return bytes;
	}

	private static byte[] bytes(final ByteBuffer buffer) {
		final ByteBuffer view = buffer.duplicate();
		view.clear();
		final byte[] bytes = new byte[view.remaining()];
		view.get(bytes);
		return bytes;
	}
}
