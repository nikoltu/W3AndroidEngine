package com.etheller.warsmash.parsers.fdf;

import java.io.IOException;
import java.io.InputStream;
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
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * libGDX takes a glyph's advance from the hinted outline and rounds it up to whole pixels, which makes a line of
 * text 4-10% wider than the font's own metrics at the sizes of the game interface. The advance here is the
 * font's unhinted one, rounded to the nearest pixel, so the widths of a line add up to the font's. Digits are the
 * exception: the original does not use their equal font advance (a "1" is narrow there), so they are as wide as their
 * outline plus a gap.
 *
 * A letter the face lacks (Korean or Chinese text of a map next to the Latin interface font) is drawn from a font of
 * the device that has it, found once when the first such letter comes.
 */
public class UnroundedAdvanceFontGenerator extends FreeTypeFontGenerator {
	/** The original spaces digits by their own width plus this much, not by the font's equal digit advance. */
	private static final float DIGIT_GAP_PER_SIZE = 0.06f;
	private final Library metricsLibrary;
	private final Face metricsFace;
	private int metricsSize;
	private final boolean fallbackAllowed;

	public UnroundedAdvanceFontGenerator(final FileHandle fontFile) {
		this(fontFile, true);
	}

	/** @param fallbackAllowed false for the font the others fall back to */
	UnroundedAdvanceFontGenerator(final FileHandle fontFile, final boolean fallbackAllowed) {
		super(fontFile);
		this.fallbackAllowed = fallbackAllowed;
		final ByteBuffer metricsBuffer = readNative(fontFile);
		this.metricsLibrary = FreeType.initFreeType();
		this.metricsFace = this.metricsLibrary.newMemoryFace(metricsBuffer, 0);
	}

	/**
	 * The font in memory the heap does not hold: a font of the device that covers Korean and Chinese is some 30 MB, and a
	 * direct buffer of that size counts against the heap of a phone, as does the array it is read through.
	 */
	private static ByteBuffer readNative(final FileHandle fontFile) {
		final int size = (int) fontFile.length();
		if (size <= 0) {
			final byte[] bytes = fontFile.readBytes();
			final ByteBuffer buffer = BufferUtils.newUnsafeByteBuffer(bytes.length);
			buffer.put(bytes);
			buffer.flip();
			return buffer;
		}
		final ByteBuffer buffer = BufferUtils.newUnsafeByteBuffer(size);
		try (InputStream input = fontFile.read()) {
			final byte[] chunk = new byte[64 * 1024];
			int read;
			while (buffer.hasRemaining()
					&& ((read = input.read(chunk, 0, Math.min(chunk.length, buffer.remaining()))) != -1)) {
				buffer.put(chunk, 0, read);
			}
		}
		catch (final IOException e) {
			BufferUtils.disposeUnsafeByteBuffer(buffer);
			throw new GdxRuntimeException("Error reading font file: " + fontFile, e);
		}
		buffer.flip();
		return buffer;
	}

	@Override
	protected Glyph createGlyph(final char c, final FreeTypeBitmapFontData data, final FreeTypeFontParameter parameter,
			final Stroker stroker, final float baseLine, final PixmapPacker packer) {
		final Glyph glyph = super.createGlyph(c, data, parameter, stroker, baseLine, packer);
		if ((glyph == null) && this.fallbackAllowed && (c > 0x7F) && !Character.isISOControl(c)) {
			return GlyphFallbackFont.createGlyph(c, data, parameter, stroker, baseLine, packer);
		}
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
					// The advance counts from the outline's left edge, so the glyph is drawn from there too: with
					// the font's own left bearing a "1" ends up against the next character and far from the last.
					glyph.xoffset -= Math.round(metrics.getHoriBearingX() / 64f);
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
		// the face frees the buffer it was made from
		this.metricsFace.dispose();
		this.metricsLibrary.dispose();
		super.dispose();
	}
}
