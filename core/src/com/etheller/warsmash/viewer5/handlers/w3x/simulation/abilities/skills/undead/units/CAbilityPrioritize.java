package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.units;

import java.util.EnumSet;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;

/**
 * Prioritize: a toggle; while it is on the unit engages the kinds of enemies in its targets (Gargoyles: flyers)
 * first, also turning from an enemy it picked on its own when one comes into range, but not from one it was
 * ordered to attack.
 */
public class CAbilityPrioritize extends CAbilityNoTargetSpellBase {
	private static final int CHECK_TICKS = (int) (0.5f / WarsmashConstants.SIMULATION_STEP_TIME);

	private boolean initiallyEnabled;
	private War3ID buffId;
	private boolean active;
	private boolean added;
	private CBuffPrioritize buff;
	private int nextCheckTick;

	public CAbilityPrioritize(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds._attacktargetpriority;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.initiallyEnabled = worldEditorAbility.getFieldAsBoolean(AbilityFields.DATA_A + level, 0);
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		super.onAdd(game, unit);
		if (!this.added) {
			this.added = true;
			this.active = this.initiallyEnabled;
		}
		if (this.active) {
			setActive(game, unit, true);
		}
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		if (this.active) {
			setActive(game, unit, false);
			this.active = true;
		}
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if (orderId == getBaseOrderId()) {
			setActive(game, caster, !this.active);
			return false;
		}
		return super.checkBeforeQueue(game, caster, playerIndex, orderId, autoOrder, target);
	}

	private EnumSet<CTargetType> getPriorityTargets() {
		final EnumSet<CTargetType> targets = EnumSet.noneOf(CTargetType.class);
		for (final CTargetType type : getTargetsAllowed()) {
			if ((type == CTargetType.AIR) || (type == CTargetType.GROUND) || (type == CTargetType.STRUCTURE)) {
				targets.add(type);
			}
		}
		return targets;
	}

	public void setActive(final CSimulation game, final CUnit unit, final boolean active) {
		this.active = active;
		if (active) {
			unit.setPriorityAttackTargets(getPriorityTargets());
			if ((this.buff == null) && !War3ID.NONE.equals(this.buffId)) {
				this.buff = new CBuffPrioritize(game.getHandleIdAllocator().createId(), this.buffId);
				unit.add(game, this.buff);
			}
		}
		else {
			unit.setPriorityAttackTargets(null);
			if (this.buff != null) {
				unit.remove(game, this.buff);
				this.buff = null;
			}
		}
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		if (!this.active || isDisabled() || (game.getGameTurnTick() < this.nextCheckTick)) {
			return;
		}
		this.nextCheckTick = game.getGameTurnTick() + CHECK_TICKS;
		final CBehavior behavior = unit.getCurrentBehavior();
		if ((behavior == null) || (behavior != unit.getAttackBehavior())) {
			return;
		}
		final CUnit attacked = unit.getAttackBehavior().getTarget().visit(AbilityTargetVisitor.UNIT);
		final EnumSet<CTargetType> priorityTargets = unit.getPriorityAttackTargets();
		if ((attacked == null) || (priorityTargets == null)
				|| CUnit.isPriorityAttackTarget(attacked, priorityTargets)) {
			return;
		}
		final COrder order = unit.getCurrentOrder();
		if ((order != null) && (order.getOrderId() == OrderIds.attack) && (order.getTarget(game) == attacked)) {
			return;
		}
		unit.acquirePriorityAttackTarget(game, unit.isMovementDisabled());
	}

	@Override
	public boolean isToggleOn() {
		return this.active;
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		return false;
	}

	@Override
	public boolean isMagic() {
		return false;
	}

	@Override
	public boolean isUniversal() {
		return true;
	}
}
