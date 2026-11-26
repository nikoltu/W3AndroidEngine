package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import com.badlogic.gdx.utils.IntSet;
import com.etheller.warsmash.util.Log;

public class CompoundDataSource implements DataSource {
	private final List<DataSource> mpqList = new CopyOnWriteArrayList<>();

	public CompoundDataSource(final List<DataSource> dataSources) {
		if (dataSources != null) {
			for (final DataSource dataSource : dataSources) {
				this.mpqList.add(dataSource);
			}
		}
		sourcesSet();
		loadFileAliases();
	}

	/**
	 * Adds a source with the highest priority and forgets which source had which file, for a source added while the
	 * data is in use.
	 */
	public void addSource(final DataSource dataSource) {
		this.mpqList.add(dataSource);
		sourcesSet();
		sourcesChanged();
	}

	/**
	 * Forgets which source had which file. A source whose files changed while the data is in use (a folder that was
	 * pointed elsewhere) calls for this on the sources that hold it.
	 */
	public void sourcesChanged() {
		this.revision++;
		this.cache.clear();
		this.sourceIndexCache.clear();
		this.graphicsIndependent = null;
	}

	@Override
	public int revision() {
		int total = this.revision;
		for (final DataSource source : this.mpqList) {
			total += source.revision();
		}
		return total;
	}

	/** The sources without graphics packages, kept until the sources change; this when none has one. */
	@Override
	public DataSource graphicsIndependent() {
		DataSource view = this.graphicsIndependent;
		if (view == null) {
			final List<DataSource> sources = new ArrayList<>(this.mpqList.size());
			boolean changed = false;
			for (final DataSource source : this.mpqList) {
				final DataSource independent = source.graphicsIndependent();
				changed |= independent != source;
				sources.add(independent);
			}
			view = changed ? new CompoundDataSource(sources) : this;
			this.graphicsIndependent = view;
		}
		return view;
	}

	/** The sources in their order of priority. */
	public List<DataSource> getSources() {
		return Collections.unmodifiableList(this.mpqList);
	}

	private volatile DataSource graphicsIndependent;
	private volatile int revision;
	// Lookups (has, findFirst) also come from the threads that prepare models for the game thread.
	Map<String, File> cache = new ConcurrentHashMap<>();
	// Index of the source that has each path, or MISSING. The sources do not change while this instance lives,
	// and the same paths are asked for many times (prefixes, extensions, aliases).
	private static final int MISSING = -1;
	private static final int UNKNOWN = -2;
	private final PathCache sourceIndexCache = new PathCache();

	// Data prepared as a folder has many files only under their alias target, and only CASC resolves aliases itself.
	// The path as asked wins, so CASC sources behave as before.
	private volatile Map<String, String> fileAliases = Collections.emptyMap();
	/** The hash codes of the alias sources, so that a path without an alias is answered without normalizing it. */
	private volatile IntSet aliasHashes = new IntSet();

	private void loadFileAliases() {
		this.fileAliases = Collections.emptyMap();
		this.aliasHashes = new IntSet();
		final int index = findSourceIndex("filealiases.json");
		if (index == MISSING) {
			return;
		}
		try (InputStream stream = this.mpqList.get(index).getResourceAsStream("filealiases.json")) {
			if (stream != null) {
				final Map<String, String> aliases = CascDataSource.cachedFileAliases(stream);
				final IntSet hashes = new IntSet(aliases.size());
				for (final String source : aliases.keySet()) {
					hashes.add(source.hashCode());
				}
				this.aliasHashes = hashes;
				this.fileAliases = aliases;
			}
		}
		catch (final IOException | RuntimeException e) {
			Log.error(e);
		}
	}

	private String resolveAlias(final String filepath) {
		final Map<String, String> fileAliases = this.fileAliases;
		if (fileAliases.isEmpty()) {
			return null;
		}
		final int hash = CascDataSource.normalizedHash(filepath);
		if ((hash != CascDataSource.NOT_ASCII) && !this.aliasHashes.contains(hash)) {
			return null;
		}
		return fileAliases.get(CascDataSource.normalizePath(filepath));
	}

	/**
	 * Whether the answers of the sources are worth remembering: not when each of them looks a path up in an index of
	 * its own. Such a compound is the game data under the folders of its prefixes, which asks it for every name under
	 * every prefix, and remembering them took half a million entries for one map and longer than asking again.
	 */
	private volatile boolean rememberSources = true;

