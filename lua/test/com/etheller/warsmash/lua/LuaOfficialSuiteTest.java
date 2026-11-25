package com.etheller.warsmash.lua;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the files of the official Lua 5.3 test suite that do not depend on io, os or the C test library. The
 * directory comes from the system property warsmash.lua.tests (the suite is not part of the repository); the tests
 * are skipped without it.
 */
class LuaOfficialSuiteTest {
	// Files that cannot run on this build, with the reason. The first assertions of the partly running ones pass
	// (calls up to line 242, goto 165, literals 266, coroutine 238, errors 141).
	private static final Map<String, String> SKIPPED = Map.ofEntries(Map.entry("gc.lua", "tests finalizers, weak tables and collector timing"),
			Map.entry("closure.lua", "waits for the collector to free weakly held objects, which never happens"),
			Map.entry("calls.lua", "expects load to honour the binary-only mode"),
			Map.entry("goto.lua", "uses debug.upvalueid"), Map.entry("literals.lua", "uses os.setlocale"),
			Map.entry("coroutine.lua", "expects a weak table to lose its entry"), Map.entry("errors.lua", "uses debug.upvalueid"),
			Map.entry("api.lua", "needs the C test library"), Map.entry("db.lua", "uses debug hooks"),
			Map.entry("attrib.lua", "tests package and require"), Map.entry("files.lua", "tests io"),
			Map.entry("main.lua", "tests the lua executable"), Map.entry("big.lua", "needs the C test library"),
			Map.entry("verybig.lua", "writes files"), Map.entry("code.lua", "needs the C test library"),
			Map.entry("all.lua", "driver"));

	// What the removed libraries would have provided to the tests.
	private static final String PRELUDE = """
			_port = true; _soft = true
			local sink = setmetatable({}, {__index = function(t) return function() return t end end})
			io = {write = function() end, stdout = sink, stderr = sink}
			arg = {}
			package = {loaded = {_G = _G, string = string, math = math, table = table, os = os, debug = debug,
			  coroutine = coroutine, utf8 = utf8}, preload = {}}
			package.loaded.package = package
			function require(name)
			  local m = package.loaded[name]
			  if m == nil and package.preload[name] then m = package.preload[name](name); package.loaded[name] = m end
			  if m == nil then error("module '" .. name .. "' not found") end
			  return m
			end
			""";

	@TestFactory
	List<DynamicTest> officialFiles() throws IOException {
		final String directory = System.getProperty("warsmash.lua.tests", "");
		assumeTrue(!directory.isEmpty() && Files.isDirectory(Path.of(directory)), "warsmash.lua.tests is not set");
		final List<DynamicTest> tests = new ArrayList<>();
		final List<Path> files;
		try (var stream = Files.list(Path.of(directory))) {
			files = stream.filter(p -> p.getFileName().toString().endsWith(".lua")).sorted().toList();
		}
		for (final Path file : files) {
			final String name = file.getFileName().toString();
			if (SKIPPED.containsKey(name)) {
				continue;
			}
			tests.add(DynamicTest.dynamicTest(name, () -> run(file)));
		}
		return tests;
	}

	private static void run(final Path file) throws IOException {
		final byte[] code = Files.readAllBytes(file);
		try (LuaState lua = new LuaState()) {
			lua.setPrintHandler(text -> {
			});
			lua.run(PRELUDE, "=prelude");
			lua.load(code, "@" + file.getFileName());
			try {
				lua.call(0, 0);
			}
			catch (final LuaException e) {
				fail(e.getMessage());
			}
		}
	}
}
