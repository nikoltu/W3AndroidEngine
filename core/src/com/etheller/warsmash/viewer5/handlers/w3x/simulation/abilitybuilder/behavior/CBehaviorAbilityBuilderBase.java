package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior;

import java.util.EnumSet;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CReleaseCheats;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CAbstractRangedBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CExactExpiry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;

public class CBehaviorAbilityBuilderBase extends CAbstractRangedBehavior implements ABBehavior {
	private Map<String, Object> localStore;
	private AbilityBuilderActiveAbility ability;
	private ABAbilityTargetStillTargetableVisitor preCastTargetableVisitor;

	private int castBehaviorNotifyTick = 0;

	private int castStartTick = 0;
	private int castTimeEndTick = 0;
	private int castPointTicks = 0;
	private int backswingTicks = 0;
	private final CExactExpiry castPoint = new CExactExpiry();
	/** Null in games saved before the backswing ended at its exact time; those end on whole steps. */
	private CExactExpiry backswing;
	private boolean doneCastTime = false;
	private boolean doneEffect = false;
	private boolean channeling = false;
	private boolean preventReInterrupt = false;

	private int castId = 0;
	private int previousCastId = 0;
	private int issuingPlayerIndex;
	private int orderId;
	private boolean autoOrder;

	private boolean instant = false;
	private CBehaviorCategory behaviorCategory = null;
	private EnumSet<SecondaryTag> channelTags;
	private boolean firstAnimation;

	public CBehaviorAbilityBuilderBase(final CUnit unit, final Map<String, Object> localStore,
			AbilityBuilderActiveAbility ability) {
		super(unit);
		this.localStore = localStore;
		this.ability = ability;
		this.preCastTargetableVisitor = new ABAbilityTargetStillTargetableVisitor();

	}

	public void setInstant(boolean instant) {
		this.instant = instant;
	}

	public void setBehaviorCategory(CBehaviorCategory behaviorCategory) {
		this.behaviorCategory = behaviorCategory;
	}

	public CBehavior reset(final CSimulation game, final CWidget target, final boolean autoOrder) {
		if (isSameCastUnderway(target, this.ability.getBaseOrderId())) {
			this.autoOrder = autoOrder;
			return this;
		}
		this.doneCastTime = false;
		this.doneEffect = false;
		this.castStartTick = 0;
		cancelBackswing(game);
		this.castTimeEndTick = this.ability.ignoreCastTime() ? 0
				: CTimer.wholeSteps(this.ability.getCastTime());
		this.castPointTicks = this.castTimeEndTick
				+ CTimer.wholeSteps(this.unit.getUnitType().getCastPoint());
		this.backswingTicks = this.castPointTicks
				+ CTimer.wholeSteps(this.unit.getUnitType().getCastBackswingPoint());
		this.localStore.put(ABLocalStoreKeys.CHANNELING, false);
		this.issuingPlayerIndex = this.unit.getPlayerIndex();
		this.orderId = this.ability.getBaseOrderId();
		this.preventReInterrupt = false;
		this.firstAnimation = true;
		this.autoOrder = autoOrder;

		this.channelTags = this.ability.getCastingSecondaryTags().clone();
		this.channelTags.add(SecondaryTag.CHANNEL);
		return innerReset(game, target, false);
	}

	public CBehavior reset(final CSimulation game, final CWidget target, int issuingPlayerIndex, int orderId, final boolean autoOrder) {
		if (isSameCastUnderway(target, orderId)) {
			this.autoOrder = autoOrder;
			return this;
		}
		this.doneCastTime = false;
		this.doneEffect = false;
		this.castStartTick = 0;
		cancelBackswing(game);
		this.castTimeEndTick = this.ability.ignoreCastTime() ? 0
				: CTimer.wholeSteps(this.ability.getCastTime());
		this.castPointTicks = this.castTimeEndTick
				+ CTimer.wholeSteps(this.unit.getUnitType().getCastPoint());
		this.backswingTicks = this.castPointTicks
				+ CTimer.wholeSteps(this.unit.getUnitType().getCastBackswingPoint());
		this.localStore.put(ABLocalStoreKeys.CHANNELING, false);
		this.issuingPlayerIndex = issuingPlayerIndex;
		this.orderId = orderId;
		this.preventReInterrupt = false;
		this.firstAnimation = true;
		this.autoOrder = autoOrder;

		this.channelTags = this.ability.getCastingSecondaryTags().clone();
		this.channelTags.add(SecondaryTag.CHANNEL);
		return innerReset(game, target, false);
	}

