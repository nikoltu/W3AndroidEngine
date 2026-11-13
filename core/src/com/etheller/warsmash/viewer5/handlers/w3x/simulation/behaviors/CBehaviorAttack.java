package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors;

import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetStillAliveAndTargetableVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackModifier;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.replacement.CUnitAttackSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CExactExpiry;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

public class CBehaviorAttack extends CAbstractRangedBehavior {
	private static final float FIRST_SWING_MAX_WAIT = 0.25f;
	private static final double TIME_EPSILON = 1e-9;

	private int highlightOrderId;
	private final AbilityTargetStillAliveAndTargetableVisitor abilityTargetStillAliveVisitor;

	public CBehaviorAttack(final CUnit unit) {
		super(unit);
		this.abilityTargetStillAliveVisitor = new AbilityTargetStillAliveAndTargetableVisitor();
	}

	private CUnitAttack unitAttack;
	private int damagePointLaunchTime;
	private final CExactExpiry damagePoint = new CExactExpiry();
	private int backSwingTime;
	/** Null in games saved before the backswing ended at its exact time; those end on backSwingTime's step. */
	private CExactExpiry backswing;
	private int thisOrderCooldownEndTime;
	private int cooldownEndTimeBeforeSwing;
	/** Game time the cooldown of the last swing ends; 0 in games saved before swings started at exact times. */
	private double cooldownReadyTime;
	private double cooldownReadyTimeBeforeSwing;
	/** Game time an attack order's first swing may start (1.29: after a random wait); 0 for none. */
	private double firstSwingTime;
	/** The first swing still waits a random time, drawn once the unit stands in range and faces its target. */
	private boolean firstSwingWaitPending;
	private double firstSwingOrderTime;
	/** Whether the unit has stood in range and facing its target since the order, so its wait runs from the order. */
	private boolean firstSwingReadyAtOrder;
	private float firstSwingOrderX;
	private double firstSwingOrigin = -1;
	private float firstSwingOrderY;
	/** Null until a swing waits for a time inside a step. */
	private CExactExpiry swingReady;
	private double swingReadyTime;
	private boolean lastWithinFacingWindow;
	private CBehaviorAttackListener attackListener;
	private CUnitAttackModifier forcedModifier;
	private CUnitAttackSettings settings;
	private boolean throughFog;

	private float orderX;
	private float orderY;
	private boolean besideAtOrder;

	public CUnitAttack getUnitAttack() {
		return this.unitAttack;
	}

	public CBehavior reset(final CSimulation game, final int highlightOrderId, final CUnitAttack unitAttack,
			final AbilityTarget target, final boolean disableMove, final CBehaviorAttackListener attackListener) {
		this.highlightOrderId = highlightOrderId;
		this.attackListener = attackListener;
		this.throughFog = false;

		this.unitAttack = unitAttack;
		this.damagePointLaunchTime = 0;
		this.damagePoint.cancel(game);
		cancelBackswing(game);
		this.thisOrderCooldownEndTime = 0;
		this.firstSwingTime = 0;
		this.firstSwingWaitPending = false;
		cancelSwingReady(game);
		setDisableMove(disableMove);
		this.orderX = this.unit.getX();
		this.orderY = this.unit.getY();
		this.besideAtOrder = true;
		return super.innerReset(game, target);
	}

	/**
	 * Measured on 1.29: the first swing of an attack order (also smart, attack-move and auto-acquired targets) starts
	 * on the first unit tick after a random wait of up to 0.25 s, even when the target is already in range and in
	 * front; later swings follow the cooldown. The wait runs from the order when the unit stands ready at once, else
	 * from the moment it has walked into range and turned to its target.
	 */
	public void delayFirstSwing(final CSimulation game) {
		this.firstSwingTime = 0;
		this.firstSwingWaitPending = true;
		this.firstSwingOrderTime = this.firstSwingOrigin >= 0 ? this.firstSwingOrigin : CTimer.currentTime(game);
		this.firstSwingReadyAtOrder = true;
		this.firstSwingOrderX = this.unit.getX();
		this.firstSwingOrderY = this.unit.getY();
	}

	/**
	 * Makes the next first swing count its wait from an earlier order time (an attack-move whose first look for
	 * targets came a step after the order); negative for none.
	 */
	public void setFirstSwingOrigin(final double orderTime) {
		this.firstSwingOrigin = orderTime;
	}

