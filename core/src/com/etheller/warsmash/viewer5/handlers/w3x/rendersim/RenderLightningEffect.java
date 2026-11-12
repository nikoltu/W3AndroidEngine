package com.etheller.warsmash.viewer5.handlers.w3x.rendersim;

import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.lightning.LightningEffectNode;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightningMovable;

public class RenderLightningEffect extends AllocatedHandle implements SimulationRenderComponentLightningMovable {
	private final LightningEffectNode source;
	private final LightningEffectNode target;
	private final War3MapViewer viewer; // TODO would be nice not to store viewer, but what is "move()"
	private final War3ID lightningId;
	private final CUnit sourceUnit;
	private final CWidget targetWidget;
	private final float[] points = new float[6];
	private float[] color;
	private Object savedForm;
	private boolean checkVisibility;

	public RenderLightningEffect(LightningEffectNode source, LightningEffectNode target, War3MapViewer viewer,
			War3ID lightningId, CUnit sourceUnit, CWidget targetWidget, float x1, float y1, float z1, float x2,
			float y2, float z2) {
		this.source = source;
		this.target = target;
		this.viewer = viewer;
		this.lightningId = lightningId;
		this.sourceUnit = sourceUnit;
		this.targetWidget = targetWidget;
		setPoints(x1, y1, z1, x2, y2, z2);
	}

	private void setPoints(float x1, float y1, float z1, float x2, float y2, float z2) {
		this.points[0] = x1;
		this.points[1] = y1;
		this.points[2] = z1;
		this.points[3] = x2;
		this.points[4] = y2;
		this.points[5] = z2;
	}

	public War3ID getLightningId() {
		return this.lightningId;
	}

	public CUnit getSourceUnit() {
		return this.sourceUnit;
	}

	public CWidget getTargetWidget() {
		return this.targetWidget;
	}

	public float[] getPoints() {
		return this.points.clone();
	}

	@Override
	public float[] getColor() {
		return this.color;
	}

	public float getLifeSpanRemaining() {
		return this.source.getLifeSpanRemaining();
	}

	public boolean isCheckVisibility() {
		return this.checkVisibility;
	}

	public void setCheckVisibility(final boolean checkVisibility) {
		this.checkVisibility = checkVisibility;
	}

	public Object getSavedForm() {
		return this.savedForm;
	}

	public void setSavedForm(Object savedForm) {
		this.savedForm = savedForm;
	}

	@Override
	public void remove() {
		// The scene also draws the children of every visible unit, so a removed lightning leaves its unit
		source.setParent(null);
		target.setParent(null);
		source.detach();
		target.detach();
	}

	@Override
	public void move(float x1, float y1, float x2, float y2) {
		final float z1 = viewer.terrain.getGroundHeight(x1, y1);
		final float z2 = viewer.terrain.getGroundHeight(x2, y2);
		move(x1, y1, z1, x2, y2, z2);
	}

	@Override
	public void move(float x1, float y1, float z1, float x2, float y2, float z2) {
		source.setLocation(x1, y1, z1);
		target.setLocation(x2, y2, z2);
		setPoints(x1, y1, z1, x2, y2, z2);
	}

	@Override
	public void setColor(float r, float g, float b, float a) {
		this.color = new float[] { r, g, b, a };
		source.setColor(r, g, b, a);
		target.setColor(r, g, b, a);
	}

	@Override
	public boolean isRemoved() {
		return source.scene == null;
	}
}