	@Override
	public CBehavior reset(final CSimulation game, final AbilityPointTarget target, final boolean autoOrder) {
		this.doneCastTime = false;
		this.doneEffect = false;
		this.castStartTick = 0;
		cancelBackswing(game);
		this.castTimeEndTick = this.ability.ignoreCastTime() ? 0
				: CTimer.wholeSteps(this.ability.getCastTime());
		this.castPointTicks = this.castTimeEndTick
				+ CTimer.wholeSteps(this.unit.getUnitType().getCastPoint());
		this.backswingTicks = this.castPointTicks
				+ CTimer.wholeSteps(this.unit.getUnitType().getCastBackswingPoint());
		this.localStore.put(ABLocalStoreKeys.CHANNELING, false);
		this.issuingPlayerIndex = this.unit.getPlayerIndex();
		this.orderId = this.ability.getBaseOrderId();
		this.preventReInterrupt = false;
		this.firstAnimation = true;
		this.autoOrder = autoOrder;

		this.channelTags = this.ability.getCastingSecondaryTags().clone();
		this.channelTags.add(SecondaryTag.CHANNEL);
		return innerReset(game, target, false);
	}

	@Override
	public CBehavior reset(final CSimulation game, final AbilityPointTarget target, int issuingPlayerIndex, int orderId,
			final boolean autoOrder) {
		this.doneCastTime = false;
		this.doneEffect = false;
		this.castStartTick = 0;
		cancelBackswing(game);
		this.castTimeEndTick = this.ability.ignoreCastTime() ? 0
				: CTimer.wholeSteps(this.ability.getCastTime());
		this.castPointTicks = this.castTimeEndTick
				+ CTimer.wholeSteps(this.unit.getUnitType().getCastPoint());
		this.backswingTicks = this.castPointTicks
				+ CTimer.wholeSteps(this.unit.getUnitType().getCastBackswingPoint());
		this.localStore.put(ABLocalStoreKeys.CHANNELING, false);
		this.issuingPlayerIndex = issuingPlayerIndex;
		this.orderId = orderId;
		this.preventReInterrupt = false;
		this.firstAnimation = true;
		this.autoOrder = autoOrder;

		this.channelTags = this.ability.getCastingSecondaryTags().clone();
		this.channelTags.add(SecondaryTag.CHANNEL);
		return innerReset(game, target, false);
	}

	// The same spell ordered again on the same target goes on with the cast under way instead of restarting it (1.29
	// Heal ordered again during its repeat keeps its cycle).
	private boolean isSameCastUnderway(final CWidget target, final int orderId) {
		if ((this.unit.getCurrentBehavior() != this) || (this.target != target) || (this.orderId != orderId)
				|| (this.castStartTick == 0) || this.channeling) {
			return false;
		}
		if (this.previousCastId != this.castId) {
			this.localStore.remove(ABLocalStoreKeys.ABILITYTARGETEDUNIT + this.previousCastId);
			this.localStore.remove(ABLocalStoreKeys.ABILITYTARGETEDITEM + this.previousCastId);
			this.localStore.remove(ABLocalStoreKeys.ABILITYTARGETEDDESTRUCTABLE + this.previousCastId);
			this.localStore.remove(ABLocalStoreKeys.combineKey(ABLocalStoreKeys.ISAUTOCAST, this.previousCastId));
		}
		return true;
	}

	public ABBehavior reset(final boolean autoOrder) {
		return null;
	}

