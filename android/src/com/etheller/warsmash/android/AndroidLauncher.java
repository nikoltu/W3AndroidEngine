package com.etheller.warsmash.android;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.DisabledAndroidAudio;
import com.badlogic.gdx.backends.android.keyboardheight.KeyboardHeightObserver;
import com.badlogic.gdx.backends.android.keyboardheight.KeyboardHeightProvider;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimThreadMode;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnitTypeData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.WarsmashGdxMapScreen;
import com.etheller.warsmash.WarsmashGdxMenuScreen;
import com.etheller.warsmash.WarsmashGdxMultiScreenGame;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.input.touch.ControlScheme;
import com.etheller.warsmash.input.touch.OnscreenKeyboard;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.input.touch.TouchSettings;
import com.etheller.warsmash.lan.LanHeadless;
import com.etheller.warsmash.lan.LanProperties;
import com.etheller.warsmash.lan.LanSession;
import com.etheller.warsmash.lan.LanSessions;
import com.etheller.warsmash.launcher.CustomContent;
import com.etheller.warsmash.launcher.DataFolderCheck;
import com.etheller.warsmash.launcher.GameResult;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.LauncherKeys;
import com.etheller.warsmash.launcher.MapListCache;
import com.etheller.warsmash.launcher.RenderScaleStore;
import com.etheller.warsmash.movie.Movies;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.datasources.IndependentStream;
import com.etheller.warsmash.util.DataSourceFileHandle;
import com.etheller.warsmash.util.StringBundle;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.AudioContext.Listener;
import com.etheller.warsmash.viewer5.AudioDestination;
import com.etheller.warsmash.viewer5.gl.ANGLEInstancedArrays;
import com.etheller.warsmash.viewer5.gl.AudioExtension;
import com.etheller.warsmash.viewer5.gl.DynamicShadowExtension;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.gl.WireframeExtension;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.WavPcm;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.GameOptions;

public class AndroidLauncher extends AndroidApplication {
	private static final String TAG = "Warsmash";
	static final String INI_FILE_NAME = "warsmash.ini";

	/** The texture size limit of this device when the player chose none: by screen size and memory. */
	static int defaultTextureSize(final Context context) {
		final boolean tablet = context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
		return GraphicsSettings.defaultTextureSize(true, tablet, GraphicsSettings
				.tierMemoryMb(System.getProperty(GraphicsSettings.MEMORY_TIER_PROPERTY), totalMemoryMb(context)));
	}

