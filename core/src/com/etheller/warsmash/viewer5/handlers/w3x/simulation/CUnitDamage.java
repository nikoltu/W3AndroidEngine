package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.listeners.CUnitAbilityEffectReactionListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDefenseType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackDamageTakenListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackDamageTakenModificationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackDamageTakenModificationListenerDamageModResult;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackEvasionListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackFinalDamageTakenModificationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitPriorityLoopData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAttackProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.listeners.CUnitAbilityProjReactionListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.listeners.CUnitAttackProjReactionListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CWidgetEvent;

/**
 * Damage taken by a unit, and the listeners that change or react to it.
 */
final class CUnitDamage {
	private CUnitDamage() {
	}

	static boolean checkForMiss(final CUnit self, final CSimulation simulation, final CUnit source, final boolean isAttack,
			final boolean isRanged, final CAttackType attackType, final CDamageType damageType, final float damage,
			final float bonusDamage) {
		boolean miss = false;
		// Evasion stays on an illusion but never makes it dodge in the original game.
		if (isAttack && !self.illusionState.illusion) {
			for (final CUnitAttackEvasionListener listener : new ArrayList<>(self.evasionListeners)) {
				miss = miss || listener.onAttack(simulation, source, self, isAttack, isRanged, damageType);
			}
		}
		return miss;
	}

	static boolean checkForAttackProjReaction(final CUnit self, final CSimulation simulation, final CUnit source,
			final CAttackProjectile projectile) {
		boolean allow = true;
		for (final CUnitAttackProjReactionListener listener : new ArrayList<>(self.attackProjReactionListeners)) {
			if (allow) {
				allow = allow && listener.onHit(simulation, source, self, projectile);
			}
		}
		return allow;
	}

	static boolean checkForAbilityProjReaction(final CUnit self, final CSimulation simulation, final CUnit source,
			final CProjectile projectile) {
		boolean allow = true;
		for (final CUnitAbilityProjReactionListener listener : new ArrayList<>(self.abilityProjReactionListeners)) {
			if (allow) {
				allow = allow && listener.onHit(simulation, source, self, projectile);
			}
		}
		return allow;
	}

	static boolean checkForAbilityEffectReaction(final CUnit self, final CSimulation simulation, final CUnit source,
			final CAbility ability) {
		boolean allow = true;
		for (final CUnitAbilityEffectReactionListener listener : new ArrayList<>(self.abilityEffectReactionListeners)) {
			if (allow) {
				allow = allow && listener.onHit(simulation, source, self, ability);
			}
		}
		return allow;
	}

	static CUnitAttackSettings checkForAttackPreLaunchModification(final CUnit self, final CSimulation simulation,
			final AbilityTarget target, final CUnitAttack attack, final CUnitAttackSettings settings) {
		if (!self.attackModifiers.isEmpty()) {
			int maxPriority = 0;
			int i = 0;
			final CUnitPriorityLoopData attackModLoop = new CUnitPriorityLoopData();
			while (i <= maxPriority) {
				attackModLoop.startLoop(i);
				for (final CUnitAttackModifier modification : new ArrayList<>(self.attackModifiers)) {
					int prio = modification.getPriority(simulation, self, target, attack, settings);
					if (prio > maxPriority) {
						maxPriority = prio;
					}
					if (prio == i && !attackModLoop.skipCurrentLevel()) {
						if (modification.checkPreLaunchApplication(simulation, self, target, attack, settings)) {
							modification.applyPreLaunchModification(simulation, self, target, attack, settings,
									attackModLoop);
						}
					}
				}
				if (attackModLoop.end()) {
					break;
				}
				i++;
			}
		}
		return settings;
	}

