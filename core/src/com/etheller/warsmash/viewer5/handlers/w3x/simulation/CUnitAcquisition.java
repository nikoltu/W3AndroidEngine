package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.EnumSet;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.AutocastType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.autocast.CAutocastAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.creeps.CCreepCastAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.BehaviorTargetUnitVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CRangedBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

/**
 * Target acquisition and autocast for a unit: the idle and attack scans, the creep spell cast, and the ranking of candidates.
 */
final class CUnitAcquisition {
	private CUnitAcquisition() {
	}

	transient static AutoAttackTargetFinderEnum autoAttackTargetFinderEnum = new AutoAttackTargetFinderEnum();

	transient static AutocastTargetFinderEnum autocastTargetFinderEnum = new AutocastTargetFinderEnum();

	static final int AUTOCAST_COMBAT_SCAN_TICKS = CTimer.wholeSteps(0.5f);

	// Measured (1.29): autocast looks this far for targets whatever the acquisition range (Web, Heal).
	static final float AUTOCAST_SEARCH_RANGE = 600f;

	// Measured (1.29): an idle caster looks for an autocast target when it is made, then every 3 s, and again as
	// soon as the spell's cooldown ends (Spell Steal, Web, Heal).
	static final int AUTOCAST_IDLE_RESCAN_TICKS = CTimer.wholeSteps(3.0f);

	// Measured (1.29): an idle unit looks for something to attack every half second, not every step. A new unit
	// first looks up to half a second after it is made, at its own phase; an order, or the end of one, makes it look
	// at once. Changing the acquisition range does not.
	static final int IDLE_ACQUIRE_SCAN_TICKS = CTimer.wholeSteps(0.5f);

	// Measured (1.29): a creep thinks of its spells when it starts a fight, then once a second while it lasts
	// (Rain of Fire at once, Animate Dead and Devour about a second later once the attackers reach it).
	static final int CREEP_CAST_INTERVAL_TICKS = CTimer.wholeSteps(1.0f);

	static boolean autoAcquireTargets(final CUnit self, final CSimulation game, final boolean disableMove) {
		return autoAcquireTargets(self, game, disableMove, true);
	}

	static boolean autoAcquireTargets(final CUnit self, final CSimulation game, final boolean disableMove, final boolean attack) {
		return autoAcquireTargets(self, game, disableMove, attack, false);
	}

	private static boolean autoAcquireTargets(final CUnit self, final CSimulation game, final boolean disableMove,
			final boolean attack, final boolean holdPosition) {
		// Cinematic maps park hostile units hidden among the player's (ShowUnit false); hidden units carry out
		// script orders but never pick targets themselves.
		if (self.hidden) {
			return false;
		}
		boolean autocast = false;
		final int tick = game.getGameTurnTick();
		if (self.auto.autoCast && (self.autocastAbility != null) && (tick >= self.acquisition.nextIdleAutocastScanTick)) {
			autocast = autoAcquireAutocastTargets(self, game, disableMove);
			if (!autocast) {
				final int cooldownTicks = autocastCooldownTicks(self, game);
				self.acquisition.nextIdleAutocastScanTick = tick
						+ ((cooldownTicks > 0) ? Math.min(cooldownTicks, AUTOCAST_IDLE_RESCAN_TICKS) : AUTOCAST_IDLE_RESCAN_TICKS);
			}
		}
		if (!autocast) {
			if (attack && self.auto.autoAttack) {
				return autoAcquireAttackTargets(self, game, disableMove, holdPosition);
			}
		}
		return autocast;
	}

	/**
	 * {@link #autoAcquireTargets} for a unit standing idle or holding its position: it looks for something to attack
	 * only when its scan is due. A unit holding its position (the one that cannot move) takes only what it can hit
	 * from where it stands.
	 */
	static boolean autoAcquireIdleTargets(final CUnit self, final CSimulation game, final boolean disableMove) {
		final int tick = game.getGameTurnTick();
		final boolean due = tick >= self.acquisition.nextIdleAcquireScanTick;
		if (due) {
			self.acquisition.nextIdleAcquireScanTick = tick + IDLE_ACQUIRE_SCAN_TICKS;
		}
		return autoAcquireTargets(self, game, disableMove, due, disableMove);
	}

