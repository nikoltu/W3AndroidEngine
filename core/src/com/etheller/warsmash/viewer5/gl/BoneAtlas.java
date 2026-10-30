package com.etheller.warsmash.viewer5.gl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.badlogic.gdx.graphics.GL20;

/**
 * The bone matrices of every instance a scene draws in a frame, packed one after another into one texture and sent
 * with one upload. One upload per instance was the largest single cost of a frame on phones.
 */
public class BoneAtlas {
	public static final int WIDTH = 1024;
	// Uploading into a texture the GPU may still read from the last frame makes some drivers wait or copy it.
	private static final int TEXTURE_COUNT = 3;

	private final GL20 gl;
	private final DataTexture[] textures = new DataTexture[TEXTURE_COUNT];
	private int textureIndex;
	private float[] data = new float[WIDTH * 4 * 16];
	private FloatBuffer heap;
	private int usedTexels;
	private int stamp;

	public BoneAtlas(final GL20 gl) {
		this.gl = gl;
	}

	public void begin() {
		this.usedTexels = 0;
		this.stamp++;
	}

	public int getStamp() {
		return this.stamp;
	}

	/**
	 * Returns the texel the matrices start at, or -1 when they would not fit in one row.
	 */
	public int add(final float[] matrices, final int floatCount) {
		final int texels = floatCount / 4;
		if (texels > WIDTH) {
			return -1;
		}
		int base = this.usedTexels;
		final int rowEnd = ((base / WIDTH) + 1) * WIDTH;
		if ((base + texels) > rowEnd) {
			base = rowEnd;
		}
		final int end = base + texels;
		if ((end * 4) > this.data.length) {
			final float[] grown = new float[Math.max(this.data.length * 2, end * 4)];
			System.arraycopy(this.data, 0, grown, 0, this.usedTexels * 4);
			this.data = grown;
		}
		System.arraycopy(matrices, 0, this.data, base * 4, floatCount);
		this.usedTexels = end;
		return base;
	}

	public void upload() {
		if (this.usedTexels == 0) {
			return;
		}
		final int rows = ((this.usedTexels - 1) / WIDTH) + 1;
		final int floats = rows * WIDTH * 4;
		if ((this.heap == null) || (this.heap.capacity() < floats)) {
			this.heap = ByteBuffer.allocateDirect(Math.max(floats, this.data.length) * 4).order(ByteOrder.nativeOrder())
					.asFloatBuffer();
		}
		if (this.data.length < floats) {
			final float[] grown = new float[floats];
			System.arraycopy(this.data, 0, grown, 0, this.usedTexels * 4);
			this.data = grown;
		}
		this.heap.clear();
		this.heap.put(this.data, 0, floats);
		this.heap.flip();
		this.textureIndex = (this.textureIndex + 1) % TEXTURE_COUNT;
		DataTexture texture = this.textures[this.textureIndex];
		if (texture == null) {
			texture = new DataTexture(this.gl, 4, WIDTH, rows);
			this.textures[this.textureIndex] = texture;
		}
		else {
			texture.reserve(WIDTH, rows);
		}
		texture.bindAndUpdate(this.heap, WIDTH, rows);
	}

	public DataTexture getTexture() {
		return this.textures[this.textureIndex];
	}
}
