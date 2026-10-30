package com.etheller.warsmash.viewer5.gl;

import java.nio.Buffer;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * libGDX keys its location maps by the names the driver reports, so a lookup with a string literal never hits the
 * identity check and compares the name character by character; with a uniform set per batch this was about a tenth
 * of the render time on phones. These maps are keyed by the caller's strings instead.
 */
public class CachedShaderProgram extends ShaderProgram {
	private final Map<String, Integer> uniformLocations = new HashMap<>();
	private final Map<String, Integer> attributeLocations = new HashMap<>();

	public CachedShaderProgram(final String vertexShader, final String fragmentShader) {
		super(vertexShader, fragmentShader);
	}

	@Override
	public int fetchUniformLocation(final String name, final boolean pedantic) {
		final Integer cached = this.uniformLocations.get(name);
		if (cached != null) {
			return cached;
		}
		final int location = super.fetchUniformLocation(name, pedantic);
		this.uniformLocations.put(name, location);
		return location;
	}

	private int cachedAttributeLocation(final String name) {
		final Integer cached = this.attributeLocations.get(name);
		if (cached != null) {
			return cached;
		}
		final int location = getAttributeLocation(name);
		this.attributeLocations.put(name, location);
		return location;
	}

	@Override
	public void setVertexAttribute(final String name, final int size, final int type, final boolean normalize,
			final int stride, final Buffer buffer) {
		final int location = cachedAttributeLocation(name);
		if (location != -1) {
			setVertexAttribute(location, size, type, normalize, stride, buffer);
		}
	}

	@Override
	public void setVertexAttribute(final String name, final int size, final int type, final boolean normalize,
			final int stride, final int offset) {
		final int location = cachedAttributeLocation(name);
		if (location != -1) {
			setVertexAttribute(location, size, type, normalize, stride, offset);
		}
	}

	@Override
	public void enableVertexAttribute(final String name) {
		final int location = cachedAttributeLocation(name);
		if (location != -1) {
			enableVertexAttribute(location);
		}
	}

	@Override
	public void disableVertexAttribute(final String name) {
		final int location = cachedAttributeLocation(name);
		if (location != -1) {
			disableVertexAttribute(location);
		}
	}
}
