package com.etheller.warsmash.viewer5.handlers.w3x.simulation.util;

/**
 * A map from long keys to objects in two flat arrays, for tables that hold millions of entries (script handles by
 * id). A slot whose value is null is empty, so a null value means no entry. Probing is linear and removing shifts the
 * entries behind back, so the arrays never hold more than the live entries and their gaps.
 */
public final class LongObjectMap {
	private long[] keys;
	private Object[] values;
	private int size;

	public int size() {
		return this.size;
	}

	public Object get(final long key) {
		if (this.size == 0) {
			return null;
		}
		final int mask = this.keys.length - 1;
		for (int i = hash(key) & mask;; i = (i + 1) & mask) {
			final Object value = this.values[i];
			if (value == null) {
				return null;
			}
			if (this.keys[i] == key) {
				return value;
			}
		}
	}

	public void put(final long key, final Object value) {
		if (value == null) {
			remove(key);
			return;
		}
		if (this.keys == null) {
			this.keys = new long[8];
			this.values = new Object[8];
		}
		int mask = this.keys.length - 1;
		int i = hash(key) & mask;
		while (this.values[i] != null) {
			if (this.keys[i] == key) {
				this.values[i] = value;
				return;
			}
			i = (i + 1) & mask;
		}
		if ((this.size + 1) * 4 > this.keys.length * 3) {
			grow();
			mask = this.keys.length - 1;
			i = hash(key) & mask;
			while (this.values[i] != null) {
				i = (i + 1) & mask;
			}
		}
		this.keys[i] = key;
		this.values[i] = value;
		this.size++;
	}

	public void remove(final long key) {
		if (this.size == 0) {
			return;
		}
		final int mask = this.keys.length - 1;
		int i = hash(key) & mask;
		while (true) {
			if (this.values[i] == null) {
				return;
			}
			if (this.keys[i] == key) {
				break;
			}
			i = (i + 1) & mask;
		}
		// Backward shift: an entry moves into the gap when its probe path crosses it.
		int gap = i;
		int next = (i + 1) & mask;
		while (this.values[next] != null) {
			final int home = hash(this.keys[next]) & mask;
			if (((next - home) & mask) >= ((next - gap) & mask)) {
				this.keys[gap] = this.keys[next];
				this.values[gap] = this.values[next];
				gap = next;
			}
			next = (next + 1) & mask;
		}
		this.values[gap] = null;
		this.size--;
		if (this.size == 0) {
			this.keys = null;
			this.values = null;
		}
	}

	private void grow() {
		final long[] oldKeys = this.keys;
		final Object[] oldValues = this.values;
		final int capacity = oldKeys.length << 1;
		this.keys = new long[capacity];
		this.values = new Object[capacity];
		final int mask = capacity - 1;
		for (int i = 0; i < oldKeys.length; i++) {
			if (oldValues[i] != null) {
				int j = hash(oldKeys[i]) & mask;
				while (this.values[j] != null) {
					j = (j + 1) & mask;
				}
				this.keys[j] = oldKeys[i];
				this.values[j] = oldValues[i];
			}
		}
	}

	private static int hash(final long key) {
		final int h = (int) (key ^ (key >>> 32));
		return h ^ (h >>> 16);
	}
}
