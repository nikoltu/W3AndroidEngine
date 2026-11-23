package com.etheller.warsmash.viewer5.handlers.mdx;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.viewer5.Shaders;
import com.etheller.warsmash.viewer5.gl.DataTexture;
import com.etheller.warsmash.viewer5.gl.WebGL;

/**
 * Draws the shape of a model flattened onto the ground along the light, for the ground shadow mask: every skinned
 * vertex is slid along the light's direction until it meets the terrain's height map, so the shadow bends over slopes
 * and steps and is cut short by a rise, as it would be in a shadow map.
 */
public final class MdxShadowCaster {
	/** How far below the foot of a unit the terrain is looked for along the light, and in how many steps. */
	private static final int SHADOW_DEPTH = 256;
	private static final int MARCH_STEPS = 6;
	/** In place of the steps: the ground is level, the shadow lands on the plane of the ground under the caster. */
	private static final int FLAT = -1;
	/** What of a shape is below the ground by more than this is under it, and casts no shadow. */
	private static final int BURIED = 4;
	private static final int HEIGHT_UNIT = 5;
	private static final int BONE_UNIT = 15;

	private static final String VERTEX_COMMON = "    uniform mat4 u_vp;\r\n" + //
			"    uniform vec3 u_light;\r\n" + //
			"    uniform float u_groundZ;\r\n" + //
			"    uniform sampler2D u_heights;\r\n" + //
			"    uniform vec2 u_heightPixel;\r\n" + //
			"    uniform vec2 u_heightOrigin;\r\n" + //
			"    attribute vec3 a_position;\r\n" + //
			"    attribute vec2 a_uv;\r\n" + //
			"    varying vec2 v_uv;\r\n" + //
			"    varying float v_height;\r\n" + //
			"    float groundAt(vec2 xy) {\r\n" + //
			"      return texture2D(u_heights, (xy - u_heightOrigin) * (u_heightPixel / 128.0) + u_heightPixel * 0.5).r * 128.0;\r\n"
			+ //
			"    }\r\n" + //
			Shaders.boneTexture + "\r\n";

	private static String flatten(final int steps) {
		if (steps == FLAT) {
			return "      v_height = position.z - u_groundZ;\r\n" + //
					"      position.xy -= u_light.xy * (max(position.z - u_groundZ, 0.0) / u_light.z);\r\n" + //
					"      position.z = u_groundZ;\r\n" + //
					"      v_uv = a_uv;\r\n" + //
					"      gl_Position = u_vp * vec4(position, 1.0);\r\n" + //
					"    }";
		}
		return ("      float span = (position.z - u_groundZ + " + SHADOW_DEPTH + ".0) / u_light.z;\r\n" + //
				"      float stride = span / STEPS.0;\r\n" + //
				"      float previous = position.z - groundAt(position.xy);\r\n" + //
				"      v_height = previous;\r\n" + //
				"      float hit = previous <= 0.0 ? 0.0 : span;\r\n" + //
				"      if (previous > 0.0) {\r\n" + //
				"        for (int i = 1; i <= STEPS; i++) {\r\n" + //
				"          vec3 q = position - u_light * (stride * float(i));\r\n" + //
				"          float above = q.z - groundAt(q.xy);\r\n" + //
				"          if (above <= 0.0) {\r\n" + //
				"            hit = stride * (float(i) - 1.0 + previous / (previous - above));\r\n" + //
				"            break;\r\n" + //
				"          }\r\n" + //
				"          previous = above;\r\n" + //
				"        }\r\n" + //
				"      }\r\n" + //
				"      position.xy -= u_light.xy * hit;\r\n" + //
				"      position.z = groundAt(position.xy);\r\n" + //
				"      v_uv = a_uv;\r\n" + //
				"      gl_Position = u_vp * vec4(position, 1.0);\r\n" + //
				"    }").replace("STEPS", Integer.toString(steps));
	}

