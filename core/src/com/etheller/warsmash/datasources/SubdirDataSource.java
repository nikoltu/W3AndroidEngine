package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public class SubdirDataSource implements DataSource {
	private final DataSource dataSource;
	private final String subdir;

	public SubdirDataSource(final DataSource dataSource, final String subdir) {
		this.dataSource = dataSource;
		this.subdir = subdir;
	}

	@Override
	public DataSource graphicsIndependent() {
		final DataSource inner = this.dataSource.graphicsIndependent();
		return inner == this.dataSource ? this : new SubdirDataSource(inner, this.subdir);
	}

	@Override
	public File getFile(final String filepath) throws IOException {
		return this.dataSource.getFile(this.subdir + filepath);
	}

	@Override
	public File fileOnDisk(final String filepath) {
		return this.dataSource.fileOnDisk(this.subdir + filepath);
	}

	@Override
	public File getDirectory(String filepath) throws IOException {
		return this.dataSource.getDirectory(this.subdir + filepath);
	}

	@Override
	public ByteBuffer read(final String path) throws IOException {
		return this.dataSource.read(this.subdir + path);
	}

	@Override
	public InputStream getResourceAsStream(final String filepath) throws IOException {
		return this.dataSource.getResourceAsStream(this.subdir + filepath);
	}

	@Override
	public IndependentStream.Opener opener(final String filepath) {
		return this.dataSource.opener(this.subdir + filepath);
	}

	@Override
	public boolean has(final String filepath) {
		return this.dataSource.has(this.subdir + filepath);
	}

	@Override
	public String findFirst(final String... filepaths) {
		final String[] subdirPaths = new String[filepaths.length];
		for (int i = 0; i < filepaths.length; i++) {
			subdirPaths[i] = this.subdir + filepaths[i];
		}
		final String found = this.dataSource.findFirst(subdirPaths);
		return found == null ? null : found.substring(this.subdir.length());
	}

	@Override
	public Collection<String> getListfile() {
		final List<String> results = new ArrayList<>();
		for (final String x : this.dataSource.getListfile()) {
			if (x.startsWith(this.subdir)) {
				results.add(x.substring(this.subdir.length()));
			}
		}
		return results;
	}

	@Override
	public Collection<String> getListfile(final Predicate<String> accept) {
		final List<String> results = new ArrayList<>();
		final int length = this.subdir.length();
		final PathFilter filter = PathFilter.of(accept);
		// Filtered by the source below: a package folder holds nearly every path of the game data, and they were all
		// gathered into a set first.
		final PathFilter inFolder = (path, from) -> path.startsWith(this.subdir, from)
				&& filter.test(path, from + length);
		for (final String x : this.dataSource.getListfile(inFolder)) {
			results.add(x.substring(length));
		}
		return results;
	}

	@Override
	public Collection<String> getListfileByExtension(final String extension) {
		final List<String> results = new ArrayList<>();
		final int length = this.subdir.length();
		for (final String x : this.dataSource.getListfileByExtension(extension)) {
			if (x.startsWith(this.subdir) && ((x.length() - length) >= extension.length())) {
				results.add(x.substring(length));
			}
		}
		return results;
	}

	@Override
	public void close() throws IOException {
		this.dataSource.close();
	}
}