	private void startFirstSwingWait(final CSimulation game) {
		this.firstSwingWaitPending = false;
		final boolean readyAtOrder = this.firstSwingReadyAtOrder
				&& (this.unit.distance(this.firstSwingOrderX, this.firstSwingOrderY) < 1);
		final double now = CTimer.currentTime(game);
		final double from = readyAtOrder ? this.firstSwingOrderTime : now;
		final double wait = game.getCombatRandom().nextFloat() * FIRST_SWING_MAX_WAIT;
		// a wait that ran out before this update still starts the swing on a unit tick
		this.firstSwingTime = Math.max(nextUnitTick(from + wait), nextUnitTick(now));
	}

	public CBehavior reset(final CSimulation game, final int highlightOrderId, final CUnitAttack unitAttack,
			final AbilityTarget target, final boolean disableMove, final CBehaviorAttackListener attackListener, CUnitAttackModifier forcedModifier) {
		this.forcedModifier = forcedModifier;
		return this.reset(game, highlightOrderId, unitAttack, target, disableMove, attackListener);
	}

	@Override
	public int getHighlightOrderId() {
		return this.highlightOrderId;
	}

	@Override
	public boolean isWithinRange(final CSimulation simulation) {
		float range = this.unitAttack.getRange();
		// Measured on 1.29: the motion buffer only keeps a target already reached; a new one is approached during cooldown.
		if (isWasInRange() && (simulation.getGameTurnTick() < this.unit.getCooldownEndTime())) {
			range += this.unitAttack.getRangeMotionBuffer();
		}
		// Measured on 1.29: a building is attacked from range plus both collision sizes off its centre in every
		// direction; a unit that already stands with its footprint touching its pathing when ordered (at a corner
		// too) attacks from there, while one walking up stops only in range.
		final CUnit targetUnit = this.target.visit(AbilityTargetVisitor.UNIT);
		final double distance = this.unit.distance(this.target);
		final boolean reached = ((targetUnit != null) && targetUnit.isBuilding())
				? ((distance <= range) || (this.besideAtOrder && isAtOrderPoint() && this.unit.isBesideBuilding(targetUnit)))
				: this.unit.canReach(this.target, range);
		return reached && (distance >= this.unit.getUnitType().getMinimumAttackRange());
	}

	private boolean isAtOrderPoint() {
		return StrictMath.hypot(this.unit.getX() - this.orderX, this.unit.getY() - this.orderY) < 1;
	}

	/** A chase that keeps its target while the fog hides it (attacking back an unseen attacker). */
	public void setThroughFog(final boolean throughFog) {
		this.throughFog = throughFog;
	}

	@Override
	protected boolean checkTargetStillValid(final CSimulation simulation) {
		if (this.unit.isDisableAttacks()) {
			return false;
		}
		final CUnit targetUnit = this.throughFog ? this.target.visit(AbilityTargetVisitor.UNIT) : null;
		if (targetUnit != null) {
			return !targetUnit.isDead() && !targetUnit.isHidden() && targetUnit.canBeTargetedBy(simulation, this.unit,
					true, this.unitAttack.getTargetsAllowed(),
					BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset(), true);
		}
		return this.target.visit(
				this.abilityTargetStillAliveVisitor.reset(simulation, this.unit, this.unitAttack.getTargetsAllowed()));
	}

	@Override
	protected void resetBeforeMoving(final CSimulation simulation) {
		this.damagePointLaunchTime = 0;
		this.damagePoint.cancel(simulation);
		this.thisOrderCooldownEndTime = 0;
	}

	@Override
	protected CBehavior updateOnInvalidTarget(final CSimulation simulation) {
		if (this.damagePointLaunchTime != 0) {
			// Measured on 1.29: a swing that loses its target before the damage point is dropped at once and costs no
			// cooldown; after the damage point the backswing still plays out.
			this.unit.setCooldownEndTime(this.cooldownEndTimeBeforeSwing);
			this.cooldownReadyTime = this.cooldownReadyTimeBeforeSwing;
			this.damagePointLaunchTime = 0;
			this.damagePoint.cancel(simulation);
			cancelBackswing(simulation);
			return this.attackListener.onFinish(simulation, this.unit);
		}
		if ((this.backSwingTime != 0) && !backswingOver(simulation)) {
			return this;
		}
		this.backSwingTime = 0;
		return this.attackListener.onFinish(simulation, this.unit);
	}

	private void cancelBackswing(final CSimulation simulation) {
		this.backSwingTime = 0;
		if (this.backswing != null) {
			this.backswing.cancel(simulation);
		}
	}

