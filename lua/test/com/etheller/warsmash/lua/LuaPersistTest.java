package com.etheller.warsmash.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Persisting suspended coroutines (Eris) with the engine's deterministic tables. */
class LuaPersistTest {
	static final String SCRIPT = String.join("\n",
			"local t = {}",
			"t.zeta = 1; t.alpha = 2; t.mid = 3; t[10] = 4; t[1] = 5; t.beta = 6; t.alpha = nil; t.alpha = 7",
			"world = {names = t, counter = 0}",
			"co = coroutine.create(function(a)",
			"  local acc = {}",
			"  for i = 1, 3 do",
			"    local v = wait('w' .. i, a + i)",
			"    world.counter = world.counter + 1",
			"    acc[#acc + 1] = v * 2",
			"  end",
			"  local ok, p = pcall(function() return wait('inpcall') .. '!' end)",
			"  local order = {}",
			"  for k in pairs(world.names) do order[#order + 1] = tostring(k) end",
			"  world.names.late = 8",
			"  for k in pairs(world.names) do order[#order + 1] = tostring(k) end",
			"  local viaGmatch = {}",
			"  for w in string.gmatch('a b c', '%a') do viaGmatch[#viaGmatch + 1] = w; wait('g' .. w) end",
			"  return table.concat(acc, ','), tostring(ok) .. p, table.concat(order, ','), table.concat(viaGmatch, ''),",
			"      world.counter, math.random(1000)",
			"end)",
			"function drive(...)",
			"  local r = table.pack(coroutine.resume(co, ...))",
			"  local out = {}",
			"  for i = 1, r.n do out[#out + 1] = tostring(r[i]) end",
			"  return table.concat(out, '|') .. ' status=' .. coroutine.status(co)",
			"end",
			"snapshot = {drive = drive, world = world}");

	static LuaState newState(final List<String> printed) {
		final LuaState lua = new LuaState();
		lua.setPrintHandler(printed::add);
		lua.register("wait", l -> l.yield(l.getTop()));
		lua.setRandomSource(new LuaRandomSource() {
			private long state = 12345;

			@Override
			public long nextLong() {
				this.state = this.state * 6364136223846793005L + 1442695040888963407L;
				return this.state;
			}
		});
		return lua;
	}

	private static String drive(final LuaState lua, final int driveIndexInSnapshot, final Object... args) {
		// snapshot.drive(...) with snapshot at the stack bottom
		lua.getField(1, "drive");
		for (final Object arg : args) {
			if (arg instanceof Long) {
				lua.pushInteger((Long) arg);
			}
			else {
				lua.pushString(String.valueOf(arg));
			}
		}
		lua.call(args.length, 1);
		final String result = lua.toStringValue(-1);
		lua.pop(1);
		return result;
	}

	static List<String> runSteps(final LuaState lua, final int from, final int to) {
		final List<String> out = new ArrayList<>();
		for (int step = from; step < to; step++) {
			out.add(drive(lua, 0, Long.valueOf(step * 10L)));
		}
		return out;
	}

	@Test
	void aCoroutineSuspendedInAJavaFunctionContinuesIdenticallyAfterPersisting() {
		final int totalSteps = 9;
		final List<String> control;
		try (LuaState lua = newState(new ArrayList<>())) {
			lua.run(SCRIPT, "=script");
			lua.getGlobal("snapshot");
			control = runSteps(lua, 0, totalSteps);
		}
		assertTrue(control.get(0).contains("w1"), control.toString());
		assertTrue(control.get(totalSteps - 1).contains("status=dead"), control.toString());

		for (final int cut : new int[] { 1, 2, 3, 4, 6 }) {
			final List<String> result = new ArrayList<>();
			final byte[] bytes;
			try (LuaState first = newState(new ArrayList<>())) {
				first.run(SCRIPT, "=script");
				first.getGlobal("snapshot");
				result.addAll(runSteps(first, 0, cut));
				bytes = first.persist(1);
			}
			assertTrue(bytes.length > 100);
			// persisting is repeatable: the same state gives the same bytes
			try (LuaState second = newState(new ArrayList<>())) {
				second.unpersist(bytes);
				assertEquals(LuaState.TYPE_TABLE, second.type(1));
				assertEquals(1, second.getTop());
				result.addAll(runSteps(second, cut, totalSteps));
				second.setTop(0);
			}
			assertEquals(control, result, "cut after step " + cut);
		}
	}

