package com.etheller.warsmash.viewer5.handlers.w3x.simulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedObjectGraph;

class CHashtableTest {
	private static final class Handle extends AllocatedHandle {
	}

	private static final class NoRules implements SavedObjectGraph.Rules {
		@Override
		public String externalName(final Object object) {
			return null;
		}

		@Override
		public boolean isInPlace(final Object object) {
			return false;
		}

		@Override
		public boolean isDropped(final Object object) {
			return false;
		}

		@Override
		public Object replace(final Object object) {
			return object;
		}

		@Override
		public Object resolveExternal(final String name) {
			return null;
		}

		@Override
		public void reportDropped(final Class<?> holder, final String field, final Object object) {
		}
	}

	@Test
	void everySlotKeepsItsOwnValueUnderOneKeyPair() {
		final CHashtable table = new CHashtable();
		final Handle handle = new Handle();
		final HandleJassValue handleValue = new HandleJassValue(null, handle);
		table.save(CHashtable.INTEGER, -7, 3, IntegerJassValue.of(123456));
		table.save(CHashtable.REAL, -7, 3, RealJassValue.of(1.5));
		table.save(CHashtable.BOOLEAN, -7, 3, BooleanJassValue.TRUE);
		table.save(CHashtable.STRING, -7, 3, StringJassValue.of("abc"));
		table.save(CHashtable.HANDLE, -7, 3, handleValue);
		assertEquals(123456, ((IntegerJassValue) table.load(CHashtable.INTEGER, -7, 3)).getValue());
		assertEquals(1.5, ((RealJassValue) table.load(CHashtable.REAL, -7, 3)).getValue());
		assertTrue(((BooleanJassValue) table.load(CHashtable.BOOLEAN, -7, 3)).getValue());
		assertEquals("abc", ((StringJassValue) table.load(CHashtable.STRING, -7, 3)).getValue());
		assertSame(handle, ((HandleJassValue) table.load(CHashtable.HANDLE, -7, 3)).getJavaValue());
		assertEquals(1, handle.getReferenceCount());
		table.save(CHashtable.BOOLEAN, -7, 3, BooleanJassValue.FALSE);
		assertTrue(table.has(CHashtable.BOOLEAN, -7, 3));
		assertFalse(((BooleanJassValue) table.load(CHashtable.BOOLEAN, -7, 3)).getValue());
		table.remove(CHashtable.HANDLE, -7, 3);
		assertEquals(0, handle.getReferenceCount());
		assertNull(table.load(CHashtable.HANDLE, -7, 3));
		assertTrue(table.has(CHashtable.INTEGER, -7, 3));
		assertFalse(table.has(CHashtable.INTEGER, -7, 4));
		assertFalse(table.has(CHashtable.INTEGER, 8, 3));
	}

	@Test
	void anEntryGoesWhenItsLastSlotDoes() {
		final CHashtable table = new CHashtable();
		table.save(CHashtable.INTEGER, 1, 1, IntegerJassValue.of(1));
		table.save(CHashtable.BOOLEAN, 1, 1, BooleanJassValue.TRUE);
		table.remove(CHashtable.INTEGER, 1, 1);
		assertTrue(table.has(CHashtable.BOOLEAN, 1, 1));
		table.remove(CHashtable.BOOLEAN, 1, 1);
		assertFalse(table.has(CHashtable.BOOLEAN, 1, 1));
		assertFalse(table.has(CHashtable.INTEGER, 1, 1));
		// A boolean saved again starts from what is saved now, not from the value it had.
		table.save(CHashtable.BOOLEAN, 1, 1, BooleanJassValue.FALSE);
		assertFalse(((BooleanJassValue) table.load(CHashtable.BOOLEAN, 1, 1)).getValue());
	}

	@Test
	void aFlushedParentKeepsOtherKeysAndDropsHandleReferences() {
		final CHashtable table = new CHashtable();
		final Handle first = new Handle();
		final Handle second = new Handle();
		table.save(CHashtable.HANDLE, 5, 0, new HandleJassValue(null, first));
		table.save(CHashtable.HANDLE, 5, 1, new HandleJassValue(null, first));
		table.save(CHashtable.HANDLE, 6, 0, new HandleJassValue(null, second));
		assertEquals(2, first.getReferenceCount());
		table.flushChild(5);
		assertEquals(0, first.getReferenceCount());
		assertEquals(1, second.getReferenceCount());
		assertFalse(table.has(CHashtable.HANDLE, 5, 0));
		assertTrue(table.has(CHashtable.HANDLE, 6, 0));
		table.flushAll();
		assertEquals(0, second.getReferenceCount());
		table.save(CHashtable.INTEGER, 1, 1, IntegerJassValue.of(1));
		assertFalse(table.has(CHashtable.INTEGER, 1, 1));
	}