	private static String vertexGroups(final int steps) {
		return VERTEX_COMMON + //
				"    attribute vec4 a_bones;\r\n" + //
				"    #ifdef EXTENDED_BONES\r\n" + //
				"    attribute vec4 a_extendedBones;\r\n" + //
				"    #endif\r\n" + //
				"    attribute float a_boneNumber;\r\n" + //
				"    void main() {\r\n" + //
				"      vec3 position = vec3(0.0);\r\n" + //
				"      if (a_boneNumber > 0.0) {\r\n" + //
				"        vec4 position4 = vec4(a_position, 1.0);\r\n" + //
				"        vec4 p = vec4(0.0);\r\n" + //
				"        for (int i = 0; i < 4; i++) {\r\n" + //
				"          if (a_bones[i] > 0.0) {\r\n" + //
				"            p += fetchMatrix(a_bones[i] - 1.0, 0.0) * position4;\r\n" + //
				"          }\r\n" + //
				"        }\r\n" + //
				"        #ifdef EXTENDED_BONES\r\n" + //
				"        for (int i = 0; i < 4; i++) {\r\n" + //
				"          if (a_extendedBones[i] > 0.0) {\r\n" + //
				"            p += fetchMatrix(a_extendedBones[i] - 1.0, 0.0) * position4;\r\n" + //
				"          }\r\n" + //
				"        }\r\n" + //
				"        #endif\r\n" + //
				"        position = p.xyz / a_boneNumber;\r\n" + //
				"      }\r\n" + //
				flatten(steps);
	}

	/** The skin of the HD models of the game: four bones, each with a weight. */
	private static String vertexSkin(final int steps) {
		return VERTEX_COMMON + //
				"    attribute vec4 a_bones;\r\n" + //
				"    attribute vec4 a_weights;\r\n" + //
				"    void main() {\r\n" + //
				"      mat4 bone = mat4(0.0);\r\n" + //
				"      bone += fetchMatrix(a_bones[0], 0.0) * a_weights[0];\r\n" + //
				"      bone += fetchMatrix(a_bones[1], 0.0) * a_weights[1];\r\n" + //
				"      bone += fetchMatrix(a_bones[2], 0.0) * a_weights[2];\r\n" + //
				"      bone += fetchMatrix(a_bones[3], 0.0) * a_weights[3];\r\n" + //
				"      vec3 position = vec3(bone * vec4(a_position, 1.0));\r\n" + //
				flatten(steps);
	}

	/** The coarse geometry of {@link ShadowMesh}, every vertex of which follows one bone. */
	private static String vertexRigid(final int steps) {
		return VERTEX_COMMON + //
				"    attribute vec4 a_bones;\r\n" + //
				"    void main() {\r\n" + //
				"      vec3 position = vec3(fetchMatrix(a_bones[0], 0.0) * vec4(a_position, 1.0));\r\n" + //
				flatten(steps);
	}

	private static final String FRAGMENT = "    uniform sampler2D u_texture;\r\n" + //
			"    uniform bool u_cutout;\r\n" + //
			"    varying vec2 v_uv;\r\n" + //
			"    varying float v_height;\r\n" + //
			"    void main() {\r\n" + //
			"      if (v_height < -" + BURIED + ".0) {\r\n" + //
			"        discard;\r\n" + //
			"      }\r\n" + //
			"      if (u_cutout && (texture2D(u_texture, v_uv).a < 0.6)) {\r\n" + //
			"        discard;\r\n" + //
			"      }\r\n" + //
			"      gl_FragColor = vec4(1.0);\r\n" + //
			"    }";

	/**
	 * What is known to be set in one of the programs, so that the many casters of a frame, which mostly differ in
	 * their place and bones, do not set it all again: every GL call is costly on a phone's driver.
	 */
	static final class ProgramState {
		private ShaderProgram program;
		private int frame = -1;
		private float lightX = Float.NaN;
		private float lightY;
		private float lightZ;
		private float groundZ = Float.NaN;
		private int cutout = -1;
		private DataTexture boneTexture;
		private int boneWidth;
		private int boneHeight;
		private float boneBase = Float.NaN;
		ShadowMesh mesh;

