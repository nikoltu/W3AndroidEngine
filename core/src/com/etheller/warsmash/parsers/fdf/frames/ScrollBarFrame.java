package com.etheller.warsmash.parsers.fdf.frames;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.command.ClickableFrame;

public class ScrollBarFrame extends AbstractRenderableFrame implements ClickableFrame {
	private final boolean vertical;
	// Fingers need more than the thin track of a slider to land on.
	private static final float SLIDER_GRAB_HEIGHT = 0.024f;
	private static final float SLIDER_GRAB_SIDE = 0.006f;
	private UIFrame controlBackdrop;
	private UIFrame controlDisabledBackdrop;
	private boolean enabled = true;
	private float grabMarginX;
	private float grabMarginY;
	private float pressX;
	private float pressY;
	private UIFrame incButtonFrame;
	private UIFrame decButtonFrame;
	private UIFrame thumbButtonFrame;
	private int scrollValuePercent = 50;
	private ScrollBarChangeListener changeListener = ScrollBarChangeListener.DO_NOTHING;
	private int minValue = 0;
	private int maxValue = 100;
	private int stepSize = 10;

	public ScrollBarFrame(final String name, final UIFrame parent, final boolean vertical) {
		super(name, parent);
		this.vertical = vertical;
	}

	public void setControlBackdrop(final UIFrame controlBackdrop) {
		this.controlBackdrop = controlBackdrop;
	}

	public void setControlDisabledBackdrop(final UIFrame controlDisabledBackdrop) {
		this.controlDisabledBackdrop = controlDisabledBackdrop;
	}

	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
		if (this.thumbButtonFrame instanceof GlueButtonFrame) {
			((GlueButtonFrame) this.thumbButtonFrame).setEnabled(enabled);
		}
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	private boolean isInGrabArea(final float screenX, final float screenY) {
		return isVisible() && this.enabled && (screenX >= (this.renderBounds.x - this.grabMarginX))
				&& (screenX <= (this.renderBounds.x + this.renderBounds.width + this.grabMarginX))
				&& (screenY >= (this.renderBounds.y - this.grabMarginY))
				&& (screenY <= (this.renderBounds.y + this.renderBounds.height + this.grabMarginY));
	}

	public void setIncButtonFrame(final UIFrame incButtonFrame) {
		this.incButtonFrame = incButtonFrame;
		((GlueButtonFrame) incButtonFrame).setButtonListener(new GlueButtonFrame.ButtonListener() {
			@Override
			public void mouseDown(final GameUI gameUI, final Viewport uiViewport) {

			}

			@Override
			public void mouseUp(final GameUI gameUI, final Viewport uiViewport) {
				setValue(gameUI, uiViewport, ScrollBarFrame.this.scrollValuePercent + ScrollBarFrame.this.stepSize);
			}

			@Override
			public void mouseDragged(final GameUI rootFrame, final Viewport uiViewport, final float x, final float y) {

			}
		});
	}

	public void setDecButtonFrame(final UIFrame decButtonFrame) {
		this.decButtonFrame = decButtonFrame;
		((GlueButtonFrame) decButtonFrame).setButtonListener(new GlueButtonFrame.ButtonListener() {
			@Override
			public void mouseDown(final GameUI gameUI, final Viewport uiViewport) {

			}

			@Override
			public void mouseUp(final GameUI gameUI, final Viewport uiViewport) {
				setValue(gameUI, uiViewport, ScrollBarFrame.this.scrollValuePercent - ScrollBarFrame.this.stepSize);
			}

			@Override
			public void mouseDragged(final GameUI rootFrame, final Viewport uiViewport, final float x, final float y) {

			}
		});
	}

