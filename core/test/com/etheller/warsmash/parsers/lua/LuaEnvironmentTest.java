package com.etheller.warsmash.parsers.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.execution.JassThread;
import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.TriggerExecutionScope;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.CodeJassValue;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.CodeJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.lua.LuaRandomSource;
import com.etheller.warsmash.lua.LuaState;

import net.warsmash.parsers.jass.SmashJassParser;

/**
 * The same scenario written in JASS and in Lua, run on the engine's thread queue with natives that log what they do:
 * the logs must be equal, which shows the natives, code values and waits of the Lua bridge behave as JASS.
 */
class LuaEnvironmentTest {
	private static final String COMMON = String.join("\n",
			"type thing extends handle",
			"native Add takes integer a, integer b returns integer",
			"native Half takes real r returns real",
			"native Echo takes string s returns string",
			"native Flip takes boolean b returns boolean",
			"native MakeThing takes integer id returns thing",
			"native ThingId takes thing t returns integer",
			"native Log takes string s returns nothing",
			"native LogInt takes string s, integer i returns nothing",
			"native LogReal takes string s, real r returns nothing",
			"native Later takes code c returns nothing",
			"native RunSync takes code c returns boolean",
			"native ForGroup takes code c returns nothing",
			"native TriggerSleepAction takes real seconds returns nothing",
			"globals",
			"    constant integer THE_ANSWER = 42",
			"    constant thing THE_THING = null",
			"endglobals",
			"");

	private static final String JASS_SCENARIO = String.join("\n",
			"function Cond takes nothing returns boolean",
			"    call Log(\"cond\")",
			"    return true",
			"endfunction",
			"function Worker takes nothing returns nothing",
			"    call LogInt(\"w1\", Add(2, 3))",
			"    call TriggerSleepAction(1.0)",
			"    call Log(\"w2\")",
			"    call TriggerSleepAction(0.5)",
			"    call LogReal(\"w3\", Half(5.0))",
			"endfunction",
			"function Other takes nothing returns nothing",
			"    call Log(Echo(\"other\"))",
			"    call TriggerSleepAction(0.5)",
			"    call Log(\"other done\")",
			"endfunction",
			"function Main takes nothing returns nothing",
			"    call Log(\"main\")",
			"    call Later(function Worker)",
			"    call Later(function Other)",
			"    call LogInt(\"sync\", 0)",
			"    if RunSync(function Cond) then",
			"        call Log(\"sync true\")",
			"    endif",
			"    if Flip(false) then",
			"        call Log(\"flipped\")",
			"    endif",
			"    call LogInt(\"answer\", THE_ANSWER)",
			"    call Log(\"main done\")",
			"endfunction",
			"");

	private static final String LUA_SCENARIO = String.join("\n",
			"function Cond()",
			"  Log('cond')",
			"  return true",
			"end",
			"function Worker()",
			"  LogInt('w1', Add(2, 3))",
			"  TriggerSleepAction(1.0)",
			"  Log('w2')",
			"  TriggerSleepAction(0.5)",
			"  LogReal('w3', Half(5.0))",
			"end",
			"function Other()",
			"  Log(Echo('other'))",
			"  TriggerSleepAction(0.5)",
			"  Log('other done')",
			"end",
			"function Main()",
			"  Log('main')",
			"  Later(Worker)",
			"  Later(Other)",
			"  LogInt('sync', 0)",
			"  if RunSync(Cond) then",
			"    Log('sync true')",
			"  end",
			"  if Flip(false) then",
			"    Log('flipped')",
			"  end",
			"  LogInt('answer', THE_ANSWER)",
			"  Log('main done')",
			"end",
			"");

	/** The natives of the scenario with the log and the waiting threads they keep. */
	private static final class Fixture {
		final JassProgram program = new JassProgram();
		final List<String> log = new ArrayList<>();
		final List<JassThread> sleepers = new ArrayList<>();
		final Map<Integer, Object> things = new HashMap<>();
		final HandleJassType thingType;

