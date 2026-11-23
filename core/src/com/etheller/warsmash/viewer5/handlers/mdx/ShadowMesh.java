package com.etheller.warsmash.viewer5.handlers.mdx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.collision.BoundingBox;

/**
 * A coarse copy of the geometry of an HD model for the ground shadow mask. The mask is a few pixels to a unit, so
 * vertices that are moved by the same bone and lie in one small cell of the model share a vertex, and every vertex
 * follows its main bone alone. The geosets share one vertex buffer and are laid out in the order of the model's
 * batches, so that a run of consecutive visible ones is a single draw: draws and bone fetches, not triangles, are
 * what a phone's GPU spends the mask pass on. It is read back from the model's own buffers the first time the model
 * casts a shadow.
 */
final class ShadowMesh {
	private static final int STRIDE = 24;
	private static final int UV_OFFSET = 12;
	private static final int BONE_OFFSET = 20;
	private static final int MAX_VERTICES = 65535;
	/** The smallest cell, in the model's units, vertices are joined in. */
	private static final float MIN_CELL = 4f;
	/** A model is cut into at most this many cells along its longest side, which keeps one a pixel of the mask. */
	private static final float CELLS_ACROSS = 20f;
	/**
	 * Cut-out geosets with no more vertices than this keep their every vertex and their alpha test, as the leaves of a
	 * tree must: the shape is in the alpha. The larger ones are the bodies of units and buildings, where it only
	 * trims an edge, and are drawn solid with the rest.
	 */
	private static final int EXACT_CUTOUT_VERTICES = 1200;
	/** A blended geoset this small is a sprite or decal; as a solid it would cast the whole of its quad. */
	private static final int SPRITE_VERTICES = 16;
	/**
	 * The larger cut-out geosets of scenery (the foliage of a tall tree) are joined in cells this much finer, and by
	 * their texture coordinates, so that the narrow points of their shadow stay and are not a solid block.
	 */
	private static final float FINE_CELLS_ACROSS = 80f;
	private static final float CUTOUT_UV_STEPS = 256f;

	final int vertexBuffer;
	final int indexBuffer;
	/** For every batch of the model, where its triangles start in the index buffer, or -1 when it has none. */
	final int[] first;
	final int[] count;
	/** Whether the batch is drawn with the alpha test of its texture. */
	final boolean[] cutout;

	private ShadowMesh(final int vertexBuffer, final int indexBuffer, final int[] first, final int[] count,
			final boolean[] cutout) {
		this.vertexBuffer = vertexBuffer;
		this.indexBuffer = indexBuffer;
		this.first = first;
		this.count = count;
		this.cutout = cutout;
	}

	void bind(final ShaderProgram shader) {
		shader.setVertexAttribute("a_position", 3, GL20.GL_FLOAT, false, STRIDE, 0);
		shader.setVertexAttribute("a_uv", 2, GL20.GL_FLOAT, false, STRIDE, UV_OFFSET);
		shader.setVertexAttribute("a_bones", 4, GL20.GL_UNSIGNED_BYTE, false, STRIDE, BONE_OFFSET);
	}

	void dispose(final GL20 gl) {
		gl.glDeleteBuffer(this.vertexBuffer);
		gl.glDeleteBuffer(this.indexBuffer);
	}

	private static final class Key {
		private final int a, b, c, d, e, f;

		private Key(final int a, final int b, final int c, final int d, final int e, final int f) {
			this.a = a;
			this.b = b;
			this.c = c;
			this.d = d;
			this.e = e;
			this.f = f;
		}

		@Override
		public boolean equals(final Object o) {
			final Key k = (Key) o;
			return (this.a == k.a) && (this.b == k.b) && (this.c == k.c) && (this.d == k.d) && (this.e == k.e)
					&& (this.f == k.f);
		}

		@Override
		public int hashCode() {
			int h = this.a;
			h = (h * 31) + this.b;
			h = (h * 31) + this.c;
			h = (h * 31) + this.d;
			h = (h * 31) + this.e;
			return (h * 31) + this.f;
		}
	}

	/**
	 * @param exactCutouts whether the small cut-out geosets keep their alpha test
	 * @return the mesh, or null when the model has to be drawn from its own buffers
	 */
	static ShadowMesh build(final MdxModel model, final boolean exactCutouts) {
		try {
			return make(model, exactCutouts);
		}
		catch (final RuntimeException e) {
			return null;
		}
	}

