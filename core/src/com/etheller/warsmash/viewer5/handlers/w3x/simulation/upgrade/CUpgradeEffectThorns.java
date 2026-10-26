package com.etheller.warsmash.viewer5.handlers.w3x.simulation.upgrade;

import java.util.HashMap;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuffType;

/**
 * Damage returned to melee attackers (rspi flat amount, rspp share of the attacker's damage), as Spiked
 * Barricades does.
 */
public class CUpgradeEffectThorns implements CUpgradeEffect {
	private final float base;
	private final float mod;
	private final NonStackingStatBuffType buffType;
	private final String stackingKey;
	private final Map<Integer, NonStackingStatBuff> levelToBuff = new HashMap<>();

	public CUpgradeEffectThorns(final float base, final float mod, final boolean percentage,
			final String stackingKey) {
		this.base = base;
		this.mod = mod;
		this.buffType = percentage ? NonStackingStatBuffType.THORNSPCT : NonStackingStatBuffType.THORNS;
		this.stackingKey = stackingKey;
	}

	private NonStackingStatBuff getBuff(final int level) {
		NonStackingStatBuff buff = this.levelToBuff.get(level);
		if (buff == null) {
			buff = new NonStackingStatBuff(this.buffType, this.stackingKey,
					Util.levelValue(this.base, this.mod, level - 1));
			this.levelToBuff.put(level, buff);
		}
		return buff;
	}

	@Override
	public void apply(final CSimulation simulation, final CUnit unit, final int level) {
		unit.addNonStackingStatBuff(simulation, getBuff(level));
	}

	@Override
	public void unapply(final CSimulation simulation, final CUnit unit, final int level) {
		unit.removeNonStackingStatBuff(simulation, getBuff(level));
	}
}
