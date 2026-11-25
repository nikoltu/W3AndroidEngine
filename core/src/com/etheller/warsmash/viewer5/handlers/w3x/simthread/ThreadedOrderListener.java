package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import java.util.function.Consumer;

import com.etheller.warsmash.util.InputLatencyProbe;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandCodec;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;

/**
 * The interface's order listener when the simulation runs on its own thread. A call made on the interface thread is
 * encoded as the command bytes a replay or a network game uses and queued in the {@link OrderInbox}; nothing of the
 * simulation is touched and no simulation object leaves the thread in a message.
 * <p>
 * The simulation thread calls {@link #drain} at every step boundary, which makes the same calls again on the listener
 * the interface used before (the delaying listener of the local game). That listener keeps its own rules: unit orders
 * and selections wait for the end of the next step, the other inputs act at once. Because the drain happens between
 * two steps, with the step count unchanged since the interface made the call, every command takes effect in the same
 * step as when the interface called that listener directly.
 */
public final class ThreadedOrderListener implements CPlayerUnitOrderListener {
	private final OrderInbox inbox;
	private final int playerIndex;
	private final CPlayerCommandCodec.Encoder encoder = new CPlayerCommandCodec.Encoder();

	public ThreadedOrderListener(final OrderInbox inbox, final int playerIndex) {
		this.inbox = inbox;
		this.playerIndex = playerIndex;
	}

	/**
	 * Simulation thread, between two steps (before the step's pause check, also while paused, and after a step's
	 * turn-completed work): applies every queued command on the target, oldest first. Returns how many.
	 *
	 * @param tick the step count the commands are applied at
	 */
	public int drain(final int tick, final CPlayerUnitOrderListener target) {
		return drain(tick, target, null);
	}

	/**
	 * @param other receives the messages that are not encoded commands (pause, save, speed), or null to refuse them
	 */
	public int drain(final int tick, final CPlayerUnitOrderListener target, final Consumer<OrderMessage> other) {
		return this.inbox.drain(tick, message -> {
			if (message.kind == OrderMessage.COMMAND) {
				CPlayerCommandCodec.decode(message.payload, 0, message.payloadLength, target);
			}
			else if (other != null) {
				other.accept(message);
			}
			else {
				throw new IllegalStateException("No handler for order message kind " + message.kind);
			}
		});
	}

	private void submit() {
		final byte[] command = this.encoder.take();
		this.inbox.submitCommand(this.playerIndex, command, 0, command.length);
	}

