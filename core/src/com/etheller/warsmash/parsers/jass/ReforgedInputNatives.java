package com.etheller.warsmash.parsers.jass;

import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.etheller.interpreter.ast.function.ConvertedConstant;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.LocalUiLink;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.RenderCommands;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.WarsmashUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CGameCacheSync;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;

/**
 * The Reforged natives for what a player's machine does outside the simulation: data a script sends to every player
 * (sync), keys and the mouse, the size of the window and the few switches of the game interface. What changes the
 * game travels as a player command, as every other input, so a network game and a replay see it at the same step;
 * what only reads this machine (a key held now, the pointer) reads the local device and is the script's to use
 * for local effects only.
 */
final class ReforgedInputNatives {
	private static final int MAX_SYNC_BYTES = 255;

	private final JassProgram program;
	private final War3MapViewer viewer;
	private final CSimulation simulation;
	private final WarsmashUI ui;
	private final LocalUiLink link;
	private final RenderCommands commands;
	// what the script last set; the interface applies it when the frame is played
	private boolean selectionEnabled = true;
	private boolean selectionCircleEnabled = true;
	private boolean targetIndicatorEnabled = true;

	private ReforgedInputNatives(final JassProgram program, final War3MapViewer viewer, final WarsmashUI ui) {
		this.program = program;
		this.viewer = viewer;
		this.simulation = viewer.simulation;
		this.ui = ui;
		this.link = LocalUiLink.of(viewer, ui);
		this.commands = this.link.commands();
	}

	static void register(final JassProgram program, final War3MapViewer viewer, final WarsmashUI ui) {
		final ReforgedInputNatives natives = new ReforgedInputNatives(program, viewer, ui);
		natives.registerSync();
		natives.registerKeys();
		natives.registerMouse();
		natives.registerClientAndInterface();
	}

	private void add(final String name, final JassFunction function) {
		this.program.getJassNativeManager().createNative(name, function);
	}

	private static <T> T object(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(ObjectJassValueVisitor.<T>getInstance());
	}

	private static String string(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(StringJassValueVisitor.getInstance());
	}

	private static int integer(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(IntegerJassValueVisitor.getInstance());
	}

	private static boolean bool(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(BooleanJassValueVisitor.getInstance());
	}

	/** The number a Convert<Type> constant stands for (a key, a mouse button), 0 for anything else. */
	private static int constant(final List<JassValue> arguments, final int index) {
		final Object value = ReforgedInputNatives.<Object>object(arguments, index);
		return value instanceof ConvertedConstant ? ((ConvertedConstant) value).getValue() : 0;
	}

	private static CommonTriggerExecutionScope scope(final TriggerExecutionScope triggerScope) {
		return triggerScope instanceof CommonTriggerExecutionScope ? (CommonTriggerExecutionScope) triggerScope
				: null;
	}

	private HandleJassValue converted(final com.etheller.interpreter.ast.scope.GlobalScope globalScope,
			final String typeName, final int value) {
		final HandleJassType type = globalScope.getHandleType(typeName);
		return this.program.getJassNativeManager().convertedConstant(type, value);
	}

	/** The listener this machine's input goes through; without an interface there is no command stream, so null. */
	private CPlayerUnitOrderListener commandSink() {
		return this.ui == null ? null : this.ui.getUnitOrderListener();
	}