	private void sourcesSet() {
		boolean indexed = true;
		for (final DataSource source : this.mpqList) {
			indexed &= (source instanceof CascDataSource) || (source instanceof FolderDataSource)
					|| (source instanceof MpqDataSource) || (source instanceof MapsFolderDataSource);
		}
		this.rememberSources = !indexed;
	}

	private int findSourceIndex(final String filepath) {
		final boolean remember = this.rememberSources;
		if (remember) {
			final int cached = this.sourceIndexCache.get(filepath, UNKNOWN);
			if (cached != UNKNOWN) {
				return cached;
			}
		}
		int found = MISSING;
		for (int i = this.mpqList.size() - 1; i >= 0; i--) {
			if (this.mpqList.get(i).has(filepath)) {
				found = i;
				break;
			}
		}
		if (remember) {
			this.sourceIndexCache.put(filepath, found);
		}
		return found;
	}

	@Override
	public File getFile(final String filepath) {
		File file = getFileFromSources(filepath);
		if (file == null) {
			final String alias = resolveAlias(filepath);
			if (alias != null) {
				file = getFileFromSources(alias);
				if (file != null) {
					this.cache.put(filepath, file);
				}
			}
		}
		return file;
	}

	private File getFileFromSources(final String filepath) {
		if (this.cache.containsKey(filepath)) {
			return this.cache.get(filepath);
		}
		try {
			for (int i = this.mpqList.size() - 1; i >= 0; i--) {
				final DataSource mpq = this.mpqList.get(i);
				final File tempProduct = mpq.getFile(filepath);
				if (tempProduct != null) {
					this.cache.put(filepath, tempProduct);
					return tempProduct;
				}
			}
		}
		catch (final IOException e) {
			// TODO Auto-generated catch block
			Log.error(e);
		}
		return null;
	}

	@Override
	public File getDirectory(String filepath) throws IOException {
		try {
			for (int i = this.mpqList.size() - 1; i >= 0; i--) {
				final DataSource mpq = this.mpqList.get(i);
				final File tempProduct = mpq.getDirectory(filepath);
				if (tempProduct != null) {
					return tempProduct;
				}
			}
		}
		catch (final IOException e) {
			// TODO Auto-generated catch block
			Log.error(e);
		}
		return null;
	}

	@Override
	public ByteBuffer read(final String path) throws IOException {
		final ByteBuffer buffer = readFromSources(path);
		if (buffer == null) {
			final String alias = resolveAlias(path);
			if (alias != null) {
				return readFromSources(alias);
			}
		}
		return buffer;
	}

	/** The file of the source that {@link #read} takes it from, when that is a file of a data folder. */
	@Override
	public File fileOnDisk(final String filepath) {
		for (int i = this.mpqList.size() - 1; i >= 0; i--) {
			final DataSource source = this.mpqList.get(i);
			final File file = source.fileOnDisk(filepath);
			if (file != null) {
				return file;
			}
			if (source.has(filepath)) {
				return null;
			}
		}
		return null;
	}

	private ByteBuffer readFromSources(final String path) {
		try {
			for (int i = this.mpqList.size() - 1; i >= 0; i--) {
				final DataSource mpq = this.mpqList.get(i);
				final ByteBuffer buffer = mpq.read(path);
				if (buffer != null) {
					return buffer;
				}
			}
		}
		catch (final IOException e) {
			// TODO Auto-generated catch block
			Log.error(e);
		}
		return null;
	}

	@Override
	public InputStream getResourceAsStream(final String filepath) {
		final InputStream stream = getResourceAsStreamFromSources(filepath);
		if (stream == null) {
			final String alias = resolveAlias(filepath);
			if (alias != null) {
				return getResourceAsStreamFromSources(alias);
			}
		}
		return stream;
	}

	private InputStream getResourceAsStreamFromSources(final String filepath) {
		try {
			final int cached = this.sourceIndexCache.get(filepath, UNKNOWN);
			if ((cached != UNKNOWN) && (cached != MISSING)) {
				final InputStream resourceAsStream = this.mpqList.get(cached).getResourceAsStream(filepath);
				if (resourceAsStream != null) {
					return resourceAsStream;
				}
			}
			for (int i = this.mpqList.size() - 1; i >= 0; i--) {
				final DataSource mpq = this.mpqList.get(i);
				final InputStream resourceAsStream = mpq.getResourceAsStream(filepath);
				if (resourceAsStream != null) {
					return resourceAsStream;
				}
			}
		}
		catch (final IOException e) {
			// TODO Auto-generated catch block
			Log.error(e);
		}
		return null;
	}

