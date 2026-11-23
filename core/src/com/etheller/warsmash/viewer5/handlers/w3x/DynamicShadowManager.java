package com.etheller.warsmash.viewer5.handlers.w3x;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.util.RenderMathUtils;
import com.etheller.warsmash.viewer5.ModelInstance;
import com.etheller.warsmash.viewer5.RenderScaler;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.gl.WebGL;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxShadowCaster;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.Terrain;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.GameOptions;

public class DynamicShadowManager {
	public static boolean IS_SHADOW_MAPPING = false;

	private final Vector3 shadowVector = new Vector3();
	private final Matrix4 depthProjectionMatrix = new Matrix4();
	private final Matrix4 depthViewMatrix = new Matrix4();
	private final Matrix4 depthModelMatrix = new Matrix4();
	private final Matrix4 depthMVP = new Matrix4();
	private final Matrix4 biasMatrix = new Matrix4();
	private final Matrix4 depthBiasMVP = new Matrix4();
	/** The GL of the context the shadow map was made in, which leaves deletes alone once that context is lost. */
	private GL30 gl;

	/** The light's direction when the map has no day and night light: a shadow is 1.14 times as long as its caster is high. */
	private static final float DEFAULT_LIGHT_X = -1.05f;
	private static final float DEFAULT_LIGHT_Y = -0.45f;
	/** A shadow is never longer than this many times the height of what casts it, however low the light is. */
	private static final float MAX_SHADOW_LENGTH = 3.5f;
	/** Reforged keeps the same shadow length at every hour of the day; above 1 lengthens the moonlight shadows. */
	private static final float NIGHT_LENGTH = Float.parseFloat(System.getProperty("warsmash.shadowNightLength", "2.5"));
	/** Without a light of their own, trees and other scenery throw shadows this part as long as units do. */
	private static final float SCENERY_LENGTH = 0.6f;
	private static final float DAY_STRENGTH = 0.6f;
	private static final float NIGHT_STRENGTH = 0.5f;
	/** The most units and buildings that are drawn in a frame, the first in the scene order; each costs a draw. */
	private static final int MAX_CASTERS = 160;
	/** The most trees and other scenery drawn in a frame, after the units, so that they never take their place. */
	private static final int MAX_SCENERY = 96;
	/** Scenery lower than this is too small to throw a shadow that is seen. */
	private static final float SCENERY_MIN_HEIGHT = 96f;
	/** How high a shadow caster is taken to be when seeing whether its shadow is in view. */
	private static final float CULL_HEIGHT = 192f;
	private static final float CULL_EDGE = 1.15f;
	/** The mask is drawn at the size of the view, so that the edges of a shadow stay sharp. */
	private static final float MASK_SCALE = 1f;
	/** How far, in mask pixels, the four taps of the composite lie from a pixel's centre: each is a bilinear mix of four. */
	private static final float EDGE_SOFTNESS = 0.5f;
	/** A unit this far above the ground is in the air: its shadow lies straight below it. */
	private static final float AIRBORNE_HEIGHT = 48f;
	/** How far along the light, as the height of a tall shadow caster, and how far to the sides the ground is tried. */
	private static final float FLAT_REACH = 160f;
	private static final float FLAT_SIDE = 48f;
	private static final float FLAT_TOLERANCE = 1.5f;
	/** Pairs of the part of the reach and of the side distance, in units of FLAT_SIDE, of the places that are tried. */
	private static final float[] FLAT_PROBES = { 0.5f, 0f, 1f, 0f, 0f, 1f, 0f, -1f, 0.5f, 1f, 0.5f, -1f };

	private static final String COMPOSITE_VERTEX = "    attribute vec2 a_position;\r\n" + //
			"    varying vec2 v_uv;\r\n" + //
			"    void main() {\r\n" + //
			"      v_uv = a_position * 0.5 + 0.5;\r\n" + //
			"      gl_Position = vec4(a_position, 0.0, 1.0);\r\n" + //
			"    }";

