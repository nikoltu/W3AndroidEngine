package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * Spirit Link: chains the target with the nearest other valid units around it; each linked unit passes a share of
 * the damage it takes evenly to the others.
 */
public class CAbilitySpiritLink extends CAbilityTargetSpellBase {
	private static final float LIGHTNING_DURATION = 0.75f;

	private float distributedDamageFactor;
	private int maximumTargets;
	private float areaOfEffect;
	private War3ID buffId;
	private War3ID lightningId;

	public CAbilitySpiritLink(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.spiritlink;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.distributedDamageFactor = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.maximumTargets = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_B + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
		this.lightningId = AbstractCAbilityTypeDefinition.getLightningId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit == null) {
			return false;
		}
		final List<CUnit> others = new ArrayList<>();
		simulation.getWorldCollision().enumUnitsInRange(targetUnit.getX(), targetUnit.getY(), this.areaOfEffect,
				(enumUnit) -> {
					if ((enumUnit != targetUnit) && !enumUnit.isHidden()
							&& enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
						others.add(enumUnit);
					}
					return false;
				});
		others.sort(Comparator.comparingDouble((enumUnit) -> enumUnit.distance(targetUnit)));
		final List<CUnit> linked = new ArrayList<>();
		linked.add(targetUnit);
		for (final CUnit other : others) {
			if (linked.size() >= this.maximumTargets) {
				break;
			}
			linked.add(other);
		}
		final CSpiritLinkGroup group = new CSpiritLinkGroup(this.distributedDamageFactor);
		CUnit previous = null;
		for (final CUnit unit : linked) {
			removeExistingLink(simulation, unit);
			unit.add(simulation, new CBuffSpiritLink(simulation.getHandleIdAllocator().createId(), this.buffId,
					getDurationForTarget(unit), group));
			simulation.createTemporarySpellEffectOnUnit(unit, getAlias(), CEffectType.TARGET);
			if ((previous != null) && !War3ID.NONE.equals(this.lightningId)) {
				simulation.createLightning(previous, this.lightningId, unit, LIGHTNING_DURATION);
			}
			previous = unit;
		}
		return false;
	}

	private void removeExistingLink(final CSimulation simulation, final CUnit unit) {
		for (final CAbility ability : new ArrayList<>(unit.getAbilities())) {
			if (ability instanceof CBuffSpiritLink) {
				unit.remove(simulation, (CBuffSpiritLink) ability);
			}
		}
	}
}
