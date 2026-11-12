package com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay;

import java.util.List;
import java.util.function.ToIntFunction;

import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCache;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandCodec;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderExecutor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;

/**
 * Plays a replay on a game started from its header: the recorded commands are applied in the steps they were
 * applied in, through the same executor as in the game, the AI and the scripts run again, and the game's checksum is
 * compared with the recorded one wherever there is one. The first step where they differ is the desync step.
 */
public final class CReplayPlayer {
	private final CReplay replay;
	private final CSimulation game;
	private final ToIntFunction<CSimulation> checksum;
	private final CPlayerUnitOrderListener[] executors;
	private int nextCommand;
	private int nextChecksum;
	private int nextCacheRead;
	private int nextLocalValue;
	private boolean localValuesRanOut;
	private ChatListener chatListener;
	private int checksumsCompared;
	private int desyncTick = -1;
	private int desyncs;

	public CReplayPlayer(final CReplay replay, final CSimulation game, final ToIntFunction<CSimulation> checksum) {
		this.replay = replay;
		this.game = game;
		this.executors = new CPlayerUnitOrderListener[game.getGameData().getMaxPlayers()];
		this.checksum = checksum;
	}

	/** Told of each chat line a recorded player sent, as the playback applies it. */
	public interface ChatListener {
		void chat(int senderIndex, int recipient, int recipientPlayerIndex, String message);
	}

	public void setChatListener(final ChatListener chatListener) {
		this.chatListener = chatListener;
	}

	/** Whether the game may make its next step. */
	public boolean canStep() {
		return this.game.getGameTurnTick() < this.replay.getLastTick();
	}

	public boolean isFinished() {
		return !canStep();
	}

	/** Applies the commands recorded after the step the game is at, just before its next step. */
	public void beforeStep() {
		final int tick = this.game.getGameTurnTick();
		final List<CReplay.Command> commands = this.replay.getCommands();
		while ((this.nextCommand < commands.size()) && (commands.get(this.nextCommand).getTick() <= tick)) {
			final CReplay.Command command = commands.get(this.nextCommand++);
			final CPlayerUnitOrderListener executor = executor(command.getPlayerIndex());
			if (executor == null) {
				continue;
			}
			try {
				CPlayerCommandCodec.decode(command.bytes(), executor);
			}
			catch (final IllegalArgumentException e) {
				Log.error("Replay: broken command at step " + command.getTick() + ": " + e.getMessage());
			}
		}
	}

	/** Compares the game with the replay after a step. */
	public void afterStep() {
		final int tick = this.game.getGameTurnTick();
		final List<CReplay.Checksum> checksums = this.replay.getChecksums();
		while ((this.nextChecksum < checksums.size()) && (checksums.get(this.nextChecksum).getTick() < tick)) {
			this.nextChecksum++;
		}
		if ((this.nextChecksum < checksums.size()) && (checksums.get(this.nextChecksum).getTick() == tick)) {
			final int recorded = checksums.get(this.nextChecksum++).getValue();
			final int now = this.checksum.applyAsInt(this.game);
			this.checksumsCompared++;
			if (now != recorded) {
				this.desyncs++;
				if (this.desyncTick < 0) {
					this.desyncTick = tick;
					Log.warn("Replay desync at step " + tick + ": recorded "
							+ Integer.toHexString(recorded) + ", played " + Integer.toHexString(now));
				}
			}
		}
	}

	/** The game cache files as the recorded game read them, in the same order; nothing is written. */
	public CGameCache.Disk recordedDisk() {
		return new CGameCache.Disk() {
			@Override
			public byte[] read() {
				final List<CReplay.CacheRead> reads = CReplayPlayer.this.replay.getCacheReads();
				if (CReplayPlayer.this.nextCacheRead >= reads.size()) {
					Log.warn("Replay: the game reads the game caches more often than recorded");
					return null;
				}
				return reads.get(CReplayPlayer.this.nextCacheRead++).getBytes();
			}

			@Override
			public boolean write(final byte[] bytes) {
				return true;
			}
		};
	}

	/**
	 * The local value a script reads, as the recorded game's script read it, in the same order. Past the recorded
	 * ones the playback's own value is the best there is.
	 */
	public float localValue(final float value) {
		final List<CReplay.LocalValue> values = this.replay.getLocalValues();
		if (this.nextLocalValue >= values.size()) {
			if (!this.localValuesRanOut) {
				this.localValuesRanOut = true;
				Log.warn("Replay: the scripts read more local values than recorded, from step "
						+ this.game.getGameTurnTick());
			}
			return value;
		}
		return values.get(this.nextLocalValue++).getValue();
	}

	private CPlayerUnitOrderListener executor(final int playerIndex) {
		if ((playerIndex < 0) || (playerIndex >= this.executors.length)) {
			return null;
		}
		if (this.executors[playerIndex] == null) {
			this.executors[playerIndex] = new CReplayCommands(new CPlayerUnitOrderExecutor(this.game, playerIndex),
					(recipient, recipientPlayerIndex, message) -> {
						if (this.chatListener != null) {
							this.chatListener.chat(playerIndex, recipient, recipientPlayerIndex, message);
						}
					});
		}
		return this.executors[playerIndex];
	}

	public CReplay getReplay() {
		return this.replay;
	}

	public int getChecksumsCompared() {
		return this.checksumsCompared;
	}

	/** The first step whose checksum differs from the recorded one, or -1. */
	public int getDesyncTick() {
		return this.desyncTick;
	}

	public int getDesyncCount() {
		return this.desyncs;
	}

	public int getCommandsApplied() {
		return this.nextCommand;
	}

	/** One line for logs and test tools. */
	public String report() {
		return "result=" + (this.desyncTick < 0 ? "OK" : "DESYNC") + " steps=" + this.game.getGameTurnTick()
				+ " lastStep=" + this.replay.getLastTick() + " complete=" + this.replay.isComplete() + " commands="
				+ this.nextCommand + "/" + this.replay.getCommands().size() + " checksums=" + this.checksumsCompared
				+ "/" + this.replay.getChecksums().size() + " desyncStep=" + this.desyncTick + " desyncs="
				+ this.desyncs;
	}
}