	public ABBehavior reset(int issuingPlayerIndex, int orderId, final boolean autoOrder) {
		return null;
	}

	@Override
	public CBehavior update(final CSimulation game, final boolean withinFacingWindow) {
		final boolean wasChanneling = this.channeling;
		if ((this.castStartTick == 0) && isTurningToCast(withinFacingWindow)) {
			return this;
		}
		if (this.castStartTick == 0) {
			CBehavior prevBeh = this.unit.getCurrentBehavior();

			this.castStartTick = game.getGameTurnTick();
			this.castBehaviorNotifyTick = this.castStartTick + CTimer.wholeSteps(0.5f);

			if (!this.target.visit(
					this.preCastTargetableVisitor.reset(game, this.unit, ability, this.autoOrder, false, issuingPlayerIndex, orderId))) {
				cleanupInputs();
				return this.unit.pollNextOrderBehavior(game);
			}

			this.ability.checkCanUse(game, unit, issuingPlayerIndex, orderId, autoOrder, BooleanAbilityActivationReceiver.INSTANCE.reset());
			if (!BooleanAbilityActivationReceiver.INSTANCE.isOk()) {
				cleanupInputs();
				return this.unit.pollNextOrderBehavior(game);
			}

			this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_CHANNEL, this.ability, this.target);
			if (this.unit.getCurrentBehavior() != prevBeh) {
				cleanupInputs();
				return this.unit.getCurrentBehavior();
			}
			else if (this.unit.isPaused()) {
				return this;
			}

			if (!instant && this.castTimeEndTick > 0) {
				this.unit.getUnitAnimationListener().playAnimation(false, PrimaryTag.STAND,
						EnumSet.of(SecondaryTag.CHANNEL), 1.0f, true);
			}
		}

		if (instant) {
			if (!this.doneCastTime) {
				// should have already checked castable/range above, as no time delay
				if (!this.unit.chargeMana(CReleaseCheats.manaCost(game, this.unit, this.ability.getChargedManaCost()))) {
					cleanupInputs();
					return this.unit.pollNextOrderBehavior(game);
				}
				this.ability.startCooldown(game, this.unit);

				this.ability.runBeginCastingActions(game, unit, orderId);
				this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_CAST, this.ability, this.target);
				this.doneCastTime = true;
			}
			CBehavior beh = tryDoEffect(game, wasChanneling);
			if (beh != null) {
				cleanupInputs();
				return beh;
			}
			final CBehavior newBehavior = (CBehavior) this.localStore.get(ABLocalStoreKeys.NEWBEHAVIOR);
			if (newBehavior != null) {
				localStore.remove(ABLocalStoreKeys.NEWBEHAVIOR);
				return newBehavior;
			}

