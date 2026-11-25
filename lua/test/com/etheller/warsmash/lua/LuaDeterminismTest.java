package com.etheller.warsmash.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The platform independence of the scripts' arithmetic and the limits, yields and memory safety of the bridge. */
class LuaDeterminismTest {
	private static String eval(final LuaState lua, final String expression) {
		lua.run("result = tostring(" + expression + ")", "=eval");
		lua.getGlobal("result");
		final String value = lua.toStringValue(-1);
		lua.pop(1);
		return value;
	}

	// --- libm

	/**
	 * native/test/golden.txt: name, input bits, second input bits, result bits, written by libmcheck.c on desktop from the
	 * double routines. The numbers of the script are floats: the lines whose inputs are exact floats give that result
	 * rounded to a float.
	 */
	@Test
	void mathFunctionsMatchTheGoldenBitsExactly() throws IOException {
		final Path golden = Path.of("native/test/golden.txt");
		final List<String> lines = Files.readAllLines(golden);
		assertEquals(4800, lines.size());
		try (LuaState lua = new LuaState()) {
			lua.run("local fns = {sin = math.sin, cos = math.cos, tan = math.tan, asin = math.asin, acos = math.acos,\n"
					+ " atan = math.atan, exp = math.exp, atan2 = math.atan, fmod = math.fmod,\n"
					+ " log = math.log, log2 = function(x) return math.log(x, 2) end,\n"
					+ " log10 = function(x) return math.log(x, 10) end, pow = function(x, y) return x ^ y end}\n"
					+ "function run(name, x, y)\n"
					+ " local f = fns[name]\n"
					+ " if not f then return nil end\n"
					+ " if name == 'pow' and y == 2 then return nil end\n" // the ^ operator squares by multiplying
					+ " if name == 'atan2' or name == 'fmod' or name == 'pow' then return f(x, y) end\n"
					+ " return f(x)\n"
					+ "end", "=golden");
			int checked = 0;
			for (final String line : lines) {
				final String[] part = line.split(" ");
				final double x = Double.longBitsToDouble(Long.parseUnsignedLong(part[1], 16));
				final double y = Double.longBitsToDouble(Long.parseUnsignedLong(part[2], 16));
				if ((x != (float) x) || (y != (float) y)) {
					continue;
				}
				lua.getGlobal("run");
				lua.pushString(part[0]);
				lua.pushNumber(x);
				lua.pushNumber(y);
				lua.call(3, 1);
				if (lua.type(-1) == LuaState.TYPE_NIL) {
					lua.pop(1);
					continue;
				}
				final float expected = (float) Double.longBitsToDouble(Long.parseUnsignedLong(part[3], 16));
				final float actual = (float) lua.toNumber(-1);
				if (Float.isNaN(expected)) {
					assertTrue(Float.isNaN(actual), line);
				}
				else {
					assertEquals(Float.floatToRawIntBits(expected), Float.floatToRawIntBits(actual), line);
				}
				lua.pop(1);
				checked++;
			}
			assertTrue(checked > 100, "checked " + checked);
		}
	}

	@Test
	void mathFunctionsAreCloseToStrictMath() {
		try (LuaState lua = new LuaState()) {
			for (double x0 = -3.0; x0 < 3.0; x0 += 0.0371) {
				final double x = (float) x0;
				lua.run("x = " + Double.toString(x), "=x");
				assertClose(StrictMath.sin(x), lua, "math.sin(x)");
				assertClose(StrictMath.cos(x), lua, "math.cos(x)");
				assertClose(StrictMath.tan(x), lua, "math.tan(x)");
				assertClose(StrictMath.atan(x), lua, "math.atan(x)");
				assertClose(StrictMath.exp(x), lua, "math.exp(x)");
				assertClose(StrictMath.log((float) (x * x) + 0.5), lua, "math.log(x * x + 0.5)");
				assertClose(StrictMath.log10((float) (x * x) + 0.5), lua, "math.log(x * x + 0.5, 10)");
				assertClose(StrictMath.pow((float) ((float) (x * x) + 0.5), 1.7), lua, "(x * x + 0.5) ^ 1.7");
				assertClose(StrictMath.asin((float) (x / 3)), lua, "math.asin(x / 3)");
			}
		}
	}

