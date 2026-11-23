package com.etheller.warsmash.viewer5;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The helper parses ahead of the builds only up to a budget of file bytes, which a few HD models fill and dozens of
 * Classic ones do not.
 */
class ModelPrepareBudgetTest {
	private static final long MB = 1L << 20;

	@Test
	void oneModelAlwaysStartsHoweverBig() {
		final ModelPrepareBudget budget = new ModelPrepareBudget();
		budget.read(200 * MB);
		assertTrue(budget.admits(0, 0));
	}

	@Test
	void parsedModelsWaitingForTheirBuildStopNewStarts() {
		final ModelPrepareBudget budget = new ModelPrepareBudget();
		assertTrue(budget.admits(0, 20 * MB));
		assertFalse(budget.admits(0, ModelPrepareBudget.MAX_FILE_BYTES));
		assertFalse(budget.admits(1, 30 * MB));
	}

	@Test
	void unreadFilesCountAsTheAverageFileReadSoFar() {
		final ModelPrepareBudget hd = new ModelPrepareBudget();
		for (int i = 0; i < 10; i++) {
			hd.read(6 * MB);
		}
		assertTrue(hd.admits(1, 6 * MB));
		// three queued HD files and two parsed ones are 30 MB of files
		assertFalse(hd.admits(3, 12 * MB));

		final ModelPrepareBudget classic = new ModelPrepareBudget();
		for (int i = 0; i < 10; i++) {
			classic.read(300 << 10);
		}
		// Classic files stay far below the bytes: only the number of queued files is limited
		assertTrue(classic.admits(ModelPrepareBudget.MAX_UNREAD - 1, 6 * MB));
		assertFalse(classic.admits(ModelPrepareBudget.MAX_UNREAD, 0));
	}

	@Test
	void beforeAnyFileIsReadOnlyTheQueueIsLimited() {
		final ModelPrepareBudget budget = new ModelPrepareBudget();
		assertTrue(budget.admits(ModelPrepareBudget.MAX_UNREAD - 1, 0));
		assertFalse(budget.admits(ModelPrepareBudget.MAX_UNREAD, 0));
	}
}
