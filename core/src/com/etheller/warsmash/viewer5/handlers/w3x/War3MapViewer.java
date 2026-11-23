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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.SimSound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.SoundLabelInfo;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.SoundLabelParams;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CStackedSounds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CWidgetAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.BuildOnBuildingIntersector;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.GetInstantTransformationBuffVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.commandbuttons.CommandCardPopulatingAbilityVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissileSplash;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissileBounce;
import com.etheller.warsmash.util.SessionResources;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundFilename;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import com.etheller.warsmash.util.StreamBytes;
import com.hiveworkshop.rms.parsers.mdlx.AnimationMap;
import com.hiveworkshop.rms.parsers.mdlx.MdlxLight;
import com.hiveworkshop.rms.parsers.mdlx.MdlxModel;
import com.hiveworkshop.rms.parsers.mdlx.timeline.MdlxTimeline;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Quaternion;
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
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.warsmash.networking.GameTurnManager;
import com.etheller.warsmash.parsers.facefx.FaceFxLibrary;
import com.etheller.warsmash.parsers.fdf.FrameTemplatePrefetch;
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
import com.etheller.warsmash.launcher.MapFiles;
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
import com.etheller.warsmash.viewer5.RenderScaler;
import com.etheller.warsmash.viewer5.SoundLoops;
import com.etheller.warsmash.viewer5.ModelInstance;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.AbilityBuilderParserUtil;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.SimpleScene;
import com.etheller.warsmash.viewer5.SceneLightManager;
import com.etheller.warsmash.viewer5.WorldScene;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.gl.WebGL;
import com.etheller.warsmash.viewer5.handlers.AbstractMdxModelViewer;
import com.etheller.warsmash.viewer5.handlers.ResourceHandler;
import com.etheller.warsmash.viewer5.handlers.ResourceHandlerConstructionParams;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxHandler;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxHandler.ShaderEnvironmentType;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;
import com.etheller.warsmash.viewer5.handlers.w3x.SplatModel.SplatMover;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.RenderCorner;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.Terrain;
import com.etheller.warsmash.viewer5.handlers.w3x.lightning.LightningEffectModel;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderFogGhost;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDoodad;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItem;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderItemTypeData;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderLightningEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderShadowType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderImage;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderSpellEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitTypeData;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget.UnitAnimationListenerImpl;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.LocalUiLink;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.PresentationClock;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.UiFrameSource;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.ui.UiFrameWriter;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimThreadGuard;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimThreadMode;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderController;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.BuffUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.EffectAttachmentUI;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedFormerly;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedInterfaceState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDoodadTable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
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
import com.etheller.warsmash.viewer5.handlers.w3x.ui.MeleeUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.ScriptMinimapIcon;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.SettableCommandErrorListener;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.sound.KeyedSounds;
import mpq.MPQArchive;
import mpq.MPQException;




public class War3MapViewer extends AbstractMdxModelViewer implements MdxAssetLoader, UiFrameSource.Holder, LocalUiLink.Holder {
	private static final int MAX_STEPS_PER_FRAME = 3;
	// A sound not decoded by then plays as soon as it is; the start of the game does not wait longer for it.
	static final long SOUND_PRELOAD_WAIT_MILLIS = 3000;
	/** How long one loading screen frame waits for the sounds to be decoded. */
	static final long SOUND_PRELOAD_FRAME_MILLIS = 8;
	/** Game seconds per real second (desktop -speed), so automated playthroughs of long games finish sooner. */
	private static float defaultTimeScale = 1;
	static final byte FOGGED_SHADE = (byte) 168;
	static final byte MASKED_PARTIALLY_VISIBLE_SHADE = (byte) 214;
	// Supported graphics modes of the map information: 1 Classic, 2 Reforged, 3 either.
	private static final long SUPPORTED_MODES_REFORGED = 2;
	/** Bits by {@link PauseSource}; the simulation thread reads it before each step and the commands it applies set it. */
	private volatile int pauseSources;
	/** The models read on helper threads that are built in one frame: one at least, more while this lasts. */
	private static final long MODEL_FINISH_SLICE_NANOS = 2_000_000L;
	/** The same while the models of units just made are built: those units show nothing until then. */
	private static final long URGENT_MODEL_FINISH_SLICE_NANOS = 6_000_000L;
	/** Up to the first fog update, 0.4 s into the game, the last of the game's slow first steps. */
	private static final int OPENING_STEPS = 8;
	private static final int FIRST_FRAME_SHADOWS = 0;
	private static final int FIRST_FRAME_STEPS = 1;
	private static final int FIRST_FRAME_SOUNDS = 2;
	private static final int FIRST_FRAME_FINISH = 3;
	private static final int FIRST_FRAME_DONE = 4;
	private static final int FIRST_FRAME_PATHING = 5;
	private int firstFrameStage = FIRST_FRAME_SHADOWS;
	/** The unit types whose parts of the map are found once the opening steps have played, and the stage after. */
	private final ArrayDeque<CUnitType> pathingPartsPending = new ArrayDeque<>();
	private int firstFrameAfterPathing;
	/** The unit types the map's games made, kept for the next loading; null where there is nowhere to keep them. */
	MapTypeUsage typeUsage;
	private int openingStepsDone;
	private long openingStepsNanos;
	/** A scene update with no time passing skips the animation work the first real one has to do. */
	private static final float OPENING_UPDATE_SECONDS = 0.001f;
	private boolean openingStepsPlayed;
	private volatile BooleanSupplier scriptDialogPause = () -> false;
	/** The supplier's last answer, kept by the simulation side for the render side's reads. */
	private volatile boolean scriptDialogPaused;
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
	volatile boolean worldHidden;
	/** The part of the screen the world is drawn at, stretched to fill (see {@link RenderScaler}). */
	float renderScale = 1f;
	final RenderScaler renderScaler = new RenderScaler();
	NoRenderController renderController;
	/** The step the game plays on to without drawing (see {@link #fastForwardTo}), or -1. */
	volatile int fastForwardTarget = -1;
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
	/** Doodads still waiting for their model to give them a first animation. */
	public final List<RenderDoodad> pendingDoodads = new ArrayList<>();
	/** The doodads scripts read: from the map file, not from the drawn ones. */
	public CDoodadTable doodadTable = CDoodadTable.empty();
	static final float SKY_FAR_PLANE = 100000f;
	static final float UNIT_INDICATOR_SECONDS = 3f;
	static final float UNIT_INDICATOR_PULSE_SECONDS = 1f;
	Scene skyScene;
	MdxComplexInstance skyInstance;
	ModelCinematic modelCinematic;
	private ModelCinematic.Timeline modelCinematicTimeline;
	private boolean modelCinematicsEnabled = true;
	final List<UnitIndicator> unitIndicators = new ArrayList<>();
	public List<RenderDoodad> decals = new ArrayList<>();
	private int fogDoodadTicks;
	private boolean doodadFogFading;
	public List<TerrainDoodad> terrainDoodads = new ArrayList<>();
	public boolean doodadsReady;
	public boolean unitsAndItemsLoaded;
	public MappedData unitsData = new MappedData();
	public MappedData unitMetaData = new MappedData();
	public final WidgetList widgets = new WidgetList();
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
	/** The constants the simulation plays by: those of the data without graphics packages. */
	public DataTable simulationMiscData;
	private com.etheller.warsmash.viewer5.handlers.w3x.ui.AllyColorFilter allyColorFilter;
	boolean restartMissionDisabled;
	String altMinimapIcon;
	private final List<ScriptMinimapIcon> scriptMinimapIcons = new ArrayList<>();
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
	private MdxComplexInstance dncPortrait;
	public CSimulation simulation;
	float updateTime = 0;

	// for World Editor, I think
	public Vector2[] startLocations;

	final DynamicShadowManager dynamicShadowManager = new DynamicShadowManager();
	private Vector3 shadowSunDirection;
	private Vector3 shadowSceneryDirection;

	/** Seeds the simulation; -Dwarsmash.seed fixes it so test runs repeat. Kept in saved games. */
	private int gameSeed = chooseGameSeed();

	private final Random seededRandom = new Random(this.gameSeed);

	private ReplaySession replaySession;
	private boolean replayNotSaved;
	volatile boolean gameEnded;
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
	private War3MapRenderController mapRenderController;
	/** The simulation's own thread and frames, when the mode asks for them; null otherwise. */
	private MapViewerSimThread simThread;
	private PresentationClock presentation;
	private volatile UiFrameSource uiFrameSource;
	private MapViewerFog mapFog;
	Collection<String> soundsToPreload;
	private long soundWaitStartedMillis;
	private int[] soundCounts;
	int localPlayerIndex;
	/**
	 * The simulation thread's copies of who is viewed (what {@link #isViewedPlayer} answers from), written where the
	 * interface changes them so the simulation never reads the interface's own fields.
	 */
	volatile int viewedLocalPlayer;
	volatile int viewedReplayPlayer = REPLAY_VISION_NONE;
	/** The interface switches a map script flips (cursor, selection, frames); local to this machine. */
	public final ScriptUiState scriptUiState = new ScriptUiState();
	private final SettableCommandErrorListener commandErrorListener;

	public final List<TextTag> textTags = new ArrayList<>();

	private final War3MapConfig mapConfig;
	CPlayerFogOfWar observerFogOfWar;
	/** Whose sight a replay's picture shows: a player, {@link #REPLAY_VISION_ALL_PLAYERS} or none (a game). */
	int replayVisionPlayer = REPLAY_VISION_NONE;
	boolean replayFogShown = true;
	CPlayerFogOfWar replayFogOfWar;
	/** With frames, the render side's copy of the local player's fog cells as of the frame being shown. */
	private CPlayerFogOfWar presentedFogOfWar;
	final List<CPlayerFogOfWar> replayVisionSources = new ArrayList<>();
	/** A replay's speed: game and animation time both run this many times faster. */
	private float replaySpeed = 1;
	public static final int REPLAY_VISION_ALL_PLAYERS = -1;
	static final int REPLAY_VISION_NONE = -2;

