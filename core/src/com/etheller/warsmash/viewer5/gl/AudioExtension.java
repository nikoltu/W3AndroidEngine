package com.etheller.warsmash.viewer5.gl;

import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.viewer5.AudioContext;

public interface AudioExtension {
	AudioContext createContext(boolean world);

	long play(Sound buffer, final float volume, final float pitch, final float x, final float y, final float z,
			final boolean is3DSound, float maxDistance, float refDistance, boolean looping);

	/** Moves a voice a 3D play started, for a backend that places voices in the world. */
	default void setPosition(final Sound buffer, final long id, final float x, final float y, final float z,
			final float maxDistance, final float refDistance) {
	}

	/** The length of a sound in seconds, 0 when the backend does not tell. */
	default float duration(final Sound buffer) {
		return 0;
	}

	/** Moves a playing voice to this many seconds into its sound, for a backend that can. */
	default void seek(final Sound buffer, final long id, final float seconds) {
	}

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

	/**
	 * Waits, at most this long, for the sounds made so far to be decoded, and leaves what was started for the
	 * game's use as it is.
	 *
	 * @return whether every sound made so far can play
	 */
	default boolean awaitLoading(final long maxMillis) {
		return true;
	}

	/**
	 * Starts decoding a sound on another thread when a backend decodes in newSound, so that the newSound which
	 * follows only has to hand the result over. Nothing to do for a backend that decodes in the background anyway.
	 */
	default void prefetchSound(final FileHandle file) {
	}

	/**
	 * Makes a sound without reading and decoding its file on the calling thread where the backend can: the sound is
	 * given to the callback on the game thread in a later frame, null when it could not be made.
	 *
	 * @return false when nothing was started: the sound is to be made with {@link #newSound} now
	 */
	default boolean newSoundLater(final FileHandle file, final Consumer<Sound> ready) {
		return false;
	}

	/**
	 * Makes a sound now when that holds the calling thread up for hardly anything: a backend that decodes in
	 * {@link #newSound} does it as it always does, one that decodes on another thread only when that is done.
	 *
	 * @return the sound, or null when it is to be made later with {@link #newSoundLater}
	 */
	default Sound newSoundIfReady(final FileHandle file) {
		return newSound(file);
	}

	default Music newMusic(final FileHandle file) {
		return Gdx.audio.newMusic(file);
	}
}
