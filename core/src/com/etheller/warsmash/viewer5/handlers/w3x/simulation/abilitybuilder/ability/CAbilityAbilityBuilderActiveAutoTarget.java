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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABAction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABCondition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.AbilityBuilderConfiguration;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.types.impl.CAbilityTypeAbilityBuilderLevelData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIdUtils;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

public class CAbilityAbilityBuilderActiveAutoTarget extends CAbilityAbilityBuilderGenericActive {
	private ABBehavior behavior;
	private final int instantOrderId;

	public CAbilityAbilityBuilderActiveAutoTarget(int handleId, War3ID code, War3ID alias,
			List<CAbilityTypeAbilityBuilderLevelData> levelData, AbilityBuilderConfiguration config,
			Map<String, Object> localStore) {
		super(handleId, code, alias, levelData, config, localStore);
		this.instantOrderId = config.getInstantCastId() == null ? 0
				: OrderIdUtils.getOrderId(config.getInstantCastId());
	}
	
	@Override
	public void onAdd(CSimulation game, CUnit unit) {
		this.behavior = this.createRangedBehavior(unit);
		super.onAdd(game, unit);
	}

	@Override
	protected boolean innerCheckCanUseSpell(CSimulation game, CUnit unit, int orderId,
			AbilityActivationReceiver receiver) {
		return true;
	}

	@Override
	protected boolean innerCheckCanTargetSpell(CSimulation game, CUnit unit, int orderId,
			AbilityTargetCheckReceiver<Void> receiver) {
		return true;
	}

	@Override
	protected boolean innerCheckCastOrderId(final CSimulation game, final CUnit unit, final int orderId) {
		return super.innerCheckCastOrderId(game, unit, orderId)
				|| ((this.instantOrderId != 0) && (orderId == this.instantOrderId));
	}

	// The corpse conditions only apply to the no-target order: an explicitly targeted corpse may be anywhere.
	@Override
	protected void innerCheckExtraCastConditions(CSimulation game, CUnit unit, int orderId,
			AbilityActivationReceiver receiver) {
		if (innerCheckCanUseSpell(game, unit, orderId, receiver)) {
			receiver.useOk();
		}
	}

	private String checkNoTargetConditions(CSimulation game, CUnit unit) {
		if (this.config.getExtraCastConditions() != null) {
			for (ABCondition condition : this.config.getExtraCastConditions()) {
				if (!condition.callback(game, unit, this.localStore, this.castId)) {
					final String failReason = (String) this.localStore.get(ABLocalStoreKeys.CANTUSEREASON);
					return failReason != null ? failReason : "unknown";
				}
			}
		}
		return null;
	}

	@Override
	protected void innerCheckCanTargetNoTarget(CSimulation game, CUnit unit, int orderId,
			AbilityTargetCheckReceiver<Void> receiver) {
		if (innerCheckCanTargetSpell(game, unit, orderId, receiver)) {
			final String failReason = checkNoTargetConditions(game, unit);
			if (failReason == null) {
				receiver.targetOk(null);
			} else if (failReason.equals("unknown")) {
				receiver.orderIdNotAccepted();
			} else {
				receiver.targetCheckFailed(failReason);
			}
		}
	}

	@Override
	public void checkCanAutoTargetNoTarget(final CSimulation game, final CUnit unit, int playerIndex,
			final int orderId, final AbilityTargetCheckReceiver<Void> receiver) {
		this.localStore.put(ABLocalStoreKeys.combineKey(ABLocalStoreKeys.ISAUTOCAST, this.castId), true);
		if (checkNoTargetConditions(game, unit) != null) {
			receiver.orderIdNotAccepted();
			return;
		}
		super.checkCanAutoTargetNoTarget(game, unit, playerIndex, orderId, receiver);
	}

	@Override
	public CBehavior beginNoTarget(CSimulation game, CUnit caster, int playerIndex, int orderId, boolean autoOrder) {
		if (orderId == this.instantOrderId) {
			orderId = getBaseOrderId();
		}
		castId++;
		this.localStore.put(ABLocalStoreKeys.combineKey(ABLocalStoreKeys.ISAUTOCAST, castId), autoOrder);
		CWidget target = autoTarget(game, caster);
		if (target != null) {
			this.runOnOrderIssuedActions(game, caster, orderId);
			this.behavior.setCastId(castId);
			return this.behavior.reset(game, target, playerIndex, orderId, autoOrder);
		} else {
			this.localStore.remove(ABLocalStoreKeys.ABILITYTARGETEDUNIT + castId);
			this.localStore.remove(ABLocalStoreKeys.ABILITYTARGETEDDESTRUCTABLE + castId);
			this.localStore.remove(ABLocalStoreKeys.ABILITYTARGETEDITEM + castId);
			return null;
		}
	}

