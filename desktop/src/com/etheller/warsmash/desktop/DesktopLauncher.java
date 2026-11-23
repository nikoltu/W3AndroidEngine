package com.etheller.warsmash.desktop;

import static org.lwjgl.openal.AL10.AL_ORIENTATION;
import static org.lwjgl.openal.AL10.AL_POSITION;
import static org.lwjgl.openal.AL10.alListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl.LwjglNativesLoader;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CCampaignProgress;
import com.etheller.warsmash.WarsmashGdxFDFTestRenderScreen;
import com.etheller.warsmash.WarsmashGdxMenuScreen;
import com.etheller.warsmash.WarsmashGdxMultiScreenGame;
import com.etheller.warsmash.audio.OpenALAudio;
import com.etheller.warsmash.audio.OpenALSound;
import com.etheller.warsmash.input.touch.ControlScheme;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.input.touch.TouchSettings;
import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.desktop.launcher.LauncherScreen;
import com.etheller.warsmash.desktop.launcher.LauncherWindow;
import com.etheller.warsmash.launcher.CustomContent;
import com.etheller.warsmash.launcher.CustomFolderChooser;
import com.etheller.warsmash.launcher.LauncherKeys;
import com.etheller.warsmash.launcher.MapListCache;
import com.etheller.warsmash.launcher.RenderScaleStore;
import com.etheller.warsmash.launcher.SkinArt;
import com.etheller.warsmash.movie.GdxMoviePlayer;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.Log;
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
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;

public class DesktopLauncher {
	/** Dots per inch of the main display against the 96 that one dp stands for. */
	private static float screenDensity() {
		try {
			return Math.max(1f, java.awt.Toolkit.getDefaultToolkit().getScreenResolution() / 96f);
		}
		catch (final Throwable e) {
			return 1f;
		}
	}