	public void setThumbButtonFrame(final UIFrame thumbButtonFrame) {
		if (this.thumbButtonFrame instanceof GlueButtonFrame) {
			((GlueButtonFrame) this.thumbButtonFrame).setButtonListener(GlueButtonFrame.ButtonListener.DO_NOTHING);
		}
		this.thumbButtonFrame = thumbButtonFrame;
		if (thumbButtonFrame instanceof GlueButtonFrame) {
			final GlueButtonFrame frame = (GlueButtonFrame) thumbButtonFrame;
			frame.setButtonListener(new GlueButtonFrame.ButtonListener() {
				@Override
				public void mouseDown(final GameUI gameUI, final Viewport uiViewport) {
				}

				@Override
				public void mouseUp(final GameUI gameUI, final Viewport uiViewport) {
				}

				@Override
				public void mouseDragged(final GameUI rootFrame, final Viewport uiViewport, final float x,
						final float y) {
					ScrollBarFrame.this.mouseDragged(rootFrame, uiViewport, x, y);
				}
			});
		}
	}

	public void setMinValue(final int minValue) {
		this.minValue = minValue;
	}

	public void setMaxValue(final int maxValue) {
		this.maxValue = maxValue;
	}

	public void setStepSize(final int stepSize) {
		this.stepSize = stepSize;
	}

	public int getMaxValue() {
		return this.maxValue;
	}

	/** Changes the range and value without telling the listener, for an owner that keeps them in its own units. */
	public void setRange(final int maxValue, final int value) {
		this.maxValue = maxValue;
		this.scrollValuePercent = Math.min(maxValue, Math.max(this.minValue, value));
	}

	private float getMaxThumbButtonTravelDistance() {
		if (this.vertical) {
			final float incButtonFrameHeight = this.incButtonFrame == null ? 0
					: this.incButtonFrame.getAssignedHeight();
			final float decButtonFrameHeight = this.decButtonFrame == null ? 0
					: this.decButtonFrame.getAssignedHeight();
			return this.renderBounds.height - thumbButtonHeight() - incButtonFrameHeight
					- decButtonFrameHeight;
		}
		else {
			final float incButtonFrameWidth = this.incButtonFrame == null ? 0 : this.incButtonFrame.getAssignedWidth();
			final float decButtonFrameWidth = this.decButtonFrame == null ? 0 : this.decButtonFrame.getAssignedWidth();
			return this.renderBounds.width - thumbButtonWidth() - incButtonFrameWidth
					- decButtonFrameWidth;
		}
	}

	public void setValue(final GameUI gameUI, final Viewport uiViewport, final int percent) {
		this.scrollValuePercent = Math.min(this.maxValue, Math.max(this.minValue, percent));
		updateThumbButtonPoint();
		this.changeListener.onChange(gameUI, uiViewport, this.scrollValuePercent);
		positionBounds(gameUI, uiViewport);
	}

	public int getValue() {
		return this.scrollValuePercent;
	}

	private float thumbButtonHeight() {
		return this.thumbButtonFrame == null ? 0 : this.thumbButtonFrame.getAssignedHeight();
	}

	private float thumbButtonWidth() {
		return this.thumbButtonFrame == null ? 0 : this.thumbButtonFrame.getAssignedWidth();
	}

	public void updateThumbButtonPoint() {
		if (this.thumbButtonFrame == null) {
			return;
		}
		if (this.vertical) {
			final float newYValue = (this.scrollValuePercent / (float) this.maxValue)
					* getMaxThumbButtonTravelDistance();
			if (this.decButtonFrame != null) {
				this.thumbButtonFrame.addSetPoint(
						new SetPoint(FramePoint.BOTTOM, this.decButtonFrame, FramePoint.TOP, 0, newYValue));
			}
			else {
				this.thumbButtonFrame
						.addSetPoint(new SetPoint(FramePoint.BOTTOM, this, FramePoint.BOTTOM, 0, newYValue));
			}
		}
		else {
			final float newXValue = (this.scrollValuePercent / (float) this.maxValue)
					* getMaxThumbButtonTravelDistance();
			if (this.decButtonFrame != null) {
				this.thumbButtonFrame.addSetPoint(
						new SetPoint(FramePoint.LEFT, this.decButtonFrame, FramePoint.RIGHT, 0, newXValue));
			}
			else {
				this.thumbButtonFrame.addSetPoint(new SetPoint(FramePoint.LEFT, this, FramePoint.LEFT, newXValue, 0));
			}
		}
	}

