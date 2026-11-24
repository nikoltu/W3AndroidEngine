package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.function.Predicate;

/** Game data another owner opened, read as it is; closing it here leaves it open for that owner to close. */
public final class BorrowedDataSource implements DataSource {
	private final DataSource data;

	public BorrowedDataSource(final DataSource data) {
		this.data = data;
	}

	@Override
	public DataSource graphicsIndependent() {
		return this.data.graphicsIndependent();
	}

	@Override
	public InputStream getResourceAsStream(final String filepath) throws IOException {
		return this.data.getResourceAsStream(filepath);
	}

	@Override
	public File getFile(final String filepath) throws IOException {
		return this.data.getFile(filepath);
	}

	@Override
	public File fileOnDisk(final String filepath) {
		return this.data.fileOnDisk(filepath);
	}

	@Override
	public File getDirectory(final String filepath) throws IOException {
		return this.data.getDirectory(filepath);
	}

	@Override
	public ByteBuffer read(final String path) throws IOException {
		return this.data.read(path);
	}

	@Override
	public IndependentStream.Opener opener(final String filepath) {
		return this.data.opener(filepath);
	}

	@Override
	public boolean has(final String filepath) {
		return this.data.has(filepath);
	}

	@Override
	public String findFirst(final String... filepaths) {
		return this.data.findFirst(filepaths);
	}

	@Override
	public Collection<String> getListfile() {
		return this.data.getListfile();
	}

	@Override
	public Collection<String> getListfile(final Predicate<String> accept) {
		return this.data.getListfile(accept);
	}

	@Override
	public Collection<String> getListfileByExtension(final String extension) {
		return this.data.getListfileByExtension(extension);
	}

	@Override
	public void close() {
	}
}
