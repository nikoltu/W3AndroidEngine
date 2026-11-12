package com.etheller.warsmash;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.BufferUtils;
import com.etheller.warsmash.util.SessionResources;
import com.etheller.warsmash.input.touch.SliderHost;
import com.etheller.warsmash.input.touch.TouchTarget;
import com.etheller.warsmash.movie.Movie;
import com.etheller.warsmash.movie.Movies;
import com.etheller.warsmash.datasources.CascDataSourceDescriptor;
import com.etheller.warsmash.datasources.CompoundDataSource;
import com.etheller.warsmash.datasources.CompoundDataSourceDescriptor;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.DataSourceDescriptor;
import com.etheller.warsmash.datasources.FolderDataSourceDescriptor;
import com.etheller.warsmash.datasources.MpqDataSourceDescriptor;
import com.etheller.warsmash.datasources.SubdirDataSource;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.frames.FadingSpriteBatch;
import com.etheller.warsmash.parsers.jass.Jass2;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.parsers.jass.Jass2.RootFrameListener;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.DataErrors;
import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.util.WarsmashUtils;
import com.etheller.warsmash.viewer5.Model;
import com.etheller.warsmash.viewer5.ModelInstance;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.RenderBatch;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.TextureMapper;
import com.etheller.warsmash.viewer5.handlers.ModelHandler;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.CameraPreset;
import com.etheller.warsmash.viewer5.handlers.w3x.camera.CameraRates;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerGameResult;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerScore;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.config.War3MapConfig;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CMapDifficulty;
import com.etheller.warsmash.launcher.GameResult;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameExit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCache;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.MeleeUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.toggle.MeleeToggleUI;

public class WarsmashGdxMapScreen implements InputProcessor, Screen, TouchTarget, SliderHost {
	public static final boolean ENABLE_AUDIO = true;
	private final War3MapViewer viewer;
	private final Rectangle tempRect = new Rectangle();

	// libGDX stuff
	private OrthographicCamera uiCamera;
	private SpriteBatch batch;
	private ExtendViewport uiViewport;
	private GlyphLayout glyphLayout;

	private Texture solidGreenTexture;

	private ShapeRenderer shapeRenderer;

	private MdxModel timeIndicator;

	private Scene uiScene;
	private WarsmashUI meleeUI;
	private MeleeUI baseMeleeUI;

	private Music currentMusic;
	private final WarsmashGdxMultiScreenGame screenManager;
	private final WarsmashGdxMenuScreen menuScreen;
	private final CPlayerUnitOrderListener uiOrderListener;
	private CommonEnvironment commonEnv;
	private int previousVao;
	private int vao;
	/** The GL the vertex array was made with, which leaves deletes alone once its context is lost. */
	private GL30 vaoGl;
	private SessionResources.Scope sessionResources;
	private boolean disposed;
	private boolean scriptFailed;
	private static final float MAX_INTERFACE_FRAME_TIME = 3 * WarsmashConstants.SIMULATION_STEP_TIME;

	public WarsmashGdxMapScreen(final War3MapViewer mapViewer, final WarsmashGdxMultiScreenGame screenManager,
			final WarsmashGdxMenuScreen menuScreen, final CPlayerUnitOrderListener uiOrderListener) {
		this.viewer = mapViewer;
		this.screenManager = screenManager;
		this.menuScreen = menuScreen;
		this.uiOrderListener = uiOrderListener;
	}

	public War3MapViewer getViewer() {
		return this.viewer;
	}

	public WarsmashGdxMenuScreen getMenuScreen() {
		return this.menuScreen;
	}

	public boolean isDisposed() {
		return this.disposed;
	}

