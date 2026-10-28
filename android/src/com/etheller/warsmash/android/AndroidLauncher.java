package com.etheller.warsmash.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.etheller.warsmash.WarsmashGdxMenuScreen;
import com.etheller.warsmash.WarsmashGdxMultiScreenGame;
import com.etheller.warsmash.input.touch.TouchInputRouter;
import com.etheller.warsmash.input.touch.TouchSettings;
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

public class AndroidLauncher extends AndroidApplication {
	private static final String TAG = "Warsmash";
	static final String INI_FILE_NAME = "warsmash.ini";
	/**
	 * Map to start right away, like the desktop -loadfile argument.
	 */
	private static final String EXTRA_LOAD_FILE = "loadfile";
	private AndroidPointerSource pointerSource;

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
			migrateIni(iniFile);
			warsmashIni = loadWarsmashIni(iniFile);
		}
		catch (final IOException e) {
			Log.e(TAG, "Unable to read " + iniFile, e);
			finish();
			return;
		}
		WarsmashConstants.loadConstants(warsmashIni.get("Emulator"), warsmashIni);
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

		final AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		config.useGL30 = true;
		config.useImmersiveMode = true;
		final String fileToLoad = getIntent().getStringExtra(EXTRA_LOAD_FILE);
		final WarsmashGdxMultiScreenGame game = new WarsmashGdxMultiScreenGame();
		initialize(game, config);
		Gdx.app.postRunnable(() -> {
			final WarsmashGdxMenuScreen menuScreen = new WarsmashGdxMenuScreen(warsmashIni, game);
			game.setScreen(menuScreen);
			if (fileToLoad != null) {
				menuScreen.startMap(fileToLoad);
			}
		});
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

	private static DataTable loadWarsmashIni(final File iniFile) throws IOException {
		final DataTable warsmashIni = new DataTable(StringBundle.EMPTY);
		try (InputStream stream = new FileInputStream(iniFile)) {
			warsmashIni.readTXT(stream, true);
		}
		return warsmashIni;
	}

	private static void loadExtensions(final File soundCache) {
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
			// SoundPool does not report durations, so they are read from the cached file.
			private final Map<Sound, Float> durations = Collections.synchronizedMap(new WeakHashMap<>());

			@Override
			public float getDuration(final Sound sound) {
				final Float duration = sound == null ? null : this.durations.get(sound);
				return duration == null ? 1 : duration;
			}

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
			public AudioContext createContext(final boolean world) {
				return new AudioContext(Listener.DO_NOTHING, new AudioDestination() {
				});
			}

			// The Android backend plays sounds from real files only, so they are copied out of the
			// game data once.
			@Override
			public Sound newSound(final FileHandle file) {
				final FileHandle copy = cachedCopy(soundCache, file);
				final Sound sound = Gdx.audio.newSound(copy);
				final float duration = readDuration(copy.file());				if (duration > 0) {
					this.durations.put(sound, duration);
				}
				return sound;
			}

			@Override
			public Music newMusic(final FileHandle file) {
				return Gdx.audio.newMusic(cachedCopy(soundCache, file));
			}
		};
	}

	/**
	 * @return the duration in seconds, or 0 if the file does not report one
	 */
	private static float readDuration(final File file) {
		final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		try {
			retriever.setDataSource(file.getAbsolutePath());
			final String millis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
			return millis == null ? 0 : Long.parseLong(millis) / 1000f;
		}
		catch (final RuntimeException e) {
			return 0;
		}
		finally {
			try {
				retriever.release();
			}
			catch (final IOException | RuntimeException e) {
				Log.w(TAG, "Unable to release the metadata reader", e);
			}
		}
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
