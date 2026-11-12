package com.etheller.warsmash.desktop;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.backends.lwjgl.LwjglFileHandle;
import com.badlogic.gdx.backends.lwjgl.LwjglFiles;
import com.badlogic.gdx.backends.lwjgl.LwjglNativesLoader;
import com.badlogic.gdx.backends.lwjgl.LwjglPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Clipboard;
import com.etheller.warsmash.WarsmashGdxMapScreen;
import com.etheller.warsmash.WarsmashGdxMenuScreen;
import com.etheller.warsmash.WarsmashGdxMultiScreenGame;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.Log;
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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.SimulationChecksum;

/**
 * Plays a map or a replay with no window, graphics driver or sound device, as fast as the game steps: the same
 * program as {@link DesktopLauncher}, with the game run as with -Dwarsmash.norender and every graphics call answered
 * by a driver that does nothing. Tests and tools use it to run a game to a step and read its checksum, which is the
 * one the drawn game has at that step.
 *
 * Arguments as DesktopLauncher's (-ini, -loadfile, -replay, -computer, -speed, -nolog), and:
 * <ul>
 * <li>-steps N: stop when the game reaches step N and print its checksum there;</li>
 * <li>-frametime S: game seconds a frame passes on to the game (default 1, so 20 steps a frame).</li>
 * </ul>
 * The program ends with the game (EndGame, the end of a replay with -Dwarsmash.replay.exit=true) or at -steps.
 *
 * -Dwarsmash.test.games=<map>;<map>;...;<steps> plays the maps one after another in this one process, each from the
 * menu: a game runs to the step, is left as Quit Mission leaves it, and the next map is started from the menu. The
 * checksum and the number of exceptions logged are printed for every game (and written to the file named by
 * -Dwarsmash.test.result), to compare a game that follows others with the same game run alone.
 */
public final class HeadlessLauncher {
	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;
	private static final String GAMES_PROPERTY = "warsmash.test.games";
	private static final String RESULT_PROPERTY = "warsmash.test.result";
	private static final long GAME_TIMEOUT_NANOS = 15L * 60L * 1_000_000_000L;
	private static final long LOAD_TIMEOUT_NANOS = 3L * 60L * 1_000_000_000L;

	private final ArrayDeque<Runnable> runnables = new ArrayDeque<>();
	private final Map<String, Preferences> preferences = new HashMap<>();
	private final String preferencesDirectory;
	private ApplicationListener listener;
	private boolean exitRequested;
	private float frameTime;
	private float currentFrameTime;
	private long frameId;

	private HeadlessLauncher(final String preferencesDirectory) {
		this.preferencesDirectory = preferencesDirectory;
	}

	public static void main(final String[] arg) {
		// War3MapViewer reads it once, when its class loads.
		System.setProperty("warsmash.norender", "true");
		String fileToLoad = null;
		String replayToPlay = null;
		String iniPath = null;
		String computerSlots = null;
		boolean noLogs = false;
		int steps = -1;
		float frameTime = 1f;
		for (int argIndex = 0; argIndex < arg.length; argIndex++) {
			final boolean hasValue = arg.length > (argIndex + 1);
			if ("-nolog".equals(arg[argIndex])) {
				noLogs = true;
			}
			else if (hasValue && "-loadfile".equals(arg[argIndex])) {
				fileToLoad = arg[++argIndex];
			}
			else if (hasValue && "-replay".equals(arg[argIndex])) {
				replayToPlay = arg[++argIndex];
			}
			else if (hasValue && "-ini".equals(arg[argIndex])) {
				iniPath = arg[++argIndex];
			}
			else if (hasValue && "-computer".equals(arg[argIndex])) {
				computerSlots = arg[++argIndex];
			}
			else if (hasValue && "-speed".equals(arg[argIndex])) {
				War3MapViewer.setDefaultTimeScale(Float.parseFloat(arg[++argIndex]));
			}
			else if (hasValue && "-steps".equals(arg[argIndex])) {
				steps = Integer.parseInt(arg[++argIndex]);
			}
			else if (hasValue && "-frametime".equals(arg[argIndex])) {
				frameTime = Float.parseFloat(arg[++argIndex]);
			}
		}
		if ((fileToLoad == null) && (replayToPlay == null) && (System.getProperty(GAMES_PROPERTY) == null)) {
			Log.warn("HeadlessLauncher: -loadfile <map> or -replay <file> is needed");
			System.exit(2);
		}
		if (!noLogs) {
			new File("Logs").mkdir();
			try {
				System.setOut(new PrintStream(
						new FileOutputStream(new File("Logs/" + System.currentTimeMillis() + ".out.log"))));
				System.setErr(new PrintStream(
						new FileOutputStream(new File("Logs/" + System.currentTimeMillis() + ".err.log"))));
			}
			catch (final FileNotFoundException e) {
				Log.error(e);
			}
		}
		Log.info("About to run loading file: " + (fileToLoad != null ? fileToLoad : replayToPlay));
		final HeadlessLauncher launcher = new HeadlessLauncher(System.getProperty("warsmash.prefs"));
		launcher.frameTime = frameTime;
		final int status = launcher.run(fileToLoad, replayToPlay, iniPath, computerSlots, steps);
		System.out.flush();
		System.err.flush();
		System.exit(status);
	}

