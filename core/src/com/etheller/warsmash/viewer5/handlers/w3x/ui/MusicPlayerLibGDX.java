package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.audio.Music;
import com.etheller.warsmash.util.SessionResources;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.DataSourceFileHandle;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;

public class MusicPlayerLibGDX implements MusicPlayer {
	private static final float GAME_MSECS_DIVISOR = 1000f;
	private static final long RETRY_NANOS = 1500000000L;
	private String[] currentPaths;
	// Only the track that plays is open: every open track holds a decoder (a MediaPlayer on Android) until disposed.
	private Music currentMusic;
	private String currentMusicPath;
	private int currentMusicIndex;
	private boolean currentMusicRandomizeIndex;
	// The map music starts with the game, as soon as the map script has named it.
	private boolean currentMusicActive = true;
	// A track that does not start (or stops at once) is not retried every frame: the next try waits, each one
	// moves on in the list, and a list where nothing plays goes quiet.
	private long nextStartNanos;
	private boolean currentMusicStarted;
	private int failedStarts;
	private final DataSource dataSource;
	private final DataTable musicSLK;
	private float volume = 1.0f;
	private String defaultMusicField;
	private boolean defaultMusicRandom;
	private int defaultMusicIndex;
	private boolean pausedByFocus;
	private boolean playingBeforeFocusLoss;
	private boolean playOnce;
	// Asking a player whether it plays is a call into the media framework that now and then takes milliseconds;
	// a playing track is checked twice a second, not every frame.
	private static final long PLAYING_CHECK_NANOS = 500_000_000L;
	private long nextPlayingCheckNanos;
	private boolean playingAtCheck;

	public MusicPlayerLibGDX(DataSource dataSource, DataTable musicSLK) {
		this.dataSource = dataSource;
		this.musicSLK = musicSLK;
	}

	@Override
	public void setPausedByFocus(final boolean paused) {
		if (paused == this.pausedByFocus) {
			return;
		}
		this.pausedByFocus = paused;
		try {
			if (paused) {
				this.playingBeforeFocusLoss = (this.currentMusic != null) && this.currentMusic.isPlaying();
				if (this.playingBeforeFocusLoss) {
					this.currentMusic.pause();
				}
			}
			else if (this.playingBeforeFocusLoss && (this.currentMusic != null)) {
				this.playingBeforeFocusLoss = false;
				this.currentMusic.play();
			}
		}
		catch (final RuntimeException exc) {
			Log.error(exc);
		}
	}

	@Override
	public void update() {
		// A track held for the lost focus is not a finished one: the next must not start over it.
		if (this.pausedByFocus) {
			return;
		}
		if (this.currentMusicActive) {
			if (this.currentPaths == null) {
				playDefaultMusic();
			}
			if ((this.currentPaths == null) || (this.currentPaths.length == 0)
					|| (this.failedStarts >= Math.max(2, this.currentPaths.length))) {
				return;
			}
			if (this.currentMusic != null) {
				final long now = System.nanoTime();
				if (now - this.nextPlayingCheckNanos >= 0) {
					this.nextPlayingCheckNanos = now + PLAYING_CHECK_NANOS;
					this.playingAtCheck = this.currentMusic.isPlaying();
				}
				if (this.playingAtCheck) {
					this.currentMusicStarted = true;
					this.failedStarts = 0;
					return;
				}
			}
			if (this.playOnce && this.currentMusicStarted) {
				playDefaultMusic();
				return;
			}
			if (System.nanoTime() - this.nextStartNanos < 0) {
				return;
			}
			if (!this.currentMusicStarted) {
				this.failedStarts++;
				if (this.failedStarts >= Math.max(2, this.currentPaths.length)) {
					closeCurrentMusic();
					return;
				}
			}
			if (this.currentMusicRandomizeIndex) {
				startTrack((int) (Math.random() * this.currentPaths.length), 0);
			}
			else {
				startTrack((this.currentMusicIndex + 1) % this.currentPaths.length, 0);
			}
		}
	}

	@Override
	public void setMusicPosition(int positionMSecs) {
		if (this.currentMusic != null) {
			this.currentMusic.setPosition(positionMSecs / GAME_MSECS_DIVISOR);
		}
	}

	@Override
	public void stopMusic() {
		if (this.currentPaths != null) {
			if (this.currentMusic != null) {
				this.currentMusic.pause();
			}
			this.currentMusicActive = false;
		}
	}

	@Override
	public void setVolume(int volume) {
		this.volume = volume / 127f;
		optionsChanged();
	}

