/**
 * 
 */
package es.amanzag.yatorrent.bencoding;

/**
 * @author Alberto Manzaneque
 *
 */
public class BEncodingException extends Exception {

	/**
	 * 
	 */
	public BEncodingException() {
	}

	/**
	 * @param arg0
	 */
	public BEncodingException(String arg0) {
		super(arg0);
	}

	/**
	 * @param arg0
	 */
	public BEncodingException(Throwable arg0) {
		super(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 */
	public BEncodingException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

}