	@Override
	public IndependentStream.Opener opener(final String filepath) {
		// the source that getResourceAsStream reads the file from
		String path = filepath;
		int index = findSourceIndex(path);
		if (index == MISSING) {
			path = resolveAlias(filepath);
			if (path == null) {
				return null;
			}
			index = findSourceIndex(path);
			if (index == MISSING) {
				return null;
			}
		}
		return this.mpqList.get(index).opener(path);
	}

	@Override
	public boolean has(final String filepath) {
		// Only files copied out with getFile are in the cache, seldom any, and hashing each path asked cost time.
		if (!this.cache.isEmpty() && this.cache.containsKey(filepath)) {
			return true;
		}
		if (findSourceIndex(filepath) != MISSING) {
			return true;
		}
		final String alias = resolveAlias(filepath);
		return (alias != null) && (findSourceIndex(alias) != MISSING);
	}

	@Override
	public String findFirst(final String... filepaths) {
		// The latest source with any of the paths decides, so a file in an override folder wins over the game data
		// even when it has another of the names (a .blp beside the game's .dds). Every source finds one of the paths
		// exactly when it has one of them, so that source is the latest of the sources remembered for the paths, and
		// only when it has several of them does it choose among them. Asking each source in turn walked all the data
		// below each of them again for every texture and sound of a map.
		int latest = MISSING;
		String latestPath = null;
		boolean several = false;
		for (final String filepath : filepaths) {
			final int index = findSourceIndex(filepath);
			if (index > latest) {
				latest = index;
				latestPath = filepath;
				several = false;
			}
			else if ((index == latest) && (index != MISSING)) {
				several = true;
			}
		}
		if (latest != MISSING) {
			return several ? this.mpqList.get(latest).findFirst(filepaths) : latestPath;
		}
		for (final String filepath : filepaths) {
			final String alias = resolveAlias(filepath);
			if ((alias != null) && (findSourceIndex(alias) != MISSING)) {
				return filepath;
			}
		}
		return null;
	}

	public void refresh(final List<DataSourceDescriptor> dataSourceDescriptors) {
		for (final DataSource dataSource : this.mpqList) {
			try {
				dataSource.close();
			}
			catch (final NullPointerException e) {
				Log.error(e);
			}
			catch (final IOException e) {
				Log.error(e);
			}
		}
		this.revision++;
		this.cache.clear();
		this.sourceIndexCache.clear();
		this.mpqList.clear();
		if (dataSourceDescriptors != null) {
			for (final DataSourceDescriptor descriptor : dataSourceDescriptors) {
				this.mpqList.add(descriptor.createDataSource());
			}
		}
		sourcesSet();
		loadFileAliases();
	}

	public interface LoadedMPQ {
		void unload();

		boolean hasListfile();

		boolean has(String path);
	}

	public Set<String> getMergedListfile() {
		final Set<String> listfile = new HashSet<>();
		for (final DataSource mpqGuy : this.mpqList) {
			final Collection<String> dataSourceListfile = mpqGuy.getListfile();
			if (dataSourceListfile != null) {
				for (final String element : dataSourceListfile) {
					listfile.add(element);
				}
			}
		}
		return listfile;
	}

	@Override
	public Collection<String> getListfile() {
		return getMergedListfile();
	}

	@Override
	public Collection<String> getListfile(final Predicate<String> accept) {
		final Set<String> accepted = new LinkedHashSet<>();
		for (final DataSource mpqGuy : this.mpqList) {
			accepted.addAll(mpqGuy.getListfile(accept));
		}
		return accepted;
	}

	@Override
	public Collection<String> getListfileByExtension(final String extension) {
		final Set<String> accepted = new LinkedHashSet<>();
		for (final DataSource mpqGuy : this.mpqList) {
			accepted.addAll(mpqGuy.getListfileByExtension(extension));
		}
		return accepted;
	}

	@Override
	public void close() throws IOException {
		for (final DataSource mpqGuy : this.mpqList) {
			mpqGuy.close();
		}
	}
}
