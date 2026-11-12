package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbilityGenericSingleIconPassiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.GenericSingleIconActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpell;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units.CBuffDisease;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CWeaponType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitAppliedUpgrade;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade.CUpgradeEffect;

/**
 * Changing a unit's type, the unit types scripts add and remove, applied upgrades and illusions.
 */
final class CUnitTypes {
	/**
	 * Base codes of the abilities whose buffs make a unit UNIT_TYPE_POISONED; measured on 1.29: Poison Sting, Slow
	 * Poison, Envenomed Weapons, Orb of Venom and Shadow Strike.
	 */
	private static boolean isPoisonCode(final War3ID code) {
		switch (code.asStringValue()) {
		case "Apoi":
		case "Aspo":
		case "Aven":
		case "Apo2":
		case "AIpb":
		case "AEsh":
			return true;
		default:
			return false;
		}
	}

	private static boolean isPoisoned(final CUnit self) {
		for (final CAbility ability : self.getAbilities()) {
			if (ability instanceof CBuff) {
				final CAbility source = ((CBuff) ability).getSourceAbility();
				if ((source != null) && isPoisonCode(source.getCode())) {
					return true;
				}
			}
		}
		return false;
	}

	private CUnitTypes() {
	}

	static void setTypeId(final CUnit self, final CSimulation game, final War3ID typeId, final boolean keepRatios) {
		setTypeId(self, game, typeId, keepRatios, true);
	}

	static void setTypeId(final CUnit self, final CSimulation game, final War3ID typeId, final boolean keepRatios,
			final boolean updateArt) {
		game.getWorldCollision().removeUnit(self);
		final CPlayer player = game.getPlayer(self.playerIndex);
		player.removeTechtreeUnlocked(game, self.typeId);
		game.getUnitData().unapplyPlayerUpgradesToUnit(game, self.playerIndex, self.unitType, self);
		self.typeId = typeId;
		player.addTechtreeUnlocked(game, self.typeId);
		final float lifeRatio = self.stats.maximumLife == 0 ? 1 : self.life / self.stats.maximumLife;
		final float manaRatio = self.stats.maximumMana == 0 ? Float.NaN : self.stats.mana / self.stats.maximumMana;
		final CUnitType previousUnitType = self.getUnitType();
		self.unitType = game.getUnitData().getUnitType(typeId);
		self.stats.baseMaximumMana += self.unitType.getManaMaximum() - previousUnitType.getManaMaximum();
		self.stats.baseMaximumLife += self.unitType.getMaxLife() - previousUnitType.getMaxLife();
		self.stats.maximumMana += self.unitType.getManaMaximum() - previousUnitType.getManaMaximum();
		self.stats.maximumLife += self.unitType.getMaxLife() - previousUnitType.getMaxLife();
		self.stats.lifeRegen = self.unitType.getLifeRegen();
		self.stats.manaRegen = self.unitType.getManaRegen();
		CUnitStats.computeAllDerivedFields(self);

		if (updateArt) {
			self.placement.flyHeight = self.unitType.getDefaultFlyingHeight();
		}
		self.placement.turnRate = self.unitType.getTurnRate();
		self.placement.propWindow = self.unitType.getPropWindow();
		self.stats.maxSpeed = self.unitType.getMaxSpeed();
		if (self.stats.maxSpeed == 0) {
			self.stats.maxSpeed = (int) (self.unitType.isBuilding() ? game.getGameplayConstants().getMaxBldgSpeed()
					: game.getGameplayConstants().getMaxUnitSpeed());
		}
		self.stats.minSpeed = self.unitType.getMinSpeed();
		if (self.stats.minSpeed == 0) {
			self.stats.minSpeed = (int) (self.unitType.isBuilding() ? game.getGameplayConstants().getMinBldgSpeed()
					: game.getGameplayConstants().getMinUnitSpeed());
		}
		self.stats.baseSpeed = self.unitType.getSpeed();
		self.classifications.removeAll(previousUnitType.getClassifications());
		self.classifications.addAll(self.unitType.getClassifications());
		self.targetedAs.removeAll(previousUnitType.getTargetedAs());
		self.targetedAs.addAll(self.unitType.getTargetedAs());
		self.defenseState.defenseType = self.unitType.getDefenseType();
		self.acquisitionRange = self.unitType.getDefaultAcquisitionRange();
		self.structure = self.unitType.isBuilding();
		self.death.raisable = self.unitType.isRaise();
		self.death.decays = self.unitType.isDecay();
		final List<War3ID> sharedAbilities = new ArrayList<War3ID>(previousUnitType.getAbilityList());
		// sharedAbilities.addAll(previousUnitType.getHeroAbilityList());
		final List<War3ID> newIds = new ArrayList<War3ID>(self.unitType.getAbilityList());
		// newIds.addAll(this.unitType.getHeroAbilityList());
		sharedAbilities.retainAll(newIds); // TODO Seems wasteful, but need to avoid messing up heros on transform
		final List<CAbility> persistedAbilities = new ArrayList<>();
		final List<CAbility> removedAbilities = new ArrayList<>();
		final List<CAbility> startingAbilities = new ArrayList<>(self.abilities);
		for (final CAbility ability : startingAbilities) {
			if (!ability.isPermanent() && !sharedAbilities.contains(ability.getAlias())
					&& (!ability.isHero() || !self.unitType.isHero())
					&& !(ability.getAbilityCategory() == CAbilityCategory.BUFF)) {
				ability.onRemove(game, self);
				game.onAbilityRemovedFromUnit(self, ability);
				removedAbilities.add(ability);
			} else {
				persistedAbilities.add(ability);
			}
		}
		for (final CAbility removed : removedAbilities) {
			self.abilities.remove(removed); // TODO remove inefficient O(N) search
		}
		game.unitUpdatedType(self, typeId, true);
		game.getUnitData().addMissingDefaultAbilitiesToUnit(game, game.getHandleIdAllocator(), self.unitType, false, -1,
				self.stats.baseSpeed, self);
		{
			// Remove and add the persisted abilities, so that some stuff like move and
			// attack are "first" in the end resulting list. This is "dumb" and a better
			// design later might be worth considering; it fixes a bug where otherwise
			// `Chaos` used on a worker into another near identical worker was changing the
			// ability order used by "smart" such that the unit would prefer Attack over
			// Harvest
			for (final CAbility persisted : persistedAbilities) {
				self.abilities.remove(persisted);
			}
			for (final CAbility persisted : persistedAbilities) {
				self.abilities.add(persisted);
			}
		}
		game.getWorldCollision().addUnit(self);
		for (final CAbility ability : persistedAbilities) {
			ability.onSetUnitType(game, self);
			game.onAbilityAddedToUnit(self, ability);
		}
		game.getUnitData().applyPlayerUpgradesToUnit(game, self.playerIndex, self.unitType, self);
		CUnitStats.computeAllDerivedFields(self);
		CUnitStats.computeAllUnitStates(self, game);
		if (keepRatios) {
			self.life = lifeRatio * self.stats.maximumLife;
			if (Float.isNaN(manaRatio)) {
				self.stats.mana = self.unitType.getManaInitial();
			} else {
				self.stats.mana = manaRatio * self.stats.maximumMana;
			}
		} else {
			self.life += self.unitType.getMaxLife() - previousUnitType.getMaxLife();
			if (Float.isNaN(manaRatio)) {
				self.stats.mana = self.unitType.getManaInitial();
			} else {
				self.stats.mana += self.unitType.getManaMaximum() - previousUnitType.getManaMaximum();
			}
		}
	}

