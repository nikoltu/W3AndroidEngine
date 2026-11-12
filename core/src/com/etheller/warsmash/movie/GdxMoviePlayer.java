package com.etheller.warsmash.movie;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.WarsmashGdxGame;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.GameOptions;

/**
 * Plays the game's movies in the game's own window, one after another: the picture keeps its shape between black bars,
 * the sound plays at the volume of the sound options and the captions are drawn under the picture. A click, a touch or
 * any key skips the movie playing. While the movies play the game's screen is neither drawn nor given input.
 */
public final class GdxMoviePlayer implements Movies.Player {
	private static final int MAX_FRAME_BYTES = 4 << 20;
	/** Pictures decoded ahead of the one on screen. */
	private static final int PICTURES = 4;
	private static final int SOUND_SAMPLES = 4096;
	private static final long STOP_WAIT_MILLIS = 1000;

	private final MovieDecoders decoders;
	private final InputProcessor skipInput = new InputAdapter() {
		@Override
		public boolean keyDown(final int keycode) {
			skip();
			return true;
		}

		@Override
		public boolean keyUp(final int keycode) {
			return true;
		}

		@Override
		public boolean keyTyped(final char character) {
			return true;
		}

		@Override
		public boolean touchDown(final int screenX, final int screenY, final int pointer, final int button) {
			skip();
			return true;
		}

		@Override
		public boolean touchUp(final int screenX, final int screenY, final int pointer, final int button) {
			return true;
		}

		@Override
		public boolean touchDragged(final int screenX, final int screenY, final int pointer) {
			return true;
		}

		@Override
		public boolean scrolled(final float amountX, final float amountY) {
			return true;
		}
	};

	private boolean active;
	private List<Movie> movies = Collections.emptyList();
	private int next;
	private Runnable done;
	private InputProcessor gameInput;
	private Playback playing;

	private SpriteBatch batch;
	private final MovieTexture picture = new MovieTexture();
	private BitmapFont font;
	private File fontFile;
	private File captionFont;
	private int fontSize;
	private String fontCharacters;
	private MovieCaptions fontCaptions;
	private final GlyphLayout captionLayout = new GlyphLayout();

	public GdxMoviePlayer(final MovieDecoders decoders) {
		this.decoders = decoders;
	}

	@Override
	public void play(final List<Movie> movies, final Runnable done) {
		final List<Movie> copy = new ArrayList<>(movies);
		Gdx.app.postRunnable(() -> start(copy, done));
	}

	private void start(final List<Movie> movies, final Runnable done) {
		if (this.active || movies.isEmpty()) {
			done.run();
			return;
		}
		this.active = true;
		this.movies = movies;
		this.next = 0;
		this.done = done;
		this.gameInput = Gdx.input.getInputProcessor();
		Gdx.input.setInputProcessor(this.skipInput);
	}

	/**
	 * Draws the movie playing; called by the game in place of its screen's frame.
	 *
	 * @return false when no movie plays, and the game draws its screen
	 */
	public boolean render() {
		if (!this.active) {
			return false;
		}
		if ((this.playing != null) && this.playing.isOver()) {
			stopPlaying();
		}
		if ((this.playing == null) && !playNext()) {
			finish();
			clearScreen();
			return true;
		}
		final Playback playback = this.playing;
		final long now = TimeUtils.millis();
		final MoviePicture picture = playback.pictureDue(now);
		draw(picture, playback, now);
		if (picture != null) {
			playback.recycle(picture);
		}
		return true;
	}

	private void skip() {
		if (this.playing != null) {
			Log.info("Movie skipped: " + this.playing.movie.getName());
			stopPlaying();
		}
	}

	private boolean playNext() {
		while (this.next < this.movies.size()) {
			final Movie movie = this.movies.get(this.next++);
			final Playback playback = open(movie);
			if (playback != null) {
				this.playing = playback;
				this.picture.forget();
				playback.start();
				return true;
			}
		}
		return false;
	}

