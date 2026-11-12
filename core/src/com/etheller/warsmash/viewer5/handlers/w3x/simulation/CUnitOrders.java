package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.blademaster.CAbilityMirrorImage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.CBehaviorAbilityBuilderNoTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CRangedBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.skills.CBehaviorNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.skills.CBehaviorTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderNoTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetPoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrderTargetWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

/**
 * Issuing orders to a unit: the order queue, starting behaviors and ordering by id.
 */
final class CUnitOrders {
	private CUnitOrders() {
	}

	static void beginBehavior(final CUnit self, final CSimulation game, final CBehavior behavior) {
		beginBehavior(self, game, behavior, false);
	}

	static void beginBehavior(final CUnit self, final CSimulation game, final CBehavior behavior, boolean interrupted) {
		if (self.orders.currentBehavior != behavior) {
			final int lastBehaviorHighlightOrderId = self.orders.currentBehavior != null
					? self.orders.currentBehavior.getHighlightOrderId()
					: -1;
			if (self.orders.currentBehavior == null) {
				self.acquisition.firstIdleAcquireScanTick = game.getGameTurnTick()
						+ CUnitAcquisition.firstIdleAcquireScanDelay(self);
				self.acquisition.nextIdleAcquireScanTick = self.acquisition.firstIdleAcquireScanTick;
			} else {
				self.acquisition.nextIdleAcquireScanTick = game.getGameTurnTick();
			}
			if (self.orders.currentBehavior != null) {
				self.orders.currentBehavior.end(game, interrupted);
			}

			self.fireBehaviorChangeEvent(game, behavior, false);

			self.orders.currentBehavior = behavior;

			if (self.orders.currentBehavior != null) {
				self.orders.currentBehavior.begin(game);

				if (self.orders.currentBehavior.getHighlightOrderId() != lastBehaviorHighlightOrderId) {
					self.stateNotifier.ordersChanged();
				}
			}
		}
	}

	static void order(final CUnit self, final CSimulation game, final COrder order, final boolean queue) {
		order(self, game, order, queue, false);
	}

