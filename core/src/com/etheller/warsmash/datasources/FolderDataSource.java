package com.etheller.warsmash.datasources;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.utils.IntSet;
import com.etheller.warsmash.util.Log;

public class FolderDataSource implements DataSource {

	private final Path folderPath;
	private volatile Set<String> listfile;
	/**
	 * Lower case relative path to the path as stored on disk. Lookups are frequent
	 * and mostly miss, so they must not touch the disk, and game data paths do not
	 * match the case of the files on case sensitive file systems.
	 */
	private volatile Map<String, String> lowerCaseToStoredPath;
	/** The hash codes of the keys of {@link #lowerCaseToStoredPath}, which answer most misses without a key. */
	private volatile IntSet keyHashes;

	public FolderDataSource(final Path folderPath) {
		this.folderPath = folderPath;
		useCurrentIndex();
	}

	private void useCurrentIndex() {
		FolderIndex index;
		synchronized (INDEXES) {
			index = INDEXES.get(this.folderPath);
			if ((index == null) || !index.isCurrent()) {
				index = new FolderIndex(this.folderPath);
				INDEXES.put(this.folderPath, index);
			}
		}
		this.keyHashes = index.keyHashes;
		this.lowerCaseToStoredPath = index.lowerCaseToStoredPath;
		this.listfile = index.listfile;
	}

	/**
	 * The data sources are made again for every game, and listing a data folder of tens of thousands of files took
	 * seconds on phones (half a minute over the shared storage). An index is reused while no folder in it has changed
	 * (adding or removing a file changes its folder's modification time), and a large one is kept in a file by
	 * {@link FolderIndexStore} for the next start of the app. Folders that cannot be read are left out instead of
	 * failing the whole source; an index made of such a folder is not kept.
	 */
	private static final Map<Path, FolderIndex> INDEXES = new HashMap<>();

	/** Walks made, for the tests. */
	static final java.util.concurrent.atomic.AtomicInteger WALKS = new java.util.concurrent.atomic.AtomicInteger();

	/** Forgets the index of a folder, in memory and in its file: the folder was changed by something that knows not what. */
	public static void dropIndex(final File folder) {
		synchronized (INDEXES) {
			INDEXES.remove(folder.toPath());
		}
		FolderIndexStore.remove(folder);
	}