	/** Leaves the game as the Quit Mission button does: the score screen's exit, back to the menu or the launcher. */
	public void quitMission() {
		this.baseMeleeUI.quitMission();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.badlogic.gdx.ApplicationAdapter#create()
	 */
	@Override
	public void show() {

		final ByteBuffer tempByteBuffer = ByteBuffer.allocateDirect(4);
		tempByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		final IntBuffer temp = tempByteBuffer.asIntBuffer();

		this.sessionResources = SessionResources.begin();
		this.vaoGl = Gdx.gl30;
		this.vaoGl.glGenVertexArrays(1, temp);
		this.previousVao = WarsmashGdxGame.VAO;
		this.vao = temp.get(0);
		WarsmashGdxGame.VAO = this.vao;

		Gdx.gl30.glBindVertexArray(WarsmashGdxGame.VAO);

		final String renderer = Gdx.gl.glGetString(GL20.GL_RENDERER);
		Log.warn("Renderer: " + renderer);

		final Element cameraData = this.viewer.miscData.get("Camera");
		Element cameraListenerData = this.viewer.miscData.get("Listener");
		if (cameraListenerData == null) {
			cameraListenerData = new Element("Listener", new DataTable(null));
		}
		final CameraPreset[] cameraPresets = new CameraPreset[6];
		for (int i = 0; i < cameraPresets.length; i++) {
			cameraPresets[i] = new CameraPreset(cameraData.getFieldFloatValue("AOA", i),
					cameraData.getFieldFloatValue("FOV", i), cameraData.getFieldFloatValue("Rotation", i),
					cameraData.getFieldFloatValue("Rotation", i + cameraPresets.length),
					cameraData.getFieldFloatValue("Rotation", i + (cameraPresets.length * 2)),
					cameraData.getFieldFloatValue("Distance", i), cameraData.getFieldFloatValue("FarZ", i),
					cameraData.getFieldFloatValue("NearZ", i), cameraData.getFieldFloatValue("Height", i),
					cameraListenerData.getFieldFloatValue("ListenerDistance", i),
					cameraListenerData.getFieldFloatValue("ListenerAOA", i));
		}

		Log.info("Loaded");
		Gdx.gl30.glClearColor(0.0f, 0.0f, 0.0f, 1); // TODO remove white background
		Gdx.gl30.glEnable(GL30.GL_SCISSOR_TEST);

		final Scene portraitScene = this.viewer.addSimpleScene();
		this.uiScene = this.viewer.addSimpleScene();
		this.uiScene.alpha = true;
		if (ENABLE_AUDIO) {
			this.uiScene.enableAudio();
		}

//		this.mainModel = (MdxModel) this.viewer.load("UI\\Glues\\MainMenu\\MainMenu3D_exp\\MainMenu3D_exp.mdx",

		// libGDX stuff
		final int width = Gdx.graphics.getWidth();
		final int height = Gdx.graphics.getHeight();

		this.glyphLayout = new GlyphLayout();

		// Constructs a new OrthographicCamera, using the given viewport width and
		// height
		// Height is multiplied by aspect ratio.
		this.uiCamera = new OrthographicCamera();
		int aspect3By4Width;
		int aspect3By4Height;
		if (width < ((height * 4) / 3)) {
			aspect3By4Width = width;
			aspect3By4Height = (width * 3) / 4;
		}
		else {
			aspect3By4Width = (height * 4) / 3;
			aspect3By4Height = height;
		}
		this.uiViewport = new ExtendViewport(aspect3By4Width, aspect3By4Height, this.uiCamera);
		this.uiViewport.update(width, height);

		this.uiCamera.position.set(this.uiViewport.getMinWorldWidth() / 2, this.uiViewport.getMinWorldHeight() / 2, 0);
		this.uiCamera.update();

		this.batch = new FadingSpriteBatch();

//		this.consoleUITexture = new Texture(new DataSourceFileHandle(this.viewer.dataSource, "AlphaUi.png"));

		this.solidGreenTexture = ImageUtils.getAnyExtensionTexture(this.viewer.dataSource,
				"ReplaceableTextures\\TeamColor\\TeamColor06.blp");

		Gdx.input.setInputProcessor(this);
		// Android's Back would otherwise close the activity and drop the match without a word.
		Gdx.input.setCatchKey(Input.Keys.BACK, true);

		this.shapeRenderer = new ShapeRenderer();

		final Element cameraRatesElement = this.viewer.miscData.get("CameraRates");
		final CameraRates cameraRates = new CameraRates(cameraRatesElement.getFieldFloatValue("AOA"),
				cameraRatesElement.getFieldFloatValue("FOV"), cameraRatesElement.getFieldFloatValue("Rotation"),
				cameraRatesElement.getFieldFloatValue("Distance"), cameraRatesElement.getFieldFloatValue("Forward"),
				cameraRatesElement.getFieldFloatValue("Strafe"));
		final MeleeUI baseMeleeUI = new MeleeUI(this.viewer.mapMpq, this.uiViewport, this.uiScene, portraitScene,
				cameraPresets, cameraRates, this.viewer, new RootFrameListener() {
					@Override
					public void onCreate(final GameUI rootFrame) {
						WarsmashGdxMapScreen.this.viewer.setGameUI(rootFrame);
					}
				}, this.uiOrderListener, new Runnable() {
					@Override
					public void run() {
						// The movies PlayCinematic asked for play as the game is left, before the next level.
						final List<Movie> movies = Movies.find(WarsmashGdxMapScreen.this.viewer.dataSource,
								WarsmashGdxMapScreen.this.viewer.simulation.takeQueuedCinematics(),
								Gdx.graphics.getWidth());
						if (movies.isEmpty()) {
							leaveGame();
						}
						else {
							// The game's music ends with the game, not under its movies.
							WarsmashGdxMapScreen.this.meleeUI.gameClosed();
							Movies.play(movies, () -> Gdx.app.postRunnable(this::leaveGame));
						}
					}

					private void leaveGame() {
						final GameResult result = gameResult(
								WarsmashGdxMapScreen.this.scriptFailed ? "error" : localPlayerResult());
						if ("victory".equals(result.result)
								&& !WarsmashGdxMapScreen.this.viewer.isReplayPlayback()) {
							WarsmashGdxMapScreen.this.menuScreen.recordVictory(result.map);
						}
						// The menu picks up its own sounds and textures from here on.
						WarsmashGdxMapScreen.this.endSession();
						final WarsmashGdxMenuScreen.GameEndListener gameEndListener = WarsmashGdxMapScreen.this.menuScreen
								.getGameEndListener();
						final CGameExit exit = WarsmashGdxMapScreen.this.viewer.getGameExit();
						// A save to go on with (a hub left for a zone) is the engine's to load, not the launcher's.
						final boolean ended = !exit.hasNextLevel() || ((exit.getSavedGame() == null)
								&& "victory".equals(result.result)
								&& WarsmashGdxMapScreen.this.menuScreen.endsBefore(exit.getNextLevel()));
						if ((gameEndListener != null) && ended && (exit.getNextReplay() == null)) {
							final SavedGame.Loadability loadFailure = SavedGame.consumeLoadFailure();
							result.loadFailure = loadFailure == null ? null : loadFailure.name();
							WarsmashGdxMapScreen.this.screenManager.setScreen(null);
							CGameCache.dropUnsaved();
							gameEndListener.gameEnded(result);
							return;
						}
						WarsmashGdxMapScreen.this.menuScreen.onReturnFromGame(exit.isCampaignSelectScreenForced());
						WarsmashGdxMapScreen.this.screenManager.setScreen(WarsmashGdxMapScreen.this.menuScreen);
						if (WarsmashGdxMapScreen.this.scriptFailed) {
							WarsmashGdxMapScreen.this.menuScreen.showMapLoadError();
						}
						CGameCache.dropUnsaved();
						if (exit.getNextReplay() != null) {
							WarsmashGdxMapScreen.this.menuScreen.startReplay(exit.getNextReplay(),
									exit.getNextReplayStartTick(), exit.isNextReplayForced());
						}
						else if (exit.hasNextLevel()) {
							WarsmashGdxMapScreen.this.menuScreen.startMap(exit.getNextLevel(),
									exit.getNextComputerSlots(), exit.getSavedGame(), exit.isSavedGameLoadedByScript());
						}
					}
				});
		baseMeleeUI.setResultShownOutside(this.menuScreen.showsResultOutside());
		baseMeleeUI.setReforgedSkin(this.menuScreen.isReforgedLoadingScreens());
		this.baseMeleeUI = baseMeleeUI;
		final MeleeToggleUI toggleUI = new MeleeToggleUI(baseMeleeUI, Arrays.asList(baseMeleeUI));
		this.meleeUI = toggleUI;
		this.viewer.getCommandErrorListener().setDelegate(this.meleeUI);
		final LibGDXContentLayerModel libgdxContentModel = new LibGDXContentLayerModel(null, this.viewer, "",
				this.viewer.mapPathSolver, "");
		final ModelInstance libgdxContentInstance = libgdxContentModel.addInstance();
		libgdxContentInstance.setLocation(0f, 0f, -0.5f);
		libgdxContentInstance.setScene(this.uiScene);
		// The interface's 3D models (time of day, cooldowns) sit between the two layers, the cursor above both.
		final LibGDXContentLayerModelInstance modalContentInstance = (LibGDXContentLayerModelInstance) libgdxContentModel
				.addInstance();
		modalContentInstance.modal = true;
		modalContentInstance.setLocation(0f, 0f, 0.5f);
		modalContentInstance.setScene(this.uiScene);
		this.meleeUI.main();
		toggleUI.setCurrentUI(0);

		updateUIScene();

		resize(width, height);

		try {
			this.viewer.loadAfterUI();
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
		this.commonEnv = Jass2.loadCommon(this.viewer.mapMpq, this.uiViewport, this.uiScene, this.viewer, this.meleeUI,
				WarsmashConstants.JASS_FILE_LIST);
		final FileHandle savedGame = this.viewer.getSavedGameToLoad();
		if (savedGame != null) {
			try {
				this.commonEnv.loadSavedGame(this.viewer, savedGame);
			}
			catch (final IOException e) {
				// Nothing of the save has been applied yet. Starting the map instead would pass for a load, so the
				// game goes back to the menu, which tells why.
				Log.error("Could not load the saved game " + savedGame.name() + ": " + e.getMessage());
				SavedGame.loadFailed(savedGame, e);
				Gdx.app.postRunnable(this.baseMeleeUI::quitMission);
			}
		}
		else if (this.commonEnv.getScriptError() != null) {
			// The original game shows an error for a map whose script does not compile and does not play it.
			Log.error("The map's script does not compile: " + this.commonEnv.getScriptError().getMessage());
			this.scriptFailed = true;
			Gdx.app.postRunnable(this.baseMeleeUI::quitMission);
		}
		else {
			this.commonEnv.main();
		}
		this.viewer.preloadSoundsAtStart(this.baseMeleeUI.getInterfaceSoundKeys());
		DataErrors.logReport("map start");
	}

	public static DataSource parseDataSources(final DataTable warsmashIni) {
		final List<DataSourceDescriptor> dataSourcesList = new ArrayList<>();
		final List<String> allCascPrefixes = new ArrayList<>();
		parseDataSourceDescriptors(warsmashIni, dataSourcesList, allCascPrefixes);
		final DataSource baseCompoundDataSource = new CompoundDataSourceDescriptor(dataSourcesList).createDataSource();

		final List<DataSource> subdirDataSourcesList = new ArrayList<>();
//		Collections.reverse(allCascPrefixes);
		for (final String prefix : allCascPrefixes) {
			subdirDataSourcesList.add(new SubdirDataSource(baseCompoundDataSource, prefix + "\\"));
		}
		subdirDataSourcesList.add(baseCompoundDataSource);
		return new CompoundDataSource(subdirDataSourcesList);
	}

	public static void parseDataSourceDescriptors(final DataTable warsmashIni,
			final List<DataSourceDescriptor> dataSourcesList, final List<String> allCascPrefixes) {
		final Element dataSourcesConfig = warsmashIni.get("DataSources");
		for (int i = 0; i < dataSourcesConfig.size(); i++) {
			final String numberText = (i < 10 ? "0" : "") + i;
			final String type = dataSourcesConfig.getField("Type" + numberText);
			final String path = dataSourcesConfig.getField("Path" + numberText);
			switch (type) {
			case "Folder": {
				dataSourcesList.add(new FolderDataSourceDescriptor(path));
				break;
			}
			case "MPQ": {
				dataSourcesList.add(new MpqDataSourceDescriptor(path));
				break;
			}
			case "CASC": {
				final String prefixes = dataSourcesConfig.getField("Prefixes" + numberText);
				final String product = dataSourcesConfig.getField("Product" + numberText);
				final boolean oldFormat = dataSourcesConfig.getFieldValue("OldFormat" + numberText) == 1;
				final List<String> parsedPrefixes = Arrays.asList(prefixes.split(","));
				allCascPrefixes.addAll(parsedPrefixes);
				dataSourcesList.add(new CascDataSourceDescriptor(path, parsedPrefixes, product, oldFormat));
				break;
			}
			case "":
				continue;
			default:
				throw new RuntimeException("Unknown data source type: " + type);
			}
		}
	}

	private void updateUIScene() {
		this.tempRect.x = this.uiViewport.getScreenX();
		this.tempRect.y = this.uiViewport.getScreenY();
		this.tempRect.width = this.uiViewport.getScreenWidth();
		this.tempRect.height = this.uiViewport.getScreenHeight();
		this.uiScene.camera.viewport(this.tempRect);
		final float worldWidth = this.uiViewport.getWorldWidth();
		final float worldHeight = this.uiViewport.getWorldHeight();
		final float xScale = worldWidth / this.uiViewport.getMinWorldWidth();
		final float yScale = worldHeight / this.uiViewport.getMinWorldHeight();
		final float uiSceneWidth = 0.8f * xScale;
		final float uiSceneHeight = 0.6f * yScale;
		final float uiSceneX = ((0.8f - uiSceneWidth) / 2);
		final float uiSceneY = ((0.6f - uiSceneHeight) / 2);
		this.uiScene.camera.ortho(uiSceneX, uiSceneWidth + uiSceneX, uiSceneY, uiSceneHeight + uiSceneY, -1024f, 1024);
	}

	@Override
	public void render(final float delta) {
		this.uiCamera.update();
		Gdx.gl30.glEnable(GL30.GL_SCISSOR_TEST);
		// Like the game, which runs slower on a slow frame instead of catching up, the interface's fades do not skip
		// ahead: the first frames, drawn while textures are still being uploaded, would end a map's opening fade from
		// black before it is ever seen.
		final float deltaTime = Math.min(Gdx.graphics.getDeltaTime(), MAX_INTERFACE_FRAME_TIME);
		Gdx.gl30.glBindVertexArray(WarsmashGdxGame.VAO);
		this.meleeUI.update(deltaTime);
		this.viewer.updateAndRender();

		WarsmashGdxGame.leaveViewerState(this.viewer.webGL);

		Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE0);
		GammaOverlay.render(this.batch);
	}

	private void renderLibGDXContent(final boolean modal) {

		Gdx.gl30.glClear(GL30.GL_DEPTH_BUFFER_BIT);
		WarsmashGdxGame.leaveViewerState(this.viewer.webGL);

		Gdx.gl30.glActiveTexture(GL30.GL_TEXTURE0);

		this.uiViewport.apply();
		this.batch.setColor(1, 1, 1, 1);
		this.batch.setProjectionMatrix(this.uiCamera.combined);
		final int drawsBefore = this.batch.totalRenderCalls;
		this.batch.begin();
		if (modal) {
			this.meleeUI.renderModal(this.batch, this.glyphLayout);
		}
		else {
			this.meleeUI.render(this.batch, this.glyphLayout);
		}
		this.batch.end();
		PerfStats.interfaceDraws(this.batch.totalRenderCalls - drawsBefore);

		WarsmashGdxGame.enterViewerState();
	}

	@Override
	public void dispose() {
		if (this.disposed) {
			return;
		}
		this.disposed = true;
		endSession();
		DataErrors.logReport("map end");
		this.meleeUI.dispose();
		this.batch.dispose();
		this.shapeRenderer.dispose();
		if (this.sessionResources != null) {
			this.sessionResources.dispose();
		}
		this.viewer.dispose();
		final IntBuffer vaos = BufferUtils.newIntBuffer(1);
		vaos.put(0, this.vao);
		this.vaoGl.glDeleteVertexArrays(1, vaos);
		WarsmashGdxGame.VAO = this.previousVao;
		Gdx.gl30.glBindVertexArray(WarsmashGdxGame.VAO);
	}

	/** How the game ended for the local player: victory, defeat or tie, or quit when it had no result yet. */
	public String localPlayerResult() {
		try {
			final CPlayerGameResult result = this.viewer.simulation.getPlayer(this.viewer.getLocalPlayerIndex())
					.getRemovedResult();
			if ((result == null) || (result == CPlayerGameResult.NEUTRAL)) {
				return "quit";
			}
			return result.name().toLowerCase(Locale.ROOT);
		}
		catch (final RuntimeException e) {
			return "quit";
		}
	}

	/**
	 * How the game ended for the local player: the map, the race played, the time, the difficulty and the numbers the
	 * score screen shows.
	 */
	public GameResult gameResult(final String result) {
		final GameResult ended = new GameResult();
		ended.result = result;
		ended.map = this.viewer.getMapFileName();
		ended.campaign = this.viewer.getCampaign();
		ended.race = localPlayerRace();
		try {
			final CSimulation simulation = this.viewer.simulation;
			ended.seconds = (int) (simulation.getGameTurnTick() * WarsmashConstants.SIMULATION_STEP_TIME);
			final War3MapConfig config = this.viewer.getMapConfig();
			if (config != null) {
				final String name = config.getMapName();
				ended.mapName = name == null ? "" : name;
				final CMapDifficulty difficulty = config.getGameDifficulty();
				ended.difficulty = difficulty == null ? "" : difficulty.name().toLowerCase(Locale.ROOT);
			}
			final CPlayer player = simulation.getPlayer(this.viewer.getLocalPlayerIndex());
			final GameResult.Score score = new GameResult.Score();
			score.heroScore = player.getScore(CPlayerScore.HERO_TOTAL);
			score.heroesKilled = player.getScore(CPlayerScore.HEROES_KILLED);
			score.unitScore = player.getScore(CPlayerScore.UNIT_TOTAL);
			score.unitsTrained = player.getScore(CPlayerScore.UNITS_TRAINED);
			score.unitsKilled = player.getScore(CPlayerScore.UNITS_KILLED);
			score.structuresBuilt = player.getScore(CPlayerScore.STRUCT_BUILT);
			score.structuresRazed = player.getScore(CPlayerScore.STRUCT_RAZED);
			score.resourceScore = player.getScore(CPlayerScore.RESOURCE_TOTAL);
			score.totalScore = player.getScore(CPlayerScore.TOTAL);
			score.goldMined = player.getScore(CPlayerScore.GOLD_MINED_TOTAL);
			score.lumberHarvested = player.getScore(CPlayerScore.LUMBER_TOTAL);
			ended.score = score;
		}
		catch (final RuntimeException e) {
			Log.error("Unable to read the score of the game: " + e);
		}
		return ended;
	}

	/** The race the local player played, a random one as it was drawn; 0 when unknown. */
	public int localPlayerRace() {
		try {
			final CRace race = this.viewer.simulation.getPlayer(this.viewer.getLocalPlayerIndex()).getRace();
			return race == null ? 0 : race.getId();
		}
		catch (final RuntimeException e) {
			return 0;
		}
	}

	private void endSession() {
		SessionResources.end(this.sessionResources);
	}

	@Override
	public void resize(final int width, final int height) {
//		super.resize(width, height);

		this.uiViewport.update(width, height);
		this.uiCamera.position.set(this.uiViewport.getMinWorldWidth() / 2, this.uiViewport.getMinWorldHeight() / 2, 0);

		this.meleeUI.resize(width, height);
		updateUIScene();

	}

	@Override
	public boolean keyDown(final int keycode) {
		if ((keycode == Input.Keys.B) && Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) {
			Gdx.input.setCursorCatched(!Gdx.input.isCursorCatched());
		}
		if (keycode == Input.Keys.ENTER) {
			if (Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT)) {
				WarsmashUtils.toggleFullScreen();
				return true;
			}
		}
		if ((keycode == Input.Keys.ESCAPE) && this.viewer.skipModelCinematic()) {
			return true;
		}
		this.meleeUI.keyDown(keycode);
		return true;
	}

