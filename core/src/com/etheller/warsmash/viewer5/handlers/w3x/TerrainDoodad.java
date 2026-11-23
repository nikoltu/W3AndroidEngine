package com.etheller.warsmash.viewer5.handlers.w3x;

import com.badlogic.gdx.math.Quaternion;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.RenderMathUtils;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxVertexLift;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.Terrain;

public class TerrainDoodad {
	private static final float[] locationHeap = new float[3];
	/** The replaceable texture of the cliff doodads, which takes the texture of the cliffs they stand in. */
	private static final int CLIFF_REPLACEABLE_ID = 11;
	/** A corner's cliff texture number for no cliff type of its own. */
	private static final int NO_CLIFF_TEXTURE = 15;
	public final MdxComplexInstance instance;
	private final GameObject row;

	/**
	 * {@code model} is null for a doodad with a model file; the model is then built with its vertices lifted by the
	 * ground heights under them, so that its edges follow the ground and the cliffs around it like cliff pieces do.
	 */
	public TerrainDoodad(final War3MapViewer map, MdxModel model, final String file, final GameObject row,
			final com.etheller.warsmash.parsers.w3x.doo.TerrainDoodad doodad, final ArgbImage pathingTextureImage) {
		final float[] centerOffset = map.terrain.centerOffset;

		final int textureWidth = pathingTextureImage.getWidth();
		final int textureHeight = pathingTextureImage.getHeight();
		final int textureWidthTerrainCells = textureWidth / 4;
		final int textureHeightTerrainCells = textureHeight / 4;
		final int minCellX = ((int) doodad.getLocation()[0]);
		final int minCellY = ((int) doodad.getLocation()[1]);
		locationHeap[0] = ((minCellX * 128) + (textureWidthTerrainCells * 64) + centerOffset[0]);
		locationHeap[1] = ((minCellY * 128) + (textureHeightTerrainCells * 64) + centerOffset[1]);
		final float fixedRotation = (float) Math.toRadians(row.readSLKTagFloat("fixedRot"));
		if (model == null) {
			final float centerX = locationHeap[0];
			final float centerY = locationHeap[1];
			final float cos = (float) Math.cos(fixedRotation);
			final float sin = (float) Math.sin(fixedRotation);
			model = (MdxModel) map.loadEdited(file, map.mapPathSolver, map.solverParams, bytes -> MdxVertexLift
					.lift(bytes, (x, y) -> map.terrain.getCliffGroundHeight(centerX + (x * cos) - (y * sin),
							centerY + (x * sin) + (y * cos))));
		}
		final MdxComplexInstance instance = (MdxComplexInstance) model.addInstance(0);
		locationHeap[2] = baseHeight(map.terrain, minCellX, minCellY, textureWidthTerrainCells,
				textureHeightTerrainCells);

		if (model.replaceables.contains(CLIFF_REPLACEABLE_ID)) {
			final String cliffTexture = map.terrain.getCliffTextureFile(cliffType(map.terrain, minCellX, minCellY,
					textureWidthTerrainCells, textureHeightTerrainCells));
			if (cliffTexture != null) {
				instance.setReplaceableTexture(CLIFF_REPLACEABLE_ID, cliffTexture + ".blp");
				if (model.hd) {
					instance.setReplaceableTextureHD(CLIFF_REPLACEABLE_ID, cliffTexture);
				}
			}
		}
		instance.groundShading = map.terrain;
		instance.move(locationHeap);
		instance.rotate(new Quaternion().setFromAxisRad(RenderMathUtils.VEC3_UNIT_Z, fixedRotation));
		instance.setScene(map.worldScene);

		this.instance = instance;
		this.row = row;
	}

	/**
	 * The cliff type a cliff doodad is textured with: the one most of the corners under it have, so that it shows the
	 * cliffs it stands in for, as the cliff pieces around it do; -1 when no corner under it has one.
	 */
	private static int cliffType(final Terrain terrain, final int minCellX, final int minCellY, final int cellsX,
			final int cellsY) {
		final int[] counts = new int[NO_CLIFF_TEXTURE];
		final int maxX = Math.min(minCellX + cellsX, terrain.columns - 1);
		final int maxY = Math.min(minCellY + cellsY, terrain.rows - 1);
		int best = -1;
		for (int i = Math.max(minCellX, 0); i <= maxX; i++) {
			for (int j = Math.max(minCellY, 0); j <= maxY; j++) {
				final int type = terrain.corners[i][j].getCliffTexture();
				if ((type >= 0) && (type < NO_CLIFF_TEXTURE)) {
					counts[type]++;
					if ((best == -1) || (counts[type] > counts[best])) {
						best = type;
					}
				}
			}
		}
		return best;
	}

	/**
	 * Cliff doodads are modelled from the bottom of the cliff they replace, like the cliff meshes, so they stand on the
	 * lowest cliff level under them; at height 0 they sink out of sight on maps whose base level is above layer 2.
	 */
	private static float baseHeight(final Terrain terrain, final int minCellX, final int minCellY, final int cellsX,
			final int cellsY) {
		int minLayer = Integer.MAX_VALUE;
		final int maxX = Math.min(minCellX + cellsX, terrain.columns - 1);
		final int maxY = Math.min(minCellY + cellsY, terrain.rows - 1);
		for (int i = Math.max(minCellX, 0); i <= maxX; i++) {
			for (int j = Math.max(minCellY, 0); j <= maxY; j++) {
				minLayer = Math.min(minLayer, terrain.corners[i][j].getLayerHeight());
			}
		}
		if (minLayer == Integer.MAX_VALUE) {
			return 0;
		}
		return (minLayer - 2) * 128;
	}
}
