package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedFormerly;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedDefault;
import com.etheller.interpreter.ast.scope.variableevent.CLimitOp;
import com.etheller.interpreter.ast.scope.variableevent.VariableEvent;
import com.etheller.warsmash.parsers.jass.JassAIEnvironment;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.units.ObjectData;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.GameDataConstants;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.TextTag;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.RemovablePathingMapInstance;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.AbstractCAbilityBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilitySellItems;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilitySellUnits;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.listeners.CUnitSpellEffectListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.handler.RandomTypeHandler;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CImage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CMultiboard;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CTrackable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackInstant;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityCollisionProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAttackProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAttackProjectileMissile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CCollisionProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CJassProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CPseudoProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CBasePlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.CPlayerAPI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfigStartLoc;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CAbilityData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CDestructableData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CPathingTextures;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CItemData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUpgradeData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CPathPoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CPathfindingProcessor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapFlag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerColor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandHost;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandLog;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderExecutor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.state.CGameState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRaceManagerEntry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRacePreference;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CUnitVisionFogModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.quest.CQuestLog;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegionManager;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.state.FalseTimeOfDay;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerFacing;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerFlyHeight;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerSleepAction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.BuildOnBuildingIntersector;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ResourceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderController;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.TextTagConfigType;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.CommandErrorListener;

public class CSimulation implements CPlayerAPI, CFogMaskSettings {
	private boolean scriptOrder;
	private boolean scriptTargetOrder;
	private final War3MapConfig mapConfig;
	private final CAbilityData abilityData;
	private final CUnitData unitData;
	private final CDestructableData destructableData;
	private final CItemData itemData;
	private final CUpgradeData upgradeData;
	private final List<CUnit> units;
	private final List<CUnit> newUnits;
	private final List<CUnit> removedUnits;
	private final List<CDestructable> destructables;
	private final List<CDestructable> removedDestructables;
	private final List<TextTag> scriptTextTags = new ArrayList<>();
	private final List<CImage> scriptImages = new ArrayList<>();
	private final List<CTrackable> trackables = new ArrayList<>();
	/** The multiboard drawn: the one shown last. */
	private CMultiboard shownMultiboard;
	private boolean multiboardsSuppressed;
	private final List<CItem> items;
	private final List<CPlayer> players;
	private final List<CPlayerUnitOrderExecutor> defaultPlayerUnitOrderExecutors;
	private final List<CEffect> projectiles;
	private final List<CEffect> newProjectiles;
	private final List<CUnitSpellEffectListener> spellEffectListeners = new ArrayList<>();
	private final HandleIdAllocator handleIdAllocator;
	private transient final SimulationRenderController simulationRenderController;
	private int gameTurnTick = 0;
	private final PathingGrid pathingGrid;
	private final CTerrainData terrainData;
	private final CWorldCollision worldCollision;
	private final CPathfindingProcessor[] pathfindingProcessors;
	private CPathfindingProcessor.SharedGraph pathfindingGraph;
	private final int mapVersion;
	/** The game the map is played in: 0 Reign of Chaos, 1 The Frozen Throne. */
	private final int mapGameVersion;
	private final GameDataConstants gameData;
	private final CGameplayConstants gameplayConstants;
	private final Random seededRandom;
	/**
	 * Measured on 1.29: attack damage dice and the first swing's wait come from a random stream of their own, which
	 * SetRandomSeed sets as well, so the script's GetRandomInt calls and critters' wandering do not change them. Null
	 * in a save from before it was kept.
	 */
	private Random combatRandom;
	private float currentGameDayTimeElapsed;
	private final Map<Integer, CUnit> handleIdToUnit = new HashMap<>();
	private final Map<Integer, CDestructable> handleIdToDestructable = new HashMap<>();
	private final Map<Integer, CItem> handleIdToItem = new HashMap<>();
	private final Map<Integer, CAbility> handleIdToAbility = new HashMap<>();
	private final LinkedList<CTimer> activeTimers = new LinkedList<>();
	private final List<CTimer> addedTimers = new ArrayList<>();
	private boolean firingTimers;
	private final List<Trigger> addedOnTickTriggers = new ArrayList<>();
	private final List<Trigger> removedOnTickTriggers = new ArrayList<>();
	private final LinkedList<Trigger> onTickTriggers = new LinkedList<>();
	private transient CommandErrorListener commandErrorListener;
	private transient CPlayerCommandLog playerCommandLog;
	private transient CPlayerCommandHost playerCommandHost;
	private final CRegionManager regionManager;
	private final List<com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitInRangeEvent> unitInRangeEvents = new ArrayList<>();
	private final Map<Long, com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CUnitVisionFogModifier> sharedVisions = new HashMap<>();
	private final List<TimeOfDayEvent> timeOfDayVariableEvents = new ArrayList<>();
	private final List<GameStateVariableEvent> gameStateVariableEvents = new ArrayList<>();
	private final Map<CUnit, CTimerFlyHeight> flyHeightChanges = new HashMap<>();
	private final Map<CUnit, CTimerFacing> facingTurns = new HashMap<>();
	private final EnumMap<JassGameEventsWar3, List<CGlobalEvent>> eventTypeToEvents = new EnumMap<>(
			JassGameEventsWar3.class);
	private boolean timeOfDaySuspended;
	private int divineInterventionGameState;
	private int disconnectedGameState;
	private float timeOfDayScale = 1.0f;
	private int allItemTypeSlots = -1;
	private int allUnitTypeSlots = -1;
	private FalseTimeOfDay falseTimeOfDay = null;
	private boolean daytime;
	private final Set<CDestructable> ownedTreeSet = new HashSet<>();
	private GlobalScope globalScope;
	private static final int BUILDING_PLACE_SEARCH_RINGS = 17;
	private Rectangle playableMapArea;
	private static final long COMBAT_SEED_SALT = 0x2545F4914F6CDD1DL;
	private static final String MISSILE_SPEED = "Missilespeed";
	private static final double SPELL_MISSILE_AT_ONCE_FLIGHT = 0.0995;
	private static final String MISSILE_HOMING = "MissileHoming";
	private boolean fogMaskEnabled = true;
	private boolean fogEnabled = true;
	private boolean cheatRevealMap = false;
	private float cheatBuildSpeed = 1.0f;
	private final List<Runnable> postUpdateCallbacks = new ArrayList<>();
	private final Set<Integer> queuedRequirementUpdates = new HashSet<>();
	private final List<Runnable> runningPostUpdateCallbacks = new ArrayList<>();
	private final CQuestLog questLog = new CQuestLog();
	private final CTerrainDeformations terrainDeformations = new CTerrainDeformations();
	private final CTerrainTypeChanges terrainTypeChanges = new CTerrainTypeChanges();
	private final EnumMap<JassGameEventsWar3, List<Trigger>> gameEventTriggers = new EnumMap<>(
			JassGameEventsWar3.class);
	private String saveBasicFilename = "";
	private RandomTypeHandler randomTypes;
	private final List<String> queuedCinematics = new ArrayList<>();
	/** The expiry time of the exact timer whose callback runs now; NaN outside of one. */
	@SavedDefault("NaN")
	private double timerFiringExactTime = Double.NaN;

	public CSimulation(final War3MapConfig config, final int mapVersion, final int mapGameVersion,
			final GameDataConstants gameData,
			final DataTable miscData,
			final ObjectData parsedUnitData, final ObjectData parsedItemData, final ObjectData parsedDestructableData,
			final ObjectData parsedAbilityData, final ObjectData parsedUpgradeData,
			final DataTable standardUpgradeEffectMeta, final SimulationRenderController simulationRenderController,
			final CPathingTextures pathingTextures, final PathingGrid pathingGrid, final CTerrainData terrainData, final Rectangle entireMapBounds,
			final Random seededRandom,
			final CommandErrorListener commandErrorListener) {
		this.mapVersion = mapVersion;
		this.mapGameVersion = mapGameVersion;
		this.gameData = gameData;
		this.gameplayConstants = new CGameplayConstants(miscData);
		this.simulationRenderController = simulationRenderController;
		this.pathingGrid = pathingGrid;
		this.abilityData = new CAbilityData(parsedAbilityData, mapGameVersion, gameData.getAbilityCompatibility());
		this.upgradeData = new CUpgradeData(this.gameplayConstants, parsedUpgradeData, standardUpgradeEffectMeta,
				mapGameVersion);
		this.itemData = new CItemData(parsedItemData, mapGameVersion);
		this.unitData = new CUnitData(this.gameplayConstants, parsedUnitData, this.abilityData, this.upgradeData,
				pathingTextures, mapGameVersion, gameData.getGameVersion());
		this.unitData.setTechEquivalents(this.itemData.getTechEquivalents());
		this.unitData.setItemData(this.itemData);
		this.unitData.getAllUnitTypesLightweight(); // Quick hack to avoid lag on first cast of Mech Critter
		this.destructableData = new CDestructableData(parsedDestructableData, pathingTextures);
		this.units = new ArrayList<>();
		this.newUnits = new ArrayList<>();
		this.removedUnits = new ArrayList<>();
		this.destructables = new ArrayList<>();
		this.removedDestructables = new ArrayList<>();
		this.items = new ArrayList<>();
		this.projectiles = new ArrayList<>();
		this.newProjectiles = new ArrayList<>();
		this.handleIdAllocator = new HandleIdAllocator();
		this.terrainData = terrainData;
		this.worldCollision = new CWorldCollision(entireMapBounds, this.gameplayConstants.getMaxCollisionRadius());
		this.regionManager = new CRegionManager(entireMapBounds, pathingGrid);
		// Created on first use; all of them share one node grid.
		this.pathfindingProcessors = new CPathfindingProcessor[this.gameData.getMaxPlayers()];
		this.seededRandom = seededRandom;
		this.combatRandom = new Random(combatSeed(seededRandom));
		this.players = new ArrayList<>();
		this.defaultPlayerUnitOrderExecutors = new ArrayList<>();
		final List<CPlayer> neutralPlayers = new ArrayList<>();
		// 1.29 gives Player(0) the id 0x100008: the game makes eight handles before the players. A player's handle
		// is made when the script first asks for it: the players the map's config asked for come first, in that
		// order, before the preplaced objects; the rest get theirs from Player() in the game.
		this.handleIdAllocator.skipHandleIds(8);
		final List<CBasePlayer> configAskedPlayers = new ArrayList<>();
		for (int i = 0; i < this.gameData.getMaxPlayers(); i++) {
			final CBasePlayer configPlayer = config.getPlayer(i);
			final War3MapConfigStartLoc startLoc = config.getStartLoc(configPlayer.getStartLocationIndex());
			CRace defaultRace = null;
			final boolean randomRace = configPlayer.isRacePrefSet(this.gameData.getRaceManager().getRandomRacePreference())
					|| (config.isMapFlagSet(CMapFlag.MAP_RANDOM_RACES) && configPlayer.isRaceSelectable());
			if (randomRace) {
				final CRaceManagerEntry raceEntry = this.gameData.getRaceManager()
						.get(seededRandom.nextInt(this.gameData.getRaceManager().getEntryCount()));
				defaultRace = this.gameData.getRaceManager().getRace(raceEntry.getRaceId());
			} else {
				for (int j = 0; j < this.gameData.getRaceManager().getEntryCount(); j++) {
					final CRaceManagerEntry entry = this.gameData.getRaceManager().get(j);
					final CRace race = this.gameData.getRaceManager().getRace(entry.getRaceId());
					final CRacePreference racePreference = this.gameData.getRaceManager()
							.getRacePreferenceById(entry.getRacePrefId());
					if (configPlayer.isRacePrefSet(racePreference)) {
						defaultRace = race;
						break;
					}
				}
			}
			if ((defaultRace == null) && (i < (this.gameData.getMaxPlayers() - 4))) {
				// Measured: the original gives a slot without a race a random one.
				final CRaceManagerEntry raceEntry = this.gameData.getRaceManager()
						.get(seededRandom.nextInt(this.gameData.getRaceManager().getEntryCount()));
				defaultRace = this.gameData.getRaceManager().getRace(raceEntry.getRaceId());
			}
			final CPlayer newPlayer = new CPlayer(defaultRace, new float[] { startLoc.getX(), startLoc.getY() },
					configPlayer, new CPlayerFogOfWar(pathingGrid));
			newPlayer.setAIDifficulty(configPlayer.getAIDifficulty());
			if (configPlayer.getHandleId() != 0) {
				configAskedPlayers.add(configPlayer);
			}
			this.players.add(newPlayer);
			this.defaultPlayerUnitOrderExecutors.add(new CPlayerUnitOrderExecutor(this, i));
			if (((newPlayer.getController() == CMapControl.NEUTRAL)
					|| (newPlayer.getController() == CMapControl.RESCUABLE))
					&& (i < (this.gameData.getMaxPlayers() - 4))) {
				neutralPlayers.add(newPlayer);
			}
		}
		configAskedPlayers.sort((a, b) -> Integer.compare(a.getHandleId(), b.getHandleId()));
		for (final CBasePlayer configPlayer : configAskedPlayers) {
			this.players.get(configPlayer.getId()).setHandleId(this.handleIdAllocator.createHandleId());
		}
		final CPlayer neutralAggressive = this.players.get(this.players.size() - 4);
		neutralAggressive.setName(miscData.getLocalizedString("WESTRING_PLAYER_NA"));
		neutralAggressive.setPlayerState(this, CPlayerState.GIVES_BOUNTY, 1);
		this.players.get(this.players.size() - 3).setName(miscData.getLocalizedString("WESTRING_PLAYER_NV"));
		this.players.get(this.players.size() - 2).setName(miscData.getLocalizedString("WESTRING_PLAYER_NE"));
		final CPlayer neutralPassive = this.players.get(this.players.size() - 1);
		neutralPassive.setName(miscData.getLocalizedString("WESTRING_PLAYER_NP"));

		for (int i = 0; i < this.gameData.getMaxPlayers(); i++) {
			final CPlayer cPlayer = this.players.get(i);
			cPlayer.setAlliance(neutralPassive, CAllianceType.PASSIVE, true);
			neutralPassive.setAlliance(cPlayer, CAllianceType.PASSIVE, true);
			// Neutral and rescuable slots are passive only with the slots the map uses and with Neutral Passive,
			// not with unused slots nor Neutral Hostile, Victim or Extra.
			if ((cPlayer != neutralPassive) && ((i >= (this.gameData.getMaxPlayers() - 4))
					|| (cPlayer.getController() == CMapControl.NONE))) {
				continue;
			}
			for (final CPlayer otherNeutral : neutralPlayers) {
				cPlayer.setAlliance(otherNeutral, CAllianceType.PASSIVE, true);
				otherNeutral.setAlliance(cPlayer, CAllianceType.PASSIVE, true);
			}
		}

		if (config.isLobbyTeams()) {
			// Team mates chosen in game setup start allied with shared vision and allied victory, as in the original.
			for (int i = 0; i < this.gameData.getMaxPlayers(); i++) {
				final CBasePlayer configPlayer = config.getPlayer(i);
				if (configPlayer.getSlotState() != CPlayerSlotState.PLAYING) {
					continue;
				}
				for (int j = 0; j < this.gameData.getMaxPlayers(); j++) {
					final CBasePlayer otherPlayer = config.getPlayer(j);
					if ((i != j) && (otherPlayer.getSlotState() == CPlayerSlotState.PLAYING)
							&& (otherPlayer.getTeam() == configPlayer.getTeam())) {
						final CPlayer player = this.players.get(i);
						final CPlayer teamMate = this.players.get(j);
						player.setAlliance(teamMate, CAllianceType.PASSIVE, true);
						player.setAlliance(teamMate, CAllianceType.HELP_REQUEST, true);
						player.setAlliance(teamMate, CAllianceType.HELP_RESPONSE, true);
						player.setAlliance(teamMate, CAllianceType.SHARED_XP, true);
						player.setAlliance(teamMate, CAllianceType.SHARED_SPELLS, true);
						player.setAlliance(teamMate, CAllianceType.SHARED_VISION, true);
						player.setPlayerState(this, CPlayerState.ALLIED_VICTORY, 1);
					}
				}
			}
		}
		this.mapConfig = config;
		if (config.isMapFlagSet(CMapFlag.MAP_SHARED_ADVANCED_CONTROL)) {
			for (int i = 0; i < this.gameData.getMaxPlayers(); i++) {
				final CBasePlayer configPlayer = config.getPlayer(i);
				if (configPlayer.getSlotState() != CPlayerSlotState.PLAYING) {
					continue;
				}
				for (int j = 0; j < this.gameData.getMaxPlayers(); j++) {
					final CBasePlayer otherPlayer = config.getPlayer(j);
					if ((i != j) && (otherPlayer.getSlotState() == CPlayerSlotState.PLAYING)
							&& (otherPlayer.getTeam() == configPlayer.getTeam())) {
						this.players.get(i).setAlliance(this.players.get(j), CAllianceType.SHARED_CONTROL, true);
						this.players.get(i).setAlliance(this.players.get(j), CAllianceType.SHARED_ADVANCED_CONTROL,
								true);
					}
				}
			}
		}
		if (config.isMapFlagSet(CMapFlag.MAP_FOG_ALWAYS_VISIBLE)) {
			this.fogEnabled = false;
			this.fogMaskEnabled = false;
		}
		else if (config.isMapFlagSet(CMapFlag.MAP_FOG_MAP_EXPLORED)) {
			this.fogMaskEnabled = false;
		}

		this.commandErrorListener = commandErrorListener;

		final CTimer fogUpdateTimer = new FogUpdateTimer();
		fogUpdateTimer.setRepeats(true);
		fogUpdateTimer.setTimeoutTime(1.0f);
		fogUpdateTimer.start(this);
	}
	