	/** Back from a platform that delivers it as an action rather than a key: the same as the Back key. */
	public void backPressed() {
		keyDown(Input.Keys.BACK);
		keyUp(Input.Keys.BACK);
	}

	@Override
	public boolean keyUp(final int keycode) {
		this.meleeUI.keyUp(keycode);
		return true;
	}

	@Override
	public boolean keyTyped(final char character) {
		return this.baseMeleeUI != null ? this.baseMeleeUI.keyTyped(character) : false;
	}

	@Override
	public boolean touchDown(final int screenX, final int screenY, final int pointer, final int button) {
		final float worldScreenY = this.viewer.canvas.getHeight() - screenY;

		if (this.meleeUI.touchDown(screenX, screenY, worldScreenY, button)) {
			return false;
		}
		return false;
	}

	@Override
	public boolean touchUp(final int screenX, final int screenY, final int pointer, final int button) {
		final float worldScreenY = this.viewer.canvas.getHeight() - screenY;

		if (this.meleeUI.touchUp(screenX, screenY, worldScreenY, button)) {
			return false;
		}
		return false;
	}

	@Override
	public boolean touchDragged(final int screenX, final int screenY, final int pointer) {
		final float worldScreenY = this.viewer.canvas.getHeight() - screenY;
		if (this.meleeUI.touchDragged(screenX, screenY, worldScreenY, pointer)) {
			return false;
		}
		return false;
	}

