package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A walk asks the system whether a file can be read for two files of each kind in a folder, not for every file (each
 * answer is a round trip to another process on the shared storage of a phone), and still reports the unreadable ones.
 */
class ReadabilityRightsTest {
	@TempDir
	File temp;

	private File data;

	@BeforeEach
	void setUp() {
		this.data = new File(this.temp, "data");
		DataFolderCheck.resetMemory();
	}

	@AfterEach
	void tearDown() {
		DataFolderCheck.resetMemory();
	}

	private void write(final String name) throws IOException {
		final File file = new File(this.data, name);
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), name.getBytes(StandardCharsets.UTF_8));
	}

	/** Files named "a_…" are of one kind, "b_…" of another, the rest of none; "locked" ones cannot be read. */
	private static class FakeRights implements DataFolderCheck.Rights {
		int asked;

		@Override
		public Object kindOf(final File file) {
			final String name = file.getName();
			return name.startsWith("a_") ? "A" : name.startsWith("b_") ? "B" : null;
		}

		@Override
		public boolean canRead(final File file) {
			this.asked++;
			return !file.getName().contains("locked");
		}
	}

	/** The same kinds, given with the folder flag (a name without a dot) by one look at each entry. */
	private static class LookingRights extends FakeRights {
		int looks;
		int kindsAsked;

		@Override
		public Object kindOf(final File file) {
			this.kindsAsked++;
			return super.kindOf(file);
		}

		@Override
		public DataFolderCheck.Look look(final File file) {
			this.looks++;
			final String name = file.getName();
			return new DataFolderCheck.Look(!name.contains("."), name.startsWith("a_") ? "A" : name.startsWith("b_") ? "B" : null);
		}
	}

	/** Rights whose answers take as long as set, to see how a walk chooses between asking by kind and asking all. */
	private static final class SlowRights extends LookingRights {
		private final long readNanos;

		SlowRights(final long readNanos) {
			this.readNanos = readNanos;
		}

		@Override
		public boolean timed() {
			return true;
		}

		@Override
		public boolean canRead(final File file) {
			final long until = System.nanoTime() + this.readNanos;
			while (System.nanoTime() < until) {
				Thread.yield();
			}
			return super.canRead(file);
		}
	}

	private DataFolderCheck.Readability walk(final DataFolderCheck.Rights rights) {
		return DataFolderCheck.readability(this.data, Integer.MAX_VALUE, 5, rights);
	}

	@Test
	void twoFilesOfEachKindInAFolderAreAskedFor() throws IOException {
		for (int i = 0; i < 40; i++) {
			write("units/a_" + i + ".slk");
			write("ui/a_" + i + ".txt");
		}
		final FakeRights rights = new FakeRights();
		final DataFolderCheck.Readability readability = walk(rights);
		assertEquals(80, readability.fileCount);
		assertEquals(0, readability.unreadable);
		assertEquals(4, rights.asked);
	}

	@Test
	void anUnreadableFileSecondInItsKindIsFoundByTheSecondAnswer() throws IOException {
		write("units/a_1.slk");
		write("units/a_2_locked.slk");
		write("units/a_3.slk");
		write("units/a_4.slk");
		write("units/a_5.slk");
		final FakeRights rights = new FakeRights();
		final DataFolderCheck.Readability readability = walk(rights);
		assertEquals(Arrays.asList("units/a_2_locked.slk"), readability.names);
		// The kind is trusted from a_3 on, after two readable files: a_4 and a_5 are not asked.
		assertEquals(3, rights.asked);
	}

	@Test
	void anUnreadableFileOfAnotherKindIsStillReported() throws IOException {
		for (int i = 0; i < 40; i++) {
			write("units/a_" + i + ".slk");
		}
		write("units/b_locked.slk");
		final DataFolderCheck.Readability readability = walk(new FakeRights());
		assertEquals(1, readability.unreadable);
		assertEquals(Arrays.asList("units/b_locked.slk"), readability.names);
		DataFolderCheck.resetMemory();
		assertEquals(1, walk(new FakeRights()).unreadable);
	}

	@Test
	void oneLookAtEachEntryGivesItsFolderFlagAndKindAndTheResultIsTheSame() throws IOException {
		for (int i = 0; i < 10; i++) {
			write("units/a_" + i + ".slk");
			write("ui/b_" + i + ".txt");
		}
		write("units/b_locked.slk");
		write("units/x_1.slk");
		final FakeRights plain = new FakeRights();
		final DataFolderCheck.Readability expected = walk(plain);
		DataFolderCheck.resetMemory();
		final LookingRights rights = new LookingRights();
		final DataFolderCheck.Readability actual = walk(rights);
		assertEquals(expected.files, actual.files);
		assertEquals(expected.fileCount, actual.fileCount);
		assertEquals(expected.unreadable, actual.unreadable);
		assertEquals(expected.names, actual.names);
		assertEquals(plain.asked, rights.asked);
		assertEquals(actual.files, rights.looks);
		assertEquals(0, rights.kindsAsked);
	}

	@Test
	void everyFileIsAskedForWhereAnAnswerIsAsCheapAsALook() throws IOException {
		for (int i = 0; i < 300; i++) {
			write("units/a_" + i + ".slk");
		}
		write("units/a_locked.slk");
		final SlowRights rights = new SlowRights(0);
		final DataFolderCheck.Readability readability = walk(rights);
		assertEquals(301, readability.fileCount);
		assertEquals(1, readability.unreadable);
		assertEquals(301, rights.asked);
	}

	@Test
	void filesAreAskedForByKindWhereAnAnswerIsMuchSlowerThanALook() throws IOException {
		for (int i = 0; i < 300; i++) {
			write("units/a_" + i + ".slk");
		}
		write("units/b_locked.slk");
		final SlowRights rights = new SlowRights(300_000);
		final DataFolderCheck.Readability readability = walk(rights);
		assertEquals(301, readability.fileCount);
		assertEquals(1, readability.unreadable);
		// The first files are asked for one by one while the walk measures, then one file of each kind.
		assertTrue(rights.asked < 130, "asked " + rights.asked);
	}

	@Test
	void aKindFoundUnreadableIsAskedForEveryFile() throws IOException {
		write("units/b_locked1.slk");
		write("units/b_locked2.slk");
		write("units/b_open.slk");
		final FakeRights rights = new FakeRights();
		final DataFolderCheck.Readability readability = walk(rights);
		assertEquals(2, readability.unreadable);
		assertEquals(3, rights.asked);
	}

	@Test
	void aFileOfNoKnownKindIsAskedForItself() throws IOException {
		write("units/x_1.slk");
		write("units/x_2.slk");
		write("units/x_locked.slk");
		final FakeRights rights = new FakeRights();
		assertEquals(1, walk(rights).unreadable);
		assertEquals(3, rights.asked);
	}

	@Test
	void theKindIsLearnedForEachFolderAlone() throws IOException {
		write("open/a_1.slk");
		write("closed/a_locked.slk");
		final DataFolderCheck.Readability readability = walk(new FakeRights());
		assertEquals(1, readability.unreadable);
		assertEquals(Arrays.asList("closed/a_locked.slk"), readability.names);
	}

	@Test
	void theSystemsOwnRightsReportAFileWithoutReadRights() throws IOException {
		for (int i = 0; i < 5; i++) {
			write("units/f" + i + ".slk");
		}
		write("units/hidden.slk");
		final File hidden = new File(this.data, "units/hidden.slk");
		Assumptions.assumeTrue(hidden.setReadable(false) && !hidden.canRead());
		try {
			final DataFolderCheck.Readability readability = DataFolderCheck.readability(this.data, Integer.MAX_VALUE, 5);
			assertEquals(1, readability.unreadable);
			assertEquals(Arrays.asList("units/hidden.slk"), readability.names);
		}
		finally {
			hidden.setReadable(true);
		}
	}

	@Test
	void aWalkWithTheSystemsRightsFindsEveryReadableFile() throws IOException {
		for (int i = 0; i < 5; i++) {
			write("units/f" + i + ".slk");
		}
		final DataFolderCheck.Readability readability = DataFolderCheck.readability(this.data, Integer.MAX_VALUE, 5);
		assertEquals(0, readability.unreadable);
		assertFalse(readability.partial);
		assertEquals(5, readability.fileCount);
	}

	/**
	 * What the checks answer for a fixed set of folders. When this fails the checks changed what they say: raise
	 * {@link FolderCheckCache#LOGIC_VERSION}, so that results kept by an older app are read again, and then change
	 * the expected text and the number here. Changing only the text defeats the purpose.
	 */
	@Test
	void theChecksAnswerAsTheirVersionNumberSays() throws IOException {
		assertEquals(3, FolderCheckCache.LOGIC_VERSION, "raise it with the checks, see this test's comment");
		assertEquals(
				"empty: 0 files|0 files|unreadable=0 names=[] partial=false content=[SCRIPTS, REQUIRED_FILES, NO_CAMPAIGN_LIST, NO_MELEE_MAPS]\n"
				+ "melee: 5 files|5 files|unreadable=1 names=[maps/b_locked.w3x] partial=false content=[SCRIPTS, REQUIRED_FILES, NO_CAMPAIGN_LIST]\n"
				+ "scripts: 3 files|3 files|unreadable=0 names=[] partial=false content=[REQUIRED_FILES, NO_CAMPAIGN_LIST, NO_MELEE_MAPS]\n",
				fingerprint());
	}

	private String fingerprint() throws IOException {
		final StringBuilder text = new StringBuilder();
		final String[][] folders = { { "empty" }, { "melee", "Scripts/common.j", "maps/(2)Test.w3x", "maps/a_1.w3x",
				"maps/b_locked.w3x", "ui/WorldEditGameStrings.txt" },
				{ "scripts", "Scripts/common.j", "Scripts/Blizzard.j", "units/UnitData.slk" } };
		for (final String[] spec : folders) {
			final File folder = new File(this.temp, spec[0]);
			folder.mkdirs();
			for (int i = 1; i < spec.length; i++) {
				final File file = new File(folder, spec[i]);
				file.getParentFile().mkdirs();
				Files.write(file.toPath(), new byte[] { 1 });
			}
			this.data = folder;
			final DataFolderCheck.Readability readability = walk(new FakeRights());
			final List<DataContentCheck.Problem> problems = DataContentCheck.check(DataFolderCheck.Kind.FOLDER, folder);
			text.append(spec[0]).append(": ").append(DataFolderCheck.describe(DataFolderCheck.Kind.FOLDER, folder))
					.append('|').append(readability.fileCount).append(" files|unreadable=").append(readability.unreadable)
					.append(" names=").append(readability.names).append(" partial=").append(readability.partial)
					.append(" content=").append(problems).append('\n');
		}
		return text.toString();
	}
}
