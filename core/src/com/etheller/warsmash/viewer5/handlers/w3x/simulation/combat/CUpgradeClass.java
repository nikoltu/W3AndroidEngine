package com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat;

import java.util.Locale;

public enum CUpgradeClass {
	ARMOR, ARTILLERY, MELEE, RANGED, CASTER;

	public static CUpgradeClass parseUpgradeClass(final String upgradeClassString) {
		try {
			return valueOf(upgradeClassString.toUpperCase(Locale.ROOT));
		}
		catch (Exception exc) {
			return null;
		}
	}
}
