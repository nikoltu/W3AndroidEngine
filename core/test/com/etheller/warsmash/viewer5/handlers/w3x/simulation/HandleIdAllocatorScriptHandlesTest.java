package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.util.AllocatedHandle;

class HandleIdAllocatorScriptHandlesTest {
	private static final class Handle extends AllocatedHandle {
	}

	@Test
	void handlesAskedForTheirIdAreFoundByIdUntilTheIdIsFreed() {
		final HandleIdAllocator allocator = new HandleIdAllocator();
		final Handle[] handles = new Handle[2000];
		for (int i = 0; i < handles.length; i++) {
			handles[i] = allocator.assign(new Handle());
			allocator.handleIdOf(handles[i]);
		}
		for (final Handle handle : handles) {
			assertSame(handle, allocator.handleOfId(handle.getHandleId(), null));
		}
		allocator.release(handles[10]);
		allocator.endStep();
		assertNull(allocator.handleOfId(handles[10].getHandleId(), null));
		assertSame(handles[11], allocator.handleOfId(handles[11].getHandleId(), null));
	}

	@Test
	void aSaveFromBeforeTheCompactTableFillsItOnFirstUse() throws Exception {
		final HandleIdAllocator allocator = new HandleIdAllocator();
		final Handle handle = allocator.assign(new Handle());
		final Map<Long, Object> legacy = new HashMap<>();
		legacy.put((long) handle.getHandleId(), handle);
		final Field field = HandleIdAllocator.class.getDeclaredField("scriptHandles");
		field.setAccessible(true);
		field.set(allocator, legacy);
		assertSame(handle, allocator.handleOfId(handle.getHandleId(), null));
		assertNull(field.get(allocator));
	}
}
