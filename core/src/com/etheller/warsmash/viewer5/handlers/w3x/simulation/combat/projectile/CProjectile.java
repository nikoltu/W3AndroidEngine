package com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile;

import com.etheller.interpreter.ast.util.CExtensibleHandleAbstract;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

public abstract class CProjectile extends CExtensibleHandleAbstract implements CEffect {
	private static final double HIT_TIME_EPSILON = 1e-6;
	/** Measured on 1.29: spell missiles hit on a 5 ms clock of game time. */
	private static final double MISSILE_TICK = 0.005;

	protected float x;
	protected float y;
	private float initialTargetX;
	private float initialTargetY;
	private final float speed;
	private AbilityTarget target;
	private final boolean homingEnabled;
	protected boolean done;
	private final CUnit source;
	private boolean reflected = false;
	private float launchLead;
	/** Launch time of a missile on the 1.29 missile clock; NaN for others and in games saved before it. */
	private double missileClockLaunch = Double.NaN;
	private double hitQuantum;
	/** End of the flight already covered by moves, on the missile clock; NaN before the first move. */
	private double flownUntil = Double.NaN;

	public CProjectile(final float x, final float y, final float speed, final AbilityTarget target,
			final boolean homingEnabled, final CUnit source) {
		this.x = x;
		this.y = y;
		this.speed = speed != 0 ? speed : 9999999;
		this.target = target;
		this.homingEnabled = homingEnabled;
		this.source = source;
		this.initialTargetX = target.getX();
		this.initialTargetY = target.getY();
	}

	@Override
	public boolean update(final CSimulation game) {
		if (this.done) {
			return true;
		}
		final float tx = getTargetX();
		final float ty = getTargetY();
		final float sx = this.x;
		final float sy = this.y;
		final float dtsx = tx - sx;
		final float dtsy = ty - sy;
		final float c = (float) Math.sqrt((dtsx * dtsx) + (dtsy * dtsy));

		final float d1x = dtsx / c;
		final float d1y = dtsy / c;

		final float flightTime = WarsmashConstants.SIMULATION_STEP_TIME + this.launchLead;
		if ((this.hitQuantum > 0) && !Double.isNaN(this.missileClockLaunch)) {
			if (Double.isNaN(this.flownUntil)) {
				this.flownUntil = this.missileClockLaunch;
			}
			final double beat = nextBeatHit(c, this.flownUntil, this.flownUntil + flightTime);
			this.flownUntil += flightTime;
			if (!Double.isNaN(beat)) {
				this.launchLead = 0;
				this.done = true;
				hitOn(game, beat);
				return true;
			}
		}
		float travelDistance = Math.min(c, this.speed * flightTime);
		this.launchLead = 0;
		final boolean done = c <= travelDistance;
		if (done) {
			travelDistance = c;
		}

		final float dx = d1x * travelDistance;
		final float dy = d1y * travelDistance;

		this.x = this.x + dx;
		this.y = this.y + dy;

		if (done && !this.done) {
			this.done = true;
			hitAtArrival(game, flightTime - (c / this.speed));
		}
		return this.done;
	}

	/**
	 * The move covers the flight up to the step's end; a missile that arrives before it hits at its exact arrival
	 * time (1.29: a Death Coil 200 away hits 128 ms after the effect, not on a step).
	 */
	private void hitAtArrival(final CSimulation game, final double earlyBy) {
		final double hitTime = onMissileClock(
				(CTimer.currentTime(game) + WarsmashConstants.SIMULATION_STEP_TIME) - earlyBy);
		if (hitTime <= (CTimer.currentTime(game) + HIT_TIME_EPSILON)) {
			onHitTarget(game);
			return;
		}
		hitLater(game, hitTime);
	}

	/**
	 * The missile lands on the last beat of its clock before it arrives: the first beat of the window from which what
	 * is left of the flight is under one beat.
	 */
	private double nextBeatHit(final double distance, final double from, final double until) {
		final double beats = Math.floor(((from - this.missileClockLaunch) / this.hitQuantum) + HIT_TIME_EPSILON) + 1;
		for (double beat = this.missileClockLaunch + (beats * this.hitQuantum); beat <= (until + HIT_TIME_EPSILON);
				beat += this.hitQuantum) {
			final double left = distance - (this.speed * (beat - from));
			if (left <= (this.speed * this.hitQuantum)) {
				return beat + (firstMissileTick() - this.missileClockLaunch);
			}
		}
		return Double.NaN;
	}

