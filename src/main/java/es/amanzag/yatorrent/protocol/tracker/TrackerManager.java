/**
 * 
 */
package es.amanzag.yatorrent.protocol.tracker;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Vector;
import java.util.logging.Logger;

import es.amanzag.yatorrent.metafile.TorrentMetadata;
import es.amanzag.yatorrent.protocol.Peer;
import es.amanzag.yatorrent.util.ConfigManager;

/**
 * @author Alberto Manzaneque
 *
 */
public class TrackerManager extends Thread implements TrackerEventProducer {
	
	public enum State { INITIALIZED, STARTED, STOPPING, STOPPED }; 
	
	private TorrentMetadata metadata;
	private URL url;
	private List<TrackerEventListener> eventListeners;
	private State state;
	private long lastRequest, waitTime;
	private boolean completed;
	
	private static Logger logger = Logger.getLogger(TrackerManager.class.getName());
	
	public TrackerManager(TorrentMetadata metadata) throws MalformedURLException {
		super("TrackerManager");
		this.metadata = metadata;
		while(this.url == null) {
		    try {
		        setUrl(metadata.getAnnounce());
		    } catch(MalformedURLException e) {
		        logger.warning("Can't use default tracker " + metadata.getAnnounce());
		        for (String announce : metadata.getAnnounceList()) {
		            try {
		                setUrl(announce);
		                break;
		            } catch (MalformedURLException e2) {
		                logger.warning("Can't use additional tracker " + metadata.getAnnounce());
		            }
                }
		    }
		    
		}
		eventListeners = new Vector<TrackerEventListener>();
		state = State.INITIALIZED;
		lastRequest = System.currentTimeMillis();
		waitTime = 0;
		completed = false;
	}
	
	public void setUrl(String urlString) throws MalformedURLException {
	    this.url = new URL(urlString);
        if(!this.url.getProtocol().equals("http")) {
            this.url = null;
            throw new MalformedURLException("protocol "+url.getProtocol()+" not supported");
        }
    }
	
	@Override
	public void run() {
		TrackerRequest req = null;
		TrackerResponse response = null;
		logger.fine("Launching Tracker manager for torrent "+metadata.getName());
		while (state != State.STOPPED) {
			if(System.currentTimeMillis()-lastRequest >= waitTime) {
				try {
					switch(state) {
					case INITIALIZED:
						req = new TrackerRequest(url);
						req.setCompactAllowed(true);
						req.setEvent("started");
						req.setInfoHash(metadata.getInfoHash());
						req.setPeerId(ConfigManager.getClientId());
						req.setPort(ConfigManager.getPort());
						
						//TODO obtener datos reales
						req.setUploaded(0);
						req.setDownloaded(0);
						req.setLeft(10000000);
						response = TrackerResponse.createFromStream(req.make());
						logger.info("Request sent to tracker "+url);
						logger.fine("Next request in "+response.getInterval()+" seconds");
						for (Peer peer : response.getPeers()) {
							for (TrackerEventListener listener : eventListeners) {
								listener.peerAddedEvent(peer);
							}
						}
						lastRequest = System.currentTimeMillis();
						waitTime = response.getInterval()*1000;
						state = State.STARTED;
						break;
					case STARTED:
						req = new TrackerRequest(url);
						req.setCompactAllowed(true);
						req.setInfoHash(metadata.getInfoHash());
						req.setPeerId(ConfigManager.getClientId());
						req.setPort(ConfigManager.getPort());
						if(completed) {
							req.setEvent("completed");
							completed = false;
						}
						
						//TODO obtener datos reales
						req.setUploaded(0);
						req.setDownloaded(0);
						req.setLeft(10000000);
						response = TrackerResponse.createFromStream(req.make());
						logger.info("Request sent to tracker "+metadata.getAnnounce());
						logger.fine("Next request in "+response.getInterval()+" seconds");
						for (Peer peer : response.getPeers()) {
//							peer.setInfoHash(metadata.getInfoHash());
							for (TrackerEventListener listener : eventListeners) {
								listener.peerAddedEvent(peer);
							}
						}
						lastRequest = System.currentTimeMillis();
						waitTime = response.getInterval()*1000;
						break;
					case STOPPING:
						req = new TrackerRequest(url);
						req.setCompactAllowed(true);
						req.setEvent("stopped");
						req.setInfoHash(metadata.getInfoHash());
						req.setPeerId(ConfigManager.getClientId());
						req.setPort(ConfigManager.getPort());
						
						//TODO obtener datos reales
						req.setUploaded(0);
						req.setDownloaded(0);
						req.setLeft(10000000);
						response = TrackerResponse.createFromStream(req.make());
						logger.info("Request sent to tracker "+metadata.getAnnounce());
						logger.fine("Next request in "+response.getInterval()+" seconds");
						for (Peer peer : response.getPeers()) {
							for (TrackerEventListener listener : eventListeners) {
								listener.peerAddedEvent(peer);
							}
						}
						lastRequest = System.currentTimeMillis();
						waitTime = response.getInterval()*1000;
						
						state = State.STOPPED;
						break;
					}

				} catch(TrackerProtocolException e) {
					logger.warning("Error sending request to tracker "+metadata.getAnnounce()+". "+e.getMessage());
					end();
				} catch (IOException e) {
					logger.warning("Error sending request to tracker "+metadata.getAnnounce()+". "+e.getMessage());
					end();
				}
			} else {
				synchronized (url) {
					try {
						url.wait(lastRequest+waitTime-System.currentTimeMillis());
					} catch (InterruptedException e) {
					} catch (IllegalArgumentException e) {}
				}
			}
		}
	}
	
	
	public void end() {
		synchronized (url) {
			state = State.STOPPING;
			url.notify();	
		}
	}

	public void addTrackerEventListener(TrackerEventListener l) {
		eventListeners.add(l);
	}

}
