package com.etheller.warsmash.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.units.ObjectData;
import com.etheller.warsmash.units.StandardObjectData;
import com.etheller.warsmash.util.StringBundle;
import com.etheller.warsmash.util.WarsmashConstants;

/**
 * The command cards of the melee races as the game data has them, for the hotkey editor: each race's units, heroes
 * and buildings, reached from its town hall through what it trains, builds and upgrades to, with every card's
 * commands, their places, keys, icons and tooltips.
 */
public final class HotkeyCatalog {
	/** The town halls the races start with: Human, Orc, Undead, Night Elf. */
	private static final String[] HALLS = { "htow", "ogre", "unpl", "etol" };
	private static final String[] RACE_NAMES = { "HUMAN", "ORC", "UNDEAD", "NIGHT_ELF" };
	private static final String[] BASE = { "CmdMove", "CmdStop", "CmdHoldPos", "CmdAttack", "CmdPatrol" };
	private static final Map<String, String> BUILD_COMMANDS = new HashMap<>();
	static {
		BUILD_COMMANDS.put("AHbu", "CmdBuildHuman");
		BUILD_COMMANDS.put("AObu", "CmdBuildOrc");
		BUILD_COMMANDS.put("AUbu", "CmdBuildUndead");
		BUILD_COMMANDS.put("AEbu", "CmdBuildNightElf");
		BUILD_COMMANDS.put("AGbu", "CmdBuild");
	}

	/** A button: what CustomKeys.txt calls it, its place on the card, its key in the game data and how it looks. */
	public static final class Command {
		public final String section;
		public final String field;
		public final String name;
		public final String dataKey;
		public final int x;
		public final int y;
		public final String icon;
		public final String tip;
		public final String text;

		Command(final String section, final String field, final String name, final String dataKey, final int x,
				final int y, final String icon, final String tip, final String text) {
			this.section = section;
			this.field = field;
			this.name = name;
			this.dataKey = dataKey == null ? "" : dataKey;
			this.x = x;
			this.y = y;
			this.icon = icon;
			this.tip = tip == null ? "" : tip;
			this.text = text == null ? "" : text;
		}

		/** The same button of the same command: one key for all cards it is on. */
		public String id() {
			return this.section + "|" + this.field;
		}
	}

	/** One command card: a unit's own, its build list or a hero's Learn list. */
	public static final class Card {
		public final String name;
		public final List<Command> commands = new ArrayList<>();

		Card(final String name) {
			this.name = name;
		}
	}

	public static final class Unit {
		public final String id;
		public final String name;
		public final String icon;
		public final List<Card> cards = new ArrayList<>();

		Unit(final String id, final String name, final String icon) {
			this.id = id;
			this.name = name;
			this.icon = icon;
		}
	}

	public static final class Race {
		/** The glue key of the race's name; null for the commands all races share. */
		public final String name;
		public final List<Unit> units = new ArrayList<>();

		Race(final String name) {
			this.name = name;
		}
	}

	public final List<Race> races = new ArrayList<>();
	private final ObjectData units;
	private final ObjectData abilities;
	private final ObjectData upgrades;
	private final DataTable commands;
	private final Element skin;
	/** The names of the cards and of the shared lists, as the launcher words them. */
	private final Map<String, String> words;

	private HotkeyCatalog(final DataSource data, final Map<String, String> words) throws IOException {
		this.words = words;
		final StandardObjectData standard = new StandardObjectData(data);
		this.units = standard.getStandardUnits();
		this.abilities = standard.getStandardAbilities();
		this.upgrades = standard.getStandardUpgrades();
		this.commands = new DataTable(StringBundle.EMPTY);
		for (final String file : new String[] { "Units\\CommandFunc.txt", "Units\\CommandStrings.txt" }) {
			try (InputStream in = data.getResourceAsStream(file)) {
				if (in != null) {
					this.commands.readTXT(in, true);
				}
			}
		}
		this.skin = GameUI.loadSkin(data, WarsmashConstants.GAME_VERSION).getSkin();
	}

	/**
	 * Reads the cards from the game data.
	 *
	 * @param words the launcher's words for "unit", "structure", "build", "learn" and the shared lists, by those keys
	 */
	public static HotkeyCatalog read(final DataSource data, final Map<String, String> words) throws IOException {
		final HotkeyCatalog catalog = new HotkeyCatalog(data, words);
		catalog.readShared();
		for (int i = 0; i < HALLS.length; i++) {
			catalog.readRace(RACE_NAMES[i], HALLS[i]);
		}
		return catalog;
	}