	static void order(final CUnit self, final CSimulation game, final COrder order, final boolean queue, final boolean autoOrder) {
		if (self.isDead() || CAbilityMirrorImage.isHidingCaster(self)) {
			return;
		}

		if (order != null) {
			final CAbility ability = game.getAbility(order.getAbilityHandleId());

			if (ability != null) {
				if (!self.getAbilities().contains(ability)) {
					// not allowed to use ability of other unit...
					return;
				}
				// Allow the ability to response to the order without actually placing itself in
				// the queue, nor modifying (interrupting) the queue.
				if (!ability.checkBeforeQueue(game, self, order.getPlayerIndex(), order.getOrderId(), autoOrder, order.getTarget(game))) {
					// TODO is this a possible bug vector that the network request doesn't
					// checkCanUse like the UI before checkBeforeQueue is called??
					order.fireEvents(game, self);
					self.stateNotifier.ordersChanged();
					return;
				}
			}
		}

		if ((self.orders.lastStartedOrder != null) && self.orders.lastStartedOrder.equals(order)
				&& (self.orders.lastStartedOrder.getOrderId() == OrderIds.smart)) {
			// I skip your spammed move orders, TODO this will probably break some repeat
			// attack order or something later
			return;
		}
		if (!queue && (!self.orders.acceptingOrders
				|| ((self.orders.currentBehavior != null) && !self.orders.currentBehavior.interruptable()))) {
			for (final COrder queuedOrder : self.orders.orderQueue) {
				if (queuedOrder != null) {
					final int abilityHandleId = queuedOrder.getAbilityHandleId();
					final CAbility ability = game.getAbility(abilityHandleId);
					if (ability != null) {
						ability.onCancelFromQueue(game, self, queuedOrder.getPlayerIndex(), queuedOrder.getOrderId());
					}
				}
			}
			self.orders.orderQueue.clear();
			self.orders.orderQueue.add(order);
			self.stateNotifier.ordersChanged();
			self.stateNotifier.waypointsChanged();
		} else if (queue && (self.orders.currentBehavior != self.stopBehavior)
				&& (self.orders.currentBehavior != self.holdPositionBehavior)) {
			if (order.getOrderId() == OrderIds.patrol) {
				if (self.orders.defaultBehavior == self.patrolBehavior) {
					self.patrolBehavior.addPatrolPoint(order.getTarget(game));
				} else {
					self.orders.orderQueue.add(order);
				}
			} else {
				self.orders.orderQueue.add(order);
			}
			self.stateNotifier.waypointsChanged();
		} else {
			// Measured (1.29): an order makes the unit look at once, but not before a new unit's first look.
			self.acquisition.nextIdleAcquireScanTick = Math.max(game.getGameTurnTick(),
					self.acquisition.firstIdleAcquireScanTick);
			if ((order != null)
					&& ((order.getOrderId() == OrderIds.attack) || (order.getOrderId() == OrderIds.smart))) {
				final AbilityTarget orderTarget = order.getTarget(game);
				final CUnit orderTargetUnit = (orderTarget != null) ? orderTarget.visit(AbilityTargetVisitor.UNIT)
						: null;
				if (orderTargetUnit != null) {
					orderTargetUnit.onAttackOrdered(game, self);
				}
			}
			self.setDefaultBehavior(self.stopBehavior);
			final boolean walking = (self.moveBehavior != null) && (self.orders.currentBehavior == self.moveBehavior);
			final CRangedBehavior walkingFor = walking ? self.moveBehavior.getRangedBehavior() : null;
			final int moveResets = walking ? self.moveBehavior.getResetCount() : 0;
			final CBehavior nextBehavior = beginOrder(self, game, order);
			if (walking && (self.moveBehavior.getResetCount() != moveResets)) {
				// The new order took over the move the unit was on (one move behaviour per unit), so it is never
				// ended; the behaviour it was walking for still has to learn it was interrupted (a build pays back).
				if ((walkingFor != null) && (self.moveBehavior.getRangedBehavior() != walkingFor)) {
					walkingFor.endMove(game, true);
				}
				// Ending that move below must not tell the new order it was interrupted: a build order whose site is
				// already in reach would pay its cost back before it starts.
				if (nextBehavior != self.moveBehavior) {
					self.moveBehavior.takenOver();
				}
			}
			beginBehavior(self, game, nextBehavior, true);
			for (final COrder queuedOrder : self.orders.orderQueue) {
				if (queuedOrder != null) {
					final int abilityHandleId = queuedOrder.getAbilityHandleId();
					final CAbility ability = game.getAbility(abilityHandleId);
					ability.onCancelFromQueue(game, self, queuedOrder.getPlayerIndex(), queuedOrder.getOrderId());
				}
			}
			self.orders.orderQueue.clear();
			self.stateNotifier.ordersChanged();
			self.stateNotifier.waypointsChanged();
			startCastInOrder(self, game, nextBehavior);
		}
	}

	/**
	 * Measured on 1.29: a cast that needs no turn starts inside the order call (its channel event fires before the
	 * order returns), and one that turns counts its turn ticks from the order's own time, not from the next step.
	 */
	static void startCastInOrder(final CUnit self, final CSimulation game, final CBehavior behavior) {
		if (self.isDead() || self.paused || self.production.constructing || (self.orders.currentBehavior != behavior)
				|| !((behavior instanceof CBehaviorTargetSpellBase) || (behavior instanceof CBehaviorNoTargetSpellBase)
						|| (behavior instanceof CBehaviorAbilityBuilderNoTarget))) {
			return;
		}
		final CBehavior next = behavior.update(game);
		if ((next != behavior) && (self.orders.currentBehavior == behavior)) {
			beginBehavior(self, game, next);
		}
	}

	static boolean order(final CUnit self, final CSimulation simulation, final int orderId, final AbilityTarget target) {
		return order(self, simulation, orderId, target, false);
	}

