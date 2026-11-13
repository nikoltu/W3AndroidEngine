package com.etheller.warsmash.parsers.jass;

import com.badlogic.gdx.files.FileHandle;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import com.badlogic.gdx.math.Rectangle;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.scope.trigger.TriggerBooleanExpression;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassType;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManager;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.GameCameraManager;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ScriptEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CTerrainDeformations;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentUbersplat;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityDisableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCache;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CLeaderboard;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CStoredUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerChatEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CSoundVolumeGroup;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitInRangeEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.MeleeUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.toggle.MeleeToggleUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;
import com.etheller.interpreter.ast.util.JassProgram;

/**
 * Natives campaign maps call: game cache, campaign progress, sounds, camera noise, minimap pings, indicators,
 * leaderboards and scripted unit helpers. Return values and null handling are measured against 1.29.
 */
final class CampaignNatives {
	private final JassProgram program;
	private final GlobalScope globals;
	private final War3MapViewer viewer;
	private final WarsmashUI ui;
	private final CSimulation simulation;
	private final Map<Integer, CLeaderboard> playerLeaderboards = new HashMap<>();

	private CampaignNatives(final JassProgram program, final War3MapViewer viewer, final WarsmashUI ui) {
		this.program = program;
		this.globals = program.getGlobalScope();
		this.viewer = viewer;
		this.ui = ui;
		this.simulation = viewer.simulation;
	}

	static void register(final JassProgram program, final War3MapViewer viewer, final WarsmashUI ui) {
		final CampaignNatives natives = new CampaignNatives(program, viewer, ui);
		natives.registerGameCache();
		natives.registerCampaign();
		natives.registerSounds();
		natives.registerCamera();
		natives.registerWorld();
		natives.registerEffects();
		natives.registerAllyColorFilter();
		natives.registerMinimapAndIndicators();
		natives.registerUnits();
		natives.registerDestructables();
		natives.registerLeaderboards();
		natives.registerTimerDialogs();
	}

	private void add(final String name, final JassFunction function) {
		this.program.getJassNativeManager().createNative(name, function);
	}

	private HandleJassType type(final String name) {
		return this.globals.getHandleType(name);
	}

	private static <T> T object(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(ObjectJassValueVisitor.<T>getInstance());
	}

	private static String string(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(StringJassValueVisitor.getInstance());
	}

	/** A text argument the original shows or keeps as the map's text when it is a map string key. */
	private String mapString(final List<JassValue> arguments, final int index) {
		return this.viewer.getGameUI().getTrigStr(string(arguments, index));
	}

	private static int integer(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(IntegerJassValueVisitor.getInstance());
	}

	private static float real(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(RealJassValueVisitor.getInstance()).floatValue();
	}

	private static boolean bool(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(BooleanJassValueVisitor.getInstance());
	}

	private static int color(final List<JassValue> arguments, final int first) {
		return ((integer(arguments, first) & 0xFF) << 24) | ((integer(arguments, first + 1) & 0xFF) << 16)
				| ((integer(arguments, first + 2) & 0xFF) << 8) | (integer(arguments, first + 3) & 0xFF);
	}

	private static String noneIfNull(final String value) {
		return value == null ? "" : value;
	}

	private void ignored(final String... names) {
		for (final String name : names) {
			add(name, (arguments, globalScope, triggerScope) -> null);
		}
	}

