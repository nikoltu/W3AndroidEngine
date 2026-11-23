package com.etheller.warsmash.viewer5.handlers.mdx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.Camera;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.SkeletalNode;
import com.etheller.warsmash.viewer5.Texture;
import com.etheller.warsmash.viewer5.TextureMapper;
import com.etheller.warsmash.viewer5.gl.ANGLEInstancedArrays;
import com.etheller.warsmash.viewer5.gl.ClientBuffer;
import com.etheller.warsmash.viewer5.gl.DataTexture;
import com.etheller.warsmash.viewer5.handlers.EmitterObject;
import com.etheller.warsmash.viewer5.handlers.w3x.W3xSceneLightManager;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.IVec3;
import com.hiveworkshop.rms.parsers.mdlx.MdlxParticleEmitter2.HeadOrTail;

//The total storage that emitted objects can use.
//This is enough to support all of the MDX geometry emitters.
//The memory layout is the same as this C struct:
//
//struct {
//  float p0[3]
//  float p1[3]
//  float p2[3]
//  float p3[3]
//  float health
//  byte color[4]
//  byte tail
//  byte leftRightTop[3]
//}
//
public class GeometryEmitterFuncs {
	public static final int BYTES_PER_OBJECT = 60;
	public static final int FLOATS_PER_OBJECT = BYTES_PER_OBJECT >> 2;

	// Offsets into the emitted object structure
	public static final int BYTE_OFFSET_P0 = 0;
	public static final int BYTE_OFFSET_P1 = 12;
	public static final int BYTE_OFFSET_P2 = 24;
	public static final int BYTE_OFFSET_P3 = 36;
	public static final int BYTE_OFFSET_HEALTH = 48;
	public static final int BYTE_OFFSET_COLOR = 52;
	public static final int BYTE_OFFSET_TAIL = 56;
	public static final int BYTE_OFFSET_LEFT_RIGHT_TOP = 57;

	// Offset aliases
	public static final int FLOAT_OFFSET_P0 = BYTE_OFFSET_P0 >> 2;
	public static final int FLOAT_OFFSET_P1 = BYTE_OFFSET_P1 >> 2;
	public static final int FLOAT_OFFSET_P2 = BYTE_OFFSET_P2 >> 2;
	public static final int FLOAT_OFFSET_P3 = BYTE_OFFSET_P3 >> 2;
	public static final int FLOAT_OFFSET_HEALTH = BYTE_OFFSET_HEALTH >> 2;
	public static final int FLOAT_OFFSET_COLOR = BYTE_OFFSET_COLOR >> 2;
	public static final int FLOAT_OFFSET_TAIL = BYTE_OFFSET_TAIL >> 2;
	public static final int BYTE_OFFSET_TEAM_COLOR = BYTE_OFFSET_LEFT_RIGHT_TOP;

	// The tail byte as it reads when the 4 bytes at BYTE_OFFSET_TAIL are carried through a float[]. It is a
	// normal or subnormal value, never a NaN, so the bits survive the copy.
	public static final float TAIL_FLAG = Float.intBitsToFloat(
			ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? 1 : 0x01000000);

	// Head or tail.
	public static final int HEAD = 0;
	public static final int TAIL = 1;

	// Emitter types
	public static final int EMITTER_PARTICLE2 = 0;
	public static final int EMITTER_RIBBON = 1;
	public static final int EMITTER_SPLAT = 2;
	public static final int EMITTER_UBERSPLAT = 3;
	public static final int EMITTER_SPN = 4; // added by Retera because reasons

	private static final Vector3 locationHeap = new Vector3();
	private static final Vector3 startHeap = new Vector3();
	private static final Vector3 endHeap = new Vector3();
	private static final float[] vectorTemp = new float[3];
	private static int[] drawCounts = new int[8];
	private static final Vector3[] vector3Heap = { new Vector3(), new Vector3(), new Vector3(), new Vector3(),
			new Vector3(), new Vector3() };

