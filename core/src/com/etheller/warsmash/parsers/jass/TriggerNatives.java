package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.RemovableTriggerEvent;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.interpreter.ast.scope.trigger.TriggerBooleanExpression;
import com.etheller.interpreter.ast.scope.variableevent.CLimitOp;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.CodeJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.parsers.jass.triggers.BoolExprAnd;
import com.etheller.warsmash.parsers.jass.triggers.BoolExprCondition;
import com.etheller.warsmash.parsers.jass.triggers.BoolExprNot;
import com.etheller.warsmash.parsers.jass.triggers.BoolExprOr;
import com.etheller.warsmash.parsers.jass.triggers.LocationJass;
import com.etheller.warsmash.parsers.jass.triggers.TriggerAction;
import com.etheller.warsmash.parsers.jass.triggers.TriggerCondition;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedFormerly;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.HandleIdAllocator;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CTrackable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegion;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegionTriggerEnter;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.region.CRegionTriggerLeave;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSound;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.state.CGameState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.state.CUnitState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerExpireTriggerEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerJassBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerNativeEvent;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog.CScriptDialogButton;

/**
 * Trigger natives: event registration, evaluation, waits and event responses.
 */
final class TriggerNatives extends CommonNatives {
	TriggerNatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		registerEvents();
		registerEventResponses();
	}

	private void registerEvents() {
		final HandleJassType eventType = type("event");
		final HandleJassType triggerType = type("trigger");
		final HandleJassType triggerconditionType = type("triggercondition");
		final HandleJassType triggeractionType = type("triggeraction");
		final HandleJassType boolexprType = type("boolexpr");
		final HandleJassType conditionfuncType = type("conditionfunc");
		final HandleJassType filterfuncType = type("filterfunc");
		final HandleJassType eventidType = type("eventid");

		// ============================================================================
		// Native trigger interface
		//
		setupTriggerAPI(jassProgramVisitor, triggerType, triggeractionType, triggerconditionType, boolexprType,
				conditionfuncType, filterfuncType, eventidType, this.simulation.getHandleIdAllocator());
		// ============================================================================
		// Trigger Game Event API
		// ============================================================================
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterVariableEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					final String varName = arguments.get(1).visit(StringJassValueVisitor.getInstance());
					final CLimitOp limitOp = arguments.get(2).visit(ObjectJassValueVisitor.<CLimitOp>getInstance());
					final Double limitval = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					final RemovableTriggerEvent event = globalScope.registerVariableEvent(trigger, varName, limitOp,
							limitval.doubleValue());
					return newEvent(eventType, event);
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterTimerEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					final Double timeout = arguments.get(1).visit(RealJassValueVisitor.getInstance());
					final Boolean periodic = arguments.get(2).visit(BooleanJassValueVisitor.getInstance());
					final CTimerNativeEvent timer = new CTimerNativeEvent(globalScope, trigger);
					timer.setRepeats(periodic.booleanValue());
					timer.setTimeoutTime(timeout.floatValue());
					timer.start(this.simulation);
					return newEvent(eventType, new TimerEventRegistration(this.environment, trigger, timer));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterTimerExpireEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CTimerJassBase timer = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					timer.addEvent(trigger);
					return newEvent(eventType, new CTimerExpireTriggerEvent(trigger, timer));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterGameStateEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					final CGameState whichState = arguments.get(1)
							.visit(ObjectJassValueVisitor.<CGameState>getInstance());
					final CLimitOp opcode = arguments.get(2).visit(ObjectJassValueVisitor.<CLimitOp>getInstance());
					final Double limitval = arguments.get(3).visit(RealJassValueVisitor.getInstance());
					if ((trigger == null) || (whichState == null) || (opcode == null)) {
						return newEvent(eventType, null);
					}
					return newEvent(eventType, this.simulation.registerGameStateEvent(globalScope, trigger, whichState,
							opcode, limitval.doubleValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterUnitStateEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CUnit whichUnit = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final Object whichState = nullable(arguments, 2, ObjectJassValueVisitor.getInstance());
					final Object opcode = nullable(arguments, 3, ObjectJassValueVisitor.getInstance());
					final Double limitval = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					// Measured: a null state or limitop counts as id 0 (life, LESS_THAN); an unknown state gives
					// no event, an unknown limitop an event that never fires.
					final CUnitState state = whichState == null ? CUnitState.LIFE
							: whichState instanceof CUnitState ? (CUnitState) whichState : null;
					if ((whichUnit == null) || (state == null)) {
						return eventType.getNullValue();
					}
					final CLimitOp limitOp = opcode == null ? CLimitOp.LESS_THAN
							: opcode instanceof CLimitOp ? (CLimitOp) opcode : null;
					return newEvent(eventType, whichUnit.addStateEvent(globalScope, whichTrigger, state,
							limitOp, limitval.doubleValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterDialogEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CScriptDialog dialog = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if (dialog == null) {
						return eventType.getNullValue();
					}
					return newEvent(eventType, dialog.addEvent(trigger));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterDialogButtonEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CScriptDialogButton dialogButton = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					if (dialogButton == null) {
						return eventType.getNullValue();
					}
					return newEvent(eventType, dialogButton.addEvent(trigger));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterGameEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					final JassGameEventsWar3 gameEvent = arguments.get(1)
							.visit(ObjectJassValueVisitor.getInstance());
					return newEvent(eventType,
							this.simulation.registerGameEvent(globalScope, trigger, gameEvent));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterEnterRegion",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					final CRegion region = arguments.get(1).visit(ObjectJassValueVisitor.<CRegion>getInstance());
					final TriggerBooleanExpression boolexpr = nullable(arguments, 2,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					return newEvent(eventType,
							region.add(new CRegionTriggerEnter(globalScope, trigger, boolexpr)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterLeaveRegion",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					final CRegion region = arguments.get(1).visit(ObjectJassValueVisitor.<CRegion>getInstance());
					final TriggerBooleanExpression boolexpr = nullable(arguments, 2,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					return newEvent(eventType,
							region.add(new CRegionTriggerLeave(globalScope, trigger, boolexpr)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterTrackableHitEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					final CTrackable trackable = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					if ((trigger == null) || (trackable == null)) {
						return eventType.getNullValue();
					}
					return newEvent(eventType, trackable.addHitEvent(trigger));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterTrackableTrackEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					final CTrackable trackable = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					if ((trigger == null) || (trackable == null)) {
						return eventType.getNullValue();
					}
					return newEvent(eventType, trackable.addTrackEvent(trigger));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterPlayerEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CPlayerJass whichPlayer = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					if (whichPlayer == null) {
						return eventType.getNullValue();
					}
					final JassGameEventsWar3 whichPlayerEvent = arguments.get(2)
							.visit(ObjectJassValueVisitor.getInstance());
					return newEvent(eventType,
							whichPlayer.addEvent(globalScope, whichTrigger, whichPlayerEvent));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterPlayerStateEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CPlayerJass whichPlayer = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final CPlayerState whichState = arguments.get(2).visit(ObjectJassValueVisitor.getInstance());
					final CLimitOp opcode = arguments.get(3).visit(ObjectJassValueVisitor.getInstance());
					final Double limitval = arguments.get(4).visit(RealJassValueVisitor.getInstance());
					if (!(whichPlayer instanceof CPlayer) || (whichState == null) || (opcode == null)) {
						return eventType.getNullValue();
					}
					return newEvent(eventType, ((CPlayer) whichPlayer).addStateEvent(globalScope,
							whichTrigger, whichState, opcode, limitval.doubleValue()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterPlayerAllianceChange",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CPlayerJass whichPlayer = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final CAllianceType whichAlliance = arguments.get(2).visit(ObjectJassValueVisitor.getInstance());
					if (!(whichPlayer instanceof CPlayer) || (whichAlliance == null)) {
						return eventType.getNullValue();
					}
					return newEvent(eventType,
							((CPlayer) whichPlayer).addAllianceEvent(globalScope, whichTrigger, whichAlliance));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterPlayerUnitEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CPlayer whichPlayer = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					final JassGameEventsWar3 whichPlayerEvent = arguments.get(2)
							.visit(ObjectJassValueVisitor.getInstance());
					final TriggerBooleanExpression filter = nullable(arguments, 3,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					if (whichPlayer == null) {
						return eventType.getNullValue();
					}
					return newEvent(eventType,
							whichPlayer.addUnitEvent(globalScope, whichTrigger, whichPlayerEvent, filter));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerEvaluate",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichTrigger == null) {
						return BooleanJassValue.FALSE;
					}
					return BooleanJassValue.of(whichTrigger.evaluate(globalScope,
							new CommonTriggerExecutionScope(whichTrigger, triggerScope)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerExecute",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichTrigger != null) {
						whichTrigger.execute(globalScope,
								new CommonTriggerExecutionScope(whichTrigger, triggerScope));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerExecuteWait",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					if (whichTrigger != null) {
						// The original returns to the caller as soon as the trigger sleeps, like TriggerExecute.
						whichTrigger.execute(globalScope, new CommonTriggerExecutionScope(whichTrigger, triggerScope));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterDeathEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CWidget whichWidget = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					return newEvent(eventType, whichWidget.addDeathEvent(globalScope, whichTrigger));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterUnitEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CUnit whichWidget = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					final JassGameEventsWar3 whichPlayerEvent = arguments.get(2)
							.visit(ObjectJassValueVisitor.getInstance());
					if (whichWidget == null) {
						return newEvent(eventType, RemovableTriggerEvent.doNothing(whichTrigger));
					}
					return newEvent(eventType,
							whichWidget.addEvent(globalScope, whichTrigger, whichPlayerEvent));
				});
		// Measured on 1.29: it returns no event and registers nothing, the trigger never runs.
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterFilterUnitEvent",
				(arguments, globalScope, triggerScope) -> eventType.getNullValue());
		jassProgramVisitor.getJassNativeManager().createNative("TriggerSleepAction",
				(arguments, globalScope, triggerScope) -> {
					final Trigger triggeringTrigger = triggerScope.getTriggeringTrigger();
					if ((triggeringTrigger == null) || triggeringTrigger.isWaitOnSleeps()) {
						final Double seconds = arguments.get(0).visit(RealJassValueVisitor.getInstance());
						scriptWait(this.simulation, globalScope, seconds.floatValue());
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerWaitForSound",
				(arguments, globalScope, triggerScope) -> {
					final Trigger triggeringTrigger = triggerScope.getTriggeringTrigger();
					if ((triggeringTrigger == null) || triggeringTrigger.isWaitOnSleeps()) {
						final CSound sound = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
						final float offset = arguments.get(1).visit(RealJassValueVisitor.getInstance())
								.floatValue();
						float seconds = 0;
						if (sound != null) {
							seconds = sound.getRemainingTime(this.simulation.getGameTurnTick()) - offset;
						}
						scriptWait(this.simulation, globalScope, seconds);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterOnTick",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					this.simulation.registerOnTickEvent(trigger);
					return newEvent(eventType, new TickEventRegistration(this.environment, trigger));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRegisterOnUnitTick",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.getInstance());
					final CUnit unit = arguments.get(1).visit(ObjectJassValueVisitor.getInstance());
					unit.registerOnTickEvent(trigger);
					return newEvent(eventType, new UnitTickEventRegistration(trigger, unit));
				});
		jassProgramVisitor.getJassNativeManager().createNative("RemoveTriggerEvent",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final RemovableTriggerEvent evt = nullable(arguments, 1, ObjectJassValueVisitor.getInstance());
					if (whichTrigger != null) {
						whichTrigger.removeEvent(evt);
					}
					return null;
				});
	}

	private void registerEventResponses() {
		final HandleJassType playerType = type("player");
		final HandleJassType widgetType = type("widget");
		final HandleJassType unitType = type("unit");
		final HandleJassType destructableType = type("destructable");
		final HandleJassType itemType = type("item");
		final HandleJassType locationType = type("location");
		final HandleJassType regionType = type("region");
		final HandleJassType unitstateType = type("unitstate");
		final HandleJassType gamestateType = type("gamestate");
		final HandleJassType orderbuttontypeType = type("orderbuttontype");

		jassProgramVisitor.getJassNativeManager().createNative("GetFilterUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getFilterUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetEnumUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getEnumUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetFilterDestructable",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(destructableType,
							((CommonTriggerExecutionScope) triggerScope).getFilterDestructable());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetEnumDestructable",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(destructableType,
							((CommonTriggerExecutionScope) triggerScope).getEnumDestructable());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetFilterItem",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(itemType,
							((CommonTriggerExecutionScope) triggerScope).getFilterItem());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetEnumItem",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(itemType,
							((CommonTriggerExecutionScope) triggerScope).getEnumItem());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetEventUnitState",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitstateType,
							((CommonTriggerExecutionScope) triggerScope).getEventUnitState());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetEventPlayerState",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(type("playerstate"), triggerScope instanceof CommonTriggerExecutionScope
							? ((CommonTriggerExecutionScope) triggerScope).getEventPlayerState() : null);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetEventGameState",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(gamestateType,
							((CommonTriggerExecutionScope) triggerScope).getEventGameState());
				});
		// Measured on 1.29: EVENT_GAME_VICTORY can be registered, but neither a player removed with a victory nor
		// anything else in a single player game fires it, so there is never a winning player.
		jassProgramVisitor.getJassNativeManager().createNative("GetWinningPlayer",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(playerType, null);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTriggeringRegion",
				(arguments, globalScope, triggerScope) -> {
					return newHandle(regionType,
							((CommonTriggerExecutionScope) triggerScope).getTriggeringRegion());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetEnteringUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getEnteringUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetLeavingUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getLeavingUnit());
				});
		final HandleJassType trackableType = type("trackable");
		jassProgramVisitor.getJassNativeManager().createNative("GetTriggeringTrackable",
				(arguments, globalScope, triggerScope) -> new HandleJassValue(trackableType,
						((CommonTriggerExecutionScope) triggerScope).getTriggeringTrackable()));
		jassProgramVisitor.getJassNativeManager().createNative("GetChangingUnit",
				(arguments, globalScope, triggerScope) -> new HandleJassValue(unitType,
						((CommonTriggerExecutionScope) triggerScope).getChangingUnit()));
		jassProgramVisitor.getJassNativeManager().createNative("GetChangingUnitPrevOwner",
				(arguments, globalScope, triggerScope) -> new HandleJassValue(playerType,
						((CommonTriggerExecutionScope) triggerScope).getChangingUnitPrevOwner()));
		jassProgramVisitor.getJassNativeManager().createNative("GetTriggerWidget",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(widgetType,
							((CommonTriggerExecutionScope) triggerScope).getTriggerWidget());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetDyingUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getDyingUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetAttacker",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getAttacker());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetKillingUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getKillingUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetEventDamageSource",
				(arguments, globalScope, triggerScope) -> {
					final CUnit damageSource = ((CommonTriggerExecutionScope) triggerScope).getDamageSource();
					// Damage from a missile whose attacker was removed while it flew has no source.
					return new HandleJassValue(unitType,
							(damageSource != null) && damageSource.isRemovedFromGame() ? null : damageSource);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetEventDamage",
				(arguments, globalScope, triggerScope) -> {
					return RealJassValue.of(((CommonTriggerExecutionScope) triggerScope).getDamageTaken());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetCancelledStructure",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getCancelledStructure());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetConstructingStructure",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getConstructingStructure());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTriggerUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getTriggeringUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTriggerPlayer",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(playerType,
							((CommonTriggerExecutionScope) triggerScope).getTriggeringPlayer());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTriggerDestructable",
				(arguments, globalScope, triggerScope) -> {
					final CWidget triggerWidget = ((CommonTriggerExecutionScope) triggerScope).getTriggerWidget();
					if (triggerWidget instanceof CDestructable) {
						return new HandleJassValue(destructableType, triggerWidget);
					}
					else {
						return new HandleJassValue(destructableType, null);
					}
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderedUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getOrderedUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetManipulatedItem",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(itemType,
							((CommonTriggerExecutionScope) triggerScope).getManipulatedItem());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetManipulatingUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getManipulatingUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetIssuedOrderId",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(((CommonTriggerExecutionScope) triggerScope).getIssuedOrderId());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderPointX",
				(arguments, globalScope, triggerScope) -> {
					return RealJassValue.of(((CommonTriggerExecutionScope) triggerScope).getOrderPointX());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderPointY",
				(arguments, globalScope, triggerScope) -> {
					return RealJassValue.of(((CommonTriggerExecutionScope) triggerScope).getOrderPointY());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderPointLoc",
				(arguments, globalScope, triggerScope) -> {
					final CommonTriggerExecutionScope commonTriggerExecutionScope = (CommonTriggerExecutionScope) triggerScope;
					final LocationJass jassLocation = new LocationJass(commonTriggerExecutionScope.getOrderPointX(),
							commonTriggerExecutionScope.getOrderPointY(),
							this.simulation.getHandleIdAllocator().createHandleId());
					return new HandleJassValue(locationType, jassLocation);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderTarget",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(widgetType,
							((CommonTriggerExecutionScope) triggerScope).getOrderTarget());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderTargetDestructable",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(destructableType,
							((CommonTriggerExecutionScope) triggerScope).getOrderTargetDestructable());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderTargetItem",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(itemType,
							((CommonTriggerExecutionScope) triggerScope).getOrderTargetItem());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetOrderTargetUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getOrderTargetUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetEventTargetUnit",
				(arguments, globalScope, triggerScope) -> {
					if (!(triggerScope instanceof CommonTriggerExecutionScope)) {
						return unitType.getNullValue();
					}
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getEventTargetUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellAbilityUnit",
				(arguments, globalScope, triggerScope) -> {
					if (!(triggerScope instanceof CommonTriggerExecutionScope)) {
						return unitType.getNullValue();
					}
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getSpellAbilityUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellTargetUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getSpellTargetUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellAbility",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(type("ability"), triggerScope instanceof CommonTriggerExecutionScope
							? ((CommonTriggerExecutionScope) triggerScope).getSpellAbility() : null);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellTargetItem",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(type("item"), triggerScope instanceof CommonTriggerExecutionScope
							? ((CommonTriggerExecutionScope) triggerScope).getSpellTargetItem() : null);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellTargetDestructable",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(type("destructable"),
							triggerScope instanceof CommonTriggerExecutionScope
									? ((CommonTriggerExecutionScope) triggerScope).getSpellTargetDestructable() : null);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetDecayingUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType, triggerScope instanceof CommonTriggerExecutionScope
							? ((CommonTriggerExecutionScope) triggerScope).getDecayingUnit() : null);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetDetectedUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType, triggerScope instanceof CommonTriggerExecutionScope
							? ((CommonTriggerExecutionScope) triggerScope).getDetectedUnit() : null);
				});
		// 1.29 never fires the detection events, so no scope carries a detecting player
		jassProgramVisitor.getJassNativeManager().createNative("GetEventDetectingPlayer",
				(arguments, globalScope, triggerScope) -> new HandleJassValue(playerType, null));
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellTargetPoint",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget spellTargetPoint = spellTargetPoint(
							(CommonTriggerExecutionScope) triggerScope);
					final LocationJass jassLocation = new LocationJass(spellTargetPoint.x, spellTargetPoint.y,
							this.simulation.getHandleIdAllocator().createHandleId());
					return new HandleJassValue(locationType, jassLocation);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellTargetX",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget spellTargetPoint = spellTargetPoint(
							(CommonTriggerExecutionScope) triggerScope);
					return RealJassValue.of(spellTargetPoint.x);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellTargetY",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget spellTargetPoint = spellTargetPoint(
							(CommonTriggerExecutionScope) triggerScope);
					return RealJassValue.of(spellTargetPoint.y);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellTargetLoc",
				(arguments, globalScope, triggerScope) -> {
					final AbilityPointTarget spellTargetPoint = spellTargetPoint(
							(CommonTriggerExecutionScope) triggerScope);
					return new HandleJassValue(locationType, new LocationJass(spellTargetPoint.x,
							spellTargetPoint.y, this.simulation.getHandleIdAllocator().createHandleId()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellAbilityId",
				(arguments, globalScope, triggerScope) -> {
					final War3ID spellAbilityId = ((CommonTriggerExecutionScope) triggerScope).getSpellAbilityId();
					return IntegerJassValue.of(spellAbilityId == null ? 0 : spellAbilityId.getValue());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetConstructedStructure",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getConstructedStructure());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetResearchingUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getResearchingUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetResearched",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(((CommonTriggerExecutionScope) triggerScope).getResearched());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSellingUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getSellingUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSoldUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType, ((CommonTriggerExecutionScope) triggerScope).getSoldUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetBuyingUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getBuyingUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSoldItem",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(itemType, ((CommonTriggerExecutionScope) triggerScope).getSoldItem());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetLevelingUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getTriggeringUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetLearningUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getTriggeringUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetLearnedSkill",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(((CommonTriggerExecutionScope) triggerScope).getLearnedSkill());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetLearnedSkillLevel",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(((CommonTriggerExecutionScope) triggerScope).getLearnedSkillLevel());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetLoadedUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getLoadedUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTransportUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getTransportUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetRescuer",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getRescuer());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetRevivableUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getRevivableUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTrainedUnit",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(unitType,
							((CommonTriggerExecutionScope) triggerScope).getTrainedUnit());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTrainedUnitType",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(((CommonTriggerExecutionScope) triggerScope).getTrainedUnitType());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellAbilityOrderId",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue
							.of(((CommonTriggerExecutionScope) triggerScope).getSpellAbilityOrderId());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellAbilityOrderButton",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue
							.of(((CommonTriggerExecutionScope) triggerScope).getSpellAbilityOrderId());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetSpellTargetType",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(orderbuttontypeType,
							((CommonTriggerExecutionScope) triggerScope).getSpellAbilityTargetType());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTriggerCastId",
				(arguments, globalScope, triggerScope) -> {
					return IntegerJassValue.of(((CommonTriggerExecutionScope) triggerScope).getTriggerCastId());
				});
	}

	/**
	 * 1.29: a unit target reads as where that unit is now; without a target (finish,
	 * endcast after it, no target spells) the point is 0,0.
	 */
	private static AbilityPointTarget spellTargetPoint(final CommonTriggerExecutionScope scope) {
		final AbilityPointTarget point = scope.getSpellTargetPoint();
		if (point != null) {
			return point;
		}
		CWidget widget = scope.getSpellTargetUnit();
		if (widget == null) {
			widget = scope.getSpellTargetItem();
		}
		if (widget == null) {
			widget = scope.getSpellTargetDestructable();
		}
		return widget == null ? new AbilityPointTarget(0, 0) : new AbilityPointTarget(widget.getX(), widget.getY());
	}

	/** Condition and Filter of one function give the same handle (measured on 1.29). */
	private static TriggerBooleanExpression functionBoolExpr(final GlobalScope globalScope, final CodeJassValue func,
			final HandleIdAllocator handleIds) {
		if (func == null) {
			return null;
		}
		final Integer instructionPtr = func.getUserFunctionInstructionPtr();
		TriggerBooleanExpression boolExpr = globalScope.getFunctionBoolExpr(instructionPtr);
		if (boolExpr == null) {
			boolExpr = handleIds.assign(new BoolExprCondition(func, globalScope));
			globalScope.putFunctionBoolExpr(instructionPtr, boolExpr);
		}
		return boolExpr;
	}

	static void setupTriggerAPI(final JassProgram jassProgramVisitor, final HandleJassType triggerType,
			final HandleJassType triggeractionType, final HandleJassType triggerconditionType,
			final HandleJassType boolexprType, final HandleJassType conditionfuncType,
			final HandleJassType filterfuncType, final HandleJassType eventidType,
			final HandleIdAllocator handleIds) {
		// ============================================================================
		// Native trigger interface
		//
		jassProgramVisitor.getJassNativeManager().createNative("CreateTrigger",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(triggerType, handleIds.assign(new Trigger()));
				});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyTrigger",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					trigger.destroy();
					handleIds.release(trigger);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("ResetTrigger",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					trigger.reset();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("EnableTrigger",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					trigger.setEnabled(true);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("DisableTrigger",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					trigger.setEnabled(false);
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsTriggerEnabled",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					return BooleanJassValue.of(trigger.isEnabled());
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerWaitOnSleeps",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					final Boolean value = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					trigger.setWaitOnSleeps(value.booleanValue());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("IsTriggerWaitOnSleeps",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					return BooleanJassValue.of(trigger.isWaitOnSleeps());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTriggeringTrigger",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(triggerType, triggerScope.getTriggeringTrigger());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTriggerEventId",
				(arguments, globalScope, triggerScope) -> {
					return new HandleJassValue(eventidType, (triggerScope instanceof CommonTriggerExecutionScope)
							? ((CommonTriggerExecutionScope) triggerScope).getTriggerEventId() : null);
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTriggerEvalCount",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					return IntegerJassValue.of(trigger.getEvalCount());
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetTriggerExecCount",
				(arguments, globalScope, triggerScope) -> {
					final Trigger trigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					return IntegerJassValue.of(trigger.getExecCount());
				});
		jassProgramVisitor.getJassNativeManager().createNative("ExecuteFunc",
				(arguments, globalScope, triggerScope) -> {
					final String funcName = arguments.get(0).visit(StringJassValueVisitor.getInstance());
					final Integer functionByName = globalScope.getUserFunctionInstructionPtr(funcName);
					Log.info("ExecuteFunc (\"" + funcName + "\")");
					if (functionByName != null) {
						globalScope.runThreadUntilSleep(globalScope.createThread(functionByName, triggerScope));
					}
					return null;
				});

		// ============================================================================
		// Boolean Expr API ( for compositing trigger conditions and unit filter
		// funcs...)
		// ============================================================================
		jassProgramVisitor.getJassNativeManager().createNative("And", (arguments, globalScope, triggerScope) -> {
			final TriggerBooleanExpression operandA = arguments.get(0)
					.visit(ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
			final TriggerBooleanExpression operandB = arguments.get(1)
					.visit(ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
			return new HandleJassValue(boolexprType, handleIds.assign(new BoolExprAnd(operandA, operandB)));
		});
		jassProgramVisitor.getJassNativeManager().createNative("Or", (arguments, globalScope, triggerScope) -> {
			final TriggerBooleanExpression operandA = arguments.get(0)
					.visit(ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
			final TriggerBooleanExpression operandB = arguments.get(1)
					.visit(ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
			return new HandleJassValue(boolexprType, handleIds.assign(new BoolExprOr(operandA, operandB)));
		});
		jassProgramVisitor.getJassNativeManager().createNative("Not", (arguments, globalScope, triggerScope) -> {
			final TriggerBooleanExpression operand = arguments.get(0)
					.visit(ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
			return new HandleJassValue(boolexprType, handleIds.assign(new BoolExprNot(operand)));
		});
		jassProgramVisitor.getJassNativeManager().createNative("Condition", (arguments, globalScope, triggerScope) -> {
			final CodeJassValue func = arguments.get(0).visit(CodeJassValueVisitor.getInstance());
			return new HandleJassValue(conditionfuncType, functionBoolExpr(globalScope, func, handleIds));
		});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyCondition",
				(arguments, globalScope, triggerScope) -> {
					final TriggerBooleanExpression condition = arguments.get(0)
							.visit(ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					reportMissingDestructorOnce("DestroyCondition");
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("Filter", (arguments, globalScope, triggerScope) -> {
			final CodeJassValue func = arguments.get(0).visit(CodeJassValueVisitor.getInstance());
			return new HandleJassValue(filterfuncType, functionBoolExpr(globalScope, func, handleIds));
		});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyFilter",
				(arguments, globalScope, triggerScope) -> {
					final TriggerBooleanExpression filter = nullable(arguments, 0,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					reportMissingDestructorOnce("DestroyFilter");
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("DestroyBoolExpr",
				(arguments, globalScope, triggerScope) -> {
					final TriggerBooleanExpression boolexpr = nullable(arguments, 0,
							ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					reportMissingDestructorOnce("DestroyBoolExpr");
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerAddCondition",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					final TriggerBooleanExpression condition = arguments.get(1)
							.visit(ObjectJassValueVisitor.<TriggerBooleanExpression>getInstance());
					final int index = whichTrigger.addCondition(condition);
					return new HandleJassValue(triggerconditionType,
							handleIds.assign(new TriggerCondition(condition, whichTrigger, index)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRemoveCondition",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					final TriggerCondition condition = arguments.get(1)
							.visit(ObjectJassValueVisitor.<TriggerCondition>getInstance());
					if (condition.getTrigger() != whichTrigger) {
						throw new IllegalArgumentException("Unable to remove condition, wrong trigger");
					}
					whichTrigger.removeConditionAtIndex(condition.getConditionIndex());
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerClearConditions",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					whichTrigger.clearConditions();
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerAddAction",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = arguments.get(0).visit(ObjectJassValueVisitor.<Trigger>getInstance());
					final CodeJassValue actionFunc = arguments.get(1).visit(CodeJassValueVisitor.getInstance());
					final int actionIndex = whichTrigger.addAction(actionFunc);
					return new HandleJassValue(triggeractionType,
							handleIds.assign(new TriggerAction(whichTrigger, actionFunc, actionIndex)));
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerRemoveAction",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = nullable(arguments, 0, ObjectJassValueVisitor.<Trigger>getInstance());
					final TriggerAction action = nullable(arguments, 1,
							ObjectJassValueVisitor.<TriggerAction>getInstance());
					if ((whichTrigger != null) && (action != null)) {
						whichTrigger.removeAction(action.getAction());
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("TriggerClearActions",
				(arguments, globalScope, triggerScope) -> {
					final Trigger whichTrigger = nullable(arguments, 0, ObjectJassValueVisitor.<Trigger>getInstance());
					if (whichTrigger != null) {
						whichTrigger.clearActions();
					}
					return null;
				});
	}

	/** A trigger's TriggerRegisterTimerEvent registration. */
	private static final class TimerEventRegistration extends RemovableTriggerEvent {
		@SavedFormerly("this$0")
		private final CommonEnvironment environment;
		@SavedFormerly("val$timer")
		private final CTimerNativeEvent timer;

		private TimerEventRegistration(final CommonEnvironment environment, final Trigger trigger,
				final CTimerNativeEvent timer) {
			super(trigger);
			this.environment = environment;
			this.timer = timer;
		}

		@Override
		public void remove() {
			// kill, not unregister: removed from its own action it must not re-arm after firing
			this.timer.kill(this.environment.simulation);
		}
	}

	/** A trigger's TriggerRegisterOnTick registration. */
	private static final class TickEventRegistration extends RemovableTriggerEvent {
		@SavedFormerly("this$0")
		private final CommonEnvironment environment;
		@SavedFormerly("val$trigger")
		private final Trigger trigger;

		private TickEventRegistration(final CommonEnvironment environment, final Trigger trigger) {
			super(trigger);
			this.environment = environment;
			this.trigger = trigger;
		}

		@Override
		public void remove() {
			this.environment.simulation.unregisterOnTickEvent(this.trigger);
		}
	}

	/** A trigger's TriggerRegisterOnUnitTick registration. */
	private static final class UnitTickEventRegistration extends RemovableTriggerEvent {
		@SavedFormerly("val$trigger")
		private final Trigger trigger;
		@SavedFormerly("val$unit")
		private final CUnit unit;

		private UnitTickEventRegistration(final Trigger trigger, final CUnit unit) {
			super(trigger);
			this.trigger = trigger;
			this.unit = unit;
		}

		@Override
		public void remove() {
			this.unit.unregisterOnTickEvent(this.trigger);
		}
	}
}
