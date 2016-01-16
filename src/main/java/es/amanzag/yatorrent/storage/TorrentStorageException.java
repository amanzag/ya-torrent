/**
 * 
 */
package es.amanzag.yatorrent.storage;

/**
 * @author Alberto Manzaneque
 *
 */
public class TorrentStorageException extends Exception {

	/**
	 * 
	 */
	public TorrentStorageException() {
	}

	/**
	 * @param arg0
	 */
	public TorrentStorageException(String arg0) {
		super(arg0);
	}

	/**
	 * @param arg0
	 */
	public TorrentStorageException(Throwable arg0) {
		super(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 */
	public TorrentStorageException(String arg0, Throwable arg1) {
		super(arg0, arg1);
	}

}
