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
	private List<Piece> pieces;
	
	public TorrentStorage(TorrentMetadata metadata, File tempTorrent) throws IOException {
		tempDir = new File(ConfigManager.getTempDir()+"/"+metadata.getName());
		
		this.torrentFile = tempTorrent;
		dataFile = new File(tempDir, DATA_FILENAME);
		stateFile = new File(tempDir, STATE_FILENAME);
		this.metadata = metadata;
		initFiles();
		dataChannel = new RandomAccessFile(dataFile, "rw").getChannel();
		stateChannel = new RandomAccessFile(stateFile, "rw").getChannel();
		initPieces();
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
			int numPieces = metadata.getPieceHashes().size();
			ByteBuffer zero = ByteBuffer.wrap(new byte[] {0,0,0,0});
			for(int i=0; i<numPieces; i++) {
				// XXX hay que hacer algo mas con el bytebuffer??
				out.write(zero);
				zero.clear();
			}
			out.close();
		}
		
	}
	
	private void initPieces() throws IOException {
		ByteBuffer states = ByteBuffer.allocate((int)stateChannel.size());
		stateChannel.position(0);
		stateChannel.read(states);
		states.flip();
		pieces = new ArrayList<Piece>(metadata.getPieceHashes().size());
		Piece tmpPiece = null;
		List<byte[]> pieceHashes = metadata.getPieceHashes();
		for(int i=0; i < pieceHashes.size()-1; i++) {
			tmpPiece = new Piece(i, metadata.getPieceLength(), pieceHashes.get(i));
			tmpPiece.markCompleted(states.getInt());
			pieces.add(tmpPiece);
		}
		
		tmpPiece = new Piece(pieceHashes.size(), (int)metadata.getTotalLength()%metadata.getPieceLength(), pieceHashes.get(pieceHashes.size()-1));
		pieces.add(tmpPiece);
		tmpPiece.markCompleted(states.getInt());		
	}
	
	
	public void forceSave() throws IOException {
		dataChannel.force(false);
		
		ByteBuffer buf = ByteBuffer.allocate(pieces.size()*4);
		for (Piece ch : pieces) {
			buf.putInt(ch.getCompletion());
		}
		buf.flip();
		stateChannel.position(0);
		stateChannel.write(buf);
		stateChannel.force(false);
	}
	
	public Piece lockPiece(int index) throws TorrentStorageException {
		Piece tmp = pieces.get(index);
		if(tmp.isLocked()) throw new TorrentStorageException("Piece "+index+" is already locked");
		tmp.setLocked(true);
		return tmp;
	}
	
	public Piece piece(int index) {
	    return pieces.get(index);
	}
	
	public void releasePiece(Piece ch) throws TorrentStorageException {
		if(!ch.isLocked()) throw new TorrentStorageException("Piece "+ch.getIndex()+" was not locked");
		ch.setLocked(false);
	}
	
	public void write(ByteBuffer data, Piece piece) throws IOException, TorrentStorageException {
		if(!piece.isLocked()) throw new TorrentStorageException("Piece "+piece.getIndex()+" is not locked so is not writable");
		int toWrite = data.remaining();
		dataChannel.position(piece.getIndex()*metadata.getPieceLength() + piece.getCompletion());
		int written = dataChannel.write(data);
		if(toWrite != written)
			throw new IOException("Not all data could be written");
		piece.markCompleted(written);
	}
	
	public void close() throws IOException {
		forceSave();
		dataChannel.close();
		stateChannel.close();
	}

}
