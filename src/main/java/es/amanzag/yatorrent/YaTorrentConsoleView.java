package es.amanzag.yatorrent;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import es.amanzag.yatorrent.events.CompletionChangedEvent;
import es.amanzag.yatorrent.events.DownloadingPeersChangedEvent;
import es.amanzag.yatorrent.events.PeerConnectionsChangedEvent;
import es.amanzag.yatorrent.util.Util;

public class YaTorrentConsoleView {
    
    private int connectedPeers = 0;
    private int downloadingPeers = 0;
    private long downloadedBytes = 0;
    private long totalBytes = 0;
    public boolean active = false;
    
    public YaTorrentConsoleView(EventBus eventBus) {
        super();
        eventBus.register(this);
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public boolean isActive() {
        return active;
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
    
    @Subscribe
    public void onCompletionChange(CompletionChangedEvent e) {
        this.downloadedBytes = e.completedBytes;
        this.totalBytes = e.totalBytes;
        refresh();
    }
    
    private void refresh() {
        if (!active) {
            return;
        }
        System.out.printf("Downloaded %s/%s (%.2f%%) \t Peers (%d/%d)\r", 
                Util.humanReadableByteCount(downloadedBytes, false),
                Util.humanReadableByteCount(totalBytes, false),
                (float)downloadedBytes * 100 / totalBytes,
                downloadingPeers, 
                connectedPeers);
    }

}
