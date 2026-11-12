package com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CGenericDamageFlags;

/** The delayed damage of a script's UnitDamagePoint. */
public class CTimerDamagePoint extends CTimer {
	private final CUnit source;
	private final float x;
	private final float y;
	private final float radius;
	private final float amount;
	private final boolean attack;
	private final boolean ranged;
	private final CAttackType attackType;
	private final CDamageType damageType;
	private final String weaponType;

	public CTimerDamagePoint(final CUnit source, final float x, final float y, final float radius, final float amount,
			final boolean attack, final boolean ranged, final CAttackType attackType, final CDamageType damageType,
			final String weaponType) {
		this.source = source;
		this.x = x;
		this.y = y;
		this.radius = radius;
		this.amount = amount;
		this.attack = attack;
		this.ranged = ranged;
		this.attackType = attackType;
		this.damageType = damageType;
		this.weaponType = weaponType;
	}

	/** Damages every unit within the radius of its edge (measured on 1.29), the source and its allies included. */
	public void damage(final CSimulation simulation) {
		final List<CUnit> targets = new ArrayList<>();
		simulation.getWorldCollision().enumUnitsInRange(this.x, this.y, this.radius, (unit) -> {
			targets.add(unit);
			return false;
		});
		for (final CUnit target : targets) {
			target.damage(simulation, this.source, new CGenericDamageFlags(this.attack, this.ranged), this.attackType,
					this.damageType, this.weaponType, this.amount);
		}
	}

	@Override
	public void onFire(final CSimulation simulation) {
		damage(simulation);
	}
}