	static boolean order(final CUnit self, final CSimulation simulation, final int orderId, final AbilityTarget target,
			final boolean autoOrder) {
		if (orderId == OrderIds.stop) {
			order(self, simulation, new COrderNoTarget(self.playerIndex, 0, orderId, false), false, autoOrder);
			return true;
		}
		if (self.removedFromGame || CAbilityMirrorImage.isHidingCaster(self)) {
			return false;
		}
		for (final CAbility ability : self.abilities) {
			// An order built on an ability the simulation no longer knows comes straight back here (COrder*.begin).
			if (simulation.getAbility(ability.getHandleId()) != ability) {
				continue;
			}
			final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE.reset();
			ability.checkCanUse(simulation, self, self.playerIndex, orderId, false, activationReceiver);
			if (activationReceiver.isOk()) {
				if (target == null) {
					final BooleanAbilityTargetCheckReceiver<Void> booleanTargetReceiver = BooleanAbilityTargetCheckReceiver
							.<Void>getInstance().reset();
					ability.checkCanTargetNoTarget(simulation, self, self.playerIndex, orderId, false, booleanTargetReceiver);
					if (booleanTargetReceiver.isTargetable()) {
						order(self, simulation, new COrderNoTarget(self.playerIndex, ability.getHandleId(), orderId, false, autoOrder), false,
								autoOrder);
						return true;
					}
				} else {
					final boolean targetable = target.visit(new AbilityTargetVisitor<Boolean>() {
						@Override
						public Boolean accept(final AbilityPointTarget target) {
							final BooleanAbilityTargetCheckReceiver<AbilityPointTarget> booleanTargetReceiver = BooleanAbilityTargetCheckReceiver
									.<AbilityPointTarget>getInstance().reset();
							ability.checkCanTarget(simulation, self, self.playerIndex, orderId, false, target,
									booleanTargetReceiver);
							final boolean pointTargetable = booleanTargetReceiver.isTargetable();
							if (pointTargetable) {
								order(self, simulation,
										new COrderTargetPoint(self.playerIndex, ability.getHandleId(), orderId, target, false, autoOrder),
										false, autoOrder);
							}
							return pointTargetable;
						}

						public Boolean acceptWidget(final CWidget target) {
							final BooleanAbilityTargetCheckReceiver<CWidget> booleanTargetReceiver = BooleanAbilityTargetCheckReceiver
									.<CWidget>getInstance().reset();
							ability.checkCanTarget(simulation, self, self.playerIndex, orderId, autoOrder, target,
									booleanTargetReceiver);
							final boolean widgetTargetable = booleanTargetReceiver.isTargetable();
							if (widgetTargetable) {
								order(self, simulation, new COrderTargetWidget(self.playerIndex, ability.getHandleId(), orderId,
										target.getHandleId(), false, autoOrder), false, autoOrder);
							}
							return widgetTargetable;
						}

						@Override
						public Boolean accept(final CUnit target) {
							return acceptWidget(target);
						}

						@Override
						public Boolean accept(final CDestructable target) {
							return acceptWidget(target);
						}

						@Override
						public Boolean accept(final CItem target) {
							return acceptWidget(target);
						}
					});
					if (targetable) {
						return true;
					}
				}
			}
		}
		return false;
	}

	static CBehavior beginOrder(final CUnit self, final CSimulation game, final COrder order) {
		self.orders.lastStartedOrder = order;
		CBehavior nextBehavior;
		if (order != null) {
			nextBehavior = order.begin(game, self);
		} else {
			nextBehavior = self.orders.defaultBehavior;
		}
		return nextBehavior;
	}

	static CBehavior pollNextOrderBehavior(final CUnit self, final CSimulation game) {
		if (self.orders.defaultBehavior != self.stopBehavior) {
			// kind of a stupid hack, meant to align in feel with some behaviors that were
			// observed on War3
			return self.orders.defaultBehavior;
		}
		if (self.orders.interruptedBehavior != null) {
			return self.orders.interruptedBehavior;
		}
		final COrder order = self.orders.orderQueue.poll();
		final CBehavior nextOrderBehavior = beginOrder(self, game, order);
		self.stateNotifier.waypointsChanged();
		return nextOrderBehavior;
	}

	static final class UseAbilityOnTargetByIdVisitor implements AbilityTargetVisitor<Void> {
		static final UseAbilityOnTargetByIdVisitor INSTANCE = new UseAbilityOnTargetByIdVisitor();
		CSimulation game;
		CUnit trainedUnit;
		int playerIndex;
		int rallyOrderId;

