package com.etheller.warsmash.parsers.lua;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.function.JassParameter;
import com.etheller.interpreter.ast.function.NativeJassFunction;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.util.JassNumbers;
import com.etheller.interpreter.ast.util.JassStrings;
import com.etheller.interpreter.ast.value.ArrayJassValue;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassType;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.warsmash.lua.LuaCoroutine;
import com.etheller.warsmash.lua.LuaException;
import com.etheller.warsmash.lua.LuaFunction;
import com.etheller.warsmash.lua.LuaNativeError;
import com.etheller.warsmash.lua.LuaRandomSource;
import com.etheller.warsmash.lua.LuaState;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

/**
 * Runs the Lua script of a map beside the engine's JASS machinery: every native the engine has is a Lua global that
 * converts its arguments by the type common.j gives, and Lua functions are code that natives take. A Lua function runs
 * on a thread of the same queue the JASS threads run on, so a wait (TriggerSleepAction) suspends it as it suspends
 * JASS code.
 */
public final class LuaEnvironment implements AutoCloseable {
	/** What the environment needs from the game around it. */
	public interface Host {
		/** The random numbers of the game: math.random takes from them. */
		LuaRandomSource random();

		/** The text a script prints. */
		void print(String text);

		/** The seconds of game time, for os.clock. */
		double gameSeconds();

		/** The game's start in seconds since 1970, for os.time. */
		long startTimeSeconds();

		/**
		 * While the initializers of the globals run, the handles they make take the early ids of the handle table,
		 * as they do for JASS.
		 */
		default void setEarlyHandleIds(final boolean early) {
		}

		/** Tells the listener of every object the game destroys, so the script can let go of it. */
		default void watchReleases(final Consumer<Object> listener) {
		}

		/** The script let go of the object: the game's own tables of script handles let go of it too. */
		default void forgetHandle(final Object handle) {
		}

		/** True while the script runs in the lobby (its config function), where the game's natives do not exist. */
		default boolean isLobby() {
			return false;
		}
	}

