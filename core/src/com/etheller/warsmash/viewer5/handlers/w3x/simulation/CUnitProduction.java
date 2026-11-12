package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit.QueueItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityDisableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityBuildInProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.build.AbilityDisableWhileUpgradingVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CWidgetEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Build queues, training, hero revival, research, upgrades and construction of a unit.
 */
final class CUnitProduction {
	private CUnitProduction() {
	}

	/**
	 * Measured in 1.29: a trained unit goes to the first 32 cell, along the same square spiral as
	 * {@link #findNearestFreeCell} from the trainer's centre, whose lower left corner lies outside the trainer's whole
	 * pathing texture (its walkable border too), whatever the unit's collision size. The spiral's first leg turns with
	 * the rally point: none goes down, up and to the right goes up, up and to the left goes left, and so on. The
	 * centre must also be pathable for this unit, because the engine cannot move a unit off a blocked start.
	 */
	static void placeTrainedUnit(final CUnit self, final CSimulation simulation, final CUnit trainer, final AbilityTarget rallyPoint) {
		final ArgbImage trainerPathing = trainer.getUnitType().getBuildingPathingPixelMap();
		if (self.isBuilding() || !trainer.isBuilding() || (trainerPathing == null)) {
			self.nudgeAround(simulation, trainer);
			return;
		}
		final CWorldCollision collision = simulation.getWorldCollision();
		final PathingGrid pathingGrid = simulation.getPathingGrid();
		final float collisionSize = self.unitType.getCollisionSize();
		if (self.placement.collisionRectangle != null) {
			CWidget.tempRect.set(self.placement.collisionRectangle);
		}
		else {
			CWidget.tempRect.setSize(16, 16);
		}
		final float halfWidth = (trainerPathing.getWidth() * 32) / 2f;
		final float halfHeight = (trainerPathing.getHeight() * 32) / 2f;
		final float trainerX = trainer.getX();
		final float trainerY = trainer.getY();
		int direction = 0;
		if ((rallyPoint != null) && (rallyPoint != trainer)) {
			final double rallyDx = rallyPoint.getX() - trainerX;
			final double rallyDy = rallyPoint.getY() - trainerY;
			if ((rallyDx != 0) || (rallyDy != 0)) {
				double angle = StrictMath.toDegrees(StrictMath.atan2(rallyDy, rallyDx));
				if (angle < 0) {
					angle += 360;
				}
				final int[] directionByQuadrant = { 2, 3, 0, 1 };
				direction = directionByQuadrant[Math.min(3, (int) (angle / 90))];
			}
		}
		final float baseX = (float) Math.floor(trainerX / 32f) * 32f;
		final float baseY = (float) Math.floor(trainerY / 32f) * 32f;
		final int[] stepX = { 0, 1, 0, -1 };
		final int[] stepY = { -1, 0, 1, 0 };
		int cellX = 0;
		int cellY = 0;
		for (int legLength = 1; legLength <= 128; legLength++) {
			for (int leg = 0; leg < 2; leg++) {
				for (int step = 0; step < legLength; step++) {
					final float cx = baseX + (cellX * 32f);
					final float cy = baseY + (cellY * 32f);
					if ((Math.abs(cx - trainerX) > halfWidth) || (Math.abs(cy - trainerY) > halfHeight)) {
						CWidget.tempRect.setCenter(cx + 16f, cy + 16f);
						if (!collision.intersectsAnythingOtherThan(CWidget.tempRect, self, self.getMovementType(), false)
								&& pathingGrid.isPathable(cx, cy, self.getMovementType(), collisionSize)
								&& pathingGrid.isPathable(cx + 16f, cy + 16f, self.getMovementType(), collisionSize)) {
							self.setPoint(cx + 16f, cy + 16f, collision, simulation.getRegionManager());
							simulation.unitRepositioned(self);
							return;
						}
					}
					cellX += stepX[direction];
					cellY += stepY[direction];
				}
				direction = (direction + 1) % 4;
			}
		}
		self.nudgeAround(simulation, trainer);
	}