	private String word(final String key) {
		final String word = this.words.get(key);
		return word != null ? word : key;
	}

	private void readShared() {
		final Race shared = new Race(null);
		final Unit unitCommands = new Unit("units", word("shared_units"), icon(command("CmdMove")));
		final Card card = new Card(word("card_commands"));
		for (final String name : BASE) {
			addCommand(card, name);
		}
		addCommand(card, "CmdSelectSkill");
		unitCommands.cards.add(card);
		final Card siege = new Card(word("card_siege"));
		for (final String name : BASE) {
			addCommand(siege, name);
		}
		addCommand(siege, "CmdAttackGround");
		unitCommands.cards.add(siege);
		shared.units.add(unitCommands);
		final Unit structureCommands = new Unit("structures", word("shared_structures"), icon(command("CmdRally")));
		final Card building = new Card(word("card_commands"));
		addCommand(building, "CmdRally");
		addCommand(building, "CmdCancelTrain");
		structureCommands.cards.add(building);
		final Card construction = new Card(word("card_construction"));
		addCommand(construction, "CmdCancelBuild");
		structureCommands.cards.add(construction);
		shared.units.add(structureCommands);
		this.races.add(shared);
	}

	private void readRace(final String name, final String hall) {
		final Race race = new Race(name);
		final List<String> reached = new ArrayList<>();
		final Set<String> buildings = new LinkedHashSet<>();
		final Deque<String> next = new ArrayDeque<>();
		next.add(hall);
		buildings.add(hall);
		while (!next.isEmpty()) {
			final String id = next.poll();
			if (reached.contains(id) || (this.units.get(id) == null)) {
				continue;
			}
			reached.add(id);
			final GameObject unit = this.units.get(id);
			for (final String trained : list(unit, "Trains")) {
				next.add(trained);
			}
			for (final String built : list(unit, "Builds")) {
				buildings.add(built);
				next.add(built);
			}
			for (final String upgraded : list(unit, "Upgrade")) {
				buildings.add(upgraded);
				next.add(upgraded);
			}
		}
		final List<Unit> plain = new ArrayList<>();
		final List<Unit> heroes = new ArrayList<>();
		final List<Unit> structures = new ArrayList<>();
		for (final String id : reached) {
			final GameObject unit = this.units.get(id);
			final boolean building = buildings.contains(id);
			final boolean hero = Character.isUpperCase(id.charAt(0));
			final Unit entry = new Unit(id, unitName(unit), icon(unit, "Art"));
			if (building) {
				entry.cards.add(buildingCard(unit));
			}
			else {
				unitCards(entry, unit, hero);
			}
			if (entry.cards.isEmpty() || entry.cards.get(0).commands.isEmpty()) {
				continue;
			}
			final List<Unit> group = building ? structures : hero ? heroes : plain;
			// An upgrade that swaps a unit for another type (the Siege Engine with Barrage) keeps its name and card:
			// for the player it is one unit, so its buttons join the first one's.
			final Unit same = named(group, entry.name);
			if (same != null) {
				merge(same, entry);
				continue;
			}
			group.add(entry);
		}
		race.units.addAll(plain);
		race.units.addAll(heroes);
		race.units.addAll(structures);
		this.races.add(race);
	}

	private static Unit named(final List<Unit> group, final String name) {
		for (final Unit unit : group) {
			if (unit.name.equals(name)) {
				return unit;
			}
		}
		return null;
	}

	private static void merge(final Unit into, final Unit from) {
		for (int i = 0; i < from.cards.size(); i++) {
			final Card card = from.cards.get(i);
			if (i >= into.cards.size()) {
				into.cards.add(card);
				continue;
			}
			final Card target = into.cards.get(i);
			for (final Command command : card.commands) {
				boolean known = false;
				for (final Command existing : target.commands) {
					known |= existing.id().equalsIgnoreCase(command.id());
				}
				if (!known) {
					target.commands.add(command);
				}
			}
		}
	}

