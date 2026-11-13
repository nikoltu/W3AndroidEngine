package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability;

import java.util.List;
import java.util.Map;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.ABBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.AbilityBuilderConfiguration;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.types.impl.CAbilityTypeAbilityBuilderLevelData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

public class CAbilityAbilityBuilderActivePointTarget extends CAbilityAbilityBuilderGenericActive {

	private ABBehavior behavior;

	public CAbilityAbilityBuilderActivePointTarget(int handleId, War3ID code, War3ID alias,
			List<CAbilityTypeAbilityBuilderLevelData> levelData, AbilityBuilderConfiguration config,
			Map<String, Object> localStore) {
		super(handleId, code, alias, levelData, config, localStore);
	}

	@Override
	public boolean isToggleOn() {
		return false;
	}

	@Override
	public void onAdd(CSimulation game, CUnit unit) {
		this.behavior = this.createRangedBehavior(unit);
		super.onAdd(game, unit);
	}

	@Override
	public CBehavior begin(CSimulation game, CUnit caster, int playerIndex, int orderId, boolean autoOrder, CWidget target) {
		if (target.visit(AbilityTargetVisitor.UNIT) == null) {
			return null;
		}
		return begin(game, caster, playerIndex, orderId, autoOrder, new AbilityPointTarget(target.getX(), target.getY()));
	}

	@Override
	public CBehavior begin(CSimulation game, CUnit caster, int playerIndex, int orderId, boolean autoOrder, AbilityPointTarget point) {
		this.internalBegin(game, caster, playerIndex, orderId, autoOrder, point);
		this.behavior.setCastId(castId);
		return this.behavior.reset(game, point, playerIndex, orderId, autoOrder);
	}

	@Override
	public void internalBegin(CSimulation game, CUnit caster, int playerIndex, int orderId, boolean autoOrder, AbilityTarget theTarget) {
		this.castId++;
		this.localStore.put(ABLocalStoreKeys.combineKey(ABLocalStoreKeys.ISAUTOCAST, castId), autoOrder);
		localStore.put(ABLocalStoreKeys.ABILITYTARGETEDLOCATION+this.castId, theTarget.visit(AbilityTargetVisitor.POINT));
		this.localStore.put(ABLocalStoreKeys.PREVIOUSBEHAVIOR, caster.getCurrentBehavior());
		this.runOnOrderIssuedActions(game, caster, orderId);
	}

	@Override
	public CBehavior beginNoTarget(CSimulation game, CUnit caster, int playerIndex, int orderId, boolean autoOrder) {
		return null;
	}
	
	/**
	 * Measured: a ground spell also takes an order aimed at a unit (a script, the computer player) and is cast on the
	 * ground where the unit stands.
	 */
	@Override
	protected void innerCheckCanTarget(CSimulation game, CUnit unit, int orderId, CWidget target,
			AbilityTargetCheckReceiver<CWidget> receiver) {
		if (target.visit(AbilityTargetVisitor.UNIT) == null) {
			receiver.orderIdNotAccepted();
			return;
		}
		if (!innerCheckTargetTargetable(game, unit, target, receiver)) {
			return;
		}
		innerCheckCanTarget(game, unit, orderId, new AbilityPointTarget(target.getX(), target.getY()),
				new AbilityTargetCheckReceiver<AbilityPointTarget>() {
					@Override
					public void targetOk(final AbilityPointTarget point) {
						receiver.targetOk(target);
					}

					@Override
					public void notAnActiveAbility() {
						receiver.notAnActiveAbility();
					}

					@Override
					public void orderIdNotAccepted() {
						receiver.orderIdNotAccepted();
					}

					@Override
					public void targetCheckFailed(final String commandStringErrorKey) {
						receiver.targetCheckFailed(commandStringErrorKey);
					}
				});
	}

	@Override
	protected boolean innerCheckCanUseSpell(CSimulation game, CUnit unit, int orderId,
			AbilityActivationReceiver receiver) {
		return true;
	}

	@Override
	protected boolean innerCheckCanTargetSpell(CSimulation game, CUnit unit, int orderId, AbilityPointTarget target,
			AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		return true;
	}

	@Override
	protected boolean innerCheckCanTargetSpell(CSimulation game, CUnit unit, int orderId, CWidget target,
			AbilityTargetCheckReceiver<CWidget> receiver) {
		receiver.orderIdNotAccepted();
		return false;
	}

	@Override
	protected boolean innerCheckCanTargetSpell(CSimulation game, CUnit unit, int orderId,
			AbilityTargetCheckReceiver<Void> receiver) {
		receiver.orderIdNotAccepted();
		return false;
	}

}
