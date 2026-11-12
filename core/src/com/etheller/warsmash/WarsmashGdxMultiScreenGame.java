package com.etheller.warsmash;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.movie.GdxMoviePlayer;
import com.etheller.warsmash.movie.Movies;
import com.etheller.warsmash.util.GraphicsContext;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.CanvasProvider;

public class WarsmashGdxMultiScreenGame extends Game implements CanvasProvider {

	@Override
	public void create() {
	}

	/**
	 * A map screen left by setScreen disposes itself on the next frame. A game that ends back to the launcher leaves
	 * it for no screen and closes the activity at once, so that frame may never come; dispose() then still has it.
	 */
	private WarsmashGdxMapScreen leftMapScreen;
	/**
	 * The menu of a game that ended back to the launcher, leaving no screen. The map disposes itself, the menu is
	 * disposed with the game; held until then, its textures and sounds kept the finished activity reachable.
	 */
	private WarsmashGdxMenuScreen endedMenuScreen;
	private GdxMoviePlayer moviePlayer;

	/** Plays the movies in this window, in place of the screen while they play. */
	public void setMoviePlayer(final GdxMoviePlayer moviePlayer) {
		if (this.moviePlayer != null) {
			Movies.removePlayer(this.moviePlayer);
			this.moviePlayer.dispose();
		}
		this.moviePlayer = moviePlayer;
		if (moviePlayer != null) {
			Movies.setPlayer(moviePlayer);
		}
	}

	@Override
	public void setScreen(final Screen screen) {
		final Screen previous = getScreen();
		super.setScreen(screen);
		if ((previous instanceof WarsmashGdxMapScreen) && (previous != screen)) {
			this.leftMapScreen = (WarsmashGdxMapScreen) previous;
		}
		if (screen != null) {
			this.endedMenuScreen = null;
		}
		else if (previous instanceof WarsmashGdxMapScreen) {
			this.endedMenuScreen = ((WarsmashGdxMapScreen) previous).getMenuScreen();
		}
	}

	@Override
	public void render() {
		TouchInputRouter.drainInstalled();
		if ((this.moviePlayer != null) && this.moviePlayer.render()) {
			return;
		}
		super.render();
		if ((this.leftMapScreen != null) && this.leftMapScreen.isDisposed()) {
			this.leftMapScreen = null;
		}
	}

	/**
	 * A closing activity (the game ended back to the launcher, Back, Exit Program) disposes the game here. The map
	 * screen's hide() leaves its dispose to the next frame, which a closing app never draws; undisposed, the finished
	 * game stayed on the heap through statics the map's dispose releases, and the next game in the same process ran
	 * out of Java heap while loading.
	 */
	@Override
	public void dispose() {
		Screen closing = getScreen();
		final WarsmashGdxMapScreen left = this.leftMapScreen;
		this.leftMapScreen = null;
		setMoviePlayer(null);
		super.dispose();
		if ((left != null) && (left != closing)) {
			left.dispose();
			// A game that ended back to the launcher left no screen; its menu goes with it.
			if (closing == null) {
				closing = left.getMenuScreen();
			}
		}
		try {
			if (closing instanceof WarsmashGdxMapScreen) {
				final WarsmashGdxMapScreen mapScreen = (WarsmashGdxMapScreen) closing;
				mapScreen.dispose();
				if (mapScreen.getMenuScreen() != null) {
					mapScreen.getMenuScreen().dispose();
				}
			}
			else if (closing != null) {
				closing.dispose();
			}
		}
		catch (final RuntimeException e) {
			Log.error(e);
		}
		final WarsmashGdxMenuScreen endedMenu = this.endedMenuScreen;
		this.endedMenuScreen = null;
		if ((endedMenu != null) && (endedMenu != closing)) {
			try {
				endedMenu.dispose();
			}
			catch (final RuntimeException e) {
				Log.error(e);
			}
		}
	}

	/** Whether nothing is on the screen yet or a map is loading, when the player must not be able to leave. */
	public boolean isLoadingMap() {
		final Screen current = getScreen();
		return (current == null)
				|| ((current instanceof WarsmashGdxMenuScreen) && ((WarsmashGdxMenuScreen) current).isLoadingScreenShown());
	}

	/** The game on the screen, or null in the menu or while a map loads. */
	public WarsmashGdxMapScreen getRunningMapScreen() {
		final Screen current = getScreen();
		if ((current instanceof WarsmashGdxMapScreen) && !((WarsmashGdxMapScreen) current).isDisposed()) {
			return (WarsmashGdxMapScreen) current;
		}
		return null;
	}

	/**
	 * After the GL context was lost: the screens are disposed without touching the new context, and the caller sets
	 * up new ones.
	 */
	public void disposeLostScreens() {
		GraphicsContext.disposeLost(this::dispose);
		this.screen = null;
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
