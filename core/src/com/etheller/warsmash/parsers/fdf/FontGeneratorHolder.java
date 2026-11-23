package com.etheller.warsmash.parsers.fdf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntMap;

public class FontGeneratorHolder {
	private static final int MIN_PAGE_SIZE = 256;
	/** The missing glyph and printable ASCII; accents and other scripts come with the first text that has them. */
	private static final String BASIC_CHARS = basicChars();
	private final FreeTypeFontGenerator generator;
	private final IntMap<BitmapFont> sizeToFont;
	private final String name;
	private static final List<FontGeneratorHolder> LIVE = new ArrayList<>();

	public FontGeneratorHolder(final String name, final FreeTypeFontGenerator generator) {
		this.name = name;
		this.generator = generator;
		this.sizeToFont = new IntMap<>();
		synchronized (LIVE) {
			LIVE.add(this);
		}
	}

	private static String basicChars() {
		final StringBuilder out = new StringBuilder("\u0000");
		for (char c = ' '; c <= '~'; c++) {
			out.append(c);
		}
		return out.toString();
	}

	/** Every live font, for the memory log: face, size, pages and glyphs. */
	public static String report() {
		final StringBuilder out = new StringBuilder("fonts");
		synchronized (LIVE) {
			for (final FontGeneratorHolder holder : LIVE) {
				for (final IntMap.Entry<BitmapFont> entry : holder.sizeToFont.entries()) {
					final BitmapFont font = entry.value;
					int glyphs = 0;
					long area = 0;
					for (final Glyph[] page : font.getData().glyphs) {
						if (page != null) {
							for (final Glyph glyph : page) {
								if (glyph != null) {
									glyphs++;
									area += (long) (glyph.width + 2) * (glyph.height + 2);
								}
							}
						}
					}
					final int pages = font.getRegions().size;
					final int pageWidth = pages == 0 ? 0 : font.getRegion(0).getTexture().getWidth();
					out.append(String.format(Locale.ROOT, " [%s@%d pages=%d page=%d glyphs=%d fill=%d%%]", holder.name,
							entry.key, pages, pageWidth, glyphs,
							pageWidth == 0 ? 0 : (area * 100) / ((long) pageWidth * pageWidth * pages)));
				}
			}
		}
		return out.toString();
	}

	public BitmapFont generateFont(final FreeTypeFontParameter parameter) {
		BitmapFont font = this.sizeToFont.get(parameter.size);
		if (font == null) {
			font = create(parameter);
			this.sizeToFont.put(parameter.size, font);
		}
		return font;
	}

	/**
	 * A font keeps the parameters it was made with to rasterize the glyphs that come later, so it needs a copy: callers
	 * reuse one parameter object for fonts of several sizes, and a glyph made with another size than its font's lands
	 * on the wrong baseline and at the wrong scale.
	 */
	private static FreeTypeFontParameter copyOf(final FreeTypeFontParameter source) {
		final FreeTypeFontParameter copy = new FreeTypeFontParameter();
		copy.size = source.size;
		copy.mono = source.mono;
		copy.hinting = source.hinting;
		copy.color = new Color(source.color);
		copy.gamma = source.gamma;
		copy.renderCount = source.renderCount;
		copy.borderWidth = source.borderWidth;
		copy.borderColor = new Color(source.borderColor);
		copy.borderStraight = source.borderStraight;
		copy.borderGamma = source.borderGamma;
		copy.shadowOffsetX = source.shadowOffsetX;
		copy.shadowOffsetY = source.shadowOffsetY;
		copy.shadowColor = new Color(source.shadowColor);
		copy.spaceX = source.spaceX;
		copy.spaceY = source.spaceY;
		copy.padTop = source.padTop;
		copy.padLeft = source.padLeft;
		copy.padBottom = source.padBottom;
		copy.padRight = source.padRight;
		copy.characters = source.characters;
		copy.kerning = source.kerning;
		copy.packer = source.packer;
		copy.flip = source.flip;
		copy.genMipMaps = source.genMipMaps;
		copy.minFilter = source.minFilter;
		copy.magFilter = source.magFilter;
		copy.incremental = source.incremental;
		return copy;
	}

	private BitmapFont create(final FreeTypeFontParameter requested) {
		final FreeTypeFontParameter parameter = copyOf(requested);
		if (FreeTypeFontGenerator.DEFAULT_CHARS.equals(parameter.characters)) {
			parameter.characters = BASIC_CHARS;
		}
		parameter.incremental = true; //enable incremental to support non-ascii characters like chinese when free-type fonts contains the char.
		final int limit = FreeTypeFontGenerator.getMaxTextureSize();
		int page = pageSize(parameter.size, limit);
		BitmapFont font;
		try {
			while (true) {
				FreeTypeFontGenerator.setMaxTextureSize(page);
				font = this.generator.generateFont(parameter);
				// A face with wider glyphs than the estimate: the basic set goes on one page of the next size up.
				if ((font.getRegions().size <= 1) || (page >= limit)) {
					break;
				}
				font.dispose();
				page *= 2;
			}
		}
		finally {
			FreeTypeFontGenerator.setMaxTextureSize(limit);
		}
		// Warcraft III puts lines one font height apart, closer than the font's own ascent and descent.
		font.getData().setLineHeight(parameter.size);
		return font;
	}

	/**
	 * The fonts of one screen that goes away again (the loading screen): the shared font of a size when there is one,
	 * otherwise a font of its own, which is disposed with the lease instead of staying in the generator for good.
	 */
	public Lease lease() {
		return new Lease();
	}

	public class Lease {
		private final IntMap<BitmapFont> own = new IntMap<>();

		public BitmapFont generateFont(final FreeTypeFontParameter parameter) {
			BitmapFont font = FontGeneratorHolder.this.sizeToFont.get(parameter.size);
			if (font == null) {
				font = this.own.get(parameter.size);
				if (font == null) {
					font = create(parameter);
					this.own.put(parameter.size, font);
				}
			}
			return font;
		}

		public void dispose() {
			for (final BitmapFont font : this.own.values()) {
				font.dispose();
			}
			this.own.clear();
		}
	}

	/**
	 * An incremental font packs its glyphs into pages of the largest allowed size, 4 MB in the GPU and as much again in
	 * the memory of the game for a font that uses a few kilobytes of it. The page is the smallest power of two that
	 * holds the basic set (about 96 glyphs of half a size wide and seven tenths of it high) with a fifth to spare; the
	 * glyphs of other scripts that come later get further pages.
	 */
	private static int pageSize(final int fontSize, final int limit) {
		final double size = Math.max(1, fontSize);
		final double area = 96 * ((0.5 * size) + 2) * ((0.7 * size) + 2) * 1.2;
		final int wanted = MathUtils.nextPowerOfTwo((int) Math.ceil(Math.sqrt(area)));
		return Math.min(limit, Math.max(MIN_PAGE_SIZE, wanted));
	}

	public void dispose() {
		for (final BitmapFont font : this.sizeToFont.values()) {
			font.dispose();
		}
		this.sizeToFont.clear();
		this.generator.dispose();
		synchronized (LIVE) {
			LIVE.remove(this);
		}
	}
}
