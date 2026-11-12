package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CReleaseCheats;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilityNeutralBuilding;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CTrackable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorHoldPosition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CGroupMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderDropItemAtPoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderDropItemAtTargetWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderNoTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetPoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

public class CPlayerUnitOrderExecutor implements CPlayerUnitOrderListener {
	private final CSimulation game;
	private final int playerIndex;

	public CPlayerUnitOrderExecutor(final CSimulation game, final int playerIndex) {
		this.game = game;
		this.playerIndex = playerIndex;
	}

	private boolean sharedControl(final CUnit unit) {
		boolean controlShared = this.game.getPlayer(unit.getPlayerIndex()).hasAlliance(this.playerIndex,
				CAllianceType.SHARED_CONTROL);
		if (!controlShared) {
			final CAbilityNeutralBuilding neutralBuildingData = unit.getNeutralBuildingData();
			if (neutralBuildingData != null) {
				final CUnit selectedPlayerUnit = neutralBuildingData.getSelectedPlayerUnit(this.playerIndex);
				if (selectedPlayerUnit != null) {
					controlShared = true;
				}
			}
		}
		return controlShared;
	}

	@Override
	public void issueTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final boolean queue) {
		final CUnit unit = this.game.getUnit(unitHandleId);
		if (unit == null) {
			return;
		}
		if ((this.playerIndex == unit.getPlayerIndex()) || sharedControl(unit)) {
			unit.order(this.game,
					new COrderTargetWidget(this.playerIndex, abilityHandleId, orderId, targetHandleId, queue), queue);
		}
	}

	@Override
	public void issueDropItemAtPointOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final float x, final float y, final boolean queue) {
		final CUnit unit = this.game.getUnit(unitHandleId);
		if (unit == null) {
			return;
		}
		if ((this.playerIndex == unit.getPlayerIndex()) || sharedControl(unit)) {
			unit.order(this.game, new COrderDropItemAtPoint(this.playerIndex, abilityHandleId, orderId, targetHandleId,
					new AbilityPointTarget(x, y), queue), queue);
		}
	}

	@Override
	public void issueDropItemAtTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetItemHandleId, final int targetHeroHandleId, final boolean queue) {
		final CUnit unit = this.game.getUnit(unitHandleId);
		if (unit == null) {
			return;
		}
		if ((this.playerIndex == unit.getPlayerIndex()) || sharedControl(unit)) {
			unit.order(this.game, new COrderDropItemAtTargetWidget(this.playerIndex, abilityHandleId, orderId,
					targetItemHandleId, targetHeroHandleId, queue), queue);
		}
	}

	@Override
	public void issuePointOrder(final int unitHandleId, final int abilityHandleId, final int orderId, final float x,
			final float y, final boolean queue) {
		final CUnit unit = this.game.getUnit(unitHandleId);
		if (unit == null) {
			return;
		}
		if ((this.playerIndex == unit.getPlayerIndex()) || sharedControl(unit)) {
			unit.order(this.game, new COrderTargetPoint(this.playerIndex, abilityHandleId, orderId,
					new AbilityPointTarget(x, y), queue), queue);
		}
	}

	@Override
	public void issueGroupPointOrder(final int[] unitHandleIds, final int[] abilityHandleIds, final int orderId,
			final float[] xs, final float[] ys, final boolean queue) {
		final List<CUnit> walkers = new ArrayList<>();
		for (int i = 0; i < unitHandleIds.length; i++) {
			issuePointOrder(unitHandleIds[i], abilityHandleIds[i], orderId, xs[i], ys[i], queue);
			final CUnit unit = this.game.getUnit(unitHandleIds[i]);
			if (!queue && (unit != null) && CGroupMove.canTakeSlot(unit)
					&& ((this.playerIndex == unit.getPlayerIndex()) || sharedControl(unit))) {
				walkers.add(unit);
			}
		}
		CGroupMove.linkMoving(walkers);
	}

	@Override
	public void issueImmediateOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final boolean queue) {
		final CUnit unit = this.game.getUnit(unitHandleId);
		if (unit == null) {
			return;
		}
		if ((this.playerIndex == unit.getPlayerIndex()) || sharedControl(unit)) {
			if (abilityHandleId == 0) {
				if (orderId == OrderIds.stop) {
					unit.order(this.game, null, queue);
				}
				else if (orderId == OrderIds.holdposition) {
					unit.order(this.game, null, queue);
					final CBehaviorHoldPosition holdPositionBehavior = unit.getHoldPositionBehavior();
					if (holdPositionBehavior != null) {
						unit.setDefaultBehavior(holdPositionBehavior);
					}
				}
			}
			else {
				unit.order(this.game, new COrderNoTarget(this.playerIndex, abilityHandleId, orderId, queue), queue);
			}
		}
	}

	@Override
	public void unitCancelTrainingItem(final int unitHandleId, final int cancelIndex) {
		final CUnit unit = this.game.getUnit(unitHandleId);
		if (unit == null) {
			return;
		}
		if ((this.playerIndex == unit.getPlayerIndex()) || sharedControl(unit)) {
			unit.cancelBuildQueueItem(this.game, cancelIndex);
		}
	}

	@Override
	public void issueGuiPlayerEvent(final int eventId) {
		final CPlayer player = this.game.getPlayer(this.playerIndex);
		final JassGameEventsWar3 eventType = JassGameEventsWar3.getByEventId(eventId);
		if (eventType != null) {
			player.firePlayerEvents(CommonTriggerExecutionScope::guiPlayerEventTriggerScope, eventType);
		}
	}

	@Override
	public void selectUnits(final int[] deselectedUnitHandleIds, final int[] selectedUnitHandleIds) {
		this.game.selectUnits(this.playerIndex, deselectedUnitHandleIds, selectedUnitHandleIds);
	}

	@Override
	public void chatMessage(final int recipient, final int recipientPlayerIndex, final String message) {
		if ((message == null) || message.trim().isEmpty()) {
			return;
		}
		this.game.getPlayer(this.playerIndex).fireChatEvents(message);
	}

	@Override
	public void clickDialogButton(final int dialogHandleId, final int buttonHandleId) {
		this.game.getPlayerCommandHost().clickDialogButton(this.playerIndex, dialogHandleId, buttonHandleId);
	}

	@Override
	public void setAlliance(final int otherPlayerIndex, final int allianceType, final boolean value) {
		if (!isOtherPlayer(otherPlayerIndex) || (allianceType < 0)
				|| (allianceType > CAllianceType.SHARED_ADVANCED_CONTROL.ordinal())) {
			return;
		}
		final CAllianceType type = CAllianceType.VALUES[allianceType];
		final boolean visionOrControl = (type == CAllianceType.SHARED_VISION)
				|| (type == CAllianceType.SHARED_CONTROL) || (type == CAllianceType.SHARED_ADVANCED_CONTROL);
		if (!visionOrControl && this.game.isAllianceChangesLocked()) {
			return;
		}
		this.game.getPlayer(this.playerIndex).setAlliance(otherPlayerIndex, type, value);
	}

	@Override
	public void transferResources(final int otherPlayerIndex, final int gold, final int lumber) {
		if (!isOtherPlayer(otherPlayerIndex) || this.game.isResourceTradingLocked()) {
			return;
		}
		final CPlayer player = this.game.getPlayer(this.playerIndex);
		final CPlayer other = this.game.getPlayer(otherPlayerIndex);
		if (this.game.isResourceTradingAlliesOnly() && !player.hasAlliance(otherPlayerIndex, CAllianceType.PASSIVE)) {
			return;
		}
		final int goldToSend = Math.max(0, Math.min(gold, player.getGold()));
		final int lumberToSend = Math.max(0, Math.min(lumber, player.getLumber()));
		player.setGold(player.getGold() - goldToSend);
		other.setGold(other.getGold() + goldToSend);
		player.setLumber(player.getLumber() - lumberToSend);
		other.setLumber(other.getLumber() + lumberToSend);
		player.addScore(CPlayerScore.GOLD_GIVEN, goldToSend);
		player.addScore(CPlayerScore.LUMBER_GIVEN, lumberToSend);
		other.addScore(CPlayerScore.GOLD_RECEIVED, goldToSend);
		other.addScore(CPlayerScore.LUMBER_RECEIVED, lumberToSend);
	}

	private boolean isOtherPlayer(final int otherPlayerIndex) {
		return (otherPlayerIndex >= 0) && (otherPlayerIndex < this.game.getGameData().getMaxPlayers())
				&& (otherPlayerIndex != this.playerIndex);
	}

	/** Only the events the command card's menus fire come from a player. */
	@Override
	public void fireGameEvent(final int eventId) {
		final JassGameEventsWar3 eventType = JassGameEventsWar3.getByEventId(eventId);
		if ((eventType == JassGameEventsWar3.EVENT_GAME_SHOW_SKILL)
				|| (eventType == JassGameEventsWar3.EVENT_GAME_BUILD_SUBMENU)) {
			this.game.fireGameEvent(eventType);
		}
	}

	@Override
	public void saveGame(final String saveName) {
		this.game.setSaveBasicFilename(saveName);
		this.game.fireGameEvent(JassGameEventsWar3.EVENT_GAME_SAVE);
	}

	/**
	 * 1.29: leaving keeps a result already given, otherwise it is a defeat.
	 */
	@Override
	public void leaveGame() {
		final CPlayer player = this.game.getPlayer(this.playerIndex);
		if (player.getRemovedResult() == null) {
			player.setRemovedResult(CPlayerGameResult.DEFEAT);
			player.setSlotState(CPlayerSlotState.LEFT);
		}
	}

	@Override
	public void cheat(final String cheat) {
		CReleaseCheats.apply(this.game, this.playerIndex, cheat);
	}

	@Override
	public void setGamePaused(final boolean paused) {
		this.game.getPlayerCommandHost().setGamePaused(this.playerIndex, paused);
	}

	@Override
	public void minimapSignal(final float x, final float y) {
		this.game.getPlayerCommandHost().minimapSignal(this.playerIndex, x, y);
	}

	@Override
	public void trackableEvent(final int trackableHandleId, final boolean hit) {
		for (final CTrackable trackable : this.game.getTrackables()) {
			if (trackable.getHandleId() == trackableHandleId) {
				if (hit) {
					trackable.hit(this.game.getGlobalScope());
				}
				else {
					trackable.track(this.game.getGlobalScope());
				}
				return;
			}
		}
	}

	@Override
	public void mouseEvent(final int eventId, final float x, final float y, final int button) {
		final JassGameEventsWar3 eventType = JassGameEventsWar3.getByEventId(eventId);
		if ((eventType != JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN)
				&& (eventType != JassGameEventsWar3.EVENT_PLAYER_MOUSE_UP)
				&& (eventType != JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE)) {
			return;
		}
		this.game.getPlayer(this.playerIndex).firePlayerEvents(
				(type, trigger, player) -> CommonTriggerExecutionScope.playerMouseScope(type, trigger, player, x, y,
						button),
				eventType);
	}

}
