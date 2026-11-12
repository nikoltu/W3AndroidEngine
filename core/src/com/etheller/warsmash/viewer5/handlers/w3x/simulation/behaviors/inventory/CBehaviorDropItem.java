package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.inventory;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetStillAliveVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CAbstractRangedBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CExactExpiry;

public class CBehaviorDropItem extends CAbstractRangedBehavior {
	/** Measured on 1.29: the item leaves the unit this long after it stands within the drop range. */
	private static final double DROP_DELAY = 0.12;

	private final CAbilityInventory inventory;
	private CItem targetItem;
	private final CExactExpiry dropTime = new CExactExpiry();
	private boolean dropPending;

	public CBehaviorDropItem(final CUnit unit, final CAbilityInventory inventory) {
		super(unit);
		this.inventory = inventory;
	}

	public CBehavior reset(CSimulation game, final CItem targetItem, final AbilityPointTarget targetPoint) {
		this.targetItem = targetItem;
		this.dropTime.cancel(game);
		this.dropPending = false;
		return innerReset(game, targetPoint);
	}

	@Override
	public boolean isWithinRange(final CSimulation simulation) {
		// Measured on 1.29: the range counts from the unit's center, without its collision size.
		final double dx = this.target.getX() - this.unit.getX();
		final double dy = this.target.getY() - this.unit.getY();
		final float range = simulation.getGameplayConstants().getDropItemRange();
		return StrictMath.sqrt((dx * dx) + (dy * dy)) <= range;
	}

	@Override
	public void endMove(final CSimulation game, final boolean interrupted) {
	}

	@Override
	public void begin(final CSimulation game) {
	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {
		this.dropTime.cancel(game);
		this.dropPending = false;
	}

	@Override
	public int getHighlightOrderId() {
		return OrderIds.dropitem;
	}

	@Override
	protected CBehavior update(final CSimulation simulation, final boolean withinFacingWindow) {
		if (!this.dropPending) {
			this.dropPending = true;
			this.dropTime.start(simulation, DROP_DELAY);
		}
		if (this.dropTime.check(simulation, this::dropInStep)) {
			return drop(simulation);
		}
		return this;
	}

	private CBehavior drop(final CSimulation simulation) {
		this.dropPending = false;
		this.inventory.dropItem(simulation, this.unit, this.targetItem, this.target.getX(), this.target.getY(), true);
		return this.unit.pollNextOrderBehavior(simulation);
	}

	private void dropInStep(final CSimulation simulation) {
		if (!this.dropPending || (this.unit.getCurrentBehavior() != this) || this.unit.isDead()) {
			return;
		}
		this.unit.beginBehavior(simulation, drop(simulation));
	}

	@Override
	protected CBehavior updateOnInvalidTarget(final CSimulation simulation) {
		return this.unit.pollNextOrderBehavior(simulation);
	}

	@Override
	protected boolean checkTargetStillValid(final CSimulation simulation) {
		return this.target.visit(AbilityTargetStillAliveVisitor.INSTANCE);
	}

	@Override
	protected void resetBeforeMoving(final CSimulation simulation) {
		this.dropTime.cancel(simulation);
		this.dropPending = false;
	}

	@Override
	public boolean interruptable() {
		return true;
	}

	@Override
	public CBehaviorCategory getBehaviorCategory() {
		return CBehaviorCategory.MOVEMENT;
	}

}
