package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackFinalDamageTakenModificationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;

public class CBuffSpiritLink extends CBuffTimed implements CUnitAttackFinalDamageTakenModificationListener {
	private final CSpiritLinkGroup group;

	public CBuffSpiritLink(final int handleId, final War3ID alias, final float duration,
			final CSpiritLinkGroup group) {
		super(handleId, alias, alias, duration);
		this.group = group;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		this.group.add(unit);
		unit.addFinalDamageTakenModificationListener(this);
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		this.group.remove(unit);
		unit.removeFinalDamageTakenModificationListener(this);
	}

	@Override
	public float onDamage(final CSimulation simulation, final CUnit attacker, final CUnit target,
			final CDamageFlags flags, final CAttackType attackType, final CDamageType damageType,
			final float previousDamage) {
		return this.group.distribute(simulation, attacker, target, previousDamage);
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isDispellable() {
		return true;
	}

	@Override
	public boolean isMagic() {
		return true;
	}
}
