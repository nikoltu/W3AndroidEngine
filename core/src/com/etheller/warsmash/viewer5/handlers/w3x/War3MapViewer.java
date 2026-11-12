package com.etheller.warsmash.viewer5.handlers.w3x;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentWeather;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentUbersplat;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.ReplayPlayback;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.ReplayRecording;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.ReplaySession;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.WeatherType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWeatherEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ExternStringMsgTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.jass.RecordingAbilityTargetCheckReceiver;
import com.etheller.interpreter.ast.value.visitor.ArithmeticJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ArithmeticLeftHandCodeJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ArithmeticLeftHandStructJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ArithmeticLeftHandHandleJassValueVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CGlobalWidgetEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CWidgetEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CStackedSounds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CWidgetAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.BuildOnBuildingIntersector;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.GetInstantTransformationBuffVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.commandbuttons.CommandCardPopulatingAbilityVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissileSplash;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissileBounce;
import com.etheller.warsmash.viewer5.AudioContext;
import java.util.IdentityHashMap;
import com.badlogic.gdx.audio.Sound;
import java.io.BufferedReader;
import java.io.File;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.etheller.warsmash.common.FetchDataTypeName;
import com.etheller.warsmash.common.LoadGenericCallback;
import com.etheller.warsmash.datasources.CompoundDataSource;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.datasources.LayeredDataSource;
import com.etheller.warsmash.datasources.MpqDataSource;
import com.etheller.warsmash.datasources.SubdirDataSource;
import com.etheller.warsmash.networking.GameTurnManager;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.w3x.War3Map;
import com.etheller.warsmash.parsers.w3x.objectdata.Warcraft3MapRuntimeObjectData;
import com.etheller.warsmash.parsers.w3x.w3e.War3MapW3e;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3iFlags;
import com.etheller.warsmash.parsers.w3x.wpm.War3MapWpm;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.units.custom.WTS;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.GameDataConstants;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.MappedData;
import com.etheller.warsmash.util.Quadtree;
import com.etheller.warsmash.util.QuadtreeIntersector;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.util.WorldEditStrings;
import com.etheller.warsmash.viewer5.CanvasProvider;
import com.etheller.warsmash.viewer5.FogSettings;
import com.etheller.warsmash.viewer5.GenericResource;
import com.etheller.warsmash.viewer5.Grid;
import com.etheller.warsmash.viewer5.ModelInstance;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.SceneLightManager;
import com.etheller.warsmash.viewer5.WorldScene;
import com.etheller.warsmash.viewer5.gl.WebGL;
import com.etheller.warsmash.viewer5.handlers.AbstractMdxModelViewer;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxHandler;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxHandler.ShaderEnvironmentType;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.Terrain;
import com.etheller.warsmash.viewer5.handlers.w3x.lightning.LightningEffectModel;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderFogGhost;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDoodad;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItem;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItemTypeData;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderLightningEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderShadowType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderImage;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitTypeData;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.BuffUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.EffectAttachmentUI;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedFormerly;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedInterfaceState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameExit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CImage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CTrackable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidgetFilterFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CTerrainData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.SimulationChecksum;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CPathingTextures;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.NoRenderController;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CMapControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CPlayerSlotState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightningMovable;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.SettableCommandErrorListener;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.sound.KeyedSounds;
import mpq.MPQArchive;
import mpq.MPQException;




public class War3MapViewer extends AbstractMdxModelViewer implements MdxAssetLoader {
	private static final int MAX_STEPS_PER_FRAME = 3;
	// A sound not decoded by then plays as soon as it is; the start of the game does not wait longer for it.
	static final long SOUND_PRELOAD_WAIT_MILLIS = 3000;
	/** Game seconds per real second (desktop -speed), so automated playthroughs of long games finish sooner. */
	private static float defaultTimeScale = 1;
	static final byte FOGGED_SHADE = (byte) 168;
	static final byte MASKED_PARTIALLY_VISIBLE_SHADE = (byte) 214;
	// Supported graphics modes of the map information: 1 Classic, 2 Reforged, 3 either.
	private static final long SUPPORTED_MODES_REFORGED = 2;
	private final EnumSet<PauseSource> pauseSources = EnumSet.noneOf(PauseSource.class);
	float timeScale = defaultTimeScale;
	private final CGameExit gameExit = new CGameExit();
	final CSoundMixer soundMixer = new CSoundMixer();
	static final List<String> ORIGIN_STRING_LIST = Arrays.asList("origin");

	public int debugDepth = 9999;

	static final float DEFAULT_TEXT_TAG_HEIGHT = 0.024f;
	static final int BUILTIN_TEXT_TAG_REPORTED_HANDLE_ID = -1;
	static final War3ID ABILITY_HERO_RAWCODE = War3ID.fromString("AHer");
	static final War3ID ABILITY_REVIVE_RAWCODE = War3ID.fromString("Arev");
	/** Runs the game without drawing it (see {@link #updateAndRender()}). */
	static final boolean NO_RENDER = Boolean.getBoolean("warsmash.norender");
	private static final String DESTRUCTABLE_PATHING = "pathTex"; // replaced from 'bptx'
	private static final String DESTRUCTABLE_PATHING_DEATH = "pathTexDeath"; // replaced from 'bptd'
	static final War3ID sloc = War3ID.fromString("sloc");
	static final LoadGenericCallback stringDataCallback = new StringDataCallbackImplementation();
	static final float[] rayHeap = new float[6];
	public static final Ray gdxRayHeap = new Ray();
	public static final Plane planeHeap = new Plane();
	static final Vector2 mousePosHeap = new Vector2();
	public static final Vector3 intersectionHeap = new Vector3();
	public static final Vector3 intersectionHeap2 = new Vector3();
	final Vector3 constructionPickHeap = new Vector3();
	static final Rectangle rectangleHeap = new Rectangle();
	public static final StreamDataCallbackImplementation streamDataCallback = new StreamDataCallbackImplementation();

	public WorldScene worldScene;
	boolean worldHidden;
	NoRenderController renderController;
	/** The step the game plays on to without drawing (see {@link #fastForwardTo}), or -1. */
	int fastForwardTarget = -1;
	FastForwardListener fastForwardReached;
	int fastForwardSteps;
	long fastForwardNanos;
	public boolean anyReady;
	public MappedData terrainData = new MappedData();
	public MappedData cliffTypesData = new MappedData();
	public MappedData waterData = new MappedData();
	public boolean terrainReady;
	public boolean cliffsReady;
	public boolean doodadsAndDestructiblesLoaded;
	public MappedData doodadsData = new MappedData();
	public MappedData doodadMetaData = new MappedData();
	public MappedData destructableMetaData = new MappedData();
	public List<RenderDoodad> doodads = new ArrayList<>();
	static final float SKY_FAR_PLANE = 100000f;
	static final float UNIT_INDICATOR_SECONDS = 3f;
	static final float UNIT_INDICATOR_PULSE_SECONDS = 1f;
	Scene skyScene;
	MdxComplexInstance skyInstance;
	ModelCinematic modelCinematic;
	final List<UnitIndicator> unitIndicators = new ArrayList<>();
	public List<RenderDoodad> decals = new ArrayList<>();
	private int fogDoodadTicks;
	public List<TerrainDoodad> terrainDoodads = new ArrayList<>();
	public boolean doodadsReady;
	public boolean unitsAndItemsLoaded;
	public MappedData unitsData = new MappedData();
	public MappedData unitMetaData = new MappedData();
	public List<RenderWidget> widgets = new ArrayList<>();
	public List<RenderUnit> units = new ArrayList<>();
	final List<RenderFogGhost> fogGhosts = new ArrayList<>();
	/** Creeps the map placed as the game began, removed ones included: x, y, level; null once taken. */
	private List<float[]> initialCreeps = new ArrayList<>();
	public List<RenderEffect> projectiles = new ArrayList<>();
	private final List<RenderLightningEffect> lightnings = new ArrayList<>();
	final List<RenderWeatherEffect> weatherEffects = new ArrayList<>();
	final CStackedSounds stackedSounds = new CStackedSounds();
	final Vector3 stackedSoundsForward = new Vector3();
	final List<com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUbersplat> scriptUbersplats = new ArrayList<>();
	final Map<CImage, RenderImage> scriptImages = new IdentityHashMap<>();
	/** Not among the effects: a trackable's model is made again from the trackable after a load. */
	final List<RenderSpellEffect> trackableModels = new ArrayList<>();
	final List<CTrackable> trackableOwners = new ArrayList<>();
	Map<War3ID, WeatherType> weatherTypes;
	public boolean unitsReady;
	public War3Map mapMpq;

	private final DataSource gameDataSource;
	private final GameDataConstants gameData;

	public Terrain terrain;
	public int renderPathing = 0;
	public int renderLighting = 1;

	final Set<String> selectedSplatModelKeys = new HashSet<>();
	public List<RenderWidget> selected = new ArrayList<>();
	final Set<String> mouseHighlightSplatModelKeys = new HashSet<>();
	final List<RenderWidget> mouseHighlightWidgets = new ArrayList<>();
	DataTable unitAckSoundsTable;
	DataTable unitCombatSoundsTable;
	DataTable animSoundsTable;
	private DataTable scriptSoundsTable;
	public DataTable miscData;
	private com.etheller.warsmash.viewer5.handlers.w3x.ui.AllyColorFilter allyColorFilter;
	boolean restartMissionDisabled;
	String altMinimapIcon;
	Element misc;
	private final Map<String, TextTagConfig> keyToTextTagConfig = new HashMap<>();
	DataTable unitGlobalStrings;
	public DataTable uiSoundsTable;
	DataTable lightningDataTable;
	Map<War3ID, LightningEffectModel> lightningTypeToModel;
	MdxComplexInstance confirmationInstance;
	public MdxComplexInstance dncUnit;
	public MdxComplexInstance dncTerrain;
	public MdxComplexInstance dncTarget;
	public CSimulation simulation;
	float updateTime = 0;

	// for World Editor, I think
	public Vector2[] startLocations;

	final DynamicShadowManager dynamicShadowManager = new DynamicShadowManager();

