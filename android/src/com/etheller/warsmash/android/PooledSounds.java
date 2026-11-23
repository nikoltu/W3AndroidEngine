package com.etheller.warsmash.android;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import com.badlogic.gdx.audio.Sound;

/**
 * The game's short sounds, on one SoundPool. A SoundPool decodes a file in the background after it is loaded and does
 * not play it before that, so a sound played right after it was made (a unit's first answer, a script's sound started
 * as it is created) was not heard at all. Here such a play starts as soon as the decoding ends.
 */
final class PooledSounds {
	private static final int MAX_STREAMS = 16;
	// Heard later than this, an answer or a click no longer goes with what caused it; loops start whenever they can.
	private static final long MAX_LATE_START_MILLIS = 1000;
	// Plays of a sound kept to be stopped or changed later; a loop is always kept until it is stopped.
	private static final int MAX_TRACKED_PLAYS = 8;
	// A SoundPool keeps at most 1 MB of a sound once decoded and cuts the rest off, so longer sounds are streamed.
	private static final long MAX_POOLED_BYTES = 1024 * 1024;
	private static final String TAG = "Warsmash";

	private static PooledSounds instance;
	// Set while the game is off the screen, which a sound that finishes decoding then must respect.
	private static boolean inBackground;

	private final SoundPool pool;
	private final SparseArray<PooledSound> loading = new SparseArray<>();
	private final SparseArray<Long> pooledBytes = new SparseArray<>();
	private long totalPooledBytes;
	private int streamedSounds;
	private long nextPlayId = 1;

	static synchronized PooledSounds get() {
		if (instance == null) {
			instance = new PooledSounds();
		}
		return instance;
	}

	/** Pauses every sound with the activity, as the backend does with its own. */
	static synchronized void pauseAll() {
		inBackground = true;
		if (instance != null) {
			instance.pool.autoPause();
		}
		StreamedSound.pauseAll();
	}

	static synchronized void resumeAll() {
		inBackground = false;
		if (instance != null) {
			instance.pool.autoResume();
		}
		StreamedSound.resumeAll();
	}

	private PooledSounds() {
		this.pool = new SoundPool.Builder().setMaxStreams(MAX_STREAMS)
				.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
						.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
				.build();
		this.pool.setOnLoadCompleteListener((soundPool, sampleId, status) -> loaded(sampleId, status == 0));
	}

	Sound newSound(final String path) {
		final SoundFileFormat format = SoundFileFormat.read(new File(path));
		// More than two channels are not played by a SoundPool, and not decoded by every device's own decoder.
		if ((format != null) && ((format.channels > 2) || (format.decodedBytes() > MAX_POOLED_BYTES))) {
			synchronized (this) {
				this.streamedSounds++;
			}
			return new StreamedSound(this, path, format.seconds);
		}
		synchronized (this) {
			final int sampleId = this.pool.load(path, 1);
			final PooledSound sound = new PooledSound(this, sampleId);
			if (sampleId == 0) {
				sound.loaded(false);
			}
			else {
				this.loading.put(sampleId, sound);
				final long bytes = format == null ? 0 : format.decodedBytes();
				this.pooledBytes.put(sampleId, bytes);
				this.totalPooledBytes += bytes;
			}
			return sound;
		}
	}

