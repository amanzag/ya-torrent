/**
 * 
 */
package es.amanzag.yatorrent.metafile;

/**
 * @author Alberto Manzaneque
 *
 */
public class MalformedMetadataException extends Exception {

	/**
	 * 
	 */
	public MalformedMetadataException() {
	}

	/**
	 * @param arg0
	 */
	public MalformedMetadataException(String arg0) {
		super(arg0);
	}

	/**
	 * @param arg0
	 */
	public MalformedMetadataException(Throwable arg0) {
		super(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 */
	public MalformedMetadataException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

}
