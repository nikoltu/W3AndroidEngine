package com.etheller.warsmash.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Disposable;

/**
 * Textures and sounds are created from many places while a screen is up and nothing else owns them. The menu and the
 * map on top of it each keep their own: what is made while the map is on screen belongs to the map, the rest to the
 * menu, and each lets go of its own when it is disposed. Launchers start a new menu for every game in the same GL
 * context, so a menu's own textures are not kept for the life of the program.
 */
public final class SessionResources {
	private static final List<Scope> OPEN = new ArrayList<>();

	private SessionResources() {
	}

	public static final class Scope {
		private final Application app;
		private final List<Disposable> resources = new ArrayList<>();

		private Scope(final Application app) {
			this.app = app;
		}

		/** Disposes what was tracked; the scope tracks nothing more. */
		public void dispose() {
			end(this);
			for (final Disposable resource : this.resources) {
				resource.dispose();
			}
			this.resources.clear();
		}
	}

	/** A new scope over the one open, which takes what is tracked from now until it ends. */
	public static synchronized Scope begin() {
		// A finished Android game activity takes its GL context and its open scopes along.
		for (final Iterator<Scope> it = OPEN.iterator(); it.hasNext();) {
			if (it.next().app != Gdx.app) {
				it.remove();
			}
		}
		final Scope scope = new Scope(Gdx.app);
		OPEN.add(scope);
		return scope;
	}

	public static synchronized <T extends Disposable> T track(final T resource) {
		if (!OPEN.isEmpty() && (resource != null)) {
			OPEN.get(OPEN.size() - 1).resources.add(resource);
		}
		return resource;
	}

	/** Stops tracking into this scope; what it has is kept for its dispose. */
	public static synchronized void end(final Scope scope) {
		OPEN.remove(scope);
	}
}
