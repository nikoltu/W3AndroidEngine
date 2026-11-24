package com.etheller.warsmash.parsers.fdf;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType.Stroker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeBitmapFontData;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.etheller.warsmash.movie.Movies;
import com.etheller.warsmash.util.Log;

/**
 * The fonts of the device that draw the letters the interface font lacks (the Korean or Chinese text of a map): Android
 * keeps its Chinese, Japanese and Korean font in /system/fonts, a computer has its own (see
 * {@link Movies#computerFontPaths()}). One font rarely has every script, so they are asked in turn, each opened when
 * the one before it did not have the letter.
 */
final class GlyphFallbackFont {
	private static final String[] ANDROID_FONTS = { "/system/fonts/NotoSansCJK-Regular.ttc",
			"/system/fonts/NotoSansCJKkr-Regular.otf", "/system/fonts/NotoSansKR-Regular.otf",
			"/system/fonts/NotoSansCJKjp-Regular.otf", "/system/fonts/NotoSansCJKsc-Regular.otf",
			"/system/fonts/DroidSansFallbackFull.ttf", "/system/fonts/DroidSansFallback.ttf" };
	private static List<File> files;
	private static final List<UnroundedAdvanceFontGenerator> OPENED = new ArrayList<>();
	/** Letters no font of the device has, so a text that keeps showing one does not ask them again every frame. */
	private static final Set<Character> MISSING = new HashSet<>();

	private GlyphFallbackFont() {
	}

	static synchronized Glyph createGlyph(final char c, final FreeTypeBitmapFontData data,
			final FreeTypeFontParameter parameter, final Stroker stroker, final float baseLine,
			final PixmapPacker packer) {
		if (MISSING.contains(c)) {
			return null;
		}
		if (files == null) {
			files = candidates();
			if (files.isEmpty()) {
				Log.warn("No font of the device for letters the interface font lacks");
			}
		}
		for (int i = 0; i < files.size(); i++) {
			if (i == OPENED.size()) {
				OPENED.add(open(files.get(i)));
			}
			final UnroundedAdvanceFontGenerator generator = OPENED.get(i);
			if ((generator == null) || !generator.hasGlyph(c)) {
				continue;
			}
			// Sets the size the glyph is drawn at on the face of this font.
			generator.scaleForPixelHeight(parameter.size);
			final Glyph glyph = generator.createGlyph(c, data, parameter, stroker, baseLine, packer);
			if (glyph != null) {
				return glyph;
			}
		}
		MISSING.add(c);
		return null;
	}

	private static UnroundedAdvanceFontGenerator open(final File file) {
		try {
			return new UnroundedAdvanceFontGenerator(Gdx.files.absolute(file.getPath()), false);
		}
		catch (final RuntimeException e) {
			Log.warn("Unable to open the fallback font " + file + ": " + e);
			return null;
		}
	}

	private static List<File> candidates() {
		final List<File> found = new ArrayList<>();
		for (final String path : ANDROID_FONTS) {
			final File font = new File(path);
			if (font.isFile()) {
				found.add(font);
			}
		}
		if (found.isEmpty()) {
			final File[] system = new File("/system/fonts").listFiles();
			if (system != null) {
				for (final File font : system) {
					final String name = font.getName().toLowerCase(Locale.ROOT);
					if ((name.contains("cjk") || name.contains("fallback")) && font.isFile()) {
						found.add(font);
					}
				}
			}
		}
		final String windows = System.getenv("WINDIR");
		for (final String path : Movies.computerFontPaths()) {
			File font = new File(path);
			if (!font.isFile() && (windows != null) && path.startsWith("C:/Windows/")) {
				font = new File(windows, path.substring("C:/Windows/".length()));
			}
			if (font.isFile()) {
				found.add(font);
			}
		}
		return found;
	}
}
