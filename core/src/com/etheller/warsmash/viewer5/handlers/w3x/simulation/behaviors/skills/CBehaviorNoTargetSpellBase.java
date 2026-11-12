package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.skills;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CReleaseCheats;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CExactExpiry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

public class CBehaviorNoTargetSpellBase implements CBehavior {
	protected final CUnit unit;
	protected final CAbilitySpellBase ability;
	private int castStartTick = 0;
	private boolean doneEffect = false;
	private boolean channeling = true;
	private boolean castEventsFired = false;
	private final CExactExpiry castPoint = new CExactExpiry();
	private boolean castPointStarted = false;
	/** Null in games saved before the backswing ended at its exact time; those end on whole steps. */
	private CExactExpiry backswing;

	public CBehaviorNoTargetSpellBase(final CUnit unit, final CAbilitySpellBase ability) {
		this.unit = unit;
		this.ability = ability;
	}

	public CBehaviorNoTargetSpellBase reset() {
		this.castStartTick = 0;
		this.castPointStarted = false;
		this.doneEffect = false;
		this.channeling = true;
		this.castEventsFired = false;
		return this;
	}

	@Override
	public CBehavior update(final CSimulation simulation) {
		this.unit.getUnitAnimationListener().playAnimation(false, this.ability.getCastingPrimaryTag(),
				this.ability.getCastingSecondaryTags(), 1.0f, true);
		if (this.castStartTick == 0) {
			this.castStartTick = simulation.getGameTurnTick();
			if (!this.castEventsFired) {
				this.castEventsFired = true;
				final CBehavior prevBeh = this.unit.getCurrentBehavior();
				this.unit.fireSpellEvents(simulation, JassGameEventsWar3.EVENT_UNIT_SPELL_CHANNEL, this.ability, null);
				this.unit.fireSpellEvents(simulation, JassGameEventsWar3.EVENT_UNIT_SPELL_CAST, this.ability, null);
				if (this.unit.getCurrentBehavior() != prevBeh) {
					return this.unit.getCurrentBehavior();
				}
			}
		}
		if (!this.castPointStarted) {
			this.castPointStarted = true;
			this.castPoint.start(simulation, castPointTime());
			// The backswing follows the cast point (1.29: effect at castpt, the cast ends castbsw later, at its exact
			// time and not on a step).
			if (this.backswing == null) {
				this.backswing = new CExactExpiry();
			}
			this.backswing.start(simulation, backswingEndTime());
		}
		if (this.doneEffect || this.castPoint.check(simulation, this::effectAtCastPoint)) {
			final CBehavior next = effect(simulation);
			if (next != null) {
				return next;
			}
		}
		if (this.doneEffect && !this.channeling && backswingOver(simulation)) {
			return this.unit.pollNextOrderBehavior(simulation);
		}
		return this;
	}

	private float castPointTime() {
		return this.ability.isInstantCast() ? 0 : this.unit.getUnitType().getCastPoint();
	}

	private float backswingEndTime() {
		return this.ability.isInstantCast() ? 0
				: castPointTime() + this.unit.getUnitType().getCastBackswingPoint();
	}

	private boolean backswingOver(final CSimulation simulation) {
		if (this.backswing == null) {
			return (simulation.getGameTurnTick() - this.castStartTick) >= CTimer.wholeSteps(backswingEndTime());
		}
		return this.backswing.check(simulation, this::endBackswingInStep);
	}

	private void endBackswingInStep(final CSimulation simulation) {
		if (this.doneEffect && !this.channeling && (this.unit.getCurrentBehavior() == this) && !this.unit.isDead()) {
			this.unit.beginBehavior(simulation, this.unit.pollNextOrderBehavior(simulation));
		}
	}

	/** The effect at the cast point, then a channel tick; a behavior to switch to, or null. */
	private CBehavior effect(final CSimulation simulation) {
		boolean wasEffectDone = this.doneEffect;
		boolean wasChanneling = this.channeling;
		if (!wasEffectDone) {
			this.doneEffect = true;
			final int manaCost = CReleaseCheats.manaCost(simulation, this.unit, this.ability.getManaCost());
			if (this.unit.getMana() < manaCost) {
				simulation.getCommandErrorListener().showInterfaceError(this.unit.getPlayerIndex(), CommandStringErrorKeys.NOT_ENOUGH_MANA);
				return this.unit.pollNextOrderBehavior(simulation);
			}
			this.unit.beginCooldown(simulation, this.ability.getCode(), this.ability.getCooldown());
			// 1.29: the effect event still sees the mana from before the cast; the cost is paid after it.
			this.unit.fireSpellEvents(simulation, JassGameEventsWar3.EVENT_UNIT_SPELL_EFFECT, this.ability, null);
			this.unit.setMana(Math.max(0, this.unit.getMana() - manaCost));
			this.channeling = this.ability.doEffect(simulation, this.unit, null);
			if (this.unit.getInventoryData() != null) {
				this.unit.getInventoryData().onItemAbilityEffect(simulation, this.unit, this.ability);
			}
			if (this.channeling) {
				simulation.unitLoopSoundEffectEvent(this.unit, this.ability.getAlias());
			} else {
				simulation.unitSoundEffectEvent(this.unit, this.ability.getAlias());
			}
		}
		this.channeling = this.channeling && this.ability.doChannelTick(simulation, this.unit, null);
		if (wasEffectDone && wasChanneling && !this.channeling) {
			endChannel(simulation, false);
		}
		return null;
	}

	/** A cast point inside a step takes effect at its exact time (1.29), not on a step. */
	private void effectAtCastPoint(final CSimulation simulation) {
		if (!this.castPointStarted || this.doneEffect || (this.unit.getCurrentBehavior() != this)
				|| this.unit.isDead()) {
			return;
		}
		final CBehavior next = effect(simulation);
		if ((next != null) && (next != this)) {
			this.unit.beginBehavior(simulation, next);
		}
	}

	@Override
	public void begin(final CSimulation game) {
	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {
		this.castPoint.cancel(game);
		if (this.backswing != null) {
			this.backswing.cancel(game);
		}
		if (this.doneEffect && this.channeling) {
			this.channeling = false;
			endChannel(game, interrupted);
		}
		if (this.castEventsFired) {
			this.castEventsFired = false;
			if (!interrupted && this.doneEffect) {
				this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_FINISH, this.ability, null);
			}
			this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_ENDCAST, this.ability, null);
		}
	}

	private void endChannel(final CSimulation game, final boolean interrupted) {
		game.unitStopSoundEffectEvent(this.unit, this.ability.getAlias());
		this.ability.doChannelEnd(game, this.unit, null, interrupted);
	}

	@Override
	public int getHighlightOrderId() {
		return this.ability.getBaseOrderId();
	}

	public CAbilitySpellBase getAbility() {
		return this.ability;
	}

	@Override
	public boolean interruptable() {
		return true;
	}

	@Override
	public <T> T visit(final CBehaviorVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public CBehaviorCategory getBehaviorCategory() {
		return CBehaviorCategory.SPELL;
	}
}
