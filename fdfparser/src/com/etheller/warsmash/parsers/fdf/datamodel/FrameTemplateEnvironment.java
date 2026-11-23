package com.etheller.warsmash.parsers.fdf.datamodel;

import java.util.HashMap;
import java.util.Map;

public class FrameTemplateEnvironment {
	private final Map<String, String> idToDecoratedString = new HashMap<>();
	private Map<String, FrameDefinition> idToFrame = new HashMap<>();
	private Runnable reloader;
	private Map<String, String> overrides = new HashMap<>();

	public void addDecoratedString(final String id, final String value) {
		this.idToDecoratedString.put(id, value);
	}

	/** Strings a map's own skin file sets: they replace the interface's strings of the same name, whenever those are read. */
	public void putOverride(final String id, final String value) {
		this.overrides.put(id, value);
	}

	public String getDecoratedString(final String id) {
		final String override = this.overrides.get(id);
		if (override != null) {
			return override;
		}
		final String decoratedString = this.idToDecoratedString.get(id);
		if (decoratedString != null) {
			return decoratedString;
		}
		return id;
	}

	/** Takes over the definitions and strings of another environment, which is not used afterwards. */
	public void addAll(final FrameTemplateEnvironment other) {
		this.idToDecoratedString.putAll(other.idToDecoratedString);
		this.idToFrame.putAll(other.idToFrame);
	}

	public void put(final String id, final FrameDefinition frame) {
		this.idToFrame.put(id, frame);
	}

	public FrameDefinition getFrame(final String id) {
		restore();
		return this.idToFrame.get(id);
	}

	/**
	 * Lets go of the frame definitions, which a screen that is not showing does not read. The next {@link #getFrame}
	 * makes them again with the reloader, so a caller that forgets {@link #restore()} only pays for it later.
	 */
	public void release(final Runnable reloader) {
		if ((this.reloader == null) && !this.idToFrame.isEmpty()) {
			this.reloader = reloader;
			this.idToFrame = new HashMap<>();
		}
	}

	/** Makes the definitions again if they were released; does nothing otherwise. */
	public void restore() {
		final Runnable reload = this.reloader;
		if (reload != null) {
			this.reloader = null;
			reload.run();
		}
	}
}