		Fixture(final String scenario) throws Exception {
			final GlobalScope globals = this.program.getGlobalScope();
			this.thingType = globals.registerHandleType("thing");
			final var natives = this.program.getJassNativeManager();
			natives.createNative("Add", (arguments, scope, trigger) -> IntegerJassValue
					.of(arguments.get(0).visit(IntegerJassValueVisitor.getInstance())
							+ arguments.get(1).visit(IntegerJassValueVisitor.getInstance())));
			natives.createNative("Half", (arguments, scope, trigger) -> RealJassValue
					.of(arguments.get(0).visit(RealJassValueVisitor.getInstance()) / 2));
			natives.createNative("Echo", (arguments, scope, trigger) -> arguments.get(0));
			natives.createNative("Flip", (arguments, scope, trigger) -> BooleanJassValue
					.inverse((BooleanJassValue) arguments.get(0)));
			natives.createNative("MakeThing", (arguments, scope, trigger) -> new HandleJassValue(this.thingType,
					this.things.computeIfAbsent(arguments.get(0).visit(IntegerJassValueVisitor.getInstance()),
							id -> new Object[] { id })));
			natives.createNative("ThingId", (arguments, scope, trigger) -> IntegerJassValue
					.of((Integer) ((Object[]) ((HandleJassValue) arguments.get(0)).getJavaValue())[0]));
			natives.createNative("Log", (arguments, scope, trigger) -> {
				this.log.add(arguments.get(0).visit(StringJassValueVisitor.getInstance()));
				return null;
			});
			natives.createNative("LogInt", (arguments, scope, trigger) -> {
				this.log.add(arguments.get(0).visit(StringJassValueVisitor.getInstance()) + " "
						+ arguments.get(1).visit(IntegerJassValueVisitor.getInstance()));
				return null;
			});
			natives.createNative("LogReal", (arguments, scope, trigger) -> {
				this.log.add(arguments.get(0).visit(StringJassValueVisitor.getInstance()) + " "
						+ arguments.get(1).visit(RealJassValueVisitor.getInstance()));
				return null;
			});
			natives.createNative("Later", (arguments, scope, trigger) -> {
				arguments.get(0).visit(CodeJassValueVisitor.getInstance()).call(scope, trigger);
				return null;
			});
			natives.createNative("RunSync", (arguments, scope, trigger) -> {
				final CodeJassValue code = arguments.get(0).visit(CodeJassValueVisitor.getInstance());
				return code.callAndExecuteCapturingReturnValue(scope, trigger, "RunSync", BooleanJassValue.FALSE);
			});
			natives.createNative("ForGroup", (arguments, scope, trigger) -> {
				arguments.get(0).visit(CodeJassValueVisitor.getInstance()).callAndExecuteCapturingReturnValue(scope,
						trigger, "ForGroup", BooleanJassValue.FALSE);
				return null;
			});
			natives.createNative("TriggerSleepAction", (arguments, scope, trigger) -> {
				final JassThread thread = scope.getCurrentThread();
				thread.setSleeping(true);
				this.sleepers.add(thread);
				return null;
			});
			new SmashJassParser(new StringReader(COMMON)).scanAndParse("common.j", this.program);
			if (scenario != null) {
				new SmashJassParser(new StringReader(scenario)).scanAndParse("scenario.j", this.program);
			}
			this.program.initialize();
		}

		/** Runs the queue until every thread ended, waking each sleeper after every round. */
		void runToEnd() {
			final GlobalScope scope = this.program.getGlobalScope();
			for (int round = 0; round < 100; round++) {
				if (scope.runThreads()) {
					return;
				}
				for (final JassThread sleeper : new ArrayList<>(this.sleepers)) {
					sleeper.setSleeping(false);
				}
				this.sleepers.clear();
			}
			throw new AssertionError("the threads did not end");
		}
	}

	private static LuaEnvironment.Host host() {
		return host(0);
	}

	private static LuaEnvironment.Host host(final double seconds) {
		return new LuaEnvironment.Host() {
			@Override
			public LuaRandomSource random() {
				return () -> 4;
			}

			@Override
			public void print(final String text) {
			}

			@Override
			public double gameSeconds() {
				return seconds;
			}

			@Override
			public long startTimeSeconds() {
				return 1_000_000;
			}
		};
	}

	private static List<String> runJass() throws Exception {
		final Fixture fixture = new Fixture(JASS_SCENARIO);
		final GlobalScope scope = fixture.program.getGlobalScope();
		scope.queueThread(scope.createThread("Main", Collections.emptyList(), TriggerExecutionScope.EMPTY));
		fixture.runToEnd();
		return fixture.log;
	}

