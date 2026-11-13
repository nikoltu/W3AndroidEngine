package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CExactExpiry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

public abstract class CAbstractRangedBehavior implements CRangedBehavior {
	/**
	 * Measured on 1.29: a unit turns towards what it acts on, starts an attack swing or a cast only on ticks every 0.03
	 * s of game time.
	 */
	private static final double UNIT_TICK = 0.03;
	/** Measured on 1.29: a cast starts once the caster's heading is within 0.2 rad of its target. */
	private static final float CAST_HALF_ANGLE = (float) StrictMath.toDegrees(0.2);
	private static final double TIME_EPSILON = 1e-6;

	protected final CUnit unit;

	public CAbstractRangedBehavior(final CUnit unit) {
		this.unit = unit;
	}

	protected AbilityTarget target;
	private boolean wasWithinPropWindow = false;
	private boolean wasInRange = false;
	private boolean disableMove = false;
	private CBehaviorMove moveBehavior;
	private boolean turningToCast = false;
	/** Null in games saved before casts started on the 1.29 turn ticks; those start on the step the turn ends. */
	private CExactExpiry castTurnDone;
	private float castTurnStartFacing;
	/** Turn ticks of the current cast turn; 0 in games saved before the heading kept its last tick's turn. */
	private int castTurnTicks;

	protected final CBehavior innerReset(CSimulation game, final AbilityTarget target) {
		return innerReset(game, target, false);
	}

	protected final CBehavior innerReset(CSimulation game, final AbilityTarget target, final boolean disableCollision) {
		this.target = target;
		this.wasWithinPropWindow = false;
		this.wasInRange = false;
		cancelCastTurn(game);
		CBehaviorMove moveBehavior;
		if (!this.unit.isMovementDisabled()) {
			moveBehavior = this.unit.getMoveBehavior().reset(this.target, this, disableCollision);
		}
		else {
			moveBehavior = null;
		}
		this.moveBehavior = moveBehavior;
		if (!isWithinRange(game)) {
			if ((this.moveBehavior == null) || this.disableMove) {
				return this.unit.pollNextOrderBehavior(game);
			}
			this.wasInRange = false;
			resetBeforeMoving(game);
			return this.unit.getMoveBehavior();
		}
		return this;
	}

	protected abstract CBehavior update(CSimulation simulation, boolean withinFacingWindow);

	protected abstract CBehavior updateOnInvalidTarget(CSimulation simulation);

	protected abstract boolean checkTargetStillValid(CSimulation simulation);

	protected abstract void resetBeforeMoving(CSimulation simulation);

	@Override
	public final CBehavior update(final CSimulation simulation) {
		if (!checkTargetStillValid(simulation)) {
			return updateOnInvalidTarget(simulation);
		}
		if (!isWithinRange(simulation)) {
			if ((this.moveBehavior == null) || this.disableMove) {
				return this.unit.pollNextOrderBehavior(simulation);
			}
			this.wasInRange = false;
			cancelCastTurn(simulation);
			resetBeforeMoving(simulation);
			return this.unit.getMoveBehavior();
		}
		this.wasInRange = true;
		if (isFacingLocked()) {
			this.wasWithinPropWindow = true;
		}
		else if (usesCastTurn() && (this.unit.getUnitType().getTurnRate() > 0) && !this.unit.isBuilding()) {
			this.wasWithinPropWindow = updateCastTurn(simulation);
		}
		else if (!this.unit.isMovementDisabled() && !isAtTarget()) {
			final float prevX = this.unit.getX();
			final float prevY = this.unit.getY();
			final float deltaX = this.target.getX() - prevX;
			final float deltaY = this.target.getY() - prevY;
			final double goalAngleRad = StrictMath.atan2(deltaY, deltaX);
			float goalAngle = (float) StrictMath.toDegrees(goalAngleRad);
			if (goalAngle < 0) {
				goalAngle += 360;
			}
			float facing = this.unit.getFacing();
			float delta = goalAngle - facing;
			final float propulsionWindow = simulation.getGameplayConstants().getAttackHalfAngle();
			final float turnStep = CBehaviorMove.getTurnStep(this.unit);

			if (delta < -180) {
				delta = 360 + delta;
			}
			if (delta > 180) {
				delta = -360 + delta;
			}
			final float absDelta = Math.abs(delta);

			if ((absDelta <= 1.0) && (absDelta != 0)) {
				this.unit.setFacing(goalAngle);
			}
			else {
				float angleToAdd = Math.signum(delta) * turnStep;
				if (absDelta < Math.abs(angleToAdd)) {
					angleToAdd = delta;
				}
				facing += angleToAdd;
				this.unit.setFacing(facing);
			}
			if (absDelta < propulsionWindow) {
				this.wasWithinPropWindow = true;
			}
			else {
				// If this happens, the unit is facing the wrong way, and has to turn before
				// moving.
				this.wasWithinPropWindow = false;
			}
		}
		else {
			this.wasWithinPropWindow = true;
		}

		return update(simulation, this.wasWithinPropWindow);
	}

