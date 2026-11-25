package com.etheller.interpreter.ast.value;

import com.etheller.interpreter.ast.execution.JassStackFrame;
import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;

/**
 * A function value ("code"): either a position in the JASS instructions or a function of another script language.
 */
public abstract class CodeJassValue implements JassValue {

	/** Runs the function on a new thread that the thread queue picks up. */
	public abstract void call(GlobalScope globalScope, TriggerExecutionScope triggerScope);

	/**
	 * Runs the function now, to its end, and gives what it returns; the default if it does not finish (it waited) or
	 * fails.
	 */
	public abstract JassValue callAndExecuteCapturingReturnValue(GlobalScope globalScope,
			TriggerExecutionScope triggerScope, String contextNameForError, JassValue defaultValue);

	/** Makes the thread run this function; the thread starts at its beginning and has an empty stack frame. */
	public abstract void startOn(JassThread thread);

	/** A code value of no function (the null of the type). */
	public abstract boolean isNull();

	/**
	 * What names the function for Condition and Filter: both of one function give the same handle. Equal keys are the
	 * same function.
	 */
	public abstract Object getBoolExprKey();

	/**
	 * The position in the JASS instructions; null for code of any other script language, which has no position.
	 */
	public Integer getUserFunctionInstructionPtr() {
		return null;
	}

	/** The function as a number, for a cast to integer: its position, or a number of its own; 0 for none. */
	public int toInteger() {
		final Integer pointer = getUserFunctionInstructionPtr();
		return pointer == null ? 0 : pointer;
	}

	public void initStack(final JassStackFrame stackFrame) {
	}

	@Override
	public <TYPE> TYPE visit(final JassValueVisitor<TYPE> visitor) {
		return visitor.accept(this);
	}

}
