package com.etheller.warsmash.viewer5.handlers.mdx;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.etheller.warsmash.viewer5.gl.CachedShaderProgram;

/**
 * Uniform and attribute locations of one model shader, found once. A name given at every draw costs a table lookup
 * and, in code the runtime compiled while its string constants were not resolved, a runtime call to resolve each of
 * them again at every execution; with hundreds of draws a frame that took a few percent of the frame on phones.
 * A location that the program does not have is -1, which the setters leave alone.
 */
final class MdxLocations {
	final int mvp;
	final int vp;
	final int mv;
	final int lightTexture;
	final int lightCount;
	final int lightTextureHeight;
	final int hasBones;
	final int boneMap;
	final int vectorSize;
	final int rowSize;
	final int boneBase;
	final int texture;
	final int vertexColor;
	final int geosetColor;
	final int layerAlpha;
	final int filterMode;
	final int unshaded;
	final int unfogged;
	final int uvTrans;
	final int uvRot;
	final int uvScale;
	final int diffuseMap;
	final int normalsMap;
	final int ormMap;
	final int emissiveMap;
	final int teamColorMap;
	final int environmentMap;
	final int iblParams;
	final int tone;
	final int unlit;
	final int eyePos;
	final int fogColor;
	final int fogParams;
	final int fogHeight;

	final int position;
	final int normal;
	final int uv;
	final int bones;
	final int boneNumber;
	final int extendedBones;
	final int tangent;
	final int weights;

	private final int links;

	private MdxLocations(final ShaderProgram shader, final int links) {
		this.links = links;
		this.mvp = uniform(shader, "u_mvp");
		this.vp = uniform(shader, "u_VP");
		this.mv = uniform(shader, "u_MV");
		this.lightTexture = uniform(shader, "u_lightTexture");
		this.lightCount = uniform(shader, "u_lightCount");
		this.lightTextureHeight = uniform(shader, "u_lightTextureHeight");
		this.hasBones = uniform(shader, "u_hasBones");
		this.boneMap = uniform(shader, "u_boneMap");
		this.vectorSize = uniform(shader, "u_vectorSize");
		this.rowSize = uniform(shader, "u_rowSize");
		this.boneBase = uniform(shader, "u_boneBase");
		this.texture = uniform(shader, "u_texture");
		this.vertexColor = uniform(shader, "u_vertexColor");
		this.geosetColor = uniform(shader, "u_geosetColor");
		this.layerAlpha = uniform(shader, "u_layerAlpha");
		this.filterMode = uniform(shader, "u_filterMode");
		this.unshaded = uniform(shader, "u_unshaded");
		this.unfogged = uniform(shader, "u_unfogged");
		this.uvTrans = uniform(shader, "u_uvTrans");
		this.uvRot = uniform(shader, "u_uvRot");
		this.uvScale = uniform(shader, "u_uvScale");
		this.diffuseMap = uniform(shader, "u_diffuseMap");
		this.normalsMap = uniform(shader, "u_normalsMap");
		this.ormMap = uniform(shader, "u_ormMap");
		this.emissiveMap = uniform(shader, "u_emissiveMap");
		this.teamColorMap = uniform(shader, "u_teamColorMap");
		this.environmentMap = uniform(shader, "u_environmentMap");
		this.iblParams = uniform(shader, "u_iblParams");
		this.tone = uniform(shader, "u_tone");
		this.unlit = uniform(shader, "u_unlit");
		this.eyePos = uniform(shader, "u_eyePos");
		this.fogColor = uniform(shader, "u_fogColor");
		this.fogParams = uniform(shader, "u_fogParams");
		this.fogHeight = uniform(shader, "u_fogHeight");
		this.position = shader.getAttributeLocation("a_position");
		this.normal = shader.getAttributeLocation("a_normal");
		this.uv = shader.getAttributeLocation("a_uv");
		this.bones = shader.getAttributeLocation("a_bones");
		this.boneNumber = shader.getAttributeLocation("a_boneNumber");
		this.extendedBones = shader.getAttributeLocation("a_extendedBones");
		this.tangent = shader.getAttributeLocation("a_tangent");
		this.weights = shader.getAttributeLocation("a_weights");
	}

	private static int uniform(final ShaderProgram shader, final String name) {
		return shader.fetchUniformLocation(name, false);
	}

	/** The locations of the shader the caller has just made current; made again when the program was relinked. */
	static MdxLocations of(final ShaderProgram shader) {
		if (shader instanceof CachedShaderProgram) {
			final CachedShaderProgram cached = (CachedShaderProgram) shader;
			final MdxLocations known = (MdxLocations) cached.getHolder();
			if ((known != null) && (known.links == cached.getLinks())) {
				return known;
			}
			final MdxLocations made = new MdxLocations(shader, cached.getLinks());
			cached.setHolder(made);
			return made;
		}
		return new MdxLocations(shader, 0);
	}
}