	private static List<String> runLua() throws Exception {
		final Fixture fixture = new Fixture(null);
		try (LuaEnvironment lua = new LuaEnvironment(fixture.program.getGlobalScope(), host())) {
			lua.runNow(lua.loadChunk(LUA_SCENARIO.getBytes(StandardCharsets.UTF_8), "scenario.lua"));
			final LuaCodeJassValue main = lua.getGlobalFunction("Main");
			assertNotNull(main);
			lua.queue(main, false);
			fixture.runToEnd();
			return fixture.log;
		}
	}

	/** The real globals of a generated script are set in the globals table, whose changes are what variable events see. */
	@Test
	void changesOfTheGlobalsTableFireVariableEvents() throws Exception {
		final Fixture fixture = new Fixture(null);
		final GlobalScope scope = fixture.program.getGlobalScope();
		final com.etheller.interpreter.ast.scope.trigger.Trigger trigger = new com.etheller.interpreter.ast.scope.trigger.Trigger();
		trigger.addAction((arguments, globalScope, triggerScope) -> {
			fixture.log.add("fired");
			return null;
		});
		scope.registerVariableEvent(trigger, "udg_x",
				com.etheller.interpreter.ast.scope.variableevent.CLimitOp.GREATER_THAN_OR_EQUAL, 1.0);
		try (LuaEnvironment lua = new LuaEnvironment(scope, host())) {
			lua.runNow(lua.loadChunk(("globals(function(_ENV) udg_x = 0.0 end)\n"
					+ "globals.udg_x = 2.0 globals.udg_x = 3.0 globals.udg_x = 0.5 globals.udg_x = 1.5\n"
					+ "udg_y = 5.0 globals.udg_y = 1.0 assert(udg_x == nil and globals.udg_x == 1.5)")
							.getBytes(StandardCharsets.UTF_8), "globals.lua"));
			fixture.runToEnd();
		}
		assertEquals(Arrays.asList("fired", "fired"), fixture.log);
	}

	@Test
	void theLuaScenarioLogsWhatTheJassOneLogs() throws Exception {
		final List<String> jass = runJass();
		assertEquals(Arrays.asList("main", "sync 0", "cond", "sync true", "flipped", "answer 42", "main done", "w1 5",
				"other", "w2", "other done", "w3 2.5"), jass);
		assertEquals(jass, runLua());
	}

	/**
	 * Natives that the engine registers through helpers (names built from parts, one lambda for many names, converters
	 * with no code of their own) are the same registry the JASS interpreter calls: every one declared with code is a
	 * Lua global, and the ones declared without code are not.
	 */
	@Test
	void everyNativeJassCanCallIsAGlobalOfTheScript() throws Exception {
		final JassProgram program = new JassProgram();
		final var natives = program.getJassNativeManager();
		final StringBuilder common = new StringBuilder("type thing extends handle\n");
		for (final String flag : new String[] { "Completed", "Failed", "Discovered" }) {
			natives.createNative("QuestSet" + flag, (arguments, scope, trigger) -> null);
			common.append("native QuestSet").append(flag).append(" takes integer q, boolean b returns nothing\n");
		}
		for (final String suffix : new String[] { "Integer", "Real" }) {
			for (final String prefix : new String[] { "BlzGet", "BlzSet" }) {
				natives.createNative(prefix + "AbilityLevel" + suffix, (arguments, scope, trigger) -> null);
				common.append("native ").append(prefix).append("AbilityLevel").append(suffix)
						.append(" takes integer a returns nothing\n");
			}
		}
		natives.createNative("NotDeclared", (arguments, scope, trigger) -> null);
		common.append("native ConvertThing takes integer i returns thing\n");
		common.append("native NotImplemented takes integer i returns nothing\n");
		new SmashJassParser(new StringReader(common.toString())).scanAndParse("common.j", program);
		program.initialize();

		final GlobalScope scope = program.getGlobalScope();
		final java.util.Set<String> callable = new java.util.TreeSet<>();
		for (final String name : scope.getNativeIds().keySet()) {
			if (scope.getNativeById(scope.getNativeId(name)).getImplementation() != null) {
				callable.add(name);
			}
		}
		assertTrue(callable.contains("ConvertThing"));
		assertTrue(callable.contains("QuestSetFailed"));
		try (LuaEnvironment lua = new LuaEnvironment(scope, host())) {
			final java.util.Set<String> globals = new java.util.TreeSet<>();
			for (final String name : scope.getNativeIds().keySet()) {
				lua.lua().getGlobal(name);
				if (lua.lua().type(-1) == LuaState.TYPE_FUNCTION) {
					globals.add(name);
				}
				lua.lua().pop(1);
			}
			assertEquals(callable, globals);
			assertEquals(new java.util.TreeSet<>(callable), new java.util.TreeSet<>(lua.getNativeNames()));
		}
	}

