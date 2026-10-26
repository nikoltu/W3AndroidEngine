package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision;

import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

/**
 * Shows every unit of a crippled player (CripplePlayer: a melee player without a town hall) to the player holding
 * this modifier.
 */
public class CCrippledPlayerFogModifier extends CFogModifier {
	private static final float REVEAL_RADIUS = 128;
	private final int crippledPlayerIndex;

	public CCrippledPlayerFogModifier(final int crippledPlayerIndex) {
		this.crippledPlayerIndex = crippledPlayerIndex;
	}

	@Override
	public void update(final CSimulation game, final CPlayer player, final PathingGrid pathingGrid,
			final CPlayerFogOfWar fogOfWar) {
		for (final CUnit unit : game.getUnits()) {
			if ((unit.getPlayerIndex() == this.crippledPlayerIndex) && !unit.isDead() && !unit.isHidden()) {
				fogOfWar.setFogStateRadius(pathingGrid, unit.getX(), unit.getY(), REVEAL_RADIUS, CFogState.VISIBLE);
			}
		}
	}
}
