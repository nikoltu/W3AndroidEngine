package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.Arrays;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityEntangledMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityOverlayedMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

public class MeleeUIMinimap {
	private static final float HERO_STEP = 0.01f;
	private final Rectangle minimap;
	private final Rectangle minimapFilledArea;
	private final Texture minimapTexture;
	private final Rectangle playableMapArea;
	private final Texture[] teamColors;
	private final Texture[] specialIcons;
	private float heroAlpha = 0.90f;
	private static final int ICON_VALUES = 7;
	private Texture[] iconTextures = new Texture[64];
	private float[] iconValues = new float[64 * ICON_VALUES];
	private int iconCount;
	private byte polarity = -1;
	private boolean visible = true;
	private final float[] cameraFootprint = new float[8];
	private Texture lineTexture;
	// The fog was drawn as one sprite per fog cell every frame, thousands of them, which phones could not keep up
	// with; it is now painted into a texture a few times a second and drawn as one sprite.
	private static final long FOG_REFRESH_MILLIS = 250;
	private Pixmap fogPixmap;
	private Texture fogTexture;
	private long fogRefreshedMillis;

	public MeleeUIMinimap(final Rectangle displayArea, final Rectangle playableMapArea, final Texture minimapTexture,
			final Texture[] teamColors, final Texture[] specialIcons) {
		this.playableMapArea = playableMapArea;
		this.minimapTexture = minimapTexture;
		this.teamColors = teamColors;
		this.minimap = displayArea;
		this.specialIcons = specialIcons;
		final float worldWidth = playableMapArea.getWidth();
		final float worldHeight = playableMapArea.getHeight();
		final float worldSize = Math.max(worldWidth, worldHeight);
		final float minimapFilledWidth = (worldWidth / worldSize) * this.minimap.width;
		final float minimapFilledHeight = (worldHeight / worldSize) * this.minimap.height;

		this.minimapFilledArea = new Rectangle(this.minimap.x + ((this.minimap.width - minimapFilledWidth) / 2),
				this.minimap.y + ((this.minimap.height - minimapFilledHeight) / 2), minimapFilledWidth,
				minimapFilledHeight);
	}

