package com.etheller.warsmash.datasources;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import com.etheller.warsmash.util.FileReadChannel;

import mpq.ArchivedFile;
import mpq.ArchivedFileExtractor;
import mpq.ArchivedFileStream;
import mpq.HashLookup;
import mpq.MPQArchive;
import mpq.MPQException;

public class MpqDataSource implements DataSource {

	private final MPQArchive archive;
	private final SeekableByteChannel inputChannel;
	private final ArchivedFileExtractor extractor = new ArchivedFileExtractor();

	public MpqDataSource(final MPQArchive archive, final SeekableByteChannel inputChannel) {
		this.archive = archive;
		this.inputChannel = inputChannel;
	}

	public MPQArchive getArchive() {
		return this.archive;
	}

	public SeekableByteChannel getInputChannel() {
		return this.inputChannel;
	}

	@Override
	public InputStream getResourceAsStream(final String filepath) throws IOException {
		ArchivedFile file = null;
		try {
			file = this.archive.lookupHash2(new HashLookup(archivePath(filepath)));
		}
		catch (final MPQException exc) {
			if (exc.getMessage().equals("lookup not found")) {
				return null;
			}
			else {
				throw new IOException(exc);
			}
		}
		final ArchivedFileStream stream = new ArchivedFileStream(this.inputChannel, this.extractor, file);
		final InputStream newInputStream = Channels.newInputStream(stream);
		return newInputStream;
	}

	/**
	 * An archive read through a {@link FileReadChannel} can be read by a helper thread while the game thread reads it:
	 * that channel keeps its position per thread. One held in memory gives each stream a channel of its own over the
	 * same bytes. Each opened stream has an extractor of its own.
	 */
	@Override
	public IndependentStream.Opener opener(final String filepath) {
		final boolean inMemory = this.inputChannel instanceof SeekableInMemoryByteChannel;
		if (!inMemory && !(this.inputChannel instanceof FileReadChannel)) {
			return null;
		}
		final ArchivedFile file;
		try {
			file = this.archive.lookupHash2(new HashLookup(archivePath(filepath)));
		}
		catch (final MPQException exc) {
			return null;
		}
		return () -> {
			final SeekableByteChannel channel = inMemory
					? new SeekableInMemoryByteChannel(((SeekableInMemoryByteChannel) this.inputChannel).array())
					: this.inputChannel;
			return new IndependentStream.Buffered(
					Channels.newInputStream(new ArchivedFileStream(channel, new ArchivedFileExtractor(), file)), 65536);
		};
	}

	@Override
	public ByteBuffer read(final String path) throws IOException {
		ArchivedFile file = null;
		try {
			file = this.archive.lookupHash2(new HashLookup(archivePath(path)));
		}
		catch (final MPQException exc) {
			if (exc.getMessage().equals("lookup not found")) {
				return null;
			}
			else {
				throw new IOException(exc);
			}
		}
		try (final ArchivedFileStream stream = new ArchivedFileStream(this.inputChannel, this.extractor, file)) {
			final long size = stream.size();
			final ByteBuffer buffer = ByteBuffer.allocate((int) size);
			stream.read(buffer);
			return buffer;
		}
	}

	@Override
	public File getFile(final String filepath) throws IOException {
		// TODO Auto-generated method stub
		// System.out.println("getting it from the outside: " +
		// filepath);
		ArchivedFile file = null;
		try {
			file = this.archive.lookupHash2(new HashLookup(archivePath(filepath)));
		}
		catch (final MPQException exc) {
			if (exc.getMessage().equals("lookup not found")) {
				return null;
			}
			else {
				throw new IOException(exc);
			}
		}
		final File tempDir = new File(System.getProperty("java.io.tmpdir"), "RMSExtract");
		final File tempProduct = new File(tempDir, filepath.replace('\\', '/'));
		// Archive names come from the map: one with ".." must not write beside the extraction folder.
		if (!tempProduct.getCanonicalPath().startsWith(tempDir.getCanonicalPath() + File.separator)) {
			throw new IOException("Archive path outside the extraction folder: " + filepath);
		}
		tempProduct.delete();
		tempProduct.getParentFile().mkdirs();
		try (InputStream newInputStream = Channels
				.newInputStream(new ArchivedFileStream(this.inputChannel, this.extractor, file))) {
			Files.copy(newInputStream, tempProduct.toPath());
		}
		tempProduct.deleteOnExit();
		return tempProduct;
	}

	@Override
	public File getDirectory(final String filepath) throws IOException {
		return null;
	}

	@Override
	public boolean has(final String filepath) {
		try {
			this.archive.lookupPath(archivePath(filepath));
			return true;
		}
		catch (final MPQException exc) {
			if (exc.getMessage().equals("lookup not found")) {
				return false;
			}
			else {
				throw new RuntimeException(exc);
			}
		}
	}

	@Override
	public Collection<String> getListfile() {
		try {
			final Set<String> listfile = new HashSet<>();
			ArchivedFile listfileContents;
			listfileContents = this.archive.lookupHash2(new HashLookup("(listfile)"));
			final ArchivedFileStream stream = new ArchivedFileStream(this.inputChannel, this.extractor,
					listfileContents);
			final InputStream newInputStream = Channels.newInputStream(stream);
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(newInputStream))) {
				String line;
				while ((line = reader.readLine()) != null) {
					listfile.add(line);
				}
			}
			catch (final IOException exc) {
				throw new RuntimeException(exc);
			}
			return listfile;
		}
		catch (final MPQException exc) {
			if (exc.getMessage().equals("lookup not found")) {
				return null;
			}
			else {
				throw new RuntimeException(exc);
			}
		}
	}

	/** Like the original's archives, a path names the same file with / as with \. */
	private static String archivePath(final String filepath) {
		return filepath.replace('/', '\\');
	}

	@Override
	public void close() throws IOException {
		this.inputChannel.close();
	}

}
