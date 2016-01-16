/**
 * 
 */
package es.amanzag.yatorrent.protocol.messages;

import es.amanzag.yatorrent.protocol.TorrentProtocolException;

/**
 * @author Alberto Manzaneque Garcia
 *
 */
public class MalformedMessageException extends TorrentProtocolException {

	/**
	 * 
	 */
	public MalformedMessageException() {
	}

	/**
	 * @param message
	 */
	public MalformedMessageException(String message) {
		super(message);
	}

	/**
	 * @param cause
	 */
	public MalformedMessageException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public MalformedMessageException(String message, Throwable cause) {
		super(message, cause);
	}

}
