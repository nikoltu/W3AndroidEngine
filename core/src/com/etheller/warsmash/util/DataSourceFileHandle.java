package com.etheller.warsmash.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;
import com.etheller.warsmash.datasources.DataSource;

public class DataSourceFileHandle extends FileHandle {
	private static final String[] SOUND_EXTENSIONS = { ".flac", ".wav", ".ogg", ".mp3" };

	private final DataSource dataSource;

	public DataSourceFileHandle(final DataSource dataSource, final String path) {
		super(resolveSoundPath(dataSource, path));
		this.dataSource = dataSource;
	}

	@Override
	public String path() {
		return file().getPath();
	}

	@Override
	public InputStream read() {
		try {
			return this.dataSource.getResourceAsStream(path());
		}
		catch (final IOException e) {
			throw new RuntimeException("Failed to load FileHandle from DataSource: " + path());
		}
	}

	/**
	 * Sound tables name one extension, but a game build may ship the sound in another
	 * format (3.0 tables say .flac while its Classic data has .ogg). The layer wins
	 * over the extension, so a Classic .ogg is preferred to a .flac of a later layer.
	 */
	public static String resolveSoundPath(final DataSource dataSource, final String path) {
		if (!isSoundPath(path)) {
			return path;
		}
		final int dotIndex = path.lastIndexOf('.');
		final String extension = path.substring(dotIndex).toLowerCase(Locale.US);
		final String basePath = path.substring(0, dotIndex);
		final String[] candidatePaths = new String[SOUND_EXTENSIONS.length];
		candidatePaths[0] = path;
		int candidateCount = 1;
		for (final String candidate : SOUND_EXTENSIONS) {
			if (!candidate.equals(extension)) {
				candidatePaths[candidateCount++] = basePath + candidate;
			}
		}
		final String found = dataSource.findFirst(candidatePaths);
		return found != null ? found : path;
	}

	public static boolean isSoundPath(final String path) {
		final int dotIndex = path.lastIndexOf('.');
		if ((dotIndex == -1) || (dotIndex < Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')))) {
			return false;
		}
		final String extension = path.substring(dotIndex).toLowerCase(Locale.US);
		for (final String candidate : SOUND_EXTENSIONS) {
			if (candidate.equals(extension)) {
				return true;
			}
		}
		return false;
	}
}