	/** Seeds the simulation; -Dwarsmash.seed fixes it so test runs repeat. Kept in saved games. */
	private int gameSeed = chooseGameSeed();

	private final Random seededRandom = new Random(this.gameSeed);

	private ReplaySession replaySession;
	private boolean replayNotSaved;
	boolean gameEnded;
	private Runnable gameEndScreen;

	final Map<String, ArgbImage> filePathToPathingMap = new HashMap<>();
	private CPathingTextures pathingTextures;

	final List<SelectionCircleSize> selectionCircleSizes = new ArrayList<>();

	final Map<CUnit, RenderUnit> unitToRenderPeer = new HashMap<>();
	final Map<CDestructable, RenderDestructable> destructableToRenderPeer = new HashMap<>();
	final Map<CItem, RenderItem> itemToRenderPeer = new HashMap<>();
	GameUI gameUI;
	Vector3 lightDirection;

	Quadtree<MdxComplexInstance> walkableObjectsTree;
	private final QuadtreeIntersectorFindsWalkableRenderHeight walkablesIntersector = new QuadtreeIntersectorFindsWalkableRenderHeight();
	final QuadtreeIntersectorFindsHitPoint walkablesIntersectionFinder = new QuadtreeIntersectorFindsHitPoint();
	private final QuadtreeIntersectorFindsHighestWalkable intersectorFindsHighestWalkable = new QuadtreeIntersectorFindsHighestWalkable();

	KeyedSounds uiSounds;
	Collection<String> soundsToPreload;
	int localPlayerIndex;
	private final SettableCommandErrorListener commandErrorListener;

	public final List<TextTag> textTags = new ArrayList<>();

	private final War3MapConfig mapConfig;
	CPlayerFogOfWar observerFogOfWar;
	/** Whose sight a replay's picture shows: a player, {@link #REPLAY_VISION_ALL_PLAYERS} or none (a game). */
	int replayVisionPlayer = REPLAY_VISION_NONE;
	boolean replayFogShown = true;
	CPlayerFogOfWar replayFogOfWar;
	final List<CPlayerFogOfWar> replayVisionSources = new ArrayList<>();
	/** A replay's speed: game and animation time both run this many times faster. */
	private float replaySpeed = 1;
	public static final int REPLAY_VISION_ALL_PLAYERS = -1;
	static final int REPLAY_VISION_NONE = -2;

	GameTurnManager gameTurnManager;

	War3MapW3i lastLoadedMapInformation;
	private String mapFileName;
	private int campaign = -1;
	private boolean reforgedCampaignMaps;
	private FileHandle savedGameToLoad;
	private boolean savedGameLoadedByScript;
	InterfaceStateKeeper interfaceStateKeeper;
	private Object scriptEnvironment;
	private final List<SavedGame.AiEntry> loadedScripts = new ArrayList<>();
	String dayNightTerrainModel;
	String dayNightUnitModel;
	String skyModel;
	float[] waterColor;
	private AudioContext uiAudioContext;
	private final Map<Sound, String> fileSounds = new IdentityHashMap<>();
	final List<CreatedDestructable> createdDestructables = new ArrayList<>();
	FileHandle pendingSave;
	FileHandle pendingLoad;
	boolean pendingLoadByScript;
	Runnable afterPendingSave;
	SaveGameListener saveGameListener;
	LoadGameListener loadGameListener;

	FogSettings defaultFogSettings;

	private int successfulFrames = 0;

	public War3MapViewer(final DataSource dataSource, final CanvasProvider canvas, final War3MapConfig mapConfig,
			final GameTurnManager gameTurnManager) {
		super(dataSource, canvas);
		this.gameTurnManager = gameTurnManager;
		this.gameData = GameDataConstants.current();
		this.startLocations = new Vector2[this.gameData.getMaxPlayers()];
		this.shaderEnvironmentType = ShaderEnvironmentType.GAME;
		this.gameDataSource = dataSource;

		final WebGL webGL = this.webGL;

		addHandler(new MdxHandler());

		this.wc3PathSolver = PathSolver.DEFAULT;

		this.worldScene = addWorldScene();

		if (!this.dynamicShadowManager.setup(webGL)) {
			throw new IllegalStateException("FrameBuffer setup failed");
		}

		this.commandErrorListener = new SettableCommandErrorListener();
		this.mapConfig = mapConfig;
	}

	public void loadSLKs(final WorldEditStrings worldEditStrings) throws IOException {
		MapViewerLoading.loadSLKs(this, worldEditStrings);
	}

	TextTagConfig getTextTagConfig(final String key) {
		TextTagConfig textTagConfig = this.keyToTextTagConfig.get(key);
		if (textTagConfig == null) {
			textTagConfig = MapViewerLoading.parseTextTagConfig(this.misc, key);
			this.keyToTextTagConfig.put(key, textTagConfig);
		}
		return textTagConfig;
	}

	public SimulationRenderComponentWeather createWeatherEffect(final War3ID weatherId, final float minX,
			final float minY, final float maxX, final float maxY) {
		return MapViewerEffects.createWeatherEffect(this, weatherId, minX, minY, maxX, maxY);
	}

	public CStackedSounds getStackedSounds() {
		return this.stackedSounds;
	}

	public List<RenderWeatherEffect> getWeatherEffects() {
		return this.weatherEffects;
	}

	public com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUbersplat createUbersplat(
			final SimulationRenderComponentUbersplat.State state) {
		return MapViewerEffects.createUbersplat(this, state);
	}

	public List<com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUbersplat> getScriptUbersplats() {
		return this.scriptUbersplats;
	}

	public void updateImage(final CImage image) {
		MapViewerEffects.updateImage(this, image);
	}

	public void showTrackable(final CTrackable trackable) {
		MapViewerSelection.showTrackable(this, trackable);
	}

	public CTrackable rayPickTrackable(final float x, final float y) {
		return MapViewerSelection.rayPickTrackable(this, x, y);
	}

	static Color parseColor(final Element selectionCircleData, final String field) {
		return new Color(selectionCircleData.getFieldFloatValue(field, 1) / 255f,
				selectionCircleData.getFieldFloatValue(field, 2) / 255f,
				selectionCircleData.getFieldFloatValue(field, 3) / 255f,
				selectionCircleData.getFieldFloatValue(field, 0) / 255f);
	}

	public GenericResource loadMapGeneric(final String path, final FetchDataTypeName dataType,
			final LoadGenericCallback callback) {
		if (this.mapMpq == null) {
			return this.loadGeneric(path, dataType, callback);
		} else {
			return this.loadGeneric(path, dataType, callback, this.dataSource);
		}
	}

	public static War3Map beginLoadingMap(final DataSource gameDataSource, final String mapFilePath)
			throws IOException {
		if (mapFilePath.startsWith("/") || !gameDataSource.has(mapFilePath)) {
			final File mapFile = new File(mapFilePath);
			if (mapFile.exists()) {
				return new War3Map(gameDataSource, mapFile);
			} else {
				throw new IllegalArgumentException("No such map file: " + mapFilePath);
			}
		}
		return new War3Map(gameDataSource, mapFilePath);
	}

	public DataTable loadWorldEditData(final War3Map map) {
		return MapViewerLoading.loadWorldEditData(this, map);
	}

	public WTS preloadWTS(final War3Map map) {
		return MapViewerLoading.preloadWTS(this, map);
	}

	@Override
	public void dispose() {
		endModelCinematic();
		closeReplaySession();
		if (this.simulation != null) {
			SimulationChecksum.gameClosed(this.simulation);
		}
		if (this.terrain != null) {
			this.terrain.dispose();
		}
		this.dynamicShadowManager.dispose();
		if (this.lightningTypeToModel != null) {
			for (final LightningEffectModel model : this.lightningTypeToModel.values()) {
				model.dispose();
			}
		}
		super.dispose();
		try {
			this.dataSource.close();
		}
		catch (final IOException e) {
			Log.error("Could not close the data of the finished map: " + e.getMessage());
		}
		CUnit.releaseStaticReferences();
		CWidgetEvent.releaseStaticReferences();
		CGlobalWidgetEvent.releaseStaticReferences();
		ArithmeticJassValueVisitor.INSTANCE.reset(null, null);
		ArithmeticLeftHandHandleJassValueVisitor.INSTANCE.reset(null, null);
		RecordingAbilityTargetCheckReceiver.getInstance().reset();
		ExternStringMsgTargetCheckReceiver.getInstance().reset();
		COrder.targetCheckReceiver.reset();
		ArithmeticLeftHandStructJassValueVisitor.INSTANCE.reset(null, null);
		ArithmeticLeftHandCodeJassValueVisitor.INSTANCE.reset(null, null);
		CUnitAttackMissileBounce.releaseStaticReferences();
		CUnitAttackMissileSplash.releaseStaticReferences();
		CommandCardPopulatingAbilityVisitor.INSTANCE.release();
		GetInstantTransformationBuffVisitor.getInstance().release();
		BuildOnBuildingIntersector.INSTANCE.reset(0, 0);
		TreeBlightingCallback.INSTANCE.reset(null);
		CWidgetAbilityTargetCheckReceiver.INSTANCE.reset();
		this.soundMixer.release();
		this.stackedSounds.stopAll();
	}

	public MapLoader createMapLoader(final War3Map war3Map, final War3MapW3i w3iFile, final int localPlayerIndex)
			throws IOException {
		this.localPlayerIndex = localPlayerIndex;
		this.mapMpq = war3Map;
		this.pathingTextures = new CPathingTextures(war3Map);
		this.lastLoadedMapInformation = w3iFile;

		return new MapLoader(war3Map, w3iFile, localPlayerIndex);
	}

	public SimulationRenderComponentLightning createLightning(final War3ID lightningId,
			final RenderUnit renderPeerSource, final RenderWidget renderPeerTarget) {
		return MapViewerEffects.createLightning(this, lightningId, renderPeerSource, renderPeerTarget);
	}

	public SimulationRenderComponentLightning createLightning(final War3ID lightningId,
			final RenderUnit renderPeerSource, final RenderWidget renderPeerTarget, final Float duration) {
		return MapViewerEffects.createLightning(this, lightningId, renderPeerSource, renderPeerTarget, duration);
	}

