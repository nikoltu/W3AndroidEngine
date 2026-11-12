package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;

final class CUnitProductionState {
	boolean constructing = false;
	boolean constructingPaused = false;
	War3ID upgradeIdType = null;
	final List<War3ID> upgradedFromTypeIds = new ArrayList<>();
	float constructionProgress;
	CUnit worker;
	boolean workerInside;
	final War3ID[] buildQueue = new War3ID[WarsmashConstants.BUILD_QUEUE_SIZE];
	final CUnit.QueueItemType[] buildQueueTypes = new CUnit.QueueItemType[WarsmashConstants.BUILD_QUEUE_SIZE];
	final boolean[] buildQueueHeroToken = new boolean[WarsmashConstants.BUILD_QUEUE_SIZE];
	boolean queuedUnitFoodPaid = false;
	AbilityTarget rallyPoint;
	boolean constructionConsumesWorker;
}
