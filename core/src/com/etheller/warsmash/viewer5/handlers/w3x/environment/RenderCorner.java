package com.etheller.warsmash.viewer5.handlers.w3x.environment;

import com.etheller.warsmash.parsers.w3x.w3e.Corner;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CTerrainCorner;

public class RenderCorner extends CTerrainCorner {
	public float depth;

	public RenderCorner(final Corner corner) {
		super(corner);
	}

}
