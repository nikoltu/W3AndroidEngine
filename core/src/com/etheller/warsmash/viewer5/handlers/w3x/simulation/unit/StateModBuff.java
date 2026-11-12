package com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit;

public class StateModBuff {
	/**
	 * Set in an INVISIBLE value (above the invisibility level byte): the invisibility is gone while the unit is paused.
	 * Measured on 1.29 for the Shade (Ghost), unlike faded Permanent Invisibility.
	 */
	public static final long INVISIBLE_UNLESS_PAUSED = 1L << 8;
	
	private StateModBuffType buffType;
	private long value;
	
	public StateModBuff(StateModBuffType buffType, long value) {
		super();
		this.buffType = buffType;
		this.value = value;
	}
	
	public StateModBuffType getBuffType() {
		return buffType;
	}
	public void setBuffType(StateModBuffType buffType) {
		this.buffType = buffType;
	}
	public long getValue() {
		return value;
	}
	public void setValue(long value) {
		this.value = value;
	}
}
