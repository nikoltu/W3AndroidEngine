package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities;

import com.etheller.interpreter.ast.util.CHandle;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;

public interface CAbilityView extends CHandle {
	void checkCanUse(CSimulation game, CUnit unit, int playerIndex, int orderId, boolean autoOrder,
			AbilityActivationReceiver receiver);

	void checkCanTarget(CSimulation game, CUnit unit, int playerIndex, int orderId, boolean autoOrder, CWidget target,
			AbilityTargetCheckReceiver<CWidget> receiver);

	void checkCanTarget(CSimulation game, CUnit unit, int playerIndex, int orderId, boolean autoOrder,
			AbilityPointTarget target, AbilityTargetCheckReceiver<AbilityPointTarget> receiver);

	void checkCanTargetNoTarget(CSimulation game, CUnit unit, int playerIndex, int orderId, boolean autoOrder,
			AbilityTargetCheckReceiver<Void> receiver);

	/**
	 * Whether the command card asks the player for a target before this order is given, although the order is also
	 * accepted from a script without one (a transport's unload-all).
	 */
	default boolean needsTargetFromCommandCard(final int orderId) {
		return false;
	}

	/**
	 * Whether this order is given to the ability with a unit or point target. A script's immediate order for it is
	 * accepted when the ability is usable, although nothing happens without a target.
	 */
	default boolean isTargetOrder(final int orderId) {
		return false;
	}

	void checkRequirementsMet(CSimulation game, CUnit unit, AbilityActivationReceiver receiver);

	boolean isRequirementsMet(CSimulation game, CUnit unit);

	@Override
	int getHandleId();

	War3ID getAlias();

	War3ID getCode();

	boolean isHero();

	boolean isDisabled();

	boolean isClickDisabled();

	boolean isIconShowing();

	/**
	 * The order id of the menu button this ability's own button is inside (a spell book), 0 when it is on the main
	 * command card.
	 */
	default int getContainerMenuOrderId() {
		return 0;
	}

	boolean isPermanent();

	boolean isPhysical();

	boolean isMagic();

	/** Whether only one unit of a selected group casts the ability. */
	default boolean isUniqueCast() {
		return false;
	}

	boolean isUniversal();

	CAbilityCategory getAbilityCategory();

	<T> T visit(CAbilityVisitor<T> visitor);
}
