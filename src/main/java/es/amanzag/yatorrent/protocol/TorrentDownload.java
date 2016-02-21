/**
 * 
 */
package es.amanzag.yatorrent.protocol;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import es.amanzag.yatorrent.metafile.MalformedMetadataException;
import es.amanzag.yatorrent.metafile.TorrentMetadata;
import es.amanzag.yatorrent.protocol.messages.PendingHandshake;
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
	private DownloadProcess process;
	private boolean start, stop, destroy;
	private State state;
	private List<Peer> remainingPeers;
	private List<PeerConnection> connectedPeers;
	private TorrentStorage storage;
	private TorrentNetworkManager networkManager;
	
	private enum State { INITIALIZED, STARTED, STOPPED, DESTROYED };
	
	public TorrentDownload(File torrentFile) throws IOException, MalformedMetadataException {
		metadata = TorrentMetadata.createFromFile(torrentFile);
		storage = new TorrentStorage(metadata, torrentFile);
		tracker = new TrackerManager(metadata);
		remainingPeers = new Vector<Peer>();
		connectedPeers = new Vector<PeerConnection>();
		tracker.addTrackerEventListener(this::onNewPeerInTheNetwork);
		networkManager = new TorrentNetworkManager(metadata);
		networkManager.addPeerConnectionListener(this);
		process = new DownloadProcess();
		start = false;
		stop = false;
		destroy = false;
		state = State.INITIALIZED;
		new Thread(process, metadata.getName()).start();
	}
	
	private class DownloadProcess implements Runnable {
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
						makeNewConnections();
						if(stop || destroy) break;
						networkManager.processSocketEvents();
						if(stop || destroy) break;
						findNewActionsToDo();
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
	}
	
	protected void doStart() throws IOException {
		networkManager.start();
		state = State.STARTED;
		start = false;
		logger.debug("Torrent "+metadata.getName()+" started");
	}
	
	protected void doStop() {
		for (PeerConnection connectedPeer : connectedPeers) {
			connectedPeer.kill();
		}
		try {
			networkManager.stop();
		} catch (IOException e) {
			logger.warn("Error closing socket selector for "+metadata.getName());
		}
		state = State.STOPPED;
		stop = false;
		logger.debug("Torrent "+metadata.getName()+" stopped");
	}
	
	protected void doDestroy() {
		state = State.DESTROYED;
		destroy = false;
		logger.debug("Torrent "+metadata.getName()+" destroyed");
	}
	
	public void start() {
		synchronized (process) {
			start = true;
			process.notify();
		}
	}
	
	public void stop() {
		synchronized (process) {
			stop = true;
			process.notify();
		}
	}
	
	public void destroy() {
		synchronized (process) {
			destroy = true;
			process.notify();
		}
	}
	
	private void makeNewConnections() {
		synchronized(remainingPeers) {
			// FIXME cuando intentamos conectar a un peer se queda en el limbo, ni connected ni remaining, por eso falla la comprobacion de maximo
			while(connectedPeers.size() < ConfigManager.getMaxConnections() && remainingPeers.size()>0) {
				Peer peer = null;
				peer = remainingPeers.get(0);
				logger.debug("Trying to connect to peer "+peer);
				remainingPeers.remove(peer);
				try {
				    networkManager.connect(peer);
				} catch (IOException e) {
					logger.debug("Could not connect to peer "+peer);
				}
			}
		}
	}
	
	
	
	private void findNewActionsToDo() {
		// TODO
	}
	
	private void onNewPeerInTheNetwork(Peer peer) {
	    synchronized(remainingPeers) {
	        // FIXME connectedpeers no contiene peers sino peerconnections
	        if(!remainingPeers.contains(peer) && !connectedPeers.contains(peer)) {
	            logger.debug("New peer for download "+metadata.getName()+", "+peer);
	            remainingPeers.add(peer);
	        }
	    }
	}
		
    @Override
    public void onNewConnection(PeerConnection peer) {
        synchronized (remainingPeers) {
            if(!connectedPeers.contains(peer)) {
                if(remainingPeers.contains(peer)) {
                    remainingPeers.remove(peer);
                }
                connectedPeers.add(peer);
                peer.enqueue(new PendingHandshake(metadata.getInfoHash(),ConfigManager.getClientId().getBytes()));
                logger.debug("New peer for download "+metadata.getName()+", "+peer.getPeer());
                // TODO if the connection is connecting to us, we need to register the socket with the selector
            }
        }
    }

    @Override
    public void onConnectionLost(PeerConnection peer) {
        connectedPeers.remove(peer);
    }
    
    @Override
    public byte[] getInfoHash() {
        return metadata.getInfoHash();
    }

}