package com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability;

import com.badlogic.gdx.graphics.Texture;
import com.etheller.warsmash.parsers.fdf.GameUI;

/**
 * A command card icon that is read the first time it is shown. The game knows icons for every ability, unit and
 * upgrade in the data, but a game only ever shows a few of them; reading them all made starting a game seconds
 * slower on phones.
 */
public final class IconTexture {
	private final GameUI gameUI;
	private final String path;
	private Texture texture;
	private boolean loaded;

	public IconTexture(final GameUI gameUI, final String path) {
		this.gameUI = gameUI;
		this.path = path;
	}

	public Texture get() {
		if (!this.loaded) {
			this.texture = this.gameUI.loadTexture(this.path);
			this.loaded = true;
		}
		return this.texture;
	}
}
