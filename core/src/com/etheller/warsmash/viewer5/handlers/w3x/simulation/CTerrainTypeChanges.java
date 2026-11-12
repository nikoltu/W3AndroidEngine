package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * Script ground type changes (SetTerrainType). A change sets the ground type and variation of the terrain corners in
 * a brush around a point; pathing and heights stay as the map has them. The corners changed are kept, so a loaded game
 * can put them back on the map's own ground.
 */
public final class CTerrainTypeChanges {
	public static final int SHAPE_ROUND = 0;
	public static final int SHAPE_SQUARE = 1;
	/** A random variation is one of this many: an extended ground texture holds 16 tiles and a plain one. */
	private static final int RANDOM_VARIATIONS = 17;

	private final List<Corner> changed = new ArrayList<>();
	private int randomSeed = 0x1B873593;

	public void set(final CSimulation simulation, final float x, final float y, final int tileType,
			final int variation, final int area, final int shape) {
		final CTerrainData terrain = simulation.getTerrainData();
		if ((terrain == null) || (area < 1) || !terrain.canTakeTerrainType(tileType)) {
			return;
		}
		final int[] size = terrain.getCornerCount();
		final int centerColumn = terrain.nearestColumn(x);
		final int centerRow = terrain.nearestRow(y);
		final int radius = area - 1;
		final List<Corner> brush = new ArrayList<>();
		for (int row = centerRow - radius; row <= (centerRow + radius); row++) {
			for (int column = centerColumn - radius; column <= (centerColumn + radius); column++) {
				if ((column < 0) || (column >= size[0]) || (row < 0) || (row >= size[1])) {
					continue;
				}
				final int dx = column - centerColumn;
				final int dy = row - centerRow;
				if ((shape == SHAPE_ROUND) && (((dx * dx) + (dy * dy)) > (radius * radius))) {
					continue;
				}
				brush.add(new Corner(column, row, tileType, variation >= 0 ? variation : randomVariation()));
			}
		}
		apply(simulation, brush);
		this.changed.addAll(brush);
	}

	private int randomVariation() {
		this.randomSeed = (this.randomSeed * 1103515245) + 12345;
		return (this.randomSeed >>> 16) % RANDOM_VARIATIONS;
	}

	/** A loaded game starts from the map's own ground: the changes are put on it again, in order. */
	public void restore(final CSimulation simulation) {
		if (!this.changed.isEmpty() && (simulation.getTerrainData() != null)) {
			apply(simulation, this.changed);
		}
	}

	private static void apply(final CSimulation simulation, final List<Corner> corners) {
		final CTerrainData terrain = simulation.getTerrainData();
		final int[] cells = new int[corners.size() * 4];
		int index = 0;
		for (final Corner corner : corners) {
			terrain.setTerrainType(corner.column, corner.row, corner.tileType, corner.variation);
			cells[index++] = corner.column;
			cells[index++] = corner.row;
			cells[index++] = corner.tileType;
			cells[index++] = corner.variation;
		}
		simulation.getSimulationRenderController().setTerrainTypes(cells);
	}

	private static final class Corner {
		private final int column;
		private final int row;
		private final int tileType;
		private final int variation;

		private Corner(final int column, final int row, final int tileType, final int variation) {
			this.column = column;
			this.row = row;
			this.tileType = tileType;
			this.variation = variation;
		}
	}
}
