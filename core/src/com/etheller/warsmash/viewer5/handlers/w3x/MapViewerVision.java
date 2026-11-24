package com.etheller.warsmash.viewer5.handlers.w3x;

import com.badlogic.gdx.graphics.Color;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3iFlags;
import com.etheller.warsmash.viewer5.FogSettings;
import com.etheller.warsmash.viewer5.FogStyle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

/**
 * What the viewing player sees: fog shading, replay and observer vision, and what is heard.
 */
final class MapViewerVision {
	private MapViewerVision() {
	}

	/**
	 * The shade a fog state is drawn with, measured on 1.29 against the same view without fog: fog leaves 34 % of the
	 * brightness. Melee maps set "masked areas are partially visible": the black mask then leaves 16 %, half of fog;
	 * campaign maps do not, and their mask stays black.
	 */
	static byte getFogShade(final War3MapViewer viewer, final byte fogState) {
		if (fogState == CFogState.FOGGED.getMask()) {
			return War3MapViewer.FOGGED_SHADE;
		}
		if ((fogState == CFogState.MASKED.getMask()) && isMaskedAreasPartiallyVisible(viewer)) {
			return War3MapViewer.MASKED_PARTIALLY_VISIBLE_SHADE;
		}
		return fogState;
	}

	/**
	 * What each fog of war cell value is drawn as: its fog state and that state's shade, under the map's fog and mask
	 * settings.
	 */
	static void fillFogTables(final War3MapViewer viewer, final CFogState[] stateByCell, final byte[] shadeByCell) {
		for (int i = 0; i < 256; i++) {
			final byte state = viewer.simulation.getFogStateFromSettings((byte) i);
			stateByCell[i] = CFogState.getByMask(state);
			shadeByCell[i] = getFogShade(viewer, state);
		}
	}

	static boolean isMaskedAreasPartiallyVisible(final War3MapViewer viewer) {
		return (viewer.lastLoadedMapInformation != null)
				&& viewer.lastLoadedMapInformation.hasFlag(War3MapW3iFlags.MASKED_AREAS_ARE_PARTIALLY_VISIBLE);
	}

	/**
	 * A replay's Replay Vision and Fog of War: the picture, the minimap and the units shown are what that player (or
	 * every player together) saw, or the whole map without the fog. The game itself, and GetLocalPlayer, stay the
	 * recording player's.
	 */
	static void setReplayVision(final War3MapViewer viewer, final int playerIndex, final boolean fogShown) {
		viewer.replayVisionPlayer = playerIndex;
		viewer.viewedReplayPlayer = playerIndex;
		viewer.replayFogShown = fogShown;
		if (viewer.hasFrames()) {
			// the frames' writer works the shown fog out on the simulation's side; nothing live is read here
			viewer.requestVision(playerIndex, fogShown);
			return;
		}
		if (viewer.replayFogOfWar == null) {
			viewer.replayFogOfWar = new CPlayerFogOfWar(viewer.simulation.getPathingGrid());
		}
		viewer.replayVisionSources.clear();
		for (int i = 0; i < viewer.getGameData().getMaxPlayers(); i++) {
			final CPlayer player = viewer.simulation.getPlayer(i);
			if (viewer.isReplayVisionPlayer(player)) {
				viewer.replayVisionSources.add(player.getFogOfWar());
			}
		}
		if (!fogShown) {
			viewer.replayFogOfWar.revealAll();
		}
		else {
			updateReplayVision(viewer);
		}
		if (viewer.observerFogOfWar == null) {
			viewer.terrain.setFogOfWarData(viewer.simulation, viewer.getFogOfWar());
		}
	}

	static void updateReplayVision(final War3MapViewer viewer) {
		if (viewer.hasFrames()) {
			return;
		}
		if ((viewer.replayVisionPlayer == War3MapViewer.REPLAY_VISION_ALL_PLAYERS) && viewer.replayFogShown) {
			viewer.replayFogOfWar.showMostVisibleOf(viewer.replayVisionSources);
		}
	}

	/**
	 * Whether what is shown to a player on screen (a message, an alert) reaches the viewer: the local player's, and in
	 * a replay the shown player's, or everyone's with All Players.
	 */
	static boolean isViewedPlayer(final War3MapViewer viewer, final int playerIndex) {
		final int vision = viewer.viewedReplayPlayer;
		if (vision == War3MapViewer.REPLAY_VISION_NONE) {
			return playerIndex == viewer.viewedLocalPlayer;
		}
		if (vision == War3MapViewer.REPLAY_VISION_ALL_PLAYERS) {
			return viewer.isReplayVisionPlayer(viewer.simulation.getPlayer(playerIndex));
		}
		return playerIndex == vision;
	}

	/** The player whose resources and sight the interface shows: a replay's Replay Vision, else the local player. */
	static int getViewingPlayerIndex(final War3MapViewer viewer) {
		return viewer.replayVisionPlayer >= 0 ? viewer.replayVisionPlayer : viewer.localPlayerIndex;
	}

