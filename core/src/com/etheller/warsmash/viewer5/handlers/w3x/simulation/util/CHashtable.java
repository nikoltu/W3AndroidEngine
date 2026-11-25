package com.etheller.warsmash.viewer5.handlers.w3x.simulation.util;

import java.util.Arrays;
import java.util.Map;

import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.interpreter.ast.util.HandleReferences;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;

/**
 * As in the original game, one key pair holds five separate values: an integer, a real, a boolean, a string and a
 * handle (every handle type shares the handle slot). FlushParentHashtable destroys the table: later saves are
 * lost (measured on 1.29).
 *
 * A script can fill a table with millions of entries, so the keys are plain ints in open-addressing arrays and a
 * number, real or boolean is stored as itself, not as a boxed script value.
 */
public class CHashtable extends AllocatedHandle {
	public static final int INTEGER = 0;
	public static final int REAL = 1;
	public static final int BOOLEAN = 2;
	public static final int STRING = 3;
	public static final int HANDLE = 4;

	private static final int BOOLEAN_VALUE = 1 << 5;
	private static final int PRESENT = (1 << 5) - 1;

	/** The parent keys and the child tables of a parent key; a slot is empty when its child table is null. */
	private int[] parentKeys;
	private Children[] children;
	private int parentCount;
	/** The nested maps of saves from before the arrays above: their entries move over when the table is next used. */
	private Map<Integer, Map<Integer, Object[]>> parentKeyToChildTable;
	/** The handle types saved so far; a saved handle names its type by the index here plus one, zero for none. */
	private HandleJassType[] handleTypes;
	private int handleTypeCount;
	private boolean destroyed;

	/**
	 * The entries of one parent key, in open-addressing arrays; an entry exists while at least one of its five slots
	 * does. Most parents have a few children, so the key, the flags and the integer share one array.
	 */
	private static final class Children {
		private static final int STRIDE = 3;
		/** Per entry: the child key, the flags and the integer. */
		private int[] data = new int[STRIDE];
		private double[] reals;
		private Object[] strings;
		/** The handle itself, or the script value when it is not a handle value; with its type's number in the next. */
		private Object[] handles;
		private short[] handleTypeIds;
		private int size;

		private int capacity() {
			return this.data.length / STRIDE;
		}

		/** Bit n of the flags: slot n holds a value; {@link CHashtable#BOOLEAN_VALUE}: the boolean is true. Zero is empty. */
		private int flags(final int index) {
			return this.data[(index * STRIDE) + 1];
		}

		private int find(final int key) {
			final int capacity = capacity();
			final int mask = capacity - 1;
			int i = hash(key) & mask;
			for (int probes = 0; probes < capacity; probes++, i = (i + 1) & mask) {
				if (this.data[(i * STRIDE) + 1] == 0) {
					return -1;
				}
				if (this.data[i * STRIDE] == key) {
					return i;
				}
			}
			return -1;
		}

		/** The index of the key's entry, new ones with no flags: the caller sets one at once. */
		private int findOrAdd(final int key) {
			int capacity = capacity();
			int mask = capacity - 1;
			int i = hash(key) & mask;
			for (int probes = 0; probes < capacity; probes++, i = (i + 1) & mask) {
				if (this.data[(i * STRIDE) + 1] == 0) {
					break;
				}
				if (this.data[i * STRIDE] == key) {
					return i;
				}
			}
			if (this.size + 1 > Math.max((capacity * 3) / 4, Math.min(capacity, 2))) {
				grow();
				capacity = capacity();
				mask = capacity - 1;
				i = hash(key) & mask;
				while (this.data[(i * STRIDE) + 1] != 0) {
					i = (i + 1) & mask;
				}
			}
			this.data[i * STRIDE] = key;
			this.size++;
			return i;
		}

