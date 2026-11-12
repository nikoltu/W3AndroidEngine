package com.etheller.warsmash.parsers.fdf.frames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.datamodel.TextJustify;

public class StringFrame extends AbstractRenderableFrame {
	private final List<SingleStringFrame> internalFrames = new ArrayList<>();
	private Color color;
	private String text = "Default string";
	private String displayText = this.text;
	private TextJustify justifyH;
	/** How the lines lie within the block of text, where justifyH places the block in the frame. */
	private TextJustify lineJustifyH;
	private TextJustify justifyV;
	private final BitmapFont frameFont;
	private Color fontShadowColor;
	private float fontShadowOffsetX;
	private float fontShadowOffsetY;
	private float alpha = 1.0f;
	private final SimpleFrame internalFramesContainer;
	private float predictedViewportHeight;
	private float predictedViewportWidth;
	/** Distance between lines when it is not the font's own height. */
	private float lineSpacing;

	// Made on first use: a renderer made with the class would hold a mesh and a shader of the GL context that
	// first loaded it, for every later game activity too.
	private static ShapeRenderer shapeRenderer;
	private final Color fontHighlightColor;
	private final Color fontDisabledColor;
	private final Color fontColor;
	private boolean passwordField;

	public StringFrame(final String name, final UIFrame parent, final Color color, final TextJustify justifyH,
			final TextJustify justifyV, final BitmapFont frameFont, final String text, final Color fontHighlightColor,
			final Color fontDisabledColor) {
		super(name, parent);
		this.fontColor = color;
		this.color = color;
		this.justifyH = justifyH;
		this.lineJustifyH = justifyH;
		this.justifyV = justifyV;
		this.frameFont = frameFont;
		this.text = text;
		this.displayText = text;
		this.fontHighlightColor = fontHighlightColor;
		this.fontDisabledColor = fontDisabledColor;
		this.internalFramesContainer = new SimpleFrame(null, this);
	}

	public void setJustifyV(final TextJustify justifyV) {
		this.justifyV = justifyV;
	}

	public void setJustifyH(final TextJustify justifyH) {
		this.justifyH = justifyH;
		this.lineJustifyH = justifyH;
	}

	public void setLineJustifyH(final TextJustify lineJustifyH) {
		this.lineJustifyH = lineJustifyH;
	}

	public String getText() {
		return this.text;
	}

	public String getDisplayText() {
		return this.displayText;
	}

	public void setText(final String text, final GameUI gameUI, final Viewport viewport) {
		if (text == null) {
			throw new IllegalArgumentException();
		}
		this.text = text;
		if (this.passwordField) {
			final StringBuilder displayTextBuilder = new StringBuilder();
			for (int i = 0; i < text.length(); i++) {
				displayTextBuilder.append('*');
			}
			this.displayText = displayTextBuilder.toString();
		}
		else {
			this.displayText = text;
		}
		positionBounds(gameUI, viewport);
	}

	public void setColor(final Color color) {
		for (final SingleStringFrame internalFrame : this.internalFrames) {
			if (internalFrame.getColor() == this.color) {
				internalFrame.setColor(color);
			}
		}
		this.color = color;
	}

	public Color getColor() {
		return this.color;
	}

	public Color getFontOriginalColor() {
		return this.fontColor;
	}

	public Color getFontDisabledColor() {
		return this.fontDisabledColor;
	}

	public Color getFontHighlightColor() {
		return this.fontHighlightColor;
	}

	public void setFontShadowColor(final Color fontShadowColor) {
		this.fontShadowColor = fontShadowColor;
		for (final SingleStringFrame internalFrame : this.internalFrames) {
			internalFrame.setFontShadowColor(fontShadowColor);
		}
	}

	public void setFontShadowOffsetX(final float fontShadowOffsetX) {
		this.fontShadowOffsetX = fontShadowOffsetX;
		for (final SingleStringFrame internalFrame : this.internalFrames) {
			internalFrame.setFontShadowOffsetX(fontShadowOffsetX);
		}
	}

	public void setFontShadowOffsetY(final float fontShadowOffsetY) {
		this.fontShadowOffsetY = fontShadowOffsetY;
		for (final SingleStringFrame internalFrame : this.internalFrames) {
			internalFrame.setFontShadowOffsetY(fontShadowOffsetY);
		}
	}

	@Override
	protected void internalRender(final SpriteBatch batch, final BitmapFont baseFont, final GlyphLayout glyphLayout) {
		this.internalFramesContainer.render(batch, baseFont, glyphLayout);

		if (GameUI.DEBUG) {
			batch.end();
			if (shapeRenderer == null) {
				shapeRenderer = new ShapeRenderer();
			}
			shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
			shapeRenderer.setColor(1f, 1f, 1f, 1f);
			shapeRenderer.begin(ShapeType.Line);
			shapeRenderer.rect(this.renderBounds.x, this.renderBounds.y, this.renderBounds.width,
					this.renderBounds.height);

			shapeRenderer.end();

			batch.begin();
		}
	}

