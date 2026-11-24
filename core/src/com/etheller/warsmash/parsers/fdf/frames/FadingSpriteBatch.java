package com.etheller.warsmash.parsers.fdf.frames;

import java.nio.IntBuffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.NumberUtils;
import com.etheller.warsmash.util.Log;

/**
 * A sprite batch whose output can be faded as a whole, so a frame and everything drawn inside it (textures, text,
 * tinted parts) fades together without each frame knowing about it.
 * <p>
 * Sprites of up to {@value #TEXTURE_UNITS} different textures share one draw call: every vertex carries the number of
 * the texture unit it samples, so the interface, whose pieces are almost all separate textures, is drawn in a few
 * calls instead of one call per texture change. Draw order is unchanged. Because a texture is only bound when the
 * batch is flushed, code that changes a texture's parameters or contents between two draws must flush first.
 */
public class FadingSpriteBatch extends SpriteBatch {
	private static final int TEXTURE_UNITS = 8;
	private static final int MAX_SPRITES = 1000;
	private static final int VERTEX_SIZE = 5;
	private static final int BATCH_VERTEX_SIZE = 6;
	private static final int SPRITE_SIZE = 4 * BATCH_VERTEX_SIZE;
	private static final int COLOR_OFFSET = 2;

	private static final String VERTEX_SHADER = "attribute vec4 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
			+ "attribute vec4 " + ShaderProgram.COLOR_ATTRIBUTE + ";\n" + "attribute vec2 "
			+ ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n" + "attribute float a_texIndex;\n"
			+ "uniform mat4 u_projTrans;\n" + "varying vec4 v_color;\n" + "varying vec2 v_texCoords;\n"
			+ "varying float v_texIndex;\n" + "void main() {\n" + "  v_color = " + ShaderProgram.COLOR_ATTRIBUTE
			+ ";\n" + "  v_color.a = v_color.a * (255.0 / 254.0);\n" + "  v_texCoords = "
			+ ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n" + "  v_texIndex = a_texIndex;\n" + "  gl_Position = u_projTrans * "
			+ ShaderProgram.POSITION_ATTRIBUTE + ";\n" + "}\n";

	private static String fragmentShader() {
		final StringBuilder source = new StringBuilder();
		source.append("#ifdef GL_ES\n#define LOWP lowp\nprecision mediump float;\n#else\n#define LOWP\n#endif\n");
		source.append("varying LOWP vec4 v_color;\nvarying vec2 v_texCoords;\nvarying float v_texIndex;\n");
		source.append("uniform sampler2D u_texture;\n");
		for (int i = 1; i < TEXTURE_UNITS; i++) {
			source.append("uniform sampler2D u_texture").append(i).append(";\n");
		}
		source.append("void main() {\n  int unit = int(v_texIndex + 0.5);\n  vec4 texel;\n");
		source.append("  if (unit == 0) texel = texture2D(u_texture, v_texCoords);\n");
		for (int i = 1; i < TEXTURE_UNITS; i++) {
			source.append("  else if (unit == ").append(i).append(") texel = texture2D(u_texture").append(i)
					.append(", v_texCoords);\n");
		}
		source.append("  gl_FragColor = v_color * texel;\n}\n");
		return source.toString();
	}

	private static final String[] SAMPLER_NAMES = new String[TEXTURE_UNITS];
	static {
		for (int i = 1; i < TEXTURE_UNITS; i++) {
			SAMPLER_NAMES[i] = "u_texture" + i;
		}
	}

	private static ShaderProgram createShader() {
		final ShaderProgram program = new ShaderProgram(VERTEX_SHADER, fragmentShader());
		if (!program.isCompiled()) {
			throw new IllegalStateException("Error compiling shader: " + program.getLog());
		}
		return program;
	}

	private Color unscaledColor;
	private float alphaScale = 1f;

	private final ShaderProgram multiShader;
	private final Mesh mesh;
	private final float[] vertices = new float[MAX_SPRITES * SPRITE_SIZE];
	private final Texture[] textures = new Texture[TEXTURE_UNITS];
	private int textureCount;
	private int maxTextures = TEXTURE_UNITS;
	private int idx;

	public FadingSpriteBatch() {
		this(createShader());
	}

