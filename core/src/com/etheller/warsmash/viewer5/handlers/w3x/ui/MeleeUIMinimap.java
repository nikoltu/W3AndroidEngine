package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderFogGhost;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

public class MeleeUIMinimap {
	private boolean observerView;
	private AllyColorFilter allyColorFilter;
	private Predicate<CUnit> heroAlertShown;
	private Supplier<String> altIconPath;
	private Function<String, Texture> altIconLoader;
	private String loadedAltIconPath;
	private Texture loadedAltIcon;
	private Supplier<String> terrainTexturePath;
	private String loadedTerrainTexturePath;
	private Texture loadedTerrainTexture;
	private Supplier<List<ScriptMinimapIcon>> scriptIcons;
	private final Map<String, Texture> scriptIconTextures = new HashMap<>();
	private static final float HERO_STEP = 0.01f;
	private final Rectangle minimap;
	private final Rectangle minimapFilledArea;
	private final Texture minimapTexture;
	private final Rectangle playableMapArea;
	private final Texture[] teamColors;
	private final Texture[] specialIcons;
	private float heroAlpha = 0.90f;
	private static final int ICON_VALUES = 8;
	private Texture[] iconTextures = new Texture[64];
	private float[] iconValues = new float[64 * ICON_VALUES];
	private int iconCount;
	private byte polarity = -1;
	private boolean visible = true;
	private boolean scriptVisible = true;
	private boolean terrainVisible = true;
	private boolean maskedAreasPartiallyVisible;
	private final float[] cameraFootprint = new float[8];
	// Measured on 1.29: a remembered building's player-coloured dot in the fog is drawn at 75 % of its colour.
	private static final float FOGGED_ICON_SHADE = 0.75f;
	private Texture lineTexture;
	// The fog was drawn as one sprite per fog cell every frame, thousands of them, which phones could not keep up
	// with; it is now painted into a texture a few times a second and drawn as one sprite.
	private static final long FOG_REFRESH_MILLIS = 250;
	private Pixmap fogPixmap;
	private Texture fogTexture;
	private long fogRefreshedMillis;
	private final byte[] fogAlphaByCell = new byte[256];
	private byte[] fogBytes;

	public MeleeUIMinimap(final Rectangle displayArea, final Rectangle playableMapArea, final Texture minimapTexture,
			final Texture[] teamColors, final Texture[] specialIcons) {
		this.playableMapArea = playableMapArea;
		this.minimapTexture = minimapTexture;
		this.teamColors = teamColors;
		this.minimap = displayArea;
		this.specialIcons = specialIcons;
		this.minimapFilledArea = new Rectangle();
		layoutFilledArea();
	}

	private void layoutFilledArea() {
		final float worldWidth = this.playableMapArea.getWidth();
		final float worldHeight = this.playableMapArea.getHeight();
		final float worldSize = Math.max(worldWidth, worldHeight);
		final float minimapFilledWidth = (worldWidth / worldSize) * this.minimap.width;
		final float minimapFilledHeight = (worldHeight / worldSize) * this.minimap.height;
		this.minimapFilledArea.set(this.minimap.x + ((this.minimap.width - minimapFilledWidth) / 2),
				this.minimap.y + ((this.minimap.height - minimapFilledHeight) / 2), minimapFilledWidth,
				minimapFilledHeight);
	}

	/** The minimap frame's area in interface units. */
	public Rectangle getDisplayArea() {
		return this.minimap;
	}

	/** Where the minimap sits in the interface: a map script moves and resizes the minimap frame. */
	public void setDisplayArea(final float x, final float y, final float width, final float height) {
		if ((x != this.minimap.x) || (y != this.minimap.y) || (width != this.minimap.width)
				|| (height != this.minimap.height)) {
			this.minimap.set(x, y, width, height);
			layoutFilledArea();
		}
	}

	public Rectangle getDisplayArea(final Rectangle out) {
		return out.set(this.minimap);
	}

	/** A map script's hiding of the minimap frame, apart from the interface's own showing and hiding. */
	public void setScriptVisible(final boolean scriptVisible) {
		this.scriptVisible = scriptVisible;
	}

	public void setObserverView(final boolean observerView) {
		this.observerView = observerView;
	}

	/** While a hurt hero's alert flashes on, its mark is drawn red instead. */
	public void setHeroAlertShown(final Predicate<CUnit> heroAlertShown) {
		this.heroAlertShown = heroAlertShown;
	}

	public void setAllyColorFilter(final AllyColorFilter allyColorFilter) {
		this.allyColorFilter = allyColorFilter;
	}

	/** Where the map's alternate icon (SetAltMinimapIcon) comes from: its current path and a texture loader. */
	public void setAltIconSource(final Supplier<String> altIconPath, final Function<String, Texture> loader) {
		this.altIconPath = altIconPath;
		this.altIconLoader = loader;
	}