		private void grow() {
			final int[] oldData = this.data;
			final int oldCapacity = capacity();
			final double[] oldReals = this.reals;
			final Object[] oldStrings = this.strings;
			final Object[] oldHandles = this.handles;
			final short[] oldHandleTypeIds = this.handleTypeIds;
			final int capacity = oldCapacity << 1;
			this.data = new int[capacity * STRIDE];
			this.reals = oldReals == null ? null : new double[capacity];
			this.strings = oldStrings == null ? null : new Object[capacity];
			this.handles = oldHandles == null ? null : new Object[capacity];
			this.handleTypeIds = oldHandleTypeIds == null ? null : new short[capacity];
			final int mask = capacity - 1;
			for (int i = 0; i < oldCapacity; i++) {
				if (oldData[(i * STRIDE) + 1] != 0) {
					int j = hash(oldData[i * STRIDE]) & mask;
					while (this.data[(j * STRIDE) + 1] != 0) {
						j = (j + 1) & mask;
					}
					System.arraycopy(oldData, i * STRIDE, this.data, j * STRIDE, STRIDE);
					if (oldReals != null) {
						this.reals[j] = oldReals[i];
					}
					if (oldStrings != null) {
						this.strings[j] = oldStrings[i];
					}
					if (oldHandles != null) {
						this.handles[j] = oldHandles[i];
						this.handleTypeIds[j] = oldHandleTypeIds[i];
					}
				}
			}
		}

		/** Removes the entry at the index, moving later entries of its probe run back over the gap. */
		private void removeAt(final int index) {
			final int capacity = capacity();
			final int mask = capacity - 1;
			int gap = index;
			for (int step = 1; step < capacity; step++) {
				final int next = (index + step) & mask;
				if (this.data[(next * STRIDE) + 1] == 0) {
					break;
				}
				final int home = hash(this.data[next * STRIDE]) & mask;
				if (((next - home) & mask) >= ((next - gap) & mask)) {
					System.arraycopy(this.data, next * STRIDE, this.data, gap * STRIDE, STRIDE);
					if (this.reals != null) {
						this.reals[gap] = this.reals[next];
					}
					if (this.strings != null) {
						this.strings[gap] = this.strings[next];
					}
					if (this.handles != null) {
						this.handles[gap] = this.handles[next];
						this.handleTypeIds[gap] = this.handleTypeIds[next];
					}
					gap = next;
				}
			}
			this.data[(gap * STRIDE) + 1] = 0;
			this.data[(gap * STRIDE) + 2] = 0;
			if (this.reals != null) {
				this.reals[gap] = 0;
			}
			if (this.strings != null) {
				this.strings[gap] = null;
			}
			if (this.handles != null) {
				this.handles[gap] = null;
				this.handleTypeIds[gap] = 0;
			}
			this.size--;
		}
	}

	public void save(final int slot, final int parentKey, final int childKey, final JassValue value) {
		migrate();
		if (this.destroyed) {
			return;
		}
		if (value == null) {
			remove(slot, parentKey, childKey);
			return;
		}
		// Converted first: nothing is added to the table when the value cannot be read.
		final long bits = bitsOf(slot, value);
		final Children table = childrenOrAdd(parentKey);
		store(table, table.findOrAdd(childKey), slot, value, bits, true);
	}

	private static long bitsOf(final int slot, final JassValue value) {
		switch (slot) {
		case INTEGER:
			return integerOf(value);
		case REAL:
			return Double.doubleToRawLongBits(realOf(value));
		case BOOLEAN:
			return booleanOf(value) ? 1 : 0;
		default:
			return 0;
		}
	}

	private void store(final Children table, final int index, final int slot, final JassValue value,
			final long bits, final boolean countHandle) {
		final int capacity = table.capacity();
		switch (slot) {
		case INTEGER:
			table.data[(index * Children.STRIDE) + 2] = (int) bits;
			break;
		case REAL:
			if (table.reals == null) {
				table.reals = new double[capacity];
			}
			table.reals[index] = Double.longBitsToDouble(bits);
			break;
		case BOOLEAN:
			table.data[(index * Children.STRIDE) + 1] = bits != 0 ? table.flags(index) | BOOLEAN_VALUE
					: table.flags(index) & ~BOOLEAN_VALUE;
			break;
		case STRING:
			if (table.strings == null) {
				table.strings = new Object[capacity];
			}
			table.strings[index] = value;
			break;
		default:
			if (table.handles == null) {
				table.handles = new Object[capacity];
				table.handleTypeIds = new short[capacity];
			}
			if (value instanceof HandleJassValue) {
				final Object handle = ((HandleJassValue) value).getJavaValue();
				if (countHandle) {
					HandleReferences.replace(table.handles[index], handle);
				}
				table.handles[index] = handle;
				table.handleTypeIds[index] = typeIdOf(((HandleJassValue) value).getType());
			}
			else {
				if (countHandle) {
					HandleReferences.replace(table.handles[index], value);
				}
				table.handles[index] = value;
				table.handleTypeIds[index] = 0;
			}
			break;
		}
		table.data[(index * Children.STRIDE) + 1] |= 1 << slot;
	}

