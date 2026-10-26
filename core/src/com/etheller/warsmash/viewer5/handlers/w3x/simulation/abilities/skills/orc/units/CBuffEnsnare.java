package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * Ensnare and Web: the unit cannot move; a flying unit is pulled down to the air unit height and is attacked as
 * though it were a land unit.
 */
public class CBuffEnsnare extends CBuffTimed {
	private final StateModBuff snareBuff;
	private final boolean flying;
	private final float airUnitHeight;
	private final float airUnitLowerDuration;
	private float lowerStepPerTick;
	private boolean addedGroundType;
	private boolean removedFlyingType;

	public CBuffEnsnare(final int handleId, final War3ID alias, final float duration, final boolean flying,
			final float airUnitHeight) {
		this(handleId, alias, duration, flying, airUnitHeight, 0);
	}

	public CBuffEnsnare(final int handleId, final War3ID alias, final float duration, final boolean flying,
			final float airUnitHeight, final float airUnitLowerDuration) {
		super(handleId, alias, alias, duration);
		this.snareBuff = new StateModBuff(StateModBuffType.SNARED, 1);
		this.flying = flying;
		this.airUnitHeight = airUnitHeight;
		this.airUnitLowerDuration = airUnitLowerDuration;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		unit.addStateModBuff(this.snareBuff);
		unit.computeUnitState(game, StateModBuffType.SNARED);
		if (this.flying) {
			final float startHeight = unit.getUnitType().getDefaultFlyingHeight();
			final float targetHeight = Math.min(this.airUnitHeight, startHeight);
			final int lowerTicks = (int) (this.airUnitLowerDuration / WarsmashConstants.SIMULATION_STEP_TIME);
			if (lowerTicks > 0) {
				// snaring drops the unit to 0 height; it comes down from where it flew instead
				unit.setFlyHeight(startHeight);
				this.lowerStepPerTick = (startHeight - targetHeight) / lowerTicks;
			}
			else {
				unit.setFlyHeight(targetHeight);
			}
			this.addedGroundType = !unit.isUnitType(CUnitTypeJass.GROUND)
					&& unit.addUnitType(game, CUnitTypeJass.GROUND);
			this.removedFlyingType = unit.removeUnitType(game, CUnitTypeJass.FLYING);
		}
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		unit.removeStateModBuff(this.snareBuff);
		unit.computeUnitState(game, StateModBuffType.SNARED);
		if (this.addedGroundType) {
			unit.removeUnitType(game, CUnitTypeJass.GROUND);
			this.addedGroundType = false;
		}
		if (this.removedFlyingType) {
			unit.addUnitType(game, CUnitTypeJass.FLYING);
			this.removedFlyingType = false;
		}
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if (this.lowerStepPerTick > 0) {
			final float targetHeight = Math.min(this.airUnitHeight, unit.getUnitType().getDefaultFlyingHeight());
			final float height = unit.getFlyHeight() - this.lowerStepPerTick;
			if (height <= targetHeight) {
				unit.setFlyHeight(targetHeight);
				this.lowerStepPerTick = 0;
			}
			else {
				unit.setFlyHeight(height);
			}
		}
		super.onTick(game, unit);
	}

	@Override
	public boolean isDispellable() {
		return true;
	}

	@Override
	public boolean isMagic() {
		return true;
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isPositive() {
		return false;
	}
}
