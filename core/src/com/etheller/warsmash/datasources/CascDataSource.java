package com.etheller.warsmash.datasources;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.badlogic.gdx.utils.IntSet;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.util.PerfStats;
import com.etheller.warsmash.util.StreamBytes;
import com.hiveworkshop.blizzard.casc.info.Info;
import com.hiveworkshop.blizzard.casc.io.WarcraftIIICASC;
import com.hiveworkshop.blizzard.casc.io.WarcraftIIICASC.FileSystem;
import com.hiveworkshop.blizzard.casc.vfs.VirtualFileSystem.PathResult;
import com.hiveworkshop.json.JSONArray;
import com.hiveworkshop.json.JSONObject;
import com.hiveworkshop.json.JSONTokener;

public class CascDataSource implements DataSource {
	private final PathIndex.Prefix[] indexPrefixes;
	private final Mount mount;
	private Map<String, String> fileAliases = Collections.emptyMap();
	/** The hash codes of the alias sources, so that a path without an alias is looked up without normalizing it. */
	private IntSet aliasHashes = new IntSet();
	private boolean closed;

	public CascDataSource(final String warcraft3InstallPath, final String[] prefixes, final String product,
			final boolean old131Format) {
		for (int i = 0; i < (prefixes.length / 2); i++) {
			final String temp = prefixes[i];
			prefixes[i] = prefixes[prefixes.length - i - 1];
			prefixes[prefixes.length - i - 1] = temp;
		}
		this.indexPrefixes = new PathIndex.Prefix[prefixes.length];
		for (int i = 0; i < prefixes.length; i++) {
			this.indexPrefixes[i] = new PathIndex.Prefix(prefixes[i]);
		}

		try {
			this.mount = Mount.acquire(warcraft3InstallPath, product, old131Format);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
		try {
			if (this.has("filealiases.json")) {
				try (InputStream stream = this.getResourceAsStream("filealiases.json")) {
					this.fileAliases = cachedFileAliases(stream);
					this.aliasHashes = new IntSet(this.fileAliases.size());
					for (final String source : this.fileAliases.keySet()) {
						this.aliasHashes.add(source.hashCode());
					}
				}
			}
		}
		catch (final IOException e) {
			Mount.release(this.mount);
			throw new RuntimeException(e);
		}
		catch (final RuntimeException e) {
			Mount.release(this.mount);
			throw e;
		}
	}

	/**
	 * Fills the map with lower case, backslash separated alias source to target
	 * paths.
	 */
	static void readFileAliases(final InputStream stream, final Map<String, String> fileAliases) throws IOException {
		stream.mark(4);
		if ('\ufeff' != stream.read()) {
			stream.reset(); // not the BOM marker
		}
		final JSONArray jsonObject = new JSONArray(new JSONTokener(stream));
		for (int i = 0; i < jsonObject.length(); i++) {
			final JSONObject alias = jsonObject.getJSONObject(i);
			final String src = alias.getString("src");
			final String dest = alias.getString("dest");
			fileAliases.put(src.toLowerCase(Locale.US).replace('/', '\\'), dest.toLowerCase(Locale.US).replace('/', '\\'));
			if ((src.toLowerCase(Locale.US).contains(".blp") || dest.toLowerCase(Locale.US).contains(".blp"))
					&& (!alias.has("assetType") || "Texture".equals(alias.getString("assetType")))) {
				// This case: I saw a texture that resolves in game but was failing in our code
				// here, because of this entry:
				// {"src":"Units/Human/WarWagon/SiegeEngine.blp",
				// "dest":"Textures/Steamtank.blp", "assetType": "Texture"},
				// Our repo here checks BLP then DDS at a high-up application level thing, and
				// the problem is that this entry is written using .BLP but we must be able to
				// resolve .DDS when we go to look it up. The actual model is .BLP so maybe
				// that's how the game does it, but my alias mapping is happening after the
				// .BLP->.DDS dynamic fix, and not before.
				fileAliases.put(src.toLowerCase(Locale.US).replace('/', '\\').replace(".blp", ".dds"),
						dest.toLowerCase(Locale.US).replace('/', '\\').replace(".blp", ".dds"));
			}
		}
	}

	private static final byte READABLE = 1;
	private static final byte UNREADABLE = 2;
	private static final int PARSED_ALIASES_KEPT = 4;
	private static final int WRITE_CHUNK = 1 << 16;
	/**
	 * A file this big is read while it is decoded instead of all at once: a whole file took a buffer of its stored
	 * size and another of its own, and a long sound read only for its length ran a phone's heap out.
	 */
	private static final long STREAM_MIN_BYTES = 1 << 20;
	private static final int STREAM_BUFFER_BYTES = 1 << 14;
	private static final LinkedList<ParsedAliases> PARSED_ALIASES = new LinkedList<>();

	private static final class ParsedAliases {
		private final byte[] json;
		private final Map<String, String> aliases;

		private ParsedAliases(final byte[] json, final Map<String, String> aliases) {
			this.json = json;
			this.aliases = aliases;
		}
	}

	/**
	 * The aliases of a filealiases.json, read only. Every map opened builds a data source of its own over the game
	 * data, and parsing the same file for each took longer than reading the map.
	 */
	static Map<String, String> cachedFileAliases(final InputStream stream) throws IOException {
		final byte[] bytes = StreamBytes.readAll(stream);
		synchronized (PARSED_ALIASES) {
			for (final Iterator<ParsedAliases> iterator = PARSED_ALIASES.iterator(); iterator.hasNext();) {
				final ParsedAliases parsed = iterator.next();
				if (Arrays.equals(parsed.json, bytes)) {
					iterator.remove();
					PARSED_ALIASES.addFirst(parsed);
					return parsed.aliases;
				}
			}
		}
		final Map<String, String> aliases = new HashMap<>();
		readFileAliases(new ByteArrayInputStream(bytes), aliases);
		final Map<String, String> readOnly = Collections.unmodifiableMap(aliases);
		synchronized (PARSED_ALIASES) {
			PARSED_ALIASES.addFirst(new ParsedAliases(bytes, readOnly));
			while (PARSED_ALIASES.size() > PARSED_ALIASES_KEPT) {
				PARSED_ALIASES.removeLast();
			}
		}
		return readOnly;
	}

	@Override
	public InputStream getResourceAsStream(final String filepath) {
		final String readable = findReadable(filepath);
		return readable == null ? null : internalGetResourceAsStream(readable);
	}

	@Override
	public IndependentStream.Opener opener(final String filepath) {
		final String readable = findReadable(filepath);
		return readable == null ? null : () -> internalGetResourceAsStream(readable);
	}

	/**
	 * The path in the file system that a request is answered from: the first prefix (or no prefix) under which the
	 * file can be read, or null. What is readable does not change while the storage is mounted, and the lookups are
	 * answered from the index of listed paths, so the answer is not kept for each path asked.
	 */
	private String findReadable(final String filepath) {
		final PathIndex.Query path = resolveAlias(filepath);
		for (final PathIndex.Prefix prefix : this.indexPrefixes) {
			if (isReadable(prefix, path)) {
				return prefix.text + "\\" + normalizePath(path.path);
			}
		}
		if (isReadable(null, path)) {
			return normalizePath(path.path);
		}
		return null;
	}

	/**
	 * The alias target of a path, or the path as asked. The index finds a path in any letter case and with either
	 * separator, so it is not normalized unless some alias may apply to it.
	 */
	private PathIndex.Query resolveAlias(final String filepath) {
		final PathIndex.Query query = query(filepath);
		if (this.fileAliases.isEmpty()) {
			return query;
		}
		final int hash = query.normalizedHash;
		if ((hash != NOT_ASCII) && !this.aliasHashes.contains(hash)) {
			return query;
		}
		final String resolvedAlias = this.fileAliases.get(normalizePath(filepath));
		return resolvedAlias != null ? new PathIndex.Query(resolvedAlias) : query;
	}

	private boolean isFound(final String filepath) {
		final PathIndex.Query path = resolveAlias(filepath);
		for (final PathIndex.Prefix prefix : this.indexPrefixes) {
			if (isReadable(prefix, path)) {
				return true;
			}
		}
		return isReadable(null, path);
	}

	static final int NOT_ASCII = 0;

	/**
	 * The query made last, on any thread. The data sources over a storage ask it and then the sources beside it for
	 * the same path, so its hash is often wanted again at once. The query does not change, so a stale or another
	 * thread's one does no harm: it is only used for the very same path.
	 */
	private static PathIndex.Query lastQuery;

	private static PathIndex.Query query(final String path) {
		PathIndex.Query query = lastQuery;
		if ((query == null) || (query.path != path)) {
			query = new PathIndex.Query(path);
			lastQuery = query;
		}
		return query;
	}

	/**
	 * The hash code of normalizePath(path) without making it, or NOT_ASCII for a path that is not plain ASCII (or
	 * whose form happens to hash to it).
	 */
	static int normalizedHash(final String path) {
		final PathIndex.Query query = lastQuery;
		if ((query != null) && (query.path == path)) {
			return query.normalizedHash;
		}
		return normalizedHashUncached(path);
	}

	static int normalizedHashUncached(final String path) {
		int hash = 0;
		final int length = path.length();
		for (int i = 0; i < length; i++) {
			char c = path.charAt(i);
			if (c >= 128) {
				return NOT_ASCII;
			}
			if ((c == '/') || (c == ':')) {
				c = '\\';
			}
			else if ((c >= 'A') && (c <= 'Z')) {
				c += 'a' - 'A';
			}
			hash = (31 * hash) + c;
		}
		return hash;
	}

	/** The path as the storage names files: lower case, with backslashes. */
	static String normalizePath(final String path) {
		final int length = path.length();
		char[] changed = null;
		for (int i = 0; i < length; i++) {
			final char c = path.charAt(i);
			if (c >= 128) {
				return path.toLowerCase(Locale.US).replace('/', '\\').replace(':', '\\');
			}
			char normal = c;
			if ((c == '/') || (c == ':')) {
				normal = '\\';
			}
			else if ((c >= 'A') && (c <= 'Z')) {
				normal = (char) (c + ('a' - 'A'));
			}
			if (normal != c) {
				if (changed == null) {
					changed = path.toCharArray();
				}
				changed[i] = normal;
			}
		}
		return changed == null ? path : new String(changed);
	}

	private InputStream internalGetResourceAsStream(final String tempFilepath) {
		try {
			if (isReadable(tempFilepath)) {
				final PathResult file = this.mount.fileSystem.resolveStoredFile(tempFilepath);
				final InputStream stream = this.mount.fileSystem.openFileStream(file, STREAM_MIN_BYTES);
				if (stream != null) {
					return new IndependentStream.Buffered(stream, STREAM_BUFFER_BYTES);
				}
				final ByteBuffer buffer = this.mount.fileSystem.readFileData(file);
				if (buffer.hasArray()) {
					// The buffer is read for this request alone, so a reader of the whole file may keep its array.
					return new StreamBytes.Owned(buffer.array());
				}
				final byte[] data = new byte[buffer.remaining()];
				buffer.clear();
				buffer.get(data);
				return new ByteArrayInputStream(data);
			}
		}
		catch (final IOException e) {
			throw new RuntimeException("CASC parser error for: " + tempFilepath, e);
		}
		return null;
	}

	@Override
	public ByteBuffer read(final String path) {
		final String readable = findReadable(path);
		return readable == null ? null : internalRead(readable);
	}

	private ByteBuffer internalRead(final String tempFilepath) {
		try {
			if (isReadable(tempFilepath)) {
				final ByteBuffer buffer = this.mount.fileSystem.readFileData(tempFilepath);
				return buffer;
			}
		}
		catch (final IOException e) {
			throw new RuntimeException("CASC parser error for: " + tempFilepath, e);
		}
		return null;
	}

	@Override
	public File getFile(final String filepath) {
		final String readable = findReadable(filepath);
		return readable == null ? null : internalGetFile(readable);
	}

	@Override
	public File getDirectory(final String filepath) throws IOException {
		return null;
	}

	private File internalGetFile(final String tempFilepath) {
		final File extracted = this.mount.extractedFiles.get(tempFilepath);
		if ((extracted != null) && extracted.isFile()) {
			return extracted;
		}
		try {
			if (isReadable(tempFilepath)) {
				// The extracted files belong to the storage, which other data sources may be using at the same time.
				synchronized (this.mount) {
					final File again = this.mount.extractedFiles.get(tempFilepath);
					if ((again != null) && again.isFile()) {
						return again;
					}
					final File tempProduct = new File(Mount.EXTRACT_FOLDER,
							tempFilepath.replace('\\', File.separatorChar));
					tempProduct.delete();
					makeFolders(tempProduct.getParentFile());
					try (final InputStream stream = this.mount.fileSystem.openFileStream(tempFilepath,
							STREAM_MIN_BYTES)) {
						if (stream != null) {
							copy(stream, tempProduct);
						}
						else {
							write(this.mount.fileSystem.readFileData(tempFilepath), tempProduct);
						}
					}
					tempProduct.deleteOnExit();
					this.mount.extractedFiles.put(tempFilepath, tempProduct);
					this.mount.extractionOrder.add(tempFilepath);
					this.mount.extractedBytes += tempProduct.length();
					this.mount.trimExtracted(tempFilepath);
					return tempProduct;
				}
			}
		}
		catch (final IOException e) {
			throw new RuntimeException("CASC parser error for: " + tempFilepath, e);
		}
		return null;
	}

	private static void copy(final InputStream stream, final File target) throws IOException {
		try (final FileChannel fileChannel = FileChannel.open(target.toPath(), StandardOpenOption.CREATE,
				StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
			final byte[] chunk = new byte[WRITE_CHUNK];
			int read;
			while ((read = stream.read(chunk)) >= 0) {
				final ByteBuffer buffer = ByteBuffer.wrap(chunk, 0, read);
				while (buffer.hasRemaining()) {
					fileChannel.write(buffer);
				}
			}
		}
	}

	private static void write(final ByteBuffer buffer, final File target) throws IOException {
		try (final FileChannel fileChannel = FileChannel.open(target.toPath(), StandardOpenOption.CREATE,
				StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.TRUNCATE_EXISTING)) {
			// A channel writes a heap buffer through a direct buffer as big as the write and keeps that on the
			// writing thread; Android counts those on the Java heap, so the file is written in parts.
			final int end = buffer.limit();
			while (buffer.position() < end) {
				buffer.limit(Math.min(end, buffer.position() + WRITE_CHUNK));
				fileChannel.write(buffer);
			}
			buffer.limit(end);
		}
	}

	/** Folders are registered for deletion before the files in them, which exit deletes in reverse order. */
	private static void makeFolders(final File folder) {
		if ((folder == null) || folder.isDirectory()) {
			return;
		}
		makeFolders(folder.getParentFile());
		if (folder.mkdir()) {
			folder.deleteOnExit();
		}
	}

	@Override
	public boolean has(final String filepath) {
		return isFound(filepath);
	}

	@Override
	public String findFirst(final String... filepaths) {
		final PathIndex.Query[] resolvedPaths = new PathIndex.Query[filepaths.length];
		for (int i = 0; i < filepaths.length; i++) {
			resolvedPaths[i] = resolveAlias(filepaths[i]);
		}
		for (final PathIndex.Prefix prefix : this.indexPrefixes) {
			for (int i = 0; i < resolvedPaths.length; i++) {
				if (isReadable(prefix, resolvedPaths[i])) {
					return filepaths[i];
				}
			}
		}
		for (int i = 0; i < resolvedPaths.length; i++) {
			if (isReadable(null, resolvedPaths[i])) {
				return filepaths[i];
			}
		}
		return null;
	}

	/**
	 * The file system also lists files of other locales and platforms that are not
	 * in local storage, so presence alone does not mean the file can be read.
	 * Paths the file system does not list at all are answered from the list of its
	 * paths, as walking the file system for each of them was slow.
	 */
	private boolean isReadable(final String tempFilepath) {
		return isReadable(null, new PathIndex.Query(tempFilepath));
	}

	private boolean isReadable(final PathIndex.Prefix prefix, final PathIndex.Query path) {
		final int index = this.mount.index.indexOf(prefix, path);
		if (index == -1) {
			return false;
		}
		final byte known = this.mount.readable[index];
		if (known != 0) {
			return known == READABLE;
		}
		final String prefixText = prefix == null ? null : prefix.text;
		try {
			final boolean readable = this.mount.fileSystem.isReadableFile(storedPath(prefixText, path.path));
			this.mount.readable[index] = readable ? READABLE : UNREADABLE;
			return readable;
		}
		catch (final IOException e) {
			throw new RuntimeException("CASC parser error for: " + storedPath(prefixText, path.path), e);
		}
	}

	private static String storedPath(final String prefix, final String path) {
		return prefix == null ? normalizePath(path) : prefix + "\\" + normalizePath(path);
	}

	@Override
	public Collection<String> getListfile() {
		return this.mount.listFile;
	}

	@Override
	public Collection<String> getListfileByExtension(final String extension) {
		return this.mount.pathsEndingWith(extension);
	}

	@Override
	public synchronized void close() throws IOException {
		if (!this.closed) {
			this.closed = true;
			Mount.release(this.mount);
		}
	}

	/**
	 * An opened storage, shared by the data sources of the same game data in this process. The engine opens its
	 * data for the menu and again for each map, the launcher for its catalog: opening took seconds on a phone,
	 * listing the file system most of it. The last storage closed stays open for the next data source, and the list
	 * of paths is kept on disk until the game data changes.
	 */
	static final class Mount {
		private static final String LISTFILE_FORMAT = "warsmash-casc-listfile 1";
		/** Movies and fonts extracted to disk are a cache: the oldest are deleted past this size and extracted again. */
		private static final long EXTRACT_LIMIT_BYTES = 768L << 20;
		static final File EXTRACT_FOLDER = new File(System.getProperty("java.io.tmpdir"), "warsmash-casc");
		private static final File INDEX_FOLDER = new File(System.getProperty("java.io.tmpdir"), "warsmash-casc-index");
		private static final Map<String, Mount> MOUNTS = new HashMap<>();
		private static boolean extractFolderCleared;

		private final String key;
		private final byte[] buildInfo;
		private final WarcraftIIICASC warcraftIIICASC;
		final FileSystem fileSystem;
		final List<String> listFile;
		final PathIndex index;
		/** Whether each listed path can be read, once asked: 0 for not yet. Bytes are written whole, so no lock. */
		final byte[] readable;
		final Map<String, File> extractedFiles = new ConcurrentHashMap<>();
		/** Paths of the extracted files, oldest first; guarded by this mount like the byte count. */
		final ArrayDeque<String> extractionOrder = new ArrayDeque<>();
		long extractedBytes;
		private int users;
		/** The listed paths of each extension asked for, in the order of the list; the list does not change. */
		private final Map<String, List<String>> pathsByExtension = new ConcurrentHashMap<>();

		List<String> pathsEndingWith(final String extension) {
			return this.pathsByExtension.computeIfAbsent(extension.toLowerCase(Locale.ROOT), (key) -> {
				final DataSource.PathFilter filter = DataSource.PathFilter.endingWith(extension);
				final List<String> found = new ArrayList<>();
				for (final String path : this.listFile) {
					if (filter.test(path, 0)) {
						found.add(path);
					}
				}
				return Collections.unmodifiableList(found);
			});
		}

		void trimExtracted(final String keep) {
			while ((this.extractedBytes > EXTRACT_LIMIT_BYTES) && (this.extractionOrder.size() > 1)) {
				final String oldest = this.extractionOrder.poll();
				if (oldest.equals(keep)) {
					this.extractionOrder.add(oldest);
					continue;
				}
				final File file = this.extractedFiles.remove(oldest);
				if (file != null) {
					this.extractedBytes -= file.length();
					file.delete();
				}
			}
		}

		private Mount(final String key, final byte[] buildInfo, final Path installPath, final String product,
				final boolean old131Format) throws IOException {
			this.key = key;
			this.buildInfo = buildInfo;
			final long opening = System.nanoTime();
			// The saved list of paths does not need the storage, so it is read and indexed while the storage opens.
			final FutureTask<Listed> saved = new FutureTask<>(() -> {
				final List<String> paths = readSavedListFile(key, buildInfo);
				return paths == null ? null : new Listed(paths);
			});
			final Thread reader = new Thread(saved, "casc-listfile");
			reader.setDaemon(true);
			reader.start();
			try {
				// Plain reads are as fast as mapping here, and a mapping per read can exhaust a 32-bit address space.
				this.warcraftIIICASC = new WarcraftIIICASC(installPath, false, product, old131Format);
			}
			catch (final IOException | RuntimeException e) {
				saved.cancel(true);
				throw e;
			}
			final long storageOpened = System.nanoTime();
			try {
				this.fileSystem = this.warcraftIIICASC.getRootFileSystem();
				Listed listed = awaitSaved(saved);
				final boolean listSaved = listed != null;
				if (listed == null) {
					final List<String> paths = this.fileSystem.enumerateFiles();
					writeListFile(savedListFile(key), listFileStamp(key, buildInfo), paths);
					listed = new Listed(paths);
				}
				this.listFile = listed.paths;
				this.index = listed.index;
				this.readable = new byte[this.listFile.size()];
				PerfStats.cascOpened(opening, storageOpened, System.nanoTime(), this.listFile.size(), listSaved);
			}
			catch (final IOException | RuntimeException e) {
				this.warcraftIIICASC.close();
				throw e;
			}
		}

		private static final class Listed {
			final List<String> paths;
			final PathIndex index;

			Listed(final List<String> paths) {
				this.paths = Collections.unmodifiableList(paths);
				this.index = new PathIndex(this.paths);
			}
		}

		/** @return the saved list read in the background, or null when there is none to use */
		private static Listed awaitSaved(final FutureTask<Listed> saved) throws IOException {
			try {
				return saved.get();
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new InterruptedIOException("interrupted while reading the list of paths");
			}
			catch (final ExecutionException e) {
				// read again from the storage
				return null;
			}
		}

		static synchronized Mount acquire(final String installPath, final String product, final boolean old131Format)
				throws IOException {
			final Path install = Paths.get(installPath);
			final byte[] buildInfo = Files.readAllBytes(install.resolve(Info.BUILD_INFO_FILE_NAME));
			final String key = install.toAbsolutePath().normalize() + "|" + product + "|" + old131Format;
			Mount mount = MOUNTS.get(key);
			if ((mount != null) && !Arrays.equals(mount.buildInfo, buildInfo)) {
				// The game was updated: data sources still open keep the old storage until they close.
				MOUNTS.remove(key);
				if (mount.users == 0) {
					mount.closeQuietly();
				}
				mount = null;
			}
			for (final Iterator<Mount> iterator = MOUNTS.values().iterator(); iterator.hasNext();) {
				final Mount other = iterator.next();
				if ((other != mount) && (other.users == 0)) {
					iterator.remove();
					other.closeQuietly();
				}
			}
			if (mount == null) {
				if (!extractFolderCleared) {
					// Files left by a session that did not exit cleanly are not deleted by deleteOnExit.
					deleteContents(EXTRACT_FOLDER);
					extractFolderCleared = true;
				}
				mount = new Mount(key, buildInfo, install, product, old131Format);
				MOUNTS.put(key, mount);
			}
			mount.users++;
			return mount;
		}

		static synchronized void release(final Mount mount) {
			mount.users--;
			if ((mount.users == 0) && (MOUNTS.get(mount.key) != mount)) {
				mount.closeQuietly();
			}
		}

		private void closeQuietly() {
			try {
				this.warcraftIIICASC.close();
			}
			catch (final IOException e) {
				Log.error(e);
			}
		}

		private static String listFileStamp(final String key, final byte[] buildInfo) {
			return LISTFILE_FORMAT + "|" + key + "|" + hex(digest(buildInfo));
		}

		private static File savedListFile(final String key) {
			return new File(INDEX_FOLDER, hex(digest(key.getBytes(StandardCharsets.UTF_8))) + ".gz");
		}

		/** @return the paths saved for this game data, or null when none are saved or they cannot be used */
		private static List<String> readSavedListFile(final String key, final byte[] buildInfo) {
			final String stamp = listFileStamp(key, buildInfo);
			final File cached = savedListFile(key);
			if (cached.isFile()) {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(
						new GZIPInputStream(new FileInputStream(cached), 65536), StandardCharsets.UTF_8), 65536)) {
					if (stamp.equals(reader.readLine())) {
						final int count = Integer.parseInt(reader.readLine());
						final List<String> paths = new ArrayList<>(count);
						String line;
						while ((line = reader.readLine()) != null) {
							paths.add(line);
						}
						if (paths.size() == count) {
							return paths;
						}
					}
				}
				catch (final IOException | RuntimeException e) {
					// read again from the storage
				}
			}
			return null;
		}

		private static void writeListFile(final File target, final String stamp, final List<String> paths) {
			for (final String path : paths) {
				if ((path.indexOf('\n') != -1) || (path.indexOf('\r') != -1)) {
					return;
				}
			}
			final File folder = target.getParentFile();
			folder.mkdirs();
			// Engines started together may write it at the same time.
			final File temp = new File(folder, target.getName() + "." + System.nanoTime() + ".tmp");
			try {
				try (Writer writer = new BufferedWriter(new OutputStreamWriter(
						new GZIPOutputStream(new FileOutputStream(temp), 65536), StandardCharsets.UTF_8))) {
					writer.write(stamp);
					writer.write('\n');
					writer.write(Integer.toString(paths.size()));
					writer.write('\n');
					for (final String path : paths) {
						writer.write(path);
						writer.write('\n');
					}
				}
				target.delete();
				if (!temp.renameTo(target)) {
					temp.delete();
				}
			}
			catch (final IOException e) {
				// Only the next start is slower without it.
				temp.delete();
			}
		}

		private static byte[] digest(final byte[] bytes) {
			try {
				return MessageDigest.getInstance("SHA-256").digest(bytes);
			}
			catch (final NoSuchAlgorithmException e) {
				throw new IllegalStateException(e);
			}
		}

		private static String hex(final byte[] bytes) {
			final StringBuilder builder = new StringBuilder(bytes.length * 2);
			for (final byte b : bytes) {
				builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
			}
			return builder.toString();
		}
	}

	private static void deleteContents(final File folder) {
		final File[] children = folder.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			if (child.isDirectory()) {
				deleteContents(child);
			}
			child.delete();
		}
	}

	public static enum Product {
		WARCRAFT_III("w3"),
		WARCRAFT_III_PUBLIC_TEST("w3t");

		private final String key;

		private Product(final String key) {
			this.key = key;
		}

		public String getKey() {
			return this.key;
		}
	}
}
