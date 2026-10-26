package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;

/**
 * Shown on the devouring unit while it digests; it lasts until the devoured unit dies or is freed.
 */
public class CBuffDigesting extends CBuffTimed {

	public CBuffDigesting(final int handleId, final War3ID alias) {
		super(handleId, alias, alias, 0);
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onTick(final CSimulation game, final CUnit caster) {
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isDispellable() {
		return false;
	}
}