	public SimulationRenderComponentLightningMovable createLightning(final War3ID lightningId, final float x1,
			final float y1, final float z1, final float x2, final float y2, final float z2) {
		return MapViewerEffects.createLightning(this, lightningId, x1, y1, z1, x2, y2, z2);
	}

	public SimulationRenderComponentLightningMovable createLightning(final War3ID lightningId, final float x1,
			final float y1, final float z1, final float x2, final float y2, final float z2,
			final boolean checkVisibility) {
		return MapViewerEffects.createLightning(this, lightningId, x1, y1, z1, x2, y2, z2, checkVisibility);
	}

	RenderLightningEffect addLightning(final RenderLightningEffect lightning) {
		this.lightnings.removeIf(RenderLightningEffect::isRemoved);
		this.lightnings.add(lightning);
		return lightning;
	}

	public List<RenderLightningEffect> getLightnings() {
		return this.lightnings;
	}

	public void spawnFxOnOrigin(final RenderUnit renderUnit, final String heroLevelUpArt) {
		MapViewerEffects.spawnFxOnOrigin(this, renderUnit, heroLevelUpArt);
	}

	protected ArgbImage getDestructablePathingPixelMap(final GameObject row) {
		return loadPathingTexture(row.getFieldAsString(DESTRUCTABLE_PATHING, 0));
	}

	protected ArgbImage getDestructablePathingDeathPixelMap(final GameObject row) {
		return loadPathingTexture(row.getFieldAsString(DESTRUCTABLE_PATHING_DEATH, 0));
	}

	public void preloadSoundsAtStart(final Collection<String> interfaceSoundKeys) {
		MapViewerLoading.preloadSoundsAtStart(this, interfaceSoundKeys);
	}

	/**
	 * Loads the map information that should be loaded after UI, such as units, who
	 * need to be able to setup their UI counterparts (icons, etc) for their
	 * abilities while loading. This allows the dynamic creation of units while the
	 * game is playing to better share code with the startup sequence's creation of
	 * units.
	 *
	 * @throws IOException
	 */
	public void loadAfterUI() throws IOException {
		if (this.unitsAndItemsLoaded) {
			MapViewerUnits.loadUnitsAndItems(this, this.allObjectData, this.lastLoadedMapInformation);
		} else {
			throw new IllegalStateException("transcription of JS has not loaded a map and has no JS async promises");
		}

		// After we finish loading units, we need to update & create the stored shadow
		// information for all unit shadows
		this.terrain.initShadows();
		this.terrain.setFogOfWarData(this.simulation, this.simulation.getPlayer(this.localPlayerIndex).getFogOfWar());
		this.worldScene.fogFilter = this.viewerFogFilter;
		if (this.mapConfig.getPlayer(this.localPlayerIndex).getSlotState() != CPlayerSlotState.PLAYING) {
			// No one plays the local slot when only computers play: the game is watched as an observer.
			startObserverView();
		}
		final CTimer fogUpdateTimer = new IdleFogTimer();
		fogUpdateTimer.setTimeoutTime(1.0f);
		fogUpdateTimer.setRepeats(true);
		fogUpdateTimer.start(this.simulation);
		final CTimer fogGpuUpdateTimer = new FogGpuUpdateTimer(this);
		fogGpuUpdateTimer.setTimeoutTime(0.03f);
		fogGpuUpdateTimer.setRepeats(true);
		fogGpuUpdateTimer.start(this.simulation);
	}

	private static final class IdleFogTimer extends CTimer {
		@Override
		public void onFire(final CSimulation simulation) {
		}
	}

	private static final class FogGpuUpdateTimer extends CTimer {
		@SavedFormerly("this$0")
		private final War3MapViewer viewer;

		private FogGpuUpdateTimer(final War3MapViewer viewer) {
			this.viewer = viewer;
		}

		@Override
		public void onFire(final CSimulation simulation) {
			final boolean fogChanged = this.viewer.terrain.reloadFogOfWarDataToGPU(simulation);
			this.viewer.terrain.updateShownBlight();
			// Doodads fade with the ground fog, so they only need the step while it changes; once a second they are
			// checked anyway.
			if (fogChanged || ((++this.viewer.fogDoodadTicks % 30) == 0)) {
				for (final RenderDoodad doodad : this.viewer.decals) {
					doodad.updateFog(this.viewer);
				}
			}
		}
	}

	float snapRunTimeDestructableX(final GameObject row, final float x, final float facingDegrees) {
		return MapViewerDoodads.snapRunTimeDestructableX(this, row, x, facingDegrees);
	}

	float snapRunTimeDestructableY(final GameObject row, final float y, final float facingDegrees) {
		return MapViewerDoodads.snapRunTimeDestructableY(this, row, y, facingDegrees);
	}

	RenderDestructable createNewDestructable(final War3ID doodadId, final GameObject row,
			final int doodadVariation, final float[] location, final float requestedFacingRadians,
			final short lifePercent, final float[] scale) {
		return MapViewerDoodads.createNewDestructable(this, doodadId, row, doodadVariation, location, requestedFacingRadians, lifePercent, scale);
	}

	CWidget createNewUnit(final Warcraft3MapRuntimeObjectData modifications, final War3ID unitId, float unitX,
			float unitY, final int playerIndex, int customTeamColor, final float unitAngle) {
		return MapViewerUnits.createNewUnit(this, modifications, unitId, unitX, unitY, playerIndex, customTeamColor, unitAngle);
	}

	CWidget createNewUnit(final Warcraft3MapRuntimeObjectData modifications, final War3ID unitId, float unitX,
			float unitY, final int playerIndex, int customTeamColor, final float unitAngle,
			int editorConfigHitPointPercent, int editorConfigManaAmount) {
		return MapViewerUnits.createNewUnit(this, modifications, unitId, unitX, unitY, playerIndex, customTeamColor, unitAngle, editorConfigHitPointPercent, editorConfigManaAmount);
	}

	SplatMover createUnitShadow(final RenderShadowType renderShadowType, final float unitX, final float unitY) {
		return MapViewerUnits.createUnitShadow(this, renderShadowType, unitX, unitY);
	}

	public SplatMover addUberSplatIngame(final float unitX, final float unitY, final String texturePath,
			final float s) {
		return MapViewerUnits.addUberSplatIngame(this, unitX, unitY, texturePath, s);
	}

	@Override
	public ArgbImage loadPathingTexture(final String pathingTexture) {
		return this.pathingTextures.get(pathingTexture);
	}

	public RenderUnitType getUnitTypeData(final War3ID key) {
		return this.renderUnitTypeData.get(key);
	}

	/**
	 * The game stops while paused (a script, the Pause button, or the game menu in a single player game); the camera,
	 * the interface and rendering go on.
	 */
	public void setGamePaused(final PauseSource source, final boolean paused) {
		if (paused) {
			this.pauseSources.add(source);
		}
		else {
			this.pauseSources.remove(source);
		}
	}

	public boolean isGamePaused() {
		return !this.pauseSources.isEmpty();
	}

	public boolean isGamePaused(final PauseSource source) {
		return this.pauseSources.contains(source);
	}

	public void requestSaveGame(final FileHandle file) {
		MapViewerSaves.requestSaveGame(this, file);
	}

	public void requestLoadGame(final FileHandle file) {
		MapViewerSaves.requestLoadGame(this, file);
	}

	public void requestLoadGame(final FileHandle file, final boolean byScript) {
		MapViewerSaves.requestLoadGame(this, file, byScript);
	}

	public void runAfterPendingSave(final Runnable action) {
		MapViewerSaves.runAfterPendingSave(this, action);
	}

	public void setLoadGameListener(final LoadGameListener loadGameListener) {
		this.loadGameListener = loadGameListener;
	}

	public void setSaveGameListener(final SaveGameListener saveGameListener) {
		this.saveGameListener = saveGameListener;
	}

	public interface SaveGameListener {
		void saveFinished(FileHandle file, boolean saved);
	}

	public interface LoadGameListener {
		void loadRequested(FileHandle file, boolean byScript);
	}

	public SavedInterfaceState captureInterfaceState() {
		return MapViewerSaves.captureInterfaceState(this);
	}

	public void restoreInterfaceState(final SavedInterfaceState state) {
		MapViewerSaves.restoreInterfaceState(this, state);
	}

	public void setInterfaceStateKeeper(final InterfaceStateKeeper keeper) {
		this.interfaceStateKeeper = keeper;
	}

	public void addLoadedScript(final SavedGame.AiEntry script) {
		this.loadedScripts.add(script);
	}

	public List<SavedGame.AiEntry> getLoadedScripts() {
		return this.loadedScripts;
	}

	public void setScriptEnvironment(final Object scriptEnvironment, final AudioContext uiAudioContext) {
		this.scriptEnvironment = scriptEnvironment;
		this.uiAudioContext = uiAudioContext;
	}

	public Object getScriptEnvironment() {
		return this.scriptEnvironment;
	}

	public AudioContext getUiAudioContext() {
		return this.uiAudioContext;
	}

	public void rememberFileSound(final Sound sound, final String fileName) {
		if (sound != null) {
			this.fileSounds.put(sound, fileName);
		}
	}

	public String getFileSoundName(final Sound sound) {
		return this.fileSounds.get(sound);
	}

	public interface InterfaceStateKeeper {
		SavedInterfaceState captureInterfaceState();

		void restoreInterfaceState(SavedInterfaceState state);
	}

	/** How a destructable the game made after the map loaded was made; saved games start again from the map. */
	public static final class CreatedDestructable {
		public final int savedHandleId;
		final int typeId;
		final float x;
		final float y;
		final float z;
		final float facing;
		final float scale;
		final int variation;

		CreatedDestructable(final int savedHandleId, final int typeId, final float x, final float y, final float z,
				final float facing, final float scale, final int variation) {
			this.savedHandleId = savedHandleId;
			this.typeId = typeId;
			this.x = x;
			this.y = y;
			this.z = z;
			this.facing = facing;
			this.scale = scale;
			this.variation = variation;
		}