	volatile GameTurnManager gameTurnManager;

	War3MapW3i lastLoadedMapInformation;
	private String mapFileName;
	private int campaign = -1;
	private boolean reforgedCampaignMaps;
	private Boolean campaignGame;
	private FileHandle savedGameToLoad;
	private boolean savedGameLoadedByScript;
	InterfaceStateKeeper interfaceStateKeeper;
	private java.util.function.Consumer<List<SimSound>> soundVoiceRestorer;
	private Object scriptEnvironment;
	private final List<SavedGame.AiEntry> loadedScripts = new ArrayList<>();
	String dayNightTerrainModel;
	String dayNightUnitModel;
	String skyModel;
	float[] waterColor;
	private AudioContext uiAudioContext;
	// Found on the render side; the simulation reads them, so that a script's sounds need no crossing once known.
	private final Map<String, UnitSound.ScriptFile> scriptSoundFiles = new java.util.concurrent.ConcurrentHashMap<>();
	private final Map<String, SoundLabelInfo> soundLabelInfos = new java.util.concurrent.ConcurrentHashMap<>();
	private final Map<String, SoundLabelParams> soundLabelParams = new java.util.concurrent.ConcurrentHashMap<>();
	private final SharedFileSounds<Sound> sharedFileSounds = new SharedFileSounds<>(
			fileName -> UnitSound.createSound(this.mapMpq, getScriptSoundFile(fileName)),
			(fileName, done) -> UnitSound.createSoundLater(this.mapMpq, getScriptSoundFile(fileName), done),
			sound -> {
				// Nothing may go on following a sound that is let go.
				SoundLoops.stopAll(sound);
				SessionResources.release(sound);
			});
	private final List<CSoundFilename> killedSounds = new ArrayList<>();
	private final CSoundFilename.Owner fileSoundOwner = new CSoundFilename.Owner() {
		@Override
		public void watchKilled(final CSoundFilename handle) {
			if (!War3MapViewer.this.killedSounds.contains(handle)) {
				War3MapViewer.this.killedSounds.add(handle);
			}
		}

		@Override
		public void releaseSound(final Sound sound) {
			War3MapViewer.this.sharedFileSounds.release(sound, System.currentTimeMillis());
		}
	};
	final List<CreatedDestructable> createdDestructables = new ArrayList<>();
	volatile FileHandle pendingSave;
	volatile FileHandle pendingLoad;
	volatile boolean pendingLoadByScript;
	Runnable afterPendingSave;
	SaveGameListener saveGameListener;
	LoadGameListener loadGameListener;

	FogSettings defaultFogSettings;
	public final ViewSettings viewSettings = new ViewSettings();

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
		SimThreadGuard.assertGl();
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

	private DataTable simulationUberSplatTable;

