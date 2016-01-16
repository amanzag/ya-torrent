package es.amanzag.yatorrent.test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;

import es.amanzag.yatorrent.metafile.MalformedMetadataException;
import es.amanzag.yatorrent.metafile.MetafileDownloader;
import es.amanzag.yatorrent.metafile.TorrentMetadata;
import es.amanzag.yatorrent.storage.Chunk;
import es.amanzag.yatorrent.storage.TorrentStorage;
import es.amanzag.yatorrent.storage.TorrentStorageException;

public class StorageTest {

	/**
	 * @param args
	 * @throws TorrentStorageException 
	 */
	public static void main(String[] args) throws MalformedURLException, IOException, MalformedMetadataException, TorrentStorageException {
		System.out.println("start");
		File torrentFile = MetafileDownloader.download(new URL(args[0]));
		TorrentMetadata torrent = TorrentMetadata.createFromFile(torrentFile);
		TorrentStorage storage = null;
		
		// more initialization code
		try {
			storage = new TorrentStorage(torrent, torrentFile);
		} catch (IOException e1) {
			System.err.println("Error creating temp directory for torrent "+torrent.getName()+": "+e1.getMessage());
		}
//		IncomingConnectionsManager incoming = new IncomingConnectionsManager(ConfigManager.getPort());
//		TorrentDownloadManager dm = new TorrentDownloadManager(torrent, files);
//		incoming.addIncomingConnectionListener(dm);
//		dm.start();
//		incoming.start();
//		System.out.println("end");
		
		Chunk ch2 = storage.lockChunk(2);
		storage.releaseChunk(ch2);
		storage.lockChunk(2);
		storage.write(ByteBuffer.wrap(new byte[]{-1}), ch2);

		Chunk ch1 = storage.lockChunk(0);
		storage.write(ByteBuffer.wrap(new byte[]{1,1,1}), ch1);
		
		Chunk ch5 = storage.lockChunk(5);
		storage.write(ByteBuffer.wrap(new byte[]{1,2,3,4,5,6,7,8,9}), ch5);
		
		storage.close();
		
	}

}
