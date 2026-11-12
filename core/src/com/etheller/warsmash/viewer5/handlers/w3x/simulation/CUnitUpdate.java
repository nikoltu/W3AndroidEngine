package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.PathingFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.OrientationInterpolation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit.PendingRegionEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit.QueueItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit.StateListenerUpdate;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitOrders.UseAbilityOnTargetByIdVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityDisableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.CAbilityBuildInProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbilityGenericSingleIconPassiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.buff.CPausedTickingBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.BehaviorTargetUnitVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CRegenType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * The unit's step: regeneration, behavior ticks, timed life, drawn facing and natural sleep.
 */
final class CUnitUpdate {
	private CUnitUpdate() {
	}

	static final double DRAWN_FACING_TICK = 0.03;

	static final float DRAWN_FACING_SNAP = 0.1f;

	static void updateDrawnFacing(final CUnit self, final double time) {
		final int tick = (int) StrictMath.floor(((time - WarsmashConstants.SIMULATION_STEP_TIME) / DRAWN_FACING_TICK)
				+ 1e-6);
		if (Float.isNaN(self.stats.drawnFacing) || (self.stats.drawnFacingTick == Integer.MIN_VALUE)) {
			if (Float.isNaN(self.stats.drawnFacing)) {
				self.stats.drawnFacing = self.facing;
			}
			self.stats.drawnFacingTick = tick;
			return;
		}
		if (tick <= self.stats.drawnFacingTick) {
			return;
		}
		int ticks = tick - self.stats.drawnFacingTick;
		self.stats.drawnFacingTick = tick;
		final int row = self.unitType.getOrientationInterpolation();
		final OrientationInterpolation turn = OrientationInterpolation.VALUES[(row < 0)
				|| (row >= OrientationInterpolation.VALUES.length) ? 0 : row];
		while ((ticks-- > 0) && ((self.stats.drawnFacing != self.facing) || (self.stats.drawnTurnVelocity != 0))) {
			float delta = self.facing - self.stats.drawnFacing;
			if (delta < -180) {
				delta += 360;
			}
			if (delta > 180) {
				delta -= 360;
			}
			final float sign = Math.signum(delta);
			final float remaining = (float) StrictMath.toRadians(Math.abs(delta));
			// Measured in 1.29: the turn brakes at a constant deceleration (so its speed follows the root of the angle
			// left) and creeps in over the last few degrees instead of stopping dead.
			final float braking = (float) StrictMath.sqrt(2 * turn.getEndingNegativeAcceleration() * turn.getBrakingScale() * remaining);
			final float creep = (float) StrictMath.pow(Math.min(1, remaining / turn.getSettleRadius()),
					turn.getSettleExponent());
			final float speed = Math.max(0, self.stats.drawnTurnVelocity * sign) + turn.getStartingAcceleration();
			final float limit = Math.min(turn.getMaxVelocity(), braking) * creep;
			self.stats.drawnTurnVelocity = Math.min(speed, limit) * sign;
			final float step = (float) StrictMath.toDegrees(self.stats.drawnTurnVelocity);
			if ((Math.abs(delta) <= Math.abs(step)) || (Math.abs(delta) < DRAWN_FACING_SNAP)) {
				self.stats.drawnFacing = self.facing;
				self.stats.drawnTurnVelocity = 0;
			}
			else {
				self.stats.drawnFacing = (((self.stats.drawnFacing + step) % 360) + 360) % 360;
			}
		}
	}

