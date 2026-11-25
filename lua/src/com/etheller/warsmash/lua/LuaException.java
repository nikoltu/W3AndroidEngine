package com.etheller.warsmash.lua;

/** A Lua error; the message is the Lua error text followed by the stack traceback. */
public class LuaException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public LuaException(final String message) {
		super(message);
	}

	public LuaException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
