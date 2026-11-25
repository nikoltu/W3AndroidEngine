package mpq;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;

import mpq.data.RawArrays;
import mpq.util.Cryption;

import com.etheller.warsmash.util.Log;

public class ArchivedFile implements Serializable {
	private static final long serialVersionUID = 5033693351138253083L;
	
	// CRC is Adler?
	// CRC requires version safety check.
	// Specification is unclear when [CRC block size > archive block size]. Assuming this never happens. May need to handle as special case.
	// Specification is unclear when [(file is single unit) equals TRUE AND (file uses CRC) equals TRUE]. Assuming flag is ignored.
	// Single Unit requires version safety check.
	
	public boolean ready;
	public int blockShift;
	public final int compressedSize;
	public final int fileSize;
	public final int flags;
	public final long fileOffset;
	public int[] blockOffsets;
	public int[] blockChecksums = null;
	public final int key;
	public final byte compression;
	private final int archiveShift;
	private boolean singleUnit;
	private boolean layoutChecked;
	
	public ArchivedFile( MPQArchive archive, HashLookup search, BlockTable.Entry file ) throws MPQException{
		// *** load simple values
		compressedSize = file.compressedSize;
		fileSize = file.fileSize;
		flags = file.flags;
		fileOffset = file.filePosition + archive.getArchiveOffset();
		// a protector's decoy block can hold any numbers
		if( fileSize < 0 || compressedSize < 0 ) throw new MPQException("invalid block: size out of range");
		
		// *** load complex values
		archiveShift = archive.getBlockShift();
		singleUnit = hasFlag(BlockTable.FLAG_SINGLE_UNIT);
		if( singleUnit ) blockShift = -fileSize;
		else blockShift = archiveShift;
		
		if( hasFlag(BlockTable.FLAG_ENCRYPTED) ){
			int key = Cryption.HashString(search.lookup, Cryption.MPQ_HASH_FILE_KEY);
			if( hasFlag(BlockTable.FLAG_FIX_KEY) ){
				key = Cryption.adjustFileDecryptKey(key, file.getFilePosition(), file.getFileSize());
			}
			this.key = key;
		}else
			key = 0;
		
		if( hasFlag(BlockTable.FLAG_COMPRESS | BlockTable.FLAG_IMPLODE) ){
			// blocks cannot be both compressed and imploded
			if( hasFlag(BlockTable.FLAG_COMPRESS) && hasFlag(BlockTable.FLAG_IMPLODE) ) throw new MPQException("invalid block: a block is both compressed and imploded");
			
			// determine the type of sector compression to use
			if( hasFlag(BlockTable.FLAG_COMPRESS) ){
				if( archive.getVersion() > 1 ) compression = 3;
				else compression = 2;
			}else{
				compression = 1;
			}
			
			// all compressed files use sector tables for standardization
			// single unit files have a known table
			if( singleUnit ){
				
				blockOffsets = new int[2];
				blockOffsets[1] = compressedSize;
				blockChecksums = null;
				ready = true;
			// table will need to be looked up
			}else{
				blockOffsets = new int[sectorCount() + 1];
				ready = false;
			}
		}else{
			compression = 0;
			blockOffsets = null;
			ready = true;
		}
	}
	
	/** The bytes of a sector; a shift a protector made too large for a sector in memory only means one sector. */
	public int sectorSize(){
		if( singleUnit ) return fileSize;
		return (int) Math.min(512L << Math.min(Math.max(archiveShift, 0), 40), 1 << 30);
	}
	
	public boolean isSingleUnit(){
		return singleUnit;
	}
	
	// the sectors of a file stored in sectors, with the one more of checksums that the CRC flag adds
	private int sectorCount(){
		int sectorSize = (int) Math.min(512L << Math.min(Math.max(archiveShift, 0), 40), 1 << 30);
		int blockn = (int) (((long) fileSize + sectorSize - 1) / sectorSize);
		
		// if CRC is used, there is an additional checksum sector with checksums for all other sectors.
		if( hasFlag(BlockTable.FLAG_SECTOR_CRC) ) blockn+= 1;
		return blockn;
	}
	
	/**
	 * Warcraft III's Storm does not know the single unit flag. A protector sets it on files that are stored in
	 * sectors, with a sector table, and the game reads them that way. A file of Reforged's editor that really is one
	 * unit has no table, so the start of the data tells which of the two a flagged file is.
	 */
	public synchronized void resolveLayout( SeekableByteChannel in ) throws IOException{
		if( layoutChecked ) return;
		layoutChecked = true;
		if( !singleUnit || compression == 0 ) return;
		
		int tableBytes = (sectorCount() + 1) * 4;
		if( compressedSize < tableBytes ) return;
		ByteBuffer head = ByteBuffer.allocate(8);
		in.position( fileOffset );
		while( head.hasRemaining() )
			if( in.read(head) == -1 )
				return;
		head.rewind();
		if( hasFlag(BlockTable.FLAG_ENCRYPTED) ){
			Cryption.decryptData(head, head, key - 1);
		}
		head.order(ByteOrder.LITTLE_ENDIAN);
		int first = head.getInt(0);
		int second = head.getInt(4);
		if( first != tableBytes || second < first || second > compressedSize ) return;
		
		singleUnit = false;
		blockShift = archiveShift;
		blockOffsets = new int[sectorCount() + 1];
		blockChecksums = null;
		ready = false;
	}
	
	public void loadOffsets( SeekableByteChannel in ) throws IOException, MPQException{
		// read sector table from file
		ByteBuffer temp = ByteBuffer.allocate(blockOffsets.length * 4);
		in.position( fileOffset );
		while( temp.hasRemaining() )
			if( in.read(temp) == -1 )
				break;
		temp.rewind();
		
		// decrypt if required
		if( hasFlag(BlockTable.FLAG_ENCRYPTED) ){
			Cryption.decryptData(temp, temp, key - 1);
		}
		
		// interpret sector table
		RawArrays.getArray(temp, blockOffsets);
		
		// validate offsets in case of corruption
		if( blockOffsets[0] >= 0 && blockOffsets[0] < blockOffsets.length * 4 ||
				blockOffsets[0] < 0 && blockOffsets[blockOffsets.length - 1] > 0 ) throw new MPQException("block sector intersects sector offset table");
		else if( blockOffsets[0] < 0 || blockOffsets[0] > blockOffsets.length * 4 ) 
			Log.errorf("block at %X has detached sectors starting at %X (%d bytes from end of sector table)%n",
					fileOffset, fileOffset + blockOffsets[0], blockOffsets[0] - blockOffsets.length * 4);
		if( fileOffset + blockOffsets[0] < 0 || fileOffset + blockOffsets[blockOffsets.length - 1] > in.size() )
			throw new MPQException("block sector located outside channel");
		for( int i = 1, prevoff = blockOffsets[0] ; i < blockOffsets.length ; i+= 1){
			int curroff = blockOffsets[i];
			if( curroff < prevoff ) throw new MPQException("block sector with negative size");
			prevoff = curroff;
		}
		
		// load CRC sector if present
		if( hasFlag(BlockTable.FLAG_SECTOR_CRC) && blockOffsets[blockOffsets.length - 1] != blockOffsets[blockOffsets.length - 2] ){
			Log.warn("block sector CRC reading currently not supported");
		}
		
		ready = true;
	}
	
	public boolean hasFlag(int flag){
		return (flags & flag) != 0;
	}
}


