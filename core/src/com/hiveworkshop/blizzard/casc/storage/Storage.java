package com.hiveworkshop.blizzard.casc.storage;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hiveworkshop.blizzard.casc.Key;
import com.hiveworkshop.blizzard.casc.nio.MalformedCASCStructureException;

/**
 * Main data storage of a CASC archive. It consists of index files which point
 * to storage containers in data files.
 */
public class Storage implements AutoCloseable {
	/**
	 * The name of the data folder containing the configuration files.
	 */
	public static final String DATA_FOLDER_NAME = "data";

	/**
	 * Number of index files used by a data store.
	 */
	private static final int INDEX_COUNT = 16;

	/**
	 * Usual number of copies of a specific index located in the folder. This is an
	 * estimate only used to increase search performance and will not effect
	 * results.
	 */
	private static final int INDEX_COPIES = 2;

	/**
	 * Size of the header in front of each file's data in a data file.
	 */
	private static final int CONTAINER_HEADER_SIZE = 30;

	private static final int READ_CHUNK = 1 << 16;

	/**
	 * File extension used by storage index files.
	 */
	public static final String INDEX_FILE_EXTENSION = "idx";

	/**
	 * File name of data files. 3 character extension is the index.
	 */
	public static final String DATA_FILE_NAME = "data";

	/**
	 * Largest permitted data file index.
	 */
	public static final int DATA_FILE_INDEX_MAXIMUM = 999;

	/**
	 * Extension length used by data files. Defined by the length needed to store
	 * DATA_FILE_INDEX_MAXIMUM as a decimal string.
	 */
	public static final int DATA_FILE_EXTENSION_LENGTH = 3;

	/**
	 * Converts an encoding key into an index file number.
	 *
	 * @param encodingKey Input encoding key.
	 * @param keyLength   Length of key to be processed.
	 * @return Index number.
	 */
	public static int getBucketIndex(final byte[] encodingKey, final int keyLength) {
		int accumulator = 0;
		for (int i = 0; i < keyLength; i += 1) {
			accumulator ^= encodingKey[i];
		}
		final int nibbleMask = (1 << 4) - 1;
		return (accumulator & nibbleMask) ^ ((accumulator >> 4) & nibbleMask);
	}

	private Path folder;

	private final HashMap<Integer, FileChannel> channelMap = new HashMap<>();

	private final IndexFile[] indicies = new IndexFile[INDEX_COUNT];

	/**
	 * Index file versions loaded. Possibly useful for debugging.
	 */
	private final long[] idxVersions = new long[INDEX_COUNT];

	/**
	 * Older copies of each index, newest first, read only when the current one
	 * points at data that cannot be the file.
	 */
	private final List<List<Path>> olderIndexPaths = new ArrayList<>();

	private final Map<Path, IndexFile> olderIndexFiles = new HashMap<>();

	/**
	 * Used to track closed status of the store.
	 */
	private boolean closed = false;

	private boolean useMemoryMapping;

	private int encodingKeyLength;

	/**
	 * Construct a storage object from the provided data folder.
	 * <p>
	 * Using memory mapping should give the best performance. However some platforms
	 * or file systems might not support it.
	 *
	 * @param dataFolder       Path of the CASC data folder.
	 * @param useOld           Use other (old?) version of index files.
	 * @param useMemoryMapping If IO should be memory mapped.
	 * @throws IOException If there was a problem loading from the data folder.
	 */
	public Storage(final Path dataFolder, final boolean useOld, final boolean useMemoryMapping) throws IOException {
		folder = dataFolder.resolve(DATA_FOLDER_NAME);
		this.useMemoryMapping = useMemoryMapping;

		final ArrayList<Path> indexFiles = new ArrayList<Path>(INDEX_COUNT * INDEX_COPIES);
		try (final DirectoryStream<Path> indexFileIterator = Files.newDirectoryStream(folder,
				"*." + INDEX_FILE_EXTENSION)) {
			for (final Path indexFile : indexFileIterator) {
				indexFiles.add(indexFile);
			}
		}

		class IndexFileNameMeta {
			private Path filePath;
			private int index;
			private long version;
		}

		final HashMap<Integer, ArrayList<IndexFileNameMeta>> metaMap = new HashMap<Integer, ArrayList<IndexFileNameMeta>>(
				INDEX_COUNT);

		for (final Path indexFile : indexFiles) {
			final String fileName = indexFile.getFileName().toString();

			final IndexFileNameMeta fileMeta = new IndexFileNameMeta();
			fileMeta.filePath = indexFile;
			fileMeta.index = Integer.parseUnsignedInt(fileName.substring(0, 2), 16);
			fileMeta.version = Long.parseUnsignedLong(fileName.substring(2, 10), 16);

			ArrayList<IndexFileNameMeta> bucketList = metaMap.get(fileMeta.index);
			if (bucketList == null) {
				bucketList = new ArrayList<>();
				metaMap.put(fileMeta.index, bucketList);
			}

			bucketList.add(fileMeta);
		}

		Comparator<IndexFileNameMeta> bucketOrder = (left, right) -> {
			return (int) (left.version - right.version);
		};
		if (!useOld) {
			bucketOrder = Collections.reverseOrder(bucketOrder);
		}

		for (int index = 0; index < indicies.length; index += 1) {
			final ArrayList<IndexFileNameMeta> bucketList = metaMap.get(index);
			if (bucketList == null) {
				throw new MalformedCASCStructureException("storage index file missing");
			}

			Collections.sort(bucketList, bucketOrder);

			final IndexFileNameMeta fileMeta = bucketList.get(0);
			final List<Path> older = new ArrayList<>();
			for (int copy = 1; copy < bucketList.size(); copy += 1) {
				older.add(bucketList.get(copy).filePath);
			}
			olderIndexPaths.add(older);
			idxVersions[index] = fileMeta.version;
			indicies[index] = new IndexFile(loadFileFully(fileMeta.filePath));
		}

		// resolve index key length being used
		int index = 0;
		encodingKeyLength = indicies[index++].getEncodingKeyLength();
		for (; index < indicies.length; index += 1) {
			if (encodingKeyLength != indicies[index].getEncodingKeyLength()) {
				throw new MalformedCASCStructureException("inconsistent encoding key length between index files");
			}
		}
	}

