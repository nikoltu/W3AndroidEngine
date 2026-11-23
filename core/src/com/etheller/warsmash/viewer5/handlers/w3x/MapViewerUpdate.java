package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.Iterator;
import java.util.List;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.Camera;
import com.etheller.warsmash.viewer5.FogStyle;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.FastForwardListener;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer.UnitIndicator;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;

/**
 * The frame of a map viewer: simulation steps, fast forward, and drawing the world.
 */
final class MapViewerUpdate {
	private MapViewerUpdate() {
	}

	static void updateStackedSounds(final War3MapViewer viewer) {
		final Camera camera = viewer.worldScene.camera;
		float targetX = camera.location.x;
		float targetY = camera.location.y;
		final Vector3 forward = viewer.stackedSoundsForward.set(camera.directionZ).scl(-1);
		if (forward.z < -0.01f) {
			final float distance = (viewer.terrain.getGroundHeight(targetX, targetY) - camera.location.z) / forward.z;
			targetX += forward.x * distance;
			targetY += forward.y * distance;
		}
		viewer.stackedSounds.update(targetX, targetY, camera.directionX.x, camera.directionX.y);
	}

	/**
	 * Plays the game on to the step without drawing it, as a replay skips ahead: as many steps a frame as fit in
	 * {@link #FAST_FORWARD_FRAME_NANOS}, so the interface still answers. The skipped steps make no missiles, effects
	 * or sounds, only the last one does; the game then goes on drawn, at its own speed. whenReached is told at the
	 * step, or where the game cannot go further (the end of a replay).
	 */
	static void fastForwardTo(final War3MapViewer viewer, final int tick, final FastForwardListener whenReached) {
		viewer.fastForwardTarget = tick;
		viewer.fastForwardReached = whenReached;
		viewer.fastForwardSteps = 0;
		viewer.fastForwardNanos = 0;
	}

	static void fastForwardSteps(final War3MapViewer viewer) {
		final long frameStart = System.nanoTime();
		final long frameEnd = frameStart + War3MapViewer.FAST_FORWARD_FRAME_NANOS;
		viewer.updateTime = 0;
		viewer.beginSimulationFrame();
		// with the thread on the skip makes its steps there one by one, the thread makes none of its own meanwhile
		viewer.beginFastForwardFrame();
		try {
			fastForwardFrame(viewer, frameStart, frameEnd);
		}
		finally {
			viewer.endFastForwardFrame();
		}
	}

	private static void fastForwardFrame(final War3MapViewer viewer, final long frameStart, final long frameEnd) {
		boolean blocked = viewer.worldHidden;
		while (!blocked && (viewer.simulation.getGameTurnTick() < viewer.fastForwardTarget)) {
			if (viewer.gameEnded
					|| (viewer.gameTurnManager.getLatestCompletedTurn() < viewer.simulation.getGameTurnTick())) {
				blocked = true;
				break;
			}
			viewer.renderController
					.setPresenting(!War3MapViewer.NO_RENDER && ((viewer.simulation.getGameTurnTick() + 1) >= viewer.fastForwardTarget));
			viewer.runSimulationUpdate();
			viewer.fastForwardSteps++;
			viewer.runTurnCompleted();
			MapViewerSaves.runPendingSaveRequests(viewer);
			if (viewer.isGamePausedOnSimulation() || (System.nanoTime() >= frameEnd)) {
				break;
			}
		}
		viewer.renderController.setPresenting(!War3MapViewer.NO_RENDER);
		viewer.fastForwardNanos += System.nanoTime() - frameStart;
		if (blocked || (viewer.simulation.getGameTurnTick() >= viewer.fastForwardTarget)) {
			final FastForwardListener reached = viewer.fastForwardReached;
			viewer.fastForwardTarget = -1;
			viewer.fastForwardReached = null;
			if (reached != null) {
				reached.reached(viewer.fastForwardSteps, viewer.fastForwardNanos);
			}
		}
	}

	/**
	 * Plays the first steps of the game while the loading screen is still up. The map's script starts in the first
	 * step and its first moments (the units it makes, their first orders and paths, the first fog update) are the
	 * slowest steps of the game, and played in its first frames they froze the picture.
	 *
	 * @return the number of steps played
	 */
	static int playOpeningSteps(final War3MapViewer viewer, final int count) {
		int played = 0;
		while ((played < count) && !viewer.gameEnded && !viewer.worldHidden && !viewer.isGamePausedOnSimulation()
				&& (viewer.gameTurnManager.getLatestCompletedTurn() >= viewer.simulation.getGameTurnTick())) {
			viewer.simulation.update();
			viewer.gameTurnManager.turnCompleted(viewer.simulation.getGameTurnTick());
			MapViewerSaves.runPendingSaveRequests(viewer);
			played++;
		}
		return played;
	}