	/**
	 * Measured (1.29): a creep with spells starts its fight, and so thinks of its spells, as soon as an enemy is sent to
	 * attack it; without spells, or when an enemy only comes near, it looks at its own phase.
	 */
	static void onAttackOrdered(final CUnit self, final CSimulation game, final CUnit attacker) {
		if ((attacker != self) && game.castsSpellsByItself(self.playerIndex) && !self.isDead()
				&& !game.getPlayer(self.playerIndex).hasAlliance(attacker.playerIndex, CAllianceType.PASSIVE)) {
			for (final CAbility ability : self.abilities) {
				if (!ability.isDisabled() && CCreepCastAbility.isCreepSpell(ability)) {
					self.acquisition.nextIdleAcquireScanTick = Math.min(self.acquisition.nextIdleAcquireScanTick, game.getGameTurnTick());
					return;
				}
			}
		}
	}

	/** A new unit's first scan, 1 step to a whole scan period away, spread over units by their handle. */
	static int firstIdleAcquireScanDelay(final CUnit self) {
		int hash = self.getHandleId() * 0x9E3779B1;
		hash ^= hash >>> 16;
		return 1 + Math.floorMod(hash, IDLE_ACQUIRE_SCAN_TICKS);
	}

	/**
	 * Creeps cast their spells by themselves while they fight (Devour, Animate Dead, War Stomp, Bloodlust, Slow...).
	 */
	static void tryCreepCast(final CUnit self, final CSimulation game) {
		if (self.acquisition.creepCastAfterWhirl) {
			self.acquisition.creepCastAfterWhirl = false;
			if (CCreepCastAbility.tryCastAfterBladestorm(game, self)) {
				return;
			}
		}
		CBehavior behavior = self.orders.currentBehavior;
		// The walk towards the unit it attacks counts too: a Doom Guard rains fire before the fight starts.
		if (behavior instanceof CBehaviorMove) {
			behavior = ((CBehaviorMove) behavior).getRangedBehavior();
		}
		if (!(behavior instanceof CBehaviorAttack)) {
			self.acquisition.creepCastFighting = false;
			if (self.orders.currentBehavior == self.orders.defaultBehavior) {
				for (final CAbility ability : self.abilities) {
					if (!ability.isDisabled() && CCreepCastAbility.tryIdleCreepCast(game, self, ability)) {
						return;
					}
				}
			}
			return;
		}
		final CUnit target = behavior.visit(BehaviorTargetUnitVisitor.INSTANCE);
		if ((target == null) || target.isDead()) {
			return;
		}
		final int tick = game.getGameTurnTick();
		if (!self.acquisition.creepCastFighting) {
			self.acquisition.creepCastThinks = -1;
		}
		final boolean think = !self.acquisition.creepCastFighting || (tick >= self.acquisition.nextCreepCastTick);
		if (think) {
			self.acquisition.creepCastFighting = true;
			self.acquisition.nextCreepCastTick = tick + CREEP_CAST_INTERVAL_TICKS;
			self.acquisition.creepCastThinks++;
		}
		final boolean inAttackRange = ((CBehaviorAttack) behavior).isWithinRange(game);
		// A hero casts his summons and buffs before his other spells, not in the order of his ability list.
		final int passes = (self.getHeroData() != null) ? 2 : 1;
		for (int pass = 0; pass < passes; pass++) {
			if ((pass == 1) && CCreepCastAbility.isSummonPending(game, self, self.acquisition.creepCastThinks)) {
				return;
			}
			for (final CAbility ability : self.abilities) {
				if (ability.isDisabled() || ((passes == 2) && (CCreepCastAbility.castsFirst(ability) != (pass == 0)))) {
					continue;
				}
				if (think && (ability instanceof CCreepCastAbility)
						&& ((CCreepCastAbility) ability).tryCreepCast(game, self, target, inAttackRange)) {
					return;
				}
				if (CCreepCastAbility.tryCreepCastRule(game, self, ability, target, think, self.acquisition.creepCastThinks)) {
					return;
				}
			}
		}
	}

