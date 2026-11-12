package com.etheller.warsmash.viewer5.handlers.w3x.replay;

import java.io.IOException;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;

import com.etheller.warsmash.networking.GameTurnManager;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.SimulationChecksum;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCache;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplay;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay.CReplayPlayer;

/**
 * A replay played as a game: the game makes its steps through this turn manager, which applies the recorded commands
 * before each step and compares the checksums after it, and stops the game at the replay's end.
 */
public final class ReplayPlayback implements GameTurnManager, ReplaySession {
	private final CReplayPlayer player;
	private final CReplay replay;
	private final FileHandle file;
	private final War3MapViewer viewer;
	private boolean forced;
	private boolean ended;
	private boolean closed;

	/**
	 * @param startTick the step the playback plays on to without drawing before it shows the game, or 0 to start at the
	 *                  beginning (a seek back restarts the replay this way)
	 */
	public ReplayPlayback(final CReplay replay, final FileHandle file, final War3MapViewer viewer,
			final int startTick) throws IOException {
		this.viewer = viewer;
		this.replay = replay;
		this.file = file;
		this.player = new CReplayPlayer(replay, viewer.simulation, SimulationChecksum::compute);
		CGameCache.restoreSnapshot(replay.getHeader().getGameCaches());
		CGameCache.setDisk(this.player.recordedDisk());
		viewer.setReplaySession(this);
		Log.warn("Replay: playing " + replay.getHeader().getMapPath() + " to step " + replay.getLastTick()
				+ ", " + replay.getCommands().size() + " commands");
		final int skipTo = startTick > 0 ? startTick : Integer.getInteger(GameReplays.SKIP_TO_PROPERTY, -1);
		if (skipTo > 0) {
			skipTo(skipTo);
		}
	}

	/** Plays on to the step without drawing the game (see {@link War3MapViewer#fastForwardTo}). */
	public void skipTo(final int tick) {
		skipTo(tick, null);
	}

	/** @param whenReached run once the game is at the step, or where the replay ends before it */
	public void skipTo(final int tick, final Runnable whenReached) {
		this.viewer.fastForwardTo(tick, (steps, nanos) -> {
			final CSimulation game = this.viewer.simulation;
			final double seconds = nanos / 1e9;
			Log.warn(String.format(Locale.ROOT,
					"Replay: skipped to step %d, %d steps in %.2f s (%.0f steps/s), checksum %08x",
					game.getGameTurnTick(), steps, seconds, steps / Math.max(seconds, 1e-9),
					SimulationChecksum.compute(game)));
			if (whenReached != null) {
				whenReached.run();
			}
		});
	}

	/**
	 * What the viewer's own interface sends: nothing reaches the game, which only gets the recorded commands. A
	 * script's SelectUnit or ForceUIKey goes through the interface as a command again; the recorded one already has
	 * it. Only the viewer's pause stays, for the menu.
	 */
	/** Called with each chat line the recorded players sent, as the playback applies it. */
	public interface ChatListener {
		void chat(int senderIndex, int recipient, int recipientPlayerIndex, String message);
	}

	public void setChatListener(final ChatListener listener) {
		this.player.setChatListener(listener == null ? null : listener::chat);
	}

	public CPlayerUnitOrderListener viewerCommands(final int localPlayerIndex) {
		return new ReplayViewerCommands(() -> this.viewer.simulation.getPlayerCommandHost(), localPlayerIndex);
	}

	/** Whether the replay is played although this engine would have refused it. */
	public boolean isForced() {
		return this.forced;
	}

	public void setForced(final boolean forced) {
		this.forced = forced;
	}

	public CReplayPlayer getPlayer() {
		return this.player;
	}

	public CReplay getReplay() {
		return this.replay;
	}

	/** The replay's file, or null when it was not read from one. */
	public FileHandle getFile() {
		return this.file;
	}

	/** The step the replay ends at; the time bar runs to it. */
	public int getLastTick() {
		return this.replay.getLastTick();
	}

	public boolean isEnded() {
		return this.ended;
	}

	@Override
	public int getLatestCompletedTurn() {
		final int tick = this.viewer.simulation.getGameTurnTick();
		if (!this.player.canStep()) {
			end();
			return tick - 1;
		}
		this.player.beforeStep();
		return tick;
	}

	@Override
	public void turnCompleted(final int gameTurnTick) {
		this.player.afterStep();
		// A replay recorded before the recording stopped at the game's end goes on past it; the game ends first.
		if (!this.player.canStep() || this.viewer.isGameEnded()) {
			end();
		}
	}

	@Override
	public void framesSkipped(final float skippedCount) {
	}

	private void end() {
		if (this.ended) {
			return;
		}
		this.ended = true;
		if (!this.viewer.isGameEnded()) {
			// What the players did after the last step, such as leaving the game, ends the recorded game.
			this.player.beforeStep();
		}
		if (!this.viewer.isGameEnded()) {
			// The recorded game ended outside its script (its player left from the menu) or was cut off: the
			// viewer is shown how it stands, on the score screen.
			this.viewer.showGameEnd();
		}
		GameReplays.report(this.player.report());
	}

	@Override
	public void gameEnded() {
	}

	@Override
	public float localValue(final float value) {
		return this.player.localValue(value);
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		CGameCache.setDisk(null);
		// The replay's caches are not the player's.
		CGameCache.dropUnsaved();
		if (!this.ended) {
			Log.warn("Replay: left at step " + this.viewer.simulation.getGameTurnTick() + ", "
					+ this.player.report());
		}
	}
}
