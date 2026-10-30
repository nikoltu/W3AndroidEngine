package com.etheller.interpreter.ast.execution;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;

public class JassThread {
	public JassStackFrame stackFrame;
	public GlobalScope globalScope;
	public TriggerExecutionScope triggerScope;
	public int instructionPtr;
	public boolean sleeping = false;
	public JassThread parent;
	private boolean sleepEndsThread;
	private boolean halted;

	public JassThread(final JassStackFrame stackFrame, final GlobalScope globalScope,
			final TriggerExecutionScope triggerScope, final int instructionPtr) {
		this.stackFrame = stackFrame;
		this.globalScope = globalScope;
		this.triggerScope = triggerScope;
		this.instructionPtr = instructionPtr;
	}

	public void setSleeping(final boolean sleeping) {
		this.sleeping = sleeping;
	}

	public boolean isSleeping() {
		return this.sleeping;
	}

	/**
	 * Called when the current function returns; a thread that runs several functions in a row starts the next one
	 * here.
	 *
	 * @return true if the thread has more to run
	 */
	public boolean advance() {
		return false;
	}

	public void setSleepEndsThread(final boolean sleepEndsThread) {
		this.sleepEndsThread = sleepEndsThread;
	}

	/**
	 * The map's main function and timer callbacks cannot wait: a sleep in them stops the thread for good.
	 */
	public boolean isSleepEndsThread() {
		return this.sleepEndsThread;
	}

	public void halt() {
		this.halted = true;
		this.sleeping = true;
	}

	public boolean isHalted() {
		return this.halted;
	}
}
