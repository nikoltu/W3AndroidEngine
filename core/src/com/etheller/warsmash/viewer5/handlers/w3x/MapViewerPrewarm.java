package com.etheller.warsmash.viewer5.handlers.w3x;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.parsers.jass.ScriptPreload;
import com.etheller.warsmash.parsers.w3x.unitsdoo.War3MapUnitsDoo;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3iFlags;
import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.handlers.blp.TextureTranscoder;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.LoadMapTask;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.MapLoader;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItemTypeData;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitTypeData;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManagerEntry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFromLabel;

/**
 * Loads the models of the units a map starts with while its loading screen is still up, one type for each step of the
 * loader. Left to the first frames of the game, they were all loaded in one frame that showed nothing new.
 */
final class MapViewerPrewarm {
	static final Pattern SCRIPT_LABEL_SOUND = Pattern
			.compile("(?:SetAmbientDaySound|SetAmbientNightSound|CreateMIDISound)\\s*\\(\\s*\"([^\"]+)\"");
	private static final int MAX_PREFETCHED_FILES = 4;
	static final Pattern SCRIPT_FILE_SOUND = Pattern
			.compile("\\b(?:CreateSound|CreateSoundFilenameWithLabel)\\s*\\(\\s*\"((?:[^\"\\\\]++|\\\\.)*+)\"");
	private static final Pattern SCRIPT_PRELOAD = Pattern
			.compile("\\bPreload\\s*\\(\\s*\"((?:[^\"\\\\]++|\\\\.)*+)\"");
	static final Pattern SCRIPT_PRELOADER = Pattern
			.compile("\\bPreloader\\s*\\(\\s*\"((?:[^\"\\\\]++|\\\\.)*+)\"");
	static final Pattern SCRIPT_SKY_MODEL = Pattern
			.compile("\\bSetSkyModel\\s*\\(\\s*\"((?:[^\"\\\\]++|\\\\.)++)\"");
	// The model is the last argument of the BJ forms, so any model path on the line is taken. The string patterns
	// here are possessive: a repeated alternative is matched recursively, and a long string overflowed the stack.
	static final Pattern SCRIPT_EFFECT_MODEL = Pattern.compile(
			"\\bAddSpecialEffect\\w*\\s*\\([^\\r\\n]*?\"((?:[^\"\\\\\\r\\n]++|\\\\.)*+)(?<=\\.[mM][dD][lLxX])\"");
	static final Pattern SCRIPT_LABEL_PARAMETERS = Pattern
			.compile("\\bSetSoundParamsFromLabel\\s*\\([^,()]+,\\s*\"((?:[^\"\\\\]++|\\\\.)++)\"");
	static final Pattern SCRIPT_LABEL_CREATE = Pattern
			.compile("\\bCreateSoundFromLabel\\s*\\(\\s*\"((?:[^\"\\\\]++|\\\\.)++)\"");
	private static final long PRELOAD_BATCH_NANOS = 8_000_000L;
	static final Pattern SCRIPT_UNIT_CREATION = Pattern.compile(
			"\\b(?:CreateUnit\\w*|CreateNUnits\\w*|BlzCreateUnit\\w*|UnitPoolAddUnitType|ReplaceUnit\\w*|CreateCorpse)\\s*\\(");
	static final Pattern SCRIPT_ITEM_CREATION = Pattern
			.compile("\\b(?:CreateItem\\w*|BlzCreateItem\\w*)\\s*\\(");
	static final Pattern SCRIPT_DESTRUCTABLE_CREATION = Pattern.compile(
			"\\b(?:CreateDestructable\\w*|CreateDeadDestructable\\w*|BlzCreateDestructable\\w*)\\s*\\(");
	private static final Pattern SCRIPT_DESTRUCTABLE_CALL = Pattern
			.compile("\\b((?:CreateDestructable|CreateDeadDestructable|BlzCreateDestructable)\\w*)\\s*\\(");
	/** What a model's vertices, sequences and bookkeeping hold on the heap beside its textures, on average. */
	private static final long MODEL_HEAP_BYTES = 1_500_000L;
	/** Sizes within a megabyte of each other count as the same size. */
	private static final int COST_BUCKET_SHIFT = 20;
	private static final String TYPE_COSTS_FILE = "script-type-costs.lst";
	private static final int MAX_TYPE_COSTS = 2000;
	private static final String BLIZZARD_SCRIPT = "Scripts\\Blizzard.j";

	private MapViewerPrewarm() {
	}

