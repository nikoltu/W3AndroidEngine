package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.alchemist;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;

/**
 * Put on every friendly unit a Healing Spray wave reaches, healed or not; each wave renews it.
 */
public class CBuffHealingSpray extends CBuffTimed {

	public CBuffHealingSpray(final int handleId, final War3ID alias, final float duration) {
		super(handleId, alias, alias, duration);
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isPositive() {
		return true;
	}

	@Override
	public boolean isDispellable() {
		return false;
	}

	@Override
	public boolean isMagic() {
		return true;
	}
}
