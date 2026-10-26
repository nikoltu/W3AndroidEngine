package com.etheller.warsmash.parsers.jass.ai;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/**
 * A computer player's town: the start location or an expansion, anchored at its gold mine. Town ids are the
 * indices the AI scripts use (0 is the start location).
 */
public class AITown {
	private final int id;
	private final float x;
	private final float y;
	private CUnit mine;
	private CUnit overlay;
	private CUnit hall;

	public AITown(final int id, final float x, final float y, final CUnit mine) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.mine = mine;
	}

	public int getId() {
		return this.id;
	}

	public float getX() {
		return this.x;
	}

	public float getY() {
		return this.y;
	}

	/**
	 * @return the town hall when there is one, else the anchor; buildings are placed around it
	 */
	public float getCenterX() {
		return this.hall != null ? this.hall.getX() : this.x;
	}

	public float getCenterY() {
		return this.hall != null ? this.hall.getY() : this.y;
	}

	public CUnit getMine() {
		return this.mine;
	}

	public void setMine(final CUnit mine) {
		this.mine = mine;
	}

	/**
	 * @return the haunted or entangled mine built on this town's gold mine, of any player
	 */
	public CUnit getOverlay() {
		return this.overlay;
	}

	public void setOverlay(final CUnit overlay) {
		this.overlay = overlay;
	}

	public CUnit getHall() {
		return this.hall;
	}

	public void setHall(final CUnit hall) {
		this.hall = hall;
	}

	public int getGold() {
		if (this.overlay != null) {
			return this.overlay.getGold();
		}
		return this.mine == null ? 0 : this.mine.getGold();
	}
}