	private Playback open(final Movie movie) {
		AviReader reader = null;
		MovieDecoders.VideoDecoder decoder = null;
		try {
			reader = new AviReader(movie.getVideo());
			decoder = this.decoders.openVideo(reader.getCodec(), reader.getWidth(), reader.getHeight());
			if (decoder == null) {
				Log.warn("No decoder for the movie " + movie + " (" + reader.getCodec() + ")");
				reader.close();
				return null;
			}
			Log.info("Movie " + movie.getName() + " " + reader.getWidth() + "x" + reader.getHeight());
			// The subtitles setting is read as each movie starts, so a change between two movies counts.
			final File captions = GameOptions.get().isSubtitles() ? movie.getCaptions() : null;
			return new Playback(movie, reader, decoder, MovieCaptions.read(captions), openSound(movie));
		}
		catch (final IOException | RuntimeException | LinkageError e) {
			Log.error("Unable to play the movie " + movie + ": " + e);
			close(decoder);
			close(reader);
			return null;
		}
	}

	private Sound openSound(final Movie movie) {
		if (movie.getAudio() == null) {
			return null;
		}
		MovieDecoders.SoundDecoder decoder = null;
		try {
			decoder = this.decoders.openSound(movie.getAudio());
			if (decoder == null) {
				return null;
			}
			final GameOptions options = GameOptions.get();
			final float volume = options.isSound() ? Math.max(0, Math.min(100, options.getSoundVolume())) / 100f : 0;
			return new Sound(decoder, volume);
		}
		catch (final IOException | RuntimeException | LinkageError e) {
			Log.error("The movie sound cannot play: " + movie.getAudio() + ": " + e);
			close(decoder);
			return null;
		}
	}

	private void stopPlaying() {
		final Playback playback = this.playing;
		this.playing = null;
		if (playback != null) {
			playback.stop();
		}
	}

	private void finish() {
		stopPlaying();
		this.active = false;
		this.movies = Collections.emptyList();
		if (Gdx.input.getInputProcessor() == this.skipInput) {
			Gdx.input.setInputProcessor(this.gameInput);
		}
		this.gameInput = null;
		disposeGraphics();
		final Runnable then = this.done;
		this.done = null;
		if (then != null) {
			then.run();
		}
	}

	/** Stops the movies at once, without going on to what was waiting for them; the game is closing. */
	public void dispose() {
		stopPlaying();
		this.active = false;
		this.done = null;
		disposeGraphics();
	}

	private static void clearScreen() {
		Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
	}

	private void draw(final MoviePicture picture, final Playback playback, final long now) {
		final GL20 gl = Gdx.gl;
		final boolean depthTest = gl.glIsEnabled(GL20.GL_DEPTH_TEST);
		final boolean cullFace = gl.glIsEnabled(GL20.GL_CULL_FACE);
		final boolean scissorTest = gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
		gl.glDisable(GL20.GL_DEPTH_TEST);
		gl.glDisable(GL20.GL_CULL_FACE);
		gl.glDisable(GL20.GL_SCISSOR_TEST);
		clearScreen();
		final int width = Gdx.graphics.getBackBufferWidth();
		final int height = Gdx.graphics.getBackBufferHeight();
		if (this.batch == null) {
			this.batch = new SpriteBatch();
		}
		this.batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
		if (picture != null) {
			this.picture.upload(picture);
		}
		if (this.picture.hasPicture()) {
			drawPicture(width, height);
		}
		final String caption = playback.captionAt(now);
		if (caption != null) {
			drawCaption(caption, playback.captions, playback.movie, width, height);
		}
		if (depthTest) {
			gl.glEnable(GL20.GL_DEPTH_TEST);
		}
		if (cullFace) {
			gl.glEnable(GL20.GL_CULL_FACE);
		}
		if (scissorTest) {
			gl.glEnable(GL20.GL_SCISSOR_TEST);
		}
		if (Gdx.gl30 != null) {
			Gdx.gl30.glBindVertexArray(WarsmashGdxGame.VAO);
		}
	}

