package com.etheller.warsmash.viewer5.handlers.w3x.save;

import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.LiveRenderObject;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderLightningEffect;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightningMovable;

/**
 * Takes the place of a lightning in a saved game: what is needed to show it again.
 */
public final class SavedLightning extends AllocatedHandle implements SimulationRenderComponentLightningMovable {
	private War3ID lightningId;
	private CUnit sourceUnit;
	private CWidget targetWidget;
	private float[] points;
	private float[] color;
	private float lifeSpan;
	private boolean checkVisibility;
	private boolean removed;
	private LiveRenderObject<SimulationRenderComponentLightning> liveObject;

	private SimulationRenderComponentLightning live() {
		return this.liveObject == null ? null : this.liveObject.value;
	}

	static SavedLightning of(final RenderLightningEffect lightning) {
		SavedLightning saved = (SavedLightning) lightning.getSavedForm();
		if (saved == null) {
			saved = new SavedLightning();
			saved.lightningId = lightning.getLightningId();
			saved.setHandleId(lightning.getHandleId());
			saved.sourceUnit = lightning.getSourceUnit();
			saved.targetWidget = lightning.getTargetWidget();
			saved.liveObject = new LiveRenderObject<>(lightning);
			lightning.setSavedForm(saved);
		}
		saved.points = lightning.getPoints();
		saved.color = lightning.getColor();
		saved.lifeSpan = lightning.getLifeSpanRemaining();
		saved.removed = lightning.isRemoved();
		saved.checkVisibility = lightning.isCheckVisibility();
		return saved;
	}

	void show(final War3MapViewer viewer) {
		if (this.removed || (live() != null) || (this.lightningId == null)) {
			return;
		}
		final SimulationRenderComponentLightning lightning;
		if (this.sourceUnit != null) {
			final RenderUnit source = viewer.getRenderPeer(this.sourceUnit);
			final RenderWidget target = viewer.getRenderPeer(this.targetWidget);
			if ((source == null) || (target == null)) {
				return;
			}
			lightning = viewer.createLightning(this.lightningId, source, target,
					this.lifeSpan > 0 ? Float.valueOf(this.lifeSpan) : Float.valueOf(-1));
		}
		else {
			lightning = viewer.createLightning(this.lightningId, this.points[0], this.points[1], this.points[2],
					this.points[3], this.points[4], this.points[5], this.checkVisibility);
		}
		this.liveObject = new LiveRenderObject<>(lightning);
		if (lightning instanceof RenderLightningEffect) {
			((RenderLightningEffect) lightning).setSavedForm(this);
		}
		if (this.color != null) {
			lightning.setColor(this.color[0], this.color[1], this.color[2], this.color[3]);
		}
	}

	@Override
	public void remove() {
		this.removed = true;
		if (live() != null) {
			live().remove();
		}
	}

	@Override
	public void setColor(final float r, final float g, final float b, final float a) {
		this.color = new float[] { r, g, b, a };
		if (live() != null) {
			live().setColor(r, g, b, a);
		}
	}

	@Override
	public float[] getColor() {
		return this.color;
	}

	@Override
	public boolean isRemoved() {
		return this.removed || ((live() != null) && live().isRemoved());
	}

	@Override
	public void move(final float x1, final float y1, final float x2, final float y2) {
		if (live() instanceof SimulationRenderComponentLightningMovable) {
			((SimulationRenderComponentLightningMovable) live()).move(x1, y1, x2, y2);
		}
	}

	@Override
	public void move(final float x1, final float y1, final float z1, final float x2, final float y2, final float z2) {
		if (live() instanceof SimulationRenderComponentLightningMovable) {
			((SimulationRenderComponentLightningMovable) live()).move(x1, y1, z1, x2, y2, z2);
		}
	}
}
