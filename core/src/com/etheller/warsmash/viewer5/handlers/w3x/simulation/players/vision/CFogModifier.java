package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision;

import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

public abstract class CFogModifier {
	// Measured on 1.29: the whole fog (units, modifiers, shared vision) is recomputed every 0.4 s from game start.
	public static final float FOG_UPDATE_TIME = 0.4f;

	/** A vision radius squared, in fog grid cells. */
	protected static float gridRadiusSq(final float radius) {
		return (radius * radius) / (CPlayerFogOfWar.GRID_STEP * CPlayerFogOfWar.GRID_STEP);
	}

	private boolean sharedVision = true;

	public void setEnabled(final boolean enabled) {
	}

	public void setSharedVision(final boolean sharedVision) {
		this.sharedVision = sharedVision;
	}

	public boolean isSharedVision() {
		return this.sharedVision;
	}

	/** Whether a modifier made with the shared vision flag acts on the allies as well as on its owner. */
	public boolean isSharedWithAllies() {
		return false;
	}

	/**
	 * Measured on 1.29: a modifier sets its state once when it is created, started or not.
	 */
	public void applyNow(final CSimulation game, final CPlayer player) {
	}

	public void onAdd(final CSimulation game, final CPlayer player) {
	}

	public void onRemove(final CSimulation game, final CPlayer player) {
	}

	public abstract void update(final CSimulation game, final CPlayer player, final PathingGrid pathingGrid,
			final CPlayerFogOfWar fogOfWar);
}
