package com.etheller.warsmash.lua;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One Lua 5.3 state (the engine's patched copy: deterministic iteration order, no finalizers, no io, no package).
 *
 * <p>
 * The stack API follows the Lua C API: indexes are 1-based from the bottom, negative ones count from the top. A
 * state is used by one thread at a time. Only table operations without metamethods are offered; code that may run
 * Lua ({@link #call}, {@link #display}) is protected, so a Lua error is always a {@link LuaException}.
 */
public final class LuaState implements AutoCloseable {
	public static final int TYPE_NONE = -1;
	public static final int TYPE_NIL = 0;
	public static final int TYPE_BOOLEAN = 1;
	public static final int TYPE_LIGHTUSERDATA = 2;
	public static final int TYPE_NUMBER = 3;
	public static final int TYPE_STRING = 4;
	public static final int TYPE_TABLE = 5;
	public static final int TYPE_FUNCTION = 6;
	public static final int TYPE_USERDATA = 7;
	public static final int TYPE_THREAD = 8;

	private static final int NO_MEMORY = Integer.MIN_VALUE;
	private static final int STATUS_FINISHED = 0;
	private static final int STATUS_YIELDED = 1;
	private static final int STATUS_NO_ROOM = 100;

	private final List<LuaFunction> functions = new ArrayList<>();
	private final Map<LuaFunction, Integer> functionIds = new IdentityHashMap<>();
	private long state; // native context
	private long current; // the Lua thread the Java code is working on: the main thread, or a coroutine during a callback
	private int depth;
	private Throwable pending;
	private LuaRandomSource randomSource;
	private Consumer<String> printHandler;

	public LuaState() {
		this(0);
	}

	/** True if the native Lua library can be loaded on this system. */
	public static boolean isSupported() {
		try {
			LuaNative.load();
			return true;
		}
		catch (final LinkageError | RuntimeException e) {
			return false;
		}
	}

	/**
	 * @param memoryLimitBytes limit of the memory the state may use, 0 for none; scripts that exceed it get a Lua
	 *                         "not enough memory" error
	 */
	public LuaState(final long memoryLimitBytes) {
		LuaNative.load();
		this.state = nNew(this, memoryLimitBytes);
		if (this.state == 0) {
			throw new IllegalStateException("Cannot create a Lua state");
		}
		this.current = nMain(this.state);
	}

	@Override
	public void close() {
		if (this.state != 0) {
			nClose(this.state);
			this.state = 0;
			this.current = 0;
			this.functions.clear();
			this.functionIds.clear();
		}
	}

	private long handle() {
		if (this.state == 0) {
			throw new IllegalStateException("The Lua state is closed");
		}
		return this.current;
	}

	// --- engine hooks

	/** Makes {@code math.random} read the engine's generator; {@code null} returns to the built-in deterministic one. */
	public void setRandomSource(final LuaRandomSource source) {
		this.randomSource = source;
		handle();
		nSetRandomHook(this.state, source != null);
	}

	/** Where {@code print} writes; the default is standard output. */
	public void setPrintHandler(final Consumer<String> handler) {
		this.printHandler = handler;
	}

	/** The values of {@code os.time()} (seconds) and {@code os.clock()}, taken from the game, not from the machine. */
	public void setClock(final long timeSeconds, final double clockSeconds) {
		handle();
		nSetTime(this.state, timeSeconds, clockSeconds);
	}

	public void setMemoryLimit(final long bytes) {
		handle();
		nSetMemoryLimit(this.state, bytes);
	}

	public long getMemoryUsed() {
		handle();
		return nMemoryUsed(this.state);
	}

	public void collectGarbage() {
		nCollect(handle());
	}

	// --- chunks and calls

	/** Compiles a text chunk and pushes it as a function. {@code chunkName}: "=name" shows as name, "@file" as a file. */
	public void load(final String code, final String chunkName) {
		load(code.getBytes(StandardCharsets.UTF_8), chunkName);
	}

	public void load(final byte[] code, final String chunkName) {
		final byte[] error = nLoad(handle(), code, chunkName.getBytes(StandardCharsets.UTF_8));
		if (error != null) {
			throw new LuaSyntaxException(new String(error, StandardCharsets.UTF_8));
		}
	}

	/**
	 * Calls the function that lies below its {@code argumentCount} arguments on the stack; they are replaced by the
	 * results ({@code resultCount}, or all of them for {@link #MULTIPLE_RESULTS}).
	 */
	public void call(final int argumentCount, final int resultCount) {
		final long thread = handle();
		if (this.depth == 0) {
			this.pending = null;
		}
		this.depth++;
		final byte[] error;
		try {
			error = nCall(this.state, thread, argumentCount, resultCount, this.depth == 1);
		}
		finally {
			this.depth--;
		}
		if (error != null) {
			final Throwable cause = this.pending;
			this.pending = null;
			throw new LuaException(new String(error, StandardCharsets.UTF_8), cause);
		}
	}

	public static final int MULTIPLE_RESULTS = -1;

	/**
	 * Limits the Lua instructions one {@link #call} or {@link #resume} from Java may run (nested ones share the
	 * budget of the outer one); past it the running code gets the error "operation limit", which the script can
	 * catch but not outrun, because the budget is not renewed until the outer call ends. The limit is counted in
	 * instructions, not time, so it is the same on every machine. 0 removes it. Set it before the coroutines are
	 * created; checking it makes the interpreter slower (a few tens of percent).
	 */
	public void setOperationLimit(final long instructions) {
		handle();
		nSetOperationLimit(this.state, instructions);
	}

	// --- coroutines

	/** Pops the function on top of the stack into a new coroutine; its first resume starts the function. */
	public LuaCoroutine newCoroutine() {
		final long[] out = new long[2];
		if (!nNewCoroutine(this.state, handle(), out)) {
			throw new LuaException("cannot create a coroutine (not enough memory, or no function on the stack)");
		}
		return new LuaCoroutine(out[0], (int) out[1]);
	}

	/**
	 * Resumes the coroutine with the {@code argumentCount} values on top of the stack, which are taken from it. Returns
	 * how many values were pushed in their place: the ones the coroutine yielded (it is then suspended; check
	 * {@link LuaCoroutine#isFinished()}) or returned. An error in the coroutine throws a {@link LuaException} whose
	 * message has the traceback and whose cause is the Java exception that caused it, if there was one.
	 */
	public int resume(final LuaCoroutine coroutine, final int argumentCount) {
		final long thread = handle();
		if (coroutine.finished || coroutine.released) {
			pop(argumentCount);
			throw new LuaException("cannot resume dead coroutine");
		}
		if (this.depth == 0) {
			this.pending = null;
		}
		this.depth++;
		final long result;
		try {
			result = nResume(this.state, thread, coroutine.thread, argumentCount, this.depth == 1);
		}
		finally {
			this.depth--;
		}
		final int status = (int) (result & 0xFF);
		if (status == STATUS_FINISHED) {
			coroutine.finished = true;
			return (int) (result >> 8);
		}
		if (status == STATUS_YIELDED) {
			return (int) (result >> 8);
		}
		if (status == STATUS_NO_ROOM) {
			throw new LuaException("stack overflow");
		}
		coroutine.finished = true;
		final byte[] error = nCoroutineError(thread, coroutine.thread);
		final Throwable cause = this.pending;
		this.pending = null;
		throw new LuaException(new String(error, StandardCharsets.UTF_8), cause);
	}

	/** Lets the state forget the coroutine (its memory is freed by a later collection). */
	public void release(final LuaCoroutine coroutine) {
		if (!coroutine.released) {
			coroutine.released = true;
			if (this.state != 0) {
				nReleaseCoroutine(handle(), coroutine.ref);
			}
		}
	}

	/**
	 * For a {@link LuaFunction} to end with {@code return lua.yield(n)}: suspends the running coroutine, handing the
	 * {@code n} values on top of the stack to whoever resumed it; the values passed to the next resume become the
	 * results of the Java function's call. Outside a coroutine the call becomes a Lua error. Works through
	 * {@code pcall}, metamethods and iterators of the script, but not through Java frames in between.
	 */
	public int yield(final int resultCount) {
		return -2 - resultCount;
	}

	/** Compiles and runs a chunk, leaving nothing on the stack. */
	public void run(final String code, final String chunkName) {
		load(code, chunkName);
		call(0, 0);
	}

	/** The text of the value as {@code tostring} makes it. */
	public String display(final int index) {
		this.pending = null;
		return new String(nToDisplay(this.state, handle(), index), StandardCharsets.UTF_8);
	}

	// --- persistence

	/**
	 * The Lua values the value at {@code valueIndex} leads to (tables, closures, strings, and suspended coroutines,
	 * also those suspended inside a Java function) as bytes; {@link #unpersist} makes an equal value from them, in
	 * this or another state. Java functions are written as their registration id, so the other state has to
	 * register the same functions in the same order, and the functions of the standard libraries by name.
	 * {@code permanentsIndex} may be a table of object to name pairs for values that must not be written out
	 * (the other state is given the same names, with the values it wants in their place), or 0 for none.
	 * Running coroutines, userdata and the memory limit's state are not persisted.
	 */
	public byte[] persist(final int valueIndex, final int permanentsIndex) {
		final long thread = handle();
		final byte[] bytes = nPersist(this.state, thread, valueIndex, permanentsIndex);
		if (bytes == null) {
			throw new LuaException(new String(nTakeError(this.state), StandardCharsets.UTF_8));
		}
		return bytes;
	}

	public byte[] persist(final int valueIndex) {
		return persist(valueIndex, 0);
	}

	/**
	 * Pushes the value that {@link #persist} wrote. {@code permanentsIndex}: the table the persisting side gave, with
	 * the same keys naming the replacement values here (as object to name pairs, like on that side), or 0.
	 */
	public void unpersist(final byte[] data, final int permanentsIndex) {
		final long thread = handle();
		checkStack(1);
		if (!nUnpersist(this.state, thread, data, permanentsIndex)) {
			throw new LuaException(new String(nTakeError(this.state), StandardCharsets.UTF_8));
		}
	}

	public void unpersist(final byte[] data) {
		unpersist(data, 0);
	}

	// --- Java functions

	/** Pushes a function that calls {@code function}; the same function object always gets the same id. */
	public void pushFunction(final LuaFunction function) {
		Integer id = this.functionIds.get(function);
		if (id == null) {
			id = this.functions.size();
			this.functions.add(function);
			this.functionIds.put(function, id);
		}
		checkStack(1);
		if (!nPushFunction(handle(), id)) {
			throw outOfMemory();
		}
	}

	public void register(final String name, final LuaFunction function) {
		pushFunction(function);
		setGlobal(name);
	}

	// called by the native bridge
	@SuppressWarnings("unused")
	private int dispatch(final int id, final long thread) {
		final long saved = this.current;
		this.current = thread;
		try {
			return this.functions.get(id).call(this);
		}
		catch (final Throwable t) {
			this.pending = t;
			final String text;
			if (t instanceof LuaNativeError) {
				text = new String(nWhere(thread), StandardCharsets.UTF_8) + t.getMessage();
			}
			else if (t instanceof LuaException) {
				text = t.getMessage();
			}
			else {
				text = String.valueOf(t);
			}
			if (!nCheckStack(thread, 2) || !nPushBytes(thread, text.getBytes(StandardCharsets.UTF_8))) {
				return NO_MEMORY;
			}
			return -1;
		}
		finally {
			this.current = saved;
		}
	}

	@SuppressWarnings("unused")
	private long randomHook() {
		return this.randomSource.nextLong();
	}

	@SuppressWarnings("unused")
	private void printHook(final byte[] text) {
		final String string = new String(text, StandardCharsets.UTF_8);
		if (this.printHandler != null) {
			this.printHandler.accept(string);
		}
		else {
			System.out.print(string);
		}
	}

	@SuppressWarnings("unused")
	private void setPending(final Throwable throwable) {
		this.pending = throwable;
	}

	// --- stack

	public int getTop() {
		return nGetTop(handle());
	}

	public void setTop(final int index) {
		final int grow = index - getTop();
		if (grow > 0) {
			checkStack(grow);
		}
		nSetTop(handle(), index);
	}

	public void pop(final int count) {
		nSetTop(handle(), -count - 1);
	}

	public void pushValue(final int index) {
		checkStack(1);
		nPushValue(handle(), index);
	}

	public void remove(final int index) {
		nRemove(handle(), index);
	}

	public void checkStack(final int extra) {
		if (!nCheckStack(handle(), extra)) {
			throw new LuaException("stack overflow");
		}
	}

	private static LuaException outOfMemory() {
		return new LuaException("not enough memory");
	}

	private static int checked(final int type) {
		if (type == -2) {
			throw outOfMemory();
		}
		return type;
	}

	// --- push

	public void pushNil() {
		checkStack(1);
		nPushNil(handle());
	}

	public void pushBoolean(final boolean value) {
		checkStack(1);
		nPushBoolean(handle(), value);
	}

	public void pushInteger(final long value) {
		checkStack(1);
		nPushInteger(handle(), value);
	}

	public void pushNumber(final double value) {
		checkStack(1);
		nPushNumber(handle(), value);
	}

	public void pushString(final String value) {
		pushBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Pushes a light userdata: an id that is a value of its own, equal to every other light userdata of the same id,
	 * usable as a table key and kept by {@link #persist}. The id must fit in a pointer of the platform (32 bits at
	 * least).
	 */
	public void pushLightUserdata(final long id) {
		checkStack(1);
		nPushLightUserdata(handle(), id);
	}

	/** The id of the light userdata at the index, or 0 if the value is not one. */
	public long toLightUserdata(final int index) {
		return nToLightUserdata(handle(), index);
	}

	/** Pushes the coroutine, which must not be released. */
	public void pushCoroutine(final LuaCoroutine coroutine) {
		if (coroutine.released) {
			throw new LuaException("the coroutine is released");
		}
		checkStack(1);
		nPushCoroutine(handle(), coroutine.ref);
	}

	/**
	 * The coroutine at the index as a {@link LuaCoroutine}, for one that {@link #unpersist} made; null if the value
	 * is not a coroutine. The coroutine counts as unfinished.
	 */
	public LuaCoroutine toCoroutine(final int index) {
		final long[] out = new long[2];
		if (!nToCoroutine(this.state, handle(), index, out)) {
			return null;
		}
		return new LuaCoroutine(out[0], (int) out[1]);
	}

	/** True if the coroutine is the one the running Lua code belongs to. */
	public boolean isRunning(final LuaCoroutine coroutine) {
		return this.current == coroutine.thread;
	}

	public void pushBytes(final byte[] value) {
		checkStack(1);
		if (!nPushBytes(handle(), value)) {
			throw outOfMemory();
		}
	}

	// --- read

	public int type(final int index) {
		return nType(handle(), index);
	}

	public boolean isNil(final int index) {
		return type(index) <= TYPE_NIL;
	}

	public boolean toBoolean(final int index) {
		return nToBoolean(handle(), index);
	}

	/** True for an integer subtype number (as {@code math.type(x) == "integer"}). */
	public boolean isInteger(final int index) {
		return nIsInteger(handle(), index);
	}

	/** True for a number or a string that converts to one. */
	public boolean isNumber(final int index) {
		return nIsNumber(handle(), index);
	}

	/** The integer value of a number or numeric string; 0 for other values. */
	public long toInteger(final int index) {
		return nToInteger(handle(), index);
	}

	public double toNumber(final int index) {
		return nToNumber(handle(), index);
	}

	/** The string or number at the index as text (a number is converted in place); {@code null} for other types. */
	public String toStringValue(final int index) {
		final byte[] bytes = nToBytes(handle(), index);
		return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
	}

	public byte[] toBytes(final int index) {
		return nToBytes(handle(), index);
	}

	public long rawLength(final int index) {
		return nRawLen(handle(), index);
	}

	// --- tables (raw: no metamethods)

	public void newTable() {
		checkStack(1);
		if (!nNewTable(handle())) {
			throw outOfMemory();
		}
	}

	/** Pushes {@code table[name]} of the table at the index and returns its type. */
	public int getField(final int index, final String name) {
		checkStack(1);
		return checked(nRawGetField(handle(), index, name.getBytes(StandardCharsets.UTF_8)));
	}

	/** {@code table[name] = top} for the table at the index; pops the value. */
	public void setField(final int index, final String name) {
		checkStack(1);
		if (!nRawSetField(handle(), index, name.getBytes(StandardCharsets.UTF_8))) {
			throw outOfMemory();
		}
	}

	public int getIndex(final int index, final long key) {
		checkStack(1);
		return nRawGetI(handle(), index, key);
	}

	/** {@code table[key] = top}; pops the value. */
	public void setIndex(final int index, final long key) {
		checkStack(1);
		if (!nRawSetI(handle(), index, key)) {
			throw outOfMemory();
		}
	}

	/** Replaces the key on top by {@code table[key]}; returns the type of the value. */
	public int getTable(final int index) {
		return nRawGet(handle(), index);
	}

	/** {@code table[key] = value} with the key and the value on top; pops both. */
	public void setTable(final int index) {
		checkStack(1);
		if (!nRawSet(handle(), index)) {
			throw outOfMemory();
		}
	}

	public int getGlobal(final String name) {
		checkStack(2);
		return checked(nGetGlobal(handle(), name.getBytes(StandardCharsets.UTF_8)));
	}

	/** Sets the global to the value on top and pops it. */
	public void setGlobal(final String name) {
		checkStack(2);
		if (!nSetGlobal(handle(), name.getBytes(StandardCharsets.UTF_8))) {
			throw outOfMemory();
		}
	}

	// --- native

	private static native long nNew(LuaState self, long memoryLimit);

	private static native long nMain(long state);

	private static native void nClose(long state);

	private static native void nSetRandomHook(long state, boolean on);

	private static native void nSetTime(long state, long seconds, double clock);

	private static native long nMemoryUsed(long state);

	private static native void nSetMemoryLimit(long state, long limit);

	private static native void nCollect(long thread);

	private static native byte[] nLoad(long thread, byte[] code, byte[] name);

	private static native byte[] nCall(long state, long thread, int arguments, int results, boolean resetBudget);

	private static native void nSetOperationLimit(long state, long limit);

	private static native boolean nIsNumber(long thread, int index);

	private static native byte[] nWhere(long thread);

	private static native boolean nNewCoroutine(long state, long thread, long[] out);

	private static native void nReleaseCoroutine(long thread, int ref);

	private static native long nResume(long state, long from, long coroutine, int arguments, boolean resetBudget);

	private static native byte[] nCoroutineError(long from, long coroutine);

	private static native byte[] nPersist(long state, long thread, int index, int permanents);

	private static native boolean nUnpersist(long state, long thread, byte[] data, int permanents);

	private static native byte[] nTakeError(long state);

	private static native byte[] nToDisplay(long state, long thread, int index);

	private static native int nGetTop(long thread);

	private static native void nSetTop(long thread, int index);

	private static native boolean nCheckStack(long thread, int extra);

	private static native void nPushValue(long thread, int index);

	private static native void nRemove(long thread, int index);

	private static native void nPushNil(long thread);

	private static native void nPushBoolean(long thread, boolean value);

	private static native void nPushInteger(long thread, long value);

	private static native void nPushNumber(long thread, double value);

	private static native void nPushLightUserdata(long thread, long value);

	private static native long nToLightUserdata(long thread, int index);

	private static native void nPushCoroutine(long thread, int ref);

	private static native boolean nToCoroutine(long state, long thread, int index, long[] out);

	private static native boolean nPushBytes(long thread, byte[] value);

	private static native boolean nPushFunction(long thread, int id);

	private static native int nType(long thread, int index);

	private static native boolean nToBoolean(long thread, int index);

	private static native boolean nIsInteger(long thread, int index);

	private static native long nToInteger(long thread, int index);

	private static native double nToNumber(long thread, int index);

	private static native byte[] nToBytes(long thread, int index);

	private static native long nRawLen(long thread, int index);

	private static native boolean nNewTable(long thread);

	private static native int nRawGetField(long thread, int index, byte[] name);

	private static native boolean nRawSetField(long thread, int index, byte[] name);

	private static native int nRawGetI(long thread, int index, long key);

	private static native boolean nRawSetI(long thread, int index, long key);

	private static native int nRawGet(long thread, int index);

	private static native boolean nRawSet(long thread, int index);

	private static native int nGetGlobal(long thread, byte[] name);

	private static native boolean nSetGlobal(long thread, byte[] name);
}
