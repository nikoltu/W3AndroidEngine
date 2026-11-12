package com.etheller.warsmash.viewer5.handlers.w3x.simulation.replay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.util.GameDataConstants;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CPlayerStateListener.CPlayerStateNotifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ai.AIDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCache;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CBasePlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapFlag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandHost;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandRecorder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerScore;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderExecutor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

public class CReplayTest {
	private static final int INTERVAL = 20;
	private static final ToIntFunction<CSimulation> GOLD_CHECKSUM = game -> {
		int sum = game.getGameTurnTick();
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			sum = (sum * 31) + game.getPlayer(i).getGold();
		}
		return sum;
	};

	private static CReplayHeader header() {
		return new CReplayHeader("test", "abc1234", false, "Maps/Test.w3x", 0x1234abcd, 4096, 0x5eed, 1L, 1337, 0, 1.0f,
				CMapDifficulty.NORMAL, INTERVAL, Collections.emptyList(), null);
	}

	private static CSimulation gameWithPlayers(final int gold) {
		final CSimulation game = TestObjects.allocate(CSimulation.class);
		TestObjects.set(game, "gameData", GameDataConstants.current());
		final List<CPlayer> players = new ArrayList<>();
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			final CPlayer player = TestObjects.allocate(CPlayer.class);
			TestObjects.set(player, "scores", new int[CPlayerScore.VALUES.length]);
			TestObjects.set(player, "stateNotifier", new CPlayerStateNotifier());
			TestObjects.set(player, "gold", gold);
			TestObjects.set(player, "lumber", gold);
			players.add(player);
		}
		TestObjects.set(game, "players", players);
		final War3MapConfig mapConfig = TestObjects.allocate(War3MapConfig.class);
		TestObjects.set(mapConfig, "mapFlagToEnabled", new EnumMap<CMapFlag, Boolean>(CMapFlag.class));
		TestObjects.set(game, "mapConfig", mapConfig);
		return game;
	}

	private static void step(final CSimulation game) {
		TestObjects.set(game, "gameTurnTick", game.getGameTurnTick() + 1);
	}

	/** The host calls a playback must make, with the step they came in. */
	private static final class HostLog implements CPlayerCommandHost {
		private final CSimulation game;
		private final List<String> calls = new ArrayList<>();

		private HostLog(final CSimulation game) {
			this.game = game;
		}

		@Override
		public void clickDialogButton(final int playerIndex, final int dialogHandleId, final int buttonHandleId) {
			this.calls.add(this.game.getGameTurnTick() + " dialog " + playerIndex + " " + dialogHandleId + " "
					+ buttonHandleId);
		}

		@Override
		public void setGamePaused(final int playerIndex, final boolean paused) {
			this.calls.add(this.game.getGameTurnTick() + " pause " + playerIndex + " " + paused);
		}

		@Override
		public void minimapSignal(final int playerIndex, final float x, final float y) {
			this.calls.add(this.game.getGameTurnTick() + " signal " + playerIndex + " " + x + " " + y);
		}
	}

	/** Plays 75 steps with commands of two players and returns the replay's bytes. */
	private static byte[] record(final CSimulation game, final HostLog host, final boolean finish)
			throws IOException {
		game.setPlayerCommandHost(host);
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final CReplayWriter writer = new CReplayWriter(bytes, header());
		final CReplayRecorder recorder = new CReplayRecorder(game, writer, INTERVAL, GOLD_CHECKSUM);
		final CPlayerCommandRecorder player0 = new CPlayerCommandRecorder(game, 0,
				new CPlayerUnitOrderExecutor(game, 0));
		final CPlayerCommandRecorder player3 = new CPlayerCommandRecorder(game, 3,
				new CPlayerUnitOrderExecutor(game, 3));
		player0.transferResources(1, 50, 0);
		while (game.getGameTurnTick() < 75) {
			step(game);
			recorder.afterStep();
			final int tick = game.getGameTurnTick();
			if (tick == 10) {
				player0.transferResources(2, 100, 10);
				player3.clickDialogButton(1048600, 1048601);
			}
			if (tick == 33) {
				player3.setGamePaused(true);
				player3.minimapSignal(-64f, 128.5f);
				player3.setGamePaused(false);
				player0.transferResources(3, 7, 7);
			}
			if (tick == 74) {
				player3.transferResources(0, 1, 0);
			}
		}
		if (finish) {
			recorder.finish();
		}
		else {
			writer.close();
		}
		return bytes.toByteArray();
	}

	private static CReplayPlayer play(final byte[] bytes, final CSimulation game, final HostLog host)
			throws IOException {
		game.setPlayerCommandHost(host);
		final CReplayPlayer player = new CReplayPlayer(CReplay.read(new ByteArrayInputStream(bytes)), game,
				GOLD_CHECKSUM);
		while (player.canStep()) {
			player.beforeStep();
			step(game);
			player.afterStep();
		}
		return player;
	}

	@Test
	public void playbackAppliesTheCommandsInTheirStepsAndMatchesEveryChecksum() throws IOException {
		final CSimulation recorded = gameWithPlayers(300);
		final HostLog recordedHost = new HostLog(recorded);
		final byte[] bytes = record(recorded, recordedHost, true);

		final CSimulation played = gameWithPlayers(300);
		final HostLog playedHost = new HostLog(played);
		final CReplayPlayer player = play(bytes, played, playedHost);

		assertEquals(-1, player.getDesyncTick(), player.report());
		assertEquals(75, played.getGameTurnTick());
		assertEquals(3, player.getChecksumsCompared());
		assertEquals(8, player.getCommandsApplied());
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			assertEquals(recorded.getPlayer(i).getGold(), played.getPlayer(i).getGold(), "player " + i);
		}
		// The recorded pause stopped only the recorded game's clock.
		final List<String> withoutPauses = new ArrayList<>(recordedHost.calls);
		withoutPauses.removeIf(call -> call.contains(" pause "));
		assertEquals(Arrays.asList("10 dialog 3 1048600 1048601", "33 signal 3 -64.0 128.5"), withoutPauses);
		assertEquals(withoutPauses, playedHost.calls);
	}

	@Test
	public void aDifferentGameIsReportedAtTheFirstChecksumThatDiffers() throws IOException {
		final byte[] bytes = record(gameWithPlayers(300), null, true);
		final CSimulation played = gameWithPlayers(300);
		TestObjects.set(played, "gameTurnTick", 0);
		final CReplay replay = CReplay.read(new ByteArrayInputStream(bytes));
		final CReplayPlayer player = new CReplayPlayer(replay, played, GOLD_CHECKSUM);
		while (player.canStep()) {
			player.beforeStep();
			step(played);
			if (played.getGameTurnTick() == 30) {
				played.getPlayer(5).setGold(1);
			}
			player.afterStep();
		}
		assertEquals(40, player.getDesyncTick());
		assertEquals(2, player.getDesyncCount());
		assertTrue(player.report().startsWith("result=DESYNC"));
	}

	@Test
	public void aReplayCutOffInTheGamePlaysToItsLastChecksum() throws IOException {
		final byte[] whole = record(gameWithPlayers(300), null, false);
		final CReplay unfinished = CReplay.read(new ByteArrayInputStream(whole));
		assertFalse(unfinished.isComplete());
		assertEquals(75, unfinished.getLastTick());

		// The process ended between two checksums: the bytes after the last flush never reached the file.
		final CSimulation recorded = gameWithPlayers(300);
		recorded.setPlayerCommandHost(null);
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final CReplayWriter writer = new CReplayWriter(bytes, header());
		final CReplayRecorder recorder = new CReplayRecorder(recorded, writer, INTERVAL, GOLD_CHECKSUM);
		final CPlayerCommandRecorder player0 = new CPlayerCommandRecorder(recorded, 0,
				new CPlayerUnitOrderExecutor(recorded, 0));
		int flushed = 0;
		while (recorded.getGameTurnTick() < 50) {
			step(recorded);
			recorder.afterStep();
			if (recorded.getGameTurnTick() == 40) {
				flushed = bytes.size();
			}
			player0.transferResources(1, 1, 1);
		}
		final byte[] cut = Arrays.copyOf(bytes.toByteArray(), flushed);
		final CReplay replay = CReplay.read(new ByteArrayInputStream(cut));
		assertFalse(replay.isComplete());
		assertEquals(2, replay.getChecksums().size());
		assertEquals(40, replay.getLastTick());
		final CSimulation played = gameWithPlayers(300);
		final CReplayPlayer player = new CReplayPlayer(replay, played, GOLD_CHECKSUM);
		while (player.canStep()) {
			player.beforeStep();
			step(played);
			player.afterStep();
		}
		assertEquals(-1, player.getDesyncTick());
		assertEquals(2, player.getChecksumsCompared());
		writer.close();
	}

	@Test
	public void theReplayEndsAtTheStepTheGameEndedIn() throws IOException {
		final CSimulation game = gameWithPlayers(0);
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final CReplayWriter writer = new CReplayWriter(bytes, header());
		final CReplayRecorder recorder = new CReplayRecorder(game, writer, INTERVAL, GOLD_CHECKSUM);
		while (game.getGameTurnTick() < 39) {
			step(game);
			recorder.afterStep();
		}
		// EndGame in a script runs in the middle of step 40: the step is still recorded, and nothing after it.
		step(game);
		recorder.gameEnded();
		assertFalse(recorder.isEnded());
		recorder.afterStep();
		assertTrue(recorder.isEnded());
		step(game);
		recorder.afterStep();
		recorder.finish();

		final CReplay replay = CReplay.read(new ByteArrayInputStream(bytes.toByteArray()));
		assertTrue(replay.isComplete());
		assertEquals(40, replay.getLastTick());
		assertEquals(2, replay.getChecksums().size());
		assertEquals(40, replay.getChecksums().get(1).getTick());
		final CSimulation played = gameWithPlayers(0);
		final CReplayPlayer player = play(bytes.toByteArray(), played, new HostLog(played));
		assertEquals(40, played.getGameTurnTick());
		assertEquals(2, player.getChecksumsCompared());
		assertEquals(-1, player.getDesyncTick());
	}

	@Test
	public void aGameEndedBetweenStepsEndsItsReplayRightAway() throws IOException {
		final CSimulation game = gameWithPlayers(0);
		game.setPlayerCommandHost(new HostLog(game));
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final CReplayWriter writer = new CReplayWriter(bytes, header());
		final CReplayRecorder recorder = new CReplayRecorder(game, writer, INTERVAL, GOLD_CHECKSUM);
		final CPlayerCommandRecorder player0 = new CPlayerCommandRecorder(game, 0,
				new CPlayerUnitOrderExecutor(game, 0));
		while (game.getGameTurnTick() < 25) {
			step(game);
			recorder.afterStep();
		}
		// Leaving from the menu: the command comes after the last step, then the game ends.
		player0.minimapSignal(1f, 2f);
		recorder.gameEnded();
		assertTrue(recorder.isEnded());

		final CReplay replay = CReplay.read(new ByteArrayInputStream(bytes.toByteArray()));
		assertEquals(25, replay.getLastTick());
		assertEquals(1, replay.getCommands().size());
		assertEquals(25, replay.getCommands().get(0).getTick());
	}

	@Test
	public void gameCacheReadsComeBackInOrderAndNothingIsWritten() throws IOException {
		final CSimulation game = gameWithPlayers(0);
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final CReplayWriter writer = new CReplayWriter(bytes, header());
		final CReplayRecorder recorder = new CReplayRecorder(game, writer, INTERVAL, GOLD_CHECKSUM);
		final List<byte[]> disk = new ArrayList<>(Arrays.asList(new byte[] { 1, 2, 3 }, null, new byte[0]));
		final CGameCache.Disk recording = recorder.recordingDisk(new CGameCache.Disk() {
			@Override
			public byte[] read() {
				return disk.remove(0);
			}

			@Override
			public boolean write(final byte[] contents) {
				return true;
			}
		});
		assertArrayEquals(new byte[] { 1, 2, 3 }, recording.read());
		assertNull(recording.read());
		assertArrayEquals(new byte[0], recording.read());
		recorder.finish();

		final CReplay replay = CReplay.read(new ByteArrayInputStream(bytes.toByteArray()));
		final CGameCache.Disk played = new CReplayPlayer(replay, gameWithPlayers(0), GOLD_CHECKSUM).recordedDisk();
		assertArrayEquals(new byte[] { 1, 2, 3 }, played.read());
		assertNull(played.read());
		assertArrayEquals(new byte[0], played.read());
		assertNull(played.read());
		assertTrue(played.write(new byte[] { 9 }));
	}

	@Test
	public void localValuesComeBackInOrderAndThePlaybacksOwnOnesAfterThem() throws IOException {
		final CSimulation game = gameWithPlayers(0);
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final CReplayWriter writer = new CReplayWriter(bytes, header());
		final CReplayRecorder recorder = new CReplayRecorder(game, writer, INTERVAL, GOLD_CHECKSUM);
		assertEquals(-1024.5f, recorder.localValue(-1024.5f));
		step(game);
		recorder.afterStep();
		assertEquals(3e7f, recorder.localValue(3e7f));
		recorder.finish();

		final CReplay replay = CReplay.read(new ByteArrayInputStream(bytes.toByteArray()));
		assertEquals(2, replay.getLocalValues().size());
		assertEquals(0, replay.getLocalValues().get(0).getTick());
		assertEquals(1, replay.getLocalValues().get(1).getTick());
		final CReplayPlayer player = new CReplayPlayer(replay, gameWithPlayers(0), GOLD_CHECKSUM);
		assertEquals(-1024.5f, player.localValue(5f));
		assertEquals(3e7f, player.localValue(6f));
		assertEquals(7f, player.localValue(7f));
	}

	@Test
	public void theGameSetupComesBackAsRecorded() throws IOException {
		final War3MapConfig config = new War3MapConfig(WarsmashConstants.MAX_PLAYERS);
		config.setGameDifficulty(CMapDifficulty.HARD);
		final CBasePlayer human = config.getPlayer(0);
		human.setName("Profile Name");
		human.setController(CMapControl.USER);
		human.setSlotState(CPlayerSlotState.PLAYING);
		human.getRacePreferences().setBitFlags(4);
		human.setTeam(1);
		human.setColor(6);
		human.setStartLocationIndex(3);
		human.setHandicap(0.8f);
		final CBasePlayer computer = config.getPlayer(1);
		computer.setController(CMapControl.COMPUTER);
		computer.setSlotState(CPlayerSlotState.PLAYING);
		computer.setAIDifficulty(AIDifficulty.INSANE);
		computer.setStartLocationIndex(0);
		final byte[] caches = { 5, 6, 7 };
		final CReplayHeader header = CReplayHeader.of("0.3.1", "abc1234", true, "Maps/Test/Sample.w3x", -5, 123456789012L, 0x7a7a7a7a,
				99L, -42, 0, 2.0f, config, INTERVAL, caches);

		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		new CReplayWriter(bytes, header).close();
		final CReplayHeader read = CReplay.readHeader(new ByteArrayInputStream(bytes.toByteArray()));
		assertEquals("0.3.1", read.getEngineVersion());
		assertEquals("abc1234", read.getBuildCommit());
		assertTrue(read.isOfficialBuild());
		assertEquals("Maps/Test/Sample.w3x", read.getMapPath());
		assertEquals(-5, read.getMapChecksum());
		assertEquals(123456789012L, read.getMapSize());
		assertEquals(0x7a7a7a7a, read.getDataChecksum());
		assertEquals(-42, read.getSeed());
		assertEquals(2.0f, read.getGameSpeed());
		assertEquals(INTERVAL, read.getChecksumInterval());
		assertArrayEquals(caches, read.getGameCaches());

		final War3MapConfig again = new War3MapConfig(WarsmashConstants.MAX_PLAYERS);
		again.getPlayer(0).setStartLocationIndex(7);
		read.applyTo(again, WarsmashConstants.RACE_MANAGER);
		assertEquals(CMapDifficulty.HARD, again.getGameDifficulty());
		final CBasePlayer human2 = again.getPlayer(0);
		assertEquals("Profile Name", human2.getName());
		assertEquals(CMapControl.USER, human2.getController());
		assertEquals(CPlayerSlotState.PLAYING, human2.getSlotState());
		assertEquals(4, human2.getRacePreferences().getBitFlags());
		assertEquals(1, human2.getTeam());
		assertEquals(6, human2.getColor());
		assertEquals(3, human2.getStartLocationIndex());
		assertEquals(0.8f, human2.getHandicap());
		assertEquals(AIDifficulty.INSANE, again.getPlayer(1).getAIDifficulty());
		assertEquals(0, again.getPlayer(1).getStartLocationIndex());
		assertEquals(CMapControl.NEUTRAL, again.getPlayer(WarsmashConstants.MAX_PLAYERS - 1).getController());
	}

	@Test
	public void otherFilesAndFormatsAreRefused() throws IOException {
		assertThrows(CReplayException.class,
				() -> CReplay.read(new ByteArrayInputStream(new byte[] { 'M', 'P', 'Q', 0x1a, 0, 0, 0, 0 })));
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		new CReplayWriter(bytes, header()).close();
		final byte[] other = bytes.toByteArray();
		other[7] = (byte) (CReplay.FORMAT_VERSION + 1);
		final CReplayException refused = assertThrows(CReplayException.class,
				() -> CReplay.read(new ByteArrayInputStream(other)));
		assertTrue(refused.getMessage().contains("format"), refused.getMessage());
	}
}
