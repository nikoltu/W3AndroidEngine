package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderDataSourceTest {
	@TempDir
	File temp;

	@Test
	void aFileAddedRightAfterTheFolderWasListedIsListed() throws Exception {
		FolderSignature.settleMillis = 2500;
		for (int i = 0; i < 200; i++) {
			final File folder = new File(this.temp, "f" + i);
			assertTrue(folder.mkdirs());
			Files.write(new File(folder, "a.txt").toPath(), new byte[] { 1 });
			final FolderDataSource source = new FolderDataSource(folder.toPath());
			assertTrue(source.getListfile().contains("a.txt"));
			Files.write(new File(folder, "b.txt").toPath(), new byte[] { 2 });
			assertTrue(source.getListfile().contains("b.txt"), "round " + i);
		}
	}

	@Test
	void aDirectoryOutsideTheFolderIsNotReturned() throws Exception {
		final File root = new File(this.temp, "root");
		assertTrue(new File(root, "inside").mkdirs());
		assertTrue(new File(this.temp, "sibling").mkdirs());
		final FolderDataSource source = new FolderDataSource(root.toPath());
		assertNotNull(source.getDirectory("inside"));
		assertNotNull(source.getDirectory("inside\\..\\inside"));
		assertNull(source.getDirectory(".."));
		assertNull(source.getDirectory("..\\sibling"));
		assertNull(source.getDirectory("../sibling"));
		assertNull(source.getDirectory(this.temp.getAbsolutePath()));
	}

	private static final String BACK = File.separator;

	private File game;
	private File index;

	@BeforeEach
	void keepIndexesIn() throws Exception {
		// Times of folders read a few milliseconds ahead of the clock must not keep a list from being made.
		FolderSignature.settleMillis = -1000;
		this.index = new File(this.temp, "kept");
		this.game = new File(this.temp, "game");
		FolderIndexStore.use(this.index);
		for (int i = 0; i < FolderIndexStore.MIN_FILES; i++) {
			write(this.game, "units/u" + (i / 50) + "/f" + i + ".txt");
		}
		write(this.game, "units/UnitData.slk");
		write(this.game, "ui/WorldEditGameStrings.txt");
		age(this.game);
		FolderDataSource.forgetIndexes();
	}

	@AfterEach
	void stopKeeping() {
		FolderSignature.settleMillis = 2500;
		FolderIndexStore.use(null);
		FolderDataSource.forgetIndexes();
	}

	private static void write(final File root, final String name) throws Exception {
		final File file = new File(root, name);
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), name.getBytes(StandardCharsets.UTF_8));
	}

	/** Folder times of a minute ago, as a folder that nothing has touched for a while. */
	private static void age(final File folder) {
		final File[] children = folder.listFiles();
		if (children != null) {
			for (final File child : children) {
				if (child.isDirectory()) {
					age(child);
				}
			}
		}
		folder.setLastModified(System.currentTimeMillis() - 60_000);
	}

	/** A new start of the app: nothing is in memory, what was kept in files is. */
	private FolderDataSource restart(final File folder) {
		FolderDataSource.forgetIndexes();
		return new FolderDataSource(folder.toPath());
	}

	/** The walks a new start of the app makes of the folder. */
	private int walksOf(final File folder) {
		final int before = FolderDataSource.WALKS.get();
		final FolderDataSource source = restart(folder);
		assertFalse(source.getListfile().isEmpty());
		return FolderDataSource.WALKS.get() - before;
	}

	private boolean somethingKept() {
		return this.index.isDirectory() && (this.index.list((dir, name) -> name.endsWith(".txt")).length > 0);
	}

	@Test
	void aLargeFolderIsNotWalkedAgainAfterTheAppRestarts() throws Exception {
		assertEquals(1, walksOf(this.game));
		assertTrue(somethingKept());
		assertEquals(0, walksOf(this.game));
		final FolderDataSource first = new FolderDataSource(this.game.toPath());
		final FolderDataSource second = restart(this.game);
		assertTrue(second.has("UNITS" + BACK + "UnitData.SLK"));
		assertTrue(second.has("units/u3/f150.txt"));
		assertNotNull(second.getFile("ui/worldeditgamestrings.txt"));
		assertEquals(new java.util.HashSet<>(first.getListfile()), new java.util.HashSet<>(second.getListfile()));
	}

	@Test
	void aSmallFolderIsNotKept() throws Exception {
		final File small = new File(this.temp, "small");
		write(small, "a.txt");
		age(small);
		new FolderDataSource(small.toPath());
		assertFalse(somethingKept());
	}

	@Test
	void aFileAddedIsFoundAfterTheRestart() throws Exception {
		walksOf(this.game);
		write(this.game, "units/u3/added.txt");
		assertEquals(1, walksOf(this.game));
		assertTrue(restart(this.game).has("units/u3/added.txt"));
		age(this.game);
		assertEquals(1, walksOf(this.game));
		assertEquals(0, walksOf(this.game));
		assertTrue(restart(this.game).has("units/u3/added.txt"));
	}

	@Test
	void aFileRemovedOrReplacedIsFoundAfterTheRestart() throws Exception {
		walksOf(this.game);
		assertTrue(new File(this.game, "units/u3/f150.txt").delete());
		assertEquals(1, walksOf(this.game));
		assertFalse(restart(this.game).has("units/u3/f150.txt"));
		age(this.game);
		assertEquals(1, walksOf(this.game));
		// A file replaced by another of the same name, put in place by a rename.
		write(this.game, "units/u4/new.tmp");
		Files.move(new File(this.game, "units/u4/new.tmp").toPath(), new File(this.game, "units/u4/f200.txt").toPath(),
				java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		assertEquals(1, walksOf(this.game));
		age(this.game);
		assertEquals(1, walksOf(this.game));
		// A file renamed to another letter case.
		assertTrue(new File(this.game, "units/u4/f201.txt").renameTo(new File(this.game, "units/u4/F201.TXT")));
		assertEquals(1, walksOf(this.game));
		final String stored = restart(this.game).getFile("units/u4/f201.txt").getPath();
		assertTrue(stored.endsWith("F201.TXT"), stored);
	}

	@Test
	void aLanguagePackImportedIntoTheFolderIsFound() throws Exception {
		walksOf(this.game);
		write(this.game, "_locales/eses.w3mod/ui/WorldEditGameStrings.txt");
		write(this.game, "_locales/eses.w3mod/units/CampaignUnitStrings.txt");
		assertEquals(1, walksOf(this.game));
		assertTrue(restart(this.game).has("_locales/eses.w3mod/ui/WorldEditGameStrings.txt"));
		age(this.game);
		assertEquals(1, walksOf(this.game));
		assertEquals(0, walksOf(this.game));
		// A set put into a folder that was there before, such as a graphics set below the units.
		write(this.game, "units/u3/hd/model.mdx");
		assertEquals(1, walksOf(this.game));
		assertTrue(restart(this.game).has("units/u3/hd/model.mdx"));
	}

	@Test
	void theLauncherCheckLeavesTheListOfFilesSoTheSourceDoesNotWalkAgain() throws Exception {
		com.etheller.warsmash.launcher.DataFolderCheck.readability(this.game, Integer.MAX_VALUE, 3);
		assertTrue(somethingKept());
		final java.util.Set<String> fromCheck = new java.util.HashSet<>(restart(this.game).getListfile());
		assertEquals(0, walksOf(this.game));
		for (final File kept : this.index.listFiles()) {
			assertTrue(kept.delete());
		}
		assertEquals(1, walksOf(this.game));
		assertEquals(fromCheck, new java.util.HashSet<>(restart(this.game).getListfile()));
		assertEquals(FolderIndexStore.MIN_FILES + 2, fromCheck.size());
	}

	@Test
	void anIndexMadeAnewAfterAnImportIsKeptForTheNextStart() throws Exception {
		walksOf(this.game);
		write(this.game, "_locales/eses.w3mod/ui/WorldEditGameStrings.txt");
		age(this.game);
		FolderDataSource.reindex(this.game);
		assertEquals(0, walksOf(this.game));
		assertTrue(restart(this.game).has("_locales/eses.w3mod/ui/WorldEditGameStrings.txt"));
	}

	@Test
	void aFolderChangedAMomentAgoIsWaitedForBeforeItsIndexIsKept() throws Exception {
		FolderSignature.settleMillis = 300;
		walksOf(this.game);
		write(this.game, "units/u3/fresh.txt");
		final long started = System.currentTimeMillis();
		FolderDataSource.reindex(this.game);
		assertTrue(System.currentTimeMillis() - started < 3000);
		assertEquals(0, walksOf(this.game));
		assertTrue(restart(this.game).has("units/u3/fresh.txt"));
	}

	@Test
	void aFolderChangedAMomentAgoIsWalkedOnceMoreWhenItHasSettledNotAtEveryCall() throws Exception {
		walksOf(this.game);
		FolderSignature.settleMillis = 600;
		write(this.game, "units/u3/fresh.txt");
		FolderDataSource.forgetIndexes();
		final int before = FolderDataSource.WALKS.get();
		final FolderDataSource source = new FolderDataSource(this.game.toPath());
		for (int i = 0; i < 5; i++) {
			assertTrue(source.getListfile().contains("units" + BACK + "u3" + BACK + "fresh.txt"));
		}
		assertEquals(1, FolderDataSource.WALKS.get() - before);
		Thread.sleep(700);
		for (int i = 0; i < 5; i++) {
			source.getListfile();
		}
		assertEquals(2, FolderDataSource.WALKS.get() - before);
		Thread.sleep(700);
		source.getListfile();
		assertEquals(2, FolderDataSource.WALKS.get() - before);
	}

	@Test
	void aChangeWhileTheFolderIsStillSettlingIsFound() throws Exception {
		FolderSignature.settleMillis = 5000;
		write(this.game, "units/u3/fresh.txt");
		FolderDataSource.forgetIndexes();
		final FolderDataSource source = new FolderDataSource(this.game.toPath());
		final int before = FolderDataSource.WALKS.get();
		source.getListfile();
		assertEquals(0, FolderDataSource.WALKS.get() - before);
		write(this.game, "units/u3/another.txt");
		new File(this.game, "units/u3").setLastModified(System.currentTimeMillis() + 3000);
		assertTrue(source.getListfile().contains("units" + BACK + "u3" + BACK + "another.txt"));
	}

	@Test
	void aKeyFileThatChangedMakesTheFolderBeWalkedAgain() throws Exception {
		walksOf(this.game);
		assertEquals(0, walksOf(this.game));
		Files.write(new File(this.game, "units/UnitData.slk").toPath(),
				"a longer file".getBytes(StandardCharsets.UTF_8));
		assertEquals(1, walksOf(this.game));
	}

	@Test
	void aListOfAnotherFolderIsNotUsed() throws Exception {
		walksOf(this.game);
		final File other = new File(this.temp, "other");
		for (int i = 0; i < FolderIndexStore.MIN_FILES; i++) {
			write(other, "x/y" + i + ".txt");
		}
		age(other);
		final File[] kept = this.index.listFiles((dir, name) -> name.endsWith(".txt"));
		assertEquals(1, kept.length);
		// The list of the first folder where the other's belongs, as if their names had hashed alike.
		final File name = new File(this.index,
				"folder-index-" + Integer.toHexString(other.getAbsolutePath().hashCode()) + ".txt");
		Files.copy(kept[0].toPath(), name.toPath());
		final FolderDataSource source = restart(other);
		assertTrue(source.has("x/y5.txt"));
		assertFalse(source.has("units/UnitData.slk"));
	}

	@Test
	void aDamagedOrCutFileIsIgnored() throws Exception {
		walksOf(this.game);
		final File kept = this.index.listFiles((dir, name) -> name.endsWith(".txt"))[0];
		final byte[] bytes = Files.readAllBytes(kept.toPath());
		Files.write(kept.toPath(), Arrays.copyOf(bytes, bytes.length / 2));
		assertEquals(1, walksOf(this.game));
		Files.write(kept.toPath(), "warsmash-folder-index 1".getBytes(StandardCharsets.UTF_8));
		assertEquals(1, walksOf(this.game));
		Files.write(kept.toPath(), "nonsense".getBytes(StandardCharsets.UTF_8));
		assertEquals(1, walksOf(this.game));
	}

	@Test
	void aFolderThatChangedTooRecentlyIsNotKept() throws Exception {
		FolderSignature.settleMillis = 600_000;
		assertTrue(walksOf(this.game) >= 1);
		assertFalse(somethingKept());
		assertTrue(walksOf(this.game) >= 1);
	}
}
