package es.amanzag.yatorrent.protocol;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import es.amanzag.yatorrent.storage.TorrentStorage;

public class PieceDownloader {
    
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
                    peerConnection.download(storage.piece(pieceIndex));
                    currentDownloads.put(peerConnection, pieceIndex);
                });
            });
    }
    
    private Optional<Integer> findDownloadablePiece(PeerConnection peerConnection, BitField piecesNeeded) {
        BitField peerPieces = piecesNeeded.intersection(peerConnection.getBitField());
        for(int i=0; i<peerPieces.getSize(); i++) {
            if(!currentDownloads.inverse().containsKey(i)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

}