	public static void bindParticleEmitter2Buffer(final ParticleEmitter2 emitter, final ClientBuffer buffer,
			final int base) {
		final MdxComplexInstance instance = emitter.instance;
		final List<Particle2> objects = emitter.objects;
		final float[] staging = buffer.staging;
		final ParticleEmitter2Object emitterObject = emitter.emitterObject;
		final int modelSpace = emitterObject.modelSpace;
		final float tailLength = emitterObject.tailLength;
		int offset = 0;

		for (int objectIndex = 0; objectIndex < emitter.alive; objectIndex++) {
			final Particle2 object = objects.get(objectIndex);
			final HeadOrTail tailness = HeadOrTail.fromId(object.tail);
			final boolean tail = tailness.isIncludesTail();
			final boolean head = tailness.isIncludesHead();

			if (head) {
				final int floatOffset = (base + offset) * FLOATS_PER_OBJECT;
				final int p0Offset = floatOffset + FLOAT_OFFSET_P0;
				Vector3 location = object.location;
				final Vector3 scale = object.scale;

				// If this is a model space emitter, the location is in local space, so convert
				// it to world space.
				if (modelSpace != 0) {
					location = locationHeap.set(location).prj(emitter.node.worldMatrix);
				}

				staging[p0Offset + 0] = location.x;
				staging[p0Offset + 1] = location.y;
				staging[p0Offset + 2] = location.z;
				if (emitterObject.xYQuad != 0) {
					final Vector3 velocity = object.velocity;
					staging[p0Offset + 3] = velocity.x;
					staging[p0Offset + 4] = velocity.y;
				}
				else {
					staging[p0Offset + 3] = 0;
					staging[p0Offset + 4] = 0;
				}
				staging[p0Offset + 5] = 0;

				staging[p0Offset + 6] = scale.x;
				staging[p0Offset + 7] = scale.y;
				staging[p0Offset + 8] = scale.z;

				staging[floatOffset + FLOAT_OFFSET_HEALTH] = object.health;

				staging[floatOffset + FLOAT_OFFSET_COLOR] = 0;
				staging[floatOffset + FLOAT_OFFSET_TAIL] = 0;

				offset += 1;
			}
			if (tail) {
				final int floatOffset = (base + offset) * FLOATS_PER_OBJECT;
				final int p0Offset = floatOffset + FLOAT_OFFSET_P0;
				final Vector3 location = object.location;
				final Vector3 scale = object.scale;

				final Vector3 velocity = object.velocity;
				final Vector3 start = startHeap;
				Vector3 end = location;

				start.x = end.x - (tailLength * velocity.x);
				start.y = end.y - (tailLength * velocity.y);
				start.z = end.z - (tailLength * velocity.z);

				// If this is a model space emitter, the start and end are in local space, so
				// convert them to world space.
				if (modelSpace != 0) {
					start.prj(emitter.node.worldMatrix);
					end = endHeap.set(end).prj(emitter.node.worldMatrix);
				}

				staging[p0Offset + 0] = start.x;
				staging[p0Offset + 1] = start.y;
				staging[p0Offset + 2] = start.z;
				staging[p0Offset + 3] = end.x;
				staging[p0Offset + 4] = end.y;
				staging[p0Offset + 5] = end.z;

				staging[p0Offset + 6] = scale.x;
				staging[p0Offset + 7] = scale.y;
				staging[p0Offset + 8] = scale.z;

				staging[floatOffset + FLOAT_OFFSET_HEALTH] = object.health;

				staging[floatOffset + FLOAT_OFFSET_COLOR] = 0;
				staging[floatOffset + FLOAT_OFFSET_TAIL] = TAIL_FLAG;

				offset += 1;
			}

		}

		buffer.commitStaging(base * FLOATS_PER_OBJECT, offset * FLOATS_PER_OBJECT);
	}


	private static void setAttribute(final ShaderProgram shader, final int location, final int size, final int type,
			final boolean normalize, final int stride, final int offset) {
		if (location >= 0) {
			shader.setVertexAttribute(location, size, type, normalize, stride, offset);
		}
	}