	/**
	 * Makes the unit types' render data and queues the loading of the types the map places, and those its melee
	 * script starts the players with, onto the loader.
	 */
	static void queue(final War3MapViewer viewer, final War3MapW3i w3i, final MapLoader loader) throws IOException {
		final RenderUnitTypeData types = new RenderUnitTypeData(viewer.allObjectData.getUnits(), viewer.mapMpq, viewer,
				viewer.unitAckSoundsTable, viewer.terrain.uberSplatTable);
		viewer.renderUnitTypeData = types;
		viewer.renderUnitTypeDataObjects = viewer.allObjectData;
		types.warmModelLookups();

		final String script = readMapScript(viewer);
		final MapScriptScans scans = new MapScriptScans(script);
		viewer.typeUsage = new MapTypeUsage(script);
		viewer.scriptSpeaksLines = script.contains("Transmission") || script.contains("CinematicScene")
				|| script.contains("Dialogue");
		viewer.prefetchFrameTemplates();
		// First, so that the decoding runs beside the model loading that follows.
		loader.addLoadTask(whenScanned(loader, () -> prefetchAmbientSounds(viewer, scans, loader), scans.ambientLabels));
		loader.addLoadTask(() -> prefetchInterfaceTextures(viewer, loader));
		loader.addLoadTask(whenScanned(loader, () -> prefetchScriptSounds(viewer, scans, loader), scans.soundFiles));
		loader.addLoadTask(whenScanned(loader, () -> prepareScriptLabels(viewer, script, scans), scans.labelParameters,
				scans.labelCreations));
		loader.addLoadTask(() -> SimulationClassPrewarm.queue(loader));

		final Set<War3ID> placed = new LinkedHashSet<>();
		final Set<War3ID> ownTypes = new LinkedHashSet<>();
		final Set<War3ID> otherTypes = new LinkedHashSet<>();
		// Whether the map's units are made from this file or by its script, which makes the ones the editor placed.
		if (viewer.dataSource.has("war3mapUnits.doo")) {
			final War3MapUnitsDoo dooFile = viewer.mapMpq.readUnits(w3i);
			for (final com.etheller.warsmash.parsers.w3x.unitsdoo.Unit unit : dooFile.getUnits()) {
				if (!War3MapViewer.sloc.equals(unit.getId())) {
					placed.add(unit.getId());
					(unit.getPlayer() == viewer.localPlayerIndex ? ownTypes : otherTypes).add(unit.getId());
				}
			}
		}
		final Set<War3ID> started = new LinkedHashSet<>();
		if (w3i.hasFlag(War3MapW3iFlags.MELEE_MAP)) {
			addMeleeStartingTypes(viewer, started, -1);
			addMeleeStartingTypes(viewer, ownTypes, viewer.localPlayerIndex);
			otherTypes.addAll(started);
		}
		started.removeAll(placed);
		otherTypes.removeAll(ownTypes);
		loader.addLoadTask(() -> prefetchUnitSounds(viewer, ownTypes, otherTypes, loader));
		// The map's items share the file with the units, and its script makes them in the first step.
		final RenderItemTypeData items = viewer.miscData == null ? null
				: new RenderItemTypeData(viewer.allObjectData.getItems(), viewer.mapMpq, viewer, viewer.miscData);
		for (final War3ID typeId : placed) {
			loader.addLoadTask(loader.modelTask(() -> load(viewer, types, items, typeId)));
		}
		for (final War3ID typeId : started) {
			loader.addLoadTask(loader.modelTask(() -> load(viewer, types, items, typeId)));
		}
		loader.addLoadTask(whenScanned(loader, () -> queuePreloads(viewer, w3i, script, scans, loader),
				scans.preloaders, scans.preloadedFiles, scans.skyModels, scans.effectModels));
		final Set<War3ID> known = new LinkedHashSet<>(placed);
		known.addAll(started);
		loader.addLoadTask(settledWhenScanned(loader,
				() -> queueScriptCreations(viewer, script, scans, known, types, items, loader),
				scans.units, scans.items, scans.destructables, scans.variations, scans.rawCodes));
		loader.addLoadTask(loader.modelTask(() -> loadSleepEffect(viewer)));
		loader.addLoadTask(whenScanned(loader, () -> queueSimulationTypes(viewer, scans, known, loader),
				scans.units, scans.rawCodes));
		if (viewer.scriptSpeaksLines) {
			// Last, so that it runs beside the decoding of the textures the loading still has under way.
			loader.addLoadTask(() -> viewer.faceFxLibrary().prepare());
		}
	}

	/** A task that waits for its turn, behind the others, until the scans of the script it needs are done. */
	private static LoadMapTask whenScanned(final MapLoader loader, final LoadMapTask task,
			final CompletableFuture<?>... scans) {
		return new LoadMapTask() {
			@Override
			public void run() throws IOException {
				if (MapScriptScans.isDone(scans)) {
					task.run();
				}
				else {
					loader.retryLater(this);
				}
			}
		};
	}

	/** {@link #whenScanned} for a task that {@link MapLoader#settled} holds until the textures before it are in. */
	private static LoadMapTask settledWhenScanned(final MapLoader loader, final LoadMapTask task,
			final CompletableFuture<?>... scans) {
		final LoadMapTask[] self = new LoadMapTask[1];
		self[0] = loader.settled(() -> {
			if (MapScriptScans.isDone(scans)) {
				task.run();
			}
			else {
				loader.retryLater(self[0]);
			}
		});
		return self[0];
	}

	/**
	 * Reads the simulation's data of the unit types the map places and its script makes, and makes one of each of
	 * their abilities, while the loading screen is still up: the first unit of a type parses its data and loads the
	 * classes of its abilities, in the frame the script made it in. The abilities made here are thrown away.
	 */
	private static void queueSimulationTypes(final War3MapViewer viewer, final MapScriptScans scans,
			final Set<War3ID> known, final MapLoader loader) throws IOException {
		final Set<War3ID> typeIds = new LinkedHashSet<>(known);
		typeIds.addAll(scans.units.join());
		loader.addSlices(typeIds.iterator(), typeId -> loadSimulationType(viewer.simulation, typeId), () -> {
		});
		loader.addLoadTask(() -> queuePathingData(viewer, typeIds, scans, loader));
	}