	@Test
	void handlesAreOneValuePerObjectAndWorkAsKeys() throws Exception {
		final Fixture fixture = new Fixture(null);
		try (LuaEnvironment lua = new LuaEnvironment(fixture.program.getGlobalScope(), host())) {
			lua.runNow(lua.loadChunk(String.join("\n",
					"local a, b, c = MakeThing(7), MakeThing(7), MakeThing(8)",
					"Log(tostring(a == b) .. tostring(a == c))",
					"local t = {}",
					"t[a] = 'first'",
					"t[c] = 'second'",
					"Log(t[b] .. t[c])",
					"LogInt('id', ThingId(c))",
					"Log(type(a) .. tostring(THE_THING))",
					"local ok, err = pcall(ThingId, 5)",
					"Log(tostring(ok) .. ' ' .. tostring(err):match('bad argument #1 to .ThingId. %(thing expected%)'))")
					.getBytes(StandardCharsets.UTF_8), "handles"));
			assertEquals(Arrays.asList("truefalse", "firstsecond", "id 8", "userdatanil",
					"false bad argument #1 to 'ThingId' (thing expected)"), fixture.log);
		}
	}

	@Test
	void conversionsFollowTheDeclaredTypes() throws Exception {
		final Fixture fixture = new Fixture(null);
		try (LuaEnvironment lua = new LuaEnvironment(fixture.program.getGlobalScope(), host())) {
			lua.runNow(lua.loadChunk(String.join("\n",
					"LogInt('int from float', Add(2.0, 0))",
					"LogReal('real from int', Half(3))",
					"Log(pcall(Echo, nil) and 'bad' or 'nil echo')",
					"Log(Echo('utf ä'))",
					"Log(tostring(Flip(nil)))",
					"Log(tostring(FourCC('hfoo')))",
					"Log(tostring(pcall(Add, 'x', 1)))",
					"Log(tostring(pcall(Later, 5)))")
					.getBytes(StandardCharsets.UTF_8), "conversions"));
			assertEquals(Arrays.asList("int from float 2", "real from int 1.5", "nil echo", "utf ä", "true",
					String.valueOf(0x68666f6f), "false", "false"), fixture.log);
		}
	}

	@Test
	void aWaitInAScriptCoroutineIsAnError() throws Exception {
		final Fixture fixture = new Fixture(null);
		try (LuaEnvironment lua = new LuaEnvironment(fixture.program.getGlobalScope(), host())) {
			lua.runNow(lua.loadChunk(String.join("\n",
					"local co = coroutine.create(function() TriggerSleepAction(1) end)",
					"local ok, err = coroutine.resume(co)",
					"Log(tostring(ok) .. ' ' .. tostring(err):gsub('^.-cannot', 'cannot'))")
					.getBytes(StandardCharsets.UTF_8), "co"));
			assertEquals(1, fixture.log.size());
			assertTrue(fixture.log.get(0).startsWith("false cannot wait in TriggerSleepAction"), fixture.log.get(0));
			assertTrue(fixture.sleepers.isEmpty());
		}
	}

	@Test
	void theSameFunctionGivesTheSameCodeKey() throws Exception {
		final Fixture fixture = new Fixture(null);
		try (LuaEnvironment lua = new LuaEnvironment(fixture.program.getGlobalScope(), host())) {
			lua.runNow(lua.loadChunk("function F() end function G() end".getBytes(StandardCharsets.UTF_8), "f"));
			final LuaCodeJassValue first = lua.getGlobalFunction("F");
			final LuaCodeJassValue again = lua.getGlobalFunction("F");
			final LuaCodeJassValue other = lua.getGlobalFunction("G");
			assertEquals(first.getBoolExprKey(), again.getBoolExprKey());
			assertTrue(!first.getBoolExprKey().equals(other.getBoolExprKey()));
			assertEquals(first, again);
		}
	}

