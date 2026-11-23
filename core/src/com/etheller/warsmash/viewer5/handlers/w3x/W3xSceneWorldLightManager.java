package com.etheller.warsmash.viewer5.handlers.w3x;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.viewer5.SceneLightInstance;
import com.etheller.warsmash.viewer5.SceneLightManager;
import com.etheller.warsmash.viewer5.gl.DataTexture;
import com.etheller.warsmash.viewer5.handlers.mdx.LightInstance;

public class W3xSceneWorldLightManager implements SceneLightManager, W3xSceneLightManager {
	/**
	 * The graphics packages' day and night light is strongly tinted at night and has no ambient part (the packages light
	 * the models from an environment map), so the night ground came out nearly black and saturated blue. At night the
	 * light's colour is moved this far towards grey (1 keeps it), and an ambient light of the light's own hue with this
	 * strength is added to the terrain light, fading out as the light reaches full day strength.
	 */
	private static final float HD_NIGHT_SATURATION = Float
			.parseFloat(System.getProperty("warsmash.hdNightSaturation", "0.3"));
	private static final float HD_TERRAIN_NIGHT_FILL = Float
			.parseFloat(System.getProperty("warsmash.hdTerrainNightFill", "0.3"));
	private static final float DE_TERRAIN_NIGHT_FILL = 0.85f;
	/**
	 * Some packages' night light (the summer tilesets') is nearly as strong as their day light, so a night map stayed
	 * bright. In the HD package, at full night the light's strength (colour brightness times intensity) is lowered to at
	 * most this.
	 */
	private static final float HD_NIGHT_MAX_STRENGTH = Float
			.parseFloat(System.getProperty("warsmash.hdNightMaxStrength", "0.2"));
	/**
	 * The same ambient light of the sun's own hue for the models of the world, which the environment map alone left
	 * nearly black (trees, crates, a mill) next to the lit ground: by day and at full night.
	 */
	private static final float HD_UNIT_DAY_FILL = 0.2f;
	private static final float HD_UNIT_NIGHT_FILL = 0.45f;
	public final List<LightInstance> lights;
	private FloatBuffer lightDataCopyHeap;
	private final DataTexture unitLightsTexture;
	private final DataTexture terrainLightsTexture;
	private final War3MapViewer viewer;
	private int terrainLightCount;
	private int unitLightCount;

	public W3xSceneWorldLightManager(final War3MapViewer viewer) {
		this.viewer = viewer;
		this.lights = new ArrayList<>();
		this.unitLightsTexture = new DataTexture(viewer.gl, 4, 4, 1);
		this.terrainLightsTexture = new DataTexture(viewer.gl, 4, 4, 1);
		this.lightDataCopyHeap = ByteBuffer.allocateDirect(16 * 1 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
	}

	@Override
	public void add(final SceneLightInstance lightInstance) {
		// TODO redesign to avoid cast
		final LightInstance mdxLight = (LightInstance) lightInstance;
		this.lights.add(mdxLight);
	}

	@Override
	public void remove(final SceneLightInstance lightInstance) {
		// TODO redesign to avoid cast
		final LightInstance mdxLight = (LightInstance) lightInstance;
		this.lights.remove(mdxLight);
	}

	@Override
	public void dispose() {
		this.unitLightsTexture.dispose();
		this.terrainLightsTexture.dispose();
	}

	@Override
	public void update() {
		final int numberOfLights = this.lights.size() + 1;
		final int bytesNeeded = numberOfLights * 4 * 16;
		if (bytesNeeded > (this.lightDataCopyHeap.capacity() * 4)) {
			this.lightDataCopyHeap = ByteBuffer.allocateDirect(bytesNeeded).order(ByteOrder.nativeOrder())
					.asFloatBuffer();
			this.unitLightsTexture.reserve(4, numberOfLights);
			this.terrainLightsTexture.reserve(4, numberOfLights);
		}

		this.unitLightCount = 0;
		this.lightDataCopyHeap.clear();
		int offset = 0;
		if (this.viewer.dncUnit != null) {
			if (!this.viewer.dncUnit.lights.isEmpty()) {
				this.viewer.dncUnit.lights.get(0).bind(0, this.lightDataCopyHeap);
				adjustPackageLight(this.lightDataCopyHeap, false);
				offset += 16;
				this.unitLightCount++;
			}
		}
		for (final LightInstance light : this.lights) {
			light.bind(offset, this.lightDataCopyHeap);
			offset += 16;
			this.unitLightCount++;
		}
		this.lightDataCopyHeap.limit(offset);
		this.unitLightsTexture.bindAndUpdateIfChanged(this.lightDataCopyHeap, 4, this.unitLightCount);

		this.terrainLightCount = 0;
		this.lightDataCopyHeap.clear();
		offset = 0;
		if (this.viewer.dncTerrain != null) {
			if (!this.viewer.dncTerrain.lights.isEmpty()) {
				this.viewer.dncTerrain.lights.get(0).bind(0, this.lightDataCopyHeap);
				adjustPackageLight(this.lightDataCopyHeap, true);
				offset += 16;
				this.terrainLightCount++;
			}
		}
		for (final LightInstance light : this.lights) {
			light.bind(offset, this.lightDataCopyHeap);
			offset += 16;
			this.terrainLightCount++;
		}
		this.lightDataCopyHeap.limit(offset);
		this.terrainLightsTexture.bindAndUpdateIfChanged(this.lightDataCopyHeap, 4, this.terrainLightCount);
	}

	private void adjustPackageLight(final FloatBuffer light, final boolean terrain) {
		if (!this.viewer.isPackageInterface()) {
			return;
		}
		if ((light.get(12) + light.get(13) + light.get(14)) * light.get(15) > 0.01f) {
			return;
		}
		final float red = light.get(8);
		final float green = light.get(9);
		final float blue = light.get(10);
		final float grey = (0.299f * red) + (0.587f * green) + (0.114f * blue);
		final boolean hd = this.viewer.isHdGraphics();
		if (hd) {
			final float maxStrength = 1f + ((HD_NIGHT_MAX_STRENGTH - 1f) * this.viewer.dynamicShadowManager.getNight());
			if ((light.get(11) * grey) > maxStrength) {
				light.put(11, maxStrength / grey);
			}
		}
		final float strength = light.get(11) * grey;
		final float night = 1f - Math.min(1f, strength);
		final float saturation = HD_NIGHT_SATURATION + ((1f - HD_NIGHT_SATURATION) * (1f - night));
		light.put(8, grey + ((red - grey) * saturation));
		light.put(9, grey + ((green - grey) * saturation));
		light.put(10, grey + ((blue - grey) * saturation));
		if (grey > 0.001f) {
			final float scale = 1f / grey;
			light.put(12, light.get(8) * scale);
			light.put(13, light.get(9) * scale);
			light.put(14, light.get(10) * scale);
			if (terrain) {
				light.put(15, (hd ? HD_TERRAIN_NIGHT_FILL : DE_TERRAIN_NIGHT_FILL) * night);
			}
			else {
				light.put(15, HD_UNIT_DAY_FILL + ((HD_UNIT_NIGHT_FILL - HD_UNIT_DAY_FILL) * night));
			}
		}
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
		return this.terrainLightsTexture;
	}

	@Override
	public int getTerrainLightCount() {
		return this.terrainLightCount;
	}
}
