package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.util.SessionResources;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.DataSourceFileHandle;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.viewer5.AudioBufferSource;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.AudioPanner;
import com.etheller.warsmash.viewer5.SoundLoops;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.SoundFileDuration;

public final class UnitSound {
	private static final UnitSound SILENT = new UnitSound(null, 0, 0, 0, 0, 0, 0, false);

	// Decoding every sound of a unit type when its first unit appeared held up the start of a game for seconds, so a
	// sound is decoded when it is first played, unless a map's loading decoded it ahead (preload).
	private final DataSource dataSource;
	private final List<String> soundPaths = new ArrayList<>();
	private final List<Sound> sounds = new ArrayList<>();
	// Finding the file a path plays is a lookup in every data source, so it is done once for a prefetch and its play.
	private final List<FileHandle> handles = new ArrayList<>();
	private final List<Float> durations = new ArrayList<>();
	// A sound device has a few dozen voices, which this many plays of one file would not leave to the rest.
	private static final int MAX_SIMULTANEOUS_PLAYS = 6;
	private final List<PlayLimit> limits = new ArrayList<>();
	// The plays that wait for the sound of a file to be decoded, by file index.
	private final Map<Integer, List<Runnable>> waitingPlays = new HashMap<>();
	// The file names as the label has them: a duration is read from the data without graphics packages, so scripts
	// wait the same time with every graphics set.
	private final List<String> labelPaths = new ArrayList<>();
	// Finding the files of every sound of a unit type took most of the frame its first unit appeared in (dozens of
	// lookups in every data source per type), so the files are found when the sound is first asked for.
	private String[] unresolvedFiles;
	private final float volume;
	private final float pitch;
	private final float pitchVariance;
	private final float minDistance;
	private final float maxDistance;
	private final float distanceCutoff;
	private final boolean looping;
	private int channel;
	private boolean wants3D = true;

	private Sound lastPlayedSound;
	private int lastPlayedIndex;

	public static UnitSound create(final DataSource dataSource, final DataTable unitAckSounds, final String soundName,
			final String soundType) {
		final Element row = unitAckSounds.get(soundName + soundType);
		if (row == null) {
			return SILENT;
		}
		final String fileNames = row.hasFieldValue("FileNames") ? row.getField("FileNames") : "";
		String directoryBase = row.hasFieldValue("DirectoryBase") ? row.getField("DirectoryBase") : "";
		if ((directoryBase.length() > 1) && !directoryBase.endsWith("\\")) {
			directoryBase += "\\";
		}
		final float volume = row.getFieldFloatValue("Volume") / 127f;
		final float pitch = row.getFieldFloatValue("Pitch");
		float pitchVariance = row.getFieldFloatValue("PitchVariance");
		if (pitchVariance == 1.0f) {
			pitchVariance = 0.0f;
		}
		final float minDistance = row.getFieldFloatValue("MinDistance");
		final float maxDistance = row.getFieldFloatValue("MaxDistance");
		// Reforged leaves the cutoff of unit responses blank, which means none beyond the maximum distance.
		final float distanceCutoff = row.hasFieldValue("DistanceCutoff") ? row.getFieldFloatValue("DistanceCutoff")
				: maxDistance;
		final String[] flags = row.getField("Flags").split(",");
		boolean looping = false;
		boolean wants3D = false;
		for (final String flag : flags) {
			final String trimmedFlag = flag.trim();
			if ("LOOPING".equals(trimmedFlag)) {
				looping = true;
			}
			else if ("WANT3D".equals(trimmedFlag)) {
				wants3D = true;
			}
		}
		final UnitSound sound = new UnitSound(dataSource, volume, pitch, pitchVariance, minDistance, maxDistance,
				distanceCutoff, looping);
		sound.channel = row.getFieldValue("Channel");
		sound.wants3D = wants3D;
		final String[] files = fileNames.split(",");
		for (int i = 0; i < files.length; i++) {
			files[i] = directoryBase + files[i];
		}
		sound.unresolvedFiles = files;
		return sound;
	}

