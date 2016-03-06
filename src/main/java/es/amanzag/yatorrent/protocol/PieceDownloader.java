package es.amanzag.yatorrent.protocol;

import java.nio.ByteBuffer;
import java.util.List;

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
        for (PeerConnection peerConnection : peers) {
            BitField peerPieces = piecesNeeded.intersection(peerConnection.getBitField());
            if(!peerConnection.isPeerChoking() && !currentDownloads.containsKey(peerConnection) && peerPieces.hasBitsSet()) {
                for(int i=0; i<peerPieces.getSize() && !currentDownloads.containsKey(peerConnection); i++) {
                    if(!currentDownloads.inverse().containsKey(i)) {
                        try {
                            Chunk piece = storage.lockChunk(i);
                            peerConnection.requestPiece(i, piece.getCompletion(), Math.min(REQUEST_SIZE, piece.getRemaining()));
                            currentDownloads.put(peerConnection, i);
                            peerConnection.addMessageListener(new PeerMessageAdapter() {
                                @Override
                                public void onPiece(int index, int offset, ByteBuffer data) {
                                    logger.debug("Received piece [index={}, offset={}, length={}] from peer {}",
                                            index, offset, data.remaining(), peerConnection.getPeer());
                                }
                                @Override
                                public void onChoke() {
                                    // TODO 
                                }
                            });
                        } catch (TorrentStorageException e) {
                            logger.warn("Tried to download piece {}, which was locked", i);
                        }
                    }
                }
            }
        }
        
    }

}
