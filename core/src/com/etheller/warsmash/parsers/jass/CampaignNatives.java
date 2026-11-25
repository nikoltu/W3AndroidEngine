package com.etheller.warsmash.parsers.jass;

import com.badlogic.gdx.files.FileHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.function.Consumer;
import java.util.Map;
import java.util.function.BiConsumer;
import com.badlogic.gdx.math.Rectangle;

import com.etheller.interpreter.ast.function.ConvertedConstant;
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
import com.etheller.warsmash.viewer5.FogSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.UnitSound;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.CustomCameraSetup;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.GameCameraManager;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.LocalUiLink;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderEvent;
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
import com.etheller.warsmash.viewer5.handlers.w3x.ui.ScriptMinimapIcon;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCache;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CheckpointSaves;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CLeaderboard;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CStoredUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerChatEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.SoundLabelParams;
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
	/** How long reads go to the render thread after a shake starts (the script names no end for it). */
	private static final float NOISE_SECONDS = 20f;
	/** How the natives that reach the local interface do it; see {@link LocalUiLink}. */
	private final LocalUiLink link;
	private final SoundVoices voices;
	private final Map<Integer, CLeaderboard> playerLeaderboards = new HashMap<>();

	private CampaignNatives(final JassProgram program, final War3MapViewer viewer, final WarsmashUI ui) {
		this.program = program;
		this.globals = program.getGlobalScope();
		this.viewer = viewer;
		this.ui = ui;
		this.simulation = viewer.simulation;
		this.link = LocalUiLink.of(viewer, ui);
		this.voices = new SoundVoices(viewer, ui, this.link);
	}

	static void register(final JassProgram program, final War3MapViewer viewer, final WarsmashUI ui) {
		final CampaignNatives natives = new CampaignNatives(program, viewer, ui);
		natives.registerGameCache();
		GameCacheSyncNatives.register(program, viewer, ui);
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

	/**
	 * A native that touches objects only the render thread may use. With the simulation on its own thread the caller
	 * waits while the render thread runs it; the simulation state it needs must be read before it is called.
	 */
	private void addRender(final String name, final JassFunction function) {
		add(name, (arguments, globalScope, triggerScope) -> this.link.onRender(name,
				() -> function.call(arguments, globalScope, triggerScope)));
	}

	/** As {@link #addRender}, but the commands the script sent before it (an effect it made) are run first. */
	private void addAfterCommands(final String name, final JassFunction function) {
		add(name, (arguments, globalScope, triggerScope) -> this.link.commands().call(name,
				() -> function.call(arguments, globalScope, triggerScope)));
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
		addRender("PlayModelCinematic", (arguments, globalScope, triggerScope) -> {
			this.viewer.playModelCinematic(string(arguments, 0));
			return null;
		});
		addRender("BlzPlayModelCinematicGameAtPosition", (arguments, globalScope, triggerScope) -> {
			this.viewer.playModelCinematicAtPosition(string(arguments, 0), real(arguments, 1), real(arguments, 2),
					real(arguments, 3), real(arguments, 4));
			return null;
		});
		addRender("BlzPreloadModelCinematicGame", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(this.viewer.preloadModelCinematic(string(arguments, 0))));
		addRender("BlzGetModelCinematicGameShotCount", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(this.viewer.getModelCinematicShotCount()));
		addRender("BlzGetModelCinematicGameCurrentShot", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(this.viewer.getModelCinematicCurrentShot()));
		addRender("BlzGetModelCinematicGameRemainingTime", (arguments, globalScope, triggerScope) -> RealJassValue
				.of(this.viewer.getModelCinematicRemainingTime()));
		addRender("BlzSetCinematicEnabledDE", (arguments, globalScope, triggerScope) -> {
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
		addRender("ReloadGame", (arguments, globalScope, triggerScope) -> {
			this.viewer.runAfterPendingSave(() -> {
				this.viewer.setRestartAsNextLevel();
				this.ui.endGame(false);
			});
			return null;
		});
		add("DisplayLoadDialog", (arguments, globalScope, triggerScope) -> {
			this.link.send(RenderEvent.DIALOG, () -> {
				final MeleeUI meleeUI = meleeUI();
				if (meleeUI != null) {
					meleeUI.displayLoadDialog();
				}
			});
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
		addRender("ForceCampaignSelectScreen", (arguments, globalScope, triggerScope) -> {
			this.viewer.getGameExit().forceCampaignSelectScreen();
			return null;
		});
		addRender("ChangeLevel", (arguments, globalScope, triggerScope) -> {
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
		addRender("SaveGame", (arguments, globalScope, triggerScope) -> {
			this.viewer.requestSaveGame(CSaveStorage.savedGame(noneIfNull(string(arguments, 0))));
			return null;
		});
		addRender("LoadGame", (arguments, globalScope, triggerScope) -> {
			this.viewer.requestLoadGame(CSaveStorage.savedGame(noneIfNull(string(arguments, 0))), true);
			return null;
		});
		addRender("SaveGameCheckpoint", (arguments, globalScope, triggerScope) -> {
			final FileHandle checkpoint = CSaveStorage.savedGame(noneIfNull(string(arguments, 0)));
			this.viewer.requestSaveGame(checkpoint);
			CheckpointSaves.delete(this.viewer.scriptUiState.getCheckpointSaves().record(checkpoint));
			return null;
		});
		addRender("DisableRestartMission", (arguments, globalScope, triggerScope) -> {
			this.viewer.setRestartMissionDisabled(bool(arguments, 0));
			return null;
		});
		addRender("DoNotSaveReplay", (arguments, globalScope, triggerScope) -> {
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

	/** What a sound native changes, read from its arguments when it is called: it runs again on the voice later. */
	private interface SoundChange {
		Consumer<CSound> read(List<JassValue> arguments);
	}

	private void soundSetter(final String name, final SoundChange change) {
		add(name, (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			if (sound != null) {
				this.voices.apply(sound, change.read(arguments));
			}
			return null;
		});
	}

	private void registerSounds() {
		// The World Editor writes this instead of the separate setters when a sound keeps its label's defaults; the
		// files and the looping come from CreateSound.
		soundSetter("SetSoundParamsFromLabel", arguments -> {
			final String label = string(arguments, 1);
			SoundLabelParams known = this.viewer.cachedSoundLabelParams(label);
			if (known == null) {
				known = this.link.onRender("SoundLabelParams", () -> this.viewer.getSoundLabelParams(label));
			}
			final SoundLabelParams params = known;
			return sound -> params.applyTo(sound);
		});
		soundSetter("SetSoundVolume", arguments -> {
			final int volume = integer(arguments, 1);
			return sound -> sound.setVolume(volume);
		});
		soundSetter("SetSoundPitch", arguments -> {
			final float pitch = real(arguments, 1);
			return sound -> sound.setPitch(pitch);
		});
		soundSetter("SetSoundChannel", arguments -> {
			final int channel = integer(arguments, 1);
			return sound -> sound.setChannel(channel);
		});
		soundSetter("SetSoundPosition", arguments -> {
			final float x = real(arguments, 1), y = real(arguments, 2), z = real(arguments, 3);
			return sound -> sound.setPosition(x, y, z);
		});
		soundSetter("SetSoundPlayPosition", arguments -> {
			final int millisecs = integer(arguments, 1);
			return sound -> sound.setPlayPosition(millisecs);
		});
		soundSetter("SetSoundVelocity", arguments -> {
			final float x = real(arguments, 1), y = real(arguments, 2), z = real(arguments, 3);
			return sound -> sound.setVelocity(x, y, z);
		});
		soundSetter("SetSoundDistances", arguments -> {
			final float min = real(arguments, 1), max = real(arguments, 2);
			return sound -> sound.setDistances(min, max);
		});
		soundSetter("SetSoundDistanceCutoff", arguments -> {
			final float cutoff = real(arguments, 1);
			return sound -> sound.setDistanceCutoff(cutoff);
		});
		soundSetter("SetSoundConeAngles", arguments -> {
			final float inside = real(arguments, 1), outside = real(arguments, 2);
			final int outsideVolume = integer(arguments, 3);
			return sound -> sound.setConeAngles(inside, outside, outsideVolume);
		});
		soundSetter("SetSoundConeOrientation", arguments -> {
			final float x = real(arguments, 1), y = real(arguments, 2), z = real(arguments, 3);
			return sound -> sound.setConeOrientation(x, y, z);
		});
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
				this.voices.attach(sound, unit);
			}
			return null;
		});
		add("GetSoundIsPlaying", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			return BooleanJassValue.of((sound != null) && sound.isPlaying(this.simulation.getGameTurnTick()));
		});
		add("GetSoundFileDuration", (arguments, globalScope, triggerScope) -> {
			final String fileName = string(arguments, 0);
			return IntegerJassValue.of(fileName == null ? 0 : Math.round(this.voices.lengthOfFile(fileName) * 1000));
		});
		add("SetThematicMusicVolume", (arguments, globalScope, triggerScope) -> {
			final int volume = integer(arguments, 0);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> this.ui.setMusicVolume(volume));
			return null;
		});
		add("GetSoundIsLoading", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			return BooleanJassValue.of((sound != null) && sound.isLoading());
		});
		add("VolumeGroupSetVolume", (arguments, globalScope, triggerScope) -> {
			final CSoundVolumeGroup group = object(arguments, 0);
			if (group != null) {
				final float scale = real(arguments, 1);
				this.link.send(RenderEvent.FRAME_COMMAND, () -> this.viewer.getSoundMixer().setGroupScale(group, scale));
			}
			return null;
		});
		add("VolumeGroupReset", (arguments, globalScope, triggerScope) -> {
			this.link.send(RenderEvent.FRAME_COMMAND, () -> this.viewer.getSoundMixer().reset());
			return null;
		});
		add("ClearMapMusic", (arguments, globalScope, triggerScope) -> {
			this.link.send(RenderEvent.FRAME_COMMAND, () -> this.ui.setMapMusic(null, false, 0));
			return null;
		});
		// Reforged lip sync data for models this engine does not have; stacking only thins out overlapping sounds of
		// many units at once in the original.
		for (final String facial : new String[] { "SetSoundFacialAnimationLabel", "SetSoundFacialAnimationGroupLabel",
				"SetSoundFacialAnimationSetFilepath" }) {
			add(facial, (arguments, globalScope, triggerScope) -> BooleanJassValue.of(object(arguments, 0) != null));
		}
		add("RegisterStackedSound", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			if (sound != null) {
				final boolean byPosition = bool(arguments, 1);
				final float width = real(arguments, 2), height = real(arguments, 3);
				this.voices.render(sound,
						voice -> this.viewer.getStackedSounds().register(voice, byPosition, width, height));
			}
			return null;
		});
		add("UnregisterStackedSound", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			if (sound != null) {
				final boolean byPosition = bool(arguments, 1);
				final float width = real(arguments, 2), height = real(arguments, 3);
				this.voices.render(sound,
						voice -> this.viewer.getStackedSounds().unregister(voice, byPosition, width, height));
			}
			return null;
		});
		add("SetCinematicAudio", (arguments, globalScope, triggerScope) -> {
			final boolean cinematic = bool(arguments, 0);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> this.viewer.getSoundMixer().setCinematicAudio(cinematic));
			return null;
		});
		add("KillSoundWhenDone", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			if (sound != null) {
				this.voices.apply(sound, CSound::killWhenDone);
			}
			return null;
		});
	}

	private void registerCamera() {
		final HandleJassType locationType = type("location");
		add("CameraSetSourceNoise", (arguments, globalScope, triggerScope) -> {
			final float magnitude = real(arguments, 0), velocity = real(arguments, 1);
			this.link.sendCamera(() -> camera().setSourceNoise(magnitude, velocity, false), NOISE_SECONDS);
			return null;
		});
		add("CameraSetTargetNoise", (arguments, globalScope, triggerScope) -> {
			final float magnitude = real(arguments, 0), velocity = real(arguments, 1);
			this.link.sendCamera(() -> camera().setTargetNoise(magnitude, velocity, false), NOISE_SECONDS);
			return null;
		});
		add("CameraSetSourceNoiseEx", (arguments, globalScope, triggerScope) -> {
			final float magnitude = real(arguments, 0), velocity = real(arguments, 1);
			final boolean verticalOnly = bool(arguments, 2);
			this.link.sendCamera(() -> camera().setSourceNoise(magnitude, velocity, verticalOnly), NOISE_SECONDS);
			return null;
		});
		add("CameraSetTargetNoiseEx", (arguments, globalScope, triggerScope) -> {
			final float magnitude = real(arguments, 0), velocity = real(arguments, 1);
			final boolean verticalOnly = bool(arguments, 2);
			this.link.sendCamera(() -> camera().setTargetNoise(magnitude, velocity, verticalOnly), NOISE_SECONDS);
			return null;
		});
		// The camera is the player's own (the mouse, the screen size move it), so a replay keeps what scripts read.
		add("GetCameraTargetPositionX",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(local(this.link.view().targetX())));
		add("GetCameraTargetPositionY",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(local(this.link.view().targetY())));
		add("GetCameraTargetPositionZ",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(local(this.link.view().targetZ())));
		add("GetCameraEyePositionX",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(local(this.link.view().eyeX())));
		add("GetCameraEyePositionY",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(local(this.link.view().eyeY())));
		add("GetCameraEyePositionZ",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(local(this.link.view().eyeZ())));
		add("GetCameraTargetPositionLoc", (arguments, globalScope, triggerScope) -> {
			final LocalUiLink.View view = this.link.view();
			return new HandleJassValue(locationType, new AbilityPointTarget(local(view.targetX()), local(view.targetY())));
		});
		add("GetCameraEyePositionLoc", (arguments, globalScope, triggerScope) -> {
			final LocalUiLink.View view = this.link.view();
			return new HandleJassValue(locationType, new AbilityPointTarget(local(view.eyeX()), local(view.eyeY())));
		});
		// Measured: a smoothing factor does not change a timed pan (linear with it too); it eases the movement the
		// player makes with the keys and the screen edge.
		add("CameraSetSmoothingFactor", (arguments, globalScope, triggerScope) -> {
			final float factor = real(arguments, 0);
			this.link.sendCamera(() -> camera().setSmoothingFactor(factor));
			return null;
		});
		// The picture has no depth of field pass.
		ignored("CameraSetDepthOfFieldScale", "CameraSetFocalDistance");
		// Measured: an empty name leaves a running model camera as it is.
		add("SetCinematicCamera", (arguments, globalScope, triggerScope) -> {
			final String file = string(arguments, 0);
			if ((file != null) && !file.isEmpty()) {
				// loading the model is the render side's work
				this.link.sendCamera(() -> {
					final String mdx = War3MapViewer.mdx(file);
					if (this.viewer.dataSource.has(mdx) || this.viewer.dataSource.has(War3MapViewer.mdl(mdx))) {
						final com.etheller.warsmash.viewer5.handlers.mdx.MdxModel model = this.viewer.loadModelMdx(file);
						if ((model != null) && !model.getCameras().isEmpty()) {
							camera().setCinematicCamera(model.getCameras().get(0), model);
						}
					}
				}, 60f);
			}
			return null;
		});
		// Measured: neither changes the camera's rotation or the target the camera natives report, with the unit
		// turning or moving or the rotation running its time.
		add("SetCameraRotateMode", (arguments, globalScope, triggerScope) -> {
			final float x = real(arguments, 0), y = real(arguments, 1), radians = real(arguments, 2),
					duration = real(arguments, 3);
			this.link.sendCamera(() -> camera().setRotateMode(x, y, radians, duration), duration);
			return null;
		});
		add("SetCameraOrientController", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final float xOffset = real(arguments, 1), yOffset = real(arguments, 2);
			// the unit's peer belongs to the render side: it is looked up there
			this.link.sendCamera(() -> camera().setOrientController(unit == null ? null : this.viewer.getRenderPeer(unit),
					xOffset, yOffset));
			return null;
		});
		add("BlzCameraSetupApplyForceDurationSmooth", (arguments, globalScope, triggerScope) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.camera.CustomCameraSetup setup = object(arguments, 0);
			if (setup != null) {
				final CustomCameraSetup applied = appliedSetup(setup);
				final boolean doPan = bool(arguments, 1);
				final float duration = real(arguments, 2);
				this.link.sendCamera(() -> camera().applyCameraSetupForceDuration(applied, doPan, duration), duration);
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

	/** A camera on another thread gets a copy of the script's setup, as for the camera natives of {@link CameraNatives}. */
	private CustomCameraSetup appliedSetup(final CustomCameraSetup setup) {
		if (!this.link.isQueued()) {
			return setup;
		}
		final CustomCameraSetup copy = new CustomCameraSetup(setup.getAoa(), setup.getFov(), setup.getRotation(),
				setup.getRoll(), setup.getDistance(), setup.getFarZ(), setup.getNearZ(), setup.getHeight());
		copy.setField(com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CCameraField.LOCAL_PITCH,
				setup.getLocalPitch());
		copy.setField(com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CCameraField.LOCAL_YAW,
				setup.getLocalYaw());
		copy.setField(com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CCameraField.LOCAL_ROLL,
				setup.getLocalRoll());
		copy.setDestPositionX(setup.getDestPositionX());
		copy.setDestPositionY(setup.getDestPositionY());
		copy.setLabel(setup.getLabel());
		copy.setCameraType(setup.getCameraType());
		return copy;
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
			final String sky = string(arguments, 0);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> this.viewer.setSkyModel(sky));
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
		add("EnableSelect", (arguments, globalScope, triggerScope) -> {
			final boolean enable = bool(arguments, 0), flag = bool(arguments, 1);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> {
				final MeleeUI meleeUI = meleeUI();
				if (meleeUI != null) {
					meleeUI.enableSelect(enable, flag);
				}
			});
			return null;
		});
		add("EnableDragSelect", (arguments, globalScope, triggerScope) -> {
			final boolean enable = bool(arguments, 0), flag = bool(arguments, 1);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> {
				final MeleeUI meleeUI = meleeUI();
				if (meleeUI != null) {
					meleeUI.enableDragSelect(enable, flag);
				}
			});
			return null;
		});
		add("EnablePreSelect", (arguments, globalScope, triggerScope) -> {
			final boolean enable = bool(arguments, 0), flag = bool(arguments, 1);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> {
				final MeleeUI meleeUI = meleeUI();
				if (meleeUI != null) {
					meleeUI.enablePreSelect(enable, flag);
				}
			});
			return null;
		});
		add("EnableMinimapFilterButtons", (arguments, globalScope, triggerScope) -> {
			final boolean enable = bool(arguments, 0), flag = bool(arguments, 1);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> {
				final MeleeUI meleeUI = meleeUI();
				if (meleeUI != null) {
					meleeUI.enableMinimapFilterButtons(enable, flag);
				}
			});
			return null;
		});
		add("SetCreepCampFilterState", (arguments, globalScope, triggerScope) -> {
			final boolean state = bool(arguments, 0);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> {
				final MeleeUI meleeUI = meleeUI();
				if (meleeUI != null) {
					meleeUI.setCreepCampFilterState(state);
				}
			});
			return null;
		});
		addRender("GetCreepCampFilterState", (arguments, globalScope, triggerScope) -> {
			final MeleeUI meleeUI = meleeUI();
			return BooleanJassValue.of((meleeUI != null) && meleeUI.getCreepCampFilterState());
		});
		add("SetDoodadAnimation", (arguments, globalScope, triggerScope) -> {
			final String doodad = new War3ID(integer(arguments, 3)).toString();
			final float x = real(arguments, 0), y = real(arguments, 1), radius = real(arguments, 2);
			final boolean nearestOnly = bool(arguments, 4);
			final String animation = noneIfNull(string(arguments, 5));
			this.link.send(RenderEvent.FRAME_COMMAND,
					() -> this.viewer.setDoodadAnimation(doodad, null, x, y, radius, nearestOnly, animation));
			return null;
		});
		add("SetDoodadAnimationRect", (arguments, globalScope, triggerScope) -> {
			final Rectangle area = object(arguments, 0);
			if (area != null) {
				final String doodad = new War3ID(integer(arguments, 1)).toString();
				final String animation = noneIfNull(string(arguments, 2));
				final Rectangle copy = new Rectangle(area);
				this.link.send(RenderEvent.FRAME_COMMAND,
						() -> this.viewer.setDoodadAnimation(doodad, copy, 0, 0, 0, false, animation));
			}
			return null;
		});
		add("SetReservedLocalHeroButtons", (arguments, globalScope, triggerScope) -> {
			final int reserved = integer(arguments, 0);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> {
				final MeleeUI meleeUI = meleeUI();
				if (meleeUI != null) {
					meleeUI.setReservedLocalHeroButtons(reserved);
				}
			});
			return null;
		});
		add("ForceUIKey", (arguments, globalScope, triggerScope) -> {
			final String key = string(arguments, 0);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> {
				final MeleeUI meleeUI = meleeUI();
				if (meleeUI != null) {
					meleeUI.forceUIKey(key);
				}
			});
			return null;
		});
		registerUbersplats();
		registerTerrainDeformations();
		// Nothing draws occluded units or the water ripples yet. HD water does not exist in the classic graphics.
		add("EnableOcclusion", (arguments, globalScope, triggerScope) -> {
			final boolean value = bool(arguments, 0);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> this.viewer.viewSettings.occlusion = value);
			return null;
		});
		add("EnableWorldFogBoundary", (arguments, globalScope, triggerScope) -> {
			final boolean value = bool(arguments, 0);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> {
				this.viewer.viewSettings.worldFogBoundary = value;
				if (this.viewer.terrain != null) {
					this.viewer.terrain.setWorldFogBoundary(value);
				}
			});
			return null;
		});
		add("SetWaterDeforms", (arguments, globalScope, triggerScope) -> {
			final boolean value = bool(arguments, 0);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> this.viewer.viewSettings.waterDeforms = value);
			return null;
		});
		// The old fog natives take the style, the z range and the density of SetTerrainFogEx, with no colour and one
		// more value this engine has no use for. Terrain and units are fogged by the one world fog.
		for (final String name : new String[] { "SetTerrainFog", "SetUnitFog" }) {
			add(name, (arguments, globalScope, triggerScope) -> {
				final FogSettings fog = this.viewer.worldScene.fogSettings;
				fog.setStyleByIndex(Math.round(real(arguments, 0)) + 1);
				fog.start = real(arguments, 1);
				fog.end = real(arguments, 2);
				fog.density = real(arguments, 3);
				return null;
			});
		}
		ignored("SetIntroShotText", "SetIntroShotModel");
		ignored("SetHDWaterParamsEx", "PreloadRefresh", "PreloadEndEx");
	}

	private interface UbersplatAction {
		void run(SimulationRenderComponentUbersplat splat, List<JassValue> arguments);
	}

	private void addUbersplatAction(final String name, final UbersplatAction action) {
		addRender(name, (arguments, globalScope, triggerScope) -> {
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
			state.handleId = (state.name != null) && this.viewer.hasSimulationUberSplat(state.name)
					? this.simulation.getHandleIdAllocator().createUbersplatId()
					: -1;
			if (state.handleId != -1) {
				state.imageId = this.simulation.getHandleIdAllocator().createImageId();
			}
			return new HandleJassValue(ubersplatType,
					this.link.onRender("CreateUbersplat", () -> this.viewer.createUbersplat(state)));
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

	private interface EffectChange {
		void apply(RenderSpellEffect effect);
	}

	/** Reads what the change needs from the simulation at the call; the change itself is left to the render side. */
	private interface EffectChangePreparer {
		EffectChange prepare(List<JassValue> arguments);
	}

	/**
	 * A setter changes the drawn effect and returns nothing, so it is a command for the render side in frame order,
	 * after the one that made the effect.
	 */
	private void addEffectChange(final String name, final EffectChangePreparer preparer) {
		add(name, (arguments, globalScope, triggerScope) -> {
			final ScriptEffect handle = object(arguments, 0);
			if (handle != null) {
				final EffectChange change = preparer.prepare(new ArrayList<>(arguments));
				if (change != null) {
					this.link.commands().send(() -> {
						final RenderSpellEffect effect = handle.getLiveEffect();
						if (effect != null) {
							change.apply(effect);
						}
					});
				}
			}
			return null;
		});
	}

	private void addEffectSetter(final String name, final EffectSetter setter) {
		addEffectChange(name, arguments -> effect -> setter.set(effect, arguments));
	}

	private void addEffectGetter(final String name, final java.util.function.ToDoubleFunction<RenderSpellEffect> getter) {
		addAfterCommands(name, (arguments, globalScope, triggerScope) -> {
			final ScriptEffect handle = object(arguments, 0);
			final RenderSpellEffect effect = handle == null ? null : handle.getLiveEffect();
			return RealJassValue.of(effect == null ? 0 : getter.applyAsDouble(effect));
		});
	}

	private void registerAllyColorFilter() {
		addRender("SetAllyColorFilterState", (arguments, globalScope, triggerScope) -> {
			this.viewer.getAllyColorFilter().setState(integer(arguments, 0));
			return null;
		});
		addRender("GetAllyColorFilterState", (arguments, globalScope, triggerScope) -> IntegerJassValue
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
		addEffectChange("BlzSetSpecialEffectPositionLoc", arguments -> {
			final AbilityPointTarget location = object(arguments, 1);
			if (location == null) {
				return null;
			}
			final float x = location.x;
			final float y = location.y;
			return effect -> effect.setScriptPosition(x, y, null, false);
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
		addEffectChange("BlzSetSpecialEffectColorByPlayer", arguments -> {
			final CPlayer player = object(arguments, 1);
			if (player == null) {
				return null;
			}
			final int color = player.getColor();
			return effect -> effect.getModelInstance().setTeamColor(color);
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
			final float x = real(arguments, 0), y = real(arguments, 1), duration = real(arguments, 2);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> this.ui.pingMinimap(x, y, duration));
			return null;
		});
		add("PingMinimapEx", (arguments, globalScope, triggerScope) -> {
			final float x = real(arguments, 0), y = real(arguments, 1), duration = real(arguments, 2);
			final int red = integer(arguments, 3), green = integer(arguments, 4), blue = integer(arguments, 5);
			final boolean extraEffects = bool(arguments, 6);
			this.link.send(RenderEvent.FRAME_COMMAND,
					() -> this.ui.pingMinimap(x, y, duration, red, green, blue, extraEffects));
			return null;
		});
		add("UnitAddIndicator", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				final int red = integer(arguments, 1), green = integer(arguments, 2), blue = integer(arguments, 3),
						alpha = integer(arguments, 4);
				this.link.send(RenderEvent.FRAME_COMMAND, () -> this.viewer.addIndicator(unit, red, green, blue, alpha));
			}
			return null;
		});
		add("AddIndicator", (arguments, globalScope, triggerScope) -> {
			final CWidget widget = object(arguments, 0);
			if (widget != null) {
				final int red = integer(arguments, 1), green = integer(arguments, 2), blue = integer(arguments, 3),
						alpha = integer(arguments, 4);
				this.link.send(RenderEvent.FRAME_COMMAND, () -> this.viewer.addIndicator(widget, red, green, blue, alpha));
			}
			return null;
		});
		final HandleJassType minimapiconType = type("minimapicon");
		add("CreateMinimapIcon", (arguments, globalScope, triggerScope) -> newMinimapIcon(minimapiconType,
				real(arguments, 0), real(arguments, 1), null, arguments, 2));
		add("CreateMinimapIconAtLoc", (arguments, globalScope, triggerScope) -> {
			final AbilityPointTarget location = object(arguments, 0);
			return newMinimapIcon(minimapiconType, location == null ? 0f : location.x,
					location == null ? 0f : location.y, null, arguments, 1);
		});
		add("CreateMinimapIconOnUnit", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit == null) {
				return new HandleJassValue(minimapiconType, null);
			}
			return newMinimapIcon(minimapiconType, 0f, 0f, unit, arguments, 1);
		});
		add("SetMinimapIconVisible", (arguments, globalScope, triggerScope) -> {
			final ScriptMinimapIcon icon = object(arguments, 0);
			if (icon != null) {
				icon.setVisible(bool(arguments, 1));
			}
			return null;
		});
		add("SetMinimapIconOrphanDestroy", (arguments, globalScope, triggerScope) -> {
			final ScriptMinimapIcon icon = object(arguments, 0);
			if (icon != null) {
				icon.setOrphanDestroy(bool(arguments, 1));
			}
			return null;
		});
		add("DestroyMinimapIcon", (arguments, globalScope, triggerScope) -> {
			final ScriptMinimapIcon icon = object(arguments, 0);
			if (icon != null) {
				this.link.send(RenderEvent.FRAME_COMMAND, () -> this.viewer.getScriptMinimapIcons().remove(icon));
			}
			return null;
		});
		add("SetAltMinimapIcon", (arguments, globalScope, triggerScope) -> {
			final String path = string(arguments, 0);
			this.link.send(RenderEvent.FRAME_COMMAND, () -> this.viewer.setAltMinimapIcon(path));
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

	/** The colour, path and fog mask of a minimap icon start at {@code first}; a missing fog mask shows it everywhere. */
	private HandleJassValue newMinimapIcon(final HandleJassType minimapiconType, final float x, final float y,
			final CUnit unit, final List<JassValue> arguments, final int first) {
		final Object fogState = object(arguments, first + 4);
		final int fogMask = fogState instanceof CFogState ? ((CFogState) fogState).getId()
				: fogState instanceof ConvertedConstant ? ((ConvertedConstant) fogState).getValue() : 7;
		final ScriptMinimapIcon icon = new ScriptMinimapIcon(x, y, unit, integer(arguments, first),
				integer(arguments, first + 1), integer(arguments, first + 2), string(arguments, first + 3), fogMask);
		this.link.send(RenderEvent.FRAME_COMMAND, () -> this.viewer.getScriptMinimapIcons().add(icon));
		return new HandleJassValue(minimapiconType, icon);
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
			final float red = integer(arguments, 1) / 255f, green = integer(arguments, 2) / 255f,
					blue = integer(arguments, 3) / 255f, alpha = integer(arguments, 4) / 255f;
			// the peer is the render side's: it is looked up there
			this.link.send(RenderEvent.FRAME_COMMAND, () -> {
				final RenderDestructable peer = destructable == null ? null : this.viewer.getRenderPeer(destructable);
				if ((peer != null)
						&& (peer.instance instanceof com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance)) {
					((com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance) peer.instance).setVertexColor(red,
							green, blue, alpha);
				}
			});
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
				final boolean shown = bool(arguments, 1);
				this.link.send(RenderEvent.FRAME_COMMAND, () -> {
					final RenderDestructable peer = this.viewer.getRenderPeer(destructable);
					if (peer != null) {
						peer.setHidden(!shown);
					}
				});
			}
			return null;
		});
		add("QueueDestructableAnimation", (arguments, globalScope, triggerScope) -> {
			final CDestructable destructable = object(arguments, 0);
			if (destructable != null) {
				final String animation = noneIfNull(string(arguments, 1));
				this.link.send(RenderEvent.FRAME_COMMAND, () -> {
					final RenderDestructable peer = this.viewer.getRenderPeer(destructable);
					if (peer != null) {
						peer.queueAnimation(animation);
					}
				});
			}
			return null;
		});
		add("SetDestructableAnimationSpeed", (arguments, globalScope, triggerScope) -> {
			final CDestructable destructable = object(arguments, 0);
			if (destructable != null) {
				final float speed = real(arguments, 1);
				this.link.send(RenderEvent.FRAME_COMMAND, () -> {
					final RenderDestructable peer = this.viewer.getRenderPeer(destructable);
					if (peer != null) {
						peer.setAnimationSpeed(speed);
					}
				});
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
				this.link.send(RenderEvent.FRAME_COMMAND, () -> this.ui.setLeaderboard(null));
				this.simulation.getHandleIdAllocator().release(board);
			}
			return null;
		});
		add("LeaderboardDisplay", boardSetter((board, arguments) -> {
			board.setDisplayed(bool(arguments, 1));
			final CLeaderboard shown = board.isDisplayed() ? board : null;
			this.link.send(RenderEvent.FRAME_COMMAND, () -> this.ui.setLeaderboard(shown));
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
				this.link.onRender("DestroyTimerDialog", () -> {
					dialog.setVisible(false);
					this.ui.removeTimerDialog(dialog);
					return null;
				});
				this.simulation.getHandleIdAllocator().release(dialog);
			}
			return null;
		});
		addRender("TimerDialogSetTitleColor", (arguments, globalScope, triggerScope) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog dialog = object(arguments, 0);
			if (dialog != null) {
				dialog.setTitleColor(color(arguments, 1));
			}
			return null;
		});
		addRender("TimerDialogSetTimeColor", (arguments, globalScope, triggerScope) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog dialog = object(arguments, 0);
			if (dialog != null) {
				dialog.setTimeColor(color(arguments, 1));
			}
			return null;
		});
	}
}
