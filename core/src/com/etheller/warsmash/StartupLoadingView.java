package com.etheller.warsmash;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.ImageUtils;

/**
 * Drawn while the menu is being built, so startup is not a black screen. Until the game data is open only a plain bar
 * is available; after that the generic loading backdrop and the load bar textures of the game itself are used.
 */
final class StartupLoadingView {
	private static final String GENERIC = "UI\\Glues\\Loading\\Load-Generic\\Generic-Loading-";
	private static final String BAR = "UI\\Glues\\Loading\\LoadBar\\Loading-Bar";
	private static final String MODEL_TEXTURES = "Textures\\Loading-Bar";
	// Layout of UI\Glues\Loading\LoadBar\LoadBar.mdx, the bar of the map loading screen: the border spans 0.458 of the
	// 0.8 wide 4:3 screen at 8:1, and every other layer covers its own part of it, given as left, right, bottom and top
	// insets in fractions of the border.
	private static final float BAR_WIDTH_OF_4_3 = 0.5726f;
	private static final float BAR_ASPECT = 8.008f;
	private static final float BAR_BOTTOM_OF_HEIGHT = 0.0548f;
	private static final float[] CHANNEL = { 0.0593f, 0.0642f, 0.2692f, 0.2710f };
	private static final float[] GLASS = { 0.0699f, 0.0685f, 0.5175f, 0.3409f };

	private final SpriteBatch batch = new SpriteBatch();
	private final ShapeRenderer shapes = new ShapeRenderer();
	private Texture[] backdrop;
	private Texture barBackground;
	private Texture barFill;
	private Texture barGlass;
	private Texture barBorder;

	void loadGameTextures(final DataSource dataSource) {
		final Texture[] quarters = { load(dataSource, GENERIC + "TopLeft.blp"),
				load(dataSource, GENERIC + "TopRight.blp"), load(dataSource, GENERIC + "BotLeft.blp"),
				load(dataSource, GENERIC + "BotRight.blp") };
		this.backdrop = (quarters[0] != null) && (quarters[1] != null) && (quarters[2] != null)
				&& (quarters[3] != null) ? quarters : null;
		this.barBackground = loadEither(dataSource, MODEL_TEXTURES + "Background.blp", BAR + "Background.blp");
		this.barFill = load(dataSource, BAR + "Fill.blp");
		this.barGlass = loadEither(dataSource, MODEL_TEXTURES + "Glass.blp", BAR + "Glass.blp");
		this.barBorder = load(dataSource, BAR + "Border.blp");
	}

	private static Texture loadEither(final DataSource dataSource, final String path, final String fallback) {
		final Texture texture = load(dataSource, path);
		return texture != null ? texture : load(dataSource, fallback);
	}

	private static Texture load(final DataSource dataSource, final String path) {
		try {
			return ImageUtils.getAnyExtensionTexture(dataSource, path);
		}
		catch (final RuntimeException e) {
			return null;
		}
	}