	public static void main(final String[] arg) {
		Log.info("Warsmash engine is starting...");
		CCampaignProgress.setAllMissionsOpen(Boolean.getBoolean("warsmash.allMissions"));
		final LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.useGL30 = true;
		// Looping ambience and the sounds of a battle share the sources; a device that gives fewer keeps what it gives.
		config.audioDeviceSimultaneousSources = 32;
		config.gles30ContextMajorVersion = 3;
		config.gles30ContextMinorVersion = 3;
//		config.samples = 16;
//		config.vSyncEnabled = false;
		config.addIcon("resources/Icon16.png", Files.FileType.Internal);
		config.addIcon("resources/Icon32.png", Files.FileType.Internal);
		config.addIcon("resources/Icon64.png", Files.FileType.Internal);
		config.addIcon("resources/Icon128.png", Files.FileType.Internal);
//		config.foregroundFPS = 0;
//		config.backgroundFPS = 0;
		final DisplayMode desktopDisplayMode = LwjglApplicationConfiguration.getDesktopDisplayMode();
		config.width = desktopDisplayMode.width;
		config.height = desktopDisplayMode.height;
		config.fullscreen = true;
		// Another preferences folder (engine profiles, launcher choices) instead of .prefs in the user's home, so
		// automated runs never change the player's own settings.
		final String preferencesDirectory = System.getProperty("warsmash.prefs");
		if ((preferencesDirectory != null) && !preferencesDirectory.isEmpty()) {
			config.preferencesDirectory = new File(preferencesDirectory).getAbsolutePath();
			config.preferencesFileType = Files.FileType.Absolute;
		}
		String fileToLoad = null;
		String replayToPlay = null;
		String iniPath = null;
		String computerSlots = null;
		boolean noLogs = false;
		String skin = null;
		boolean waitForTap = false;
		boolean launcherMode = false;
		boolean glueMenu = false;
		boolean window = false;
		for (int argIndex = 0; argIndex < arg.length; argIndex++) {
			if ("-window".equals(arg[argIndex])) {
				config.fullscreen = false;
				window = true;
			}
			else if ("-glue".equals(arg[argIndex])) {
				glueMenu = true;
			}
			else if ("-nolog".equals(arg[argIndex])) {
				noLogs = true;
			}
			else if ((arg.length > (argIndex + 1)) && "-loadfile".equals(arg[argIndex])) {
				argIndex++;
				fileToLoad = arg[argIndex];
			}
			else if ((arg.length > (argIndex + 1)) && "-replay".equals(arg[argIndex])) {
				argIndex++;
				replayToPlay = arg[argIndex];
			}
			else if ((arg.length > (argIndex + 1)) && "-ini".equals(arg[argIndex])) {
				argIndex++;
				iniPath = arg[argIndex];
			}
			else if ((arg.length > (argIndex + 1)) && "-computer".equals(arg[argIndex])) {
				argIndex++;
				computerSlots = arg[argIndex];
			}
			else if ((arg.length > (argIndex + 1)) && "-speed".equals(arg[argIndex])) {
				argIndex++;
				War3MapViewer.setDefaultTimeScale(Float.parseFloat(arg[argIndex]));
			}
			else if ((arg.length > (argIndex + 1)) && "-skin".equals(arg[argIndex])) {
				argIndex++;
				skin = arg[argIndex];
			}
			else if ("-waitfortap".equals(arg[argIndex])) {
				waitForTap = true;
			}
			else if ("-launcher".equals(arg[argIndex])) {
				launcherMode = true;
			}
		}
		if (!noLogs) {
			new File("Logs").mkdir();
			try {
				System.setOut(new PrintStream(
						new FileOutputStream(new File("Logs/" + System.currentTimeMillis() + ".out.log"))));
			}
			catch (final FileNotFoundException e) {
				Log.error(e);
			}
			try {
				System.setErr(new PrintStream(
						new FileOutputStream(new File("Logs/" + System.currentTimeMillis() + ".err.log"))));
			}
			catch (final FileNotFoundException e) {
				Log.error(e);
			}
		}
		loadExtensions();
		com.etheller.warsmash.lan.LanSessions.setNetwork(new DesktopLanNetwork());
		final DataTable warsmashIni = loadWarsmashIni(iniPath);
		final Element emulatorConstants = warsmashIni.get("Emulator");
		WarsmashConstants.loadConstants(emulatorConstants, warsmashIni);
		// Always installed: the Options page can switch to the touch scheme without a restart.
		final Element touchSettings = warsmashIni.get("Touch");
		TouchInputRouter.install(new TouchInputRouter(
				TouchSettings.fromIni(touchSettings, screenDensity(), ControlScheme.MOUSE_KEYBOARD), TimeUtils::millis,
				(touchSettings != null) && (touchSettings.getFieldValue("ShowTouchDebug") != 0)));

		if (fileToLoad != null) {
			Log.info("About to run loading file: " + fileToLoad);
		}
		// Without a map to load the program opens on its launcher; -glue opens the engine's own menu instead.
		final boolean showLauncher = !glueMenu && !launcherMode && (fileToLoad == null) && (replayToPlay == null);
		if (showLauncher) {
			LauncherWindow.configure(config, window);
		}
		final WarsmashGdxMultiScreenGame warsmashGdxMultiScreenGame = new WarsmashGdxMultiScreenGame();
		warsmashGdxMultiScreenGame.setMoviePlayer(new GdxMoviePlayer(new FfmpegMovieDecoders()));
		new LwjglApplication(warsmashGdxMultiScreenGame, config);
		final String finalFileToLoad = fileToLoad;
		final String finalReplayToPlay = replayToPlay;
		final String finalComputerSlots = computerSlots;
		final String finalSkin = skin;
		final boolean finalWaitForTap = waitForTap;
		final boolean finalLauncherMode = launcherMode;
		final String finalIniPath = iniPath;
		Gdx.app.postRunnable(new Runnable() {
			@Override
			public void run() {
				if (showLauncher) {
					warsmashGdxMultiScreenGame
							.setScreen(new LauncherScreen(warsmashGdxMultiScreenGame, warsmashIni, finalIniPath));
				}
				else if ((finalFileToLoad != null) && finalFileToLoad.toLowerCase().endsWith(".toc")) {
					warsmashGdxMultiScreenGame.setScreen(new WarsmashGdxFDFTestRenderScreen(warsmashIni,
							warsmashGdxMultiScreenGame, finalFileToLoad));
				}
				else {
					final WarsmashGdxMenuScreen menuScreen = new WarsmashGdxMenuScreen(warsmashIni,
							warsmashGdxMultiScreenGame);
					// Without -skin the loading screens follow the skin the launcher was left on.
					final String loadingSkin = finalSkin != null ? finalSkin
							: Gdx.app.getPreferences(LauncherScreen.PREFERENCES).getString(LauncherKeys.KEY_SKIN, null);
					menuScreen.setReforgedLoadingScreens(SkinArt.REFORGED_SKIN.equalsIgnoreCase(loadingSkin));
					// So do the in-game graphics, unless the configuration names them.
					if (warsmashIni.get(GraphicsSettings.SECTION) == null) {
						final Preferences launcher = Gdx.app.getPreferences(LauncherScreen.PREFERENCES);
						GraphicsSettings.apply(warsmashIni,
								GraphicsSet.fromIniName(launcher.getString(LauncherKeys.KEY_GRAPHICS, null)),
								launcher.contains(LauncherKeys.KEY_TEXTURE_SIZE)
										? launcher.getInteger(LauncherKeys.KEY_TEXTURE_SIZE, 0)
										: -1);
					}
					menuScreen.setLauncherPreferences(LauncherScreen.PREFERENCES);
					menuScreen.setWaitForTap(finalWaitForTap);
					if (finalLauncherMode) {
						// As the Android launcher starts a game: no menu around it, and the end of the game closes the
						// program instead of showing the menu.
						menuScreen.setLauncherMode(true);
						menuScreen.setStartupMap(finalFileToLoad);
						menuScreen.setGameEndListener(result -> {
							Log.error("Game ended: " + result.result + " " + result.map + " " + result.loadFailure);
							Gdx.app.exit();
						});
					}
					warsmashGdxMultiScreenGame.setScreen(menuScreen);
					if (finalReplayToPlay != null) {
						menuScreen.startReplay(Gdx.files.absolute(new File(finalReplayToPlay).getAbsolutePath()));
					}
					else if (finalFileToLoad != null) {
						menuScreen.startMap(finalFileToLoad, finalComputerSlots);
					}
				}
			}
		});
	}