	static void stepSimulationWithoutRender(final War3MapViewer viewer) {
		viewer.beginSimulationFrame();
		if (viewer.isSimulationThreaded()) {
			stepThreadedWithoutRender(viewer);
			return;
		}
		if (viewer.isGamePausedOnSimulation()) {
			MapViewerSaves.runPendingSaveRequests(viewer);
			viewer.runWhilePaused();
			return;
		}
		if (viewer.worldHidden || viewer.gameEnded) {
			return;
		}
		viewer.updateTime += Gdx.graphics.getRawDeltaTime() * viewer.timeScale;
		while ((viewer.updateTime >= WarsmashConstants.SIMULATION_STEP_TIME) && !viewer.gameEnded
				&& !viewer.isGamePausedOnSimulation()
				&& (viewer.gameTurnManager.getLatestCompletedTurn() >= viewer.simulation.getGameTurnTick())
				&& !viewer.isGamePausedOnSimulation()) {
			viewer.updateTime -= WarsmashConstants.SIMULATION_STEP_TIME;
			viewer.runSimulationUpdate();
			viewer.runTurnCompleted();
			MapViewerSaves.runPendingSaveRequests(viewer);
		}
		if (viewer.updateTime > (WarsmashConstants.SIMULATION_STEP_TIME * 3)) {
			viewer.gameTurnManager.framesSkipped(viewer.updateTime / WarsmashConstants.SIMULATION_STEP_TIME);
			viewer.updateTime = 0;
		}
	}

	/**
	 * A game not drawn, with the thread on: the frame's steps are made there and waited for, as there is nothing else
	 * to do meanwhile. A pause, a turn not there yet or the game's end stop them there, as the loop above does here.
	 */
	private static void stepThreadedWithoutRender(final War3MapViewer viewer) {
		if (viewer.isGamePausedOnSimulation() || viewer.worldHidden || viewer.gameEnded) {
			viewer.runGrantedSteps(0);
			return;
		}
		viewer.updateTime += Gdx.graphics.getRawDeltaTime() * viewer.timeScale;
		final int due = (int) (viewer.updateTime / WarsmashConstants.SIMULATION_STEP_TIME);
		viewer.updateTime -= due * WarsmashConstants.SIMULATION_STEP_TIME;
		viewer.runGrantedSteps(due);
	}

	/**
	 * Points the world's scenes at the render scale's smaller target; the interface scenes keep the screen.
	 *
	 * @return the scale the world is drawn at, 1 when it is drawn on the screen
	 */
	private static float beginScaledWorld(final War3MapViewer viewer, final boolean allowed) {
		float scale = 1f;
		if (allowed && (viewer.renderScale < 1f)) {
			final Rectangle rect = viewer.worldScene.camera.rect;
			final int width = Math.max(Gdx.graphics.getBackBufferWidth(), (int) Math.ceil(rect.x + rect.width));
			final int height = Math.max(Gdx.graphics.getBackBufferHeight(), (int) Math.ceil(rect.y + rect.height));
			if (viewer.renderScaler.begin(width, height, viewer.renderScale)) {
				scale = viewer.renderScale;
			}
		}
		else if (viewer.renderScale >= 1f) {
			viewer.renderScaler.dispose();
		}
		viewer.worldScene.setRenderScale(scale);
		if (viewer.skyScene != null) {
			viewer.skyScene.setRenderScale(scale);
		}
		return scale;
	}

	static void renderSky(final War3MapViewer viewer) {
		final Camera camera = viewer.worldScene.camera;
		final Camera skyCamera = viewer.skyScene.camera;
		skyCamera.viewport(camera.rect);
		skyCamera.perspective(camera.getFov(), camera.getAspect(), 16f, War3MapViewer.SKY_FAR_PLANE);
		skyCamera.setLocation(camera.location);
		skyCamera.setRotation(camera.rotation);
		skyCamera.update();
		viewer.skyInstance.setLocation(camera.location);
		// The sky scene has no fog of its own; it takes the world fog only when the map draws fog over the sky.
		if (viewer.worldScene.fogSettings.drawOverSky) {
			viewer.skyScene.fogSettings.copyFrom(viewer.worldScene.fogSettings);
		}
		else if (viewer.skyScene.fogSettings.style != FogStyle.NONE) {
			viewer.skyScene.fogSettings.style = FogStyle.NONE;
		}
		viewer.skyScene.startFrame();
		viewer.skyScene.renderOpaque();
		viewer.skyScene.renderTranslucent();
		viewer.gl.glDepthMask(true);
		viewer.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
	}

	/**
	 * UnitAddIndicator: the unit pulses in the color for a moment.
	 */
	static void addIndicator(final War3MapViewer viewer, final CWidget widget, final int red, final int green, final int blue, final int alpha) {
		if (widget instanceof CUnit) {
			final RenderUnit peer = viewer.getRenderPeer((CUnit) widget);
			if (peer != null) {
				final UnitIndicator indicator = new UnitIndicator();
				indicator.unit = peer;
				indicator.color = new float[] { red / 255f, green / 255f, blue / 255f };
				indicator.strength = alpha / 255f;
				viewer.unitIndicators.add(indicator);
			}
		}
	}

