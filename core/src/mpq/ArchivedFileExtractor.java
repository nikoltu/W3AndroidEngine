package mpq;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

import mpq.compression.Compression;
import mpq.util.Cryption;

import com.etheller.warsmash.util.Log;

public class ArchivedFileExtractor {
	private Compression decompress = new Compression();
	
	/**
	 * @param blockSize the bytes of a whole sector; the buffer may be smaller when the file is smaller than one
	 */
	public ByteBuffer readBlock(ByteBuffer bufferold, SeekableByteChannel in, ArchivedFile file, int block, int blockSize) throws IOException, MPQException{
		// *** calculate the current block size
		int currentSize;
		if( file.fileSize < (long) (block + 1) * blockSize )
			currentSize = file.fileSize % blockSize;
		else
			currentSize = blockSize;
		
		// *** read block
		if( file.blockOffsets != null ){
			// use block offset table
			if( !file.ready ){
				file.loadOffsets(in);
			}
			if( block < 0 || block + 1 >= file.blockOffsets.length ) throw new MPQException("block sector outside the sector table");
			final long position = file.fileOffset + file.blockOffsets[block];
			// a decoy block of one unit names more bytes than the archive holds; a sector table was checked when loaded
			long storedBytes = (long) file.blockOffsets[block+1] - file.blockOffsets[block];
			if( file.isSingleUnit() ) storedBytes = Math.max(0, Math.min(storedBytes, in.size() - position));
			final int stored = (int) storedBytes;
			if( stored > bufferold.capacity() ){
				// a buffer sized to a small file, and a sector stored in more bytes than it holds
				bufferold = ByteBuffer.allocate(stored);
			}
			bufferold.limit(stored);
			in.position(position);
		}else{
			// compute offset
			bufferold.limit(currentSize);
			in.position(file.fileOffset + (long) blockSize * block);
		}
		while( bufferold.hasRemaining() )
			if( in.read(bufferold) == -1 )
				break;
		bufferold.rewind();
		
		// *** decrypt if required
		if( file.key != 0 ){
			Cryption.decryptData(bufferold, bufferold, file.key + block);
		}
		
		// *** CRC check goes here
		if( file.blockChecksums != null ){
			// TODO add support for CRC
			Log.warn("block sector CRC validation currently not supported");
		}
				
		// *** decompress if required
		if( file.compression > 0 ){
			// only decompress if block is compressed
			if( bufferold.limit() < currentSize ){				
				// decompress block; a part of a sector takes a buffer of its own size, not a cached sector-sized one
				// shifts that give sectors too big to keep around are allocated at the size they are used
				final int size = currentSize == blockSize && file.blockShift >= 0 && file.blockShift <= 16 ? file.blockShift : -currentSize;
				if( file.compression >= 3 ){
					bufferold = decompress.blockDecompress3(bufferold, size);
				}else if( file.compression == 2 ){
					bufferold = decompress.blockDecompress2(bufferold, size);
				}else{
					bufferold = decompress.blockDecompress1(bufferold, size);
				}
			}
		}
		return bufferold;
	}
}
