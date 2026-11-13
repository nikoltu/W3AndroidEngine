package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassTypeConstructor;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassType;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.UnknownEnumHandle;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.parsers.jass.triggers.CUnitPool;
import com.etheller.warsmash.parsers.jass.triggers.EnumSetHandle;
import com.etheller.warsmash.parsers.jass.triggers.LocationJass;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CComputerWorkers;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.CBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.neutral.CAbilityWayGate;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units.CBuffEnsnare;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CGenericDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerColor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.state.CUnitState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerDamagePoint;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerFlyHeight;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CRarityControl;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ResourceType;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit natives: creation, life and state, position and movement, appearance and properties.
 */
final class UnitNatives extends CommonNatives {
	UnitNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		registerProperties();
		registerLifeAndState();
		registerCreation();
		registerPositionAndMovement();
		registerAppearance();
	}

	private void registerProperties() {
		final HandleJassType playerType = type("player");
		final HandleJassType resourcetypeType = type("resourcetype");

		jassProgramVisitor.getJassNativeManager().createNative("GetUnitName",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichWidget = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					if (whichWidget == null) {
						return JassType.STRING.getNullValue();
					}
					return new StringJassValue(whichWidget.getName());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitOwner",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CPlayer whichPlayer = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final boolean changeColor = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());

					if ((whichUnit != null) && (whichPlayer != null)) {
						whichUnit.changeOwner(this.simulation, whichPlayer.getId(), changeColor);
						CComputerWorkers.onScriptUnitGiven(this.simulation, whichUnit);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetResourceAmount",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int resourceAmount = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					whichUnit.setGold(resourceAmount);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddResourceAmount",
				(final List<JassValue> arguments, final GlobalScope globalScope,
						final TriggerExecutionScope triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int resourceAmount = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					whichUnit.setGold(whichUnit.getGold() + resourceAmount);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetResourceAmount",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(whichUnit.getGold());
				});
		jassProgramVisitor.getJassNativeManager().createNative("WaygateGetDestinationX",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit != null) {
						final CAbilityWayGate wayGateAbility = whichUnit
								.getFirstAbilityOfType(CAbilityWayGate.class);
						if (wayGateAbility != null) {
							return RealJassValue.of(wayGateAbility.getDestination().x);
						}
					}
					return RealJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("WaygateGetDestinationY",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit != null) {
						final CAbilityWayGate wayGateAbility = whichUnit
								.getFirstAbilityOfType(CAbilityWayGate.class);
						if (wayGateAbility != null) {
							return RealJassValue.of(wayGateAbility.getDestination().y);
						}
					}
					return RealJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("WaygateSetDestination",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float x = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float y = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (whichUnit != null) {
						final CAbilityWayGate wayGateAbility = whichUnit
								.getFirstAbilityOfType(CAbilityWayGate.class);
						if (wayGateAbility != null) {
							wayGateAbility.setDestination(new AbilityPointTarget(x, y));
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("WaygateActivate",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean activate = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (whichUnit != null) {
						final CAbilityWayGate wayGateAbility = whichUnit
								.getFirstAbilityOfType(CAbilityWayGate.class);
						if (wayGateAbility != null) {
							wayGateAbility.setGateEnabled(activate);
							if (activate) {
								whichUnit.getUnitAnimationListener().addSecondaryTag(SecondaryTag.ALTERNATE);
							}
							else {
								whichUnit.getUnitAnimationListener().removeSecondaryTag(SecondaryTag.ALTERNATE);
							}
							whichUnit.getUnitAnimationListener().forceResetCurrentAnimation();
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("WaygateIsActive",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit != null) {
						final CAbilityWayGate wayGateAbility = whichUnit
								.getFirstAbilityOfType(CAbilityWayGate.class);
						if (wayGateAbility != null) {
							return BooleanJassValue.of(wayGateAbility.isGateEnabled());
						}
					}
					return BooleanJassValue.FALSE;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitType",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit != null) {
						final CUnitTypeJass whichUnitType = arguments.get(1)
								.visit(ObjectJassValueVisitor.getInstance());
						return BooleanJassValue.of(CBuffEnsnare.isScriptUnitType(whichUnit, whichUnitType));
					}
					return BooleanJassValue.FALSE;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitInTransport",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnit whichTransport = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && (whichTransport != null)
							&& (whichTransport.getCargoData() != null)
							&& whichTransport.getCargoData().contains(whichUnit));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitLoaded",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit != null) {
						for (final CUnit transport : this.simulation.getUnitsIncludingNew()) {
							if ((transport.getCargoData() != null) && transport.getCargoData().contains(whichUnit)) {
								return BooleanJassValue.TRUE;
							}
						}
					}
					return BooleanJassValue.FALSE;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitUseFood",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean useFood = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (whichUnit != null) {
						final CPlayer player = this.simulation.getPlayer(whichUnit.getPlayerIndex());
						player.setUnitFoodUsed(whichUnit, useFood ? whichUnit.getUnitType().getFoodUsed() : 0);
						player.setUnitFoodMade(whichUnit, useFood ? whichUnit.getUnitType().getFoodMade() : 0);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitFoodUsed",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(whichUnit != null ? whichUnit.getFoodUsed() : 0);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitFoodMade",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(whichUnit != null ? whichUnit.getFoodMade() : 0);
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitRace",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CRace whichRace = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());

					if (whichUnit != null) {
						final CUnitType t = whichUnit.getUnitType();

						return new BooleanJassValue(t.getRace().equals(whichRace));
					}

					return JassType.BOOLEAN.getNullValue();
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnit", (arguments, globalScope, triggerScope) -> {
			final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			final CUnit whichSpecifiedUnit = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());

			if ((whichUnit != null) && (whichSpecifiedUnit != null)) {
				return new BooleanJassValue(whichUnit.getHandleId() == whichSpecifiedUnit.getHandleId());
			}

			return JassType.BOOLEAN.getNullValue();
		});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitPointValue",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichWidget = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichWidget == null) {
						return IntegerJassValue.ZERO;
					}
					return IntegerJassValue.of(whichWidget.getUnitType().getPointValue());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitPointValueByType",
				(arguments, globalScope, triggerScope) -> {
					final int rawcode = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					return IntegerJassValue.of(this.simulation.getUnitData()
							.getUnitType(new War3ID(rawcode)).getPointValue());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitIllusion",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && whichUnit.isIllusion());
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitAddType",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CUnitTypeJass whichClassification = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(whichUnit.addUnitType(this.simulation, whichClassification));
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitRemoveType",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CUnitTypeJass whichClassification = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(whichUnit.removeUnitType(this.simulation, whichClassification));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitUserData",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichUnit == null) {
						return IntegerJassValue.ZERO;
					}
					return IntegerJassValue.of(whichUnit.getTriggerEditorCustomValue());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitUserData",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int data = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichUnit != null) {
						whichUnit.setTriggerEditorCustomValue(data);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitTypeId",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					if (whichUnit == null) {
						return IntegerJassValue.of(0);
					}
					return IntegerJassValue.of(whichUnit.getTypeId().getValue());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOwningPlayer",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					if (whichUnit == null) {
						return playerType.getNullValue();
					}
					return new HandleJassValue(playerType,
							this.simulation.getPlayer(whichUnit.getPlayerIndex()));
				});
		// Measured on 1.29: the unit natives read the fog where the unit stands, even for the player's own units, and
		// IsUnitInvisible is simply "not visible", also for units in fog or black mask.
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitVisible",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					final CPlayer whichPlayer = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && (whichPlayer != null)
							&& isUnitVisibleToPlayer(whichUnit, whichPlayer));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitInvisible",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					final CPlayer whichPlayer = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && (whichPlayer != null)
							&& !isUnitVisibleToPlayer(whichUnit, whichPlayer));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitFogged",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CPlayer whichPlayer = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && (whichPlayer != null)
							&& (unitFogState(whichUnit, whichPlayer) == CFogState.FOGGED)
							&& !this.simulation.isUnitVisionShared(whichUnit, whichPlayer));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitMasked",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CPlayer whichPlayer = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && (whichPlayer != null)
							&& (unitFogState(whichUnit, whichPlayer) == CFogState.MASKED)
							&& !this.simulation.isUnitVisionShared(whichUnit, whichPlayer));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitEnemy",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CPlayer whichPlayer = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if ((whichUnit == null) || (whichPlayer == null)) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(!whichUnit.isUnitAlly(whichPlayer));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitAlly",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CPlayer whichPlayer = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if ((whichUnit == null) || (whichPlayer == null)) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(whichUnit.isUnitAlly(whichPlayer));
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitDetected",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					final CPlayer whichPlayer = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					// Measured on 1.29: only an invisible, living unit reads as detected, and a reveal on it counts
					// even where the player's fog hides it.
					return BooleanJassValue.of((whichUnit != null) && (whichPlayer != null) && !whichUnit.isDead()
							&& (whichUnit.isRevealedTo(whichPlayer.getId()) || ((whichUnit.getInvisLevels() > 0)
									&& whichUnit.isVisible(this.simulation, whichPlayer.getId()))));
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitInstantReturnResources",
				(arguments, globalScope, triggerScope) -> {
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (targetUnit != null) {
						final CAbilityHarvest harv = targetUnit.getFirstAbilityOfType(CAbilityHarvest.class);
						if ((harv != null) && (harv.getCarriedResourceType() != null)
								&& (harv.getCarriedResourceAmount() > 0)) {
							final CPlayer pl = this.simulation.getPlayer(targetUnit.getPlayerIndex());
							switch (harv.getCarriedResourceType()) {
							case FOOD:
								// This might be a bad idea? Not sure it will ever matter
								pl.setFoodCap(Math.min(pl.getFoodCap() + harv.getCarriedResourceAmount(),
										pl.getFoodCapCeiling()));
								this.simulation.unitGainResourceEvent(targetUnit, pl.getId(),
										harv.getCarriedResourceType(), harv.getCarriedResourceAmount());
								harv.setCarriedResources(ResourceType.FOOD, 0);
								break;
							case GOLD:
								pl.addGold(harv.getCarriedResourceAmount());
								this.simulation.unitGainResourceEvent(targetUnit, pl.getId(),
										harv.getCarriedResourceType(), harv.getCarriedResourceAmount());
								harv.setCarriedResources(ResourceType.GOLD, 0);
								break;
							case LUMBER:
								pl.addLumber(harv.getCarriedResourceAmount());
								this.simulation.unitGainResourceEvent(targetUnit, pl.getId(),
										harv.getCarriedResourceType(), harv.getCarriedResourceAmount());
								harv.setCarriedResources(ResourceType.LUMBER, 0);
								break;
							case MANA:
								// ??
								break;
							default:
								break;

							}
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitGoldCost",
				(arguments, globalScope, triggerScope) -> {
					if (arguments.get(0) instanceof IntegerJassValue) {
						// Something dumb happened here, there seems to be a SmashJass version of this
						// and a CommonAI version of this both
						final Integer typeId = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
						final CUnitType unitTypeData = this.simulation.getUnitData()
								.getUnitType(new War3ID(typeId));
						if (unitTypeData != null) {
							return IntegerJassValue.of(unitTypeData.getGoldCost());
						}
						return IntegerJassValue.ZERO;
					}
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (targetUnit != null) {
						return IntegerJassValue.of(targetUnit.getUnitType().getGoldCost());
					}
					return IntegerJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitWoodCost",
				(arguments, globalScope, triggerScope) -> {
					if (arguments.get(0) instanceof IntegerJassValue) {
						// Something dumb happened here, there seems to be a SmashJass version of this
						// and a CommonAI version of this both
						final Integer typeId = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
						final CUnitType unitTypeData = this.simulation.getUnitData()
								.getUnitType(new War3ID(typeId));
						if (unitTypeData != null) {
							return IntegerJassValue.of(unitTypeData.getLumberCost());
						}
						return IntegerJassValue.ZERO;
					}
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (targetUnit != null) {
						return IntegerJassValue.of(targetUnit.getUnitType().getLumberCost());
					}
					return IntegerJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SendUnitBackToWork",
				(arguments, globalScope, triggerScope) -> {
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final ResourceType resourceType = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					if (targetUnit != null) {
						return new HandleJassValue(resourcetypeType,
								targetUnit.backToWork(this.simulation, resourceType));
					}
					return resourcetypeType.getNullValue();
				});
		jassProgramVisitor.getJassNativeManager().createNative("StartSacrificingUnit",
				(arguments, globalScope, triggerScope) -> {
					final CUnit factory = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnit toSacrifice = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final int resultUnitId = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					factory.queueSacrificingUnit(this.simulation, new War3ID(resultUnitId), toSacrifice);
					factory.notifyOrdersChanged();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("StartTrainingUnit",
				(arguments, globalScope, triggerScope) -> {
					final CUnit factory = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int resultUnitId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					factory.queueTrainingUnit(this.simulation, new War3ID(resultUnitId));
					factory.notifyOrdersChanged();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ForceResetUnitCurrentAnimation",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					whichUnit.getUnitAnimationListener().forceResetCurrentAnimation();
					return null;
				});
	}

	private void registerLifeAndState() {
		jassProgramVisitor.getJassNativeManager().createNative("UnitDamageTarget",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CWidget target = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final double amount = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final boolean attack = arguments.get(3).visit(BooleanJassValueVisitor.getInstance());
					final boolean ranged = arguments.get(4).visit(BooleanJassValueVisitor.getInstance());
					final Object attackTypeValue = nullable(arguments, 5, ObjectJassValueVisitor.getInstance());
					if (attackTypeValue instanceof UnknownEnumHandle) {
						// 1.29 deals no damage with an attack type it has no constant for.
						return BooleanJassValue.FALSE;
					}
					CAttackType attackType = (CAttackType) attackTypeValue;
					final Object damageTypeValue = nullable(arguments, 6, ObjectJassValueVisitor.getInstance());
					final Object weaponTypeValue = nullable(arguments, 7, ObjectJassValueVisitor.getInstance());
					// 1.29 still deals the damage with an unknown damage type of 0 or more, but
					// none with a negative one or with an unknown weapon type.
					if ((weaponTypeValue instanceof UnknownEnumHandle) || ((damageTypeValue instanceof UnknownEnumHandle)
							&& (((UnknownEnumHandle) damageTypeValue).getHandleId() < 0))) {
						return BooleanJassValue.FALSE;
					}
					CDamageType damageType = damageTypeValue instanceof CDamageType ? (CDamageType) damageTypeValue
							: null;
					CWeaponSoundTypeJass weaponType = (CWeaponSoundTypeJass) weaponTypeValue;
					if (whichUnit != null) {
						if (target != null) {
							if (attackType == null) {
								attackType = CAttackType.UNKNOWN;
							}
							if (weaponType == null) {
								weaponType = CWeaponSoundTypeJass.WHOKNOWS;
							}
							if (damageType == null) {
								damageType = CDamageType.UNKNOWN;
							}
							target.damage(this.simulation, whichUnit,
									new CGenericDamageFlags(attack, ranged), attackType, damageType,
									weaponType.name(), (float) amount);
							return BooleanJassValue.TRUE;
						}
					}
					return BooleanJassValue.FALSE;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitDamagePoint",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float delay = realArg(arguments, 1);
					final float radius = realArg(arguments, 2);
					final float x = realArg(arguments, 3);
					final float y = realArg(arguments, 4);
					final float amount = realArg(arguments, 5);
					final boolean attack = arguments.get(6).visit(BooleanJassValueVisitor.getInstance());
					final boolean ranged = arguments.get(7).visit(BooleanJassValueVisitor.getInstance());
					final Object attackTypeValue = nullable(arguments, 8, ObjectJassValueVisitor.getInstance());
					final Object damageTypeValue = nullable(arguments, 9, ObjectJassValueVisitor.getInstance());
					final Object weaponTypeValue = nullable(arguments, 10, ObjectJassValueVisitor.getInstance());
					if ((whichUnit == null) || (attackTypeValue instanceof UnknownEnumHandle)
							|| (weaponTypeValue instanceof UnknownEnumHandle) || ((damageTypeValue instanceof UnknownEnumHandle)
									&& (((UnknownEnumHandle) damageTypeValue).getHandleId() < 0))) {
						return BooleanJassValue.FALSE;
					}
					final CAttackType attackType = attackTypeValue == null ? CAttackType.UNKNOWN
							: (CAttackType) attackTypeValue;
					final CDamageType damageType = damageTypeValue instanceof CDamageType
							? (CDamageType) damageTypeValue : CDamageType.UNKNOWN;
					final CWeaponSoundTypeJass weaponType = weaponTypeValue == null ? CWeaponSoundTypeJass.WHOKNOWS
							: (CWeaponSoundTypeJass) weaponTypeValue;
					final CTimerDamagePoint damagePoint = new CTimerDamagePoint(whichUnit, x, y, radius, amount,
							attack, ranged, attackType, damageType, weaponType.name());
					if (delay > 0) {
						damagePoint.setTimeoutTime(delay);
						damagePoint.start(this.simulation);
					}
					else {
						damagePoint.damage(this.simulation);
					}
					return BooleanJassValue.TRUE;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitState",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnitState whichUnitState = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final float value = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (whichUnit != null) {
						whichUnit.setUnitState(this.simulation, whichUnitState, value);
					}
					else {
						Log.warn("got SetUnitState(null," + whichUnitState + "," + value
								+ ")  call (skipping because unit is null)");
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitState",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					final CUnitState whichUnitState = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if (whichUnit == null) {
						return RealJassValue.ZERO;
					}
					return RealJassValue
							.of(whichUnit.getScriptUnitState(this.simulation, whichUnitState));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetWidgetLife",
				(arguments, globalScope, triggerScope) -> {
					final CWidget whichWidget = inGame(nullable(arguments, 0, ObjectJassValueVisitor.getInstance()));
					if (whichWidget instanceof CUnit) {
						return new RealJassValue(((CUnit) whichWidget).getScriptLife());
					}
					return new RealJassValue(whichWidget == null ? 0 : whichWidget.getLife());
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetWidgetLife",
				(arguments, globalScope, triggerScope) -> {
					final CWidget whichWidget = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichWidget != null) {
						final float life = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
						if ((whichWidget instanceof CUnit) && ((CUnit) whichWidget).isDead()) {
							((CUnit) whichWidget).setDeadScriptLife(life);
						}
						else {
							whichWidget.setLife(this.simulation, life);
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitApplyTimedLife",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int buffId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final float duration = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					if ((whichUnit != null) && !whichUnit.isDead()) {
						for (final CAbility ability : new ArrayList<>(whichUnit.getAbilities())) {
							if (ability instanceof CBuffTimedLife) {
								((CBuffTimedLife) ability).markReplaced();
								whichUnit.remove(this.simulation, ability);
							}
						}
						// The original game counts these units as summoned (half experience when killed).
						whichUnit.addClassification(CUnitClassification.SUMMONED);
						whichUnit.add(this.simulation, new CBuffTimedLife(
								this.simulation.getHandleIdAllocator().createId(), new War3ID(buffId), duration,
								false, false));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitPauseTimedLife",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (whichUnit != null) {
						for (final CAbility ability : whichUnit.getAbilities()) {
							if (ability instanceof CBuffTimedLife) {
								((CBuffTimedLife) ability).setTimerPaused(flag);
							}
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitPaused",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && whichUnit.isPaused());
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitRemoveBuffs",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean removePositive = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final boolean removeNegative = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					for (final CBuff buff : matchingBuffs(whichUnit, removePositive, removeNegative, true, true,
							false, false, false)) {
						if (buff instanceof CBuffTimedLife) {
							((CBuffTimedLife) buff).markReplaced();
						}
						whichUnit.remove(this.simulation, buff);
					}
					// Measured on 1.29: stripping an illusion of its buffs ends it.
					if ((whichUnit != null) && whichUnit.isIllusion() && removePositive) {
						whichUnit.kill(this.simulation);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitRemoveBuffsEx",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					for (final CBuff buff : matchingBuffs(whichUnit, arguments, 1)) {
						if (buff instanceof CBuffTimedLife) {
							((CBuffTimedLife) buff).markReplaced();
						}
						whichUnit.remove(this.simulation, buff);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitHasBuffsEx",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(!matchingBuffs(whichUnit, arguments, 1).isEmpty());
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitCountBuffsEx",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return IntegerJassValue.of(matchingBuffs(whichUnit, arguments, 1).size());
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitHidden",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance());
					if (unit == null) {
						return BooleanJassValue.FALSE;
					}
					if (unit.isRemovedFromGame()) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(unit.isHidden());
				});
		jassProgramVisitor.getJassNativeManager().createNative("PauseUnit",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (unit != null) {
						unit.setPaused(flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitInvulnerable",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (whichUnit != null) {
						whichUnit.setInvulnerable(this.simulation, flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitExploded",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (whichUnit != null) {
						whichUnit.setExplodesOnDeath(flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitAddDefenseBonus",
				(arguments, globalScope, triggerScope) -> {
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final Double defenseAmount = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					if (targetUnit != null) {
						targetUnit.setTemporaryDefenseBonus(
								targetUnit.getTemporaryDefenseBonus() + defenseAmount.floatValue());
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitSetTemporaryDefenseBonus",
				(arguments, globalScope, triggerScope) -> {
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final Double defenseAmount = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					if (targetUnit != null) {
						targetUnit.setTemporaryDefenseBonus(defenseAmount.floatValue());
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitGetTemporaryDefenseBonus",
				(arguments, globalScope, triggerScope) -> {
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return RealJassValue.of(targetUnit.getTemporaryDefenseBonus());
				});
		final JassFunction getUnitArmor = (arguments, globalScope, triggerScope) -> {
			final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
			return RealJassValue.of(targetUnit.getDefense());
		};
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitDefense", getUnitArmor);
		jassProgramVisitor.getJassNativeManager().createNative("BlzGetUnitArmor", getUnitArmor);
		jassProgramVisitor.getJassNativeManager().createNative("HealUnit",
				(arguments, globalScope, triggerScope) -> {
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float amount = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (targetUnit != null) {
						targetUnit.heal(this.simulation, amount);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitAlive",
				(arguments, globalScope, triggerScope) -> {
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (targetUnit != null) {
						return BooleanJassValue.of(!targetUnit.isDead());
					}
					return BooleanJassValue.FALSE;
				});
		jassProgramVisitor.getJassNativeManager().createNative("WidgetAlive",
				(arguments, globalScope, triggerScope) -> {
					final CWidget targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (targetUnit != null) {
						return BooleanJassValue.of(!targetUnit.isDead());
					}
					return BooleanJassValue.FALSE;
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzUnitIsAlive",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of((whichUnit != null) && !whichUnit.isDead());
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzUnitHeal",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float life = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					if ((whichUnit == null) || whichUnit.isDead()) {
						return RealJassValue.ZERO;
					}
					final float before = whichUnit.getLife();
					whichUnit.heal(this.simulation, life);
					return RealJassValue.of(whichUnit.getLife() - before);
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitExplodeOnDeathBuffId",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final int buffId = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					if (whichUnit != null) {
						whichUnit.setExplodesOnDeathBuffId(new War3ID(buffId));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnsetUnitExplodeOnDeathBuffId",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit != null) {
						whichUnit.setExplodesOnDeathBuffId(null);
					}
					return null;
				});
	}

	private void registerCreation() {
		final HandleJassType unitType = type("unit");

		final JassFunction createUnitFxn = (arguments, globalScope, triggerScope) -> {
			final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
			final int rawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
			final double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
			final double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
			final double facing = arguments.get(4).visit(RealJassValueVisitor.getInstance());
			if (player == null) {
				return unitType.getNullValue();
			}
			final War3ID rawcodeId = new War3ID(rawcode);
			final CUnit newUnit = this.simulation.createUnitSimple(rawcodeId, player.getId(),
					(float) x, (float) y, (float) facing);
			CComputerWorkers.onScriptUnitGiven(this.simulation, newUnit);
			return new HandleJassValue(unitType, newUnit);
		};
		jassProgramVisitor.getJassNativeManager().createNative("CreateUnit", createUnitFxn);
		unitType.setConstructorNative(new HandleJassTypeConstructor("CreateUnit"));
		final HandleJassType unitpoolType = type("unitpool");
		jassProgramVisitor.getJassNativeManager().createNative("CreateUnitPool",
				(arguments, globalScope, triggerScope) -> newHandle(unitpoolType, new CUnitPool()));
		jassProgramVisitor.getJassNativeManager().createNative("DestroyUnitPool",
				(arguments, globalScope, triggerScope) -> {
					release(nullable(arguments, 0, ObjectJassValueVisitor.getInstance()));
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitPoolAddUnitType",
				(arguments, globalScope, triggerScope) -> {
					final CUnitPool pool = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (pool != null) {
						pool.add(new War3ID(intArg(arguments, 1)), realArg(arguments, 2));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("UnitPoolRemoveUnitType",
				(arguments, globalScope, triggerScope) -> {
					final CUnitPool pool = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (pool != null) {
						pool.remove(new War3ID(intArg(arguments, 1)));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("PlaceRandomUnit",
				(arguments, globalScope, triggerScope) -> {
					final CUnitPool pool = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CPlayer player = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final float x = realArg(arguments, 2);
					final float y = realArg(arguments, 3);
					final float facing = realArg(arguments, 4);
					if ((pool == null) || (player == null)) {
						return unitType.getNullValue();
					}
					final War3ID picked = pool.pick(this.simulation.getSeededRandom());
					if (picked == null) {
						return unitType.getNullValue();
					}
					final CUnit newUnit = this.simulation.createUnitSimple(picked, player.getId(), x, y, facing);
					CComputerWorkers.onScriptUnitGiven(this.simulation, newUnit);
					return new HandleJassValue(unitType, newUnit);
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateUnitByName",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final String legacyName = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final double facing = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					if (player == null) {
						return unitType.getNullValue();
					}
					final CUnitType unitTypeByJassLegacyName = this.simulation.getUnitData()
							.getUnitTypeByJassLegacyName(legacyName);
					final CUnit newUnit = this.simulation.createUnitSimple(
							unitTypeByJassLegacyName.getTypeId(), player.getId(), (float) x, (float) y,
							(float) facing);
					CComputerWorkers.onScriptUnitGiven(this.simulation, newUnit);
					return new HandleJassValue(unitType, newUnit);
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateUnitAtLoc",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int rawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final AbilityPointTarget whichLocation = nullable(arguments, 2,
							ObjectJassValueVisitor.getInstance());
					final float facing = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (player == null) {
						return unitType.getNullValue();
					}
					final War3ID rawcodeId = new War3ID(rawcode);
					final float x = whichLocation == null ? 0 : (float) whichLocation.x;
					final float y = whichLocation == null ? 0 : (float) whichLocation.y;
					final CUnit newUnit = this.simulation.createUnitSimple(rawcodeId,
							player.getId(), x, y, facing);
					CComputerWorkers.onScriptUnitGiven(this.simulation, newUnit);
					return new HandleJassValue(unitType, newUnit);
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateUnitAtLocByName",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final String legacyName = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final AbilityPointTarget whichLocation = nullable(arguments, 2,
							ObjectJassValueVisitor.getInstance());
					final float facing = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (player == null) {
						return unitType.getNullValue();
					}
					final CUnitType unitTypeByJassLegacyName = this.simulation.getUnitData()
							.getUnitTypeByJassLegacyName(legacyName);
					final float x = whichLocation == null ? 0 : (float) whichLocation.x;
					final float y = whichLocation == null ? 0 : (float) whichLocation.y;
					final CUnit newUnit = this.simulation
							.createUnitSimple(unitTypeByJassLegacyName.getTypeId(), player.getId(), x, y, facing);
					CComputerWorkers.onScriptUnitGiven(this.simulation, newUnit);
					return new HandleJassValue(unitType, newUnit);
				});
		jassProgramVisitor.getJassNativeManager().createNative("CreateCorpse",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int rawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final double facing = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					if (player == null) {
						return unitType.getNullValue();
					}
					final War3ID rawcodeId = new War3ID(rawcode);
					final CUnit newUnit = this.simulation.createUnit(rawcodeId, player.getId(),
							(float) x, (float) y, (float) facing);
					if (newUnit != null) {
						newUnit.becomeCorpse(this.simulation);
					}
					return new HandleJassValue(unitType, newUnit);
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzCreateUnitWithSkin",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					int rawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final double x = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final double y = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final double facing = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					final int skinId = arguments.get(5).visit(IntegerJassValueVisitor.getInstance());
					final War3ID rawcodeId = new War3ID(rawcode);
					final CUnit newUnit = this.simulation.createUnitSimple(rawcodeId,
							player.getId(), (float) x, (float) y, (float) facing);
					if (skinId != rawcode) {
						// throw new IllegalStateException("Our engine does not support UnitSkinID !=
						// UnitID (skinId=" + new War3ID(skinId) + ", unitId=" + new War3ID(rawcode) +
						// ")");
						rawcode = skinId;
					}
					CComputerWorkers.onScriptUnitGiven(this.simulation, newUnit);
					return new HandleJassValue(unitType, newUnit);
				});
		jassProgramVisitor.getJassNativeManager().createNative("KillUnit",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit != null) {
						whichUnit.kill(this.simulation);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveUnit",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit != null) {
						// A channel in progress ends as interrupted, as on death, so what it made
						// (locusts, a blizzard's waves) does not outlive the removed caster.
						if (!whichUnit.isDead()) {
							whichUnit.beginBehavior(this.simulation, null, true);
						}
						this.simulation.removeUnit(whichUnit);
						meleeUI.removedUnit(whichUnit);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ShowUnit",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean show = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (whichUnit != null) {
						whichUnit.setHidden(!show);
						if (!show) {
							this.simulation.unitHiddenByScript(whichUnit);
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ResurrectUnit",
				(arguments, globalScope, triggerScope) -> {
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if ((targetUnit != null) && targetUnit.isDead()) {
						targetUnit.resurrect(this.simulation);
					}
					return null;
				});
	}

	private void registerPositionAndMovement() {
		final HandleJassType unitType = type("unit");
		final HandleJassType destructableType = type("destructable");
		final HandleJassType locationType = type("location");

		jassProgramVisitor.getJassNativeManager().createNative("SetUnitX",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float positionX = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (whichUnit != null) {
						whichUnit.setRegionEventsDeferred(true);
						whichUnit.setX(positionX, this.simulation.getWorldCollision(),
								this.simulation.getRegionManager());
						whichUnit.setRegionEventsDeferred(false);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitY",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final float positionX = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (whichUnit != null) {
						whichUnit.setRegionEventsDeferred(true);
						whichUnit.setY(positionX, this.simulation.getWorldCollision(),
								this.simulation.getRegionManager());
						whichUnit.setRegionEventsDeferred(false);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitPosition",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final double positionX = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					final double positionY = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					if (whichUnit != null) {
						whichUnit.setRegionEventsDeferred(true);
						whichUnit.setPointAndCheckUnstuck((float) positionX, (float) positionY,
								this.simulation);
						whichUnit.setRegionEventsDeferred(false);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitPositionLoc",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final AbilityPointTarget positionLoc = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					if (whichUnit != null) {
						whichUnit.setRegionEventsDeferred(true);
						whichUnit.setPointAndCheckUnstuck(positionLoc.x, positionLoc.y,
								this.simulation);
						whichUnit.setRegionEventsDeferred(false);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitFacing",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final double facing = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					if (whichUnit != null) {
						// Script timers run before the units in a step: the drawn turn ticks that already passed must
						// still follow the old heading.
						this.simulation.setUnitFacingTimed(whichUnit, (float) facing, 0);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitFacingTimed",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final double facing = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					final double duration = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					if (whichUnit != null) {
						this.simulation.setUnitFacingTimed(whichUnit, (float) facing, (float) duration);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitMoveSpeed",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final double newSpeed = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					if (whichUnit != null) {
						whichUnit.setSpeed((int) newSpeed);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitFlyHeight",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final float newHeight = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float rate = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (whichUnit != null) {
						this.simulation.setUnitFlyHeight(whichUnit, newHeight, rate);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitTurnSpeed",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final float newSpeed = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (whichUnit != null) {
						whichUnit.setTurnRate(newSpeed);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitPropWindow",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final float newAngle = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					if (whichUnit != null) {
						whichUnit.setPropWindow(newAngle);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitAcquireRange",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final double range = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					// The original game ignores a range of 50 or less (measured); the unit keeps the range it had.
					if ((unit != null) && (range > 50)) {
						unit.setAcquisitionRange((float) range);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitAcquireRange",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (unit != null) {
						return RealJassValue.of(unit.getAcquisitionRange());
					}
					return RealJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitTurnSpeed",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (unit != null) {
						return RealJassValue.of(unit.getTurnRate());
					}
					return RealJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitPropWindow",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (unit != null) {
						return RealJassValue.of(unit.getPropWindow());
					}
					return RealJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitFlyHeight",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					if (unit != null) {
						final CBuffEnsnare snare = CBuffEnsnare.getFlyingSnare(unit);
						if (snare != null) {
							return RealJassValue.of(snare.getScriptFlyHeight());
						}
						// Measured on 1.29: a script reads the target of SetUnitFlyHeight at once, while the unit
						// still rises or sinks to it at the rate.
						final CTimerFlyHeight change = this.simulation.getFlyHeightChange(unit);
						return RealJassValue.of(change != null ? change.getTargetHeight() : unit.getFlyHeight());
					}
					return RealJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitDefaultAcquireRange",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (unit != null) {
						return RealJassValue.of(unit.getUnitType().getDefaultAcquisitionRange());
					}
					return RealJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitDefaultTurnSpeed",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (unit != null) {
						return RealJassValue.of(unit.getUnitType().getTurnRate());
					}
					return RealJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitDefaultPropWindow",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (unit != null) {
						return RealJassValue.of(unit.getUnitType().getPropWindow());
					}
					return RealJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitDefaultFlyHeight",
				(arguments, globalScope, triggerScope) -> {
					final CUnit unit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (unit != null) {
						return RealJassValue.of(unit.getUnitType().getDefaultFlyingHeight());
					}
					return RealJassValue.ZERO;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitFacing",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					return RealJassValue.of(whichUnit != null ? whichUnit.getDrawnFacing(simulation) : 0.0);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitMoveSpeed",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					// A unit type without a move speed, such as a Farm, reads 0; a rooted Ancient keeps its own speed.
					return RealJassValue.of((whichUnit != null)
							&& ((whichUnit.getMoveBehavior() != null) || (whichUnit.getUnitType().getSpeed() > 0))
									? whichUnit.getSpeed()
									: 0.0);
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitInRange",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final CUnit otherUnit = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final Double distance = arguments.get(2).visit(RealJassValueVisitor.getInstance());

					// Measured on 1.29: the collision sizes of both units count, not only the first one's.
					if ((whichUnit != null) && (otherUnit != null) && (distance != null)
							&& ((whichUnit.distance(otherUnit.getX(), otherUnit.getY())
									- otherUnit.getUnitType().getCollisionSize()) <= distance)) {
						return new BooleanJassValue(true);
					}

					return JassType.BOOLEAN.getNullValue();
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitInRangeXY",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final Double x = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					final Double y = arguments.get(2).visit(RealJassValueVisitor.getInstance());
					final Double distance = arguments.get(3).visit(RealJassValueVisitor.getInstance());

					if ((whichUnit != null) && (x != null) && (y != null) && (distance != null)
							&& (whichUnit.distance(x, y) <= distance)) {
						return new BooleanJassValue(true);
					}

					return JassType.BOOLEAN.getNullValue();
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitInRangeLoc",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final AbilityPointTarget whichLocation = nullable(arguments, 1,
							ObjectJassValueVisitor.getInstance());
					final Double distance = arguments.get(2).visit(RealJassValueVisitor.getInstance());

					if ((whichUnit != null) && (whichLocation != null) && (distance != null)
							&& (whichUnit.distance(whichLocation.x, whichLocation.y) <= distance)) {
						return new BooleanJassValue(true);
					}

					return JassType.BOOLEAN.getNullValue();
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetWidgetX",
				(arguments, globalScope, triggerScope) -> {
					final CWidget whichWidget = inGame(nullable(arguments, 0, ObjectJassValueVisitor.getInstance()));
					return RealJassValue.of(whichWidget == null ? 0 : whichWidget.getX());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetWidgetY",
				(arguments, globalScope, triggerScope) -> {
					final CWidget whichWidget = inGame(nullable(arguments, 0, ObjectJassValueVisitor.getInstance()));
					return RealJassValue.of(whichWidget == null ? 0 : whichWidget.getY());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitX",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichWidget = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					return RealJassValue.of(whichWidget == null ? 0 : whichWidget.getX());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitY",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichWidget = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					return RealJassValue.of(whichWidget == null ? 0 : whichWidget.getY());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitLoc",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichWidget = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					if (whichWidget == null) {
						return new HandleJassValue(locationType,
								new LocationJass(0, 0, this.simulation.getHandleIdAllocator().createHandleId()));
					}
					return new HandleJassValue(locationType, new LocationJass(whichWidget.getX(),
							whichWidget.getY(), this.simulation.getHandleIdAllocator().createHandleId()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("BlzSetUnitFacingEx",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final double facing = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					if (whichUnit == null) {
						return null;
					}
					whichUnit.setFacingAndDrawnFacing((float) facing);
					final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
					if (renderPeer != null) {
						renderPeer.setFacing((float) facing);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveGuardPosition",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit != null) {
						whichUnit.removeGuardPosition();
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("RecycleGuardPosition",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (whichUnit != null) {
						whichUnit.setGuardPosition(whichUnit.getX(), whichUnit.getY());
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitCreepGuard",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean creepGuard = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (whichUnit != null) {
						if (creepGuard) {
							whichUnit.setGuardPosition(whichUnit.getX(), whichUnit.getY());
						}
						else {
							whichUnit.removeGuardPosition();
						}
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitRallyPoint",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					float x, y;
					if (whichUnit != null) {
						final AbilityTarget rallyPoint = whichUnit.getRallyPoint();
						if (rallyPoint != null) {
							x = rallyPoint.getX();
							y = rallyPoint.getY();
						}
						else {
							x = y = 0;
						}
					}
					else {
						x = y = 0;
					}
					return new HandleJassValue(locationType,
							new LocationJass(x, y, this.simulation.getHandleIdAllocator().createHandleId()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitRallyUnit",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					HandleJassValue rallyUnit;
					if (whichUnit != null) {
						final AbilityTarget rallyPoint = whichUnit.getRallyPoint();
						if (rallyPoint != null) {
							rallyUnit = rallyPoint.visit(new AbilityTargetVisitor<HandleJassValue>() {
								@Override
								public HandleJassValue accept(final AbilityPointTarget target) {
									return unitType.getNullValue();
								}

								@Override
								public HandleJassValue accept(final CUnit target) {
									return new HandleJassValue(unitType, target);
								}

								@Override
								public HandleJassValue accept(final CDestructable target) {
									return unitType.getNullValue();
								}

								@Override
								public HandleJassValue accept(final CItem target) {
									return unitType.getNullValue();
								}
							});
						}
						else {
							rallyUnit = unitType.getNullValue();
						}
					}
					else {
						rallyUnit = unitType.getNullValue();
					}
					return rallyUnit;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitRallyDestructable",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					HandleJassValue rallyDest;
					if (whichUnit != null) {
						final AbilityTarget rallyPoint = whichUnit.getRallyPoint();
						if (rallyPoint != null) {
							rallyDest = rallyPoint.visit(new AbilityTargetVisitor<HandleJassValue>() {
								@Override
								public HandleJassValue accept(final AbilityPointTarget target) {
									return destructableType.getNullValue();
								}

								@Override
								public HandleJassValue accept(final CUnit target) {
									return destructableType.getNullValue();
								}

								@Override
								public HandleJassValue accept(final CDestructable target) {
									return new HandleJassValue(destructableType, target);
								}

								@Override
								public HandleJassValue accept(final CItem target) {
									return destructableType.getNullValue();
								}
							});
						}
						else {
							rallyDest = destructableType.getNullValue();
						}
					}
					else {
						rallyDest = destructableType.getNullValue();
					}
					return rallyDest;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitCurrentOrder",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = inGame(nullable(arguments, 0, ObjectJassValueVisitor.<CUnit>getInstance()));
					int orderId;
					if ((whichUnit != null) && (whichUnit.isPaused() || whichUnit.isStunnedBySpell())) {
						// 1.29: a paused or stunned unit reads
						// stunned.
						orderId = OrderIds.stunned;
					}
					else if ((whichUnit != null) && whichUnit.isWalkingToPost()) {
												orderId = OrderIds.move;
					}
					else if ((whichUnit != null) && (whichUnit.getCurrentOrder() != null)) {
						orderId = whichUnit.getCurrentOrder().getOrderId();
						// 1.29: hold position reads 0, also while it attacks; patrol
						// reads 851991; a Peasant raising its building reads repair.
						if (orderId == OrderIds.holdposition) {
							orderId = 0;
						}
						else if (orderId == OrderIds.patrol) {
							orderId = OrderIds._instant1;
						}
						else {
							final CBehavior behavior = whichUnit.getCurrentBehavior();
							if ((behavior != null) && (behavior.getHighlightOrderId() == OrderIds.repair)) {
								orderId = OrderIds.repair;
							}
							// Measured on 1.29: a unit given smart on its transport reads board.
							else if ((behavior != null) && (orderId == OrderIds.smart)
									&& (behavior.getHighlightOrderId() == OrderIds.board)) {
								orderId = OrderIds.board;
							}
							// Measured on 1.29: a harvesting worker carrying its load back reads resumeharvesting.
							else if ((behavior != null) && (orderId == OrderIds.harvest)
									&& (behavior.getHighlightOrderId() == OrderIds.returnresources)) {
								orderId = OrderIds.resumeharvesting;
							}
						}
					}
					else {
						orderId = 0;
					}
					return IntegerJassValue.of(orderId);
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitMovementTypeNoCollision",
				(arguments, globalScope, triggerScope) -> {
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean flag = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (targetUnit != null) {
						targetUnit.setNoCollisionMovementType(flag);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsUnitMovementDisabled",
				(arguments, globalScope, triggerScope) -> {
					final CUnit targetUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if (targetUnit != null) {
						return BooleanJassValue.of(targetUnit.isMovementDisabled());
					}
					return BooleanJassValue.FALSE;
				});
	}

	private void registerAppearance() {
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitColor",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final Object whichPlayerColor = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
					if ((renderPeer != null) && (whichPlayerColor instanceof CPlayerColor)) {
						renderPeer.setPlayerColor(((CPlayerColor) whichPlayerColor).ordinal());
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitScale",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final float scaleX = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float scaleY = arguments.get(2).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float scaleZ = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
					if (renderPeer != null) {
						renderPeer.instance.setUniformScale(scaleX);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitTimeScale",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final float timeScale = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
					if (renderPeer != null) {
						renderPeer.instance.setAnimationSpeed(timeScale);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitBlendTime",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final float blendTime = arguments.get(1).visit(RealJassValueVisitor.getInstance()).floatValue();
					final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
					if (renderPeer != null) {
						renderPeer.instance.setBlendTime(blendTime);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitVertexColor",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int red = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final int green = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
					final int blue = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
					final int alpha = arguments.get(4).visit(IntegerJassValueVisitor.getInstance());
					if (whichUnit != null) {
						war3MapViewer.simulation.changeUnitVertexColor(whichUnit, red / 255f, green / 255f,
								blue / 255f, alpha / 255f);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("QueueUnitAnimation",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final String whichAnimation = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
					if (renderPeer != null) {
						renderPeer.queueAnimation(whichAnimation);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitAnimation",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final String whichAnimation = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
					if (renderPeer != null) {
						renderPeer.playAnimation(whichAnimation);
					}
					if (whichUnit != null) {
						whichUnit.holdScriptAnimation();
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ResetUnitAnimation",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final RenderUnit renderPeer = whichUnit == null ? null : war3MapViewer.getRenderPeer(whichUnit);
					if (renderPeer != null) {
						renderPeer.resetAnimation();
					}
					if (whichUnit != null) {
						whichUnit.releaseScriptAnimation();
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitAnimationByIndex",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final int whichAnimation = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
					final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
					if (renderPeer != null) {
						renderPeer.playAnimation(whichAnimation);
					}
					if (whichUnit != null) {
						whichUnit.holdScriptAnimation();
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitAnimationWithRarity",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final String whichAnimation = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final CRarityControl rarityControl = nullable(arguments, 2,
							ObjectJassValueVisitor.getInstance());
					final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
					if (renderPeer != null) {
						renderPeer.playAnimationWithRarity(whichAnimation, rarityControl);
					}
					if (whichUnit != null) {
						whichUnit.holdScriptAnimation();
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddUnitAnimationProperties",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final String animProperties = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final boolean add = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
					if (renderPeer != null) {
						renderPeer.addAnimationProperties(animProperties, add);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitLookAt",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final String boneNameString = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final CUnit lookAtTarget = arguments.get(2).visit(ObjectJassValueVisitor.getInstance());
					final float offsetX = arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float offsetY = arguments.get(4).visit(RealJassValueVisitor.getInstance()).floatValue();
					final float offsetZ = arguments.get(5).visit(RealJassValueVisitor.getInstance()).floatValue();
					final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
					final RenderUnit renderPeerTarget = war3MapViewer.getRenderPeer(lookAtTarget);
					if ((renderPeer != null) && (renderPeerTarget != null)) {
						renderPeer.lockTargetFacing(boneNameString, renderPeerTarget, offsetX, offsetY, offsetZ);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ResetUnitLookAt",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final RenderUnit renderPeer = war3MapViewer.getRenderPeer(whichUnit);
					if (renderPeer != null) {
						renderPeer.resetLookAt();
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitAnimationByTag",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean force = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final PrimaryTag animationName = nullable(arguments, 2, ObjectJassValueVisitor.getInstance());
					final EnumSetHandle<SecondaryTag> secondaryAnimationTags = nullable(arguments, 3,
							ObjectJassValueVisitor.getInstance());
					final float speedRatio = arguments.get(4).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					final boolean allowRarityVariations = arguments.get(5)
							.visit(BooleanJassValueVisitor.getInstance());
					whichUnit.getUnitAnimationListener().playAnimation(force, animationName,
							secondaryAnimationTags.getEnumSet(), speedRatio, allowRarityVariations);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitAnimationByTagWithDuration",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean force = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final PrimaryTag animationName = nullable(arguments, 2, ObjectJassValueVisitor.getInstance());
					final EnumSetHandle<SecondaryTag> secondaryAnimationTags = nullable(arguments, 3,
							ObjectJassValueVisitor.getInstance());
					final float duration = arguments.get(4).visit(RealJassValueVisitor.getInstance()).floatValue();
					final boolean allowRarityVariations = arguments.get(5)
							.visit(BooleanJassValueVisitor.getInstance());
					whichUnit.getUnitAnimationListener().playAnimationWithDuration(force, animationName,
							secondaryAnimationTags.getEnumSet(), duration, allowRarityVariations);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("SetUnitAnimationToWalk",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean force = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					final float currentMovementSpeed = arguments.get(2).visit(RealJassValueVisitor.getInstance())
							.floatValue();
					final boolean allowRarityVariations = arguments.get(3)
							.visit(BooleanJassValueVisitor.getInstance());
					whichUnit.getUnitAnimationListener().playWalkAnimation(force, currentMovementSpeed,
							allowRarityVariations);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("QueueUnitAnimationByTag",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final PrimaryTag animationName = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final EnumSetHandle<SecondaryTag> secondaryAnimationTags = nullable(arguments, 2,
							ObjectJassValueVisitor.getInstance());
					final boolean allowRarityVariations = arguments.get(3)
							.visit(BooleanJassValueVisitor.getInstance());
					whichUnit.getUnitAnimationListener().queueAnimation(animationName,
							secondaryAnimationTags.getEnumSet(), allowRarityVariations);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("AddUnitAnimationSecondaryTag",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final SecondaryTag whichTag = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(whichUnit.getUnitAnimationListener().addSecondaryTag(whichTag));
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveUnitAnimationSecondaryTag",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final SecondaryTag whichTag = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					return BooleanJassValue.of(whichUnit.getUnitAnimationListener().removeSecondaryTag(whichTag));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitCastPoint",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return RealJassValue.of(whichUnit.getUnitType().getCastPoint());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitCastBackswingPoint",
				(arguments, globalScope, triggerScope) -> {
					final CUnit whichUnit = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					return RealJassValue.of(whichUnit.getUnitType().getCastBackswingPoint());
				});
	}

	private static List<CBuff> matchingBuffs(final CUnit unit, final List<JassValue> arguments, final int first) {
		final boolean[] flags = new boolean[7];
		for (int i = 0; i < flags.length; i++) {
			flags[i] = arguments.get(first + i).visit(BooleanJassValueVisitor.getInstance());
		}
		return matchingBuffs(unit, flags[0], flags[1], flags[2], flags[3], flags[4], flags[5], flags[6]);
	}

	/**
	 * Buffs flagged neither magic nor physical are matched by either flag, since most engine buffs leave both
	 * unset.
	 */
	private static List<CBuff> matchingBuffs(final CUnit unit, final boolean positive, final boolean negative,
			final boolean magic, final boolean physical, final boolean timedLife, final boolean aura,
			final boolean autoDispel) {
		final List<CBuff> buffs = new ArrayList<>();
		if (unit == null) {
			return buffs;
		}
		for (final CAbility ability : unit.getAbilities()) {
			if (!(ability instanceof CBuff)) {
				continue;
			}
			final CBuff buff = (CBuff) ability;
			final boolean polarityOk = buff.isPositive() ? positive : negative;
			final boolean typeOk = (!buff.isMagic() && !buff.isPhysical()) ? (magic || physical)
					: ((magic && buff.isMagic()) || (physical && buff.isPhysical()));
			if (polarityOk && typeOk && (timedLife || !buff.isTimedLifeBar()) && (aura || !buff.isAura())
					&& (!autoDispel || buff.isDispellable())) {
				buffs.add(buff);
			}
		}
		return buffs;
	}
}
