package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;

final class CUnitVisibilityState {
	byte invisLevels = 0;
	final List<Integer> detectorLevels = new ArrayList<>(1);
	long detections = 0;
	// Players whose fog has shown this unit at least once: 1.29 reports such a unit fogged, not masked, even under
	// black mask set later.
	long seenByPlayers = 0;
	int ultravisionSources = 0;
	boolean hideMinimapIcon;
	long unselectablePlayers = 0;
	long untargetablePlayers = 0;
}
