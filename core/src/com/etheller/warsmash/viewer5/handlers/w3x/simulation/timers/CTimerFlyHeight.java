package com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers;

import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/** Moves a unit's fly height toward a script's SetUnitFlyHeight target at its rate, one step at a time. */
public class CTimerFlyHeight extends CTimer {
	private final CUnit unit;
	private final float targetHeight;
	private final float stepChange;

	public CTimerFlyHeight(final CUnit unit, final float targetHeight, final float rate) {
		this.unit = unit;
		this.targetHeight = targetHeight;
		this.stepChange = rate * WarsmashConstants.SIMULATION_STEP_TIME;
		setTimeoutTime(WarsmashConstants.SIMULATION_STEP_TIME);
		setRepeats(true);
	}

	public CUnit getUnit() {
		return this.unit;
	}

	public float getTargetHeight() {
		return this.targetHeight;
	}

	@Override
	public void onFire(final CSimulation simulation) {
		if (this.unit.isDead() || (simulation.getFlyHeightChange(this.unit) != this)) {
			stop(simulation);
			return;
		}
		final float height = this.unit.getFlyHeight();
		final float left = this.targetHeight - height;
		if (Math.abs(left) <= this.stepChange) {
			this.unit.setFlyHeight(this.targetHeight);
			stop(simulation);
		}
		else {
			this.unit.setFlyHeight(height + Math.copySign(this.stepChange, left));
		}
	}

	private void stop(final CSimulation simulation) {
		setRepeats(false);
		simulation.endFlyHeightChange(this);
	}
}
