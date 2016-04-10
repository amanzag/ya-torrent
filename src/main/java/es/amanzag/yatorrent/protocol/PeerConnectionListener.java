/**
 * 
 */
package es.amanzag.yatorrent.protocol;

/**
 * @author Alberto Manzaneque
 *
 */
public interface PeerConnectionListener {
	
	void onNewConnection(PeerConnection peer);
	void onConnectionFailed(Peer peer);
	byte[] getInfoHash();

}