	private static ShadowMesh make(final MdxModel model, final boolean exactCutouts) {
		final GL30 gl = Gdx.gl30;
		if ((gl == null) || (model.arrayBuffer == 0)) {
			return null;
		}
		final List<Batch> batches = model.batches;
		final int batchCount = batches.size();
		int upperBound = 0;
		for (int bi = 0; bi < batchCount; bi++) {
			final Batch batch = batches.get(bi);
			if (batch.hd) {
				if ((batch.skinningType != SkinningType.Skin) || batch.geoset.wideSkin) {
					return null;
				}
				upperBound += batch.geoset.vertices;
			}
		}
		if ((upperBound == 0) || (upperBound > MAX_VERTICES)) {
			return null;
		}
		float cell = MIN_CELL;
		final BoundingBox box = model.bounds.getBoundingBox();
		if (box != null) {
			final float size = Math.max(box.max.x - box.min.x, Math.max(box.max.y - box.min.y, box.max.z - box.min.z));
			cell = Math.max(cell, size / CELLS_ACROSS);
		}
		final float fineCell = Math.max(MIN_CELL / 2f, cell * CELLS_ACROSS / FINE_CELLS_ACROSS);
		final int[] first = new int[batchCount];
		final int[] count = new int[batchCount];
		final boolean[] cutout = new boolean[batchCount];
		Arrays.fill(first, -1);
		final ByteBuffer vertexBytes = ByteBuffer.allocateDirect(upperBound * STRIDE).order(ByteOrder.nativeOrder());
		int[] indices = new int[1 << 12];
		int indexTotal = 0;
		int vertexTotal = 0;
		for (int bi = 0; bi < batchCount; bi++) {
			final Batch batch = batches.get(bi);
			final Geoset geoset = batch.geoset;
			final Layer layer = batch.layer;
			final int n = geoset.vertices;
			// Additive glows, modulated layers and the like are light, not matter.
			if (!batch.hd || (layer.filterMode > 2) || (n == 0) || (geoset.elements == 0)) {
				continue;
			}
			final boolean isCutout = (exactCutouts || ((layer.filterMode == 2) && (n <= SPRITE_VERTICES)))
					&& (layer.filterMode != 0);
			final boolean exactVertices = isCutout && (n <= EXACT_CUTOUT_VERTICES);
			final float[] positions = new float[n * 3];
			final float[] uvs = isCutout ? new float[n * 2] : null;
			final byte[] skin = new byte[n * 8];
			final byte[] faceBytes = new byte[geoset.elements * 2];
			gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, model.arrayBuffer);
			if (!read(gl, GL20.GL_ARRAY_BUFFER, geoset.positionOffset, positions, null)
					|| ((uvs != null)
							&& !read(gl, GL20.GL_ARRAY_BUFFER, geoset.uvOffset + (layer.coordId * n * 8), uvs, null))
					|| !read(gl, GL20.GL_ARRAY_BUFFER, geoset.skinOffset, null, skin)) {
				return null;
			}
			gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, model.elementBuffer);
			if (!read(gl, GL20.GL_ELEMENT_ARRAY_BUFFER, geoset.faceOffset, null, faceBytes)) {
				return null;
			}
			final ShortBuffer faces = ByteBuffer.wrap(faceBytes).order(ByteOrder.nativeOrder()).asShortBuffer();

