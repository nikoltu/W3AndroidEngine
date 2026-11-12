package com.etheller.warsmash.parsers.jass;

import com.etheller.interpreter.ast.debug.JassException;
import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.parsers.jass.Jass2.CommonEnvironment;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.ai.AIDifficulty;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimerSleepAction;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;
import java.util.List;

/**
 * Natives that start and command computer player scripts, and those common.ai declares again.
 */
final class AINatives extends CommonNatives {
	AINatives(final CommonEnvironment environment) {
		super(environment);
	}

	@Override
	void register() {
		final HandleJassType aidifficultyType = type("aidifficulty");

		jassProgramVisitor.getJassNativeManager().createNative("StartMeleeAI",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final String filePath = nullable(arguments, 1, StringJassValueVisitor.getInstance());
					if (filePath != null) {
						Jass2.doAIScript(dataSource, uiViewport, uiScene, war3MapViewer, player, filePath, meleeUI,
								originalFiles, jassProgramVisitor, this.simulation, "main", true);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("StartCampaignAI",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final String filePath = nullable(arguments, 1, StringJassValueVisitor.getInstance());
					// Measured: a second AI script for the same player does not start.
					if ((player != null) && (filePath != null) && (player.getAiScript() == null)) {
						Jass2.doAIScript(dataSource, uiViewport, uiScene, war3MapViewer, player, filePath, meleeUI,
								originalFiles, jassProgramVisitor, this.simulation, "main", true);
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("CommandAI",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					if ((player != null) && (player.getAiScript() != null)) {
						player.getAiScript().command(arguments.get(1).visit(IntegerJassValueVisitor.getInstance()),
								arguments.get(2).visit(IntegerJassValueVisitor.getInstance()));
					}
					return null;
				});
		jassProgramVisitor.getJassNativeManager().createNative("GetAIDifficulty",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final AIDifficulty difficulty = player == null ? null : player.getAIDifficulty();
					return new HandleJassValue(aidifficultyType,
							difficulty == null ? AIDifficulty.NORMAL : difficulty);
				});
		jassProgramVisitor.getJassNativeManager().createNative("PauseCompAI",
				(arguments, globalScope, triggerScope) -> {
					final CPlayer player = nullable(arguments, 0, ObjectJassValueVisitor.getInstance());
					final boolean pause = arguments.get(1).visit(BooleanJassValueVisitor.getInstance());
					if (player != null) {
						if (player.getAiScript() != null) {
							player.getAiScript().setPaused(pause);
						}
					}
					return null;
				});
		////// ====== Common AI functions =======
		/// These are getting declared in Jass2 here so that you can magically use them
		////// from outside
		// common ai, maybe, sometimes
		registerSharedCommonAIFunctions(jassProgramVisitor, war3MapViewer, meleeUI, this.simulation);
	}

	static void registerSharedCommonAIFunctions(final JassProgram jassProgramVisitor,
			final War3MapViewer war3MapViewer, final WarsmashUI meleeUI, final CSimulation simulation) {
		jassProgramVisitor.getJassNativeManager().createNative("Sleep", new JassFunction() {
			@Override
			public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
					final TriggerExecutionScope triggerScope) {
				final Double seconds = arguments.get(0).visit(RealJassValueVisitor.getInstance());
				final JassThread currentThread = globalScope.getCurrentThread();
				if (currentThread != null) {
					currentThread.setSleeping(true);
					final CTimerSleepAction timer = new CTimerSleepAction(currentThread);
					timer.setRepeats(false);
					timer.setTimeoutTime(seconds.floatValue());
					timer.start(simulation);
				}
				else {
					throw new JassException(globalScope, "Needs to sleep " + seconds + " but no thread was found",
							null);
				}
				return null;
			}
		});
		jassProgramVisitor.getJassNativeManager().createNative("DebugS", new JassFunction() {
			@Override
			public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
					final TriggerExecutionScope triggerScope) {
				final String str = nullable(arguments, 0, StringJassValueVisitor.getInstance());
				final String value = "DebugS: " + str;
				meleeUI.displayTimedText(0, 0, 20, value);
				Log.warn(value);
				return null;
			}
		});
		jassProgramVisitor.getJassNativeManager().createNative("DebugFI", new JassFunction() {
			@Override
			public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
					final TriggerExecutionScope triggerScope) {
				final String str = nullable(arguments, 0, StringJassValueVisitor.getInstance());
				final int val = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());

				final String value = "DebugFI: " + String.format(str, val);
				meleeUI.displayTimedText(0, 0, 20, value);
				Log.warn(value);
				return null;
			}
		});
		jassProgramVisitor.getJassNativeManager().createNative("DebugUnitID", new JassFunction() {
			@Override
			public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
					final TriggerExecutionScope triggerScope) {
				final String str = nullable(arguments, 0, StringJassValueVisitor.getInstance());
				final int valRawcode = arguments.get(1).visit(IntegerJassValueVisitor.getInstance());
				final War3ID valId = new War3ID(valRawcode);

				final String value = "DebugUnitID: " + String.format(str, valId);
				meleeUI.displayTimedText(0, 0, 20, value);
				Log.warn(value);
				return null;
			}
		});
		jassProgramVisitor.getJassNativeManager().createNative("DisplayText", new JassFunction() {
			@Override
			public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
					final TriggerExecutionScope triggerScope) {
				final int p = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
				final String str = nullable(arguments, 1, StringJassValueVisitor.getInstance());

				if (war3MapViewer.isViewedPlayer(p)) {
					meleeUI.displayTimedText(0, 0, 20, str);
				}
				Log.warn(p + ": " + str);
				return null;
			}
		});
		jassProgramVisitor.getJassNativeManager().createNative("DisplayTextI", new JassFunction() {
			@Override
			public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
					final TriggerExecutionScope triggerScope) {
				final int p = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
				final String str = nullable(arguments, 1, StringJassValueVisitor.getInstance());
				final int v1 = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
				final String value = String.format(str, v1);
				if (war3MapViewer.isViewedPlayer(p)) {
					meleeUI.displayTimedText(0, 0, 20, value);
				}
				Log.warn(p + ": " + value);
				return null;
			}
		});
		jassProgramVisitor.getJassNativeManager().createNative("DisplayTextII", new JassFunction() {
			@Override
			public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
					final TriggerExecutionScope triggerScope) {
				final int p = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
				final String str = nullable(arguments, 1, StringJassValueVisitor.getInstance());
				final int v1 = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
				final int v2 = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
				final String value = String.format(str, v1, v2);
				if (war3MapViewer.isViewedPlayer(p)) {
					meleeUI.displayTimedText(0, 0, 20, value);
				}
				Log.warn(p + ": " + value);
				return null;
			}
		});
		jassProgramVisitor.getJassNativeManager().createNative("DisplayTextIII", new JassFunction() {
			@Override
			public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
					final TriggerExecutionScope triggerScope) {
				final int p = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
				final String str = nullable(arguments, 1, StringJassValueVisitor.getInstance());
				final int v1 = arguments.get(2).visit(IntegerJassValueVisitor.getInstance());
				final int v2 = arguments.get(3).visit(IntegerJassValueVisitor.getInstance());
				final int v3 = arguments.get(4).visit(IntegerJassValueVisitor.getInstance());
				final String value = String.format(str, v1, v2, v3);
				if (war3MapViewer.isViewedPlayer(p)) {
					meleeUI.displayTimedText(0, 0, 20, value);
				}
				Log.warn(p + ": " + value);
				return null;
			}
		});
		jassProgramVisitor.getJassNativeManager().createNative("GetUnitBuildTime",
				(arguments, globalScope, triggerScope) -> {
					final Integer typeId = arguments.get(0).visit(IntegerJassValueVisitor.getInstance());
					final CUnitType unitTypeData = simulation.getUnitData().getUnitType(new War3ID(typeId));
					if (unitTypeData != null) {
						return IntegerJassValue.of(unitTypeData.getBuildTime());
					}
					return IntegerJassValue.ZERO;
				});
	}
}