	/** The saved value of the slot, or null when there is none. */
	public JassValue load(final int slot, final int parentKey, final int childKey) {
		migrate();
		final Children table = childrenOf(parentKey);
		if (table == null) {
			return null;
		}
		final int index = table.find(childKey);
		if ((index < 0) || ((table.flags(index) & (1 << slot)) == 0)) {
			return null;
		}
		switch (slot) {
		case INTEGER:
			return IntegerJassValue.of(table.data[(index * Children.STRIDE) + 2]);
		case REAL:
			return RealJassValue.of(table.reals[index]);
		case BOOLEAN:
			return BooleanJassValue.of((table.flags(index) & BOOLEAN_VALUE) != 0);
		case STRING:
			return (JassValue) table.strings[index];
		default:
			final int typeId = table.handleTypeIds[index];
			return typeId == 0 ? (JassValue) table.handles[index]
					: new HandleJassValue(this.handleTypes[typeId - 1], table.handles[index]);
		}
	}

	public boolean has(final int slot, final int parentKey, final int childKey) {
		migrate();
		final Children table = childrenOf(parentKey);
		if (table == null) {
			return false;
		}
		final int index = table.find(childKey);
		return (index >= 0) && ((table.flags(index) & (1 << slot)) != 0);
	}

	public void remove(final int slot, final int parentKey, final int childKey) {
		migrate();
		final Children table = childrenOf(parentKey);
		if (table == null) {
			return;
		}
		final int index = table.find(childKey);
		if ((index < 0) || ((table.flags(index) & (1 << slot)) == 0)) {
			return;
		}
		switch (slot) {
		case INTEGER:
			table.data[(index * Children.STRIDE) + 2] = 0;
			break;
		case REAL:
			table.reals[index] = 0;
			break;
		case BOOLEAN:
			table.data[(index * Children.STRIDE) + 1] &= ~BOOLEAN_VALUE;
			break;
		case STRING:
			table.strings[index] = null;
			break;
		default:
			HandleReferences.change(table.handles[index], -1);
			table.handles[index] = null;
			table.handleTypeIds[index] = 0;
			break;
		}
		table.data[(index * Children.STRIDE) + 1] &= ~(1 << slot);
		if ((table.flags(index) & PRESENT) == 0) {
			// Only the boolean value bit can be left, which an entry without values does not keep.
			table.data[(index * Children.STRIDE) + 1] = 0;
			table.removeAt(index);
			if (table.size == 0) {
				removeParent(parentKey);
			}
		}
	}

	public void flushChild(final int parentKey) {
		migrate();
		final Children table = childrenOf(parentKey);
		if (table != null) {
			removeParent(parentKey);
			dropHandleReferences(table);
		}
	}

	public void flushAll() {
		migrate();
		if (this.children != null) {
			for (final Children table : this.children) {
				if (table != null) {
					dropHandleReferences(table);
				}
			}
		}
		this.parentKeys = null;
		this.children = null;
		this.parentCount = 0;
		this.destroyed = true;
	}

	private static void dropHandleReferences(final Children table) {
		if (table.handles != null) {
			for (final Object handle : table.handles) {
				HandleReferences.change(handle, -1);
			}
		}
	}

	private short typeIdOf(final HandleJassType type) {
		for (int i = 0; i < this.handleTypeCount; i++) {
			if (this.handleTypes[i] == type) {
				return (short) (i + 1);
			}
		}
		if (this.handleTypes == null) {
			this.handleTypes = new HandleJassType[8];
		}
		else if (this.handleTypeCount == this.handleTypes.length) {
			this.handleTypes = Arrays.copyOf(this.handleTypes, this.handleTypeCount << 1);
		}
		this.handleTypes[this.handleTypeCount++] = type;
		return (short) this.handleTypeCount;
	}

