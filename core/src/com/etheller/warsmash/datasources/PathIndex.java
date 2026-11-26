package com.etheller.warsmash.datasources;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The positions of a list of paths, found by the path in any letter case and without the trailing separators the
 * listing ignores. It holds one table of positions and no copy of the paths: the entries are compared against the list
 * itself, which a game's data keeps anyway. A table of lower case copies took several times the memory of the list.
 */
final class PathIndex {
	private final List<String> paths;
	/**
	 * The position + 1 of each path in its slot, in the low bits, and the high bits of the path's hash above it: a path
	 * looked for is mostly missing, and telling it apart from the paths in the slots it passes by their hash spares
	 * reading those paths, which lie all over the memory.
	 */
	private final int[] table;
	private final int mask;
	private final int indexBits;
	private final int indexMask;
	private final int tagBits;
	/** The lower case form of the few paths that have characters other than ASCII; the others fold their letters. */
	private final Map<Integer, String> exotic = new HashMap<>();

	PathIndex(final List<String> paths) {
		this.paths = paths;
		int capacity = 16;
		while (capacity < (paths.size() * 2)) {
			capacity <<= 1;
		}
		this.table = new int[capacity];
		this.mask = capacity - 1;
		this.indexBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(paths.size()));
		this.indexMask = (int) ((1L << this.indexBits) - 1);
		this.tagBits = Math.max(0, 31 - this.indexBits);
		for (int i = 0; i < paths.size(); i++) {
			final String path = paths.get(i);
			final boolean plain = isAscii(path);
			final String form = plain ? path : path.toLowerCase(Locale.ROOT);
			if (!plain) {
				this.exotic.put(i, form);
			}
			final int mixed = mix(hash(null, form, form.length(), plain ? LETTERS : EXACT));
			final int tag = tag(mixed);
			int slot = mixed & this.mask;
			while (this.table[slot] != 0) {
				if (matches(this.table[slot], tag, null, form, form.length(), plain ? LETTERS : EXACT)) {
					break;
				}
				slot = (slot + 1) & this.mask;
			}
			if (this.table[slot] == 0) {
				this.table[slot] = (tag << this.indexBits) | (i + 1);
			}
		}
	}

	int size() {
		return this.paths.size();
	}

	/** @return the position of the first listed path that is this path, or -1 */
	int indexOf(final String path) {
		return indexOf(null, path);
	}

	/**
	 * @return the position of the listed path that is prefix + "\\" + path (just path for a null prefix), or -1. The
	 *         path may be as asked, not made lower case with backslashes: '/' and ':' in it count as backslashes.
	 */
	int indexOf(final String prefix, String path) {
		if (!isAscii(path)) {
			path = path.toLowerCase(Locale.US).replace('/', '\\').replace(':', '\\');
		}
		int end = ((prefix == null) ? 0 : prefix.length() + 1) + path.length();
		while ((end > 0) && isSeparator(charAt(prefix, path, end - 1), end - 1, prefix)) {
			end--;
		}
		String first = prefix;
		String second = path;
		int mode = QUERY;
		if (!isAscii(path) || ((prefix != null) && !isAscii(prefix))) {
			first = null;
			second = ((prefix == null) ? path : prefix + "\\" + path).substring(0, end).toLowerCase(Locale.ROOT);
			end = second.length();
			mode = EXACT;
		}
		return find(mix(hash(first, second, end, mode)), first, second, end, mode);
	}

	private int find(final int mixed, final String prefix, final String path, final int length, final int mode) {
		final int tag = tag(mixed);
		int slot = mixed & this.mask;
		while (true) {
			final int entry = this.table[slot];
			if (entry == 0) {
				return -1;
			}
			if (matches(entry, tag, prefix, path, length, mode)) {
				return (entry & this.indexMask) - 1;
			}
			slot = (slot + 1) & this.mask;
		}
	}

	private int tag(final int mixed) {
		return this.tagBits == 0 ? 0 : mixed >>> (32 - this.tagBits);
	}

	private boolean matches(final int entry, final int tag, final String prefix, final String path, final int length,
			final int mode) {
		return ((entry >>> this.indexBits) == tag) && matches((entry & this.indexMask) - 1, prefix, path, length, mode);
	}

	/**
	 * A path as {@link #indexOf(String, String)} hashes it, worked out in one pass so that it can be looked up under
	 * several prefixes: the hash of prefix + "\\" + path follows from the prefix's hash and the path's. A storage asks
	 * for every path under each of its prefixes, and the data sources over it ask again for each folder they add, so a
	 * map's textures and sounds took several passes over each name per prefix.
	 */
	static final class Query {
		final String path;
		/** False for a path the index looks up the long way: not plain ASCII, or nothing but separators. */
		final boolean plain;
		/** The length of the path without its trailing separators, its hash and 31 to the power of that length. */
		final int length;
		final int hash;
		final int power;
		/** The hash code of the whole path in lower case with backslashes, or {@link CascDataSource#NOT_ASCII}. */
		final int normalizedHash;

		Query(final String path) {
			this.path = path;
			int hash = 0;
			int power = 1;
			int keptHash = 0;
			int keptPower = 1;
			int kept = 0;
			boolean plain = true;
			final int length = path.length();
			for (int i = 0; i < length; i++) {
				char c = path.charAt(i);
				if (c >= 128) {
					plain = false;
					break;
				}
				final boolean separator = (c == '\\') || (c == '/') || (c == ':');
				if (separator) {
					c = '\\';
				}
				else if ((c >= 'A') && (c <= 'Z')) {
					c += 'a' - 'A';
				}
				hash = (31 * hash) + c;
				power *= 31;
				if (!separator) {
					keptHash = hash;
					keptPower = power;
					kept = i + 1;
				}
			}
			this.plain = plain && (kept > 0);
			this.length = kept;
			this.hash = keptHash;
			this.power = keptPower;
			this.normalizedHash = plain ? hash : CascDataSource.NOT_ASCII;
		}
	}

	/** A prefix with the hash of prefix + "\\" as {@link #indexOf(String, String)} hashes it. */
	static final class Prefix {
		final String text;
		final boolean plain;
		final int hash;

		Prefix(final String text) {
			this.text = text;
			this.plain = isAscii(text);
			int hash = 0;
			for (int k = 0; k < text.length(); k++) {
				hash = (31 * hash) + fold(text.charAt(k));
			}
			this.hash = (31 * hash) + '\\';
		}
	}

	/**
	 * @param prefix null for the path itself
	 * @return the same as {@link #indexOf(String, String)} for the prefix's text and the query's path
	 */
	int indexOf(final Prefix prefix, final Query query) {
		final String text = (prefix == null) ? null : prefix.text;
		if (!query.plain || ((prefix != null) && !prefix.plain)) {
			return indexOf(text, query.path);
		}
		final int end = ((prefix == null) ? 0 : text.length() + 1) + query.length;
		final int hash = (prefix == null) ? query.hash : (prefix.hash * query.power) + query.hash;
		return find(mix(hash), text, query.path, end, QUERY);
	}

	String get(final int index) {
		return this.paths.get(index);
	}

	private static char charAt(final String prefix, final String path, final int index) {
		if (prefix == null) {
			return path.charAt(index);
		}
		if (index < prefix.length()) {
			return prefix.charAt(index);
		}
		if (index == prefix.length()) {
			return '\\';
		}
		return path.charAt(index - prefix.length() - 1);
	}

	private static final int EXACT = 0;
	private static final int LETTERS = 1;
	/** Letters folded, and '/' and ':' as backslashes, except in the prefix, which the storage names as it is. */
	private static final int QUERY = 2;

	private static boolean isSeparator(final char c, final int index, final String prefix) {
		return (c == '\\') || (((prefix == null) || (index > prefix.length())) && ((c == '/') || (c == ':')));
	}

	private static char fold(final String prefix, final String path, final int k, final int mode) {
		final char c = charAt(prefix, path, k);
		if (mode == EXACT) {
			return c;
		}
		if ((mode == QUERY) && ((c == '/') || (c == ':')) && ((prefix == null) || (k > prefix.length()))) {
			return '\\';
		}
		return fold(c);
	}

	private static int hash(final String prefix, final String path, final int length, final int mode) {
		int hash = 0;
		for (int k = 0; k < length; k++) {
			hash = (31 * hash) + fold(prefix, path, k, mode);
		}
		return hash;
	}

	/** Whether the entry's lower case form is the first length characters of prefix + "\\" + path. */
	private boolean matches(final int entry, final String prefix, final String path, final int length,
			final int queryMode) {
		final String lowered = this.exotic.isEmpty() ? null : this.exotic.get(entry);
		final String listed = lowered != null ? lowered : this.paths.get(entry);
		if (listed.length() != length) {
			return false;
		}
		final boolean foldEntry = lowered == null;
		for (int k = 0; k < length; k++) {
			final char a = foldEntry ? fold(listed.charAt(k)) : listed.charAt(k);
			final char b = fold(prefix, path, k, queryMode);
			if (a != b) {
				return false;
			}
		}
		return true;
	}

	private static char fold(final char c) {
		return ((c >= 'A') && (c <= 'Z')) ? (char) (c + ('a' - 'A')) : c;
	}

	private static boolean isAscii(final String text) {
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) >= 128) {
				return false;
			}
		}
		return true;
	}

	private static int mix(final int hash) {
		final int h = hash * 0x9E3779B9;
		return h ^ (h >>> 15);
	}
}
