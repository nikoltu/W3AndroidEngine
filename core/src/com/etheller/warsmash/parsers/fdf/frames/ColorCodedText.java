package com.etheller.warsmash.parsers.fdf.frames;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.Align;

/**
 * Text drawn straight with a font, outside of a frame, with its color codes: "|cAARRGGBB" starts a color, "|r" goes
 * back to the base color and "|n" breaks the line. Lines longer than the wrap width break at their last space, or
 * inside a word that does not fit alone. The layout is kept until the text, the font or the sizes change.
 */
public final class ColorCodedText {
	private final List<Piece> pieces = new ArrayList<>();
	private String text;
	private BitmapFont font;
	private float lineHeight;
	private float wrapWidth;
	private float width;
	private float height;

	/**
	 * Lays the text out for this font, lines lineHeight apart and wrapped at wrapWidth (0 for no wrapping), unless it
	 * already is.
	 */
	public void set(final String text, final BitmapFont font, final GlyphLayout glyphLayout, final float lineHeight,
			final float wrapWidth) {
		if ((font == this.font) && text.equals(this.text) && (lineHeight == this.lineHeight)
				&& (wrapWidth == this.wrapWidth)) {
			return;
		}
		this.text = text;
		this.font = font;
		this.lineHeight = lineHeight;
		this.wrapWidth = wrapWidth;
		this.pieces.clear();
		this.width = 0;
		final TextPen pen = new TextPen(font);
		final StringBuilder lineText = new StringBuilder();
		final List<Color> lineColors = new ArrayList<>();
		Color color = null;
		int line = 0;
		final int length = text.length();
		for (int i = 0; i < length; i++) {
			final char c = text.charAt(i);
			if ((c == '|') && ((i + 1) < length)) {
				final char escaped = text.charAt(i + 1);
				if (((escaped == 'c') || (escaped == 'C')) && ((i + 10) <= length)) {
					final Color parsed = parseColor(text.substring(i + 2, i + 10));
					if (parsed != null) {
						color = parsed;
						i += 9;
						continue;
					}
				}
				else if ((escaped == 'r') || (escaped == 'R')) {
					color = null;
					i++;
					continue;
				}
				else if ((escaped == 'n') || (escaped == 'N')) {
					line = addLine(lineText, lineColors, line, pen, glyphLayout);
					i++;
					continue;
				}
			}
			else if ((c == '\r') || (c == '\n')) {
				if ((c == '\r') && ((i + 1) < length) && (text.charAt(i + 1) == '\n')) {
					i++;
				}
				line = addLine(lineText, lineColors, line, pen, glyphLayout);
				continue;
			}
			lineText.append(c);
			lineColors.add(color);
		}
		line = addLine(lineText, lineColors, line, pen, glyphLayout);
		this.height = font.getCapHeight() + ((line - 1) * lineHeight);
	}

	/** Lays out one line of the text, wrapped into as many lines as it needs; returns the next line number. */
	private int addLine(final StringBuilder lineText, final List<Color> lineColors, int line, final TextPen pen,
			final GlyphLayout glyphLayout) {
		int start = 0;
		final int length = lineText.length();
		if (this.wrapWidth > 0) {
			for (int end = start + 1; end <= length; end++) {
				if (((end - start) < 2) || (lineText.charAt(end - 1) == ' ')) {
					continue;
				}
				glyphLayout.setText(this.font, lineText, start, end, Color.WHITE, 0, Align.left, false, null);
				if (glyphLayout.width <= this.wrapWidth) {
					continue;
				}
				final int space = lineText.lastIndexOf(" ", end - 1);
				if (space > start) {
					addPieces(lineText, lineColors, start, space, line, pen, glyphLayout);
					start = space + 1;
				}
				else {
					addPieces(lineText, lineColors, start, end - 1, line, pen, glyphLayout);
					start = end - 1;
				}
				pen.newLine();
				line++;
			}
		}
		addPieces(lineText, lineColors, start, length, line, pen, glyphLayout);
		pen.newLine();
		lineText.setLength(0);
		lineColors.clear();
		return line + 1;
	}

	/** Adds the runs of one color between start and end as pieces of this line. */
	private void addPieces(final StringBuilder lineText, final List<Color> lineColors, final int start, final int end,
			final int line, final TextPen pen, final GlyphLayout glyphLayout) {
		int runStart = start;
		for (int i = start + 1; i <= end; i++) {
			if ((i == end) || (lineColors.get(i) != lineColors.get(runStart))) {
				final String pieceText = lineText.substring(runStart, i);
				final float x = pen.place(pieceText);
				glyphLayout.setText(this.font, pieceText);
				this.width = Math.max(this.width, x + glyphLayout.width);
				this.pieces.add(new Piece(pieceText, lineColors.get(runStart), x, line));
				runStart = i;
			}
		}
	}

	public float getWidth() {
		return this.width;
	}

	/** From the top of the first line to the baseline of the last. */
	public float getHeight() {
		return this.height;
	}

	/**
	 * Draws the text with the top of its first line at y. Uncolored parts take the base color; every part takes its
	 * alpha.
	 */
	public void draw(final Batch batch, final float x, final float y, final Color baseColor) {
		for (final Piece piece : this.pieces) {
			final Color color = piece.color == null ? baseColor : piece.color;
			this.font.setColor(color.r, color.g, color.b, baseColor.a);
			this.font.draw(batch, piece.text, x + piece.x, y - (piece.line * this.lineHeight));
		}
	}

	/** Draws the whole text in one color, such as its shadow. */
	public void drawInColor(final Batch batch, final float x, final float y, final Color color) {
		this.font.setColor(color);
		for (final Piece piece : this.pieces) {
			this.font.draw(batch, piece.text, x + piece.x, y - (piece.line * this.lineHeight));
		}
	}

	/** The color of "|cAARRGGBB" from its eight hex digits, or null if they are not hex. */
	public static Color parseColor(final String hex) {
		try {
			final int argb = (int) Long.parseLong(hex, 16);
			// The alpha of a color code is not used: the text is always opaque.
			return new Color((argb << 8) | 0xFF);
		}
		catch (final NumberFormatException exc) {
			return null;
		}
	}

	private static final class Piece {
		private final String text;
		private final Color color;
		private final float x;
		private final int line;

		private Piece(final String text, final Color color, final float x, final int line) {
			this.text = text;
			this.color = color;
			this.x = x;
			this.line = line;
		}
	}
}
