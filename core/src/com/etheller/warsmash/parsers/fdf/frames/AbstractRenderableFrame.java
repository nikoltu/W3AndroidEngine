package com.etheller.warsmash.parsers.fdf.frames;

import java.util.EnumMap;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.AnchorDefinition;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.util.Log;

public abstract class AbstractRenderableFrame implements UIFrame {
	private static final FramePoint[] LEFT_ANCHOR_PRIORITY = { FramePoint.LEFT, FramePoint.TOPLEFT,
			FramePoint.BOTTOMLEFT };
	private static final FramePoint[] RIGHT_ANCHOR_PRIORITY = { FramePoint.RIGHT, FramePoint.TOPRIGHT,
			FramePoint.BOTTOMRIGHT };
	private static final FramePoint[] CENTER_HORIZ_ANCHOR_PRIORITY = { FramePoint.CENTER, FramePoint.TOP,
			FramePoint.BOTTOM };
	private static final FramePoint[] CENTER_VERT_ANCHOR_PRIORITY = { FramePoint.CENTER, FramePoint.LEFT,
			FramePoint.RIGHT };
	private static final FramePoint[] TOP_ANCHOR_PRIORITY = { FramePoint.TOP, FramePoint.TOPLEFT, FramePoint.TOPRIGHT };
	private static final FramePoint[] BOTTOM_ANCHOR_PRIORITY = { FramePoint.BOTTOM, FramePoint.BOTTOMLEFT,
			FramePoint.BOTTOMRIGHT };
	private static final boolean DEBUG_LOG = false;
	protected String name;
	protected UIFrame parent;
	protected boolean visible = true;
	/** A map script hid the frame: the game keeps showing and hiding it as it likes, and it stays out of sight. */
	private boolean scriptHidden;
	private float renderScale = 1f;
	protected int level;
	private float renderAlpha = 1f;
	protected final Rectangle renderBounds = new Rectangle(0, 0, 0, 0); // in libgdx rendering space
	private final EnumMap<FramePoint, FramePointAssignment> framePointToAssignment = new EnumMap<>(FramePoint.class);
	protected float assignedHeight;
	protected float assignedWidth;

	public AbstractRenderableFrame(final String name, final UIFrame parent) {
		this.name = name;
		this.parent = parent;
	}

	@Override
	public void setSetAllPoints(final boolean setAllPoints) {
		for (final FramePoint framePoint : FramePoint.values()) {
			if (!this.framePointToAssignment.containsKey(framePoint)) {
				this.framePointToAssignment.put(framePoint, new SetPoint(framePoint, this.parent, framePoint, 0, 0));
			}
		}
	}

	@Override
	public void setSetAllPoints(final boolean setAllPoints, final float inset) {
		this.framePointToAssignment.put(FramePoint.TOPLEFT,
				new SetPoint(FramePoint.TOPLEFT, this.parent, FramePoint.TOPLEFT, inset, -inset));
		this.framePointToAssignment.put(FramePoint.LEFT,
				new SetPoint(FramePoint.LEFT, this.parent, FramePoint.LEFT, inset, 0));
		this.framePointToAssignment.put(FramePoint.BOTTOMLEFT,
				new SetPoint(FramePoint.BOTTOMLEFT, this.parent, FramePoint.BOTTOMLEFT, inset, inset));
		this.framePointToAssignment.put(FramePoint.BOTTOM,
				new SetPoint(FramePoint.BOTTOM, this.parent, FramePoint.BOTTOM, 0, inset));
		this.framePointToAssignment.put(FramePoint.BOTTOMRIGHT,
				new SetPoint(FramePoint.BOTTOMRIGHT, this.parent, FramePoint.BOTTOMRIGHT, -inset, inset));
		this.framePointToAssignment.put(FramePoint.RIGHT,
				new SetPoint(FramePoint.RIGHT, this.parent, FramePoint.RIGHT, -inset, 0));
		this.framePointToAssignment.put(FramePoint.TOPRIGHT,
				new SetPoint(FramePoint.TOPRIGHT, this.parent, FramePoint.TOPRIGHT, -inset, -inset));
		this.framePointToAssignment.put(FramePoint.TOP,
				new SetPoint(FramePoint.TOP, this.parent, FramePoint.TOP, 0, -inset));
		this.framePointToAssignment.put(FramePoint.CENTER,
				new SetPoint(FramePoint.CENTER, this.parent, FramePoint.CENTER, 0, 0));
	}

