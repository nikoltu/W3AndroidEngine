package com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;

/**
 * A recorded player's commands as a playback applies them. A pause only stopped the recorded game's clock, not its
 * steps; applied again it would stop the playback for good, since the resume comes in a later step.
 */
final class CReplayCommands implements CPlayerUnitOrderListener {
	/** Where the chat lines of the recorded player go, to be shown. */
	interface ChatSink {
		void chat(int recipient, int recipientPlayerIndex, String message);
	}

	private final CPlayerUnitOrderListener executor;
	private final ChatSink chatSink;

	CReplayCommands(final CPlayerUnitOrderListener executor, final ChatSink chatSink) {
		this.executor = executor;
		this.chatSink = chatSink;
	}

	@Override
	public void issueTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final boolean queue) {
		this.executor.issueTargetOrder(unitHandleId, abilityHandleId, orderId, targetHandleId, queue);
	}

	@Override
	public void issuePointOrder(final int unitHandleId, final int abilityHandleId, final int orderId, final float x,
			final float y, final boolean queue) {
		this.executor.issuePointOrder(unitHandleId, abilityHandleId, orderId, x, y, queue);
	}

	@Override
	public void issueGroupPointOrder(final int[] unitHandleIds, final int[] abilityHandleIds, final int orderId,
			final float[] xs, final float[] ys, final boolean queue) {
		this.executor.issueGroupPointOrder(unitHandleIds, abilityHandleIds, orderId, xs, ys, queue);
	}

	@Override
	public void issueDropItemAtPointOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final float x, final float y, final boolean queue) {
		this.executor.issueDropItemAtPointOrder(unitHandleId, abilityHandleId, orderId, targetHandleId, x, y, queue);
	}

	@Override
	public void issueDropItemAtTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetItemHandleId, final int targetHeroHandleId, final boolean queue) {
		this.executor.issueDropItemAtTargetOrder(unitHandleId, abilityHandleId, orderId, targetItemHandleId,
				targetHeroHandleId, queue);
	}

	@Override
	public void issueImmediateOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final boolean queue) {
		this.executor.issueImmediateOrder(unitHandleId, abilityHandleId, orderId, queue);
	}

	@Override
	public void unitCancelTrainingItem(final int unitHandleId, final int cancelIndex) {
		this.executor.unitCancelTrainingItem(unitHandleId, cancelIndex);
	}

	@Override
	public void issueGuiPlayerEvent(final int eventId) {
		this.executor.issueGuiPlayerEvent(eventId);
	}

	@Override
	public void selectUnits(final int[] deselectedUnitHandleIds, final int[] selectedUnitHandleIds) {
		this.executor.selectUnits(deselectedUnitHandleIds, selectedUnitHandleIds);
	}

	@Override
	public void chatMessage(final int recipient, final int recipientPlayerIndex, final String message) {
		this.executor.chatMessage(recipient, recipientPlayerIndex, message);
		if ((message != null) && !message.trim().isEmpty()) {
			this.chatSink.chat(recipient, recipientPlayerIndex, message);
		}
	}

	@Override
	public void clickDialogButton(final int dialogHandleId, final int buttonHandleId) {
		this.executor.clickDialogButton(dialogHandleId, buttonHandleId);
	}

	@Override
	public void setAlliance(final int otherPlayerIndex, final int allianceType, final boolean value) {
		this.executor.setAlliance(otherPlayerIndex, allianceType, value);
	}

	@Override
	public void transferResources(final int otherPlayerIndex, final int gold, final int lumber) {
		this.executor.transferResources(otherPlayerIndex, gold, lumber);
	}

	@Override
	public void fireGameEvent(final int eventId) {
		this.executor.fireGameEvent(eventId);
	}

	@Override
	public void saveGame(final String saveName) {
		this.executor.saveGame(saveName);
	}

	@Override
	public void leaveGame() {
		this.executor.leaveGame();
	}

	@Override
	public void cheat(final String cheat) {
		this.executor.cheat(cheat);
	}

	@Override
	public void setGamePaused(final boolean paused) {
	}

	@Override
	public void minimapSignal(final float x, final float y) {
		this.executor.minimapSignal(x, y);
	}

	@Override
	public void trackableEvent(final int trackableHandleId, final boolean hit) {
		this.executor.trackableEvent(trackableHandleId, hit);
	}

	@Override
	public void mouseEvent(final int eventId, final float x, final float y, final int button) {
		this.executor.mouseEvent(eventId, x, y, button);
	}
}
