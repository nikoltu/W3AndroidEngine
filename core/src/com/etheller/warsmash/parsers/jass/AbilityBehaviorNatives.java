package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassTypeConstructor;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StaticStructTypeJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.CodeJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StaticStructTypeJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.IconUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.OrderButtonUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.COrderButton.JassOrderButtonType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.COrderButton;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.jass.CAbilityJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.jass.CAbilityOrderButtonJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.jass.CAbilityTypeJassDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.jass.CodeJassValueBehaviorExpr;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.BehaviorTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.jass.CAbstractRangedBehaviorJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.jass.CBehaviorJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.jass.CRangedBehaviorJass;

/**
 * Warsmash ability API: behaviors, ability types, icons, order buttons and usable checks.
 */
final class AbilityBehaviorNatives extends CommonNatives {
	private final RenderSide render;

	AbilityBehaviorNatives(final CommonEnvironment environment) {
		super(environment);
		this.render = RenderSide.of(this.war3MapViewer);
	}

	@Override
	void register() {
		registerBehaviors();
		registerAbilityTypes();
		registerOrderButtons();
		registerUsableChecks();
	}

	private void registerBehaviors() {
		final HandleJassType unitType = type("unit");
		final HandleJassType abilitybehaviorType = type("behavior");
		final HandleJassType behaviorexprType = type("behaviorexpr");
		final HandleJassType abilitytargetType = type("abilitytarget");
		final HandleJassType rangedbehaviorType = type("rangedbehavior");
		final HandleJassType abstractrangedbehaviorType = type("abstractrangedbehavior");

		// Warsmash Ability API
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitMoveFollowBehavior",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int highlightOrderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final CWidget whichFollowTarget = nullable(arguments, 2, ObjectJassValueVisitor.getInstance());
					if (unit == null) {
						return null;
					}
					return new HandleJassValue(abilitybehaviorType,
							unit.getFollowBehavior().reset(this.simulation, highlightOrderId, whichFollowTarget));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitMovePointBehavior",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int highlightOrderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final double targetX = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final double targetY = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					if (unit == null) {
						return null;
					}
					return new HandleJassValue(abilitybehaviorType, unit.getMoveBehavior().reset(highlightOrderId,
							new AbilityPointTarget((float) targetX, (float) targetY)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitMovePointBehaviorLoc",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int highlightOrderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final AbilityPointTarget target = arguments.get(2).visit(ObjectJassValueVisitor.getInstance());
					if (unit == null) {
						return null;
					}

					return new HandleJassValue(abilitybehaviorType, unit.getMoveBehavior().reset(highlightOrderId,
							new AbilityPointTarget(target.x, target.y)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitAttackMovePointBehavior",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final double targetX = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					final double targetY = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					if (unit == null) {
						return null;
					}
					return new HandleJassValue(abilitybehaviorType, unit.getAttackMoveBehavior()
							.reset(new AbilityPointTarget((float) targetX, (float) targetY)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitAttackMovePointBehaviorLoc",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final AbilityPointTarget target = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if (unit == null) {
						return null;
					}
					return new HandleJassValue(abilitybehaviorType,
							unit.getAttackMoveBehavior().reset(new AbilityPointTarget(target.x, target.y)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitAttackWidgetBehavior",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int highlightOrderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int whichUnitAttackIndex = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final CWidget target = nullable(arguments, 3, ObjectJassValueVisitor.getInstance());
					if (unit == null) {
						return null;
					}
					return new HandleJassValue(abilitybehaviorType,
							unit.getAttackBehavior().reset(this.simulation, highlightOrderId,
									unit.getCurrentAttacks().get(whichUnitAttackIndex), target, false,
									CBehaviorAttackListener.DO_NOTHING));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitAttackGroundBehavior",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int highlightOrderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int whichUnitAttackIndex = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final double targetX = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final double targetY = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					if (unit == null) {
						return null;
					}
					return new HandleJassValue(abilitybehaviorType,
							unit.getAttackBehavior().reset(this.simulation, highlightOrderId,
									unit.getCurrentAttacks().get(whichUnitAttackIndex),
									new AbilityPointTarget((float) targetX, (float) targetY), false,
									CBehaviorAttackListener.DO_NOTHING));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitAttackGroundBehaviorLoc",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int highlightOrderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int whichUnitAttackIndex = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final AbilityPointTarget target = arguments.get(3).visit(ObjectJassValueVisitor.getInstance());
					if (unit == null) {
						return null;
					}
					return new HandleJassValue(abilitybehaviorType, unit.getAttackBehavior().reset(this.simulation,
							highlightOrderId, unit.getCurrentAttacks().get(whichUnitAttackIndex),
							new AbilityPointTarget(target.x, target.y), false, CBehaviorAttackListener.DO_NOTHING));
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitPollNextOrderBehavior",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					return new HandleJassValue(abilitybehaviorType,
							whichUnit.pollNextOrderBehavior(this.simulation));
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateAbilityBehavior",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(abilitybehaviorType, new CBehaviorJass(globalScope));
				});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyAbilityBehavior",
				(arguments, globalScope, triggerScope) -> {
					return null;
				});
		abilitybehaviorType.setConstructorNative(new HandleJassTypeConstructor("CreateAbilityBehavior"));
		abilitybehaviorType.setDestructorNative(new HandleJassTypeConstructor("DestroyAbilityBehavior"));
		jassProgramVisitor.getJassNativeManager().createNative("CreateRangedBehavior",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(rangedbehaviorType, new CRangedBehaviorJass(globalScope));
				});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyRangedBehavior",
				(arguments, globalScope, triggerScope) -> {
					return null;
				});
		rangedbehaviorType.setConstructorNative(new HandleJassTypeConstructor("CreateRangedBehavior"));
		rangedbehaviorType.setDestructorNative(new HandleJassTypeConstructor("DestroyRangedBehavior"));
		jassProgramVisitor.getJassNativeManager().createNative("CreateAbstractRangedBehavior",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return new HandleJassValue(abstractrangedbehaviorType,
							new CAbstractRangedBehaviorJass(unit, globalScope));
				});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyAbstractRangedBehavior",
				(arguments, globalScope, triggerScope) -> {
					return null;
				});
		abstractrangedbehaviorType
				.setConstructorNative(new HandleJassTypeConstructor("CreateAbstractRangedBehavior"));
		abstractrangedbehaviorType
				.setDestructorNative(new HandleJassTypeConstructor("DestroyAbstractRangedBehavior"));
		jassProgramVisitor.getJassNativeManager().createNative("AbstractRangedBehaviorResetI",
				(arguments, globalScope, triggerScope) -> {
					final CAbstractRangedBehaviorJass whichBehavior = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final AbilityTarget target = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					return new HandleJassValue(abilitybehaviorType,
							whichBehavior.resetNative(this.simulation, target));
				});
		jassProgramVisitor.getJassNativeManager().createNative("AbstractRangedBehaviorResetII",
				(arguments, globalScope, triggerScope) -> {
					final CAbstractRangedBehaviorJass whichBehavior = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					final AbilityTarget target = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final boolean disableCollision = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					return new HandleJassValue(abilitybehaviorType,
							whichBehavior.resetNative(this.simulation, target, disableCollision));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetRangedBehaviorTarget",
				(arguments, globalScope, triggerScope) -> {
					final CBehavior whichBehavior = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return new HandleJassValue(abilitytargetType,
							whichBehavior.visit(BehaviorTargetVisitor.INSTANCE));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetAbstractRangedBehaviorSourceUnit",
				(arguments, globalScope, triggerScope) -> {
					final CAbstractRangedBehaviorJass whichBehavior = nullable(arguments, 0,
							ObjectJassValueVisitor.getInstance());
					return new HandleJassValue(unitType, whichBehavior.getUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitCanReach",
				(arguments, globalScope, triggerScope) -> {
					final CUnit source = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final AbilityTarget target = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final float radius = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (target == null) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(source.canReach(target, radius));
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateBehaviorExpr",
				(arguments, globalScope, triggerScope) -> {
					final CodeJassValue func = arguments.get(0).visit(CodeJassValueVisitor.getInstance());
					return new HandleJassValue(behaviorexprType, new CodeJassValueBehaviorExpr(func));
				});
	}

	private void registerAbilityTypes() {
		final HandleJassType iconuiType = type("iconui");

		jassProgramVisitor.getJassNativeManager().createNative("RegisterAbilityType",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CAbilityTypeJassDefinition whichAbilityType = nullable(arguments, 1,
							ObjectJassValueVisitor.getInstance());
					this.simulation.getAbilityData().registerJassType(new War3ID(rawcode),
							whichAbilityType);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RegisterAbilityStructType",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final StaticStructTypeJassValue whichAbilityType = nullable(arguments, 1,
							StaticStructTypeJassValueVisitor.getInstance());
					final War3ID codeId = new War3ID(rawcode);
					this.simulation.getAbilityData().registerJassType(codeId,
							new CAbilityTypeJassDefinition(globalScope, whichAbilityType));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitIconUI",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return new HandleJassValue(iconuiType,
							war3MapViewer.getAbilityDataUI().getUnitUI(new War3ID(rawcode)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetAbilityOnIconUI",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final int level = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final AbilityUI abilityUI = war3MapViewer.getAbilityDataUI().getUI(new War3ID(rawcode));
					return new HandleJassValue(iconuiType, abilityUI == null ? null : abilityUI.getOnIconUI(level));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetAbilityOffIconUI",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final int level = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final AbilityUI abilityUI = war3MapViewer.getAbilityDataUI().getUI(new War3ID(rawcode));
					return new HandleJassValue(iconuiType, abilityUI == null ? null : abilityUI.getOffIconUI(level));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetAbilityLearnIconUI",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final AbilityUI abilityUI = war3MapViewer.getAbilityDataUI().getUI(new War3ID(rawcode));
					return new HandleJassValue(iconuiType, abilityUI == null ? null : abilityUI.getLearnIconUI());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetItemIconUI",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return new HandleJassValue(iconuiType,
							war3MapViewer.getAbilityDataUI().getItemUI(new War3ID(rawcode)));
				});
	}

	private void registerOrderButtons() {
		final HandleJassType orderbuttonType = type("orderbutton");

		jassProgramVisitor.getJassNativeManager().createNative("CreateOrderButton",
				(arguments, globalScope, triggerScope) -> {
					final JassOrderButtonType type = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int orderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final CAbilityOrderButtonJass javaValue = new CAbilityOrderButtonJass(
							this.simulation.getHandleIdAllocator().createHandleId(), orderId);
					javaValue.setType(type);
					final AbilityDataUI abilityDataUI = war3MapViewer.getAbilityDataUI();
					this.render.post(RenderSide.WORLD, () -> abilityDataUI.createRenderPeer(javaValue));
					return new HandleJassValue(orderbuttonType, javaValue);
				});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyOrderButton",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final AbilityDataUI abilityDataUI = war3MapViewer.getAbilityDataUI();
					this.render.post(RenderSide.WORLD, () -> abilityDataUI.removeRenderPeer(orderCommandCard));
					return null;
				});
		orderbuttonType.setConstructorNative(new HandleJassTypeConstructor("CreateOrderButton"));
		orderbuttonType.setDestructorNative(new HandleJassTypeConstructor("DestroyOrderButton"));
		jassProgramVisitor.getJassNativeManager().createNative("AbilityAddOrderButton",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityJass abilityTypeTmp = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final CAbilityOrderButtonJass commandCard = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					abilityTypeTmp.addJassOrder(commandCard);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("AbilityRemoveOrderButton",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityJass abilityTypeTmp = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final CAbilityOrderButtonJass commandCard = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					abilityTypeTmp.removeJassOrder(commandCard);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonAutoCastOrderId",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final int autoCastOrderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					orderCommandCard.setAutoCastOrderId(autoCastOrderId);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonUnAutoCastOrderId",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final int autoCastUnOrderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					orderCommandCard.setAutoCastUnOrderId(autoCastUnOrderId);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonContainerMenuOrderId",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final int orderId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					orderCommandCard.setContainerMenuOrderId(orderId);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonDisabled",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final boolean disabled = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					orderCommandCard.setDisabled(disabled);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonManaCost",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final int manaCost = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					orderCommandCard.setManaCost(manaCost);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonGoldCost",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final int goldCost = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					orderCommandCard.setGoldCost(goldCost);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonLumberCost",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final int lumberCost = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					orderCommandCard.setLumberCost(lumberCost);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonFoodCost",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final int foodCost = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					orderCommandCard.setFoodCost(foodCost);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonCharges",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final int charges = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					orderCommandCard.setCharges(charges);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonAutoCastActive",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final boolean active = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					orderCommandCard.setAutoCastActive(active);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonHidden",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final boolean hidden = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					orderCommandCard.setHidden(hidden);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsOrderButtonHidden",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(orderCommandCard.isHidden());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonIconPath",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final String iconPath = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final AbilityDataUI abilityDataUI = war3MapViewer.getAbilityDataUI();
					postToButton(orderCommandCard, peer -> {
						peer.setIcon(this.gameUI.loadTexture(iconPath));
						peer.setIconDisabled(this.gameUI
								.loadTexture(AbilityDataUI.disable(iconPath, abilityDataUI.getDisabledPrefix())));
					});
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonButtonPositionX",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final int buttonPosX = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					orderCommandCard.setButtonPositionX(buttonPosX);
					postToButton(orderCommandCard, peer -> peer.setButtonPositionX(buttonPosX));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonButtonPositionY",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final int buttonPosY = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					orderCommandCard.setButtonPositionY(buttonPosY);
					postToButton(orderCommandCard, peer -> peer.setButtonPositionY(buttonPosY));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonToolTip",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final String tip = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					orderCommandCard.setTip(tip);
					postToButton(orderCommandCard, peer -> peer.setTip(tip));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonUberTip",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final String uberTip = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					orderCommandCard.setUberTip(uberTip);
					postToButton(orderCommandCard, peer -> peer.setUberTip(uberTip));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonHotKey",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final char hotkey = arguments.get(1).visit(StringJassValueVisitor.getInstance()).charAt(0);
					orderCommandCard.setHotkey(hotkey);
					postToButton(orderCommandCard, peer -> peer.setHotkey(hotkey));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonByIconUI",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final IconUI theIconUi = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					orderCommandCard.setButtonPositionX(theIconUi.getButtonPositionX());
					orderCommandCard.setButtonPositionY(theIconUi.getButtonPositionY());
					orderCommandCard.setTip(theIconUi.getToolTip());
					orderCommandCard.setUberTip(theIconUi.getUberTip());
					orderCommandCard.setHotkey(theIconUi.getHotkey());
					postToButton(orderCommandCard, peer -> peer.setFromIconUI(theIconUi));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonPreviewBuildUnitId",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final War3ID unitId = new War3ID(arguments.get(1).visit(IntegerJassValueVisitor.getInstance()));
					orderCommandCard.setPreviewBuildUnitId(unitId);
					postToButton(orderCommandCard, peer -> peer.setPreviewBuildUnitId(unitId));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetOrderButtonAOE",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final float radius = (float) arguments.get(1).visit(RealJassValueVisitor.getInstance())
							.doubleValue();
					orderCommandCard.setMouseTargetRadius(radius);
					postToButton(orderCommandCard, peer -> peer.setMouseTargetRadius(radius));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonOrderId",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(orderCommandCard.getOrderId());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonAutoCastOrderId",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(orderCommandCard.getAutoCastOrderId());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonUnAutoCastOrderId",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(orderCommandCard.getAutoCastUnOrderId());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonContainerMenuOrderId",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(orderCommandCard.getContainerMenuOrderId());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsOrderButtonDisabled",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(orderCommandCard.isDisabled());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonManaCost",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(orderCommandCard.getManaCost());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonGoldCost",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(orderCommandCard.getGoldCost());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonLumberCost",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(orderCommandCard.getLumberCost());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonFoodCost",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(orderCommandCard.getFoodCost());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonCharges",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(orderCommandCard.getCharges());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsOrderButtonAutoCastActive",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(orderCommandCard.isAutoCastActive());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonButtonPositionX",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(orderCommandCard.getButtonPositionX());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonButtonPositionY",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(orderCommandCard.getButtonPositionY());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonToolTip",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return StringJassValue.of(orderCommandCard.getTip());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonUberTip",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return StringJassValue.of(orderCommandCard.getUberTip());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonHotKey",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return StringJassValue.of(String.valueOf(orderCommandCard.getHotkey()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonPreviewBuildUnitId",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final War3ID unitId = orderCommandCard.getPreviewBuildUnitId();
					return IntegerJassValue.of(unitId == null ? 0 : unitId.getValue());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderButtonAOE",
				(arguments, globalScope, triggerScope) -> {
					final COrderButton orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					return RealJassValue.of(orderCommandCard.getMouseTargetRadius());
				});
	}

	/** The look of a button is drawn by the render side from its own copy, which a command updates in frame order. */
	private void postToButton(final COrderButton button, final java.util.function.Consumer<OrderButtonUI> change) {
		final AbilityDataUI abilityDataUI = war3MapViewer.getAbilityDataUI();
		this.render.post(RenderSide.WORLD, () -> {
			final OrderButtonUI peer = abilityDataUI.getRenderPeer(button);
			if (peer != null) {
				change.accept(peer);
			}
		});
	}
	private void registerUsableChecks() {
		jassProgramVisitor.getJassNativeManager().createNative("FailUsableCheckOnRequirement",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityOrderButtonJass orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final int techId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int techLevel = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					orderCommandCard.getUsableReceiver().missingRequirement(new War3ID(techId), techLevel);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("FailUsableCheckOnHeroLevelRequirement",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityOrderButtonJass orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final int heroLevel = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					orderCommandCard.getUsableReceiver().missingHeroLevelRequirement(heroLevel);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("FailUsableCheckOnCooldown",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityOrderButtonJass orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final float cooldownRemaining = arguments.get(1).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					final float cooldown = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					orderCommandCard.getUsableReceiver().cooldownNotYetReady(cooldownRemaining, cooldown);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("FailUsableCheckWithMessage",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityOrderButtonJass orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final String message = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					orderCommandCard.getUsableReceiver().activationCheckFailed(message);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("FailTargetCheckWithMessage",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityOrderButtonJass orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final String message = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					orderCommandCard.getTargetReceiver().targetCheckFailed(message);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PassTargetCheck",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityOrderButtonJass orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					final Object finalizedTarget = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					orderCommandCard.getTargetReceiver().targetOk(finalizedTarget);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PassUsableCheck",
				(arguments, globalScope, triggerScope) -> {
					final CAbilityOrderButtonJass orderCommandCard = arguments.get(0)
							.visit(ObjectJassValueVisitor.getInstance());
					orderCommandCard.getUsableReceiver().useOk();
					return null;
				});
	}
}