		UseAbilityOnTargetByIdVisitor reset(final CSimulation game, final CUnit trainedUnit,
				final int playerIndex, final int rallyOrderId) {
			this.game = game;
			this.trainedUnit = trainedUnit;
			this.playerIndex = playerIndex;
			this.rallyOrderId = rallyOrderId;
			return this;
		}

		@Override
		public Void accept(final AbilityPointTarget target) {
			CAbility abilityToUse = null;
			for (final CAbility ability : this.trainedUnit.getAbilities()) {
				ability.checkCanUse(this.game, this.trainedUnit, this.playerIndex, this.rallyOrderId, false,
						BooleanAbilityActivationReceiver.INSTANCE.reset());
				if (BooleanAbilityActivationReceiver.INSTANCE.isOk()) {
					final BooleanAbilityTargetCheckReceiver<AbilityPointTarget> targetCheckReceiver = BooleanAbilityTargetCheckReceiver
							.<AbilityPointTarget>getInstance().reset();
					ability.checkCanTarget(this.game, this.trainedUnit, this.playerIndex, this.rallyOrderId, false, target,
							targetCheckReceiver);
					if (targetCheckReceiver.isTargetable()) {
						abilityToUse = ability;
					}
				}
			}
			if (abilityToUse != null) {
				this.trainedUnit.order(this.game, new COrderTargetPoint(this.playerIndex, abilityToUse.getHandleId(),
						this.rallyOrderId, target, false), false);
			}
			return null;
		}

		@Override
		public Void accept(final CUnit targetUnit) {
			return acceptWidget(this.game, this.trainedUnit, this.rallyOrderId, targetUnit);
		}

		Void acceptWidget(final CSimulation game, final CUnit trainedUnit, final int rallyOrderId,
				final CWidget target) {
			CAbility abilityToUse = null;
			for (final CAbility ability : trainedUnit.getAbilities()) {
				ability.checkCanUse(game, trainedUnit, this.playerIndex, rallyOrderId, false, BooleanAbilityActivationReceiver.INSTANCE.reset());
				if (BooleanAbilityActivationReceiver.INSTANCE.isOk()) {
					final BooleanAbilityTargetCheckReceiver<CWidget> targetCheckReceiver = BooleanAbilityTargetCheckReceiver
							.<CWidget>getInstance().reset();
					ability.checkCanTarget(game, trainedUnit, this.playerIndex, rallyOrderId, false, target, targetCheckReceiver);
					if (targetCheckReceiver.isTargetable()) {
						abilityToUse = ability;
					}
				}
			}
			if (abilityToUse != null) {
				trainedUnit.order(game, new COrderTargetWidget(this.playerIndex, abilityToUse.getHandleId(),
						rallyOrderId, target.getHandleId(), false), false);
			}
			return null;
		}

		@Override
		public Void accept(final CDestructable target) {
			return acceptWidget(this.game, this.trainedUnit, this.rallyOrderId, target);
		}

		@Override
		public Void accept(final CItem target) {
			return acceptWidget(this.game, this.trainedUnit, this.rallyOrderId, target);
		}
	}

	/**
	 * An ability that owned these behaviors left the unit: a unit busy with one of them (or walking for one) stops,
	 * and one that would return to one of them by default returns to stop instead.
	 */
	static void releaseBehaviors(final CUnit self, final CSimulation game, final CBehavior... released) {
		boolean busy = false;
		for (final CBehavior behavior : released) {
			if (behavior == null) {
				continue;
			}
			if (self.orders.defaultBehavior == behavior) {
				self.orders.defaultBehavior = self.stopBehavior;
			}
			if ((self.orders.currentBehavior == behavior) || ((self.orders.currentBehavior == self.moveBehavior)
					&& (self.moveBehavior != null) && (self.moveBehavior.getRangedBehavior() == behavior))) {
				busy = true;
			}
		}
		if (busy) {
			// The order the unit was on ends with the behavior; the next queued order, if any, takes over.
			CBehavior next = pollNextOrderBehavior(self, game);
			for (final CBehavior behavior : released) {
				if ((behavior != null) && (behavior == next)) {
					next = self.orders.defaultBehavior;
				}
			}
			beginBehavior(self, game, next, true);
		}
	}
}