			if (!this.channeling) {
				cleanupInputs();
				return this.unit.pollNextOrderBehavior(game);
			}
		}
		else {
			final int ticksSinceCast = game.getGameTurnTick() - this.castStartTick;
			if (!this.doneCastTime && ticksSinceCast >= this.castTimeEndTick) {
				if (!this.isWithinRange(game)) {
					// target moved too far, out of range now
					cleanupInputs();
					return this.unit.pollNextOrderBehavior(game);
				}

				this.ability.checkCanUse(game, unit, issuingPlayerIndex, orderId, autoOrder, BooleanAbilityActivationReceiver.INSTANCE.reset());
				if (!BooleanAbilityActivationReceiver.INSTANCE.isOk()) {
					cleanupInputs();
					return this.unit.pollNextOrderBehavior(game);
				}
				CBehavior prevBeh = this.unit.getCurrentBehavior();

				this.ability.runBeginCastingActions(game, unit, orderId);
				CBehavior newBehavior = (CBehavior) localStore.get(ABLocalStoreKeys.NEWBEHAVIOR);
				if (newBehavior != null) {
					localStore.remove(ABLocalStoreKeys.NEWBEHAVIOR);
					return newBehavior;
				}

				this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_CAST, this.ability, this.target);
				if (this.unit.getCurrentBehavior() != prevBeh) {
					cleanupInputs();
					return this.unit.getCurrentBehavior();
				} else if (this.unit.isPaused()) {
					return this;
				}

				this.channeling = (boolean) localStore.get(ABLocalStoreKeys.CHANNELING);
				if (!this.channeling) {
					this.unit.getUnitAnimationListener().playAnimation(this.firstAnimation,
							this.ability.getCastingPrimaryTag(), this.ability.getCastingSecondaryTags(), 1.0f, true);
					this.firstAnimation = false;
					this.unit.getUnitAnimationListener().queueAnimation(PrimaryTag.STAND, SequenceUtils.EMPTY, true);
				}
				this.doneCastTime = true;
				this.castPoint.start(game, this.unit.getUnitType().getCastPoint());
				startBackswing(game);
			}
			if (this.channeling) {
				this.unit.getUnitAnimationListener().playAnimation(false, this.ability.getCastingPrimaryTag(),
						this.channelTags, 1.0f, true);
			}

			if (this.doneCastTime
					&& (this.doneEffect || this.castPoint.check(game, this::effectAtCastPoint))) {
				final CBehavior next = effectAtPoint(game, wasChanneling);
				if (next != null) {
					return next;
				}
			}
			if (this.doneEffect && !this.channeling && backswingOver(game, ticksSinceCast)) {
				// A creep's own Heal is not repeated: its spell thoughts order every heal anew (1.29).
				if (this.doneEffect && this.ability.isRepeatCast()
						&& !(this.autoOrder && game.isNeutralAggressive(this.unit.getPlayerIndex()))) {
					// The cast holds until the cooldown is over, also on the last repeat (1.29 Heal: E+1.0 s, not
					// the backswing).
					if (this.ability.getCooldownRemainingTicks(game, this.unit) > 0) {
						return this;
					}
					if (canRepeat(game)) {
						fireEndEvents(game, false);
						restartCast();
						return isWithinRange(game) ? update(game, withinFacingWindow) : this;
					}
				}
				cleanupInputs();
				return this.unit.pollNextOrderBehavior(game);
			}
		}
		return this;
	}

	/**
	 * The backswing follows the cast point (1.29: the cast ends castbsw after the effect, at its exact time and not on
	 * a step).
	 */
	private void startBackswing(final CSimulation game) {
		if (this.backswing == null) {
			this.backswing = new CExactExpiry();
		}
		this.backswing.start(game,
				this.unit.getUnitType().getCastPoint() + this.unit.getUnitType().getCastBackswingPoint());
	}

	private void cancelBackswing(final CSimulation game) {
		if (this.backswing != null) {
			this.backswing.cancel(game);
		}
	}

	private boolean backswingOver(final CSimulation game, final int ticksSinceCast) {
		if (this.backswing == null) {
			return ticksSinceCast >= this.backswingTicks;
		}
		return this.backswing.check(game, this::endBackswingInStep);
	}

	private void endBackswingInStep(final CSimulation game) {
		if (!this.doneEffect || this.channeling || (this.unit.getCurrentBehavior() != this) || this.unit.isDead()
				|| isRepeatHeld(game)) {
			return;
		}
		cleanupInputs();
		this.unit.beginBehavior(game, this.unit.pollNextOrderBehavior(game));
	}

	/** A repeating cast goes on in its step update, which also waits for the cooldown. */
	private boolean isRepeatHeld(final CSimulation game) {
		return this.ability.isRepeatCast() && !(this.autoOrder && game.isNeutralAggressive(this.unit.getPlayerIndex()));
	}

	private boolean canRepeat(final CSimulation game) {
		if (!this.target.visit(this.preCastTargetableVisitor.reset(game, this.unit, this.ability, this.autoOrder, false,
				this.issuingPlayerIndex, this.orderId))) {
			return false;
		}
		this.ability.checkCanUse(game, this.unit, this.issuingPlayerIndex, this.orderId, this.autoOrder,
				BooleanAbilityActivationReceiver.INSTANCE.reset());
		return BooleanAbilityActivationReceiver.INSTANCE.isOk();
	}

	private void restartCast() {
		this.doneCastTime = false;
		this.doneEffect = false;
		this.castStartTick = 0;
		this.localStore.put(ABLocalStoreKeys.CHANNELING, false);
	}

	private void cleanupInputs() {
		this.ability.cleanupInputs(castId);
	}

	/** Past the cast point: the effect, then a channel tick; a behavior to switch to, or null. */
	private CBehavior effectAtPoint(final CSimulation game, final boolean wasChanneling) {
		if (this.castPointTicks > this.castTimeEndTick && !this.isWithinRange(game)) {
			// Unit moved too far, out of range now
			cleanupInputs();
			return this.unit.pollNextOrderBehavior(game);
		}
		CBehavior beh = tryDoEffect(game, wasChanneling);
		if (beh != null) {
			cleanupInputs();
			return beh;
		}
		final CBehavior newBehavior = (CBehavior) this.localStore.get(ABLocalStoreKeys.NEWBEHAVIOR);
		if (newBehavior != null) {
			localStore.remove(ABLocalStoreKeys.NEWBEHAVIOR);
			return newBehavior;
		}
		return null;
	}

	/** A cast point inside a step takes effect at its exact time (1.29), not on a step. */
	private void effectAtCastPoint(final CSimulation game) {
		if (!this.doneCastTime || this.doneEffect || (this.unit.getCurrentBehavior() != this)
				|| this.unit.isDead()) {
			return;
		}
		final CBehavior next = effectAtPoint(game, this.channeling);
		if ((next != null) && (next != this)) {
			this.unit.beginBehavior(game, next);
		}
	}

	private CBehavior tryDoEffect(CSimulation game, boolean wasChanneling) {
		boolean wasEffectDone = this.doneEffect;
		if (!wasEffectDone) {
			// Instant casts paid already; others pay mana and start the cooldown at the effect, so a cast stopped
			// before it costs nothing (1.29).
			if (!this.instant) {
				if (!this.unit.chargeMana(CReleaseCheats.manaCost(game, this.unit, this.ability.getChargedManaCost()))) {
					this.doneEffect = true;
					return this.unit.pollNextOrderBehavior(game);
				}
				this.ability.startCooldown(game, this.unit);
			}
			CBehavior prevBeh = this.unit.getCurrentBehavior();
			if (this.channeling) {
				game.unitLoopSoundEffectEvent(this.unit, this.ability.getAlias());
			} else {
				game.unitSoundEffectEvent(this.unit, this.ability.getAlias());
			}
			this.doneEffect = true;

			if (this.unit.getCurrentBehavior() != prevBeh) {
				return this.unit.getCurrentBehavior();
			} else if (this.unit.isPaused()) {
				return this;
			}

			this.ability.runEndCastingActions(game, unit, orderId);
			this.channeling = (boolean) localStore.get(ABLocalStoreKeys.CHANNELING);

			this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_EFFECT, this.ability, this.target);
			if (this.unit.getCurrentBehavior() != prevBeh) {
				return this.unit.getCurrentBehavior();
			}
			else if (this.unit.isPaused()) {
				return this;
			}

		}
		this.channeling = this.channeling && this.doChannelTick(game, this.unit, this.target);
		if (wasEffectDone && wasChanneling && !this.channeling) {
			endChannel(game, false);
			return this.unit.pollNextOrderBehavior(game);
		}
		return null;
	}

	@Override
	public void begin(final CSimulation game) {
		if (ability.getItem() != null && ability.getItem().getItemType().isActivelyUsed()) {
			// This is for runes/glyphs that use on pickup. We will never see an update loop
			// for these

			if (!this.unit.chargeMana(CReleaseCheats.manaCost(game, this.unit, this.ability.getChargedManaCost()))) {
				cleanupInputs();
				this.unit.beginBehavior(game, this.unit.pollNextOrderBehavior(game));
				return;
			}
			this.ability.startCooldown(game, this.unit);

			this.ability.runBeginCastingActions(game, unit, orderId);

			CBehavior beh = tryDoEffect(game, false);
			if (beh != null) {
				cleanupInputs();
				this.unit.beginBehavior(game, beh);
				return;
			}
			CBehavior newBehavior = (CBehavior) localStore.get(ABLocalStoreKeys.NEWBEHAVIOR);
			if (newBehavior != null) {
				localStore.remove(ABLocalStoreKeys.NEWBEHAVIOR);
				this.unit.beginBehavior(game, newBehavior);
				return;
			}

			cleanupInputs();
			this.unit.beginBehavior(game, this.unit.pollNextOrderBehavior(game));
		}
	}

	public boolean doChannelTick(final CSimulation game, final CUnit caster, final AbilityTarget target) {
		this.ability.runChannelTickActions(game, caster, this.orderId);
		return (boolean) this.localStore.get(ABLocalStoreKeys.CHANNELING);
	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {
		cancelBackswing(game);
		checkEndChannel(game, interrupted);
		fireEndEvents(game, interrupted);
	}

	private void fireEndEvents(final CSimulation game, final boolean interrupted) {
		Boolean preventEndEvents = (Boolean) this.localStore.get(ABLocalStoreKeys.PREVENTENDEVENTS + this.castId);
		if (preventEndEvents == null || !preventEndEvents) {
			if (!interrupted) {
				this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_FINISH, this.ability, this.target);
			}
			this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_ENDCAST, this.ability, this.target);
		}
	}

	private void checkEndChannel(final CSimulation game, final boolean interrupted) {
		if (this.channeling) {
			this.channeling = false;
			this.localStore.put(ABLocalStoreKeys.CHANNELING, false);
			endChannel(game, interrupted);
		}
	}

	private void endChannel(final CSimulation game, final boolean interrupted) {
		this.localStore.put(ABLocalStoreKeys.INTERRUPTED, interrupted);
		game.unitStopSoundEffectEvent(this.unit, this.ability.getAlias());
		this.ability.runEndChannelActions(game, this.unit, this.orderId);
		cleanupInputs();
	}

	@Override
	public int getHighlightOrderId() {
		return this.ability.getBaseOrderId();
	}

	@Override
	public boolean isWithinRange(CSimulation simulation) {
		float range = this.ability.getCastRange();
		if (this.castStartTick > 0) {
			range += simulation.getGameplayConstants().getSpellCastRangeBuffer();
		}
		return this.unit.canReach(this.target, range);
	}

	@Override
	public void endMove(final CSimulation game, final boolean interrupted) {
		if (interrupted && !this.preventReInterrupt) {
			this.preventReInterrupt = true;
			this.ability.runCancelPreCastActions(game, this.unit, this.orderId);
			checkEndChannel(game, interrupted);
		}
	}

	@Override
	protected CBehavior updateOnInvalidTarget(final CSimulation simulation) {
		return this.unit.pollNextOrderBehavior(simulation);
	}

	@Override
	protected boolean checkTargetStillValid(CSimulation simulation) {
		return this.doneEffect || this.target.visit(this.preCastTargetableVisitor.reset(simulation, this.unit,
				this.ability, this.autoOrder, this.channeling, issuingPlayerIndex, orderId));
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
		cancelBackswing(simulation);
	}

	public void setCastId(int castId) {
		this.previousCastId = this.castId;
		this.castId = castId;
	}

	// A new order cancels a spell at any point, before its effect too (no mana is spent then).
	@Override
	public boolean interruptable() {
		return true;
	}

	@Override
	public CBehaviorCategory getBehaviorCategory() {
		if (this.behaviorCategory != null) {
			return this.behaviorCategory;
		}
		return CBehaviorCategory.SPELL;
	}

	@Override
	public CAbility getAbility() {
		return this.ability;
	}

}