	@Override
	public synchronized void issueTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final boolean queue) {
		if (InputLatencyProbe.ENABLED) {
			InputLatencyProbe.submitted(unitHandleId);
		}
		this.encoder.issueTargetOrder(unitHandleId, abilityHandleId, orderId, targetHandleId, queue);
		submit();
	}

	@Override
	public synchronized void issuePointOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final float x, final float y, final boolean queue) {
		if (InputLatencyProbe.ENABLED) {
			InputLatencyProbe.submitted(unitHandleId);
		}
		this.encoder.issuePointOrder(unitHandleId, abilityHandleId, orderId, x, y, queue);
		submit();
	}

	@Override
	public synchronized void issueGroupPointOrder(final int[] unitHandleIds, final int[] abilityHandleIds,
			final int orderId, final float[] xs, final float[] ys, final boolean queue) {
		if (InputLatencyProbe.ENABLED) {
			InputLatencyProbe.submitted(unitHandleIds[0]);
		}
		this.encoder.issueGroupPointOrder(unitHandleIds, abilityHandleIds, orderId, xs, ys, queue);
		submit();
	}

	@Override
	public synchronized void issueDropItemAtPointOrder(final int unitHandleId, final int abilityHandleId,
			final int orderId, final int targetHandleId, final float x, final float y, final boolean queue) {
		this.encoder.issueDropItemAtPointOrder(unitHandleId, abilityHandleId, orderId, targetHandleId, x, y, queue);
		submit();
	}

	@Override
	public synchronized void issueDropItemAtTargetOrder(final int unitHandleId, final int abilityHandleId,
			final int orderId, final int targetItemHandleId, final int targetHeroHandleId, final boolean queue) {
		this.encoder.issueDropItemAtTargetOrder(unitHandleId, abilityHandleId, orderId, targetItemHandleId,
				targetHeroHandleId, queue);
		submit();
	}

	@Override
	public synchronized void issueImmediateOrder(final int unitHandleId, final int abilityHandleId,
			final int orderId, final boolean queue) {
		if (InputLatencyProbe.ENABLED) {
			InputLatencyProbe.submitted(unitHandleId);
		}
		this.encoder.issueImmediateOrder(unitHandleId, abilityHandleId, orderId, queue);
		submit();
	}

	@Override
	public synchronized void unitCancelTrainingItem(final int unitHandleId, final int cancelIndex) {
		this.encoder.unitCancelTrainingItem(unitHandleId, cancelIndex);
		submit();
	}

	@Override
	public synchronized void issueGuiPlayerEvent(final int eventId) {
		this.encoder.issueGuiPlayerEvent(eventId);
		submit();
	}

	@Override
	public synchronized void selectUnits(final int[] deselectedUnitHandleIds, final int[] selectedUnitHandleIds) {
		this.encoder.selectUnits(deselectedUnitHandleIds, selectedUnitHandleIds);
		submit();
	}

	@Override
	public synchronized void chatMessage(final int recipient, final int recipientPlayerIndex, final String message) {
		this.encoder.chatMessage(recipient, recipientPlayerIndex, message);
		submit();
	}

	@Override
	public synchronized void clickDialogButton(final int dialogHandleId, final int buttonHandleId) {
		this.encoder.clickDialogButton(dialogHandleId, buttonHandleId);
		submit();
	}

	@Override
	public synchronized void setAlliance(final int otherPlayerIndex, final int allianceType, final boolean value) {
		this.encoder.setAlliance(otherPlayerIndex, allianceType, value);
		submit();
	}

	@Override
	public synchronized void transferResources(final int otherPlayerIndex, final int gold, final int lumber) {
		this.encoder.transferResources(otherPlayerIndex, gold, lumber);
		submit();
	}

	@Override
	public synchronized void fireGameEvent(final int eventId) {
		this.encoder.fireGameEvent(eventId);
		submit();
	}

	@Override
	public synchronized void saveGame(final String saveName) {
		this.encoder.saveGame(saveName);
		submit();
	}

	@Override
	public synchronized void leaveGame() {
		this.encoder.leaveGame();
		submit();
	}

	@Override
	public synchronized void cheat(final String cheat) {
		this.encoder.cheat(cheat);
		submit();
	}

	@Override
	public synchronized void setGamePaused(final boolean paused) {
		this.encoder.setGamePaused(paused);
		submit();
	}

	@Override
	public synchronized void minimapSignal(final float x, final float y) {
		this.encoder.minimapSignal(x, y);
		submit();
	}

	@Override
	public synchronized void trackableEvent(final int trackableHandleId, final boolean hit) {
		this.encoder.trackableEvent(trackableHandleId, hit);
		submit();
	}

	@Override
	public synchronized void mouseEvent(final int eventId, final float x, final float y, final int button) {
		this.encoder.mouseEvent(eventId, x, y, button);
		submit();
	}

	@Override
	public synchronized void syncData(final String prefix, final String data) {
		this.encoder.syncData(prefix, data);
		submit();
	}

	@Override
	public synchronized void keyEvent(final int key, final int metaKey, final boolean down) {
		this.encoder.keyEvent(key, metaKey, down);
		submit();
	}

	@Override
	public synchronized void frameEvent(final int frameId, final int eventType, final float value,
			final String text) {
		this.encoder.frameEvent(frameId, eventType, value, text);
		submit();
	}
}
