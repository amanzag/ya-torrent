/**
 * 
 */
package es.amanzag.yatorrent.protocol;

/**
 * @author Alberto Manzaneque Garcia
 *
 */
public class TorrentProtocolException extends RuntimeException {

	/**
	 * 
	 */
	public TorrentProtocolException() {
	}

	/**
	 * @param message
	 */
	public TorrentProtocolException(String message) {
		super(message);
	}

	/**
	 * @param cause
	 */
	public TorrentProtocolException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public TorrentProtocolException(String message, Throwable cause) {
		super(message, cause);
	}

}
