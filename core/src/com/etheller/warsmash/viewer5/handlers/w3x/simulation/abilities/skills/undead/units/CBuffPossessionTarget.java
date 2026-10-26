package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * Held on the unit being possessed until the Banshee's spirit reaches it or her channel breaks.
 */
public class CBuffPossessionTarget extends CBuffTimed {
	private final StateModBuff stunBuff = new StateModBuff(StateModBuffType.STUN, 1);
	private final StateModBuff invulnerableBuff;
	private final StateModBuff magicImmuneBuff;

	public CBuffPossessionTarget(final int handleId, final War3ID alias, final boolean invulnerable,
			final boolean magicImmune) {
		super(handleId, alias, alias, 0);
		this.invulnerableBuff = invulnerable ? new StateModBuff(StateModBuffType.INVULNERABLE, 1) : null;
		this.magicImmuneBuff = magicImmune ? new StateModBuff(StateModBuffType.MAGIC_IMMUNE, 1) : null;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		unit.addStateModBuff(this.stunBuff);
		unit.computeUnitState(game, StateModBuffType.STUN);
		if (this.invulnerableBuff != null) {
			unit.addStateModBuff(this.invulnerableBuff);
			unit.computeUnitState(game, StateModBuffType.INVULNERABLE);
		}
		if (this.magicImmuneBuff != null) {
			unit.addStateModBuff(this.magicImmuneBuff);
			unit.computeUnitState(game, StateModBuffType.MAGIC_IMMUNE);
		}
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		unit.removeStateModBuff(this.stunBuff);
		unit.computeUnitState(game, StateModBuffType.STUN);
		if (this.invulnerableBuff != null) {
			unit.removeStateModBuff(this.invulnerableBuff);
			unit.computeUnitState(game, StateModBuffType.INVULNERABLE);
		}
		if (this.magicImmuneBuff != null) {
			unit.removeStateModBuff(this.magicImmuneBuff);
			unit.computeUnitState(game, StateModBuffType.MAGIC_IMMUNE);
		}
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isPositive() {
		return false;
	}
}