	@Override
	public boolean mouseMoved(final int screenX, final int screenY) {
		final float worldScreenY = this.viewer.canvas.getHeight() - screenY;
		if (this.meleeUI.mouseMoved(screenX, screenY, worldScreenY)) {
			return false;
		}
		return false;
	}

	@Override
	public boolean scrolled(final float amountX, final float amountY) {
		this.meleeUI.scrolled(amountX, amountY);
		return false;
	}

	@Override
	public boolean touchCancelled(final int screenX, final int screenY, final int pointer, final int button) {
		return false;
	}

	@Override
	public boolean isOverSlider(final int screenX, final int screenY) {
		return this.meleeUI.isOverSlider(screenX, screenY);
	}

	@Override
	public boolean isOverClickableUi(final int screenX, final int screenY) {
		return this.meleeUI.isOverClickableUi(screenX, screenY);
	}

	@Override
	public boolean isOverInventorySlot(final int screenX, final int screenY) {
		return this.meleeUI.isOverInventorySlot(screenX, screenY);
	}

	@Override
	public boolean isOverAutocastButton(final int screenX, final int screenY) {
		return this.meleeUI.isOverAutocastButton(screenX, screenY);
	}

	@Override
	public boolean hasActiveCommand() {
		return this.meleeUI.hasActiveCommand();
	}