	@Override
	public void setWidth(final float width) {
		this.assignedWidth = width;
		this.renderBounds.width = width;
	}

	@Override
	public float getAssignedWidth() {
		return this.assignedWidth;
	}

	@Override
	public float getAssignedHeight() {
		return this.assignedHeight;
	}

	@Override
	public void setHeight(final float height) {
		this.assignedHeight = height;
		this.renderBounds.height = height;
	}

	private FramePointAssignment getByPriority(final FramePoint[] priorities) {
		for (final FramePoint priorityFramePoint : priorities) {
			final FramePointAssignment framePointAssignment = this.framePointToAssignment.get(priorityFramePoint);
			if (framePointAssignment != null) {
				return framePointAssignment;
			}
		}
		return null;
	}

	public void clearFramePointAssignments() {
		this.framePointToAssignment.clear();
	}

	/** Opposite anchors decide the size of a frame; a size given before them does not apply. */
	public void anchorsDecideSize() {
		if ((getLeftAnchor() != null) && (getRightAnchor() != null)) {
			this.assignedWidth = 0;
		}
		if ((getTopAnchor() != null) && (getBottomAnchor() != null)) {
			this.assignedHeight = 0;
		}
	}

	/** The width the frame has: its own, else what its left and right points leave between them; 0 when neither. */
	protected float anchoredWidth(final GameUI gameUI, final Viewport viewport) {
		if (this.assignedWidth != 0) {
			return this.assignedWidth;
		}
		final FramePointAssignment left = getLeftAnchor();
		final FramePointAssignment right = getRightAnchor();
		return (left != null) && (right != null) ? Math.max(0, right.getX(gameUI, viewport) - left.getX(gameUI, viewport))
				: 0;
	}

	private FramePointAssignment getLeftAnchor() {
		return getByPriority(LEFT_ANCHOR_PRIORITY);
	}

	private FramePointAssignment getRightAnchor() {
		return getByPriority(RIGHT_ANCHOR_PRIORITY);
	}

	private FramePointAssignment getTopAnchor() {
		return getByPriority(TOP_ANCHOR_PRIORITY);
	}

	private FramePointAssignment getBottomAnchor() {
		return getByPriority(BOTTOM_ANCHOR_PRIORITY);
	}

	private FramePointAssignment getCenterHorizontalAnchor() {
		return getByPriority(CENTER_HORIZ_ANCHOR_PRIORITY);
	}

	private FramePointAssignment getCenterVerticalAnchor() {
		return getByPriority(CENTER_VERT_ANCHOR_PRIORITY);
	}

	@Override
	public float getFramePointX(final FramePoint framePoint) {
		switch (framePoint) {
		case CENTER:
		case BOTTOM:
		case TOP:
			return this.renderBounds.x + (this.renderBounds.width / 2);
		case BOTTOMLEFT:
		case LEFT:
		case TOPLEFT:
			return this.renderBounds.x;
		case BOTTOMRIGHT:
		case RIGHT:
		case TOPRIGHT:
			return this.renderBounds.x + this.renderBounds.width;
		default:
			return 0;
		}
	}

