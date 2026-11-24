package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.io.IOException;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;

/**
 * Works out which cells of the corner grid hold a cliff and which a ramp, from the cliff levels, the ramp flags and
 * the ramp models the cliff types have. The game reads the result (ramps, the height of ramp entrances) and the drawn
 * terrain places its cliff and ramp models from the same walk, so both always agree.
 */
public final class CCliffLayout {
	// The original never finished height 3 ramps: a few of their models exist, but they do not fit.
	private static final boolean DISALLOW_HEIGHT_3_RAMPS = true;

	private CCliffLayout() {
	}

	/** The cliff types of a map: where each one keeps its ramp models, and whether a model file exists. */
	public interface CliffTypes {
		CliffTypes NONE = new CliffTypes() {
			@Override
			public String getRampModelDir(final int cliffType) {
				return null;
			}

			@Override
			public boolean has(final String path) {
				return false;
			}
		};

		String getRampModelDir(int cliffType);

		boolean has(String path);
	}

	/** Told about each cliff and ramp model the walk places. */
	public interface Listener {
		Listener NONE = new Listener() {
			@Override
			public void ramp(final String path, final int column, final int row) {
			}

			@Override
			public void cliff(final int column, final int row, final int base, final int cliffType) {
			}
		};

		/** A ramp model goes at the cell, in place of any model there. */
		void ramp(String path, int column, int row) throws IOException;

		/** The cell is a cliff of the given type rising from the base level, not a ramp. */
		void cliff(int column, int row, int base, int cliffType) throws IOException;
	}

	/** Marks the cells that span more than one cliff level; corners are indexed [column][row]. */
	public static void markCliffs(final CTerrainCorner[][] corners, final int columns, final int rows) {
		for (int i = 0; i < (columns - 1); i++) {
			for (int j = 0; j < (rows - 1); j++) {
				final CTerrainCorner bottomLeft = corners[i][j];
				final CTerrainCorner bottomRight = corners[i + 1][j];
				final CTerrainCorner topLeft = corners[i][j + 1];
				final CTerrainCorner topRight = corners[i + 1][j + 1];

				bottomLeft.cliff = (bottomLeft.getLayerHeight() != bottomRight.getLayerHeight())
						|| (bottomLeft.getLayerHeight() != topLeft.getLayerHeight())
						|| (bottomLeft.getLayerHeight() != topRight.getLayerHeight());
			}
		}
	}

