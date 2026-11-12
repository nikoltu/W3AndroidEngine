package com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders;

import java.util.Locale;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.util.Log;

public class OrderIdUtils {
	private static final Map<Integer, String> orderIdToString = new HashMap<>();
	private static final Map<String, Integer> stringToOrderId = new HashMap<>();
	// Measured on 1.29: OrderId2String gives null for these, OrderId("stunned") is 0.
	private static final Set<Integer> NO_STRING = new HashSet<>(
			Arrays.asList(OrderIds.stunned, OrderIds.skillmenu, OrderIds.smartvariant));

	static {
		for (final Field field : OrderIds.class.getDeclaredFields()) {
			final String name = field.getName();
			try {
				final Object value = field.get(null);
				if ((value instanceof Integer) && !NO_STRING.contains(value)) {
					final Integer orderId = (Integer) value;
					orderIdToString.put(orderId, name);
					stringToOrderId.put(name, orderId);
				}
			}
			catch (final IllegalArgumentException e) {
				Log.error(e);
			}
			catch (final IllegalAccessException e) {
				Log.error(e);
			}
		}
	}

	public static int getOrderId(final String orderIdString) {
		final Integer orderId = stringToOrderId.get(orderIdString.toLowerCase(Locale.ROOT));
		if (orderId == null) {
			return 0;
		}
		return orderId;
	}

	public static String getStringFromOrderId(final Integer orderId) {
		final String name = orderIdToString.get(orderId);
		// "_" names are placeholders for ids the game has no string for (1.29 OrderId2String gives null)
		if ((name != null) && name.startsWith("_")) {
			return null;
		}
		return name;
	}
}
