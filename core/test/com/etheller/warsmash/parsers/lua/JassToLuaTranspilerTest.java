package com.etheller.warsmash.parsers.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.etheller.warsmash.lua.LuaState;

class JassToLuaTranspilerTest {
	private static final String COMMON = String.join("\n",
			"native I2R takes integer i returns real",
			"native R2I takes real r returns integer",
			"native Sqrt takes real r returns real",
			"globals",
			"    constant integer SOME = 5",
			"endglobals",
			"");

	private static String run(final String blizzard, final String call) {
		final String lua = JassToLuaTranspiler.transpile(COMMON, blizzard);
		try (LuaState state = new LuaState()) {
			state.register("I2R", l -> {
				l.pushNumber(l.toNumber(1));
				return 1;
			});
			state.register("R2I", l -> {
				l.pushInteger((long) l.toNumber(1));
				return 1;
			});
			state.register("Sqrt", l -> {
				l.pushNumber(StrictMath.sqrt(l.toNumber(1)));
				return 1;
			});
			// The environment defines this one for scripts; here the test does.
			state.run("function __ra(a, b) return a + b end function __rs(a, b) return a - b end "
					+ "function __rm(a, b) return a * b end function __rd(a, b) return a / b end "
					+ "function __req(a, b) return a == b end function __i2r(a) return a + 0.0 end", "=arithmetic");
			state.run("function __jarray(default) return setmetatable({}, {__index = function() return default end}) end",
					"=jarray");
			state.run(lua, "=transpiled");
			state.run("result = tostring(" + call + ")", "=call");
			state.getGlobal("result");
			return state.toStringValue(-1);
		}
	}

	@Test
	void integerDivisionTruncatesTowardZeroAndRealsDivideExactly() {
		final String source = String.join("\n",
				"function Div takes integer a, integer b returns integer",
				"    return a / b",
				"endfunction",
				"function RealDiv takes integer a, integer b returns real",
				"    return I2R(a) / b",
				"endfunction",
				"function IntAsReal takes integer a returns real",
				"    return a",
				"endfunction");
		assertEquals("-2", run(source, "Div(-5, 2)"));
		assertEquals("-2", run(source, "Div(5, -2)"));
		assertEquals("3", run(source, "Div(7, 2)"));
		assertEquals("2.5", run(source, "RealDiv(5, 2)"));
		assertEquals("4.0", run(source, "IntAsReal(4)"));
	}

	@Test
	void controlFlowStringsRawcodesAndArraysWork() {
		final String source = String.join("\n",
				"globals",
				"    integer array squares",
				"    string array names",
				"    constant integer CODE = 'hfoo'",
				"    string greeting = \"say \\\"hi\\\"\n\"",
				"    real half = 0.5",
				"endglobals",
				"function Fill takes integer n returns integer",
				"    local integer i = 1",
				"    local integer total = 0",
				"    loop",
				"        exitwhen i > n",
				"        set squares[i] = i * i",
				"        set total = total + squares[i]",
				"        set i = i + 1",
				"    endloop",
				"    return total",
				"endfunction",
				"function Pick takes integer v returns string",
				"    if v == 1 then",
				"        return \"one\" + \"!\"",
				"    elseif v == 2 and not (v == 3) then",
				"        return \"two\"",
				"    else",
				"        return null",
				"    endif",
				"endfunction",
				"function Unset takes nothing returns integer",
				"    return squares[99] + 7",
				"endfunction",
				"function Code takes nothing returns integer",
				"    return CODE",
				"endfunction",
				"function Loc takes nothing returns real",
				"    local real r",
				"    local integer end = 3",
				"    set r = end + half",
				"    return r",
				"endfunction");
		assertEquals("30", run(source, "Fill(4)"));
		assertEquals("one!", run(source, "Pick(1)"));
		assertEquals("two", run(source, "Pick(2)"));
		assertEquals("nil", run(source, "Pick(5)"));
		assertEquals("7", run(source, "Unset()"));
		assertEquals(String.valueOf(0x68666f6f), run(source, "Code()"));
		assertEquals("3.5", run(source, "Loc()"));
		assertEquals("say \"hi\"\n", run(source, "greeting"));
	}

	@Test
	void aHandleOfBlizzardJThatTheEngineAlreadyMadeIsNotMadeAgain() {
		final String common = String.join("\n", "type timer extends handle", "native CreateTimer takes nothing returns timer",
				"");
		final String blizzard = String.join("\n", "globals", "    timer made = CreateTimer()", "    integer count = 3",
				"endglobals", "");
		final String lua = JassToLuaTranspiler.transpile(common, blizzard);
		assertTrue(lua.contains("if made == nil then made = CreateTimer() end"), lua);
		assertTrue(lua.contains("count = 3"), lua);
		assertTrue(JassToLuaTranspiler.transpile(common, "", blizzard).contains("made = CreateTimer()"));
		assertTrue(!JassToLuaTranspiler.transpile(common, "", blizzard).contains("if made == nil"));
	}

	@Test
	void theGameDataBlizzardJTranspilesAndCompiles() throws IOException {
		final String directory = System.getenv("WARSMASH_LUA_SCRIPTS");
		Assumptions.assumeTrue((directory != null) && !directory.isEmpty());
		final String common = new String(Files.readAllBytes(Paths.get(directory, "common.j")), StandardCharsets.UTF_8);
		final String blizzard = new String(Files.readAllBytes(Paths.get(directory, "blizzard.j")),
				StandardCharsets.UTF_8);
		final long start = System.nanoTime();
		final String lua = JassToLuaTranspiler.transpile(common, blizzard);
		final long millis = (System.nanoTime() - start) / 1_000_000;
		Files.write(Path.of(System.getProperty("java.io.tmpdir"), "blizzard-transpiled.lua"),
				lua.getBytes(StandardCharsets.UTF_8));
		System.out.println("blizzard.j -> " + lua.length() + " chars of Lua in " + millis + " ms");
		assertTrue(lua.contains("function PolledWait("), "PolledWait");
		try (LuaState state = new LuaState()) {
			state.load(lua, "=blizzard");
			state.pop(1);
		}
	}
}
