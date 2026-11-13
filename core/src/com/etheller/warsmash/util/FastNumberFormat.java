package com.etheller.warsmash.util;

public class FastNumberFormat {
	public static String formatWholeNumber(final float value) {
		int intValue = (int) value;
		final StringBuilder builder = new StringBuilder(11);
		do {
			builder.append(intValue % 10);
			intValue /= 10;
		}
		while (intValue > 0);
		final int len = builder.length();
		final int halfLength = len / 2;
		for (int i = 0; i < halfLength; i++) {
			final char swapCharA = builder.charAt(i);
			final char swapCharB = builder.charAt(len - 1 - i);
			builder.setCharAt(len - 1 - i, swapCharA);
			builder.setCharAt(i, swapCharB);
		}
		return builder.toString();
	}
}
