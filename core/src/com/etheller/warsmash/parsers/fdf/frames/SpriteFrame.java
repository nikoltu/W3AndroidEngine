package com.etheller.warsmash.parsers.fdf.frames;

import java.util.EnumSet;
import java.util.Locale;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.etheller.warsmash.parsers.fdf.GameUI;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.viewer5.Scene;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxComplexInstance;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;
import com.etheller.warsmash.viewer5.handlers.mdx.SequenceLoopMode;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.PrimaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;
import com.etheller.warsmash.viewer5.handlers.w3x.SequenceUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simthread.SimThreadGuard;
import com.hiveworkshop.rms.parsers.mdlx.MdlxExtent;

public class SpriteFrame extends AbstractUIFrame {

	protected Scene scene;
	protected final Viewport uiViewport;
	private MdxComplexInstance instance;
	private float zDepth;
	private boolean sizedByModel;
	private float modelMinX;
	private float modelMinY;

	public SpriteFrame(final String name, final UIFrame parent, final Scene scene, final Viewport uiViewport) {
		super(name, parent);
		this.scene = scene;
		this.uiViewport = uiViewport;
	}

	/** The scene the model is drawn in, in place of the interface's; before the model is set. */
	public void setScene(final Scene scene) {
		this.scene = scene;
	}

	public void setModel(final MdxModel model) {
		SimThreadGuard.assertGl();
		if (this.instance != null) {
			this.scene.removeInstance(this.instance);
		}
		if (model != null) {
			this.instance = (MdxComplexInstance) model.addInstance();
			this.instance.setSequenceLoopMode(SequenceLoopMode.MODEL_LOOP);
			this.instance.setScene(this.scene);
			this.instance.setLocation(this.renderBounds.x, this.renderBounds.y, this.zDepth);
		}
		else {
			this.instance = null;
		}
		applyModelBounds();
	}

	/**
	 * A MODEL frame without a size of its own takes the size of its model's bounds, and its points place those
	 * bounds: the score screen's player rows hang down from their TOPLEFT point.
	 */
	public void setSizedByModel(final boolean sizedByModel) {
		this.sizedByModel = sizedByModel;
		applyModelBounds();
	}

	private void applyModelBounds() {
		if (!this.sizedByModel || (this.instance == null)) {
			this.modelMinX = 0;
			this.modelMinY = 0;
			return;
		}
		final MdxModel model = (MdxModel) this.instance.model;
		// The model's own extent, not a sequence's: the score screen bars carry the top bar's sequence extents
		// (0 to 0.0328 up) although the bottom bar hangs 0.0375 below its origin.
		final BoundingBox box = model.bounds.getBoundingBox();
		if ((box != null) && (box.getWidth() > 0) && (box.getHeight() > 0)) {
			this.modelMinX = box.min.x;
			this.modelMinY = box.min.y;
			setWidth(GameUI.convertX(this.uiViewport, box.getWidth()));
			setHeight(GameUI.convertY(this.uiViewport, box.getHeight()));
			return;
		}
		final MdlxExtent extent = model.getSequences().isEmpty() ? null : model.getSequences().get(0).getExtent();
		if ((extent == null) || (extent.getMax()[0] < extent.getMin()[0])) {
			return;
		}
		this.modelMinX = extent.getMin()[0];
		this.modelMinY = extent.getMin()[1];
		setWidth(GameUI.convertX(this.uiViewport, extent.getMax()[0] - this.modelMinX));
		setHeight(GameUI.convertY(this.uiViewport, extent.getMax()[1] - this.modelMinY));
	}

	@Override
	public void setVisible(final boolean visible) {
		super.setVisible(visible);
		updateInstanceLocation(this.uiViewport);
	}

	@Override
	protected void internalRender(final SpriteBatch batch, final BitmapFont baseFont, final GlyphLayout glyphLayout) {
		super.internalRender(batch, baseFont, glyphLayout);
	}

	@Override
	public void setFramePointX(final FramePoint framePoint, final float x) {
		super.setFramePointX(framePoint, x);
		updateInstanceLocation(this.uiViewport);
	}

	@Override
	public void setFramePointY(final FramePoint framePoint, final float y) {
		super.setFramePointY(framePoint, y);
		updateInstanceLocation(this.uiViewport);
	}

	public void setZDepth(final float depth) {
		this.zDepth = depth;
		updateInstanceLocation(this.uiViewport);
	}

	@Override
	protected void innerPositionBounds(final GameUI gameUI, final Viewport viewport) {
		super.innerPositionBounds(gameUI, viewport);
		updateInstanceLocation(viewport);
	}

	public void setSequence(final int index) {
		SimThreadGuard.assertGl();
		if (this.instance != null) {
			this.instance.setSequence(index);
		}
	}

	public void setSequence(final String animationName) {
		SimThreadGuard.assertGl();
		if (this.instance != null) {
			SequenceUtils.randomSequence(this.instance, animationName.toLowerCase(Locale.ROOT));
		}
	}

	public void setSequence(final PrimaryTag animationName) {
		SimThreadGuard.assertGl();
		if (this.instance != null) {
			SequenceUtils.randomSequence(this.instance, animationName);
		}
	}

	public void setSequence(final PrimaryTag animationName, final EnumSet<SecondaryTag> secondaryTags) {
		SimThreadGuard.assertGl();
		if (this.instance != null) {
			SequenceUtils.randomSequence(this.instance, animationName, secondaryTags, true);
		}
	}

	public void setAnimationSpeed(final float speedRatio) {
		if (this.instance != null) {
			this.instance.setAnimationSpeed(speedRatio);
		}
	}

	public void setFrame(final int animationFrame) {
		if (this.instance != null) {
			this.instance.setFrame(animationFrame);
		}
	}

	public void setFrameByRatio(final float ratioOfAnimationCompleted) {
		if (this.instance != null) {
			this.instance.setFrameByRatio(ratioOfAnimationCompleted);
		}
	}

	private void updateInstanceLocation(final Viewport viewport) {
		if (this.instance != null) {
			this.instance.setLocation(GameUI.unconvertX(viewport, this.renderBounds.x) - this.modelMinX,
					GameUI.unconvertY(viewport, this.renderBounds.y) - this.modelMinY, this.zDepth);
			if (isVisible()) {
				this.instance.show();
			}
			else {
				this.instance.hide();
			}
		}
	}

	public boolean isSequenceEnded() {
		return this.instance.sequenceEnded;
	}

	public void setReplaceableId(final int replaceableId, final String blpPath) {
		if (this.instance != null) {
			this.instance.setReplaceableTexture(replaceableId, blpPath);
		}

	}

	public void setVertexColor(final Color color) {
		if (this.instance != null) {
			this.instance.setVertexColor(color);
		}
	}

	public void setModelAlpha(final float alpha) {
		SimThreadGuard.assertGl();
		if (this.instance != null) {
			this.instance.setVertexAlpha(alpha);
		}
	}

	public void setModelScale(float scale) {
		SimThreadGuard.assertGl();
		if (this.instance != null) {
			this.instance.setUniformScale(scale);
		}
	}

}