	/**
	 * Whether the splat type exists, read from the data every graphics set shares, so that scripts create the same
	 * splats (and take the same handles) on every machine of a game.
	 */
	public synchronized boolean hasSimulationUberSplat(final String name) {
		if (this.simulationUberSplatTable == null) {
			final DataTable table = new DataTable(this.worldEditStrings);
			try (InputStream stream = this.dataSource.graphicsIndependent()
					.getResourceAsStream("Splats\\UberSplatData.slk")) {
				if (stream != null) {
					table.readSLK(stream);
				}
			}
			catch (final IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
			this.simulationUberSplatTable = table;
		}
		return this.simulationUberSplatTable.get(name) != null;
	}

	public List<com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUbersplat> getScriptUbersplats() {
		return this.scriptUbersplats;
	}

	public void updateImage(final CImage image) {
		SimThreadGuard.assertGl();
		MapViewerEffects.updateImage(this, image);
	}

	public void showTrackable(final CTrackable trackable) {
		SimThreadGuard.assertGl();
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
		if (MapFiles.isAbsolute(mapFilePath) || !gameDataSource.has(mapFilePath)) {
			File mapFile = MapFiles.find(mapFilePath);
			if (mapFile == null) {
				mapFile = new File(mapFilePath);
			}
			if (mapFile.exists()) {
				return new War3Map(gameDataSource, mapFile);
			} else {
				Log.warn(MapFiles.describeMissing(mapFilePath));
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
		if ((this.simThread != null) && this.simThread.isThreaded()) {
			// a step that runs ends before the game it plays is taken apart
			this.simThread.shutdown();
			this.simThread = null;
		}
		endModelCinematic();
		if ((this.worldScene != null) && (this.worldScene.audioContext != null)) {
			this.worldScene.audioContext.endLoops();
		}
		this.killedSounds.clear();
		this.sharedFileSounds.clear();
		closeReplaySession();
		if (this.simulation != null) {
			SimulationChecksum.gameClosed(this.simulation);
		}
		if (this.typeUsage != null) {
			this.typeUsage.flush();
		}
		if (this.terrain != null) {
			this.terrain.dispose();
		}
		this.dynamicShadowManager.dispose();
		this.renderScaler.dispose();
		if (this.lightningTypeToModel != null) {
			for (final LightningEffectModel model : this.lightningTypeToModel.values()) {
				model.dispose();
			}
		}
		super.dispose();
		try {
			this.dataSource.close();
			if (this.mapMpq != null) {
				this.mapMpq.close();
			}
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
		CWidgetAbilityTargetCheckReceiver.INSTANCE.reset();
		if (this.simThread != null) {
			this.simThread.shutdown();
			this.simThread = null;
		}
		if (this.mapRenderController != null) {
			this.mapRenderController.releaseSounds();
			this.mapRenderController = null;
		}
		this.soundMixer.release();
		this.stackedSounds.stopAll();
	}

	public MapLoader createMapLoader(final War3Map war3Map, final War3MapW3i w3iFile, final int localPlayerIndex)
			throws IOException {
		this.localPlayerIndex = localPlayerIndex;
		this.viewedLocalPlayer = localPlayerIndex;
		this.mapMpq = war3Map;
		this.pathingTextures = new CPathingTextures(war3Map);
		this.lastLoadedMapInformation = w3iFile;
		// The simulation made near the end of the loading reads these; they are parsed beside the loading before it.
		AbilityBuilderParserUtil.prepare("abilityBehaviors");
		if (!this.gameData.getAbilityCompatibility().isEmpty()) {
			AbilityBuilderParserUtil.prepare("abilityBehaviors_compatibility");
		}

		return new MapLoader(war3Map, w3iFile, localPlayerIndex);
	}

	public SimulationRenderComponentLightning createLightning(final War3ID lightningId,
			final RenderUnit renderPeerSource, final RenderWidget renderPeerTarget) {
		SimThreadGuard.assertGl();
		return MapViewerEffects.createLightning(this, lightningId, renderPeerSource, renderPeerTarget);
	}

	public SimulationRenderComponentLightning createLightning(final War3ID lightningId,
			final RenderUnit renderPeerSource, final RenderWidget renderPeerTarget, final Float duration) {
		SimThreadGuard.assertGl();
		return MapViewerEffects.createLightning(this, lightningId, renderPeerSource, renderPeerTarget, duration);
	}

	public SimulationRenderComponentLightningMovable createLightning(final War3ID lightningId, final float x1,
			final float y1, final float z1, final float x2, final float y2, final float z2) {
		SimThreadGuard.assertGl();
		return MapViewerEffects.createLightning(this, lightningId, x1, y1, z1, x2, y2, z2);
	}

	public SimulationRenderComponentLightningMovable createLightning(final War3ID lightningId, final float x1,
			final float y1, final float z1, final float x2, final float y2, final float z2,
			final boolean checkVisibility) {
		SimThreadGuard.assertGl();
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
		this.mapFog = new MapViewerFog(this);
	}

	private static final class IdleFogTimer extends CTimer {
		@Override
		public void onFire(final CSimulation simulation) {
		}
	}

	/**
	 * A loaded game has no fade to continue: the ground and the doodads take the shade of the fog state they have
	 * now, so they match while the game is still paused.
	 */
	public void settleFogShading() {
		this.mapFog.settle();
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

	private static final long IDLE_MODEL_CHECK_NANOS = 2_000_000_000L;
	private long nextIdleModelCheckNanos;

	/** Lets go of the models of unit types that have not been shown for a while, see {@link RenderUnitTypeData#evictIdleModels}. */
	private void evictIdleUnitModels() {
		final long now = System.nanoTime();
		if ((now - this.nextIdleModelCheckNanos) < 0) {
			return;
		}
		this.nextIdleModelCheckNanos = now + IDLE_MODEL_CHECK_NANOS;
		this.renderUnitTypeData.evictIdleModels(this, now);
	}

	public RenderUnitType getUnitTypeData(final War3ID key) {
		return this.renderUnitTypeData.get(key);
	}

	/**
	 * The game stops while paused (a script, the Pause button, or the game menu in a single player game); the camera,
	 * the interface and rendering go on.
	 */
	public synchronized void setGamePaused(final PauseSource source, final boolean paused) {
		if (paused) {
			this.pauseSources |= 1 << source.ordinal();
		}
		else {
			this.pauseSources &= ~(1 << source.ordinal());
		}
	}

	public boolean isGamePaused() {
		return (this.pauseSources != 0) || this.scriptDialogPaused;
	}

	/**
	 * {@link #isGamePaused()} for the side that steps the simulation: it asks the script dialog, which reads the
	 * simulation's clock, and keeps the answer for the render side.
	 */
	public boolean isGamePausedOnSimulation() {
		this.scriptDialogPaused = this.scriptDialogPause.getAsBoolean();
		return isGamePaused();
	}

	/** Whether a script dialog on the screen has stopped the game; it is not a pause that a command or a save keeps. */
	public void setScriptDialogPause(final BooleanSupplier scriptDialogPause) {
		this.scriptDialogPause = scriptDialogPause;
	}

	public boolean isGamePaused(final PauseSource source) {
		return (this.pauseSources & (1 << source.ordinal())) != 0;
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

	/** What a script's sound path plays and how long it lasts, found once for each path. */
	public UnitSound.ScriptFile getScriptSoundFile(final String fileName) {
		UnitSound.ScriptFile file = this.scriptSoundFiles.get(fileName);
		if (file == null) {
			file = UnitSound.scriptFile(this.mapMpq, fileName);
			this.scriptSoundFiles.put(fileName, file);
		}
		return file;
	}

	/** Any thread: what {@link #getScriptSoundFile} found for the path before, null when it has not been asked for. */
	public UnitSound.ScriptFile cachedScriptSoundFile(final String fileName) {
		return this.scriptSoundFiles.get(fileName);
	}

	/** The files of a sound label and their lengths, found once for each label. Render thread. */
	public SoundLabelInfo getSoundLabelInfo(final String label) {
		final String key = label == null ? "" : label;
		SoundLabelInfo info = this.soundLabelInfos.get(key);
		if (info == null) {
			final UnitSound sound = this.uiSounds.getSound(label);
			final float[] durations = new float[sound.getSoundCount()];
			for (int i = 0; i < durations.length; i++) {
				durations[i] = sound.getDuration(i);
			}
			info = new SoundLabelInfo(durations);
			this.soundLabelInfos.put(key, info);
		}
		return info;
	}

	/** Any thread: what {@link #getSoundLabelInfo} found before, null when it has not been asked for. */
	public SoundLabelInfo cachedSoundLabelInfo(final String label) {
		return this.soundLabelInfos.get(label == null ? "" : label);
	}

	/** What a sound label sets on the sounds made from it, found once for each label. Render thread. */
	public SoundLabelParams getSoundLabelParams(final String label) {
		final String key = label == null ? "" : label;
		SoundLabelParams params = this.soundLabelParams.get(key);
		if (params == null) {
			params = SoundLabelParams.of(getSoundLabel(label));
			this.soundLabelParams.put(key, params);
		}
		return params;
	}

	/** Any thread: what {@link #getSoundLabelParams} found before, null when it has not been asked for. */
	public SoundLabelParams cachedSoundLabelParams(final String label) {
		return this.soundLabelParams.get(label == null ? "" : label);
	}

	/**
	 * The sound of a file a script creates sounds from. Every handle of the file shares it: a map that creates a sound
	 * each time it needs one would otherwise decode the file again every time. A handle gives it back through
	 * {@link #getFileSoundOwner} once it is killed and has played out.
	 */
	public Sound getFileSound(final String fileName) {
		return this.sharedFileSounds.acquire(fileName, getScriptSoundFile(fileName).getDuration());
	}

	/**
	 * As {@link #getFileSound}, for a handle that waits for its sound: one not decoded yet is made in the background
	 * and given to the callback when it is.
	 */
	public void getFileSoundLater(final String fileName, final Consumer<Sound> ready) {
		this.sharedFileSounds.acquireLater(fileName, getScriptSoundFile(fileName).getDuration(), ready);
	}

	/** The sound of a file for a handle that a loaded game made, which is never let go. */
	public Sound getRestoredFileSound(final String fileName) {
		return this.sharedFileSounds.pin(fileName, getScriptSoundFile(fileName).getDuration());
	}

	/** Holds the sound a handle made from a file takes from {@link #getFileSound} until it is killed and has played out. */
	public CSoundFilename.Owner getFileSoundOwner() {
		return this.fileSoundOwner;
	}

	private void releaseFinishedSounds() {
		if (!this.killedSounds.isEmpty() && (this.simulation != null)) {
			final int tick = getPresentedTick();
			for (int i = this.killedSounds.size() - 1; i >= 0; i--) {
				if (this.killedSounds.get(i).releaseIfDone(tick)) {
					this.killedSounds.remove(i);
				}
			}
		}
		this.sharedFileSounds.sweep(System.currentTimeMillis());
	}

	public String getFileSoundName(final Sound sound) {
		return this.sharedFileSounds.fileNameOf(sound);
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
		if (this.simThread != null) {
			// the removals are for the peers of the game being left, not for the ones the loaded game makes next
			this.simThread.playFrames();
		}
	}

	public void finishLoadingSavedGame(final Collection<CDestructable> preparedDestructables) {
		MapViewerUnits.finishLoadingSavedGame(this, preparedDestructables);
		if (this.simThread != null) {
			this.simThread.playFrames();
		}
	}

	/** Set by the sound natives: gives the sounds a saved game held the voices that continue them. */
	public void setSoundVoiceRestorer(final java.util.function.Consumer<List<SimSound>> restorer) {
		this.soundVoiceRestorer = restorer;
	}

	public void restoreSoundVoices(final List<SimSound> sounds) {
		if ((this.soundVoiceRestorer != null) && !sounds.isEmpty()) {
			this.soundVoiceRestorer.accept(sounds);
		}
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

	/**
	 * Whether the game is a campaign's (its rules and graphics are those of a campaign mission), as the game was
	 * started, not by where its map is kept.
	 */
	public void setCampaignGame(final boolean campaignGame) {
		this.campaignGame = campaignGame;
	}

	/** A viewer nothing told falls back to the map's folder, which is how every game was told before the flag. */
	public boolean isCampaignGame() {
		return this.campaignGame != null ? this.campaignGame : CampaignMaps.isCampaignMap(this.mapFileName);
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

	/**
	 * Every widget; destructables are listed apart for the animation update, which only visits the ones that can
	 * change (see {@link RenderDestructable#wake()}).
	 */
	public static final class WidgetList extends ArrayList<RenderWidget> {
		private static final int DESTRUCTABLE_RECHECK_FRAMES = 64;

		final List<RenderWidget> animated = new ArrayList<>();
		final List<RenderDestructable> destructables = new ArrayList<>();
		public final List<RenderDestructable> active = new ArrayList<>();
		private int recheckCursor;

		@Override
		public boolean add(final RenderWidget widget) {
			if (!super.add(widget)) {
				return false;
			}
			if (widget instanceof RenderDestructable) {
				this.destructables.add((RenderDestructable) widget);
				((RenderDestructable) widget).wake();
			}
			else {
				this.animated.add(widget);
			}
			return true;
		}

		@Override
		public boolean remove(final Object widget) {
			if (!super.remove(widget)) {
				return false;
			}
			if (widget instanceof RenderDestructable) {
				this.destructables.remove(widget);
				this.active.remove(widget);
				((RenderDestructable) widget).sleep();
			}
			else {
				this.animated.remove(widget);
			}
			return true;
		}

		public void wakeAllDestructables() {
			for (int i = 0, n = this.destructables.size(); i < n; i++) {
				this.destructables.get(i).wake();
			}
		}

		void recheckSome() {
			final int n = this.destructables.size();
			if (n == 0) {
				return;
			}
			final int slice = (n + DESTRUCTABLE_RECHECK_FRAMES - 1) / DESTRUCTABLE_RECHECK_FRAMES;
			for (int i = 0; i < slice; i++) {
				if (this.recheckCursor >= n) {
					this.recheckCursor = 0;
				}
				final RenderDestructable destructable = this.destructables.get(this.recheckCursor++);
				if (destructable.hasPendingChange()) {
					destructable.wake();
				}
			}
		}
	}

	private void updateDestructableAnimations() {
		// Changes the game reports wake a destructable at once; this only catches a state written without a report
		// (a loaded game).
		this.widgets.recheckSome();
		final List<RenderDestructable> active = this.widgets.active;
		for (int i = active.size() - 1; i >= 0; i--) {
			final RenderDestructable destructable = active.get(i);
			destructable.updateAnimations(this);
			if (destructable.canSleep()) {
				destructable.sleep();
				final int last = active.size() - 1;
				active.set(i, active.get(last));
				active.remove(last);
			}
		}
	}

	@Override
	public void update() {
		PerfStats.frameStart();
		if (this.anyReady) {
			beginSimulationFrame();
			this.successfulFrames++;
			// while paused everything is still placed and drawn, but no game or animation time passes
			final boolean paused = isGamePaused();
			final float deltaTime = paused ? 0 : Gdx.graphics.getDeltaTime() * this.replaySpeed;
			MapViewerVision.updateReplayVision(this);
			this.terrain.update(deltaTime);
			PerfStats.end(PerfStats.Section.TERRAIN_UPDATE);

			final float rawDeltaTime = paused ? 0 : Gdx.graphics.getRawDeltaTime() * this.replaySpeed;
			if (this.simThread != null) {
				this.simThread.advancePresentation(Gdx.graphics.getRawDeltaTime() * this.replaySpeed, this.timeScale,
						paused);
			}
			super.update(rawDeltaTime);
			if (PerfStats.ENABLED) {
				PerfStats.textures(getTextureBytes(), getEvictedTextures(), getEvictedModels());
			}
			releaseFinishedSounds();
			PerfStats.end(PerfStats.Section.SCENE_UPDATE);

			if (this.openingStepsPlayed) {
				this.openingStepsPlayed = false;
			}
			else {
				this.updateTime += rawDeltaTime * this.timeScale;
			}

			final Iterator<TextTag> textTagIterator = this.textTags.iterator();
			while (textTagIterator.hasNext()) {
				if (textTagIterator.next().update(deltaTime)) {
					textTagIterator.remove();
				}
			}
			for (final RenderWidget unit : this.widgets.animated) {
				unit.updateAnimations(this);
			}
			updateDestructableAnimations();
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
			// A doodad's stand animation is picked once its model has loaded; after that it follows its own sequence
			// ends (see RenderDoodad#enableLazyClock).
			for (int i = this.pendingDoodads.size() - 1; i >= 0; i--) {
				final RenderDoodad item = this.pendingDoodads.get(i);
				if (item.instance.model.ok && item.startStandSequence()) {
					final int last = this.pendingDoodads.size() - 1;
					this.pendingDoodads.set(i, this.pendingDoodads.get(last));
					this.pendingDoodads.remove(last);
				}
			}

			PerfStats.end(PerfStats.Section.WIDGETS);
			if (this.worldHidden || this.gameEnded) {
				// The game is over on the score screen; its scores must not change behind it.
				this.updateTime = 0;
			}
			if ((this.simThread != null) && this.simThread.isThreaded()) {
				// the simulation thread makes the steps that came due; it waits for a turn or a pause by itself
				if (!this.gameEnded && !isGamePaused() && !isFastForwarding()
						&& (this.updateTime >= WarsmashConstants.SIMULATION_STEP_TIME)) {
					final int due = (int) (this.updateTime / WarsmashConstants.SIMULATION_STEP_TIME);
					this.updateTime -= due * WarsmashConstants.SIMULATION_STEP_TIME;
					this.simThread.grantSteps(due, this.timeScale * this.replaySpeed);
				}
			}
			int stepsThisFrame = 0;
			// A pause, from a command or the map's script, stops the very step it comes at, whatever the frame time.
			while ((this.updateTime >= WarsmashConstants.SIMULATION_STEP_TIME) && !this.gameEnded
					&& !isGamePaused() && !isSimulationThreaded()) {
				if (stepsThisFrame >= (MAX_STEPS_PER_FRAME * this.timeScale * this.replaySpeed)) {
					// A slow device would otherwise spend the next frame catching up and fall further behind each
					// time; like the original, the game runs slower instead.
					this.updateTime = 0;
					break;
				}
				stepsThisFrame++;
				PerfStats.stepMark();
				final int latestTurn = this.gameTurnManager.getLatestCompletedTurn();
				if (isGamePaused()) {
					break;
				}
				if (latestTurn >= this.simulation.getGameTurnTick()) {
					this.updateTime -= WarsmashConstants.SIMULATION_STEP_TIME;
					PerfStats.stepEnd(PerfStats.Step.TURN);
					runSimulationUpdate();
					PerfStats.simulationStep();
					PerfStats.stepMark();
					runTurnCompleted();
					PerfStats.stepEnd(PerfStats.Step.TURN);
					MapViewerSaves.runPendingSaveRequests(this);
					PerfStats.stepEnd(PerfStats.Step.SAVES);
				} else {
					if (this.updateTime > (WarsmashConstants.SIMULATION_STEP_TIME * 3)) {
						this.gameTurnManager.framesSkipped(this.updateTime / WarsmashConstants.SIMULATION_STEP_TIME);
						this.updateTime = 0;
					}
					break;
				}
			}
			PerfStats.end(PerfStats.Section.SIMULATION);
			if (this.mapFog != null) {
				this.mapFog.update();
			}
			if (hasPendingModels()) {
				finishReadyModels(hasUrgentPendingModels() ? URGENT_MODEL_FINISH_SLICE_NANOS : MODEL_FINISH_SLICE_NANOS);
			}
			if (!this.gameEnded && (this.renderUnitTypeData != null)) {
				// the simulation offers the types it needs, so no frame depends on the simulation's step
				this.typePrewarm.update(this);
				evictIdleUnitModels();
			}
			else if (this.gameEnded) {
				// The score screen's models queue their textures after the game ended; nothing else uploads them.
				final long end = System.nanoTime() + MODEL_FINISH_SLICE_NANOS;
				while (hasDeferredTextures() && finishReadyDeferredTexture() && (System.nanoTime() < end)) {
				}
			}
			if (isSimulationThreaded()) {
				// the simulation thread does a pause's work and the saves the steps ask for
				if (paused && ((this.pendingSave != null) || (this.pendingLoad != null))) {
					whileSimulationHeld(() -> MapViewerSaves.runPendingSaveRequests(this));
				}
			}
			else {
				if (paused) {
					MapViewerSaves.runPendingSaveRequests(this);
				}
				if (isGamePaused()) {
					runWhilePaused();
				}
			}
			final float timeOfDay = presentedTimeOfDay();
			if (this.dncTerrain != null) {
				final float dayRatio = timeOfDay / this.simulation.getGameplayConstants().getGameDayHours();
				this.dncTerrain.setFrameByRatio(dayRatio);
				this.dncTerrain.update(rawDeltaTime, null);
				this.dynamicShadowManager.setSunLight(this.shadowSunDirection, this.shadowSceneryDirection, dayRatio);
			}
			if (this.dncUnit != null) {
				this.dncUnit.setFrameByRatio(timeOfDay / this.simulation.getGameplayConstants().getGameDayHours());
				this.dncUnit.update(rawDeltaTime, null);
			}

			if (this.dncPortrait != null) {
				this.dncPortrait.setFrameByRatio(timeOfDay / this.simulation.getGameplayConstants().getGameDayHours());
				this.dncPortrait.update(rawDeltaTime, null);
			}

			if (this.dncTarget != null) {
				this.dncTarget.setFrameByRatio(timeOfDay / this.simulation.getGameplayConstants().getGameDayHours());
				this.dncTarget.update(rawDeltaTime, null);
			}
		}
	}

	/**
	 * The clock that says which game time the picture shows and between which frames of the game; null without a
	 * simulation thread, where the render objects follow the live simulation.
	 */
	public PresentationClock getPresentation() {
		return this.presentation;
	}

	/** The time of day of the step the picture shows; with the thread on, the simulation's own is a step ahead. */
	private float presentedTimeOfDay() {
		if (isSimulationThreaded() && (this.presentation != null)) {
			final SimFrame frame = this.presentation.frameBefore();
			if (frame != null) {
				return frame.timeOfDay;
			}
		}
		return this.simulation.getGameTimeOfDay();
	}

	/** Whether the simulation makes its steps on its own thread ({@link SimThreadMode#ON}). */
	public boolean isSimulationThreaded() {
		final MapViewerSimThread thread = this.simThread;
		return (thread != null) && thread.isThreaded();
	}

	/**
	 * Render thread: runs the action while the simulation makes no step, waiting for a step that runs; for what reads
	 * or changes the simulation directly from the render side. Without the thread on, at once.
	 */
	public void whileSimulationHeld(final Runnable action) {
		final MapViewerSimThread thread = this.simThread;
		if ((thread == null) || !thread.isThreaded()) {
			action.run();
			return;
		}
		thread.hold();
		try {
			action.run();
		}
		finally {
			thread.release();
		}
	}

	/**
	 * Render thread: like {@link #whileSimulationHeld}, but only when no step runs now.
	 *
	 * @return false when the action was not run
	 */
	public boolean tryWhileSimulationHeld(final Runnable action) {
		final MapViewerSimThread thread = this.simThread;
		if ((thread == null) || !thread.isThreaded()) {
			action.run();
			return true;
		}
		return thread.tryWhileHeld(action);
	}

	/** Render thread (the app leaves the screen): no step runs until {@link #unparkSimulation}. */
	public void parkSimulation(final long maxWaitMillis) {
		if (isSimulationThreaded()) {
			this.simThread.park(maxWaitMillis);
		}
	}

	public void unparkSimulation() {
		if (isSimulationThreaded()) {
			this.simThread.unpark();
		}
	}

	/**
	 * Something of the game's flow (a command from the network) that changes the simulation: on the simulation thread
	 * between steps when it has one, else on the render thread's next frame. The caller does not wait.
	 */
	public void postToSimulation(final Runnable job) {
		if (isSimulationThreaded()) {
			this.simThread.post(job);
		}
		else {
			Gdx.app.postRunnable(job);
		}
	}

	/**
	 * Something for the interface or the screens that the simulation side asks for: from the simulation thread it
	 * runs on the render thread while the step waits; elsewhere at once.
	 */
	public void onRenderThread(final Runnable action) {
		final MapViewerSimThread thread = this.simThread;
		if ((thread != null) && thread.isThreaded() && thread.isSimThread()) {
			thread.callOnRender(action);
		}
		else {
			action.run();
		}
	}

	/**
	 * Like {@link #onRenderThread}, but from the simulation thread the render thread runs it on its next frame and the
	 * step does not wait; for what leaves the game (its screen goes away meanwhile).
	 */
	public void onRenderThreadLater(final Runnable action) {
		final MapViewerSimThread thread = this.simThread;
		if ((thread != null) && thread.isThreaded() && thread.isSimThread()) {
			Gdx.app.postRunnable(action);
		}
		else {
			action.run();
		}
	}

	/** The number the frames of the game give a missile; 0 without a simulation thread. */
	public int projectileFrameId(final CProjectile projectile) {
		return this.simThread == null ? 0 : this.simThread.projectileId(projectile);
	}

	void setPresentation(final PresentationClock presentation) {
		this.presentation = presentation;
	}

	/** What the script natives reach the local interface through; null for a game without a simulation thread. */
	@Override
	public LocalUiLink localUiLink() {
		return this.simThread == null ? null : this.simThread.localUiLink();
	}

	/** How natives that touch the interface or the render side are called when the simulation has its own thread; else null. */
	public BiFunction<String, JassFunction, JassFunction> renderThreadNativeWrapper() {
		return this.simThread == null ? null : this.simThread.nativeWrapper();
	}

	/** Render thread, a game not drawn with the thread on: the steps due are made on it and waited for. */
	void runGrantedSteps(final int steps) {
		this.simThread.runGrantedSteps(steps);
	}

	/** Render thread, a skip ahead with the thread on: it drives the steps itself, the thread only runs them. */
	void beginFastForwardFrame() {
		if (isSimulationThreaded()) {
			this.simThread.dropGrantedSteps();
			this.simThread.hold();
		}
	}

	void endFastForwardFrame() {
		if (isSimulationThreaded()) {
			this.simThread.release();
		}
	}

	/** One simulation step; on the simulation thread when the game has one. */
	void runSimulationUpdate() {
		if (this.simThread != null) {
			this.simThread.update();
		}
		else {
			this.simulation.update();
		}
	}

	/** The game turn manager's end-of-step work, on the simulation thread when the game has one. */
	void runTurnCompleted() {
		if (this.simThread != null) {
			this.simThread.turnCompleted(this.gameTurnManager, this.simulation.getGameTurnTick());
		}
		else {
			this.gameTurnManager.turnCompleted(this.simulation.getGameTurnTick());
		}
	}

	void runWhilePaused() {
		if (this.simThread != null) {
			this.simThread.whilePaused(this.gameTurnManager);
		}
		else {
			this.gameTurnManager.whilePaused();
		}
	}

	/** Before the frame's first look at the game: lets the simulation take the commands the interface queued. */
	void beginSimulationFrame() {
		if (this.simThread != null) {
			this.simThread.beforeFrame();
			if (!this.gameEnded && (this.renderUnitTypeData != null)) {
				this.typePrewarm.loadUrgent(this);
			}
		}
	}

	/**
	 * The listener the interface gives its commands to: with a simulation thread they are queued and the thread hands
	 * them to the target between steps; without one the target itself.
	 */
	public CPlayerUnitOrderListener threadedOrderListener(final CPlayerUnitOrderListener target,
			final int playerIndex) {
		return this.simThread == null ? target : this.simThread.orderListener(target, playerIndex);
	}

	/** A unit's peer made its real animation listener: the simulation gets it, or its stand-in with a simulation thread. */
	public void setSimulationAnimationListener(final CUnit unit, final UnitAnimationListenerImpl listener) {
		if (this.simThread == null) {
			unit.setUnitAnimationListener(listener);
		}
		else {
			this.simThread.attachAnimationListener(unit, listener);
		}
	}

	/** A destructable's peer exists: the events that name its handle can find the object. */
	public void destructablePeerCreated(final CDestructable destructable) {
		if (this.simThread != null) {
			this.simThread.destructablePeerCreated(destructable);
		}
	}

	/** The peer has added the tags its type requires to the real listener. */
	public void simulationAnimationTagsReady(final CUnit unit, final UnitAnimationListenerImpl listener) {
		if (this.simThread != null) {
			this.simThread.seedAnimationTags(unit, listener);
		}
	}

	/**
	 * With warsmash.norender only the game runs: no model, terrain or effect is updated or drawn, the way a replay
	 * skips ahead. The game must come out the same as when it is drawn.
	 */
	@Override
	public void updateAndRender() {
		if (this.typeUsage != null) {
			this.typeUsage.flushIfDue();
		}
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
		if (this.simThread != null) {
			this.simThread.afterFrame();
		}
		// The map's script makes its starting units in the first game step.
		if ((this.soundsToPreload != null) && (getPresentedTick() > 0)) {
			final Collection<String> interfaceSoundKeys = this.soundsToPreload;
			// it reads the units the game has: with the thread on, while no step runs (else on a later frame)
			if (tryWhileSimulationHeld(() -> MapViewerLoading.preloadSounds(this, interfaceSoundKeys))) {
				this.soundsToPreload = null;
			}
		}
	}

	/**
	 * Does what the first frame of a new game would otherwise do on its own while the loading screen is still up: the
	 * programs of the unit shadows, the game's first steps (see {@link MapViewerUpdate#playOpeningSteps}) and the
	 * sounds of the units they make. Every step is the same step the frames would play; only when they are played
	 * changes.
	 */
	/**
	 * Whether the loading screen plays the game's first steps (see {@link #prepareFirstFrameNext}), after which the
	 * map's script has made its first changes to the pathing.
	 */
	boolean playsOpeningSteps() {
		return !NO_RENDER && (this.fastForwardTarget < 0) && !isReplayPlayback() && (this.savedGameToLoad == null);
	}

	/** Queues the types whose parts of the map are found after the opening steps instead of while loading. */
	void deferPathingParts(final Collection<CUnitType> types) {
		this.simulation.deferPathingParts();
		this.pathingPartsPending.addAll(types);
	}

	public void prepareFirstFrame() {
		while (!prepareFirstFrameNext()) {
		}
	}

	/**
	 * {@link #prepareFirstFrame} one slice at a time, the unit shadows' programs, then each of the steps and last what
	 * follows them, so a loading screen can draw between them.
	 *
	 * @return whether the first frame is prepared
	 */
	public boolean prepareFirstFrameNext() {
		switch (this.firstFrameStage) {
		case FIRST_FRAME_SHADOWS:
			this.dynamicShadowManager.prepare(this.webGL);
			// Without the thread's own steps (SERIAL) the frames are made as the steps are, so they are left to the frames.
			this.firstFrameStage = (!this.anyReady || NO_RENDER || ((this.simThread != null) && !this.simThread.isThreaded())
					|| (this.fastForwardTarget >= 0) || isReplayPlayback() || (this.simulation.getGameTurnTick() != 0))
							? FIRST_FRAME_DONE : FIRST_FRAME_STEPS;
			break;
		case FIRST_FRAME_STEPS: {
			final long start = System.nanoTime();
			if ((isSimulationThreaded() ? this.simThread.playOpeningSteps(1)
					: MapViewerUpdate.playOpeningSteps(this, 1)) == 0) {
				afterOpeningSteps(this.openingStepsDone == 0 ? FIRST_FRAME_DONE : FIRST_FRAME_SOUNDS);
			}
			else if (++this.openingStepsDone >= OPENING_STEPS) {
				afterOpeningSteps(FIRST_FRAME_SOUNDS);
			}
			else if ((this.openingStepsDone == 1) && (this.soundsToPreload != null)) {
				// The units the script made in the first step are known now; the steps that follow play while their
				// sounds are decoded.
				final Collection<String> interfaceSoundKeys = this.soundsToPreload;
				tryWhileSimulationHeld(() -> MapViewerLoading.queueSounds(this, interfaceSoundKeys, false));
			}
			this.openingStepsNanos += System.nanoTime() - start;
			break;
		}
		case FIRST_FRAME_PATHING: {
			// One unit size a slice: the steps the script made its first changes in are played by now.
			final long start = System.nanoTime();
			final CUnitType pathingType = this.pathingPartsPending.poll();
			whileSimulationHeld(() -> this.simulation.prewarmPathingParts(pathingType));
			PerfStats.setupPart("pathing parts", start);
			if (this.pathingPartsPending.isEmpty()) {
				this.firstFrameStage = this.firstFrameAfterPathing;
			}
			this.openingStepsNanos += System.nanoTime() - start;
			break;
		}
		case FIRST_FRAME_SOUNDS: {
			final long start = System.nanoTime();
			if ((this.soundsToPreload == null) || (getPresentedTick() <= 0)) {
				this.firstFrameStage = FIRST_FRAME_FINISH;
			}
			else {
				// The wait is spread over the frames of the loading screen instead of holding one for a second.
				if (this.soundCounts == null) {
					this.soundWaitStartedMillis = System.currentTimeMillis();
					final Collection<String> interfaceSoundKeys = this.soundsToPreload;
					final int[][] queued = new int[1][];
					if (tryWhileSimulationHeld(() -> queued[0] = MapViewerLoading.queueSounds(this, interfaceSoundKeys, false))) {
						this.soundCounts = queued[0];
					}
				}
				else if (Extensions.audio.awaitLoading(SOUND_PRELOAD_FRAME_MILLIS)
						|| ((System.currentTimeMillis() - this.soundWaitStartedMillis) >= SOUND_PRELOAD_WAIT_MILLIS)) {
					// What is heard only after a selection is asked for now, and decodes while the game runs.
					final Collection<String> interfaceSoundKeys = this.soundsToPreload;
					final int[][] queued = new int[1][];
					if (tryWhileSimulationHeld(() -> queued[0] = MapViewerLoading.queueSounds(this, interfaceSoundKeys, true))) {
						this.soundCounts = queued[0];
						Extensions.audio.finishLoading(0);
						MapViewerLoading.logSoundsPreloaded(this.soundCounts, this.soundWaitStartedMillis);
						this.soundsToPreload = null;
						this.firstFrameStage = FIRST_FRAME_FINISH;
					}
				}
			}
			this.openingStepsNanos += System.nanoTime() - start;
			break;
		}
		case FIRST_FRAME_DONE:
			break;
		default: {
			final long start = System.nanoTime();
			// The loading time must not be played as game time on top of them.
			this.openingStepsPlayed = true;
			if ((this.soundsToPreload != null) && (getPresentedTick() > 0)) {
				final Collection<String> interfaceSoundKeys = this.soundsToPreload;
				// with the thread on, the units are read while it makes no step; else with the frames
				if (tryWhileSimulationHeld(() -> MapViewerLoading.preloadSounds(this, interfaceSoundKeys))) {
					this.soundsToPreload = null;
				}
			}
			super.update(OPENING_UPDATE_SECONDS);
			for (final RenderWidget unit : this.widgets.animated) {
				unit.updateAnimations(this);
			}
			updateDestructableAnimations();
			PerfStats.openingStepsDone(this.openingStepsDone, this.openingStepsNanos + (System.nanoTime() - start));
			this.firstFrameStage = FIRST_FRAME_DONE;
			break;
		}
		}
		return this.firstFrameStage == FIRST_FRAME_DONE;
	}

	private void afterOpeningSteps(final int next) {
		this.firstFrameAfterPathing = next;
		this.firstFrameStage = this.pathingPartsPending.isEmpty() ? next : FIRST_FRAME_PATHING;
	}

	public void fastForwardTo(final int tick, final FastForwardListener whenReached) {
		final MapViewerSimThread thread = this.simThread;
		if ((thread != null) && thread.isThreaded() && thread.isSimThread()) {
			// the render thread drives a skip; it takes it on its next frame
			Gdx.app.postRunnable(() -> MapViewerUpdate.fastForwardTo(this, tick, whenReached));
			return;
		}
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

	public DynamicShadowManager getDynamicShadowManager() {
		return this.dynamicShadowManager;
	}

	public void setRenderScale(final float renderScale) {
		this.renderScale = Math.max(0.25f, Math.min(1f, renderScale));
	}

	public float getRenderScale() {
		return this.renderScale;
	}

	public void setWorldHidden(final boolean worldHidden) {
		this.worldHidden = worldHidden;
	}

	public boolean isWorldHidden() {
		return this.worldHidden;
	}

	/** PlayModelCinematic: a new one replaces the one still playing. */
	public void playModelCinematic(final String path) {
		playModelCinematic(path, false, 0, 0, 0, 0);
	}

	/** BlzPlayModelCinematicGameAtPosition: the cinematic plays in the world, at the place, turned about the Z axis. */
	public void playModelCinematicAtPosition(final String path, final float x, final float y, final float z,
			final float rotationDegrees) {
		playModelCinematic(path, true, x, y, z, rotationDegrees);
	}

	private void playModelCinematic(final String path, final boolean inGame, final float x, final float y,
			final float z, final float rotationDegrees) {
		endModelCinematic();
		this.modelCinematicTimeline = null;
		if (!this.modelCinematicsEnabled) {
			return;
		}
		this.modelCinematic = ModelCinematic.play(this, path, inGame, x, y, z, rotationDegrees);
		if (this.modelCinematic != null) {
			this.modelCinematicTimeline = this.modelCinematic.getTimeline();
		}
	}

	/** BlzPreloadModelCinematicGame: loads the model so it plays without a pause. */
	public boolean preloadModelCinematic(final String path) {
		return ModelCinematic.load(this, path) != null;
	}

	/** BlzSetCinematicEnabledDE: while it is off, model cinematics are not played. */
	public void setModelCinematicsEnabled(final boolean enabled) {
		this.modelCinematicsEnabled = enabled;
		if (!enabled) {
			endModelCinematic();
		}
	}

	public int getModelCinematicShotCount() {
		return this.modelCinematicTimeline == null ? 0 : this.modelCinematicTimeline.getShotCount();
	}

	public int getModelCinematicCurrentShot() {
		return this.modelCinematicTimeline == null ? 0
				: this.modelCinematicTimeline.getCurrentShot(this.simulation.getGameTurnTick());
	}

	public float getModelCinematicRemainingTime() {
		return this.modelCinematicTimeline == null ? 0f
				: this.modelCinematicTimeline.getRemainingSeconds(this.simulation.getGameTurnTick());
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
		SimThreadGuard.assertGl();
		this.waterColor = color;
		this.terrain.setWaterBaseColor(color[0], color[1], color[2], color[3]);
	}

	public void setSkyModel(final String path) {
		SimThreadGuard.assertGl();
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
			this.skyScene.unlit = true;
		}
		final MdxModel model = MapViewerEffects.loadModelMdx(this.dataSource, this, path, this.mapPathSolver, this.solverParams);
		if (model == null) {
			return;
		}
		this.skyInstance = (MdxComplexInstance) model.addInstance();
		this.skyInstance.setSequenceLoopMode(SequenceLoopMode.ALWAYS_LOOP);
		this.skyInstance.setSequence(0);
		this.skyInstance.setScene(this.skyScene);
	}

	public void addIndicator(final CWidget widget, final int red, final int green, final int blue, final int alpha) {
		SimThreadGuard.assertGl();
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
		SimThreadGuard.assertGl();
		MapViewerDoodads.setDoodadAnimation(this, doodadId, area, centerX, centerY, radius, nearestOnly, animation);
	}

	public void setDoodadColor(final String doodadId, final Rectangle area, final float centerX,
			final float centerY, final float radius, final boolean nearestOnly, final int playerColor) {
		SimThreadGuard.assertGl();
		MapViewerDoodads.setDoodadColor(this, doodadId, area, centerX, centerY, radius, nearestOnly, playerColor);
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

	RenderUnitTypeData renderUnitTypeData;
	/** The object data the render unit types above were made for. */
	Warcraft3MapRuntimeObjectData renderUnitTypeDataObjects;
	final MapViewerTypePrewarm typePrewarm = new MapViewerTypePrewarm();
	RenderItemTypeData renderItemTypeData;
	/** Whether the map's script has characters speak lines with a portrait. */
	boolean scriptSpeaksLines;
	private FrameTemplatePrefetch frameTemplatePrefetch;

	void prefetchFrameTemplates() {
		this.frameTemplatePrefetch = FrameTemplatePrefetch.start(this.mapMpq, MeleeUI.FRAME_DEF_TOC,
				MeleeUI.SMASH_FRAME_DEF_TOC);
	}

	/** The interface's frame templates being read while the map loads, handed over once. */
	public FrameTemplatePrefetch takeFrameTemplatePrefetch() {
		final FrameTemplatePrefetch prefetch = this.frameTemplatePrefetch;
		this.frameTemplatePrefetch = null;
		return prefetch;
	}

	public boolean isScriptSpeakingLines() {
		return this.scriptSpeaksLines;
	}

	private FaceFxLibrary faceFx;

	/** The faces of the map's data, which the loading lists ahead of the first spoken line. */
	public FaceFxLibrary faceFxLibrary() {
		if (this.faceFx == null) {
			this.faceFx = new FaceFxLibrary(this.dataSource, this.gameDataSource,
					this.mapMpq == null ? null : this.mapMpq.getMapContentsDataSource());
		}
		return this.faceFx;
	}

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

	/**
	 * The direction of the main light of a day and night cycle model, from the ground towards the light. The Classic
	 * models have one light for both; the Reforged ones give units and terrain a light each, which the shadows follow.
	 */
	private static Vector3 readSunDirection(final DataSource data, final String modelPath) {
		final String path = mdx(modelPath);
		try {
			if (!data.has(path)) {
				return null;
			}
			try (InputStream stream = data.getResourceAsStream(path)) {
				final MdlxModel parser = StreamBytes.parse(stream, MdlxModel::new);
				for (final MdlxLight light : parser.getLights()) {
					if (light.type != MdlxLight.Type.DIRECTIONAL) {
						continue;
					}
					for (final MdlxTimeline<?> timeline : light.getTimelines()) {
						if (timeline.name.equals(AnimationMap.KGRT.getWar3id()) && (timeline.getValues().length > 0)) {
							final float[] q = (float[]) timeline.getValues()[0];
							return new Quaternion(q[0], q[1], q[2], q[3]).transform(new Vector3(0, 0, 1)).nor();
						}
					}
				}
			}
		}
		catch (final IOException | RuntimeException e) {
			Log.warn("The light of " + modelPath + " could not be read: " + e);
		}
		return null;
	}

	public void setDayNightModels(final String terrainDNCFile, final String unitDNCFile) {
		SimThreadGuard.assertGl();
		this.dayNightTerrainModel = terrainDNCFile;
		this.dayNightUnitModel = unitDNCFile;
		this.shadowSunDirection = readSunDirection(this.dataSource, unitDNCFile);
		final Vector3 terrainDirection = readSunDirection(this.dataSource, terrainDNCFile);
		this.shadowSceneryDirection = ((terrainDirection == null) || (this.shadowSunDirection == null)
				|| (terrainDirection.dst2(this.shadowSunDirection) < 1e-6f)) ? null : terrainDirection;
		if (this.shadowSunDirection == null) {
			this.shadowSunDirection = terrainDirection;
		}
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

	/** SetPortraitLight: the light of the portraits comes from the day and night model, as the world's does. */
	public void setPortraitLight(final String portraitDNCFile) {
		SimThreadGuard.assertGl();
		this.dncPortrait = null;
		if ((portraitDNCFile == null) || portraitDNCFile.isEmpty()) {
			return;
		}
		final MdxModel model = loadModelMdx(portraitDNCFile);
		if (model == null) {
			return;
		}
		final MdxComplexInstance instance = (MdxComplexInstance) model.addInstance();
		instance.setSequenceLoopMode(SequenceLoopMode.ALWAYS_LOOP);
		instance.setSequence(0);
		this.dncPortrait = instance;
	}

	public static String mdx(String mdxPath) {
		if (mdxPath.toLowerCase(Locale.ROOT).endsWith(".mdl")) {
			mdxPath = mdxPath.substring(0, mdxPath.length() - 4);
		}
		if (!mdxPath.toLowerCase(Locale.ROOT).endsWith(".mdx")) {
			mdxPath += ".mdx";
		}
		return mdxPath;
	}

	public static String mdl(String mdxPath) {
		if (mdxPath.toLowerCase(Locale.ROOT).endsWith(".mdx")) {
			mdxPath = mdxPath.substring(0, mdxPath.length() - 4);
		}
		if (!mdxPath.toLowerCase(Locale.ROOT).endsWith(".mdl")) {
			mdxPath += ".mdl";
		}
		return mdxPath;
	}

	public String blp(String iconPath) {
		final int lastDotIndex = iconPath.lastIndexOf('.');
		if (lastDotIndex != -1) {
			iconPath = iconPath.substring(0, lastDotIndex);
		}
		if (!iconPath.toLowerCase(Locale.ROOT).endsWith(".blp")) {
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
	public SceneLightManager createPortraitLightManager() {
		return newPortraitLights();
	}

	private W3xScenePortraitLightManager newPortraitLights() {
		return isPackageInterface() ? W3xScenePortraitLightManager.forPackageModels(this, this.lightDirection)
				: new W3xScenePortraitLightManager(this, this.lightDirection);
	}

	/** The scene of the unit portrait: the only simple scene whose light SetPortraitLight replaces. */
	public Scene addPortraitScene() {
		final W3xScenePortraitLightManager portraitLights = newPortraitLights();
		portraitLights.setLightOverride(() -> (this.dncPortrait == null) || this.dncPortrait.lights.isEmpty() ? null
				: this.dncPortrait.lights.get(0));
		final Scene scene = new SimpleScene(this, portraitLights);
		this.scenes.add(scene);
		return scene;
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
		return getScriptSoundsTable().get(label);
	}

	private DataTable getScriptSoundsTable() {
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
		return this.scriptSoundsTable;
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

	/**
	 * Shows the unit with the model of another unit type (BlzSetUnitSkin); the simulation unit keeps its own type.
	 * False when the id is no unit type or the unit has no drawn peer.
	 */
	public boolean setUnitSkin(final CUnit unit, final War3ID skinId) {
		SimThreadGuard.assertGl();
		if ((this.renderUnitTypeData == null) || !this.unitToRenderPeer.containsKey(unit)
				|| (this.renderUnitTypeData.get(skinId) == null)) {
			return false;
		}
		this.renderController.unitUpdatedType(unit, skinId, true);
		return true;
	}

	/** Shows the item with the model of another item type (BlzSetItemSkin); false when that type has no model. */
	public boolean setItemSkin(final CItem item, final War3ID skinId) {
		SimThreadGuard.assertGl();
		final RenderItem renderItem = getRenderPeer(item);
		final RenderItemType renderItemType = this.renderItemTypeData == null ? null
				: this.renderItemTypeData.get(skinId);
		if ((renderItem == null) || (renderItemType == null)) {
			return false;
		}
		renderItem.updateItemModel(this, renderItemType);
		return true;
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

	/** The icons map scripts put on the minimap; only the minimap draws them, the simulation never reads them. */
	public List<ScriptMinimapIcon> getScriptMinimapIcons() {
		return this.scriptMinimapIcons;
	}

	/** The source of interface state both the panels and the simulation side use; null until the interface data exists. */
	@Override
	public UiFrameSource uiFrameSource() {
		UiFrameSource source = this.uiFrameSource;
		if ((source == null) && (this.abilityDataUI != null) && (this.gameUI != null)) {
			synchronized (this) {
				source = this.uiFrameSource;
				if (source == null) {
					source = new UiFrameSource(new UiFrameWriter(this.abilityDataUI, this.gameUI.getTemplates(),
							this.miscData, getAllObjectData().getUnits(), this.localPlayerIndex));
					this.uiFrameSource = source;
				}
			}
		}
		return source;
	}

	public RenderUnit getRenderPeer(final CUnit unit) {
		SimThreadGuard.assertGl();
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
		SimThreadGuard.assertGl();
		return this.destructableToRenderPeer.get(dest);
	}

	public RenderItem getRenderPeer(final CItem item) {
		SimThreadGuard.assertGl();
		return this.itemToRenderPeer.get(item);
	}

	public RenderWidget getRenderPeer(final CWidget damagedDestructable) {
		SimThreadGuard.assertGl();
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
		return (this.gameTurnManager instanceof com.etheller.warsmash.networking.WarsmashClient)
				|| (this.gameTurnManager instanceof com.etheller.warsmash.lan.LockstepTurnManager);
	}

	/** Whether the game is a LAN game, whose turns come from a host. */
	public boolean isLanGame() {
		return this.gameTurnManager instanceof com.etheller.warsmash.lan.LockstepTurnManager;
	}

	public GameTurnManager getGameTurnManager() {
		return this.gameTurnManager;
	}

	public void setGameTurnManager(final GameTurnManager gameTurnManager) {
		this.gameTurnManager = gameTurnManager;
	}

	public RenderSpellEffect spawnSpellEffectOnUnitEx(final CWidget unit, final War3ID alias,
			final CEffectType effectType, final int index) {
		SimThreadGuard.assertGl();
		return MapViewerEffects.spawnSpellEffectOnUnitEx(this, unit, alias, effectType, index);
	}

	public RenderSpellEffect spawnSpellEffectOnUnitEx(final CWidget unit, final War3ID alias,
			final CEffectType effectType, final int index, final String attachPointName) {
		SimThreadGuard.assertGl();
		return MapViewerEffects.spawnSpellEffectOnUnitEx(this, unit, alias, effectType, index, attachPointName);
	}

	public List<RenderSpellEffect> spawnSpellEffectOnUnitEx(final CUnit unit, final War3ID alias,
			final CEffectType effectType) {
		SimThreadGuard.assertGl();
		return MapViewerEffects.spawnSpellEffectOnUnitEx(this, unit, alias, effectType);
	}

	public RenderSpellEffect spawnSpellEffectEx(final float x, final float y, final float facing, final War3ID alias,
			final CEffectType effectType, final int index) {
		SimThreadGuard.assertGl();
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
		SimThreadGuard.assertGl();
		return MapViewerEffects.addSpecialEffectTarget(this, modelName, targetWidget, attachPointName);
	}

	public RenderSpellEffect addSpecialEffectTarget(final String modelName, final CWidget targetWidget,
			List<String> attachPointNames) {
		SimThreadGuard.assertGl();
		return MapViewerEffects.addSpecialEffectTarget(this, modelName, targetWidget, attachPointNames);
	}

	public RenderSpellEffect addSpecialEffect(final String modelName, final float x, final float y, final float yaw) {
		SimThreadGuard.assertGl();
		return MapViewerEffects.addSpecialEffect(this, modelName, x, y, yaw);
	}

	@Override
	public MdxModel loadModelMdx(final String path) {
		return MapViewerEffects.loadModelMdx(this, path);
	}

	/** The model of an effect that may wait for its file, see {@link MapViewerEffects#loadWaitingModelMdx}. */
	public MdxModel loadWaitingModelMdx(final String path) {
		return MapViewerEffects.loadWaitingModelMdx(this, path);
	}

	@Override
	public boolean setDeferModelLoadsInGame(final boolean defer) {
		// a game that is not drawn, or skips ahead, would only pile up the models it never shows
		setPrepareModelsAhead(!NO_RENDER && (this.fastForwardTarget < 0));
		return setDeferModelLoads(defer && (isDeferModelTextures() || this.deferModelsWhileLoading));
	}

	@Override
	public MdxModel emptyModel() {
		if (this.emptyModel == null) {
			final ResourceHandler handler = (ResourceHandler) findHandler(".mdx")[0];
			final MdxModel model = (MdxModel) handler
					.construct(new ResourceHandlerConstructionParams(this, handler, ".mdx", this.mapPathSolver, ""));
			model.loadData(null, null);
			this.emptyModel = model;
		}
		return this.emptyModel;
	}

	public static MdxModel loadModelMdx(final DataSource dataSource, final ModelViewer modelViewer, final String path,
			final PathSolver pathSolver, final Object solverParams) {
		return MapViewerEffects.loadModelMdx(dataSource, modelViewer, path, pathSolver, solverParams);
	}

	/** The simulation changed the blight of these corners; the ground shows it once the viewer sees the place. */
	public void blightChanged(final float[] cornerPositions, final boolean blighted) {
		SimThreadGuard.assertGl();
		for (int k = 0; k < cornerPositions.length; k += 2) {
			final RenderCorner corner = this.terrain.getCorner(cornerPositions[k], cornerPositions[k + 1]);
			if (corner != null) {
				corner.setBlight(blighted);
				this.terrain.blightChanged(cornerPositions[k], cornerPositions[k + 1]);
			}
		}
		this.terrain.updateShownBlight();
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

	/** Whether the world is drawn with the Reforged HD package (not Classic, not DE). */
	public boolean isHdGraphics() {
		return LayeredDataSource.graphicsOf(this.gameDataSource) == GraphicsSet.REFORGED;
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
		if ((this.presentation != null) && (this.presentedFogOfWar != null)) {
			return this.presentedFogOfWar;
		}
		return this.simulation.getPlayer(this.localPlayerIndex).getFogOfWar();
	}

	/**
	 * Render thread, for each frame the presentation plays: takes the local player's fog cells the frame carries, so
	 * what the picture asks of the fog never reads the simulation's own.
	 */
	void takePresentedFog(final SimFrame frame) {
		if (!frame.fogChanged || (this.simulation == null)) {
			return;
		}
		CPlayerFogOfWar copy = this.presentedFogOfWar;
		if ((copy == null) || (copy.getFogOfWarBuffer().capacity() != frame.fogLength)) {
			copy = new CPlayerFogOfWar(this.simulation.getPathingGrid());
			if (copy.getFogOfWarBuffer().capacity() != frame.fogLength) {
				return;
			}
			this.presentedFogOfWar = copy;
		}
		System.arraycopy(frame.fog, 0, copy.getFogOfWarBuffer().array(), 0, frame.fogLength);
	}

	/**
	 * Render thread: the game step the picture shows, which for a game with frames is the one of the frame being shown
	 * (the simulation itself may be steps ahead).
	 */
	public int getPresentedTick() {
		final PresentationClock clock = this.presentation;
		if (clock != null) {
			final SimFrame frame = clock.frameBefore();
			// no frame yet: the game has not taken a step the picture could show
			return frame != null ? frame.tick : 0;
		}
		return this.simulation.getGameTurnTick();
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
		SimThreadGuard.assertGl();
		MapViewerVision.startObserverView(this);
	}

	public boolean isObserverView() {
		return this.observerFogOfWar != null;
	}

	public static byte fadeLineOfSightColor(final byte lastFogStateColor, final byte state) {
		return MapViewerVision.fadeLineOfSightColor(lastFogStateColor, state);
	}

	private MdxModel emptyModel;

	/**
	 * While the loading screen is up, the models of unit and item types, whose use waits for the game, are read and
	 * parsed on the helper thread beside the loading and built by the loader as they come, as they are in the game.
	 */
	private boolean deferModelsWhileLoading;

	public final class MapLoader {
		/** The longest a step of the loading runs before it hands the frame back, so that a paused app can stop. */
		private static final long SLICE_NANOS = 20_000_000L;
		/** The time the tasks of one frame of the loading screen may take together. */
		public static final long FRAME_NANOS = 100_000_000L;
		/** The models read ahead that wait to be built at most; the loader waits for the helper beyond that. */
		private static final int MODEL_BACKLOG = 6;
		/** The textures that may wait for their decoding while the loader goes on with other tasks. */
		private static final int TEXTURE_BACKLOG = 32;

		private final LoadMapTask deferredTexturesTask = this::loadDeferredTextures;
		/** Whether the last look at the deferred textures found none to upload and left the loader to its tasks. */
		private boolean texturesNotReady;

		private final LinkedList<LoadMapTask> loadMapTasks = new LinkedList<>();
		private int startingTaskCount;

		private char tileset;
		private DataSource tilesetSource;

		private War3MapW3e terrainData;

		private War3MapWpm terrainPathing;

		private MapLoader(final War3Map war3Map, final War3MapW3i w3iFile, final int localPlayerIndex) {
			final PathSolver wc3PathSolver = War3MapViewer.this.wc3PathSolver;
			War3MapViewer.this.deferModelsWhileLoading = !NO_RENDER && (War3MapViewer.this.fastForwardTarget < 0);

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
								final byte[] mapData = StreamBytes.readAll(mapStream);
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
					// Last, so the map's own files win over the game data's version folders and the tileset files.
					dataSources.add(war3Map.getMapContentsDataSource());
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
				MapViewerVision.applyMapTerrainFog(War3MapViewer.this, w3iFile);
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
				War3MapViewer.this.worldScene.setGridBounds(centerOffset[0], centerOffset[1],
						(mapSize[0] * 128) - 128, (mapSize[1] * 128) - 128);
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
				final Warcraft3MapRuntimeObjectData.Loading loading = War3MapViewer.this.mapMpq
						.readModificationsInSteps(War3MapViewer.this.preloadedWTS);
				addNextTask(() -> War3MapViewer.this.allObjectData = loading.getResult());
				final List<Warcraft3MapRuntimeObjectData.Step> steps = loading.getSteps();
				for (int i = steps.size() - 1; i >= 0; i--) {
					final Warcraft3MapRuntimeObjectData.Step step = steps.get(i);
					addNextTask(step::run);
				}
			});
			this.loadMapTasks.add(() -> {
				War3MapViewer.this.mapRenderController = new War3MapRenderController(War3MapViewer.this);
				final SimThreadMode simThreadMode = SimThreadMode.current();
				SimulationRenderController simulationSide = War3MapViewer.this.mapRenderController;
				if (simThreadMode.usesFrames()) {
					War3MapViewer.this.simThread = new MapViewerSimThread(War3MapViewer.this,
							War3MapViewer.this.mapRenderController);
					simulationSide = War3MapViewer.this.simThread.controller();
				}
				War3MapViewer.this.renderController = new NoRenderController(simulationSide);
				War3MapViewer.this.renderController.setPresenting(!NO_RENDER);
				War3MapViewer.this.simulation = new CSimulation(War3MapViewer.this.mapConfig, w3iFile.getVersion(),
						War3MapViewer.this.gameData.mapGameVersion(War3MapViewer.this.mapFileName,
								w3iFile.getGameDataVersion(), War3MapViewer.this.isCampaignGame()),
						War3MapViewer.this.gameData,
						War3MapViewer.this.simulationMiscData, War3MapViewer.this.allObjectData.getUnits(),
						War3MapViewer.this.allObjectData.getItems(),
						War3MapViewer.this.allObjectData.getDestructibles(),
						War3MapViewer.this.allObjectData.getAbilities(), War3MapViewer.this.allObjectData.getUpgrades(),
						War3MapViewer.this.allObjectData.getStandardUpgradeEffectMeta(),
						War3MapViewer.this.renderController, War3MapViewer.this.pathingTextures,
						War3MapViewer.this.terrain.pathingGrid,
						CTerrainData.load(this.terrainData, this.tileset,
								War3MapViewer.this.dataSource.graphicsIndependent(),
								War3MapViewer.this.worldEditStrings),
						War3MapViewer.this.terrain.getEntireMap(),
						War3MapViewer.this.seededRandom, War3MapViewer.this.simThread == null
								? War3MapViewer.this.commandErrorListener
								: War3MapViewer.this.simThread.onRender(War3MapViewer.this.commandErrorListener));
				War3MapViewer.this.simulation.setPlayableMapArea(War3MapViewer.this.terrain.getPlayableMapArea());
				if (War3MapViewer.this.simThread != null) {
					War3MapViewer.this.simThread.attach(War3MapViewer.this.simulation);
				}
			});

			this.loadMapTasks.add(() -> {
				War3MapViewer.this.walkableObjectsTree = new Quadtree<>(War3MapViewer.this.terrain.getEntireMap());
				if (War3MapViewer.this.doodadsAndDestructiblesLoaded) {
					MapViewerDoodads.queueDoodadsAndDestructibles(War3MapViewer.this, War3MapViewer.this.allObjectData,
							w3iFile, this);
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

			this.loadMapTasks.add(() -> {
				MapViewerEffects.applyMapGlobalWeather(War3MapViewer.this, w3iFile);
			});

			this.loadMapTasks.add(() -> {
				MapViewerPrewarm.queue(War3MapViewer.this, w3iFile, this);
			});

			this.startingTaskCount = this.loadMapTasks.size();
		}

		/**
		 * Runs tasks until the time is up and returns whether all are done: a frame of the loading screen costs
		 * more than most tasks, so one task a frame spent over half of the loading on drawing.
		 */
		public boolean processFor(final long nanos) throws IOException {
			final long end = System.nanoTime() + nanos;
			boolean done;
			do {
				done = process();
			}
			while (!done && (System.nanoTime() < end));
			return done;
		}

		public boolean process() throws IOException {
			final LoadMapTask nextTask = this.loadMapTasks.pollFirst();
			nextTask.run();
			if (War3MapViewer.this.deferModelsWhileLoading) {
				keepModelsComing();
			}
			if (hasDeferredTextures() && !((nextTask == this.deferredTexturesTask) && this.texturesNotReady)) {
				addNextTask(this.deferredTexturesTask);
			}
			if (this.loadMapTasks.isEmpty() && War3MapViewer.this.deferModelsWhileLoading && hasPendingModels()) {
				this.loadMapTasks.addFirst(this::finishModels);
			}
			final boolean done = this.loadMapTasks.isEmpty();
			if (done) {
				War3MapViewer.this.deferModelsWhileLoading = false;
			}
			return done;
		}

		private void buildReadyModels(final long nanos) {
			final boolean before = setDeferTextureLoads(true);
			try {
				finishReadyModels(nanos);
			}
			finally {
				setDeferTextureLoads(before);
			}
		}

		/** Builds the models the helper has read, and waits for it while too many are not built. */
		private void keepModelsComing() {
			if (!hasPendingModels()) {
				return;
			}
			buildReadyModels(SLICE_NANOS / 2);
			final long limit = System.nanoTime() + SLICE_NANOS * 5;
			while ((pendingModelCount() >= MODEL_BACKLOG) && (System.nanoTime() < limit)) {
				LockSupport.parkNanos(500_000L);
				buildReadyModels(SLICE_NANOS / 2);
			}
		}

		/** The end of the loading: the models the helper is still at are built before the game starts. */
		private void finishModels() {
			buildReadyModels(SLICE_NANOS);
			if (hasPendingModels()) {
				LockSupport.parkNanos(1_000_000L);
				this.loadMapTasks.addFirst(this::finishModels);
			}
		}

		/**
		 * Wraps a task that loads models, whose textures are then decoded and uploaded in frames of their own after
		 * it, a slice at a time: one model's textures can take longer than an app that is paused may be held up.
		 */
		public LoadMapTask modelTask(final LoadMapTask task) {
			return () -> {
				final boolean before = setDeferTextureLoads(true);
				try {
					task.run();
				}
				finally {
					setDeferTextureLoads(before);
				}
			};
		}

		/**
		 * Uploads the textures the helpers have decoded. The tasks that follow go on meanwhile unless one needs the
		 * textures of those before it in memory, there are many waiting or the loading has no other task left: a
		 * wait for the helpers then is the only thing to do.
		 */
		private void loadDeferredTextures() {
			final long end = System.nanoTime() + SLICE_NANOS;
			this.texturesNotReady = false;
			final LoadMapTask following = this.loadMapTasks.peekFirst();
			if ((following != null) && !(following instanceof SettledTask) && (deferredTextureCount() < TEXTURE_BACKLOG)) {
				this.texturesNotReady = !finishReadyDeferredTexture();
				while (!this.texturesNotReady && hasDeferredTextures() && (System.nanoTime() < end)
						&& finishReadyDeferredTexture()) {
				}
				return;
			}
			do {
				loadNextDeferredTexture();
			}
			while (hasDeferredTextures() && (System.nanoTime() < end));
		}

		public LoadMapTask settled(final LoadMapTask task) {
			return new SettledTask(task);
		}

		/**
		 * Runs the items in slices of a frame each, ahead of the tasks still waiting, and then the task that follows.
		 */
		public <T> void addSlices(final Iterator<T> items, final Consumer<T> each, final LoadMapTask after)
				throws IOException {
			addSlices(items, each, () -> true, after);
		}

		/** {@link #addSlices(Iterator, Consumer, LoadMapTask)} that stops taking items once keepGoing says no. */
		public <T> void addSlices(final Iterator<T> items, final Consumer<T> each, final BooleanSupplier keepGoing,
				final LoadMapTask after) throws IOException {
			if (!items.hasNext() || !keepGoing.getAsBoolean()) {
				after.run();
				return;
			}
			addNextTask(modelTask(() -> {
				final long end = System.nanoTime() + SLICE_NANOS;
				do {
					each.accept(items.next());
				}
				while (items.hasNext() && keepGoing.getAsBoolean() && (System.nanoTime() < end)
						&& (pendingModelCount() < MODEL_BACKLOG));
				addSlices(items, each, keepGoing, after);
			}));
		}

		/** Puts a task ahead of those waiting, for the rest of work a task spreads over several frames. */
		public void addNextTask(final LoadMapTask task) {
			this.loadMapTasks.addFirst(task);
			this.startingTaskCount++;
		}

		public void addLoadTask(final LoadMapTask task) {
			this.loadMapTasks.add(task);
			this.startingTaskCount++;
		}

		public float getCompletionRatio() {
			return 1.0f - (this.loadMapTasks.size() / (float) this.startingTaskCount);
		}
	}

	public static interface LoadMapTask {
		void run() throws IOException;
	}

	/** A task that reads the texture memory the tasks before it have used, so their textures are uploaded first. */
	private static final class SettledTask implements LoadMapTask {
		private final LoadMapTask task;

		private SettledTask(final LoadMapTask task) {
			this.task = task;
		}

		@Override
		public void run() throws IOException {
			this.task.run();
		}
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