	/**
	 * Whether the sounds of units, combat and effects at a point are heard: the original plays none from a place the
	 * player does not see.
	 */
	static boolean isSoundHeardAt(final War3MapViewer viewer, final float x, final float y) {
		if (viewer.simulation == null) {
			return true;
		}
		return viewer.getFogOfWar().isVisible(viewer.simulation, viewer.simulation.getPathingGrid(), x, y);
	}

	/** Whether the picture shows what happens at the point: the viewer's fog is visible there. */
	static boolean isPointShownToViewer(final War3MapViewer viewer, final float x, final float y) {
		if (viewer.simulation == null) {
			// Blight set while the map loads shows once the viewer sees it.
			return false;
		}
		return viewer.getFogOfWar().isVisible(viewer.simulation, viewer.simulation.getPathingGrid(), x, y);
	}

	/** Whether the picture shows the unit: the local player sees it, or a replay's vision does. */
	static boolean isShownToViewer(final War3MapViewer viewer, final CUnit unit) {
		if ((viewer.replayVisionPlayer != War3MapViewer.REPLAY_VISION_NONE) && !viewer.replayFogShown) {
			return true;
		}
		if (viewer.replayVisionPlayer == War3MapViewer.REPLAY_VISION_ALL_PLAYERS) {
			for (int i = 0; i < viewer.getGameData().getMaxPlayers(); i++) {
				if (viewer.isReplayVisionPlayer(viewer.simulation.getPlayer(i)) && unit.isVisible(viewer.simulation, i)) {
					return true;
				}
			}
			return false;
		}
		return unit.isVisible(viewer.simulation, getViewingPlayerIndex(viewer));
	}

	/**
	 * What a player defeated with observers on death sees: the whole map. Only the picture changes, the original's
	 * IsVisibleToPlayer and its like keep answering from the player's own fog.
	 */
	static void startObserverView(final War3MapViewer viewer) {
		if (viewer.observerFogOfWar == null) {
			viewer.observerFogOfWar = new CPlayerFogOfWar(viewer.simulation.getPathingGrid());
			viewer.observerFogOfWar.revealAll();
			viewer.terrain.setFogOfWarData(viewer.simulation, viewer.observerFogOfWar);
		}
	}

	static byte fadeLineOfSightColor(final byte lastFogStateColor, final byte state) {
		final short prevValue = (short) (lastFogStateColor & 0xFF);
		final short newValue = (short) (state & 0xFF);
		final short delta = (short) (newValue - prevValue);
		final short appliedMagnitude = (short) Math.min(9, Math.abs(delta));

		return (byte) ((prevValue + (appliedMagnitude * (delta < 0 ? -1 : 1))) & 0xFF);
	}

	/**
	 * The terrain fog the map was saved with (Map Properties of the editor). A map script that sets its own fog
	 * overrides it, and ResetTerrainFog returns to it.
	 */
	static void applyMapTerrainFog(final War3MapViewer viewer, final War3MapW3i mapInfo) {
		final int style = mapInfo.getUseTerrainFog();
		if ((style <= 0) || (style >= FogStyle.values().length)) {
			return;
		}
		final short[] color = mapInfo.getFogColor();
		final FogSettings fog = new FogSettings();
		fog.setStyleByIndex(style);
		fog.start = mapInfo.getFogHeight()[0];
		fog.end = mapInfo.getFogHeight()[1];
		// v39+ maps carry the distance range of the linear fog and a cap of the fog opacity next to the old z range;
		// older maps leave both at 0, which has to keep the old behaviour.
		final float[] linearRange = mapInfo.getFogLinearRange();
		if ((style == FogStyle.LINEAR.ordinal()) && (linearRange[1] > linearRange[0]) && (linearRange[1] > 0)) {
			fog.start = linearRange[0];
			fog.end = linearRange[1];
		}
		final float maxOpacity = mapInfo.getFogMaxOpacity();
		fog.maxOpacity = ((maxOpacity > 0) && (maxOpacity < 1)) ? maxOpacity : 1f;
		final float[] heightRange = mapInfo.getFogHeightRange();
		if (heightRange[1] > heightRange[0]) {
			fog.heightMin = heightRange[0];
			fog.heightMax = heightRange[1];
		}
		fog.drawOverSky = mapInfo.isFogDrawOverSky();
		fog.density = mapInfo.getFogDensity();
		fog.color = new Color((color[0] & 0xFF) / 255f, (color[1] & 0xFF) / 255f, (color[2] & 0xFF) / 255f, 1f);
		viewer.defaultFogSettings = fog;
		resetTerrainFog(viewer);
	}

	static void resetTerrainFog(final War3MapViewer viewer) {
		if (viewer.defaultFogSettings != null) {
			viewer.worldScene.fogSettings.copyFrom(viewer.defaultFogSettings);
		}
	}
}