			final Map<Key, Integer> lookup = new HashMap<>();
			final int[] remap = new int[n];
			final float[] sum = new float[n * 3];
			final int[] members = new int[n];
			final int[] bones = new int[n];
			final int base = vertexTotal;
			int local = 0;
			for (int v = 0; v < n; v++) {
				// The bone with the most weight; the bytes of a skin are four bones, then their four weights.
				int bone = skin[v * 8] & 0xFF;
				int best = skin[(v * 8) + 4] & 0xFF;
				for (int j = 1; j < 4; j++) {
					final int weight = skin[(v * 8) + 4 + j] & 0xFF;
					if (weight > best) {
						best = weight;
						bone = skin[(v * 8) + j] & 0xFF;
					}
				}
				final float x = positions[v * 3];
				final float y = positions[(v * 3) + 1];
				final float z = positions[(v * 3) + 2];
				final Key key;
				if (exactVertices) {
					key = new Key(Float.floatToIntBits(x), Float.floatToIntBits(y), Float.floatToIntBits(z), bone,
							Float.floatToIntBits(uvs[v * 2]), Float.floatToIntBits(uvs[(v * 2) + 1]));
				}
				else if (isCutout) {
					key = new Key((int) Math.floor(x / fineCell), (int) Math.floor(y / fineCell),
							(int) Math.floor(z / fineCell), bone, Math.round(uvs[v * 2] * CUTOUT_UV_STEPS),
							Math.round(uvs[(v * 2) + 1] * CUTOUT_UV_STEPS));
				}
				else {
					key = new Key((int) Math.floor(x / cell), (int) Math.floor(y / cell), (int) Math.floor(z / cell),
							bone, 0, 0);
				}
				Integer slot = lookup.get(key);
				if (slot == null) {
					slot = local++;
					lookup.put(key, slot);
					bones[slot] = bone;
					if (isCutout) {
						vertexBytes.putFloat(((base + slot) * STRIDE) + UV_OFFSET, uvs[v * 2]);
						vertexBytes.putFloat(((base + slot) * STRIDE) + UV_OFFSET + 4, uvs[(v * 2) + 1]);
					}
				}
				sum[slot * 3] += x;
				sum[(slot * 3) + 1] += y;
				sum[(slot * 3) + 2] += z;
				members[slot]++;
				remap[v] = slot;
			}
			for (int s = 0; s < local; s++) {
				final int at = (base + s) * STRIDE;
				final float inv = 1f / members[s];
				vertexBytes.putFloat(at, sum[s * 3] * inv);
				vertexBytes.putFloat(at + 4, sum[(s * 3) + 1] * inv);
				vertexBytes.putFloat(at + 8, sum[(s * 3) + 2] * inv);
				if (!isCutout) {
					vertexBytes.putFloat(at + UV_OFFSET, 0f);
					vertexBytes.putFloat(at + UV_OFFSET + 4, 0f);
				}
				vertexBytes.putInt(at + BONE_OFFSET, bones[s]);
			}
			vertexTotal += local;
			first[bi] = indexTotal;
			for (int t = 0; (t + 2) < geoset.elements; t += 3) {
				final int p = base + remap[faces.get(t) & 0xFFFF];
				final int q = base + remap[faces.get(t + 1) & 0xFFFF];
				final int r = base + remap[faces.get(t + 2) & 0xFFFF];
				if ((p == q) || (q == r) || (p == r)) {
					continue;
				}
				if ((indexTotal + 3) > indices.length) {
					indices = Arrays.copyOf(indices, indices.length * 2);
				}
				indices[indexTotal++] = p;
				indices[indexTotal++] = q;
				indices[indexTotal++] = r;
			}
			count[bi] = indexTotal - first[bi];
			cutout[bi] = isCutout;
		}
		if (indexTotal == 0) {
			return null;
		}
		final ByteBuffer indexBytes = ByteBuffer.allocateDirect(indexTotal * 2).order(ByteOrder.nativeOrder());
		for (int i = 0; i < indexTotal; i++) {
			indexBytes.putShort(i * 2, (short) indices[i]);
		}
		final int vertexBuffer = gl.glGenBuffer();
		gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, vertexBuffer);
		vertexBytes.position(0).limit(vertexTotal * STRIDE);
		gl.glBufferData(GL20.GL_ARRAY_BUFFER, vertexTotal * STRIDE, vertexBytes, GL20.GL_STATIC_DRAW);
		final int indexBuffer = gl.glGenBuffer();
		gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
		indexBytes.position(0).limit(indexTotal * 2);
		gl.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, indexTotal * 2, indexBytes, GL20.GL_STATIC_DRAW);
		return new ShadowMesh(vertexBuffer, indexBuffer, first, count, cutout);
	}

	/** Reads floats or bytes from the bound buffer of a target; false when the driver does not let it be mapped. */
	private static boolean read(final GL30 gl, final int target, final int offset, final float[] floats,
			final byte[] bytes) {
		final int length = floats != null ? floats.length * 4 : bytes.length;
		final java.nio.Buffer mapped = gl.glMapBufferRange(target, offset, length, GL30.GL_MAP_READ_BIT);
		if (!(mapped instanceof ByteBuffer)) {
			return false;
		}
		final ByteBuffer data = ((ByteBuffer) mapped).order(ByteOrder.nativeOrder());
		if (floats != null) {
			data.asFloatBuffer().get(floats);
		}
		else {
			data.get(bytes);
		}
		gl.glUnmapBuffer(target);
		return true;
	}
}
