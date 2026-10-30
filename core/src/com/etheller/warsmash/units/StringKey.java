package com.etheller.warsmash.units;

/**
 * A hashable wrapper object for a String that can be used as the key in a
 * hashtable, but which disregards case as a key -- except that it will remember
 * case if directly asked for its value. The game needs this to be able to show
 * the original case of a string to the user in the editor, while still doing
 * map lookups in a case insensitive way.
 *
 * @author Eric
 *
 */
public final class StringKey {
	private final String string;
	private int hash;
	private boolean hashed;

	public StringKey(final String string) {
		this.string = string;
	}

	public String getString() {
		return this.string;
	}

	@Override
	public int hashCode() {
		if (!this.hashed) {
			this.hash = 31 + lowerCaseHash(this.string);
			this.hashed = true;
		}
		return this.hash;
	}

	// Same value as string.toLowerCase().hashCode(), without making the lower case copy for plain ASCII keys: object
	// data lookups hash a new key every time, and lowering those strings was a third of the game start on phones.
	private static int lowerCaseHash(final String string) {
		int hash = 0;
		for (int i = 0, n = string.length(); i < n; i++) {
			char c = string.charAt(i);
			if (c >= 128) {
				return string.toLowerCase().hashCode();
			}
			if ((c >= 'A') && (c <= 'Z')) {
				c += 'a' - 'A';
			}
			hash = (31 * hash) + c;
		}
		return hash;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final StringKey other = (StringKey) obj;
		if (this.string == null) {
			if (other.string != null) {
				return false;
			}
		}
		else if (!this.string.equalsIgnoreCase(other.string)) {
			return false;
		}
		return true;
	}
}