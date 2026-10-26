package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentModel;

/**
 * Cyclone: the unit is lifted into a whirlwind, spinning; it cannot move, attack or cast, and nobody can attack it
 * or cast on it until it lands.
 */
public class CBuffCyclone extends CBuffTimed {
	private static final float LIFT_HEIGHT = 200f;
	private static final float LIFT_TIME = 0.5f;
	private static final float SPIN_DEGREES_PER_SECOND = 540f;

	private final StateModBuff stunBuff = new StateModBuff(StateModBuffType.STUN, 1);
	private final StateModBuff invulnerableBuff = new StateModBuff(StateModBuffType.INVULNERABLE, 1);
	private final boolean dispellable;
	private float baseFlyHeight;
	private float lift;
	private SimulationRenderComponentModel whirlwind;
	private SimulationRenderComponent sound;

	public CBuffCyclone(final int handleId, final War3ID alias, final float duration, final boolean dispellable) {
		super(handleId, alias, alias, duration);
		this.dispellable = dispellable;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		unit.addStateModBuff(this.stunBuff);
		unit.computeUnitState(game, StateModBuffType.STUN);
		unit.addStateModBuff(this.invulnerableBuff);
		unit.computeUnitState(game, StateModBuffType.INVULNERABLE);
		this.baseFlyHeight = unit.getFlyHeight();
		this.lift = 0;
		this.whirlwind = game.spawnSpellEffectOnPoint(unit.getX(), unit.getY(), unit.getFacing(), getAlias(),
				CEffectType.EFFECT, 0);
		this.sound = game.unitLoopSoundEffectEvent(unit, getAlias());
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		unit.removeStateModBuff(this.stunBuff);
		unit.computeUnitState(game, StateModBuffType.STUN);
		unit.removeStateModBuff(this.invulnerableBuff);
		unit.computeUnitState(game, StateModBuffType.INVULNERABLE);
		unit.setFlyHeight(this.baseFlyHeight);
		if (this.whirlwind != null) {
			this.whirlwind.remove();
			this.whirlwind = null;
		}
		if (this.sound != null) {
			this.sound.remove();
			this.sound = null;
		}
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if (this.lift < LIFT_HEIGHT) {
			this.lift = Math.min(LIFT_HEIGHT,
					this.lift + ((LIFT_HEIGHT / LIFT_TIME) * WarsmashConstants.SIMULATION_STEP_TIME));
			unit.setFlyHeight(this.baseFlyHeight + this.lift);
		}
		unit.setFacing(unit.getFacing() + (SPIN_DEGREES_PER_SECOND * WarsmashConstants.SIMULATION_STEP_TIME));
		super.onTick(game, unit);
	}

	@Override
	public boolean isDispellable() {
		return this.dispellable;
	}

	@Override
	public boolean isMagic() {
		return true;
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