	/** Functions that scripts get which are not natives; they are registered in this order. */
	private static final String BOOTSTRAP = String.join("\n",
			"function __jarray(default)",
			"  return setmetatable({}, {__index = function() return default end})",
			"end",
			// Generated scripts keep their real globals in this table and fill it by calling it with a function.
			"do",
			"  local store = {}",
			"  globals = setmetatable({}, {",
			"    __index = function(t, k) local v = store[k] if v ~= nil then return v end return _G[k] end,",
			"    __newindex = function(t, k, v)",
			"      local old = store[k]",
			"      store[k] = v",
			"      if type(v) == 'number' then __wc_variable(k, old or 0.0, v) end",
			"    end,",
			"    __call = function(self, declare) declare(self) end})",
			"end",
			"loadstring = nil",
			// The random numbers of a script are the game's: the same draws as GetRandomInt and GetRandomReal, and
			// the seed is the game's seed.
			"if SetRandomSeed and GetRandomInt and GetRandomReal then",
			"  local seed, int, real = SetRandomSeed, GetRandomInt, GetRandomReal",
			"  function math.randomseed(x) seed(x) end",
			"  function math.random(m, n)",
			"    if m == nil then return real(0, 1) end",
			"    if n == nil then return int(1, m) end",
			"    return int(m, n)",
			"  end",
			"end",
			// A function a native keeps as code has a count of what holds it; at zero it leaves the tables, which is
			// when it can be collected. Numbers are never reused, so a saved game and a replay agree on them.
			"function __wc_retain(id)",
			"  local refs = __wc.refs",
			"  refs[id] = (refs[id] or 0) + 1",
			"end",
			"function __wc_release(id)",
			"  local refs = __wc.refs",
			"  local n = (refs[id] or 0) - 1",
			"  if n > 0 then refs[id] = n return end",
			"  refs[id] = nil",
			"  local f = __wc.codes[id]",
			"  if f ~= nil then __wc.ids[f] = nil __wc.codes[id] = nil end",
			"end",
			// What the objects of triggers hold. A condition or filter holds the code it was made with, a trigger's
			// action or condition holds its own share; they let go when the object or the trigger is destroyed.
			"function __wc_track(kind, a, b, r, id)",
			"  local wc = __wc",
			"  local owned = wc.owned or {}",
			"  wc.owned = owned",
			"  local shared = wc.shared or {}",
			"  wc.shared = shared",
			"  local function free(h)",
			"    local held = owned[h]",
			"    if held then owned[h] = nil __wc_release(held) end",
			"  end",
			"  local function lists(trigger)",
			"    local s = shared[trigger]",
			"    if not s then s = {actions = {}, conditions = {}} shared[trigger] = s end",
			"    return s",
			"  end",
			"  local function clear(list)",
			"    for i = 1, #list do free(list[i]) end",
			"    return {}",
			"  end",
			"  if kind == 1 then",
			"    if r and id then owned[r] = id end",
			"  elseif kind == 2 then",
			"    if r and id then owned[r] = id local s = lists(a) s.actions[#s.actions + 1] = r end",
			"  elseif kind == 3 then",
			"    local held = b and owned[b]",
			"    if r and held then",
			"      __wc_retain(held)",
			"      owned[r] = held",
			"      local s = lists(a)",
			"      s.conditions[#s.conditions + 1] = r",
			"    end",
			"  elseif kind == 4 then",
			"    if a then free(a) end",
			"  elseif kind == 5 then",
			"    if b then free(b) end",
			"  elseif kind == 6 then",
			"    local s = a and shared[a]",
			"    if s then s.actions = clear(s.actions) end",
			"  elseif kind == 7 then",
			"    local s = a and shared[a]",
			"    if s then s.conditions = clear(s.conditions) end",
			"  elseif kind == 8 then",
			"    local s = a and shared[a]",
			"    if s then",
			"      clear(s.actions)",
			"      clear(s.conditions)",
			"      shared[a] = nil",
			"    end",
			"  end",
			"end",
			"function __wc_stats()",
			"  local function count(t) local n = 0 for _ in pairs(t or {}) do n = n + 1 end return n end",
			"  return 'codes ' .. count(__wc.codes) .. ', refs ' .. count(__wc.refs) .. ', timers ' .. count(__wc.timers)",
			"    .. ', owned ' .. count(__wc.owned) .. ', triggers ' .. count(__wc.shared)",
			"end",
			// The code of each timer that was started with one; a timer keeps one handler.
			"function __wc_timer(timer, id)",
			"  local timers = __wc.timers",
			"  local old = timers[timer]",
			"  timers[timer] = id",
			"  if old then __wc_release(old) end",
			"  __wc.starts = __wc.starts + 1",
			"  return __wc.starts % 64 == 0",
			"end",
			"function __wc_timer_drop(timer)",
			"  local old = __wc.timers[timer]",
			"  if old then __wc.timers[timer] = nil __wc_release(old) end",
			"end",
			"function __wc_timer_keys()",
			"  local keys = {}",
			"  for k in pairs(__wc.timers) do keys[#keys + 1] = k end",
			"  table.sort(keys)",
			"  return keys",
			"end",
			// A saved game keeps the globals and the suspended coroutines: _G itself stays, its contents are copied.
			"function __wc_capture(threads)",
			"  local globals = {}",
			"  for k, v in pairs(_G) do if k ~= '__wc_perms' then globals[k] = v end end",
			"  return {globals = globals, threads = threads}",
			"end",
			"function __wc_restore(root)",
			"  local pairs, globals = pairs, _G",
			"  local perms = globals.__wc_perms",
			"  for k in pairs(globals) do globals[k] = nil end",
			"  for k, v in pairs(root.globals) do globals[k] = v end",
			"  globals.__wc_perms = perms",
			"  return root.threads",
			"end",
			// The functions the game gives a script, by the names they have before any script runs: a script may keep
			// a native under another global (a hook that calls the old one), and a save names them as they were.
			"function __wc_freeze_perms()",
			"  local perms, names = {}, {'_G', 'coroutine', 'table', 'string', 'math', 'utf8', 'os', 'debug'}",
			"  for _, lib in ipairs(names) do",
			"    local t = lib == '_G' and _G or _G[lib]",
			"    if type(t) == 'table' then",
			"      for k, v in pairs(t) do",
			"        if type(k) == 'string' and type(v) == 'function' and debug.getinfo(v, 'S').what == 'C' then",
			"          local name = 'wc.' .. lib .. '.' .. k",
			"          if perms[v] == nil or name < perms[v] then perms[v] = name end",
			"        end",
			"      end",
			"    end",
			"  end",
			"  __wc_perms = perms",
			"end",
			"function __wc_fill_perms(into)",
			"  for f, name in pairs(__wc_perms) do into[f] = name end",
			"end",
			// Newer scripts call this; the common.j of the game data does not declare it.
			"if UnitAlive == nil then",
			"  function UnitAlive(id) return not IsUnitType(id, UNIT_TYPE_DEAD) and GetUnitTypeId(id) ~= 0 end",
			"end");

	/**
	 * FourCC as the game's helper file has it: on line 40 of luahelper.lua, and a text of fewer than four characters
	 * fails in its arithmetic on the missing byte.
	 */
	private static final String FOUR_CC = "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n"
			+ "function FourCC(id) return string.byte(id, 1) * 16777216 + string.byte(id, 2) * 65536 + string.byte(id, 3) * 256 + string.byte(id, 4) end";

	/** What a native does with the code of the objects of triggers; the numbers are those of __wc_track. */
	private static final Map<String, Integer> CODE_OWNERSHIP = new java.util.HashMap<>();
	static {
		CODE_OWNERSHIP.put("Condition", 1);
		CODE_OWNERSHIP.put("Filter", 1);
		CODE_OWNERSHIP.put("TriggerAddAction", 2);
		CODE_OWNERSHIP.put("TriggerAddCondition", 3);
		CODE_OWNERSHIP.put("DestroyBoolExpr", 4);
		CODE_OWNERSHIP.put("DestroyCondition", 4);
		CODE_OWNERSHIP.put("DestroyFilter", 4);
		CODE_OWNERSHIP.put("TriggerRemoveAction", 5);
		CODE_OWNERSHIP.put("TriggerRemoveCondition", 5);
		CODE_OWNERSHIP.put("TriggerClearActions", 6);
		CODE_OWNERSHIP.put("TriggerClearConditions", 7);
		CODE_OWNERSHIP.put("DestroyTrigger", 8);
	}

	/** Natives that end an object the way the Destroy ones do. */
	private static final Set<String> REMOVING_NATIVES = new java.util.HashSet<>(
			java.util.Arrays.asList("RemoveUnit", "RemoveItem", "RemoveDestructable", "RemoveLocation", "RemoveRect",
					"RemoveRegion", "RemoveWeatherEffect"));

