package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.List;

import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CWidgetEvent;

/**
 * Trigger events of neutral buildings and shops: a unit or an item sold to a buyer, an item pawned to a shop.
 */
public final class CShopEvents {
	private CShopEvents() {
	}

	public static void fireSellEvents(final CSimulation game, final CUnit shop, final CUnit soldUnit,
			final CUnit buyingUnit) {
		final List<CWidgetEvent> eventList = shop.getEventList(JassGameEventsWar3.EVENT_UNIT_SELL);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				final CWidgetEvent event = eventList.get(i);
				event.fire(shop, CommonTriggerExecutionScope.unitSellScope(JassGameEventsWar3.EVENT_UNIT_SELL,
						event.getTrigger(), shop, soldUnit, buyingUnit));
			}
		}
		game.getPlayer(shop.getPlayerIndex()).firePlayerUnitEvents(shop,
				(eventType, trigger, unit) -> CommonTriggerExecutionScope.unitSellScope(eventType, trigger, shop,
						soldUnit, buyingUnit),
				JassGameEventsWar3.EVENT_PLAYER_UNIT_SELL);
	}

	public static void fireSellItemEvents(final CSimulation game, final CUnit shop, final CItem soldItem,
			final CUnit buyingUnit) {
		final List<CWidgetEvent> eventList = shop.getEventList(JassGameEventsWar3.EVENT_UNIT_SELL_ITEM);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				final CWidgetEvent event = eventList.get(i);
				event.fire(shop, CommonTriggerExecutionScope.unitSellItemScope(JassGameEventsWar3.EVENT_UNIT_SELL_ITEM,
						event.getTrigger(), shop, shop, soldItem, buyingUnit));
			}
		}
		game.getPlayer(shop.getPlayerIndex()).firePlayerUnitEvents(shop,
				(eventType, trigger, unit) -> CommonTriggerExecutionScope.unitSellItemScope(eventType, trigger, shop,
						shop, soldItem, buyingUnit),
				JassGameEventsWar3.EVENT_PLAYER_UNIT_SELL_ITEM);
	}

	public static void firePawnItemEvents(final CSimulation game, final CUnit pawningUnit, final CItem pawnedItem,
			final CUnit shop) {
		final List<CWidgetEvent> eventList = pawningUnit.getEventList(JassGameEventsWar3.EVENT_UNIT_PAWN_ITEM);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				final CWidgetEvent event = eventList.get(i);
				event.fire(pawningUnit,
						CommonTriggerExecutionScope.unitSellItemScope(JassGameEventsWar3.EVENT_UNIT_PAWN_ITEM,
								event.getTrigger(), pawningUnit, pawningUnit, pawnedItem, shop));
			}
		}
		game.getPlayer(pawningUnit.getPlayerIndex()).firePlayerUnitEvents(pawningUnit,
				(eventType, trigger, unit) -> CommonTriggerExecutionScope.unitSellItemScope(eventType, trigger,
						pawningUnit, pawningUnit, pawnedItem, shop),
				JassGameEventsWar3.EVENT_PLAYER_UNIT_PAWN_ITEM);
	}
}
