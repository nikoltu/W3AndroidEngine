package com.etheller.warsmash.viewer5.handlers.mdx;

import java.util.List;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.Camera;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.Texture;
import com.etheller.warsmash.viewer5.gl.DataTexture;
import com.etheller.warsmash.viewer5.gl.WebGL;
import com.etheller.warsmash.viewer5.handlers.w3x.DynamicShadowManager;
import com.etheller.warsmash.viewer5.handlers.w3x.W3xSceneLightManager;

public class BatchGroup extends GenericGroup {
	private static final int GROUND_SHADOW_MAP_UNIT = 12;
	private static final int GROUND_FOG_OF_WAR_MAP_UNIT = 13;
	// Environment-map lighting of HD materials: overall strength, and the share kept at night when the DNC sun
	// colour is black (the Reforged DNC lights carry no ambient). Tunable without a rebuild for screenshot matching.
	private static final float HD_IBL_STRENGTH = Float.parseFloat(System.getProperty("warsmash.hdIblStrength", "1.0"));
	private static final float HD_NIGHT_AMBIENT = Float.parseFloat(System.getProperty("warsmash.hdNightAmbient", "1.0"));

	private static final float[] tempFloat3Array = new float[3];

	private final MdxModel model;
	public final SkinningType skinningType;
	public final boolean hd;

	public BatchGroup(final MdxModel model, final SkinningType skinningType, final boolean hd) {
		this.model = model;
		this.skinningType = skinningType;
		this.hd = hd;
	}

