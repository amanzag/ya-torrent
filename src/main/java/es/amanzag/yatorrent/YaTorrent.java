package es.amanzag.yatorrent;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import com.google.common.eventbus.EventBus;

import es.amanzag.yatorrent.metafile.MalformedMetadataException;
import es.amanzag.yatorrent.metafile.MetafileDownloader;
import es.amanzag.yatorrent.protocol.IncomingConnectionsManager;
import es.amanzag.yatorrent.protocol.TorrentDownload;
import es.amanzag.yatorrent.util.ConfigManager;

public class YaTorrent {

    public static void main(String[] args) {
        if(args.length != 1) {
            System.err.println("Please specify a torrent URL");
            System.exit(1);
        }

        try {
            String torrentLocator = args[0];
            System.out.println("Downloading torrent file: " + torrentLocator);
            File torrentFile = MetafileDownloader.download(new URL(torrentLocator));
            System.out.println("Torrent file downloaded");
            
            EventBus eventBus = new EventBus();
            new YaTorrentConsoleView(eventBus);
            
            IncomingConnectionsManager incoming = new IncomingConnectionsManager(ConfigManager.getPort());
            TorrentDownload dm = new TorrentDownload(torrentFile, eventBus);
            incoming.addPeerConnectionListener(dm);
            dm.start();
            incoming.start();
            System.out.println("ya-torrent engine started");
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        System.out.println("Stopping torrent engine");
                        dm.destroy();
                        dm.getTorrentThread().join();
                        System.out.println("Bye!");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            
        } catch (IOException e) {
            e.printStackTrace();
        } catch (MalformedMetadataException e) {
            System.err.println("The torrent file seems to be malformed. " + e.getMessage());
        }
    }

}