	/**
	 * Updates one tick of simulation logic and return true if it's time to remove
	 * this unit from the game.
	 */
	static boolean update(final CUnit self, final CSimulation game) {
		if (!self.stateListenersUpdates.isEmpty()) {
			for (final StateListenerUpdate update : self.stateListenersUpdates) {
				switch (update.getUpdateType()) {
				case ADD:
					self.stateNotifier.subscribe(update.listener);
					break;
				case REMOVE:
					self.stateNotifier.unsubscribe(update.listener);
					break;
				}
			}
			self.stateListenersUpdates.clear();
		}
		updateDrawnFacing(self, CTimer.currentTime(game));
		if ((self.pendingRegionEvents != null) && !self.pendingRegionEvents.isEmpty()) {
			final List<PendingRegionEvent> events = self.pendingRegionEvents;
			self.pendingRegionEvents = null;
			for (final PendingRegionEvent event : events) {
				if (event.enter) {
					game.getRegionManager().onUnitEnterRegion(self, event.region);
				}
				else {
					game.getRegionManager().onUnitLeaveRegion(self, event.region);
				}
			}
		}
		// Mana regeneration and stat changes write the fields directly; they reach the state events here. Measured: the
		// original writes a dying unit's life once more a moment after its death.
		CUnitStats.checkStateEvents(self, false);
		CUnitStats.holdFadeWhilePaused(self, game);
		if (self.isDead() && !self.death.corpse && (game.getGameTurnTick() == (self.death.deathTurnTick + 1))) {
			CUnitStats.checkStateEvents(self, true);
		}
		self.aggro.spellMissileSource = null;
		if ((self.aggro.pendingSpellAnswerSource != null) && !self.isDead()) {
			CUnitAggro.resolveSpellAnswer(self, game);
		}
		if ((self.aggro.pendingAttackBackSource != null) && !self.isDead()) {
			CUnitAggro.resolveAttackBack(self, game);
		}
		if (self.aggro.guarding && !self.isDead()) {
			CUnitAggro.updateGuard(self, game);
			updateNightSleep(self, game);
		}
		if (self.isDead()) {
			if (!self.death.falseDeath) {
				final int gameTurnTick = game.getGameTurnTick();
				if (!self.death.corpse) {
					if ((self.placement.collisionRectangle != null) && !self.death.freshCorpse) {
						// Moved this here because doing it on "kill" was able to happen in some cases
						// while also iterating over the units that are in the collision system, and
						// then it hit the "writing while iterating" problem.
						game.getWorldCollision().removeUnit(self);
						// Measured: the original raises or resurrects a unit one second after it dies, during
						// its death animation, so the corpse can be used from the moment of death.
						if (self.isRaisable() && self.isDecays() && !self.unitType.isHero() && !self.isHidden()) {
							self.death.freshCorpse = true;
							game.getWorldCollision().addUnit(self);
						}
					}
					if (gameTurnTick > (self.death.deathTurnTick
							+ CTimer.wholeSteps(self.unitType.getDeathTime()))) {
						self.death.corpse = true;
						if (!self.isRaisable()) {
							self.death.boneCorpse = true;
							// start final phase immediately for "cant raise" case
						}
						if (!self.unitType.isHero()) {
							if (!self.isDecays()) {
								// if we dont raise AND dont decay, then now that death anim is over
								// we just delete the unit
								return true;
							}
							CUnitDeath.fireDecayEvents(self, game);
						} else {
							game.heroDeathEvent(self);
						}
						self.death.deathTurnTick = gameTurnTick;
					}
				} else if (!self.death.boneCorpse) {
					if (game.getGameTurnTick() > (self.death.deathTurnTick
							+ CTimer.wholeSteps(decayTime(self, game, game.getGameplayConstants().getDecayTime())))) {
						self.death.boneCorpse = true;
						self.death.deathTurnTick = gameTurnTick;

						if (self.isRaisable() && !self.death.freshCorpse) {
							game.getWorldCollision().addUnit(self);
						}
					}
				} else if (game.getGameTurnTick() > (self.death.deathTurnTick
						+ CTimer.wholeSteps(decayTime(self, game, self.getEndingDecayTime(game))))) {
					if (self.unitType.isHero()) {
						if (!self.getHeroData().isAwaitingRevive()) {
							self.setHidden(true);
							self.getHeroData().setAwaitingRevive(true);
							game.heroDissipateEvent(self);
						}
						return false;
					}
					return true;
				}
			}
		}
		else {
			CUnitPlacement.innerUpdateNoCollisionMovementType(self, game.getWorldCollision());
			if (!self.paused) {
				if ((self.production.rallyPoint != self) && (self.production.rallyPoint instanceof CUnit)
						&& ((CUnit) self.production.rallyPoint).isDead()) {
					self.setRallyPoint(self);
				}
				if (self.production.constructing) {
					if (!(self.production.constructingPaused || self.blocked.constructionBlocked)) {
						self.production.constructionProgress += WarsmashConstants.SIMULATION_STEP_TIME * game.getCheatBuildSpeed();
					}
					final int buildTime;
					final boolean upgrading = self.isUpgrading();
					if (!upgrading) {
						buildTime = self.unitType.getBuildTime();
						if (!(self.production.constructingPaused || self.blocked.constructionBlocked)) {
							final float healthGain = ((WarsmashConstants.SIMULATION_STEP_TIME * game.getCheatBuildSpeed()) / buildTime)
									* (self.stats.maximumLife * (1.0f - WarsmashConstants.BUILDING_CONSTRUCT_START_LIFE));
							self.setLife(game, Math.min(self.life + healthGain, self.stats.maximumLife));
						}
					} else {
						buildTime = game.getUnitData().getUnitType(self.production.upgradeIdType).getBuildTime();
					}
					if (self.production.constructionProgress >= buildTime) {
						self.production.constructing = false;
						self.production.constructingPaused = false;
						self.production.constructionProgress = 0;
						if (self.production.constructionConsumesWorker) {
							if (self.production.worker != null) {
								game.removeUnit(self.production.worker);
								self.production.worker = null;
							}
						} else {
							popoutWorker(self, game);
						}
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
						final CPlayer player = game.getPlayer(self.playerIndex);
						if (upgrading) {
							if (self.unitType.getFoodMade() != 0) {
								player.setFoodCap(player.getFoodCap() - self.unitType.getFoodMade());
							}
							final War3ID previousTypeId = self.typeId;
							self.setTypeId(game, self.production.upgradeIdType, true);
							self.production.upgradeIdType = null;
							// Measured: an upgraded building still meets requirements for the types it was upgraded
							// from, so a Castle unlocks what a Keep unlocks.
							self.production.upgradedFromTypeIds.add(previousTypeId);
							player.addTechtreeUnlocked(game, previousTypeId);
						}
						if (self.unitType.getFoodMade() != 0) {
							player.setFoodCap(player.getFoodCap() + self.unitType.getFoodMade());
						}
						player.removeTechtreeInProgress(game, self.unitType.getTypeId());
						if (!upgrading) {
							player.addTechtreeUnlocked(game, self.unitType.getTypeId());
							player.scoreUnitGained(self.unitType);
							game.unitConstructFinishEvent(self);
							self.fireConstructFinishEvents(game);
						} else {
							game.unitUpgradeFinishEvent(self); 
							CUnitEvents.firePlayerThenUnitEvents(self, game, JassGameEventsWar3.EVENT_UNIT_UPGRADE_FINISH,
									JassGameEventsWar3.EVENT_PLAYER_UNIT_UPGRADE_FINISH);
						}
						if (upgrading || true) {
							// TODO shouldnt need to play stand here, probably
							self.getUnitAnimationListener().playAnimation(false, PrimaryTag.STAND, SequenceUtils.EMPTY, 1.0f,
									true);
						}
						self.stateNotifier.ordersChanged();
					}
					for (int i = self.abilities.size() - 1; i >= 0; i--) {
						// okay if it removes self from this during onTick() because of reverse
						// iteration order
						CAbility abil = self.abilities.get(i);
						if (abil.getAbilityCategory() == CAbilityCategory.BUFF) {
							abil.onTick(game, self);
						}
					}
				} else {
					final War3ID queuedRawcode = self.production.buildQueue[0];
					if (queuedRawcode != null) {
						// queue step forward
						if (self.production.queuedUnitFoodPaid) {
							if (!self.blocked.trainingBlocked) {
								self.production.constructionProgress += WarsmashConstants.SIMULATION_STEP_TIME * game.getCheatBuildSpeed();
							}
						} else {
							if ((self.production.buildQueueTypes[0] == QueueItemType.UNIT)
									|| (self.production.buildQueueTypes[0] == QueueItemType.HERO_REVIVE)) {
								// waits for the player's food to change, see retryQueuedFood
							} else if (self.production.buildQueueTypes[0] == QueueItemType.SACRIFICE) {
								self.production.queuedUnitFoodPaid = true;
							} else {
								self.production.queuedUnitFoodPaid = true;
								Log.warn(
										"Unpaid food for non unit queue item ???? Attempting to correct this by setting paid=true");
							}
						}
						if (self.production.buildQueueTypes[0] == QueueItemType.UNIT) {
							final CUnitType trainedUnitType = game.getUnitData().getUnitType(queuedRawcode);
							if (self.production.constructionProgress >= trainedUnitType.getBuildTime()) {
								self.production.constructionProgress = 0;
								final CUnit trainedUnit = game.createUnit(queuedRawcode, self.playerIndex, self.getX(),
										self.getY(), game.getGameplayConstants().getBuildingAngle());
								// dont add food cost to player 2x
								trainedUnit.setFoodUsed(trainedUnitType.getFoodUsed());
								final CPlayer player = game.getPlayer(self.playerIndex);
								player.setUnitFoodMade(trainedUnit, trainedUnitType.getFoodMade());
								player.removeTechtreeInProgress(game, queuedRawcode);
								player.addTechtreeUnlocked(game, queuedRawcode);
								player.scoreUnitGained(trainedUnitType);
								trainedUnit.placeTrainedUnit(game, self, self.production.rallyPoint);
								// Measured on 1.29: the rally order is given before the train-finish event runs.
								if (self.production.rallyPoint != null) {
									final int rallyOrderId = OrderIds.smart;
									self.production.rallyPoint.visit(UseAbilityOnTargetByIdVisitor.INSTANCE.reset(game,
											trainedUnit, trainedUnit.playerIndex, rallyOrderId));
								}
								self.fireTrainFinishEvents(game, trainedUnit);
								game.unitTrainedEvent(self, trainedUnit);
								CUnitProduction.shiftBuildQueue(self, game, 0);
								CUnitProduction.fireBuildQueueStartEvents(self, game);
								self.stateNotifier.queueChanged();
							}
						} else if (self.production.buildQueueTypes[0] == QueueItemType.SACRIFICE) {
							final CUnitType trainedUnitType = game.getUnitData().getUnitType(queuedRawcode);
							if (self.production.constructionProgress >= trainedUnitType.getBuildTime()) {
								self.production.constructionProgress = 0;
								final CUnit trainedUnit = game.createUnit(queuedRawcode, self.playerIndex, self.getX(),
										self.getY(), game.getGameplayConstants().getBuildingAngle());

								game.removeUnit(self.getWorker());

								// dont add food cost to player 2x
								trainedUnit.setFoodUsed(trainedUnitType.getFoodUsed());
								final CPlayer player = game.getPlayer(self.playerIndex);
								player.setUnitFoodMade(trainedUnit, trainedUnitType.getFoodMade());
								player.removeTechtreeInProgress(game, queuedRawcode);
								player.addTechtreeUnlocked(game, queuedRawcode);
								player.scoreUnitGained(trainedUnitType);
								trainedUnit.placeTrainedUnit(game, self, self.production.rallyPoint);
								// Measured on 1.29: the rally order is given before the train-finish event runs.
								if (self.production.rallyPoint != null) {
									final int rallyOrderId = OrderIds.smart;
									self.production.rallyPoint.visit(UseAbilityOnTargetByIdVisitor.INSTANCE.reset(game,
											trainedUnit, trainedUnit.playerIndex, rallyOrderId));
								}
								self.fireTrainFinishEvents(game, trainedUnit);
								game.unitTrainedEvent(self, trainedUnit);
								CUnitProduction.shiftBuildQueue(self, game, 0);
								CUnitProduction.fireBuildQueueStartEvents(self, game);
								self.stateNotifier.queueChanged();
							}
						} else if (self.production.buildQueueTypes[0] == QueueItemType.HERO_REVIVE) {
							final CUnit revivingHero = game.getUnit(queuedRawcode.getValue());
							final CUnitType trainedUnitType = revivingHero.getUnitType();
							final CGameplayConstants gameplayConstants = game.getGameplayConstants();
							if (self.production.constructionProgress >= CUnitProduction.heroReviveTime(game, revivingHero)) {
								self.production.constructionProgress = 0;
								revivingHero.reviveHeroAt(game, self, gameplayConstants.getHeroReviveLifeFactor(),
										gameplayConstants.getHeroReviveManaFactor(),
										gameplayConstants.getHeroReviveManaStart());
								// dont add food cost to player 2x
								revivingHero.setFoodUsed(trainedUnitType.getFoodUsed());
								final CPlayer player = game.getPlayer(self.playerIndex);
								player.setUnitFoodMade(revivingHero, trainedUnitType.getFoodMade());
								// NOTE: Dont "add techtree unlocked" here, because hero doesn't lose that
								// status upon death
								game.heroReviveEvent(self, revivingHero);
								if (self.production.rallyPoint != null) {
									final int rallyOrderId = OrderIds.smart;
									self.production.rallyPoint.visit(UseAbilityOnTargetByIdVisitor.INSTANCE.reset(game,
											revivingHero, revivingHero.playerIndex, rallyOrderId));
								}
								CUnitProduction.shiftBuildQueue(self, game, 0);
								CUnitProduction.fireBuildQueueStartEvents(self, game);
								self.stateNotifier.queueChanged();
							}
						} else if (self.production.buildQueueTypes[0] == QueueItemType.RESEARCH) {
							final CUpgradeType trainedUnitType = game.getUpgradeData().getType(queuedRawcode);
							// TODO the "getBuildTime" math below probably would be better served to have
							// been cached, for performance, since we are in the update method. But maybe it
							// doens't matter.
							final CPlayer player = game.getPlayer(self.playerIndex);
							final int techtreeUnlocked = player.getTechtreeUnlocked(queuedRawcode);
							if (self.production.constructionProgress >= trainedUnitType.getBuildTime(techtreeUnlocked)) {
								self.production.constructionProgress = 0;
								// off the queue first: a finished research takes back the queued items of its kind
								CUnitProduction.shiftBuildQueue(self, game, 0);
								player.removeTechtreeInProgress(game, queuedRawcode);
								player.addTechResearched(game, queuedRawcode, 1);
								self.fireResearchFinishEvents(game, queuedRawcode);
								game.researchFinishEvent(self, queuedRawcode,
										player.getTechtreeUnlocked(queuedRawcode));
								CUnitProduction.fireBuildQueueStartEvents(self, game);
								self.stateNotifier.queueChanged();
							}
						}
					}
					final float lifeBeforeRegen = self.life;
					final float manaBeforeRegen = self.stats.mana;
					if ((self.life < self.stats.maximumLife) || (self.stats.currentLifeRegenPerTick < 0)) {
						final CRegenType lifeRegenType = self.getUnitType().getLifeRegenType();
						boolean active = false;
						switch (lifeRegenType) {
						case ALWAYS:
							active = true;
							break;
						case DAY:
							active = game.isDay();
							break;
						case NIGHT:
							active = game.isNight();
							break;
						case BLIGHT:
							active = PathingFlags.isPathingFlag(game.getPathingGrid().getPathing(self.getX(), self.getY()),
									PathingFlags.BLIGHTED);
							break;
						default:
							active = false;
						}
						if (active) {
							float lifePlusRegen = self.life + self.stats.currentLifeRegenPerTick;
							if (lifePlusRegen > self.stats.maximumLife) {
								lifePlusRegen = self.stats.maximumLife;
							}
							self.setLife(game, lifePlusRegen);
						} else {
							float lifePlusRegen = (self.life + self.stats.currentLifeRegenPerTick)
									- self.stats.baseLifeRegenPerTick;
							if (lifePlusRegen > self.stats.maximumLife) {
								lifePlusRegen = self.stats.maximumLife;
							}
							self.setLife(game, lifePlusRegen);
						}
					}
					if ((self.stats.mana < self.stats.maximumMana) || (self.stats.currentManaRegenPerTick < 0)) {
						float manaPlusRegen = self.stats.mana + self.stats.currentManaRegenPerTick;
						if (manaPlusRegen > self.stats.maximumMana) {
							manaPlusRegen = self.stats.maximumMana;
						}
						self.stats.mana = Math.max(manaPlusRegen, 0);
						self.stateNotifier.manaChanged();
					}
					self.stats.regenTick = game.getGameTurnTick();
					self.stats.lifeRegenGiven = self.life - lifeBeforeRegen;
					self.stats.lifeAfterRegen = self.life;
					self.stats.manaRegenGiven = self.stats.mana - manaBeforeRegen;
					self.stats.manaAfterRegen = self.stats.mana;
					if (!self.hidden && game.castsSpellsByItself(self.playerIndex)) {
						self.tryCreepCast(game);
					}
					// Autocast spells (Heal, Slow, Spell Steal) are also cast in the middle of a fight, not only
					// by idle units.
					if ((self.orders.currentBehavior instanceof CBehaviorAttack) && self.auto.autoCast
							&& (self.autocastAbility != null)
							&& ((game.getGameTurnTick() % CUnitAcquisition.AUTOCAST_COMBAT_SCAN_TICKS) == 0)) {
						self.autoAcquireAutocastTargets(game, self.movement.moveDisabled);
					}
					if (self.orders.currentBehavior != null) {
						final CUnit target = self.orders.currentBehavior.visit(BehaviorTargetUnitVisitor.INSTANCE);
						if ((target != null) && !game.getPlayer(self.playerIndex).hasAlliance(target.getPlayerIndex(),
								CAllianceType.SHARED_VISION)) {
							if (self.attackFogMod.getPlayerIndex() != target.getPlayerIndex()) {
								game.getPlayer(self.attackFogMod.getPlayerIndex()).removeFogModifer(game,
										self.attackFogMod);
								self.attackFogMod.setPlayerIndex(target.getPlayerIndex());
								game.getPlayer(target.getPlayerIndex()).addFogModifer(game, self.attackFogMod, false);
							}
						} else {
							if (self.attackFogMod.getPlayerIndex() != self.playerIndex) {
								game.getPlayer(self.attackFogMod.getPlayerIndex()).removeFogModifer(game,
										self.attackFogMod);
								self.attackFogMod.setPlayerIndex(self.playerIndex);
							}
						}

						self.beginBehavior(game, self.orders.currentBehavior.update(game));
					} else {
						// check to auto acquire targets
						self.autoAcquireTargets(game, self.movement.moveDisabled);
					}
//					for (CAbility ability : new ArrayList<>(this.abilities)) {
//						ability.onTick(game, this);
//					}
					for (int i = self.abilities.size() - 1; i >= 0; i--) {
						// okay if it removes self from this during onTick() because of reverse
						// iteration order; a timed life running out kills the unit, which can take several
						if (i < self.abilities.size()) {
							self.abilities.get(i).onTick(game, self);
						}
					}
					final GlobalScope globalScope = game.getGlobalScope();
					for (int i = self.onTickTriggers.size() - 1; i >= 0; i--) {
						final Trigger trigger = self.onTickTriggers.get(i);
						final TriggerExecutionScope triggerExecutionScope = CommonTriggerExecutionScope
								.unitTriggerScope(null, trigger, self);
						trigger.fire(globalScope, triggerExecutionScope, triggerExecutionScope);
					}
				}
			} else if (!self.production.constructing) {
				// Paused units only allow passives to function. Buffs don't tick (except a few)
				// Base and bonus life/mana regen function, but regen from Str/Int doesn't
				if ((self.life < self.stats.maximumLife)
						|| ((self.stats.currentLifeRegenPerTick - self.stats.lifeRegenStrengthBonus) < 0)) {
					final CRegenType lifeRegenType = self.getUnitType().getLifeRegenType();
					boolean active = false;
					switch (lifeRegenType) {
					case ALWAYS:
						active = true;
						break;
					case DAY:
						active = game.isDay();
						break;
					case NIGHT:
						active = game.isNight();
						break;
					case BLIGHT:
						active = PathingFlags.isPathingFlag(game.getPathingGrid().getPathing(self.getX(), self.getY()),
								PathingFlags.BLIGHTED);
						break;
					default:
						active = false;
					}
					if (active) {
						float lifePlusRegen = (self.life + self.stats.currentLifeRegenPerTick)
								- (self.stats.lifeRegenStrengthBonus * WarsmashConstants.SIMULATION_STEP_TIME);
						if (lifePlusRegen > self.stats.maximumLife) {
							lifePlusRegen = self.stats.maximumLife;
						}
						self.setLife(game, lifePlusRegen);
					}
				}
				if ((self.stats.mana < self.stats.maximumMana)
						|| ((self.stats.currentManaRegenPerTick - self.stats.manaRegenIntelligenceBonus) < 0)) {
					float manaPlusRegen = (self.stats.mana + self.stats.currentManaRegenPerTick)
							- (self.stats.manaRegenIntelligenceBonus * WarsmashConstants.SIMULATION_STEP_TIME);
					if (manaPlusRegen > self.stats.maximumMana) {
						manaPlusRegen = self.stats.maximumMana;
					}
					self.stats.mana = Math.max(manaPlusRegen, 0);
					self.stateNotifier.manaChanged();
				}

				// try to remove the below line of code, once all the abilities are on tick
				// triggers and none of them just magically assume tick to be called on all
				// abilities regardless of what they are (just add this stuff as global,
				// non-unit tick triggers)
				for (int i = self.abilities.size() - 1; i >= 0; i--) {
					// okay if it removes self from this during onTick() because of reverse
					// iteration order
					if ((i < self.abilities.size())
							&& ((self.abilities.get(i) instanceof AbilityGenericSingleIconPassiveAbility)
									|| (self.abilities.get(i) instanceof CPausedTickingBuff))) {
						self.abilities.get(i).onTick(game, self);
					}
				}
			}
		}
		return false;

	}

