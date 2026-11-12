package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedDefault;

/**
 * What scripts see of a sound is kept in game steps: it plays from the step it was started for its script length, the
 * same on every computer whether or not it has an audio device or has loaded the file yet.
 */
public abstract class AbstractCSound extends AllocatedHandle implements CSound {
	@SavedDefault("-1")
	private float duration = -1;
	private float fileDuration;
	private int startTick;
	private float playPosition;
	private boolean started;
	private boolean stopped;
	private boolean killWhenDone;

	protected AbstractCSound(final float fileDuration) {
		this.fileDuration = fileDuration;
	}

	protected abstract boolean isLooping();

	/** Makes the sound heard; the script state is already updated. */
	protected abstract void play();

	/** Makes the sound silent; the script state is already updated. */
	protected abstract void silence();

	@Override
	public final void start(final int gameTurnTick) {
		if (isKilled(gameTurnTick)) {
			return;
		}
		this.startTick = gameTurnTick;
		this.started = true;
		this.stopped = false;
		starting(gameTurnTick);
		play();
	}

	/** Called as a start begins, before the sound is made heard. */
	protected void starting(final int gameTurnTick) {
	}

	/** The length of the file a start plays, when it can differ between starts. */
	protected final void setFileDuration(final float fileDuration) {
		this.fileDuration = fileDuration;
	}

	@Override
	public final void stop() {
		this.stopped = true;
		silence();
	}

	@Override
	public float getDuration() {
		return this.duration >= 0 ? this.duration : this.fileDuration;
	}

	@Override
	public void setDuration(final float seconds) {
		this.duration = seconds;
	}

	private float getElapsed(final int gameTurnTick) {
		return ((gameTurnTick - this.startTick) * WarsmashConstants.SIMULATION_STEP_TIME) + this.playPosition;
	}

	@Override
	public void setPlayPosition(final int millisecs) {
		this.playPosition = Math.max(0, millisecs) / 1000f;
	}

	/** Where a start begins, in seconds; the audio is played from its start when the platform cannot seek. */
	protected final float getPlayPosition() {
		return this.playPosition;
	}

	@Override
	public boolean isPlaying(final int gameTurnTick) {
		return this.started && !this.stopped
				&& (isLooping() || (getElapsed(gameTurnTick) < getDuration()));
	}

	/**
	 * 1.29: counted from the last start whether or not the sound was stopped since, and 0 for one never started.
	 */
	@Override
	public float getRemainingTime(final int gameTurnTick) {
		if (!this.started) {
			return 0;
		}
		return Math.max(0, getDuration() - getElapsed(gameTurnTick));
	}

	@Override
	public void killWhenDone() {
		this.killWhenDone = true;
	}

	@Override
	public boolean isKilled(final int gameTurnTick) {
		return this.killWhenDone && !isPlaying(gameTurnTick);
	}
}
