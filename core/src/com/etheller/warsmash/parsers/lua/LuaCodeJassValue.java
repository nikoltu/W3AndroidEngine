package com.etheller.warsmash.parsers.lua;

import java.util.Objects;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.JassValue;

/** A Lua function that natives take as code (Condition, TriggerAddAction, TimerStart). */
public final class LuaCodeJassValue extends CodeJassValue {
	/** What Condition and Filter key their handles by: the same function gives the same handle. */
	public static final class Key {
		private final int id;

		Key(final int id) {
			this.id = id;
		}

		@Override
		public int hashCode() {
			return this.id;
		}

		@Override
		public boolean equals(final Object obj) {
			return (obj instanceof Key) && (((Key) obj).id == this.id);
		}
	}

	private final LuaEnvironment environment;
	private final int id;
	private final Key key;

	LuaCodeJassValue(final LuaEnvironment environment, final int id) {
		this.environment = environment;
		this.id = id;
		this.key = new Key(id);
	}

	int getId() {
		return this.id;
	}

	LuaEnvironment getEnvironment() {
		return this.environment;
	}

	@Override
	public void startOn(final JassThread thread) {
		this.environment.holdFunction(this.id);
		thread.setContinuation(new LuaContinuation(this.environment, this.id));
		thread.instructionPtr = -1;
	}

	@Override
	public void call(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
		globalScope.queueThread(globalScope.createThread(this, triggerScope));
	}

	@Override
	public JassValue callAndExecuteCapturingReturnValue(final GlobalScope globalScope,
			final TriggerExecutionScope triggerScope, final String contextNameForError, final JassValue defaultValue) {
		final JassThread thread = globalScope.createThreadCapturingReturnValue(this, triggerScope);
		return globalScope.runThreadUntilCompletionAndReadReturnValue(thread, contextNameForError, defaultValue);
	}

	@Override
	public int toInteger() {
		return this.id;
	}

	@Override
	public boolean isNull() {
		return false;
	}

	@Override
	public Object getBoolExprKey() {
		return this.key;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id);
	}

	@Override
	public boolean equals(final Object obj) {
		return (obj instanceof LuaCodeJassValue) && (((LuaCodeJassValue) obj).id == this.id)
				&& (((LuaCodeJassValue) obj).environment == this.environment);
	}
}
