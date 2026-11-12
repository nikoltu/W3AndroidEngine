package com.etheller.warsmash.parsers.jass;

import com.badlogic.gdx.math.Rectangle;
import com.etheller.interpreter.ast.debug.JassException;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.scope.trigger.TriggerBooleanExpression;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.CodeJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.parsers.jass.triggers.UnitGroup;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitEnumFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit group natives: groups, enumeration and selection.
 */
final class GroupNatives extends CommonNatives {
	GroupNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		registerGroups();
		registerEnumeration();
	}

	private void registerGroups() {
		final HandleJassType unitType = type("unit");
		final HandleJassType groupType = type("group");

		// ============================================================================
		// Group API
		//
		jassProgramVisitor.getJassNativeManager().createNative("CreateGroup",
				(arguments, globalScope, triggerScope) -> new HandleJassValue(groupType,
						new UnitGroup(this.simulation.getHandleIdAllocator().createHandleId())));
		jassProgramVisitor.getJassNativeManager().createNative("DestroyGroup",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					release(group);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupAddUnit",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = nullable(arguments, 0,
							ObjectJassValueVisitor.<List<CUnit>>getInstance());
					final CUnit whichUnit = arguments.get(1).visit(ObjectJassValueVisitor.<CUnit>getInstance());
					if (group != null && whichUnit != null) {
						if (!group.contains(whichUnit)) {
							group.add(whichUnit);
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupRemoveUnit",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					final CUnit whichUnit = arguments.get(1).visit(ObjectJassValueVisitor.<CUnit>getInstance());
					group.remove(whichUnit);
					return null;
				});
		final JassFunction groupAddGroupFast = (arguments, globalScope, triggerScope) -> {
			final List<CUnit> group = arguments.get(0).visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
			final List<CUnit> addGroup = arguments.get(1).visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
			// TODO: this is not fast. This is slow. use HashSet<Unit> to be fast.
			for (final CUnit unit : addGroup) {
				if (!group.contains(unit)) {
					group.add(unit);
				}
			}
			return null;
		};
		jassProgramVisitor.getJassNativeManager().createNative("BlzGroupAddGroupFast", groupAddGroupFast);
		jassProgramVisitor.getJassNativeManager().createNative("GroupAddGroupFast", groupAddGroupFast);
		final JassFunction groupRemoveGroupFast = (arguments, globalScope, triggerScope) -> {
			final List<CUnit> group = arguments.get(0).visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
			final List<CUnit> removeGroup = arguments.get(1)
					.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
			group.removeAll(removeGroup);
			return null;
		};
		jassProgramVisitor.getJassNativeManager().createNative("BlzGroupRemoveGroupFast", groupRemoveGroupFast);
		jassProgramVisitor.getJassNativeManager().createNative("GroupRemoveGroupFast", groupRemoveGroupFast);
		jassProgramVisitor.getJassNativeManager().createNative("GroupClear",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					group.clear();
					return null;
				});
		final JassFunction groupGetSize = (arguments, globalScope, triggerScope) -> {
			final List<CUnit> group = arguments.get(0).visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
			return IntegerJassValue.of(group.size());
		};
		jassProgramVisitor.getJassNativeManager().createNative("BlzGroupGetSize", groupGetSize);
		jassProgramVisitor.getJassNativeManager().createNative("GroupGetSize", groupGetSize);
		final JassFunction groupGetUnitAt = (arguments, globalScope, triggerScope) -> {
			final List<CUnit> group = arguments.get(0).visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
			final int index = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
			return new HandleJassValue(unitType, group.get(index));
		};
		jassProgramVisitor.getJassNativeManager().createNative("BlzGroupUnitAt", groupGetUnitAt);
		jassProgramVisitor.getJassNativeManager().createNative("GroupUnitAt", groupGetUnitAt);
		// Scripts call these for the local player, usually inside a GetLocalPlayer() check. The change reaches the
		// game through the player's selection command, as a click does; IsUnitSelected sees it at once.
		jassProgramVisitor.getJassNativeManager().createNative("SelectUnit",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit != null) {
						final boolean select = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
						this.simulation.getPlayer(war3MapViewer.getLocalPlayerIndex()).getSelection()
								.scriptSelect(whichUnit, select);
						meleeUI.scriptSelectUnit(whichUnit, select);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ClearSelection",
				(arguments, globalScope, triggerScope) -> {
					this.simulation.getPlayer(war3MapViewer.getLocalPlayerIndex()).getSelection().scriptClear();
					meleeUI.scriptClearSelection();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitSelected",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CPlayerJass player = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					return BooleanJassValue.of((whichUnit != null) && (player != null)
							&& this.simulation.getPlayer(player.getId()).getSelection().isSelected(whichUnit));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitInGroup",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance());
					final List<CUnit> group = nullable(arguments, 1,
							ObjectJassValueVisitor.<List<CUnit>>getInstance());
					return BooleanJassValue.of((whichUnit != null) && (group != null) && group.contains(whichUnit));
				});
	}

	private void registerEnumeration() {
		final HandleJassType unitType = type("unit");

		jassProgramVisitor.getJassNativeManager().createNative("GroupEnumUnitsOfType",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					// As in the original, every enumeration replaces the contents of the group.
					group.clear();
					final String unitname = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final TriggerBooleanExpression filter = nullable(arguments, 2,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
						if (unitname.equals(unit.getUnitType().getLegacyName())) {
							if ((filter == null) || filter.evaluate(globalScope,
									CommonTriggerExecutionScope.filterScope(triggerScope, unit))) {
								group.add(unit);
							}
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupEnumUnitsOfPlayer",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					group.clear();
					final CPlayerJass player = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final TriggerBooleanExpression filter = nullable(arguments, 2,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					if (player == null) {
						return null;
					}
					for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
						if (unit.getPlayerIndex() == player.getId()) {
							if ((filter == null) || filter.evaluate(globalScope,
									CommonTriggerExecutionScope.filterScope(triggerScope, unit))) {
								group.add(unit);
							}
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupEnumUnitsSelected",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					group.clear();
					final CPlayerJass player = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CPlayerJass>getInstance());
					final TriggerBooleanExpression filter = nullable(arguments, 2,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					final List<CUnit> selected = new ArrayList<>(
							this.simulation.getPlayer(player.getId()).getSelection().getUnits());
					for (final CUnit unit : selected) {
						if ((filter == null) || filter.evaluate(globalScope,
								CommonTriggerExecutionScope.filterScope(triggerScope, unit))) {
							group.add(unit);
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupEnumUnitsOfTypeCounted",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					group.clear();
					final String unitname = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final TriggerBooleanExpression filter = nullable(arguments, 2,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					final Integer countLimit = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
					int count = 0;
					for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
						if (unitname.equals(unit.getUnitType().getLegacyName())) {
							if ((filter == null) || filter.evaluate(globalScope,
									CommonTriggerExecutionScope.filterScope(triggerScope, unit))) {
								group.add(unit);
								count++;
								if (count >= countLimit) {
									break;
								}
							}
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupEnumUnitsInRect",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					group.clear();
					final Rectangle rect = nullable(arguments, 1, ObjectJassValueVisitor.<Rectangle>getInstance());
					if (rect != null) {
						final TriggerBooleanExpression filter = nullable(arguments, 2,
								ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
						// A corpse is enumerated until it decays, as by the range enumeration.
						this.simulation.getWorldCollision().enumUnitsOrCorpsesInRect(rect, (unit) -> {
							if (rectCellsContain(rect, unit.getX(), unit.getY()) && ((filter == null)
									|| filter.evaluate(globalScope,
											CommonTriggerExecutionScope.filterScope(triggerScope, unit)))) {
								group.add(unit);
							}
							return false;
						});
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupEnumUnitsInRectCounted",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					group.clear();
					final Rectangle rect = arguments.get(1).visit(ObjectJassValueVisitor.<Rectangle>getInstance());
					final TriggerBooleanExpression filter = nullable(arguments, 2,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					final Integer countLimit = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
					this.simulation.getWorldCollision().enumUnitsOrCorpsesInRect(rect,
							new CUnitEnumFunction() {
								int count = 0;

								@Override
								public boolean call(final CUnit unit) {
									if (rectCellsContain(rect, unit.getX(), unit.getY()) && ((filter == null)
											|| filter.evaluate(globalScope,
													CommonTriggerExecutionScope.filterScope(triggerScope, unit)))) {
										group.add(unit);
										this.count++;
										if (this.count >= countLimit) {
											return true;
										}
									}
									return false;
								}
							});
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupEnumUnitsInRange",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					group.clear();
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float radius = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final TriggerBooleanExpression filter = nullable(arguments, 4,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					this.simulation.getWorldCollision().enumUnitsOrCorpsesInRange(x, y, radius,
							(unit) -> {
								// Measured on 1.29: centre to centre, a unit's collision size does not count.
								if (StrictMath.hypot(unit.getX() - x, unit.getY() - y) <= radius) {
									if ((filter == null) || filter.evaluate(globalScope,
											CommonTriggerExecutionScope.filterScope(triggerScope, unit))) {
										group.add(unit);
									}
								}
								return false;
							});
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupEnumUnitsInRangeOfLoc",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					group.clear();
					final AbilityPointTarget whichLocation = arguments.get(1)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					final float x = whichLocation.x;
					final float y = whichLocation.y;
					final float radius = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final TriggerBooleanExpression filter = nullable(arguments, 3,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());

					this.simulation.getWorldCollision().enumUnitsOrCorpsesInRange(x, y, radius,
							(unit) -> {
								if (StrictMath.hypot(unit.getX() - x, unit.getY() - y) <= radius) {
									if ((filter == null) || filter.evaluate(globalScope,
											CommonTriggerExecutionScope.filterScope(triggerScope, unit))) {
										group.add(unit);
									}
								}
								return false;
							});
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupEnumUnitsInRangeCounted",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					group.clear();
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float radius = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final TriggerBooleanExpression filter = nullable(arguments, 4,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					final Integer countLimit = arguments.get(5).visit(IntegerJassValueVisitor.getInstance());
					this.simulation.getWorldCollision().enumUnitsOrCorpsesInRange(x, y, radius,
							new CUnitEnumFunction() {
								int count = 0;

								@Override
								public boolean call(final CUnit unit) {
									if (StrictMath.hypot(unit.getX() - x, unit.getY() - y) <= radius) {
										if ((filter == null) || filter.evaluate(globalScope,
												CommonTriggerExecutionScope.filterScope(triggerScope, unit))) {
											group.add(unit);
											this.count++;
											if (this.count >= countLimit) {
												return true;
											}
										}
									}
									return false;
								}
							});
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupEnumUnitsInRangeOfLocCounted",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					group.clear();
					final AbilityPointTarget whichLocation = arguments.get(1)
							.visit(ObjectJassValueVisitor.<AbilityPointTarget>getInstance());
					final float x = whichLocation.x;
					final float y = whichLocation.y;
					final float radius = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final TriggerBooleanExpression filter = nullable(arguments, 3,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					final Integer countLimit = arguments.get(4).visit(IntegerJassValueVisitor.getInstance());
					this.simulation.getWorldCollision().enumUnitsOrCorpsesInRange(x, y, radius,
							new CUnitEnumFunction() {
								int count = 0;

								@Override
								public boolean call(final CUnit unit) {
									if (StrictMath.hypot(unit.getX() - x, unit.getY() - y) <= radius) {
										if ((filter == null) || filter.evaluate(globalScope,
												CommonTriggerExecutionScope.filterScope(triggerScope, unit))) {
											group.add(unit);
											this.count++;
											if (this.count >= countLimit) {
												return true;
											}
										}
									}
									return false;
								}
							});
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForGroup",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (group != null) {
						final CodeJassValue callback = arguments.get(1).visit(CodeJassValueVisitor.getInstance());
						try {
							// Measured on 1.29: the callback may add or remove units of the same group; the loop
							// still calls it once for each unit the group held when it started.
							for (final CUnit unit : new ArrayList<>(group)) {
								globalScope.runThreadUntilCompletion(globalScope.createThread(callback,
										CommonTriggerExecutionScope.enumScope(triggerScope, unit)));
							}
						}
						catch (final Exception e) {
							throw new JassException(globalScope, "Exception during ForGroup", e);
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("FirstOfGroup",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = arguments.get(0)
							.visit(ObjectJassValueVisitor.<List<CUnit>>getInstance());
					if (group.isEmpty()) {
						return unitType.getNullValue();
					}
					return new HandleJassValue(unitType, group.get(0));
				});
		// unit group extensions
		jassProgramVisitor.getJassNativeManager().createNative("GroupEnumUnitsInRangeOfUnit",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnit whichUnit = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final float x = whichUnit.getX();
					final float y = whichUnit.getY();
					final float radius = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final TriggerBooleanExpression filter = nullable(arguments, 3,
							ObjectJassValueVisitor.getInstance());

					this.simulation.getWorldCollision().enumUnitsOrCorpsesInRect(
							tempRect.set(x - radius, y - radius, radius * 2, radius * 2), (unit) -> {
								if (whichUnit.canReach(unit, radius)) {
									if ((filter == null) || filter.evaluate(globalScope,
											CommonTriggerExecutionScope.filterScope(triggerScope, unit))) {
										group.add(unit);
									}
								}
								return false;
							});
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GroupEnumUnitsInRangeOfUnitCounted",
				(arguments, globalScope, triggerScope) -> {
					final List<CUnit> group = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnit whichUnit = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final float x = whichUnit.getX();
					final float y = whichUnit.getY();
					final float radius = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final TriggerBooleanExpression filter = nullable(arguments, 3,
							ObjectJassValueVisitor.getInstance());
					final Integer countLimit = arguments.get(4).visit(IntegerJassValueVisitor.getInstance());

					this.simulation.getWorldCollision().enumUnitsOrCorpsesInRect(
							tempRect.set(x - radius, y - radius, radius * 2, radius * 2), new CUnitEnumFunction() {
								int count = 0;

								@Override
								public boolean call(final CUnit unit) {
									if (whichUnit.canReach(unit, radius)) {
										if ((filter == null) || filter.evaluate(globalScope,
												CommonTriggerExecutionScope.filterScope(triggerScope, unit))) {
											group.add(unit);
											this.count++;
											if (this.count >= countLimit) {
												return true;
											}
										}
									}
									return false;
								}
							});
					return null;
				});
	}
}