	static long totalMemoryMb(final Context context) {
		final ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
		((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
		return memory.totalMem >> 20;
	}
	/**
	 * Map to start right away, like the desktop -loadfile argument.
	 */
	static final String EXTRA_LOAD_FILE = "loadfile";
	/** Fixed random seed for test runs, like the desktop -Dwarsmash.seed. */
	static final String EXTRA_SEED = "seed";
	/** Profile of a test run started with a map, like the desktop -Dwarsmash.profile; never stored as current. */
	static final String EXTRA_TEST_PROFILE = "testprofile";
	/** Memory tier a run started with a map plays as ("low", "medium", "high"), like the desktop -Dwarsmash.memtier. */
	static final String EXTRA_MEMORY_TIER = "memtier";
	static final String EXTRA_SIM_THREAD = "simthread";
	private AndroidPointerSource pointerSource;
	/** Started by the launcher: the game begins at once and the launcher comes back when it ends. */
	private boolean fromLauncher;
	private volatile boolean resultWritten;
	private boolean initialized;
	private boolean loadingNoted;
	/** The game started from the launcher that is running, for the crash handler. */
	private static volatile AndroidLauncher launched;
	/** The game activity that is running by any start path, for the crash log. */
	private static volatile AndroidLauncher crashSource;
	private static boolean crashHandlerSet;
	private static final int REQUEST_MOVIES = 1;
	/** The game saved when the GL context was lost, in the app's own files; not one of the player's saves. */
	private static final String CONTEXT_LOSS_SAVE = "context-loss" + CSaveStorage.SAVE_EXTENSION;
	/** Goes on with the game once the movies it asked for have played. */
	private Runnable afterMovies;
	private GameAudioFocus audioFocus;
	private Movies.Player moviePlayer;
	private WarsmashGdxMultiScreenGame game;
	private final Handler loadWatch = new Handler(Looper.getMainLooper());
	private final Handler barsWatch = new Handler(Looper.getMainLooper());
	private final Runnable hideBars = this::hideSystemBars;
	private final Runnable loadWatcher = this::keepScreenOnWhileLoading;
	private boolean screenKeptOn;
	private boolean replayScreenOn;

	/**
	 * Settings files written before version 2 were made with music off by default, when music could not play yet; they
	 * get the new default once.
	 */
	private static void migrateIni(final File iniFile) throws IOException {
		final List<String> lines = new ArrayList<>(Files.readAllLines(iniFile.toPath(), StandardCharsets.UTF_8));
		boolean inEmulator = false;
		int emulatorHeader = -1;
		for (int i = 0; i < lines.size(); i++) {
			final String line = lines.get(i).trim();
			if (line.startsWith("[")) {
				inEmulator = "[Emulator]".equalsIgnoreCase(line);
				if (inEmulator) {
					emulatorHeader = i;
				}
			}
			else if (inEmulator && line.startsWith("SettingsVersion=")) {
				return;
			}
		}
		if (emulatorHeader < 0) {
			return;
		}
		for (int i = emulatorHeader + 1; (i < lines.size()) && !lines.get(i).trim().startsWith("["); i++) {
			if (lines.get(i).trim().equals("EnableMusic=0")) {
				lines.set(i, "EnableMusic=1");
			}
		}
		lines.add(emulatorHeader + 1, "SettingsVersion=2");
		Files.write(iniFile.toPath(), lines, StandardCharsets.UTF_8);
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (GlesSupport.redirect(this)) {
			return;
		}
		final String token = getIntent().getStringExtra(LaunchContract.EXTRA_TOKEN);
		if (token != null) {
			// Android restores a closed game activity with its old extras; that must not start the game again.
			if (showRunningGame(this)) {
				// One game at a time: the engine's state is shared by the whole process.
				Log.w(TAG, "A game is already running; the second start is dropped");
				this.resultWritten = true;
				finish();
				return;
			}
			if ((savedInstanceState != null) || !LaunchContract.consumeToken(this, token)) {
				Log.w(TAG, "Launch without a valid token, back to the launcher");
				this.resultWritten = true;
				startActivity(new Intent(this, LauncherActivity.class));
				finish();
				return;
			}
			this.fromLauncher = true;
			launched = this;
			CrashLogs.crashIfAsked(this);
		}
		// Every start path, also a map started from outside the launcher, leaves a crash log.
		crashSource = this;
		leaveCrashResult();
		MemoryState.noteAsync(this, "game activity created");
		ExitLogs.recordPreviousExit(this);
		final File iniFile = new File(EngineResources.appFolder(this), INI_FILE_NAME);
		if (!iniFile.isFile()) {
			final Intent intent = new Intent(this, DataFolderActivity.class);
			if (getIntent().getExtras() != null) {
				intent.putExtras(getIntent().getExtras());
			}
			startActivity(intent);
			finish();
			return;
		}
		final DataTable warsmashIni;
		try {
			EngineResources.ensureCurrent(this);
			migrateIni(iniFile);
			warsmashIni = loadWarsmashIni(iniFile);
		}
		catch (final IOException e) {
			Log.e(TAG, "Unable to read " + iniFile, e);
			if (this.fromLauncher) {
				// The launcher waits for this game's result; without one the player is left on the home screen.
				final GameResult failed = withoutGame("error");
				failed.map = getIntent().getStringExtra(LaunchContract.EXTRA_MAP_FILE);
				failed.reason = GameResult.describeFailure(e);
				gameEnded(failed);
				return;
			}
			finish();
			return;
		}
		// A data folder that was removed or lost its access would fail deep inside the engine start.
		if (!DataFolderCheck.hasGameData(warsmashIni.get("DataSources"))) {
			Log.e(TAG, "No game data in the configured data folder");
			final Intent intent = new Intent(this, DataFolderActivity.class);
			intent.putExtra(DataFolderActivity.EXTRA_OPEN, true);
			startActivity(intent);
			finish();
			return;
		}
		MapListCache.setShared(GameCatalog.mapListFile(this));
		RenderScaleStore.Holder.install(new RenderScaleStore() {
			@Override
			public int percent() {
				return GraphicsSettings
						.renderScalePercent(new EngineSettings(AndroidLauncher.this).graphics(GraphicsSettings.KEY_RENDER_SCALE, ""));
			}

			@Override
			public void set(final int percent) {
				new EngineSettings(AndroidLauncher.this).setGraphics(GraphicsSettings.KEY_RENDER_SCALE,
						Integer.toString(percent));
			}
		});
		WarsmashConstants.loadConstants(warsmashIni.get("Emulator"), warsmashIni);
		// The warsmash.lan.* test settings of the ini are for runs started with a map, not for the launcher's games,
		// and only a test (debuggable) build takes them.
		final boolean testBuild = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
		LanProperties.apply(this.fromLauncher || !testBuild ? null : warsmashIni.get("Emulator"));
		CCampaignProgress.setAllMissionsOpen(testBuild);
		// The process outlives the run, so a later game must not inherit a test run's tier.
		final String memoryTier = this.fromLauncher || !testBuild ? null : getIntent().getStringExtra(EXTRA_MEMORY_TIER);
		if (memoryTier != null) {
			System.setProperty(GraphicsSettings.MEMORY_TIER_PROPERTY, memoryTier);
		}
		else {
			System.clearProperty(GraphicsSettings.MEMORY_TIER_PROPERTY);
		}
		// Same for the simulation scheduling (off / serial / on): a test run names it, every other game takes the default,
		// the simulation thread, unless the system property says otherwise.
		final String simThread = this.fromLauncher || !testBuild ? null : getIntent().getStringExtra(EXTRA_SIM_THREAD);
		SimThreadMode.set(SimThreadMode.parse(simThread != null ? simThread : System.getProperty(SimThreadMode.PROPERTY, "on")));
		GraphicsSettings.applyDefaultTextureSize(warsmashIni, defaultTextureSize(this));
		GraphicsSettings.setDeviceMemoryMb(totalMemoryMb(this));
		final Element touchSettings = warsmashIni.get("Touch");
		final TouchInputRouter router = new TouchInputRouter(
				TouchSettings.fromIni(touchSettings, getResources().getDisplayMetrics().density,
						ControlScheme.TOUCH),
				SystemClock::uptimeMillis, (touchSettings != null) && (touchSettings.getFieldValue("ShowTouchDebug") != 0));
		router.setPenCapable(AndroidPointerSource.hasStylus());
		TouchInputRouter.install(router);
		this.pointerSource = new AndroidPointerSource(router,
				AndroidPointerSource.touchMajorScale(getResources().getDisplayMetrics().widthPixels,
						getResources().getDisplayMetrics().heightPixels));
		loadExtensions(new File(getCacheDir(), "sounds"));
		this.moviePlayer = (movies, done) -> runOnUiThread(() -> {
			if (isFinishing() || isDestroyed() || (this.afterMovies != null)) {
				done.run();
				return;
			}
			this.afterMovies = done;
			startActivityForResult(MovieActivity.intent(this, movies), REQUEST_MOVIES);
			overridePendingTransition(0, 0);
		});
		Movies.setPlayer(this.moviePlayer);

		final AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		config.useGL30 = true;
		config.useImmersiveMode = true;
		final String fileToLoad = getIntent().getStringExtra(EXTRA_LOAD_FILE);
		final String seed = getIntent().getStringExtra(EXTRA_SEED);
		if (seed != null) {
			System.setProperty("warsmash.seed", seed);
		}
		// A map started from outside the launcher (automated runs) may name its own profile, as the desktop
		// -Dwarsmash.profile does, so its saves and caches stay out of the player's. The process outlives the run, so
		// a later game must not inherit it.
		// Automated runs may keep the models of unit types that are no longer shown, to measure what letting them go saves.
		if (getIntent().getBooleanExtra("keepmodels", false)) {
			System.setProperty(RenderUnitTypeData.KEEP_MODELS_PROPERTY, "true");
		}
		else {
			System.clearProperty(RenderUnitTypeData.KEEP_MODELS_PROPERTY);
		}
		final String testProfile = this.fromLauncher ? null : getIntent().getStringExtra(EXTRA_TEST_PROFILE);
		if ((fileToLoad != null) && (testProfile != null) && !testProfile.isEmpty()) {
			System.setProperty("warsmash.profile", testProfile);
		}
		else {
			System.clearProperty("warsmash.profile");
		}
		final WarsmashGdxMultiScreenGame game = new WarsmashGdxMultiScreenGame();
		initialize(GlContextWatch.watch(game, () -> contextLost(game, warsmashIni, fileToLoad)), config);
		keepSystemBarsHidden();
		this.initialized = true;
		if (getIntent().getStringExtra(LaunchContract.EXTRA_REPLAY_FILE) != null) {
			// A replay is watched without touching the screen, which must not go dark under it.
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			this.replayScreenOn = true;
		}
		this.game = game;
		BackCallbacks.register(this, this::goBack);
		// An unhandled Escape comes back from Android as Back and would close the game; a keyboard's Escape works as
		// on a PC instead (ends a cinematic, closes menus and dialogs).
		Gdx.input.setCatchKey(Input.Keys.ESCAPE, true);
		GlContextWatch.dropContextOnPauseIfAsked(this, this.graphics.getView());
		trackOnscreenKeyboard();
		Gdx.app.postRunnable(() -> {
			// The launcher's Options page may have changed the file since an earlier game in this process read it.
			GameOptions.reload();
			startEngine(game, warsmashIni, fileToLoad, null);
		});
	}

	/**
	 * The skin of the loading screens when the engine is started without the launcher: the one the extras name, else
	 * the one the launcher was left on.
	 */
	private String chosenSkin() {
		final String skin = getIntent().getStringExtra(LaunchContract.EXTRA_SKIN);
		return skin != null ? skin
				: getSharedPreferences(LauncherKeys.PREFERENCES, MODE_PRIVATE).getString(LauncherKeys.KEY_SKIN, null);
	}

	/** @param continued a saved game to go on with in place of the game asked for, or null */
	private void startEngine(final WarsmashGdxMultiScreenGame game, final DataTable warsmashIni,
			final String fileToLoad, final FileHandle continued) {
		final WarsmashGdxMenuScreen menuScreen = new WarsmashGdxMenuScreen(warsmashIni, game);
		String continuedMap = null;
		if (continued != null) {
			CSaveStorage.finishBackgroundWrites();
			try {
				continuedMap = SavedGame.readHeader(continued).mapFile;
			}
			catch (final IOException e) {
				Log.e(TAG, "Unable to read the saved game " + continued, e);
			}
		}
		final FileHandle save = continuedMap != null ? continued : null;
		if (this.fromLauncher) {
			final LaunchRequest launch = LaunchContract.request(getIntent());
			launch.prepare(menuScreen);
			if (save != null) {
				menuScreen.setStartupMap(continuedMap);
			}
			menuScreen.setGameEndListener(new WarsmashGdxMenuScreen.GameEndListener() {
				@Override
				public void gameEnded(final GameResult result) {
					AndroidLauncher.this.gameEnded(result);
				}

				@Override
				public void startupFailed(final Throwable error, final GameResult result) {
					final Thread thread = Thread.currentThread();
					new Thread(() -> CrashLogs.write(AndroidLauncher.this, thread, error), "startup-failure-log").start();
					AndroidLauncher.this.gameEnded(result);
				}
			});
			menuScreen.whenLoaded(menu -> {
				final boolean started;
				if (save != null) {
					menu.useProfile(launch.profile);
					started = menu.startSavedGame(save);
				}
				else {
					started = launch.start(menu);
				}
				if (!started) {
					Log.e(TAG, "The launcher asked for a game the engine cannot start: " + launch);
					final GameResult failed = withoutGame("error");
					failed.map = launch.mapFile;
					failed.reason = menu.mapLoadFailureReason();
					failed.issue = menu.mapLoadFailureIssue();
					gameEnded(failed);
				}
			});
		}
		else if (save != null) {
			menuScreen.setStartupMap(continuedMap);
			menuScreen.whenLoaded(menu -> {
				if (!menu.startSavedGame(save)) {
					menu.showMapLoadError();
				}
			});
		}
		if (!this.fromLauncher) {
			menuScreen.setReforgedLoadingScreens(LaunchRequest.SKIN_REFORGED.equals(chosenSkin()));
		}
		game.setScreen(menuScreen);
		if (!this.fromLauncher && (save == null) && (fileToLoad != null)) {
			if (LanHeadless.requested()) {
				// A LAN test run: the ini names the role, as the desktop's system properties do.
				// After a lost GL context the session is still there: the game is rejoined, not begun a second time.
				if (LanHeadless.isActive()) {
					menuScreen.whenLoaded(LanHeadless::resume);
				}
				else {
					menuScreen.whenLoaded(
							menu -> LanHeadless.begin(menu, fileToLoad, System.getProperty("warsmash.lan.computer")));
				}
			}
			else {
				menuScreen.startMap(fileToLoad);
			}
		}
	}

	/**
	 * Everything the screens drew with went with the GL context, so they are made again: a single player game goes on
	 * from a save, a network game or a finished one ends, and the menu or a loading game starts over.
	 */
	private void contextLost(final WarsmashGdxMultiScreenGame game, final DataTable warsmashIni,
			final String fileToLoad) {
		final WarsmashGdxMapScreen running = game.getRunningMapScreen();
		final LanSession lan = LanSessions.current();
		if (this.fromLauncher && (running != null) && (lan != null) && (lan.client() != null)
				&& lan.client().isInGame() && !running.getViewer().isGameEnded()) {
			// A network game cannot be saved; the host has every turn of it, so it is played through again from there.
			final GameResult result = running.gameResult(running.localPlayerResult());
			game.disposeLostScreens();
			lan.rejoinInPlace(() -> startEngine(game, warsmashIni, fileToLoad, null), () -> gameEnded(result));
			return;
		}
		final FileHandle save = Gdx.files.local(CONTEXT_LOSS_SAVE);
		final boolean saved = (running != null) && running.saveAfterContextLoss(save);
		final GameResult result = (running != null) && !saved ? running.gameResult(running.localPlayerResult())
				: null;
		if (saved) {
			Log.i(TAG, "The game goes on from a save made at step " + running.getViewer().simulation.getGameTurnTick());
		}
		game.disposeLostScreens();
		if ((result != null) && this.fromLauncher) {
			gameEnded(result);
			return;
		}
		startEngine(game, warsmashIni, fileToLoad, saved ? save : null);
	}

	/**
	 * A game started from the launcher that has not ended (left in the background, say) is brought back instead of
	 * the launcher or a second game opening beside it in the same process.
	 *
	 * @return whether there was such a game
	 */
	static boolean showRunningGame(final Activity from) {
		final AndroidLauncher running = launched;
		if ((running == null) || running.isFinishing() || running.isDestroyed() || (running == from)) {
			return false;
		}
		final Intent intent = new Intent(from, AndroidLauncher.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
		from.startActivity(intent);
		return true;
	}

	/** Leaves the result for the launcher and gives the screen back to it. */
	private void gameEnded(final GameResult result) {
		writeResult(result);
		endLanSession();
		runOnUiThread(() -> {
			final Intent intent = new Intent(this, LauncherActivity.class);
			intent.putExtra(LaunchContract.EXTRA_RETURNED, true);
			startActivity(intent);
			finish();
			overridePendingTransition(0, 0);
		});
	}

	/** A LAN session lasts until the game is left for the launcher; closing it waits for its threads, so not here. */
	private static void endLanSession() {
		final LanSession session = LanSessions.current();
		if (session != null) {
			new Thread(() -> LanSessions.end(session), "lan-end").start();
		}
	}

	private void writeResult(final GameResult written) {
		if (this.resultWritten) {
			return;
		}
		this.resultWritten = true;
		written.source = LaunchContract.request(getIntent()).source(written);
		if (written.map == null) {
			written.map = "";
		}
		written.profile = GameCatalog.currentProfile(this);
		written.finished = System.currentTimeMillis();
		LaunchContract.writeResult(this, written);
	}

	/** A result without the game's own: one that could not start, a crash or the program closed. */
	private static GameResult withoutGame(final String result) {
		final GameResult failed = new GameResult();
		failed.result = result;
		return failed;
	}

	/** The map of the game on the screen, or the one it was asked to start while it loads; for the crash log. */
	String currentMapFile() {
		final WarsmashGdxMultiScreenGame current = this.game;
		final WarsmashGdxMapScreen running = current == null ? null : current.getRunningMapScreen();
		if ((running != null) && (running.getViewer() != null)) {
			return running.getViewer().getMapFileName();
		}
		return LaunchContract.request(getIntent()).mapFile;
	}

	/** An exception that ends the program leaves a crash result, so the launcher can tell a crash from a force stop. */
	private static synchronized void leaveCrashResult() {
		if (crashHandlerSet) {
			return;
		}
		crashHandlerSet = true;
		CrashLogs.prepare();
		final Thread.UncaughtExceptionHandler system = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
			final AndroidLauncher game = crashSource;
			if (game != null) {
				if (game.fromLauncher) {
					try {
						game.writeResult(withoutGame("crash"));
					}
					catch (final Throwable e) {
						Log.e(TAG, "Unable to leave the crash result", e);
					}
				}
				CrashLogs.write(game, thread, error);
			}
			if (system != null) {
				system.uncaughtException(thread, error);
			}
		});
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_MOVIES) {
			final Runnable done = this.afterMovies;
			this.afterMovies = null;
			if (done != null) {
				done.run();
			}
		}
	}

