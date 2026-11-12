package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.scope.variableevent.CLimitOp;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityDisableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorStun;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CWeaponType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDefaultEtherealDamageModListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDefaultLifestealListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDefaultMagicImmuneDamageModListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDefaultSleepListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitDefaultThornsListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.state.CUnitState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitStateEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CWidgetEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingFx;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;

/**
 * Recomputes a unit's derived stats and state flags from its buffs, upgrades and type data, and reads and writes the stats scripts see.
 */
final class CUnitStats {
	private CUnitStats() {
	}

	/**
	 * Measured on 1.29: an invisibility fade does not run while the unit is paused (PauseUnit before the fade ends
	 * keeps it visible) and starts over when the pause ends.
	 */
	static void holdFadeWhilePaused(final CUnit self, final CSimulation game) {
		final CTimer fade = self.getFadeTimer();
		if (fade == null) {
			return;
		}
		if (self.paused) {
			if (fade.isRunning()) {
				fade.pause(game);
				self.fadeHoldGeneration = fade.getGeneration();
			}
		}
		else if (self.fadeHoldGeneration != -1) {
			// Anything else that paused or started the timer meanwhile (the ability removed) keeps it as it is.
			if (fade.getGeneration() == self.fadeHoldGeneration) {
				fade.start(game);
			}
			self.fadeHoldGeneration = -1;
		}
	}