	void render(final float progress) {
		final int width = Gdx.graphics.getWidth();
		final int height = Gdx.graphics.getHeight();
		Gdx.gl.glViewport(0, 0, width, height);
		Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		this.batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
		this.shapes.setProjectionMatrix(this.batch.getProjectionMatrix());

		final float fourThirdsWidth = Math.min(width, (height * 4f) / 3f);
		final float barWidth = fourThirdsWidth * BAR_WIDTH_OF_4_3;
		final float barHeight = barWidth / BAR_ASPECT;
		final float barX = (width - barWidth) / 2;
		final float barY = height * BAR_BOTTOM_OF_HEIGHT;
		final float clamped = Math.max(0, Math.min(1, progress));

		this.batch.begin();
		this.batch.setColor(Color.WHITE);
		if (this.backdrop != null) {
			drawBackdrop(width, height);
		}
		final boolean textured = (this.barBackground != null) && (this.barFill != null);
		if (textured) {
			drawPart(this.barBackground, barX, barY, barWidth, barHeight, CHANNEL, 1);
			drawPart(this.barFill, barX, barY, barWidth, barHeight, CHANNEL, clamped);
			if (this.barGlass != null) {
				drawPart(this.barGlass, barX, barY, barWidth, barHeight, GLASS, 1);
			}
			if (this.barBorder != null) {
				this.batch.draw(this.barBorder, barX, barY, barWidth, barHeight);
			}
		}
		this.batch.end();

		if (!textured) {
			Gdx.gl.glEnable(GL20.GL_BLEND);
			this.shapes.begin(ShapeType.Filled);
			this.shapes.setColor(0.05f, 0.06f, 0.09f, 1);
			this.shapes.rect(barX, barY, barWidth, barHeight);
			this.shapes.setColor(0.22f, 0.45f, 0.78f, 1);
			final float inset = Math.max(2, barHeight * 0.12f);
			this.shapes.rect(barX + inset, barY + inset, (barWidth - (2 * inset)) * clamped, barHeight - (2 * inset));
			this.shapes.end();
			this.shapes.begin(ShapeType.Line);
			this.shapes.setColor(0.79f, 0.64f, 0.29f, 1);
			this.shapes.rect(barX, barY, barWidth, barHeight);
			this.shapes.end();
		}
	}

	private void drawPart(final Texture texture, final float barX, final float barY, final float barWidth,
			final float barHeight, final float[] insets, final float shown) {
		final float x = barX + (barWidth * insets[0]);
		final float y = barY + (barHeight * insets[2]);
		final float partWidth = barWidth * (1 - insets[0] - insets[1]);
		final float partHeight = barHeight * (1 - insets[2] - insets[3]);
		this.batch.draw(texture, x, y, partWidth * shown, partHeight, 0, 0, Math.round(texture.getWidth() * shown),
				texture.getHeight(), false, false);
	}

	/** The four quarters form one 4:3 picture; it is scaled to the full screen width and cropped, like the map loading screen. */
	private void drawBackdrop(final int width, final int height) {
		final Texture topLeft = this.backdrop[0];
		final Texture topRight = this.backdrop[1];
		final Texture botLeft = this.backdrop[2];
		final Texture botRight = this.backdrop[3];
		final float sourceWidth = topLeft.getWidth() + topRight.getWidth();
		final float sourceHeight = (sourceWidth * 3) / 4;
		final float scale = Math.max(width / sourceWidth, height / sourceHeight);
		final float drawWidth = sourceWidth * scale;
		final float drawHeight = sourceHeight * scale;
		final float x = (width - drawWidth) / 2;
		final float y = (height - drawHeight) / 2;
		final float topHeight = topLeft.getHeight() * scale;
		final float bottomHeight = drawHeight - topHeight;
		final float leftWidth = topLeft.getWidth() * scale;
		final float top = y + bottomHeight;
		this.batch.draw(topLeft, x, top, leftWidth, topHeight);
		this.batch.draw(topRight, x + leftWidth, top, drawWidth - leftWidth, topHeight);
		final int botSourceHeight = Math.round(bottomHeight / scale);
		this.batch.draw(botLeft, x, y, leftWidth, bottomHeight, 0, 0, botLeft.getWidth(),
				Math.min(botSourceHeight, botLeft.getHeight()), false, false);
		this.batch.draw(botRight, x + leftWidth, y, drawWidth - leftWidth, bottomHeight, 0, 0, botRight.getWidth(),
				Math.min(botSourceHeight, botRight.getHeight()), false, false);
	}

	void dispose() {
		this.batch.dispose();
		this.shapes.dispose();
		final Texture[] owned = { this.barBackground, this.barFill, this.barGlass, this.barBorder };
		for (final Texture texture : owned) {
			if (texture != null) {
				texture.dispose();
			}
		}
		if (this.backdrop != null) {
			for (final Texture texture : this.backdrop) {
				texture.dispose();
			}
		}
	}
}