	static void popoutWorker(final CUnit self, final CSimulation game) {
		if ((self.production.worker != null) && self.production.workerInside) {
			self.production.worker.endInsideInvulnerable(game);
			self.production.worker.setHidden(false);
			self.production.worker.setAcceptingOrders(true);
			self.production.worker.nudgeAround(game, self);
			if (self.production.constructionConsumesWorker) {
				game.getPlayer(self.production.worker.getPlayerIndex()).setUnitFoodUsed(self.production.worker,
						self.production.worker.getUnitType().getFoodUsed());
			}
		}
		self.production.worker = null;
	}

	/**
	 * Measured on 1.29: a guarding creep that can sleep is asleep the moment it is idle at night (made at night, at
	 * nightfall, when UnitAddSleep allows it). Called for help or UnitWakeUp, it stays awake at least 2 s; a hit gives
	 * no such delay and ends one already running. A creep that chased and lost its target
	 * sleeps once its walk back ends; otherwise it falls asleep where it stands. It snores under the creep sleep
	 * effect and wakes at daybreak.
	 */
	static void updateNightSleep(final CUnit self, final CSimulation game) {
		if (!self.aggro.guarding || self.isDead()) {
			return;
		}
		if (!self.canSleep() || ((self.acquisition.naturalSleep == null)
				&& (game.getPlayer(self.playerIndex).getPlayerState(game, CPlayerState.NO_CREEP_SLEEP) != 0))) {
			wakeFromNaturalSleep(self, game);
			return;
		}
		if (self.acquisition.naturalSleep != null) {
			if (game.isDay() || !self.stateModBuffs.contains(self.acquisition.naturalSleep)) {
				wakeFromNaturalSleep(self, game);
			}
			return;
		}
		final int tick = game.getGameTurnTick();
		if (game.isNight() && self.isIdleForAttack()
				&& ((tick - self.acquisition.lastWakeTick) >= CTimer.wholeSteps(CUnit.SLEEP_AFTER_WAKE_SECONDS))) {
			self.acquisition.naturalSleep = new StateModBuff(StateModBuffType.SLEEPING, 1);
			self.addStateModBuff(self.acquisition.naturalSleep);
			self.computeUnitState(game, StateModBuffType.SLEEPING);
			self.acquisition.naturalSleepEffect = game.createPersistentSpellEffectOnUnit(self, CUnit.CREEP_SLEEP, CEffectType.CASTER);
		}
	}

