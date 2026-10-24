package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class FolderDataSource implements DataSource {

	private final Path folderPath;
	private final Set<String> listfile;
	/**
	 * Lower case relative path to the path as stored on disk. Lookups are frequent
	 * and mostly miss, so they must not touch the disk, and game data paths do not
	 * match the case of the files on case sensitive file systems.
	 */
	private final Map<String, String> lowerCaseToStoredPath;

	public FolderDataSource(final Path folderPath) {
		this.folderPath = folderPath;
		this.listfile = new HashSet<>();
		this.lowerCaseToStoredPath = new HashMap<>();
		try {
			Files.walk(folderPath).filter(Files::isRegularFile).forEach(new Consumer<Path>() {
				@Override
				public void accept(final Path t) {
					final String relativePath = folderPath.relativize(t).toString();
					FolderDataSource.this.listfile.add(relativePath);
					FolderDataSource.this.lowerCaseToStoredPath.put(relativePath.toLowerCase(Locale.US), relativePath);
				}
			});
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public InputStream getResourceAsStream(String filepath) throws IOException {
		final String storedPath = findStoredPath(filepath);
		if (storedPath == null) {
			return null;
		}
		return Files.newInputStream(this.folderPath.resolve(storedPath), StandardOpenOption.READ);
	}

	@Override
	public File getFile(String filepath) throws IOException {
		final String storedPath = findStoredPath(filepath);
		if (storedPath == null) {
			return null;
		}
		return new File(this.folderPath.toString() + File.separatorChar + storedPath);
	}

	@Override
	public File getDirectory(String filepath) throws IOException {
		filepath = fixFilepath(filepath);
		File file = new File(this.folderPath.toString() + File.separatorChar + filepath);
		if(!file.exists() || !file.isDirectory()) {
			return null;
		}
		return file;
	}

	@Override
	public ByteBuffer read(String path) throws IOException {
		final String storedPath = findStoredPath(path);
		if (storedPath == null) {
			return null;
		}
		return ByteBuffer.wrap(Files.readAllBytes(Paths.get(this.folderPath.toString(), storedPath)));
	}

	@Override
	public boolean has(final String filepath) {
		return findStoredPath(filepath) != null;
	}

	private String findStoredPath(final String filepath) {
		return this.lowerCaseToStoredPath.get(fixFilepath(filepath).toLowerCase(Locale.US));
	}

	@Override
	public Collection<String> getListfile() {
		return this.listfile;
	}

	@Override
	public void close() {
	}

	private static String fixFilepath(final String filepath) {
		return filepath.replace('\\', File.separatorChar).replace('/', File.separatorChar).replace(':',
				File.separatorChar);
	}
}