	public char getTileset() {
		return this.terrainData.getTileset();
	}

	public CTerrainData getTerrainData() {
		return this.terrainData;
	}

	public CUnitData getUnitData() {
		return this.unitData;
	}

	public CUpgradeData getUpgradeData() {
		return this.upgradeData;
	}

	public CAbilityData getAbilityData() {
		return this.abilityData;
	}

	public CDestructableData getDestructableData() {
		return this.destructableData;
	}

	public CItemData getItemData() {
		return this.itemData;
	}

	/**
	 * Also the units created during the current step, which join the unit list only at the next step.
	 */
	public List<CUnit> getUnitsIncludingNew() {
		final List<CUnit> all = new ArrayList<>(this.units);
		all.addAll(this.newUnits);
		return all;
	}

	public List<CUnit> getUnits() {
		return this.units;
	}

	public SimulationRenderController getSimulationRenderController() {
		return this.simulationRenderController;
	}

	public List<CItem> getItems() {
		return this.items;
	}

	public CDestructable getDestructable(final int handleId) {
		return this.handleIdToDestructable.get(handleId);
	}

	public List<CDestructable> getDestructables() {
		return this.destructables;
	}

	public void registerTimer(final CTimer timer) {
		this.addedTimers.remove(timer);
		if (this.firingTimers && (timer.getEngineFireTick() <= this.gameTurnTick) && timer.canFireInCurrentTick()) {
			internalRegisterTimer(timer);
			return;
		}
		this.activeTimers.remove(timer);
		this.addedTimers.add(timer);
	}

	public void unregisterTimer(final CTimer timer) {
		// Removed at once: a timer paused by another timer's callback must not fire later in the same tick (1.29).
		this.addedTimers.remove(timer);
		this.activeTimers.remove(timer);
	}

	/** Drops the wake-ups of threads that sleep in a script program no longer run. */
	public void unregisterSleepTimersOf(final GlobalScope scope) {
		final Predicate<CTimer> ofScope = timer -> (timer instanceof CTimerSleepAction)
				&& (((CTimerSleepAction) timer).getSleepingThread().globalScope == scope);
		this.addedTimers.removeIf(ofScope);
		this.activeTimers.removeIf(ofScope);
	}

	private void internalRegisterTimer(final CTimer timer) {
		this.activeTimers.remove(timer);
		final ListIterator<CTimer> listIterator = this.activeTimers.listIterator();
		while (listIterator.hasNext()) {
			final CTimer nextTimer = listIterator.next();
			if ((nextTimer.getEngineFireTick() > timer.getEngineFireTick())
					|| ((nextTimer.getEngineFireTick() == timer.getEngineFireTick())
							&& (nextTimer.getQueueTime() > timer.getQueueTime()))) {
				listIterator.previous();
				listIterator.add(timer);
				return;
			}
		}
		this.activeTimers.addLast(timer);
	}

	/**
	 * The timer part of a step: timers started since the last one join the queue, then every timer due by this step
	 * fires in order of its tick and, within a tick, of its time (start order for the same time).
	 */
	void fireDueTimers() {
		for (final CTimer timer : this.addedTimers) {
			internalRegisterTimer(timer);
		}
		this.addedTimers.clear();
		this.firingTimers = true;
		try {
			while (!this.activeTimers.isEmpty()
					&& (this.activeTimers.peek().getEngineFireTick() <= this.gameTurnTick)) {
				this.activeTimers.pop().fire(this);
			}
		}
		finally {
			this.firingTimers = false;
		}
	}

	public void internalUnregisterTimer(final CTimer timer) {
		this.activeTimers.remove(timer);
	}

	public CUnit internalCreateUnit(final War3ID typeId, final int playerIndex, final float x, final float y,
			final float facing, final ArgbImage buildingPathingPixelMap, int editorConfigHitPointPercent,
			int editorConfigManaAmount) {
		final CUnit unit = this.unitData.create(this, playerIndex, typeId, x, y, facing, buildingPathingPixelMap,
				this.handleIdAllocator, editorConfigHitPointPercent, editorConfigManaAmount);
		this.newUnits.add(unit);
		this.handleIdToUnit.put(unit.getHandleId(), unit);
		return unit;
	}

	public CDestructable internalCreateDestructable(final War3ID typeId, final float x, final float y,
			final RemovablePathingMapInstance pathingInstance, final RemovablePathingMapInstance pathingInstanceDeath) {
		final CDestructable dest = this.destructableData.create(this, typeId, x, y, this.handleIdAllocator,
				pathingInstance, pathingInstanceDeath);
		this.handleIdToDestructable.put(dest.getHandleId(), dest);
		this.worldCollision.addDestructable(dest);
		this.destructables.add(dest);
		dest.setBlighted(dest.checkIsOnBlight(this));
		return dest;
	}

	public CItem internalCreateItem(final War3ID alias, final float unitX, final float unitY) {
		final CItem item = this.itemData.create(this, alias, unitX, unitY, this.handleIdAllocator.createHandleId());
		// Measured on 1.29: an item takes one image pool number, a destructable none.
		item.setShadowPoolIds(new int[] { this.handleIdAllocator.createImageId() });
		this.handleIdToItem.put(item.getHandleId(), item);
		this.items.add(item);
		this.worldCollision.addItem(item);
		return item;
	}

	/**
	 * A living unit's shadow takes image pool numbers; measured on 1.29, a unit gives them back when it dies (a corpse
	 * has no shadow), so they take them again when they come back to life.
	 */
	public void takeShadowPoolIds(final CUnit unit) {
		final int imageCount = this.unitData.getShadowImageCount(unit.getTypeId());
		if ((imageCount == 0) || unit.hasShadowPoolIds()) {
			return;
		}
		final int[] imageIds = new int[imageCount];
		for (int i = 0; i < imageCount; i++) {
			imageIds[i] = this.handleIdAllocator.createImageId();
		}
		unit.setShadowPoolIds(imageIds);
	}

	public CItem createItem(final War3ID alias, final float unitX, final float unitY) {
		if (this.itemData.getItemType(alias) == null) {
			return null;
		}
		float x = unitX;
		float y = unitY;
		if (this.pathingGrid.contains(x, y) && !this.pathingGrid.isPathable(x, y, PathingGrid.PathingType.WALKABLE)) {
			// Measured on 1.29: an item made on unwalkable ground goes to the centre of a walkable cell on the nearest
			// square ring of cells round the point; within a ring the first walkable cell wins, walking the ring from its
			// second cell on the low-y side towards +x, then up the +x side, back along the high-y side and down the -x side.
			search: for (int ring = 1; ring <= ITEM_SPOT_RINGS; ring++) {
				final int steps = ring * 8;
				for (int n = 0; n < steps; n++) {
					final int k = (n + 1) % steps;
					final int side = k / (ring * 2);
					final int along = k % (ring * 2);
					final int i;
					final int j;
					switch (side) {
					case 0:
						i = -ring + along;
						j = -ring;
						break;
					case 1:
						i = ring;
						j = -ring + along;
						break;
					case 2:
						i = ring - along;
						j = ring;
						break;
					default:
						i = -ring;
						j = ring - along;
						break;
					}
					final float spotX = unitX + (i * 32);
					final float spotY = unitY + (j * 32);
					if (!this.pathingGrid.contains(spotX, spotY)
							|| !this.pathingGrid.isPathable(spotX, spotY, PathingGrid.PathingType.WALKABLE)) {
						continue;
					}
					x = this.pathingGrid.getWorldX(this.pathingGrid.getCellX(spotX));
					y = this.pathingGrid.getWorldY(this.pathingGrid.getCellY(spotY));
					break search;
				}
			}
		}
		return this.simulationRenderController.createItem(this, alias, x, y);
	}

	private static final int ITEM_SPOT_RINGS = 32;

	public void updateItemModel(CItem item) {
		this.simulationRenderController.updateItemModel(item);
	}

	public CUnit createUnit(final War3ID typeId, final int playerIndex, final float x, final float y,
			final float facing) {
		return this.createUnit(typeId, playerIndex, x, y, facing, false);
	}

	public CUnit createUnit(final War3ID typeId, final int playerIndex, final float x, final float y,
			final float facing, final boolean constructing) {
		if (this.unitData.getUnitType(typeId) == null) {
			return null;
		}
		final CUnit createdUnit = this.simulationRenderController.createUnit(this, typeId, playerIndex, x, y, facing);
		if (createdUnit != null) {
			takeShadowPoolIds(createdUnit);
			if (this.unitData.hasUberSplat(typeId)) {
				createdUnit.setUbersplatPoolIds(this.handleIdAllocator.createImageId(),
						this.handleIdAllocator.createUbersplatId());
			}
			createdUnit.setConstructing(constructing);
			setupCreatedUnit(createdUnit);
			final float handicap = getPlayer(playerIndex).getHandicap();
			if (handicap != 1f) {
				createdUnit.setHandicap(handicap);
			}
			if (isNeutralAggressive(playerIndex) && !createdUnit.isBuilding()) {
				createdUnit.setGuardPosition(x, y);
			}
			if (createdUnit.getCollisionRectangle() == null) {
				this.worldCollision.addUnit(createdUnit);
			} // else the unit was probably injected into collision before default behaviors
			if (createdUnit.isGuarding()) {
				createdUnit.joinCreepCamp(this);
			}
			createdUnit.performDefaultBehavior(this);
			// Fog is recomputed once a second; without this a script could not order a unit it just made against
			// anything, because the new unit would not yet see its target.
			updateFogOfWarOfNewUnit(createdUnit);
			createdUnit.onCreatedNightSleep(this);
			if (createdUnit.isHero()) {
				heroCreateEvent(createdUnit);
			}
		}
		return createdUnit;
	}