	@Override
	public synchronized void close() throws IOException {
		if (closed) {
			return;
		}

		IOException exception = null;
		for (final Map.Entry<Integer, FileChannel> channelEntry : channelMap.entrySet()) {
			try {
				channelEntry.getValue().close();
			} catch (final IOException e) {
				if (exception != null) {
					exception.addSuppressed(e);
				} else {
					exception = e;
				}
			}
		}

		closed = true;

		if (exception != null) {
			throw new IOException("IOExceptions occured during closure", exception);
		}
	}

	public boolean hasBanks(final Key encodingKey) {
		final int bucketIndex = getBucketIndex(encodingKey.getKey(), encodingKeyLength);
		final IndexFile index = indicies[bucketIndex];
		final IndexEntry indexEntry = index.getEntry(encodingKey);

		return indexEntry != null;
	}

	public BankStream getBanks(final Key encodingKey) throws IOException {
		final int bucketIndex = getBucketIndex(encodingKey.getKey(), encodingKeyLength);
		final IndexFile index = indicies[bucketIndex];
		final IndexEntry indexEntry = index.getEntry(encodingKey);

		if (indexEntry == null) {
			throw new FileNotFoundException("encoding key not in store indicies");
		}

		try {
			return openBanks(index, indexEntry);
		} catch (final MalformedCASCStructureException e) {
			// Some installs keep a key in more than one index entry, or in an older index, with only one of them
			// pointing at the file.
			for (final IndexFile candidate : candidateIndices(bucketIndex)) {
				for (final IndexEntry entry : candidate.getEntries(encodingKey)) {
					if ((candidate == index) && (entry == indexEntry)) {
						continue;
					}
					try {
						return openBanks(candidate, entry);
					} catch (final MalformedCASCStructureException | EOFException again) {
						continue;
					}
				}
			}
			throw e;
		}
	}

	private List<IndexFile> candidateIndices(final int bucketIndex) throws IOException {
		final List<IndexFile> candidates = new ArrayList<>();
		candidates.add(indicies[bucketIndex]);
		for (final Path olderPath : olderIndexPaths.get(bucketIndex)) {
			candidates.add(loadOlderIndex(olderPath));
		}
		return candidates;
	}

	/**
	 * Opens the data an index entry points at. Some installs point an entry past
	 * the file's container header, at its BLTE data or at zeros: the header is then
	 * looked for 30 bytes earlier, where it counts only with the entry's key, and
	 * BLTE data without a header counts when its header hashes to the key. Data
	 * found either way has its banks checked against their hashes.
	 */
	private BankStream openBanks(final IndexFile index, final IndexEntry indexEntry) throws IOException {
		final long dataOffset = indexEntry.getDataOffset();
		final int storeIndex = index.getStoreIndex(dataOffset);
		final long storeOffset = index.getStoreOffset(dataOffset);

		final ByteBuffer storageBuffer = getStorageBuffer(storeIndex, storeOffset, indexEntry.getFileSize());
		final boolean blte = BLTEContent.hasIdentifier(storageBuffer);
		if (!blte) {
			try {
				return new BankStream(storageBuffer, indexEntry.getKey());
			} catch (final MalformedCASCStructureException e) {
				final BankStream earlier = withEarlierHeader(storeIndex, storeOffset, indexEntry);
				if (earlier == null) {
					throw e;
				}
				return earlier;
			}
		}
		final BankStream earlier = withEarlierHeader(storeIndex, storeOffset, indexEntry);
		if (earlier != null) {
			return earlier;
		}
		final BankStream bare = new BankStream(storageBuffer, indexEntry.getKey());
		bare.verifyBankHashes();
		return bare;
	}