	private void resolveFiles() {
		final String[] files = this.unresolvedFiles;
		if (files == null) {
			return;
		}
		this.unresolvedFiles = null;
		for (final String labelPath : files) {
			final String resolvedPath = resolveSoundPath(this.dataSource, labelPath);
			if (this.dataSource.has(resolvedPath)) {
				this.soundPaths.add(resolvedPath);
				this.sounds.add(null);
				this.handles.add(null);
				this.durations.add(null);
				this.labelPaths.add(labelPath);
			}
			else if (!labelPath.endsWith("\\") && !labelPath.isEmpty()) {
				Log.warn("Sound file not found: " + resolvedPath);
			}
		}
	}

	/** The number of files the sound plays one of. */
	private int fileCount() {
		resolveFiles();
		return this.sounds.size();
	}

	private static String resolveSoundPath(final DataSource dataSource, String filePath) {
		final int lastDotIndex = filePath.lastIndexOf('.');
		if (lastDotIndex != -1) {
			filePath = filePath.substring(0, lastDotIndex);
		}
		return DataSourceFileHandle.resolveSoundPath(dataSource, filePath + ".wav");
	}

	private FileHandle handle(final int index) {
		FileHandle handle = this.handles.get(index);
		if (handle == null) {
			handle = new DataSourceFileHandle(this.dataSource, this.soundPaths.get(index));
			this.handles.set(index, handle);
		}
		return handle;
	}

	private Sound getSound(final int index) {
		Sound sound = this.sounds.get(index);
		if (sound == null) {
			final long start = PerfStats.ENABLED ? System.nanoTime() : 0;
			try {
				sound = SessionResources.track(Extensions.audio.newSound(handle(index)));
				if (PerfStats.ENABLED) {
					PerfStats.loaded(this.soundPaths.get(index), System.nanoTime() - start);
				}
			}
			catch (final Exception exc) {
				Log.error(exc);
				return null;
			}
			this.sounds.set(index, sound);
		}
		return sound;
	}

	/** Whether the sound of the file at the index is made, so that a play of it needs no waiting. */
	public boolean isMade(final int index) {
		resolveFiles();
		return this.sounds.get(index) != null;
	}

	/**
	 * Has the sound of the file at the index made off this thread, and runs the callback once it is.
	 *
	 * @return false when that cannot be done, so that the play makes the sound itself
	 */
	public boolean makeLater(final int index, final Runnable made) {
		resolveFiles();
		return decodeLater(index, made);
	}

	/**
	 * Decodes every file of this sound ahead of its first play. Where the backend decodes on a helper thread a file
	 * not decoded yet is not waited for: it is taken when it is done, and a play before that waits for it as for any
	 * sound first played.
	 */
	public void preload() {
		for (int i = 0; i < fileCount(); i++) {
			if ((this.sounds.get(i) != null) || this.waitingPlays.containsKey(i)) {
				continue;
			}
			if (!decodeLater(i, null)) {
				getSound(i);
			}
		}
	}

	/**
	 * Starts decoding every file of this sound where a backend can do it off this thread, so that its first play
	 * finds it decoded.
	 */
	public void prefetch() {
		for (int i = 0; i < fileCount(); i++) {
			prefetch(i);
		}
	}

	/** Starts decoding one file of this sound, as {@link #prefetch()} does for all of them. */
	public void prefetch(final int index) {
		resolveFiles();
		if ((this.sounds.get(index) == null) && (this.handles.get(index) == null)) {
			Extensions.audio.prefetchSound(handle(index));
		}
	}

	/**
	 * @return the file a script's sound path plays, which may have another extension than the path
	 */
	public static String resolveFilePath(final DataSource dataSource, String filePath) {
		final int lastDotIndex = filePath.lastIndexOf('.');
		if (lastDotIndex != -1) {
			filePath = filePath.substring(0, lastDotIndex);
		}
		return DataSourceFileHandle.resolveSoundPath(dataSource, filePath + ".wav");
	}

