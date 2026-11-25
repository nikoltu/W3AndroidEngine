package com.etheller.warsmash.lua;

/** The engine's random number generator that backs {@code math.random}. */
@FunctionalInterface
public interface LuaRandomSource {
	/** The next 64 random bits. */
	long nextLong();
}
