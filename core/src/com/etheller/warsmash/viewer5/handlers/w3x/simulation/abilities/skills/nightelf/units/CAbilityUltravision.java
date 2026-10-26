package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;

/**
 * Ultravision: once its research is done the unit sees as far at night as during the day.
 */
public class CAbilityUltravision extends CAbilityPassiveSpellBase {
	private boolean active;

	public CAbilityUltravision(final int handleId, final War3ID code, final War3ID alias) {
		super(handleId, code, alias);
		setIconShowing(false);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		if (!this.active) {
			this.active = true;
			unit.addUltravision();
		}
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		if (this.active) {
			this.active = false;
			unit.removeUltravision();
		}
	}
}