	private static int integerOf(final JassValue value) {
		return value instanceof IntegerJassValue ? ((IntegerJassValue) value).getValue()
				: value.visit(IntegerJassValueVisitor.getInstance());
	}

	private static double realOf(final JassValue value) {
		return value instanceof RealJassValue ? ((RealJassValue) value).getValue()
				: value.visit(RealJassValueVisitor.getInstance());
	}

	private static boolean booleanOf(final JassValue value) {
		return value instanceof BooleanJassValue ? ((BooleanJassValue) value).getValue()
				: value.visit(BooleanJassValueVisitor.getInstance());
	}

	private Children childrenOf(final int parentKey) {
		if (this.parentCount == 0) {
			return null;
		}
		final int mask = this.parentKeys.length - 1;
		for (int i = hash(parentKey) & mask;; i = (i + 1) & mask) {
			final Children table = this.children[i];
			if (table == null) {
				return null;
			}
			if (this.parentKeys[i] == parentKey) {
				return table;
			}
		}
	}

	private Children childrenOrAdd(final int parentKey) {
		if (this.parentKeys == null) {
			this.parentKeys = new int[4];
			this.children = new Children[4];
		}
		int mask = this.parentKeys.length - 1;
		int i = hash(parentKey) & mask;
		while (this.children[i] != null) {
			if (this.parentKeys[i] == parentKey) {
				return this.children[i];
			}
			i = (i + 1) & mask;
		}
		if ((this.parentCount + 1) * 4 > this.parentKeys.length * 3) {
			growParents();
			mask = this.parentKeys.length - 1;
			i = hash(parentKey) & mask;
			while (this.children[i] != null) {
				i = (i + 1) & mask;
			}
		}
		final Children table = new Children();
		this.parentKeys[i] = parentKey;
		this.children[i] = table;
		this.parentCount++;
		return table;
	}

	private void growParents() {
		final int[] oldKeys = this.parentKeys;
		final Children[] oldChildren = this.children;
		final int capacity = oldKeys.length << 1;
		this.parentKeys = new int[capacity];
		this.children = new Children[capacity];
		final int mask = capacity - 1;
		for (int i = 0; i < oldKeys.length; i++) {
			if (oldChildren[i] != null) {
				int j = hash(oldKeys[i]) & mask;
				while (this.children[j] != null) {
					j = (j + 1) & mask;
				}
				this.parentKeys[j] = oldKeys[i];
				this.children[j] = oldChildren[i];
			}
		}
	}

	private void removeParent(final int parentKey) {
		final int mask = this.parentKeys.length - 1;
		int i = hash(parentKey) & mask;
		while (this.parentKeys[i] != parentKey || this.children[i] == null) {
			i = (i + 1) & mask;
		}
		int gap = i;
		int next = (i + 1) & mask;
		while (this.children[next] != null) {
			final int home = hash(this.parentKeys[next]) & mask;
			if (((next - home) & mask) >= ((next - gap) & mask)) {
				this.parentKeys[gap] = this.parentKeys[next];
				this.children[gap] = this.children[next];
				gap = next;
			}
			next = (next + 1) & mask;
		}
		this.children[gap] = null;
		this.parentCount--;
		if (this.parentCount == 0) {
			this.parentKeys = null;
			this.children = null;
		}
	}

	/** Moves the entries of a save from before the arrays into them. */
	private void migrate() {
		final Map<Integer, Map<Integer, Object[]>> legacy = this.parentKeyToChildTable;
		if (legacy == null) {
			return;
		}
		this.parentKeyToChildTable = null;
		for (final Map.Entry<Integer, Map<Integer, Object[]>> parent : legacy.entrySet()) {
			for (final Map.Entry<Integer, Object[]> child : parent.getValue().entrySet()) {
				final Object[] values = child.getValue();
				for (int slot = 0; slot < values.length; slot++) {
					if (values[slot] != null) {
						final JassValue value = (JassValue) values[slot];
						final Children table = childrenOrAdd(parent.getKey());
						// What counted the handles references was saved with them.
						store(table, table.findOrAdd(child.getKey()), slot, value, bitsOf(slot, value), false);
					}
				}
			}
		}
	}

	private static int hash(final int key) {
		return key ^ (key >>> 16);
	}
}
