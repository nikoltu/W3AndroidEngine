package com.etheller.interpreter.ast.value;

import java.util.Objects;

import com.etheller.interpreter.ast.execution.JassStackFrame;
import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;

/** A function of the JASS instructions, found by the position of its first instruction. */
public class InstructionCodeJassValue extends CodeJassValue {
	private final Integer userFunctionInstructionPtr;

	public InstructionCodeJassValue(final Integer userFunctionInstructionPtr) {
		this.userFunctionInstructionPtr = userFunctionInstructionPtr;
	}

	@Override
	public Integer getUserFunctionInstructionPtr() {
		return this.userFunctionInstructionPtr;
	}

	@Override
	public void initStack(final JassStackFrame stackFrame) {
	}

	@Override
	public void startOn(final JassThread thread) {
		thread.instructionPtr = this.userFunctionInstructionPtr;
		initStack(thread.stackFrame);
	}

	@Override
	public boolean isNull() {
		return this.userFunctionInstructionPtr == null;
	}

	@Override
	public Object getBoolExprKey() {
		return this.userFunctionInstructionPtr;
	}

	@Override
	public final void call(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
		final JassThread thread = globalScope.createThread(this, triggerScope);
		globalScope.queueThread(thread);
		return;
	}

	@Override
	public final JassValue callAndExecuteCapturingReturnValue(final GlobalScope globalScope,
			final TriggerExecutionScope triggerScope, final String contextNameForError, final JassValue defaultValue) {
		final JassThread thread = globalScope.createThreadCapturingReturnValue(this, triggerScope);
		return globalScope.runThreadUntilCompletionAndReadReturnValue(thread,
				contextNameForError, defaultValue);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.userFunctionInstructionPtr);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final InstructionCodeJassValue other = (InstructionCodeJassValue) obj;
		return Objects.equals(this.userFunctionInstructionPtr, other.userFunctionInstructionPtr);
	}

}