	private static void assertClose(final double expected, final LuaState lua, final String expression) {
		lua.run("result = " + expression, "=close");
		lua.getGlobal("result");
		final double actual = lua.toNumber(-1);
		lua.pop(1);
		assertTrue(Math.abs(expected - actual) <= (2e-6 * Math.max(1.0, Math.abs(expected))),
				expression + ": " + expected + " vs " + actual);
	}

	// --- string.format of nan and infinity

	@Test
	void formatOfNanAndInfinityIsTheSameEverywhere() {
		try (LuaState lua = new LuaState()) {
			assertEquals("inf", eval(lua, "string.format('%f', math.huge)"));
			assertEquals("-inf", eval(lua, "string.format('%f', -math.huge)"));
			assertEquals("nan", eval(lua, "string.format('%f', (math.huge - math.huge))"));
			assertEquals("nan", eval(lua, "string.format('%f', -(math.huge - math.huge))"));
			assertEquals("inf", eval(lua, "string.format('%e', math.huge)"));
			assertEquals("-inf", eval(lua, "string.format('%g', -math.huge)"));
			assertEquals("  inf", eval(lua, "string.format('%5.1f', math.huge)"));
			assertEquals("inf   |", eval(lua, "string.format('%-6g|', math.huge)"));
			assertEquals("INF", eval(lua, "string.format('%G', math.huge)"));
			assertEquals("NAN", eval(lua, "string.format('%E', (math.huge - math.huge))"));
			assertEquals("+inf", eval(lua, "string.format('%+f', math.huge)"));
			assertEquals("       inf", eval(lua, "string.format('%010f', math.huge)"));
			assertEquals("inf", eval(lua, "string.format('%a', math.huge)"));
			assertEquals("-inf", eval(lua, "string.format('%A', -math.huge)").toLowerCase());
			assertEquals("1.500000", eval(lua, "string.format('%f', 1.5)"));
		}
	}

	// --- operation limit

	@Test
	void operationLimitStopsRunawayScriptsDeterministically() {
		final long[] counts = new long[2];
		for (int run = 0; run < 2; run++) {
			try (LuaState lua = new LuaState()) {
				if (run == 1) {
					lua.run("junk = {} for i = 1, 5000 do junk[i] = {i} end", "=junk");
					lua.collectGarbage();
				}
				lua.setOperationLimit(200_000);
				final LuaException error = assertThrows(LuaException.class,
						() -> lua.run("n = 0 while true do n = n + 1 end", "=loop"));
				assertTrue(error.getMessage().contains("operation limit"), error.getMessage());
				lua.getGlobal("n");
				counts[run] = lua.toInteger(-1);
				lua.pop(1);
				assertTrue(counts[run] > 10_000 && counts[run] < 200_000, "n = " + counts[run]);
				// the budget is renewed by the next call from Java
				lua.run("ok = 1", "=after");
				// a script cannot outrun the limit by catching the error: the budget stays used up until the call ends
				assertThrows(LuaException.class, () -> lua.run(
						"pcall(function() while true do end end) local t = 0 for i = 1, 1e7 do t = t + i end", "=catch"));
			}
		}
		assertEquals(counts[0], counts[1]);
	}

	@Test
	void operationLimitCanBeRemovedAndAppliesToCoroutines() {
		try (LuaState lua = new LuaState()) {
			lua.setOperationLimit(50_000);
			lua.load("while true do end", "=spin");
			final LuaCoroutine co = lua.newCoroutine();
			final LuaException error = assertThrows(LuaException.class, () -> lua.resume(co, 0));
			assertTrue(error.getMessage().contains("operation limit"), error.getMessage());
			assertTrue(co.isFinished());
			lua.setOperationLimit(0);
			lua.run("local t = 0 for i = 1, 3e6 do t = t + 1 end total = t", "=free");
			lua.getGlobal("total");
			assertEquals(3_000_000, lua.toInteger(-1));
		}
	}

	// --- yield from Java functions

	private static void registerWait(final LuaState lua) {
		lua.register("wait", l -> l.yield(l.getTop()));
	}

