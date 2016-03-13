package es.amanzag.yatorrent.protocol;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import es.amanzag.yatorrent.storage.Piece;
import es.amanzag.yatorrent.storage.TorrentStorage;

public class PieceDownloader {
    
    private final static Logger logger = LoggerFactory.getLogger(PieceDownloader.class);
    
    private List<PeerConnection> peers;
    private BitField localBitField;
    private TorrentStorage storage;
    private BiMap<PeerConnection, Integer> currentDownloads;
    
    public PieceDownloader(List<PeerConnection> peers, BitField localBitField, TorrentStorage storage) {
        this.peers = peers;
        this.localBitField = localBitField;
        this.storage = storage;
        currentDownloads = HashBiMap.create();
    }
    
    public void scheduleDownloads() {
        BitField piecesNeeded = localBitField.reverse();
        peers.stream()
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
                            }
                            @Override public void onPiece(int receivedPieceIndex) {
                                currentDownloads.remove(peerConnection);
                                // FIXME
//                            peerConnection.removeMessageListener(listener);
                            }
                        };
                        peerConnection.addMessageListener(listener);
                        peerConnection.download(newDownload);
                        currentDownloads.put(peerConnection, pieceIndex);
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

}
