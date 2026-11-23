package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

/**
 * Decides which game time the render side shows. The presentation time trails the newest published simulation time by
 * the delay D (50 ms, one step), so the renderer always has the frame before and the frame after the shown moment to
 * interpolate between. When the simulation is late the clock slows down, then extrapolates, rather than stopping.
 * <p>
 * Everything here runs on the render thread only; the implementation reads the {@link SimFrameRing} (published frames)
 * and nothing from the simulation. The clock also releases frames it has moved past.
 */
public interface PresentationClock {
	/** Target delay between the newest simulation time and the presentation time, in milliseconds. */
	int DELAY_MILLIS = 50;

	/** What the clock is doing this frame. */
	enum State {
		/** Running at full speed with frames to interpolate between. */
		NORMAL,
		/** Running slower than real time to wait for a late step. */
		SLOWED,
		/** No newer frame; moving units continue along their path or velocity, new events are held. */
		EXTRAPOLATING,
		/** Past the extrapolation limit: the picture is held until a frame arrives. */
		STARVED,
		/** The game is paused (the clock stays at the pause tick). */
		PAUSED
	}

	/**
	 * Moves the presentation time forward by the real time of this render frame, scaled by the game speed, and updates
	 * the state. Call once per render frame, before reading {@link #frameBefore()} / {@link #frameAfter()}.
	 */
	void advance(long realNanos);

	/** Presentation time in game milliseconds. */
	long presentationMillis();

	/** The frame at or before the presentation time (null before the first frame). */
	SimFrame frameBefore();

	/** The frame after it, or null while extrapolating or starved. */
	SimFrame frameAfter();

	/** 0..1 between {@link #frameBefore()} and {@link #frameAfter()}; beyond 1 while extrapolating (capped by the implementation). */
	float interpolation();

	State state();

	/** The frame before {@link #frameBefore()} (for the speed of a thing that has no path), or null. */
	SimFrame framePrevious();

	/** How far the presentation time is past the newest frame while extrapolating or starved, else 0. */
	long extrapolatedMillis();

	/** Presentation time in game milliseconds at fractional precision (frame time plus the interpolation). */
	float presentationMillisExact();

	/** The reader may release every frame below this sequence; the clock still shows the frames from it on. */
	long retainFromSequence();

	/** How far the newest simulation time is ahead of the presentation time, in milliseconds (the sim_lead_ms counter). */
	long leadMillis();

	/** Game speed factor (game speed times replay speed) applied to the presentation time. */
	void setTimeScale(float scale);

	/** Pauses or resumes the clock; the simulation's own pause arrives in the frames. */
	void setPaused(boolean paused);

	/**
	 * Whether units the simulation made are waiting for their models to load: the clock then runs slower, within a
	 * limit, so that the units are not shown later than the moment they were made at.
	 */
	default void setModelWait(final boolean waiting) {
	}

	/** Jumps to a game time after a load or a fast-forward, dropping the history. */
	void reset(long gameTimeMillis);
}