	private static final String COMPOSITE_FRAGMENT = "    uniform sampler2D u_mask;\r\n" + //
			"    uniform vec2 u_texel;\r\n" + //
			"    uniform float u_strength;\r\n" + //
			"    varying vec2 v_uv;\r\n" + //
			"    void main() {\r\n" + //
			"      float m = texture2D(u_mask, v_uv + u_texel).r + texture2D(u_mask, v_uv - u_texel).r\r\n" + //
			"          + texture2D(u_mask, v_uv + vec2(u_texel.x, -u_texel.y)).r\r\n" + //
			"          + texture2D(u_mask, v_uv + vec2(-u_texel.x, u_texel.y)).r;\r\n" + //
			"      gl_FragColor = vec4(0.0, 0.0, 0.0, m * 0.25 * u_strength);\r\n" + //
			"    }";

	private final Vector3 sunDirection = new Vector3(DEFAULT_LIGHT_X, DEFAULT_LIGHT_Y, 1f).nor();
	private final Vector3 sceneryDirection = new Vector3();
	private boolean ownSceneryDirection;
	private final Vector3 shadowLight = new Vector3();
	private final Vector3 sceneryLight = new Vector3();
	private final Vector3 straightDown = new Vector3(0f, 0f, 1f);
	private final Vector3 viewHeap = new Vector3();
	private final Vector3 viewHeap2 = new Vector3();
	private float night;
	private float strength = DAY_STRENGTH;
	private final MdxShadowCaster caster = new MdxShadowCaster();
	private final List<MdxComplexInstance> casters = new ArrayList<>();
	private final List<MdxComplexInstance> sceneryCasters = new ArrayList<>();
	private final List<MdxComplexInstance> selected = new ArrayList<>();
	private static final Comparator<MdxComplexInstance> BY_MODEL = (a, b) -> Integer
			.compare(System.identityHashCode(a.model), System.identityHashCode(b.model));
	private boolean modelShadows;
	private boolean sceneShadows;
	private int maskFramebuffer;
	private int maskTexture;
	private int maskWidth;
	private int maskHeight;
	private int quadBuffer;
	private ShaderProgram compositeShader;

	private void disposeMask() {
		final GL30 gl = Gdx.gl30;
		if ((gl == null) || (this.maskFramebuffer == 0)) {
			return;
		}
		gl.glDeleteFramebuffer(this.maskFramebuffer);
		gl.glDeleteTexture(this.maskTexture);
		this.maskFramebuffer = 0;
		this.maskTexture = 0;
		this.maskWidth = 0;
		this.maskHeight = 0;
	}

	/**
	 * Takes the direction of the shadows from the main light of the day and night cycle and their length and strength
	 * from the time of day.
	 *
	 * @param towardLight   the unit vector from the ground towards the cycle's main light for units and buildings, or
	 *                      null for the default
	 * @param towardScenery the same for trees and other scenery when the cycle lights them from a light of their own,
	 *                      or null to throw their shadows along the unit light, shortened
	 * @param dayRatio      how far the day is, 0 to 1, with the middle at noon
	 */
	public void setSunLight(final Vector3 towardLight, final Vector3 towardScenery, final float dayRatio) {
		if (towardLight != null) {
			this.sunDirection.set(towardLight);
		}
		this.ownSceneryDirection = towardScenery != null;
		if (this.ownSceneryDirection) {
			this.sceneryDirection.set(towardScenery);
		}
		final float hour = (dayRatio - (float) Math.floor(dayRatio)) * 24f;
		this.night = hour < 12f ? 1f - smoothStep(5f, 7f, hour) : smoothStep(17f, 19f, hour);
		this.strength = DAY_STRENGTH + ((NIGHT_STRENGTH - DAY_STRENGTH) * this.night);
	}

	/** 1 at night, 0 by day, following the time of day last passed to setSunLight. */
	float getNight() {
		return this.night;
	}

	private static float smoothStep(final float from, final float to, final float value) {
		final float t = Math.max(0f, Math.min(1f, (value - from) / (to - from)));
		return t * t * (3f - (2f * t));
	}