	private void updateFogOfWarOfNewUnit(final CUnit unit) {
		unit.updateFogOfWar(this);
		// Players the owner shares its vision with see the new unit at once too (measured on 1.29).
		final CPlayer owner = getPlayer(unit.getPlayerIndex());
		for (final CPlayer receiver : this.players) {
			if ((receiver != owner) && sharesVision(owner, receiver)) {
				unit.updateFogOfWar(this, receiver.getFogOfWar());
			}
		}
	}

	/**
	 * As in the original game, a building a script creates is moved off cliffs, slopes and other buildings instead of
	 * overlapping them. Measured: the original takes the first free grid point along a square spiral that starts
	 * downwards and turns counter-clockwise (down 1, right 1, up 2, left 2, down 3, ...), not the nearest one. It
	 * gives up after 1225 points (a spot found at step 1168 was taken, one at 1243 was not) and then keeps the point.
	 */
	private Vector2 findBuildingPlace(final CUnitType unitType, final float x, final float y) {
		final ArgbImage pathingMap = unitType.getBuildingPathingPixelMap();
		final float offsetX = ((pathingMap.getWidth() / 2) % 2) == 1 ? 32f : 0f;
		final float offsetY = ((pathingMap.getHeight() / 2) % 2) == 1 ? 32f : 0f;
		final Vector2 clamped = placeOutsidePlayableArea(unitType, gridPoint(x, offsetX), gridPoint(y, offsetY), true,
				new boolean[1]);
		final float baseX = clamped.x;
		final float baseY = clamped.y;
		final int[] stepX = { 0, 1, 0, -1 };
		final int[] stepY = { -1, 0, 1, 0 };
		int cellX = 0;
		int cellY = 0;
		int direction = 0;
		final int maxSteps = ((2 * BUILDING_PLACE_SEARCH_RINGS) + 1) * ((2 * BUILDING_PLACE_SEARCH_RINGS) + 1);
		int visited = 0;
		for (int legLength = 1; visited < maxSteps; legLength++) {
			for (int leg = 0; leg < 2; leg++) {
				for (int step = 0; (step < legLength) && (visited < maxSteps); step++) {
					final float candidateX = baseX + (cellX * 64f);
					final float candidateY = baseY + (cellY * 64f);
					// Measured: a script-created Undead building is not moved to blight.
					if (this.pathingGrid.contains(candidateX, candidateY)
							&& !AbstractCAbilityBuild.isBuildLocationObstructed(this, unitType, pathingMap, false,
							candidateX, candidateY, null, BuildOnBuildingIntersector.INSTANCE, true)) {
						return new Vector2(candidateX, candidateY);
					}
					visited++;
					cellX += stepX[direction];
					cellY += stepY[direction];
				}
				direction = (direction + 1) % 4;
			}
		}
		return new Vector2(baseX, baseY);
	}

	/**
	 * Measured: the point is cut toward zero onto the 64 grid (-34.5 to -34, -63.25 to -63, 15.75 to 15, 51.56 to 51
	 * cells), and a building whose centre sits between grid lines is then put half a cell away from zero (a Circle of
	 * Power asked at -1024, 1616 stands at -1056, 1632; one asked at -976, 1600 at -992, 1632).
	 */
	private static float gridPoint(final float value, final float offset) {
		final float point = ((int) (value / 64f)) * 64f;
		return value < 0 ? point - offset : point + offset;
	}

	public void setPlayableMapArea(final Rectangle playableMapArea) {
		this.playableMapArea = playableMapArea;
	}

	/**
	 * Measured: a point outside the playable area is first cut back into the map's 32 cell grid. A building is then
	 * only clamped to the map (on its 64 grid, its far edge one cell in). A flyer goes to the nearest playable cell and,
	 * when it was out on one side only, slides along that edge by the number of cells it was out (33 past the right
	 * edge ends one cell up, 64 past two); out on two sides it is just clamped, and past the top of the map it is only
	 * clamped to the map. A land unit is only clamped to the map and then pushed off unwalkable ground as usual.
	 */
	private Vector2 placeOutsidePlayableArea(final CUnitType unitType, final float x, final float y,
			final boolean building, final boolean[] exact) {
		final PathingGrid grid = this.pathingGrid;
		final int width = grid.getWidth();
		final int height = grid.getHeight();
		final float worldX = grid.getWorldXFromCorner(0);
		final float worldY = grid.getWorldYFromCorner(0);
		if (building) {
			return new Vector2(Math.max(worldX, Math.min(worldX + (width * 32f) - 64f, x)),
					Math.max(worldY, Math.min(worldY + (height * 32f) - 64f, y)));
		}
		final int cellX = Math.max(0, Math.min(width - 1, (int) Math.floor((x - worldX) / 32f)));
		final int cellY = Math.max(0, Math.min(height - 1, (int) Math.floor((y - worldY) / 32f)));
		final int minX = Math.round((this.playableMapArea.x - worldX) / 32f);
		final int minY = Math.round((this.playableMapArea.y - worldY) / 32f);
		final int maxX = Math.round((this.playableMapArea.x + this.playableMapArea.width - worldX) / 32f) - 1;
		final int maxY = Math.round((this.playableMapArea.y + this.playableMapArea.height - worldY) / 32f) - 1;
		final float originX = worldX + (cellX * 32f);
		final float originY = worldY + (cellY * 32f);
		if (unitType.getMovementType() != PathingGrid.MovementType.FLY) {
			// A land unit only loses what lies beyond the map; the unit nudge then looks for a free cell within its
			// own search range and leaves the unit where it is when there is none.
			return new Vector2(Math.max(worldX, Math.min(worldX + (width * 32f) - 32f, x)),
					Math.max(worldY, Math.min(worldY + (height * 32f) - 32f, y)));
		}
		if (y >= (worldY + (height * 32f))) {
			exact[0] = true;
			return new Vector2(originX, originY);
		}
		exact[0] = true;
		final int clampX = Math.max(minX, Math.min(maxX, cellX));
		final int clampY = Math.max(minY, Math.min(maxY, cellY));
		float placeX = worldX + (clampX * 32f);
		float placeY = worldY + (clampY * 32f);
		final boolean outX = cellX != clampX;
		final boolean outY = cellY != clampY;
		if (outX && !outY) {
			final int slide = cellX < minX ? minX - cellX : cellX - maxX;
			placeY += (cellX < minX ? -slide : slide) * 32f;
		}
		else if (outY && !outX) {
			final int slide = cellY < minY ? minY - cellY : cellY - (maxY + 1);
			placeX += (cellY < minY ? slide : -slide) * 32f;
		}
		return new Vector2(placeX, placeY);
	}

	public CUnit createUnitSimple(final War3ID typeId, final int playerIndex, float x, float y,
			final float facing) {
		final CUnitType unitType = this.unitData.getUnitType(typeId);
		boolean keepPlace = false;
		if ((unitType != null) && (unitType.getBuildingPathingPixelMap() != null)
				&& !unitType.isCanBeBuiltOnThem()) {
			final Vector2 place = findBuildingPlace(unitType, x, y);
			x = place.x;
			y = place.y;
			// Measured: when the search finds nothing the building stays on the rounded point; it is not pushed
			// out again by the unit nudge below.
			keepPlace = true;
		}
		else if ((unitType != null) && (this.playableMapArea != null) && !this.playableMapArea.contains(x, y)) {
			final boolean[] exact = new boolean[1];
			final Vector2 place = placeOutsidePlayableArea(unitType, x, y, false, exact);
			x = place.x;
			y = place.y;
			keepPlace = exact[0];
		}
		final CUnit newUnit = createUnit(typeId, playerIndex, x, y, facing);
		if (newUnit != null) {
			final CPlayer player = getPlayer(playerIndex);
			final CUnitType newUnitType = newUnit.getUnitType();
			player.setUnitFoodUsed(newUnit, newUnitType.getFoodUsed());
			player.setUnitFoodMade(newUnit, newUnitType.getFoodMade());
			player.addTechtreeUnlocked(this, typeId);
			newUnit.unlockUpgradedFromTypes(this, player);
			player.scoreUnitGained(newUnitType);
			// nudge unit
			final boolean silentEnter = this.regionManager.isRunningEnterTriggers();
			if (silentEnter) {
				this.regionManager.setSilentEnterUnit(newUnit);
			}
			if (keepPlace) {
				newUnit.setPoint(x, y, this.worldCollision, this.regionManager);
				unitRepositioned(newUnit);
			}
			else {
				newUnit.setPointAndCheckUnstuck(x, y, this);
				// The unit sees from the spot it was nudged to, so a script can order it against what stands there.
				if ((newUnit.getX() != x) || (newUnit.getY() != y)) {
					updateFogOfWarOfNewUnit(newUnit);
				}
			}
			if (silentEnter) {
				this.regionManager.setSilentEnterUnit(null);
			}
			if (newUnit.isGuarding()) {
				newUnit.setGuardPosition(newUnit.getX(), newUnit.getY());
			}
			if (!newUnit.isBuilding()) {
				newUnit.getUnitAnimationListener().playAnimation(false, PrimaryTag.BIRTH, SequenceUtils.EMPTY, 1.0f,
						true);
				newUnit.getUnitAnimationListener().queueAnimation(PrimaryTag.STAND, SequenceUtils.EMPTY, true);
			}
		}
		return newUnit;
	}

	public CDestructable createDestructable(final War3ID typeId, final float x, final float y, final float facing,
			final float scale, final int variation) {
		return this.simulationRenderController.createDestructable(typeId, x, y, facing, scale, variation);
	}

	public CDestructable createDestructableZ(final War3ID typeId, final float x, final float y, final float z,
			final float facing, final float scale, final int variation) {
		return this.simulationRenderController.createDestructableZ(typeId, x, y, z, facing, scale, variation);
	}

	public CUnit getUnit(final int handleId) {
		return this.handleIdToUnit.get(handleId);
	}

	public CAbility getAbility(final int handleId) {
		return this.handleIdToAbility.get(handleId);
	}

	protected void onAbilityAddedToUnit(final CUnit unit, final CAbility ability) {
		this.handleIdToAbility.put(ability.getHandleId(), ability);
	}

	protected void onAbilityRemovedFromUnit(final CUnit unit, final CAbility ability) {
		this.handleIdToAbility.remove(ability.getHandleId());
	}

	public CAttackProjectile createProjectile(final CUnit source, final float launchX, final float launchY,
			final float launchFacing, final CUnitAttackMissile attack, final AbilityTarget target, final float damage,
			final int bounceIndex, final CUnitAttackListener attackListener, final CUnitAttackSettings settings) {
		final CAttackProjectile projectile = new CAttackProjectileMissile(
				projectileLaunchX(source, launchX, launchFacing), projectileLaunchY(source, launchY, launchFacing),
				settings.getProjectileSpeed(), target, source, damage, attack, bounceIndex, attackListener, settings);
		this.simulationRenderController.spawnProjectileArt(projectile, source, settings, bounceIndex);
		leadMidStepLaunch(projectile);
		this.newProjectiles.add(projectile);
		return projectile;
	}

	/**
	 * Where a unit's missiles start: its launch offsets, which the unit data gives forward and sideways, turned to
	 * the launch facing (radians).
	 */
	private float projectileLaunchX(final CUnit source, final float launchX, final float launchFacing) {
		final War3ID typeId = source.getTypeId();
		final float offsetX = this.unitData.getProjectileLaunchX(typeId);
		final float offsetY = this.unitData.getProjectileLaunchY(typeId);
		return launchX + (offsetY * (float) StrictMath.cos(launchFacing)) + (offsetX * (float) StrictMath.sin(launchFacing));
	}

	private float projectileLaunchY(final CUnit source, final float launchY, final float launchFacing) {
		final War3ID typeId = source.getTypeId();
		final float offsetX = this.unitData.getProjectileLaunchX(typeId);
		final float offsetY = this.unitData.getProjectileLaunchY(typeId);
		return (launchY + (offsetY * (float) StrictMath.sin(launchFacing))) - (offsetX * (float) StrictMath.cos(launchFacing));
	}

	/** Missile speed of the ability's data (Missilespeed). */
	private float abilityMissileSpeed(final War3ID spellAlias) {
		final GameObject abilityEditorData = this.abilityData.getEditorData(spellAlias.asStringValue());
		return abilityEditorData == null ? 0 : abilityEditorData.getFieldAsFloat(MISSILE_SPEED, 0);
	}

	/** Whether the ability's missiles follow their target (MissileHoming). */
	private boolean abilityMissileHoming(final War3ID spellAlias) {
		final GameObject abilityEditorData = this.abilityData.getEditorData(spellAlias.asStringValue());
		return (abilityEditorData != null) && abilityEditorData.getFieldAsBoolean(MISSILE_HOMING, 0);
	}

	public CAbilityProjectile createProjectile(final CUnit source, final War3ID spellAlias, final float launchX,
			final float launchY, final float launchFacing, final Float speed, final Boolean homing,
			final AbilityTarget target, final CAbilityProjectileListener projectileListener) {
		final CAbilityProjectile projectile = new CAbilityProjectile(projectileLaunchX(source, launchX, launchFacing),
				projectileLaunchY(source, launchY, launchFacing),
				speed == null ? abilityMissileSpeed(spellAlias) : speed, target,
				homing == null ? abilityMissileHoming(spellAlias) : homing, source, projectileListener);
		this.simulationRenderController.spawnProjectileArt(projectile, source, spellAlias);
		leadMidStepLaunch(projectile);
		this.newProjectiles.add(projectile);
		projectileListener.onLaunch(this, projectile, target);
		projectile.startMissileClock(CTimer.currentTime(this));
		hitAtLaunchIfNear(projectile);
		return projectile;
	}

