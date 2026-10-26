package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

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
 * Disease from a Disease Cloud: damage every second that leaves the unit at no less than 1 life.
 */
public class CBuffDisease extends CBuffTimed {
	private static final CSpellDamageFlags DAMAGE_FLAGS = new CSpellDamageFlags();
	private static final int TICKS_PER_SECOND = (int) (1.0f / WarsmashConstants.SIMULATION_STEP_TIME);

	static {
		DAMAGE_FLAGS.setNonlethal(true);
	}

	private final float duration;
	private CUnit source;
	private float damagePerSecond;
	private int expireTick;
	private int nextDamageTick;

	public CBuffDisease(final int handleId, final War3ID alias, final float duration, final CUnit source,
			final float damagePerSecond) {
		super(handleId, alias, alias, duration);
		this.duration = duration;
		this.source = source;
		this.damagePerSecond = damagePerSecond;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		this.expireTick = game.getGameTurnTick() + (int) (this.duration / WarsmashConstants.SIMULATION_STEP_TIME);
		this.nextDamageTick = game.getGameTurnTick() + TICKS_PER_SECOND;
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
	}

	public void refresh(final CSimulation game, final CUnit source, final float damagePerSecond) {
		this.source = source;
		this.damagePerSecond = Math.max(this.damagePerSecond, damagePerSecond);
		this.expireTick = game.getGameTurnTick() + (int) (this.duration / WarsmashConstants.SIMULATION_STEP_TIME);
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		final int tick = game.getGameTurnTick();
		if (tick >= this.expireTick) {
			unit.remove(game, this);
			return;
		}
		if (tick >= this.nextDamageTick) {
			this.nextDamageTick += TICKS_PER_SECOND;
			if (unit.getLife() > 1) {
				unit.damage(game, this.source, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.DISEASE,
						CWeaponSoundTypeJass.WHOKNOWS.name(), this.damagePerSecond);
			}
		}
	}

	@Override
	public float getDurationRemaining(final CSimulation game, final CUnit unit) {
		return Math.max(0, this.expireTick - game.getGameTurnTick()) * WarsmashConstants.SIMULATION_STEP_TIME;
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
