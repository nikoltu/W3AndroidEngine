package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.DataSourceFileHandle;
import com.etheller.warsmash.viewer5.AudioBufferSource;
import com.etheller.warsmash.viewer5.AudioContext;
import com.etheller.warsmash.viewer5.AudioPanner;
import com.etheller.warsmash.viewer5.gl.Extensions;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSoundMixer;

public final class UnitSound {
	private static final UnitSound SILENT = new UnitSound(null, 0, 0, 0, 0, 0, 0, false);

	// Decoding every sound of a unit type when its first unit appeared held up the start of a game for seconds, so a
	// sound is decoded when it is first played.
	private final DataSource dataSource;
	private final List<String> soundPaths = new ArrayList<>();
	private final List<Sound> sounds = new ArrayList<>();
	private final float volume;
	private final float pitch;
	private final float pitchVariance;
	private final float minDistance;
	private final float maxDistance;
	private final float distanceCutoff;
	private final boolean looping;
	private int channel;

	private Sound lastPlayedSound;

	public static UnitSound create(final DataSource dataSource, final DataTable unitAckSounds, final String soundName,
			final String soundType) {
		final Element row = unitAckSounds.get(soundName + soundType);
		if (row == null) {
			return SILENT;
		}
		final String fileNames = row.getField("FileNames");
		String directoryBase = row.getField("DirectoryBase");
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
		final float distanceCutoff = row.getFieldFloatValue("DistanceCutoff");
		final String[] flags = row.getField("Flags").split(",");
		boolean looping = false;
		for (final String flag : flags) {
			if ("LOOPING".equals(flag)) {
				looping = true;
			}
		}
		final UnitSound sound = new UnitSound(dataSource, volume, pitch, pitchVariance, minDistance, maxDistance,
				distanceCutoff, looping);
		sound.channel = row.getFieldValue("Channel");
		for (final String fileName : fileNames.split(",")) {
			final String resolvedPath = resolveSoundPath(dataSource, directoryBase + fileName);
			if (dataSource.has(resolvedPath)) {
				sound.soundPaths.add(resolvedPath);
				sound.sounds.add(null);
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
				sound = Extensions.audio.newSound(new DataSourceFileHandle(this.dataSource, this.soundPaths.get(index)));
			}
			catch (final Exception exc) {
				exc.printStackTrace();
				return null;
			}
			this.sounds.set(index, sound);
		}
		return sound;
	}

	public static Sound createSound(final DataSource dataSource, String filePath) {
		final int lastDotIndex = filePath.lastIndexOf('.');
		if (lastDotIndex != -1) {
			filePath = filePath.substring(0, lastDotIndex);
		}
		Sound newSound = null;
		final String resolvedPath = DataSourceFileHandle.resolveSoundPath(dataSource, filePath + ".wav");
		if (dataSource.has(resolvedPath)) {
			try {
				newSound = Extensions.audio.newSound(new DataSourceFileHandle(dataSource, resolvedPath));
			}
			catch (final Exception exc) {
				exc.printStackTrace();
			}
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
			final float duration = Extensions.audio.getDuration(this.lastPlayedSound);
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
		panner.connect(audioContext.destination);

		// Source.
		source.buffer = getSound(index);
		if (source.buffer == null) {
			return -1;
		}
		source.connect(panner);

		// Make a sound.
		long soundId = -1;
		final float mixedVolume = this.volume * CSoundMixer.channelScale(this.channel);
		if (loopOverride == null) {
			soundId = source.start(0, mixedVolume,
					(this.pitch + ((float) Math.random() * this.pitchVariance * 2)) - this.pitchVariance, this.looping);
		}
		else {
			soundId = source.start(0, mixedVolume,
					(this.pitch + ((float) Math.random() * this.pitchVariance * 2)) - this.pitchVariance, loopOverride);
		}
		this.lastPlayedSound = source.buffer;
		return soundId;
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