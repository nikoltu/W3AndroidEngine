package com.etheller.warsmash.viewer5.handlers.w3x;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.SceneLightInstance;
import com.etheller.warsmash.viewer5.SceneLightManager;
import com.etheller.warsmash.viewer5.StaticSceneLightInstance;
import com.etheller.warsmash.viewer5.gl.DataTexture;

public class W3xScenePortraitLightManager implements SceneLightManager, W3xSceneLightManager {
	/**
	 * The graphics packages' portraits are lit from the environment map as well, so they get a weaker and warmer direct
	 * light, none of the Classic light's ambient and less of the environment map and of the specular highlights than the
	 * world: with the Classic values the faces and the light fur and bones come out overexposed.
	 */
	private static final float PACKAGE_LIGHT_INTENSITY = 0.7f;
	private static final float PACKAGE_LIGHT_RED = 1f;
	private static final float PACKAGE_LIGHT_GREEN = 0.78f;
	private static final float PACKAGE_LIGHT_BLUE = 0.6f;
	private static final float PACKAGE_AMBIENT_INTENSITY = 0f;
	private static final float PACKAGE_IBL_SCALE = 0.2f;
	private static final float PACKAGE_SPECULAR_SCALE = 0.25f;
	/**
	 * One light does not suit every model of a portrait: the diffuse light wraps a little round the model so that
	 * the dark ones are not lost, and what is brighter than the knee is compressed so that the light stone and skin do
	 * not burn out. The packages' environment lit them softer.
	 */
	private final float[] hdTone = { 0.25f, 1f, 0.7f, 0f };
	private float hdIblScale = 1f;
	private float hdSpecularScale = 1f;
	public final List<SceneLightInstance> lights;
	private FloatBuffer lightDataCopyHeap;
	private final DataTexture unitLightsTexture;
	private int unitLightCount;
	private Supplier<SceneLightInstance> lightOverride;

	public W3xScenePortraitLightManager(GL20 gl) {
		this.lights = new ArrayList<>();
		this.unitLightsTexture = new DataTexture(gl, 4, 4, 1);
		this.lightDataCopyHeap = ByteBuffer.allocateDirect(16 * 1 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
	}

	public W3xScenePortraitLightManager(final ModelViewer viewer, final Vector3 lightDirection) {
		this(viewer, lightDirection, 1, 0.3f);
	}

	public W3xScenePortraitLightManager(final ModelViewer viewer, final Vector3 lightDirection, final float intensity,
			final float ambientIntensity) {
		this(viewer.gl);
		add(StaticSceneLightInstance.createDefault(lightDirection, intensity, ambientIntensity));
	}

	public static W3xScenePortraitLightManager forPackageModels(final ModelViewer viewer,
			final Vector3 lightDirection) {
		final W3xScenePortraitLightManager lights = new W3xScenePortraitLightManager(viewer.gl);
		lights.add(StaticSceneLightInstance.createDefault(lightDirection,
				new float[] { PACKAGE_LIGHT_RED, PACKAGE_LIGHT_GREEN, PACKAGE_LIGHT_BLUE }, PACKAGE_LIGHT_INTENSITY,
				new float[] { 1, 1, 1 }, PACKAGE_AMBIENT_INTENSITY));
		lights.hdIblScale = PACKAGE_IBL_SCALE;
		lights.hdSpecularScale = PACKAGE_SPECULAR_SCALE;
		return lights;
	}

	@Override
	public float[] getHdTone() {
		return this.hdIblScale < 1f ? this.hdTone : null;
	}

	@Override
	public float getHdIblScale() {
		return this.hdIblScale;
	}

	@Override
	public float getHdSpecularScale() {
		return this.hdSpecularScale;
	}

	/** A light that replaces the ones of the portrait while it is there (SetPortraitLight); the supplier may give null. */
	public void setLightOverride(final Supplier<SceneLightInstance> lightOverride) {
		this.lightOverride = lightOverride;
	}

	@Override
	public void add(final SceneLightInstance lightInstance) {
		this.lights.add(lightInstance);
	}

	@Override
	public void remove(final SceneLightInstance lightInstance) {
		this.lights.remove(lightInstance);
	}

	@Override
	public void dispose() {
		this.unitLightsTexture.dispose();
	}

	@Override
	public void update() {
		final int numberOfLights = this.lights.size() + 1;
		final int bytesNeeded = numberOfLights * 4 * 16;
		if (bytesNeeded > (this.lightDataCopyHeap.capacity() * 4)) {
			this.lightDataCopyHeap = ByteBuffer.allocateDirect(bytesNeeded).order(ByteOrder.nativeOrder())
					.asFloatBuffer();
			this.unitLightsTexture.reserve(4, numberOfLights);
		}

		this.unitLightCount = 0;
		this.lightDataCopyHeap.clear();
		int offset = 0;
		final SceneLightInstance override = this.lightOverride == null ? null : this.lightOverride.get();
		if (override != null) {
			override.bind(offset, this.lightDataCopyHeap);
			offset += 16;
			this.unitLightCount++;
		}
		else {
			for (final SceneLightInstance light : this.lights) {
				light.bind(offset, this.lightDataCopyHeap);
				offset += 16;
				this.unitLightCount++;
			}
		}
		this.lightDataCopyHeap.limit(offset);
		this.unitLightsTexture.bindAndUpdateIfChanged(this.lightDataCopyHeap, 4, this.unitLightCount);
	}

	@Override
	public DataTexture getUnitLightsTexture() {
		return this.unitLightsTexture;
	}

	@Override
	public int getUnitLightCount() {
		return this.unitLightCount;
	}

	@Override
	public DataTexture getTerrainLightsTexture() {
		return null;
	}

	@Override
	public int getTerrainLightCount() {
		return 0;
	}
}
