package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import java.util.EnumSet;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPassiveSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;

/**
 * Bouncing Missile Filter: bouncing attacks (the Huntress glaive) neither bounce off nor onto the target kinds it
 * names, in the melee data the structures.
 */
public class CAbilityBouncingMissileFilter extends CAbilityPassiveSpellBase {
	private static final EnumSet<CTargetType> KINDS = EnumSet.of(CTargetType.GROUND, CTargetType.AIR,
			CTargetType.STRUCTURE, CTargetType.WARD);

	private EnumSet<CTargetType> filteredKinds = EnumSet.noneOf(CTargetType.class);

	public CAbilityBouncingMissileFilter(final int handleId, final War3ID code, final War3ID alias) {
		super(handleId, code, alias);
		setIconShowing(false);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.filteredKinds = EnumSet.noneOf(CTargetType.class);
		for (final CTargetType targetType : getTargetsAllowed()) {
			if (KINDS.contains(targetType)) {
				this.filteredKinds.add(targetType);
			}
		}
	}

	private boolean isFiltered(final CWidget target) {
		final CUnit unit = target.visit(AbilityTargetVisitor.UNIT);
		if (unit == null) {
			return false;
		}
		for (final CTargetType kind : this.filteredKinds) {
			if (unit.getTargetedAs().contains(kind)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isBounceFiltered(final CSimulation simulation, final CUnit source, final CWidget target) {
		for (final CAbility ability : source.getAbilities()) {
			if ((ability instanceof CAbilityBouncingMissileFilter) && !ability.isDisabled()
					&& ((CAbilityBouncingMissileFilter) ability).isFiltered(target)) {
				return true;
			}
		}
		return false;
	}
}
