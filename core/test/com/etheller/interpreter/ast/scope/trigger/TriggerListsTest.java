package com.etheller.interpreter.ast.scope.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.function.JassFunction;

class TriggerListsTest {
	private static final class Counting implements TriggerBooleanExpression {
		private final List<Counting> order;
		private final boolean result;

		private Counting(final List<Counting> order, final boolean result) {
			this.order = order;
			this.result = result;
		}

		@Override
		public boolean evaluate(final com.etheller.interpreter.ast.scope.GlobalScope globalScope,
				final com.etheller.interpreter.ast.scope.TriggerExecutionScope triggerScope) {
			this.order.add(this);
			return this.result;
		}
	}

	private static final class Removal extends RemovableTriggerEvent {
		private int removed;

		private Removal(final Trigger trigger) {
			super(trigger);
		}

		@Override
		public void remove() {
			this.removed++;
		}
	}

	@Test
	void conditionsKeepTheirIndexesAndAllRunInOrder() {
		final Trigger trigger = new Trigger();
		final List<Counting> order = new ArrayList<>();
		final Counting first = new Counting(order, false);
		final Counting second = new Counting(order, true);
		assertTrue(trigger.evaluate(null, null));
		assertEquals(0, trigger.addCondition(first));
		assertFalse(trigger.evaluate(null, null));
		assertEquals(1, trigger.addCondition(second));
		order.clear();
		assertFalse(trigger.evaluate(null, null));
		assertEquals(List.of(first, second), order);
		trigger.removeCondition(first);
		order.clear();
		assertTrue(trigger.evaluate(null, null));
		assertEquals(List.of(second), order);
		trigger.removeConditionAtIndex(0);
		assertTrue(trigger.evaluate(null, null));
		assertThrows(IndexOutOfBoundsException.class, () -> trigger.removeConditionAtIndex(0));
	}

	@Test
	void aNullConditionStaysACondition() {
		final Trigger trigger = new Trigger();
		assertEquals(0, trigger.addCondition(null));
		assertFalse(trigger.evaluate(null, null));
		final List<Counting> order = new ArrayList<>();
		assertEquals(1, trigger.addCondition(new Counting(order, true)));
		assertFalse(trigger.evaluate(null, null));
		assertEquals(1, order.size());
		trigger.removeConditionAtIndex(0);
		assertTrue(trigger.evaluate(null, null));
		trigger.clearConditions();
		assertTrue(trigger.evaluate(null, null));
	}

	@Test
	void actionsAreFoundByIndexAndRemovedByIdentity() {
		final Trigger trigger = new Trigger();
		final JassFunction a = (arguments, globalScope, triggerScope) -> null;
		final JassFunction b = (arguments, globalScope, triggerScope) -> null;
		assertEquals(0, trigger.addAction(a));
		assertSame(a, trigger.getActionAt(0));
		assertEquals(1, trigger.addAction(b));
		assertSame(b, trigger.getActionAt(1));
		trigger.removeAction(a);
		assertSame(b, trigger.getActionAt(0));
		assertThrows(IndexOutOfBoundsException.class, () -> trigger.getActionAt(1));
		trigger.removeAction(a);
		trigger.clearActions();
		assertThrows(IndexOutOfBoundsException.class, () -> trigger.getActionAt(0));
		assertEquals(0, trigger.addAction(a));
	}

	@Test
	void destroyingARemovesEveryEventOnce() {
		final Trigger trigger = new Trigger();
		final Removal first = new Removal(trigger);
		final Removal second = new Removal(trigger);
		final Removal third = new Removal(trigger);
		trigger.removeEvent(second);
		assertEquals(1, second.removed);
		trigger.destroy();
		assertEquals(1, first.removed);
		assertEquals(1, third.removed);
		assertEquals(1, second.removed);
		trigger.destroy();
		assertEquals(1, first.removed);
	}
}
