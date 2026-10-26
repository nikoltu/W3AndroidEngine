package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.units;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * War Club: the Mountain Giant tears a tree out of the ground and uses it as a club, which swaps its attack for the
 * club attack for a number of strikes.
 */
public class CAbilityWarClub extends CAbilityTargetSpellBase {
	private float attachDelay;
	private float removeDelay;
	private int disabledAttackIndex;
	private int enabledAttackIndex;
	private int maximumAttacks;
	private War3ID buffId;

	private int attachTick;
	private int endTick;
	private boolean attached;

	public CAbilityWarClub(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.grabtree;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.attachDelay = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.removeDelay = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.disabledAttackIndex = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_C + level, 0);
		this.enabledAttackIndex = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_D + level, 0);
		this.maximumAttacks = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_E + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		final float closeEnoughRange = game.getGameplayConstants().getCloseEnoughRange();
		if (getCastRange() < closeEnoughRange) {
			setCastRange(getCastRange() + closeEnoughRange);
		}
		super.onAdd(game, unit);
	}

	@Override
	protected void innerCheckCanTarget(final CSimulation game, final CUnit unit, final int orderId,
			final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		if (target.visit(AbilityTargetVisitor.DESTRUCTABLE) == null) {
			receiver.targetCheckFailed(CommandStringErrorKeys.MUST_TARGET_A_TREE);
		}
		else {
			super.innerCheckCanTarget(game, unit, orderId, target, receiver);
		}
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit unit, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		this.attachTick = tick + (int) (this.attachDelay / WarsmashConstants.SIMULATION_STEP_TIME);
		this.endTick = tick + (int) (Math.max(this.attachDelay, this.removeDelay)
				/ WarsmashConstants.SIMULATION_STEP_TIME);
		this.attached = false;
		return true;
	}

	@Override
	public boolean doChannelTick(final CSimulation simulation, final CUnit unit, final AbilityTarget target) {
		final int tick = simulation.getGameTurnTick();
		if (!this.attached && (tick >= this.attachTick)) {
			this.attached = true;
			final CDestructable tree = target.visit(AbilityTargetVisitor.DESTRUCTABLE);
			if ((tree == null) || tree.isDead()) {
				return false;
			}
			tree.setLife(simulation, 0);
			for (final CAbility ability : unit.getAbilities()) {
				if (ability instanceof CBuffWarClub) {
					((CBuffWarClub) ability).resetStrikes(this.maximumAttacks);
					return tick < this.endTick;
				}
			}
			unit.add(simulation, new CBuffWarClub(simulation.getHandleIdAllocator().createId(), this.buffId,
					this.disabledAttackIndex, this.enabledAttackIndex, this.maximumAttacks));
		}
		return tick < this.endTick;
	}
}
