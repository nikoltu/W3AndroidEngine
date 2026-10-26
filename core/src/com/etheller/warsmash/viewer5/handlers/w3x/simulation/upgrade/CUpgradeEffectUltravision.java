package com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/**
 * rauv: the unit keeps its day sight radius at night (Glyph of Ultravision).
 */
public class CUpgradeEffectUltravision implements CUpgradeEffect {
	@Override
	public void apply(final CSimulation simulation, final CUnit unit, final int level) {
		unit.addUltravision();
	}

	@Override
	public void unapply(final CSimulation simulation, final CUnit unit, final int level) {
		unit.removeUltravision();
	}
}
