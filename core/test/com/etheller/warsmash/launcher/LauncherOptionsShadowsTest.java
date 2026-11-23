package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.datasources.GraphicsSettings;

class LauncherOptionsShadowsTest {
	@TempDir
	File temp;

	private EngineSettings settings() {
		return new EngineSettings(new File(this.temp, "Save"), "p", new File(this.temp, "warsmash.ini"));
	}

	private static LauncherOptions.Row shadows() {
		for (final LauncherOptions.Row row : LauncherOptions.GRAPHICS) {
			if ("unitShadowMode".equals(row.key)) {
				return row;
			}
		}
		throw new AssertionError("no unit shadows row");
	}

	@Test
	void unsetRowShowsBlobUnderClassicGraphicsAndFullUnderHd() {
		final EngineSettings engine = settings();
		assertEquals(1, shadows().value(engine));
		engine.setGraphics(GraphicsSettings.KEY_SET, GraphicsSet.REFORGED.getIniName());
		assertEquals(2, shadows().value(engine));
	}

	@Test
	void aChosenModeOrANamedOneWinsOverTheGraphics() {
		final EngineSettings engine = settings();
		engine.setGraphics(GraphicsSettings.KEY_UNIT_SHADOWS, "Full");
		assertEquals(2, shadows().value(engine));
		engine.setNumber("unitShadowMode", 0);
		assertEquals(0, shadows().value(engine));
	}
}