	/** Waits, at most this long, for the sounds made so far to be decoded. */
	boolean awaitLoading(final long maxMillis) {
		final long end = SystemClock.uptimeMillis() + maxMillis;
		synchronized (this) {
			long left;
			while ((this.loading.size() > 0) && ((left = end - SystemClock.uptimeMillis()) > 0)) {
				try {
					wait(left);
				}
				catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			return this.loading.size() == 0;
		}
	}

	/** Waits until every sound made so far is decoded, or the time is up. */
	void finishLoading(final long maxMillis) {
		final long end = SystemClock.uptimeMillis() + maxMillis;
		synchronized (this) {
			long left;
			while ((this.loading.size() > 0) && ((left = end - SystemClock.uptimeMillis()) > 0)) {
				try {
					wait(left);
				}
				catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			Log.i(TAG, "Sound pool: " + this.pooledBytes.size() + " sounds, "
					+ ((this.totalPooledBytes + 524288) / 1048576) + " MB decoded, " + this.loading.size()
					+ " still decoding; " + this.streamedSounds + " streamed");
		}
	}

	private void loaded(final int sampleId, final boolean ok) {
		final PooledSound sound;
		synchronized (this) {
			sound = this.loading.get(sampleId);
			this.loading.remove(sampleId);
			notifyAll();
		}
		if (sound != null) {
			sound.loaded(ok);
		}
		if (!ok) {
			// A sample that did not decode still holds its slot in the pool and its share of the bytes counted.
			unload(sampleId);
		}
	}

	static synchronized boolean isInBackground() {
		return inBackground;
	}

	synchronized long nextPlayId() {
		return this.nextPlayId++;
	}

	synchronized void unload(final int sampleId) {
		this.loading.remove(sampleId);
		final Long bytes = this.pooledBytes.get(sampleId);
		if (bytes != null) {
			this.totalPooledBytes -= bytes;
			this.pooledBytes.remove(sampleId);
		}
		notifyAll();
		this.pool.unload(sampleId);
	}

	private static final class Play {
		private int streamId;
		private float volume;
		private float pan;
		private float pitch;
		private boolean looping;
		private boolean paused;
		private final long askedAt = SystemClock.uptimeMillis();
	}

	private static final class PooledSound implements Sound {
		private final PooledSounds owner;
		private final SoundPool pool;
		private final int sampleId;
		private final Map<Long, Play> plays = new LinkedHashMap<>();
		private boolean ready;
		private boolean gone;

		private PooledSound(final PooledSounds owner, final int sampleId) {
			this.owner = owner;
			this.pool = owner.pool;
			this.sampleId = sampleId;
		}

		private synchronized void loaded(final boolean ok) {
			if (this.gone || this.ready) {
				return;
			}
			if (!ok) {
				this.gone = true;
				this.plays.clear();
				return;
			}
			this.ready = true;
			final long now = SystemClock.uptimeMillis();
			final Iterator<Play> iterator = this.plays.values().iterator();
			while (iterator.hasNext()) {
				final Play play = iterator.next();
				if (!play.looping && ((now - play.askedAt) > MAX_LATE_START_MILLIS)) {
					iterator.remove();
				}
				else if (!startStream(play)) {
					iterator.remove();
				}
			}
		}

		private boolean startStream(final Play play) {
			play.streamId = this.pool.play(this.sampleId, leftVolume(play), rightVolume(play), 1,
					play.looping ? -1 : 0, play.pitch);
			if (play.streamId == 0) {
				return false;
			}
			if (play.paused) {
				this.pool.pause(play.streamId);
			}
			else if (isInBackground()) {
				// autoPause only holds what played when it was called, and autoResume lets go of the same.
				this.pool.autoPause();
			}
			return true;
		}

		private static float leftVolume(final Play play) {
			return play.pan > 0 ? play.volume * (1 - play.pan) : play.volume;
		}

		private static float rightVolume(final Play play) {
			return play.pan < 0 ? play.volume * (1 + play.pan) : play.volume;
		}

		private synchronized long start(final float volume, final float pitch, final float pan, final boolean looping) {
			if (this.gone) {
				return -1;
			}
			final Play play = new Play();
			play.volume = volume;
			play.pitch = pitch;
			play.pan = pan;
			play.looping = looping;
			if (this.ready && !startStream(play)) {
				return -1;
			}
			final long id = this.owner.nextPlayId();
			this.plays.put(id, play);
			if (this.plays.size() > MAX_TRACKED_PLAYS) {
				final Iterator<Play> iterator = this.plays.values().iterator();
				while (iterator.hasNext()) {
					if (!iterator.next().looping) {
						iterator.remove();
						break;
					}
				}
			}
			return id;
		}

		@Override
		public long play() {
			return start(1, 1, 0, false);
		}

		@Override
		public long play(final float volume) {
			return start(volume, 1, 0, false);
		}

		@Override
		public long play(final float volume, final float pitch, final float pan) {
			return start(volume, pitch, pan, false);
		}

		@Override
		public long loop() {
			return start(1, 1, 0, true);
		}

		@Override
		public long loop(final float volume) {
			return start(volume, 1, 0, true);
		}

		@Override
		public long loop(final float volume, final float pitch, final float pan) {
			return start(volume, pitch, pan, true);
		}

		@Override
		public synchronized void stop() {
			for (final Play play : this.plays.values()) {
				if (play.streamId != 0) {
					this.pool.stop(play.streamId);
				}
			}
			this.plays.clear();
		}

		@Override
		public synchronized void pause() {
			for (final Play play : this.plays.values()) {
				play.paused = true;
				if (play.streamId != 0) {
					this.pool.pause(play.streamId);
				}
			}
		}

		@Override
		public synchronized void resume() {
			for (final Play play : this.plays.values()) {
				play.paused = false;
				if (play.streamId != 0) {
					this.pool.resume(play.streamId);
				}
			}
		}

		@Override
		public void dispose() {
			synchronized (this) {
				if (this.gone && !this.ready) {
					this.plays.clear();
					return;
				}
				stop();
				this.gone = true;
				this.ready = false;
			}
			this.owner.unload(this.sampleId);
		}

		@Override
		public synchronized void stop(final long soundId) {
			final Play play = this.plays.remove(soundId);
			if ((play != null) && (play.streamId != 0)) {
				this.pool.stop(play.streamId);
			}
		}

		@Override
		public synchronized void pause(final long soundId) {
			final Play play = this.plays.get(soundId);
			if (play != null) {
				play.paused = true;
				if (play.streamId != 0) {
					this.pool.pause(play.streamId);
				}
			}
		}

		@Override
		public synchronized void resume(final long soundId) {
			final Play play = this.plays.get(soundId);
			if (play != null) {
				play.paused = false;
				if (play.streamId != 0) {
					this.pool.resume(play.streamId);
				}
			}
		}

		@Override
		public synchronized void setLooping(final long soundId, final boolean looping) {
			final Play play = this.plays.get(soundId);
			if (play != null) {
				play.looping = looping;
				if (play.streamId != 0) {
					this.pool.setLoop(play.streamId, looping ? -1 : 0);
				}
			}
		}

		@Override
		public synchronized void setPitch(final long soundId, final float pitch) {
			final Play play = this.plays.get(soundId);
			if (play != null) {
				play.pitch = pitch;
				if (play.streamId != 0) {
					this.pool.setRate(play.streamId, pitch);
				}
			}
		}

		@Override
		public synchronized void setVolume(final long soundId, final float volume) {
			setPan(soundId, 0, volume);
		}

		@Override
		public synchronized void setPan(final long soundId, final float pan, final float volume) {
			final Play play = this.plays.get(soundId);
			if (play != null) {
				play.pan = pan;
				play.volume = volume;
				if (play.streamId != 0) {
					this.pool.setVolume(play.streamId, leftVolume(play), rightVolume(play));
				}
			}
		}
	}
}