	/** True for a native that ends the object it takes first: the script holds it no longer. */
	private static boolean destroysFirstArgument(final String name, final List<JassParameter> parameters) {
		return !parameters.isEmpty() && (parameters.get(0).getType() instanceof HandleJassType)
				&& (name.startsWith("Destroy") || REMOVING_NATIVES.contains(name));
	}

	private static final String[] SLEEPING_NATIVES = { "TriggerSleepAction", "TriggerWaitForSound" };
	/** Natives that run their code at once and keep nothing of it. */
	private static final Set<String> CODE_ONLY_DURING_CALL = new java.util.HashSet<>(
			java.util.Arrays.asList("ForGroup", "ForForce", "EnumDestructablesInRect", "EnumItemsInRect"));
	/**
	 * The instructions one run of a script may take without a wait before it is stopped (a loop that never ends); 0 for
	 * no limit. The game's own Lua stops nothing that was tried (loops of 1e8 iterations, about 4e8 instructions, run
	 * to the end), so this is only the guard against a loop that never ends.
	 */
	private static final long OPERATION_LIMIT = Long.getLong("warsmash.lua.operations", 4_000_000_000L);

	/** Logs {@link #statistics()} once a minute of game time, to find what a long game keeps. */
	private static final boolean MEMORY_LOG = Boolean.getBoolean("warsmash.lua.memlog");

	private double nextMemoryLog = Double.MAX_VALUE;
	private final LuaState lua;
	private final GlobalScope globalScope;
	private final Host host;
	private final LuaHandles handles = new LuaHandles();
	/** How many natives are running, and what the game destroyed while they ran. */
	private int callDepth;
	private final List<Object> pendingReleases = new ArrayList<>();
	private final Deque<LuaContinuation> running = new ArrayDeque<>();
	private final List<String> nativeNames = new ArrayList<>();
	private final java.util.Set<String> missingReported = new java.util.HashSet<>();
	/** The continuations that have a coroutine, in the order they got it. */
	private final Set<LuaContinuation> live = new LinkedHashSet<>();

	public LuaEnvironment(final GlobalScope globalScope, final Host host) {
		this.globalScope = globalScope;
		this.host = host;
		this.lua = new LuaState();
		this.lua.setRandomSource(host.random());
		this.lua.setPrintHandler(host::print);
		updateClock();
		this.lua.setOperationLimit(OPERATION_LIMIT);
		this.lua.run("__wc = {codes = {}, ids = {}, count = 0, refs = {}, timers = {}, starts = 0}", "=wc");
		bindNatives();
		this.lua.register("ExecuteFunc", this::executeFunc);
		this.lua.register("__report", state -> {
			Log.error(state.getTop() > 0 ? state.display(1) : "script error");
			return 0;
		});
		this.lua.run(BOOTSTRAP, "=wc");
		this.lua.run(FOUR_CC, "=luahelper.lua");
		registerArithmetic();
		this.lua.register("__early", state -> {
			host.setEarlyHandleIds(state.toBoolean(1));
			return 0;
		});
		this.lua.register("__wc_variable", state -> {
			this.globalScope.scriptVariableChanged(state.toStringValue(1), state.toNumber(2), state.toNumber(3));
			return 0;
		});
		this.lua.register("__wc_tostring", state -> {
			// a handle prints as its type and a number, as the game's own handles do
			final HandleJassValue handle = this.handles.get(state.toLightUserdata(1));
			final String type = handle == null ? "userdata" : handle.getType().getName();
			state.pushString(type + ": " + String.format("%016X", 0x1FB8F8D0000L + (state.toLightUserdata(1) * 16)));
			return 1;
		});
		this.lua.pushLightUserdata(0);
		this.lua.setGlobal("__wc_lud");
		this.lua.run("debug.setmetatable(__wc_lud, {__tostring = __wc_tostring}) __wc_lud = nil", "=wc");
		exportGlobals();
		this.nextMemoryLog = 0;
		this.lua.run("__wc_freeze_perms()", "=wc");
		this.lua.run(HIDDEN_LIBRARIES, "=wc");
		host.watchReleases(this::gameReleased);
	}

	/**
	 * The game destroyed the object (a unit that decayed, a timer, an effect): the script no longer holds it.
	 *
	 * @return true if the script held it
	 */
	boolean released(final Object javaValue) {
		if (!this.handles.release(javaValue)) {
			return false;
		}
		this.host.forgetHandle(javaValue);
		return true;
	}

	/** The game's own notice that an object is destroyed; while a native runs, it waits until the native is done. */
	private void gameReleased(final Object javaValue) {
		if (this.callDepth > 0) {
			this.pendingReleases.add(javaValue);
		}
		else {
			released(javaValue);
		}
	}

	private void releasePending() {
		if (this.pendingReleases.isEmpty()) {
			return;
		}
		final List<Object> pending = new ArrayList<>(this.pendingReleases);
		this.pendingReleases.clear();
		for (final Object javaValue : pending) {
			released(javaValue);
		}
	}

	/**
	 * What the game's Lua does not have: no debug library and no collectgarbage. The engine keeps the traceback for its own
	 * error reports.
	 */
	private static final String HIDDEN_LIBRARIES = "__wc_traceback = debug.traceback debug = nil collectgarbage = nil";

