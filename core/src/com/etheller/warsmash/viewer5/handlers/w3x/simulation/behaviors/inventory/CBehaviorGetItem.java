package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.inventory;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerScore;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetItemVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetStillAliveVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CAbstractRangedBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

public class CBehaviorGetItem extends CAbstractRangedBehavior {
	private final CAbilityInventory inventory;

	public CBehaviorGetItem(final CUnit unit, final CAbilityInventory inventory) {
		super(unit);
		this.inventory = inventory;
	}

	public CBehavior reset(CSimulation game, final CItem targetItem) {
		return innerReset(game, targetItem);
	}

	@Override
	public boolean isWithinRange(final CSimulation simulation) {
		return this.unit.canReach(this.target, simulation.getGameplayConstants().getPickupItemRange());
	}

	@Override
	public void endMove(final CSimulation game, final boolean interrupted) {
	}

	@Override
	public void begin(final CSimulation game) {
	}

	@Override
	public void end(final CSimulation game, final boolean interrupted) {
	}

	@Override
	public int getHighlightOrderId() {
		return OrderIds.getitem;
	}

	@Override
	protected CBehavior update(final CSimulation simulation, final boolean withinFacingWindow) {
		final CItem targetItem = this.target.visit(AbilityTargetItemVisitor.INSTANCE);
		if (this.inventory.giveItem(simulation, this.unit, targetItem, true) != -1) {
			// Measured: items picked up count for the score, items a script gives do not.
			simulation.getPlayer(this.unit.getPlayerIndex()).addScore(CPlayerScore.ITEMS_GAINED, 1);
		}
		return this.unit.pollNextOrderBehavior(simulation);
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
