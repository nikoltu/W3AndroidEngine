package com.etheller.warsmash.parsers.fdf;

import java.nio.ByteBuffer;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType.Face;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType.GlyphMetrics;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType.Library;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType.Stroker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

/**
 * libGDX takes a glyph's advance from the hinted outline and rounds it up to whole pixels, which makes a line of
 * text 4-10% wider than the font's own metrics at the sizes of the game interface. The advance here is the
 * font's unhinted one, rounded to the nearest pixel, so the widths of a line add up to the font's. Digits are the
 * exception: the original does not use their equal font advance (a "1" is narrow there), so they are as wide as their
 * outline plus a gap.
 */
public class UnroundedAdvanceFontGenerator extends FreeTypeFontGenerator {
	/** The original spaces digits by their own width plus this much, not by the font's equal digit advance. */
	private static final float DIGIT_GAP_PER_SIZE = 0.06f;
	private final Library metricsLibrary;
	private final Face metricsFace;
	private int metricsSize;

	public UnroundedAdvanceFontGenerator(final FileHandle fontFile) {
		super(fontFile);
		final byte[] bytes = fontFile.readBytes();
		final ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
		buffer.put(bytes);
		buffer.flip();
		this.metricsLibrary = FreeType.initFreeType();
		this.metricsFace = this.metricsLibrary.newMemoryFace(buffer, 0);
	}

	@Override
	protected Glyph createGlyph(final char c, final FreeTypeBitmapFontData data, final FreeTypeFontParameter parameter,
			final Stroker stroker, final float baseLine, final PixmapPacker packer) {
		final Glyph glyph = super.createGlyph(c, data, parameter, stroker, baseLine, packer);
		if ((glyph != null) && (parameter.size > 0) && !parameter.mono) {
			if (this.metricsSize != parameter.size) {
				this.metricsFace.setPixelSizes(0, parameter.size);
				this.metricsSize = parameter.size;
			}
			if (this.metricsFace.loadChar(c, FreeType.FT_LOAD_NO_HINTING)) {
				final GlyphMetrics metrics = this.metricsFace.getGlyph().getMetrics();
				if ((c >= '0') && (c <= '9')) {
					glyph.xadvance = Math.round((metrics.getWidth() / 64f) + (DIGIT_GAP_PER_SIZE * parameter.size))
							+ parameter.spaceX;
				}
				else {
					glyph.xadvance = Math.round(metrics.getHoriAdvance() / 64f) + parameter.spaceX;
				}
			}
		}
		return glyph;
	}

	@Override
	public void dispose() {
		this.metricsFace.dispose();
		this.metricsLibrary.dispose();
		super.dispose();
	}
}
