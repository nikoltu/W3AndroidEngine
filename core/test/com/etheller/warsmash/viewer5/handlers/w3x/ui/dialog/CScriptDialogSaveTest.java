package com.etheller.warsmash.viewer5.handlers.w3x.ui.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.w3x.save.SavedObjectGraph;

/** What a saved game needs of a dialog to stop the game and show it as it was: when it was shown, and its lost title. */
class CScriptDialogSaveTest {
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

	private static CScriptDialog roundTrip(final CScriptDialog dialog) throws IOException {
		final ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
		final SavedObjectGraph.Writer writer;
		try (DataOutputStream bodies = new DataOutputStream(bodyBytes)) {
			writer = new SavedObjectGraph.Writer(new Rules(), bodies);
			writer.writeRoot(dialog);
		}
		final byte[] table = writer.finishTable();
		SavedObjectGraph.checkClassesMatch(table);
		try (DataInputStream bodies = new DataInputStream(new ByteArrayInputStream(bodyBytes.toByteArray()))) {
			return (CScriptDialog) new SavedObjectGraph.Reader(new Rules(), table, bodies).readRoot();
		}
	}

	@Test
	void shownTickAndLostTitleSurviveASave() throws IOException {
		final CScriptDialog dialog = new CScriptDialog(null, null, null);
		dialog.setShownTick(1234);
		dialog.setShownOrder(7);
		dialog.loseTitle(null, null);
		final CScriptDialog loaded = roundTrip(dialog);
		assertEquals(1234, loaded.getShownTick());
		assertEquals(7, loaded.getShownOrder());
		assertTrue(loaded.isTitleLost());
	}

	@Test
	void aNewMessageBringsTheTitleBack() {
		final CScriptDialog dialog = new CScriptDialog(null, null, null);
		dialog.loseTitle(null, null);
		assertTrue(dialog.isTitleLost());
		dialog.setTitle(null, "Victory!");
		assertFalse(dialog.isTitleLost());
		assertEquals("Victory!", dialog.getTitle());
	}

	@Test
	void ofSeveralShownDialogsOnlyTheLastShownStays() {
		final CScriptDialog first = new CScriptDialog(null, null, null);
		final CScriptDialog second = new CScriptDialog(null, null, null);
		final CScriptDialog hidden = new CScriptDialog(null, null, null);
		first.setShownOrder(1);
		first.setVisible(true);
		second.setShownOrder(2);
		second.setVisible(true);
		hidden.setShownOrder(3);
		assertEquals(second, CScriptDialog.shownLast(java.util.Arrays.asList(first, hidden, second)));
		second.setVisible(false);
		assertEquals(first, CScriptDialog.shownLast(java.util.Arrays.asList(first, hidden, second)));
		first.setVisible(false);
		assertEquals(null, CScriptDialog.shownLast(java.util.Arrays.asList(first, hidden, second)));
	}
}