	static int autocastCooldownTicks(final CUnit self, final CSimulation game) {
		if (self.autocastAbility instanceof AbilityBuilderAbility) {
			return (int) ((AbilityBuilderAbility) self.autocastAbility).getCooldownRemainingTicks(game, self);
		}
		if (self.autocastAbility instanceof CAbilitySpellBase) {
			return self.getCooldownRemainingTicks(game, ((CAbilitySpellBase) self.autocastAbility).getCode());
		}
		return 0;
	}

	static boolean autoAcquireAutocastTargets(final CUnit self, final CSimulation game, final boolean disableMove) {
		if ((self.autocastAbility != null) && !self.autocastAbility.isDisabled()
				&& self.autocastAbility.getAutocastType() != AutocastType.NOAUTOCASTBEHAVIOR) {
			if (self.autocastAbility.getAutocastType() == AutocastType.NOTARGET) {
				final BooleanAbilityTargetCheckReceiver<Void> booleanTargetReceiver = BooleanAbilityTargetCheckReceiver
						.<Void>getInstance().reset();
				self.autocastAbility.checkCanAutoTargetNoTarget(game, self, self.playerIndex,
						self.autocastAbility.getBaseOrderId(), booleanTargetReceiver);
				if (booleanTargetReceiver.isTargetable()) {
					return self.order(game, self.autocastAbility.getBaseOrderId(), null, true);
				}
			} else if (self.autocastAbility.getAutocastType() != AutocastType.NONE) {
				if (self.placement.collisionRectangle != null) {
					CWidget.tempRect.set(self.placement.collisionRectangle);
				} else {
					CWidget.tempRect.set(self.getX(), self.getY(), 0, 0);
				}
				final float halfSize = AUTOCAST_SEARCH_RANGE;
				CWidget.tempRect.x -= halfSize;
				CWidget.tempRect.y -= halfSize;
				CWidget.tempRect.width += halfSize * 2;
				CWidget.tempRect.height += halfSize * 2;
				game.getWorldCollision().enumUnitsInRect(CWidget.tempRect,
						autocastTargetFinderEnum.reset(game, self, self.autocastAbility, disableMove));
				if (autocastTargetFinderEnum.currentUnitTarget != null) {
					self.order(game, self.autocastAbility.getBaseOrderId(), autocastTargetFinderEnum.currentUnitTarget,
							true);
					return true;
				}
			}
		}
		return false;
	}

	static boolean autoAcquireAttackTargets(final CUnit self, final CSimulation game, final boolean disableMove) {
		return autoAcquireAttackTargets(self, game, disableMove, false);
	}

	private static boolean autoAcquireAttackTargets(final CUnit self, final CSimulation game, final boolean disableMove,
			final boolean attackRangeOnly) {
		if (!self.getCurrentAttacks().isEmpty() && !self.classifications.contains(CUnitClassification.PEON)) {
			if (self.placement.collisionRectangle != null) {
				CWidget.tempRect.set(self.placement.collisionRectangle);
			} else {
				CWidget.tempRect.set(self.getX(), self.getY(), 0, 0);
			}
			float halfSize = self.acquisitionRange;
			if (attackRangeOnly) {
				for (final CUnitAttack attack : self.getCurrentAttacks()) {
					halfSize = Math.max(halfSize, attack.getRange());
				}
			}
			CWidget.tempRect.x -= halfSize;
			CWidget.tempRect.y -= halfSize;
			CWidget.tempRect.width += halfSize * 2;
			CWidget.tempRect.height += halfSize * 2;
			if (self.priorityAttackTargets != null) {
				game.getWorldCollision().enumUnitsInRect(CWidget.tempRect,
						autoAttackTargetFinderEnum.reset(game, self, disableMove, self.priorityAttackTargets, attackRangeOnly));
				if (autoAttackTargetFinderEnum.attackNearest()) {
					return true;
				}
			}
			game.getWorldCollision().enumUnitsInRect(CWidget.tempRect,
					autoAttackTargetFinderEnum.reset(game, self, disableMove, null, attackRangeOnly));
			return autoAttackTargetFinderEnum.attackNearest();
		}
		return false;
	}

