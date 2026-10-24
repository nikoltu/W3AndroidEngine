package com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing;

public class CPathPoint {
	public float x;
	public float y;

	public CPathPoint(final float x, final float y) {
		this.x = x;
		this.y = y;
	}

	public double distance(final double px, final double py) {
		final double dx = px - this.x;
		final double dy = py - this.y;
		return Math.sqrt((dx * dx) + (dy * dy));
	}

	public double distance(final CPathPoint point) {
		return distance(point.x, point.y);
	}
}
