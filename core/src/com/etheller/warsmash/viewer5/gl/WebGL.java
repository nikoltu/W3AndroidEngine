package com.etheller.warsmash.viewer5.gl;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.Texture;

/**
 * This needs a rename. Just a ripoff of ghostwolf's wrapper utility class, it's
 * a utility, not a webgl
 */
public class WebGL {
	private static final Pattern DESKTOP_VERSION = Pattern.compile("^\\s*#version\\s+(\\d+)[^\\n]*\\n");
	// Shared snippets such as the light system use texture2D in both GLSL 1.x and
	// 3.x shaders; ES 3.00 only has texture.
	private static final String ES3_SAMPLER_PRECISION = "precision highp sampler2DArray;\nprecision highp usampler2D;\n"
			+ "precision highp isampler2D;\nprecision highp sampler2DShadow;\n#define texture2D texture\n";
	public GL20 gl;
	public Map<Integer, ShaderProgram> shaderPrograms;
	public ShaderProgram currentShaderProgram;
	public String floatPrecision;
	public final com.badlogic.gdx.graphics.Texture emptyTexture;
	/** Stands for a texture that is not uploaded yet, so what uses it draws nothing. */
	private final com.badlogic.gdx.graphics.Texture transparentTexture;
	/** Stand in for a normal map and an ORM map that could not be read, made when first needed. */
	private com.badlogic.gdx.graphics.Texture neutralNormalTexture;
	private com.badlogic.gdx.graphics.Texture neutralOrmTexture;
	public ANGLEInstancedArrays instancedArrays;

