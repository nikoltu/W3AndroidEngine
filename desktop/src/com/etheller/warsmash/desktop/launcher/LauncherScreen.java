package com.etheller.warsmash.desktop.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.etheller.warsmash.BuildInfo;
import com.etheller.warsmash.GlueBackdropRenderer;
import com.etheller.warsmash.WarsmashGdxMapScreen;
import com.etheller.warsmash.WarsmashGdxMenuScreen;
import com.etheller.warsmash.WarsmashGdxMultiScreenGame;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.launcher.CampaignChoice;
import com.etheller.warsmash.launcher.CampaignResult;
import com.etheller.warsmash.launcher.CustomGame;
import com.etheller.warsmash.launcher.CustomKeys;
import com.etheller.warsmash.launcher.DataFolderCheck;
import com.etheller.warsmash.launcher.DataPackage;
import com.etheller.warsmash.launcher.EngineSettings;
import com.etheller.warsmash.launcher.GameCatalog;
import com.etheller.warsmash.launcher.GameResult;
import com.etheller.warsmash.launcher.GlueText;
import com.etheller.warsmash.launcher.HotkeyCatalog;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.LauncherBoard;
import com.etheller.warsmash.launcher.LauncherKeys;
import com.etheller.warsmash.launcher.LauncherLog;
import com.etheller.warsmash.launcher.LauncherOptions;
import com.etheller.warsmash.launcher.LauncherStore;
import com.etheller.warsmash.launcher.LauncherStrings;
import com.etheller.warsmash.launcher.Profiles;
import com.etheller.warsmash.launcher.SharedDataSource;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.launcher.UpdateCheck;
import com.etheller.warsmash.movie.Movies;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.DataSourceFileHandle;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.FogSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.GameReplays;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignCatalog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMenuData;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.menu.CampaignMission;

/**
 * The launcher in the engine's window: the Android launcher's pages, skins and choices drawn with the engine, with
 * the window, mouse and keyboard of a desktop. A game starts in the same window and comes back here when it ends.
 */
public final class LauncherScreen implements Screen, InputProcessor {
	/** The launcher's own preferences on the desktop. */
	public static final String PREFERENCES = "WarsmashLauncher";
	static final String KEY_UI_SCALE = "desktopUiScale";
	static final String KEY_FULLSCREEN = "desktopFullscreen";
	static final String KEY_VSYNC = "desktopVsync";
	static final String KEY_WINDOW = "desktopWindow";
	/** The engine configuration the launcher writes when the player chooses the game data. */
	public static final String USER_INI = "warsmash.user.ini";
	public static final int MIN_WIDTH = 1280;
	public static final int MIN_HEIGHT = 720;

	private static final String[][] RAIL = { { "KEY_CAMPAIGN", "Campaign" },
		{ "KEY_LOAD_SAVED_GAME", "Load Saved Game" }, { "KEY_VIEW_REPLAY", "View Replay" },
		{ "KEY_CUSTOM_GAME", "Custom Games" }, { "KEY_LOCAL_AREA_NETWORK", "Local Area Network" },
		{ "KEY_OPTIONS", "Options" }, { "KEY_HELP", "Help" } };
	private static final int[] RAIL_KEYS = { Input.Keys.C, Input.Keys.L, Input.Keys.R, Input.Keys.U, Input.Keys.N,
		Input.Keys.O, Input.Keys.H };
	private static final boolean[] RAIL_OPEN = { true, true, true, true, false, true, true };

	private enum Page {
		HOME,
		CAMPAIGN,
		MISSIONS,
		CUSTOM,
		LOAD,
		REPLAY,
		OPTIONS,
		HELP,
		PROFILE
	}

	private enum Dialog {
		NONE,
		EXIT,
		MESSAGE,
		DELETE_SAVE,
		DELETE_REPLAY,
		RENAME_REPLAY,
		OVERWRITE_REPLAY,
		TRY_REPLAY,
		DELETE_PROFILE,
		RENAME,
		PORTRAIT,
		ADVANCED,
		UPDATE
	}

	private final WarsmashGdxMultiScreenGame game;
	private final String iniPath;
	private DataTable ini;
	private File iniFile;
	private final Gui gui = new Gui();
	/** The campaign mission's result the Missions page shows instead of the chapters, until Continue or Back. */
	private GameResult campaignResult;
	private final Preferences launcherPreferences;
	private final DesktopStore preferences;
	private final DesktopStore engineStore;
	private final File saves = new File("Save");
	private LauncherStrings strings;
	private Map<String, String> glue = new HashMap<>();
	private volatile DataSource data;
	/** The game's cursor, drawn once the game data is open. */
	private LauncherCursor cursor;
	private volatile boolean dataFailed;
	private volatile boolean dataReady;
	private LauncherArt art;
	private boolean skinChanged;
	private Profiles profiles;
	private GameCatalog catalog;
	private GameCatalog.Campaigns campaigns;
	private List<GameCatalog.MapEntry> maps;
	private GameCatalog.MapDetails mapDetails;
	private TextureRegion minimap;
	private List<GameCatalog.SaveEntry> saveList;
	private final CustomGame lobby = new CustomGame();
	private GlueBackdropRenderer renderer;
	private final GlueBackdropRenderer.Request[] shownScenes = new GlueBackdropRenderer.Request[2];
	private final String[] sceneKeys = new String[2];
	private long sceneRequests;
	/** The scene recordings playing, by layer. */
	private final SceneRecording[] recordings = new SceneRecording[2];
	/** Whether the game data has a scene recording, by its name. */
	private final Map<String, Boolean> recorded = new HashMap<>();
	private LauncherBoard board;

	/** The newer engine release the launcher found, once the check is done; the check runs once per launch. */
	private volatile UpdateCheck.Release updateRelease;
	private volatile boolean updateChecked;
	private boolean updateStarted;
	private boolean updateHandled;
	private Page page = Page.HOME;
	private Dialog dialog = Dialog.NONE;
	private String messageTitle;
	private String messageLine;
	private int optionsTab;
	/** What the radio rows of the shown options tab add to the row height, when their choices take a line of their own. */
	private float optionsExtraH;
	private int helpTab;
	private String profileShown;
	private final StringBuilder nameField = new StringBuilder();
	private final StringBuilder renameField = new StringBuilder();
	private final StringBuilder saveFilter = new StringBuilder();
	private Map<String, CCampaignProgress.Snapshot> profileProgress;
	private boolean launching;
	private WarsmashGdxMenuScreen menuScreen;
	private LaunchRequest launched;
	private Music music;
	private Sound click;
	private boolean playingMovie;
	private String dataStatus = "";
	/** The package import running for the game data page, or null. */
	private volatile PackageImport packageImport;
	/** The data folder whose unreadable files are being counted, so the count starts once. */
	private File readabilityWalk;
	private HotkeyCatalog hotkeys;
	private boolean readingHotkeys;
	private CustomKeys customKeys;
	private int keysRace = 1;
	private int keysUnit;
	private int keysCard;
	private String keysCommand;
	/** The command whose new key the next key press is, on the Controls page. */
	private String capturing;

	public LauncherScreen(final WarsmashGdxMultiScreenGame game, final DataTable ini, final String iniPath) {
		this.game = game;
		this.iniPath = iniPath;
		this.launcherPreferences = Gdx.app.getPreferences(PREFERENCES);
		this.preferences = new DesktopStore(this.launcherPreferences);
		this.engineStore = new DesktopStore(Gdx.app.getPreferences(Profiles.ENGINE_PREFERENCES));
		try (InputStream in = LauncherScreen.class.getResourceAsStream("/strings.xml")) {
			this.strings = new LauncherStrings(in);
		}
		catch (final IOException | RuntimeException e) {
			throw new IllegalStateException("The launcher's texts are missing from the class path", e);
		}
		useIni(ini, iniPath);
		this.profiles = new Profiles(this.engineStore, this.preferences, this.saves);
		openData();
	}

	/** The configuration in use: the one given on the command line, else the game data the player chose here. */
	private void useIni(final DataTable given, final String givenPath) {
		DataTable chosen = given;
		File chosenFile = new File(givenPath != null ? givenPath : "warsmash.ini");
		final File user = new File(USER_INI);
		if ((givenPath == null) && user.isFile()) {
			chosen = com.etheller.warsmash.desktop.DesktopLauncher.loadWarsmashIni(user.getPath());
			chosenFile = user;
		}
		this.ini = chosen;
		this.iniFile = chosenFile;
		WarsmashConstants.loadConstants(chosen.get("Emulator"), chosen);
	}

	/** The game data is opened away from the window; the pages show once it is read. */
	private void openData() {
		this.dataReady = false;
		this.dataFailed = false;
		final DataTable ini = this.ini;
		final Thread thread = new Thread(() -> {
			try {
				final DataSource opened = WarsmashGdxMapScreen.parseDataSources(ini);
				final Map<String, String> read = GlueText.read(opened);
				this.glue = read != null ? read : new HashMap<>();
				// The catalog reads the same data on its own thread and closes it.
				this.data = new SharedDataSource(opened);
				this.dataReady = true;
			}
			catch (final Exception | LinkageError e) {
				LauncherLog.w("The game data could not be read", e);
				this.dataFailed = true;
			}
		}, "LauncherData");
		thread.setDaemon(true);
		thread.start();
	}

	/** The art, catalog and scenes, once the data is open; on the window's thread. */
	private void dataOpened() {
		this.recorded.clear();
		this.art = new LauncherArt(this.data, reforgedSkin());
		this.catalog = new GameCatalog(this.ini, GameCatalog.dataKey(this.ini), this.saves,
				() -> GameCatalog.currentProfile(this.engineStore), new File("Logs/launcher-maps.txt"),
				Gdx.app::postRunnable, this.data);
		readCatalog();
		try {
			this.click = Gdx.audio.newSound(new DataSourceFileHandle(this.data, "Sound\\Interface\\MouseClick1.ogg"));
		}
		catch (final RuntimeException e) {
			this.click = null;
		}
		startRenderer();
		updateMusic();
	}

	private void readCatalog() {
		this.campaigns = null;
		this.maps = null;
		this.saveList = null;
		this.mapDetails = null;
		this.catalog.campaigns(found -> this.campaigns = found);
		this.catalog.maps(found -> this.maps = found);
		this.catalog.saves(found -> this.saveList = found);
		this.replayList = null;
		this.catalog.replays(found -> this.replayList = found);
	}

	private void startRenderer() {
		if ((this.renderer == null) && animated()) {
			this.renderer = new GlueBackdropRenderer(this.ini, new GlueBackdropRenderer.Listener() {
				@Override
				public void shown(final int layer, final long request) {
				}

				@Override
				public void failed(final int layer, final long request) {
				}
			}, this.data);
			this.renderer.create();
			this.sceneKeys[0] = null;
			this.sceneKeys[1] = null;
		}
	}

	private void stopRenderer() {
		if (this.renderer != null) {
			this.renderer.dispose();
			this.renderer = null;
		}
		for (int layer = 0; layer < this.recordings.length; layer++) {
			closeRecording(layer);
		}
	}

	private boolean animated() {
		return this.preferences.getBoolean(LauncherKeys.KEY_ANIMATED, true);
	}

	private boolean reforgedSkin() {
		return SkinArt.REFORGED_SKIN.equals(this.preferences.getString(LauncherKeys.KEY_SKIN, SkinArt.CLASSIC_SKIN))
				&& (this.data == null || this.data.has("webui\\common\\homescreen.jpg"));
	}

	private String s(final String name) {
		return this.strings.get(name);
	}

	private String glue(final String key, final String fallback) {
		final String value = this.glue.get(key.toUpperCase(Locale.ROOT));
		return value != null ? value : fallback;
	}

	private int uiPercent() {
		final int stored = this.preferences.getInt(KEY_UI_SCALE, 0);
		return stored > 0 ? stored : LauncherBoard.autoScale(Gdx.graphics.getHeight());
	}

	// ---------------------------------------------------------------- screen

	@Override
	public void show() {
		Gdx.input.setInputProcessor(this);
		if (Gdx.app instanceof LwjglApplication) {
			((LwjglApplication) Gdx.app).setCloseRequestListener(() -> {
				// Closing the window on Home is a plain intention; elsewhere it may be a slip.
				if ((this.art == null) || ((this.page == Page.HOME) && (this.dialog == Dialog.NONE))) {
					return true;
				}
				this.gui.closeMenu();
				this.dialog = Dialog.EXIT;
				return false;
			});
		}
		Gdx.graphics.setContinuousRendering(true);
		Gdx.graphics.setVSync(this.preferences.getBoolean(KEY_VSYNC, true));
	}

	@Override
	public void render(final float delta) {
		Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		if (this.playingMovie) {
			return;
		}
		checkUpdates();
		if ((this.art == null) && this.dataReady) {
			dataOpened();
		}
		this.board = LauncherBoard.window(Math.max(1, Gdx.graphics.getWidth()), Math.max(1, Gdx.graphics.getHeight()),
				uiPercent());
		if (this.art == null) {
			drawWaiting();
			return;
		}
		if (this.skinChanged) {
			changeSkin();
		}
		if (this.renderer != null) {
			this.renderer.render();
			Gdx.graphics.setContinuousRendering(true);
			// The scenes leave their own GL state behind; the batch expects the defaults.
			Gdx.gl30.glBindVertexArray(0);
			Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
			Gdx.gl.glDepthMask(true);
			Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
			Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
			Gdx.gl.glDisable(GL20.GL_CULL_FACE);
			Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		}
		this.gui.topLayer = this.gui.menuOpen() ? 99 : this.dialog != Dialog.NONE ? 1 : 0;
		this.gui.begin(this.art, this.board, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		final String[] scenes = { null, null };
		drawPage(scenes);
		if (this.dialog != Dialog.NONE) {
			this.gui.layer = 1;
			drawDialog();
			this.gui.layer = 0;
		}
		this.gui.end();
		showScenes(scenes);
		if (this.cursor == null) {
			this.cursor = new LauncherCursor(this.data);
		}
		this.cursor.render(Gdx.input.getX(), Gdx.input.getY());
	}

	/** Looks for a newer release beside the launcher and offers it over the first page that shows. */
	private void checkUpdates() {
		if (this.updateHandled) {
			return;
		}
		if (!this.updateStarted) {
			this.updateStarted = true;
			if (!this.preferences.getBoolean(LauncherKeys.KEY_UPDATE_CHECK, true)) {
				this.updateHandled = true;
				return;
			}
			final Thread thread = new Thread(() -> {
				this.updateRelease = UpdateCheck.fetchLatest(2000);
				this.updateChecked = true;
			}, "UpdateCheck");
			thread.setDaemon(true);
			thread.start();
			return;
		}
		if (!this.updateChecked) {
			return;
		}
		if (!UpdateCheck.offers(GameReplays.engineVersion(), this.updateRelease,
				this.preferences.getString(LauncherKeys.KEY_UPDATE_SKIPPED, ""))) {
			this.updateHandled = true;
		}
		else if ((this.art != null) && (this.dialog == Dialog.NONE)) {
			this.updateHandled = true;
			this.dialog = Dialog.UPDATE;
		}
	}

	private void disposeCursor() {
		if (this.cursor != null) {
			this.cursor.dispose();
			this.cursor = null;
		}
	}

	private void drawWaiting() {
		if (this.waitingArt == null) {
			this.waitingArt = new LauncherArt(new EmptySource(), false);
		}
		this.gui.begin(this.waitingArt, this.board, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		final String line = this.dataFailed ? s("game_unreadable") : s("launcher_reading");
		this.gui.text(line, 0, (this.board.height / 2) - 60, this.board.width, 120, this.board.tablet ? 48 : 32,
				0xFFFCD312, Align.center);
		if (this.dataFailed) {
			if (this.gui.button("data", s("options_find_installed"), (this.board.width / 2) - 400,
					(this.board.height / 2) + 100, 800, 112, 40, Gui.TONE_NORMAL)) {
				findInstalledGame();
			}
			if (this.gui.button("choose", s("options_change"), (this.board.width / 2) - 400,
					(this.board.height / 2) + 240, 800, 112, 40, Gui.TONE_NORMAL)) {
				chooseFolder();
			}
		}
		this.gui.end();
	}

	private LauncherArt waitingArt;

	@Override
	public void resize(final int width, final int height) {
		if ((width <= 0) || (height <= 0)) {
			return;
		}
		if (!Gdx.graphics.isFullscreen() && ((width < MIN_WIDTH) || (height < MIN_HEIGHT))) {
			Gdx.graphics.setWindowedMode(Math.max(MIN_WIDTH, width), Math.max(MIN_HEIGHT, height));
			return;
		}
		if (this.art != null) {
			this.art.dropFonts();
		}
		if (this.waitingArt != null) {
			this.waitingArt.dropFonts();
		}
		this.sceneKeys[0] = null;
		this.sceneKeys[1] = null;
		LauncherWindow.remember(this.launcherPreferences);
	}

	@Override
	public void pause() {
	}

	@Override
	public void resume() {
	}

	@Override
	public void hide() {
		if (this.cursor != null) {
			this.cursor.showArrow();
		}
		if (Gdx.app instanceof LwjglApplication) {
			((LwjglApplication) Gdx.app).setCloseRequestListener(null);
		}
		LauncherWindow.remember(this.launcherPreferences);
	}

	@Override
	public void dispose() {
		stopRenderer();
		disposeCursor();
		stopMusic();
		if (this.click != null) {
			this.click.dispose();
		}
		if (this.art != null) {
			this.art.dispose();
		}
		if (this.waitingArt != null) {
			this.waitingArt.dispose();
		}
		if (this.catalog != null) {
			this.catalog.close();
		}
		this.gui.dispose();
	}

	// ---------------------------------------------------------------- scenes

	/** The focus of a scene framed on its model, which stands at the model x of the recording's width. */
	private static final float MODEL_FOCUS = -1;

	/**
	 * A page's original scene, in board coordinates: the skin's recording of it when the game data has one, cropped
	 * at the focus across (or {@link #MODEL_FOCUS}), else the live 3D scene of the skin field, with its fog. With a
	 * model x (a part of the recorded width, else negative) the live scene is cropped around its model the way the
	 * recording is.
	 *
	 * @return what the layer shows, which {@link #showScenes} keeps
	 */
	private String scene(final int layer, final String scene, final float focus, final String skinField,
			final String fallbackField, final FogSettings fog, final int fogVersion, final float x, final float y,
			final float w, final float h, final float modelX) {
		final String name = SkinArt.sceneName(scene, this.art.reforged);
		if (hasRecording(name)) {
			if ((this.sceneKeys[layer] != null) && (this.renderer != null)) {
				this.renderer.show(layer, null);
			}
			this.sceneKeys[layer] = null;
			SceneRecording playing = this.recordings[layer];
			final boolean animated = animated();
			if ((playing == null) || !playing.name.equals(name) || (playing.animated != animated)) {
				closeRecording(layer);
				playing = new SceneRecording(this.data, recordingPath(name), name, animated);
				this.recordings[layer] = playing;
			}
			playing.draw(this.gui.batch, x, y, w, h,
					focus != MODEL_FOCUS ? focus : CampaignChoice.modelFocus(modelX, w / h, playing.aspect()));
			return name;
		}
		closeRecording(layer);
		return liveScene(layer, skinField, fallbackField, fog, fogVersion, x, y, w, h, modelX);
	}

	private static String recordingPath(final String name) {
		return "webui\\webms\\" + name + ".webm";
	}

	private boolean hasRecording(final String name) {
		Boolean has = this.recorded.get(name);
		if (has == null) {
			has = this.data.has(recordingPath(name));
			this.recorded.put(name, has);
		}
		return has;
	}

	/** How many of the menu and campaign scenes the game data has recorded for the skin. */
	private int recordings() {
		int count = 0;
		for (final String scene : SkinArt.SCENES) {
			if (hasRecording(SkinArt.sceneName(scene, this.art.reforged))) {
				count++;
			}
		}
		return count;
	}

	private void closeRecording(final int layer) {
		if (this.recordings[layer] != null) {
			this.recordings[layer].close();
			this.recordings[layer] = null;
		}
	}

	/** The live 3D scene of a skin field, for game data without the recordings. */
	private String liveScene(final int layer, final String skinField, final String fallbackField, final FogSettings fog,
			final int fogVersion, final float x, final float y, final float w, final float h, final float modelX) {
		final LauncherBoard b = this.board;
		final float s = b.scale;
		final Rectangle clip = new Rectangle(x * s, Gdx.graphics.getHeight() - ((y + h) * s), w * s, h * s);
		Rectangle viewport = clip;
		final float sceneAspect = 1920f / 810f;
		final float sceneW = h * sceneAspect;
		if ((modelX >= 0) && (sceneW > w)) {
			final float crop = CampaignChoice.modelFocus(modelX, w / h, sceneAspect);
			viewport = new Rectangle((x - (crop * (sceneW - w))) * s, clip.y, sceneW * s, h * s);
		}
		final String key = skinField + "|" + fallbackField + "|" + fogVersion + "|" + (fog == null ? "" : fog.hashCode())
				+ "|" + viewport + "|" + clip;
		if (!key.equals(this.sceneKeys[layer]) && (this.renderer != null)) {
			this.sceneKeys[layer] = key;
			this.shownScenes[layer] = new GlueBackdropRenderer.Request(++this.sceneRequests, skinField, fallbackField,
					fog, fogVersion, viewport, clip);
			this.renderer.show(layer, this.shownScenes[layer]);
		}
		return key;
	}

	private void showScenes(final String[] wanted) {
		for (int layer = 0; layer < 2; layer++) {
			if (wanted[layer] == null) {
				closeRecording(layer);
			}
			if ((wanted[layer] == null) && (this.sceneKeys[layer] != null) && (this.renderer != null)) {
				this.sceneKeys[layer] = null;
				this.renderer.show(layer, null);
			}
		}
	}

	private boolean roc() {
		return "roc".equals(this.preferences.getString(LauncherKeys.KEY_EDITION, "tft"));
	}

	// ---------------------------------------------------------------- pages

	private void drawPage(final String[] scenes) {
		switch (this.page) {
		case CAMPAIGN:
		case MISSIONS:
			campaignPages(scenes);
			break;
		case CUSTOM:
			editionBackdrop(scenes, true);
			customPage();
			shell(3, false);
			break;
		case LOAD:
			editionBackdrop(scenes, true);
			loadPage();
			shell(1, false);
			break;
		case REPLAY:
			editionBackdrop(scenes, true);
			replayPage();
			shell(2, false);
			break;
		case OPTIONS:
			editionBackdrop(scenes, true);
			optionsPage();
			shell(5, false);
			break;
		case HELP:
			editionBackdrop(scenes, true);
			helpPage();
			shell(6, false);
			break;
		case PROFILE:
			editionBackdrop(scenes, true);
			profilePage();
			shell(-1, false);
			break;
		default:
			editionBackdrop(scenes, false);
			homePage(scenes);
			break;
		}
	}

	/** The edition's main menu scene behind the page, darker behind the other pages than behind Home. */
	private void editionBackdrop(final String[] scenes, final boolean shaded) {
		final LauncherBoard b = this.board;
		final boolean roc = roc();
		scenes[0] = scene(0, roc ? "mainmenu" : "mainmenu_tft", roc ? 0.05f : 1,
				roc ? "GlueSpriteLayerBackground_V0" : "GlueSpriteLayerBackground_V1", "GlueSpriteLayerBackground", null,
				roc ? 0 : 1, 0, 0, b.width, b.height, -1);
		if (shaded) {
			this.gui.fill(0, 0, b.width, b.height, 0x73000000);
		}
	}

	/** Everything the pages share: the rail with the shown page's entry pressed, the logo, the profile, the footer. */
	private void shell(final int selected, final boolean home) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean reforged = this.art.reforged;
		final float railW = b.railW - 6;
		if (reforged) {
			g.cover(this.art.image(SkinArt.RAIL_BACKGROUND), 0, 0, railW, b.height, 0.5f);
			g.fill(0, 0, railW, b.height, 0x33000000);
		}
		else {
			g.gradient(0, 0, railW, b.height, 0xDB000000, 0xB3000000, true);
		}
		g.fill(railW, 0, 6, b.height, reforged ? 0xFF6A655C : 0xFF2C2416);
		if (b.tablet && home) {
			g.gradient(0, 0, railW, b.logoY + b.logoH + 30, 0xFF000000, 0x00000000, true);
		}
		final TextureRegion logo = this.art.image(roc() ? SkinArt.LOGO_ROC : SkinArt.LOGO_TFT);
		final int logoState = g.hit("logo", b.logoX, b.logoY, b.logoW, b.logoH);
		g.contain(logo, b.logoX, b.logoY, b.logoW, b.logoH, 1);
		if ((logoState & Gui.HOVER) != 0) {
			g.additive(true);
			g.contain(logo, b.logoX, b.logoY, b.logoW, b.logoH, 0.25f);
			g.additive(false);
			g.tip(s("launcher_edition_tip"));
		}
		if ((logoState & Gui.CLICK) != 0) {
			playClick();
			if (home) {
				this.preferences.edit().putString(LauncherKeys.KEY_EDITION, roc() ? "tft" : "roc").apply();
				updateMusic();
			}
			else {
				showPage(Page.HOME);
			}
		}
		for (int i = 0; i < RAIL.length; i++) {
			final float y = b.holderY + (i * b.holderPitch);
			if (railEntry("rail" + i, glue(RAIL[i][0], RAIL[i][1]), y, i == selected, RAIL_OPEN[i])) {
				railChosen(i);
			}
		}
		if (b.quitY > 0) {
			if (railEntry("quit", glue("KEY_QUIT", "Quit"), b.quitY, false, true)) {
				this.dialog = Dialog.EXIT;
			}
		}
		if (b.tablet) {
			footer();
		}
		chip();
	}

	private boolean railEntry(final Object id, final String label, final float y, final boolean selected,
			final boolean open) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final float hw = b.holderW;
		final float hh = b.holderH;
		final float x = b.holderX;
		final int tone = !open ? Gui.TONE_OFF : selected ? Gui.TONE_DOWN : Gui.TONE_NORMAL;
		final float size = hh * 0.594f * 0.52f;
		final boolean clicked;
		if (this.art.reforged) {
			g.fill((x + (hw * 0.04f)) - 6, (y + (hh * 0.14f)) - 6, (hw * 0.92f) + 12, (hh * 0.72f) + 12, 0x8C000000);
			clicked = g.button(id, label, x + (hw * 0.04f), y + (hh * 0.14f), hw * 0.92f, hh * 0.72f, size, tone);
		}
		else {
			g.image(SkinArt.HOLDER, x, y, hw, hh);
			clicked = g.button(id, label, x + (hw * 0.0455f), y + (hh * 0.188f), hw * 0.909f, hh * 0.594f, size, tone);
		}
		if (!open && g.over(x, y, hw, hh)) {
			g.tip(s("launcher_later"));
		}
		if (clicked) {
			playClick();
		}
		return clicked;
	}