	public static void bindParticleEmitter2Shader(final ParticleEmitter2 emitter, final ShaderProgram shader,
			final EmitterLocations loc) {
		final MdxComplexInstance instance = emitter.instance;
		final Scene scene = instance.scene;
		final Camera camera = scene.camera;
		final ParticleEmitter2Object emitterObject = emitter.emitterObject;
		final MdxModel model = emitterObject.model;
		final ModelViewer viewer = model.viewer;
		final GL20 gl = viewer.gl;
		final float[][] colors = emitterObject.colors;
		final float[][] intervals = emitterObject.intervals;
		final long replaceableId = emitterObject.replaceableId;
		Vector3[] vectors;
		Texture texture;

		gl.glBlendFunc(emitterObject.blendSrc, emitterObject.blendDst);

		if ((replaceableId > 0) && (replaceableId < WarsmashConstants.REPLACEABLE_TEXTURE_LIMIT)
				&& (instance.replaceableTextures[(int) replaceableId] != null)) {
			texture = instance.replaceableTextures[(int) replaceableId];
		}
		else {
			texture = emitterObject.internalTexture;
		}

		viewer.webGL.bindTexture(texture, 0);

		// Choose between a default rectangle and a billboarded one
		if (emitterObject.xYQuad != 0) {
			vectors = camera.vectors;
		}
		else {
			vectors = camera.billboardedVectors;
		}

		shader.setUniformf(loc.emitter, EMITTER_PARTICLE2);

		shader.setUniformf(loc.filterMode, emitterObject.filterModeForShader);
		shader.setUniformf(loc.lifeSpan, emitterObject.lifeSpan);
		shader.setUniformf(loc.timeMiddle, emitterObject.timeMiddle);
		shader.setUniformf(loc.columns, emitterObject.columns);
		shader.setUniformf(loc.rows, emitterObject.rows);
		shader.setUniformf(loc.teamColored, emitterObject.teamColored);
		shader.setUniformi(loc.unshaded, emitterObject.emitterUsesMdlOrUnshaded != 0 ? 1 : 0);
		shader.setUniformi(loc.unfogged, emitterObject.unfogged != 0 ? 1 : 0);
		scene.fogSettings.uploadUniforms(shader, loc.fogColor, loc.fogParams, loc.fogHeight);

		final W3xSceneLightManager lightManager = (W3xSceneLightManager) scene.getLightManager();
		final DataTexture unitLightsTexture = lightManager.getUnitLightsTexture();

		unitLightsTexture.bind(14);
		shader.setUniformi(loc.lightTexture, 14);
		shader.setUniformf(loc.lightCount, lightManager.getUnitLightCount());
		shader.setUniformf(loc.lightTextureHeight, unitLightsTexture.getHeight());

		shader.setUniform3fv(loc.intervals[0], intervals[0], 0, 3);
		shader.setUniform3fv(loc.intervals[1], intervals[1], 0, 3);
		shader.setUniform3fv(loc.intervals[2], intervals[2], 0, 3);
		shader.setUniform3fv(loc.intervals[3], intervals[3], 0, 3);

		shader.setUniform4fv(loc.colors[0], colors[0], 0, 4);
		shader.setUniform4fv(loc.colors[1], colors[1], 0, 4);
		shader.setUniform4fv(loc.colors[2], colors[2], 0, 4);

		shader.setUniform3fv(loc.scaling, emitterObject.scaling, 0, 3);

		if (emitterObject.headOrTail.isIncludesHead()) {
			shader.setUniform3fv(loc.vertices[0], asFloatArray(vectors[0]), 0, 3);
			shader.setUniform3fv(loc.vertices[1], asFloatArray(vectors[1]), 0, 3);
			shader.setUniform3fv(loc.vertices[2], asFloatArray(vectors[2]), 0, 3);
			shader.setUniform3fv(loc.vertices[3], asFloatArray(vectors[3]), 0, 3);
		}

		shader.setUniform3fv(loc.cameraZ, asFloatArray(camera.billboardedVectors[6]), 0, 3);
	}

