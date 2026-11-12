package com.etheller.warsmash.parsers.jass.ai;

import java.util.Arrays;

import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.PathingFlags;

/**
 * The land masses of the map as the computer player plans routes for amphibious units it has not been told to take
 * through water: walkable cells that are not water, shallow water included.
 */
final class AILand {
	private static final int NONE = -1;

	private final PathingGrid grid;
	private final int width;
	private final int height;
	private final int[] masses;

	private AILand(final PathingGrid grid) {
		this.grid = grid;
		this.width = grid.getWidth();
		this.height = grid.getHeight();
		this.masses = new int[this.width * this.height];
		Arrays.fill(this.masses, NONE);
		final int[] queue = new int[this.masses.length];
		int next = 0;
		for (int start = 0; start < this.masses.length; start++) {
			if ((this.masses[start] != NONE) || !isLand(start)) {
				continue;
			}
			final int mass = next++;
			int head = 0;
			int tail = 0;
			queue[tail++] = start;
			this.masses[start] = mass;
			while (head < tail) {
				final int cell = queue[head++];
				final int x = cell % this.width;
				final int y = cell / this.width;
				for (int step = 0; step < 4; step++) {
					final int nx = x + ((step == 0) ? 1 : (step == 1) ? -1 : 0);
					final int ny = y + ((step == 2) ? 1 : (step == 3) ? -1 : 0);
					if ((nx < 0) || (ny < 0) || (nx >= this.width) || (ny >= this.height)) {
						continue;
					}
					final int neighbor = (ny * this.width) + nx;
					if ((this.masses[neighbor] == NONE) && isLand(neighbor)) {
						this.masses[neighbor] = mass;
						queue[tail++] = neighbor;
					}
				}
			}
		}
	}

	static AILand of(final AIController ai) {
		if ((ai.land == null) || (ai.land.grid != ai.simulation.getPathingGrid())) {
			ai.land = new AILand(ai.simulation.getPathingGrid());
		}
		return ai.land;
	}

	private boolean isLand(final int cell) {
		final short pathing = this.grid.getTerrainPathing(this.grid.getWorldX(cell % this.width),
				this.grid.getWorldY(cell / this.width));
		return !PathingFlags.isPathingFlag(pathing, PathingFlags.UNWALKABLE)
				&& PathingFlags.isPathingFlag(pathing, PathingFlags.UNSWIMABLE);
	}

	/**
	 * @return the land mass of the nearest land cell within the radius, or -1
	 */
	int massNear(final float x, final float y, final float radius) {
		final int cx = this.grid.getCellX(x);
		final int cy = this.grid.getCellY(y);
		final int cells = (int) Math.ceil(radius / 32f);
		int best = NONE;
		int bestDistance = Integer.MAX_VALUE;
		for (int dy = -cells; dy <= cells; dy++) {
			for (int dx = -cells; dx <= cells; dx++) {
				final int nx = cx + dx;
				final int ny = cy + dy;
				final int d = (dx * dx) + (dy * dy);
				if ((nx < 0) || (ny < 0) || (nx >= this.width) || (ny >= this.height) || (d >= bestDistance)
						|| (d > (cells * cells))) {
					continue;
				}
				final int mass = this.masses[(ny * this.width) + nx];
				if (mass != NONE) {
					best = mass;
					bestDistance = d;
				}
			}
		}
		return best;
	}
}
