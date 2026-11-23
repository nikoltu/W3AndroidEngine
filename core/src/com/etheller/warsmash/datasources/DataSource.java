package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public interface DataSource {
	/**
	 * Efficiently return a stream instance that will read the data source file's
	 * contents directly from the data source. For example, this will read a file
	 * within an MPQ or CASC storage without extracting it.
	 *
	 * @param filepath
	 * @return
	 * @throws IOException
	 */
	InputStream getResourceAsStream(String filepath) throws IOException;

	/**
	 * Inefficiently copy a file from the data source onto the Hard Drive of the
	 * computer, and then return a java File instance pointed at the file.
	 *
	 * @param filepath
	 * @return
	 * @throws IOException
	 */
	File getFile(String filepath) throws IOException;

	/**
	 * Returns a directory from a FolderDataSource, otherwise returns null
	 *
	 * @param filepath
	 * @return
	 * @throws IOException
	 */
	File getDirectory(String filepath) throws IOException;

	ByteBuffer read(String path) throws IOException;

	/**
	 * Returns true if the data source contains a valid entry for a particular file.
	 * Some data sources (MPQs) may contain files for which this returns true, even
	 * though they cannot list the file in their Listfile.
	 *
	 * @param filepath
	 * @return
	 */
	boolean has(String filepath);

	/**
	 * Returns the first of the given paths that exists, searching layer by layer so
	 * that a higher priority layer wins over the order of the paths.
	 *
	 * @param filepaths
	 * @return the matching path, or null if none exists
	 */
	default String findFirst(final String... filepaths) {
		for (final String filepath : filepaths) {
			if (has(filepath)) {
				return filepath;
			}
		}
		return null;
	}

	/**
	 * The file as {@link #getResourceAsStream} would open it, but opened only when the returned opener is called, on
	 * any thread: a helper thread may open and read it while the game thread uses the data source. The lookup is done
	 * here.
	 *
	 * @return null when the file is missing, or when it can be opened only on the thread that uses the data source (an
	 *         archive read through one shared channel)
	 */
	default IndependentStream.Opener opener(final String filepath) {
		return null;
	}

	/**
	 * @return a list of data source contents, or null if no list is provided
	 */
	Collection<String> getListfile();

	/**
	 * A filter of listed paths that can also judge the end of a path, path.substring(from), without that substring
	 * where its answer does not need it: a folder of a data source asks about the part of each of its paths after the
	 * folder name, and the game data have some hundred thousand paths.
	 */
	interface PathFilter extends Predicate<String> {
		/** @return test(path.substring(from)) */
		boolean test(String path, int from);

		@Override
		default boolean test(final String path) {
			return test(path, 0);
		}

		/** The paths whose part after the start offset is at least as long as the extension and ends in it. */
		static PathFilter endingWith(final String extension) {
			final int length = extension.length();
			return (path, from) -> ((path.length() - from) >= length)
					&& path.regionMatches(true, path.length() - length, extension, 0, length);
		}

		/** The filter as a PathFilter, which for any other filter makes the substring. */
		static PathFilter of(final Predicate<String> accept) {
			if (accept instanceof PathFilter) {
				return (PathFilter) accept;
			}
			return (path, from) -> accept.test(from == 0 ? path : path.substring(from));
		}
	}

	/**
	 * The listed paths that the filter accepts. A data source made of several others answers this without merging
	 * their whole lists, which for the game data are some hundred thousand paths.
	 *
	 * @return the accepted paths, none twice
	 */
	default Collection<String> getListfile(final Predicate<String> accept) {
		final Collection<String> listfile = getListfile();
		if (listfile == null) {
			return Collections.emptyList();
		}
		final List<String> accepted = new ArrayList<>();
		for (final String path : listfile) {
			if (accept.test(path)) {
				accepted.add(path);
			}
		}
		return accepted;
	}

	/**
	 * The listed paths that end in the extension, in any case, as {@link #getListfile(Predicate)} gives them for that
	 * test; a data source that lists a game's files at once answers this from a list it keeps, which the filters of
	 * every folder laid over it each walked the whole of.
	 */
	default Collection<String> getListfileByExtension(final String extension) {
		return getListfile(PathFilter.endingWith(extension));
	}

	/**
	 * This data as the Classic game reads it, without the graphics packages laid over it. What the simulation reads
	 * comes from here, so that players using other graphics sets (and a replay) simulate alike.
	 *
	 * @return this when no graphics package is part of it
	 */
	default DataSource graphicsIndependent() {
		return this;
	}

	/**
	 * Changes whenever the files this source answers for change while it is in use (a source added, the sources
	 * refreshed), so that what was learned about a missing file is not kept past it.
	 */
	default int revision() {
		return 0;
	}

	void close() throws IOException;
}