	static void computeUnitState(final CUnit self, final CSimulation game, final StateModBuffType type) {
		switch (type) {
		case ENABLE_ATTACK:
		case DISABLE_ATTACK_INDEX:
		case DISABLE_ALL:
		case DISABLE_MOVE:
		case DISABLE_ATTACK:
		case DISABLE_MELEE_ATTACK:
		case DISABLE_RANGED_ATTACK:
		case DISABLE_SPECIAL_ATTACK:
		case DISABLE_SPELLS:
		case ETHEREAL:
		case POLYMORPHED:
			byte extraEnabledAttacks = 0;
			byte disabledAttacks = 0;
			boolean isDisableAll = false;
			boolean isDisableMove = false;
			boolean isDisableAttack = false;
			boolean isDisableMeleeAttack = false;
			boolean isDisableRangedAttack = false;
			boolean isDisableSpecialAttack = false;
			boolean isDisableSpells = false;
			boolean isEthereal = false;
			boolean isPolymorphed = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.ENABLE_ATTACK) {
					if (buff.getValue() != 0) {
						extraEnabledAttacks |= buff.getValue();
					}
				}
				if (buff.getBuffType() == StateModBuffType.DISABLE_ATTACK_INDEX) {
					disabledAttacks |= buff.getValue();
				}
				if (buff.getBuffType() == StateModBuffType.DISABLE_ALL) {
					if (buff.getValue() != 0) {
						isDisableAll = true;
					}
				}
				if (buff.getBuffType() == StateModBuffType.DISABLE_MOVE) {
					if (buff.getValue() != 0) {
						isDisableMove = true;
					}
				}
				if (buff.getBuffType() == StateModBuffType.DISABLE_ATTACK) {
					if (buff.getValue() != 0) {
						isDisableAttack = true;
					}
				}
				if (buff.getBuffType() == StateModBuffType.DISABLE_MELEE_ATTACK) {
					if (buff.getValue() != 0) {
						isDisableMeleeAttack = true;
					}
				}
				if (buff.getBuffType() == StateModBuffType.DISABLE_RANGED_ATTACK) {
					if (buff.getValue() != 0) {
						isDisableRangedAttack = true;
					}
				}
				if (buff.getBuffType() == StateModBuffType.DISABLE_SPECIAL_ATTACK) {
					if (buff.getValue() != 0) {
						isDisableSpecialAttack = true;
					}
				}
				if (buff.getBuffType() == StateModBuffType.DISABLE_SPELLS) {
					if (buff.getValue() != 0) {
						isDisableSpells = true;
					}
				}
				if (buff.getBuffType() == StateModBuffType.ETHEREAL) {
					if (buff.getValue() != 0) {
						isEthereal = true;
					}
				}
				if (buff.getBuffType() == StateModBuffType.POLYMORPHED) {
					if (buff.getValue() != 0) {
						isPolymorphed = true;
					}
				}
			}
			if (isDisableAll) {
				isDisableMove = true;
				isDisableAttack = true;
				isDisableSpells = true;
			}
//			CAbility attack = this.getFirstAbilityOfType(CAbilityAttack.class);
//			if (attack != null) {
//				attack.setDisabled(isDisableAttack, CAbilityDisableType.ATTACKDISABLED);
//			}
			for (final CAbility ability : self.abilities) {
				if (((isDisableAttack || isEthereal) && (ability.getAbilityCategory() == CAbilityCategory.ATTACK))
						|| (isDisableMove && (ability.getAbilityCategory() == CAbilityCategory.MOVEMENT))
						|| (isDisableSpells && (ability.getAbilityCategory() == CAbilityCategory.SPELL)
								&& ability.isMagic())
						|| (isEthereal && ability.isPhysical()
								&& ((ability.getAbilityCategory() == CAbilityCategory.SPELL)
										|| (ability.getAbilityCategory() == CAbilityCategory.CORE)))
						|| (isPolymorphed && ((ability.getAbilityCategory() == CAbilityCategory.SPELL)
								|| (ability.getAbilityCategory() == CAbilityCategory.PASSIVE
										|| ability.getAbilityCategory() == CAbilityCategory.ATTACK
										|| (ability.getAbilityCategory() == CAbilityCategory.CORE
												&& ability.isPhysical()))))) {
					ability.setDisabled(true, CAbilityDisableType.DISABLED_EFFECT);
				} else {
					ability.setDisabled(false, CAbilityDisableType.DISABLED_EFFECT);
				}
			}
			final List<CUnitAttack> newAttackList = new ArrayList<CUnitAttack>();
			for (int i = 0; i < self.unitSpecificAttacks.size(); i++) {
				final CUnitAttack attack = self.unitSpecificAttacks.get(i);
				if ((((self.getUnitType().getAttacksEnabled() & (1 << i)) != 0) || ((extraEnabledAttacks & (1 << i)) != 0))
						&& ((disabledAttacks & (1 << i)) == 0) && !isDisableAttack
						&& (!isDisableMeleeAttack || !attack.getWeaponType().equals(CWeaponType.NORMAL))
						&& (!isDisableRangedAttack || attack.getWeaponType().equals(CWeaponType.NORMAL))
						&& (!isDisableSpecialAttack || !((attack.getTargetsAllowed().size() == 1)
								&& attack.getTargetsAllowed().equals(EnumSet.of(CTargetType.TREE))))) {
					newAttackList.add(attack);
				}

			}
			self.setUnitSpecificCurrentAttacks(newAttackList);
			self.notifyAttacksChanged();
			if ((self.orders.currentBehavior instanceof CBehaviorAttack)
					&& !newAttackList.contains(((CBehaviorAttack) self.orders.currentBehavior).getUnitAttack())) {
				self.performDefaultBehavior(game);
			}
			switch (type) {
			case ENABLE_ATTACK:
			case DISABLE_ATTACK_INDEX:
				self.checkDisabledAbilities(game, isDisableAttack);
				break;
			case DISABLE_ALL:
				self.checkDisabledAbilities(game, isDisableAll);
				break;
			case DISABLE_MOVE:
				self.checkDisabledAbilities(game, isDisableMove);
				break;
			case DISABLE_ATTACK:
				self.checkDisabledAbilities(game, isDisableAttack);
				break;
			case DISABLE_MELEE_ATTACK:
				self.checkDisabledAbilities(game, isDisableMeleeAttack);
				break;
			case DISABLE_RANGED_ATTACK:
				self.checkDisabledAbilities(game, isDisableRangedAttack);
				break;
			case DISABLE_SPECIAL_ATTACK:
				self.checkDisabledAbilities(game, isDisableSpecialAttack);
				break;
			case DISABLE_SPELLS:
				self.checkDisabledAbilities(game, isDisableSpells);
				break;
			case ETHEREAL:
				self.checkDisabledAbilities(game, isEthereal);
				break;
			case POLYMORPHED:
				self.checkDisabledAbilities(game, isPolymorphed);
				break;
			default:
				break;
			}

