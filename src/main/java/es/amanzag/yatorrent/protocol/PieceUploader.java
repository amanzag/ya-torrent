package es.amanzag.yatorrent.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PieceUploader {
    
    private final static Logger logger = LoggerFactory.getLogger(PieceUploader.class);
    
    private PeerRepository peerRepository;
    
    public PieceUploader(PeerRepository peerRepository) {
        this.peerRepository = peerRepository;
    }

    public void scheduleUploads() {
        peerRepository.getConnectedPeers().stream()
            .filter(peer -> peer.isPeerInterested() && peer.isAmChoking())
            .forEach(peer -> {
                logger.debug("Unchoking peer {}", peer);
                peer.setAmChoking(false);
            });
    }

}
