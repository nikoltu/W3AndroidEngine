package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.CRC32;

import com.etheller.interpreter.ast.function.ConvertedConstant;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.GlobalScopeAssignable;
import com.etheller.interpreter.ast.util.CHandle;
import com.etheller.interpreter.ast.value.ArrayJassValue;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.warsmash.parsers.jass.JassAIEnvironment;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CLevelingAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CPathfindingProcessor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CPathfindingProcessor.PathfindingJob;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

/**
 * Determinism check: with -Dwarsmash.checksum=<file> (or Checksum= in the ini's [Emulator] section) every
 * {@link #INTERVAL} steps one line "tick crc32 category=crc32..." of the simulation state is written to the file.
 * Two runs of the same game must write the same lines; -Dwarsmash.checksum.detail=<tick>[,<first>-<last>...]
 * (ChecksumDetail=) also writes every hashed object at those steps to <file>.detail-<tick>.txt, to see which
 * object went apart first.
 *
 * Floats are hashed by their bits and objects in handle id order, so the lines of a desktop and a device run can be
 * compared too. Off, the step hook only reads one static field.
 */
public final class SimulationChecksum {
	public static final String PROPERTY = "warsmash.checksum";
	public static final String DETAIL_PROPERTY = "warsmash.checksum.detail";
	public static final int INTERVAL = 20;

	private static final String COMMAND_LINE_FILE = System.getProperty(PROPERTY);
	private static final String COMMAND_LINE_DETAIL = System.getProperty(DETAIL_PROPERTY);
	private static final String[] CATEGORIES = { "units", "order", "players", "items", "destructables",
			"projectiles", "timers", "random", "handles", "pathing", "jass", "ai", "tick" };

	private static boolean enabled = notEmpty(COMMAND_LINE_FILE);
	private static String fileName = COMMAND_LINE_FILE;
	private static Set<Integer> detailTicks = parseTicks(COMMAND_LINE_DETAIL);
	private static Writer writer;
	private static CSimulation current;
	private static int games;
	/** The file this process started; later games are added to it. */
	private static String startedFile;
	private static boolean shutdownHook;
	/** The game that ended in the step being run; its last step gets a line too. */
	private static CSimulation ended;

	/** Lines per cached block; a block with one changed line is hashed again line by line. */
	private static final int BLOCK = 64;
	/**
	 * What seldom changes (script globals, destructables) is kept hashed in blocks of lines, by the object: a block
	 * whose values are all the same is joined to the checksum by its own checksum, without hashing its lines again.
	 * Most of a game's lines are script constants and trees. The blocks hold the game's own objects, which reach the
	 * game again, so they are let go when the game is closed rather than by a weak key that would never be cleared.
	 */
	private static final Map<CSimulation, GameLines> GAME_LINES = new HashMap<>();

	private final int[] crcs = new int[CATEGORIES.length];
	private int total;
	private final CRC32 segment = new CRC32();
	private boolean capturing;
	private int capturedCrc;
	private long capturedLength;

	private final StringBuilder line = new StringBuilder(256);
	private final StringBuilder readable = new StringBuilder();
	private final ByteArrayOutputStream randomBytes = new ByteArrayOutputStream(256);
	private byte[] bytes = new byte[256];
	private Writer detail;

	private SimulationChecksum() {
	}

	/**
	 * The ini values win over the command line; without them the command line values stay, also when the process
	 * outlives one game (Android) and a later launch reads an ini without them.
	 */
	public static void configure(final String iniFile, final String iniDetail) {
		final String file = notEmpty(iniFile) ? unquote(iniFile) : COMMAND_LINE_FILE;
		final String detailValue = notEmpty(iniDetail) ? unquote(iniDetail) : COMMAND_LINE_DETAIL;
		if (notEmpty(file)) {
			System.setProperty(PROPERTY, file);
		}
		else {
			System.clearProperty(PROPERTY);
		}
		if (notEmpty(detailValue)) {
			System.setProperty(DETAIL_PROPERTY, detailValue);
		}
		else {
			System.clearProperty(DETAIL_PROPERTY);
		}
		synchronized (SimulationChecksum.class) {
			if ((writer != null) && !String.valueOf(file).equals(fileName)) {
				close();
			}
			fileName = file;
			detailTicks = parseTicks(detailValue);
			enabled = notEmpty(file);
		}
	}

	/**
	 * The checksum of the game's state now, the total of a checksum line; for a replay, which keeps its own.
	 */
	public static int compute(final CSimulation simulation) {
		final SimulationChecksum checksum = new SimulationChecksum();
		try {
			checksum.hash(simulation);
		}
		catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
		return checksum.total;
	}

	public static void afterStep(final CSimulation simulation) {
		if (enabled) {
			record(simulation);
		}
	}

