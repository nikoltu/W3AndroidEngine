package com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers;

import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;

public abstract class CTimer {
	private static final float MIN_SAME_TICK_TIMEOUT = 0.001f;
	/**
	 * A length read from data or a script as a decimal number of seconds is a float a little under it (0.65 is
	 * 12.9999995 steps), so it is counted with this much slack to keep its last step; the float's own error grows
	 * with the length, so the slack does too (past 1024 s a fixed one is too small).
	 */
	private static final double WHOLE_STEP_SLACK = 1e-3;
	private static final double WHOLE_STEP_RELATIVE_SLACK = 2e-7;
	private static final int EXACT_LENGTH_MAX_DECIMALS = 6;
	/** Half of a float's relative precision: a decimal this close to a float is the one it was written as. */
	private static final double FLOAT_HALF_RELATIVE_PRECISION = 0x1p-24;

	private int engineFireTick;
	private double scheduleTime;
	private float timeoutTime;
	private float segmentTime;
	private boolean running = false;
	private boolean repeats;
	private boolean paused;
	private float pausedElapsed;
	private float pausedRemaining;
	private int resumedFiresLeft;
	private int generation;

	public void setTimeoutTime(final float timeoutTime) {
		this.timeoutTime = timeoutTime;
	}

	public void resetTimeoutTime(final float timeoutTime) {
		this.timeoutTime = timeoutTime;
		this.segmentTime = timeoutTime;
		this.engineFireTick = (int) Math.round(this.scheduleTime / WarsmashConstants.SIMULATION_STEP_TIME)
				+ wholeSteps(timeoutTime);
	}

	public boolean isRepeats() {
		return this.repeats;
	}

	public boolean isRunning() {
		return this.running;
	}

	public int getGeneration() {
		return this.generation;
	}

	public float getTimeoutTime() {
		return this.timeoutTime;
	}

	/**
	 * Script timers expire at their exact time, several times within one simulation step if the timeout is shorter
	 * than the step; engine timers keep whole steps.
	 */
	protected boolean isExactTiming() {
		return false;
	}

	public boolean canFireInCurrentTick() {
		return isExactTiming() && (this.segmentTime >= MIN_SAME_TICK_TIMEOUT);
	}

	private static double now(final CSimulation simulation) {
		// Inside an exact timer's callback the game clock reads that timer's expiry time, not the step's (1.29: a
		// timer started there counts from it).
		final double firingExactTime = simulation.getTimerFiringExactTime();
		if (!Double.isNaN(firingExactTime)) {
			return firingExactTime;
		}
		return simulation.getGameTurnTick() * (double) WarsmashConstants.SIMULATION_STEP_TIME;
	}

	/** The game time now: a step's start in the unit update, a timer's expiry inside its callback. */
	public static double currentTime(final CSimulation simulation) {
		return now(simulation);
	}

	/** Time this timer is ordered by among those of its step. */
	public double getQueueTime() {
		if (isExactTiming()) {
			return this.scheduleTime + this.segmentTime;
		}
		return this.engineFireTick * (double) WarsmashConstants.SIMULATION_STEP_TIME;
	}

	/** One-shot expiry at the given game time, which may lie inside a step. */
	public void startAt(final CSimulation simulation, final double time) {
		this.repeats = false;
		this.resumedFiresLeft = 0;
		final double now = now(simulation);
		schedule(simulation, now, (float) Math.max(0, time - now));
	}

	public void start(final CSimulation simulation) {
		this.resumedFiresLeft = 0;
		schedule(simulation, now(simulation), this.timeoutTime);
	}

	public void startRepeatingTimerWithDelay(final CSimulation simulation, final float delay) {
		this.repeats = true;
		this.resumedFiresLeft = 0;
		schedule(simulation, now(simulation), delay);
	}

	private void schedule(final CSimulation simulation, final double startTime, final float length) {
		this.generation++;
		this.running = true;
		this.paused = false;
		this.scheduleTime = startTime;
		this.segmentTime = length;
		final int currentTick = simulation.getGameTurnTick();
		if (isExactTiming()) {
			this.engineFireTick = Math.max(currentTick,
					(int) Math.ceil(((startTime + length) / WarsmashConstants.SIMULATION_STEP_TIME) - 1e-3));
		}
		else {
			this.engineFireTick = currentTick + wholeSteps(length);
		}
		simulation.registerTimer(this);
	}

