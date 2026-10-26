package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * Phase Shift: the unit is out of existence; it cannot be harmed or targeted and its model is not drawn.
 */
public class CBuffPhaseShift extends CBuffTimed {
	private final StateModBuff invulnerableBuff = new StateModBuff(StateModBuffType.INVULNERABLE, 1);
	private float[] savedVertexColor;

	public CBuffPhaseShift(final int handleId, final War3ID alias, final float duration) {
		super(handleId, alias, alias, duration);
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		unit.addStateModBuff(this.invulnerableBuff);
		unit.computeUnitState(game, StateModBuffType.INVULNERABLE);
		final float[] vertexColor = game.getUnitVertexColor(unit);
		if (vertexColor != null) {
			this.savedVertexColor = vertexColor.clone();
			game.changeUnitVertexColor(unit, vertexColor[0], vertexColor[1], vertexColor[2], 0);
		}
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		unit.removeStateModBuff(this.invulnerableBuff);
		unit.computeUnitState(game, StateModBuffType.INVULNERABLE);
		if (this.savedVertexColor != null) {
			game.changeUnitVertexColor(unit, this.savedVertexColor[0], this.savedVertexColor[1],
					this.savedVertexColor[2], this.savedVertexColor[3]);
			this.savedVertexColor = null;
		}
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}
}