	@Override
	public void render(final MdxComplexInstance instance, final Matrix4 mvp) {
		final Scene scene = instance.scene;
		final Camera camera = scene.camera;
		final MdxModel model = this.model;
		final List<Texture> textures = model.getTextures();
		final MdxHandler handler = model.handler;
		final List<Batch> batches = model.batches;
		final List<Integer> replaceables = model.replaceables;
		final ModelViewer viewer = model.viewer;
		final GL20 gl = viewer.gl;
		final WebGL webGL = viewer.webGL;
		final SkinningType skinningType = this.skinningType;
		final boolean hd = this.hd;
		final ShaderProgram shader;
		final W3xSceneLightManager lightManager = (W3xSceneLightManager) scene.getLightManager();

		if (hd) {
			shader = instance.groundShading != null ? handler.shaders.groundHd : handler.shaders.hd;
		}
		else if (skinningType == SkinningType.ExtendedVertexGroups) {
			if (DynamicShadowManager.IS_SHADOW_MAPPING) {
				shader = handler.shaders.extendedShadowMap;
			}
			else {
				shader = handler.shaders.extended;
			}
		}
		else {
			if (DynamicShadowManager.IS_SHADOW_MAPPING) {
				shader = handler.shaders.complexShadowMap;
			}
			else if (instance.groundShading != null) {
				shader = handler.shaders.groundComplex;
			}
			else {
				shader = handler.shaders.complex;
			}
		}

		webGL.useShaderProgram(shader);
		final MdxLocations locations = MdxLocations.of(shader);

		shader.setUniformMatrix(hd ? locations.vp : locations.mvp, mvp);

		final GroundShading groundShading = instance.groundShading;
		final DataTexture lightsTexture = groundShading != null ? lightManager.getTerrainLightsTexture()
				: lightManager.getUnitLightsTexture();

		lightsTexture.bind(14);
		shader.setUniformi(locations.lightTexture, 14);
		shader.setUniformf(locations.lightCount,
				groundShading != null ? lightManager.getTerrainLightCount() : lightManager.getUnitLightCount());
		shader.setUniformf(locations.lightTextureHeight, lightsTexture.getHeight());

		// Instances of models with no bones don't have a bone texture.
		if (instance.bindBones(shader, locations)) {
			shader.setUniformf(locations.hasBones, 1);
		}
		else {
			shader.setUniformf(locations.hasBones, 0);
		}

		gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, model.arrayBuffer);
		gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, model.elementBuffer);

		if (hd) {
			shader.setUniformi(locations.diffuseMap, 0);
			shader.setUniformi(locations.normalsMap, 1);
			shader.setUniformi(locations.ormMap, 2);
			shader.setUniformi(locations.emissiveMap, 3);
			shader.setUniformi(locations.teamColorMap, 4);
			shader.setUniformi(locations.environmentMap, 5);
			scene.fogSettings.uploadUniforms(shader, locations.fogColor, locations.fogParams, locations.fogHeight);
			shader.setUniformf(locations.iblParams, HD_IBL_STRENGTH * lightManager.getHdIblScale(), HD_NIGHT_AMBIENT,
					lightManager.getHdSpecularScale());
			shader.setUniform4fv(locations.vertexColor, instance.vertexColor, 0, instance.vertexColor.length);
			shader.setUniformf(locations.unlit, scene.unlit ? 1f : 0f);
			if (shader == handler.shaders.groundHd) {
				groundShading.bindGroundShading(shader, GROUND_SHADOW_MAP_UNIT, GROUND_FOG_OF_WAR_MAP_UNIT);
			}
			final float[] tone = lightManager.getHdTone();
			if (tone != null) {
				shader.setUniformf(locations.tone, tone[0], tone[1], tone[2], tone[3]);
			}
			else {
				shader.setUniformf(locations.tone, 0f, 0f, 1f, 0f);
			}

			gl.glEnable(GL20.GL_BLEND);
			gl.glEnable(GL20.GL_DEPTH_TEST);
			gl.glDepthMask(true);

			shader.setUniformMatrix(locations.mv, camera.viewMatrix);

			tempFloat3Array[0] = camera.location.x;
			tempFloat3Array[1] = camera.location.y;
			tempFloat3Array[2] = camera.location.z;
			shader.setUniform3fv(locations.eyePos, tempFloat3Array, 0, 3);

			float lastUnshaded = -1f;
			float lastUnfogged = -1f;
			for (final int index : getObjectArray()) {
				final Batch batch = batches.get(index);
				final Geoset geoset = batch.geoset;
				final Material material = batch.material;
				final Layer diffuseLayer = material.layers.get(0);
				final Layer normalsLayer = material.layers.get(1);
				final Layer ormLayer = material.layers.get(2);
				final Layer emissiveLayer = material.layers.get(3);
				final Layer teamColorLayer = material.layers.get(4);
				final Layer environmentMapLayer = material.layers.get(5);
				final float[] geosetColor = instance.geosetColors[geoset.index];
				final float layerAlpha = instance.layerAlphas[diffuseLayer.index];

				if ((geosetColor[3] > 0.01) && (layerAlpha > 0)) {
					shader.setUniformf(locations.layerAlpha, layerAlpha * geosetColor[3]);
					shader.setUniformf(locations.filterMode, diffuseLayer.filterMode);
					final float unshaded = diffuseLayer.unshaded != 0 ? 1f : 0f;
					if (unshaded != lastUnshaded) {
						shader.setUniformf(locations.unshaded, unshaded);
						lastUnshaded = unshaded;
					}
					final float unfogged = diffuseLayer.unfogged != 0 ? 1f : 0f;
					if (unfogged != lastUnfogged) {
						shader.setUniformf(locations.unfogged, unfogged);
						lastUnfogged = unfogged;
					}

					final int diffuseId = Math.max(0, instance.layerTextures[diffuseLayer.index]);
					final int normalsId = Math.max(0, instance.layerTextures[normalsLayer.index]);
					final int ormId = Math.max(0, instance.layerTextures[ormLayer.index]);
					final int emissiveId = Math.max(0, instance.layerTextures[emissiveLayer.index]);
					final int teamColorId = Math.max(0, instance.layerTextures[teamColorLayer.index]);
					final int environmentMapId = Math.max(0, instance.layerTextures[environmentMapLayer.index]);

					final Texture diffuseTexture;
					final Texture normalsTexture;
					final Texture ormTexture;
					final Texture emissiveTexture = textures.get(emissiveId);
					final Texture teamColorTexture;
					final Texture environmentMapTexture = textures.get(environmentMapId);

					{
						final Integer replaceable = replaceables.get(diffuseId);
						if ((replaceable > 0) && (replaceable < WarsmashConstants.REPLACEABLE_TEXTURE_LIMIT)
								&& (instance.replaceableTextures_diffuse != null)
								&& (instance.replaceableTextures_diffuse[replaceable] != null)) {
							diffuseTexture = instance.replaceableTextures_diffuse[replaceable];
						}
						else {
							diffuseTexture = textures.get(diffuseId);
						}
					}

					{
						final Integer replaceable = replaceables.get(normalsId);
						if ((replaceable > 0) && (replaceable < WarsmashConstants.REPLACEABLE_TEXTURE_LIMIT)
								&& (instance.replaceableTextures_normal != null)
								&& (instance.replaceableTextures_normal[replaceable] != null)) {
							normalsTexture = instance.replaceableTextures_normal[replaceable];
						}
						else {
							normalsTexture = textures.get(normalsId);
						}
					}

					{
						final Integer replaceable = replaceables.get(ormId);
						if ((replaceable > 0) && (replaceable < WarsmashConstants.REPLACEABLE_TEXTURE_LIMIT)
								&& (instance.replaceableTextures_orm != null)
								&& (instance.replaceableTextures_orm[replaceable] != null)) {
							ormTexture = instance.replaceableTextures_orm[replaceable];
						}
						else {
							ormTexture = textures.get(ormId);
						}
					}

					final Integer replaceable = replaceables.get(teamColorId);
					if ((replaceable > 0) && (replaceable < WarsmashConstants.REPLACEABLE_TEXTURE_LIMIT)
							&& (instance.replaceableTextures[replaceable] != null)) {
						teamColorTexture = instance.replaceableTextures[replaceable];
					}
					else {
						teamColorTexture = textures.get(teamColorId);
					}

					if (isPending(diffuseTexture) || isPending(normalsTexture) || isPending(ormTexture)) {
						continue;
					}
					webGL.bindTexture(diffuseTexture, 0);
					if (isFallback(normalsTexture)) {
						webGL.bindNeutralNormal(1);
					}
					else {
						webGL.bindTexture(normalsTexture, 1);
					}
					if (isFallback(ormTexture)) {
						webGL.bindNeutralOrm(2);
					}
					else {
						webGL.bindTexture(ormTexture, 2);
					}
					// The grey stand-in suits a diffuse map, but as light it would wash the model out: a missing
					// emissive map emits nothing and a missing environment map reflects nothing.
					webGL.bindTexture(isFallback(emissiveTexture) ? null : emissiveTexture, 3);
					webGL.bindTexture(teamColorTexture, 4);
					webGL.bindTexture(isFallback(environmentMapTexture) ? null : environmentMapTexture, 5);

					if (instance.vertexColor[3] < 1.0f) {
						diffuseLayer.bindBlended(shader, locations.filterMode);
					}
					else {
						diffuseLayer.bind(shader, locations.filterMode);
					}

					geoset.bindHd(shader, locations, batch.skinningType, diffuseLayer.coordId);
					geoset.render();
				}
			}
		}
		else {
			shader.setUniformi(locations.texture, 0);

			shader.setUniform4fv(locations.vertexColor, instance.vertexColor, 0, instance.vertexColor.length);
			if (shader == handler.shaders.groundComplex) {
				groundShading.bindGroundShading(shader, GROUND_SHADOW_MAP_UNIT, GROUND_FOG_OF_WAR_MAP_UNIT);
			}

			for (final int index : getObjectArray()) {
				final Batch batch = batches.get(index);
				final Geoset geoset = batch.geoset;
				final Layer layer = batch.layer;
				final int geosetIndex = geoset.index;
				final int layerIndex = layer.index;
				final float[] geosetColor = instance.geosetColors[geosetIndex];
				final float layerAlpha = instance.layerAlphas[layerIndex];

				if ((geosetColor[3] > 0.01) && (layerAlpha > 0.01) && !(instance.teamGlowHidden
						&& (replaceables.get(Math.max(0, instance.layerTextures[layerIndex])) == 2))) {
					// BELOW: I updated it to "Math.max(0," because MDL and MDX parser for PRSCMOD
					// menu screen behaved differently,
					// the MDL case was getting "no data" for default value when unanimated, and "no
					// data" resolved to -1,
					// whereas MDX binary contained an "unused" 0 value.
					final int layerTexture = Math.max(0, instance.layerTextures[layerIndex]);
					final float[] uvAnim = instance.uvAnims[layerIndex];

					shader.setUniform4fv(locations.geosetColor, geosetColor, 0, geosetColor.length);

					shader.setUniformf(locations.layerAlpha, layerAlpha);
					shader.setUniformi(locations.unshaded, layer.unshaded != 0 ? 1 : 0);
					shader.setUniformi(locations.unfogged, layer.unfogged != 0 ? 1 : 0);
					scene.fogSettings.uploadUniforms(shader, locations.fogColor, locations.fogParams, locations.fogHeight);

					shader.setUniform2fv(locations.uvTrans, uvAnim, 0, 2);
					shader.setUniform2fv(locations.uvRot, uvAnim, 2, 2);
					shader.setUniform1fv(locations.uvScale, uvAnim, 4, 1);

					if (instance.additiveOverrideMeshMode) {
						layer.bindBlended(shader, locations.filterMode);
						gl.glBlendFunc(FilterMode.ADDITIVE_ALPHA[0], FilterMode.ADDITIVE_ALPHA[1]);
					}
					else if (instance.vertexColor[3] < 1.0f) {
						layer.bindBlended(shader, locations.filterMode);
					}
					else {
						layer.bind(shader, locations.filterMode);
					}

					final Integer replaceable = replaceables.get(layerTexture); // TODO is this OK?
					Texture texture;

					if ((replaceable > 0) && (replaceable < WarsmashConstants.REPLACEABLE_TEXTURE_LIMIT)
							&& (instance.replaceableTextures[replaceable] != null)) {
						texture = instance.replaceableTextures[replaceable];
					}
					else {
						texture = textures.get(layerTexture);

						Texture textureLookup = instance.textureMapper.get(texture);
						if (textureLookup == null) {
							textureLookup = texture;
						}
						texture = textureLookup;
					}

					if (isPending(texture)) {
						continue;
					}
					viewer.webGL.bindTexture(texture, 0);

					if (skinningType == SkinningType.ExtendedVertexGroups) {
						geoset.bindExtended(shader, locations, layer.coordId);
					}
					else {
						geoset.bind(shader, locations, layer.coordId);
					}

					geoset.render();
				}
			}
		}

	}

	private static boolean isFallback(final Texture texture) {
		return (texture != null) && texture.isFallback();
	}

	/** A texture still waiting for its upload draws as nothing rather than as black. */
	private static boolean isPending(final Texture texture) {
		return (texture != null) && texture.isPending();
	}

	/**
	 * Draws the opaque and cut-out layers of this group into a ground shadow mask with the bound shader: no lighting,
	 * only the shape of the model.
	 */
	void renderShadow(final MdxComplexInstance instance, final ShaderProgram shader) {
		if (!instance.bindBones(shader)) {
			return;
		}
		final MdxModel model = this.model;
		final List<Batch> batches = model.batches;
		final GL20 gl = model.viewer.gl;
		gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, model.arrayBuffer);
		gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, model.elementBuffer);
		for (final int index : getObjectArray()) {
			final Batch batch = batches.get(index);
			final Geoset geoset = batch.geoset;
			final Layer layer = this.hd ? batch.material.layers.get(0) : batch.layer;
			// Additive glows, modulated layers and the like are light, not matter.
			if (layer.filterMode > 2) {
				continue;
			}
			if ((instance.geosetColors[geoset.index][3] <= 0.01f) || (instance.layerAlphas[layer.index] <= 0.01f)) {
				continue;
			}
			final boolean cutout = layer.filterMode != 0;
			shader.setUniformi("u_cutout", cutout ? 1 : 0);
			if (cutout) {
				bindCutoutTexture(instance, layer);
			}
			if (this.hd) {
				geoset.bindHd(shader, batch.skinningType, layer.coordId);
			}
			else if (this.skinningType == SkinningType.ExtendedVertexGroups) {
				geoset.bindExtended(shader, layer.coordId);
			}
			else {
				geoset.bind(shader, layer.coordId);
			}
			geoset.render();
		}
	}

	/**
	 * Draws the shadow of an instance from the model's {@link ShadowMesh}: a run of visible batches without an alpha test
	 * is one draw. The bones are bound by the caller, and what the caller's state says is already set is not set again.
	 */
	void renderShadowMesh(final MdxComplexInstance instance, final ShaderProgram shader, final ShadowMesh mesh,
			final MdxShadowCaster.ProgramState state) {
		final MdxModel model = this.model;
		final List<Batch> batches = model.batches;
		final GL20 gl = model.viewer.gl;
		if (state.mesh != mesh) {
			state.mesh = mesh;
			gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, mesh.vertexBuffer);
			gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, mesh.indexBuffer);
			mesh.bind(shader);
		}
		int runStart = 0;
		int runEnd = 0;
		for (final int index : getObjectArray()) {
			final int first = mesh.first[index];
			final int count = mesh.count[index];
			if ((first < 0) || (count == 0)) {
				continue;
			}
			final Batch batch = batches.get(index);
			final Layer layer = batch.layer;
			if ((instance.geosetColors[batch.geoset.index][3] <= 0.01f)
					|| (instance.layerAlphas[layer.index] <= 0.01f)) {
				continue;
			}
			if (mesh.cutout[index]) {
				state.setCutout(shader, true);
				bindCutoutTexture(instance, layer);
				PerfStats.draw();
				gl.glDrawElements(GL20.GL_TRIANGLES, count, GL20.GL_UNSIGNED_SHORT, first * 2);
				continue;
			}
			if ((runEnd > runStart) && (first == runEnd)) {
				runEnd += count;
				continue;
			}
			if (runEnd > runStart) {
				state.setCutout(shader, false);
				PerfStats.draw();
				gl.glDrawElements(GL20.GL_TRIANGLES, runEnd - runStart, GL20.GL_UNSIGNED_SHORT, runStart * 2);
			}
			runStart = first;
			runEnd = first + count;
		}
		if (runEnd > runStart) {
			state.setCutout(shader, false);
			PerfStats.draw();
			gl.glDrawElements(GL20.GL_TRIANGLES, runEnd - runStart, GL20.GL_UNSIGNED_SHORT, runStart * 2);
		}
	}

	private void bindCutoutTexture(final MdxComplexInstance instance, final Layer layer) {
		final MdxModel model = this.model;
		final List<Texture> textures = model.getTextures();
		final List<Integer> replaceables = model.replaceables;
		final int textureId = Math.max(0, instance.layerTextures[layer.index]);
		final Integer replaceable = replaceables.get(textureId);
		Texture texture = null;
		if ((replaceable > 0) && (replaceable < WarsmashConstants.REPLACEABLE_TEXTURE_LIMIT)) {
			if (this.hd) {
				if (instance.replaceableTextures_diffuse != null) {
					texture = instance.replaceableTextures_diffuse[replaceable];
				}
			}
			else {
				texture = instance.replaceableTextures[replaceable];
			}
		}
		if (texture == null) {
			texture = textures.get(textureId);
			if (!this.hd) {
				final Texture mapped = instance.textureMapper.get(texture);
				if (mapped != null) {
					texture = mapped;
				}
			}
		}
		model.viewer.webGL.bindTexture(texture, 0);
	}
}