	@Override
	public void screenToWorld(final int screenX, final int screenY, final float[] out) {
		this.meleeUI.screenToWorld(screenX, screenY, out);
	}

	@Override
	public void worldToScreen(final float[] world, final float[] out) {
		this.meleeUI.worldToScreen(world, out);
	}

	@Override
	public boolean isPlacingBuilding() {
		return this.meleeUI.isPlacingBuilding();
	}

	@Override
	public void cancelPointerDrag() {
		this.meleeUI.cancelPointerDrag();
	}

	@Override
	public void endTouchHover() {
		this.meleeUI.endTouchHover();
	}

	@Override
	public boolean skipCinematic() {
		return this.viewer.skipModelCinematic() || this.meleeUI.skipCinematic();
	}

	@Override
	public int touchOverlaySlotAt(final int screenX, final int screenY) {
		return this.meleeUI.touchOverlaySlotAt(screenX, screenY);
	}

	@Override
	public void selectControlGroupSlot(final int slot) {
		this.meleeUI.selectControlGroupSlot(slot);
	}

	@Override
	public void assignControlGroupSlot(final int slot, final boolean add) {
		this.meleeUI.assignControlGroupSlot(slot, add);
	}

	private static class Message {
		private final float time;
		private final String text;

		public Message(final float time, final String text) {
			this.time = time;
			this.text = text;
		}
	}

