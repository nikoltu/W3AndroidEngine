package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.skills;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CReleaseCheats;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetStillAliveAndTargetableVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CAbstractRangedBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CExactExpiry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

public class CBehaviorTargetSpellBase extends CAbstractRangedBehavior {
	protected final CAbilitySpellBase ability;
	private final AbilityTargetStillAliveAndTargetableVisitor stillAliveVisitor;
	private int castStartTick = 0;
	private boolean doneEffect = false;
	private boolean channeling = false;
	private boolean castEventsFired = false;
	private int castEventTick = -1;
	private final CExactExpiry castPoint = new CExactExpiry();
	/** Null in games saved before the backswing ended at its exact time; those end on whole steps. */
	private CExactExpiry backswing;

	public CBehaviorTargetSpellBase(final CUnit unit, final CAbilitySpellBase ability) {
		super(unit);
		this.ability = ability;
		this.stillAliveVisitor = new AbilityTargetStillAliveAndTargetableVisitor();
	}

	public CBehavior reset(CSimulation game, final CWidget target) {
		this.castStartTick = 0;
		this.castPoint.cancel(game);
		cancelBackswing(game);
		this.castEventTick = -1;
		this.doneEffect = false;
		this.channeling = false;
		this.castEventsFired = false;
		return innerReset(game, target, false);
	}

	public CBehavior reset(CSimulation game, final AbilityPointTarget target) {
		this.castStartTick = 0;
		this.castPoint.cancel(game);
		cancelBackswing(game);
		this.doneEffect = false;
		this.channeling = false;
		this.castEventsFired = false;
		return innerReset(game, target, false);
	}

	@Override
	public boolean isWithinRange(final CSimulation simulation) {
		if (this.channeling) {
			return true; // dont run away after channeling begins
		}
		final float castRange = this.ability.getCastRange();
		return this.unit.canReach(this.target, castRange);
	}

