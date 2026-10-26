package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CSpellDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * Lightning Shield: every second the shielded unit shocks every other living unit around it, friend or foe.
 */
public class CBuffLightningShield extends CBuffTimed {
	private static final CSpellDamageFlags DAMAGE_FLAGS = new CSpellDamageFlags();
	private static final int TICKS_PER_SECOND = (int) (1.0f / WarsmashConstants.SIMULATION_STEP_TIME);

	private final CUnit caster;
	private final float damagePerSecond;
	private final float areaOfEffect;
	private final boolean positive;
	private int nextDamageTick;

	public CBuffLightningShield(final int handleId, final War3ID alias, final float duration, final CUnit caster,
			final float damagePerSecond, final float areaOfEffect, final boolean positive) {
		super(handleId, alias, alias, duration);
		this.positive = positive;
		this.caster = caster;
		this.damagePerSecond = damagePerSecond;
		this.areaOfEffect = areaOfEffect;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		this.nextDamageTick = game.getGameTurnTick() + TICKS_PER_SECOND;
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if (game.getGameTurnTick() >= this.nextDamageTick) {
			this.nextDamageTick += TICKS_PER_SECOND;
			game.getWorldCollision().enumUnitsInRange(unit.getX(), unit.getY(), this.areaOfEffect, (victim) -> {
				if ((victim != unit) && !victim.isDead() && !victim.isHidden() && !victim.isBuilding()
						&& !victim.isInvulnerable()) {
					victim.damage(game, this.caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.LIGHTNING,
							CWeaponSoundTypeJass.WHOKNOWS.name(), this.damagePerSecond);
					game.createTemporarySpellEffectOnUnit(victim, getAlias(), CEffectType.SPECIAL);
				}
				return false;
			});
		}
		super.onTick(game, unit);
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isPositive() {
		return this.positive;
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
