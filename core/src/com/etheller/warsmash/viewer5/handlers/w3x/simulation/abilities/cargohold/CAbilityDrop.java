package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractGenericSingleIconNoSmartActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.cargohold.CBehaviorDrop;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

public class CAbilityDrop extends AbstractGenericSingleIconNoSmartActiveAbility {
	private float castRange;
	private CBehaviorDrop behaviorDrop;

	public CAbilityDrop(final int handleId, final War3ID code, final War3ID alias, final float castRange) {
		super(handleId, code, alias);
		this.castRange = castRange;
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		this.behaviorDrop = new CBehaviorDrop(unit, this);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {

	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.unloadall;
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		return null;
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		return this.behaviorDrop.reset(game, point);
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder) {
		return caster.pollNextOrderBehavior(game);
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		receiver.useOk();
	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId) {
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
	}

	@Override
	public boolean isToggleOn() {
		return false;
	}

	@Override
	protected void innerCheckCanTarget(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		receiver.orderIdNotAccepted();
	}

	private static final float LAND_SEARCH_RADIUS = 96f;

	/** Whether a unit of that movement type can stand at or right next to the point. */
	public static boolean isNearLand(final PathingGrid grid, final float x, final float y,
			final MovementType movementType) {
		for (float dy = -LAND_SEARCH_RADIUS; dy <= LAND_SEARCH_RADIUS; dy += 32f) {
			for (float dx = -LAND_SEARCH_RADIUS; dx <= LAND_SEARCH_RADIUS; dx += 32f) {
				if (grid.contains(x + dx, y + dy) && grid.isPathable(x + dx, y + dy, movementType)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected void innerCheckCanTarget(final CSimulation game, final CUnit unit, final int orderId,
			final AbilityPointTarget target, final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		if (!isNearLand(game.getPathingGrid(), target.getX(), target.getY(), MovementType.FOOT)) {
			// Measured on 1.29: unload-all on water is refused.
			receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_TARGET_THERE);
		}
		else if (!unit.isMovementDisabled() || unit.canReach(target, this.castRange)) {
			receiver.targetOk(target);
		}
		else {
			receiver.targetCheckFailed(CommandStringErrorKeys.TARGET_IS_OUTSIDE_RANGE);
		}
	}

	@Override
	public boolean needsTargetFromCommandCard(final int orderId) {
		return orderId == OrderIds.unloadall;
	}

	@Override
	protected void innerCheckCanTargetNoTarget(final CSimulation game, final CUnit unit, final int orderId,
			final AbilityTargetCheckReceiver<Void> receiver) {
		// Measured on 1.29: unload-all without a target is taken and does nothing.
		receiver.targetOk(null);
	}

	public float getCastRange() {
		return this.castRange;
	}

	public void setCastRange(final float castRange) {
		this.castRange = castRange;
	}

	@Override
	public boolean isPhysical() {
		return false;
	}

	@Override
	public boolean isMagic() {
		return false;
	}

	@Override
	public boolean isUniversal() {
		return false;
	}

	@Override
	public CAbilityCategory getAbilityCategory() {
		return CAbilityCategory.CORE;
	}
}