	public CAbilityProjectile createProjectile(final CUnit source, final CUnitAttackSettings settings,
			final float launchX, final float launchY, final float launchFacing, final AbilityTarget target,
			final CAbilityProjectileListener projectileListener) {
		final CAbilityProjectile projectile = new CAbilityProjectile(projectileLaunchX(source, launchX, launchFacing),
				projectileLaunchY(source, launchY, launchFacing), settings.getProjectileSpeed(), target,
				settings.isProjectileHomingEnabled(), source, projectileListener);
		this.simulationRenderController.spawnProjectileArt(projectile, source, settings, 0);
		leadMidStepLaunch(projectile);
		this.newProjectiles.add(projectile);
		projectileListener.onLaunch(this, projectile, target);
		return projectile;
	}

	public CJassProjectile createProjectile(final CUnit source, final War3ID spellAlias, final float launchX,
			final float launchY, final float launchFacing, final Float speed, final Boolean homing,
			final AbilityTarget target) {
		final CJassProjectile projectile = new CJassProjectile(projectileLaunchX(source, launchX, launchFacing),
				projectileLaunchY(source, launchY, launchFacing), speed, target, homing, source);
		this.simulationRenderController.spawnProjectileArt(projectile, source, spellAlias);
		leadMidStepLaunch(projectile);
		this.newProjectiles.add(projectile);
		return projectile;
	}

	public CCollisionProjectile createCollisionProjectile(final CUnit source, final War3ID spellAlias,
			final float launchX, final float launchY, final float launchFacing, final Float speed, final Boolean homing,
			final AbilityTarget target, final int maxHits, final int hitsPerTarget, final float startingRadius,
			final float finalRadius, final float collisionInterval,
			final CAbilityCollisionProjectileListener projectileListener, final boolean provideCounts) {
		final CCollisionProjectile projectile = new CCollisionProjectile(
				projectileLaunchX(source, launchX, launchFacing), projectileLaunchY(source, launchY, launchFacing),
				speed == null ? abilityMissileSpeed(spellAlias) : speed, target,
				homing == null ? abilityMissileHoming(spellAlias) : homing, source, maxHits, hitsPerTarget,
				startingRadius, finalRadius, collisionInterval, projectileListener, provideCounts);
		this.simulationRenderController.spawnProjectileArt(projectile, source, spellAlias);
		this.newProjectiles.add(projectile);
		projectileListener.onLaunch(this, projectile, target);
		return projectile;
	}

	public CPseudoProjectile createPseudoProjectile(final CUnit source, final War3ID spellAlias,
			final CEffectType effectType, final int effectArtIndex, final float launchX, final float launchY,
			final float launchFacing, final float speed, final float projectileStepInterval,
			final int projectileArtSkip, final boolean homing, final AbilityTarget target, final int maxHits,
			final int hitsPerTarget, final float startingRadius, final float finalRadius,
			final CAbilityCollisionProjectileListener projectileListener, final boolean provideCounts) {
		final CPseudoProjectile projectile = new CPseudoProjectile(projectileLaunchX(source, launchX, launchFacing),
				projectileLaunchY(source, launchY, launchFacing), speed, projectileStepInterval, projectileArtSkip,
				target, homing, source, spellAlias, effectType, effectArtIndex, maxHits, hitsPerTarget, startingRadius,
				finalRadius, projectileListener, provideCounts);
		this.newProjectiles.add(projectile);
		projectileListener.onLaunch(this, projectile, target);
		return projectile;
	}

	/**
	 * Inside an exact timer's callback the step has already moved on to the timer's step end, so a missile launched
	 * there is given the flight between its exact launch time and that step end.
	 */
	private void leadMidStepLaunch(final CProjectile projectile) {
		if (!Double.isNaN(this.timerFiringExactTime)) {
			final double lead = (this.gameTurnTick * (double) WarsmashConstants.SIMULATION_STEP_TIME)
					- this.timerFiringExactTime;
			if (lead > 0) {
				projectile.setLaunchLead((float) lead);
			}
		}
	}

	/**
	 * Measured on 1.29: a spell missile whose flight would take under 0.1 s hits as it is launched (Storm Bolt 80 and
	 * 99 away and Death Coil 160 away hit at once, Storm Bolt 100 away and Death Coil 170 away fly).
	 */
	private void hitAtLaunchIfNear(final CAbilityProjectile projectile) {
		final double distance = StrictMath.hypot(projectile.getTargetX() - projectile.getX(),
				projectile.getTargetY() - projectile.getY());
		if (distance < (projectile.getSpeed() * SPELL_MISSILE_AT_ONCE_FLIGHT)) {
			projectile.hitAtLaunch(this);
		}
	}

	public void registerEffect(final CEffect effect) {
		this.newProjectiles.add(effect);
	}

	public SimulationRenderComponentLightning createLightning(final CUnit source, final War3ID lightningId,
			final CUnit target) {
		return this.simulationRenderController.createLightning(this, lightningId, source, target);
	}

	public SimulationRenderComponentLightning createLightning(final CUnit source, final War3ID lightningId,
			final CUnit target, final Float duration) {
		return this.simulationRenderController.createLightning(this, lightningId, source, target, duration);
	}

	public SimulationRenderComponentLightning createAbilityLightning(final CUnit source, final War3ID lightningId,
			final int lightningIndex, final CUnit target) {
		return this.simulationRenderController.createAbilityLightning(this, lightningId, source, target,
				lightningIndex);
	}

	public SimulationRenderComponentLightning createAbilityLightning(final CUnit source, final War3ID lightningId,
			final int lightningIndex, final CUnit target, final Float duration) {
		return this.simulationRenderController.createAbilityLightning(this, lightningId, source, target, lightningIndex,
				duration);
	}

	public SimulationRenderComponent createStaticUberSplat(float x, float y, War3ID id) {
		return this.simulationRenderController.createStaticUberSplat(x, y, id);
	}

	public void createInstantAttackEffect(final CUnit source, final CUnitAttackInstant attack, final CWidget target) {
		this.simulationRenderController.createInstantAttackEffect(this, source, attack, target);
	}

	public PathingGrid getPathingGrid() {
		return this.pathingGrid;
	}

	public void findNaiveSlowPath(final CUnit ignoreIntersectionsWithThisUnit,
			final CUnit ignoreIntersectionsWithThisSecondUnit, final float startX, final float startY,
			final CPathPoint goal, final PathingGrid.MovementType movementType, final float collisionSize,
			final boolean allowSmoothing, final CBehaviorMove queueItem) {
		final int playerIndex = queueItem.getUnit().getPlayerIndex();
		CPathfindingProcessor pathfindingProcessor = this.pathfindingProcessors[playerIndex];
		if (pathfindingProcessor == null) {
			if (this.pathfindingGraph == null) {
				this.pathfindingGraph = new CPathfindingProcessor.SharedGraph(this.pathingGrid);
			}
			pathfindingProcessor = new CPathfindingProcessor(this.pathingGrid, this.worldCollision,
					this.pathfindingGraph);
			this.pathfindingProcessors[playerIndex] = pathfindingProcessor;
		}
		pathfindingProcessor.findNaiveSlowPath(ignoreIntersectionsWithThisUnit, ignoreIntersectionsWithThisSecondUnit,
				startX, startY, goal, movementType, collisionSize, allowSmoothing, queueItem);
	}

	/** Whether a unit at a point can walk to within a reach of another point, ignoring other units. */
	public boolean canWalkNear(final CUnit unit, final float toX, final float toY, final float reach) {
		if (this.pathfindingGraph == null) {
			this.pathfindingGraph = new CPathfindingProcessor.SharedGraph(this.pathingGrid);
		}
		return this.pathfindingGraph.canReach(this.pathingGrid, unit.getMovementType(),
				unit.getUnitType().getCollisionSize(), unit.getX(), unit.getY(), toX, toY, reach);
	}

	/** Path search nodes that only earlier searches used are made again when needed; a save leaves them out. */
	public void dropStalePathfindingNodes() {
		if (this.pathfindingGraph != null) {
			this.pathfindingGraph.dropStaleNodes();
		}
	}

	public void removeFromPathfindingQueue(final CBehaviorMove behaviorMove) {
		final int playerIndex = behaviorMove.getUnit().getPlayerIndex();
		final CPathfindingProcessor pathfindingProcessor = this.pathfindingProcessors[playerIndex];
		if (pathfindingProcessor != null) {
			pathfindingProcessor.removeFromPathfindingQueue(behaviorMove);
		}
	}

	protected void updateFogOfWar() {
		final long fogStart = System.nanoTime();
		for (final CPlayer player : this.players) {
			player.getFogOfWar().convertVisibleToFogged();
			player.updateFogModifiers(this);
		}
		for (final CUnit unit : this.units) {
			unit.updateFogOfWar(this);
		}
		// Units a timer created earlier in this step join the unit list only at the next step; leaving them out
		// would fog what they see for a whole second.
		for (final CUnit unit : this.newUnits) {
			unit.updateFogOfWar(this);
		}
		shareVision();
		for (final CPlayer player : this.players) {
			player.updateFogModifiersAfterUnits(this);
		}
		for (final CUnit unit : this.units) {
			unit.updateSeenBy(this, this.players.size());
		}
		PerfStats.addStep(PerfStats.Step.FOG, System.nanoTime() - fogStart);
	}

	/**
	 * A player sharing vision (ALLIANCE_SHARED_VISION from giver to receiver) adds what it sees to the receiver's fog
	 * at the fog update, not at once (measured on 1.29), and only one way.
	 */
	private void shareVision() {
		boolean anyShared = false;
		for (final CPlayer giver : this.players) {
			for (final CPlayer receiver : this.players) {
				if ((giver != receiver) && sharesVision(giver, receiver)) {
					if (!anyShared) {
						anyShared = true;
						for (final CPlayer player : this.players) {
							player.getFogOfWar().keepOwnVision();
						}
					}
					receiver.getFogOfWar().addOwnVisionOf(giver.getFogOfWar());
				}
			}
		}
	}

	private static boolean sharesVision(final CPlayer giver, final CPlayer receiver) {
		return giver.hasAlliance(receiver.getId(), CAllianceType.SHARED_VISION)
				|| giver.hasAlliance(receiver.getId(), CAllianceType.SHARED_VISION_FORCED);
	}

	public void addUnitInRangeEvent(final com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitInRangeEvent event) {
		this.unitInRangeEvents.add(event);
	}

	public void removeUnitInRangeEvent(final com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitInRangeEvent event) {
		this.unitInRangeEvents.remove(event);
	}

	public void unitSummoned(final CUnit summoner, final CUnit summoned) {
		summoner.fireSummonEvents(this);
		getPlayer(summoner.getPlayerIndex()).fireUnitSummonEvents(summoner, summoned);
	}

	/**
	 * UnitShareVision: while shared the viewer sees what the unit sees, like an ally's unit; measured on 1.29.
	 */
	public void setUnitSharedVision(final CUnit unit, final CPlayer viewer, final boolean share) {
		final long key = (((long) unit.getHandleId()) << 8) | viewer.getId();
		final com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CUnitVisionFogModifier existing = this.sharedVisions.remove(key);
		if (existing != null) {
			viewer.removeFogModifer(this, existing);
		}
		if (share) {
			final com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CUnitVisionFogModifier modifier = new com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CUnitVisionFogModifier(
					unit, false);
			viewer.addFogModifer(this, modifier, false);
			this.sharedVisions.put(key, modifier);
		}
	}

	/** Neutral Aggressive, Victim, Extra and Passive: the last four player slots. */
	public boolean isNeutralPlayer(final int playerIndex) {
		return playerIndex >= (this.players.size() - 4);
	}

	public boolean isUnitVisionShared(final CUnit unit, final CPlayer viewer) {
		return !this.sharedVisions.isEmpty()
				&& this.sharedVisions.containsKey((((long) unit.getHandleId()) << 8) | viewer.getId());
	}