	/**
	 * Turns to an enemy of the kinds this unit prefers when one is in acquisition range; false if there is none.
	 */
	static boolean acquirePriorityAttackTarget(final CUnit self, final CSimulation game, final boolean disableMove) {
		if ((self.priorityAttackTargets == null) || self.getCurrentAttacks().isEmpty()) {
			return false;
		}
		if (self.placement.collisionRectangle != null) {
			CWidget.tempRect.set(self.placement.collisionRectangle);
		} else {
			CWidget.tempRect.set(self.getX(), self.getY(), 0, 0);
		}
		final float halfSize = self.acquisitionRange;
		CWidget.tempRect.x -= halfSize;
		CWidget.tempRect.y -= halfSize;
		CWidget.tempRect.width += halfSize * 2;
		CWidget.tempRect.height += halfSize * 2;
		game.getWorldCollision().enumUnitsInRect(CWidget.tempRect,
				autoAttackTargetFinderEnum.reset(game, self, disableMove, self.priorityAttackTargets, false));
		return autoAttackTargetFinderEnum.attackNearest();
	}

	static boolean isPriorityAttackTarget(final CUnit unit, final EnumSet<CTargetType> priorityTargets) {
		return (priorityTargets.contains(CTargetType.AIR) && unit.isUnitType(CUnitTypeJass.FLYING))
				|| (priorityTargets.contains(CTargetType.GROUND) && !unit.isBuilding()
						&& unit.isUnitType(CUnitTypeJass.GROUND))
				|| (priorityTargets.contains(CTargetType.STRUCTURE) && unit.isBuilding());
	}

	static final class AutocastTargetFinderEnum implements CUnitEnumFunction {
		final static BooleanAbilityTargetCheckReceiver<CWidget> targetCheckReceiver = BooleanAbilityTargetCheckReceiver
				.<CWidget>getInstance();
		CSimulation game;
		CUnit source;
		CAutocastAbility ability;
		boolean disableMove;
		AutocastType type;

		CUnit currentUnitTarget;
		double comparisonValue;

		AutocastTargetFinderEnum reset(final CSimulation game, final CUnit source,
				final CAutocastAbility ability, final boolean disableMove) {
			this.game = game;
			this.source = source;
			this.ability = ability;
			this.disableMove = disableMove;
			this.type = ability.getAutocastType();

			this.currentUnitTarget = null;
			this.comparisonValue = Double.NaN;
			return this;
		}

