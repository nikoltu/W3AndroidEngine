package mpq;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import mpq.util.Cryption;

/**
 * Archives built in the test, small and with the tricks that map protectors rely on Warcraft III's Storm to put up
 * with: decoy hash entries, several locales of a file, bogus headers ahead of the real one, flags Storm does not know.
 */
class MPQArchiveTest {
	private static final int EXISTS = BlockTable.FLAG_EXISTS;
	private static final int COMPRESS = BlockTable.FLAG_COMPRESS;
	private static final int ENCRYPTED = BlockTable.FLAG_ENCRYPTED;
	private static final int SINGLE_UNIT = BlockTable.FLAG_SINGLE_UNIT;
	private static final int ENGLISH = 0x409;
	private static final int GERMAN = 0x407;
	private static final int FRENCH = 0x40C;

	private static final byte[] TEXT = ("function main takes nothing returns nothing\n"
			+ "    call BJDebugMsg(\"hello hello hello hello hello hello\")\nendfunction\n").repeat(20)
					.getBytes(StandardCharsets.UTF_8);

	@AfterEach
	void restoreLocale() {
		MPQArchive.setPreferredLocale(ENGLISH);
	}

	/** Lays an archive out: header, file data, hash table, block table, and the bytes ahead of the header. */
	private static final class Builder {
		private final ByteArrayOutputStream data = new ByteArrayOutputStream();
		private final List<int[]> blocks = new ArrayList<>();
		private final int hashCount;
		private final long[] hashes;
		private final int[] locales;
		private final int[] blockIndices;
		private int shift = 3;
		private int version = 0;
		private boolean junkExtendedTable;

		Builder(final int hashCount) {
			this.hashCount = hashCount;
			hashes = new long[hashCount];
			locales = new int[hashCount];
			blockIndices = new int[hashCount];
			java.util.Arrays.fill(blockIndices, HashTable.BLOCK_EMPTY_ALWAYS);
		}

		Builder shift(final int shift) {
			this.shift = shift;
			return this;
		}

		Builder version(final int version) {
			this.version = version;
			return this;
		}

		Builder junkExtendedTable() {
			this.junkExtendedTable = true;
			return this;
		}

		int block(final byte[] stored, final int fileSize, final int flags) {
			final int position = data.size();
			data.write(stored, 0, stored.length);
			blocks.add(new int[] { position, stored.length, fileSize, flags });
			return blocks.size() - 1;
		}

		int file(final byte[] content) {
			return block(content, content.length, EXISTS);
		}

		int firstSlot(final String name) {
			return new HashLookup(name).index & (hashCount - 1);
		}

		/** Puts entries for a name in the slots from where its probing begins, one after the other. */
		void chain(final String name, final int... localeAndBlock) {
			final HashLookup lookup = new HashLookup(name);
			int slot = firstSlot(name);
			for (int i = 0; i < localeAndBlock.length; i += 2) {
				while (blockIndices[slot] != HashTable.BLOCK_EMPTY_ALWAYS) {
					slot = (slot + 1) & (hashCount - 1);
				}
				hashes[slot] = lookup.hash;
				locales[slot] = localeAndBlock[i];
				blockIndices[slot] = localeAndBlock[i + 1];
			}
		}

		byte[] build(final byte[] prefix) {
			final ByteBuffer hashTable = ByteBuffer.allocate(hashCount * 16).order(ByteOrder.LITTLE_ENDIAN);
			for (int i = 0; i < hashCount; i++) {
				hashTable.putLong(hashes[i]).putShort((short) locales[i]).putShort((short) 0).putInt(blockIndices[i]);
			}
			final int headerSize = junkExtendedTable ? 44 : 32;
			final ByteBuffer blockTable = ByteBuffer.allocate(blocks.size() * 16).order(ByteOrder.LITTLE_ENDIAN);
			for (final int[] block : blocks) {
				blockTable.putInt(headerSize + block[0]).putInt(block[1]).putInt(block[2]).putInt(block[3]);
			}
			encrypt(hashTable.array(), Cryption.KEY_HASH_TABLE);
			encrypt(blockTable.array(), Cryption.KEY_BLOCK_TABLE);

			final int hashPosition = headerSize + data.size();
			final int blockPosition = hashPosition + hashTable.capacity();
			final int extendedPosition = blockPosition + blockTable.capacity();
			final int archiveSize = extendedPosition + (junkExtendedTable ? blocks.size() * 2 : 0);
			final ByteBuffer header = ByteBuffer.allocate(junkExtendedTable ? 44 : 32).order(ByteOrder.LITTLE_ENDIAN);
			header.put(new byte[] { 'M', 'P', 'Q', 0x1a });
			// protectors put 2 MB here; Storm does not look at it
			header.putInt(junkExtendedTable ? 0x200102 : 32).putInt(archiveSize).putShort((short) version)
					.putShort((short) shift);
			header.putInt(hashPosition).putInt(blockPosition).putInt(hashCount).putInt(blocks.size());
			if (junkExtendedTable) {
				header.putLong(extendedPosition).putShort((short) 0).putShort((short) 0);
			}
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			out.write(prefix, 0, prefix.length);
			out.write(header.array(), 0, header.capacity());
			final byte[] fileData = data.toByteArray();
			out.write(fileData, 0, fileData.length);
			out.write(hashTable.array(), 0, hashTable.capacity());
			out.write(blockTable.array(), 0, blockTable.capacity());
			if (junkExtendedTable) {
				for (int i = 0; i < blocks.size(); i++) {
					out.write(1);
					out.write(0);
				}
			}
			return out.toByteArray();
		}
	}

