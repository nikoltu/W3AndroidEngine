package com.etheller.warsmash.viewer5.handlers.w3x.simthread;

import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightningMovable;

/**
 * What a script's lightning handle is while the simulation runs on its own thread: it exists at once and the render
 * thread makes the lightning when the frame holding the creating command is played. What the script can read back
 * (removed, color) is kept here and changes only through the script's own calls, so it does not depend on frame timing.
 */
public final class PendingLightning extends AllocatedHandle implements SimulationRenderComponentLightningMovable {
	private final RenderCommands commands;
	private volatile SimulationRenderComponentLightningMovable lightning;
	private boolean removed;
	private float[] color;

	public PendingLightning(final RenderCommands commands) {
		this.commands = commands;
	}

	/** Render thread: the lightning the creating command made. */
	public void attach(final SimulationRenderComponentLightningMovable made) {
		this.lightning = made;
	}

	public SimulationRenderComponentLightningMovable made() {
		return this.lightning;
	}

	@Override
	public void remove() {
		this.removed = true;
		this.commands.send(() -> {
			final SimulationRenderComponentLightningMovable made = this.lightning;
			if (made != null) {
				made.remove();
			}
		});
	}

	@Override
	public void setColor(final float r, final float g, final float b, final float a) {
		this.color = new float[] { r, g, b, a };
		this.commands.send(() -> {
			final SimulationRenderComponentLightningMovable made = this.lightning;
			if (made != null) {
				made.setColor(r, g, b, a);
			}
		});
	}

	@Override
	public float[] getColor() {
		return this.color;
	}

	@Override
	public void move(final float x1, final float y1, final float x2, final float y2) {
		this.commands.send(() -> {
			final SimulationRenderComponentLightningMovable made = this.lightning;
			if (made != null) {
				made.move(x1, y1, x2, y2);
			}
		});
	}

	@Override
	public void move(final float x1, final float y1, final float z1, final float x2, final float y2, final float z2) {
		this.commands.send(() -> {
			final SimulationRenderComponentLightningMovable made = this.lightning;
			if (made != null) {
				made.move(x1, y1, z1, x2, y2, z2);
			}
		});
	}

	@Override
	public boolean isRemoved() {
		return this.removed;
	}
}
