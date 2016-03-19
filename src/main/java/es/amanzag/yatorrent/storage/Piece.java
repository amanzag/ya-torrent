/**
 * 
 */
package es.amanzag.yatorrent.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import es.amanzag.yatorrent.metafile.TorrentMetadata;

/**
 * @author Alberto Manzaneque
 *
 */
public class Piece {
	
	private int index, length, completion;
	private boolean locked;
	private byte[] checksum;
	private FileChannel dataChannel;
	private TorrentMetadata metadata;
	
	public Piece(int index, int length, byte[] checksum, FileChannel dataChannel, TorrentMetadata metadata) {
		this.index = index;
		this.length = length;
		this.checksum = checksum;
		this.completion = 0;
		this.dataChannel = dataChannel;
		this.metadata = metadata;
		locked = false;
	}

	public int getCompletion() {
		return completion;
	}

	void markCompleted(int nBytes) {
		this.completion += nBytes;
	}

	public int getLength() {
		return length;
	}

	public byte[] getChecksum() {
		return checksum;
	}
	
	public boolean isComplete() {
		return length == completion;
	}

	public int getIndex() {
		return index;
	}
	
	public void lock() {
	    if(locked) {
	        throw new IllegalStateException("Can't lock a Piece that is already locked");
	    }
	    locked = true;
	}
	
	public void unlock() {
	    if(!locked) {
	        throw new IllegalStateException("Can't unlock a piece that isn't locked");
	    }
	    locked = false;
	}
	
	public boolean isLocked() {
		return locked;
	}
	
	public int getRemaining() {
	    return length-completion;
	}
	
	public void write(ByteBuffer data) throws IOException {
	    if(!locked) {
            throw new IllegalStateException("Piece "+index+" is not locked so is not writable");
        }
	    if(data.remaining() > length - completion) {
	        throw new TorrentStorageException("Tried to write more bytes than remainin in this piece");
	    }
        int toWrite = data.remaining();
        dataChannel.position(index * metadata.getPieceLength() + completion);
        int written = dataChannel.write(data);
        if(toWrite != written) {
            throw new IOException("Not all data could be written");
        }
        markCompleted(written);
	}
	
	public void read(ByteBuffer buffer, int offset) throws IOException {
	    if(!isComplete()) {
	        throw new IllegalStateException("Piece "+index+"is not complete so is not readable");
	    }
	    dataChannel.position(index * metadata.getPieceLength() + offset);
	    dataChannel.read(buffer);
	}
	
}
