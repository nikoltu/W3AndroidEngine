package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.util.GameDataConstants;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CPlayerStateListener.CPlayerStateNotifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

public class CPlayerCommandTest {
	private static final List<Consumer<CPlayerUnitOrderListener>> EVERY_COMMAND = List.of(
			l -> l.issueTargetOrder(1, 2, 3, 4, true), l -> l.issuePointOrder(5, 6, 7, -12.5f, 1e9f, false),
			l -> l.issueGroupPointOrder(new int[] { 1, 2 }, new int[] { 3, 4 }, 5, new float[] { 0.1f, -0.2f },
					new float[] { Float.MIN_VALUE, Float.MAX_VALUE }, true),
			l -> l.issueDropItemAtPointOrder(1, 2, 3, 4, 5.5f, 6.5f, true),
			l -> l.issueDropItemAtTargetOrder(1, 2, 3, 4, 5, false), l -> l.issueImmediateOrder(9, 0, 851972, false),
			l -> l.unitCancelTrainingItem(7, 2), l -> l.issueGuiPlayerEvent(17),
			l -> l.selectUnits(new int[] { 10, 11 }, new int[0]), l -> l.chatMessage(3, 5, "gg äö |cffff0000x|r"),
			l -> l.clickDialogButton(2, 1), l -> l.setAlliance(4, CAllianceType.SHARED_VISION.ordinal(), true),
			l -> l.transferResources(1, 100, 50), l -> l.fireGameEvent(42), l -> l.saveGame("My Save"),
			l -> l.leaveGame(), l -> l.cheat("greedisgood 500"), l -> l.setGamePaused(true),
			l -> l.minimapSignal(-1024f, 2048.25f), l -> l.trackableEvent(1048600, true),
			l -> l.trackableEvent(7, false),
			l -> l.mouseEvent(JassGameEventsWar3.EVENT_PLAYER_MOUSE_DOWN.getEventId(), -512.5f, 1e-3f, 3),
			l -> l.mouseEvent(JassGameEventsWar3.EVENT_PLAYER_MOUSE_MOVE.getEventId(), 7f, -7f, 0));

	private static byte[] encode(final Consumer<CPlayerUnitOrderListener> command) {
		final CPlayerCommandCodec.Encoder encoder = new CPlayerCommandCodec.Encoder();
		command.accept(encoder);
		return encoder.take();
	}

	@Test
	public void everyCommandDecodesToTheSameCall() {
		for (final Consumer<CPlayerUnitOrderListener> command : EVERY_COMMAND) {
			final byte[] bytes = encode(command);
			final CPlayerCommandCodec.Encoder again = new CPlayerCommandCodec.Encoder();
			CPlayerCommandCodec.decode(bytes, again);
			assertArrayEquals(bytes, again.take());
		}
	}

	@Test
	public void severalCommandsDecodeInOrder() {
		final CPlayerCommandCodec.Encoder encoder = new CPlayerCommandCodec.Encoder();
		for (final Consumer<CPlayerUnitOrderListener> command : EVERY_COMMAND) {
			command.accept(encoder);
		}
		final byte[] all = encoder.take();
		final CPlayerCommandCodec.Encoder again = new CPlayerCommandCodec.Encoder();
		CPlayerCommandCodec.decode(all, again);
		assertArrayEquals(all, again.take());
	}

	@Test
	public void brokenBytesAreRejected() {
		final byte[] bytes = encode(l -> l.chatMessage(0, -1, "hello"));
		final byte[] cut = java.util.Arrays.copyOf(bytes, bytes.length - 2);
		assertThrows(IllegalArgumentException.class,
				() -> CPlayerCommandCodec.decode(cut, new CPlayerCommandCodec.Encoder()));
		assertThrows(IllegalArgumentException.class,
				() -> CPlayerCommandCodec.decode(new byte[] { 99 }, new CPlayerCommandCodec.Encoder()));
	}