	/** Whole simulation steps in a length of seconds, the part of a step left over dropped. */
	public static int wholeSteps(final double seconds) {
		final double steps = seconds / WarsmashConstants.SIMULATION_STEP_TIME;
		return (int) (steps + stepSlack(steps));
	}

	/** Simulation steps needed to cover a length of seconds, a part of a step counted as a whole one. */
	public static int ceilSteps(final double seconds) {
		final double steps = seconds / WarsmashConstants.SIMULATION_STEP_TIME;
		return (int) Math.ceil(steps - stepSlack(steps));
	}

	/**
	 * A length in seconds as the decimal it was given as (0.63f is 0.6299999952); the original game ends timed
	 * effects and lands swing and cast points after exactly that.
	 */
	public static double exactLength(final double seconds) {
		if (seconds <= 0) {
			return 0;
		}
		final double tolerance = seconds * FLOAT_HALF_RELATIVE_PRECISION;
		double scale = 1;
		for (int decimals = 0; decimals <= EXACT_LENGTH_MAX_DECIMALS; decimals++) {
			final double decimal = Math.rint(seconds * scale) / scale;
			if (Math.abs(decimal - seconds) <= tolerance) {
				return decimal;
			}
			scale *= 10;
		}
		return seconds;
	}

	private static double stepSlack(final double steps) {
		return Math.min(0.5, WHOLE_STEP_SLACK + (Math.abs(steps) * WHOLE_STEP_RELATIVE_SLACK));
	}

	public void pause(final CSimulation simulation) {
		if (!this.paused) {
			this.pausedElapsed = getElapsed(simulation);
			this.pausedRemaining = getRemaining(simulation);
		}
		this.paused = true;
		this.running = false;
		this.resumedFiresLeft = 0;
		this.generation++;
		simulation.unregisterTimer(this);
	}

	public void kill(final CSimulation simulation) {
		this.pause(simulation);
		this.repeats = false;
	}

	public void resume(final CSimulation simulation) {
		if (!this.paused) {
			start(simulation);
			return;
		}
		// 1.29: a resumed periodic timer fires after what was left, once more after a full period, then stops and
		// reads as paused again.
		this.resumedFiresLeft = this.repeats ? 2 : 1;
		schedule(simulation, now(simulation), this.pausedRemaining);
	}

	public float getElapsed(final CSimulation simulation) {
		if (this.paused) {
			return this.pausedElapsed;
		}
		final double elapsed = now(simulation) - this.scheduleTime;
		return (float) Math.max(0, Math.min(elapsed, this.segmentTime));
	}

	public float getRemaining(final CSimulation simulation) {
		if (this.paused) {
			return this.pausedRemaining;
		}
		return this.segmentTime - getElapsed(simulation);
	}

	public void setRepeats(final boolean repeats) {
		this.repeats = repeats;
	}

	public int getEngineFireTick() {
		return this.engineFireTick;
	}

	public abstract void onFire(final CSimulation simulation);

	public void fire(final CSimulation simulation) {
		// its implied that we will have "unregisterTimer" happen automatically
		// before this is called
		this.running = false;
		// the value of this.repeats is snapshotted before firing, because it is allowed
		// to change
		// while firing (we might recycle this timer object)
		final boolean repeats = this.repeats;
		final int generation = this.generation;
		final double firedAt = this.scheduleTime + this.segmentTime;
		final double outerTime = simulation.getTimerFiringExactTime();
		if (isExactTiming()) {
			simulation.setTimerFiringExactTime(Math.min(firedAt,
					simulation.getGameTurnTick() * (double) WarsmashConstants.SIMULATION_STEP_TIME));
		}
		try {
			onFire(simulation);
		}
		finally {
			simulation.setTimerFiringExactTime(outerTime);
		}
		if (generation != this.generation) {
			return;
		}
		if (this.resumedFiresLeft > 0) {
			this.resumedFiresLeft--;
			if (this.resumedFiresLeft == 0) {
				this.paused = true;
				return;
			}
		}
		if (repeats) {
			if (isExactTiming() && (this.timeoutTime >= MIN_SAME_TICK_TIMEOUT)) {
				schedule(simulation, firedAt, this.timeoutTime);
			}
			else {
				schedule(simulation, now(simulation), this.timeoutTime);
			}
		}
	}
}
