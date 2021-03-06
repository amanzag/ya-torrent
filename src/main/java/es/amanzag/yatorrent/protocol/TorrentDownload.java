/**
 * 
 */
package es.amanzag.yatorrent.protocol;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import es.amanzag.yatorrent.events.PeerConnectionsChangedEvent;
import es.amanzag.yatorrent.metafile.MalformedMetadataException;
import es.amanzag.yatorrent.metafile.TorrentMetadata;
import es.amanzag.yatorrent.protocol.io.TorrentNetworkManager;
import es.amanzag.yatorrent.protocol.tracker.TrackerManager;
import es.amanzag.yatorrent.storage.TorrentStorage;
import es.amanzag.yatorrent.util.ConfigManager;

/**
 * @author Alberto Manzaneque
 *
 */
public class TorrentDownload implements PeerConnectionListener {
	
	private static Logger logger = LoggerFactory.getLogger(TorrentDownload.class);
	
	private TorrentMetadata metadata;
	private TrackerManager tracker;
	private boolean start, stop, destroy;
	private State state;
	private PeerRepository peerRepository;
	private TorrentStorage storage;
	private TorrentNetworkManager networkManager;
	private BitField localBitField;
	private PieceDownloader pieceDownloader;
	private PieceUploader pieceUploader;
	private Thread torrentThread;
	private EventBus eventBus;
	
	private enum State { INITIALIZED, STARTED, STOPPED, DESTROYED };
	
	public TorrentDownload(File torrentFile, EventBus eventBus) throws IOException, MalformedMetadataException {
		metadata = TorrentMetadata.createFromFile(torrentFile);
		storage = new TorrentStorage(metadata, torrentFile, eventBus);
		tracker = new TrackerManager(metadata);
		peerRepository = new PeerRepository();
		tracker.addTrackerEventListener(this::onNewPeerInTheNetwork);
		networkManager = new TorrentNetworkManager(metadata, storage, eventBus);
		networkManager.addPeerConnectionListener(this);
		start = false;
		stop = false;
		destroy = false;
		state = State.INITIALIZED;
		localBitField = storage.asBitField();
		this.eventBus = eventBus;
		pieceDownloader = new PieceDownloader(peerRepository, localBitField, storage, eventBus);
		pieceUploader = new PieceUploader(peerRepository);
		
		torrentThread = new Thread(this::run, metadata.getName());
		torrentThread.start();
	}
	
	public Thread getTorrentThread() {
        return torrentThread;
    }
	
	public void run() {
		tracker.start();
		// state machine
		try {
			while (state != State.DESTROYED) {
				switch(state) {
				case INITIALIZED:
					logger.debug("Torrent "+metadata.getName()+" initialized");
					synchronized (this) {
						if(!start) wait();
						if(start) {
							// TODO sacar fuera del synchronized lo que pueda tardar
							doStart();
						}
					}
					break;
				case STOPPED:
					synchronized (this) {
						if(!start && !destroy) wait();
						if(start) {
							doStart();
						}
						if (destroy) {
							doDestroy();
						}
					}
					break;
				case STARTED:
					synchronized (this) {
						if(stop) doStop();
						if(destroy) {
							doStop();
							doDestroy();
						}
					}
					checkCompletionStatus();
					makeNewConnections();
					if(stop || destroy || state != State.STARTED) break;
					networkManager.processSocketEvents();
					if(stop || destroy || state != State.STARTED) break;
			        pieceDownloader.scheduleDownloads();
			        pieceUploader.scheduleUploads();
					break;
				case DESTROYED:
					break;
				}
			}
		} catch (InterruptedException e) {
			logger.warn(e.getMessage());
			doStop();
			doDestroy();
		} catch (IOException e) {
			logger.error("Unrecoverable IO exception in torrent "+metadata.getName()+". "+e.getMessage(), e);
			doStop();
			doDestroy();
		}
		
	}		
	
	protected void doStart() throws IOException {
		networkManager.start();
		state = State.STARTED;
		start = false;
		logger.debug("Torrent "+metadata.getName()+" started");
	}
	
	protected void doStop() {
		for (PeerConnection connectedPeer : new ArrayList<>(peerRepository.getConnectedPeers())) {
			connectedPeer.kill();
		}
		try {
			networkManager.stop();
		} catch (IOException e) {
			logger.warn("Error closing socket selector for "+metadata.getName());
		}
		state = State.STOPPED;
		stop = false;
		try {
            storage.forceSave();
        } catch (IOException e) {
            logger.error("Error saving torrent files", e);
        }
		logger.debug("Torrent "+metadata.getName()+" stopped");
	}
	