	private void registerSync() {
		// The event of a data sent from the server has no sender here: nothing in this engine speaks for a server,
		// so such a trigger stays registered and never runs.
		add("BlzTriggerRegisterPlayerSyncEvent", (arguments, globalScope, triggerScope) -> {
			final Trigger trigger = object(arguments, 0);
			final CPlayer player = object(arguments, 1);
			final HandleJassType eventType = globalScope.getHandleType("event");
			if ((trigger == null) || (player == null)) {
				return new HandleJassValue(eventType, null);
			}
			final String prefix = string(arguments, 2);
			if (bool(arguments, 3)) {
				return new HandleJassValue(eventType, null);
			}
			return new HandleJassValue(eventType, player.addSyncEvent(globalScope, trigger, prefix));
		});
		add("BlzSendSyncData", (arguments, globalScope, triggerScope) -> {
			final String prefix = string(arguments, 0);
			final String data = string(arguments, 1);
			final CPlayerUnitOrderListener sink = commandSink();
			if ((sink == null) || (prefix == null) || (data == null) || prefix.isEmpty()
					|| CGameCacheSync.isCacheSync(prefix)
					|| (prefix.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SYNC_BYTES)
					|| (data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SYNC_BYTES)) {
				return BooleanJassValue.FALSE;
			}
			sink.syncData(prefix, data);
			return BooleanJassValue.TRUE;
		});
		add("BlzGetTriggerSyncPrefix", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = scope(triggerScope);
			return new StringJassValue(scope == null || scope.getSyncPrefix() == null ? "" : scope.getSyncPrefix());
		});
		add("BlzGetTriggerSyncData", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = scope(triggerScope);
			return new StringJassValue(scope == null || scope.getSyncData() == null ? "" : scope.getSyncData());
		});
	}

	private void registerKeys() {
		add("BlzTriggerRegisterPlayerKeyEvent", (arguments, globalScope, triggerScope) -> {
			final Trigger trigger = object(arguments, 0);
			final CPlayer player = object(arguments, 1);
			final HandleJassType eventType = globalScope.getHandleType("event");
			if ((trigger == null) || (player == null)) {
				return new HandleJassValue(eventType, null);
			}
			return new HandleJassValue(eventType, player.addKeyEvent(globalScope, trigger, constant(arguments, 2),
					integer(arguments, 3), bool(arguments, 4)));
		});
		add("BlzGetTriggerPlayerKey", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = scope(triggerScope);
			return scope == null || scope.getTriggerEventId() == null ? globalScope.getHandleType("oskeytype")
					.getNullValue() : converted(globalScope, "oskeytype", scope.getKeyCode());
		});
		add("BlzGetTriggerPlayerMetaKey", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = scope(triggerScope);
			return IntegerJassValue.of(scope == null ? 0 : scope.getKeyMetaKeys());
		});
		add("BlzGetTriggerPlayerIsKeyDown", (arguments, globalScope, triggerScope) -> {
			final CommonTriggerExecutionScope scope = scope(triggerScope);
			return BooleanJassValue.of((scope != null) && scope.isKeyDown());
		});
		// What this machine's keyboard holds now: a local read, like GetLocalPlayer.
		add("BlzIsKeyPressed", (arguments, globalScope, triggerScope) -> {
			final int gdxKey = OsKeys.toGdx(constant(arguments, 0));
			return BooleanJassValue.of((gdxKey >= 0) && this.link.input().key(gdxKey));
		});
		add("BlzIsMetaKeyPressed", (arguments, globalScope, triggerScope) -> {
			final int mask = integer(arguments, 0);
			return BooleanJassValue.of((mask != 0) && ((this.link.input().metaKeys() & mask) == mask));
		});
	}

	private void registerMouse() {
		add("BlzIsMouseButtonPressed", (arguments, globalScope, triggerScope) -> {
			// common.j numbers the buttons 1 left, 2 middle and 3 right.
			switch (constant(arguments, 0)) {
			case 1:
				return BooleanJassValue.of(this.link.input().mouseButton(Buttons.LEFT));
			case 2:
				return BooleanJassValue.of(this.link.input().mouseButton(Buttons.MIDDLE));
			case 3:
				return BooleanJassValue.of(this.link.input().mouseButton(Buttons.RIGHT));
			default:
				return BooleanJassValue.FALSE;
			}
		});
		add("BlzGetMouseScreenPosX",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(this.link.input().mouseX()));
		add("BlzGetMouseScreenPosY",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(this.link.input().mouseY()));
		add("BlzSetMousePos", (arguments, globalScope, triggerScope) -> {
			final int x = integer(arguments, 0);
			final int y = integer(arguments, 1);
			this.commands.send(() -> {
				if (Gdx.input != null) {
					Gdx.input.setCursorPosition(x, y);
				}
			});
			return null;
		});
		add("BlzGetMouseFocusUnit", (arguments, globalScope, triggerScope) -> {
			final CUnit unit;
			if (this.commands.isQueued()) {
				final int handle = this.link.input().hoveredUnitHandle();
				unit = handle == 0 ? null : this.simulation.getUnit(handle);
			}
			else {
				unit = this.ui == null ? null : this.ui.getMouseFocusUnit();
			}
			return new HandleJassValue(globalScope.getHandleType("unit"),
					(unit == null) || unit.isRemovedFromGame() ? null : unit);
		});
		add("BlzEnableCursor", (arguments, globalScope, triggerScope) -> {
			final boolean enabled = bool(arguments, 0);
			this.commands.send(() -> this.viewer.scriptUiState.setCursorEnabled(enabled));
			return null;
		});
	}

	private void registerClientAndInterface() {
		add("BlzGetLocalClientWidth",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(this.link.input().width()));
		add("BlzGetLocalClientHeight",
				(arguments, globalScope, triggerScope) -> IntegerJassValue.of(this.link.input().height()));
		// This machine's own focus, as the backend reports it: a script must keep it out of the game state.
		add("BlzIsLocalClientActive", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(this.viewer.scriptUiState.isClientActive()));
		add("BlzIsSelectionEnabled", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(this.selectionEnabled));
		add("BlzEnableSelections", (arguments, globalScope, triggerScope) -> {
			final boolean enabled = bool(arguments, 0);
			final boolean circle = bool(arguments, 1);
			this.selectionEnabled = enabled;
			this.selectionCircleEnabled = circle;
			this.commands.send(() -> {
				this.viewer.scriptUiState.setSelectionEnabled(enabled);
				this.viewer.scriptUiState.setSelectionCircleEnabled(circle);
			});
			return null;
		});
		add("BlzIsSelectionCircleEnabled", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(this.selectionCircleEnabled));
		add("BlzEnableTargetIndicator", (arguments, globalScope, triggerScope) -> {
			final boolean enabled = bool(arguments, 0);
			this.targetIndicatorEnabled = enabled;
			this.commands.send(() -> this.viewer.scriptUiState.setTargetIndicatorEnabled(enabled));
			return null;
		});
		add("BlzIsTargetIndicatorEnabled", (arguments, globalScope, triggerScope) -> BooleanJassValue
				.of(this.targetIndicatorEnabled));
		// Shows a chat line of the player on this machine, as the chat command does for every machine.
		add("BlzDisplayChatMessage", (arguments, globalScope, triggerScope) -> {
			final CPlayer player = object(arguments, 0);
			final String message = string(arguments, 2);
			if ((player != null) && (message != null)) {
				this.simulation.getPlayerCommandHost().chatMessage(player.getId(), integer(arguments, 1), -1,
						message);
			}
			return null;
		});
	}
}