	static int queue(final CUnit self, final CSimulation game, final War3ID rawcode, final QueueItemType queueItemType) {
		for (int i = 0; i < self.production.buildQueue.length; i++) {
			if (self.production.buildQueue[i] == null) {
				setBuildQueueItem(self, game, i, rawcode, queueItemType);
				if ((queueItemType == QueueItemType.UNIT) || (queueItemType == QueueItemType.RESEARCH)
						|| (queueItemType == QueueItemType.SACRIFICE)) {
					final CPlayer player = game.getPlayer(self.playerIndex);
					player.addTechtreeInProgress(game, rawcode);
				}
				return i;
			}
		}
		return -1;
	}

	/**
	 * Measured on 1.29: an item waiting at the front for food takes it as soon as the player's food used drops or food
	 * cap rises, in the middle of whatever changed it; raising the food cap ceiling alone does not wake it.
	 */
	static void retryQueuedFood(final CUnit self, final CSimulation game) {
		final CPlayer player = game.getPlayer(self.playerIndex);
		final War3ID rawcode = self.production.buildQueue[0];
		if ((rawcode == null) || self.production.queuedUnitFoodPaid || self.isDead()) {
			player.removeFoodWaiter(self);
			return;
		}
		final int foodNeeded;
		if (self.production.buildQueueTypes[0] == QueueItemType.UNIT) {
			foodNeeded = game.getUnitData().getUnitType(rawcode).getFoodUsed();
		}
		else if (self.production.buildQueueTypes[0] == QueueItemType.HERO_REVIVE) {
			foodNeeded = game.getUnit(rawcode.getValue()).getUnitType().getFoodUsed();
		}
		else {
			foodNeeded = 0;
		}
		if ((player.getFoodUsed() + foodNeeded) <= player.getFoodCapLimit()) {
			player.removeFoodWaiter(self);
			self.production.queuedUnitFoodPaid = true;
			player.setFoodUsed(player.getFoodUsed() + foodNeeded);
			fireBuildQueueStartEvents(self, game);
		}
	}

	/**
	 * Measured on 1.29: the start events of a queue item fire when it begins progress at the front of the queue,
	 * after it was paid for, and not while it waits behind another item or for food.
	 */
	static void fireBuildQueueStartEvents(final CUnit self, final CSimulation game) {
		final War3ID rawcode = self.production.buildQueue[0];
		if ((rawcode == null) || !self.production.queuedUnitFoodPaid) {
			return;
		}
		switch (self.production.buildQueueTypes[0]) {
		case UNIT:
		case SACRIFICE:
			CUnitEvents.fireTrainEvents(self, game, JassGameEventsWar3.EVENT_UNIT_TRAIN_START,
					JassGameEventsWar3.EVENT_PLAYER_UNIT_TRAIN_START, rawcode);
			break;
		case RESEARCH:
			CUnitEvents.fireResearchEvents(self, game, JassGameEventsWar3.EVENT_UNIT_RESEARCH_START,
					JassGameEventsWar3.EVENT_PLAYER_UNIT_RESEARCH_START, rawcode);
			break;
		default:
			break;
		}
	}

	static float getBuildQueueTimeRemaining(final CUnit self, final CSimulation simulation) {
		if (!self.isBuildQueueActive()) {
			return 0;
		}
		switch (self.production.buildQueueTypes[0]) {
		case RESEARCH: {
			final War3ID rawcode = self.production.buildQueue[0];
			final CUpgradeType trainedUnitType = simulation.getUpgradeData().getType(rawcode);
			return trainedUnitType.getBuildTime(simulation.getPlayer(self.playerIndex).getTechtreeUnlocked(rawcode));
		}
		case SACRIFICE:
		case UNIT: {
			final CUnitType trainedUnitType = simulation.getUnitData().getUnitType(self.production.buildQueue[0]);
			return trainedUnitType.getBuildTime();
		}
		case HERO_REVIVE: {
			return heroReviveTime(simulation, simulation.getUnit(self.production.buildQueue[0].getValue()));
		}
		default:
			return 0;
		}
	}