	private static void encrypt(final byte[] bytes, final int key) {
		final ByteBuffer buffer = ByteBuffer.wrap(bytes);
		Cryption.encryptData(buffer, buffer, key);
	}

	private static byte[] deflate(final byte[] content) {
		final Deflater deflater = new Deflater();
		deflater.setInput(content);
		deflater.finish();
		final byte[] buffer = new byte[content.length + 64];
		final int length = deflater.deflate(buffer);
		deflater.end();
		final byte[] out = new byte[length + 1];
		out[0] = 0x02;
		System.arraycopy(buffer, 0, out, 1, length);
		return out;
	}

	private static byte[] bzip2(final byte[] content) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(0x10);
		try (BZip2CompressorOutputStream stream = new BZip2CompressorOutputStream(out)) {
			stream.write(content);
		}
		return out.toByteArray();
	}

	/** A file of one sector as stored in an archive: the table of the sector's offsets, then the sector. */
	private static byte[] sectored(final byte[] sector, final String name, final boolean encrypted) {
		final ByteBuffer out = ByteBuffer.allocate(8 + sector.length).order(ByteOrder.LITTLE_ENDIAN);
		out.putInt(8).putInt(8 + sector.length).put(sector);
		final byte[] bytes = out.array();
		if (encrypted) {
			final int key = Cryption.HashString(name, Cryption.MPQ_HASH_FILE_KEY);
			final byte[] table = java.util.Arrays.copyOf(bytes, 8);
			encrypt(table, key - 1);
			final byte[] body = java.util.Arrays.copyOfRange(bytes, 8, bytes.length);
			encrypt(body, key);
			System.arraycopy(table, 0, bytes, 0, 8);
			System.arraycopy(body, 0, bytes, 8, body.length);
		}
		return bytes;
	}

	private static byte[] read(final byte[] archiveBytes, final String name) throws IOException, MPQException {
		final SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(archiveBytes);
		final MPQArchive archive = new MPQArchive(channel);
		final ArchivedFile file = archive.lookupHash2(new HashLookup(name));
		try (InputStream in = Channels
				.newInputStream(new ArchivedFileStream(channel, new ArchivedFileExtractor(), file))) {
			return in.readAllBytes();
		}
	}

	private static byte[] bytes(final String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void entriesPastTheBlockTableAndDeletedOnesAreSkipped() throws Exception {
		final Builder builder = new Builder(64);
		final int real = builder.file(bytes("real"));
		// the decoys come first in the chain: a block that does not exist, a deletion, then the file
		builder.chain("war3map.j", 0, 5000, 0, HashTable.BLOCK_EMPTY_NOW, 0, real);
		assertArrayEquals(bytes("real"), read(builder.build(new byte[0]), "war3map.j"));
	}

	@Test
	void aBlockThatIsNoFileIsSkipped() throws Exception {
		final Builder builder = new Builder(64);
		final int hole = builder.block(bytes("hole"), 4, 0);
		final int marker = builder.block(bytes("gone"), 4, EXISTS | BlockTable.FLAG_DELETE_MARKER);
		final int real = builder.file(bytes("real"));
		builder.chain("war3map.w3i", 0, hole, 0, marker, 0, real);
		assertArrayEquals(bytes("real"), read(builder.build(new byte[0]), "war3map.w3i"));
	}

	@Test
	void aNameOnlyDecoysHaveIsNotFound() throws Exception {
		final Builder builder = new Builder(64);
		builder.file(bytes("x"));
		builder.chain("war3map.j", 0, 5000, 0, HashTable.BLOCK_EMPTY_NOW);
		final byte[] archive = builder.build(new byte[0]);
		final MPQArchive opened = new MPQArchive(new SeekableInMemoryByteChannel(archive));
		assertThrows(MPQException.class, () -> opened.lookupPath("war3map.j"));
	}

	@Test
	void theLocaleOfTheGameIsPreferredThenTheNeutralOne() throws Exception {
		final Builder builder = new Builder(64);
		final int neutral = builder.file(bytes("neutral"));
		final int english = builder.file(bytes("english"));
		final int german = builder.file(bytes("german"));
		builder.chain("war3map.j", 0, neutral, GERMAN, german, ENGLISH, english);
		final byte[] archive = builder.build(new byte[0]);

		MPQArchive.setPreferredLocale(ENGLISH);
		assertArrayEquals(bytes("english"), read(archive, "war3map.j"));
		MPQArchive.setPreferredLocale(GERMAN);
		assertArrayEquals(bytes("german"), read(archive, "war3map.j"));
		MPQArchive.setPreferredLocale(FRENCH);
		assertArrayEquals(bytes("neutral"), read(archive, "war3map.j"));
	}

	@Test
	void withoutANeutralEntryTheFirstOneIsRead() throws Exception {
		final Builder builder = new Builder(64);
		final int german = builder.file(bytes("german"));
		final int english = builder.file(bytes("english"));
		builder.chain("war3map.j", GERMAN, german, ENGLISH, english);
		MPQArchive.setPreferredLocale(FRENCH);
		assertArrayEquals(bytes("german"), read(builder.build(new byte[0]), "war3map.j"));
	}

	@Test
	void aSingleEntryIsReadInAnyLocale() throws Exception {
		final Builder builder = new Builder(64);
		builder.chain("war3map.j", 0, builder.file(bytes("only")));
		MPQArchive.setPreferredLocale(GERMAN);
		assertArrayEquals(bytes("only"), read(builder.build(new byte[0]), "war3map.j"));
	}

	@Test
	void decoyHeadersAheadOfTheRealOneAreSkipped() throws Exception {
		final Builder builder = new Builder(64);
		builder.chain("war3map.j", 0, builder.file(bytes("real")));

		// 0: a user data header that points into nothing; 512: an archive header whose tables are not in the file;
		// 1024: a user data header that points at the decoy; the real header is at 2048
		final byte[] prefix = new byte[2048];
		final ByteBuffer bytes = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN);
		bytes.position(0).put(new byte[] { 'M', 'P', 'Q', 0x1b }).putInt(512).putInt(100_000_000).putInt(16);
		bytes.position(512).put(new byte[] { 'M', 'P', 'Q', 0x1a }).putInt(0x200102).putInt(77).putShort((short) 0)
				.putShort((short) 3).putInt(0x7000_0000).putInt(0x7000_1000).putInt(64).putInt(5);
		bytes.position(1024).put(new byte[] { 'M', 'P', 'Q', 0x1b }).putInt(512).putInt(512).putInt(16);
		final byte[] archive = builder.build(prefix);
		assertEquals(2048, new MPQArchive(new SeekableInMemoryByteChannel(archive)).getArchiveOffset());
		assertArrayEquals(bytes("real"), read(archive, "war3map.j"));
	}

	@Test
	void aUserDataHeaderThatPointsAtTheArchiveIsFollowed() throws Exception {
		final Builder builder = new Builder(64);
		builder.chain("war3map.j", 0, builder.file(bytes("real")));
		// a header that is not 512 aligned, which only the user data header can lead to
		final byte[] prefix = new byte[512 + 100];
		final ByteBuffer bytes = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN);
		bytes.put(new byte[] { 'M', 'P', 'Q', 0x1b }).putInt(100).putInt(612).putInt(16);
		assertArrayEquals(bytes("real"), read(builder.build(prefix), "war3map.j"));
	}

	@Test
	void aFileMarkedSingleUnitThatIsStoredInSectorsIsReadAsSectors() throws Exception {
		final Builder builder = new Builder(64);
		for (final boolean encrypted : new boolean[] { false, true }) {
			final String name = encrypted ? "war3map.w3u" : "war3map.w3e";
			final int flags = EXISTS | COMPRESS | SINGLE_UNIT | (encrypted ? ENCRYPTED : 0);
			builder.chain(name, 0, builder.block(sectored(deflate(TEXT), name, encrypted), TEXT.length, flags));
		}
		final byte[] archive = builder.build(new byte[0]);
		assertArrayEquals(TEXT, read(archive, "war3map.w3e"));
		assertArrayEquals(TEXT, read(archive, "war3map.w3u"));
	}

	@Test
	void aFileThatReallyIsOneUnitIsStillReadAsOne() throws Exception {
		final Builder builder = new Builder(64);
		builder.chain("war3map.j", 0, builder.block(deflate(TEXT), TEXT.length, EXISTS | COMPRESS | SINGLE_UNIT));
		assertArrayEquals(TEXT, read(builder.build(new byte[0]), "war3map.j"));
	}

	@Test
	void bzip2SectorsAreRead() throws Exception {
		final Builder builder = new Builder(64);
		builder.chain("war3map.j", 0,
				builder.block(sectored(bzip2(TEXT), "war3map.j", false), TEXT.length, EXISTS | COMPRESS));
		assertArrayEquals(TEXT, read(builder.build(new byte[0]), "war3map.j"));
	}

	@Test
	void aSectorShiftOfTwentyOneAndJunkInTheExtendedBlockTableDoNotMatter() throws Exception {
		final Builder builder = new Builder(64).shift(21).version(1).junkExtendedTable();
		builder.chain("war3map.j", 0,
				builder.block(sectored(deflate(TEXT), "war3map.j", true), TEXT.length, EXISTS | COMPRESS | ENCRYPTED));
		builder.chain("war3map.w3i", 0, builder.file(bytes("plain")));
		final byte[] archive = builder.build(new byte[0]);
		assertArrayEquals(TEXT, read(archive, "war3map.j"));
		assertArrayEquals(bytes("plain"), read(archive, "war3map.w3i"));
	}
}
