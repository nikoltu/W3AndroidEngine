package com.etheller.warsmash.parsers.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.HandleJassType;
import com.etheller.interpreter.ast.value.HandleJassValue;
import com.etheller.warsmash.lua.LuaRandomSource;

import net.warsmash.parsers.jass.SmashJassParser;

/** The functions that triggers, conditions and filters take as code leave the tables when their owner is destroyed. */
class LuaCodeReleaseTest {
	private static final String COMMON = String.join("\n",
			"type trigger extends handle",
			"type boolexpr extends handle",
			"type triggeraction extends handle",
			"type triggercondition extends handle",
			"native CreateTrigger takes nothing returns trigger",
			"native DestroyTrigger takes trigger t returns nothing",
			"native Condition takes code c returns boolexpr",
			"native DestroyBoolExpr takes boolexpr b returns nothing",
			"native TriggerAddAction takes trigger t, code c returns triggeraction",
			"native TriggerRemoveAction takes trigger t, triggeraction a returns nothing",
			"native TriggerAddCondition takes trigger t, boolexpr b returns triggercondition",
			"native TriggerClearActions takes trigger t returns nothing",
			"native TriggerClearConditions takes trigger t returns nothing",
			"");

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

	private static LuaEnvironment environment(final JassProgram program) throws Exception {
		final GlobalScope globals = program.getGlobalScope();
		final HandleJassType trigger = globals.registerHandleType("trigger");
		final HandleJassType boolexpr = globals.registerHandleType("boolexpr");
		final HandleJassType action = globals.registerHandleType("triggeraction");
		final HandleJassType condition = globals.registerHandleType("triggercondition");
		final var natives = program.getJassNativeManager();
		natives.createNative("CreateTrigger", (arguments, scope, t) -> new HandleJassValue(trigger, new Object()));
		natives.createNative("DestroyTrigger", (arguments, scope, t) -> null);
		natives.createNative("Condition", (arguments, scope, t) -> new HandleJassValue(boolexpr, new Object()));
		natives.createNative("DestroyBoolExpr", (arguments, scope, t) -> null);
		natives.createNative("TriggerAddAction", (arguments, scope, t) -> new HandleJassValue(action, new Object()));
		natives.createNative("TriggerRemoveAction", (arguments, scope, t) -> null);
		natives.createNative("TriggerAddCondition", (arguments, scope, t) -> new HandleJassValue(condition,
				new Object()));
		natives.createNative("TriggerClearActions", (arguments, scope, t) -> null);
		natives.createNative("TriggerClearConditions", (arguments, scope, t) -> null);
		new SmashJassParser(new StringReader(COMMON)).scanAndParse("common.j", program);
		program.initialize();
		return new LuaEnvironment(globals, host());
	}

	private static int codes(final LuaEnvironment lua) {
		lua.run("__n = 0 for _ in pairs(__wc.codes) do __n = __n + 1 end", "=count");
		lua.lua().getGlobal("__n");
		final int n = (int) lua.lua().toInteger(-1);
		lua.lua().pop(1);
		return n;
	}

	private static void run(final LuaEnvironment lua, final String... lines) {
		lua.runNow(lua.loadChunk(String.join("\n", lines).getBytes(StandardCharsets.UTF_8), "test"));
	}

	@Test
	void destroyedTriggersAndConditionsFreeTheirCode() throws Exception {
		try (LuaEnvironment lua = environment(new JassProgram())) {
			final int before = codes(lua);
			run(lua,
					"for i = 1, 40 do",
					"  local t = CreateTrigger()",
					"  local c = Condition(function() return true end)",
					"  TriggerAddCondition(t, c)",
					"  TriggerAddAction(t, function() end)",
					"  DestroyBoolExpr(c)",
					"  DestroyTrigger(t)",
					"end");
			assertEquals(before, codes(lua));
		}
	}

	@Test
	void aTriggerKeepsTheCodeOfItsConditionUntilItIsDestroyedEvenIfTheConditionObjectIsNot() throws Exception {
		try (LuaEnvironment lua = environment(new JassProgram())) {
			final int before = codes(lua);
			run(lua,
					"t = CreateTrigger()",
					"local c = Condition(function() return true end)",
					"TriggerAddCondition(t, c)",
					"DestroyBoolExpr(c)");
			assertEquals(before + 1, codes(lua));
			run(lua, "DestroyTrigger(t)");
			assertEquals(before, codes(lua));
		}
	}

	@Test
	void removedAndClearedActionsAndConditionsFreeTheirCode() throws Exception {
		try (LuaEnvironment lua = environment(new JassProgram())) {
			final int before = codes(lua);
			run(lua,
					"local t = CreateTrigger()",
					"local a = TriggerAddAction(t, function() end)",
					"TriggerAddAction(t, function() end)",
					"TriggerRemoveAction(t, a)",
					"TriggerClearActions(t)",
					"local c = Condition(function() return true end)",
					"TriggerAddCondition(t, c)",
					"DestroyBoolExpr(c)",
					"TriggerClearConditions(t)");
			assertEquals(before, codes(lua));
		}
	}

	@Test
	void codeOfAnObjectThatIsNotDestroyedStays() throws Exception {
		try (LuaEnvironment lua = environment(new JassProgram())) {
			final int before = codes(lua);
			run(lua,
					"t = CreateTrigger()",
					"TriggerAddAction(t, function() end)");
			assertEquals(before + 1, codes(lua));
		}
	}
}
