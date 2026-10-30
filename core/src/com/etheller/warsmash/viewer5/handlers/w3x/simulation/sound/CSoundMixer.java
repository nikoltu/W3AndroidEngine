package com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CSoundVolumeGroup;

/**
 * VolumeGroupSetVolume scales: every sound channel of the game belongs to one volume group.
 */
public final class CSoundMixer {
	private static final float[] GROUP_SCALES = new float[CSoundVolumeGroup.VALUES.length];
	private static Runnable musicScaleListener;
	static {
		reset();
	}

	private CSoundMixer() {
	}

	public static void reset() {
		for (int i = 0; i < GROUP_SCALES.length; i++) {
			GROUP_SCALES[i] = 1f;
		}
		notifyMusic();
	}

	public static void setGroupScale(final CSoundVolumeGroup group, final float scale) {
		GROUP_SCALES[group.ordinal()] = Math.max(0f, Math.min(1f, scale));
		notifyMusic();
	}

	public static float getGroupScale(final CSoundVolumeGroup group) {
		return GROUP_SCALES[group.ordinal()];
	}

	public static void setMusicScaleListener(final Runnable listener) {
		musicScaleListener = listener;
	}

	private static void notifyMusic() {
		if (musicScaleListener != null) {
			musicScaleListener.run();
		}
	}

	public static float channelScale(final int channel) {
		return GROUP_SCALES[groupOfChannel(channel).ordinal()];
	}

	// Channels of the sound data: 0 general, 1 unit selection, 2 unit acknowledgement, 3 unit movement, 4 unit ready,
	// 5 combat, 6 error, 7 music, 8 interface, 9 looping movement, 10 looping ambient, 11 animations, 12 constructions,
	// 13 birth, 14 fire.
	private static CSoundVolumeGroup groupOfChannel(final int channel) {
		switch (channel) {
		case 1:
		case 2:
		case 4:
			return CSoundVolumeGroup.UNITSOUNDS;
		case 3:
		case 9:
			return CSoundVolumeGroup.UNITMOVEMENT;
		case 5:
		case 11:
			return CSoundVolumeGroup.COMBAT;
		case 6:
		case 8:
			return CSoundVolumeGroup.UI;
		case 7:
			return CSoundVolumeGroup.MUSIC;
		case 14:
			return CSoundVolumeGroup.FIRE;
		case 10:
			return CSoundVolumeGroup.AMBIENTSOUNDS;
		default:
			return CSoundVolumeGroup.SPELLS;
		}
	}
}
