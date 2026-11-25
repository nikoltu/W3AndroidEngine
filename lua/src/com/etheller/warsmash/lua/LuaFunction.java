package com.etheller.warsmash.lua;

/**
 * A Java function callable from Lua. The arguments are the stack slots 1..getTop(); the function pushes its
 * results and returns how many it pushed. A thrown exception becomes a Lua error carrying it as the cause.
 */
@FunctionalInterface
public interface LuaFunction {
	int call(LuaState lua);
}
