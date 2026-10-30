package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;

public class CSoundFromLabel implements CSound {
	private float duration = -1;

	private final UnitSound sound;
	private final boolean looping;
	private final boolean is3d;
	private final boolean stopWhenOutOfRange;
	private final int fadeInRate;
	private final int fadeOutRate;
	private final AudioContext audioContext;
	private long lastStartTimestamp;
	private final CSoundParameters parameters = new CSoundParameters();
	private boolean started;

	public CSoundFromLabel(final UnitSound sound, final AudioContext audioContext, final boolean looping,
			final boolean is3d, final boolean stopWhenOutOfRange, final int fadeInRate, final int fadeOutRate) {
		this.sound = sound;
		this.audioContext = audioContext;
		this.looping = looping;
		this.is3d = is3d;
		this.stopWhenOutOfRange = stopWhenOutOfRange;
		this.fadeInRate = fadeInRate;
		this.fadeOutRate = fadeOutRate;
	}

	@Override
	public void setPosition(final float x, final float y, final float z) {
		this.parameters.positioned = true;
		this.parameters.x = x;
		this.parameters.y = y;
		this.parameters.z = z;
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
	public boolean isPlaying() {
		return this.started && (this.looping || (getRemainingTimeToPlayOnTheDesyncLocalComputer() > 0));
	}

	@Override
	public void start() {
		this.parameters.updatePositionFromUnit();
		final float x = this.parameters.x;
		final float y = this.parameters.y;
		final float z = this.parameters.z;
		final long id;
		if (this.looping) {
			id = this.sound.play(this.audioContext, x, y, z, true);
		}
		else {
			id = this.sound.play(this.audioContext, x, y, z);
		}
		this.started = id != -1;
		this.lastStartTimestamp = TimeUtils.millis();
	}

	@Override
	public void stop() {
		this.started = false;
		if (this.sound.getLastPlayedSound() != null) {
			this.sound.getLastPlayedSound().stop();
		}
	}

	@Override
	public float getPredictedDuration() {
		if (this.duration >= 0) {
			return this.duration;
		}
		return Extensions.audio.getDuration(this.sound.getLastPlayedSound());
	}

	@Override
	public void setDuration(final float seconds) {
		this.duration = seconds;
	}

	@Override
	public float getRemainingTimeToPlayOnTheDesyncLocalComputer() {
		final long currentTime = TimeUtils.millis();
		final long deltaTime = currentTime - this.lastStartTimestamp;
		final float deltaTimeSeconds = deltaTime / 1000f;
		final float predictedDuration = getPredictedDuration();
		if (deltaTimeSeconds > predictedDuration) {
			return 0;
		}
		return predictedDuration - deltaTimeSeconds;
	}

}