	private void hitOn(final CSimulation game, final double hitTime) {
		if (hitTime <= (CTimer.currentTime(game) + HIT_TIME_EPSILON)) {
			onHitTarget(game);
		}
		else {
			hitLater(game, hitTime);
		}
	}

	public void setHitQuantum(final double hitQuantum) {
		this.hitQuantum = hitQuantum;
	}

	private void hitLater(final CSimulation game, final double hitTime) {
		final CTimer timer = new CTimer() {
			@Override
			protected boolean isExactTiming() {
				return true;
			}

			@Override
			public void onFire(final CSimulation simulation) {
				onHitTarget(simulation);
			}
		};
		timer.startAt(game, hitTime);
	}

	/**
	 * Puts the missile on the 1.29 missile clock: it starts on the first 5 ms tick after its launch and hits on the
	 * last tick its flight has passed (a Death Coil effect at 0.452 s with 128.6 ms of flight hits at 0.580 s).
	 */
	public void startMissileClock(final double launchTime) {
		this.missileClockLaunch = launchTime;
	}

	private double firstMissileTick() {
		return (Math.floor((this.missileClockLaunch / MISSILE_TICK) + HIT_TIME_EPSILON) + 1) * MISSILE_TICK;
	}

	private double onMissileClock(final double arrivalTime) {
		if (Double.isNaN(this.missileClockLaunch)) {
			return arrivalTime;
		}
		return firstMissileTick() + (Math.floor(((arrivalTime - this.missileClockLaunch) / MISSILE_TICK)
				+ HIT_TIME_EPSILON) * MISSILE_TICK);
	}

	/** Hits where it is launched, at once or on the first tick of the missile clock. */
	public void hitAtLaunch(final CSimulation game) {
		if (this.done) {
			return;
		}
		this.done = true;
		if (Double.isNaN(this.missileClockLaunch)) {
			onHitTarget(game);
		}
		else {
			hitLater(game, firstMissileTick());
		}
	}

	protected abstract void onHitTarget(CSimulation game);

	/** A unit target that left the game (removed, or removal pending in this step) or is hidden (loaded) takes no hit. */
	protected final boolean isTargetGone(final CSimulation game) {
		final CUnit targetUnit = (this.target == null) ? null : this.target.visit(AbilityTargetVisitor.UNIT);
		return (targetUnit != null)
				&& (targetUnit.isRemovedFromGame() || game.isRemovalPending(targetUnit) || targetUnit.isHidden());
	}

	/**
	 * A missile launched at an exact time inside a step (a swing's or a cast's point) has flown this long by the step's
	 * end; its first move covers it.
	 */
	public void setLaunchLead(final float seconds) {
		this.launchLead = seconds;
	}

	public final float getX() {
		return this.x;
	}

	public final float getY() {
		return this.y;
	}

	public final float getSpeed() {
		return this.speed;
	}

	public final CUnit getSource() {
		return this.source;
	}

	public final AbilityTarget getTarget() {
		return this.target;
	}

	public void setTarget(final AbilityTarget target) {
		this.target = target;
		this.initialTargetX = target.getX();
		this.initialTargetY = target.getY();
	}

	public final boolean isDone() {
		return this.done;
	}

	public void setDone(final boolean done) {
		this.done = done;
	}

	public final float getTargetX() {
		if (this.homingEnabled) {
			return this.target.getX();
		}
		else {
			return this.initialTargetX;
		}
	}

	public final float getTargetY() {
		if (this.homingEnabled) {
			return this.target.getY();
		}
		else {
			return this.initialTargetY;
		}
	}
	public boolean isHomingEnabled() {
		return this.homingEnabled;
	}

	public boolean isReflected() {
		return this.reflected;
	}

	public void setReflected(final boolean reflected) {
		this.reflected = reflected;
	}
}
