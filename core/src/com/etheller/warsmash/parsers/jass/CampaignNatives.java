package com.etheller.warsmash.parsers.jass;

import java.util.HashMap;
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
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRace;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.GameCameraManager;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityDisableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCache;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CLeaderboard;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CStoredUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CSoundVolumeGroup;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitInRangeEvent;
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
		add("SaveGameCache", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(object(arguments, 0) != null));
		add("ReloadGameCachesFromDisk", (arguments, globalScope, triggerScope) -> BooleanJassValue.TRUE);
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
		add("SetTutorialCleared", (arguments, globalScope, triggerScope) -> {
			CCampaignProgress.setTutorialCleared(bool(arguments, 0));
			return null;
		});
		add("SetCampaignMenuRaceEx", (arguments, globalScope, triggerScope) -> {
			CCampaignProgress.setMenuCampaignIndex(integer(arguments, 0));
			return null;
		});
		add("SetCampaignMenuRace", (arguments, globalScope, triggerScope) -> null);
		add("ForceCampaignSelectScreen", (arguments, globalScope, triggerScope) -> {
			CCampaignProgress.forceCampaignSelectScreen();
			return null;
		});
		add("ChangeLevel", (arguments, globalScope, triggerScope) -> {
			CCampaignProgress.setPendingLevel(string(arguments, 0));
			this.ui.endGame(bool(arguments, 1));
			return null;
		});
		// No saved games yet (D-100): there is never a save to find, and a checkpoint or a save call does nothing.
		add("SaveGameExists", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		add("CopySaveGame", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		add("RemoveSaveDirectory", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		ignored("SaveGame", "LoadGame", "SaveGameCheckpoint", "DisableRestartMission", "DoNotSaveReplay");
	}

	private void registerSounds() {
		add("SetSoundVolume", soundSetter((sound, arguments) -> sound.setVolume(integer(arguments, 1))));
		add("SetSoundPitch", soundSetter((sound, arguments) -> sound.setPitch(real(arguments, 1))));
		add("SetSoundChannel", soundSetter((sound, arguments) -> sound.setChannel(integer(arguments, 1))));
		add("SetSoundPosition", soundSetter(
				(sound, arguments) -> sound.setPosition(real(arguments, 1), real(arguments, 2), real(arguments, 3))));
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
			return BooleanJassValue.of((sound != null) && sound.isPlaying());
		});
		add("SetThematicMusicVolume", (arguments, globalScope, triggerScope) -> {
			this.ui.setMusicVolume(integer(arguments, 0));
			return null;
		});
		add("GetSoundIsLoading", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		add("VolumeGroupSetVolume", (arguments, globalScope, triggerScope) -> {
			final CSoundVolumeGroup group = object(arguments, 0);
			if (group != null) {
				CSoundMixer.setGroupScale(group, real(arguments, 1));
			}
			return null;
		});
		add("VolumeGroupReset", (arguments, globalScope, triggerScope) -> {
			CSoundMixer.reset();
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
		ignored("RegisterStackedSound", "UnregisterStackedSound",
				"KillSoundWhenDone", "SetCinematicAudio");
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
		add("GetCameraTargetPositionX",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(camera().getNoisyTargetX()));
		add("GetCameraTargetPositionY",
				(arguments, globalScope, triggerScope) -> RealJassValue.of(camera().getNoisyTargetY()));
		add("GetCameraEyePositionX", (arguments, globalScope, triggerScope) -> RealJassValue.of(camera().getEyeX()));
		add("GetCameraEyePositionY", (arguments, globalScope, triggerScope) -> RealJassValue.of(camera().getEyeY()));
		add("GetCameraEyePositionZ", (arguments, globalScope, triggerScope) -> RealJassValue.of(camera().getEyeZ()));
		add("GetCameraTargetPositionLoc", (arguments, globalScope, triggerScope) -> new HandleJassValue(locationType,
				new AbilityPointTarget(camera().getNoisyTargetX(), camera().getNoisyTargetY())));
		add("GetCameraEyePositionLoc", (arguments, globalScope, triggerScope) -> new HandleJassValue(locationType,
				new AbilityPointTarget(camera().getEyeX(), camera().getEyeY())));
		// Measured: a smoothing factor does not change a timed pan (linear with it too). The original applies it to
		// the free camera, which has no such movement here.
		ignored("CameraSetSmoothingFactor", "SetCinematicCamera", "CameraSetDepthOfFieldScale",
				"CameraSetFocalDistance", "BlzCameraSetupSetCameraType");
		add("BlzCameraSetupApplyForceDurationSmooth", (arguments, globalScope, triggerScope) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.camera.CustomCameraSetup setup = object(arguments, 0);
			if (setup != null) {
				camera().applyCameraSetupForceDuration(setup, bool(arguments, 1), real(arguments, 2));
			}
			return null;
		});
	}

	private GameCameraManager camera() {
		return this.ui.getCameraManager();
	}

	private void registerWorld() {
		add("SetRandomSeed", (arguments, globalScope, triggerScope) -> {
			this.simulation.getSeededRandom().setSeed(integer(arguments, 0));
			return null;
		});
		add("SetSkyModel", (arguments, globalScope, triggerScope) -> {
			this.viewer.setSkyModel(string(arguments, 0));
			return null;
		});
		add("MathRound", (arguments, globalScope, triggerScope) -> IntegerJassValue.of(Math.round(real(arguments, 0))));
		add("SkinManagerGetLocalPath",
				(arguments, globalScope, triggerScope) -> new StringJassValue(noneIfNull(string(arguments, 0))));
		add("BlzIsSelectionEnabled", (arguments, globalScope, triggerScope) -> BooleanJassValue.TRUE);
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
		final HandleJassType ubersplatType = type("ubersplat");
		add("CreateUbersplat", (arguments, globalScope, triggerScope) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover splat = this.viewer
					.addNamedUberSplat(real(arguments, 0), real(arguments, 1), noneIfNull(string(arguments, 2)));
			return new HandleJassValue(ubersplatType, splat);
		});
		add("DestroyUbersplat", (arguments, globalScope, triggerScope) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover splat = object(arguments, 0);
			if (splat != null) {
				this.viewer.destroyUberSplat(splat);
			}
			return null;
		});
		add("ShowUbersplat", (arguments, globalScope, triggerScope) -> {
			final com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover splat = object(arguments, 0);
			if (splat != null) {
				this.viewer.showUberSplat(splat, bool(arguments, 1));
			}
			return null;
		});
		final HandleJassType terraindeformationType = type("terraindeformation");
		add("TerrainDeformCrater", (arguments, globalScope, triggerScope) -> new HandleJassValue(terraindeformationType, null));
		// Splat render flags only reorder drawing; the engine's splats always draw.
		ignored("SetUbersplatRender", "SetUbersplatRenderAlways", "ResetUbersplat", "FinishUbersplat");
		// Occlusion and the fog at the map edge have no drawing here yet, HD water and terrain fog extras do not exist
		// in the classic graphics.
		ignored("EnableOcclusion", "EnableWorldFogBoundary", "SetHDWaterParamsEx", "BlzSetTerrainFogDrawOverSky",
				"BlzSetTerrainFogMaxLinearDensity", "SetTerrainFogExV", "SetPlayerRaceSkin", "PreloadRefresh",
				"PreloadEndEx", "SyncSelections", "SetEnemyStartLocPrio", "SetEnemyStartLocPrioCount",
				"MultiboardSuppressDisplay", "SetReservedLocalHeroButtons", "PlayCinematic", "PlayModelCinematic",
				"CreateCommandButtonEffect", "CreateUpgradeCommandButtonEffect", "CreateLearnCommandButtonEffect",
				"DestroyCommandButtonEffect", "TriggerRegisterCommandEvent", "TriggerRegisterUpgradeCommandEvent",
				"RemoveWeatherEffect", "SetAllyColorFilterState",
				"BlzSetSpecialEffectScale", "BlzSetSpecialEffectZ", "BlzSetSpecialEffectColor", "SetItemColor");
	}

	private void registerMinimapAndIndicators() {
		add("PingMinimap", (arguments, globalScope, triggerScope) -> {
			this.ui.pingMinimap(real(arguments, 0), real(arguments, 1), real(arguments, 2), 255, 255, 255, false);
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
		ignored("DestroyMinimapIcon", "SetMinimapIconOrphanDestroy", "SetAltMinimapIcon", "UnitSetUsesAltIcon");
	}

	private void registerUnits() {
		final HandleJassType raceType = type("race");
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
		add("GetHeroProperName", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CAbilityHero hero = unit == null ? null : unit.getHeroData();
			return new StringJassValue(hero == null ? "" : noneIfNull(hero.getProperName()));
		});
		add("GetPlayerUnitCount", (arguments, globalScope, triggerScope) -> {
			final CPlayer player = object(arguments, 0);
			if (player == null) {
				return IntegerJassValue.ZERO;
			}
			final boolean includeIncomplete = bool(arguments, 1);
			int count = 0;
			for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
				if ((unit.getPlayerIndex() == player.getId()) && !unit.isDead()
						&& (includeIncomplete || !unit.isConstructing())) {
					count++;
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
				unit.wakeFromNaturalSleep(this.simulation);
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
				unit.resetCooldowns();
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
			return globalScope.getNativeById(globalScope.getNativeId("UnitUseItemPointLoc")).call(
					java.util.Arrays.asList(arguments.get(0), arguments.get(1),
							new HandleJassValue(type("location"), location)),
					globalScope, triggerScope);
		});
		// No chat box in a single player game on this port: nothing can type, so the event never fires.
		add("TriggerRegisterPlayerChatEvent",
				(arguments, globalScope, triggerScope) -> new HandleJassValue(eventType, null));
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
				unit.setConstructionProgress((unit.getUnitType().getBuildTime() * integer(arguments, 1)) / 100f);
			}
			return null;
		});
		// The unit field table is Reforged data the classic game has no counterpart of: nothing was applied.
		add("BlzSetUnitRealField", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		// Hero and unit overrides the classic data has no field for in the interface yet.
		ignored("UnitAddSleep",
				"BlzShowUnitTeamGlow", "BlzSetUnitArmor",
				"BlzSetUnitName", "SetPlayerHandicapDamage", "SetPlayerHandicapReviveTime");
	}

	private static CRace jassRace(final CUnitRace race) {
		switch (race) {
		case HUMAN:
			return WarsmashConstants.RACE_MANAGER.getRace(1);
		case ORC:
			return WarsmashConstants.RACE_MANAGER.getRace(2);
		case UNDEAD:
			return WarsmashConstants.RACE_MANAGER.getRace(3);
		case NIGHTELF:
			return WarsmashConstants.RACE_MANAGER.getRace(4);
		case DEMON:
			return WarsmashConstants.RACE_MANAGER.getRace(5);
		default:
			return WarsmashConstants.RACE_MANAGER.getRace(7);
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
		for (final String name : new String[] { "BlzCreateDeadDestructableWithSkinPitchRoll",
				"BlzCreateDeadDestructableWithSkinPitchRollColor", "BlzCreateDeadDestructableZWithSkinPitchRoll" }) {
			final boolean hasZ = name.contains("DestructableZ");
			add(name, (arguments, globalScope, triggerScope) -> {
				int index = 0;
				final int rawcode = integer(arguments, index++);
				final float x = real(arguments, index++);
				final float y = real(arguments, index++);
				final float z = hasZ ? real(arguments, index++) : 0;
				final float face = real(arguments, index);
				index += 3;
				final float scale = real(arguments, index++);
				final int variation = integer(arguments, index);
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
				new CLeaderboard()));
		add("DestroyLeaderboard", (arguments, globalScope, triggerScope) -> {
			final CLeaderboard board = object(arguments, 0);
			if (board != null) {
				board.setDisplayed(false);
				board.clear();
				this.playerLeaderboards.values().removeIf(board::equals);
				this.ui.setLeaderboard(null);
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
			board.addItem(noneIfNull(string(arguments, 1)), integer(arguments, 2), player == null ? -1 : player.getId());
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
		add("LeaderboardSetLabel", boardSetter((board, arguments) -> board.setLabel(string(arguments, 1))));
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
				(board, arguments) -> board.setItemLabel(integer(arguments, 1), noneIfNull(string(arguments, 2)))));
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