		@Override
		public boolean call(final CUnit unit) {
			if (unit.isAutoTargetable() && !unit.isHidden() && this.type != AutocastType.NONE
					&& this.source.canReach(unit, AUTOCAST_SEARCH_RANGE)) {
				switch (this.type) {
				case ATTACKINGALLY:
				case ATTACKINGENEMY:
					if ((unit.getCurrentBehavior() != null) && (unit.getCurrentBehavior() instanceof CRangedBehavior)) {
						final CRangedBehavior rbeh = (CRangedBehavior) unit.getCurrentBehavior();
						if (rbeh.getHighlightOrderId() == OrderIds.attack) {
							final AbilityTarget target = rbeh.getTarget();
							final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
							if (targetUnit != null) {
								if (((this.type == AutocastType.ATTACKINGALLY)
										&& this.game.getPlayer(this.source.getPlayerIndex())
												.hasAlliance(targetUnit.getPlayerIndex(), CAllianceType.PASSIVE))
										|| ((this.type == AutocastType.ATTACKINGENEMY)
												&& !this.game.getPlayer(this.source.getPlayerIndex()).hasAlliance(
														targetUnit.getPlayerIndex(), CAllianceType.PASSIVE))) {
									targetCheckReceiver.reset();
									this.ability.checkCanAutoTarget(this.game, this.source, this.source.playerIndex,
											this.ability.getBaseOrderId(), unit, targetCheckReceiver);
									if (targetCheckReceiver.isTargetable()) {
										if (this.currentUnitTarget == null) {
											this.currentUnitTarget = unit;
											this.comparisonValue = this.source.distance(unit);
										} else {
											final double dist = this.source.distance(unit);
											if (dist < this.comparisonValue) {
												this.currentUnitTarget = unit;
												this.comparisonValue = dist;
											}
										}
									}
								}
							}
						}
					}
					break;
				case ATTACKTARGETING:
					if (!this.game.getPlayer(this.source.getPlayerIndex()).hasAlliance(unit.getPlayerIndex(),
							CAllianceType.PASSIVE) && !unit.isDead() && !unit.isInvulnerable()
							&& !unit.isUnitType(CUnitTypeJass.SLEEPING)) {
						for (final CUnitAttack attack : this.source.getCurrentAttacks()) {
							if (this.source.canReach(unit, this.source.acquisitionRange)
									&& unit.canBeTargetedBy(this.game, this.source, attack.getTargetsAllowed())
									&& (this.source.distance(unit) >= this.source.getUnitType()
											.getMinimumAttackRange())) {
								targetCheckReceiver.reset();
								this.ability.checkCanAutoTarget(this.game, this.source, this.source.playerIndex,
										this.ability.getBaseOrderId(), unit, targetCheckReceiver);
								if (targetCheckReceiver.isTargetable()) {
									if (this.currentUnitTarget == null) {
										this.currentUnitTarget = unit;
										this.comparisonValue = this.source.distance(unit);
									} else {
										final double dist = this.source.distance(unit);
										if (dist < this.comparisonValue) {
											this.currentUnitTarget = unit;
											this.comparisonValue = dist;
										}
									}
								}
							}
						}
					}

					break;
				case HIGESTHP:
					targetCheckReceiver.reset();
					this.ability.checkCanAutoTarget(this.game, this.source, this.source.playerIndex,
							this.ability.getBaseOrderId(), unit, targetCheckReceiver);
					if (targetCheckReceiver.isTargetable()) {
						if (this.currentUnitTarget == null) {
							this.currentUnitTarget = unit;
							this.comparisonValue = unit.getLife() / unit.getMaximumLife();
						} else {
							final double ratio = unit.getLife() / unit.getMaximumLife();
							if (ratio > this.comparisonValue) {
								this.currentUnitTarget = unit;
								this.comparisonValue = ratio;
							}
						}
					}
					break;
				case LOWESTHP:
					targetCheckReceiver.reset();
					this.ability.checkCanAutoTarget(this.game, this.source, this.source.playerIndex,
							this.ability.getBaseOrderId(), unit, targetCheckReceiver);
					if (targetCheckReceiver.isTargetable()) {
						if (unit.getLife() < unit.getMaximumLife()) {
							if (this.currentUnitTarget == null) {
								this.currentUnitTarget = unit;
								this.comparisonValue = unit.getLife() / unit.getMaximumLife();
							} else {
								final double ratio = unit.getLife() / unit.getMaximumLife();
								if (ratio < this.comparisonValue) {
									this.currentUnitTarget = unit;
									this.comparisonValue = ratio;
								}
							}
						}
					}
					break;
				case FARTHESTVALID:
					targetCheckReceiver.reset();
					this.ability.checkCanAutoTarget(this.game, this.source, this.source.getPlayerIndex(), this.ability.getBaseOrderId(), unit,
							targetCheckReceiver);
					if (targetCheckReceiver.isTargetable()) {
						if (this.currentUnitTarget == null) {
							this.currentUnitTarget = unit;
							this.comparisonValue = this.source.distance(unit);
						} else {
							final double dist = this.source.distance(unit);
							if (dist > this.comparisonValue) {
								this.currentUnitTarget = unit;
								this.comparisonValue = dist;
							}
						}
					}
					break;
				case NEARESTVALID:
					targetCheckReceiver.reset();
					this.ability.checkCanAutoTarget(this.game, this.source, this.source.playerIndex,
							this.ability.getBaseOrderId(), unit, targetCheckReceiver);
					if (targetCheckReceiver.isTargetable()) {
						if (this.currentUnitTarget == null) {
							this.currentUnitTarget = unit;
							this.comparisonValue = this.source.distance(unit);
						} else {
							final double dist = this.source.distance(unit);
							if (dist < this.comparisonValue) {
								this.currentUnitTarget = unit;
								this.comparisonValue = dist;
							}
						}
					}
					break;
				case NEARESTENEMY:
					targetCheckReceiver.reset();
					this.ability.checkCanAutoTarget(this.game, this.source, this.source.playerIndex,
							this.ability.getBaseOrderId(), unit, targetCheckReceiver);
					if (targetCheckReceiver.isTargetable() && !this.game.getPlayer(this.source.getPlayerIndex())
							.hasAlliance(unit.getPlayerIndex(), CAllianceType.PASSIVE)) {
						if (this.currentUnitTarget == null) {
							this.currentUnitTarget = unit;
							this.comparisonValue = this.source.distance(unit);
						} else {
							final double dist = this.source.distance(unit);
							if (dist < this.comparisonValue) {
								this.currentUnitTarget = unit;
								this.comparisonValue = dist;
							}
						}
					}
					break;
				case NONE:
				case NOTARGET:
				default:
					break;

				}
			}

			return false;
		}
	}

