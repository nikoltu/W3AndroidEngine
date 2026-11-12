package com.etheller.warsmash.viewer5.handlers.w3x.simulation.util;

public enum TextTagConfigType {
	GOLD("Gold"), LUMBER("Lumber"), GOLD_BOUNTY("Bounty"), LUMBER_BOUNTY("LumberBounty"), XP("XP"),
	MISS_TEXT("Miss"), CRITICAL_STRIKE("CriticalStrike"), SHADOW_STRIKE("ShadowStrike"), MANA_BURN("ManaBurn"),
	BASH("Bash");

	private final String key;

	TextTagConfigType(final String key) {
		this.key = key;
	}

	public String getKey() {
		return this.key;
	}

	/** Resource and experience numbers are drawn for the player who receives them only; combat text is for all. */
	public boolean isShownToReceiverOnly() {
		switch (this) {
		case GOLD:
		case LUMBER:
		case GOLD_BOUNTY:
		case LUMBER_BOUNTY:
		case XP:
			return true;
		default:
			return false;
		}
	}

	public static final TextTagConfigType[] VALUES = values();
}