		void setCutout(final ShaderProgram shader, final boolean value) {
			final int next = value ? 1 : 0;
			if (this.cutout != next) {
				this.cutout = next;
				shader.setUniformi("u_cutout", next);
			}
		}

		private void forget() {
			this.lightX = Float.NaN;
			this.groundZ = Float.NaN;
			this.cutout = -1;
			this.boneTexture = null;
			this.boneBase = Float.NaN;
			this.mesh = null;
		}
	}

	private final ProgramState groups = new ProgramState();
	private final ProgramState extended = new ProgramState();
	private final ProgramState skin = new ProgramState();
	private final ProgramState skinMesh = new ProgramState();
	private final ProgramState flatGroups = new ProgramState();
	private final ProgramState flatExtended = new ProgramState();
	private final ProgramState flatSkin = new ProgramState();
	private final ProgramState flatSkinMesh = new ProgramState();
	private int frame;
	private DataTexture boundBoneTexture;
	private final float[] heightRow = new float[4];

	/**
	 * Takes the layout of the terrain's height texture, a corner per texel, that the shadows land on; the texture is
	 * bound to {@link #heightUnit()} by the caller.
	 *
	 * @param columns the width of the height texture
	 * @param rows    its height
	 * @param originX the world x of its first corner
	 * @param originY the world y of its first corner
	 */
	public void setTerrain(final int columns, final int rows, final float originX, final float originY) {
		this.heightRow[0] = 1f / columns;
		this.heightRow[1] = 1f / rows;
		this.heightRow[2] = originX;
		this.heightRow[3] = originY;
	}

	/** Forgets what the programs were left with: the frame's matrix and light are set again. */
	public void beginFrame() {
		this.frame++;
		this.boundBoneTexture = null;
	}

	public static int heightUnit() {
		return HEIGHT_UNIT;
	}

	/**
	 * @param light   the direction from the ground towards the light, with a positive z: the shadow slides by its x
	 *                and y for every unit of height it falls by its z
	 * @param groundZ the height of the ground under the unit
	 * @param flat    whether the ground the shadow lands on is level, so that it is put on the plane of the ground
	 *                under the unit without following the light over the height map
	 * @param scenery whether this is a tree or other scenery, the small cut-out parts of which keep their alpha test
	 */
	public void draw(final WebGL webGL, final MdxComplexInstance instance, final Matrix4 viewProjection,
			final Vector3 light, final float groundZ, final boolean flat, final boolean scenery) {
		prepare(webGL);
		final MdxModel model = (MdxModel) instance.model;
		drawGroups(webGL, instance, model.opaqueGroups, viewProjection, light, groundZ, flat, scenery);
		drawGroups(webGL, instance, model.translucentGroups, viewProjection, light, groundZ, flat, scenery);
	}

	/** Compiles the programs, which on a phone's driver takes long enough to be seen in the first frame that draws. */
	public void prepare(final WebGL webGL) {
		if (this.groups.program == null) {
			this.groups.program = webGL.createShaderProgram(vertexGroups(MARCH_STEPS), FRAGMENT);
			this.extended.program = webGL.createShaderProgram("#define EXTENDED_BONES\r\n" + vertexGroups(MARCH_STEPS),
					FRAGMENT);
			this.skin.program = webGL.createShaderProgram(vertexSkin(MARCH_STEPS), FRAGMENT);
			this.skinMesh.program = webGL.createShaderProgram(vertexRigid(MARCH_STEPS), FRAGMENT);
			this.flatGroups.program = webGL.createShaderProgram(vertexGroups(FLAT), FRAGMENT);
			this.flatExtended.program = webGL.createShaderProgram("#define EXTENDED_BONES\r\n" + vertexGroups(FLAT),
					FRAGMENT);
			this.flatSkin.program = webGL.createShaderProgram(vertexSkin(FLAT), FRAGMENT);
			this.flatSkinMesh.program = webGL.createShaderProgram(vertexRigid(FLAT), FRAGMENT);
		}
	}

