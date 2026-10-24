package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

public interface CSound {
	void start();

	void stop();

	float getPredictedDuration();

	float getRemainingTimeToPlayOnTheDesyncLocalComputer();
}
