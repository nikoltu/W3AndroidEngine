package com.etheller.interpreter.ast.execution;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.util.JassSettings;
import com.etheller.warsmash.util.Log;

public class JassThread {
	public JassStackFrame stackFrame;
	public GlobalScope globalScope;
	public TriggerExecutionScope triggerScope;
	public int instructionPtr;
	public boolean sleeping = false;
	/**
	 * Operations since the thread started or last waited, counted as in the original game (see
	 * {@link com.etheller.interpreter.ast.util.JassSettings#OP_LIMIT}).
	 */
	public int opCount;
	public JassThread parent;
	private boolean sleepEndsThread;
	private boolean halted;
	private ThreadContinuation continuation;

	public JassThread(final JassStackFrame stackFrame, final GlobalScope globalScope,
			final TriggerExecutionScope triggerScope, final int instructionPtr) {
		this.stackFrame = stackFrame;
		this.globalScope = globalScope;
		this.triggerScope = triggerScope;
		this.instructionPtr = instructionPtr;
	}

	public void setSleeping(final boolean sleeping) {
		this.sleeping = sleeping;
		if (sleeping) {
			this.opCount = 0;
			for (JassStackFrame frame = this.stackFrame; frame != null; frame = frame.stackBase) {
				frame.countLocalStartingValues();
			}
		}
	}

	/**
	 * Counts the instruction's operations and stops the thread when it goes over the limit, as the original game
	 * does: silently, while the game and other threads, also the one that started this one, go on.
	 *
	 * @return false if the thread was stopped and the instruction must not run
	 */
	public boolean countOps(final int opCost) {
		if ((this.opCount += opCost) >= JassSettings.OP_LIMIT) {
			Log.warn("JASS thread stopped: operation limit");
			halt();
			return false;
		}
		return true;
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
		return resumeContinuation();
	}

	/** The code this thread runs in place of instructions (Lua), or null. */
	public ThreadContinuation getContinuation() {
		return this.continuation;
	}

	public void setContinuation(final ThreadContinuation continuation) {
		this.continuation = continuation;
	}

	/**
	 * Runs the continuation up to its end or to the next sleep.
	 *
	 * @return true if it is not finished
	 */
	protected final boolean resumeContinuation() {
		final ThreadContinuation current = this.continuation;
		if (current == null) {
			return false;
		}
		final boolean more;
		try {
			more = current.resume(this);
		}
		catch (final RuntimeException | Error e) {
			this.continuation = null;
			throw e;
		}
		if (!more && (this.continuation == current)) {
			this.continuation = null;
		}
		return more;
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
