package com.etheller.warsmash.networking;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandCodec;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;

public class WarsmashClientSendingOrderListener implements CPlayerUnitOrderListener {
	private final WarsmashClientWriter writer;
	private final CPlayerCommandCodec.Encoder encoder = new CPlayerCommandCodec.Encoder();

	public WarsmashClientSendingOrderListener(final WarsmashClientWriter writer) {
		this.writer = writer;
	}

	@Override
	public void issueTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final boolean queue) {
		this.writer.issueTargetOrder(unitHandleId, abilityHandleId, orderId, targetHandleId, queue);
		this.writer.send();
	}

	@Override
	public void issuePointOrder(final int unitHandleId, final int abilityHandleId, final int orderId, final float x,
			final float y, final boolean queue) {
		this.writer.issuePointOrder(unitHandleId, abilityHandleId, orderId, x, y, queue);
		this.writer.send();
	}

	@Override
	public void issueDropItemAtPointOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final float x, final float y, final boolean queue) {
		this.writer.issueDropItemAtPointOrder(unitHandleId, abilityHandleId, orderId, targetHandleId, x, y, queue);
		this.writer.send();
	}

	@Override
	public void issueDropItemAtTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetItemHandleId, final int targetHeroHandleId, final boolean queue) {
		this.writer.issueDropItemAtTargetOrder(unitHandleId, abilityHandleId, orderId, targetItemHandleId,
				targetHeroHandleId, queue);
		this.writer.send();
	}

	@Override
	public void issueImmediateOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final boolean queue) {
		this.writer.issueImmediateOrder(unitHandleId, abilityHandleId, orderId, queue);
		this.writer.send();
	}

	@Override
	public void unitCancelTrainingItem(final int unitHandleId, final int cancelIndex) {
		this.writer.unitCancelTrainingItem(unitHandleId, cancelIndex);
		this.writer.send();
	}

	@Override
	public void issueGuiPlayerEvent(final int eventId) {
		this.writer.issueGuiPlayerEvent(eventId);
		this.writer.send();
	}

	private void sendCommand() {
		this.writer.issuePlayerCommand(this.encoder.take());
		this.writer.send();
	}

	@Override
	public void selectUnits(final int[] deselectedUnitHandleIds, final int[] selectedUnitHandleIds) {
		this.encoder.selectUnits(deselectedUnitHandleIds, selectedUnitHandleIds);
		sendCommand();
	}

	@Override
	public void chatMessage(final int recipient, final int recipientPlayerIndex, final String message) {
		this.encoder.chatMessage(recipient, recipientPlayerIndex, message);
		sendCommand();
	}

	@Override
	public void clickDialogButton(final int dialogHandleId, final int buttonHandleId) {
		this.encoder.clickDialogButton(dialogHandleId, buttonHandleId);
		sendCommand();
	}

	@Override
	public void setAlliance(final int otherPlayerIndex, final int allianceType, final boolean value) {
		this.encoder.setAlliance(otherPlayerIndex, allianceType, value);
		sendCommand();
	}

	@Override
	public void transferResources(final int otherPlayerIndex, final int gold, final int lumber) {
		this.encoder.transferResources(otherPlayerIndex, gold, lumber);
		sendCommand();
	}

	@Override
	public void fireGameEvent(final int eventId) {
		this.encoder.fireGameEvent(eventId);
		sendCommand();
	}

	@Override
	public void saveGame(final String saveName) {
		this.encoder.saveGame(saveName);
		sendCommand();
	}

	@Override
	public void leaveGame() {
		this.encoder.leaveGame();
		sendCommand();
	}

	@Override
	public void cheat(final String cheat) {
		this.encoder.cheat(cheat);
		sendCommand();
	}

	@Override
	public void setGamePaused(final boolean paused) {
		this.encoder.setGamePaused(paused);
		sendCommand();
	}

	@Override
	public void minimapSignal(final float x, final float y) {
		this.encoder.minimapSignal(x, y);
		sendCommand();
	}

	@Override
	public void trackableEvent(final int trackableHandleId, final boolean hit) {
		this.encoder.trackableEvent(trackableHandleId, hit);
		sendCommand();
	}

	@Override
	public void mouseEvent(final int eventId, final float x, final float y, final int button) {
		this.encoder.mouseEvent(eventId, x, y, button);
		sendCommand();
	}
}
