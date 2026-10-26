package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUpgradeType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

public final class CUndeadUnitAbilities {
	private CUndeadUnitAbilities() {
	}

	public static <T extends CBuff> T findBuff(final CUnit unit, final Class<T> buffClass, final War3ID alias) {
		for (final CAbility ability : unit.getAbilities()) {
			if (buffClass.isInstance(ability) && alias.equals(ability.getAlias())) {
				return buffClass.cast(ability);
			}
		}
		return null;
	}

	public static boolean hasBuff(final CUnit unit, final War3ID alias) {
		return findBuff(unit, CBuff.class, alias) != null;
	}

	/**
	 * Gives a unit to another player the way Charm and Possession do: researches that travel with the unit, its
	 * food and its player upgrades move to the new owner.
	 */
	public static void transferOwnership(final CSimulation simulation, final CUnit targetUnit,
			final int newPlayerIndex) {
		final List<War3ID> targetUpgradesUsed = targetUnit.getUnitType().getUpgradesUsed();
		final CPlayer targetPlayer = simulation.getPlayer(targetUnit.getPlayerIndex());
		final CPlayer newPlayer = simulation.getPlayer(newPlayerIndex);
		for (final War3ID targetUpgradeUsed : targetUpgradesUsed) {
			final CUpgradeType upgradeType = simulation.getUpgradeData().getType(targetUpgradeUsed);
			if ((upgradeType != null) && upgradeType.isTransferWithUnitOwnership()) {
				final int targetPlayerTechUnlocked = targetPlayer.getTechtreeUnlocked(targetUpgradeUsed);
				final int newPlayerTechUnlocked = newPlayer.getTechtreeUnlocked(targetUpgradeUsed);
				if (targetPlayerTechUnlocked > newPlayerTechUnlocked) {
					newPlayer.setTechResearched(simulation, targetUpgradeUsed, targetPlayerTechUnlocked);
				}
			}
		}
		simulation.getUnitData().unapplyPlayerUpgradesToUnit(simulation, targetUnit.getPlayerIndex(),
				targetUnit.getUnitType(), targetUnit);
		final int oldFoodUsed = targetUnit.getFoodUsed();
		targetPlayer.setUnitFoodUsed(targetUnit, 0);
		targetPlayer.removeTechtreeUnlocked(simulation, targetUnit.getTypeId());
		targetUnit.setPlayerIndex(simulation, newPlayerIndex, true);
		newPlayer.addTechtreeUnlocked(simulation, targetUnit.getTypeId());
		simulation.getUnitData().applyPlayerUpgradesToUnit(simulation, targetUnit.getPlayerIndex(),
				targetUnit.getUnitType(), targetUnit);
		newPlayer.setUnitFoodUsed(targetUnit, oldFoodUsed);
		targetUnit.order(simulation, OrderIds.stop, null);
	}
}