	/** Sets the shadow light to the light direction with its elevation lowered for the night and kept above a limit. */
	private void prepareShadowLight() {
		final float length = 1f + ((NIGHT_LENGTH - 1f) * this.night);
		final float x = this.sunDirection.x;
		final float y = this.sunDirection.y;
		final float horizontal = (float) Math.sqrt((x * x) + (y * y));
		final float z = Math.max(Math.max(this.sunDirection.z, 0.01f) / length, horizontal / MAX_SHADOW_LENGTH);
		this.shadowLight.set(x, y, z);
		if (this.ownSceneryDirection) {
			final float sx = this.sceneryDirection.x;
			final float sy = this.sceneryDirection.y;
			final float sHorizontal = (float) Math.sqrt((sx * sx) + (sy * sy));
			this.sceneryLight.set(sx, sy, Math.max(Math.max(this.sceneryDirection.z, 0.01f) / length,
					sHorizontal / MAX_SHADOW_LENGTH));
		}
		else {
			this.sceneryLight.set(x, y, Math.max(z / SCENERY_LENGTH, horizontal / MAX_SHADOW_LENGTH));
		}
	}

	/** Whether the shadow of something at this place, from its foot to the end of its length, could reach the view. */
	private boolean shadowInView(final Matrix4 viewProjection, final float x, final float y, final float z) {
		final float tipX = x - ((this.shadowLight.x / this.shadowLight.z) * CULL_HEIGHT);
		final float tipY = y - ((this.shadowLight.y / this.shadowLight.z) * CULL_HEIGHT);
		final float[] m = viewProjection.val;
		final float footW = (m[Matrix4.M30] * x) + (m[Matrix4.M31] * y) + (m[Matrix4.M32] * z) + m[Matrix4.M33];
		final float tipW = (m[Matrix4.M30] * tipX) + (m[Matrix4.M31] * tipY) + (m[Matrix4.M32] * z) + m[Matrix4.M33];
		if ((footW <= 0f) || (tipW <= 0f)) {
			return true;
		}
		final Vector3 foot = this.viewHeap.set(x, y, z).prj(viewProjection);
		final Vector3 tip = this.viewHeap2.set(tipX, tipY, z).prj(viewProjection);
		return !(((foot.x < -CULL_EDGE) && (tip.x < -CULL_EDGE)) || ((foot.x > CULL_EDGE) && (tip.x > CULL_EDGE))
				|| ((foot.y < -CULL_EDGE) && (tip.y < -CULL_EDGE)) || ((foot.y > CULL_EDGE) && (tip.y > CULL_EDGE)));
	}

	/** Whether units cast shadows in the shape of their models onto the ground instead of round blobs. */
	public boolean isModelShadows() {
		return this.modelShadows;
	}

	public void setModelShadows(final boolean modelShadows) {
		this.modelShadows = modelShadows;
	}

	/**
	 * Whether buildings, trees and other scenery cast shadows in the shape of their models as well, in place of the
	 * shadow images that are laid under them.
	 */
	public boolean isSceneShadows() {
		return this.modelShadows && this.sceneShadows;
	}

	public void setSceneShadows(final boolean sceneShadows) {
		this.sceneShadows = sceneShadows;
	}

	/** Whether scenery of this model, at this scale, is tall enough to cast a model shadow while scene shadows are on. */
	public boolean castsSceneShadow(final MdxModel model, final float scale) {
		if (!isSceneShadows()) {
			return false;
		}
		final BoundingBox box = model.bounds.getBoundingBox();
		return (box == null) || ((box.max.z * scale) >= SCENERY_MIN_HEIGHT);
	}

