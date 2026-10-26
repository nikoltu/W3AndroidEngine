package com.etheller.warsmash.parsers.jass.ai;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;

/**
 * A structure a computer player's worker was sent to build that has not been laid down yet. Until it is, the
 * structure already counts for the AI scripts so they do not order it again.
 */
public class AIBuildJob {
	private final CUnit worker;
	private final CUnitType type;
	private final float x;
	private final float y;
	private final int town;
	private final int issuedTick;

	public AIBuildJob(final CUnit worker, final CUnitType type, final float x, final float y, final int town,
			final int issuedTick) {
		this.worker = worker;
		this.type = type;
		this.x = x;
		this.y = y;
		this.town = town;
		this.issuedTick = issuedTick;
	}

	public CUnit getWorker() {
		return this.worker;
	}

	public CUnitType getType() {
		return this.type;
	}

	public War3ID getTypeId() {
		return this.type.getTypeId();
	}

	public float getX() {
		return this.x;
	}

	public float getY() {
		return this.y;
	}

	public int getTown() {
		return this.town;
	}

	public int getIssuedTick() {
		return this.issuedTick;
	}
}
