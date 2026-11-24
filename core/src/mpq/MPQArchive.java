package mpq;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;

import mpq.compression.Compression;
import mpq.data.BlockTableEntry;
import mpq.data.FileHeader;
import mpq.data.HashTableEntry;
import mpq.data.ArchiveHeader;
import mpq.data.RawArrays;
import mpq.data.UserDataHeader;
import mpq.util.Cryption;

import com.etheller.warsmash.util.Log;

public class MPQArchive {	
	private long archiveOffset;
	private short blockShift;
	private HashTable hashTable;
	private BlockTable blockTable;
	private short version;
	private long archiveSize;
	
	/** The locale a file is read in when an archive holds it for several; Storm is told the language of the game. */
	private static volatile int preferredLocale = 0x409;
	
	public static void setPreferredLocale(int locale){
		preferredLocale = locale & 0xFFFF;
	}
	
	public static int getPreferredLocale(){
		return preferredLocale;
	}
	
	// bytes of an archive header read at once: past the 8 of the identifier and size, enough for every format version
	private static final int HEADER_READ = 256;
	private static final int MAX_TABLE_ENTRIES = 1 << 22;
	
	private static boolean readFully(SeekableByteChannel in, long position, ByteBuffer buffer) throws IOException{
		in.position(position);
		while( buffer.hasRemaining() )
			if( in.read(buffer) == -1 )
				return false;
		return true;
	}
	
	/**
	 * Reads the archive header at an offset, or null when the bytes there are not one. Storm takes any 512 byte aligned
	 * "MPQ" without asking more of it, and protectors place bogus ones ahead of the real one; so the table
	 * positions and sizes of a candidate have to fit in the channel.
	 */
	private ArchiveHeader readHeaderAt(SeekableByteChannel in, long offset, long size, boolean strict) throws IOException{
		ByteBuffer id = ByteBuffer.allocate(FileHeader.STRUCT_SIZE);
		FileHeader header = new FileHeader(id);
		if( !readFully(in, offset, id) ) return null;
		if( header.getIdentifierInt() != FileHeader.ARCHIVE_IDENTIFIER_INT ) return null;
		
		// the size field is not trusted: protectors set it to 2 MB or to garbage, and Storm reads a fixed structure
		ByteBuffer buffer = ByteBuffer.allocate(HEADER_READ);
		ArchiveHeader archiveheader = new ArchiveHeader(buffer);
		in.position(offset + FileHeader.STRUCT_SIZE);
		while( buffer.hasRemaining() )
			if( in.read(buffer) == -1 )
				break;
		if( buffer.position() < ArchiveHeader.STRUCT_SIZE_V1 ) return null;
		
		if( strict ){
			long hashPosition = (long) archiveheader.getHashTablePosition() & 0xFFFFFFFFL;
			long blockPosition = (long) archiveheader.getBlockTablePosition() & 0xFFFFFFFFL;
			int hashCount = archiveheader.getHashTableSize();
			int blockCount = archiveheader.getBlockTableSize();
			if( hashCount <= 0 || hashCount > MAX_TABLE_ENTRIES || (hashCount & hashCount - 1) != 0 ) return null;
			if( blockCount < 0 || blockCount > MAX_TABLE_ENTRIES ) return null;
			if( offset + hashPosition + (long) hashCount * HashTableEntry.STRUCT_SIZE > size ) return null;
			if( offset + blockPosition + (long) blockCount * BlockTableEntry.STRUCT_SIZE > size ) return null;
		}
		return archiveheader;
	}
	
	// locates the archive header from within a SeekableByteChannel
	private ArchiveHeader locateArchive(SeekableByteChannel in) throws IOException, MPQException{
		long size = in.size();
		long start = in.position() & ~(512 - 1);
		ByteBuffer id = ByteBuffer.allocate(FileHeader.STRUCT_SIZE);
		FileHeader header = new FileHeader(id);
		
		// look for a header at every 512 bytes: the first that holds a plausible archive is the one
		long firstIdentifier = -1;
		for( long offset = start ; offset + FileHeader.STRUCT_SIZE <= size ; offset+= 512 ){
			id.clear();
			if( !readFully(in, offset, id) ) break;
			int identifier = header.getIdentifierInt();
			if( identifier == FileHeader.ARCHIVE_IDENTIFIER_INT ){
				if( firstIdentifier < 0 ) firstIdentifier = offset;
				ArchiveHeader archiveheader = readHeaderAt(in, offset, size, true);
				if( archiveheader != null ){
					archiveOffset = offset;
					return archiveheader;
				}
			// a user data header names where the archive header is, but a decoy one names something else
			}else if( identifier == FileHeader.USERDATA_IDENTIFIER_INT ){
				ByteBuffer pointer = ByteBuffer.allocate(UserDataHeader.STRUCT_SIZE).order(ByteOrder.LITTLE_ENDIAN);
				if( !readFully(in, offset + FileHeader.STRUCT_SIZE, pointer) ) continue;
				long target = offset + ((long) pointer.getInt(0) & 0xFFFFFFFFL);
				if( target <= offset || target + FileHeader.STRUCT_SIZE > size ) continue;
				ArchiveHeader archiveheader = readHeaderAt(in, target, size, true);
				if( archiveheader != null ){
					archiveOffset = target;
					return archiveheader;
				}
			}
		}
		
		// no candidate passed: use the first identifier as before, and let loading its tables report what is wrong
		if( firstIdentifier >= 0 ){
			ArchiveHeader archiveheader = readHeaderAt(in, firstIdentifier, size, false);
			if( archiveheader != null ){
				archiveOffset = firstIdentifier;
				return archiveheader;
			}
		}
		throw new MPQException("channel does not contain a MPQ archive");
	}
	
