package com.etheller.warsmash.viewer5.handlers.mdx;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.etheller.warsmash.viewer5.gl.CachedShaderProgram;

/**
 * Uniform and attribute locations of the particle shader, found once per program like {@link MdxLocations} does for
 * the model shaders. A location that the program does not have is -1.
 */
final class EmitterLocations {
	final int mvp;
	final int texture;
	final int emitter;
	final int filterMode;
	final int lifeSpan;
	final int timeMiddle;
	final int columns;
	final int rows;
	final int teamColored;
	final int unshaded;
	final int unfogged;
	final int fogColor;
	final int fogParams;
	final int fogHeight;
	final int lightTexture;
	final int lightCount;
	final int lightTextureHeight;
	final int[] intervals = new int[4];
	final int[] colors = new int[3];
	final int scaling;
	final int[] vertices = new int[4];
	final int cameraZ;
	final int intervalTimes;

	final int position;
	final int p0;
	final int p1;
	final int p2;
	final int p3;
	final int health;
	final int color;
	final int tail;
	final int leftRightTop;

	private final int links;

	private EmitterLocations(final ShaderProgram shader, final int links) {
		this.links = links;
		this.mvp = uniform(shader, "u_mvp");
		this.texture = uniform(shader, "u_texture");
		this.emitter = uniform(shader, "u_emitter");
		this.filterMode = uniform(shader, "u_filterMode");
		this.lifeSpan = uniform(shader, "u_lifeSpan");
		this.timeMiddle = uniform(shader, "u_timeMiddle");
		this.columns = uniform(shader, "u_columns");
		this.rows = uniform(shader, "u_rows");
		this.teamColored = uniform(shader, "u_teamColored");
		this.unshaded = uniform(shader, "u_unshaded");
		this.unfogged = uniform(shader, "u_unfogged");
		this.fogColor = uniform(shader, "u_fogColor");
		this.fogParams = uniform(shader, "u_fogParams");
		this.fogHeight = uniform(shader, "u_fogHeight");
		this.lightTexture = uniform(shader, "u_lightTexture");
		this.lightCount = uniform(shader, "u_lightCount");
		this.lightTextureHeight = uniform(shader, "u_lightTextureHeight");
		for (int i = 0; i < this.intervals.length; i++) {
			this.intervals[i] = uniform(shader, "u_intervals[" + i + "]");
		}
		for (int i = 0; i < this.colors.length; i++) {
			this.colors[i] = uniform(shader, "u_colors[" + i + "]");
		}
		this.scaling = uniform(shader, "u_scaling");
		for (int i = 0; i < this.vertices.length; i++) {
			this.vertices[i] = uniform(shader, "u_vertices[" + i + "]");
		}
		this.cameraZ = uniform(shader, "u_cameraZ");
		this.intervalTimes = uniform(shader, "u_intervalTimes");
		this.position = shader.getAttributeLocation("a_position");
		this.p0 = shader.getAttributeLocation("a_p0");
		this.p1 = shader.getAttributeLocation("a_p1");
		this.p2 = shader.getAttributeLocation("a_p2");
		this.p3 = shader.getAttributeLocation("a_p3");
		this.health = shader.getAttributeLocation("a_health");
		this.color = shader.getAttributeLocation("a_color");
		this.tail = shader.getAttributeLocation("a_tail");
		this.leftRightTop = shader.getAttributeLocation("a_leftRightTop");
	}

	private static int uniform(final ShaderProgram shader, final String name) {
		return shader.fetchUniformLocation(name, false);
	}

	/** The locations of the shader the caller has just made current; made again when the program was relinked. */
	static EmitterLocations of(final ShaderProgram shader) {
		if (shader instanceof CachedShaderProgram) {
			final CachedShaderProgram cached = (CachedShaderProgram) shader;
			final Object held = cached.getHolder();
			if ((held instanceof EmitterLocations) && (((EmitterLocations) held).links == cached.getLinks())) {
				return (EmitterLocations) held;
			}
			final EmitterLocations made = new EmitterLocations(shader, cached.getLinks());
			cached.setHolder(made);
			return made;
		}
		return new EmitterLocations(shader, 0);
	}
}