	@Override
	public void setFramePointX(final FramePoint framePoint, final float x) {
		if (this.renderBounds.width == 0) {
			this.renderBounds.x = x;
			return;
		}
		switch (framePoint) {
		case CENTER:
		case BOTTOM:
		case TOP:
			this.renderBounds.x = x - (this.renderBounds.width / 2);
			return;
		case BOTTOMLEFT:
		case LEFT:
		case TOPLEFT:
			if (getRightAnchor() != null) {
				final float oldRightX = this.renderBounds.x + this.renderBounds.width;
				this.renderBounds.x = x;
				this.renderBounds.width = oldRightX - x;
			}
			else {
				// no right anchor, keep width
				this.renderBounds.x = x;
			}
			return;
		case BOTTOMRIGHT:
		case RIGHT:
		case TOPRIGHT:
			if (getLeftAnchor() != null) {
				this.renderBounds.width = x - this.renderBounds.x;
			}
			else {
				this.renderBounds.x = x - this.renderBounds.width;
			}
			return;
		default:
			return;
		}
	}

	@Override
	public float getFramePointY(final FramePoint framePoint) {
		switch (framePoint) {
		case LEFT:
		case CENTER:
		case RIGHT:
			return this.renderBounds.y + (this.renderBounds.height / 2);
		case BOTTOMLEFT:
		case BOTTOM:
		case BOTTOMRIGHT:
			return this.renderBounds.y;
		case TOPLEFT:
		case TOP:
		case TOPRIGHT:
			return this.renderBounds.y + this.renderBounds.height;
		default:
			return 0;
		}
	}

	@Override
	public void setFramePointY(final FramePoint framePoint, final float y) {
		if (this.renderBounds.height == 0) {
			this.renderBounds.y = y;
			return;
		}
		switch (framePoint) {
		case LEFT:
		case CENTER:
		case RIGHT:
			this.renderBounds.y = y - (this.renderBounds.height / 2);
			return;
		case TOPLEFT:
		case TOP:
		case TOPRIGHT:
			if (getBottomAnchor() != null) {
				this.renderBounds.height = y - this.renderBounds.y;
			}
			else {
				this.renderBounds.y = y - this.renderBounds.height;
			}
			return;
		case BOTTOMLEFT:
		case BOTTOM:
		case BOTTOMRIGHT:
			if (getTopAnchor() != null) {
				final float oldBottomY = this.renderBounds.y + this.renderBounds.height;
				this.renderBounds.y = y;
				this.renderBounds.height = oldBottomY - y;
			}
			else {
				this.renderBounds.y = y;
			}
			return;
		default:
			return;
		}
	}

	@Override
	public void addAnchor(final AnchorDefinition anchorDefinition) {
		this.framePointToAssignment.put(anchorDefinition.getMyPoint(), new SetPoint(anchorDefinition.getMyPoint(),
				this.parent, anchorDefinition.getMyPoint(), anchorDefinition.getX(), anchorDefinition.getY()));
	}

	@Override
	public void addSetPoint(final SetPoint setPointDefinition) {
		this.framePointToAssignment.put(setPointDefinition.getMyPoint(), setPointDefinition);
	}

