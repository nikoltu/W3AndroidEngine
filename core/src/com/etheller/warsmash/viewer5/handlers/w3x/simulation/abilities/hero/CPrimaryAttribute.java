package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero;

import java.util.Locale;

public enum CPrimaryAttribute {
	STRENGTH,
	INTELLIGENCE,
	AGILITY;

	public static CPrimaryAttribute parsePrimaryAttribute(final String targetTypeString) {
		if (targetTypeString == null) {
			return STRENGTH;
		}
		switch (targetTypeString.toUpperCase(Locale.ROOT)) {
		case "STR":
			return STRENGTH;
		case "INT":
			return INTELLIGENCE;
		case "AGI":
			return AGILITY;
		default:
			return STRENGTH;
		}
	}
}
