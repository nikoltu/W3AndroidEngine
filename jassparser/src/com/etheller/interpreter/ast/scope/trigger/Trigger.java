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
import com.etheller.interpreter.ast.util.CAllocatedHandle;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.JassValue;

public class Trigger implements CAllocatedHandle {
	private int handleId;
	private boolean handleReleased;
	private boolean handleIdKept;
	private int referenceCount;
	// A script may make millions of triggers, most with one of each, so a list holds nothing (null) or its only element
	// itself until a second one comes. Elements are never lists, and a list from a save is used as it is.
	private Object conditions;
	private Object actions;
	private Object events;
	private int evalCount;
	private int execCount;
	private boolean enabled = true;
	// used for eval
	private transient TriggerExecutionScope triggerExecutionScope;
	private boolean waitOnSleeps = true;

	public void addEvent(final RemovableTriggerEvent event) {
		this.events = with(this.events, event);
	}

	public int addAction(final JassFunction function) {
		final int index = sizeOf(this.actions);
		this.actions = with(this.actions, function);
		return index;
	}

	public int addAction(final CodeJassValue function) {
		return addAction(new JassThreadActionFunc(function));
	}

	public JassFunction getActionAt(final int index) {
		return (JassFunction) at(this.actions, index);
	}

	public void removeAction(final JassFunction action) {
		final List<Object> list = listOf(this.actions);
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) == action) {
				list.remove(i);
				this.actions = shrunk(list);
				return;
			}
		}
	}

	public void clearActions() {
		this.actions = null;
	}

	public int addCondition(final TriggerBooleanExpression boolexpr) {
		final int index = sizeOf(this.conditions);
		this.conditions = with(this.conditions, boolexpr);
		return index;
	}

	public void removeCondition(final TriggerBooleanExpression boolexpr) {
		final List<Object> list = listOf(this.conditions);
		if (list.remove(boolexpr)) {
			this.conditions = shrunk(list);
		}
	}

	public void removeConditionAtIndex(final int conditionIndex) {
		final List<Object> list = listOf(this.conditions);
		list.remove(conditionIndex);
		this.conditions = shrunk(list);
	}

	public void clearConditions() {
		this.conditions = null;
	}

	private static int sizeOf(final Object slot) {
		if (slot == null) {
			return 0;
		}
		return slot instanceof List ? ((List<?>) slot).size() : 1;
	}

	private static Object at(final Object slot, final int index) {
		if (slot instanceof List) {
			return ((List<?>) slot).get(index);
		}
		if ((slot == null) || (index != 0)) {
			throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + sizeOf(slot));
		}
		return slot;
	}

	private static Object with(final Object slot, final Object element) {
		if ((slot == null) && (element != null)) {
			return element;
		}
		final List<Object> list = listOf(slot);
		list.add(element);
		return list;
	}

	/** The elements as a list that may be changed, which then replaces the slot through {@link #shrunk}. */
	@SuppressWarnings("unchecked")
	private static List<Object> listOf(final Object slot) {
		if (slot instanceof ArrayList) {
			return (List<Object>) slot;
		}
		final List<Object> list = new ArrayList<>(2);
		if (slot instanceof List) {
			list.addAll((List<Object>) slot);
		}
		else if (slot != null) {
			list.add(slot);
		}
		return list;
	}

	private static Object shrunk(final List<Object> list) {
		if (list.isEmpty()) {
			return null;
		}
		if ((list.size() == 1) && (list.get(0) != null)) {
			return list.get(0);
		}
		return list;
	}

	/** A copy to walk while the trigger changes. */
	@SuppressWarnings("unchecked")
	private static <T> List<T> copyOf(final Object slot) {
		if (slot == null) {
			return Collections.emptyList();
		}
		if (slot instanceof List) {
			return new ArrayList<>((List<T>) slot);
		}
		return Collections.singletonList((T) slot);
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
		for (final TriggerBooleanExpression condition : Trigger.<TriggerBooleanExpression>copyOf(this.conditions)) {
			// A null boolexpr evaluates false.
			if ((condition == null) || !condition.evaluate(globalScope, triggerScope)) {
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
		if (this.actions == null) {
			return null;
		}
		final List<JassFunction> copy = Trigger.<JassFunction>copyOf(this.actions);
		if (copy.isEmpty()) {
			return null;
		}
		final ActionsThread thread = new ActionsThread(globalScope, triggerScope, copy);
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
		for (final RemovableTriggerEvent event : Trigger.<RemovableTriggerEvent>copyOf(this.events)) {
			event.remove();
		}
		this.events = null;
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

	@Override
	public void setHandleId(final int handleId) {
		this.handleId = handleId;
	}

	@Override
	public boolean releaseHandle() {
		if (this.handleReleased) {
			return false;
		}
		this.handleReleased = true;
		return true;
	}

	@Override
	public void keepHandleId() {
		this.handleIdKept = true;
	}

	@Override
	public boolean isHandleIdKept() {
		return this.handleIdKept;
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

	public TriggerExecutionScope getTriggerExecutionScope() {
		if (this.triggerExecutionScope == null) {
			this.triggerExecutionScope = new TriggerExecutionScope(this);
		}
		return this.triggerExecutionScope;
	}

	public void removeEvent(final RemovableTriggerEvent evt) {
		if (evt != null) {
			evt.remove();
			final List<Object> list = listOf(this.events);
			if (list.remove(evt)) {
				this.events = shrunk(list);
			}
		}
	}

	@Override
	public int changeReferenceCount(final int delta) {
		this.referenceCount += delta;
		return this.referenceCount;
	}

	@Override
	public int getReferenceCount() {
		return this.referenceCount;
	}
}