	/**
	 * Turns this freshly created unit into an illusion: it keeps moving, attacking, its hero attributes and its
	 * items (whose stat bonuses still apply) but loses every other ability, the hero skill menu, food use and its
	 * place in the player's hero list, and on death it vanishes with the buff's special effect instead of a corpse.
	 * A hero's illusion still reads its learned skills to scripts, a plain unit's its abilities that are not spells.
	 */
	static void makeIllusion(final CUnit self, final CSimulation simulation, final float damageDealtFactor,
			final float damageTakenFactor, final War3ID buffId) {
		self.illusionState.illusion = true;
		self.illusionState.illusionDamageDealtFactor = damageDealtFactor;
		self.illusionState.illusionDamageTakenFactor = damageTakenFactor;
		self.setExplodesOnDeath(true);
		self.setExplodesOnDeathBuffId(buffId);
		final CPlayer player = simulation.getPlayer(self.playerIndex);
		player.setUnitFoodUsed(self, 0);
		player.setUnitFoodMade(self, 0);
		simulation.getPlayerHeroes(self.playerIndex).remove(self);
		for (final CAbility ability : new ArrayList<>(self.getAbilities())) {
			if (ability instanceof CAbilityHero) {
				ability.setIconShowing(false);
			}
			else if (!(ability instanceof CAbilityMove) && !(ability instanceof CAbilityAttack)
					&& !(ability instanceof CAbilityInventory) && (ability.getItem() == null)
					&& !isPassiveKeptByIllusion(ability)) {
				if ((self.getHeroData() != null) || !isSpellLostByIllusion(ability)) {
					self.illusionState.illusionAbilityLevels.put(ability.getAlias().getValue(),
							ability instanceof CLevelingAbility ? Math.max(1, ((CLevelingAbility) ability).getLevel())
									: 1);
				}
				self.remove(simulation, ability);
			}
		}
	}

