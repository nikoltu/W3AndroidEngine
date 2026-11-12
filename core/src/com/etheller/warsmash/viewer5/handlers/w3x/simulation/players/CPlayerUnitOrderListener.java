package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

public interface CPlayerUnitOrderListener {
	void issueTargetOrder(int unitHandleId, int abilityHandleId, int orderId, int targetHandleId, boolean queue);

	void issuePointOrder(int unitHandleId, int abilityHandleId, int orderId, float x, float y, boolean queue);

	/**
	 * One point order for several units, each already sent to its own formation slot. Kept as separate orders where
	 * the group cannot travel as one (network), so the units only lose walking at the group's speed.
	 */
	default void issueGroupPointOrder(final int[] unitHandleIds, final int[] abilityHandleIds, final int orderId,
			final float[] xs, final float[] ys, final boolean queue) {
		for (int i = 0; i < unitHandleIds.length; i++) {
			issuePointOrder(unitHandleIds[i], abilityHandleIds[i], orderId, xs[i], ys[i], queue);
		}
	}

	void issueDropItemAtPointOrder(int unitHandleId, int abilityHandleId, int orderId, int targetHandleId, float x,
			float y, final boolean queue);

	void issueDropItemAtTargetOrder(int unitHandleId, int abilityHandleId, int orderId, int targetItemHandleId,
			int targetHeroHandleId, final boolean queue);

	void issueImmediateOrder(int unitHandleId, int abilityHandleId, int orderId, boolean queue);

	void unitCancelTrainingItem(int unitHandleId, int cancelIndex);

	void issueGuiPlayerEvent(int eventId);

	/**
	 * Every other player input that changes the game goes through here too, so a network game or a replay can apply
	 * it in the same step on every machine.
	 */
	void selectUnits(int[] deselectedUnitHandleIds, int[] selectedUnitHandleIds);

	void chatMessage(int recipient, int recipientPlayerIndex, String message);

	void clickDialogButton(int dialogHandleId, int buttonHandleId);

	void setAlliance(int otherPlayerIndex, int allianceType, boolean value);

	void transferResources(int otherPlayerIndex, int gold, int lumber);

	void fireGameEvent(int eventId);

	void saveGame(String saveName);

	void leaveGame();

	void cheat(String cheat);

	void setGamePaused(boolean paused);

	void minimapSignal(float x, float y);

	/** The player's pointer came onto a trackable (track) or clicked it (hit). */
	void trackableEvent(int trackableHandleId, boolean hit);

	/**
	 * The player's mouse on the game world: a button pressed or released, or the pointer moved (button 0), with the
	 * point of the ground under it. Sent only while a trigger listens for that player's mouse event.
	 */
	void mouseEvent(int eventId, float x, float y, int button);
}
