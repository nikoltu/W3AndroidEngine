package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.orc.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetUnitVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.CUnitTypeJass;

public class CAbilityEnsnare extends CAbilityTargetSpellBase {
	private float airUnitHeight;
	private float projectileSpeed;
	private boolean projectileHomingEnabled;
	private War3ID airBuffId;
	private War3ID groundBuffId;

	public CAbilityEnsnare(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.ensnare;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.airUnitHeight = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.projectileSpeed = worldEditorAbility.getFieldAsFloat(AbilityFields.PROJECTILE_SPEED, 0);
		this.projectileHomingEnabled = worldEditorAbility.getFieldAsBoolean(AbilityFields.PROJECTILE_HOMING_ENABLED,
				0);
		this.airBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
		final War3ID groundBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 1);
		this.groundBuffId = War3ID.NONE.equals(groundBuffId) ? this.airBuffId : groundBuffId;
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if (targetUnit != null) {
			simulation.createProjectile(caster, getAlias(), caster.getX(), caster.getY(),
					(float) caster.angleTo(targetUnit), this.projectileSpeed, this.projectileHomingEnabled,
					targetUnit, new CAbilityProjectileListener() {
						@Override
						public void onLaunch(final CSimulation game, final CProjectile projectile,
								final AbilityTarget target) {
						}

						@Override
						public void onHit(final CSimulation game, final CProjectile projectile,
								final AbilityTarget target) {
							final CUnit unitTarget = target.visit(AbilityTargetUnitVisitor.INSTANCE);
							if ((unitTarget != null) && !unitTarget.isDead()) {
								final boolean flying = unitTarget.isUnitType(CUnitTypeJass.FLYING);
								unitTarget.add(game,
										new CBuffEnsnare(game.getHandleIdAllocator().createId(),
												flying ? CAbilityEnsnare.this.airBuffId
														: CAbilityEnsnare.this.groundBuffId,
												getDurationForTarget(unitTarget), flying,
												CAbilityEnsnare.this.airUnitHeight));
							}
						}
					});
		}
		return false;
	}
}