	/**
	 * The path search data of the walking sizes among the types, one size at a time, as the first orders need it. A
	 * script that makes its units through variables (a map's own creep tables) names no type in a creation call, so the
	 * types it has as raw codes anywhere follow the ones it makes plainly: otherwise the first search of each size
	 * finds the parts of the whole map in the step that orders it.
	 */
	private static void queuePathingData(final War3MapViewer viewer, final Set<War3ID> typeIds,
			final MapScriptScans scans, final MapLoader loader) throws IOException {
		final Set<String> sizes = new LinkedHashSet<>();
		final List<CUnitType> walkers = new ArrayList<>();
		for (final War3ID typeId : typeIds) {
			final CUnitType type = viewer.simulation.getUnitData().getUnitType(typeId);
			if ((type != null) && (type.getSpeed() > 0) && !type.isBuilding()
					&& sizes.add(type.getMovementType() + ":" + type.getCollisionSize()) && (walkers.size() < 4)) {
				walkers.add(type);
			}
		}
		if (walkers.size() < 4) {
			for (final War3ID typeId : scans.rawCodes.join()) {
				if (typeIds.contains(typeId)) {
					continue;
				}
				final CUnitType type = viewer.simulation.getUnitData().getUnitTypeLightweight(typeId);
				if ((type != null) && (type.getSpeed() > 0) && !type.isBuilding()
						&& (type.getMovementType() != PathingGrid.MovementType.FLY)
						&& sizes.add(type.getMovementType() + ":" + type.getCollisionSize())) {
					walkers.add(type);
					if (walkers.size() >= 4) {
						break;
					}
				}
			}
		}
		if (viewer.playsOpeningSteps()) {
			viewer.deferPathingParts(walkers);
		}
		loader.addSlices(walkers.iterator(), viewer.simulation::prewarmPathing, () -> {
		});
	}

	private static final char QUOTE = '\'';

	/** Every four-character code in single quotes in the script, most often named first. */
	static List<War3ID> rawCodes(final String script) {
		final Map<War3ID, int[]> counts = new LinkedHashMap<>();
		scanQuotedCodes(script, counts);
		scanIntegerCodes(script, counts);
		return mostOftenFirst(counts);
	}

	private static void scanQuotedCodes(final String script, final Map<War3ID, int[]> counts) {
		final int length = script.length();
		for (int i = 0; (i + 5) < length; i++) {
			if ((script.charAt(i) == QUOTE) && (script.charAt(i + 5) == QUOTE)) {
				boolean word = true;
				for (int j = i + 1; j < (i + 5); j++) {
					word &= isWordChar(script.charAt(j));
				}
				if (word) {
					counts.computeIfAbsent(War3ID.fromString(script.substring(i + 1, i + 5)), id -> new int[1])[0]++;
					i += 5;
				}
			}
		}
	}

	/**
	 * Raw codes written as decimal integers (map script optimizers replace 'h001' with its number): nine or ten digits
	 * whose four bytes are all letters or digits, the first a letter.
	 */
	private static void scanIntegerCodes(final String script, final Map<War3ID, int[]> counts) {
		final int length = script.length();
		int i = 0;
		while (i < length) {
			if (!isDigit(script.charAt(i))) {
				i++;
				continue;
			}
			final int start = i;
			long value = 0;
			while ((i < length) && isDigit(script.charAt(i))) {
				value = (value * 10) + (script.charAt(i) - '0');
				i++;
				if (value > 0xFFFFFFFFL) {
					break;
				}
			}
			final int digits = i - start;
			final boolean standalone = (start == 0) || (!isWordChar(script.charAt(start - 1))
					&& (script.charAt(start - 1) != '.'));
			if ((digits < 9) || (digits > 10) || (value > 0x7A7A7A7AL) || !standalone
					|| ((i < length) && (isWordChar(script.charAt(i)) || (script.charAt(i) == '.')))) {
				while ((i < length) && isWordChar(script.charAt(i))) {
					i++;
				}
				continue;
			}
			final char[] code = new char[4];
			boolean valid = true;
			for (int j = 0; j < 4; j++) {
				final char c = (char) ((value >>> (24 - (8 * j))) & 0xFF);
				code[j] = c;
				valid &= isWordChar(c);
			}
			if (valid && Character.isLetter(code[0])) {
				counts.computeIfAbsent(War3ID.fromString(new String(code)), id -> new int[1])[0]++;
			}
		}
	}

	private static boolean isDigit(final char c) {
		return (c >= '0') && (c <= '9');
	}

	private static boolean isWordChar(final char c) {
		return (c == '_') || isDigit(c) || ((c >= 'A') && (c <= 'Z')) || ((c >= 'a') && (c <= 'z'));
	}

	/** The effect a creep falls asleep under, which a creep made at night shows in the first step. */
	private static void loadSleepEffect(final War3MapViewer viewer) {
		try {
			// Read from the data: the interface that names the effects is made after the loading screen.
			final GameObject row = viewer.allObjectData.getAbilities().get(CUnit.CREEP_SLEEP);
			if (row != null) {
				for (final String path : row.getFieldAsList(AbilityDataUI.CASTER_ART)) {
					viewer.loadModelMdx(path);
				}
			}
		}
		catch (final RuntimeException e) {
			// Made again when a creep falls asleep, and fails there as it would have anyway.
			Log.error(e);
		}
	}

	private static void loadSimulationType(final CSimulation simulation, final War3ID typeId) {
		try {
			final CUnitType type = simulation.getUnitData().getUnitType(typeId);
			if (type != null) {
				for (final War3ID abilityId : type.getAbilityList()) {
					makeAbility(simulation, abilityId);
				}
				for (final War3ID abilityId : type.getHeroAbilityList()) {
					makeAbility(simulation, abilityId);
				}
			}
		}
		catch (final RuntimeException e) {
			// The same type is made again when a unit of it is made, and fails there as it would have anyway.
			Log.error(e);
		}
	}

	private static void makeAbility(final CSimulation simulation, final War3ID abilityId) {
		try {
			simulation.getAbilityData().createAbility(abilityId, 0);
		}
		catch (final RuntimeException e) {
			// Made again when the ability is added to a unit, and fails there as it would have anyway.
			Log.error(e);
		}
	}

