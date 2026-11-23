package com.etheller.warsmash.parsers.facefx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.datasources.BorrowedDataSource;
import com.etheller.warsmash.datasources.CompoundDataSource;
import com.etheller.warsmash.datasources.DataSource;

/** The game's animsets are listed once for all the maps played on the same data, as the map's own data lists them. */
class FaceFxLibraryListingTest {
	private static final class Listed implements DataSource {
		private final List<String> paths;
		int listings;

		Listed(final String... paths) {
			this.paths = Arrays.asList(paths);
		}

		@Override
		public Collection<String> getListfileByExtension(final String extension) {
			this.listings++;
			return DataSource.super.getListfileByExtension(extension);
		}

		@Override
		public InputStream getResourceAsStream(final String filepath) {
			return null;
		}

		@Override
		public File getFile(final String filepath) {
			return null;
		}

		@Override
		public File getDirectory(final String filepath) {
			return null;
		}

		@Override
		public ByteBuffer read(final String path) {
			return null;
		}

		@Override
		public boolean has(final String filepath) {
			return false;
		}

		@Override
		public Collection<String> getListfile() {
			return this.paths;
		}

		@Override
		public void close() {
		}
	}

	@Test
	void gameAnimsetsAreListedOnceForEveryMapOnTheSameData() {
		final Listed game = new Listed(
				"war3.w3mod\\_hd.w3mod\\_locales\\enus.w3mod\\sound\\dialogue\\faceanimation\\human01\\facialanimation\\arthas.animset_ingame",
				"war3.w3mod\\_de.w3mod\\_locales\\enus.w3mod\\sound\\dialogue\\faceanimation\\human01\\facialanimation\\arthas.animset_ingame",
				"war3.w3mod\\_de.w3mod\\_locales\\enus.w3mod\\sound\\dialogue\\faceanimation\\human02\\facialanimation\\uther.animset_ingame",
				"war3.w3mod\\units\\human\\footman\\footman.mdx");
		final CompoundDataSource base = new CompoundDataSource(Arrays.asList(game));
		final List<String> first = FaceFxLibrary.gameAnimsets(new BorrowedDataSource(base));
		final List<String> second = FaceFxLibrary.gameAnimsets(new BorrowedDataSource(base));
		assertSame(first, second);
		assertEquals(1, game.listings);
		assertEquals(Arrays.asList("sound\\dialogue\\faceanimation\\human01\\facialanimation\\arthas.animset_ingame",
				"sound\\dialogue\\faceanimation\\human02\\facialanimation\\uther.animset_ingame"), first);

		// Other data is listed again.
		final Listed other = new Listed("war3.w3mod\\x\\facialanimation\\jaina.animset_ingame");
		assertEquals(Arrays.asList("x\\facialanimation\\jaina.animset_ingame"),
				FaceFxLibrary.gameAnimsets(new CompoundDataSource(Arrays.asList(other))));
	}

	@Test
	void theListingIsWhatTheMapsDataLists() {
		final Listed game = new Listed(
				"war3.w3mod\\_hd.w3mod\\sound\\faceanimation\\human01\\facialanimation\\arthas.animset_ingame",
				"war3.w3mod\\_hd.w3mod\\sound\\faceanimation\\human02\\facialanimation\\arthas.animset_ingame");
		final Listed map = new Listed("custom\\facialanimation\\villager.animset_ingame");
		final CompoundDataSource mapData = new CompoundDataSource(Arrays.asList(new BorrowedDataSource(game), map));
		final List<String> throughMap = new ArrayList<>();
		for (final String path : mapData.getListfileByExtension(FaceFxAnimIndex.ANIMSET_EXTENSION)) {
			throughMap.add(FaceFxAnimIndex.logicalPath(path));
		}
		final FaceFxLibrary library = new FaceFxLibrary(mapData, new BorrowedDataSource(game), map);
		assertEquals(throughMap, new ArrayList<>(library.listAnimsets()));
	}
}
