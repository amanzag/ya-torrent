/**
 * 
 */
package es.amanzag.yatorrent.protocol.tracker;

/**
 * @author Alberto Manzaneque
 *
 */
public interface TrackerEventProducer {
	public void addTrackerEventListener(TrackerEventListener l);
}