	public static void bindRibbonEmitterBuffer(final RibbonEmitter emitter, final ClientBuffer buffer, final int base) {
		Ribbon object = emitter.first;
		final ByteBuffer byteView = buffer.byteView;
		final float[] staging = buffer.staging;
		final RibbonEmitterObject emitterObject = emitter.emitterObject;
		final long columns = emitterObject.columns;
		final int alive = emitter.alive;
		final float chainLengthFactor = 1 / (float) (alive - 1);
		int offset = 0;

		while (object.next != null) {
			final float[] next = object.next.vertices;
			final int floatOffset = (base + offset) * FLOATS_PER_OBJECT;
			final int p0Offset = floatOffset + FLOAT_OFFSET_P0;
			final float[] vertices = object.vertices;

			staging[p0Offset + 0] = vertices[0];
			staging[p0Offset + 1] = vertices[1];
			staging[p0Offset + 2] = vertices[2];
			staging[p0Offset + 3] = vertices[3];
			staging[p0Offset + 4] = vertices[4];
			staging[p0Offset + 5] = vertices[5];
			staging[p0Offset + 6] = next[3];
			staging[p0Offset + 7] = next[4];
			staging[p0Offset + 8] = next[5];
			staging[p0Offset + 9] = next[0];
			staging[p0Offset + 10] = next[1];
			staging[p0Offset + 11] = next[2];
			staging[floatOffset + FLOAT_OFFSET_COLOR] = 0;
			staging[floatOffset + FLOAT_OFFSET_TAIL] = 0;

			object = object.next;
			offset += 1;
		}

		// The color and left/right/top bytes go in after the float copy, over the zeros it leaves in their slots.
		buffer.commitStaging(base * FLOATS_PER_OBJECT, offset * FLOATS_PER_OBJECT);
		offset = 0;
		object = emitter.first;
		while (object.next != null) {
			final int byteOffset = (base + offset) * BYTES_PER_OBJECT;
			final int colorOffset = byteOffset + BYTE_OFFSET_COLOR;
			final int leftRightTopOffset = byteOffset + BYTE_OFFSET_LEFT_RIGHT_TOP;
			final float left = ((object.slot % columns) + (1 - (offset * chainLengthFactor) - chainLengthFactor))
					/ columns;
			final float top = object.slot / (float) columns;
			final float right = left + chainLengthFactor;
			final byte[] color = object.color;

			byteView.put(colorOffset + 0, color[0]);
			byteView.put(colorOffset + 1, color[1]);
			byteView.put(colorOffset + 2, color[2]);
			byteView.put(colorOffset + 3, color[3]);

			byteView.put(leftRightTopOffset + 0, (byte) (left * 255));
			byteView.put(leftRightTopOffset + 1, (byte) (right * 255));
			byteView.put(leftRightTopOffset + 2, (byte) (top * 255));

			object = object.next;
			offset += 1;
		}
	}

	public static void bindRibbonEmitterShader(final RibbonEmitter emitter, final ShaderProgram shader,
			final EmitterLocations loc) {
		final TextureMapper textureMapper = emitter.instance.textureMapper;
		final RibbonEmitterObject emitterObject = emitter.emitterObject;
		final Layer layer = emitterObject.layer;
		final MdxModel model = emitterObject.model;
		final GL20 gl = model.viewer.gl;
		final Texture texture = model.getTextures().get(layer.textureId);

		layer.bind(shader, loc.filterMode);

		Texture mappedTexture = textureMapper.get(texture);
		if (mappedTexture == null) {
			mappedTexture = texture;
		}
		model.viewer.webGL.bindTexture(mappedTexture, 0);

		shader.setUniformf(loc.emitter, EMITTER_RIBBON);

		shader.setUniformf(loc.columns, emitterObject.columns);
		shader.setUniformf(loc.rows, emitterObject.rows);
	}