		public void write(final DataOutputStream out) throws IOException {
			out.writeInt(this.savedHandleId);
			out.writeInt(this.typeId);
			out.writeFloat(this.x);
			out.writeFloat(this.y);
			out.writeFloat(this.z);
			out.writeFloat(this.facing);
			out.writeFloat(this.scale);
			out.writeInt(this.variation);
		}

		public static CreatedDestructable read(final DataInputStream in) throws IOException {
			return new CreatedDestructable(in.readInt(), in.readInt(), in.readFloat(), in.readFloat(), in.readFloat(),
					in.readFloat(), in.readFloat(), in.readInt());
		}
	}

	public List<CreatedDestructable> getCreatedDestructables() {
		// A removed destructable stays in the game's list and a script variable can still hold it, so a save names
		// it too: loading has to make it again, unless its id was given to a destructable made later.
		final Set<Integer> removedIds = new HashSet<>();
		for (final CDestructable destructable : this.simulation.getDestructables()) {
			if (destructable.isRemovedFromGame()) {
				removedIds.add(destructable.getHandleId());
			}
		}
		final Set<Integer> liveIds = new HashSet<>();
		for (final CreatedDestructable created : this.createdDestructables) {
			if (this.simulation.getDestructable(created.savedHandleId) != null) {
				liveIds.add(created.savedHandleId);
			}
		}
		final List<CreatedDestructable> kept = new ArrayList<>();
		final Set<Integer> removedKept = new HashSet<>();
		for (final CreatedDestructable created : this.createdDestructables) {
			final int id = created.savedHandleId;
			if ((this.simulation.getDestructable(id) != null)
					|| (removedIds.contains(id) && !liveIds.contains(id) && removedKept.add(id))) {
				kept.add(created);
			}
		}
		return kept;
	}

	public CDestructable createDestructableForSavedGame(final CreatedDestructable created) {
		return MapViewerDoodads.createDestructableForSavedGame(this, created);
	}

	public void clearRenderPeersForSavedGame() {
		MapViewerUnits.clearRenderPeersForSavedGame(this);
	}

	public void finishLoadingSavedGame(final Collection<CDestructable> preparedDestructables) {
		MapViewerUnits.finishLoadingSavedGame(this, preparedDestructables);
	}

	public void setMapFileName(final String mapFileName) {
		this.mapFileName = mapFileName;
	}

	/** The next level to load is this map again, with the computer players it has now. */
	public void setRestartAsNextLevel() {
		final StringBuilder computerSlots = new StringBuilder();
		for (int i = 0; i < this.gameData.getMaxPlayers(); i++) {
			final CPlayer player = this.simulation.getPlayer(i);
			if ((player.getController() == CMapControl.COMPUTER)
					&& (player.getSlotState() == CPlayerSlotState.PLAYING)) {
				computerSlots.append(computerSlots.length() == 0 ? "" : ",").append(i);
			}
		}
		getGameExit().setNextLevel(getMapFileName(), computerSlots.length() == 0 ? null : computerSlots.toString());
	}

	public String getMapFileName() {
		return this.mapFileName;
	}

	/**
	 * The campaign the game is a mission of, which its saves keep so that a loaded save goes on as that campaign's
	 * mission.
	 *
	 * @param campaign     by the number scripts know it by, -1 for none
	 * @param reforgedMaps whether the campaign is played in its Reforged version
	 */
	public void setCampaign(final int campaign, final boolean reforgedMaps) {
		this.campaign = campaign;
		this.reforgedCampaignMaps = reforgedMaps;
	}

	public int getCampaign() {
		return this.campaign;
	}

	public boolean isReforgedCampaignMaps() {
		return this.reforgedCampaignMaps;
	}

	/**
	 * @param savedGame the save put in place of the map's start, or null to start the map
	 * @param byScript  whether the map's own LoadGame asked for it (zone travel), not the player
	 */
	public void setSavedGameToLoad(final FileHandle savedGame, final boolean byScript) {
		this.savedGameToLoad = savedGame;
		this.savedGameLoadedByScript = byScript;
	}

	public FileHandle getSavedGameToLoad() {
		return this.savedGameToLoad;
	}

	public boolean isSavedGameLoadedByScript() {
		return this.savedGameLoadedByScript;
	}

	private static int chooseGameSeed() {
		final Integer fixed = Integer.getInteger("warsmash.seed");
		final int seed = fixed != null ? fixed : ThreadLocalRandom.current().nextInt();
		Log.warn("Game seed: " + seed);
		return seed;
	}

	public int getGameSeed() {
		return this.gameSeed;
	}

	/** The simulation's random numbers come back with the saved game; only the seed it started from is set here. */
	public void setGameSeedForSavedGame(final int gameSeed) {
		this.gameSeed = gameSeed;
	}

	/** A replay's game starts from the recorded seed; set before the map is loaded. */
	public void setGameSeed(final int gameSeed) {
		this.gameSeed = gameSeed;
		this.seededRandom.setSeed(gameSeed);
	}

	public void setReplaySession(final ReplaySession replaySession) {
		closeReplaySession();
		this.replaySession = replaySession;
		if (this.replayNotSaved && (replaySession instanceof ReplayRecording)) {
			((ReplayRecording) replaySession).doNotSave();
		}
	}

	/** DoNotSaveReplay, also when the map's script calls it before the recording starts. */
	public void doNotSaveReplay() {
		this.replayNotSaved = true;
		if (this.replaySession instanceof ReplayRecording) {
			((ReplayRecording) this.replaySession).doNotSave();
		}
	}

	public ReplaySession getReplaySession() {
		return this.replaySession;
	}

	/** See {@link ReplaySession#localValue(float)}. */
	public float replayedLocalValue(final float value) {
		return this.replaySession == null ? value : this.replaySession.localValue(value);
	}

	/** A replay plays the game again; what the game wrote for the player (its saves) it does not write again. */
	public boolean isReplayPlayback() {
		return this.replaySession instanceof ReplayPlayback;
	}

	/**
	 * EndGame or a quit: the game makes no more steps, also none left in the frame it ended in, and its replay ends
	 * at this step.
	 */
	public void gameEnded() {
		if (this.gameEnded) {
			return;
		}
		this.gameEnded = true;
		this.updateTime = 0;
		SimulationChecksum.gameEnded(this.simulation);
		if (this.replaySession != null) {
			this.replaySession.gameEnded();
		}
	}

	public boolean isGameEnded() {
		return this.gameEnded;
	}

	/** What the interface shows when a game ends with its score screen. */
	public void setGameEndScreen(final Runnable gameEndScreen) {
		this.gameEndScreen = gameEndScreen;
	}

	/** Ends the game on its score screen, as a replay that ends without the game's own EndGame does. */
	public void showGameEnd() {
		if (this.gameEndScreen != null) {
			this.gameEndScreen.run();
		}
		else {
			gameEnded();
		}
	}

	private void closeReplaySession() {
		if (this.replaySession != null) {
			final ReplaySession session = this.replaySession;
			this.replaySession = null;
			session.close();
		}
	}

	public static enum PauseSource {
		SCRIPT,
		PLAYER,
		MENU,
		/** The app is not on the screen (Android). */
		BACKGROUND,
		/** A replay's own Pause; the replay's game knows nothing of it. */
		REPLAY;
	}

	@Override
	public void update() {
		PerfStats.frameStart();
		if (this.anyReady) {
			this.successfulFrames++;
			// while paused everything is still placed and drawn, but no game or animation time passes
			final boolean paused = isGamePaused();
			final float deltaTime = paused ? 0 : Gdx.graphics.getDeltaTime() * this.replaySpeed;
			MapViewerVision.updateReplayVision(this);
			this.terrain.update(deltaTime);
			PerfStats.end(PerfStats.Section.TERRAIN_UPDATE);

			final float rawDeltaTime = paused ? 0 : Gdx.graphics.getRawDeltaTime() * this.replaySpeed;
			super.update(rawDeltaTime);
			PerfStats.end(PerfStats.Section.SCENE_UPDATE);

			this.updateTime += rawDeltaTime * this.timeScale;

			final Iterator<TextTag> textTagIterator = this.textTags.iterator();
			while (textTagIterator.hasNext()) {
				if (textTagIterator.next().update(deltaTime)) {
					textTagIterator.remove();
				}
			}
			for (final RenderWidget unit : this.widgets) {
				unit.updateAnimations(this);
			}
			for (int i = this.fogGhosts.size() - 1; i >= 0; i--) {
				if (this.fogGhosts.get(i).update(this)) {
					this.fogGhosts.remove(i);
				}
			}
			for (int i = 0, n = this.weatherEffects.size(); i < n; i++) {
				this.weatherEffects.get(i).update(this);
			}
			MapViewerUpdate.updateStackedSounds(this);
			MapViewerUpdate.updateUnitIndicators(this, deltaTime);
			this.scriptUbersplats.removeIf(splat -> !splat.update());
			for (int i = 0, n = this.trackableModels.size(); i < n; i++) {
				this.trackableModels.get(i).updateAnimations(this, deltaTime);
			}
			final Iterator<RenderEffect> projectileIterator = this.projectiles.iterator();
			while (projectileIterator.hasNext()) {
				final RenderEffect projectile = projectileIterator.next();
				if (projectile.updateAnimations(this, deltaTime)) {
					projectileIterator.remove();
				}
			}
			for (final RenderDoodad item : this.doodads) {
				final ModelInstance instance = item.instance;
				if (instance instanceof MdxComplexInstance) {
					final MdxComplexInstance mdxComplexInstance = (MdxComplexInstance) instance;
					if ((mdxComplexInstance.sequence == -1) || (!item.holdsScriptedEnd() && mdxComplexInstance.sequenceEnded
							&& ((item.getAnimation() != AnimationTokens.PrimaryTag.DEATH)
									|| (((MdxModel) mdxComplexInstance.model).sequences.get(mdxComplexInstance.sequence)
											.getFlags() == 0)))) {
						SequenceUtils.randomSequence(mdxComplexInstance, item.getAnimation(), SequenceUtils.EMPTY,
								true);

					}
				}
			}

			PerfStats.end(PerfStats.Section.WIDGETS);
			if (this.worldHidden || this.gameEnded) {
				// The game is over on the score screen; its scores must not change behind it.
				this.updateTime = 0;
			}
			int stepsThisFrame = 0;
			while ((this.updateTime >= WarsmashConstants.SIMULATION_STEP_TIME) && !this.gameEnded) {
				if (stepsThisFrame >= (MAX_STEPS_PER_FRAME * this.timeScale * this.replaySpeed)) {
					// A slow device would otherwise spend the next frame catching up and fall further behind each
					// time; like the original, the game runs slower instead.
					this.updateTime = 0;
					break;
				}
				stepsThisFrame++;
				if (this.gameTurnManager.getLatestCompletedTurn() >= this.simulation.getGameTurnTick()) {
					this.updateTime -= WarsmashConstants.SIMULATION_STEP_TIME;
					this.simulation.update();
					PerfStats.simulationStep();
					this.gameTurnManager.turnCompleted(this.simulation.getGameTurnTick());
					MapViewerSaves.runPendingSaveRequests(this);
				} else {
					if (this.updateTime > (WarsmashConstants.SIMULATION_STEP_TIME * 3)) {
						this.gameTurnManager.framesSkipped(this.updateTime / WarsmashConstants.SIMULATION_STEP_TIME);
						this.updateTime = 0;
					}
					break;
				}
			}
			PerfStats.end(PerfStats.Section.SIMULATION);
			if (paused) {
				MapViewerSaves.runPendingSaveRequests(this);
			}
			if (this.dncTerrain != null) {
				this.dncTerrain.setFrameByRatio(
						this.simulation.getGameTimeOfDay() / this.simulation.getGameplayConstants().getGameDayHours());
				this.dncTerrain.update(rawDeltaTime, null);
			}
			if (this.dncUnit != null) {
				this.dncUnit.setFrameByRatio(
						this.simulation.getGameTimeOfDay() / this.simulation.getGameplayConstants().getGameDayHours());
				this.dncUnit.update(rawDeltaTime, null);
			}

			if (this.dncTarget != null) {
				this.dncTarget.setFrameByRatio(
						this.simulation.getGameTimeOfDay() / this.simulation.getGameplayConstants().getGameDayHours());
				this.dncTarget.update(rawDeltaTime, null);
			}
		}
	}