	private void unitCards(final Unit entry, final GameObject unit, final boolean hero) {
		final Card card = new Card(word("card_commands"));
		for (final String name : BASE) {
			if ("CmdAttack".equals(name) && (unit.getFieldAsInteger("weapsOn", 0) == 0)) {
				continue;
			}
			addCommand(card, name);
		}
		Card build = null;
		for (final String ability : list(unit, "abilList")) {
			final String buildCommand = BUILD_COMMANDS.get(ability);
			if (buildCommand != null) {
				addCommand(card, buildCommand);
				build = new Card(word("card_build"));
				for (final String structure : list(unit, "Builds")) {
					addUnit(build, structure);
				}
				addCommand(build, "CmdCancel");
				continue;
			}
			addAbility(card, ability, CustomKeys.HOTKEY);
		}
		entry.cards.add(card);
		if (hero) {
			addCommand(card, "CmdSelectSkill");
			final Card learn = new Card(word("card_learn"));
			for (final String ability : list(unit, "heroAbilList")) {
				addAbility(card, ability, CustomKeys.HOTKEY);
				addAbility(learn, ability, CustomKeys.RESEARCH);
			}
			addCommand(learn, "CmdCancel");
			entry.cards.add(learn);
		}
		if (build != null) {
			entry.cards.add(build);
		}
	}

	private Card buildingCard(final GameObject unit) {
		final Card card = new Card(word("card_commands"));
		final List<String> trains = list(unit, "Trains");
		for (final String trained : trains) {
			addUnit(card, trained);
		}
		for (final String upgraded : list(unit, "Upgrade")) {
			addUnit(card, upgraded);
		}
		for (final String research : list(unit, "Researches")) {
			final GameObject upgrade = this.upgrades.get(research);
			if (upgrade != null) {
				add(card, research, CustomKeys.HOTKEY, upgrade.getFieldAsString("Hotkey", 0),
						upgrade.getFieldAsInteger("Buttonpos", 0), upgrade.getFieldAsInteger("Buttonpos", 1),
						icon(upgrade, "Art"), upgrade.getFieldAsString("Tip", 0), upgrade.getFieldAsString("Ubertip", 0));
			}
		}
		for (final String ability : list(unit, "abilList")) {
			addAbility(card, ability, CustomKeys.HOTKEY);
		}
		if (!trains.isEmpty()) {
			addCommand(card, "CmdRally");
		}
		return card;
	}

	private void addUnit(final Card card, final String id) {
		final GameObject unit = this.units.get(id);
		if (unit != null) {
			add(card, id, CustomKeys.HOTKEY, unit.getFieldAsString("Hotkey", 0), unit.getFieldAsInteger("Buttonpos", 0),
					unit.getFieldAsInteger("Buttonpos", 1), icon(unit, "Art"), unit.getFieldAsString("Tip", 0),
					unit.getFieldAsString("Ubertip", 0));
		}
	}

	private void addAbility(final Card card, final String id, final String field) {
		final GameObject ability = this.abilities.get(id);
		if (ability == null) {
			return;
		}
		final boolean learn = CustomKeys.RESEARCH.equals(field);
		final String place = learn ? "Researchbuttonpos" : "Buttonpos";
		add(card, id, field, ability.getFieldAsString(field, 0), ability.getFieldAsInteger(place, 0),
				ability.getFieldAsInteger(place, 1), icon(ability, learn ? "ResearchArt" : "Art"),
				ability.getFieldAsString(learn ? "Researchtip" : "Tip", 0),
				ability.getFieldAsString(learn ? "Researchubertip" : "Ubertip", 0));
		final String off = ability.getFieldAsString(CustomKeys.OFF, 0);
		if (!learn && !off.isEmpty() && !off.equalsIgnoreCase(ability.getFieldAsString(field, 0))) {
			add(card, id, CustomKeys.OFF, off, -1, -1, icon(ability, "Unart"), ability.getFieldAsString("Untip", 0),
					ability.getFieldAsString("Unubertip", 0));
		}
	}

	private void addCommand(final Card card, final String name) {
		final Element command = command(name);
		if (command == null) {
			return;
		}
		add(card, name, CustomKeys.HOTKEY, command.getField("Hotkey"), command.getFieldValue("Buttonpos", 0),
				command.getFieldValue("Buttonpos", 1), icon(command), command.getField("Tip"),
				command.getField("UberTip"));
	}

	/** Buttons without a key, such as auras, and buttons off the card are not hotkeys. */
	private void add(final Card card, final String section, final String field, final String dataKey, final int x,
			final int y, final String icon, final String tip, final String text) {
		if ((dataKey == null) || dataKey.isEmpty()) {
			return;
		}
		if ((x >= 0) && ((x > 3) || (y < 0) || (y > 2))) {
			return;
		}
		for (final Command existing : card.commands) {
			if (existing.id().equalsIgnoreCase(section + "|" + field)) {
				return;
			}
		}
		card.commands.add(new Command(section, field, name(tip, section), dataKey, x, y, icon, tip,
				text == null ? "" : resolve(text.replace("|n", "\n").replace("|N", "\n"))));
	}

