package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;
import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementEffectPriority;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementResult;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDeathReplacementStacking;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CUnitDeathVisionFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CWidgetEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.TextTagConfigType;

/**
 * Killing, decaying, reviving and corpse handling of a unit.
 */
final class CUnitDeath {
	private CUnitDeath() {
	}

	static void kill(final CUnit self, final CSimulation simulation, final CUnit source) {
		self.deadScriptLife = Float.NaN;
		self.beginBehavior(simulation, null, true);

		final CUnitDeathReplacementResult result = new CUnitDeathReplacementResult();
		CUnitDeathReplacementStacking allowContinue = new CUnitDeathReplacementStacking();
		for (final CUnitDeathReplacementEffectPriority priority : CUnitDeathReplacementEffectPriority.values()) {
			if (allowContinue.isAllowStacking()) {
				for (final CUnitDeathReplacementEffect effect : new ArrayList<>(
						self.deathReplacementEffects.get(priority))) {
					if (allowContinue.isAllowSamePriorityStacking()) {
						allowContinue = effect.onDeath(simulation, self, source, result);
					}
				}
			}
		}
		if (result.isReviving()) {
			return;
		}

		self.orders.orderQueue.clear();
		// Measured on 1.29: a unit killed while walking under an order reads current order 0.
		self.orders.lastStartedOrder = null;
		self.killPathingInstance();
		self.freeShadowPoolIds(simulation.getHandleIdAllocator());
		CUnitUpdate.popoutWorker(self, simulation);
		for (int i = self.abilities.size() - 1; i >= 0; i--) {
			// okay if it removes self from this during onDeath() because of reverse
			// iteration order
			if (i < self.abilities.size()) {
				self.abilities.get(i).onDeath(simulation, self);
			}
		}
		simulation.getPlayer(self.playerIndex).addFogModifer(simulation, new CUnitDeathVisionFogModifier(self), false);
		if (source != null) {
			simulation.getPlayer(source.getPlayerIndex()).addFogModifer(simulation,
					new CUnitDeathVisionFogModifier(self), false);
		}
		if (self.attackFogMod.getPlayerIndex() != self.playerIndex) {
			simulation.getPlayer(self.attackFogMod.getPlayerIndex()).removeFogModifer(simulation, self.attackFogMod);
			self.attackFogMod.setPlayerIndex(self.playerIndex);
		}

		if (result.isReincarnating()) {
			return;
		}

		if (self.production.constructing) {
			simulation.createDeathExplodeEffect(self, self.death.explodesOnDeathBuffId);
		} else {
			self.death.deathTurnTick = simulation.getGameTurnTick();
		}

		final CPlayer player = simulation.getPlayer(self.playerIndex);
		if (self.food.foodMade != 0) {
			player.setUnitFoodMade(self, 0);
		}
		if (self.food.foodUsed != 0) {
			player.setUnitFoodUsed(self, 0);
		}
		// A dead hero awaiting revive no longer counts as unlocked (requirements, GetPlayerTechCount); the
		// per-type limit still counts it, see CPlayer.isTechtreeAllowedByMax.
		if (self.production.constructing) {
			player.removeTechtreeInProgress(simulation, self.unitType.getTypeId());
		} else if (!self.death.animatedCorpse) {
			player.removeTechtreeUnlocked(simulation, self.unitType.getTypeId());
			for (final War3ID upgradedFromTypeId : self.production.upgradedFromTypeIds) {
				player.removeTechtreeUnlocked(simulation, upgradedFromTypeId);
			}
		}

		// Award hero experience; illusions give neither experience nor bounty
		if ((source != null) && !self.illusionState.illusion) {
			final CPlayer sourcePlayer = simulation.getPlayer(source.getPlayerIndex());
			if (!sourcePlayer.hasAlliance(self.playerIndex, CAllianceType.PASSIVE)) {
				sourcePlayer.scoreKill(self.unitType);
				if (player.getPlayerState(simulation, CPlayerState.GIVES_BOUNTY) > 0) {
					int goldBountyAwarded = self.unitType.getGoldBountyAwardedBase();
					final int goldBountyAwardedDice = self.unitType.getGoldBountyAwardedDice();
					final int goldBountyAwardedSides = self.unitType.getGoldBountyAwardedSides();
					for (int i = 0; i < goldBountyAwardedDice; i++) {
						final int singleRoll = goldBountyAwardedSides == 0 ? 0
								: simulation.getSeededRandom().nextInt(goldBountyAwardedSides);
						goldBountyAwarded += singleRoll + 1;
					}
					if (goldBountyAwarded > 0) {
						sourcePlayer.addGold(goldBountyAwarded);
						simulation.spawnTextTag(self, sourcePlayer.getId(), TextTagConfigType.GOLD_BOUNTY,
								goldBountyAwarded);
					}
					int lumberBountyAwarded = self.unitType.getLumberBountyAwardedBase();
					final int lumberBountyAwardedDice = self.unitType.getLumberBountyAwardedDice();
					final int lumberBountyAwardedSides = self.unitType.getLumberBountyAwardedSides();
					for (int i = 0; i < lumberBountyAwardedDice; i++) {
						lumberBountyAwarded += simulation.getSeededRandom().nextInt(lumberBountyAwardedSides) + 1;
					}
					if (lumberBountyAwarded > 0) {
						sourcePlayer.addLumber(lumberBountyAwarded);
					}
				}
				final CGameplayConstants gameplayConstants = simulation.getGameplayConstants();
				if (gameplayConstants.isBuildingKillsGiveExp() || !source.isBuilding()) {
					final CUnit killedUnit = self;
					final CAbilityHero killedUnitHeroData = self.getHeroData();
					final boolean killedUnitIsAHero = killedUnitHeroData != null;
					int availableAwardXp;
					if (killedUnitIsAHero) {
						availableAwardXp = gameplayConstants.getGrantHeroXP(killedUnitHeroData.getHeroLevel());
					} else {
						availableAwardXp = gameplayConstants.getGrantNormalXP(self.unitType.getLevel());
						if (self.isUnitType(CUnitTypeJass.SUMMONED)) {
							availableAwardXp = (int) (availableAwardXp * gameplayConstants.getSummonedKillFactor());
						}
					}
					final List<CUnit> xpReceivingHeroes = new ArrayList<>();
					final int heroExpRange = gameplayConstants.getHeroExpRange();
					simulation.getWorldCollision().enumUnitsInRect(new Rectangle(self.getX() - heroExpRange,
							self.getY() - heroExpRange, heroExpRange * 2, heroExpRange * 2), new CUnitEnumFunction() {
								@Override
								public boolean call(final CUnit unit) {
									if ((unit.distance(killedUnit) <= heroExpRange)
											&& sourcePlayer.hasAlliance(unit.getPlayerIndex(), CAllianceType.SHARED_XP)
											&& unit.isHero() && !unit.isDead() && !unit.illusionState.illusion) {
										xpReceivingHeroes.add(unit);
									}
									return false;
								}
							});
					if (xpReceivingHeroes.isEmpty()) {
						if (gameplayConstants.isGlobalExperience()) {
							for (int i = 0; i < simulation.getGameData().getMaxPlayers(); i++) {
								if (sourcePlayer.hasAlliance(i, CAllianceType.SHARED_XP)) {
									xpReceivingHeroes.addAll(simulation.getPlayerHeroes(i));
								}
							}
						}
					}
					xpReceivingHeroes.removeIf((hero) -> hero.getHeroData().isXpSuspended());
					if (!gameplayConstants.isMaxLevelHeroesDrainExp()) {
						xpReceivingHeroes.removeIf(
								(hero) -> hero.getHeroData().getHeroLevel() >= gameplayConstants.getMaxHeroLevel());
					}
					// HeroFactorXP is the creep reduction table: it only scales the experience from creeps.
					final boolean creepKill = simulation.isNeutralAggressive(self.playerIndex);
					for (final CUnit receivingHero : xpReceivingHeroes) {
						final CAbilityHero heroData = receivingHero.getHeroData();
						final float share = availableAwardXp * (1f / xpReceivingHeroes.size());
						final int xp = creepKill ? CUnitEvents.creepXp(gameplayConstants, heroData, share) : (int) share;
						heroData.addXp(simulation, receivingHero, xp, true);
					}
				}
			}
		}
		if (self.unitType.isHero()) {
			simulation.heroKilledEvent(self);
		}
		self.fireDeathEvents(simulation);
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_DEATH);
		if (eventList != null) {
			// A death trigger may register or destroy events on this unit while it runs.
			for (final CWidgetEvent event : new ArrayList<>(eventList)) {
				event.fire(self, CommonTriggerExecutionScope.unitDeathScope(JassGameEventsWar3.EVENT_UNIT_DEATH,
						event.getTrigger(), self, source, simulation.getPlayer(self.playerIndex)));
			}
		}
		simulation.getPlayer(self.playerIndex).fireUnitDeathEvents(self, source);
		if (self.isExplodesOnDeath()) {
			self.setHidden(true);
			simulation.createDeathExplodeEffect(self, self.death.explodesOnDeathBuffId);
			simulation.removeUnit(self);
		}
	}

	/** The corpse starts to decay when the death animation ends (measured on 1.29: 3 s after a Footman dies). */
	static void fireDecayEvents(final CUnit self, final CSimulation simulation) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_DECAY);
		if (eventList != null) {
			for (final CWidgetEvent event : new ArrayList<>(eventList)) {
				event.fire(self, CommonTriggerExecutionScope.unitDecayScope(JassGameEventsWar3.EVENT_UNIT_DECAY,
						event.getTrigger(), self, simulation.getPlayer(self.playerIndex)));
			}
		}
		simulation.getPlayer(self.playerIndex).fireUnitDecayEvents(self);
	}

	static void kill(final CUnit self, final CSimulation simulation) {
		if (!self.isDead()) {
			self.setLife(simulation, 0f);
		}
	}

	/**
	 * Brings a dead unit back. Measured on 1.29 (Resurrection): it uses its food again and counts again in its player's
	 * techtree (requirements, GetPlayerTechCount).
	 */
	static void resurrect(final CUnit self, final CSimulation simulation) {
		resurrect(self, simulation, false);
	}

	static void resurrect(final CUnit self, final CSimulation simulation, final boolean animatedCorpse) {
		final boolean wasCounted = !self.isDead() || self.death.animatedCorpse;
		self.death.animatedCorpse = animatedCorpse;
		simulation.getWorldCollision().removeUnit(self);
		self.death.corpse = false;
		self.death.boneCorpse = false;
		self.death.freshCorpse = false;
		self.death.deathTurnTick = 0;
		self.death.explodesOnDeath = false;
		self.death.explodesOnDeathBuffId = null;
		self.setLife(simulation, self.getMaximumLife());
		simulation.takeShadowPoolIds(self);
		if (!wasCounted && !animatedCorpse) {
			final CPlayer player = simulation.getPlayer(self.playerIndex);
			player.setUnitFoodUsed(self, self.unitType.getFoodUsed());
			player.setUnitFoodMade(self, self.unitType.getFoodMade());
			CUnitProduction.unlockTechtreeOnRevive(self, simulation);
		}
		simulation.getWorldCollision().addUnit(self);
		simulation.unitUpdatedType(self, self.typeId, true); // clear out some state
		self.unitAnimationListener.playAnimation(true, PrimaryTag.STAND, SequenceUtils.EMPTY, 0.0f, true);
	}

	/**
	 * Turns a unit just made by a script or an ability into a corpse that can be used at once, without a death:
	 * no death events, bounty or experience (CreateCorpse, Create Corpse, Exhume Corpses).
	 */
	static void becomeCorpse(final CUnit self, final CSimulation game) {
		self.beginBehavior(game, null, true);
		self.orders.orderQueue.clear();
		self.killPathingInstance();
		game.getWorldCollision().removeUnit(self);
		final CPlayer player = game.getPlayer(self.playerIndex);
		if (self.food.foodMade != 0) {
			player.setUnitFoodMade(self, 0);
		}
		if (self.food.foodUsed != 0) {
			player.setUnitFoodUsed(self, 0);
		}
		self.life = 0;
		self.death.corpse = true;
		self.death.boneCorpse = true;
		self.death.deathTurnTick = game.getGameTurnTick();
		game.getWorldCollision().addUnit(self);
		self.stateNotifier.lifeChanged();
	}

	/**
	 * A corpse picked up by a Meat Wagon: hidden, out of the world and not decaying while it is carried.
	 */
	static void holdCorpse(final CUnit self, final CSimulation game) {
		game.getWorldCollision().removeUnit(self);
		self.placement.collisionRectangle = null;
		self.setHidden(true);
		resetCorpseDecay(self, game);
	}

	static void resetCorpseDecay(final CUnit self, final CSimulation game) {
		self.death.deathTurnTick = game.getGameTurnTick();
	}

	/**
	 * Puts a carried corpse back on the ground as a fresh corpse.
	 */
	static void releaseCorpse(final CUnit self, final CSimulation game, final float x, final float y) {
		self.setX(x);
		self.setY(y);
		resetCorpseDecay(self, game);
		game.getWorldCollision().addUnit(self);
		self.setHidden(false);
		game.unitRepositioned(self);
	}
}
