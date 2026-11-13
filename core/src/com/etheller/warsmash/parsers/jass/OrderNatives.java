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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityReviveHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpell;
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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CWidgetAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.PointAbilityTargetCheckReceiver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
					return BooleanJassValue.of(issuePointOrder(whichUnit.getPlayerIndex(), whichUnit, unitId,
							arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue(),
							arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IssueTargetOrder",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && issueTargetOrder(whichUnit.getPlayerIndex(), whichUnit,
							OrderIdUtils.getOrderId(arguments.get(1).visit(StringJassValueVisitor.getInstance())),
							nullable(arguments, 2, ObjectJassValueVisitor.getInstance())));
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
					if (buildType == null) {
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
		jassProgramVisitor.getJassNativeManager().createNative("DoStopOrder",
				(arguments, globalScope, triggerScope) -> {
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (targetUnit != null) {
						targetUnit.performDefaultBehavior(this.simulation);
					}
					return null;
				});
	}

	private void registerItemOrders() {
		jassProgramVisitor.getJassNativeManager().createNative("UnitUseItem",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit == null) {
						return BooleanJassValue.FALSE;
					}
					final CItem item = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final CPlayerUnitOrderExecutor defaultPlayerUnitOrderExecutor = this.simulation
							.getDefaultPlayerUnitOrderExecutor(whichUnit.getPlayerIndex());
					final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
					final int orderId = OrderIdUtils.getOrderId(
							"itemuse" + String.format("%02d", item.getContainedInventory().getSlot(item)));
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
					}
					return BooleanJassValue.of(itemUsable);
				});
		final JassFunction unitUseItemTarget = (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
			if (whichUnit == null) {
				return BooleanJassValue.FALSE;
			}
			final CItem item = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
			CWidget whichTarget = arguments.get(2).visit(ObjectJassValueVisitor.getInstance());
			final CPlayerUnitOrderExecutor defaultPlayerUnitOrderExecutor = this.simulation
					.getDefaultPlayerUnitOrderExecutor(whichUnit.getPlayerIndex());
			final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
			final int orderId = OrderIdUtils
					.getOrderId("itemuse" + String.format("%02d", item.getContainedInventory().getSlot(item)));
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
					final AbilityPointTarget whichLocation = arguments.get(2)
							.visit(ObjectJassValueVisitor.getInstance());
					final CPlayerUnitOrderExecutor defaultPlayerUnitOrderExecutor = this.simulation
							.getDefaultPlayerUnitOrderExecutor(whichUnit.getPlayerIndex());
					final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
					final int orderId = OrderIdUtils.getOrderId(
							"itemuse" + String.format("%02d", item.getContainedInventory().getSlot(item)));
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
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
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
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					final int order = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					boolean success = true;
					for (final CUnit unit : group) {
						success &= unit.order(this.simulation, order, null);
					}
					return BooleanJassValue.of(success);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupPointOrder",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					final String order = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final Double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final Double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final int orderId = OrderIdUtils.getOrderId(order);
					return BooleanJassValue.of(groupPointOrder(group, orderId, x.floatValue(), y.floatValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupPointOrderLoc",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					final String order = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final AbilityPointTarget whichLocation = arguments.get(2)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					final int orderId = OrderIdUtils.getOrderId(order);
					return BooleanJassValue.of(groupPointOrder(group, orderId, whichLocation.x, whichLocation.y));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupPointOrderById",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					final int orderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final Double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final Double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					return BooleanJassValue.of(groupPointOrder(group, orderId, x.floatValue(), y.floatValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupPointOrderByIdLoc",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					final int orderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final AbilityPointTarget whichLocation = arguments.get(2)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					return BooleanJassValue.of(groupPointOrder(group, orderId, whichLocation.x, whichLocation.y));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupTargetOrder",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					final String order = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final CWidget target = arguments.get(2).visit(ObjectJassValueVisitor.<CWidget>getInstance());
					return BooleanJassValue.of(groupTargetOrder(group, OrderIdUtils.getOrderId(order), target));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupTargetOrderById",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
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

	private boolean isUnitTypeId(final int id) {
		return this.simulation.getUnitData().getUnitType(new War3ID(id)) != null;
	}

	// Measured (1.29): a held item's ability (Dagger of Escape) does not take a script order by its own order
	// string; the item is used through the item-use orders.
	private static boolean isHeldItemAbility(final CUnit unit, final CAbility ability) {
		final CAbilityInventory inventory = unit.getFirstAbilityOfType(CAbilityInventory.class);
		return (inventory != null) && (ability != inventory) && inventory.isHeldItemAbility(ability);
	}

	private boolean issuePointOrder(final int orderingPlayerIndex, final CUnit whichUnit, final int orderId,
			final float x, final float y) {
		if (CAbilityMirrorImage.isHidingCaster(whichUnit)) {
			return false;
		}
		final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
		int abilityHandleId = 0;
		AbilityPointTarget targetAsPoint = new AbilityPointTarget(x, y);
		for (final CAbility ability : whichUnit.getAbilities()) {
			if (isHeldItemAbility(whichUnit, ability)) {
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
					whichUnit.getHandleId(), abilityHandleId, orderId, targetAsPoint.x, targetAsPoint.y, false);
		}
		return abilityHandleId != 0;
	}

	private boolean issueTargetOrder(final int orderingPlayerIndex, final CUnit whichUnit, final int orderId,
			CWidget whichTarget) {
		if ((whichTarget == null) || (this.simulation.getWidget(whichTarget.getHandleId()) == null)
				|| CAbilityMirrorImage.isHidingCaster(whichUnit)) {
			return false;
		}
		int abilityHandleId = 0;
		// Measured: script attack orders may target a unit hidden by fog; spell orders (Hex) may not.
		this.simulation.setScriptOrder(orderId == OrderIds.attack);
		this.simulation.setScriptTargetOrder(true);
		try {
			final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
			for (final CAbility ability : whichUnit.getAbilities()) {
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
						whichUnit.getHandleId(), abilityHandleId, orderId, whichTarget.getHandleId(), false);
			}
			else if (whichTarget.visit(AbilityTargetVisitor.UNIT) != null) {
				return issueSpellPointOrderAtUnit(orderingPlayerIndex, whichUnit, orderId, whichTarget);
			}
		}
		finally {
			this.simulation.setScriptOrder(false);
			this.simulation.setScriptTargetOrder(false);
		}
		return abilityHandleId != 0;
	}

	// Measured (1.29): a unit-target script order of a point-target spell (Dispel Magic,
	// Disenchant, Flame Strike, Blizzard, Shockwave, Carrion Swarm) casts it at the unit.
	private boolean issueSpellPointOrderAtUnit(final int orderingPlayerIndex, final CUnit whichUnit, final int orderId,
			final CWidget whichTarget) {
		final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
		int abilityHandleId = 0;
		AbilityPointTarget targetAsPoint = new AbilityPointTarget(whichTarget.getX(), whichTarget.getY());
		for (final CAbility ability : whichUnit.getAbilities()) {
			if (!(ability instanceof CAbilitySpell) && !(ability instanceof AbilityBuilderActiveAbility)
					&& !(ability instanceof CAbilityCarrionSwarmDummy)) {
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
					whichUnit.getHandleId(), abilityHandleId, orderId, targetAsPoint.x, targetAsPoint.y, false);
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
		if (CAbilityMirrorImage.isHidingCaster(whichUnit)) {
			return orderId == OrderIds.stop;
		}
		final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
		int abilityHandleId = 0;
		for (final CAbility ability : whichUnit.getAbilities()) {
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
			}
		}
		// Orders without an ability, such as stop, are still issued.
		this.simulation.getDefaultPlayerUnitOrderExecutor(orderingPlayerIndex).issueImmediateOrder(
				whichUnit.getHandleId(), abilityHandleId, orderId, false);
		// Measured on 1.29: stop is accepted (true) although no ability carries it.
		return (abilityHandleId != 0) || ((orderId == OrderIds.stop) && !whichUnit.isDead());
	}
}