	private int run(final String fileToLoad, final String replayToPlay, final String iniPath,
			final String computerSlots, final int steps) {
		LwjglNativesLoader.load();
		installBackend();
		installExtensions();
		final DataTable warsmashIni = DesktopLauncher.loadWarsmashIni(iniPath);
		final Element emulatorConstants = warsmashIni.get("Emulator");
		WarsmashConstants.loadConstants(emulatorConstants, warsmashIni);

		final WarsmashGdxMultiScreenGame game = new WarsmashGdxMultiScreenGame();
		this.listener = game;
		game.create();
		game.resize(WIDTH, HEIGHT);
		final WarsmashGdxMenuScreen menuScreen = new WarsmashGdxMenuScreen(warsmashIni, game);
		game.setScreen(menuScreen);
		final String gamesSpec = System.getProperty(GAMES_PROPERTY);
		if ((gamesSpec != null) && (replayToPlay == null)) {
			final int status = runGames(game, menuScreen, gamesSpec, computerSlots);
			game.pause();
			try {
				game.dispose();
			}
			catch (final RuntimeException e) {
				Log.error(e);
			}
			return status;
		}
		if (replayToPlay != null) {
			menuScreen.startReplay(Gdx.files.absolute(new File(replayToPlay).getAbsolutePath()));
		}
		else {
			menuScreen.startMap(fileToLoad, computerSlots);
		}

		final long started = System.nanoTime();
		long gameStarted = 0;
		int firstTick = 0;
		CSimulation last = null;
		while (!this.exitRequested) {
			runPostedRunnables();
			final CSimulation simulation = runningGame(game);
			float frame = this.frameTime;
			if (simulation != null) {
				final int tick = simulation.getGameTurnTick();
				if (last == null) {
					gameStarted = System.nanoTime();
					firstTick = tick;
					Log.warn(String.format(Locale.ROOT, "Headless: game loaded in %.1f s",
							(gameStarted - started) / 1e9));
				}
				last = simulation;
				if ((steps >= 0) && (tick >= steps)) {
					break;
				}
				if (steps >= 0) {
					// Lands on the step itself rather than past it.
					frame = Math.min(frame, (steps - tick) * WarsmashConstants.SIMULATION_STEP_TIME);
				}
			}
			else if (last != null) {
				break;
			}
			this.frameId++;
			this.currentFrameTime = frame;
			game.render();
		}
		if (last != null) {
			final double seconds = (System.nanoTime() - gameStarted) / 1e9;
			final int tick = last.getGameTurnTick();
			Log.warn(String.format(Locale.ROOT,
					"Headless: step %d checksum %08x, %d steps in %.2f s (%.0f steps/s)", tick,
					SimulationChecksum.compute(last), tick - firstTick, seconds,
					(tick - firstTick) / Math.max(seconds, 1e-9)));
		}
		game.pause();
		try {
			game.dispose();
		}
		catch (final RuntimeException e) {
			Log.error(e);
		}
		return 0;
	}

