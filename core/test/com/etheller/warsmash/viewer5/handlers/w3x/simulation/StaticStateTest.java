package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.warsmash.movie.Movies;
import com.etheller.warsmash.parsers.jass.Jass2;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxHandler;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialog;

/**
 * The simulation and the script runtime keep a game's state in that game's objects, so a second game in the same
 * process, or a loaded save, starts from what the map and the save give it. A static field that can change, or a
 * static collection or array, is only allowed for what is not a game's: the player's profile and options, tools,
 * per-call scratch objects and tables filled once from constants.
 */
class StaticStateTest {
	private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
			// Scratch enumerators, reset before every use within one call.
			"simulation.CUnitPlacement#regionCheckerImpl", "simulation.CUnitAcquisition#autoAttackTargetFinderEnum",
			"simulation.CUnitAcquisition#autocastTargetFinderEnum",
			// The checksum switch and its file, which holds every game the process runs.
			"simulation.SimulationChecksum#enabled", "simulation.SimulationChecksum#fileName",
			"simulation.SimulationChecksum#detailTicks", "simulation.SimulationChecksum#writer",
			"simulation.SimulationChecksum#current", "simulation.SimulationChecksum#games",
			"simulation.SimulationChecksum#startedFile", "simulation.SimulationChecksum#shutdownHook",
			"simulation.SimulationChecksum#ended",
			// What the checksum keeps hashed of seldom changing objects until the game is closed; the game never reads
			// it.
			"simulation.SimulationChecksum#GAME_LINES",
			// The player profile: its files, campaign progress and game caches as saved on disk.
			"simulation.campaign.CSaveStorage#profile", "simulation.campaign.CSaveStorage#lastBackgroundWrite",
			"simulation.campaign.CCampaignProgress#campaignList", "simulation.campaign.CCampaignProgress#loaded",
			"simulation.campaign.CCampaignProgress#PROGRESS", "simulation.campaign.CGameCache#loadedFromDisk",
			"simulation.campaign.CGameCache#FILES",
			// Where the game caches are read from: the profile's file, or what a replay recorded.
			"simulation.campaign.CGameCache#disk",
			// The player's sound options, and which game's volume groups the sound device plays by.
			"simulation.sound.CSoundMixer#soundOption", "simulation.sound.CSoundMixer#musicOption",
			"simulation.sound.CSoundMixer#ambientOption", "simulation.sound.CSoundMixer#movementOption",
			"simulation.sound.CSoundMixer#unitOption", "simulation.sound.CSoundMixer#audible",
			// Filled once from constants.
			"simulation.CUnitClassification#UNIT_EDITOR_KEY_TO_CLASSIFICATION",
			"simulation.SimulationTypeCodes#CODES",
			"simulation.SimulationChecksum#X_POWER_2N",
			"simulation.orders.OrderIdUtils#orderIdToString", "simulation.orders.OrderIdUtils#stringToOrderId",
			"simulation.orders.OrderIdUtils#NO_STRING", "simulation.data.CUnitRace#keyToRace",
			"simulation.abilities.harvest.CAbilityWispHarvest#TREE_ALIVE_TYPE_ONLY",
			"simulation.abilities.skills.human.archmage.CAbilityMassTeleport#TELEPORTED_UNITS",
			"simulation.abilities.skills.nightelf.units.CAbilityBouncingMissileFilter#KINDS",
			"simulation.combat.attacks.listeners.CUnitAttackDamageTakenListener#ENEMY_TARGET",
			// Which missing destructors were reported already, for the log.
			"jass.CommonNatives#REPORTED_MISSING_DESTRUCTORS",
			// The script log file.
			"interpreter.ast.util.JassLog#jassLogWriter",
			// Tables of the generated script lexer.
			"interpreter.JassLexer#channelNames", "interpreter.JassLexer#modeNames",
			// Shared immutable small script integers.
			"interpreter.ast.value.IntegerJassValue#CACHE",
			// What the saved games list shows of saves that failed to load.
			"save.SavedGame#FAILED_LOADS", "save.SavedGame#unreportedFailure",
			// Saving and loading: what is known of classes and of script functions, and the size the last save needed.
			"save.SavedGameRules#DROPPED_CLASSES", "save.SavedObjectGraph#FIELDS",
			"save.SavedObjectGraph#UNSTORABLE_CLASSES", "save.SavedObjectGraph#LIST_TYPES",
			"save.SavedObjectGraph#HASH_COLLECTIONS", "save.SavedObjectGraph#MAP_TYPES",
			"save.SavedObjectGraph#lastObjectCount", "save.SavedObjectGraph#PRIMITIVES",
			"save.SavedObjectGraph#FORMER_CLASS_NAMES",
			"save.SavedObjectGraph#FORMER_CLASS_NAMES_BEFORE_NAMED_TIMER_EVENT",
			"save.SavedObjectGraph#FORMER_CLASS_NAMES_SINCE_NAMED_TIMER_EVENT",
			"save.SavedScriptPositions$Program#FUNCTION_HASHES",
			"save.SavedScriptPositions#HASHED_FIELDS",
			// The platform's movie player.
			"movie.Movies#player",
			// The engine's settings, read from the ini of the data source the menu and the launcher use; a running game
			// reads its GameDataConstants copy (see gameDataConstantsAreNotReadInGame).
			"constants#MAX_PLAYERS", "constants#GAME_VERSION", "constants#MODEL_DETAIL_PARTICLE_FACTOR",
			"constants#CATCH_CURSOR", "constants#FULL_SCREEN_MENU_BACKDROP", "constants#FIX_FLAT_FILES_TILESET_LOADING",
			"constants#ENABLE_MUSIC", "constants#LOAD_UNITS_FROM_WORLDEDIT_DATA",
			"constants#CRASH_ON_INCOMPATIBLE_132_FEATURES", "constants#INPUT_HOTKEY_MODE",
			"constants#PARSE_REIGN_OF_CHAOS_BETA_MODELS_INSTEAD", "constants#USE_NINE_ITEM_INVENTORY",
			"constants#RACE_MANAGER", "constants#JASS_FILE_LIST", "constants#ABILITY_COMPATIBILITY",
			// Arrays of constants, never written.
			"simulation.behaviors.cargohold.CargoReach#NEIGHBOR_X",
			"simulation.behaviors.cargohold.CargoReach#NEIGHBOR_Y",
			"simulation.config.War3MapConfigStartLoc#NO_INDICES",
			"simulation.config.War3MapConfigStartLoc#NO_PRIORITIES", "simulation.data.CUnitData#REQUIRES_TIER_X",
			"simulation.data.CUpgradeData#EFFECT", "simulation.data.CUpgradeData#EFFECT_BASE",
			"simulation.data.CUpgradeData#EFFECT_MOD", "simulation.data.CUpgradeData#EFFECT_CODE",
			"simulation.SimulationChecksum#CATEGORIES", "simulation.sound.SoundFileDuration#MPEG1_BITRATES",
			"simulation.sound.SoundFileDuration#MPEG2_BITRATES",
			"simulation.sound.SoundFileDuration#MPEG1_SAMPLE_RATES",
			"jass.ai.AIAttack#SIEGE_UNITS", "save.SavedGameRules#DROPPED_PREFIXES", "movie.Movies#WIDTHS",
			"movie.Movies#AUDIO_EXTENSIONS", "constants#DEFAULT_JASS_FILE_LIST",
			"interpreter.JassParser#_decisionToDFA", "interpreter.JassParser#ruleNames",
			"interpreter.JassParser#_LITERAL_NAMES", "interpreter.JassParser#_SYMBOLIC_NAMES",
			"interpreter.JassParser#tokenNames", "interpreter.JassLexer#_decisionToDFA",
			"interpreter.JassLexer#ruleNames", "interpreter.JassLexer#_LITERAL_NAMES",
			"interpreter.JassLexer#_SYMBOLIC_NAMES", "interpreter.JassLexer#tokenNames",
			"interpreter.ast.value.ArrayJassValue#EMPTY",
			// The search area of one call, cleared at its start.
			"simulation.behaviors.cargohold.CargoReach#visited", "simulation.behaviors.cargohold.CargoReach#queue",
			"simulation.behaviors.cargohold.CargoReach#transportReach"));

	private static final Set<String> STATIC_SELECTION_READERS = new HashSet<>(Arrays.asList(
			// The data source the menu has selected: menus, lobby setup, launcher.
			"MenuCampaigns.java", "MenuLoadingScreen.java", "MenuMapStart.java", "MenuSkirmish.java", "MenuUI.java",
			"TestUI.java", "MapInfoPane.java", "PlayerSlotPane.java", "TeamSetupPane.java", "BattleNetUI.java",
			"GlueBackdropRenderer.java", "GameCatalog.java", "HotkeyCatalog.java",
			"WarsmashGdxFDFTestRenderScreen.java",
			// Game launch: the script that configures the game before it exists.
			"ConfigNatives.java",
			// The skin and the game data are taken at construction of the menu or game UI; the capture itself.
			"GameUI.java", "GameDataConstants.java"));

	private static final Set<String> RENDER_ALLOWED = new HashSet<>(Arrays.asList(
			// Scratch objects of the render thread, filled and read within one call.
			"w3x.environment.Terrain#sizeHeap", "w3x.environment.Terrain#fourComponentHeap",
			"w3x.rendersim.RenderDoodad#VERTEX_COLOR_HEAP", "w3x.rendersim.RenderUnit#heapZ",
			"w3x.SplatModel#scratchFloatBuffer", "w3x.SplatModel#scratchShortBuffer", "w3x.TerrainDoodad#locationHeap",
			"w3x.War3MapViewer#rayHeap", "mdx.AttachmentInstance#visbilityHeap", "mdx.BatchGroup#tempFloat3Array",
			"mdx.EventObjectEmitter#valueHeap", "mdx.GeometryEmitterFuncs#vectorTemp", "mdx.GeometryEmitterFuncs#vector3Heap",
			"mdx.LightInstance#vectorHeap", "mdx.LightInstance#scalarHeap", "mdx.MdxComplexInstance#visibilityHeap",
			"mdx.MdxComplexInstance#translationHeap", "mdx.MdxComplexInstance#rotationHeap",
			"mdx.MdxComplexInstance#scaleHeap", "mdx.MdxComplexInstance#colorHeap", "mdx.MdxComplexInstance#alphaHeap",
			"mdx.MdxComplexInstance#textureIdHeap", "mdx.MdxComplexInstance#attachingModels", "mdx.Particle#latitudeHeap",
			"mdx.Particle#lifeSpanHeap", "mdx.Particle#gravityHeap", "mdx.Particle#speedHeap", "mdx.Particle#tempVector",
			"mdx.Particle2#widthHeap", "mdx.Particle2#lengthHeap", "mdx.Particle2#latitudeHeap", "mdx.Particle2#variationHeap",
			"mdx.Particle2#speedHeap", "mdx.Particle2#gravityHeap", "mdx.ParticleEmitter#emissionRateHeap",
			"mdx.ParticleEmitter2#emissionRateHeap", "mdx.Ribbon#vectorHeap", "mdx.Ribbon#colorHeap", "mdx.Ribbon#alphaHeap",
			"mdx.Ribbon#slotHeap", "mdx.SdSequence#KEY_HEAP", "mdx.SdSequence#VALUES_HEAP", "mdx.SdSequence#IN_TANS_HEAP",
			"mdx.SdSequence#OUT_TANS_HEAP", "mdx.SetupSimpleGroups#alphaHeap",
			// Tables and shared values filled once from constants, never written.
			"w3x.DialogueLabels#GROUPS", "w3x.environment.PathingGrid#movetpToMovementType",
			"w3x.environment.Terrain#colorTags", "w3x.rendersim.ability.AbilityDataUI#CASTER_ART_ATTACHMENT_POINT",
			"w3x.rendersim.ability.AbilityDataUI#TARGET_ART_ATTACHMENT_POINT",
			"w3x.rendersim.ability.AbilityDataUI#BUFF_TARGET_ART_ATTACHMENT_POINT",
			"w3x.rendersim.RenderDoodad#VERTEX_COLOR_BLACK", "w3x.rendersim.RenderSpellEffect#DEFAULT_ANIMATION_QUEUE",
			"w3x.rendersim.RenderSpellEffect#STAND_ONLY", "w3x.rendersim.RenderSpellEffect#DEATH_ONLY",
			"w3x.rendersim.RenderSpellEffect#STAND_THEN_DEATH", "w3x.rendersim.RenderUnit#ILLUSION_TINT",
			"w3x.rendersim.RenderUnitTypeData#CLASSIC_MODEL_QUALITIES", "w3x.replay.GameReplays#DATA_FILES",
			"w3x.replay.W3gReplay#MAGIC", "w3x.ui.ControlGroupBar#NORMAL", "w3x.ui.ControlGroupBar#PUSHED",
			"w3x.ui.ControlGroupBar#HIGHLIGHT", "w3x.ui.GameOptions#PARTICLE_FACTORS",
			"w3x.ui.mapsetup.AdvancedOptionsPane#OBSERVER_ITEMS", "w3x.ui.mapsetup.AdvancedOptionsPane#VISIBILITY_ITEMS",
			"w3x.ui.mapsetup.MapListContainer#TEST_MAP_FOLDERS", "w3x.ui.MeleeAllianceDialog#ALLY_TYPES",
			"w3x.ui.MeleeChat#PLAYER_TEXT_COLORS", "w3x.ui.MeleeScoreScreen#COLUMN_KEYS",
			"w3x.ui.MeleeScoreScreen#BAR_FRAME_HEIGHT", "w3x.ui.MeleeScoreScreen#BAR_CENTER",
			"w3x.ui.MeleeScoreScreen#SORT_COLUMN", "w3x.ui.MeleeUICinematics#REFORGED_GRAPHICS_PACKAGES",
			"w3x.ui.MeleeUITeamResources#UPKEEP_COLORS", "w3x.ui.menu.CampaignCatalog#FORSAKEN_KINGDOM_MAPS",
			"w3x.ui.MenuCampaigns#CAMPAIGN_DIFFICULTIES", "w3x.ui.MenuCampaigns#CAMPAIGN_DIFFICULTY_KEYS",
			"w3x.ui.ReforgedLoadingScreen#BACKGROUNDS", "w3x.ui.ReforgedLoadingScreen#RACE_CRESTS",
			"w3x.ui.ReforgedLoadingScreen#RACE_ICONS", "w3x.ui.ReforgedLoadingScreen#PLAYER_COLORS",
			"w3x.ui.replay.ReplayPanel#SPEEDS", "w3x.Variations#CLIFF_VARS", "w3x.Variations#CITY_CLIFF_VARS",
			"w3x.War3MapViewer#ORIGIN_STRING_LIST", "mdx.FilterMode#ERROR_DEFAULT", "mdx.FilterMode#MODULATE_2X",
			"mdx.FilterMode#MODULATE", "mdx.FilterMode#ADDITIVE_ALPHA", "mdx.FilterMode#BLEND",
			"mdx.ReplaceableIds#REPLACEABLE_ID_TO_STR", "mdx.Sd#forcedInterpMap", "mdx.Sd#defVals", "mdx.SdSequence#NO_TANS",
			"mdx.Sequence#PRIMARY_TAGS", "mdx.Sequence#SECONDARY_TAGS",
			// Animation tag sets passed to the animation API, which takes EnumSet; nothing adds to them.
			"w3x.SequenceUtils#EMPTY", "w3x.SequenceUtils#READY", "w3x.SequenceUtils#ONE", "w3x.SequenceUtils#TWO",
			"w3x.SequenceUtils#FLESH", "w3x.SequenceUtils#TALK", "w3x.SequenceUtils#BONE", "w3x.SequenceUtils#HIT",
			"w3x.SequenceUtils#SPELL", "w3x.SequenceUtils#SPELL_EATTREE", "w3x.SequenceUtils#SPELL_THROW",
			"w3x.SequenceUtils#WORK", "w3x.SequenceUtils#COMPLETE", "w3x.SequenceUtils#FAST", "w3x.SequenceUtils#ALTERNATE",
			// Process-wide switches of the graphics device and the command line, set before the viewers exist.
			"w3x.DynamicShadowManager#IS_SHADOW_MAPPING", "w3x.environment.Terrain#WIREFRAME_TERRAIN",
			"mdx.MdxViewer#DEFAULT_SHADER_ENV", "w3x.War3MapViewer#defaultTimeScale",
			// Key counters that only have to stay unique, and the player's options and replay bundle as read from disk.
			"w3x.rendersim.RenderImage#nextKey", "w3x.rendersim.RenderUbersplat#nextKey", "w3x.ui.GameOptions#current",
			"mdx.MdxModel#reportedStreamConversion"));

	@Test
	void gameStateIsNotStatic() throws Exception {
		final List<String> found = new ArrayList<>();
		scan(CSimulation.class, "com.etheller.warsmash.viewer5.handlers.w3x.simulation", "simulation", found);
		scan(Jass2.class, "com.etheller.warsmash.parsers.jass", "jass", found);
		scan(GlobalScope.class, "com.etheller.interpreter", "interpreter", found);
		scan(CScriptDialog.class, "com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog", "dialog", found);
		scan(SavedGame.class, "com.etheller.warsmash.viewer5.handlers.w3x.save", "save", found);
		scan(Movies.class, "com.etheller.warsmash.movie", "movie", found);
		scanClass(WarsmashConstants.class, "constants", found);
		final List<String> unexpected = new ArrayList<>();
		for (final String field : found) {
			if (!ALLOWED.contains(field)) {
				unexpected.add(field);
			}
		}
		assertEquals(Collections.emptyList(), unexpected);
		final List<String> stale = new ArrayList<>(ALLOWED);
		stale.removeAll(found);
		assertEquals(Collections.emptyList(), stale, "allowed but no longer found: remove from the list");
		assertTrue(found.size() > 10, "the class folders were not found: " + found);
	}

	/**
	 * The render side keeps what belongs to one viewer in that viewer. What is left static is reviewed here, by
	 * group: GL and process state of the graphics device, scratch objects of the single render thread, caches keyed
	 * by their content, and tables filled once. A new mutable static there has to be argued for in this list.
	 */
	@Test
	void renderStateIsReviewed() throws Exception {
		final List<String> found = new ArrayList<>();
		final List<String> covered = Arrays.asList("com.etheller.warsmash.viewer5.handlers.w3x.simulation.",
				"com.etheller.warsmash.viewer5.handlers.w3x.save.",
				"com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.");
		scan(War3MapViewer.class, "com.etheller.warsmash.viewer5.handlers.w3x", "w3x", covered, found);
		scan(MdxHandler.class, "com.etheller.warsmash.viewer5.handlers.mdx", "mdx", Collections.emptyList(), found);
		final List<String> unexpected = new ArrayList<>();
		for (final String field : found) {
			if (!RENDER_ALLOWED.contains(field)) {
				unexpected.add(field);
			}
		}
		assertEquals(Collections.emptyList(), unexpected);
		final List<String> stale = new ArrayList<>(RENDER_ALLOWED);
		stale.removeAll(found);
		assertEquals(Collections.emptyList(), stale, "allowed but no longer found: remove from the list");
		assertTrue(found.size() > 5, "the class folders were not found: " + found);
	}

	/**
	 * The launcher and the menus keep changing the static selection in WarsmashConstants (another data source, the
	 * version switch) while a game runs, so the game reads its own GameDataConstants copy. Only the menu, launcher
	 * and launch code listed here may read the statics of those constants.
	 */
	@Test
	void gameDataConstantsAreNotReadInGame() throws IOException {
		final Pattern moved = Pattern.compile("WarsmashConstants\\.(MAX_PLAYERS|ABILITY_COMPATIBILITY|"
				+ "FIX_FLAT_FILES_TILESET_LOADING|ENABLE_MUSIC|LOAD_UNITS_FROM_WORLDEDIT_DATA|INPUT_HOTKEY_MODE|"
				+ "USE_NINE_ITEM_INVENTORY|CATCH_CURSOR|GAME_VERSION|RACE_MANAGER)\\b");
		Path source = Paths.get("src");
		if (!Files.isDirectory(source.resolve("com"))) {
			source = Paths.get("core", "src");
		}
		final List<String> found = new ArrayList<>();
		try (Stream<Path> files = Files.walk(source)) {
			for (final Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
				final String name = file.getFileName().toString();
				for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					final String trimmed = line.trim();
					if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
						continue;
					}
					if (moved.matcher(line).find() && !found.contains(name)) {
						found.add(name);
					}
				}
			}
		}
		Collections.sort(found);
		final List<String> unexpected = new ArrayList<>(found);
		unexpected.removeAll(STATIC_SELECTION_READERS);
		assertEquals(Collections.emptyList(), unexpected, "read the game's own GameDataConstants instead");
		final List<String> stale = new ArrayList<>(STATIC_SELECTION_READERS);
		stale.removeAll(found);
		assertEquals(Collections.emptyList(), stale, "listed but no longer reading the statics: remove from the list");
	}

	private static void scanClass(final Class<?> type, final String shortName, final List<String> found) {
		for (final Field field : type.getDeclaredFields()) {
			if (isGameState(type, field)) {
				found.add(shortName + "#" + field.getName());
			}
		}
	}

	private static void scan(final Class<?> anchor, final String packageName, final String shortName,
			final List<String> found) throws IOException, URISyntaxException {
		scan(anchor, packageName, shortName, Collections.emptyList(), found);
	}

	private static void scan(final Class<?> anchor, final String packageName, final String shortName,
			final List<String> excludedPackages, final List<String> found) throws IOException, URISyntaxException {
		final Path location = Paths.get(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
		if (Files.isDirectory(location)) {
			scan(anchor, location, packageName, shortName, excludedPackages, found);
			return;
		}
		try (FileSystem jar = FileSystems.newFileSystem(location, (ClassLoader) null)) {
			scan(anchor, jar.getPath("/"), packageName, shortName, excludedPackages, found);
		}
	}

	private static void scan(final Class<?> anchor, final Path root, final String packageName,
			final String shortName, final List<String> excludedPackages, final List<String> found)
			throws IOException {
		final Path folder = root.resolve(packageName.replace('.', '/'));
		try (Stream<Path> files = Files.walk(folder)) {
			files.filter(file -> file.toString().endsWith(".class")).forEach(file -> {
				final String relative = root.relativize(file).toString().replace('\\', '/');
				final String className = relative.substring(0, relative.length() - ".class".length()).replace('/',
						'.');
				for (final String excluded : excludedPackages) {
					if (className.startsWith(excluded)) {
						return;
					}
				}
				final Class<?> type;
				final Field[] fields;
				try {
					type = Class.forName(className, false, anchor.getClassLoader());
					fields = type.getDeclaredFields();
				}
				catch (final ClassNotFoundException | LinkageError e) {
					// Needs a library the tests do not have (graphics, Android); nothing of a game's state.
					return;
				}
				for (final Field field : fields) {
					if (isGameState(type, field)) {
						found.add(shortName + className.substring(packageName.length()) + "#" + field.getName());
					}
				}
			});
		}
	}

	private static boolean isGameState(final Class<?> type, final Field field) {
		final int modifiers = field.getModifiers();
		if (!Modifier.isStatic(modifiers) || field.isSynthetic()) {
			return false;
		}
		if (type.isEnum() && field.getName().equals("VALUES") && field.getType().isArray()) {
			// values() copied once; never written.
			return false;
		}
		if (!Modifier.isFinal(modifiers)) {
			return true;
		}
		return Collection.class.isAssignableFrom(field.getType()) || Map.class.isAssignableFrom(field.getType())
				|| field.getType().isArray();
	}
}