	@Override
	protected void onPause() {
		MemoryState.noteAsync(this, "game paused");
		// The backend pauses only the music and sounds it made itself.
		PreparedMusic.pauseAll();
		PooledSounds.pauseAll();
		if (this.audioFocus != null) {
			this.audioFocus.release();
		}
		this.loadWatch.removeCallbacks(this.loadWatcher);
		super.onPause();
		// The game cannot draw or step while the app is away; the session service keeps the connection, and the
		// host and the others are told why this player stands still.
		LanSessions.appState(true);
	}

	@Override
	protected void onResume() {
		super.onResume();
		this.loadWatch.post(this.loadWatcher);
		LanSessions.appState(false);
		if (this.audioFocus == null) {
			this.audioFocus = new GameAudioFocus(this);
		}
		this.audioFocus.acquire();
		PreparedMusic.resumeAll();
		PooledSounds.resumeAll();
	}

	/** Notes the memory as a map starts and finishes loading, with the highest total in between. */
	private void noteLoading(final boolean loading) {
		if (loading) {
			if (!this.loadingNoted) {
				this.loadingNoted = true;
				MemoryState.resetPeak();
				MemoryState.noteAsync(this, "load start");
			}
			MemoryState.samplePeakAsync();
		}
		else if (this.loadingNoted) {
			this.loadingNoted = false;
			MemoryState.noteAsync(this, "load end (peak pss " + (MemoryState.peakPssKb() >> 10) + "MB)");
		}
	}