	/** The picture keeps its shape, as large as the window allows, with black beside it. */
	private void drawPicture(final int width, final int height) {
		float w = width;
		float h = (width * (float) this.picture.getHeight()) / this.picture.getWidth();
		if (h > height) {
			h = height;
			w = (height * (float) this.picture.getWidth()) / this.picture.getHeight();
		}
		final SpriteBatch batch = this.batch;
		batch.disableBlending();
		batch.begin();
		this.picture.draw(batch, (width - w) / 2, (height - h) / 2, w, h, 0, 1, 1, 0);
		batch.end();
		batch.enableBlending();
	}

	private void drawCaption(final String caption, final MovieCaptions captions, final Movie movie, final int width,
			final int height) {
		final BitmapFont font = font(captions, movie, height);
		final float lineWidth = width * 0.9f;
		final SpriteBatch batch = this.batch;
		batch.begin();
		this.captionLayout.setText(font, caption, Color.WHITE, lineWidth, Align.center, true);
		font.draw(batch, this.captionLayout, (width - lineWidth) / 2, (height / 16f) + this.captionLayout.height);
		batch.end();
	}

	/**
	 * A font that has every letter of the captions (the game's own for the Latin ones, one of the language's or the
	 * computer's for the rest) at a twenty-fourth of the window's height, as the device player sizes its captions.
	 */
	private BitmapFont font(final MovieCaptions captions, final Movie movie, final int height) {
		final int size = Math.max(12, Math.round(height / 24f));
		if (captions != this.fontCaptions) {
			this.fontCaptions = captions;
			this.fontCharacters = characters(captions);
			this.captionFont = chooseFont(movie.getCaptionFonts(), captions);
			disposeFont();
		}
		final File fontFile = this.captionFont;
		if ((this.font != null) && (size == this.fontSize) && sameFile(fontFile, this.fontFile)) {
			return this.font;
		}
		disposeFont();
		this.fontSize = size;
		this.fontFile = fontFile;
		if (fontFile != null) {
			FreeTypeFontGenerator generator = null;
			try {
				generator = new FreeTypeFontGenerator(Gdx.files.absolute(fontFile.getAbsolutePath()));
				final FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
				parameter.size = size;
				parameter.characters = this.fontCharacters;
				parameter.borderWidth = Math.max(1, size / 16f);
				parameter.borderColor = new Color(0, 0, 0, 0.85f);
				parameter.shadowOffsetY = Math.max(1, size / 12);
				parameter.shadowColor = new Color(0, 0, 0, 0.6f);
				parameter.minFilter = TextureFilter.Linear;
				parameter.magFilter = TextureFilter.Linear;
				this.font = generator.generateFont(parameter);
			}
			catch (final GdxRuntimeException e) {
				Log.error("Unable to read the caption font " + fontFile + ": " + e);
			}
			finally {
				if (generator != null) {
					generator.dispose();
				}
			}
		}
		if (this.font == null) {
			this.font = new BitmapFont();
			this.font.getData().setScale(size / this.font.getLineHeight());
		}
		return this.font;
	}

	/** @return the first font with every letter of the captions, else the one with the most; null when none loads */
	private static File chooseFont(final List<File> fonts, final MovieCaptions captions) {
		final Set<Character> letters = new LinkedHashSet<>();
		for (final String text : captions.getTexts()) {
			for (final char c : text.toCharArray()) {
				if (c > ' ') {
					letters.add(c);
				}
			}
		}
		File best = null;
		int fewestMissing = Integer.MAX_VALUE;
		for (final File file : fonts) {
			FreeTypeFontGenerator generator = null;
			try {
				generator = new FreeTypeFontGenerator(Gdx.files.absolute(file.getAbsolutePath()));
				int missing = 0;
				for (final Character letter : letters) {
					if (!generator.hasGlyph(letter)) {
						missing++;
					}
				}
				if (missing < fewestMissing) {
					fewestMissing = missing;
					best = file;
				}
				if (missing == 0) {
					break;
				}
			}
			catch (final GdxRuntimeException e) {
				Log.warn("Unable to read the caption font " + file + ": " + e);
			}
			finally {
				if (generator != null) {
					generator.dispose();
				}
			}
		}
		if ((best != null) && (fewestMissing > 0)) {
			Log.warn("No caption font has all the letters of the captions; " + best + " lacks " + fewestMissing);
		}
		return best;
	}

