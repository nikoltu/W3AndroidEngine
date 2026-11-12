package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.Set;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

final class CUnitAcquisitionState {
	int nextIdleAutocastScanTick;
	int nextIdleAcquireScanTick;
	int firstIdleAcquireScanTick;
	int nextCreepCastTick;
	boolean creepCastFighting;
	boolean creepCastAfterWhirl;
	int creepCastThinks;
	Set<Integer> creepCastOnce;
	StateModBuff naturalSleep;
	SimulationRenderComponentModel naturalSleepEffect;
	int lastWakeTick = Integer.MIN_VALUE / 2;
}
