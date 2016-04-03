package es.amanzag.yatorrent;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import es.amanzag.yatorrent.events.DownloadingPeersChangedEvent;
import es.amanzag.yatorrent.events.PeerConnectionsChangedEvent;

public class YaTorrentConsoleView {
    
    private int connectedPeers = 0;
    private int downloadingPeers = 0;
    
    public YaTorrentConsoleView(EventBus eventBus) {
        super();
        eventBus.register(this);
    }

    @Subscribe
    public void onPeerConnectionsChange(PeerConnectionsChangedEvent e) {
        this.connectedPeers = e.connectedPeers;
        refresh();
    }
    
    @Subscribe
    public void onDownloadingPeersChange(DownloadingPeersChangedEvent e) {
        this.downloadingPeers = e.downloadingPeers;
        refresh();
    }
    
    private void refresh() {
        System.out.printf("Peers (%s/%s)\r", downloadingPeers, connectedPeers);
    }

}
