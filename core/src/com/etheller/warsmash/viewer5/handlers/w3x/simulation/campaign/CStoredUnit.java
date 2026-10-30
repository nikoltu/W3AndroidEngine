package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.Aliased;

/**
 * What StoreUnit keeps. Measured on 1.29: type, hero experience, skill points, base attributes, learned skills and
 * items with their charges. A restored unit is a new one with full life and mana and the default move speed, and the
 * stored unit keeps its items.
 */
public final class CStoredUnit {
	private final War3ID typeId;
	private boolean hero;
	private int xp;
	private int skillPoints;
	private int strength;
	private int agility;
	private int intelligence;
	private final List<StoredSkill> skills = new ArrayList<>();
	private final List<StoredItem> items = new ArrayList<>();

	public CStoredUnit(final CUnit unit) {
		this.typeId = unit.getTypeId();
		final CAbilityHero heroData = unit.getHeroData();
		if (heroData != null) {
			this.hero = true;
			this.xp = heroData.getXp();
			this.skillPoints = heroData.getSkillPoints();
			this.strength = heroData.getStrength().getBase();
			this.agility = heroData.getAgility().getBase();
			this.intelligence = heroData.getIntelligence().getBase();
			for (final CAbility learned : heroData.getLearnedHeroAbilities()) {
				if ((learned instanceof Aliased) && (learned instanceof CLevelingAbility)) {
					this.skills.add(new StoredSkill(((Aliased) learned).getAlias(),
							((CLevelingAbility) learned).getLevel()));
				}
			}
		}
		final CAbilityInventory inventory = unit.getInventoryData();
		if (inventory != null) {
			for (int slot = 0; slot < inventory.getItemCapacity(); slot++) {
				final CItem item = inventory.getItemInSlot(slot);
				if (item != null) {
					this.items.add(new StoredItem(item.getTypeId(), item.getCharges(), slot));
				}
			}
		}
	}

	public CUnit restore(final CSimulation game, final int playerIndex, final float x, final float y,
			final float facing) {
		final CUnit unit = game.createUnitSimple(this.typeId, playerIndex, x, y, facing);
		if (unit == null) {
			return null;
		}
		final CAbilityHero heroData = unit.getHeroData();
		if (this.hero && (heroData != null)) {
			heroData.setXpQuiet(game, unit, this.xp);
			heroData.setStrengthBase(game, unit, this.strength);
			heroData.setAgilityBase(game, unit, this.agility);
			heroData.setIntelligenceBase(game, unit, this.intelligence);
			heroData.restoreLearnedSkills(game, unit, this.skills);
			heroData.setSkillPoints(this.skillPoints);
			unit.setLife(game, unit.getMaximumLife());
			unit.setMana(unit.getMaximumMana());
		}
		final CAbilityInventory inventory = unit.getInventoryData();
		if (inventory != null) {
			for (final StoredItem stored : this.items) {
				final CItem item = game.createItem(stored.typeId, unit.getX(), unit.getY());
				if (item != null) {
					item.setCharges(stored.charges);
					inventory.giveItem(game, unit, item, stored.slot, false);
				}
			}
		}
		return unit;
	}

	public static final class StoredSkill {
		public final War3ID abilityId;
		public final int level;

		StoredSkill(final War3ID abilityId, final int level) {
			this.abilityId = abilityId;
			this.level = level;
		}
	}

	private static final class StoredItem {
		private final War3ID typeId;
		private final int charges;
		private final int slot;

		StoredItem(final War3ID typeId, final int charges, final int slot) {
			this.typeId = typeId;
			this.charges = charges;
			this.slot = slot;
		}
	}
}
