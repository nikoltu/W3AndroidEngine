package com.etheller.warsmash.viewer5.handlers.w3x.save;

/**
 * What the player sees of a saved game that is not in the simulation: where the camera is and what is selected.
 */
public final class SavedInterfaceState {
	public float cameraX;
	public float cameraY;
	public int[] selectedUnitHandleIds;
	public int[][] controlGroupHandleIds;
	public boolean userControlEnabled;
	public boolean cinematicMode;
	public java.util.List<com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog> timerDialogs;
	public java.util.List<com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialog> scriptDialogs;
	public float[] cameraFields;
	public boolean userUIEnabled = true;
	/** Bits of the selection switches scripts turned off (MeleeUI.SELECTION_*); none in a game that never did. */
	public int selectionOff;
	public boolean allyFilterButtonEnabled = true;
	public boolean creepFilterButtonEnabled = true;
	public String dayNightTerrainModel;
	public String dayNightUnitModel;
	public String skyModel;
	public int fogStyle;
	public float[] fogColor;
	public float fogDensity;
	public float fogStart;
	public float fogEnd;
	public float[] waterColor;
	public float[] cameraBounds;
	public String mapMusic;
	public boolean mapMusicRandom;
	public int mapMusicIndex;
	public String playingMusic;
	public int allyColorFilterState;
	public boolean restartMissionDisabled;
	public String altMinimapIcon;
	/** By {@code CSoundVolumeGroup} ordinal; null in saves from before it was kept, meaning every group at 1. */
	public float[] volumeGroupScales;
	/** The ground's blight and what the viewer had seen of it, a bit per corner; null in saves from before they were kept. */
	public byte[] blightGround;
	public byte[] blightSeen;
}
