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
	void onConnectionLost(PeerConnection peer);
	byte[] getInfoHash();

}
