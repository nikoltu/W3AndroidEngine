package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetUnitVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CAbilityProjectileListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.projectile.CProjectile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponentLightning;

/**
 * Possession: the Banshee channels on a land unit that cannot act meanwhile, taking extra damage from attacks;
 * when the channel completes her spirit flies into it, her body is gone and the unit is hers for good.
 */
public class CAbilityPossession extends CAbilityTargetSpellBase {
	private int maximumCreepLevel;
	private float damageAmplification;
	private boolean targetInvulnerable;
	private boolean targetMagicImmune;
	private War3ID targetBuffId;
	private War3ID casterBuffId;
	private War3ID lightningId;
	private float projectileSpeed;
	private boolean projectileHomingEnabled;

	private CBuffPossessionTarget targetBuff;
	private CBuffPossessionCaster casterBuff;
	private SimulationRenderComponentLightning lightning;
	private int channelEndTick;

	public CAbilityPossession(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.possession;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.maximumCreepLevel = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
		this.damageAmplification = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.targetInvulnerable = worldEditorAbility.getFieldAsBoolean(AbilityFields.DATA_C + level, 0);
		this.targetMagicImmune = worldEditorAbility.getFieldAsBoolean(AbilityFields.DATA_D + level, 0);
		this.targetBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
		this.casterBuffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 1);
		this.lightningId = AbstractCAbilityTypeDefinition.getLightningId(worldEditorAbility, level);
		this.projectileSpeed = worldEditorAbility.getFieldAsFloat(AbilityFields.PROJECTILE_SPEED, 0);
		this.projectileHomingEnabled = worldEditorAbility.getFieldAsBoolean(AbilityFields.PROJECTILE_HOMING_ENABLED,
				0);
	}

	@Override
	protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit == null) || targetUnit.isBuilding()) {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_UNIT_WITH_THIS_ACTION);
		}
		else if (game.isNeutralAggressive(targetUnit.getPlayerIndex())
				&& (targetUnit.getUnitType().getLevel() > this.maximumCreepLevel)) {
			receiver.targetCheckFailed(CommandStringErrorKeys.THAT_CREATURE_IS_TOO_POWERFUL);
		}
		else {
			receiver.targetOk(target);
		}
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit == null) || targetUnit.isDead()) {
			return false;
		}
		final float duration = getDurationForTarget(targetUnit);
		this.channelEndTick = simulation.getGameTurnTick()
				+ (int) (duration / WarsmashConstants.SIMULATION_STEP_TIME);
		this.targetBuff = new CBuffPossessionTarget(simulation.getHandleIdAllocator().createId(), this.targetBuffId,
				this.targetInvulnerable, this.targetMagicImmune);
		targetUnit.add(simulation, this.targetBuff);
		if (!War3ID.NONE.equals(this.casterBuffId)) {
			this.casterBuff = new CBuffPossessionCaster(simulation.getHandleIdAllocator().createId(),
					this.casterBuffId, this.damageAmplification);
			caster.add(simulation, this.casterBuff);
		}
		if (!War3ID.NONE.equals(this.lightningId)) {
			this.lightning = simulation.createLightning(caster, this.lightningId, targetUnit);
		}
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		if ((targetUnit == null) || targetUnit.isDead()) {
			return false;
		}
		return simulation.getGameTurnTick() < this.channelEndTick;
	}

	@Override
	public void doChannelEnd(final CSimulation game, final CUnit caster, final AbilityTarget target,
			final boolean interrupted) {
		if (this.lightning != null) {
			this.lightning.remove();
			this.lightning = null;
		}
		if (this.casterBuff != null) {
			caster.remove(game, this.casterBuff);
			this.casterBuff = null;
		}
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		final CBuffPossessionTarget targetBuff = this.targetBuff;
		this.targetBuff = null;
		if ((targetUnit == null) || targetUnit.isDead() || interrupted || caster.isDead()
				|| (game.getGameTurnTick() < this.channelEndTick)) {
			if ((targetUnit != null) && (targetBuff != null) && targetUnit.getAbilities().contains(targetBuff)) {
				targetUnit.remove(game, targetBuff);
			}
			return;
		}
		final int newOwner = caster.getPlayerIndex();
		game.createProjectile(caster, getAlias(), caster.getX(), caster.getY(), (float) caster.angleTo(targetUnit),
				this.projectileSpeed, this.projectileHomingEnabled, targetUnit, new CAbilityProjectileListener() {
					@Override
					public void onLaunch(final CSimulation game, final CProjectile projectile,
							final AbilityTarget target) {
					}

					@Override
					public void onHit(final CSimulation game, final CProjectile projectile,
							final AbilityTarget target) {
						final CUnit possessed = target.visit(AbilityTargetUnitVisitor.INSTANCE);
						if (possessed == null) {
							return;
						}
						if ((targetBuff != null) && possessed.getAbilities().contains(targetBuff)) {
							possessed.remove(game, targetBuff);
						}
						if (!possessed.isDead()) {
							CUndeadUnitAbilities.transferOwnership(game, possessed, newOwner);
						}
					}
				});
		caster.setHidden(true);
		game.removeUnit(caster);
	}
}