	/**
	 * Lays the cliffs and ramps of an area out again. Ramps reach two cells past the area. The walk settles the cliff
	 * type of cells that have none and drops the ramp flag of corners on ramps too high to have a model, as the drawn
	 * terrain always did.
	 */
	public static void update(final CTerrainCorner[][] corners, final int columns, final int rows,
			final Rectangle area, final CliffTypes cliffTypes, final Listener listener) throws IOException {
		for (int i = (int) area.getX(); i < (int) (area.getX() + area.getWidth()); i++) {
			for (int j = (int) area.getY(); j < (int) (area.getY() + area.getHeight()); j++) {
				corners[i][j].romp = false;
				corners[i][j].openCliff = false;
			}
		}

		final Rectangle adjusted = new Rectangle(area.x - 2, area.y - 2, area.width + 4, area.height + 4);
		final Rectangle rampArea = new Rectangle();
		Intersector.intersectRectangles(new Rectangle(0, 0, columns, rows), adjusted, rampArea);

		final int xLimit = (int) ((rampArea.getX() + rampArea.getWidth()) - 1);
		for (int i = (int) rampArea.getX(); i < xLimit; i++) {
			final int yLimit = (int) ((rampArea.getY() + rampArea.getHeight()) - 1);
			for (int j = (int) rampArea.getY(); j < yLimit; j++) {
				final CTerrainCorner bottomLeft = corners[i][j];
				final CTerrainCorner bottomRight = corners[i + 1][j];
				final CTerrainCorner topLeft = corners[i][j + 1];
				final CTerrainCorner topRight = corners[i + 1][j + 1];

				if (bottomLeft.cliff && !bottomLeft.hideCliff) {
					final int base = Math.min(Math.min(bottomLeft.getLayerHeight(), bottomRight.getLayerHeight()),
							Math.min(topLeft.getLayerHeight(), topRight.getLayerHeight()));

					final boolean facingDown = (topLeft.getLayerHeight() >= bottomLeft.getLayerHeight())
							&& (topRight.getLayerHeight() >= bottomRight.getLayerHeight());
					final boolean facingLeft = (bottomRight.getLayerHeight() >= bottomLeft.getLayerHeight())
							&& (topRight.getLayerHeight() >= topLeft.getLayerHeight());

					int bottomLeftCliffTex = bottomLeft.getCliffTexture();
					if (bottomLeftCliffTex == 15) {
						boolean foundTexture = false;
						final int topLeftCliffTex = topLeft.getCliffTexture();
						if (topLeftCliffTex == 15) {
							final int topRightCliffTex = topRight.getCliffTexture();
							if (topRightCliffTex == 15) {
								final int bottomRightCliffTex = bottomRight.getCliffTexture();
								if (bottomRightCliffTex == 15) {
									bottomLeftCliffTex -= 14;
								}
								else {
									bottomLeftCliffTex = bottomRightCliffTex;
									foundTexture = true;
								}
							}
							else {
								bottomLeftCliffTex = topRightCliffTex;
								foundTexture = true;
							}
						}
						else {
							bottomLeftCliffTex = topLeftCliffTex;
							foundTexture = true;
						}
						if (foundTexture) {
							bottomLeft.setCliffTexture(bottomLeftCliffTex);
						}
					}
					if (!(facingDown && (j == 0)) && !(!facingDown && (j >= (rows - 2))) && !(facingLeft && (i == 0))
							&& !(!facingLeft && (i >= (columns - 2)))) {
						final boolean verticalRamp = (bottomLeft.isRamp() != bottomRight.isRamp())
								&& (topLeft.isRamp() != topRight.isRamp());

						final boolean horizontalRamp = (bottomLeft.isRamp() != topLeft.isRamp())
								&& (bottomRight.isRamp() != topRight.isRamp());

						if (verticalRamp || horizontalRamp) {
							final boolean rampBlockedByCliff = (verticalRamp
									&& corners[i][j + (facingDown ? -1 : 1)].cliff)
									|| (horizontalRamp && corners[i + (facingLeft ? -1 : 1)][j].cliff);
							final int topLeftHeight = topLeft.getLayerHeight() - base;
							final int topRightHeight = topRight.getLayerHeight() - base;
							final int bottomRightHeight = bottomRight.getLayerHeight() - base;
							final int bottomLeftHeight = bottomLeft.getLayerHeight() - base;
							boolean invalidRamp = false;
							if (DISALLOW_HEIGHT_3_RAMPS) {
								if (rampBlockedByCliff) {
									invalidRamp = true;
								}
								else if (topLeftHeight > 1) {
									invalidRamp = true;
									topLeft.setRamp(0);
								}
								else if (topRightHeight > 1) {
									invalidRamp = true;
									topRight.setRamp(0);
								}
								else if (bottomRightHeight > 1) {
									invalidRamp = true;
									bottomRight.setRamp(0);
								}
								else if (bottomLeftHeight > 1) {
									invalidRamp = true;
									bottomLeft.setRamp(0);
								}
							}
							if (!invalidRamp) {
								final String rampModelDir = cliffTypes.getRampModelDir(bottomLeftCliffTex);
								final String fileName = "Doodads\\Terrain\\" + rampModelDir + "\\" + rampModelDir
										+ getRampLetter(topLeftHeight, topLeft.isRamp())
										+ getRampLetter(topRightHeight, topRight.isRamp())
										+ getRampLetter(bottomRightHeight, bottomRight.isRamp())
										+ getRampLetter(bottomLeftHeight, bottomLeft.isRamp()) + "0.mdx";

								if (cliffTypes.has(fileName)) {
									listener.ramp(fileName, i + ((horizontalRamp ? 1 : 0) * (facingLeft ? -1 : 0)),
											j - ((verticalRamp ? 1 : 0) * (facingDown ? 1 : 0)));
									bottomLeft.romp = true;
									bottomLeft.setCliffTexture(bottomLeftCliffTex);
									bottomRight.setCliffTexture(bottomLeftCliffTex);
									topLeft.setCliffTexture(bottomLeftCliffTex);
									topRight.setCliffTexture(bottomLeftCliffTex);
									final CTerrainCorner across = corners[i
											+ ((facingLeft ? -1 : 1) * (horizontalRamp ? 1 : 0))][j
													+ ((facingDown ? -1 : 1) * (verticalRamp ? 1 : 0))];
									across.setCliffTexture(bottomLeftCliffTex);
									across.romp = true;

									continue;
								}
							}
						}
					}

					if (isCornerRampEntrance(corners, columns, rows, i, j)) {
						continue;
					}

					// Ramps move 1 right/down in some cases and thus their area is one bigger to
					// the top and left.
					if (!area.contains(i, j)) {
						continue;
					}

					if (touchesRampSlope(corners, columns, rows, i, j)) {
						bottomLeft.openCliff = true;
						continue;
					}

					listener.cliff(i, j, base, bottomLeftCliffTex);
				}
			}
		}
		for (int i = (int) rampArea.getX(); i < xLimit; i++) {
			final int yLimit = (int) ((rampArea.getY() + rampArea.getHeight()) - 1);
			for (int j = (int) rampArea.getY(); j < yLimit; j++) {
				final CTerrainCorner bottomLeft = corners[i][j];
				if (bottomLeft.isRamp() && !bottomLeft.romp) {
					bottomLeft.hideCliff = false;
				}
			}
		}
	}