	/** Where a script's picture for the minimap's terrain (BlzChangeMinimapTerrainTex) is named; it loads like the icons do. */
	public void setTerrainTextureSource(final Supplier<String> terrainTexturePath) {
		this.terrainTexturePath = terrainTexturePath;
	}

	private Texture terrainTexture() {
		final String path = (this.terrainTexturePath == null) || (this.altIconLoader == null) ? null
				: this.terrainTexturePath.get();
		if ((path == null) || path.isEmpty()) {
			return this.minimapTexture;
		}
		if (!path.equals(this.loadedTerrainTexturePath)) {
			this.loadedTerrainTexturePath = path;
			this.loadedTerrainTexture = loadScriptIconTexture(path);
		}
		return this.loadedTerrainTexture == null ? this.minimapTexture : this.loadedTerrainTexture;
	}

	/** The icons map scripts created (CreateMinimapIcon*); their textures come from the alternate icon's loader. */
	public void setScriptIconSource(final Supplier<List<ScriptMinimapIcon>> scriptIcons) {
		this.scriptIcons = scriptIcons;
	}

	private Texture scriptIconTexture(final String path) {
		if ((path == null) || path.isEmpty() || (this.altIconLoader == null)) {
			return null;
		}
		if (!this.scriptIconTextures.containsKey(path)) {
			Texture texture = loadScriptIconTexture(path);
			if ((texture == null) && (path.toLowerCase(Locale.ROOT).endsWith(".mdx") || path.toLowerCase(Locale.ROOT).endsWith(".mdl"))) {
				// The Reforged data names the icon's model; the sprite batch can only draw the texture of the same name.
				texture = loadScriptIconTexture(path.substring(0, path.length() - 4) + ".blp");
			}
			this.scriptIconTextures.put(path, texture);
		}
		return this.scriptIconTextures.get(path);
	}

	private Texture loadScriptIconTexture(final String path) {
		try {
			return this.altIconLoader.apply(path);
		}
		catch (final RuntimeException e) {
			return null;
		}
	}

	private Texture altIcon() {
		final String path = this.altIconPath == null ? null : this.altIconPath.get();
		if ((path == null) || path.isEmpty()) {
			return null;
		}
		if (!path.equals(this.loadedAltIconPath)) {
			this.loadedAltIconPath = path;
			try {
				this.loadedAltIcon = this.altIconLoader.apply(path);
			}
			catch (final IllegalStateException e) {
				// HumanX05 names UI\Minimap\Minimap-Special, which the 1.29 data does not have: no icon then.
				this.loadedAltIcon = null;
			}
		}
		return this.loadedAltIcon;
	}

