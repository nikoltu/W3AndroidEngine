package com.etheller.warsmash.datasources;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.etheller.warsmash.util.Log;
import com.hiveworkshop.blizzard.casc.info.Info;
import com.hiveworkshop.blizzard.casc.io.WarcraftIIICASC;
import com.hiveworkshop.blizzard.casc.io.WarcraftIIICASC.FileSystem;
import com.hiveworkshop.json.JSONArray;
import com.hiveworkshop.json.JSONObject;
import com.hiveworkshop.json.JSONTokener;

public class CascDataSource implements DataSource {
	private final String[] prefixes;
	private final Mount mount;
	private Map<String, String> fileAliases = Collections.emptyMap();
	private boolean closed;

	public CascDataSource(final String warcraft3InstallPath, final String[] prefixes, final String product,
			final boolean old131Format) {
		this.prefixes = prefixes;
		for (int i = 0; i < (prefixes.length / 2); i++) {
			final String temp = prefixes[i];
			prefixes[i] = prefixes[prefixes.length - i - 1];
			prefixes[prefixes.length - i - 1] = temp;
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

	private static final int PARSED_ALIASES_KEPT = 4;
	private static final int WRITE_CHUNK = 1 << 16;
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
		final byte[] bytes = readAll(stream);
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

	private static byte[] readAll(final InputStream stream) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final byte[] buffer = new byte[8192];
		int read;
		while ((read = stream.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	@Override
	public InputStream getResourceAsStream(String filepath) {
		filepath = filepath.toLowerCase(Locale.US).replace('/', '\\').replace(':', '\\');
		final String resolvedAlias = this.fileAliases.get(filepath);
		if (resolvedAlias != null) {
			filepath = resolvedAlias;
		}
		for (final String prefix : this.prefixes) {
			final String tempFilepath = prefix + "\\" + filepath;
			final InputStream stream = internalGetResourceAsStream(tempFilepath);
			if (stream != null) {
				return stream;
			}
		}
		return internalGetResourceAsStream(filepath);
	}

	private InputStream internalGetResourceAsStream(final String tempFilepath) {
		try {
			if (isReadable(tempFilepath)) {
				final ByteBuffer buffer = this.mount.fileSystem.readFileData(tempFilepath);
				if (buffer.hasArray()) {
					return new ByteArrayInputStream(buffer.array());
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
	public ByteBuffer read(String path) {
		path = path.toLowerCase(Locale.US).replace('/', '\\').replace(':', '\\');
		final String resolvedAlias = this.fileAliases.get(path);
		if (resolvedAlias != null) {
			path = resolvedAlias;
		}
		for (final String prefix : this.prefixes) {
			final String tempFilepath = prefix + "\\" + path;
			final ByteBuffer stream = internalRead(tempFilepath);
			if (stream != null) {
				return stream;
			}
		}
		return internalRead(path);
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
	public File getFile(String filepath) {
		filepath = filepath.toLowerCase(Locale.US).replace('/', '\\').replace(':', '\\');
		final String resolvedAlias = this.fileAliases.get(filepath);
		if (resolvedAlias != null) {
			filepath = resolvedAlias;
		}
		for (final String prefix : this.prefixes) {
			final String tempFilepath = prefix + "\\" + filepath;
			final File file = internalGetFile(tempFilepath);
			if (file != null) {
				return file;
			}
		}
		return internalGetFile(filepath);
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
					final ByteBuffer buffer = this.mount.fileSystem.readFileData(tempFilepath);
					final File tempProduct = new File(Mount.EXTRACT_FOLDER,
							tempFilepath.replace('\\', File.separatorChar));
					tempProduct.delete();
					makeFolders(tempProduct.getParentFile());
					try (final FileChannel fileChannel = FileChannel.open(tempProduct.toPath(),
							StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ,
							StandardOpenOption.TRUNCATE_EXISTING)) {
						// A channel writes a heap buffer through a direct buffer as big as the write and keeps that on
						// the writing thread; Android counts those on the Java heap, so the file is written in parts.
						final int end = buffer.limit();
						while (buffer.position() < end) {
							buffer.limit(Math.min(end, buffer.position() + WRITE_CHUNK));
							fileChannel.write(buffer);
						}
						buffer.limit(end);
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
	public boolean has(String filepath) {
		filepath = filepath.toLowerCase(Locale.US).replace('/', '\\').replace(':', '\\');
		final String resolvedAlias = this.fileAliases.get(filepath);
		if (resolvedAlias != null) {
			filepath = resolvedAlias;
		}
		for (final String prefix : this.prefixes) {
			if (isReadable(prefix + "\\" + filepath)) {
				return true;
			}
		}
		return isReadable(filepath);
	}

	@Override
	public String findFirst(final String... filepaths) {
		final String[] resolvedPaths = new String[filepaths.length];
		for (int i = 0; i < filepaths.length; i++) {
			final String filepath = filepaths[i].toLowerCase(Locale.US).replace('/', '\\').replace(':', '\\');
			final String resolvedAlias = this.fileAliases.get(filepath);
			resolvedPaths[i] = resolvedAlias != null ? resolvedAlias : filepath;
		}
		for (final String prefix : this.prefixes) {
			for (int i = 0; i < resolvedPaths.length; i++) {
				if (isReadable(prefix + "\\" + resolvedPaths[i])) {
					return filepaths[i];
				}
			}
		}
		for (int i = 0; i < resolvedPaths.length; i++) {
			if (isReadable(resolvedPaths[i])) {
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
		if (!this.mount.paths.contains(listedForm(tempFilepath))) {
			return false;
		}
		final Boolean cached = this.mount.readableCache.get(tempFilepath);
		if (cached != null) {
			return cached;
		}
		try {
			final boolean readable = this.mount.fileSystem.isReadableFile(tempFilepath);
			this.mount.readableCache.put(tempFilepath, readable);
			return readable;
		}
		catch (final IOException e) {
			throw new RuntimeException("CASC parser error for: " + tempFilepath, e);
		}
	}

	/** The path as the file system lists it: lower case, and without the trailing separators it ignores. */
	private static String listedForm(final String path) {
		int end = path.length();
		while ((end > 0) && (path.charAt(end - 1) == '\\')) {
			end--;
		}
		return path.substring(0, end).toLowerCase(Locale.ROOT);
	}

	@Override
	public Collection<String> getListfile() {
		return this.mount.listFile;
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
		final Set<String> paths;
		final Map<String, Boolean> readableCache = new ConcurrentHashMap<>();
		final Map<String, File> extractedFiles = new ConcurrentHashMap<>();
		/** Paths of the extracted files, oldest first; guarded by this mount like the byte count. */
		final ArrayDeque<String> extractionOrder = new ArrayDeque<>();
		long extractedBytes;
		private int users;

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
			// Plain reads are as fast as mapping here, and a mapping per read can exhaust a 32-bit address space.
			this.warcraftIIICASC = new WarcraftIIICASC(installPath, false, product, old131Format);
			try {
				this.fileSystem = this.warcraftIIICASC.getRootFileSystem();
				this.listFile = Collections.unmodifiableList(listFile(key, buildInfo, this.fileSystem));
				this.paths = new HashSet<>(this.listFile.size() * 2);
				for (final String path : this.listFile) {
					this.paths.add(path.toLowerCase(Locale.ROOT));
				}
			}
			catch (final IOException | RuntimeException e) {
				this.warcraftIIICASC.close();
				throw e;
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

		private static List<String> listFile(final String key, final byte[] buildInfo, final FileSystem fileSystem)
				throws IOException {
			final String stamp = LISTFILE_FORMAT + "|" + key + "|" + hex(digest(buildInfo));
			final File cached = new File(INDEX_FOLDER, hex(digest(key.getBytes(StandardCharsets.UTF_8))) + ".gz");
			if (cached.isFile()) {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(
						new GZIPInputStream(new FileInputStream(cached), 65536), StandardCharsets.UTF_8))) {
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
					// read again from the storage below
				}
			}
			final List<String> paths = fileSystem.enumerateFiles();
			writeListFile(cached, stamp, paths);
			return paths;
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
