package com.etheller.warsmash;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL30;
import com.etheller.warsmash.viewer5.gl.WebGL;

public final class WarsmashGdxGame {
	/** The vertex array object bound by the active screen; screens restore it after binding their own. */
	public static int VAO;

	private WarsmashGdxGame() {
	}

	/** The state the model viewer draws in, which the interface drawing in between leaves. */
	public static void enterViewerState() {
		Gdx.gl30.glEnable(GL30.GL_SCISSOR_TEST);
		Gdx.gl30.glBindVertexArray(VAO);
	}

	/** The state the interface (libGDX) drawing expects, after the model viewer drew. */
	public static void leaveViewerState(final WebGL webGL) {
		Gdx.gl30.glDisable(GL30.GL_SCISSOR_TEST);
		Gdx.gl30.glDisable(GL30.GL_CULL_FACE);
		webGL.useShaderProgram(null);
	}
}
