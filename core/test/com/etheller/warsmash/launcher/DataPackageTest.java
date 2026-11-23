package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.MapListContainer;

/** Importing a data package zip, and the test maps the custom game lists leave out. */
class DataPackageTest {
	@TempDir
	File temp;

	private static Map<String, String> gameFiles(final String root, final String entries) {
		final Map<String, String> files = new LinkedHashMap<>();
		files.put(root + "ui/WorldEditGameStrings.txt", "[WorldEditStrings]\n");
		files.put(root + "units/UnitData.slk", "ID;PWXL;N;E\n");
		files.put(root + DataPackage.MARKER, "tool=Reforged Extractor toolkit 0.2.0\ngameVersion=3.0.1\n"
				+ "profile=full-classic\nlocale=enUS\nentries=" + entries + "\n");
		return files;
	}

	private File zip(final String name, final Map<String, String> files) throws IOException {
		final File zip = new File(this.temp, name);
		try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
			for (final Map.Entry<String, String> file : files.entrySet()) {
				out.putNextEntry(new ZipEntry(file.getKey()));
				out.write(file.getValue().getBytes(StandardCharsets.UTF_8));
				out.closeEntry();
			}
		}
		return zip;
	}

	@Test
	void importsAndReplacesTheOldFolder() throws IOException {
		final File target = new File(this.temp, "data");
		target.mkdirs();
		Files.write(new File(target, "stale.txt").toPath(), new byte[] { 1 });
		final long[] last = new long[4];
		try (DataPackage dataPackage = DataPackage.open(zip("p.zip", gameFiles("", "3")))) {
			assertEquals("3.0.1", dataPackage.gameVersion);
			assertEquals("enUS", dataPackage.locale);
			assertEquals(3, dataPackage.fileCount());
			dataPackage.importInto(target, (bytes, total, files, count) -> {
				last[0] = bytes;
				last[1] = total;
				last[2] = files;
				last[3] = count;
			}, () -> false);
		}
		assertEquals(last[1], last[0]);
		assertEquals(3, last[2]);
		assertEquals(3, last[3]);
		assertEquals(DataFolderCheck.Kind.FOLDER, DataFolderCheck.detect(target));
		assertFalse(new File(target, "stale.txt").exists());
		assertFalse(new File(this.temp, "data.importing").exists());
		assertFalse(new File(this.temp, "data.old").exists());
		assertEquals("Reforged Extractor toolkit 0.2.0", DataFolderCheck.packerTool(target));
	}

	@Test
	void takesAPackageZippedWithItsFolder() throws IOException {
		final File target = new File(this.temp, "data");
		try (DataPackage dataPackage = DataPackage.open(zip("p.zip", gameFiles("w3-data-classic/", "3")))) {
			dataPackage.importInto(target, (bytes, total, files, count) -> {
			}, () -> false);
		}
		assertTrue(new File(target, "units/UnitData.slk").isFile());
	}

	@Test
	void refusesZipsThatAreNotWholePackages() throws IOException {
		final Map<String, String> noMarker = gameFiles("", "3");
		noMarker.remove(DataPackage.MARKER);
		assertEquals(DataPackage.Problem.NO_MARKER, problem(zip("a.zip", noMarker)));
		assertEquals(DataPackage.Problem.INCOMPLETE, problem(zip("b.zip", gameFiles("", "4"))));
		final Map<String, String> escaping = gameFiles("", "4");
		escaping.put("../outside.txt", "x");
		assertEquals(DataPackage.Problem.UNSAFE_PATH, problem(zip("c.zip", escaping)));
		final File notZip = new File(this.temp, "d.zip");
		Files.write(notZip.toPath(), "not a zip".getBytes(StandardCharsets.UTF_8));
		assertEquals(DataPackage.Problem.NOT_ZIP, problem(notZip));
		final Map<String, String> noData = new LinkedHashMap<>();
		noData.put(DataPackage.MARKER, "tool=Reforged Extractor toolkit\nentries=1\n");
		try (DataPackage dataPackage = DataPackage.open(zip("e.zip", noData))) {
			final DataPackage.ImportException e = assertThrows(DataPackage.ImportException.class,
					() -> dataPackage.importInto(new File(this.temp, "data"), (bytes, total, files, count) -> {
					}, () -> false));
			assertEquals(DataPackage.Problem.NO_GAME_DATA, e.problem);
		}
		assertFalse(new File(this.temp, "data").exists());
	}

	private static DataPackage.Problem problem(final File zip) {
		try (DataPackage dataPackage = DataPackage.open(zip)) {
			return null;
		}
		catch (final DataPackage.ImportException e) {
			return e.problem;
		}
		catch (final IOException e) {
			return DataPackage.Problem.IO;
		}
	}

	@Test
	void cancellingKeepsTheOldData() throws IOException {
		final File target = new File(this.temp, "data");
		target.mkdirs();
		Files.write(new File(target, "kept.txt").toPath(), new byte[] { 1 });
		try (DataPackage dataPackage = DataPackage.open(zip("p.zip", gameFiles("", "3")))) {
			final DataPackage.ImportException e = assertThrows(DataPackage.ImportException.class,
					() -> dataPackage.importInto(target, (bytes, total, files, count) -> {
					}, () -> true));
			assertEquals(DataPackage.Problem.CANCELLED, e.problem);
		}
		assertTrue(new File(target, "kept.txt").isFile());
		assertFalse(new File(this.temp, "data.importing").exists());
	}

	@Test
	void findsPackagesInAFolder() throws IOException {
		zip("w3-data-classic.zip", gameFiles("", "3"));
		zip("other.zip", new LinkedHashMap<>());
		assertEquals(1, DataPackage.packagesIn(this.temp).size());
	}

	/** A graphics set zip as the packer makes it: its own folder with .nomedia, and a file of the other set. */
	private static Map<String, String> setFiles(final String root, final String set, final String other) {
		final Map<String, String> files = new LinkedHashMap<>();
		files.put(root + other + "/units/shared.blp", "from " + set);
		files.put(root + set + "/.nomedia", "");
		files.put(root + set + "/units/human/footman/footman.mdx", set + " footman");
		files.put(root + set + "/units/fallback.blp", "from " + set);
		return files;
	}

	private static void importZip(final File zip, final File target) throws IOException {
		try (DataPackage dataPackage = DataPackage.open(zip)) {
			dataPackage.importInto(target, (bytes, total, files, count) -> {
			}, () -> false);
		}
	}

	private static String read(final File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	@Test
	void recognisesGraphicsSetZips() throws IOException {
		try (DataPackage dataPackage = DataPackage
				.open(zip("w3-data-set-de.zip", setFiles("", "_de.w3mod", "_hd.w3mod")))) {
			assertEquals("_de.w3mod", dataPackage.graphicsSet);
			assertEquals("DE", dataPackage.setName());
			assertEquals(4, dataPackage.fileCount());
			assertEquals("", dataPackage.tool);
		}
		try (DataPackage dataPackage = DataPackage
				.open(zip("w3-data-set-hd.zip", setFiles("w3-data-set-hd/", "_hd.w3mod", "_de.w3mod")))) {
			assertEquals("HD", dataPackage.setName());
		}
		try (DataPackage dataPackage = DataPackage.open(zip("p.zip", gameFiles("", "3")))) {
			assertEquals(null, dataPackage.graphicsSet);
			assertEquals("", dataPackage.setName());
		}
		final Map<String, String> mixed = setFiles("", "_de.w3mod", "_hd.w3mod");
		mixed.put("units/UnitData.slk", "ID;PWXL;N;E\n");
		assertEquals(DataPackage.Problem.NO_MARKER, problem(zip("mixed.zip", mixed)));
	}

	@Test
	void aGraphicsSetNeedsTheClassicDataFirst() throws IOException {
		final File target = new File(this.temp, "data");
		try (DataPackage dataPackage = DataPackage.open(zip("set.zip", setFiles("", "_de.w3mod", "_hd.w3mod")))) {
			final DataPackage.ImportException e = assertThrows(DataPackage.ImportException.class,
					() -> dataPackage.importInto(target, (bytes, total, files, count) -> {
					}, () -> false));
			assertEquals(DataPackage.Problem.NO_CLASSIC, e.problem);
			assertEquals("DE", e.file);
		}
		assertFalse(new File(target, "_de.w3mod").exists());
		assertFalse(new File(this.temp, "data.importing").exists());
	}

	@Test
	void aGraphicsSetJoinsTheClassicData() throws IOException {
		final File target = new File(this.temp, "data");
		final Map<String, String> classic = gameFiles("", "5");
		classic.put("_de.w3mod/units/fallback.blp", "from classic");
		classic.put("_de.w3mod/units/classiconly.blp", "from classic");
		importZip(zip("classic.zip", classic), target);
		importZip(zip("set.zip", setFiles("", "_de.w3mod", "_hd.w3mod")), target);
		assertEquals(DataFolderCheck.Kind.FOLDER, DataFolderCheck.detect(target));
		assertTrue(new File(target, DataPackage.MARKER).isFile());
		assertEquals("_de.w3mod footman", read(new File(target, "_de.w3mod/units/human/footman/footman.mdx")));
		assertEquals("from _de.w3mod", read(new File(target, "_de.w3mod/units/fallback.blp")));
		assertEquals("from classic", read(new File(target, "_de.w3mod/units/classiconly.blp")));
		assertEquals("from _de.w3mod", read(new File(target, "_hd.w3mod/units/shared.blp")));
		assertFalse(new File(this.temp, "data.importing").exists());
		assertFalse(new File(this.temp, "data.old").exists());

		// The other set joins the same data; what it brings of the first set replaces the same file.
		importZip(zip("set2.zip", setFiles("", "_hd.w3mod", "_de.w3mod")), target);
		assertEquals("_hd.w3mod footman", read(new File(target, "_hd.w3mod/units/human/footman/footman.mdx")));
		assertEquals("from _hd.w3mod", read(new File(target, "_de.w3mod/units/shared.blp")));
		assertEquals("from _de.w3mod", read(new File(target, "_hd.w3mod/units/shared.blp")));
		assertEquals("_de.w3mod footman", read(new File(target, "_de.w3mod/units/human/footman/footman.mdx")));
	}

	@Test
	void aNewerClassicPackageKeepsTheGraphicsSets() throws IOException {
		final File target = new File(this.temp, "data");
		importZip(zip("classic.zip", gameFiles("", "3")), target);
		importZip(zip("set.zip", setFiles("", "_hd.w3mod", "_de.w3mod")), target);
		Files.write(new File(target, "stale.txt").toPath(), new byte[] { 1 });
		final Map<String, String> newer = gameFiles("", "4");
		newer.put("_de.w3mod/units/shared.blp", "from the newer classic");
		importZip(zip("classic2.zip", newer), target);
		assertFalse(new File(target, "stale.txt").exists());
		assertEquals("_hd.w3mod footman", read(new File(target, "_hd.w3mod/units/human/footman/footman.mdx")));
		assertEquals("from the newer classic", read(new File(target, "_de.w3mod/units/shared.blp")));
		assertFalse(new File(this.temp, "data.old").exists());
	}

	@Test
	void setsOfAnImportThatStoppedBeforeTakingThemAreKept() throws IOException {
		final File target = new File(this.temp, "data");
		importZip(zip("classic.zip", gameFiles("", "3")), target);
		final File old = new File(this.temp, "data.old");
		new File(old, "_de.w3mod/units").mkdirs();
		Files.write(new File(old, "_de.w3mod/units/kept.blp").toPath(), new byte[] { 1 });
		importZip(zip("set.zip", setFiles("", "_hd.w3mod", "_de.w3mod")), target);
		assertTrue(new File(target, "_de.w3mod/units/kept.blp").isFile());
		assertFalse(old.exists());
	}

	@Test
	void listsClassicPackagesBeforeGraphicsSets() throws IOException {
		zip("a-set.zip", setFiles("", "_de.w3mod", "_hd.w3mod"));
		zip("w3-data-classic.zip", gameFiles("", "3"));
		final java.util.List<File> packages = DataPackage.packagesIn(this.temp);
		assertEquals(2, packages.size());
		assertEquals("w3-data-classic.zip", packages.get(0).getName());
	}

	private static Map<String, String> languageFiles(final String code, final String marker) {
		final Map<String, String> files = new LinkedHashMap<>();
		final String folder = "_locales/" + code.toLowerCase() + ".w3mod/";
		files.put(folder + "ui/worldeditgamestrings.txt", "[WorldEditStrings]\nWESTRING_X=" + code + "\n");
		files.put(folder + "sound/dialogue/line.flac", "speech " + code);
		files.put(DataPackage.LANGUAGE_MARKER, "tool=Reforged Extractor toolkit 0.2.0\ngameVersion=3.0.1\n"
				+ "profile=full-classic\nlocale=" + code + "\nspeech=yes\nentries=" + marker + "\n");
		return files;
	}

	@Test
	void recognisesLanguagePacks() throws IOException {
		try (DataPackage dataPackage = DataPackage.open(zip("lang.zip", languageFiles("esES", "3")))) {
			assertEquals("esES", dataPackage.language);
			assertEquals("esES", dataPackage.locale);
			assertEquals("3.0.1", dataPackage.gameVersion);
			assertTrue(dataPackage.addsToClassic());
			assertEquals(null, dataPackage.graphicsSet);
			assertEquals(3, dataPackage.fileCount());
		}
		try (DataPackage dataPackage = DataPackage.open(zip("p.zip", gameFiles("", "3")))) {
			assertEquals("", dataPackage.language);
			assertFalse(dataPackage.addsToClassic());
		}
		assertEquals(DataPackage.Problem.INCOMPLETE, problem(zip("short.zip", languageFiles("esES", "9"))));
		final Map<String, String> stray = languageFiles("esES", "4");
		stray.put("units/UnitData.slk", "x");
		assertEquals(DataPackage.Problem.NO_MARKER, problem(zip("stray.zip", stray)));
		final Map<String, String> two = languageFiles("esES", "4");
		two.put("_locales/frfr.w3mod/ui/worldeditgamestrings.txt", "x");
		assertEquals(DataPackage.Problem.NO_MARKER, problem(zip("two.zip", two)));
		final Map<String, String> wrong = languageFiles("esES", "3");
		wrong.put(DataPackage.LANGUAGE_MARKER,
				wrong.get(DataPackage.LANGUAGE_MARKER).replace("locale=esES", "locale=frFR"));
		assertEquals(DataPackage.Problem.NO_MARKER, problem(zip("wrong.zip", wrong)));
		final Map<String, String> empty = new LinkedHashMap<>();
		empty.put(DataPackage.LANGUAGE_MARKER, "tool=x\nentries=1\n");
		assertEquals(DataPackage.Problem.NO_MARKER, problem(zip("empty.zip", empty)));
	}

	@Test
	void aLanguagePackNeedsTheClassicDataFirst() throws IOException {
		final File target = new File(this.temp, "data");
		try (DataPackage dataPackage = DataPackage.open(zip("lang.zip", languageFiles("esES", "3")))) {
			final DataPackage.ImportException e = assertThrows(DataPackage.ImportException.class,
					() -> dataPackage.importInto(target, (bytes, total, files, count) -> {
					}, () -> false));
			assertEquals(DataPackage.Problem.NO_CLASSIC_LANGUAGE, e.problem);
			assertEquals("esES", e.file);
		}
		assertFalse(new File(target, "_locales").exists());
		assertFalse(new File(this.temp, "data.importing").exists());
	}

	@Test
	void aLanguagePackJoinsTheClassicDataAndSurvivesANewerClassic() throws IOException {
		final File target = new File(this.temp, "data");
		importZip(zip("classic.zip", gameFiles("", "3")), target);
		importZip(zip("lang.zip", languageFiles("esES", "3")), target);
		importZip(zip("lang2.zip", languageFiles("frFR", "3")), target);
		final File es = new File(target, "_locales/eses.w3mod");
		assertEquals("[WorldEditStrings]\nWESTRING_X=esES\n", read(new File(es, "ui/worldeditgamestrings.txt")));
		assertTrue(new File(target, "_locales/frfr.w3mod/sound/dialogue/line.flac").isFile());
		assertFalse(new File(target, DataPackage.LANGUAGE_MARKER).exists());
		assertTrue(new File(target, DataPackage.MARKER).isFile());
		assertEquals(java.util.Arrays.asList("enUS", "esES", "frFR"), DataFolderCheck.locales(DataFolderCheck.Kind.FOLDER, target));

		importZip(zip("classic2.zip", gameFiles("", "3")), target);
		assertEquals("[WorldEditStrings]\nWESTRING_X=esES\n", read(new File(es, "ui/worldeditgamestrings.txt")));
		assertFalse(new File(this.temp, "data.old").exists());
	}

	private static int filesBelow(final File folder) {
		int count = 0;
		for (final File child : folder.listFiles()) {
			count += child.isDirectory() ? filesBelow(child) : 1;
		}
		return count;
	}

	private static int entriesBelow(final File folder) {
		int count = 0;
		for (final File child : folder.listFiles()) {
			count += 1 + (child.isDirectory() ? entriesBelow(child) : 0);
		}
		return count;
	}

	@Test
	void anImportMakesTheListOfFilesOfTheFolderAnewForTheFirstGame() throws IOException {
		final File kept = new File(this.temp, "kept");
		com.etheller.warsmash.datasources.FolderIndexStore.use(kept);
		com.etheller.warsmash.datasources.FolderSignature.settleMillis = 0;
		try {
			final File target = new File(this.temp, "Games/WC3i");
			final Map<String, String> classic = gameFiles("", "1103");
			for (int i = 0; i < 1100; i++) {
				classic.put("units/fill/f" + i + ".txt", "x");
			}
			importZip(zip("classic.zip", classic), target);
			assertTrue(listed(kept, "f1099.txt"));
			importZip(zip("lang.zip", languageFiles("esES", "3")), target);
			assertTrue(listed(kept, "eses.w3mod"));
			assertTrue(listed(kept, "f1099.txt"));
		}
		finally {
			com.etheller.warsmash.datasources.FolderSignature.settleMillis = 2500;
			com.etheller.warsmash.datasources.FolderIndexStore.use(null);
		}
	}

	private static boolean listed(final File kept, final String name) throws IOException {
		final File[] lists = kept.listFiles((dir, file) -> file.endsWith(".txt"));
		return (lists != null) && (lists.length == 1) && read(lists[0]).toLowerCase().contains(name.toLowerCase());
	}

	@Test
	void aLanguagePackKeepsWhatWasCountedOfTheFolderAndAddsItsFiles() throws IOException {
		final File target = new File(this.temp, "Games/WC3");
		importZip(zip("classic.zip", gameFiles("", "3")), target);
		final String before = DataFolderCheck.describe(DataFolderCheck.Kind.FOLDER, target);
		DataFolderCheck.readability(target, Integer.MAX_VALUE, 3);
		assertEquals(String.format("%,d files", filesBelow(target)), before);
		importZip(zip("lang.zip", languageFiles("esES", "3")), target);
		// The count after the import is the old count and the added files, not a new walk of the folder.
		final DataFolderCheck.Readability kept = DataFolderCheck.lastReadability(target);
		assertEquals(entriesBelow(target), kept.files);
		assertEquals(0, kept.unreadable);
		assertEquals(String.format("%,d files", filesBelow(target)),
				DataFolderCheck.describe(DataFolderCheck.Kind.FOLDER, target));
		assertEquals(java.util.Arrays.asList("enUS", "esES"), DataFolderCheck.locales(DataFolderCheck.Kind.FOLDER, target));
		// A second pack of the same language replaces files and adds none.
		importZip(zip("lang2.zip", languageFiles("esES", "3")), target);
		assertEquals(String.format("%,d files", filesBelow(target)),
				DataFolderCheck.describe(DataFolderCheck.Kind.FOLDER, target));
		assertEquals(entriesBelow(target), DataFolderCheck.lastReadability(target).files);
	}

	@Test
	void theCachedResultOfAFolderGrowsWithALanguagePackAndIsKeptForTheNextStart() throws IOException {
		final File target = new File(this.temp, "Games/WC3c");
		importZip(zip("classic.zip", gameFiles("", "3")), target);
		com.etheller.warsmash.datasources.FolderSignature.settleMillis = 0;
		DataFolderCheck.useCache(new File(this.temp, "folder-check.txt"));
		try {
			FolderCheckCacheTest.age(target);
			DataFolderCheck.readability(target, Integer.MAX_VALUE, 3);
			importZip(zip("lang.zip", languageFiles("esES", "3")), target);
			final int walks = DataFolderCheck.WALKS.get();
			// A new start of the app: what is left is the file, and it fits the folder as the import left it.
			DataFolderCheck.resetMemory();
			final DataFolderCheck.Readability kept = DataFolderCheck.readability(target, Integer.MAX_VALUE, 3);
			assertEquals(walks, DataFolderCheck.WALKS.get());
			assertEquals(entriesBelow(target), kept.files);
			assertEquals(filesBelow(target), kept.fileCount);
			// A file put into the folder after the import is found.
			Files.write(new File(target, "units/extra.slk").toPath(), new byte[] { 1 });
			FolderCheckCacheTest.touch(new File(target, "units"));
			DataFolderCheck.resetMemory();
			assertEquals(filesBelow(target), DataFolderCheck.readability(target, Integer.MAX_VALUE, 3).fileCount);
			assertEquals(walks + 1, DataFolderCheck.WALKS.get());
		}
		finally {
			com.etheller.warsmash.datasources.FolderSignature.settleMillis = 2500;
			DataFolderCheck.useCache(null);
			DataFolderCheck.resetMemory();
		}
	}

	@Test
	void aFolderChangedFromOutsideBeforeAnImportIsWalkedAgainAfterIt() throws IOException {
		final File target = new File(this.temp, "Games/WC3d");
		importZip(zip("classic.zip", gameFiles("", "3")), target);
		com.etheller.warsmash.datasources.FolderSignature.settleMillis = 0;
		DataFolderCheck.useCache(new File(this.temp, "folder-check2.txt"));
		try {
			FolderCheckCacheTest.age(target);
			DataFolderCheck.readability(target, Integer.MAX_VALUE, 3);
			Files.write(new File(target, "units/outside.slk").toPath(), new byte[] { 1 });
			FolderCheckCacheTest.touch(new File(target, "units"));
			importZip(zip("lang.zip", languageFiles("esES", "3")), target);
			DataFolderCheck.resetMemory();
			final int walks = DataFolderCheck.WALKS.get();
			assertEquals(filesBelow(target), DataFolderCheck.readability(target, Integer.MAX_VALUE, 3).fileCount);
			assertEquals(walks + 1, DataFolderCheck.WALKS.get());
		}
		finally {
			com.etheller.warsmash.datasources.FolderSignature.settleMillis = 2500;
			DataFolderCheck.useCache(null);
			DataFolderCheck.resetMemory();
		}
	}

	@Test
	void aLanguagePackIntoAFolderNotCountedYetLeavesItToBeCounted() throws IOException {
		final File target = new File(this.temp, "Games/WC3b");
		importZip(zip("classic.zip", gameFiles("", "3")), target);
		DataFolderCheck.forget(target);
		importZip(zip("lang.zip", languageFiles("esES", "3")), target);
		assertEquals(null, DataFolderCheck.lastReadability(target));
		assertEquals(String.format("%,d files", filesBelow(target)),
				DataFolderCheck.describe(DataFolderCheck.Kind.FOLDER, target));
	}

	@Test
	void aLanguagePackJoinsAFolderOutsideTheAppFolder() throws IOException {
		final File own = new File(new File(this.temp, "Games"), "My WC3");
		importZip(zip("classic.zip", gameFiles("", "3")), own);
		importZip(zip("lang.zip", languageFiles("esES", "3")), own);
		assertTrue(new File(own, "_locales/eses.w3mod/ui/worldeditgamestrings.txt").isFile());
		assertEquals(java.util.Arrays.asList("enUS", "esES"), DataFolderCheck.locales(DataFolderCheck.Kind.FOLDER, own));
		assertFalse(new File(this.temp, "data/_locales").exists());
	}

	@Test
	void aLanguagePackIntoAFolderThatCannotBeWrittenSaysSo() throws IOException {
		final File own = new File(this.temp, "Locked");
		importZip(zip("classic.zip", gameFiles("", "3")), own);
		own.setWritable(false);
		try {
			org.junit.jupiter.api.Assumptions.assumeFalse(own.canWrite());
			try (DataPackage dataPackage = DataPackage.open(zip("lang.zip", languageFiles("esES", "3")))) {
				final DataPackage.ImportException e = assertThrows(DataPackage.ImportException.class,
						() -> dataPackage.importInto(own, (bytes, total, files, count) -> {
						}, () -> false));
				assertEquals(DataPackage.Problem.NOT_WRITABLE, e.problem);
			}
		}
		finally {
			own.setWritable(true);
		}
	}

	@Test
	void listsLanguagePacksAfterClassicPackages() throws IOException {
		zip("a-lang.zip", languageFiles("esES", "3"));
		zip("w3-data-classic.zip", gameFiles("", "3"));
		final java.util.List<File> packages = DataPackage.packagesIn(this.temp);
		assertEquals(2, packages.size());
		assertEquals("w3-data-classic.zip", packages.get(0).getName());
	}

	@Test
	void readabilityCountsFilesAndKeepsTheFirstNames() throws IOException {
		final File folder = new File(this.temp, "data");
		new File(folder, "units").mkdirs();
		Files.write(new File(folder, "units/UnitData.slk").toPath(), new byte[] { 1 });
		final DataFolderCheck.Readability readability = DataFolderCheck.readability(folder, 100, 3);
		assertEquals(0, readability.unreadable);
		assertEquals(2, readability.files);
		assertFalse(readability.partial);
		assertTrue(DataFolderCheck.readability(folder, 1, 3).partial);
		assertEquals(1, DataFolderCheck.readability(new File(this.temp, "missing"), 100, 3).unreadable);
	}

	@Test
	void testMapsAreNotListed() {
		assertTrue(MapListContainer.isListedMap("Maps\\FrozenThrone\\(2)EchoIsles.w3x"));
		assertTrue(MapListContainer.isListedMap("(2)EchoIsles.w3x"));
		assertFalse(MapListContainer.isListedMap("Maps\\WC3Probes\\chain.w3x"));
		assertFalse(MapListContainer.isListedMap("maps/probemaps/test.w3x"));
		assertFalse(MapListContainer.isListedMap("ProbeMaps\\test.w3x"));
		assertTrue(MapListContainer.isTestMap("ProbeMaps\\test.w3x"));
		assertFalse(MapListContainer.isTestMap("Maps\\Campaign\\probemaps.w3x"));
	}
}
