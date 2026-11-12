package com.etheller.warsmash.android;

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
import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
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
import com.etheller.warsmash.WarsmashGdxMapScreen;
import com.etheller.warsmash.WarsmashGdxMenuScreen;
import com.etheller.warsmash.WarsmashGdxMultiScreenGame;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.input.touch.OnscreenKeyboard;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.input.touch.TouchSettings;
import com.etheller.warsmash.launcher.DataFolderCheck;
import com.etheller.warsmash.launcher.GameResult;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.LauncherKeys;
import com.etheller.warsmash.movie.Movies;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.units.DataTable;
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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.GameOptions;

public class AndroidLauncher extends AndroidApplication {
	private static final String TAG = "Warsmash";
	static final String INI_FILE_NAME = "warsmash.ini";

	/** The texture size limit of this device when the player chose none: by screen size and memory. */
	static int defaultTextureSize(final Context context) {
		final ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
		((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
		final boolean tablet = context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
		return GraphicsSettings.defaultTextureSize(true, tablet, memory.totalMem >> 20);
	}
	/**
	 * Map to start right away, like the desktop -loadfile argument.
	 */
	static final String EXTRA_LOAD_FILE = "loadfile";
	/** Fixed random seed for test runs, like the desktop -Dwarsmash.seed. */
	static final String EXTRA_SEED = "seed";
	/** Profile of a test run started with a map, like the desktop -Dwarsmash.profile; never stored as current. */
	static final String EXTRA_TEST_PROFILE = "testprofile";
	private AndroidPointerSource pointerSource;
	/** Started by the launcher: the game begins at once and the launcher comes back when it ends. */
	private boolean fromLauncher;
	private volatile boolean resultWritten;
	private boolean initialized;
	/** The game started from the launcher that is running, for the crash handler. */
	private static volatile AndroidLauncher launched;
	private static boolean crashHandlerSet;
	private static final int REQUEST_MOVIES = 1;
	/** The game saved when the GL context was lost, in the app's own files; not one of the player's saves. */
	private static final String CONTEXT_LOSS_SAVE = "context-loss" + CSaveStorage.SAVE_EXTENSION;
	/** Goes on with the game once the movies it asked for have played. */
	private Runnable afterMovies;
	private Movies.Player moviePlayer;
	private WarsmashGdxMultiScreenGame game;

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
			leaveCrashResult();
		}
		final File iniFile = new File(getExternalFilesDir(null), INI_FILE_NAME);
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
		WarsmashConstants.loadConstants(warsmashIni.get("Emulator"), warsmashIni);
		GraphicsSettings.applyDefaultTextureSize(warsmashIni, defaultTextureSize(this));
		final Element touchSettings = warsmashIni.get("Touch");
		if ((touchSettings == null) || !"Off".equalsIgnoreCase(touchSettings.getField("TouchControls"))) {
			final TouchInputRouter router = new TouchInputRouter(
					TouchSettings.fromIni(touchSettings, getResources().getDisplayMetrics().density),
					SystemClock::uptimeMillis,
					(touchSettings != null) && (touchSettings.getFieldValue("ShowTouchDebug") != 0));
			TouchInputRouter.install(router);
			this.pointerSource = new AndroidPointerSource(router);
		}
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
		final String testProfile = this.fromLauncher ? null : getIntent().getStringExtra(EXTRA_TEST_PROFILE);
		if ((fileToLoad != null) && (testProfile != null) && !testProfile.isEmpty()) {
			System.setProperty("warsmash.profile", testProfile);
		}
		else {
			System.clearProperty("warsmash.profile");
		}
		final WarsmashGdxMultiScreenGame game = new WarsmashGdxMultiScreenGame();
		initialize(GlContextWatch.watch(game, () -> contextLost(game, warsmashIni, fileToLoad)), config);
		this.initialized = true;
		if (getIntent().getStringExtra(LaunchContract.EXTRA_REPLAY_FILE) != null) {
			// A replay is watched without touching the screen, which must not go dark under it.
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
			menuScreen.setGameEndListener(this::gameEnded);
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
					gameEnded(withoutGame("error"));
				}
			});
		}
		else if (save != null) {
			menuScreen.setStartupMap(continuedMap);
			menuScreen.whenLoaded(menu -> menu.startSavedGame(save));
		}
		if (!this.fromLauncher) {
			menuScreen.setReforgedLoadingScreens(LaunchRequest.SKIN_REFORGED.equals(chosenSkin()));
		}
		game.setScreen(menuScreen);
		if (!this.fromLauncher && (save == null) && (fileToLoad != null)) {
			menuScreen.startMap(fileToLoad);
		}
	}

	/**
	 * Everything the screens drew with went with the GL context, so they are made again: a single player game goes on
	 * from a save, a network game or a finished one ends, and the menu or a loading game starts over.
	 */
	private void contextLost(final WarsmashGdxMultiScreenGame game, final DataTable warsmashIni,
			final String fileToLoad) {
		final WarsmashGdxMapScreen running = game.getRunningMapScreen();
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
		runOnUiThread(() -> {
			final Intent intent = new Intent(this, LauncherActivity.class);
			intent.putExtra(LaunchContract.EXTRA_RETURNED, true);
			startActivity(intent);
			finish();
			overridePendingTransition(0, 0);
		});
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

	/** An exception that ends the program leaves a crash result, so the launcher can tell a crash from a force stop. */
	private static synchronized void leaveCrashResult() {
		if (crashHandlerSet) {
			return;
		}
		crashHandlerSet = true;
		final Thread.UncaughtExceptionHandler system = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
			final AndroidLauncher game = launched;
			if (game != null) {
				try {
					game.writeResult(withoutGame("crash"));
				}
				catch (final Throwable e) {
					Log.e(TAG, "Unable to leave the crash result", e);
				}
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
		// The backend pauses only the music and sounds it made itself.
		PreparedMusic.pauseAll();
		PooledSounds.pauseAll();
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		PreparedMusic.resumeAll();
		PooledSounds.resumeAll();
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
		Movies.removePlayer(this.moviePlayer);
		// Exit Program in the game menu closes the program; the launcher hears that it was no crash.
		if (this.fromLauncher && isFinishing()) {
			writeResult(withoutGame("exit"));
		}
		if (!this.initialized) {
			skipKeyboardHeightClose();
		}
		super.onDestroy();
	}

	/**
	 * The backend closes its keyboard height provider on destroy without checking that initialize made one, so an
	 * activity that finished before starting the engine (no data folder, say) would fail there.
	 */
	private void skipKeyboardHeightClose() {
		try {
			final Field field = AndroidApplication.class.getDeclaredField("keyboardHeightProvider");
			field.setAccessible(true);
			if (field.get(this) == null) {
				field.set(this, new KeyboardHeightProvider() {
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
		return warsmashIni;
	}

	static void loadExtensions(final File soundCache) {
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

			// The Android backend plays sounds from real files only, so they are copied out of the
			// game data once.
			@Override
			public Sound newSound(final FileHandle file) {
				final FileHandle copy = cachedCopy(soundCache, file);
				// The launcher's scenes run without sound.
				if (Gdx.audio instanceof DisabledAndroidAudio) {
					return Gdx.audio.newSound(copy);
				}
				return PooledSounds.get().newSound(copy.file().getPath());
			}

			@Override
			public void finishLoading(final long maxMillis) {
				PooledSounds.get().finishLoading(maxMillis);
			}

			@Override
			public Music newMusic(final FileHandle file) {
				// Only the copy out of the game data, made once per track, stays on this thread.
				return new PreparedMusic(cachedCopy(soundCache, file).file().getPath());
			}
		};
	}

	private static FileHandle cachedCopy(final File cacheFolder, final FileHandle file) {
		final File copy = new File(cacheFolder, file.path().replaceAll("[^A-Za-z0-9._-]", "_"));
		if (!copy.isFile() || (copy.length() == 0)) {
			cacheFolder.mkdirs();
			final File partial = new File(cacheFolder, copy.getName() + ".part");
			try (InputStream in = file.read(); OutputStream out = new FileOutputStream(partial)) {
				final byte[] buffer = new byte[16384];
				int read;
				while ((read = in.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
			}
			catch (final IOException e) {
				throw new GdxRuntimeException("Unable to copy " + file.path(), e);
			}
			if (!partial.renameTo(copy)) {
				throw new GdxRuntimeException("Unable to store " + copy);
			}
		}
		return Gdx.files.absolute(copy.getAbsolutePath());
	}
}