	private void drawGroups(final WebGL webGL, final MdxComplexInstance instance,
			final java.util.List<GenericGroup> groups, final Matrix4 viewProjection, final Vector3 light,
			final float groundZ, final boolean flat, final boolean scenery) {
		for (int i = 0, n = groups.size(); i < n; i++) {
			final GenericGroup group = groups.get(i);
			if (!(group instanceof BatchGroup)) {
				continue;
			}
			final BatchGroup batchGroup = (BatchGroup) group;
			final MdxModel model = (MdxModel) instance.model;
			final ProgramState state;
			ShadowMesh mesh = null;
			if (batchGroup.hd) {
				mesh = model.shadowMesh(scenery);
				state = mesh != null ? (flat ? this.flatSkinMesh : this.skinMesh) : (flat ? this.flatSkin : this.skin);
			}
			else if (batchGroup.skinningType == SkinningType.ExtendedVertexGroups) {
				state = flat ? this.flatExtended : this.extended;
			}
			else {
				state = flat ? this.flatGroups : this.groups;
			}
			final ShaderProgram shader = state.program;
			webGL.useShaderProgram(shader);
			if (state.frame != this.frame) {
				state.frame = this.frame;
				state.forget();
				shader.setUniformMatrix("u_vp", viewProjection);
				shader.setUniformi("u_heights", HEIGHT_UNIT);
				shader.setUniformf("u_heightPixel", this.heightRow[0], this.heightRow[1]);
				shader.setUniformf("u_heightOrigin", this.heightRow[2], this.heightRow[3]);
				shader.setUniformi("u_texture", 0);
			}
			if ((light.x != state.lightX) || (light.y != state.lightY) || (light.z != state.lightZ)) {
				state.lightX = light.x;
				state.lightY = light.y;
				state.lightZ = light.z;
				shader.setUniformf("u_light", light.x, light.y, light.z);
			}
			if (groundZ != state.groundZ) {
				state.groundZ = groundZ;
				shader.setUniformf("u_groundZ", groundZ);
			}
			if (mesh == null) {
				batchGroup.renderShadow(instance, shader);
				state.forget();
				this.boundBoneTexture = null;
				continue;
			}
			final DataTexture atlas = instance.packedBoneTexture();
			if (atlas != null) {
				if (atlas != this.boundBoneTexture) {
					atlas.bind(BONE_UNIT);
					this.boundBoneTexture = atlas;
				}
				if ((atlas != state.boneTexture) || (atlas.getWidth() != state.boneWidth)
						|| (atlas.getHeight() != state.boneHeight)) {
					state.boneTexture = atlas;
					state.boneWidth = atlas.getWidth();
					state.boneHeight = atlas.getHeight();
					shader.setUniformi("u_boneMap", BONE_UNIT);
					shader.setUniformf("u_vectorSize", 1f / state.boneWidth);
					shader.setUniformf("u_rowSize", 1f / state.boneHeight);
				}
				final float base = instance.packedBoneBase();
				if (base != state.boneBase) {
					state.boneBase = base;
					shader.setUniformf("u_boneBase", base);
				}
			}
			else {
				if (!instance.bindBones(shader)) {
					continue;
				}
				state.boneTexture = null;
				state.boneBase = Float.NaN;
				this.boundBoneTexture = null;
			}
			batchGroup.renderShadowMesh(instance, shader, mesh, state);
		}
	}

	public static void prepareState(final GL20 gl) {
		gl.glDisable(GL20.GL_BLEND);
		gl.glDisable(GL20.GL_CULL_FACE);
		gl.glDisable(GL20.GL_DEPTH_TEST);
		gl.glDepthMask(false);
	}
}
