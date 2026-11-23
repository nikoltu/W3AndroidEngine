package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** The launcher's texts and their translations. */
class LauncherStringsTest {
	private static ByteArrayInputStream xml(final String body) {
		return new ByteArrayInputStream(("<resources>" + body + "</resources>").getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void aTranslationReplacesOnlyTheTextsItHas() throws IOException {
		final LauncherStrings strings = new LauncherStrings(xml(
				"<string name=\"back\">Back</string><string name=\"delete\">Delete '%1$s'?</string>"));
		strings.overlay(xml("<string name=\"back\">Atrás</string>"));
		assertEquals("Atrás", strings.get("back"));
		assertEquals("Delete 'x'?", strings.get("delete", "x"));
	}
}
