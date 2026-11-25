package com.etheller.warsmash.parsers.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.util.AllocatedHandle;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.warsmash.lua.LuaRandomSource;

import net.warsmash.parsers.jass.SmashJassParser;

/** What a script holds of the engine's objects leaves with them: a long game does not keep every unit it ever saw. */
class LuaHandleReleaseTest {
	private static final String COMMON = String.join("\n",
			"type unit extends handle",
			"type timer extends handle",
			"type effect extends handle",
			"native CreateUnit takes nothing returns unit",
			"native RemoveUnit takes unit u returns nothing",
			"native GetUnitX takes unit u returns integer",
			"native CreateTimer takes nothing returns timer",
			"native DestroyTimer takes timer t returns nothing",
			"native AddSpecialEffect takes nothing returns effect",
			"native DestroyEffect takes effect e returns nothing",
			"native GetHandleId takes handle h returns integer",
			"");

	private static final class Unit extends AllocatedHandle {
	}

	private final List<Unit> units = new ArrayList<>();
	private int nextId = 0x100000;
	private int lastUnitXArgument = -1;

	private static LuaEnvironment.Host host() {
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
				return 0;
			}

			@Override
			public long startTimeSeconds() {
				return 1_000_000;
			}
		};
	}

	private LuaEnvironment environment() throws Exception {
		final JassProgram program = new JassProgram();
		final GlobalScope globals = program.getGlobalScope();
		final HandleJassType unit = globals.registerHandleType("unit");
		final HandleJassType timer = globals.registerHandleType("timer");
		final HandleJassType effect = globals.registerHandleType("effect");
		final var natives = program.getJassNativeManager();
		natives.createNative("CreateUnit", (arguments, scope, t) -> {
			final Unit made = new Unit();
			made.setHandleId(this.nextId++);
			this.units.add(made);
			return new HandleJassValue(unit, made);
		});
		natives.createNative("RemoveUnit", (arguments, scope, t) -> null);
		natives.createNative("GetUnitX", (arguments, scope, t) -> {
			this.lastUnitXArgument = ((HandleJassValue) arguments.get(0)).getJavaValue() == null ? 0 : 1;
			return IntegerJassValue.of(7);
		});
		natives.createNative("CreateTimer", (arguments, scope, t) -> new HandleJassValue(timer, new Object()));
		natives.createNative("DestroyTimer", (arguments, scope, t) -> null);
		natives.createNative("AddSpecialEffect", (arguments, scope, t) -> new HandleJassValue(effect, new Object()));
		natives.createNative("DestroyEffect", (arguments, scope, t) -> null);
		natives.createNative("GetHandleId", (arguments, scope, t) -> IntegerJassValue
				.of(((Unit) ((HandleJassValue) arguments.get(0)).getJavaValue()).getHandleId()));
		new SmashJassParser(new StringReader(COMMON)).scanAndParse("common.j", program);
		program.initialize();
		return new LuaEnvironment(globals, host());
	}

	private static void run(final LuaEnvironment lua, final String... lines) {
		lua.runNow(lua.loadChunk(String.join("\n", lines).getBytes(StandardCharsets.UTF_8), "test"));
	}

	private static long global(final LuaEnvironment lua, final String name) {
		lua.lua().getGlobal(name);
		final long value = lua.lua().toInteger(-1);
		lua.lua().pop(1);
		return value;
	}

	@Test
	void tenThousandCreatedAndDestroyedObjectsLeaveNothingHeld() throws Exception {
		try (LuaEnvironment lua = environment()) {
			run(lua,
					"for i = 1, 10000 do",
					"  local u = CreateUnit()",
					"  local t = CreateTimer()",
					"  local e = AddSpecialEffect()",
					"  DestroyEffect(e)",
					"  DestroyTimer(t)",
					"  RemoveUnit(u)",
					"end");
			assertEquals(30000, lua.handles().size());
			assertEquals(0, lua.handles().liveSize());
		}
	}

	@Test
	void objectsTheGameDestroysOnItsOwnAreReleasedToo() throws Exception {
		try (LuaEnvironment lua = environment()) {
			run(lua, "units = {} for i = 1, 5000 do units[i] = CreateUnit() end");
			assertEquals(5000, lua.handles().liveSize());
			for (final Unit made : this.units) {
				assertTrue(lua.released(made));
			}
			assertEquals(0, lua.handles().liveSize());
		}
	}

	@Test
	void aDestroyedObjectIsANullHandleToNativesAndItsIdIsStillThere() throws Exception {
		try (LuaEnvironment lua = environment()) {
			run(lua,
					"u = CreateUnit()",
					"n = GetUnitX(u)",
					"RemoveUnit(u)",
					"id = GetHandleId(u)",
					"x = GetUnitX(u)");
			assertEquals(0x100000, global(lua, "id"));
			assertEquals(0, this.lastUnitXArgument);
			assertEquals(7, global(lua, "x"));
		}
	}

	@Test
	void numbersAreNotGivenAgainAfterAnObjectIsDestroyed() throws Exception {
		try (LuaEnvironment lua = environment()) {
			run(lua,
					"a = CreateUnit()",
					"RemoveUnit(a)",
					"b = CreateUnit()",
					"same = (a == b) and 1 or 0");
			assertEquals(0, global(lua, "same"));
		}
	}

	@Test
	void aSavedGameKeepsTheNumbersOfHeldAndOfDestroyedObjects() {
		final HandleJassType type = new HandleJassType(null, "unit");
		final Unit first = new Unit();
		first.setHandleId(0x100000);
		final Unit second = new Unit();
		second.setHandleId(0x100001);
		final LuaHandles before = new LuaHandles();
		before.idOf(new HandleJassValue(type, first));
		before.idOf(new HandleJassValue(type, second));
		before.release(first);

		final LuaHandles after = new LuaHandles();
		after.restore(before.snapshot(), before.snapshotReleasedIds());
		assertEquals(2, after.size());
		assertEquals(1, after.liveSize());
		assertTrue(after.isReleased(1));
		assertEquals(0x100000, after.releasedHandleId(1));
		assertEquals(2, after.idOf(new HandleJassValue(type, second)));
		// A corpse the game hands out again has the number it had.
		assertEquals(1, after.idOf(new HandleJassValue(type, first)));
		assertEquals(1, after.liveSize());
	}

	@Test
	void aHeldObjectKeepsItsNumber() throws Exception {
		try (LuaEnvironment lua = environment()) {
			run(lua,
					"a = CreateUnit()",
					"b = CreateUnit()",
					"RemoveUnit(b)",
					"id = GetHandleId(a)");
			assertEquals(1, lua.handles().liveSize());
			assertEquals(0x100000, global(lua, "id"));
		}
	}
}
