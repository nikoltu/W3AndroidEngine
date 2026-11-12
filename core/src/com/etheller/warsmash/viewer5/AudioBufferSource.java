package com.etheller.warsmash.viewer5;

import com.badlogic.gdx.audio.Sound;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;

public class AudioBufferSource {
	public Sound buffer;
	private AudioPanner panner;

	public void connect(final AudioPanner panner) {
		this.panner = panner;
	}

	public long start(final int value, final float volume, final float pitch, final boolean looping) {
		if (this.buffer == null) {
			return -1;
		}
		if (!this.panner.isPositional()) {
			return Extensions.audio.play(this.buffer, volume, pitch, 0, 0, 0, false, 1, 1, looping);
		}
		if (!this.panner.isWithinListenerDistance()) {
			return -1;
		}
		final AudioContext.Listener listener = this.panner.listener;
		if (listener.is3DSupported()) {
			return Extensions.audio.play(this.buffer, volume, pitch, this.panner.x, this.panner.y, this.panner.z,
					true, this.panner.maxDistance, this.panner.refDistance, looping);
		}
		final float pan = listener instanceof AudioContext.Listener.Placed
				? ((AudioContext.Listener.Placed) listener).pan(this.panner.x, this.panner.y, this.panner.z)
				: 0;
		return Extensions.audio.playPanned(this.buffer, volume * this.panner.getDistanceGain(), pitch, pan,
				looping);
	}

	/**
	 * Starts a loop. One heard from a place keeps following the camera: it is started even when out of range or in
	 * fog, silent, so that the id the caller keeps stays valid, and is made audible when the camera comes near.
	 *
	 * @param volume         the loudness before the game's volume groups
	 * @param heardOnlyWhereSeen whether the loop is silent where the player sees nothing
	 */
	public long startLoop(final float volume, final int channel, final float pitch, final boolean heardOnlyWhereSeen) {
		if (this.buffer == null) {
			return -1;
		}
		if (!this.panner.isPositional()) {
			final long id = start(0, volume * CSoundMixer.audible().channelScale(channel), pitch, true);
			if (id != -1) {
				CSoundMixer.audible().loopStarted(this.buffer, id, volume, channel);
			}
			return id;
		}
		final PositionalLoop loop = new PositionalLoop(this.buffer, this.panner, volume, channel, pitch,
				heardOnlyWhereSeen);
		final long id = loop.begin();
		if ((id != -1) && (this.panner.context != null)) {
			this.panner.context.addLoop(loop);
		}
		return id;
	}
}