	// 
	private void deserializeHashTable(SeekableByteChannel in, ArchiveHeader archiveheader) throws IOException, MPQException{
		// *** deserialize header
		// version 1
		long htoffset = (long) archiveheader.getHashTablePosition() & 0xFFFFFFFFL;
		int htsize = archiveheader.getHashTableSize();
		int rsize = htsize * HashTableEntry.STRUCT_SIZE;
		// version 2; the high bits only matter in a channel that is larger than 32 bits reach, protectors fill them with junk
		if( version >= 1 && in.size() > 0xFFFFFFFFL ){
			htoffset|= ((long) archiveheader.getHashTablePositionHigh() & 0xFFFFL) << 32;
		}
		// version 3
		int csize;
		if( version >= 3 ){
			csize = (int) archiveheader.getHashTableSizeCompressed();
		}else{
			csize = rsize;
		}
		
		// *** validate hashtable
		// no hashtable to load
		if( htoffset == 0 ){
			hashTable = null;
			return;
		// hashtable size not power of 2
		}else if( (htsize & htsize - 1) != 0 )
			throw new MPQException("hashtable was not power of two ( was " + htsize + " )");
		
		// *** read MPQ archive hashtable
		ByteBuffer buffer = ByteBuffer.allocate(rsize);
		buffer.limit(csize);
		in.position(htoffset + archiveOffset);
		while( buffer.hasRemaining() )
			if( in.read(buffer) == -1 )
				break;
		buffer.rewind();
		
		// *** decrypt hashtable
		Cryption.decryptData(buffer, buffer, Cryption.KEY_HASH_TABLE);
		
		// *** decompress hashtable
		if( csize < rsize ){
			buffer = new Compression().blockDecompressAny(buffer, ByteBuffer.allocate(rsize));
			if( buffer.limit() != buffer.capacity() ) Log.warn("hashtable decompressed size did not match expected size");
		}
		
		// *** deserialize hashtable
		HashTable.Entry[] entries = new HashTable.Entry[htsize];
		HashTableEntry htentry = new HashTableEntry();
		for( int i = 0 ; i < htsize ; i+= 1 ){
			htentry.move(buffer);
			HashTable.Entry tempentry = new HashTable.Entry();
			tempentry.hash = htentry.getHash();
			tempentry.locale = htentry.getLocale();
			tempentry.platform = htentry.getPlatform();
			tempentry.blockIndex = htentry.getBlockIndex();
			entries[i] = tempentry;
			buffer.position(buffer.position() + HashTableEntry.STRUCT_SIZE);
		}
		hashTable = new HashTable(entries);
	}
	
