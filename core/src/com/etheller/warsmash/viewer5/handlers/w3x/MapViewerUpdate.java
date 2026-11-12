package com.etheller.warsmash.viewer5.handlers.w3x;

import java.util.Iterator;
import java.util.List;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector3;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.Camera;
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
		boolean blocked = viewer.worldHidden;
		while (!blocked && (viewer.simulation.getGameTurnTick() < viewer.fastForwardTarget)) {
			if (viewer.gameEnded
					|| (viewer.gameTurnManager.getLatestCompletedTurn() < viewer.simulation.getGameTurnTick())) {
				blocked = true;
				break;
			}
			viewer.renderController
					.setPresenting(!War3MapViewer.NO_RENDER && ((viewer.simulation.getGameTurnTick() + 1) >= viewer.fastForwardTarget));
			viewer.simulation.update();
			viewer.fastForwardSteps++;
			viewer.gameTurnManager.turnCompleted(viewer.simulation.getGameTurnTick());
			MapViewerSaves.runPendingSaveRequests(viewer);
			if (viewer.isGamePaused() || (System.nanoTime() >= frameEnd)) {
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

	static void stepSimulationWithoutRender(final War3MapViewer viewer) {
		if (viewer.isGamePaused()) {
			MapViewerSaves.runPendingSaveRequests(viewer);
			return;
		}
		if (viewer.worldHidden || viewer.gameEnded) {
			return;
		}
		viewer.updateTime += Gdx.graphics.getRawDeltaTime() * viewer.timeScale;
		while ((viewer.updateTime >= WarsmashConstants.SIMULATION_STEP_TIME) && !viewer.gameEnded
				&& (viewer.gameTurnManager.getLatestCompletedTurn() >= viewer.simulation.getGameTurnTick())) {
			viewer.updateTime -= WarsmashConstants.SIMULATION_STEP_TIME;
			viewer.simulation.update();
			viewer.gameTurnManager.turnCompleted(viewer.simulation.getGameTurnTick());
			MapViewerSaves.runPendingSaveRequests(viewer);
		}
		if (viewer.updateTime > (WarsmashConstants.SIMULATION_STEP_TIME * 3)) {
			viewer.gameTurnManager.framesSkipped(viewer.updateTime / WarsmashConstants.SIMULATION_STEP_TIME);
			viewer.updateTime = 0;
		}
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
			viewer.startFrame();
			worldScene.startFrame();
			if (viewer.modelCinematic != null) {
				if (!viewer.modelCinematic.isDone()) {
					viewer.modelCinematic.render();
					return;
				}
				viewer.endModelCinematic();
			}
			if (!viewer.worldHidden && (viewer.skyInstance != null)) {
				renderSky(viewer);
			}
			// The score screen covers the game: only the interface scenes are drawn.
			if (!viewer.worldHidden && (viewer.debugDepth > 0)) {
				worldScene.renderOpaque(viewer.dynamicShadowManager, viewer.webGL);
			}
			PerfStats.end(PerfStats.Section.SHADOWS);
			if (!viewer.worldHidden && (viewer.debugDepth > 1)) {
				viewer.terrain.renderGround(viewer.dynamicShadowManager);
			}
			PerfStats.end(PerfStats.Section.GROUND);
			if (!viewer.worldHidden && (viewer.debugDepth > 2)) {
				viewer.terrain.renderCliffs();
			}
			PerfStats.end(PerfStats.Section.CLIFFS);
			if (!viewer.worldHidden && (viewer.debugDepth > 3)) {
				worldScene.renderOpaque();
			}
			PerfStats.end(PerfStats.Section.OPAQUE);
			if (!viewer.worldHidden && (viewer.debugDepth > 4)) {
				viewer.terrain.renderUberSplats(false);
			}
			PerfStats.end(PerfStats.Section.SPLATS);
			if (!viewer.worldHidden && (viewer.debugDepth > 5)) {
				viewer.terrain.renderWater();
			}
			PerfStats.end(PerfStats.Section.WATER);
			if (!viewer.worldHidden && (viewer.debugDepth > 6)) {
				worldScene.renderTranslucent();
			}
			PerfStats.end(PerfStats.Section.TRANSLUCENT);
			if (!viewer.worldHidden && (viewer.debugDepth > 7)) {
				viewer.terrain.renderUberSplats(true);
			}
			PerfStats.end(PerfStats.Section.SPLATS);

			final List<Scene> scenes = viewer.scenes;
			for (final Scene scene : scenes) {
				if ((scene != worldScene) && (scene != viewer.skyScene)) {
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