	/**
	 * Makes the index of a folder anew after an import changed it, so that the first game after the import does not
	 * walk it. Only where indexes are kept in files; elsewhere the index is made when a game asks for it. A folder
	 * that changed a moment ago is waited for until it can be kept, as the file system's times need.
	 */
	public static void reindex(final File folder) {
		dropIndex(folder);
		if (!FolderIndexStore.isUsed()) {
			return;
		}
		final Path path = folder.toPath();
		try {
			FolderIndex index = new FolderIndex(path);
			if (!index.settled) {
				final long wait = Math.min(FolderSignature.settleMillis, index.millisToSettle(System.currentTimeMillis()));
				Thread.sleep(Math.max(0, wait) + 50);
				index = new FolderIndex(path);
			}
			synchronized (INDEXES) {
				INDEXES.put(path, index);
			}
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		catch (final RuntimeException e) {
			// The import is done; the index is made when a game asks for it.
			Log.warn("The folder index cannot be made after an import: " + e);
		}
	}

	/** Forgets the indexes held in memory, as a new start of the app does; for the tests. */
	static void forgetIndexes() {
		synchronized (INDEXES) {
			INDEXES.clear();
		}
	}

	private static final class FolderIndex {
		// In the order of the walk, so that names that differ in case alone are resolved as they were the first time.
		private final Set<String> listfile = new LinkedHashSet<>();
		private final Map<String, String> lowerCaseToStoredPath = new HashMap<>();
		private final IntSet keyHashes = new IntSet();
		private final FolderSignature signature = new FolderSignature();
		private final File folder;
		/**
		 * False when a folder changed so shortly before the listing that a file added right after it would leave the
		 * modification time as it was (file systems stamp folders coarsely, FAT in two seconds).
		 */
		private boolean settled = true;
		private boolean complete = true;

		private FolderIndex(final Path folderPath) {
			this.folder = folderPath.toFile();
			final FolderIndexStore.Kept kept = FolderIndexStore.load(this.folder);
			if (kept != null) {
				this.signature.copyFrom(kept.signature);
				for (final String relativePath : kept.files) {
					add(relativePath);
				}
				return;
			}
			walk(folderPath);
			if (this.settled && this.complete) {
				FolderIndexStore.save(this.folder, this.signature, this.listfile);
			}
			else {
				FolderIndexStore.remove(this.folder);
			}
		}

		private void add(final String relativePath) {
			this.listfile.add(relativePath);
			final String key = relativePath.toLowerCase(Locale.US);
			this.lowerCaseToStoredPath.put(key, relativePath);
			this.keyHashes.add(key.hashCode());
		}

		private void walk(final Path folderPath) {
			WALKS.incrementAndGet();
			final long listedAt = System.currentTimeMillis();
			try {
				Files.walkFileTree(folderPath, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
						// Read before the folder is listed, so that a change during the walk shows at the next check, and
						// as the signature reads it (File and the attributes round a time of less than a millisecond
						// differently).
						final String relative = FolderSignature.relative(FolderIndex.this.folder, dir.toFile());
						if (relative == null) {
							FolderIndex.this.complete = false;
						}
						else {
							FolderIndex.this.signature.dirs.put(relative, dir.toFile().lastModified());
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
						if (attrs.isRegularFile()) {
							final String relativePath = folderPath.relativize(file).toString();
							add(relativePath);
							final String slashed = relativePath.replace(File.separatorChar, '/');
							// A file that cannot be opened would make the index look changed at every check.
							if (FolderSignature.isOpenedFile(slashed) && FolderSignature.isReadable(file.toFile())) {
								FolderIndex.this.signature.keys.put(slashed,
										new long[] { attrs.size(), file.toFile().lastModified() });
							}
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
						Log.error("Data folder entry left out, it cannot be read: " + exc);
						FolderIndex.this.complete = false;
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
						if (exc != null) {
							Log.error("Data folder listed only in part, it cannot be read: " + exc);
							FolderIndex.this.complete = false;
						}
						return FileVisitResult.CONTINUE;
					}
				});
			}
			catch (final IOException e) {
				throw new RuntimeException(e);
			}
			this.settled = this.signature.settled(listedAt);
		}

		private boolean isCurrent() {
			if (!this.signature.matches(this.folder)) {
				return false;
			}
			// A large folder that had just changed when it was walked is not walked at every call until it has settled
			// (seconds over the shared storage of a phone): its index is used meanwhile and made once more then. A
			// small one is walked at once, so that a file added right after the listing is never missed.
			return this.settled || (this.listfile.size() >= FolderIndexStore.MIN_FILES
					&& (System.currentTimeMillis() - this.signature.newest() < FolderSignature.settleMillis));
		}

		private long millisToSettle(final long now) {
			return FolderSignature.settleMillis - (now - this.signature.newest());
		}
	}

	@Override
	public InputStream getResourceAsStream(String filepath) throws IOException {
		final String storedPath = findStoredPath(filepath);
		if (storedPath == null) {
			return null;
		}
		return new IndependentStream.FromFile(this.folderPath.resolve(storedPath).toFile());
	}

	@Override
	public IndependentStream.Opener opener(final String filepath) {
		final String storedPath = findStoredPath(filepath);
		if (storedPath == null) {
			return null;
		}
		final File file = this.folderPath.resolve(storedPath).toFile();
		return () -> new IndependentStream.FromFile(file);
	}

	@Override
	public File fileOnDisk(final String filepath) {
		final String storedPath = findStoredPath(filepath);
		return storedPath == null ? null : this.folderPath.resolve(storedPath).toFile();
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
		// Paths come from map and data content: ".." or an absolute path must not leave the data folder.
		if (!this.folderPath.resolve(filepath).normalize().startsWith(this.folderPath.normalize())) {
			return null;
		}
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
		// Reads through a channel (Files.readAllBytes, Files.newInputStream) keep a direct buffer as big as the largest
		// read on every thread that made one; Android counts those on the Java heap.
		final File file = new File(this.folderPath.toString(), storedPath);
		try (DataInputStream stream = new DataInputStream(new FileInputStream(file))) {
			final byte[] bytes = new byte[(int) file.length()];
			stream.readFully(bytes);
			return ByteBuffer.wrap(bytes);
		}
	}

	@Override
	public boolean has(final String filepath) {
		return findStoredPath(filepath) != null;
	}

	private String findStoredPath(final String filepath) {
		final int hash = asciiKeyHash(filepath);
		if ((hash != NOT_ASCII) && !this.keyHashes.contains(hash)) {
			return null;
		}
		return this.lowerCaseToStoredPath.get(lookupKey(filepath));
	}

	private static final int NOT_ASCII = 0;

	/**
	 * The hash code of lookupKey(filepath) without making the key, or NOT_ASCII for a path that is not plain ASCII
	 * (or whose key happens to hash to it), which is looked up the long way.
	 */
	private static int asciiKeyHash(final String filepath) {
		final KeyHash last = lastKeyHash;
		if ((last != null) && (last.path == filepath)) {
			return last.hash;
		}
		final int hash = computeAsciiKeyHash(filepath);
		lastKeyHash = new KeyHash(filepath, hash);
		return hash;
	}

	/**
	 * The hash worked out last, on any thread: the game data asks each of its folders for the same path in turn. It
	 * does not change, so another thread's one does no harm; it is used only for the very same path.
	 */
	private static KeyHash lastKeyHash;

	private static final class KeyHash {
		final String path;
		final int hash;

		KeyHash(final String path, final int hash) {
			this.path = path;
			this.hash = hash;
		}
	}

	private static int computeAsciiKeyHash(final String filepath) {
		int hash = 0;
		final int length = filepath.length();
		for (int i = 0; i < length; i++) {
			char c = filepath.charAt(i);
			if (c >= 128) {
				return NOT_ASCII;
			}
			if ((c == '\\') || (c == '/') || (c == ':')) {
				c = File.separatorChar;
			}
			else if ((c >= 'A') && (c <= 'Z')) {
				c += 'a' - 'A';
			}
			hash = (31 * hash) + c;
		}
		return hash;
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
		// A source kept open while files were added or removed (a map dropped into the data folder) lists them anew.
		useCurrentIndex();
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