	@Test
	void javaFunctionYieldsAndJavaResumesWithValues() {
		try (LuaState lua = new LuaState()) {
			registerWait(lua);
			lua.load("local total = 0\n"
					+ "local got = wait('first', 1)\n"
					+ "total = total + got\n"
					+ "local a, b = wait('second')\n"
					+ "return 'done', total, a, b", "=script");
			final LuaCoroutine co = lua.newCoroutine();
			assertEquals(0, lua.getTop());

			assertEquals(2, lua.resume(co, 0));
			assertEquals(1, lua.toInteger(-1));
			assertEquals("first", lua.toStringValue(-2));
			assertFalse(co.isFinished());
			lua.pop(2);

			lua.pushInteger(41);
			assertEquals(1, lua.resume(co, 1));
			assertEquals("second", lua.toStringValue(-1));
			lua.pop(1);

			lua.pushString("x");
			lua.pushNumber(2.5);
			assertEquals(4, lua.resume(co, 2));
			assertTrue(co.isFinished());
			assertEquals("done", lua.toStringValue(1));
			assertEquals(41, lua.toInteger(2));
			assertEquals("x", lua.toStringValue(3));
			assertEquals(2.5, lua.toNumber(4));
			lua.setTop(0);
			lua.release(co);
		}
	}

	@Test
	void yieldWorksThroughPcallMetamethodsAndScriptCoroutines() {
		try (LuaState lua = new LuaState()) {
			registerWait(lua);
			lua.load("local log = {}\n"
					+ "local ok, v = pcall(function() return wait('in pcall') + 1 end)\n"
					+ "log[#log + 1] = tostring(ok) .. ':' .. tostring(v)\n"
					+ "local t = setmetatable({}, {__index = function(_, k) return wait('index ' .. k) end})\n"
					+ "log[#log + 1] = t.key\n"
					+ "for x in (function() return wait('iter') end) do log[#log + 1] = x break end\n"
					+ "return table.concat(log, ',')", "=script");
			final LuaCoroutine co = lua.newCoroutine();
			assertEquals(1, lua.resume(co, 0));
			assertEquals("in pcall", lua.toStringValue(-1));
			lua.pop(1);
			lua.pushInteger(9);
			assertEquals(1, lua.resume(co, 1));
			assertEquals("index key", lua.toStringValue(-1));
			lua.pop(1);
			lua.pushString("V");
			assertEquals(1, lua.resume(co, 1));
			assertEquals("iter", lua.toStringValue(-1));
			lua.pop(1);
			lua.pushString("I");
			assertEquals(1, lua.resume(co, 1));
			assertEquals("true:10,V,I", lua.toStringValue(-1));
			assertTrue(co.isFinished());

			// a coroutine of the script itself: wrap resumes it from Lua, the Java function yields to that resumer
			lua.setTop(0);
			lua.run("w = coroutine.wrap(function(a) local b = wait(a, 'out') return 'end ' .. b end)\n"
					+ "r1, r2 = w('in')\n"
					+ "r3 = w('back')", "=wrap");
			lua.getGlobal("r1");
			lua.getGlobal("r2");
			lua.getGlobal("r3");
			assertEquals("in", lua.toStringValue(1));
			assertEquals("out", lua.toStringValue(2));
			assertEquals("end back", lua.toStringValue(3));
		}
	}

	@Test
	void yieldOutsideACoroutineIsALuaError() {
		try (LuaState lua = new LuaState()) {
			registerWait(lua);
			final LuaException error = assertThrows(LuaException.class, () -> lua.run("wait(1)", "=main"));
			assertTrue(error.getMessage().contains("outside a coroutine"), error.getMessage());
			lua.run("ok, err = pcall(wait, 1)", "=pcall");
			lua.getGlobal("ok");
			assertFalse(lua.toBoolean(-1));
		}
	}

	@Test
	void errorsInResumedCodeCarryATraceback() {
		try (LuaState lua = new LuaState()) {
			registerWait(lua);
			lua.register("boom", l -> {
				throw new IllegalStateException("java failure");
			});
			lua.load("local function inner() boom() end\nwait('x')\ninner()", "=deep");
			final LuaCoroutine co = lua.newCoroutine();
			assertEquals(1, lua.resume(co, 0));
			lua.pop(1);
			final LuaException error = assertThrows(LuaException.class, () -> lua.resume(co, 0));
			assertTrue(error.getMessage().contains("stack traceback"), error.getMessage());
			assertTrue(error.getMessage().contains("inner"), error.getMessage());
			assertTrue(error.getMessage().contains("deep"), error.getMessage());
			assertNotNull(error.getCause());
			assertTrue(error.getCause() instanceof IllegalStateException);
			assertTrue(co.isFinished());
			assertThrows(LuaException.class, () -> lua.resume(co, 0));
			assertEquals(0, lua.getTop());

			lua.load("error({code = 7})", "=obj");
			final LuaCoroutine co2 = lua.newCoroutine();
			final LuaException objError = assertThrows(LuaException.class, () -> lua.resume(co2, 0));
			assertTrue(objError.getMessage().contains("error object is a table value"), objError.getMessage());
		}
	}

