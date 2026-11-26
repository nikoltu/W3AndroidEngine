package com.etheller.warsmash.viewer5.gl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DesktopShaderAdapterTest {
	@Test
	void versionDirectiveIsRemovedButTheShaderBodyStays() {
		assertEquals("void main() {}\n",
				DesktopShaderAdapter.stripVersionDirective("#version 120\r\nvoid main() {}\n"));
		assertEquals("void main() {}\n",
				DesktopShaderAdapter.stripVersionDirective("#version 330 core\nvoid main() {}\n"));
	}

	@Test
	void sourceWithoutVersionDirectiveIsUnchanged() {
		final String source = "varying vec4 v_color;\n";
		assertEquals(source, DesktopShaderAdapter.stripVersionDirective(source));
	}

	@Test
	void prependsDeclareTheCoreProfileCompatibilityMacros() {
		assertTrue(DesktopShaderAdapter.VERTEX_PREPEND.startsWith("#version 330 core\n"));
		assertTrue(DesktopShaderAdapter.VERTEX_PREPEND.contains("#define attribute in\n"));
		assertTrue(DesktopShaderAdapter.VERTEX_PREPEND.contains("#define varying out\n"));

		assertTrue(DesktopShaderAdapter.FRAGMENT_PREPEND.startsWith("#version 330 core\n"));
		assertTrue(DesktopShaderAdapter.FRAGMENT_PREPEND.contains("#define varying in\n"));
		assertTrue(DesktopShaderAdapter.FRAGMENT_PREPEND.contains("out vec4 g_fragColor;\n"));
		assertTrue(DesktopShaderAdapter.FRAGMENT_PREPEND.contains("#define gl_FragColor g_fragColor\n"));
	}

	@Test
	void osCheckMatchesMacOsOnly() {
		assertTrue(DesktopShaderAdapter.isMacDesktop("Mac OS X"));
		assertFalse(DesktopShaderAdapter.isMacDesktop("Windows 11"));
		assertFalse(DesktopShaderAdapter.isMacDesktop("Linux"));
		assertFalse(DesktopShaderAdapter.isMacDesktop(null));
	}
}
