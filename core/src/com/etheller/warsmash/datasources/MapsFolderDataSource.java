package com.etheller.warsmash.datasources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The Maps folder of a folder, offered as the Maps folder of the game data. Nothing else of the folder is visible, so
 * that the other files a player keeps there (replays, saves, campaigns) are neither indexed nor able to replace
 * game files.
 */
public class MapsFolderDataSource implements DataSource {
	private static final String PREFIX = "maps\\";

	private volatile FolderDataSource maps;

	/** @param folder the folder holding the Maps folder, or null for a source that offers nothing yet */
	public MapsFolderDataSource(final Path folder) {
		this.maps = folder == null ? null : new FolderDataSource(folder.resolve("Maps"));
	}

	/**
	 * Points the source at another folder, or at none, and reads the maps in it again; also the way to see maps
	 * added to or removed from the same folder while the source is in use. Whoever holds the source has to forget
	 * what it learned about its files ({@link CompoundDataSource#sourcesChanged()}).
	 */
	public void setFolder(final Path folder) {
		final FolderDataSource before = this.maps;
		this.maps = folder == null ? null : new FolderDataSource(folder.resolve("Maps"));
		if (before != null) {
			before.close();
		}
	}

	/** The path inside the Maps folder, or null for a path outside it. */
	private static String inside(final String filepath) {
		final String normal = filepath.replace('/', '\\');
		return normal.toLowerCase(Locale.ROOT).startsWith(PREFIX) ? normal.substring(PREFIX.length()) : null;
	}

	@Override
	public InputStream getResourceAsStream(final String filepath) throws IOException {
		final String path = inside(filepath);
		final FolderDataSource maps = this.maps;
		return (path == null) || (maps == null) ? null : maps.getResourceAsStream(path);
	}

	@Override
	public File getFile(final String filepath) throws IOException {
		final String path = inside(filepath);
		final FolderDataSource maps = this.maps;
		return (path == null) || (maps == null) ? null : maps.getFile(path);
	}

	@Override
	public File fileOnDisk(final String filepath) {
		final String path = inside(filepath);
		final FolderDataSource maps = this.maps;
		return (path == null) || (maps == null) ? null : maps.fileOnDisk(path);
	}

	@Override
	public File getDirectory(final String filepath) throws IOException {
		final String path = inside(filepath);
		final FolderDataSource maps = this.maps;
		return (path == null) || (maps == null) ? null : maps.getDirectory(path);
	}

	@Override
	public ByteBuffer read(final String filepath) throws IOException {
		final String path = inside(filepath);
		final FolderDataSource maps = this.maps;
		return (path == null) || (maps == null) ? null : maps.read(path);
	}

	@Override
	public IndependentStream.Opener opener(final String filepath) {
		final String path = inside(filepath);
		final FolderDataSource maps = this.maps;
		return (path == null) || (maps == null) ? null : maps.opener(path);
	}

	@Override
	public boolean has(final String filepath) {
		final String path = inside(filepath);
		final FolderDataSource maps = this.maps;
		return (path != null) && (maps != null) && maps.has(path);
	}

	@Override
	public Collection<String> getListfile() {
		final List<String> files = new ArrayList<>();
		final FolderDataSource maps = this.maps;
		if (maps == null) {
			return files;
		}
		for (final String file : maps.getListfile()) {
			files.add("Maps\\" + file.replace('/', '\\'));
		}
		return files;
	}

	@Override
	public void close() throws IOException {
		final FolderDataSource maps = this.maps;
		if (maps != null) {
			maps.close();
		}
	}
}