	/**
	 * Draws the shapes of the units in view, flattened along the light, into a mask of the world's size. This is done
	 * before the world itself, so that the world's render target is not left and come back to in the middle of the
	 * frame, which a tile based GPU pays for. {@link #compositeUnitShadows} lays the mask over the ground.
	 *
	 * @return whether there is anything in the mask
	 */
	public boolean renderUnitShadowMask(final WebGL webGL, final Scene scene, final Terrain terrain) {
		this.casters.clear();
		this.sceneryCasters.clear();
		if (!this.modelShadows || !scene.show || !GameOptions.get().isUnitShadows()) {
			return false;
		}
		final List<ModelInstance> instances = scene.instances;
		for (int i = 0, n = instances.size(); i < n; i++) {
			final ModelInstance instance = instances.get(i);
			if (instance.rendered && (instance instanceof MdxComplexInstance)) {
				final MdxComplexInstance mdx = (MdxComplexInstance) instance;
				if (mdx.groundShadow) {
					this.casters.add(mdx);
				}
				else if (mdx.sceneryShadow) {
					this.sceneryCasters.add(mdx);
				}
			}
		}
		if (this.casters.isEmpty() && this.sceneryCasters.isEmpty()) {
			return false;
		}
		final GL30 gl = Gdx.gl30;
		final Rectangle rect = scene.camera.rect;
		final int width = Math.max(1, (int) Math.ceil(rect.width * MASK_SCALE));
		final int height = Math.max(1, (int) Math.ceil(rect.height * MASK_SCALE));
		if ((this.maskFramebuffer == 0) || (width != this.maskWidth) || (height != this.maskHeight)) {
			if (!createMask(gl, width, height)) {
				this.modelShadows = false;
				return false;
			}
		}
		gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, this.maskFramebuffer);
		gl.glViewport(0, 0, width, height);
		gl.glDisable(GL20.GL_SCISSOR_TEST);
		gl.glClearColor(0f, 0f, 0f, 0f);
		gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		MdxShadowCaster.prepareState(gl);
		final Matrix4 viewProjection = scene.camera.viewProjectionMatrix;
		prepareShadowLight();
		terrain.bindShadowHeights(this.caster, MdxShadowCaster.heightUnit());
		this.caster.beginFrame();
		drawCasters(webGL, this.casters, MAX_CASTERS, viewProjection, terrain, false);
		drawCasters(webGL, this.sceneryCasters, MAX_SCENERY, viewProjection, terrain, true);
		gl.glActiveTexture(GL20.GL_TEXTURE0);
		gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, RenderScaler.currentTarget());
		gl.glEnable(GL20.GL_SCISSOR_TEST);
		return true;
	}

	private void drawCasters(final WebGL webGL, final List<MdxComplexInstance> list, final int limit,
			final Matrix4 viewProjection, final Terrain terrain, final boolean scenery) {
		this.selected.clear();
		for (int i = 0, n = list.size(); (i < n) && (this.selected.size() < limit); i++) {
			final MdxComplexInstance instance = list.get(i);
			final float x = instance.worldLocation.x;
			final float y = instance.worldLocation.y;
			if (shadowInView(viewProjection, x, y, terrain.getGroundHeight(x, y))) {
				this.selected.add(instance);
			}
		}
		// The mask is a union, so the order does not matter: casters of one model are drawn together, which keeps
		// its geometry bound.
		this.selected.sort(BY_MODEL);
		for (int i = 0, n = this.selected.size(); i < n; i++) {
			final MdxComplexInstance instance = this.selected.get(i);
			final float groundZ = terrain.getGroundHeight(instance.worldLocation.x, instance.worldLocation.y);
			// What is in the air has its shadow straight below it, wherever the light is.
			final Vector3 light = (instance.worldLocation.z - groundZ) > AIRBORNE_HEIGHT ? this.straightDown
					: (scenery ? this.sceneryLight : this.shadowLight);
			this.caster.draw(webGL, instance, viewProjection, light, groundZ,
					levelUnder(terrain, instance.worldLocation.x, instance.worldLocation.y, groundZ, light), scenery);
		}
	}

	/**
	 * Whether the ground the shadow of something here falls on, from its foot along the light to the tip of a long
	 * shadow and a little to the sides, is level, in which case the shadow needs no search along the light.
	 */
	private boolean levelUnder(final Terrain terrain, final float x, final float y, final float groundZ,
			final Vector3 light) {
		final float dx = -(light.x / light.z) * FLAT_REACH;
		final float dy = -(light.y / light.z) * FLAT_REACH;
		final float length = (float) Math.sqrt((dx * dx) + (dy * dy));
		final float sideX = length > 0.001f ? (-dy / length) * FLAT_SIDE : FLAT_SIDE;
		final float sideY = length > 0.001f ? (dx / length) * FLAT_SIDE : 0f;
		for (int i = 0; i < FLAT_PROBES.length; i += 2) {
			final float along = FLAT_PROBES[i];
			final float side = FLAT_PROBES[i + 1];
			final float height = terrain.getGroundHeight(x + (dx * along) + (sideX * side),
					y + (dy * along) + (sideY * side));
			if (Math.abs(height - groundZ) > FLAT_TOLERANCE) {
				return false;
			}
		}
		return true;
	}

	private boolean createMask(final GL30 gl, final int width, final int height) {
		// One channel is all the mask holds; a driver that cannot draw to it gets four.
		return createMask(gl, width, height, GL30.GL_R8, GL30.GL_RED)
				|| createMask(gl, width, height, GL20.GL_RGBA, GL20.GL_RGBA);
	}

	private boolean createMask(final GL30 gl, final int width, final int height, final int internalFormat,
			final int format) {
		disposeMask();
		this.maskTexture = gl.glGenTexture();
		gl.glBindTexture(GL20.GL_TEXTURE_2D, this.maskTexture);
		gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL20.GL_UNSIGNED_BYTE, null);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
		this.maskFramebuffer = gl.glGenFramebuffer();
		gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, this.maskFramebuffer);
		gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D,
				this.maskTexture, 0);
		final int status = gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER);
		gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, RenderScaler.currentTarget());
		if (status != GL20.GL_FRAMEBUFFER_COMPLETE) {
			disposeMask();
			return false;
		}
		this.maskWidth = width;
		this.maskHeight = height;
		return true;
	}

	private void prepareComposite(final WebGL webGL) {
		if (this.compositeShader == null) {
			final GL30 gl = Gdx.gl30;
			this.compositeShader = webGL.createShaderProgram(COMPOSITE_VERTEX, COMPOSITE_FRAGMENT);
			this.quadBuffer = gl.glGenBuffer();
			final FloatBuffer quad = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
			quad.put(new float[] { -1, -1, 1, -1, -1, 1, 1, 1 }).flip();
			gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, this.quadBuffer);
			gl.glBufferData(GL20.GL_ARRAY_BUFFER, 32, quad, GL20.GL_STATIC_DRAW);
		}
	}

	/**
	 * Compiles the programs of the unit shadows while the loading screen is still up, so that the first frame that has
	 * something to shade does not.
	 */
	public void prepare(final WebGL webGL) {
		if (this.modelShadows && GameOptions.get().isUnitShadows()) {
			this.caster.prepare(webGL);
			prepareComposite(webGL);
		}
	}

	/** Lays the mask of {@link #renderUnitShadowMask} over what is drawn so far, the ground. */
	public void compositeUnitShadows(final WebGL webGL) {
		if ((this.maskFramebuffer == 0) || (this.casters.isEmpty() && this.sceneryCasters.isEmpty())) {
			return;
		}
		final GL30 gl = Gdx.gl30;
		prepareComposite(webGL);
		webGL.useShaderProgram(this.compositeShader);
		gl.glActiveTexture(GL20.GL_TEXTURE0);
		gl.glBindTexture(GL20.GL_TEXTURE_2D, this.maskTexture);
		this.compositeShader.setUniformi("u_mask", 0);
		this.compositeShader.setUniformf("u_texel", EDGE_SOFTNESS / this.maskWidth, EDGE_SOFTNESS / this.maskHeight);
		this.compositeShader.setUniformf("u_strength", this.strength);
		gl.glDisable(GL20.GL_DEPTH_TEST);
		gl.glDepthMask(false);
		gl.glDisable(GL20.GL_CULL_FACE);
		gl.glEnable(GL20.GL_BLEND);
		gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, this.quadBuffer);
		this.compositeShader.setVertexAttribute("a_position", 2, GL20.GL_FLOAT, false, 0, 0);
		PerfStats.draw();
		gl.glDrawArrays(GL20.GL_TRIANGLE_STRIP, 0, 4);
		gl.glEnable(GL20.GL_DEPTH_TEST);
		gl.glDepthMask(true);
	}

	public void dispose() {
		if (this.gl != null) {
			this.gl.glDeleteFramebuffer(this.framebufferName);
			this.gl.glDeleteTexture(this.depthTexture);
		}
		disposeMask();
	}

	/**
	 * No depth map is drawn: the terrain shaders do not read one, so a framebuffer cleared every frame only cost tile
	 * based GPUs a render pass. Unit, building and doodad shadows are the ground splats and the terrain's shadow map.
	 */
	public boolean setup(final WebGL webGL) {
		prepareShadowMatrix();
		return true;
	}

	/**
	 * @return
	 */
	public Matrix4 prepareShadowMatrix() {
		final Vector3 lightInvDir = this.shadowVector;
		lightInvDir.set(500f, 2000, 2000);

		// Compute the MVP matrix from the light's point of view
		this.depthProjectionMatrix.setToOrtho(-10, 10, -10, 10, -10, 20);
		this.depthViewMatrix.set(this.depthProjectionMatrix);
		this.depthViewMatrix.setToLookAt(lightInvDir, Vector3.Zero, RenderMathUtils.VEC3_UNIT_Y);
		this.depthModelMatrix.idt();
		this.depthMVP.set(this.depthProjectionMatrix).mul(this.depthViewMatrix).mul(this.depthModelMatrix);

//		this.shader.setUniformMatrix("depthMVP", this.depthMVP);

		this.biasMatrix.val[Matrix4.M00] = 0.5f;
		this.biasMatrix.val[Matrix4.M10] = 0.0f;
		this.biasMatrix.val[Matrix4.M20] = 0.0f;
		this.biasMatrix.val[Matrix4.M30] = 0.5f;
		this.biasMatrix.val[Matrix4.M01] = 0.0f;
		this.biasMatrix.val[Matrix4.M11] = 0.5f;
		this.biasMatrix.val[Matrix4.M21] = 0.0f;
		this.biasMatrix.val[Matrix4.M31] = 0.5f;
		this.biasMatrix.val[Matrix4.M02] = 0.0f;
		this.biasMatrix.val[Matrix4.M12] = 0.0f;
		this.biasMatrix.val[Matrix4.M22] = 0.5f;
		this.biasMatrix.val[Matrix4.M32] = 0.5f;
		this.biasMatrix.val[Matrix4.M03] = 0.0f;
		this.biasMatrix.val[Matrix4.M13] = 0.0f;
		this.biasMatrix.val[Matrix4.M23] = 0.0f;
		this.biasMatrix.val[Matrix4.M33] = 1.0f;
		this.depthBiasMVP.set(this.biasMatrix).mul(this.depthMVP);

		return this.depthMVP;
	}

	public void beginShadowMap(final WebGL webGL) {
		IS_SHADOW_MAPPING = true;

		Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebufferName);
		Extensions.dynamicShadowExtension.glFramebufferTexture(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
				this.depthTexture, 0);

		Extensions.dynamicShadowExtension.glDrawBuffer(GL30.GL_NONE); // No color buffer is drawn to.
		Gdx.gl30.glViewport(0, 0, 1024, 1024);

	}

	public Matrix4 getDepthBiasMVP() {
		return this.depthBiasMVP;
	}

	// Don't forget to change viewport back
	public void endShadowMap() {
		IS_SHADOW_MAPPING = false;
		Gdx.gl30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, RenderScaler.currentTarget());
	}

	public int getDepthTexture() {
		return this.depthTexture;
	}

	public static final String vertexShader = "#version 330 core\r\n" + //
			"\r\n" + //
			"// Input vertex data, different for all executions of this shader.\r\n" + //
			"layout(location = 0) in vec3 vertexPosition_modelspace;\r\n" + //
			"\r\n" + //
			"// Values that stay constant for the whole mesh.\r\n" + //
			"uniform mat4 depthMVP;\r\n" + //
			"\r\n" + //
			"void main(){\r\n" + //
			" gl_Position =  depthMVP * vec4(vertexPosition_modelspace,1);\r\n" + //
			"}";

	public static final String fragmentShader = "#version 330 core\r\n" + //
			"\r\n" + //
			"// Ouput data\r\n" + //
			"layout(location = 0) out float fragmentdepth;\r\n" + //
			"\r\n" + //
			"void main(){\r\n" + //
			"    // Not really needed, OpenGL does it anyway\r\n" + //
			"    fragmentdepth = gl_FragCoord.z;\r\n" + //
			"}";
	private int depthTexture;
	private int framebufferName;

}
