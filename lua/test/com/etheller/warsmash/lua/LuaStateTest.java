package com.etheller.warsmash.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class LuaStateTest {
	private static String eval(final LuaState lua, final String expression) {
		lua.run("result = tostring(" + expression + ")", "=eval");
		lua.getGlobal("result");
		final String value = lua.toStringValue(-1);
		lua.pop(1);
		return value;
	}

	@Test
	void runsChunksAndReadsGlobals() {
		try (LuaState lua = new LuaState()) {
			lua.run("x = 6 * 7; y = 'a' .. 'b'; z = 7 // 2; w = 7 / 2", "=chunk");
			lua.getGlobal("x");
			assertTrue(lua.isInteger(-1));
			assertEquals(42, lua.toInteger(-1));
			lua.getGlobal("z");
			assertTrue(lua.isInteger(-1));
			lua.getGlobal("w");
			assertFalse(lua.isInteger(-1));
			assertEquals(3.5, lua.toNumber(-1));
			lua.pop(3);
			lua.getGlobal("y");
			assertEquals("ab", lua.toStringValue(-1));
			assertEquals(LuaState.TYPE_STRING, lua.type(-1));
			lua.setTop(0);
			assertEquals(0, lua.getTop());
		}
	}

	@Test
	void callsLuaFunctionsWithArgumentsAndResults() {
		try (LuaState lua = new LuaState()) {
			lua.run("function add(a, b) return a + b, a * b end", "=chunk");
			lua.getGlobal("add");
			lua.pushInteger(3);
			lua.pushInteger(4);
			lua.call(2, 2);
			assertEquals(2, lua.getTop());
			assertEquals(7, lua.toInteger(1));
			assertEquals(12, lua.toInteger(2));
		}
	}

	@Test
	void javaFunctionsAreCalledAndReturnValues() {
		try (LuaState lua = new LuaState()) {
			final List<String> seen = new ArrayList<>();
			lua.register("native", state -> {
				seen.add(state.toStringValue(1) + ":" + state.toInteger(2) + ":" + state.toBoolean(3));
				state.pushString("ok");
				state.pushNumber(2.5);
				return 2;
			});
			lua.run("a, b = native('x', 5, true)", "=chunk");
			assertEquals(List.of("x:5:true"), seen);
			assertEquals("ok", eval(lua, "a"));
			assertEquals("2.5", eval(lua, "b"));
		}
	}

	@Test
	void stringsAreBytesAndKeepUtf8() {
		try (LuaState lua = new LuaState()) {
			lua.pushString("Zaibas ąčę");
			lua.setGlobal("s");
			assertEquals("13", eval(lua, "#s"));
			lua.pushBytes(new byte[] { 0, 1, (byte) 0xff, 0 });
			lua.setGlobal("b");
			assertEquals("4", eval(lua, "#b"));
			lua.getGlobal("b");
			assertEquals(4, lua.toBytes(-1).length);
			assertEquals((byte) 0xff, lua.toBytes(-1)[2]);
		}
	}

	@Test
	void luaErrorsCarryATraceback() {
		try (LuaState lua = new LuaState()) {
			lua.load("local function inner() error('boom') end\nfunction outer() inner() end", "=trace");
			lua.call(0, 0);
			lua.getGlobal("outer");
			final LuaException e = assertThrows(LuaException.class, () -> lua.call(0, 0));
			assertTrue(e.getMessage().contains("trace:1: boom"), e.getMessage());
			assertTrue(e.getMessage().contains("stack traceback:"), e.getMessage());
			assertTrue(e.getMessage().contains("inner"), e.getMessage());
			assertNull(e.getCause());
			// the state is still usable
			assertEquals("3", eval(lua, "1 + 2"));
		}
	}

	@Test
	void syntaxErrorsAreReported() {
		try (LuaState lua = new LuaState()) {
			final LuaSyntaxException e = assertThrows(LuaSyntaxException.class, () -> lua.load("x = = 1", "=bad"));
			assertTrue(e.getMessage().startsWith("bad:1:"), e.getMessage());
			assertEquals(0, lua.getTop());
			assertThrows(LuaSyntaxException.class, () -> lua.load("\u001bLua", "=bin"));
		}
	}

	@Test
	void javaExceptionsBecomeLuaErrorsAndComeBackAsTheCause() {
		try (LuaState lua = new LuaState()) {
			lua.register("fail", state -> {
				throw new IllegalStateException("from java");
			});
			lua.run("function guarded() local ok, e = pcall(fail); return e end", "=chunk");
			lua.getGlobal("guarded");
			lua.call(0, 1);
			assertTrue(lua.toStringValue(-1).contains("from java"));
			lua.setTop(0);
			lua.load("fail()", "=direct");
			final LuaException e = assertThrows(LuaException.class, () -> lua.call(0, 0));
			assertTrue(e.getCause() instanceof IllegalStateException);
			assertTrue(e.getMessage().contains("from java"), e.getMessage());
		}
	}

	@Test
	void javaCanCallBackIntoLua() {
		try (LuaState lua = new LuaState()) {
			lua.register("twice", state -> {
				state.pushValue(1);
				state.pushValue(2);
				state.call(1, 1);
				state.pushValue(1);
				state.pushValue(-2);
				state.call(1, 1);
				return 1;
			});
			assertEquals("20", eval(lua, "twice(function(v) return v * 2 end, 5)"));
		}
	}

	@Test
	void javaFunctionsWorkInsideCoroutines() {
		try (LuaState lua = new LuaState()) {
			lua.register("plus1", state -> {
				state.pushInteger(state.toInteger(1) + 1);
				return 1;
			});
			lua.run("local co = coroutine.wrap(function(a) local b = coroutine.yield(plus1(a)); return plus1(b) end)\n"
					+ "r1 = co(10); r2 = co(20)", "=co");
			assertEquals("11", eval(lua, "r1"));
			assertEquals("21", eval(lua, "r2"));
		}
	}

	@Test
	void tablesAreReadAndWrittenRaw() {
		try (LuaState lua = new LuaState()) {
			lua.newTable();
			lua.pushInteger(1);
			lua.setField(-2, "one");
			lua.pushString("v");
			lua.setIndex(-2, 3);
			lua.setGlobal("t");
			assertEquals("1", eval(lua, "t.one"));
			assertEquals("v", eval(lua, "t[3]"));
			lua.getGlobal("t");
			assertEquals(LuaState.TYPE_NUMBER, lua.getField(-1, "one"));
			assertEquals(LuaState.TYPE_NIL, lua.getField(-2, "two"));
			assertEquals(LuaState.TYPE_STRING, lua.getIndex(-3, 3));
		}
	}

	@Test
	void metamethodsDoNotRunThroughRawAccess() {
		try (LuaState lua = new LuaState()) {
			lua.run("t = setmetatable({}, {__index = function() error('no') end})", "=chunk");
			lua.getGlobal("t");
			assertEquals(LuaState.TYPE_NIL, lua.getField(-1, "x"));
		}
	}

	@Test
	void displayUsesTostring() {
		try (LuaState lua = new LuaState()) {
			lua.run("t = setmetatable({}, {__tostring = function() return 'named' end})", "=chunk");
			lua.getGlobal("t");
			assertEquals("named", lua.display(-1));
		}
	}

	@Test
	void restrictedLibrariesAreAbsent() {
		try (LuaState lua = new LuaState()) {
			for (final String name : new String[] { "io", "package", "require", "dofile", "loadfile", "os.execute", "os.remove",
					"os.rename", "os.tmpname", "os.getenv", "os.exit", "os.setlocale", "string.dump", "debug.sethook",
					"debug.getregistry", "debug.debug", "debug.upvalueid" }) {
				assertEquals("nil", eval(lua, "(function() local ok, v = pcall(function() return " + name + " end) return ok and v end)()"),
						name);
			}
			for (final String name : new String[] { "os.time", "os.clock", "os.date", "os.difftime", "debug.traceback", "debug.getinfo",
					"coroutine.wrap", "utf8.char", "math.tointeger", "string.pack", "table.move" }) {
				assertEquals("function", eval(lua, "type(" + name + ")"), name);
			}
			assertEquals("nil", eval(lua, "load(string.char(27) .. 'Lua')"));
		}
	}

	/** The orders the game's own Lua printed (Reforged 3.0.1) for these tables: the hash order of Lua 5.3. */
	@Test
	void pairsVisitsTheHashPartInTheGamesOrder() {
		try (LuaState lua = new LuaState()) {
			lua.run("local function keys(t) local out = {} for k in pairs(t) do out[#out + 1] = tostring(k) end "
					+ "return table.concat(out, ' ') end\n"
					+ "mixed = keys({ 10, 20, 30, a = 1, b = 2, c = 3, [100] = 4, [2.5] = 5, [true] = 6, x = 7, y = 8, z = 9 })\n"
					+ "local l = {} for c = 122, 97, -1 do l[string.char(c)] = c end letters = keys(l)\n"
					+ "local w = {} for _, k in ipairs({'gold', 'lumber', 'food', 'unit', 'hero', 'item', 'spell'}) do w[k] = 1 end "
					+ "words = keys(w)\n"
					+ "local r = { a = 1, b = 2, c = 3, d = 4 } r.b = nil r.e = 5 removal = keys(r)", "=order");
			assertEquals("1 2 3 true z y 100 2.5 c b a x", eval(lua, "mixed"));
			assertEquals("l k j i h g f e d c b a z y x w v u t s r q p o n m", eval(lua, "letters"));
			assertEquals("spell gold lumber food unit item hero", eval(lua, "words"));
			assertEquals("d c e a", eval(lua, "removal"));
		}
	}

	@Test
	void tablesAsKeysAreVisitedInTheSameOrderInEveryState() {
		final String script = "local t, out = {}, {}\n" + "for i = 1, 40 do t[{}] = i; t[function() end] = i end\n"
				+ "for k, v in pairs(t) do out[#out + 1] = v end\n" + "result = table.concat(out, ',')";
		final String first;
		try (LuaState lua = new LuaState()) {
			lua.run(script, "=a");
			first = eval(lua, "result");
		}
		try (LuaState lua = new LuaState()) {
			lua.run("local junk = {} for i = 1, 5000 do junk[i] = {i} end", "=junk");
			lua.run(script, "=b");
			assertEquals(first, eval(lua, "result"));
		}
	}

	@Test
	void sameScriptGivesTheSameOutputInTwoStates() {
		final String script = "local t, out = {}, {}\n" + "for i = 1, 300 do t[{}] = i; t['s' .. i] = i; t[i * 1.5] = i end\n"
				+ "for k, v in pairs(t) do out[#out + 1] = tostring(k) .. '=' .. v end\n"
				+ "local fs = {}; for i = 1, 20 do fs[i] = function() return i end end\n"
				+ "for i = 1, 20 do out[#out + 1] = tostring(fs[i]) end\n"
				+ "local arr = {}; for i = 1, 500 do arr[i] = (i * 7919) % 101 end\n"
				+ "table.sort(arr, function(a, b) return a < b end)\n" + "out[#out + 1] = table.concat(arr, ' ')\n"
				+ "out[#out + 1] = tostring(0/0) .. tostring(1/0) .. tostring(-1/0)\n"
				+ "for i = 1, 20 do out[#out + 1] = math.random(1, 1000) .. ' ' .. math.random() end\n"
				+ "out[#out + 1] = os.time() .. ' ' .. os.clock() .. ' ' .. os.date('%c', 1e9)\n"
				+ "out[#out + 1] = os.time{year = 2021, month = 14, day = 35, hour = 25}\n"
				+ "out[#out + 1] = tostring(2^0.5) .. ' ' .. 10 // 3 .. ' ' .. 10 / 4 .. ' ' .. (3 | 4)\n"
				+ "result = table.concat(out, '\\n')";
		final String first = runAndCollect(script, 1);
		final String second = runAndCollect(script, 6);
		assertEquals(first, second);
		assertTrue(first.length() > 1000);
	}

	private static String runAndCollect(final String script, final int ballast) {
		try (LuaState lua = new LuaState()) {
			// a different amount of garbage before the script, so that the addresses cannot agree by accident
			lua.run("local junk = {} for i = 1, " + ballast * 1000 + " do junk[i] = {i} end", "=junk");
			lua.run(script, "=det");
			lua.getGlobal("result");
			return lua.toStringValue(-1);
		}
	}

	@Test
	void randomComesFromTheEngineWhenSet() {
		try (LuaState lua = new LuaState()) {
			final long[] next = { 0 };
			lua.setRandomSource(() -> next[0]);
			assertEquals("1", eval(lua, "math.random(1, 10)"));
			next[0] = -1L; // all bits set: the top of the range
			assertEquals("10", eval(lua, "math.random(1, 10)"));
			lua.setRandomSource(null);
			assertNotNull(eval(lua, "math.random(1, 1000000)"));
		}
		// the stub is the same sequence in every state
		try (LuaState one = new LuaState(); LuaState two = new LuaState()) {
			assertEquals(eval(one, "math.random(1, 1000000)"), eval(two, "math.random(1, 1000000)"));
		}
	}

	@Test
	void randomHookExceptionBecomesAnError() {
		try (LuaState lua = new LuaState()) {
			lua.setRandomSource(() -> {
				throw new IllegalStateException("no random");
			});
			lua.load("return math.random(1, 3)", "=r");
			final LuaException e = assertThrows(LuaException.class, () -> lua.call(0, 1));
			assertTrue(e.getCause() instanceof IllegalStateException);
		}
	}

	@Test
	void clockAndTimeComeFromTheGame() {
		try (LuaState lua = new LuaState()) {
			assertEquals("0", eval(lua, "os.time()"));
			lua.setClock(1000, 12.5);
			assertEquals("1000", eval(lua, "os.time()"));
			assertEquals("12.5", eval(lua, "os.clock()"));
			assertEquals("1970-01-01 00:16:40", eval(lua, "os.date('%Y-%m-%d %H:%M:%S', os.time())"));
			assertEquals("1609459200", eval(lua, "os.time{year=2021, month=1, day=1, hour=0}"));
		}
	}

	@Test
	void printGoesToTheHandler() {
		try (LuaState lua = new LuaState()) {
			final StringBuilder out = new StringBuilder();
			lua.setPrintHandler(out::append);
			lua.run("print('a', 1, nil, true)\nprint()", "=p");
			assertEquals("a\t1\tnil\ttrue\n\n", out.toString());
		}
	}

	@Test
	void printedObjectsHaveStableIds() {
		try (LuaState lua = new LuaState()) {
			lua.run("local t, f = {}, function() end\n" + "a, b, c, d = tostring(t), tostring(f), tostring(t), tostring({})", "=ids");
			assertEquals("table: 000001FB7B800010", eval(lua, "a"));
			assertEquals("function: 000001FB7B800020", eval(lua, "b"));
			assertEquals("table: 000001FB7B800010", eval(lua, "c"));
			assertEquals("table: 000001FB7B800030", eval(lua, "d"));
		}
	}

	@Test
	void finalizersAndWeakTablesAreIgnored() {
		try (LuaState lua = new LuaState()) {
			lua.run("finalized = 0\n" + "do local x = setmetatable({}, {__gc = function() finalized = finalized + 1 end}) end\n"
					+ "weak = setmetatable({}, {__mode = 'kv'})\n" + "weak[1] = {}; weak[{}] = 1\n" + "collectgarbage(); collectgarbage()\n"
					+ "count = 0; for _ in pairs(weak) do count = count + 1 end", "=gc");
			assertEquals("0", eval(lua, "finalized"));
			assertEquals("2", eval(lua, "count"));
			lua.collectGarbage();
		}
	}

	@Test
	void memoryLimitStopsRunawayScripts() {
		try (LuaState lua = new LuaState(8L << 20)) {
			lua.load("local t = {} for i = 1, 1e8 do t[i] = {i} end", "=mem");
			final LuaException e = assertThrows(LuaException.class, () -> lua.call(0, 0));
			assertTrue(e.getMessage().contains("not enough memory"), e.getMessage());
			assertTrue(lua.getMemoryUsed() <= (8L << 20));
			lua.setTop(0);
			lua.collectGarbage();
			assertEquals("3", eval(lua, "1 + 2"));
		}
	}

	@Test
	void numbersFormatTheSameEverywhere() {
		try (LuaState lua = new LuaState()) {
			assertEquals("3", eval(lua, "3"));
			assertEquals("3.0", eval(lua, "3.0"));
			assertEquals("-0.0", eval(lua, "-0.0"));
			assertEquals("inf", eval(lua, "1e100"));
			assertEquals("2147483647", eval(lua, "math.maxinteger"));
			assertEquals("2.147484e+09", eval(lua, "math.maxinteger + 0.0"));
			assertEquals("1.0", eval(lua, "0/0"));
			assertEquals("1.701412e+38", eval(lua, "1/0"));
			assertEquals("-1.701412e+38", eval(lua, "-1/0"));
			assertEquals("0", eval(lua, "1 << 62"));
			assertEquals("-2147483648", eval(lua, "math.maxinteger + 1"));
			assertEquals("0.3333333", eval(lua, "1/3"));
			assertEquals("9.007199e+15", eval(lua, "2^53"));
			assertEquals("inf", eval(lua, "math.huge"));
			assertEquals("-inf", eval(lua, "-math.huge"));
			assertEquals("0.1", eval(lua, "0.1"));
			assertEquals("3", eval(lua, "7 // 2"));
			assertEquals("-4", eval(lua, "-7 // 2"));
			assertEquals("1", eval(lua, "-7 % 4"));
		}
	}

	@Test
	void closedStateRefusesFurtherUse() {
		final LuaState lua = new LuaState();
		lua.close();
		lua.close();
		assertThrows(IllegalStateException.class, lua::getTop);
	}
}