	/**
	 * The memory the types a script makes may take up ahead of time: their textures in the bytes they are uploaded in,
	 * and an allowance for the heap of each model. The rest is loaded when the script makes the first of their units,
	 * a frame that waits for the files. The textures stay in memory for the whole game whether the script makes the
	 * types or not, so a phone with little memory takes only as much as a few small types need.
	 */
	static long scriptCreationBudget(final long deviceMemoryMb, final long maxHeapBytes) {
		final long mb = 1L << 20;
		if (deviceMemoryMb <= 0) {
			return Math.min(1024 * mb, maxHeapBytes / 2);
		}
		final long fromTier = deviceMemoryMb < 4 * 1024 ? 16 * mb
				: deviceMemoryMb < 6 * 1024 ? 32 * mb : deviceMemoryMb < 8 * 1024 ? 64 * mb : 128 * mb;
		return Math.min(maxHeapBytes / 4, fromTier);
	}

	/** The raw codes a script hands to the calls that make something, most often named first. */
	static List<War3ID> createdTypes(final Pattern call, final String script) {
		final Map<War3ID, int[]> counts = new LinkedHashMap<>();
		final Matcher matcher = call.matcher(script);
		while (matcher.find()) {
			// Not a region of the whole script: on Android each matcher keeps a native copy of its input until the
			// collector frees it, and there is one for every call in the script.
			final War3ID typeId = firstRawCode(script.substring(matcher.end(), lineEnd(script, matcher.end())));
			if (typeId != null) {
				counts.computeIfAbsent(typeId, id -> new int[1])[0]++;
			}
		}
		return mostOftenFirst(counts);
	}

	/** The end of the line that holds the position: a line ends with a line feed or a carriage return. */
	private static int lineEnd(final String script, final int from) {
		for (int i = from, n = script.length(); i < n; i++) {
			final char c = script.charAt(i);
			if ((c == '\n') || (c == '\r')) {
				return i;
			}
		}
		return script.length();
	}

	/** The raw code a piece of script names first: in single quotes if it has one, else written as a number. */
	private static War3ID firstRawCode(final String text) {
		final Map<War3ID, int[]> counts = new LinkedHashMap<>();
		scanQuotedCodes(text, counts);
		if (counts.isEmpty()) {
			scanIntegerCodes(text, counts);
		}
		return counts.isEmpty() ? null : counts.keySet().iterator().next();
	}

	/**
	 * The variations of each destructable type a script makes, as far as they are written into the calls as numbers.
	 * A variation is the model that is shown, and the models of a type differ in their files.
	 */
	static Map<War3ID, Set<Integer>> createdDestructableVariations(final String script) {
		final Map<War3ID, Set<Integer>> variations = new LinkedHashMap<>();
		final Matcher matcher = SCRIPT_DESTRUCTABLE_CALL.matcher(script);
		while (matcher.find()) {
			final int lineEnd = lineEnd(script, matcher.end());
			final List<String> arguments = splitArguments(script.substring(matcher.end(), lineEnd));
			// The argument order of the natives: type, x, y, [z], face, [pitch, roll], scale, variation.
			final String name = matcher.group(1);
			final int index = 5 + (name.contains("DestructableZ") ? 1 : 0) + (name.contains("PitchRoll") ? 2 : 0);
			if (arguments.size() <= index) {
				continue;
			}
			final War3ID typeId = firstRawCode(arguments.get(0));
			if (typeId == null) {
				continue;
			}
			try {
				final int variation = Integer.parseInt(arguments.get(index));
				variations.computeIfAbsent(typeId, id -> new LinkedHashSet<>()).add(variation);
			}
			catch (final NumberFormatException e) {
				// Not a number the script writes into the call: the first variation stands in for it.
			}
		}
		return variations;
	}