	static float heroReviveTime(final CSimulation simulation, final CUnit hero) {
		return simulation.getGameplayConstants().getHeroReviveTime(hero.getUnitType().getBuildTime(),
				hero.getHeroData().getHeroLevel())
				* simulation.getPlayer(hero.getPlayerIndex()).getHandicapReviveTime();
	}

	static void cancelBuildQueueItem(final CUnit self, final CSimulation game, final int cancelIndex) {
		cancelBuildQueueItem(self, game, cancelIndex, true);
	}

	/**
	 * The cancel order takes back the last item of the queue, not the one in progress.
	 */
	static void cancelLastBuildQueueItem(final CUnit self, final CSimulation game) {
		int index = self.production.buildQueueTypes.length - 1;
		while ((index > 0) && (self.production.buildQueueTypes[index] == null)) {
			index--;
		}
		cancelBuildQueueItem(self, game, index, true);
	}

	/**
	 * Measured on 1.29: a building that dies refunds its queue and gives back the food before its death events, but
	 * fires no cancel events.
	 */
	static void clearBuildQueueOnDeath(final CUnit self, final CSimulation game) {
		for (int i = self.production.buildQueueTypes.length - 1; i >= 0; i--) {
			cancelBuildQueueItem(self, game, i, false);
		}
	}

	static void cancelBuildQueueItem(final CUnit self, final CSimulation game, final int cancelIndex, final boolean fireEvents) {
		if ((cancelIndex >= 0) && (cancelIndex < self.production.buildQueueTypes.length)) {
			final QueueItemType cancelledType = self.production.buildQueueTypes[cancelIndex];
			if (cancelledType != null) {
				if (fireEvents) {
					// Measured on 1.29: a queued item fires its cancel events too, and they see the player's
					// resources and food from before the refund.
					switch (cancelledType) {
					case UNIT:
					case SACRIFICE:
						CUnitEvents.fireTrainEvents(self, game, JassGameEventsWar3.EVENT_UNIT_TRAIN_CANCEL,
								JassGameEventsWar3.EVENT_PLAYER_UNIT_TRAIN_CANCEL, self.production.buildQueue[cancelIndex]);
						break;
					case RESEARCH:
						CUnitEvents.fireResearchEvents(self, game, JassGameEventsWar3.EVENT_UNIT_RESEARCH_CANCEL,
								JassGameEventsWar3.EVENT_PLAYER_UNIT_RESEARCH_CANCEL, self.production.buildQueue[cancelIndex]);
						break;
					default:
						break;
					}
					if (self.production.buildQueueTypes[cancelIndex] != cancelledType) {
						// a cancel trigger already changed the queue
						return;
					}
				}
				if (cancelIndex == 0) {
					self.production.constructionProgress = 0.0f;
					switch (cancelledType) {
					case SACRIFICE:
					case RESEARCH: {
						break;
					}
					case UNIT: {
						final CPlayer player = game.getPlayer(self.playerIndex);
						final CUnitType unitType = game.getUnitData().getUnitType(self.production.buildQueue[cancelIndex]);
						player.setFoodUsed(player.getFoodUsed() - unitType.getFoodUsed());
						break;
					}
					case HERO_REVIVE: {
						final CPlayer player = game.getPlayer(self.playerIndex);
						final CUnitType unitType = game.getUnit(self.production.buildQueue[cancelIndex].getValue()).getUnitType();
						player.setFoodUsed(player.getFoodUsed() - unitType.getFoodUsed());
						break;
					}
					}
				}
				switch (cancelledType) {
				case RESEARCH: {
					final CPlayer player = game.getPlayer(self.playerIndex);
					final CUpgradeType upgradeType = game.getUpgradeData().getType(self.production.buildQueue[cancelIndex]);
					player.refundFor(upgradeType);
					player.removeTechtreeInProgress(game, self.production.buildQueue[cancelIndex]);
					break;
				}
				case UNIT: {
					final CPlayer player = game.getPlayer(self.playerIndex);
					final CUnitType unitType = game.getUnitData().getUnitType(self.production.buildQueue[cancelIndex]);
					refundQueuedUnit(self, player, unitType, cancelIndex);
					player.removeTechtreeInProgress(game, self.production.buildQueue[cancelIndex]);
					break;
				}
				case SACRIFICE: {
					final CPlayer player = game.getPlayer(self.playerIndex);
					final CUnitType unitType = game.getUnitData().getUnitType(self.production.buildQueue[cancelIndex]);
					refundQueuedUnit(self, player, unitType, cancelIndex);
					player.removeTechtreeInProgress(game, self.production.buildQueue[cancelIndex]);

					self.getWorker().setHidden(false);
					break;
				}
				case HERO_REVIVE: {
					final CPlayer player = game.getPlayer(self.playerIndex);
					final CUnit hero = game.getUnit(self.production.buildQueue[cancelIndex].getValue());
					final CUnitType unitType = hero.getUnitType();
					final CAbilityHero heroData = hero.getHeroData();
					heroData.setReviving(false);
					final CGameplayConstants gameplayConstants = game.getGameplayConstants();
					player.refund(
							gameplayConstants.getHeroReviveGoldCost(unitType.getGoldCost(), heroData.getHeroLevel()),
							gameplayConstants.getHeroReviveLumberCost(unitType.getLumberCost(),
									heroData.getHeroLevel()));
					break;
				}
				}
				shiftBuildQueue(self, game, cancelIndex);
				if (fireEvents && (cancelIndex == 0)) {
					fireBuildQueueStartEvents(self, game);
				}
				self.stateNotifier.queueChanged();
			}
		}
	}

