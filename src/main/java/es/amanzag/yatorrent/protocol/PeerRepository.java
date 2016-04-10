package es.amanzag.yatorrent.protocol;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class PeerRepository {
    
    private HashSet<Peer> disconnectedPeers;
    private HashMap<Peer, PeerConnection> connectedPeers;
    
    public PeerRepository() {
        disconnectedPeers = new HashSet<>();
        connectedPeers = new HashMap<>();
    }
    
    public Set<Peer> getDisconnectedPeers() {
        return ImmutableSet.copyOf(disconnectedPeers);
    }
    
    public Collection<PeerConnection> getConnectedPeers() {
        return ImmutableList.copyOf(connectedPeers.values());
    }
    
    public void add(Peer peer) {
        if (!connectedPeers.containsKey(peer)) {
            disconnectedPeers.add(peer);
        }
    }
    
    public void add(PeerConnection peerConnection) {
        if (!connectedPeers.containsKey(peerConnection.getPeer())) {
            connectedPeers.put(peerConnection.getPeer(), peerConnection);
        }
    }
    
    public void remove(Peer peer) {
        disconnectedPeers.remove(peer);
        connectedPeers.remove(peer);
    }

    public void remove(PeerConnection peerConnection) {
        disconnectedPeers.remove(peerConnection.getPeer());
        connectedPeers.remove(peerConnection.getPeer());
    }
    
    public int disconnectedPeersCount() {
        return disconnectedPeers.size();
    }
    
    public int connectedPeersCount() {
        return connectedPeers.size();
    }

}