	/** The screen stays on while a map loads, which can take minutes on a first run and draws no touch input. */
	private void keepScreenOnWhileLoading() {
		final WarsmashGdxMultiScreenGame current = this.game;
		final boolean loading = (current == null) || current.isLoadingMap();
		final boolean keep = this.replayScreenOn || ((current != null) && current.isLoadingMap());
		noteLoading(loading);
		if (keep != this.screenKeptOn) {
			this.screenKeptOn = keep;
			if (keep) {
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			}
			else {
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			}
		}
		this.loadWatch.postDelayed(this.loadWatcher, 1000);
	}

	/**
	 * Back from Android 13 on, where the system does not send it as a key: a running game gets it as its Back key
	 * (game menu, step back in menus); while a map loads it does nothing; anywhere else it closes the activity.
	 */
	private void goBack() {
		final WarsmashGdxMultiScreenGame current = this.game;
		if ((current == null) || current.isLoadingMap()) {
			// Leaving in the middle of loading would drop the player from a game the others are still starting.
			return;
		}
		final WarsmashGdxMapScreen running = current.getRunningMapScreen();
		if (running == null) {
			if (current.getScreen() instanceof WarsmashGdxMenuScreen) {
				// A campaign mission that is a model plays on the menu's screen; Back skips it.
				final WarsmashGdxMenuScreen menu = (WarsmashGdxMenuScreen) current.getScreen();
				Gdx.app.postRunnable(menu::skipModelMission);
				if (menu.isPlayingModelMission()) {
					return;
				}
			}
			finishAfterTransition();
			return;
		}
		Gdx.app.postRunnable(() -> {
			if (!running.isDisposed()) {
				running.backPressed();
			}
		});
	}

