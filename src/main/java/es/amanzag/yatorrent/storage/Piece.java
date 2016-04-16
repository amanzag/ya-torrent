/**
 * 
 */
package es.amanzag.yatorrent.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.amanzag.yatorrent.metafile.TorrentMetadata;

/**
 * @author Alberto Manzaneque
 *
 */
public class Piece {

    private final static Logger logger = LoggerFactory.getLogger(Piece.class);
	
	private int index, length, completion;
	private boolean locked;
	private byte[] checksum;
	private FileChannel dataChannel;
	private TorrentMetadata metadata;
	private List<PieceListener> listeners;
	private SortedSet<Block> outOfOrderBlocks;
	
	public Piece(int index, int length, byte[] checksum, FileChannel dataChannel, TorrentMetadata metadata) {
		this.index = index;
		this.length = length;
		this.checksum = checksum;
		this.completion = 0;
		this.dataChannel = dataChannel;
		this.metadata = metadata;
		locked = false;
		listeners = new LinkedList<>();
		outOfOrderBlocks = new TreeSet<>();
	}
	
	public int getCompletion() {
		return completion;
	}

	void markCompleted(int nBytes) {
		this.completion += nBytes;
		for (PieceListener l : listeners) {
            l.onCompletionChanged(nBytes, completion, length);
        }
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
	
	public void write(int offset, ByteBuffer data) throws IOException {
	    if (offset == getCompletion()) {
	        write(data);
	        try {
	            Block first = outOfOrderBlocks.first();
	            while (true) {
	                if(first.offset == getCompletion()) {
	                    write(first.data);
	                    outOfOrderBlocks.remove(first);
	                    first = outOfOrderBlocks.first();
	                } else if(first.offset < getCompletion()) {
	                    logger.warn("Out of order block is behind current completion. Discarding block");
	                    outOfOrderBlocks.remove(first);
	                } else {
	                    break;
	                }
	            }
	        } catch (NoSuchElementException e) {
	            // no out of order blocks, carry on
	        }
	    } else if (offset < getCompletion()) {
	        throw new TorrentStorageException("Tried to write data that was already written");
	    } else {
	        ByteBuffer clone = ByteBuffer.allocate(data.remaining());
	        clone.put(data);
	        clone.flip();
	        outOfOrderBlocks.add(new Block(offset, clone));
	    }
	}
	
	public void read(ByteBuffer buffer, int offset) throws IOException {
	    if(!isComplete()) {
	        throw new IllegalStateException("Piece "+index+"is not complete so is not readable");
	    }
	    dataChannel.position(index * metadata.getPieceLength() + offset);
	    dataChannel.read(buffer);
	}
	
	public boolean validateData() throws IOException {
	    try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(dataChannel.map(MapMode.READ_ONLY, index * metadata.getPieceLength(), length));
            byte[] calculatedChecksum = md.digest();
            return Arrays.equals(checksum, calculatedChecksum);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
	}
	
	public void addListener(PieceListener listener) {
	    listeners.add(listener);
	    
	}
	
	private static class Block implements Comparable<Block> {
	    int offset;
	    ByteBuffer data;

	    public Block(int offset, ByteBuffer data) {
            this.offset = offset;
            this.data = data;
        }
	    
        @Override
        public int compareTo(Block o) {
            return new Integer(offset).compareTo(o.offset);
        }
	}
	
}
