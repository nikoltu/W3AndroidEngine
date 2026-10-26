package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CSpellDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

public class CSpiritLinkGroup {
	private static final CDamageFlags DAMAGE_FLAGS = new CSpellDamageFlags();

	private final float distributedDamageFactor;
	private final List<CUnit> members = new ArrayList<>();
	// Shared damage must not be shared again by the units that receive it.
	private boolean distributing;

	public CSpiritLinkGroup(final float distributedDamageFactor) {
		this.distributedDamageFactor = distributedDamageFactor;
	}

	public void add(final CUnit unit) {
		this.members.add(unit);
	}

	public void remove(final CUnit unit) {
		this.members.remove(unit);
	}

	public float distribute(final CSimulation simulation, final CUnit source, final CUnit target,
			final float damage) {
		if (this.distributing || (damage <= 0)) {
			return damage;
		}
		final List<CUnit> receivers = new ArrayList<>();
		for (final CUnit member : this.members) {
			if ((member != target) && !member.isDead()) {
				receivers.add(member);
			}
		}
		if (receivers.isEmpty()) {
			return damage;
		}
		final float shared = damage * this.distributedDamageFactor;
		final float perUnit = shared / receivers.size();
		this.distributing = true;
		try {
			for (final CUnit receiver : receivers) {
				receiver.damage(simulation, source, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.SPIRIT_LINK,
						CWeaponSoundTypeJass.WHOKNOWS.name(), perUnit);
			}
		}
		finally {
			this.distributing = false;
		}
		return damage - shared;
	}
}