	static boolean isSpellLostByIllusion(final CAbility ability) {
		return (ability instanceof CAbilitySpell) || ((ability instanceof GenericSingleIconActiveAbility)
				&& (((GenericSingleIconActiveAbility) ability).getUIManaCost() > 0));
	}

	/**
	 * The original game keeps passives on an illusion (Bash still stuns); auras keep their icon but give nothing.
	 */
	static boolean isPassiveKeptByIllusion(final CAbility ability) {
		return ability instanceof AbilityGenericSingleIconPassiveAbility;
	}

	static boolean isUnitType(final CUnit self, final CUnitTypeJass whichUnitType) {
		switch (whichUnitType) {
		case HERO:
			return self.isHero() && !self.illusionState.illusion;
		case DEAD:
			return self.isDead();
		case STRUCTURE:
			return self.isBuilding() || self.uprooted;

		case FLYING:
			return self.targetedAs.contains(CTargetType.AIR);
		case GROUND:
			return self.targetedAs.contains(CTargetType.GROUND);

		case ATTACKS_FLYING:
			for (final CUnitAttack attack : self.getCurrentAttacks()) {
				if (attack.getTargetsAllowed().contains(CTargetType.AIR)) {
					return true;
				}
			}
			return false;
		case ATTACKS_GROUND:
			for (final CUnitAttack attack : self.getCurrentAttacks()) {
				if (attack.getTargetsAllowed().contains(CTargetType.GROUND)) {
					return true;
				}
			}
			return false;

		case MELEE_ATTACKER:
			boolean hasAttacks = false;
			for (final CUnitAttack attack : self.getCurrentAttacks()) {
				if (attack.getWeaponType() != CWeaponType.NORMAL) {
					return false;
				}
				hasAttacks = true;
			}
			return hasAttacks;

		case RANGED_ATTACKER:
			for (final CUnitAttack attack : self.getCurrentAttacks()) {
				if (attack.getWeaponType() != CWeaponType.NORMAL) {
					return true;
				}
			}
			return false;

		case GIANT:
			return self.classifications.contains(CUnitClassification.GIANT);
		case SUMMONED:
			return self.classifications.contains(CUnitClassification.SUMMONED);
		case STUNNED:
			// 1.29: false also on a stunned unit, and UnitAddType cannot set it.
			return false;
		case PLAGUED:
			return self.getFirstAbilityOfType(CBuffDisease.class) != null;
		case SNARED:
			boolean isSnared = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.SNARED) {
					if (buff.getValue() != 0) {
						isSnared = true;
					}
				}
			}
			return isSnared;
		case UNDEAD:
			return self.classifications.contains(CUnitClassification.UNDEAD);
		case MECHANICAL:
			return self.classifications.contains(CUnitClassification.MECHANICAL);
		case PEON:
			return self.classifications.contains(CUnitClassification.PEON);
		case SAPPER:
			return self.classifications.contains(CUnitClassification.SAPPER);
		case TOWNHALL:
			return self.classifications.contains(CUnitClassification.TOWNHALL);
		case ANCIENT:
			return self.classifications.contains(CUnitClassification.ANCIENT);