	static CUnitAttackSettings checkForAttackModification(final CUnit self, final CSimulation simulation, final AbilityTarget target,
			final CUnitAttack attack, final CUnitAttackSettings settings) {
		if (!self.attackModifiers.isEmpty()) {
			int maxPriority = 0;
			int i = 0;
			final CUnitPriorityLoopData attackModLoop = new CUnitPriorityLoopData();
			while (i <= maxPriority) {
				attackModLoop.startLoop(i);
				for (final CUnitAttackModifier modification : new ArrayList<>(self.attackModifiers)) {
					int prio = modification.getPriority(simulation, self, target, attack, settings);
					if (prio > maxPriority) {
						maxPriority = prio;
					}
					if (prio == i && !attackModLoop.skipCurrentLevel()) {
						if (modification.checkApplication(simulation, self, target, attack, settings)) {
							modification.applyModification(simulation, self, target, attack, settings, attackModLoop);
						}
					}
				}
				if (attackModLoop.end()) {
					break;
				}
				i++;
			}
		}
		return settings;
	}

	static boolean isImmuneToDamage(final CUnit self, final CSimulation simulation, final CDamageFlags flags,
			final CAttackType attackType, final CDamageType damageType) {
		if (flags != null && flags.isOnlyDamageSummons() && !self.illusionState.illusion
				&& !self.isUnitType(CUnitTypeJass.SUMMONED)) {
			return true;
		}
		if (simulation.getGameplayConstants().isMagicImmuneResistsDamage()) {
			if (attackType.isMagic() || damageType.isMagic()) {
				if (self.isMagicImmune() || (self.isLimitedMagicImmune() && !flags.isPassLimitedMagicImmune())) {
					return true;
				}
			} else if (attackType.isPhysical() || damageType.isPhysical()) {
				if (self.isUnitType(CUnitTypeJass.ETHEREAL)) {
					return true;
				}
			}
		} else {
			if (damageType.isOldMagic()
					&& (self.isMagicImmune() || (self.isLimitedMagicImmune() && !flags.isPassLimitedMagicImmune()))) {
				return true;
			}
		}
		return false;
	}

	static float damage(final CUnit self, final CSimulation simulation, final CUnit source, final CDamageFlags flags,
			final CAttackType attackType, final CDamageType damageType, final String weaponSoundType,
			final float damage) {
		return damage(self, simulation, source, flags, attackType, damageType, weaponSoundType, damage, 0);
	}

