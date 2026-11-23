package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;

/**
 * A script sound. Its timing is game state counted in simulation steps; only hearing it is local.
 */
public interface CSound {
	void start(int gameTurnTick);

	void stop();

	/** In seconds: the SetSoundDuration value, else the length of the file. */
	float getDuration();

	/** For TriggerWaitForSound: seconds left of the script length since the last start. */
	float getRemainingTime(int gameTurnTick);

	/**
	 * SetSoundDuration: scripts wait by this value, not by the length of the file.
	 */
	void setDuration(final float seconds);

	default void setVolume(final int volume) {
	}

	default void setPitch(final float pitch) {
	}

	/** Each start picks a pitch up to this far either side of the set one (RANDOMPITCH labels). */
	default void setPitchVariance(final float variance) {
	}

	default void setChannel(final int channel) {
	}

	default void setPosition(final float x, final float y, final float z) {
	}

	default void setDistances(final float minDistance, final float maxDistance) {
	}

	default void setDistanceCutoff(final float cutoff) {
	}

	default void setConeAngles(final float inside, final float outside, final int outsideVolume) {
	}

	default void setConeOrientation(final float x, final float y, final float z) {
	}

	/** SetSoundPlayPosition: the next starts begin this far into the sound, in milliseconds. */
	default void setPlayPosition(final int millisecs) {
	}

	default void setVelocity(final float x, final float y, final float z) {
	}

	default void attachToUnit(final RenderUnit unit) {
	}

	/** GetSoundIsLoading: files are read when the sound is created, so no sound is ever part way through loading. */
	default boolean isLoading() {
		return false;
	}

	boolean isPlaying(int gameTurnTick);

	/**
	 * A load starts what was heard when the game was saved. The platform cannot start a sound part way in, so only a
	 * loop is taken up again; a sound that plays once keeps its script timing and is silent for the rest of its length.
	 */
	default void resumeAfterLoad(final int gameTurnTick) {
	}

	/** KillSoundWhenDone: once the sound is not playing any more, it is released and cannot be started again. */
	void killWhenDone();

	boolean isKilled(int gameTurnTick);

	default void setDialogueSpeakerNameKey(final String key) {
	}

	default String getDialogueSpeakerNameKey() {
		return "";
	}

	default void setDialogueTextKey(final String key) {
	}

	default String getDialogueTextKey() {
		return "";
	}
}
