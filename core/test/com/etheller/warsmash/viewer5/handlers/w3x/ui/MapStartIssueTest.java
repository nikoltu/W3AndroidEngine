package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.launcher.MapIssue;
import com.etheller.warsmash.launcher.MapIssueException;
import com.etheller.warsmash.parsers.w3x.War3Map;

/** The engine's own refusal of a map it cannot run, which every way of starting one ends in. */
class MapStartIssueTest {
	@TempDir
	File temp;

	private War3Map map(final String script) throws Exception {
		final File folder = new File(this.temp, "maps/" + script.replace('/', '_') + ".w3x");
		final File file = new File(folder, script);
		Files.createDirectories(file.getParentFile().toPath());
		Files.write(file.toPath(), new byte[] { 1 });
		return new War3Map(new FolderDataSource(this.temp.toPath()), folder);
	}

	@Test
	void aLuaMapIsNotRefused() throws Exception {
		assertDoesNotThrow(() -> MenuMapStart.requireJassScript(map("war3map.lua"), "Maps/Lua.w3x"));
	}

	@Test
	void aLuaMapIsRefusedWithItsIssueWhereTheLuaLibraryIsOff() throws Exception {
		System.setProperty("warsmash.lua.disabled", "true");
		try {
			final MapIssueException refused = assertThrows(MapIssueException.class,
					() -> MenuMapStart.requireJassScript(map("war3map.lua"), "Maps/Lua.w3x"));
			assertEquals(MapIssue.LUA, refused.issue());
		}
		finally {
			System.clearProperty("warsmash.lua.disabled");
		}
	}

	@Test
	void aMapWithoutAScriptIsRefusedWithItsIssue() throws Exception {
		final MapIssueException refused = assertThrows(MapIssueException.class,
				() -> MenuMapStart.requireJassScript(map("war3map.w3i"), "Maps/Bare.w3x"));
		assertEquals(MapIssue.NO_SCRIPT, refused.issue());
	}

	@Test
	void aJassMapIsNotRefused() throws Exception {
		assertDoesNotThrow(() -> MenuMapStart.requireJassScript(map("war3map.j"), "Maps/Jass.w3x"));
	}
}
