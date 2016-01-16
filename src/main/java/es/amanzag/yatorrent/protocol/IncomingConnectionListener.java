/**
 * 
 */
package es.amanzag.yatorrent.protocol;

/**
 * @author Alberto Manzaneque
 *
 */
public interface IncomingConnectionListener {
	
	byte[] getInfoHash();
	void incomingConnectionReceived(PeerConnection peer);

}