			if (isEthereal) {
				if (!self.damageTakenModificationListeners.contains(CUnitDefaultEtherealDamageModListener.INSTANCE)) {
					self.addDamageTakenModificationListener(CUnitDefaultEtherealDamageModListener.INSTANCE);
				}
				game.changeUnitVertexColor(self, RenderUnit.ETHEREAL);
			} else {
				if (self.damageTakenModificationListeners.contains(CUnitDefaultEtherealDamageModListener.INSTANCE)) {
					self.removeDamageTakenModificationListener(CUnitDefaultEtherealDamageModListener.INSTANCE);
					game.changeUnitVertexColor(self, RenderUnit.DEFAULT);
				}
			}
			break;
		case DISABLE_AUTO_ATTACK:
			boolean isDisableAutoAttack = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.DISABLE_AUTO_ATTACK) {
					if (buff.getValue() != 0) {
						isDisableAutoAttack = true;
					}
				}
			}
			self.auto.autoAttack = !isDisableAutoAttack;
			break;
		case DISABLE_AUTO_CAST:
			boolean isDisableAutoCast = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.DISABLE_AUTO_CAST) {
					if (buff.getValue() != 0) {
						isDisableAutoCast = true;
					}
				}
			}
			self.auto.autoCast = !isDisableAutoCast;
			break;
		case DISABLE_ASSIST_ALLY:
			boolean isDisableAssistAlly = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.DISABLE_ASSIST_ALLY) {
					if (buff.getValue() != 0) {
						isDisableAssistAlly = true;
					}
				}
			}
			self.auto.assistAlly = !isDisableAssistAlly;
			break;
		case MAGIC_IMMUNE:
			boolean isMagicImmune = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.MAGIC_IMMUNE) {
					if (buff.getValue() != 0) {
						isMagicImmune = true;
					}
				}
			}
			self.setMagicImmune(isMagicImmune);
			if (isMagicImmune) {
				if (!self.finalDamageTakenModificationListeners
						.contains(CUnitDefaultMagicImmuneDamageModListener.INSTANCE)) {
					self.addFinalDamageTakenModificationListener(CUnitDefaultMagicImmuneDamageModListener.INSTANCE);
				}
			} else {
				if (self.finalDamageTakenModificationListeners
						.contains(CUnitDefaultMagicImmuneDamageModListener.INSTANCE)) {
					self.removeFinalDamageTakenModificationListener(CUnitDefaultMagicImmuneDamageModListener.INSTANCE);
				}
			}
			break;
		case MORPH_IMMUNE:
			boolean isMorphImmune = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.MORPH_IMMUNE) {
					if (buff.getValue() != 0) {
						isMorphImmune = true;
					}
				}
			}
			self.invulnerability.morphImmune = isMorphImmune;
			break;
		case RESISTANT:
			boolean isResistant = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.RESISTANT) {
					if (buff.getValue() != 0) {
						isResistant = true;
					}
				}
			}
			self.invulnerability.resistant = isResistant;
			break;
		case SLEEPING:
		case STUN:
			boolean isSleeping = false;
			boolean isStun = false;
			boolean updateAnim = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.SLEEPING) {
					if (buff.getValue() != 0) {
						isSleeping = true;
					}
					if (buff.getValue() == 1) {
						updateAnim = true;
					}
				}
				if (buff.getBuffType() == StateModBuffType.STUN) {
					if (buff.getValue() != 0) {
						isStun = true;
					}
					if (buff.getValue() == 1) {
						updateAnim = true;
					}
				}
			}
			if (isSleeping || isStun) {
				if ((self.orders.currentBehavior == null)
						|| (self.orders.currentBehavior.getHighlightOrderId() != OrderIds.stunned)) {
					if (self.orders.currentBehavior != null) {
						self.orders.interruptedBehavior = self.orders.currentBehavior;
						self.orders.interruptedDefaultBehavior = self.orders.defaultBehavior;
					}
					self.orders.currentBehavior = new CBehaviorStun(self, updateAnim);
					self.orders.currentBehavior.begin(game);
					self.setDefaultBehavior(self.orders.currentBehavior);
					self.stateNotifier.ordersChanged();
				}
			} else {
				if ((self.orders.currentBehavior != null)
						&& (self.orders.currentBehavior.getHighlightOrderId() == OrderIds.stunned)) {
//					this.setAcceptingOrders(true);
					self.setDefaultBehavior(self.orders.interruptedDefaultBehavior);
					self.orders.currentBehavior = self.pollNextOrderBehavior(game);
					self.orders.interruptedBehavior = null;
					self.orders.interruptedDefaultBehavior = null;
					self.stateNotifier.ordersChanged();
				}
			}

			if (isSleeping) {
				if (!self.damageTakenListeners.contains(CUnitDefaultSleepListener.INSTANCE)) {
					self.addDamageTakenListener(CUnitDefaultSleepListener.INSTANCE);
				}
			} else {
				if (self.damageTakenListeners.contains(CUnitDefaultSleepListener.INSTANCE)) {
					self.removeDamageTakenListener(CUnitDefaultSleepListener.INSTANCE);
				}
			}
			break;
		case SNARED:
			boolean isSnared = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.SNARED) {
					if (buff.getValue() != 0) {
						isSnared = true;
					}
				}
			}
			if (isSnared) {
				self.setFlyHeight(0);
				self.movement.moveDisabled = true;
			} else {
				if (self.movement.moveDisabled) {
					self.setFlyHeight(self.unitType.getDefaultFlyingHeight());
					self.movement.moveDisabled = false;
				}
			}
			break;
		case PAUSED:
			boolean isPaused = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.PAUSED) {
					if (buff.getValue() != 0) {
						isPaused = true;
					}
				}
			}
			if (self.paused != isPaused) {
				self.paused = isPaused;
				computeUnitState(self, game, StateModBuffType.INVISIBLE);
			}
			break;
		case DISABLE_UNIT_COLLISION:
			boolean isDisableCollision = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.DISABLE_UNIT_COLLISION) {
					if (buff.getValue() != 0) {
						isDisableCollision = true;
					}
				}
			}
			if (isDisableCollision) {
				if (!self.movement.noUnitCollision) {
					self.movement.noUnitCollision = true;
				}
			} else {
				if (self.movement.noUnitCollision) {
					self.movement.noUnitCollision = false;
				}
			}
			break;
		case DISABLE_BUILDING_COLLISION:
			boolean isDisableBuildCollision = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.DISABLE_BUILDING_COLLISION) {
					if (buff.getValue() != 0) {
						isDisableBuildCollision = true;
					}
				}
			}
			if (isDisableBuildCollision) {
				if (!self.movement.noBuildingCollision) {
					self.movement.noBuildingCollision = true;
				}
			} else {
				if (self.movement.noBuildingCollision) {
					self.movement.noBuildingCollision = false;
				}
			}
			break;
		case INVULNERABLE:
			boolean isInvuln = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.INVULNERABLE) {
					if (buff.getValue() != 0) {
						isInvuln = true;
					}
				}
			}
			self.invulnerability.buffInvulnerable = isInvuln;
			self.invulnerability.invulnerable = self.invulnerability.scriptInvulnerable || self.invulnerability.buffInvulnerable;
			self.stateNotifier.abilitiesChanged();
			break;
		case HIDE_MINIMAP_ICON:
			boolean hideMini = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.HIDE_MINIMAP_ICON) {
					if (buff.getValue() != 0) {
						hideMini = true;
					}
				}
			}
			self.visibility.hideMinimapIcon = hideMini;
			break;
		case FALSE_OWNERSHIP:
			int fakePlayerId = -1;
			int prio = -1;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.FALSE_OWNERSHIP) {
					if (buff.getValue() != 0) {
						int pr = (int) (buff.getValue() & 0b1111);
						if (pr > prio) {
							prio = pr;
							fakePlayerId = (int) (buff.getValue() >> 4);
						}
					}
				}
			}
			if (fakePlayerId >= 0) {
				self.fakePlayerIndex = fakePlayerId;
			} else {
				self.fakePlayerIndex = self.playerIndex;
			}
			break;
		case DETECTOR:
			self.visibility.detectorLevels.clear();
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.DETECTOR) {
					if (buff.getValue() != 0) {
						self.visibility.detectorLevels.add((int) buff.getValue());
					}
				}
			}
			break;
		case INVISIBLE:
		case DETECTED:
			byte invisLevels = 0;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.INVISIBLE) {
					if ((buff.getValue() != 0)
							&& (!self.paused || ((buff.getValue() & StateModBuff.INVISIBLE_UNLESS_PAUSED) == 0))) {
						invisLevels = (byte) (invisLevels | buff.getValue());
					}
				}
			}
			self.visibility.invisLevels = invisLevels;

			long detections = 0;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.DETECTED) {
					if (buff.getValue() != 0) {
						if ((((byte) buff.getValue()) & self.visibility.invisLevels) == self.visibility.invisLevels) {
							detections = (detections | (buff.getValue() >> 8));
						}
					}
				}
			}
			self.visibility.detections = detections;
			break;
		case UNSELECTABLE:
			long unselectablePlayers = 0;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.UNSELECTABLE) {
					if (buff.getValue() != 0) {
						unselectablePlayers = (unselectablePlayers | buff.getValue());
					}
				}
			}
			self.visibility.unselectablePlayers = unselectablePlayers;
			break;
		case UNTARGETABLE:
			long untargetablePlayers = 0;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.UNTARGETABLE) {
					if (buff.getValue() != 0) {
						untargetablePlayers = (untargetablePlayers | buff.getValue());
					}
				}
			}
			self.visibility.untargetablePlayers = untargetablePlayers;
			break;
		case UNENUMERABLE:
			boolean isUnenumerable = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.UNENUMERABLE) {
					if (buff.getValue() != 0) {
						isUnenumerable = true;
					}
				}
			}
			self.unenumerable = isUnenumerable;
			break;
		case UNAUTOATTACKABLE:
			boolean isUnAutoAttackable = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.UNAUTOATTACKABLE) {
					if (buff.getValue() != 0) {
						isUnAutoAttackable = true;
					}
				}
			}
			self.auto.autoTargetable = !isUnAutoAttackable;
			break;
		case BLOCK_REPAIR:
			boolean isRepairBlocked = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.BLOCK_REPAIR) {
					if (buff.getValue() != 0) {
						isRepairBlocked = true;
					}
				}
			}
			self.blocked.repairBlocked = isRepairBlocked;
			break;
		case BLOCK_TRAINING:
			boolean isTrainingBlocked = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.BLOCK_TRAINING) {
					if (buff.getValue() != 0) {
						isTrainingBlocked = true;
					}
				}
			}
			self.blocked.trainingBlocked = isTrainingBlocked;
			break;
		case BLOCK_CONSTRUCTION:
			boolean isConstructionBlocked = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.BLOCK_CONSTRUCTION) {
					if (buff.getValue() != 0) {
						isConstructionBlocked = true;
					}
				}
			}
			self.blocked.constructionBlocked = isConstructionBlocked;
			break;
		default:
			break;
		}
	}

	static void computeDerivedFields(final CUnit self, final NonStackingStatBuffType type) {
		switch (type) {
		case DEF:
		case DEFPCT:
			self.defenseState.baseDefense = self.unitType.getDefense() + self.defenseState.agilityDefensePermanentBonus
					+ self.defenseState.permanentDefenseBonus;
			self.defenseState.currentDefenseDisplay = Math.round(self.defenseState.baseDefense);

			float totalNSDefBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.DEF, 0);
			totalNSDefBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.DEFPCT, totalNSDefBuff,
					self.defenseState.baseDefense);

			self.defenseState.totalTemporaryDefenseBonus = self.defenseState.temporaryDefenseBonus + self.defenseState.agilityDefenseTemporaryBonus
					+ totalNSDefBuff;
			self.defenseState.currentDefense = self.defenseState.baseDefense + self.defenseState.totalTemporaryDefenseBonus + self.defenseState.scriptDefenseBonus;
			break;
		case HPGEN:
		case HPGENPCT:
		case MAXHPGENPCT:
			float totalNSHPGenBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.HPGEN, 0);
			totalNSHPGenBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.HPGENPCT, totalNSHPGenBuff,
					self.stats.lifeRegen + self.stats.lifeRegenBonus + self.stats.lifeRegenStrengthBonus);
			totalNSHPGenBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.MAXHPGENPCT, totalNSHPGenBuff,
					self.stats.maximumLife);
			self.stats.currentLifeRegenPerTick = (self.stats.lifeRegen + self.stats.lifeRegenBonus + self.stats.lifeRegenStrengthBonus
					+ totalNSHPGenBuff) * WarsmashConstants.SIMULATION_STEP_TIME;
			break;
		case MPGEN:
		case MPGENPCT:
		case MAXMPGENPCT:
			float totalNSMPGenBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.MPGEN, 0);
			totalNSMPGenBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.MPGENPCT, totalNSMPGenBuff,
					self.stats.manaRegen + self.stats.manaRegenBonus + self.stats.manaRegenIntelligenceBonus);
			totalNSMPGenBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.MAXMPGENPCT, totalNSMPGenBuff,
					self.stats.maximumMana);
			self.stats.currentManaRegenPerTick = (self.stats.manaRegen + self.stats.manaRegenBonus + self.stats.manaRegenIntelligenceBonus
					+ totalNSMPGenBuff) * WarsmashConstants.SIMULATION_STEP_TIME;
			break;
		case MVSPDPCT:
		case MVSPD:
			float totalNSMvSpdBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.MVSPD, 0);
			totalNSMvSpdBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.MVSPDPCT, totalNSMvSpdBuff,
					self.stats.baseSpeed);
			self.stats.speedBonus = Math.round(totalNSMvSpdBuff + self.defenseState.agilityMoveBonus);
			self.computeFinalSpeed();
			break;
		case MELEEATK:
		case MELEEATKPCT:
			for (final CUnitAttack attack : self.getUnitSpecificAttacks()) {
				if ((attack.getWeaponType() != null) && (attack.getWeaponType() == CWeaponType.NORMAL)) {
					attack.setNonStackingFlatBuffs(self.nonStackingStatBuffs.get(NonStackingStatBuffType.MELEEATK));
					attack.setNonStackingPctBuffs(self.nonStackingStatBuffs.get(NonStackingStatBuffType.MELEEATKPCT));
					attack.computeDerivedFields();
				}
			}
			self.notifyAttacksChanged();
			break;
		case RNGDATK:
		case RNGDATKPCT:
			for (final CUnitAttack attack : self.getUnitSpecificAttacks()) {
				if ((attack.getWeaponType() != null) && (attack.getWeaponType() != CWeaponType.NORMAL)) {
					attack.setNonStackingFlatBuffs(self.nonStackingStatBuffs.get(NonStackingStatBuffType.RNGDATK));
					attack.setNonStackingPctBuffs(self.nonStackingStatBuffs.get(NonStackingStatBuffType.RNGDATKPCT));
					attack.computeDerivedFields();
				}
			}
			self.notifyAttacksChanged();
			break;
		case ALLATK:
		case ALLATKPCT:
			for (final CUnitAttack attack : self.getUnitSpecificAttacks()) {
				if ((attack.getWeaponType() != null) && (attack.getWeaponType() == CWeaponType.NORMAL)) {
					attack.setNonStackingFlatBuffs(self.nonStackingStatBuffs.get(NonStackingStatBuffType.MELEEATK));
					attack.setNonStackingPctBuffs(self.nonStackingStatBuffs.get(NonStackingStatBuffType.MELEEATKPCT));
					attack.computeDerivedFields();
				}
			}
			for (final CUnitAttack attack : self.getUnitSpecificAttacks()) {
				if ((attack.getWeaponType() != null) && (attack.getWeaponType() != CWeaponType.NORMAL)) {
					attack.setNonStackingFlatBuffs(self.nonStackingStatBuffs.get(NonStackingStatBuffType.RNGDATK));
					attack.setNonStackingPctBuffs(self.nonStackingStatBuffs.get(NonStackingStatBuffType.RNGDATKPCT));
					attack.computeDerivedFields();
				}
			}
			self.notifyAttacksChanged();
			break;
		case ATKSPD:
			float totalNSAtkSpdBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.ATKSPD, 0);
			for (final CUnitAttack attack : self.getUnitSpecificAttacks()) {
				attack.setAttackSpeedModifier(totalNSAtkSpdBuff);
			}
			self.notifyAttacksChanged();
			break;
		case HPSTEAL:
			float totalNSVampBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.HPSTEAL, 0);
			if (self.lifestealListener != null) {
				self.lifestealListener.setAmount(totalNSVampBuff);
			} else {
				self.lifestealListener = new CUnitDefaultLifestealListener(totalNSVampBuff);
				self.addPostDamageListener(self.lifestealListener);
			}
			break;
		case THORNS:
			float totalNSThornsBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.THORNS, 0);
			if (self.flatThornsListener != null) {
				self.flatThornsListener.setAmount(totalNSThornsBuff);
			} else {
				self.flatThornsListener = new CUnitDefaultThornsListener(false, totalNSThornsBuff);
				self.addDamageTakenListener(self.flatThornsListener);
			}
			break;
		case THORNSPCT:
			float totalNSThornsPctBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.THORNSPCT, 0);
			if (self.percentThornsListener != null) {
				self.percentThornsListener.setAmount(totalNSThornsPctBuff);
			} else {
				self.percentThornsListener = new CUnitDefaultThornsListener(true, totalNSThornsPctBuff);
				self.addDamageTakenListener(self.percentThornsListener);
			}
			break;
		case MAXHPPCT:
		case MAXHP:
			float totalNSMaxHPBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.MAXHP, 0);
			totalNSMaxHPBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.MAXHPPCT, totalNSMaxHPBuff,
					self.stats.baseMaximumLife);
			final int newMaxLife = Math.round((self.stats.baseMaximumLife + totalNSMaxHPBuff) * self.handicap);
			if (newMaxLife > self.stats.maximumLife) {
				self.life += newMaxLife - self.stats.maximumLife;
				self.stats.maximumLife = newMaxLife;
			} else {
				self.stats.maximumLife = newMaxLife;
				self.life = Math.min(self.life, self.stats.maximumLife);
			}
			break;
		case MAXMPPCT:
		case MAXMP:
			float totalNSMaxMPBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.MAXMP, 0);
			totalNSMaxMPBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.MAXMPPCT, totalNSMaxMPBuff,
					self.stats.baseMaximumMana);
			final int newMaxMana = self.stats.baseMaximumMana + Math.round(totalNSMaxMPBuff);
			if (newMaxMana > self.stats.maximumMana) {
				self.stats.mana += newMaxMana - self.stats.maximumMana;
				self.stats.maximumMana = newMaxMana;
			} else {
				self.stats.maximumMana = newMaxMana;
				self.stats.mana = Math.min(self.stats.mana, self.stats.maximumMana);
			}
			break;
		case STR:
		case STRPCT:
		case AGI:
		case AGIPCT:
		case INT:
		case INTPCT:
			throw new RuntimeException("Cannot run method with hero stat type");
		default:
			break;
		}
	}

	static void computeDerivedHeroFields(final CUnit self, final CSimulation game, final NonStackingStatBuffType type) {
		switch (type) {
		case STRPCT:
		case STR:
			if (self.isHero()) {
				float totalStrBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.STR, 0);
				totalStrBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.STRPCT, totalStrBuff,
						self.getHeroData().getStrength().getCurrentBase());
				self.getHeroData().setStrengthBuffBonus(game, self, Math.round(totalStrBuff));
			}
			break;
		case AGIPCT:
		case AGI:
			if (self.isHero()) {
				float totalAgiBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.AGI, 0);
				totalAgiBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.AGIPCT, totalAgiBuff,
						self.getHeroData().getAgility().getCurrentBase());
				self.getHeroData().setAgilityBuffBonus(game, self, Math.round(totalAgiBuff));
			}
			break;
		case INTPCT:
		case INT:
			if (self.isHero()) {
				float totalIntBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.INT, 0);
				totalIntBuff = self.nonStackingStatBuffs.sum(NonStackingStatBuffType.INTPCT, totalIntBuff,
						self.getHeroData().getIntelligence().getCurrentBase());
				self.getHeroData().setIntelligenceBuffBonus(game, self, Math.round(totalIntBuff));
			}
			break;
		default:
			throw new RuntimeException("Cannot run method with non-hero stat type");
		}
	}

	static NonStackingFx addNonStackingFx(final CUnit self, final CSimulation game, final String stackingKey, final War3ID id,
			final CEffectType target) {
		List<NonStackingFx> existingArts = self.nonStackingFx.get(stackingKey);
		final NonStackingFx newFx = new NonStackingFx(stackingKey, id);
		if (existingArts == null) {
			existingArts = new ArrayList<>();
			self.nonStackingFx.put(stackingKey, existingArts);
		}
		if (existingArts.isEmpty()) {
			final SimulationRenderComponent fx = game.createPersistentSpellEffectOnUnit(self, id, target);
			newFx.setArt(fx);
		} else {
			newFx.setArt(existingArts.iterator().next().getArt());
		}
		existingArts.add(newFx);
		return newFx;
	}

	static void removeNonStackingFx(final CUnit self, final CSimulation game, final NonStackingFx fx) {
		final List<NonStackingFx> existingArts = self.nonStackingFx.get(fx.getStackingKey());
		if (existingArts != null) {
			existingArts.remove(fx);
			if (existingArts.isEmpty()) {
				fx.getArt().remove();
			}
		}
	}

	static void addNonStackingDisplayBuff(final CUnit self, final CSimulation game, final String stackingKey, final CBuff buff) {
		List<CBuff> existingBuffs = self.nonStackingDisplayBuffs.get(stackingKey);
		if (existingBuffs == null) {
			existingBuffs = new ArrayList<>();
			self.nonStackingDisplayBuffs.put(stackingKey, existingBuffs);
		}
		if (existingBuffs.isEmpty()) {
			self.add(game, buff);
		} else {
			final CBuff currentBuff = self.getFirstAbilityOfType(buff.getClass());
			if (currentBuff == null) {
				existingBuffs.clear();
				self.add(game, buff);
			} else {
				if (buff.getLevel() >= currentBuff.getLevel()) {
					self.remove(game, currentBuff);
					self.add(game, buff);
				}

			}
		}
		existingBuffs.add(buff);
	}

	static void removeNonStackingDisplayBuff(final CUnit self, final CSimulation game, final String stackingKey, final CBuff buff) {
		final List<CBuff> existingBuffs = self.nonStackingDisplayBuffs.get(stackingKey);
		if (existingBuffs != null) {
			existingBuffs.remove(buff);
			CBuff currentBuff = self.getFirstAbilityOfType(buff.getClass());
			if (currentBuff == buff) {
				self.remove(game, currentBuff);
				// Auras leave a removed unit one by one; the next one's buff would go on a unit no longer drawn.
				if (!existingBuffs.isEmpty() && !self.removedFromGame) {
					currentBuff = null;
					for (final CBuff iterBuff : existingBuffs) {
						if ((currentBuff == null) || (currentBuff.getLevel() < iterBuff.getLevel())) {
							currentBuff = iterBuff;
						}
					}
					self.add(game, currentBuff);
				}
			}
		}
	}

	static void computeAllDerivedFields(final CUnit self) {
		self.stats.baseLifeRegenPerTick = self.stats.lifeRegen * WarsmashConstants.SIMULATION_STEP_TIME;
		computeDerivedFields(self, NonStackingStatBuffType.DEF);
		computeDerivedFields(self, NonStackingStatBuffType.HPGEN);
		computeDerivedFields(self, NonStackingStatBuffType.MPGEN);
		if (self.getUnitSpecificAttacks() != null) {
			computeDerivedFields(self, NonStackingStatBuffType.ALLATK);
			computeDerivedFields(self, NonStackingStatBuffType.ATKSPD);
		}
		computeDerivedFields(self, NonStackingStatBuffType.HPSTEAL);
		computeDerivedFields(self, NonStackingStatBuffType.MVSPD);
		computeDerivedFields(self, NonStackingStatBuffType.THORNS);
		computeDerivedFields(self, NonStackingStatBuffType.THORNSPCT);
		computeDerivedFields(self, NonStackingStatBuffType.MAXHP);
		computeDerivedFields(self, NonStackingStatBuffType.MAXMP);
	}

	static void computeAllUnitStates(final CUnit self, final CSimulation game) {
		computeUnitState(self, game, StateModBuffType.FALSE_OWNERSHIP);
		computeUnitState(self, game, StateModBuffType.HIDE_MINIMAP_ICON);
		computeUnitState(self, game, StateModBuffType.DETECTOR);
		computeUnitState(self, game, StateModBuffType.INVISIBLE);
		computeUnitState(self, game, StateModBuffType.INVULNERABLE);
		computeUnitState(self, game, StateModBuffType.ENABLE_ATTACK);
		computeUnitState(self, game, StateModBuffType.DISABLE_AUTO_ATTACK);
		computeUnitState(self, game, StateModBuffType.DISABLE_AUTO_CAST);
		computeUnitState(self, game, StateModBuffType.DISABLE_ASSIST_ALLY);
		computeUnitState(self, game, StateModBuffType.DISABLE_UNIT_COLLISION);
		computeUnitState(self, game, StateModBuffType.DISABLE_BUILDING_COLLISION);
		computeUnitState(self, game, StateModBuffType.MAGIC_IMMUNE);
		computeUnitState(self, game, StateModBuffType.MORPH_IMMUNE);
		computeUnitState(self, game, StateModBuffType.RESISTANT);
		computeUnitState(self, game, StateModBuffType.STUN);
		computeUnitState(self, game, StateModBuffType.PAUSED);
		computeUnitState(self, game, StateModBuffType.UNSELECTABLE);
		computeUnitState(self, game, StateModBuffType.UNTARGETABLE);
		computeUnitState(self, game, StateModBuffType.UNENUMERABLE);
		computeUnitState(self, game, StateModBuffType.UNAUTOATTACKABLE);
		computeUnitState(self, game, StateModBuffType.BLOCK_REPAIR);
		computeUnitState(self, game, StateModBuffType.BLOCK_TRAINING);
		computeUnitState(self, game, StateModBuffType.BLOCK_CONSTRUCTION);
	}

	static void setUnitState(final CUnit self, final CSimulation game, final CUnitState whichUnitState, final float value) {
		switch (whichUnitState) {
		// Measured: the original keeps a script's life and mana within the unit's maximum, and leaves the maximum
		// itself as it is.
		case LIFE:
			if (self.isDead()) {
				self.setDeadScriptLife(value);
				return;
			}
			self.applyLife(game, Math.min(value, self.getMaximumLife()));
			break;
		case MANA:
			self.stats.mana = Math.max(0, Math.min(value, self.getMaximumMana()));
			self.stateNotifier.manaChanged();
			break;
		default:
			return;
		}
		checkStateEvents(self, true);
	}

	static RemovableTriggerEvent addStateEvent(final CUnit self, final GlobalScope globalScope, final Trigger whichTrigger,
			final CUnitState state, final CLimitOp limitOp, final double limitValue) {
		final CUnitStateEvent event = new CUnitStateEvent(globalScope, self, whichTrigger, state, limitOp, limitValue);
		self.addEvent(event);
		return event;
	}

	static void checkStateEvents(final CUnit self, final boolean written) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_STATE_LIMIT);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				if (i < eventList.size()) {
					((CUnitStateEvent) eventList.get(i)).check(self, written);
				}
			}
		}
	}

	static float getUnitState(final CUnit self, final CSimulation game, final CUnitState whichUnitState) {
		switch (whichUnitState) {
		case LIFE:
			return self.getLife();
		case MANA:
			return self.getMana();
		case MAX_LIFE:
			return self.getMaximumLife();
		case MAX_MANA:
			return self.getMaximumMana();
		}
		return 0;
	}

	static float getScriptUnitState(final CUnit self, final CSimulation game, final CUnitState whichUnitState) {
		final float value = getUnitState(self, game, whichUnitState);
		switch (whichUnitState) {
		case LIFE:
			if (self.isDead() && !Float.isNaN(self.deadScriptLife)) {
				return self.deadScriptLife;
			}
			return value - regenNotYetDue(self, game, self.life, self.stats.lifeAfterRegen, self.stats.lifeRegenGiven);
		case MANA:
			return value - regenNotYetDue(self, game, self.stats.mana, self.stats.manaAfterRegen, self.stats.manaRegenGiven);
		default:
			return value;
		}
	}

	static float regenNotYetDue(final CUnit self, final CSimulation game, final float value, final float afterRegen,
			final float given) {
		if ((given == 0) || (value != afterRegen) || (self.stats.regenTick != (game.getGameTurnTick() - 1))) {
			return 0;
		}
		final double exactTime = game.getTimerFiringExactTime();
		if (Double.isNaN(exactTime)) {
			return 0;
		}
		final double ahead = (game.getGameTurnTick() * (double) WarsmashConstants.SIMULATION_STEP_TIME) - exactTime;
		if (ahead <= 0) {
			return 0;
		}
		return (float) (given * Math.min(1, ahead / WarsmashConstants.SIMULATION_STEP_TIME));
	}
}
