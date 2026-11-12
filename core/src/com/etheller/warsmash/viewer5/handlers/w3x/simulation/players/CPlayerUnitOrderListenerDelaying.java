package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import java.util.ArrayList;
import java.util.List;

public class CPlayerUnitOrderListenerDelaying implements CPlayerUnitOrderListener {
	private final CPlayerUnitOrderListener delegate;
	private List<Runnable> actions = new ArrayList<>();
	private List<Runnable> publishing = new ArrayList<>();

	public CPlayerUnitOrderListenerDelaying(final CPlayerUnitOrderListener delegate) {
		this.delegate = delegate;
	}

	@Override
	public void issueTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final boolean queue) {
		this.actions.add(() -> {
			this.delegate.issueTargetOrder(unitHandleId, abilityHandleId, orderId, targetHandleId, queue);
		});
	}

	@Override
	public void issuePointOrder(final int unitHandleId, final int abilityHandleId, final int orderId, final float x,
			final float y, final boolean queue) {
		this.actions.add(() -> {
			this.delegate.issuePointOrder(unitHandleId, abilityHandleId, orderId, x, y, queue);
		});
	}

	@Override
	public void issueGroupPointOrder(final int[] unitHandleIds, final int[] abilityHandleIds, final int orderId,
			final float[] xs, final float[] ys, final boolean queue) {
		this.actions.add(() -> {
			this.delegate.issueGroupPointOrder(unitHandleIds, abilityHandleIds, orderId, xs, ys, queue);
		});
	}

	@Override
	public void issueDropItemAtPointOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetHandleId, final float x, final float y, final boolean queue) {
		this.actions.add(() -> {
			this.delegate.issueDropItemAtPointOrder(unitHandleId, abilityHandleId, orderId, targetHandleId, x, y,
					queue);
		});
	}

	@Override
	public void issueDropItemAtTargetOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final int targetItemHandleId, final int targetHeroHandleId, final boolean queue) {
		this.actions.add(() -> {
			this.delegate.issueDropItemAtTargetOrder(unitHandleId, abilityHandleId, orderId, targetItemHandleId,
					targetHeroHandleId, queue);
		});
	}

	@Override
	public void issueImmediateOrder(final int unitHandleId, final int abilityHandleId, final int orderId,
			final boolean queue) {
		this.actions.add(() -> {
			this.delegate.issueImmediateOrder(unitHandleId, abilityHandleId, orderId, queue);
		});
	}

	@Override
	public void unitCancelTrainingItem(final int unitHandleId, final int cancelIndex) {
		this.actions.add(() -> {
			this.delegate.unitCancelTrainingItem(unitHandleId, cancelIndex);
		});
	}

	@Override
	public void issueGuiPlayerEvent(final int eventId) {
		this.actions.add(() -> {
			this.delegate.issueGuiPlayerEvent(eventId);
		});
	}

	// Unit orders and selections wait for the end of the step, as they do in a network game (measured on 1.29: a
	// script's SelectUnit reaches GroupEnumUnitsSelected and the selection events only after the step). The other
	// inputs act at once, as they always did here: they come between two steps either way, and a dialog button or a
	// resume must still work while the game is paused and no step ends.
	@Override
	public void selectUnits(final int[] deselectedUnitHandleIds, final int[] selectedUnitHandleIds) {
		this.actions.add(() -> {
			this.delegate.selectUnits(deselectedUnitHandleIds, selectedUnitHandleIds);
		});
	}

	@Override
	public void chatMessage(final int recipient, final int recipientPlayerIndex, final String message) {
		this.delegate.chatMessage(recipient, recipientPlayerIndex, message);
	}

	@Override
	public void clickDialogButton(final int dialogHandleId, final int buttonHandleId) {
		this.delegate.clickDialogButton(dialogHandleId, buttonHandleId);
	}

	@Override
	public void setAlliance(final int otherPlayerIndex, final int allianceType, final boolean value) {
		this.delegate.setAlliance(otherPlayerIndex, allianceType, value);
	}

	@Override
	public void transferResources(final int otherPlayerIndex, final int gold, final int lumber) {
		this.delegate.transferResources(otherPlayerIndex, gold, lumber);
	}

	@Override
	public void fireGameEvent(final int eventId) {
		this.delegate.fireGameEvent(eventId);
	}

	@Override
	public void saveGame(final String saveName) {
		this.delegate.saveGame(saveName);
	}

	@Override
	public void leaveGame() {
		this.delegate.leaveGame();
	}

	@Override
	public void cheat(final String cheat) {
		this.delegate.cheat(cheat);
	}

	@Override
	public void setGamePaused(final boolean paused) {
		this.delegate.setGamePaused(paused);
	}

	@Override
	public void minimapSignal(final float x, final float y) {
		this.delegate.minimapSignal(x, y);
	}

	@Override
	public void trackableEvent(final int trackableHandleId, final boolean hit) {
		this.delegate.trackableEvent(trackableHandleId, hit);
	}

	// A click that also orders units keeps its place among the orders of the step.
	@Override
	public void mouseEvent(final int eventId, final float x, final float y, final int button) {
		this.actions.add(() -> {
			this.delegate.mouseEvent(eventId, x, y, button);
		});
	}

	// An action can make the script issue more of them (skipping a cinematic selects units); those are inputs of the
	// next step, as any other input given between two steps, so they wait for the next publish.
	public void publishDelayedActions() {
		final List<Runnable> batch = this.actions;
		this.actions = this.publishing;
		this.publishing = batch;
		try {
			for (final Runnable action : batch) {
				action.run();
			}
		}
		finally {
			batch.clear();
		}
	}

}
