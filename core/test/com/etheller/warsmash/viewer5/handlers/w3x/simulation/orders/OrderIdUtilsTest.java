package com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrderIdUtilsTest {
	@Test
	void orderNamesComeFromTheOrderIdFields() {
		assertEquals(OrderIds.smart, OrderIdUtils.getOrderId("smart"));
		assertEquals("itemdrag00", OrderIdUtils.getStringFromOrderId(852002));
		assertEquals(852013, OrderIdUtils.getOrderId("itemuse05"));
	}

	@Test
	void nineSlotInventoryIdsFollowTheGameData() {
		assertEquals(852002, OrderIds.itemDragFirst(false));
		assertEquals(852013, OrderIds.itemUseLast(false));
		assertEquals(85200200, OrderIds.itemDragFirst(true));
		assertEquals(85201300, OrderIds.itemUseLast(true));
	}
}
