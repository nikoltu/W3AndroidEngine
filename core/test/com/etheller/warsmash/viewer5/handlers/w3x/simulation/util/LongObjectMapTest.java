package com.etheller.warsmash.viewer5.handlers.w3x.simulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class LongObjectMapTest {
	@Test
	void anEmptyMapFindsNothing() {
		final LongObjectMap map = new LongObjectMap();
		assertNull(map.get(0));
		map.remove(5);
		assertEquals(0, map.size());
	}

	@Test
	void aNullValueIsNoEntry() {
		final LongObjectMap map = new LongObjectMap();
		map.put(3, "a");
		map.put(3, null);
		assertNull(map.get(3));
		assertEquals(0, map.size());
	}

	@Test
	void behavesLikeAHashMapThroughManyRandomChanges() {
		final Random random = new Random(11);
		final LongObjectMap map = new LongObjectMap();
		final Map<Long, Object> model = new HashMap<>();
		for (int step = 0; step < 300000; step++) {
			// Ids in runs, as handles are numbered, in four pools, with gaps from freed ones.
			final long key = (((long) random.nextInt(4)) << 32) | (0x100000 + random.nextInt(5000));
			if (random.nextInt(3) == 0) {
				map.remove(key);
				model.remove(key);
			}
			else {
				final Object value = new Object();
				map.put(key, value);
				model.put(key, value);
			}
			assertEquals(model.size(), map.size());
			assertSame(model.get(key), map.get(key));
		}
		for (final Map.Entry<Long, Object> entry : model.entrySet()) {
			assertSame(entry.getValue(), map.get(entry.getKey()));
		}
		for (final Long key : new ArrayList<>(model.keySet())) {
			map.remove(key);
			model.remove(key);
			assertNull(map.get(key));
		}
		assertEquals(0, map.size());
	}
}
