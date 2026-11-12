package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.bloodmage.phoenix;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CSpellDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * Burning left by Phoenix Fire: damage per second while the buff lasts.
 */
public class CBuffPhoenixFire extends CBuffTimed {
	private static final CSpellDamageFlags DAMAGE_FLAGS = new CSpellDamageFlags();
	private static final float BURN_INTERVAL = 1.0f;

	private final CUnit source;
	private final float damagePerSecond;
	private int nextBurnTick;
	private int burnsLeft;

	public CBuffPhoenixFire(final int handleId, final War3ID alias, final float duration, final CUnit source,
			final float damagePerSecond) {
		super(handleId, alias, alias, duration);
		this.source = source;
		this.damagePerSecond = damagePerSecond;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		this.nextBurnTick = game.getGameTurnTick();
		this.burnsLeft = Math.max(1, StrictMath.round(getDurationMax() / BURN_INTERVAL));
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if ((this.damagePerSecond > 0) && (this.burnsLeft > 0) && (game.getGameTurnTick() >= this.nextBurnTick)) {
			this.nextBurnTick += ticks(BURN_INTERVAL);
			this.burnsLeft--;
			unit.damage(game, this.source, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.FIRE,
					CWeaponSoundTypeJass.WHOKNOWS.name(), this.damagePerSecond * BURN_INTERVAL);
			if (unit.isDead()) {
				return;
			}
		}
		super.onTick(game, unit);
	}

	private static int ticks(final float seconds) {
		return StrictMath.round(seconds / WarsmashConstants.SIMULATION_STEP_TIME);
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isPositive() {
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