	@Override
	public void optionsChanged() {
		if (this.currentMusic != null) {
			this.currentMusic.setVolume(this.volume * CSoundMixer.audible().musicScale());
		}
	}

	@Override
	public void resumeMusic() {
		this.currentMusicActive = true;
	}

	@Override
	public Music setDefaultMusic(String musicField, boolean random, int index) {
		this.defaultMusicField = musicField;
		this.defaultMusicRandom = random;
		this.defaultMusicIndex = index;
		return null;
	}

	@Override
	public Music playDefaultMusic() {
		if (this.defaultMusicField == null) {
			return null;
		}
		return playMusicEx(this.defaultMusicField, this.defaultMusicRandom, this.defaultMusicIndex, 0, -1);
	}

	@Override
	public void playMusicOnce(final String musicField) {
		playMusicEx(musicField, false, 0, 0, -1);
		this.playOnce = true;
	}

	@Override
	public Music playMusicEx(String musicField, boolean random, int index, int fromMSecs, int fadeInMSecs) {
		this.playOnce = false;
		stopMusic();
		if (musicField == null) {
			return null;
		}
		this.currentMusicActive = true;

		final String[] semicolonMusics = musicField.split(";");
		final List<String> musicPaths = new ArrayList<>();
		for (String musicPath : semicolonMusics) {
			// dumb support for comma as well as semicolon, I wonder if we can
			// clean this up, simplify?
			if (this.musicSLK.get(musicPath) != null) {
				musicPath = this.musicSLK.get(musicPath).getField("FileNames");
			}
			final String[] moreSplitMusics = musicPath.split(",");
			for (final String finalSplitPath : moreSplitMusics) {
				final String path = findMusicFile(this.dataSource, finalSplitPath);
				if (path != null) {
					musicPaths.add(path);
				}
			}
		}
		this.currentPaths = musicPaths.toArray(new String[musicPaths.size()]);
		this.currentMusicRandomizeIndex = random;
		this.failedStarts = 0;
		if (this.currentPaths.length == 0) {
			closeCurrentMusic();
			return null;
		}
		if (random) {
			index = (int) (Math.random() * this.currentPaths.length);
		}
		index = Math.min(index, this.currentPaths.length - 1);
		return startTrack(index, fromMSecs);
	}

	/**
	 * Plays one track of the current list from the start (or the given position). The same track again reuses its
	 * player; any other track closes it.
	 */
	private Music startTrack(final int index, final int fromMSecs) {
		this.currentMusicIndex = index;
		this.currentMusicStarted = false;
		this.playingAtCheck = false;
		this.nextStartNanos = System.nanoTime() + RETRY_NANOS;
		final String path = this.currentPaths[index];
		if ((this.currentMusic != null) && path.equals(this.currentMusicPath)) {
			this.currentMusic.stop();
		}
		else {
			closeCurrentMusic();
			try {
				this.currentMusic = SessionResources
						.track(Extensions.audio.newMusic(new DataSourceFileHandle(this.dataSource, path)));
			}
			catch (final RuntimeException exc) {
				Log.error(exc);
				return null;
			}
			this.currentMusicPath = path;
			this.currentMusic.setVolume(this.volume * CSoundMixer.audible().musicScale());
		}
		try {
			this.currentMusic.play();
			if (fromMSecs != 0) {
				this.currentMusic.setPosition(fromMSecs / GAME_MSECS_DIVISOR);
			}
		}
		catch (final RuntimeException exc) {
			Log.error(exc);
			closeCurrentMusic();
			return null;
		}
		return this.currentMusic;
	}

	private void closeCurrentMusic() {
		if (this.currentMusic != null) {
			// The session disposes what it tracked again at its end, which a disposed track ignores.
			this.currentMusic.dispose();
			this.currentMusic = null;
			this.playingAtCheck = false;
			this.currentMusicPath = null;
		}
	}

	/**
	 * The music tables name the music of the newest graphics pack (FLAC); the classic data holds the same tracks as
	 * MP3 and the HD pack as OGG.
	 *
	 * @return the path of the track as it exists in the data, or null
	 */
	public static String findMusicFile(final DataSource dataSource, final String path) {
		if (dataSource.has(path)) {
			return path;
		}
		final int dot = path.lastIndexOf('.');
		if (dot >= 0) {
			for (final String extension : new String[] { ".mp3", ".ogg", ".flac" }) {
				final String candidate = path.substring(0, dot) + extension;
				if (dataSource.has(candidate)) {
					return candidate;
				}
			}
		}
		return null;
	}
}
