package com.etheller.warsmash.viewer5.gl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.viewer5.AudioContext;

public interface AudioExtension {
	AudioContext createContext(boolean world);

	long play(Sound buffer, final float volume, final float pitch, final float x, final float y, final float z,
			final boolean is3DSound, float maxDistance, float refDistance, boolean looping);

	/**
	 * Plays a sound whose loudness and side were worked out by the caller, for a backend without 3D sound. pan is -1
	 * for left, 1 for right.
	 */
	default long playPanned(final Sound buffer, final float volume, final float pitch, final float pan,
			final boolean looping) {
		return play(buffer, volume, pitch, 0, 0, 0, false, 1, 1, looping);
	}

	/**
	 * Sounds come from data sources rather than plain files, which a backend may not accept as is.
	 */
	default Sound newSound(final FileHandle file) {
		return Gdx.audio.newSound(file);
	}

	/**
	 * Waits, at most this long, until the sounds made so far can play. A backend that decodes in newSound has
	 * nothing to wait for.
	 */
	default void finishLoading(final long maxMillis) {
	}

	default Music newMusic(final FileHandle file) {
		return Gdx.audio.newMusic(file);
	}
}
