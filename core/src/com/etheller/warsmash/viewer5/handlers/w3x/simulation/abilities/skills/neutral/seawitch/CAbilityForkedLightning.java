package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.neutral.seawitch;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAreaTargetVisibility;
import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;

/**
 * Forked Lightning: lightning from the caster to the target and the nearest other enemies inside a cone toward it,
 * up to the maximum number of targets.
 */
public class CAbilityForkedLightning extends CAbilityTargetSpellBase {
	private float damage;
	private int maximumTargets;
	private float distance;
	private float finalArea;
	private float areaOfEffect;
	private War3ID lightningId;

	public CAbilityForkedLightning(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.forkedlightning;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.damage = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.maximumTargets = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_B + level, 0);
		this.distance = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.finalArea = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.lightningId = AbstractCAbilityTypeDefinition.getLightningId(worldEditorAbility, level);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit primary = target.visit(AbilityTargetVisitor.UNIT);
		if (primary == null) {
			return false;
		}
		final float originX = caster.getX();
		final float originY = caster.getY();
		final double angle = caster.angleTo(primary);
		final float dirX = (float) StrictMath.cos(angle);
		final float dirY = (float) StrictMath.sin(angle);
		final List<CUnit> candidates = new ArrayList<>();
		final float searchRadius = this.distance + this.finalArea;
		simulation.getWorldCollision().enumUnitsInRange(originX, originY, searchRadius, (enumUnit) -> {
			if ((enumUnit != primary) && isInCone(enumUnit, originX, originY, dirX, dirY)
					&& !enumUnit.isUnitAlly(simulation.getPlayer(caster.getPlayerIndex()))
					&& enumUnit.canBeAreaTargetedBy(simulation, caster, CAreaTargetVisibility.NOT_HIDDEN_GHOST, getTargetsAllowed(),
							BooleanAbilityTargetCheckReceiver.<CWidget>getInstance().reset())) {
				candidates.add(enumUnit);
			}
			return false;
		});
		candidates.sort((a, b) -> Double.compare(a.distanceSquaredNoCollision(originX, originY),
				b.distanceSquaredNoCollision(originX, originY)));
		final List<CUnit> targets = new ArrayList<>();
		targets.add(primary);
		for (final CUnit candidate : candidates) {
			if (targets.size() >= this.maximumTargets) {
				break;
			}
			targets.add(candidate);
		}
		final float boltDuration = getDuration();
		for (final CUnit unit : targets) {
			simulation.createLightning(caster, this.lightningId, unit, boltDuration);
			simulation.createTemporarySpellEffectOnUnit(unit, getAlias(), CEffectType.TARGET);
			unit.damage(simulation, caster, DAMAGE_FLAGS, CAttackType.SPELLS, CDamageType.LIGHTNING,
					CWeaponSoundTypeJass.WHOKNOWS.name(), this.damage);
		}
		return false;
	}

	// Measured on 1.29: the cone widens from the area of effect at the caster to the final area at full distance,
	// both as distances to the side of the line toward the target.
	private boolean isInCone(final CUnit unit, final float originX, final float originY, final float dirX,
			final float dirY) {
		final float dx = unit.getX() - originX;
		final float dy = unit.getY() - originY;
		final float forward = (dx * dirX) + (dy * dirY);
		if ((forward < 0) || (forward > this.distance)) {
			return false;
		}
		final float side = Math.abs((dx * dirY) - (dy * dirX));
		final float width = this.areaOfEffect + (((this.finalArea - this.areaOfEffect) * forward) / this.distance);
		return side <= width;
	}
}