	private class LibGDXContentLayerModelInstance extends ModelInstance {
		private boolean modal;


		public LibGDXContentLayerModelInstance(final Model model) {
			super(model);
		}

		@Override
		public void updateAnimations(final float dt) {

		}

		@Override
		public void clearEmittedObjects() {

		}

		@Override
		protected void updateLights(final Scene scene2) {

		}

		@Override
		public void renderOpaque(final Matrix4 mvp) {

		}

		@Override
		public void renderTranslucent() {
			renderLibGDXContent(this.modal);
		}

		@Override
		public void load() {
		}

		@Override
		protected RenderBatch getBatch(final TextureMapper textureMapper2) {
			throw new UnsupportedOperationException("NOT API");
		}

		@Override
		public void setReplaceableTexture(final int replaceableTextureId, final String replaceableTextureFile) {

		}

		@Override
		public boolean isBatched() {
			return super.isBatched();
		}

		@Override
		protected void removeLights(final Scene scene2) {
			// TODO Auto-generated method stub

		}

		@Override
		public void setReplaceableTextureHD(final int replaceableTextureId, final String replaceableTextureFile) {
			// TODO Auto-generated method stub

		}

	}

	private class LibGDXContentLayerModel extends Model {

		public LibGDXContentLayerModel(final ModelHandler handler, final ModelViewer viewer, final String extension,
				final PathSolver pathSolver, final String fetchUrl) {
			super(handler, viewer, extension, pathSolver, fetchUrl);
			this.ok = true;
		}

