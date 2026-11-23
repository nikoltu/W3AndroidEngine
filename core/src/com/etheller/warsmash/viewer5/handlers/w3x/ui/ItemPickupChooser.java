package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CPrimaryAttribute;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.impl.CAbilityTypeItemStatBonusLevelData;

/**
 * Picks, for a smart order on a ground item given to a group, the one unit that goes for it. A hero whose primary
 * attribute the item raises goes for it; otherwise the nearest hero with room; otherwise the nearest other unit with
 * room. When nobody has room, the nearest unit with an inventory goes, so the group shows one error and not one each.
 * The rest of the group only walks to the item.
 */
public final class ItemPickupChooser {
	private ItemPickupChooser() {
	}

	/** A selected unit that can take the item: whether it is a hero, its primary attribute, and its squared distance. */
	public static final class Candidate {
		final boolean hero;
		final CPrimaryAttribute primaryAttribute;
		final boolean hasRoom;
		final double distanceSquared;

		public Candidate(final boolean hero, final CPrimaryAttribute primaryAttribute, final boolean hasRoom,
				final double distanceSquared) {
			this.hero = hero;
			this.primaryAttribute = primaryAttribute;
			this.hasRoom = hasRoom;
			this.distanceSquared = distanceSquared;
		}
	}

	/** Strength, agility and intelligence the abilities of the item give, from their data fields. */
	public static int[] itemStats(final CSimulation simulation, final CItem item) {
		final int[] stats = new int[3];
		for (final War3ID abilityId : item.getAbilityIds()) {
			final CAbilityType<?> type = simulation.getAbilityData().getAbilityType(abilityId);
			final CAbilityTypeItemStatBonusLevelData data = type == null ? null : type.getItemStatBonus();
			if (data != null) {
				stats[0] += data.getStrength();
				stats[1] += data.getAgility();
				stats[2] += data.getIntelligence();
			}
		}
		return stats;
	}

	private static int bonusFor(final int[] itemStats, final CPrimaryAttribute attribute) {
		if (attribute == null) {
			return 0;
		}
		switch (attribute) {
		case AGILITY:
			return itemStats[1];
		case INTELLIGENCE:
			return itemStats[2];
		default:
			return itemStats[0];
		}
	}

	/** Index into the candidates of the unit that takes the item, or -1 when there is no candidate. */
	public static int choose(final int[] itemStats, final List<Candidate> candidates) {
		final int taker = chooseWithRoom(itemStats, candidates);
		if (taker != -1) {
			return taker;
		}
		int nearest = -1;
		for (int i = 0; i < candidates.size(); i++) {
			if ((nearest == -1) || (candidates.get(i).distanceSquared < candidates.get(nearest).distanceSquared)) {
				nearest = i;
			}
		}
		return nearest;
	}

	private static int chooseWithRoom(final int[] itemStats, final List<Candidate> candidates) {
		int best = -1;
		int bestTier = -1;
		int bestBonus = 0;
		for (int i = 0; i < candidates.size(); i++) {
			final Candidate candidate = candidates.get(i);
			if (!candidate.hasRoom) {
				continue;
			}
			final int bonus = candidate.hero ? bonusFor(itemStats, candidate.primaryAttribute) : 0;
			final int tier = candidate.hero ? (bonus > 0 ? 2 : 1) : 0;
			final boolean better;
			if (best == -1) {
				better = true;
			}
			else if (tier != bestTier) {
				better = tier > bestTier;
			}
			else if (bonus != bestBonus) {
				better = bonus > bestBonus;
			}
			else {
				better = candidate.distanceSquared < candidates.get(best).distanceSquared;
			}
			if (better) {
				best = i;
				bestTier = tier;
				bestBonus = bonus;
			}
		}
		return best;
	}
}