	@Test
	void tableOrderSurvivesPersisting() {
		try (LuaState first = newState(new ArrayList<>())) {
			first.run(SCRIPT, "=script");
			first.run("local o = {} for k in pairs(world.names) do o[#o + 1] = tostring(k) end order = table.concat(o, ',')", "=o");
			first.getGlobal("order");
			final String before = first.toStringValue(-1);
			first.pop(1);
			first.getGlobal("snapshot");
			final byte[] bytes = first.persist(1);
			final byte[] again = first.persist(1);
			assertEquals(bytes.length, again.length);
			assertTrue(java.util.Arrays.equals(bytes, again));
			try (LuaState second = newState(new ArrayList<>())) {
				second.unpersist(bytes);
				second.getField(1, "world");
				second.getField(-1, "names");
				second.setGlobal("names");
				second.setTop(0);
				second.run("local o = {} for k in pairs(names) do o[#o + 1] = tostring(k) end order = table.concat(o, ',')", "=o");
				second.getGlobal("order");
				assertEquals(before, second.toStringValue(-1));
				assertTrue(before.contains("zeta") && before.contains("alpha"), before);
			}
		}
	}

	@Test
	void persistedStateIsTheSameOnEveryRun() {
		byte[][] runs = new byte[2][];
		for (int i = 0; i < 2; i++) {
			try (LuaState lua = newState(new ArrayList<>())) {
				if (i == 1) {
					lua.run("local junk = {} for n = 1, 3000 do junk[n] = {n} end", "=junk");
					lua.collectGarbage();
				}
				lua.run(SCRIPT, "=script");
				lua.getGlobal("snapshot");
				runSteps(lua, 0, 3);
				runs[i] = lua.persist(1);
			}
		}
		assertEquals(runs[0].length, runs[1].length);
		assertTrue(java.util.Arrays.equals(runs[0], runs[1]));
	}

	@Test
	void permanentsKeepValuesOutOfTheBytes() {
		try (LuaState first = newState(new ArrayList<>())) {
			first.run("big = {1, 2, 3} data = {ref = big, n = 5}", "=perm");
			first.newTable(); // permanents: object -> name
			first.getGlobal("big");
			first.pushString("the big one");
			first.setTable(1);
			first.getGlobal("data");
			final byte[] bytes = first.persist(-1, 1);
			try (LuaState second = newState(new ArrayList<>())) {
				second.run("replacement = {'x'}", "=r");
				second.newTable();
				second.getGlobal("replacement");
				second.pushString("the big one");
				second.setTable(1);
				second.unpersist(bytes, 1);
				second.getField(2, "ref");
				second.getGlobal("replacement");
				assertEquals(second.display(3), second.display(4));
				second.getField(2, "n");
				assertEquals(5, second.toInteger(-1));
			}
		}
	}

	@Test
	void whatCannotBePersistedIsALuaException() {
		try (LuaState lua = newState(new ArrayList<>())) {
			lua.register("native", l -> 0);
			lua.run("t = {f = print, g = function() return 1 end}", "=t");
			lua.getGlobal("t");
			lua.persist(1); // library functions and Java functions are fine
			lua.run("u = {c = coroutine.running()}", "=main");
			lua.getGlobal("u");
			final LuaException error = assertThrows(LuaException.class, () -> lua.persist(-1));
			assertFalse(error.getMessage().isEmpty());
			assertThrows(LuaException.class, () -> lua.unpersist(new byte[] { 1, 2, 3 }));
		}
	}
}
