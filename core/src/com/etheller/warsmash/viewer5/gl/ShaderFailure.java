package com.etheller.warsmash.viewer5.gl;

import java.nio.IntBuffer;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;

/**
 * A shader program the driver would not compile or link. The message names the program, its defines, both compile
 * logs, the link log and the driver's limits, as a shader works on one GPU and fails on another and the crash log is
 * often all there is to go on.
 */
public class ShaderFailure extends IllegalStateException {
	private static final int[] LIMITS = { GL20.GL_MAX_VERTEX_UNIFORM_VECTORS, GL20.GL_MAX_FRAGMENT_UNIFORM_VECTORS,
			GL20.GL_MAX_VARYING_VECTORS, GL20.GL_MAX_VERTEX_ATTRIBS, GL20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS,
			GL20.GL_MAX_TEXTURE_IMAGE_UNITS, GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS };
	private static final String[] LIMIT_NAMES = { "MAX_VERTEX_UNIFORM_VECTORS", "MAX_FRAGMENT_UNIFORM_VECTORS",
			"MAX_VARYING_VECTORS", "MAX_VERTEX_ATTRIBS", "MAX_VERTEX_TEXTURE_IMAGE_UNITS", "MAX_TEXTURE_IMAGE_UNITS",
			"MAX_COMBINED_TEXTURE_IMAGE_UNITS" };

	public ShaderFailure(final String message) {
		super(message);
	}

	static String describe(final GL20 gl, final String vertexSrc, final String fragmentSrc, final String programLog) {
		final StringBuilder report = new StringBuilder("Bad shader ").append(caller());
		final String defines = defines(vertexSrc);
		if (!defines.isEmpty()) {
			report.append(" [").append(defines).append(']');
		}
		report.append('\n');
		final int vertex = compile(gl, GL20.GL_VERTEX_SHADER, vertexSrc, "Vertex shader", report);
		final int fragment = compile(gl, GL20.GL_FRAGMENT_SHADER, fragmentSrc, "Fragment shader", report);
		if ((vertex != 0) && (fragment != 0)) {
			final int program = gl.glCreateProgram();
			gl.glAttachShader(program, vertex);
			gl.glAttachShader(program, fragment);
			gl.glLinkProgram(program);
			final IntBuffer status = BufferUtils.newIntBuffer(1);
			gl.glGetProgramiv(program, GL20.GL_LINK_STATUS, status);
			report.append("Link: ").append(status.get(0) != 0 ? "ok" : "failed").append(", log: ")
					.append(orNone(gl.glGetProgramInfoLog(program))).append('\n');
			gl.glDeleteProgram(program);
		}
		if (vertex != 0) {
			gl.glDeleteShader(vertex);
		}
		if (fragment != 0) {
			gl.glDeleteShader(fragment);
		}
		report.append("Program log: ").append(orNone(programLog)).append('\n');
		report.append("GL: ").append(gl.glGetString(GL20.GL_VERSION)).append(" / ")
				.append(gl.glGetString(GL20.GL_RENDERER)).append(" / GLSL ")
				.append(gl.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION)).append('\n');
		final IntBuffer value = BufferUtils.newIntBuffer(16);
		for (int i = 0; i < LIMITS.length; i++) {
			value.clear();
			value.put(0, -1);
			gl.glGetIntegerv(LIMITS[i], value);
			report.append(i == 0 ? "Limits: " : ", ").append(LIMIT_NAMES[i]).append('=').append(value.get(0));
		}
		// A limit the context does not know (desktop core profiles have no varying vectors) leaves an error behind.
		while (gl.glGetError() != GL20.GL_NO_ERROR) {
		}
		return report.toString();
	}

	/** Returns the shader when it compiled, otherwise 0; its status and log go into the report either way. */
	private static int compile(final GL20 gl, final int type, final String source, final String name,
			final StringBuilder report) {
		final int shader = gl.glCreateShader(type);
		gl.glShaderSource(shader, source);
		gl.glCompileShader(shader);
		final IntBuffer status = BufferUtils.newIntBuffer(1);
		gl.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, status);
		final boolean compiled = status.get(0) != 0;
		report.append(name).append(": ").append(compiled ? "compiled" : "failed").append(", log: ")
				.append(orNone(gl.glGetShaderInfoLog(shader))).append('\n');
		if (!compiled) {
			gl.glDeleteShader(shader);
			return 0;
		}
		return shader;
	}

	/** The engine code that asked for the program, as the shaders themselves carry no names. */
	private static String caller() {
		for (final StackTraceElement frame : new Throwable().getStackTrace()) {
			final String className = frame.getClassName();
			if (!className.equals(ShaderFailure.class.getName()) && !className.equals(WebGL.class.getName())) {
				return className.substring(className.lastIndexOf('.') + 1) + "." + frame.getMethodName();
			}
		}
		return "?";
	}

	private static String defines(final String source) {
		final StringBuilder defines = new StringBuilder();
		for (final String line : source.split("\n")) {
			final String trimmed = line.trim();
			if (trimmed.startsWith("#define ")) {
				if (defines.length() > 0) {
					defines.append(", ");
				}
				defines.append(trimmed.substring(8).trim());
			}
		}
		return defines.toString();
	}

	static String numbered(final String title, final String source) {
		final StringBuilder text = new StringBuilder(title).append(":\n");
		final String[] lines = source.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			text.append(i + 1).append(": ").append(lines[i].replace("\r", "")).append('\n');
		}
		return text.toString();
	}

	private static String orNone(final String log) {
		return (log == null) || log.trim().isEmpty() ? "(empty)" : log.trim();
	}
}
