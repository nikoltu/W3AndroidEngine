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

	public String getPath() {
		return this.path;
	}

	public Texture get() {
		if (!this.loaded) {
			this.texture = this.gameUI.loadTexture(this.path);
			if (this.texture == null) {
				// Reforged campaign maps point units at versioned icons (BTNPriestV0.blp) that only the HD
				// package has; the classic package keeps the same picture under the plain name.
				final String unversioned = unversioned(this.path);
				if (unversioned != null) {
					this.texture = this.gameUI.loadTexture(unversioned);
				}
			}
			this.loaded = true;
		}
		return this.texture;
	}

	static String unversioned(final String path) {
		final int dotIndex = path.lastIndexOf('.');
		final int end = dotIndex == -1 ? path.length() : dotIndex;
		if ((end >= 2) && (path.charAt(end - 2) == 'V') && Character.isDigit(path.charAt(end - 1))) {
			return path.substring(0, end - 2) + path.substring(end);
		}
		return null;
	}
}