	@Test
	void manyCoroutinesYieldInterleaved() {
		try (LuaState lua = new LuaState()) {
			registerWait(lua);
			final List<LuaCoroutine> all = new ArrayList<>();
			for (int i = 0; i < 50; i++) {
				lua.load("local id = ... local s = 0 for i = 1, 3 do s = s + wait(id, i) end return id, s", "=co" + i);
				all.add(lua.newCoroutine());
			}
			for (int i = 0; i < 50; i++) {
				lua.pushInteger(i);
				assertEquals(2, lua.resume(all.get(i), 1));
				lua.setTop(0);
			}
			for (int step = 2; step <= 4; step++) {
				for (int i = 0; i < 50; i++) {
					lua.pushInteger(10);
					final int n = lua.resume(all.get(i), 1);
					if (step < 4) {
						assertEquals(2, n);
						assertEquals(i, lua.toInteger(-2));
						assertEquals(step, lua.toInteger(-1));
					}
					else {
						assertEquals(2, n);
						assertEquals(i, lua.toInteger(-2));
						assertEquals(30, lua.toInteger(-1));
					}
					lua.setTop(0);
				}
			}
			for (final LuaCoroutine co : all) {
				assertTrue(co.isFinished());
				lua.release(co);
			}
		}
	}

	// --- memory

	@Test
	void pushingPastTheMemoryLimitFailsWithoutPanicking() {
		try (LuaState lua = new LuaState()) {
			lua.setMemoryLimit(lua.getMemoryUsed() + 100_000);
			final byte[] chunk = new byte[10_000];
			boolean failed = false;
			for (int i = 0; i < 100 && !failed; i++) {
				try {
					chunk[0] = (byte) i; // distinct strings: no interning
					lua.pushBytes(chunk);
				}
				catch (final LuaException e) {
					assertTrue(e.getMessage().contains("memory"), e.getMessage());
					failed = true;
				}
			}
			assertTrue(failed);
			lua.setTop(0);
			lua.collectGarbage();
			// tables, closures and globals fail the same way
			lua.setMemoryLimit(lua.getMemoryUsed() + 2_000);
			boolean tableFailed = false;
			lua.newTable();
			try {
				for (int i = 0; i < 100_000; i++) {
					lua.pushInteger(i);
					lua.setIndex(1, i + 1);
				}
			}
			catch (final LuaException e) {
				tableFailed = true;
			}
			assertTrue(tableFailed);
			lua.setTop(0);
			lua.collectGarbage();
			lua.setMemoryLimit(0);
			assertEquals("ok", eval(lua, "'ok'"));
		}
	}

	@Test
	void javaFunctionsThatRunOutOfMemoryInsideACallBecomeLuaErrors() {
		try (LuaState lua = new LuaState()) {
			lua.register("hog", l -> {
				final byte[] chunk = new byte[20_000];
				for (int i = 0; i < 100; i++) {
					chunk[0] = (byte) i;
					l.pushBytes(chunk);
				}
				return 0;
			});
			lua.setMemoryLimit(lua.getMemoryUsed() + 100_000);
			lua.run("ok, err = pcall(hog)", "=hog");
			lua.getGlobal("ok");
			assertFalse(lua.toBoolean(-1));
			lua.getGlobal("err");
			assertTrue(lua.toStringValue(-1).contains("memory"), lua.toStringValue(-1));
			lua.setTop(0);
			lua.collectGarbage();
			lua.setMemoryLimit(0);
			assertEquals("fine", eval(lua, "'fine'"));
		}
	}

	@Test
	void aTooSmallMemoryLimitDoesNotCreateAState() {
		assertThrows(IllegalStateException.class, () -> new LuaState(2_000));
	}
}
