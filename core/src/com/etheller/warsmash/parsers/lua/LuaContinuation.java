package com.etheller.warsmash.parsers.lua;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.execution.ThreadContinuation;
import com.etheller.warsmash.lua.LuaCoroutine;
import com.etheller.warsmash.lua.LuaState;
import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedTransient;

/**
 * A Lua function that a JASS thread of the engine runs. The function is one coroutine: a wait of the thread yields it,
 * and the thread resumes it when it wakes.
 */
public final class LuaContinuation implements ThreadContinuation {
	private final LuaEnvironment environment;
	private final int codeId;
	/** Where the coroutine is in the state a save holds; -1 when it is not in one. */
	private int saveIndex = -1;
	@SavedTransient
	private LuaCoroutine coroutine;
	@SavedTransient
	private JassThread thread;

	LuaContinuation(final LuaEnvironment environment, final int codeId) {
		this.environment = environment;
		this.codeId = codeId;
	}

	int getCodeId() {
		return this.codeId;
	}

	int getSaveIndex() {
		return this.saveIndex;
	}

	void setSaveIndex(final int saveIndex) {
		this.saveIndex = saveIndex;
	}

	/** Gives back the coroutine a loaded game brought with it. */
	void attach(final LuaCoroutine loaded) {
		this.coroutine = loaded;
		this.environment.started(this);
	}

	JassThread getThread() {
		return this.thread;
	}

	LuaCoroutine getCoroutine() {
		return this.coroutine;
	}

	@Override
	public boolean resume(final JassThread jassThread) {
		this.thread = jassThread;
		this.environment.updateClock();
		final LuaState lua = this.environment.lua();
		final int base = lua.getTop();
		if (this.coroutine == null) {
			this.environment.pushCode(this.codeId);
			this.coroutine = lua.newCoroutine();
			this.environment.started(this);
		}
		final int results;
		this.environment.enter(this);
		try {
			results = lua.resume(this.coroutine, 0);
		}
		catch (final RuntimeException e) {
			lua.setTop(base);
			finish(lua);
			throw e;
		}
		finally {
			this.environment.leave(this);
		}
		if (this.coroutine.isFinished()) {
			if ((jassThread.stackFrame != null) && (jassThread.stackFrame.stackBase != null)) {
				jassThread.stackFrame.push(results > 0 ? this.environment.toJassValue(base + 1)
						: com.etheller.interpreter.ast.value.BooleanJassValue.FALSE);
			}
			lua.setTop(base);
			finish(lua);
			return false;
		}
		lua.setTop(base);
		if (!jassThread.isSleeping()) {
			finish(lua);
			throw new IllegalStateException("A Lua function yielded without a wait");
		}
		return true;
	}

	private void finish(final LuaState lua) {
		if (this.coroutine != null) {
			lua.release(this.coroutine);
			this.coroutine = null;
		}
		this.environment.finished(this);
		this.environment.releaseCode(this.codeId);
	}
}
