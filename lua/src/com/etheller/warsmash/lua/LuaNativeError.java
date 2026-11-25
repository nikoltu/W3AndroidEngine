package com.etheller.warsmash.lua;

/**
 * An error a Java function raises as luaL_error does: the message gets the position of the Lua code that made the call
 * in front of it.
 */
public class LuaNativeError extends LuaException {
	private static final long serialVersionUID = 1L;

	public LuaNativeError(final String message) {
		super(message);
	}
}
