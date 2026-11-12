package com.etheller.warsmash.viewer5.handlers.w3x;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.etheller.warsmash.viewer5.FogSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedGame;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedInterfaceState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CSoundVolumeGroup;

/**
 * Save and load requests of a map viewer and the interface state kept across them.
 */
final class MapViewerSaves {
	private MapViewerSaves() {
	}

	/**
	 * Scripts ask in the middle of a thread, and the menu in the middle of a frame; the game state is only whole
	 * between two simulation steps.
	 */
	static void requestSaveGame(final War3MapViewer viewer, final FileHandle file) {
		viewer.pendingSave = file;
	}

	static void requestLoadGame(final War3MapViewer viewer, final FileHandle file) {
		requestLoadGame(viewer, file, false);
	}

	/** byScript: a map's LoadGame (zone travel), which the loaded map tells apart from a player's load. */
	static void requestLoadGame(final War3MapViewer viewer, final FileHandle file, final boolean byScript) {
		viewer.pendingLoad = file;
		viewer.pendingLoadByScript = byScript;
	}

	/**
	 * SaveAndChangeLevelBJ is SaveGame then ChangeLevel in one thread; leaving the map at once would drop the save
	 * still waiting for the end of the step, so the level changes after it is written.
	 */
	static void runAfterPendingSave(final War3MapViewer viewer, final Runnable action) {
		if (viewer.pendingSave == null) {
			action.run();
		}
		else {
			viewer.afterPendingSave = action;
		}
	}

	static void runPendingSaveRequests(final War3MapViewer viewer) {
		if (viewer.pendingSave != null) {
			final FileHandle file = viewer.pendingSave;
			viewer.pendingSave = null;
			// The recorded game made this save; a playback goes on as if it had, without writing over the player's.
			final boolean saved = viewer.isReplayPlayback() || SavedGame.save(viewer, file);
			if (viewer.saveGameListener != null) {
				viewer.saveGameListener.saveFinished(file, saved);
			}
			if (viewer.afterPendingSave != null) {
				final Runnable action = viewer.afterPendingSave;
				viewer.afterPendingSave = null;
				action.run();
				return;
			}
		}
		if ((viewer.pendingLoad != null) && (viewer.loadGameListener != null)) {
			final FileHandle file = viewer.pendingLoad;
			viewer.pendingLoad = null;
			viewer.loadGameListener.loadRequested(file, viewer.pendingLoadByScript);
		}
	}

	static SavedInterfaceState captureInterfaceState(final War3MapViewer viewer) {
		final SavedInterfaceState state = viewer.interfaceStateKeeper == null ? new SavedInterfaceState()
				: viewer.interfaceStateKeeper.captureInterfaceState();
		state.dayNightTerrainModel = viewer.dayNightTerrainModel;
		state.dayNightUnitModel = viewer.dayNightUnitModel;
		state.skyModel = viewer.skyModel;
		state.blightGround = viewer.terrain.captureBlight(false);
		state.blightSeen = viewer.terrain.captureBlight(true);
		final FogSettings fog = viewer.worldScene.fogSettings;
		state.fogStyle = fog.style.ordinal();
		state.fogColor = new float[] { fog.color.r, fog.color.g, fog.color.b, fog.color.a };
		state.fogDensity = fog.density;
		state.fogStart = fog.start;
		state.fogEnd = fog.end;
		state.waterColor = viewer.waterColor;
		state.allyColorFilterState = viewer.getAllyColorFilter().getState();
		state.restartMissionDisabled = viewer.restartMissionDisabled;
		state.altMinimapIcon = viewer.altMinimapIcon;
		state.volumeGroupScales = new float[CSoundVolumeGroup.VALUES.length];
		for (final CSoundVolumeGroup group : CSoundVolumeGroup.VALUES) {
			state.volumeGroupScales[group.ordinal()] = viewer.soundMixer.getGroupScale(group);
		}
		return state;
	}

	static void restoreInterfaceState(final War3MapViewer viewer, final SavedInterfaceState state) {
		if (state.dayNightTerrainModel != null) {
			viewer.setDayNightModels(state.dayNightTerrainModel, state.dayNightUnitModel);
		}
		if (state.skyModel != null) {
			viewer.setSkyModel(state.skyModel);
		}
		viewer.terrain.restoreBlight(state.blightGround, state.blightSeen);
		if (state.fogColor != null) {
			final FogSettings fog = viewer.worldScene.fogSettings;
			fog.setStyleByIndex(state.fogStyle);
			fog.color = new Color(state.fogColor[0], state.fogColor[1], state.fogColor[2], state.fogColor[3]);
			fog.density = state.fogDensity;
			fog.start = state.fogStart;
			fog.end = state.fogEnd;
		}
		if (state.waterColor != null) {
			viewer.setWaterBaseColor(state.waterColor);
		}
		viewer.getAllyColorFilter().setState(state.allyColorFilterState);
		viewer.restartMissionDisabled = state.restartMissionDisabled;
		viewer.altMinimapIcon = state.altMinimapIcon;
		final float[] scales = state.volumeGroupScales;
		for (final CSoundVolumeGroup group : CSoundVolumeGroup.VALUES) {
			viewer.soundMixer.setGroupScale(group,
					(scales != null) && (group.ordinal() < scales.length) ? scales[group.ordinal()] : 1f);
		}
		if (viewer.interfaceStateKeeper != null) {
			viewer.interfaceStateKeeper.restoreInterfaceState(state);
		}
	}
}
