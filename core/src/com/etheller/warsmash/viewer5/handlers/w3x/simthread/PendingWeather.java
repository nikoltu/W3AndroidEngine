package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentWeather;

/**
 * What a script's weather effect handle is while the simulation runs on its own thread: the handle exists at once and
 * the render thread makes the weather when the frame holding the creating command is played. The script's later calls
 * run as commands after that one, on the render thread.
 */
public final class PendingWeather implements SimulationRenderComponentWeather {
	private volatile SimulationRenderComponentWeather weather;

	/** Render thread: the weather the creating command made. */
	public void attach(final SimulationRenderComponentWeather made) {
		this.weather = made;
	}

	public SimulationRenderComponentWeather made() {
		return this.weather;
	}

	@Override
	public void setEnabled(final boolean enabled) {
		final SimulationRenderComponentWeather made = this.weather;
		if (made != null) {
			made.setEnabled(enabled);
		}
	}

	@Override
	public void remove() {
		final SimulationRenderComponentWeather made = this.weather;
		if (made != null) {
			made.remove();
		}
	}

	@Override
	public boolean isRemoved() {
		final SimulationRenderComponentWeather made = this.weather;
		return (made == null) || made.isRemoved();
	}
}