package com.etheller.warsmash.datasources;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hiveworkshop.blizzard.casc.io.WarcraftIIICASC;
import com.hiveworkshop.blizzard.casc.io.WarcraftIIICASC.FileSystem;
import com.hiveworkshop.json.JSONArray;
import com.hiveworkshop.json.JSONObject;
import com.hiveworkshop.json.JSONTokener;

public class CascDataSource implements DataSource {
	private final String[] prefixes;
	private WarcraftIIICASC warcraftIIICASC;
	private FileSystem rootFileSystem;
	private List<String> listFile;
	private Map<String, String> fileAliases;
	private final Map<String, Boolean> readableCache = new ConcurrentHashMap<>();
	private final Map<String, File> extractedFiles = new ConcurrentHashMap<>();
	private final File extractFolder = new File(System.getProperty("java.io.tmpdir"), "warsmash-casc");

	public CascDataSource(final String warcraft3InstallPath, final String[] prefixes, final String product,
			final boolean old131Format) {
		this.prefixes = prefixes;
		for (int i = 0; i < (prefixes.length / 2); i++) {
			final String temp = prefixes[i];
			prefixes[i] = prefixes[prefixes.length - i - 1];
			prefixes[prefixes.length - i - 1] = temp;
		}

		// Files left by a session that did not exit cleanly are not deleted by deleteOnExit.
		deleteContents(this.extractFolder);
		try {
			// Plain reads are as fast as mapping here, and a mapping per read can exhaust a 32-bit address space.
			this.warcraftIIICASC = new WarcraftIIICASC(Paths.get(warcraft3InstallPath), false, product,
					old131Format);
			this.rootFileSystem = this.warcraftIIICASC.getRootFileSystem();
			this.listFile = this.rootFileSystem.enumerateFiles();
			this.fileAliases = new HashMap<>();
			if (this.has("filealiases.json")) {
				try (InputStream stream = this.getResourceAsStream("filealiases.json")) {
					stream.mark(4);
					if ('\ufeff' != stream.read()) {
						stream.reset(); // not the BOM marker
					}
					final JSONArray jsonObject = new JSONArray(new JSONTokener(stream));
					for (int i = 0; i < jsonObject.length(); i++) {
						final JSONObject alias = jsonObject.getJSONObject(i);
						final String src = alias.getString("src");
						final String dest = alias.getString("dest");
						this.fileAliases.put(src.toLowerCase(Locale.US).replace('/', '\\'),
								dest.toLowerCase(Locale.US).replace('/', '\\'));
						if ((src.toLowerCase(Locale.US).contains(".blp")
								|| dest.toLowerCase(Locale.US).contains(".blp"))
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
							this.fileAliases.put(src.toLowerCase(Locale.US).replace('/', '\\').replace(".blp", ".dds"),
									dest.toLowerCase(Locale.US).replace('/', '\\').replace(".blp", ".dds"));
						}
					}
				}
			}
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
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
				final ByteBuffer buffer = this.rootFileSystem.readFileData(tempFilepath);
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
				final ByteBuffer buffer = this.rootFileSystem.readFileData(tempFilepath);
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
		final File extracted = this.extractedFiles.get(tempFilepath);
		if ((extracted != null) && extracted.isFile()) {
			return extracted;
		}
		try {
			if (isReadable(tempFilepath)) {
				final ByteBuffer buffer = this.rootFileSystem.readFileData(tempFilepath);
				final File tempProduct = new File(this.extractFolder,
						tempFilepath.replace('\\', File.separatorChar));
				tempProduct.delete();
				tempProduct.getParentFile().mkdirs();
				try (final FileChannel fileChannel = FileChannel.open(tempProduct.toPath(), StandardOpenOption.CREATE,
						StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.TRUNCATE_EXISTING)) {
					fileChannel.write(buffer);
				}
				tempProduct.deleteOnExit();
				this.extractedFiles.put(tempFilepath, tempProduct);
				return tempProduct;
			}
		}
		catch (final IOException e) {
			throw new RuntimeException("CASC parser error for: " + tempFilepath, e);
		}
		return null;
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
	 */
	private boolean isReadable(final String tempFilepath) {
		final Boolean cached = this.readableCache.get(tempFilepath);
		if (cached != null) {
			return cached;
		}
		try {
			final boolean readable = this.rootFileSystem.isReadableFile(tempFilepath);
			this.readableCache.put(tempFilepath, readable);
			return readable;
		}
		catch (final IOException e) {
			throw new RuntimeException("CASC parser error for: " + tempFilepath, e);
		}
	}

	@Override
	public Collection<String> getListfile() {
		return this.listFile;
	}

	@Override
	public void close() throws IOException {
		this.warcraftIIICASC.close();
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