	static void updateUnitIndicators(final War3MapViewer viewer, final float deltaTime) {
		if (viewer.unitIndicators.isEmpty()) {
			return;
		}
		// the pulse is around the colour the simulation has for the unit; a frame a step runs, the pulse waits
		viewer.tryWhileSimulationHeld(() -> pulseUnitIndicators(viewer, deltaTime));
	}

	private static void pulseUnitIndicators(final War3MapViewer viewer, final float deltaTime) {
		for (final Iterator<UnitIndicator> iterator = viewer.unitIndicators.iterator(); iterator.hasNext();) {
			final UnitIndicator indicator = iterator.next();
			indicator.elapsed += deltaTime;
			// Pulses around the color the game has for the unit, which can change meanwhile.
			final float[] original = viewer.simulation.getUnitVertexColor(indicator.unit.getSimulationUnit());
			if (indicator.elapsed >= War3MapViewer.UNIT_INDICATOR_SECONDS) {
				indicator.unit.setVertexColoring(viewer, original[0], original[1], original[2]);
				iterator.remove();
			}
			else {
				final float pulse = indicator.strength
						* (float) Math.abs(Math.sin((indicator.elapsed * Math.PI * 2) / War3MapViewer.UNIT_INDICATOR_PULSE_SECONDS));
				final float[] color = indicator.color;
				indicator.unit.setVertexColoring(viewer, original[0] + ((color[0] - original[0]) * pulse),
						original[1] + ((color[1] - original[1]) * pulse),
						original[2] + ((color[2] - original[2]) * pulse));
			}
		}
	}

	static void render(final War3MapViewer viewer) {
		if (viewer.anyReady) {
			final Scene worldScene = viewer.worldScene;

			PerfStats.mark();
			final boolean fullScreenCinematic = (viewer.modelCinematic != null) && !viewer.modelCinematic.isInGame();
			if (viewer.modelCinematic != null) {
				if (viewer.modelCinematic.isDone()) {
					viewer.endModelCinematic();
				}
				else {
					viewer.modelCinematic.placeWorldCamera();
				}
			}
			viewer.startFrame();
			final boolean terrainShown = viewer.scriptUiState.isTerrainShown();
			final boolean unitShadows = !viewer.worldHidden && terrainShown
					&& viewer.dynamicShadowManager.renderUnitShadowMask(viewer.webGL, worldScene, viewer.terrain);
			final float scale = beginScaledWorld(viewer, true);
			worldScene.startFrame();
			if (!viewer.worldHidden && (viewer.skyInstance != null) && viewer.scriptUiState.isSkyBoxShown()) {
				renderSky(viewer);
			}
			// The score screen covers the game: only the interface scenes are drawn.
			PerfStats.end(PerfStats.Section.SHADOWS);
			if (!viewer.worldHidden && terrainShown && (viewer.debugDepth > 1)) {
				viewer.terrain.renderGround(viewer.dynamicShadowManager);
			}
			PerfStats.end(PerfStats.Section.GROUND);
			if (!viewer.worldHidden && terrainShown && (viewer.debugDepth > 2)) {
				viewer.terrain.renderCliffs();
			}
			if (unitShadows) {
				viewer.dynamicShadowManager.compositeUnitShadows(viewer.webGL);
			}
			PerfStats.end(PerfStats.Section.CLIFFS);
			if (!viewer.worldHidden && (viewer.debugDepth > 3)) {
				worldScene.renderOpaque();
			}
			PerfStats.end(PerfStats.Section.OPAQUE);
			if (!viewer.worldHidden && terrainShown && (viewer.debugDepth > 4)) {
				viewer.terrain.renderUberSplats(false);
			}
			PerfStats.end(PerfStats.Section.SPLATS);
			if (!viewer.worldHidden && terrainShown && (viewer.debugDepth > 5)) {
				viewer.terrain.renderWater();
			}
			PerfStats.end(PerfStats.Section.WATER);
			if (!viewer.worldHidden && (viewer.debugDepth > 6)) {
				worldScene.renderTranslucent();
			}
			PerfStats.end(PerfStats.Section.TRANSLUCENT);
			if (!viewer.worldHidden && terrainShown && (viewer.debugDepth > 7)) {
				viewer.terrain.renderUberSplats(true);
			}
			PerfStats.end(PerfStats.Section.SPLATS);
			if (scale < 1f) {
				viewer.renderScaler.end(worldScene.camera.rect, scale);
			}

			final List<Scene> scenes = viewer.scenes;
			for (final Scene scene : scenes) {
				if (!fullScreenCinematic && (scene != worldScene) && (scene != viewer.skyScene)) {
					scene.startFrame();
					if (viewer.debugDepth > 8) {
						scene.renderOpaque();
					}
					if (viewer.debugDepth > 9) {
						scene.renderTranslucent();
					}
				}
			}
			PerfStats.end(PerfStats.Section.INTERFACE);

			// glGetError waits for the GPU on phones, so it is only asked while debugging.
			if (WarsmashConstants.ENABLE_DEBUG) {
				final int glGetError = Gdx.gl.glGetError();
				if (glGetError != GL20.GL_NO_ERROR) {
					throw new IllegalStateException("GL ERROR: " + glGetError);
				}
			}
		}
	}

}