	public void render(final CSimulation game, final SpriteBatch batch, final Iterable<RenderUnit> units,
			final PathingGrid pathingGrid, final CPlayerFogOfWar fogOfWar, final CPlayer player) {
		if (!this.visible) {
			return;
		}
		batch.draw(this.minimapTexture, this.minimap.x, this.minimap.y, this.minimap.width, this.minimap.height);
		final Color og = batch.getColor();

		final int minX = pathingGrid.getFogOfWarIndexX(this.playableMapArea.getX());
		final int minY = pathingGrid.getFogOfWarIndexY(this.playableMapArea.getY());
		final int maxX = pathingGrid.getFogOfWarIndexX(this.playableMapArea.getX() + this.playableMapArea.getWidth());
		final int maxY = pathingGrid.getFogOfWarIndexY(this.playableMapArea.getY() + this.playableMapArea.getHeight());
		final float mapXMod = this.minimapFilledArea.width / (maxX - minX);
		final float mapYMod = this.minimapFilledArea.height / (maxY - minY);

		final int width = maxX - minX;
		final int height = maxY - minY;
		if ((width > 0) && (height > 0)) {
			if ((this.fogPixmap == null) || (this.fogPixmap.getWidth() != width)
					|| (this.fogPixmap.getHeight() != height)) {
				if (this.fogTexture != null) {
					this.fogTexture.dispose();
					this.fogPixmap.dispose();
				}
				this.fogPixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
				this.fogPixmap.setBlending(Pixmap.Blending.None);
				this.fogTexture = new Texture(this.fogPixmap);
				this.fogTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
				this.fogRefreshedMillis = 0;
			}
			final long now = System.currentTimeMillis();
			if ((now - this.fogRefreshedMillis) >= FOG_REFRESH_MILLIS) {
				this.fogRefreshedMillis = now;
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						final CFogState state = fogOfWar.getFogState(game, x + minX, y + minY);
						final int rgba;
						if (CFogState.FOGGED.equals(state)) {
							rgba = 0x0000007F;
						} else if (CFogState.MASKED.equals(state)) {
							rgba = 0x000000FF;
						} else {
							rgba = 0;
						}
						// Pixmap rows run top down, the minimap's fog cells bottom up.
						this.fogPixmap.drawPixel(x, height - 1 - y, rgba);
					}
				}
				this.fogTexture.draw(this.fogPixmap, 0, 0);
			}
			batch.setColor(1f, 1f, 1f, 1f);
			batch.draw(this.fogTexture, this.minimapFilledArea.x, this.minimapFilledArea.y, width * mapXMod,
					height * mapYMod);
		}

		drawCameraFootprint(batch);
		batch.setColor(1, 1, 1, 1);

		this.heroAlpha += HERO_STEP * this.polarity;
		if ((this.heroAlpha <= 0.5) || (this.heroAlpha >= 0.95)) {
			this.polarity *= -1;
		}

		this.iconCount = 0;
		for (final RenderUnit unit : units) {
			final CUnit simUnit = unit.getSimulationUnit();
			int dimensions = 4;
			if (!(simUnit.isHidden() || simUnit.isHideMinimapIcon() || simUnit.isDead())
					&& simUnit.isVisible(game, player.getId())) {
				float red = 1f;
				float green = 1f;
				float alpha = 1f;
				int layer = 1;
				final Texture minimapIcon;
				if (simUnit.getGoldMineData() != null) {
					minimapIcon = this.specialIcons[0];
					dimensions = 21;
				} else if (simUnit.getOverlayedGoldMineData() != null) {
					final CAbilityOverlayedMine overlayedGoldMineData = simUnit.getOverlayedGoldMineData();
					if (overlayedGoldMineData instanceof CAbilityEntangledMine) {
						minimapIcon = this.specialIcons[3];
					} else {
						minimapIcon = this.specialIcons[4];
					}
					dimensions = 21;
				} else if (simUnit.getUnitType().isNeutralBuildingShowMinimapIcon()) {
					minimapIcon = this.specialIcons[1];
					dimensions = 21;
				} else if (simUnit.isHero()) {
					if (!player.hasAlliance(simUnit.getFakePlayerIndex(), CAllianceType.PASSIVE)) {
						green = 0.2f;
					}
					alpha = this.heroAlpha;
					layer = 2;
					minimapIcon = this.specialIcons[2];
					dimensions = 28;
				} else {
					if (simUnit.isBuilding()) {
						dimensions = 10;
					}
					layer = 0;
					if (simUnit.getFakePlayerIndex() == player.getId()) {
						minimapIcon = this.specialIcons[5];
					}
					else {
						minimapIcon = this.teamColors[unit.getSimulationUnit().getFakePlayerIndex()];
					}
				}
				final int offset = dimensions / 2;
				addIcon(minimapIcon, layer, (this.minimapFilledArea.x + (((unit.location[0] - this.playableMapArea.getX())
						/ (this.playableMapArea.getWidth())) * this.minimapFilledArea.width)) - offset,
						(this.minimapFilledArea.y + (((unit.location[1] - this.playableMapArea.getY())
								/ (this.playableMapArea.getHeight())) * this.minimapFilledArea.height)) - offset,
						dimensions, red, green, alpha);
			}
		}
		drawIcons(batch);
	}

	// Icons are drawn grouped by texture, since every texture change is a separate draw call and the dots of
	// different players otherwise alternate unit by unit.
	private void addIcon(final Texture texture, final int layer, final float x, final float y, final int dimensions,
			final float red, final float green, final float alpha) {
		final int index = this.iconCount++;
		if (index >= this.iconTextures.length) {
			final int size = this.iconTextures.length * 2;
			this.iconTextures = Arrays.copyOf(this.iconTextures, size);
			this.iconValues = Arrays.copyOf(this.iconValues, size * ICON_VALUES);
		}
		this.iconTextures[index] = texture;
		final float[] values = this.iconValues;
		final int base = index * ICON_VALUES;
		values[base] = layer;
		values[base + 1] = x;
		values[base + 2] = y;
		values[base + 3] = dimensions;
		values[base + 4] = red;
		values[base + 5] = green;
		values[base + 6] = alpha;
	}

	private void drawIcons(final SpriteBatch batch) {
		final Texture[] textures = this.iconTextures;
		final float[] values = this.iconValues;
		final int count = this.iconCount;
		for (int layer = 0; layer <= 2; layer++) {
			for (int i = 0; i < count; i++) {
				final Texture texture = textures[i];
				if ((texture == null) || (values[i * ICON_VALUES] != layer)) {
					continue;
				}
				for (int j = i; j < count; j++) {
					if ((textures[j] == texture) && (values[j * ICON_VALUES] == layer)) {
						final int base = j * ICON_VALUES;
						batch.setColor(values[base + 4], values[base + 5], values[base + 5], values[base + 6]);
						batch.draw(texture, values[base + 1], values[base + 2], values[base + 3], values[base + 3]);
						textures[j] = null;
					}
				}
			}
		}
		batch.setColor(1, 1, 1, 1);
	}

	public float[] getCameraFootprint() {
		return this.cameraFootprint;
	}

	private void drawCameraFootprint(final SpriteBatch batch) {
		if (this.lineTexture == null) {
			final Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
			pixmap.setColor(Color.WHITE);
			pixmap.fill();
			this.lineTexture = new Texture(pixmap);
			pixmap.dispose();
		}
		batch.setColor(1, 1, 1, 1);
		for (int i = 0; i < 4; i++) {
			final int next = (i + 1) % 4;
			drawLine(batch, toMinimapX(this.cameraFootprint[i * 2]), toMinimapY(this.cameraFootprint[(i * 2) + 1]),
					toMinimapX(this.cameraFootprint[next * 2]), toMinimapY(this.cameraFootprint[(next * 2) + 1]));
		}
	}

	public float toMinimapX(final float worldX) {
		final float x = this.minimapFilledArea.x + (((worldX - this.playableMapArea.getX())
				/ this.playableMapArea.getWidth()) * this.minimapFilledArea.width);
		return Math.max(this.minimapFilledArea.x, Math.min(this.minimapFilledArea.x + this.minimapFilledArea.width, x));
	}

	public float toMinimapY(final float worldY) {
		final float y = this.minimapFilledArea.y + (((worldY - this.playableMapArea.getY())
				/ this.playableMapArea.getHeight()) * this.minimapFilledArea.height);
		return Math.max(this.minimapFilledArea.y,
				Math.min(this.minimapFilledArea.y + this.minimapFilledArea.height, y));
	}

	private void drawLine(final SpriteBatch batch, final float x1, final float y1, final float x2, final float y2) {
		final float dx = x2 - x1;
		final float dy = y2 - y1;
		final float length = (float) Math.sqrt((dx * dx) + (dy * dy));
		final float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
		batch.draw(this.lineTexture, x1, y1, 0, 0.5f, length, 1f, 1, 1, angle, 0, 0, 1, 1, false, false);
	}

	public Vector2 getWorldPointFromScreen(final float screenX, final float screenY) {
		final Rectangle filledArea = this.minimapFilledArea;
		final float clickX = (screenX - filledArea.x) / filledArea.width;
		final float clickY = (screenY - filledArea.y) / filledArea.height;
		final float worldX = (clickX * this.playableMapArea.width) + this.playableMapArea.x;
		final float worldY = (clickY * this.playableMapArea.height) + this.playableMapArea.y;
		return new Vector2(worldX, worldY);

	}

	public boolean containsMouse(final float x, final float y) {
		return this.minimapFilledArea.contains(x, y);
	}

	public void setVisible(final boolean visible) {
		this.visible = visible;
	}
}