	static void shiftBuildQueue(final CUnit self, final CSimulation game, final int fromIndex) {
		for (int i = fromIndex; i < (self.production.buildQueue.length - 1); i++) {
			setBuildQueueItem(self, game, i, self.production.buildQueue[i + 1], self.production.buildQueueTypes[i + 1]);
			self.production.buildQueueHeroToken[i] = self.production.buildQueueHeroToken[i + 1];
		}
		setBuildQueueItem(self, game, self.production.buildQueue.length - 1, null, null);
		self.production.buildQueueHeroToken[self.production.buildQueue.length - 1] = false;
	}

	/**
	 * A cancelled hero that was trained for a free hero token gives the token back, not its price.
	 */
	static void refundQueuedUnit(final CUnit self, final CPlayer player, final CUnitType unitType, final int index) {
		if (self.production.buildQueueHeroToken[index]) {
			player.setHeroTokens(player.getHeroTokens() + 1);
		}
		else {
			player.refundFor(unitType);
		}
	}

	static void payForQueuedUnit(final CUnit self, final CPlayer player, final CUnitType unitType) {
		int index = self.production.buildQueue.length - 1;
		while ((index > 0) && (self.production.buildQueue[index] == null)) {
			index--;
		}
		final boolean heroToken = unitType.isHero() && (player.getHeroTokens() > 0);
		self.production.buildQueueHeroToken[index] = heroToken;
		if (heroToken) {
			player.setHeroTokens(player.getHeroTokens() - 1);
		}
		else {
			player.chargeFor(unitType);
		}
	}

