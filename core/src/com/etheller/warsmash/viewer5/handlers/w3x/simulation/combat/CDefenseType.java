package com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat;

import java.util.Locale;

import com.etheller.interpreter.ast.util.CHandle;
import com.etheller.warsmash.util.Log;

public enum CDefenseType implements CodeKeyType, CHandle {
	SMALL,
	MEDIUM,
	LARGE,
	FORT,
	NORMAL,
	HERO,
	DIVINE,
	NONE;

	public static final CDefenseType[] VALUES = values();

	private String codeKey;

	private CDefenseType() {
		this.codeKey = name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
	}

	@Override
	public String getCodeKey() {
		return this.codeKey;
	}

	public static CDefenseType parseDefenseType(final String typeString) {
		final String upperCaseTypeString = typeString.trim().toUpperCase(Locale.ROOT);
		if (upperCaseTypeString.isEmpty()) {
			return NONE;
		}
		if (upperCaseTypeString.equals("HEAVY")) {
			return LARGE;
		}
		try {
			return valueOf(upperCaseTypeString);
		}
		catch (final Exception exc) {
			Log.error(exc);
			return NONE;
		}
	}

	@Override
	public int getHandleId() {
		return ordinal();
	}
}
