package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

public class CBehaviorAttackMove implements CRangedBehavior {

	/** Measured (1.29): on an attack-move or a patrol a unit looks for targets at the order, then every half second. */
	public static final int SCAN_TICKS = CTimer.wholeSteps(0.5f);

	private final CUnit unit;
	private AbilityPointTarget target;
	private boolean justAutoAttacked = false;
	private int nextScanTick = Integer.MIN_VALUE;
	private boolean endedMove = false;
	private boolean continueMove = false;
	/** Game time of the order until the first look for targets; negative after it. */
	private double orderTime = -1;

	public CBehaviorAttackMove(final CUnit unit) {
		this.unit = unit;
	}

	public CBehavior reset(final CSimulation game, final AbilityPointTarget target) {
		final CBehavior behavior = reset(target);
		this.orderTime = CTimer.currentTime(game);
		return behavior;
	}

	public CBehavior reset(final AbilityPointTarget target) {
		this.orderTime = -1;
		this.nextScanTick = Integer.MIN_VALUE;
		// Measured on 1.29: an attack-move given again to the same point walks on along its way, as a repeated move
		// does; starting the path search over at every repeat left the unit walking straight into the nearest pocket.
		final CBehaviorMove moveBehavior = this.unit.getMoveBehavior();
		this.continueMove = (this.unit.getCurrentBehavior() == moveBehavior)
				&& moveBehavior.continuesAttackMove(this, target);
		this.target = target;
		this.endedMove = false;
		return this;
	}

	@Override
	public int getHighlightOrderId() {
		return OrderIds.attack;
	}

	@Override
	public boolean isWithinRange(final CSimulation simulation) {
		final CBehaviorAttack attackBehavior = this.unit.getAttackBehavior();
		// Measured on 1.29: a target in range at an attack-move order is taken at the order, and the first swing's
		// wait counts from there.
		if ((this.orderTime >= 0) && (attackBehavior != null)) {
			attackBehavior.setFirstSwingOrigin(this.orderTime);
		}
		this.orderTime = -1;
		final int tick = simulation.getGameTurnTick();
		this.justAutoAttacked = false;
		if (tick >= this.nextScanTick) {
			this.nextScanTick = tick + SCAN_TICKS;
			this.justAutoAttacked = this.unit.autoAcquireAttackTargets(simulation, false);
		}
		if (attackBehavior != null) {
			attackBehavior.setFirstSwingOrigin(-1);
		}
		if (this.justAutoAttacked) {
			// kind of a hack
			return true;
		}
		return innerIsWithinRange(); // TODO this is not how it was meant to be used
	}

	private boolean innerIsWithinRange() {
		// Measured on 1.29: an attack-move to a point walks on to the point as a move does, whatever the unit's size.
		return (this.unit.distance(this.target.x, this.target.y)
				+ this.unit.getUnitType().getCollisionSize()) <= CBehaviorMove.MOVE_END_DISTANCE;
	}

	@Override
	public CBehavior update(final CSimulation simulation) {
		if ((this.orderTime >= 0) && isWithinRange(simulation) && this.justAutoAttacked) {
			// the first update comes at the order: a target already in range is taken then, not a step later
			this.justAutoAttacked = false;
			return this.unit.getCurrentBehavior();
		}
		if (this.justAutoAttacked) {
			this.justAutoAttacked = false;
			return this.unit.getCurrentBehavior();
		}
		if (innerIsWithinRange()) {
			this.unit.setDefaultBehavior(this.unit.getStopBehavior());
			return this.unit.pollNextOrderBehavior(simulation);
		}
		if (this.continueMove) {
			this.continueMove = false;
			if (this.unit.getMoveBehavior().continuesAttackMove(this, this.target)) {
				return this.unit.getMoveBehavior();
			}
		}
		return this.unit.getMoveBehavior().reset(this.target, this, false);
	}

	@Override
	public void begin(final CSimulation game) {

	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {
	}

	@Override
	public void endMove(final CSimulation game, final boolean interrupted) {
	}

	@Override
	public boolean interruptable() {
		return true;
	}

	@Override
	public AbilityTarget getTarget() {
		return this.target;
	}

	@Override
	public <T> T visit(final CBehaviorVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public CBehaviorCategory getBehaviorCategory() {
		return CBehaviorCategory.MOVEMENT;
	}

}