	private static synchronized void record(final CSimulation simulation) {
		try {
			final int tick = simulation.getGameTurnTick();
			if ((simulation != current) || (writer == null)) {
				// A loaded save is a new simulation object that goes on from its saved step.
				startGame(simulation, tick);
			}
			final boolean last = ended == simulation;
			if (last) {
				ended = null;
				writer.write("# end tick " + tick + "\n");
			}
			if (((tick % INTERVAL) != 0) && !last) {
				return;
			}
			final SimulationChecksum checksum = new SimulationChecksum();
			if (detailTicks.contains(tick)) {
				final File detailFile = new File(fileName + ".detail-" + (games > 1 ? games + "-" : "") + tick + ".txt");
				checksum.detail = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(detailFile), StandardCharsets.UTF_8));
			}
			try {
				checksum.hash(simulation);
			}
			finally {
				if (checksum.detail != null) {
					checksum.detail.close();
				}
			}
			writer.write(checksum.summary(tick));
			writer.flush();
		}
		catch (final IOException e) {
			enabled = false;
			Log.error(e);
		}
	}

	/**
	 * Each game opens the file again, after the games this process wrote to it before: a file taken away between two
	 * games of one process (a test tool's, on Android) is made again rather than written past.
	 */
	private static void startGame(final CSimulation simulation, final int tick) throws IOException {
		if (writer != null) {
			close();
		}
		final File file = new File(fileName);
		final File parent = file.getAbsoluteFile().getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}
		final boolean append = fileName.equals(startedFile) && file.isFile();
		if (!append) {
			games = 0;
		}
		writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, append), StandardCharsets.UTF_8));
		startedFile = fileName;
		if (!shutdownHook) {
			shutdownHook = true;
			Runtime.getRuntime().addShutdownHook(new Thread(SimulationChecksum::closeAtExit, "checksum close"));
		}
		current = simulation;
		games++;
		writer.write("# game " + games + " tick " + tick + "\n");
		writer.flush();
	}

	/**
	 * The game ended (EndGame, a quit) in this step: the step's line is written whatever its number, so that the file
	 * covers the game to its end and two runs that end on different steps differ.
	 */
	public static synchronized void gameEnded(final CSimulation simulation) {
		if (enabled && (writer != null) && (current == simulation)) {
			ended = simulation;
		}
	}

	/** The game is left: its file is closed, whichever way the game ended. */
	public static synchronized void gameClosed(final CSimulation simulation) {
		if ((writer != null) && (current == simulation)) {
			if (ended == simulation) {
				// The game ended outside a step, so no step wrote its last line.
				record(simulation);
			}
			close();
		}
		if (ended == simulation) {
			ended = null;
		}
		synchronized (GAME_LINES) {
			GAME_LINES.remove(simulation);
		}
	}

	private static GameLines gameLines(final CSimulation simulation) {
		synchronized (GAME_LINES) {
			return GAME_LINES.computeIfAbsent(simulation, key -> new GameLines());
		}
	}

	private static synchronized void closeAtExit() {
		if (writer != null) {
			close();
		}
	}

	private static void close() {
		try {
			writer.close();
		}
		catch (final IOException e) {
			Log.error(e);
		}
		writer = null;
		current = null;
	}

	private String summary(final int tick) {
		final StringBuilder sb = new StringBuilder();
		sb.append(tick).append(' ').append(hex(this.total));
		for (int i = 0; i < CATEGORIES.length; i++) {
			sb.append(' ').append(CATEGORIES[i]).append('=').append(hex(this.crcs[i]));
		}
		return sb.append('\n').toString();
	}

	private void hash(final CSimulation simulation) throws IOException {
		hashUnits(simulation);
		hashPlayers(simulation);
		hashItems(simulation);
		hashDestructables(simulation);
		hashProjectiles(simulation);
		hashTimers(simulation);
		hashRandom(simulation);
		final HandleIdAllocator handles = simulation.getHandleIdAllocator();
		begin(8, "handles", "next").i("next", handles.getNextId()).i("handle", handles.getNextHandleId())
				.i("free", handles.getFreeHandleIdCount()).end();
		hashPathing(simulation);
		hashJass(10, "jass", simulation, simulation.getGlobalScope());
		for (final CPlayer player : simulation.getPlayers()) {
			final JassAIEnvironment ai = player.getAiScript();
			if (ai != null) {
				hashJass(11, "ai" + player.getId(), simulation, ai.getGlobalScope());
			}
		}
		begin(12, "tick", "tick").i("tick", simulation.getGameTurnTick()).end();
		flush();
	}

	private void hashUnits(final CSimulation simulation) throws IOException {
		final List<CUnit> units = simulation.getUnits();
		begin(1, "order", "units").i("count", units.size());
		for (final CUnit unit : units) {
			this.line.append(unit.getHandleId()).append(',');
			if (this.detail != null) {
				this.readable.append(unit.getHandleId()).append(',');
			}
		}
		end();
		final CUnit[] sorted = units.toArray(new CUnit[0]);
		Arrays.sort(sorted, Comparator.comparingInt(CUnit::getHandleId));
		for (final CUnit unit : sorted) {
			begin(0, "unit", unit.getHandleId()).id("type", unit.getTypeId()).i("owner", unit.getPlayerIndex())
					.f("x", unit.getX()).f("y", unit.getY()).f("facing", unit.getFacing())
					.f("fly", unit.getFlyHeight()).f("life", unit.getLife()).f("mana", unit.getMana())
					.b("dead", unit.isDead()).b("hidden", unit.isHidden()).b("paused", unit.isPaused())
					.f("build", unit.getConstructionProgress()).i("gold", unit.getGold())
					.i("userData", unit.getTriggerEditorCustomValue());
			final CBehavior behavior = unit.getCurrentBehavior();
			s("behavior", SimulationTypeCodes.of(behavior, "?"));
			final COrder order = unit.getCurrentOrder();
			if (order == null) {
				s("order", "-");
			}
			else {
				i("order", order.getOrderId()).i("orderAbility", order.getAbilityHandleId());
				target("target", order.getTarget(simulation));
			}
			i("queue", unit.getOrderQueue().size());
			final CAbilityHero hero = unit.getHeroData();
			if (hero != null) {
				i("level", hero.getHeroLevel()).i("xp", hero.getXp());
			}
			final CAbilityInventory inventory = unit.getInventoryData();
			if (inventory != null) {
				for (int slot = 0; slot < inventory.getItemCapacity(); slot++) {
					final CItem item = inventory.getItemInSlot(slot);
					name("slot", slot, "").value(item == null ? 0 : item.getHandleId());
				}
			}
			for (final CAbility ability : unit.getAbilities()) {
				final int handleId = ability.getHandleId();
				name("a", handleId, "").value(ability.getAlias());
				if (ability instanceof CLevelingAbility) {
					name("a", handleId, ".level").value(((CLevelingAbility) ability).getLevel());
				}
				if (ability instanceof CBuff) {
					name("a", handleId, ".left").value(((CBuff) ability).getDurationRemaining(simulation, unit));
				}
				final int cooldown = unit.getCooldownRemainingTicks(simulation, ability.getAlias());
				if (cooldown != 0) {
					name("a", handleId, ".cooldown").value(cooldown);
				}
				name("a", handleId, ".disabled").value(ability.isDisabled());
			}
			end();
		}
	}

	private void hashPlayers(final CSimulation simulation) throws IOException {
		final List<CPlayer> players = simulation.getPlayers();
		for (final CPlayer player : players) {
			begin(2, "player", player.getId()).i("gold", player.getGold()).i("lumber", player.getLumber())
					.i("foodCap", player.getFoodCap()).i("foodUsed", player.getFoodUsed());
			for (int other = 0; other < players.size(); other++) {
				int bits = 0;
				for (final CAllianceType type : CAllianceType.values()) {
					if (player.hasAlliance(other, type)) {
						bits |= 1 << type.ordinal();
					}
				}
				if (bits != 0) {
					name("ally", other, "").value(bits);
				}
			}
			// The selection the game has; what a script selected and has not reached it yet is the local view.
			name("selection");
			for (final CUnit unit : player.getSelection().getUnits()) {
				this.line.append(unit.getHandleId()).append(',');
				if (this.detail != null) {
					this.readable.append(unit.getHandleId()).append(',');
				}
			}
			separator();
			techtree("unlocked", player.getTechtreeUnlockedLevels());
			techtree("inProgress", player.getTechtreeInProgressCounts());
			end();
		}
	}

	private void techtree(final String name, final Map<War3ID, Integer> techtree) {
		final War3ID[] keys = techtree.keySet().toArray(new War3ID[0]);
		Arrays.sort(keys, Comparator.comparingInt(War3ID::getValue));
		for (final War3ID key : keys) {
			i(name + "." + key, techtree.get(key));
		}
	}

	private void hashItems(final CSimulation simulation) throws IOException {
		final CItem[] items = simulation.getItems().toArray(new CItem[0]);
		Arrays.sort(items, Comparator.comparingInt(CItem::getHandleId));
		for (final CItem item : items) {
			final CUnit holder = item.getContainedUnit();
			begin(3, "item", item.getHandleId()).id("type", item.getTypeId()).f("x", item.getX()).f("y", item.getY())
					.f("life", item.getLife()).i("charges", item.getCharges()).b("hidden", item.isHidden())
					.i("holder", holder == null ? 0 : holder.getHandleId()).end();
		}
	}

	private void hashDestructables(final CSimulation simulation) throws IOException {
		final CDestructable[] destructables = simulation.getDestructables().toArray(new CDestructable[0]);
		Arrays.sort(destructables, Comparator.comparingInt(CDestructable::getHandleId));
		if (this.detail != null) {
			for (final CDestructable destructable : destructables) {
				destructableLine(destructable);
				end();
			}
			return;
		}
		DestructableLines lines;
		final GameLines game = gameLines(simulation);
		synchronized (game) {
			lines = game.destructables;
			if ((lines == null) || !Arrays.equals(lines.order, destructables)) {
				// One destructable more or less moves every block after it.
				lines = new DestructableLines(destructables);
				game.destructables = lines;
			}
		}
		synchronized (lines) {
			this.category = 4;
			for (int first = 0; first < destructables.length; first += BLOCK) {
				final int block = first / BLOCK;
				final int last = Math.min(first + BLOCK, destructables.length);
				boolean same = lines.blocks.valid[block];
				for (int i = first; same && (i < last); i++) {
					same = lines.matches(i, destructables[i]);
				}
				if (same) {
					lines.blocks.add(this, block);
					continue;
				}
				startBlock();
				for (int i = first; i < last; i++) {
					destructableLine(destructables[i]);
					end();
					lines.keep(i, destructables[i]);
				}
				lines.blocks.end(this, block, true);
			}
		}
	}

	private void destructableLine(final CDestructable destructable) {
		begin(4, "destructable", destructable.getHandleId()).id("type", destructable.getDestType().getTypeId())
				.f("x", destructable.getX()).f("y", destructable.getY()).f("life", destructable.getLife());
	}

	private static final class GameLines {
		private DestructableLines destructables;
		private final Map<GlobalScope, ScopeLines> scopes = new HashMap<>();
	}

	private static final class DestructableLines {
		private final CDestructable[] order;
		private final War3ID[] types;
		private final int[] values;
		private final Blocks blocks;

		DestructableLines(final CDestructable[] order) {
			this.order = order;
			this.types = new War3ID[order.length];
			this.values = new int[order.length * 4];
			this.blocks = new Blocks((order.length + BLOCK) / BLOCK);
		}

		boolean matches(final int index, final CDestructable destructable) {
			final int at = index * 4;
			return (this.types[index] == destructable.getDestType().getTypeId())
					&& (this.values[at] == destructable.getHandleId())
					&& (this.values[at + 1] == Float.floatToIntBits(destructable.getX()))
					&& (this.values[at + 2] == Float.floatToIntBits(destructable.getY()))
					&& (this.values[at + 3] == Float.floatToIntBits(destructable.getLife()));
		}

		void keep(final int index, final CDestructable destructable) {
			final int at = index * 4;
			this.types[index] = destructable.getDestType().getTypeId();
			this.values[at] = destructable.getHandleId();
			this.values[at + 1] = Float.floatToIntBits(destructable.getX());
			this.values[at + 2] = Float.floatToIntBits(destructable.getY());
			this.values[at + 3] = Float.floatToIntBits(destructable.getLife());
		}
	}

	/** The checksums of blocks of lines hashed earlier, each with its length's combining operator. */
	private static final class Blocks {
		private final boolean[] valid;
		private final int[] crcs;
		private final long[] lengths;
		private final int[] operators;

		Blocks(final int count) {
			this.valid = new boolean[count];
			this.crcs = new int[count];
			this.lengths = new long[count];
			this.operators = new int[count];
		}

		void add(final SimulationChecksum checksum, final int block) {
			checksum.flush();
			checksum.add(checksum.category, this.crcs[block], this.lengths[block], this.operators[block]);
		}

		void end(final SimulationChecksum checksum, final int block, final boolean keep) {
			checksum.flush();
			checksum.capturing = false;
			this.valid[block] = keep && (checksum.capturedLength > 0);
			this.crcs[block] = checksum.capturedCrc;
			this.lengths[block] = checksum.capturedLength;
			this.operators[block] = crcShift(checksum.capturedLength);
		}
	}

	private void startBlock() {
		flush();
		this.capturing = true;
		this.capturedCrc = 0;
		this.capturedLength = 0;
	}

	private void hashProjectiles(final CSimulation simulation) throws IOException {
		final List<CEffect> projectiles = simulation.getProjectiles();
		begin(5, "projectiles", "count").i("count", projectiles.size()).end();
		int index = 0;
		for (final CEffect effect : projectiles) {
			begin(5, "projectile", index++).s("class", SimulationTypeCodes.of(effect, "?"));
			if (effect instanceof CProjectile) {
				final CProjectile projectile = (CProjectile) effect;
				final CUnit source = projectile.getSource();
				i("source", source == null ? 0 : source.getHandleId()).f("x", projectile.getX()).f("y", projectile.getY())
						.f("targetX", projectile.getTargetX()).f("targetY", projectile.getTargetY());
			}
			end();
		}
	}

	private void hashTimers(final CSimulation simulation) throws IOException {
		final List<CTimer> active = simulation.getActiveTimers();
		begin(6, "timers", "count").i("active", active.size()).i("added", simulation.getAddedTimers().size()).end();
		int index = 0;
		for (final CTimer timer : active) {
			begin(6, "timer", index++).s("class", SimulationTypeCodes.of(timer, "?")).i("fire", timer.getEngineFireTick())
					.f("timeout", timer.getTimeoutTime()).b("repeats", timer.isRepeats()).end();
		}
	}

	private void hashRandom(final CSimulation simulation) throws IOException {
		// java.util.Random keeps its seed private; its serialized form holds it on every platform.
		this.randomBytes.reset();
		try (ObjectOutputStream out = new ObjectOutputStream(this.randomBytes)) {
			out.writeObject(simulation.getSeededRandom());
			out.writeObject(simulation.getCombatRandomIfMade());
		}
		final CRC32 seed = new CRC32();
		seed.update(this.randomBytes.toByteArray());
		begin(7, "random", "seed").s("state", hex((int) seed.getValue())).end();
	}

	private void hashPathing(final CSimulation simulation) throws IOException {
		final CPathfindingProcessor[] processors = simulation.getPathfindingProcessors();
		for (int i = 0; i < processors.length; i++) {
			if (processors[i] == null) {
				continue;
			}
			final List<PathfindingJob> queue = processors[i].getMoveQueue();
			begin(9, "pathing", i).i("queue", queue.size()).i("nextTurn", processors[i].getNextTurn())
					.i("jobId", processors[i].getPathfindJobId());
			int index = 0;
			for (final PathfindingJob job : queue) {
				final CUnit unit = job.getIgnoreIntersectionsWithThisUnit();
				name("job", index, "").value(unit == null ? 0 : unit.getHandleId());
				name("job", index, ".x").value(job.getStartX());
				name("job", index, ".y").value(job.getStartY());
				name("job", index, ".turn").value(job.getTurn());
				name("job", index, ".started").value(job.isJobStarted());
				index++;
			}
			end();
		}
	}

	private void hashJass(final int category, final String scope, final CSimulation simulation,
			final GlobalScope globalScope) throws IOException {
		if (globalScope == null) {
			return;
		}
		final List<GlobalScopeAssignable> globals = globalScope.getIndexedGlobals();
		final Map<String, Integer> ids = globalScope.getGlobalIds();
		ScopeLines lines;
		final GameLines game = gameLines(simulation);
		synchronized (game) {
			lines = game.scopes.get(globalScope);
			// Globals are only ever added; a scope that got more is named again.
			if ((lines == null) || !lines.scope.equals(scope) || (lines.names.length != globals.size())
					|| (lines.idCount != ids.size())) {
				lines = new ScopeLines(scope, names(globals.size(), ids), ids.size(), globals);
				game.scopes.put(globalScope, lines);
			}
		}
		synchronized (lines) {
			hashJass(category, scope, globals, lines);
		}
	}

	private static String[] names(final int count, final Map<String, Integer> ids) {
		final String[] names = new String[count];
		for (final Map.Entry<String, Integer> entry : ids.entrySet()) {
			if (entry.getValue() < names.length) {
				names[entry.getValue()] = entry.getKey();
			}
		}
		return names;
	}

	private static final class ScopeLines {
		private final String scope;
		private final String[] names;
		private final int idCount;
		/** The value each line was hashed with; only values whose line cannot change are kept. */
		private final JassValue[] values;
		/**
		 * Where each block starts, and the end; an array is a block of its own that is always hashed, so that the
		 * globals around it can be kept.
		 */
		private final int[] starts;
		private final Blocks blocks;

		ScopeLines(final String scope, final String[] names, final int idCount,
				final List<GlobalScopeAssignable> globals) {
			this.scope = scope;
			this.names = names;
			this.idCount = idCount;
			this.values = new JassValue[names.length];
			final int[] starts = new int[(names.length * 2) + 1];
			int count = 0;
			int runStart = 0;
			for (int i = 0; i < names.length; i++) {
				if (value(globals.get(i)) instanceof ArrayJassValue) {
					if (runStart < i) {
						starts[count++] = runStart;
					}
					starts[count++] = i;
					runStart = i + 1;
				}
				else if ((i - runStart) == BLOCK) {
					starts[count++] = runStart;
					runStart = i;
				}
			}
			if (runStart < names.length) {
				starts[count++] = runStart;
			}
			starts[count] = names.length;
			this.starts = Arrays.copyOf(starts, count + 1);
			this.blocks = new Blocks(count);
		}
	}

	private void hashJass(final int category, final String scope, final List<GlobalScopeAssignable> globals,
			final ScopeLines lines) throws IOException {
		this.category = category;
		for (int block = 0; block < (lines.starts.length - 1); block++) {
			final int first = lines.starts[block];
			final int last = lines.starts[block + 1];
			if (this.detail != null) {
				for (int i = first; i < last; i++) {
					jassLine(category, scope, lines.names[i], i, value(globals.get(i)));
				}
				continue;
			}
			boolean same = lines.blocks.valid[block];
			for (int i = first; same && (i < last); i++) {
				same = lines.values[i] == value(globals.get(i));
			}
			if (same) {
				lines.blocks.add(this, block);
				continue;
			}
			startBlock();
			boolean keep = true;
			for (int i = first; i < last; i++) {
				final JassValue value = value(globals.get(i));
				jassLine(category, scope, lines.names[i], i, value);
				if (isImmutable(value)) {
					lines.values[i] = value;
				}
				else {
					keep = false;
				}
			}
			lines.blocks.end(this, block, keep);
		}
	}

	private static JassValue value(final GlobalScopeAssignable global) {
		return global == null ? null : global.getValue();
	}

	private void jassLine(final int category, final String scope, final String name, final int index,
			final JassValue value) throws IOException {
		begin(category, scope, name == null ? "#" + index : name);
		if (value instanceof ArrayJassValue) {
			final ArrayJassValue array = (ArrayJassValue) value;
			final int length = array.storedLength();
			for (int element = 0; element < length; element++) {
				final JassValue elementValue = array.get(element);
				if (!isDefault(elementValue)) {
					name(element);
					value(elementValue);
				}
			}
		}
		else {
			name("v");
			value(value);
		}
		end();
	}

	/** A value whose line cannot change while the global holds it. */
	private static boolean isImmutable(final JassValue value) {
		if ((value == null) || (value instanceof IntegerJassValue) || (value instanceof RealJassValue)
				|| (value instanceof BooleanJassValue) || (value instanceof StringJassValue)) {
			return true;
		}
		if (value instanceof HandleJassValue) {
			// Not handles with ids, which can be given out late, nor groups, whose members change; the line of the
			// constants comes from their kind or their fixed number.
			final Object javaValue = ((HandleJassValue) value).getJavaValue();
			return (javaValue instanceof Enum) || (javaValue instanceof ConvertedConstant)
					|| !((javaValue instanceof CHandle) || (javaValue instanceof Collection));
		}
		return false;
	}

	/** After {@link #name}: the value and its separator. */
	private void value(final JassValue value) {
		if (value == null) {
			value("null");
		}
		else if (value instanceof IntegerJassValue) {
			value(((IntegerJassValue) value).getValue());
		}
		else if (value instanceof RealJassValue) {
			// JASS reals are floats in the game; the interpreter keeps doubles.
			final double real = ((RealJassValue) value).getValue();
			this.line.append(Long.toHexString(Double.doubleToLongBits(real))).append(' ');
			if (this.detail != null) {
				this.readable.append(Long.toHexString(Double.doubleToLongBits(real))).append('(').append(real)
						.append(") ");
			}
		}
		else if (value instanceof BooleanJassValue) {
			value(((BooleanJassValue) value).getValue());
		}
		else if (value instanceof StringJassValue) {
			value(String.valueOf(((StringJassValue) value).getValue()));
		}
		else if (value instanceof HandleJassValue) {
			value(handle(((HandleJassValue) value).getJavaValue()));
		}
		else {
			value(SimulationTypeCodes.of(value, "value"));
		}
	}

	/**
	 * Array elements still at their type's default are left out: the AI scripts fill whole arrays with zeros, and an
	 * element set back to the default reads the same as one never set.
	 */
	private static boolean isDefault(final JassValue value) {
		if (value == null) {
			return true;
		}
		if (value instanceof IntegerJassValue) {
			return ((IntegerJassValue) value).getValue() == 0;
		}
		if (value instanceof RealJassValue) {
			return Double.doubleToRawLongBits(((RealJassValue) value).getValue()) == 0;
		}
		if (value instanceof BooleanJassValue) {
			return !((BooleanJassValue) value).getValue();
		}
		if (value instanceof HandleJassValue) {
			return ((HandleJassValue) value).getJavaValue() == null;
		}
		return false;
	}

	private static String handle(final Object javaValue) {
		if (javaValue == null) {
			return "null";
		}
		if (javaValue instanceof Enum) {
			// The constant's place, which a release build keeps; its name and its class's name it may not.
			return "enum" + ((Enum<?>) javaValue).ordinal();
		}
		if (javaValue instanceof CHandle) {
			return "h" + ((CHandle) javaValue).getHandleId();
		}
		if (javaValue instanceof CPlayer) {
			return "player" + ((CPlayer) javaValue).getId();
		}
		if (javaValue instanceof Collection) {
			// Unit groups: the members in their iteration order, which ForGroup and FirstOfGroup follow.
			final StringBuilder sb = new StringBuilder("[");
			for (final Object member : (Collection<?>) javaValue) {
				sb.append(member instanceof CHandle ? Integer.toString(((CHandle) member).getHandleId())
						: member == null ? "null" : SimulationTypeCodes.of(member, "object")).append(',');
			}
			return sb.append(']').toString();
		}
		return SimulationTypeCodes.of(javaValue, "object");
	}

	private void target(final String name, final AbilityTarget target) {
		if (target == null) {
			s(name, "-");
		}
		else if (target instanceof CHandle) {
			i(name, ((CHandle) target).getHandleId());
		}
		else {
			f(name + ".x", target.getX()).f(name + ".y", target.getY());
		}
	}

	private int category;

	private SimulationChecksum begin(final int category, final String kind, final String key) {
		start(category, kind);
		this.line.append(key).append(' ');
		if (this.detail != null) {
			this.readable.append(key).append(' ');
		}
		return this;
	}

	private SimulationChecksum begin(final int category, final String kind, final int key) {
		start(category, kind);
		this.line.append(key).append(' ');
		if (this.detail != null) {
			this.readable.append(key).append(' ');
		}
		return this;
	}

	private void start(final int category, final String kind) {
		this.category = category;
		this.line.setLength(0);
		this.line.append(kind).append(' ');
		if (this.detail != null) {
			this.readable.setLength(0);
			this.readable.append(kind).append(' ');
		}
	}

	private void end() throws IOException {
		// The line's UTF-8 bytes; nearly every line is ASCII, which needs no String and no encoder.
		final int length = this.line.length();
		if (this.bytes.length < length) {
			this.bytes = new byte[Math.max(length, this.bytes.length * 2)];
		}
		byte[] encoded = this.bytes;
		int count = length;
		for (int index = 0; index < length; index++) {
			final char c = this.line.charAt(index);
			if (c >= 0x80) {
				encoded = this.line.toString().getBytes(StandardCharsets.UTF_8);
				count = encoded.length;
				break;
			}
			encoded[index] = (byte) c;
		}
		update(encoded, count);
		if (this.detail != null) {
			final CRC32 own = new CRC32();
			own.update(encoded, 0, count);
			this.detail.write(CATEGORIES[this.category]);
			this.detail.write(' ');
			this.detail.write(hex((int) own.getValue()));
			this.detail.write(' ');
			this.detail.write(this.readable.toString().trim());
			this.detail.write('\n');
		}
	}

	/**
	 * Lines in a row of one category are hashed together: the checksum of the joined bytes is the same, and a
	 * checksum call per line costs more than the line on Android, where CRC32 goes through JNI.
	 */
	private void update(final byte[] encoded, final int count) {
		if ((this.category != this.pendingCategory) || ((this.pendingLength + count) > this.pending.length)) {
			flush();
			this.pendingCategory = this.category;
		}
		if (count > this.pending.length) {
			hashSegment(encoded, count);
			return;
		}
		System.arraycopy(encoded, 0, this.pending, this.pendingLength, count);
		this.pendingLength += count;
	}

	private void flush() {
		if (this.pendingLength > 0) {
			hashSegment(this.pending, this.pendingLength);
			this.pendingLength = 0;
		}
	}

	private void hashSegment(final byte[] bytes, final int count) {
		this.segment.reset();
		this.segment.update(bytes, 0, count);
		add(this.pendingCategory, (int) this.segment.getValue(), count, crcShift(count));
	}

	/** Joins bytes hashed apart to what was hashed before them, as if they were hashed after it. */
	private void add(final int category, final int crc, final long length, final int shift) {
		if (length == 0) {
			return;
		}
		this.crcs[category] = combine(this.crcs[category], crc, shift);
		this.total = combine(this.total, crc, shift);
		if (this.capturing) {
			this.capturedCrc = combine(this.capturedCrc, crc, shift);
			this.capturedLength += length;
		}
	}

	private final byte[] pending = new byte[65536];
	private int pendingLength;
	private int pendingCategory = -1;

	// CRC-32 joining as zlib's crc32_combine: the first checksum is moved past the second one's bytes by
	// multiplying it with x^(8 * length) modulo the CRC polynomial, in the reflected bit order of CRC32.
	private static final int POLYNOMIAL = 0xedb88320;
	private static final int[] X_POWER_2N = new int[32];

	static {
		int p = 1 << 30; // x^1
		X_POWER_2N[0] = p;
		for (int n = 1; n < 32; n++) {
			X_POWER_2N[n] = p = multiplyModP(p, p);
		}
	}

	private static int multiplyModP(final int a, int b) {
		int m = 1 << 31;
		int p = 0;
		for (;;) {
			if ((a & m) != 0) {
				p ^= b;
				if ((a & (m - 1)) == 0) {
					break;
				}
			}
			m >>>= 1;
			b = (b & 1) != 0 ? (b >>> 1) ^ POLYNOMIAL : b >>> 1;
		}
		return p;
	}

	/** x^(8 * length) modulo the polynomial: what a checksum is multiplied by to move it past length bytes. */
	static int crcShift(long length) {
		int k = 3;
		int p = 1 << 31; // x^0
		while (length != 0) {
			if ((length & 1) != 0) {
				p = multiplyModP(X_POWER_2N[k & 31], p);
			}
			length >>>= 1;
			k++;
		}
		return p;
	}

	/** The CRC-32 of the bytes of the first checksum followed by the bytes of the second (of the shift's length). */
	static int combine(final int first, final int second, final int shift) {
		return multiplyModP(shift, first) ^ second;
	}

	private SimulationChecksum name(final String name) {
		this.line.append(name).append('=');
		if (this.detail != null) {
			this.readable.append(name).append('=');
		}
		return this;
	}

	private SimulationChecksum name(final int index) {
		this.line.append(index).append('=');
		if (this.detail != null) {
			this.readable.append(index).append('=');
		}
		return this;
	}

	/** A name made of a prefix, a number and a suffix, as "a1234.level", without building it. */
	private SimulationChecksum name(final String prefix, final int number, final String suffix) {
		this.line.append(prefix).append(number).append(suffix).append('=');
		if (this.detail != null) {
			this.readable.append(prefix).append(number).append(suffix).append('=');
		}
		return this;
	}

	private void separator() {
		this.line.append(' ');
		if (this.detail != null) {
			this.readable.append(' ');
		}
	}

	private void value(final int value) {
		this.line.append(value).append(' ');
		if (this.detail != null) {
			this.readable.append(value).append(' ');
		}
	}

	private void value(final boolean value) {
		value(value ? "1" : "0");
	}

	private void value(final War3ID value) {
		value(value == null ? "-" : value.toString());
	}

	private void value(final float value) {
		final int bits = Float.floatToIntBits(value);
		appendHex(this.line, bits).append(' ');
		if (this.detail != null) {
			appendHex(this.readable.append(value).append('('), bits).append(") ");
		}
	}

	private void value(final String value) {
		this.line.append(value).append(' ');
		if (this.detail != null) {
			this.readable.append(value).append(' ');
		}
	}

	private SimulationChecksum i(final String name, final int value) {
		name(name).value(value);
		return this;
	}

	private SimulationChecksum b(final String name, final boolean value) {
		name(name).value(value);
		return this;
	}

	private SimulationChecksum id(final String name, final War3ID value) {
		name(name).value(value);
		return this;
	}

	private SimulationChecksum f(final String name, final float value) {
		name(name).value(value);
		return this;
	}

	private SimulationChecksum s(final String name, final String value) {
		name(name).value(value);
		return this;
	}

	private static final String HEX_DIGITS = "0123456789abcdef";

	/** As {@link Integer#toHexString(int)}. */
	private static StringBuilder appendHex(final StringBuilder sb, final int value) {
		int shift = 28;
		while ((shift > 0) && (((value >>> shift) & 0xF) == 0)) {
			shift -= 4;
		}
		for (; shift >= 0; shift -= 4) {
			sb.append(HEX_DIGITS.charAt((value >>> shift) & 0xF));
		}
		return sb;
	}

	private static String hex(final int value) {
		final String text = Integer.toHexString(value);
		return "00000000".substring(text.length()) + text;
	}

	private static boolean notEmpty(final String value) {
		return (value != null) && !unquote(value).isEmpty();
	}

	private static String unquote(final String value) {
		final String trimmed = value.trim();
		if ((trimmed.length() >= 2) && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return trimmed;
	}

	private static Set<Integer> parseTicks(final String value) {
		final Set<Integer> ticks = new HashSet<>();
		if (notEmpty(value)) {
			for (final String part : unquote(value).split("[,; ]+")) {
				final int dash = part.indexOf('-', 1);
				if (dash > 0) {
					final int first = Integer.parseInt(part.substring(0, dash));
					final int last = Integer.parseInt(part.substring(dash + 1));
					for (int tick = first; tick <= last; tick++) {
						ticks.add(tick);
					}
				}
				else if (!part.isEmpty()) {
					ticks.add(Integer.parseInt(part));
				}
			}
		}
		return ticks;
	}
}
