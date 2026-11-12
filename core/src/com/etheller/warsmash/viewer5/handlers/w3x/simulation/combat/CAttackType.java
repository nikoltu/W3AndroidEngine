package com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat;

import java.util.Locale;

import com.etheller.interpreter.ast.util.CHandle;

public enum CAttackType implements CodeKeyType, CHandle {
	SPELLS(true, false, false),
	NORMAL(false, false, true),
	PIERCE(false, false, true),
	SIEGE(false, false, true),
	MAGIC(false, true, false),
	CHAOS(false, false, true),
	HERO(false, false, true),
	UNKNOWN(false, false, false);

	public static final CAttackType[] VALUES = values();

	private final String codeKey;
	private final String damageKey;
	private final boolean universal;
	private final boolean magic;
	private final boolean physical;

	private CAttackType(boolean universal, boolean magic, boolean physical) {
		final String name = name();
		this.codeKey = name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
		this.damageKey = this.codeKey;
		this.universal = universal;
		this.magic = magic;
		this.physical = physical;
	}

	@Override
	public String getCodeKey() {
		return this.codeKey;
	}

	public String getDamageKey() {
		return this.damageKey;
	}

	public static CAttackType parseAttackType(final String attackTypeString) {
		final String upperCaseAttackType = attackTypeString.trim().toUpperCase(Locale.ROOT);
		// Units without a second attack leave its type empty (Reforged data), which still keeps the attack slot.
		if (upperCaseAttackType.isEmpty() || "_".equals(upperCaseAttackType)) {
			return UNKNOWN;
		}
		if ("SEIGE".equals(upperCaseAttackType)) {
			return SIEGE;
		}
		return valueOf(upperCaseAttackType);
	}

	@Override
	public int getHandleId() {
		return ordinal();
	}
	
	public boolean isUniversal() {
		return this.universal;
	}
	
	public boolean isMagic() {
		return this.magic;
	}
	
	public boolean isPhysical() {
		return this.physical;
	}
}