	@Override
	protected void innerPositionBounds(final GameUI gameUI, final Viewport viewport) {
		if (this.controlBackdrop != null) {
			this.controlBackdrop.positionBounds(gameUI, viewport);
		}
		if (this.controlDisabledBackdrop != null) {
			this.controlDisabledBackdrop.positionBounds(gameUI, viewport);
		}
		if (!this.vertical) {
			this.grabMarginX = GameUI.convertX(viewport, SLIDER_GRAB_SIDE);
			this.grabMarginY = Math.max(0,
					(GameUI.convertY(viewport, SLIDER_GRAB_HEIGHT) - this.renderBounds.height) / 2);
		}
		if (this.incButtonFrame != null) {
			this.incButtonFrame.positionBounds(gameUI, viewport);
		}
		if (this.decButtonFrame != null) {
			this.decButtonFrame.positionBounds(gameUI, viewport);
		}
		updateThumbButtonPoint();
		if (this.thumbButtonFrame != null) {
			this.thumbButtonFrame.positionBounds(gameUI, viewport);
		}
	}

	@Override
	protected void internalRender(final SpriteBatch batch, final BitmapFont baseFont, final GlyphLayout glyphLayout) {
		final UIFrame backdrop = !this.enabled && (this.controlDisabledBackdrop != null) ? this.controlDisabledBackdrop
				: this.controlBackdrop;
		if (backdrop != null) {
			backdrop.render(batch, baseFont, glyphLayout);
		}
		if (this.incButtonFrame != null) {
			this.incButtonFrame.render(batch, baseFont, glyphLayout);
		}
		if (this.decButtonFrame != null) {
			this.decButtonFrame.render(batch, baseFont, glyphLayout);
		}
		if (this.thumbButtonFrame != null) {
			this.thumbButtonFrame.render(batch, baseFont, glyphLayout);
		}
	}

	@Override
	public void mouseDown(final GameUI gameUI, final Viewport uiViewport) {
		if (!this.vertical && this.enabled) {
			mouseDragged(gameUI, uiViewport, this.pressX, this.pressY);
		}
	}

	@Override
	public void mouseUp(final GameUI gameUI, final Viewport uiViewport) {
	}

	@Override
	public void mouseEnter(final GameUI gameUI, final Viewport uiViewport) {
	}

	@Override
	public void mouseExit(final GameUI gameUI, final Viewport uiViewport) {
	}

	@Override
	public void onClick(final int button) {
	}

	@Override
	public void mouseDragged(final GameUI rootFrame, final Viewport uiViewport, final float x, final float y) {
		final float maxThumbButtonTravelDistance = getMaxThumbButtonTravelDistance();
		final int newScrollValuePercent;
		if (this.vertical) {
			final float decFrameHeight = this.decButtonFrame == null ? 0 : this.decButtonFrame.getAssignedHeight();
			newScrollValuePercent = Math.min(this.maxValue, Math.max(this.minValue, Math
					.round(((y - this.renderBounds.y - decFrameHeight - (thumbButtonHeight() / 2))
							/ maxThumbButtonTravelDistance) * this.maxValue)));
		}
		else {
			final float decFrameWidth = this.decButtonFrame == null ? 0 : this.decButtonFrame.getAssignedWidth();
			newScrollValuePercent = Math.min(this.maxValue,
					Math.max(this.minValue, Math.round(
							((x - this.renderBounds.x - decFrameWidth - (thumbButtonWidth() / 2))
									/ maxThumbButtonTravelDistance) * this.maxValue)));
		}
		if (newScrollValuePercent != this.scrollValuePercent) {
			setValue(rootFrame, uiViewport, newScrollValuePercent);
			positionBounds(rootFrame, uiViewport);
		}

	}

