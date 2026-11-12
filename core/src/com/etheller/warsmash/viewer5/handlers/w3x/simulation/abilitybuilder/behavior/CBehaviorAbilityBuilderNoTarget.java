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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CExactExpiry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;

public class CBehaviorAbilityBuilderNoTarget implements ABBehavior {
	private Map<String, Object> localStore;
	private AbilityBuilderActiveAbility ability;

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

	private CUnit unit;
	private int issuingPlayerIndex;
	private int orderId;
	private boolean autoOrder;

	private boolean instant = false;
	private CBehaviorCategory behaviorCategory = null;
	private int castId = 0;
	private EnumSet<SecondaryTag> channelTags;
	private boolean firstAnimation;

	public CBehaviorAbilityBuilderNoTarget(final CUnit unit, final Map<String, Object> localStore,
			AbilityBuilderActiveAbility ability) {
		this.unit = unit;
		this.localStore = localStore;
		this.ability = ability;
	}

	public void setInstant(boolean instant) {
		this.instant = instant;
	}

	public void setBehaviorCategory(CBehaviorCategory behaviorCategory) {
		this.behaviorCategory = behaviorCategory;
	}

	@Override
	public ABBehavior reset(final CSimulation game, final CWidget target, final boolean autoOrder) {
		return null;
	}

	@Override
	public ABBehavior reset(final CSimulation game, CWidget target, int issuingPlayerIndex, int orderId, final boolean autoOrder) {
		return null;
	}

	@Override
	public ABBehavior reset(final CSimulation game, final AbilityPointTarget target, final boolean autoOrder) {
		return null;
	}

	@Override
	public ABBehavior reset(final CSimulation game, AbilityPointTarget target, int issuingPlayerIndex, int orderId, final boolean autoOrder) {
		return null;
	}

	@Override
	public ABBehavior reset(final boolean autoOrder) {
		this.doneCastTime = false;
		this.doneEffect = false;
		this.castStartTick = 0;
		this.castTimeEndTick = this.ability.ignoreCastTime() ? 0
				: CTimer.wholeSteps(this.ability.getCastTime());
		this.castPointTicks = this.castTimeEndTick
				+ CTimer.wholeSteps(this.unit.getUnitType().getCastPoint());
		this.backswingTicks = this.castPointTicks
				+ CTimer.wholeSteps(this.unit.getUnitType().getCastBackswingPoint());
		this.localStore.put(ABLocalStoreKeys.CHANNELING, false);
		this.issuingPlayerIndex = this.unit.getPlayerIndex();
		this.orderId = this.ability.getBaseOrderId();
		this.firstAnimation = true;
		this.autoOrder = autoOrder;

		this.channelTags = this.ability.getCastingSecondaryTags().clone();
		this.channelTags.add(SecondaryTag.CHANNEL);
		return this;
	}

	@Override
	public ABBehavior reset(int issuingPlayerIndex, int orderId, final boolean autoOrder) {
		this.doneCastTime = false;
		this.doneEffect = false;
		this.castStartTick = 0;
		this.castTimeEndTick = this.ability.ignoreCastTime() ? 0
				: CTimer.wholeSteps(this.ability.getCastTime());
		this.castPointTicks = this.castTimeEndTick
				+ CTimer.wholeSteps(this.unit.getUnitType().getCastPoint());
		this.backswingTicks = this.castPointTicks
				+ CTimer.wholeSteps(this.unit.getUnitType().getCastBackswingPoint());
		this.localStore.put(ABLocalStoreKeys.CHANNELING, false);
		this.issuingPlayerIndex = issuingPlayerIndex;
		this.orderId = orderId;
		this.firstAnimation = true;
		this.autoOrder = autoOrder;

		this.channelTags = this.ability.getCastingSecondaryTags().clone();
		this.channelTags.add(SecondaryTag.CHANNEL);
		return this;
	}

	@Override
	public CBehavior update(final CSimulation game) {
		boolean wasChanneling = this.channeling;
		if (this.castStartTick == 0) {
			this.castStartTick = game.getGameTurnTick();

			this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_CHANNEL, this.ability, null);
			// In war3, non-targeted spells cannot be interrupted by pausing on spell events

			if (!instant && this.castTimeEndTick > 0) {
				this.unit.getUnitAnimationListener().playAnimation(false, PrimaryTag.STAND,
						EnumSet.of(SecondaryTag.CHANNEL), 1.0f, true);
			}
		}