	/**
	 * The reals of transpiled JASS are the original game's software floats, which the script's own arithmetic is not:
	 * these do the arithmetic the interpreter does. They come last in the registration order.
	 */
	private void registerArithmetic() {
		this.lua.register("__ra", state -> {
			state.pushNumber(JassNumbers.round(JassNumbers.add(state.toNumber(1), state.toNumber(2))));
			return 1;
		});
		this.lua.register("__rs", state -> {
			state.pushNumber(JassNumbers.round(JassNumbers.subtract(state.toNumber(1), state.toNumber(2))));
			return 1;
		});
		this.lua.register("__rm", state -> {
			state.pushNumber(JassNumbers.round(JassNumbers.multiply(state.toNumber(1), state.toNumber(2))));
			return 1;
		});
		this.lua.register("__rd", state -> {
			final double divisor = state.toNumber(2);
			if (divisor == 0) {
				throw new LuaNativeError("division by zero");
			}
			state.pushNumber(JassNumbers.round(JassNumbers.divide(state.toNumber(1), divisor)));
			return 1;
		});
		this.lua.register("__req", state -> {
			state.pushBoolean(JassNumbers.equal(state.toNumber(1), state.toNumber(2)));
			return 1;
		});
		this.lua.register("__i2r", state -> {
			state.pushNumber(JassNumbers.fromInt((int) state.toInteger(1)));
			return 1;
		});
	}

	LuaState lua() {
		return this.lua;
	}

	public GlobalScope getGlobalScope() {
		return this.globalScope;
	}

	LuaHandles handles() {
		return this.handles;
	}

	@Override
	public void close() {
		this.lua.close();
	}

	/** Advances the clock scripts see (os.clock) to the time of the game. */
	public void updateClock() {
		final double seconds = this.host.gameSeconds();
		this.lua.setClock(this.host.startTimeSeconds() + (long) seconds, seconds);
		if (MEMORY_LOG && (seconds >= this.nextMemoryLog)) {
			this.nextMemoryLog = seconds + 60;
			Log.info("Lua memory at " + (int) seconds + " s: " + statistics());
		}
	}

	/** What the script holds: its memory in kilobytes, the functions kept as code, the handles it has met. */
	public String statistics() {
		final long kilobytes = this.lua.getMemoryUsed() / 1024;
		this.lua.getGlobal("__wc_stats");
		this.lua.call(0, 1);
		final String counts = this.lua.toStringValue(-1);
		this.lua.pop(1);
		return kilobytes + " KB, " + counts + ", handles " + this.handles.liveSize() + " of "
				+ this.handles.size() + " (" + this.handles.heldByType() + ")";
	}

	// --- natives

	private void bindNatives() {
		// By id, which is the order common.j declares them in: the same on every run, which a saved game relies on.
		final Map<Integer, String> byId = new TreeMap<>();
		for (final Map.Entry<String, Integer> entry : this.globalScope.getNativeIds().entrySet()) {
			byId.put(entry.getValue(), entry.getKey());
		}
		for (final Map.Entry<Integer, String> entry : byId.entrySet()) {
			final NativeJassFunction function = this.globalScope.getNativeById(entry.getKey());
			if ((function == null) || "ExecuteFunc".equals(entry.getValue())) {
				continue;
			}
			if ((function.getImplementation() == null) && !this.host.isLobby()) {
				continue;
			}
			if (function.getImplementation() == null) {
				// A script's top level calls natives while the lobby reads it (Filter, Condition, ...), before the
				// game's natives exist; those calls do nothing.
				final String missing = entry.getValue();
				this.nativeNames.add(missing);
				this.lua.register(missing, state -> {
					if (this.missingReported.add(missing)) {
						Log.warn("Lua: native " + missing + " is not implemented; the call does nothing");
					}
					state.setTop(0);
					return 0;
				});
				continue;
			}
			this.nativeNames.add(entry.getValue());
			this.lua.register(entry.getValue(), new LuaNative(function));
		}
	}

	private final class LuaNative implements LuaFunction {
		private final NativeJassFunction function;
		private final String name;
		private final List<JassParameter> parameters;
		private final boolean sleeps;
		private final int[] codeParameters;
		private final boolean duringCall;
		private final boolean timerStart;
		private final int ownership;
		private final boolean destroys;
		private final boolean handleIdOf;

		LuaNative(final NativeJassFunction function) {
			this.function = function;
			this.name = function.getName();
			this.parameters = function.getParameters();
			boolean isSleeping = false;
			for (final String sleeping : SLEEPING_NATIVES) {
				isSleeping |= sleeping.equals(this.name);
			}
			this.sleeps = isSleeping;
			final List<Integer> codes = new ArrayList<>();
			for (int i = 0; i < this.parameters.size(); i++) {
				if (this.parameters.get(i).getType() == JassType.CODE) {
					codes.add(i);
				}
			}
			this.codeParameters = codes.stream().mapToInt(Integer::intValue).toArray();
			this.duringCall = CODE_ONLY_DURING_CALL.contains(this.name);
			this.timerStart = "TimerStart".equals(this.name);
			this.ownership = CODE_OWNERSHIP.getOrDefault(this.name, 0);
			this.destroys = destroysFirstArgument(this.name, this.parameters);
			this.handleIdOf = "GetHandleId".equals(this.name);
		}