	@Test
	public void recorderLogsTheStepAndAppliesTheRecordedCommand() {
		final CSimulation game = TestObjects.allocate(CSimulation.class);
		TestObjects.set(game, "gameTurnTick", 37);
		final CPlayerCommandCodec.Encoder applied = new CPlayerCommandCodec.Encoder();
		final CPlayerCommandRecorder recorder = new CPlayerCommandRecorder(game, 2, applied);
		recorder.transferResources(1, 100, 50);
		recorder.selectUnits(new int[] { 4 }, new int[] { 5, 6 });
		final List<CPlayerCommandLog.Entry> entries = game.getPlayerCommandLog().getEntries();
		assertEquals(2, entries.size());
		assertEquals(37, entries.get(0).getGameTurnTick());
		assertEquals(2, entries.get(0).getPlayerIndex());
		final CPlayerCommandCodec.Encoder replayed = new CPlayerCommandCodec.Encoder();
		for (final CPlayerCommandLog.Entry entry : entries) {
			entry.applyTo(replayed);
		}
		assertArrayEquals(applied.take(), replayed.take());
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

	@Test
	public void resourceTransferGivesNoMoreThanThePlayerHas() {
		final CSimulation game = gameWithPlayers(300);
		final CPlayerUnitOrderExecutor executor = new CPlayerUnitOrderExecutor(game, 0);
		executor.transferResources(1, 1000, 100);
		assertEquals(0, game.getPlayer(0).getGold());
		assertEquals(600, game.getPlayer(1).getGold());
		assertEquals(200, game.getPlayer(0).getLumber());
		assertEquals(400, game.getPlayer(1).getLumber());
		assertEquals(300, game.getPlayer(0).getScore(CPlayerScore.GOLD_GIVEN));
		assertEquals(100, game.getPlayer(1).getScore(CPlayerScore.LUMBER_RECEIVED));
		executor.transferResources(0, 100, 100);
		executor.transferResources(1, -50, -50);
		assertEquals(600, game.getPlayer(1).getGold());
		assertEquals(400, game.getPlayer(1).getLumber());
	}

	@Test
	public void lockedTradingAndAlliancesIgnoreTheCommand() {
		final CSimulation game = gameWithPlayers(300);
		@SuppressWarnings("unchecked")
		final EnumMap<CMapFlag, Boolean> flags = (EnumMap<CMapFlag, Boolean>) TestObjects
				.get(TestObjects.get(game, "mapConfig"), "mapFlagToEnabled");
		flags.put(CMapFlag.MAP_LOCK_RESOURCE_TRADING, true);
		flags.put(CMapFlag.MAP_LOCK_ALLIANCE_CHANGES, true);
		final CPlayerUnitOrderExecutor executor = new CPlayerUnitOrderExecutor(game, 0);
		executor.transferResources(1, 100, 100);
		assertEquals(300, game.getPlayer(0).getGold());
		// Would need the player's alliance table if it were applied.
		executor.setAlliance(1, CAllianceType.PASSIVE.ordinal(), true);
		executor.setAlliance(1, CAllianceType.RESCUABLE.ordinal(), true);
		executor.setAlliance(0, CAllianceType.SHARED_VISION.ordinal(), true);
	}

	@Test
	public void cheatCommandGivesEveryPlayerGold() {
		final CSimulation game = gameWithPlayers(0);
		TestObjects.set(game.getPlayer(0), "mapControl", CMapControl.USER);
		TestObjects.set(game.getPlayer(0), "slotState", CPlayerSlotState.PLAYING);
		new CPlayerUnitOrderExecutor(game, 0).cheat("  GreedIsGood 500 ");
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			assertEquals(500, game.getPlayer(i).getGold());
			assertEquals(500, game.getPlayer(i).getLumber());
		}
		new CPlayerUnitOrderExecutor(game, 0).cheat("keysersoze x");
		assertEquals(500, game.getPlayer(3).getGold());
	}
}
