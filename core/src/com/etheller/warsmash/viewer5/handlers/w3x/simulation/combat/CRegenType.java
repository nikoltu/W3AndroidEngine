package com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat;

import java.util.Locale;

import com.etheller.warsmash.util.Log;

public enum CRegenType {
	NONE, ALWAYS, BLIGHT, DAY, NIGHT;

	public static CRegenType parseRegenType(final String typeString) {
		if (typeString.trim().isEmpty()) {
			return NONE;
		}
		try {
			return valueOf(typeString.toUpperCase(Locale.ROOT));
		}
		catch (final Exception exc) {
			Log.error(exc);
			return NONE;
		}
	}
}