	protected void doDestroy() {
		state = State.DESTROYED;
		destroy = false;
		logger.debug("Torrent "+metadata.getName()+" destroyed");
	}
	
	public void start() {
		synchronized (this) {
			start = true;
			this.notify();
		}
	}
	
	public void stop() {
		synchronized (this) {
			stop = true;
			this.notify();
		}
	}
	
	public void destroy() {
		synchronized (this) {
			destroy = true;
			this.notify();
		}
	}
	
	private void makeNewConnections() {
		synchronized(peerRepository) {
		    int connectionsToMake = Math.max(
		            ConfigManager.getMaxConnections() - peerRepository.connectedPeersCount(),
		            0);
	        peerRepository.getDisconnectedPeers().stream()
	            .filter(peer -> peer.shouldTryConnection())
	            .limit(connectionsToMake)
	            .forEach(peer -> {
	                logger.debug("Trying to connect to peer "+peer);
	                try {
	                    networkManager.connect(peer);
	                    // FIXME cuando intentamos conectar a un peer se queda en el limbo, ni connected ni remaining, por eso falla la comprobacion de maximo
	                    peerRepository.remove(peer);
	                } catch (IOException e) {
	                    logger.debug("Could not connect to peer "+peer);
	                }
	            });
		    
		}
	}
	
	private void onNewPeerInTheNetwork(Peer peer) {
	    synchronized(peerRepository) {
	        logger.debug("New peer for download "+metadata.getName()+", "+peer);
	        peerRepository.add(peer);
	    }
	}
		
    @Override
    public void onNewConnection(PeerConnection peerConnection) {
        synchronized (peerRepository) {
            peerRepository.add(peerConnection);
            logger.debug("New peer for download "+metadata.getName()+", "+peerConnection);
            peerConnection.sendHandshake();
            
            peerConnection.addMessageListener(new PeerMessageListener() {
                @Override
                public void onHandshake(byte[] infoHash, byte[] peerId) {
                    // we don't want to consider the peer connectable unless they've sent at least the handshake
                    peerConnection.getPeer().recordConnection();
                    peerConnection.sendBitField(localBitField);
                }
                @Override
                public void onBitfield(BitField bitField) {
                    peerConnection.setAmInterested(hasAnInterestingPiece(bitField));
                }
                @Override
                public void onHave(int pieceIndex) {
                    peerConnection.setAmInterested(hasAnInterestingPiece(peerConnection.getBitField()));
                }
                @Override
                public void onPiece(int pieceIndex) {
                    localBitField.setPresent(pieceIndex, true);
                    peerConnection.setAmInterested(hasAnInterestingPiece(peerConnection.getBitField()));
                    
                    for (PeerConnection peerConnection : peerRepository.getConnectedPeers()) {
                        peerConnection.sendHave(pieceIndex);
                    }
                }
                @Override
                public void onDisconnect() {
                    peerRepository.remove(peerConnection);
                    Peer peer = peerConnection.getPeer();
                    peer.recordDisconnection();
                    if (!peer.shouldForget()) {
                        peerRepository.add(peer);
                    }
                    publishPeerConnectionsChangedEvent();
                }
            });
            
            publishPeerConnectionsChangedEvent();
            
            // TODO if the connection is connecting to us, we need to register the socket with the selector
        }
    }

    @Override
    public void onConnectionFailed(Peer peer) {
        peer.recordDisconnection();
        if (!peer.shouldForget()) {
            peerRepository.add(peer);
        }
        
    }
    
    @Override
    public byte[] getInfoHash() {
        return metadata.getInfoHash();
    }
    
    private boolean hasAnInterestingPiece(BitField remoteBitField) {
        return localBitField.reverse().intersection(remoteBitField).hasBitsSet();
    }
    
    private void checkCompletionStatus() {
        if (!localBitField.hasBitsUnset()) {
            try {
                if (!storage.isCommited()) {
                    logger.info("Torrent {} completed, commiting...", metadata.getName());
                    storage.commit();
                }
            } catch (IOException e) {
                logger.error("Error while writing torrent data", e);
            }
        }
    }
    
    private void publishPeerConnectionsChangedEvent() {
        PeerConnectionsChangedEvent event = new PeerConnectionsChangedEvent();
        event.connectedPeers = peerRepository.connectedPeersCount();
        eventBus.post(event);
    }

}
