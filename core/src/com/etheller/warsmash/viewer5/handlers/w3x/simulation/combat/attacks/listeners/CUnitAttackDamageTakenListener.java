package com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;

public interface CUnitAttackDamageTakenListener {
	public static final Set<CTargetType> ENEMY_TARGET = Collections.unmodifiableSet(EnumSet.of(CTargetType.ENEMIES));
	
	public void onDamage(final CSimulation simulation, CUnit attacker, CUnit target, final CDamageFlags flags, CDamageType weaponType, float damage, float bonusDamage, float trueDamage);
}