	/**
	 * Measured on 1.29: a caster within 0.2 rad of its target casts at once without turning. Otherwise its heading
	 * turns by its turn rate on each unit tick after the order and the cast starts on the first tick that finds the
	 * heading within 0.2 rad. While it casts it no longer turns after the target.
	 */
	private boolean updateCastTurn(final CSimulation simulation) {
		final float goalAngle = goalAngle();
		final float delta = angleDelta(goalAngle, this.unit.getFacing());
		final float absDelta = Math.abs(delta);
		if (!this.turningToCast) {
			if (absDelta <= CAST_HALF_ANGLE) {
				return true;
			}
			this.turningToCast = true;
			final double now = CTimer.currentTime(simulation);
			final double turnTicks = Math.ceil(((absDelta - CAST_HALF_ANGLE) / getTurnRateDegrees()) - TIME_EPSILON);
			this.castTurnStartFacing = this.unit.getFacing();
			this.castTurnTicks = (int) turnTicks;
			if (this.castTurnDone == null) {
				this.castTurnDone = new CExactExpiry();
			}
			this.castTurnDone.startRemaining(simulation, (nextUnitTick(now) + (turnTicks * UNIT_TICK)) - now);
		}
		turnTowards(goalAngle, delta, CBehaviorMove.getTurnStep(this.unit));
		if ((this.castTurnDone != null) && !this.castTurnDone.check(simulation, this::castTurnDoneInStep)) {
			return false;
		}
		this.turningToCast = false;
		this.unit.setFacing(castTurnEndFacing(goalAngle));
		return true;
	}

	private void castTurnDoneInStep(final CSimulation simulation) {
		if (!this.turningToCast || (this.unit.getCurrentBehavior() != this) || this.unit.isDead()
				|| this.unit.isPaused() || !checkTargetStillValid(simulation) || !isWithinRange(simulation)) {
			return;
		}
		this.turningToCast = false;
		this.unit.setFacing(castTurnEndFacing(goalAngle()));
		this.wasWithinPropWindow = true;
		final CBehavior next = update(simulation, true);
		if (next != this) {
			this.unit.beginBehavior(simulation, next);
		}
	}

	/**
	 * Measured on 1.29: the heading turns by the turn rate on each turn tick and stops where the last tick leaves it,
	 * up to 0.2 rad short of the target (a caster 40 degrees away ends up 6 degrees off it, one 180 degrees away 8).
	 */
	private float castTurnEndFacing(final float goalAngle) {
		if (this.castTurnTicks <= 0) {
			return goalAngle;
		}
		final float delta = angleDelta(goalAngle, this.castTurnStartFacing);
		final float maxTurn = this.castTurnTicks * getTurnRateDegrees();
		if (Math.abs(delta) <= maxTurn) {
			return goalAngle;
		}
		float facing = this.castTurnStartFacing + (Math.signum(delta) * maxTurn);
		if (facing < 0) {
			facing += 360;
		}
		else if (facing >= 360) {
			facing -= 360;
		}
		return facing;
	}

	private void cancelCastTurn(final CSimulation simulation) {
		this.turningToCast = false;
		if (this.castTurnDone != null) {
			this.castTurnDone.cancel(simulation);
		}
	}

	private float getTurnRateDegrees() {
		return (float) StrictMath.toDegrees(this.unit.getTurnRate());
	}

	private float goalAngle() {
		final double goalAngleRad = StrictMath.atan2(this.target.getY() - this.unit.getY(),
				this.target.getX() - this.unit.getX());
		float goalAngle = (float) StrictMath.toDegrees(goalAngleRad);
		if (goalAngle < 0) {
			goalAngle += 360;
		}
		return goalAngle;
	}

	private static float angleDelta(final float goalAngle, final float facing) {
		float delta = goalAngle - facing;
		if (delta < -180) {
			delta = 360 + delta;
		}
		if (delta > 180) {
			delta = -360 + delta;
		}
		return delta;
	}

	private void turnTowards(final float goalAngle, final float delta, final float turnStep) {
		final float absDelta = Math.abs(delta);
		if (absDelta <= turnStep) {
			this.unit.setFacing(goalAngle);
		}
		else {
			this.unit.setFacing(this.unit.getFacing() + (Math.signum(delta) * turnStep));
		}
	}

	/**
	 * The first unit tick after the given game time. The ticks count from the start of the map script, which is game
	 * time 0.
	 */
	protected static double nextUnitTick(final double time) {
		return (StrictMath.floor((time / UNIT_TICK) + TIME_EPSILON) + 1) * UNIT_TICK;
	}

	/** True for casts, which turn and start as {@link #updateCastTurn} measures. */
	protected boolean usesCastTurn() {
		return false;
	}

	/** True while the unit keeps its heading instead of turning after its target. */
	protected boolean isFacingLocked() {
		return false;
	}

	private boolean isAtTarget() {
		return (this.target.getX() == this.unit.getX()) && (this.target.getY() == this.unit.getY());
	}

	/**
	 * Measured on 1.29: a caster turns towards its target before the cast starts (a Paladin facing away from it
	 * starts Holy Light about 0.16 s later, a creep turned 90 degrees away from an ally Bloodlust 0.1 s later).
	 */
	protected boolean isTurningToCast(final boolean withinFacingWindow) {
		if (usesCastTurn()) {
			return !withinFacingWindow;
		}
		return !withinFacingWindow && (this.unit.getUnitType().getTurnRate() > 0) && !this.unit.isBuilding();
	}

	protected boolean isWasInRange() {
		return this.wasInRange;
	}

	public void setDisableMove(final boolean disableMove) {
		this.disableMove = disableMove;
	}

	@Override
	public AbilityTarget getTarget() {
		return this.target;
	}

	@Override
	public <T> T visit(final CBehaviorVisitor<T> visitor) {
		return visitor.accept(this);
	}

}