	public void render(final CSimulation game, final SpriteBatch batch, final Iterable<RenderUnit> units,
			final List<RenderFogGhost> fogGhosts, final PathingGrid pathingGrid, final CPlayerFogOfWar fogOfWar,
			final CPlayer player) {
		if (!this.visible || !this.scriptVisible) {
			return;
		}
		// A map without a minimap picture has no terrain texture either; it shows black like the terrain-off view.
		final Texture terrain = this.terrainVisible ? terrainTexture() : null;
		if (terrain != null) {
			batch.draw(terrain, this.minimap.x, this.minimap.y, this.minimap.width, this.minimap.height);
		}
		else {
			batch.setColor(0, 0, 0, 1);
			batch.draw(whiteTexture(), this.minimap.x, this.minimap.y, this.minimap.width, this.minimap.height);
			batch.setColor(1, 1, 1, 1);
		}
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
				// A put per pixel on the pixmap's direct buffer is slow on Android: the pixels are filled in a plain array
				// and copied at once, and the settings-dependent look is worked out once per stored value, not per cell.
				final byte[] cells = fogOfWar.getFogOfWarBuffer().array();
				final int cellsWide = fogOfWar.getWidth();
				for (int value = 0; value < 256; value++) {
					final CFogState state = CFogState.getByMask(game.getFogStateFromSettings((byte) value));
					// Measured on 1.29: fog leaves 75 % of the minimap's brightness, a partially visible mask 38 %.
					if (CFogState.FOGGED.equals(state)) {
						this.fogAlphaByCell[value] = (byte) 0x3F;
					} else if (CFogState.MASKED.equals(state)) {
						this.fogAlphaByCell[value] = this.maskedAreasPartiallyVisible ? (byte) 0x9F : (byte) 0xFF;
					} else {
						this.fogAlphaByCell[value] = 0;
					}
				}
				if ((this.fogBytes == null) || (this.fogBytes.length != width * height * 4)) {
					this.fogBytes = new byte[width * height * 4];
				}
				final byte[] fogBytes = this.fogBytes;
				for (int y = 0; y < height; y++) {
					// Pixmap rows run top down, the minimap's fog cells bottom up.
					int offset = ((height - 1 - y) * width * 4) + 3;
					final int row = (y + minY) * cellsWide;
					for (int x = 0; x < width; x++) {
						final int cell = row + x + minX;
						fogBytes[offset] = (cell >= 0) && (cell < cells.length) ? this.fogAlphaByCell[cells[cell] & 0xFF]
								: 0;
						offset += 4;
					}
				}
				final ByteBuffer fogPixels = this.fogPixmap.getPixels();
				fogPixels.position(0);
				fogPixels.put(fogBytes);
				fogPixels.position(0);
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
			final boolean unitVisible = this.observerView || simUnit.isVisible(game, player.getId());
			if (!(simUnit.isHidden() || simUnit.isHideMinimapIcon() || simUnit.isDead())
					&& (unitVisible || (!unit.hasShownFogGhost() && isExploredLandmark(game, simUnit,
							unit.getMinimapLandmarkKind(), pathingGrid, fogOfWar, this.maskedAreasPartiallyVisible)))) {
				addUnitIcon(game, simUnit, unit.getMinimapLandmarkKind(), simUnit.isUsesAltMinimapIcon(),
						simUnit.isBuilding(), simUnit.getFakePlayerIndex(),
						unit.location[0], unit.location[1], player, 1f);
			}
		}
		for (int i = 0, n = fogGhosts.size(); i < n; i++) {
			final RenderFogGhost ghost = fogGhosts.get(i);
			if (!ghost.isMinimapIconHidden() && ghost.isShown()) {
				addUnitIcon(game, ghost.getUnit(), ghost.getMinimapLandmarkKind(), ghost.isUsesAltMinimapIcon(), true,
						ghost.getFakePlayerIndex(), ghost.getX(), ghost.getY(), player, FOGGED_ICON_SHADE);
			}
		}
		addScriptIcons(game, pathingGrid, fogOfWar);
		drawIcons(batch);
	}

	private void addScriptIcons(final CSimulation game, final PathingGrid pathingGrid, final CPlayerFogOfWar fogOfWar) {
		final List<ScriptMinimapIcon> icons = this.scriptIcons == null ? null : this.scriptIcons.get();
		if (icons == null) {
			return;
		}
		for (int i = icons.size() - 1; i >= 0; i--) {
			if (icons.get(i).isOrphaned()) {
				icons.remove(i);
			}
		}
		for (int i = 0, n = icons.size(); i < n; i++) {
			final ScriptMinimapIcon icon = icons.get(i);
			if (!icon.isVisible()) {
				continue;
			}
			final float x = icon.getX();
			final float y = icon.getY();
			final CFogState fogState = fogOfWar.getFogState(game, pathingGrid.getFogOfWarIndexX(x),
					pathingGrid.getFogOfWarIndexY(y));
			if (!this.observerView && ((icon.getFogMask() & fogState.getId()) == 0)) {
				continue;
			}
			Texture texture = scriptIconTexture(icon.getPath());
			int dimensions = 16;
			if (texture == null) {
				texture = whiteTexture();
				dimensions = 6;
			}
			final int offset = dimensions / 2;
			addIcon(texture, 3,
					(this.minimapFilledArea.x + (((x - this.playableMapArea.getX()) / this.playableMapArea.getWidth())
							* this.minimapFilledArea.width)) - offset,
					(this.minimapFilledArea.y + (((y - this.playableMapArea.getY()) / this.playableMapArea.getHeight())
							* this.minimapFilledArea.height)) - offset,
					dimensions, icon.getRed(), icon.getGreen(), icon.getBlue(), 1f);
		}
	}

	private void addUnitIcon(final CSimulation game, final CUnit simUnit, final int landmarkKind,
			final boolean usesAltIcon, final boolean building,
			final int fakePlayerIndex, final float x, final float y, final CPlayer player, final float shade) {
		int dimensions = 4;
		float red = 1f;
		float green = 1f;
		float blue = 1f;
		float alpha = 1f;
		int layer = 1;
		final Texture minimapIcon;
		final Texture altIcon = usesAltIcon ? altIcon() : null;
		if (altIcon != null) {
			minimapIcon = altIcon;
			dimensions = 21;
		} else if (landmarkKind == RenderUnit.LANDMARK_GOLD_MINE) {
			minimapIcon = this.specialIcons[0];
			dimensions = 21;
		} else if ((landmarkKind == RenderUnit.LANDMARK_ENTANGLED_MINE)
				|| (landmarkKind == RenderUnit.LANDMARK_OTHER_OVERLAY_MINE)) {
			minimapIcon = this.specialIcons[landmarkKind == RenderUnit.LANDMARK_ENTANGLED_MINE ? 3 : 4];
			dimensions = 21;
		} else if (landmarkKind == RenderUnit.LANDMARK_NEUTRAL_BUILDING) {
			minimapIcon = this.specialIcons[1];
			dimensions = 21;
		} else if (simUnit.isHero()) {
			if (!player.hasAlliance(fakePlayerIndex, CAllianceType.PASSIVE)) {
				green = 0.2f;
				blue = 0.2f;
			}
			alpha = this.heroAlpha;
			if ((this.heroAlertShown != null) && this.heroAlertShown.test(simUnit)) {
				green = 0f;
				blue = 0f;
				alpha = 1f;
			}
			layer = 2;
			minimapIcon = this.specialIcons[2];
			dimensions = 28;
		} else {
			if (building) {
				dimensions = 10;
			}
			layer = 0;
			final float[] filtered = this.allyColorFilter == null ? null
					: this.allyColorFilter.minimapColor(game, player.getId(), fakePlayerIndex);
			if (filtered != null) {
				minimapIcon = this.specialIcons[5];
				red = filtered[0];
				green = filtered[1];
				blue = filtered[2];
			}
			else if (fakePlayerIndex == player.getId()) {
				minimapIcon = this.specialIcons[5];
			}
			else {
				minimapIcon = this.teamColors[fakePlayerIndex];
			}
			red *= shade;
			green *= shade;
			blue *= shade;
		}
		final int offset = dimensions / 2;
		addIcon(minimapIcon, layer,
				(this.minimapFilledArea.x
						+ (((x - this.playableMapArea.getX()) / (this.playableMapArea.getWidth()))
								* this.minimapFilledArea.width))
						- offset,
				(this.minimapFilledArea.y
						+ (((y - this.playableMapArea.getY()) / (this.playableMapArea.getHeight()))
								* this.minimapFilledArea.height))
						- offset,
				dimensions, red, green, blue, alpha);
	}

	private static boolean isExploredLandmark(final CSimulation game, final CUnit unit, final int landmarkKind,
			final PathingGrid pathingGrid, final CPlayerFogOfWar fogOfWar, final boolean maskedAreasPartiallyVisible) {
		return (landmarkKind != RenderUnit.LANDMARK_NONE)
				&& (maskedAreasPartiallyVisible || !CFogState.MASKED.equals(fogOfWar.getFogState(game, pathingGrid.getFogOfWarIndexX(unit.getX()),
						pathingGrid.getFogOfWarIndexY(unit.getY()))));
	}

	// Icons are drawn grouped by texture, since every texture change is a separate draw call and the dots of
	// different players otherwise alternate unit by unit.
	private void addIcon(final Texture texture, final int layer, final float x, final float y, final int dimensions,
			final float red, final float green, final float blue, final float alpha) {
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
		values[base + 6] = blue;
		values[base + 7] = alpha;
	}

	private void drawIcons(final SpriteBatch batch) {
		final Texture[] textures = this.iconTextures;
		final float[] values = this.iconValues;
		final int count = this.iconCount;
		for (int layer = 0; layer <= 3; layer++) {
			for (int i = 0; i < count; i++) {
				final Texture texture = textures[i];
				if ((texture == null) || (values[i * ICON_VALUES] != layer)) {
					continue;
				}
				for (int j = i; j < count; j++) {
					if ((textures[j] == texture) && (values[j * ICON_VALUES] == layer)) {
						final int base = j * ICON_VALUES;
						batch.setColor(values[base + 4], values[base + 5], values[base + 6], values[base + 7]);
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

	public void dispose() {
		if (this.fogTexture != null) {
			this.fogTexture.dispose();
			this.fogPixmap.dispose();
		}
		if (this.lineTexture != null) {
			this.lineTexture.dispose();
		}
	}

	private Texture whiteTexture() {
		if (this.lineTexture == null) {
			final Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
			pixmap.setColor(Color.WHITE);
			pixmap.fill();
			this.lineTexture = new Texture(pixmap);
			pixmap.dispose();
		}
		return this.lineTexture;
	}

	private void drawCameraFootprint(final SpriteBatch batch) {
		whiteTexture();
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
		return this.scriptVisible && this.minimapFilledArea.contains(x, y);
	}

	public void setVisible(final boolean visible) {
		this.visible = visible;
	}

	public boolean isVisible() {
		return this.visible;
	}

	public void setMaskedAreasPartiallyVisible(final boolean maskedAreasPartiallyVisible) {
		this.maskedAreasPartiallyVisible = maskedAreasPartiallyVisible;
	}

	public void setTerrainVisible(final boolean terrainVisible) {
		this.terrainVisible = terrainVisible;
	}
}