	static void setBuildQueueItem(final CUnit self, final CSimulation game, final int index, final War3ID rawcode,
			final QueueItemType queueItemType) {
		self.production.buildQueue[index] = rawcode;
		self.production.buildQueueTypes[index] = queueItemType;
		final CPlayer player = game.getPlayer(self.playerIndex);
		if (index == 0) {
			self.production.queuedUnitFoodPaid = true;
			player.removeFoodWaiter(self);
			if (rawcode != null) {
				if (queueItemType == QueueItemType.UNIT) {
					final CUnitType unitType = game.getUnitData().getUnitType(self.production.buildQueue[index]);
					if (unitType.getFoodUsed() != 0) {
						final int newFoodUsed = player.getFoodUsed() + unitType.getFoodUsed();
						if (newFoodUsed <= player.getFoodCapLimit()) {
							player.setFoodUsed(newFoodUsed);
						} else {
							self.production.queuedUnitFoodPaid = false;
							player.addFoodWaiter(self, () -> retryQueuedFood(self, game));
							game.getCommandErrorListener().showInterfaceError(self.playerIndex,
									CommandStringErrorKeys.NOT_ENOUGH_FOOD);
							player.removeTechtreeInProgress(game, rawcode);
						}
					}
				} else if (queueItemType == QueueItemType.HERO_REVIVE) {
					final CUnitType unitType = game.getUnit(self.production.buildQueue[index].getValue()).getUnitType();
					if (unitType.getFoodUsed() != 0) {
						final int newFoodUsed = player.getFoodUsed() + unitType.getFoodUsed();
						if (newFoodUsed <= player.getFoodCapLimit()) {
							player.setFoodUsed(newFoodUsed);
						} else {
							self.production.queuedUnitFoodPaid = false;
							player.addFoodWaiter(self, () -> retryQueuedFood(self, game));
							game.getCommandErrorListener().showInterfaceError(self.playerIndex,
									CommandStringErrorKeys.NOT_ENOUGH_FOOD);
						}
					}
				}
			}
		}
	}

	static void queueTrainingUnit(final CUnit self, final CSimulation game, final War3ID rawcode) {
		final int index = queue(self, game, rawcode, QueueItemType.UNIT);
		if (index >= 0) {
			payForQueuedUnit(self, game.getPlayer(self.playerIndex), game.getUnitData().getUnitType(rawcode));
			if (index == 0) {
				fireBuildQueueStartEvents(self, game);
			}
		}
	}

	static void queueSacrificingUnit(final CUnit self, final CSimulation game, final War3ID rawcode, final CUnit sacrifice) {
		final int index = queue(self, game, rawcode, QueueItemType.SACRIFICE);
		if (index >= 0) {
			sacrifice.setHidden(true);
			self.setWorker(sacrifice, true);

			payForQueuedUnit(self, game.getPlayer(self.playerIndex), game.getUnitData().getUnitType(rawcode));
			if (index == 0) {
				fireBuildQueueStartEvents(self, game);
			}
		}
	}

	static void queueRevivingHero(final CUnit self, final CSimulation game, final CUnit hero) {
		final CAbilityHero heroData = hero == null ? null : hero.getHeroData();
		if ((heroData == null) || heroData.isReviving()) {
			return;
		}
		if (queue(self, game, new War3ID(hero.getHandleId()), QueueItemType.HERO_REVIVE) >= 0) {
			hero.getHeroData().setReviving(true);
			final CPlayer player = game.getPlayer(self.playerIndex);
			final int heroReviveGoldCost = game.getGameplayConstants()
					.getHeroReviveGoldCost(hero.getUnitType().getGoldCost(), hero.getHeroData().getHeroLevel());
			final int heroReviveLumberCost = game.getGameplayConstants()
					.getHeroReviveLumberCost(hero.getUnitType().getLumberCost(), hero.getHeroData().getHeroLevel());
			player.charge(heroReviveGoldCost, heroReviveLumberCost);
		}
	}

	/**
	 * Brings a dead hero back next to the building that revives it (altar or tavern). Food and costs are the
	 * caller's.
	 */
	static void reviveHeroAt(final CUnit self, final CSimulation game, final CUnit reviver, final float lifeFactor,
			final float manaFactor, final float manaStart) {
		final CAbilityHero heroData = self.getHeroData();
		heroData.setReviving(false);
		heroData.setAwaitingRevive(false);
		unlockTechtreeOnRevive(self, game);
		self.death.corpse = false;
		self.death.boneCorpse = false;
		self.death.freshCorpse = false;
		self.death.deathTurnTick = 0;
		self.setX(reviver.getX());
		self.setY(reviver.getY());
		game.getWorldCollision().addUnit(self);
		self.setPoint(reviver.getX(), reviver.getY(), game.getWorldCollision(), game.getRegionManager());
		self.setHidden(false);
		self.setLife(game, self.getMaximumLife() * lifeFactor);
		game.takeShadowPoolIds(self);
		self.setMana((self.getMaximumMana() * manaFactor) + (manaStart * self.unitType.getManaInitial()));
		self.nudgeAround(game, reviver);
		game.unitRepositioned(self); // dont blend animation
	}

