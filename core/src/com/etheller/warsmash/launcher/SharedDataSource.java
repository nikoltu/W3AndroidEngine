package com.etheller.warsmash.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.function.Predicate;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.IndependentStream;

/**
 * Game data opened once and read from two threads, the window's (the launcher and the games it starts) and the
 * catalog's: one read at a time.
 */
public final class SharedDataSource implements DataSource {
	private final DataSource data;

	public SharedDataSource(final DataSource data) {
		this.data = data;
	}

	@Override
	public synchronized InputStream getResourceAsStream(final String filepath) throws IOException {
		return this.data.getResourceAsStream(filepath);
	}

	@Override
	public synchronized File getFile(final String filepath) throws IOException {
		return this.data.getFile(filepath);
	}

	@Override
	public synchronized File fileOnDisk(final String filepath) {
		return this.data.fileOnDisk(filepath);
	}

	@Override
	public synchronized File getDirectory(final String filepath) throws IOException {
		return this.data.getDirectory(filepath);
	}

	@Override
	public synchronized ByteBuffer read(final String path) throws IOException {
		return this.data.read(path);
	}

	@Override
	public synchronized IndependentStream.Opener opener(final String filepath) {
		final IndependentStream.Opener opener = this.data.opener(filepath);
		if (opener == null) {
			return null;
		}
		return () -> {
			synchronized (this) {
				return opener.open();
			}
		};
	}

	@Override
	public synchronized boolean has(final String filepath) {
		return this.data.has(filepath);
	}

	@Override
	public synchronized String findFirst(final String... filepaths) {
		return this.data.findFirst(filepaths);
	}

	@Override
	public synchronized Collection<String> getListfile() {
		return this.data.getListfile();
	}

	@Override
	public synchronized Collection<String> getListfile(final Predicate<String> accept) {
		return this.data.getListfile(accept);
	}

	@Override
	public synchronized Collection<String> getListfileByExtension(final String extension) {
		return this.data.getListfileByExtension(extension);
	}

	@Override
	public synchronized void close() throws IOException {
		this.data.close();
	}
}