	@Test
	void functionsOfCallsThatKeepNothingAreFreedAndThoseOfRunningThreadsStayUntilTheyEnd() throws Exception {
		final Fixture fixture = new Fixture(null);
		try (LuaEnvironment lua = new LuaEnvironment(fixture.program.getGlobalScope(), host())) {
			lua.runNow(lua.loadChunk(String.join("\n",
					"function Count() local n = 0 for _ in pairs(__wc.codes) do n = n + 1 end return n end",
					"function Later1() TriggerSleepAction(1.0) Log('later done') end")
					.getBytes(StandardCharsets.UTF_8), "defs"));
			lua.runNow(lua.loadChunk(String.join("\n",
					"local before = Count()",
					"for i = 1, 50 do ForGroup(function() Log('x') end) end",
					"LogInt('growth', Count() - before)",
					"Later(Later1)",
					"LogInt('with thread', Count() - before)")
					.getBytes(StandardCharsets.UTF_8), "sync"));
			fixture.runToEnd();
			lua.runNow(lua.loadChunk("LogInt('after', Count())".getBytes(StandardCharsets.UTF_8), "after"));
			final List<String> counts = new ArrayList<>();
			for (final String line : fixture.log) {
				if (!line.equals("x")) {
					counts.add(line);
				}
			}
			assertEquals("growth 0", counts.get(0));
			assertEquals("with thread 1", counts.get(1));
			assertEquals("later done", counts.get(2));
			assertTrue(Integer.parseInt(counts.get(3).substring(6)) <= Integer.parseInt(counts.get(1).substring(12)) + 4,
					counts.toString());
		}
	}

	@Test
	void aScriptThatNeverEndsIsStopped() throws Exception {
		final Fixture fixture = new Fixture(null);
		try (LuaEnvironment lua = new LuaEnvironment(fixture.program.getGlobalScope(), host())) {
			lua.runNow(lua.loadChunk("while true do end".getBytes(StandardCharsets.UTF_8), "forever"));
			lua.runNow(lua.loadChunk("Log('alive')".getBytes(StandardCharsets.UTF_8), "after"));
			assertEquals(Arrays.asList("alive"), fixture.log);
		}
	}

	@Test
	void theClockOfTheScriptIsTheClockOfTheGame() throws Exception {
		final Fixture fixture = new Fixture(null);
		try (LuaEnvironment lua = new LuaEnvironment(fixture.program.getGlobalScope(), host(12.75))) {
			lua.runNow(lua.loadChunk("Log(tostring(os.time()) .. ' ' .. tostring(os.clock()))"
					.getBytes(StandardCharsets.UTF_8), "clock"));
			assertEquals(Arrays.asList("1000012 12.75"), fixture.log);
		}
	}

	@Test
	void aCodeValueCastsToAnIntegerWithoutAnInstructionPosition() throws Exception {
		final Fixture fixture = new Fixture(null);
		try (LuaEnvironment lua = new LuaEnvironment(fixture.program.getGlobalScope(), host())) {
			lua.runNow(lua.loadChunk("function F() end".getBytes(StandardCharsets.UTF_8), "f"));
			final LuaCodeJassValue code = lua.getGlobalFunction("F");
			assertEquals(code.getId(), code.toInteger());
			assertTrue(code.toInteger() > 0);
		}
	}

	private static final String SAVE_SCENARIO = String.join("\n",
			"state = {n = 0, order = {}}",
			"state.order.zeta = 1; state.order.alpha = 2; state.order.mid = 3",
			"function Worker()",
			"  state.thing = MakeThing(7)",
			"  LogInt('w1', Add(2, 3))",
			"  TriggerSleepAction(1.0)",
			"  state.n = state.n + 1",
			"  state.order.late = 4",
			"  local keys = {}",
			"  for k in pairs(state.order) do keys[#keys + 1] = k end",
			"  table.sort(keys)",
			"  Log('w2 ' .. tostring(state.thing == MakeThing(7)) .. ' ' .. ThingId(state.thing) .. ' ' .. table.concat(keys, ','))",
			"  local ok, err = pcall(function()",
			"    TriggerSleepAction(0.5)",
			"    Log('in pcall')",
			"    error('boom')",
			"  end)",
			"  LogInt('w3 ' .. tostring(ok) .. ' ' .. tostring(err):match('boom'), state.n)",
			"  TriggerSleepAction(0.5)",
			"  Log('w4 ' .. tostring(Echo('x')))",
			"end",
			"function Main()",
			"  Log('main')",
			"  Later(Worker)",
			"end",
			"");

	private static final class Run implements AutoCloseable {
		final Fixture fixture;
		final LuaEnvironment lua;

		Run() throws Exception {
			this.fixture = new Fixture(null);
			this.lua = new LuaEnvironment(this.fixture.program.getGlobalScope(), host());
		}