	private void registerGameCache() {
		final HandleJassType gamecacheType = type("gamecache");
		final HandleJassType unitType = type("unit");
		add("InitGameCache", (arguments, globalScope, triggerScope) -> new HandleJassValue(gamecacheType,
				new CGameCache(noneIfNull(string(arguments, 0)))));
		add("SaveGameCache", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			return BooleanJassValue.of((cache != null) && cache.save());
		});
		add("ReloadGameCachesFromDisk", (arguments, globalScope, triggerScope) -> {
			CGameCache.reloadAllFromDisk();
			return BooleanJassValue.TRUE;
		});
		ignored("SyncStoredInteger", "SyncStoredReal", "SyncStoredBoolean", "SyncStoredUnit", "SyncStoredString");
		add("FlushGameCache", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			if (cache != null) {
				cache.flush();
			}
			return null;
		});
		add("FlushStoredMission", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			if (cache != null) {
				cache.flushMission(string(arguments, 1));
			}
			return null;
		});
		add("StoreInteger", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			if (cache != null) {
				cache.storeInteger(string(arguments, 1), string(arguments, 2), integer(arguments, 3));
			}
			return null;
		});
		add("StoreReal", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			if (cache != null) {
				cache.storeReal(string(arguments, 1), string(arguments, 2), real(arguments, 3));
			}
			return null;
		});
		add("StoreBoolean", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			if (cache != null) {
				cache.storeBoolean(string(arguments, 1), string(arguments, 2), bool(arguments, 3));
			}
			return null;
		});
		add("StoreString", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			return BooleanJassValue.of((cache != null)
					&& cache.storeString(string(arguments, 1), string(arguments, 2), noneIfNull(string(arguments, 3))));
		});
		add("StoreUnit", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			final CUnit unit = object(arguments, 3);
			if ((cache == null) || (unit == null)) {
				return BooleanJassValue.FALSE;
			}
			return BooleanJassValue.of(cache.storeUnit(string(arguments, 1), string(arguments, 2), new CStoredUnit(unit)));
		});
		add("RestoreUnit", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			final CPlayer player = object(arguments, 3);
			final CStoredUnit stored = cache == null ? null : cache.getUnit(string(arguments, 1), string(arguments, 2));
			if ((stored == null) || (player == null)) {
				return new HandleJassValue(unitType, null);
			}
			return new HandleJassValue(unitType, stored.restore(this.simulation, player.getId(), real(arguments, 4),
					real(arguments, 5), real(arguments, 6)));
		});
		add("GetStoredInteger", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			final Integer value = cache == null ? null : cache.getInteger(string(arguments, 1), string(arguments, 2));
			return IntegerJassValue.of(value == null ? 0 : value);
		});
		add("GetStoredReal", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			final Float value = cache == null ? null : cache.getReal(string(arguments, 1), string(arguments, 2));
			return RealJassValue.of(value == null ? 0 : value);
		});
		add("GetStoredBoolean", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			final Boolean value = cache == null ? null : cache.getBoolean(string(arguments, 1), string(arguments, 2));
			return BooleanJassValue.of((value != null) && value);
		});
		add("GetStoredString", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			final String value = cache == null ? null : cache.getString(string(arguments, 1), string(arguments, 2));
			return new StringJassValue(noneIfNull(value));
		});
		add("HaveStoredInteger", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			return BooleanJassValue
					.of((cache != null) && (cache.getInteger(string(arguments, 1), string(arguments, 2)) != null));
		});
		add("HaveStoredReal", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			return BooleanJassValue
					.of((cache != null) && (cache.getReal(string(arguments, 1), string(arguments, 2)) != null));
		});
		add("HaveStoredBoolean", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			return BooleanJassValue
					.of((cache != null) && (cache.getBoolean(string(arguments, 1), string(arguments, 2)) != null));
		});
		add("HaveStoredString", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			return BooleanJassValue
					.of((cache != null) && (cache.getString(string(arguments, 1), string(arguments, 2)) != null));
		});
		add("HaveStoredUnit", (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			return BooleanJassValue
					.of((cache != null) && (cache.getUnit(string(arguments, 1), string(arguments, 2)) != null));
		});
		add("FlushStoredInteger", flush(CGameCache::flushInteger));
		add("FlushStoredReal", flush(CGameCache::flushReal));
		add("FlushStoredBoolean", flush(CGameCache::flushBoolean));
		add("FlushStoredString", flush(CGameCache::flushString));
		add("FlushStoredUnit", flush(CGameCache::flushUnit));
	}

	private interface CacheFlush {
		void flush(CGameCache cache, String mission, String key);
	}

	private static JassFunction flush(final CacheFlush flush) {
		return (arguments, globalScope, triggerScope) -> {
			final CGameCache cache = object(arguments, 0);
			if (cache != null) {
				flush.flush(cache, string(arguments, 1), string(arguments, 2));
			}
			return null;
		};
	}

	private void registerCampaign() {
		add("SetMissionAvailable", (arguments, globalScope, triggerScope) -> {
			CCampaignProgress.setMissionAvailable(integer(arguments, 0), integer(arguments, 1), bool(arguments, 2));
			return null;
		});
		add("SetCampaignAvailable", (arguments, globalScope, triggerScope) -> {
			CCampaignProgress.setCampaignAvailable(integer(arguments, 0), bool(arguments, 1));
			return null;
		});
		add("SetOpCinematicAvailable", (arguments, globalScope, triggerScope) -> {
			CCampaignProgress.setOpeningCinematicAvailable(integer(arguments, 0), bool(arguments, 1));
			return null;
		});
		add("SetEdCinematicAvailable", (arguments, globalScope, triggerScope) -> {
			CCampaignProgress.setEndingCinematicAvailable(integer(arguments, 0), bool(arguments, 1));
			return null;
		});
		add("PlayModelCinematic", (arguments, globalScope, triggerScope) -> {
			this.viewer.playModelCinematic(string(arguments, 0));
			return null;
		});
		add("BlzPlayModelCinematicGameAtPosition", (arguments, globalScope, triggerScope) -> {
			this.viewer.playModelCinematicAtPosition(string(arguments, 0), real(arguments, 1), real(arguments, 2),
					real(arguments, 3), real(arguments, 4));
			return null;
		});
		add("BlzPreloadModelCinematicGame", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(this.viewer.preloadModelCinematic(string(arguments, 0))));
		add("BlzGetModelCinematicGameShotCount", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(this.viewer.getModelCinematicShotCount()));
		add("BlzGetModelCinematicGameCurrentShot", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(this.viewer.getModelCinematicCurrentShot()));
		add("BlzGetModelCinematicGameRemainingTime", (arguments, globalScope, triggerScope) -> RealJassValue
				.of(this.viewer.getModelCinematicRemainingTime()));
		add("BlzSetCinematicEnabledDE", (arguments, globalScope, triggerScope) -> {
			this.viewer.setModelCinematicsEnabled(bool(arguments, 0));
			return null;
		});
		add("PlayCinematic", (arguments, globalScope, triggerScope) -> {
			this.simulation.queueCinematic(string(arguments, 0));
			return null;
		});
		// Measured: in a custom or melee map the buttons never change from hidden; only a campaign mission sets them.
		add("SetCustomCampaignButtonVisible", (arguments, globalScope, triggerScope) -> {
			if (isCampaignMission()) {
				CCampaignProgress.setCustomCampaignButtonVisible(integer(arguments, 0), bool(arguments, 1));
			}
			return null;
		});
		add("GetCustomCampaignButtonVisible", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(isCampaignMission() && CCampaignProgress.isCustomCampaignButtonVisible(integer(arguments, 0))));
		add("ReloadGame", (arguments, globalScope, triggerScope) -> {
			this.viewer.runAfterPendingSave(() -> {
				this.viewer.setRestartAsNextLevel();
				this.ui.endGame(false);
			});
			return null;
		});
		add("DisplayLoadDialog", (arguments, globalScope, triggerScope) -> {
			final MeleeUI meleeUI = meleeUI();
			if (meleeUI != null) {
				meleeUI.displayLoadDialog();
			}
			return null;
		});
		add("SetTutorialCleared", (arguments, globalScope, triggerScope) -> {
			CCampaignProgress.setTutorialCleared(bool(arguments, 0));
			return null;
		});
		add("SetCampaignMenuRaceEx", (arguments, globalScope, triggerScope) -> {
			CCampaignProgress.setMenuCampaignIndex(integer(arguments, 0));
			return null;
		});
		add("SetCampaignMenuRace", (arguments, globalScope, triggerScope) -> {
			final CRace race = object(arguments, 0);
			final int campaign = race == null ? -1 : classicCampaignOfRace(race.getId());
			if (campaign != -1) {
				CCampaignProgress.setClassicMenuCampaignIndex(campaign);
			}
			return null;
		});
		add("ForceCampaignSelectScreen", (arguments, globalScope, triggerScope) -> {
			this.viewer.getGameExit().forceCampaignSelectScreen();
			return null;
		});
		add("ChangeLevel", (arguments, globalScope, triggerScope) -> {
			final String level = campaignEditionLevel(string(arguments, 0));
			final boolean showScoreScreen = bool(arguments, 1);
			this.viewer.runAfterPendingSave(() -> {
				this.viewer.getGameExit().setNextLevel(level, null);
				this.ui.endGame(showScoreScreen);
			});
			return null;
		});
		add("SaveGameExists", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(CSaveStorage.savedGame(noneIfNull(string(arguments, 0))).exists()));
		add("CopySaveGame", (arguments, globalScope, triggerScope) -> {
			final FileHandle source = CSaveStorage.savedGame(noneIfNull(string(arguments, 0)));
			if (!source.exists()) {
				return BooleanJassValue.FALSE;
			}
			source.copyTo(CSaveStorage.savedGame(noneIfNull(string(arguments, 1))));
			return BooleanJassValue.TRUE;
		});
		add("RemoveSaveDirectory", (arguments, globalScope, triggerScope) -> {
			final FileHandle folder = CSaveStorage.saveFolder(noneIfNull(string(arguments, 0)));
			if ((folder == null) || !folder.isDirectory()) {
				return BooleanJassValue.FALSE;
			}
			return BooleanJassValue.of(folder.deleteDirectory());
		});
		add("RenameSaveDirectory", (arguments, globalScope, triggerScope) -> {
			final FileHandle source = CSaveStorage.saveFolder(noneIfNull(string(arguments, 0)));
			final FileHandle target = CSaveStorage.saveFolder(noneIfNull(string(arguments, 1)));
			if ((source == null) || (target == null) || !source.isDirectory() || target.exists()) {
				return BooleanJassValue.FALSE;
			}
			source.moveTo(target);
			return BooleanJassValue.of(target.isDirectory());
		});
		add("SaveGame", (arguments, globalScope, triggerScope) -> {
			this.viewer.requestSaveGame(CSaveStorage.savedGame(noneIfNull(string(arguments, 0))));
			return null;
		});
		add("LoadGame", (arguments, globalScope, triggerScope) -> {
			this.viewer.requestLoadGame(CSaveStorage.savedGame(noneIfNull(string(arguments, 0))), true);
			return null;
		});
		add("SaveGameCheckpoint", (arguments, globalScope, triggerScope) -> {
			this.viewer.requestSaveGame(CSaveStorage.savedGame(noneIfNull(string(arguments, 0))));
			return null;
		});
		add("DisableRestartMission", (arguments, globalScope, triggerScope) -> {
			this.viewer.setRestartMissionDisabled(bool(arguments, 0));
			return null;
		});
		add("DoNotSaveReplay", (arguments, globalScope, triggerScope) -> {
			this.viewer.doNotSaveReplay();
			return null;
		});
	}

	private static final String CLASSIC_CAMPAIGN_FOLDER = "campaign\\classic\\";
	private static final String REFORGED_CAMPAIGN_FOLDER = "campaign\\reforged\\";

	/**
	 * The Classic copy of the last Reign of Chaos Night Elf mission names the Reforged credits map, which the game
	 * data hold only as the Classic one (1.29 goes on to its own credits map). A campaign level of the other edition is
	 * played in this map's edition when the data hold it there, and a level the data lack is taken from the other
	 * edition, so a campaign stays on its own maps.
	 */
	private String campaignEditionLevel(final String level) {
		if (level == null) {
			return null;
		}
		final String levelPath = level.toLowerCase(Locale.ROOT).replace('/', '\\');
		final boolean levelClassic = levelPath.contains(CLASSIC_CAMPAIGN_FOLDER);
		if (!levelClassic && !levelPath.contains(REFORGED_CAMPAIGN_FOLDER)) {
			return level;
		}
		final DataSource dataSource = this.viewer.dataSource;
		final String otherEdition = otherCampaignEditionLevel(dataSource, level, levelPath, levelClassic);
		if (otherEdition == null) {
			return level;
		}
		final String mapFile = this.viewer.getMapFileName();
		final String mapPath = mapFile == null ? "" : mapFile.toLowerCase(Locale.ROOT).replace('/', '\\');
		final boolean mapClassic = mapPath.contains(CLASSIC_CAMPAIGN_FOLDER);
		final boolean mapReforged = mapPath.contains(REFORGED_CAMPAIGN_FOLDER);
		if ((mapClassic && !levelClassic) || (mapReforged && levelClassic) || !dataSource.has(level)) {
			return otherEdition;
		}
		return level;
	}

	private boolean isCampaignMission() {
		final String mapFile = this.viewer.getMapFileName();
		final String mapPath = mapFile == null ? "" : mapFile.toLowerCase(Locale.ROOT).replace('/', '\\');
		return mapPath.contains(CLASSIC_CAMPAIGN_FOLDER) || mapPath.contains(REFORGED_CAMPAIGN_FOLDER);
	}

	/** Classic Reign of Chaos maps are .w3m, The Frozen Throne and Reforged ones .w3x. */
	private static String otherCampaignEditionLevel(final DataSource dataSource, final String level,
			final String levelPath, final boolean levelClassic) {
		final String folder = levelClassic ? CLASSIC_CAMPAIGN_FOLDER : REFORGED_CAMPAIGN_FOLDER;
		final int folderEnd = levelPath.indexOf(folder) + folder.length();
		final int extensionStart = level.lastIndexOf('.');
		if (extensionStart <= folderEnd) {
			return null;
		}
		final String base = level.substring(0, folderEnd - folder.length()) + "Campaign\\"
				+ (levelClassic ? "Reforged\\" : "Classic\\") + level.substring(folderEnd, extensionStart);
		final String[] extensions = levelClassic ? new String[] { ".w3x" } : new String[] { ".w3m", ".w3x" };
		for (final String extension : extensions) {
			if (dataSource.has(base + extension)) {
				return base + extension;
			}
		}
		return null;
	}

	/** The order of the classic campaign screen: Tutorial (RACE_OTHER), Human, Undead, Orc, Night Elf. */
	private static int classicCampaignOfRace(final int raceId) {
		switch (raceId) {
		case 7:
			return 0;
		case 1:
			return 1;
		case 3:
			return 2;
		case 2:
			return 3;
		case 4:
			return 4;
		default:
			return -1;
		}
	}

	private void registerSounds() {
		// The World Editor writes this instead of the separate setters when a sound keeps its label's defaults; the
		// files and the looping come from CreateSound.
		add("SetSoundParamsFromLabel", soundSetter(
				(sound, arguments) -> applyLabelParameters(sound, this.viewer.getSoundLabel(string(arguments, 1)))));
		add("SetSoundVolume", soundSetter((sound, arguments) -> sound.setVolume(integer(arguments, 1))));
		add("SetSoundPitch", soundSetter((sound, arguments) -> sound.setPitch(real(arguments, 1))));
		add("SetSoundChannel", soundSetter((sound, arguments) -> sound.setChannel(integer(arguments, 1))));
		add("SetSoundPosition", soundSetter(
				(sound, arguments) -> sound.setPosition(real(arguments, 1), real(arguments, 2), real(arguments, 3))));
		add("SetSoundPlayPosition", soundSetter((sound, arguments) -> sound.setPlayPosition(integer(arguments, 1))));
		add("SetSoundVelocity", soundSetter(
				(sound, arguments) -> sound.setVelocity(real(arguments, 1), real(arguments, 2), real(arguments, 3))));
		add("SetSoundDistances",
				soundSetter((sound, arguments) -> sound.setDistances(real(arguments, 1), real(arguments, 2))));
		add("SetSoundDistanceCutoff", soundSetter((sound, arguments) -> sound.setDistanceCutoff(real(arguments, 1))));
		add("SetSoundConeAngles", soundSetter((sound, arguments) -> sound.setConeAngles(real(arguments, 1),
				real(arguments, 2), integer(arguments, 3))));
		add("SetSoundConeOrientation", soundSetter((sound, arguments) -> sound.setConeOrientation(real(arguments, 1),
				real(arguments, 2), real(arguments, 3))));
		add("SetDialogueSpeakerNameKey", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			if (sound != null) {
				sound.setDialogueSpeakerNameKey(noneIfNull(string(arguments, 1)));
			}
			return BooleanJassValue.of(sound != null);
		});
		add("SetDialogueTextKey", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			if (sound != null) {
				sound.setDialogueTextKey(noneIfNull(string(arguments, 1)));
			}
			return BooleanJassValue.of(sound != null);
		});
		add("GetDialogueSpeakerNameKey", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			return new StringJassValue(sound == null ? "" : sound.getDialogueSpeakerNameKey());
		});
		add("GetDialogueTextKey", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			return new StringJassValue(sound == null ? "" : sound.getDialogueTextKey());
		});
		add("AttachSoundToUnit", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			final CUnit unit = object(arguments, 1);
			if ((sound != null) && (unit != null)) {
				sound.attachToUnit(this.viewer.getRenderPeer(unit));
			}
			return null;
		});
		add("GetSoundIsPlaying", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			return BooleanJassValue.of((sound != null) && sound.isPlaying(this.simulation.getGameTurnTick()));
		});
		add("GetSoundFileDuration", (arguments, globalScope, triggerScope) -> {
			final String fileName = string(arguments, 0);
			return IntegerJassValue.of(fileName == null ? 0
					: Math.round(UnitSound.fileDuration(this.viewer.mapMpq, fileName) * 1000));
		});
		add("SetThematicMusicVolume", (arguments, globalScope, triggerScope) -> {
			this.ui.setMusicVolume(integer(arguments, 0));
			return null;
		});
		add("GetSoundIsLoading", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		add("VolumeGroupSetVolume", (arguments, globalScope, triggerScope) -> {
			final CSoundVolumeGroup group = object(arguments, 0);
			if (group != null) {
				this.viewer.getSoundMixer().setGroupScale(group, real(arguments, 1));
			}
			return null;
		});
		add("VolumeGroupReset", (arguments, globalScope, triggerScope) -> {
			this.viewer.getSoundMixer().reset();
			return null;
		});
		add("ClearMapMusic", (arguments, globalScope, triggerScope) -> {
			this.ui.setMapMusic(null, false, 0);
			return null;
		});
		// Reforged lip sync data for models this engine does not have; stacking only thins out overlapping sounds of
		// many units at once in the original.
		for (final String facial : new String[] { "SetSoundFacialAnimationLabel", "SetSoundFacialAnimationGroupLabel",
				"SetSoundFacialAnimationSetFilepath" }) {
			add(facial, (arguments, globalScope, triggerScope) -> BooleanJassValue.of(object(arguments, 0) != null));
		}
		add("RegisterStackedSound", soundSetter((sound, arguments) -> this.viewer.getStackedSounds().register(sound,
				bool(arguments, 1), real(arguments, 2), real(arguments, 3))));
		add("UnregisterStackedSound", soundSetter((sound, arguments) -> this.viewer.getStackedSounds()
				.unregister(sound, bool(arguments, 1), real(arguments, 2), real(arguments, 3))));
		ignored("SetCinematicAudio");
		add("KillSoundWhenDone", soundSetter((sound, arguments) -> sound.killWhenDone()));
	}

	static void applyLabelParameters(final CSound sound, final Element row) {
		if (row == null) {
			return;
		}
		// Dialogue and MIDI labels only carry a volume.
		if (!row.getField("Volume").isEmpty()) {
			sound.setVolume(row.getFieldValue("Volume"));
		}
		if (!row.getField("Pitch").isEmpty()) {
			sound.setPitch(row.getFieldFloatValue("Pitch"));
			sound.setPitchVariance(hasFlag(row, "RANDOMPITCH") ? row.getFieldFloatValue("PitchVariance") : 0f);
		}
		if (!row.getField("Channel").isEmpty()) {
			sound.setChannel(row.getFieldValue("Channel"));
		}
		if (!row.getField("MinDistance").isEmpty()) {
			sound.setDistances(row.getFieldFloatValue("MinDistance"), row.getFieldFloatValue("MaxDistance"));
			if (row.hasFieldValue("DistanceCutoff")) {
				sound.setDistanceCutoff(row.getFieldFloatValue("DistanceCutoff"));
			}
		}
	}

	private static boolean hasFlag(final Element row, final String flag) {
		for (final String value : row.getField("Flags").split("[,|]")) {
			if (flag.equals(value.trim())) {
				return true;
			}
		}
		return false;
	}

	private interface SoundSetter {
		void set(CSound sound, List<JassValue> arguments);
	}

	private static JassFunction soundSetter(final SoundSetter setter) {
		return (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			if (sound != null) {
				setter.set(sound, arguments);
			}
			return null;
		};
	}

	private void registerCamera() {
		final HandleJassType locationType = type("location");
		add("CameraSetSourceNoise", (arguments, globalScope, triggerScope) -> {
			camera().setSourceNoise(real(arguments, 0), real(arguments, 1), false);
			return null;
		});
		add("CameraSetTargetNoise", (arguments, globalScope, triggerScope) -> {
			camera().setTargetNoise(real(arguments, 0), real(arguments, 1), false);
			return null;
		});
		add("CameraSetSourceNoiseEx", (arguments, globalScope, triggerScope) -> {
			camera().setSourceNoise(real(arguments, 0), real(arguments, 1), bool(arguments, 2));
			return null;
		});
		add("CameraSetTargetNoiseEx", (arguments, globalScope, triggerScope) -> {
			camera().setTargetNoise(real(arguments, 0), real(arguments, 1), bool(arguments, 2));
			return null;
		});
		// The camera is the player's own (the mouse, the screen size move it), so a replay keeps what scripts read.
		add("GetCameraTargetPositionX",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(local(camera().getNoisyTargetX())));
		add("GetCameraTargetPositionY",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(local(camera().getNoisyTargetY())));
		add("GetCameraTargetPositionZ",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(local(camera().getNoisyTargetZ())));
		add("GetCameraEyePositionX",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(local(camera().getEyeX())));
		add("GetCameraEyePositionY",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(local(camera().getEyeY())));
		add("GetCameraEyePositionZ",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(local(camera().getEyeZ())));
		add("GetCameraTargetPositionLoc", (arguments, globalScope, triggerScope) -> new HandleJassValue(locationType,
				new AbilityPointTarget(local(camera().getNoisyTargetX()), local(camera().getNoisyTargetY()))));
		add("GetCameraEyePositionLoc", (arguments, globalScope, triggerScope) -> new HandleJassValue(locationType,
				new AbilityPointTarget(local(camera().getEyeX()), local(camera().getEyeY()))));
		// Measured: a smoothing factor does not change a timed pan (linear with it too). The original applies it to
		// the free camera, which has no such movement here.
		ignored("CameraSetSmoothingFactor", "CameraSetDepthOfFieldScale", "CameraSetFocalDistance",
				"BlzCameraSetupSetCameraType");
		// Measured: an empty name leaves a running model camera as it is.
		add("SetCinematicCamera", (arguments, globalScope, triggerScope) -> {
			final String file = string(arguments, 0);
			if ((file != null) && !file.isEmpty()) {
				final String mdx = War3MapViewer.mdx(file);
				if (this.viewer.dataSource.has(mdx) || this.viewer.dataSource.has(War3MapViewer.mdl(mdx))) {
					final com.etheller.warsmash.viewer5.handlers.mdx.MdxModel model = this.viewer.loadModelMdx(file);
					if ((model != null) && !model.getCameras().isEmpty()) {
						camera().setCinematicCamera(model.getCameras().get(0), model);
					}
				}
			}
			return null;
		});
		// Measured: neither changes the camera's rotation or the target the camera natives report, with the unit
		// turning or moving or the rotation running its time.
		ignored("SetCameraRotateMode", "SetCameraOrientController");
		add("BlzCameraSetupApplyForceDurationSmooth", (arguments, globalScope, triggerScope) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.camera.CustomCameraSetup setup = object(arguments, 0);
			if (setup != null) {
				camera().applyCameraSetupForceDuration(setup, bool(arguments, 1), real(arguments, 2));
			}
			return null;
		});
	}

	private MeleeUI meleeUI() {
		if (this.ui instanceof MeleeToggleUI) {
			return ((MeleeToggleUI) this.ui).getMeleeUI();
		}
		return this.ui instanceof MeleeUI ? (MeleeUI) this.ui : null;
	}

	private GameCameraManager camera() {
		return this.ui.getCameraManager();
	}

	private float local(final float value) {
		return this.viewer.replayedLocalValue(value);
	}

	private void registerWorld() {
		add("SetRandomSeed", (arguments, globalScope, triggerScope) -> {
			this.simulation.setRandomSeed(integer(arguments, 0));
			return null;
		});
		add("SetSkyModel", (arguments, globalScope, triggerScope) -> {
			this.viewer.setSkyModel(string(arguments, 0));
			return null;
		});
		add("MathRound", (arguments, globalScope, triggerScope) -> IntegerJassValue.of(Math.round(real(arguments, 0))));
		add("SkinManagerGetLocalPath", (arguments, globalScope, triggerScope) -> {
			// A key of the local player's race skin (war3skins.txt), e.g. AttackMarker in Human01.
			final String key = noneIfNull(string(arguments, 0));
			final GameUI gameUI = this.viewer.getGameUI();
			return new StringJassValue(
					(gameUI != null) && gameUI.hasSkinField(key) ? gameUI.getSkinField(key) : key);
		});
		add("BlzIsSelectionEnabled", (arguments, globalScope, triggerScope) -> BooleanJassValue.TRUE);
		add("EnableSelect", (arguments, globalScope, triggerScope) -> {
			final MeleeUI meleeUI = meleeUI();
			if (meleeUI != null) {
				meleeUI.enableSelect(bool(arguments, 0), bool(arguments, 1));
			}
			return null;
		});
		add("EnableDragSelect", (arguments, globalScope, triggerScope) -> {
			final MeleeUI meleeUI = meleeUI();
			if (meleeUI != null) {
				meleeUI.enableDragSelect(bool(arguments, 0), bool(arguments, 1));
			}
			return null;
		});
		add("EnablePreSelect", (arguments, globalScope, triggerScope) -> {
			final MeleeUI meleeUI = meleeUI();
			if (meleeUI != null) {
				meleeUI.enablePreSelect(bool(arguments, 0), bool(arguments, 1));
			}
			return null;
		});
		add("EnableMinimapFilterButtons", (arguments, globalScope, triggerScope) -> {
			final MeleeUI meleeUI = meleeUI();
			if (meleeUI != null) {
				meleeUI.enableMinimapFilterButtons(bool(arguments, 0), bool(arguments, 1));
			}
			return null;
		});
		add("SetCreepCampFilterState", (arguments, globalScope, triggerScope) -> {
			final MeleeUI meleeUI = meleeUI();
			if (meleeUI != null) {
				meleeUI.setCreepCampFilterState(bool(arguments, 0));
			}
			return null;
		});
		add("GetCreepCampFilterState", (arguments, globalScope, triggerScope) -> {
			final MeleeUI meleeUI = meleeUI();
			return BooleanJassValue.of((meleeUI != null) && meleeUI.getCreepCampFilterState());
		});
		add("SetDoodadAnimation", (arguments, globalScope, triggerScope) -> {
			this.viewer.setDoodadAnimation(new War3ID(integer(arguments, 3)).toString(), null, real(arguments, 0),
					real(arguments, 1), real(arguments, 2), bool(arguments, 4), noneIfNull(string(arguments, 5)));
			return null;
		});
		add("SetDoodadAnimationRect", (arguments, globalScope, triggerScope) -> {
			final Rectangle area = object(arguments, 0);
			if (area != null) {
				this.viewer.setDoodadAnimation(new War3ID(integer(arguments, 1)).toString(), area, 0, 0, 0, false,
						noneIfNull(string(arguments, 2)));
			}
			return null;
		});
		add("SetReservedLocalHeroButtons", (arguments, globalScope, triggerScope) -> {
			final MeleeUI meleeUI = meleeUI();
			if (meleeUI != null) {
				meleeUI.setReservedLocalHeroButtons(integer(arguments, 0));
			}
			return null;
		});
		add("ForceUIKey", (arguments, globalScope, triggerScope) -> {
			final MeleeUI meleeUI = meleeUI();
			if (meleeUI != null) {
				meleeUI.forceUIKey(string(arguments, 0));
			}
			return null;
		});
		registerUbersplats();
		registerTerrainDeformations();
		// Occlusion and the fog at the map edge have no drawing here yet; HD water and terrain fog extras do not exist
		// in the classic graphics.
		// The old fog natives and the water ripples have no counterpart in the classic graphics; a single player game
		// has no other player to wait for at a sync point, and the intro shot belongs to the loading screen.
		ignored("SetTerrainFog", "SetUnitFog", "SetWaterDeforms", "TriggerSyncStart", "TriggerSyncReady",
				"SetIntroShotText", "SetIntroShotModel");
		ignored("EnableOcclusion", "EnableWorldFogBoundary", "SetHDWaterParamsEx", "BlzSetTerrainFogDrawOverSky",
				"BlzSetTerrainFogMaxLinearDensity", "SetTerrainFogExV", "PreloadRefresh", "PreloadEndEx",
				"SyncSelections",
				"CreateCommandButtonEffect", "CreateUpgradeCommandButtonEffect", "CreateLearnCommandButtonEffect",
				"DestroyCommandButtonEffect", "TriggerRegisterCommandEvent", "TriggerRegisterUpgradeCommandEvent",
				"SetItemColor");
	}

	private interface UbersplatAction {
		void run(SimulationRenderComponentUbersplat splat, List<JassValue> arguments);
	}

	private void addUbersplatAction(final String name, final UbersplatAction action) {
		add(name, (arguments, globalScope, triggerScope) -> {
			final SimulationRenderComponentUbersplat splat = object(arguments, 0);
			if (splat != null) {
				action.run(splat, arguments);
			}
			return null;
		});
	}

	private void registerUbersplats() {
		final HandleJassType ubersplatType = type("ubersplat");
		add("CreateUbersplat", (arguments, globalScope, triggerScope) -> {
			final SimulationRenderComponentUbersplat.State state = new SimulationRenderComponentUbersplat.State();
			state.x = real(arguments, 0);
			state.y = real(arguments, 1);
			state.name = string(arguments, 2);
			state.red = integer(arguments, 3);
			state.green = integer(arguments, 4);
			state.blue = integer(arguments, 5);
			state.alpha = integer(arguments, 6);
			state.forcePaused = bool(arguments, 7);
			state.noBirthTime = bool(arguments, 8);
			state.handleId = (state.name != null) && (this.viewer.terrain.uberSplatTable.get(state.name) != null)
					? this.simulation.getHandleIdAllocator().createUbersplatId()
					: -1;
			if (state.handleId != -1) {
				state.imageId = this.simulation.getHandleIdAllocator().createImageId();
			}
			return new HandleJassValue(ubersplatType, this.viewer.createUbersplat(state));
		});
		addUbersplatAction("DestroyUbersplat", (splat, arguments) -> {
			final SimulationRenderComponentUbersplat.State state = splat.getState();
			if ((state != null) && !state.destroyed) {
				this.simulation.getHandleIdAllocator().freeUbersplatId(state.handleId);
				this.simulation.getHandleIdAllocator().freeImageId(state.imageId);
			}
			splat.destroy();
		});
		addUbersplatAction("ResetUbersplat", (splat, arguments) -> splat.reset());
		addUbersplatAction("FinishUbersplat", (splat, arguments) -> splat.finish());
		addUbersplatAction("ShowUbersplat", (splat, arguments) -> splat.show(bool(arguments, 1)));
		addUbersplatAction("SetUbersplatRender", (splat, arguments) -> splat.setRender(bool(arguments, 1)));
		addUbersplatAction("SetUbersplatRenderAlways",
				(splat, arguments) -> splat.setRenderAlways(bool(arguments, 1)));
	}

	private void registerTerrainDeformations() {
		final HandleJassType deformationType = type("terraindeformation");
		final CTerrainDeformations deformations = this.simulation.getTerrainDeformations();
		add("TerrainDeformCrater", (arguments, globalScope, triggerScope) -> deformationHandle(deformationType,
				deformations.crater(this.simulation, real(arguments, 0), real(arguments, 1), real(arguments, 2),
						real(arguments, 3), integer(arguments, 4), bool(arguments, 5))));
		add("TerrainDeformRipple", (arguments, globalScope, triggerScope) -> deformationHandle(deformationType,
				deformations.ripple(this.simulation, real(arguments, 0), real(arguments, 1), real(arguments, 2),
						real(arguments, 3), integer(arguments, 4), integer(arguments, 5), real(arguments, 6),
						real(arguments, 7), real(arguments, 8), bool(arguments, 9))));
		add("TerrainDeformWave", (arguments, globalScope, triggerScope) -> deformationHandle(deformationType,
				deformations.wave(this.simulation, real(arguments, 0), real(arguments, 1), real(arguments, 2),
						real(arguments, 3), real(arguments, 4), real(arguments, 5), real(arguments, 6),
						real(arguments, 7), integer(arguments, 8), integer(arguments, 9))));
		add("TerrainDeformRandom", (arguments, globalScope, triggerScope) -> deformationHandle(deformationType,
				deformations.random(this.simulation, real(arguments, 0), real(arguments, 1), real(arguments, 2),
						real(arguments, 3), real(arguments, 4), integer(arguments, 5), integer(arguments, 6))));
		add("TerrainDeformStop", (arguments, globalScope, triggerScope) -> {
			final CTerrainDeformations.Deformation deformation = object(arguments, 0);
			deformations.stop(this.simulation, deformation, integer(arguments, 1));
			return null;
		});
		add("TerrainDeformStopAll", (arguments, globalScope, triggerScope) -> {
			deformations.stopAll(this.simulation);
			return null;
		});
	}

	/** The original's first deformation handle is slot 0, which scripts see as null. */
	private static HandleJassValue deformationHandle(final HandleJassType type,
			final CTerrainDeformations.Deformation deformation) {
		return new HandleJassValue(type, deformation.getSlot() == 0 ? null : deformation);
	}

	private interface EffectSetter {
		void set(RenderSpellEffect effect, List<JassValue> arguments);
	}

	private void addEffectSetter(final String name, final EffectSetter setter) {
		add(name, (arguments, globalScope, triggerScope) -> {
			final ScriptEffect handle = object(arguments, 0);
			final RenderSpellEffect effect = handle == null ? null : handle.getLiveEffect();
			if (effect != null) {
				setter.set(effect, arguments);
			}
			return null;
		});
	}

	private void addEffectGetter(final String name, final java.util.function.ToDoubleFunction<RenderSpellEffect> getter) {
		add(name, (arguments, globalScope, triggerScope) -> {
			final ScriptEffect handle = object(arguments, 0);
			final RenderSpellEffect effect = handle == null ? null : handle.getLiveEffect();
			return RealJassValue.of(effect == null ? 0 : getter.applyAsDouble(effect));
		});
	}

	private void registerAllyColorFilter() {
		add("SetAllyColorFilterState", (arguments, globalScope, triggerScope) -> {
			this.viewer.getAllyColorFilter().setState(integer(arguments, 0));
			return null;
		});
		add("GetAllyColorFilterState", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(this.viewer.getAllyColorFilter().getState()));
	}

	private void registerEffects() {
		addEffectGetter("BlzGetLocalSpecialEffectX", RenderSpellEffect::getScriptX);
		addEffectGetter("BlzGetLocalSpecialEffectY", RenderSpellEffect::getScriptY);
		addEffectGetter("BlzGetLocalSpecialEffectZ", RenderSpellEffect::getScriptZ);
		addEffectSetter("BlzSetSpecialEffectX",
				(effect, arguments) -> effect.setScriptPosition(real(arguments, 1), null, null, false));
		addEffectSetter("BlzSetSpecialEffectY",
				(effect, arguments) -> effect.setScriptPosition(null, real(arguments, 1), null, false));
		addEffectSetter("BlzSetSpecialEffectZ",
				(effect, arguments) -> effect.setScriptPosition(null, null, real(arguments, 1), false));
		addEffectSetter("BlzSetSpecialEffectHeight",
				(effect, arguments) -> effect.setScriptPosition(null, null, real(arguments, 1), true));
		addEffectSetter("BlzSetSpecialEffectPosition", (effect, arguments) -> effect
				.setScriptPosition(real(arguments, 1), real(arguments, 2), real(arguments, 3), false));
		addEffectSetter("BlzSetSpecialEffectPositionLoc", (effect, arguments) -> {
			final AbilityPointTarget location = object(arguments, 1);
			if (location != null) {
				effect.setScriptPosition(location.x, location.y, null, false);
			}
		});
		addEffectSetter("BlzSetSpecialEffectScale",
				(effect, arguments) -> effect.getModelInstance().setUniformScale(real(arguments, 1)));
		addEffectSetter("BlzSetSpecialEffectColor", (effect, arguments) -> {
			final float[] color = effect.getModelInstance().vertexColor;
			effect.getModelInstance().setVertexColor(colorByte(arguments, 1), colorByte(arguments, 2),
					colorByte(arguments, 3), color[3]);
		});
		addEffectSetter("BlzSetSpecialEffectAlpha",
				(effect, arguments) -> effect.getModelInstance().setVertexAlpha(colorByte(arguments, 1)));
		addEffectSetter("BlzSetSpecialEffectColorByPlayer", (effect, arguments) -> {
			final CPlayer player = object(arguments, 1);
			if (player != null) {
				effect.getModelInstance().setTeamColor(player.getColor());
			}
		});
		addEffectSetter("BlzSetSpecialEffectTimeScale",
				(effect, arguments) -> effect.getModelInstance().setAnimationSpeed(real(arguments, 1)));
		addEffectSetter("BlzSetSpecialEffectTime", (effect, arguments) -> effect.setScriptTime(real(arguments, 1)));
		addEffectSetter("BlzSetSpecialEffectOrientation", (effect, arguments) -> effect
				.setScriptOrientation(real(arguments, 1), real(arguments, 2), real(arguments, 3)));
		addEffectSetter("BlzSetSpecialEffectYaw", (effect, arguments) -> effect.setScriptOrientation(
				real(arguments, 1), effect.getScriptPitch(), effect.getScriptRoll()));
		addEffectSetter("BlzSetSpecialEffectPitch", (effect, arguments) -> effect
				.setScriptOrientation(effect.getYaw(), real(arguments, 1), effect.getScriptRoll()));
		addEffectSetter("BlzSetSpecialEffectRoll", (effect, arguments) -> effect
				.setScriptOrientation(effect.getYaw(), effect.getScriptPitch(), real(arguments, 1)));
	}

	private static float colorByte(final List<JassValue> arguments, final int index) {
		return Math.max(0, Math.min(255, integer(arguments, index))) / 255f;
	}

	private void registerMinimapAndIndicators() {
		add("PingMinimap", (arguments, globalScope, triggerScope) -> {
			this.ui.pingMinimap(real(arguments, 0), real(arguments, 1), real(arguments, 2));
			return null;
		});
		add("PingMinimapEx", (arguments, globalScope, triggerScope) -> {
			this.ui.pingMinimap(real(arguments, 0), real(arguments, 1), real(arguments, 2), integer(arguments, 3),
					integer(arguments, 4), integer(arguments, 5), bool(arguments, 6));
			return null;
		});
		add("UnitAddIndicator", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				this.viewer.addIndicator(unit, integer(arguments, 1), integer(arguments, 2), integer(arguments, 3),
						integer(arguments, 4));
			}
			return null;
		});
		add("AddIndicator", (arguments, globalScope, triggerScope) -> {
			final CWidget widget = object(arguments, 0);
			if (widget != null) {
				this.viewer.addIndicator(widget, integer(arguments, 1), integer(arguments, 2), integer(arguments, 3),
						integer(arguments, 4));
			}
			return null;
		});
		final HandleJassType minimapiconType = type("minimapicon");
		add("CreateMinimapIconAtLoc", (arguments, globalScope, triggerScope) -> new HandleJassValue(minimapiconType,
				null));
		add("CreateMinimapIconOnUnit", (arguments, globalScope, triggerScope) -> new HandleJassValue(minimapiconType,
				null));
		ignored("DestroyMinimapIcon", "SetMinimapIconOrphanDestroy");
		add("SetAltMinimapIcon", (arguments, globalScope, triggerScope) -> {
			this.viewer.setAltMinimapIcon(string(arguments, 0));
			return null;
		});
		add("UnitSetUsesAltIcon", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				unit.setUsesAltMinimapIcon(bool(arguments, 1));
			}
			return null;
		});
	}

	private void registerUnits() {
		final HandleJassType raceType = type("race");
		add("SetUnitRescuable", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CPlayer player = object(arguments, 1);
			if ((unit != null) && (player != null)) {
				unit.setRescuable(player.getId(), bool(arguments, 2));
			}
			return null;
		});
		add("SetUnitRescueRange", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				unit.setRescueRange(real(arguments, 1));
			}
			return null;
		});
		add("SetUnitPathing", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				unit.setPathingDisabled(!bool(arguments, 1));
			}
			return null;
		});
		final HandleJassType eventType = type("event");
		add("TriggerRegisterUnitInRange", (arguments, globalScope, triggerScope) -> {
			final Trigger trigger = object(arguments, 0);
			final CUnit unit = object(arguments, 1);
			final TriggerBooleanExpression filter = object(arguments, 3);
			if ((trigger == null) || (unit == null)) {
				return new HandleJassValue(eventType, null);
			}
			final CUnitInRangeEvent event = new CUnitInRangeEvent(globalScope, this.simulation, unit, real(arguments, 2),
					trigger, filter);
			this.simulation.addUnitInRangeEvent(event);
			return new HandleJassValue(eventType, event);
		});
		add("GetUnitDefaultMoveSpeed", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			return RealJassValue.of(unit == null ? 0 : unit.getUnitType().getSpeed());
		});
		add("GetUnitRace", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			return new HandleJassValue(raceType, unit == null ? null : jassRace(unit.getUnitType().getRace()));
		});
		add("IsUnitIdType", (arguments, globalScope, triggerScope) -> {
			final CUnitType unitType = this.simulation.getUnitData().getUnitType(new War3ID(integer(arguments, 0)));
			final CUnitTypeJass whichType = object(arguments, 1);
			return BooleanJassValue.of((unitType != null) && (whichType != null) && isOfType(unitType, whichType));
		});
		add("IsHeroUnitId", (arguments, globalScope, triggerScope) -> {
			final CUnitType unitType = this.simulation.getUnitData().getUnitType(new War3ID(integer(arguments, 0)));
			return BooleanJassValue.of((unitType != null) && unitType.isHero());
		});
		add("GetHeroProperName", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if ((unit == null) || (unit.getHeroData() == null)) {
				// Measured on 1.29: null, not an empty string, for a null unit or a unit that is not a hero.
				return JassType.STRING.getNullValue();
			}
			final CAbilityHero hero = unit.isIllusion() ? null : unit.getHeroData();
			return new StringJassValue(hero == null ? "" : noneIfNull(hero.getProperName()));
		});
		add("GetPlayerUnitCount", (arguments, globalScope, triggerScope) -> {
			final CPlayer player = object(arguments, 0);
			if (player == null) {
				return IntegerJassValue.ZERO;
			}
			final boolean includeIncomplete = bool(arguments, 1);
			int count = 0;
			// Measured on 1.29: structures are not counted (GetPlayerStructureCount has them); a reincarnating unit is.
			for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
				if ((unit.getPlayerIndex() == player.getId()) && (!unit.isDead() || unit.isFalseDeath())
						&& !unit.isAnimatedCorpse()) {
					if (!unit.isBuilding() && (includeIncomplete || !unit.isConstructing())) {
						count++;
					}
					if (includeIncomplete) {
						for (int i = 0; i < WarsmashConstants.BUILD_QUEUE_SIZE; i++) {
							if (unit.getQueuedTrainingUnitTypeId(i) != null) {
								count++;
							}
						}
					}
				}
			}
			return IntegerJassValue.of(count);
		});
		add("IsItemOwned", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			return BooleanJassValue.of((item != null) && (item.getContainedUnit() != null));
		});
		add("UnitStripHeroLevel", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CAbilityHero hero = unit == null ? null : unit.getHeroData();
			final int levels = integer(arguments, 1);
			if ((hero == null) || (levels <= 0)) {
				return BooleanJassValue.FALSE;
			}
			return BooleanJassValue.of(hero.stripLevels(this.simulation, unit, levels));
		});
		add("ReviveHeroLoc", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final AbilityPointTarget location = object(arguments, 1);
			if ((unit == null) || (location == null) || (unit.getHeroData() == null) || !unit.isDead()) {
				return BooleanJassValue.FALSE;
			}
			unit.reviveHeroAtPoint(this.simulation, location.x, location.y, bool(arguments, 2));
			return BooleanJassValue.TRUE;
		});
		add("UnitWakeUp", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				unit.wakeUp(this.simulation);
			}
			return null;
		});
		add("UnitIsSleeping", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			return BooleanJassValue.of((unit != null) && unit.isNaturallyAsleep());
		});
		add("UnitIgnoreAlarm", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		add("UnitIgnoreAlarmToggled", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		add("UnitSuspendDecay", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				unit.setDecays(!bool(arguments, 1));
			}
			return null;
		});
		add("UnitResetCooldown", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				// Measured on 1.29: an order issued in the same tick still sees the cooldown.
				final CTimer reset = new CTimer() {
					@Override
					public void onFire(final CSimulation game) {
						unit.resetCooldowns();
					}
				};
				reset.setTimeoutTime(0);
				reset.start(this.simulation);
			}
			return null;
		});
		add("UnitShareVision", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CPlayer player = object(arguments, 1);
			if ((unit != null) && (player != null)) {
				this.simulation.setUnitSharedVision(unit, player, bool(arguments, 2));
			}
			return null;
		});
		add("RemoveAllGuardPositions", (arguments, globalScope, triggerScope) -> {
			final CPlayer player = object(arguments, 0);
			if (player != null) {
				for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
					if (unit.getPlayerIndex() == player.getId()) {
						unit.removeGuardPosition();
					}
				}
			}
			return null;
		});
		final HandleJassType unitHandle = type("unit");
		add("GetSummonedUnit", (arguments, globalScope, triggerScope) -> new HandleJassValue(unitHandle,
				((CommonTriggerExecutionScope) triggerScope).getSummonedUnit()));
		add("GetSummoningUnit", (arguments, globalScope, triggerScope) -> new HandleJassValue(unitHandle,
				((CommonTriggerExecutionScope) triggerScope).getSummoningUnit()));
		add("GetRevivingUnit", (arguments, globalScope, triggerScope) -> new HandleJassValue(unitHandle,
				((CommonTriggerExecutionScope) triggerScope).getRevivingUnit()));
		add("UnitUseItemPoint", (arguments, globalScope, triggerScope) -> {
			final Object location = new AbilityPointTarget(real(arguments, 2), real(arguments, 3));
			final java.util.List<JassValue> locArguments = java.util.Arrays.asList(arguments.get(0),
					arguments.get(1), new HandleJassValue(type("location"), location));
			// 1.29 common.j declares only UnitUseItemPoint; UnitUseItemPointLoc is a Blizzard.j function there.
			final Integer locId = globalScope.getNativeId("UnitUseItemPointLoc");
			if (locId != null) {
				globalScope.getNativeById(locId).call(locArguments, globalScope, triggerScope);
			}
			else {
				this.program.getJassNativeManager().getUndeclaredNative("UnitUseItemPointLoc").call(locArguments,
						globalScope, triggerScope);
			}
			// Measured on 1.29: false even when the item is used (instant, walking to the point, or channeling).
			return BooleanJassValue.FALSE;
		});
		add("TriggerRegisterPlayerChatEvent", (arguments, globalScope, triggerScope) -> {
			final Trigger whichTrigger = object(arguments, 0);
			final CPlayer whichPlayer = object(arguments, 1);
			if ((whichTrigger == null) || (whichPlayer == null)) {
				return new HandleJassValue(eventType, null);
			}
			final CPlayerChatEvent event = new CPlayerChatEvent(globalScope, whichPlayer, whichTrigger,
					mapString(arguments, 2), bool(arguments, 3));
			whichPlayer.addEvent(event);
			return new HandleJassValue(eventType, event);
		});
		add("GetEventPlayerChatString", (arguments, globalScope, triggerScope) -> new StringJassValue(
				noneIfNull(((CommonTriggerExecutionScope) triggerScope).getChatString())));
		add("GetEventPlayerChatStringMatched", (arguments, globalScope, triggerScope) -> new StringJassValue(
				noneIfNull(((CommonTriggerExecutionScope) triggerScope).getChatStringMatched())));
		add("BlzIsUnitInvulnerable", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			return BooleanJassValue.of((unit != null) && unit.isInvulnerable());
		});
		add("BlzSetUnitMaxHP", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				unit.setMaximumLife(integer(arguments, 1));
			}
			return null;
		});
		add("BlzUnitDisableAbility", (arguments, globalScope, triggerScope) -> {
			final CAbility ability = abilityOf(object(arguments, 0), integer(arguments, 1));
			if (ability != null) {
				ability.setDisabled(bool(arguments, 2), CAbilityDisableType.TRIGGER);
				ability.setIconShowing(!bool(arguments, 3));
			}
			return null;
		});
		add("BlzUnitHideAbility", (arguments, globalScope, triggerScope) -> {
			final CAbility ability = abilityOf(object(arguments, 0), integer(arguments, 1));
			if (ability != null) {
				ability.setIconShowing(!bool(arguments, 2));
			}
			return null;
		});
		add("UnitSetConstructionProgress", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if ((unit != null) && unit.isConstructing()) {
				final float fraction = integer(arguments, 1) / 100f;
				unit.setConstructionProgress(unit.getUnitType().getBuildTime() * fraction);
				// Measured: the life is set to the starting life plus the same fraction of the maximum, so a
				// building set to 99 % finishes at full life.
				unit.setLife(this.simulation, Math.min(unit.getMaximumLife(),
						unit.getMaximumLife() * (WarsmashConstants.BUILDING_CONSTRUCT_START_LIFE + fraction)));
			}
			return null;
		});
		add("UnitSetUpgradeProgress", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if ((unit != null) && unit.isUpgrading()) {
				final int buildTime = this.simulation.getUnitData().getUnitType(unit.getUpgradeIdType()).getBuildTime();
				unit.setConstructionProgress((buildTime * integer(arguments, 1)) / 100f);
			}
			return null;
		});
		// The unit field table is Reforged data the classic game has no counterpart of: nothing was applied.
		add("BlzSetUnitRealField", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		// Hero and unit overrides the classic data has no field for in the interface yet.
		ignored("BlzShowUnitTeamGlow");
		add("SetPlayerHandicapDamage", (arguments, globalScope, triggerScope) -> {
			final CPlayer player = object(arguments, 0);
			if (player != null) {
				player.setHandicapDamage(real(arguments, 1));
			}
			return null;
		});
		add("GetPlayerHandicapDamage", (arguments, globalScope, triggerScope) -> {
			final CPlayer player = object(arguments, 0);
			return RealJassValue.of(player == null ? 0 : player.getHandicapDamage());
		});
		add("SetPlayerHandicapReviveTime", (arguments, globalScope, triggerScope) -> {
			final CPlayer player = object(arguments, 0);
			if (player != null) {
				player.setHandicapReviveTime(real(arguments, 1));
			}
			return null;
		});
		add("GetPlayerHandicapReviveTime", (arguments, globalScope, triggerScope) -> {
			final CPlayer player = object(arguments, 0);
			return RealJassValue.of(player == null ? 0 : player.getHandicapReviveTime());
		});
		final JassFunction addSleep = (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				unit.setCanSleep(bool(arguments, 1));
				if (!unit.canSleep()) {
					unit.wakeFromNaturalSleep(this.simulation);
				}
				else {
					unit.updateNightSleep(this.simulation);
				}
			}
			return null;
		};
		add("UnitAddSleep", addSleep);
		// Measured on 1.29: the permanent variant sets the same night sleep flag, and UnitCanSleepPerm answers false
		// even right after UnitAddSleepPerm(true); a creep set so still wakes at daybreak.
		add("UnitAddSleepPerm", addSleep);
		add("UnitCanSleep", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			return BooleanJassValue.of((unit != null) && unit.canSleep());
		});
		add("UnitCanSleepPerm", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		add("BlzSetUnitArmor", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				unit.setTotalDefense(real(arguments, 1));
			}
			return null;
		});
		add("BlzSetUnitName", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final String name = mapString(arguments, 1);
			// Measured: an empty name is ignored and the unit keeps the name it had.
			if ((unit != null) && (name != null) && !name.isEmpty()) {
				unit.setName(name);
			}
			return null;
		});
	}

	private CRace jassRace(final CUnitRace race) {
		final CRaceManager raceManager = this.viewer.getGameData().getRaceManager();
		switch (race) {
		case HUMAN:
			return raceManager.getRace(1);
		case ORC:
			return raceManager.getRace(2);
		case UNDEAD:
			return raceManager.getRace(3);
		case NIGHTELF:
			return raceManager.getRace(4);
		case DEMON:
			return raceManager.getRace(5);
		default:
			return raceManager.getRace(7);
		}
	}

	private static boolean isOfType(final CUnitType unitType, final CUnitTypeJass whichType) {
		switch (whichType) {
		case HERO:
			return unitType.isHero();
		case STRUCTURE:
			return unitType.isBuilding();
		default:
			for (final CUnitClassification classification : unitType.getClassifications()) {
				if (classification.name().equals(whichType.name())) {
					return true;
				}
			}
			return false;
		}
	}

	private static CAbility abilityOf(final CUnit unit, final int rawcode) {
		if (unit != null) {
			final War3ID alias = new War3ID(rawcode);
			for (final CAbility ability : unit.getAbilities()) {
				if (alias.equals(ability.getAlias())) {
					return ability;
				}
			}
		}
		return null;
	}

	private void registerDestructables() {
		final HandleJassType destructableType = type("destructable");
		add("CreateDeadDestructable", (arguments, globalScope, triggerScope) -> {
			final CDestructable destructable = this.simulation.createDestructable(new War3ID(integer(arguments, 0)),
					real(arguments, 1), real(arguments, 2), real(arguments, 3), real(arguments, 4), integer(arguments, 5));
			destructable.setLife(this.simulation, 0);
			return new HandleJassValue(destructableType, destructable);
		});
		add("CreateDeadDestructableZ", (arguments, globalScope, triggerScope) -> {
			final CDestructable destructable = this.simulation.createDestructableZ(new War3ID(integer(arguments, 0)),
					real(arguments, 1), real(arguments, 2), real(arguments, 3), real(arguments, 4), real(arguments, 5),
					integer(arguments, 6));
			destructable.setLife(this.simulation, 0);
			return new HandleJassValue(destructableType, destructable);
		});
		// The 3.0 variants, in the argument order of common.j: object id, x, y, [z], face, [roll, pitch], scale,
		// variation, [skin id], [color]. As for live destructables, the skin id picks the type that is shown.
		for (final String name : new String[] { "BlzCreateDeadDestructableWithSkin",
				"BlzCreateDeadDestructableZWithSkin", "BlzCreateDeadDestructablePitchRoll",
				"BlzCreateDeadDestructableZPitchRoll", "BlzCreateDeadDestructableWithSkinPitchRoll",
				"BlzCreateDeadDestructableZWithSkinPitchRoll", "BlzCreateDeadDestructableWithColor",
				"BlzCreateDeadDestructableZWithColor", "BlzCreateDeadDestructableWithSkinColor",
				"BlzCreateDeadDestructableZWithSkinColor", "BlzCreateDeadDestructablePitchRollWithColor",
				"BlzCreateDeadDestructableZPitchRollWithColor", "BlzCreateDeadDestructableWithSkinPitchRollColor",
				"BlzCreateDeadDestructableZWithSkinPitchRollColor" }) {
			final boolean hasZ = name.startsWith("BlzCreateDeadDestructableZ");
			final boolean hasPitchRoll = name.contains("PitchRoll");
			final boolean hasSkin = name.contains("WithSkin");
			add(name, (arguments, globalScope, triggerScope) -> {
				int index = 0;
				int rawcode = integer(arguments, index++);
				final float x = real(arguments, index++);
				final float y = real(arguments, index++);
				final float z = hasZ ? real(arguments, index++) : 0;
				final float face = real(arguments, index++);
				if (hasPitchRoll) {
					index += 2;
				}
				final float scale = real(arguments, index++);
				final int variation = integer(arguments, index++);
				if (hasSkin) {
					rawcode = integer(arguments, index);
				}
				final CDestructable destructable = hasZ
						? this.simulation.createDestructableZ(new War3ID(rawcode), x, y, z, face, scale, variation)
						: this.simulation.createDestructable(new War3ID(rawcode), x, y, face, scale, variation);
				destructable.setLife(this.simulation, 0);
				return new HandleJassValue(destructableType, destructable);
			});
		}
		add("SetDestructableVertexColor", (arguments, globalScope, triggerScope) -> {
			final CDestructable destructable = object(arguments, 0);
			final RenderDestructable peer = destructable == null ? null : this.viewer.getRenderPeer(destructable);
			if ((peer != null) && (peer.instance instanceof com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance)) {
				((com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance) peer.instance).setVertexColor(
						integer(arguments, 1) / 255f, integer(arguments, 2) / 255f, integer(arguments, 3) / 255f,
						integer(arguments, 4) / 255f);
			}
			return null;
		});
		add("SetDestructableMaxLife", (arguments, globalScope, triggerScope) -> {
			final CDestructable destructable = object(arguments, 0);
			if (destructable != null) {
				destructable.setMaxLife(this.simulation, real(arguments, 1));
			}
			return null;
		});
		add("GetDestructableOccluderHeight", (arguments, globalScope, triggerScope) -> {
			final CDestructable destructable = object(arguments, 0);
			return RealJassValue.of(destructable == null ? 0 : destructable.getOccluderHeight());
		});
		add("SetDestructableOccluderHeight", (arguments, globalScope, triggerScope) -> {
			final CDestructable destructable = object(arguments, 0);
			if (destructable != null) {
				destructable.setOccluderHeight(real(arguments, 1));
			}
			return null;
		});
		add("ShowDestructable", (arguments, globalScope, triggerScope) -> {
			final CDestructable destructable = object(arguments, 0);
			if (destructable != null) {
				final RenderDestructable peer = this.viewer.getRenderPeer(destructable);
				if (peer != null) {
					peer.setHidden(!bool(arguments, 1));
				}
			}
			return null;
		});
		add("QueueDestructableAnimation", (arguments, globalScope, triggerScope) -> {
			final CDestructable destructable = object(arguments, 0);
			if (destructable != null) {
				final RenderDestructable peer = this.viewer.getRenderPeer(destructable);
				if (peer != null) {
					peer.queueAnimation(noneIfNull(string(arguments, 1)));
				}
			}
			return null;
		});
		add("SetDestructableAnimationSpeed", (arguments, globalScope, triggerScope) -> {
			final CDestructable destructable = object(arguments, 0);
			if (destructable != null) {
				final RenderDestructable peer = this.viewer.getRenderPeer(destructable);
				if (peer != null) {
					peer.setAnimationSpeed(real(arguments, 1));
				}
			}
			return null;
		});
	}

	private void registerLeaderboards() {
		final HandleJassType leaderboardType = type("leaderboard");
		add("CreateLeaderboard", (arguments, globalScope, triggerScope) -> new HandleJassValue(leaderboardType,
				this.simulation.getHandleIdAllocator().assign(new CLeaderboard())));
		add("DestroyLeaderboard", (arguments, globalScope, triggerScope) -> {
			final CLeaderboard board = object(arguments, 0);
			if (board != null) {
				board.setDisplayed(false);
				board.clear();
				this.playerLeaderboards.values().removeIf(board::equals);
				this.ui.setLeaderboard(null);
				this.simulation.getHandleIdAllocator().release(board);
			}
			return null;
		});
		add("LeaderboardDisplay", boardSetter((board, arguments) -> {
			board.setDisplayed(bool(arguments, 1));
			this.ui.setLeaderboard(board.isDisplayed() ? board : null);
		}));
		add("IsLeaderboardDisplayed", (arguments, globalScope, triggerScope) -> {
			final CLeaderboard board = object(arguments, 0);
			return BooleanJassValue.of((board != null) && board.isDisplayed());
		});
		add("LeaderboardGetItemCount", (arguments, globalScope, triggerScope) -> {
			final CLeaderboard board = object(arguments, 0);
			return IntegerJassValue.of(board == null ? 0 : board.getItemCount());
		});
		add("LeaderboardSetSizeByItemCount",
				boardSetter((board, arguments) -> board.setSizeByItemCount(integer(arguments, 1))));
		add("LeaderboardAddItem", boardSetter((board, arguments) -> {
			final CPlayer player = object(arguments, 3);
			board.addItem(noneIfNull(mapString(arguments, 1)), integer(arguments, 2), player == null ? -1 : player.getId());
		}));
		add("LeaderboardRemoveItem", boardSetter((board, arguments) -> board.removeItem(integer(arguments, 1))));
		add("LeaderboardRemovePlayerItem", boardSetter((board, arguments) -> {
			final CPlayer player = object(arguments, 1);
			if (player != null) {
				board.removePlayerItem(player.getId());
			}
		}));
		add("LeaderboardClear", boardSetter((board, arguments) -> board.clear()));
		add("LeaderboardSortItemsByValue", boardSetter((board, arguments) -> board.sortByValue(bool(arguments, 1))));
		add("LeaderboardSortItemsByPlayer", boardSetter((board, arguments) -> board.sortByPlayer(bool(arguments, 1))));
		add("LeaderboardSortItemsByLabel", boardSetter((board, arguments) -> board.sortByLabel(bool(arguments, 1))));
		add("LeaderboardHasPlayerItem", (arguments, globalScope, triggerScope) -> {
			final CLeaderboard board = object(arguments, 0);
			final CPlayer player = object(arguments, 1);
			return BooleanJassValue.of((board != null) && (player != null) && board.hasPlayerItem(player.getId()));
		});
		add("LeaderboardGetPlayerIndex", (arguments, globalScope, triggerScope) -> {
			final CLeaderboard board = object(arguments, 0);
			final CPlayer player = object(arguments, 1);
			return IntegerJassValue.of((board == null) || (player == null) ? -1 : board.getPlayerIndex(player.getId()));
		});
		add("LeaderboardSetLabel", boardSetter((board, arguments) -> board.setLabel(mapString(arguments, 1))));
		add("LeaderboardGetLabelText", (arguments, globalScope, triggerScope) -> {
			final CLeaderboard board = object(arguments, 0);
			return new StringJassValue(board == null ? "" : board.getLabel());
		});
		add("PlayerSetLeaderboard", (arguments, globalScope, triggerScope) -> {
			final CPlayer player = object(arguments, 0);
			final CLeaderboard board = object(arguments, 1);
			if (player != null) {
				if (board == null) {
					this.playerLeaderboards.remove(player.getId());
				}
				else {
					this.playerLeaderboards.put(player.getId(), board);
				}
			}
			return null;
		});
		add("PlayerGetLeaderboard", (arguments, globalScope, triggerScope) -> {
			final CPlayer player = object(arguments, 0);
			return new HandleJassValue(leaderboardType,
					player == null ? null : this.playerLeaderboards.get(player.getId()));
		});
		add("LeaderboardSetLabelColor", boardSetter((board, arguments) -> board.setLabelColor(color(arguments, 1))));
		add("LeaderboardSetValueColor", boardSetter((board, arguments) -> board.setValueColor(color(arguments, 1))));
		add("LeaderboardSetStyle", boardSetter((board, arguments) -> board.setStyle(bool(arguments, 1),
				bool(arguments, 2), bool(arguments, 3), bool(arguments, 4))));
		add("LeaderboardSetItemValue",
				boardSetter((board, arguments) -> board.setItemValue(integer(arguments, 1), integer(arguments, 2))));
		add("LeaderboardSetItemLabel", boardSetter(
				(board, arguments) -> board.setItemLabel(integer(arguments, 1), noneIfNull(mapString(arguments, 2)))));
		add("LeaderboardSetItemStyle", boardSetter((board, arguments) -> board.setItemStyle(integer(arguments, 1),
				bool(arguments, 2), bool(arguments, 3), bool(arguments, 4))));
		add("LeaderboardSetItemLabelColor", boardSetter(
				(board, arguments) -> board.setItemLabelColor(integer(arguments, 1), color(arguments, 2))));
		add("LeaderboardSetItemValueColor", boardSetter(
				(board, arguments) -> board.setItemValueColor(integer(arguments, 1), color(arguments, 2))));
	}

	private static JassFunction boardSetter(final BiConsumer<CLeaderboard, List<JassValue>> setter) {
		return (arguments, globalScope, triggerScope) -> {
			final CLeaderboard board = object(arguments, 0);
			if (board != null) {
				setter.accept(board, arguments);
			}
			return null;
		};
	}

	private void registerTimerDialogs() {
		add("DestroyTimerDialog", (arguments, globalScope, triggerScope) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog dialog = object(arguments, 0);
			if (dialog != null) {
				dialog.setVisible(false);
				this.ui.removeTimerDialog(dialog);
				this.simulation.getHandleIdAllocator().release(dialog);
			}
			return null;
		});
		add("TimerDialogSetTitleColor", (arguments, globalScope, triggerScope) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog dialog = object(arguments, 0);
			if (dialog != null) {
				dialog.setTitleColor(color(arguments, 1));
			}
			return null;
		});
		add("TimerDialogSetTimeColor", (arguments, globalScope, triggerScope) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog dialog = object(arguments, 0);
			if (dialog != null) {
				dialog.setTimeColor(color(arguments, 1));
			}
			return null;
		});
	}
}