	static float damage(final CUnit self, final CSimulation simulation, final CUnit source, final CDamageFlags flags,
			final CAttackType attackType, final CDamageType damageType, final String weaponSoundType,
			final float damage, final float bonusDamage) {
		final boolean wasDead = self.isDead();
		if (wasDead) {
			return 0;
		}
		if (isImmuneToDamage(self, simulation, flags, attackType, damageType)) {
			return 0;
		}
		float trueDamage = 0;
		if (!self.invulnerability.invulnerable || (flags != null && (flags.isIgnoreInvulnerable() || (flags.isOnlyDamageSummons()
				&& simulation.getGameplayConstants().isInvulnerableSummonsTakeDispelDamage()
				&& self.isUnitType(CUnitTypeJass.SUMMONED))))) {

			final CUnitAttackDamageTakenModificationListenerDamageModResult result = new CUnitAttackDamageTakenModificationListenerDamageModResult(
					damage, bonusDamage);
			for (final CUnitAttackDamageTakenModificationListener listener : self.damageTakenModificationListeners) {
				listener.onDamage(simulation, source, self, flags, attackType, damageType, result);
			}

			final float damageRatioFromArmorClass = simulation.getGameplayConstants().getDamageRatioAgainst(attackType,
					(self.isBuilding() && self.isConstructing()) ? CDefenseType.NORMAL : self.getDefenseType());
			final float damageRatioFromDefense;
			final float defense = self.defenseState.currentDefense;
			if (damageType != CDamageType.NORMAL || (self.isBuilding() && self.isConstructing())) {
				damageRatioFromDefense = 1.0f;
			} else if (defense >= 0) {
				damageRatioFromDefense = 1f - ((defense * simulation.getGameplayConstants().getDefenseArmor())
						/ (1 + (simulation.getGameplayConstants().getDefenseArmor() * defense)));
			} else {
				damageRatioFromDefense = 2f
						- (float) StrictMath.pow(1f - simulation.getGameplayConstants().getDefenseArmor(), -defense);
			}
			trueDamage = damageRatioFromArmorClass * damageRatioFromDefense * result.computeFinalDamage();
			if ((source != null) && (trueDamage > 0)) {
				trueDamage *= simulation.getPlayer(source.getPlayerIndex()).getHandicapDamage();
				// whosyourdaddy, measured on 1.29: a Footman's 13 hits for 1300 and a script's 10 for 1000, whatever
				// the armor.
				if (simulation.getPlayer(source.getPlayerIndex()).hasCheat(CReleaseCheats.GOD_MODE)) {
					trueDamage = result.computeFinalDamage() * CReleaseCheats.GOD_MODE_DAMAGE_FACTOR;
				}
			}
			// The hit on a whosyourdaddy player's unit still fires the damage event, with no damage.
			if ((trueDamage > 0) && simulation.getPlayer(self.getPlayerIndex()).hasCheat(CReleaseCheats.GOD_MODE)) {
				trueDamage = 0;
			}

			for (final CUnitAttackFinalDamageTakenModificationListener listener : new ArrayList<>(
					self.finalDamageTakenModificationListeners)) {
				trueDamage = listener.onDamage(simulation, source, self, flags, attackType, damageType, trueDamage);
			}
			if (self.illusionState.illusion) {
				trueDamage *= self.illusionState.illusionDamageTakenFactor;
			}

			CUnitAggro.acquireAttackerOnHit(self, simulation, source, damage + bonusDamage);
			final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_DAMAGED);
			if (eventList != null) {
				for (final CWidgetEvent event : eventList) {
					final CommonTriggerExecutionScope damageScope = CommonTriggerExecutionScope.unitDamageTakenScope(
							JassGameEventsWar3.EVENT_UNIT_DAMAGED, event.getTrigger(), self, source, trueDamage);
					event.fire(self, damageScope);
					// BlzSetEventDamage in a damage trigger changes the damage this hit deals.
					trueDamage = damageScope.getDamageTaken();
				}
			}

			final boolean wasAboveMax = self.life > self.stats.maximumLife;
			self.life -= trueDamage;
			if ((result.computeFinalDamage() < 0) && !wasAboveMax && (self.life > self.stats.maximumLife)) {
				// NOTE wasAboveMax is for that weird life drain power to drain above max... to
				// be honest that's a crazy mechanic anyway so I didn't test whether it works
				// yet
				self.life = self.stats.maximumLife;
			}
			if (flags.isNonlethal() && self.life < 1) {
				self.life = 1;
			} else if (self.life < 0) {
				self.life = 0;
			}
			self.stateNotifier.lifeChanged();
			CUnitStats.checkStateEvents(self, false);
		}
		for (final CUnitAttackDamageTakenListener listener : new ArrayList<>(self.damageTakenListeners)) {
			listener.onDamage(simulation, source, self, flags, damageType, damage, bonusDamage, trueDamage);
		}
		simulation.unitDamageEvent(self, weaponSoundType, self.unitType.getArmorType());
		if ((source != null) && (trueDamage > 0)) {
			simulation.unitAttackedEvent(self, source);
		}
		if (self.isDead()) {
			if (!wasDead) {
				// A hero killed by artillery keeps its body, so it can still be revived.
				if (flags.isExplode() && !self.unitType.isHero()) {
					self.setExplodesOnDeath(true);
				}
				CUnitDeath.kill(self, simulation, source);
			}
		} else {
			// 1.29: a hit for 0 (Life Drain's first) draws no answer or run.
			if (((damage + bonusDamage) > 0) && (self.isIdleForAttack() || CUnitAggro.isReturningFromLostTarget(self)
					|| (self.isFleeing() && self.isCritter()))) {
				self.queueAttackBack(simulation, source, true, false);
			}
			self.aggro.lastCombatTick = simulation.getGameTurnTick();
			self.aggro.lastHurtTick = self.aggro.lastCombatTick;
			if ((source != null) && (source != self)) {
				CUnitAggro.noteHit(self, simulation.getGameTurnTick(), source, trueDamage);
			}
			CUnitUpdate.wakeByHit(self, simulation);
			if (source != null) {
				source.aggro.lastCombatTick = self.aggro.lastCombatTick;
			}
			CUnitAggro.callForHelp(self, simulation, source);
		}
		return trueDamage;
	}
}
