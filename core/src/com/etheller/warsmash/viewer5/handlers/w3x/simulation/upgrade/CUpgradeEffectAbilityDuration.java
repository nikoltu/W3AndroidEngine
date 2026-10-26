package com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade;

import java.util.Map;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;

/**
 * Lengthens what an ability summons or applies (rrai: Skeletal Mastery adds to the Raise Dead skeleton lifetime).
 */
public class CUpgradeEffectAbilityDuration implements CUpgradeEffect {
	private final float base;
	private final float mod;
	private final War3ID rawcode;

	public CUpgradeEffectAbilityDuration(final float base, final float mod, final War3ID rawcode) {
		this.base = base;
		this.mod = mod;
		this.rawcode = rawcode;
	}

	@Override
	public void apply(final CSimulation simulation, final CUnit unit, final int level) {
		adjust(unit, Util.levelValue(this.base, this.mod, level - 1));
	}

	@Override
	public void unapply(final CSimulation simulation, final CUnit unit, final int level) {
		adjust(unit, -Util.levelValue(this.base, this.mod, level - 1));
	}

	private void adjust(final CUnit unit, final float delta) {
		for (final CAbility ability : unit.getAbilities()) {
			if ((ability instanceof AbilityBuilderAbility) && this.rawcode.equals(ability.getCode())) {
				final Map<String, Object> localStore = ((AbilityBuilderAbility) ability).getLocalStore();
				final Object current = localStore.get(ABLocalStoreKeys.DURATIONBONUS);
				localStore.put(ABLocalStoreKeys.DURATIONBONUS, (current == null ? 0 : (Float) current) + delta);
			}
		}
	}
}
