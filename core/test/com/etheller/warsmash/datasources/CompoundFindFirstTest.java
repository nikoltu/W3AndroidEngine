package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * A compound finds the first of several paths as asking its sources' own findFirst from the latest one did, also with
 * compounds and folders of other sources among its sources.
 */
class CompoundFindFirstTest {
	private static final String[] NAMES = { "a.blp", "a.dds", "b.blp", "b.dds", "c.mdx", "d.wav", "d.flac" };

	@Test
	void findsWhatAskingEachSourceInTurnFound() {
		final Random random = new Random(11);
		for (int round = 0; round < 300; round++) {
			final DataSource inner = new CompoundDataSource(Arrays.asList(files(random), files(random)));
			final List<DataSource> sources = Arrays.asList(files(random), new SubdirDataSource(inner, "sub\\"), inner,
					new PrefersLast(files(random)), files(random));
			final CompoundDataSource compound = new CompoundDataSource(sources);
			for (int ask = 0; ask < 20; ask++) {
				final String[] paths = new String[1 + random.nextInt(4)];
				for (int i = 0; i < paths.length; i++) {
					paths[i] = NAMES[random.nextInt(NAMES.length)];
				}
				String expected = null;
				for (int i = sources.size() - 1; (i >= 0) && (expected == null); i--) {
					expected = sources.get(i).findFirst(paths);
				}
				assertEquals(expected, compound.findFirst(paths), Arrays.toString(paths));
				// asked again, from what the compound remembers
				assertEquals(expected, compound.findFirst(paths), Arrays.toString(paths));
			}
		}
	}

	private static Files files(final Random random) {
		final Set<String> names = new HashSet<>();
		for (final String name : NAMES) {
			if (random.nextInt(4) == 0) {
				names.add(name);
			}
			if (random.nextInt(4) == 0) {
				names.add("sub\\" + name);
			}
		}
		return new Files(names);
	}

	/** Chooses the last of the paths it has, as a layered source chooses a packaged path first. */
	private static final class PrefersLast extends Files {
		PrefersLast(final Files files) {
			super(files.names);
		}

		@Override
		public String findFirst(final String... filepaths) {
			for (int i = filepaths.length - 1; i >= 0; i--) {
				if (has(filepaths[i])) {
					return filepaths[i];
				}
			}
			return null;
		}
	}

	private static class Files implements DataSource {
		final Set<String> names;

		Files(final Set<String> names) {
			this.names = names;
		}

		@Override
		public boolean has(final String filepath) {
			return this.names.contains(filepath);
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
		public Collection<String> getListfile() {
			return this.names;
		}

		@Override
		public void close() {
		}
	}
}
