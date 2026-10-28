package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.skills.CBehaviorNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;

/**
 * Shadowmeld: at night (or always, for the permanent item version) the unit fades out of sight while it stands still,
 * and shows again as soon as it moves, attacks, casts or the day breaks. Its Hide order makes it stand still and stop
 * acquiring targets, so that it stays hidden.
 */
public class CAbilityShadowmeld extends CAbilityNoTargetSpellBase {
	private float fadeDuration;
	private float dayNightDuration;
	private float actionDuration;
	private boolean permanent;

	private final StateModBuff invisibility = new StateModBuff(StateModBuffType.INVISIBLE, 1);
	private boolean melded;
	private boolean eligible;
	private boolean lastBlockedByDay = true;
	private int meldTick;

	public CAbilityShadowmeld(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.ambush;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.fadeDuration = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.dayNightDuration = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.actionDuration = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.permanent = worldEditorAbility.getFieldAsBoolean(AbilityFields.DATA_D + level, 0);
		setCastingPrimaryTag(PrimaryTag.STAND);
		setCastingSecondaryTags(SequenceUtils.EMPTY);
	}

	@Override
	public boolean isMagic() {
		return false;
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		// Measured: the fade starts when the order is given, not at the cast point, and takes the fade time alone,
		// also right after moving or at the start.
		if ((orderId == OrderIds.ambush) && (this.permanent || game.isNight()) && !this.melded) {
			this.eligible = true;
			this.meldTick = game.getGameTurnTick() + (int) (this.fadeDuration / WarsmashConstants.SIMULATION_STEP_TIME);
		}
		return super.checkBeforeQueue(game, caster, playerIndex, orderId, autoOrder, target);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		return true;
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		final boolean dark = this.permanent || game.isNight();
		final boolean acting = isActing(unit);
		final boolean nowEligible = dark && !acting && !unit.isDead();
		final int tick = game.getGameTurnTick();
		if (nowEligible && !this.eligible) {
			final float delay;
			if (this.lastBlockedByDay) {
				delay = Math.max(this.fadeDuration, this.dayNightDuration);
			}
			else {
				delay = this.actionDuration + this.fadeDuration;
			}
			this.meldTick = tick + (int) (delay / WarsmashConstants.SIMULATION_STEP_TIME);
		}
		else if (!nowEligible) {
			this.lastBlockedByDay = !dark;
			if (this.melded) {
				unmeld(game, unit);
			}
		}
		this.eligible = nowEligible;
		if (this.eligible && !this.melded && (tick >= this.meldTick)) {
			this.melded = true;
			unit.addStateModBuff(this.invisibility);
			unit.computeUnitState(game, StateModBuffType.INVISIBLE);
		}
	}

	private boolean isActing(final CUnit unit) {
		final CBehavior behavior = unit.getCurrentBehavior();
		if ((behavior == null) || (behavior.getBehaviorCategory() == CBehaviorCategory.IDLE)) {
			return false;
		}
		return !((behavior instanceof CBehaviorNoTargetSpellBase)
				&& (((CBehaviorNoTargetSpellBase) behavior).getAbility() == this));
	}

	private void unmeld(final CSimulation game, final CUnit unit) {
		this.melded = false;
		unit.removeStateModBuff(this.invisibility);
		unit.computeUnitState(game, StateModBuffType.INVISIBLE);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		if (this.melded) {
			unmeld(game, unit);
		}
		this.eligible = false;
		this.lastBlockedByDay = true;
	}

	public boolean isMelded() {
		return this.melded;
	}
}
