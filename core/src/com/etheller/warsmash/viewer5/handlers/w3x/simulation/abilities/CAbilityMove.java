package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.neutral.CAbilityWayGate;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorBoardTransport;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorFollow;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorHoldPosition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMoveThroughWayGate;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorPatrol;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

public class CAbilityMove extends AbstractCAbility {
	public static final War3ID CODE = War3ID.fromString("Amov");

	public CAbilityMove(final int handleId) {
		super(handleId, CODE);
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		receiver.useOk();
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		switch (orderId) {
		case OrderIds.smart:
		case OrderIds.patrol:
		case OrderIds.move:
			if ((orderId == OrderIds.smart) && (target instanceof CUnit)
					&& CAbilityAttack.isUnhittableEnemy(game, unit, (CUnit) target, receiver)) {
				// Measured on 1.29: a smart order on an enemy no weapon can hit (a Footman on a flyer) is refused,
				// not taken as a follow.
				break;
			}
			if ((target instanceof CUnit) && (target != unit)) {
				receiver.targetOk(target);
			}
			else if ((orderId == OrderIds.move) && (target instanceof CItem)) {
				// Measured on 1.29: a move order on an item walks to where it lies and picks nothing up.
				receiver.targetOk(target);
			}
			else {
				receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_UNIT_WITH_THIS_ACTION);
			}
			break;
		case OrderIds.board:
			if ((target instanceof CUnit) && (target != unit) && (unit.getBoardTransportBehavior()
					.getPartnerAbility(game, unit, (CUnit) target, true, true) != null)) {
				receiver.targetOk(target);
			}
			else {
				receiver.targetCheckFailed(CommandStringErrorKeys.UNABLE_TO_LOAD_TARGET);
			}
			break;
		case OrderIds.attack:
			// Measured on 1.29: a unit without an attack (Goblin Sapper, or one whose 'Aatk' a script removed) takes
			// an attack order on a unit or a destructable and walks up to it.
			if (hasNoAttack(unit) && (target != unit)) {
				receiver.targetOk(target);
			}
			else {
				receiver.orderIdNotAccepted();
			}
			break;
		default:
			receiver.orderIdNotAccepted();
			break;
		}
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget target,
			final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		switch (orderId) {
		case OrderIds.smart:
		case OrderIds.move:
		case OrderIds.patrol:
			receiver.targetOk(target);
			break;
		case OrderIds.attack:
			// Measured on 1.29: a unit without an attack (transport ship) takes an attack-move and moves.
			if (hasNoAttack(unit)) {
				receiver.targetOk(target);
			}
			else {
				receiver.orderIdNotAccepted();
			}
			break;
		default:
			receiver.orderIdNotAccepted();
			break;
		}
	}

	@Override
	public void checkCanTargetNoTarget(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTargetCheckReceiver<Void> receiver) {
		switch (orderId) {
		case OrderIds.holdposition:
			receiver.targetOk(null);
			break;
		default:
			receiver.orderIdNotAccepted();
			break;
		}
	}

	private static boolean hasNoAttack(final CUnit unit) {
		return unit.getCurrentAttacks().isEmpty() || (unit.getAttackBehavior() == null);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		unit.setMoveBehavior(new CBehaviorMove(unit));
		unit.setFollowBehavior(new CBehaviorFollow(unit));
		unit.setPatrolBehavior(new CBehaviorPatrol(unit));
		unit.setHoldPositionBehavior(new CBehaviorHoldPosition(unit));
		unit.setBoardTransportBehavior(new CBehaviorBoardTransport(unit));
		if ((unit.getAttackBehavior() != null) && (unit.getAttackMoveBehavior() == null)) {
			unit.setAttackMoveBehavior(new CBehaviorAttackMove(unit));
		}
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {

	}

	@Override
	public void onRemoveDisabled(final CSimulation game, final CUnit unit) {
		// Removed for good (a script removing 'Amov'): the unit stays where it is, as a unit without move speed.
		unit.releaseBehaviors(game, unit.getMoveBehavior(), unit.getFollowBehavior(), unit.getPatrolBehavior(),
				unit.getHoldPositionBehavior(), unit.getBoardTransportBehavior(), unit.getAttackMoveBehavior());
		unit.setMoveBehavior(null);
		unit.setFollowBehavior(null);
		unit.setPatrolBehavior(null);
		unit.setHoldPositionBehavior(null);
		unit.setBoardTransportBehavior(null);
		unit.setAttackMoveBehavior(null);
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		return true;
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		final boolean smart = orderId == OrderIds.smart;
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit != null) {
			if (orderId == OrderIds.attack) {
				// Measured on 1.29: the unit walks up to where its target stands and the order ends there; it does
				// not follow.
				return caster.getMoveBehavior().reset(OrderIds.move,
						new AbilityPointTarget(targetUnit.getX(), targetUnit.getY()));
			}
			CBehavior behavior = null;
			if (smart || (orderId == OrderIds.board)) {
				final CBehaviorBoardTransport boardTransportBehavior = caster.getBoardTransportBehavior();
				final CAbilityRanged transportLoad = boardTransportBehavior.getPartnerAbility(game, caster, targetUnit,
						true, true);
				if (transportLoad != null) {
					behavior = boardTransportBehavior.reset(game, OrderIds.move, targetUnit);
				}
			}
			else if ((orderId == OrderIds.move)
					&& (CAbilityWayGate.getWayGateAbility(game, caster, targetUnit, true, false) != null)) {
				behavior = new CBehaviorMoveThroughWayGate(caster).reset(game, targetUnit);
			}
			if ((behavior == null) && targetUnit.isBuilding()) {
				// Measured on 1.29: a structure is walked up to, as far as it lets the unit, not followed; any other
				// unit is followed, an enemy too (without attacking it or anything else on the way).
				behavior = caster.getMoveBehavior().reset(OrderIds.move,
						new AbilityPointTarget(targetUnit.getX(), targetUnit.getY()));
			}
			if (behavior == null) {
				behavior = caster.getFollowBehavior().reset(game, smart ? OrderIds.move : orderId, targetUnit, smart);
				caster.setDefaultBehavior(behavior);
			}
			return behavior;
		}
		if ((orderId == OrderIds.attack) || ((orderId == OrderIds.move) && (target instanceof CItem))) {
			return caster.getMoveBehavior().reset(OrderIds.move, new AbilityPointTarget(target.getX(), target.getY()));
		}
		// NOTE: shouldn't happen, target is always unit for this ability
		return caster.pollNextOrderBehavior(game);
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		if (orderId == OrderIds.patrol) {
			final CBehavior patrolBehavior = caster.getPatrolBehavior().reset(point);
			caster.setDefaultBehavior(patrolBehavior);
			return patrolBehavior;
		}
		else {
			return caster.getMoveBehavior().reset(OrderIds.move, point);
		}
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder) {
		if (orderId == OrderIds.holdposition) {
			caster.setDefaultBehavior(caster.getHoldPositionBehavior());
		}
		return caster.pollNextOrderBehavior(game);
	}

	@Override
	public <T> T visit(final CAbilityVisitor<T> visitor) {
		return visitor.accept(this);
	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId) {
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
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
		return CAbilityCategory.MOVEMENT;
	}

}
