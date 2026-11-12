package com.etheller.warsmash.android;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.etheller.warsmash.movie.AviReader;
import com.etheller.warsmash.movie.Movie;
import com.etheller.warsmash.movie.MovieCaptions;

/**
 * Plays the game's movies full screen, one after another: the VP9 picture read out of its AVI file into the device's
 * decoder, the sound beside it, and the captions under the picture. A tap or Back skips the movie playing; leaving the
 * screen stops it, and coming back goes on from where it stopped.
 */
public final class MovieActivity extends Activity implements SurfaceHolder.Callback {
	private static final String TAG = "Warsmash";
	private static final String EXTRA_VIDEOS = "videos";
	private static final String EXTRA_AUDIOS = "audios";
	private static final String EXTRA_CAPTIONS = "captions";
	private static final String VP9 = MediaFormat.MIMETYPE_VIDEO_VP9;
	private static final int MAX_FRAME_BYTES = 2 << 20;
	private static final long CAPTION_MILLIS = 100;

	static Intent intent(final Context context, final List<Movie> movies) {
		final ArrayList<String> videos = new ArrayList<>();
		final ArrayList<String> audios = new ArrayList<>();
		final ArrayList<String> captions = new ArrayList<>();
		for (final Movie movie : movies) {
			videos.add(movie.getVideo().getAbsolutePath());
			audios.add(movie.getAudio() == null ? "" : movie.getAudio().getAbsolutePath());
			captions.add(movie.getCaptions() == null ? "" : movie.getCaptions().getAbsolutePath());
		}
		final Intent intent = new Intent(context, MovieActivity.class);
		intent.putStringArrayListExtra(EXTRA_VIDEOS, videos);
		intent.putStringArrayListExtra(EXTRA_AUDIOS, audios);
		intent.putStringArrayListExtra(EXTRA_CAPTIONS, captions);
		return intent;
	}

