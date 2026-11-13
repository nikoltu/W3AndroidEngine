package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.Locale;

import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;

/**
 * The release cheats of a single player game. Measured on 1.29: typed in chat by the only human player they never
 * reach the chat triggers, and the line "Name: Cheat enabled!" (or "Cheat disabled!" when a toggle goes off) takes
 * the chat line's place; a known code with an argument that is not a number does nothing and shows nothing. The
 * resource, map, upgrade, time of day and game rule cheats reach every player; whosyourdaddy only the typing player.
 * From a script (the Cheat native) the developer cheats campaign AI scripts call (showmap, av, dg, vision N, gold N,
 * ...) do nothing in a release game.
 */
public final class CReleaseCheats {
	public static final int GOD_MODE = 1;
	public static final int NO_FOOD_LIMIT = 1 << 1;
	public static final int NO_MANA_COST = 1 << 2;
	public static final int NO_TECH_REQUIREMENTS = 1 << 3;
	public static final int NO_DEFEAT = 1 << 4;
	public static final int NO_VICTORY = 1 << 5;
	/** whoisjohngalt: measured on 1.29, Iron Forged Swords still takes its 60 s; nothing found that it changes. */
	public static final int FAST_RESEARCH = 1 << 6;
	public static final int NO_COOLDOWNS = 1 << 7;
	public static final int FAST_DEATH = 1 << 8;
	/**
	 * iocainepowder, measured on 1.29: a Footman's corpse (3 s death, 2 s decay, 88 s bones) is gone 8.75 s after it
	 * dies, the decay running 16 times faster.
	 */
	public static final float FAST_DECAY_FACTOR = 16f;

	/** Measured on 1.29: greedisgood, keysersoze and leafittome with no amount give 500. */
	public static final int DEFAULT_RESOURCES = 500;
	public static final float GOD_MODE_DAMAGE_FACTOR = 100f;
	private static final float SUNRISE = 6f;
	private static final float SUNSET = 18f;

	public enum Code {
		GREEDISGOOD,
		KEYSERSOZE,
		LEAFITTOME,
		WHOSYOURDADDY,
		ISEEDEADPEOPLE,
		POINTBREAK,
		THEREISNOSPOON,
		WARPTEN,
		SYNERGY,
		SHARPANDSHINY,
		ALLYOURBASEAREBELONGTOUS,
		SOMEBODYSETUPUSTHEBOMB,
		STRENGTHANDHONOR,
		ITVEXESME,
		RISEANDSHINE,
		LIGHTSOUT,
		DAYLIGHTSAVINGS,
		WHOISJOHNGALT,
		THEDUDEABIDES,
		IOCAINEPOWDER,
		MOTHERLAND;

		private static final Code[] VALUES = values();

		public String text() {
			return name().toLowerCase(Locale.US);
		}

		static Code of(final String text) {
			for (final Code code : VALUES) {
				if (code.text().equals(text)) {
					return code;
				}
			}
			return null;
		}
	}

	/** What the typing player is told; IGNORED shows nothing. */
	public enum Result {
		ENABLED("CHEATENABLED"),
		DISABLED("CHEATDISABLED"),
		IGNORED(null);

		private final String messageKey;

		Result(final String messageKey) {
			this.messageKey = messageKey;
		}

		public String getMessageKey() {
			return this.messageKey;
		}
	}

	/** A chat line that is a cheat: its code and whatever follows it, both lower case and trimmed. */
	public static final class Parsed {
		private final Code code;
		private final String argument;

		Parsed(final Code code, final String argument) {
			this.code = code;
			this.argument = argument;
		}

		public Code getCode() {
			return this.code;
		}

		public String getArgument() {
			return this.argument;
		}

		/** The amount of a resource cheat, or -1 when the argument is not one. */
		int amount() {
			if (this.argument.isEmpty()) {
				return DEFAULT_RESOURCES;
			}
			try {
				return Integer.parseInt(this.argument);
			}
			catch (final NumberFormatException e) {
				return -1;
			}
		}

		/** The hour of daylightsavings N, NaN without one, or null when it is not a number. */
		Float hour() {
			if (this.argument.isEmpty()) {
				return Float.NaN;
			}
			try {
				final float hour = Float.parseFloat(this.argument);
				return Float.isFinite(hour) && (hour >= 0) && (hour < 24) ? hour : null;
			}
			catch (final NumberFormatException e) {
				return null;
			}
		}

		/** The campaign and mission number of motherland, or null when they do not name one. */
		public String[] motherland() {
			final String[] parts = this.argument.split("\\s+");
			if ((parts.length != 2) || !isCampaignRace(parts[0])) {
				return null;
			}
			try {
				return Integer.parseInt(parts[1]) > 0 ? parts : null;
			}
			catch (final NumberFormatException e) {
				return null;
			}
		}

