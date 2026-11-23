package com.etheller.warsmash.viewer5.handlers.mdx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Raises the vertices of a model file by a height that depends on where each vertex is, and turns their normals to
 * those of that height's surface: such a model is lit as the ground it is laid on, as the cliffs are, and not by the
 * slopes of its own faces, which would light it brighter or darker than the ground and cliffs it meets.
 */
public final class MdxVertexLift {
	/** The height at a point of the model's own horizontal plane. */
	public interface Height {
		float at(float x, float y);
	}

	private static final int TAG_GEOS = 0x534F4547;
	private static final int TAG_VRTX = 0x58545256;
	private static final int TAG_NRMS = 0x534D524E;
	/** Half the distance over which the slope of the height is taken for a normal. */
	private static final float NORMAL_STEP = 64;

	private MdxVertexLift() {
	}

	/** A copy of the model file with {@code height} added to the z of every geoset vertex, and its normals. */
	public static byte[] lift(final byte[] file, final Height height) {
		final byte[] lifted = file.clone();
		final ByteBuffer buffer = ByteBuffer.wrap(lifted).order(ByteOrder.LITTLE_ENDIAN);
		int chunk = 4;
		while ((chunk + 8) <= lifted.length) {
			final int tag = buffer.getInt(chunk);
			final int size = buffer.getInt(chunk + 4);
			final int end = chunk + 8 + size;
			if ((size < 0) || (end > lifted.length)) {
				break;
			}
			if (tag == TAG_GEOS) {
				int geoset = chunk + 8;
				while ((geoset + 12) <= end) {
					final int geosetSize = buffer.getInt(geoset);
					if ((geosetSize <= 0) || (buffer.getInt(geoset + 4) != TAG_VRTX)) {
						break;
					}
					final int count = buffer.getInt(geoset + 8);
					final int first = geoset + 12;
					if ((count < 0) || ((first + (count * 12L)) > end)) {
						break;
					}
					for (int i = 0; i < count; i++) {
						final int at = first + (i * 12);
						buffer.putFloat(at + 8,
								buffer.getFloat(at + 8) + height.at(buffer.getFloat(at), buffer.getFloat(at + 4)));
					}
					final int normals = first + (count * 12);
					if (((normals + 8 + (count * 12L)) <= end) && (buffer.getInt(normals) == TAG_NRMS)
							&& (buffer.getInt(normals + 4) == count)) {
						for (int i = 0; i < count; i++) {
							final float x = buffer.getFloat(first + (i * 12));
							final float y = buffer.getFloat(first + (i * 12) + 4);
							final float nx = height.at(x - NORMAL_STEP, y) - height.at(x + NORMAL_STEP, y);
							final float ny = height.at(x, y - NORMAL_STEP) - height.at(x, y + NORMAL_STEP);
							final float nz = 2 * NORMAL_STEP;
							final float length = (float) Math.sqrt((nx * nx) + (ny * ny) + (nz * nz));
							final int at = normals + 8 + (i * 12);
							buffer.putFloat(at, nx / length);
							buffer.putFloat(at + 4, ny / length);
							buffer.putFloat(at + 8, nz / length);
						}
					}
					geoset += geosetSize;
				}
			}
			chunk = end;
		}
		return lifted;
	}
}
