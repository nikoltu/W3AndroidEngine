package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.archmage.CAbilityMassTeleport;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityReviveHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpell;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.warden.CAbilityBlink;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.blademaster.CAbilityMirrorImage;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.dreadlord.CAbilityCarrionSwarmDummy;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CGroupMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIdUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderExecutor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CWidgetAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.PointAbilityTargetCheckReceiver;
import com.etheller.interpreter.ast.function.JassNativeManager;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Unit, group and item use orders issued by scripts.
 */
final class OrderNatives extends CommonNatives {
	OrderNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		registerUnitOrders();
		registerItemOrders();
		registerGroupOrders();
	}

	private void registerUnitOrders() {
		final JassFunction issuePointOrderById = (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			final int orderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
			// Measured on 1.29: a structure that goes on a gold mine is refused as a point order too, on the mine.
			if ((whichUnit != null) && isBuiltOnMineOnly(orderId)) {
				return BooleanJassValue.FALSE;
			}
			final boolean issued = (whichUnit != null) && issuePointOrder(whichUnit.getPlayerIndex(), whichUnit,
					orderId, arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue(),
					arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue());
			// Measured on 1.29: a worker given a building's id as a point order starts to build it, but the native
			// reports false; IssueBuildOrderById reports true. The AI's move (851988) is carried out and reports
			// false too, and so is a smart order on a point.
			return BooleanJassValue.of(issued && !isUnitTypeId(orderId) && (orderId != OrderIds.AImove)
					&& (orderId != OrderIds.smart));
		};
		jassProgramVisitor.getJassNativeManager().createNative("IssuePointOrderById", issuePointOrderById);
		jassProgramVisitor.getJassNativeManager().createNative("IssuePointOrder",
				(arguments, globalScope, triggerScope) -> {
					final String orderString = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					return issuePointOrderById.call(Arrays.asList(arguments.get(0),
							IntegerJassValue.of(OrderIdUtils.getOrderId(orderString)), arguments.get(2),
							arguments.get(3)), globalScope, triggerScope);
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssuePointOrderLoc",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget whichLocation = arguments.get(2)
							.visit(ObjectJassValueVisitor.getInstance());
					if (whichLocation == null) {
						return BooleanJassValue.FALSE;
					}
					final String orderString = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					return issuePointOrderById.call(Arrays.asList(arguments.get(0),
							IntegerJassValue.of(OrderIdUtils.getOrderId(orderString)),
							new RealJassValue(whichLocation.x), new RealJassValue(whichLocation.y)), globalScope,
							triggerScope);
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssuePointOrderByIdLoc",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget whichLocation = arguments.get(2)
							.visit(ObjectJassValueVisitor.getInstance());
					if (whichLocation == null) {
						return BooleanJassValue.FALSE;
					}
					return issuePointOrderById.call(Arrays.asList(arguments.get(0), arguments.get(1),
							new RealJassValue(whichLocation.x), new RealJassValue(whichLocation.y)), globalScope,
							triggerScope);
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueBuildOrderById",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int unitId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichUnit == null) {
						return BooleanJassValue.FALSE;
					}
					// Measured on 1.29: an id that is not a unit type (such as an order id) gives true and no order.
					if (!isUnitTypeId(unitId)) {
						return BooleanJassValue.TRUE;
					}
					if (isBuiltOnMineOnly(unitId)) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(issuePointOrder(whichUnit.getPlayerIndex(), whichUnit, unitId,
							arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue(),
							arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueTargetOrder",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final String orderString = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					int orderId = OrderIdUtils.getOrderId(orderString);
					if ((orderId == 0) && (orderString != null) && !orderString.isEmpty()) {
						// Measured on 1.29: the name of a unit type gives its id as the order, so an Acolyte ordered
						// to "undeadgoldmine2" on a gold mine raises the Haunted Gold Mine.
						final CUnitType buildType = this.simulation.getUnitData().getUnitTypeByJassLegacyName(orderString);
						if (buildType != null) {
							orderId = buildType.getTypeId().getValue();
						}
					}
					return BooleanJassValue.of((whichUnit != null) && issueTargetOrder(whichUnit.getPlayerIndex(), whichUnit,
							orderId, nullable(arguments, 2, ObjectJassValueVisitor.getInstance())));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueTargetOrderById",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && issueTargetOrder(whichUnit.getPlayerIndex(), whichUnit,
							arguments.get(1).visit(IntegerJassValueVisitor.getInstance()),
							nullable(arguments, 2, ObjectJassValueVisitor.getInstance())));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueImmediateOrder",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final String orderString = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					int orderId = OrderIdUtils.getOrderId(orderString);
					if ((orderId == 0) && (orderString != null) && !orderString.isEmpty()) {
						// Measured on 1.29: the name of a unit type trains it, as its id does.
						final CUnitType trainType = this.simulation.getUnitData().getUnitTypeByJassLegacyName(orderString);
						if (trainType != null) {
							orderId = trainType.getTypeId().getValue();
						}
					}
					return BooleanJassValue.of((whichUnit != null)
							&& issueImmediateOrder(whichUnit.getPlayerIndex(), whichUnit, orderId));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueImmediateOrderById",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && issueImmediateOrder(whichUnit.getPlayerIndex(), whichUnit,
							arguments.get(1).visit(IntegerJassValueVisitor.getInstance())));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueBuildOrder",
				(arguments, globalScope, triggerScope) -> {
					final CUnitType buildType = this.simulation.getUnitData()
							.getUnitTypeByJassLegacyName(arguments.get(1).visit(StringJassValueVisitor.getInstance()));
					if ((buildType == null) || buildType.isCanBeBuiltOnThem()) {
						return BooleanJassValue.FALSE;
					}
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && issuePointOrder(whichUnit.getPlayerIndex(),
							whichUnit, buildType.getTypeId().getValue(),
							arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue(),
							arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueInstantPointOrder",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && issuePointOrder(whichUnit.getPlayerIndex(), whichUnit,
							OrderIdUtils.getOrderId(arguments.get(1).visit(StringJassValueVisitor.getInstance())),
							arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue(),
							arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueInstantPointOrderById",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && issuePointOrder(whichUnit.getPlayerIndex(), whichUnit,
							arguments.get(1).visit(IntegerJassValueVisitor.getInstance()),
							arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue(),
							arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueInstantTargetOrder",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && issueTargetOrder(whichUnit.getPlayerIndex(), whichUnit,
							OrderIdUtils.getOrderId(arguments.get(1).visit(StringJassValueVisitor.getInstance())),
							nullable(arguments, 2, ObjectJassValueVisitor.getInstance())));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueInstantTargetOrderById",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && issueTargetOrder(whichUnit.getPlayerIndex(), whichUnit,
							arguments.get(1).visit(IntegerJassValueVisitor.getInstance()),
							nullable(arguments, 2, ObjectJassValueVisitor.getInstance())));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueNeutralImmediateOrder",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer forWhichPlayer = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnit neutralStructure = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					if ((forWhichPlayer == null) || (neutralStructure == null)) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(issueImmediateOrder(forWhichPlayer.getId(), neutralStructure,
							OrderIdUtils.getOrderId(arguments.get(2).visit(StringJassValueVisitor.getInstance()))));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueNeutralPointOrder",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer forWhichPlayer = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnit neutralStructure = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					if ((forWhichPlayer == null) || (neutralStructure == null)) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(issuePointOrder(forWhichPlayer.getId(), neutralStructure,
							OrderIdUtils.getOrderId(arguments.get(2).visit(StringJassValueVisitor.getInstance())),
							arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue(),
							arguments.get(4).visit(RealJassValueVisitor.getInstance()).floatValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueNeutralTargetOrder",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer forWhichPlayer = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnit neutralStructure = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					if ((forWhichPlayer == null) || (neutralStructure == null)) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(issueTargetOrder(forWhichPlayer.getId(), neutralStructure,
							OrderIdUtils.getOrderId(arguments.get(2).visit(StringJassValueVisitor.getInstance())),
							nullable(arguments, 3, ObjectJassValueVisitor.getInstance())));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueNeutralImmediateOrderById",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer forWhichPlayer = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnit neutralStructure = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					if ((forWhichPlayer == null) || (neutralStructure == null)) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(issueImmediateOrder(forWhichPlayer.getId(), neutralStructure,
							arguments.get(2).visit(IntegerJassValueVisitor.getInstance())));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueNeutralPointOrderById",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer forWhichPlayer = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnit neutralStructure = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					if ((forWhichPlayer == null) || (neutralStructure == null)) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(issuePointOrder(forWhichPlayer.getId(), neutralStructure,
							arguments.get(2).visit(IntegerJassValueVisitor.getInstance()),
							arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue(),
							arguments.get(4).visit(RealJassValueVisitor.getInstance()).floatValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueNeutralTargetOrderById",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer forWhichPlayer = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnit neutralStructure = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					if ((forWhichPlayer == null) || (neutralStructure == null)) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(issueTargetOrder(forWhichPlayer.getId(), neutralStructure,
							arguments.get(2).visit(IntegerJassValueVisitor.getInstance()),
							nullable(arguments, 3, ObjectJassValueVisitor.getInstance())));
				});
		registerQueuedOrders();
		jassProgramVisitor.getJassNativeManager().createNative("DoStopOrder",
				(arguments, globalScope, triggerScope) -> {
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (targetUnit != null) {
						targetUnit.performDefaultBehavior(this.simulation);
					}
					return null;
				});
	}

	/**
	 * The Reforged orders that wait behind the current one, as a player shift-queues them: the same checks and
	 * carrying out as the other script orders, queued.
	 */
	private void registerQueuedOrders() {
		final JassNativeManager natives = jassProgramVisitor.getJassNativeManager();
		natives.createNative("BlzQueueImmediateOrderById", (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			return BooleanJassValue.of((whichUnit != null) && issueImmediateOrder(whichUnit.getPlayerIndex(),
					whichUnit, arguments.get(1).visit(IntegerJassValueVisitor.getInstance()), true));
		});
		final JassFunction queuePoint = (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			return BooleanJassValue.of((whichUnit != null) && issuePointOrder(whichUnit.getPlayerIndex(), whichUnit,
					arguments.get(1).visit(IntegerJassValueVisitor.getInstance()),
					arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue(),
					arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue(), true));
		};
		natives.createNative("BlzQueuePointOrderById", queuePoint);
		// The instant target only tells which unit a spell with a second target aims at; the order is the same.
		natives.createNative("BlzQueueInstantPointOrderById", queuePoint);
		natives.createNative("BlzQueueBuildOrderById", (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			final int unitId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
			if ((whichUnit == null) || !isUnitTypeId(unitId)) {
				return BooleanJassValue.FALSE;
			}
			return BooleanJassValue.of(issuePointOrder(whichUnit.getPlayerIndex(), whichUnit, unitId,
					arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue(),
					arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue(), true));
		});
		final JassFunction queueTarget = (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			return BooleanJassValue.of((whichUnit != null) && issueTargetOrder(whichUnit.getPlayerIndex(), whichUnit,
					arguments.get(1).visit(IntegerJassValueVisitor.getInstance()),
					nullable(arguments, 2, ObjectJassValueVisitor.getInstance()), true));
		};
		natives.createNative("BlzQueueTargetOrderById", queueTarget);
		natives.createNative("BlzQueueInstantTargetOrderById", queueTarget);
		natives.createNative("BlzQueueNeutralImmediateOrderById", (arguments, globalScope, triggerScope) -> {
			final CPlayer forWhichPlayer = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			final CUnit neutralStructure = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
			return BooleanJassValue.of((forWhichPlayer != null) && (neutralStructure != null)
					&& issueImmediateOrder(forWhichPlayer.getId(), neutralStructure,
							arguments.get(2).visit(IntegerJassValueVisitor.getInstance()), true));
		});
		natives.createNative("BlzQueueNeutralPointOrderById", (arguments, globalScope, triggerScope) -> {
			final CPlayer forWhichPlayer = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			final CUnit neutralStructure = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
			return BooleanJassValue.of((forWhichPlayer != null) && (neutralStructure != null)
					&& issuePointOrder(forWhichPlayer.getId(), neutralStructure,
							arguments.get(2).visit(IntegerJassValueVisitor.getInstance()),
							arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue(),
							arguments.get(4).visit(RealJassValueVisitor.getInstance()).floatValue(), true));
		});
		natives.createNative("BlzQueueNeutralTargetOrderById", (arguments, globalScope, triggerScope) -> {
			final CPlayer forWhichPlayer = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			final CUnit neutralStructure = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
			return BooleanJassValue.of((forWhichPlayer != null) && (neutralStructure != null)
					&& issueTargetOrder(forWhichPlayer.getId(), neutralStructure,
							arguments.get(2).visit(IntegerJassValueVisitor.getInstance()),
							nullable(arguments, 3, ObjectJassValueVisitor.getInstance()), true));
		});
		// The orders the unit has: the one it is on and the ones waiting behind it.
		natives.createNative("BlzGetUnitOrderCount", (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
			if (whichUnit == null) {
				return IntegerJassValue.ZERO;
			}
			final boolean busy = (whichUnit.getCurrentOrder() != null)
					&& (whichUnit.getCurrentOrder().getOrderId() != OrderIds.stop);
			return IntegerJassValue.of((busy ? 1 : 0) + whichUnit.getOrderQueue().size());
		});
		// With onlyQueued the order the unit is on goes on; without, the unit stops as well.
		natives.createNative("BlzUnitClearOrders", (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
			if (whichUnit != null) {
				whichUnit.clearQueuedOrders(this.simulation);
				if (!arguments.get(1).visit(BooleanJassValueVisitor.getInstance())) {
					whichUnit.performDefaultBehavior(this.simulation);
				}
			}
			return null;
		});
		// The unit stops at once whatever order it is on; the queue goes too on request.
		natives.createNative("BlzUnitForceStopOrder", (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
			if (whichUnit != null) {
				if (arguments.get(1).visit(BooleanJassValueVisitor.getInstance())) {
					whichUnit.clearQueuedOrders(this.simulation);
				}
				whichUnit.performDefaultBehavior(this.simulation);
			}
			return null;
		});
	}

	/** A worn item has no numbered slot, so it has no "itemuse" order to give. */
	private static boolean isCarried(final CItem item) {
		return (item != null) && (item.getContainedInventory() != null)
				&& (item.getContainedInventory().getSlot(item) >= 0);
	}

	private void registerItemOrders() {
		jassProgramVisitor.getJassNativeManager().createNative("UnitUseItem",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit == null) {
						return BooleanJassValue.FALSE;
					}
					final CItem item = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if (!isCarried(item)) {
						return BooleanJassValue.FALSE;
					}
					final CPlayerUnitOrderExecutor defaultPlayerUnitOrderExecutor = this.simulation
							.getDefaultPlayerUnitOrderExecutor(whichUnit.getPlayerIndex());
					final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
					final int orderId = OrderIdUtils.getOrderId(
							"itemuse" + String.format(Locale.ROOT, "%02d", item.getContainedInventory().getSlot(item)));
					int abilityHandleId = 0;
					boolean itemUsable = false;
					for (final CAbility ability : whichUnit.getAbilities()) {
						ability.checkCanUse(this.simulation, whichUnit, whichUnit.getPlayerIndex(), orderId, false,
								activationReceiver.reset());
						if (activationReceiver.isOk()) {
							// Measured on 1.29: an item whose ability needs a target is accepted (true) but nothing
							// is used or spent.
							itemUsable |= ability instanceof CAbilityInventory;
							final BooleanAbilityTargetCheckReceiver<Void> targetReceiver = BooleanAbilityTargetCheckReceiver
									.<Void>getInstance();
							ability.checkCanTargetNoTarget(this.simulation, whichUnit, whichUnit.getPlayerIndex(), orderId,
									false, targetReceiver.reset());
							if (targetReceiver.isTargetable()) {
								abilityHandleId = ability.getHandleId();
							}
						}
					}
					if (abilityHandleId != 0) {
						defaultPlayerUnitOrderExecutor.issueImmediateOrder(whichUnit.getHandleId(), abilityHandleId,
								orderId, false);
						// Measured on 1.29: the item is on cooldown the moment UnitUseItem accepts it, before the
						// cast point, so a second use or a hand-over right after finds it running.
						for (final CAbility itemAbility : whichUnit.getInventoryData().getItemAbilities(item)) {
							if (itemAbility instanceof AbilityBuilderAbility) {
								if (((AbilityBuilderAbility) itemAbility).getCooldown() > 0) {
									((AbilityBuilderAbility) itemAbility).startCooldown(this.simulation, whichUnit);
								}
							}
							else if ((itemAbility instanceof CAbilitySpellBase)
									&& (((CAbilitySpellBase) itemAbility).getCooldown() > 0)) {
								whichUnit.beginCooldown(this.simulation, ((CAbilitySpellBase) itemAbility).getCode(),
										((CAbilitySpellBase) itemAbility).getCooldown());
							}
						}
					}
					return BooleanJassValue.of(itemUsable);
				});
		final JassFunction unitUseItemTarget = (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
			if (whichUnit == null) {
				return BooleanJassValue.FALSE;
			}
			final CItem item = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
			if (!isCarried(item)) {
				return BooleanJassValue.FALSE;
			}
			CWidget whichTarget = arguments.get(2).visit(ObjectJassValueVisitor.getInstance());
			final CPlayerUnitOrderExecutor defaultPlayerUnitOrderExecutor = this.simulation
					.getDefaultPlayerUnitOrderExecutor(whichUnit.getPlayerIndex());
			final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
			final int orderId = OrderIdUtils
					.getOrderId("itemuse" + String.format(Locale.ROOT, "%02d", item.getContainedInventory().getSlot(item)));
			int abilityHandleId = 0;
			for (final CAbility ability : whichUnit.getAbilities()) {
				ability.checkCanUse(this.simulation, whichUnit, whichUnit.getPlayerIndex(), orderId, false,
						activationReceiver.reset());
				if (activationReceiver.isOk()) {
					final CWidgetAbilityTargetCheckReceiver targetReceiver = CWidgetAbilityTargetCheckReceiver.INSTANCE;
					ability.checkCanTarget(this.simulation, whichUnit, whichUnit.getPlayerIndex(), orderId, false,
							whichTarget, targetReceiver.reset());
					if (targetReceiver.getTarget() != null) {
						whichTarget = targetReceiver.getTarget();
						abilityHandleId = ability.getHandleId();
					}
				}
			}
			if (abilityHandleId != 0) {
				defaultPlayerUnitOrderExecutor.issueTargetOrder(whichUnit.getHandleId(), abilityHandleId, orderId,
						whichTarget.getHandleId(), false);
			}
			return BooleanJassValue.of(abilityHandleId != 0);
		};
		jassProgramVisitor.getJassNativeManager().createNative("UnitUseItemTarget", unitUseItemTarget);
		jassProgramVisitor.getJassNativeManager().createNative("UnitUseItemDestructable", unitUseItemTarget);
		jassProgramVisitor.getJassNativeManager().createNative("UnitUseItemPointLoc",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichUnit == null) {
						return BooleanJassValue.FALSE;
					}
					final CItem item = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if (!isCarried(item)) {
						return BooleanJassValue.FALSE;
					}
					final AbilityPointTarget whichLocation = arguments.get(2)
							.visit(ObjectJassValueVisitor.getInstance());
					final boolean maskedPoint = isMaskedForOwner(whichUnit, whichLocation.x, whichLocation.y);
					// Measured on 1.29: only a Blink item is refused at a point under the black mask of its owner; other
					// point items still walk to the point and are used there.
					boolean refusedAtMask = false;
					if (maskedPoint) {
						for (final CAbility itemAbility : item.getContainedInventory().getItemAbilities(item)) {
							refusedAtMask |= itemAbility instanceof CAbilityBlink;
						}
					}
					if (refusedAtMask) {
						return BooleanJassValue.FALSE;
					}
					final CPlayerUnitOrderExecutor defaultPlayerUnitOrderExecutor = this.simulation
							.getDefaultPlayerUnitOrderExecutor(whichUnit.getPlayerIndex());
					final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
					final int orderId = OrderIdUtils.getOrderId(
							"itemuse" + String.format(Locale.ROOT, "%02d", item.getContainedInventory().getSlot(item)));
					int abilityHandleId = 0;
					AbilityPointTarget targetAsPoint = new AbilityPointTarget(whichLocation.x, whichLocation.y);
					for (final CAbility ability : whichUnit.getAbilities()) {
						ability.checkCanUse(this.simulation, whichUnit,
								whichUnit.getPlayerIndex(), orderId, false, activationReceiver.reset());
						if (activationReceiver.isOk()) {
							final PointAbilityTargetCheckReceiver targetReceiver = PointAbilityTargetCheckReceiver.INSTANCE;
							ability.checkCanTarget(this.simulation, whichUnit,
									whichUnit.getPlayerIndex(), orderId, false, targetAsPoint,
									targetReceiver.reset());
							if (targetReceiver.getTarget() != null) {
								targetAsPoint = targetReceiver.getTarget();
								abilityHandleId = ability.getHandleId();
							}
						}
					}
					if (abilityHandleId != 0) {
						defaultPlayerUnitOrderExecutor.issuePointOrder(whichUnit.getHandleId(), abilityHandleId,
								orderId, targetAsPoint.x, targetAsPoint.y, false);
					}
					return BooleanJassValue.of(abilityHandleId != 0);
				});
	}

	private void registerGroupOrders() {
		jassProgramVisitor.getJassNativeManager().createNative("GroupImmediateOrder",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = nullable(arguments, 0,
							ObjectJassValueVisitor.<List<CUnit>>getInstance());
					if (group == null) {
						return BooleanJassValue.FALSE;
					}
					final String order = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final int orderId = OrderIdUtils.getOrderId(order);
					boolean success = true;
					for (final CUnit unit : group) {
						success &= unit.order(this.simulation, orderId, null);
					}
					return BooleanJassValue.of(success);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupImmediateOrderById",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = nullable(arguments, 0,
							ObjectJassValueVisitor.<List<CUnit>>getInstance());
					if (group == null) {
						return BooleanJassValue.FALSE;
					}
					final int order = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					boolean success = true;
					for (final CUnit unit : group) {
						success &= unit.order(this.simulation, order, null);
					}
					return BooleanJassValue.of(success);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupPointOrder",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = nullable(arguments, 0,
							ObjectJassValueVisitor.<List<CUnit>>getInstance());
					if (group == null) {
						return BooleanJassValue.FALSE;
					}
					final String order = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final Double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final Double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final int orderId = OrderIdUtils.getOrderId(order);
					return BooleanJassValue.of(groupPointOrder(group, orderId, x.floatValue(), y.floatValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupPointOrderLoc",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = nullable(arguments, 0,
							ObjectJassValueVisitor.<List<CUnit>>getInstance());
					if (group == null) {
						return BooleanJassValue.FALSE;
					}
					final String order = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final AbilityPointTarget whichLocation = arguments.get(2)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					final int orderId = OrderIdUtils.getOrderId(order);
					return BooleanJassValue.of(groupPointOrder(group, orderId, whichLocation.x, whichLocation.y));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupPointOrderById",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = nullable(arguments, 0,
							ObjectJassValueVisitor.<List<CUnit>>getInstance());
					if (group == null) {
						return BooleanJassValue.FALSE;
					}
					final int orderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final Double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final Double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					return BooleanJassValue.of(groupPointOrder(group, orderId, x.floatValue(), y.floatValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupPointOrderByIdLoc",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = nullable(arguments, 0,
							ObjectJassValueVisitor.<List<CUnit>>getInstance());
					if (group == null) {
						return BooleanJassValue.FALSE;
					}
					final int orderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final AbilityPointTarget whichLocation = arguments.get(2)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					return BooleanJassValue.of(groupPointOrder(group, orderId, whichLocation.x, whichLocation.y));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupTargetOrder",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = nullable(arguments, 0,
							ObjectJassValueVisitor.<List<CUnit>>getInstance());
					if (group == null) {
						return BooleanJassValue.FALSE;
					}
					final String order = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final CWidget target = arguments.get(2).visit(ObjectJassValueVisitor.<CWidget>getInstance());
					return BooleanJassValue.of(groupTargetOrder(group, OrderIdUtils.getOrderId(order), target));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupTargetOrderById",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = nullable(arguments, 0,
							ObjectJassValueVisitor.<List<CUnit>>getInstance());
					if (group == null) {
						return BooleanJassValue.FALSE;
					}
					final int orderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final CWidget target = arguments.get(2).visit(ObjectJassValueVisitor.<CWidget>getInstance());
					return BooleanJassValue.of(groupTargetOrder(group, orderId, target));
				});
	}

	private boolean groupPointOrder(final List<CUnit> group, final int orderId, final float x, final float y) {
		final List<CUnit> units = new ArrayList<>(group.subList(0,
				Math.min(group.size(), CGroupMove.MAX_SCRIPT_GROUP_ORDER_UNITS)));
		return CGroupMove.order(this.simulation, units, orderId, x, y,
				(unit, slotX, slotY) -> unit.order(this.simulation, orderId, new AbilityPointTarget(slotX, slotY)));
	}

	// Measured on 1.29: a point build order for a structure that goes on a gold mine (Haunted Gold Mine) is refused
	// and gives no order; it takes the target order on the mine instead.
	private boolean isBuiltOnMineOnly(final int id) {
		final CUnitType type = this.simulation.getUnitData().getUnitType(new War3ID(id));
		return (type != null) && type.isCanBeBuiltOnThem();
	}

	private boolean isUnitTypeId(final int id) {
		return this.simulation.getUnitData().getUnitType(new War3ID(id)) != null;
	}

	// Measured (1.29): a held item's ability (Dagger of Escape) does not take a script order by its own order
	// string; the item is used through the item-use orders.
	private static boolean isHeldItemAbility(final CUnit unit, final CAbility ability) {
		final CAbilityInventory inventory = unit.getFirstAbilityOfType(CAbilityInventory.class);
		return (inventory != null) && (ability != inventory) && inventory.isHeldItemAbility(ability);
	}

	// Measured (1.29): the spells inside a spell book take no script order; only the book's menu gives them.
	private static boolean isMenuSpell(final CAbility ability) {
		return ability.getContainerMenuOrderId() != 0;
	}

	private boolean isMaskedForOwner(final CUnit unit, final float x, final float y) {
		final CPlayer owner = this.simulation.getPlayer(unit.getPlayerIndex());
		return (owner != null) && !this.simulation.isNeutralPlayer(owner.getId())
				&& (fogState(owner, x, y) == CFogState.MASKED);
	}

	private boolean issuePointOrder(final int orderingPlayerIndex, final CUnit whichUnit, final int orderId,
			final float x, final float y) {
		return issuePointOrder(orderingPlayerIndex, whichUnit, orderId, x, y, false);
	}

	private boolean issuePointOrder(final int orderingPlayerIndex, final CUnit whichUnit, final int orderId,
			final float x, final float y, final boolean queue) {
		this.simulation.setScriptPointOrder(true);
		try {
			return issuePointOrderChecked(orderingPlayerIndex, whichUnit, orderId, x, y, queue);
		}
		finally {
			this.simulation.setScriptPointOrder(false);
		}
	}

	private boolean issuePointOrderChecked(final int orderingPlayerIndex, final CUnit whichUnit, final int orderId,
			final float x, final float y, final boolean queue) {
		// Measured on 1.29: a drop order cannot be given as a point order, it needs the item (UnitDropItemPoint).
		if (whichUnit.isLoaded() || CAbilityMirrorImage.isHidingCaster(whichUnit) || (orderId == OrderIds.dropitem)) {
			return false;
		}
		final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
		int abilityHandleId = 0;
		AbilityPointTarget targetAsPoint = new AbilityPointTarget(x, y);
		final boolean maskedPoint = isMaskedForOwner(whichUnit, x, y);
		for (final CAbility ability : whichUnit.getAbilities()) {
			if (isHeldItemAbility(whichUnit, ability) || isMenuSpell(ability)) {
				continue;
			}
			// Measured on 1.29: Blink is refused at a point under the black mask of its owner; other point
			// orders (move, attack, patrol, Flame Strike, Blizzard) are not.
			if (maskedPoint && (ability instanceof CAbilityBlink)) {
				continue;
			}
			ability.checkCanUse(this.simulation, whichUnit, orderingPlayerIndex, orderId, false, 
					activationReceiver.reset());
			if (activationReceiver.isOk()) {
				final PointAbilityTargetCheckReceiver targetReceiver = PointAbilityTargetCheckReceiver.INSTANCE;
				ability.checkCanTarget(this.simulation, whichUnit, orderingPlayerIndex, orderId, false, targetAsPoint,
					targetReceiver.reset());
				if (targetReceiver.getTarget() != null) {
					targetAsPoint = targetReceiver.getTarget();
					abilityHandleId = ability.getHandleId();
				}
			}
		}
		if (abilityHandleId != 0) {
			this.simulation.getDefaultPlayerUnitOrderExecutor(orderingPlayerIndex).issuePointOrder(
					whichUnit.getHandleId(), abilityHandleId, orderId, targetAsPoint.x, targetAsPoint.y, queue);
		}
		return abilityHandleId != 0;
	}

	private boolean issueTargetOrder(final int orderingPlayerIndex, final CUnit whichUnit, final int orderId,
			final CWidget whichTarget) {
		return issueTargetOrder(orderingPlayerIndex, whichUnit, orderId, whichTarget, false);
	}

	private boolean issueTargetOrder(final int orderingPlayerIndex, final CUnit whichUnit, final int orderId,
			CWidget whichTarget, final boolean queue) {
		if ((whichTarget == null) || (this.simulation.getWidget(whichTarget.getHandleId()) == null)
				|| whichUnit.isLoaded() || CAbilityMirrorImage.isHidingCaster(whichUnit)) {
			return false;
		}
		int abilityHandleId = 0;
		// Measured: script attack orders may target a unit hidden by fog; spell orders (Hex) may not.
		this.simulation.setScriptOrder(orderId == OrderIds.attack);
		this.simulation.setScriptTargetOrder(true);
		try {
			final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
			for (final CAbility ability : whichUnit.getAbilities()) {
				if (isMenuSpell(ability)) {
					continue;
				}
				ability.checkCanUse(this.simulation, whichUnit, orderingPlayerIndex, orderId, false, 
						activationReceiver.reset());
				if (activationReceiver.isOk()) {
					final CWidgetAbilityTargetCheckReceiver targetReceiver = CWidgetAbilityTargetCheckReceiver.INSTANCE;
					ability.checkCanTarget(this.simulation, whichUnit, orderingPlayerIndex, orderId, false, whichTarget,
						targetReceiver.reset());
					if (targetReceiver.getTarget() != null) {
						whichTarget = targetReceiver.getTarget();
						abilityHandleId = ability.getHandleId();
					}
				}
			}
			if (abilityHandleId != 0) {
				this.simulation.getDefaultPlayerUnitOrderExecutor(orderingPlayerIndex).issueTargetOrder(
						whichUnit.getHandleId(), abilityHandleId, orderId, whichTarget.getHandleId(), queue);
			}
			else {
				return issueSpellPointOrderAtUnit(orderingPlayerIndex, whichUnit, orderId, whichTarget, queue);
			}
		}
		finally {
			this.simulation.setScriptOrder(false);
			this.simulation.setScriptTargetOrder(false);
		}
		return abilityHandleId != 0;
	}

	// Measured (1.29): a target script order of a point-target spell (Dispel Magic,
	// Disenchant, Flame Strike, Blizzard, Shockwave, Carrion Swarm) casts it where the widget stands: a unit, a tree,
	// an item or another destructable.
	private boolean issueSpellPointOrderAtUnit(final int orderingPlayerIndex, final CUnit whichUnit, final int orderId,
			final CWidget whichTarget, final boolean queue) {
		final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
		int abilityHandleId = 0;
		AbilityPointTarget targetAsPoint = new AbilityPointTarget(whichTarget.getX(), whichTarget.getY());
		for (final CAbility ability : whichUnit.getAbilities()) {
			if (isMenuSpell(ability) || (!(ability instanceof CAbilitySpell)
					&& !(ability instanceof AbilityBuilderActiveAbility)
					&& !(ability instanceof CAbilityCarrionSwarmDummy))
					// A living unit that Mass Teleport refuses is not a point to cast it at; trees, items and corpses are.
					|| ((ability instanceof CAbilityMassTeleport) && (whichTarget instanceof CUnit)
							&& !((CUnit) whichTarget).isDead())) {
				continue;
			}
			ability.checkCanUse(this.simulation, whichUnit, orderingPlayerIndex, orderId, false, 
					activationReceiver.reset());
			if (activationReceiver.isOk()) {
				final PointAbilityTargetCheckReceiver targetReceiver = PointAbilityTargetCheckReceiver.INSTANCE;
				if ((ability instanceof CAbilityPointTargetSpellBase)
						&& (orderId == ((CAbilityPointTargetSpellBase) ability).getBaseOrderId())) {
					((CAbilityPointTargetSpellBase) ability).checkCanTargetWidgetPosition(this.simulation, whichUnit,
							orderingPlayerIndex, orderId, whichTarget, targetAsPoint, targetReceiver.reset());
				}
				else {
					ability.checkCanTarget(this.simulation, whichUnit, orderingPlayerIndex, orderId, false,
							targetAsPoint, targetReceiver.reset());
				}
				if (targetReceiver.getTarget() != null) {
					targetAsPoint = targetReceiver.getTarget();
					abilityHandleId = ability.getHandleId();
				}
			}
		}
		if (abilityHandleId != 0) {
			this.simulation.getDefaultPlayerUnitOrderExecutor(orderingPlayerIndex).issuePointOrder(
					whichUnit.getHandleId(), abilityHandleId, orderId, targetAsPoint.x, targetAsPoint.y, queue);
		}
		return abilityHandleId != 0;
	}

	// Measured (1.29): true when any unit of the group took the order.
	private boolean groupTargetOrder(final List<CUnit> group, final int orderId, final CWidget target) {
		boolean success = false;
		for (final CUnit unit : new ArrayList<>(group.subList(0,
				Math.min(group.size(), CGroupMove.MAX_SCRIPT_GROUP_ORDER_UNITS)))) {
			success |= issueTargetOrder(unit.getPlayerIndex(), unit, orderId, target);
		}
		return success;
	}

	private boolean issueImmediateOrder(final int orderingPlayerIndex, final CUnit whichUnit, final int orderId) {
		return issueImmediateOrder(orderingPlayerIndex, whichUnit, orderId, false);
	}

	private boolean issueImmediateOrder(final int orderingPlayerIndex, final CUnit whichUnit, final int orderId,
			final boolean queue) {
		if (whichUnit.isLoaded() || CAbilityMirrorImage.isHidingCaster(whichUnit)) {
			return orderId == OrderIds.stop;
		}
		final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
		int abilityHandleId = 0;
		boolean targetOrderAccepted = false;
		for (final CAbility ability : whichUnit.getAbilities()) {
			if (isMenuSpell(ability)) {
				continue;
			}
			if (ability instanceof CAbilityReviveHero) {
				// Measured on 1.29: a script order never revives a dead hero at an altar, neither by the hero's
				// type nor by its handle; players and the AI still can.
				continue;
			}
			ability.checkCanUse(this.simulation, whichUnit, orderingPlayerIndex, orderId, false, 
					activationReceiver.reset());
			if (activationReceiver.isOk()) {
				final BooleanAbilityTargetCheckReceiver<Void> targetReceiver = BooleanAbilityTargetCheckReceiver
						.<Void>getInstance();
				ability.checkCanTargetNoTarget(this.simulation, whichUnit, orderingPlayerIndex, orderId, false,
					targetReceiver.reset());
				if (targetReceiver.isTargetable()) {
					abilityHandleId = ability.getHandleId();
				}
				else if (ability.isTargetOrder(orderId)) {
					targetOrderAccepted = true;
				}
			}
		}
		// Orders without an ability, such as stop, are still issued.
		this.simulation.getDefaultPlayerUnitOrderExecutor(orderingPlayerIndex).issueImmediateOrder(
				whichUnit.getHandleId(), abilityHandleId, orderId, queue);
		// Measured on 1.29: stop is accepted (true) although no ability carries it.
		// Measured on 1.29: the order of a spell that needs a target is accepted without one and does nothing.
		return (abilityHandleId != 0) || targetOrderAccepted || ((orderId == OrderIds.stop) && !whichUnit.isDead());
	}
}