	@Override
	protected CBehavior update(final CSimulation simulation, final boolean withinFacingWindow) {
		if ((this.castStartTick == 0) && isTurningToCast(withinFacingWindow)) {
			return this;
		}
		this.unit.getUnitAnimationListener().playAnimation(false, this.ability.getCastingPrimaryTag(),
				this.ability.getCastingSecondaryTags(), 1.0f, true);
		if (this.castStartTick == 0) {
			this.castStartTick = simulation.getGameTurnTick();
			this.castPoint.start(simulation, castPointSeconds());
			startBackswing(simulation);
			if (!this.castEventsFired) {
				this.castEventsFired = true;
				final CBehavior prevBeh = this.unit.getCurrentBehavior();
				this.unit.fireSpellEvents(simulation, JassGameEventsWar3.EVENT_UNIT_SPELL_CHANNEL, this.ability,
						this.target);
				final int castingTicks = this.ability.isCastingTimeBeforeCast()
						? CTimer.wholeSteps(this.ability.getCastingTime())
						: 0;
				if (castingTicks > 0) {
					this.castEventTick = this.castStartTick + castingTicks;
				}
				else {
					this.unit.fireSpellEvents(simulation, JassGameEventsWar3.EVENT_UNIT_SPELL_CAST, this.ability,
							this.target);
				}
				if (this.unit.getCurrentBehavior() != prevBeh) {
					return this.unit.getCurrentBehavior();
				}
			}
		}
		if (this.castEventTick >= 0) {
			// Measured on 1.29 (Dark Conversion): the casting time runs between the channel and cast events, and
			// the cast point counts from the cast event.
			if (simulation.getGameTurnTick() < this.castEventTick) {
				return this;
			}
			this.castEventTick = -1;
			this.castStartTick = simulation.getGameTurnTick();
			this.castPoint.start(simulation, castPointSeconds());
			startBackswing(simulation);
			final CBehavior prevBeh = this.unit.getCurrentBehavior();
			this.unit.fireSpellEvents(simulation, JassGameEventsWar3.EVENT_UNIT_SPELL_CAST, this.ability, this.target);
			if (this.unit.getCurrentBehavior() != prevBeh) {
				return this.unit.getCurrentBehavior();
			}
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

	/** Measured on 1.29: a spell cast from an item has no cast point, it takes effect once the unit faces the target. */
	private float castPointSeconds() {
		final CAbilityInventory inventory = this.unit.getInventoryData();
		if ((inventory != null) && inventory.isItemSpellCast(this.ability)) {
			return 0;
		}
		return this.unit.getUnitType().getCastPoint();
	}

	/**
	 * The backswing follows the cast point (1.29: effect at castpt, the cast ends castbsw later, at its exact time and
	 * not on a step).
	 */
	private void startBackswing(final CSimulation simulation) {
		if (this.backswing == null) {
			this.backswing = new CExactExpiry();
		}
		this.backswing.start(simulation,
				this.unit.getUnitType().getCastPoint() + this.unit.getUnitType().getCastBackswingPoint());
	}

	private void cancelBackswing(final CSimulation simulation) {
		if (this.backswing != null) {
			this.backswing.cancel(simulation);
		}
	}

	private boolean backswingOver(final CSimulation simulation) {
		if (this.backswing == null) {
			return (simulation.getGameTurnTick() - this.castStartTick) >= CTimer.wholeSteps(
					this.unit.getUnitType().getCastPoint() + this.unit.getUnitType().getCastBackswingPoint());
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
				simulation.getCommandErrorListener().showInterfaceError(this.unit.getPlayerIndex(),
						CommandStringErrorKeys.NOT_ENOUGH_MANA);
				return this.unit.pollNextOrderBehavior(simulation);
			}
			this.unit.beginCooldown(simulation, this.ability.getCode(), this.ability.getCooldown());
			// 1.29: the effect event still sees the mana from before the cast; the cost is paid after it.
			this.unit.fireSpellEvents(simulation, JassGameEventsWar3.EVENT_UNIT_SPELL_EFFECT, this.ability, this.target);
			this.unit.setMana(Math.max(0, this.unit.getMana() - manaCost));
			this.channeling = this.ability.doEffect(simulation, this.unit, this.target);
			if (this.unit.getInventoryData() != null) {
				this.unit.getInventoryData().onItemAbilityEffect(simulation, this.unit, this.ability);
			}
			if (this.channeling) {
				simulation.unitLoopSoundEffectEvent(this.unit, this.ability.getAlias());
			}
			else {
				simulation.unitSoundEffectEvent(this.unit, this.ability.getAlias());
			}
		}
		this.channeling = this.channeling && this.ability.doChannelTick(simulation, this.unit, this.target);
		if (wasEffectDone && wasChanneling && !this.channeling) {
			endChannel(simulation, false);
		}
		return null;
	}

	/** A cast point inside a step takes effect at its exact time (1.29: 0.452 s after the cast, not on a step). */
	private void effectAtCastPoint(final CSimulation simulation) {
		if (this.doneEffect || (this.unit.getCurrentBehavior() != this) || this.unit.isDead()
				|| !checkTargetStillValid(simulation)) {
			return;
		}
		final CBehavior next = effect(simulation);
		if ((next != null) && (next != this)) {
			this.unit.beginBehavior(simulation, next);
		}
	}

	@Override
	protected CBehavior updateOnInvalidTarget(final CSimulation simulation) {
		return this.unit.pollNextOrderBehavior(simulation);
	}

	@Override
	protected boolean checkTargetStillValid(final CSimulation simulation) {
		/*
		 * BELOW: "doneEffect" allows us to channel "at" something that died, if you hit
		 * a bug with that, then fix it here
		 */
		return this.doneEffect || this.target.visit(this.stillAliveVisitor.reset(simulation, this.unit,
				this.ability.getTargetsAllowed()));
	}

	@Override
	protected boolean usesCastTurn() {
		return true;
	}

	@Override
	protected boolean isFacingLocked() {
		return this.castStartTick != 0;
	}

	@Override
	protected void resetBeforeMoving(final CSimulation simulation) {
		this.castStartTick = 0;
		this.castPoint.cancel(simulation);
		cancelBackswing(simulation);
	}

	@Override
	public void begin(final CSimulation game) {
	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {
		cancelBackswing(game);
		checkEndChannel(game, interrupted);
		if (this.castEventsFired) {
			this.castEventsFired = false;
			if (!interrupted && this.doneEffect) {
				this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_FINISH, this.ability, this.target);
			}
			this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_ENDCAST, this.ability, this.target);
		}
	}

	@Override
	public void endMove(final CSimulation game, final boolean interrupted) {
		checkEndChannel(game, interrupted);
	}

	private void checkEndChannel(final CSimulation game, final boolean interrupted) {
		if (this.channeling) {
			this.channeling = false;
			endChannel(game, interrupted);
		}
	}

	private void endChannel(CSimulation game, boolean interrupted) {
		game.unitStopSoundEffectEvent(this.unit, this.ability.getAlias());
		this.ability.doChannelEnd(game, this.unit, this.target, interrupted);
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
	public CBehaviorCategory getBehaviorCategory() {
		return CBehaviorCategory.SPELL;
	}
}