	static void unlockTechtreeOnRevive(final CUnit self, final CSimulation game) {
		final CPlayer player = game.getPlayer(self.playerIndex);
		player.addTechtreeUnlocked(game, self.unitType.getTypeId());
		for (final War3ID upgradedFromTypeId : self.production.upgradedFromTypeIds) {
			player.addTechtreeUnlocked(game, upgradedFromTypeId);
		}
	}

	/**
	 * ReviveHero from a script: the hero comes back at the point with the same life and mana rules as an altar.
	 */
	static void reviveHeroAtPoint(final CUnit self, final CSimulation game, final float x, final float y, final boolean doEyecandy) {
		final CAbilityHero heroData = self.getHeroData();
		heroData.setReviving(false);
		heroData.setAwaitingRevive(false);
		unlockTechtreeOnRevive(self, game);
		self.death.corpse = false;
		self.death.boneCorpse = false;
		self.death.freshCorpse = false;
		self.death.deathTurnTick = 0;
		self.setX(x);
		self.setY(y);
		game.getWorldCollision().addUnit(self);
		self.setPoint(x, y, game.getWorldCollision(), game.getRegionManager());
		self.setHidden(false);
		self.setLife(game, self.getMaximumLife());
		game.takeShadowPoolIds(self);
		self.setMana(self.getMaximumMana());
		// Measured on 1.29: a hero revived by script uses its food again at once.
		game.getPlayer(self.playerIndex).setUnitFoodUsed(self, self.unitType.getFoodUsed());
		game.unitRepositioned(self);
	}

	static void queueResearch(final CUnit self, final CSimulation game, final War3ID rawcode) {
		final int index = queue(self, game, rawcode, QueueItemType.RESEARCH);
		if (index >= 0) {
			final CPlayer player = game.getPlayer(self.playerIndex);
			final CUpgradeType upgradeType = game.getUpgradeData().getType(rawcode);
			player.chargeFor(upgradeType);
			if (index == 0) {
				fireBuildQueueStartEvents(self, game);
			}
		}
	}

	static interface RallyProvider {
		float getX();

		float getY();
	}

	/**
	 * Measured: a Castle placed by script trains a Spell Breaker that requires a Keep, as an upgraded
	 * Castle would.
	 */
	static void unlockUpgradedFromTypes(final CUnit self, final CSimulation simulation, final CPlayer player) {
		for (final War3ID upgradedFromTypeId : simulation.getUnitData().getUpgradedFromTypeIds(self.typeId)) {
			self.production.upgradedFromTypeIds.add(upgradedFromTypeId);
			player.addTechtreeUnlocked(simulation, upgradedFromTypeId);
		}
	}