	public static void bindEventObjectSplEmitterBuffer(
			final EventObjectEmitter<EventObjectEmitterObject, EventObjectSpl> emitter, final ClientBuffer buffer,
			final int base) {
		final List<EventObjectSpl> objects = emitter.objects;
		final ByteBuffer byteView = buffer.byteView;
		final float[] staging = buffer.staging;
		int offset = 0;

		for (final EventObjectSpl object : objects) {
			final int floatOffset = (base + offset) * FLOATS_PER_OBJECT;

			// Once one object does not fit, none of the later ones do either.
			if (staging.length < (floatOffset + FLOATS_PER_OBJECT)) {
				Log.warn("Staging has a length of " + staging.length + " which is not large enough. Aborting");
				break;
			}
			System.arraycopy(object.vertices, 0, staging, floatOffset + FLOAT_OFFSET_P0, 12);

			staging[floatOffset + FLOAT_OFFSET_HEALTH] = object.health;
			staging[floatOffset + FLOAT_OFFSET_COLOR] = 0;
			staging[floatOffset + FLOAT_OFFSET_TAIL] = 0;

			offset += 1;
		}

		buffer.commitStaging(base * FLOATS_PER_OBJECT, offset * FLOATS_PER_OBJECT);

		// The normal bytes go in after the float copy, over the zeros it leaves in their slots.
		for (int i = 0; i < offset; i++) {
			final IVec3 normal = objects.get(i).normal;
			final int leftRightTopOffset = ((base + i) * BYTES_PER_OBJECT) + BYTE_OFFSET_LEFT_RIGHT_TOP;
			byteView.put(leftRightTopOffset + 0, (byte) (normal.x));
			byteView.put(leftRightTopOffset + 1, (byte) (normal.y));
			byteView.put(leftRightTopOffset + 2, (byte) (normal.z));
		}
	}

	public static void bindEventObjectSplEmitterShader(final EventObjectSplEmitter emitter,
			final ShaderProgram shader, final EmitterLocations loc) {
		final MdxComplexInstance instance = emitter.instance;
		final Scene scene = instance.scene;
		final TextureMapper textureMapper = instance.textureMapper;
		final EventObjectEmitterObject emitterObject = emitter.emitterObject;
		final float[] intervalTimes = emitterObject.intervalTimes;
		final float[][] intervals = emitterObject.intervals;
		final float[][] colors = emitterObject.colors;
		final MdxModel model = emitterObject.model;
		final GL20 gl = model.viewer.gl;
		final Texture texture = emitterObject.internalTexture;

		gl.glBlendFunc(emitterObject.blendSrc, emitterObject.blendDst);

		Texture finalTexture = textureMapper.get(texture);
		if (finalTexture == null) {
			finalTexture = texture;
		}
		model.viewer.webGL.bindTexture(finalTexture, 0);

		shader.setUniformf(loc.lifeSpan, emitterObject.lifeSpan);
		shader.setUniformf(loc.columns, emitterObject.columns);
		shader.setUniformf(loc.rows, emitterObject.rows);
		shader.setUniformi(loc.unshaded, emitterObject.emitterUsesMdlOrUnshaded != 0 ? 1 : 0);

		final W3xSceneLightManager lightManager = (W3xSceneLightManager) scene.getLightManager();
		final DataTexture unitLightsTexture = lightManager.getUnitLightsTexture();

		unitLightsTexture.bind(14);
		shader.setUniformi(loc.lightTexture, 14);
		shader.setUniformf(loc.lightCount, lightManager.getUnitLightCount());
		shader.setUniformf(loc.lightTextureHeight, unitLightsTexture.getHeight());

		// 3 because the uniform is shared with UBR, which has 3 values.
		vectorTemp[0] = intervalTimes[0];
		vectorTemp[1] = intervalTimes[1];
		vectorTemp[2] = 0;
		shader.setUniform3fv(loc.intervalTimes, vectorTemp, 0, 3);

		shader.setUniform3fv(loc.intervals[0], intervals[0], 0, 3);
		shader.setUniform3fv(loc.intervals[1], intervals[1], 0, 3);

		shader.setUniform4fv(loc.colors[0], colors[0], 0, 4);
		shader.setUniform4fv(loc.colors[1], colors[1], 0, 4);
		shader.setUniform4fv(loc.colors[2], colors[2], 0, 4);
	}

