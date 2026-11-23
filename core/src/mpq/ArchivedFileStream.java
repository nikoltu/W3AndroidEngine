package mpq;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

public class ArchivedFileStream implements SeekableByteChannel{
	private boolean open;
	private SeekableByteChannel from;
	private ByteBuffer buffer;
	/** The bytes of a sector, which is more than the buffer holds for a file smaller than one sector. */
	private final int blockSize;
	private ArchivedFile file;
	private ArchivedFileExtractor extractor;
	private long position;
	private int currentBlock;
	
	public ArchivedFileStream(SeekableByteChannel in, ArchivedFileExtractor extractor, ArchivedFile file){
		from = in;
		this.extractor = extractor;
		this.file = file;
		if( file.hasFlag(BlockTable.FLAG_SINGLE_UNIT) ){
			blockSize = file.fileSize;
		}else{
			blockSize = 512 << file.blockShift;
		}
		// Map protectors set sectors of up to 16 MB, and a buffer that big for every small file filled the heap.
		buffer = ByteBuffer.allocate(Math.max(0, Math.min(blockSize, file.fileSize)));
		position = 0;
		currentBlock = -1;
		open = true;
	}

	@Override
	public void close() throws IOException {
		from = null;
		buffer = null;
		open = false;
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	@Override
	public long position() throws IOException {
		return position;
	}

	@Override
	public SeekableByteChannel position(long newPosition)
			throws IOException {
		// *** argument validation as described by SeekableByteChannel interface
		if( newPosition < 0 ) throw new IllegalArgumentException("files cannot have a negative positon");
		
		// update stream position
		position = newPosition;
		// try and update the buffer position of loaded sectors
		if( currentBlock != -1 ){		
			if( currentBlock != newPosition / blockSize )
				currentBlock = -1;
			else
				buffer.position((int) (newPosition % blockSize));
		}
		
		// *** return value as described by SeekableByteChannel interface
		return this;
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		// closed
		if( !open ) throw new ClosedChannelException();
		// end of stream
		if( position >= file.fileSize ) return -1;
		
		// load current block if no block is currently loaded
		if( currentBlock == -1 ){
			currentBlock = (int) (position / blockSize);
			buffer.clear();
			try {
				buffer = extractor.readBlock(buffer, from, file, currentBlock, blockSize);
			} catch (MPQException e) {
				throw new IOException(e);
			}
			buffer.position((int) (position % blockSize));
			
		}
		
		long positionstart = position;
		while( dst.hasRemaining() ){				
			if( buffer.remaining() > dst.remaining() ){
				int limit = buffer.limit();
				buffer.limit(buffer.position() + dst.remaining());
				position+= buffer.remaining();
				dst.put(buffer);
				buffer.limit(limit);
			}else{
				position+= buffer.remaining();
				dst.put(buffer);
				if(position < file.fileSize){
					currentBlock = (int) (position / blockSize);
					buffer.clear();
					try {
						buffer = extractor.readBlock(buffer, from, file, currentBlock, blockSize);
					} catch (MPQException e) {
						throw new IOException(e);
					}
				}else{
					break;
				}
			}
		}
		
		return (int) (position - positionstart);
	}

	@Override
	public long size() throws IOException {
		return file.fileSize;
	}

	@Override
	public SeekableByteChannel truncate(long size) throws IOException {
		throw new NonWritableChannelException();
	}

	@Override
	public int write(ByteBuffer src) throws IOException {
		throw new NonWritableChannelException();
	}
}
