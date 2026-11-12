package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;

/**
 * The last stop of a player's commands before the game: each one is written to the game's command log with the
 * current step, and what is applied is the written command itself, so a replay of the log does exactly the same.
 */
public class CPlayerCommandRecorder implements CPlayerUnitOrderListener {
	private final CSimulation game;
	private final int playerIndex;
	private final CPlayerUnitOrderListener executor;
	private final CPlayerCommandCodec.Encoder encoder = new CPlayerCommandCodec.Encoder();

	public CPlayerCommandRecorder(final CSimulation game, final int playerIndex,
			final CPlayerUnitOrderListener executor) {
		this.game = game;
		this.playerIndex = playerIndex;
		this.executor = executor;
	}

	public int getPlayerIndex() {
		return this.playerIndex;
	}

	private void apply() {
		final byte[] command = this.encoder.take();
		this.game.getPlayerCommandLog().add(this.game.getGameTurnTick(), this.playerIndex, command);
		CPlayerCommandCodec.decode(command, this.executor);
	}

	@Override
	public void issueTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final boolean queue) {
		this.encoder.issueTargetOrder(unitHandleId, abilityHandleId, orderId, targetHandleId, queue);
		apply();
	}

	@Override
	public void issuePointOrder(final int unitHandleId, final int abilityHandleId, final int orderId, final float x,
			final float y, final boolean queue) {
		this.encoder.issuePointOrder(unitHandleId, abilityHandleId, orderId, x, y, queue);
		apply();
	}

	@Override
	public void issueGroupPointOrder(final int[] unitHandleIds, final int[] abilityHandleIds, final int orderId,
			final float[] xs, final float[] ys, final boolean queue) {
		this.encoder.issueGroupPointOrder(unitHandleIds, abilityHandleIds, orderId, xs, ys, queue);
		apply();
	}

	@Override
	public void issueDropItemAtPointOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final float x, final float y, final boolean queue) {
		this.encoder.issueDropItemAtPointOrder(unitHandleId, abilityHandleId, orderId, targetHandleId, x, y, queue);
		apply();
	}

	@Override
	public void issueDropItemAtTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetItemHandleId, final int targetHeroHandleId, final boolean queue) {
		this.encoder.issueDropItemAtTargetOrder(unitHandleId, abilityHandleId, orderId, targetItemHandleId,
				targetHeroHandleId, queue);
		apply();
	}

	@Override
	public void issueImmediateOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final boolean queue) {
		this.encoder.issueImmediateOrder(unitHandleId, abilityHandleId, orderId, queue);
		apply();
	}

	@Override
	public void unitCancelTrainingItem(final int unitHandleId, final int cancelIndex) {
		this.encoder.unitCancelTrainingItem(unitHandleId, cancelIndex);
		apply();
	}

	@Override
	public void issueGuiPlayerEvent(final int eventId) {
		this.encoder.issueGuiPlayerEvent(eventId);
		apply();
	}

	@Override
	public void selectUnits(final int[] deselectedUnitHandleIds, final int[] selectedUnitHandleIds) {
		this.encoder.selectUnits(deselectedUnitHandleIds, selectedUnitHandleIds);
		apply();
	}

	@Override
	public void chatMessage(final int recipient, final int recipientPlayerIndex, final String message) {
		this.encoder.chatMessage(recipient, recipientPlayerIndex, message);
		apply();
	}

	@Override
	public void clickDialogButton(final int dialogHandleId, final int buttonHandleId) {
		this.encoder.clickDialogButton(dialogHandleId, buttonHandleId);
		apply();
	}

	@Override
	public void setAlliance(final int otherPlayerIndex, final int allianceType, final boolean value) {
		this.encoder.setAlliance(otherPlayerIndex, allianceType, value);
		apply();
	}

	@Override
	public void transferResources(final int otherPlayerIndex, final int gold, final int lumber) {
		this.encoder.transferResources(otherPlayerIndex, gold, lumber);
		apply();
	}

	@Override
	public void fireGameEvent(final int eventId) {
		this.encoder.fireGameEvent(eventId);
		apply();
	}

	@Override
	public void saveGame(final String saveName) {
		this.encoder.saveGame(saveName);
		apply();
	}

	@Override
	public void leaveGame() {
		this.encoder.leaveGame();
		apply();
	}

	@Override
	public void cheat(final String cheat) {
		this.encoder.cheat(cheat);
		apply();
	}

	@Override
	public void setGamePaused(final boolean paused) {
		this.encoder.setGamePaused(paused);
		apply();
	}

	@Override
	public void minimapSignal(final float x, final float y) {
		this.encoder.minimapSignal(x, y);
		apply();
	}

	@Override
	public void trackableEvent(final int trackableHandleId, final boolean hit) {
		this.encoder.trackableEvent(trackableHandleId, hit);
		apply();
	}

	@Override
	public void mouseEvent(final int eventId, final float x, final float y, final int button) {
		this.encoder.mouseEvent(eventId, x, y, button);
		apply();
	}
}
