package com.etheller.interpreter.ast.scope.trigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.etheller.interpreter.ast.debug.JassException;
import com.etheller.interpreter.ast.execution.JassStackFrame;
import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.util.CHandle;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.JassValue;

public class Trigger implements CHandle {
	private static int STUPID_STATIC_TRIGGER_COUNT_DELETE_THIS_LATER = 452354453;
	private final int handleId = STUPID_STATIC_TRIGGER_COUNT_DELETE_THIS_LATER++;
	private final List<TriggerBooleanExpression> conditions = new ArrayList<>();
	private final List<JassFunction> actions = new ArrayList<>();
	private final List<RemovableTriggerEvent> events = new ArrayList<>();
	private int evalCount;
	private int execCount;
	private boolean enabled = true;
	// used for eval
	private transient final TriggerExecutionScope triggerExecutionScope = new TriggerExecutionScope(this);
	private boolean waitOnSleeps = true;

	public void addEvent(final RemovableTriggerEvent event) {
		this.events.add(event);
	}

	public int addAction(final JassFunction function) {
		final int index = this.actions.size();
		this.actions.add(function);
		return index;
	}

	public int addAction(final CodeJassValue function) {
		final int index = this.actions.size();
		this.actions.add(new JassThreadActionFunc(function));
		return index;
	}

	public int addCondition(final TriggerBooleanExpression boolexpr) {
		final int index = this.conditions.size();
		this.conditions.add(boolexpr);
		return index;
	}

	public void removeCondition(final TriggerBooleanExpression boolexpr) {
		this.conditions.remove(boolexpr);
	}

	public void removeConditionAtIndex(final int conditionIndex) {
		this.conditions.remove(conditionIndex);
	}

	public void clearConditions() {
		this.conditions.clear();
	}

	public int getEvalCount() {
		return this.evalCount;
	}

	public int getExecCount() {
		return this.execCount;
	}

	public boolean evaluate(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
		// Every condition runs, even after one has failed (1.29).
		boolean result = true;
		for (final TriggerBooleanExpression condition : new ArrayList<>(this.conditions)) {
			if (!condition.evaluate(globalScope, triggerScope)) {
				result = false;
			}
		}
		return result;
	}

	/**
	 * Runs the trigger for one of its events: the conditions, then the actions on a new thread that runs right away
	 * until it finishes or sleeps.
	 */
	public void fire(final GlobalScope globalScope, final TriggerExecutionScope evaluateScope,
			final TriggerExecutionScope executeScope) {
		this.evalCount++;
		if (evaluate(globalScope, evaluateScope)) {
			this.execCount++;
			runActions(globalScope, executeScope);
		}
	}

	/**
	 * TriggerExecute: the actions without the conditions, also when the trigger is disabled.
	 *
	 * @return the thread if it is still running (it slept), otherwise null
	 */
	public JassThread execute(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
		this.evalCount++;
		this.execCount++;
		return runActions(globalScope, triggerScope);
	}

	private JassThread runActions(final GlobalScope globalScope, final TriggerExecutionScope triggerScope) {
		if (this.actions.isEmpty()) {
			return null;
		}
		final ActionsThread thread = new ActionsThread(globalScope, triggerScope, new ArrayList<>(this.actions));
		try {
			if (thread.advance() && globalScope.runThreadUntilSleep(thread)) {
				return thread;
			}
		}
		catch (final JassException e) {
			throw e;
		}
		catch (final Exception e) {
			throw new JassException(globalScope, "Exception during Trigger action execute", e);
		}
		return null;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
	}

	public void destroy() {
		for (final RemovableTriggerEvent event : this.events) {
			event.remove();
		}
		this.events.clear();
	}

	public void reset() {
//		this.actions.clear();
//		this.conditions.clear();
//		this.enabled = true;
//		this.waitOnSleeps = true;
		this.evalCount = 0;
		this.execCount = 0;
	}

	public void setWaitOnSleeps(final boolean waitOnSleeps) {
		this.waitOnSleeps = waitOnSleeps;
	}

	public boolean isWaitOnSleeps() {
		return this.waitOnSleeps;
	}

	@Override
	public int getHandleId() {
		return this.handleId;
	}

	private static final class JassThreadActionFunc implements JassFunction {
		private final CodeJassValue codeJassValue;

		public JassThreadActionFunc(final CodeJassValue codeJassValue) {
			this.codeJassValue = codeJassValue;
		}

		@Override
		public JassValue call(final List<JassValue> arguments, final GlobalScope globalScope,
				final TriggerExecutionScope triggerScope) {
			globalScope.runThreadUntilSleep(globalScope.createThread(this.codeJassValue, triggerScope));
			return null;
		}

	}

	/**
	 * All actions of one trigger run in order on one thread, so a sleep in one action also delays the next.
	 */
	private static final class ActionsThread extends JassThread {
		private final List<JassFunction> actions;
		private int nextAction;
		private JassThread waiter;

		public ActionsThread(final GlobalScope globalScope, final TriggerExecutionScope triggerScope,
				final List<JassFunction> actions) {
			super(null, globalScope, triggerScope, -1);
			this.actions = actions;
		}

		@Override
		public boolean advance() {
			while (this.nextAction < this.actions.size()) {
				final JassFunction action = this.actions.get(this.nextAction++);
				if (action instanceof JassThreadActionFunc) {
					final CodeJassValue code = ((JassThreadActionFunc) action).codeJassValue;
					this.stackFrame = new JassStackFrame();
					this.stackFrame.returnAddressInstructionPtr = -1;
					this.instructionPtr = code.getUserFunctionInstructionPtr();
					code.initStack(this.stackFrame);
					return true;
				}
				action.call(Collections.emptyList(), this.globalScope, this.triggerScope);
			}
			if (this.waiter != null) {
				this.waiter.setSleeping(false);
				this.waiter = null;
			}
			return false;
		}
	}

	/**
	 * TriggerExecuteWait: the waiting thread sleeps until the trigger's thread is done.
	 */
	public static void wakeWhenDone(final JassThread triggerThread, final JassThread waiter) {
		((ActionsThread) triggerThread).waiter = waiter;
		waiter.setSleeping(true);
	}

	public TriggerExecutionScope getTriggerExecutionScope() {
		return this.triggerExecutionScope;
	}

	public void removeEvent(final RemovableTriggerEvent evt) {
		if (evt != null) {
			evt.remove();
			this.events.remove(evt);
		}
	}
}
