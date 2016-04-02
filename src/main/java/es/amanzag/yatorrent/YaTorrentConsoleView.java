package es.amanzag.yatorrent;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import es.amanzag.yatorrent.events.PeerConnectionsChangedEvent;

public class YaTorrentConsoleView {
    
    public YaTorrentConsoleView(EventBus eventBus) {
        super();
        eventBus.register(this);
    }

    @Subscribe
    public void onPeerConnectionsChange(PeerConnectionsChangedEvent e) {
        System.out.printf("Peers (?/%s)\r", e.connectedPeers);
    }

}