	private boolean backswingOver(final CSimulation simulation) {
		if (this.backswing == null) {
			return simulation.getGameTurnTick() >= this.backSwingTime;
		}
		return this.backswing.check(simulation, this::endBackswingInStep);
	}

	/**
	 * Measured on 1.29: the backswing ends at its exact time (damage point + backswing after the swing start, not on a
	 * step), and the unit goes on with its next order then.
	 */
	private void endBackswingInStep(final CSimulation simulation) {
		if ((this.backSwingTime == 0) || (this.damagePointLaunchTime != 0) || (this.unit.getCurrentBehavior() != this)
				|| this.unit.isDead()) {
			return;
		}
		this.backSwingTime = 0;
		final CBehavior next = checkTargetStillValid(simulation) ? this.attackListener.onFirstUpdateAfterBackswing(this)
				: this.attackListener.onFinish(simulation, this.unit);
		if (next != this) {
			this.unit.beginBehavior(simulation, next);
		}
	}

	private void launch(final CSimulation simulation) {
		final int damage = this.unitAttack.roll(simulation.getCombatRandom());
		this.settings.setBaseDamage(damage);
		final AbilityTarget target = this.target;
		if ((this.forcedModifier != null)
				&& this.forcedModifier.checkApplication(simulation, this.unit, target, this.unitAttack, this.settings)) {
			this.forcedModifier.applyModification(simulation, this.unit, target, this.unitAttack, this.settings, null);
		}
		else {
			this.settings = this.unit.checkForAttackModification(simulation, target, this.unitAttack, this.settings);
		}
		this.forcedModifier = null;
		this.unitAttack.launch(simulation, this.unit, target, damage, this.attackListener);
		this.damagePointLaunchTime = 0;
	}

	/**
	 * A damage point inside a step lands at its exact time (1.29: 0.433 s after the swing starts, not on a step), if
	 * the swing is still on then.
	 */
	private void launchAtDamagePoint(final CSimulation simulation) {
		if ((this.damagePointLaunchTime != 0) && (this.unit.getCurrentBehavior() == this) && !this.unit.isDead()
				&& checkTargetStillValid(simulation)) {
			launch(simulation);
		}
	}

	/** True when a swing may start now; a start inside the coming step is left to a timer at that time. */
	private boolean swingReadyNow(final CSimulation simulation) {
		final double readyTime = Math.max(this.firstSwingTime, this.cooldownReadyTime);
		final double now = CTimer.currentTime(simulation);
		if (readyTime <= (now + TIME_EPSILON)) {
			return true;
		}
		if (this.swingReady == null) {
			this.swingReady = new CExactExpiry();
		}
		if (this.swingReadyTime != readyTime) {
			this.swingReadyTime = readyTime;
			this.swingReady.startRemaining(simulation, readyTime - now);
		}
		return this.swingReady.check(simulation, this::swingInStep);
	}

	private void cancelSwingReady(final CSimulation simulation) {
		this.swingReadyTime = 0;
		if (this.swingReady != null) {
			this.swingReady.cancel(simulation);
		}
	}

	private void swingInStep(final CSimulation simulation) {
		this.swingReadyTime = 0;
		if ((this.unit.getCurrentBehavior() != this) || this.unit.isDead() || this.unit.isPaused()
				|| (this.damagePointLaunchTime != 0) || (this.backSwingTime != 0) || !this.lastWithinFacingWindow
				|| !checkTargetStillValid(simulation) || !isWithinRange(simulation)
				|| this.unit.isWhirlingAttackSuppressed()) {
			return;
		}
		startSwing(simulation);
	}

