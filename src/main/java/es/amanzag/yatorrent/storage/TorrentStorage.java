/**
 * 
 */
package es.amanzag.yatorrent.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import es.amanzag.yatorrent.metafile.TorrentMetadata;
import es.amanzag.yatorrent.util.ConfigManager;

/**
 * @author Alberto Manzaneque
 *
 */
public class TorrentStorage {
	
	private final static String TORRENT_FILENAME = "torrent";
	private final static String DATA_FILENAME = "data";
	private final static String STATE_FILENAME = "state";
	
	private File dataFile, stateFile, torrentFile, tempDir;
	private FileChannel dataChannel, stateChannel;
	private TorrentMetadata metadata;
	private List<Chunk> chunks;
	
	public TorrentStorage(TorrentMetadata metadata, File tempTorrent) throws IOException {
		tempDir = new File(ConfigManager.getTempDir()+"/"+metadata.getName());
		
		this.torrentFile = tempTorrent;
		dataFile = new File(tempDir, DATA_FILENAME);
		stateFile = new File(tempDir, STATE_FILENAME);
		this.metadata = metadata;
		initFiles();
		dataChannel = new RandomAccessFile(dataFile, "rw").getChannel();
		stateChannel = new RandomAccessFile(stateFile, "rw").getChannel();
		initChunks();
	}
	
	/**
	 * @param torrentName
	 * @return The location in which the torrent file for a yet started download should be,
	 * even if it doesn't exist
	 */
	public static File getMetadataFileForExistingTorrent(String torrentName) {
		return new File(ConfigManager.getTempDir()+"/"+torrentName);
	}
	
	private void initFiles() throws IOException {
		if(!tempDir.exists()) {
			if(!tempDir.mkdir())
				throw new IOException("Temp dir '' can not be created");
		}
	
		File tmp = new File(tempDir, TORRENT_FILENAME);
		if(!tmp.exists()) {
			FileChannel in = new FileInputStream(torrentFile).getChannel();
			FileChannel out = new FileOutputStream(tmp).getChannel();
			in.transferTo(0, in.size(), out);
			in.close();
			out.close();
		}
		torrentFile = tmp;
		
		if(!stateFile.exists()) {
			FileChannel out = new FileOutputStream(stateFile).getChannel();
			int numChunks = metadata.getPieceHashes().size();
			ByteBuffer zero = ByteBuffer.wrap(new byte[] {0,0,0,0});
			for(int i=0; i<numChunks; i++) {
				// XXX hay que hacer algo mas con el bytebuffer??
				out.write(zero);
				zero.clear();
			}
			out.close();
		}
		
	}
	
	private void initChunks() throws IOException {
		ByteBuffer states = ByteBuffer.allocate((int)stateChannel.size());
		stateChannel.position(0);
		stateChannel.read(states);
		states.flip();
		chunks = new ArrayList<Chunk>(metadata.getPieceHashes().size());
		Chunk tmpChunk = null;
		List<byte[]> pieceHashes = metadata.getPieceHashes();
		for(int i=0; i < pieceHashes.size()-1; i++) {
			tmpChunk = new Chunk(i, metadata.getPieceLength(), pieceHashes.get(i));
			tmpChunk.markCompleted(states.getInt());
			chunks.add(tmpChunk);
		}
		
		tmpChunk = new Chunk(pieceHashes.size(), (int)metadata.getTotalLength()%metadata.getPieceLength(), pieceHashes.get(pieceHashes.size()-1));
		chunks.add(tmpChunk);
		tmpChunk.markCompleted(states.getInt());		
	}
	
	
	public void forceSave() throws IOException {
		dataChannel.force(false);
		
		ByteBuffer buf = ByteBuffer.allocate(chunks.size()*4);
		for (Chunk ch : chunks) {
			buf.putInt(ch.getCompletion());
		}
		buf.flip();
		stateChannel.position(0);
		stateChannel.write(buf);
		stateChannel.force(false);
	}
	
	public Chunk lockChunk(int index) throws TorrentStorageException {
		Chunk tmp = chunks.get(index);
		if(tmp.isLocked()) throw new TorrentStorageException("Chunk "+index+" is already locked");
		tmp.setLocked(true);
		return tmp;
	}
	
	public Chunk chunk(int index) {
	    return chunks.get(index);
	}
	
	public void releaseChunk(Chunk ch) throws TorrentStorageException {
		if(!ch.isLocked()) throw new TorrentStorageException("Chunk "+ch.getIndex()+" was not locked");
		ch.setLocked(false);
	}
	
	public void write(ByteBuffer data, Chunk chunk) throws IOException, TorrentStorageException {
		if(!chunk.isLocked()) throw new TorrentStorageException("Chunk "+chunk.getIndex()+" is not locked so is not writable");
		int toWrite = data.remaining();
		dataChannel.position(chunk.getIndex()*metadata.getPieceLength() + chunk.getCompletion());
		int written = dataChannel.write(data);
		if(toWrite != written)
			throw new IOException("Not all data could be written");
		chunk.markCompleted(written);
	}
	
	public void close() throws IOException {
		forceSave();
		dataChannel.close();
		stateChannel.close();
	}

}