	@Override
	public void positionBounds(final GameUI gameUI, final Viewport viewport) {
		if (this.parent == null) {
			// TODO this is a bit of a hack, remove later
			return;
		}
		if (this.framePointToAssignment.isEmpty()) {
			this.renderBounds.x = this.parent.getFramePointX(FramePoint.LEFT);
			this.renderBounds.y = this.parent.getFramePointY(FramePoint.BOTTOM);
		}
		else {
			final FramePointAssignment leftAnchor = getLeftAnchor();
			final FramePointAssignment rightAnchor = getRightAnchor();
			final FramePointAssignment topAnchor = getTopAnchor();
			final FramePointAssignment bottomAnchor = getBottomAnchor();
			final FramePointAssignment centerHorizontalAnchor = getCenterHorizontalAnchor();
			final FramePointAssignment centerVerticalAnchor = getCenterVerticalAnchor();
			if (leftAnchor != null) {
				this.renderBounds.x = leftAnchor.getX(gameUI, viewport);
				if (this.assignedWidth == 0) {
					if (rightAnchor != null) {
						this.renderBounds.width = rightAnchor.getX(gameUI, viewport) - this.renderBounds.x;
					}
					else if (centerHorizontalAnchor != null) {
						this.renderBounds.width = (centerHorizontalAnchor.getX(gameUI, viewport) - this.renderBounds.x)
								* 2;
					}
				}
			}
			else if (rightAnchor != null) {
				this.renderBounds.x = rightAnchor.getX(gameUI, viewport) - this.renderBounds.width;
				if (centerHorizontalAnchor != null) {
					this.renderBounds.width = (this.renderBounds.x - centerHorizontalAnchor.getX(gameUI, viewport)) * 2;
				}
			}
			else if (centerHorizontalAnchor != null) {
				this.renderBounds.x = centerHorizontalAnchor.getX(gameUI, viewport) - (this.renderBounds.width / 2);
			}
			if (bottomAnchor != null) {
				this.renderBounds.y = bottomAnchor.getY(gameUI, viewport);
				if (this.assignedHeight == 0) {
					if (topAnchor != null) {
						this.renderBounds.height = topAnchor.getY(gameUI, viewport) - this.renderBounds.y;
					}
					else if (centerVerticalAnchor != null) {
						this.renderBounds.height = (centerVerticalAnchor.getY(gameUI, viewport) - this.renderBounds.y)
								* 2;
					}
				}
			}
			else if (topAnchor != null) {
				this.renderBounds.y = topAnchor.getY(gameUI, viewport) - this.renderBounds.height;
				if (centerVerticalAnchor != null) {
					this.renderBounds.height = (this.renderBounds.y - centerVerticalAnchor.getY(gameUI, viewport)) * 2;
				}
			}
			else if (centerVerticalAnchor != null) {
				this.renderBounds.y = centerVerticalAnchor.getY(gameUI, viewport) - (this.renderBounds.height / 2);
			}
		}
		if (DEBUG_LOG) {
			Log.info(getClass().getSimpleName() + ":" + this.name + ":" + hashCode()
					+ " finishing position bounds: " + this.renderBounds);
		}
		innerPositionBounds(gameUI, viewport);
	}

	protected abstract void innerPositionBounds(GameUI gameUI, final Viewport viewport);

	@Override
	public boolean isVisible() {
		return this.visible && !this.scriptHidden;
	}

	public void setScriptHidden(final boolean scriptHidden) {
		this.scriptHidden = scriptHidden;
	}

	/**
	 * A frame made under an owner that keeps no children of its own is held by the root, so the owner's hiding does not
	 * reach it by the usual way of the owner not drawing its children.
	 */
	public boolean isHiddenByAncestor() {
		for (UIFrame ancestor = this.parent; ancestor != null; ancestor = ancestor.getParent()) {
			if (!ancestor.isVisible()) {
				return true;
			}
		}
		return false;
	}

	public boolean isScriptHidden() {
		return this.scriptHidden;
	}

	/** Draws the frame and what it holds enlarged about its center; input finds it where it is drawn. */
	public void setRenderScale(final float renderScale) {
		this.renderScale = renderScale;
	}

	public float getRenderScale() {
		return this.renderScale;
	}

	/** The point of the frame's parent that lands on a point of the screen once this frame's scale is undone. */
	public static float localX(final UIFrame frame, final float x) {
		if ((frame instanceof AbstractRenderableFrame) && (((AbstractRenderableFrame) frame).renderScale != 1f)) {
			final AbstractRenderableFrame scaled = (AbstractRenderableFrame) frame;
			final float center = scaled.renderBounds.x + (scaled.renderBounds.width / 2);
			return center + ((x - center) / scaled.renderScale);
		}
		return x;
	}

	public static float localY(final UIFrame frame, final float y) {
		if ((frame instanceof AbstractRenderableFrame) && (((AbstractRenderableFrame) frame).renderScale != 1f)) {
			final AbstractRenderableFrame scaled = (AbstractRenderableFrame) frame;
			final float center = scaled.renderBounds.y + (scaled.renderBounds.height / 2);
			return center + ((y - center) / scaled.renderScale);
		}
		return y;
	}

