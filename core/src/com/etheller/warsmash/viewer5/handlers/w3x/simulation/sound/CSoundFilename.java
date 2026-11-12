package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import com.badlogic.gdx.audio.Sound;
import com.etheller.warsmash.viewer5.AudioBufferSource;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.AudioPanner;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;

public class CSoundFilename extends AbstractCSound {
	private final Sound sound;
	private final boolean looping;
	private final boolean stopWhenOutOfRange;
	private final int fadeInRate;
	private final int fadeOutRate;
	private final AudioContext audioContext;
	private final CSoundParameters parameters = new CSoundParameters();
	private final String eaxSetting;
	private long lastSoundId = -1;

	/**
	 * @param fileDuration the length of the file in seconds (0 when it is missing); the sound may be null when this
	 *                     computer cannot play it
	 */
	public CSoundFilename(final Sound sound, final float fileDuration, final AudioContext audioContext,
			final boolean looping, final boolean stopWhenOutOfRange, final int fadeInRate, final int fadeOutRate,
			final String eaxSetting) {
		super(fileDuration);
		this.sound = sound;
		this.audioContext = audioContext;
		this.looping = looping;
		this.stopWhenOutOfRange = stopWhenOutOfRange;
		this.fadeInRate = fadeInRate;
		this.fadeOutRate = fadeOutRate;
		this.eaxSetting = eaxSetting;
	}

	@Override
	public void setPosition(final float x, final float y, final float z) {
		this.parameters.positioned = true;
		this.parameters.x = x;
		this.parameters.y = y;
		this.parameters.z = z;
	}

	@Override
	public void setVolume(final int volume) {
		this.parameters.volume = volume;
	}

	@Override
	public void setPitch(final float pitch) {
		this.parameters.pitch = pitch;
	}

	@Override
	public void setPitchVariance(final float variance) {
		this.parameters.pitchVariance = variance;
	}

	@Override
	public void setChannel(final int channel) {
		this.parameters.channel = channel;
	}

	@Override
	public void setDistances(final float minDistance, final float maxDistance) {
		this.parameters.minDistance = minDistance;
		this.parameters.maxDistance = maxDistance;
	}

	@Override
	public void setDistanceCutoff(final float cutoff) {
		this.parameters.distanceCutoff = cutoff;
	}

	@Override
	public void setConeAngles(final float inside, final float outside, final int outsideVolume) {
		this.parameters.coneInside = inside;
		this.parameters.coneOutside = outside;
		this.parameters.coneOutsideVolume = outsideVolume;
	}

	@Override
	public void setConeOrientation(final float x, final float y, final float z) {
		this.parameters.coneX = x;
		this.parameters.coneY = y;
		this.parameters.coneZ = z;
	}

	@Override
	public void setVelocity(final float x, final float y, final float z) {
		this.parameters.velocityX = x;
		this.parameters.velocityY = y;
		this.parameters.velocityZ = z;
	}

	@Override
	public void attachToUnit(final RenderUnit unit) {
		this.parameters.attachedUnit = unit;
	}

	@Override
	public void setDialogueSpeakerNameKey(final String key) {
		this.parameters.dialogueSpeakerNameKey = key;
	}

	@Override
	public String getDialogueSpeakerNameKey() {
		return this.parameters.dialogueSpeakerNameKey;
	}

	@Override
	public void setDialogueTextKey(final String key) {
		this.parameters.dialogueTextKey = key;
	}

	@Override
	public String getDialogueTextKey() {
		return this.parameters.dialogueTextKey;
	}

	@Override
	protected boolean isLooping() {
		return this.looping;
	}

	@Override
	protected void play() {
		if ((this.audioContext == null) || (this.sound == null)) {
			return;
		}
		// The platform cannot start a sound part way in, but one started past its end is silent as in the script.
		if (!this.looping && (getPlayPosition() >= getDuration())) {
			return;
		}
		final AudioPanner panner = this.audioContext.createPanner(this.stopWhenOutOfRange);
		final AudioBufferSource source = this.audioContext.createBufferSource();

		this.parameters.updatePositionFromUnit();
		if (this.parameters.attachedUnit != null) {
			panner.followLocation = this.parameters.attachedUnit.location;
		}

		// A 3D sound nobody placed plays at the listener rather than at the map origin.
		if (this.parameters.positioned) {
			panner.setPosition(this.parameters.x, this.parameters.y, this.parameters.z);
		}
		else {
			panner.setPosition(this.audioContext.listener.getX(), this.audioContext.listener.getY(),
					this.audioContext.listener.getZ());
			panner.followListener = true;
		}
		panner.setDistances(Math.min(this.parameters.distanceCutoff, this.parameters.maxDistance),
				this.parameters.minDistance);
		panner.connect(this.audioContext.destination);

		// Source.
		source.buffer = this.sound;
		source.connect(panner);

		// Make a sound.
		if (this.looping) {
			this.lastSoundId = source.startLoop(this.parameters.unmixedVolume(1.0f, this.audioContext),
					this.parameters.channel, this.parameters.startPitch(), false);
		}
		else {
			this.lastSoundId = source.start(0, this.parameters.scaledVolume(1.0f, this.audioContext),
					this.parameters.startPitch(), false);
		}
	}

	@Override
	protected void silence() {
		if (this.sound != null) {
			// Without an id this would also stop other sounds created from the same file.
			if (this.lastSoundId != -1) {
				this.sound.stop(this.lastSoundId);
			}
			else {
				this.sound.stop();
			}
		}
	}

	/**
	 * A stacked sound plays as one loop for all its areas; CStackedSounds sets its loudness and side from the nearest
	 * area every frame, so it is started unplaced.
	 */
	long startStacked() {
		if ((this.sound == null) || (this.audioContext == null)) {
			return -1;
		}
		return Extensions.audio.play(this.sound, 0f, this.parameters.startPitch(), 0, 0, 0, false, 1, 1, true);
	}

	void setStackedLevel(final long id, final float gain, final float pan) {
		final float volume = this.parameters.scaledVolume(1.0f, null) * gain;
		this.sound.setPan(id, pan, volume);
	}

	void stopStacked(final long id) {
		this.sound.stop(id);
	}

	CSoundParameters getParameters() {
		return this.parameters;
	}
}
