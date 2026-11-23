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
	public java.util.List<com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CTimerDialog> timerDialogs;
	public java.util.List<com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialog> scriptDialogs;
	public float[] cameraFields;
	@SavedDefault("true")
	public boolean userUIEnabled = true;
	/** False while ShowInterface(false) keeps the game interface away, as in cinematic mode; true in older saves. */
	@SavedDefault("true")
	public boolean interfaceShown = true;
	public boolean cinematicPanelsHidden;
	public boolean subtitlesForced;
	/** The cinematic filter, see CineFilter.capture; null in saves from before it was kept. */
	public float[] cineFilter;
	public String cineFilterTexture;
	/** Bits of the selection switches scripts turned off (MeleeUI.SELECTION_*); none in a game that never did. */
	public int selectionOff;
	@SavedDefault("true")
	public boolean allyFilterButtonEnabled = true;
	@SavedDefault("true")
	public boolean creepFilterButtonEnabled = true;
	public String dayNightTerrainModel;
	public String dayNightUnitModel;
	public String skyModel;
	public int fogStyle;
	public float[] fogColor;
	public float fogDensity;
	public float fogStart;
	public float fogEnd;
	/** 0 in saves from before the rest of the fog was kept; the fog then keeps what the map set. */
	public float fogMaxOpacity;
	public float fogHeightMin;
	public float fogHeightMax;
	public boolean fogDrawOverSky;
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
	/** The scene SetCinematicScene put up (speaker, text, portrait), with how long it had run; absent in older saves. */
	public boolean sceneActive;
	public int sceneUnitId;
	/** CPlayerColor ordinal, -1 for none. */
	@SavedDefault("-1")
	public int sceneColor = -1;
	public String sceneSpeaker;
	public String sceneText;
	public float sceneDuration;
	public float sceneVoiceover;
	public float sceneElapsed;
	/** EnableOcclusion, EnableWorldFogBoundary and SetWaterDeforms; on in saves from before they were kept. */
	@SavedDefault("true")
	public boolean occlusion = true;
	@SavedDefault("true")
	public boolean worldFogBoundary = true;
	@SavedDefault("true")
	public boolean waterDeforms = true;
}
