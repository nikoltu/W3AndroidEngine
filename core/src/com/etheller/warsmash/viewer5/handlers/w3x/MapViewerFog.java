package com.etheller.warsmash.viewer5.handlers.w3x;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderDoodad;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

/**
 * Takes the viewing player's fog of war to the screen once a frame: the ground fog texture and the fog shading of
 * the doodads. It works from a copy of the cells, and the GPU is only touched when the fog or its shades changed.
 */
final class MapViewerFog {
	/** Doodads are stepped at least this often (in frames) even when the fog did not change. */
	private static final int DOODAD_CHECK_FRAMES = 60;

	private final War3MapViewer viewer;
	private byte[] states = new byte[0];
	private byte[] lastStates = new byte[0];
	private final CFogState[] stateByCell = new CFogState[256];
	private final byte[] shadeByCell = new byte[256];
	private final byte[] lastShadeByCell = new byte[256];
	private final CFogState[] lastStateByCell = new CFogState[256];
	private final byte[] shadeByState = new byte[CFogState.VALUES.length];
	private CPlayerFogOfWar lastFogOfWar;
	private int version;
	private int doodadFrames;
	private boolean doodadsFading;

	MapViewerFog(final War3MapViewer viewer) {
		this.viewer = viewer;
	}

	void update() {
		refresh(false);
	}

	/**
	 * A loaded game has no fade to continue: the ground and the doodads take the shade of the fog state they have
	 * now, so they match while the game is still paused.
	 */
	void settle() {
		while (refresh(true)) {
			// each pass moves the drawn fog one fade step
		}
	}

	/** @return whether the ground fog is still fading */
	private boolean refresh(final boolean snap) {
		final CPlayerFogOfWar fogOfWar = this.viewer.getFogOfWar();
		final ByteBuffer source = fogOfWar.getFogOfWarBuffer().duplicate();
		source.clear();
		if (this.states.length != source.remaining()) {
			this.states = new byte[source.remaining()];
			this.lastStates = new byte[this.states.length];
			this.version++;
		}
		source.get(this.states);
		MapViewerVision.fillFogTables(this.viewer, this.stateByCell, this.shadeByCell);
		boolean moved = (fogOfWar != this.lastFogOfWar) || !Arrays.equals(this.states, this.lastStates)
				|| !Arrays.equals(this.shadeByCell, this.lastShadeByCell)
				|| !Arrays.equals(this.stateByCell, this.lastStateByCell);
		if (moved) {
			System.arraycopy(this.states, 0, this.lastStates, 0, this.states.length);
			System.arraycopy(this.shadeByCell, 0, this.lastShadeByCell, 0, 256);
			System.arraycopy(this.stateByCell, 0, this.lastStateByCell, 0, 256);
			this.lastFogOfWar = fogOfWar;
			this.version++;
		}
		final boolean fogChanged = this.viewer.terrain.uploadFogOfWar(this.states, this.shadeByCell, this.version);
		this.viewer.terrain.updateShownBlight();
		// Doodads fade with the ground fog, so they only need the step while it changes; every so often they are
		// checked anyway.
		// They start black (masked) and are still fading when the ground has settled, so they keep stepping until
		// they have reached their shade.
		if (fogChanged || moved || this.doodadsFading || snap || ((++this.doodadFrames % DOODAD_CHECK_FRAMES) == 0)) {
			this.doodadsFading = updateDoodads(fogOfWar, snap);
		}
		return fogChanged;
	}

	/** @return whether a doodad is still fading toward its fog state */
	private boolean updateDoodads(final CPlayerFogOfWar fogOfWar, final boolean snap) {
		final List<RenderDoodad> doodads = this.viewer.decals;
		final int doodadCount = doodads.size();
		if (doodadCount == 0) {
			return false;
		}
		final PathingGrid pathingGrid = this.viewer.simulation.getPathingGrid();
		for (final CFogState state : CFogState.VALUES) {
			this.shadeByState[state.ordinal()] = this.viewer.getFogShade(state.getMask());
		}
		final int fogWidth = fogOfWar.getWidth();
		boolean fading = false;
		for (int i = 0; i < doodadCount; i++) {
			fading |= doodads.get(i).updateFog(this.states, fogWidth, pathingGrid, this.stateByCell, this.shadeByState,
					snap);
		}
		return fading;
	}
}