	@Override
	public void positionBounds(final GameUI gameUI, final Viewport viewport) {
		createInternalFrames(gameUI.getGlyphLayout());
		if (getAssignedHeight() == 0) {
			this.renderBounds.height = getPredictedViewportHeight();
		}
		if (getAssignedWidth() == 0) {
			this.renderBounds.width = getPredictedViewportWidth();
		}
		super.positionBounds(gameUI, viewport);
	}

	@Override
	protected void innerPositionBounds(final GameUI gameUI, final Viewport viewport) {
		this.internalFramesContainer.positionBounds(gameUI, viewport);
	}

	/**
	 * Centered text centers each of its lines, not only the block of them. A line of several colors is several frames
	 * at the same height.
	 */
	private void centerLines(final float width) {
		final Map<Float, Float> lineEnds = new HashMap<>();
		for (final SingleStringFrame internalFrame : this.internalFrames) {
			lineEnds.merge(internalFrame.getLineY(), internalFrame.getLineX() + internalFrame.getAssignedWidth(),
					Math::max);
		}
		for (final SingleStringFrame internalFrame : this.internalFrames) {
			final float lineY = internalFrame.getLineY();
			internalFrame.addAnchor(new AnchorDefinition(FramePoint.TOPLEFT,
					internalFrame.getLineX() + ((width - lineEnds.get(lineY)) / 2), lineY));
		}
	}

	/**
	 * Splits the text into one frame per run of one color on one line. "|cAARRGGBB" starts a color, "|r" ends it and
	 * "|n" breaks the line; spaces next to them are kept. Text wraps at spaces when the frame has a width.
	 */
	private void createInternalFrames(final GlyphLayout glyphLayout) {
		for (final SingleStringFrame internalFrame : this.internalFrames) {
			this.internalFramesContainer.remove(internalFrame);
		}
		this.internalFrames.clear();
		final LineBuilder line = new LineBuilder(glyphLayout, getAssignedWidth());
		final String text = this.displayText;
		final int length = text.length();
		for (int i = 0; i < length; i++) {
			final char c = text.charAt(i);
			if ((c == '|') && ((i + 1) < length)) {
				final char escaped = text.charAt(i + 1);
				if (((escaped == 'c') || (escaped == 'C')) && ((i + 10) <= length)) {
					final Color parsed = ColorCodedText.parseColor(text.substring(i + 2, i + 10));
					if (parsed != null) {
						line.setColor(parsed);
						i += 9;
						continue;
					}
				}
				else if ((escaped == 'r') || (escaped == 'R')) {
					line.setColor(this.color);
					i++;
					continue;
				}
				else if ((escaped == 'n') || (escaped == 'N')) {
					line.breakLine();
					i++;
					continue;
				}
				line.appendChar(c);
			}
			else if ((c == '\r') || (c == '\n')) {
				if ((c == '\r') && ((i + 1) < length) && (text.charAt(i + 1) == '\n')) {
					i++;
				}
				line.breakLine();
			}
			else {
				line.appendChar(c);
			}
		}
		line.finish();
		final float usedWidthMax = line.usedWidthMax;
		final float usedHeight = line.lineTop + this.frameFont.getCapHeight();

		if (this.lineJustifyH == TextJustify.CENTER) {
			centerLines(usedWidthMax);
		}
		this.internalFramesContainer.setWidth(usedWidthMax);
		this.internalFramesContainer.setHeight(usedHeight);
		this.predictedViewportHeight = (usedHeight - this.frameFont.getCapHeight()) + getLineSpacing();
		this.predictedViewportWidth = usedWidthMax;

		this.internalFramesContainer.clearFramePointAssignments();
		switch (this.justifyH) {
		case CENTER:
			switch (this.justifyV) {
			case MIDDLE:
				this.internalFramesContainer.addAnchor(new AnchorDefinition(FramePoint.CENTER, 0, 0));
				break;
			case BOTTOM:
				this.internalFramesContainer.addAnchor(new AnchorDefinition(FramePoint.BOTTOM, 0, 0));
				break;
			case TOP:
			default:
				this.internalFramesContainer.addAnchor(new AnchorDefinition(FramePoint.TOP, 0, 0));
				break;
			}
			break;
		case RIGHT:
			switch (this.justifyV) {
			case MIDDLE:
				this.internalFramesContainer.addAnchor(new AnchorDefinition(FramePoint.RIGHT, 0, 0));
				break;
			case BOTTOM:
				this.internalFramesContainer.addAnchor(new AnchorDefinition(FramePoint.BOTTOMRIGHT, 0, 0));
				break;
			case TOP:
			default:
				this.internalFramesContainer.addAnchor(new AnchorDefinition(FramePoint.TOPRIGHT, 0, 0));
				break;
			}
			break;
		case LEFT:
		default:
			switch (this.justifyV) {
			case MIDDLE:
				this.internalFramesContainer.addAnchor(new AnchorDefinition(FramePoint.LEFT, 0, 0));
				break;
			case BOTTOM:
				this.internalFramesContainer.addAnchor(new AnchorDefinition(FramePoint.BOTTOMLEFT, 0, 0));
				break;
			case TOP:
			default:
				this.internalFramesContainer.addAnchor(new AnchorDefinition(FramePoint.TOPLEFT, 0, 0));
				break;
			}
			break;
		}

		for (final SingleStringFrame internalFrame : this.internalFrames) {
			this.internalFramesContainer.add(internalFrame);
		}
	}

