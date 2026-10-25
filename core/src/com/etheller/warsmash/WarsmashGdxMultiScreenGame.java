package com.etheller.warsmash;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.viewer5.CanvasProvider;

public class WarsmashGdxMultiScreenGame extends Game implements CanvasProvider {

	@Override
	public void create() {
	}

	@Override
	public void render() {
		TouchInputRouter.drainInstalled();
		super.render();
	}

	@Override
	public float getWidth() {
		return Gdx.graphics.getWidth();
	}

	@Override
	public float getHeight() {
		return Gdx.graphics.getHeight();
	}

}
