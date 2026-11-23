package com.etheller.warsmash.viewer5.handlers.w3x.environment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.badlogic.gdx.graphics.GL30;
import com.etheller.warsmash.util.ImageUtils.RgbaImage;

/**
 * The ground tile images of a map, packed into a few texture arrays. A map can carry up to 17 ground images (16 tile
 * types and the blight), but a fragment shader may read only 16 textures on most phone GPUs, and the ground shader
 * also needs the shadow and fog maps: one array per image left the images past the thirteenth with no sampler.
 */
final class GroundBanks {
	/** Layers one image takes: the 4x4 tiles of the plain image, 4x8 of an extended one. */
	static final int LAYERS_PER_SLOT = 32;
	static final int SLOTS_PER_BANK = 6;
	static final int MAX_BANKS = 3;
	static final int MAX_SLOTS = SLOTS_PER_BANK * MAX_BANKS;
	private static final int DEFAULT_TILE_SIZE = 64;

	private final int[] banks = new int[MAX_BANKS];
	private int tileSize;
	private int slotCount;

	/** @return the slot of the next image, or -1 when the arrays are full */
	int allocateSlot() {
		return this.slotCount < MAX_SLOTS ? this.slotCount++ : -1;
	}

	int bankCount() {
		return Math.min(MAX_BANKS, (this.slotCount + SLOTS_PER_BANK - 1) / SLOTS_PER_BANK);
	}

	int bankTexture(final GL30 gl, final int bank) {
		ensureBank(gl, bank);
		return this.banks[bank];
	}

	private void ensureBank(final GL30 gl, final int bank) {
		if (this.banks[bank] != 0) {
			return;
		}
		if (this.tileSize == 0) {
			this.tileSize = DEFAULT_TILE_SIZE;
		}
		final int id = gl.glGenTexture();
		this.banks[bank] = id;
		gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, id);
		final int layers = SLOTS_PER_BANK * LAYERS_PER_SLOT;
		// Zeroed rather than left undefined: an image that failed to load must not show whatever the driver gave.
		final ByteBuffer empty = ByteBuffer.allocateDirect(this.tileSize * this.tileSize * 4 * layers)
				.order(ByteOrder.nativeOrder());
		gl.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL30.GL_RGBA8, this.tileSize, this.tileSize, layers, 0,
				GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, empty);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR_MIPMAP_LINEAR);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
		gl.glGenerateMipmap(GL30.GL_TEXTURE_2D_ARRAY);
	}

	/**
	 * Puts an image into its slot.
	 *
	 * @param sourceTile the tile size of the image (a quarter of its height)
	 */
	void upload(final GL30 gl, final int slot, final RgbaImage image, final int sourceTile, final boolean extended) {
		if (this.tileSize == 0) {
			this.tileSize = sourceTile;
		}
		final int bank = slot / SLOTS_PER_BANK;
		ensureBank(gl, bank);
		gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, this.banks[bank]);
		final int layerBase = (slot % SLOTS_PER_BANK) * LAYERS_PER_SLOT;
		final ByteBuffer pixels = image.getPixels();
		final int width = image.getWidth();
		final ByteBuffer scaled = sourceTile == this.tileSize ? null
				: ByteBuffer.allocateDirect(this.tileSize * this.tileSize * 4).order(ByteOrder.nativeOrder());
		if (scaled == null) {
			gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, width);
		}
		for (int y = 0; y < 4; y++) {
			for (int x = 0; x < 4; x++) {
				for (int half = 0; half < (extended ? 2 : 1); half++) {
					final int originX = (x + (half * 4)) * sourceTile;
					final int originY = y * sourceTile;
					final int layer = layerBase + (y * 4) + x + (half * 16);
					if (scaled == null) {
						pixels.position(((originY * width) + originX) * 4);
						gl.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, this.tileSize, this.tileSize, 1,
								GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, pixels);
					}
					else {
						scaled.clear();
						for (int ty = 0; ty < this.tileSize; ty++) {
							final int sy = originY + ((ty * sourceTile) / this.tileSize);
							for (int tx = 0; tx < this.tileSize; tx++) {
								final int sx = originX + ((tx * sourceTile) / this.tileSize);
								final int from = ((sy * width) + sx) * 4;
								scaled.put(pixels.get(from)).put(pixels.get(from + 1)).put(pixels.get(from + 2))
										.put(pixels.get(from + 3));
							}
						}
						scaled.flip();
						gl.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, this.tileSize, this.tileSize, 1,
								GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, scaled);
					}
				}
			}
		}
		if (scaled == null) {
			gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, 0);
		}
		gl.glGenerateMipmap(GL30.GL_TEXTURE_2D_ARRAY);
	}

	void dispose(final GL30 gl) {
		for (int i = 0; i < this.banks.length; i++) {
			if (this.banks[i] != 0) {
				gl.glDeleteTexture(this.banks[i]);
				this.banks[i] = 0;
			}
		}
	}
}