	static final class AutoAttackTargetFinderEnum implements CUnitEnumFunction {
		CSimulation game;
		CUnit source;
		boolean disableMove;
		EnumSet<CTargetType> priorityTargets;
		boolean attackRangeOnly;
		boolean foundAnyTarget;
		CUnit nearestTarget;
		CUnitAttack nearestTargetAttack;
		double nearestTargetDistance;
		int nearestTargetRank;

		AutoAttackTargetFinderEnum reset(final CSimulation game, final CUnit source,
				final boolean disableMove, final EnumSet<CTargetType> priorityTargets, final boolean attackRangeOnly) {
			this.game = game;
			this.source = source;
			this.disableMove = disableMove;
			this.priorityTargets = priorityTargets;
			this.attackRangeOnly = attackRangeOnly;
			this.foundAnyTarget = false;
			this.nearestTarget = null;
			this.nearestTargetAttack = null;
			this.nearestTargetDistance = Double.MAX_VALUE;
			this.nearestTargetRank = Integer.MIN_VALUE;
			return this;
		}

		/**
		 * Attacks the target the enumeration found, as the original game picks it: the best ranked, then the
		 * nearest.
		 */
		boolean attackNearest() {
			if (this.nearestTarget != null) {
				// The events below run triggers that may look for targets with this same finder.
				final CSimulation game = this.game;
				final CUnit source = this.source;
				final CUnit target = this.nearestTarget;
				final CBehavior attack = source.getAttackBehavior().reset(game, OrderIds.attack,
						this.nearestTargetAttack, target, this.disableMove, CBehaviorAttackListener.DO_NOTHING);
				source.getAttackBehavior().delayFirstSwing(game);
				source.beginBehavior(game, attack, true);
				this.foundAnyTarget = true;
				source.fireAcquiredTargetEvents(game, target);
				source.alertCreeps(game, target);
				return true;
			}
			return this.foundAnyTarget;
		}