		@Override
		protected ModelInstance createInstance(final int type) {
			return new LibGDXContentLayerModelInstance(this);
		}

		@Override
		protected void lateLoad() {
		}

		@Override
		protected void load(final InputStream src, final Object options) {
		}

		@Override
		protected void error(final Exception e) {
		}

	}

	@Override
	public void pause() {
		// A single player game waits while the app is away from the screen; a network game cannot stop for one
		// player. The desktop backend calls this whenever the window loses focus, and a game there keeps running.
		if ((Gdx.app.getType() == ApplicationType.Android) && !this.viewer.isNetworkGame()) {
			this.viewer.setGamePaused(War3MapViewer.PauseSource.BACKGROUND, true);
			Gdx.app.log("Warsmash", "Game paused in the background at step " + gameStep());
		}
	}

	@Override
	public void resume() {
		if (this.viewer.isGamePaused(War3MapViewer.PauseSource.BACKGROUND)) {
			this.viewer.setGamePaused(War3MapViewer.PauseSource.BACKGROUND, false);
			Gdx.app.log("Warsmash", "Game resumed at step " + gameStep());
		}
	}

	private int gameStep() {
		return this.viewer.simulation == null ? 0 : this.viewer.simulation.getGameTurnTick();
	}

	/**
	 * Saves the running game when the GL context was lost, so that it goes on from the save once the screens are
	 * made again.
	 *
	 * @return false for a network game, a game that is over (its score screen) or one that could not be saved
	 */
	public boolean saveAfterContextLoss(final FileHandle file) {
		if (this.disposed || (this.viewer.simulation == null) || this.viewer.isNetworkGame()
				|| this.viewer.isWorldHidden()) {
			return false;
		}
		try {
			return SavedGame.save(this.viewer, file);
		}
		catch (final RuntimeException e) {
			Log.error(e);
			return false;
		}
	}

	@Override
	public void hide() {
		Gdx.input.setCatchKey(Input.Keys.BACK, false);
		this.meleeUI.gameClosed();
		endSession();
		// Leaving the game happens in the middle of this screen's own frame, which still has to finish.
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				dispose();
			}
		});
	}
}