	@Test
	void behavesLikeNestedMapsThroughManyRandomChanges() {
		final Random random = new Random(7);
		final CHashtable table = new CHashtable();
		final Map<Long, Object[]> model = new HashMap<>();
		for (int step = 0; step < 200000; step++) {
			// A few parents with many children and many with few, keys of any sign.
			final int parent = random.nextInt(4) == 0 ? random.nextInt(30) - 10 : random.nextInt(3000) * 1024;
			final int child = random.nextInt(40) - 5;
			final int slot = random.nextInt(4);
			final long key = (((long) parent) << 32) | (child & 0xFFFFFFFFL);
			final int action = random.nextInt(10);
			if (action < 5) {
				final Object[] values = model.computeIfAbsent(key, k -> new Object[5]);
				final int number = random.nextInt();
				switch (slot) {
				case 0:
					table.save(slot, parent, child, IntegerJassValue.of(number));
					values[slot] = number;
					break;
				case 1:
					table.save(slot, parent, child, RealJassValue.of(number / 8.0));
					values[slot] = RealJassValue.of(number / 8.0).getValue();
					break;
				case 2:
					table.save(slot, parent, child, BooleanJassValue.of((number & 1) == 0));
					values[slot] = (number & 1) == 0;
					break;
				default:
					table.save(slot, parent, child, StringJassValue.of("s" + number));
					values[slot] = "s" + number;
					break;
				}
			}
			else if (action < 8) {
				table.remove(slot, parent, child);
				final Object[] values = model.get(key);
				if (values != null) {
					values[slot] = null;
					boolean any = false;
					for (final Object value : values) {
						any |= value != null;
					}
					if (!any) {
						model.remove(key);
					}
				}
			}
			else if (random.nextInt(50) == 0) {
				table.flushChild(parent);
				model.keySet().removeIf(k -> (int) (k >> 32) == parent);
			}
			final Object[] expected = model.get(key);
			for (int check = 0; check < 4; check++) {
				final Object want = expected == null ? null : expected[check];
				final JassValue got = table.load(check, parent, child);
				assertEquals(want != null, table.has(check, parent, child), "step " + step);
				if (want == null) {
					assertNull(got, "step " + step);
				}
				else if (check == 0) {
					assertEquals(want, ((IntegerJassValue) got).getValue());
				}
				else if (check == 1) {
					assertEquals(want, ((RealJassValue) got).getValue());
				}
				else if (check == 2) {
					assertEquals(want, ((BooleanJassValue) got).getValue());
				}
				else {
					assertEquals(want, ((StringJassValue) got).getValue());
				}
			}
		}
		for (final Map.Entry<Long, Object[]> entry : model.entrySet()) {
			final int parent = (int) (entry.getKey() >> 32);
			final int child = (int) (long) entry.getKey();
			for (int slot = 0; slot < 4; slot++) {
				assertEquals(entry.getValue()[slot] != null, table.has(slot, parent, child));
			}
		}
	}

	@Test
	void entriesSurviveASavedGameRoundTrip() throws IOException {
		final CHashtable table = new CHashtable();
		final Handle handle = new Handle();
		for (int i = 0; i < 500; i++) {
			table.save(CHashtable.INTEGER, i * 1024, i % 7, IntegerJassValue.of(i));
		}
		table.save(CHashtable.REAL, 1, 2, RealJassValue.of(2.25));
		table.save(CHashtable.STRING, 1, 2, StringJassValue.of("x"));
		table.save(CHashtable.BOOLEAN, 1, 2, BooleanJassValue.TRUE);
		table.save(CHashtable.HANDLE, 1, 2, new HandleJassValue(null, handle));
		final CHashtable loaded = (CHashtable) roundTrip(table);
		assertNotSame(table, loaded);
		for (int i = 0; i < 500; i++) {
			assertEquals(i, ((IntegerJassValue) loaded.load(CHashtable.INTEGER, i * 1024, i % 7)).getValue());
		}
		assertEquals(2.25, ((RealJassValue) loaded.load(CHashtable.REAL, 1, 2)).getValue());
		assertEquals("x", ((StringJassValue) loaded.load(CHashtable.STRING, 1, 2)).getValue());
		assertTrue(((BooleanJassValue) loaded.load(CHashtable.BOOLEAN, 1, 2)).getValue());
		assertNotSame(handle, ((HandleJassValue) loaded.load(CHashtable.HANDLE, 1, 2)).getJavaValue());
		loaded.flushAll();
		assertFalse(loaded.has(CHashtable.INTEGER, 0, 0));
	}

	@Test
	void aSaveFromBeforeTheArraysFillsThemOnFirstUse() throws Exception {
		final Handle handle = new Handle();
		handle.changeReferenceCount(1);
		final Map<Integer, Map<Integer, Object[]>> legacy = new HashMap<>();
		final Map<Integer, Object[]> children = new HashMap<>();
		final Object[] values = new Object[5];
		values[CHashtable.INTEGER] = IntegerJassValue.of(99);
		values[CHashtable.STRING] = StringJassValue.of(null);
		values[CHashtable.BOOLEAN] = BooleanJassValue.TRUE;
		values[CHashtable.HANDLE] = new HandleJassValue(null, handle);
		children.put(4, values);
		legacy.put(0x100005, children);
		final CHashtable table = new CHashtable();
		final Field field = CHashtable.class.getDeclaredField("parentKeyToChildTable");
		field.setAccessible(true);
		field.set(table, legacy);
		assertEquals(99, ((IntegerJassValue) table.load(CHashtable.INTEGER, 0x100005, 4)).getValue());
		assertNull(((StringJassValue) table.load(CHashtable.STRING, 0x100005, 4)).getValue());
		assertTrue(table.has(CHashtable.BOOLEAN, 0x100005, 4));
		assertFalse(table.has(CHashtable.REAL, 0x100005, 4));
		// The reference was counted when it was saved, not again when it moves.
		assertEquals(1, handle.getReferenceCount());
		table.flushChild(0x100005);
		assertEquals(0, handle.getReferenceCount());
		assertNull(field.get(table));
	}

	private static Object roundTrip(final Object root) throws IOException {
		final ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
		final SavedObjectGraph.Writer writer;
		try (DataOutputStream bodies = new DataOutputStream(bodyBytes)) {
			writer = new SavedObjectGraph.Writer(new NoRules(), bodies);
			writer.writeRoot(root);
		}
		final byte[] table = writer.finishTable();
		SavedObjectGraph.checkClassesMatch(table);
		try (DataInputStream bodies = new DataInputStream(new ByteArrayInputStream(bodyBytes.toByteArray()))) {
			return new SavedObjectGraph.Reader(new NoRules(), table, bodies).readRoot();
		}
	}
}
