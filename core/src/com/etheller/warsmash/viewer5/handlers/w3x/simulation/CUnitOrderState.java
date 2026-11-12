package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.LinkedList;
import java.util.Queue;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;

final class CUnitOrderState {
	CBehavior currentBehavior;
	final Queue<COrder> orderQueue = new LinkedList<>();
	boolean acceptingOrders = true;
	CBehavior defaultBehavior;
	CBehavior interruptedDefaultBehavior;
	CBehavior interruptedBehavior;
	COrder lastStartedOrder = null;
}
