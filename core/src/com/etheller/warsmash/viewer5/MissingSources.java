package com.etheller.warsmash.viewer5;

import java.util.HashMap;
import java.util.Map;

import com.etheller.warsmash.datasources.DataSource;

/**
 * The paths no data source had, and what they were answered with, so that the same missing file (an effect asking
 * for it every few milliseconds) is neither looked for nor reported again. Forgotten when the data source is
 * another one, or has changed its files.
 */
final class MissingSources {
	private final Map<String, String> answers = new HashMap<>();
	private DataSource data;
	private int revision;

	/** The answer given for a path that was missing, or null when it is not known to be missing. */
	synchronized String get(final DataSource dataSource, final String src) {
		if (this.answers.isEmpty()) {
			return null;
		}
		if ((this.data != dataSource) || (this.revision != dataSource.revision())) {
			this.answers.clear();
			return null;
		}
		return this.answers.get(src);
	}

	synchronized void put(final DataSource dataSource, final String src, final String answer) {
		if ((this.data != dataSource) || (this.revision != dataSource.revision())) {
			this.answers.clear();
			this.data = dataSource;
			this.revision = dataSource.revision();
		}
		this.answers.put(src, answer);
	}

	synchronized void clear() {
		this.answers.clear();
		this.data = null;
	}

	synchronized int size() {
		return this.answers.size();
	}
}