		void load() {
			this.lua.runNow(this.lua.loadChunk(SAVE_SCENARIO.getBytes(StandardCharsets.UTF_8), "save.lua"));
		}

		/** One pass of the thread queue, then every sleeper wakes. */
		void round() {
			this.fixture.program.getGlobalScope().runThreads();
		}

		@Override
		public void close() {
			this.lua.close();
		}
	}

	/** A map that hooks a native keeps the old one under a global of its own; the save names it as the game gave it. */
	@Test
	void aNativeAHookKeptUnderAnotherGlobalIsSavedAndLoaded() throws Exception {
		final LuaEnvironment.Snapshot snapshot;
		try (Run first = new Run()) {
			first.lua.run("RealAdd = Add; function Add(a, b) return RealAdd(a, b) + 100 end; Kept = {fn = RealAdd}",
					"=hook");
			snapshot = first.lua.capture();
		}
		try (Run second = new Run()) {
			second.lua.restore(snapshot, new ArrayList<>());
			second.lua.run("Log(tostring(Kept.fn(1, 2)) .. ' ' .. tostring(Add(1, 2)) .. ' ' .. tostring(RealAdd == Kept.fn))",
					"=check");
			assertEquals(Arrays.asList("3 103 true"), second.fixture.log);
		}
	}

	@Test
	void aScriptSavedWhileItWaitsContinuesAsIfNothingHappened() throws Exception {
		final List<String> control;
		try (Run run = new Run()) {
			run.load();
			run.lua.queue(run.lua.getGlobalFunction("Main"), false);
			run.fixture.runToEnd();
			control = run.fixture.log;
		}
		assertEquals(Arrays.asList("main", "w1 5", "w2 true 7 alpha,late,mid,zeta", "in pcall", "w3 false boom 1",
				"w4 x"), control);

		for (final int rounds : new int[] { 1, 2, 3 }) {
			final List<String> before;
			final LuaEnvironment.Snapshot snapshot;
			final List<JassThread> saved = new ArrayList<>();
			try (Run first = new Run()) {
				first.load();
				first.lua.queue(first.lua.getGlobalFunction("Main"), false);
				for (int i = 0; i < rounds; i++) {
					first.round();
					for (final JassThread sleeper : new ArrayList<>(first.fixture.sleepers)) {
						if (i < rounds - 1) {
							sleeper.setSleeping(false);
						}
					}
					if (i < rounds - 1) {
						first.fixture.sleepers.clear();
					}
				}
				snapshot = first.lua.capture();
				saved.addAll(first.fixture.program.getGlobalScope().getThreads());
				before = new ArrayList<>(first.fixture.log);
			}
			assertTrue(!saved.isEmpty(), "a thread is waiting");

			try (Run second = new Run()) {
				final GlobalScope scope = second.fixture.program.getGlobalScope();
				final List<LuaContinuation> continuations = new ArrayList<>();
				final List<JassThread> loaded = new ArrayList<>();
				for (final JassThread thread : saved) {
					final LuaContinuation continuation = (LuaContinuation) thread.getContinuation();
					final JassThread copy = scope.createThread(new LuaCodeJassValue(second.lua, continuation.getCodeId()),
							TriggerExecutionScope.EMPTY);
					final LuaContinuation restored = (LuaContinuation) copy.getContinuation();
					restored.setSaveIndex(continuation.getSaveIndex());
					continuations.add(restored);
					loaded.add(copy);
				}
				// The saved handles were made in another program: a game that loads has its own types for them.
				final List<HandleJassValue> loadedHandles = new ArrayList<>();
				for (final HandleJassValue handle : snapshot.handles) {
					loadedHandles.add(new HandleJassValue(second.fixture.thingType, handle.getJavaValue()));
					// ... and the objects it keeps are the ones the natives give.
					second.fixture.things.put((Integer) ((Object[]) handle.getJavaValue())[0], handle.getJavaValue());
				}
				snapshot.handles = loadedHandles;
				second.lua.restore(snapshot, continuations);
				for (final JassThread copy : loaded) {
					copy.setSleeping(true);
					second.fixture.sleepers.add(copy);
					scope.queueThread(copy);
				}
				second.fixture.runToEnd();
				final List<String> all = new ArrayList<>(before);
				all.addAll(second.fixture.log);
				assertEquals(control, all, "saved after " + rounds + " rounds");
			}
		}
	}

	@SuppressWarnings("unused")
	private static StringJassValue unused() {
		return null;
	}
}
