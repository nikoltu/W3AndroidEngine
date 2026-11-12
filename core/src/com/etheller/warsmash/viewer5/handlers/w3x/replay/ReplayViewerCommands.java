package com.etheller.warsmash.viewer5.handlers.w3x.replay;

import java.util.function.Supplier;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandHost;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;

/** See {@link ReplayPlayback#viewerCommands(int)}. */
final class ReplayViewerCommands implements CPlayerUnitOrderListener {
	private final Supplier<CPlayerCommandHost> host;
	private final int localPlayerIndex;

	ReplayViewerCommands(final Supplier<CPlayerCommandHost> host, final int localPlayerIndex) {
		this.host = host;
		this.localPlayerIndex = localPlayerIndex;
	}

	@Override
	public void issueTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final boolean queue) {
	}

	@Override
	public void issuePointOrder(final int unitHandleId, final int abilityHandleId, final int orderId, final float x,
			final float y, final boolean queue) {
	}

	@Override
	public void issueGroupPointOrder(final int[] unitHandleIds, final int[] abilityHandleIds, final int orderId,
			final float[] xs, final float[] ys, final boolean queue) {
	}

	@Override
	public void issueDropItemAtPointOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final float x, final float y, final boolean queue) {
	}

	@Override
	public void issueDropItemAtTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetItemHandleId, final int targetHeroHandleId, final boolean queue) {
	}

	@Override
	public void issueImmediateOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final boolean queue) {
	}

	@Override
	public void unitCancelTrainingItem(final int unitHandleId, final int cancelIndex) {
	}

	@Override
	public void issueGuiPlayerEvent(final int eventId) {
	}

	@Override
	public void selectUnits(final int[] deselectedUnitHandleIds, final int[] selectedUnitHandleIds) {
	}

	@Override
	public void chatMessage(final int recipient, final int recipientPlayerIndex, final String message) {
	}

	@Override
	public void clickDialogButton(final int dialogHandleId, final int buttonHandleId) {
	}

	@Override
	public void setAlliance(final int otherPlayerIndex, final int allianceType, final boolean value) {
	}

	@Override
	public void transferResources(final int otherPlayerIndex, final int gold, final int lumber) {
	}

	@Override
	public void fireGameEvent(final int eventId) {
	}

	@Override
	public void saveGame(final String saveName) {
	}

	@Override
	public void leaveGame() {
	}

	@Override
	public void cheat(final String cheat) {
	}

	@Override
	public void setGamePaused(final boolean paused) {
		this.host.get().setGamePaused(this.localPlayerIndex, paused);
	}

	@Override
	public void minimapSignal(final float x, final float y) {
	}

	@Override
	public void trackableEvent(final int trackableHandleId, final boolean hit) {
	}

	@Override
	public void mouseEvent(final int eventId, final float x, final float y, final int button) {
	}
}