	private List<String> videos;
	private List<String> audios;
	private List<String> captionFiles;
	private SurfaceView picture;
	private TextView captionView;
	private Surface surface;
	private int next;
	private Playback playing;
	/** Where the movie stopped when the screen was left, to go on from there; -1 when none did. */
	private int resumeMovie = -1;
	private long resumeMicros;
	private float volume;
	private boolean subtitles;
	private boolean done;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		BackCallbacks.register(this, this::skip);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		this.videos = list(EXTRA_VIDEOS);
		this.audios = list(EXTRA_AUDIOS);
		this.captionFiles = list(EXTRA_CAPTIONS);
		final EngineSettings settings = new EngineSettings(this);
		this.volume = settings.flag("sound", true)
				? Math.max(0, Math.min(100, settings.number("soundVolume", 100))) / 100f
				: 0;
		this.subtitles = settings.flag("subtitles", true);
		final FrameLayout root = new FrameLayout(this);
		root.setBackgroundColor(Color.BLACK);
		this.picture = new SurfaceView(this);
		this.picture.getHolder().addCallback(this);
		root.addView(this.picture, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
		this.captionView = new TextView(this);
		this.captionView.setTextColor(Color.WHITE);
		this.captionView.setGravity(Gravity.CENTER_HORIZONTAL);
		this.captionView.setShadowLayer(6, 0, 2, Color.BLACK);
		this.captionView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDisplayMetrics().heightPixels / 24f);
		final FrameLayout.LayoutParams captionLayout = new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
				Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
		captionLayout.bottomMargin = getResources().getDisplayMetrics().heightPixels / 16;
		root.addView(this.captionView, captionLayout);
		root.setOnClickListener(v -> skip());
		setContentView(root);
		hideSystemBars();
		if (this.videos.isEmpty()) {
			finishPlaying();
		}
	}

	private List<String> list(final String extra) {
		final List<String> values = getIntent().getStringArrayListExtra(extra);
		return values != null ? values : new ArrayList<>();
	}

	@SuppressWarnings("deprecation")
	private void hideSystemBars() {
		getWindow().getDecorView()
				.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
						| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
						| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
	}

	@Override
	public void surfaceCreated(final SurfaceHolder holder) {
		this.surface = holder.getSurface();
		if (this.playing == null) {
			playNext();
		}
	}

	@Override
	public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
	}

	@Override
	public void surfaceDestroyed(final SurfaceHolder holder) {
		final Playback playback = this.playing;
		stopPlaying();
		if (playback != null) {
			// The decoder must let go of the surface before it is gone.
			playback.join();
		}
		this.surface = null;
	}

	@Override
	public void onBackPressed() {
		skip();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (!isChangingConfigurations()) {
			final Playback playback = this.playing;
			if (playback != null) {
				this.resumeMovie = this.next - 1;
				this.resumeMicros = playback.positionMicros();
				stopPlaying();
				// The decoder must let go of the surface before the movie starts on it again.
				playback.join();
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		hideSystemBars();
		// A surface kept while the screen was away is not created again.
		if ((this.surface != null) && (this.playing == null)) {
			playNext();
		}
	}

	@Override
	protected void onDestroy() {
		stopPlaying();
		super.onDestroy();
	}

	private void skip() {
		if (this.playing != null) {
			Log.i(TAG, "Movie skipped: " + this.playing.video.getName());
			stopPlaying();
			playNext();
		}
	}

	private void stopPlaying() {
		final Playback playback = this.playing;
		this.playing = null;
		if (playback != null) {
			playback.cancel();
		}
		this.captionView.setText("");
	}

	private void playNext() {
		if (this.done) {
			return;
		}
		if (this.next >= this.videos.size()) {
			finishPlaying();
			return;
		}
		if (this.surface == null) {
			return;
		}
		long startMicros = 0;
		if (this.resumeMovie >= 0) {
			this.next = this.resumeMovie;
			startMicros = this.resumeMicros;
			this.resumeMovie = -1;
		}
		final int index = this.next++;
		final File video = new File(this.videos.get(index));
		final AviReader reader;
		try {
			reader = new AviReader(video);
		}
		catch (final IOException e) {
			Log.w(TAG, "Unable to read the movie " + video, e);
			playNext();
			return;
		}
		fit(reader.getWidth(), reader.getHeight());
		final String audio = this.audios.size() > index ? this.audios.get(index) : "";
		final String captions = this.captionFiles.size() > index ? this.captionFiles.get(index) : "";
		this.playing = new Playback(video, reader, audio.isEmpty() ? null : new File(audio),
				MovieCaptions.read(captions.isEmpty() || !this.subtitles ? null : new File(captions)), startMicros);
		this.playing.start();
	}

	/** The picture keeps its shape, as wide as the screen allows, with black above and below. */
	private void fit(final int width, final int height) {
		final View root = (View) this.picture.getParent();
		final int screenW = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
		final int screenH = root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels;
		if ((width <= 0) || (height <= 0)) {
			return;
		}
		int w = screenW;
		int h = Math.round(((float) screenW * height) / width);
		if (h > screenH) {
			h = screenH;
			w = Math.round(((float) screenH * width) / height);
		}
		final FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) this.picture.getLayoutParams();
		if ((layout.width != w) || (layout.height != h)) {
			layout.width = w;
			layout.height = h;
			this.picture.setLayoutParams(layout);
		}
	}

	private void movieEnded(final Playback playback) {
		if (this.playing == playback) {
			this.playing = null;
			this.captionView.setText("");
			playNext();
		}
	}

	private void finishPlaying() {
		if (this.done) {
			return;
		}
		this.done = true;
		stopPlaying();
		setResult(RESULT_OK);
		finish();
		overridePendingTransition(0, 0);
	}

	/**
	 * Whether a VP9 frame starts with a key frame, from the first byte of its header: the frame marker, the profile,
	 * show_existing_frame and frame_type (0 for a key frame).
	 */
	private static boolean isVp9KeyFrame(final int firstByte) {
		if ((firstByte >>> 6) != 2) {
			return false;
		}
		final int profile = ((firstByte >>> 5) & 1) | (((firstByte >>> 4) & 1) << 1);
		final int shift = profile == 3 ? 1 : 0;
		final boolean showExisting = ((firstByte >>> (3 - shift)) & 1) != 0;
		return !showExisting && (((firstByte >>> (2 - shift)) & 1) == 0);
	}

	/** One movie: the decoding thread, the sound and the captions on the screen's thread. */
	private final class Playback implements Runnable {
		private final File video;
		private final AviReader reader;
		private final File audio;
		private final MovieCaptions captions;
		/** The time in the movie to show first; the frames before it are decoded from a key frame, not shown. */
		private final long startMicros;
		private final Thread thread;
		private MediaPlayer sound;
		private volatile boolean cancelled;
		private volatile long startedAt = -1;

		Playback(final File video, final AviReader reader, final File audio, final MovieCaptions captions,
				final long startMicros) {
			this.video = video;
			this.reader = reader;
			this.audio = audio;
			this.captions = captions;
			this.startMicros = startMicros;
			this.thread = new Thread(this, "Movie");
		}

		void start() {
			this.sound = prepareSound();
			this.thread.start();
			if (this.captions.size() > 0) {
				MovieActivity.this.captionView.postDelayed(this::showCaption, CAPTION_MILLIS);
			}
		}

		private MediaPlayer prepareSound() {
			if (this.audio == null) {
				return null;
			}
			final MediaPlayer player = new MediaPlayer();
			try {
				player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
						.setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
				player.setDataSource(this.audio.getAbsolutePath());
				player.setVolume(MovieActivity.this.volume, MovieActivity.this.volume);
				player.prepare();
				if (this.startMicros > 0) {
					player.seekTo((int) (this.startMicros / 1000));
				}
				return player;
			}
			catch (final IOException | RuntimeException e) {
				Log.w(TAG, "The movie sound cannot play: " + this.audio, e);
				player.release();
				return null;
			}
		}

		private void showCaption() {
			if (this.cancelled || (MovieActivity.this.playing != this)) {
				return;
			}
			final long started = this.startedAt;
			final String text = started < 0 ? null : this.captions.at(SystemClock.elapsedRealtime() - started);
			final CharSequence shown = MovieActivity.this.captionView.getText();
			if (!(text == null ? "" : text).contentEquals(shown)) {
				MovieActivity.this.captionView.setText(text == null ? "" : text);
			}
			MovieActivity.this.captionView.postDelayed(this::showCaption, CAPTION_MILLIS);
		}

		/** The time in the movie on the screen now. */
		long positionMicros() {
			final long started = this.startedAt;
			return started < 0 ? this.startMicros : (SystemClock.elapsedRealtime() - started) * 1000;
		}

		void cancel() {
			this.cancelled = true;
			this.thread.interrupt();
		}

		void join() {
			try {
				this.thread.join(1000);
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		@Override
		public void run() {
			MediaCodec codec = null;
			try {
				codec = decoder();
				if (codec != null) {
					decode(codec);
				}
			}
			catch (final IOException | RuntimeException e) {
				Log.w(TAG, "The movie stopped: " + this.video, e);
			}
			catch (final InterruptedException e) {
				// skipped
			}
			finally {
				if (codec != null) {
					try {
						codec.stop();
					}
					catch (final RuntimeException e) {
						Log.w(TAG, "Unable to stop the movie decoder", e);
					}
					codec.release();
				}
				try {
					this.reader.close();
				}
				catch (final IOException e) {
					Log.w(TAG, "Unable to close the movie", e);
				}
				final MediaPlayer player = this.sound;
				this.sound = null;
				if (player != null) {
					try {
						player.stop();
					}
					catch (final RuntimeException e) {
						Log.w(TAG, "Unable to stop the movie sound", e);
					}
					player.release();
				}
				if (!this.cancelled) {
					runOnUiThread(() -> movieEnded(this));
				}
			}
		}

		private MediaCodec decoder() throws IOException {
			if (!"VP90".equalsIgnoreCase(this.reader.getCodec())) {
				Log.w(TAG, "The movie " + this.video + " is " + this.reader.getCodec() + ", not VP9");
				return null;
			}
			final MediaFormat format = MediaFormat.createVideoFormat(VP9, this.reader.getWidth(),
					this.reader.getHeight());
			format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_FRAME_BYTES);
			final String name = new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format);
			if (name == null) {
				Log.w(TAG, "No VP9 decoder for " + this.reader.getWidth() + "x" + this.reader.getHeight());
				return null;
			}
			final Surface target = MovieActivity.this.surface;
			if ((target == null) || this.cancelled) {
				return null;
			}
			final MediaCodec codec = MediaCodec.createByCodecName(name);
			codec.configure(format, target, null, 0);
			codec.start();
			Log.i(TAG, "Movie " + this.video.getName() + " " + this.reader.getWidth() + "x" + this.reader.getHeight()
					+ " on " + name);
			return codec;
		}

		private void decode(final MediaCodec codec) throws IOException, InterruptedException {
			final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
			final long frameMicros = Math.max(1, this.reader.frameTimeMicros(1));
			if (this.startMicros > 0) {
				this.reader.seekKeyFrame(this.startMicros / frameMicros, MovieActivity::isVp9KeyFrame);
			}
			boolean inputDone = false;
			long dropped = 0;
			while (!this.cancelled) {
				if (!inputDone) {
					final int in = codec.dequeueInputBuffer(5000);
					if (in >= 0) {
						final ByteBuffer buffer = codec.getInputBuffer(in);
						buffer.clear();
						final long frame = this.reader.readFrame(buffer);
						if (frame < 0) {
							codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
							inputDone = true;
						}
						else {
							codec.queueInputBuffer(in, 0, buffer.position(), this.reader.frameTimeMicros(frame), 0);
						}
					}
				}
				final int out = codec.dequeueOutputBuffer(info, 5000);
				if (out < 0) {
					continue;
				}
				final boolean last = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
				if ((info.size <= 0) || (info.presentationTimeUs < this.startMicros)) {
					codec.releaseOutputBuffer(out, false);
				}
				else {
					if (this.startedAt < 0) {
						this.startedAt = SystemClock.elapsedRealtime() - (info.presentationTimeUs / 1000);
						if (this.sound != null) {
							this.sound.start();
						}
					}
					final long dueMillis = this.startedAt + (info.presentationTimeUs / 1000);
					final long wait = dueMillis - SystemClock.elapsedRealtime();
					if (wait > 0) {
						Thread.sleep(wait);
					}
					// A frame more than two frames late is not shown, so the picture catches up with the sound.
					final boolean late = -wait > ((2 * frameMicros) / 1000);
					if (late) {
						dropped++;
					}
					codec.releaseOutputBuffer(out, !late);
				}
				if (last) {
					break;
				}
			}
			if (dropped > 0) {
				Log.i(TAG, "Movie " + this.video.getName() + ": " + dropped + " late frames not shown");
			}
			// The picture is done; the sound may run on for a moment.
			final MediaPlayer player = this.sound;
			while (!this.cancelled && (player != null) && player.isPlaying()) {
				Thread.sleep(50);
			}
		}
	}
}
