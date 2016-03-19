package es.amanzag.yatorrent.protocol;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PieceUploader {
    
    private final static Logger logger = LoggerFactory.getLogger(PieceUploader.class);
    
    private List<PeerConnection> peers;
    
    public PieceUploader(List<PeerConnection> peers) {
        this.peers = peers;
    }

    public void scheduleUploads() {
        peers.stream()
            .filter(peer -> peer.isPeerInterested() && peer.isAmChoking())
            .forEach(peer -> {
                logger.debug("Unchoking peer {}", peer);
                peer.setAmChoking(false);
            });
    }

}
