/**
 * 
 */
package es.amanzag.yatorrent.bencoding;

/**
 * @author Alberto Manzaneque
 *
 */
public class BInteger implements BElement {
	
	private long value;
	
	public BInteger(long value) {
		this.value = value;
	}
	
	public long getValue() {
		return value;
	}
	
	@Override
	public String toString() {
		return Long.toString(value);
	}

}
