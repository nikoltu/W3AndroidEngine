package com.etheller.warsmash.viewer5.handlers.mdx;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * The shading of a ground that some models are drawn as part of, as the cliff doodads are of the cliffs they stand
 * in for: they are lit by the ground's lights, and darkened by its shadows and its fog of war where they stand, so that
 * nothing shows where they meet it.
 */
public interface GroundShading {
	/**
	 * Binds the ground's shadow map and fog of war map to the given texture units, with the uniforms that place a
	 * world position on them.
	 */
	void bindGroundShading(ShaderProgram shader, int shadowMapUnit, int fogOfWarMapUnit);
}
