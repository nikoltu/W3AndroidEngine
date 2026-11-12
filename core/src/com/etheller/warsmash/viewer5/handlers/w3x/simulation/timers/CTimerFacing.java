package com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers;

import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/**
 * A script's SetUnitFacingTimed: the unit turns the shorter way at an even speed that would take the duration, and
 * stops when the duration is over. Any other change of its facing (an order, SetUnitFacing) ends the turn.
 * <p>
 * Measured on 1.29: the turn stops for good once less than about 12 degrees are left, so the unit never reaches the
 * exact facing (90 degrees in one second ends at 81).
 */
public class CTimerFacing extends CTimer {
	private static final float STOP_ANGLE = 12f;

	private final CUnit unit;
	private final float stepAngle;
	private float angleLeft;
	private int stepsLeft;
	private float lastFacing;

	public CTimerFacing(final CUnit unit, final float facing, final float duration) {
		this.unit = unit;
		float delta = ((facing - unit.getFacing()) % 360 + 360) % 360;
		if (delta > 180) {
			delta -= 360;
		}
		this.stepsLeft = Math.max(1, wholeSteps(duration));
		this.stepAngle = delta / this.stepsLeft;
		this.angleLeft = Math.abs(delta);
		this.lastFacing = unit.getFacing();
		setTimeoutTime(WarsmashConstants.SIMULATION_STEP_TIME);
		setRepeats(true);
	}

	@Override
	public void onFire(final CSimulation simulation) {
		if (this.unit.isDead() || (this.unit.getFacing() != this.lastFacing)
				|| (simulation.getFacingTurn(this.unit) != this)) {
			stop(simulation);
			return;
		}
		if (this.angleLeft < STOP_ANGLE) {
			stop(simulation);
			return;
		}
		this.unit.setFacingAndDrawnFacing(this.unit.getFacing() + this.stepAngle);
		this.lastFacing = this.unit.getFacing();
		this.angleLeft -= Math.abs(this.stepAngle);
		if (--this.stepsLeft <= 0) {
			stop(simulation);
		}
	}

	public CUnit getUnit() {
		return this.unit;
	}

	private void stop(final CSimulation simulation) {
		setRepeats(false);
		simulation.endFacingTurn(this);
	}
}