	/**
	 * @return the length in seconds of the file a script's sound path plays, 0 when there is none
	 */
	public static float fileDuration(final DataSource dataSource, final String filePath) {
		final DataSource data = dataSource.graphicsIndependent();
		return SoundFileDuration.read(data, resolveFilePath(data, filePath));
	}

	/** Starts the decoding of the file a later {@link #createSound} of the same path takes over. */
	public static void prefetchSound(final DataSource dataSource, final String filePath) {
		prefetchSound(dataSource, locate(dataSource, filePath, 0));
	}

	/** Starts the decoding of the file a later {@link #createSound} of the same file takes over. */
	public static void prefetchSound(final DataSource dataSource, final ScriptFile file) {
		if (file.handlePath != null) {
			Extensions.audio.prefetchSound(DataSourceFileHandle.ofResolved(dataSource, file.handlePath));
		}
	}

	public static Sound createSound(final DataSource dataSource, final String filePath) {
		return createSound(dataSource, locate(dataSource, filePath, 0));
	}

	/**
	 * Makes the sound of a file on a helper thread where the backend can, so that reading and decoding it do not hold
	 * the game thread up.
	 *
	 * @param ready is given the sound in a later frame, null when it could not be made
	 * @return false when nothing was started: the sound is to be made with {@link #createSound}
	 */
	public static boolean createSoundLater(final DataSource dataSource, final ScriptFile file,
			final Consumer<Sound> ready) {
		if (file.handlePath == null) {
			return false;
		}
		try {
			return Extensions.audio.newSoundLater(DataSourceFileHandle.ofResolved(dataSource, file.handlePath),
					sound -> ready.accept(sound == null ? null : SessionResources.track(sound)));
		}
		catch (final RuntimeException exc) {
			return false;
		}
	}

	public static Sound createSound(final DataSource dataSource, final ScriptFile file) {
		Sound newSound = null;
		if (file.handlePath != null) {
			final long start = PerfStats.ENABLED ? System.nanoTime() : 0;
			try {
				newSound = SessionResources.track(
						Extensions.audio.newSound(DataSourceFileHandle.ofResolved(dataSource, file.handlePath)));
				if (PerfStats.ENABLED) {
					PerfStats.loaded(file.resolvedPath, System.nanoTime() - start);
				}
			}
			catch (final Exception exc) {
				Log.error(exc);
			}
		}
		else {
			Log.warn("Sound file not found: " + file.resolvedPath);
		}
		return newSound;
	}

	/**
	 * What a script's sound path plays and how long it lasts. Each is a lookup in every data source, and the length
	 * also a read of the file, so a map's loading finds them for the paths its script names rather than its first
	 * step, which made all of a campaign's dialogue.
	 */
	public static ScriptFile scriptFile(final DataSource dataSource, final String filePath) {
		return locate(dataSource, filePath, fileDuration(dataSource, filePath));
	}

	private static ScriptFile locate(final DataSource dataSource, final String filePath, final float duration) {
		final String resolvedPath = resolveFilePath(dataSource, filePath);
		final String handlePath = dataSource.has(resolvedPath)
				? DataSourceFileHandle.resolveSoundPath(dataSource, resolvedPath)
				: null;
		return new ScriptFile(resolvedPath, handlePath, duration);
	}

	public static final class ScriptFile {
		private final String resolvedPath;
		// What a file handle of the resolved path reads, null when the data has no such file.
		private final String handlePath;
		private final float duration;

		private ScriptFile(final String resolvedPath, final String handlePath, final float duration) {
			this.resolvedPath = resolvedPath;
			this.handlePath = handlePath;
			this.duration = duration;
		}

		/** @return the length in seconds, as {@link UnitSound#fileDuration} gives it */
		public float getDuration() {
			return this.duration;
		}
	}

