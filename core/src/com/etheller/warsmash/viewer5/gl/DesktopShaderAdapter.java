package com.etheller.warsmash.viewer5.gl;

import java.util.regex.Pattern;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * macOS-only shader compatibility. libGDX's ShaderProgram already has the small hook we need:
 * the static prependVertexCode/prependFragmentCode strings are added to shader source before it is
 * compiled. Installing GLSL 330 core compatibility there covers libGDX's own shaders (SpriteBatch,
 * MovieTexture, FadingSpriteBatch) without copying the whole ShaderProgram class.
 *
 * On every other operating system install() does nothing, so Windows, Linux and Android keep the
 * unmodified libGDX behaviour.
 */
public final class DesktopShaderAdapter {
	private static final Pattern VERSION_DIRECTIVE = Pattern.compile("^[ \\t]*#version[ \\t]+\\d+[^\\r\\n]*\\r?\\n?", Pattern.MULTILINE);

	public static final String VERTEX_PREPEND = "#version 330 core\n"
			+ "#define lowp\n"
			+ "#define mediump\n"
			+ "#define highp\n"
			+ "#define attribute in\n"
			+ "#define varying out\n"
			+ "#define texture2D texture\n"
			+ "#define textureCube texture\n"
			+ "#define texture2DProj textureProj\n"
			+ "#define texture2DLod textureLod\n";

	public static final String FRAGMENT_PREPEND = "#version 330 core\n"
			+ "#define lowp\n"
			+ "#define mediump\n"
			+ "#define highp\n"
			+ "#define varying in\n"
			+ "#define texture2D texture\n"
			+ "#define textureCube texture\n"
			+ "#define texture2DProj textureProj\n"
			+ "#define texture2DLod textureLod\n"
			+ "out vec4 g_fragColor;\n"
			+ "#define gl_FragColor g_fragColor\n";

	private DesktopShaderAdapter() {
	}

	public static boolean isMacDesktop() {
		return isMacDesktop(System.getProperty("os.name"));
	}

	static boolean isMacDesktop(final String osName) {
		return osName != null && osName.toLowerCase().contains("mac");
	}

	public static void install() {
		if (!isMacDesktop()) {
			return;
		}
		ShaderProgram.prependVertexCode = VERTEX_PREPEND;
		ShaderProgram.prependFragmentCode = FRAGMENT_PREPEND;
	}

	/** Removes an existing #version line so that the prepend hook supplies the only one. */
	public static String stripVersionDirective(final String source) {
		if (source == null) {
			return null;
		}
		return VERSION_DIRECTIVE.matcher(source).replaceFirst("");
	}
}
