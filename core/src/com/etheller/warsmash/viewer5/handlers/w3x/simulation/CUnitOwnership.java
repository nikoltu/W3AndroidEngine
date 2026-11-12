package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.List;
import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CWidgetEvent;

/**
 * Changing the owner of a unit, and rescuing it.
 */
final class CUnitOwnership {
	private CUnitOwnership() {
	}

	/**
	 * Gives the unit to another player as SetUnitOwner, Charm, Possession and Control Magic do. Measured on 1.29
	 * (unit-change-owner): food used and food made move with the unit, the order is dropped, and the change fires
	 * EVENT_UNIT_CHANGE_OWNER and the new owner's EVENT_PLAYER_UNIT_CHANGE_OWNER at once; giving a unit to its own
	 * player fires nothing.
	 */
	static void changeOwner(final CUnit self, final CSimulation simulation, final int newPlayerIndex, final boolean changeColor) {
		final int previousPlayerIndex = self.playerIndex;
		if (newPlayerIndex == previousPlayerIndex) {
			if (changeColor) {
				simulation.changeUnitColor(self, newPlayerIndex);
			}
			return;
		}
		final CPlayer previousPlayer = simulation.getPlayer(previousPlayerIndex);
		final CPlayer newPlayer = simulation.getPlayer(newPlayerIndex);
		final boolean alive = !self.isDead();
		if (alive) {
			for (final War3ID upgradeUsed : self.unitType.getUpgradesUsed()) {
				final CUpgradeType upgradeType = simulation.getUpgradeData().getType(upgradeUsed);
				if ((upgradeType != null) && upgradeType.isTransferWithUnitOwnership()) {
					final int previousLevel = previousPlayer.getTechtreeUnlocked(upgradeUsed);
					if (previousLevel > newPlayer.getTechtreeUnlocked(upgradeUsed)) {
						newPlayer.setTechResearched(simulation, upgradeUsed, previousLevel);
					}
				}
			}
		}
		simulation.getUnitData().unapplyPlayerUpgradesToUnit(simulation, previousPlayerIndex, self.unitType, self);
		final int foodUsed = self.food.foodUsed;
		final int foodMade = self.food.foodMade;
		previousPlayer.setUnitFoodUsed(self, 0);
		previousPlayer.setUnitFoodMade(self, 0);
		final boolean hero = simulation.getPlayerHeroes(previousPlayerIndex).remove(self);
		if (alive) {
			if (self.production.constructing) {
				previousPlayer.removeTechtreeInProgress(simulation, self.unitType.getTypeId());
			}
			else {
				previousPlayer.removeTechtreeUnlocked(simulation, self.unitType.getTypeId());
				for (final War3ID upgradedFromTypeId : self.production.upgradedFromTypeIds) {
					previousPlayer.removeTechtreeUnlocked(simulation, upgradedFromTypeId);
				}
			}
		}
		self.setPlayerIndex(simulation, newPlayerIndex, changeColor);
		if (alive) {
			if (self.production.constructing) {
				newPlayer.addTechtreeInProgress(simulation, self.unitType.getTypeId());
			}
			else {
				newPlayer.addTechtreeUnlocked(simulation, self.unitType.getTypeId());
				for (final War3ID upgradedFromTypeId : self.production.upgradedFromTypeIds) {
					newPlayer.addTechtreeUnlocked(simulation, upgradedFromTypeId);
				}
			}
		}
		if (hero) {
			simulation.getPlayerHeroes(newPlayerIndex).add(self);
		}
		simulation.getUnitData().applyPlayerUpgradesToUnit(simulation, newPlayerIndex, self.unitType, self);
		newPlayer.setUnitFoodUsed(self, foodUsed);
		newPlayer.setUnitFoodMade(self, foodMade);
		if (alive) {
			// 1.29 (unit-change-owner): the unit is idle at once, GetUnitCurrentOrder reads 0 and not stop.
			self.order(simulation, (COrder) null, false);
		}
		CUnitEvents.fireChangeOwnerEvents(self, simulation, previousPlayer, newPlayer);
	}

	/**
	 * A unit of a rescuable-controlled player is rescuable by the players it has ALLIANCE_RESCUABLE towards;
	 * SetUnitRescuable turns that on or off for one unit and one player, also for units of other players.
	 */
	static boolean isRescuableBy(final CUnit self, final CSimulation simulation, final int byPlayerIndex) {
		final int bit = 1 << byPlayerIndex;
		if ((self.rescue.unrescuableByPlayers & bit) != 0) {
			return false;
		}
		if ((self.rescue.rescuableByPlayers & bit) != 0) {
			return true;
		}
		final CPlayer owner = simulation.getPlayer(self.playerIndex);
		return (owner.getController() == CMapControl.RESCUABLE)
				&& owner.hasAlliance(byPlayerIndex, CAllianceType.RESCUABLE);
	}

	/**
	 * Measured on 1.29 (unit-rescue): the first tick a ground unit of a rescuing player stands within the rescue range
	 * (between 414 and 415 apart by default, also for larger units; flying units rescue nothing) the unit fires
	 * EVENT_UNIT_RESCUED and its owner's EVENT_PLAYER_UNIT_RESCUED. The ownership change itself is Blizzard.j's
	 * (RescueUnitBJ), and a rescued unit rescues the ones near it in turn.
	 */
	static void checkRescue(final CUnit self, final CSimulation simulation) {
		if ((self.rescue.rescuableByPlayers == 0)
				&& (simulation.getPlayer(self.playerIndex).getController() != CMapControl.RESCUABLE)) {
			return;
		}
		if (self.isDead() || self.hidden || self.isRemovedFromGame()
				|| ((simulation.getGameTurnTick() - self.rescue.lastRescueTick) < CUnit.RESCUE_REPEAT_TICKS)) {
			return;
		}
		final float range = self.rescue.rescueRange;
		final float x = self.getX();
		final float y = self.getY();
		final CUnit[] rescuer = { null };
		simulation.getWorldCollision().enumUnitsInRect(new Rectangle(x - range, y - range, range * 2, range * 2),
				(enumUnit) -> {
					if ((enumUnit.playerIndex != self.playerIndex) && !enumUnit.isDead() && !enumUnit.hidden
							&& !enumUnit.isRemovedFromGame()
							&& !enumUnit.isBuilding() && !enumUnit.isUnitType(CUnitTypeJass.FLYING)
							&& (StrictMath.hypot(enumUnit.getX() - x, enumUnit.getY() - y) <= range) && isRescuableBy(self, simulation, enumUnit.playerIndex)) {
						rescuer[0] = enumUnit;
						return true;
					}
					return false;
				});
		if (rescuer[0] == null) {
			return;
		}
		self.rescue.lastRescueTick = simulation.getGameTurnTick();
		final CPlayer owner = simulation.getPlayer(self.playerIndex);
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_RESCUED);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				final CWidgetEvent event = eventList.get(i);
				event.fire(self, CommonTriggerExecutionScope.unitRescuedScope(JassGameEventsWar3.EVENT_UNIT_RESCUED,
						event.getTrigger(), self, rescuer[0], owner));
			}
		}
		owner.fireUnitRescuedEvents(self, rescuer[0]);
	}
}
