package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.util.GameDataConstants;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CPlayerStateListener.CPlayerStateNotifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CReleaseCheats.Code;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CReleaseCheats.Result;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerScore;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

public class CReleaseCheatsTest {
	private static CSimulation game(final int... humans) {
		final CSimulation game = TestObjects.allocate(CSimulation.class);
		TestObjects.set(game, "gameData", GameDataConstants.current());
		final List<CPlayer> players = new ArrayList<>();
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			final CPlayer player = TestObjects.allocate(CPlayer.class);
			TestObjects.set(player, "scores", new int[CPlayerScore.VALUES.length]);
			TestObjects.set(player, "stateNotifier", new CPlayerStateNotifier());
			TestObjects.set(player, "mapControl", i < 4 ? CMapControl.COMPUTER : CMapControl.NONE);
			TestObjects.set(player, "slotState", i < 4 ? CPlayerSlotState.PLAYING : CPlayerSlotState.EMPTY);
			TestObjects.set(player, "foodCap", 10);
			TestObjects.set(player, "foodCapCeiling", 100);
			TestObjects.set(player, "rawcodeToTechtreeUnlocked", new HashMap<War3ID, Integer>());
			players.add(player);
		}
		for (final int human : humans) {
			TestObjects.set(players.get(human), "mapControl", CMapControl.USER);
		}
		TestObjects.set(game, "players", players);
		return game;
	}

	@Test
	public void parsesTheCodeWhateverTheCaseAndSpacing() {
		assertEquals(Code.GREEDISGOOD, CReleaseCheats.parse("  GreedIsGood   500 ").getCode());
		assertEquals("500", CReleaseCheats.parse("  GreedIsGood   500 ").getArgument());
		assertEquals("", CReleaseCheats.parse("WhosYourDaddy").getArgument());
		assertEquals("human 3", CReleaseCheats.parse("motherland Human 3").getArgument());
		assertEquals(Code.ALLYOURBASEAREBELONGTOUS, CReleaseCheats.parse("allyourbasearebelongtous").getCode());
		assertNull(CReleaseCheats.parse("greedisgoodx"));
		assertNull(CReleaseCheats.parse("hello greedisgood"));
		assertNull(CReleaseCheats.parse(""));
		assertNull(CReleaseCheats.parse(null));
		for (final Code code : Code.values()) {
			assertEquals(code, CReleaseCheats.parse(code.text()).getCode());
		}
	}

	@Test
	public void resourceCheatsGiveEveryPlayerAndDefaultTo500() {
		final CSimulation game = game(0);
		assertEquals(Result.ENABLED, CReleaseCheats.apply(game, 0, "greedisgood"));
		assertEquals(Result.ENABLED, CReleaseCheats.apply(game, 0, "keysersoze 100"));
		assertEquals(Result.ENABLED, CReleaseCheats.apply(game, 0, "leafittome 50"));
		for (int i = 0; i < WarsmashConstants.MAX_PLAYERS; i++) {
			assertEquals(600, game.getPlayer(i).getGold());
			assertEquals(550, game.getPlayer(i).getLumber());
		}
		assertEquals(Result.IGNORED, CReleaseCheats.apply(game, 0, "greedisgood abc"));
		assertEquals(600, game.getPlayer(0).getGold());
	}

	@Test
	public void onlyTheOneHumanOfTheGameCanCheat() {
		final CSimulation alone = game(0);
		assertTrue(CReleaseCheats.isSinglePlayer(alone, 0));
		assertFalse(CReleaseCheats.isSinglePlayer(alone, 1));
		assertEquals(Result.IGNORED, CReleaseCheats.apply(alone, 1, "greedisgood 10"));
		assertEquals(0, alone.getPlayer(0).getGold());

		final CSimulation twoHumans = game(0, 2);
		assertFalse(CReleaseCheats.isSinglePlayer(twoHumans, 0));
		assertEquals(Result.IGNORED, CReleaseCheats.apply(twoHumans, 0, "greedisgood 10"));
		assertEquals(0, twoHumans.getPlayer(0).getGold());

		// A human who left still makes it a multiplayer game.
		TestObjects.set(twoHumans.getPlayer(2), "slotState", CPlayerSlotState.LEFT);
		assertFalse(CReleaseCheats.isSinglePlayer(twoHumans, 0));

		final CSimulation noHuman = game();
		assertFalse(CReleaseCheats.isSinglePlayer(noHuman, 0));
	}

	@Test
	public void togglesTellEnabledThenDisabled() {
		final CSimulation game = game(0);
		assertEquals(Result.ENABLED, CReleaseCheats.preview(game, 0, "whosyourdaddy"));
		assertEquals(Result.ENABLED, CReleaseCheats.apply(game, 0, "whosyourdaddy"));
		assertTrue(game.getPlayer(0).hasCheat(CReleaseCheats.GOD_MODE));
		assertFalse(game.getPlayer(1).hasCheat(CReleaseCheats.GOD_MODE));
		assertEquals(Result.DISABLED, CReleaseCheats.preview(game, 0, "whosyourdaddy"));
		assertEquals(Result.DISABLED, CReleaseCheats.apply(game, 0, "WHOSYOURDADDY"));
		assertFalse(game.getPlayer(0).hasCheat(CReleaseCheats.GOD_MODE));

		assertEquals(Result.ENABLED, CReleaseCheats.apply(game, 0, "iseedeadpeople"));
		assertTrue(game.isCheatRevealMap());
		assertEquals(Result.DISABLED, CReleaseCheats.apply(game, 0, "iseedeadpeople"));
		assertFalse(game.isCheatRevealMap());
	}

	@Test
	public void gameRuleCheatsReachEveryPlayer() {
		final CSimulation game = game(0);
		CReleaseCheats.apply(game, 0, "pointbreak");
		CReleaseCheats.apply(game, 0, "synergy");
		CReleaseCheats.apply(game, 0, "thereisnospoon");
		CReleaseCheats.apply(game, 0, "strengthandhonor");
		for (final int i : new int[] { 0, 3 }) {
			final CPlayer player = game.getPlayer(i);
			assertTrue(player.getFoodCapLimit() > 1000000);
			assertTrue(player.isRequirementMet(War3ID.fromString("hcas"), 1));
			assertTrue(player.hasCheat(CReleaseCheats.NO_MANA_COST));
			assertTrue(player.hasCheat(CReleaseCheats.NO_COOLDOWNS));
		}
		assertEquals(Result.DISABLED, CReleaseCheats.apply(game, 0, "thedudeabides"));
		assertFalse(game.getPlayer(3).hasCheat(CReleaseCheats.NO_COOLDOWNS));
		assertTrue(game.getPlayer(3).hasCheat(CReleaseCheats.NO_MANA_COST));
		assertTrue(CReleaseCheats.isNoDefeat(game));
		assertFalse(CReleaseCheats.isNoVictory(game));
		CReleaseCheats.apply(game, 0, "pointbreak");
		CReleaseCheats.apply(game, 0, "synergy");
		assertEquals(10, game.getPlayer(3).getFoodCapLimit());
		assertFalse(game.getPlayer(3).isRequirementMet(War3ID.fromString("hcas"), 1));
	}

	@Test
	public void argumentsThatNameNothingAreIgnored() {
		final CSimulation game = game(0);
		assertEquals(Result.IGNORED, CReleaseCheats.preview(game, 0, "daylightsavings noon"));
		assertEquals(Result.IGNORED, CReleaseCheats.preview(game, 0, "daylightsavings 25"));
		assertEquals(Result.IGNORED, CReleaseCheats.preview(game, 0, "motherland human"));
		assertEquals(Result.IGNORED, CReleaseCheats.preview(game, 0, "motherland elves 2"));
		assertEquals(Result.ENABLED, CReleaseCheats.preview(game, 0, "motherland nightelfx 3"));
		assertEquals(Result.IGNORED, CReleaseCheats.preview(game, 0, "hello"));
	}

	@Test
	public void theScriptCheatKeepsItsOwnMeasuredSet() {
		final CSimulation game = game();
		CReleaseCheats.apply(game, "greedisgood 25");
		CReleaseCheats.apply(game, "greedisgood");
		CReleaseCheats.apply(game, "itvexesme");
		assertEquals(25, game.getPlayer(5).getGold());
		assertFalse(CReleaseCheats.isNoVictory(game));
	}
}
