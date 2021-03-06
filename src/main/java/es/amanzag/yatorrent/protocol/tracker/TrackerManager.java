/**
 * 
 */
package es.amanzag.yatorrent.protocol.tracker;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	
	private static Logger logger = LoggerFactory.getLogger(TrackerManager.class);
	
	public TrackerManager(TorrentMetadata metadata) throws MalformedURLException {
		super("TrackerManager");
		this.metadata = metadata;
		while(this.url == null) {
		    try {
		        // temporary hack to select working tracker
//		        setUrl(metadata.getAnnounce());
		        setUrl(metadata.getAnnounceList().get(3));
		    } catch(MalformedURLException e) {
		        logger.warn("Can't use default tracker " + metadata.getAnnounce());
		        for (String announce : metadata.getAnnounceList()) {
		            try {
		                setUrl(announce);
		                break;
		            } catch (MalformedURLException e2) {
		                logger.warn("Can't use additional tracker " + announce);
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
		logger.debug("Launching Tracker manager for torrent "+metadata.getName());
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
						logger.debug("Next request in "+response.getInterval()+" seconds");
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
						logger.debug("Next request in "+response.getInterval()+" seconds");
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
						logger.debug("Next request in "+response.getInterval()+" seconds");
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

				} catch(IOException | TrackerProtocolException e) {
					logger.error("Error sending request to tracker "+url+". "+e.getMessage(), e);
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