	private static String characters(final MovieCaptions captions) {
		final Set<Character> set = new LinkedHashSet<>();
		for (final char c : FreeTypeFontGenerator.DEFAULT_CHARS.toCharArray()) {
			set.add(c);
		}
		for (final String text : captions.getTexts()) {
			for (final char c : text.toCharArray()) {
				if (c >= ' ') {
					set.add(c);
				}
			}
		}
		final StringBuilder characters = new StringBuilder(set.size());
		for (final Character c : set) {
			characters.append(c.charValue());
		}
		return characters.toString();
	}

	private static boolean sameFile(final File a, final File b) {
		return (a == null) ? (b == null) : a.equals(b);
	}

	private void disposeFont() {
		if (this.font != null) {
			this.font.dispose();
			this.font = null;
		}
	}

	private void disposeGraphics() {
		this.picture.dispose();
		disposeFont();
		this.fontFile = null;
		this.captionFont = null;
		this.fontCharacters = null;
		this.fontCaptions = null;
		if (this.batch != null) {
			this.batch.dispose();
			this.batch = null;
		}
	}

	private static void close(final java.io.Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			}
			catch (final IOException | RuntimeException e) {
				Log.error("Unable to close the movie: " + e);
			}
		}
	}

	/** One movie: the decoding thread, which keeps a few pictures ready, and the sound. */
	private static final class Playback implements Runnable {
		private final Movie movie;
		private final AviReader reader;
		private final MovieDecoders.VideoDecoder decoder;
		private final MovieCaptions captions;
		private final Sound sound;
		private final Thread thread;
		private final BlockingQueue<MoviePicture> ready = new ArrayBlockingQueue<>(PICTURES);
		private final BlockingQueue<MoviePicture> free = new ArrayBlockingQueue<>(PICTURES);
		private volatile boolean cancelled;
		private volatile boolean decoded;
		private long startedAt = -1;
		private long dropped;

		Playback(final Movie movie, final AviReader reader, final MovieDecoders.VideoDecoder decoder,
				final MovieCaptions captions, final Sound sound) {
			this.movie = movie;
			this.reader = reader;
			this.decoder = decoder;
			this.captions = captions;
			this.sound = sound;
			for (int i = 0; i < PICTURES; i++) {
				this.free.add(new MoviePicture());
			}
			this.thread = new Thread(this, "Movie");
			this.thread.setDaemon(true);
		}

		void start() {
			this.thread.start();
		}

		private long frameMillis(final long frame) {
			return this.reader.frameTimeMicros(frame) / 1000;
		}

		/**
		 * @return the newest picture due by now, which the caller gives back with {@link #recycle}; null when the
		 *         picture on screen stays. Pictures already late are passed over, so the picture keeps up with the
		 *         sound.
		 */
		MoviePicture pictureDue(final long now) {
			if (this.startedAt < 0) {
				final MoviePicture first = this.ready.peek();
				if (first == null) {
					return null;
				}
				this.startedAt = now - frameMillis(first.getFrame());
				if (this.sound != null) {
					this.sound.start();
				}
			}
			MoviePicture due = null;
			MoviePicture head;
			while (((head = this.ready.peek()) != null) && ((this.startedAt + frameMillis(head.getFrame())) <= now)) {
				this.ready.poll();
				if (due != null) {
					recycle(due);
					this.dropped++;
				}
				due = head;
			}
			return due;
		}

		void recycle(final MoviePicture picture) {
			this.free.offer(picture);
		}

		String captionAt(final long now) {
			return this.startedAt < 0 ? null : this.captions.at(now - this.startedAt);
		}

		/** @return true once the last picture has been shown and the sound has played out */
		boolean isOver() {
			return this.decoded && this.ready.isEmpty() && ((this.sound == null) || this.sound.isFinished()
					|| (this.startedAt < 0));
		}

		void stop() {
			this.cancelled = true;
			this.thread.interrupt();
			if (this.sound != null) {
				this.sound.stop();
			}
			try {
				this.thread.join(STOP_WAIT_MILLIS);
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			if (this.dropped > 0) {
				Log.info("Movie " + this.movie.getName() + ": " + this.dropped + " late frames not shown");
			}
		}

		@Override
		public void run() {
			try {
				decode();
			}
			catch (final IOException | RuntimeException e) {
				if (!this.cancelled) {
					Log.warn("The movie stopped: " + this.movie + ": " + e);
				}
			}
			catch (final InterruptedException e) {
				// skipped
			}
			finally {
				close(this.decoder);
				close(this.reader);
				this.decoded = true;
			}
		}

		private void decode() throws IOException, InterruptedException {
			final ByteBuffer data = ByteBuffer.allocateDirect(MAX_FRAME_BYTES);
			while (!this.cancelled) {
				data.clear();
				final long frame = this.reader.readFrame(data);
				final boolean end = frame < 0;
				if (end) {
					this.decoder.send(null, -1);
				}
				else if (data.position() == 0) {
					continue;
				}
				else {
					data.flip();
					this.decoder.send(data, frame);
				}
				while (!this.cancelled) {
					final MoviePicture picture = this.free.take();
					final int got = this.decoder.receive(picture);
					if (got == MovieDecoders.VideoDecoder.PICTURE) {
						this.ready.put(picture);
						continue;
					}
					this.free.put(picture);
					if ((got == MovieDecoders.VideoDecoder.END) || end) {
						return;
					}
					break;
				}
			}
		}
	}

	/** The movie's sound, fed to the game's audio from its own thread. */
	private static final class Sound implements Runnable {
		private final MovieDecoders.SoundDecoder decoder;
		private final AudioDevice device;
		private final Thread thread;
		private volatile boolean cancelled;
		private volatile boolean finished;
		private boolean started;

		Sound(final MovieDecoders.SoundDecoder decoder, final float volume) {
			this.decoder = decoder;
			this.device = Gdx.audio.newAudioDevice(decoder.getSampleRate(), false);
			this.device.setVolume(volume);
			this.thread = new Thread(this, "Movie sound");
			this.thread.setDaemon(true);
		}

		void start() {
			if (!this.started) {
				this.started = true;
				this.thread.start();
			}
		}

		boolean isFinished() {
			return this.finished;
		}

		@Override
		public void run() {
			final short[] samples = new short[SOUND_SAMPLES];
			try {
				int count;
				while (!this.cancelled && ((count = this.decoder.read(samples)) >= 0)) {
					if (count > 0) {
						this.device.writeSamples(samples, 0, count);
					}
				}
				if (!this.cancelled) {
					// What was written last is still in the device's buffers.
					Thread.sleep((this.device.getLatency() * 1000L) / this.decoder.getSampleRate());
				}
			}
			catch (final IOException | RuntimeException e) {
				if (!this.cancelled) {
					Log.warn("The movie sound stopped: " + e);
				}
			}
			catch (final InterruptedException e) {
				// skipped
			}
			finally {
				close(this.decoder);
				this.finished = true;
			}
		}

		/** Stops the sound; on the render thread, which made the device, as it belongs to the game's audio. */
		void stop() {
			this.cancelled = true;
			if (this.started) {
				this.thread.interrupt();
				try {
					this.thread.join(STOP_WAIT_MILLIS);
				}
				catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			else {
				close(this.decoder);
			}
			if (!this.thread.isAlive()) {
				this.device.dispose();
			}
			else {
				Log.warn("The movie sound did not stop in time");
			}
		}
	}
}