	/** Back before Android 13, which the game screen catches itself; while a map loads it is ignored too. */
	@Override
	public void onBackPressed() {
		final WarsmashGdxMultiScreenGame current = this.game;
		if ((current == null) || current.isLoadingMap()) {
			return;
		}
		super.onBackPressed();
	}

	@Override
	protected void onDestroy() {
		if (launched == this) {
			launched = null;
		}
		if (crashSource == this) {
			crashSource = null;
		}
		Movies.removePlayer(this.moviePlayer);
		if (this.audioFocus != null) {
			this.audioFocus.release();
			this.audioFocus = null;
		}
		// Exit Program in the game menu closes the program; the launcher hears that it was no crash.
		if (this.fromLauncher && isFinishing()) {
			writeResult(withoutGame("exit"));
		}
		// A start dropped for a game already running (or without a valid token) must not end that game's session.
		if (isFinishing() && (this.fromLauncher || this.initialized)) {
			endLanSession();
		}
		if (!this.initialized) {
			skipKeyboardHeightClose(this);
		}
		this.barsWatch.removeCallbacks(this.hideBars);
		super.onDestroy();
		// libGDX keeps the application object in Gdx.app until the next game starts; it must not keep the game.
		this.game = null;
		this.pointerSource = null;
	}

	/**
	 * The backend closes its keyboard height provider on destroy without checking that initialize made one, so an
	 * activity that finished before starting the engine (no data folder, say) would fail there.
	 */
	static void skipKeyboardHeightClose(final AndroidApplication application) {
		try {
			final Field field = AndroidApplication.class.getDeclaredField("keyboardHeightProvider");
			field.setAccessible(true);
			if (field.get(application) == null) {
				field.set(application, new KeyboardHeightProvider() {
					@Override
					public void start() {
					}

					@Override
					public void close() {
					}

					@Override
					public void setKeyboardHeightObserver(final KeyboardHeightObserver observer) {
					}

					@Override
					public int getKeyboardLandscapeHeight() {
						return 0;
					}

					@Override
					public int getKeyboardPortraitHeight() {
						return 0;
					}
				});
			}
		}
		catch (final ReflectiveOperationException e) {
			Log.w(TAG, "Unable to stand in for the keyboard height provider", e);
		}
	}

