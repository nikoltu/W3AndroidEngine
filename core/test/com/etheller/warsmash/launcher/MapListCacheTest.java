package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.FolderDataSource;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.StringBundle;

/** The custom game list kept on disk is used while the map files are the same and made again when they change. */
class MapListCacheTest {
	@TempDir
	File temp;

	private DataTable ini(final File data) throws IOException {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		ini.readTXT(new ByteArrayInputStream(("[DataSources]\nType00=Folder\nPath00=" + data.getPath() + "\n")
				.getBytes(StandardCharsets.UTF_8)), true);
		return ini;
	}

	private static void touch(final File file, final long time) throws IOException {
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), new byte[] { 1, 2, 3 });
		file.setLastModified(time);
	}

	private List<GameCatalog.MapEntry> maps(final DataTable ini, final File data, final File list)
			throws InterruptedException {
		final GameCatalog catalog = new GameCatalog(ini, GameCatalog.dataKey(ini), new File(this.temp, "Save"),
				() -> "Player", list, Runnable::run, new FolderDataSource(data.toPath()));
		final AtomicReference<List<GameCatalog.MapEntry>> answer = new AtomicReference<>();
		final CountDownLatch done = new CountDownLatch(1);
		catalog.maps(maps -> {
			answer.set(maps);
			done.countDown();
		});
		done.await();
		catalog.close();
		return answer.get();
	}

	@Test
	void fingerprintTellsAddedRemovedAndChangedMaps() throws IOException {
		final File data = new File(this.temp, "war3.w3mod");
		touch(new File(data, "Maps/Download/a.w3x"), 1_000_000);
		final DataTable ini = ini(data);
		final String before = GameCatalog.mapFingerprint(ini);
		assertEquals(before, GameCatalog.mapFingerprint(ini));

		final File added = new File(data, "Maps/Download/b.w3m");
		touch(added, 2_000_000);
		final String withAdded = GameCatalog.mapFingerprint(ini);
		assertNotEquals(before, withAdded);

		touch(added, 3_000_000);
		assertNotEquals(withAdded, GameCatalog.mapFingerprint(ini));

		final String changed = GameCatalog.mapFingerprint(ini);
		assertTrue(added.delete());
		assertNotEquals(changed, GameCatalog.mapFingerprint(ini));
	}

	@Test
	void unchangedMapsUseTheKeptListAndANewMapMakesItAgain() throws Exception {
		final File data = new File(this.temp, "war3.w3mod");
		touch(new File(data, "Maps/Download/a.w3x"), 1_000_000);
		final DataTable ini = ini(data);
		final File list = new File(this.temp, "cache/maps.txt");

		// The files here are not maps, so a scan lists nothing.
		assertTrue(maps(ini, data, list).isEmpty());

		// Put a map into the kept list as a scan would have, with the header the current files give.
		final String header = Files.readAllLines(list.toPath()).get(0);
		Files.write(list.toPath(), (header + "\nMaps/Download/a.w3x\t2\tKept\t0\n").getBytes(StandardCharsets.UTF_8));
		final List<GameCatalog.MapEntry> kept = maps(ini, data, list);
		assertEquals(1, kept.size());
		assertEquals("Kept", kept.get(0).name);

		touch(new File(data, "Maps/Download/b.w3x"), 2_000_000);
		assertTrue(maps(ini, data, list).isEmpty());
		assertNotEquals(header, Files.readAllLines(list.toPath()).get(0));
	}
}
