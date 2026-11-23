package com.etheller.warsmash.viewer5.handlers.w3x.environment;

import java.io.IOException;

import com.badlogic.gdx.graphics.GL30;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.util.ImageUtils.RgbaImage;

public class GroundTexture {
	/** Where the image lies in the ground arrays, -1 when they are full. */
	public final int slot;
	private String tileId;
	private int tileSize;
	private boolean buildable;
	public boolean extended;

	/** @param maxSize the largest width and height of the tile image, 0 for no limit */
	public GroundTexture(final String path, final Element terrainTileInfo, final DataSource dataSource, final GL30 gl,
			final int maxSize, final GroundBanks banks) throws IOException {
		this.slot = banks.allocateSlot();
		if (terrainTileInfo != null) {
			this.tileId = terrainTileInfo.getId();
			final String buildableFieldValue = terrainTileInfo.getField("buildable");
			this.buildable = buildableFieldValue.isEmpty() ? false : Integer.parseInt(buildableFieldValue) == 1;
		}
		else {
			this.buildable = true;
		}
		// 2.0 and later data ship the tiles as .dds only, and the image loader falls back to it.
		if (dataSource.findFirst(path, path.substring(0, path.length() - 4) + ".dds") != null) {
			final RgbaImage image = ImageUtils.getAnyExtensionRgbaImage(dataSource, path,
					"ground texture: " + this.tileId, true, maxSize);
			if (this.slot >= 0) {
				loadImage(gl, image, banks);
			}
		}
	}

	public boolean isBuildable() {
		return this.buildable;
	}

	private void loadImage(final GL30 gl, final RgbaImage image, final GroundBanks banks) {
		final int width = image.getWidth();
		final int height = image.getHeight();

		this.tileSize = (int) (height * 0.25);
		this.extended = (width > height);
		banks.upload(gl, this.slot, image, this.tileSize, this.extended);
	}
}
