package com.etheller.warsmash.viewer5.handlers.mdx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.GL20;
import com.etheller.warsmash.util.RenderMathUtils;
import com.hiveworkshop.rms.parsers.mdlx.MdlxGeoset;

public class SetupGeosets {

	public static void setupGeosets(final MdxModel model, final List<MdlxGeoset> geosets, final boolean bigNodeSpace) {
		if (geosets.size() > 0) {
			final GL20 gl = model.viewer.gl;
			int positionBytes = 0;
			int normalBytes = 0;
			int uvBytes = 0;
			int tangentBytes = 0;
			int skinBytes = 0;
			int faceBytes = 0;
			final SkinningType[] batchTypes = new SkinningType[geosets.size()];

			final int extendedBatchStride = bigNodeSpace ? 36 : 9;
			final int normalBatchStride = bigNodeSpace ? 20 : 5;
			final int openGLSkinType = bigNodeSpace ? GL20.GL_UNSIGNED_INT : GL20.GL_UNSIGNED_BYTE;
			final int normalBatchBoneCountOffsetBytes = bigNodeSpace ? 16 : 4;
			final int extendedBatchBoneCountOffsetBytes = bigNodeSpace ? 32 : 8;

			for (int i = 0, l = geosets.size(); i < l; i++) {
				final MdlxGeoset geoset = geosets.get(i);

				if ((geoset.lod == 0) || (geoset.lod == -1)) {
					final int vertices = geoset.getVertices().length / 3;

					positionBytes += vertices * 12;
					normalBytes += vertices * 12;
					uvBytes += geoset.getUvSets().length * vertices * 8;

					if (geoset.tangents != null) {
						tangentBytes += vertices * 16;
					}

					if ((geoset.skin != null) && isHdMaterial(model, geoset)) {
						skinBytes += vertices * (needsWideSkin(geoset) ? WIDE_SKIN_STRIDE : 8);

						batchTypes[i] = SkinningType.Skin;
					}
					else if (geoset.skin != null) {
						skinBytes += vertices * extendedBatchStride;

						batchTypes[i] = SkinningType.ExtendedVertexGroups;
					}
					else {
						long biggestGroup = 0;

						for (final long group : geoset.getMatrixGroups()) {
							if (group > biggestGroup) {
								biggestGroup = group;
							}
						}

						if (biggestGroup > 4) {
							skinBytes += vertices * extendedBatchStride;

							batchTypes[i] = SkinningType.ExtendedVertexGroups;
						}
						else {
							skinBytes += vertices * normalBatchStride;

							batchTypes[i] = SkinningType.VertexGroups;
						}
					}

					faceBytes += geoset.getFaces().length * 2;
				}
			}

			int positionOffset = 0;
			int normalOffset = positionOffset + positionBytes;
			int uvOffset = normalOffset + normalBytes;
			int tangentOffset = uvOffset + uvBytes;
			int skinOffset = tangentOffset + tangentBytes;
			int faceOffset = 0;

			model.arrayBuffer = gl.glGenBuffer();
			gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, model.arrayBuffer);
			gl.glBufferData(GL20.GL_ARRAY_BUFFER, skinOffset + skinBytes, null, GL20.GL_STATIC_DRAW);

			model.elementBuffer = gl.glGenBuffer();
			gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, model.elementBuffer);
			gl.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, faceBytes, null, GL20.GL_STATIC_DRAW);

			for (int i = 0, l = geosets.size(); i < l; i++) {
				final MdlxGeoset geoset = geosets.get(i);

				final SkinningType batchType = batchTypes[i];
				if ((geoset.lod == 0) || (geoset.lod == -1)) {
					final float[] positions = geoset.getVertices();
					final float[] normals = geoset.getNormals();
					final float[][] uvSets = geoset.getUvSets();
					final float[] tangents = geoset.getTangents();
					final int[] faces = geoset.getFaces();
					int[] skin = null;
					boolean wideSkin = false;
					final int vertices = geoset.getVertices().length / 3;

					int maxBones;
					int skinStride;
					int boneCountOffsetBytes;
					if (batchType == SkinningType.ExtendedVertexGroups) {
						maxBones = 8;
						skinStride = extendedBatchStride;
						boneCountOffsetBytes = extendedBatchBoneCountOffsetBytes;
					}
					else {
						maxBones = 4;
						skinStride = normalBatchStride;
						boneCountOffsetBytes = normalBatchBoneCountOffsetBytes;
					}

					if (batchType == SkinningType.Skin) {
						skin = new int[geoset.skin.length];
						for (int j = 0; j < geoset.skin.length; j++) {
							skin[j] = geoset.skin[j] & 0xFFFF;
						}
						wideSkin = needsWideSkin(geoset);
					}
					else if (geoset.skin != null) {
						skin = skinToVertexGroups(geoset.skin, vertices);
					}
					else {
						final long[] matrixIndices = geoset.getMatrixIndices();
						final short[] vertexGroups = geoset.getVertexGroups();
						final List<long[]> matrixGroups = new ArrayList<>();
						int offset = 0;
						// Normally the shader supports up to 4 bones per vertex.
						// This is enough for almost every existing Warcraft 3 model.
						// That being said, there are a few models with geosets that need more, for
						// example the Water Elemental.
						// These geosets use a different shader, which support up to 8 bones per vertex.

						skin = new int[vertices * (maxBones + 1)];

						// Slice the matrix groups
						for (final long size : geoset.getMatrixGroups()) {
							matrixGroups.add(Arrays.copyOfRange(matrixIndices, offset, (int) (offset + size)));
							offset += size;
						}

						// Parse the skinning.
						for (int si = 0; si < vertices; si++) {
							final short vertexGroup = vertexGroups[si];
							final long[] matrixGroup = (vertexGroup >= matrixGroups.size()) ? null
									: matrixGroups.get(vertexGroup);

							offset = si * (maxBones + 1);

							// Somehow in some bad models a vertex group index refers to an invalid matrix
							// group.
							// Such models are still loaded by the game.
							if (matrixGroup != null) {
								final int bones = Math.min(matrixGroup.length, maxBones);

								for (int j = 0; j < bones; j++) {
									skin[offset + j] = (int) (matrixGroup[j] + 1); // 1 is added to diffrentiate
																					// between matrix 0, and no matrix.
								}

								skin[offset + maxBones] = bones;
							}
						}
					}

					final boolean unselectable = geoset.getSelectionFlags() == 4;
					final Geoset vGeoset = new Geoset(model, model.getGeosets().size(), i, positionOffset,
							normalOffset, uvOffset, tangentOffset, skinOffset, faceOffset, vertices, faces.length,
							openGLSkinType, skinStride, boneCountOffsetBytes, unselectable, geoset);
					vGeoset.wideSkin = wideSkin;

					model.getGeosets().add(vGeoset);

					final Material material = model.materials.get((int) geoset.getMaterialId());
					final boolean isHd = "Shader_HD_DefaultUnit".equals(material.shader);

					if (isHd) {
						model.batches.add(new Batch(model.batches.size(), vGeoset, material, batchType));
					}
					else {
						for (final Layer layer : model.getMaterials().get((int) geoset.getMaterialId()).layers) {
							model.batches.add(new Batch(model.batches.size(), vGeoset, layer, batchType));
						}
					}

					// Positions.
					gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, positionOffset, positions.length * 4,
							RenderMathUtils.wrap(positions));
					positionOffset += positions.length * 4;

					// Normals.
					gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, normalOffset, normals.length * 4,
							RenderMathUtils.wrap(normals));
					normalOffset += normals.length * 4;

					// Texture coordinates.
					for (final float[] uvSet : uvSets) {
						gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, uvOffset, uvSet.length * 4, RenderMathUtils.wrap(uvSet));
						uvOffset += uvSet.length * 4;
					}

					if (tangents != null) {
						gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, tangentOffset, tangents.length * 4,
								RenderMathUtils.wrap(tangents));
						tangentOffset += tangents.length * 4;
					}

					// Skin.
					if (wideSkin) {
						final ByteBuffer wide = wideSkinBytes(skin);
						gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, skinOffset, wide.capacity(), wide);
						skinOffset += wide.capacity();
					}
					else if (batchType == SkinningType.Skin) {
						gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, skinOffset, skin.length,
								RenderMathUtils.wrapAsBytes(skin));
						skinOffset += skin.length;
					}
					else {
						gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, skinOffset, skin.length * (bigNodeSpace ? 4 : 1),
								bigNodeSpace ? RenderMathUtils.wrap(skin) : RenderMathUtils.wrapAsBytes(skin));
						skinOffset += skin.length * (bigNodeSpace ? 4 : 1);
					}

					// Faces.
					gl.glBufferSubData(GL20.GL_ELEMENT_ARRAY_BUFFER, faceOffset, faces.length * 2,
							RenderMathUtils.wrapFaces(faces));
					faceOffset += faces.length * 2;
				}

			}
		}
	}


	/**
	 * Bytes per vertex of a wide HD skin: four bone indices as unsigned shorts, then four weights as bytes.
	 */
	static final int WIDE_SKIN_STRIDE = 12;

	/**
	 * The HD skin keeps a bone index in one byte, which cannot address the bones of a model with more than 255 of them.
	 * Only the geosets that really use such an index take the wider layout.
	 */
	private static boolean needsWideSkin(final MdlxGeoset geoset) {
		final short[] skin = geoset.skin;
		for (int in = 0; in < skin.length; in += 8) {
			for (int j = 0; j < 4; j++) {
				if ((skin[in + j] & 0xFFFF) > 255) {
					return true;
				}
			}
		}
		return false;
	}

	private static ByteBuffer wideSkinBytes(final int[] skin) {
		final ByteBuffer buffer = ByteBuffer.allocateDirect((skin.length / 8) * WIDE_SKIN_STRIDE)
				.order(ByteOrder.nativeOrder());
		for (int in = 0; in < skin.length; in += 8) {
			for (int j = 0; j < 4; j++) {
				buffer.putShort((short) skin[in + j]);
			}
			for (int j = 4; j < 8; j++) {
				buffer.put((byte) skin[in + j]);
			}
		}
		buffer.clear();
		return buffer;
	}

	private static boolean isHdMaterial(final MdxModel model, final MdlxGeoset geoset) {
		return "Shader_HD_DefaultUnit".equals(model.materials.get((int) geoset.getMaterialId()).shader);
	}

	/**
	 * Reforged SD models can carry weighted skin, but the SD shader averages its bones equally. A bone therefore fills
	 * as many of the 8 extended slots as its weight is worth.
	 */
	private static int[] skinToVertexGroups(final short[] skin, final int vertices) {
		final int slots = 8;
		final int[] groups = new int[vertices * (slots + 1)];
		for (int vertex = 0; vertex < vertices; vertex++) {
			final int in = vertex * 8;
			final int out = vertex * (slots + 1);
			int total = 0;
			for (int j = 0; j < 4; j++) {
				total += skin[in + 4 + j] & 0xFF;
			}
			if (total == 0) {
				groups[out] = (skin[in] & 0xFFFF) + 1;
				groups[out + slots] = 1;
				continue;
			}
			int used = 0;
			int weightSoFar = 0;
			for (int j = 0; j < 4; j++) {
				weightSoFar += skin[in + 4 + j] & 0xFF;
				final int end = Math.round(((float) weightSoFar * slots) / total);
				while (used < end) {
					groups[out + used++] = (skin[in + j] & 0xFFFF) + 1;
				}
			}
			groups[out + slots] = used;
		}
		return groups;
	}
}
