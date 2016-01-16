/**
 * 
 */
package es.amanzag.yatorrent.protocol.tracker;

import es.amanzag.yatorrent.protocol.Peer;

/**
 * @author Alberto Manzaneque
 *
 */
public interface TrackerEventListener {
	public void peerAddedEvent(Peer peer);

}