	public void update() {
		PerfStats.stepMark();
		updateSelections();
		for (int i = this.unitInRangeEvents.size() - 1; i >= 0; i--) {
			this.unitInRangeEvents.get(i).update(this);
		}
		final Iterator<CUnit> unitIterator = this.units.iterator();
		while (unitIterator.hasNext()) {
			final CUnit unit = unitIterator.next();
			if (unit.update(this)) {
				unitIterator.remove();
				for (final CAbility ability : unit.getAbilities()) {
					this.handleIdToAbility.remove(ability.getHandleId());
				}
				this.handleIdToUnit.remove(unit.getHandleId());
				this.simulationRenderController.removeUnit(unit);
				getPlayerHeroes(unit.getPlayerIndex()).remove(unit);
				unit.onRemove(this);
				freeUnitHandleId(unit);
			}
		}
		finishAddingNewUnits();
		for (int i = 0; i < this.units.size(); i++) {
			this.units.get(i).checkRescue(this);
		}
		PerfStats.stepEnd(PerfStats.Step.UNITS);
		for (final CDestructable destructable : this.removedDestructables) {
			this.worldCollision.removeDestructable(destructable);
			this.simulationRenderController.removeDestructable(destructable);
			destructable.onRemove(this);
			// Measured on 1.29: the id of a removed destructable is given again, like a removed unit's.
			this.handleIdToDestructable.remove(destructable.getHandleId(), destructable);
			this.handleIdAllocator.release(destructable);
		}
		this.removedDestructables.clear();
		// Missiles launched since the last move (a cast point inside the last step, a script) fly from this step on.
		this.projectiles.addAll(this.newProjectiles);
		this.newProjectiles.clear();
		final Iterator<CEffect> projectileIterator = this.projectiles.iterator();
		while (projectileIterator.hasNext()) {
			final CEffect projectile = projectileIterator.next();
			if (projectile.update(this)) {
				projectileIterator.remove();
			}
		}
		this.projectiles.addAll(this.newProjectiles);
		this.newProjectiles.clear();
		PerfStats.stepEnd(PerfStats.Step.PROJECTILES);
		for (final CPathfindingProcessor pathfindingProcessor : this.pathfindingProcessors) {
			if (pathfindingProcessor != null) {
				pathfindingProcessor.update(this);
			}
		}
		PerfStats.stepEnd(PerfStats.Step.PATHING);
		this.gameTurnTick++;
		final float timeOfDayBefore = getGameTimeOfDay();
		if (this.falseTimeOfDay != null) {
			if (!this.falseTimeOfDay.tick()) {
				this.falseTimeOfDay = null;
			}
		} else if (!this.timeOfDaySuspended) {
			this.currentGameDayTimeElapsed = (this.currentGameDayTimeElapsed
					+ (WarsmashConstants.SIMULATION_STEP_TIME * this.timeOfDayScale))
					% this.gameplayConstants.getGameDayLength();
		}
		final float timeOfDayAfter = getGameTimeOfDay();
		this.daytime = (timeOfDayAfter >= this.gameplayConstants.getDawnTimeGameHours())
				&& (timeOfDayAfter < this.gameplayConstants.getDuskTimeGameHours());
		fireDueTimers();
		checkTimeOfDayEvents(timeOfDayBefore, timeOfDayAfter);
		PerfStats.stepEnd(PerfStats.Step.TIMERS);
		this.onTickTriggers.addAll(this.addedOnTickTriggers);
		this.addedOnTickTriggers.clear();
		for (final Trigger trigger : this.onTickTriggers) {
			final TriggerExecutionScope triggerScope = trigger.getTriggerExecutionScope();
			trigger.fire(this.globalScope, triggerScope, triggerScope);
		}
		this.onTickTriggers.removeAll(this.removedOnTickTriggers);
		this.removedOnTickTriggers.clear();

		this.globalScope.runThreads();
		PerfStats.stepEnd(PerfStats.Step.JASS);
		for (final CPlayer player : this.players) {
			final JassAIEnvironment aiScript = player.getAiScript();
			if (aiScript != null) {
				aiScript.update();
			}
		}

		PerfStats.stepEnd(PerfStats.Step.AI);
		this.runningPostUpdateCallbacks.clear();
		this.runningPostUpdateCallbacks.addAll(this.postUpdateCallbacks);
		this.postUpdateCallbacks.clear();
		for (final Runnable runnable : this.runningPostUpdateCallbacks) {
			runnable.run();
		}
		expireScriptTextTags();
		this.handleIdAllocator.endStep();
		SimulationChecksum.afterStep(this);
	}

	private void updateSelections() {
		for (final CPlayer player : this.players) {
			final int playerIndex = player.getId();
			player.getSelection().update(
					unit -> !unit.isPaused() && controls(playerIndex, unit),
					unit -> controls(playerIndex, unit) || unit.isVisible(this, playerIndex),
					(unit, selected) -> unit.fireSelectionEvents(this, player, selected));
		}
	}

	private boolean controls(final int playerIndex, final CUnit unit) {
		return (unit.getPlayerIndex() == playerIndex)
				|| getPlayer(unit.getPlayerIndex()).hasAlliance(playerIndex, CAllianceType.SHARED_CONTROL);
	}

	/**
	 * ShowUnit(false): the unit leaves every player's selection at the next step.
	 */
	public void unitHiddenByScript(final CUnit unit) {
		for (final CPlayer player : this.players) {
			player.getSelection().unitHidden(unit);
		}
	}

	/**
	 * A player's selection command, applied in the step it is scheduled for on every machine.
	 */
	public void selectUnits(final int playerIndex, final int[] deselectedUnitHandleIds,
			final int[] selectedUnitHandleIds) {
		final CPlayer player = getPlayer(playerIndex);
		player.getSelection().apply(unitsOf(deselectedUnitHandleIds), unitsOf(selectedUnitHandleIds),
				(unit, selected) -> unit.fireSelectionEvents(this, player, selected));
	}

	private List<CUnit> unitsOf(final int[] handleIds) {
		final List<CUnit> units = new ArrayList<>(handleIds.length);
		for (final int handleId : handleIds) {
			final CUnit unit = getUnit(handleId);
			if (unit != null) {
				units.add(unit);
			}
		}
		return units;
	}

	public void removeUnit(final CUnit unit) {
		unit.setHidden(true);
		final CPlayer player = this.getPlayer(unit.getPlayerIndex());
		if (unit.getFoodMade() != 0) {
			player.setUnitFoodMade(unit, 0);
		}
		if (unit.getFoodUsed() != 0) {
			player.setUnitFoodUsed(unit, 0);
		}
		// The ground it stood on is free at once, so a script can build there in the same step.
		unit.killPathingInstance();
		this.worldCollision.removeUnit(unit);
		this.removedUnits.add(unit);
	}

	private void finishAddingNewUnits() {
		this.units.addAll(this.newUnits);
		this.newUnits.clear();
		for (final CUnit unit : this.removedUnits) {
			this.units.remove(unit);
			for (final CAbility ability : unit.getAbilities()) {
				this.handleIdToAbility.remove(ability.getHandleId());
			}
			this.handleIdToUnit.remove(unit.getHandleId());
			this.simulationRenderController.removeUnit(unit);
			getPlayerHeroes(unit.getPlayerIndex()).remove(unit);
			unit.onRemove(this);
			freeUnitHandleId(unit);
		}
		this.removedUnits.clear();
	}

	/** A unit gone from the game gives its id back; nothing keyed by the id may stay for the next owner of it. */
	private void freeUnitHandleId(final CUnit unit) {
		if (!this.sharedVisions.isEmpty()) {
			for (final CPlayer viewer : this.players) {
				final long key = (((long) unit.getHandleId()) << 8) | viewer.getId();
				final CUnitVisionFogModifier existing = this.sharedVisions.remove(key);
				if (existing != null) {
					viewer.removeFogModifer(this, existing);
				}
			}
		}
		this.handleIdAllocator.release(unit);
		// Measured on 1.29: a dying unit keeps its pool numbers until it is gone.
		unit.freePoolIds(this.handleIdAllocator);
	}

	public float getGameTimeOfDay() {
		if ((this.falseTimeOfDay != null) && this.falseTimeOfDay.isInitialized()) {
			return this.falseTimeOfDay.getTimeOfDay();
		}
		return (this.currentGameDayTimeElapsed / this.gameplayConstants.getGameDayLength())
				* this.gameplayConstants.getGameDayHours();
	}

	/**
	 * The time of day a script reads: inside a timer that expires within a step, the time at that expiry rather than
	 * at the step's end (1.29: a timer 0.01 s after another reads a time of day 0.0005 hours later).
	 */
	public float getScriptTimeOfDay() {
		if ((this.falseTimeOfDay != null) && this.falseTimeOfDay.isInitialized()) {
			return this.falseTimeOfDay.getTimeOfDay();
		}
		final float dayLength = this.gameplayConstants.getGameDayLength();
		float elapsed = this.currentGameDayTimeElapsed - timeOfDayAheadOfScript();
		if (elapsed < 0) {
			elapsed += dayLength;
		}
		return (elapsed / dayLength) * this.gameplayConstants.getGameDayHours();
	}

	/** Game day seconds the step's clock has run past the exact time of the timer now firing. */
	private float timeOfDayAheadOfScript() {
		if (Double.isNaN(this.timerFiringExactTime) || (this.falseTimeOfDay != null) || this.timeOfDaySuspended) {
			return 0;
		}
		final double ahead = (this.gameTurnTick * (double) WarsmashConstants.SIMULATION_STEP_TIME)
				- this.timerFiringExactTime;
		return ahead > 0 ? (float) (ahead * this.timeOfDayScale) : 0;
	}

	public void setGameTimeOfDay(final float value) {
		final float timeOfDayBefore = getGameTimeOfDay();
		if (this.falseTimeOfDay != null) {
			this.falseTimeOfDay.setTimeOfDay(value);
		}
		else {
			this.currentGameDayTimeElapsed = (value / this.gameplayConstants.getGameDayHours())
					* this.gameplayConstants.getGameDayLength();
			final float ahead = timeOfDayAheadOfScript();
			if (ahead > 0) {
				this.currentGameDayTimeElapsed = (this.currentGameDayTimeElapsed + ahead)
						% this.gameplayConstants.getGameDayLength();
			}
		}
		final float timeOfDayAfter = getGameTimeOfDay();
		this.daytime = (timeOfDayAfter >= this.gameplayConstants.getDawnTimeGameHours())
				&& (timeOfDayAfter < this.gameplayConstants.getDuskTimeGameHours());
		checkTimeOfDayEvents(timeOfDayBefore, timeOfDayAfter);
	}

	public float getTimeOfDayScale() {
		return this.timeOfDayScale;
	}

	public void setTimeOfDayScale(final float timeOfDayScale) {
		this.timeOfDayScale = timeOfDayScale;
	}

	private void checkTimeOfDayEvents(final float timeOfDayBefore, final float timeOfDayAfter) {
		for (final TimeOfDayEvent timeOfDayEvent : this.timeOfDayVariableEvents) {
			if (!timeOfDayEvent.isMatching(timeOfDayBefore) && timeOfDayEvent.isMatching(timeOfDayAfter)) {
				timeOfDayEvent.fire();
			}
		}
	}

	public void addFalseTimeOfDay(final int hour, final int minute, final float duration) {
		final float timeOfDayBefore = getGameTimeOfDay();
		this.falseTimeOfDay = new FalseTimeOfDay(hour, minute,
				CTimer.wholeSteps(duration));
		checkTimeOfDayEvents(timeOfDayBefore, getGameTimeOfDay());
	}

	public boolean isFalseTimeOfDay() {
		return ((this.falseTimeOfDay != null) && this.falseTimeOfDay.isInitialized());
	}

	public int getGameTurnTick() {
		return this.gameTurnTick;
	}

	public CWorldCollision getWorldCollision() {
		return this.worldCollision;
	}

	public CRegionManager getRegionManager() {
		return this.regionManager;
	}

	public CGameplayConstants getGameplayConstants() {
		return this.gameplayConstants;
	}

	public Random getSeededRandom() {
		return this.seededRandom;
	}

	/** See {@link #combatRandom}. */
	public Random getCombatRandom() {
		if (this.combatRandom == null) {
			this.combatRandom = new Random(combatSeed(this.seededRandom));
		}
		return this.combatRandom;
	}

	/** The combat stream as it is, null while a loaded old save has not used it yet. */
	Random getCombatRandomIfMade() {
		return this.combatRandom;
	}

	/** SetRandomSeed: every random stream of the game starts again from the seed. */
	public void setRandomSeed(final int seed) {
		this.seededRandom.setSeed(seed);
		this.combatRandom = new Random(combatSeed(this.seededRandom));
	}