	/**
	 * Lays out words and colored runs line by line. A run starts where its first glyph would be in the whole line,
	 * spaces before it included.
	 */
	private final class LineBuilder {
		private final GlyphLayout glyphLayout;
		private final float wrapWidth;
		private final StringBuilder word = new StringBuilder();
		private final StringBuilder run = new StringBuilder();
		private final TextPen pen = new TextPen(StringFrame.this.frameFont);
		private Color runColor = StringFrame.this.color;
		private Color color = StringFrame.this.color;
		private float runX;
		private float lineTop;
		private float usedWidthMax;
		private boolean lineEmpty = true;

		private LineBuilder(final GlyphLayout glyphLayout, final float wrapWidth) {
			this.glyphLayout = glyphLayout;
			this.wrapWidth = wrapWidth;
		}

		void appendChar(final char c) {
			this.word.append(c);
			if (c == ' ') {
				endWord();
			}
		}

		void setColor(final Color color) {
			endWord();
			endRun();
			this.color = color;
		}

		void breakLine() {
			endWord();
			endRun();
			if (this.lineEmpty) {
				addFrame("", 0);
			}
			newLine();
		}

		void finish() {
			endWord();
			endRun();
			if (this.lineEmpty) {
				addFrame("", 0);
			}
		}

		private void endWord() {
			if (this.word.length() == 0) {
				return;
			}
			final String wordString = this.word.toString();
			this.word.setLength(0);
			if ((this.wrapWidth > 0) && this.pen.isLineStarted()
					&& ((this.pen.nextX(wordString.charAt(0)) + measure(wordString)) >= this.wrapWidth)) {
				endRun();
				newLine();
			}
			final float wordX = this.pen.place(wordString);
			if (this.run.length() == 0) {
				this.runX = wordX;
				this.runColor = this.color;
			}
			this.run.append(wordString);
		}

		private void endRun() {
			if (this.run.length() == 0) {
				return;
			}
			addFrame(this.run.toString(), this.runX);
			this.run.setLength(0);
		}

		private void newLine() {
			this.lineTop += getLineSpacing();
			this.pen.newLine();
			this.lineEmpty = true;
		}

		private void addFrame(final String text, final float x) {
			final float width = measure(text);
			this.usedWidthMax = Math.max(this.usedWidthMax, x + width);
			final SingleStringFrame frame = new SingleStringFrame(text, StringFrame.this.internalFramesContainer,
					this.runColor, TextJustify.LEFT, TextJustify.TOP, StringFrame.this.frameFont);
			frame.setHeight(getLineSpacing());
			frame.setWidth(width);
			frame.setAlpha(StringFrame.this.alpha);
			frame.setFontShadowColor(StringFrame.this.fontShadowColor);
			frame.setFontShadowOffsetX(StringFrame.this.fontShadowOffsetX);
			frame.setFontShadowOffsetY(StringFrame.this.fontShadowOffsetY);
			frame.addAnchor(new AnchorDefinition(FramePoint.TOPLEFT, x, -this.lineTop));
			StringFrame.this.internalFrames.add(frame);
			this.runColor = this.color;
			this.lineEmpty = false;
		}

		private float measure(final String text) {
			this.glyphLayout.setText(StringFrame.this.frameFont, text);
			return this.glyphLayout.width;
		}
	}

	public void setAlpha(final float alpha) {
		this.alpha = alpha;
		for (final SingleStringFrame internalFrame : this.internalFrames) {
			internalFrame.setAlpha(alpha);
		}

	}

	public float getPredictedViewportHeight() {
		return this.predictedViewportHeight;
	}

	public float getPredictedViewportWidth() {
		return this.predictedViewportWidth;
	}

	public float getLineSpacing() {
		return this.lineSpacing > 0 ? this.lineSpacing : this.frameFont.getLineHeight();
	}

	public void setLineSpacing(final float lineSpacing) {
		this.lineSpacing = lineSpacing;
	}

	public BitmapFont getFrameFont() {
		return this.frameFont;
	}

	public void setPasswordField(final boolean passwordField) {
		this.passwordField = passwordField;
	}
}