		if (instant) {
			if (!this.doneCastTime) {
				// should have already checked castable above, as no time delay
				if (!this.unit.chargeMana(CReleaseCheats.manaCost(game, this.unit, this.ability.getChargedManaCost()))) {
					cleanupInputs();
					return this.unit.pollNextOrderBehavior(game);
				}
				this.ability.startCooldown(game, this.unit);

				this.ability.runBeginCastingActions(game, unit, orderId);
				this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_CAST, this.ability, null);
				this.doneCastTime = true;
			}
			CBehavior beh = tryDoEffect(game, wasChanneling);
			if (beh != null) {
				cleanupInputs();
				return beh;
			}
			CBehavior newBehavior = (CBehavior) localStore.get(ABLocalStoreKeys.NEWBEHAVIOR);
			if (newBehavior != null) {
				localStore.remove(ABLocalStoreKeys.NEWBEHAVIOR);
				return newBehavior;
			}
			if (!this.channeling) {
				cleanupInputs();
				return this.unit.pollNextOrderBehavior(game);
			}
		} else {
			final int ticksSinceCast = game.getGameTurnTick() - this.castStartTick;
			if (!this.doneCastTime && ticksSinceCast >= this.castTimeEndTick) {
				this.ability.checkCanUse(game, unit, issuingPlayerIndex, orderId, autoOrder, BooleanAbilityActivationReceiver.INSTANCE.reset());
				if (!BooleanAbilityActivationReceiver.INSTANCE.isOk()) {
					cleanupInputs();
					return this.unit.pollNextOrderBehavior(game);
				}

				this.ability.runBeginCastingActions(game, unit, orderId);
				CBehavior newBehavior = (CBehavior) localStore.get(ABLocalStoreKeys.NEWBEHAVIOR);
				if (newBehavior != null) {
					localStore.remove(ABLocalStoreKeys.NEWBEHAVIOR);
					return newBehavior;
				}

				this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_CAST, this.ability, null);

				this.channeling = (boolean) localStore.get(ABLocalStoreKeys.CHANNELING);
				if (!this.channeling) {
					this.unit.getUnitAnimationListener().playAnimation(this.firstAnimation, this.ability.getCastingPrimaryTag(),
							this.ability.getCastingSecondaryTags(), 1.0f, true);
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

	private boolean backswingOver(final CSimulation game, final int ticksSinceCast) {
		if (this.backswing == null) {
			return ticksSinceCast >= this.backswingTicks;
		}
		return this.backswing.check(game, this::endBackswingInStep);
	}

	private void endBackswingInStep(final CSimulation game) {
		if (!this.doneEffect || this.channeling || (this.unit.getCurrentBehavior() != this) || this.unit.isDead()) {
			return;
		}
		cleanupInputs();
		this.unit.beginBehavior(game, this.unit.pollNextOrderBehavior(game));
	}

	/** Past the cast point: the effect, then a channel tick; a behavior to switch to, or null. */
	private CBehavior effectAtPoint(final CSimulation game, final boolean wasChanneling) {
		CBehavior beh = tryDoEffect(game, wasChanneling);
		if (beh != null) {
			cleanupInputs();
			return beh;
		}
		CBehavior newBehavior = (CBehavior) localStore.get(ABLocalStoreKeys.NEWBEHAVIOR);
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
			if (this.channeling) {
				game.unitLoopSoundEffectEvent(this.unit, this.ability.getAlias());
			} else {
				game.unitSoundEffectEvent(this.unit, this.ability.getAlias());
			}
			this.doneEffect = true;

			this.ability.runEndCastingActions(game, unit, orderId);
			this.channeling = (boolean) localStore.get(ABLocalStoreKeys.CHANNELING);
			this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_EFFECT, this.ability, null);
		}
		this.channeling = this.channeling && this.doChannelTick(game, this.unit);
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
			//this.unit.beginBehavior(game, this.unit.pollNextOrderBehavior(game));
		}
	}

	public boolean doChannelTick(CSimulation game, CUnit caster) {
		this.ability.runChannelTickActions(game, caster, orderId);
		return (boolean) localStore.get(ABLocalStoreKeys.CHANNELING);
	}

	@Override
	public void end(final CSimulation game, boolean interrupted) {
		if (this.backswing != null) {
			this.backswing.cancel(game);
		}
		checkEndChannel(game, interrupted);
		Boolean preventEndEvents = (Boolean) this.localStore.get(ABLocalStoreKeys.PREVENTENDEVENTS + this.castId);
		if (preventEndEvents == null || !preventEndEvents) {
			if (!interrupted) {
				this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_FINISH, this.ability, null);
			}
			this.unit.fireSpellEvents(game, JassGameEventsWar3.EVENT_UNIT_SPELL_ENDCAST, this.ability, null);
		}
	}

	private void checkEndChannel(final CSimulation game, final boolean interrupted) {
		if (this.channeling) {
			this.channeling = false;
			this.localStore.put(ABLocalStoreKeys.CHANNELING, false);
			endChannel(game, interrupted);
		}
	}

	private void endChannel(CSimulation game, boolean interrupted) {
		this.localStore.put(ABLocalStoreKeys.INTERRUPTED, interrupted);
		game.unitStopSoundEffectEvent(this.unit, this.ability.getAlias());
		this.ability.runEndChannelActions(game, unit, orderId);
	}

	private void cleanupInputs() {
		this.ability.cleanupInputs(this.castId);
	}

	@Override
	public int getHighlightOrderId() {
		return this.ability.getBaseOrderId();
	}

	public void setCastId(int castId) {
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
		return ability;
	}

	@Override
	public <T> T visit(final CBehaviorVisitor<T> visitor) {
		return visitor.accept(this);
	}
}