	private void startSwing(final CSimulation simulation) {
		final int currentTurnTick = simulation.getGameTurnTick();
		final float cooldownTime = this.unitAttack.getCooldownTime();
		final float animationDamagePoint = this.unitAttack.getAnimationDamagePoint();
		final float animationBackswingPoint = this.unitAttack.getAnimationBackswingPoint();
		final int a1DamagePointSteps = Math.max(1, CTimer.ceilSteps(animationDamagePoint));
		this.cooldownEndTimeBeforeSwing = this.unit.getCooldownEndTime();
		this.cooldownReadyTimeBeforeSwing = this.cooldownReadyTime;
		// The next swing may start at the exact end of the cooldown; the unit counts as cooling down until the
		// update before it.
		final double now = CTimer.currentTime(simulation);
		this.cooldownReadyTime = now + CTimer.exactLength(cooldownTime);
		final int cooldownEndTick = Math.max(currentTurnTick + 1, (int) StrictMath
				.ceil((this.cooldownReadyTime / WarsmashConstants.SIMULATION_STEP_TIME) - TIME_EPSILON) - 1);
		this.unit.setCooldownEndTime(cooldownEndTick);
		this.thisOrderCooldownEndTime = cooldownEndTick;
		this.firstSwingTime = 0;
		this.firstSwingWaitPending = false;
		this.damagePointLaunchTime = currentTurnTick + a1DamagePointSteps;
		this.damagePoint.start(simulation, animationDamagePoint);
		this.backSwingTime = currentTurnTick + Math.max(a1DamagePointSteps,
				CTimer.ceilSteps(animationDamagePoint + animationBackswingPoint));
		if (this.backswing == null) {
			this.backswing = new CExactExpiry();
		}
		this.backswing.start(simulation, animationDamagePoint + animationBackswingPoint);
		this.settings = this.unitAttack.initialSettings();
		if (this.forcedModifier != null && this.forcedModifier.checkPreLaunchApplication(simulation, this.unit, target, this.unitAttack,
				this.settings)) {
			this.forcedModifier.applyPreLaunchModification(simulation, this.unit, target, this.unitAttack, this.settings, null);
		} else {
			this.settings = this.unit.checkForAttackPreLaunchModification(simulation, target, unitAttack, this.settings);
		}
		this.unit.getUnitAnimationListener().playAnimationWithDuration(true, PrimaryTag.ATTACK,
				this.settings.getAnimationNames(), animationBackswingPoint + animationDamagePoint, true);
		// Measured: the attacked unit's owner gets the event when each swing starts.
		final CUnit attackedUnit = this.target.visit(AbilityTargetVisitor.UNIT);
		if (attackedUnit != null) {
			attackedUnit.fireAttackedEvents(simulation, this.unit);
		}
		// Measured: every swing counts as an attack for effects that end when the unit attacks, so a unit that keeps
		// swinging stays visible (Permanent Invisibility fades only after a gap in its attacks).
		this.unit.fireBehaviorChangeEvent(simulation, this, true);
		this.unit.getUnitAnimationListener().queueAnimation(PrimaryTag.STAND, SequenceUtils.READY, false);
		if ((this.damagePointLaunchTime != 0)
				&& this.damagePoint.check(simulation, this::launchAtDamagePoint)) {
			launch(simulation);
		}
	}

	@Override
	public CBehavior update(final CSimulation simulation, final boolean withinFacingWindow) {
		final int cooldownEndTime = this.unit.getCooldownEndTime();
		final int currentTurnTick = simulation.getGameTurnTick();
		this.lastWithinFacingWindow = withinFacingWindow;
		if (withinFacingWindow) {
			if (this.damagePointLaunchTime != 0) {
				if (this.damagePoint.check(simulation, this::launchAtDamagePoint)) {
					launch(simulation);
				}
			}
			else {
				if (this.firstSwingWaitPending) {
					startFirstSwingWait(simulation);
				}
				if ((currentTurnTick >= cooldownEndTime) && !this.unit.isWhirlingAttackSuppressed()
						&& swingReadyNow(simulation)) {
					startSwing(simulation);
				}
				else if (currentTurnTick >= this.thisOrderCooldownEndTime) {
					this.unit.getUnitAnimationListener().playAnimation(false, PrimaryTag.STAND, SequenceUtils.READY,
							1.0f, false);
				}
			}
		}
		else {
			this.firstSwingReadyAtOrder = false;
			this.unit.getUnitAnimationListener().playAnimation(false, PrimaryTag.STAND, SequenceUtils.READY, 1.0f,
					false);
		}
		if ((this.backSwingTime != 0) && (this.damagePointLaunchTime == 0) && backswingOver(simulation)) {
			this.backSwingTime = 0;
			return this.attackListener.onFirstUpdateAfterBackswing(this);
		}
		return this;
	}

	@Override
	public void begin(final CSimulation game) {
		if (this.unit.isMovementDisabled()) {
			this.unit.getUnitAnimationListener().lockTurretFacing(this.target);
		}
	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {
		if (this.unit.isMovementDisabled()) {
			this.unit.getUnitAnimationListener().clearTurretFacing();
		}
	}

	@Override
	public void endMove(final CSimulation game, final boolean interrupted) {

	}

	@Override
	public boolean interruptable() {
		return true;
	}

	@Override
	public CBehaviorCategory getBehaviorCategory() {
		return CBehaviorCategory.ATTACK;
	}

}