		@Override
		public int call(final LuaState state) {
			final int count = this.parameters.size();
			final int given = state.getTop();
			if (this.handleIdOf && (given >= 1) && (state.type(1) == LuaState.TYPE_LIGHTUSERDATA)
					&& LuaEnvironment.this.handles.isReleased(state.toLightUserdata(1))) {
				// The object is gone, but a script still keys its tables by the id the object had.
				final int handleId = LuaEnvironment.this.handles.releasedHandleId(state.toLightUserdata(1));
				state.setTop(0);
				state.pushInteger(handleId);
				return 1;
			}
			final List<JassValue> arguments = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				arguments.add(toNativeArgument(this.name, this.parameters.get(i).getType(), i + 1, i < given));
			}
			final LuaContinuation active = LuaEnvironment.this.running.peek();
			if (this.sleeps && ((active == null) || !state.isRunning(active.getCoroutine()))) {
				throw new LuaNativeError("cannot wait in " + this.name + " outside the function the thread runs");
			}
			final List<LuaCodeJassValue> held = holdCode(arguments);
			final JassThread current = LuaEnvironment.this.globalScope.getCurrentThread();
			final TriggerExecutionScope scope = current == null ? TriggerExecutionScope.EMPTY : current.triggerScope;
			final JassValue result;
			// What the game destroys during the call is let go of after it, once the call has used the numbers.
			LuaEnvironment.this.callDepth++;
			try {
				try {
					result = this.function.call(arguments, LuaEnvironment.this.globalScope, scope);
				}
				finally {
					if (this.duringCall) {
						for (final LuaCodeJassValue code : held) {
							releaseCode(code.getId());
						}
					}
				}
				if (this.timerStart) {
					keepTimerCode(arguments, held);
				}
				else if ("DestroyTimer".equals(this.name)) {
					forgetTimerCode(arguments);
				}
				else if (this.ownership != 0) {
					trackCode(this.ownership, arguments, result, held);
				}
				if (this.destroys && isSet(arguments.get(0))) {
					released(((HandleJassValue) arguments.get(0)).getJavaValue());
				}
			}
			finally {
				if (--LuaEnvironment.this.callDepth == 0) {
					releasePending();
				}
			}
			state.setTop(0);
			final int pushed = pushJassValue(withoutNullObject(result)) ? 1 : 0;
			if (this.sleeps && (active != null) && (current != null) && (current == active.getThread())
					&& current.isSleeping()) {
				return state.yield(pushed);
			}
			return pushed;
		}

