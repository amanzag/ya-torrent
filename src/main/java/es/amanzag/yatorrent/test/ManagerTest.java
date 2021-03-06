package es.amanzag.yatorrent.test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import com.google.common.eventbus.EventBus;

import es.amanzag.yatorrent.metafile.MalformedMetadataException;
import es.amanzag.yatorrent.metafile.MetafileDownloader;
import es.amanzag.yatorrent.protocol.IncomingConnectionsManager;
import es.amanzag.yatorrent.protocol.TorrentDownload;
import es.amanzag.yatorrent.util.ConfigManager;

public class ManagerTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws MalformedURLException, IOException, MalformedMetadataException {
		File torrentFile = MetafileDownloader.download(
		        new URL("http://dl7.torrentreactor.net/download.php?id=18512523&name=American+Dad+S11E15+HDTV+x264-LOL%5Bettv%5D&hash=055c047535925358181004dc279617eff2ca3cbc"));
		// more initialization code
		IncomingConnectionsManager incoming = new IncomingConnectionsManager(ConfigManager.getPort());
		TorrentDownload dm = new TorrentDownload(torrentFile, new EventBus());
		incoming.addPeerConnectionListener(dm);
		dm.start();
		incoming.start();
		Runtime.getRuntime().addShutdownHook(new Thread() {
		    @Override
		    public void run() {
		        dm.destroy();
		        try {
                    dm.getTorrentThread().join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
		    }
		});
	}

}