	private FadingSpriteBatch(final ShaderProgram shader) {
		super(1, shader);
		this.multiShader = shader;
		final IntBuffer units = BufferUtils.newIntBuffer(16);
		Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_IMAGE_UNITS, units);
		this.maxTextures = Math.max(1, Math.min(TEXTURE_UNITS, units.get(0)));
		this.mesh = new Mesh((overrideVertexType != null) ? overrideVertexType : defaultVertexDataType, false,
				MAX_SPRITES * 4, MAX_SPRITES * 6,
				new VertexAttribute(Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
				new VertexAttribute(Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
				new VertexAttribute(Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"),
				new VertexAttribute(Usage.Generic, 1, "a_texIndex"));
		final short[] indices = new short[MAX_SPRITES * 6];
		for (int i = 0, v = 0; i < indices.length; i += 6, v += 4) {
			indices[i] = (short) v;
			indices[i + 1] = (short) (v + 1);
			indices[i + 2] = (short) (v + 2);
			indices[i + 3] = (short) (v + 2);
			indices[i + 4] = (short) (v + 3);
			indices[i + 5] = (short) v;
		}
		this.mesh.setIndices(indices);
	}

	@Override
	protected void setupMatrices() {
		super.setupMatrices();
		// Uniform values are lost with the GL context, so they are set again with every begin.
		if (getShader() == this.multiShader) {
			for (int i = 1; i < TEXTURE_UNITS; i++) {
				this.multiShader.setUniformi(SAMPLER_NAMES[i], i);
			}
		}
	}

	@Override
	public void end() {
		flush();
		super.end();
	}

	@Override
	public void flush() {
		if (this.idx == 0) {
			return;
		}
		this.renderCalls++;
		this.totalRenderCalls++;
		final int sprites = this.idx / SPRITE_SIZE;
		if (sprites > this.maxSpritesInBatch) {
			this.maxSpritesInBatch = sprites;
		}
		for (int i = 0; i < this.textureCount; i++) {
			this.textures[i].bind(i);
		}
		if (this.textureCount > 1) {
			Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
		}
		this.mesh.setVertices(this.vertices, 0, this.idx);
		this.mesh.getIndicesBuffer(true).position(0).limit(sprites * 6);
		if (isBlendingEnabled()) {
			Gdx.gl.glEnable(GL20.GL_BLEND);
			if (getBlendSrcFunc() != -1) {
				Gdx.gl.glBlendFuncSeparate(getBlendSrcFunc(), getBlendDstFunc(), getBlendSrcFuncAlpha(),
						getBlendDstFuncAlpha());
			}
		}
		else {
			Gdx.gl.glDisable(GL20.GL_BLEND);
		}
		this.mesh.render(getShader(), GL20.GL_TRIANGLES, 0, sprites * 6);
		this.idx = 0;
		this.textureCount = 0;
	}

	@Override
	public void dispose() {
		super.dispose();
		this.mesh.dispose();
		this.multiShader.dispose();
	}

	private boolean missingTextureReported;

	/** A frame without its picture draws nothing; a null stored in a batch slot would fail the whole flush. */
	private void missingTexture() {
		if (!this.missingTextureReported) {
			this.missingTextureReported = true;
			Log.warn("FadingSpriteBatch: skipping a draw without a texture");
		}
	}

	/** The texture unit the next sprite of the texture is sampled from; flushes first when the batch has no room. */
	private int room(final Texture texture) {
		if (!isDrawing()) {
			throw new IllegalStateException("FadingSpriteBatch.begin must be called before draw.");
		}
		if (this.idx == this.vertices.length) {
			flush();
		}
		for (int i = 0; i < this.textureCount; i++) {
			if (this.textures[i] == texture) {
				return i;
			}
		}
		if (this.textureCount >= ((getShader() == this.multiShader) ? this.maxTextures : 1)) {
			flush();
		}
		this.textures[this.textureCount] = texture;
		return this.textureCount++;
	}

	private void quad(final Texture texture, final float x1, final float y1, final float u1, final float v1,
			final float x2, final float y2, final float u2, final float v2, final float x3, final float y3,
			final float u3, final float v3, final float x4, final float y4, final float u4, final float v4) {
		if (texture == null) {
			missingTexture();
			return;
		}
		final float unit = room(texture);
		final float color = super.getPackedColor();
		final float[] v = this.vertices;
		int i = this.idx;
		v[i++] = x1;
		v[i++] = y1;
		v[i++] = color;
		v[i++] = u1;
		v[i++] = v1;
		v[i++] = unit;
		v[i++] = x2;
		v[i++] = y2;
		v[i++] = color;
		v[i++] = u2;
		v[i++] = v2;
		v[i++] = unit;
		v[i++] = x3;
		v[i++] = y3;
		v[i++] = color;
		v[i++] = u3;
		v[i++] = v3;
		v[i++] = unit;
		v[i++] = x4;
		v[i++] = y4;
		v[i++] = color;
		v[i++] = u4;
		v[i++] = v4;
		v[i++] = unit;
		this.idx = i;
	}

	private void rect(final Texture texture, final float x, final float y, final float width, final float height,
			final float u, final float v, final float u2, final float v2) {
		final float fx2 = x + width;
		final float fy2 = y + height;
		quad(texture, x, y, u, v, x, fy2, u, v2, fx2, fy2, u2, v2, fx2, y, u2, v);
	}

	private void transformed(final Texture texture, final float x, final float y, final float originX,
			final float originY, final float width, final float height, final float scaleX, final float scaleY,
			final float rotation, final float u, final float v, final float u2, final float v2) {
		final float worldOriginX = x + originX;
		final float worldOriginY = y + originY;
		float fx = -originX;
		float fy = -originY;
		float fx2 = width - originX;
		float fy2 = height - originY;
		if ((scaleX != 1) || (scaleY != 1)) {
			fx *= scaleX;
			fy *= scaleY;
			fx2 *= scaleX;
			fy2 *= scaleY;
		}
		final float p1x = fx;
		final float p1y = fy;
		final float p2x = fx;
		final float p2y = fy2;
		final float p3x = fx2;
		final float p3y = fy2;
		final float p4x = fx2;
		final float p4y = fy;
		float x1;
		float y1;
		float x2;
		float y2;
		float x3;
		float y3;
		float x4;
		float y4;
		if (rotation != 0) {
			final float cos = MathUtils.cosDeg(rotation);
			final float sin = MathUtils.sinDeg(rotation);
			x1 = (cos * p1x) - (sin * p1y);
			y1 = (sin * p1x) + (cos * p1y);
			x2 = (cos * p2x) - (sin * p2y);
			y2 = (sin * p2x) + (cos * p2y);
			x3 = (cos * p3x) - (sin * p3y);
			y3 = (sin * p3x) + (cos * p3y);
			x4 = x1 + (x3 - x2);
			y4 = y3 - (y2 - y1);
		}
		else {
			x1 = p1x;
			y1 = p1y;
			x2 = p2x;
			y2 = p2y;
			x3 = p3x;
			y3 = p3y;
			x4 = p4x;
			y4 = p4y;
		}
		x1 += worldOriginX;
		y1 += worldOriginY;
		x2 += worldOriginX;
		y2 += worldOriginY;
		x3 += worldOriginX;
		y3 += worldOriginY;
		x4 += worldOriginX;
		y4 += worldOriginY;
		quad(texture, x1, y1, u, v, x2, y2, u, v2, x3, y3, u2, v2, x4, y4, u2, v);
	}

	@Override
	public void draw(final Texture texture, final float x, final float y, final float originX, final float originY,
			final float width, final float height, final float scaleX, final float scaleY, final float rotation,
			final int srcX, final int srcY, final int srcWidth, final int srcHeight, final boolean flipX,
			final boolean flipY) {
		if (texture == null) {
			missingTexture();
			return;
		}
		final float invTexWidth = 1.0f / texture.getWidth();
		final float invTexHeight = 1.0f / texture.getHeight();
		float u = srcX * invTexWidth;
		float v = (srcY + srcHeight) * invTexHeight;
		float u2 = (srcX + srcWidth) * invTexWidth;
		float v2 = srcY * invTexHeight;
		if (flipX) {
			final float tmp = u;
			u = u2;
			u2 = tmp;
		}
		if (flipY) {
			final float tmp = v;
			v = v2;
			v2 = tmp;
		}
		transformed(texture, x, y, originX, originY, width, height, scaleX, scaleY, rotation, u, v, u2, v2);
	}

	@Override
	public void draw(final Texture texture, final float x, final float y, final float width, final float height,
			final int srcX, final int srcY, final int srcWidth, final int srcHeight, final boolean flipX,
			final boolean flipY) {
		if (texture == null) {
			missingTexture();
			return;
		}
		final float invTexWidth = 1.0f / texture.getWidth();
		final float invTexHeight = 1.0f / texture.getHeight();
		float u = srcX * invTexWidth;
		float v = (srcY + srcHeight) * invTexHeight;
		float u2 = (srcX + srcWidth) * invTexWidth;
		float v2 = srcY * invTexHeight;
		if (flipX) {
			final float tmp = u;
			u = u2;
			u2 = tmp;
		}
		if (flipY) {
			final float tmp = v;
			v = v2;
			v2 = tmp;
		}
		rect(texture, x, y, width, height, u, v, u2, v2);
	}

	@Override
	public void draw(final Texture texture, final float x, final float y, final int srcX, final int srcY,
			final int srcWidth, final int srcHeight) {
		if (texture == null) {
			missingTexture();
			return;
		}
		final float invTexWidth = 1.0f / texture.getWidth();
		final float invTexHeight = 1.0f / texture.getHeight();
		rect(texture, x, y, srcWidth, srcHeight, srcX * invTexWidth, (srcY + srcHeight) * invTexHeight,
				(srcX + srcWidth) * invTexWidth, srcY * invTexHeight);
	}

	@Override
	public void draw(final Texture texture, final float x, final float y, final float width, final float height,
			final float u, final float v, final float u2, final float v2) {
		rect(texture, x, y, width, height, u, v, u2, v2);
	}

	@Override
	public void draw(final Texture texture, final float x, final float y) {
		if (texture == null) {
			missingTexture();
			return;
		}
		rect(texture, x, y, texture.getWidth(), texture.getHeight(), 0, 1, 1, 0);
	}

	@Override
	public void draw(final Texture texture, final float x, final float y, final float width, final float height) {
		rect(texture, x, y, width, height, 0, 1, 1, 0);
	}

	@Override
	public void draw(final TextureRegion region, final float x, final float y) {
		rect(region.getTexture(), x, y, region.getRegionWidth(), region.getRegionHeight(), region.getU(),
				region.getV2(), region.getU2(), region.getV());
	}

	@Override
	public void draw(final TextureRegion region, final float x, final float y, final float width,
			final float height) {
		rect(region.getTexture(), x, y, width, height, region.getU(), region.getV2(), region.getU2(), region.getV());
	}

	@Override
	public void draw(final TextureRegion region, final float x, final float y, final float originX,
			final float originY, final float width, final float height, final float scaleX, final float scaleY,
			final float rotation) {
		transformed(region.getTexture(), x, y, originX, originY, width, height, scaleX, scaleY, rotation,
				region.getU(), region.getV2(), region.getU2(), region.getV());
	}

	@Override
	public void draw(final TextureRegion region, final float x, final float y, final float originX,
			final float originY, final float width, final float height, final float scaleX, final float scaleY,
			final float rotation, final boolean clockwise) {
		final float u1;
		final float v1;
		final float u2;
		final float v2;
		final float u3;
		final float v3;
		final float u4;
		final float v4;
		if (clockwise) {
			u1 = region.getU2();
			v1 = region.getV2();
			u2 = region.getU();
			v2 = region.getV2();
			u3 = region.getU();
			v3 = region.getV();
			u4 = region.getU2();
			v4 = region.getV();
		}
		else {
			u1 = region.getU();
			v1 = region.getV();
			u2 = region.getU2();
			v2 = region.getV();
			u3 = region.getU2();
			v3 = region.getV2();
			u4 = region.getU();
			v4 = region.getV2();
		}
		final float worldOriginX = x + originX;
		final float worldOriginY = y + originY;
		float fx = -originX;
		float fy = -originY;
		float fx2 = width - originX;
		float fy2 = height - originY;
		if ((scaleX != 1) || (scaleY != 1)) {
			fx *= scaleX;
			fy *= scaleY;
			fx2 *= scaleX;
			fy2 *= scaleY;
		}
		float x1 = fx;
		float y1 = fy;
		float x2 = fx;
		float y2 = fy2;
		float x3 = fx2;
		float y3 = fy2;
		float x4 = fx2;
		float y4 = fy;
		if (rotation != 0) {
			final float cos = MathUtils.cosDeg(rotation);
			final float sin = MathUtils.sinDeg(rotation);
			final float r1x = (cos * x1) - (sin * y1);
			final float r1y = (sin * x1) + (cos * y1);
			final float r2x = (cos * x2) - (sin * y2);
			final float r2y = (sin * x2) + (cos * y2);
			final float r3x = (cos * x3) - (sin * y3);
			final float r3y = (sin * x3) + (cos * y3);
			x1 = r1x;
			y1 = r1y;
			x2 = r2x;
			y2 = r2y;
			x3 = r3x;
			y3 = r3y;
			x4 = x1 + (x3 - x2);
			y4 = y3 - (y2 - y1);
		}
		quad(region.getTexture(), x1 + worldOriginX, y1 + worldOriginY, u1, v1, x2 + worldOriginX, y2 + worldOriginY,
				u2, v2, x3 + worldOriginX, y3 + worldOriginY, u3, v3, x4 + worldOriginX, y4 + worldOriginY, u4, v4);
	}

	@Override
	public void draw(final TextureRegion region, final float width, final float height, final Affine2 transform) {
		final float x1 = transform.m02;
		final float y1 = transform.m12;
		final float x2 = (transform.m01 * height) + transform.m02;
		final float y2 = (transform.m11 * height) + transform.m12;
		final float x3 = (transform.m00 * width) + (transform.m01 * height) + transform.m02;
		final float y3 = (transform.m10 * width) + (transform.m11 * height) + transform.m12;
		final float x4 = (transform.m00 * width) + transform.m02;
		final float y4 = (transform.m10 * width) + transform.m12;
		quad(region.getTexture(), x1, y1, region.getU(), region.getV(), x2, y2, region.getU(), region.getV2(), x3, y3,
				region.getU2(), region.getV2(), x4, y4, region.getU2(), region.getV());
	}

	@Override
	public void draw(final Texture texture, final float[] spriteVertices, final int offset, final int count) {
		final boolean fade = this.alphaScale < 1f;
		for (int src = offset, end = offset + count; src < end; src += 4 * VERTEX_SIZE) {
			final int unit = room(texture);
			final float[] v = this.vertices;
			int i = this.idx;
			for (int k = 0; k < 4; k++) {
				final int s = src + (k * VERTEX_SIZE);
				v[i++] = spriteVertices[s];
				v[i++] = spriteVertices[s + 1];
				// Text and sprites carry their own vertex colors, which the batch tint does not reach.
				float color = spriteVertices[s + COLOR_OFFSET];
				if (fade) {
					final int abgr = NumberUtils.floatToIntColor(color);
					final int alpha = (int) (((abgr >>> 24) & 0xff) * this.alphaScale);
					color = NumberUtils.intToFloatColor((abgr & 0x00ffffff) | (alpha << 24));
				}
				v[i++] = color;
				v[i++] = spriteVertices[s + 3];
				v[i++] = spriteVertices[s + 4];
				v[i++] = unit;
			}
			this.idx = i;
		}
	}

	public float getAlphaScale() {
		return this.alphaScale;
	}

	public void setAlphaScale(final float alphaScale) {
		this.alphaScale = alphaScale;
		final Color color = unscaled();
		super.setColor(color.r, color.g, color.b, color.a * alphaScale);
	}

	private Color unscaled() {
		// SpriteBatch's constructor may set a color before this class's fields exist.
		if (this.unscaledColor == null) {
			this.unscaledColor = new Color(1, 1, 1, 1);
		}
		return this.unscaledColor;
	}

	@Override
	public void setColor(final Color tint) {
		unscaled().set(tint);
		super.setColor(tint.r, tint.g, tint.b, tint.a * this.alphaScale);
	}

	@Override
	public void setColor(final float r, final float g, final float b, final float a) {
		unscaled().set(r, g, b, a);
		super.setColor(r, g, b, a * this.alphaScale);
	}

	@Override
	public void setPackedColor(final float packedColor) {
		final Color color = unscaled();
		Color.abgr8888ToColor(color, packedColor);
		super.setColor(color.r, color.g, color.b, color.a * this.alphaScale);
	}

	@Override
	public Color getColor() {
		return unscaled();
	}

	@Override
	public float getPackedColor() {
		return unscaled().toFloatBits();
	}
}