	private BankStream withEarlierHeader(final int storeIndex, final long storeOffset, final IndexEntry indexEntry)
			throws IOException {
		if (storeOffset < CONTAINER_HEADER_SIZE) {
			return null;
		}
		try {
			final ByteBuffer withHeader = getStorageBuffer(storeIndex, storeOffset - CONTAINER_HEADER_SIZE,
					indexEntry.getFileSize());
			if (BLTEContent.hasIdentifier(withHeader)) {
				return null;
			}
			final BankStream stream = new BankStream(withHeader, indexEntry.getKey());
			stream.verifyBankHashes();
			return stream;
		} catch (final MalformedCASCStructureException | EOFException e) {
			return null;
		}
	}

	private synchronized IndexFile loadOlderIndex(final Path path) throws IOException {
		IndexFile older = olderIndexFiles.get(path);
		if (older == null) {
			older = new IndexFile(loadFileFully(path));
			olderIndexFiles.put(path, older);
		}
		return older;
	}

	private synchronized FileChannel getDataFileChannel(final int index) throws IOException {
		if (closed) {
			throw new ClosedChannelException();
		}

		FileChannel fileChannel = channelMap.get(index);
		// A thread interrupted while reading closes the channel for every reader of this storage.
		if ((fileChannel == null) || !fileChannel.isOpen()) {
			if (index > DATA_FILE_INDEX_MAXIMUM) {
				throw new MalformedCASCStructureException("storage data file index too large");
			}

			final StringBuilder builder = new StringBuilder();
			builder.append(DATA_FILE_NAME);
			builder.append('.');
			final String extensionNumber = Integer.toUnsignedString(index);
			final int extensionZeroCount = DATA_FILE_EXTENSION_LENGTH - extensionNumber.length();
			for (int i = 0; i < extensionZeroCount; i += 1) {
				builder.append('0');
			}
			builder.append(extensionNumber);

			final Path filePath = folder.resolve(builder.toString());
			fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
			channelMap.put(index, fileChannel);
		}

		return fileChannel;
	}

	/**
	 * Reads into the buffer until it is full or the file ends. A channel reads a heap buffer through a direct buffer as
	 * big as the read and keeps that on the reading thread for its later reads; Android counts those on the Java heap,
	 * so each read is kept small.
	 */
	private static void readFully(final FileChannel channel, final ByteBuffer buffer, final long offset)
			throws IOException {
		final int capacity = buffer.capacity();
		do {
			buffer.limit(Math.min(capacity, buffer.position() + READ_CHUNK));
		}
		while (buffer.hasRemaining() && (channel.read(buffer, offset + buffer.position()) != -1));
		buffer.limit(capacity);
	}

	/**
	 * Fetch a buffer from storage.
	 *
	 * @param index  Data file index.
	 * @param offset Data file offset.
	 * @param length Requested buffer length.
	 * @return Storage buffer.
	 * @throws IOException If a problem occurs when preparing the storage buffer.
	 */
	private ByteBuffer getStorageBuffer(final int index, final long offset, final long length) throws IOException {
		final FileChannel fileChannel = getDataFileChannel(index);
		if (length > Integer.MAX_VALUE) {
			throw new MalformedCASCStructureException("data buffer too large to process");
		}

		final ByteBuffer storageBuffer;
		if (useMemoryMapping) {
			final MappedByteBuffer mappedBuffer = fileChannel.map(MapMode.READ_ONLY, offset, length);
			mappedBuffer.load();
			storageBuffer = mappedBuffer;
		} else {
			storageBuffer = ByteBuffer.allocate((int) length);
			readFully(fileChannel, storageBuffer, offset);

			if (storageBuffer.hasRemaining()) {
				throw new EOFException("unexpected end of file");
			}
			storageBuffer.clear();
		}

		return storageBuffer;
	}

	/**
	 * Loads a file fully into memory. Memory mapping is used if allowed.
	 *
	 * @param file Path of file to load into memory.
	 * @return File buffered into memory.
	 * @throws IOException If an IO exception occurs.
	 */
	private ByteBuffer loadFileFully(final Path file) throws IOException {
		final ByteBuffer fileBuffer;
		try (final FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
			final long fileLength = channel.size();
			if (fileLength > Integer.MAX_VALUE) {
				throw new MalformedCASCStructureException("file too large to process");
			}

			if (useMemoryMapping) {
				final MappedByteBuffer mappedBuffer = channel.map(MapMode.READ_ONLY, 0, fileLength);
				mappedBuffer.load();
				fileBuffer = mappedBuffer;
			} else {
				fileBuffer = ByteBuffer.allocate((int) fileLength);
				readFully(channel, fileBuffer, 0);

				if (fileBuffer.hasRemaining()) {
					throw new EOFException("unexpected end of file");
				}
				fileBuffer.clear();
			}
		}

		return fileBuffer;
	}

}