	/**
	 * A frame that has a scale of its own and sits on the screen, or on frames that do, is drawn at that scale alone:
	 * the scale of the frames it is a child of does not shrink it again.
	 */
	boolean ignoresAncestorScale() {
		return ignoresAncestorScale(0);
	}

	private boolean ignoresAncestorScale(final int depth) {
		if ((this.renderScale == 1f) || (depth > 8) || this.framePointToAssignment.isEmpty()) {
			return false;
		}
		for (final FramePointAssignment assignment : this.framePointToAssignment.values()) {
			if (!(assignment instanceof SetPoint)) {
				return false;
			}
			final UIFrame other = ((SetPoint) assignment).getOther();
			if ((other == null) || (other.getParent() == null)) {
				continue;
			}
			if (!((other instanceof AbstractRenderableFrame)
					&& ((AbstractRenderableFrame) other).ignoresAncestorScale(depth + 1))) {
				return false;
			}
		}
		return true;
	}

	/** The frames above one that is drawn at its own scale alone are already off the matrix its children get. */
	private static boolean stopsScaleChain(final UIFrame frame) {
		return (frame instanceof AbstractRenderableFrame) && ((AbstractRenderableFrame) frame).ignoresAncestorScale();
	}

	private boolean hasScaledAncestor() {
		for (UIFrame ancestor = this.parent; ancestor != null; ancestor = ancestor.getParent()) {
			if ((ancestor instanceof AbstractRenderableFrame)
					&& (((AbstractRenderableFrame) ancestor).renderScale != 1f)) {
				return true;
			}
			if (stopsScaleChain(ancestor)) {
				break;
			}
		}
		return false;
	}

	/** The batch's matrix once the scales of the frames this one sits in are taken off again. */
	private void undoAncestorScales(final Matrix4 matrix) {
		for (UIFrame ancestor = this.parent; ancestor != null; ancestor = ancestor.getParent()) {
			if ((ancestor instanceof AbstractRenderableFrame)
					&& (((AbstractRenderableFrame) ancestor).renderScale != 1f)) {
				final AbstractRenderableFrame scaled = (AbstractRenderableFrame) ancestor;
				final float centerX = scaled.renderBounds.x + (scaled.renderBounds.width / 2);
				final float centerY = scaled.renderBounds.y + (scaled.renderBounds.height / 2);
				matrix.translate(centerX, centerY, 0).scale(1f / scaled.renderScale, 1f / scaled.renderScale, 1f)
						.translate(-centerX, -centerY, 0);
			}
			if (stopsScaleChain(ancestor)) {
				break;
			}
		}
	}

	/** A point the parent received, as the child takes it; inverse of what the drawing does to the child. */
	public static float childX(final UIFrame parent, final UIFrame child, final float x) {
		float result = x;
		if ((child instanceof AbstractRenderableFrame) && ((AbstractRenderableFrame) child).ignoresAncestorScale()) {
			for (UIFrame frame = parent; frame != null; frame = frame.getParent()) {
				if ((frame instanceof AbstractRenderableFrame)
						&& (((AbstractRenderableFrame) frame).renderScale != 1f)) {
					final AbstractRenderableFrame scaled = (AbstractRenderableFrame) frame;
					final float center = scaled.renderBounds.x + (scaled.renderBounds.width / 2);
					result = center + ((result - center) * scaled.renderScale);
				}
				if (stopsScaleChain(frame)) {
					break;
				}
			}
		}
		return localX(child, result);
	}

	public static float childY(final UIFrame parent, final UIFrame child, final float y) {
		float result = y;
		if ((child instanceof AbstractRenderableFrame) && ((AbstractRenderableFrame) child).ignoresAncestorScale()) {
			for (UIFrame frame = parent; frame != null; frame = frame.getParent()) {
				if ((frame instanceof AbstractRenderableFrame)
						&& (((AbstractRenderableFrame) frame).renderScale != 1f)) {
					final AbstractRenderableFrame scaled = (AbstractRenderableFrame) frame;
					final float center = scaled.renderBounds.y + (scaled.renderBounds.height / 2);
					result = center + ((result - center) * scaled.renderScale);
				}
				if (stopsScaleChain(frame)) {
					break;
				}
			}
		}
		return localY(child, result);
	}

