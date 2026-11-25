package com.etheller.warsmash.lua;

/**
 * A Lua coroutine driven from Java: created from a function with {@link LuaState#newCoroutine()}, resumed with
 * {@link LuaState#resume}. A Java function running inside it may yield with {@link LuaState#yield}. Release it
 * with {@link LuaState#release} when it is not needed any more (the state's registry keeps it alive until then).
 */
public final class LuaCoroutine {
	final long thread;
	final int ref;
	boolean finished;
	boolean released;

	LuaCoroutine(final long thread, final int ref) {
		this.thread = thread;
		this.ref = ref;
	}

	/** True once the body returned or raised an error; a finished coroutine cannot be resumed. */
	public boolean isFinished() {
		return this.finished;
	}
}
