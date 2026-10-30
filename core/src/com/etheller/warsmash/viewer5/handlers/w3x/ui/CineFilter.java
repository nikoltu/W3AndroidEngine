package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CBlendMode;

/**
 * The cinematic filter (SetCineFilter* and DisplayCineFilter): a full screen texture over the world and under the
 * interface, whose color and texture coordinates go from the start to the end values over the duration.
 */
public final class CineFilter {
	private String texturePath;
	private Texture texture;
	private boolean textureLoaded;
	private CBlendMode blendMode = CBlendMode.BLEND;
	private final float[] startUV = { 0, 0, 1, 1 };
	private final float[] endUV = { 0, 0, 1, 1 };
	private final int[] startColor = { 255, 255, 255, 255 };
	private final int[] endColor = { 255, 255, 255, 255 };
	private float duration;
	private float elapsed;
	private boolean displayed;

	public void setTexture(final String texturePath) {
		this.texturePath = texturePath;
		this.texture = null;
		this.textureLoaded = false;
	}

	public void setBlendMode(final CBlendMode blendMode) {
		this.blendMode = blendMode == null ? CBlendMode.BLEND : blendMode;
	}

	public void setStartUV(final float minU, final float minV, final float maxU, final float maxV) {
		set(this.startUV, minU, minV, maxU, maxV);
	}

	public void setEndUV(final float minU, final float minV, final float maxU, final float maxV) {
		set(this.endUV, minU, minV, maxU, maxV);
	}

	public void setStartColor(final int red, final int green, final int blue, final int alpha) {
		set(this.startColor, red, green, blue, alpha);
	}

	public void setEndColor(final int red, final int green, final int blue, final int alpha) {
		set(this.endColor, red, green, blue, alpha);
	}

	public void setDuration(final float duration) {
		this.duration = duration;
	}

	public void display(final boolean display) {
		this.displayed = display;
		this.elapsed = 0;
	}

	public boolean isDisplayed() {
		return this.displayed;
	}

	public void update(final float deltaTime) {
		if (this.displayed) {
			this.elapsed += deltaTime;
		}
	}

	public void render(final SpriteBatch batch, final GameUI gameUI, final float x, final float y, final float width,
			final float height) {
		if (!this.displayed) {
			return;
		}
		if (!this.textureLoaded) {
			this.textureLoaded = true;
			if ((this.texturePath != null) && !this.texturePath.isEmpty()) {
				this.texture = gameUI.loadTexture(this.texturePath);
			}
		}
		final float t = this.duration > 0 ? Math.min(1, this.elapsed / this.duration) : 1;
		batch.setColor(lerp(this.startColor[0], this.endColor[0], t) / 255f,
				lerp(this.startColor[1], this.endColor[1], t) / 255f,
				lerp(this.startColor[2], this.endColor[2], t) / 255f,
				lerp(this.startColor[3], this.endColor[3], t) / 255f);
		switch (this.blendMode) {
		case NONE:
			batch.disableBlending();
			break;
		case ADDITIVE:
			batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
			break;
		case MODULATE:
			batch.setBlendFunction(GL20.GL_ZERO, GL20.GL_SRC_COLOR);
			break;
		case MODULATE_2X:
			batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_SRC_COLOR);
			break;
		default:
			break;
		}
		final Texture drawn = this.texture != null ? this.texture : gameUI.loadTexture("Textures\\white.blp");
		if (drawn != null) {
			batch.draw(drawn, x, y, width, height, lerp(this.startUV[0], this.endUV[0], t),
					lerp(this.startUV[3], this.endUV[3], t), lerp(this.startUV[2], this.endUV[2], t),
					lerp(this.startUV[1], this.endUV[1], t));
		}
		batch.enableBlending();
		batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		batch.setColor(1, 1, 1, 1);
	}

	private static float lerp(final float from, final float to, final float t) {
		return from + ((to - from) * t);
	}

	private static void set(final float[] values, final float a, final float b, final float c, final float d) {
		values[0] = a;
		values[1] = b;
		values[2] = c;
		values[3] = d;
	}

	private static void set(final int[] values, final int a, final int b, final int c, final int d) {
		values[0] = a;
		values[1] = b;
		values[2] = c;
		values[3] = d;
	}
}