		@Override
		public boolean call(final CUnit unit) {
			if ((this.priorityTargets != null) && !isPriorityAttackTarget(unit, this.priorityTargets)) {
				return false;
			}
			if (unit.isAutoTargetable() && !unit.isHidden() && !this.source.isHidden()
					&& (this.source.getAttackBehavior() != null)
					&& !this.source.getFirstAbilityOfType(CAbilityAttack.class).isDisabled()) {
				// TODO this "attack behavior null" check was added for some weird Root edge
				// case with NE, maybe
				// refactor it later
				if (!this.game.getPlayer(this.source.getPlayerIndex()).hasAlliance(unit.getPlayerIndex(),
						CAllianceType.PASSIVE) && !unit.isDead() && !unit.isInvulnerable()
						&& !unit.isUnitType(CUnitTypeJass.SLEEPING)) {
					for (final CUnitAttack attack : this.source.getCurrentAttacks()) {
						if (this.source.canReach(unit, this.attackRangeOnly ? attack.getRange() : this.source.acquisitionRange)
								&& unit.canBeTargetedBy(this.game, this.source, attack.getTargetsAllowed())
								&& (this.source.distance(unit) >= this.source.getUnitType().getMinimumAttackRange())) {
							if (!(unit.isUnitType(CUnitTypeJass.ETHEREAL)
									&& (attack.getAttackType() != CAttackType.MAGIC)
									&& (attack.getAttackType() != CAttackType.SPELLS))
									&& !(this.game.getGameplayConstants().isMagicImmuneResistsDamage()
											&& unit.isUnitType(CUnitTypeJass.MAGIC_IMMUNE)
											&& (attack.getAttackType() == CAttackType.MAGIC))) {
								final double distance = this.source.distance(unit);
								final int rank = acquireRank(this.game, this.source, unit);
								if ((rank > this.nearestTargetRank)
										|| ((rank == this.nearestTargetRank) && (distance < this.nearestTargetDistance))) {
									this.nearestTarget = unit;
									this.nearestTargetAttack = attack;
									this.nearestTargetDistance = distance;
									this.nearestTargetRank = rank;
								}
								return false;
							}
						}
					}
				}
			}
			return false;
		}
	}

	/**
	 * Measured on 1.29 (enemies that stand still until the unit looks): an idle unit picking a target in its
	 * acquisition range takes, before any nearer one, a unit that is attacking it or one of its allies (a Neutral
	 * Hostile creep does not), then the nearest other unit that can fight; a hero, a unit it could hit from where it
	 * stands or one that could hit it has no lead. Workers and units that cannot fight (paused) come after these, and
	 * buildings last, a tower that can hit it before other buildings.
	 */
	static int acquireRank(final CSimulation game, final CUnit source, final CUnit target) {
		if (target.isBuilding()) {
			return target.canAttackFromHere(game, source) ? 1 : 0;
		}
		if (target.isPaused() || target.getCurrentAttacks().isEmpty() || target.isDisableAttacks()
				|| target.getClassifications().contains(CUnitClassification.PEON)) {
			return 2;
		}
		if (!game.isNeutralAggressive(source.getPlayerIndex()) && isAttackingAllyOf(game, target, source)) {
			return 4;
		}
		return 3;
	}

	private static boolean isAttackingAllyOf(final CSimulation game, final CUnit attacker, final CUnit unit) {
		CBehavior behavior = attacker.getCurrentBehavior();
		if (behavior instanceof CBehaviorMove) {
			behavior = ((CBehaviorMove) behavior).getRangedBehavior();
		}
		if (!(behavior instanceof CBehaviorAttack)) {
			return false;
		}
		final CUnit attacked = behavior.visit(BehaviorTargetUnitVisitor.INSTANCE);
		return (attacked != null) && ((attacked == unit)
				|| game.getPlayer(unit.getPlayerIndex()).hasAlliance(attacked.getPlayerIndex(), CAllianceType.PASSIVE));
	}

	static boolean canAttackFromHere(final CUnit self, final CSimulation game, final CUnit target) {
		if (self.paused || self.isDisableAttacks() || (self.attackBehavior == null)) {
			return false;
		}
		for (final CUnitAttack attack : self.getCurrentAttacks()) {
			if (self.canReach(target, attack.getRange()) && target.canBeTargetedBy(game, self, attack.getTargetsAllowed())) {
				return true;
			}
		}
		return false;
	}
}