	public int getLevel() {
		return this.level;
	}

	@Override
	public void setVisible(final boolean visible) {
		this.visible = visible;
	}

	public void setLevel(final int level) {
		this.level = level;
	}

	@Override
	public final void render(final SpriteBatch batch, final BitmapFont font20, final GlyphLayout glyphLayout) {
		if (this.visible && !this.scriptHidden && !isHiddenByAncestor()) {
			final boolean detached = ignoresAncestorScale() && hasScaledAncestor();
			final Matrix4 attached = detached ? new Matrix4(batch.getTransformMatrix()) : null;
			if (detached) {
				final Matrix4 own = new Matrix4(attached);
				undoAncestorScales(own);
				batch.setTransformMatrix(own);
			}
			if (this.renderScale != 1f) {
				renderScaled(batch, font20, glyphLayout);
			}
			else {
				renderFaded(batch, font20, glyphLayout);
			}
			if (detached) {
				batch.setTransformMatrix(attached);
			}
		}
	}

	private void renderScaled(final SpriteBatch batch, final BitmapFont font20, final GlyphLayout glyphLayout) {
		final float centerX = this.renderBounds.x + (this.renderBounds.width / 2);
		final float centerY = this.renderBounds.y + (this.renderBounds.height / 2);
		final Matrix4 outer = new Matrix4(batch.getTransformMatrix());
		final Matrix4 scaled = new Matrix4(outer).translate(centerX, centerY, 0).scale(this.renderScale,
				this.renderScale, 1f).translate(-centerX, -centerY, 0);
		batch.setTransformMatrix(scaled);
		renderFaded(batch, font20, glyphLayout);
		batch.setTransformMatrix(outer);
	}

	private void renderFaded(final SpriteBatch batch, final BitmapFont font20, final GlyphLayout glyphLayout) {
		if ((this.renderAlpha < 1f) && (batch instanceof FadingSpriteBatch)) {
			if (this.renderAlpha <= 0f) {
				return;
			}
			final FadingSpriteBatch fadingBatch = (FadingSpriteBatch) batch;
			final float outerAlpha = fadingBatch.getAlphaScale();
			fadingBatch.setAlphaScale(outerAlpha * this.renderAlpha);
			internalRender(batch, font20, glyphLayout);
			fadingBatch.setAlphaScale(outerAlpha);
		}
		else {
			internalRender(batch, font20, glyphLayout);
		}
	}

	/**
	 * Opacity of this frame and everything drawn inside it, when drawn through a {@link FadingSpriteBatch}.
	 */
	public void setRenderAlpha(final float renderAlpha) {
		this.renderAlpha = renderAlpha;
	}

	public float getRenderAlpha() {
		return this.renderAlpha;
	}

	@Override
	public UIFrame getParent() {
		return this.parent;
	}

	@Override
	public void setParent(final UIFrame parent) {
		this.parent = parent;
	}

	@Override
	public boolean isVisibleOnScreen() {
		boolean visibleOnScreen = isVisible();
		UIFrame ancestor = this.parent;
		while (visibleOnScreen && (ancestor != null)) {
			visibleOnScreen &= ancestor.isVisible();
			ancestor = ancestor.getParent();
		}
		return visibleOnScreen;
	}

	protected abstract void internalRender(SpriteBatch batch, BitmapFont baseFont, GlyphLayout glyphLayout);

	@Override
	public UIFrame touchDown(final float screenX, final float screenY, final int button) {
		return null;
	}

	@Override
	public UIFrame touchUp(final float screenX, final float screenY, final int button) {
		return null;
	}

	@Override
	public UIFrame getFrameChildUnderMouse(final float screenX, final float screenY) {
		return null;
	}

	@Override
	public String getName() {
		return this.name;
	}

	public Rectangle getRenderBounds() {
		return this.renderBounds;
	}
}