	@Override
	public void internalBegin(CSimulation game, CUnit caster, int playerIndex, int orderId, boolean autoOrder, AbilityTarget noTarget) {
		this.castId++;
		this.localStore.put(ABLocalStoreKeys.combineKey(ABLocalStoreKeys.ISAUTOCAST, castId), autoOrder);
		this.localStore.put(ABLocalStoreKeys.PREVIOUSBEHAVIOR, caster.getCurrentBehavior());
		CWidget target = autoTarget(game, caster);
		if (target != null) {
			this.runOnOrderIssuedActions(game, caster, orderId);
		} else {
			this.localStore.remove(ABLocalStoreKeys.ABILITYTARGETEDUNIT + castId);
			this.localStore.remove(ABLocalStoreKeys.ABILITYTARGETEDDESTRUCTABLE + castId);
			this.localStore.remove(ABLocalStoreKeys.ABILITYTARGETEDITEM + castId);
		}
	}
	
	public CWidget autoTarget(CSimulation game, CUnit caster) {
		CWidget target = null;

		if (this.config.getSpecialFields() != null && this.config.getSpecialFields().getAutoAquireTarget() != null) {
			this.localStore.remove(ABLocalStoreKeys.ABILITYTARGETEDUNIT + castId);
			this.localStore.remove(ABLocalStoreKeys.ABILITYTARGETEDDESTRUCTABLE + castId);
			this.localStore.remove(ABLocalStoreKeys.ABILITYTARGETEDITEM + castId);
			
			for (ABAction action : this.config.getSpecialFields().getAutoAquireTarget()) {
				action.runAction(game, caster, this.localStore, castId);
			}
			
			target = (CWidget) this.localStore.get(ABLocalStoreKeys.ABILITYTARGETEDUNIT + castId);
			if (target == null) {
				target = (CWidget) this.localStore.get(ABLocalStoreKeys.ABILITYTARGETEDDESTRUCTABLE + castId);
				if (target == null) {
					target = (CWidget) this.localStore.get(ABLocalStoreKeys.ABILITYTARGETEDITEM + castId);
				}
			}
		}
		return target;
	}

	protected boolean innerCheckCanTargetSpell(CSimulation game, CUnit unit, int orderId, CWidget target,
			AbilityTargetCheckReceiver<CWidget> receiver) {
		CWidget prevTarget = null;
		prevTarget = (CWidget) this.localStore.get(ABLocalStoreKeys.ABILITYTARGETEDUNIT + castId);
		if (prevTarget == null) {
			prevTarget = (CWidget) this.localStore.get(ABLocalStoreKeys.ABILITYTARGETEDDESTRUCTABLE + castId);
			if (prevTarget == null) {
				prevTarget = (CWidget) this.localStore.get(ABLocalStoreKeys.ABILITYTARGETEDITEM + castId);
			}
		}
		if (target == prevTarget) {
			return true;
		}
		if (orderId == getBaseOrderId()) {
			final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
			if ((targetUnit == null)
					|| (targetUnit.isDead() && !targetUnit.isHidden() && targetUnit.isUsableCorpse())) {
				return true;
			}
			receiver.orderIdNotAccepted();
			return false;
		} else {
			receiver.orderIdNotAccepted();
			return false;
		}
	}
	


	@Override
	public CBehavior begin(CSimulation game, CUnit caster, int playerIndex, int orderId, boolean autoOrder, CWidget target) {
		this.castId++;
		this.localStore.put(ABLocalStoreKeys.combineKey(ABLocalStoreKeys.ISAUTOCAST, this.castId), autoOrder);
		this.localStore.put(ABLocalStoreKeys.ABILITYTARGETEDUNIT + this.castId, target.visit(AbilityTargetVisitor.UNIT));
		this.localStore.put(ABLocalStoreKeys.ABILITYTARGETEDITEM + this.castId, target.visit(AbilityTargetVisitor.ITEM));
		this.localStore.put(ABLocalStoreKeys.ABILITYTARGETEDDESTRUCTABLE + this.castId,
				target.visit(AbilityTargetVisitor.DESTRUCTABLE));
		this.localStore.put(ABLocalStoreKeys.PREVIOUSBEHAVIOR, caster.getCurrentBehavior());
		this.runOnOrderIssuedActions(game, caster, orderId);
		this.behavior.setCastId(this.castId);
		return this.behavior.reset(game, target, playerIndex, orderId, autoOrder);
	}

	// Unused
	
	@Override
	public CBehavior begin(CSimulation game, CUnit caster, int playerIndex, int orderId, boolean autoOrder, AbilityPointTarget point) {
		return null;
	}

	@Override
	protected boolean innerCheckCanTargetSpell(CSimulation game, CUnit unit, int orderId, AbilityPointTarget target,
			AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		receiver.orderIdNotAccepted();
		return false;
	}

}
