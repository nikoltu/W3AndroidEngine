package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderNoTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetPoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CWidgetEvent;

/**
 * The game events a unit fires: spell, order, item, hero, train, research and construction events.
 */
final class CUnitEvents {
	private CUnitEvents() {
	}

	static void fireChangeOwnerEvents(final CUnit self, final CSimulation simulation, final CPlayer previousPlayer,
			final CPlayer newPlayer) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_CHANGE_OWNER);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				final CWidgetEvent event = eventList.get(i);
				event.fire(self, CommonTriggerExecutionScope.unitChangeOwnerScope(
						JassGameEventsWar3.EVENT_UNIT_CHANGE_OWNER, event.getTrigger(), self, newPlayer,
						previousPlayer));
			}
		}
		newPlayer.fireUnitChangeOwnerEvents(self, previousPlayer);
	}

	static void fireAcquiredTargetEvents(final CUnit self, final CSimulation simulation, final CUnit target) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_ACQUIRED_TARGET);
		if (eventList != null) {
			for (final CWidgetEvent event : eventList) {
				event.fire(self, CommonTriggerExecutionScope.unitAcquiredTargetScope(
						JassGameEventsWar3.EVENT_UNIT_ACQUIRED_TARGET, event.getTrigger(), self, target,
						simulation.getPlayer(self.playerIndex)));
			}
		}
	}

	static void fireAttackedEvents(final CUnit self, final CSimulation simulation, final CUnit attacker) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_ATTACKED);
		if (eventList != null) {
			for (final CWidgetEvent event : eventList) {
				event.fire(self, CommonTriggerExecutionScope.unitAttackedScope(JassGameEventsWar3.EVENT_UNIT_ATTACKED,
						event.getTrigger(), self, attacker, simulation.getPlayer(self.playerIndex)));
			}
		}
		simulation.getPlayer(self.playerIndex).fireUnitAttackedEvents(self, attacker);
	}

	static void fireSpellEvents(final CUnit self, final CSimulation game, final JassGameEventsWar3 eventId, final CAbility ability,
			AbilityTarget target) {
		// 1.29: finish, and the endcast after it, have no target unit and target
		// point 0,0; an endcast without finish (interrupted) keeps the target.
		if (eventId == JassGameEventsWar3.EVENT_UNIT_SPELL_FINISH) {
			self.finishedSpell = ability;
			target = null;
		}
		else if (eventId == JassGameEventsWar3.EVENT_UNIT_SPELL_ENDCAST) {
			if (self.finishedSpell == ability) {
				target = null;
			}
			self.finishedSpell = null;
		}
		if (eventId == JassGameEventsWar3.EVENT_UNIT_SPELL_EFFECT) {
			game.spellEffectEvent(self, ability);
			final CUnit targetUnit = (target == null) ? null : target.visit(AbilityTargetVisitor.UNIT);
			if (targetUnit != null) {
				targetUnit.onSpellEffect(game, self);
			}
		}
		if (target == null) {
			fireSpellEventsNoTarget(self, game, eventId, ability);
		} else {
			target.visit(new AbilityTargetVisitor<Object>() {

				@Override
				public Object accept(final AbilityPointTarget target) {
					fireSpellEventsPointTarget(self, game, eventId, ability, target);
					return null;
				}

				@Override
				public Object accept(final CUnit target) {
					fireSpellEventsUnitTarget(self, game, eventId, ability, target);
					return null;
				}

				@Override
				public Object accept(final CDestructable target) {
					fireSpellEventsDestructableTarget(self, game, eventId, ability, target);
					return null;
				}

				@Override
				public Object accept(final CItem target) {
					fireSpellEventsItemTarget(self, game, eventId, ability, target);
					return null;
				}

			});
		}
	}

	static void fireSpellEventsNoTarget(final CUnit self, final CSimulation game, final JassGameEventsWar3 eventId,
			final CAbility ability) {
		final List<CWidgetEvent> eventList = self.getEventList(eventId);
		if (eventList != null) {
			for (final CWidgetEvent event : eventList) {
				event.fire(self, CommonTriggerExecutionScope.unitSpellNoTargetScope(eventId, event.getTrigger(),
						ability, self, ability.getAlias()));
			}
		}
		game.getPlayer(self.playerIndex).fireSpellEventsNoTarget(unitSpellEventToPlayerEvent(self, eventId), ability, self);
		game.fireSpellEventsNoTarget(eventId, ability, self);
	}

	static void fireSpellEventsPointTarget(final CUnit self, final CSimulation game, final JassGameEventsWar3 eventId,
			final CAbility ability, final AbilityPointTarget target) {
		final List<CWidgetEvent> eventList = self.getEventList(eventId);
		if (eventList != null) {
			for (final CWidgetEvent event : eventList) {
				event.fire(self, CommonTriggerExecutionScope.unitSpellPointScope(eventId, event.getTrigger(), ability,
						self, target, ability.getAlias()));
			}
		}
		game.getPlayer(self.playerIndex).fireSpellEventsPointTarget(unitSpellEventToPlayerEvent(self, eventId), ability, self,
				target);
		game.fireSpellEventsPointTarget(eventId, ability, self, target);
	}

	static void fireSpellEventsUnitTarget(final CUnit self, final CSimulation game, final JassGameEventsWar3 eventId,
			final CAbility ability, final CUnit target) {
		final List<CWidgetEvent> eventList = self.getEventList(eventId);
		if (eventList != null) {
			for (final CWidgetEvent event : eventList) {
				event.fire(self, CommonTriggerExecutionScope.unitSpellTargetUnitScope(eventId, event.getTrigger(),
						ability, self, target, ability.getAlias()));
			}
		}
		game.getPlayer(self.playerIndex).fireSpellEventsUnitTarget(unitSpellEventToPlayerEvent(self, eventId), ability, self,
				target);
		game.fireSpellEventsUnitTarget(eventId, ability, self, target);
	}

	static void fireSpellEventsItemTarget(final CUnit self, final CSimulation game, final JassGameEventsWar3 eventId,
			final CAbility ability, final CItem target) {
		final List<CWidgetEvent> eventList = self.getEventList(eventId);
		if (eventList != null) {
			for (final CWidgetEvent event : eventList) {
				event.fire(self, CommonTriggerExecutionScope.unitSpellTargetItemScope(eventId, event.getTrigger(),
						ability, self, target, ability.getAlias()));
			}
		}
		game.getPlayer(self.playerIndex).fireSpellEventsItemTarget(unitSpellEventToPlayerEvent(self, eventId), ability, self,
				target);
		game.fireSpellEventsItemTarget(eventId, ability, self, target);
	}

	static void fireSpellEventsDestructableTarget(final CUnit self, final CSimulation game, final JassGameEventsWar3 eventId,
			final CAbility ability, final CDestructable target) {
		final List<CWidgetEvent> eventList = self.getEventList(eventId);
		if (eventList != null) {
			for (final CWidgetEvent event : eventList) {
				event.fire(self, CommonTriggerExecutionScope.unitSpellTargetDestructableScope(eventId,
						event.getTrigger(), ability, self, target, ability.getAlias()));
			}
		}
		game.getPlayer(self.playerIndex).fireSpellEventsDestructableTarget(unitSpellEventToPlayerEvent(self, eventId),
				ability, self, target);
		game.fireSpellEventsDestructableTarget(eventId, ability, self, target);
	}

	static JassGameEventsWar3 unitSpellEventToPlayerEvent(final CUnit self, final JassGameEventsWar3 eventId) {
		switch (eventId) {
		case EVENT_UNIT_SPELL_CAST:
			return JassGameEventsWar3.EVENT_PLAYER_UNIT_SPELL_CAST;
		case EVENT_UNIT_SPELL_CHANNEL:
			return JassGameEventsWar3.EVENT_PLAYER_UNIT_SPELL_CHANNEL;
		case EVENT_UNIT_SPELL_EFFECT:
			return JassGameEventsWar3.EVENT_PLAYER_UNIT_SPELL_EFFECT;
		case EVENT_UNIT_SPELL_FINISH:
			return JassGameEventsWar3.EVENT_PLAYER_UNIT_SPELL_FINISH;
		case EVENT_UNIT_SPELL_ENDCAST:
			return JassGameEventsWar3.EVENT_PLAYER_UNIT_SPELL_ENDCAST;
		default:
			return null;
		}
	}

	/**
	 * Selection events go to the unit and to the player who selected it, not to its owner.
	 */
	static void fireSelectionEvents(final CUnit self, final CSimulation game, final CPlayer selector, final boolean selected) {
		final JassGameEventsWar3 type = selected ? JassGameEventsWar3.EVENT_UNIT_SELECTED
				: JassGameEventsWar3.EVENT_UNIT_DESELECTED;
		final List<CWidgetEvent> eventList = self.getEventList(type);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				final CWidgetEvent event = eventList.get(i);
				event.fire(self, CommonTriggerExecutionScope.simpleUnitScope(type, event.getTrigger(), self, selector));
			}
		}
		selector.fireSimpleUnitEvents(self, selected ? JassGameEventsWar3.EVENT_PLAYER_UNIT_SELECTED
				: JassGameEventsWar3.EVENT_PLAYER_UNIT_DESELECTED, game);
	}

	static void firePickUpItemEvents(final CUnit self, final CSimulation game, final CItem item) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_PICKUP_ITEM);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				CWidgetEvent event = eventList.get(i);
				event.fire(self, CommonTriggerExecutionScope.unitPickupItemScope(
						JassGameEventsWar3.EVENT_UNIT_PICKUP_ITEM, event.getTrigger(), self, item));
			}
		}
		game.getPlayer(self.playerIndex).firePickUpItemEvents(self, item, game);
	}

	static void fireUseItemEvents(final CUnit self, final CSimulation game, final CItem item) {
		fireItemEvents(self, game, item, JassGameEventsWar3.EVENT_UNIT_USE_ITEM, JassGameEventsWar3.EVENT_PLAYER_UNIT_USE_ITEM);
	}

	static void fireDropItemEvents(final CUnit self, final CSimulation game, final CItem item) {
		fireItemEvents(self, game, item, JassGameEventsWar3.EVENT_UNIT_DROP_ITEM,
				JassGameEventsWar3.EVENT_PLAYER_UNIT_DROP_ITEM);
	}

	static void fireItemEvents(final CUnit self, final CSimulation game, final CItem item, final JassGameEventsWar3 unitEvent,
			final JassGameEventsWar3 playerEvent) {
		final List<CWidgetEvent> eventList = self.getEventList(unitEvent);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				final CWidgetEvent event = eventList.get(i);
				event.fire(self,
						CommonTriggerExecutionScope.unitPickupItemScope(unitEvent, event.getTrigger(), self, item));
			}
		}
		game.getPlayer(self.playerIndex).fireItemEvents(self, item, playerEvent);
	}

	static void fireOrderEvents(final CUnit self, final CSimulation game, final COrderNoTarget order) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_ISSUED_ORDER);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				CWidgetEvent event = eventList.get(i);
				event.fire(self, CommonTriggerExecutionScope.unitOrderScope(JassGameEventsWar3.EVENT_UNIT_ISSUED_ORDER,
						event.getTrigger(), self, order.getOrderId()));
			}
		}
		game.getPlayer(self.playerIndex).fireOrderEvents(self, game, order);
	}

	static void fireOrderEvents(final CUnit self, final CSimulation game, final COrderTargetPoint order) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_ISSUED_POINT_ORDER);
		if (eventList != null) {
			final AbilityPointTarget target = order.getTarget(game);
			for (int i = eventList.size() - 1; i >= 0; i--) {
				CWidgetEvent event = eventList.get(i);
				event.fire(self,
						CommonTriggerExecutionScope.unitOrderPointScope(
								JassGameEventsWar3.EVENT_UNIT_ISSUED_POINT_ORDER, event.getTrigger(), self,
								order.getOrderId(), target.x, target.y));
			}
		}
		game.getPlayer(self.playerIndex).fireOrderEvents(self, game, order);
	}

	static void fireOrderEvents(final CUnit self, final CSimulation game, final COrderTargetWidget order) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_ISSUED_TARGET_ORDER);
		if (eventList != null) {
			final CWidget target = order.getTarget(game);
			for (int i = eventList.size() - 1; i >= 0; i--) {
				CWidgetEvent event = eventList.get(i);
				event.fire(self,
						CommonTriggerExecutionScope.unitOrderTargetScope(
								JassGameEventsWar3.EVENT_UNIT_ISSUED_TARGET_ORDER, event.getTrigger(), self,
								order.getOrderId(), target));
			}
		}
		game.getPlayer(self.playerIndex).fireOrderEvents(self, game, order);
	}

	static void fireConstructFinishEvents(final CUnit self, final CSimulation game) {
		final CUnit constructingUnit = self.production.worker;
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_CONSTRUCT_FINISH);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				CWidgetEvent event = eventList.get(i);
				event.fire(self, CommonTriggerExecutionScope.unitConstructFinishScope(
						JassGameEventsWar3.EVENT_UNIT_CONSTRUCT_FINISH, event.getTrigger(), self, constructingUnit));
			}
		}
		game.getPlayer(self.playerIndex).fireConstructFinishEvents(self, game, constructingUnit);
	}

	/**
	 * Creep experience is reduced by the hero level it is gained at: a kill that takes the hero past a level is
	 * counted at each level's rate in turn, as the original game does.
	 */
	static int creepXp(final CGameplayConstants gameplayConstants, final CAbilityHero heroData,
			final float rawXp) {
		float remaining = rawXp;
		float gained = 0;
		int level = heroData.getHeroLevel();
		int xp = heroData.getXp();
		while (remaining > 0) {
			final float factor = gameplayConstants.getHeroFactorXp(level);
			if (factor <= 0) {
				break;
			}
			final float toNextLevel = level < gameplayConstants.getMaxHeroLevel()
					? gameplayConstants.getNeedHeroXPSum(level) - xp
					: Float.MAX_VALUE;
			if ((remaining * factor) <= toNextLevel) {
				gained += remaining * factor;
				break;
			}
			gained += toNextLevel;
			xp += (int) toNextLevel;
			remaining -= toNextLevel / factor;
			level++;
		}
		return Math.round(gained);
	}

	static void fireSummonEvents(final CUnit self, final CSimulation game) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_SUMMON);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				final CWidgetEvent event = eventList.get(i);
				event.fire(self, CommonTriggerExecutionScope.unitSummonerScope(JassGameEventsWar3.EVENT_UNIT_SUMMON,
						event.getTrigger(), self, game.getPlayer(self.playerIndex)));
			}
		}
	}

	static void fireHeroLevelEvents(final CUnit self, final CSimulation game) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_HERO_LEVEL);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				final CWidgetEvent event = eventList.get(i);
				event.fire(self, CommonTriggerExecutionScope.playerHeroRevivableScope(
						JassGameEventsWar3.EVENT_UNIT_HERO_LEVEL, event.getTrigger(), self));
			}
		}
		game.getPlayer(self.playerIndex).fireHeroLevelEvents(self);
	}

	static void fireHeroSkillEvents(final CUnit self, final CSimulation game, final War3ID skill, final int skillLevel) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_HERO_SKILL);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				final CWidgetEvent event = eventList.get(i);
				event.fire(self, CommonTriggerExecutionScope.heroSkillScope(JassGameEventsWar3.EVENT_UNIT_HERO_SKILL,
						event.getTrigger(), self, skill.getValue(), skillLevel));
			}
		}
		game.getPlayer(self.playerIndex).firePlayerUnitEvents(self,
				(eventType, trigger, unit) -> CommonTriggerExecutionScope.heroSkillScope(eventType, trigger, unit,
						skill.getValue(), skillLevel),
				JassGameEventsWar3.EVENT_PLAYER_HERO_SKILL);
	}

	static void fireLoadedEvents(final CUnit self, final CSimulation game, final CUnit transport) {
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_LOADED);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				final CWidgetEvent event = eventList.get(i);
				event.fire(self, CommonTriggerExecutionScope.unitLoadedScope(JassGameEventsWar3.EVENT_UNIT_LOADED,
						event.getTrigger(), self, transport));
			}
		}
		game.getPlayer(self.playerIndex).firePlayerUnitEvents(self,
				(eventType, trigger, unit) -> CommonTriggerExecutionScope.unitLoadedScope(eventType, trigger, unit,
						transport),
				JassGameEventsWar3.EVENT_PLAYER_UNIT_LOADED);
	}

	static void fireTrainFinishEvents(final CUnit self, final CSimulation game, final CUnit trainedUnit) {
		final CPlayer player = game.getPlayer(self.playerIndex);
		player.fireTrainFinishEvents(self, game, trainedUnit);
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_TRAIN_FINISH);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				CWidgetEvent event = eventList.get(i);
				event.fire(self, CommonTriggerExecutionScope.unitTrainFinishScope(
						JassGameEventsWar3.EVENT_UNIT_TRAIN_FINISH, event.getTrigger(), self, trainedUnit, player));
			}
		}
	}

	static void fireTrainEvents(final CUnit self, final CSimulation game, final JassGameEventsWar3 unitEventType,
			final JassGameEventsWar3 playerEventType, final War3ID trainedUnitType) {
		final CPlayer player = game.getPlayer(self.playerIndex);
		player.firePlayerUnitEvents(self, (eventType, trigger, unit) -> CommonTriggerExecutionScope
				.unitTrainScope(eventType, trigger, unit, trainedUnitType, player), playerEventType);
		final List<CWidgetEvent> eventList = self.getEventList(unitEventType);
		if (eventList != null) {
			for (final CWidgetEvent event : new ArrayList<>(eventList)) {
				event.fire(self, CommonTriggerExecutionScope.unitTrainScope(unitEventType, event.getTrigger(), self,
						trainedUnitType, player));
			}
		}
	}

	static void fireResearchEvents(final CUnit self, final CSimulation game, final JassGameEventsWar3 unitEventType,
			final JassGameEventsWar3 playerEventType, final War3ID researched) {
		final CPlayer player = game.getPlayer(self.playerIndex);
		player.firePlayerUnitEvents(self, (eventType, trigger, unit) -> CommonTriggerExecutionScope
				.unitResearchFinishScope(eventType, trigger, unit, researched, player), playerEventType);
		final List<CWidgetEvent> eventList = self.getEventList(unitEventType);
		if (eventList != null) {
			for (final CWidgetEvent event : new ArrayList<>(eventList)) {
				event.fire(self, CommonTriggerExecutionScope.unitResearchFinishScope(unitEventType,
						event.getTrigger(), self, researched, player));
			}
		}
	}

	static void firePlayerThenUnitEvents(final CUnit self, final CSimulation game, final JassGameEventsWar3 unitEventType,
			final JassGameEventsWar3 playerEventType) {
		final CPlayer player = game.getPlayer(self.playerIndex);
		player.fireSimpleUnitEvents(self, playerEventType, game);
		final List<CWidgetEvent> eventList = self.getEventList(unitEventType);
		if (eventList != null) {
			for (final CWidgetEvent event : new ArrayList<>(eventList)) {
				event.fire(self, CommonTriggerExecutionScope.simpleUnitScope(unitEventType, event.getTrigger(), self,
						player));
			}
		}
	}

	static void fireResearchFinishEvents(final CUnit self, final CSimulation game, final War3ID researched) {
		game.getPlayer(self.playerIndex).fireResearchFinishEvents(self, game, researched);
		final List<CWidgetEvent> eventList = self.getEventList(JassGameEventsWar3.EVENT_UNIT_RESEARCH_FINISH);
		if (eventList != null) {
			for (int i = eventList.size() - 1; i >= 0; i--) {
				CWidgetEvent event = eventList.get(i);
				event.fire(self, CommonTriggerExecutionScope.unitResearchFinishScope(
						JassGameEventsWar3.EVENT_UNIT_RESEARCH_FINISH, event.getTrigger(), self, researched, game.getPlayer(self.playerIndex)));
			}
		}
	}
}