	static void wakeFromNaturalSleep(final CUnit self, final CSimulation game) {
		if (self.acquisition.naturalSleep != null) {
			self.removeStateModBuff(self.acquisition.naturalSleep);
			self.computeUnitState(game, StateModBuffType.SLEEPING);
			self.acquisition.naturalSleep = null;
		}
		if (self.acquisition.naturalSleepEffect != null) {
			self.acquisition.naturalSleepEffect.remove();
			self.acquisition.naturalSleepEffect = null;
		}
	}

	static void wakeByHit(final CUnit self, final CSimulation game) {
		self.acquisition.lastWakeTick = Integer.MIN_VALUE / 2;
		wakeFromNaturalSleep(self, game);
	}

	/**
	 * Measured on 1.29: a creep made at night is asleep at once, unless an enemy it would attack is already in its
	 * acquisition range; then it fights instead.
	 */
	static void onCreatedNightSleep(final CUnit self, final CSimulation game) {
		if (self.aggro.guarding && self.canSleep() && game.isNight() && self.isIdleForAttack()
				&& !self.autoAcquireAttackTargets(game, self.movement.moveDisabled)) {
			updateNightSleep(self, game);
		}
	}

	/** iocainepowder makes corpses decay faster. */
	private static float decayTime(final CUnit self, final CSimulation game, final float decayTime) {
		return game.getPlayer(self.getPlayerIndex()).hasCheat(CReleaseCheats.FAST_DEATH)
				? decayTime / CReleaseCheats.FAST_DECAY_FACTOR
				: decayTime;
	}
}
