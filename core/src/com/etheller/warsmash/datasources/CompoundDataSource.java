package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CompoundDataSource implements DataSource {
	private final List<DataSource> mpqList = new ArrayList<>();

	public CompoundDataSource(final List<DataSource> dataSources) {
		if (dataSources != null) {
			for (final DataSource dataSource : dataSources) {
				this.mpqList.add(dataSource);
			}
		}
	}

	Map<String, File> cache = new HashMap<>();
	// Index of the source that has each path, or MISSING. The sources do not change while this instance lives,
	// and the same paths are asked for many times (prefixes, extensions, aliases).
	private static final int MISSING = -1;
	private final Map<String, Integer> sourceIndexCache = new ConcurrentHashMap<>();

	private int findSourceIndex(final String filepath) {
		final Integer cached = this.sourceIndexCache.get(filepath);
		if (cached != null) {
			return cached;
		}
		int found = MISSING;
		for (int i = this.mpqList.size() - 1; i >= 0; i--) {
			if (this.mpqList.get(i).has(filepath)) {
				found = i;
				break;
			}
		}
		this.sourceIndexCache.put(filepath, found);
		return found;
	}

	@Override
	public File getFile(final String filepath) {
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
			e.printStackTrace();
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
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public ByteBuffer read(final String path) throws IOException {
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
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public InputStream getResourceAsStream(final String filepath) {
		try {
			final Integer cached = this.sourceIndexCache.get(filepath);
			if ((cached != null) && (cached != MISSING)) {
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
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public boolean has(final String filepath) {
		if (this.cache.containsKey(filepath)) {
			return true;
		}
		return findSourceIndex(filepath) != MISSING;
	}

	@Override
	public String findFirst(final String... filepaths) {
		for (final String filepath : filepaths) {
			if (this.cache.containsKey(filepath)) {
				return filepath;
			}
		}
		for (int i = this.mpqList.size() - 1; i >= 0; i--) {
			final String found = this.mpqList.get(i).findFirst(filepaths);
			if (found != null) {
				return found;
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
				e.printStackTrace();
			}
			catch (final IOException e) {
				e.printStackTrace();
			}
		}
		this.cache.clear();
		this.sourceIndexCache.clear();
		this.mpqList.clear();
		if (dataSourceDescriptors != null) {
			for (final DataSourceDescriptor descriptor : dataSourceDescriptors) {
				this.mpqList.add(descriptor.createDataSource());
			}
		}
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
	public void close() throws IOException {
		for (final DataSource mpqGuy : this.mpqList) {
			mpqGuy.close();
		}
	}
}