	public WebGL(final GL20 gl) {
		gl.glDepthFunc(GL20.GL_LEQUAL);
		gl.glEnable(GL20.GL_DEPTH_TEST);

		// TODO here ghostwolf throws exceptions for unsupported versions of opengl

		this.gl = gl;

		this.shaderPrograms = new HashMap<>();

		this.currentShaderProgram = null;
		this.floatPrecision = "precision mediump float;\n";

		final Pixmap imageData = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				imageData.drawPixel(i, j, 0x000000FF);
			}
		}
		this.emptyTexture = new com.badlogic.gdx.graphics.Texture(imageData);
		final Pixmap transparentData = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
		transparentData.setColor(0);
		transparentData.fill();
		this.transparentTexture = new com.badlogic.gdx.graphics.Texture(transparentData);
		transparentData.dispose();
		this.instancedArrays = Extensions.angleInstancedArrays;
	}

	public void dispose() {
		for (final ShaderProgram program : this.shaderPrograms.values()) {
			program.dispose();
		}
		this.shaderPrograms.clear();
		this.currentShaderProgram = null;
		this.emptyTexture.dispose();
		this.transparentTexture.dispose();
		if (this.neutralNormalTexture != null) {
			this.neutralNormalTexture.dispose();
			this.neutralNormalTexture = null;
		}
		if (this.neutralOrmTexture != null) {
			this.neutralOrmTexture.dispose();
			this.neutralOrmTexture = null;
		}
	}

	/** Binds a normal map that leaves the surface flat, for a normal map that could not be read. */
	public void bindNeutralNormal(final int unit) {
		if (this.neutralNormalTexture == null) {
			this.neutralNormalTexture = solidTexture(0x8080FFFF);
		}
		this.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
		this.neutralNormalTexture.bind();
	}

	/**
	 * Binds an ORM map for a rough, unoccluded, non-metal surface with no team colour, for an ORM map that could not be
	 * read.
	 */
	public void bindNeutralOrm(final int unit) {
		if (this.neutralOrmTexture == null) {
			this.neutralOrmTexture = solidTexture(0xFFCC0000);
		}
		this.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
		this.neutralOrmTexture.bind();
	}

	private static com.badlogic.gdx.graphics.Texture solidTexture(final int rgba8888) {
		final Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
		pixmap.setColor(rgba8888);
		pixmap.fill();
		final com.badlogic.gdx.graphics.Texture texture = new com.badlogic.gdx.graphics.Texture(pixmap);
		pixmap.dispose();
		return texture;
	}

	public ShaderProgram createShaderProgram(String vertexSrc, String fragmentSrc) {
		if (Gdx.graphics.getGLVersion().getType() == GLVersion.Type.GLES) {
			vertexSrc = adaptToGLES(vertexSrc);
			fragmentSrc = adaptToGLES(fragmentSrc);
		}
		else {
			vertexSrc = vertexSrc.replace("mediump", "");
			fragmentSrc = fragmentSrc.replace("mediump", "");
		}
		final Map<Integer, ShaderProgram> shaderPrograms = this.shaderPrograms;

		final int hash = stringHash(vertexSrc + fragmentSrc);
		ShaderProgram.pedantic = false;
		if (!shaderPrograms.containsKey(hash)) {
			shaderPrograms.put(hash, new CachedShaderProgram(vertexSrc, fragmentSrc));
		}

		final ShaderProgram shaderProgram = shaderPrograms.get(hash);

		if (shaderProgram.isCompiled()) {
			return shaderProgram;
		}
		shaderPrograms.remove(hash);
		final String report = ShaderFailure.describe(this.gl, vertexSrc, fragmentSrc, shaderProgram.getLog());
		shaderProgram.dispose();
		Log.error(report);
		Log.error(ShaderFailure.numbered("Vertex shader source", vertexSrc));
		Log.error(ShaderFailure.numbered("Fragment shader source", fragmentSrc));
		throw new ShaderFailure(report);
	}

	/**
	 * The shaders are written for desktop GLSL. OpenGL ES accepts the same bodies
	 * once the version names an ES dialect and a default float precision is given.
	 */
	private static String adaptToGLES(final String source) {
		final Matcher versionMatcher = DESKTOP_VERSION.matcher(source);
		// Without this a sampler is lowp, which a driver such as Mali's may honour for the float data textures (bone
		// matrices, lights, heights) by returning reduced precision values.
		String header = "precision highp float;\nprecision highp int;\nprecision highp sampler2D;\n";
		String body = source;
		if (versionMatcher.find()) {
			if (Integer.parseInt(versionMatcher.group(1)) >= 300) {
				header = "#version 300 es\n" + header + ES3_SAMPLER_PRECISION;
			}
			body = source.substring(versionMatcher.end());
		}
		return header + body;
	}

	public void enableVertexAttribs(final int start, final int end) {
		final GL20 gl = this.gl;

		for (int i = start; i < end; i++) {
			gl.glEnableVertexAttribArray(i);
		}
	}

	public void disableVertexAttribs(final int start, final int end) {
		final GL20 gl = this.gl;

		for (int i = start; i < end; i++) {
			gl.glDisableVertexAttribArray(i);
		}
	}

	public void useShaderProgram(final ShaderProgram shaderProgram) {
		final ShaderProgram currentShaderProgram = this.currentShaderProgram;

		if ((shaderProgram != null) && shaderProgram.isCompiled() && (shaderProgram != currentShaderProgram)) {
			int oldAttribs = 0;
			final int newAttribs = shaderProgram.getAttributes().length;

			if (currentShaderProgram != null) {
				oldAttribs = currentShaderProgram.getAttributes().length;
			}

			shaderProgram.begin();

			if (newAttribs > oldAttribs) {
				this.enableVertexAttribs(oldAttribs, newAttribs);
			}
			else if (newAttribs < oldAttribs) {
				this.disableVertexAttribs(newAttribs, oldAttribs);
			}

			this.currentShaderProgram = shaderProgram;
		}
		else if (shaderProgram == null) {
			int oldAttribs = 0;
			final int newAttribs = 0;

			if (currentShaderProgram != null) {
				oldAttribs = currentShaderProgram.getAttributes().length;
				currentShaderProgram.end();
			}

			if (newAttribs > oldAttribs) {
				this.enableVertexAttribs(oldAttribs, newAttribs);
			}
			else if (newAttribs < oldAttribs) {
				this.disableVertexAttribs(newAttribs, oldAttribs);
			}

			this.currentShaderProgram = shaderProgram;
		}
	}

	public void bindTexture(final Texture texture, final int unit) {
		final GL20 gl = this.gl;

		gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);

		if (texture != null) {
			texture.markDrawn();
		}
		if ((texture != null) && texture.isPending()) {
			this.transparentTexture.bind();
		}
		else if (texture != null /* && texture.ok */) {
			texture.internalBind();
		}
		else {
			this.emptyTexture.bind();
		}
	}

	private int stringHash(final String src) {
		return src.hashCode();
	}
}