	/**
	 * Plays the maps of the spec "map;map;...;steps" one after another from the menu. Returns 0 when every game ran
	 * to its step or ended by itself (EndGame) and logged no exception, 1 when one did not, 3 when a game never
	 * started.
	 */
	private int runGames(final WarsmashGdxMultiScreenGame game, final WarsmashGdxMenuScreen menuScreen,
			final String spec, final String computerSlots) {
		final String[] parts = spec.split(";");
		final int steps = Integer.parseInt(parts[parts.length - 1].trim());
		final StringBuilder report = new StringBuilder();
		int status = 0;
		for (int index = 0; index < (parts.length - 1); index++) {
			final String map = parts[index].trim();
			menuScreen.startMap(map, computerSlots);
			final int throwablesBefore = Log.getLoggedThrowableCount();
			final long started = System.nanoTime();
			CSimulation simulation = null;
			int firstTick = 0;
			int lastTick = 0;
			String checksum = "none";
			String how = "steps";
			boolean quit = false;
			while (true) {
				runPostedRunnables();
				final CSimulation running = runningGame(game);
				float frame = this.frameTime;
				if (running != null) {
					if (simulation == null) {
						firstTick = running.getGameTurnTick();
					}
					simulation = running;
					lastTick = running.getGameTurnTick();
					if (lastTick >= steps) {
						checksum = String.format(Locale.ROOT, "%08x", SimulationChecksum.compute(running));
						game.getRunningMapScreen().quitMission();
						quit = true;
					}
					else {
						frame = Math.min(frame, (steps - lastTick) * WarsmashConstants.SIMULATION_STEP_TIME);
					}
				}
				else if (simulation != null) {
					if (!quit) {
						how = "ended";
						checksum = String.format(Locale.ROOT, "%08x", SimulationChecksum.compute(simulation));
					}
					break;
				}
				if (((System.nanoTime() - started) > (simulation == null ? LOAD_TIMEOUT_NANOS : GAME_TIMEOUT_NANOS))
						|| this.exitRequested) {
					how = "timeout";
					break;
				}
				this.frameId++;
				this.currentFrameTime = frame;
				game.render();
			}
			// The screens the game left are disposed on the frames that follow, before the next game starts.
			for (int frames = 0; frames < 3; frames++) {
				runPostedRunnables();
				this.frameId++;
				this.currentFrameTime = 0f;
				game.render();
			}
			final int throwables = Log.getLoggedThrowableCount() - throwablesBefore;
			if ((simulation == null) || "timeout".equals(how) || (throwables != 0)) {
				status = simulation == null ? 3 : Math.max(status, 1);
			}
			report.append(String.format(Locale.ROOT, "game %d map %s from %d to %d checksum %s exceptions %d end %s%n",
					index + 1, map, firstTick, lastTick, checksum, throwables, how));
			if (simulation == null) {
				break;
			}
		}
		Log.warn("Headless games:\n" + report);
		final String resultFile = System.getProperty(RESULT_PROPERTY);
		if ((resultFile != null) && !resultFile.isEmpty()) {
			try (java.io.Writer writer = new java.io.OutputStreamWriter(new FileOutputStream(resultFile),
					java.nio.charset.StandardCharsets.UTF_8)) {
				writer.write(report.toString());
				writer.write("status " + status + "\n");
			}
			catch (final java.io.IOException e) {
				Log.error(e);
				status = 1;
			}
		}
		return status;
	}

	private static CSimulation runningGame(final WarsmashGdxMultiScreenGame game) {
		final WarsmashGdxMapScreen mapScreen = game.getRunningMapScreen();
		if ((mapScreen == null) || (mapScreen.getViewer() == null) || mapScreen.getViewer().isWorldHidden()) {
			return null;
		}
		return mapScreen.getViewer().simulation;
	}

	private void runPostedRunnables() {
		Runnable runnable;
		while ((runnable = this.runnables.pollFirst()) != null) {
			runnable.run();
		}
	}

	private Preferences preferences(final String name) {
		Preferences found = this.preferences.get(name);
		if (found == null) {
			if ((this.preferencesDirectory != null) && !this.preferencesDirectory.isEmpty()) {
				found = new LwjglPreferences(new LwjglFileHandle(
						new File(new File(this.preferencesDirectory).getAbsoluteFile(), name), Files.FileType.Absolute));
			}
			else {
				found = new LwjglPreferences(
						new LwjglFileHandle(new File(".prefs/", name), Files.FileType.External));
			}
			this.preferences.put(name, found);
		}
		return found;
	}

