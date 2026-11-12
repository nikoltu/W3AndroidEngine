package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.RemovablePathingMapInstance;

final class CUnitPlacementState {
	float flyHeight;
	float turnRate;
	float propWindow;
	Rectangle collisionRectangle;
	RemovablePathingMapInstance pathingInstance;
}