	private static boolean touchesRampSlope(final CTerrainCorner[][] corners, final int columns, final int rows,
			final int x, final int y) {
		return (rampHeight(corners, columns, rows, x, y) != 0f) || (rampHeight(corners, columns, rows, x + 1, y) != 0f)
				|| (rampHeight(corners, columns, rows, x, y + 1) != 0f)
				|| (rampHeight(corners, columns, rows, x + 1, y + 1) != 0f);
	}

	/** Whether the cell is the slope of a ramp: all four corners flagged ramp, not split diagonally. */
	public static boolean isCornerRampEntrance(final CTerrainCorner[][] corners, final int columns, final int rows,
			final int x, final int y) {
		if ((x == columns) || (y == rows)) {
			return false;
		}

		final CTerrainCorner bottomLeft = corners[x][y];
		final CTerrainCorner bottomRight = corners[x + 1][y];
		final CTerrainCorner topLeft = corners[x][y + 1];
		final CTerrainCorner topRight = corners[x + 1][y + 1];

		return bottomLeft.isRamp() && topLeft.isRamp() && bottomRight.isRamp() && topRight.isRamp()
				&& !((bottomLeft.getLayerHeight() == topRight.getLayerHeight())
						&& (topLeft.getLayerHeight() == bottomRight.getLayerHeight()));
	}

	/**
	 * Ramp height a corner adds to its cliff level: half a level where it is the lowest corner of a ramp slope cell
	 * next to it.
	 */
	public static float rampHeight(final CTerrainCorner[][] corners, final int columns, final int rows, final int i,
			final int j) {
		for (int xOffset = -1; xOffset <= 0; xOffset++) {
			for (int yOffset = -1; yOffset <= 0; yOffset++) {
				if (((i + xOffset) >= 0) && ((i + xOffset) < (columns - 1)) && ((j + yOffset) >= 0)
						&& ((j + yOffset) < (rows - 1))) {
					final CTerrainCorner bottomLeft = corners[i + xOffset][j + yOffset];
					final CTerrainCorner bottomRight = corners[i + 1 + xOffset][j + yOffset];
					final CTerrainCorner topLeft = corners[i + xOffset][j + 1 + yOffset];
					final CTerrainCorner topRight = corners[i + 1 + xOffset][j + 1 + yOffset];

					final int base = Math.min(Math.min(bottomLeft.getLayerHeight(), bottomRight.getLayerHeight()),
							Math.min(topLeft.getLayerHeight(), topRight.getLayerHeight()));
					if (corners[i][j].getLayerHeight() != base) {
						continue;
					}

					if (isCornerRampEntrance(corners, columns, rows, i + xOffset, j + yOffset)) {
						return 0.5f;
					}
				}
			}
		}
		return 0f;
	}

	private static char getRampLetter(final int layerHeightOffset, final boolean isRamp) {
		if (isRamp) {
			switch (layerHeightOffset) {
			case 0:
				return 'L';
			case 1:
				return 'H';
			case 2:
				return 'X';
			default:
				throw new IllegalArgumentException("Invalid ramp");
			}
		}
		else {
			return (char) ('A' + layerHeightOffset);
		}
	}
}