	public static DataTable loadWarsmashIni(final String iniPath) {
		final File defaultIniFile = new File(DEFAULT_INI);
		final DataTable warsmashIni = readIni(iniPath != null ? new File(iniPath) : defaultIniFile);
		if ((iniPath != null) && defaultIniFile.isFile() && !sameFile(new File(iniPath), defaultIniFile)) {
			addMissingSettings(warsmashIni, readIni(defaultIniFile));
		}
		MapListCache.setShared(new File(MAP_LIST_FILE));
		CustomFolderChooser.Holder.install(
				new com.etheller.warsmash.desktop.launcher.DesktopCustomFolder(iniPath != null ? new File(iniPath) : defaultIniFile));
		RenderScaleStore.Holder.install(new com.etheller.warsmash.desktop.launcher.DesktopRenderScale());
		CustomContent.apply(warsmashIni);
		return warsmashIni;
	}

	private static final String DEFAULT_INI = "warsmash.ini";
	/** The custom game list the launcher and the engine's menu share. */
	public static final String MAP_LIST_FILE = "Logs/launcher-maps.txt";

	private static DataTable readIni(final File iniFile) {
		final DataTable warsmashIni = new DataTable(StringBundle.EMPTY);
		try (FileInputStream warsmashIniInputStream = new FileInputStream(iniFile)) {
			warsmashIni.readTXT(warsmashIniInputStream, true);
		}
		catch (final FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
		return warsmashIni;
	}

	/**
	 * Data packaging tools write an ini with only the game data sources. Missing sections come from the default ini,
	 * and so do its relative folder sources, which are the engine's own files. Keys are not filled in one by one: an
	 * absent AbilityCompatibility means none, unlike the default ini.
	 */
	private static void addMissingSettings(final DataTable warsmashIni, final DataTable defaults) {
		for (final String section : defaults.keySet()) {
			final Element defaultElement = defaults.get(section);
			final Element element = warsmashIni.get(section);
			if (element == null) {
				warsmashIni.put(section, defaultElement);
			}
			else if ("DataSources".equalsIgnoreCase(section)) {
				addEngineDataSources(element, defaultElement);
			}
		}
	}

	private static void addEngineDataSources(final Element dataSources, final Element defaults) {
		int count = dataSources.getFieldValue("Count");
		final List<File> folders = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			if ("Folder".equalsIgnoreCase(dataSources.getField(dataSourceKey("Type", i)))) {
				folders.add(new File(dataSources.getField(dataSourceKey("Path", i))));
			}
		}
		final int defaultCount = defaults.getFieldValue("Count");
		for (int i = 0; i < defaultCount; i++) {
			final File folder = new File(defaults.getField(dataSourceKey("Path", i)));
			if (!"Folder".equalsIgnoreCase(defaults.getField(dataSourceKey("Type", i))) || folder.isAbsolute()) {
				continue;
			}
			boolean present = false;
			for (final File existing : folders) {
				present |= sameFile(existing, folder);
			}
			if (!present) {
				dataSources.setField(dataSourceKey("Type", count), "Folder");
				dataSources.setField(dataSourceKey("Path", count), folder.getPath());
				count++;
			}
		}
		dataSources.setField("Count", Integer.toString(count));
	}

	private static String dataSourceKey(final String prefix, final int index) {
		return String.format(Locale.ROOT, "%s%02d", prefix, index);
	}

	private static boolean sameFile(final File a, final File b) {
		try {
			return a.getCanonicalFile().equals(b.getCanonicalFile());
		}
		catch (final IOException e) {
			return a.getAbsoluteFile().equals(b.getAbsoluteFile());
		}
	}

	public static DataTable loadWarsmashIni() {
		return loadWarsmashIni(null);
	}

