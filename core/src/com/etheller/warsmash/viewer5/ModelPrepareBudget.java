package com.etheller.warsmash.viewer5;

/**
 * How many model files the helper reads and parses ahead of the builds on the game thread. A parsed model holds its
 * vertices and every key of its animations on the Java heap until it is built, a few frames' work each, and an HD
 * model holds several times what a Classic one does: when a map started with dozens of new HD unit types, the parsed
 * models waiting for their build filled a phone's 512 MB heap. Past this budget the rest wait as file names.
 */
final class ModelPrepareBudget {
	/**
	 * The file bytes of the started models that are not built yet (their parsed form takes a few times that on the
	 * heap); a started model whose file is not read yet counts as large as the files read so far on average.
	 */
	static final long MAX_FILE_BYTES = 24L << 20;
	/** Started models whose file is not read yet: queued for the helper, holding nothing but a stream to open. */
	static final int MAX_UNREAD = 8;

	private long readFiles;
	private long readBytes;

	/** Notes the size of a file the helper has read, for the average. */
	void read(final long fileBytes) {
		this.readFiles++;
		this.readBytes += fileBytes;
	}

	long averageFileBytes() {
		return this.readFiles == 0 ? 0 : this.readBytes / this.readFiles;
	}

	/**
	 * Whether another model may be started now.
	 *
	 * @param unread    started models whose file the helper has not read yet
	 * @param readBytes the file bytes of started models that are read and not yet built
	 */
	boolean admits(final int unread, final long readBytes) {
		if ((unread == 0) && (readBytes == 0)) {
			// one at a time always goes, however big
			return true;
		}
		return (unread < MAX_UNREAD) && ((readBytes + (unread * averageFileBytes())) < MAX_FILE_BYTES);
	}
}