	public static void bindEventObjectUbrEmitterBuffer(
			final EventObjectEmitter<EventObjectEmitterObject, EventObjectUbr> emitter, final ClientBuffer buffer,
			final int base) {
		final List<EventObjectUbr> objects = emitter.objects;
		final float[] staging = buffer.staging;
		int offset = 0;

		for (final EventObjectUbr object : objects) {
			final int floatOffset = (base + offset) * FLOATS_PER_OBJECT;

			System.arraycopy(object.vertices, 0, staging, floatOffset + FLOAT_OFFSET_P0, 12);

			staging[floatOffset + FLOAT_OFFSET_HEALTH] = object.health;
			staging[floatOffset + FLOAT_OFFSET_COLOR] = 0;
			staging[floatOffset + FLOAT_OFFSET_TAIL] = 0;

			offset += 1;
		}

		buffer.commitStaging(base * FLOATS_PER_OBJECT, offset * FLOATS_PER_OBJECT);
	}

	public static void bindEventObjectUbrEmitterShader(final EventObjectUbrEmitter emitter,
			final ShaderProgram shader, final EmitterLocations loc) {
		final TextureMapper textureMapper = emitter.instance.textureMapper;
		final EventObjectEmitterObject emitterObject = emitter.emitterObject;
		final float[] intervalTimes = emitterObject.intervalTimes;
		final float[][] colors = emitterObject.colors;
		final MdxModel model = emitterObject.model;
		final GL20 gl = model.viewer.gl;
		final Texture texture = emitterObject.internalTexture;

		gl.glBlendFunc(emitterObject.blendSrc, emitterObject.blendDst);

		Texture finalTexture = textureMapper.get(texture);
		if (finalTexture == null) {
			finalTexture = texture;
		}
		model.viewer.webGL.bindTexture(finalTexture, 0);

		shader.setUniformf(loc.lifeSpan, emitterObject.lifeSpan);
		shader.setUniformf(loc.columns, emitterObject.columns);
		shader.setUniformf(loc.rows, emitterObject.rows);

		shader.setUniform3fv(loc.intervalTimes, intervalTimes, 0, 3);

		shader.setUniform4fv(loc.colors[0], colors[0], 0, 4);
		shader.setUniform4fv(loc.colors[1], colors[1], 0, 4);
		shader.setUniform4fv(loc.colors[2], colors[2], 0, 4);
	}

	/** How many instances the emitter draws this frame; 0 when it draws nothing. */
	private static int drawCount(final MdxEmitter<?, ?, ?> emitter) {
		int alive = emitter.alive;
		final EmitterObject emitterObject = emitter.emitterObject;
		final int emitterType = emitterObject.getGeometryEmitterType();

		if (emitterType == EMITTER_PARTICLE2) {
			final ParticleEmitter2Object emitterObject2 = ((ParticleEmitter2) emitter).emitterObject;
			// The HD models draw their team glow as particles of the TeamGlow replaceable texture.
			if (((ParticleEmitter2) emitter).instance.teamGlowHidden && (emitterObject2.replaceableId == 2)) {
				return 0;
			}
			if (emitterObject2.headOrTail.isIncludesHead() && emitterObject2.headOrTail.isIncludesTail()) {
				alive *= 2;
			}
		}
		else if (emitterType == EMITTER_RIBBON) {
			alive -= 1;
		}
		else if (emitterType == EMITTER_SPN) {
			return 0;
		}
		return Math.max(alive, 0);
	}