	public static void loadExtensions() {
		LwjglNativesLoader.load();
		Extensions.angleInstancedArrays = new ANGLEInstancedArrays() {
			@Override
			public void glVertexAttribDivisorANGLE(final int index, final int divisor) {
				GL33.glVertexAttribDivisor(index, divisor);
			}

			@Override
			public void glDrawElementsInstancedANGLE(final int mode, final int count, final int type,
					final int indicesOffset, final int instanceCount) {
				GL31.glDrawElementsInstanced(mode, count, type, indicesOffset, instanceCount);
			}

			@Override
			public void glDrawArraysInstancedANGLE(final int mode, final int first, final int count,
					final int instanceCount) {
				GL31.glDrawArraysInstanced(mode, first, count, instanceCount);
			}
		};
		Extensions.dynamicShadowExtension = new DynamicShadowExtension() {
			@Override
			public void glFramebufferTexture(final int target, final int attachment, final int texture,
					final int level) {
				GL32.glFramebufferTexture(target, attachment, texture, level);
			}

			@Override
			public void glDrawBuffer(final int mode) {
				GL11.glDrawBuffer(mode);
			}
		};
		Extensions.wireframeExtension = new WireframeExtension() {
			@Override
			public void glPolygonMode(final int face, final int mode) {
				GL11.glPolygonMode(face, mode);
			}
		};
		Extensions.audio = new AudioExtension() {
			final FloatBuffer orientation = BufferUtils.createFloatBuffer(6).clear();
			final FloatBuffer position = BufferUtils.createFloatBuffer(3).clear();

			@Override
			public void prefetchSound(final FileHandle file) {
				if (Gdx.audio instanceof OpenALAudio) {
					((OpenALAudio) Gdx.audio).prefetchSound(file);
				}
			}

			@Override
			public void finishLoading(final long maxMillis) {
				if (Gdx.audio instanceof OpenALAudio) {
					((OpenALAudio) Gdx.audio).dropPrefetched();
				}
			}

			@Override
			public boolean newSoundLater(final FileHandle file, final java.util.function.Consumer<Sound> ready) {
				return (Gdx.audio instanceof OpenALAudio) && ((OpenALAudio) Gdx.audio).newSoundLater(file, ready);
			}

			@Override
			public Sound newSoundIfReady(final FileHandle file) {
				return (Gdx.audio instanceof OpenALAudio) ? ((OpenALAudio) Gdx.audio).newSoundIfReady(file)
						: Gdx.audio.newSound(file);
			}

			@Override
			public long play(final Sound buffer, final float volume, final float pitch, final float x, final float y,
					final float z, final boolean is3dSound, final float maxDistance, final float refDistance,
					final boolean looping) {
				return ((OpenALSound) buffer).play(volume, pitch, x, y, z, is3dSound, maxDistance, refDistance,
						looping);
			}

			@Override
			public void setPosition(final Sound buffer, final long id, final float x, final float y, final float z,
					final float maxDistance, final float refDistance) {
				if (buffer instanceof OpenALSound) {
					((OpenALSound) buffer).setPosition(id, x, y, z, true, maxDistance, refDistance);
				}
			}

			@Override
			public float duration(final Sound buffer) {
				return buffer instanceof OpenALSound ? ((OpenALSound) buffer).duration() : 0;
			}

			@Override
			public void seek(final Sound buffer, final long id, final float seconds) {
				if (buffer instanceof OpenALSound) {
					((OpenALSound) buffer).setOffset(id, seconds);
				}
			}

			@Override
			public AudioContext createContext(final boolean world) {
				Listener listener;
				if (world && AL.isCreated()) {
					listener = new Listener() {
						private float x;
						private float y;
						private float z;

						@Override
						public void setPosition(final float x, final float y, final float z) {
							this.x = x;
							this.y = y;
							this.z = z;
							position.put(0, x);
							position.put(1, y);
							position.put(2, z);
							alListener(AL_POSITION, position);
						}

						@Override
						public float getX() {
							return this.x;
						}

						@Override
						public float getY() {
							return this.y;
						}

						@Override
						public float getZ() {
							return this.z;
						}

						@Override
						public void setOrientation(final float forwardX, final float forwardY, final float forwardZ,
								final float upX, final float upY, final float upZ) {
							orientation.put(0, forwardX);
							orientation.put(1, forwardY);
							orientation.put(2, forwardZ);
							orientation.put(3, upX);
							orientation.put(4, upY);
							orientation.put(5, upZ);
							alListener(AL_ORIENTATION, orientation);
						}

						@Override
						public boolean is3DSupported() {
							return true;
						}
					};
				}
				else {
					listener = Listener.DO_NOTHING;
				}

				return new AudioContext(listener, new AudioDestination() {
				});
			}
		};
		Extensions.GL_LINE = GL11.GL_LINE;
		Extensions.GL_FILL = GL11.GL_FILL;
	}
}