	private void installBackend() {
		// The engine's shaders are never compiled here, so no uniform has a location to find.
		ShaderProgram.pedantic = false;
		final GL30 gl = (GL30) proxy(new Class<?>[] { GL30.class }, new NoGraphicsDriver());
		final GLVersion glVersion = new GLVersion(Application.ApplicationType.Desktop, "3.3.0 headless", "headless",
				"headless");
		final Graphics graphics = (Graphics) proxy(new Class<?>[] { Graphics.class }, (p, method, args) -> {
			switch (method.getName()) {
			case "getGL20":
			case "getGL30":
				return gl;
			case "isGL30Available":
				return true;
			case "getGLVersion":
				return glVersion;
			case "getWidth":
			case "getBackBufferWidth":
				return WIDTH;
			case "getHeight":
			case "getBackBufferHeight":
				return HEIGHT;
			case "getDeltaTime":
			case "getRawDeltaTime":
				return this.currentFrameTime;
			case "getFrameId":
				return this.frameId;
			case "getFramesPerSecond":
				return 60;
			case "getDensity":
			case "getBackBufferScale":
				return 1f;
			case "getPpiX":
			case "getPpiY":
			case "getPpcX":
			case "getPpcY":
				return 96f;
			default:
				return defaultValue(method);
			}
		});
		final Audio audio = (Audio) proxy(new Class<?>[] { Audio.class }, (p, method, args) -> {
			switch (method.getName()) {
			case "newSound":
				return proxy(new Class<?>[] { Sound.class }, (sound, soundMethod, soundArgs) -> defaultValue(soundMethod));
			case "newMusic":
				return proxy(new Class<?>[] { Music.class }, (music, musicMethod, musicArgs) -> defaultValue(musicMethod));
			default:
				return defaultValue(method);
			}
		});
		final Input input = (Input) proxy(new Class<?>[] { Input.class }, (p, method, args) -> defaultValue(method));
		final Clipboard clipboard = (Clipboard) proxy(new Class<?>[] { Clipboard.class },
				(p, method, args) -> defaultValue(method));
		final Files files = new LwjglFiles();
		final Application application = (Application) proxy(new Class<?>[] { Application.class },
				(p, method, args) -> {
					switch (method.getName()) {
					case "getApplicationListener":
						return this.listener;
					case "getGraphics":
						return graphics;
					case "getAudio":
						return audio;
					case "getInput":
						return input;
					case "getFiles":
						return files;
					case "getType":
						return Application.ApplicationType.HeadlessDesktop;
					case "getPreferences":
						return preferences((String) args[0]);
					case "getClipboard":
						return clipboard;
					case "postRunnable":
						this.runnables.add((Runnable) args[0]);
						return null;
					case "exit":
						this.exitRequested = true;
						return null;
					case "addLifecycleListener":
					case "removeLifecycleListener":
						return null;
					case "log":
					case "error":
					case "debug":
						Log.warn(args[0] + ": " + args[1]);
						if ((args.length > 2) && (args[2] instanceof Throwable)) {
							Log.error((Throwable) args[2]);
						}
						return null;
					case "getJavaHeap":
						return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
					default:
						return defaultValue(method);
					}
				});
		Gdx.app = application;
		Gdx.graphics = graphics;
		Gdx.audio = audio;
		Gdx.input = input;
		Gdx.files = files;
		Gdx.gl = gl;
		Gdx.gl20 = gl;
		Gdx.gl30 = gl;
	}

	private static void installExtensions() {
		Extensions.angleInstancedArrays = new ANGLEInstancedArrays() {
			@Override
			public void glVertexAttribDivisorANGLE(final int index, final int divisor) {
			}

			@Override
			public void glDrawElementsInstancedANGLE(final int mode, final int count, final int type,
					final int indicesOffset, final int instanceCount) {
			}

			@Override
			public void glDrawArraysInstancedANGLE(final int mode, final int first, final int count,
					final int instanceCount) {
			}
		};
		Extensions.dynamicShadowExtension = new DynamicShadowExtension() {
			@Override
			public void glFramebufferTexture(final int target, final int attachment, final int texture,
					final int level) {
			}

			@Override
			public void glDrawBuffer(final int mode) {
			}
		};
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
				return -1;
			}