	/**
	 * Draws the emitters of one model instance. Their instance data goes into the shared buffer one emitter after the
	 * other and is uploaded once, instead of giving the buffer fresh storage for every emitter.
	 */
	public static void renderEmitters(final SkeletalNode[] nodes, final int[] emitterNodes, final ShaderProgram shader,
			final EmitterLocations loc) {
		final int count = emitterNodes.length;
		if (drawCounts.length < count) {
			drawCounts = new int[count];
		}
		final int[] counts = drawCounts;
		int total = 0;
		ModelViewer viewer = null;

		for (int i = 0; i < count; i++) {
			final MdxEmitter<?, ?, ?> emitter = (MdxEmitter<?, ?, ?>) nodes[emitterNodes[i]].object;
			final int alive = drawCount(emitter);
			counts[i] = alive;
			if (alive > 0) {
				total += alive;
				viewer = emitter.instance.model.viewer;
			}
		}
		if (total == 0) {
			return;
		}

		final ANGLEInstancedArrays instancedArrays = viewer.webGL.instancedArrays;
		final ClientBuffer buffer = viewer.buffer;

		buffer.reserve(total * BYTES_PER_OBJECT);

		int base = 0;
		for (int i = 0; i < count; i++) {
			if (counts[i] > 0) {
				final MdxEmitter<?, ?, ?> emitter = (MdxEmitter<?, ?, ?>) nodes[emitterNodes[i]].object;
				switch (emitter.emitterObject.getGeometryEmitterType()) {
				case EMITTER_PARTICLE2:
					bindParticleEmitter2Buffer((ParticleEmitter2) emitter, buffer, base);
					break;
				case EMITTER_RIBBON:
					bindRibbonEmitterBuffer((RibbonEmitter) emitter, buffer, base);
					break;
				case EMITTER_SPLAT:
					bindEventObjectSplEmitterBuffer((EventObjectSplEmitter) emitter, buffer, base);
					break;
				default:
					bindEventObjectUbrEmitterBuffer((EventObjectUbrEmitter) emitter, buffer, base);
					break;
				}
				base += counts[i];
			}
		}

		buffer.bindAndReplace(total * BYTES_PER_OBJECT);

		base = 0;
		for (int i = 0; i < count; i++) {
			final int alive = counts[i];
			if (alive > 0) {
				final MdxEmitter<?, ?, ?> emitter = (MdxEmitter<?, ?, ?>) nodes[emitterNodes[i]].object;
				final int emitterType = emitter.emitterObject.getGeometryEmitterType();
				final int byteBase = base * BYTES_PER_OBJECT;

				switch (emitterType) {
				case EMITTER_PARTICLE2:
					bindParticleEmitter2Shader((ParticleEmitter2) emitter, shader, loc);
					break;
				case EMITTER_RIBBON:
					bindRibbonEmitterShader((RibbonEmitter) emitter, shader, loc);
					break;
				case EMITTER_SPLAT:
					bindEventObjectSplEmitterShader((EventObjectSplEmitter) emitter, shader, loc);
					break;
				default:
					bindEventObjectUbrEmitterShader((EventObjectUbrEmitter) emitter, shader, loc);
					break;
				}

				// Binding the textures and layers above may have changed the bound array buffer.
				buffer.bind();

				shader.setUniformf(loc.emitter, emitterType);

				setAttribute(shader, loc.p0, 3, GL20.GL_FLOAT, false, BYTES_PER_OBJECT, byteBase + BYTE_OFFSET_P0);
				setAttribute(shader, loc.p1, 3, GL20.GL_FLOAT, false, BYTES_PER_OBJECT, byteBase + BYTE_OFFSET_P1);
				setAttribute(shader, loc.p2, 3, GL20.GL_FLOAT, false, BYTES_PER_OBJECT, byteBase + BYTE_OFFSET_P2);
				setAttribute(shader, loc.p3, 3, GL20.GL_FLOAT, false, BYTES_PER_OBJECT, byteBase + BYTE_OFFSET_P3);
				setAttribute(shader, loc.health, 1, GL20.GL_FLOAT, false, BYTES_PER_OBJECT,
						byteBase + BYTE_OFFSET_HEALTH);
				setAttribute(shader, loc.color, 4, GL20.GL_UNSIGNED_BYTE, true, BYTES_PER_OBJECT,
						byteBase + BYTE_OFFSET_COLOR);
				setAttribute(shader, loc.tail, 1, GL20.GL_UNSIGNED_BYTE, false, BYTES_PER_OBJECT,
						byteBase + BYTE_OFFSET_TAIL);
				setAttribute(shader, loc.leftRightTop, 3, GL20.GL_UNSIGNED_BYTE, false, BYTES_PER_OBJECT,
						byteBase + BYTE_OFFSET_LEFT_RIGHT_TOP);

				PerfStats.draw();
				instancedArrays.glDrawArraysInstancedANGLE(GL20.GL_TRIANGLES, 0, 6, alive);
				base += alive;
			}
		}
	}

	private static final float[] asFloatArray(final Vector3 vec) {
		vectorTemp[0] = vec.x;
		vectorTemp[1] = vec.y;
		vectorTemp[2] = vec.z;
		return vectorTemp;
	}

}
