package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;

/**
 * Creates illusions the way every illusion ability does (Mirror Image, Wand of Illusion and the like): a copy of
 * the original's type, hero level, attributes and learned skills (only passives stay), carrying copies of its items in the same slots so their stat
 * bonuses apply, with the same share of life and mana, living for the given duration.
 */
public final class CIllusions {
	private CIllusions() {
	}

	public static CUnit create(final CSimulation simulation, final CUnit original, final int playerIndex,
			final float x, final float y, final float facing, final float damageDealtFactor,
			final float damageTakenFactor, final float duration, final War3ID buffId) {
		final CUnit image = simulation.createUnit(original.getTypeId(), playerIndex, x, y, facing);
		if (image == null) {
			return null;
		}
		final CAbilityHero originalHero = original.getHeroData();
		final CAbilityHero imageHero = image.getHeroData();
		if ((originalHero != null) && (imageHero != null)) {
			imageHero.setHeroLevel(simulation, image, originalHero.getHeroLevel(), false);
			imageHero.setStrengthBase(simulation, image, originalHero.getStrength().getBase());
			imageHero.setAgilityBase(simulation, image, originalHero.getAgility().getBase());
			imageHero.setIntelligenceBase(simulation, image, originalHero.getIntelligence().getBase());
			imageHero.copyLearnedSkills(simulation, image, originalHero);
		}
		image.makeIllusion(simulation, damageDealtFactor, damageTakenFactor, buffId);
		final CAbilityInventory originalInventory = original.getInventoryData();
		final CAbilityInventory imageInventory = image.getInventoryData();
		if ((originalInventory != null) && (imageInventory != null)) {
			for (int slot = 0; slot < originalInventory.getItemCapacity(); slot++) {
				final CItem item = originalInventory.getItemInSlot(slot);
				if (item != null) {
					final CItem copy = simulation.createItem(item.getTypeId(), x, y);
					if (copy != null) {
						copy.setCharges(item.getCharges());
						imageInventory.giveItem(simulation, image, copy, slot, false);
					}
				}
			}
		}
		image.setLife(simulation, image.getMaximumLife() * share(original.getLife(), original.getMaximumLife()));
		image.setMana(image.getMaximumMana() * share(original.getMana(), original.getMaximumMana()));
		image.add(simulation,
				new CBuffTimedLife(simulation.getHandleIdAllocator().createId(), buffId, duration, false));
		return image;
	}

	private static float share(final float value, final float maximum) {
		return maximum > 0 ? value / maximum : 1;
	}
}