		case TAUREN:
			return self.classifications.contains(CUnitClassification.TAUREN);
		case POISONED:
			return isPoisoned(self);
		case POLYMORPHED:
			boolean isPolymorphed = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.POLYMORPHED) {
					if (buff.getValue() != 0) {
						isPolymorphed = true;
					}
				}
			}
			return isPolymorphed;
		case SLEEPING:
			boolean isSleeping = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.SLEEPING) {
					if (buff.getValue() != 0) {
						isSleeping = true;
					}
				}
			}
			return isSleeping;
		case RESISTANT:
			return self.invulnerability.resistant;
		case ETHEREAL:
			boolean isEthereal = false;
			for (final StateModBuff buff : self.stateModBuffs) {
				if (buff.getBuffType() == StateModBuffType.ETHEREAL) {
					if (buff.getValue() != 0) {
						isEthereal = true;
					}
				}
			}
			return isEthereal;
		case MAGIC_IMMUNE:
			return self.isMagicImmune() || self.isLimitedMagicImmune();
		}
		return false;
	}

	static void addCheesyStateModBuff(final CUnit self, final CSimulation game, final StateModBuffType type) {
		// this might be less efficient than the original state mod buff system,
		// and cannot access the Detector Levels, etc, with the value
		self.addStateModBuff(new StateModBuff(type, CUnit.CHEESY_UNIQUE_NUMBER));
		self.computeUnitState(game, type);
	}

	static boolean removeCheesyStateModBuff(final CUnit self, final CSimulation game, final StateModBuffType type) {
		// this might be less efficient than the original state mod buff system,
		// and cannot access the Detector Levels, etc, with the value
		StateModBuff toRemove = null;
		for (final StateModBuff stateModBuff : self.stateModBuffs) {
			if ((stateModBuff.getBuffType() == type) && (stateModBuff.getValue() == CUnit.CHEESY_UNIQUE_NUMBER)) {
				toRemove = stateModBuff;
				break;
			}
		}
		if (toRemove != null) {
			self.removeStateModBuff(toRemove);
			self.computeUnitState(game, type);
			return true;
		}
		return false;
	}

	static boolean addUnitType(final CUnit self, final CSimulation game, final CUnitTypeJass whichUnitType) {
		switch (whichUnitType) {
		case HERO:
		case ATTACKS_FLYING:
		case ATTACKS_GROUND:
		case MELEE_ATTACKER:
		case RANGED_ATTACKER:
		case PLAGUED:
		case POISONED:
		case POLYMORPHED:
			// types which we are not allowing them to add and remove
			// I read online on a Hive thing that maybe users expect the API for these types
			// to be something that doesn't work, i.e. "IsUnitType(UNIT_TYPE_STUNNED)" would
			// return false on a stunned unit, but would return true if they use
			// "UnitAddType(UNIT_TYPE_STUNNED)"
			// then check for it. Seems extremely bogus but it's possible some custom
			// content might
			// depend on the pointless "feature" of adding and remove a setting that doesn't
			// work.
			// If you are here reading this because you're playing something that depends on
			// this
			// "feature" then this would be the place to add it
			return false;
		case SNARED:
			addCheesyStateModBuff(self, game, StateModBuffType.SNARED);
			return true;
		case STUNNED:
			return false;
		case SLEEPING:
			addCheesyStateModBuff(self, game, StateModBuffType.SLEEPING);
			return true;
		case DEAD:
			if (!self.isDead()) {
				self.kill(game);
				return true;
			}
			return false;
		case STRUCTURE:
			if (!self.isBuilding()) {
				// below is maybe not what wc3 actually does. the "Factory" ability
				// for pocket factory sets it to structure kind of like this,
				// but probably doesn't interact with its world collision (?)
				game.getWorldCollision().removeUnit(self);
				self.setStructure(true);
				game.getWorldCollision().addUnit(self);
				return true;
			}
			return false;
		case FLYING:
			return self.targetedAs.add(CTargetType.AIR);
		case GROUND:
			return self.targetedAs.add(CTargetType.GROUND);
		case GIANT:
			return self.classifications.add(CUnitClassification.GIANT);
		case SUMMONED:
			return self.classifications.add(CUnitClassification.SUMMONED);
		case UNDEAD:
			return self.classifications.add(CUnitClassification.UNDEAD);
		case MECHANICAL:
			return self.classifications.add(CUnitClassification.MECHANICAL);
		case PEON:
			return self.classifications.add(CUnitClassification.PEON);
		case SAPPER:
			return self.classifications.add(CUnitClassification.SAPPER);
		case TOWNHALL:
			return self.classifications.add(CUnitClassification.TOWNHALL);
		case ANCIENT:
			return self.classifications.add(CUnitClassification.ANCIENT);
		case TAUREN:
			return self.classifications.add(CUnitClassification.TAUREN);
		case RESISTANT:
			addCheesyStateModBuff(self, game, StateModBuffType.RESISTANT);
			return true;
		case ETHEREAL:
			addCheesyStateModBuff(self, game, StateModBuffType.ETHEREAL);
			return true;
		case MAGIC_IMMUNE:
			addCheesyStateModBuff(self, game, StateModBuffType.MAGIC_IMMUNE);
			return true;
		}
		return false;
	}

	static boolean removeUnitType(final CUnit self, final CSimulation game, final CUnitTypeJass whichUnitType) {
		switch (whichUnitType) {
		case HERO:
		case ATTACKS_FLYING:
		case ATTACKS_GROUND:
		case MELEE_ATTACKER:
		case RANGED_ATTACKER:
		case PLAGUED:
		case POISONED:
		case POLYMORPHED:
			// types which we are not allowing them to add and remove
			// I read online on a Hive thing that maybe users expect the API for these types
			// to be something that doesn't work, i.e. "IsUnitType(UNIT_TYPE_STUNNED)" would
			// return false on a stunned unit, but would return true if they use
			// "UnitAddType(UNIT_TYPE_STUNNED)"
			// then check for it. Seems extremely bogus but it's possible some custom
			// content might
			// depend on the pointless "feature" of adding and remove a setting that doesn't
			// work.
			// If you are here reading this because you're playing something that depends on
			// this
			// "feature" then this would be the place to add it
			return false;
		case SNARED:
			return removeCheesyStateModBuff(self, game, StateModBuffType.SNARED);
		case STUNNED:
			return false;
		case SLEEPING:
			return removeCheesyStateModBuff(self, game, StateModBuffType.SLEEPING);
		case DEAD:
			if (self.isDead()) {
				self.resurrect(game);
				return true;
			}
			return false;
		case STRUCTURE:
			if (self.isBuilding()) {
				// below is maybe not what wc3 actually does. the "Factory" ability
				// for pocket factory sets it to structure kind of like this,
				// but probably doesn't interact with its world collision (?)
				game.getWorldCollision().removeUnit(self);
				self.setStructure(false);
				game.getWorldCollision().addUnit(self);
				return true;
			}
			return false;
		case FLYING:
			return self.targetedAs.remove(CTargetType.AIR);
		case GROUND:
			return self.targetedAs.remove(CTargetType.GROUND);
		case GIANT:
			return self.classifications.remove(CUnitClassification.GIANT);
		case SUMMONED:
			return self.classifications.remove(CUnitClassification.SUMMONED);
		case UNDEAD:
			return self.classifications.remove(CUnitClassification.UNDEAD);
		case MECHANICAL:
			return self.classifications.remove(CUnitClassification.MECHANICAL);
		case PEON:
			return self.classifications.remove(CUnitClassification.PEON);
		case SAPPER:
			return self.classifications.remove(CUnitClassification.SAPPER);
		case TOWNHALL:
			return self.classifications.remove(CUnitClassification.TOWNHALL);
		case ANCIENT:
			return self.classifications.remove(CUnitClassification.ANCIENT);
		case TAUREN:
			return self.classifications.remove(CUnitClassification.TAUREN);
		case RESISTANT:
			return removeCheesyStateModBuff(self, game, StateModBuffType.RESISTANT);
		case ETHEREAL:
			return removeCheesyStateModBuff(self, game, StateModBuffType.ETHEREAL);
		case MAGIC_IMMUNE:
			return removeCheesyStateModBuff(self, game, StateModBuffType.MAGIC_IMMUNE);
		}
		return false;
	}

	static CUnitAppliedUpgrade getAppliedUpgrade(final CUnit self, War3ID id) {
		for (CUnitAppliedUpgrade upgrade : self.appliedUpgrades) {
			if (upgrade.getType().getTypeId().equals(id)) {
				return upgrade;
			}
		}
		return null;
	}

	static void applyUpgrade(final CUnit self, CSimulation simulation, CUpgradeType upgradeType, int level) {
		CUnitAppliedUpgrade upgrade = getAppliedUpgrade(self, upgradeType.getTypeId());
		if (upgrade != null) {
			if (upgrade.getLevel() == level) {
				Log.warn("Unit already has upgrade? " + upgrade.getLevel() + " (target" + level + ")");
				return;
			}
			unapplyUpgrade(self, simulation, upgradeType, upgrade);
		}
		for (CUpgradeEffect upgradeEffect : upgradeType.getUpgradeEffects()) {
			upgradeEffect.apply(simulation, self, level);
		}
		self.appliedUpgrades.add(new CUnitAppliedUpgrade(upgradeType, level));
		self.notifyUpgradesChanged();
	}

	static void unapplyUpgrade(final CUnit self, CSimulation simulation, CUpgradeType upgradeType) {
		CUnitAppliedUpgrade upgrade = getAppliedUpgrade(self, upgradeType.getTypeId());
		unapplyUpgrade(self, simulation, upgradeType, upgrade);
	}

	static void unapplyUpgrade(final CUnit self, CSimulation simulation, CUpgradeType upgradeType, CUnitAppliedUpgrade upgrade) {
		if (upgrade != null) {
			for (CUpgradeEffect upgradeEffect : upgradeType.getUpgradeEffects()) {
				upgradeEffect.unapply(simulation, self, upgrade.getLevel());
			}
		}
		self.appliedUpgrades.remove(upgrade);
	}
}