		private static boolean isCampaignRace(final String race) {
			switch (race) {
			case "human":
			case "orc":
			case "undead":
			case "nightelf":
			case "humanx":
			case "orcx":
			case "undeadx":
			case "nightelfx":
				return true;
			default:
				return false;
			}
		}
	}

	private CReleaseCheats() {
	}

	/** The cheat a chat line names, or null when the line is ordinary chat. */
	public static Parsed parse(final String text) {
		if (text == null) {
			return null;
		}
		final String line = text.trim().toLowerCase(Locale.US);
		final int space = indexOfWhitespace(line);
		final Code code = Code.of(space < 0 ? line : line.substring(0, space));
		if (code == null) {
			return null;
		}
		return new Parsed(code, space < 0 ? "" : line.substring(space + 1).trim());
	}

	private static int indexOfWhitespace(final String line) {
		for (int i = 0; i < line.length(); i++) {
			if (Character.isWhitespace(line.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Cheats only work in a game with a single human player; a replay of such a game runs them again from its command
	 * stream.
	 */
	public static boolean isSinglePlayer(final CSimulation simulation, final int playerIndex) {
		int humans = 0;
		boolean issuerIsHuman = false;
		for (int i = 0; i < simulation.getGameData().getMaxPlayers(); i++) {
			final CPlayer player = simulation.getPlayer(i);
			if ((player.getController() == CMapControl.USER) && (player.getSlotState() != null)
					&& (player.getSlotState() != CPlayerSlotState.EMPTY)) {
				humans++;
				issuerIsHuman |= i == playerIndex;
			}
		}
		return (humans == 1) && issuerIsHuman;
	}

	/** What applying the line would tell its player, without applying it. */
	public static Result preview(final CSimulation simulation, final int playerIndex, final String text) {
		final Parsed parsed = parse(text);
		if (parsed == null) {
			return Result.IGNORED;
		}
		final CPlayer player = simulation.getPlayer(playerIndex);
		switch (parsed.getCode()) {
		case GREEDISGOOD:
		case KEYSERSOZE:
		case LEAFITTOME:
			return parsed.amount() < 0 ? Result.IGNORED : Result.ENABLED;
		case DAYLIGHTSAVINGS: {
			final Float hour = parsed.hour();
			if (hour == null) {
				return Result.IGNORED;
			}
			return toggledOn(!hour.isNaN() || !simulation.isTimeOfDaySuspended());
		}
		case MOTHERLAND:
			return parsed.motherland() == null ? Result.IGNORED : Result.ENABLED;
		case ISEEDEADPEOPLE:
			return toggledOn(!simulation.isCheatRevealMap());
		case WARPTEN:
			return toggledOn(simulation.getCheatBuildSpeed() == 1.0f);
		default:
			break;
		}
		final int flag = flag(parsed.getCode());
		return flag == 0 ? Result.ENABLED : toggledOn(!player.hasCheat(flag));
	}

	private static Result toggledOn(final boolean on) {
		return on ? Result.ENABLED : Result.DISABLED;
	}

	private static int flag(final Code code) {
		switch (code) {
		case WHOSYOURDADDY:
			return GOD_MODE;
		case POINTBREAK:
			return NO_FOOD_LIMIT;
		case THEREISNOSPOON:
			return NO_MANA_COST;
		case SYNERGY:
			return NO_TECH_REQUIREMENTS;
		case STRENGTHANDHONOR:
			return NO_DEFEAT;
		case ITVEXESME:
			return NO_VICTORY;
		case WHOISJOHNGALT:
			return FAST_RESEARCH;
		case THEDUDEABIDES:
			return NO_COOLDOWNS;
		case IOCAINEPOWDER:
			return FAST_DEATH;
		default:
			return 0;
		}
	}

	/**
	 * A chat cheat typed by a player, as the command stream carries it. Ignored unless the game has that one human
	 * player. motherland leaves the game and is handled where the game is left; here it does nothing.
	 */
	public static Result apply(final CSimulation simulation, final int playerIndex, final String text) {
		final Parsed parsed = parse(text);
		if ((parsed == null) || !isSinglePlayer(simulation, playerIndex)) {
			return Result.IGNORED;
		}
		final Result result = preview(simulation, playerIndex, text);
		if (result == Result.IGNORED) {
			return result;
		}
		final boolean on = result == Result.ENABLED;
		final CPlayer issuer = simulation.getPlayer(playerIndex);
		switch (parsed.getCode()) {
		case GREEDISGOOD:
		case KEYSERSOZE:
		case LEAFITTOME:
			giveResources(simulation, parsed.getCode(), parsed.amount());
			break;
		case ISEEDEADPEOPLE:
			simulation.toggleCheatRevealMap();
			break;
		case WARPTEN:
			simulation.toggleCheatBuildSpeed();
			break;
		case WHOSYOURDADDY:
			issuer.setCheat(GOD_MODE, on);
			break;
		case THEREISNOSPOON:
			// Measured on 1.29: Storm Bolt then casts again 2.5 s later, and thedudeabides after it takes the
			// cooldowns back.
			setForEveryPlayer(simulation, NO_MANA_COST, on);
			setForEveryPlayer(simulation, NO_COOLDOWNS, on);
			break;
		case SHARPANDSHINY:
			researchUpgrades(simulation);
			break;
		case RISEANDSHINE:
			simulation.setGameTimeOfDay(SUNRISE);
			break;
		case LIGHTSOUT:
			simulation.setGameTimeOfDay(SUNSET);
			break;
		case DAYLIGHTSAVINGS: {
			final Float hour = parsed.hour();
			if (hour.isNaN()) {
				simulation.setTimeOfDaySuspended(on);
			}
			else {
				simulation.setGameTimeOfDay(hour);
			}
			break;
		}
		case ALLYOURBASEAREBELONGTOUS:
			issuer.firePlayerEvents(CommonTriggerExecutionScope::triggerPlayerScope,
					JassGameEventsWar3.EVENT_PLAYER_VICTORY);
			break;
		case SOMEBODYSETUPUSTHEBOMB:
			issuer.firePlayerEvents(CommonTriggerExecutionScope::triggerPlayerScope,
					JassGameEventsWar3.EVENT_PLAYER_DEFEAT);
			break;
		case MOTHERLAND:
			break;
		default:
			setForEveryPlayer(simulation, flag(parsed.getCode()), on);
			break;
		}
		return result;
	}

	/**
	 * The Cheat native. Measured on 1.29: greedisgood, keysersoze, leafittome, iseedeadpeople and warpten work from a
	 * script; itvexesme and strengthandhonor leave IsNoVictoryCheat and IsNoDefeatCheat false.
	 */
	public static void apply(final CSimulation simulation, final String text) {
		final Parsed parsed = parse(text);
		if (parsed == null) {
			return;
		}
		switch (parsed.getCode()) {
		case GREEDISGOOD:
		case KEYSERSOZE:
		case LEAFITTOME:
			if (!parsed.getArgument().isEmpty() && (parsed.amount() >= 0)) {
				giveResources(simulation, parsed.getCode(), parsed.amount());
			}
			break;
		case ISEEDEADPEOPLE:
			simulation.toggleCheatRevealMap();
			break;
		case WARPTEN:
			simulation.toggleCheatBuildSpeed();
			break;
		default:
			break;
		}
	}

	private static void giveResources(final CSimulation simulation, final Code code, final int amount) {
		for (int i = 0; i < simulation.getGameData().getMaxPlayers(); i++) {
			final CPlayer player = simulation.getPlayer(i);
			if (code != Code.LEAFITTOME) {
				player.addGold(amount);
			}
			if (code != Code.KEYSERSOZE) {
				player.addLumber(amount);
			}
		}
	}

	private static void setForEveryPlayer(final CSimulation simulation, final int flag, final boolean on) {
		for (int i = 0; i < simulation.getGameData().getMaxPlayers(); i++) {
			simulation.getPlayer(i).setCheat(flag, on);
		}
	}

	/** Measured on 1.29: one more level of every upgrade, for every player. */
	private static void researchUpgrades(final CSimulation simulation) {
		for (final War3ID upgradeId : simulation.getUpgradeData().getTypeIds()) {
			final CUpgradeType upgradeType = simulation.getUpgradeData().getType(upgradeId);
			if (upgradeType == null) {
				continue;
			}
			for (int i = 0; i < simulation.getGameData().getMaxPlayers(); i++) {
				final CPlayer player = simulation.getPlayer(i);
				final int level = player.getTechtreeUnlocked(upgradeId);
				final int maxAllowed = player.getTechtreeMaxAllowed(upgradeId);
				if ((level < upgradeType.getLevelCount()) && ((maxAllowed < 0) || (level < maxAllowed))) {
					player.addTechResearched(simulation, upgradeId, 1);
				}
			}
		}
	}

	/** The mana a spell costs its caster: nothing while thereisnospoon is on. */
	public static int manaCost(final CSimulation simulation, final CUnit caster, final int manaCost) {
		return isManaFree(simulation, caster) ? 0 : manaCost;
	}

	public static boolean isManaFree(final CSimulation simulation, final CUnit caster) {
		return simulation.getPlayer(caster.getPlayerIndex()).hasCheat(NO_MANA_COST);
	}

	public static boolean isNoDefeat(final CSimulation simulation) {
		return anyPlayerHas(simulation, NO_DEFEAT);
	}

	public static boolean isNoVictory(final CSimulation simulation) {
		return anyPlayerHas(simulation, NO_VICTORY);
	}

	private static boolean anyPlayerHas(final CSimulation simulation, final int flag) {
		for (int i = 0; i < simulation.getGameData().getMaxPlayers(); i++) {
			if (simulation.getPlayer(i).hasCheat(flag)) {
				return true;
			}
		}
		return false;
	}
}
