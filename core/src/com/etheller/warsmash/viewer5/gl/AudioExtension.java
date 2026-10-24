package com.etheller.warsmash.viewer5.gl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.viewer5.AudioContext;

public interface AudioExtension {
	AudioContext createContext(boolean world);

	float getDuration(Sound sound);

	long play(Sound buffer, final float volume, final float pitch, final float x, final float y, final float z,
			final boolean is3DSound, float maxDistance, float refDistance, boolean looping);

	/**
	 * Sounds come from data sources rather than plain files, which a backend may not accept as is.
	 */
	default Sound newSound(final FileHandle file) {
		return Gdx.audio.newSound(file);
	}

	default Music newMusic(final FileHandle file) {
		return Gdx.audio.newMusic(file);
	}
}