	static void cancelConstruction(final CUnit self, final CSimulation game) {
		final float refundRate = game.getGameplayConstants().getConstructionRefundRate();
		game.getPlayer(self.playerIndex).refund((int) (self.unitType.getGoldCost() * refundRate),
				(int) (self.unitType.getLumberCost() * refundRate));
		self.setLife(game, 0);
		final CPlayer player = game.getPlayer(self.playerIndex);
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_CONSTRUCT_CANCEL);
		if (eventList != null) {
			for (final CWidgetEvent event : new ArrayList<>(eventList)) {
				event.fire(self, CommonTriggerExecutionScope.unitConstructCancelScope(
						JassGameEventsWar3.EVENT_UNIT_CONSTRUCT_CANCEL, event.getTrigger(), self, player));
			}
		}
		player.firePlayerUnitEvents(self,
				(eventType, trigger, unit) -> CommonTriggerExecutionScope.unitConstructCancelScope(eventType, trigger,
						unit, player),
				JassGameEventsWar3.EVENT_PLAYER_UNIT_CONSTRUCT_CANCEL);
	}

	static void cancelUpgrade(final CUnit self, final CSimulation game) {
		if (self.production.upgradeIdType == null) {
			return;
		}
		revertUpgrade(self, game, game.getGameplayConstants().getUpgradeRefundRate());
	}

	static void revertUpgrade(final CUnit self, final CSimulation game, final float refundRate) {
		final CPlayer player = game.getPlayer(self.playerIndex);
		player.setUnitFoodUsed(self, self.unitType.getFoodUsed());
		int goldCost, lumberCost;
		final CUnitType newUpgradeUnitType = game.getUnitData().getUnitType(self.production.upgradeIdType);
		if (game.getGameplayConstants().isRelativeUpgradeCosts()) {
			goldCost = newUpgradeUnitType.getGoldCost() - self.unitType.getGoldCost();
			lumberCost = newUpgradeUnitType.getLumberCost() - self.unitType.getLumberCost();
		} else {
			goldCost = newUpgradeUnitType.getGoldCost();
			lumberCost = newUpgradeUnitType.getLumberCost();
		}
		player.refund((int) (goldCost * refundRate), (int) (lumberCost * refundRate));

		final Iterator<CAbility> abilityIterator = self.abilities.iterator();
		while (abilityIterator.hasNext()) {
			final CAbility ability = abilityIterator.next();
			if (ability instanceof CAbilityBuildInProgress) {
				abilityIterator.remove();
			} else {
				ability.setDisabled(false, CAbilityDisableType.CONSTRUCTION);
				ability.setIconShowing(true);
			}
		}
		self.checkDisabledAbilities(game, false);

		game.unitCancelUpgradingEvent(self, self.production.upgradeIdType);
		self.production.upgradeIdType = null;
		self.production.constructing = false;
		self.production.constructionProgress = 0;
		self.unitAnimationListener.playAnimation(true, PrimaryTag.STAND, SequenceUtils.EMPTY, 0.0f, true);
		CUnitEvents.firePlayerThenUnitEvents(self, game, JassGameEventsWar3.EVENT_UNIT_UPGRADE_CANCEL,
				JassGameEventsWar3.EVENT_PLAYER_UNIT_UPGRADE_CANCEL);
	}

	static void beginUpgrade(final CUnit self, final CSimulation game, final War3ID rawcode) {
		self.production.upgradeIdType = rawcode;
		self.production.constructing = true;
		self.production.constructionProgress = 0;

		final CPlayer player = game.getPlayer(self.playerIndex);
		final CUnitType newUpgradeUnitType = game.getUnitData().getUnitType(rawcode);
		player.setUnitFoodUsed(self, newUpgradeUnitType.getFoodUsed());
		int goldCost, lumberCost;
		if (game.getGameplayConstants().isRelativeUpgradeCosts()) {
			goldCost = newUpgradeUnitType.getGoldCost() - self.unitType.getGoldCost();
			lumberCost = newUpgradeUnitType.getLumberCost() - self.unitType.getLumberCost();
		} else {
			goldCost = newUpgradeUnitType.getGoldCost();
			lumberCost = newUpgradeUnitType.getLumberCost();
		}
		player.charge(goldCost, lumberCost);
		self.add(game, new CAbilityBuildInProgress(game.getHandleIdAllocator().createId()));
		for (final CAbility ability : self.getAbilities()) {
			ability.visit(AbilityDisableWhileUpgradingVisitor.INSTANCE);
		}
		self.checkDisabledAbilities(game, true);
		player.addTechtreeInProgress(game, rawcode);

		game.unitUpgradingEvent(self, rawcode);
		self.unitAnimationListener.playAnimation(true, PrimaryTag.BIRTH, SequenceUtils.EMPTY, 0.0f, true);
		CUnitEvents.firePlayerThenUnitEvents(self, game, JassGameEventsWar3.EVENT_UNIT_UPGRADE_START,
				JassGameEventsWar3.EVENT_PLAYER_UNIT_UPGRADE_START);
	}
}