	public UnitSound(final DataSource dataSource, final float volume, final float pitch, final float pitchVariation,
			final float minDistance, final float maxDistance, final float distanceCutoff, final boolean looping) {
		this.dataSource = dataSource;
		this.volume = volume;
		this.pitch = pitch;
		this.pitchVariance = pitchVariation;
		this.minDistance = minDistance;
		this.maxDistance = maxDistance;
		this.distanceCutoff = distanceCutoff;
		this.looping = looping;
	}

	public boolean playUnitResponse(final AudioContext audioContext, final RenderUnit unit) {
		return playUnitResponse(audioContext, unit, (int) (Math.random() * fileCount()));
	}

	public boolean playUnitResponse(final AudioContext audioContext, final RenderUnit unit, final int index) {
		final long millisTime = TimeUtils.millis();
		if (millisTime < unit.lastUnitResponseEndTimeMillis) {
			return false;
		}
		if (play(audioContext, unit.location[0], unit.location[1], unit.location[2], index) != -1) {
			final float duration = getDuration(this.lastPlayedIndex);
			unit.lastUnitResponseEndTimeMillis = millisTime + (long) (1000 * duration);
			return true;
		}
		return false;
	}

	public long play(final AudioContext audioContext, final float x, final float y, final float z,
			final boolean loopOverride) {
		return play(audioContext, x, y, z, (int) (Math.random() * fileCount()), loopOverride);
	}

	public long play(final AudioContext audioContext, final float x, final float y, final float z) {
		return play(audioContext, x, y, z, (int) (Math.random() * fileCount()));
	}

	public long play(final AudioContext audioContext, final float x, final float y, final float z, final int index) {
		return play(audioContext, x, y, z, index, null);
	}

	public long play(final AudioContext audioContext, final float x, final float y, final float z, final int index,
			final Boolean loopOverride) {
		return play(audioContext, x, y, z, index, loopOverride, null);
	}

	/**
	 * Plays a random file of the sound; a loop follows the live position given, such as a unit's.
	 */
	public long playFollowing(final AudioContext audioContext, final float x, final float y, final float z,
			final Boolean loopOverride, final float[] follow) {
		return play(audioContext, x, y, z, (int) (Math.random() * fileCount()), loopOverride, follow);
	}

	/**
	 * @param follow a live position that a loop moves with (a unit's), or null for the given place
	 */
	public long play(final AudioContext audioContext, final float x, final float y, final float z, final int index,
			final Boolean loopOverride, final float[] follow) {
		if (fileCount() == 0) {
			return -1;
		}

		if (audioContext == null) {
			return -1;
		}
		final boolean loop = loopOverride == null ? this.looping : loopOverride;
		if (!loop && (this.sounds.get(index) == null)
				&& playWhenDecoded(audioContext, x, y, z, index, loopOverride, follow)) {
			return -1;
		}
		final AudioPanner panner = audioContext.createPanner();
		final AudioBufferSource source = audioContext.createBufferSource();

		// Panner settings
		panner.setPosition(x, y, z);
		panner.setDistances(this.distanceCutoff, this.minDistance);
		panner.setWants3D(this.wants3D);
		panner.followLocation = follow;
		panner.connect(audioContext.destination);

		// Source.
		source.buffer = getSound(index);
		if (source.buffer == null) {
			return -1;
		}
		source.connect(panner);

		// Make a sound.
		final float pitchNow = (this.pitch + ((float) Math.random() * this.pitchVariance * 2)) - this.pitchVariance;
		final long soundId;
		if (loop) {
			// The original plays nothing of a place the player does not see, and a loop follows the player's sight.
			soundId = source.startLoop(this.volume, this.channel, pitchNow, true);
		}
		else {
			if (!limitFor(index).tryStart(TimeUtils.millis(), (long) (1000 * getDuration(index)), MAX_SIMULTANEOUS_PLAYS)) {
				return -1;
			}
			soundId = source.start(0, this.volume * CSoundMixer.audible().channelScale(this.channel), pitchNow, false);
		}
		this.lastPlayedSound = source.buffer;
		this.lastPlayedIndex = index;
		return soundId;
	}