	/**
	 * The keyboard covers the full screen game surface rather than resizing or panning it, so the engine is told how
	 * much of the screen's bottom it hides.
	 */
	private void trackOnscreenKeyboard() {
		final View decor = getWindow().getDecorView();
		decor.setOnApplyWindowInsetsListener((view, insets) -> {
			updateOnscreenKeyboard(view, insets);
			return view.onApplyWindowInsets(insets);
		});
		decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> updateOnscreenKeyboard(decor, null));
	}

	private static void updateOnscreenKeyboard(final View decor, final WindowInsets applied) {
		final int covered;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			final WindowInsets insets = applied != null ? applied : decor.getRootWindowInsets();
			if (insets == null) {
				return;
			}
			covered = insets.isVisible(WindowInsets.Type.ime()) ? insets.getInsets(WindowInsets.Type.ime()).bottom
					: 0;
		}
		else {
			final Rect visible = new Rect();
			decor.getWindowVisibleDisplayFrame(visible);
			covered = decor.getRootView().getHeight() - visible.bottom;
		}
		OnscreenKeyboard.setCoveredHeightPixels(covered);
	}

	@Override
	public void onWindowFocusChanged(final boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			hideSystemBars();
		}
	}

	/**
	 * A mouse pushed against a screen edge asks the system for its bars like a swipe does, and some devices then keep
	 * them out; they are hidden again shortly after they appear, which leaves the system's own handling alone.
	 */
	private void keepSystemBarsHidden() {
		getWindow().getDecorView().setOnApplyWindowInsetsListener((view, insets) -> {
			final WindowInsets applied = view.onApplyWindowInsets(insets);
			this.barsWatch.removeCallbacks(this.hideBars);
			this.barsWatch.postDelayed(this.hideBars, 1500);
			return applied;
		});
	}

	@SuppressWarnings("deprecation")
	private void hideSystemBars() {
		final View decor = getWindow().getDecorView();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			final WindowInsetsController controller = decor.getWindowInsetsController();
			if (controller != null) {
				controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
				controller.hide(WindowInsets.Type.systemBars());
				return;
			}
		}
		decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
	}

	@Override
	public boolean dispatchTouchEvent(final MotionEvent event) {
		if ((this.pointerSource != null) && this.pointerSource.onTouchEvent(event)) {
			return true;
		}
		return super.dispatchTouchEvent(event);
	}

	@Override
	public boolean dispatchGenericMotionEvent(final MotionEvent event) {
		if ((this.pointerSource != null) && this.pointerSource.onGenericMotionEvent(event)) {
			return true;
		}
		return super.dispatchGenericMotionEvent(event);
	}

	static DataTable loadWarsmashIni(final File iniFile) throws IOException {
		final DataTable warsmashIni = new DataTable(StringBundle.EMPTY);
		try (InputStream stream = new FileInputStream(iniFile)) {
			warsmashIni.readTXT(stream, true);
		}
		CustomContent.apply(warsmashIni);
		return warsmashIni;
	}

	static void loadExtensions(final File soundCache) {
		SoundCache.trimInBackground(soundCache);
		Extensions.angleInstancedArrays = new ANGLEInstancedArrays() {
			@Override
			public void glVertexAttribDivisorANGLE(final int index, final int divisor) {
				Gdx.gl30.glVertexAttribDivisor(index, divisor);
			}

			@Override
			public void glDrawElementsInstancedANGLE(final int mode, final int count, final int type,
					final int indicesOffset, final int instanceCount) {
				Gdx.gl30.glDrawElementsInstanced(mode, count, type, indicesOffset, instanceCount);
			}

			@Override
			public void glDrawArraysInstancedANGLE(final int mode, final int first, final int count,
					final int instanceCount) {
				Gdx.gl30.glDrawArraysInstanced(mode, first, count, instanceCount);
			}
		};
		Extensions.dynamicShadowExtension = new DynamicShadowExtension() {
			private final IntBuffer drawBuffers = BufferUtils.newIntBuffer(1);

			// OpenGL ES 3.0 has no glFramebufferTexture, the shadow map is a 2D texture.
			@Override
			public void glFramebufferTexture(final int target, final int attachment, final int texture,
					final int level) {
				Gdx.gl30.glFramebufferTexture2D(target, attachment, GL20.GL_TEXTURE_2D, texture, level);
			}

			@Override
			public void glDrawBuffer(final int mode) {
				this.drawBuffers.clear();
				this.drawBuffers.put(mode);
				this.drawBuffers.flip();
				Gdx.gl30.glDrawBuffers(1, this.drawBuffers);
			}
		};
		// OpenGL ES has no polygon mode, so the debug wireframe view is unavailable.
		Extensions.wireframeExtension = new WireframeExtension() {
			@Override
			public void glPolygonMode(final int face, final int mode) {
			}
		};
		Extensions.audio = new AudioExtension() {
			@Override
			public long play(final Sound buffer, final float volume, final float pitch, final float x, final float y,
					final float z, final boolean is3dSound, final float maxDistance, final float refDistance,
					final boolean looping) {
				if (buffer == null) {
					return -1;
				}
				return looping ? buffer.loop(volume, pitch, 0) : buffer.play(volume, pitch, 0);
			}

			// A SoundPool cannot seek, so only streamed sounds (the long loops) tell a length and carry on in time.
			@Override
			public float duration(final Sound buffer) {
				return buffer instanceof StreamedSound ? ((StreamedSound) buffer).seconds() : 0;
			}

			@Override
			public void seek(final Sound buffer, final long id, final float seconds) {
				if (buffer instanceof StreamedSound) {
					((StreamedSound) buffer).seek(id, seconds);
				}
			}

			@Override
			public long playPanned(final Sound buffer, final float volume, final float pitch, final float pan,
					final boolean looping) {
				if (buffer == null) {
					return -1;
				}
				return looping ? buffer.loop(volume, pitch, pan) : buffer.play(volume, pitch, pan);
			}

			@Override
			public AudioContext createContext(final boolean world) {
				return new AudioContext(Listener.DO_NOTHING, new AudioDestination() {
				});
			}

			// Sounds the loading screen asked for, already decoding in the pool, by data path.
			private final Map<String, Sound> prefetched = new HashMap<>();

			// The Android backend plays sounds from real files only, so they are copied out of the
			// game data once.
			@Override
			public Sound newSound(final FileHandle file) {
				final Sound prefetchedSound = this.prefetched.remove(file.path());
				if (prefetchedSound != null) {
					return prefetchedSound;
				}
				final FileHandle copy = cachedCopy(soundCache, file);
				// The launcher's scenes run without sound.
				if (Gdx.audio instanceof DisabledAndroidAudio) {
					return Gdx.audio.newSound(copy);
				}
				return PooledSounds.get().newSound(copy.file().getPath());
			}

			// The pool decodes only from the newSound on, and the first game step then waited for the decoding of
			// every answer it preloads; started from the loading screen, that decoding is done by then.
			@Override
			public void prefetchSound(final FileHandle file) {
				if ((file == null) || (Gdx.audio instanceof DisabledAndroidAudio)
						|| this.prefetched.containsKey(file.path())) {
					return;
				}
				try {
					this.prefetched.put(file.path(),
							PooledSounds.get().newSound(cachedCopy(soundCache, file).file().getPath()));
				}
				catch (final RuntimeException e) {
					// Left to newSound, which reports it.
				}
			}

			// A sound whose copy is not made yet is copied out of the game data, and handed to the pool, on a helper
			// thread; the game data is read there only where it can be (a file its data source opens on any thread).
			@Override
			public boolean newSoundLater(final FileHandle file, final Consumer<Sound> ready) {
				final IndependentStream.Opener opener = helperOpener(file, this.prefetched);
				if (opener == null) {
					return false;
				}
				SOUND_PREPARER.execute(() -> {
					Sound made = null;
					try {
						made = PooledSounds.get()
								.newSound(cachedCopy(soundCache, file, opener).file().getPath());
					}
					catch (final RuntimeException e) {
						Log.w(TAG, "Unable to prepare " + file.path(), e);
					}
					final Sound result = made;
					Gdx.app.postRunnable(() -> ready.accept(result));
				});
				return true;
			}

			@Override
			public Sound newSoundIfReady(final FileHandle file) {
				return helperOpener(file, this.prefetched) != null ? null : newSound(file);
			}

			/** @return how a helper thread reads the file, null when the sound is made on the calling thread */
			private IndependentStream.Opener helperOpener(final FileHandle file, final Map<String, Sound> prefetched) {
				if (!(file instanceof DataSourceFileHandle) || (Gdx.audio instanceof DisabledAndroidAudio)
						|| prefetched.containsKey(file.path()) || hasCopy(soundCache, file)) {
					return null;
				}
				try {
					return ((DataSourceFileHandle) file).opener();
				}
				catch (final RuntimeException e) {
					return null;
				}
			}

			@Override
			public boolean awaitLoading(final long maxMillis) {
				return PooledSounds.get().awaitLoading(maxMillis);
			}

			@Override
			public void finishLoading(final long maxMillis) {
				PooledSounds.get().finishLoading(maxMillis);
				// Nothing the game asked for is still to come from the loading screen's list.
				for (final Sound sound : this.prefetched.values()) {
					sound.dispose();
				}
				this.prefetched.clear();
			}

			@Override
			public Music newMusic(final FileHandle file) {
				// Only the copy out of the game data, made once per track, stays on this thread.
				return new PreparedMusic(cachedCopy(soundCache, file).file().getPath());
			}
		};
	}

	private static final ExecutorService SOUND_PREPARER = Executors.newFixedThreadPool(2, runnable -> {
		final Thread thread = new Thread(runnable, "SoundPrepare");
		thread.setDaemon(true);
		return thread;
	});
	// One copy is made by one thread at a time: the game thread asking for a sound a helper is copying waits for it.
	private static final Map<String, Object> COPY_LOCKS = new ConcurrentHashMap<>();

	private static boolean hasCopy(final File cacheFolder, final FileHandle file) {
		final File copy = new File(cacheFolder, SoundCache.copyName(file.path()));
		return copy.isFile() && (copy.length() > 0);
	}

	private static FileHandle cachedCopy(final File cacheFolder, final FileHandle file) {
		return cachedCopy(cacheFolder, file, null);
	}

	/** @param opener reads the file when it is not read by the file's own handle, which is bound to its thread */
	private static FileHandle cachedCopy(final File cacheFolder, final FileHandle file,
			final IndependentStream.Opener opener) {
		final File copy = new File(cacheFolder, SoundCache.copyName(file.path()));
		SoundCache.use(copy);
		synchronized (COPY_LOCKS.computeIfAbsent(copy.getName(), name -> new Object())) {
			if (copy.isFile() && (copy.length() > 0)) {
				SoundCache.touch(copy);
			}
			else {
				makeCopy(cacheFolder, file, opener, copy);
			}
		}
		return Gdx.files.absolute(copy.getAbsolutePath());
	}

	private static void makeCopy(final File cacheFolder, final FileHandle file, final IndependentStream.Opener opener,
			final File copy) {
		cacheFolder.mkdirs();
		final File partial = new File(cacheFolder, copy.getName() + ".part");
		try (InputStream in = opener != null ? opener.open() : file.read();
				OutputStream out = new FileOutputStream(partial)) {
			if (in == null) {
				throw new IOException("The file is gone from its data source");
			}
			final byte[] buffer = new byte[16384];
			if (file.extension().equalsIgnoreCase("wav")) {
				// The device's decoders play only some of the encodings a wav can hold (not ADPCM, A-law or
				// 8 bit on every device), so the copy is made 16 bit PCM.
				final ByteArrayOutputStream whole = new ByteArrayOutputStream();
				int read;
				while ((read = in.read(buffer)) != -1) {
					whole.write(buffer, 0, read);
				}
				byte[] bytes = whole.toByteArray();
				try {
					bytes = WavPcm.toPcm16(bytes);
				}
				catch (final IOException | RuntimeException e) {
					Log.w("Warsmash", "Unable to convert " + file.path() + ": " + e);
				}
				out.write(bytes);
			}
			else {
				int read;
				while ((read = in.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
			}
		}
		catch (final IOException | RuntimeException e) {
			partial.delete();
			throw new GdxRuntimeException("Unable to copy " + file.path(), e);
		}
		if (!partial.renameTo(copy)) {
			partial.delete();
			throw new GdxRuntimeException("Unable to store " + copy);
		}
		SoundCache.copied(cacheFolder, copy.length());
	}
}
