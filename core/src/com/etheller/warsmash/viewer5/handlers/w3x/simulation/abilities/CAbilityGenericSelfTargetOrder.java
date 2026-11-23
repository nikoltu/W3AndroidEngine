package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

/**
 * An ability from the object data that does nothing, has an order and allows no targets: a unit target order of it
 * is accepted only on the caster itself.
 */
public class CAbilityGenericSelfTargetOrder extends CAbilityGenericDoNothing {
	private final int orderId;

	public CAbilityGenericSelfTargetOrder(final War3ID code, final War3ID alias, final int handleId,
			final int orderId) {
		super(code, alias, handleId);
		this.orderId = orderId;
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		if (orderId == this.orderId) {
			receiver.useOk();
		}
		else {
			receiver.notAnActiveAbility();
		}
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		if ((orderId == this.orderId) && (target == unit)) {
			receiver.targetOk(target);
		}
		else {
			receiver.orderIdNotAccepted();
		}
	}
}