	/**
	 * The combat stream's seed follows from the main stream's state without drawing from it: java.util.Random keeps
	 * its seed private, so a copy made through its serialized form draws instead.
	 */
	private static long combatSeed(final Random random) {
		try {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
			try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
				out.writeObject(random);
			}
			try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
				return ((Random) in.readObject()).nextLong() ^ COMBAT_SEED_SALT;
			}
		}
		catch (final IOException | ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Built on first use from this game's data and tileset; a loaded save from before it was kept builds it again. */
	public RandomTypeHandler getRandomTypes() {
		if (this.randomTypes == null) {
			this.randomTypes = new RandomTypeHandler();
		}
		return this.randomTypes;
	}

	public double getTimerFiringExactTime() {
		return this.timerFiringExactTime;
	}

	public void setTimerFiringExactTime(final double time) {
		this.timerFiringExactTime = time;
	}

	public void unitDamageEvent(final CUnit damagedUnit, final String weaponSound, final String armorType) {
		this.simulationRenderController.spawnDamageSound(damagedUnit, weaponSound, armorType);
	}

	public void destructableDamageEvent(final CDestructable damagedDestructable, final String weaponSound,
			final String armorType) {
		this.simulationRenderController.spawnDamageSound(damagedDestructable, weaponSound, armorType);
	}

	public void itemDamageEvent(final CItem damageItem, final String weaponSound, final String armorType) {
		this.simulationRenderController.spawnDamageSound(damageItem, weaponSound, armorType);
	}

	public void unitConstructedEvent(final CUnit constructingUnit, final CUnit constructedStructure) {
		this.simulationRenderController.spawnUnitConstructionSound(constructingUnit, constructedStructure);
		getPlayer(constructedStructure.getPlayerIndex()).firePlayerUnitEvents(constructedStructure,
				CommonTriggerExecutionScope::unitConstructStartScope, JassGameEventsWar3.EVENT_PLAYER_UNIT_CONSTRUCT_START);
	}

	public void unitUpgradingEvent(final CUnit cUnit, final War3ID upgradeIdType) {
		this.simulationRenderController.unitUpgradingEvent(cUnit, upgradeIdType);
	}

	public void unitCancelUpgradingEvent(final CUnit cUnit, final War3ID upgradeIdType) {
		this.simulationRenderController.unitCancelUpgradingEvent(cUnit, upgradeIdType);
	}

	public boolean isNeutralAggressive(final int playerIndex) {
		return playerIndex == (this.players.size() - 4);
	}

	/**
	 * Measured on 1.29: units of Neutral Hostile and of a computer player that runs no AI script cast their spells by
	 * themselves while they fight (a scripted Blademaster casts Mirror Image as soon as an enemy comes at him).
	 */
	public boolean castsSpellsByItself(final int playerIndex) {
		if (isNeutralAggressive(playerIndex)) {
			return true;
		}
		final CPlayer player = getPlayer(playerIndex);
		return (player != null) && (player.getController() == CMapControl.COMPUTER) && (player.getAiScript() == null);
	}

	public boolean isNeutralPassive(final int playerIndex) {
		return playerIndex == (this.players.size() - 1);
	}

	public void setScriptOrder(final boolean scriptOrder) {
		this.scriptOrder = scriptOrder;
	}

	public boolean isScriptOrder() {
		return this.scriptOrder;
	}

	public void setScriptTargetOrder(final boolean scriptTargetOrder) {
		this.scriptTargetOrder = scriptTargetOrder;
	}

	public boolean isScriptTargetOrder() {
		return this.scriptTargetOrder;
	}

	public CQuestLog getQuestLog() {
		return this.questLog;
	}

	public CTerrainTypeChanges getTerrainTypeChanges() {
		return this.terrainTypeChanges;
	}

	public CTerrainDeformations getTerrainDeformations() {
		return this.terrainDeformations;
	}

	public boolean isAllianceChangesLocked() {
		return this.mapConfig.isMapFlagSet(CMapFlag.MAP_LOCK_ALLIANCE_CHANGES);
	}

	public boolean isSharedAdvancedControl() {
		return this.mapConfig.isMapFlagSet(CMapFlag.MAP_SHARED_ADVANCED_CONTROL);
	}

	public boolean isResourceTradingLocked() {
		return this.mapConfig.isMapFlagSet(CMapFlag.MAP_LOCK_RESOURCE_TRADING);
	}

	public boolean isResourceTradingAlliesOnly() {
		return this.mapConfig.isMapFlagSet(CMapFlag.MAP_RESOURCE_TRADING_ALLIES_ONLY);
	}

	@Override
	public CPlayer getPlayer(final int index) {
		return this.players.get(index);
	}

	public CPlayerUnitOrderExecutor getDefaultPlayerUnitOrderExecutor(final int index) {
		return this.defaultPlayerUnitOrderExecutors.get(index);
	}

	public CPlayerCommandLog getPlayerCommandLog() {
		if (this.playerCommandLog == null) {
			this.playerCommandLog = new CPlayerCommandLog();
		}
		return this.playerCommandLog;
	}

	public CPlayerCommandHost getPlayerCommandHost() {
		return this.playerCommandHost == null ? CPlayerCommandHost.NONE : this.playerCommandHost;
	}

	public void setPlayerCommandHost(final CPlayerCommandHost playerCommandHost) {
		this.playerCommandHost = playerCommandHost;
	}

	public CommandErrorListener getCommandErrorListener() {
		return this.commandErrorListener;
	}

	public void unitConstructFinishEvent(final CUnit constructedStructure) {
		this.simulationRenderController.spawnUnitConstructionFinishSound(constructedStructure);
		noteAlertPosition(constructedStructure);
	}

	public void unitUpgradeFinishEvent(final CUnit constructedStructure) {
		this.simulationRenderController.spawnUnitUpgradeFinishSound(constructedStructure);
		noteAlertPosition(constructedStructure);
	}

	public void unitAttackedEvent(final CUnit unit, final CUnit attacker) {
		if (this.commandErrorListener != null) {
			this.commandErrorListener.unitAttacked(unit, attacker);
		}
	}

	private void noteAlertPosition(final CUnit unit) {
		if (this.commandErrorListener != null) {
			this.commandErrorListener.noteAlertPosition(unit.getPlayerIndex(), unit.getX(), unit.getY());
		}
	}

	public void createDeathExplodeEffect(final CUnit cUnit, final War3ID explodesOnDeathBuffId) {
		this.simulationRenderController.spawnDeathExplodeEffect(cUnit, explodesOnDeathBuffId);
	}

	public HandleIdAllocator getHandleIdAllocator() {
		return this.handleIdAllocator;
	}

	public List<CPlayer> getPlayers() {
		return this.players;
	}

	public List<CEffect> getProjectiles() {
		return this.projectiles;
	}

	public List<CTimer> getActiveTimers() {
		return this.activeTimers;
	}

	public List<CTimer> getAddedTimers() {
		return this.addedTimers;
	}

	/** By player index; a player that never moved a unit has none. */
	public CPathfindingProcessor[] getPathfindingProcessors() {
		return this.pathfindingProcessors;
	}

	public void unitTrainedEvent(final CUnit trainingUnit, final CUnit trainedUnit) {
		this.simulationRenderController.spawnUnitReadySound(trainedUnit);
		noteAlertPosition(trainedUnit);
	}

	public void researchFinishEvent(final CUnit cUnit, final War3ID queuedRawcode, final int level) {
		getCommandErrorListener().showUpgradeCompleteAlert(cUnit.getPlayerIndex(), queuedRawcode, level);
		noteAlertPosition(cUnit);
	}

	public void heroReviveEvent(final CUnit trainingUnit, final CUnit trainedUnit) {
		this.simulationRenderController.heroRevived(trainedUnit);
		this.simulationRenderController.spawnUnitReadySound(trainedUnit);
		noteAlertPosition(trainedUnit);
	}

	public void unitRepositioned(final CUnit cUnit) {
		this.simulationRenderController.unitRepositioned(cUnit);
	}

	public void unitGainResourceEvent(final CUnit unit, final int playerIndex, final ResourceType resourceType,
			final int amount) {
		switch (resourceType) {
		case GOLD: {
			spawnTextTag(unit, playerIndex, TextTagConfigType.GOLD, amount);
			break;
		}
		case LUMBER: {
			spawnTextTag(unit, playerIndex, TextTagConfigType.LUMBER, amount);
			break;
		}
		}
	}

	public TextTag spawnTextTag(final CUnit unit, final int playerIndex, final TextTagConfigType type,
			final int amount) {
		return forReceiver(this.simulationRenderController.spawnTextTag(unit, type, amount), playerIndex, type);
	}

	public TextTag spawnTextTag(final CUnit unit, final int playerIndex, final TextTagConfigType type,
			final String message) {
		return forReceiver(this.simulationRenderController.spawnTextTag(unit, type, message), playerIndex, type);
	}

	private static TextTag forReceiver(final TextTag textTag, final int playerIndex, final TextTagConfigType type) {
		if ((textTag != null) && (type != null) && type.isShownToReceiverOnly()) {
			textTag.setReceivingPlayer(playerIndex);
		}
		return textTag;
	}

	public TextTag createTextTag() {
		final TextTag textTag = this.simulationRenderController.createTextTag();
		textTag.setHandleId(this.handleIdAllocator.createTextTagId());
		this.scriptTextTags.add(textTag);
		return textTag;
	}

	public void destroyTextTag(final TextTag textTag) {
		this.simulationRenderController.destroyTextTag(textTag);
		this.scriptTextTags.remove(textTag);
		this.handleIdAllocator.freeTextTagId(textTag.getHandleId());
	}

	/** The image takes the next number of the image pool. */
	public void addImage(final CImage image) {
		image.setHandleId(this.handleIdAllocator.createImageId());
		this.scriptImages.add(image);
	}

	public void destroyImage(final CImage image) {
		if (image.isValid() && !image.isDestroyed()) {
			image.destroy();
			this.scriptImages.remove(image);
			this.handleIdAllocator.freeImageId(image.getHandleId());
		}
	}

	public List<CImage> getScriptImages() {
		return this.scriptImages;
	}

	public void addTrackable(final CTrackable trackable) {
		this.trackables.add(trackable);
	}

	public List<CTrackable> getTrackables() {
		return this.trackables;
	}

	public CMultiboard getShownMultiboard() {
		return this.multiboardsSuppressed ? null : this.shownMultiboard;
	}

	public void setShownMultiboard(final CMultiboard shownMultiboard) {
		this.shownMultiboard = shownMultiboard;
	}

	public boolean isShownMultiboard(final CMultiboard board) {
		return this.shownMultiboard == board;
	}

	public void setMultiboardsSuppressed(final boolean multiboardsSuppressed) {
		this.multiboardsSuppressed = multiboardsSuppressed;
	}

	/** Measured on 1.29: a script text tag that runs out of life gives its id back like a destroyed one. */
	private void expireScriptTextTags() {
		for (int i = this.scriptTextTags.size() - 1; i >= 0; i--) {
			final TextTag textTag = this.scriptTextTags.get(i);
			if (textTag.advanceGameAge(WarsmashConstants.SIMULATION_STEP_TIME)) {
				destroyTextTag(textTag);
			}
		}
	}

	public void unitGainLevelEvent(final CUnit unit, final boolean showEffect) {
		unit.fireHeroLevelEvents(this);
		if (showEffect) {
			this.simulationRenderController.spawnGainLevelEffect(unit);
		}
	}

	public void heroCreateEvent(final CUnit hero) {
		getPlayerHeroes(hero.getPlayerIndex()).add(hero);
	}

	public void unitPickUpItemEvent(final CUnit cUnit, final CItem item) {
		this.simulationRenderController.spawnUIUnitGetItemSound(cUnit, item);
	}

	public void unitDropItemEvent(final CUnit cUnit, final CItem item) {
		this.simulationRenderController.spawnUIUnitDropItemSound(cUnit, item);
	}

	public List<CUnit> getPlayerHeroes(final int playerIndex) {
		return this.players.get(playerIndex).getHeroes();
	}

	public void unitsLoaded() {
		// called on startup after the system loads the map's units layer, but not any
		// custom scripts yet
		finishAddingNewUnits();
		for (final CUnit unit : this.units) {
			final CPlayer player = this.players.get(unit.getPlayerIndex());
			player.setUnitFoodUsed(unit, unit.getUnitType().getFoodUsed());
			player.setUnitFoodMade(unit, unit.getUnitType().getFoodMade());
			player.addTechtreeUnlocked(this, unit.getTypeId());
			unit.unlockUpgradedFromTypes(this, player);
			player.scoreUnitGained(unit.getUnitType());
		}
	}

	public CWidget getWidget(final int handleId) {
		final CUnit unit = this.handleIdToUnit.get(handleId);
		if (unit != null) {
			return unit;
		}
		final CDestructable destructable = this.handleIdToDestructable.get(handleId);
		if (destructable != null) {
			return destructable;
		}
		final CItem item = this.handleIdToItem.get(handleId);
		if (item != null) {
			return item;
		}
		return null;
	}

	public void createEffectOnUnit(final CUnit unit, final String effectPath) {
		this.simulationRenderController.spawnEffectOnUnit(unit, effectPath);
	}

	public void createTemporarySpellEffectOnUnit(final CUnit unit, final War3ID alias, final CEffectType effectType) {
		this.simulationRenderController.spawnTemporarySpellEffectOnUnit(unit, alias, effectType);
	}

	public SimulationRenderComponentModel createPersistentSpellEffectOnUnit(final CUnit unit, final War3ID alias,
			final CEffectType effectType) {
		return this.simulationRenderController.spawnPersistentSpellEffectOnUnit(unit, alias, effectType);
	}

	public SimulationRenderComponentModel createPersistentSpellEffectOnUnit(final CUnit unit, final War3ID alias,
			final CEffectType effectType, final int index) {
		return this.simulationRenderController.spawnPersistentSpellEffectOnUnit(unit, alias, effectType, index);
	}

	public SimulationRenderComponent unitSoundEffectEvent(final CUnit caster, final War3ID alias) {
		return this.simulationRenderController.spawnAbilitySoundEffect(caster, alias);
	}

	public SimulationRenderComponent unitLoopSoundEffectEvent(final CUnit caster, final War3ID alias) {
		return this.simulationRenderController.loopAbilitySoundEffect(caster, alias);
	}

	public void unitStopSoundEffectEvent(final CUnit caster, final War3ID alias) {
		this.simulationRenderController.stopAbilitySoundEffect(caster, alias);
	}

	public SimulationRenderComponent locationSoundEffectEvent(final float x, final float y, final War3ID alias, final boolean looping) {
		return this.simulationRenderController.spawnAbilitySoundEffect(x, y, alias, looping);
	}

	public void unitPreferredSelectionReplacement(final CUnit unit, final CUnit newUnit) {
		for (final CPlayer player : this.players) {
			player.getSelection().unitReplaced(unit, newUnit);
		}
		this.simulationRenderController.unitPreferredSelectionReplacement(unit, newUnit);
	}

	public void registerTimeOfDayEvent(final TimeOfDayEvent timeOfDayEvent) {
		this.timeOfDayVariableEvents.add(timeOfDayEvent);
	}

	public boolean isTimeOfDayEventRegistered(final TimeOfDayEvent timeOfDayEvent) {
		return this.timeOfDayVariableEvents.contains(timeOfDayEvent);
	}

	public void unregisterTimeOfDayEvent(final TimeOfDayEvent timeOfDayEvent) {
		this.timeOfDayVariableEvents.remove(timeOfDayEvent);
	}

	public void registerOnTickEvent(final Trigger trigger) {
		this.addedOnTickTriggers.add(trigger);
	}

	public void unregisterOnTickEvent(final Trigger trigger) {
		this.removedOnTickTriggers.add(trigger);
	}

	public RemovableTriggerEvent registerTimeOfDayEvent(final GlobalScope globalScope, final Trigger trigger,
			final CLimitOp opcode, final double doubleValue) {
		final TimeOfDayVariableEvent timeOfDayVariableEvent = new TimeOfDayVariableEvent(trigger, opcode, doubleValue,
				globalScope);
		this.timeOfDayVariableEvents.add(timeOfDayVariableEvent);
		return new TimeOfDayEventRegistration(this, trigger, timeOfDayVariableEvent);
	}

	static final class FogUpdateTimer extends CTimer {
		@Override
		public void onFire(final CSimulation simulation) {
			simulation.updateFogOfWar();
		}
	}

	private static final class TimeOfDayEventRegistration extends RemovableTriggerEvent {
		@SavedFormerly("this$0")
		private final CSimulation simulation;
		@SavedFormerly("val$timeOfDayVariableEvent")
		private final TimeOfDayVariableEvent event;

		private TimeOfDayEventRegistration(final CSimulation simulation, final Trigger trigger,
				final TimeOfDayVariableEvent event) {
			super(trigger);
			this.simulation = simulation;
			this.event = event;
		}

		@Override
		public void remove() {
			this.simulation.timeOfDayVariableEvents.remove(this.event);
		}
	}

	public RemovableTriggerEvent registerGameEvent(final GlobalScope globalScope, final Trigger trigger,
			final JassGameEventsWar3 gameEvent) {
		final List<Trigger> triggers = this.gameEventTriggers.computeIfAbsent(gameEvent, key -> new ArrayList<>());
		if ((gameEvent != JassGameEventsWar3.EVENT_GAME_LOADED) && (gameEvent != JassGameEventsWar3.EVENT_GAME_SAVE)
				&& (gameEvent != JassGameEventsWar3.EVENT_GAME_SHOW_SKILL)
				&& (gameEvent != JassGameEventsWar3.EVENT_GAME_BUILD_SUBMENU)) {
			Log.warn("Game event not yet implemented: " + gameEvent);
		}
		triggers.add(trigger);
		return new GameEventRegistration(trigger, triggers);
	}

	private static final class GameEventRegistration extends RemovableTriggerEvent {
		private final Trigger trigger;
		private final List<Trigger> triggers;

		private GameEventRegistration(final Trigger trigger, final List<Trigger> triggers) {
			super(trigger);
			this.trigger = trigger;
			this.triggers = triggers;
		}

		@Override
		public void remove() {
			this.triggers.remove(this.trigger);
		}
	}

	public void fireGameEvent(final JassGameEventsWar3 gameEvent) {
		final List<Trigger> triggers = this.gameEventTriggers.get(gameEvent);
		if (triggers != null) {
			for (final Trigger trigger : new ArrayList<>(triggers)) {
				final CommonTriggerExecutionScope scope = new CommonTriggerExecutionScope(trigger,
						TriggerExecutionScope.EMPTY);
				trigger.fire(this.globalScope, scope, scope);
			}
		}
	}

	public String getSaveBasicFilename() {
		return this.saveBasicFilename;
	}

	public void setSaveBasicFilename(final String saveBasicFilename) {
		this.saveBasicFilename = saveBasicFilename;
	}

	private List<CGlobalEvent> getOrCreateEventList(final JassGameEventsWar3 eventType) {
		List<CGlobalEvent> eventList = this.eventTypeToEvents.get(eventType);
		if (eventList == null) {
			eventList = new ArrayList<>();
			this.eventTypeToEvents.put(eventType, eventList);
		}
		return eventList;
	}

	protected List<CGlobalEvent> getEventList(final JassGameEventsWar3 eventType) {
		return this.eventTypeToEvents.get(eventType);
	}

	public void addGlobalEvent(final CGlobalEvent newEvent) {
		getOrCreateEventList(newEvent.getEventType()).add(newEvent);
	}

	public void removeGlobalEvent(final CGlobalEvent globalEvent) {
		final List<CGlobalEvent> eventList = getEventList(globalEvent.getEventType());
		if (eventList != null) {
			eventList.remove(globalEvent);
		}
	}

	public void fireSpellEventsNoTarget(final JassGameEventsWar3 eventId, final CAbility spellAbility,
			final CUnit spellAbilityUnit) {
		final List<CGlobalEvent> eventList = getEventList(eventId);
		if (eventList != null) {
			for (final CGlobalEvent event : eventList) {
				event.fire(spellAbilityUnit, CommonTriggerExecutionScope.unitSpellNoTargetScope(eventId,
						event.getTrigger(), spellAbility, spellAbilityUnit, spellAbility.getAlias()));
			}
		}
	}

	public void fireSpellEventsPointTarget(final JassGameEventsWar3 eventId, final CAbility spellAbility,
			final CUnit spellAbilityUnit, final AbilityPointTarget abilityPointTarget) {
		final List<CGlobalEvent> eventList = getEventList(eventId);
		if (eventList != null) {
			for (final CGlobalEvent event : eventList) {
				event.fire(spellAbilityUnit,
						CommonTriggerExecutionScope.unitSpellPointScope(eventId, event.getTrigger(), spellAbility,
								spellAbilityUnit, abilityPointTarget, spellAbility.getAlias()));
			}
		}
	}

	public void fireSpellEventsUnitTarget(final JassGameEventsWar3 eventId, final CAbility spellAbility,
			final CUnit spellAbilityUnit, final CUnit unitTarget) {
		final List<CGlobalEvent> eventList = getEventList(eventId);
		if (eventList != null) {
			for (final CGlobalEvent event : eventList) {
				event.fire(spellAbilityUnit, CommonTriggerExecutionScope.unitSpellTargetUnitScope(eventId,
						event.getTrigger(), spellAbility, spellAbilityUnit, unitTarget, spellAbility.getAlias()));
			}
		}
	}

	public void fireSpellEventsItemTarget(final JassGameEventsWar3 eventId, final CAbility spellAbility,
			final CUnit spellAbilityUnit, final CItem itemTarget) {
		final List<CGlobalEvent> eventList = getEventList(eventId);
		if (eventList != null) {
			for (final CGlobalEvent event : eventList) {
				event.fire(spellAbilityUnit, CommonTriggerExecutionScope.unitSpellTargetItemScope(eventId,
						event.getTrigger(), spellAbility, spellAbilityUnit, itemTarget, spellAbility.getAlias()));
			}
		}
	}

	public void fireSpellEventsDestructableTarget(final JassGameEventsWar3 eventId, final CAbility spellAbility,
			final CUnit spellAbilityUnit, final CDestructable destTarget) {
		final List<CGlobalEvent> eventList = getEventList(eventId);
		if (eventList != null) {
			for (final CGlobalEvent event : eventList) {
				event.fire(spellAbilityUnit, CommonTriggerExecutionScope.unitSpellTargetDestructableScope(eventId,
						event.getTrigger(), spellAbility, spellAbilityUnit, destTarget, spellAbility.getAlias()));
			}
		}
	}

	public void addSpellEffectListener(final CUnitSpellEffectListener listener) {
		this.spellEffectListeners.add(listener);
	}

	public void removeSpellEffectListener(final CUnitSpellEffectListener listener) {
		this.spellEffectListeners.remove(listener);
	}

	public void spellEffectEvent(final CUnit caster, final CAbility ability) {
		for (int i = this.spellEffectListeners.size() - 1; i >= 0; i--) {
			this.spellEffectListeners.get(i).onSpellEffect(this, caster, ability);
		}
	}

	public void heroDeathEvent(final CUnit cUnit) {
		this.simulationRenderController.heroDeathEvent(cUnit);
	}

	public void heroKilledEvent(final CUnit hero) {
		if (this.commandErrorListener != null) {
			this.commandErrorListener.heroDied(hero);
		}
	}

	public void heroDissipateEvent(final CUnit cUnit) {
		getPlayer(cUnit.getPlayerIndex()).onHeroDeath(cUnit);
	}

	public void removeItem(final CItem cItem) {
		cItem.forceDropIfHeld(this);
		cItem.setHidden(true); // TODO fix
		cItem.setLife(this, 0);
		this.worldCollision.removeItem(cItem);
		// Measured on 1.29: the id of a removed item is given again.
		if (this.handleIdToItem.remove(cItem.getHandleId(), cItem)) {
			this.handleIdAllocator.release(cItem);
			cItem.freePoolIds(this.handleIdAllocator);
		}
	}

	public void removeDestructable(final CDestructable dest) {
		// A removed destructable is gone for enumerations too; a script that removes it again (a campaign cleanup
		// enumerating a rect it already cleared) must not remove its model twice.
		if (dest.isRemovedFromGame() || this.removedDestructables.contains(dest)) {
			return;
		}
		if (dest.isDead()) {
			// Measured on 1.29: removing a destructable that is already dead fires its death event again.
			dest.fireDeathEvents(this);
		}
		else {
			dest.setLife(this, 0);
		}
		this.removedDestructables.add(dest);
	}

	public SimulationRenderComponentModel createSpellEffectOverDestructable(final CUnit source,
			final CDestructable target, final War3ID alias, final float artAttachmentHeight) {
		return this.simulationRenderController.createSpellEffectOverDestructable(source, target, alias,
				artAttachmentHeight);
	}

	public SimulationRenderComponentModel spawnSpellEffectOnPoint(final float x, final float y, final float facing,
			final War3ID alias, final CEffectType effectType, final int index) {
		return this.simulationRenderController.spawnSpellEffectOnPoint(x, y, facing, alias, effectType, index);
	}

	public void spawnTemporarySpellEffectOnPoint(final float x, final float y, final float facing, final War3ID alias,
			final CEffectType effectType, final int index) {
		this.simulationRenderController.spawnTemporarySpellEffectOnPoint(x, y, facing, alias, effectType, index);
	}

	public void tagTreeOwned(final CDestructable target) {
		this.ownedTreeSet.add(target);
	}

	public void untagTreeOwned(final CDestructable target) {
		this.ownedTreeSet.remove(target);
	}

	public boolean isTreeOwned(final CDestructable tree) {
		return this.ownedTreeSet.contains(tree);
	}

	public static interface TimeOfDayEvent {
		public void fire();

		public boolean isMatching(double timeOfDayBefore);
	}

	private static final class TimeOfDayVariableEvent extends VariableEvent implements TimeOfDayEvent {
		private final GlobalScope globalScope;

		public TimeOfDayVariableEvent(final Trigger trigger, final CLimitOp limitOp, final double doubleValue,
				final GlobalScope globalScope) {
			super(trigger, limitOp, doubleValue);
			this.globalScope = globalScope;
		}

		@Override
		public void fire() {
			final CommonTriggerExecutionScope scope = CommonTriggerExecutionScope.gameStateLimitScope(getTrigger(),
					CGameState.TIME_OF_DAY);
			this.globalScope.queueTrigger(null, null, getTrigger(), scope, scope);
		}
	}

	/** A trigger on an integer game state: divine intervention or disconnected. */
	private static final class GameStateVariableEvent extends VariableEvent {
		private final GlobalScope globalScope;
		private final CGameState gameState;

		private GameStateVariableEvent(final Trigger trigger, final CGameState gameState, final CLimitOp limitOp,
				final double doubleValue, final GlobalScope globalScope) {
			super(trigger, limitOp, doubleValue);
			this.gameState = gameState;
			this.globalScope = globalScope;
		}

		private void fire() {
			final CommonTriggerExecutionScope scope = CommonTriggerExecutionScope.gameStateLimitScope(getTrigger(),
					this.gameState);
			this.globalScope.queueTrigger(null, null, getTrigger(), scope, scope);
		}
	}

	private static final class GameStateEventRegistration extends RemovableTriggerEvent {
		private final CSimulation simulation;
		private final GameStateVariableEvent event;

		private GameStateEventRegistration(final CSimulation simulation, final GameStateVariableEvent event) {
			super(event.getTrigger());
			this.simulation = simulation;
			this.event = event;
		}

		@Override
		public void remove() {
			this.simulation.gameStateVariableEvents.remove(this.event);
		}
	}

	public RemovableTriggerEvent registerGameStateEvent(final GlobalScope globalScope, final Trigger trigger,
			final CGameState gameState, final CLimitOp opcode, final double value) {
		if (gameState == CGameState.TIME_OF_DAY) {
			return registerTimeOfDayEvent(globalScope, trigger, opcode, value);
		}
		final GameStateVariableEvent event = new GameStateVariableEvent(trigger, gameState, opcode, value,
				globalScope);
		this.gameStateVariableEvents.add(event);
		return new GameStateEventRegistration(this, event);
	}

	/**
	 * Measured on 1.29: an integer game state's events fire at once each time the state is set to a value they match,
	 * also when it matched before (the time of day fires only when it comes to match).
	 */
	private void fireGameStateEvents(final CGameState gameState, final int value) {
		for (final GameStateVariableEvent event : new ArrayList<>(this.gameStateVariableEvents)) {
			if ((event.gameState == gameState) && event.isMatching(value)) {
				event.fire();
			}
		}
	}

	/**
	 * A script's SetUnitFlyHeight: at a positive rate the height moves toward the target that many units per second,
	 * replacing a change still under way; otherwise it is set at once.
	 */
	public void setUnitFlyHeight(final CUnit unit, final float height, final float rate) {
		final CTimerFlyHeight running = this.flyHeightChanges.remove(unit);
		if (running != null) {
			running.setRepeats(false);
		}
		if ((rate <= 0) || (unit.getFlyHeight() == height)) {
			unit.setFlyHeight(height);
			return;
		}
		final CTimerFlyHeight change = new CTimerFlyHeight(unit, height, rate);
		this.flyHeightChanges.put(unit, change);
		change.start(this);
	}

	public CTimerFlyHeight getFlyHeightChange(final CUnit unit) {
		return this.flyHeightChanges.get(unit);
	}

	public void endFlyHeightChange(final CTimerFlyHeight change) {
		if (this.flyHeightChanges.get(change.getUnit()) == change) {
			this.flyHeightChanges.remove(change.getUnit());
		}
	}

	/** A script's SetUnitFacingTimed; a duration of 0 or less turns the unit as SetUnitFacing does. */
	public void setUnitFacingTimed(final CUnit unit, final float facing, final float duration) {
		this.facingTurns.remove(unit);
		if (duration <= 0) {
			unit.getDrawnFacing(this);
			unit.setFacing(facing);
			return;
		}
		final CTimerFacing turn = new CTimerFacing(unit, facing, duration);
		this.facingTurns.put(unit, turn);
		turn.start(this);
	}

	public CTimerFacing getFacingTurn(final CUnit unit) {
		return this.facingTurns.get(unit);
	}

	public void endFacingTurn(final CTimerFacing turn) {
		if (this.facingTurns.get(turn.getUnit()) == turn) {
			this.facingTurns.remove(turn.getUnit());
		}
	}

	public void setAllItemTypeSlots(final int slots) {
		this.allItemTypeSlots = slots;
		for (final CUnit unit : this.units) {
			final CAbilitySellItems sellItems = getItemStock(unit, false);
			if (sellItems != null) {
				sellItems.setItemTypeSlots(slots);
			}
		}
	}

	public void setAllUnitTypeSlots(final int slots) {
		this.allUnitTypeSlots = slots;
		for (final CUnit unit : this.units) {
			final CAbilitySellUnits sellUnits = getUnitStock(unit, false);
			if (sellUnits != null) {
				sellUnits.setUnitTypeSlots(slots);
			}
		}
	}

	/**
	 * The item stock of a shop (not the items it makes); with create, a shop without one gets an empty stock for
	 * scripts to fill, as the marketplace does.
	 */
	public CAbilitySellItems getItemStock(final CUnit unit, final boolean create) {
		for (final CAbility ability : unit.getAbilities()) {
			if ((ability instanceof CAbilitySellItems) && !((CAbilitySellItems) ability).isMakeItems()) {
				return (CAbilitySellItems) ability;
			}
		}
		if (!create) {
			return null;
		}
		final CAbilitySellItems sellItems = new CAbilitySellItems(this.handleIdAllocator.createId(),
				Collections.emptyList(), false);
		sellItems.setItemTypeSlots(this.allItemTypeSlots);
		unit.add(this, sellItems);
		return sellItems;
	}

	public CAbilitySellUnits getUnitStock(final CUnit unit, final boolean create) {
		final CAbilitySellUnits existing = unit.getFirstAbilityOfType(CAbilitySellUnits.class);
		if ((existing != null) || !create) {
			return existing;
		}
		final CAbilitySellUnits sellUnits = new CAbilitySellUnits(this.handleIdAllocator.createId(),
				Collections.emptyList());
		sellUnits.setUnitTypeSlots(this.allUnitTypeSlots);
		unit.add(this, sellUnits);
		return sellUnits;
	}

	/**
	 * The shops that scripts stock for all (AddItemToAllStock, AddUnitToAllStock) are those with the Sell Items or
	 * Sell Units ability in their data, such as the marketplace.
	 */
	public List<CUnit> getScriptStockedShops(final War3ID sellAbilityId) {
		final List<CUnit> shops = new ArrayList<>();
		for (final CUnit unit : this.units) {
			if (!unit.isDead() && unit.getUnitType().getAbilityList().contains(sellAbilityId)) {
				shops.add(unit);
			}
		}
		return shops;
	}

	public int getDivineInterventionGameState() {
		return this.divineInterventionGameState;
	}

	public void setDivineInterventionGameState(final int value) {
		this.divineInterventionGameState = value;
		fireGameStateEvents(CGameState.DIVINE_INTERVENTION, value);
	}

	public int getDisconnectedGameState() {
		return this.disconnectedGameState;
	}

	public void setDisconnectedGameState(final int value) {
		this.disconnectedGameState = value;
		fireGameStateEvents(CGameState.DISCONNECTED, value);
	}

	public void setTimeOfDaySuspended(final boolean flag) {
		this.timeOfDaySuspended = flag;

	}

	public boolean isTimeOfDaySuspended() {
		return this.timeOfDaySuspended;
	}

	public boolean isDay() {
		return this.daytime;
	}

	public boolean isNight() {
		return !this.daytime;
	}

	public void setBlight(final float x, final float y, final float radius, final boolean blighted) {
		this.simulationRenderController.setBlight(x, y, radius, blighted);
	}

	public void setBlightSquare(final float x, final float y, final float halfSize, final boolean blighted) {
		this.simulationRenderController.setBlightSquare(x, y, halfSize, blighted);
	}

	public void unitUpdatedType(final CUnit unit, final War3ID typeId, final boolean updatePortrait) {
		// The new look starts from its own type's tint, whatever color the old one had.
		unit.setVertexColor(this.unitData.getTintingColor(typeId));
		this.simulationRenderController.unitUpdatedType(unit, typeId, updatePortrait);
	}

	private void setupCreatedUnit(final CUnit unit) {
		final CUnitType unitTypeInstance = unit.getUnitType();
		final int manaInitial = unitTypeInstance.getManaInitial();
		final int speed = unitTypeInstance.getSpeed();
		this.unitData.addDefaultAbilitiesToUnit(this, this.handleIdAllocator, unitTypeInstance, true, manaInitial,
				speed, unit);
		this.unitData.applyPlayerUpgradesToUnit(this, unit.getPlayerIndex(), unitTypeInstance, unit);
		if (unitTypeInstance.isHero()) {
			// A new hero also starts with the mana its intelligence gives, as in the original game.
			unit.setMana(Math.min(unit.getMaximumMana(),
					manaInitial + (unit.getMaximumMana() - unitTypeInstance.getManaMaximum())));
		}
		final ArgbImage buildingPathingPixelMap = unitTypeInstance.getBuildingPathingPixelMap();
		if (buildingPathingPixelMap != null) {
			unit.regeneratePathingInstance(this, buildingPathingPixelMap);
		}
	}

	public void changeUnitScale(final CUnit unit, final float scale, boolean multiplier) {
		this.simulationRenderController.changeUnitScale(unit, scale, multiplier);
	}

	public void changeUnitColor(final CUnit unit, final int playerIndex) {
		this.simulationRenderController.changeUnitColor(unit, playerIndex);
	}

	public void changeUnitVertexColor(final CUnit unit, final Color color) {
		unit.setVertexColor(new float[] { color.r, color.g, color.b, color.a });
		this.simulationRenderController.changeUnitVertexColor(unit, color);
	}

	public void changeUnitVertexColor(final CUnit unit, final float r, final float g, final float b) {
		unit.setVertexColor(new float[] { r, g, b, 1 });
		this.simulationRenderController.changeUnitVertexColor(unit, r, g, b);
	}

	public void changeUnitVertexColor(final CUnit unit, final float r, final float g, final float b, final float a) {
		unit.setVertexColor(new float[] { r, g, b, a });
		this.simulationRenderController.changeUnitVertexColor(unit, r, g, b, a);
	}

	/** The unit's vertex color {r, g, b, a}, a copy the caller may change. */
	public float[] getUnitVertexColor(final CUnit unit) {
		final float[] vertexColor = unit.getVertexColor();
		return vertexColor != null ? vertexColor.clone() : this.unitData.getTintingColor(unit.getTypeId());
	}

	public void setGlobalScope(final GlobalScope globalScope) {
		this.globalScope = globalScope;
	}

	public GlobalScope getGlobalScope() {
		return this.globalScope;
	}

	public int[] getTerrainModBufferSize(float x, float y, float width, float height) {
		return this.terrainData.getTerrainModBufferSize(x, y, width, height);
	}

	public int[] getTerrainModBufferSize(float centerX, float centerY, float radius) {
		return this.terrainData.getTerrainModBufferSize(centerX, centerY, radius);
	}

	public void adjustTerrain(int[] rect, float[] modBuffer) {
		this.terrainData.adjustGround(rect, modBuffer);
		this.simulationRenderController.adjustTerrain(rect, modBuffer);
	}

	public void adjustTerrain(float x, float y, float i) {
		this.terrainData.adjustGround(x, y, i);
		this.simulationRenderController.adjustTerrain(x, y, i);
	}

	public float getTerrainSpaceX(final float x) {
		return this.terrainData.getTerrainSpaceX(x);
	}

	public float getTerrainSpaceY(final float y) {
		return this.terrainData.getTerrainSpaceY(y);
	}

	public int getTerrainHeight(final float x, final float y) {
		return this.terrainData.getLayerHeight(x, y);
	}

	public boolean isTerrainRomp(final float x, final float y) {
		return this.terrainData.isRamp(x, y);
	}

	public boolean isTerrainWater(final float x, final float y) {
		return this.terrainData.isWater(x, y);
	}

	public int getMapVersion() {
		return this.mapVersion;
	}

	/** PlayCinematic: the movie plays when the game that asked for it ends, in the order they were asked for. */
	public void queueCinematic(final String name) {
		if ((name != null) && !name.isEmpty()) {
			this.queuedCinematics.add(name);
		}
	}

	/** @return the movies asked for since the last call, oldest first */
	public List<String> takeQueuedCinematics() {
		final List<String> taken = new ArrayList<>(this.queuedCinematics);
		this.queuedCinematics.clear();
		return taken;
	}

	public GameDataConstants getGameData() {
		return this.gameData;
	}

	public int getMapGameVersion() {
		return this.mapGameVersion;
	}

	public boolean isMapReignOfChaos() {
		return this.mapVersion <= 24;
	}

	public void setFogMaskEnabled(final boolean enable) {
		this.fogMaskEnabled = enable;
	}

	@Override
	public boolean isFogMaskEnabled() {
		return this.fogMaskEnabled;
	}

	public void setFogEnabled(final boolean fogEnabled) {
		this.fogEnabled = fogEnabled;
	}

	@Override
	public boolean isFogEnabled() {
		return this.fogEnabled;
	}

	/** iseedeadpeople: measured on 1.29, every place is visible to every player. */
	public void toggleCheatRevealMap() {
		this.cheatRevealMap = !this.cheatRevealMap;
	}

	public boolean isCheatRevealMap() {
		return this.cheatRevealMap;
	}

	/** warpten: measured on 1.29, train, research and build run 16 times faster. */
	public void toggleCheatBuildSpeed() {
		this.cheatBuildSpeed = this.cheatBuildSpeed == 1.0f ? 16.0f : 1.0f;
	}

	public float getCheatBuildSpeed() {
		return this.cheatBuildSpeed;
	}

	@Override
	public byte getFogStateFromSettings(final byte mask) {
		if (this.cheatRevealMap) {
			return CFogState.VISIBLE.getMask();
		}
		final CFogState state = CFogState.getByMask(mask);
		switch (state) {
		case MASKED:
			if (this.fogMaskEnabled) {
				if (this.fogEnabled) {
					return CFogState.MASKED.getMask();
				} else {
					return CFogState.MASKED.getMask();
				}
			} else if (this.fogEnabled) {
				return CFogState.FOGGED.getMask();
			} else {
				return CFogState.VISIBLE.getMask();
			}
		case FOGGED:
			if (this.fogMaskEnabled) {
				if (this.fogEnabled) {
					return CFogState.FOGGED.getMask();
				} else {
					return CFogState.VISIBLE.getMask();
				}
			} else if (this.fogEnabled) {
				return CFogState.FOGGED.getMask();
			} else {
				return CFogState.VISIBLE.getMask();
			}
		case VISIBLE:
			return state.getMask();
		}
		return 0;
	}

	@Override
	public void setColor(final CPlayerJass player, final CPlayerColor color) {
		final int previousColor = player.getColor();
		final int newColor = color.ordinal();
		player.setColor(newColor);
		for (final CUnit unit : this.units) {
			if (unit.getPlayerIndex() == player.getId()) {
				this.simulationRenderController.changeUnitPlayerColor(unit, previousColor, newColor);
			}
		}
		for (final CUnit unit : this.newUnits) {
			if (unit.getPlayerIndex() == player.getId()) {
				this.simulationRenderController.changeUnitPlayerColor(unit, previousColor, newColor);
			}
		}
	}

	// Deaths and trained units each ask for this, so a fight queued it many times a step; a second run in the same
	// step changes nothing.
	public void fireRequirementUpdateForAbilities(final CPlayer player, final boolean disable) {
		final Integer key = (player.getId() * 2) + (disable ? 1 : 0);
		if (!this.queuedRequirementUpdates.add(key)) {
			return;
		}
		this.postUpdateCallbacks.add(new Runnable() {
			@Override
			public void run() {
				CSimulation.this.queuedRequirementUpdates.remove(key);
				for (final CUnit unit : getUnits()) {
					if (unit.getPlayerIndex() == player.getId()) {
						unit.checkDisabledAbilities(CSimulation.this, disable);
					}
				}
				// A unit made this tick (a trained unit that unlocks its own requirement, such as the first
				// Hippogryph for Pick up Archer) only joins the unit list on the next update.
				for (final CUnit unit : CSimulation.this.newUnits) {
					if (unit.getPlayerIndex() == player.getId()) {
						unit.checkDisabledAbilities(CSimulation.this, disable);
					}
				}
			}
		});
	}

}
