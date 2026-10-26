package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;

public class CBuffManaFlare extends CBuffTimed {
	private SimulationRenderComponent sound;

	public CBuffManaFlare(final int handleId, final War3ID alias, final float duration) {
		super(handleId, alias, alias, duration);
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		this.sound = game.unitLoopSoundEffectEvent(unit, getAlias());
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		if (this.sound != null) {
			this.sound.remove();
			this.sound = null;
		}
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}
}
