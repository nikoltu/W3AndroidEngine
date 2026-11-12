package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.util.SessionResources;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.DataSourceFileHandle;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.AudioBufferSource;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.AudioPanner;
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
	private final List<Float> durations = new ArrayList<>();
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
		for (final String fileName : fileNames.split(",")) {
			final String resolvedPath = resolveSoundPath(dataSource, directoryBase + fileName);
			if (dataSource.has(resolvedPath)) {
				sound.soundPaths.add(resolvedPath);
				sound.sounds.add(null);
				sound.durations.add(null);
			}
			else if (!fileName.isEmpty()) {
				Log.warn("Sound file not found: " + resolvedPath);
			}
		}
		return sound;
	}

	private static String resolveSoundPath(final DataSource dataSource, String filePath) {
		final int lastDotIndex = filePath.lastIndexOf('.');
		if (lastDotIndex != -1) {
			filePath = filePath.substring(0, lastDotIndex);
		}
		return DataSourceFileHandle.resolveSoundPath(dataSource, filePath + ".wav");
	}

	private Sound getSound(final int index) {
		Sound sound = this.sounds.get(index);
		if (sound == null) {
			try {
				sound = SessionResources.track(
						Extensions.audio.newSound(new DataSourceFileHandle(this.dataSource, this.soundPaths.get(index))));
			}
			catch (final Exception exc) {
				Log.error(exc);
				return null;
			}
			this.sounds.set(index, sound);
		}
		return sound;
	}

	/**
	 * Decodes every file of this sound now rather than at its first play.
	 */
	public void preload() {
		for (int i = 0; i < this.sounds.size(); i++) {
			getSound(i);
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
		return SoundFileDuration.read(dataSource, resolveFilePath(dataSource, filePath));
	}

	public static Sound createSound(final DataSource dataSource, final String filePath) {
		Sound newSound = null;
		final String resolvedPath = resolveFilePath(dataSource, filePath);
		if (dataSource.has(resolvedPath)) {
			try {
				newSound = SessionResources.track(
						Extensions.audio.newSound(new DataSourceFileHandle(dataSource, resolvedPath)));
			}
			catch (final Exception exc) {
				Log.error(exc);
			}
		}
		else {
			Log.warn("Sound file not found: " + resolvedPath);
		}
		return newSound;
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
		return playUnitResponse(audioContext, unit, (int) (Math.random() * this.sounds.size()));
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
		return play(audioContext, x, y, z, (int) (Math.random() * this.sounds.size()), loopOverride);
	}

	public long play(final AudioContext audioContext, final float x, final float y, final float z) {
		return play(audioContext, x, y, z, (int) (Math.random() * this.sounds.size()));
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
		return play(audioContext, x, y, z, (int) (Math.random() * this.sounds.size()), loopOverride, follow);
	}

	/**
	 * @param follow a live position that a loop moves with (a unit's), or null for the given place
	 */
	public long play(final AudioContext audioContext, final float x, final float y, final float z, final int index,
			final Boolean loopOverride, final float[] follow) {
		if (this.sounds.isEmpty()) {
			return -1;
		}

		if (audioContext == null) {
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
		final boolean loop = loopOverride == null ? this.looping : loopOverride;
		final float pitchNow = (this.pitch + ((float) Math.random() * this.pitchVariance * 2)) - this.pitchVariance;
		final long soundId;
		if (loop) {
			// The original plays nothing of a place the player does not see, and a loop follows the player's sight.
			soundId = source.startLoop(this.volume, this.channel, pitchNow, true);
		}
		else {
			soundId = source.start(0, this.volume * CSoundMixer.audible().channelScale(this.channel), pitchNow, false);
		}
		this.lastPlayedSound = source.buffer;
		this.lastPlayedIndex = index;
		return soundId;
	}

	/**
	 * @return the length in seconds of the label's first file, which scripts wait by; 0 when it has none
	 */
	public float getDuration() {
		return this.soundPaths.isEmpty() ? 0 : getDuration(0);
	}

	/**
	 * @return the length in seconds of the file played last, 0 before any
	 */
	public float getLastPlayedDuration() {
		return this.lastPlayedSound == null ? 0 : getDuration(this.lastPlayedIndex);
	}

	/** @return the length in seconds of the label's file at this index */
	public float getDuration(final int index) {
		Float duration = this.durations.get(index);
		if (duration == null) {
			duration = SoundFileDuration.read(this.dataSource, this.soundPaths.get(index));
			this.durations.set(index, duration);
		}
		return duration;
	}

	public boolean isLooping() {
		return this.looping;
	}

	public int getSoundCount() {
		return this.sounds.size();
	}

	public Sound getLastPlayedSound() {
		return this.lastPlayedSound;
	}

	public void stop() {
		for (final Sound sound : this.sounds) {
			if (sound != null) {
				sound.stop();
			}
		}
	}

	public void stop(long soundId) {
		// This may misbehave if called for a list longer than 1, due to the random
		// index used when starting?
		// Not sure if IDs are unique per source
		for (final Sound sound : this.sounds) {
			if (sound != null) {
				sound.stop(soundId);
			}
		}
	}
}