	private void deserializeBlockTable(SeekableByteChannel in, ArchiveHeader archiveheader) throws IOException, MPQException{
		// *** deserialize header
		// version 1
		long btoffset = (long) archiveheader.getBlockTablePosition() & 0xFFFFFFFFL;
		int btsize = archiveheader.getBlockTableSize();
		int rsize = btsize * BlockTableEntry.STRUCT_SIZE;
		// version 2
		long hbtoffset;
		int rhsize;
		if( version >= 1 && in.size() > 0xFFFFFFFFL ){
			hbtoffset = archiveheader.getHighBlockTablePosition();
			rhsize = btsize * 2;
			btoffset|= ((long) archiveheader.getBlockTablePositionHigh() & 0xFFFFL) << 32;
		}else{
			hbtoffset = 0;
			rhsize = 0;
		}
		// version 4
		int csize;
		int chsize;
		if( version >= 3 ){
			csize = (int) archiveheader.getBlockTableSizeCompressed();
			chsize = (int) archiveheader.getHighBlockTableSizeCompressed();
		}else{
			csize = rsize;
			chsize = rhsize;
		}
		
		// *** validate blocktable
		// no blocktable to load
		if( btoffset == 0 ){
			blockTable = null;
			return;
		// blocktable size clamp
		}else if( btsize > 1 << 20 || btsize < 0 ){
			Log.warn("blocktable is stupidly large ( " + btsize + " ) so was clamped to " + (1 << 20));
			btsize = 1 << 20;
		}
		
		// *** read MPQ archive blocktable
		ByteBuffer buffer = ByteBuffer.allocate(rsize);
		buffer.limit(csize);
		in.position(btoffset + archiveOffset);
		while( buffer.hasRemaining() )
			if( in.read(buffer) == -1 )
				break;
		buffer.rewind();
		
		// *** decrypt blocktable
		Cryption.decryptData(buffer, buffer, Cryption.KEY_BLOCK_TABLE);
		
		// *** decompress blocktable
		if( csize < rsize ){
			buffer = new Compression().blockDecompressAny(buffer, ByteBuffer.allocate(rsize));
			if( buffer.limit() != buffer.capacity() ) Log.warn("blocktable decompressed size did not match expected size");
		}
		
		// *** deserialize blocktable
		BlockTable.Entry[] entries = new BlockTable.Entry[btsize];
		BlockTableEntry btentry = new BlockTableEntry();
		for( int i = 0 ; i < btsize ; i+= 1 ){
			btentry.move(buffer);
			BlockTable.Entry tempentry = new BlockTable.Entry();
			tempentry.filePosition = (long) btentry.getFilePosition() & 0xFFFFFFFFL;
			tempentry.compressedSize = btentry.getCompressedSize();
			tempentry.fileSize = btentry.getFileSize();
			tempentry.flags = btentry.getFlags();
			entries[i] = tempentry;
			buffer.position(buffer.position() + BlockTableEntry.STRUCT_SIZE);
		}
		
		// *** add high blocktable
		if( hbtoffset > 0 ){
			// read MPQ archive high blocktable
			buffer = ByteBuffer.allocate(rhsize);
			buffer.limit(chsize);
			in.position(hbtoffset + archiveOffset);
			while( buffer.hasRemaining() )
				if( in.read(buffer) == -1 )
					break;
			buffer.rewind();
			
			// decompress high blocktable
			if( chsize < rhsize ){
				buffer = new Compression().blockDecompressAny(buffer, ByteBuffer.allocate(rhsize));
				if( buffer.limit() != buffer.capacity() ) Log.warn("high blocktable decompressed size did not match expected size");
			}
			
			// deserialize high blocktable
			short[] highposarray = RawArrays.getShortArray(buffer);
			for( int i = 0 ; i < btsize ; i+= 1 ){
				entries[i].filePosition|= ((long) highposarray[i] & 0xFFFFL) << 32;
			}
		}
		blockTable = new BlockTable(entries);
	}
	
	public void loadArchive(SeekableByteChannel in, boolean fold) throws IOException, MPQException{
		// *** find archive header
		ArchiveHeader archiveheader = locateArchive(in);
		
		// *** deserialize archive globals
		archiveSize = (long) archiveheader.getArchiveSize() & 0xFFFFFFFFL;
		// force old allows support for Warcraft III archives with corrupted version field (not 0)
		if( fold ) version = 0;
		else version = archiveheader.getFormatVersion();
		blockShift = archiveheader.getBlockSize();
		if( version >= 2 ) archiveSize = archiveheader.getArchiveSizeLong();
		
		// *** deserialize archive components
		deserializeHashTable(in, archiveheader);
		deserializeBlockTable(in, archiveheader);
		if( version >= 2 ) Log.warn("het and bet tables not supported");
	}
	
	public MPQArchive(SeekableByteChannel in) throws MPQException, IOException{
		loadArchive(in, false);
	}
	
	public MPQArchive(){
	}
	
	public long getArchiveOffset() {
		return archiveOffset;
	}

	public short getBlockShift() {
		return blockShift;
	}

	public short getVersion() {
		return version;
	}

	public boolean isOffsetInArchive(long offset){
		return offset >= 0 && offset <= archiveSize;
	}
	
	public boolean isPositionInArchive(long position){
		return isOffsetInArchive(position - archiveOffset);
	}
	
	// a block is a file when it exists and is not a deletion marker; Storm opens nothing else
	private boolean isFileBlock(int block){
		int flags = blockTable.lookupEntry(block).flags;
		return (flags & BlockTable.FLAG_EXISTS) != 0 && (flags & BlockTable.FLAG_DELETE_MARKER) == 0;
	}
	
	private int lookupBlock(HashLookup hash) throws MPQException{
		if( hashTable == null || blockTable == null ) throw MPQException.LOOKUP_NOT_FOUND;
		return hashTable.lookupBlock(hash, blockTable.size(), preferredLocale, this::isFileBlock);
	}
	
	public int lookupPath(String path) throws MPQException{
		return lookupBlock(new HashLookup(path));
	}
	
	public BlockTable.Entry lookupHash(HashLookup hash) throws MPQException{
		return blockTable.lookupEntry(lookupBlock(hash));
	}
	
	public ArchivedFile lookupHash2(HashLookup hash) throws MPQException{
		return new ArchivedFile(this, hash, blockTable.lookupEntry(lookupBlock(hash)));
	}
}
