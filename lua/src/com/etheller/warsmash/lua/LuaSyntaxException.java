package com.etheller.warsmash.lua;

/** A chunk that did not compile. */
public class LuaSyntaxException extends LuaException {
	private static final long serialVersionUID = 1L;

	public LuaSyntaxException(final String message) {
		super(message);
	}
}