	/**
	 * With warsmash.norender only the game runs: no model, terrain or effect is updated or drawn, the way a replay
	 * skips ahead. The game must come out the same as when it is drawn.
	 */
	@Override
	public void updateAndRender() {
		if ((this.fastForwardTarget >= 0) && this.anyReady && !isGamePaused()) {
			MapViewerUpdate.fastForwardSteps(this);
		}
		else if (NO_RENDER) {
			if (this.anyReady) {
				MapViewerUpdate.stepSimulationWithoutRender(this);
			}
		}
		else {
			super.updateAndRender();
		}
		// The map's script makes its starting units in the first game step.
		if ((this.soundsToPreload != null) && (this.simulation.getGameTurnTick() > 0)) {
			final Collection<String> interfaceSoundKeys = this.soundsToPreload;
			this.soundsToPreload = null;
			MapViewerLoading.preloadSounds(this, interfaceSoundKeys);
		}
	}

	public void fastForwardTo(final int tick, final FastForwardListener whenReached) {
		MapViewerUpdate.fastForwardTo(this, tick, whenReached);
	}

	public static interface FastForwardListener {
		/** @param nanos the time the steps took, frames between them left out */
		void reached(int steps, long nanos);
	}

	public boolean isFastForwarding() {
		return this.fastForwardTarget >= 0;
	}

	static final long FAST_FORWARD_FRAME_NANOS = 40_000_000L;

	public void setWorldHidden(final boolean worldHidden) {
		this.worldHidden = worldHidden;
	}

	public boolean isWorldHidden() {
		return this.worldHidden;
	}

	/** PlayModelCinematic: a new one replaces the one still playing. */
	public void playModelCinematic(final String path) {
		endModelCinematic();
		this.modelCinematic = ModelCinematic.play(this, path);
	}

	/** @return whether a model cinematic was playing, now skipped */
	public boolean skipModelCinematic() {
		if (this.modelCinematic == null) {
			return false;
		}
		this.modelCinematic.skip();
		return true;
	}

	public boolean isModelCinematicPlaying() {
		return this.modelCinematic != null;
	}

	void endModelCinematic() {
		if (this.modelCinematic != null) {
			this.modelCinematic.dispose();
			this.modelCinematic = null;
		}
	}

	/**
	 * SetSkyModel. The sky draws behind everything and moves with the camera, so it has a scene of its own whose far
	 * plane the map's camera distance settings cannot cut through.
	 */
	public void setWaterBaseColor(final float[] color) {
		this.waterColor = color;
		this.terrain.setWaterBaseColor(color[0], color[1], color[2], color[3]);
	}

	public void setSkyModel(final String path) {
		this.skyModel = path;
		if (this.skyInstance != null) {
			this.skyInstance.detach();
			this.skyInstance = null;
		}
		if ((path == null) || path.isEmpty()) {
			return;
		}
		if (this.skyScene == null) {
			this.skyScene = addSimpleScene();
			this.skyScene.alpha = true;
		}
		final MdxModel model = MapViewerEffects.loadModelMdx(this.dataSource, this, path, this.mapPathSolver, this.solverParams);
		this.skyInstance = (MdxComplexInstance) model.addInstance();
		this.skyInstance.setSequenceLoopMode(SequenceLoopMode.ALWAYS_LOOP);
		this.skyInstance.setSequence(0);
		this.skyInstance.setScene(this.skyScene);
	}

	public void addIndicator(final CWidget widget, final int red, final int green, final int blue, final int alpha) {
		MapViewerUpdate.addIndicator(this, widget, red, green, blue, alpha);
	}

	static final class UnitIndicator {
		RenderUnit unit;
		float[] color;
		float strength;
		float elapsed;
	}

	public void setDoodadAnimation(final String doodadId, final Rectangle area, final float centerX,
			final float centerY, final float radius, final boolean nearestOnly, final String animation) {
		MapViewerDoodads.setDoodadAnimation(this, doodadId, area, centerX, centerY, radius, nearestOnly, animation);
	}

	@Override
	public void render() {
		MapViewerUpdate.render(this);
	}

	public void deselect() {
		MapViewerSelection.deselect(this);
	}

	public void doUnselectUnit(final RenderWidget widget) {
		MapViewerSelection.doUnselectUnit(this, widget);
	}

	public void doSelectUnit(final List<RenderWidget> units) {
		MapViewerSelection.doSelectUnit(this, units);
	}

	public void clearUnitMouseOverHighlight(final RenderWidget unit) {
		MapViewerSelection.clearUnitMouseOverHighlight(this, unit);
	}

	public void clearUnitMouseOverHighlight() {
		MapViewerSelection.clearUnitMouseOverHighlight(this);
	}

	public void showUnitMouseOverHighlight(final RenderWidget unit) {
		MapViewerSelection.showUnitMouseOverHighlight(this, unit);
	}

	public void getClickLocation(final Vector3 out, final int screenX, final int screenY,
			final boolean intersectWithWater, final boolean pushResultZAboveWater) {
		MapViewerSelection.getClickLocation(this, out, screenX, screenY, intersectWithWater, pushResultZAboveWater);
	}

	public void getClickLocationOnZPlane(final Vector3 out, final int screenX, final int screenY, final float worldZ) {
		MapViewerSelection.getClickLocationOnZPlane(this, out, screenX, screenY, worldZ);
	}

	public void showConfirmation(final Vector3 position, final float red, final float green, final float blue) {
		MapViewerSelection.showConfirmation(this, position, red, green, blue);
	}

	public RenderWidget rayPickUnit(final float x, final float y) {
		return MapViewerSelection.rayPickUnit(this, x, y);
	}

	public RenderWidget rayPickUnit(final float x, final float y, final CWidgetFilterFunction filter) {
		return MapViewerSelection.rayPickUnit(this, x, y, filter);
	}

	public RenderWidget pickNearestTree(final float x, final float y, final float tolerancePx,
			final CWidgetFilterFunction filter) {
		return MapViewerSelection.pickNearestTree(this, x, y, tolerancePx, filter);
	}

