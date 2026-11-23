package com.etheller.warsmash.viewer5.handlers.w3x.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** An interface state of a save from before the cinematic scene and the view switches were kept still loads. */
class SavedInterfaceStateTest {
	private static final class Rules implements SavedObjectGraph.Rules {
		@Override
		public String externalName(final Object object) {
			return null;
		}

		@Override
		public boolean isInPlace(final Object object) {
			return false;
		}

		@Override
		public boolean isDropped(final Object object) {
			return false;
		}

		@Override
		public Object replace(final Object object) {
			return object;
		}

		@Override
		public Object resolveExternal(final String name) {
			return null;
		}

		@Override
		public void reportDropped(final Class<?> holder, final String field, final Object object) {
		}
	}

	private static byte[] bodies;

	private static byte[] table(final Object root) throws IOException {
		final ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
		final SavedObjectGraph.Writer writer;
		try (DataOutputStream out = new DataOutputStream(bodyBytes)) {
			writer = new SavedObjectGraph.Writer(new Rules(), out);
			writer.writeRoot(root);
		}
		bodies = bodyBytes.toByteArray();
		return writer.finishTable();
	}

	private static SavedInterfaceState read(final byte[] table) throws IOException {
		SavedObjectGraph.checkClassesMatch(table);
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bodies))) {
			return (SavedInterfaceState) new SavedObjectGraph.Reader(new Rules(), table, in).readRoot();
		}
	}

	@Test
	void theSceneAndTheViewSwitchesComeBack() throws IOException {
		final SavedInterfaceState state = new SavedInterfaceState();
		state.sceneActive = true;
		state.sceneUnitId = 0x48706161;
		state.sceneColor = 3;
		state.sceneSpeaker = "Arthas";
		state.sceneText = "It ends here.";
		state.sceneDuration = 6.5f;
		state.sceneVoiceover = 5f;
		state.sceneElapsed = 2.25f;
		state.occlusion = false;
		state.worldFogBoundary = false;
		state.waterDeforms = false;
		final SavedInterfaceState loaded = read(table(state));
		assertTrue(loaded.sceneActive);
		assertEquals(0x48706161, loaded.sceneUnitId);
		assertEquals(3, loaded.sceneColor);
		assertEquals("Arthas", loaded.sceneSpeaker);
		assertEquals("It ends here.", loaded.sceneText);
		assertEquals(6.5f, loaded.sceneDuration);
		assertEquals(5f, loaded.sceneVoiceover);
		assertEquals(2.25f, loaded.sceneElapsed);
		assertFalse(loaded.occlusion);
		assertFalse(loaded.worldFogBoundary);
		assertFalse(loaded.waterDeforms);
	}

	@Test
	void aSaveWithoutThemKeepsTheSwitchesOnAndNoScene() throws IOException {
		final InterfaceStateOlder old = new InterfaceStateOlder();
		old.cameraX = 12f;
		old.cinematicMode = true;
		// Same length, so the class table of an engine that had no scene fields is what is written.
		final byte[] table = new String(table(old), StandardCharsets.ISO_8859_1)
				.replace("save.InterfaceStateOlder", "save.SavedInterfaceState").getBytes(StandardCharsets.ISO_8859_1);
		final SavedInterfaceState loaded = read(table);
		assertEquals(12f, loaded.cameraX);
		assertFalse(loaded.sceneActive);
		assertNull(loaded.sceneText);
		assertEquals(-1, loaded.sceneColor);
		assertTrue(loaded.occlusion);
		assertTrue(loaded.worldFogBoundary);
		assertTrue(loaded.waterDeforms);
	}
}

/** The interface state of an older engine, named to be as long as the class that replaced it. */
final class InterfaceStateOlder {
	float cameraX;
	boolean cinematicMode;
}