		/**
		 * The functions this call takes as code are held for the call; a native that keeps one (a timer, a trigger)
		 * holds it from here on, as nothing else releases it.
		 */
		private List<LuaCodeJassValue> holdCode(final List<JassValue> arguments) {
			if (this.codeParameters.length == 0) {
				return Collections.emptyList();
			}
			final List<LuaCodeJassValue> held = new ArrayList<>(this.codeParameters.length);
			for (final int index : this.codeParameters) {
				if (arguments.get(index) instanceof LuaCodeJassValue) {
					final LuaCodeJassValue code = (LuaCodeJassValue) arguments.get(index);
					retainCode(code.getId());
					held.add(code);
				}
			}
			return held;
		}
	}

	/**
	 * The natives that make an object of the code a trigger holds give one even when the trigger or the code is missing:
	 * a script gets a handle of its own, never nil, from Condition, Filter, TriggerAddAction and TriggerAddCondition.
	 */
	private static JassValue withoutNullObject(final JassValue result) {
		if ((result instanceof HandleJassValue) && (((HandleJassValue) result).getJavaValue() == null)) {
			final HandleJassValue handle = (HandleJassValue) result;
			switch (handle.getType().getName()) {
			case "conditionfunc":
			case "filterfunc":
			case "triggeraction":
			case "triggercondition":
				return new HandleJassValue((HandleJassType) handle.getType(), new Object());
			default:
				break;
			}
		}
		return result;
	}

	/** Tells the script which object holds the code of a call, or which holds are let go. */
	private void trackCode(final int kind, final List<JassValue> arguments, final JassValue result,
			final List<LuaCodeJassValue> held) {
		this.lua.getGlobal("__wc_track");
		this.lua.pushInteger(kind);
		for (int i = 0; i < 2; i++) {
			if ((i < arguments.size()) && isSet(arguments.get(i))) {
				this.lua.pushInteger(this.handles.idOf((HandleJassValue) arguments.get(i)));
			}
			else {
				this.lua.pushNil();
			}
		}
		if (isSet(result)) {
			this.lua.pushInteger(this.handles.idOf((HandleJassValue) result));
		}
		else {
			this.lua.pushNil();
		}
		if (held.isEmpty()) {
			this.lua.pushNil();
		}
		else {
			this.lua.pushInteger(held.get(0).getId());
		}
		this.lua.call(5, 0);
	}

	private void retainCode(final int id) {
		this.lua.getGlobal("__wc_retain");
		this.lua.pushInteger(id);
		this.lua.call(1, 0);
	}

	/** A thread that runs the function holds it until the thread ends. */
	void holdFunction(final int id) {
		retainCode(id);
	}

	void releaseCode(final int id) {
		this.lua.getGlobal("__wc_release");
		this.lua.pushInteger(id);
		this.lua.call(1, 0);
	}

	private static boolean isSet(final JassValue value) {
		return (value instanceof HandleJassValue) && (((HandleJassValue) value).getJavaValue() != null);
	}

	/** A timer holds the code it was started with until it is started again, destroyed, or has run out. */
	private void keepTimerCode(final List<JassValue> arguments, final List<LuaCodeJassValue> held) {
		if (!isSet(arguments.get(0))) {
			for (final LuaCodeJassValue code : held) {
				releaseCode(code.getId());
			}
			return;
		}
		this.lua.getGlobal("__wc_timer");
		this.lua.pushInteger(this.handles.idOf((HandleJassValue) arguments.get(0)));
		if (held.isEmpty()) {
			this.lua.pushNil();
		}
		else {
			this.lua.pushInteger(held.get(0).getId());
		}
		this.lua.call(2, 1);
		final boolean sweep = this.lua.toBoolean(-1);
		this.lua.pop(1);
		if (sweep) {
			sweepTimers();
		}
	}

	private void forgetTimerCode(final List<JassValue> arguments) {
		if (isSet(arguments.get(0))) {
			this.lua.getGlobal("__wc_timer_drop");
			this.lua.pushInteger(this.handles.idOf((HandleJassValue) arguments.get(0)));
			this.lua.call(1, 0);
		}
	}

	/** Lets go of the code of the timers that have run out; they cannot start it again. */
	private void sweepTimers() {
		this.lua.getGlobal("__wc_timer_keys");
		this.lua.call(0, 1);
		final int keys = this.lua.getTop();
		final int count = (int) this.lua.rawLength(keys);
		final List<Long> expired = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			this.lua.getIndex(keys, i);
			final long number = this.lua.toInteger(-1);
			this.lua.pop(1);
			final HandleJassValue handle = this.handles.get(number);
			if ((handle != null) && (handle.getJavaValue() instanceof CTimer)) {
				final CTimer timer = (CTimer) handle.getJavaValue();
				if (!timer.isRunning() && !timer.isPaused() && !timer.isRepeats()) {
					expired.add(number);
				}
			}
		}
		this.lua.pop(1);
		for (final long number : expired) {
			this.lua.getGlobal("__wc_timer_drop");
			this.lua.pushInteger(number);
			this.lua.call(1, 0);
		}
	}

	private JassValue toNativeArgument(final String nativeName, final JassType type, final int index,
			final boolean given) {
		final int luaType = given ? this.lua.type(index) : LuaState.TYPE_NIL;
		final boolean numeric = given && ((luaType == LuaState.TYPE_NUMBER)
				|| ((luaType == LuaState.TYPE_STRING) && this.lua.isNumber(index)));
		if (type == JassType.INTEGER) {
			if (numeric) {
				if ((luaType == LuaState.TYPE_NUMBER) && this.lua.isInteger(index)) {
					return IntegerJassValue.of((int) this.lua.toInteger(index));
				}
				final double number = this.lua.toNumber(index);
				if ((number != Math.floor(number)) || Double.isInfinite(number)) {
					throw new LuaNativeError("bad argument #" + index + " to '" + nativeName
							+ "' (number has no integer representation)");
				}
				return IntegerJassValue.of((int) (long) number);
			}
			throw badArgument(nativeName, index, "number", luaType, given);
		}
		if (type == JassType.REAL) {
			if (numeric) {
				return RealJassValue.of(this.lua.toNumber(index));
			}
			throw badArgument(nativeName, index, "number", luaType, given);
		}
		if (type == JassType.STRING) {
			if ((luaType == LuaState.TYPE_STRING) || (luaType == LuaState.TYPE_NUMBER)) {
				return StringJassValue.of(fromLuaBytes(this.lua.toBytes(index)));
			}
			throw badArgument(nativeName, index, "string", luaType, given);
		}
		if (type == JassType.BOOLEAN) {
			return BooleanJassValue.of(this.lua.toBoolean(index));
		}
		if (type == JassType.CODE) {
			if (luaType == LuaState.TYPE_FUNCTION) {
				return new LuaCodeJassValue(this, codeFor(index));
			}
			if (luaType == LuaState.TYPE_NIL) {
				return null;
			}
			throw badArgument(nativeName, index, "function", luaType, given);
		}
		if (type instanceof HandleJassType) {
			final HandleJassType handleType = (HandleJassType) type;
			if (luaType == LuaState.TYPE_NIL) {
				return handleType.getNullValue();
			}
			if (luaType == LuaState.TYPE_LIGHTUSERDATA) {
				final long number = this.lua.toLightUserdata(index);
				final HandleJassValue handle = this.handles.as(number, handleType);
				if (handle != null) {
					return handle;
				}
				if (this.handles.isReleased(number)) {
					return handleType.getNullValue();
				}
			}
			// the game's own message names the handle type and nothing else
			throw new LuaNativeError("bad argument #" + index + " to '" + nativeName + "' (" + handleType.getName()
					+ " expected)");
		}
		return null;
	}

	private static String fromLuaBytes(final byte[] bytes) {
		return JassStrings.fromBytes(bytes, 0, bytes.length);
	}

	private static LuaNativeError badArgument(final String nativeName, final int index, final String expected,
			final int luaType, final boolean given) {
		final String[] names = { "nil", "boolean", "userdata", "number", "string", "table", "function", "userdata",
				"thread" };
		return new LuaNativeError("bad argument #" + index + " to '" + nativeName + "' (" + expected + " expected, got "
				+ (given && (luaType >= 0) && (luaType < names.length) ? names[luaType] : "no value") + ")");
	}

	/**
	 * Pushes the value for the script.
	 *
	 * @return false if there was no value to push (a native with no result)
	 */
	boolean pushJassValue(final JassValue value) {
		if (value == null) {
			return false;
		}
		if (value instanceof IntegerJassValue) {
			this.lua.pushInteger(((IntegerJassValue) value).getValue());
		}
		else if (value instanceof RealJassValue) {
			this.lua.pushNumber(((RealJassValue) value).getValue());
		}
		else if (value instanceof BooleanJassValue) {
			this.lua.pushBoolean(((BooleanJassValue) value).getValue());
		}
		else if (value instanceof StringJassValue) {
			final String string = ((StringJassValue) value).getValue();
			if (string == null) {
				// the game's Lua gives an empty text where a native has no string
				this.lua.pushBytes(new byte[0]);
			}
			else {
				this.lua.pushBytes(JassStrings.toBytes(string));
			}
		}
		else if (value instanceof HandleJassValue) {
			final HandleJassValue handle = (HandleJassValue) value;
			if (handle.getJavaValue() == null) {
				this.lua.pushNil();
			}
			else {
				this.lua.pushLightUserdata(this.handles.idOf(handle));
			}
		}
		else if (value instanceof LuaCodeJassValue) {
			pushCode(((LuaCodeJassValue) value).getId());
		}
		else {
			this.lua.pushNil();
		}
		return true;
	}

	/** The value at the stack index as a JASS value, by what it is. */
	JassValue toJassValue(final int index) {
		switch (this.lua.type(index)) {
		case LuaState.TYPE_BOOLEAN:
			return BooleanJassValue.of(this.lua.toBoolean(index));
		case LuaState.TYPE_NUMBER:
			return this.lua.isInteger(index) ? IntegerJassValue.of((int) this.lua.toInteger(index))
					: RealJassValue.of(this.lua.toNumber(index));
		case LuaState.TYPE_STRING:
			return StringJassValue.of(fromLuaBytes(this.lua.toBytes(index)));
		case LuaState.TYPE_LIGHTUSERDATA:
			return this.handles.valueOf(this.lua.toLightUserdata(index));
		case LuaState.TYPE_FUNCTION:
			return new LuaCodeJassValue(this, codeFor(index));
		default:
			return BooleanJassValue.FALSE;
		}
	}

	private int executeFunc(final LuaState state) {
		if ((state.getTop() < 1) || (state.type(1) != LuaState.TYPE_STRING)) {
			throw new LuaNativeError("bad argument #1 to 'ExecuteFunc' (string expected)");
		}
		final String name = state.toStringValue(1);
		Log.info("ExecuteFunc (\"" + name + "\")");
		final LuaCodeJassValue code = getGlobalFunction(name);
		if (code != null) {
			final JassThread current = this.globalScope.getCurrentThread();
			this.globalScope.runThreadUntilSleep(this.globalScope.createThread(code,
					current == null ? TriggerExecutionScope.EMPTY : current.triggerScope));
		}
		return 0;
	}

	/** The globals common.j defines (constants such as UNIT_TYPE_HERO) as globals of the script. */
	private void exportGlobals() {
		final Map<Integer, String> byId = new TreeMap<>();
		for (final Map.Entry<String, Integer> entry : this.globalScope.getGlobalIds().entrySet()) {
			byId.put(entry.getValue(), entry.getKey());
		}
		for (final Map.Entry<Integer, String> entry : byId.entrySet()) {
			final JassValue value = this.globalScope.getGlobalById(entry.getKey());
			if ((value == null) || (value instanceof ArrayJassValue) || (value instanceof CodeJassValue)) {
				continue;
			}
			if (pushJassValue(value)) {
				this.lua.setGlobal(entry.getValue());
			}
		}
	}

	// --- Lua functions as code

	/** The number of the function at the stack index; the same function always gets the same number. */
	int codeFor(final int functionIndex) {
		final int absolute = functionIndex < 0 ? this.lua.getTop() + functionIndex + 1 : functionIndex;
		this.lua.getGlobal("__wc");
		this.lua.getField(-1, "ids");
		this.lua.pushValue(absolute);
		this.lua.getTable(-2);
		if (this.lua.type(-1) == LuaState.TYPE_NUMBER) {
			final int id = (int) this.lua.toInteger(-1);
			this.lua.pop(3);
			return id;
		}
		this.lua.pop(1);
		this.lua.getField(-2, "count");
		final int id = (int) this.lua.toInteger(-1) + 1;
		this.lua.pop(1);
		this.lua.pushInteger(id);
		this.lua.setField(-3, "count");
		this.lua.pushValue(absolute);
		this.lua.pushInteger(id);
		this.lua.setTable(-3);
		this.lua.getField(-2, "codes");
		this.lua.pushValue(absolute);
		this.lua.setIndex(-2, id);
		this.lua.pop(3);
		return id;
	}

	/** Pushes the function with the number. */
	void pushCode(final int id) {
		this.lua.getGlobal("__wc");
		this.lua.getField(-1, "codes");
		this.lua.getIndex(-1, id);
		this.lua.remove(-2);
		this.lua.remove(-2);
	}

	// --- running scripts

	/**
	 * Compiles a chunk as a function that can be run as code.
	 *
	 * @throws com.etheller.warsmash.lua.LuaSyntaxException if the text does not compile
	 */
	public LuaCodeJassValue loadChunk(final byte[] source, final String chunkName) {
		this.lua.load(source, "=" + chunkName);
		final int id = codeFor(-1);
		this.lua.pop(1);
		return new LuaCodeJassValue(this, id);
	}

	/** Compiles the text and keeps the function as the global of this name. */
	public void defineChunk(final String globalName, final byte[] source, final String chunkName) {
		this.lua.load(source, "=" + chunkName);
		this.lua.setGlobal(globalName);
	}

	/** The global function of the script with this name, or null. */
	public LuaCodeJassValue getGlobalFunction(final String name) {
		this.lua.getGlobal(name);
		try {
			if (this.lua.type(-1) != LuaState.TYPE_FUNCTION) {
				return null;
			}
			return new LuaCodeJassValue(this, codeFor(-1));
		}
		finally {
			this.lua.pop(1);
		}
	}

	/**
	 * Runs the function on a thread now, until it ends or waits; a wait stops it for good, as it does in the map's
	 * main function of JASS.
	 */
	public void runNow(final LuaCodeJassValue code) {
		final JassThread thread = this.globalScope.createThread(code, TriggerExecutionScope.EMPTY);
		thread.setSleepEndsThread(true);
		this.globalScope.runThreadUntilCompletion(thread);
	}

	/** Starts the function on a thread of the queue. */
	public void queue(final LuaCodeJassValue code, final boolean sleepEndsThread) {
		final JassThread thread = this.globalScope.createThread(code, TriggerExecutionScope.EMPTY);
		thread.setSleepEndsThread(sleepEndsThread);
		this.globalScope.queueThread(thread);
	}

	void started(final LuaContinuation continuation) {
		this.live.add(continuation);
	}

	void finished(final LuaContinuation continuation) {
		this.live.remove(continuation);
	}

	/** What a saved game keeps of the script: the Lua state and the engine objects the script holds. */
	public static final class Snapshot {
		byte[] state;
		List<HandleJassValue> handles;
		/** The handle ids the destroyed objects of the numbers had; null in saves from before they were let go. */
		int[] releasedHandleIds;
	}

	/**
	 * The script's state for a save: the globals, everything they lead to, the coroutines of the threads that wait. The
	 * continuations of the threads get the place of their coroutine in it. Call it between steps, not from the script.
	 */
	public Snapshot capture() {
		final int base = this.lua.getTop();
		final List<LuaContinuation> waiting = new ArrayList<>(this.live);
		this.lua.getGlobal("__wc_capture");
		this.lua.newTable();
		for (int i = 0; i < waiting.size(); i++) {
			this.lua.pushCoroutine(waiting.get(i).getCoroutine());
			this.lua.setIndex(-2, i + 1);
			waiting.get(i).setSaveIndex(i + 1);
		}
		this.lua.call(1, 1);
		final int root = this.lua.getTop();
		final int permanents = pushPermanents();
		final Snapshot snapshot = new Snapshot();
		snapshot.state = this.lua.persist(root, permanents);
		snapshot.handles = this.handles.snapshot();
		snapshot.releasedHandleIds = this.handles.snapshotReleasedIds();
		this.lua.setTop(base);
		return snapshot;
	}

	/** The table that keeps the globals table itself out of the bytes: the loading side puts its own there. */
	private int pushPermanents() {
		this.lua.newTable();
		this.lua.getGlobal("_G");
		this.lua.pushString("wc.globals");
		this.lua.setTable(-3);
		this.lua.getGlobal("__wc_fill_perms");
		this.lua.pushValue(-2);
		this.lua.call(1, 0);
		return this.lua.getTop();
	}

	/**
	 * Puts the state of a save in place of the script's: the globals take the saved values, and each continuation that
	 * the save held gets its coroutine back.
	 *
	 * @param continuations the continuations of the threads the save held
	 */
	public void restore(final Snapshot snapshot, final Collection<LuaContinuation> continuations) {
		final int base = this.lua.getTop();
		this.live.clear();
		final int permanents = pushPermanents();
		this.lua.unpersist(snapshot.state, permanents);
		final int root = this.lua.getTop();
		this.lua.getGlobal("__wc_restore");
		this.lua.pushValue(root);
		this.lua.call(1, 1);
		final int threads = this.lua.getTop();
		final Map<Integer, LuaCoroutine> coroutines = new java.util.HashMap<>();
		final int count = (int) this.lua.rawLength(threads);
		for (int i = 1; i <= count; i++) {
			this.lua.getIndex(threads, i);
			coroutines.put(i, this.lua.toCoroutine(-1));
			this.lua.pop(1);
		}
		for (final LuaContinuation continuation : continuations) {
			final LuaCoroutine coroutine = coroutines.get(continuation.getSaveIndex());
			if (coroutine != null) {
				continuation.attach(coroutine);
			}
		}
		this.handles.restore(snapshot.handles, snapshot.releasedHandleIds);
		this.lua.setTop(base);
	}

	void enter(final LuaContinuation continuation) {
		this.running.push(continuation);
	}

	void leave(final LuaContinuation continuation) {
		this.running.pop();
	}

	/** The names of the natives the script has, in the order they were registered. */
	public List<String> getNativeNames() {
		return Collections.unmodifiableList(this.nativeNames);
	}

	/** Runs Lua text as a chunk on the caller's stack; for tests and tools. */
	public void run(final String code, final String chunkName) {
		this.lua.run(code, chunkName);
	}

	public LuaState getLuaState() {
		return this.lua;
	}
}
