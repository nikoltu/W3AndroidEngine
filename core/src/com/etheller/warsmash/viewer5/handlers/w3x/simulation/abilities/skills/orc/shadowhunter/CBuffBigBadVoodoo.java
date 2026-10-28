package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.shadowhunter;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * Big Bad Voodoo's buffs: the protected units' one makes them invulnerable, the caster's one only marks the channel.
 * Both last until the ability takes them off.
 */
public class CBuffBigBadVoodoo extends CBuffTimed {
	private final StateModBuff invulnerableBuff;

	public CBuffBigBadVoodoo(final int handleId, final War3ID alias, final boolean protecting) {
		super(handleId, alias, alias, 0);
		this.invulnerableBuff = protecting ? new StateModBuff(StateModBuffType.INVULNERABLE, 1) : null;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		if (this.invulnerableBuff != null) {
			unit.addStateModBuff(this.invulnerableBuff);
			unit.computeUnitState(game, StateModBuffType.INVULNERABLE);
		}
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		if (this.invulnerableBuff != null) {
			unit.removeStateModBuff(this.invulnerableBuff);
			unit.computeUnitState(game, StateModBuffType.INVULNERABLE);
		}
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
