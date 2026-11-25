package com.etheller.warsmash.parsers.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.function.JassNativeManager;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.warsmash.lua.LuaRandomSource;

import net.warsmash.parsers.jass.SmashJassParser;

/** Every native a common.j declares is a function of a Lua script, however the engine registered its code. */
class LuaNativeReportTest {
	private static final JassFunction STUB = (arguments, scope, trigger) -> IntegerJassValue.of(0);

	private static final String COMMON = String.join("\n",
			"native Plain takes integer a returns integer",
			"constant native Constant takes nothing returns integer",
			"native BlzSetThing takes integer a, real r returns nothing",
			"native BlzGetThingX takes integer a returns real",
			"native NoCode takes nothing returns nothing",
			"//  native Commented takes nothing returns nothing",
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

	@Test
	void declarationsAreReadFromCommonJ() {
		assertEquals(Arrays.asList("Plain", "Constant", "BlzSetThing", "BlzGetThingX", "NoCode"),
				LuaNativeReport.declaredNatives(COMMON));
	}

	@Test
	void helperRegisteredNativesAreCallableAndGapsAreNamed() throws Exception {
		final JassProgram program = new JassProgram();
		final JassNativeManager natives = program.getJassNativeManager();
		// The way the engine registers its families of natives: a helper that adds one per name.
		for (final String name : new String[] { "BlzSetThing", "BlzGetThingX" }) {
			addHelper(natives, name);
		}
		natives.createNative("Plain", STUB);
		natives.createNative("Constant", STUB);
		natives.createNative("NotInCommonJ", STUB);
		new SmashJassParser(new StringReader(COMMON)).scanAndParse("common.j", program);
		program.initialize();
		try (LuaEnvironment lua = new LuaEnvironment(program.getGlobalScope(), host())) {
			final LuaNativeReport report = LuaNativeReport.compute(COMMON, lua,
					natives.getUndeclaredNativeNames());
			assertEquals(Arrays.asList("Plain", "Constant", "BlzSetThing", "BlzGetThingX"), report.getCallable());
			assertEquals(Collections.singletonList("NoCode"), report.getWithoutCode());
			assertTrue(report.getHidden().isEmpty());
			assertTrue(report.getNotInProgram().isEmpty());
			assertEquals(Collections.singletonList("NotInCommonJ"), report.getUndeclared());
		}
	}

	private static void addHelper(final JassNativeManager natives, final String name) {
		natives.createNative(name, STUB);
	}

	/**
	 * With the real common.j of the game data (when the checkout has it) and code for every declared native, none of
	 * them may be hidden from a script, whatever types they take.
	 */
	@Test
	void everyNativeOfTheGameCommonJIsCallable() throws Exception {
		final Path common = findCommon();
		assumeTrue(common != null, "no common.j beside the checkout");
		final String text = new String(Files.readAllBytes(common), StandardCharsets.UTF_8);
		final List<String> declared = LuaNativeReport.declaredNatives(text);
		final JassProgram program = new JassProgram();
		final JassNativeManager natives = program.getJassNativeManager();
		for (final String name : declared) {
			natives.createNative(name, STUB);
		}
		new SmashJassParser(new StringReader(text)).scanAndParse("common.j", program);
		program.initialize();
		try (LuaEnvironment lua = new LuaEnvironment(program.getGlobalScope(), host())) {
			final LuaNativeReport report = LuaNativeReport.compute(text, lua, natives.getUndeclaredNativeNames());
			assertTrue(declared.size() > 1000, report.summary());
			assertEquals(declared.size(), report.getCallable().size(), report.detail());
		}
	}

	private static Path findCommon() {
		final String given = System.getProperty("warsmash.test.commonj");
		final String[] candidates = { given, System.getenv("WARSMASH_TEST_COMMONJ"),
				"../../classic-data-3.0.1/scripts/common.j", "../classic-data-3.0.1/scripts/common.j" };
		for (final String candidate : candidates) {
			if ((candidate != null) && Files.isRegularFile(Paths.get(candidate))) {
				return Paths.get(candidate);
			}
		}
		return null;
	}
}