	/**
	 * A file first played is read and decoded on a helper thread where the backend can, and the play starts in the
	 * frame after it is done, instead of holding the frame of the first play up. A loop is not deferred: its id is
	 * given back to be stopped by.
	 *
	 * @return whether the play is waiting for the sound; false when the sound is made now by the caller
	 */
	private boolean playWhenDecoded(final AudioContext audioContext, final float x, final float y, final float z,
			final int index, final Boolean loopOverride, final float[] follow) {
		return decodeLater(index, () -> play(audioContext, x, y, z, index, loopOverride, follow));
	}

	/**
	 * Has the file at the index read and decoded off this thread, then runs the play, if any, and those that come to
	 * wait for it meanwhile.
	 *
	 * @return whether the sound is being made; false when the caller is to make it now
	 */
	private boolean decodeLater(final int index, final Runnable play) {
		final List<Runnable> waiting = this.waitingPlays.get(index);
		if (waiting != null) {
			if (play != null) {
				waiting.add(play);
			}
			return true;
		}
		final List<Runnable> plays = new ArrayList<>();
		if (play != null) {
			plays.add(play);
		}
		final FileHandle handle = handle(index);
		final boolean started;
		try {
			started = Extensions.audio.newSoundLater(handle, sound -> {
				this.waitingPlays.remove(index);
				if (sound == null) {
					// a play that made the sound meanwhile leaves the others nothing to wait for
					if (this.sounds.get(index) == null) {
						Log.warn("Sound file not read: " + this.soundPaths.get(index));
						return;
					}
				}
				else if (this.sounds.get(index) == null) {
					this.sounds.set(index, SessionResources.track(sound));
				}
				else {
					// made by another way (a preload) while this one was read
					sound.dispose();
				}
				for (final Runnable waitingPlay : plays) {
					waitingPlay.run();
				}
			});
		}
		catch (final RuntimeException exc) {
			return false;
		}
		if (started) {
			this.waitingPlays.put(index, plays);
		}
		return started;
	}

	private PlayLimit limitFor(final int index) {
		while (this.limits.size() <= index) {
			this.limits.add(new PlayLimit());
		}
		return this.limits.get(index);
	}

	/**
	 * @return the length in seconds of the label's first file, which scripts wait by; 0 when it has none
	 */
	public float getDuration() {
		return fileCount() == 0 ? 0 : getDuration(0);
	}

	/**
	 * @return the length in seconds of the file played last, 0 before any
	 */
	public float getLastPlayedDuration() {
		return this.lastPlayedSound == null ? 0 : getDuration(this.lastPlayedIndex);
	}

	/** @return the length in seconds of the label's file at this index */
	public float getDuration(final int index) {
		resolveFiles();
		Float duration = this.durations.get(index);
		if (duration == null) {
			final DataSource data = this.dataSource.graphicsIndependent();
			duration = SoundFileDuration.read(data, resolveSoundPath(data, this.labelPaths.get(index)));
			this.durations.set(index, duration);
		}
		return duration;
	}

	public boolean isLooping() {
		return this.looping;
	}

	public int getSoundCount() {
		return fileCount();
	}

	public Sound getLastPlayedSound() {
		return this.lastPlayedSound;
	}

	public void stop() {
		for (final Sound sound : this.sounds) {
			if (sound != null) {
				SoundLoops.stopAll(sound);
			}
		}
	}

	public void stop(long soundId) {
		// This may misbehave if called for a list longer than 1, due to the random
		// index used when starting?
		// Not sure if IDs are unique per source
		for (final Sound sound : this.sounds) {
			if (sound != null) {
				SoundLoops.stop(sound, soundId);
			}
		}
	}
}
