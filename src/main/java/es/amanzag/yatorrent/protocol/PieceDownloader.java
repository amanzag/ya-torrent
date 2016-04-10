package es.amanzag.yatorrent.protocol;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.eventbus.EventBus;

import es.amanzag.yatorrent.events.DownloadingPeersChangedEvent;
import es.amanzag.yatorrent.storage.Piece;
import es.amanzag.yatorrent.storage.TorrentStorage;

public class PieceDownloader {
    
    private final static Logger logger = LoggerFactory.getLogger(PieceDownloader.class);
    
    private PeerRepository peerRepository;
    private BitField localBitField;
    private TorrentStorage storage;
    private BiMap<PeerConnection, Integer> currentDownloads;
    private EventBus eventBus;
    
    public PieceDownloader(PeerRepository peerRepository, BitField localBitField, TorrentStorage storage, EventBus eventBus) {
        this.peerRepository = peerRepository;
        this.localBitField = localBitField;
        this.storage = storage;
        this.eventBus = eventBus;
        currentDownloads = HashBiMap.create();
    }
    
    public void scheduleDownloads() {
        BitField piecesNeeded = localBitField.reverse();
        peerRepository.getConnectedPeers().stream()
            .filter(peer -> !peer.isPeerChoking())
            .filter(peer -> !currentDownloads.containsKey(peer))
            .forEach(peerConnection -> {
                findDownloadablePiece(peerConnection, piecesNeeded).ifPresent(pieceIndex -> {
                    Piece newDownload = storage.piece(pieceIndex);
                    if(newDownload.isComplete()) {
                        logger.warn("Was going to download piece {} but it is already downloaded", pieceIndex);
                    } else {
                        PeerMessageListener listener = new PeerMessageListener() {
                            @Override public void onDisconnect() {
                                currentDownloads.remove(peerConnection);
                                publishDownloadingPeersChangedEvent();
                            }
                            @Override public void onPiece(int receivedPieceIndex) {
                                currentDownloads.remove(peerConnection);
                                publishDownloadingPeersChangedEvent();
                                // FIXME
//                            peerConnection.removeMessageListener(listener);
                            }
                        };
                        peerConnection.addMessageListener(listener);
                        peerConnection.download(newDownload);
                        currentDownloads.put(peerConnection, pieceIndex);
                        publishDownloadingPeersChangedEvent();
                    }
                });
            });
    }
    
    private Optional<Integer> findDownloadablePiece(PeerConnection peerConnection, BitField piecesNeeded) {
        BitField peerPieces = piecesNeeded.intersection(peerConnection.getBitField());
        for(int i=0; i<peerPieces.getSize(); i++) {
            if(peerPieces.isPresent(i) && !currentDownloads.inverse().containsKey(i)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }
    
    private void publishDownloadingPeersChangedEvent() {
        DownloadingPeersChangedEvent e = new DownloadingPeersChangedEvent();
        e.downloadingPeers = currentDownloads.size();
        eventBus.post(e);
    }

}
