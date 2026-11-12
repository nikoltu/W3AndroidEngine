package com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign;

import com.badlogic.gdx.files.FileHandle;

/**
 * What a game leaves to the one after it, read as it ends: the map ChangeLevel, a restart or a loaded save names, the
 * computer slots a restart keeps, and whether the menu opens on the campaign screen. Each game has its own, so a game
 * left another way passes nothing on.
 */
public final class CGameExit {
	private String nextLevel;
	private String nextComputerSlots;
	private boolean campaignSelectScreenForced;
	private FileHandle savedGame;
	private boolean savedGameLoadedByScript;
	private FileHandle nextReplay;
	private int nextReplayStartTick;
	private boolean nextReplayForced;

	public void setNextLevel(final String levelFile, final String computerSlots) {
		this.nextLevel = levelFile;
		this.nextComputerSlots = computerSlots;
	}

	public boolean hasNextLevel() {
		return this.nextLevel != null;
	}

	public String getNextLevel() {
		return this.nextLevel;
	}

	/** The computer players' slots, comma separated, or null for the map's own. */
	public String getNextComputerSlots() {
		return this.nextComputerSlots;
	}

	/**
	 * The next level is this save's map with the save put in place.
	 *
	 * @param byScript whether the map's own LoadGame asked for it, not the player
	 */
	public void setSavedGame(final FileHandle savedGame, final boolean byScript) {
		this.savedGame = savedGame;
		this.savedGameLoadedByScript = byScript;
	}

	/** @return the save the next level loads, or null to start its map */
	public FileHandle getSavedGame() {
		return this.savedGame;
	}

	public boolean isSavedGameLoadedByScript() {
		return this.savedGameLoadedByScript;
	}

	/** A replay's Restart: the same replay is played again from its start. */
	public void setNextReplay(final FileHandle replay, final int startTick, final boolean forced) {
		this.nextReplay = replay;
		this.nextReplayStartTick = startTick;
		this.nextReplayForced = forced;
	}

	public FileHandle getNextReplay() {
		return this.nextReplay;
	}

	/** The step the next replay plays on to before it is shown: a seek back restarts the replay there. */
	public int getNextReplayStartTick() {
		return this.nextReplayStartTick;
	}

	/** Whether the next replay is played although this engine would refuse it. */
	public boolean isNextReplayForced() {
		return this.nextReplayForced;
	}

	public void forceCampaignSelectScreen() {
		this.campaignSelectScreenForced = true;
	}

	public boolean isCampaignSelectScreenForced() {
		return this.campaignSelectScreenForced;
	}
}