	private static final class MappedDataCallbackImplementation implements LoadGenericCallback {
		@Override
		public Object call(final InputStream data) {
			final StringBuilder stringBuilder = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(data, "utf-8"))) {
				String line;
				while ((line = reader.readLine()) != null) {
					stringBuilder.append(line);
					stringBuilder.append("\n");
				}
			} catch (final UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
			return new MappedData(stringBuilder.toString());
		}
	}

	private static final class StringDataCallbackImplementation implements LoadGenericCallback {
		@Override
		public Object call(final InputStream data) {
			if (data == null) {
				Log.warn("data null");
			}
			final StringBuilder stringBuilder = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(data, "utf-8"))) {
				String line;
				while ((line = reader.readLine()) != null) {
					stringBuilder.append(line);
					stringBuilder.append("\n");
				}
			} catch (final UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
			return stringBuilder.toString();
		}
	}

	private static final class StreamDataCallbackImplementation implements LoadGenericCallback {
		@Override
		public Object call(final InputStream data) {
			return data;
		}
	}

	public static final class SolverParams {
		public char tileset;
		public boolean reforged;
		public boolean hd;
	}

	public static final class CliffInfo {
		public List<float[]> locations = new ArrayList<>();
		public List<Integer> textures = new ArrayList<>();
	}

	float selectionCircleScaleFactor;
	DataTable worldEditData;
	private WorldEditStrings worldEditStrings;
	Warcraft3MapRuntimeObjectData allObjectData;
	AbilityDataUI abilityDataUI;
	public int imageWalkableZOffset;
	WTS preloadedWTS;

	Color selectionCircleColorFriend;

	Color selectionCircleColorNeutral;

	Color selectionCircleColorEnemy;

	private int localPlayerServerSlot;

	int[] blightChangedCorners = new int[512];
	float[] blightSources = new float[48];
	int blightSourceCount;

	RenderUnitTypeData renderUnitTypeData;
	RenderItemTypeData renderItemTypeData;

	public void standOnRepeat(final MdxComplexInstance instance) {
		instance.setSequenceLoopMode(SequenceLoopMode.ALWAYS_LOOP);
		SequenceUtils.randomStandSequence(instance);
	}

	static final class SelectionCircleSize {
		final float size;
		final String texture;
		private final String textureDotted;

		public SelectionCircleSize(final float size, final String texture, final String textureDotted) {
			this.size = size;
			this.texture = texture;
			this.textureDotted = textureDotted;
		}
	}

	// A script that never calls SetDayNightModels still gets the tileset's lighting; a later call replaces it.
	private void applyTilesetDayNightModels(final char tileset) {
		if ((this.worldEditData == null) || (this.dncTerrain != null)) {
			return;
		}
		final Element terrainLights = this.worldEditData.get("TerrainLights");
		final Element unitLights = this.worldEditData.get("UnitLights");
		if ((terrainLights == null) || (unitLights == null)) {
			return;
		}
		final String key = String.valueOf(tileset);
		final String terrainModel = terrainLights.getField(key);
		final String unitModel = unitLights.getField(key);
		if ((terrainModel == null) || terrainModel.isEmpty() || (unitModel == null) || unitModel.isEmpty()) {
			return;
		}
		setDayNightModels(terrainModel, unitModel);
	}

	public void setDayNightModels(final String terrainDNCFile, final String unitDNCFile) {
		this.dayNightTerrainModel = terrainDNCFile;
		this.dayNightUnitModel = unitDNCFile;
		final MdxModel terrainDNCModel = loadModelMdx(terrainDNCFile);
		this.dncTerrain = (MdxComplexInstance) terrainDNCModel.addInstance();
		this.dncTerrain.setSequenceLoopMode(SequenceLoopMode.ALWAYS_LOOP);
		this.dncTerrain.setSequence(0);
		final MdxModel unitDNCModel = loadModelMdx(unitDNCFile);
		this.dncUnit = (MdxComplexInstance) unitDNCModel.addInstance();
		this.dncUnit.setSequenceLoopMode(SequenceLoopMode.ALWAYS_LOOP);
		this.dncUnit.setSequence(0);
		final MdxModel targetDNCModel = loadModelMdx(
				"Environment\\DNC\\DNCLordaeron\\DNCLordaeronTarget\\DNCLordaeronTarget.mdl");
		this.dncTarget = (MdxComplexInstance) targetDNCModel.addInstance();
		this.dncTarget.setSequenceLoopMode(SequenceLoopMode.ALWAYS_LOOP);
		this.dncTarget.setSequence(0);
	}

	public static String mdx(String mdxPath) {
		if (mdxPath.toLowerCase().endsWith(".mdl")) {
			mdxPath = mdxPath.substring(0, mdxPath.length() - 4);
		}
		if (!mdxPath.toLowerCase().endsWith(".mdx")) {
			mdxPath += ".mdx";
		}
		return mdxPath;
	}

	public static String mdl(String mdxPath) {
		if (mdxPath.toLowerCase().endsWith(".mdx")) {
			mdxPath = mdxPath.substring(0, mdxPath.length() - 4);
		}
		if (!mdxPath.toLowerCase().endsWith(".mdl")) {
			mdxPath += ".mdl";
		}
		return mdxPath;
	}

	public String blp(String iconPath) {
		final int lastDotIndex = iconPath.lastIndexOf('.');
		if (lastDotIndex != -1) {
			iconPath = iconPath.substring(0, lastDotIndex);
		}
		if (!iconPath.toLowerCase().endsWith(".blp")) {
			iconPath += ".blp";
		}
		return iconPath;
	}

	@Override
	public SceneLightManager createLightManager(final boolean simple) {
		if (simple) {
			return new W3xScenePortraitLightManager(this, this.lightDirection);
		} else {
			return new W3xSceneWorldLightManager(this);
		}
	}

	@Override
	public WorldEditStrings getWorldEditStrings() {
		return this.worldEditStrings;
	}

	public void setGameUI(final GameUI gameUI) {
		this.gameUI = gameUI;
		this.abilityDataUI = new AbilityDataUI(this.allObjectData, gameUI, this);
	}

	public GameDataConstants getGameData() {
		return this.gameData;
	}

	public GameUI getGameUI() {
		return this.gameUI;
	}

	public AbilityDataUI getAbilityDataUI() {
		return this.abilityDataUI;
	}

	public KeyedSounds getUiSounds() {
		return this.uiSounds;
	}

	/** Scripts name labels of unit response, combat and animation sounds as well as UI and ambience ones. */
	public Element getSoundLabel(final String label) {
		if ((label == null) || label.isEmpty()) {
			return null;
		}
		for (final DataTable table : new DataTable[] { this.uiSoundsTable, this.unitAckSoundsTable,
				this.unitCombatSoundsTable, this.animSoundsTable }) {
			final Element row = table == null ? null : table.get(label);
			if (row != null) {
				return row;
			}
		}
		if (this.scriptSoundsTable == null) {
			// Campaign dialogue alone is thousands of rows that only scripts look up.
			this.scriptSoundsTable = new DataTable(this.worldEditStrings);
			for (final String name : new String[] { "DialogSounds", "MIDISounds" }) {
				try (InputStream stream = this.dataSource.getResourceAsStream("UI\\SoundInfo\\" + name + ".slk")) {
					if (stream != null) {
						this.scriptSoundsTable.readSLK(stream);
					}
				}
				catch (final IOException e) {
					Log.error(e);
				}
			}
		}
		return this.scriptSoundsTable.get(label);
	}

	public Warcraft3MapRuntimeObjectData getAllObjectData() {
		return this.allObjectData;
	}

	public float getWalkableRenderHeight(final float x, final float y) {
		this.walkableObjectsTree.intersect(x, y, this.walkablesIntersector.reset(x, y));
		return this.walkablesIntersector.z;
	}

	public MdxComplexInstance getHighestWalkableUnder(final float x, final float y) {
		this.walkableObjectsTree.intersect(x, y, this.intersectorFindsHighestWalkable.reset(x, y));
		return this.intersectorFindsHighestWalkable.highestInstance;
	}

	public int getLocalPlayerIndex() {
		return this.localPlayerIndex;
	}

	public RenderFogGhost addFogGhost(final RenderUnit unit) {
		final RenderFogGhost ghost = new RenderFogGhost(this, unit);
		this.fogGhosts.add(ghost);
		return ghost;
	}

	public List<RenderFogGhost> getFogGhosts() {
		return this.fogGhosts;
	}

	public void addInitialCreep(final float x, final float y, final int level) {
		if (this.initialCreeps != null) {
			this.initialCreeps.add(new float[] { x, y, level });
		}
	}

	/** The creeps the map placed as the game began, once; empty for a loaded game. */
	public List<float[]> takeInitialCreeps() {
		final List<float[]> creeps = this.initialCreeps == null ? new ArrayList<>() : this.initialCreeps;
		this.initialCreeps = null;
		return creeps;
	}

	public com.etheller.warsmash.viewer5.handlers.w3x.ui.AllyColorFilter getAllyColorFilter() {
		if (this.allyColorFilter == null) {
			this.allyColorFilter = new com.etheller.warsmash.viewer5.handlers.w3x.ui.AllyColorFilter(this.miscData);
			this.allyColorFilter.setState(com.etheller.warsmash.viewer5.handlers.w3x.ui.GameOptions.get().getAllyFilter());
		}
		return this.allyColorFilter;
	}

	/** DisableRestartMission: the game menu's Restart button stays greyed out. */
	public float getTimeScale() {
		return this.timeScale;
	}

	/** Steps per frame follow it; the steps themselves stay the same length. */
	public void setTimeScale(final float timeScale) {
		this.timeScale = timeScale;
	}

	public static void setDefaultTimeScale(final float timeScale) {
		defaultTimeScale = timeScale;
	}

	/** The game's VolumeGroupSetVolume scales. */
	public CSoundMixer getSoundMixer() {
		return this.soundMixer;
	}

	public CGameExit getGameExit() {
		return this.gameExit;
	}

	public boolean isRestartMissionDisabled() {
		return this.restartMissionDisabled;
	}

	public void setRestartMissionDisabled(final boolean restartMissionDisabled) {
		this.restartMissionDisabled = restartMissionDisabled;
	}

	public String getAltMinimapIcon() {
		return this.altMinimapIcon;
	}

	public void setAltMinimapIcon(final String altMinimapIcon) {
		this.altMinimapIcon = altMinimapIcon;
	}

	public RenderUnit getRenderPeer(final CUnit unit) {
		return this.unitToRenderPeer.get(unit);
	}

	public int getMissileTeamColor(final CUnit source) {
		final RenderUnit renderPeer = getRenderPeer(source);
		if (renderPeer != null) {
			return renderPeer.playerIndex;
		}
		// Missiles outlive their source: a bounce or a delayed launch can come after the unit was removed.
		return this.simulation.getPlayer(source.getPlayerIndex()).getColor();
	}

	public RenderDestructable getRenderPeer(final CDestructable dest) {
		return this.destructableToRenderPeer.get(dest);
	}

	public RenderItem getRenderPeer(final CItem item) {
		return this.itemToRenderPeer.get(item);
	}

	public RenderWidget getRenderPeer(final CWidget damagedDestructable) {
		RenderWidget damagedWidget = War3MapViewer.this.unitToRenderPeer.get(damagedDestructable);
		if (damagedWidget == null) {
			damagedWidget = War3MapViewer.this.destructableToRenderPeer.get(damagedDestructable);
		}
		if (damagedWidget == null) {
			damagedWidget = War3MapViewer.this.itemToRenderPeer.get(damagedDestructable);
		}
		return damagedWidget;
	}

	private static final class QuadtreeIntersectorFindsWalkableRenderHeight
			implements QuadtreeIntersector<MdxComplexInstance> {
		private float z;
		private final Ray ray = new Ray();
		private final Vector3 intersection = new Vector3();

		private QuadtreeIntersectorFindsWalkableRenderHeight reset(final float x, final float y) {
			this.z = -Float.MAX_VALUE;
			this.ray.set(x, y, 4096, 0, 0, -8192);
			return this;
		}

		@Override
		public boolean onIntersect(final MdxComplexInstance intersectingObject) {
			if (intersectingObject.intersectRayWithCollision(this.ray, this.intersection, true, true)) {
				this.z = Math.max(this.z, this.intersection.z);
			}
			return false;
		}
	}

	private static final class QuadtreeIntersectorFindsHighestWalkable
			implements QuadtreeIntersector<MdxComplexInstance> {
		private float z;
		private final Ray ray = new Ray();
		private final Vector3 intersection = new Vector3();
		private MdxComplexInstance highestInstance;

		private QuadtreeIntersectorFindsHighestWalkable reset(final float x, final float y) {
			this.z = -Float.MAX_VALUE;
			this.ray.set(x, y, 4096, 0, 0, -8192);
			this.highestInstance = null;
			return this;
		}

		@Override
		public boolean onIntersect(final MdxComplexInstance intersectingObject) {
			if (intersectingObject.intersectRayWithCollision(this.ray, this.intersection, true, true)) {
				if (this.intersection.z > this.z) {
					this.z = this.intersection.z;
					this.highestInstance = intersectingObject;
				}
			}
			return false;
		}
	}

	static final class QuadtreeIntersectorFindsHitPoint implements QuadtreeIntersector<MdxComplexInstance> {
		private Ray ray;
		final Vector3 intersection = new Vector3();
		boolean found;

		QuadtreeIntersectorFindsHitPoint reset(final Ray ray) {
			this.ray = ray;
			this.found = false;
			return this;
		}

		@Override
		public boolean onIntersect(final MdxComplexInstance intersectingObject) {
			if (intersectingObject.intersectRayWithCollision(this.ray, this.intersection, true, true)) {
				this.found = true;
				return true;
			}
			return false;
		}
	}

	public void add(final TextTag textTag) {
		this.textTags.add(textTag);
	}

	public SettableCommandErrorListener getCommandErrorListener() {
		return this.commandErrorListener;
	}

	public War3MapConfig getMapConfig() {
		return this.mapConfig;
	}

	public void setLocalPlayerServerSlot(final int localPlayerServerSlot) {
		this.localPlayerServerSlot = localPlayerServerSlot;
	}

	public boolean isNetworkGame() {
		return this.gameTurnManager instanceof com.etheller.warsmash.networking.WarsmashClient;
	}

	public void setGameTurnManager(final GameTurnManager gameTurnManager) {
		this.gameTurnManager = gameTurnManager;
	}

	public RenderSpellEffect spawnSpellEffectOnUnitEx(final CWidget unit, final War3ID alias,
			final CEffectType effectType, final int index) {
		return MapViewerEffects.spawnSpellEffectOnUnitEx(this, unit, alias, effectType, index);
	}

	public RenderSpellEffect spawnSpellEffectOnUnitEx(final CWidget unit, final War3ID alias,
			final CEffectType effectType, final int index, final String attachPointName) {
		return MapViewerEffects.spawnSpellEffectOnUnitEx(this, unit, alias, effectType, index, attachPointName);
	}

	public List<RenderSpellEffect> spawnSpellEffectOnUnitEx(final CUnit unit, final War3ID alias,
			final CEffectType effectType) {
		return MapViewerEffects.spawnSpellEffectOnUnitEx(this, unit, alias, effectType);
	}

	public RenderSpellEffect spawnSpellEffectEx(final float x, final float y, final float facing, final War3ID alias,
			final CEffectType effectType, final int index) {
		return MapViewerEffects.spawnSpellEffectEx(this, x, y, facing, alias, effectType, index);
	}

	public List<EffectAttachmentUI> getEffectAttachmentUIList(final War3ID alias, final CEffectType effectType) {
		return MapViewerEffects.getEffectAttachmentUIList(this, alias, effectType);
	}

	public EffectAttachmentUI getEffectAttachmentUI(final War3ID alias, final CEffectType effectType, final int index) {
		return MapViewerEffects.getEffectAttachmentUI(this, alias, effectType, index);
	}

	public List<War3ID> getLightningEffectList(final War3ID alias) {
		final AbilityUI abilityUI = War3MapViewer.this.abilityDataUI.getUI(alias);
		if (abilityUI == null) {
			// Mana Flare names its lightning on the buff.
			final BuffUI buffUI = War3MapViewer.this.abilityDataUI.getBuffUI(alias);
			return buffUI == null ? Collections.emptyList() : buffUI.getLightningEffects();
		}
		return abilityUI.getLightningEffects();
	}

	public RenderSpellEffect addSpecialEffectTarget(final String modelName, final CWidget targetWidget,
			final String attachPointName) {
		return MapViewerEffects.addSpecialEffectTarget(this, modelName, targetWidget, attachPointName);
	}

	public RenderSpellEffect addSpecialEffectTarget(final String modelName, final CWidget targetWidget,
			List<String> attachPointNames) {
		return MapViewerEffects.addSpecialEffectTarget(this, modelName, targetWidget, attachPointNames);
	}

	public RenderSpellEffect addSpecialEffect(final String modelName, final float x, final float y, final float yaw) {
		return MapViewerEffects.addSpecialEffect(this, modelName, x, y, yaw);
	}

	@Override
	public MdxModel loadModelMdx(final String path) {
		return MapViewerEffects.loadModelMdx(this, path);
	}

	public static MdxModel loadModelMdx(final DataSource dataSource, final ModelViewer modelViewer, final String path,
			final PathSolver pathSolver, final Object solverParams) {
		return MapViewerEffects.loadModelMdx(dataSource, modelViewer, path, pathSolver, solverParams);
	}

	public void setBlight(final float whichLocationX, final float whichLocationY, final float radius,
			final boolean blighted) {
		MapViewerBlight.setBlight(this, whichLocationX, whichLocationY, radius, blighted);
	}

	public void setBlight(final float whichLocationX, final float whichLocationY, final float radius,
			final boolean square, final boolean blighted) {
		MapViewerBlight.setBlight(this, whichLocationX, whichLocationY, radius, square, blighted);
	}

	public byte getFogShade(final byte fogState) {
		return MapViewerVision.getFogShade(this, fogState);
	}

	public boolean isMaskedAreasPartiallyVisible() {
		return MapViewerVision.isMaskedAreasPartiallyVisible(this);
	}

	/**
	 * A map made for Reforged graphics alone (the Reforged versions of the campaign missions): the game plays it in
	 * Reforged mode, with that mode's interface for its cinematics.
	 */
	public boolean isReforgedGraphicsMap() {
		return isReforgedGraphicsMap(this.lastLoadedMapInformation);
	}

	public static boolean isReforgedGraphicsMap(final War3MapW3i mapInformation) {
		return (mapInformation != null) && (mapInformation.getSupportedModes() == SUPPORTED_MODES_REFORGED);
	}

	/** Whether the game is played with Reforged graphics, whose interface has the Reforged cinematics. */
	public boolean isReforgedGraphics() {
		return isReforgedGraphicsMap() || (LayeredDataSource.graphicsOf(this.gameDataSource) != GraphicsSet.CLASSIC);
	}

	/** Whether the interface comes from a graphics package (Reforged or DE), not from the Classic files. */
	public boolean isPackageInterface() {
		return LayeredDataSource.graphicsOf(this.gameDataSource) != GraphicsSet.CLASSIC;
	}

	public CPlayerFogOfWar getFogOfWar() {
		if (this.observerFogOfWar != null) {
			return this.observerFogOfWar;
		}
		if (this.replayVisionPlayer != REPLAY_VISION_NONE) {
			if (!this.replayFogShown || (this.replayVisionPlayer == REPLAY_VISION_ALL_PLAYERS)) {
				return this.replayFogOfWar;
			}
			return this.simulation.getPlayer(this.replayVisionPlayer).getFogOfWar();
		}
		return this.simulation.getPlayer(this.localPlayerIndex).getFogOfWar();
	}

	public void setReplayVision(final int playerIndex, final boolean fogShown) {
		MapViewerVision.setReplayVision(this, playerIndex, fogShown);
	}

	/** The players Replay Vision offers: those who played, not the neutral ones. */
	public boolean isReplayVisionPlayer(final CPlayer player) {
		return (player != null) && (player.getId() < (this.gameData.getMaxPlayers() - 4))
				&& (player.getSlotState() == CPlayerSlotState.PLAYING);
	}

	public int getReplayVisionPlayer() {
		return this.replayVisionPlayer;
	}

	public boolean isReplayFogShown() {
		return this.replayFogShown;
	}

	/** See {@link MapViewerVision#isViewedPlayer}. */
	public boolean isViewedPlayer(final int playerIndex) {
		return MapViewerVision.isViewedPlayer(this, playerIndex);
	}

	public int getViewingPlayerIndex() {
		return MapViewerVision.getViewingPlayerIndex(this);
	}

	@Override
	public boolean enableAudio() {
		final boolean enabled = super.enableAudio();
		if (this.worldScene.audioContext != null) {
			this.worldScene.audioContext.visibility = this::isSoundHeardAt;
		}
		return enabled;
	}

	public boolean isSoundHeardAt(final float x, final float y) {
		return MapViewerVision.isSoundHeardAt(this, x, y);
	}

	final WorldScene.FogFilter viewerFogFilter = new WorldScene.FogFilter() {
		@Override
		public boolean isVisible(final float x, final float y) {
			return isPointShownToViewer(x, y);
		}

		@Override
		public boolean isExplored(final float x, final float y) {
			return (simulation == null) || (getFogOfWar().getFogState(simulation, simulation.getPathingGrid(), x,
					y) != CFogState.MASKED);
		}
	};

	public boolean isPointShownToViewer(final float x, final float y) {
		return MapViewerVision.isPointShownToViewer(this, x, y);
	}

	public boolean isShownToViewer(final CUnit unit) {
		return MapViewerVision.isShownToViewer(this, unit);
	}

	public float getReplaySpeed() {
		return this.replaySpeed;
	}

	public void setReplaySpeed(final float replaySpeed) {
		this.replaySpeed = replaySpeed;
	}

	public void startObserverView() {
		MapViewerVision.startObserverView(this);
	}

	public boolean isObserverView() {
		return this.observerFogOfWar != null;
	}

	public static byte fadeLineOfSightColor(final byte lastFogStateColor, final byte state) {
		return MapViewerVision.fadeLineOfSightColor(lastFogStateColor, state);
	}

	public final class MapLoader {
		private final LinkedList<LoadMapTask> loadMapTasks = new LinkedList<>();
		private int startingTaskCount;

		private char tileset;
		private DataSource tilesetSource;

		private War3MapW3e terrainData;

		private War3MapWpm terrainPathing;

		private MapLoader(final War3Map war3Map, final War3MapW3i w3iFile, final int localPlayerIndex) {
			final PathSolver wc3PathSolver = War3MapViewer.this.wc3PathSolver;

			this.loadMapTasks.add(() -> {
				this.tileset = 'A';
				this.tileset = w3iFile.getTileset();
				int gameDataSet = w3iFile.getGameDataSet();

				try {
					// Slightly complex. Here's the theory:
					// 1.) Copy map into RAM
					// 2.) Setup a Data Source that will read assets
					// from either the map or the game, giving the map priority.
					SeekableByteChannel sbc;
					final DataSource compoundDataSource = war3Map.getCompoundDataSource();
					final List<DataSource> dataSources = new ArrayList<>();
					dataSources.add(compoundDataSource);
					if (War3MapViewer.this.gameData.isFixFlatFilesTilesetLoading()) {
						dataSources.add(new SubdirDataSource(compoundDataSource, this.tileset + ".mpq/"));
					} else {
						try (InputStream mapStream = compoundDataSource.getResourceAsStream(this.tileset + ".mpq")) {
							if (mapStream == null) {
								dataSources.add(new SubdirDataSource(compoundDataSource, this.tileset + ".mpq/"));
								dataSources.add(new SubdirDataSource(compoundDataSource,
										"_tilesets/" + this.tileset + ".w3mod/"));
							} else {
								final byte[] mapData = IOUtils.toByteArray(mapStream);
								sbc = new SeekableInMemoryByteChannel(mapData);
								final DataSource internalMpqContentsDataSource = new MpqDataSource(new MPQArchive(sbc),
										sbc);
								dataSources.add(internalMpqContentsDataSource);
							}
						} catch (final IOException exc) {
							dataSources.add(new SubdirDataSource(compoundDataSource, this.tileset + ".mpq/"));
							dataSources.add(
									new SubdirDataSource(compoundDataSource, "_tilesets/" + this.tileset + ".w3mod/"));
						}
					}
					// Some tileset textures exist only in the graphics packages (the Stratholme bridge); first in the
					// list, so any Classic file wins over them.
					dataSources.add(0, new SubdirDataSource(compoundDataSource,
							"_hd.w3mod/_tilesets/" + this.tileset + ".w3mod/"));
					dataSources.add(1, new SubdirDataSource(compoundDataSource,
							"_de.w3mod/_tilesets/" + this.tileset + ".w3mod/"));
					if (gameDataSet <= 0) {
						gameDataSet = w3iFile.hasFlag(War3MapW3iFlags.MELEE_MAP) ? 2 : 1;
					}
					if (gameDataSet == 1) {
						// Custom data
						dataSources.add(new SubdirDataSource(compoundDataSource,
								"Custom_V" + War3MapViewer.this.gameData.getGameVersion() + "\\"));
					} else {
						// Melee data
						dataSources.add(new SubdirDataSource(compoundDataSource,
								"Melee_V" + War3MapViewer.this.gameData.getGameVersion() + "\\"));
					}
					this.tilesetSource = new CompoundDataSource(dataSources);
				} catch (final MPQException e) {
					throw new RuntimeException(e);
				}
			});

			this.loadMapTasks.add(() -> {
				setDataSource(this.tilesetSource);
				War3MapViewer.this.mapMpq.setDataSource(this.tilesetSource);
			});
			this.loadMapTasks.add(() -> {
				War3MapViewer.this.worldEditStrings = new WorldEditStrings(War3MapViewer.this.dataSource);
			});
			this.loadMapTasks.add(() -> {
				loadSLKs(War3MapViewer.this.worldEditStrings);
			});

			this.loadMapTasks.add(() -> {
				War3MapViewer.this.solverParams.tileset = Character.toLowerCase(this.tileset);
			});

			this.loadMapTasks.add(() -> {
				this.terrainData = War3MapViewer.this.mapMpq.readEnvironment();
			});

			this.loadMapTasks.add(() -> {
				this.terrainPathing = War3MapViewer.this.mapMpq.readPathing();
			});

			this.loadMapTasks.add(() -> {
				War3MapViewer.this.terrain = new Terrain(this.terrainData, this.terrainPathing, w3iFile,
						War3MapViewer.this.webGL, War3MapViewer.this.dataSource, War3MapViewer.this.worldEditStrings,
						War3MapViewer.this, War3MapViewer.this.worldEditData);
			});

			this.loadMapTasks.add(() -> {
				final float[] centerOffset = this.terrainData.getCenterOffset();
				final int[] mapSize = this.terrainData.getMapSize();

				War3MapViewer.this.terrainReady = true;
				War3MapViewer.this.anyReady = true;
				War3MapViewer.this.cliffsReady = true;

				// Override the grid based on the map.
				War3MapViewer.this.worldScene.grid = new Grid(centerOffset[0], centerOffset[1],
						(mapSize[0] * 128) - 128, (mapSize[1] * 128) - 128, 16 * 128, 16 * 128);
			});

			this.loadMapTasks.add(() -> {
				final MdxModel confirmation = (MdxModel) load("UI\\Feedback\\Confirmation\\Confirmation.mdx",
						PathSolver.DEFAULT, null);
				War3MapViewer.this.confirmationInstance = (MdxComplexInstance) confirmation.addInstance();
				War3MapViewer.this.confirmationInstance
						.setSequenceLoopMode(SequenceLoopMode.NEVER_LOOP_AND_HIDE_WHEN_DONE);
				War3MapViewer.this.confirmationInstance.setSequence(0);
				War3MapViewer.this.confirmationInstance.setScene(War3MapViewer.this.worldScene);
			});

			this.loadMapTasks.add(() -> {
				if (War3MapViewer.this.preloadedWTS != null) {
					War3MapViewer.this.allObjectData = War3MapViewer.this.mapMpq
							.readModifications(War3MapViewer.this.preloadedWTS);
				} else {
					War3MapViewer.this.allObjectData = War3MapViewer.this.mapMpq.readModifications();
				}
			});
			this.loadMapTasks.add(() -> {
				War3MapViewer.this.renderController = new NoRenderController(
						new War3MapRenderController(War3MapViewer.this));
				War3MapViewer.this.renderController.setPresenting(!NO_RENDER);
				War3MapViewer.this.simulation = new CSimulation(War3MapViewer.this.mapConfig, w3iFile.getVersion(),
						War3MapViewer.this.gameData.mapGameVersion(War3MapViewer.this.mapFileName,
								w3iFile.getGameDataVersion()),
						War3MapViewer.this.gameData,
						War3MapViewer.this.miscData, War3MapViewer.this.allObjectData.getUnits(),
						War3MapViewer.this.allObjectData.getItems(),
						War3MapViewer.this.allObjectData.getDestructibles(),
						War3MapViewer.this.allObjectData.getAbilities(), War3MapViewer.this.allObjectData.getUpgrades(),
						War3MapViewer.this.allObjectData.getStandardUpgradeEffectMeta(),
						War3MapViewer.this.renderController, War3MapViewer.this.pathingTextures,
						War3MapViewer.this.terrain.pathingGrid,
						CTerrainData.load(this.terrainData, this.tileset, War3MapViewer.this.dataSource,
								War3MapViewer.this.worldEditStrings),
						War3MapViewer.this.terrain.getEntireMap(),
						War3MapViewer.this.seededRandom, War3MapViewer.this.commandErrorListener);
				War3MapViewer.this.simulation.setPlayableMapArea(War3MapViewer.this.terrain.getPlayableMapArea());
			});

			this.loadMapTasks.add(() -> {
				War3MapViewer.this.walkableObjectsTree = new Quadtree<>(War3MapViewer.this.terrain.getEntireMap());
				if (War3MapViewer.this.doodadsAndDestructiblesLoaded) {
					MapViewerDoodads.loadDoodadsAndDestructibles(War3MapViewer.this, War3MapViewer.this.allObjectData, w3iFile);
				} else {
					throw new IllegalStateException(
							"transcription of JS has not loaded a map and has no JS async promises");
				}
			});

			this.loadMapTasks.add(() -> {
				MapViewerLoading.loadSounds(War3MapViewer.this);
			});

			this.loadMapTasks.add(() -> {
				War3MapViewer.this.terrain.createWaves();
			});

			this.loadMapTasks.add(() -> {
				applyTilesetDayNightModels(this.tileset);
			});

			this.startingTaskCount = this.loadMapTasks.size();
		}

		public boolean process() throws IOException {
			final LoadMapTask nextTask = this.loadMapTasks.pollFirst();
			nextTask.run();
			return this.loadMapTasks.isEmpty();
		}

		public void addLoadTask(final LoadMapTask task) {
			this.loadMapTasks.add(task);
		}

		public float getCompletionRatio() {
			return 1.0f - (this.loadMapTasks.size() / (float) this.startingTaskCount);
		}
	}

	public static interface LoadMapTask {
		void run() throws IOException;
	}

	public float getElapsedSecondsForRender(final CTimer timer) {
		return Math.min(timer.getElapsed(this.simulation) + this.updateTime, timer.getTimeoutTime());
	}

	public float getRemainingSecondsForRender(final CTimer timer) {
		return timer.getTimeoutTime() - getElapsedSecondsForRender(timer);
	}

	public void resetTerrainFog() {
		MapViewerVision.resetTerrainFog(this);
	}
}