	private void railChosen(final int entry) {
		switch (entry) {
		case 0:
			showPage(Page.CAMPAIGN);
			break;
		case 1:
			showPage(Page.LOAD);
			break;
		case 2:
			this.catalog.replays(found -> this.replayList = found);
			showPage(Page.REPLAY);
			break;
		case 3:
			showPage(Page.CUSTOM);
			break;
		case 5:
			this.optionsTab = 0;
			showPage(Page.OPTIONS);
			break;
		case 6:
			this.helpTab = 0;
			showPage(Page.HELP);
			break;
		default:
			break;
		}
	}

	private void footer() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final float shift = 1848 - b.height;
		final float y = 1600 - shift;
		g.fill(24, y, 592, 88, 0x8C000000);
		final boolean reforgedData = this.data.has("webui\\common\\homescreen.jpg");
		float x = 44;
		for (final String[] item : new String[][] { { s("launcher_classic"), "1" },
			{ s("launcher_reforged"), reforgedData ? "1" : "0" } }) {
			g.fill(x, y + 34, 20, 20, "1".equals(item[1]) ? SkinArt.Palette.OK : SkinArt.Palette.OFF);
			x += 34;
			g.text(item[0], x, y, 200, 88, 28, 0xFFE6E0D2, Align.left);
			x += g.width(item[0], 28) + 28;
		}
		if (g.over(24, y, 300, 88)) {
			g.tip(s("launcher_data_found").replace("%1$s", reforgedData ? "Classic, Reforged" : "Classic"));
		}
		final boolean on = this.preferences.getBoolean(LauncherKeys.KEY_MUSIC, true);
		g.text(s("launcher_music"), 380, y, 140, 88, 28, 0xFFE6E0D2, Align.right);
		if (g.check("music", 540, y + 18, 52, on)) {
			this.preferences.edit().putBoolean(LauncherKeys.KEY_MUSIC, !on).apply();
			updateMusic();
		}
		g.text(versionLine(), 24, 1730 - shift, 592, 48, 24, SkinArt.Palette.CAPTION, Align.left);
	}

	private String versionLine() {
		final BuildInfo build = BuildInfo.current();
		return build.displayVersion() + (build.isOfficial() ? "" : " - " + s("build_unofficial")) + " ("
				+ dataVersion() + ")";
	}

	private String dataVersion() {
		final File folder = dataFolder();
		if (folder == null) {
			return s("launcher_data_version_unknown");
		}
		final String version = installedGame()
				? DataFolderCheck.describe(DataFolderCheck.Kind.CASC, folder).replace("version ", "")
				: DataFolderCheck.folderGameVersion(folder);
		return (version == null) || version.isEmpty() ? s("launcher_data_version_unknown") : version;
	}

	private void chip() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final String name = this.profiles.current();
		final int state = g.hit("chip", b.chipX, b.chipY, Math.min(b.tablet ? 1400 : 900, b.width - b.chipX), b.chipH);
		g.fill(b.chipX - 5, b.chipY - 5, b.chipH + 10, b.chipH + 10, 0xFF6D6658);
		g.fill(b.chipX - 3, b.chipY - 3, b.chipH + 6, b.chipH + 6, 0xFF1A1A1A);
		g.cover(this.art.portrait(portrait(name)), b.chipX, b.chipY, b.chipH, b.chipH, 0.5f);
		g.text(name, b.chipX + b.chipH + (b.tablet ? 28 : 18), b.chipY, Math.min(b.tablet ? 1400 : 900, b.width - b.chipX),
				b.chipH, b.name, (state & Gui.HOVER) != 0 ? this.gui.palette.gold : this.gui.palette.text, Align.left);
		if ((state & Gui.CLICK) != 0) {
			playClick();
			this.profileShown = null;
			showPage(Page.PROFILE);
		}
	}

	private int portrait(final String name) {
		final int portrait = this.profiles.portrait(name);
		return Math.max(0, Math.min(SkinArt.portraitCount(this.art.reforged) - 1, portrait));
	}

	// ---------------------------------------------------------------- Home

	private void homePage(final String[] scenes) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float bw = b.bannerW;
		final float bx = b.width - bw;
		final float fr = b.frame;
		final Banner next = banner();
		// The Reforged scenes have their figures near the middle; the classic ones are framed on the model.
		scenes[1] = scene(1, next.scene, this.art.reforged ? 0.3f : MODEL_FOCUS, next.background, null, next.fog, 0,
				bx + (fr / 2), fr / 2, bw - fr, b.height - fr, CampaignChoice.modelX(next.scene));
		g.gradient(bx, b.height * 0.46f, bw, b.height * 0.38f, 0x00000000, 0xE6000000, true);
		g.fill(bx, b.height * 0.84f, bw, b.height * 0.16f, 0xE6000000);
		final LauncherArt.Frame frame = this.art.frame(SkinArt.PANEL_BORDER);
		if (frame != null) {
			g.nine(frame, bx, 0, bw, b.height, fr);
		}
		final float ix = bx + (t ? 64 : 30);
		final float iw = bw - (t ? 128 : 60);
		final float bh = t ? 112 : 96;
		final float by = b.height - (t ? 72 : 24) - bh;
		g.text(s("launcher_continue_campaign"), ix, by - (t ? 300 : 178), iw, t ? 44 : 30, t ? 32 : 22,
				g.palette.gold, Align.left);
		g.lines(next.title, ix, by - (t ? 250 : 150), iw, t ? 140 : 84, t ? 60 : 34, g.palette.text, Align.left);
		g.text(next.line, ix, by - (t ? 88 : 50), iw, t ? 40 : 28, t ? 30 : 20, g.palette.text2, Align.left);
		if (g.button("continue", glue("CONTINUE", "Continue"), ix, by, iw, bh, t ? 44 : 30, Gui.TONE_NORMAL)) {
			playClick();
			continueCampaign();
		}
		shell(RAIL.length, true);
	}

	/** What the Continue Campaign banner shows: the next mission, or what it showed last. */
	private static final class Banner {
		String scene = "roc_prologue";
		String background = "TutorialBackdrop";
		FogSettings fog;
		String title = "";
		String line = "";
		CampaignCatalog.Entry entry;
		int mission;
	}

	private Banner banner() {
		final Banner next = new Banner();
		if (this.campaigns != null) {
			final CampaignCatalog catalog = this.campaigns.catalog;
			final CCampaignProgress.Snapshot progress = this.campaigns.progress;
			final CampaignCatalog.Entry entry = CampaignChoice.playedCampaign(catalog, progress);
			if ((entry != null) && !entry.getData().getMissions().isEmpty()) {
				final List<CampaignMission> missions = entry.getData().getMissions();
				final int mission = Math.max(0, CampaignChoice.furthestMission(entry, progress));
				next.entry = entry;
				next.mission = mission;
				next.scene = CampaignChoice.campaignScene(entry.getSection());
				next.background = entry.getData().getBackground();
				next.fog = entry.getData().getBackgroundFogSettings();
				next.title = glue(missions.get(mission).getMissionName(), missions.get(mission).getMissionName());
				next.line = GlueText.plain(glue(entry.getData().getHeader(), entry.getData().getHeader())) + " \u00b7 "
						+ GlueText.plain(glue(missions.get(mission).getHeader(), missions.get(mission).getHeader()));
				return next;
			}
		}
		final String[] parts = this.preferences.getString(LauncherKeys.KEY_BANNER, "").split("\t", -1);
		if (parts.length == 4) {
			next.scene = parts[0];
			next.background = parts[1];
			next.title = parts[2];
			next.line = parts[3];
			return next;
		}
		next.title = glue("CAMP_MAP_PROLOGUE01", "");
		final String campaign = glue("CAMP_TUTORIAL_NAME", "");
		final String chapter = glue("CAMP_CHAPTER_1", s("launcher_chapter_one"));
		next.line = campaign.isEmpty() ? chapter : campaign + " \u00b7 " + chapter;
		return next;
	}

	private void continueCampaign() {
		final Banner next = banner();
		if (next.entry == null) {
			showPage(Page.CAMPAIGN);
			return;
		}
		this.preferences.edit().putString(LauncherKeys.KEY_BANNER, next.scene + "\t" + next.background + "\t"
				+ next.title.replace('\t', ' ') + "\t" + next.line.replace('\t', ' ')).apply();
		launchMission(next.entry, next.mission, Page.HOME);
	}

	// ---------------------------------------------------------------- Campaign and Missions

	private CampaignCatalog.Entry chosenCampaign() {
		return CampaignChoice.chosenCampaign(this.campaigns.catalog, this.campaigns.progress,
				this.preferences.contains(LauncherKeys.KEY_CAMPAIGN)
						? this.preferences.getInt(LauncherKeys.KEY_CAMPAIGN, 0)
						: null);
	}

	private void campaignPages(final String[] scenes) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		if (this.campaigns == null) {
			editionBackdrop(scenes, true);
			g.text(s("game_loading_list"), b.contentX, b.contentY, b.contentW, 100, b.body, g.palette.text2,
					Align.center);
			shell(0, false);
			return;
		}
		final CampaignCatalog.Entry entry = chosenCampaign();
		scenes[0] = scene(0, entry != null ? CampaignChoice.campaignScene(entry.getSection()) : "roc_prologue", 0.22f,
				entry != null ? entry.getData().getBackground() : "TutorialBackdrop", null,
				entry != null ? entry.getData().getBackgroundFogSettings() : null, 0, 0, 0, b.width, b.height, -1);
		g.gradient(b.width * 0.4f, 0, b.width * 0.6f, b.height, 0x00000000, 0xC7000000, false);
		if ((this.page == Page.MISSIONS) && (entry != null) && (this.campaignResult != null)) {
			if (this.art.reforged) {
				resultPage(entry, this.campaignResult);
			}
			else {
				classicResultPage(entry, this.campaignResult);
			}
		}
		else if ((this.page == Page.MISSIONS) && (entry != null)) {
			missionsPage(entry);
		}
		else {
			campaignPage(entry);
		}
		shell(0, false);
	}

	private float listX() {
		return this.board.contentX + this.board.campaignListX();
	}

	/** A campaign or mission row: the arrow, the header in gold and the name under it. */
	private boolean campaignRow(final Object id, final String header, final String name, final boolean chosen,
			final float x, final float y, final float w, final float rowH) {
		return campaignRow(id, header, name, chosen, false, x, y, w, rowH);
	}

	/** A row; a muted one, of a campaign still to come, is drawn without colour, dimmed, and cannot be chosen. */
	private boolean campaignRow(final Object id, final String header, final String name, final boolean chosen,
			final boolean muted, final float x, final float y, final float w, final float rowH) {
		final Gui g = this.gui;
		final boolean t = this.board.tablet;
		final float hs = t ? 30 : 20;
		final float ns = t ? 50 : 32;
		final float a = Math.min(rowH * 0.62f, 72);
		final int state = muted ? 0 : g.hit(id, x, y, w, rowH);
		if ((state & Gui.HOVER) != 0) {
			g.image(this.art.image(SkinArt.ROW_HIGHLIGHT), x, y, w, rowH, 0.5f);
		}
		final boolean reforged = this.art.reforged && this.art.has(SkinArt.CHEVRON_RIGHT);
		final float alpha = muted ? 0.5f : 1;
		if (reforged) {
			// The Reforged campaign list's arrow: a small blue button with a gold chevron, its text in capitals.
			final float button = a * 0.78f;
			final float bx = x + ((a - button) / 2);
			final float by = y + ((rowH - button) / 2);
			g.drawButton(bx, by, button, button, muted ? Gui.TONE_OFF : Gui.TONE_NORMAL, alpha);
			g.image(muted ? this.art.muted(SkinArt.CHEVRON_RIGHT) : this.art.image(SkinArt.CHEVRON_RIGHT),
					bx + (button * 0.22f), by + (button * 0.22f), button * 0.56f, button * 0.56f, alpha);
		}
		else {
			g.image(muted ? this.art.muted(SkinArt.CAMPAIGN_ARROW) : this.art.image(SkinArt.CAMPAIGN_ARROW), x,
					y + ((rowH - a) / 2), a, a, alpha);
		}
		final String headerText = reforged ? upper(glue(header, header)) : glue(header, header);
		final String nameText = reforged ? upper(glue(name, name)) : glue(name, name);
		g.text(headerText, x + a + 20, y + (rowH * 0.08f), w - a - 20, hs * 1.4f, hs,
				muted ? Gui.muted(g.palette.gold) : g.palette.gold, Align.left);
		final int nameColour = chosen ? g.palette.gold : g.palette.text;
		final boolean cut = g.text(nameText, x + a + 20, y + (rowH * 0.08f) + (hs * 1.3f), w - a - 20, ns * 1.3f,
				ns, muted ? Gui.muted(nameColour) : nameColour, Align.left);
		if (cut && ((state & Gui.HOVER) != 0)) {
			g.tip(GlueText.plain(glue(name, name)));
		}
		return (state & Gui.CLICK) != 0;
	}

	/** A section header of the campaign list is this part of a row high. */
	private static final float SECTION_HEIGHT = 0.62f;

	/** The header and name of each Forsaken Kingdom campaign, listed as still to come. */
	private static final String[][] FORSAKEN_NAMES = { { "campaign_fk_prologue_header", "campaign_fk_prologue" },
		{ "campaign_fk_forsaken_header", "campaign_fk_forsaken" } };

	private static float lineHeight(final CampaignChoice.ListLine line, final float rowH) {
		switch (line.kind) {
		case CampaignChoice.ListLine.GAP:
			return rowH / 2;
		case CampaignChoice.ListLine.SECTION:
			return rowH * SECTION_HEIGHT;
		default:
			return rowH;
		}
	}

	/**
	 * A section header of the campaign list: the section's name in gold capitals, followed by a thin line on the
	 * classic skin, between the Reforged heading ornament and its mirror on the Reforged one.
	 */
	private void sectionHeader(final int section, final float x, final float y, final float w, final float h) {
		final Gui g = this.gui;
		final float size = h * 0.46f;
		final float gap = size * 0.45f;
		final float ornamentW = size * 2.9f;
		final TextureRegion ornament = this.art.reforged ? this.art.image(SkinArt.HEADER_ORNAMENT) : null;
		float at = x;
		if (ornament != null) {
			g.contain(ornament, at, y + ((h - size) / 2), ornamentW, size, 1);
			at += ornamentW + gap;
		}
		final String name = s(section == CampaignChoice.SECTION_FROZEN ? "profile_frozen"
				: section == CampaignChoice.SECTION_FORSAKEN ? "campaign_forsaken" : "profile_reign");
		at = spaced(upper(name), at, y, h, size, size * 0.08f, g.palette.gold) + gap;
		if (section == CampaignChoice.SECTION_FORSAKEN) {
			final String soon = s("campaign_coming_soon");
			final float soonW = g.width(soon, size * 0.8f);
			g.text(soon, at, y, soonW + 2, h, size * 0.8f, g.palette.text2, Align.left);
			at += soonW + gap;
		}
		if (ornament != null) {
			final float scale = Math.min(ornamentW / ornament.getRegionWidth(), size / ornament.getRegionHeight());
			final float shownW = ornament.getRegionWidth() * scale;
			final float shownH = ornament.getRegionHeight() * scale;
			// A negative width draws the left ornament mirrored.
			g.image(ornament, at + ((ornamentW + shownW) / 2), y + ((h - shownH) / 2), -shownW, shownH, 1);
		}
		else if (at < (x + w)) {
			final int line = g.palette.line;
			g.gradient(at, y + ((h - 2) / 2), (x + w) - at, 2, line, line & 0x00FFFFFF, false);
		}
	}

	/** A line of letters spaced apart, as the section headers have it; gives where it ends. */
	private float spaced(final String text, final float x, final float y, final float h, final float size,
			final float spacing, final int argb) {
		float at = x;
		for (int i = 0; i < text.length(); i++) {
			final String letter = text.substring(i, i + 1);
			final float letterW = this.gui.width(letter, size);
			this.gui.text(letter, at, y, letterW + 2, h, size, argb, Align.left);
			at += letterW + spacing;
		}
		return at - spacing;
	}

	private void campaignPage(final CampaignCatalog.Entry chosen) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final CampaignCatalog catalog = this.campaigns.catalog;
		final CCampaignProgress.Snapshot progress = this.campaigns.progress;
		final float rowH = t ? 135 : 96;
		final float w = t ? 924 : 600;
		final float x = listX();
		final float top = b.contentY + 16;
		final float listH = (b.height - 8) - top;
		final List<CampaignChoice.ListLine> lines = CampaignChoice.campaignList(catalog, progress);
		float contentH = 0;
		for (final CampaignChoice.ListLine line : lines) {
			contentH += lineHeight(line, rowH);
		}
		final float offset = g.beginList("campaigns", x, top, w, listH, contentH);
		float y = top - offset;
		for (final CampaignChoice.ListLine line : lines) {
			if (line.kind == CampaignChoice.ListLine.SECTION) {
				sectionHeader(line.index, x, y, w, rowH * SECTION_HEIGHT);
			}
			else if (line.kind == CampaignChoice.ListLine.COMING) {
				final String[] names = FORSAKEN_NAMES[line.index];
				campaignRow(null, s(names[0]), s(names[1]), false, true, x, y, w, rowH);
			}
			else if (line.kind == CampaignChoice.ListLine.CAMPAIGN) {
				final CampaignCatalog.Entry entry = line.entry;
				final CampaignMenuData data = entry.getData();
				if (campaignRow("camp" + entry.getNumber(), data.getHeader(), data.getName(), entry == chosen, x, y,
						w, rowH)) {
					playClick();
					if (this.preferences.getInt(LauncherKeys.KEY_CAMPAIGN, -1) != entry.getNumber()) {
						this.preferences.edit().putInt(LauncherKeys.KEY_CAMPAIGN, entry.getNumber())
								.remove(LauncherKeys.KEY_MISSION).apply();
					}
					showPage(Page.MISSIONS);
				}
			}
			y += lineHeight(line, rowH);
		}
		g.endList();
		if (lines.isEmpty()) {
			g.text(s("game_no_campaigns"), x, top, w, 100, b.body, g.palette.text2, Align.left);
		}
		// The Reforged campaign screen asks for the version and difficulty on the chosen campaign's page.
		if (!this.art.reforged) {
			difficultyBoxes();
		}
	}

	/** The campaign version and difficulty boxes at the bottom left, their labels above their menus. */
	private void difficultyBoxes() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float edge = t ? 38 : 14;
		final float pad = t ? 24 : 12;
		final float inner = edge + pad;
		final float menuH = t ? 98 : 80;
		final float boxW = t ? 820 : 508;
		final float label = t ? 36 : 24;
		final float labelH = t ? 48 : 32;
		final float gap = t ? 12 : 8;
		final float descSize = t ? 26 : 19;
		final float descLine = t ? 36 : 26;
		final float descH = 2 * descLine;
		final float versionH = (2 * inner) + labelH + gap + menuH;
		final float boxH = versionH + (t ? 20 : 12) + descH;
		final float dy = (t ? b.contentY + b.contentH : b.height - 8) - boxH;
		final float dx = b.contentX;
		final float innerW = boxW - (2 * inner);
		if (this.campaigns.catalog.hasReforgedMaps()) {
			final float vy = dy - 24 - versionH;
			g.edit(dx, vy, boxW, versionH, edge, 0);
			g.text(glue("CAMPAIGN_VERSION", s("launcher_campaign_maps")), dx + inner, vy + inner, innerW, labelH, label,
					g.palette.gold, Align.left);
			final String profile = this.profiles.current();
			final int version = LaunchRequest.CAMPAIGN_MAPS_CLASSIC.equals(this.profiles.campaignMaps(profile)) ? 1 : 0;
			g.dropdown("version", glueList(LauncherKeys.CAMPAIGN_MAPS, 1), version, dx + inner, vy + inner + labelH + gap,
					innerW, menuH, t ? 34 : 26, true, null,
					choice -> this.profiles.setCampaignMaps(profile, LauncherKeys.CAMPAIGN_MAPS[choice][0]));
		}
		g.edit(dx, dy, boxW, boxH, edge, 0);
		g.text(glue("SELECT_DIFFICULTY_LEVEL", "Difficulty Level:"), dx + inner, dy + inner, innerW, labelH, label,
				g.palette.gold, Align.left);
		final String current = this.preferences.getString(LauncherKeys.KEY_DIFFICULTY,
				LauncherKeys.DIFFICULTIES[0][0]);
		int selected = 0;
		for (int i = 0; i < LauncherKeys.DIFFICULTIES.length; i++) {
			if (LauncherKeys.DIFFICULTIES[i][0].equals(current)) {
				selected = i;
			}
		}
		final float my = dy + inner + labelH + gap;
		g.dropdown("difficulty", glueList(LauncherKeys.DIFFICULTIES, 1), selected, dx + inner, my, innerW, menuH,
				t ? 34 : 26, true, null, choice -> this.preferences.edit()
						.putString(LauncherKeys.KEY_DIFFICULTY, LauncherKeys.DIFFICULTIES[choice][0]).apply());
		g.lines(glue("DIFFICULTY_TEXT", ""), dx + inner, my + menuH + (t ? 20 : 12), innerW, descH + 8, descSize,
				g.palette.text, Align.left);
	}

	private String[] glueList(final String[][] table, final int keyColumn) {
		final String[] labels = new String[table.length];
		for (int i = 0; i < table.length; i++) {
			labels[i] = glue(table[i][keyColumn], table[i][keyColumn + 1]);
		}
		return labels;
	}

	private void missionsPage(final CampaignCatalog.Entry entry) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final boolean reforged = this.art.reforged;
		final CCampaignProgress.Snapshot progress = this.campaigns.progress;
		final List<CampaignMission> missions = entry.getData().getMissions();
		int chosen = this.preferences.getInt(LauncherKeys.KEY_MISSION, -1);
		if ((chosen < 0) || (chosen >= missions.size()) || !CampaignCatalog.isMissionOpen(entry, chosen, progress)) {
			chosen = Math.max(0, CampaignChoice.furthestMission(entry, progress));
		}
		final float rowH = reforged ? (t ? 124 : 84) : (t ? 135 : 96);
		final float w = t ? 924 : 600;
		final float x = listX();
		final float top = b.contentY + 16;
		final float listH = (b.height - 8) - top;
		final CampaignMenuData data = entry.getData();
		final List<Object[]> rows = new ArrayList<>();
		addCinematic(rows, data.getIntroCinematic());
		if (progress.isOpeningCinematicAvailable(entry.getNumber())) {
			addCinematic(rows, data.getOpenCinematic());
		}
		for (int i = 0; i < missions.size(); i++) {
			if (CampaignCatalog.isMissionOpen(entry, i, progress)) {
				rows.add(new Object[] { missions.get(i), i });
			}
		}
		if (progress.isEndingCinematicAvailable(entry.getNumber())) {
			addCinematic(rows, data.getEndCinematic());
		}
		final float contentH = rows.size() * rowH;
		float listTop = top;
		if (reforged) {
			// As on the Reforged campaign screen, a short chapter list stands level with the campaign's name.
			final float middle = campaignBlock(entry);
			listTop = Math.max(top, Math.min((top + listH) - contentH, middle - (contentH / 2)));
		}
		final float offset = g.beginList("missions" + entry.getNumber(), x, listTop, w, (top + listH) - listTop,
				contentH);
		float y = listTop - offset;
		for (final Object[] row : rows) {
			final CampaignMission mission = (CampaignMission) row[0];
			final int index = row[1] == null ? -1 : (Integer) row[1];
			final boolean isChosen = index == chosen;
			final boolean clicked;
			if (reforged) {
				final boolean won = (index >= 0) && CampaignCatalog.isMissionWon(entry, index, progress);
				clicked = chapterRow("m" + index + mission.getMissionName(), mission, isChosen,
						index < 0 ? SkinArt.CHAPTER_CINEMATIC : won ? SkinArt.CHAPTER_WON : SkinArt.CHAPTER_OPEN, x, y,
						w, rowH);
			}
			else {
				clicked = campaignRow("m" + index + mission.getMissionName(), mission.getHeader(),
						mission.getMissionName(), isChosen, x, y, w, rowH);
			}
			if (clicked) {
				playClick();
				if (index < 0) {
					playMovies(mission.getMovies());
				}
				else {
					launchMission(entry, index, Page.MISSIONS);
				}
			}
			y += rowH;
		}
		g.endList();
		if (!reforged) {
			classicName(data);
		}
		if (g.button("back", glue("BACK", s("launcher_back")), b.backX, b.backY, b.backW, b.backH, t ? 36 : 30,
				Gui.TONE_NORMAL)) {
			playClick();
			showPage(Page.CAMPAIGN);
		}
	}

	/** The classic campaign's header and name, centred under the scene left of the list. */
	private void classicName(final CampaignMenuData data) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final float nameW = (listX() - 60) - b.contentX;
		if (b.tablet) {
			g.text(glue(data.getHeader(), ""), b.contentX, (b.contentY + b.contentH) - 158, nameW, 48, 40,
					g.palette.gold, Align.center);
			g.text(glue(data.getName(), ""), b.contentX, (b.contentY + b.contentH) - 106, nameW, 86, 72,
					g.palette.text, Align.center);
		}
		else {
			g.text(glue(data.getHeader(), ""), b.contentX, (b.contentY + b.contentH) - 124, nameW, 28, 24,
					g.palette.gold, Align.center);
			g.text(glue(data.getName(), ""), b.contentX, (b.contentY + b.contentH) - 92, nameW, 50, 40,
					g.palette.text, Align.center);
		}
	}

	private static String upper(final String glueText) {
		return GlueText.plain(glueText).toUpperCase(Locale.ROOT);
	}

	/** The text size at most this one at which a line fits a width. */
	private float fitted(final String text, final float size, final float width) {
		final float natural = this.gui.width(text, size);
		return natural <= width ? size : Math.max(size * 0.6f, (size * width) / natural);
	}

	/**
	 * The Reforged campaign screen's left side: the shield of the campaign version, the campaign's header and name,
	 * and under the name one bar with the version and difficulty menus and the button to the saved games. Gives the
	 * height of the name's middle.
	 */
	private float campaignBlock(final CampaignCatalog.Entry entry) {
		return campaignBlock(entry, null);
	}

	/** The campaign block; after a mission Continue stands in place of the version and difficulty menus. */
	private float campaignBlock(final CampaignCatalog.Entry entry, final GameResult result) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final CampaignMenuData data = entry.getData();
		final boolean versions = this.campaigns.catalog.hasReforgedMaps();
		final String profile = this.profiles.current();
		final int version = !versions || LaunchRequest.CAMPAIGN_MAPS_CLASSIC.equals(this.profiles.campaignMaps(profile))
				? 1
				: 0;
		final float shield = t ? 176 : 84;
		final float headerSize = t ? 46 : 22;
		final float nameSize = t ? 60 : 30;
		final float menuH = t ? 98 : 72;
		final float gap = t ? 12 : 6;
		final float left = b.contentX + (t ? 24 : 0);
		final float right = listX() - (t ? 48 : 16);
		final float y = b.contentY + ((b.contentH - shield - gap - menuH) * 0.5f);
		g.image(version == 0 ? SkinArt.VERSION_REFORGED : SkinArt.VERSION_CLASSIC, left, y, shield, shield);
		final float tx = left + shield + (t ? 20 : 10);
		final float textW = right - tx;
		final String header = upper(glue(data.getHeader(), data.getHeader()));
		final String name = upper(glue(data.getName(), data.getName()));
		g.text(header, tx, y + (shield * 0.06f), textW, shield * 0.4f, fitted(header, headerSize, textW),
				g.palette.gold, Align.left);
		g.text(name, tx, y + (shield * 0.48f), textW, shield * 0.46f, fitted(name, nameSize, textW), g.palette.text,
				Align.left);
		final float menuY = y + shield + gap;
		final float text = t ? 32 : 22;
		final float folderW = t ? 120 : 84;
		final float difficultyW = t ? 300 : 150;
		float mx = tx;
		if (result != null) {
			final float continueW = t ? 340 : 170;
			if (g.button("continue", upper(glue("CONTINUE", "Continue")), mx, menuY, continueW, menuH, text,
					Gui.TONE_NORMAL)) {
				playClick();
				continueAfter(entry, result);
			}
			mx += continueW + gap;
		}
		else {
			if (versions) {
				final float versionW = t ? 400 : 170;
				g.dropdown("version", glueList(LauncherKeys.CAMPAIGN_MAPS, 1), version, mx, menuY, versionW, menuH,
						text, true, null,
						choice -> this.profiles.setCampaignMaps(profile, LauncherKeys.CAMPAIGN_MAPS[choice][0]));
				mx += versionW + gap;
			}
			final String current = this.preferences.getString(LauncherKeys.KEY_DIFFICULTY,
					LauncherKeys.DIFFICULTIES[0][0]);
			int selected = 0;
			for (int i = 0; i < LauncherKeys.DIFFICULTIES.length; i++) {
				if (LauncherKeys.DIFFICULTIES[i][0].equals(current)) {
					selected = i;
				}
			}
			g.dropdown("difficulty", glueList(LauncherKeys.DIFFICULTIES, 1), selected, mx, menuY, difficultyW, menuH,
					text, true, null, choice -> this.preferences.edit()
							.putString(LauncherKeys.KEY_DIFFICULTY, LauncherKeys.DIFFICULTIES[choice][0]).apply());
			mx += difficultyW + gap;
		}
		if (g.button("saves", "", mx, menuY, folderW, menuH, text, Gui.TONE_NORMAL)) {
			playClick();
			showPage(Page.LOAD);
		}
		if (g.over(mx, menuY, folderW, menuH)) {
			g.tip(GlueText.plain(glue("KEY_LOAD_SAVED_GAME", "Load Saved Game")));
		}
		final float icon = menuH * 0.5f;
		g.image(SkinArt.ICON_FOLDER_GOLD, mx + ((folderW - icon) / 2), menuY + ((menuH - icon) / 2), icon, icon);
		return y + (shield * 0.5f);
	}

	/**
	 * After a campaign mission, as on the Reforged campaign screen: the campaign with Continue on the left, the
	 * campaign's banner with the mission's result on the right.
	 */
	private void resultPage(final CampaignCatalog.Entry entry, final GameResult result) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		campaignBlock(entry, result);
		final CampaignResult.Layout l = new CampaignResult.Layout(listX() + ((t ? 924 : 600) / 2f), b.width,
				b.height);
		g.image(CampaignResult.banner(entry.getSection()), l.x, 0, l.w, l.h);
		final String[] title = CampaignResult.title(result);
		g.text(glue(title[0], title[1]), l.contentX, l.titleY, l.contentW, l.titleH, l.titleSize, 0xFFFFFFFF,
				Align.center);
		for (final float dividerY : l.dividerY) {
			g.image(SkinArt.RESULT_DIVIDER, l.dividerX, dividerY, l.dividerW, l.dividerH);
		}
		for (int i = 0; i < CampaignResult.ROWS.length; i++) {
			final String[] row = CampaignResult.ROWS[i];
			final int[] numbers = CampaignResult.numbers(result.score, i);
			final float y = l.rowY[i];
			g.image(row[0], l.iconX, y, l.icon, l.icon);
			g.text(upper(glue(row[1], row[2])), l.detailsX, y + l.headingY, l.columnW * 2, l.headingH, l.headingSize,
					CampaignResult.HEADING_GOLD, Align.left);
			final String[] labels = { glue("SCORE", "Score"), glue(row[3], row[4]) };
			for (int c = 0; c < 2; c++) {
				final float x = l.detailsX + (c * l.columnW);
				g.text(labels[c], x, y + l.labelY, l.columnW, l.labelH, l.labelSize, CampaignResult.LABEL_GREY,
						Align.left);
				g.text(Integer.toString(numbers[c]), x, y + l.valueY, l.columnW, l.valueH, l.valueSize, 0xFFFFFFFF,
						Align.left);
			}
		}
		final String name = missionName(entry, result);
		g.text(name, l.contentX, l.nameY, l.contentW, l.nameH, fitted(GlueText.plain(name), l.nameSize, l.contentW),
				0xFFFFFFFF, Align.center);
		g.text(result.playTime(), l.contentX, l.durationY, l.contentW, l.durationH, l.durationSize,
				CampaignResult.HEADING_GOLD, Align.center);
		final String[] difficulty = CampaignResult.difficulty(result.difficulty);
		if (difficulty != null) {
			g.text(glue(difficulty[1], difficulty[2]), l.contentX, l.difficultyY, l.contentW, l.difficultyH,
					l.difficultySize, CampaignResult.LABEL_GREY, Align.center);
			g.image(difficulty[0], l.difficultyIconX, l.difficultyIconY, l.difficultyIcon, l.difficultyIcon);
		}
		if (g.button("back", glue("BACK", s("launcher_back")), b.backX, b.backY, b.backW, b.backH, t ? 36 : 30,
				Gui.TONE_NORMAL)) {
			playClick();
			this.campaignResult = null;
		}
	}

	/**
	 * After a campaign mission on the classic skin: the same result and Continue as the Reforged one, in a panel of the
	 * classic frame where the mission list stands, the campaign's name under the scene.
	 */
	private void classicResultPage(final CampaignCatalog.Entry entry, final GameResult result) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		classicName(entry.getData());
		final float bottom = t ? b.contentY + b.contentH : b.height - 8;
		final CampaignResult.ClassicLayout l = new CampaignResult.ClassicLayout(listX(), b.contentY, t ? 924 : 600,
				bottom - b.contentY, b.pageFrame, b.buttonH);
		g.panel(l.x, l.y, l.w, l.h, b.pageFrame);
		final String[] title = CampaignResult.title(result);
		g.text(glue(title[0], title[1]), l.contentX, l.titleY, l.contentW, l.titleH, l.titleSize, g.palette.gold,
				Align.center);
		for (final float lineY : l.lineY) {
			final int line = g.palette.line;
			final float half = l.contentW / 2;
			g.gradient(l.contentX, lineY, half, l.lineH, line & 0x00FFFFFF, line, false);
			g.gradient(l.contentX + half, lineY, half, l.lineH, line, line & 0x00FFFFFF, false);
		}
		for (int i = 0; i < CampaignResult.ROWS.length; i++) {
			final String[] row = CampaignResult.ROWS[i];
			final int[] numbers = CampaignResult.numbers(result.score, i);
			final float y = l.rowY[i];
			final String heading = glue(row[1], row[2]);
			g.text(heading, l.contentX, y, l.headingW, l.rowH, fitted(GlueText.plain(heading), l.headingSize,
					l.headingW), g.palette.gold, Align.left);
			final String[] labels = { glue("SCORE", "Score"), glue(row[3], row[4]) };
			for (int c = 0; c < 2; c++) {
				g.text(labels[c], l.columnX[c], y + l.labelY, l.columnW, l.labelH, l.labelSize, g.palette.text2,
						Align.left);
				g.text(Integer.toString(numbers[c]), l.columnX[c], y + l.valueY, l.columnW, l.valueH, l.valueSize,
						g.palette.text, Align.left);
			}
		}
		final String name = missionName(entry, result);
		g.text(name, l.contentX, l.nameY, l.contentW, l.nameH, fitted(GlueText.plain(name), l.nameSize, l.contentW),
				g.palette.text, Align.center);
		g.text(result.playTime(), l.contentX, l.durationY, l.contentW, l.durationH, l.durationSize, g.palette.gold,
				Align.center);
		final String[] difficulty = CampaignResult.difficulty(result.difficulty);
		if (difficulty != null) {
			g.text(glue(difficulty[1], difficulty[2]), l.contentX, l.difficultyY, l.contentW, l.difficultyH,
					l.difficultySize, g.palette.text2, Align.center);
		}
		if (g.button("continue", glue("CONTINUE", "Continue"), l.buttonX, l.buttonY, l.buttonW, l.buttonH,
				b.buttonText, Gui.TONE_NORMAL)) {
			playClick();
			continueAfter(entry, result);
			return;
		}
		if (g.button("back", glue("BACK", s("launcher_back")), b.backX, b.backY, b.backW, b.backH, t ? 36 : 30,
				Gui.TONE_NORMAL)) {
			playClick();
			this.campaignResult = null;
		}
	}

	/** The played mission's name as the list has it, else the map's own name. */
	private String missionName(final CampaignCatalog.Entry entry, final GameResult result) {
		final String listed = playedName(entry, result);
		return listed.isEmpty() ? result.mapName : listed;
	}

	/** The mission's name as the campaign list has it (a map names itself by its file), else empty. */
	private String playedName(final CampaignCatalog.Entry entry, final GameResult result) {
		final int played = CampaignResult.playedMission(this.campaigns.catalog, entry, result.map);
		if (played < 0) {
			return "";
		}
		final CampaignMission mission = entry.getData().getMissions().get(played);
		return glue(mission.getMissionName(), mission.getMissionName());
	}

	/** Continue after a mission: the next mission after a victory, the same one after a loss, else the chapters. */
	private void continueAfter(final CampaignCatalog.Entry entry, final GameResult result) {
		final int played = CampaignResult.playedMission(this.campaigns.catalog, entry, result.map);
		final int next = CampaignResult.continueMission(entry, this.campaigns.progress, played,
				"victory".equals(result.result));
		this.campaignResult = null;
		if (next >= 0) {
			launchMission(entry, next, Page.MISSIONS);
		}
	}

	private void addCinematic(final List<Object[]> rows, final CampaignMission cinematic) {
		if ((cinematic != null) && this.campaigns.hasMovie(cinematic)) {
			rows.add(new Object[] { cinematic, null });
		}
	}

	/** A Reforged chapter row: its icon (cinematic, won or not yet), the chapter's kind in gold and its name. */
	private boolean chapterRow(final Object id, final CampaignMission mission, final boolean chosen, final String icon,
			final float x, final float y, final float w, final float rowH) {
		final Gui g = this.gui;
		final int state = g.hit(id, x, y, w, rowH);
		if ((state & Gui.HOVER) != 0) {
			g.image(this.art.image(SkinArt.ROW_HIGHLIGHT), x, y, w, rowH, 0.5f);
		}
		final float a = rowH * 0.62f;
		g.image(icon, x + (rowH * 0.12f), y + ((rowH - a) / 2), a, a);
		final float tx = (rowH * 0.24f) + a;
		final String header = glue(mission.getHeader(), mission.getHeader());
		final String name = glue(mission.getMissionName(), mission.getMissionName());
		g.text(header, x + tx, y + (rowH * 0.12f), w - tx, rowH * 0.3f, rowH * 0.22f, g.palette.gold, Align.left);
		final boolean cut = g.text(name, x + tx, y + (rowH * 0.42f), w - tx, rowH * 0.42f, rowH * 0.32f,
				chosen ? g.palette.gold : g.palette.text, Align.left);
		if (cut && ((state & Gui.HOVER) != 0)) {
			g.tip(GlueText.plain(name));
		}
		return (state & Gui.CLICK) != 0;
	}

	private void playMovies(final List<String> names) {
		stopMusic();
		this.playingMovie = true;
		stopRenderer();
		disposeCursor();
		final List<com.etheller.warsmash.movie.Movie> movies = Movies.find(this.data, names,
				Gdx.graphics.getWidth());
		Movies.play(movies, () -> Gdx.app.postRunnable(() -> {
			this.playingMovie = false;
			this.game.setScreen(this);
			startRenderer();
			updateMusic();
		}));
	}

	private void launchMission(final CampaignCatalog.Entry entry, final int mission, final Page returnPage) {
		this.preferences.edit().putInt(LauncherKeys.KEY_CAMPAIGN, entry.getNumber())
				.putInt(LauncherKeys.KEY_MISSION, mission).apply();
		launchGame(LaunchRequest.mission(this.campaigns.catalog, entry, mission,
				this.profiles.campaignMaps(this.profiles.current()),
				this.preferences.getString(LauncherKeys.KEY_DIFFICULTY, LauncherKeys.DIFFICULTIES[0][0])), returnPage);
	}

	// ---------------------------------------------------------------- Custom Games

	private void customPage() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float pf = b.pageFrame;
		final float listW = t ? 620 : 420;
		g.panel(b.contentX, b.contentY, listW, b.contentH, pf);
		final float rowH = t ? 112 : 96;
		final float lx = b.contentX + pf + 8;
		final float ly = b.contentY + pf + 12;
		final float lw = listW - (2 * pf) - 16;
		final float lh = b.contentH - (2 * pf) - 24;
		if (this.maps == null) {
			g.text(s("game_loading_list"), lx, ly, lw, rowH, b.body, g.palette.text2, Align.left);
		}
		else {
			mapList(lx, ly, lw, lh, rowH);
		}
		final GameCatalog.MapDetails details = this.mapDetails;
		if (t) {
			final float rx = b.contentX + 656;
			final float mw = b.contentW - 656 - 536;
			final float ax = rx + mw + 36;
			g.panel(rx, b.contentY, mw, 620, pf);
			if (details != null) {
				g.fill(rx + pf + 6, b.contentY + pf + 7, 398, 398, 0xFF555555);
				g.fill(rx + pf + 9, b.contentY + pf + 10, 392, 392, 0xFF000000);
				g.image(this.minimap != null ? this.minimap : this.art.image(SkinArt.MINIMAP_UNKNOWN), rx + pf + 9,
						b.contentY + pf + 10, 392, 392);
				final float tx = rx + 480;
				g.text(mapTitle(details), tx, b.contentY + 50, mw - 490 - pf, 60, 42, g.palette.text, Align.left);
				final String[][] facts = { { glue("SUGGESTED_PLAYERS", "Suggested Players:"), details.suggested },
					{ glue("MAP_SIZE", "Map Size:"), details.size }, { glue("TILESET", "Tileset:"), details.tileset } };
				for (int i = 0; i < facts.length; i++) {
					g.text(facts[i][0], tx, b.contentY + 118 + (i * 46), 270, 46, 30, g.palette.gold, Align.left);
					g.text(facts[i][1], tx + 279, b.contentY + 118 + (i * 46), mw - 490 - pf - 279, 46, 30,
							g.palette.text, Align.left);
				}
				g.lines(details.description, tx, b.contentY + 279, (rx + mw) - pf - 20 - tx, 300, 27, g.palette.text2,
						Align.left);
			}
			g.panel(ax, b.contentY, 500, 620, pf);
			advancedOptions(ax + 44, b.contentY + 46, 420, 82, 48, 27, 34);
			final float sy = b.contentY + 656;
			final float sw = b.contentW - 656;
			g.panel(rx, sy, sw, b.contentH - 656, pf);
			if (details != null) {
				slotRows(details, rx + pf + 11, sy, sw - (2 * (pf + 11)), b.contentH - 656, 130, 154, 38, 28);
			}
		}
		else {
			final float rx = b.contentX + 430;
			final float rw = b.contentW - 430;
			g.panel(rx, b.contentY, rw, 120, pf);
			if (details != null) {
				g.image(this.minimap != null ? this.minimap : this.art.image(SkinArt.MINIMAP_UNKNOWN), (rx + pf) - 6,
						b.contentY + 16, 88, 88);
				g.text(mapTitle(details), rx + pf + 100, b.contentY + 14, rw - 140, 44, 28, g.palette.text, Align.left);
				g.text(details.suggested + " \u00b7 " + details.size + " \u00b7 " + details.tileset, rx + pf + 100,
						b.contentY + 56, rw - 140, 32, 20, g.palette.text, Align.left);
			}
			g.panel(rx, b.contentY + 132, rw, b.contentH - 132, pf);
			if (details != null) {
				slotRows(details, rx + pf, b.contentY + 132 + 20, rw - (2 * pf), b.contentH - 152, 80, 96, 22, 0);
			}
			final float[] advanced = b.actionSlot(1);
			if (this.gui.button("advanced", s("game_advanced"), advanced[0], b.backY, advanced[1], b.backH, 30,
					details == null ? Gui.TONE_OFF : Gui.TONE_NORMAL)) {
				this.dialog = Dialog.ADVANCED;
			}
		}
		if (g.button("cancel", glue("CANCEL", "Cancel"), b.backX, b.backY, b.backW, b.backH, t ? 36 : 30,
				Gui.TONE_NORMAL)) {
			playClick();
			showPage(Page.HOME);
		}
		final float[] start = b.actionSlot(2);
		if (g.button("start", glue("KEY_START_GAME", "Start Game"), start[0], b.backY, start[1], b.backH, t ? 36 : 30,
				details == null ? Gui.TONE_OFF : this.art.reforged ? Gui.TONE_CONFIRM : Gui.TONE_DOWN)) {
			startCustom();
		}
	}

	private String mapTitle(final GameCatalog.MapDetails details) {
		final int players = CampaignChoice.players(details);
		final String gold = String.format(Locale.ROOT, "|c%08X", this.gui.palette.gold);
		return (players > 0 ? gold + players + "|r " : "") + details.name;
	}

	private void mapList(final float x, final float y, final float w, final float h, final float rowH) {
		final Gui g = this.gui;
		final String chosen = CampaignChoice.chosenMap(this.preferences.getString(LauncherKeys.KEY_MAP, null),
				this.maps);
		final String folder = CampaignChoice.shownMapFolder(this.preferences.getString(LauncherKeys.KEY_MAP_FOLDER,
				null), chosen, this.maps);
		final String prefix = folder.isEmpty() ? "" : folder + "/";
		final List<String[]> rows = new ArrayList<>();
		if (!folder.isEmpty()) {
			final int up = folder.lastIndexOf('/');
			rows.add(new String[] { "..", up < 0 ? "" : folder.substring(0, up), null });
		}
		for (final String child : CampaignChoice.childFolders(this.maps, folder)) {
			rows.add(new String[] { CampaignChoice.folderName(child.substring(prefix.length())), child, null });
		}
		final List<GameCatalog.MapEntry> shown = new ArrayList<>();
		for (final GameCatalog.MapEntry entry : this.maps) {
			if (CampaignChoice.mapFolder(entry.path).equalsIgnoreCase(folder)) {
				shown.add(entry);
			}
		}
		final float contentH = (rows.size() + shown.size()) * rowH;
		final float offset = g.beginList("maps" + folder, x, y, w, h, contentH);
		float rowY = y - offset;
		final float line = Math.min(rowH * 0.86f, 84);
		final float icon = line * 0.82f;
		for (final String[] row : rows) {
			final int state = g.hit("folder" + row[1], x, rowY, w, rowH);
			if ((state & Gui.HOVER) != 0) {
				g.image(this.art.image(SkinArt.ROW_HIGHLIGHT), x, rowY, w, rowH, 0.5f);
			}
			g.image(SkinArt.ICON_FOLDER, x + 8, rowY + ((rowH - icon) / 2), icon, icon);
			g.text(row[0], x + 8 + icon + 24, rowY, w - icon - 40, rowH, line * 0.56f, g.palette.text, Align.left);
			if ((state & Gui.CLICK) != 0) {
				playClick();
				this.preferences.edit().putString(LauncherKeys.KEY_MAP_FOLDER, row[1]).apply();
			}
			rowY += rowH;
		}
		for (final GameCatalog.MapEntry entry : shown) {
			final int state = g.hit("map" + entry.path, x, rowY, w, rowH);
			final boolean isChosen = entry.path.equals(chosen);
			if (isChosen || ((state & Gui.HOVER) != 0)) {
				g.image(this.art.image(SkinArt.ROW_HIGHLIGHT), x, rowY, w, rowH, isChosen ? 1 : 0.5f);
			}
			g.image(entry.melee ? SkinArt.ICON_MELEE : SkinArt.ICON_UMS, x + 8, rowY + ((rowH - icon) / 2), icon, icon);
			if (entry.players > 0) {
				g.text(Integer.toString(entry.players), x + 8, rowY + ((rowH - icon) / 2), icon, icon, icon * 0.5f,
						g.palette.gold, Align.center);
			}
			final String label = entry.label != null ? entry.label : entry.name;
			final boolean cut = g.text(label, x + 8 + icon + 24, rowY, w - icon - 40, rowH, line * 0.56f,
					g.palette.text, Align.left);
			if (cut && ((state & Gui.HOVER) != 0)) {
				g.tip(GlueText.plain(label));
			}
			if ((state & (Gui.CLICK | Gui.RIGHT)) != 0) {
				playClick();
				if (!entry.path.equals(chosen) || (this.mapDetails == null)) {
					this.preferences.edit().putString(LauncherKeys.KEY_MAP, entry.path).apply();
					loadMapDetails(entry.path);
				}
				if ((state & Gui.DOUBLE) != 0) {
					startWhenRead(entry.path);
				}
				if ((state & Gui.RIGHT) != 0) {
					g.contextMenu("mapMenu", new String[] { glue("KEY_START_GAME", "Start Game") }, null,
							choice -> startWhenRead(entry.path));
				}
			}
			rowY += rowH;
		}
		g.endList();
		if (this.maps.isEmpty()) {
			g.text(s("game_no_maps"), x, y, w, rowH, this.board.body, g.palette.text2, Align.left);
		}
		if ((chosen != null) && ((this.mapDetails == null) || !this.mapDetails.path.equals(chosen))
				&& !chosen.equals(this.loadingMap)) {
			loadMapDetails(chosen);
		}
	}

	private String loadingMap;
	private boolean pendingStart;

	/** Starts the custom game on a map now, or once its details are read. */
	private void startWhenRead(final String path) {
		if ((this.mapDetails != null) && this.mapDetails.path.equals(path) && !path.equals(this.loadingMap)) {
			startCustom();
		}
		else {
			this.pendingStart = true;
		}
	}

	private void loadMapDetails(final String path) {
		this.loadingMap = path;
		this.catalog.mapDetails(path, details -> {
			if (!path.equals(this.loadingMap)) {
				return;
			}
			this.loadingMap = null;
			if (details == null) {
				this.messageTitle = s("game_map_unreadable");
				this.messageLine = path;
				this.dialog = Dialog.MESSAGE;
				return;
			}
			this.mapDetails = details;
			this.minimap = details.minimap != null ? this.art.region(details.minimap) : null;
			this.lobby.chooseSlots(details);
			this.lobby.advanced = CustomGame
					.advancedOptions(this.preferences.getString(LauncherKeys.KEY_OPTIONS, null));
			if (this.pendingStart) {
				this.pendingStart = false;
				startCustom();
			}
		});
	}

	private void advancedOptions(final float x, final float y, final float w, final float step, final float box,
			final float size, final float titleSize) {
		final Gui g = this.gui;
		g.text(glue("ADVANCED_OPTIONS", "Advanced Options"), x, y, w, 48, titleSize, g.palette.gold, Align.left);
		final String[] labels = optionLabels();
		for (int i = 0; i < labels.length; i++) {
			final int option = CustomGame.OPTION_SHOWN[i];
			final float rowY = y + 61 + (i * step);
			final boolean on = this.lobby.advanced[option];
			if (g.check("option" + i, x, rowY, box, on)
					| ((g.hit("optionLabel" + i, x + box + 24, rowY, w - box - 24, box) & Gui.CLICK) != 0)) {
				this.preferences.edit().putString(LauncherKeys.KEY_OPTIONS, this.lobby.setOption(option, !on)).apply();
			}
			g.text(labels[i], x + box + 24, rowY, w - box - 24, box, size, g.palette.text, Align.left);
		}
	}

	private String[] optionLabels() {
		return new String[] { glue("LOCK_TEAMS", "Lock Teams"),
			glue("FULL_SHARED_UNIT_CONTROL", s("game_shared_control")), glue("RANDOM_RACES", "Random Races"),
			glue("RANDOM_HERO", "Random Hero") };
	}

	private void slotRows(final GameCatalog.MapDetails details, final float x, final float top, final float w,
			final float h, final float menuH, final float pitch, final float size, final float headSize) {
		final Gui g = this.gui;
		final boolean t = this.board.tablet;
		final float gap = t ? 28 : 16;
		final float available = w - (3 * gap);
		final float[] widths = { available * 0.42f, available * 0.246f, available * 0.148f, available * 0.186f };
		float rowsTop = top + 20;
		if (headSize > 0) {
			final String[] heads = { glue("PLAYERS", "Player"), glue("RACE", "Race"), glue("TEAM", "Team"),
				glue("COLOR", "Color") };
			float hx = x;
			for (int i = 0; i < heads.length; i++) {
				g.text(heads[i], hx, top + 41, widths[i], 40, headSize, g.palette.gold, Align.left);
				hx += widths[i] + gap;
			}
			rowsTop = top + 100;
		}
		final boolean editable = !CustomGame.fixedSlots(details);
		final String[] types = glueList(CustomGame.SLOT_TYPES, 1);
		final String[] races = glueList(CustomGame.RACES, 0);
		final String[] teams = teamNames(details);
		if (!details.customForces && (g.width(teams[teams.length - 1], size) > (widths[2] - (menuH * 0.9f)))) {
			// A narrow column keeps the team's number rather than an ellipsis.
			for (int i = 0; i < teams.length; i++) {
				teams[i] = Integer.toString(i + 1);
			}
		}
		final String[] colours = glueList(CustomGame.COLOR_NAMES, 0);
		final List<Integer> visible = new ArrayList<>();
		for (int i = 0; i < details.slots.size(); i++) {
			if (this.lobby.slotTypes[i] != CustomGame.SLOT_HIDDEN) {
				visible.add(i);
			}
		}
		final float listH = (top + h) - rowsTop - 16;
		final float offset = g.beginList("slots" + details.path, x, rowsTop, w, listH, visible.size() * pitch);
		float y = rowsTop - offset;
		for (final int index : visible) {
			float cx = x;
			if (this.lobby.slotTypes[index] == CustomGame.SLOT_YOU) {
				g.drawButton(cx, y, widths[0], menuH, Gui.TONE_DOWN);
				g.text(this.profiles.current(), cx + (menuH * 0.3f), y, widths[0] - (menuH * 0.6f), menuH, size,
						0xFFFFFFFF, Align.left);
			}
			else {
				g.dropdown("type" + index, types, this.lobby.slotTypes[index], cx, y, widths[0], menuH, size, editable,
						null, choice -> this.lobby.slotTypes[index] = choice);
			}
			cx += widths[0] + gap;
			g.dropdown("race" + index, races, this.lobby.slotRaces[index], cx, y, widths[1], menuH, size, editable,
					null, choice -> this.lobby.slotRaces[index] = choice);
			cx += widths[1] + gap;
			final int team = this.lobby.slotTeams[index] >= 0 ? this.lobby.slotTeams[index]
					: CustomGame.defaultTeam(details, index);
			g.dropdown("team" + index, teams, Math.min(team, teams.length - 1), cx, y, widths[2], menuH, size,
					editable && !details.fixedSettings, null, choice -> this.lobby.slotTeams[index] = choice);
			cx += widths[2] + gap;
			g.dropdown("colour" + index, colours, this.lobby.colorOf(details, index), cx, y, widths[3], menuH, size,
					editable, CustomGame.PLAYER_COLORS, choice -> this.lobby.slotColors[index] = choice);
			y += pitch;
		}
		g.endList();
	}

	private String[] teamNames(final GameCatalog.MapDetails details) {
		if (details.customForces && !details.forces.isEmpty()) {
			final String[] names = new String[details.forces.size()];
			for (int i = 0; i < names.length; i++) {
				names[i] = GlueText.plain(details.forces.get(i));
			}
			return names;
		}
		final String[] names = new String[details.slots.size()];
		final String format = glue("TEAM_FORMAT", "Team %d").replace("%s", "%d");
		for (int i = 0; i < names.length; i++) {
			try {
				names[i] = String.format(Locale.ROOT, format, i + 1);
			}
			catch (final RuntimeException e) {
				names[i] = Integer.toString(i + 1);
			}
		}
		return names;
	}

	private void startCustom() {
		final GameCatalog.MapDetails details = this.mapDetails;
		if (details == null) {
			return;
		}
		this.preferences.edit().putInt(LauncherKeys.KEY_LAUNCH_RACE, this.lobby.playerRace()).apply();
		launchGame(this.lobby.request(details), Page.CUSTOM);
	}

	// ---------------------------------------------------------------- Load Saved Game

	private GameCatalog.SaveEntry chosenSave() {
		final String name = this.preferences.getString(LauncherKeys.KEY_SAVE, null);
		for (final GameCatalog.SaveEntry entry : this.saveList) {
			if (entry.name.equals(name)) {
				return entry;
			}
		}
		return this.saveList.isEmpty() ? null : this.saveList.get(0);
	}

	private final Map<String, TextureRegion> savePictures = new HashMap<>();

	private void loadPage() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float pf = b.pageFrame;
		final float infoW = t ? 860 : b.contentW - 716;
		final float listW = b.contentW - infoW - (t ? 40 : 16);
		final float ix = b.contentX + listW + (t ? 40 : 16);
		g.panel(b.contentX, b.contentY, listW, b.contentH, pf);
		g.panel(ix, b.contentY, infoW, b.contentH, pf);
		final GameCatalog.SaveEntry chosen = this.saveList == null ? null : chosenSave();
		final float rowH = t ? 123 : 96;
		final float icon = rowH * 0.7f;
		if (this.saveList == null) {
			g.text(s("game_loading_list"), b.contentX, b.contentY, listW, b.contentH, t ? 36 : 28, g.palette.text2,
					Align.center);
		}
		else if (this.saveList.isEmpty()) {
			g.text(s("game_no_saves"), b.contentX, b.contentY, listW, b.contentH, t ? 36 : 28, g.palette.text2,
					Align.center);
		}
		else {
			final float x = b.contentX + pf + 8;
			final float w = listW - (2 * pf) - 16;
			float top = b.contentY + pf + 14;
			List<GameCatalog.SaveEntry> shown = this.saveList;
			if (this.art.reforged) {
				// The Reforged list of saved games has a field above it that keeps the saves whose name has its text.
				final float fieldH = t ? 96 : 72;
				final float textSize = t ? 34 : 24;
				g.field("saveFilter", this.saveFilter, x, top, w, fieldH, textSize, 60, s("launcher_filter_saves"));
				final float glass = fieldH * 0.5f;
				g.image(SkinArt.ICON_SEARCH, (x + w) - glass - (fieldH * 0.3f), top + ((fieldH - glass) / 2), glass,
						glass);
				top += fieldH + (t ? 16 : 8);
				final String filter = this.saveFilter.toString().trim().toLowerCase(Locale.ROOT);
				if (!filter.isEmpty()) {
					shown = new ArrayList<>();
					for (final GameCatalog.SaveEntry entry : this.saveList) {
						if (entry.name.toLowerCase(Locale.ROOT).contains(filter)) {
							shown.add(entry);
						}
					}
				}
			}
			final float offset = g.beginList("saves", x, top, w, (b.contentY + b.contentH) - pf - 14 - top,
					shown.size() * rowH);
			float y = top - offset;
			final DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
			for (final GameCatalog.SaveEntry entry : shown) {
				final boolean loadable = entry.loadability == SavedGame.Loadability.LOADABLE;
				final int state = g.hit("save" + entry.name, x, y, w, rowH);
				if ((entry == chosen) || ((state & Gui.HOVER) != 0)) {
					g.image(this.art.image(SkinArt.ROW_HIGHLIGHT), x, y, w, rowH, entry == chosen ? 1 : 0.5f);
				}
				final float alpha = loadable ? 1 : 0.5f;
				g.image(this.art.image(SkinArt.ICON_FILE), x + 12, y + ((rowH - icon) / 2), icon, icon, alpha);
				g.text(entry.name, x + 12 + icon + 18, y, w * 0.6f, rowH, rowH * 0.42f,
						loadable ? g.palette.text : SkinArt.Palette.DISABLED, Align.left);
				g.text(format.format(new Date(entry.saved)), x + (w * 0.5f), y, (w * 0.5f) - 16, rowH, rowH * 0.34f,
						g.palette.text2, Align.right);
				if (!loadable && ((state & Gui.HOVER) != 0)) {
					g.tip(loadabilityText(entry.loadability));
				}
				if ((state & (Gui.CLICK | Gui.RIGHT)) != 0) {
					playClick();
					this.preferences.edit().putString(LauncherKeys.KEY_SAVE, entry.name).apply();
					if (((state & Gui.DOUBLE) != 0) && loadable) {
						launchGame(loadRequest(entry), Page.LOAD);
					}
					else if ((state & Gui.RIGHT) != 0) {
						g.contextMenu("saveMenu",
								new String[] { glue("LOAD_GAME", "Load Game"), glue("DELETE", "Delete") },
								new boolean[] { loadable, true }, choice -> {
									if (choice == 0) {
										launchGame(loadRequest(entry), Page.LOAD);
									}
									else {
										this.dialog = Dialog.DELETE_SAVE;
									}
								});
					}
				}
				y += rowH;
			}
			g.endList();
		}
		if (chosen != null) {
			g.text(chosen.name, ix + pf, b.contentY + pf + (t ? 8 : 4), infoW - (2 * pf), t ? 60 : 40, t ? 44 : 28,
					g.palette.text, Align.center);
			final float pw = infoW * 0.6f;
			final float ph = pw * 0.75f;
			final float py = b.contentY + pf + (t ? 76 : 50);
			g.fill((ix + ((infoW - pw) / 2)) - 2, py - 2, pw + 4, ph + 4, 0xFF444444);
			g.fill(ix + ((infoW - pw) / 2), py, pw, ph, 0xFF000000);
			final TextureRegion picture = savePicture(chosen);
			if (picture != null) {
				g.cover(picture, ix + ((infoW - pw) / 2), py, pw, ph, 0.5f);
			}
			if (chosen.loadability != SavedGame.Loadability.LOADABLE) {
				g.lines(loadabilityText(chosen.loadability), ix + pf + 16, py + ph + (t ? 40 : 20), infoW - (2 * pf) - 32,
						200, t ? 30 : 20, SkinArt.Palette.DANGER_TEXT, Align.center);
			}
			else {
				final String[][] facts = { { glue("COLON_ELAPSED_TIME", "Elapsed Time:"),
					CampaignChoice.elapsed(chosen.gameTime) },
					{ glue("COLON_SAVED", "Saved:"), DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
							.format(new Date(chosen.saved)) } };
				for (int i = 0; i < facts.length; i++) {
					final float fy = py + ph + (t ? 40 : 20) + (i * (t ? 48 : 34));
					g.text(facts[i][0], ix + pf + 16, fy, infoW / 2, t ? 48 : 34, t ? 30 : 20, g.palette.gold,
							Align.left);
					g.text(facts[i][1], ix + (infoW / 2), fy, (infoW / 2) - pf - 16, t ? 48 : 34, t ? 30 : 20,
							g.palette.text, Align.right);
				}
			}
		}
		final float size = t ? 36 : 30;
		if (g.button("back", glue("BACK", s("launcher_back")), b.backX, b.backY, b.backW, b.backH, size,
				Gui.TONE_NORMAL)) {
			playClick();
			showPage(Page.HOME);
		}
		final float[] delete = b.actionSlot(1);
		if (g.button("delete", glue("DELETE", "Delete"), delete[0], b.backY, delete[1], b.backH, size,
				chosen == null ? Gui.TONE_OFF : this.art.reforged ? Gui.TONE_DANGER : Gui.TONE_NORMAL)) {
			this.dialog = Dialog.DELETE_SAVE;
		}
		final float[] load = b.actionSlot(2);
		final boolean loadable = (chosen != null) && (chosen.loadability == SavedGame.Loadability.LOADABLE);
		if (g.button("load", glue("LOAD_GAME", "Load Game"), load[0], b.backY, load[1], b.backH, size,
				!loadable ? Gui.TONE_OFF : this.art.reforged ? Gui.TONE_CONFIRM : Gui.TONE_DOWN)) {
			launchGame(loadRequest(chosen), Page.LOAD);
		}
	}

	/** A save's picture: its campaign mission's Reforged picture, else nothing. */
	private TextureRegion savePicture(final GameCatalog.SaveEntry entry) {
		if (!this.art.reforged || (entry.mapFile == null)) {
			return null;
		}
		final String file = CampaignChoice.missionPicture(
				new File(entry.mapFile.replace('\\', '/')).getName().replaceAll("\\.w3[xm]$", ""),
				this.art.missionPictures());
		return file == null ? null : this.art.picture(file);
	}

	private String loadabilityText(final SavedGame.Loadability loadability) {
		switch (loadability) {
		case DAMAGED:
			return s("game_save_damaged");
		case OTHER_VERSION:
			return s("game_save_other_version");
		case OTHER_SCRIPTS:
			return s("game_save_other_scripts");
		default:
			return "";
		}
	}

	// ---------------------------------------------------------------- View Replay

	private List<GameCatalog.ReplayEntry> replayList;
	/** The map whose minimap Map Info shows for the chosen replay, and that minimap. */
	private String replayMinimapMap;
	private TextureRegion replayMinimap;

	private GameCatalog.ReplayEntry chosenReplay() {
		final String name = this.preferences.getString(LauncherKeys.KEY_REPLAY, null);
		for (final GameCatalog.ReplayEntry entry : this.replayList) {
			if (entry.file.getName().equals(name)) {
				return entry;
			}
		}
		return this.replayList.isEmpty() ? null : this.replayList.get(0);
	}

	private String raceName(final GameCatalog.ReplayPlayer player) {
		final String[] race = CustomGame.RACES[player.raceIndex()];
		return glue(race[0], race[1]);
	}

	/** Races as the list's second line names them: "Orc vs Human", or the number of players. */
	private String replayRaces(final GameCatalog.ReplayEntry entry) {
		if (entry.players.size() == 1) {
			return raceName(entry.players.get(0));
		}
		if (entry.players.size() == 2) {
			return raceName(entry.players.get(0)) + " vs " + raceName(entry.players.get(1));
		}
		return entry.players.size() + " " + glue("PLAYERS", "players").toLowerCase(Locale.ROOT);
	}

	private void startReplay(final GameCatalog.ReplayEntry entry) {
		if (entry == null) {
			return;
		}
		if (entry.playable()) {
			launchGame(LaunchRequest.replay(entry), Page.REPLAY);
		}
		else if (entry.canTryAnyway()) {
			this.triedReplay = entry;
			this.dialog = Dialog.TRY_REPLAY;
		}
	}

	/** View Replay: laid out as Load Saved Game, as the original's two screens are alike. */
	private void replayPage() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float pf = b.pageFrame;
		final float infoW = t ? 860 : b.contentW - 716;
		final float listW = b.contentW - infoW - (t ? 40 : 16);
		final float ix = b.contentX + listW + (t ? 40 : 16);
		g.panel(b.contentX, b.contentY, listW, b.contentH, pf);
		g.panel(ix, b.contentY, infoW, b.contentH, pf);
		final GameCatalog.ReplayEntry chosen = this.replayList == null ? null : chosenReplay();
		final float rowH = t ? 150 : 96;
		final float icon = rowH * (t ? 0.55f : 0.7f);
		if (this.replayList == null) {
			g.text(s("game_loading_list"), b.contentX, b.contentY, listW, b.contentH, t ? 36 : 28, g.palette.text2,
					Align.center);
		}
		else if (this.replayList.isEmpty()) {
			g.text(s("replay_none"), b.contentX, b.contentY, listW, b.contentH, t ? 36 : 28, g.palette.text2,
					Align.center);
		}
		else {
			final float x = b.contentX + pf + 8;
			final float w = listW - (2 * pf) - 16;
			final float top = b.contentY + pf + 14;
			final float offset = g.beginList("replays", x, top, w, b.contentH - (2 * pf) - 28,
					this.replayList.size() * rowH);
			float y = top - offset;
			final DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
			for (final GameCatalog.ReplayEntry entry : this.replayList) {
				final boolean playable = entry.playable();
				final int state = g.hit("replay" + entry.file.getName(), x, y, w, rowH);
				if ((entry == chosen) || ((state & Gui.HOVER) != 0)) {
					g.image(this.art.image(SkinArt.ROW_HIGHLIGHT), x, y, w, rowH, entry == chosen ? 1 : 0.5f);
				}
				final float alpha = playable ? 1 : 0.5f;
				g.image(this.art.image(entry.melee == null ? SkinArt.ICON_FILE
						: entry.melee ? SkinArt.ICON_MELEE : SkinArt.ICON_UMS), x + 12, y + ((rowH - icon) / 2), icon,
						icon, alpha);
				final float textX = x + 12 + icon + 18;
				final String when = t ? format.format(new Date(entry.recorded)) : CampaignChoice.elapsed(entry.length);
				g.text(entry.name, textX, y + (rowH * 0.08f), (w * 0.62f) - icon, rowH * 0.5f, rowH * 0.3f,
						playable ? g.palette.text : SkinArt.Palette.DISABLED, Align.left);
				g.text(when, x + (w * 0.5f), y + (rowH * 0.08f), (w * 0.5f) - 16, rowH * 0.5f, rowH * 0.2f,
						g.palette.text2, Align.right);
				final String second = entry.damaged ? s("replay_damaged")
						: t ? entry.mapName() + " · " + replayRaces(entry) + " · "
								+ CampaignChoice.elapsed(entry.length) + " · " + entry.version
								: entry.mapName() + " · " + entry.version;
				g.text(second, textX, y + (rowH * 0.5f), w - (textX - x) - 16, rowH * 0.42f, rowH * 0.2f,
						playable ? g.palette.text2 : SkinArt.Palette.DISABLED, Align.left);
				if ((state & (Gui.CLICK | Gui.RIGHT)) != 0) {
					playClick();
					this.preferences.edit().putString(LauncherKeys.KEY_REPLAY, entry.file.getName()).apply();
					if (((state & Gui.DOUBLE) != 0) && playable) {
						startReplay(entry);
					}
					else if ((state & Gui.RIGHT) != 0) {
						g.contextMenu("replayMenu",
								new String[] { glue("KEY_VIEW_REPLAY", "View Replay"), s("replay_rename"),
									glue("DELETE", "Delete") },
								new boolean[] { playable, true, true }, choice -> {
									if (choice == 0) {
										startReplay(entry);
									}
									else if (choice == 1) {
										openRenameReplay(entry);
									}
									else {
										this.dialog = Dialog.DELETE_REPLAY;
									}
								});
					}
				}
				y += rowH;
			}
			g.endList();
		}
		if (chosen != null) {
			replayInfo(chosen, ix, infoW);
		}
		final float size = t ? 36 : 30;
		if (g.button("back", glue("BACK", s("launcher_back")), b.backX, b.backY, b.backW, b.backH, size,
				Gui.TONE_NORMAL)) {
			playClick();
			showPage(Page.HOME);
		}
		final float[] delete = b.actionSlot(1);
		if (g.button("delete", glue("DELETE", "Delete"), delete[0], b.backY, delete[1], b.backH, size,
				chosen == null ? Gui.TONE_OFF : this.art.reforged ? Gui.TONE_DANGER : Gui.TONE_NORMAL)) {
			this.dialog = Dialog.DELETE_REPLAY;
		}
		final float[] view = b.actionSlot(2);
		final boolean playable = (chosen != null) && (chosen.playable() || chosen.canTryAnyway());
		final String viewLabel = this.art.reforged ? glue("WATCH_REPLAY", "Watch")
				: glue("KEY_VIEW_REPLAY", "View Replay");
		if (g.button("view", viewLabel, view[0], b.backY, view[1], b.backH, size,
				!playable ? Gui.TONE_OFF : this.art.reforged ? Gui.TONE_CONFIRM : Gui.TONE_DOWN)) {
			startReplay(chosen);
		}
	}

	/**
	 * Map Info: the replay's name, the map's minimap, the facts and the players; a replay this engine cannot play says
	 * why instead (the original's Incompatible Replay).
	 */
	private void replayInfo(final GameCatalog.ReplayEntry chosen, final float ix, final float infoW) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float pf = b.pageFrame;
		if ((chosen.mapFile != null) && !chosen.mapFile.equals(this.replayMinimapMap)) {
			final String map = chosen.mapFile;
			this.replayMinimapMap = map;
			this.replayMinimap = null;
			this.catalog.mapDetails(map, details -> {
				if (map.equals(this.replayMinimapMap) && (details != null) && (details.minimap != null)) {
					this.replayMinimap = this.art.region(details.minimap);
				}
			});
		}
		final boolean playable = chosen.playable();
		g.text(chosen.name, ix + pf, b.contentY + pf + (t ? 8 : 4), infoW - (2 * pf), t ? 60 : 40, t ? 44 : 28,
				g.palette.text, Align.center);
		final float pw = t ? 400 : 150;
		final float py = b.contentY + pf + (t ? 76 : 50);
		final float px = t ? ix + ((infoW - pw) / 2) : ix + pf + 16;
		g.fill(px - 2, py - 2, pw + 4, pw + 4, 0xFF444444);
		g.image(this.replayMinimap != null ? this.replayMinimap : this.art.image(SkinArt.MINIMAP_UNKNOWN), px, py, pw,
				pw, playable ? 1 : 0.5f);
		final float size = t ? 30 : 20;
		final float step = t ? 48 : 34;
		final float factsX = t ? ix + pf + 16 : px + pw + 20;
		final float factsW = t ? infoW - (2 * pf) - 32 : (ix + infoW) - pf - 16 - factsX;
		float y = t ? py + pw + 32 : py;
		if (chosen.damaged) {
			g.lines(s("replay_damaged"), factsX, y, factsW, 200, size, SkinArt.Palette.DANGER_TEXT, Align.left);
			return;
		}
		final List<String[]> facts = new ArrayList<>();
		if (t) {
			facts.add(new String[] { s("replay_map"), chosen.mapName() });
		}
		facts.add(new String[] { s("replay_length"), CampaignChoice.elapsed(chosen.length) });
		facts.add(new String[] { s("replay_recorded"),
			DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(chosen.recorded)) });
		facts.add(new String[] { s("replay_version"), chosen.version });
		for (int i = 0; i < facts.size(); i++) {
			g.text(facts.get(i)[0], factsX, y, factsW / 2, step, size, g.palette.gold, Align.left);
			final boolean version = i == (facts.size() - 1);
			g.text(facts.get(i)[1], factsX + (factsW * 0.4f), y, factsW * 0.6f, step, size,
					version && !playable ? SkinArt.Palette.DANGER_TEXT : g.palette.text, Align.right);
			y += step;
		}
		if (!playable) {
			final String why = chosen.warcraft ? s("replay_warcraft")
					: chosen.otherVersion ? glue("REPLAY_CONFIRM_DESC", s("replay_other_version"))
							: s("replay_unplayable").replace("%1$s", chosen.unplayable);
			g.lines(glue("REPLAY_CONFIRM", "Incompatible Replay!") + "\n" + why, factsX, y + (step * 0.3f), factsW,
					step * 3, size, SkinArt.Palette.DANGER_TEXT, Align.left);
			y += step * 3.3f;
		}
		else if (!t) {
			y = Math.max(y, py + pw) + (step * 0.3f);
		}
		final float playersX = t ? factsX : ix + pf + 16;
		final float playersW = t ? factsW : infoW - (2 * pf) - 32;
		final float square = size * 0.8f;
		final float renameW = t ? 320 : 220;
		final float renameH = t ? 112 : 96;
		final float renameY = (b.contentY + b.contentH) - pf - renameH;
		for (final GameCatalog.ReplayPlayer player : chosen.players) {
			if ((y + step) > (renameY - 8)) {
				break;
			}
			g.fill(playersX, y + ((step - square) / 2), square, square,
					CustomGame.PLAYER_COLORS[Math.abs(player.color) % CustomGame.PLAYER_COLORS.length]);
			final String name = player.name != null ? player.name
					: s("replay_computer") + " (" + glue("PLAYER", "Player") + " " + (player.slot + 1) + ")";
			final boolean symbol = player.raceIndex() > 0;
			g.text(name, playersX + square + 14, y, (playersW * 0.6f) - (symbol ? size * 1.5f : 0), step, size,
					g.palette.text, Align.left);
			if (symbol) {
				final float mark = size * 1.3f;
				g.image(this.art.image(SkinArt.RACE_SYMBOLS[player.raceIndex() - 1]),
						playersX + (playersW * 0.6f) - (size * 1.4f), y + ((step - mark) / 2), mark, mark);
			}
			g.text(raceName(player), playersX + (playersW * 0.6f), y, playersW * 0.4f, step, size, g.palette.text2,
					Align.right);
			y += step;
		}
		if (g.button("renameReplay", s("replay_rename"), (ix + infoW) - pf - renameW, renameY, renameW, renameH,
				t ? 30 : 24, Gui.TONE_NORMAL)) {
			playClick();
			openRenameReplay(chosen);
		}
	}

	private GameCatalog.ReplayEntry renamingReplay;
	private GameCatalog.ReplayEntry triedReplay;

	private void openRenameReplay(final GameCatalog.ReplayEntry entry) {
		this.renamingReplay = entry;
		this.renameField.setLength(0);
		this.renameField.append(entry.name);
		this.dialog = Dialog.RENAME_REPLAY;
	}

	/** Rename Replay: the name field and the file it makes; a taken name asks before the other replay goes. */
	private void renameReplayDialog() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final GameCatalog.ReplayEntry entry = this.renamingReplay;
		final float w = t ? 1400 : 1000;
		final float h = t ? 560 : 400;
		final float x = (b.width - w) / 2;
		final float y = (b.height - h) / 2;
		final float edge = t ? 48 : 32;
		g.dialog(x, y, w, h);
		g.text(s("replay_rename_title"), x + edge + 20, y + edge + 10, w, t ? 70 : 50, t ? 44 : 32, g.palette.gold,
				Align.left);
		g.focus = "renameReplay";
		g.field("renameReplay", this.renameField, x + edge + 20, y + edge + (t ? 100 : 70), w - (2 * edge) - 40,
				t ? 112 : 80, t ? 40 : 28, 64, "");
		final String typed = this.renameField.toString().trim();
		final boolean usable = (entry != null) && !CSaveStorage.safeName(typed).isEmpty();
		final File target = usable ? GameCatalog.replayFile(entry, typed) : null;
		g.text(target != null ? GameReplays.FOLDER + "/" + target.getName() : "", x + edge + 20,
				y + edge + (t ? 220 : 160), w - (2 * edge) - 40, t ? 50 : 36, t ? 30 : 22, g.palette.text2,
				Align.left);
		final float buttonW = t ? 360 : 260;
		final float buttonH = t ? 112 : 96;
		final float by = (y + h) - edge - 20 - buttonH;
		boolean close = false;
		if (g.button("renameReplayCancel", glue("CANCEL", "Cancel"), (x + w) - edge - 20 - (2 * buttonW) - 32, by,
				buttonW, buttonH, t ? 40 : 30, Gui.TONE_NORMAL)) {
			close = true;
		}
		if ((g.button("renameReplayOk", s("game_ok"), (x + w) - edge - 20 - buttonW, by, buttonW, buttonH,
				t ? 40 : 30, usable ? Gui.TONE_NORMAL : Gui.TONE_OFF) || g.keys.contains(Input.Keys.ENTER))
				&& usable) {
			g.focus = null;
			playClick();
			if (target.equals(entry.file)) {
				this.dialog = Dialog.NONE;
			}
			else if (target.exists()) {
				this.dialog = Dialog.OVERWRITE_REPLAY;
			}
			else {
				renameReplay(entry, typed);
			}
			return;
		}
		if (close) {
			playClick();
			g.focus = null;
			this.dialog = Dialog.NONE;
		}
	}

	private void renameReplay(final GameCatalog.ReplayEntry entry, final String name) {
		this.dialog = Dialog.NONE;
		final File renamed = GameCatalog.renameReplay(entry, name);
		if (renamed != null) {
			this.preferences.edit().putString(LauncherKeys.KEY_REPLAY, renamed.getName()).apply();
		}
		this.catalog.replays(found -> this.replayList = found);
	}

	// ---------------------------------------------------------------- Options

	private float tabsRow(final String[] names, final int selected, final java.util.function.IntConsumer chosen) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final int n = 6;
		final float tabW = (b.contentW - ((n - 1) * b.tabGap)) / n;
		for (int i = 0; i < names.length; i++) {
			if (g.button("tab" + i, names[i], b.contentX + (i * (tabW + b.tabGap)), b.contentY, tabW, b.tabH, b.tabText,
					i == selected ? Gui.TONE_DOWN : Gui.TONE_NORMAL)) {
				playClick();
				chosen.accept(i);
			}
		}
		return b.contentY + b.tabH + (b.tablet ? 40 : 14);
	}

	/** A row of the options panel: the line above it and the gold label. */
	private float optionRow(final float x, final float y, final float w, final int index, final String label) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		if (index > 0) {
			g.fill(x, y, w, 2, 0x14FFFFFF);
		}
		g.text(label, x + b.labelX, y, b.controlX - b.labelX - b.pageFrame - 20, b.rowH, b.body, g.palette.gold,
				Align.left);
		return y + ((b.rowH - b.control) / 2);
	}

	private void optionsPage() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float pf = b.pageFrame;
		final float top = tabsRow(new String[] { s("options_graphics"), s("options_sound"), s("options_gameplay"),
			s("options_controls"), s("options_game_data"), s("options_about") }, this.optionsTab,
				tab -> this.optionsTab = tab);
		if (this.optionsTab != 3) {
			this.capturing = null;
		}
		final List<Runnable> rows = new ArrayList<>();
		final float x = b.contentX + pf;
		final float w = b.contentW - (2 * pf);
		final float controlX = x + (b.controlX - pf);
		final float[] rowY = { 0 };
		final int[] index = { 0 };
		final EngineSettings engine = new EngineSettings(this.saves, this.profiles.current(), this.iniFile);
		this.optionsExtraH = 0;
		switch (this.optionsTab) {
		case 1:
			engineRows(rows, rowY, index, x, w, controlX, engine, LauncherOptions.SOUND);
			rows.add(checkRow(rowY, index, x, w, controlX, s("options_menu_music"),
					this.preferences.getBoolean(LauncherKeys.KEY_MUSIC, true), on -> {
						this.preferences.edit().putBoolean(LauncherKeys.KEY_MUSIC, on).apply();
						updateMusic();
					}));
			break;
		case 2:
			engineRows(rows, rowY, index, x, w, controlX, engine, LauncherOptions.GAMEPLAY);
			rows.add(infoRow(rowY, index, x, w, controlX, s("options_profile"), this.profiles.current()));
			break;
		case 3:
			controlsPage(top);
			if (g.button("back", glue("BACK", s("launcher_back")), b.backX, b.backY, b.backW, b.backH, t ? 36 : 30,
					Gui.TONE_NORMAL)) {
				playClick();
				showPage(Page.HOME);
			}
			return;
		case 4:
			gameDataRows(rows, rowY, index, x, w, controlX);
			break;
		case 5:
			rows.add(infoRow(rowY, index, x, w, controlX, s("options_launcher"), versionLine()));
			rows.add(infoRow(rowY, index, x, w, controlX, s("options_engine"), s("options_engine_value")));
			rows.add(infoRow(rowY, index, x, w, controlX, s("options_license"), s("options_license_value")));
			rows.add(infoRow(rowY, index, x, w, controlX, s("options_art"), s("options_art_value")));
			rows.add(checkRow(rowY, index, x, w, controlX, s("options_check_updates"),
					this.preferences.getBoolean(LauncherKeys.KEY_UPDATE_CHECK, true),
					on -> this.preferences.edit().putBoolean(LauncherKeys.KEY_UPDATE_CHECK, on).apply()));
			final String skipped = this.preferences.getString(LauncherKeys.KEY_UPDATE_SKIPPED, "");
			if (!skipped.isEmpty()) {
				final int at = index[0]++;
				rows.add(() -> {
					final LauncherBoard bd = this.board;
					optionRow(x, rowY[0], w, at, s("options_skipped_update"));
					if (this.gui.button("skippedUpdate", s("options_skipped_update_clear") + " · " + skipped, controlX,
							rowY[0] + 8, 640, bd.rowH - 16, bd.tablet ? 32 : 24, Gui.TONE_NORMAL)) {
						playClick();
						this.preferences.edit().remove(LauncherKeys.KEY_UPDATE_SKIPPED).apply();
					}
					rowY[0] += bd.rowH;
				});
			}
			break;
		default:
			graphicsRows(rows, rowY, index, x, w, controlX, engine);
			break;
		}
		final float want = (index[0] * b.rowH) + this.optionsExtraH + (2 * pf) + (this.optionsTab == 4 ? b.buttonH + (t ? 40 : 16) : 0);
		final float panelH = Math.min(want, (b.contentY + b.contentH) - top);
		g.panel(b.contentX, top, b.contentW, panelH, pf);
		final float offset = g.beginList("options" + this.optionsTab, x, top + pf, w, panelH - (2 * pf),
				want - (2 * pf));
		rowY[0] = (top + pf) - offset;
		for (final Runnable row : rows) {
			row.run();
		}
		g.endList();
		if (g.button("back", glue("BACK", s("launcher_back")), b.backX, b.backY, b.backW, b.backH, t ? 36 : 30,
				Gui.TONE_NORMAL)) {
			playClick();
			showPage(Page.HOME);
		}
	}

	private void engineRows(final List<Runnable> rows, final float[] rowY, final int[] index, final float x,
			final float w, final float controlX, final EngineSettings engine, final List<LauncherOptions.Row> table) {
		for (final LauncherOptions.Row row : table) {
			switch (row.kind) {
			case CHECK:
				rows.add(checkRow(rowY, index, x, w, controlX, s(row.label), row.value(engine) != 0,
						on -> row.set(engine, on ? 1 : 0)));
				break;
			case RADIO: {
				final String[] choices = new String[row.choices.size()];
				for (int i = 0; i < choices.length; i++) {
					choices[i] = s(row.choices.get(i));
				}
				rows.add(radioRow(rowY, index, x, w, controlX, s(row.label), choices, null, row.value(engine),
						choice -> row.set(engine, choice)));
				break;
			}
			default:
				rows.add(sliderRow(rowY, index, x, w, controlX, s(row.label), row.value(engine),
						s(row.choices.get(0)), s(row.choices.get(1)), value -> row.set(engine, value)));
				break;
			}
		}
	}

	private Runnable checkRow(final float[] rowY, final int[] index, final float x, final float w,
			final float controlX, final String label, final boolean value,
			final java.util.function.Consumer<Boolean> changed) {
		final int at = index[0]++;
		return () -> {
			final float cy = optionRow(x, rowY[0], w, at, label);
			if (this.gui.check("check" + label, controlX, cy, this.board.control, value)) {
				playClick();
				changed.accept(!value);
			}
			rowY[0] += this.board.rowH;
		};
	}

	private Runnable radioRow(final float[] rowY, final int[] index, final float x, final float w,
			final float controlX, final String label, final String[] choices, final boolean[] enabled,
			final int selected, final java.util.function.IntConsumer changed) {
		final int at = index[0]++;
		final LauncherBoard b = this.board;
		final float[] names = new float[choices.length];
		for (int j = 0; j < choices.length; j++) {
			names[j] = this.gui.width(choices[j], b.body);
		}
		final LauncherBoard.RadioPlaces places = b.radioPlaces(names, w);
		this.optionsExtraH += places.height - b.rowH;
		return () -> {
			final float y = rowY[0];
			if (places.ownLine) {
				if (at > 0) {
					this.gui.fill(x, y, w, 2, 0x14FFFFFF);
				}
				this.gui.text(label, x + b.labelX, y, w - b.labelX - 20, places.labelH, b.body,
						this.gui.palette.gold, Align.left);
			}
			else {
				optionRow(x, y, w, at, label);
			}
			final float cy = y + places.choicesY + ((b.rowH - b.control) / 2);
			for (int j = 0; j < choices.length; j++) {
				final boolean open = (enabled == null) || enabled[j];
				final float rx = x + places.x[j];
				final boolean clicked = this.gui.radio("radio" + label + j, rx, cy, b.control, j == selected,
						places.w[j]);
				this.gui.text(choices[j], rx + b.control + LauncherBoard.RADIO_NAME_GAP, y + places.choicesY,
						places.w[j] - b.control - LauncherBoard.RADIO_NAME_GAP, b.rowH, b.body,
						open ? this.gui.palette.text : SkinArt.Palette.DISABLED, Align.left);
				if (clicked && open && (j != selected)) {
					playClick();
					changed.accept(j);
				}
			}
			rowY[0] += places.height;
		};
	}

	private Runnable sliderRow(final float[] rowY, final int[] index, final float x, final float w,
			final float controlX, final String label, final int value, final String low, final String high,
			final java.util.function.IntConsumer changed) {
		final int at = index[0]++;
		return () -> {
			final LauncherBoard b = this.board;
			final float cy = optionRow(x, rowY[0], w, at, label);
			final float lowW = b.tablet ? 90 : 72;
			final float sliderW = b.tablet ? 720 : 380;
			this.gui.text(low, controlX, rowY[0], lowW, b.rowH, b.body * 0.85f, this.gui.palette.text, Align.left);
			final int next = this.gui.slider("slider" + label, controlX + lowW, cy, sliderW, b.control, value);
			if (this.gui.released("slider" + label) && (next != value)) {
				changed.accept(next);
			}
			this.gui.text(high, controlX + lowW + sliderW + (b.tablet ? 20 : 12), rowY[0], b.tablet ? 240 : 140, b.rowH,
					b.body * 0.85f, this.gui.palette.text, Align.left);
			rowY[0] += b.rowH;
		};
	}

	private Runnable infoRow(final float[] rowY, final int[] index, final float x, final float w,
			final float controlX, final String label, final String value) {
		final int at = index[0]++;
		return () -> {
			final LauncherBoard b = this.board;
			optionRow(x, rowY[0], w, at, label);
			final boolean cut = this.gui.text(value, controlX, rowY[0], (x + w) - controlX - 20, b.rowH, b.body,
					this.gui.palette.text, Align.left);
			if (cut && this.gui.over(controlX, rowY[0], (x + w) - controlX, b.rowH)) {
				this.gui.tip(value);
			}
			rowY[0] += b.rowH;
		};
	}

	private void graphicsRows(final List<Runnable> rows, final float[] rowY, final int[] index, final float x,
			final float w, final float controlX, final EngineSettings engine) {
		final boolean reforgedData = this.data.has("webui\\common\\homescreen.jpg");
		rows.add(radioRow(rowY, index, x, w, controlX, s("options_interface"),
				new String[] { s("launcher_classic"), s(reforgedData ? "launcher_reforged" : "options_reforged_locked") },
				new boolean[] { true, reforgedData }, this.art.reforged ? 1 : 0, choice -> chooseSkin(choice == 1)));
		final GraphicsSet[] sets = GraphicsSet.values();
		final String[] setNames = new String[sets.length];
		final boolean[] setsHere = new boolean[sets.length];
		for (int i = 0; i < sets.length; i++) {
			setsHere[i] = (this.data != null) && sets[i].isAvailable(this.data);
			setNames[i] = s(LauncherOptions.GRAPHICS_SETS[i][setsHere[i] ? 0 : 1]);
		}
		final int setChoice = graphicsSet().ordinal();
		rows.add(radioRow(rowY, index, x, w, controlX, s("options_ingame_graphics"), setNames, setsHere,
				setsHere[setChoice] ? setChoice : 0, choice -> this.preferences.edit()
						.putString(LauncherKeys.KEY_GRAPHICS, sets[choice].getIniName()).apply()));
		final String[] sizeNames = new String[LauncherOptions.TEXTURE_SIZES.length];
		for (int i = 0; i < sizeNames.length; i++) {
			sizeNames[i] = s(LauncherOptions.TEXTURE_SIZES[i]);
		}
		rows.add(radioRow(rowY, index, x, w, controlX, s("options_texture_size"), sizeNames, null,
				LauncherOptions.textureSizeChoice(textureSize()), choice -> this.preferences.edit()
						.putInt(LauncherKeys.KEY_TEXTURE_SIZE, GraphicsSettings.TEXTURE_SIZES[choice]).apply()));
		rows.add(radioRow(rowY, index, x, w, controlX, s("options_display_mode"),
				new String[] { s("options_window"), s("options_fullscreen") }, null, Gdx.graphics.isFullscreen() ? 1 : 0,
				choice -> setFullscreen(choice == 1)));
		final int at = index[0]++;
		rows.add(() -> uiScaleRow(rowY, at, x, w, controlX));
		rows.add(checkRow(rowY, index, x, w, controlX, s("options_vsync"),
				this.preferences.getBoolean(KEY_VSYNC, true), on -> {
					this.preferences.edit().putBoolean(KEY_VSYNC, on).apply();
					Gdx.graphics.setVSync(on);
				}));
		rows.add(checkRow(rowY, index, x, w, controlX, s("options_animated"),
				this.preferences.getBoolean(LauncherKeys.KEY_ANIMATED, true), on -> {
					this.preferences.edit().putBoolean(LauncherKeys.KEY_ANIMATED, on).apply();
					if (on) {
						startRenderer();
					}
					else {
						stopRenderer();
					}
				}));
		engineRows(rows, rowY, index, x, w, controlX, engine, LauncherOptions.GRAPHICS);
	}

	/** UI scale: Auto by the window's height, or a value from 80 to 150 % in steps of 5. */
	private void uiScaleRow(final float[] rowY, final int at, final float x, final float w, final float controlX) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final float cy = optionRow(x, rowY[0], w, at, s("options_ui_scale"));
		final boolean auto = this.preferences.getInt(KEY_UI_SCALE, 0) <= 0;
		final int current = uiPercent();
		float cx = controlX;
		if (g.check("uiAuto", cx, cy, b.control, auto)) {
			setUiScale(auto ? current : 0);
		}
		if (g.over(cx, cy, b.control, b.control)) {
			g.tip(s("options_ui_auto_tip"));
		}
		cx += b.control + 14;
		g.text(s("options_ui_auto"), cx, rowY[0], b.tablet ? 120 : 80, b.rowH, b.body, g.palette.text, Align.left);
		cx += b.tablet ? 140 : 90;
		final float buttonW = b.control * 1.4f;
		if (g.button("uiDown", "-", cx, cy, buttonW, b.control, b.body, Gui.TONE_NORMAL)) {
			setUiScale(Math.max(LauncherBoard.UI_MIN, current - 5));
		}
		cx += buttonW + (b.tablet ? 16 : 8);
		final float valueW = b.tablet ? 200 : 120;
		final float sliderW = Math.min(b.tablet ? 520 : 260, (x + w) - cx - buttonW - (b.tablet ? 40 : 20) - valueW);
		final int value = Math.round(((current - LauncherBoard.UI_MIN) * 100f)
				/ (LauncherBoard.UI_MAX - LauncherBoard.UI_MIN));
		final int next = g.slider("uiSlider", cx, cy, sliderW, b.control, value);
		final int percent = LauncherBoard.UI_MIN
				+ (Math.round(((LauncherBoard.UI_MAX - LauncherBoard.UI_MIN) * next) / 500f) * 5);
		if (g.released("uiSlider") && (percent != current)) {
			setUiScale(percent);
		}
		cx += sliderW + (b.tablet ? 16 : 8);
		if (g.button("uiUp", "+", cx, cy, buttonW, b.control, b.body, Gui.TONE_NORMAL)) {
			setUiScale(Math.min(LauncherBoard.UI_MAX, current + 5));
		}
		cx += buttonW + (b.tablet ? 24 : 12);
		g.text(percent + " %", cx, rowY[0], valueW, b.rowH, b.body, g.palette.gold, Align.left);
		rowY[0] += b.rowH;
	}

	// ---------------------------------------------------------------- Options > Controls

	private CustomKeys customKeys() {
		if (this.customKeys == null) {
			this.customKeys = CustomKeys.read(CustomKeys.playerFile(),
					CustomKeys.LAYOUT_GRID.equals(this.engineStore.getString(CustomKeys.KEY_LAYOUT, "")));
		}
		return this.customKeys;
	}

	private void saveKeys() {
		try {
			customKeys().write(CustomKeys.playerFile());
		}
		catch (final IOException e) {
			LauncherLog.w("Unable to write the hotkeys", e);
		}
	}

	private String keyOf(final HotkeyCatalog.Command command) {
		return customKeys().key(command.section, command.field, command.dataKey, command.x, command.y);
	}

	private String commandName(final HotkeyCatalog.Command command) {
		return CustomKeys.OFF.equals(command.field) ? s("controls_off").replace("%1$s", command.name) : command.name;
	}

	/**
	 * The hotkey editor: the layout (the game data's keys or the grid) and Reset, the races, a race's units with their
	 * command cards as in the game, the chosen command's tooltip, and every command of the card with its key; a click
	 * on a key waits for the new one. Two commands of one card with one key are shown red, with the reason.
	 */
	private void controlsPage(final float top) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float pf = b.pageFrame;
		final float k = t ? 1 : 0.62f;
		final float bottom = b.contentY + b.contentH;
		g.panel(b.contentX, top, b.contentW, bottom - top, pf);
		final float x0 = b.contentX + pf + (20 * k);
		final float y0 = top + pf + (16 * k);
		final float right = (b.contentX + b.contentW) - pf - (20 * k);
		final float inner = bottom - pf - (16 * k);
		final float big = Math.max(t ? 32 : 22, b.body * k);
		final float small = Math.max(t ? 28 : 19, b.small * k);
		final CustomKeys keys = customKeys();

		final float headH = 80 * k;
		g.text(s("controls_layout"), x0, y0, (360 * k) + 60, headH, big, g.palette.gold, Align.left);
		final float radio = b.control * (t ? 0.9f : 0.8f);
		final String[] layouts = { s("controls_classic"), s("controls_grid") };
		for (int j = 0; j < layouts.length; j++) {
			final float rx = x0 + (380 * k) + 60 + (j * (t ? 380 : 250));
			if (g.radio("layout" + j, rx, y0 + ((headH - radio) / 2), radio, keys.grid() == (j == 1), t ? 360 : 230)) {
				playClick();
				this.capturing = null;
				keys.setGrid(j == 1);
				this.engineStore.edit().putString(CustomKeys.KEY_LAYOUT, j == 1 ? CustomKeys.LAYOUT_GRID : "classic")
						.apply();
			}
			g.text(layouts[j], rx + radio + 14, y0, t ? 320 : 210, headH, big, g.palette.text, Align.left);
		}
		final float resetW = t ? 520 : 330;
		if (g.button("resetKeys", s("controls_reset"), right - resetW, y0 - 4, resetW, t ? 96 : 76, t ? 32 : 22,
				keys.isEmpty() ? Gui.TONE_OFF : Gui.TONE_NORMAL)) {
			playClick();
			this.capturing = null;
			keys.clear();
			saveKeys();
		}

		if (this.hotkeys == null) {
			if (!this.readingHotkeys && (this.catalog != null)) {
				this.readingHotkeys = true;
				final Map<String, String> words = new HashMap<>();
				for (final String word : new String[] { "shared_units", "shared_structures", "card_commands", "card_siege",
					"card_construction", "card_build", "card_learn" }) {
					words.put(word, s("controls_" + word));
				}
				this.catalog.hotkeys(words, found -> this.hotkeys = found);
			}
			g.text(s("game_loading_list"), x0, y0 + (120 * k), right - x0, 100 * k, big, g.palette.text2, Align.left);
			return;
		}

		final List<HotkeyCatalog.Race> races = this.hotkeys.races;
		final float raceY = y0 + (100 * k);
		final float raceH = t ? 84 : 52;
		final float raceW = Math.min(t ? 300 : 200, ((right - x0) - ((races.size() - 1) * 12)) / races.size());
		this.keysRace = Math.max(0, Math.min(races.size() - 1, this.keysRace));
		for (int i = 0; i < races.size(); i++) {
			final String[] name = races.get(i).name;
			if (g.button("race" + i, name == null ? s("controls_common") : glue(name[0], name[1]),
					x0 + (i * (raceW + 12)), raceY, raceW, raceH, t ? 30 : 21,
					i == this.keysRace ? Gui.TONE_DOWN : Gui.TONE_NORMAL) && (i != this.keysRace)) {
				playClick();
				this.keysRace = i;
				this.keysUnit = 0;
				this.keysCard = 0;
				this.keysCommand = null;
				this.capturing = null;
			}
		}

		// The race's units, heroes and buildings.
		final HotkeyCatalog.Race race = races.get(this.keysRace);
		final float listY = raceY + raceH + (24 * k);
		final float listW = t ? 560 : 330;
		final float listH = inner - listY;
		final float unitH = t ? 96 : 52;
		g.edit(x0, listY, listW, listH, t ? 14 : 8, 0x8C000000);
		this.keysUnit = Math.max(0, Math.min(race.units.size() - 1, this.keysUnit));
		final float unitsTop = listY + 12;
		final float offset = g.beginList("keyUnits" + this.keysRace, x0 + 12, unitsTop, listW - 24, listH - 24,
				race.units.size() * unitH);
		float uy = unitsTop - offset;
		for (int i = 0; i < race.units.size(); i++) {
			final HotkeyCatalog.Unit unit = race.units.get(i);
			final int state = g.hit("keyUnit" + unit.id, x0 + 12, uy, listW - 24, unitH);
			if ((i == this.keysUnit) || ((state & Gui.HOVER) != 0)) {
				g.image(this.art.image(SkinArt.ROW_HIGHLIGHT), x0 + 12, uy, listW - 24, unitH,
						i == this.keysUnit ? 1 : 0.5f);
			}
			final float icon = unitH * 0.76f;
			g.fill(x0 + 20, (uy + (unitH * 0.12f)) - 2, icon + 4, icon + 4, 0xFF000000);
			g.cover(unit.icon == null ? null : this.art.picture(unit.icon), x0 + 22, uy + (unitH * 0.12f), icon, icon,
					0.5f);
			final boolean cut = g.text(unit.name, x0 + 22 + icon + 22, uy, listW - icon - 80, unitH, big,
					i == this.keysUnit ? g.palette.gold : g.palette.text, Align.left);
			if (cut && ((state & Gui.HOVER) != 0)) {
				g.tip(unit.name);
			}
			if (((state & Gui.CLICK) != 0) && (i != this.keysUnit)) {
				playClick();
				this.keysUnit = i;
				this.keysCard = 0;
				this.keysCommand = null;
				this.capturing = null;
			}
			uy += unitH;
		}
		g.endList();
		if (race.units.isEmpty()) {
			return;
		}

		// The command card, 4 x 3 as in the game, with each button's key in its corner.
		final HotkeyCatalog.Unit unit = race.units.get(this.keysUnit);
		this.keysCard = Math.max(0, Math.min(unit.cards.size() - 1, this.keysCard));
		final HotkeyCatalog.Card card = unit.cards.get(this.keysCard);
		final java.util.Set<String> conflicts = HotkeyCatalog.conflicts(card, keys);
		final float cell = t ? 118 : 56;
		final float gap = t ? 10 : 5;
		final float cardW = (4 * cell) + (3 * gap);
		final float cx = x0 + listW + (t ? 40 : 24);
		final float tabH = t ? 60 : 40;
		if (unit.cards.size() > 1) {
			final float cardTabW = (cardW - ((unit.cards.size() - 1) * 8)) / unit.cards.size();
			for (int i = 0; i < unit.cards.size(); i++) {
				if (g.button("card" + i, unit.cards.get(i).name, cx + (i * (cardTabW + 8)), listY, cardTabW, tabH,
						small * 0.85f, i == this.keysCard ? Gui.TONE_DOWN : Gui.TONE_NORMAL) && (i != this.keysCard)) {
					playClick();
					this.keysCard = i;
					this.keysCommand = null;
					this.capturing = null;
				}
			}
		}
		else {
			g.text(s("controls_card"), cx, listY, cardW, tabH, small, g.palette.gold, Align.left);
		}
		final float gy = listY + tabH + (t ? 16 : 8);
		g.fill(cx - 8, gy - 8, cardW + 16, (3 * cell) + (2 * gap) + 16, 0xFF000000);
		g.outline(cx - 11, gy - 11, cardW + 22, (3 * cell) + (2 * gap) + 22, 3, 0xFF3A3631);
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 4; column++) {
				final float px = cx + (column * (cell + gap));
				final float py = gy + (row * (cell + gap));
				g.fill(px, py, cell, cell, 0xFF0C0C0C);
				g.outline(px, py, cell, cell, 2, 0xFF222222);
			}
		}
		HotkeyCatalog.Command selected = null;
		for (final HotkeyCatalog.Command command : card.commands) {
			if (command.id().equals(this.keysCommand)) {
				selected = command;
			}
		}
		if ((selected == null) && !card.commands.isEmpty()) {
			selected = card.commands.get(0);
			this.keysCommand = selected.id();
		}
		for (final HotkeyCatalog.Command command : card.commands) {
			if (command.x < 0) {
				continue;
			}
			final float px = cx + (command.x * (cell + gap));
			final float py = gy + (command.y * (cell + gap));
			final int state = g.hit("cell" + command.id(), px, py, cell, cell);
			g.cover(command.icon == null ? null : this.art.picture(command.icon), px, py, cell, cell, 0.5f);
			if (command == selected) {
				g.outline(px - 2, py - 2, cell + 4, cell + 4, t ? 4 : 3, 0xFFFCD312);
			}
			else if ((state & Gui.HOVER) != 0) {
				g.fill(px, py, cell, cell, 0x22FFFFFF);
			}
			final float badge = cell * 0.36f;
			final String shown = CustomKeys.shown(keyOf(command));
			final float badgeW = shown.length() > 1 ? cell * 0.7f : badge;
			g.fill((px + cell) - badgeW, (py + cell) - badge, badgeW, badge, 0xC0000000);
			g.text(shown, (px + cell) - badgeW, (py + cell) - badge, badgeW, badge, badge * 0.75f,
					conflicts.contains(command.id()) ? 0xFFFF6A5A : 0xFFFFFFFF, Align.center);
			if ((state & Gui.CLICK) != 0) {
				playClick();
				this.keysCommand = command.id();
				this.capturing = null;
			}
		}

		// The chosen command's tooltip as the game shows it, with its key as the player has it.
		final float tipY = gy + (3 * cell) + (2 * gap) + (t ? 36 : 18);
		final float tipH = inner - tipY;
		if ((selected != null) && (tipH > 40)) {
			g.fill(cx - 8, tipY, cardW + 16, tipH, 0xE0000000);
			g.outline(cx - 8, tipY, cardW + 16, tipH, 3, 0xFF7D7462);
			final float titleSize = t ? big : small;
			final String title = CustomKeys.titled(selected.tip, selected.dataKey, keyOf(selected)).replace("%d", "1");
			g.text(title, cx + 8, tipY + 8, cardW - 16, titleSize * 1.5f, titleSize, 0xFFFFFFFF, Align.left);
			g.lines(selected.text, cx + 8, tipY + 16 + (titleSize * 1.5f), cardW - 16,
					tipH - 24 - (titleSize * 1.5f), small * 0.85f, 0xFFE8E0C8, Align.left);
		}

		// Every command of the card with its key.
		final float kx = cx + cardW + (t ? 48 : 28);
		final float kw = right - kx;
		final float keyW = t ? 220 : 130;
		final float rowH = t ? 84 : 44;
		g.text(s("controls_command"), kx, listY, kw - keyW, tabH, small, g.palette.gold, Align.left);
		g.text(s("controls_key"), (kx + kw) - keyW, listY, keyW, tabH, small, g.palette.gold, Align.center);
		final float noteH = t ? 130 : 84;
		final float rowsH = Math.max(rowH, inner - gy - noteH);
		final float rowsOffset = g.beginList("keyCommands" + unit.id + this.keysCard, kx, gy, kw, rowsH,
				card.commands.size() * rowH);
		float ry = gy - rowsOffset;
		for (final HotkeyCatalog.Command command : card.commands) {
			final boolean bad = conflicts.contains(command.id());
			final boolean waiting = command.id().equals(this.capturing);
			final int state = g.hit("row" + command.id(), kx, ry, kw - keyW - 12, rowH);
			if ((command == selected) || ((state & Gui.HOVER) != 0)) {
				g.image(this.art.image(SkinArt.ROW_HIGHLIGHT), kx, ry, kw, rowH, command == selected ? 1 : 0.5f);
			}
			final float icon = rowH * 0.8f;
			g.cover(command.icon == null ? null : this.art.picture(command.icon), kx + 6, ry + (rowH * 0.1f), icon,
					icon, 0.5f);
			final String name = commandName(command);
			final boolean cut = g.text(name, kx + icon + 22, ry, kw - keyW - icon - 40, rowH, small * 1.05f,
					bad ? 0xFFFF8A7A : g.palette.text, Align.left);
			if (cut && ((state & Gui.HOVER) != 0)) {
				g.tip(name);
			}
			if ((state & Gui.CLICK) != 0) {
				playClick();
				this.keysCommand = command.id();
				this.capturing = null;
			}
			final float keyX = (kx + kw) - keyW;
			final int keyState = g.hit("key" + command.id(), keyX, ry + (rowH * 0.1f), keyW, rowH * 0.8f);
			g.edit(keyX, ry + (rowH * 0.1f), keyW, rowH * 0.8f, t ? 12 : 8, waiting ? 0xE63C2D05 : 0xD9000000);
			if ((keyState & Gui.HOVER) != 0) {
				g.fill(keyX + 4, ry + (rowH * 0.1f) + 4, keyW - 8, (rowH * 0.8f) - 8, 0x22FFFFFF);
				g.tip(s("controls_key_tip"));
			}
			g.text(waiting ? s("controls_press") : CustomKeys.shown(keyOf(command)), keyX, ry + (rowH * 0.1f), keyW,
					rowH * 0.8f, waiting ? small * 0.8f : big,
					bad ? 0xFFFF6A5A : waiting ? 0xFFFFFFFF : g.palette.gold, Align.center);
			if ((keyState & Gui.CLICK) != 0) {
				playClick();
				this.keysCommand = command.id();
				this.capturing = waiting ? null : command.id();
			}
			ry += rowH;
		}
		g.endList();
		final float noteY = gy + rowsH + (t ? 16 : 8);
		if (!conflicts.isEmpty()) {
			final Map<String, List<String>> byKey = new java.util.LinkedHashMap<>();
			for (final HotkeyCatalog.Command command : card.commands) {
				if (conflicts.contains(command.id())) {
					byKey.computeIfAbsent(CustomKeys.shown(keyOf(command)), key -> new ArrayList<>())
							.add(commandName(command));
				}
			}
			final StringBuilder list = new StringBuilder();
			for (final Map.Entry<String, List<String>> entry : byKey.entrySet()) {
				if (list.length() > 0) {
					list.append("; ");
				}
				list.append(entry.getKey()).append(" \u2013 ").append(String.join(" / ", entry.getValue()));
			}
			g.lines(s("controls_conflict").replace("%1$s", list), kx, noteY, kw, inner - noteY, small, 0xFFFF8A7A,
					Align.left);
		}
		else {
			g.lines(s(keys.grid() ? "controls_line_grid" : "controls_line_classic"), kx, noteY, kw, inner - noteY,
					small, g.palette.text2, Align.left);
		}
	}

	/** The key pressed while a command waits for its new one: a letter or a digit sets it, Escape keeps the old. */
	private void captureKey(final int keycode) {
		final String command = this.capturing;
		this.capturing = null;
		String key = null;
		if ((keycode >= Input.Keys.A) && (keycode <= Input.Keys.Z)) {
			key = String.valueOf((char) ('A' + (keycode - Input.Keys.A)));
		}
		else if ((keycode >= Input.Keys.NUM_0) && (keycode <= Input.Keys.NUM_9)) {
			key = String.valueOf((char) ('0' + (keycode - Input.Keys.NUM_0)));
		}
		else if ((keycode >= Input.Keys.NUMPAD_0) && (keycode <= Input.Keys.NUMPAD_9)) {
			key = String.valueOf((char) ('0' + (keycode - Input.Keys.NUMPAD_0)));
		}
		if ((key == null) || (this.hotkeys == null)) {
			if (keycode != Input.Keys.ESCAPE) {
				this.capturing = command;
			}
			return;
		}
		playClick();
		final CustomKeys keys = customKeys();
		for (final HotkeyCatalog.Race race : this.hotkeys.races) {
			for (final HotkeyCatalog.Unit unit : race.units) {
				for (final HotkeyCatalog.Card card : unit.cards) {
					for (final HotkeyCatalog.Command found : card.commands) {
						if (found.id().equals(command)) {
							keys.remove(found.section, found.field);
							if (!key.equals(CustomKeys.shown(keyOf(found)))) {
								keys.set(found.section, found.field, key);
							}
							saveKeys();
							return;
						}
					}
				}
			}
		}
	}

	private void setUiScale(final int percent) {
		playClick();
		this.preferences.edit().putInt(KEY_UI_SCALE, percent).apply();
		this.art.dropFonts();
		this.sceneKeys[0] = null;
		this.sceneKeys[1] = null;
	}

	private void setFullscreen(final boolean fullscreen) {
		this.preferences.edit().putBoolean(KEY_FULLSCREEN, fullscreen).apply();
		LauncherWindow.setFullscreen(this.launcherPreferences, fullscreen);
	}

	private GraphicsSet graphicsSet() {
		return GraphicsSet.fromIniName(this.preferences.getString(LauncherKeys.KEY_GRAPHICS, null));
	}

	/** The desktop has the memory for every texture at its full size unless the player sets a limit. */
	private int textureSize() {
		return this.preferences.getInt(LauncherKeys.KEY_TEXTURE_SIZE, 0);
	}

	private void chooseSkin(final boolean reforged) {
		this.preferences.edit().putString(LauncherKeys.KEY_SKIN, reforged ? SkinArt.REFORGED_SKIN : SkinArt.CLASSIC_SKIN)
				.apply();
		// The rest of this frame still draws with the old art (its font generator included), so it is changed
		// before the next frame begins.
		this.skinChanged = true;
	}

	private void changeSkin() {
		this.skinChanged = false;
		final LauncherArt old = this.art;
		this.art = new LauncherArt(this.data, reforgedSkin());
		this.minimap = (this.mapDetails != null) && (this.mapDetails.minimap != null)
				? this.art.region(this.mapDetails.minimap)
				: null;
		this.savePictures.clear();
		old.dispose();
		updateMusic();
	}

	private boolean installedGame() {
		final File folder = dataFolder();
		return (folder != null) && (DataFolderCheck.detect(folder) == DataFolderCheck.Kind.CASC);
	}

	/** The folder of the engine configuration's first data source. */
	private File dataFolder() {
		final com.etheller.warsmash.units.Element sources = this.ini.get("DataSources");
		if (sources == null) {
			return null;
		}
		final String path = sources.getField("Path00");
		return (path == null) || path.isEmpty() ? null : new File(path.replace("\"", ""));
	}

	private void gameDataRows(final List<Runnable> rows, final float[] rowY, final int[] index, final float x,
			final float w, final float controlX) {
		final File folder = dataFolder();
		final boolean casc = installedGame();
		final DataFolderCheck.Kind kind = casc ? DataFolderCheck.Kind.CASC : DataFolderCheck.Kind.FOLDER;
		rows.add(infoRow(rowY, index, x, w, controlX, s("options_source"),
				casc ? s("options_read_only").replace("%1$s", s("options_source_casc")) : s("options_source_folder")));
		rows.add(infoRow(rowY, index, x, w, controlX, s("options_folder"),
				folder == null ? "" : folder.getAbsolutePath()));
		rows.add(infoRow(rowY, index, x, w, controlX, s("options_version"), dataVersion()));
		final String locale = folder == null ? "" : DataFolderCheck.locale(kind, folder);
		rows.add(infoRow(rowY, index, x, w, controlX, s("options_locale"),
				locale.isEmpty() ? s("options_not_stated") : locale));
		if (!casc && (folder != null)) {
			rows.add(infoRow(rowY, index, x, w, controlX, s("options_files"), filesText(kind, folder)));
		}
		rows.add(infoRow(rowY, index, x, w, controlX, s("options_reforged_ui"),
				s(this.data.has("webui\\common\\homescreen.jpg") ? "options_found" : "options_missing")));
		final int recordings = recordings();
		rows.add(infoRow(rowY, index, x, w, controlX, s("options_backgrounds"),
				recordings == 0 ? s("options_live_scenes")
						: s("options_backgrounds_count").replace("%1$d", Integer.toString(recordings))
								.replace("%2$d", Integer.toString(SkinArt.SCENES.length))));
		final int buttons = index[0];
		rows.add(() -> {
			final LauncherBoard b = this.board;
			final float buttonW = b.tablet ? 480 : 300;
			final float y = rowY[0] + (b.tablet ? 20 : 8);
			if (this.gui.button("change", s("options_change"), x + b.labelX, y, buttonW, b.buttonH, b.buttonText,
					Gui.TONE_NORMAL)) {
				playClick();
				chooseFolder();
			}
			final PackageImport running = this.packageImport;
			if (running != null) {
				if (this.gui.button("cancel-import", s("data_folder_import_cancel"),
						x + b.labelX + buttonW + (b.tablet ? 32 : 16), y, buttonW, b.buttonH, b.buttonText,
						Gui.TONE_NORMAL)) {
					playClick();
					running.cancelled = true;
				}
			}
			else if (this.gui.button("find", s("options_find_installed"), x + b.labelX + buttonW + (b.tablet ? 32 : 16),
					y, buttonW, b.buttonH, b.buttonText, Gui.TONE_NORMAL)) {
				playClick();
				findInstalledGame();
			}
			final String status = running != null ? running.text() : this.dataStatus;
			if (!status.isEmpty()) {
				final float statusX = x + b.labelX + (2 * (buttonW + (b.tablet ? 32 : 16)));
				final float statusW = w - (2 * (buttonW + 32)) - b.labelX;
				final boolean cut = this.gui.text(status, statusX, y, statusW, b.buttonH, b.body,
						this.gui.palette.text2, Align.left);
				if (cut && this.gui.over(statusX, y, statusW, b.buttonH)) {
					this.gui.tip(status);
				}
			}
		});
		index[0] = buttons;
	}

	private void findInstalledGame() {
		final File found = GameDataFolders.findInstalled();
		if (found == null) {
			this.dataStatus = s("options_not_found");
			return;
		}
		useDataFolder(found);
	}

	private void chooseFolder() {
		if (this.packageImport != null) {
			return;
		}
		GameDataFolders.choose(dataFolder(), chosen -> Gdx.app.postRunnable(() -> {
			if (chosen != null) {
				useDataFolder(chosen);
			}
		}));
	}

	/** "21,183 files", and how many of them cannot be read once that is known; the count starts in the background. */
	private String filesText(final DataFolderCheck.Kind kind, final File folder) {
		final String files = DataFolderCheck.describe(kind, folder);
		final DataFolderCheck.Readability known = DataFolderCheck.lastReadability(folder);
		if (known == null) {
			if (!folder.equals(this.readabilityWalk)) {
				this.readabilityWalk = folder;
				final Thread walk = new Thread(() -> DataFolderCheck.readability(folder, Integer.MAX_VALUE, 0),
						"DataFolderReadability");
				walk.setDaemon(true);
				walk.start();
			}
			return files;
		}
		return known.unreadable == 0 ? files : this.strings.get("options_files_unreadable", files, known.unreadable);
	}

	/**
	 * Writes the engine configuration for a game data folder and reads the data again from it. A data package zip, or
	 * a folder with one and no data, is unpacked first.
	 */
	private void useDataFolder(final File chosen) {
		if (chosen.isFile() && chosen.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
			importPackage(chosen);
			return;
		}
		final File located = DataFolderCheck.locate(chosen);
		final File folder = located != null ? located : chosen;
		final DataFolderCheck.Kind kind = DataFolderCheck.detect(folder);
		if (kind == DataFolderCheck.Kind.NONE) {
			final List<File> packages = DataPackage.packagesIn(chosen);
			if (!packages.isEmpty()) {
				importPackage(packages.get(0));
				return;
			}
			final DataFolderCheck.Readability readability = DataFolderCheck.readability(chosen, 200_000, 3);
			this.dataStatus = readability.unreadable == 0 ? s("data_folder_missing")
					: this.strings.get(readability.partial ? "data_folder_unreadable_files_more"
							: "data_folder_unreadable_files", readability.unreadable) + ": "
							+ readability.firstNames();
			return;
		}
		try {
			// The engine's own folders of warsmash.ini are added to these when the file is read.
			final String section = DataFolderCheck.dataSourcesSection(kind, folder);
			Files.write(new File(USER_INI).toPath(), section.getBytes(StandardCharsets.UTF_8));
		}
		catch (final IOException e) {
			LauncherLog.w("Unable to write the engine configuration", e);
			this.dataStatus = s("data_folder_write_failed");
			return;
		}
		this.dataStatus = "";
		reopen(com.etheller.warsmash.desktop.DesktopLauncher.loadWarsmashIni(USER_INI), new File(USER_INI));
	}

	/** An import of a data package zip, unpacked next to it on its own thread while the launcher keeps drawing. */
	private final class PackageImport implements DataPackage.Progress {
		final String name;
		volatile boolean opened;
		volatile boolean cancelled;
		volatile long bytes;
		volatile long totalBytes;
		volatile int files;
		volatile int totalFiles;

		PackageImport(final String name) {
			this.name = name;
		}

		@Override
		public void update(final long bytes, final long totalBytes, final int files, final int totalFiles) {
			this.bytes = bytes;
			this.totalBytes = totalBytes;
			this.files = files;
			this.totalFiles = totalFiles;
		}

		String text() {
			if (this.cancelled) {
				return s("data_folder_import_cancelling");
			}
			if (!this.opened) {
				return s("data_folder_importing_open") + " · " + this.name;
			}
			if (this.files >= this.totalFiles) {
				return s("data_folder_importing_check");
			}
			final long percent = this.totalBytes > 0 ? (this.bytes * 100) / this.totalBytes : 0;
			return LauncherScreen.this.strings.get("data_folder_importing_detail", percent,
					LauncherStrings.size(this.bytes), LauncherStrings.size(this.totalBytes), this.files,
					this.totalFiles);
		}
	}

	/**
	 * Unpacks a data package into a folder named after the zip, beside it; a folder of that name that is not an earlier
	 * import of a package is left alone and another name is taken. A graphics set is added to the data folder in use.
	 */
	private void importPackage(final File zip) {
		if (this.packageImport != null) {
			return;
		}
		final String base = zip.getName().replaceAll("(?i)\\.zip$", "");
		File target = new File(zip.getAbsoluteFile().getParentFile(), base);
		for (int i = 2; target.exists() && !new File(target, DataPackage.MARKER).isFile(); i++) {
			target = new File(zip.getAbsoluteFile().getParentFile(), base + " (" + i + ")");
		}
		final File packageFolder = target;
		// An installed game is read in place and never written to, so a graphics set has nothing to join there.
		final File current = installedGame() ? null : dataFolder();
		final File[] into = { packageFolder };
		final PackageImport job = new PackageImport(zip.getName());
		this.packageImport = job;
		this.dataStatus = "";
		final Thread thread = new Thread(() -> {
			String problem = null;
			try (DataPackage dataPackage = DataPackage.open(zip)) {
				if ((dataPackage.graphicsSet != null) && (current != null)) {
					into[0] = current;
				}
				job.update(0, dataPackage.totalBytes, 0, dataPackage.fileCount());
				job.opened = true;
				dataPackage.importInto(into[0], job, () -> job.cancelled);
			}
			catch (final DataPackage.ImportException e) {
				LauncherLog.w("Game data import from " + zip + " stopped", e);
				problem = problemText(job.name, e);
			}
			catch (final IOException | RuntimeException e) {
				LauncherLog.w("Game data import from " + zip + " failed", e);
				problem = this.strings.get("data_folder_import_not_zip", job.name);
			}
			final String failed = problem;
			Gdx.app.postRunnable(() -> {
				this.packageImport = null;
				if (failed != null) {
					this.dataStatus = failed;
				}
				else {
					useDataFolder(into[0]);
				}
			});
		}, "DataImport");
		thread.setDaemon(true);
		thread.start();
	}

	private String problemText(final String name, final DataPackage.ImportException failure) {
		switch (failure.problem) {
		case NO_MARKER:
			return this.strings.get("data_folder_import_no_marker", name);
		case INCOMPLETE:
			return this.strings.get("data_folder_import_incomplete", name, failure.file);
		case UNSAFE_PATH:
			return this.strings.get("data_folder_import_unsafe", name, failure.file);
		case UNSUPPORTED:
			return this.strings.get("data_folder_import_unsupported", name, failure.file);
		case NO_SPACE: {
			final String[] sizes = failure.file.split(" / ");
			if (sizes.length == 2) {
				return this.strings.get("data_folder_import_no_space", LauncherStrings.size(Long.parseLong(sizes[1])),
						LauncherStrings.size(Long.parseLong(sizes[0])));
			}
			return this.strings.get("data_folder_import_io", name, failure.file);
		}
		case DAMAGED:
			return this.strings.get("data_folder_import_damaged", name, failure.file);
		case NO_GAME_DATA:
			return this.strings.get("data_folder_import_no_game_data", name);
		case NO_CLASSIC:
			return this.strings.get("data_folder_import_no_classic", name, failure.file);
		case CANCELLED:
			return s("data_folder_import_cancelled");
		case IO:
			return this.strings.get("data_folder_import_io", name, failure.file.isEmpty() ? name : failure.file);
		default:
			return this.strings.get("data_folder_import_not_zip", name);
		}
	}

	private void reopen(final DataTable ini, final File file) {
		stopRenderer();
		disposeCursor();
		stopMusic();
		if (this.art != null) {
			this.art.dispose();
			this.art = null;
		}
		if (this.catalog != null) {
			this.catalog.close();
			this.catalog = null;
		}
		this.ini = ini;
		this.iniFile = file;
		WarsmashConstants.loadConstants(ini.get("Emulator"), ini);
		this.data = null;
		this.hotkeys = null;
		this.readingHotkeys = false;
		openData();
	}

	// ---------------------------------------------------------------- Help

	private void helpPage() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float pf = b.pageFrame;
		final float top = tabsRow(new String[] { s("help_game_data"), s("help_problems") }, this.helpTab,
				tab -> this.helpTab = tab);
		final float bottom = t ? b.contentY + b.contentH : b.height - 16;
		g.panel(b.contentX, top, b.contentW, bottom - top, pf);
		float ty = top;
		final String[][] items = this.helpTab == 1
				? new String[][] { { "help_problem_data", "help_problem_data_line" },
					{ "help_problem_save", "help_problem_save_line" },
					{ "help_problem_closed", "help_problem_closed_line" } }
				: new String[][] { { "help_pc_installed", "help_pc_installed_line" },
					{ "help_pc_folder", "help_pc_folder_line" }, { "help_pc_choose", "help_pc_choose_line" } };
		if (t) {
			g.text(s(this.helpTab == 1 ? "help_problems_title" : "help_data_title"), b.contentX + pf + 23, top + pf,
					b.contentW, 96, b.title, g.palette.text, Align.left);
			ty = top + 96;
		}
		final float y0 = ty + (t ? 14 : pf + 8);
		final float step = (bottom - y0 - (t ? 60 : pf)) / items.length;
		final float disc = t ? 56 : 40;
		for (int i = 0; i < items.length; i++) {
			final float mid = y0 + (i * step) + (step / 2);
			final float dx = b.contentX + pf + 40;
			g.fill(dx, mid - (disc / 2), disc, disc, g.palette.gold);
			g.text(Integer.toString(i + 1), dx, mid - (disc / 2), disc, disc, disc * 0.55f, 0xFF000000, Align.center);
			final float tx = dx + disc + 40;
			g.text(s(items[i][0]), tx, mid - (t ? 50 : 34), (b.contentX + b.contentW) - tx - pf, t ? 50 : 34,
					t ? 36 : 24, g.palette.gold, Align.left);
			g.text(s(items[i][1]), tx, mid + 2, (b.contentX + b.contentW) - tx - pf, t ? 44 : 30, t ? 30 : 20,
					g.palette.text, Align.left);
		}
		if (t && g.button("back", glue("BACK", s("launcher_back")), b.backX, b.backY, b.backW, b.backH, 36,
				Gui.TONE_NORMAL)) {
			playClick();
			showPage(Page.HOME);
		}
	}

	// ---------------------------------------------------------------- Profiles

	private void profilePage() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float pf = b.pageFrame;
		final float k = t ? 1 : 0.62f;
		final List<String> names = this.profiles.names();
		if ((this.profileShown == null) || !names.contains(this.profileShown)) {
			this.profileShown = this.profiles.current();
		}
		final String shown = this.profileShown;
		final float listW = t ? 1060 : 540;
		final float rx = b.contentX + listW + (t ? 36 : 16);
		final float rw = (b.contentX + b.contentW) - rx;
		g.panel(b.contentX, b.contentY, listW, b.contentH, pf);
		final float rowH = 150 * k;
		final float icon = rowH - (28 * k);
		final float lx = b.contentX + pf + 8;
		final float lw = listW - (2 * pf) - 16;
		final float fieldH = 112 * k;
		final float fy = (b.contentY + b.contentH) - pf - (16 * k) - fieldH;
		final float top = b.contentY + pf + (12 * k);
		final float offset = g.beginList("profiles", lx, top, lw, fy - 16 - top, names.size() * rowH);
		float y = top - offset;
		for (final String name : names) {
			final int state = g.hit("profile" + name, lx, y, lw, rowH);
			if (name.equals(shown) || ((state & Gui.HOVER) != 0)) {
				g.image(this.art.image(SkinArt.ROW_HIGHLIGHT), lx, y, lw, rowH, name.equals(shown) ? 1 : 0.5f);
			}
			g.fill(lx + 6, (y + (14 * k)) - 2, icon + 4, icon + 4, 0xFF6D6658);
			g.cover(this.art.portrait(portrait(name)), lx + 8, y + (14 * k), icon, icon, 0.5f);
			g.text(name, lx + icon + (36 * k), y + (22 * k), lw - icon - 50, 50 * k, Math.max(40 * k, 24),
					g.palette.text, Align.left);
			g.text(s("profile_saved_games").replace("%1$d", Integer.toString(this.profiles.savedGames(name))),
					lx + icon + (36 * k), y + (78 * k), lw - icon - 50, 40 * k, Math.max(28 * k, 18), g.palette.text2,
					Align.left);
			if ((state & (Gui.CLICK | Gui.RIGHT)) != 0) {
				playClick();
				this.profileShown = name;
				if ((state & Gui.DOUBLE) != 0) {
					selectProfile(name);
				}
				else if ((state & Gui.RIGHT) != 0) {
					g.contextMenu("profileMenu",
							new String[] { glue("SELECT", "Select"), s("profile_change_portrait"), s("profile_rename"),
								glue("DELETE", "Delete") },
							new boolean[] { true, true, true, names.size() > 1 }, choice -> {
								switch (choice) {
								case 0:
									selectProfile(name);
									break;
								case 1:
									this.dialog = Dialog.PORTRAIT;
									break;
								case 2:
									this.renameField.setLength(0);
									this.renameField.append(name);
									this.gui.focus = "rename";
									this.dialog = Dialog.RENAME;
									break;
								default:
									this.dialog = Dialog.DELETE_PROFILE;
									break;
								}
							});
				}
			}
			y += rowH;
		}
		g.endList();
		final float addW = 240 * k;
		final float fieldW = lw - addW - 16;
		g.field("newName", this.nameField, lx, fy, fieldW, fieldH, Math.max(36 * k, 22), Profiles.MAX_NAME,
				s("profile_name"));
		final Profiles.NameProblem problem = this.profiles.check(this.nameField.toString(), null);
		if (g.button("add", glue("ADD", "Add"), lx + fieldW + 16, fy, addW, fieldH, Math.max(34 * k, 22),
				problem == Profiles.NameProblem.NONE ? Gui.TONE_NORMAL : Gui.TONE_OFF)
				|| ((problem == Profiles.NameProblem.NONE) && "newName".equals(g.focus)
						&& this.gui.keys.contains(Input.Keys.ENTER))) {
			playClick();
			this.profiles.create(this.nameField.toString().trim(), 0, false);
			this.profileShown = this.nameField.toString().trim();
			this.nameField.setLength(0);
		}
		g.panel(rx, b.contentY, rw, b.contentH, pf);
		final float left = rx + pf + (22 * k);
		final float t0 = b.contentY + pf + (16 * k);
		final float ps = 240 * k;
		g.fill(left - 5, t0 - 5, ps + 10, ps + 10, 0xFF6D6658);
		g.fill(left - 3, t0 - 3, ps + 6, ps + 6, 0xFF1A1A1A);
		g.cover(this.art.portrait(portrait(shown)), left, t0, ps, ps, 0.5f);
		g.text(shown, left + ps + (40 * k), t0 + (14 * k), rw - ps - (120 * k), 76 * k, Math.max(60 * k, 32),
				g.palette.text, Align.left);
		final int savedGames = this.profiles.savedGames(shown);
		g.text(savedGames == 0 ? s("game_no_saves") : s("profile_saved_games").replace("%1$d", Integer.toString(savedGames)),
				left + ps + (40 * k), t0 + (84 * k), 600, 40 * k, Math.max(28 * k, 18), g.palette.text2, Align.left);
		if (g.button("portrait", s("profile_change_portrait"), left + ps + (40 * k), t0 + (128 * k), 340 * k, 112 * k,
				Math.max(32 * k, 20), Gui.TONE_NORMAL)) {
			playClick();
			this.dialog = Dialog.PORTRAIT;
		}
		if (g.button("rename", s("profile_rename"), left + ps + (40 * k) + (370 * k), t0 + (128 * k), 260 * k, 112 * k,
				Math.max(32 * k, 20), Gui.TONE_NORMAL)) {
			playClick();
			this.renameField.setLength(0);
			this.renameField.append(shown);
			g.focus = "rename";
			this.dialog = Dialog.RENAME;
		}
		profileFacts(shown, left, t0 + ps + (50 * k), rw - (2 * pf) - (44 * k), k);
		final float size = t ? 36 : 30;
		if (g.button("back", glue("BACK", s("launcher_back")), b.backX, b.backY, b.backW, b.backH, size,
				Gui.TONE_NORMAL)) {
			playClick();
			showPage(Page.HOME);
		}
		final float[] delete = b.actionSlot(1);
		if (g.button("deleteProfile", glue("DELETE", "Delete"), delete[0], b.backY, delete[1], b.backH, size,
				names.size() < 2 ? Gui.TONE_OFF : this.art.reforged ? Gui.TONE_DANGER : Gui.TONE_NORMAL)) {
			this.dialog = Dialog.DELETE_PROFILE;
		}
		final float[] select = b.actionSlot(2);
		if (g.button("select", glue("SELECT", "Select"), select[0], b.backY, select[1], b.backH, size,
				this.art.reforged ? Gui.TONE_CONFIRM : Gui.TONE_DOWN)) {
			selectProfile(shown);
		}
	}

	private void selectProfile(final String name) {
		playClick();
		this.profiles.select(name);
		this.profileProgress = null;
		readCatalog();
		showPage(Page.HOME);
	}

	/** The profile's campaigns, as far as each got, and its statistics of custom games. */
	private void profileFacts(final String name, final float x, final float top, final float width, final float k) {
		final Gui g = this.gui;
		if ((this.profileProgress == null) && (this.campaigns != null) && !this.readingProgress) {
			this.readingProgress = true;
			final List<String> names = this.profiles.names();
			final List<File> folders = new ArrayList<>();
			for (final String profile : names) {
				folders.add(this.profiles.folder(profile));
			}
			this.catalog.progress(folders, found -> {
				this.readingProgress = false;
				final Map<String, CCampaignProgress.Snapshot> progress = new HashMap<>();
				if (found != null) {
					for (int i = 0; i < names.size(); i++) {
						progress.put(names.get(i), found.get(i));
					}
				}
				this.profileProgress = progress;
			});
		}
		final float columnW = width * 0.52f;
		final float headSize = Math.max(30 * k, 19);
		final float rowSize = Math.max(28 * k, 17);
		final float step = 48 * k;
		float y = top;
		final CCampaignProgress.Snapshot progress = this.profileProgress == null ? null : this.profileProgress.get(name);
		if ((this.campaigns != null) && (progress != null)) {
			final CampaignCatalog catalog = this.campaigns.catalog;
			boolean head = true;
			for (final Object data : catalog.getDatas()) {
				if (data == null) {
					head = true;
					continue;
				}
				if (head) {
					head = false;
					if (y > top) {
						y += 20 * k;
					}
					g.text(s(y == top ? "profile_reign" : "profile_frozen"), x, y, columnW, 48 * k, headSize,
							g.palette.gold, Align.left);
					g.fill(x, y + (46 * k), columnW - 20, 2, g.palette.line);
					y += 60 * k;
				}
				final CampaignMenuData menu = (CampaignMenuData) data;
				CampaignCatalog.Entry entry = null;
				for (final CampaignCatalog.Entry candidate : catalog.getEntries()) {
					if (candidate.getData() == menu) {
						entry = candidate;
					}
				}
				if (entry == null) {
					continue;
				}
				final int furthest = CampaignChoice.furthestMission(entry, progress);
				final boolean started = CampaignChoice.started(entry, progress);
				final String value = !started ? s("profile_not_started")
						: CampaignCatalog.isCompleted(entry, progress) ? s("profile_completed")
								: furthest < 0 ? ""
										: GlueText.plain(glue(menu.getMissions().get(furthest).getHeader(),
												menu.getMissions().get(furthest).getHeader()));
				g.text(CampaignChoice.shortCampaign(GlueText.plain(glue(menu.getHeader(), menu.getHeader()))),
						x + (14 * k), y, 250 * k, step, rowSize, g.palette.text, Align.left);
				g.text(value, x + (250 * k), y, columnW - (250 * k), step, rowSize, g.palette.text, Align.left);
				y += step;
			}
		}
		final float sx = x + columnW;
		final float sw = width - columnW;
		g.text(s("profile_statistics"), sx, top, sw, 48 * k, headSize, g.palette.gold, Align.left);
		g.fill(sx, top + (46 * k), sw - 20, 2, g.palette.line);
		final int[][] counts = this.profiles.statistics(name, CustomGame.RACES.length);
		int games = 0;
		int wins = 0;
		int favourite = -1;
		int mostWins = -1;
		for (int i = 0; i < counts.length; i++) {
			games += counts[i][0];
			wins += counts[i][1];
			if ((counts[i][0] > 0) && ((favourite < 0) || (counts[i][0] > counts[favourite][0]))) {
				favourite = i;
			}
			if ((counts[i][1] > 0) && ((mostWins < 0) || (counts[i][1] > counts[mostWins][1]))) {
				mostWins = i;
			}
		}
		final String[][] facts = { { s("profile_games_played"), Integer.toString(games) },
			{ s("profile_wins"), wins + " (" + (games == 0 ? 0 : Math.round((wins * 100f) / games)) + " %)" },
			{ s("profile_favourite"), favourite < 0 ? "\u2014" : glue(CustomGame.RACES[favourite][0],
					CustomGame.RACES[favourite][1]) },
			{ s("profile_most_wins"), mostWins < 0 ? "\u2014" : glue(CustomGame.RACES[mostWins][0],
					CustomGame.RACES[mostWins][1]) } };
		for (int i = 0; i < facts.length; i++) {
			final float fy = top + (60 * k) + (i * step);
			g.text(facts[i][0], sx + (14 * k), fy, 230 * k, step, rowSize, g.palette.text2, Align.left);
			g.text(facts[i][1], sx + (237 * k), fy, sw - (237 * k), step, rowSize, g.palette.text, Align.left);
		}
	}

	private boolean readingProgress;

	// ---------------------------------------------------------------- dialogs

	private void drawDialog() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		g.fill(0, 0, b.width, b.height, 0xAB000000);
		switch (this.dialog) {
		case EXIT:
			question(s("launcher_exit"), "", new String[] { s("game_no"), s("game_yes") }, choice -> {
				if (choice == 1) {
					Gdx.app.exit();
				}
			}, 0);
			break;
		case MESSAGE:
			question(this.messageTitle, this.messageLine, new String[] { s("game_ok") }, choice -> {
			}, 0);
			break;
		case DELETE_SAVE: {
			final GameCatalog.SaveEntry entry = this.saveList == null ? null : chosenSave();
			question(s("game_delete_save").replace("%1$s", entry == null ? "" : entry.name), "",
					new String[] { s("game_no"), s("game_yes") }, choice -> {
						if ((choice == 1) && (entry != null)) {
							if (!entry.file.delete()) {
								LauncherLog.w("Unable to delete " + entry.file);
							}
							this.catalog.saves(found -> this.saveList = found);
						}
					}, 0);
			break;
		}
		case DELETE_PROFILE: {
			final String name = this.profileShown;
			question(String.format(Locale.ROOT, glue("CONFIRM_DELETE_PROFILE", s("profile_delete_fallback")), name),
					s("profile_delete_line"), new String[] { s("game_no"), s("game_yes") }, choice -> {
						if (choice == 1) {
							this.profiles.delete(name);
							this.profileShown = null;
							this.profileProgress = null;
							readCatalog();
						}
					}, 0);
			break;
		}
		case RENAME:
			renameDialog();
			break;
		case DELETE_REPLAY: {
			final GameCatalog.ReplayEntry entry = this.replayList == null ? null : chosenReplay();
			question(s("replay_delete").replace("%1$s", entry == null ? "" : entry.name), "",
					new String[] { s("game_no"), s("game_yes") }, choice -> {
						if ((choice == 1) && (entry != null)) {
							if (!entry.file.delete()) {
								LauncherLog.w("Unable to delete " + entry.file);
							}
							this.catalog.replays(found -> this.replayList = found);
						}
					}, 0);
			break;
		}
		case RENAME_REPLAY:
			renameReplayDialog();
			break;
		case TRY_REPLAY: {
			final GameCatalog.ReplayEntry entry = this.triedReplay;
			// The original asks before it plays a replay of another version, ignoring the recorded checksums.
			question(glue("REPLAY_CONFIRM", "Incompatible Replay!"),
					glue("REPLAY_CONFIRM_DESC", s("replay_other_version")),
					new String[] { s("game_no"), s("game_yes") }, choice -> {
						if ((choice == 1) && (entry != null)) {
							launchGame(LaunchRequest.replay(entry, true), Page.REPLAY);
						}
					}, 0);
			break;
		}
		case OVERWRITE_REPLAY: {
			final GameCatalog.ReplayEntry entry = this.renamingReplay;
			final String name = this.renameField.toString().trim();
			question(glue("OVERWRITE_REPLAY_MESSAGE", s("replay_overwrite")), "",
					new String[] { s("game_no"), s("game_yes") }, choice -> {
						if ((choice == 1) && (entry != null)) {
							renameReplay(entry, name);
						}
					}, 0);
			break;
		}
		case PORTRAIT:
			portraitDialog();
			break;
		case UPDATE: {
			final UpdateCheck.Release latest = this.updateRelease;
			question(s("update_title"),
					s("update_offer_desktop").replace("%1$s", latest.tag).replace("%2$s", GameReplays.engineVersion()),
					new String[] { s("update_open_page"), s("update_later"), s("update_skip") }, choice -> {
						if (choice == 0) {
							Gdx.net.openURI(latest.pageUrl);
						}
						else if (choice == 2) {
							this.preferences.edit().putString(LauncherKeys.KEY_UPDATE_SKIPPED, latest.tag).apply();
						}
					}, 1);
			break;
		}
		case ADVANCED: {
			final float w = 1120;
			final float h = 600;
			final float x = (b.width - w) / 2;
			final float y = (b.height - h) / 2;
			g.dialog(x, y, w, h);
			advancedOptions(x + 46, y + 36, w - 92, 96, 40, 24, 30);
			if (g.button("advancedOk", s("game_ok"), (x + w) - 34 - 320, (y + h) - 34 - 96, 320, 96, 30,
					Gui.TONE_NORMAL) || g.keys.contains(Input.Keys.ENTER)) {
				this.dialog = Dialog.NONE;
			}
			break;
		}
		default:
			break;
		}
	}

	/** A question box: the title, a line and its buttons; Enter takes the first button, the safe one. */
	private void question(final String title, final String line, final String[] buttons,
			final java.util.function.IntConsumer chosen, final int enterChoice) {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float w = t ? 1400 : 1000;
		final float h = t ? 620 : 440;
		final float x = (b.width - w) / 2;
		final float y = (b.height - h) / 2;
		g.dialog(x, y, w, h);
		final float edge = t ? 48 : 32;
		final float icon = t ? 140 : 96;
		g.image(SkinArt.QUESTION, x + edge + 20, y + edge + 20, icon, icon);
		g.lines(title, x + edge + icon + 60, y + edge + 30, w - (2 * edge) - icon - 80, t ? 220 : 160, t ? 44 : 32,
				g.palette.gold, Align.left);
		g.lines(line, x + edge + icon + 60, y + edge + (t ? 250 : 180), w - (2 * edge) - icon - 80, t ? 140 : 100,
				t ? 32 : 24, g.palette.text, Align.left);
		final float buttonW = t ? 360 : 260;
		final float buttonH = t ? 112 : 96;
		final float gap = t ? 32 : 20;
		float bx = (x + w) - edge - 20 - (buttons.length * buttonW) - ((buttons.length - 1) * gap);
		int choice = -1;
		for (int i = 0; i < buttons.length; i++) {
			if (g.button("question" + i, buttons[i], bx, (y + h) - edge - 20 - buttonH, buttonW, buttonH, t ? 40 : 30,
					Gui.TONE_NORMAL)) {
				choice = i;
			}
			bx += buttonW + gap;
		}
		if (g.keys.contains(Input.Keys.ENTER)) {
			choice = enterChoice;
		}
		if (choice >= 0) {
			playClick();
			this.dialog = Dialog.NONE;
			chosen.accept(choice);
		}
	}

	private void renameDialog() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float w = t ? 1400 : 1000;
		final float h = t ? 560 : 400;
		final float x = (b.width - w) / 2;
		final float y = (b.height - h) / 2;
		final float edge = t ? 48 : 32;
		g.dialog(x, y, w, h);
		g.text(s("profile_rename_title"), x + edge + 20, y + edge + 10, w, t ? 70 : 50, t ? 44 : 32, g.palette.gold,
				Align.left);
		g.focus = "rename";
		g.field("rename", this.renameField, x + edge + 20, y + edge + (t ? 100 : 70), w - (2 * edge) - 40,
				t ? 112 : 80, t ? 40 : 28, Profiles.MAX_NAME, s("profile_name"));
		final Profiles.NameProblem problem = this.profiles.check(this.renameField.toString(), this.profileShown);
		final String problemText = problem == Profiles.NameProblem.EMPTY ? s("profile_needs_name")
				: problem == Profiles.NameProblem.TAKEN ? s("profile_name_taken")
						: problem == Profiles.NameProblem.CHARACTERS ? s("profile_name_characters") : "";
		g.text(problemText, x + edge + 20, y + edge + (t ? 220 : 160), w - (2 * edge) - 40, t ? 50 : 36,
				t ? 30 : 22, SkinArt.Palette.DANGER_TEXT, Align.left);
		final float buttonW = t ? 360 : 260;
		final float buttonH = t ? 112 : 96;
		final float by = (y + h) - edge - 20 - buttonH;
		boolean close = false;
		if (g.button("renameCancel", glue("CANCEL", "Cancel"), (x + w) - edge - 20 - (2 * buttonW) - 32, by, buttonW,
				buttonH, t ? 40 : 30, Gui.TONE_NORMAL)) {
			close = true;
		}
		if ((g.button("renameOk", s("game_ok"), (x + w) - edge - 20 - buttonW, by, buttonW, buttonH, t ? 40 : 30,
				problem == Profiles.NameProblem.NONE ? Gui.TONE_NORMAL : Gui.TONE_OFF)
				|| g.keys.contains(Input.Keys.ENTER)) && (problem == Profiles.NameProblem.NONE)) {
			final String typed = this.renameField.toString().trim();
			if (this.profiles.rename(this.profileShown, typed)) {
				this.profileShown = typed;
			}
			close = true;
		}
		if (close) {
			playClick();
			g.focus = null;
			this.dialog = Dialog.NONE;
		}
	}

	private void portraitDialog() {
		final Gui g = this.gui;
		final LauncherBoard b = this.board;
		final boolean t = b.tablet;
		final float w = Math.min(b.width - 80, t ? 2200 : 1400);
		final float h = Math.min(b.height - 80, t ? 1500 : 680);
		final float x = (b.width - w) / 2;
		final float y = (b.height - h) / 2;
		final float edge = t ? 48 : 32;
		g.dialog(x, y, w, h);
		g.text(s("profile_choose_portrait"), x + edge + 20, y + edge, w, t ? 80 : 56, t ? 44 : 32, g.palette.gold,
				Align.left);
		final float cell = t ? 150 : 100;
		final float gap = t ? 16 : 10;
		final float gx = x + edge + 20;
		final float gy = y + edge + (t ? 100 : 70);
		final float gw = w - (2 * edge) - 40;
		final float gh = h - (2 * edge) - (t ? 100 : 70) - (t ? 140 : 110);
		final int columns = Math.max(1, (int) ((gw + gap) / (cell + gap)));
		final List<Integer> all = new ArrayList<>();
		for (final SkinArt.PortraitGroup group : SkinArt.portraitGroups(this.art.reforged)) {
			for (final int portrait : group.portraits) {
				if ((portrait >= 0) && !all.contains(portrait)) {
					all.add(portrait);
				}
			}
		}
		final int rows = (all.size() + columns - 1) / columns;
		final float offset = g.beginList("portraits", gx, gy, gw, gh, rows * (cell + gap));
		final int current = this.profiles.portrait(this.profileShown);
		for (int i = 0; i < all.size(); i++) {
			final int portrait = all.get(i);
			final float px = gx + ((i % columns) * (cell + gap));
			final float py = (gy + ((i / columns) * (cell + gap))) - offset;
			if (((py + cell) < gy) || (py > (gy + gh))) {
				continue;
			}
			final TextureRegion image = this.art.portrait(portrait);
			if (image == null) {
				continue;
			}
			final int state = g.hit("portrait" + portrait, px, py, cell, cell);
			g.cover(image, px, py, cell, cell, 0.5f);
			if ((portrait == current) || ((state & Gui.HOVER) != 0)) {
				g.image(SkinArt.PORTRAIT_ACTIVE, px, py, cell, cell);
			}
			if ((state & Gui.CLICK) != 0) {
				playClick();
				this.profiles.setPortrait(this.profileShown, portrait);
			}
		}
		g.endList();
		final float buttonW = t ? 360 : 260;
		final float buttonH = t ? 112 : 96;
		if (g.button("portraitOk", s("game_ok"), (x + w) - edge - 20 - buttonW, (y + h) - edge - 20 - buttonH,
				buttonW, buttonH, t ? 40 : 30, Gui.TONE_NORMAL) || g.keys.contains(Input.Keys.ENTER)) {
			playClick();
			this.dialog = Dialog.NONE;
		}
	}

	// ---------------------------------------------------------------- navigation and games

	private void showPage(final Page next) {
		if (next != Page.MISSIONS) {
			this.campaignResult = null;
		}
		this.gui.closeMenu();
		this.gui.focus = null;
		this.capturing = null;
		this.page = next;
	}

	/** Esc: closes a menu or dialog, else goes back a page; Home asks to exit. */
	private void back() {
		if (this.gui.menuOpen()) {
			this.gui.closeMenu();
			return;
		}
		if (this.dialog != Dialog.NONE) {
			this.dialog = Dialog.NONE;
			this.gui.focus = null;
			return;
		}
		switch (this.page) {
		case HOME:
			this.dialog = Dialog.EXIT;
			break;
		case MISSIONS:
			if (this.campaignResult != null) {
				this.campaignResult = null;
				break;
			}
			showPage(Page.CAMPAIGN);
			break;
		default:
			showPage(Page.HOME);
			break;
		}
	}

	/** Enter: the page's main action. */
	private void mainAction() {
		switch (this.page) {
		case HOME:
			continueCampaign();
			break;
		case CUSTOM:
			startCustom();
			break;
		case LOAD: {
			final GameCatalog.SaveEntry entry = this.saveList == null ? null : chosenSave();
			if ((entry != null) && (entry.loadability == SavedGame.Loadability.LOADABLE)) {
				launchGame(loadRequest(entry), Page.LOAD);
			}
			break;
		}
		case REPLAY:
			if (this.replayList != null) {
				startReplay(chosenReplay());
			}
			break;
		case PROFILE:
			if (this.gui.focus == null) {
				selectProfile(this.profileShown);
			}
			break;
		default:
			break;
		}
	}

	private LaunchRequest loadRequest(final GameCatalog.SaveEntry entry) {
		return LaunchRequest.load(entry, this.campaigns == null ? null : this.campaigns.catalog);
	}

	/** Starts a game in this window; the launcher comes back when it ends, on the page given. */
	private void launchGame(final LaunchRequest request, final Page returnPage) {
		if (this.launching) {
			return;
		}
		this.launching = true;
		playClick();
		request.profile = this.profiles.current();
		request.skin = this.art.reforged ? SkinArt.REFORGED_SKIN : SkinArt.CLASSIC_SKIN;
		if (this.art.reforged) {
			request.portrait = this.profiles.portrait(request.profile) + 1;
		}
		final LauncherStore.Edit edit = this.preferences.edit()
				.putString(LauncherKeys.KEY_RETURN_PAGE, returnPage.name())
				.putLong(LauncherKeys.KEY_LAUNCHED, System.currentTimeMillis());
		if ((request.saveFile != null) && (request.campaign >= 0)) {
			// A campaign mission's save ends on its campaign's result, over that campaign as a mission started from
			// its list does.
			edit.putInt(LauncherKeys.KEY_CAMPAIGN, request.campaign);
			if (request.mission >= 0) {
				edit.putInt(LauncherKeys.KEY_MISSION, request.mission);
			}
			else {
				edit.remove(LauncherKeys.KEY_MISSION);
			}
		}
		edit.commit();
		stopMusic();
		stopRenderer();
		disposeCursor();
		this.launched = request;
		GraphicsSettings.apply(this.ini, graphicsSet(), textureSize());
		this.menuScreen = new WarsmashGdxMenuScreen(this.ini, this.game);
		// The game reads the data the launcher has open rather than opening it again beside it.
		this.menuScreen.setGameData(this.data);
		request.prepare(this.menuScreen);
		this.menuScreen.setGameEndListener(result -> Gdx.app.postRunnable(() -> gameEnded(result)));
		this.menuScreen.whenLoaded(menu -> {
			if (!request.start(menu)) {
				LauncherLog.w("The launcher asked for a game the engine cannot start: " + request);
				final GameResult failed = new GameResult();
				failed.result = "error";
				failed.map = request.mapFile;
				Gdx.app.postRunnable(() -> gameEnded(failed));
			}
		});
		this.game.setScreen(this.menuScreen);
	}

	private void gameEnded(final GameResult returned) {
		final String result = returned.result;
		final int race = returned.race;
		final WarsmashGdxMenuScreen menu = this.menuScreen;
		this.menuScreen = null;
		this.launching = false;
		this.game.setScreen(this);
		if (menu != null) {
			try {
				menu.dispose();
			}
			catch (final RuntimeException e) {
				LauncherLog.w("Unable to let go of the engine's menu", e);
			}
		}
		returned.source = this.launched == null ? LaunchRequest.SOURCE_LOAD : this.launched.source(returned);
		returned.profile = this.launched == null ? "" : this.launched.profile;
		returned.finished = System.currentTimeMillis();
		this.launched = null;
		Page next;
		try {
			next = Page.valueOf(this.preferences.getString(LauncherKeys.KEY_RETURN_PAGE, Page.HOME.name()));
		}
		catch (final IllegalArgumentException e) {
			next = Page.HOME;
		}
		final LauncherStore.Edit edit = this.preferences.edit().remove(LauncherKeys.KEY_LAUNCHED);
		if (CampaignResult.shows(returned) && (returned.campaign >= 0)
				&& (this.preferences.getInt(LauncherKeys.KEY_CAMPAIGN, -1) != returned.campaign)) {
			// The result is of the campaign the game played, which a save loaded in the game may have changed.
			edit.putInt(LauncherKeys.KEY_CAMPAIGN, returned.campaign).remove(LauncherKeys.KEY_MISSION);
		}
		if (LaunchRequest.SOURCE_CUSTOM.equals(returned.source) && !returned.failed() && !"exit".equals(result)
				&& !returned.profile.isEmpty()) {
			final int played = (race > 0) && (race < CustomGame.RACES.length) ? race
					: this.preferences.getInt(LauncherKeys.KEY_LAUNCH_RACE, 0);
			this.profiles.record(returned.profile, result, played);
		}
		if ("victory".equals(result) && LaunchRequest.SOURCE_CAMPAIGN.equals(returned.source)) {
			edit.remove(LauncherKeys.KEY_MISSION);
		}
		edit.apply();
		this.profileProgress = null;
		readCatalog();
		startRenderer();
		updateMusic();
		final boolean showResult = CampaignResult.shows(returned);
		if (showResult) {
			// As the Reforged campaign screen does, the mission's result comes first, over its campaign; both skins
			// share the flow and differ only in its look.
			next = Page.MISSIONS;
		}
		showPage(next);
		this.campaignResult = showResult ? returned : null;
		if (returned.loadFailure != null) {
			this.messageTitle = s("game_cannot_load");
			try {
				this.messageLine = loadabilityText(SavedGame.Loadability.valueOf(returned.loadFailure));
			}
			catch (final IllegalArgumentException e) {
				this.messageLine = returned.loadFailure;
			}
			this.dialog = Dialog.MESSAGE;
		}
		else if (returned.failed()) {
			this.messageTitle = s("game_not_started");
			this.messageLine = returned.map == null ? "" : returned.map;
			this.dialog = Dialog.MESSAGE;
		}
	}

	// ---------------------------------------------------------------- sound

	private void playClick() {
		if (this.click != null) {
			this.click.play(0.6f);
		}
	}

	private void updateMusic() {
		stopMusic();
		if ((this.data == null) || this.launching || !this.preferences.getBoolean(LauncherKeys.KEY_MUSIC, true)) {
			return;
		}
		final String path = roc() ? "Sound\\Music\\mp3Music\\MainScreen.mp3"
				: "Sound\\Music\\mp3Music\\war3XMainScreen.mp3";
		try {
			if (this.data.has(path)) {
				this.music = Gdx.audio.newMusic(new DataSourceFileHandle(this.data, path));
				this.music.setLooping(true);
				this.music.setVolume(0.4f);
				this.music.play();
			}
		}
		catch (final RuntimeException e) {
			LauncherLog.w("Unable to play the menu music", e);
			this.music = null;
		}
	}

	private void stopMusic() {
		if (this.music != null) {
			this.music.stop();
			this.music.dispose();
			this.music = null;
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean keyDown(final int keycode) {
		final boolean alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
		if ((keycode == Input.Keys.F11) || ((keycode == Input.Keys.ENTER) && alt)) {
			setFullscreen(!Gdx.graphics.isFullscreen());
			return true;
		}
		if (this.art == null) {
			return true;
		}
		if (this.capturing != null) {
			captureKey(keycode);
			return true;
		}
		if (keycode == Input.Keys.ESCAPE) {
			back();
			return true;
		}
		final boolean control = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
				|| Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
		final boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
				|| Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
		if (this.gui.focus != null) {
			if (fieldKey(keycode, control)) {
				return true;
			}
			if (keycode == Input.Keys.TAB) {
				this.gui.focus = null;
				this.gui.navigate(keycode, shift);
				return true;
			}
			this.gui.keys.add(keycode);
			return true;
		}
		if ((keycode == Input.Keys.TAB) || (keycode == Input.Keys.UP) || (keycode == Input.Keys.DOWN)
				|| (keycode == Input.Keys.LEFT) || (keycode == Input.Keys.RIGHT)) {
			this.gui.navigate(keycode, shift);
			return true;
		}
		if (((keycode == Input.Keys.ENTER) || (keycode == Input.Keys.SPACE)) && this.gui.keyFocusShown()) {
			this.gui.keyPress();
			return true;
		}
		this.gui.keys.add(keycode);
		if ((this.dialog != Dialog.NONE) || this.gui.menuOpen()) {
			return true;
		}
		if (keycode == Input.Keys.ENTER) {
			mainAction();
			return true;
		}
		for (int i = 0; i < RAIL_KEYS.length; i++) {
			if ((keycode == RAIL_KEYS[i]) && RAIL_OPEN[i]) {
				playClick();
				railChosen(i);
				return true;
			}
		}
		if (keycode == Input.Keys.Q) {
			this.dialog = Dialog.EXIT;
		}
		return true;
	}

	/** A key of the text field that has the keys: editing, the caret and the clipboard. */
	private boolean fieldKey(final int keycode, final boolean control) {
		final char edit;
		switch (keycode) {
		case Input.Keys.BACKSPACE:
			edit = Gui.EDIT_BACKSPACE;
			break;
		case Input.Keys.FORWARD_DEL:
			edit = Gui.EDIT_DELETE;
			break;
		case Input.Keys.LEFT:
			edit = Gui.EDIT_LEFT;
			break;
		case Input.Keys.RIGHT:
			edit = Gui.EDIT_RIGHT;
			break;
		case Input.Keys.HOME:
			edit = Gui.EDIT_HOME;
			break;
		case Input.Keys.END:
			edit = Gui.EDIT_END;
			break;
		case Input.Keys.A:
			edit = control ? Gui.EDIT_SELECT_ALL : 0;
			break;
		case Input.Keys.C:
			edit = control ? Gui.EDIT_COPY : 0;
			break;
		case Input.Keys.X:
			edit = control ? Gui.EDIT_CUT : 0;
			break;
		case Input.Keys.V:
			if (control) {
				final String pasted = Gdx.app.getClipboard().getContents();
				if (pasted != null) {
					this.gui.typed.append(pasted.replace('\n', ' ').replace('\r', ' '));
				}
				return true;
			}
			edit = 0;
			break;
		default:
			edit = 0;
			break;
		}
		if (edit == 0) {
			return false;
		}
		this.gui.typed.append(edit);
		return true;
	}

	@Override
	public boolean keyUp(final int keycode) {
		return true;
	}

	@Override
	public boolean keyTyped(final char character) {
		if ((this.gui.focus != null) && (character >= ' ') && (character != 127)) {
			this.gui.typed.append(character);
		}
		return true;
	}

	@Override
	public boolean touchDown(final int screenX, final int screenY, final int pointer, final int button) {
		if (this.board != null) {
			if ((this.gui.focus != null) && (button == Input.Buttons.LEFT)) {
				this.gui.focus = null;
			}
			this.gui.mousePressed(screenX, screenY, button);
		}
		return true;
	}

	@Override
	public boolean touchUp(final int screenX, final int screenY, final int pointer, final int button) {
		if (this.board != null) {
			this.gui.mouseReleased(screenX, screenY, button);
		}
		return true;
	}

	@Override
	public boolean touchDragged(final int screenX, final int screenY, final int pointer) {
		if (this.board != null) {
			this.gui.mouseMoved(screenX, screenY);
		}
		return true;
	}

	@Override
	public boolean mouseMoved(final int screenX, final int screenY) {
		if (this.board != null) {
			this.gui.mouseMoved(screenX, screenY);
			this.gui.hideKeyFocus();
		}
		return true;
	}

	@Override
	public boolean scrolled(final float amountX, final float amountY) {
		this.gui.wheel += Math.round(Math.signum(amountY));
		return true;
	}

	@Override
	public boolean touchCancelled(final int screenX, final int screenY, final int pointer, final int button) {
		return true;
	}

	/** No game data: the waiting page draws with the default font and no art. */
	private static final class EmptySource implements DataSource {
		@Override
		public InputStream getResourceAsStream(final String filepath) {
			return null;
		}

		@Override
		public File getFile(final String filepath) {
			return null;
		}

		@Override
		public File getDirectory(final String filepath) {
			return null;
		}

		@Override
		public java.nio.ByteBuffer read(final String path) {
			return null;
		}

		@Override
		public boolean has(final String filepath) {
			return false;
		}

		@Override
		public java.util.Collection<String> getListfile() {
			return null;
		}

		@Override
		public void close() {
		}
	}
}
