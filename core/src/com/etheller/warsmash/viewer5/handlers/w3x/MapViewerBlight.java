package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.Arrays;
import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.RenderCorner;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.blight.CAbilityBlight;

/**
 * Blight on the terrain: the ground it covers, its sources and the corners it reaches.
 */
final class MapViewerBlight {
	private MapViewerBlight() {
	}

	static void setBlight(final War3MapViewer viewer, final float whichLocationX, final float whichLocationY, final float radius,
			final boolean blighted) {
		setBlight(viewer, whichLocationX, whichLocationY, radius, false, blighted);
	}

	static void setBlight(final War3MapViewer viewer, final float whichLocationX, final float whichLocationY, final float radius,
			final boolean square, final boolean blighted) {
		// Measured on the original game: blight is set on the terrain corners (every 128) around the corner nearest
		// to the point (halves rounded away from zero), with the radius rounded to whole corners and a corner counted
		// when it is within that many corners plus a little; a 128 tile is blighted, for building and blight checks
		// too, when any of its four corners is.
		final int cornerRadius = getBlightCornerRadius(radius);
		final float cornerReach = cornerRadius + 0.35f;
		final float centerX = roundHalfAwayFromZero(whichLocationX / 128f) * 128f;
		final float centerY = roundHalfAwayFromZero(whichLocationY / 128f) * 128f;
		int changedCount = 0;
		int minI = Integer.MAX_VALUE;
		int maxI = Integer.MIN_VALUE;
		int minJ = Integer.MAX_VALUE;
		int maxJ = Integer.MIN_VALUE;
		boolean sourcesFound = false;
		for (int i = -cornerRadius; i <= cornerRadius; i++) {
			for (int j = -cornerRadius; j <= cornerRadius; j++) {
				if (!square && (((i * i) + (j * j)) > (cornerReach * cornerReach))) {
					continue;
				}
				final float x = centerX + (i * 128f);
				final float y = centerY + (j * 128f);
				final RenderCorner corner = viewer.terrain.getCorner(x, y);
				if ((corner == null) || ((corner.getBlight() != 0) == blighted)) {
					continue;
				}
				if (!blighted) {
					if (!sourcesFound) {
						findStandingBlightSources(viewer, centerX, centerY, cornerRadius);
						sourcesFound = true;
					}
					if (isInStandingBlightSource(viewer, x, y)) {
						continue;
					}
				}
				corner.setBlight(blighted);
				viewer.terrain.blightChanged(x, y);
				if (viewer.blightChangedCorners.length < ((changedCount + 1) * 2)) {
					viewer.blightChangedCorners = Arrays.copyOf(viewer.blightChangedCorners,
							viewer.blightChangedCorners.length * 2);
				}
				viewer.blightChangedCorners[changedCount * 2] = i;
				viewer.blightChangedCorners[(changedCount * 2) + 1] = j;
				changedCount++;
				minI = Math.min(minI, i);
				maxI = Math.max(maxI, i);
				minJ = Math.min(minJ, j);
				maxJ = Math.max(maxJ, j);
			}
		}
		if (changedCount == 0) {
			return;
		}
		final PathingGrid pathingGrid = viewer.simulation.getPathingGrid();
		for (int k = 0; k < changedCount; k++) {
			final float cornerX = centerX + (viewer.blightChangedCorners[k * 2] * 128f);
			final float cornerY = centerY + (viewer.blightChangedCorners[(k * 2) + 1] * 128f);
			for (int a = -1; a <= 0; a++) {
				for (int b = -1; b <= 0; b++) {
					final float tileX = cornerX + (a * 128f);
					final float tileY = cornerY + (b * 128f);
					final boolean tileBlighted = blighted || isCornerBlighted(viewer, tileX, tileY)
							|| isCornerBlighted(viewer, tileX + 128f, tileY) || isCornerBlighted(viewer, tileX, tileY + 128f)
							|| isCornerBlighted(viewer, tileX + 128f, tileY + 128f);
					for (float x = tileX + 16f; x < (tileX + 128f); x += 32f) {
						for (float y = tileY + 16f; y < (tileY + 128f); y += 32f) {
							if (pathingGrid.contains(x, y)) {
								pathingGrid.setBlighted(x, y, tileBlighted);
							}
						}
					}
				}
			}
		}
		final float minX = centerX + ((minI - 1) * 128f);
		final float minY = centerY + ((minJ - 1) * 128f);
		final Rectangle blightRectangle = new Rectangle(minX, minY, ((maxI - minI) + 2) * 128f,
				((maxJ - minJ) + 2) * 128f);
		viewer.terrain.updateShownBlight();
		if (blighted) {
			viewer.simulation.getWorldCollision().enumDestructablesInRect(blightRectangle,
					TreeBlightingCallback.INSTANCE.reset(viewer.simulation));
		}
	}

	static int getBlightCornerRadius(final float radius) {
		return (int) ((radius / 128f) + 0.5f);
	}

	/**
	 * Measured: the blight of a standing blight source (Necropolis) cannot be removed, it stays as it was.
	 */
	static void findStandingBlightSources(final War3MapViewer viewer, final float centerX, final float centerY, final int cornerRadius) {
		viewer.blightSourceCount = 0;
		final float reach = (cornerRadius + 2) * 128f;
		for (final CUnit unit : viewer.simulation.getUnitsIncludingNew()) {
			final CAbilityBlight blight = unit.getFirstAbilityOfType(CAbilityBlight.class);
			if ((blight == null) || !blight.isCreatesBlight() || blight.isDisabled() || unit.isDead()) {
				continue;
			}
			final int sourceRadius = getBlightCornerRadius(blight.getCurrentRadius());
			final float sourceX = roundHalfAwayFromZero(unit.getX() / 128f) * 128f;
			final float sourceY = roundHalfAwayFromZero(unit.getY() / 128f) * 128f;
			final float sourceReach = reach + (sourceRadius * 128f);
			final float dx = sourceX - centerX;
			final float dy = sourceY - centerY;
			if (((dx * dx) + (dy * dy)) > (sourceReach * sourceReach)) {
				continue;
			}
			if (viewer.blightSources.length < ((viewer.blightSourceCount + 1) * 3)) {
				viewer.blightSources = Arrays.copyOf(viewer.blightSources, viewer.blightSources.length * 2);
			}
			final float sourceCornerReach = sourceRadius + 0.35f;
			viewer.blightSources[viewer.blightSourceCount * 3] = sourceX;
			viewer.blightSources[(viewer.blightSourceCount * 3) + 1] = sourceY;
			viewer.blightSources[(viewer.blightSourceCount * 3) + 2] = sourceCornerReach * sourceCornerReach;
			viewer.blightSourceCount++;
		}
	}

	static boolean isInStandingBlightSource(final War3MapViewer viewer, final float x, final float y) {
		for (int k = 0; k < viewer.blightSourceCount; k++) {
			final int i = Math.round((x - viewer.blightSources[k * 3]) / 128f);
			final int j = Math.round((y - viewer.blightSources[(k * 3) + 1]) / 128f);
			if (((i * i) + (j * j)) <= viewer.blightSources[(k * 3) + 2]) {
				return true;
			}
		}
		return false;
	}

	static boolean isCornerBlighted(final War3MapViewer viewer, final float x, final float y) {
		final RenderCorner corner = viewer.terrain.getCorner(x, y);
		return (corner != null) && (corner.getBlight() != 0);
	}

	static float roundHalfAwayFromZero(final float value) {
		return (int) (value + (value < 0 ? -0.5f : 0.5f));
	}
}