			@Override
			public AudioContext createContext(final boolean world) {
				return new AudioContext(Listener.DO_NOTHING, new AudioDestination() {
				});
			}
		};
		Extensions.GL_LINE = GL20.GL_LINES;
		Extensions.GL_FILL = 0;
	}

	private static Object proxy(final Class<?>[] interfaces, final InvocationHandler handler) {
		return Proxy.newProxyInstance(HeadlessLauncher.class.getClassLoader(), interfaces, (p, method, args) -> {
			if (method.getDeclaringClass() == Object.class) {
				switch (method.getName()) {
				case "hashCode":
					return System.identityHashCode(p);
				case "equals":
					return p == args[0];
				default:
					return interfaces[0].getSimpleName() + "@headless";
				}
			}
			return handler.invoke(p, method, args);
		});
	}

	private static Object defaultValue(final Method method) {
		final Class<?> type = method.getReturnType();
		if (type == boolean.class) {
			return false;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == short.class) {
			return (short) 0;
		}
		if (type == byte.class) {
			return (byte) 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == float.class) {
			return 0f;
		}
		if (type == double.class) {
			return 0d;
		}
		if (type == char.class) {
			return (char) 0;
		}
		if (type == String.class) {
			return "";
		}
		return null;
	}

	/**
	 * Every object the program asks for gets a new name, every status check passes and every query reads a usable
	 * value, so nothing the engine builds for drawing fails; nothing is drawn.
	 */
	private static final class NoGraphicsDriver implements InvocationHandler {
		private final AtomicInteger names = new AtomicInteger(1);

		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args) {
			final String name = method.getName();
			switch (name) {
			case "glGenTexture":
			case "glGenBuffer":
			case "glGenFramebuffer":
			case "glGenRenderbuffer":
			case "glGenVertexArray":
			case "glCreateShader":
			case "glCreateProgram":
				return this.names.getAndIncrement();
			case "glGenTextures":
			case "glGenBuffers":
			case "glGenFramebuffers":
			case "glGenRenderbuffers":
			case "glGenVertexArrays":
			case "glGenQueries":
			case "glGenSamplers":
			case "glGenTransformFeedbacks":
				fillNames(args);
				return null;
			case "glCheckFramebufferStatus":
				return GL20.GL_FRAMEBUFFER_COMPLETE;
			case "glGetShaderiv":
			case "glGetProgramiv":
				putInt(args[args.length - 1], isStatus((Integer) args[1]) ? 1 : 0);
				return null;
			case "glGetIntegerv":
				putInt(args[args.length - 1], integerValue((Integer) args[0]));
				return null;
			case "glGetFloatv":
				if (args[args.length - 1] instanceof FloatBuffer) {
					final FloatBuffer buffer = (FloatBuffer) args[args.length - 1];
					buffer.put(buffer.position(), 1f);
				}
				return null;
			case "glGetString":
				return ((Integer) args[0]) == GL20.GL_VERSION ? "3.3 headless" : "headless";
			case "glGetShaderInfoLog":
			case "glGetProgramInfoLog":
				return "";
			case "glGetUniformLocation":
			case "glGetAttribLocation":
				return 0;
			case "glIsEnabled":
				return false;
			default:
				return defaultValue(method);
			}
		}

		private static boolean isStatus(final int parameter) {
			return (parameter == GL20.GL_COMPILE_STATUS) || (parameter == GL20.GL_LINK_STATUS)
					|| (parameter == GL20.GL_VALIDATE_STATUS);
		}

		private static int integerValue(final int parameter) {
			switch (parameter) {
			case GL20.GL_MAX_TEXTURE_SIZE:
			case GL20.GL_MAX_RENDERBUFFER_SIZE:
				return 8192;
			case GL20.GL_MAX_TEXTURE_IMAGE_UNITS:
			case GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS:
			case GL20.GL_MAX_VERTEX_ATTRIBS:
				return 16;
			default:
				return 0;
			}
		}

		private void fillNames(final Object[] args) {
			for (final Object arg : args) {
				if (arg instanceof IntBuffer) {
					final IntBuffer buffer = (IntBuffer) arg;
					for (int i = buffer.position(); i < buffer.limit(); i++) {
						buffer.put(i, this.names.getAndIncrement());
					}
					return;
				}
				if (arg instanceof int[]) {
					final int[] array = (int[]) arg;
					for (int i = 0; i < array.length; i++) {
						array[i] = this.names.getAndIncrement();
					}
					return;
				}
			}
		}

		private static void putInt(final Object target, final int value) {
			if (target instanceof IntBuffer) {
				final IntBuffer buffer = (IntBuffer) target;
				if (buffer.position() < buffer.limit()) {
					buffer.put(buffer.position(), value);
				}
			}
		}
	}
}
