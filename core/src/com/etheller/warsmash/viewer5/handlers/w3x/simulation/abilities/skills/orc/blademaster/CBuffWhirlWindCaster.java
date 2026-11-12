package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.blademaster;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitEnumFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CSpellDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

public class CBuffWhirlWindCaster extends CBuffTimed {
	private final static CDamageFlags DAMAGE_FLAGS = new CSpellDamageFlags();
	private final CAbilityWhirlWind abilityImmolation;
	private int nextDamageTick;
	private boolean pulsed;
	private final Rectangle recycleRect = new Rectangle();
	private StateModBuff disableSpells;

	public CBuffWhirlWindCaster(final int handleId, final War3ID alias, final CAbilityWhirlWind abilityImmolation,
			final float duration) {
		super(handleId, alias, alias, duration);
		this.abilityImmolation = abilityImmolation;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		unit.getUnitAnimationListener().addSecondaryTag(SecondaryTag.SPIN);
		unit.getUnitAnimationListener().forceResetCurrentAnimation();
		unit.addUnitType(game, CUnitTypeJass.MAGIC_IMMUNE);
		// Measured: a spell ordered during Bladestorm is refused, with no mana spent and Bladestorm going on.
		this.disableSpells = new StateModBuff(StateModBuffType.DISABLE_SPELLS, 1);
		unit.addStateModBuff(this.disableSpells);
		unit.computeUnitState(game, StateModBuffType.DISABLE_SPELLS);
		// Measured: the first pulse lands one interval after the order; the effect itself runs a tick after it.
		this.nextDamageTick = (game.getGameTurnTick()
				+ CTimer.wholeSteps(this.abilityImmolation.getDamageInterval())) - 1;
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		unit.getUnitAnimationListener().removeSecondaryTag(SecondaryTag.SPIN);
		unit.getUnitAnimationListener().forceResetCurrentAnimation();
		unit.removeUnitType(game, CUnitTypeJass.MAGIC_IMMUNE);
		unit.removeStateModBuff(this.disableSpells);
		unit.computeUnitState(game, StateModBuffType.DISABLE_SPELLS);
		if ((unit.getHeroData() != null) && game.castsSpellsByItself(unit.getPlayerIndex())) {
			unit.markBladestormEnded();
		}
	}

	@Override
	public void onTick(final CSimulation game, final CUnit caster) {
		super.onTick(game, caster);
		final int currentTick = game.getGameTurnTick();
		if (currentTick >= this.nextDamageTick) {
			final int delayTicks = CTimer.wholeSteps(this.abilityImmolation.getDamageInterval());
			final float areaOfEffect = CBuffWhirlWindCaster.this.abilityImmolation.getAreaOfEffect();
			this.nextDamageTick = currentTick + delayTicks;
			// Measured: a paused unit takes only the first pulse; its buffs do not tick while paused.
			final boolean firstPulse = !this.pulsed;
			this.pulsed = true;
			this.recycleRect.set(caster.getX() - areaOfEffect, caster.getY() - areaOfEffect, areaOfEffect * 2,
					areaOfEffect * 2);
			game.getWorldCollision().enumUnitsInRect(this.recycleRect, new CUnitEnumFunction() {
				@Override
				public boolean call(final CUnit enumUnit) {
					if (caster.canReach(enumUnit, areaOfEffect) && (firstPulse || !enumUnit.isPaused())
							&& enumUnit.canBeAreaTargetedBy(game, caster, CAreaTargetVisibility.ANY,
							CBuffWhirlWindCaster.this.abilityImmolation.getTargetsAllowed())) {
						enumUnit.damage(game, caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.UNIVERSAL,
								CWeaponSoundTypeJass.WHOKNOWS.name(),
								CBuffWhirlWindCaster.this.abilityImmolation.getDamagePerSecond()
										* CBuffWhirlWindCaster.this.abilityImmolation.getDamageInterval());
					}
					return false;
				}
			});
		}
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {

	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId) {
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		return null;
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		return null;
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder) {
		return null;
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget target,
			final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public void checkCanTargetNoTarget(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTargetCheckReceiver<Void> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		receiver.notAnActiveAbility();
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

}
