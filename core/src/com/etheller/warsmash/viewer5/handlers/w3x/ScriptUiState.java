package com.etheller.warsmash.viewer5.handlers.w3x;

import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimThreadGuard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CheckpointSaves;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.CommandButtonEffect;

/** The switches of the game interface a map script flips with the Reforged natives; local to this machine. */
public final class ScriptUiState {
	private boolean cursorEnabled = true;
	private boolean selectionEnabled = true;
	private boolean selectionCircleEnabled = true;
	private boolean targetIndicatorEnabled = true;
	private boolean originFramesHidden;
	private boolean uiAutoPosition = true;
	private boolean terrainShown = true;
	private boolean skyBoxShown = true;
	private boolean clientActive = true;
	private boolean pauseThematicMusicOnFocusLost;
	private String minimapTerrainTexture;
	private final List<CommandButtonEffect> commandButtonEffects = new ArrayList<>();
	private int commandButtonEffectsVersion;
	private final CheckpointSaves checkpointSaves = new CheckpointSaves();
	private Runnable frameFlush;
	private UIFrame worldFrame;

	/** Called by the interface every frame, for the Frame API to lay out the frames a script changed. */
	public void setFrameFlush(final Runnable frameFlush) {
		SimThreadGuard.assertGl();
		this.frameFlush = frameFlush;
	}

	public void flushFrames() {
		if (this.frameFlush != null) {
			this.frameFlush.run();
		}
	}

	/** The script's handle of the world frame, whose rectangle the 3D world follows once the game's own frames are hidden. */
	public UIFrame getWorldFrame() {
		return this.worldFrame;
	}

	public void setWorldFrame(final UIFrame worldFrame) {
		this.worldFrame = worldFrame;
	}

	public boolean isCursorEnabled() {
		return this.cursorEnabled;
	}

	public void setCursorEnabled(final boolean cursorEnabled) {
		SimThreadGuard.assertGl();
		this.cursorEnabled = cursorEnabled;
	}

	public boolean isSelectionEnabled() {
		return this.selectionEnabled;
	}

	public void setSelectionEnabled(final boolean selectionEnabled) {
		SimThreadGuard.assertGl();
		this.selectionEnabled = selectionEnabled;
	}

	public boolean isSelectionCircleEnabled() {
		return this.selectionCircleEnabled;
	}

	public void setSelectionCircleEnabled(final boolean selectionCircleEnabled) {
		SimThreadGuard.assertGl();
		this.selectionCircleEnabled = selectionCircleEnabled;
	}

	public boolean isTargetIndicatorEnabled() {
		return this.targetIndicatorEnabled;
	}

	public void setTargetIndicatorEnabled(final boolean targetIndicatorEnabled) {
		SimThreadGuard.assertGl();
		this.targetIndicatorEnabled = targetIndicatorEnabled;
	}

	public boolean isOriginFramesHidden() {
		return this.originFramesHidden;
	}

	public void setOriginFramesHidden(final boolean originFramesHidden) {
		SimThreadGuard.assertGl();
		this.originFramesHidden = originFramesHidden;
	}

	public boolean isUiAutoPosition() {
		return this.uiAutoPosition;
	}

	public void setUiAutoPosition(final boolean uiAutoPosition) {
		SimThreadGuard.assertGl();
		this.uiAutoPosition = uiAutoPosition;
	}

	public boolean isTerrainShown() {
		return this.terrainShown;
	}

	public void setTerrainShown(final boolean terrainShown) {
		SimThreadGuard.assertGl();
		this.terrainShown = terrainShown;
	}

	public boolean isSkyBoxShown() {
		return this.skyBoxShown;
	}

	public void setSkyBoxShown(final boolean skyBoxShown) {
		SimThreadGuard.assertGl();
		this.skyBoxShown = skyBoxShown;
	}

	/** Whether the window or app has the user's attention; the backend tells it on every focus change. */
	public boolean isClientActive() {
		return this.clientActive;
	}

	public void setClientActive(final boolean clientActive) {
		SimThreadGuard.assertGl();
		this.clientActive = clientActive;
	}

	public boolean isPauseThematicMusicOnFocusLost() {
		return this.pauseThematicMusicOnFocusLost;
	}

	public void setPauseThematicMusicOnFocusLost(final boolean pause) {
		SimThreadGuard.assertGl();
		this.pauseThematicMusicOnFocusLost = pause;
	}

	/** Whether the thematic music must stand still now: the map asked for it and the client has no focus. */
	public boolean isMusicHeldByFocus() {
		return this.pauseThematicMusicOnFocusLost && !this.clientActive;
	}

	/** The picture a script put in place of the minimap's terrain (BlzChangeMinimapTerrainTex), or null. */
	public String getMinimapTerrainTexture() {
		return this.minimapTerrainTexture;
	}

	public void setMinimapTerrainTexture(final String minimapTerrainTexture) {
		SimThreadGuard.assertGl();
		this.minimapTerrainTexture = minimapTerrainTexture;
	}

	public List<CommandButtonEffect> getCommandButtonEffects() {
		return Collections.unmodifiableList(this.commandButtonEffects);
	}

	public void addCommandButtonEffect(final CommandButtonEffect effect) {
		this.commandButtonEffects.add(effect);
		this.commandButtonEffectsVersion++;
	}

	public void removeCommandButtonEffect(final CommandButtonEffect effect) {
		if (this.commandButtonEffects.remove(effect)) {
			this.commandButtonEffectsVersion++;
		}
	}

	/** Changes with every effect added or destroyed, for the command card to be filled again. */
	public int getCommandButtonEffectsVersion() {
		return this.commandButtonEffectsVersion;
	}

	public CheckpointSaves getCheckpointSaves() {
		return this.checkpointSaves;
	}
}
