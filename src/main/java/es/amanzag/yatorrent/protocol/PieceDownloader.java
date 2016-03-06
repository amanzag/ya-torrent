package es.amanzag.yatorrent.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import es.amanzag.yatorrent.storage.Chunk;
import es.amanzag.yatorrent.storage.TorrentStorage;
import es.amanzag.yatorrent.storage.TorrentStorageException;

public class PieceDownloader {
    
    private final static Logger logger = LoggerFactory.getLogger(PieceDownloader.class);
    private final static int REQUEST_SIZE = 16 * 1024;
    
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
                    try {
                        Chunk piece = storage.lockChunk(pieceIndex);
                        int tempCompletion = piece.getCompletion();
                        while(tempCompletion < piece.getLength()) {
                            peerConnection.requestPiece(pieceIndex, tempCompletion, Math.min(REQUEST_SIZE, piece.getLength()-tempCompletion));
                            tempCompletion += REQUEST_SIZE;
                        }
                        currentDownloads.put(peerConnection, pieceIndex);
                        peerConnection.addMessageListener(new PeerMessageAdapter() {
                            @Override
                            public void onPiece(int index, int offset, ByteBuffer data) {
                                if(piece.getIndex() != index) {
                                    logger.error("Got block of a different piece than expected");
                                } else if(piece.getCompletion() != offset) {
                                    logger.error("Piece {}. Got block starting in {} but was expecting {}",
                                            index, offset, piece.getCompletion());
                                } else {
                                    logger.debug("Received block [index={}, offset={}, length={}] from peer {}",
                                            index, offset, data.remaining(), peerConnection.getPeer());
                                    try {
                                        storage.write(data, piece);
                                    } catch (IOException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    } catch (TorrentStorageException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                }
                            }
                            @Override
                            public void onChoke() {
                                // TODO 
                            }
                        });
                    } catch (TorrentStorageException e) {
                        logger.warn("Tried to download piece {}, which was locked", pieceIndex);
                    }
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
