package com.etheller.warsmash.viewer5.handlers.w3x.simulation.util;

import java.util.Arrays;

/**
 * The handles a script asked the id of, by pool and id. Handles of the main pool are numbered one after another from
 * a first id, so they sit in an array indexed by their id; everything else (the small pools, ids a script made up) is
 * in a map. A script can ask for millions of them.
 */
public final class ScriptHandleTable {
	private static final int DENSE_POOL = 0;
	private static final int MAX_DENSE = 1 << 26;

	private final int firstDenseId;
	private Object[] dense;
	private LongObjectMap sparse;

	public ScriptHandleTable(final int firstDenseId) {
		this.firstDenseId = firstDenseId;
	}

	public Object get(final int pool, final int id) {
		if (isDense(pool, id)) {
			final int index = id - this.firstDenseId;
			return (this.dense != null) && (index < this.dense.length) ? this.dense[index] : null;
		}
		return this.sparse == null ? null : this.sparse.get(key(pool, id));
	}

	public void put(final int pool, final int id, final Object handle) {
		if (isDense(pool, id)) {
			final int index = id - this.firstDenseId;
			if (handle == null) {
				remove(pool, id);
				return;
			}
			if (this.dense == null) {
				this.dense = new Object[Math.max(1024, index + 1)];
			}
			else if (index >= this.dense.length) {
				this.dense = Arrays.copyOf(this.dense, Math.max(index + 1, this.dense.length << 1));
			}
			this.dense[index] = handle;
			return;
		}
		if (this.sparse == null) {
			if (handle == null) {
				return;
			}
			this.sparse = new LongObjectMap();
		}
		this.sparse.put(key(pool, id), handle);
	}

	public void remove(final int pool, final int id) {
		if (isDense(pool, id)) {
			final int index = id - this.firstDenseId;
			if ((this.dense != null) && (index < this.dense.length)) {
				this.dense[index] = null;
			}
		}
		else if (this.sparse != null) {
			this.sparse.remove(key(pool, id));
		}
	}

	private boolean isDense(final int pool, final int id) {
		return (pool == DENSE_POOL) && (id >= this.firstDenseId) && (id - this.firstDenseId < MAX_DENSE);
	}

	private static long key(final int pool, final int id) {
		return (((long) pool) << 32) | (id & 0xFFFFFFFFL);
	}
}