	private Element command(final String name) {
		return this.commands.get(name);
	}

	private String icon(final Element command) {
		return command == null ? null : skinPath(command.getField("Art"));
	}

	private String icon(final GameObject object, final String field) {
		return skinPath(object.getFieldAsString(field, 0));
	}

	/** An icon's path without its extension, as the launcher's pictures are asked for; skin keys resolved. */
	private String skinPath(final String art) {
		if ((art == null) || art.isEmpty()) {
			return null;
		}
		String path = GameUI.trySkinField(this.skin, art);
		final int dot = path.lastIndexOf('.');
		if (dot > path.lastIndexOf('\\')) {
			path = path.substring(0, dot);
		}
		return path;
	}

	private static String unitName(final GameObject unit) {
		final String name = unit.getFieldAsString("Name", 0);
		return name.isEmpty() ? unit.getId() : name;
	}

	/** A command's name from its tooltip title: no colours, key mark or level. */
	static String name(final String tip, final String fallback) {
		if ((tip == null) || tip.isEmpty()) {
			return fallback;
		}
		String name = GlueText.plain(tip);
		for (final String end : new String[] { " - [", " (" }) {
			final int at = name.indexOf(end);
			if (at > 0) {
				name = name.substring(0, at);
			}
		}
		return name.trim();
	}

	/** The tooltip text's references to the data, such as &lt;AHhb,DataA1&gt;, with their values. */
	private String resolve(final String text) {
		final StringBuilder out = new StringBuilder();
		int at = 0;
		while (at < text.length()) {
			final int open = text.indexOf('<', at);
			final int close = open < 0 ? -1 : text.indexOf('>', open);
			if ((open < 0) || (close < 0)) {
				out.append(text, at, text.length());
				break;
			}
			out.append(text, at, open);
			final String[] parts = text.substring(open + 1, close).split(",");
			if ((parts.length == 2) || (parts.length == 3)) {
				GameObject object = this.units.get(parts[0]);
				if (object == null) {
					object = this.abilities.get(parts[0]);
				}
				if (object != null) {
					if ((parts.length == 3) && "%".equals(parts[2])) {
						out.append((int) (object.readSLKTagFloat(parts[1]) * 100f));
					}
					else {
						out.append(number(object.readSLKTag(parts[1])));
					}
				}
			}
			at = close + 1;
		}
		return out.toString();
	}

	private static String number(final String value) {
		try {
			final float number = Float.parseFloat(value);
			if (number == Math.rint(number)) {
				return Integer.toString((int) number);
			}
			return String.format(Locale.ROOT, "%.2f", number).replaceAll("0+$", "");
		}
		catch (final NumberFormatException e) {
			return value;
		}
	}

	private static List<String> list(final GameObject object, final String field) {
		final String value = object.getField(field);
		if ((value == null) || value.isEmpty() || "_".equals(value)) {
			return Collections.emptyList();
		}
		final List<String> found = new ArrayList<>();
		for (final String part : value.split(",")) {
			final String id = part.trim().replace("\"", "");
			if ((id.length() == 4) && !found.contains(id)) {
				found.add(id);
			}
		}
		return found;
	}

	/**
	 * The commands of a card that share a key with another of its commands. Buttons of one place are never shown
	 * together (a unit and the one it becomes), and the turn-off keys are not compared.
	 */
	public static Set<String> conflicts(final Card card, final CustomKeys keys) {
		final Map<String, List<Command>> byKey = new HashMap<>();
		for (final Command command : card.commands) {
			if (command.x < 0) {
				continue;
			}
			final String key = CustomKeys.shown(keys.key(command.section, command.field, command.dataKey, command.x,
					command.y));
			byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(command);
		}
		final Set<String> conflicts = new LinkedHashSet<>();
		for (final List<Command> same : byKey.values()) {
			final Set<Integer> places = new LinkedHashSet<>();
			for (final Command command : same) {
				places.add((command.y * 4) + command.x);
			}
			if (places.size() > 1) {
				for (final Command command : same) {
					conflicts.add(command.id());
				}
			}
		}
		return conflicts;
	}
}
