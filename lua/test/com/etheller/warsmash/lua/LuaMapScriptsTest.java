package com.etheller.warsmash.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Compiles, without running, the war3map.lua of the maps in the directory of the system property warsmash.lua.maps,
 * one {@code <name>.lua} per map. The scripts are not part of the repository; the test is skipped without them.
 */
class LuaMapScriptsTest {
	@TestFactory
	List<DynamicTest> mapScriptsCompile() throws IOException {
		final String directory = System.getProperty("warsmash.lua.maps", "");
		assumeTrue(!directory.isEmpty() && Files.isDirectory(Path.of(directory)), "warsmash.lua.maps is not set");
		final List<DynamicTest> tests = new ArrayList<>();
		try (var stream = Files.list(Path.of(directory))) {
			for (final Path file : stream.filter(p -> p.getFileName().toString().endsWith(".lua")).sorted().toList()) {
				tests.add(DynamicTest.dynamicTest(file.getFileName().toString(), () -> compile(file)));
			}
		}
		assumeTrue(!tests.isEmpty(), "no scripts in " + directory);
		return tests;
	}

	private static void compile(final Path file) throws IOException {
		final byte[] code = Files.readAllBytes(file);
		try (LuaState lua = new LuaState()) {
			final long start = System.nanoTime();
			lua.load(code, "@war3map.lua");
			System.out.println(file.getFileName() + ": compiled " + code.length + " bytes in " + (System.nanoTime() - start) / 1000000
					+ " ms");
			assertEquals(LuaState.TYPE_FUNCTION, lua.type(-1));
		}
	}
}