	/** The arguments of a call whose opening bracket is cut off, up to its closing one. */
	private static List<String> splitArguments(final String text) {
		final List<String> arguments = new ArrayList<>();
		int depth = 0;
		boolean quoted = false;
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			if (quoted) {
				quoted = c != '"' && c != '\n' && c != '\r';
			}
			else if (c == '"') {
				quoted = true;
			}
			else if (c == '(') {
				depth++;
			}
			else if ((c == ')') && (depth-- == 0)) {
				arguments.add(text.substring(start, i).trim());
				return arguments;
			}
			else if ((c == ',') && (depth == 0)) {
				arguments.add(text.substring(start, i).trim());
				start = i + 1;
			}
		}
		return arguments;
	}

	/** Every raw code in a script, most often named first. */
	static List<War3ID> namedTypes(final String script) {
		return rawCodes(script);
	}

	private static List<War3ID> mostOftenFirst(final Map<War3ID, int[]> counts) {
		final List<Map.Entry<War3ID, int[]>> entries = new ArrayList<>(counts.entrySet());
		entries.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
		final List<War3ID> types = new ArrayList<>(entries.size());
		for (final Map.Entry<War3ID, int[]> entry : entries) {
			types.add(entry.getKey());
		}
		return types;
	}

	/** A type the map's script may make, with what it took in texture memory the last time it was loaded. */
	private static final class Candidate {
		private final String key;
		private final Runnable load;
		private long bytes = -1;

		private Candidate(final String key, final Runnable load) {
			this.key = key;
			this.load = load;
		}

		/** Types whose size was never seen come after those whose size is known. */
		private long cost() {
			return this.bytes < 0 ? Long.MAX_VALUE : this.bytes >> COST_BUCKET_SHIFT;
		}
	}

	/**
	 * Queues the loading of the types the map's script makes while it plays, a type for each step of the loader, until
	 * the budget is spent: first the types it hands to the calls that make units, items and destructables, then the
	 * unit types it names anywhere else, which it takes from lists and variables. Of each group the types that took
	 * the least texture memory when they were loaded before come first, the ones the script names most often among
	 * those of about the same size, so that the budget covers as many types as it can; with nothing known yet the
	 * script's order stands. A type made for the first time in the game loaded its model and textures in the frame
	 * the script made it in.
	 */
	private static void queueScriptCreations(final War3MapViewer viewer, final String script,
			final MapScriptScans scans, final Set<War3ID> known, final RenderUnitTypeData types, final RenderItemTypeData items,
			final MapLoader loader) throws IOException {
		final long budget = scriptCreationBudget(GraphicsSettings.deviceMemoryMb(), Runtime.getRuntime().maxMemory());
		if (budget <= 0) {
			return;
		}
		final List<War3ID> units = new ArrayList<>(scans.units.join());
		units.removeAll(known);
		final List<War3ID> itemTypes = new ArrayList<>(scans.items.join());
		itemTypes.removeAll(known);
		final List<War3ID> destructables = scans.destructables.join();
		final Map<War3ID, Set<Integer>> variations = scans.variations.join();
		final List<Candidate> created = new ArrayList<>();
		final int count = Math.max(units.size(), Math.max(itemTypes.size(), destructables.size()));
		for (int i = 0; i < count; i++) {
			if (i < units.size()) {
				final War3ID typeId = units.get(i);
				created.add(new Candidate("u" + typeId, () -> load(viewer, types, null, typeId)));
			}
			if (i < destructables.size()) {
				final War3ID typeId = destructables.get(i);
				created.add(new Candidate("d" + typeId, () -> loadDestructable(viewer, typeId, variations.get(typeId))));
			}
			if (i < itemTypes.size()) {
				final War3ID typeId = itemTypes.get(i);
				created.add(new Candidate("i" + typeId, () -> loadItem(items, typeId)));
			}
		}
		final Set<War3ID> made = new LinkedHashSet<>(units);
		made.addAll(known);
		final List<Candidate> named = new ArrayList<>();
		for (final War3ID typeId : scans.rawCodes.join()) {
			if (!made.contains(typeId) && (viewer.allObjectData.getUnits().get(typeId) != null)) {
				named.add(new Candidate("u" + typeId, () -> load(viewer, types, null, typeId)));
			}
		}
		final Map<String, Long> seen = readTypeCosts();
		for (final Candidate candidate : created) {
			candidate.bytes = seen.getOrDefault(candidate.key, -1L);
		}
		for (final Candidate candidate : named) {
			candidate.bytes = seen.getOrDefault(candidate.key, -1L);
		}
		// The sort keeps the order of the script among types of about the same size.
		created.sort(Comparator.comparingLong(Candidate::cost));
		named.sort(Comparator.comparingLong(Candidate::cost));
		final List<Candidate> learned = new ArrayList<>();
		final Set<String> learnedKeys = new LinkedHashSet<>();
		if (viewer.typeUsage != null) {
			for (final War3ID typeId : viewer.typeUsage.typesByTick()) {
				if (!known.contains(typeId) && (viewer.allObjectData.getUnits().get(typeId) != null)) {
					final Candidate candidate = new Candidate("u" + typeId, () -> load(viewer, types, null, typeId));
					candidate.bytes = seen.getOrDefault(candidate.key, -1L);
					learned.add(candidate);
					learnedKeys.add(candidate.key);
				}
			}
		}
		// What earlier games made comes first, earliest made first. The wait for the textures after each type, which
		// measures it, is not needed for these: they load side by side until together they take what the budget says.
		final long learnedBudget = Math.max(budget, learnedBudget(budget, Runtime.getRuntime().maxMemory()));
		final Deque<Candidate> pending = new ArrayDeque<>();
		for (final Candidate candidate : created) {
			if (!learnedKeys.contains(candidate.key)) {
				pending.add(candidate);
			}
		}
		for (final Candidate candidate : named) {
			if (!learnedKeys.contains(candidate.key)) {
				pending.add(candidate);
			}
		}
		final long baseline = viewer.getTextureBytes();
		final long started = System.nanoTime();
		final int total = pending.size() + learned.size();
		final int[] loadedAhead = new int[1];
		loader.addSlices(learned.iterator(), candidate -> {
			candidate.load.run();
			loadedAhead[0]++;
		}, () -> ((viewer.getTextureBytes() - baseline) + (loadedAhead[0] * MODEL_HEAP_BYTES)) < learnedBudget
				&& heapHasRoom(),
				() -> loader.addNextTask(loader.settled(loader.modelTask(() -> loadScriptCreations(viewer, pending,
						budget, baseline, started, loadedAhead[0], total, null, 0, seen, loader)))));
	}

	/**
	 * Whether the heap is less than seven tenths full. A map that fills most of it by itself (tens of thousands of
	 * doodads) gets no types loaded ahead: they load when the script first makes one, as the budget leaves them to.
	 */
	private static boolean heapHasRoom() {
		final Runtime runtime = Runtime.getRuntime();
		return (runtime.totalMemory() - runtime.freeMemory()) < ((runtime.maxMemory() / 10) * 7);
	}

	/** What the types earlier games of the map made may take: they are made in the game anyway, a little later. */
	static long learnedBudget(final long budget, final long maxHeapBytes) {
		return Math.min(maxHeapBytes / 2, budget * 6);
	}

	private static void loadScriptCreations(final War3MapViewer viewer, final Deque<Candidate> pending,
			final long budget, final long baseline, final long started, final int loaded, final int total,
			final Candidate last, final long lastBefore, final Map<String, Long> seen, final MapLoader loader) {
		// The textures of the type loaded in the step before are uploaded by now, so what they added is its size.
		final long now = viewer.getTextureBytes();
		if (last != null) {
			// A type whose textures an earlier one loaded took nothing then, so it counts as cheap until it is seen
			// loading them itself; the largest size seen is its own.
			final long size = now - lastBefore;
			final Long known = seen.get(last.key);
			if ((known == null) || (size > known)) {
				seen.put(last.key, size);
			}
		}
		final long used = (now - baseline) + (loaded * MODEL_HEAP_BYTES);
		final Candidate next = pending.peek();
		if ((next == null) || (used >= budget) || ((next.bytes >= 0) && ((used + next.bytes) > budget))
				|| !heapHasRoom()) {
			Log.info("Script types loaded ahead: " + loaded + " of " + total + ", " + (used >> 20) + " of "
					+ (budget >> 20) + " MB in " + ((System.nanoTime() - started) / 1_000_000) + " ms");
			writeTypeCosts(seen);
			return;
		}
		pending.poll().load.run();
		loader.addNextTask(loader.settled(loader.modelTask(() -> loadScriptCreations(viewer, pending, budget, baseline,
				started, loaded + 1, total, next, now, seen, loader))));
	}

	/** What the types took in texture memory when last loaded ahead, which the next loading sorts them by. */
	private static Map<String, Long> readTypeCosts() {
		final Map<String, Long> costs = new LinkedHashMap<>();
		try {
			final File file = typeCostsFile();
			if ((file != null) && file.isFile()) {
				for (final String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
						.split("\n")) {
					final int space = line.indexOf(' ');
					if (space > 0) {
						costs.put(line.substring(0, space), Long.parseLong(line.substring(space + 1).trim()));
					}
				}
			}
		}
		catch (final IOException | RuntimeException e) {
			// The script's order stands.
		}
		return costs;
	}

	private static void writeTypeCosts(final Map<String, Long> costs) {
		try {
			final File file = typeCostsFile();
			if ((file != null) && !costs.isEmpty()) {
				final StringBuilder text = new StringBuilder();
				int lines = 0;
				for (final Map.Entry<String, Long> cost : costs.entrySet()) {
					if (lines++ < MAX_TYPE_COSTS) {
						text.append(cost.getKey()).append(' ').append(cost.getValue()).append('\n');
					}
				}
				final File partial = new File(file.getParentFile(), TYPE_COSTS_FILE + ".tmp");
				Files.write(partial.toPath(), text.toString().getBytes(StandardCharsets.UTF_8));
				Files.move(partial.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (final IOException | RuntimeException e) {
			Log.warn("Costs of script types not kept: " + e);
		}
	}

	/** Kept beside the decoded textures, in the application's cache, never in the game's files. */
	private static File typeCostsFile() {
		final File folder = TextureTranscoder.cacheFolder();
		return folder == null ? null : new File(folder, TYPE_COSTS_FILE);
	}

	private static void loadItem(final RenderItemTypeData items, final War3ID typeId) {
		try {
			if (items != null) {
				items.get(typeId);
			}
		}
		catch (final RuntimeException e) {
			// Made again when the script makes the item, and fails there as it would have anyway.
			Log.error(e);
		}
	}

	/**
	 * Loads what the first destructable of a type reads when the script makes it: the models of the variations the
	 * script names, the model of its portrait, its pathing maps and the simulation's data of the type.
	 */
	private static void loadDestructable(final War3MapViewer viewer, final War3ID typeId,
			final Set<Integer> variations) {
		try {
			final GameObject row = viewer.allObjectData.getDestructibles().get(typeId);
			if (row != null) {
				if (variations == null) {
					MapViewerDoodads.getDoodadModel(viewer, 0, row);
				}
				else {
					for (final int variation : variations) {
						MapViewerDoodads.getDoodadModel(viewer, variation, row);
					}
				}
				viewer.loadModelMdx(row.readSLKTag("portraitmodel"));
				viewer.getDestructablePathingPixelMap(row);
				viewer.getDestructablePathingDeathPixelMap(row);
				viewer.simulation.getDestructableData().getUnitType(typeId);
			}
		}
		catch (final RuntimeException e) {
			// Made again when the script makes the destructable, and fails there as it would have anyway.
			Log.error(e);
		}
	}

	/**
	 * Decodes the textures the last game screen's setup loaded while this loading screen is still up, so that the
	 * setup, which is a single frame, only uploads them. The list is what that setup recorded, so nothing here is
	 * a guess about the interface.
	 */
	private static void prefetchInterfaceTextures(final War3MapViewer viewer, final MapLoader loader)
			throws IOException {
		ImageUtils.dropPrefetched();
		final List<String> paths = ImageUtils.readUiTextureList();
		loader.addSlices(paths.iterator(), path -> ImageUtils.prefetchTexture(viewer.mapMpq, path), () -> {
		});
	}

	private static String readMapScript(final War3MapViewer viewer) {
		for (final String path : new String[] { "war3map.j", "Scripts\\war3map.j" }) {
			if (viewer.mapMpq.has(path)) {
				return readText(viewer.mapMpq, path);
			}
		}
		return "";
	}

	private static String readText(final DataSource dataSource, final String path) {
		try (InputStream stream = dataSource.getResourceAsStream(path)) {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			final byte[] buffer = new byte[8192];
			for (int read = stream.read(buffer); read != -1; read = stream.read(buffer)) {
				bytes.write(buffer, 0, read);
			}
			return new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
		}
		catch (final IOException | RuntimeException e) {
			Log.error(e);
			return "";
		}
	}

	/** Reads the escapes left to right in one pass, as the game's script reader does: a pair is never reread. */
	static String unescape(final String jassString) {
		if (jassString.indexOf('\\') == -1) {
			return jassString;
		}
		final StringBuilder result = new StringBuilder(jassString.length());
		for (int i = 0; i < jassString.length(); i++) {
			final char c = jassString.charAt(i);
			if ((c != '\\') || (i + 1 == jassString.length())) {
				result.append(c);
				continue;
			}
			final char escaped = jassString.charAt(++i);
			switch (escaped) {
			case 'n':
				result.append('\n');
				break;
			case 't':
				result.append('\t');
				break;
			case 'r':
				result.append('\r');
				break;
			case 'b':
				result.append('\b');
				break;
			case 'f':
				result.append('\f');
				break;
			case '"':
			case '\\':
				result.append(escaped);
				break;
			default:
				result.append(c).append(escaped);
				break;
			}
		}
		return result.toString();
	}

	/**
	 * Queues the files the map's script preloads at its start, which the game loaded in its first frame: the lists
	 * the script hands to Preloader, and what the standard melee script gives the playing races.
	 */
	private static void queuePreloads(final War3MapViewer viewer, final War3MapW3i w3i, final String script,
			final MapScriptScans scans, final MapLoader loader) {
		final Set<String> lists = new LinkedHashSet<>();
		if (w3i.hasFlag(War3MapW3iFlags.MELEE_MAP) && script.contains("MeleeStartingUnits")) {
			lists.add("scripts\\SharedMelee.pld");
			for (final String race : playingRaces(viewer, -1)) {
				final String name = meleeListName(race);
				if (name != null) {
					lists.add("scripts\\" + name + "Melee.pld");
				}
			}
		}
		lists.addAll(scans.preloaders.join());
		final Set<String> files = new LinkedHashSet<>();
		for (final String list : lists) {
			if (viewer.dataSource.has(list)) {
				addPreloadedFiles(files, readText(viewer.dataSource, list));
			}
		}
		files.addAll(scans.preloadedFiles.join());
		for (final String path : scans.skyModels.join()) {
			loader.addLoadTask(loader.modelTask(() -> MapViewerEffects.loadModelMdx(viewer.dataSource, viewer, path,
					viewer.mapPathSolver, viewer.solverParams)));
		}
		for (final String path : scans.effectModels.join()) {
			loader.addLoadTask(loader.modelTask(() -> loadEffectModel(viewer, path)));
		}
		if (!files.isEmpty()) {
			final Deque<String> names = new ArrayDeque<>(files);
			loader.addLoadTask(loader.modelTask(() -> loadPreloaded(viewer, names, loader)));
		}
	}

	/** The model of an effect the script makes, which it may do from its first step on. */
	private static void loadEffectModel(final War3MapViewer viewer, final String path) {
		final String mdxPath = War3MapViewer.mdx(path);
		if (viewer.dataSource.has(mdxPath) || viewer.dataSource.has(War3MapViewer.mdl(mdxPath))) {
			try {
				MapViewerEffects.loadModelMdx(viewer, path);
			}
			catch (final RuntimeException e) {
				// Made again when the script makes the effect, and fails there as it would have anyway.
				Log.error(e);
			}
		}
	}

	static void addPreloadedFiles(final Set<String> files, final String script) {
		final Matcher matcher = SCRIPT_PRELOAD.matcher(script);
		while (matcher.find()) {
			files.add(unescape(matcher.group(1)));
		}
	}

	/** Loads as many as fit a frame, and queues the rest, so that the loading screen keeps moving. */
	private static void loadPreloaded(final War3MapViewer viewer, final Deque<String> names, final MapLoader loader) {
		final long end = System.nanoTime() + PRELOAD_BATCH_NANOS;
		do {
			ScriptPreload.load(viewer, names.poll());
		}
		while (!names.isEmpty() && (System.nanoTime() < end));
		if (!names.isEmpty()) {
			loader.addLoadTask(loader.modelTask(() -> loadPreloaded(viewer, names, loader)));
		}
	}

	private static String meleeListName(final String race) {
		switch (race) {
		case "human":
			return "Human";
		case "orc":
			return "Orc";
		case "undead":
			return "Undead";
		case "nightelf":
			return "NightElf";
		default:
			return null;
		}
	}

	/**
	 * Starts the decoding of the ambience the map's script names, which it starts in the first game step; where a
	 * sound is decoded on the calling thread, that decoding held up the first frame for a second.
	 */
	private static void prefetchAmbientSounds(final War3MapViewer viewer, final MapScriptScans scans,
			final MapLoader loader) throws IOException {
		final Set<String> labels = new LinkedHashSet<>(scans.ambientLabels.join());
		loader.addSlices(labels.iterator(), label -> {
			final UnitSound sound = viewer.uiSounds.getSound(label);
			if (sound.getSoundCount() <= MAX_PREFETCHED_FILES) {
				sound.prefetch();
				viewer.getSoundLabelInfo(label);
			}
			else if (sound.getSoundCount() > 0) {
				// The script starts the day ambience in the first step, whose file is known from the step and the count.
				final int variant = CSoundFromLabel.variant(0, 0, sound.getSoundCount());
				sound.prefetch(variant);
				sound.getDuration(0);
				sound.getDuration(variant);
			}
		}, () -> {
		});
	}

	/**
	 * Starts the decoding of the sound files the map's script makes sounds of. The script makes them all at its start,
	 * in the first game step, which decoded a campaign's dialogue there for half a second. What the script does not
	 * make by the end of the sound preload is let go then.
	 */
	private static void prefetchScriptSounds(final War3MapViewer viewer, final MapScriptScans scans,
			final MapLoader loader) throws IOException {
		// Each time a path is named, as two sounds of the same file each take a decoding.
		final List<String> files = scans.soundFiles.join();
		loader.addSlices(files.iterator(),
				file -> UnitSound.prefetchSound(viewer.mapMpq, viewer.getScriptSoundFile(file)), () -> {
				});
	}

	/**
	 * Finds the sound labels the script asks for, with the lengths of their files: the map script's own labels are in
	 * a table read only for them, and the game's script makes sounds of labels too, all in the first step.
	 */
	private static void prepareScriptLabels(final War3MapViewer viewer, final String script,
			final MapScriptScans scans) {
		if (script.isEmpty()) {
			return;
		}
		for (final String label : scans.labelParameters.join()) {
			viewer.getSoundLabelParams(label);
		}
		final Set<String> labels = new LinkedHashSet<>(scans.labelCreations.join());
		if (viewer.mapMpq.has(BLIZZARD_SCRIPT)) {
			addMatches(labels, SCRIPT_LABEL_CREATE, readText(viewer.mapMpq, BLIZZARD_SCRIPT));
		}
		for (final String label : labels) {
			viewer.getSoundLabelInfo(label);
		}
	}

	static void addMatches(final Set<String> found, final Pattern pattern, final String text) {
		final Matcher matcher = pattern.matcher(text);
		while (matcher.find()) {
			found.add(unescape(matcher.group(1)));
		}
	}

	/**
	 * Starts the decoding of the answers the sound preload of a game's first step asks for, from the unit types the
	 * map is going to start with, which is what that step finds.
	 */
	private static void prefetchUnitSounds(final War3MapViewer viewer, final Set<War3ID> own,
			final Set<War3ID> others, final MapLoader loader) throws IOException {
		final Set<War3ID> made = new LinkedHashSet<>(own);
		MapViewerLoading.addMadeUnitTypes(viewer, made);
		made.removeAll(own);
		others.removeAll(own);
		loader.addSlices(soundPrefetches(viewer, own, made, others).iterator(), Runnable::run, () -> {
		});
	}

	/** Starts the decoding of what the sound preload asks for of these unit types. */
	static void prefetchSoundsOf(final War3MapViewer viewer, final Set<War3ID> own, final Set<War3ID> made,
			final Set<War3ID> others) {
		for (final Runnable prefetch : soundPrefetches(viewer, own, made, others)) {
			prefetch.run();
		}
	}

	/** One step for each unit type, which a copy of its sounds out of the game data makes take long. */
	private static List<Runnable> soundPrefetches(final War3MapViewer viewer, final Set<War3ID> own,
			final Set<War3ID> made, final Set<War3ID> others) {
		final List<Runnable> prefetches = new ArrayList<>();
		for (final War3ID typeId : own) {
			prefetches.add(() -> {
				final UnitSoundset soundset = viewer.renderUnitTypeData.getSoundset(typeId);
				if (soundset != null) {
					soundset.prefetch();
				}
			});
		}
		for (final War3ID typeId : made) {
			prefetches.add(() -> {
				final UnitSoundset soundset = viewer.renderUnitTypeData.getSoundset(typeId);
				if (soundset != null) {
					soundset.ready.prefetch();
					soundset.what.prefetch();
				}
			});
		}
		for (final War3ID typeId : others) {
			prefetches.add(() -> {
				final UnitSoundset soundset = viewer.renderUnitTypeData.getSoundset(typeId);
				if (soundset != null) {
					soundset.what.prefetch();
				}
			});
		}
		return prefetches;
	}

	private static void load(final War3MapViewer viewer, final RenderUnitTypeData types,
			final RenderItemTypeData items, final War3ID typeId) {
		try {
			final RenderUnitType type = types.get(typeId);
			if (type != null) {
				type.getPortraitModel();
				// The building's ground texture, which its unit's first frame loaded otherwise.
				if (type.getUberSplat() != null) {
					viewer.load(type.getUberSplat(), PathSolver.DEFAULT, null);
				}
			}
			else if (items != null) {
				items.get(typeId);
			}
		}
		catch (final RuntimeException e) {
			// The same type is made again when a unit of it is placed, and fails there as it would have anyway.
			Log.error(e);
		}
	}

	private static Set<String> playingRaces(final War3MapViewer viewer, final int playerIndex) {
		final CSimulation simulation = viewer.simulation;
		final Set<String> races = new LinkedHashSet<>();
		for (int i = 0; i < (viewer.getGameData().getMaxPlayers() - 4); i++) {
			if ((playerIndex != -1) && (i != playerIndex)) {
				continue;
			}
			final CPlayer player = simulation.getPlayer(i);
			if ((player.getSlotState() == CPlayerSlotState.PLAYING) && (player.getRace() != null)) {
				final CRaceManagerEntry entry = viewer.getGameData().getRaceManager().get(player.getRace());
				if (entry != null) {
					races.add(entry.getKey().toLowerCase(java.util.Locale.ROOT));
				}
			}
		}
		return races;
	}

	/**
	 * The town hall, the workers and the mine the standard melee script gives each playing race.
	 *
	 * @param playerIndex the only player to give them to, or -1 for all
	 */
	private static void addMeleeStartingTypes(final War3MapViewer viewer, final Set<War3ID> started,
			final int playerIndex) {
		for (final String race : playingRaces(viewer, playerIndex)) {
			final String[] ids;
			switch (race) {
			case "human":
				ids = new String[] { "htow", "hpea" };
				break;
			case "orc":
				ids = new String[] { "ogre", "opeo" };
				break;
			case "undead":
				ids = new String[] { "unpl", "uaco", "ugol" };
				break;
			case "nightelf":
				ids = new String[] { "etol", "ewsp" };
				break;
			default:
				ids = new String[0];
				break;
			}
			for (final String id : ids) {
				started.add(War3ID.fromString(id));
			}
		}
	}
}
