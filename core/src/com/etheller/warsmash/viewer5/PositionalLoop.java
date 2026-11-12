package com.etheller.warsmash.viewer5;

import com.badlogic.gdx.audio.Sound;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;

/**
 * A looping sound heard from a place in the world. Its loudness and side depend on where the camera is, so they are
 * set again every frame; a backend with 3D sound attenuates by itself and only needs it silenced when out of range
 * or unseen.
 */
final class PositionalLoop {
	private static final float LEVEL_STEP = 0.002f;

	private final Sound sound;
	private final AudioPanner panner;
	private final float volume;
	private final int channel;
	private final float pitch;
	private final boolean heardOnlyWhereSeen;
	private long id = -1;
	private float lastVolume = -1;
	private float lastPan = Float.NaN;

	PositionalLoop(final Sound sound, final AudioPanner panner, final float volume, final int channel,
			final float pitch, final boolean heardOnlyWhereSeen) {
		this.sound = sound;
		this.panner = panner;
		this.volume = volume;
		this.channel = channel;
		this.pitch = pitch;
		this.heardOnlyWhereSeen = heardOnlyWhereSeen;
	}

	long begin() {
		this.panner.refreshPosition();
		final boolean heard = isHeard();
		final float mixed = this.volume * CSoundMixer.audible().channelScale(this.channel);
		if (this.panner.listener.is3DSupported()) {
			this.lastVolume = heard ? mixed : 0;
			this.id = Extensions.audio.play(this.sound, this.lastVolume, this.pitch, this.panner.x, this.panner.y,
					this.panner.z, true, this.panner.maxDistance, this.panner.refDistance, true);
			return this.id;
		}
		this.lastVolume = heard ? mixed * this.panner.getDistanceGain() : 0;
		this.lastPan = pan();
		this.id = Extensions.audio.playPanned(this.sound, this.lastVolume, this.pitch, this.lastPan, true);
		return this.id;
	}

	void update() {
		if (this.id == -1) {
			return;
		}
		this.panner.refreshPosition();
		final boolean heard = isHeard();
		final float mixed = heard ? this.volume * CSoundMixer.audible().channelScale(this.channel) : 0;
		if (this.panner.listener.is3DSupported()) {
			if (Math.abs(mixed - this.lastVolume) > LEVEL_STEP) {
				this.lastVolume = mixed;
				this.sound.setVolume(this.id, mixed);
			}
			return;
		}
		final float level = heard ? mixed * this.panner.getDistanceGain() : 0;
		final float pan = heard ? pan() : this.lastPan;
		if ((Math.abs(level - this.lastVolume) > LEVEL_STEP) || (Math.abs(pan - this.lastPan) > LEVEL_STEP)) {
			this.lastVolume = level;
			this.lastPan = pan;
			this.sound.setPan(this.id, pan, level);
		}
	}

	private boolean isHeard() {
		if (!this.panner.isWithinListenerDistance()) {
			return false;
		}
		final AudioContext.SoundVisibility visibility = this.panner.context == null ? null
				: this.panner.context.visibility;
		return !this.heardOnlyWhereSeen || (visibility == null) || visibility.isHeardAt(this.panner.x, this.panner.y);
	}

	private float pan() {
		return this.panner.listener instanceof AudioContext.Listener.Placed
				? ((AudioContext.Listener.Placed) this.panner.listener).pan(this.panner.x, this.panner.y,
						this.panner.z)
				: 0;
	}
}