	@Override
	public UIFrame touchUp(final float screenX, final float screenY, final int button) {
		if (isInGrabArea(screenX, screenY)) {
			UIFrame frameChildUnderMouse = this.thumbButtonFrame == null ? null
					: this.thumbButtonFrame.touchUp(screenX, screenY, button);
			if (frameChildUnderMouse != null) {
				return frameChildUnderMouse;
			}
			if (this.incButtonFrame != null) {
				frameChildUnderMouse = this.incButtonFrame.touchUp(screenX, screenY, button);
				if (frameChildUnderMouse != null) {
					return frameChildUnderMouse;
				}
			}
			if (this.decButtonFrame != null) {
				frameChildUnderMouse = this.decButtonFrame.touchUp(screenX, screenY, button);
				if (frameChildUnderMouse != null) {
					return frameChildUnderMouse;
				}
			}
			if (this.controlBackdrop != null) {
				frameChildUnderMouse = this.controlBackdrop.touchUp(screenX, screenY, button);
				if (frameChildUnderMouse != null) {
					return frameChildUnderMouse;
				}
			}
			return this;
		}
		return super.touchUp(screenX, screenY, button);
	}

	@Override
	public UIFrame touchDown(final float screenX, final float screenY, final int button) {
		if (isInGrabArea(screenX, screenY)) {
			this.pressX = screenX;
			this.pressY = screenY;
			UIFrame frameChildUnderMouse = this.thumbButtonFrame == null ? null
					: this.thumbButtonFrame.touchDown(screenX, screenY, button);
			if (frameChildUnderMouse != null) {
				return frameChildUnderMouse;
			}
			if (this.incButtonFrame != null) {
				frameChildUnderMouse = this.incButtonFrame.touchDown(screenX, screenY, button);
				if (frameChildUnderMouse != null) {
					return frameChildUnderMouse;
				}
			}
			if (this.decButtonFrame != null) {
				frameChildUnderMouse = this.decButtonFrame.touchDown(screenX, screenY, button);
				if (frameChildUnderMouse != null) {
					return frameChildUnderMouse;
				}
			}
			if (this.controlBackdrop != null) {
				frameChildUnderMouse = this.controlBackdrop.touchDown(screenX, screenY, button);
				if (frameChildUnderMouse != null) {
					return frameChildUnderMouse;
				}
			}
			return this;
		}
		return super.touchDown(screenX, screenY, button);
	}

	@Override
	public UIFrame getFrameChildUnderMouse(final float screenX, final float screenY) {
		if (isInGrabArea(screenX, screenY)) {
			UIFrame frameChildUnderMouse = this.thumbButtonFrame == null ? null
					: this.thumbButtonFrame.getFrameChildUnderMouse(screenX, screenY);
			if (frameChildUnderMouse != null) {
				return frameChildUnderMouse;
			}
			if (this.incButtonFrame != null) {
				frameChildUnderMouse = this.incButtonFrame.getFrameChildUnderMouse(screenX, screenY);
				if (frameChildUnderMouse != null) {
					return frameChildUnderMouse;
				}
			}
			if (this.decButtonFrame != null) {
				frameChildUnderMouse = this.decButtonFrame.getFrameChildUnderMouse(screenX, screenY);
				if (frameChildUnderMouse != null) {
					return frameChildUnderMouse;
				}
			}
			if (this.controlBackdrop != null) {
				frameChildUnderMouse = this.controlBackdrop.getFrameChildUnderMouse(screenX, screenY);
				if (frameChildUnderMouse != null) {
					return frameChildUnderMouse;
				}
			}
			return this;
		}
		return super.getFrameChildUnderMouse(screenX, screenY);
	}

	/** Whether the frame is a slider or its thumb, which a held finger may still drag. */
	public static boolean isSliderPart(final UIFrame frame) {
		final UIFrame slider = (frame != null) && (frame.getParent() instanceof ScrollBarFrame) ? frame.getParent()
				: frame;
		return (slider instanceof ScrollBarFrame) && !((ScrollBarFrame) slider).vertical;
	}

	public void setChangeListener(final ScrollBarChangeListener changeListener) {
		this.changeListener = changeListener;
	}

	public interface ScrollBarChangeListener {
		void onChange(GameUI gameUI, Viewport uiViewport, int newValue);

		ScrollBarChangeListener DO_NOTHING = new ScrollBarChangeListener() {
			@Override
			public void onChange(final GameUI gameUI, final Viewport uiViewport, final int newValue) {

			}
		};
	}

	@Override
	public String getSoundKey() {
		return SOUND_KEY_MENU_BUTTON_CLICK;
	}
}
