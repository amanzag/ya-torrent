/**
 * 
 */
package es.amanzag.yatorrent.bencoding;

/**
 * @author Alberto Manzaneque
 *
 */
public class BEncodingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BEncodingException(String arg0) {
		super(arg0);
	}

	public BEncodingException(Throwable arg0) {
		super(arg0);
	}

	public BEncodingException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

}
