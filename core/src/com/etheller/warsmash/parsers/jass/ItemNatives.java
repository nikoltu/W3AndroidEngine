package com.etheller.warsmash.parsers.jass;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.interpreter.ast.scope.trigger.TriggerBooleanExpression;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassType;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.CodeJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.parsers.jass.triggers.CItemPool;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.ItemUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItemType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilitySellItems;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item.shop.CAbilitySellUnits;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.item.CItemTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

/**
 * Item natives: items, inventories, shop stock and random item and creep choices.
 */
final class ItemNatives extends CommonNatives {
	private static final War3ID SELL_ITEMS_ABILITY_ID = War3ID.fromString("Asid");
	private static final War3ID SELL_UNITS_ABILITY_ID = War3ID.fromString("Asud");

	ItemNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		registerItems();
		registerInventory();
		registerStock();
	}

	private void registerItems() {
		final HandleJassType playerType = type("player");
		final HandleJassType itemType = type("item");
		final HandleJassType itemtypeType = type("itemtype");

		jassProgramVisitor.getJassNativeManager().createNative("CreateItem",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final double x = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					final double y = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					return new HandleJassValue(itemType, this.simulation
							.createItem(new War3ID(rawcode), (float) x, (float) y));
				});
		final HandleJassType itempoolType = type("itempool");
		jassProgramVisitor.getJassNativeManager().createNative("CreateItemPool",
				(arguments, globalScope, triggerScope) -> newHandle(itempoolType, new CItemPool()));
		jassProgramVisitor.getJassNativeManager().createNative("DestroyItemPool",
				(arguments, globalScope, triggerScope) -> {
					release(nullable(arguments, 0, ObjectJassValueVisitor.getInstance()));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ItemPoolAddItemType",
				(arguments, globalScope, triggerScope) -> {
					final CItemPool pool = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (pool != null) {
						pool.add(new War3ID(intArg(arguments, 1)), realArg(arguments, 2));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ItemPoolRemoveItemType",
				(arguments, globalScope, triggerScope) -> {
					final CItemPool pool = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (pool != null) {
						pool.remove(new War3ID(intArg(arguments, 1)));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PlaceRandomItem",
				(arguments, globalScope, triggerScope) -> {
					final CItemPool pool = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (pool == null) {
						return itemType.getNullValue();
					}
					final War3ID picked = pool.pick(this.simulation.getSeededRandom());
					if (picked == null) {
						return itemType.getNullValue();
					}
					return new HandleJassValue(itemType,
							this.simulation.createItem(picked, realArg(arguments, 1), realArg(arguments, 2)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzCreateItemWithSkin",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final double x = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					final double y = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					return new HandleJassValue(itemType, this.simulation
							.createItem(new War3ID(rawcode), (float) x, (float) y));
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveItem",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichItem != null) {
						this.simulation.removeItem(whichItem);
						meleeUI.removedItem(whichItem);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetItemPlayer",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichItem == null) {
						return IntegerJassValue.of(0);
					}
					final CUnit containedUnit = whichItem.getContainedUnit();
					if (containedUnit != null) {
						return new HandleJassValue(playerType,
								this.simulation.getPlayer(containedUnit.getPlayerIndex()));
					}
					final int itemPlayerIndex = whichItem.getPlayerIndex();
					return new HandleJassValue(playerType, this.simulation.getPlayer(
							itemPlayerIndex < 0 ? this.simulation.getGameData().getMaxPlayers() - 1 : itemPlayerIndex));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetItemTypeId",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if ((whichItem == null) || (whichItem.isDead() && whichItem.isHidden())) {
						return IntegerJassValue.of(0);
					}
					return IntegerJassValue.of(whichItem.getTypeId().getValue());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetItemX",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichWidget = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return RealJassValue.of(whichWidget == null ? 0 : whichWidget.getX());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetItemY",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichWidget = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return RealJassValue.of(whichWidget == null ? 0 : whichWidget.getY());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetItemPosition",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichWidget = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (whichWidget != null) {
						whichWidget.forceDropIfHeld(this.simulation);
						whichWidget.setPointAndCheckUnstuck(x, y, this.simulation);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetItemDropOnDeath",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichWidget = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (whichWidget != null) {
						whichWidget.setDropOnDeath(flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetItemDroppable",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichWidget = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (whichWidget != null) {
						whichWidget.setDroppable(flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetItemPawnable",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichWidget = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (whichWidget != null) {
						whichWidget.setPawnable(flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetItemInvulnerable",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichWidget = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (whichWidget != null) {
						whichWidget.setInvulernable(flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsItemInvulnerable",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichItem == null) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(whichItem.isInvulnerable());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetItemVisible",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichWidget = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (whichWidget != null) {
						whichWidget.setHidden(!flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsItemVisible",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichItem == null) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(!whichItem.isHidden());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsItemPowerup",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichItem == null) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(whichItem.getItemType().isUseAutomaticallyWhenAcquired());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsItemSellable",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichItem == null) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(whichItem.getItemType().isSellable());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsItemPawnable",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichItem == null) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(whichItem.getItemType().isPawnable());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsItemIdPowerup",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CItemType itemClass = this.simulation.getItemData().getItemType(new War3ID(rawcode));
					if (itemClass == null) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(itemClass.isUseAutomaticallyWhenAcquired());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsItemIdSellable",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CItemType itemClass = this.simulation.getItemData().getItemType(new War3ID(rawcode));
					if (itemClass == null) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(itemClass.isSellable());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsItemIdPawnable",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CItemType itemClass = this.simulation.getItemData().getItemType(new War3ID(rawcode));
					if (itemClass == null) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(itemClass.isPawnable());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsItemIdPerishable",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CItemType itemClass = this.simulation.getItemData().getItemType(new War3ID(rawcode));
					if (itemClass == null) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(itemClass.isPerishable());
				});
		jassProgramVisitor.getJassNativeManager().createNative("EnumItemsInRect",
				(arguments, globalScope, triggerScope) -> {
					final Rectangle rect = arguments.get(0).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					final TriggerBooleanExpression filter = nullable(arguments, 1,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					final CodeJassValue actionFunc = nullable(arguments, 2, CodeJassValueVisitor.getInstance());
					this.simulation.getWorldCollision().enumItemsInRect(rect, (unit) -> {
						if (rectCellsContain(rect, unit.getX(), unit.getY()) && ((filter == null)
								|| filter.evaluate(globalScope,
										CommonTriggerExecutionScope.filterScope(triggerScope, unit)))) {
							globalScope.runThreadUntilCompletion(globalScope.createThread(actionFunc,
									CommonTriggerExecutionScope.enumScope(triggerScope, unit)));
						}
						return false;
					});
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetItemLevel",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichItem == null) {
						return IntegerJassValue.of(0);
					}
					return IntegerJassValue.of(whichItem.getItemType().getLevel());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetItemType",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichItem == null) {
						return itemtypeType.getNullValue();
					}
					return new HandleJassValue(itemtypeType, whichItem.getItemType().getItemClass());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetItemDropID",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int unitId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichItem != null) {
						whichItem.setDropId(new War3ID(unitId));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetItemDropID",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichItem == null) {
						return IntegerJassValue.of(0);
					}
					return IntegerJassValue.of(whichItem.getDropId().getValue());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetItemName",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichItem == null) {
						return JassType.STRING.getNullValue();
					}
					final ItemUI itemUI = war3MapViewer.getAbilityDataUI().getItemUI(whichItem.getTypeId());
					return new StringJassValue(itemUI.getName());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetItemCharges",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichItem == null) {
						return IntegerJassValue.of(0);
					}
					return IntegerJassValue.of(whichItem.getCharges());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetItemCharges",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int charges = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichItem != null) {
						whichItem.setCharges(charges);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetItemUserData",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichItem == null) {
						return IntegerJassValue.of(0);
					}
					return IntegerJassValue.of(whichItem.getUserData());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetItemUserData",
				(arguments, globalScope, triggerScope) -> {
					final CItem whichItem = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int value = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichItem != null) {
						whichItem.setUserData(value);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ChooseRandomCreep",
				(arguments, globalScope, triggerScope) -> {
					final int level = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final War3ID creepId = this.simulation.getUnitData().chooseRandomCreep(level,
							this.simulation.getSeededRandom());
					return IntegerJassValue.of(creepId == null ? 0 : creepId.getValue());
				});
		jassProgramVisitor.getJassNativeManager().createNative("ChooseRandomNPBuilding",
				(arguments, globalScope, triggerScope) -> {
					final War3ID buildingId = this.simulation.getUnitData()
							.chooseRandomNPBuilding(this.simulation.getSeededRandom());
					return IntegerJassValue.of(buildingId == null ? 0 : buildingId.getValue());
				});
		jassProgramVisitor.getJassNativeManager().createNative("ChooseRandomItem",
				(arguments, globalScope, triggerScope) -> {
					final int level = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final War3ID randomItemId = this.simulation.getItemData()
							.chooseRandomItem(level, this.simulation.getSeededRandom());
					return IntegerJassValue.of(randomItemId == null ? 0 : randomItemId.getValue());
				});
		jassProgramVisitor.getJassNativeManager().createNative("ChooseRandomItemEx",
				(arguments, globalScope, triggerScope) -> {
					final CItemTypeJass whichType = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int level = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final War3ID randomItemId = this.simulation.getItemData().chooseRandomItem(
							whichType, level, this.simulation.getSeededRandom());
					return IntegerJassValue.of(randomItemId == null ? 0 : randomItemId.getValue());
				});
	}

	// A unit inventory gated by a research (the 2-slot unit one needs Backpack) is there but does not work until then.
	private CAbilityInventory usableInventory(final CUnit unit) {
		final CAbilityInventory inventory = (unit == null) ? null : unit.getInventoryData();
		if ((inventory != null) && !inventory.isRequirementsMet(this.simulation, unit)) {
			return null;
		}
		return inventory;
	}

	private void registerInventory() {
		final HandleJassType itemType = type("item");

		jassProgramVisitor.getJassNativeManager().createNative("UnitAddItemById",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int rawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());

					final CItem newItem = this.simulation.createItem(new War3ID(rawcode),
							unit.getX(), unit.getY());
					final CAbilityInventory inventoryData = usableInventory(unit);
					if ((inventoryData != null) && (newItem != null)) {
						inventoryData.giveItem(this.simulation, unit, newItem, false);
					}
					return new HandleJassValue(itemType, newItem);
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitAddItemToSlotById",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int rawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int slot = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());

					final CItem newItem = this.simulation.createItem(new War3ID(rawcode),
							unit.getX(), unit.getY());
					final CAbilityInventory inventoryData = usableInventory(unit);
					if ((inventoryData != null) && (newItem != null)) {
						inventoryData.giveItem(this.simulation, unit, newItem, slot, false);
					}

					return new HandleJassValue(itemType, newItem);
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitDropItemSlot",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CItem whichItem = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final int slot = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					return BooleanJassValue.of((usableInventory(whichUnit) != null)
							&& usableInventory(whichUnit).moveItemToSlot(whichItem, slot));
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitRemoveItemFromSlot",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int slot = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final CAbilityInventory inventoryData = usableInventory(whichUnit);
					if ((inventoryData == null) || (slot < 0) || (slot >= inventoryData.getItemCapacity())
							|| (inventoryData.getItemInSlot(slot) == null)) {
						return new HandleJassValue(itemType, null);
					}
					final CItem item = inventoryData.getItemInSlot(slot);
					inventoryData.dropItem(this.simulation, whichUnit, slot, whichUnit.getX(), whichUnit.getY(),
							false);
					return new HandleJassValue(itemType, item);
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitInventorySize",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CAbilityInventory inventoryData = usableInventory(whichUnit);
					return IntegerJassValue.of(inventoryData == null ? 0 : inventoryData.getItemCapacity());
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitAddItem",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CItem whichItem = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final CAbilityInventory inventoryData = usableInventory(whichUnit);
					if ((inventoryData == null) || (whichItem == null)) {
						return BooleanJassValue.FALSE;
					}
					final int slot = inventoryData.giveItem(this.simulation, whichUnit,
							whichItem, false);
					// A power up is used at once and dies instead of taking a slot.
					return BooleanJassValue.of((slot >= 0) || whichItem.isDead());
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitDropItemTarget",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CItem whichItem = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final CWidget target = nullable(arguments, 2, ObjectJassValueVisitor.getInstance());
					final CAbilityInventory inventoryData = usableInventory(whichUnit);
					if ((inventoryData == null) || (whichItem == null) || !(target instanceof CUnit)
							|| (inventoryData.getSlot(whichItem) == -1) || whichUnit.isIllusion()) {
						return BooleanJassValue.FALSE;
					}
					this.simulation.getDefaultPlayerUnitOrderExecutor(whichUnit.getPlayerIndex())
							.issueDropItemAtTargetOrder(whichUnit.getHandleId(), inventoryData.getHandleId(),
									OrderIds.dropitem, whichItem.getHandleId(), target.getHandleId(), false);
					return BooleanJassValue.TRUE;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitDropItemPoint",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CItem whichItem = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final float x = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final CAbilityInventory inventoryData = usableInventory(whichUnit);
					if ((inventoryData == null) || (whichItem == null) || (inventoryData.getSlot(whichItem) == -1)
							|| whichUnit.isIllusion()) {
						return BooleanJassValue.FALSE;
					}
					this.simulation.getDefaultPlayerUnitOrderExecutor(whichUnit.getPlayerIndex())
							.issueDropItemAtPointOrder(whichUnit.getHandleId(), inventoryData.getHandleId(),
									OrderIds.dropitem, whichItem.getHandleId(), x, y, false);
					return BooleanJassValue.TRUE;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitRemoveItem",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CItem whichItem = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final CAbilityInventory inventoryData = usableInventory(whichUnit);
					if ((inventoryData != null) && (whichItem != null)) {
						inventoryData.dropItem(this.simulation, whichUnit, whichItem, whichUnit.getX(),
								whichUnit.getY(), true);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitItemInSlot",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int whichSlot = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichUnit == null) {
						return new HandleJassValue(itemType, null);
					}
					final CAbilityInventory inventoryData = usableInventory(whichUnit);
					if (inventoryData != null) {
						return new HandleJassValue(itemType, inventoryData.getItemInSlot(whichSlot));
					}
					return new HandleJassValue(itemType, null);
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitHasItem",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CItem whichItem = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final CAbilityInventory inventoryData = usableInventory(whichUnit);
					return BooleanJassValue
							.of((inventoryData != null) && (whichItem != null) && (inventoryData.getSlot(whichItem) != -1));
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitInventoryCount",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CAbilityInventory inventoryData = usableInventory(whichUnit);
					int count = 0;
					if (inventoryData != null) {
						for (int slot = 0; slot < inventoryData.getItemCapacity(); slot++) {
							if (inventoryData.getItemInSlot(slot) != null) {
								count++;
							}
						}
					}
					return IntegerJassValue.of(count);
				});
		// 3.0 equipment and bag slots do not exist in this ruleset, so items are never equipped or bagged.
		for (final String name : new String[] { "BlzIsItemEquipped", "BlzIsItemInBag", "BlzUnitEquipItem",
				"BlzUnitHasItemBagged", "BlzUnitHasItemEquipped", "BlzUnitHasAnyItemEquipped",
				"BlzUnitHasItemEquipmentOfType", "BlzUnitCanEquipItemOfEquipmentType" }) {
			jassProgramVisitor.getJassNativeManager().createNative(name,
					(arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		}
		jassProgramVisitor.getJassNativeManager().createNative("BlzUnitHasLoadoutSlotEmpty",
				(arguments, globalScope, triggerScope) -> BooleanJassValue.TRUE);
		for (final String name : new String[] { "BlzUnitItemInBagSlot", "BlzUnitItemInEquipmentSlot",
				"BlzUnitUnequipItemFromSlot" }) {
			jassProgramVisitor.getJassNativeManager().createNative(name,
					(arguments, globalScope, triggerScope) -> new HandleJassValue(itemType, null));
		}
		jassProgramVisitor.getJassNativeManager().createNative("BlzUnitExtendedInventorySize",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(0));
		for (final String name : new String[] { "BlzUnitUnequipItem", "BlzSetItemColor", "BlzResetUnitTalents",
				"BlzSetHUDScale", "BlzLockHUDScale", "BlzUnlockHUDScale",
				"BlzSetThematicMusicAbsoluteVolume" }) {
			jassProgramVisitor.getJassNativeManager().createNative(name,
					(arguments, globalScope, triggerScope) -> null);
		}
	}

	private void registerStock() {
		jassProgramVisitor.getJassNativeManager().createNative("SetAllItemTypeSlots",
				(arguments, globalScope, triggerScope) -> {
					final int slots = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					this.simulation.setAllItemTypeSlots(slots);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetAllUnitTypeSlots",
				(arguments, globalScope, triggerScope) -> {
					final int slots = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					this.simulation.setAllUnitTypeSlots(slots);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetItemTypeSlots",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int slots = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichUnit != null) {
						this.simulation.getItemStock(whichUnit, true).setItemTypeSlots(slots);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitTypeSlots",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int slots = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichUnit != null) {
						this.simulation.getUnitStock(whichUnit, true).setUnitTypeSlots(slots);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddItemToStock",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int itemId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int currentStock = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final int stockMax = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
					final CSimulation game = this.simulation;
					if (whichUnit != null) {
						game.getItemStock(whichUnit, true).addToStock(game, new War3ID(itemId), currentStock,
								stockMax);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddUnitToStock",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int unitId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int currentStock = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final int stockMax = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
					final CSimulation game = this.simulation;
					if (whichUnit != null) {
						game.getUnitStock(whichUnit, true).addToStock(game, new War3ID(unitId), currentStock,
								stockMax);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddItemToAllStock",
				(arguments, globalScope, triggerScope) -> {
					final int itemId = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final int currentStock = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int stockMax = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final CSimulation game = this.simulation;
					for (final CUnit shop : game.getScriptStockedShops(SELL_ITEMS_ABILITY_ID)) {
						game.getItemStock(shop, true).addToStock(game, new War3ID(itemId), currentStock, stockMax);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddUnitToAllStock",
				(arguments, globalScope, triggerScope) -> {
					final int unitId = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final int currentStock = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int stockMax = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final CSimulation game = this.simulation;
					for (final CUnit shop : game.getScriptStockedShops(SELL_UNITS_ABILITY_ID)) {
						game.getUnitStock(shop, true).addToStock(game, new War3ID(unitId), currentStock, stockMax);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveItemFromStock",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int itemId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichUnit != null) {
						final CAbilitySellItems stock = this.simulation.getItemStock(whichUnit,
								false);
						if (stock != null) {
							stock.removeFromStock(new War3ID(itemId));
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveUnitFromStock",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int unitId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichUnit != null) {
						final CAbilitySellUnits stock = this.simulation.getUnitStock(whichUnit,
								false);
						if (stock != null) {
							stock.removeFromStock(new War3ID(unitId));
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveItemFromAllStock",
				(arguments, globalScope, triggerScope) -> {
					final int itemId = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CSimulation game = this.simulation;
					for (final CUnit shop : game.getScriptStockedShops(SELL_ITEMS_ABILITY_ID)) {
						final CAbilitySellItems stock = game.getItemStock(shop, false);
						if (stock != null) {
							stock.removeFromStock(new War3ID(itemId));
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveUnitFromAllStock",
				(arguments, globalScope, triggerScope) -> {
					final int unitId = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CSimulation game = this.simulation;
					for (final CUnit shop : game.getScriptStockedShops(SELL_UNITS_ABILITY_ID)) {
						final CAbilitySellUnits stock = game.getUnitStock(shop, false);
						if (stock != null) {
							stock.removeFromStock(new War3ID(unitId));
						}
					}
					return null;
				});
	}
}
