package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;

final class CUnitMovementState {
	boolean moveDisabled = false;
	boolean noUnitCollision = false;
	boolean noBuildingCollision = false;
	MovementType movementOverride = null;
	boolean pathingDisabled;
	MovementType nextMovementOverride = null;
}
