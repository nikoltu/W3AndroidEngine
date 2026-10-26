package com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.template.CAbilityAbilityBuilderStatAuraTemplate;

/**
 * Raises the first data field of a stat aura (raud), as Improved War Drums does for the War Drums damage bonus.
 */
public class CUpgradeEffectAuraData implements CUpgradeEffect {
	private final float base;
	private final float mod;
	private final War3ID rawcode;

	public CUpgradeEffectAuraData(final float base, final float mod, final War3ID rawcode) {
		this.base = base;
		this.mod = mod;
		this.rawcode = rawcode;
	}

	@Override
	public void apply(final CSimulation simulation, final CUnit unit, final int level) {
		adjust(simulation, unit, Util.levelValue(this.base, this.mod, level - 1));
	}

	@Override
	public void unapply(final CSimulation simulation, final CUnit unit, final int level) {
		adjust(simulation, unit, -Util.levelValue(this.base, this.mod, level - 1));
	}

	private void adjust(final CSimulation simulation, final CUnit unit, final float delta) {
		for (final CAbility ability : unit.getAbilities()) {
			if ((ability instanceof CAbilityAbilityBuilderStatAuraTemplate)
					&& (this.rawcode.equals(ability.getAlias()) || this.rawcode.equals(ability.getCode()))) {
				((CAbilityAbilityBuilderStatAuraTemplate) ability).addDataBonus(simulation, unit, 0, delta);
			}
		}
	}
}
