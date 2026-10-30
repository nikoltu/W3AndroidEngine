package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
		FolderIndex index;
		synchronized (INDEXES) {
			index = INDEXES.get(folderPath);
			if ((index == null) || !index.isCurrent()) {
				index = new FolderIndex(folderPath);
				INDEXES.put(folderPath, index);
			}
		}
		this.listfile = index.listfile;
		this.lowerCaseToStoredPath = index.lowerCaseToStoredPath;
	}

	/**
	 * The data sources are made again for every game, and listing a data folder of tens of thousands of files took
	 * seconds on phones. An index is reused while no folder in it has changed (adding or removing a file changes its
	 * folder's modification time). Folders that cannot be read are left out instead of failing the whole source.
	 */
	private static final Map<Path, FolderIndex> INDEXES = new HashMap<>();

	private static final class FolderIndex {
		private final Set<String> listfile = new HashSet<>();
		private final Map<String, String> lowerCaseToStoredPath = new HashMap<>();
		private final Map<Path, FileTime> folderTimes = new HashMap<>();

		private FolderIndex(final Path folderPath) {
			try {
				Files.walkFileTree(folderPath, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
						FolderIndex.this.folderTimes.put(dir, attrs.lastModifiedTime());
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
						if (attrs.isRegularFile()) {
							final String relativePath = folderPath.relativize(file).toString();
							FolderIndex.this.listfile.add(relativePath);
							FolderIndex.this.lowerCaseToStoredPath.put(relativePath.toLowerCase(Locale.US),
									relativePath);
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
						return FileVisitResult.CONTINUE;
					}
				});
			}
			catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}

		private boolean isCurrent() {
			for (final Map.Entry<Path, FileTime> folder : this.folderTimes.entrySet()) {
				try {
					if (!Files.getLastModifiedTime(folder.getKey()).equals(folder.getValue())) {
						return false;
					}
				}
				catch (final IOException e) {
					return false;
				}
			}
			return true;
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
		return this.lowerCaseToStoredPath.get(lookupKey(filepath));
	}

	// The same key as fixFilepath(filepath).toLowerCase(Locale.US), built in one pass for ASCII paths: every file
	// request asks each folder source in turn, which made the three copies a visible part of game start on phones.
	private static String lookupKey(final String filepath) {
		final int length = filepath.length();
		final char[] key = new char[length];
		for (int i = 0; i < length; i++) {
			char c = filepath.charAt(i);
			if (c >= 128) {
				return fixFilepath(filepath).toLowerCase(Locale.US);
			}
			if ((c == '\\') || (c == '/') || (c == ':')) {
				c = File.separatorChar;
			}
			else if ((c >= 'A') && (c <= 'Z')) {
				c += 'a' - 'A';
			}
			key[i] = c;
		}
		return new String(key);
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
