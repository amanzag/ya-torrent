/**
 * 
 */
package es.amanzag.yatorrent.protocol;

/**
 * @author Alberto Manzaneque
 *
 */
public interface PeerMessageProducer {
	
	void addMessageListener(PeerMessageListener listener);
	void removeMessageListener(PeerMessageListener listener);

}
