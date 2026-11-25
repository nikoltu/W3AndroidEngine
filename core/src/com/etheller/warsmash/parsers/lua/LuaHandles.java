package com.etheller.warsmash.parsers.lua;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.etheller.interpreter.ast.util.CAllocatedHandle;
import com.etheller.interpreter.ast.util.CHandle;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;

/**
 * The engine objects a Lua script holds. A script sees one value per object (a light userdata carrying the number
 * given here), so handles compare equal and work as table keys, as in the game. The numbers follow the order the
 * script first met the objects, which is the same on every run, and a number is never given to a second object.
 *
 * The script holds an object until the game destroys it. The number then stays with the script, which may still hold
 * it, but names no object any more: natives take it as a null handle, and GetHandleId still gives the id the object
 * had.
 */
final class LuaHandles {
	private final List<HandleJassValue> values = new ArrayList<>();
	private final Map<Object, Integer> ids = new IdentityHashMap<>();
	/** The handle id each released number had; 0 for a number that is held or whose object had none. */
	private int[] releasedIds = new int[256];
	/** One null handle per type stands in the list for the objects of that type that were destroyed. */
	private final Map<HandleJassType, HandleJassValue> destroyed = new IdentityHashMap<>();
	/** The number a destroyed object had, by its handle id: ids of held objects are never given again. */
	private final Map<Integer, Integer> releasedByHandleId = new HashMap<>();
	private int live;

	/** The number of the object, given on first use; the object is kept until the game destroys it. */
	long idOf(final HandleJassValue handle) {
		final Object javaValue = handle.getJavaValue();
		Integer id = this.ids.get(javaValue);
		if (id == null) {
			if (javaValue instanceof CHandle) {
				// A destroyed object the game still hands out (a corpse in a group) keeps the number it had.
				final Integer before = this.releasedByHandleId.get(((CHandle) javaValue).getHandleId());
				if (before != null) {
					return before;
				}
			}
			this.values.add(handle);
			this.live++;
			// A script that holds an object holds it for good, so its id is never given to another one.
			if (javaValue instanceof CAllocatedHandle) {
				((CAllocatedHandle) javaValue).keepHandleId();
			}
			id = this.values.size();
			this.ids.put(javaValue, id);
		}
		return id;
	}

	/** The handle with this number, or null for none (a number whose object was destroyed has none). */
	HandleJassValue get(final long id) {
		if ((id < 1) || (id > this.values.size())) {
			return null;
		}
		final HandleJassValue handle = this.values.get((int) id - 1);
		return handle == null || handle.getJavaValue() == null ? null : handle;
	}

	/** The handle with this number, a null handle of its type once the object is destroyed; null for no such number. */
	HandleJassValue valueOf(final long id) {
		return (id >= 1) && (id <= this.values.size()) ? this.values.get((int) id - 1) : null;
	}

	/** The same object as a value of the type, if the type accepts it. */
	HandleJassValue as(final long id, final HandleJassType type) {
		final HandleJassValue handle = get(id);
		if ((handle == null) || type.isAssignableFrom(handle.getType())) {
			return handle;
		}
		return null;
	}

	/** True for a number that was given to an object the game has destroyed since. */
	boolean isReleased(final long id) {
		return (id >= 1) && (id <= this.values.size()) && (get(id) == null);
	}

	/** The handle id the destroyed object of the number had, 0 if unknown. */
	int releasedHandleId(final long id) {
		return (id >= 1) && (id <= this.releasedIds.length) ? this.releasedIds[(int) id - 1] : 0;
	}

	/**
	 * The game destroyed the object: the script no longer holds it. Its number stays taken.
	 *
	 * @return true if the script held the object
	 */
	boolean release(final Object javaValue) {
		final Integer id = this.ids.remove(javaValue);
		if (id == null) {
			return false;
		}
		final HandleJassValue held = this.values.get(id - 1);
		// The type stays, so a saved game and the natives still know what the number was.
		this.values.set(id - 1, this.destroyed.computeIfAbsent(held.getType(), type -> new HandleJassValue(type, null)));
		if (id > this.releasedIds.length) {
			this.releasedIds = Arrays.copyOf(this.releasedIds, Math.max(id, this.releasedIds.length * 2));
		}
		final int handleId = javaValue instanceof CHandle ? ((CHandle) javaValue).getHandleId() : 0;
		this.releasedIds[id - 1] = handleId;
		if (handleId > 0) {
			this.releasedByHandleId.put(handleId, id);
		}
		this.live--;
		return true;
	}

	/** The handles in the order of their numbers; a destroyed object's place holds a null handle of its type. */
	List<HandleJassValue> snapshot() {
		return new ArrayList<>(this.values);
	}

	/** The handle ids of the destroyed objects by number, for a save. */
	int[] snapshotReleasedIds() {
		return Arrays.copyOf(this.releasedIds, this.values.size());
	}

	/** Takes the handles of a saved game; their numbers are the positions in the list. */
	void restore(final List<HandleJassValue> saved, final int[] savedReleasedIds) {
		this.values.clear();
		this.ids.clear();
		this.values.addAll(saved);
		this.live = 0;
		for (int i = 0; i < saved.size(); i++) {
			final Object javaValue = saved.get(i).getJavaValue();
			if (javaValue != null) {
				this.ids.put(javaValue, i + 1);
				this.live++;
			}
		}
		this.releasedIds = new int[Math.max(256, saved.size())];
		this.releasedByHandleId.clear();
		if (savedReleasedIds != null) {
			System.arraycopy(savedReleasedIds, 0, this.releasedIds, 0,
					Math.min(savedReleasedIds.length, this.releasedIds.length));
			for (int i = 0; i < saved.size(); i++) {
				if (this.releasedIds[i] > 0) {
					this.releasedByHandleId.put(this.releasedIds[i], i + 1);
				}
			}
		}
	}

	/** The numbers given so far, destroyed objects included. */
	int size() {
		return this.values.size();
	}

	/** The held objects by handle type, the largest groups first; for the memory log. */
	String heldByType() {
		final Map<String, Integer> counts = new HashMap<>();
		for (final HandleJassValue handle : this.values) {
			if ((handle != null) && (handle.getJavaValue() != null)) {
				counts.merge(handle.getType().getName(), 1, Integer::sum);
			}
		}
		final List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
		sorted.sort((a, b) -> b.getValue() - a.getValue());
		final StringBuilder text = new StringBuilder();
		for (int i = 0; (i < sorted.size()) && (i < 6); i++) {
			text.append(i == 0 ? "" : ", ").append(sorted.get(i).getKey()).append(' ').append(sorted.get(i).getValue());
		}
		return text.toString();
	}

	/** The objects the script holds now. */
	int liveSize() {
		return this.live;
	}
}
